import numpy as np

from . import common, decals, palette

import kit
import paint
from paint import hexc

HEAD = "gimbal_head"


def size_of(d):
    size = d.get("size_mm")
    if isinstance(size, list) and len(size) == 3:
        return float(size[0]), float(size[1]), float(size[2])
    return 40.0, 40.0, 40.0


def params(key, d, overrides=None):
    w, h, depth = size_of(d)
    colour = palette.fit(palette.accent(d, palette.SHELL["tour"]), "accessory")
    cine = max(w, h, depth) >= 80.0
    p = dict(w=w, h=h, d=depth, colour=colour, cine=cine, density=common.clamp(330.0 / max(w, h, depth), 3.0, 10.0),
             lens=0.78 if cine else 0.62)
    if overrides:
        p.update(overrides)
    return p


def lens_face(colour, size):
    ring = hexc("#8d949c")

    def decal(region, ctx):
        h, w = region.shape[:2]
        cx, cy = w / 2, h / 2
        r = min(w, h) * size / 2
        paint.edges(region, hexc(palette.mix(colour, "#ffffff", 0.12)), hexc(palette.detail(colour)))
        if r < 1.2:
            paint.px(region, int(cx), int(cy), hexc(palette.GLASS))
            return
        paint.disc(region, cx, cy, r + 0.8, ring)
        paint.disc(region, cx, cy, r, hexc("#141518"))
        paint.disc(region, cx, cy, r * 0.82, hexc(palette.GLASS))
        paint.disc(region, cx, cy, r * 0.5, hexc("#0a1420"))
        paint.disc(region, cx - r * 0.35, cy - r * 0.35, max(0.6, r * 0.16), hexc("#d8efff"))
        if r >= 3.0:
            paint.disc(region, cx + r * 0.3, cy + r * 0.28, max(0.5, r * 0.08), hexc("#6f93c0"))

    return decals.named(decal, "lens_face", colour, size)


def bay_door(colour):
    dark = hexc(palette.detail(colour))

    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 8 or h < 6:
            return
        x0, x1 = int(w * 0.2), int(w * 0.8)
        y0, y1 = int(h * 0.25), int(h * 0.8)
        paint.rect(region, x0, y0, x1, y0 + 1, dark)
        paint.rect(region, x0, y1 - 1, x1, y1, dark)
        paint.rect(region, x0, y0, x0 + 1, y1, dark)
        paint.rect(region, x1 - 1, y0, x1, y1, dark)
        paint.rect(region, (x0 + x1) // 2 - 1, y0 + 2, (x0 + x1) // 2 + 1, y0 + 3, hexc(palette.ACCENT))

    return decals.named(decal, "bay_door", colour)


def materials(key, p):
    colour = p["colour"]
    body = common.material(f"gimbal_{key}", paint.plastic(colour, 0.025), "accessory", "accessory", colour)
    face = common.material(f"gimbal_face_{key}", paint.plastic(palette.mix(colour, "#15161a", 0.55), 0.02))
    paint.register("gimbal_motor", paint.alu("#3a3d42"))
    paint.register("gimbal_plate", paint.plastic("#1d1e21", 0.04))
    lock = common.material("cam_lock", paint.plastic(palette.ACCENT, 0.03))
    return body, face, lock


def head_pose(p):
    w, h, d = p["w"], p["h"], p["d"]
    return 0.0, -0.6 * h, -0.6 * d


def build(key, d, overrides=None):
    common.setup()
    p = params(key, d, overrides)
    body, face, lock = materials(key, p)
    w, h, dd = p["w"], p["h"], p["d"]
    m = kit.Model("accessory", key, density=p["density"], gui=(20, 205, 0), gui_fill=15.0)
    m.box(-0.34 * w, -0.045 * h, -0.46 * dd, 0.34 * w, 0.0, -0.04 * dd, "gimbal_plate", name="mount_plate")
    for sx in (-1, 1):
        for z in (-0.12 * dd, -0.38 * dd):
            m.cylinder(sx * 0.26 * w, z, -0.14 * h, -0.045 * h, max(1.2, 0.1 * w), "rubber", name="damper", bevel=0.2)
    m.box(-0.32 * w, -0.19 * h, -0.48 * dd, 0.32 * w, -0.14 * h, -0.06 * dd, body, name="bracket")
    _, yc, zc = head_pose(p)
    hw, hh, hd = 0.52 * w, 0.42 * h, 0.42 * dd
    zr = zc + hd / 2 + 0.11 * dd
    yaw_d = 0.3 * w
    m.cylinder(0.0, zr, -0.3 * h, -0.19 * h, yaw_d, "gimbal_motor", name="yaw_motor", bevel=0.3)
    m.cylinder(0.0, zr, -0.26 * h, -0.22 * h, yaw_d * 1.08, lock, name="gimbal_lock", bevel=0.2,
               no_top=True, no_bottom=True)
    roll_d = 0.26 * min(w, h)
    m.box(-0.07 * w, yc + roll_d * 0.4, zr - 0.05 * dd, 0.07 * w, -0.3 * h, zr + 0.05 * dd, body, name="yaw_arm")
    m.cylinder_x(0.0, yc, zr, 0.14 * dd, roll_d, "gimbal_motor", name="roll_motor", along="z")
    back = zc + hd / 2
    arm_z0 = back + 0.01 * dd
    arm_z1 = zr - 0.07 * dd
    if arm_z1 - arm_z0 < 0.8:
        arm_z1 = arm_z0 + 0.8
    sides = (-1, 1) if p["cine"] else (1,)
    x_out = 0.44 * w
    m.box(-x_out if p["cine"] else -0.06 * w, yc - 0.07 * h, arm_z0, x_out, yc + 0.07 * h, arm_z1, body, name="roll_arm")
    for sx in sides:
        x0, x1 = sx * 0.37 * w, sx * x_out
        m.box(min(x0, x1), yc - 0.09 * h, zc - 0.06 * dd, max(x0, x1), yc + 0.09 * h, arm_z1, body, name="tilt_arm")
    m.cylinder_x(0.32 * w, yc, zc, 0.1 * w, 0.3 * min(h, dd), "gimbal_motor", name="tilt_motor")
    if p["cine"]:
        m.cylinder_x(-0.32 * w, yc, zc, 0.1 * w, 0.2 * min(h, dd), "gimbal_motor", name="tilt_bearing")
    head_decals = {"north": lens_face(p["colour"], p["lens"])}
    if p["cine"]:
        head_decals["west"] = bay_door(p["colour"])
    m.box(-hw / 2, yc - hh / 2, zc - hd / 2, hw / 2, yc + hh / 2, zc + hd / 2, body, name=HEAD,
          faces={"north": face}, decals=head_decals)
    return m


def head_element(model):
    found = [el for el in model.elements if el.name == HEAD]
    return found


def pivot_of(model):
    found = head_element(model)
    if len(found) != 1:
        return None
    el = found[0]
    frm, to = np.array(el.frm, dtype=float), np.array(el.to, dtype=float)
    return ((frm + to) / 2.0 - kit.ANCHOR) * kit.UNIT_MM
