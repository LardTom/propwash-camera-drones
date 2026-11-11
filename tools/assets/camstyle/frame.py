import math

from . import common, decals, palette

import kit
import paint

DENSITY_WB = [250.0, 380.0, 695.0]
DENSITY = [3.2, 2.6, 1.6]


def motors_of(d):
    arms = d.get("arms")
    order = ("front_left", "front_right", "rear_left", "rear_right")
    if isinstance(arms, dict) and all(k in arms for k in order):
        return [[float(v) for v in arms[k]["pos"]] for k in order]
    wb = float(d["wheelbase_mm"])
    s = wb / (2.0 * math.sqrt(2.0))
    y = 3.0 if wb >= 200 else 2.5 if wb >= 100 else 1.5
    return [[-s, y, -s], [s, y, -s], [-s, y, s], [s, y, s]]


def anchor(d, mount, default):
    anchors = d.get("accessory_anchors") or {}
    value = anchors.get(mount) or {}
    return common.vec(value.get("pos"), default)


def chamfer(h):
    return common.clamp(0.14 * h, 1.5, 9.0)


def nose_lift(z, nose, rear, chin):
    nl = 0.2 * (rear - nose)
    if z >= nose + nl or (chin < nose - 1.0 and chin <= z <= nose + nl * 0.35):
        return 0.0
    return 0.5 if z >= nose + nl * 0.35 else 1.0


def hull_bottom(top, underside, lift):
    for ch in (9.0, 1.5):
        bottom = underside - lift * ch
        if chamfer(top - bottom) == ch:
            return bottom
    return (underside - 0.14 * lift * top) / (1.0 - 0.14 * lift)


def params(key, d, overrides=None):
    wb = float(d["wheelbase_mm"])
    shell = palette.accent(d, palette.SHELL["tour"])
    kind = palette.shell_class(d)
    battery = d.get("battery") or {}
    camera = d.get("camera") or {}
    cam = common.vec(camera.get("pos"), [0.0, float(d.get("electronics_mm", 0.05 * wb)), -0.3 * wb])
    front = anchor(d, "front", [0.0, -0.05 * wb, -0.3 * wb])
    top = float(battery.get("plate_mm", 0.03 * wb))
    nose = cam[2] + 2.0
    rear = 0.33 * wb
    chin = front[2] - 0.05 * wb
    bottom = min(hull_bottom(top, front[1], nose_lift(front[2], nose, rear, chin)), -0.03 * wb)
    width = common.clamp(0.24 * wb, 52.0, 150.0)
    cine = d.get("role") == "cinelifter" or kind == "cine"
    p = dict(wb=wb, kind=kind, shell=palette.fit(shell, "frame"), motors=motors_of(d), top=top, bottom=bottom,
             width=width, nose=nose, rear=rear, chin=chin, front=front, cam=cam,
             arm_w=common.clamp(0.045 * wb, 8.0, 30.0), pod_d=common.clamp(0.072 * wb, 16.0, 54.0),
             feet=0.0 if cine else 0.1 * wb, legs=cine, leg_drop=145.0 if cine else 0.0,
             sensors_front=kind != "compact", density=common.interp(wb, DENSITY_WB, DENSITY, extrapolate="hold"))
    if overrides:
        p.update(overrides)
    return p


def materials(key, p):
    shell = p["shell"]
    body = common.material(f"shell_{key}", paint.plastic(shell, 0.025), "frame", "frame", shell)
    seam = common.material(f"shell_seam_{key}", paint.plastic(palette.detail(shell), 0.02), "frame", "frame", shell)
    accent = palette.fit(palette.ACCENT, "tpu")
    trim = common.material("cam_trim", paint.plastic(accent, 0.03), "frame", "tpu", accent)
    return body, seam, trim


def bar_between(m, a, b, width, y0, y1, material, name, **kw):
    dx, dz = b[0] - a[0], b[1] - a[1]
    length = math.hypot(dx, dz)
    yaw = math.degrees(math.atan2(-dz, dx))
    return m.bar(a[0], a[1], round(yaw, 3), length, width, y0, y1, material, name=name, **kw)


def hull(m, p, body, seam):
    w2 = p["width"] / 2
    top, bottom = p["top"], p["bottom"]
    h = top - bottom
    ch = chamfer(h)
    nose, rear = p["nose"], p["rear"]
    length = rear - nose
    nl = 0.2 * length
    tl = 0.1 * length
    shell = p["shell"]
    side_vents = decals.vents(shell, 4)
    m.box(-w2, bottom + ch, nose + nl, w2, top - ch, rear - tl, body, name="shell",
          decals={"east": side_vents, "west": side_vents}, skip=("north", "south"), lowres={"east": 0.6, "west": 0.6})
    m.box(-w2 + ch, top - ch, nose + nl - ch * 0.5, w2 - ch, top, rear - tl, body, name="shell_deck",
          decals={"up": decals.deck(shell)}, lowres={"up": 0.7, "east": 0.6, "west": 0.6})
    m.box(-w2 + ch, bottom, nose + nl, w2 - ch, bottom + ch, rear - tl, body, name="shell_belly",
          decals={"down": decals.sensor_pair(0.42, 0.14, palette.LED_ON)}, lowres={"down": 0.4, "east": 0.6, "west": 0.6})
    nw = 0.8 * w2
    front_eyes = {"north": decals.sensor_pair(0.55, 0.16)} if p["sensors_front"] else None
    m.box(-nw, bottom + ch * 0.5, nose + nl * 0.35, nw, top - ch * 1.2, nose + nl, body, name="shell_nose",
          skip=("south",), decals=front_eyes)
    tw = 0.62 * w2
    m.box(-tw, bottom + ch, nose, tw, top - ch * 2.0, nose + nl * 0.35, body, name="shell_nose", skip=("south",))
    rw = 0.84 * w2
    m.box(-rw, bottom + ch * 0.5, rear - tl, rw, top - ch * 0.6, rear, body, name="shell_tail",
          decals={"south": decals.plate(shell, p["sensors_front"])}, skip=("north",))
    m.box(-w2 - 0.2, bottom + ch * 0.4, nose + nl + 0.25 * length, w2 + 0.2, bottom + ch * 0.4 + max(0.6, h * 0.04),
          rear - tl - 0.15 * length, seam, name="shell_seam", only=("east", "west"))
    if p["chin"] < nose - 1.0:
        m.box(-tw, bottom, p["chin"], tw, bottom + max(1.5, 0.12 * h), nose + nl * 0.35, seam, name="shell_chin",
              skip=("south",))
    rail_z0 = -0.12 * p["wb"]
    rail_z1 = 0.2 * p["wb"]
    for sx in (-1, 1):
        x0 = sx * (w2 - ch - 0.5)
        x1 = sx * (w2 - ch - 0.5 - max(1.5, 0.03 * p["width"]))
        m.box(min(x0, x1), top, rail_z0, max(x0, x1), top + 2.0, rail_z1, seam, name="battery_rail", skip=("down",))


def arms(m, p, body, seam, trim):
    w2 = p["width"] / 2
    aw = p["arm_w"]
    at = 0.62 * aw
    motors = p["motors"]
    motor_y = sum(v[1] for v in motors) / len(motors)
    y1 = motor_y - 0.4
    y0 = y1 - at
    nose, rear = p["nose"], p["rear"]
    length = rear - nose
    hinge_front = nose + 0.32 * length
    hinge_rear = rear - 0.22 * length
    pod_d = p["pod_d"]
    pod_h = 0.7 * pod_d
    ring = decals.pod_ring(palette.ACCENT)
    for i, (mx, my, mz) in enumerate(motors):
        sx = -1 if mx < 0 else 1
        hz = hinge_front if mz < 0 else hinge_rear
        hx = sx * (w2 - aw * 0.3)
        bar_between(m, (hx, hz), (mx, mz), aw, y0, y1, body, "arm", skip=("west",))
        m.cylinder(hx, hz, y0 - 1.0, y1 + 1.0, aw * 1.3, seam, name="arm_hinge")
        sheet = m.sheet(f"pod{i}", mx - pod_d / 2, mz - pod_d / 2, mx + pod_d / 2, mz + pod_d / 2, ring)
        m.cylinder(mx, mz, my - pod_h, my - 0.05, pod_d, body, name="motor_pod", sheet_up=sheet)
        if mz < 0:
            m.box(mx - pod_d * 0.18, my - pod_h * 0.55, mz - pod_d / 2 - 0.6, mx + pod_d * 0.18, my - pod_h * 0.35,
                  mz - pod_d / 2, trim, name="arm_light", only=("north", "up", "down", "east", "west"))
        if p["feet"] > 0:
            fw = max(3.0, pod_d * 0.32)
            fy = my - pod_h - p["feet"]
            m.box(mx - fw / 2, fy + 2.0, mz - fw / 2, mx + fw / 2, my - pod_h + 0.5, mz + fw / 2, seam, name="foot")
            m.box(mx - fw / 2 - 0.4, fy, mz - fw / 2 - 0.4, mx + fw / 2 + 0.4, fy + 2.0, mz + fw / 2 + 0.4, "rubber",
                  name="foot_pad")


def landing_legs(m, p, body, seam, trim):
    if not p["legs"]:
        return
    w2 = p["width"] / 2
    bottom = p["bottom"]
    ground = bottom - p["leg_drop"]
    strut = max(6.0, 0.024 * p["wb"])
    length = p["rear"] - p["nose"]
    for sx in (-1, 1):
        x = sx * w2 * 0.62
        m.box(x - strut / 2, ground + strut, -strut / 2, x + strut / 2, bottom + 2.0, strut / 2, "carbon", name="leg",
              rot=(0, 0, sx * 14.0), origin=(x, bottom, 0.0))
        foot_x = x + sx * math.tan(math.radians(14.0)) * (bottom - ground - strut)
        m.cylinder_x(foot_x, ground + strut / 2, 0.0, 0.42 * length, strut, "carbon", end=trim, name="leg_skid", along="z")
        m.box(x - strut * 0.9, bottom - strut * 0.6, -strut * 1.4, x + strut * 0.9, bottom + 0.5, strut * 1.4, seam,
              name="leg_mount")


def build(key, d, overrides=None):
    common.setup()
    p = params(key, d, overrides)
    body, seam, trim = materials(key, p)
    m = kit.Model("frame", key, density=p["density"], gui=(40, 200, 0), gui_fill=15.0)
    hull(m, p, body, seam)
    arms(m, p, body, seam, trim)
    landing_legs(m, p, body, seam, trim)
    return m
