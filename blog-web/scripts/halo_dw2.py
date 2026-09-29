# -*- coding: utf-8 -*-
"""
去水印核心 v3（统一几何 + 局部背景场）
- big  900x180 底距207：RW=min(900,w-92)，垂直不缩；缩放组 x 偏置 -2
- url  696x72  底距88 ：RW=min(696,w-92)，垂直不缩
- foot 740x56  底距2  ：固定字号水平居中，窄图溢出取居中段
- 背景：非字形区 max 池化估逐像素亮背景（适配白纸+文字行截图）
- 检测：浅环境 core 像素上温和暗化占比；深色底环带 NCC 兜底
- 恢复：逐图 k 除算 + 软混合 + 环带二次清理
"""
from pathlib import Path
import cv2
import numpy as np

TPLD = Path(__file__).resolve().parent / "halo_templates"

def _load(name):
    t = np.load(TPLD / name).clip(0, 0.3).astype(np.float32)
    t[t < 0.008] = 0
    return t

BIG0 = _load("big_final.npy")
URL0 = _load("url_final.npy")
FOOT0 = _load("foot_final.npy")


def _resize_tpl(t0, rw, rh):
    if (rw, rh) == t0.shape[1::-1]:
        return t0
    t = cv2.resize(t0, (rw, rh), interpolation=cv2.INTER_LINEAR)
    t[t < 0.008] = 0
    return t


def _ring_mask(tg, k=3):
    core = (tg > 0.055).astype(np.uint8)
    ker = np.ones((2*k+1, 2*k+1), np.uint8)
    return (cv2.dilate(core, ker) - cv2.erode(core, ker)) > 0


def _bg_field(groi, tg, ksize=25, local=False):
    """背景场 + 可信浅底掩膜。
    local=False：均匀浅底单值 bg（大尺寸成熟路径），整 ROI 可信/不可信
    local=True ：max 场补白底 + 形态学开运算排除灰色 UI 大面板块"""
    if not local:
        bgn = tg < 0.015
        bv = groi[bgn & (groi > 150)]
        if len(bv) < 400:
            return np.full_like(groi, 100), np.zeros(groi.shape, bool)
        bg = float(np.median(bv))
        iqr = float(np.percentile(bv, 85)-np.percentile(bv, 15))
        trusted = np.ones(groi.shape, bool) if (bg > 165 and iqr <= 16) \
            else np.zeros(groi.shape, bool)
        return np.full_like(groi, bg), trusted
    k = 21 if local else max(5, ksize | 1)
    ak = 3 if local else 7
    avoid = cv2.dilate((tg > 0.02).astype(np.uint8),
                       np.ones((ak, ak), np.uint8))
    samp = groi.copy()
    samp[avoid > 0] = 0
    bgmax = cv2.dilate(samp, np.ones((k, k), np.uint8)).astype(np.float32)
    # 邻域内看得到近白色（>=248）才可信：白页/文字行间隙通过，
    # 灰按钮面(~240)/深色UI/彩色块不可信，不恢复
    trusted = bgmax >= 248
    return bgmax, trusted


def _shift(tpl3, sx, sy):
    H, W = tpl3.shape[:2]
    M = np.array([[1, 0, sx], [0, 1, sy]], dtype=np.float32)
    return cv2.warpAffine(tpl3, M, (W, H), flags=cv2.INTER_LINEAR, borderValue=0)


def _ncc_at(g, tg, ring, x, y):
    H, W = tg.shape
    if x < 0 or y < 0 or y+H > g.shape[0] or x+W > g.shape[1]:
        return 0.0
    p = g[y:y+H, x:x+W][ring].astype(np.float64)
    t = tg[ring].astype(np.float64)
    p -= p.mean(); t -= t.mean()
    den = np.sqrt((p*p).sum())*np.sqrt((t*t).sum())
    return float((p*t).sum()/den) if den > 1e-9 else 0.0


def _evaluate(groi, tg, ks=25, dhi=0.22, dstr=0.32, envlo=185, local=False):
    """返回 (score, dmed, lightfrac, gentle, strong, bgf)；可信浅底上统计"""
    bgf, trusted = _bg_field(groi, tg, ks, local=local)
    core = tg > 0.05
    cc = core & trusted
    if cc.sum() < 300:
        return -9, 0.0, 0.0, 0.0, 0.0, bgf
    d = (bgf-groi)/np.clip(bgf, 60, 255)
    dv = d[cc]
    lf = cc.sum()/max(core.sum(), 1)
    gentle = ((dv > 0.03) & (dv < dhi)).mean()
    strong = (dv > dstr).mean()
    score = float(gentle - 0.8*strong)
    pos = dv[(dv > 0.02) & (dv < dstr)]
    dmed = float(np.median(pos)) if len(pos) else 0.0
    return score, dmed, float(lf), float(gentle), float(strong), bgf


def _refine(g, tg, x, y, radius=3, ks=25, dhi=0.22, dstr=0.32, envlo=185,
            rules=((0.33, 0.20, 0.70),), local=False):
    """±radius 搜索；浅底在满足 rules 的位置中取 gentle 最大；深色底 NCC"""
    H, W = tg.shape
    ring = _ring_mask(tg)
    hit_best = None    # 满足规则
    any_best = None    # 任意浅底（sc 最大）
    dark_best = None
    for dy in range(-radius, radius+1):
        for dx in range(-radius, radius+1):
            xx, yy = x+dx, y+dy
            if xx < 0 or yy < 0 or yy+H > g.shape[0] or xx+W > g.shape[1]:
                continue
            roi = g[yy:yy+H, xx:xx+W]
            sc, dmed, lf, ge, st, bgf = _evaluate(roi, tg, ks, dhi, dstr,
                                                  envlo, local)
            if lf > 0.5:
                cand = (sc, dx, dy, dmed, lf, ge, st, bgf)
                if any_best is None or sc > any_best[0]:
                    any_best = cand
                if any(ge > gmin and st < smax and lf > lmin
                       for gmin, smax, lmin in rules):
                    if hit_best is None or ge > hit_best[5]:
                        hit_best = cand
            nc = _ncc_at(g, tg, ring, xx, yy)
            if nc < -0.62 and (dark_best is None or nc < dark_best[0]):
                dark_best = (nc, dx, dy)
    if hit_best is not None:
        return ("light",) + hit_best
    if dark_best is not None:
        return ("dark", dark_best[0], dark_best[1], dark_best[2],
                0.0, 0.0, 0.0, 0.0, None)
    if any_best is not None:
        return ("miss",) + any_best
    return ("none", -9, 0, 0, 0.0, 0.0, 0.0, 0.0, None)


def _apply(im, x, y, tpl, dx, dy, k, ks=25, local=False):
    a = _shift(np.clip(tpl*k, 0, 0.35), dx, dy)
    H, W = a.shape[:2]
    ax1, ay1 = max(0, x), max(0, y)
    ax2, ay2 = min(im.shape[1], x+W), min(im.shape[0], y+H)
    tx, ty = ax1-x, ay1-y
    sub = im[ay1:ay2, ax1:ax2].astype(np.float32)
    aa = a[ty:ty+sub.shape[0], tx:tx+sub.shape[1]]
    # 命中位置重算背景场（模板需同步 shift 掩膜）
    ag0 = _shift(tpl, dx, dy).mean(2)
    gray0 = cv2.cvtColor(sub.astype(np.uint8), cv2.COLOR_BGR2GRAY).astype(np.float32)
    bgf, trusted = _bg_field(gray0, ag0, ks, local=local)
    rec = np.clip(sub/np.clip(1-aa, 0.55, 1), 0, 255)
    m = np.clip(aa/0.02, 0, 1) * trusted[..., None]   # 仅可信浅底恢复
    out = sub*(1-m)+rec*m
    # 环带二次清理（同样限可信区）
    cm = (ag0 > 0.04).astype(np.uint8)
    band = ((cv2.dilate(cm, np.ones((7, 7), np.uint8)) > 0)
            & (ag0 < 0.03) & trusted)
    gray = cv2.cvtColor(out.astype(np.uint8), cv2.COLOR_BGR2GRAY).astype(np.float32)
    bgf2, tr2 = _bg_field(gray, ag0, max(ks-4, 11), local=local)
    resid = np.clip((bgf2-gray)/np.clip(bgf2, 60, 255), 0, 0.10)*band
    resid = cv2.GaussianBlur(resid, (0, 0), 0.8)[..., None]
    out = np.clip(out/np.clip(1-resid, 0.85, 1), 0, 255)
    im[ay1:ay2, ax1:ax2] = out.astype(np.uint8)


def _do_group(im, g, t0, rw, rh, x, y, dx_bias, ks, dhi, dstr, rules,
              mode="uniform"):
    """mode: uniform（单值浅底）| local（面板安全场）| auto（先u后l）"""
    tpl = _resize_tpl(t0, rw, rh)
    x += dx_bias
    tg = tpl.mean(2)
    attempts = [False, True] if mode == "auto" else [mode == "local"]
    for local in attempts:
        r = _refine(g, tg, x, y, ks=ks, dhi=dhi, dstr=dstr,
                    rules=rules, local=local)
        kind, sc, dx, dy, dmed, lf, ge, st, bgf = r
        if kind == "light":
            tmed = float(np.median(tg[tg > 0.06]))
            k = float(np.clip(dmed/max(tmed, 1e-3), 0.7, 1.25))
            _apply(im, x, y, tpl, dx, dy, k, ks=ks, local=local)
            return f"{'L' if local else 'U'} g={ge:.2f} s={st:.2f} lf={lf:.2f} k={k:.2f} ({dx},{dy})"
    return None


def process(im, verbose=False):
    g = cv2.cvtColor(im, cv2.COLOR_BGR2GRAY).astype(np.float32)
    h, w = g.shape
    log = []

    if h >= 387:
        rw = min(900, w-92)
        if rw > 200:
            rules = ([(0.45, 0.15, 0.90)] if w >= 992
                     else [(0.30, 0.18, 0.65), (0.38, 0.30, 0.55)])
            r = _do_group(im, g, BIG0, rw, 180, w-16-rw, h-207-180,
                          -2 if rw < 900 else 0, 25, 0.22, 0.32,
                          rules, mode="auto")
            if r:
                log.append("big " + r)

    if h >= 160:
        rw = min(696, w-92)
        if rw > 200:
            rules = ([(0.45, 0.15, 0.90)] if rw >= 696
                     else [(0.28, 0.25, 0.85)])
            r = _do_group(im, g, URL0, rw, 72, w-16-rw, h-88-72, 0,
                          21, 0.22, 0.32, rules, mode="auto")
            if r:
                log.append("url " + r)

    if h >= 64:
        rw = min(740, w)
        x = (w-rw)//2
        y = h-58
        tpl = FOOT0 if rw == 740 else FOOT0[:, (740-rw)//2:(740-rw)//2+rw]
        r = _refine(g, tpl.mean(2), x, y, radius=2, ks=15,
                    dhi=0.30, dstr=0.40, rules=((0.24, 0.30, 0.75),),
                    local=False)
        kind, sc, dx, dy, dmed, lf, ge, st, bgf = r
        # foot 真实字心 d≈0.13-0.20；按钮灰栏假阳性 dmed<0.11
        if kind == "light" and 0.11 <= dmed <= 0.28:
            tmed = float(np.median(tpl.mean(2)[tpl.mean(2) > 0.06]))
            k = float(np.clip(dmed/max(tmed, 1e-3), 0.7, 1.25))
            _apply(im, x, y, tpl, dx, dy, k, ks=15, local=False)
            log.append(f"foot g={ge:.2f} s={st:.2f} k={k:.2f} ({dx},{dy})")

    return log


def remaining_groups(im, handled):
    """未处理几何组上是否仍有"肉眼可见"的水印（中亮底 170~247 上的黑字）。
    handled：process() 已成功处理的组名集合；返回 [(组名, 可见占比, dmed), ...]。
    深底（邻域看不到 >=170 像素）上 alpha=0.075 的黑字差异 <3%，不可见，放行。"""
    g = cv2.cvtColor(im, cv2.COLOR_BGR2GRAY).astype(np.float32)
    h, w = g.shape
    groups = []
    if h >= 387:
        rw = min(900, w - 92)
        if rw > 200:
            groups.append(("big", _resize_tpl(BIG0, rw, 180),
                           w - 16 - rw + (-2 if rw < 900 else 0), h - 207 - 180))
    if h >= 160:
        rw = min(696, w - 92)
        if rw > 200:
            groups.append(("url", _resize_tpl(URL0, rw, 72),
                           w - 16 - rw, h - 88 - 72))
    out = []
    for name, tpl, x, y in groups:
        if name in handled:
            continue
        TH, TW = tpl.shape[:2]
        if x < 0 or y < 0 or y + TH > h or x + TW > w:
            continue
        tg = tpl.mean(2)
        roi = g[y:y + TH, x:x + TW]
        avoid = cv2.dilate((tg > 0.02).astype(np.uint8),
                           np.ones((3, 3), np.uint8))
        samp = roi.copy()
        samp[avoid > 0] = 0
        bgmax = cv2.dilate(samp, np.ones((21, 21), np.uint8)).astype(np.float32)
        core = tg > 0.05
        # 当前像素本身也要中亮：黑底上的黑字水印不可见，
        # 且不能让终端白字的 bgmax=255 把邻近黑底反差误算成水印
        cc = core & (bgmax >= 170) & (roi >= 128)
        if cc.sum() / max(core.sum(), 1) < 0.35:
            continue
        d = (bgmax - roi) / np.clip(bgmax, 60, 255)
        dv = d[cc]
        vis = float((dv > 0.055).mean())
        band = dv[(dv > 0.02) & (dv < 0.40)]
        dmed = float(np.median(band)) if len(band) else 0.0
        if vis >= 0.35 and dmed >= 0.06:
            out.append((name, round(vis, 2), round(dmed, 3)))
    return out
