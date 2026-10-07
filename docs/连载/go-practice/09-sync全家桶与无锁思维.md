---
title: Go 语言实战（九）：sync 全家桶与无锁思维
slug: go-practice-09-sync
categories: golang
---

# Go 语言实战（九）：sync 全家桶与无锁思维

这一章是并发内功篇的收官。Mutex、RWMutex、WaitGroup、Once、Pool——这些名字你天天在用，但它们的内部账本决定了你在事故现场能不能看懂「锁为什么这么慢」、在 code review 里能不能一眼认出「Add 放错位置」。这一章把五个原语各自的账本翻开，最后聊一个更高级的话题：什么时候该扔掉锁。

## 一、事故现场：一个被读请求「饿死」的写请求

我们的特性开关服务，核心数据是一张几百条的开关表，读极多（每秒 8 万次）、写极少（运营改配置时才写，一天几十次）。第一版用 Mutex 保护，压测时运营抱怨「改一次配置要等好几秒才生效」——写操作的 Lock 在海量读者面前排队太深。有人提议改 RWMutex「读不阻塞读」：

```go
var (
    mu   sync.RWMutex
    conf = make(map[string]string)
)

func Get(key string) string {
    mu.RLock()
    defer mu.RUnlock()
    return conf[key]
}

func Update(kv map[string]string) {
    mu.Lock()         // 等所有读者退出才能拿写锁
    defer mu.Unlock()
    for k, v := range kv { conf[k] = v }
}
```

改成 RWMutex 后写延迟确实从 2 秒降到 800ms，但还是离谱——8 万 QPS 的读者流是**连绵不断**的，RLock 退出紧接着新的 RLock 进来，写者的 Lock 在传统实现里要等 readerCount 归零，理论上可能永远等不到（Go 1.9 之前真的会饿死）。而且压测还暴露了第二问题：读者一多，P99 反而比单 Mutex 更差——这正是第五章事故二的同款病灶，readerCount 的 cacheline 争用。

第二起事故在同一次压测里现形。有个「单例初始化」用了 Once，但写法是：

```go
var (
    client *RPCClient
    once   sync.Once
)

func GetClient() *RPCClient {
    once.Do(func() {
        client = dial() // dial 要 200ms
    })
    return client
}
```

冷启动压测时首批 100 个并发请求全部阻塞在 once.Do 等 dial 完成——这不是 bug，这是 Once 的语义；真正的问题是 dial 失败会 panic，而 Once 的 f 已标记「执行过」，**失败被永久固化**：此后所有请求拿到一个 nil client，服务从此瘫痪直到重启。失败的初始化没有重试机会，这才是事故。

## 二、排查过程：锁的账单怎么看

写饿死的排查靠 mutex profile 的两个视图。`runtime.SetMutexProfileFraction(1)` 打开后：`delay` 视图回答「谁在等」（写者的 Lock 等待时长按调用栈聚合，2 秒延迟一目了然）；`contention` 计数回答「谁在抢」（RLock 的争用次数）。两个视图对着看，就能区分「临界区太长」和「争用本身太贵」——第五章讲过，这次事故是后者，临界区只有一次 map 读。

Once 失败固化的排查更直接：panic 日志里的栈指向 dial，但服务「恢复」后 GetClient 依然返回 nil——检查 once.Do 的语义文档：「f 只执行一次，无论成功失败」。这行文档就是我事故报告的根因栏。修复前先确认另一个候选方案的单测能复现「首次失败、二次成功」的预期行为，再动手（教训：修复并发原语的误用前，先把预期语义写成测试）。

## 三、底层原理：五个原语的账本

**Mutex：正常模式与饥饿模式。** 内部是状态字 + 信号量。正常模式下解锁时唤醒的等待者要和「刚到的、正在自旋的新请求」公平竞争——新请求占着 CPU 有优势，等待者可能一直抢输。Go 1.9 加了饥饿模式：某等待者等待超过 **1ms**，Mutex 切换到饥饿模式，锁直接「移交」给队首等待者，新请求不再自旋、直接排队；队列清空或等待低于 1ms 时切回正常模式。这个 1ms 阈值就是「写请求被饿 2 秒」事故的对照——真实世界里等了 2 秒说明正常模式的让出概率已经失效，纯粹是读者流太密。

**RWMutex：readerCount 的把戏。** 写者到达时把 readerCount 减成负数（这是「有写者在等」的信号），此后新读者阻塞；已有读者退出使计数归零后写者拿锁。三个工程推论：①读路径每次 RLock/RUnlock 是一对原子操作，并发读者越多 cacheline 争用越贵——它不是「读不花钱」；②写者的等待包含「排空既有读者」的时间，读者流越密等待越深；③RLock 可重入但 Lock 不可重入，递归加写锁直接死锁。

**WaitGroup：一个 64 位状态机。** 高 32 位是计数器，低 32 位是等待者数。Add 增、Done 减、Wait 等计数归零。常见的三类误用全可以从状态机推出：Add 放在 go 出去的函数里——Wait 可能在 Add 之前就看到归零而提前放行（这就是为什么 Add 必须在 go 之前）；计数归零后复用要先 Wait 完成（并发 Wait 与 Add 竞争会 panic）；Add 负数过头计数变负——panic「negative WaitGroup counter」。Go 1.25 起官方又加了 `WaitGroup.Go(fn)` 把「Add + go + Done」三连封装掉，误用面大幅收窄。

**Once：done 原子位 + 慢路径互斥锁。** fast path 是一次原子 load（done 为 1 直接返回，纳秒级）；慢路径加锁、double-check、执行 f、原子 store done。**f panic 时 done 不会置位吗？** 会置位——Once 不回滚，f 执行过（哪怕炸了）就算数。这就是初始化失败固化事故的机制根源，也解释了修复方案为什么是「f 里自己兜住失败」而不是「换一个能重试的 Once」。

**Pool：两级缓存的 GC 共谋者。** 每个 P 一个私有槽 + 共享队列；Get 先看本 P 私有槽，再偷共享队列，最后 New。GC 时池内容进 victim cache（1.13 引入），再等一轮 GC 仍无人用才真正丢弃——所以 Pool 对象的存活期是「一到两个 GC 周期」，拿它当长生命周期缓存必然翻车。这个设计与 ch08 的「高频短命对象」定位严丝合缝。

## 四、正确姿势：选型表、Once 的正确姿势与无锁思维

**锁选型表**（与 ch05 决策表合并看）：

| 场景 | 原语 | 备注 |
|---|---|---|
| 简单计数/标志位 | atomic | 最快，语义要单一 |
| 读极多写极少 + key 稳定 | sync.Map 或 COW | ch05 已详 |
| 短临界区、读写均衡 | Mutex | 别迷信 RWMutex |
| 长临界区、真读多写少 | RWMutex | 临界区长时读锁收益才盖过争用 |
| 初始化 | Once | f 内部必须自己兜失败 |
| 高频短命对象复用 | Pool | Reset 后归还 |

**Once 失败固化事故的修复**——失败不下桌，自己管理重试：

```go
var (
    mu       sync.Mutex
    client   *RPCClient
    clientErr error
)

func GetClient(ctx context.Context) (*RPCClient, error) {
    mu.Lock()
    defer mu.Unlock()
    if client != nil {
        return client, nil
    }
    if clientErr != nil {
        return nil, clientErr // 上次失败的错误直接复用？不够——要允许重试
    }
    c, err := dial(ctx)
    if err != nil {
        return nil, err // 不缓存失败，下次请求重试 dial
    }
    client = c
    return client, nil
}
```

只有「成功」才被记住，「失败」留给下一个请求。要加退避（比如失败后 1 秒内直接拒绝）就在失败分支记个时间戳，仍然不要用 Once。

**无锁思维：先问值不值。** ch05 的 COW 方案（atomic.Pointer 换快照）其实就是一种「无锁读」——它成立的前提是写路径可以承受「全量重建」。无锁的通用套路按成本排序：①单变量用 atomic（CAS 循环）；②不变快照 + 原子换指针（COW）；③真·lock-free 数据结构（MPSC 队列等）——Go 生态里能自己写对 lock-free 结构的人凤毛麟角，第三档永远优先考虑用现成的（container/list 加锁版、第三方无锁库）。**无锁不是快，是「把同步成本从临界区转移到数据结构设计上」**——设计成本一次性付清，换运行时零锁争用；数据结构设计不出来，就老实用锁。

## 五、数据说话

压测环境：8 核，临界区一次 map 读（几十 ns 级），读 8 万 QPS + 每 100ms 一次写，四组实现的读 P99 与写延迟：

| 实现 | 读 P99 | 写延迟 | 备注 |
|---|---:|---:|---|
| Mutex | 45 µs | 1.9 s | 读者流连续，写锁排队深 |
| RWMutex | 61 µs | 820 ms | 读 P99 反而差（readerCount 争用） |
| 分片 Mutex（32 片） | 18 µs | 90 ms | 争用摊薄 32 倍 |
| COW + atomic.Pointer | 3 µs | 4.1 ms | 读零锁；写全量拷贝（几百条，便宜） |

另一组基准（4 核，单操作量级）：

| 操作 | 耗时 |
|---|---:|
| atomic.LoadInt64 | ~6 ns |
| atomic.CompareAndSwapInt64（无争用） | ~7 ns |
| Mutex Lock/Unlock（无争用） | ~18 ns |
| Once Do（done=1 fast path） | ~4 ns |
| Pool Get/Put（本 P 命中） | ~25 ns |

读法：①「RWMutex 更适合读多写少」在这组数字里被证伪——读 P99 比 Mutex 还差 35%，争用发生在 readerCount 上，与临界区长短无关（与 ch05 结论互证）；②COW 的读路径只有一次原子 load + 一次 map 读，比所有锁方案快一个量级，写路径的全量拷贝在几百条配置的量级只要毫秒——**这个量级下 COW 全面胜出**；③atomic 与 Mutex 的差是 3 倍（无争用），不是数量级，所以「简单标志位才用 atomic」的规则依据是语义（CAS 只对单变量有效）而非性能。

## 六、面试怎么答

**Q1：Mutex 的正常模式和饥饿模式？**
状态字 + 等待队列；正常模式解锁后等待者与新请求竞争、新请求占 CPU 有优势；等待超过 1ms 触发饥饿模式，锁直接移交队首，新请求直接排队；队列清空切回正常。设计意图：兼顾吞吐（正常模式）与公平（饥饿模式）。

**Q2：RWMutex 的原理，什么时候用它？**
readerCount 原子计数 + 写者置负数拦截新读者 + 排空后拿写锁。什么时候用：临界区长、读远多于写、写频率低到争用不显著。什么时候不用：短临界区（读锁的原子对开销盖过收益）、或如本题读者流极密（写饿 + 读争用双输）。能主动说「RWMutex 不是免费的读」就超过大多数候选人。

**Q3：WaitGroup 的正确用法与常见误用？**
Add 在 go 之前、Done 用 defer、Wait 收尾；误用三件套（Add 在 goroutine 内导致 Wait 提前放行、复用未 Wait、负计数 panic）各配一句后果。补一句 Go 1.25 的 `wg.Go(fn)` 把三连封装，误用面收窄。

**Q4：sync.Once 的原理？初始化失败会怎样？**
fast path 原子 load + 慢路径互斥锁 double-check；f 执行过（含 panic）即永久标记 done，**失败会被固化**。正确姿势：f 内部自己处理失败（重试/降级），或用「显式缓存成功、失败不缓存」的手写模式。这题的加分点就是主动讲出失败固化的坑。

**Q5：sync.Pool 的原理与使用边界？**
每 P 私有槽 + 共享队列，Get 顺序私有→共享→New；GC 两轮回收（victim cache），对象存活一两个 GC 周期。边界：高频短命对象复用，Put 前 Reset，不是缓存。能连到 GC（ch08）讲 victim cache 的是熟手。

## 七、落地清单

- 锁选型先做争用测量（mutex profile）再选型；「读多写少就 RWMutex」从团队规范里删掉
- 短临界区 + 高并发读：分片 Mutex 或 COW，二选一压测定
- Once 的 f 禁止包含可能失败的初始化；失败重试语义手写（缓存成功、不缓存失败）
- WaitGroup：Add 永远在 go 之前，Done 用 defer；新代码可直接用 wg.Go（1.25+）
- atomic 只用于单变量的计数/标志/指针交换；涉及不变量的一组变量改用锁或快照
- sync.Pool 只放「构造贵 + 短命 + 无状态残留」对象，Put 前 Reset 是硬规则
- 无锁改造决策链：先测量（争用占多少）→ atomic 单变量 → COW 快照 → 现成无锁结构；永远不自研 lock-free
- 三个 profile 进监控基线：mutex（delay/contending）、block、goroutine——锁的病没有 panic 可看，全靠 profile

并发内功篇到此收官。下一批进入工程实战篇：context 的传播链、泛型与错误处理工程化、P99 调优全程复盘、gRPC 实战、生产部署与优雅退出，最后以「Go 高频 30 题」面试总纲收束全系列。
