from . import common, display, palette

import kit
import paint
from paint import hexc

LENSES = {
    "lens_24": dict(focal=24, d=30.0, l=24.0, ring="#8d949c"),
    "lens_50": dict(focal=50, d=32.0, l=38.0, ring="#c9ced5"),
    "lens_85": dict(focal=85, d=38.0, l=60.0, ring="#c9a24a"),
}


def grip(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#232428"))
    for x in range(w):
        if x % 2 == 0:
            region[:, x, :3] = hexc("#2f3136")
    if ctx.side == "side" and h >= 3:
        region[0, :, :3] = hexc("#3a3d42")


def register(key, p):
    paint.register("lens_body", paint.plastic("#1c1d20", 0.03))
    paint.register("lens_grip", grip)
    paint.register("lens_mount", paint.alu("#b9c0c8"))
    return paint.register(f"lens_ring_{key}", paint.alu(p["ring"]))


def build(key, overrides=None):
    common.setup()
    p = dict(LENSES[key])
    if overrides:
        p.update(overrides)
    ring = register(key, p)
    d, l = p["d"], p["l"]
    m = kit.Model("lens", key, density=common.clamp(360.0 / max(d, l), 5.0, 10.0), gui=display.gui("lens")[0],
                  gui_fill=display.gui("lens")[1])
    z0, z1 = -l / 2, l / 2
    m.cylinder_x(0.0, 0.0, z1 - 2.5, 5.0, d * 0.86, "lens_mount", end="lens_mount", along="z", name="mount", bevel=0.4)
    body_len = l - 5.0 - 3.0
    m.cylinder_x(0.0, 0.0, z0 + 3.0 + body_len / 2, body_len, d * 0.94, "lens_body", end="lens_body", along="z",
                 name="barrel", bevel=0.5)
    grip_len = max(4.0, 0.36 * l)
    m.cylinder_x(0.0, 0.0, z0 + 3.0 + body_len * 0.45, grip_len, d, "lens_grip", end="lens_body", along="z",
                 name="focus_ring", bevel=0.4, no_start=True, no_end=True)
    m.cylinder_x(0.0, 0.0, z0 + 1.5, 3.0, d, ring, end=ring, along="z", name="front_ring", bevel=0.4)
    g = d * 0.68
    m.box(-g / 2, -g / 2, z0 - 0.2, g / 2, g / 2, z0, "lens_glass", name="lens_glass", only=("north",))
    return m


def ids(key):
    return f"{kit.NAMESPACE}:item/{key}", f"{kit.NAMESPACE}:item/{key}"
