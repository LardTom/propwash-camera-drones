import json
from pathlib import Path

from . import battery, frame, gimbal, motor, paths, prop, stack, video

CATEGORIES = ("frame", "stack", "motor", "prop", "video", "battery", "accessory")
BUILDERS = {"frame": frame.build, "stack": stack.build, "video": video.build, "battery": battery.build,
            "accessory": gimbal.build}


def load(parts_dir=paths.PARTS, categories=CATEGORIES, only=None):
    out = []
    for category in categories:
        folder = Path(parts_dir) / category
        if not folder.is_dir():
            continue
        for file in sorted(folder.glob("*.json")):
            if only and file.stem not in only:
                continue
            out.append((category, file.stem, json.loads(file.read_text(encoding="utf-8"))))
    return out


def build_into(writer, category, key, definition, overrides=None):
    if category == "motor":
        model, seat = motor.build(key, definition, overrides)
        writer.motor(model, seat)
    elif category == "prop":
        writer.prop(key, prop.build(key, definition, overrides))
    elif category == "battery":
        model, stages, cases = battery.build_with_stages(key, definition, overrides)
        writer.battery(model, stages, cases, battery.CHARGE_COMPONENT)
    else:
        writer.simple(BUILDERS[category](key, definition, overrides))
