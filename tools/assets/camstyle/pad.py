import numpy as np

from . import palette

import paint
from paint import hexc

KEY = "landing_pad"
SIZE = 32
HEIGHT = 2.0


class _Ctx:
    def __init__(self, w, h, side):
        self.w = w
        self.h = h
        self.side = side
        self.rng = np.random.default_rng(11)
        self.mm_per_px = 1.0


def blank():
    return np.zeros((SIZE, SIZE, 4), dtype=np.float64)


def top():
    region = blank()
    paint.fill(region, hexc("#2e3033"))
    paint.noise(region, _Ctx(SIZE, SIZE, "top"), 0.04)
    paint.edges(region, hexc("#4a4d52"), hexc("#1d1e21"))
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    d = np.hypot(xx + 0.5 - SIZE / 2, yy + 0.5 - SIZE / 2)
    region[(d >= 11.0) & (d < 12.6), :3] = hexc("#e4e6ea")
    white = hexc("#f2f4f6")
    paint.rect(region, 11, 9, 14, 23, white)
    paint.rect(region, 18, 9, 21, 23, white)
    paint.rect(region, 14, 15, 18, 17, white)
    accent = hexc(palette.ACCENT)
    for cx, cy, sx, sy in ((2, 2, 1, 1), (29, 2, -1, 1), (2, 29, 1, -1), (29, 29, -1, -1)):
        for i in range(4):
            for j in range(4 - i):
                paint.px(region, cx + sx * i, cy + sy * j, accent)
    return region


def side():
    region = blank()
    paint.fill(region, hexc("#232428"))
    paint.noise(region, _Ctx(SIZE, SIZE, "side"), 0.04)
    for x in range(SIZE):
        if (x // 4) % 2 == 0:
            region[0:2, x, :3] = hexc(palette.ACCENT)
    region[2, :, :3] = hexc("#3a3d42")
    region[3, :, :3] = hexc("#1b1c1f")
    return region


def bottom():
    region = blank()
    paint.fill(region, hexc("#1d1e21"))
    paint.noise(region, _Ctx(SIZE, SIZE, "bottom"), 0.05)
    return region


def textures():
    out = {}
    for name, painter in (("top", top), ("side", side), ("bottom", bottom)):
        img = np.clip(painter(), 0, 255)
        img[..., 3] = 255
        out[name] = img.astype(np.uint8)
    return out


def model_json(namespace):
    tex = {name: f"{namespace}:block/{KEY}_{name}" for name in ("top", "side", "bottom")}
    tex["particle"] = tex["side"]
    side_uv = [0, 0, 16, HEIGHT]
    return {
        "parent": "minecraft:block/block",
        "textures": tex,
        "elements": [{
            "name": "pad",
            "from": [0, 0, 0],
            "to": [16, HEIGHT, 16],
            "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "north": {"uv": side_uv, "texture": "#side", "cullface": "north"},
                "south": {"uv": side_uv, "texture": "#side", "cullface": "south"},
                "west": {"uv": side_uv, "texture": "#side", "cullface": "west"},
                "east": {"uv": side_uv, "texture": "#side", "cullface": "east"},
            },
        }],
    }


def item_json(namespace):
    return {"model": {"type": "minecraft:model", "model": f"{namespace}:block/{KEY}"}}
