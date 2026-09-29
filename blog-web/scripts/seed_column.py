#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
创建 /column 页面所需的数据：
  1. slug=project 的「项目实战」分类（若不存在）；
  2. 一篇原创发刊词文章并发布（若已存在同 slug 文章则跳过）。
幂等，可重复执行。正式执行：HALO_PAT=pat_xxx python seed_column.py --apply
"""
import argparse
import os
import sys
import time

import requests

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parent))
from card_image import render_cover  # noqa: E402

HALO_URL = os.environ.get("HALO_URL", "http://49.235.136.65:8090").rstrip("/")
HALO_PUBLIC_URL = os.environ.get("HALO_PUBLIC_URL", HALO_URL).rstrip("/")
HALO_PAT = os.environ.get("HALO_PAT", "").strip()
CONTENT_API = "/apis/api.content.halo.run/v1alpha1"
CONSOLE_API = "/apis/api.console.halo.run/v1alpha1"
EXTENSION_API = "/apis/content.halo.run/v1alpha1"
UPLOAD_URLS = [
    "/apis/console.api.storage.halo.run/v1alpha1/attachments/-/upload",
    f"{CONSOLE_API}/attachments/upload",
]

SLUG = "project"
DISPLAY = "项目实战"
DESCRIPTION = "企业级项目从 0 到 1 实战讲解：需求拆解、架构设计、编码落地到部署上线，以连载方式持续更新。"
POST_SLUG = "project-column-preface"

BODY_HTML = """<h2>为什么要有这个专栏</h2>
<p>很多同学的学习路径是这样的：语法看完了、八股背了不少、视频也刷了好几套，但一打开 IDE 还是不知道一个真实项目该从哪里下手——表怎么设计、包怎么分、接口先写哪个、异常怎么统一处理、部署时又冒出一堆新名词。</p>
<p>「看得懂」和「写得出」之间，差的是一次完整的从 0 到 1。这个专栏就是把这段路掰开了走给你看：每个项目都从需求分析开始，经历架构设计、编码实现、测试联调到容器化部署，全过程留痕，不跳步、不堆 PPT。</p>
<h2>专栏怎么更新</h2>
<p>项目以<strong>连载</strong>形式推进，每篇只解决一个阶段的问题，建议按顺序阅读：</p>
<ol>
<li><strong>需求与建模</strong>：把一句话需求拆成可开发的功能清单，设计数据库表结构与接口契约；</li>
<li><strong>骨架搭建</strong>：工程分层、统一返回体与异常处理、参数校验、日志与配置管理；</li>
<li><strong>核心功能</strong>：按模块逐个落地，关键代码都会解释"为什么这样写"；</li>
<li><strong>质量与部署</strong>：单元测试、接口联调、Docker 镜像与云上部署、常见线上问题排查。</li>
</ol>
<h2>你会看到什么项目</h2>
<p>选题全部来自真实工作场景，计划覆盖三类方向：</p>
<ul>
<li><strong>业务系统类</strong>：Spring Boot + Vue 的前后端分离全栈项目，练透 CRUD 之外的工程基本功；</li>
<li><strong>高并发类</strong>：秒杀、短链、签到等场景，练习缓存、消息队列与限流降级的组合拳；</li>
<li><strong>微服务改造类</strong>：把一个单体逐步拆成 Spring Cloud 微服务，理解每一步拆分的收益与代价。</li>
</ul>
<p>具体项目排期会在后续连载文章中公布。已完结的项目会在卡片上标注「已完结」，更新中的项目标注「连载中」。</p>
<h2>跟着练的建议</h2>
<p>不要只收藏。每篇文章读完后，自己动手把当天的代码敲一遍、跑起来，再尝试改动一个需求；遇到报错先看日志再搜索。能把项目独立跑通并讲清楚每一层的职责，它才算真正变成了你简历上的项目。</p>
<p>我们第一篇连载见。</p>"""

EXCERPT = "从看懂教程到独立做出项目，中间差一次完整的从 0 到 1。本专栏用真实场景项目，带你走完需求拆解、架构设计、编码落地到部署上线的全过程。"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if args.apply and not HALO_PAT:
        print("错误：--apply 需要环境变量 HALO_PAT")
        return 2

    s = requests.Session()
    s.headers.update({"Accept": "application/json"})
    if HALO_PAT:
        s.headers["Authorization"] = f"Bearer {HALO_PAT}"

    # 1) 分类（公开列表只读；创建走扩展 API）
    r = s.get(f"{HALO_URL}{CONTENT_API}/categories?size=200", timeout=60)
    existing = {c["spec"]["slug"]: c for c in r.json()["items"]}
    cat = existing.get(SLUG)
    if cat is None:
        print(f"创建分类：{SLUG}")
        body = {
            "apiVersion": "content.halo.run/v1alpha1",
            "kind": "Category",
            "metadata": {"name": SLUG},
            "spec": {"displayName": DISPLAY, "slug": SLUG, "cover": "",
                     "description": DESCRIPTION, "template": "",
                     "hideFromList": False, "preventParentPostCascadeQuery": False,
                     "priority": 0},
        }
        if args.apply:
            rr = s.post(f"{HALO_URL}{EXTENSION_API}/categories", json=body, timeout=60)
            if rr.status_code not in (200, 201):
                print(f"分类创建失败 {rr.status_code}: {rr.text[:300]}")
                return 1
            cat = rr.json()
        else:
            print("DRY-RUN：分类未创建")
    else:
        print(f"分类已存在：{SLUG}")

    # 2) 发刊词（metadata.name 固定，直接按名查询）
    post_name = f"post-{POST_SLUG}"
    existed = s.get(f"{HALO_URL}{EXTENSION_API}/posts/{post_name}",
                    timeout=60, allow_redirects=False)
    if existed.status_code == 200 and "json" in existed.headers.get("Content-Type", ""):
        print("发刊词已存在，跳过（如需重建请先在后台删除该文章）")
        return 0
    if not args.apply and existed.status_code in (401, 403, 302):
        print("DRY-RUN：无令牌无法确认文章是否存在（执行时会先查重）")

    cover_url = ""
    if args.apply:
        png = render_cover("项目实战专栏发刊词", DISPLAY)
        files = {"file": ("project-column-cover.png", png, "image/png")}
        last = None
        for path in UPLOAD_URLS:
            rr = s.post(f"{HALO_URL}{path}", files=files, timeout=180)
            if rr.status_code == 404 and path == UPLOAD_URLS[0]:
                last = "404"; continue
            if rr.status_code >= 400:
                last = rr.text[:200]; break
            pl = (rr.json().get("status") or {}).get("permalink") or ""
            cover_url = HALO_PUBLIC_URL + pl if pl.startswith("/") else pl
            break
        if not cover_url:
            print(f"封面上传失败：{last}")
            return 1
        print("封面已上传：", cover_url)
        time.sleep(0.5)

    post_name = f"post-{POST_SLUG}"
    post_body = {
        "apiVersion": "content.halo.run/v1alpha1",
        "kind": "Post",
        "metadata": {"name": post_name,
                     "annotations": {"haloweb/series-status": "updating"}},
        "spec": {
            "title": "项目实战专栏发刊词：从看懂教程到做出项目",
            "slug": POST_SLUG, "categories": [SLUG], "tags": [],
            "cover": cover_url,
            "excerpt": {"raw": EXCERPT, "autoGenerate": False},
            "publish": False, "visible": "PUBLIC", "deleted": False, "pinned": True,
            "allowComment": True, "priority": 0,
        },
    }
    payload = {"post": post_body,
               "content": {"raw": BODY_HTML, "content": BODY_HTML, "rawType": "HTML"}}
    print("创建并发刊发刊词…")
    if args.apply:
        rr = s.post(f"{HALO_URL}{CONSOLE_API}/posts", json=payload, timeout=60)
        if rr.status_code not in (200, 201):
            print(f"文章创建失败 {rr.status_code}: {rr.text[:300]}")
            return 1
        pr2 = s.put(f"{HALO_URL}{CONSOLE_API}/posts/{post_name}/publish", timeout=60)
        if pr2.status_code >= 300:
            print(f"发布失败 {pr2.status_code}: {pr2.text[:300]}")
            return 1
        print("完成：/column 现在应显示专栏卡片（连载中）")
    else:
        print("DRY-RUN：文章未创建")
    return 0


if __name__ == "__main__":
    sys.exit(main())
