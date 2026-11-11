import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from camstyle import itemview as iv
from camstyle import paths

CONTEXTS = [
    ("gui", "GUI"),
    ("fp_right", "1. Person rechts"),
    ("fp_left", "1. Person links"),
    ("tp_right", "3. Person rechts"),
    ("ground", "Boden"),
    ("frame", "Rahmen"),
]

PAIRS = {
    "frame": [("long_range_7", "compact_fold_frame"), ("long_range_7", "tour_fold_frame"), ("long_range_7", "cine_frame")],
    "stack": [("aio_20", "nav_fc_compact"), ("stack_30", "nav_fc_pro")],
    "motor": [("m1404_4600", "m1404_1600"), ("m2004_3000", "m2008_500"), ("m2807_1300", "m4114_320")],
    "prop": [("p51_3b", "fold_6"), ("p7_2b", "fold_9"), ("p7_2b", "fold_15")],
    "battery": [("b4s_650", "smart_2s_3850_lion"), ("b6s_1300", "smart_4s_4240_hv"), ("b6s_4000_lion", "smart_6s_8560_hv")],
    "video": [("digital_lite", "cam_link_compact"), ("digital_hd", "cam_link_pro")],
    "accessory": [("action_cam_light", "gimbal_compact"), ("action_cam_heavy", "gimbal_tour"),
                  ("action_cam_heavy", "gimbal_cine")],
    "item": [("remote", "flight_planner"), ("action_cam_flashback", "lens_24"), ("action_cam_flashback", "lens_50"),
             ("action_cam_flashback", "lens_85"), ("battery_charger", "landing_pad")],
}

BG = (30, 32, 36, 255)
HEAD = (230, 232, 236, 255)
SUB = (150, 156, 166, 255)
ACCENT = (255, 187, 0, 255)
CELL_W, CELL_H = 224, 156
LABEL_W = 176
GAP = 6


def font(size):
    for name in ("DejaVuSans.ttf", "Arial.ttf", "Helvetica.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def render_cell(store, item_id, context, category):
    if context == "gui":
        cell = Image.new("RGBA", (CELL_W, CELL_H), (44, 47, 53, 255))
        gui = iv.view_gui(store, item_id, size=CELL_H - 12, gui_scale=8, category=category)
        cell.alpha_composite(gui, ((CELL_W - gui.width) // 2, 6))
        return cell
    if context in ("fp_right", "fp_left"):
        return iv.view_first_person(store, item_id, CELL_W, CELL_H, left=context == "fp_left", category=category)
    if context == "tp_right":
        return iv.view_third_person(store, item_id, CELL_W, CELL_H, category=category)
    if context == "ground":
        return iv.view_ground(store, item_id, CELL_W, CELL_H, category=category)
    return iv.view_item_frame(store, item_id, CELL_W, CELL_H, category=category)


def scale_strip(entries, category, width):
    strip = Image.new("RGBA", (width, CELL_H), (38, 41, 46, 255))
    draw = ImageDraw.Draw(strip)
    models = []
    for label, store, item_id in entries:
        layer = iv.render_layer(store, item_id, category)
        if layer is None:
            continue
        pts = iv.placed_points(store, layer.model_id, layer.local)
        if pts is None:
            continue
        extent = float(np.max(pts.max(axis=0) - pts.min(axis=0))) * 16.0 * 8.0
        models.append((label, store, layer, extent))
    if not models:
        draw.text((10, 6), "kein Drohnen-Modell", fill=SUB, font=font(12))
        return strip
    biggest = max(m[3] for m in models)
    px_per_mm = min(4.0, (CELL_H - 44) / biggest * 1.2)
    x = 10
    for label, store, layer, extent in models:
        img = iv.true_scale(store, layer.model_id, px_per_mm, local=layer.local)
        if img.height > CELL_H - 24:
            img = img.resize((max(1, int(img.width * (CELL_H - 24) / img.height)), CELL_H - 24), Image.NEAREST)
        if x + img.width > width - 10:
            break
        strip.alpha_composite(img, (x, CELL_H - 18 - img.height))
        caption = f"{label} ({extent:.0f} mm)"
        draw.text((x, CELL_H - 16), caption, fill=SUB, font=font(11))
        x += max(img.width, 180) + 24
    ruler = 50.0 * px_per_mm
    draw.line((width - 20 - ruler, 12, width - 20, 12), fill=ACCENT, width=2)
    draw.text((width - 20 - ruler, 16), "50 mm", fill=ACCENT, font=font(11))
    draw.text((10, 6), "gemeinsamer Maßstab (Drohnen-Modelle, echte Größe)", fill=SUB, font=font(12))
    return strip


def sheet(category, pairs, base, store, contexts):
    part_category = None if category == "item" else category
    width = LABEL_W + len(contexts) * (CELL_W + GAP) + GAP
    strip = part_category is not None
    height = 44 + len(pairs) * (2 * (CELL_H + GAP) + (CELL_H + 3 * GAP if strip else GAP))
    out = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(out)
    draw.text((GAP, 8), f"Propwash gegen Kamera-Drohnen – {category}", fill=HEAD, font=font(18))
    for i, (_, name) in enumerate(contexts):
        draw.text((LABEL_W + i * (CELL_W + GAP) + 4, 28), name, fill=SUB, font=font(12))
    y = 44
    for base_key, cam_key in pairs:
        rows = [("Propwash", base, f"propwash:{base_key}"), ("Kamera-Drohnen", store, f"{paths.NS}:{cam_key}")]
        for label, st, item_id in rows:
            draw.text((GAP, y + 6), label, fill=HEAD, font=font(13))
            draw.text((GAP, y + 24), item_id.split(":", 1)[1], fill=SUB, font=font(12))
            if st.json(item_id, "items") is None:
                draw.text((LABEL_W, y + 40), "Item fehlt", fill=ACCENT, font=font(12))
            else:
                for i, (context, _) in enumerate(contexts):
                    out.alpha_composite(render_cell(st, item_id, context, part_category), (LABEL_W + i * (CELL_W + GAP), y))
            y += CELL_H + GAP
        if strip:
            entries = [(f"{lab}: {iid.split(':', 1)[1]}", st, iid) for lab, st, iid in rows]
            out.alpha_composite(scale_strip(entries, category, width - LABEL_W - GAP), (LABEL_W, y))
            y += CELL_H + 3 * GAP
        draw.line((GAP, y - GAP - 2, width - GAP, y - GAP - 2), fill=(60, 64, 72, 255), width=1)
    return out


def main(argv=None):
    parser = argparse.ArgumentParser(description="Vergleichsbilder Propwash gegen Kamera-Drohnen je Kategorie: GUI, erste "
                                                 "Person (beide Hände), dritte Person, Boden, Item-Rahmen, dazu ein Streifen "
                                                 "im gemeinsamen Maßstab.")
    parser.add_argument("--category", action="append", choices=sorted(PAIRS), help="Kategorie (mehrfach), Standard: alle")
    parser.add_argument("--assets", default=str(paths.ASSETS_ROOT), help="assets-Ordner der Kamera-Drohnen")
    parser.add_argument("--propwash-assets", help="Propwash-Assets (Ordner oder Jar), Standard: propwash-common aus mavenLocal")
    parser.add_argument("--out", default=str(paths.BUILD / "style-compare"), help="Zielordner der PNGs")
    parser.add_argument("--contexts", help="Auswahl, z. B. gui,fp_right,ground")
    args = parser.parse_args(argv)
    pw = paths.propwash_assets(args.propwash_assets)
    base = iv.AssetStore([pw])
    store = iv.AssetStore([Path(args.assets), pw])
    contexts = CONTEXTS
    if args.contexts:
        wanted = args.contexts.split(",")
        contexts = [c for c in CONTEXTS if c[0] in wanted]
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    for category in args.category or sorted(PAIRS):
        img = sheet(category, PAIRS[category], base, store, contexts)
        file = out_dir / f"compare_{category}.png"
        img.save(file, optimize=True)
        print(f"{file} ({len(PAIRS[category])} Paare)")


if __name__ == "__main__":
    main()
