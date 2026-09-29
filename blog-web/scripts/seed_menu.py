#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
给主菜单(primary)补齐三个根级入口：项目实战 /column、在线工具 /tools、知识星球 /zsxq。

顺序：首页(0) Java面试八股(1) 文章(2) 项目实战(3) 在线工具(4) 知识星球(5) 关于(6)
Halo 2.26：MenuItem.spec.menuName 归属菜单，根项不设 parent；
菜单展示顺序以 Menu.spec.menuItems 数组为准。
幂等：按 href 识别已存在的项，不重复创建。
执行：HALO_PAT=pat_xxx python seed_menu.py --apply
"""
import argparse
import os
import sys

import requests

HALO_URL = os.environ.get("HALO_URL", "http://49.235.136.65:8090").rstrip("/")
HALO_PAT = os.environ.get("HALO_PAT", "").strip()
# group 为空的扩展走 core 风格端点 /api/v1alpha1/...
CORE = "/api/v1alpha1"
PUBLIC_MENU = "/apis/api.halo.run/v1alpha1/menus/-"

# href -> (显示名, 固定 name, priority)
ITEMS = [
    ("/column", "项目实战", "menu-item-column", 3),
    ("/tools", "在线工具", "menu-item-tools", 4),
    ("/zsxq", "知识星球", "menu-item-zsxq", 5),
]
# 既有项需要的目标优先级（"关于"原为 4，会与新增项冲突，调整为 6）
PRIORITY_FIX = {"/about": 6}
ORDER = ["/", "/java-interview", "/archives", "/column", "/tools", "/zsxq", "/about"]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if args.apply and not HALO_PAT:
        print("错误：--apply 需要环境变量 HALO_PAT")
        return 2

    s = requests.Session()
    s.headers.update({"Accept": "application/json",
                      "Authorization": f"Bearer {HALO_PAT}"})

    # 公开聚合视图：href -> item
    agg = requests.get(f"{HALO_URL}{PUBLIC_MENU}", timeout=60).json()
    menu_view = agg.get("menu") or agg
    by_href = {}
    for it in menu_view.get("menuItems") or []:
        href = (it.get("status") or {}).get("href") or it.get("spec", {}).get("href")
        by_href[href] = it
    print("现有菜单：", [(i.get("status", {}).get("displayName")
                          or i["spec"].get("displayName"),
                          (i.get("status") or {}).get("href") or i["spec"].get("href"))
                         for i in menu_view.get("menuItems") or []])

    created = {}
    for href, name, fixed_name, priority in ITEMS:
        if href in by_href:
            print(f"已存在，跳过：{name} {href}")
            created[href] = by_href[href]["metadata"]["name"]
            continue
        body = {
            "apiVersion": "v1alpha1",
            "kind": "MenuItem",
            "metadata": {"name": fixed_name},
            "spec": {
                "displayName": name, "href": href, "target": "_self",
                "priority": priority, "menuName": "primary",
            },
        }
        print(f"创建菜单项：{name} {href}")
        if args.apply:
            r = s.post(f"{HALO_URL}{CORE}/menuitems", json=body, timeout=60)
            if r.status_code not in (200, 201):
                print(f"  创建失败 {r.status_code}: {r.text[:300]}")
                return 1
            created[href] = fixed_name

    # 既有项优先级纠偏（如"关于"）
    if args.apply:
        for href, want_p in PRIORITY_FIX.items():
            old_item = by_href.get(href)
            if old_item and old_item.get("spec", {}).get("priority") != want_p:
                n = old_item["metadata"]["name"]
                full = s.get(f"{HALO_URL}{CORE}/menuitems/{n}", timeout=60).json()
                full["spec"]["priority"] = want_p
                rr = s.put(f"{HALO_URL}{CORE}/menuitems/{n}", json=full, timeout=60)
                print(f"优先级调整：{full['spec'].get('displayName')} -> {want_p} ({rr.status_code})")

    # 取完整 Menu 对象并重排 menuItems 数组
    if args.apply:
        r = s.get(f"{HALO_URL}{CORE}/menus/primary", timeout=60)
        if r.status_code >= 400:
            print(f"菜单对象获取失败 {r.status_code}: {r.text[:300]}")
            return 1
        menu = r.json()
        # 重新拉一次聚合视图拿到全部 name（含新建）
        agg2 = requests.get(f"{HALO_URL}{PUBLIC_MENU}", timeout=60).json()
        mv2 = agg2.get("menu") or agg2
        href_name = {}
        for it in mv2.get("menuItems") or []:
            h = (it.get("status") or {}).get("href") or it.get("spec", {}).get("href")
            href_name[h] = it["metadata"]["name"]
        ordered = [href_name[h] for h in ORDER if h in href_name]
        # 兜底：任何不在 ORDER 里的项追加在末尾，避免丢项
        known = set(ordered)
        for it in mv2.get("menuItems") or []:
            n = it["metadata"]["name"]
            if n not in known:
                ordered.append(n)
        old = menu.get("spec", {}).get("menuItems") or []
        if old != ordered:
            menu["spec"]["menuItems"] = ordered
            pr = s.put(f"{HALO_URL}{CORE}/menus/primary", json=menu, timeout=60)
            if pr.status_code >= 300:
                print(f"菜单重排失败 {pr.status_code}: {pr.text[:300]}")
                return 1
            print("菜单顺序已更新：", ORDER)
        else:
            print("菜单顺序无需调整")
    print("完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
