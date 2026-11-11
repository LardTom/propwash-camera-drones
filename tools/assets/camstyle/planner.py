import numpy as np

from . import common, palette

import kit
import paint
from paint import hexc

KEY = "flight_planner"

DISPLAY = {
    "thirdperson_righthand": {"rotation": [37, 0, 0], "translation": [-4.5, 2.8, 1.3], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"rotation": [37, 0, 0], "translation": [-4.5, 2.8, 1.3], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"rotation": [-20, -18, 0], "translation": [-1.5, 3.2, -1.5], "scale": [0.42, 0.42, 0.42]},
    "firstperson_lefthand": {"rotation": [-20, 18, 0], "translation": [1.5, 3.2, -1.5], "scale": [0.42, 0.42, 0.42]},
    "gui": {"rotation": [18, -16, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 1.5, 0], "scale": [0.28, 0.28, 0.28]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
    "on_shelf": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [-78, 180, 0], "translation": [0, 5.25, 0.5], "scale": [0.4, 0.4, 0.4]},
}


def mm(u):
    return (u - kit.ANCHOR) * kit.UNIT_MM


def screen(region, ctx):
    h, w = region.shape[:2]
    rng = np.random.default_rng(7)
    grass = [hexc("#3d6e3b"), hexc("#4b7f43"), hexc("#355f33")]
    for y in range(h):
        for x in range(w):
            region[y, x, :3] = grass[int(rng.integers(0, 3))]
    region[..., 3] = 255
    bar = max(2, h // 9)
    yy, xx = np.mgrid[0:h, 0:w]
    river = np.abs((yy - bar) - (0.55 * xx - w * 0.12)) < max(1.2, w * 0.03)
    region[river & (yy > bar), :3] = hexc("#3a6ea5")
    sand = np.abs((yy - bar) - (0.55 * xx - w * 0.12)) < max(2.2, w * 0.055)
    region[sand & ~river & (yy > bar), :3] = hexc("#b9a77a")
    for i in range(6):
        tx = int(rng.integers(2, max(3, w - 2)))
        ty = int(rng.integers(bar + 2, max(bar + 3, h - 2)))
        paint.rect(region, tx, ty, tx + 2, ty + 2, hexc("#24452a"))
    paint.rect(region, int(w * 0.62), int(h * 0.62), int(w * 0.8), int(h * 0.82), hexc("#8d949c"))
    paint.rect(region, int(w * 0.64), int(h * 0.65), int(w * 0.78), int(h * 0.79), hexc("#6b5640"))
    points = [(0.14, 0.72), (0.3, 0.38), (0.55, 0.3), (0.82, 0.45), (0.7, 0.88)]
    pts = [(int(w * x), int(h * y)) for x, y in points]
    line = hexc("#f2f4f6")
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for t in range(n + 1):
            paint.px(region, int(round(x0 + (x1 - x0) * t / n)), int(round(y0 + (y1 - y0) * t / n)), line)
    for i, (x, y) in enumerate(pts):
        colour = hexc("#ffbb00") if i == 0 else hexc(palette.ACCENT)
        paint.rect(region, x - 1, y - 1, x + 2, y + 2, hexc("#101215"))
        paint.px(region, x, y, colour)
        paint.px(region, x + 1, y, colour)
        paint.px(region, x, y + 1, colour)
        paint.px(region, x + 1, y + 1, colour)
    dx, dy = pts[2]
    dx += 3
    dy += 3
    for r, c in ((0, 1), (1, 0), (1, 1), (1, 2), (2, 1)):
        paint.px(region, dx + c, dy + r, hexc("#ffffff"))
    paint.rect(region, 0, 0, w, bar, hexc("#101215"))
    paint.rect(region, 1, 1, max(2, w // 6), bar - 1, hexc(palette.LED_GREEN))
    for i in range(4):
        x = w - 2 - (3 - i) * 2
        paint.rect(region, x, bar - 1 - (i + 1) * (bar - 1) // 4, x + 1, bar - 1, hexc("#e4e6ea"))
    paint.rect(region, int(w * 0.4), 1, int(w * 0.6), bar - 1, hexc(palette.ACCENT))


def body_back(region, ctx):
    h, w = region.shape[:2]
    if w >= 8 and h >= 6:
        paint.rect(region, int(w * 0.35), int(h * 0.35), int(w * 0.65), int(h * 0.65), hexc("#2a2d33"))
        paint.rect(region, int(w * 0.38), int(h * 0.45), int(w * 0.62), int(h * 0.55), hexc("#3a3d42"))


def button_row(region, ctx):
    h, w = region.shape[:2]
    if w < 8 or h < 2:
        return
    for i, colour in enumerate(("#5f6770", "#5f6770", "#ffbb00")):
        x = int(w * 0.38) + i * 3
        paint.px(region, x, h // 2, hexc(colour))
        paint.px(region, x + 1, h // 2, hexc(colour))


def register():
    paint.register("planner_body", paint.plastic("#2b2e33", 0.03))
    paint.register("planner_rubber", paint.plastic("#1b1c1f", 0.06))
    paint.register("planner_screen", screen)
    paint.register("planner_metal", paint.alu("#8c949e"))


def build():
    common.setup()
    register()
    m = kit.Model("item", KEY, density=2.0, gui=(18, -16, 0))
    m.box(mm(1.0), mm(3.0), mm(7.5), mm(15.0), mm(13.0), mm(8.5), "planner_body", name="housing",
          decals={"north": body_back, "south": button_row})
    m.box(mm(2.0), mm(4.2), mm(8.5), mm(14.0), mm(12.4), mm(8.55), "planner_screen", name="screen", only=("south",),
          lowres={"south": 2.0})
    for sx in (0.4, 14.6):
        m.box(mm(sx - 0.6), mm(4.5), mm(7.2), mm(sx + 0.6), mm(11.5), mm(8.8), "planner_rubber", name="grip")
    for cx in (1.0, 15.0):
        for cy in (3.0, 13.0):
            m.box(mm(cx - 0.6), mm(cy - 0.6), mm(7.3), mm(cx + 0.6), mm(cy + 0.6), mm(8.7), "planner_rubber", name="bumper")
    m.box(mm(6.0), mm(13.0), mm(7.7), mm(10.0), mm(13.6), mm(8.3), "planner_metal", name="clip")
    m.box(mm(12.4), mm(13.0), mm(7.8), mm(13.2), mm(15.0), mm(8.2), "planner_rubber", name="antenna")
    return m


def display_json():
    return {k: dict(v) for k, v in DISPLAY.items()}
