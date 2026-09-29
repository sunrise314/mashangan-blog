# -*- coding: utf-8 -*-
"""批量替换 Halo 文章中的字词（正文 raw/content、标题、摘要）。

走 Console API：每次更新会生成新的内容快照（后台可回滚）并自动同步搜索索引，
严禁直接 UPDATE extensions 表（会导致快照、索引、内存缓存不一致）。

用法：
  1) 在下方 REPLACEMENTS 中填写「原词 -> 新词」（支持多组，按顺序执行）
  2) 预演（默认，只统计不改动）：
       python batch_replace_text.py
  3) 正式执行（需要个人访问令牌，pat_ 开头，Halo 后台 -> 个人资料 -> PAT 生成）：
       PowerShell:  $env:HALO_PAT="pat_xxx"; python batch_replace_text.py --apply
       Linux/macOS: HALO_PAT=pat_xxx python batch_replace_text.py --apply

可选：
  --only <postName>   只处理某一篇（先拿一篇验证）
  --skip-code         替换正文时跳过 Markdown 代码块 / HTML <pre><code> 片段
  --fields            替换范围，默认 "content,title,excerpt"
"""
import argparse
import json
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path

import requests

# ---------------------------------------------------------------------------
# 替换规则：按顺序执行，前面的结果会进入后面的替换。请按需修改。
# ---------------------------------------------------------------------------
REPLACEMENTS: list[tuple[str, str]] = [
    # ("旧词", "新词"),
    # ("犬小哈", "示例替换"),
]

HALO_URL = os.environ.get("HALO_URL", "http://49.235.136.65:8090").rstrip("/")
HALO_PAT = os.environ.get("HALO_PAT", "").strip()

CONSOLE_API = "/apis/api.console.halo.run/v1alpha1"
EXTENSION_API = "/apis/content.halo.run/v1alpha1"

BACKUP_FILE = Path(__file__).with_name(
    f"batch_replace_backup_{datetime.now():%Y%m%d-%H%M%S}.jsonl"
)

FENCE_RE = re.compile(r"(```.*?\n.*?```|~~~.*?\n.*?~~~)", re.DOTALL)
CODE_TAG_RE = re.compile(r"(<(?:pre|code)\b[^>]*>.*?</(?:pre|code)>)", re.DOTALL | re.IGNORECASE)


def log(msg: str) -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def replace_literal(text: str, mode: str = "all") -> tuple[str, int]:
    """对文本执行全部替换，返回 (新文本, 命中次数)。

    mode: all=全文替换；md=跳过 Markdown 代码围栏；html=跳过 <pre>/<code> 标签。
    """
    if not text:
        return text, 0
    total_hits = 0

    def _do(seg: str) -> str:
        nonlocal total_hits
        for old, new in REPLACEMENTS:
            count = seg.count(old)
            if count:
                total_hits += count
                seg = seg.replace(old, new)
        return seg

    if mode == "all":
        return _do(text), total_hits

    # 跳过代码段：split 带捕获组时，奇数位为代码片段本身，原样保留
    pattern = FENCE_RE if mode == "md" else CODE_TAG_RE
    parts = pattern.split(text)
    rebuilt = [part if i % 2 == 1 else _do(part) for i, part in enumerate(parts)]
    return "".join(rebuilt), total_hits


class HaloClient:
    def __init__(self, base_url: str, pat: str):
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Authorization": f"Bearer {pat}",
                "Accept": "application/json",
                "User-Agent": "halo-batch-replace/1.0",
            }
        )
        self.base_url = base_url

    def _request(self, method: str, path: str, **kwargs):
        resp = self.session.request(method, f"{self.base_url}{path}", timeout=60, **kwargs)
        if resp.status_code >= 400:
            raise RuntimeError(f"{method} {path} -> {resp.status_code}: {resp.text[:500]}")
        return resp

    def list_post_names(self) -> list[dict]:
        """分页列出全部文章（含草稿，不含回收站），返回 [{name, published, title}]。"""
        posts, page, size = [], 1, 100
        while True:
            data = self._request(
                "GET", f"{CONSOLE_API}/posts", params={"page": page, "size": size}
            ).json()
            items = data.get("items", [])
            for item in items:
                post = item.get("post", item)  # 兼容不同版本的列表包装
                spec = post.get("spec", {})
                if spec.get("deleted"):
                    continue
                posts.append(
                    {
                        "name": post["metadata"]["name"],
                        "published": bool(spec.get("releaseSnapshot")),
                        "title": spec.get("title", ""),
                    }
                )
            if len(items) < size or len(posts) >= data.get("total", 0):
                break
            page += 1
        return posts

    def get_post(self, name: str) -> dict:
        return self._request("GET", f"{EXTENSION_API}/posts/{name}").json()

    def get_head_content(self, name: str) -> dict:
        return self._request("GET", f"{CONSOLE_API}/posts/{name}/head-content").json()

    def update_and_publish(self, post: dict, raw: str, html: str, raw_type: str) -> None:
        name = post["metadata"]["name"]
        payload = {
            "post": post,
            "content": {"version": None, "raw": raw, "content": html, "rawType": raw_type},
        }
        self._request("PUT", f"{CONSOLE_API}/posts/{name}", json=payload)
        # 已发布文章更新后会产生新的 head 快照（待发布草稿），需再发布才对前台生效；
        # 原本是草稿的文章不要替用户发布。
        if post.get("spec", {}).get("releaseSnapshot"):
            self._request("PUT", f"{CONSOLE_API}/posts/{name}/publish")


def main() -> int:
    parser = argparse.ArgumentParser(description="批量替换 Halo 文章字词")
    parser.add_argument("--apply", action="store_true", help="真正写入（默认仅预演）")
    parser.add_argument("--only", help="只处理指定 metadata.name 的文章")
    parser.add_argument("--skip-code", action="store_true", help="跳过代码块中的替换")
    parser.add_argument(
        "--fields", default="content,title,excerpt", help="替换范围：content,title,excerpt"
    )
    args = parser.parse_args()

    if not REPLACEMENTS:
        log("错误：请先在脚本顶部 REPLACEMENTS 中填写替换规则")
        return 2
    # 读取 head 快照原文（Markdown）也需要 Console 权限，预演同样需要 PAT
    if not HALO_PAT:
        log("错误：需要环境变量 HALO_PAT（pat_ 开头的个人访问令牌，预演和执行都需要）")
        return 2

    fields = {f.strip() for f in args.fields.split(",") if f.strip()}
    mode = "正式执行" if args.apply else "预演（不写入）"
    log(f"模式：{mode} | 规则 {len(REPLACEMENTS)} 组 | 范围：{sorted(fields)}")
    for old, new in REPLACEMENTS:
        log(f"  「{old}」 -> 「{new}」")

    client = HaloClient(HALO_URL, HALO_PAT)
    posts = client.list_post_names()
    if args.only:
        posts = [p for p in posts if p["name"] == args.only]
    log(f"待扫描文章：{len(posts)} 篇")

    changed, total_hits = 0, 0
    for idx, meta in enumerate(posts, 1):
        name = meta["name"]
        try:
            post = client.get_post(name)
            head = client.get_head_content(name)
        except RuntimeError as exc:
            log(f"[{idx}/{len(posts)}] 跳过 {name}（读取失败）：{exc}")
            continue

        spec = post.setdefault("spec", {})
        hits = 0
        new_raw = new_html = None

        if "content" in fields:
            new_raw, c1 = replace_literal(
                head.get("raw", ""), "md" if args.skip_code else "all"
            )
            new_html, c2 = replace_literal(
                head.get("content", ""), "html" if args.skip_code else "all"
            )
            hits += c1 + c2

        new_title = title_hits = None
        if "title" in fields:
            new_title, title_hits = replace_literal(spec.get("title", ""), "all")
            hits += title_hits

        excerpt_hits = 0
        if "excerpt" in fields and isinstance(spec.get("excerpt"), dict):
            raw_excerpt = spec["excerpt"].get("raw") or ""
            new_excerpt, excerpt_hits = replace_literal(raw_excerpt, "all")
            hits += excerpt_hits
            if excerpt_hits:
                spec["excerpt"]["raw"] = new_excerpt

        if hits == 0:
            continue

        total_hits += hits
        changed += 1
        status = "已发布" if meta["published"] else "草稿"
        log(
            f"[{idx}/{len(posts)}] {meta['title'][:30]} ({name}, {status}) "
            f"命中 {hits} 处"
        )

        if not args.apply:
            continue

        # 写入前留一份本地兜底备份（Halo 自身也有快照历史，可在后台回滚）
        with BACKUP_FILE.open("a", encoding="utf-8") as fp:
            fp.write(
                json.dumps(
                    {
                        "name": name,
                        "title": spec.get("title"),
                        "raw": head.get("raw"),
                        "content": head.get("content"),
                        "rawType": head.get("rawType"),
                    },
                    ensure_ascii=False,
                )
                + "\n"
            )

        if "content" in fields:
            head["raw"], head["content"] = new_raw, new_html
        if title_hits:
            spec["title"] = new_title
        try:
            client.update_and_publish(
                post,
                head.get("raw", ""),
                head.get("content", ""),
                head.get("rawType", "HTML"),
            )
        except RuntimeError as exc:
            log(f"  !! 更新失败，已中止：{exc}")
            return 1

    log(
        f"完成：{changed} 篇文章需改动，共 {total_hits} 处命中。"
        + (f"本地备份：{BACKUP_FILE}" if args.apply and changed else "（预演未写入）")
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
