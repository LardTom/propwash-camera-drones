from . import common, palette

import kit
import paint
import stacks
from paint import hexc


def board_size(d):
    mount = str(d.get("mount", "propwash:s30"))
    if d.get("board_size_mm"):
        return float(d["board_size_mm"])
    return 26.0 if mount.endswith("whoop_aio") else 27.0 if mount.endswith("s20") else 36.0 if mount.endswith("s30") else 30.0


def patch_top(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#c9b48a"))
    paint.noise(region, ctx, 0.04)
    paint.edges(region, hexc("#e2d2ae"), hexc("#9a8762"))
    cx, cy = w / 2, h / 2
    s = min(w, h) * 0.3
    paint.rect(region, int(cx - s), int(cy - s), int(cx + s) + 1, int(cy + s) + 1, hexc("#d8dde3"))
    paint.px(region, int(cx + s * 0.4), int(cy), hexc("#6e747c"))


def shield_top(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#aeb5bd"))
    paint.noise(region, ctx, 0.03)
    paint.edges(region, hexc("#d3d8de"), hexc("#7d838b"))
    for x in range(2, w - 2, 3):
        paint.px(region, x, 2, hexc("#8a9099"))


def register():
    paint.register("nav_patch", paint.plastic("#c9b48a", 0.03))
    paint.register("nav_shield", paint.alu("#aeb5bd"))
    paint.register("nav_led", paint.glossy(palette.LED_GREEN))


def build(key, d, overrides=None):
    common.setup()
    register()
    size = board_size(d)
    half = size / 2
    hole = half - 2.75
    aio = bool(d.get("all_in_one", False))
    m = kit.Model("stack", key, density=5.5 if size >= 30 else 8.0, gui=(50, 225, 0))
    holes = [(sx * hole, sz * hole, 1.5) for sx in (-1, 1) for sz in (-1, 1)]
    for sx in (-1, 1):
        for sz in (-1, 1):
            stacks.grommet(m, sx * hole, sz * hole, 0.0, 1.2, min(4.2, size * 0.12))
    y = 1.2
    if not aio:
        esc = m.sheet("esc", -half, -half, half, half, stacks.pcb_sheet(-half, -half, half, half, holes=holes,
                                                                        pads=stacks.corner_pads(half, 1.6)))
        m.box(-half, y, -half, half, y + 1.4, half, "pcb", name="esc_pcb", sheet_up=esc)
        for sx in (-1, 1):
            for sz in (-1, 1):
                m.box(sx * half * 0.55 - 2.2, y + 1.4, sz * half * 0.4 - 1.8, sx * half * 0.55 + 2.2, y + 2.3,
                      sz * half * 0.4 + 1.8, "chip", name="fet")
                m.box(sx * hole - 1.6, y + 1.4, sz * hole - 1.6, sx * hole + 1.6, y + 3.4, sz * hole + 1.6, "black",
                      name="nylon_standoff", skip=("up", "down"))
        y += 3.4
    fc = m.sheet("fc", -half, -half, half, half, stacks.pcb_sheet(-half, -half, half, half, holes=holes,
                                                                   silk_box=[(-half * 0.8, -half * 0.8, half * 0.1, half * 0.1)]))
    m.box(-half, y, -half, half, y + 1.4, half, "pcb", name="fc_pcb", sheet_up=fc)
    y += 1.4
    sw = half * 0.85
    shield = m.sheet("shield", -sw, -sw, sw * 0.15, sw * 0.15, shield_top)
    m.box(-sw, y, -sw, sw * 0.15, y + 1.2, sw * 0.15, "nav_shield", name="shield", sheet_up=shield)
    pw = size * 0.3
    pz = half * 0.42
    patch = m.sheet("patch", half * 0.42 - pw / 2, pz - pw / 2, half * 0.42 + pw / 2, pz + pw / 2, patch_top)
    m.box(half * 0.42 - pw / 2, y, pz - pw / 2, half * 0.42 + pw / 2, y + 2.2, pz + pw / 2, "nav_patch", name="gps_patch",
          sheet_up=patch)
    m.box(-half * 0.6, y, half * 0.35, -half * 0.6 + 3.0, y + 0.9, half * 0.35 + 3.0, "chip", name="compass")
    m.box(half * 0.75 - 0.8, y, -half * 0.75 - 0.8, half * 0.75 + 0.8, y + 0.7, -half * 0.75 + 0.8, "nav_led", name="status_led")
    m.box(-half * 0.3, y - 1.4, -half - 2.2, half * 0.3, y - 0.2, -half, "dark_grey", name="sensor_port",
          only=("north", "up", "east", "west"))
    return m
