#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
清理上一轮（v1，带水印）上传的旧附件。

流程：
  1. 从 image_migration_cache.v1.bak.json 取 138 个旧附件 URL；
  2. 列出 Halo 全部附件，按 permalink 路径匹配旧附件；
  3. 扫描全部文章（正文 HTML/raw + 封面）确认每个旧附件已无引用；
  4. 仅删除"匹配到且零引用"的附件；仍被引用的列出并保留。
默认 DRY-RUN，加 --apply 且提供 HALO_PAT 才真正删除。
"""
import argparse
import json
import os
import sys
from pathlib import Path
from urllib.parse import urlparse

import requests

HALO_URL = os.environ.get("HALO_URL", "http://49.235.136.65:8090").rstrip("/")
HALO_PAT = os.environ.get("HALO_PAT", "").strip()
BAK = Path(__file__).with_name("image_migration_cache.v1.bak.json")
CONSOLE_API = "/apis/api.console.halo.run/v1alpha1"
EXTENSION_API = "/apis/content.halo.run/v1alpha1"
STORAGE_API = "/apis/storage.halo.run/v1alpha1"


def path_of(url: str) -> str:
    p = urlparse(url or "").path
    return p.rstrip("/")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if args.apply and not HALO_PAT:
        print("错误：--apply 需要 HALO_PAT")
        return 2

    old = json.loads(BAK.read_text(encoding="utf-8"))
    old_paths = {path_of(v["newUrl"]): k for k, v in old.items()}
    print(f"v1 旧附件记录：{len(old_paths)}")

    sess = requests.Session()
    if HALO_PAT:
        sess.headers.update({"Authorization": f"Bearer {HALO_PAT}",
                             "Accept": "application/json"})

    # 1) 全部附件
    matches = {}
    page = 1
    total = None
    while True:
        r = sess.get(f"{HALO_URL}{CONSOLE_API}/attachments",
                     params={"page": page, "size": 100}, timeout=60)
        r.raise_for_status()
        data = r.json()
        items = data.get("items", [])
        total = data.get("total", total)
        for it in items:
            pl = path_of((it.get("status") or {}).get("permalink") or "")
            if pl in old_paths:
                matches[pl] = it
        if len(items) < 100 or (total is not None and page * 100 >= total):
            break
        page += 1
    print(f"附件库中匹配到旧附件：{len(matches)} / {len(old_paths)}")
    missing = set(old_paths) - set(matches)
    for p in sorted(missing):
        print("  未在附件库找到：", p)

    # 2) 全部文章引用计数（按路径匹配）
    r = sess.get(f"{HALO_URL}/apis/api.content.halo.run/v1alpha1/posts",
                 params={"size": 1000}, timeout=60)
    r.raise_for_status()
    posts = r.json()["items"]
    refs = {p: [] for p in matches}
    pub = requests.Session()  # 公开内容不需要令牌
    for post in posts:
        name = post["metadata"]["name"]
        d = pub.get(f"{HALO_URL}/apis/api.content.halo.run/v1alpha1/posts/{name}",
                    timeout=60).json()
        content = d.get("content") or {}
        blob = " ".join([
            (content.get("content") or ""),
            (content.get("raw") or ""),
            (d.get("spec") or {}).get("cover") or "",
        ])
        for p in matches:
            if p in blob:
                refs[p].append(name)

    orphans = [p for p, who in refs.items() if not who]
    still = {p: who for p, who in refs.items() if who}
    print(f"零引用可删：{len(orphans)}；仍被引用保留：{len(still)}")
    for p, who in still.items():
        print("  保留（仍被引用）：", p, who)

    if not args.apply:
        print("DRY-RUN：未删除。确认无误后加 --apply 执行。")
        return 0

    # 3) 删除孤儿附件
    ok = fail = 0
    for p in orphans:
        name = matches[p]["metadata"]["name"]
        resp = sess.delete(f"{HALO_URL}{STORAGE_API}/attachments/{name}",
                           timeout=60)
        if resp.status_code < 300:
            ok += 1
        else:
            fail += 1
            print(f"  删除失败 {p} -> {resp.status_code}: {resp.text[:200]}")
    print(f"完成：删除 {ok}，失败 {fail}，保留 {len(still)}（进回收站，可在 Halo 后台彻底清除）")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
