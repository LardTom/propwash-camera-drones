import hashlib
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

from . import display, extras, paths, sounds

import paintable
import props

CONTEXTS = ("gui", "ground", "fixed", "on_shelf", "thirdperson_righthand", "firstperson_righthand", "head")
PART_TEXTURE_SIZES = (32, 64)
BLOCK_TEXTURE_SIZES = (16, 32)
HEAD = "gimbal_head"
PROP_VARIANTS = ["_ccw", "_blur"] + [f"_{d}" for d in props.DAMAGE] + [f"_{d}_ccw" for d in props.DAMAGE]
FRONT_BOXES = ((62.0, 58.0, 64.0), (34.0, 26.0, 40.0), (12.0, 10.0, 20.0), (44.0, 44.0, 46.0), (30.0, 24.0, 36.0))
FRONT_MAX_GAP = 1.0
FRONT_MAX_OVERLAP = 2.0
FRONT_NEAR = 80.0
FRONT_STEP = 0.5
MM_PER_BLOCK = 128.0
FACES = ((0, 2, 6, 4), (1, 5, 7, 3), (0, 4, 5, 1), (2, 3, 7, 6), (0, 1, 3, 2), (4, 6, 7, 5))


def split(resource_id):
    if ":" not in resource_id:
        return "minecraft", resource_id
    ns, path = resource_id.split(":", 1)
    return ns, path


def model_refs(node, out):
    if isinstance(node, dict):
        if node.get("type", "").endswith("model") and isinstance(node.get("model"), str):
            out.append(node["model"])
        if node.get("type", "").endswith("special") and isinstance(node.get("base"), str):
            out.append(node["base"])
        for value in node.values():
            model_refs(value, out)
    elif isinstance(node, list):
        for value in node:
            model_refs(value, out)


def load(file):
    return json.loads(file.read_text(encoding="utf-8"))


def check_models(assets_root, namespace, used_textures):
    root = Path(assets_root) / namespace
    problems = []
    folder = root / "models"
    for file in sorted(folder.rglob("*.json")) if folder.is_dir() else []:
        rel = file.relative_to(folder).with_suffix("").as_posix()
        data = load(file)
        parent = data.get("parent")
        if parent and split(parent)[0] not in ("minecraft", namespace):
            problems.append(f"{rel}: fremdes Eltern-Modell {parent}")
        for el in data.get("elements", []):
            values = el["from"] + el["to"]
            if min(values) < display.MODEL_MIN or max(values) > display.MODEL_MAX:
                problems.append(f"{rel}: Element {el.get('name')} außerhalb {display.MODEL_MIN:g}…{display.MODEL_MAX:g}")
        block = rel.startswith("block/")
        sizes = BLOCK_TEXTURE_SIZES if block else PART_TEXTURE_SIZES
        for key, sprite in data.get("textures", {}).items():
            if isinstance(sprite, dict):
                sprite = sprite["sprite"]
            if sprite.startswith("#"):
                continue
            ns, path = split(sprite)
            if ns != namespace:
                problems.append(f"{rel}: fremde Textur {sprite} (nur eigene, erzeugte Texturen)")
                continue
            used_textures.add(path)
            png = Path(assets_root) / ns / "textures" / f"{path}.png"
            if not png.exists():
                problems.append(f"{rel}: Textur {sprite} fehlt")
                continue
            with Image.open(png) as img:
                if img.size[0] != img.size[1] or img.size[0] not in sizes:
                    problems.append(f"{rel}: Textur {sprite} ist {img.size[0]}×{img.size[1]} (erlaubt {sizes})")
        if not block:
            shown = data.get("display", {})
            for context in CONTEXTS:
                if context not in shown:
                    problems.append(f"{rel}: display.{context} fehlt")
                elif max(abs(s) for s in shown[context].get("scale", [1, 1, 1])) > 4.0:
                    problems.append(f"{rel}: display.{context}.scale über 4")
            if data.get("gui_light") != "side":
                problems.append(f"{rel}: gui_light ist nicht side")
        if rel.startswith("part/accessory/") or rel.startswith(display.ICON_PREFIX + "/accessory/"):
            problems += check_head(rel, data)
    return problems


def check_head(rel, data):
    heads = [el for el in data.get("elements", []) if el.get("name") == HEAD]
    if len(heads) != 1:
        return [f"{rel}: {len(heads)} Elemente {HEAD} (genau eines für GimbalProvider#modelPose)"]
    el = heads[0]
    rotation = el.get("rotation") or {}
    center = [round((a + b) / 2.0, 4) for a, b in zip(el["from"], el["to"])]
    origin = rotation.get("origin")
    if origin is None or any(abs(a - b) > 1e-3 for a, b in zip(origin, center)):
        return [f"{rel}: {HEAD} ohne Drehpunkt in der Mitte (rotation.origin {origin}, Mitte {center})"]
    if abs(float(rotation.get("angle", 0))) > 1e-6:
        return [f"{rel}: {HEAD} ist im Modell gedreht"]
    return []


def check_items(assets_root, namespace):
    root = Path(assets_root) / namespace
    problems = []
    folder = root / "items"
    for file in sorted(folder.rglob("*.json")) if folder.is_dir() else []:
        rel = file.relative_to(folder).with_suffix("").as_posix()
        data = load(file)
        refs = []
        model_refs(data.get("model", {}), refs)
        if not refs:
            problems.append(f"items/{rel}: kein Modell")
        for ref in refs:
            ns, path = split(ref)
            if ns == namespace and not (Path(assets_root) / ns / "models" / f"{path}.json").exists():
                problems.append(f"items/{rel}: Modell {ref} fehlt")
            if ns not in (namespace,):
                problems.append(f"items/{rel}: fremdes Modell {ref}")
    return problems


def check_parts(assets_root, namespace, parts_dir=paths.PARTS):
    root = Path(assets_root) / namespace
    problems = []
    paint_file = root / "drone_paint.json"
    paint = load(paint_file).get("models", {}) if paint_file.exists() else {}
    layout_file = root / "drone_layout.json"
    seats = load(layout_file).get("motor_seat", {}) if layout_file.exists() else {}
    for category_dir in sorted(Path(parts_dir).iterdir()) if Path(parts_dir).is_dir() else []:
        category = category_dir.name
        for part in sorted(category_dir.glob("*.json")):
            key = part.stem
            if not (root / "items" / f"{key}.json").exists():
                problems.append(f"{category}/{key}: Inventar-Symbol items/{key}.json fehlt")
            render = root / "items/drone_render" / category / f"{key}.json"
            if not render.exists():
                problems.append(f"{category}/{key}: Render-Definition items/drone_render/{category}/{key}.json fehlt")
            if not (root / "models/part" / category / f"{key}.json").exists():
                problems.append(f"{category}/{key}: Modell models/part/{category}/{key}.json fehlt")
            if category == "prop":
                for suffix in PROP_VARIANTS:
                    if not (root / "items/drone_render/prop" / f"{key}{suffix}.json").exists():
                        problems.append(f"prop/{key}: Variante {suffix} fehlt")
            if category == "motor" and key not in seats:
                problems.append(f"motor/{key}: motor_seat fehlt in drone_layout.json")
            if f"{category}/{key}" not in paint:
                problems.append(f"{category}/{key}: kein Eintrag in drone_paint.json (nicht lackierbar)")
    for name in paint:
        if not (root / "items/drone_render" / f"{name}_paint.json").exists():
            problems.append(f"drone_paint.json: {name} ohne Render-Definition {name}_paint")
    as_tuples = {name: [(g["channel"], g["base"]) for g in entries] for name, entries in paint.items()}
    problems += paintable.check_references(as_tuples)
    return problems


def check_extras(assets_root, namespace):
    root = Path(assets_root) / namespace
    return [f"items/{key}.json fehlt" for key in extras.ITEM_KEYS if not (root / "items" / f"{key}.json").exists()]


def axis_rotation(axis, degrees):
    a = math.radians(float(degrees))
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1.0, 0.0, 0.0], [0.0, c, -s], [0.0, s, c]])
    if axis == "y":
        return np.array([[c, 0.0, s], [0.0, 1.0, 0.0], [-s, 0.0, c]])
    if axis == "z":
        return np.array([[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]])
    raise ValueError(f"unbekannte Drehachse {axis}")


def rotation_matrix(rotation):
    if "axis" in rotation:
        return axis_rotation(rotation["axis"], rotation.get("angle", 0.0))
    return (axis_rotation("z", rotation.get("z", 0.0)) @ axis_rotation("y", rotation.get("y", 0.0))
            @ axis_rotation("x", rotation.get("x", 0.0)))


def rotation_rescale(rotation):
    if not rotation.get("rescale") or "axis" not in rotation:
        return np.ones(3)
    c = abs(math.cos(math.radians(float(rotation.get("angle", 0.0)))))
    f = 1.0 / c if c > 1e-6 else 1.0
    return np.array({"x": (1.0, f, f), "y": (f, 1.0, f)}.get(rotation["axis"], (f, f, 1.0)))


def render_solids(render, model):
    transformation = render.get("transformation") or {}
    for key in ("left_rotation", "right_rotation"):
        if list(transformation.get(key, [0.0, 0.0, 0.0, 1.0])) != [0.0, 0.0, 0.0, 1.0]:
            raise ValueError(f"transformation.{key} ist keine Einheitsdrehung")
    scale = np.array(transformation.get("scale", [1.0, 1.0, 1.0]), float)
    translation = np.array(transformation.get("translation", [0.0, 0.0, 0.0]), float)
    solids = []
    for el in model.get("elements", []):
        lo, hi = el["from"], el["to"]
        corners = np.array([[hi[0] if i & 1 else lo[0], hi[1] if i & 2 else lo[1], hi[2] if i & 4 else lo[2]]
                            for i in range(8)], float)
        rotation = el.get("rotation")
        if rotation:
            origin = np.array(rotation.get("origin", [8.0, 8.0, 8.0]), float)
            corners = (corners - origin) @ rotation_matrix(rotation).T * rotation_rescale(rotation) + origin
        solids.append((corners / 16.0 * scale + translation - 0.5) * MM_PER_BLOCK)
    return solids


def front_fit(solids, anchor, size):
    lo = np.array([anchor[0] - size[0] / 2.0, anchor[1] - size[1], anchor[2] - size[2]])
    hi = np.array([anchor[0] + size[0] / 2.0, anchor[1], anchor[2]])
    gap, overlap = math.inf, 0.0
    for corners in solids:
        if np.any(corners.min(axis=0) > hi + FRONT_NEAR) or np.any(corners.max(axis=0) < lo - FRONT_NEAR):
            continue
        for face in FACES:
            p0, p1, p2, p3 = (corners[i] for i in face)
            nu = max(1, math.ceil(float(np.linalg.norm(p1 - p0)) / FRONT_STEP))
            nv = max(1, math.ceil(float(np.linalg.norm(p3 - p0)) / FRONT_STEP))
            u, v = np.meshgrid(np.arange(nu + 1) / nu, np.arange(nv + 1) / nv, indexing="ij")
            u, v = u.reshape(-1, 1), v.reshape(-1, 1)
            p = p0 * ((1 - u) * (1 - v)) + p1 * (u * (1 - v)) + p2 * (u * v) + p3 * ((1 - u) * v)
            distance = np.sqrt((np.maximum(np.maximum(lo - p, 0.0), p - hi) ** 2).sum(axis=1))
            gap = min(gap, float(distance.min()))
            inside = p[distance == 0.0]
            if len(inside):
                overlap = max(overlap, float(np.minimum(inside - lo, hi - inside).min(axis=1).max()))
    return gap, overlap


def front_sizes(parts_dir):
    sizes = list(FRONT_BOXES)
    for file in sorted((Path(parts_dir) / "accessory").glob("*.json")):
        data = load(file)
        if data.get("slot") == "front" and isinstance(data.get("size_mm"), list) and len(data["size_mm"]) == 3:
            sizes.append(tuple(float(v) for v in data["size_mm"]))
    return sizes


def check_front_fit(assets_root, namespace=paths.NS, parts_dir=paths.PARTS):
    root = Path(assets_root) / namespace
    sizes = front_sizes(parts_dir)
    problems = []
    for file in sorted((Path(parts_dir) / "frame").glob("*.json")):
        key = file.stem
        data = load(file)
        anchor = (data.get("accessory_anchors") or {}).get("front") or {}
        if "front" not in (data.get("accessory_mounts") or []) or not isinstance(anchor.get("pos"), list):
            continue
        render_file = root / "items/drone_render/frame" / f"{key}.json"
        if not render_file.exists():
            continue
        render = load(render_file).get("model", {})
        ns, path = split(str(render.get("model", "")))
        model_file = Path(assets_root) / ns / "models" / f"{path}.json"
        if not model_file.exists():
            continue
        if float(anchor.get("tilt_deg", 0.0)) != 0.0:
            problems.append(f"frame/{key}: Anker vorne mit Neigung {anchor['tilt_deg']}°, Sitz des Zubehörs nicht gemessen")
            continue
        try:
            solids = render_solids(render, load(model_file))
        except ValueError as e:
            problems.append(f"frame/{key}: Sitz des Zubehörs vorne nicht messbar ({e})")
            continue
        pos = [float(v) for v in anchor["pos"]]
        for size in sizes:
            gap, overlap = front_fit(solids, pos, size)
            if gap > FRONT_MAX_GAP or overlap > FRONT_MAX_OVERLAP:
                problems.append(f"frame/{key}: Zubehör vorne {size[0]:.0f}×{size[1]:.0f}×{size[2]:.0f} nicht am Rahmen: "
                                f"Abstand {gap:.1f} mm, Überschneidung {overlap:.1f} mm, Anker ({pos[0]:.1f}; {pos[1]:.1f}; "
                                f"{pos[2]:.1f}) (höchstens {FRONT_MAX_GAP:g} mm Abstand, {FRONT_MAX_OVERLAP:g} mm Überschneidung)")
    return problems


def lang_keys():
    keys = {}
    for module in sorted(paths.LANG.iterdir()) if paths.LANG.is_dir() else []:
        for language in ("en_us", "de_de"):
            file = module / f"{language}.json"
            if file.exists():
                keys.setdefault(language, set()).update(load(file).keys())
    return keys


def vorbis_mono(data):
    marker = data.find(b"\x01vorbis")
    if not data.startswith(b"OggS") or marker < 0:
        return None
    return data[marker + 11], int.from_bytes(data[marker + 12:marker + 16], "little")


def check_sounds(assets_root, namespace):
    root = Path(assets_root) / namespace
    problems = []
    file = root / "sounds.json"
    if not file.exists():
        return ["sounds.json fehlt"]
    data = load(file)
    keys = lang_keys()
    wanted = {event for event, _, _ in sounds.EVENTS}
    if set(data) != wanted:
        problems.append(f"sounds.json: Ereignisse {sorted(set(data) ^ wanted)} weichen ab")
    for event, entry in sorted(data.items()):
        subtitle = entry.get("subtitle")
        for language in ("en_us", "de_de"):
            if subtitle and subtitle not in keys.get(language, set()):
                problems.append(f"sounds.json: {event} Untertitel {subtitle} fehlt in {language}")
        for sound in entry.get("sounds", []):
            ns, path = split(sound["name"] if isinstance(sound, dict) else sound)
            ogg = Path(assets_root) / ns / "sounds" / f"{path}.ogg"
            if ns != namespace:
                problems.append(f"sounds.json: {event} nutzt fremden Sound {ns}:{path}")
            elif not ogg.exists():
                problems.append(f"sounds.json: {event} Datei sounds/{path}.ogg fehlt")
            else:
                info = vorbis_mono(ogg.read_bytes())
                if info is None:
                    problems.append(f"sounds/{path}.ogg ist kein Ogg Vorbis")
                elif info != (1, sounds.RATE):
                    problems.append(f"sounds/{path}.ogg: {info[0]} Kanäle, {info[1]} Hz (erwartet Mono {sounds.RATE} Hz)")
    folder = root / "sounds"
    named = {f"{sounds.FOLDER}/{name}" for _, name, _ in sounds.EVENTS}
    for ogg in sorted(folder.rglob("*")) if folder.is_dir() else []:
        if ogg.is_file() and ogg.relative_to(folder).with_suffix("").as_posix() not in named:
            problems.append(f"sounds/{ogg.relative_to(folder).as_posix()}: nicht in sounds.json")
    return problems


def check_stray_textures(assets_root, namespace, used):
    root = Path(assets_root) / namespace / "textures"
    problems = []
    for png in sorted(root.rglob("*.png")) if root.is_dir() else []:
        rel = png.relative_to(root).with_suffix("").as_posix()
        if rel not in used:
            problems.append(f"textures/{rel}.png: von keinem Modell benutzt (nur erzeugte Texturen)")
    return problems


def check(assets_root, namespace=paths.NS, with_sounds=True, full=True):
    used = set()
    problems = check_models(assets_root, namespace, used)
    problems += check_items(assets_root, namespace)
    problems += check_front_fit(assets_root, namespace)
    if full:
        problems += check_parts(assets_root, namespace)
        problems += check_extras(assets_root, namespace)
        problems += check_stray_textures(assets_root, namespace, used)
        if with_sounds:
            problems += check_sounds(assets_root, namespace)
    return problems


def vendored_intact():
    problems = []
    sums = paths.STYLE / "SHA256SUMS"
    if not sums.exists():
        return ["propwash_style/SHA256SUMS fehlt"]
    listed = set()
    for line in sums.read_text(encoding="utf-8").splitlines():
        digest, name = line.split(None, 1)
        name = name.strip()
        listed.add(name)
        file = paths.STYLE / name
        if not file.exists():
            problems.append(f"propwash_style/{name} fehlt")
        elif hashlib.sha256(file.read_bytes()).hexdigest() != digest:
            problems.append(f"propwash_style/{name} weicht von der Propwash-Quelle ab (Kopie muss unverändert bleiben)")
    for file in sorted(paths.STYLE.glob("*.py")):
        if file.name not in listed:
            problems.append(f"propwash_style/{file.name} steht nicht in SHA256SUMS")
    return problems


def same_tree(generated, committed, namespace=paths.NS, folders=("models", "textures", "items", "sounds"),
              files=("sounds.json", "drone_paint.json", "drone_layout.json")):
    problems = []
    a_root = Path(generated) / namespace
    b_root = Path(committed) / namespace
    names = set()
    for root in (a_root, b_root):
        for folder in folders:
            base = root / folder
            if base.is_dir():
                names.update(p.relative_to(root).as_posix() for p in base.rglob("*") if p.is_file())
        for name in files:
            if (root / name).exists():
                names.add(name)
    for name in sorted(names):
        a, b = a_root / name, b_root / name
        if not a.exists():
            problems.append(f"{name}: liegt in den Ressourcen, erzeugt der Generator aber nicht")
        elif not b.exists():
            problems.append(f"{name}: erzeugt, fehlt in den Ressourcen (style_generate.py --write)")
        elif a.read_bytes() != b.read_bytes():
            problems.append(f"{name}: weicht vom Generator ab")
    return problems
