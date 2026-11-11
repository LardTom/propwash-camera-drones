from . import common, decals, palette

import kit
import paint

CHARGE_COMPONENT = "propwash:battery_charge"
CHARGE_STEPS = 1000
LED_SUFFIX = "_led"


def dims(d):
    pack = d.get("pack_mm")
    if isinstance(pack, list) and len(pack) == 3:
        return float(pack[1]), float(pack[2]), float(pack[0])
    height = (float(d["mass_g"]) / 2.4 / 2.86) ** (1.0 / 3.0) * 10.0
    return 1.6 * height, height, 2.6 * height


def params(key, d, overrides=None):
    w, h, l = dims(d)
    cells = int(d.get("cells", 4))
    base = palette.fit(palette.accent(d, palette.SHELL["tour"]), "battery")
    p = dict(w=w, h=h, l=l, cells=cells, base=base, band=palette.band(cells), leds=4, lit=4,
             density=common.clamp(300.0 / max(w, h, l), 2.0, 6.0))
    if overrides:
        p.update(overrides)
    return p


def build(key, d, overrides=None):
    common.setup()
    p = params(key, d, overrides)
    w, h, l = p["w"], p["h"], p["l"]
    base = p["base"]
    shell = common.material(f"smart_{key}", paint.plastic(base, 0.02), "battery", "battery", base)
    trim = common.material(f"smart_trim_{key}", paint.plastic(palette.detail(base), 0.02), "battery", "battery", base)
    m = kit.Model("battery", key, density=p["density"], gui=(30, 225, 0))
    ch = common.clamp(0.12 * h, 1.2, 6.0)
    side = decals.stripe(p["band"], 0.22)
    m.box(-w / 2, 0.0, -l / 2, w / 2, h - ch, l / 2, shell, name="pack",
          decals={"north": decals.contacts(p["cells"] + 2), "south": decals.led_row(p["leds"], p["lit"]),
                  "east": side, "west": side})
    m.box(-w / 2 + ch, h - ch, -l / 2 + ch * 0.6, w / 2 - ch, h, l / 2 - ch * 0.6, shell, name="pack_top",
          decals={"up": decals.battery_top(base, p["leds"], p["lit"])})
    grip = max(1.0, 0.04 * l)
    for i in range(3):
        z = -l / 2 + ch * 0.6 + (i + 1) * grip * 2.2
        m.box(-w / 2 + ch * 1.6, h, z - grip / 2, w / 2 - ch * 1.6, h + 0.6, z + grip / 2, trim, name="grip",
              skip=("down",))
    lw = max(2.0, 0.1 * l)
    for sx in (-1, 1):
        x0 = sx * w / 2
        x1 = sx * (w / 2 + 0.6)
        m.box(min(x0, x1), h * 0.3, -l / 2 + 0.12 * l, max(x0, x1), h * 0.7, -l / 2 + 0.12 * l + lw, trim, name="latch",
              skip=("east",) if sx < 0 else ("west",))
    return m


def lit_at(step, leds):
    return (step * leds + CHARGE_STEPS - 1) // CHARGE_STEPS


def led_stages(key, d, overrides=None):
    leds = params(key, d, overrides)["leds"]
    stages = []
    for lit in range(leds):
        model = build(key, d, dict(overrides or {}, lit=lit))
        model.key = f"{key}{LED_SUFFIX}{lit}"
        stages.append(model)
    return stages


def charge_cases(stages):
    leds = len(stages)
    values = [[] for _ in range(leds)]
    for step in range(CHARGE_STEPS + 1):
        lit = lit_at(step, leds)
        if lit < leds:
            values[lit].append(round(step / CHARGE_STEPS, 3))
    return [(values[lit], stages[lit].model_id) for lit in range(leds)]


def build_with_stages(key, d, overrides=None):
    stages = led_stages(key, d, overrides)
    return build(key, d, overrides), stages, charge_cases(stages)
