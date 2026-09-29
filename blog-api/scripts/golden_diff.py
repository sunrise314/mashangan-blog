# -*- coding: utf-8 -*-
"""阶段3 验收：blog-api(本机) vs 线上 Halo 同端点逐字段 golden diff。
策略：我方输出的每个字段（ours-subset）必须与 Halo 完全一致；
Halo 有而我们没有的字段是有意省略，不判失败。"""
import json
import re
import sys
import urllib.request
import urllib.error

HALO = "http://49.235.136.65:8090/apis"
LOCAL = "http://127.0.0.1:8090/apis"

TIME_KEYS = {"creationTimestamp", "publishTime", "lastModifyTime"}
failures = []
warnings = []
section_fail = {}
REPORT = []

# 已知且可接受的差异：mybatis 分类有 1 篇公开接口不可见的草稿，
# Halo status.postCount=5（含草稿）/visiblePostCount=4；公开数据无法迁移该草稿。
# 前端徽章使用 visiblePostCount（两边均为 4）。
def is_known(msg):
    return "mybatis" in msg and "status.postCount: 4 != 5" in msg


def emit(line):
    REPORT.append(line)
    print(line)


def http(base, path, method="GET", body=None):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def ts_ms(v):
    """ISO-8601 -> epoch ms（Halo 纳秒 / PG 微秒，容差 1ms）"""
    m = re.match(r"(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})$", v)
    if not m:
        return None
    import datetime
    date, hh, mm, ss, frac, tz = m.groups()
    micro = int((frac or "0")[:6].ljust(6, "0"))
    y, mo, d = map(int, date.split("-"))
    dt = datetime.datetime(y, mo, d, int(hh), int(mm), int(ss), micro,
                           tzinfo=datetime.timezone.utc)
    if tz != "Z":
        sign = 1 if tz[0] == "+" else -1
        off = int(tz[1:3]) * 60 + int(tz[-2:])
        dt -= datetime.timedelta(minutes=sign * off)
    return dt.timestamp() * 1000 + (int((frac or "0")[6:9] or 0) / 1_000_000 if frac else 0)


def cmp_val(path, key, ours, theirs):
    if key in TIME_KEYS and isinstance(ours, str) and isinstance(theirs, str):
        a, b = ts_ms(ours), ts_ms(theirs)
        if a is not None and b is not None and abs(a - b) <= 1.0:
            return
        failures.append(f"{path}.{key}: {ours!r} != {theirs!r}")
        return
    if ours != theirs:
        so, st = json.dumps(ours, ensure_ascii=False), json.dumps(theirs, ensure_ascii=False)
        msg = f"{path}.{key}: {so[:120]} != {st[:120]}"
        (warnings if is_known(msg) else failures).append(msg)


def cmp_obj(path, ours, theirs):
    """ours 中出现的每个路径必须在 theirs 中存在且相等"""
    if isinstance(ours, dict):
        if not isinstance(theirs, dict):
            failures.append(f"{path}: ours=object theirs={type(theirs).__name__}")
            return
        for k, v in ours.items():
            if k not in theirs:
                failures.append(f"{path}.{k}: ours={json.dumps(v, ensure_ascii=False)[:80]!r} theirs=<缺失>")
                continue
            cmp_obj(f"{path}.{k}", v, theirs[k])
    elif isinstance(ours, list):
        if not isinstance(theirs, list):
            failures.append(f"{path}: ours=list theirs={type(theirs).__name__}")
            return
        if len(ours) != len(theirs):
            failures.append(f"{path}: 列表长度 {len(ours)} != {len(theirs)}")
            return
        for i, (a, b) in enumerate(zip(ours, theirs)):
            cmp_obj(f"{path}[{i}]", a, b)
    else:
        key = path.rsplit(".", 1)[-1].split("[")[0]
        cmp_val(path.rsplit(".", 1)[0], key, ours, theirs)


def section(name):
    failures.clear()


def finish(name, ok_note=""):
    n = len(failures)
    w = len(warnings)
    section_fail[name] = n
    if n == 0:
        extra = f"（{w} 条已知差异已接受）" if w else ""
        emit(f"[PASS] {name} {ok_note}{extra}")
        if w:
            for x in sorted(set(warnings))[:3]:
                emit("   已知: " + x)
        warnings.clear()
    else:
        emit(f"[FAIL] {name} —— {n} 处差异（前 20）：")
        for f in failures[:20]:
            emit("   - " + f)
    return n


# ---------- A. 分类列表 ----------
section("categories")
hc = http(HALO, "/api.content.halo.run/v1alpha1/categories?size=200")
lc = http(LOCAL, "/api.content.halo.run/v1alpha1/categories?size=200")
for k in ("page", "size", "total", "totalPages", "hasNext"):
    cmp_obj(f"env.{k}", hc[k], lc[k]) if False else None
    if hc.get(k) != lc.get(k):
        failures.append(f"envelope.{k}: {hc.get(k)} != {lc.get(k)}")
hn = [i["metadata"]["name"] for i in hc["items"]]
ln = [i["metadata"]["name"] for i in lc["items"]]
if hn != ln:
    failures.append(f"分类顺序/集合不一致\nhalo={hn}\nours={ln}")
else:
    hmap = {i["metadata"]["name"]: i for i in hc["items"]}
    for item in lc["items"]:
        cmp_obj(f"category[{item['metadata']['name']}]", item, hmap[item["metadata"]["name"]])
finish("A 分类列表", f"({len(ln)} 个，顺序一致)")

# ---------- B. 全站文章列表 ----------
section("posts-list")
hposts, lposts = [], []
hfirst = http(HALO, "/api.content.halo.run/v1alpha1/posts?size=100&page=1")
lfirst = http(LOCAL, "/api.content.halo.run/v1alpha1/posts?size=100&page=1")
for k in ("page", "size", "total", "totalPages", "hasNext"):
    if hfirst.get(k) != lfirst.get(k):
        failures.append(f"envelope.{k}: {hfirst.get(k)} != {lfirst.get(k)}")
hposts.extend(hfirst["items"])
lposts.extend(lfirst["items"])
for p in range(2, hfirst["totalPages"] + 1):
    hposts.extend(http(HALO, f"/api.content.halo.run/v1alpha1/posts?size=100&page={p}")["items"])
    lposts.extend(http(LOCAL, f"/api.content.halo.run/v1alpha1/posts?size=100&page={p}")["items"])
hpn = [p["metadata"]["name"] for p in hposts]
lpn = [p["metadata"]["name"] for p in lposts]
if hpn != lpn:
    diff = [(i, a, b) for i, (a, b) in enumerate(zip(hpn, lpn)) if a != b]
    failures.append(f"文章顺序不一致，首个分歧 {diff[:3]}")
else:
    hpmap = {p["metadata"]["name"]: p for p in hposts}
    for p in lposts:
        cmp_obj(f"post[{p['metadata']['name']}]", p, hpmap[p["metadata"]["name"]])
finish("B 全站文章列表", f"(total={lfirst['total']}, 3 页合并顺序一致)")

# ---------- C. 每个分类下的文章（顺序即排序规则） ----------
section("category-posts")
for cat in lc["items"]:
    name = cat["metadata"]["name"]
    seq_h, seq_l = [], []
    p = 1
    while True:
        e = http(HALO, f"/api.content.halo.run/v1alpha1/categories/{name}/posts?size=100&page={p}")
        seq_h.extend(i["metadata"]["name"] for i in e["items"])
        if p >= e["totalPages"]:
            break
        p += 1
    p = 1
    while True:
        e = http(LOCAL, f"/api.content.halo.run/v1alpha1/categories/{name}/posts?size=100&page={p}")
        seq_l.extend(i["metadata"]["name"] for i in e["items"])
        if p >= e["totalPages"]:
            break
        p += 1
    if seq_h != seq_l:
        failures.append(f"分类 {name}({cat['spec']['slug']}): halo {len(seq_h)} 篇 vs ours {len(seq_l)} 篇; "
                        f"首个分歧: {next((x for x in zip(seq_h, seq_l) if x[0] != x[1]), None)}")
finish("C 分类页文章排序（33 个分类，含题库升序/全站降序）")

# ---------- D. 全部文章详情 ----------
section("post-detail")
for idx, name in enumerate(hpn):
    hd = http(HALO, f"/api.content.halo.run/v1alpha1/posts/{name}")
    ld = http(LOCAL, f"/api.content.halo.run/v1alpha1/posts/{name}")
    before = len(failures)
    cmp_obj(f"detail[{name}]", ld, hd)
    if len(failures) > before + 8:
        failures[:] = failures[:before + 8]  # 单篇最多留 8 条
finish("D 全部文章详情（287 篇，含正文 raw/content 精确比对）")

# ---------- E. 独立页面 ----------
section("singlepages")
hsp = http(HALO, "/api.content.halo.run/v1alpha1/singlepages?size=100")
lsp = http(LOCAL, "/api.content.halo.run/v1alpha1/singlepages?size=100")
snh = [i["metadata"]["name"] for i in hsp["items"]]
snl = [i["metadata"]["name"] for i in lsp["items"]]
if snh != snl:
    failures.append(f"页面集合/顺序: {snh} != {snl}")
else:
    hmap = {i["metadata"]["name"]: i for i in hsp["items"]}
    for i in lsp["items"]:
        cmp_obj(f"sp[{i['metadata']['name']}]", i, hmap[i["metadata"]["name"]])
        hd = http(HALO, f"/api.content.halo.run/v1alpha1/singlepages/{i['metadata']['name']}")
        ld = http(LOCAL, f"/api.content.halo.run/v1alpha1/singlepages/{i['metadata']['name']}")
        cmp_obj(f"spdetail[{i['metadata']['name']}]", ld, hd)
finish("E 独立页面列表+详情", f"({len(snl)} 个)")

# ---------- F. 菜单 ----------
section("menu")
hm = http(HALO, "/api.halo.run/v1alpha1/menus/-")
lm = http(LOCAL, "/api.halo.run/v1alpha1/menus/-")
cmp_obj("menu", lm, hm)
finish("F 主菜单（7 项含 targetRef 解析）")

# ---------- G. 搜索（信息级：Halo 拼音分词 vs 我方 ILIKE，集合允许不同） ----------
emit("")
emit("---- G 搜索（信息级，不计失败）----")
for kw in ["线程", "Redis", "设计模式", "MySQL", "Agent", "并发"]:
    hr = http(HALO, "/api.halo.run/v1alpha1/indices/-/search", "POST",
              {"keyword": kw, "limit": 20})
    lr = http(LOCAL, "/api.halo.run/v1alpha1/indices/-/search", "POST",
              {"keyword": kw, "limit": 20})
    hs = {h.get("metadata", {}).get("name") or h.get("name") for h in hr.get("hits", [])}
    ls = {h.get("metadata", {}).get("name") or h.get("name") for h in lr.get("hits", [])}
    only_h, only_l = hs - ls, ls - hs
    mark = "OK " if not only_h and not only_l else "DIFF"
    emit(f"  [{mark}] {kw}: halo {len(hs)} / ours {len(ls)}; "
         f"仅halo={sorted(only_h)[:5]} 仅ours={sorted(only_l)[:5]}")

# ---------- 汇总 ----------
emit("")
total = sum(section_fail.values())
for k, v in section_fail.items():
    emit(f"  {k}: {v} 处差异")
emit(f"总计 {total} 处差异")
with open(r"C:\Users\Administrator\AppData\Local\Temp\_golden_diff_out.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(REPORT))
sys.exit(1 if total else 0)
