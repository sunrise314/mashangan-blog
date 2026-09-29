#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Java 面试八股题库 - 原创题目批量发布脚本
=========================================
为 24 个专题各发布原创面试题（题干 + 答案，Markdown 格式）。
题目内容为原创，不搬运任何第三方受版权保护的题库。

用法：
  python seed_questions.py            # 试运行
  HALO_PAT=pat_xxx python seed_questions.py --apply
"""
import os
import sys

import requests

try:
    import markdown as md_lib
except ImportError:
    print("缺少依赖：python -m pip install markdown")
    sys.exit(1)


def render_markdown(text: str) -> str:
    return md_lib.markdown(text, extensions=["fenced_code", "tables", "sane_lists"], output_format="html")


HALO_URL = os.environ.get("HALO_URL", "http://49.235.136.65:8090").rstrip("/")
HALO_PAT = os.environ.get("HALO_PAT", "").strip()
APPLY = "--apply" in sys.argv

CONTENT_API = "/apis/api.content.halo.run/v1alpha1"
CONSOLE_API = "/apis/api.console.halo.run/v1alpha1"


class HaloAdmin:
    def __init__(self, base: str, pat: str):
        self.base = base
        self.s = requests.Session()
        headers = {"Accept": "application/json", "Content-Type": "application/json"}
        if pat:
            headers["Authorization"] = f"Bearer {pat}"
        self.s.headers.update(headers)

    def list_post_slugs(self):
        r = self.s.get(f"{self.base}{CONTENT_API}/posts?size=1000")
        r.raise_for_status()
        return {p["spec"]["slug"] for p in r.json()["items"]}

    def create_and_publish_post(self, title, slug, markdown, category_name):
        import uuid
        post_name = f"post-{slug}-{uuid.uuid4().hex[:8]}"
        payload = {
            "post": {
                "apiVersion": "content.halo.run/v1alpha1",
                "kind": "Post",
                "metadata": {"name": post_name},
                "spec": {
                    "title": title, "slug": slug,
                    "categories": [category_name], "tags": [], "cover": "",
                    "excerpt": {"raw": "", "autoGenerate": True},
                    "publish": False, "visible": "PUBLIC", "deleted": False,
                    "pinned": False, "allowComment": True, "priority": 0,
                },
            },
            "content": {"raw": markdown, "content": render_markdown(markdown), "rawType": "Markdown"},
        }
        r = self.s.post(f"{self.base}{CONSOLE_API}/posts", json=payload)
        if r.status_code not in (200, 201):
            return False, f"create {r.status_code} {r.text[:150]}"
        p = self.s.put(f"{self.base}{CONSOLE_API}/posts/{post_name}/publish")
        if p.status_code != 200:
            return False, f"publish {p.status_code} {p.text[:150]}"
        return True, post_name


# ============ 原创题库（按专题分组） ============
# 每道题：slug, title, content(Markdown 答案)
QUESTIONS = {

# ---------- Java 基础 ----------
"java-basic": [
{"slug":"jb-int-vs-integer","title":"int 和 Integer 有什么区别？","content":"""## 一、本质区别

- **int** 是 Java 的 8 种基本数据类型之一，直接存数值，占 4 字节，默认值 0。
- **Integer** 是 int 的包装类，是对象，存的是指向堆中对象的引用，默认值 null。

## 二、自动装箱与拆箱

```java
Integer a = 10;          // 自动装箱：Integer.valueOf(10)
int b = a;               // 自动拆箱：a.intValue()
```

## 三、Integer 缓存

`Integer.valueOf()` 对 -128~127 的值做了缓存，范围内返回同一对象，超出则 new 新对象：

```java
Integer x = 127, y = 127;
x == y;   // true（缓存命中，同一对象）
Integer m = 128, n = 128;
m == n;   // false（超出缓存，两个对象）
```

因此比较 Integer 的值必须用 `equals()`，不能用 `==`。

## 四、使用建议

- 方法局部变量、循环计数用 int，性能更好。
- 集合泛型、可为 null 的字段用 Integer。
- 数据库数值列映射到实体类时，优先用包装类以区分 null 与 0。"""},

{"slug":"jb-string-immutable","title":"String 为什么是不可变的？","content":"""## 一、不可变的体现

String 内部用 `final char[]`（JDK 9 后是 `byte[]`）存储字符，且没有提供修改内部数组的方法，一旦创建内容就不能改。所谓"修改"其实是创建新对象。

## 二、设计原因

1. **字符串常量池复用**：相同字面量共享同一对象，节省内存。
2. **线程安全**：不可变对象天然线程安全，多线程下无需同步。
3. **hashCode 缓存**：String 的 hashCode 在首次计算后缓存，作为 HashMap 键时性能稳定。
4. **安全性**：作为参数传递时不会被意外篡改，如数据库连接串、文件名。

## 三、注意事项

```java
String s = "hello";
s += " world";   // 不是修改原对象，而是新建 "hello world"
```

频繁拼接字符串会产生大量临时对象，应使用 StringBuilder。"""},

{"slug":"jb-final-keyword","title":"final 关键字有哪些用法？","content":"""## 一、修饰类

`final` 修饰的类不能被继承，如 String、Math。常用于工具类或不希望被扩展的类。

## 二、修饰方法

`final` 方法不能被子类重写，防止子类改变父类行为。构造方法隐式为 final。

## 三、修饰变量

- **成员变量**：必须在声明时、构造方法或初始化块中赋值，赋值后不可改。
- **局部变量**：使用前必须赋值，赋值后不可改。
- **引用变量**：引用本身不可变，但引用指向的对象内容可变。

```java
final List<String> list = new ArrayList<>();
list.add("a");        // 合法：对象内容可变
list = new ArrayList<>(); // 非法：引用不可重新指向
```

## 四、编译期常量

`final` + 基本类型/String + 编译期可知的值，编译器会做常量内联优化。"""},

{"slug":"jb-overload-vs-override","title":"重载（overload）和重写（override）的区别？","content":"""## 一、定义

- **重载**：同一个类中，方法名相同，参数列表（类型/个数/顺序）不同。与返回值无关。
- **重写**：子类重新实现父类的方法，方法签名（名称、参数、返回值）必须一致。

## 二、对比

| 维度 | 重载 | 重写 |
|------|------|------|
| 位置 | 同类 | 父子类 |
| 参数列表 | 必须不同 | 必须相同 |
| 返回值 | 可不同 | 相同或协变 |
| 权限 | 可不同 | 不能更严格 |
| 绑定时机 | 编译期（静态绑定） | 运行期（动态绑定） |

## 三、重写的限制

- 父类方法不能是 final、static、private。
- 子类方法权限不能比父类更严格（public → protected 不行）。
- 不能抛出比父类更宽泛的受检异常。"""},

{"slug":"jb-interface-vs-abstract","title":"接口和抽象类有什么区别？","content":"""## 一、核心区别

| 维度 | 抽象类 | 接口 |
|------|--------|------|
| 关键字 | abstract class | interface |
| 继承 | 单继承 | 多实现 |
| 构造方法 | 有 | 无 |
| 成员变量 | 任意 | 仅 public static final |
| 方法实现 | 可有抽象/具体方法 | 默认方法、静态方法（JDK 8+） |
| 设计目的 | "is-a" 共性抽取 | "can-do" 行为规范 |

## 二、使用选择

- 多个类有共同属性和状态 → 抽象类（如 Animal）。
- 定义行为契约、支持多态组合 → 接口（如 Runnable、Comparable）。
- 需要多重继承 → 接口。

## 三、JDK 8 之后

接口支持 `default` 方法（有默认实现）和 `static` 方法，缩小了与抽象类的差距，但接口仍不能持有实例状态。"""},
],

# ---------- Java 集合 ----------
"java-collection": [
{"slug":"jc-arraylist-vs-linkedlist","title":"ArrayList 和 LinkedList 的区别？","content":"""## 一、底层结构

- **ArrayList**：基于动态数组，连续内存存储。
- **LinkedList**：基于双向链表，节点分散存储。

## 二、性能对比

| 操作 | ArrayList | LinkedList |
|------|-----------|------------|
| 随机访问 | O(1) | O(n) |
| 尾部追加 | O(1)（均摊） | O(1) |
| 中间插入/删除 | O(n)（需移动元素） | O(1)（定位后） |
| 内存占用 | 紧凑，有扩容浪费 | 每个节点额外存前后指针 |

## 三、使用场景

- 查多改少、需要随机访问 → ArrayList。
- 频繁在头部/中间增删 → LinkedList。
- 实际开发中 ArrayList 占绝大多数，除非明确需要链表特性。

## 四、扩容机制

ArrayList 默认初始容量 10，扩容时新容量 = 旧容量 × 1.5，数组拷贝用 `Arrays.copyOf`。"""},

{"slug":"jc-hashmap-principle","title":"HashMap 的底层实现原理？","content":"""## 一、数据结构

JDK 1.8 后 HashMap = 数组 + 链表 + 红黑树。

- 数组是主体，通过 `(n-1) & hash` 定位桶。
- 哈希冲突时，同桶元素挂成链表。
- 链表长度 ≥ 8 且数组长度 ≥ 64 时，链表转红黑树；节点数 ≤ 6 时转回链表。

## 二、put 流程

1. 计算 key 的 hash（`h ^ (h >>> 16)` 扰动）。
2. 定位桶：`(table.length - 1) & hash`。
3. 桶为空 → 直接放入；不为空 → 遍历链表/树，key 相同则覆盖，否则尾插。
4. 元素数超过阈值（容量 × 负载因子 0.75）→ 扩容为 2 倍，rehash。

## 三、线程安全

HashMap 非线程安全。并发下可能数据丢失或死循环（JDK 1.7 头插法）。需要线程安全用 ConcurrentHashMap。"""},

{"slug":"jc-hashmap-concurrent","title":"ConcurrentHashMap 如何保证线程安全？","content":"""## 一、JDK 1.7：分段锁

内部是 Segment 数组，每个 Segment 是一个小 HashMap，put 时只锁当前 Segment，并发度 = Segment 数（默认 16）。

## 二、JDK 1.8：CAS + synchronized

- 数组节点为空时，用 CAS 插入，无锁。
- 节点不为空时，synchronized 锁当前桶头节点。
- 扩容时多线程协作迁移数据。

## 三、与 Hashtable 对比

| | ConcurrentHashMap | Hashtable |
|--|-------------------|-----------|
| 锁粒度 | 桶级 | 整个表 |
| 性能 | 高 | 低 |
| null 键/值 | 不允许 | 不允许 |

## 四、为什么不允许 null

因为 `get` 返回 null 时无法区分是"key 不存在"还是"value 就是 null"，并发场景下会产生歧义。"""},

{"slug":"jc-set-impl","title":"HashSet 和 TreeSet 的区别？","content":"""## 一、底层实现

- **HashSet**：内部是 HashMap，元素作为 HashMap 的 key，value 是固定 Object。
- **TreeSet**：内部是 TreeMap，基于红黑树。

## 二、对比

| 维度 | HashSet | TreeSet |
|------|---------|---------|
| 有序性 | 无序 | 自然排序或定制排序 |
| 元素要求 | 重写 equals/hashCode | 实现 Comparable 或传入 Comparator |
| 时间复杂度 | O(1) | O(log n) |
| null 元素 | 允许一个 | 不允许（除非 Comparator 支持） |

## 三、使用场景

- 只需去重、不关心顺序 → HashSet。
- 需要排序输出 → TreeSet。
- 保持插入顺序 → LinkedHashSet。"""},

{"slug":"jc-iterator-fail-fast","title":"什么是 fail-fast 机制？","content":"""## 一、定义

在用迭代器遍历集合时，如果集合结构被修改（增/删），迭代器会立即抛出 `ConcurrentModificationException`，这就是 fail-fast。

## 二、实现原理

集合内部维护 `modCount` 记录结构修改次数。迭代器创建时记录 `expectedModCount = modCount`，每次 `next()` 检查两者是否相等，不等则抛异常。

## 三、触发场景

```java
List<String> list = new ArrayList<>(Arrays.asList("a","b","c"));
for (String s : list) {
    list.remove(s);   // 抛 ConcurrentModificationException
}
```

## 四、解决方案

- 用 `Iterator.remove()` 删除。
- JUC 的 CopyOnWriteArrayList 是 fail-safe，遍历时修改不抛异常（读的是快照）。"""},
],

# ---------- MySQL ----------
"mysql": [
{"slug":"mysql-index-btree","title":"MySQL 索引为什么用 B+ 树？","content":"""## 一、B+ 树特点

- 非叶子节点只存索引，不存数据，单节点可容纳更多索引，树更矮。
- 叶子节点通过双向链表连接，范围查询效率高。
- 所有数据都在叶子节点，查询路径长度一致，性能稳定。

## 二、为什么不用 B 树

B 树非叶子节点也存数据，导致单节点索引少、树更高、磁盘 I/O 多；范围查询需要中序遍历，效率低。

## 三、为什么不用哈希

哈希等值查询 O(1)，但不支持范围查询、排序、模糊查询，数据库这些场景很多。

## 四、为什么不用红黑树

红黑树是二叉树，节点只有两个子节点，数据量大时树很高，磁盘 I/O 次数多。B+ 树是多叉，矮胖，适合磁盘存储。"""},

{"slug":"mysql-transaction-acid","title":"事务的 ACID 特性是什么？","content":"""## 一、原子性（Atomicity）

事务中的操作要么全部成功，要么全部失败回滚。由 undo log 保证：记录修改前的旧值，回滚时反向操作。

## 二、一致性（Consistency）

事务前后数据满足业务约束。由原子性、隔离性、持久性共同保证，是事务的最终目标。

## 三、隔离性（Isolation）

并发事务互不干扰。由锁机制和 MVCC 保证。

## 四、持久性（Durability）

事务提交后数据永久保存。由 redo log 保证：修改先写 redo log，即使宕机也能恢复。

## 五、InnoDB 实现

- undo log → 原子性
- redo log → 持久性
- MVCC + 锁 → 隔离性"""},

{"slug":"mysql-isolation-levels","title":"MySQL 的四种事务隔离级别？","content":"""## 一、四种级别（由低到高）

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
|---------|------|-----------|------|
| READ UNCOMMITTED | 可能 | 可能 | 可能 |
| READ COMMITTED | 否 | 可能 | 可能 |
| REPEATABLE READ（InnoDB 默认） | 否 | 否 | 否（间隙锁） |
| SERIALIZABLE | 否 | 否 | 否 |

## 二、问题解释

- **脏读**：读到其他事务未提交的数据。
- **不可重复读**：同一事务内两次读同一行，结果不同（被其他事务 update）。
- **幻读**：同一事务内两次范围查询，行数不同（被其他事务 insert/delete）。

## 三、InnoDB 默认 RR

通过 MVCC 解决不可重复读，通过间隙锁（Next-Key Lock）解决幻读。"""},

{"slug":"mysql-mvcc","title":"什么是 MVCC？","content":"""## 一、定义

MVCC（Multi-Version Concurrency Control，多版本并发控制），通过维护数据的多个历史版本，实现读写不阻塞，提高并发性能。

## 二、实现要素

1. **隐藏字段**：每行有 `trx_id`（创建该版本的事务 ID）和 `roll_pointer`（指向 undo log 中的上一版本）。
2. **undo log**：存储旧版本数据，形成版本链。
3. **Read View**：事务启动时生成的快照，记录活跃事务 ID 列表，用于判断版本可见性。

## 三、可见性规则

- 版本的 trx_id < Read View 中最小活跃 ID → 可见。
- 版本的 trx_id > Read View 中最大活跃 ID → 不可见。
- 在活跃列表中 → 不可见，沿版本链找上一版本。

## 四、效果

- RC 级别：每次 select 都生成新 Read View。
- RR 级别：只在第一次 select 生成 Read View，后续复用。"""},

{"slug":"mysql-slow-sql","title":"如何排查和优化慢 SQL？","content":"""## 一、排查步骤

1. 开启慢查询日志：`slow_query_log = ON`，`long_query_time = 1`。
2. 用 `explain` 分析执行计划，关注 type、key、rows、Extra。
3. 用 `show profile` 看各阶段耗时。

## 二、常见优化

- **索引**：给 where、join、order by 字段加合适索引，避免索引失效（函数、隐式转换、%前缀 like）。
- **查询**：避免 select *，用 limit 分页，减少回表（覆盖索引）。
- **表结构**：大表分表，冷热数据分离。
- **锁**：减少长事务，避免锁等待。

## 三、explain 重点

- type：system > const > eq_ref > ref > range > index > ALL，至少要 range。
- key：实际用到的索引。
- Extra：Using index（覆盖索引，好）；Using filesort / Using temporary（需优化）。"""},
],

# ---------- Redis ----------
"redis": [
{"slug":"redis-data-structures","title":"Redis 常见数据结构及使用场景？","content":"""## 一、String

最基础，存字符串/整数/二进制。场景：缓存、计数器（incr）、分布式锁（setnx）、限流。

## 二、List

双向链表。场景：消息队列（lpush/brpop）、最新列表。

## 三、Hash

键值对集合。场景：对象存储（用户信息）、购物车。

## 四、Set

无序去重集合。场景：标签、共同好友、抽奖（srandmember）。

## 五、ZSet

有序集合，每个元素带 score。场景：排行榜、延迟队列、带权重的任务。

## 六、高级结构

- HyperLogLog：基数统计，如 UV，误差约 0.81%。
- Bitmap：位运算，签到、在线状态。
- Geo：地理位置，附近的人。
- Stream：消息队列，支持消费者组。"""},

{"slug":"redis-persistence","title":"Redis 的持久化方式有哪些？","content":"""## 一、RDB（快照）

- 原理：在某个时间点把内存数据序列化写入 dump.rdb。
- 触发：save（阻塞）、bgsave（fork 子进程）、自动配置（save 900 1）。
- 优点：文件紧凑、恢复快。
- 缺点：可能丢失最后一次快照后的数据。

## 二、AOF（追加日志）

- 原理：把写命令追加到 appendonly.aof。
- 刷盘策略：always（每条）、everysec（每秒，默认）、no（系统决定）。
- 优点：数据更安全。
- 缺点：文件大、恢复慢。

## 三、混合持久化（Redis 4.0+）

AOF 重写时，先把内存数据以 RDB 格式写入文件开头，再把增量命令以 AOF 格式追加。兼顾恢复速度和数据安全。

## 四、选择

- 可以容忍丢失分钟级数据 → RDB。
- 数据重要、尽量不丢 → AOF（everysec）。
- 生产常用：混合持久化。"""},

{"slug":"redis-cache-penetration","title":"缓存穿透、击穿、雪崩及解决方案？","content":"""## 一、缓存穿透

查询不存在的数据，缓存和数据库都没有，请求全打到数据库。

解决方案：
- 缓存空值（设置较短过期时间）。
- 布隆过滤器，不存在的 key 直接拦截。

## 二、缓存击穿

热点 key 过期瞬间，大量并发请求打到数据库。

解决方案：
- 互斥锁（setnx），只有一个请求查库并回填缓存。
- 热点 key 永不过期，逻辑过期 + 异步刷新。

## 三、缓存雪崩

大量 key 同时过期或 Redis 宕机，请求全部打到数据库。

解决方案：
- 过期时间加随机值，避免集中过期。
- 多级缓存（本地缓存 + Redis）。
- Redis 高可用集群，熔断降级。"""},

{"slug":"redis-distributed-lock","title":"Redis 如何实现分布式锁？","content":"""## 一、基本实现

```
SET lock_key unique_value NX EX 30
```
- NX：key 不存在才设置。
- EX：过期时间，防止死锁。
- unique_value：释放锁时校验，防止误删别人的锁。

## 二、释放锁（Lua 脚本保证原子）

```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
end
return 0
```

## 三、Redisson 实现

- 可重入锁：维护计数器。
- 看门狗：业务未执行完自动续期（默认 30s，每 10s 检查）。
- 支持公平锁、读写锁、红锁。

## 四、注意事项

- 必须设置过期时间，防止持锁节点宕机导致死锁。
- 释放锁必须校验 value，避免误删。"""},

{"slug":"redis-single-thread","title":"Redis 为什么单线程还这么快？","content":"""## 一、核心原因

1. **纯内存操作**：数据在内存中，访问速度极快。
2. **I/O 多路复用**：用 epoll 处理大量并发连接，单线程也能处理高并发。
3. **避免上下文切换**：单线程没有线程切换开销，也没有锁竞争。

## 二、Redis 6.0 多线程

Redis 6.0 引入多线程，但只用于**网络 I/O 的读写**，命令执行仍是单线程。原因是瓶颈在网络 I/O 而非 CPU。

## 三、适用场景

- 适合高并发、数据量适中、延迟敏感的场景。
- 不适合大 key 操作（如 hgetall 百万级 hash），会阻塞主线程。"""},
],

# ---------- JVM ----------
"jvm": [
{"slug":"jvm-memory-structure","title":"JVM 运行时内存结构？","content":"""## 一、线程私有

- **程序计数器**：记录当前线程执行的字节码行号，唯一不会 OOM 的区域。
- **虚拟机栈**：每个方法调用创建一个栈帧，存局部变量、操作数栈、动态链接。栈深度过深抛 StackOverflowError。
- **本地方法栈**：为 native 方法服务。

## 二、线程共享

- **堆**：对象实例和数组，GC 主要区域，可分为新生代 + 老年代。
- **方法区（元空间）**：类信息、常量、静态变量、JIT 编译后的代码。JDK 8 后用本地内存（元空间）替代永久代。

## 三、直接内存

NIO 用的堆外内存，不受堆大小限制，但受物理内存限制，可能 OOM。"""},

{"slug":"jvm-gc-algorithms","title":"常见的垃圾回收算法？","content":"""## 一、判断对象是否可回收

- **引用计数法**：对象被引用计数 +1，为 0 则回收。无法解决循环引用。
- **可达性分析**：从 GC Roots 出发，不可达的对象可回收。GC Roots 包括栈中引用、静态变量、常量、JNI 引用。

## 二、回收算法

- **标记-清除**：标记存活对象，清除垃圾。缺点：内存碎片。
- **标记-复制**：内存分两块，存活对象复制到另一块，清空原块。缺点：内存利用率低。
- **标记-整理**：标记后把存活对象移到一端，清除边界外。无碎片，但移动开销大。
- **分代收集**：新生代用复制算法（对象朝生夕灭），老年代用标记-清除或标记-整理。

## 三、垃圾收集器

- Serial / ParNew / Parallel Scavenge（新生代，复制）
- CMS / G1 / ZGC（老年代或整堆）"""},

{"slug":"jvm-cms-vs-g1","title":"CMS 和 G1 的区别？","content":"""## 一、CMS

- 基于标记-清除算法。
- 以最短停顿时间为目标。
- 流程：初始标记（STW）→ 并发标记 → 重新标记（STW）→ 并发清除。
- 缺点：内存碎片、并发阶段消耗 CPU、浮动垃圾、可能 Concurrent Mode Failure 退化为 Serial Old。

## 二、G1

- 把堆分成多个 Region，每个 Region 可充当 Eden/Survivor/Old/Humongous。
- 整体标记-整理，局部复制，无碎片。
- 可预测停顿时间：根据每个 Region 的回收价值，优先回收垃圾多的 Region。
- 流程：初始标记 → 并发标记 → 最终标记 → 筛选回收（STW）。

## 三、选择

- 堆较小、追求低延迟 → CMS。
- 堆大（>4G）、可预测停顿 → G1（JDK 9 默认）。"""},

{"slug":"jvm-class-loading","title":"类加载过程是怎样的？","content":"""## 一、五个阶段

1. **加载**：通过类的全限定名获取二进制字节流，在内存中生成 Class 对象。
2. **验证**：校验字节码的合法性（魔数、版本、语义）。
3. **准备**：为类的静态变量分配内存并赋默认值（零值），final static 在编译期已赋值。
4. **解析**：把符号引用替换为直接引用。
5. **初始化**：执行 `<clinit>` 方法，给静态变量赋程序设定的初始值，执行静态代码块。

## 二、双亲委派模型

加载器收到请求先委托给父加载器，父加载器找不到才自己加载。

- 启动类加载器（Bootstrap）：rt.jar
- 扩展类加载器（Ext）：jre/lib/ext
- 应用类加载器（App）：classpath

好处：避免核心类被篡改，保证类的唯一性。

## 三、主动引用触发初始化

new 对象、访问静态变量、调用静态方法、反射、初始化子类时父类先初始化。"""},

{"slug":"jvm-oom-types","title":"常见的 OOM 有哪些？","content":"""## 一、Java heap space

堆内存不足，对象太多或内存泄漏。排查：dump 堆快照，用 MAT 分析大对象/泄漏点。

## 二、GC overhead limit exceeded

GC 耗时过长（98% 时间用于 GC 且回收不到 2% 内存）。通常是堆太小或内存泄漏。

## 三、Java.lang.StackOverflowError

栈深度过深，递归没有出口。

## 四、Metaspace

元空间不足，动态生成类太多（如大量反射、CGLIB）。调大 `-XX:MaxMetaspaceSize`。

## 五、Direct buffer memory

堆外内存不足，NIO 用的直接内存超限。

## 六、unable to create new native thread

线程数超过系统限制。减少线程数或调大系统限制。"""},
],

# ---------- Java 并发 ----------
"java-concurrent": [
{"slug":"jc-synchronized-vs-reentrantlock","title":"synchronized 和 ReentrantLock 的区别？","content":"""## 一、底层实现

- **synchronized**：JVM 级，字节码指令 monitorenter/monitorexit，依赖对象头的 Mark Word。
- **ReentrantLock**：JDK 级，基于 AQS（AbstractQueuedSynchronizer）实现。

## 二、对比

| 维度 | synchronized | ReentrantLock |
|------|-------------|---------------|
| 实现 | JVM | JDK |
| 释放 | 自动 | 需手动 unlock（finally） |
| 公平锁 | 非公平 | 可配置公平/非公平 |
| 可中断 | 不可 | lockInterruptibly |
| 超时 | 不支持 | tryLock(timeout) |
| 条件变量 | 一个 | 多个 Condition |

## 三、锁升级（synchronized）

无锁 → 偏向锁 → 轻量级锁 → 重量级锁，只能升级不能降级。

## 四、使用建议

简单同步用 synchronized（不易忘记解锁）；需要公平、可中断、超时、多条件时用 ReentrantLock。"""},

{"slug":"jc-thread-pool","title":"线程池的核心参数与工作流程？","content":"""## 一、七大核心参数

1. **corePoolSize**：核心线程数，即使空闲也保留。
2. **maximumPoolSize**：最大线程数。
3. **keepAliveTime**：非核心线程空闲存活时间。
4. **unit**：时间单位。
5. **workQueue**：阻塞队列。
6. **threadFactory**：线程工厂。
7. **handler**：拒绝策略。

## 二、工作流程

1. 线程数 < corePoolSize → 创建核心线程执行。
2. 线程数 ≥ corePoolSize → 任务入队。
3. 队列满 → 创建非核心线程（直到 maxPoolSize）。
4. 线程数 = maxPoolSize 且队列满 → 执行拒绝策略。

## 三、拒绝策略

- AbortPolicy：抛异常（默认）
- CallerRunsPolicy：调用者线程执行
- DiscardPolicy：直接丢弃
- DiscardOldestPolicy：丢弃队列最旧任务

## 四、为什么不建议用 Executors

FixedThreadPool/SingleThreadPool 队列无界可能 OOM；CachedThreadPool 线程数无界可能耗尽资源。应手动 new ThreadPoolExecutor。"""},

{"slug":"jc-aqs-principle","title":"AQS 的原理是什么？","content":"""## 一、定义

AQS（AbstractQueuedSynchronizer）是 JUC 锁的基础框架，用一个 int 状态变量（state）和一个 FIFO 等待队列实现同步。

## 二、核心

- **state**：同步状态，0 表示空闲，>0 表示被占用。
- **CLH 队列**：未获取锁的线程封装成 Node 入队，自旋 + 阻塞等待。

## 三、两种模式

- **独占模式**：同一时刻只有一个线程持有锁，如 ReentrantLock。
- **共享模式**：多个线程可同时持有，如 Semaphore、CountDownLatch。

## 四、获取锁流程

1. 尝试 CAS 修改 state。
2. 成功 → 获取锁。
3. 失败 → 封装 Node 入队，park 阻塞。
4. 前驱节点释放锁后 unpark 后继，后继重试。

## 五、子类实现

子类只需实现 `tryAcquire/tryRelease`（独占）或 `tryAcquireShared/tryReleaseShared`（共享），队列管理由 AQS 提供。"""},

{"slug":"jc-volatile","title":"volatile 关键字的作用？","content":"""## 一、两大作用

1. **可见性**：被 volatile 修饰的变量，一个线程修改后其他线程立即可见。实现：写入时立即刷新主内存，读取时从主内存重新加载。
2. **禁止指令重排**：在读写操作前后插入内存屏障。

## 二、不保证原子性

```java
volatile int count = 0;
count++;   // 不是原子操作，多线程下仍有问题
```

自增是 read-modify-write 三步，volatile 只保证可见不保证原子。需要原子性用 AtomicInteger 或加锁。

## 三、使用场景

- 状态标志位：`volatile boolean running = true`。
- 双重检查锁单例中的 instance 变量（防止指令重排导致拿到未初始化对象）。

## 四、与 synchronized 区别

volatile 不互斥，synchronized 互斥且保证可见和原子。"""},

{"slug":"jc-countdownlatch-cyclicbarrier","title":"CountDownLatch 和 CyclicBarrier 的区别？","content":"""## 一、CountDownLatch

- 一个或多个线程等待其他线程完成后再继续。
- 用计数器实现，countDown() 减 1，await() 等待归零。
- 一次性，归零后不能重置。

场景：主线程等待多个子任务完成。

## 二、CyclicBarrier

- 一组线程互相等待，全部到达屏障点后一起继续。
- 可循环使用，重置后可再次等待。
- 可传入 barrierAction，所有线程到达后执行。

场景：多线程计算后合并结果，可反复使用。

## 三、核心区别

| 维度 | CountDownLatch | CyclicBarrier |
|------|---------------|---------------|
| 等待方 | 主线程等子线程 | 子线程互相等 |
| 复用 | 一次性 | 可循环 |
| 回调 | 无 | 支持 barrierAction |"""},
],

# ---------- RocketMQ ----------
"rocketmq": [
{"slug":"rocketmq-architecture","title":"RocketMQ 的整体架构？","content":"""## 一、四大角色

- **Producer**：消息生产者。
- **Consumer**：消息消费者，支持 Push/Pull 两种模式。
- **NameServer**：路由注册中心，无状态，可集群部署，节点间不通信。
- **Broker**：消息存储与转发，可主从部署。

## 二、工作流程

1. NameServer 启动，Broker 启动后向所有 NameServer 注册路由信息并保持心跳。
2. Producer 从 NameServer 获取 Topic 的路由信息，选择队列发送消息。
3. Broker 存储消息，Consumer 从 Broker 拉取消息消费。

## 三、为什么用 NameServer 而不用 ZooKeeper

- NameServer 轻量、无状态、启动快。
- 各 NameServer 独立，互不依赖，可用性更高。
- Broker 向所有 NameServer 广播，保证路由一致。"""},

{"slug":"rocketmq-message-type","title":"RocketMQ 支持哪些消息类型？","content":"""## 一、普通消息

最基本的消息，发完即走。

## 二、顺序消息

同一队列内消息严格有序。发送时用 MessageQueueSelector 把同一业务 key 的消息发到同一队列，消费时同一队列只一个线程消费。

## 三、延迟消息

消息发送后延迟一段时间才投递。RocketMQ 支持固定延迟级别（1s/5s/10s/...2h），开源版不支持任意时间。

## 四、事务消息

半消息 + 本地事务 + 回查机制，保证分布式事务最终一致。

## 五、批量消息

把多条消息打包一次发送，减少网络开销，提高吞吐。"""},

{"slug":"rocketmq-reliable","title":"RocketMQ 如何保证消息不丢？","content":"""## 一、生产端不丢

- 同步发送：等待 Broker ACK，失败重试。
- 开启重试机制：retryTimesWhenSendFailed。
- 事务消息保证本地事务与消息发送一致。

## 二、Broker 不丢

- 同步刷盘：消息写入磁盘才返回成功（flushDiskType=SYNC_FLUSH）。
- 同步复制：主从都写入才返回成功（brokerRole=SYNC_MASTER）。

## 三、消费端不丢

- 手动 ACK：消费成功后才提交 offset。
- 消费失败重新入队或进重试队列。
- 死信队列：重试多次仍失败的消息进死信队列，人工处理。"""},

{"slug":"rocketmq-vs-kafka","title":"RocketMQ 和 Kafka 的区别？","content":"""## 一、对比

| 维度 | RocketMQ | Kafka |
|------|----------|-------|
| 开发语言 | Java | Java/Scala |
| 消息模型 | Topic/Queue | Topic/Partition |
| 顺序消息 | 支持 | 支持（分区内） |
| 延迟消息 | 支持 | 不原生支持 |
| 事务消息 | 支持 | 不支持 |
| 吞吐量 | 十万级 | 百万级 |
| 适用场景 | 业务消息、事务 | 日志、大数据流 |

## 二、选择

- 业务系统需要事务消息、延迟消息、顺序消息 → RocketMQ。
- 日志采集、大数据管道、超高吞吐 → Kafka。"""},

{"slug":"rocketmq-consume-mode","title":"RocketMQ 的集群消费和广播消费？","content":"""## 一、集群消费（CLUSTERING）

- 同一条消息只被消费组内的一个消费者消费。
- 负载均衡：队列在消费者之间分配。
- 实际生产中最常用。

## 二、广播消费（BROADCASTING）

- 同一条消息被消费组内所有消费者都消费一次。
- 每个消费者都消费全量消息。
- 适用于需要所有节点都处理的场景，如配置刷新、缓存失效。

## 三、注意

广播消费下，消费失败不重试，需要业务自行保证。"""},
],

# ---------- MyBatis ----------
"mybatis": [
{"slug":"mybatis-architecture","title":"MyBatis 的核心组件和执行流程？","content":"""## 一、核心组件

- **SqlSessionFactory**：SqlSession 工厂，重量级，全局一个。
- **SqlSession**：与数据库交互的会话，非线程安全，每次操作创建。
- **Executor**：执行器，负责 SQL 执行和缓存维护。
- **StatementHandler**：处理 JDBC Statement。
- **ParameterHandler**：参数处理。
- **ResultSetHandler**：结果集映射。

## 二、执行流程

1. 读取配置文件（mybatis-config.xml）和 Mapper 映射文件，构建 Configuration。
2. SqlSessionFactoryBuilder 创建 SqlSessionFactory。
3. openSession() 获取 SqlSession。
4. SqlSession 获取 Mapper 代理对象（JDK 动态代理）。
5. 调用 Mapper 方法，最终通过 Executor 执行 SQL，返回结果。

## 三、插件机制

基于动态代理，可拦截四大对象（Executor、StatementHandler、ParameterHandler、ResultSetHandler），实现分页、监控等。"""},

{"slug":"mybatis-cache","title":"MyBatis 的一级缓存和二级缓存？","content":"""## 一、一级缓存

- 默认开启，作用域是 SqlSession。
- 同一个 SqlSession 内，相同 SQL 直接从缓存取。
- 执行 update/insert/delete 或 close 后缓存清空。
- 多个 SqlSession 不共享，可能导致脏读。

## 二、二级缓存

- 需手动开启，作用域是 Mapper（namespace）。
- 多个 SqlSession 共享同一 Mapper 的缓存。
- 实现：Mapper 上标注 `<cache/>`，实体类实现 Serializable。
- 多表操作时可能脏数据，实际生产用得少，多用 Redis 替代。

## 三、注意

二级缓存跨 SqlSession，一个 SqlSession 更新数据，另一个可能读到旧值。分布式环境下建议不用。"""},

{"slug":"mybatis-#-$","title":"MyBatis 中 #{} 和 ${} 的区别？","content":"""## 一、#{}

- 预编译参数，JDBC 的 PreparedStatement。
- 会自动加引号，防止 SQL 注入。
- 推荐使用。

## 二、${}

- 字符串拼接，直接替换到 SQL 中。
- 有 SQL 注入风险。
- 仅用于无法使用 #{} 的场景，如动态表名、列名、order by 字段。

## 三、示例

```xml
<!-- #{} 安全 -->
SELECT * FROM user WHERE id = #{id}

<!-- ${} 有注入风险，仅用于动态字段 -->
ORDER BY ${column}
```

## 四、结论

能用 #{} 就用 #{}，必须用 ${} 时确保参数经过白名单校验。"""},

{"slug":"mybatis-dynamic-sql","title":"MyBatis 常用动态 SQL 标签？","content":"""## 一、if

条件判断，满足则拼接 SQL 片段。

```xml
WHERE 1=1
<if test="name != null">AND name = #{name}</if>
```

## 二、where

自动去掉多余的 AND/OR，自动加 WHERE。

```xml
<where>
  <if test="name != null">AND name = #{name}</if>
</where>
```

## 三、set

用于 update，自动去掉多余逗号。

## 四、foreach

遍历集合，常用于 IN 查询和批量插入。

```xml
IN
<foreach collection="ids" item="id" open="(" separator="," close=")">
  #{id}
</foreach>
```

## 五、choose/when/otherwise

类似 switch-case。"""},

{"slug":"mybatis-lazy-loading","title":"MyBatis 的延迟加载？","content":"""## 一、定义

关联查询时，只在真正用到关联对象时才执行查询加载，避免一次性加载所有数据。

## 二、配置

```xml
<settings>
  <setting name="lazyLoadingEnabled" value="true"/>
  <setting name="aggressiveLazyLoading" value="false"/>
</settings>
```

## 三、原理

- MyBatis 为关联对象创建 CGLIB 动态代理。
- 调用代理对象的方法时，触发二次查询加载数据。
- aggressiveLazyLoading=false 时，只有访问关联属性才加载。

## 四、注意

- 需在 SqlSession 关闭前访问关联对象，否则无法加载。
- 延迟加载减少首次查询数据量，但 N+1 问题需评估。"""},
],

# ---------- Spring ----------
"spring": [
{"slug":"spring-ioc-di","title":"Spring 的 IOC 和 DI 是什么？","content":"""## 一、IOC（控制反转）

对象的创建和依赖管理由容器负责，而不是在代码中 new。控制权从业务代码转移到 Spring 容器。

## 二、DI（依赖注入）

IOC 的实现方式。容器把对象依赖的其他对象注入进去。

三种注入方式：
- **构造器注入**：通过构造方法，推荐（依赖不可变、无循环依赖风险）。
- **setter 注入**：通过 setter 方法，可选依赖。
- **字段注入**：@Autowired 直接注字段，简洁但不推荐（测试不便、隐藏依赖）。

## 三、Bean 生命周期

实例化 → 属性赋值 → 初始化（Aware 接口、BeanPostProcessor 前置、@PostConstruct、InitializingBean、init-method、后置）→ 使用 → 销毁（@PreDestroy、DisposableBean、destroy-method）。"""},

{"slug":"spring-bean-scope","title":"Spring Bean 的作用域有哪些？","content":"""## 一、singleton（默认）

容器中只有一个实例，全局共享。适合无状态 Bean。

## 二、prototype

每次获取都创建新实例。适合有状态 Bean。

## 三、request（Web）

每次 HTTP 请求一个实例。

## 四、session（Web）

每个 HTTP Session 一个实例。

## 五、application（Web）

整个 ServletContext 一个实例。

## 六、websocket

每个 WebSocket 连接一个实例。

## 七、注意

singleton 注入 prototype Bean 时，prototype 只注入一次，不会每次都新。需用 ObjectFactory 或 lookup-method 解决。"""},

{"slug":"spring-aop","title":"Spring AOP 的实现原理？","content":"""## 一、AOP 概念

面向切面编程，把横切逻辑（日志、事务、权限）从业务代码中抽离，通过动态织入实现。

## 二、动态代理

- **JDK 动态代理**：目标类实现接口时使用，基于反射生成代理类。
- **CGLIB 代理**：目标类无接口时使用，基于 ASM 生成目标类的子类。

Spring 自动选择：有接口用 JDK，无接口用 CGLIB；也可强制 CGLIB。

## 三、核心概念

- JoinPoint：连接点，可被拦截的方法。
- Pointcut：切点，定义哪些方法被拦截。
- Advice：通知，拦截后执行的逻辑（before/after/around/afterReturning/afterThrowing）。
- Aspect：切面，切点 + 通知。

## 四、事务

@Transactional 基于 AOP 实现，默认只对 RuntimeException 回滚。"""},

{"slug":"spring-transaction","title":"Spring 事务的传播行为和隔离级别？","content":"""## 一、七种传播行为

| 传播行为 | 说明 |
|---------|------|
| REQUIRED（默认） | 有事务就加入，没有就新建 |
| REQUIRES_NEW | 总是新建事务，挂起当前事务 |
| SUPPORTS | 有事务就加入，没有就非事务执行 |
| NOT_SUPPORTED | 非事务执行，挂起当前事务 |
| MANDATORY | 必须有事务，否则抛异常 |
| NEVER | 必须非事务，有事务抛异常 |
| NESTED | 嵌套事务，savepoint |

## 二、隔离级别

同数据库：READ_UNCOMMITTED、READ_COMMITTED、REPEATABLE_READ、SERIALIZABLE，默认用数据库默认。

## 三、事务失效场景

- 方法非 public
- 同类内部方法调用（绕过代理）
- 异常被 try-catch 吞掉
- 抛出非 RuntimeException（默认不回滚）
- 数据库引擎不支持事务（如 MyISAM）"""},

{"slug":"spring-circular-dependency","title":"Spring 如何解决循环依赖？","content":"""## 一、什么是循环依赖

A 依赖 B，B 依赖 A，形成循环。

## 二、三级缓存

Spring 通过三级缓存解决 singleton 的循环依赖：

1. **singletonObjects**：一级缓存，存完全初始化好的 Bean。
2. **earlySingletonObjects**：二级缓存，存早期引用（实例化但未属性赋值）。
3. **singletonFactories**：三级缓存，存 Bean 的 ObjectFactory。

## 三、流程

1. A 实例化后，把自己的工厂放入三级缓存。
2. A 填充属性时发现依赖 B，去创建 B。
3. B 实例化后发现依赖 A，从三级缓存获取 A 的早期引用（解决循环）。
4. B 初始化完成，A 拿到 B 后继续初始化。

## 四、限制

- 只解决 singleton 模式的 setter/字段注入循环依赖。
- 构造器注入循环依赖无法解决（实例化都完成不了）。
- prototype 作用域不解决。"""},
],

# ---------- Spring Cloud ----------
"spring-cloud": [
{"slug":"sc-components","title":"Spring Cloud 常用组件有哪些？","content":"""## 一、注册中心

- **Eureka**：Netflix 出品，AP，自我保护机制。
- **Nacos**：阿里出品，支持 CP/AP 切换，同时支持配置中心。
- **Consul**：支持服务发现、KV 存储、健康检查。

## 二、配置中心

- **Spring Cloud Config**：基于 Git。
- **Nacos Config**：配置实时推送。
- **Apollo**：携程开源，功能完善。

## 三、网关

- **Gateway**：Spring 官方，基于 WebFlux，异步非阻塞。
- **Zuul**：Netflix 出品，同步阻塞，已停止维护。

## 四、负载均衡

- **Ribbon**：客户端负载均衡（已停更，被 LoadBalancer 替代）。
- **Spring Cloud LoadBalancer**：官方替代。

## 五、熔断降级

- **Hystrix**（停更）→ **Resilience4j** / **Sentinel**（阿里）。

## 六、调用

- **OpenFeign**：声明式 HTTP 客户端。"""},

{"slug":"sc-nacos","title":"Nacos 的 CP 和 AP 模式？","content":"""## 一、AP 模式（临时实例）

- 服务实例注册后，通过心跳维持，心跳超时则剔除。
- 各节点数据可能短暂不一致，但保证可用性。
- 适合服务注册场景，实例变化频繁。

## 二、CP 模式（持久实例）

- 实例注册需要集群半数以上节点确认，强一致性。
- 节点不可用时不会自动剔除，需手动注销。
- 适合需要强一致性的场景。

## 三、切换

通过 `ephemeral` 参数控制：true 为 AP（临时），false 为 CP（持久）。

## 四、为什么 Nacos 支持切换

服务注册发现更看重可用性（AP），配置管理更看重一致性（CP），Nacos 通过 Raft 协议和 Distro 协议分别保证。"""},

{"slug":"sc-gateway","title":"Spring Cloud Gateway 的工作原理？","content":"""## 一、核心概念

- **Route**：路由，由 ID、目标 URI、断言、过滤器组成。
- **Predicate**：断言，匹配 HTTP 请求的条件（路径、方法、Header 等）。
- **Filter**：过滤器，请求前后处理（鉴权、限流、日志、修改请求/响应）。

## 二、执行流程

1. 请求进入 Gateway，由 HandlerMapping 匹配路由。
2. 匹配成功后，请求经过过滤器链。
3. 过滤器分 pre（请求转发前）和 post（响应返回前）。
4. 最终请求转发到目标服务，响应返回。

## 三、全局过滤器

- GlobalFilter：对所有路由生效，如负载均衡（LoadBalancerClientFilter）、路由转发。
- 自定义全局过滤器需实现 GlobalFilter 和 Ordered。

## 四、与 Zuul 区别

Gateway 基于 WebFlux 异步非阻塞，性能更好；Zuul 1.x 同步阻塞。"""},

{"slug":"sc-sentinel","title":"Sentinel 的工作原理？","content":"""## 一、核心功能

- **流量控制**：QPS、线程数限流，支持匀速、预热等模式。
- **熔断降级**：慢调用比例、异常比例、异常数达到阈值时熔断。
- **系统保护**：CPU、负载、入口 QPS、平均 RT、并发线程数。
- **热点参数限流**：针对参数维度限流。

## 二、工作原理

- 基于滑动窗口统计实时指标。
- 通过责任链模式处理请求：NodeSelectorSlot → ClusterBuilderSlot → StatisticSlot → FlowSlot → DegradeSlot → SystemSlot。
- 每个 Slot 负责一个功能，前一个通过才进入下一个。

## 三、熔断状态

Closed → Open（达到阈值）→ Half-Open（探测恢复）→ Closed

## 四、与 Hystrix 区别

Sentinel 更轻量、功能更全、支持动态规则、有控制台。"""},

{"slug":"sc-feign","title":"OpenFeign 的工作原理？","content":"""## 一、使用

```java
@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/user/{id}")
    User getUser(@PathVariable Long id);
}
```

## 二、原理

1. 启动时扫描 @FeignClient 注解，为每个接口生成 JDK 动态代理。
2. 代理对象注册到 Spring 容器。
3. 调用方法时，代理根据注解构造 HTTP 请求，通过 LoadBalancer 选实例，发起 HTTP 调用。
4. 返回结果反序列化为方法返回类型。

## 三、超时配置

```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 10000
```

## 四、注意

Feign 默认不开启重试，需手动配置 Retryer。生产中建议配合 Sentinel 做熔断。"""},
],

# ---------- 设计模式 ----------
"design-pattern": [
{"slug":"dp-singleton","title":"单例模式有哪些实现方式？","content":"""## 一、饿汉式

```java
public class Singleton {
    private static final Singleton INSTANCE = new Singleton();
    private Singleton() {}
    public static Singleton getInstance() { return INSTANCE; }
}
```
类加载时即创建，线程安全，但可能浪费内存。

## 二、懒汉式（线程不安全）

```java
public class Singleton {
    private static Singleton instance;
    private Singleton() {}
    public static Singleton getInstance() {
        if (instance == null) instance = new Singleton();
        return instance;
    }
}
```

## 三、双重检查锁（DCL）

```java
public class Singleton {
    private static volatile Singleton instance;
    private Singleton() {}
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) instance = new Singleton();
            }
        }
        return instance;
    }
}
```
volatile 防止指令重排导致拿到未初始化对象。

## 四、静态内部类（推荐）

```java
public class Singleton {
    private Singleton() {}
    private static class Holder { static final Singleton INSTANCE = new Singleton(); }
    public static Singleton getInstance() { return Holder.INSTANCE; }
}
```
懒加载 + 线程安全，无锁。

## 五、枚举

```java
public enum Singleton { INSTANCE; }
```
最简洁，天然防反射和序列化破坏。"""},

{"slug":"dp-factory","title":"工厂模式有哪几种？","content":"""## 一、简单工厂

一个工厂类根据参数创建不同产品。违反开闭原则，新增产品需改工厂。

```java
public class ShapeFactory {
    public Shape create(String type) {
        if ("circle".equals(type)) return new Circle();
        if ("square".equals(type)) return new Square();
        return null;
    }
}
```

## 二、工厂方法

定义工厂接口，每个产品对应一个具体工厂。符合开闭原则，但类数量多。

```java
public interface ShapeFactory { Shape create(); }
public class CircleFactory implements ShapeFactory {
    public Shape create() { return new Circle(); }
}
```

## 三、抽象工厂

创建一系列相关产品（产品族）。如同一品牌的手机 + 电脑。

## 四、Spring 中的工厂

BeanFactory 是 Spring 最核心的工厂，负责创建和管理 Bean。"""},

{"slug":"dp-strategy","title":"策略模式的原理和应用？","content":"""## 一、定义

定义一系列算法，封装成独立策略类，使它们可互换。客户端运行时选择策略，避免大量 if-else。

## 二、结构

- Strategy：策略接口。
- ConcreteStrategy：具体策略实现。
- Context：持有策略引用，调用策略方法。

## 三、示例

```java
public interface PayStrategy { void pay(double amount); }
public class Alipay implements PayStrategy { ... }
public class WechatPay implements PayStrategy { ... }

public class PayContext {
    private PayStrategy strategy;
    public PayContext(PayStrategy strategy) { this.strategy = strategy; }
    public void pay(double amount) { strategy.pay(amount); }
}
```

## 四、Spring 应用

Spring 的 Resource（资源访问策略）、BeanPostProcessor 都体现了策略模式。"""},

{"slug":"dp-observer","title":"观察者模式的原理和应用？","content":"""## 一、定义

定义对象间一对多依赖，当一个对象状态变化时，所有依赖它的对象自动收到通知并更新。也叫发布-订阅模式。

## 二、结构

- Subject（主题）：维护观察者列表，提供注册/注销/通知方法。
- Observer（观察者）：定义更新接口。

## 三、JDK 实现

```java
// 主题
public class Subject extends Observable {
    public void change() {
        setChanged();
        notifyObservers("data");
    }
}
// 观察者
public class MyObserver implements Observer {
    public void update(Observable o, Object arg) { ... }
}
```

## 四、应用

- Spring 的事件机制（ApplicationEvent + ApplicationListener）。
- 消息队列的发布订阅。
- GUI 事件监听。"""},

{"slug":"dp-decorator","title":"装饰器模式的原理和应用？","content":"""## 一、定义

动态地给对象添加额外职责，比继承更灵活。

## 二、结构

- Component：抽象组件。
- ConcreteComponent：具体组件。
- Decorator：装饰器抽象类，持有 Component 引用。
- ConcreteDecorator：具体装饰器，添加职责。

## 三、Java IO 中的应用

```java
InputStream is = new FileInputStream("a.txt");
InputStream bis = new BufferedInputStream(is);     // 缓冲装饰
InputStream dis = new DataInputStream(bis);       // 数据类型装饰
```

## 四、与继承区别

- 继承：编译期静态，类爆炸。
- 装饰器：运行期动态组合，灵活。

## 五、Spring 中的应用

Spring AOP 的通知链、BeanWrapper 都有装饰器思想。"""},
],

# ---------- 计算机网络 ----------
"network": [
{"slug":"net-tcp-three-way","title":"TCP 三次握手过程？","content":"""## 一、过程

1. **第一次**：客户端发送 SYN=1, seq=x，进入 SYN_SENT 状态。
2. **第二次**：服务端回复 SYN=1, ACK=1, seq=y, ack=x+1，进入 SYN_RCVD 状态。
3. **第三次**：客户端回复 ACK=1, seq=x+1, ack=y+1，双方进入 ESTABLISHED 状态。

## 二、为什么是三次

- 两次无法确认客户端的接收能力和服务端的发送能力。
- 三次是最少能确认双方收发能力都正常的次数。
- 防止已失效的连接请求到达服务端导致错误建立连接。

## 三、为什么不是四次

服务端的 SYN 和 ACK 可以合并在一次发送，所以三次足够。

## 四、SYN 攻击

攻击者发送大量 SYN 但不回 ACK，占满服务端半连接队列。防护：SYN cookies、增大半连接队列、缩短 SYN 超时。"""},

{"slug":"net-tcp-four-way","title":"TCP 四次挥手过程？","content":"""## 一、过程

1. 客户端发 FIN=1, seq=u，进入 FIN_WAIT_1。
2. 服务端回 ACK=1, ack=u+1，进入 CLOSE_WAIT；客户端进入 FIN_WAIT_2。
3. 服务端发完数据后发 FIN=1, seq=w，进入 LAST_ACK。
4. 客户端回 ACK=1, ack=w+1，进入 TIME_WAIT；服务端收到后关闭。

## 二、为什么挥手是四次

握手时服务端 SYN+ACK 合并，挥手时 ACK 和 FIN 不能合并，因为服务端可能还有数据要发，先发 ACK 确认，数据发完再发 FIN。

## 三、TIME_WAIT

- 客户端回 ACK 后等待 2MSL 才关闭。
- 原因：确保最后一个 ACK 到达服务端（丢了可重传）；让本次连接的所有报文在网络中消失，避免影响新连接。
- 大量 TIME_WAIT 可通过 `tcp_tw_reuse` 优化。"""},

{"slug":"net-tcp-vs-udp","title":"TCP 和 UDP 的区别？","content":"""## 一、对比

| 维度 | TCP | UDP |
|------|-----|-----|
| 连接 | 面向连接 | 无连接 |
| 可靠性 | 可靠 | 不可靠 |
| 顺序 | 保证 | 不保证 |
| 流量控制 | 有 | 无 |
| 拥塞控制 | 有 | 无 |
| 首部大小 | 20~60 字节 | 8 字节 |
| 性能 | 较低 | 较高 |

## 二、TCP 保证可靠的机制

- 序列号 + 确认应答
- 超时重传
- 流量控制（滑动窗口）
- 拥塞控制（慢启动、拥塞避免、快重传、快恢复）

## 三、应用场景

- TCP：HTTP、FTP、邮件、文件传输。
- UDP：DNS、视频直播、游戏、语音通话。"""},

{"slug":"net-http-https","title":"HTTP 和 HTTPS 的区别？","content":"""## 一、核心区别

HTTPS = HTTP + TLS/SSL，在 HTTP 和 TCP 之间加了加密层。

## 二、对比

| 维度 | HTTP | HTTPS |
|------|------|-------|
| 端口 | 80 | 443 |
| 传输 | 明文 | 加密 |
| 证书 | 不需要 | 需要 CA 证书 |
| 安全 | 易被窃听/篡改 | 机密性+完整性+身份认证 |
| 性能 | 较快 | 略慢（TLS 握手） |

## 三、TLS 握手

1. 客户端发 ClientHello（支持的加密套件、随机数）。
2. 服务端回 ServerHello + 证书。
3. 客户端验证证书，生成预主密钥，用证书公钥加密发给服务端。
4. 双方用预主密钥 + 两个随机数生成会话密钥。
5. 后续通信用会话密钥对称加密。

## 四、HTTPS 优化

- Session 复用
- OCSP Stapling
- HTTP/2 多路复用"""},

{"slug":"net-http-status","title":"常见 HTTP 状态码？","content":"""## 一、1xx 信息

- 100 Continue：继续发送。

## 二、2xx 成功

- 200 OK：请求成功。
- 201 Created：资源创建成功。
- 204 No Content：成功但无响应体。
- 206 Partial Content：范围请求。

## 三、3xx 重定向

- 301 Moved Permanently：永久重定向。
- 302 Found：临时重定向。
- 304 Not Modified：缓存有效。
- 307 Temporary Redirect：临时重定向，保持请求方法。

## 四、4xx 客户端错误

- 400 Bad Request：请求语法错误。
- 401 Unauthorized：未认证。
- 403 Forbidden：禁止访问。
- 404 Not Found：资源不存在。
- 405 Method Not Allowed：方法不允许。
- 429 Too Many Requests：限流。

## 五、5xx 服务端错误

- 500 Internal Server Error：服务器内部错误。
- 502 Bad Gateway：网关错误。
- 503 Service Unavailable：服务不可用。
- 504 Gateway Timeout：网关超时。"""},
],

# ---------- 操作系统 ----------
"os": [
{"slug":"os-process-thread","title":"进程和线程的区别？","content":"""## 一、定义

- **进程**：程序运行的实例，资源分配的基本单位，有独立地址空间。
- **线程**：进程内的执行单元，CPU 调度的基本单位，共享进程资源。

## 二、对比

| 维度 | 进程 | 线程 |
|------|------|------|
| 资源 | 独立地址空间 | 共享进程资源 |
| 切换开销 | 大（切换页表、TLB） | 小 |
| 通信 | IPC（管道、消息队列、共享内存） | 共享内存（需同步） |
| 健壮性 | 一个崩不影响其他 | 一个崩整个进程崩 |

## 三、线程切换开销

线程切换只需要保存/恢复寄存器、栈指针；进程切换还要切换页表、刷新 TLB，开销大得多。

## 四、协程

用户态轻量级线程，由程序自己调度，不依赖内核，切换开销极小。适合高并发 I/O 场景。"""},

{"slug":"os-scheduler","title":"进程调度算法有哪些？","content":"""## 一、先来先服务（FCFS）

按到达顺序执行，非抢占。简单但短作业可能等待很久。

## 二、短作业优先（SJF）

预估运行时间短的先执行。平均等待时间最短，但长作业可能饥饿。

## 三、时间片轮转（RR）

每个进程分配一个时间片，用完就切换。适合交互式系统，响应快。

## 四、优先级调度

高优先级进程先执行。可抢占或非抢占，低优先级可能饥饿，可用老化（aging）解决。

## 五、多级反馈队列

- 多个队列，优先级从高到低，时间片从小到大。
- 新进程进最高优先级队列，用完时间片降一级。
- 短作业在高队列快速完成，长作业逐渐降级。
- 综合了多种算法优点。

## 六、Linux 调度

CFS（完全公平调度器），按虚拟运行时间排序，红黑树实现。"""},

{"slug":"os-deadlock","title":"什么是死锁？如何避免？","content":"""## 一、四个必要条件

1. **互斥**：资源同一时间只能被一个进程使用。
2. **持有并等待**：进程持有资源同时等待其他资源。
3. **不可剥夺**：已获得的资源不能被强制夺走。
4. **循环等待**：进程间形成循环等待链。

## 二、预防

破坏四个条件之一：
- 破坏互斥：难（有些资源必须互斥）。
- 破坏持有并等待：一次性申请所有资源。
- 破坏不可剥夺：可抢占。
- 破坏循环等待：资源有序分配。

## 三、避免

银行家算法：分配资源前判断是否会导致系统进入不安全状态。

## 四、检测与解除

- 用资源分配图检测死锁。
- 解除：终止进程、抢占资源。"""},

{"slug":"os-memory-manage","title":"操作系统的内存管理？","content":"""## 一、虚拟内存

每个进程有独立虚拟地址空间，通过页表映射到物理内存。进程以为自己独占内存。

## 二、分页

内存分成固定大小的页（通常 4KB），虚拟页映射到物理页帧。

- 页表：记录虚拟页到物理页的映射。
- TLB：页表缓存，加速地址翻译。
- 缺页中断：访问的页不在内存，从磁盘加载。

## 三、分段

按逻辑单位分段（代码段、数据段、栈段），段大小可变，更符合程序逻辑。

## 四、段页式

先分段，段内分页，结合两者优点。

## 五、页面置换算法

- FIFO：先进先出，可能 Belady 异常。
- LRU：最近最少使用，性能好。
- LFU：最不经常使用。
- Clock：时钟算法，近似 LRU。"""},

{"slug":"os-io-multiplexing","title":"什么是 I/O 多路复用？","content":"""## 一、定义

单个线程通过系统调用同时监听多个文件描述符，哪个就绪就处理哪个，避免阻塞在单个 I/O 上。

## 二、select

```c
select(maxfd, &readfds, &writefds, &exceptfds, &timeout);
```
- 位图存储 fd，有最大数量限制（1024）。
- 每次调用都要把 fd 集合从用户态拷贝到内核态。
- 内核遍历所有 fd，返回时不知道哪些就绪，需用户遍历。

## 三、poll

用链表代替位图，无数量限制，但仍是线性扫描。

## 四、epoll（Linux 特有）

- epoll_create 创建实例。
- epoll_ctl 注册/修改/删除 fd。
- epoll_wait 等待就绪。
- 只返回就绪的 fd，无需遍历全部。
- 支持水平触发（LT）和边缘触发（ET）。

## 五、应用

Redis、Nginx、Netty 都基于 epoll 实现高并发。"""},
],

# ---------- Zookeeper ----------
"zookeeper": [
{"slug":"zk-data-model","title":"ZooKeeper 的数据模型？","content":"""## 一、ZNode

ZooKeeper 数据以树形结构存储，每个节点叫 ZNode，路径用 / 分隔，如 /a/b/c。

## 二、节点类型

- **持久节点**：创建后一直存在，直到删除。
- **临时节点**：创建者会话断开自动删除。
- **持久顺序节点**：持久 + 单调递增序号。
- **临时顺序节点**：临时 + 单调递增序号，常用于分布式锁。

## 三、特性

- 每个 ZNode 可存数据（默认 1MB）。
- 有版本号（dataVersion），乐观锁。
- 支持 Watcher 监听节点变化。

## 四、应用

- 配置中心：监听配置节点变化。
- 注册中心：服务注册临时节点，下线自动消失。
- 分布式锁：临时顺序节点实现公平锁。"""},

{"slug":"zk-consistency","title":"ZooKeeper 如何保证一致性？","content":"""## 一、ZAB 协议

ZooKeeper Atomic Broadcast，类似 Paxos。

## 二、角色

- **Leader**：处理写请求，发起投票。
- **Follower**：处理读请求，参与投票。
- **Observer**：只读，不参与投票，扩展读能力。

## 三、写流程

1. 客户端写请求发给任意节点，非 Leader 转发给 Leader。
2. Leader 生成事务提议（proposal），发给所有 Follower。
3. Follower 收到后持久化，返回 ACK。
4. Leader 收到半数以上 ACK，提交事务，通知所有节点 commit。

## 四、一致性

- 写请求：半数以上成功即提交，保证最终一致。
- 读请求：默认从本机读，可能读到旧数据；sync 读保证强一致。
- 顺序一致性：同一客户端请求按顺序执行。"""},

{"slug":"zk-distributed-lock","title":"ZooKeeper 实现分布式锁？","content":"""## 一、原理

利用临时顺序节点 + Watcher。

## 二、加锁流程

1. 在 /lock 节点下创建临时顺序节点 /lock/seq-000000001。
2. 获取 /lock 下所有子节点，判断自己是否最小。
3. 是最小 → 获取锁成功。
4. 不是最小 → 监听前一个节点的删除事件，阻塞等待。

## 三、释放锁

- 主动删除自己的节点。
- 会话断开，临时节点自动删除（避免死锁）。

## 四、优点

- 临时节点自动释放，无死锁。
- 顺序节点实现公平锁。
- Watcher 通知，无需轮询。

## 五、缺点

- 频繁创建删除节点，性能不如 Redis 锁。
- 网络抖动导致会话断开可能误释放锁。"""},

{"slug":"zk-watcher","title":"ZooKeeper 的 Watcher 机制？","content":"""## 一、定义

客户端可以在 ZNode 上注册 Watcher，当节点状态变化时，服务端通知客户端，触发回调。

## 二、特性

- **一次性**：Watcher 触发后失效，需重新注册。
- **轻量**：只发送事件类型和路径，不发送数据。
- **异步**：通知是异步发送的。

## 三、事件类型

- NodeCreated：节点创建。
- NodeDeleted：节点删除。
- NodeDataChanged：节点数据变化。
- NodeChildrenChanged：子节点变化。

## 四、应用

- 配置中心：监听配置节点，数据变化自动更新。
- 服务发现：监听服务节点列表变化。

## 五、Curator 客户端

Apache Curator 封装了 Watcher 的一次性问题，提供 NodeCache、PathChildrenCache 等持续监听。"""},

{"slug":"zk-vs-nacos","title":"ZooKeeper 和 Nacos 的区别？","content":"""## 一、对比

| 维度 | ZooKeeper | Nacos |
|------|-----------|-------|
| 一致性 | CP（ZAB） | 支持 CP/AP |
| 配置中心 | 需自行封装 | 原生支持，实时推送 |
| 服务发现 | 临时节点 | 临时/持久实例 |
| 控制台 | 无官方 | 有 Web 控制台 |
| 语言 | Java | Java |

## 二、选择

- 需要强一致、分布式协调（锁、选主）→ ZooKeeper。
- 微服务注册发现 + 配置管理一体化 → Nacos。
- 实际项目中注册中心多用 Nacos/Eureka，协调服务用 ZooKeeper。

## 三、Nacos 优势

Nacos 集成服务发现和配置管理，有控制台，支持 AP/CP 切换，更适合云原生微服务。"""},
],

# ---------- RabbitMQ ----------
"rabbitmq": [
{"slug":"rabbitmq-architecture","title":"RabbitMQ 的核心概念？","content":"""## 一、核心组件

- **Producer**：生产者，发送消息到 Exchange。
- **Exchange**：交换机，接收消息并按路由规则路由到 Queue。
- **Queue**：队列，存储消息，消费者从队列取。
- **Consumer**：消费者。
- **Binding**：Exchange 和 Queue 的绑定关系，带 routing key。

## 二、交换机类型

- **direct**：精确匹配 routing key。
- **fanout**：广播到所有绑定队列，忽略 routing key。
- **topic**：模糊匹配，支持 * 和 # 通配符。
- **headers**：根据消息头匹配，用得少。

## 三、工作流程

1. Producer 发送消息到 Exchange，带 routing key。
2. Exchange 根据类型和 routing key 路由到匹配的 Queue。
3. Consumer 从 Queue 消费消息并 ACK。"""},

{"slug":"rabbitmq-message-ack","title":"RabbitMQ 的消息确认机制？","content":"""## 一、生产者确认（Publisher Confirm）

- 消息投递到 Exchange 后返回 confirm。
- 消息路由到 Queue（持久化消息持久化到磁盘）后返回 ack。
- 可配合 mandatory 参数，消息不可路由时返回给生产者。

## 二、消费者确认（Consumer ACK）

- **自动确认**（autoAck=true）：消息投递给消费者即认为消费成功，可能丢消息。
- **手动确认**（autoAck=false）：消费者处理完后调用 basicAck/basicNack/basicReject。

## 三、失败处理

- basicAck：确认成功，消息从队列删除。
- basicNack/reject：拒绝，可选择 requeue=true 重新入队或 false 丢弃/进死信队列。
- 多次 requeue 可能导致消息循环，建议配合死信队列。

## 四、死信队列

消息被拒绝且 requeue=false、超时、队列满时进入死信队列，便于排查和补偿。"""},

{"slug":"rabbitmq-reliable","title":"RabbitMQ 如何保证消息可靠？","content":"""## 一、生产端

- 开启 Publisher Confirm，确保消息到达 Exchange。
- 开启 Return 机制，消息不可路由时通知生产者。
- 本地消息表 + 定时补偿。

## 二、Broker 端

- 交换机、队列、消息都持久化（durable=true, deliveryMode=2）。
- 镜像队列：队列复制到多个节点，主节点宕机从节点接管。

## 三、消费端

- 手动 ACK，处理完再确认。
- 失败消息进死信队列。
- 消费幂等：消息可能重复，业务需去重。

## 四、顺序消息

RabbitMQ 单个队列内消息有序，但多消费者消费时顺序被打乱。保证顺序需单队列单消费者，或按业务 key 路由到同一队列。"""},

{"slug":"rabbitmq-vs-rocketmq","title":"RabbitMQ 和 RocketMQ 的区别？","content":"""## 一、对比

| 维度 | RabbitMQ | RocketMQ |
|------|----------|----------|
| 开发语言 | Erlang | Java |
| 吞吐量 | 万级 | 十万级 |
| 延迟 | 微秒级 | 毫秒级 |
| 事务消息 | 不支持 | 支持 |
| 顺序消息 | 支持（弱） | 支持 |
| 延迟消息 | 支持插件 | 原生支持 |
| 管理界面 | 有 | 有 |

## 二、选择

- 吞吐量要求不高、需要低延迟、灵活路由 → RabbitMQ。
- 高吞吐、事务消息、顺序消息、大规模 → RocketMQ。

## 三、RabbitMQ 优势

- Erlang 实现，天生高并发、低延迟。
- 交换机模型灵活，路由能力强。
- 插件丰富。

## 四、RocketMQ 优势

- Java 实现，与 Java 生态融合好。
- 支持事务消息，适合分布式事务。
- 阿里双11验证，稳定性高。"""},

{"slug":"rabbitmq-prefetch","title":"RabbitMQ 的 prefetch 机制？","content":"""## 一、定义

prefetch（QoS）控制 RabbitMQ 一次推送给消费者多少条未确认的消息，实现限流和负载均衡。

## 二、设置

```java
channel.basicQos(10); // 每个消费者最多 10 条未确认消息
```

## 三、作用

- 防止消息都推给快的消费者，慢的消费者闲置，实现公平分发。
- 防止消费者内存被大量未处理消息占满。

## 四、三种范围

- prefetchCount=0：不限。
- channel.basicQos(prefetchSize, prefetchCount, global)。
  - global=false：每个消费者限制。
  - global=true：整个 channel 限制。

## 五、注意

prefetch 只对手动 ACK 模式有效，自动 ACK 下消息立即从队列移除，无限制。"""},
],

# ---------- Tomcat ----------
"tomcat": [
{"slug":"tomcat-architecture","title":"Tomcat 的整体架构？","content":"""## 一、核心组件

- **Server**：Tomcat 顶层容器，管理 Service 生命周期。
- **Service**：包含 Connector 和 Engine，一个 Tomcat 可有多个 Service。
- **Connector**：连接器，监听端口，接收请求并转换为 Request，如 HTTP/1.1、AJP。
- **Engine**：引擎，处理请求，包含多个 Host。
- **Host**：虚拟主机，包含多个 Context。
- **Context**：Web 应用，一个 Context 对应一个部署的应用。
- **Wrapper**：Servlet 封装，一个 Wrapper 对应一个 Servlet。

## 二、请求处理流程

1. Connector 接收 HTTP 请求，生成 Request 和 Response。
2. Engine 选择 Host，Host 选择 Context（根据 URL 路径）。
3. Context 选择 Wrapper（根据 Servlet 映射）。
4. Wrapper 调用 Servlet 的 service 方法。
5. 响应沿原路返回。

## 三、类加载器

Tomcat 打破双亲委派：WebappClassLoader 先自己加载，找不到再委托父加载器，保证各 Web 应用隔离。"""},

{"slug":"tomcat-connector","title":"Tomcat Connector 的工作模式？","content":"""## 一、三种运行模式

- **BIO**：阻塞 IO，每个请求一个线程，Tomcat 7 前默认。
- **NIO**：非阻塞 IO，基于 Java NIO，Tomcat 8 默认。
- **APR**：基于 Apache Portable Runtime，原生 C 库，性能最好。

## 二、NIO 模式

- 一个 Acceptor 线程接收连接。
- 一个 Poller 线程轮询 Selector，检测就绪事件。
- 线程池处理请求。
- 少量线程处理大量连接，适合高并发。

## 三、配置

```xml
<Connector port="8080" protocol="org.apache.coyote.http11.Http11NioProtocol"
           maxThreads="200" minSpareThreads="20" acceptCount="100"/>
```

## 四、优化

- 调整 maxThreads（最大线程数）。
- 调整 acceptCount（等待队列）。
- 启用压缩 compression。
- 调整连接超时 connectionTimeout。"""},

{"slug":"tomcat-session","title":"Tomcat 的 Session 管理？","content":"""## 一、Session 实现

Tomcat 用 StandardSession 实现 HttpSession，存在内存中，由 Manager 管理。

## 二、持久化

- **StandardManager**：Tomcat 关闭时序列化 Session 到 SESSIONS.ser，启动时恢复。
- **PersistentManager**：可把空闲 Session 保存到文件或数据库。

## 三、集群 Session 共享

- **粘性会话（Sticky Session）**：同一用户请求固定到同一节点，无需共享，但节点宕机 Session 丢失。
- **Session 复制**：节点间广播 Session，一致性好但开销大。
- **集中存储**：Session 存 Redis 等，推荐。

## 四、Session 超时

```xml
<session-config>
  <session-timeout>30</session-timeout>
</session-config>
```

## 五、Cookie

Tomcat 默认用 JSESSIONID Cookie 跟踪 Session，也支持 URL 重写。"""},

{"slug":"tomcat-valve","title":"Tomcat 的 Valve 机制？","content":"""## 一、定义

Valve（阀门）是 Tomcat 的拦截器机制，类似 Servlet Filter，但作用在容器层面（Engine/Host/Context）。

## 二、常用 Valve

- **AccessLogValve**：访问日志。
- **RemoteAddrValve**：IP 黑白名单。
- **RemoteHostValve**：主机名黑白名单。
- **SingleSignOnValve**：单点登录。
- **CorsFilter** 实际是 Filter 不是 Valve。

## 三、配置示例

```xml
<Valve className="org.apache.catalina.valves.AccessLogValve"
       directory="logs" prefix="access" suffix=".log"
       pattern="%h %l %u %t &quot;%r&quot; %s %b"/>
```

## 四、与 Filter 区别

- Valve 是 Tomcat 特有，容器级。
- Filter 是 Servlet 规范，应用级。
- Valve 可以做 Filter 做不到的事，如修改 Request 对象本身。"""},

{"slug":"tomcat-deploy","title":"Tomcat 的部署方式有哪些？","content":"""## 一、WAR 包部署

把 WAR 放到 webapps 目录，Tomcat 自动解压部署。最常用。

## 二、目录部署

直接把解压后的应用目录放到 webapps，或通过 Context 配置指定路径。

## 三、Context 片段部署

在 conf/Catalina/localhost/ 下放 context.xml，指定 docBase。

## 四、Manager 应用

通过 Tomcat 自带的 Manager 应用（/manager/html）远程部署，需配置用户。

## 五、嵌入式 Tomcat

Spring Boot 默认用嵌入式 Tomcat，无需单独安装，打成 jar 直接运行。

## 六、注意

- 生产环境建议关掉 Manager 应用或限制访问。
- 热部署可能导致内存泄漏（类加载器无法回收）。"""},
],

# ---------- Dubbo ----------
"dubbo": [
{"slug":"dubbo-architecture","title":"Dubbo 的整体架构？","content":"""## 一、核心角色

- **Provider**：服务提供者，暴露服务。
- **Consumer**：服务消费者，调用远程服务。
- **Registry**：注册中心，服务注册与发现。
- **Monitor**：监控中心，统计调用次数和耗时。
- **Container**：服务运行容器。

## 二、调用流程

1. Provider 启动时向 Registry 注册服务。
2. Consumer 启动时向 Registry 订阅服务，获取 Provider 列表。
3. Consumer 调用服务时，通过负载均衡选一个 Provider，发起 RPC 调用。
4. 调用数据异步上报 Monitor。

## 三、协议

- **dubbo**：默认，基于 Netty + Hessian，适合服务间调用。
- **rest**：HTTP RESTful。
- **hessian**：基于 HTTP + Hessian。
- **http**：基于 HTTP 表单。
- **grpc**：基于 gRPC。

## 四、注册中心

推荐 Nacos，也支持 ZooKeeper、Redis、Multicast。"""},

{"slug":"dubbo-loadbalance","title":"Dubbo 的负载均衡策略？","content":"""## 一、四种策略

- **Random**（默认）：随机，按权重加权随机。
- **RoundRobin**：轮询，按权重轮询，慢的 Provider 会累积请求。
- **LeastActive**：最少活跃调用，慢的 Provider 收到更少请求。
- **ConsistentHash**：一致性哈希，相同参数请求到同一 Provider。

## 二、配置

```java
@DubboReference(loadbalance = "random")
private UserService userService;
```

或服务端配置：
```java
@DubboService(loadbalance = "leastactive")
public class UserServiceImpl implements UserService {}
```

## 三、权重

通过 `weight` 参数调整，动态调节负载。LeastActive + 权重是生产常用组合，避免慢节点拖垮。

## 四、集群容错

- failover（默认）：失败重试，默认 2 次。
- failfast：快速失败，只调一次。
- failsafe：安全失败，忽略异常。
- failback：异步重试，适合通知。
- forking：并行调多个，取第一个成功。"""},

{"slug":"dubbo-spi","title":"Dubbo 的 SPI 机制？","content":"""## 一、SPI 定义

Service Provider Interface，服务提供接口。Java 有自带 SPI，Dubbo 对其增强。

## 二、Dubbo SPI 优势

- 按需加载，不一次性加载所有实现。
- 支持自适应扩展（@Adaptive），运行时动态选择实现。
- 支持自动包装（Wrapper），AOP 能力。
- 支持依赖注入。

## 三、使用

1. 接口加 @SPI 注解。
2. META-INF/dubbo/ 下配置文件：key=实现类全限定名。
3. ExtensionLoader.getExtensionLoader(接口.class).getExtension("key")。

## 四、@Adaptive

动态生成代理类，根据 URL 参数选择具体实现，避免硬编码。Dubbo 的 Protocol、LoadBalance 等都用自适应扩展。

## 五、应用

Dubbo 的协议、负载均衡、序列化、注册中心等全部通过 SPI 实现，可灵活扩展。"""},

{"slug":"dubbo-rpc","title":"Dubbo 的 RPC 调用过程？","content":"""## 一、代理层

Consumer 调用接口方法，实际调用的是代理对象（JDK 动态代理或 Javassist）。

## 二、路由层

- Cluster：集群容错，处理调用失败。
- Directory：管理 Provider 列表。
- Router：根据路由规则过滤 Provider。
- LoadBalance：从 Provider 中选一个。

## 三、协议层

- Filter 链：监控、限流、日志等。
- Protocol：把调用转为特定协议（dubbo/rest 等）。

## 四、传输层

- Client/Server：基于 Netty 通信。
- Codec：序列化/反序列化（Hessian2、JSON、Protobuf 等）。

## 五、服务端

- Handler 接收请求。
- 反射调用目标方法。
- 返回结果。

## 六、全链路

Consumer 代理 → Cluster → Directory/Router/LoadBalance → Filter → Protocol → Transport → Provider Filter → Invoker → 实现类。"""},

{"slug":"dubbo-vs-feign","title":"Dubbo 和 OpenFeign 的区别？","content":"""## 一、对比

| 维度 | Dubbo | OpenFeign |
|------|-------|-----------|
| 协议 | 多协议（dubbo 默认） | HTTP |
| 性能 | 高（二进制协议） | 较低（HTTP+JSON） |
| 服务治理 | 完善（路由、熔断、限流） | 需配合 Sentinel |
| 注册中心 | 必需 | 必需 |
| 语言 | Java 为主 | 跨语言（HTTP） |
| 适用 | 内部服务调用 | 内外通用 |

## 二、选择

- 内部 Java 微服务，追求高性能 → Dubbo。
- 需要跨语言、对外暴露、简单 → OpenFeign。
- 实际中可混用：对外 REST，对内 Dubbo。

## 三、Dubbo 优势

- 高性能二进制协议，序列化开销小。
- 丰富的服务治理能力。
- 支持多协议、多注册中心。

## 四、Feign 优势

- HTTP 协议通用，调试方便。
- 声明式调用，简单直观。
- 跨语言友好。"""},
],

# ---------- Netty ----------
"netty": [
{"slug":"netty-reactor","title":"Netty 的 Reactor 线程模型？","content":"""## 一、Reactor 模式

基于事件驱动，I/O 多路复用监听多个连接，事件就绪后分发给处理器。

## 二、Netty 三种模型

- **单线程 Reactor**：一个线程处理所有连接的 accept 和 I/O，简单但不适合高并发。
- **多线程 Reactor**：一个 Acceptor 线程接收连接，I/O 由线程池处理。
- **主从 Reactor**（Netty 默认）：BossGroup 接收连接，WorkerGroup 处理 I/O。

## 三、Netty 默认模型

```java
EventLoopGroup boss = new NioEventLoopGroup(1);
EventLoopGroup worker = new NioEventLoopGroup();
ServerBootstrap b = new ServerBootstrap();
b.group(boss, worker);
```

- BossGroup 通常 1 个线程，负责 accept。
- WorkerGroup 默认 CPU 核数 × 2 线程，负责 I/O。
- 每个 Channel 绑定一个 EventLoop，全程单线程，无锁。

## 四、优势

- 事件驱动，高吞吐。
- Channel 绑定固定 EventLoop，避免锁竞争。
- Pipeline 模式灵活扩展。"""},

{"slug":"netty-pipeline","title":"Netty 的 ChannelPipeline 机制？","content":"""## 一、定义

每个 Channel 有一个 Pipeline，由多个 ChannelHandler 组成，形成责任链。入站和出站事件在 Pipeline 中传播。

## 二、入站和出站

- **InboundHandler**：处理入站事件（读、连接建立等），从 head 向 tail 传播。
- **OutboundHandler**：处理出站事件（写、连接等），从 tail 向 head 传播。

## 三、常用 Handler

- ByteToMessageDecoder：字节解码为消息。
- MessageToByteEncoder：消息编码为字节。
- IdleStateHandler：心跳检测。
- LoggingHandler：日志。
- 自定义业务 Handler。

## 四、示例

```java
ch.pipeline()
  .addLast(new LengthFieldBasedFrameDecoder(...))
  .addLast(new MyDecoder())
  .addLast(new MyEncoder())
  .addLast(new BusinessHandler());
```

## 五、注意

- Handler 处理耗时操作应放到业务线程池，避免阻塞 EventLoop。
- Handler 加 @Sharable 注解可共享，否则每次新建。"""},

{"slug":"netty-bytebuf","title":"Netty 的 ByteBuf 设计？","content":"""## 一、与 ByteBuffer 对比

- JDK ByteBuffer 只有一个 position，读写切换需 flip()，易出错。
- ByteBuf 有 readerIndex 和 writerIndex 两个指针，读写分离，无需 flip。

## 二、核心方法

- readXxx() / writeXxx()：读写并移动指针。
- getXxx() / setXxx()：读写不移动指针。
- discardReadBytes()：丢弃已读数据，腾出空间。
- markReaderIndex() / resetReaderIndex()：标记重置。

## 三、内存管理

- **池化**：PooledByteBufAllocator，复用内存，减少 GC。
- **堆内/堆外**：heap buffer（JVM 堆）/ direct buffer（堆外，零拷贝）。
- **引用计数**：基于 ReferenceCounted，release() 释放。

## 四、零拷贝

- CompositeByteBuf：组合多个 ByteBuf，无需拷贝合并。
- wrap / slice：共享底层内存。
- FileRegion：文件传输零拷贝。

## 五、建议

- 生产用 PooledByteBufAllocator。
- 申请的 ByteBuf 必须 release，否则内存泄漏。"""},

{"slug":"netty-sticky-packet","title":"Netty 如何解决粘包拆包？","content":"""## 一、问题

TCP 是字节流，没有消息边界，多个请求可能粘在一起（粘包），一个请求可能被拆分（拆包）。

## 二、解决方案

1. **固定长度**：FixedLengthFrameDecoder，每条消息固定长度。
2. **特殊分隔符**：DelimiterBasedFrameDecoder，按换行符等分隔。
3. **长度字段**：LengthFieldBasedFrameDecoder，消息头带长度字段，最常用。
4. **自定义协议**：魔数 + 长度 + 序列号 + 数据体。

## 三、LengthFieldBasedFrameDecoder

```java
new LengthFieldBasedFrameDecoder(
    maxFrameLength,    // 最大帧长度
    lengthFieldOffset,  // 长度字段偏移
    lengthFieldLength,  // 长度字段长度
    lengthAdjustment,   // 长度调整
    initialBytesToStrip // 跳过的字节数
);
```

## 四、自定义协议示例

```
+--------+--------+----------+--------+
| 魔数    | 长度    | 序列号   | 数据   |
| 4 byte | 4 byte | 4 byte   | N byte |
+--------+--------+----------+--------+
```

## 五、注意

解码器放在 Pipeline 最前面，业务 Handler 在后面。"""},

{"slug":"netty-heartbeat","title":"Netty 如何实现心跳检测？","content":"""## 一、IdleStateHandler

Netty 提供 IdleStateHandler 检测空闲状态。

```java
ch.pipeline().addLast(new IdleStateHandler(5, 0, 0, TimeUnit.SECONDS));
```
三个参数：读空闲时间、写空闲时间、所有空闲时间。

## 二、事件触发

- readerIdleTime 超时 → IdleStateEvent.READER_IDLE
- writerIdleTime 超时 → IdleStateEvent.WRITER_IDLE
- allIdleTime 超时 → IdleStateEvent.ALL_IDLE

## 三、处理心跳

```java
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            if (state == IdleState.READER_IDLE) {
                ctx.close();  // 读超时，断开连接
            }
        }
    }
}
```

## 四、心跳机制

- 客户端定时发心跳包，服务端收到不响应或回响应。
- 服务端读超时未收到心跳，主动断开。
- 客户端写超时未收到响应，主动重连。

## 五、TCP keepalive

系统级 TCP keepalive 默认 2 小时，不适合业务心跳。应用层心跳更灵活。"""},
],

# ---------- 分库分表 ----------
"sharding": [
{"slug":"sharding-strategy","title":"分库分表有哪些策略？","content":"""## 一、垂直拆分

- **垂直分库**：按业务拆分库，如用户库、订单库、商品库。
- **垂直分表**：按字段拆分表，如主表存常用字段，扩展表存大字段。
- 解决：单库压力、单表字段过多。

## 二、水平拆分

- **水平分库**：同一张表数据按规则分到多个库。
- **水平分表**：同一张表数据按规则分到多张表。
- 解决：单表数据量过大。

## 三、分片规则

- **范围**：按时间或 ID 范围，如 2023 年一个表，2024 年一个表。易扩展但热点问题。
- **取模**：user_id % N，均匀分布但扩容需迁移。
- **一致性哈希**：节点变化时只迁移部分数据。
- **枚举**：按省份、状态等枚举值分片。

## 四、选择

- 数据量大且增长快 → 水平拆分。
- 业务清晰 → 垂直拆分。
- 生产常用：垂直分库 + 水平分表组合。"""},

{"slug":"sharding-key","title":"如何选择分片键？","content":"""## 一、选择原则

1. **查询频率高**：最常用的查询条件字段，如 user_id、order_id。
2. **分布均匀**：避免数据倾斜，如不要用性别（只有两个值）。
3. **业务关联**：关联表用相同分片键，减少跨库 join。
4. **不可变**：分片键值一旦确定不能改。

## 二、常见分片键

- **用户 ID**：用户相关数据，如订单按 user_id 分片。
- **订单 ID**：订单数据。
- **时间**：日志、流水等时序数据。

## 三、非分片键查询

- 没有分片键的查询需扫所有分片，效率低。
- 解决方案：
  - 建立索引表（非分片键 → 分片键映射）。
  - 冗余数据。
  - 用 ES 等搜索引擎做非分片键查询。

## 四、跨库 join

- 尽量避免，业务层组装。
- 用宽表冗余字段。
- 用 ES 查询后回源。"""},

{"slug":"sharding-id-gen","title":"分布式 ID 生成方案有哪些？","content":"""## 一、UUID

- 优点：本地生成，无网络开销，唯一。
- 缺点：无序，索引效率低；太长。

## 二、数据库自增

- 优点：简单，有序。
- 缺点：单库瓶颈；分库后不全局唯一。

## 三、号段模式

- 从数据库批量获取 ID 段（如 1-1000），在内存中使用。
- 优点：性能高，减少数据库压力。
- 缺点：服务重启可能断号。

## 四、Snowflake（雪花算法）

- 64 位：1 符号位 + 41 时间戳 + 10 机器 ID + 12 序列号。
- 优点：趋势递增，性能高，全局唯一。
- 缺点：依赖时钟，时钟回拨会重复。

## 五、Leaf（美团）

- 号段模式 + Snowflake 双模式。
- 解决时钟回拨问题。

## 六、Redis

- incr 生成，性能高。
- 需保证 Redis 高可用。"""},

{"slug":"sharding-migration","title":"分库分表扩容如何平滑迁移？","content":"""## 一、取模扩容问题

user_id % 4 改为 % 8，大部分数据需要迁移。

## 二、方案一：停机迁移

- 停机，写脚本把数据按新规则重新分配。
- 简单但有停机时间。

## 三、方案二：双写 + 迁移

1. 新数据同时写旧分片和新分片。
2. 后台把旧数据迁移到新分片。
3. 校验一致后，切读到新分片。
4. 停写旧分片。
- 不停机但复杂。

## 四、方案三：一致性哈希

- 新增节点只迁移相邻节点的部分数据。
- 取模扩容影响小。

## 五、方案四：预留分片

- 初始就分足够多的逻辑分片（如 1024），开始只分配少量库。
- 扩容时只需把部分逻辑分片迁移到新库，无需改规则。
- 推荐方案。

## 六、注意

- 迁移过程保证数据一致性。
- 做好回滚方案。"""},

{"slug":"sharding-shardingsphere","title":"ShardingSphere 的核心功能？","content":"""## 一、定位

ShardingSphere 是分布式数据库中间件，提供分库分表、读写分离、数据加密等能力。

## 二、三种模式

- **ShardingSphere-JDBC**：嵌入应用，JAR 包方式，性能好但只支持 Java。
- **ShardingSphere-Proxy**：独立部署代理，支持多语言，有网络开销。
- **ShardingSphere-Sidecar**：云原生，Service Mesh 模式。

## 三、核心功能

- **数据分片**：分库分表，支持多种分片算法。
- **分布式事务**：XA、BASE（柔性事务）。
- **读写分离**：主写从读，负载均衡。
- **数据加密**：透明加解密。
- **影子库**：压测数据隔离。

## 四、分片算法

- 精确分片（PreciseShardingAlgorithm）：= 和 IN。
- 范围分片（RangeShardingAlgorithm）：BETWEEN。
- 复合分片（ComplexKeysShardingAlgorithm）：多字段。
- Hint 分片：强制指定分片。

## 五、注意

- 不支持跨库事务（XA 除外，性能差）。
- 不支持跨库 join、子查询（部分支持）。
- 分布式主键需配置。"""},
],

# ---------- 高并发 ----------
"high-concurrency": [
{"slug":"hc-optimize","title":"高并发系统的优化手段有哪些？","content":"""## 一、前端优化

- CDN 加速静态资源。
- 浏览器缓存、资源合并压缩。
- 页面懒加载、预加载。

## 二、网关层

- Nginx 负载均衡，动静分离。
- 限流（令牌桶、漏桶）。
- 缓存静态页面。

## 三、应用层

- 多级缓存：本地缓存（Caffeine）+ 分布式缓存（Redis）。
- 异步化：消息队列削峰填谷。
- 线程池调优，合理设置参数。
- 无锁化：CAS、ThreadLocal、ConcurrentHashMap。
- 服务降级、熔断。

## 四、数据层

- 读写分离：主写从读。
- 分库分表。
- 索引优化，慢查询治理。
- 热数据缓存。

## 五、系统设计

- 无状态服务，方便水平扩展。
- 消息队列解耦。
- 微服务拆分，独立扩缩容。

## 六、监控

- 全链路监控，及时发现瓶颈。
- 压测验证容量。"""},

{"slug":"hc-cache-strategy","title":"高并发下的缓存策略？","content":"""## 一、缓存层级

- L1：浏览器缓存。
- L2：CDN 缓存。
- L3：Nginx/网关缓存。
- L4：应用本地缓存（Caffeine、Guava）。
- L5：分布式缓存（Redis）。
- L6：数据库。

## 二、缓存更新策略

- **Cache Aside**：应用先查缓存，没有查数据库，写入缓存。删除缓存（不是更新）。最常用。
- **Read/Write Through**：缓存层统一处理读写。
- **Write Behind**：异步写回数据库，性能高但可能丢数据。

## 三、热点数据

- 热点 key 永不过期 + 逻辑过期 + 异步刷新。
- 本地缓存挡热点，减少 Redis 压力。

## 四、缓存一致性

- 延迟双删：更新数据库 → 删缓存 → 延迟 500ms → 再删缓存。
- 订阅 binlog（canal）异步删缓存。
- 最终一致即可，不追求强一致。

## 五、缓存穿透/击穿/雪崩

见 Redis 相关题目。"""},

{"slug":"hc-current-limit","title":"常见的限流算法？","content":"""## 一、固定窗口

把时间分成固定窗口，窗口内计数，超过阈值限流。
- 优点：简单。
- 缺点：窗口边界可能突刺（如窗口末尾和下一个窗口开头各来一半，合起来超限）。

## 二、滑动窗口

把窗口分成多个小格子，随时间滑动，统计格子总数。
- 解决固定窗口的边界问题。
- 实现稍复杂。

## 三、漏桶算法

请求进桶，桶以固定速率流出，满了就拒绝。
- 输出速率恒定，平滑流量。
- 不能应对突发流量。

## 四、令牌桶算法

桶里以固定速率放令牌，请求取令牌，没令牌就拒绝。
- 允许突发流量（桶里有令牌时可瞬间取走）。
- 最常用。Sentinel、Guava RateLimiter 都基于此。

## 五、分布式限流

- Redis + Lua 实现令牌桶。
- Sentinel 集群限流。
- 网关层限流（Nginx、Gateway）。"""},

{"slug":"hc-degrade","title":"服务降级和熔断的区别？","content":"""## 一、降级（Degrade）

- 主动关闭非核心功能，保证核心功能可用。
- 如大促时关闭评论、推荐，保交易。
- 是一种策略，通常按预设规则触发。

## 二、熔断（Circuit Breaker）

- 下游服务故障时，上游不再调用，直接返回降级结果。
- 类似电路保险丝，防止故障蔓延。
- 基于错误率、响应时间等指标自动触发。

## 三、熔断器状态

- **Closed**：正常调用，统计指标。
- **Open**：达到阈值，直接返回降级响应。
- **Half-Open**：放少量请求试探，成功则关闭，失败则继续打开。

## 四、实现

- Sentinel：熔断规则（慢调用比例、异常比例、异常数）。
- Hystrix：已停更。
- Resilience4j：轻量。

## 五、降级方案

- 返回兜底数据。
- 返回缓存数据。
- 返回默认值或提示。
- 排队等待（异步处理）。"""},

{"slug":"hc-seckill","title":"秒杀系统如何设计？","content":"""## 一、挑战

高并发、库存有限、防超卖、防刷。

## 二、优化手段

### 1. 前端
- 按钮置灰，防止重复提交。
- 答题/验证码防机器人。

### 2. 网关层
- 限流（用户维度、IP 维度）。
- 静态资源 CDN。

### 3. 应用层
- Redis 预减库存，判断是否还有库存。
- 异步下单：请求写 MQ，消费者处理订单。
- 本地缓存挡热点商品。

### 4. 数据层
- Redis 存库存，扣减用 Lua 保证原子。
- 数据库乐观锁防超卖：update stock set count=count-1 where id=? and count>0。
- 订单表唯一索引（user_id + goods_id）防重复下单。

## 三、流程

1. 用户请求秒杀 → 网关限流。
2. Redis 检查库存（Lua 原子操作）。
3. 有库存 → 扣减 + 发 MQ。
4. 消费者创建订单 + 数据库扣库存。
5. 返回排队中，客户端轮询结果。

## 四、关键

- 库存用 Redis 挡大部分流量。
- MQ 削峰，数据库只处理真实订单。
- 幂等：用户+商品唯一。"""},
],

# ---------- 高可用 ----------
"high-availability": [
{"slug":"ha-redis-cluster","title":"Redis 高可用方案有哪些？","content":"""## 一、主从复制

- Master 写，Slave 异步复制数据。
- 读可从 Slave，分担读压力。
- Master 挂了需手动切换，不能自动故障转移。

## 二、哨兵模式（Sentinel）

- Sentinel 监控 Master/Slave 健康。
- Master 故障时自动选举新 Master，通知客户端。
- 适合中小规模。

## 三、集群模式（Cluster）

- 数据分片到多个节点，每片有主从。
- 16384 个槽位，按 key 的 CRC16 % 16384 分配。
- 客户端直连节点，自动路由。
- 支持水平扩展，自动故障转移。
- 适合大规模。

## 四、对比

| 方案 | 数据量 | 故障转移 | 扩展 |
|------|--------|---------|------|
| 主从 | 单机 | 手动 | 读扩展 |
| 哨兵 | 单机 | 自动 | 读扩展 |
| 集群 | 分片 | 自动 | 读写扩展 |

## 五、选择

- 数据量小，读多写少 → 哨兵。
- 数据量大，需水平扩展 → 集群。"""},

{"slug":"ha-db-ha","title":"MySQL 高可用方案？","content":"""## 一、主从复制

- Master 写，Slave 读，异步复制。
- Master 挂了需手动切换。
- 可能数据丢失（异步复制延迟）。

## 二、MHA（Master High Availability）

- 自动监控 Master，故障时自动切换。
- 需额外管理节点。

## 三、MGR（MySQL Group Replication）

- 基于 Paxos，多主复制，强一致。
- 至少 3 节点，自动故障转移。
- MySQL 5.7+ 支持。

## 四、Galera Cluster

- 多主同步复制，强一致。
- 任意节点读写。
- 写入需所有节点确认，性能稍低。

## 五、云方案

- 云厂商 RDS：自动主备、备份、监控。
- 自建成本高，云方案省心。

## 六、读写分离

- 中间件（ShardingSphere-Proxy、MyCat）实现读写分离。
- 应用层配置多个数据源。
- 注意主从延迟，关键查询走主库。"""},

{"slug":"ha-service-ha","title":"微服务高可用设计？","content":"""## 一、无状态服务

- 服务不保存状态，状态存 Redis/DB。
- 多实例部署，负载均衡。
- 任一实例挂了不影响。

## 二、服务发现

- 注册中心（Nacos/Eureka）。
- 服务实例上下线自动感知。
- 客户端负载均衡跳过不健康节点。

## 三、健康检查

- 注册中心心跳检测，不健康实例剔除。
- K8s liveness/readiness 探针。

## 四、熔断降级

- Sentinel/Hystrix 防止故障蔓延。
- 降级返回兜底数据。

## 五、限流

- 保护服务不被压垮。
- 网关限流 + 服务限流双层。

## 六、多机房

- 同城双活、异地多活。
- 数据同步（DTS、消息队列）。

## 七、灰度发布

- 金丝雀发布，逐步放量。
- 出现问题快速回滚。"""},

{"slug":"ha-cap","title":"CAP 定理和 BASE 理论？","content":"""## 一、CAP 定理

分布式系统不可能同时满足三个特性，最多满足两个：

- **Consistency（一致性）**：所有节点同一时刻看到相同数据。
- **Availability（可用性）**：每个请求都能收到响应（不一定是最新数据）。
- **Partition tolerance（分区容错性）**：网络分区时系统仍能运行。

## 二、CP 系统

- 保证一致性和分区容错，牺牲可用性。
- 网络分区时拒绝服务。
- 如 ZooKeeper、HBase、MongoDB（强一致模式）。

## 三、AP 系统

- 保证可用性和分区容错，牺牲一致性。
- 网络分区时仍响应，但可能返回旧数据。
- 如 Eureka、Cassandra、DynamoDB。

## 四、为什么 P 必须选

分布式系统网络分区不可避免，所以 P 必须满足，只能在 C 和 A 之间选择。

## 五、BASE 理论

CAP 的延伸，适合大规模分布式：

- **Basically Available（基本可用）**：允许损失部分可用性。
- **Soft state（软状态）**：允许中间状态。
- **Eventually consistent（最终一致）**：最终达到一致。

## 六、应用

- 支付、交易 → 强一致（CP）。
- 社交、电商详情页 → 最终一致（AP）。"""},

{"slug":"ha-fault-tolerant","title":"如何设计故障容错的系统？","content":"""## 一、冗余

- 多实例部署，避免单点。
- 数据多副本（主从、集群）。
- 多机房部署。

## 二、故障检测

- 心跳检测，快速发现故障。
- 全链路监控，告警。

## 三、故障转移

- 自动切换（哨兵、集群选主）。
- 手动切流（流量调度）。

## 四、降级

- 非核心功能降级，保核心。
- 返回兜底数据。

## 五、熔断

- 下游故障快速失败，防止雪崩。

## 六、限流

- 保护自身不被打垮。

## 七、回滚

- 发布支持快速回滚。
- 数据变更可回滚。

## 八、预案

- 故障演练，定期验证。
- 应急手册。

## 九、关键原则

- 故障不可避免，要假设任何组件都会挂。
- 优先保证核心链路可用。
- 能自动恢复的不人工介入。"""},
],

# ---------- Elasticsearch ----------
"elasticsearch": [
{"slug":"es-architecture","title":"Elasticsearch 的核心概念？","content":"""## 一、核心概念

- **Index（索引）**：类似数据库的表，存储文档。
- **Document（文档）**：JSON 格式的数据，类似行。
- **Field（字段）**：文档中的键值对，类似列。
- **Shard（分片）**：索引分成多个分片，分布在不同节点。
- **Replica（副本）**：分片的副本，高可用。

## 二、集群

- 多个 Node 组成 Cluster。
- 一个 Master 节点管理集群状态。
- Data 节点存储数据。
- Coordinating 节点路由请求。

## 三、倒排索引

- ES 用倒排索引实现快速全文检索。
- 词 → 文档列表的映射。
- 分词后建立索引，查询时匹配词，找到文档。

## 四、写入流程

1. 文档写入 Primary Shard。
2. 同步到 Replica Shard。
3. 写入 TransLog（防止数据丢失）。
4. Refresh 后可被搜索（写入内存的 Segment）。
5. Flush 把 Segment 持久化到磁盘。

## 五、搜索流程

1. 协调节点把请求广播到所有相关分片。
2. 每个分片本地查询，返回 Top N。
3. 协调节点合并排序，返回结果。"""},

{"slug":"es-inverted-index","title":"Elasticsearch 的倒排索引原理？","content":"""## 一、定义

正排索引：文档 → 词列表。
倒排索引：词 → 文档列表。

## 二、构建过程

1. 文档内容分词，得到词（term）。
2. 建立 term → [doc1, doc2, ...] 的映射。
3. 记录词频、位置等信息。

## 三、示例

```
文档1：Java 面试
文档2：Java 集合
文档3：Redis 缓存

倒排索引：
Java → [1, 2]
面试 → [1]
集合 → [2]
Redis → [3]
缓存 → [3]
```

查询 "Java" 直接得到文档 1、2。

## 四、分词

- 英文：按空格和标点分词，转小写，去停用词。
- 中文：IK 分词器（ik_smart 粗粒度、ik_max_word 细粒度）。
- 也可指定 keyword 不分词（精确匹配）。

## 五、相关度评分

- TF-IDF：词频 × 逆文档频率。
- BM25：ES 默认，TF-IDF 的改进版，考虑词频饱和。"""},

{"slug":"es-query","title":"Elasticsearch 常用查询类型？","content":"""## 一、全文查询

- **match**：对查询词分词后匹配。
- **match_phrase**：短语匹配，词序固定。
- **multi_match**：多字段匹配。
- **query_string**：支持 Lucene 查询语法。

## 二、精确查询

- **term**：精确匹配不分词的值。
- **terms**：多值精确匹配。
- **range**：范围查询。
- **exists**：字段存在。

## 三、复合查询

- **bool**：组合 must/should/must_not/filter。
- **filter**：过滤，不评分，可缓存。
- **constant_score**：固定评分。

## 四、示例

```json
{
  "query": {
    "bool": {
      "must": [{ "match": { "title": "Java" } }],
      "filter": [{ "range": { "price": { "gte": 100 } } }]
    }
  }
}
```

## 五、注意

- 过滤用 filter，不参与评分，性能好。
- 避免深度分页（用 scroll 或 search_after）。"""},

{"slug":"es-cluster","title":"Elasticsearch 集群架构？","content":"""## 一、节点角色

- **Master**：管理集群元数据，选举。
- **Data**：存储分片数据，执行 CRUD、搜索。
- **Ingest**：数据预处理（pipeline）。
- **Coordinating**：路由请求，合并结果。
- **Machine Learning**：机器学习。
- **Remote Cluster**：跨集群搜索。

## 二、分片

- Index 创建时指定主分片数（不可改）和副本数（可改）。
- 主分片负责写入，副本分片提供读和高可用。
- 分片数预估：单分片不超过 50GB，单节点分片不超过 1000。

## 三、选举

- 基于 Bully 算法。
- master-eligible 节点参与选举。
- 半数以上同意才选为 Master。

## 四、脑裂

- 网络分区导致两个 Master。
- 配置 discovery.zen.minimum_master_nodes = N/2 + 1 防止。

## 五、扩展

- 加节点：自动 rebalance 分片。
- 扩副本：API 修改副本数。
- 主分片数不可变，需重建索引。"""},

{"slug":"es-optimize","title":"Elasticsearch 性能优化？","content":"""## 一、写入优化

- 批量写入（Bulk API），减少网络开销。
- 增加 refresh_interval（如 30s），减少 refresh 频率。
- 关闭副本，写完再开。
- 用 SSD 存储。
- TransLog 异步刷盘。

## 二、查询优化

- 用 filter 替代 query 做过滤。
- 避免深度分页，用 search_after。
- 只查需要的字段（_source 过滤）。
- 合理设置分片数，避免分片过多。

## 三、索引设计

- 合理映射，避免默认动态映射。
- keyword 类型精确查询，text 类型全文检索。
- 大字段 store=false，不存储原始值。
- 索引生命周期管理（ILM），冷热数据分离。

## 四、集群优化

- 主分片数 = 节点数或倍数。
- 副本数根据读压力和可用性调整。
- 内存：堆内存不超过 32GB，留给文件系统缓存。
- 路由：自定义路由，把相关数据放同一分片。

## 五、常见问题

- 慢查询：检查查询语句、分片数、索引设计。
- OOM：检查聚合、深度分页、字段数量。
- 集群黄/红：检查分片分配、节点状态。"""},
],

# ---------- RAG ----------
"rag": [
{"slug":"rag-what","title":"什么是 RAG？","content":"""## 一、定义

RAG（Retrieval-Augmented Generation，检索增强生成）把外部知识检索和大语言模型生成结合起来。先从知识库检索相关文档，再让 LLM 基于检索结果生成回答。

## 二、解决的问题

- LLM 知识截止，不知道最新信息。
- LLM 幻觉，编造内容。
- LLM 不知道企业私有知识。
- RAG 通过检索真实文档，让回答有据可依。

## 三、基本流程

1. **索引阶段**：文档切分 → 向量化 → 存入向量数据库。
2. **检索阶段**：用户问题向量化 → 在向量库检索 Top-K 相关文档。
3. **生成阶段**：把检索结果拼到 Prompt → 调用 LLM 生成回答。

## 四、对比

- **Fine-tune**：改变模型参数，适合风格/格式调整，知识更新需重训。
- **RAG**：不改模型，知识存在向量库，更新只需更新文档。
- 实际常结合：RAG 做知识注入，Fine-tune 做风格对齐。"""},

{"slug":"rag-embedding","title":"RAG 中的 Embedding 和向量数据库？","content":"""## 一、Embedding（嵌入）

把文本转换为高维向量（如 768/1024 维），语义相近的文本向量距离近。

## 二、向量数据库

专门存储和检索向量的数据库，支持相似性搜索。

- **Milvus**：开源，高性能，分布式。
- **Pinecone**：云服务。
- **Weaviate**：开源，支持混合检索。
- **Chroma**：轻量，适合原型。
- **FAISS**：Meta 开源的向量检索库，非数据库。

## 三、相似度计算

- 余弦相似度：方向相近，与长度无关，最常用。
- 内积：考虑长度。
- 欧氏距离：空间距离。

## 四、检索方式

- **向量检索**：语义相似，能找到同义表达。
- **关键词检索（BM25）**：精确词匹配。
- **混合检索**：向量 + 关键词，召回更全面。

## 五、Embedding 模型

- OpenAI text-embedding-3。
- BGE（智源）：中文效果好。
- M3E、GTE：中文开源。
- 选择时考虑语言、维度、性能。"""},

{"slug":"rag-chunking","title":"RAG 中的文档分块策略？","content":"""## 一、为什么要分块

文档太长无法直接向量化（Embedding 有 token 限制），且 LLM 上下文有限。分块后每块独立检索。

## 二、分块方法

### 1. 固定长度分块

按 token 数切分，如每块 500 token，重叠 50 token。
- 优点：简单。
- 缺点：可能切断语义。

### 2. 按结构分块

按标题、段落、章节切分。
- 优点：保留语义。
- 缺点：实现复杂。

### 3. 语义分块

用语义相似度判断切分点。
- 优点：语义完整。
- 缺点：开销大。

### 4. 父子分块

小块用于检索，大块用于 LLM 生成。

## 三、分块大小选择

- 太小：丢失上下文。
- 太大：噪声多，检索不精准。
- 经验：500-1000 token，重叠 10-20%。

## 四、注意

- 保留元数据（来源、标题、页码），便于溯源。
- 结构化文档（表格、代码）需特殊处理。"""},

{"slug":"rag-evaluation","title":"如何评估 RAG 系统的效果？","content":"""## 一、评估维度

- **检索质量**：检索到的文档是否相关。
- **生成质量**：回答是否准确、完整、流畅。
- **端到端效果**：整体回答是否满足用户。

## 二、检索评估指标

- **Recall（召回率）**：相关文档中被检索到的比例。
- **Precision（精确率）**：检索到的文档中相关的比例。
- **MRR**：第一个相关文档的排名倒数。
- **NDCG**：考虑排名的相关性。

## 三、生成评估指标

- **Faithfulness（忠实度）**：回答是否基于检索文档，无幻觉。
- **Answer Relevancy（回答相关性）**：回答是否切题。
- **Context Precision（上下文精确率）**：检索上下文是否相关。
- **Context Recall（上下文召回率）**：关键信息是否被检索到。

## 四、评估方法

- **人工评估**：准确但慢。
- **LLM-as-Judge**：用 LLM 打分，快但有偏差。
- **RAGAS**：开源 RAG 评估框架，自动计算上述指标。
- **TruLens、DeepEval**：其他评估工具。

## 五、优化方向

- 检索差 → 优化分块、Embedding、检索方式。
- 生成差 → 优化 Prompt、调 LLM。
- 端到端差 → 考虑重排（Rerank）、多轮检索。"""},

{"slug":"rag-optimize","title":"RAG 系统的优化手段？","content":"""## 一、检索优化

- **分块优化**：合理的块大小和重叠。
- **Embedding 优化**：用更适合的模型，微调。
- **混合检索**：向量 + 关键词，提升召回。
- **重排（Rerank）**：用 Cross-Encoder 对检索结果重排序，提升精度。
- **查询改写**：用 LLM 改写用户问题，提升检索效果。
- **多查询**：生成多个相关问题分别检索，合并结果。

## 二、生成优化

- **Prompt 工程**：清晰的指令，要求基于上下文回答。
- **上下文压缩**：对检索结果做摘要或提取关键句，减少噪声。
- **引用溯源**：回答中标注来源，增加可信度。
- **迭代生成**：多轮检索 + 生成，处理复杂问题。

## 三、工程优化

- **缓存**：相似问题复用检索结果。
- **异步**：索引构建异步，不阻塞查询。
- **降级**：检索失败时降级为直接生成或返回无答案。
- **监控**：记录查询、检索、生成的质量指标。

## 四、最新趋势

- **GraphRAG**：知识图谱增强，处理复杂关系。
- **Agentic RAG**：用 Agent 决定何时检索、如何检索。
- **Self-RAG**：模型自己判断是否需要检索。"""},
],

# ---------- AI Agent ----------
"ai-agent": [
{"slug":"agent-what","title":"什么是 AI Agent？","content":"""## 一、定义

AI Agent 是能感知环境、自主决策并执行动作的智能体。它能基于目标，自主规划、调用工具、执行任务，而不仅仅是回答问题。

## 二、核心能力

- **感知**：理解用户输入和环境状态。
- **规划**：把目标拆解为步骤。
- **记忆**：短期（对话上下文）和长期（向量库）记忆。
- **工具调用**：调用搜索、代码执行、API 等工具。
- **执行**：执行动作并观察结果。
- **反思**：根据结果调整策略。

## 三、与普通 LLM 区别

- 普通 LLM：输入 → 输出，一次完成。
- Agent：目标 → 规划 → 行动 → 观察 → 再规划，多轮循环，直到达成目标。

## 四、应用

- 智能助手：自动完成复杂任务。
- 代码 Agent：自动写代码、调试、测试。
- 数据分析 Agent：自动分析数据、生成报告。
- 客服 Agent：自动处理多轮对话、调用业务系统。

## 五、挑战

- 规划能力有限。
- 工具调用可靠性。
- 长任务的记忆和状态管理。
- 成本和延迟。"""},

{"slug":"agent-react","title":"ReAct 模式是什么？","content":"""## 一、定义

ReAct = Reasoning + Acting，是 Agent 的经典工作模式。交替进行推理和行动。

## 二、流程

1. **Thought（思考）**：分析当前状态，决定下一步。
2. **Action（行动）**：调用工具或执行操作。
3. **Observation（观察）**：获取执行结果。
4. 循环 1-3，直到任务完成。

## 三、示例

```
Question: 北京今天天气怎么样？
Thought: 我需要查询北京今天的天气。
Action: search("北京今天天气")
Observation: 北京今天晴，气温 15-25°C。
Thought: 我已获得天气信息。
Answer: 北京今天晴，气温 15-25°C。
```

## 四、优点

- 把推理和行动结合，可解决需要外部信息的问题。
- 过程可解释，便于调试。

## 五、扩展

- **Reflexion**：加入反思，失败后总结经验。
- **Tree of Thoughts**：树形搜索，探索多种思路。
- **Plan-and-Execute**：先规划整体步骤，再逐步执行。
- **多 Agent 协作**：多个 Agent 分工合作。"""},

{"slug":"agent-tools","title":"AI Agent 如何调用工具？","content":"""## 一、Function Calling

LLM 支持的函数调用能力。开发者定义工具 schema（名称、参数、描述），LLM 决定是否调用及参数，返回结构化 JSON。

## 二、流程

1. 定义工具：名称、描述、参数 JSON Schema。
2. 用户提问，LLM 判断需要调用工具。
3. LLM 返回工具名和参数。
4. 应用执行工具，得到结果。
5. 把结果返回 LLM，生成最终回答。

## 三、工具类型

- **搜索工具**：WebSearch、知识库检索。
- **代码执行**：Python、Shell。
- **API 调用**：数据库、第三方服务。
- **文件操作**：读写文件。
- **浏览器**：网页操作。

## 四、设计原则

- 工具描述清晰，LLM 才能正确选择。
- 参数定义准确，减少调用错误。
- 工具结果简洁，不浪费 token。
- 错误处理：工具失败时 LLM 可重试或换策略。

## 五、安全

- 工具调用需权限控制，防止滥用。
- 危险操作（删除、转账）需用户确认。
- 沙箱隔离执行环境。"""},

{"slug":"agent-memory","title":"AI Agent 的记忆机制？","content":"""## 一、短期记忆

- 当前对话上下文。
- 受 LLM 上下文窗口限制。
- 太长会丢失早期信息，浪费 token。

## 二、长期记忆

- 向量数据库存储历史交互、知识。
- 检索相关记忆注入上下文。
- 支持跨会话记忆。

## 三、记忆类型

- **情景记忆**：具体的交互经历。
- **语义记忆**：事实和知识。
- **程序记忆**：技能和流程。

## 四、记忆管理

- **写入**：重要信息提取后存向量库。
- **检索**：根据当前任务召回相关记忆。
- **遗忘**：清理过时、不重要的记忆。
- **压缩**：对长对话做摘要，减少 token。

## 五、实现

- MemGPT / Letta：分层记忆管理。
- LangChain Memory：对话记忆。
- 向量库 + 摘要 + 时间窗口。

## 六、挑战

- 记忆的相关性和准确性。
- 记忆冲突处理。
- 隐私保护。"""},

{"slug":"agent-framework","title":"主流的 AI Agent 框架有哪些？","content":"""## 一、LangChain / LangGraph

- LangChain：LLM 应用编排，支持工具、记忆、RAG。
- LangGraph：基于图的 Agent 编排，适合复杂多步任务。
- 生态最丰富，社区活跃。

## 二、AutoGen

- 微软出品，多 Agent 对话框架。
- 支持多个 Agent 协作，角色灵活。
- 适合复杂任务拆解。

## 三、CrewAI

- 面向多 Agent 协作，定义角色和任务。
- 简洁易用。

## 四、LlamaIndex

- 数据框架，擅长 RAG。
- 也支持 Agent。

## 五、Semantic Kernel

- 微软出品，.NET/Python。
- 企业级，与 Azure 集成好。

## 六、OpenAI Agents SDK

- OpenAI 官方，轻量。
- 原生支持 Function Calling、HandOff（Agent 间交接）。

## 七、选择

- 快速原型 → LangChain。
- 复杂编排 → LangGraph / AutoGen。
- RAG 为主 → LlamaIndex。
- 企业 .NET → Semantic Kernel。

## 八、趋势

- 多 Agent 协作。
- 标准化 Agent 协议（MCP）。
- 端到端 Agent 开发平台。"""},
],

}  # END QUESTIONS


def main():
    if APPLY and not HALO_PAT:
        print("错误：--apply 需要 HALO_PAT")
        sys.exit(1)

    total = sum(len(v) for v in QUESTIONS.values())
    print(f"共 {len(QUESTIONS)} 个专题，{total} 道题")
    print(f"模式：{'正式执行' if APPLY else '试运行'}")

    admin = HaloAdmin(HALO_URL, HALO_PAT)
    if APPLY:
        existing = admin.list_post_slugs()
    else:
        existing = set()

    created = 0
    skipped = 0
    failed = 0

    for category, items in QUESTIONS.items():
        for item in items:
            if item["slug"] in existing:
                skipped += 1
                continue
            if not APPLY:
                continue
            ok, msg = admin.create_and_publish_post(
                item["title"], item["slug"], item["content"], category
            )
            if ok:
                created += 1
                print(f"  [发布] {item['title']}")
            else:
                failed += 1
                print(f"  [失败] {item['title']}：{msg}")

    print(f"\n完成。新增 {created}，跳过 {skipped}，失败 {failed}")


if __name__ == "__main__":
    main()
