---
title: Go 语言实战（五）：map 并发崩溃复盘——concurrent map writes 与 sync.Map
slug: go-practice-05-map
categories: golang
---

# Go 语言实战（五）：map 并发崩溃复盘——concurrent map writes 与 sync.Map

Go 的所有 panic 都可以 recover，只有一类错误例外：`fatal error: concurrent map writes`。它不走 panic/recover 流程，直接把进程砸死，日志里只有一段裸栈。我在一个风控服务上完整领教过它的威力——凌晨三点被告警吵醒，重启后以为平息了，两小时后同一位置再炸一次。这一章把「为什么 map 并发写要杀死进程」「怎么查」「sync.Map 到底什么时候用」一次讲透。

## 一、事故现场：两起事故，一种病根

**事故一：风控缓存炸进程。** 我们的风控服务把规则表缓存在进程内存里：`map[int64]Rule`，请求协程读，后台刷新协程每五分钟全量重建后整体写入。代码大概长这样：

```go
var rules = make(map[int64]Rule)

// 请求协程（高并发读）
func GetRule(userID int64) (Rule, bool) {
    r, ok := rules[userID] // ← 崩溃点
    return r, ok
}

// 刷新协程（每 5 分钟写一次）
func refresh() {
    fresh := buildFromDB()
    rules = fresh // 整体替换，看起来"原子"？
}
```

写法看起来很克制——刷新协程先构建新 map 再整体赋值，没有逐 key 写。但它炸了，错误就是标题里那行 `fatal error: concurrent map read and map write`。原因在下一节讲透，这里先记住症状：进程直接死亡，`recover` 无法拦截，崩溃点在「读」的一方，但真正犯规的是「写」的一方——日志最会骗人的地方就在这。

**事故二：为了修事故一，引入了更慢的 bug。** 复活节修法：上锁。用 `sync.RWMutex` 包住 map，读走 `RLock`，写走 `Lock`。进程不再死了，但压测时发现读多写少（读：写 ≈ 1000:1）的场景下，QPS 3 万时读接口 P99 从 8ms 涨到 40ms。更诡异的是：写 QPS 只有 30，锁的争用怎么会这么重？后面用 mutex profile 拆开看，答案和 RWMutex 的实现有关。

## 二、排查过程：读不懂的崩溃，和读得懂的火焰图

事故一的排查要回答三个问题。

第一问：为什么 recover 不到？因为这不是 panic。runtime 检测到并发冲突时调用的是 `throw`（不可恢复的致命错误），直接打印栈、`exit(2)`。panic/recover 是语言层的异常机制，throw 是运行时的自杀机制——两者的区别就是「程序可以带着错误继续跑吗」，map 并发损坏的回答是「不能」，因为 map 的内部结构可能已经被破坏，继续跑比死更危险。这个设计后面还会展开。

第二问：谁在写？fatal error 的输出会打印所有 goroutine 的栈，搜「map」相关帧：`runtime.mapassign`（写）出现在刷新协程，`runtime.mapaccess2`（读）出现在请求协程——两边栈都有了，冲突对一目了然。如果栈被截断，`GOTRACEBACK=all` 环境变量可以拉满输出。

第三问：为什么「整体替换」也炸？因为 `rules = fresh` 替换的是**变量指向**，而 `rules[userID]` 的读操作内部至少分三步：取变量当前指向的 hmap、按 hash 定位桶、读桶内数据。读协程刚取到旧 map 的指针，还没来得及读桶；刷新协程此刻不止替换了变量——旧 map 在赋值前还经历了 `buildFromDB` 期间的持续写入。只要「读到的 map」与「被写的 map」是同一个，炸的就是同一瞬间。把写入收敛到只发生在「从未被别人读过的新 map」上，崩溃才消失——这是事故二修复方案的地基，先卖个关子。

事故二的排查靠 pprof 的 mutex profile 和一次压测对比。`runtime.SetMutexProfileFraction(1)` 打开后 `go tool pprof` 分析，热点不在业务代码，在 `runtime_canSpin` 和 RWMutex 内部——大量读协程在 `RLock` 上自旋、排队。读完第三节的 RWMutex 实现你就明白：**读锁不是免费的**，每个 `RLock` 都要对共享的 readerCount 做原子加，几万个协程反复打同一个缓存行，锁还没「持住」， cacheline 已经在核间弹来弹去了。这叫锁的**争用开销**，它和临界区长短无关，和并发读的数量成正比。

## 三、底层原理：hmap 的账本，与「为什么宁可杀死进程」

map 的运行时结构（Go 1.24 之前是哈希桶方案，1.24 起换成了 Swiss Table，思想相通，这里讲经典版）：

```go
type hmap struct {
    count     int            // 元素个数
    flags     uint8          // 状态标志，含 hashWriting 位
    B         uint8          // 桶数 = 2^B
    buckets   unsafe.Pointer // 桶数组
    oldbuckets unsafe.Pointer // 增量扩容时的旧桶
    ...
}
```

每个桶（bmap）存 8 个键值对，外加 8 个 tophash（hash 高 8 位快筛）和一个溢出桶指针。负载因子超过 6.5 触发翻倍扩容，溢出桶过多触发等量扩容（整理碎片）——**扩容是增量的**，每次读写顺带搬迁一小批桶，全程保持 hmap 处于「新旧并存、逐步迁移」的中间态。

现在回答灵魂问题：map 为什么不允许并发读写？因为增量扩容意味着**任意一次写都可能触发内部结构的重排**——桶在搬、指针在换、B 在变。两个协程同时写，一次搬迁交错就可能丢键、断链，甚至造出环（Go 早期版本真出现过并发 map 死循环吃满 CPU 的事故）。与其让损坏静默扩散，runtime 选择了最激进的自保：`hmap.flags` 里有个 `hashWriting` 位，写操作开始时置位、结束时清除，任何操作（读或写）发现这个位已被置上，立刻 `throw`。**杀死进程不是 Go 团队偷懒没做并发安全，而是刻意为之的 fail-fast**：结构损坏的 map 继续服务，比崩溃糟糕得多。这也解释了 recover 为什么拦不住——语言层的恢复机制不该为「运行时状态已不可信」背书。

接下来拆事故二的 RWMutex。它的实现依赖两个计数：readerCount（读协程数量）和 readerWait（写者到达后被拦下的读者数）。写者到达时把 readerCount 减成负数，此后所有新读者阻塞排队；已有读者退出后写者拿锁。三个推论：①读路径每次 RLock/RUnlock 是对共享计数的一对原子操作，并发读越多，cacheline 争用越凶——这就是 P99 40ms 的来源，**争用发生在「拿锁」而不是「用锁」**；②持续不断的读者流理论上能把写者饿死，Go 1.9 加了饥饿模式兜底，但兜底只是不再饿死，写延迟照样抖；③读多写少的场景里，正确的解法不是换锁，而是**把读路径上的同步原语彻底去掉**。

这就引出 sync.Map。它的结构是两个 map 的组合：

```go
type Map struct {
    mu     Mutex
    read   atomic.Pointer[readOnly] // 无锁读的快照，entry 指针原子可变
    dirty  map[any]*entry           // 锁保护的"全量新版"
    misses atomic.Int64             // read 缺失计数
}
```

读路径：先查 `read`（一次原子 load + 常规 map 读），命中则直接返回，全程无锁；miss 则加锁查 `dirty` 并累加 misses，misses 攒到 dirty 长度时把 dirty 整体提升为新的 read。写路径：更新 read 中已有的 key 可以只改 entry 指针（原子 CAS），新增 key 才要锁 dirty。所以它的适用边界非常清晰：**key 集合稳定、读远多于写**时接近零成本；写新 key 频繁时每次都要锁 dirty 加双份记账，反而比 Mutex+map 更慢。

## 四、正确姿势：一张决策表 + 两段代码

并发 map 选型决策表（按命中率从高到低排）：

| 场景 | 方案 | 读路径成本 |
|---|---|---|
| 读极多、写极少、key 集合稳定 | sync.Map | 原子 load，~20ns |
| 读多写少、整体快照可接受 | RWMutex + atomic.Pointer 存整个 map（COW） | 原子 load，最快 |
| 读写均衡或写多 | 分片 Mutex（16~64 片）或单 Mutex | 低争用锁 |
| 单协程或外部已保证互斥 | 裸 map | 零 |

事故一 + 事故二的最终修复用的是 COW（copy-on-write）方案，它对我们「每五分钟全量重建」的刷新模式是量身定做：

```go
type RuleCache struct {
    v atomic.Pointer[map[int64]Rule] // 指向"不可变"快照
}

func (c *RuleCache) Get(userID int64) (Rule, bool) {
    m := *c.v.Load()               // 读路径：一次原子 load，零锁
    r, ok := m[userID]
    return r, ok
}

func (c *RuleCache) Refresh(fresh map[int64]Rule) {
    c.v.Store(&fresh)              // 写路径：构建完毕后原子换指针
}
```

它成立的三个前提值得默念：①fresh 这个 map 构建完成后**永不再写**（不可变快照）；②读者拿到的永远是某一时刻的完整一致快照（读不到「改了一半」的中间态）；③容忍分钟级的数据新鲜度。刷新频率高、或需要增量单 key 更新的场景，退回 sync.Map 或分片锁。另外两个细节：分片锁的片数取 2 的幂，按 `hash(key) & (shards-1)` 分片，让热 key 天然打散；`len(map)` 和 `range` 同样要过锁——它们也在读，这是事故一教训的延伸。

## 五、数据说话

压测环境：map 内 10 万条数据，8 核，读写比 1000:1，读接口压 3 万 QPS，写入按每秒 30 次全量刷新。四组实现的读接口 P99 与写耗时如下（数字量级可复现，绝对值因机器而异）：

| 实现 | 读 P99 | 写(刷新)耗时 | 备注 |
|---|---:|---:|---|
| 裸 map | — | — | 压不到 1 分钟 fatal error |
| Mutex + map | 38 ms | 0.9 ms | 读写同锁，读全被写串行化 |
| RWMutex + map（事故二版） | 40 ms | 1.2 ms | readerCount 争用主导 |
| sync.Map | 6 ms | 1.5 ms | 新 key 写路径无优势，本场景无新增 |
| COW + atomic.Pointer | 3 ms | 2.1 ms | 读最快，刷新时多付一份构建成本 |

三个结论。第一，RWMutex 并不天然快于 Mutex：读写比 1000:1 的场景下两者 P99 打平甚至更差，争用在 readerCount 上，不在临界区里——**「读多写少所以用 RWMutex」是面试答案，不是工程答案**。第二，COW 的读 P99 只有 RWMutex 的十三分之一，代价是刷新多花 1ms 且峰值多驻留一份 map 内存（10 万条 × 每条 200B ≈ 20MB，每 5 分钟一次，完全可接受）。第三，sync.Map 在本场景表现良好但不是冠军，它的甜区是「key 稳定 + 高频读 + 偶发单 key 写」；我们的刷新是「整体换血」，COW 才是对口科室。

## 六、面试怎么答

**Q1：map 的底层实现？**
经典版：hmap + bmap 桶数组，每桶 8 槽 + tophash 快筛 + 溢出桶；负载因子 6.5 触发翻倍扩容，溢出桶过多触发等量扩容，均为增量搬迁；1.24 起默认改用 Swiss Table（更小的元数据、SIMD 友好的批量查找）。能说出「1.24 换了实现」的凤毛麟角。

**Q2：为什么 map 不允许并发读写？为什么设计成不安全的？**
三层：①增量扩容使任意写都可能重排内部结构，并发写会丢数据甚至成环；②runtime 用 flags 的 hashWriting 位主动检测，冲突即 throw——这是刻意的 fail-fast，不是没做；③为什么不给 map 内置锁——90% 的使用场景没有并发（局部变量、单协程），内置锁让所有人付出不必要的热身成本，Go 的哲学是「要安全自己包一层」。补一句「所以 sync.Map 才存在，它不是 map 的替代品，是并发场景专用件」收尾。

**Q3：concurrent map writes 能被 recover 吗？**
不能。它由 runtime.throw 触发，不走 panic 链，直接 exit(2)。panic/recover 是语言层机制，前提是运行时状态可信；map 结构已损坏时继续执行更危险。关联考点：数组越界同理（部分场景 throw），以及「goroutine 泄漏是可 recover 的 panic 都救不了的慢性病」。

**Q4：sync.Map 的原理与适用场景？**
read/dirty 双 map + misses 提升。读路径原子 load 无锁；写已有 key 走原子 CAS 改 entry，新 key 锁 dirty；miss 攒够整体提升。适用：读远多于写、key 集合稳定、可容忍 len/range 不快照；不适用：写新 key 频繁（双份记账反而慢）、需要精确 len、需要快照遍历。每个适用/不适用都给一个业务例子。

**Q5：map 遍历顺序为什么是随机的？**
两层：实现上，遍历起点桶和槽都随机化；动机上，一是防止用户依赖插入顺序（哈希表顺序无保证，写死会阻碍运行时演进），二是早期还兼防哈希碰撞 DoS。工程含义：需要有序就显式排序 key，或改用有序结构。

## 七、落地清单

- 任何被多协程访问的 map，读写（含 len、range）必须有同步措施——「我只是读」不豁免
- 默认选型顺序：key 稳定读极多 → sync.Map 或 COW；写多/均衡 → 分片 Mutex；别把 RWMutex 当读优化银弹
- COW 方案的铁律：快照构建完成后不可变；刷新频率与数据新鲜度需求要能对账
- 线上见 `fatal error: concurrent map ...`：不要只重启，先从 fatal 栈里找出读写冲突对，写方通常是元凶
- mutex/block profile 常开（fraction=1 的开销可忽略），P99 劣化先看锁争用再怀疑业务
- 新服务把「map 并发访问」写进 code review 检查项：搜 `map[` 出现的函数，核对调用方并发约束
- 记住这组数字防拍脑袋：原子 load ~20ns，无争用 Mutex ~18ns，RWMutex 在万级并发读时 P99 劣化 5 倍不是 bug 是机理

到这里，第一批「并发与数据结构五连」完结。下一批进入原理内功篇：interface 的 nil 陷阱、defer/panic/recover 的边界、GC 与逃逸分析、sync 全家桶。它们是面试里区分「写过 Go」和「理解 Go」的分水岭。
