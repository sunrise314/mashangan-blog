---
title: Go 语言实战（三）：给协程上个闸——worker pool、errgroup 与泄漏排查
slug: go-practice-03-pool
categories: golang
---

# Go 语言实战（三）：给协程上个闸——worker pool、errgroup 与泄漏排查

第一章的事故是「瞬时堆积」，这一章是它的孪生兄弟：「慢性泄漏」。堆积是一波流量打爆你，泄漏是每个请求漏一点，几小时后服务慢慢死去。我在一个内容聚合服务里完整经历过一次，它教会我的东西比任何教程都多：**goroutine 泄漏的可怕不在于它难发现，而在于它总在周五下午才发作。**

## 一、事故现场：fd 打满，服务「缓缓熄火」

那个服务抓取几百个媒体站点做内容聚合，核心逻辑简单到不值得多看一眼：

```go
func (f *Fetcher) FetchAll(urls []string) {
    for _, u := range urls {
        go f.fetch(u) // 每个任务一个 goroutine，简单直接
    }
}

func (f *Fetcher) fetch(url string) {
    // 埋雷点：http.Get 用的是 http.DefaultClient，没有任何超时
    resp, err := http.Get(url)
    if err != nil {
        log.Printf("fetch %s: %v", url, err)
        return
    }
    defer resp.Body.Close()
    body, err := io.ReadAll(resp.Body)
    if err != nil {
        log.Printf("read %s: %v", url, err)
        return
    }
    f.store(url, body)
}
```

周五中午例行抓取，其中一家站点换了 CDN 配置，TCP 连接能建立，但响应体永远不发完。`http.Get` 没有超时，连接就这样挂着；goroutine 阻塞在 `io.ReadAll` 里等数据，每个 goroutine 手里攥着一个打开的 socket（文件描述符）。fd 数量涨到进程限制（容器里 `ulimit -n 1024`）后，新的连接全部失败：

```
dial tcp: socket: too many open files
```

从这一刻起进入死亡螺旋：健康检查的 HTTP 请求也打不开 socket，Kubernetes 把实例摘出负载均衡；换到别的实例，流量过去又把那台也拖满。看起来像「网络抖动」，实际是自我窒息。

## 二、排查过程：从一条报错到一万个阻塞栈

第一步，确认表象。容器里数 fd：`ls /proc/1/fd | wc -l`，1024，顶格。这不是根因，是病灶的位置——fd 被谁占着？

第二步，抓 goroutine 剖面看「谁拿着 fd 不放」。进程还活着（虽然半死），pprof 还能访问：

```bash
curl http://localhost:6060/debug/pprof/goroutine?debug=1 > gor.txt
```

输出按调用栈聚合，开头一行直接给出总量：`goroutine profile: total 51237`。往下扫，四万九千个 goroutine 聚在同一个栈：

```
49182 @ ... io.ReadAll ...
#   0x... net/http.(*persistConn).readLoop
#   0x... net/http.(*Client).do
#   0x... fetcher.(*Fetcher).fetch
#   ... fetcher/fetch.go:23
```

四万九千个 goroutine 全在「读响应体」。到这一步可以下结论了：不是泄漏在 channel 或锁上，而是阻塞在网络上——没有超时的 HTTP 请求把 goroutine 和 fd 一起冻住了。

第三步，把因果链补完整。goroutine 阻塞 → 它持有的 `resp.Body` 和底层 TCP 连接不关闭（`defer resp.Body.Close()` 还没执行到）→ fd 泄漏 → fd 打满 → 全进程网络瘫痪。这里有个 Go 新手常有的误会：goroutine 是轻，但它持有的系统资源（fd、内存、锁）一点都不轻，**goroutine 不退出，资源就永远不还**。

第四步，验证修复。给 `http.Client` 加上总超时后重放故障（用一个本地假服务器：接受连接、不响应），goroutine 数量在超时后应声回落——闭环。

## 三、底层原理：泄漏的本质是「等一个永远不来的事件」

goroutine 泄漏的定义精确到一句话：**goroutine 阻塞在一个永远不会满足的条件上，永远无法回到运行队列，永远无法被回收。** 让它「永远等不到」的只有三类事件，背下来，排查时按类过滤：

1. **channel 收发**：发送永远没有接收者（第一章的死锁就是全家福版本），或接收永远等不到发送——最常见的是「退出信号没有广播到所有监听者」；
2. **锁**：死锁，或者忘记 unlock 导致后续所有 Lock 永久阻塞；
3. **网络/系统调用**：没有超时的 IO——本例。syscall 里的阻塞是内核态的，连 Go 的抢占都救不了它（抢占只发生在用户态代码运行时）。

pprof 的 goroutine 剖面为什么这么好用？因为它的实现就是遍历所有 goroutine、按调用栈哈希聚合、输出计数。四万九千个 goroutine 聚在同一栈上意味着「四万九千个 G 在等同一件事」，而一件被等了四万九千次的事显然没有发生——逻辑闭环，这就是 debug=1 输出的读法。

另一个要知道的机制：fd 与 goroutine 的生命周期绑定。`net.Conn` 的关闭只能由持有它的代码触发，而持有它的正是那个阻塞中的 goroutine。所以「fd 泄漏」和「goroutine 泄漏」在 IO 场景下是同一个问题的两个投影，`NumGoroutine` 曲线和 fd 曲线会同涨同跌——监控里把两条曲线放一起，诊断效率翻倍。

## 四、正确姿势：闸门、超时、退出路径，三件套

**第一件：给并发上闸。** 两种写法，先推荐 errgroup：

```go
import "golang.org/x/sync/errgroup"

func (f *Fetcher) FetchAll(ctx context.Context, urls []string) error {
    g, ctx := errgroup.WithContext(ctx)
    g.SetLimit(20) // 最多 20 个并发在飞
    for _, u := range urls {
        url := u
        g.Go(func() error {
            return f.fetch(ctx, url)
        })
    }
    return g.Wait() // 返回第一个 error，并通过 ctx 取消其余任务
}
```

`errgroup` 的机制值得知道（面试常问）：它内部就是 WaitGroup + 一个被 `sync.Once` 保护的首个 error + 一个 cancel 函数；`WithContext` 生成的 ctx 在任何子任务出错时被取消；`SetLimit` 用带缓冲 channel 做信号量，`g.Go` 超限即阻塞。三十行以内的轮子，站在它肩膀上就好。

需要常驻 worker 池时手写，注意退出路径必须闭环：

```go
func (f *Fetcher) Start(ctx context.Context, workers int) {
    var wg sync.WaitGroup
    for i := 0; i < workers; i++ {
        wg.Add(1)
        go func() {
            defer wg.Done()
            for {
                select {
                case <-ctx.Done():
                    return // 退出路径 1：全局取消
                case u, ok := <-f.jobs:
                    if !ok {
                        return // 退出路径 2：任务队列关闭
                    }
                    f.fetch(ctx, u)
                }
            }
        }()
    }
    wg.Wait()
}
```

**第二件：给 IO 上超时。** 这次事故的根因不是并发模型，是 `http.DefaultClient` 零超时。生产级配置应该分层：

```go
client := &http.Client{
    Timeout: 15 * time.Second, // 总超时兜底（含读 body）
    Transport: &http.Transport{
        DialContext:         (&net.Dialer{Timeout: 3 * time.Second}).DialContext,
        TLSHandshakeTimeout: 3 * time.Second,
        ResponseHeaderTimeout: 5 * time.Second, // 响应头 5 秒内必须到
        IdleConnTimeout:     90 * time.Second,
        MaxIdleConnsPerHost: 8,
    },
}
```

总超时是保险丝，分层超时是断路器：连接慢、握手慢、响应慢分别有各自的预算，一个慢环节不会吃掉全部 15 秒。

**第三件：给退出上监控。** `runtime.NumGoroutine()` 的曲线必须有「增长斜率」告警：泄漏不是看绝对值（每种服务的常态不同），是看斜率——只涨不跌的锯齿变成只涨不跌的直线，就该报警了。我们在那个事故之后加的阈值是：5 分钟内斜率持续为正且超出常态 3 倍，触发 P2 告警。

## 五、数据说话

同一批 5000 个 URL（其中 10% 模拟为「慢站」：连接后不响应），三组配置对比如下，数字来自那个服务的压测环境，量级可直接参考：

| 配置 | 峰值 goroutine | 峰值 fd | 峰值 RSS | 总耗时 |
|---|---:|---:|---:|---:|
| 不限流 + 无超时（事故版） | 51,237 | 1024（顶格） | 1.8 GB | 任务失败，雪崩 |
| 不限流 + 15s 超时 | 5,000 | ~5,200 | 900 MB | 4 分 12 秒 |
| SetLimit(20) + 分层超时 | 21 | ~60 | 55 MB | 5 分 03 秒 |

这组数字有三个读法。第一，不限流加超时虽然能跑完，但 5000 并发同时打向下游是变相 DoS，5000 个 goroutine 的栈加上每个 200KB 的响应缓冲，RSS 逼近 1GB——而且完全没必要。第二，20 并发的总耗时只比 5000 并发慢 12%：慢站拖累了无限并发的整体进度（大量 goroutine 卡在慢站上空等），而有限并发让快站的请求源源不断——**并发数不等于吞吐，吞吐取决于最慢环节的通行能力**。第三，fd 和 RSS 的差别更悬殊：60 对 5200，55MB 对 900MB。这就是闸门的意义：资源占用从「随输入规模增长」变成「随配置增长」，后者才配得上「容量规划」四个字。

## 六、面试怎么答

**Q1：线上服务 goroutine 数持续增长，怎么排查？**
三步框架：①看曲线形态定性质——缓慢爬坡是泄漏，垂直拉起是堆积（第一章）；②pprof goroutine profile（debug=1）按栈聚合，找到数量最大的栈，确认阻塞类型（chan/semacquire/IO）；③顺着栈定位条件不满足的原因，修条件（补发送者/加超时/补退出广播），而不是简单加内存。最后补一句：修完后用同样的负载回放验证 NumGoroutine 回落。

**Q2：goroutine 泄漏的常见原因？**
按「等不到的事件」分类：channel（无接收者的发送、无发送者的接收、退出信号未广播）、锁（死锁、忘 unlock）、网络（无超时 IO）。每类给一个一句话案例，然后主动说「fd 与 goroutine 生命周期绑定，IO 泄漏会连带 fd 泄漏」。

**Q3：设计一个 worker pool，如何优雅退出？**
画结构：jobs 带缓冲 channel + 固定 worker 协程 + WaitGroup 收尾。退出两条路径：调用方 `close(jobs)` 让 worker 的 `for range` 自然结束；或传入 ctx，`select` 监听 `ctx.Done()`。要点：close 只能由发送方做一次；worker 内的 IO 要带 ctx 超时，否则「退出广播」到了也走不掉；`wg.Wait()` 保证收尾干净。

**Q4：errgroup 的原理？**
WaitGroup 语义 + 首错传播 + ctx 取消三合一：`g.Go` 包装任务，`sync.Once` 记录第一个非 nil error 并调用 cancel；`Wait` 阻塞等全部完成并返回首个 error；`SetLimit` 用带缓冲 channel 计数限流，`Go` 满员阻塞。适用：一批可并行任务、任一失败即取消其余；不适用：常驻池、需要任务排队缓冲的场景。

**Q5：http.Client 的超时怎么配？**
两层：`Client.Timeout` 是总兜底（连接到读完 body）；`Transport` 分层——DialContext（TCP 连接）、TLSHandshake、ResponseHeader（首字节）、IdleConnTimeout（连接池回收）。分层超时的价值：把「故障定位」从「整体超时了」细化到「卡在哪个阶段」。

## 七、落地清单

- 禁止在生产代码里裸用 `http.DefaultClient`/`http.Get`；所有 Client 必须有总超时 + 分层超时
- 所有 `go` 语句必须有并发上限：errgroup.SetLimit 优先，常驻池手写并带退出路径
- worker pool 的退出必须双路径（jobs 关闭 + ctx 取消），worker 内 IO 带 ctx 超时
- 监控三件套：`NumGoroutine` 曲线、增长斜率告警、fd 使用率
- code review 重点扫：`http.Get(`、无 select/超时的 `<-ch`、`go func` 里是否有 `for` 内阻塞调用
- 故障演练常态化：用假慢站（接受连接不响应）做回归，验证超时与闸门有效
- `ulimit -n` 与容器 limits 显式化：fd 上限是资源的最后防线，不能依赖默认值

下一章我们离开并发，去拆 Go 里坑人最多、面试最爱问的数据结构：slice。三次事故，一个根源——共享底层数组。
