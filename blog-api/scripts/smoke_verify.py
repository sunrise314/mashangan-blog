# -*- coding: utf-8 -*-
import json
import urllib.request

BASE = "http://localhost:8090/apis"
failures = []


def get(path):
    with urllib.request.urlopen(BASE + path) as r:
        return r.status, json.loads(r.read().decode("utf-8"))


def post(path, body):
    req = urllib.request.Request(
        BASE + path, data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req) as r:
        return r.status, json.loads(r.read().decode("utf-8"))


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f" -> {detail}"))
    if not cond:
        failures.append(name)


# 1. 全站列表：发布时间降序
_, posts = get("/api.content.halo.run/v1alpha1/posts")
names = [i["metadata"]["name"] for i in posts["items"]]
check("posts desc order", names == ["p2", "p1"], names)
p1 = next(i for i in posts["items"] if i["metadata"]["name"] == "p1")
check("post permalink", p1["status"]["permalink"] == "/archives/concurrency-basics")
check("post phase", p1["status"]["phase"] == "PUBLISHED")
check("post nested category", p1["categories"][0]["spec"]["slug"] == "jvm")
check("post nested category label",
      p1["categories"][0]["metadata"]["labels"]["haloweb.section"] == "interview")
check("post nested tag", p1["tags"][0]["spec"]["slug"] == "hot")
check("post excerpt structure", p1["spec"]["excerpt"]["raw"] == "线程状态与生命周期"
      and p1["spec"]["excerpt"]["autoGenerate"] is False)
check("page envelope", all(k in posts for k in
      ("page", "size", "total", "items", "first", "last", "hasNext", "hasPrevious", "totalPages")))

# 2. 分类内文章：与全站默认排序一致（置顶优先 + 发布时间降序）
_, catposts = get("/api.content.halo.run/v1alpha1/categories/c-jvm/posts")
catnames = [i["metadata"]["name"] for i in catposts["items"]]
check("category posts set", set(catnames) == {"p1", "p2"}, catnames)

# 3. 父分类仅统计直接归属文章（Halo 公开行为，不级联子分类）
_, parentposts = get("/api.content.halo.run/v1alpha1/categories/c-java-interview/posts")
check("parent direct-only", parentposts["total"] == 0, parentposts["total"])

# 4. 文章详情含正文
_, detail = get("/api.content.halo.run/v1alpha1/posts/p1")
check("detail content html", detail["content"]["content"] == "<h1>线程</h1><p>线程状态与生命周期详解</p>")
check("detail raw falls back to html", detail["content"]["raw"] == detail["content"]["content"])

# 5. 分类列表：children、postCount、计数
_, cats = get("/api.content.halo.run/v1alpha1/categories?size=200")
catbyname = {c["metadata"]["name"]: c for c in cats["items"]}
check("categories count", cats["total"] == 2, cats["total"])
parent = catbyname["c-java-interview"]
check("category children", parent["spec"]["children"] == ["c-jvm"], parent["spec"])
check("category permalink", parent["status"]["permalink"] == "/categories/java-interview")
jvm = catbyname["c-jvm"]
check("category postCount", jvm["postCount"] == 2 and jvm["status"]["visiblePostCount"] == 2)

# 6. 独立页面
_, pages = get("/api.content.halo.run/v1alpha1/singlepages")
check("singlepage list omits content", "content" not in pages["items"][0], pages["items"][0].keys())
_, about = get("/api.content.halo.run/v1alpha1/singlepages/sp-about")
check("singlepage detail content", about["content"]["content"] == "<p>关于本站</p>")
check("singlepage permalink", about["status"]["permalink"] == "/about")

# 7. 菜单树与 ref 解析
_, menu = get("/api.halo.run/v1alpha1/menus/-")
items = menu["menuItems"]
hrefs = [i["spec"]["href"] for i in items]
check("menu refs resolved",
      hrefs == ["/categories/jvm", "/archives/concurrency-basics", "/about"], hrefs)
check("menu root displayName", all(i.get("displayName") == i["spec"]["displayName"] for i in items))
check("menu status mirror", all(i["status"]["href"] == i["spec"]["href"] for i in items))
check("menu children array", all(isinstance(i["children"], list) for i in items))
check("menu names flat", menu["spec"]["menuItems"][0] == "mi-cat")

# 8. 搜索：标题高亮 / 正文命中摘要 / 空结果 / 分类标签 slug
_, s1 = post("/api.halo.run/v1alpha1/indices/-/search", {"keyword": "线程", "limit": 10})
check("search title highlight",
      s1["hits"][0]["title"] == "Java <B>线程</B>基础详解", s1["hits"][0]["title"])
check("search hit tag slug", s1["hits"][0]["tags"] == ["hot"], s1["hits"][0]["tags"])
check("search hit category slug", s1["hits"][0]["categories"] == ["jvm"])
check("search total", s1["total"] == 1)

_, s2 = post("/api.halo.run/v1alpha1/indices/-/search", {"keyword": "内存模型", "limit": 10})
check("search body hit", s2["total"] == 1 and s2["hits"][0]["metadataName"] == "p2")
desc = s2["hits"][0]["description"]
check("search snippet highlight", "<B>内存模型</B>" in desc, desc)

_, s3 = post("/api.halo.run/v1alpha1/indices/-/search", {"keyword": "不存在的词xyz", "limit": 10})
check("search empty", s3["hits"] == [] and s3["total"] == 0)

# 9. 大小写不敏感
_, s4 = post("/api.halo.run/v1alpha1/indices/-/search", {"keyword": "volatile", "limit": 10})
check("search case-insensitive", s4["total"] == 1)

print()
if failures:
    print(f"{len(failures)} FAILURES: {failures}")
    raise SystemExit(1)
print("ALL CHECKS PASSED")
