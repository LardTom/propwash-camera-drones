import numpy as np

from . import palette

import paint
from paint import hexc

PLATE_TEXT = "LARDTOM"


def named(fn, *parts):
    fn.__name__ = "_".join(str(p) for p in parts)
    return fn


def eye(region, cx, cy, r, ring="#2a2e34"):
    if r < 0.6:
        paint.px(region, int(cx), int(cy), hexc(palette.SENSOR))
        return
    paint.disc(region, cx, cy, r + 0.6, hexc(ring))
    paint.disc(region, cx, cy, r, hexc(palette.SENSOR))
    if r >= 1.4:
        paint.disc(region, cx - r * 0.35, cy - r * 0.35, max(0.5, r * 0.28), hexc("#5d7a99"))


def sensor_pair(spread=0.5, size=0.2, light=None):
    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 4 or h < 3:
            return
        r = max(0.6, min(w, h) * size)
        cy = h * 0.5
        for sx in (-1, 1):
            eye(region, w * 0.5 + sx * w * spread * 0.5, cy, r)
        if light is not None and w >= 8:
            paint.rect(region, int(w * 0.5) - 1, int(cy) - 1, int(w * 0.5) + 1, int(cy) + 1, hexc(light))

    return named(decal, "sensor_pair", spread, size, light)


def vents(colour, count=4):
    dark = hexc(palette.detail(colour))

    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 8 or h < 4:
            return
        x0, x1 = int(w * 0.3), int(w * 0.7)
        step = max(2, (x1 - x0) // count)
        for i in range(count):
            x = x0 + i * step
            paint.rect(region, x, int(h * 0.3), x + 1, int(h * 0.75), dark)

    return named(decal, "vents", colour, count)


def plate(colour, sensors=True):
    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 6 or h < 4:
            return
        if sensors:
            r = max(0.6, min(w, h) * 0.16)
            for sx in (-1, 1):
                eye(region, w * 0.5 + sx * w * 0.3, h * 0.42, r)
        tw = paint.text_width(PLATE_TEXT)
        if w >= tw + 6 and h >= 14:
            y = int(h * 0.68)
            paint.rect(region, (w - tw) // 2 - 2, y - 2, (w + tw) // 2 + 2, y + 7, hexc("#2a2d33"))
            paint.text(region, (w - tw) // 2, y, PLATE_TEXT, hexc(palette.NAMEPLATE))
        else:
            paint.px(region, w // 2, int(h * 0.75), hexc(palette.LED_GREEN))

    return named(decal, "plate", colour, sensors)


def deck(colour, mark=True):
    seam = hexc(palette.detail(colour))

    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 6 or h < 6:
            return
        paint.rect(region, 1, int(h * 0.5), w - 1, int(h * 0.5) + 1, seam)
        if mark:
            cx, cy = w * 0.5, h * 0.8
            r = max(1.0, min(w, h) * 0.1)
            paint.disc(region, cx, cy, r + 0.7, seam)
            paint.disc(region, cx, cy, r, hexc(palette.mix(colour, "#ffffff", 0.18)))

    return named(decal, "deck", colour, mark)


def pod_ring(ring_colour):
    ring = hexc(ring_colour)

    def painter(region, ctx):
        h, w = region.shape[:2]
        cx, cy = w / 2, h / 2
        r = min(w, h) / 2
        yy, xx = np.mgrid[0:h, 0:w]
        d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
        paint.fill(region, hexc("#1f2023"))
        paint.noise(region, ctx, 0.03)
        region[(d > r * 0.78) & (d <= r), :3] = ring

    return named(painter, "pod_ring", ring_colour)


def led_row(count, lit, on=palette.LED_ON, off=palette.LED_OFF, button=True):
    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 6 or h < 3:
            return
        span = w * (0.5 if button else 0.7)
        x0 = w * 0.5 - span * 0.5 + (w * 0.08 if button else 0.0)
        cy = h * 0.55
        r = max(0.5, min(h * 0.12, span / (count * 3.0)))
        for i in range(count):
            cx = x0 + span * (i + 0.5) / count
            colour = on if i < lit else off
            if r < 0.8:
                paint.px(region, int(cx), int(cy), hexc(colour))
            else:
                paint.disc(region, cx, cy, r, hexc(colour))
        if button:
            bx = x0 - w * 0.12
            paint.disc(region, bx, cy, max(0.8, r * 1.5), hexc("#2a2d33"))
            paint.disc(region, bx, cy, max(0.5, r * 0.9), hexc("#9aa1aa"))

    return named(decal, "led_row", count, lit, on, off, button)


def contacts(count=6):
    gold = hexc(palette.CONTACT)

    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 6 or h < 3:
            return
        paint.rect(region, int(w * 0.2), int(h * 0.3), int(w * 0.8), int(h * 0.75), hexc("#121316"))
        n = max(2, min(count, (int(w * 0.6)) // 2))
        for i in range(n):
            x = int(w * 0.22 + i * (w * 0.56) / n)
            paint.px(region, x, int(h * 0.5), gold)

    return named(decal, "contacts", count)


def latch(colour):
    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 4 or h < 3:
            return
        paint.rect(region, int(w * 0.12), int(h * 0.3), int(w * 0.26) + 1, int(h * 0.7) + 1, hexc(colour))

    return named(decal, "latch", colour)


def stripe(colour, at=0.18):
    def decal(region, ctx):
        h, w = region.shape[:2]
        if h < 3:
            return
        y = int(h * at)
        paint.rect(region, 0, y, w, y + max(1, h // 10), hexc(colour))

    return named(decal, "stripe", colour, at)


def battery_top(colour, count, lit):
    seam = hexc(palette.detail(colour))
    leds = led_row(count, lit, button=True)

    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 6 or h < 8:
            return
        paint.rect(region, 1, int(h * 0.12), w - 1, int(h * 0.12) + 1, seam)
        strip = region[int(h * 0.82):h - 1, :]
        if strip.shape[0] >= 2:
            leds(strip, ctx)

    return named(decal, "battery_top", colour, count, lit)
