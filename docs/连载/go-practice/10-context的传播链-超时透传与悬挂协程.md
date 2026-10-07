---
title: Go 语言实战（十）：context 的传播链
slug: go-practice-10-context
categories: golang
---

# Go 语言实战（十）：context 的传播链

从这一章开始进入工程实战篇。并发内功（第一、二批）解决「单进程内怎么写对」，工程篇解决「服务上线后怎么活得好」。第一个主角是 context——它是我见过被误用最多的标准库：该透传的地方用 `context.Background()` 截断了超时，该传的 goroutine 忘了传导致协程悬挂，该 defer 的 cancel 没调导致内存缓慢上涨。这一章从一次雪崩说起，把 context 的传播机制翻到底层，最后给出一条可以直接进 code review 规范的检查清单。

## 一、事故现场：下游抖 10 秒，上游全躺平

我们的支付回调服务 PayCallback，收到渠道回调后要调风控服务做一次校验。某天风控服务因为一次糟糕的发布抖动，RT 从 80ms 涨到 10 秒。PayCallback 第一版代码里，这个调用是这样写的：

```go
func handlePayCallback(w http.ResponseWriter, r *http.Request) {
    record := parseCallback(r)
    // 没有超时，没有 ctx——http.Post 默认零超时
    resp, err := http.Post(riskURL, "application/json", bytes.NewReader(record))
    if err != nil {
        w.WriteHeader(500)
        return
    }
    defer resp.Body.Close()
    // ... 校验通过后入库
}
```

`http.Post` 内部用 `http.DefaultClient`，Timeout 为零——**愿意等一万年**。风控抖起来之后，每个回调请求挂 10 秒，渠道侧重试继续打进来，服务里的 goroutine 以每秒几千的速度堆积。Go 的 goroutine 便宜，便宜指的是栈只有 2KB 起，但每个卡住的 goroutine 手里还攥着连接：HTTP Transport 的连接池被占满，后续请求在池前排队，**连不依赖风控的接口也开始超时**——典型的雪崩：一个下游拖垮整台机器。

超时加上之后（`client.Timeout = 500ms`），服务缓过来了，但发布同事顺手写了一个异步落库优化，又埋了一颗雷：

```go
func handlePayCallback(w http.ResponseWriter, r *http.Request) {
    record := parseCallback(r)
    go func() {
        time.Sleep(2 * time.Second) // 等风控结果落库后再补一条审计
        saveAudit(record)           // 问题函数
    }()
    // ...
}
```

滚动发布时进程收到 SIGTERM 直接退出，这批 `saveAudit` 写到一半，审计数据一部分丢失、一部分重复（渠道侧没收到 200 会重发）。而且排查日志时发现这批审计日志**全部没有 trace id**——因为日志中间件是从 ctx 里取的，而这里压根没传。两个症状，同一个根：context 没有跟着调用链走下去。

## 二、排查过程：goroutine profile 里看「谁在悬挂」

第一起雪崩的定位靠 goroutine profile。`/debug/pprof/goroutine` 的 full 栈视图里，4.7 万个 goroutine 全部停在同一个位置：`net/http.(*Transport).roundTrip`——调用栈从 `handlePayCallback` 一路下去，每个栈帧一致。goroutine 数量本身不是问题（我们平时也有一两万），**数万个同栈帧的堆积**才是问题：它们在等同一个慢下游。再对照 block profile 和监控里连接池的占用曲线，定位链就闭合了：下游 10s → 无超时 → 池满 → 全站排队。

第二起问题的排查更有意思：日志系统里搜审计关键字，发现 30% 的审计记录缺 trace id。沿着日志中间件查，取 trace 的入口是 `trace.FromContext(ctx)`，参数来自请求 ctx——而 `saveAudit` 的 goroutine 调用栈里根本没有 ctx。缺 trace 不是日志系统的 bug，是调用链断裂的信号。**goroutine profile 看堆积、日志缺字段看断裂**，这两个入口几乎覆盖了 context 问题的全部排查场景。

## 三、底层原理：一棵 cancel 树的诞生

`context.Context` 是四个方法的接口：`Deadline`、`Done`、`Err`、`Value`。核心实现是 `cancelCtx`，把它拆开看，传播机制一目了然。

**done channel 的惰性创建与 close 广播。** `cancelCtx` 的 done 字段初始为 nil，第一次调用 `Done()` 才创建一个 `chan struct{}`。`cancel()` 做的事：关掉这个 channel，设置 err。第二章讲过 channel 的 close 语义——**close 是一个广播原语**，所有 `<-ctx.Done()` 的监听者同时解除阻塞。这就是「取消传播」的地基：父节点取消，所有等待 `父.Done()` 的人醒来。

**children map：取消的自上而下遍历。** `WithCancel(parent)` 会在 parent 的 children map 里登记子 ctx。父被 cancel 时，遍历 children 逐个 `cancel()`——先关自己的 done，再递归关子树。所以取消是**自上而下**的单向传播：父取消，子必取消；子取消，父无感。这就是为什么「叶子节点的超时」必须小于等于父链上任何一层：`WithTimeout(reqCtx, 500ms)` 的 500ms 只会收紧，不会放宽——你没法用一个子 ctx 把父的超时「延后」。

**propagateCancel 的两条路。** `WithCancel` 内部调 `propagateCancel(parent, c)` 绑定父子关系，逻辑分三支：父的 `Done()` 是 nil（比如 Background）→ 什么都不做，子 ctx 永远不会被动取消；父是 `*cancelCtx` → 直接把子塞进 parent.children（零开销路径）；父可取消但**不是** cancelCtx（比如自定义实现或 timerCtx 的外层）→ **起一个 goroutine**，select 监听父 Done 和子 Done，谁先来就取消谁。注意第三支：每次 WithCancel 都可能养一个看门 goroutine，这是 context 自身的开销来源之一。

**timerCtx：超时是定时取消。** `WithTimeout(parent, d)` = `WithDeadline(parent, time.Now().Add(d))`，timerCtx 在 cancelCtx 外面套了一个 `time.AfterFunc`：定时器到点调 cancel。所以**不调用 cancel 的代价是具体的**：①timer 到点前一直挂在 runtime 定时器堆上；②timerCtx 作为子节点留在父的 children map 里——如果父是长生命周期的（比如全局复用的一个 ctx），这些孩子**永不释放**，这就是「服务内存缓慢上涨」的一类经典元凶。go vet 的 `lostcancel` 检查就是抓「cancel 返回值被丢弃」这类代码。

**valueCtx：链表查找。** `WithValue(parent, k, v)` 生成一个 valueCtx 节点，`Value()` 沿 parent 链**逐层向上**查找，O(链深)。key 建议用自定义类型（`type ctxKey struct{}`）而不是 string——string key 会和其他中间件撞车。链深一般在个位数到十几层，每层查找十几纳秒，一百万 QPS 也只是零头；但别把业务参数塞进去——value 是**只读元数据**（trace、鉴权态），不是函数参数的替代品，参数该显式传就显式传，藏在 ctx 里的参数会让函数签名失去信息量。

## 四、正确姿势：让 ctx 长满每一层调用链

事故一的修复，本质是把「无界的等待」改成「有界的传播」：

```go
var riskClient = &http.Client{ Timeout: 500 * time.Millisecond }

func handlePayCallback(w http.ResponseWriter, r *http.Request) {
    ctx := r.Context() // 框架入口的请求级 ctx，别再 Background
    record := parseCallback(r)

    riskCtx, cancel := context.WithTimeout(ctx, 500*time.Millisecond)
    defer cancel() // 提前返回也释放 timer 和 children 登记

    req, _ := http.NewRequestWithContext(riskCtx, http.MethodPost, riskURL, bytes.NewReader(record))
    resp, err := riskClient.Do(req)
    // ...
}
```

事故二的修复要做一个决策：审计落库的生命周期到底归谁。如果允许「请求返回后继续跑」，就应该**脱离请求 ctx**、用 Background 派生并自带超时与 errgroup 约束（第三章的 worker pool 模式）；如果要求「随请求取消」，就传请求 ctx 并在 goroutine 里 select Done。两种都合法，怕的是不选：

```go
// 方案 A：跟随请求——请求取消/超时，落库一并放弃
go func() {
    if err := saveAuditCtx(ctx, record); err != nil { log.Warn(...) }
}()

// 方案 B：独立生命周期——滚动发布不丢数据（推荐，审计类业务选这个）
g, gctx := errgroup.WithContext(context.Background())
g.Go(func() error {
    ctx, cancel := context.WithTimeout(gctx, 3*time.Second)
    defer cancel()
    return saveAuditCtx(ctx, record)
})
```

两条工程铁律直接进规范：**ctx 永远是函数第一个参数**，命名 `ctx`，别塞进 struct 字段（例外：明确短生命周期的对象如一次请求的 handler 闭包，也要注释声明）；**每个 WithCancel/WithTimeout/WithDeadline 的返回 cancel 当行 defer**——`defer cancel()` 是零成本的，泄漏是真实成本的。

## 五、数据说话

雪崩场景复现（压测 2000 QPS 回调流量，风控 RT 恒定 10s，网关超时 1s）：

| 实现 | goroutine 峰值 | Transport 连接占用 | 无关接口 P99 |
|---|---:|---:|---:|
| http.Post 零超时 | 47,000+ | 100%（池满排队） | 8.2 s |
| client.Timeout=500ms（不透传 ctx） | 3,100 | 62% | 210 ms |
| NewRequestWithContext 透传 | **420** | **8%** | **45 ms** |

第三列是关键：仅仅设置 client.Timeout 是「各管一段」，透传 ctx 才让上游网关的 1s 超时、本进程的 500ms 超时、风控侧的熔断器共享同一张时间表，取消能一插到底。

context 自身开销的基准（8 核，单操作量级，与 ch09 的原语基准同口径）：

| 操作 | 耗时 |
|---|---:|
| context.Background()（全局单例） | ~2 ns |
| WithCancel（父为 cancelCtx，零开销路径） | ~120 ns |
| WithTimeout + defer cancel | ~180 ns |
| cancel 传播（1 层父 + 100 个子） | ~9 µs |
| valueCtx.Value（链深 10，未命中到底） | ~95 ns |

读法：①一次 WithTimeout+defer cancel 不到 200ns，相对任何一次 RPC 都是零头，「怕慢不传 ctx」不成立；②cancel 传播是 O(子树大小) 的遍历，100 个孩子微秒级，但**别拿 ctx 当万级扇出的广播总线**，那是 job 队列的活；③value 查找线性于链深，中间件各塞一层也没问题，塞一千层才会疼。

## 六、面试怎么答

**Q1：context 是什么，解决什么问题？**
一句话框架：**跨 API 与 goroutine 边界的取消信号 + 请求级元数据的传播通道**。没有它，超时和取消只能靠各层自己配，配不全就是「上游放弃了、下游还在烧」。底层是 cancel 树：close done channel 广播 + children 自上而下遍历。

**Q2：WithTimeout 之后不调用 cancel 会怎样？**
两个具体的泄漏点：timer 挂到 deadline 才释放；timerCtx 作为子节点留在父的 children map，父是长生命周期 ctx 时永不回收。所以「立即 defer cancel」是硬规则——cancel 提前调用只是让释放来得更早，不影响语义。

**Q3：WithValue 能不能传业务参数？**
不能。value 的定位是只读元数据（trace、鉴权、租户），两个理由：函数签名失去参数信息、链式查找 O(n) 且 key 易撞。业务参数显式传参。这题的加分点是说出「自定义 key 类型避免跨中间件碰撞」。

**Q4：怎么避免 goroutine 泄漏，和 context 什么关系？**
泄漏的本质是 goroutine 阻塞在一个**永远不来**的信号上。context 提供了那个信号：长任务在 select 里同时监听 `ctx.Done()` 和业务 channel，退出路径永远存在。配合 goroutine profile 巡检：数量基线 + 同栈帧堆积告警。

**Q5：Background 和 TODO 的区别？**
语义上 Background 是「根、永不取消」，TODO 是「占位、还没想清楚用哪个」。实现上是同一个单例。真实项目里两者都应该只出现在 main 和中间件入口——业务函数出现 Background，九成是透传断了。

## 七、落地清单

- ctx 永远第一个参数、命名 ctx；禁止存进 struct 字段（除非注释声明生命周期）
- 每个派生 ctx（WithCancel/Timeout/Deadline）当行 `defer cancel()`，go vet lostcancel 进 CI
- 网络调用一律带 ctx 的版本：http.NewRequestWithContext、QueryContext/ExecContext、grpc 调用自带 ctx
- client.Timeout 与 ctx 超时**同时设**（client.Timeout 兜住读 body 的整段，ctx 管传播）；两层取更紧
- goroutine 退出路径必须存在：select 业务 channel + ctx.Done()；异步任务想清楚生命周期归请求还是归进程，二选一
- Value 只放只读元数据（trace/鉴权/租户），key 用自定义类型；业务参数显式传
- goroutine profile 进监控基线：数量告警 + 同栈帧 TOP3 告警——context 的病不发 panic，只在 profile 里长个子
- code review 一票否决项：业务函数里出现 context.Background()；日志缺 trace id 视为调用链断裂信号

下一章继续工程篇：错误处理与泛型的工程化——`%w` 丢了错误链，重试逻辑就会失效；样板代码堆成山，就该轮到泛型出场了。
