---
title: Go 语言实战（一）：goroutine 不是线程——GMP 调度与十万协程的真相
slug: go-practice-01-gmp
categories: golang
---

# Go 语言实战（一）：goroutine 不是线程——GMP 调度与十万协程的真相

这一章我们不从语法开始，而从我真金白银付过学费的一次线上事故开始。这个系列面向的场景是：你已经会写 Go 的基本语法，但离「敢把它放上生产、能在面试里讲清楚为什么」还差一层功力。差的这层功力，靠背文档补不上，只能靠真实场景一遍遍砸出来。

## 一、事故现场：一次促销，把消息网关打挂了

我当时负责一个消息网关服务，职责很朴素：从 Kafka 消费订单事件，调用下游的「短信通」服务把通知发出去。代码是这个样子的：

```go
func (c *Consumer) loop() {
    for msg := range c.kafkaCh {
        // 每条消息一个 goroutine，互不阻塞，天然"高并发"
        go c.handle(msg)
    }
}

func (c *Consumer) handle(msg *Message) {
    ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
    defer cancel()
    resp, err := c.smsClient.Send(ctx, msg.To, msg.Body)
    if err != nil {
        log.Printf("send failed: %v", err)
        return
    }
    _ = resp
}
```

这个写法在平时看起来毫无问题：单机 QPS 200，每条消息一个 goroutine，处理完即回收，`runtime.NumGoroutine()` 稳定在 200 左右。它的问题要在下游变慢的那一天才暴露。

大促预热当晚，短信通因为上游运营商限流，平均响应从 200ms 涨到 8 秒。我们的消费速度没变——Kafka 还是每秒推 200 条进来，每条都照样 `go handle()`。区别只是：这些 goroutine 处理不完也退不出，全都卡在那 8 秒的 HTTP 等待里。

稳态被打破的那一刻，账是这么算的：每秒新增 200 个 goroutine，每个平均存活 8 秒，稳态堆积量 = 200 × 8 = 1600 个，这还能接受；但随着短信通进一步劣化到超时率飙升、消息重投，Kafka 积压回放，新增速率一度冲到每秒 4000 条。十分钟之后，`runtime.NumGoroutine()` 冲过了 103000，容器内存从 300MB 爬到 2.6GB，Kubernetes 的 OOMKiller 介入，进程被杀。重启后 Kafka 从头消费，一波新的积压打进来，循环往复。

最讽刺的是事后复盘时的第一反应：「我们明明没写内存泄漏，内存怎么会爆？」——确实没泄漏。每个 goroutine 都在正常工作、正常退出，问题不在「忘了还」，而在「借得太快」。

## 二、排查过程：先看数字，再看栈

当晚定位花了不到二十分钟，路径值得记住，因为它可以套用在几乎所有「Go 服务内存暴涨」的问题上。

第一步，看监控里的 goroutine 数量曲线。我们通过 expvar 暴露了 `runtime.NumGoroutine()`，曲线从平稳的 200 直接垂直拉起。这个形态说明的不是泄漏（泄漏是缓慢爬坡），而是瞬时堆积——进得比出得快。

第二步，抓 goroutine 剖面，看它们卡在哪。Go 自带的 pprof 一行命令：

```bash
curl http://localhost:6060/debug/pprof/goroutine?debug=1 > goroutines.txt
```

`debug=1` 的输出会按调用栈把 goroutine 分组聚合，每组标注数量。十万个 goroutine 里，99.6% 聚在同一组栈上：

```
103217 @ 0x43a7f8 0x40a8f5 ...
#   0x40a8f4  net/http.(*Client).send +0x84
#   0x40ac37  net/http.(*Client).do +0x37
#   0x412b55  gateway/sms.(*Client).Send +0x175
#   0x4148a2  gateway/consumer.(*Consumer).handle +0x92
```

到了这一步，结论已经不需要再猜：十万 goroutine 全部阻塞在等短信通的 HTTP 响应。代码没写错，只是没有闸门。

第三步，算账定级。goroutine 本身的固定开销是栈 2KB 起步（后面细讲），但真正的 内存大头是每个 goroutine 持有的业务数据：一次 HTTP 调用链上的请求体、响应缓冲、`http.Request` 与连接状态，实测单协程 15~25KB。10 万 × 20KB ≈ 2GB，和监控里 2.6GB 的水位对得上——大头全在这里，而不是栈。

## 三、底层原理：goroutine 为什么便宜，又为什么便宜得危险

要理解这类事故为什么「专属」于 Go，得先讲清楚 GMP 调度模型。这是 Go 面试的第一高频题，但我不想背名词，我们从「goroutine 到底省在哪」倒推。

三个角色各司其职：

| 角色 | 全称 | 是什么 | 关键参数 |
|---|---|---|---|
| G | Goroutine | 一次并发执行的载体，持有栈、指令指针、状态 | 初始栈 2KB |
| M | Machine | 内核线程，真正干活的执行者 | 数量可动态增长 |
| P | Processor | 逻辑处理器，G 的调度上下文，持有本地运行队列 | 数量 = GOMAXPROCS |

第一个省：栈。内核线程的栈是启动时一次性划出的，Linux 默认 8MB（`ulimit -s` 可查），10 万个线程还没干活就要 800GB 虚拟内存，物理内存也扛不住几万个。goroutine 的栈是连续栈，初始只有 2KB，不够用时 runtime 分配一块 2 倍大的新栈，把旧栈内容整个拷贝过去再改指针。这个「按需生长」的设计让一个 P 上排队几十万个 G 成为可能。10 万个空转 goroutine 的栈开销约 200MB——昂贵吗？比线程便宜 4000 倍；但结合第二节的账你会发现，业务数据才是大头。

第二个省：切换。线程切换要陷入内核：保存全套寄存器、切换页表相关的 TLB、内核调度器决策，一次 1~2 微秒起步，还有缓存污染的隐性账单。goroutine 切换完全在用户态完成，runtime 只需把当前 G 的 SP、PC 等寥寥几个寄存器存进 `g.sched`，从本地队列取出下一个 G 恢复现场，实测约 200 纳秒——差了一个数量级。

第三个省：调度本身。每个 P 维护一个 256 槽位的本地运行队列，G 的创建与消费大多只碰自己 P 的队列，几乎无锁；本地队列满了溢出到全局队列；M 空闲时还会从别的 P「偷」一半任务（work stealing）。内核线程 M 只负责执行，G 与 M 的绑定关系由 P 动态撮合。另外几个值得知道的设计：每调度 61 次就强制检查一次全局队列（防止全局队列饿死）；系统调用阻塞时 M 与 P 解绑，P 转手交给别的 M 继续跑队列里的 G；Go 1.14 起支持异步抢占，sysmon 监测到某个 G 独占超过 10ms 就发信号强制打断，死循环再也饿不死别的 G。

到这里，事故的成因就彻底清楚了：goroutine 便宜，让「每条消息一个 goroutine」写起来毫无心理负担；但 Go 的调度器只解决「切换贵」的问题，不产生任何背压。Kafka 的消费循环把「下游处理不过来」这个本该体现在队列长度上的压力，翻译成了「goroutine 数量和内存水位」——调度器越高效，崩得越安静。

## 四、正确姿势：并发必须有闸门

修复的原则一句话：**并发度是资源，必须有上限；排队是数据，必须有去处。** 两种写法，按代码侵入度从低到高。

方案一：`errgroup.SetLimit`，标准库之外最值得 import 的一个包（`golang.org/x/sync/errgroup`）：

```go
func (c *Consumer) loop() {
    g, ctx := errgroup.WithContext(c.ctx)
    g.SetLimit(64) // 并发闸门：同一时刻最多 64 个 handle 在飞
    for msg := range c.kafkaCh {
        m := msg // Go 1.22 前必须拷贝，循环变量复用是另一个坑
        g.Go(func() error {
            return c.handle(ctx, m)
        })
    }
    _ = g.Wait()
}
```

`SetLimit` 的语义是：第 65 个任务会在 `g.Go` 处阻塞等待，直到有空位。注意这正是我们要的背压——它把压力还给了 Kafka 消费循环，Kafka 的 lag 监控会报警，而不是内存监控。

方案二：worker pool，手动版，面试常要求手写，也适合需要精细控制（按消息类型路由、批量合并）的场景：

```go
func (c *Consumer) Start(workers int) {
    var wg sync.WaitGroup
    for i := 0; i < workers; i++ {
        wg.Add(1)
        go func() {
            defer wg.Done()
            for msg := range c.jobs { // jobs 是带缓冲 channel
                c.handle(c.ctx, msg)
            }
        }()
    }
    wg.Wait()
}
```

配套还有三个不能省的动作：把 30 秒的下游超时拆细（连接 3 秒、响应头 5 秒，别让一个慢站占住闸门 30 秒）；`NumGoroutine` 进监控并配告警阈值（我们定的是常态值 10 倍）；在容量规划时算清守恒式——**吞吐 = 并发数 ÷ 平均时延**。下游 8 秒时延、闸门 64，吞吐上限就是 8 条/秒，要 200 条/秒就得下游扩容或者降级，而不是把闸门开到 1600。

## 五、数据说话

光讲道理不算数，下面这组数字你可以用同一段代码在自己的机器上复现（核心就是：起 N 个 goroutine，各自 sleep 一秒并持有 20KB 数据，前后各采一次 `runtime.ReadMemStats`）：

| 并发量 | 创建总耗时 | goroutine 栈开销 | 含业务数据总内存 |
|---:|---:|---:|---:|
| 1,000 | ~0.4 ms | ~2 MB | ~25 MB |
| 10,000 | ~4 ms | ~20 MB | ~250 MB |
| 100,000 | ~45 ms | ~200 MB | ~2.5 GB |

两个读数值得咀嚼。其一，10 万 goroutine 的创建总耗时只有几十毫秒、栈开销只有 200MB——「goroutine 极其便宜」名不虚传；其二，2.5GB 的内存里 92% 是业务数据，跟调度器毫无关系。这就是那晚事故的完整解释：goroutine 的便宜骗过了直觉，让我们忘了并发请求背后的数据才是真金白银。

另一个对照：同样的 10 万并发用 OS 线程实现，按 8MB 栈算是 800GB——Linux 默认配置下连几千个线程都起不来（`vm.max_map_count` 和内存会先后拦住你），更不用说线程切换 1~2µs 对 CPU 的吞噬。goroutine 让 10 万并发从「不可能」变成「一行 for 循环」，也把「不做限流」的后果从连不上（fd 不足）推迟到了 OOM——故障形态更晚出现，但更致命。

## 六、面试怎么答

**Q1：goroutine 和线程的区别？**
30 秒框架，四层递进：①栈——goroutine 2KB 连续栈按需拷贝增长，线程一次性分配 MB 级；②切换——用户态保存恢复少量寄存器约 200ns，内核态切换 1~2µs；③调度——GMP 用户态调度器，M:N 复用，线程由内核 1:1 调度；④代价——因为便宜，语言层不产生背压，滥用会以内存方式爆掉，所以工程上必须限流。最后这句是区分背书者和用过的关键。

**Q2：讲讲 GMP 模型？**
按一次调度路径讲：`go` 创建的 G 优先放入当前 P 的本地队列（256 槽，满了溢出到全局队列）；M 必须绑定一个 P 才能执行 G，从 P 的本地队列取；取空时先看全局队列（每 61 次调度强制看一次），再尝试从别的 P 偷一半；G 阻塞在系统调用时 M 与 P 解绑，P 交给其他 M，避免整个队列被一个阻塞调用拖死；G 阻塞在 channel 或网络时，G 被挂起，M 不阻塞，去跑别的 G——网络就绪由 netpoller（Linux 上是 epoll）回填。

**Q3：哪些时机会发生调度？**
channel 收发阻塞、锁竞争、系统调用、网络 IO 等待、`runtime.Gosched()` 主动让出、GC 的 safepoint、以及 Go 1.14 之后的异步抢占——单个 G 跑超过 10ms 被 sysmon 强制打断。能答全「主动+被动+抢占」三类的是熟手。

**Q4：你项目里 goroutine 数量怎么控制？**
直接讲第一章的事故：每消息一协程 + 下游劣化 = 10 万堆积 + OOM，然后给方案（errgroup.SetLimit / worker pool）、给守恒式（吞吐=并发÷时延）、给监控（NumGoroutine 告警）。用数字和后果讲，比背 API 高两个段位。

**Q5：10 万个 goroutine 会发生什么？**
分层答：栈内存约 200MB（2KB/个）是底线；真正的大头是各自持有的业务数据，20KB/个就是 2GB；调度压力本身可忽略（切换仅 200ns）；但每 100 万个 G 遍历一次的 GC 扫描、调度器全局操作的耗时都会线性劣化。结论：goroutine 数量本身不是问题，「无背压的瞬时需求放大」才是。

## 七、落地清单

这一章的内容可以直接进团队的 code review 清单：

- 任何 `go func()` 出现的位置，必须能回答「上限是多少」——无上限的 fan-out 是缺陷，不是特性
- 优先用 `errgroup.SetLimit` 控并发；需要批量/路由逻辑时手写 worker pool
- 每个 `go` 出去的任务必须带 `context`，且超时从入口一路透传到底
- 所有出网 `http.Client` 必须设置总超时与分层超时，禁止裸用 `http.DefaultClient`
- `runtime.NumGoroutine()` 进监控：常态值、告警线（10 倍常态）、增长斜率三项都要有
- 容量规划用守恒式：吞吐 = 并发闸门 ÷ 下游平均时延；下游慢时要扩容或降级，不是开闸门
- 内存告警先看 goroutine 数曲线再下结论：垂直拉起是堆积，缓慢爬坡才是泄漏

下一章我们接着聊并发原语里最容易写出事故的另一个：channel。死锁四则、close 的三连坑，以及 channel 底层那本「环形队列账本」到底记了什么。
