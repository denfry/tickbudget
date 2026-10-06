"""Generates the TickBudget logo and icon deterministically (no AI, no external assets).

Usage: python scripts/generate_logo.py
Requires: Pillow
Output: .github/assets/logo.png (1024x1024), .github/assets/icon.png (512x512)
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 1024
SCALE = 4  # supersampling for smooth edges
S = SIZE * SCALE

BG_TOP = (15, 23, 42)
BG_BOTTOM = (30, 41, 59)
TRACK = (51, 65, 85)
ACCENT = (34, 197, 94)
ACCENT_DIM = (71, 85, 105)
TICK = (100, 116, 139)
HAND = (241, 245, 249)


def px(v):
    return int(round(v * SCALE))


def gradient_background():
    img = Image.new("RGB", (S, S))
    draw = ImageDraw.Draw(img)
    for y in range(S):
        t = y / (S - 1)
        color = tuple(int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3))
        draw.line([(0, y), (S, y)], fill=color)
    return img


def polar(cx, cy, r, deg):
    a = math.radians(deg)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def round_cap(draw, cx, cy, r, deg, width, color):
    x, y = polar(cx, cy, r, deg)
    w = width / 2
    draw.ellipse([px(x - w), px(y - w), px(x + w), px(y + w)], fill=color)


def build():
    img = gradient_background()
    draw = ImageDraw.Draw(img)

    cx, cy, r, width = 512, 440, 270, 64
    box = [px(cx - r), px(cy - r), px(cx + r), px(cy + r)]

    # Tick marks around the dial: one per 5 ms of a 50 ms tick (10 marks).
    for i in range(10):
        deg = -90 + i * 36
        x1, y1 = polar(cx, cy, r + 58, deg)
        x2, y2 = polar(cx, cy, r + 98, deg)
        draw.line([(px(x1), px(y1)), (px(x2), px(y2))], fill=TICK, width=px(14))

    # Track ring, then the used-budget arc (270 degrees = 75% of the tick).
    draw.arc(box, 0, 360, fill=TRACK, width=px(width))
    start, end = -90, 180
    draw.arc(box, start, end, fill=ACCENT, width=px(width))
    round_cap(draw, cx, cy, r - width / 2, start, width, ACCENT)
    round_cap(draw, cx, cy, r - width / 2, end, width, ACCENT)

    # Clock hand pointing at the end of the used arc, plus hub.
    hx, hy = polar(cx, cy, r - width / 2 - 40, end)
    draw.line([(px(cx), px(cy)), (px(hx), px(hy))], fill=HAND, width=px(26))
    for (x, y, rad) in ((hx, hy, 13), (cx, cy, 34)):
        draw.ellipse([px(x - rad), px(y - rad), px(x + rad), px(y + rad)], fill=HAND)

    # Budget bar: five slices, three consumed.
    bar_y, bar_h, gap = 830, 76, 26
    total_w = 700
    seg_w = (total_w - gap * 4) / 5
    x0 = (SIZE - total_w) / 2
    for i in range(5):
        x = x0 + i * (seg_w + gap)
        color = ACCENT if i < 3 else ACCENT_DIM
        draw.rounded_rectangle(
            [px(x), px(bar_y), px(x + seg_w), px(bar_y + bar_h)],
            radius=px(bar_h / 2),
            fill=color,
        )
    return img


def with_rounded_corners(img):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S, S], radius=px(SIZE * 0.22), fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def main():
    out_dir = Path(__file__).resolve().parent.parent / ".github" / "assets"
    out_dir.mkdir(parents=True, exist_ok=True)
    big = with_rounded_corners(build())
    big.resize((SIZE, SIZE), Image.LANCZOS).save(out_dir / "logo.png", optimize=True)
    big.resize((512, 512), Image.LANCZOS).save(out_dir / "icon.png", optimize=True)


if __name__ == "__main__":
    main()
