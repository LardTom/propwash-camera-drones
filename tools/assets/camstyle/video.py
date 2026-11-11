from . import common, palette

import kit
import paint
import video
from paint import hexc


def link_top(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#2b2e33"))
    paint.noise(region, ctx, 0.03)
    paint.edges(region, hexc("#43474e"), hexc("#18191c"))
    if w >= 8 and h >= 6:
        for y in range(2, h - 2, 2):
            paint.rect(region, 2, y, w - 2, y + 1, hexc("#24272b"))
        paint.px(region, w - 3, 1, hexc(palette.LED_GREEN))


def register():
    paint.register("cam_link_body", paint.alu("#2b2e33"))


def build(key, d, overrides=None):
    common.setup()
    register()
    width = float(d.get("camera_width_mm", 14.0))
    p = dict(width=width, density=10.0 if width <= 14.0 else 7.0)
    if overrides:
        p.update(overrides)
    w = p["width"]
    m = kit.Model("video", key, density=p["density"], gui=(20, 205, 0), gui_fill=15.0)
    h = 0.75 * w
    m.box(-w / 2, -h / 2, -0.45 * w, w / 2, h / 2, 0.35 * w, "cam_body", name="cam_body",
          faces={"north": "cam_bezel_dark"}, decals={"east": video.screw_decal, "west": video.screw_decal})
    lens_d = 0.55 * w
    lens_len = 0.25 * w
    front = -0.45 * w
    m.cylinder_x(0.0, 0.0, front - lens_len / 2, lens_len, lens_d, "lens_barrel", end="lens_barrel", along="z",
                 name="lens_barrel", bevel=0.4)
    m.box(-lens_d / 2, -lens_d / 2, front - lens_len - 0.25, lens_d / 2, lens_d / 2, front - lens_len, "lens_glass",
          name="lens_glass", only=("north",))
    bw, bh, bl = 1.5 * w, 0.28 * w, 1.7 * w
    z0 = 0.35 * w
    top = m.sheet("link_top", -bw / 2, z0, bw / 2, z0 + bl, link_top)
    m.box(-bw / 2, -h / 2, z0, bw / 2, -h / 2 + bh, z0 + bl, "cam_link_body", name="link", sheet_up=top)
    m.box(-0.12 * w, -h / 2 + bh, z0, 0.12 * w, -h / 2 + bh + 0.6, z0 + 0.3 * w, "coax", name="ribbon")
    for s in (-1, 1):
        x = s * (bw / 2 - 0.15 * w)
        video.antenna(m, x, -h / 2 + bh * 0.5, z0 + bl - 0.1 * w, (100, s * 18, 0), 0.9 * w, max(1.0, 0.09 * w),
                      stalk="antenna_black", tee=(0.45 * w, max(0.8, 0.07 * w), "antenna_white"))
    return m
