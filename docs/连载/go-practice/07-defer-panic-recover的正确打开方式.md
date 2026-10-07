---
title: Go 语言实战（七）：defer/panic/recover 的正确打开方式
slug: go-practice-07-defer
categories: golang
---

# Go 语言实战（七）：defer/panic/recover 的正确打开方式

defer 是 Go 最优雅的语法糖，也是最容易写出「看起来没问题」代码的语句。这一章的两起事故：一起让文件句柄在循环里悄悄堆积、最终 fd 打满（第三章的 fd 事故换了个凶手重演）；一起让进程被一个「明明 recover 过」的 panic 干掉。它们分别对应 defer 的两个本质：它是一条**链**，以及 panic 的传播有**边界**。

## 一、事故现场：循环里的 defer，和越不过 goroutine 的 recover

先看第一起。一个数据导入服务要逐行处理上传的 CSV，每一行都要落一份原始文件做审计，代码的直觉写法：

```go
func importRows(rows []Row) {
    for i, row := range rows {
        f, err := os.Create(fmt.Sprintf("/tmp/audit/%d.csv", i))
        if err != nil {
            log.Printf("create: %v", err)
            continue
        }
        defer f.Close() // 想当然：函数结束会关
        writeAudit(f, row)
    }
}
```

`defer` 的语义是「**函数返回前**执行」，不是「代码块结束时执行」。循环 10 万行，就注册 10 万个 `f.Close()`，全部压在 `importRows` 的 defer 链上——文件句柄一个不关，进程 fd 一路涨到上限，后续所有 Create 失败，审计功能整个瘫痪。而这个 bug 最阴险的地方是：功能完全正确（数据都导入了），只是资源在漏，往往要跑上几天才炸。

第二起更刺激。我们的 HTTP 服务里有个异步任务 goroutine，入口套了 recover，自以为万无一失：

```go
func startWorker() {
    go func() {
        defer func() {
            if r := recover(); r != nil {
                log.Printf("worker panic: %v", r)
            }
        }()
        runPipeline() // 内部又起了一批 go doStep(...)
    }()
}

func doStep(step int) {
    // panic(nil pointer) —— 但这里没有任何 recover
}
```

某天 `doStep` 因为脏数据 panic 了。worker 入口的 recover 没接到（接不到，后面讲为什么），panic 沿着 doStep 自己的 goroutine 往上炸穿整个 goroutine，Go 运行时对「未被 recover 的 panic」的处置是：打印栈、**退出整个进程**。凌晨两点，所有实例被同一份脏数据依次击落。

## 二、排查过程：fd 曲线与 panic 尸检

事故一的排查几乎照抄第三章的路径，但最后一步不同：goroutine profile 显示 goroutine 数量正常（没有泄漏），fd 曲线却持续爬升。goroutine 不漏、fd 在漏，答案只剩「资源注册了没释放」。grep `defer` 扫出循环里那行，`go vet` 其实也能给出警告（loopclosure 类工具对 defer-in-loop 有提示），对账即中。

事故二的排查是「尸检」：进程的崩溃日志里有完整 panic 栈——panic 时 Go 会打印**当前 goroutine 的完整调用栈**加一句 `exit status 2`。栈里只有 `doStep` 与它上面的 runtime 帧，看不到任何 recover 相关帧。对照 recover 的语义「**只能捕获当前 goroutine 当前 defer 链上的 panic**」，答案就在栈的形状里：doStep 起在另一个 goroutine，worker 入口那个 recover 挂在另一个栈上，鞭长莫及。

这两个案例共同修正一个新手心智模型：panic 不是异常对象在「飞行」，而是 goroutine 栈的展开（unwind）过程——runtime 从 panic 点沿**当前 goroutine** 的 defer 链逐个执行，链走完还没人 recover，进程死亡。栈与栈之间没有任何传播通道。

## 三、底层原理：defer 链与 panic 的展开

**defer 的运行时表示是挂在 G 上的一个链表**。每个 `_defer` 结构记录了函数指针、参数和链上下一个节点的位置；函数 return 时，runtime 逆序遍历这条链执行。三种实现形态（性能差异巨大，面试加分点）：

1. **堆分配（Go 1.12 及以前是唯一形态）**：`_defer` 对象分配在堆上，开销 ~50ns+一次堆分配；
2. **栈分配（Go 1.13）**：defer 对象放栈上，链仍走 runtime，~35ns；
3. **开放编码（open-coded，Go 1.14+，默认生效）**：函数里 defer 数量少（≤8）且不在循环里时，编译器直接把 deferred 调用内联成 return 前的普通代码加一个位图标记，开销降到 ~1ns，几乎与手写裸调用无异。

第三种形态有个关键限定：**循环里的 defer 会退化**（编译期无法静态展开），回到栈/堆分配——这就是事故一除了 fd 泄漏之外，还白付了一份 defer 注册开销的原因。条件允许时把循环体重构成函数，既修泄漏又吃满 open-coded 红利。

**panic 的展开机制**。`panic(v)` 做三件事：把 v 挂到当前 G；从当前函数开始，逐帧执行各帧 defer 链（这一步叫 unwind，defer 里的代码照常执行，这就是 defer 能做清理的原因）；如果某个 defer 里调用了 `recover()` 且当前 G 确实处于 panic 状态，展开停止，程序从 recover 的调用者处继续；整条链走完没人 recover → `fatal panic`，打印全栈、`exit(2)`。三个工程边界从机制里直接推出来：

- recover 必须写在 **deferred 函数**里直接调用（`defer func(){ if r := recover(); ... }()`），写在普通代码里恒返回 nil；
- panic 跨不过 **goroutine 边界**——每个 `go` 语句都是一棵独立的栈，各自的 panic 各自recover；
- recover 之后再 panic，新的 panic 取代旧的，栈展开从当前 defer 链继续——「吞掉再包一层错误」要显式做。

还有一个高频考点顺手讲透：**defer 与返回值的关系**。`return x` 不是原子的，它是「把 x 赋给返回值槽 → 执行 defer 链 → 真正 RET」三步。无名返回值时 defer 改不动结果；**命名返回值**时 defer 里改的就是那个槽本身——`defer func(){ ret++ }()` 会改变函数返回值。这个特性是「defer 里统一包装 error」写法的实现基础，也是面试最爱挖的坑。

## 四、正确姿势：defer 的四条纪律与 panic 的使用边界

**纪律一：循环体内的 defer 必须消除**。两个改法，按语义选：

```go
// 改法 A：循环体提函数，defer 随函数结束执行（推荐，每行文件及时关闭）
for i, row := range rows {
    if err := writeOne(i, row); err != nil { /* ... */ }
}
func writeOne(i int, row Row) error {
    f, err := os.Create(...)
    if err != nil { return err }
    defer f.Close()
    return writeAudit(f, row)
}

// 改法 B：确实需要"函数级"defer 时，把 Close 挂到显式清理切片
closers := make([]func(), 0, len(rows))
defer func() { for _, c := range closers { c() } }()
for i, row := range rows {
    f, _ := os.Create(...)
    closers = append(closers, f.Close)
    // ...
}
```

**纪律二：每个 goroutine 的入口必须有 recover**。这不是风格，是进程存活问题。团队里直接封一个模板：

```go
func SafeGo(name string, fn func(ctx context.Context)) {
    go func() {
        defer func() {
            if r := recover(); r != nil {
                log.Printf("panic in %s: %v\n%s", name, r, debug.Stack())
            }
        }()
        fn(ctx)
    }()
}
```

配合另一条铁律：**panic 在 goroutine 里炸，炸的是整个进程**——所以「这个 goroutine 挂了无所谓」的认知是错的，任何一针没有 recover 的 `go` 都是把进程押给最脏的那份数据。

**纪律三：panic 只用于两种场景**——程序自身的不可恢复错误（初始化失败、内部不变量被破坏）与「必须打断多层调用的真正异常路径」；除此之外一律 error 传播。跨包 API 用 panic 表达可预期失败是设计错误，`regexp.MustCompile` 那种 Must 前缀是给「启动期就该死的配置错误」准备的特例，不是通例。

**纪律四：defer 写「开资源」的对面，且参数立即求值**。`defer f.Close()` 的 `f` 在 defer 语句时就定了；`defer mu.Unlock()` 永远成对；`defer wg.Done()` 必须与 `wg.Add` 一一对应且 Add 在 go 之前——这三对搭档的顺序错误，是 defer 类事故里除循环 defer 外的第二高发。

## 五、数据说话

| 操作 | 耗时（Go 1.22） | 说明 |
|---|---:|---|
| open-coded defer（函数级，非循环） | ~1 ns | 与手写调用几乎等价 |
| 栈分配 defer | ~35 ns | 循环内、defer>8 时退化至此 |
| 堆分配 defer（含 cgo 的函数） | ~60 ns + 1 次分配 | 老形态，现代代码很少踩到 |
| panic + recover 一次完整往返 | ~2~3 µs | 含栈展开与 defer 链执行 |
| 手写 `if err != nil` 判断 | <1 ns | 对照组 |

三个读数。第一，Go 1.14 之后「defer 有性能开销所以循环里手写 Close」这类顾虑基本过时——函数级 defer 一纳秒，性能账不再是理由，**正确性（泄漏）才是**。第二，panic+recover 的微秒级成本意味着「用 panic 做控制流」在热路径上不可接受，但作为「异常路径保险丝」完全无感——每个请求一次 recover 的量级是纳秒到微秒，抵不过一次磁盘 IO 的万分之一。第三，把事故一的循环 defer 修正后顺手收益：10 万行的注册开销从 ~6ms（堆形态）降到 ~0.1ms，fd 从 10 万峰值降到个位数。

## 六、面试怎么答

**Q1：defer 的执行顺序和参数求值时机？**
LIFO 逆序执行；defer 语句执行时**立即**对函数与实参求值并快照（`defer fmt.Println(i)` 记住的是当时的 i），延迟的只是调用本身。要拿到最新值就传指针或用闭包。加分项：命名返回值可被 defer 修改，因为 return 分三步、defer 卡在赋值与 RET 之间。

**Q2：defer 的性能演进？**
三段史：1.12 堆分配 ~50ns → 1.13 栈分配 ~35ns → 1.14 open-coded ~1ns（条件：defer ≤8 且不在循环）。主动补一句「所以循环内 defer 会退化」，把考点带进工程坑。

**Q3：panic 的传播规则？recover 的边界？**
沿当前 goroutine 的 defer 链逐帧展开，defer 正常执行；recover 必须在 deferred 函数里直接调用；跨 goroutine 不传播——子 goroutine panic 必须在自己的入口 recover，否则进程 exit(2)。这题必须带 SafeGo 模板收尾，光背规则不给方案等于没答。

**Q4：什么场景该 panic，什么场景该 error？**
内部不变量被破坏、初始化失败（Must 系）用 panic——程序已不可信；一切可预期的失败（输入、IO、依赖）用 error。标准库的示范：json.Unmarshal 返回 error，编译期配置 MustCompile panic。

**Q5：循环里 defer 有什么问题？**
两层：资源层面，defer 绑定函数生命周期，循环内注册全憋到函数结束，fd/锁/连接堆积泄漏；性能层面，open-coded 退化回堆/栈分配。修复两法（提函数 / 收集 closers）都要能写。

## 七、落地清单

- `defer` 禁止出现在 for 循环体内；review 见到即打回（提函数或收集 closers）
- 每个裸 `go` 语句的函数体首行必须是 recover 模板；团队统一用 SafeGo 封装
- panic 白名单：初始化 Must 系、内部不变量断言；其余一律 error
- 资源三对搭档写死顺序：Create→defer Close、Lock→defer Unlock、Add→go→Done
- 命名返回值 + defer 修改返回值的模式集中封装（如统一 error 包装器），禁止散写
- 崩溃日志必须留全栈（`debug.Stack()` 进日志），panic 尸检是定位这类事故的唯一入口
- 上线前跑一遍 `go vet`（loopclosure/framepointer）+ 压测看 fd 曲线——defer 类泄漏没有 error 可看，只能看曲线

下一章我们从语法层下潜到运行时：GC 的三色标记与混合写屏障，以及那份「分配的账单」——内存逃逸分析。
