#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
补救上一轮（v1，带水印）附件：对仍引用 /upload 旧图的文章逐张重新判定。
- v1 缓存给出 外链URL -> 旧附件URL 的映射；
- 重新下载外链并走 prepare_image：
    dewm/clean -> 上传新版本，文章旧附件 URL 替换为新 URL，旧图变孤儿（稍后清理）；
    watermark  -> 旧附件本身是自托管且同样带水印，保留旧引用不动；
- 封面同步替换；默认 DRY-RUN，--apply 才上传/更新。
"""
import argparse
import json
import sys
import time
from pathlib import Path
from urllib.parse import urlparse

sys.path.insert(0, str(Path(__file__).resolve().parent))
import migrate_images as mi  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if args.apply and not mi.HALO_PAT:
        print("错误：--apply 需要 HALO_PAT")
        return 2

    old = json.loads(mi.Path(mi.CACHE_FILE).with_name(
        "image_migration_cache.v1.bak.json").read_text(encoding="utf-8"))
    ext_by_url = {v["newUrl"]: k for k, v in old.items()}  # 旧附件URL -> 外链

    client = mi.HaloClient(mi.HALO_URL, mi.HALO_PAT) if args.apply else None
    posts = mi.list_all_posts()
    print(f"文章 {len(posts)} 篇，v1 旧图 {len(ext_by_url)} 张，模式："
          f"{'APPLY' if args.apply else 'DRY-RUN'}")

    # 新图上传结果缓存（本次运行内，跨文章去重）：旧URL -> (新URL|None,status)
    done = {}
    stats = {"posts": 0, "dewm": 0, "clean": 0, "raw": 0, "keep_wm": 0,
             "fail": 0}

    for idx, item in enumerate(posts, 1):
        name = item["metadata"]["name"]
        detail = mi.get_public_post_detail(name)
        content = detail.get("content") or {}
        html = content.get("content") or ""
        raw = content.get("raw") or ""
        cover = (detail.get("spec") or {}).get("cover") or ""
        blob = " ".join([html, raw, cover])
        used = [u for u in ext_by_url if u in blob]
        if not used:
            continue

        mapping = {}
        for old_url in used:
            if old_url in done:
                new_url, status = done[old_url]
            else:
                ext_url = ext_by_url[old_url]
                try:
                    data, ext = mi.download_image(ext_url)
                    new_data, status, detail_txt = mi.prepare_image(data, ext)
                    if status == "watermark":
                        new_url = None
                    elif args.apply:
                        up_ext = detail_txt if status == "dewm" else ext
                        new_url = client.upload_attachment(ext_url, new_data, up_ext)
                    else:
                        new_url = f"<DRY {Path(urlparse(old_url).path).name}->{status}>"
                    done[old_url] = (new_url, status)
                except Exception as e:  # noqa: BLE001
                    stats["fail"] += 1
                    print(f"  [失败] {ext_url} -> {e}")
                    done[old_url] = (None, "error")
                    continue
            if new_url:
                mapping[old_url] = new_url
                stats[status if status in ("dewm", "clean", "raw") else "raw"] += 1
            else:
                stats["keep_wm"] += 1

        if not mapping:
            print(f"[{idx}/{len(posts)}] {item.get('spec',{}).get('title',name)[:30]}"
                  f"：{len(used)} 张旧图全部保留（无法安全去水印）")
            continue

        new_html, c1 = mi.replace_all(html, mapping)
        new_raw, c2 = mi.replace_all(raw, mapping)
        new_cover = mapping.get(cover, cover)
        print(f"[{idx}/{len(posts)}] {item.get('spec',{}).get('title',name)[:30]}"
              f"：替换 HTML {c1} / raw {c2} / 封面 {int(cover in mapping)}"
              f"，保留带水印旧图 {len(used)-len(mapping)} 张")

        if args.apply:
            full = client.get_post(name)
            if cover in mapping:
                full.setdefault("spec", {})["cover"] = new_cover
            client.update_and_publish_post(
                full, new_raw, new_html,
                content.get("rawType") or "HTML")
            stats["posts"] += 1
            time.sleep(0.1)
        else:
            stats["posts"] += 1

    print(f"完成：更新文章 {stats['posts']} 篇；新图 去水印{stats['dewm']}/"
          f"无水印{stats['clean']}/原样{stats['raw']}；保留带水印自托管 {stats['keep_wm']}；"
          f"失败 {stats['fail']}")
    return 1 if stats["fail"] else 0


if __name__ == "__main__":
    sys.exit(main())
