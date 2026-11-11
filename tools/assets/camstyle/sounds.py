import io
import struct

import numpy as np

RATE = 48000
SERIAL = 0x63616d64
FOLDER = "audio"

EVENTS = [
    ("planner.click", "planner_click", dict(preload=True)),
    ("planner.waypoint", "planner_waypoint", dict(preload=True)),
    ("mission.start", "mission_start", dict(preload=True)),
    ("mission.done", "mission_done", dict(preload=True)),
    ("gimbal.calibrate", "gimbal_calibrate", dict(attenuation_distance=12)),
    ("warning.obstacle", "warning_obstacle", dict(preload=True)),
    ("warning.limit", "warning_limit", dict(preload=True)),
    ("crew.request", "crew_request", dict(preload=True)),
]


def t_axis(seconds):
    return np.arange(int(round(RATE * seconds))) / RATE


def envelope(n, attack=0.004, release=0.04):
    env = np.ones(n)
    a = max(1, int(RATE * attack))
    r = max(1, int(RATE * release))
    env[:a] = np.linspace(0.0, 1.0, a)
    env[-r:] = np.minimum(env[-r:], np.linspace(1.0, 0.0, r))
    return env


def tone(freq, seconds, level=0.5, harmonics=(1.0, 0.25, 0.08), attack=0.004, release=0.04, glide=None):
    t = t_axis(seconds)
    if glide is None:
        phase = 2 * np.pi * freq * t
    else:
        f = np.linspace(freq, glide, len(t))
        phase = 2 * np.pi * np.cumsum(f) / RATE
    wave = sum(a * np.sin((i + 1) * phase) for i, a in enumerate(harmonics))
    wave = wave / max(1e-9, sum(harmonics))
    return level * wave * envelope(len(t), attack, release)


def silence(seconds):
    return np.zeros(int(round(RATE * seconds)))


def chain(*parts):
    return np.concatenate(parts)


def planner_click():
    rng = np.random.default_rng(101)
    n = int(RATE * 0.035)
    noise = rng.normal(0.0, 1.0, n) * np.exp(-np.arange(n) / (RATE * 0.003))
    click = 0.35 * noise + tone(3200.0, 0.035, 0.25, (1.0,), 0.0005, 0.03)
    return click


def planner_waypoint():
    return chain(tone(1175.0, 0.07, 0.4), silence(0.015), tone(1568.0, 0.11, 0.4))


def mission_start():
    return chain(tone(1047.0, 0.11, 0.42), silence(0.04), tone(1319.0, 0.11, 0.42), silence(0.04),
                 tone(1568.0, 0.2, 0.45, release=0.09))


def mission_done():
    return chain(tone(1568.0, 0.1, 0.4), silence(0.03), tone(1319.0, 0.1, 0.4), silence(0.03),
                 tone(2093.0, 0.28, 0.42, release=0.16))


def gimbal_calibrate():
    rng = np.random.default_rng(202)
    out = []
    for step, (f0, f1, sec) in enumerate(((220.0, 340.0, 0.18), (340.0, 260.0, 0.14), (260.0, 420.0, 0.2),
                                          (420.0, 300.0, 0.16))):
        t = t_axis(sec)
        f = np.linspace(f0, f1, len(t))
        phase = 2 * np.pi * np.cumsum(f) / RATE
        buzz = np.sign(np.sin(phase)) * 0.25 + np.sin(2 * phase) * 0.2 + np.sin(5 * phase) * 0.08
        hiss = rng.normal(0.0, 0.03, len(t))
        out.append(0.45 * (buzz + hiss) * envelope(len(t), 0.01, 0.03))
        out.append(silence(0.025))
    return np.concatenate(out)


def warning_obstacle():
    return tone(2350.0, 0.075, 0.48, (1.0, 0.3), 0.002, 0.02)


def warning_limit():
    return chain(tone(880.0, 0.12, 0.45, (1.0, 0.35, 0.1)), silence(0.03), tone(698.0, 0.16, 0.45, (1.0, 0.35, 0.1)))


def crew_request():
    return chain(tone(1245.0, 0.09, 0.38, release=0.06), silence(0.02), tone(1865.0, 0.32, 0.38, release=0.25))


def samples(name):
    data = globals()[name]()
    peak = float(np.max(np.abs(data))) or 1.0
    if peak > 0.95:
        data = data * (0.95 / peak)
    return data.astype(np.float32)


CRC_TABLE = []
for _i in range(256):
    _r = _i << 24
    for _ in range(8):
        _r = ((_r << 1) ^ 0x04C11DB7) if _r & 0x80000000 else (_r << 1)
    CRC_TABLE.append(_r & 0xFFFFFFFF)


def ogg_crc(data):
    crc = 0
    for b in data:
        crc = ((crc << 8) & 0xFFFFFFFF) ^ CRC_TABLE[((crc >> 24) & 0xFF) ^ b]
    return crc


def fix_serial(raw, serial=SERIAL):
    out = bytearray(raw)
    pos = 0
    while pos < len(out):
        if out[pos:pos + 4] != b"OggS":
            raise ValueError("kein Ogg-Seitenkopf bei %d" % pos)
        segments = out[pos + 26]
        table = out[pos + 27:pos + 27 + segments]
        length = 27 + segments + sum(table)
        struct.pack_into("<I", out, pos + 14, serial)
        struct.pack_into("<I", out, pos + 22, 0)
        crc = ogg_crc(bytes(out[pos:pos + length]))
        struct.pack_into("<I", out, pos + 22, crc)
        pos += length
    return bytes(out)


def encode(data):
    import soundfile
    buf = io.BytesIO()
    soundfile.write(buf, data, RATE, format="OGG", subtype="VORBIS")
    return fix_serial(buf.getvalue())


def available():
    try:
        import soundfile
        return "VORBIS" in soundfile.available_subtypes("OGG")
    except (ImportError, OSError):
        return False


def sounds_json(namespace):
    out = {}
    for event, name, extra in EVENTS:
        entry = {"name": f"{namespace}:{FOLDER}/{name}"}
        entry.update(extra)
        out[event] = {"subtitle": f"{namespace}.subtitles.{event}", "sounds": [entry]}
    return out


def files():
    return [(f"sounds/{FOLDER}/{name}.ogg", name) for _, name, _ in EVENTS]
