# -*- coding: utf-8 -*-
"""离线生成"原创示意图"卡片（Pillow，零外部依赖、零版权风险）。

用途：替代教程正文中被清理的第三方截图与文章封面。卡片为装饰性示意图，
画面上明确标注"原创示意图（非软件截图）"，不冒充任何真实软件界面。

render_inline(caption, category) -> PNG bytes  (1200x900, 4:3)
render_cover(title, category)   -> PNG bytes  (1280x720, 16:9)
"""
from __future__ import annotations

import hashlib
import io
from math import cos, pi, sin

from PIL import Image, ImageDraw, ImageFont

# 与站点 #0962a9 品牌蓝协调的专业配色（深，浅）
PALETTES = [
    ((9, 98, 169), (77, 155, 216)),    # 品牌蓝
    ((15, 76, 117), (64, 155, 196)),   # 深海蓝
    ((30, 64, 120), (91, 141, 239)),   # 靛蓝
    ((13, 110, 107), (45, 176, 166)),  # 青绿
    ((15, 118, 110), (52, 211, 153)),  # 翡翠
    ((67, 56, 202), (129, 140, 248)),  # 紫罗兰
    ((30, 41, 59), (100, 116, 139)),   # 石板灰
    ((29, 78, 137), (56, 189, 248)),   # 天蓝
]
FONT_REG = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"


def _font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    for path in ([FONT_BOLD] if bold else []) + [FONT_REG]:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def _lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _gradient(size, c1, c2):
    w, h = size
    base = Image.new("RGB", size, c1)
    top = Image.new("RGB", size, c2)
    mask = Image.new("L", (1, h))
    mask.putdata([int(255 * y / max(h - 1, 1)) for y in range(h)])
    mask = mask.resize(size)
    base.paste(top, (0, 0), mask)
    return base


def _wrap(draw, text, font, max_width):
    lines, cur = [], ""
    for ch in text:
        trial = cur + ch
        if draw.textlength(trial, font=font) <= max_width or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = ch
    if cur:
        lines.append(cur)
    return lines[:5]


def _decorate(draw, w, h, dark, light):
    # 半透明大圆与点阵，增加层次但不喧宾夺主
    ov = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    od.ellipse([w - 360, -180, w + 120, 300], fill=(*light, 60))
    od.ellipse([-200, h - 260, 260, h + 200], fill=(255, 255, 255, 26))
    for i in range(10):
        for j in range(6):
            x, y = 80 + i * 56, 70 + j * 56
            od.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(255, 255, 255, 28))
    return ov


def _draw_glyph(draw, cx, cy, r, ink):
    """画一个"图片占位"线稿图标：圆角框 + 太阳 + 山峦，暗示这是示意图而非截图。"""
    x0, y0, x1, y1 = cx - r, cy - int(r * 0.72), cx + r, cy + int(r * 0.72)
    draw.rounded_rectangle([x0, y0, x1, y1], radius=18, outline=ink, width=8)
    draw.ellipse([cx + r - 78, y0 + 26, cx + r - 30, y0 + 74], outline=ink, width=7)
    # 两座山
    draw.line([(x0 + 40, y1 - 40), (cx - 30, cy - 10), (cx + 10, cy + 26),
               (cx + 55, cy - 18)], fill=ink, width=8, joint="curve")
    draw.line([(cx + 20, y1 - 44), (cx + 90, cy - 20), (x1 - 36, y1 - 58)],
              fill=ink, width=8, joint="curve")


def _render(caption: str, category: str, w: int, h: int, title_size: int) -> bytes:
    idx = int(hashlib.md5(f"{category}|{caption}".encode("utf-8")).hexdigest()[:8], 16)
    dark, light = PALETTES[idx % len(PALETTES)]
    img = _gradient((w, h), dark, light)
    img = Image.alpha_composite(img.convert("RGBA"), _decorate(None, w, h, dark, light)).convert("RGB")
    d = ImageDraw.Draw(img)

    # 中央白色卡片
    pad_x, card_w = 90, w - 180
    x0, x1 = 90, w - 90
    y0, y1 = int(h * 0.16), int(h * 0.84)
    d.rounded_rectangle([x0, y0, x1, y1], radius=28, fill=(255, 255, 255))

    # 分类胶囊
    cat = (category or "教程").strip()[:14]
    f_pill = _font(30, True)
    tw = d.textlength(cat, font=f_pill)
    d.rounded_rectangle([130, y0 + 56, 130 + tw + 56, y0 + 116], radius=30,
                        fill=(*dark, 255) if len(dark) == 3 else dark)
    d.text((158, y0 + 68), cat, font=f_pill, fill=(255, 255, 255))

    _draw_glyph(d, w // 2, y0 + 230, 70, _lerp(dark, light, 0.35))

    # 图注主文字
    f_body = _font(title_size, True)
    lines = _wrap(d, caption or "示意图", f_body, card_w - 120)
    total_h = len(lines) * int(title_size * 1.45)
    ty = (y0 + y1) // 2 - total_h // 2 + 40
    ink = (30, 41, 59)
    for line in lines:
        lw = d.textlength(line, font=f_body)
        d.text((w // 2 - lw // 2, ty), line, font=f_body, fill=ink)
        ty += int(title_size * 1.45)

    # 底部声明
    f_note = _font(26)
    note = "码上岸 · 原创示意图（非软件截图）"
    nw = d.textlength(note, font=f_note)
    d.text((w // 2 - nw // 2, y1 - 66), note, font=f_note, fill=(100, 116, 139))

    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def render_inline(caption: str, category: str = "教程") -> bytes:
    """正文配图 1200x900（4:3）。"""
    return _render(caption, category, 1200, 900, 46)


def render_cover(title: str, category: str = "教程") -> bytes:
    """文章封面 1280x720（16:9）。"""
    return _render(title, category, 1280, 720, 52)
