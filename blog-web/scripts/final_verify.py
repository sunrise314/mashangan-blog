# -*- coding: utf-8 -*-
"""全量核验：所有文章中的图片唯一 URL，检查 HTTP 状态与类型"""
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import requests
sys.path.insert(0, str(Path(__file__).resolve().parent))
import migrate_images as mi

posts = mi.list_all_posts()
urls = set()
for p in posts:
    d = mi.get_public_post_detail(p["metadata"]["name"])
    c = d.get("content") or {}
    for u in mi.extract_html_img_urls(c.get("content") or ""):
        urls.add(u)
    cov = (d.get("spec") or {}).get("cover")
    if cov:
        urls.add(cov)

internal = sorted(u for u in urls if "/upload/" in u)
external = sorted(u for u in urls if "quanxiaoha" in u)
other = sorted(u for u in urls if u not in set(internal) and u not in set(external))
print(f"唯一图片：站内 {len(internal)}，犬小哈外链 {len(external)}，其他 {len(other)}")

sess = requests.Session()
sess.headers["Referer"] = ""
def check(u):
    try:
        r = sess.get(u, timeout=30)
        return u, r.status_code, r.headers.get("Content-Type", "")[:20]
    except Exception as e:  # noqa: BLE001
        return u, "ERR", str(e)[:60]

bad = []
with ThreadPoolExecutor(8) as ex:
    futs = [ex.submit(check, u) for u in urls]
    for i, f in enumerate(as_completed(futs), 1):
        u, code, ct = f.result()
        if code != 200 or not ct.startswith("image"):
            bad.append((u, code, ct))
print(f"检查 {len(urls)} 个 URL，异常 {len(bad)}")
for b in bad:
    print("  ", b)
