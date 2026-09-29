#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将文章中热链在外部图床（如 img.quanxiaoha.com）的图片迁移到 Halo 附件库。

工作流程：
  1. 拉取全部文章，收集每篇文章的封面（spec.cover）、正文 HTML、raw 中的外部图片 URL；
  2. 按 URL 全局去重后下载图片（不带 Referer 绕过图床防盗链），依据 Content-Type 补扩展名；
  3. 逐张做水印处理（halo_dw2）：
       a. 能在浅底安全去除的「犬小哈教程」水印 → 去水印后上传；
       b. 检测到仍有肉眼可见水印（压在彩色块/终端/按钮栏上无法安全去除）→ 不上传，
          文章中保留原外链，跳过地址写入 image_migration_skipped.json；
       c. 无可见水印 → 原图上传；
       动图（gif）/矢量图（svg）不做识别与处理，原样上传；
  4. 通过 Halo Console API 上传为附件，拿到 /upload/... 的站内永久链接；
  5. 替换封面与正文（HTML/raw 同步）中的图片地址，并更新文章、重新发布；
  6. URL 映射缓存到脚本同目录的 image_migration_cache.json，中断后重跑可续传、不重复上传。

安全策略：
  - 默认是 DRY-RUN：只收集、下载、打印将要执行的替换，不上传、不改文章；
  - 真正执行需要显式加 --apply，并提供 Halo 个人访问令牌（环境变量 HALO_PAT，pat_ 开头）；
  - 单张图片下载/上传失败只跳过该地址并记录，绝不半篇更新，文章要么整体成功要么不动；
  - 缓存条目带 "v": 2 标记；旧版（v1，带水印）记录会被自动忽略并以去水印版本重新上传。

用法示例：
  # 1) 预演（不需要令牌，只统计和验证图片可下载）
  python migrate_images.py

  # 2) 只预演前 2 篇
  python migrate_images.py --limit 2

  # 3) 正式执行（先在 Halo 后台 头像 -> 个人资料 -> 个人令牌 生成令牌）
  HALO_PAT=pat_xxxx python migrate_images.py --apply

  # 4) 只处理指定文章
  HALO_PAT=pat_xxxx python migrate_images.py --apply --post mybatis-plus-xxx

可通过环境变量覆盖默认地址：
  HALO_URL         Halo 服务地址（脚本调用 API 用），默认 http://49.235.136.65:8090
  HALO_PUBLIC_URL  附件对外访问的前缀（写入文章的地址），默认与 HALO_URL 相同
  HALO_PAT         个人访问令牌（--apply 时必需）
"""

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path
from urllib.parse import urlparse

import cv2
import numpy as np
import requests

sys.path.insert(0, str(Path(__file__).resolve().parent))
from halo_dw2 import process, remaining_groups  # noqa: E402

CACHE_VERSION = 2
# 不做水印识别/处理的类型（动图、矢量、图标）
RAW_PASS_EXT = {".gif", ".svg", ".ico", ".webp"}

HALO_URL = os.environ.get("HALO_URL", "http://49.235.136.65:8090").rstrip("/")
HALO_PUBLIC_URL = os.environ.get("HALO_PUBLIC_URL", HALO_URL).rstrip("/")
HALO_PAT = os.environ.get("HALO_PAT", "").strip()

CACHE_FILE = Path(__file__).with_name("image_migration_cache.json")

CONTENT_API = "/apis/api.content.halo.run/v1alpha1"
CONSOLE_API = "/apis/api.console.halo.run/v1alpha1"
EXTENSION_API = "/apis/content.halo.run/v1alpha1"
# 2.26 的新附件端点（policyName 由系统设置自动提供）；失败时回退旧端点
UPLOAD_URLS = [
    "/apis/console.api.storage.halo.run/v1alpha1/attachments/-/upload",
    f"{CONSOLE_API}/attachments/upload",
]

# 图床无扩展名，需要按 Content-Type 决定上传文件名后缀
EXT_BY_CONTENT_TYPE = {
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
    "image/gif": ".gif",
    "image/webp": ".webp",
    "image/svg+xml": ".svg",
    "image/bmp": ".bmp",
    "image/x-icon": ".ico",
    "image/vnd.microsoft.icon": ".ico",
}

IMG_SRC_RE = re.compile(r"(<img\b[^>]*?\bsrc=)([\"'])(.*?)(\2)", re.IGNORECASE)

download_session = requests.Session()
download_session.headers.update(
    {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
        ),
        # 显式不带 Referer：犬小哈图床对带外站 Referer 的请求返回 403
        "Referer": "",
    }
)


def log(msg: str) -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def load_cache() -> dict:
    if CACHE_FILE.exists():
        try:
            return json.loads(CACHE_FILE.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            log(f"警告：缓存文件损坏，将忽略：{CACHE_FILE}")
    return {}


def save_cache(cache: dict) -> None:
    tmp = CACHE_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(CACHE_FILE)


def is_external_image(url: str) -> bool:
    """判断是否为需要迁移的外部图片地址（http/https 且主机不是 Halo 自身）。"""
    if not url:
        return False
    if not url.lower().startswith(("http://", "https://")):
        return False
    host = urlparse(url).netloc.lower()
    own_host = urlparse(HALO_PUBLIC_URL).netloc.lower()
    return bool(host) and host != own_host


def extract_html_img_urls(html: str) -> list:
    if not html:
        return []
    return [m.group(3) for m in IMG_SRC_RE.finditer(html)]


def guess_ext(content_type: str, url: str) -> str:
    ct = (content_type or "").split(";", 1)[0].strip().lower()
    if ct in EXT_BY_CONTENT_TYPE:
        return EXT_BY_CONTENT_TYPE[ct]
    suffix = Path(urlparse(url).path).suffix
    if suffix and len(suffix) <= 5:
        return suffix
    # 图床地址没有扩展名且没识别出类型时，按 jpg 兜底（绝大多数为 jpeg）
    return ".jpg"


def download_image(url: str, retries: int = 3) -> tuple:
    """下载图片，返回 (二进制内容, 扩展名)。请求不带 Referer。"""
    last_err = None
    for attempt in range(1, retries + 1):
        try:
            resp = download_session.get(url, timeout=30)
            if resp.status_code == 403:
                raise RuntimeError("403 防盗链拦截")
            resp.raise_for_status()
            data = resp.content
            if not data:
                raise RuntimeError("响应内容为空")
            ext = guess_ext(resp.headers.get("Content-Type", ""), url)
            return data, ext
        except Exception as exc:  # noqa: BLE001 - 记录后重试
            last_err = exc
            if attempt < retries:
                time.sleep(1.5 * attempt)
    raise RuntimeError(f"下载失败（重试 {retries} 次）：{last_err}")


def prepare_image(data: bytes, ext: str) -> tuple:
    """水印处理，返回 (new_data|None, status, detail)。
    status:
      "raw"       不识别类型，原样上传
      "clean"     无可见水印，原样上传
      "dewm"      已去水印，返回重编码字节
      "watermark" 仍有可见水印且无法安全去除 -> 不上传（new_data=None）
    """
    if ext.lower() in RAW_PASS_EXT:
        return data, "raw", ext
    im = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    if im is None:
        # 解码失败（少见格式/损坏）：保守起见原样上传
        return data, "raw", ext
    log = process(im)
    handled = {line.split()[0] for line in log}
    remain = remaining_groups(im, handled)
    if remain:
        return None, "watermark", ",".join(f"{g}:{v}/{d}" for g, v, d in remain)
    if not log:
        return data, "clean", ext
    if ext.lower() in (".jpg", ".jpeg"):
        ok, buf = cv2.imencode(
            ".jpg", im, [int(cv2.IMWRITE_JPEG_QUALITY), 92]
        )
    elif ext.lower() == ".png":
        ok, buf = cv2.imencode(".png", im)
    else:
        # bmp 等其它位图按 png 无损编码，并相应改扩展名
        ok, buf = cv2.imencode(".png", im)
        if ok:
            return buf.tobytes(), "dewm", ".png"
    if not ok:
        return data, "clean", "encode-fail"
    return buf.tobytes(), "dewm", ext


class HaloClient:
    def __init__(self, base_url: str, pat: str):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Authorization": f"Bearer {pat}",
                "Accept": "application/json",
                "User-Agent": "halo-image-migration/1.0",
            }
        )
        self._upload_url = None

    def _request(self, method: str, path: str, **kwargs):
        resp = self.session.request(method, f"{self.base_url}{path}", timeout=60, **kwargs)
        if resp.status_code >= 400:
            raise RuntimeError(
                f"{method} {path} -> {resp.status_code}: {resp.text[:500]}"
            )
        return resp

    def get_post(self, name: str) -> dict:
        """获取完整 Post 扩展对象（含 metadata.version，用于乐观锁更新）。"""
        return self._request("GET", f"{EXTENSION_API}/posts/{name}").json()

    def get_head_content(self, name: str) -> dict:
        return self._request("GET", f"{CONSOLE_API}/posts/{name}/head-content").json()

    def upload_attachment(self, url: str, data: bytes, ext: str) -> str:
        """上传附件，返回可公开访问的完整 URL（成功后会写入缓存）。"""
        filename = f"{Path(urlparse(url).path).name or 'image'}{ext}"
        files = {"file": (filename, data, "application/octet-stream")}

        candidates = [self._upload_url] if self._upload_url else UPLOAD_URLS
        last_err = None
        for upload_path in candidates:
            if not upload_path:
                continue
            try:
                resp = self.session.post(
                    f"{self.base_url}{upload_path}",
                    files=files,
                    timeout=120,
                )
                if resp.status_code == 404 and upload_path == UPLOAD_URLS[0]:
                    # 旧版本 Halo 没有新端点，尝试旧端点
                    last_err = RuntimeError(f"404 on {upload_path}")
                    continue
                if resp.status_code >= 400:
                    raise RuntimeError(f"{resp.status_code}: {resp.text[:300]}")
                self._upload_url = upload_path
                attachment = resp.json()
                permalink = (attachment.get("status") or {}).get("permalink") or ""
                if not permalink:
                    raise RuntimeError(f"附件返回缺少 status.permalink：{attachment}")
                return HALO_PUBLIC_URL + permalink if permalink.startswith("/") else permalink
            except Exception as exc:  # noqa: BLE001
                last_err = exc
        raise RuntimeError(f"附件上传失败：{last_err}")

    def update_and_publish_post(self, post: dict, raw: str, html: str, raw_type: str) -> None:
        name = post["metadata"]["name"]
        payload = {
            "post": post,
            "content": {
                "version": None,
                "raw": raw,
                "content": html,
                "rawType": raw_type,
            },
        }
        self._request("PUT", f"{CONSOLE_API}/posts/{name}", json=payload)
        # 更新已发布文章会产生新的 head snapshot（待发布草稿），需再发布才生效
        self._request("PUT", f"{CONSOLE_API}/posts/{name}/publish")


def list_all_posts() -> list:
    resp = download_session.get(
        f"{HALO_URL}{CONTENT_API}/posts",
        params={"size": 1000},
        headers={"Accept": "application/json"},
        timeout=60,
    )
    resp.raise_for_status()
    return resp.json()["items"]


def get_public_post_detail(name: str) -> dict:
    resp = download_session.get(
        f"{HALO_URL}{CONTENT_API}/posts/{name}",
        headers={"Accept": "application/json"},
        timeout=60,
    )
    resp.raise_for_status()
    return resp.json()


def replace_all(text: str, mapping: dict) -> tuple:
    """按映射做精确字符串替换，返回 (新文本, 替换次数)。"""
    if not text:
        return text, 0
    count = 0
    for old, new in mapping.items():
        if old in text:
            count += text.count(old)
            text = text.replace(old, new)
    return text, count


def main() -> int:
    parser = argparse.ArgumentParser(description="迁移文章外部图片到 Halo 附件库")
    parser.add_argument("--apply", action="store_true", help="真正上传并更新文章（默认仅预演）")
    parser.add_argument("--limit", type=int, default=0, help="只处理前 N 篇文章（调试用）")
    parser.add_argument("--post", action="append", default=[], help="只处理指定 metadata.name，可多次传入")
    args = parser.parse_args()

    if args.apply and not HALO_PAT:
        log("错误：--apply 需要环境变量 HALO_PAT（Halo 后台生成的个人访问令牌，pat_ 开头）")
        return 2

    mode = "正式执行" if args.apply else "预演 DRY-RUN（不上传、不改文章）"
    log(f"Halo API：{HALO_URL}　附件前缀：{HALO_PUBLIC_URL}　模式：{mode}")

    posts = list_all_posts()
    if args.post:
        wanted = set(args.post)
        posts = [p for p in posts if p["metadata"]["name"] in wanted]
    if args.limit > 0:
        posts = posts[: args.limit]
    log(f"待检查文章：{len(posts)} 篇")

    cache = load_cache()
    client = HaloClient(HALO_URL, HALO_PAT) if args.apply else None
    # 本次运行内的下载去重（跨文章同一 URL 只下载一次）
    session_downloads = {}

    stats = {"posts_changed": 0, "posts_skipped": 0, "images_uploaded": 0,
             "images_cached": 0, "images_failed": 0, "failures": [],
             "images_dewm": 0, "images_clean": 0, "images_raw": 0,
             "images_skip_wm": 0}
    skipped_wm = []

    def cached_entry(url: str):
        """v2 缓存才有效；v1（带水印）记录返回 None 强制重传。"""
        ent = cache.get(url)
        if isinstance(ent, dict) and ent.get("v", 1) >= CACHE_VERSION:
            return ent
        return None

    for idx, post_item in enumerate(posts, 1):
        name = post_item["metadata"]["name"]
        title = post_item.get("spec", {}).get("title", name)

        # 公开详情包含 content.content / content.raw
        detail = get_public_post_detail(name)
        content_html = (detail.get("content") or {}).get("content") or ""
        raw = (detail.get("content") or {}).get("raw") or ""
        raw_type = (detail.get("content") or {}).get("rawType") or "HTML"
        cover = detail.get("spec", {}).get("cover") or ""

        urls = set(extract_html_img_urls(content_html))
        # raw 按编辑器类型收集，避免把 SVG xmlns 之类的非图片 URL 误当图片
        if (raw_type or "").upper() in ("MARKDOWN", "MD", "COMMONMARK"):
            urls.update(m.group(1) for m in re.finditer(r"!\[[^\]]*\]\((https?://[^\s)]+)", raw))
        else:
            urls.update(extract_html_img_urls(raw))
        if cover:
            urls.add(cover)
        external = sorted(u for u in urls if is_external_image(u))

        if not external:
            stats["posts_skipped"] += 1
            log(f"[{idx}/{len(posts)}] 跳过（无外链图片）：{title}")
            continue

        # 先完成全部图片的下载/水印处理/上传，任一失败则整篇放弃，避免半成品。
        # 带可见水印且无法安全去除的图片不进 mapping（文章中保留外链），不算失败。
        mapping = {}
        skip_urls_here = set()
        post_ok = True
        for url in external:
            ent = cached_entry(url)
            if ent is not None:
                if ent.get("skip") == "watermark":
                    stats["images_skip_wm"] += 1
                    skip_urls_here.add(url)
                else:
                    mapping[url] = ent["newUrl"]
                    stats["images_cached"] += 1
                continue
            try:
                if url in session_downloads:
                    data, ext = session_downloads[url]
                else:
                    data, ext = download_image(url)
                    session_downloads[url] = (data, ext)
                new_data, status, detail_txt = prepare_image(data, ext)
                short = f"{urlparse(url).netloc}/{Path(urlparse(url).path).name}"
                if status == "watermark":
                    if args.apply:
                        cache[url] = {"skip": "watermark", "reason": detail_txt,
                                      "v": CACHE_VERSION}
                        save_cache(cache)
                    stats["images_skip_wm"] += 1
                    skipped_wm.append({"post": name, "url": url,
                                       "reason": detail_txt})
                    log(f"  保留外链（水印无法安全去除 {detail_txt}）：{short}")
                    skip_urls_here.add(url)
                    continue
                if status == "dewm":
                    ext = detail_txt
                    stats["images_dewm"] += 1
                elif status == "clean":
                    stats["images_clean"] += 1
                else:
                    stats["images_raw"] += 1
                if args.apply:
                    new_url = client.upload_attachment(url, new_data, ext)
                    cache[url] = {"newUrl": new_url, "ext": ext,
                                  "dewm": status == "dewm", "v": CACHE_VERSION}
                    save_cache(cache)
                    stats["images_uploaded"] += 1
                    tag = "去水印后" if status == "dewm" else ""
                    log(f"  已上传{tag} {short} -> {new_url}")
                else:
                    new_url = f"<DRY-RUN 附件地址:{Path(urlparse(url).path).name}{ext}>"
                    stats["images_uploaded"] += 1
                mapping[url] = new_url
            except Exception as exc:  # noqa: BLE001
                post_ok = False
                stats["images_failed"] += 1
                stats["failures"].append({"post": name, "url": url, "error": str(exc)})
                log(f"  图片处理失败，整篇跳过：{url} -> {exc}")

        if not post_ok:
            continue

        new_html, html_replaced = replace_all(content_html, mapping)
        new_raw, raw_replaced = replace_all(raw, mapping)
        cover_replaced = 0
        new_cover = cover
        if cover and cover in mapping:
            new_cover = mapping[cover]
            cover_replaced = 1

        total_replace = html_replaced + raw_replaced + cover_replaced
        skip_here = len(skip_urls_here)
        log(
            f"[{idx}/{len(posts)}] {title}：外链 {len(external)} 张"
            f"（保留外链 {skip_here} 张），"
            f"封面替换 {cover_replaced}，HTML 替换 {html_replaced} 处，raw 替换 {raw_replaced} 处"
        )

        if total_replace == 0:
            log("  无可替换图片（全部保留外链或无变化），文章不更新")
            continue
        if not args.apply:
            stats["posts_changed"] += 1
            continue

        # 取完整 Post 对象并只改封面，随后更新内容并发布
        full_post = client.get_post(name)
        if new_cover:
            full_post.setdefault("spec", {})["cover"] = new_cover
        client.update_and_publish_post(full_post, new_raw, new_html, raw_type)
        stats["posts_changed"] += 1
        log(f"  文章已更新并发布：{title}")

    log(
        "完成：文章更新 {posts_changed} 篇，跳过 {posts_skipped} 篇；"
        "图片新上传 {images_uploaded} 张（去水印 {images_dewm} / 无水印 {images_clean} / 原样 {images_raw}），"
        "命中缓存 {images_cached} 张，保留外链（有水印）{images_skip_wm} 张，失败 {images_failed} 张".format(
            **stats
        )
    )
    if skipped_wm:
        # 跨次去重后落盘，便于人工复核哪些图片仍热链在犬小哈图床
        uniq = {item["url"]: item for item in skipped_wm}
        skip_path = Path(__file__).with_name("image_migration_skipped.json")
        old = []
        if skip_path.exists():
            try:
                old = json.loads(skip_path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                old = []
        merged = {item["url"]: item for item in old}
        merged.update(uniq)
        skip_path.write_text(
            json.dumps(list(merged.values()), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        log(f"保留外链明细已写入：{skip_path}")
    if stats["failures"]:
        fail_path = Path(__file__).with_name("image_migration_failures.json")
        fail_path.write_text(
            json.dumps(stats["failures"], ensure_ascii=False, indent=2), encoding="utf-8"
        )
        log(f"失败明细已写入：{fail_path}（修复后重跑本脚本即可，成功的部分有缓存不会重复上传）")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
