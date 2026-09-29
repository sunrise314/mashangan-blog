#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
版权图片整改（外链热链 + 去水印迁移图）。

范围（严格限定，不碰"无水印同源图"）：
  1. 正文/封面中所有站外热链图片（当前为 img.quanxiaoha.com）；
  2. image_migration_cache.json 中 v2 且 dewm=true 的 96 个去水印附件。

处理方式（用户已确认）：
  - 不删除引用位：每一张旧图替换为一张程序生成的"原创示意图"卡片
    （card_image.py，画面标注"原创示意图（非软件截图）"，不冒充真实界面）；
  - 正文图按 URL 全局去重生成（4:3）；封面按文章逐篇生成（16:9）；
  - 示意图上传 Halo 附件库，站内自托管；
  - 替换发布完成后，去水印旧附件重新全量扫描，确认零引用才删除（进回收站）。

默认 DRY-RUN；正式执行：HALO_PAT=pat_xxx python remediate_images.py --apply
断点续传：image_remediation_cache.json 记录旧 URL -> 新附件 URL。
"""
import argparse
import hashlib
import json
import os
import re
import sys
import time
from pathlib import Path
from urllib.parse import urlparse

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent))
from card_image import render_cover, render_inline  # noqa: E402

HALO_URL = os.environ.get("HALO_URL", "http://49.235.136.65:8090").rstrip("/")
HALO_PUBLIC_URL = os.environ.get("HALO_PUBLIC_URL", HALO_URL).rstrip("/")
HALO_PAT = os.environ.get("HALO_PAT", "").strip()

HERE = Path(__file__).resolve().parent
MIG_CACHE = HERE / "image_migration_cache.json"
CACHE_FILE = HERE / "image_remediation_cache.json"

CONTENT_API = "/apis/api.content.halo.run/v1alpha1"
CONSOLE_API = "/apis/api.console.halo.run/v1alpha1"
EXTENSION_API = "/apis/content.halo.run/v1alpha1"
STORAGE_API = "/apis/storage.halo.run/v1alpha1"
UPLOAD_URLS = [
    "/apis/console.api.storage.halo.run/v1alpha1/attachments/-/upload",
    f"{CONSOLE_API}/attachments/upload",
]
IMG_SRC_RE = re.compile(r"""<img\b[^>]*?\bsrc=(['"])(.*?)\1""", re.IGNORECASE)
CAPTION_RE = re.compile(
    r"""<img\b[^>]*?\bsrc=['"](?P<url>[^'"]+)['"][^>]*?>"""
    r"""(?:\s*<span[^>]*class=['"][^'"]*image-caption[^'"]*['"][^>]*>(?P<cap>.*?)</span>)?""",
    re.IGNORECASE | re.DOTALL,
)
TAG_RE = re.compile(r"<[^>]+>")


def log(msg: str) -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def load_json(path: Path, default):
    if path.exists():
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            log(f"警告：{path.name} 损坏，忽略")
    return default


def save_cache(cache: dict) -> None:
    tmp = CACHE_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(CACHE_FILE)


def strip_tags(s: str) -> str:
    return re.sub(r"\s+", " ", TAG_RE.sub("", s or "")).strip()


def is_external(url: str, own_host: str) -> bool:
    if not url or not url.lower().startswith(("http://", "https://")):
        return False
    host = urlparse(url).netloc.lower()
    return bool(host) and host != own_host


class Halo:
    def __init__(self, pat: str):
        self.s = requests.Session()
        self.s.headers.update({"Authorization": f"Bearer {pat}",
                               "Accept": "application/json",
                               "User-Agent": "halo-image-remediation/1.0"})
        self._upload_url = None

    def req(self, method, path, **kw):
        r = self.s.request(method, f"{HALO_URL}{path}", timeout=120, **kw)
        if r.status_code >= 400:
            raise RuntimeError(f"{method} {path} -> {r.status_code}: {r.text[:400]}")
        return r

    def upload_png(self, key: str, data: bytes) -> str:
        filename = f"{key}.png"
        files = {"file": (filename, data, "image/png")}
        candidates = [self._upload_url] if self._upload_url else UPLOAD_URLS
        last = None
        for path in candidates:
            if not path:
                continue
            try:
                r = self.s.post(f"{HALO_URL}{path}", files=files, timeout=180)
                if r.status_code == 404 and path == UPLOAD_URLS[0]:
                    last = RuntimeError("404 new endpoint")
                    continue
                if r.status_code >= 400:
                    raise RuntimeError(f"{r.status_code}: {r.text[:300]}")
                self._upload_url = path
                pl = (r.json().get("status") or {}).get("permalink") or ""
                if not pl:
                    raise RuntimeError(f"附件返回缺 permalink: {r.text[:300]}")
                return HALO_PUBLIC_URL + pl if pl.startswith("/") else pl
            except Exception as e:  # noqa: BLE001
                last = e
        raise RuntimeError(f"上传失败：{last}")

    def get_post(self, name):
        return self.req("GET", f"{EXTENSION_API}/posts/{name}").json()

    def update_publish(self, post, raw, html, raw_type):
        name = post["metadata"]["name"]
        payload = {"post": post,
                   "content": {"version": None, "raw": raw, "content": html,
                               "rawType": raw_type or "HTML"}}
        self.req("PUT", f"{CONSOLE_API}/posts/{name}", json=payload)
        self.req("PUT", f"{CONSOLE_API}/posts/{name}/publish")

    def list_attachments(self):
        items, page = [], 1
        while True:
            d = self.req("GET", f"{CONSOLE_API}/attachments",
                         params={"page": page, "size": 100}).json()
            items.extend(d.get("items", []))
            if page * 100 >= d.get("total", 0):
                break
            page += 1
        return items

    def delete_attachment(self, name):
        self.s.delete(f"{HALO_URL}{STORAGE_API}/attachments/{name}", timeout=60)


def list_posts():
    posts, page = [], 1
    while True:
        d = requests.get(f"{HALO_URL}{CONTENT_API}/posts",
                         params={"page": page, "size": 100},
                         headers={"Accept": "application/json"}, timeout=60).json()
        posts.extend(d["items"])
        if not d.get("hasNext"):
            break
        page += 1
    return posts


def post_detail(name):
    return requests.get(f"{HALO_URL}{CONTENT_API}/posts/{name}",
                        headers={"Accept": "application/json"}, timeout=60).json()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--post", action="append", default=[], help="只处理指定文章")
    args = ap.parse_args()
    if args.apply and not HALO_PAT:
        log("错误：--apply 需要环境变量 HALO_PAT")
        return 2

    own_host = urlparse(HALO_PUBLIC_URL).netloc.lower()
    mig = load_json(MIG_CACHE, {})
    dewm_urls = {e["newUrl"] for e in mig.values()
                 if isinstance(e, dict) and e.get("dewm") and e.get("newUrl")}
    cache = load_json(CACHE_FILE, {})

    # 分类名映射（示意图标签用）
    cats = requests.get(f"{HALO_URL}{CONTENT_API}/categories?size=200", timeout=60).json()["items"]
    cat_name = {c["metadata"]["name"]: c["spec"].get("displayName") or c["spec"]["slug"]
                for c in cats}

    posts = list_posts()
    if args.post:
        posts = [p for p in posts if p["metadata"]["name"] in set(args.post)]
    log(f"扫描文章 {len(posts)} 篇；去水印目标附件 {len(dewm_urls)} 个")

    # 收集每张目标 URL 的最佳图注
    captions: dict[str, str] = {}
    units = []  # (name, title, category_label, html, raw, raw_type, cover, ext_urls, dewm_here)
    n_ext = n_dewm = n_cover = 0
    for p in posts:
        name = p["metadata"]["name"]
        d = post_detail(name)
        spec = d.get("spec") or {}
        html = (d.get("content") or {}).get("content") or ""
        raw = (d.get("content") or {}).get("raw") or ""
        raw_type = (d.get("content") or {}).get("rawType") or "HTML"
        cover = spec.get("cover") or ""
        label = cat_name.get((spec.get("categories") or [""])[0], "教程")

        found = {}
        for m in CAPTION_RE.finditer(html):
            u, cap = m.group("url"), strip_tags(m.group("cap") or "")
            if is_external(u, own_host) or u in dewm_urls:
                found[u] = cap
                if cap and len(cap) > len(captions.get(u, "")):
                    captions[u] = cap
        ext = sorted(u for u in found if is_external(u, own_host))
        dewm_here = sorted(u for u in found if u in dewm_urls)
        cover_hit = cover if (is_external(cover, own_host) or cover in dewm_urls) else ""
        if ext or dewm_here or cover_hit:
            units.append((name, spec.get("title", name), label, html, raw, raw_type,
                          cover, ext, dewm_here, cover_hit))
            n_ext += len(ext)
            n_dewm += len(dewm_here)
            n_cover += bool(cover_hit)

    uniq_urls = {u for _, _, _, _, _, _, _, ext, dewm_here, _ in units
                 for u in ext + dewm_here}
    log(f"命中：文章 {len(units)} 篇；正文目标 URL 唯一 {len(uniq_urls)} 个"
        f"（外链出现 {n_ext} 处、去水印出现 {n_dewm} 处）；封面 {n_cover} 篇")

    client = Halo(HALO_PAT) if args.apply else None

    def ensure(url, kind, label, text):
        """生成（或取缓存）示意图并上传。key 区分正文/封面。"""
        ckey = f"{kind}:{url}" if kind == "inline" else f"cover:{url}"
        if ckey in cache:
            return cache[ckey]["newUrl"]
        if kind == "cover":
            data = render_cover(text[:40] or "教程文章", label)
            key = "cover-" + hashlib.md5(ckey.encode()).hexdigest()[:12]
        else:
            data = render_inline((captions.get(url) or text or "教程配图")[:40], label)
            key = "illust-" + hashlib.md5(url.encode()).hexdigest()[:12]
        if not args.apply:
            new_url = f"<DRY-RUN {key}.png>"
        else:
            new_url = client.upload_png(key, data)
        cache[ckey] = {"newUrl": new_url, "kind": kind}
        if args.apply:
            save_cache(cache)
        return new_url

    changed = 0
    for (name, title, label, html, raw, raw_type, cover,
         ext, dewm_here, cover_hit) in units:
        mapping = {u: ensure(u, "inline", label, title) for u in ext + dewm_here}
        new_cover = cover
        if cover_hit:
            new_cover = ensure(cover_hit, "cover", label, title)
            # 封面位也可能同时在正文出现，封面替换不影响正文 mapping
        def replace_all(text):
            cnt = 0
            for old, new in mapping.items():
                if old in text:
                    cnt += text.count(old)
                    text = text.replace(old, new)
            return text, cnt
        new_html, c1 = replace_all(html)
        new_raw, c2 = replace_all(raw)
        if cover_hit:
            new_html = new_html.replace(cover_hit, new_cover) if cover_hit in new_html else new_html
            new_raw = new_raw.replace(cover_hit, new_cover) if cover_hit in new_raw else new_raw
        log(f"- {title}：正文替换 {c1 + c2} 处，封面 {'是' if cover_hit else '否'}")
        if not args.apply:
            changed += 1
            continue
        full = client.get_post(name)
        full.setdefault("spec", {})["cover"] = new_cover if cover_hit else cover
        client.update_publish(full, new_raw, new_html, raw_type)
        changed += 1

    log(f"文章更新完成：{changed} 篇")

    # 去水印旧附件：零引用才删除
    if not dewm_urls:
        return 0
    if not args.apply:
        log("DRY-RUN：旧去水印附件删除动作未执行。确认无误后加 --apply。")
        return 0

    log("全量复查去水印旧附件引用情况…")
    atts = client.list_attachments()
    by_path = {urlparse((a.get("status") or {}).get("permalink") or "").path.rstrip("/"): a
               for a in atts}
    # 重新拉最新正文确认零引用
    latest = {}
    for p in list_posts():
        d = post_detail(p["metadata"]["name"])
        c = d.get("content") or {}
        latest[p["metadata"]["name"]] = " ".join(
            [c.get("content") or "", c.get("raw") or "", (d.get("spec") or {}).get("cover") or ""])
    deleted = kept = missing = 0
    for u in sorted(dewm_urls):
        path = urlparse(u).path.rstrip("/")
        att = by_path.get(path)
        if att is None:
            missing += 1
            continue
        refs = [n for n, blob in latest.items() if path in blob]
        if refs:
            kept += 1
            log(f"  保留（仍有引用）：{path} <- {refs}")
            continue
        r = client.s.delete(f"{HALO_URL}{STORAGE_API}/attachments/{att['metadata']['name']}",
                            timeout=60)
        if r.status_code < 300:
            deleted += 1
        else:
            kept += 1
            log(f"  删除失败 {r.status_code}: {path}")
    log(f"旧附件处理：删除 {deleted}，保留 {kept}，库中未找到 {missing}（删除项进 Halo 回收站）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
