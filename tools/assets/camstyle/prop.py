import math

from . import common, palette

import kit
import paint
import paintable
import props
from paint import hexc

DIAMETERS = [130.0, 178.0, 250.0, 380.0]
HUB_D = [13.0, 14.0, 17.0, 22.0]
HUB_H = [6.0, 6.5, 7.5, 9.0]
THICK = [1.6, 1.9, 2.2, 2.8]
CHORD_K = 0.78
TIP_FROM = 0.86
FOLD_GAP = 0.6


def material_of(definition):
    value = str(definition.get("material", "glass_fiber_nylon")).split(":")[-1]
    return value if value in palette.PROP_MATERIAL_COLOURS else "glass_fiber_nylon"


def params(key, definition, overrides=None):
    diameter = float(definition["diameter_mm"])
    blades = int(definition["blades"])
    pitch_in = float(definition.get("pitch_in", diameter / 25.4 * 0.5))
    template = props.PROPS["p7_2b"]
    radius = diameter / 2.0
    ratio = radius / template["radius"]
    pd = pitch_in * 25.4 / diameter
    steep = common.clamp(pd / 0.6, 0.6, 1.3)
    hub_d = common.interp(diameter, DIAMETERS, HUB_D, extrapolate="slope")
    hub_h = common.clamp(common.interp(diameter, DIAMETERS, HUB_H, extrapolate="slope"), 4.0, 10.0)
    grip = hub_d * 0.5 + max(4.0, 0.05 * diameter)
    segments = []
    for r0, r1, chord, angle in template["segments"]:
        c = chord * ratio * CHORD_K * (2.0 / max(2, blades)) ** 0.3
        a = math.degrees(math.atan(math.tan(math.radians(angle)) * steep))
        lo = max(r0 * ratio, grip)
        hi = r1 * ratio
        if hi <= lo:
            continue
        segments.append((round(lo, 3), round(hi, 3), round(c, 3), round(a, 2)))
    material = material_of(definition)
    colour = palette.accent(definition, palette.PROP_MATERIAL_COLOURS[material])
    thick = common.clamp(common.interp(diameter, DIAMETERS, THICK, extrapolate="slope"), 1.2, 3.2)
    p = dict(radius=radius, blades=blades, hub_d=round(hub_d, 2), hub_h=round(hub_h, 2), thick=round(thick, 3), color=colour,
             nut=None, segments=segments, sweep=[0, 3, 6, 9][:len(segments)], material=material, grip=round(grip, 2),
             tip=palette.ACCENT)
    if overrides:
        p.update(overrides)
    return p


def fold_hub_top(color):
    base = hexc(color)

    def painter(region, ctx):
        h, w = region.shape[:2]
        paint.fill(region, paint.shade(base, 1.08))
        cx, cy = w / 2, h / 2
        r = min(w, h) / 2
        paint.disc(region, cx, cy, r * 0.62, paint.shade(base, 0.82))
        paint.disc(region, cx, cy, r * 0.32, hexc("#b9c0c8"))
        paint.disc(region, cx, cy, r * 0.14, hexc("#4a4f57"))

    return painter


def blade_materials(key, p):
    blade = common.material(f"prop_{key}", paint.prop_material(p["color"]), "prop", "prop", p["color"])
    hub = common.material(f"prop_hub_{key}", paint.plastic(p["color"]), "prop", "prop", p["color"])
    tip = common.material("prop_tip_cam", paint.prop_material(p["tip"]))
    return blade, hub, tip


def build_hub(m, p, hub_mat):
    hd, hh = p["hub_d"], p["hub_h"]
    sheet = m.sheet("hub_top", -hd / 2, -hd / 2, hd / 2, hd / 2, fold_hub_top(p["color"]))
    m.cylinder(0, 0, 0.0, hh, hd, hub_mat, name="hub", sheet_up=sheet, bevel=max(0.4, hd * 0.06))
    grip = p["grip"]
    t = p["thick"]
    y_mid = hh * 0.5
    arm_w = max(3.0, hd * 0.42)
    for b in range(p["blades"]):
        yaw = 360.0 * b / p["blades"] + 15.0
        m.bar(0, 0, yaw, grip - hd * 0.3, arm_w, y_mid - t * 1.4, y_mid + t * 1.4, hub_mat, start=hd * 0.3, name="hub")
        c, s = kit.yaw_dir(yaw)
        hx, hz = (grip - FOLD_GAP) * c, (grip - FOLD_GAP) * s
        m.cylinder(hx, hz, y_mid + t * 1.4, y_mid + t * 1.4 + 0.8, max(1.6, arm_w * 0.55), "steel", name="screw", bevel=0.2)


def build_prop(key, ccw=False, damage=None):
    p = props.PROPS[key]
    suffix = ("_" + damage if damage else "") + ("_ccw" if ccw else "")
    density = 6.0 if p["radius"] < 40 else 3.6 if p["radius"] < 100 else 2.6
    m = kit.Model("prop", key + suffix, density=density, gui=(58, 225, 0), gui_fill=15.5)
    blade_mat, hub_mat, tip_mat = blade_materials(key, p)
    build_hub(m, p, hub_mat)
    y_mid = p["hub_h"] * 0.5
    spin = -1 if ccw else 1
    t = p["thick"]
    hub = p["hub_d"] * 0.5
    tip_from = p["radius"] * TIP_FROM
    for b in range(p["blades"]):
        base_yaw = 360.0 * b / p["blades"] + 15.0
        spec = dict(props.blade_damage(damage, b))
        if "bend_at" in spec:
            spec["bend_radius"] = hub + (p["radius"] - hub) * spec["bend_at"]
        pieces, marks = props.blade_pieces(p, spec, spin)
        for pc in pieces:
            yaw = base_yaw + spin * pc["sweep"]
            if pc["r0"] < tip_from < pc["r1"]:
                props.emit_piece(m, dict(pc, r1=tip_from), blade_mat, "blade", spin, yaw, spec, y_mid, t)
                props.emit_piece(m, dict(pc, r0=tip_from), tip_mat, "blade_tip", spin, yaw, spec, y_mid, t)
                continue
            mat = tip_mat if pc["r0"] >= tip_from - 1e-6 else blade_mat
            props.emit_piece(m, pc, mat, "blade_tip" if mat == tip_mat else "blade", spin, yaw, spec, y_mid, t)
        for mk in marks:
            props.emit_piece(m, mk, "prop_break", "break", spin, base_yaw + spin * mk["sweep"], spec, y_mid, t, pad=0.02)
    return m


def build(key, definition, overrides=None):
    common.setup()
    p = params(key, definition, overrides)
    props.PROPS[key] = p
    paintable.MATERIALS[("prop", f"prop_{key}")] = ("prop", p["color"])
    paintable.MATERIALS[("prop", f"prop_hub_{key}")] = ("prop", p["color"])
    cw = build_prop(key)
    ccw = build_prop(key, ccw=True)
    blur = props.build_blur(key, cw)
    damaged = [(build_prop(key, damage=d), build_prop(key, ccw=True, damage=d)) for d in props.DAMAGE]
    return cw, ccw, blur, damaged
