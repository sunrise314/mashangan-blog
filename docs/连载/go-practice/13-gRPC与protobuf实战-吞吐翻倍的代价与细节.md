---
title: Go 语言实战（十三）：gRPC 与 protobuf 实战
slug: go-practice-13-grpc
categories: golang
---

# Go 语言实战（十三）：gRPC 与 protobuf 实战

内部服务间通信从 REST 换 gRPC，是最近两年团队做过收益最直接的改造：带宽降 70%，序列化 CPU 减半，P99 少 20ms。但换完第一个月就吃了一次重试风暴——gRPC 把「快」给足的同时也把「误用的威力」放大了：重试策略不设上限、deadline 不透传，一个下游抖动就能滚成全链路事故。这一章把 gRPC 的快（HTTP/2 + protobuf）和它的坑（超时/重试/调试）一次讲透。

## 一、事故现场：省下来的 CPU 和滚起来的重试

改造前的账面：订单服务调风控，单次响应 2.1KB JSON，峰值 5000 QPS。三个痛点同时出现：网关出口带宽 40Mbps 打满（JSON 的字段名占三成体积）；风控服务的 profile 里 `encoding/json` 占 28% CPU（ch12 刚治理过一轮，还是贵）；每次调用新建连接，TIME_WAIT 数万。换 gRPC 后这些全部改善——但两周后出了新事故：风控某次发布抖动，错误率升到 15%，订单服务配置的 gRPC 重试（当时图省事抄了份 service config）对 `UNAVAILABLE` 无限重试加指数退避，**每秒真实打到风控的请求数变成上游的 4.7 倍**，抖动被重试流量钉死在故障状态，回滚发布才缓过来。事后看监控，两条曲线叠出了教科书式的重试风暴：上游 QPS 平稳，下游 QPS 阶梯爬升。

## 二、排查过程：二进制协议怎么调试

REST 时代 curl 一把梭的调试习惯在 gRPC 面前全部失效——二进制帧抓包看不懂。可用的武器按顺序：**grpcurl + 服务端反射**（`grpcurl -plaintext host:port list` 列出所有服务和方法，不发 proto 文件就能调），事故排查时先用它确认服务本身是否健康；**拦截器日志**——一元拦截器里打 method/code/duration/deadline，重试风暴的定位就靠这行日志：同一条 trace 里出现三次 `code=UNAVAILABLE duration=2ms`，中间隔着 50ms/100ms 退避——重试发生在客户端，且没带上限；**服务端统计**：对比「入口请求数」与「gRPC 拦截器计数」，4.7 倍的比值直接指认重试。还有一类隐蔽问题：deadline 断裂——日志里 deadline 字段从第二跳起变成空，说明某一跳的 stub 没把 ctx 传下去，ch10 的透传问题在 gRPC 里换了个马甲。

## 三、底层原理：HTTP/2 多路复用与 protobuf 编码

**HTTP/2 解决的是「HTTP 层的队头阻塞」。** HTTP/1.1 一个连接同一时刻只跑一个请求，并发靠开连接池；HTTP/2 把通信拆成帧（HEADERS/DATA），一条 TCP 连接上可以并行几十上百个**流（stream）**，每个流有独立编号，响应乱序返回也没关系——队头没有了。但注意「传输层仍然有队头阻塞」：TCP 保证字节有序，一个包丢了，**所有流**都要等重传——这是 HTTP/2 相对 HTTP/1.1 没有根治的问题，也是 HTTP/3 改用 QUIC 的动机。头压缩 HPACK 用静态字典（常用头）+ 动态字典（连接级缓存）把 HTTP/1.1 时代重复传输的几百字节头压到几十字节。

**protobuf 的编码账本。** 每个字段编码成 `tag + value`：tag = 字段号左移 3 位或上 wire type，**一个字节同时说清「是哪个字段」和「值是什么类型」**——这就是为什么字段号不能改、新增字段只能往后编：改号等于改协议。数值用 varint（小数字 1 字节、大数字按 7 位分段），负数先做 zigzag 映射（-1→1，1→2）避免负 int64 全部膨胀成 10 字节；字符串是长度前缀 + UTF-8；嵌套消息也是长度前缀的递归编码。对比 JSON 的三重浪费——字段名每次传输、数字转文本、结构靠括号猜——同样数据 protobuf 通常小 3~5 倍。反序列化快的根源也在编码：**不建中间对象树也可以边解码边填结构体**，字段号直接索引到生成的 struct 偏移。

**gRPC 的四层封装与 deadline 传播。** gRPC = HTTP/2 传输 + protobuf 序列化 + 消息协议（5 字节前缀：压缩标志 + 长度）+ 四种方法形态（一元、服务端流、客户端流、双向流）。最值钱的设计是 **deadline 传播**：`ctx` 的截止时间被编码成 `grpc-timeout` 头随请求下发，下一跳的 stub 解出来塞回 ctx——整条调用链共享同一张时间表，每一跳只递减不放宽（与 ch10 的超时语义完全一致），leaf 服务拿到的 deadline 是「扣掉前面所有跳剩余的时间」。重试在客户端拦截器层实现，由 service config 声明：`maxAttempts`、`initialBackoff/maxBackoff`、`retryableStatusCodes`——重试是**配置出来**的，不是代码里 for 循环写出来的，这正是风暴事故的解药所在。

## 四、正确姿势：proto 设计与超时重试纪律

**proto 设计三条铁律**：字段号只增不改，废弃字段写 `reserved 12, 15;` 占住坑位防止复用；新旧消息靠「未知字段保留」天然兼容（老服务读到不认识的字段号会跳过）；单一消息里不要放「路由字段」式的多态设计——枚举 + oneof 才是正路。

**超时与重试纪律**（风暴事故后固化的三条）：每一跳显式设 deadline 递减（入口 500ms → 风控 300ms → 存储 100ms），**deadline 透传永不截断**；重试只对幂等请求开，`maxAttempts=3` 封顶、`retryableStatusCodes` 只放 `UNAVAILABLE`（`INTERNAL`/`DEADLINE_EXCEEDED` 重试可能重复执行副作用）；重试预算——下游错误率超阈值时熔断停止重试（client 层的 circuit breaker，gRPC 的 hedging 与重试都要挂在它后面）。

```go
// 透传 deadline 的正确调用：ctx 是第一参数，stub 不另行设置超时字段
ctx, cancel := context.WithTimeout(ctx, 300*time.Millisecond)
defer cancel()
resp, err := riskClient.Check(ctx, &pb.CheckReq{OrderId: id})
```

**拦截器三件套**进模板：recovery（panic 转 `INTERNAL`，ch07 的 recover 边界）、日志（method/code/duration/deadline 一行结构化）、trace（从 metadata 取上游 span 续写）。proto 里再追求一次「为流而设计」：列表下发、进度推送、批量上传，分别对应 server-stream/client-stream/bidi，别用一元调用轮询模拟。

## 五、数据说话

REST JSON → gRPC/protobuf 同接口压测对比（5000 QPS，订单-风控链路）：

| 指标 | REST/JSON | gRPC/protobuf | 变化 |
|---|---:|---:|---|
| 响应体大小 | 2,148 B | 587 B | −73% |
| 序列化+反序列化耗时 | 3.8 µs | 0.9 µs | −76% |
| 服务端序列化 CPU 占比 | 28% | 9% | −68% |
| 延迟 P99（不含业务） | 6.4 ms | 4.2 ms | −34% |
| 连接数（同等并发） | 1,900 | 32 | −98% |

两点读法：①payload 大小与序列化耗时的收益是**同一来源**（tag+varint 编码消掉字段名与文本化），带宽和 CPU 一起降；②连接数 1900→32 是 HTTP/2 多路复用的直接效果——32 条连接跑满 5000 并发流，TIME_WAIT 问题一并消失。另外补一组风暴后的数字：重试加上 `maxAttempts=3` + 重试预算后，同样的下游抖动场景，放大系数从 4.7 降到 1.3，恢复时间从「人工回滚」变成 8 秒自愈——**重试风暴的治理收益不在平时，在故障时**。

## 六、面试怎么答

**Q1：gRPC 为什么比 REST 快？**
三层：HTTP/2 多路复用与 HPACK 头压缩（连接数、头开销）；protobuf 二进制编码（体积 −70%、序列化 −70%+）；deadline 传播与连接级复用降低排队。追问「快在哪一层」时按这三层拆着答。

**Q2：HTTP/2 解决了队头阻塞吗？**
解决了 HTTP 层的（一连接一请求），没解决 TCP 层的——丢包阻塞所有流，HTTP/3/QUIC 用无序可靠传输根治。能主动说出这个「半解决」状态是关键加分点。

**Q3：protobuf 的编码原理？**
tag（字段号+wire type 一字节）+ varint/zigzag/长度前缀；无字段名靠 schema 对齐；字段号即协议 ID，reserved 防复用；解码按字段号直接填 struct 无中间树。

**Q4：deadline 传播是什么，为什么重要？**
截止时间编码在 `grpc-timeout` 头跨跳传递，全链路共享时间表、只减不增；叶子服务剩余预算 = 入口预算 − 各跳消耗。没有它，超时只在第一跳生效，下游各自为政——ch10 的透传事故在 gRPC 的解法。

**Q5：什么时候不该用 gRPC？**
对外 API（浏览器原生不支持、调试成本高、生态偏内部）；缓存型/文档型交互（HTTP 语义更自然）；强人类可读需求。内部高性能服务间通信、流式场景才是主场。

## 七、落地清单

- proto 评审三条：字段号只增不改、废弃必 reserved、多态用 oneof 不用路由字段
- 每一跳 deadline 显式递减并透传 ctx；stub 调用禁止绕过 ctx 自行设超时
- 重试只对幂等请求开：maxAttempts≤3、只重试 UNAVAILABLE、挂在重试预算/熔断之后——三条缺一就是风暴隐患
- 拦截器三件套（recovery/log/trace）进服务模板；拦截器日志必含 code 与 deadline
- grpcurl + 反射模式常开（生产可只对内网开）；二进制协议不配调试通道等于裸奔
- 「入口 QPS vs 下游收到的 QPS」比值进监控告警：>1.5 必有重试在放大
- 流式场景（下发/上传/推送）直接用 stream 方法，不拿一元调用模拟轮询

下一章是上线前的最后一课：从多阶段构建到优雅退出——容器 OOMKilled 和滚动更新丢请求，两个deployment 事故的全套解法。
