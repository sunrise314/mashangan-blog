#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Java 面试八股题库种子脚本
=================================
在 Halo 中创建：
  1. 父分类「Java 面试题 | 八股文」（slug = java-interview）
  2. 24 个专题子分类（Java 基础 / 集合 / JVM / 并发 / MySQL / Redis ...）
  3. 3 道原创示例题（挂在「Java 基础面试题」下，演示题号、TOC、上一题/下一题效果）

注意：分类不能勾选「不在列表中隐藏」——Halo 会连带隐藏其下所有文章，
公开 API 将查不到题目。脚本统一给 25 个分类打标签
metadata.labels["haloweb.section"] = "interview"，由前台在首页/归档中排除。

前台 /java-interview 页面由 web/pages/java-interview 渲染，
题目内容请自行在 Halo 后台编写发布，不要搬运他人受版权保护的题库内容。

用法：
  # 先试运行（只打印将要创建的内容，不写入；无需 PAT）
  python seed_interview.py

  # 真正执行
  HALO_PAT=pat_xxxx python seed_interview.py --apply

可重复执行：已存在的分类/文章会自动跳过，不会重复创建。

环境变量：
  HALO_URL   Halo 地址，默认 http://49.235.136.65:8090
  HALO_PAT   个人访问令牌（Halo 后台「个人中心 - PAT」生成，pat_ 开头）

依赖：
  python -m pip install requests markdown
"""

import os
import sys

import requests

try:
    import markdown as md_lib
except ImportError:
    print("缺少依赖：请先执行  python -m pip install markdown")
    sys.exit(1)


def render_markdown(text: str) -> str:
    """渲染成与 Halo 默认编辑器一致的 HTML（代码块/表格/删除线/目录扩展）"""
    return md_lib.markdown(
        text,
        extensions=["fenced_code", "tables", "sane_lists"],
        output_format="html",
    )

HALO_URL = os.environ.get("HALO_URL", "http://49.235.136.65:8090").rstrip("/")
HALO_PAT = os.environ.get("HALO_PAT", "").strip()
APPLY = "--apply" in sys.argv

CONTENT_API = "/apis/api.content.halo.run/v1alpha1"
CONSOLE_API = "/apis/api.console.halo.run/v1alpha1"
# 分类的增删改走 Halo 通用扩展资源接口（console 下没有独立的 categories 端点）
EXT_API = "/apis/content.halo.run/v1alpha1"

# 栏目分区标签（前台据此把题库分类从首页教程列表/归档中排除）
SECTION_LABEL_KEY = "haloweb.section"
SECTION_LABEL_VAL = "interview"

# 24 个专题（顺序即前台展示顺序）
TOPICS = [
    ("java-basic", "Java 基础面试题"),
    ("java-collection", "Java 集合面试题"),
    ("mysql", "MySQL 面试题"),
    ("redis", "Redis 面试题"),
    ("rocketmq", "RocketMQ 面试题"),
    ("mybatis", "MyBatis 面试题"),
    ("spring-cloud", "Spring Cloud 面试题"),
    ("java-concurrent", "Java 并发面试题"),
    ("design-pattern", "设计模式面试题"),
    ("spring", "Spring 面试题"),
    ("tomcat", "Tomcat 面试题"),
    ("zookeeper", "Zookeeper 面试题"),
    ("rabbitmq", "RabbitMQ 面试题"),
    ("jvm", "JVM 面试题"),
    ("network", "计算机网络面试题"),
    ("os", "操作系统面试题"),
    ("dubbo", "Dubbo 面试题"),
    ("rag", "RAG 面试题"),
    ("ai-agent", "AI Agent 面试题"),
    ("netty", "Netty 面试题"),
    ("sharding", "分库分表面试题"),
    ("high-concurrency", "高并发面试题"),
    ("high-availability", "高可用面试题"),
    ("elasticsearch", "Elasticsearch 面试题"),
]

ROOT_SLUG = "java-interview"
ROOT_NAME = "Java 面试题 | 八股文"

# 原创示例题（Markdown），仅用于演示，可在后台删除或修改
SAMPLE_POSTS = [
    {
        "slug": "double-equals-vs-equals",
        "title": "== 和 equals 的区别是什么？",
        "content": """## 一、== 的作用

`==` 是 Java 的运算符，它的比较规则取决于两边的类型：

- **基本数据类型**（byte、short、int、long、float、double、char、boolean）：比较的是**字面值**是否相等。
- **引用数据类型**：比较的是两个引用是否指向堆中的**同一个对象**（即内存地址是否相同）。

```java
int a = 10;
int b = 10;
System.out.println(a == b); // true，基本类型比较值

String s1 = new String("hello");
String s2 = new String("hello");
System.out.println(s1 == s2); // false，两个不同对象
```

## 二、equals 的作用

`equals` 是 `Object` 类定义的方法，源码如下：

```java
public boolean equals(Object obj) {
    return (this == obj);
}
```

可以看到，`Object` 的默认实现仍然是用 `==` 比较地址。但很多类（典型如 `String`、`Integer`）**重写**了 `equals`，改为比较内容：

```java
String s1 = new String("hello");
String s2 = new String("hello");
System.out.println(s1.equals(s2)); // true，String 重写后比较内容
```

## 三、总结

| 对比项 | == | equals |
| --- | --- | --- |
| 基本类型 | 比较值 | 不适用 |
| 引用类型 | 比较内存地址 | 默认比较地址，重写后可比较内容 |
| 是否可重写 | 运算符，不可重写 | 方法，可以按业务规则重写 |

> 小提示：重写 `equals` 时务必同时重写 `hashCode`，否则在 HashMap、HashSet 等基于哈希的集合中会出现行为异常。
""",
    },
    {
        "slug": "string-stringbuilder-stringbuffer",
        "title": "String、StringBuilder 和 StringBuffer 的区别？",
        "content": """## 一、可变性不同

- `String` 是**不可变类**，内部用 `final` 修饰字符数组。对字符串做拼接实际上会创建新的 String 对象。
- `StringBuilder` 与 `StringBuffer` 都继承自 `AbstractStringBuilder`，内容**可变**，拼接时直接修改内部数组。

## 二、线程安全不同

- `String` 不可变，天然线程安全。
- `StringBuffer` 的方法加了 `synchronized`，是**线程安全**的。
- `StringBuilder` 没有同步措施，**线程不安全**，但单线程下性能更好。

## 三、性能对比

大量字符串拼接时：

```java
// 差：循环中产生大量临时对象
String s = "";
for (int i = 0; i < 1000; i++) {
    s = s + i;
}

// 推荐：单线程使用 StringBuilder
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 1000; i++) {
    sb.append(i);
}
```

## 四、选型建议

| 场景 | 推荐类型 |
| --- | --- |
| 少量字符串、不需要修改 | String |
| 单线程大量拼接（方法内局部变量最常见） | StringBuilder |
| 多线程共享、需要保证安全 | StringBuffer |
""",
    },
    {
        "slug": "why-override-equals-and-hashcode",
        "title": "为什么重写 equals 时一定要重写 hashCode？",
        "content": """## 一、先说结论

这是 Java 的**通用约定**：如果两个对象通过 `equals` 比较相等，那么它们的 `hashCode` 必须相同。只重写 `equals` 不重写 `hashCode`，会破坏这个约定，导致对象在 HashMap、HashSet 等集合中行为异常。

## 二、HashMap 的存取过程

HashMap 先根据 key 的 `hashCode` 定位哈希桶，再在桶内通过 `equals` 判断 key 是否相等：

1. **put**：`hashCode` → 找桶 → 桶内 `equals` 比较 → 决定覆盖还是新增。
2. **get**：同样先算 `hashCode` 找桶，再用 `equals` 匹配。

## 三、不重写会出什么问题

假设只重写了 `equals`（按业务字段比较内容），而用默认的 `hashCode`（基于对象地址）：

```java
Map<User, String> map = new HashMap<>();
map.put(new User(1L, "张三"), "data");

// 另一个内容相等的对象去查
String v = map.get(new User(1L, "张三")); // null！
```

两个对象 `equals` 相等但 `hashCode` 不同，get 时定位到了不同的桶，自然找不到。

## 四、正确做法

- 两个对象 `equals` 为 true → `hashCode` 必须相等。
- `hashCode` 相等的对象，`equals` 不一定为 true（允许哈希冲突）。
- 现代项目建议直接用 IDE 生成，或用 Lombok 的 `@EqualsAndHashCode`。
""",
    },
]


class HaloAdmin:
    def __init__(self, base: str, pat: str = ""):
        self.base = base
        self.s = requests.Session()
        headers = {"Accept": "application/json", "Content-Type": "application/json"}
        if pat:
            headers["Authorization"] = f"Bearer {pat}"
        self.s.headers.update(headers)

    def list_categories(self):
        r = self.s.get(f"{self.base}{CONTENT_API}/categories?size=1000")
        r.raise_for_status()
        return {c["spec"]["slug"]: c for c in r.json()["items"]}

    def create_category(self, name: str, display_name: str, children=None, priority=0):
        body = {
            "apiVersion": "content.halo.run/v1alpha1",
            "kind": "Category",
            "metadata": {
                "name": name,
                "labels": {SECTION_LABEL_KEY: SECTION_LABEL_VAL},
            },
            "spec": {
                "displayName": display_name,
                "slug": name,
                "cover": "",
                "description": "",
                "template": "",
                # 必须可见：分类隐藏会导致其下文章被公开 API 一并隐藏
                "hideFromList": False,
                "preventParentPostCascadeQuery": False,
                "priority": priority,
                "children": children or [],
            },
        }
        r = self.s.post(f"{self.base}{EXT_API}/categories", json=body)
        return r

    def get_category_raw(self, name: str):
        """扩展接口读取分类原始对象（公开 VO 带 postCount 等字段，不能直接回写）"""
        r = self.s.get(f"{self.base}{EXT_API}/categories/{name}")
        r.raise_for_status()
        return r.json()

    def update_category(self, name: str, body: dict):
        r = self.s.put(f"{self.base}{EXT_API}/categories/{name}", json=body)
        return r

    def map_post_names_by_slug(self):
        """公开接口拉取全部文章，建立 slug -> metadata.name 映射"""
        r = self.s.get(f"{self.base}{CONTENT_API}/posts?size=1000")
        r.raise_for_status()
        return {p["spec"]["slug"]: p["metadata"]["name"] for p in r.json()["items"]}

    def update_content_and_publish(self, post_name: str, markdown: str):
        """更新已发布文章的正文（Halo 不做服务端渲染，content 必须是渲染好的 HTML）"""
        payload = {"raw": markdown, "content": render_markdown(markdown), "rawType": "Markdown"}
        u = self.s.put(f"{self.base}{CONSOLE_API}/posts/{post_name}/content", json=payload)
        if u.status_code != 200:
            return False, f"content update failed: {u.status_code} {u.text[:200]}"
        p = self.s.put(f"{self.base}{CONSOLE_API}/posts/{post_name}/publish")
        if p.status_code != 200:
            return False, f"publish failed: {p.status_code} {p.text[:200]}"
        return True, post_name

    def create_and_publish_post(self, title: str, slug: str, markdown: str, category_name: str):
        import uuid
        post_name = f"post-{slug}-{uuid.uuid4().hex[:8]}"
        # 关键：rawType 必须是 "Markdown"（首字母大写），
        # content 必须由调用方预先渲染为 HTML，Halo 只按 HTML 存储与展示
        payload = {
            "post": {
                "apiVersion": "content.halo.run/v1alpha1",
                "kind": "Post",
                "metadata": {"name": post_name},
                "spec": {
                    "title": title,
                    "slug": slug,
                    "categories": [category_name],
                    "tags": [],
                    "cover": "",
                    "excerpt": {"raw": "", "autoGenerate": True},
                    "publish": False,
                    "visible": "PUBLIC",
                    "deleted": False,
                    "pinned": False,
                    "allowComment": True,
                    "priority": 0,
                },
            },
            "content": {
                "raw": markdown,
                "content": render_markdown(markdown),
                "rawType": "Markdown",
            },
        }
        r = self.s.post(f"{self.base}{CONSOLE_API}/posts", json=payload)
        if r.status_code not in (200, 201):
            return False, f"create failed: {r.status_code} {r.text[:200]}"
        p = self.s.put(f"{self.base}{CONSOLE_API}/posts/{post_name}/publish")
        if p.status_code != 200:
            return False, f"publish failed: {p.status_code} {p.text[:200]}"
        return True, post_name


def main():
    if APPLY and not HALO_PAT:
        print("错误：--apply 需要环境变量 HALO_PAT（Halo 后台生成的个人访问令牌，pat_ 开头）")
        sys.exit(1)
    if not HALO_PAT:
        print("提示：未提供 HALO_PAT，仅支持试运行（读取公开接口）。--apply 时必须提供。")

    print(f"Halo 地址：{HALO_URL}")
    print(f"模式：{'正式执行 (--apply)' if APPLY else '试运行（加 --apply 真正写入）'}")

    admin = HaloAdmin(HALO_URL, HALO_PAT)
    existing = admin.list_categories()
    print(f"现有分类 {len(existing)} 个")

    def ensure_section_marks(category: dict):
        """补齐分区标签、可见性、优先级（已存在的分类可能是旧版脚本建的隐藏分类）"""
        meta = category.setdefault("metadata", {})
        labels = meta.setdefault("labels", {})
        spec = category.setdefault("spec", {})
        changed = False
        if labels.get(SECTION_LABEL_KEY) != SECTION_LABEL_VAL:
            labels[SECTION_LABEL_KEY] = SECTION_LABEL_VAL
            changed = True
        if spec.get("hideFromList"):
            spec["hideFromList"] = False
            changed = True
        return changed

    # 1. 创建 24 个专题子分类
    for index, (slug, display) in enumerate(TOPICS):
        if slug in existing:
            topic = existing[slug]
            need_update = ensure_section_marks(topic)
            if topic.get("spec", {}).get("priority") != index:
                topic["spec"]["priority"] = index
                need_update = True
            if not need_update:
                print(f"  [跳过] 专题已存在：{display}（{slug}）")
                continue
            if not APPLY:
                print(f"  [试运行] 将校正专题标签/排序：{display}（{slug}）")
                continue
            # 回写必须用扩展接口的原始对象（公开 VO 含 postCount 等多余字段）
            raw = admin.get_category_raw(slug)
            ensure_section_marks(raw)
            raw["spec"]["priority"] = index
            r = admin.update_category(slug, raw)
            print(f"  [{'更新' if r.status_code == 200 else '失败'}] 专题校正：{display}（{slug}）{r.status_code}")
            continue
        if not APPLY:
            print(f"  [试运行] 将创建专题：{display}（{slug}）")
            continue
        r = admin.create_category(slug, display, priority=index)
        if r.status_code in (200, 201):
            print(f"  [创建] 专题：{display}（{slug}）")
        else:
            print(f"  [失败] 专题 {slug}：{r.status_code} {r.text[:200]}")

    # 2. 创建/更新父分类并挂上全部子分类
    child_names = [slug for slug, _ in TOPICS]
    if ROOT_SLUG in existing:
        if APPLY:
            # 回写必须用扩展接口的原始对象（公开列表返回的 VO 不能直接 PUT）
            root = admin.get_category_raw(ROOT_SLUG)
        else:
            root = existing[ROOT_SLUG]
        old_children = root.get("spec", {}).get("children", [])
        merged = list(dict.fromkeys([*old_children, *child_names]))
        root["spec"]["children"] = merged
        ensure_section_marks(root)
        if not APPLY:
            print(f"  [试运行] 将更新父分类，挂载 {len(merged)} 个子专题：{ROOT_NAME}")
        else:
            # 子分类刚批量创建后，CategoryReconciler 可能仍在级联刷新，
            # 立刻更新父分类会撞瞬时 5xx，重试几次即可
            import time
            r = None
            for attempt in range(1, 4):
                r = admin.update_category(ROOT_SLUG, root)
                if r.status_code == 200:
                    break
                time.sleep(1)
            print(f"  [更新] 父分类：{ROOT_NAME}（{r.status_code}），挂载 {len(merged)} 个子专题"
                  + ("" if r.status_code == 200 else f" {r.text[:200]}"))
    elif APPLY:
        r = admin.create_category(ROOT_SLUG, ROOT_NAME, child_names)
        if r.status_code in (200, 201):
            print(f"  [创建] 父分类：{ROOT_NAME}（{ROOT_SLUG}），含 {len(child_names)} 个专题")
        else:
            print(f"  [失败] 父分类：{r.status_code} {r.text[:200]}")
    else:
        print(f"  [试运行] 将创建父分类：{ROOT_NAME}（{ROOT_SLUG}）")

    # 3. 创建示例题
    if not APPLY:
        print(f"  [试运行] 将在「Java 基础面试题」下创建 {len(SAMPLE_POSTS)} 道示例题")
        print("试运行结束。确认无误后加 --apply 执行。")
        return

    name_by_slug = admin.map_post_names_by_slug()
    for item in SAMPLE_POSTS:
        existing_name = name_by_slug.get(item["slug"])
        if existing_name:
            # 已存在：检查正文是否已是渲染 HTML（旧版脚本误存成了原始 Markdown，需要修复）
            detail = admin.s.get(
                f"{admin.base}{CONTENT_API}/posts/{existing_name}"
            ).json()
            head = (detail.get("content", {}).get("content") or "").lstrip()[:80]
            if head.startswith("<"):
                print(f"  [跳过] 示例题已是 HTML 正文：{item['title']}")
                continue
            ok, msg = admin.update_content_and_publish(existing_name, item["content"])
            print(f"  [{'修复' if ok else '失败'}] 重渲染正文：{item['title']}"
                  + ("" if ok else f"：{msg}"))
            continue
        ok, msg = admin.create_and_publish_post(
            item["title"], item["slug"], item["content"], "java-basic",
        )
        print(f"  [{'发布' if ok else '失败'}] {item['title']}" + ("" if ok else f"：{msg}"))

    print("完成。打开前台 /java-interview 查看效果。")


if __name__ == "__main__":
    main()
