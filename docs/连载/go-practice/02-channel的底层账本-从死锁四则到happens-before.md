---
title: Go 语言实战（二）：channel 的底层账本——从死锁四则到 happens-before
slug: go-practice-02-channel
categories: golang
---

# Go 语言实战（二）：channel 的底层账本——从死锁四则到 happens-before

channel 是 Go 并发哲学的门面——「不要通过共享内存来通信，而要通过通信来共享内存」。但这句话没告诉你的是：channel 用错的代价极高，轻则 goroutine 泄漏，重则进程 panic。这一章的两起事故都是我亲手写出来的，一起死锁、一起 close panic，把它们的解剖结果拼起来，正好凑齐 channel 底层那本账的全部条目。

## 一、事故现场：一个改配置改出来的死锁，和一个改代码改出来的 panic

先看死锁。我们的支付服务里有一条很朴素的流水线：订单事件进 `orderCh`，一组处理 goroutine 消费它，把结果写进 `resultCh`；对账模块从 `resultCh` 消费。为了削峰，两个 channel 都带了缓冲。某个周五下午，为了「临时降低对账频率」，有人把对账模块的启动改成了配置开关控制——结果发到生产时开关没开。三分钟后：

```
fatal error: all goroutines are asleep - deadlocked!

goroutine 87 [chan send]:
payment.Pipeline.process(...)
    pipeline.go:47  +0x9c
```

这条链路是这样的：处理 goroutine 先从 `orderCh` 收到订单，处理后往 `resultCh` 发送；`resultCh` 缓冲满了（没人消费），发送阻塞；所有处理 goroutine 陆续阻塞；`orderCh` 没人收也满了，生产方阻塞——进程里所有 goroutine 都睡了，runtime 的死锁检测器直接 `fatal error` 杀进程。

这里有个必须划重点的知识点：**runtime 只能检测「全部 goroutine 都阻塞」的死锁**。如果当时进程里还有别的活跃 goroutine（比如 HTTP 服务还在接请求），这个死锁就不会 fatal error，而是演变成更隐蔽的形态——处理 goroutine 永久泄漏，结果队列无限堆积。测试环境更容易看到 fatal（goroutine 少而纯），生产环境反而更容易「带病运行」，这个反直觉的规律值得记下来。

第二起事故在另一个服务。退货流程需要异步通知：「超时 5 秒没收到回调就关闭结果 channel」，同时正常完成也会 close。两行代码各自都对，合在一起就是：

```go
// goroutine A：正常完成
close(resultCh)

// goroutine B：超时兜底
select {
case <-time.After(5 * time.Second):
    close(resultCh) // panic: close of closed channel
case <-done:
}
```

`close` 不是幂等操作，两个 goroutine 竞争 close 同一个 channel，输的那个直接 panic——而 panic 在异步 goroutine 里没人 recover，进程照挂。

## 二、排查过程：把「等」和「panic」翻译成人话

死锁的排查工具很简单，但要会用两个姿势。

姿势一：进程还活着（部分死锁），用 pprof 抓栈。`curl localhost:6060/debug/pprof/goroutine?debug=2` 输出的每个 goroutine 首行都带阻塞状态，重点搜三个关键字：`chan send`（阻塞在发送）、`chan receive`（阻塞在接收）、`semacquire`（阻塞在锁）。把它们画成图——谁等谁——死锁环一眼可见。

姿势二：进程已死（全部死锁），fatal error 的输出本身就带全量 goroutine 栈，别急着重启，先看日志。那次事故里 `goroutine 87 [chan send] pipeline.go:47` 直接指到了发送点，顺着 `resultCh` 的下游一查，「开关没开」五分钟就水落石出。

panic 的排查靠读栈。`close of closed channel` 的调用栈是从 `runtime.closechan` 展开的，栈顶往下一层就是业务代码里 close 的位置。要回答「是谁先 close 的」，光看这一个 panic 不够，需要 `go build -race`：在测试环境开着竞态检测重放流量，runtime 会在第二次 close 发生时精确报告两个 goroutine 的冲突位置。竞态检测器查不出「逻辑上不该有第二次 close」这种设计问题，但能确定性抓住物理上的并发冲突——前者靠设计规则（下一节），后者靠工具。

## 三、底层原理：hchan，一本环形队列账本

channel 的一切行为都可以从 `runtime/chan.go` 的 `hchan` 结构体推导出来，我们把它拆开看：

```go
type hchan struct {
    qcount   uint           // 当前队列里的元素个数
    dataqsiz uint           // 环形缓冲区容量（make 时传入）
    buf      unsafe.Pointer // 环形缓冲区本体
    sendx    uint           // 下一个写入位置
    recvx    uint           // 下一个读取位置
    recvq    waitq          // 等待接收的 goroutine 队列
    sendq    waitq          // 等待发送的 goroutine 队列
    lock     mutex
    closed   uint32
}
```

这就是一本账：`buf` 是环形队列，`sendx`/`recvx` 是读写指针，`sendq`/`recvq` 是「排队的 goroutine」，`lock` 保护整本账。发送操作 `chansend` 的分支逻辑值得逐条记住，因为面试和排障都会用到：

```go
// chansend 的四条路径（伪代码）
lock(&c.lock)
if c.closed != 0 { unlock; panic("send on closed channel") } // 路径④
if sg := c.recvq.pop(); sg != nil {          // 路径①：有接收者排队
    send(c, sg, ep)  // 数据直接从发送方拷进接收方的栈，不过 buf
    return
}
if c.qcount < c.dataqsiz {                   // 路径②：缓冲未满
    qp := chanbuf(c, c.sendx); memmove(qp, ep, ...); c.sendx++
    return
}
// 路径③：缓冲满且无接收者——当前 goroutine 打包成 sudog 挂进 sendq，gopark 让出
```

路径①是个反直觉的细节：**无缓冲 channel 的传输根本不经过 buf**，发送方直接把数据 memcpy 进接收方 goroutine 的栈，两边同时就绪时性能极高。路径③解释了事故一：缓冲满 + 无接收者，goroutine 被挂进 `sendq` 永久 park——如果永远不会有人来收，这就是泄漏或死锁。

第二个反直觉细节：`hchan.lock` 是一把真互斥锁。channel 不是无锁的，高争用场景下它的表现和 mutex 同级。所以「用 channel 一定比锁快」是错觉，选型应该看场景而不是信仰。

最后是最有含金量的部分：channel 为什么能替代锁？答案在 Go 内存模型（2022 修订版）的 happens-before 规则里：

1. 对无缓冲 channel，接收发生在发送完成**之前**；
2. 对带缓冲 channel（容量 C），第 k 次接收发生在第 k+C 次发送完成**之前**；
3. channel 的 close 发生在「因 close 而收到零值」**之前**；
4. 一次发送发生在对应那次接收完成**之前**。

翻译成人话：数据通过 channel 交出去的那一刻，「写入方之前的一切内存操作」对「接收方之后的一切操作」可见。这就是「用通信共享内存」的物理基础——channel 不只是传数据，它同时传递了内存可见性。事故一里如果我们用 `select` 对两个 channel 做非阻塞探测，或者干脆让对账模块常驻，都不会走到全量死锁那一步。

## 四、正确姿势：所有权规则与优雅关闭

channel 的事故九成出在 close 和退出上，给出四条可落地的规则。

规则一：**单向类型在函数签名上写清所有权**。编译器强制的约定胜过任何注释：

```go
func produce(ch chan<- Order)   // 只准发，不准收、不准 close 的误会都没有
func consume(ch <-chan Order)   // 只准收
```

规则二：**由唯一的发送方 close；多个发送方时不 close**。close 的语义是「不会再有数据」，这个承诺只有发送方有能力做出。多个发送方时，用单独的退出信号广播：

```go
// N 个生产者，1 个 close 委员：等所有生产者退出后由 WaitGroup 的持有者 close
var wg sync.WaitGroup
for i := 0; i < producers; i++ {
    wg.Add(1)
    go func() { defer wg.Done(); produceLoop(out) }()
}
go func() { wg.Wait(); close(out) }() // 唯一 closer
```

规则三：**用 context 广播退出，而不是用 close 通知**。「超时兜底 close」那个事故的正解是让下游用 `ctx.Done()` 感知取消，close 收敛到单一生命周期终点（如 `defer close()` 配合单一 goroutine 持有）。

规则四：**接收侧永远用 `v, ok := <-ch` 双返回值**，`ok=false` 表示 channel 已关闭且缓冲已排空，据此退出 for 循环。顺便背下这张行为表，面试必考、事故常见：

| 操作 | nil channel | 已关闭 channel | 正常 channel |
|---|---|---|---|
| 发送 | 永久阻塞 | **panic** | 阻塞或入队 |
| 接收 | 永久阻塞 | 立即返回零值, ok=false | 阻塞或出队 |
| close | **panic** | **panic**（重复 close） | ok |

nil channel 那两格「永久阻塞」不只是坑，也是技巧：在 `select` 里把某个分支的 channel 置为 `nil` 可以动态禁用该分支，这是老手写状态机时的常用手段。

## 五、数据说话

用 benchmark 把几个直觉校准一下（Go 1.22，4 核，数字量级可复现）：

| 场景 | 单次操作耗时 | 说明 |
|---|---:|---|
| 无缓冲 channel 收发各一次（双方就绪） | ~100 ns | 走路径①直接拷栈，最快 |
| 缓冲 64 的 channel 一发一收 | ~120 ns | 过账本（lock + 环形队列） |
| 同场景 + 4 个 goroutine 争用 | ~600 ns | lock 争用开始主导 |
| mutex 加锁解锁（无争用） | ~18 ns | 比想象中便宜得多 |
| 关闭 channel 广播唤醒 1000 个等待者 | ~4 µs | `close` 是 O(等待者数) |

三个结论。第一，channel 的单次开销在百纳秒级，作为「业务事件」的传递介质毫无压力，但别拿它当高频计数器——那个用 `atomic.AddInt64`，18 纳秒以内。第二，争用之下 channel 与 mutex 会趋同，因为它俩底层就是同一把锁；channel 的价值从来不是快，而是**语义**——它把「数据 + 可见性 + 阻塞时机」打包成一个原语。第三，close 广播是 O(N) 唤醒，一万个 goroutine 等同一个 channel 的退出信号也只要几十微秒，「用 close 广播退出很慢」是谣言，放心用。

## 六、面试怎么答

**Q1：channel 的底层数据结构？**
从 hchan 讲起：环形缓冲（buf + sendx/recvx）、等待队列（sendq/recvq，sudog 链表）、一把互斥锁、closed 标记。然后主动加分项：无缓冲且双方就绪时数据直接从发送方栈拷到接收方栈，不过 buf——很多面经答案漏了这条。

**Q2：向 nil channel / 已关闭 channel 收发会怎样？**
背第四节那张表。背完补一句工程含义：nil channel 阻塞用于 select 分支禁用；send on closed panic 和重复 close panic 是线上真实事故源，所以要有「单一 closer」规则。

**Q3：如何优雅地关闭 channel / 退出所有 goroutine？**
分层答：单一发送方——发送方 close，接收方 `v, ok` 退出；多发送方——引入额外的协调者（WaitGroup 收敛后由一个 goroutine close），或直接用 context 取消广播；优雅退出还要配 `for range` 消费 + `wg.Wait()` 收尾，保证「发完、收完、再关」的顺序。

**Q4：channel 的 happens-before 规则？**
背三条核心：发送完成先于对应接收完成；close 先于因 close 收到的零值；缓冲为 C 时第 k 次接收先于第 k+C 次发送完成。然后讲用途：channel 传递的不只是数据，是内存可见性——这是「用通信共享内存」能替代锁的根本原因。

**Q5：select 的机制？**
多个 case 就绪时伪随机选择（防饿死，不是轮询）；所有 case 都不就绪且有 default 则走 default；无 default 则阻塞直到某个 case 就绪。加分项：`select{}` 零分支永久阻塞，runtime 死锁检测会抓住它；nil channel 分支被禁用的技巧也在这里讲。

## 七、落地清单

- channel 的所有权（创建、发送、close）收敛到单一 goroutine；单向类型写进函数签名
- 多发送方场景禁止各自 close，用 WaitGroup 收敛出唯一 closer，或改用 context 取消
- 接收侧一律 `v, ok := <-ch` 或 `for range`，不裸读
- 发送侧评估缓冲：缓冲只是「延迟排队」，满了一样阻塞；需要丢弃语义就显式 select + default
- 高频计数、标志位用 atomic，不拿 channel 凑热闹
- code review 搜三个关键词：`chan send`/`chan receive` 阻塞对（谁发给谁、谁收谁的）、无缓冲发送方的超时保护、`close(` 是否只有一处
- 监控上保留 `NumGoroutine` 增长斜率：部分死锁不 fatal，只能靠曲线发现

下一章是本章的直接续集：channel 用对了是闸门，用漏了就是泄漏源。我们聊 worker pool、errgroup 与「goroutine 泄漏」的完整排查套路。
