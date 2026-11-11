import argparse
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np
from PIL import Image, ImageDraw

from camstyle import paths

import kit
import render

BG = (46, 49, 56, 255)
CELL = 220
VIEWS = {
    "schräg": (30, 225, 0),
    "vorn": (12, 180, 0),
    "seite": (8, 270, 0),
    "unten": (-35, 200, 0),
}
PRESETS = {
    "compact": dict(frame="compact_fold_frame", stack="nav_fc_compact", motor="m1404_1600", prop="fold_6",
                    video="cam_link_compact", battery="smart_2s_3850_lion", accessory="gimbal_compact"),
    "tour": dict(frame="tour_fold_frame", stack="nav_fc_pro", motor="m2008_500", prop="fold_9", video="cam_link_pro",
                 battery="smart_4s_4240_hv", accessory="gimbal_tour"),
    "cine": dict(frame="cine_frame", stack="nav_fc_pro", motor="m4114_320", prop="fold_15", video="cam_link_pro",
                 battery="smart_6s_8560_hv", accessory="gimbal_cine"),
}
SPIN_CCW = (True, False, False, True)


def use_assets(roots):
    roots = [Path(r) for r in roots]

    def find(ns, rel):
        for root in roots:
            file = root / ns / rel
            if file.exists():
                return file
        if ns == "propwash":
            raise FileNotFoundError(rel)
        raise FileNotFoundError(f"{ns}:{rel}")

    def texture(sprite_id):
        if isinstance(sprite_id, dict):
            sprite_id = sprite_id["sprite"]
        ns, path = sprite_id.split(":", 1)
        file = find(ns, f"textures/{path}.png")
        if file not in render._texture_cache:
            render._texture_cache[file] = np.asarray(Image.open(file).convert("RGBA"), dtype=np.float64)
        return render._texture_cache[file]

    def model_path(model_id):
        ns, path = model_id.split(":", 1)
        return find(ns, f"models/{path}.json")

    render.texture = texture
    render.model_path = model_path


def render_node(root, category, key):
    file = Path(root) / paths.NS / "items/drone_render" / category / f"{key}.json"
    node = json.loads(file.read_text(encoding="utf-8"))["model"]
    factor = 1.0
    if "transformation" in node:
        factor = node["transformation"]["scale"][0]
    return node["model"], factor


def extent(quads):
    pts = np.concatenate([q[0] for q in quads])
    mm = (pts - kit.ANCHOR) * kit.UNIT_MM
    return mm.min(axis=0), mm.max(axis=0)


def scaled_quads(quads, factor):
    if factor == 1.0:
        return quads
    out = []
    for corners, uv, tex, rot in quads:
        out.append(((corners - kit.ANCHOR) * factor + kit.ANCHOR, uv, tex, rot))
    return out


def cell(quads, label, rotation, translucent=False, size=CELL):
    canvas = render.Canvas(size, size, BG)
    lo, hi = extent(quads)
    span = float(np.linalg.norm(hi - lo))
    scale = (size * 0.8) / max(span, 1e-3)
    tf = render.ViewTransform(rotation, scale, (size / 2, size / 2 + 6), pivot=(lo + hi) / 2)
    canvas.draw(quads, tf, translucent)
    canvas.flush()
    img = canvas.image()
    ImageDraw.Draw(img).text((6, size - 14), label, fill=(230, 230, 230, 255))
    return img


def grid(cells, columns):
    rows = (len(cells) + columns - 1) // columns
    w = max(c.width for c in cells)
    h = max(c.height for c in cells)
    out = Image.new("RGBA", (columns * w, rows * h), BG)
    for i, img in enumerate(cells):
        out.paste(img, ((i % columns) * w, (i // columns) * h))
    return out


def part_sheet(root, out_file, views=("schräg", "vorn")):
    cells = []
    base = Path(root) / paths.NS / "items/drone_render"
    for category in ("frame", "stack", "motor", "prop", "battery", "video", "accessory"):
        folder = base / category
        for item in sorted(folder.glob("*.json")) if folder.is_dir() else []:
            key = item.stem
            if key.endswith("_paint") or any(key.endswith(s) for s in ("_worn", "_damaged", "_broken", "_ccw", "_blur",
                                                                        "_worn_ccw", "_damaged_ccw", "_broken_ccw")):
                continue
            model_id, factor = render_node(root, category, key)
            quads, translucent, _ = render.load_model(model_id)
            quads = scaled_quads(quads, factor)
            for view in views:
                cells.append(cell(quads, f"{category}/{key} {view}", VIEWS[view], translucent))
    for key in ("lens_24", "lens_50", "lens_85", "flight_planner"):
        quads, translucent, _ = render.load_model(f"{paths.NS}:item/{key}")
        for view in views:
            cells.append(cell(quads, f"item/{key} {view}", VIEWS[view], translucent))
    quads, _, _ = render.load_model(f"{paths.NS}:block/landing_pad")
    cells.append(cell(quads, "block/landing_pad", VIEWS["schräg"]))
    img = grid(cells, 4)
    Path(out_file).parent.mkdir(parents=True, exist_ok=True)
    img.save(out_file, optimize=True)
    return out_file


def placement(offset, yaw=0.0, tilt=0.0):
    r = kit.element_matrix(tilt, yaw, 0.0)
    return r, np.array(offset, dtype=float)


def assembly(root, preset):
    p = PRESETS[preset]
    frame_def = json.loads((paths.PARTS / "frame" / f"{p['frame']}.json").read_text(encoding="utf-8"))
    battery_def = json.loads((paths.PARTS / "battery" / f"{p['battery']}.json").read_text(encoding="utf-8"))
    layout = json.loads((Path(root) / paths.NS / "drone_layout.json").read_text(encoding="utf-8"))
    wb = float(frame_def["wheelbase_mm"])
    s = wb / (2.0 * math.sqrt(2.0))
    motor_y = 3.0 if wb >= 200 else 2.5 if wb >= 100 else 1.5
    motors = [(-s, motor_y, -s), (s, motor_y, -s), (-s, motor_y, s), (s, motor_y, s)]
    seat = layout["motor_seat"][p["motor"]]
    plate = float(frame_def["battery"]["plate_mm"])
    cam = frame_def["camera"]["pos"]
    front = frame_def["accessory_anchors"]["front"]["pos"]
    parts = [("frame", p["frame"], (0, 0, 0), False), ("stack", p["stack"], (0, motor_y, 0), False),
             ("battery", p["battery"], (0, plate + 2.0, 0.04 * wb), False),
             ("video", p["video"], (cam[0], cam[1], cam[2] + 10.0), False),
             ("accessory", p["accessory"], tuple(front), False)]
    for i, m in enumerate(motors):
        parts.append(("motor", p["motor"], m, False))
        parts.append(("prop", p["prop"] + ("_ccw" if SPIN_CCW[i] else ""), (m[0], m[1] + seat, m[2]), True))
    pieces = []
    for category, key, offset, prop in parts:
        model_id, factor = render_node(root, category, key)
        quads, translucent, _ = render.load_model(model_id)
        quads = scaled_quads(quads, factor)
        pieces.append((quads, placement(offset), translucent))
    return pieces


def assembly_cell(pieces, label, rotation, size=360):
    canvas = render.Canvas(size, size, BG)
    pts = []
    for quads, (rot, offset), _ in pieces:
        corners = np.concatenate([q[0] for q in quads])
        mm = (corners - kit.ANCHOR) * kit.UNIT_MM
        pts.append(mm @ rot.T + offset)
    pts = np.concatenate(pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    scale = (size * 0.85) / max(float(np.linalg.norm(hi - lo)), 1e-3)
    for quads, place, translucent in pieces:
        tf = render.ViewTransform(rotation, scale, (size / 2, size / 2), placement=place, pivot=(lo + hi) / 2)
        canvas.draw(quads, tf, translucent)
    canvas.flush()
    img = canvas.image()
    ImageDraw.Draw(img).text((6, size - 14), label, fill=(230, 230, 230, 255))
    return img


def assembly_sheet(root, out_file):
    cells = []
    for preset in PRESETS:
        pieces = assembly(root, preset)
        for view, rotation in VIEWS.items():
            cells.append(assembly_cell(pieces, f"{preset} {view}", rotation))
    img = grid(cells, len(VIEWS))
    Path(out_file).parent.mkdir(parents=True, exist_ok=True)
    img.save(out_file, optimize=True)
    return out_file


def main(argv=None):
    parser = argparse.ArgumentParser(description="Vorschaubilder der Kamera-Drohnen: alle Teile und die drei Presets "
                                                 "zusammengesetzt (Software-Rasterer der Basis).")
    parser.add_argument("--assets", default=str(paths.ASSETS_ROOT), help="assets-Ordner (Standard: Mod-Ressourcen)")
    parser.add_argument("--propwash-assets", help="Propwash-Assets (Ordner oder Jar), nur für Vergleiche nötig")
    parser.add_argument("--out", default=str(paths.BUILD / "style-preview"), help="Zielordner der PNGs")
    args = parser.parse_args(argv)
    use_assets([args.assets])
    out = Path(args.out)
    print(part_sheet(args.assets, out / "parts.png"))
    print(assembly_sheet(args.assets, out / "presets.png"))


if __name__ == "__main__":
    main()
