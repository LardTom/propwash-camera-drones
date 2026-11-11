import colorsys

import paintable

CHANNELS = dict(paintable.CHANNELS)

ACCENT = "#2ab3d4"

SHELL = {
    "compact": "#c9cdd2",
    "tour": "#5a6068",
    "cine": "#1b1d20",
}

CARBON = "#1b1d20"
MATTE = "#232428"
RUBBER = "#2b2c2e"
PCB = "#141619"
MOTOR_BASE = "#1f2023"
MOTOR_BASE_LARGE = "#2a2b2f"
MOTOR_CAP_DARK = "#1d1e21"
GLASS = "#0e1a26"
SENSOR = "#10151b"
LED_ON = "#e9fbff"
LED_GREEN = "#62e07a"
LED_OFF = "#3a4048"
CONTACT = "#d9a33a"
NAMEPLATE = "#e4e6ea"

BAND_BY_CELLS = {1: "#f2f2f2", 2: "#35b86b", 3: "#e8b623", 4: "#f07c1e", 6: "#2a6de0", 8: "#b8307e"}

PROP_MATERIAL_COLOURS = {
    "polycarbonate": "#e4e4ea",
    "glass_fiber_nylon": "#5c6068",
    "carbon_fiber_nylon": "#3a3c42",
    "carbon": "#2b2c31",
}


def rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def hexs(values):
    return "#%02x%02x%02x" % tuple(max(0, min(255, int(round(v)))) for v in values)


def valid(value):
    return isinstance(value, str) and value.startswith("#") and len(value) == 7


def accent(definition, default):
    visual = definition.get("visual") or {}
    value = visual.get("accent")
    return value if valid(value) else default


def fit(colour, channel):
    reference = CHANNELS[channel] - 2
    r, g, b = rgb(colour)
    top = max(r, g, b, 1)
    if top <= reference:
        return colour
    k = reference / top
    return hexs((r * k, g * k, b * k))


def mix(a, b, t):
    ra, rb = rgb(a), rgb(b)
    return hexs(tuple(x * (1 - t) + y * t for x, y in zip(ra, rb)))


def scale(colour, factor):
    return hexs(tuple(c * factor for c in rgb(colour)))


def hsv(colour):
    r, g, b = (c / 255.0 for c in rgb(colour))
    return colorsys.rgb_to_hsv(r, g, b)


def from_hsv(h, s, v):
    return hexs(tuple(c * 255.0 for c in colorsys.hsv_to_rgb(h % 1.0, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))))


def luminance(colour):
    r, g, b = rgb(colour)
    return 0.299 * r + 0.587 * g + 0.114 * b


def readable_on(colour):
    return "#15161a" if luminance(colour) > 150 else "#ffffff"


def detail(colour):
    return scale(colour, 0.72) if luminance(colour) > 60 else mix(colour, "#5d636b", 0.35)


def band(cells):
    if cells in BAND_BY_CELLS:
        return BAND_BY_CELLS[cells]
    return BAND_BY_CELLS[min(BAND_BY_CELLS, key=lambda c: abs(c - cells))]


def shell_class(definition):
    colour = accent(definition, SHELL["tour"]).lower()
    for name, value in SHELL.items():
        if value == colour:
            return name
    lum = luminance(colour)
    return "compact" if lum > 150 else "tour" if lum > 60 else "cine"
