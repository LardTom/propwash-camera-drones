import json
import shutil
from pathlib import Path

from . import common, display, paths

import addon
import kit
import paintable

CREDIT = "Propwash: Camera Drones - lardtom - tools/assets/style_generate.py"
HEAD = "gimbal_head"
GLOW = {"screen": 6}
OWNED = ("models", "textures", "items", "sounds")
OWNED_FILES = ("sounds.json", "drone_paint.json", "drone_layout.json")
NO_PAINT = ("antenna",)


def pivot_rotation(element):
    if element.get("name") != HEAD or "rotation" in element:
        return
    origin = [round((a + b) / 2.0, 4) for a, b in zip(element["from"], element["to"])]
    element["rotation"] = {"angle": 0, "axis": "x", "origin": kit.r4(origin)}


def finish_json(data):
    data["credit"] = CREDIT
    for element in data.get("elements", []):
        pivot_rotation(element)
        glow = GLOW.get(element.get("name"))
        if glow:
            element["light_emission"] = glow
    return data


class Output(addon.Output):
    def model(self, model, display_json):
        data = finish_json(model.to_json(display_json))
        path = self.asset(model.model_id, "models", ".json")
        kit.dump_json(path, data)
        self.written.append(path)
        if self.bbmodel_root is not None:
            kit.dump_json(self.bbmodel_root / f"{model.category}/{model.key}.bbmodel", model.to_bbmodel(display_json))

    def raw_model(self, model_id, data):
        path = self.asset(model_id, "models", ".json")
        kit.dump_json(path, finish_json(data))
        self.written.append(path)

    def json(self, rel, data):
        path = self.root / self.namespace / rel
        kit.dump_json(path, data)
        self.written.append(path)

    def binary(self, rel, data):
        path = self.root / self.namespace / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        self.written.append(path)


class Writer:
    def __init__(self, assets_root, namespace=paths.NS, icons=True, oversize="scaled", bbmodel_root=None):
        common.setup(namespace)
        self.namespace = namespace
        self.root = Path(assets_root)
        self.out = Output(self.root, namespace, bbmodel_root)
        self.icons = icons
        self.oversize = oversize
        self.report = []

    def wipe(self):
        base = self.root / self.namespace
        for folder in OWNED:
            if (base / folder).is_dir():
                shutil.rmtree(base / folder)
        for name in OWNED_FILES:
            if (base / name).exists():
                (base / name).unlink()

    def clean(self, category, key):
        base = self.root / self.namespace
        longer = [k for k in owned_keys(category) if k != key and k.startswith(key + "_")]
        patterns = [(base / "models/part" / category, f"{key}*.json"), (base / "models" / display.ICON_PREFIX / category, f"{key}*.json"),
                    (base / "textures/item/part" / category, f"{key}*.png"), (base / "items/drone_render" / category, f"{key}*.json")]
        for folder, pattern in patterns:
            if folder.is_dir():
                for file in folder.glob(pattern):
                    stem = file.stem
                    if any(stem == k or stem.startswith(k + "_") for k in longer):
                        continue
                    if stem == key or stem.startswith(key + "_"):
                        file.unlink()

    def simple(self, model, icon_source=None):
        model.pack()
        if display.fits(model):
            display_json, pre = display.compute(model)
            self.out.texture(*model.texture_map()["0"])
            self.out.model(model, display_json)
            self.out.render_item(model)
            addon.write_paint(self.out, model, display_json)
            if self.icons:
                self.out.icon(model.key, display.item_definition(model.model_id, pre))
            self.report.append((model.category, model.key, "modell"))
            return
        source = model
        if icon_source is not None:
            icon_source.key = model.key + "_icon"
            icon_source.pack()
            source = icon_source
        icon, _ = display.icon_model(source)
        if icon is None:
            icon = display.scaled_copy(source, 1.0, f"{kit.NAMESPACE}:{display.ICON_PREFIX}/{source.category}/{source.key}")
        display_json, pre = display.compute(icon)
        self.out.texture(*source.texture_map()["0"])
        self.out.model(icon, display_json)
        if self.icons:
            self.out.icon(model.key, display.item_definition(icon.model_id, pre))
        if self.oversize == "scaled":
            if source is not model:
                self.out.texture(*model.texture_map()["0"])
            factor = display.render_factor(model)
            scaled = display.scaled_copy(model, factor, model.model_id)
            scaled_display, _ = display.compute(scaled)
            self.out.model(scaled, scaled_display)
            self.scaled_render_item(model.category, model.key, model.model_id, factor)
            variant = addon.write_paint(self.out, scaled, scaled_display)
            if variant is not None:
                self.scaled_render_item(model.category, variant.key, variant.model_id, factor,
                                        paintable.default_tints(variant.groups))
            self.report.append((model.category, model.key, f"skaliert {factor:.3f}"))
        else:
            self.report.append((model.category, model.key, "nur Symbol, an der Drohne Form aus der Definition"))

    def battery(self, model, stages, cases, component):
        model.pack()
        if not display.fits(model):
            raise RuntimeError(f"{model.key}: Akku außerhalb der Modellgrenzen")
        display_json, pre = display.compute(model)
        self.out.texture(*model.texture_map()["0"])
        self.out.model(model, display_json)
        self.out.render_item(model)
        addon.write_paint(self.out, model, display_json)
        for stage in stages:
            stage.pack()
            self.out.texture(*stage.texture_map()["0"])
            self.out.model(stage, display_json)
        if self.icons:
            self.out.icon(model.key, display.component_item_definition(model.model_id, pre, component, cases))
        self.report.append((model.category, model.key, "modell"))

    def scaled_render_item(self, category, key, model_id, factor, tints=None):
        path = self.root / self.namespace / "items/drone_render" / category / f"{key}.json"
        kit.dump_json(path, {"model": display.scaled_render_node(model_id, factor, tints)})
        if path not in self.out.written:
            self.out.written.append(path)

    def motor(self, model, seat):
        self.simple(model)
        self.out.motor_seats[model.key] = seat

    def prop(self, key, built):
        cw, ccw, blur, damaged = built
        for model in [cw, ccw, blur] + [m for pair in damaged for m in pair]:
            if not display.fits(model):
                raise RuntimeError(f"{model.key}: Prop außerhalb der Modellgrenzen")
        addon.write_props(self.out, key, built, "model" if self.icons else "none")
        self.report.append(("prop", key, "modell"))

    def item(self, model, model_id, texture_id, key=None, display_json=None, pre=1.0):
        model.pack()
        model.override_id = model_id
        model.extra_textures = {"0": (texture_id, model.image)}
        if display_json is None:
            display_json, pre = display.compute(model)
        self.out.texture(texture_id, model.image)
        self.out.model(model, display_json)
        if key is not None and self.icons:
            self.out.icon(key, display.item_definition(model_id, pre))
        self.report.append(("item", key or model_id, "modell"))
        return display_json, pre

    def finish(self, merge=True):
        problems = paintable.check_references(self.out.paint_groups)
        if problems:
            raise RuntimeError("Lack-Referenzen zu hell: " + "; ".join(problems))
        base = self.root / self.namespace
        if self.out.paint_groups:
            fresh = paint_summary(self.out.paint_groups)
            if merge:
                merge_json(base / "drone_paint.json", fresh, "models")
            else:
                kit.dump_json(base / "drone_paint.json", fresh)
        if self.out.motor_seats:
            fresh = {"frames": {}, "motor_seat": dict(sorted(self.out.motor_seats.items()))}
            if merge:
                merge_json(base / "drone_layout.json", fresh, "motor_seat")
            else:
                kit.dump_json(base / "drone_layout.json", fresh)
        return self.report


def paint_summary(groups_by_model):
    groups = {}
    for name, entries in groups_by_model.items():
        kept = list(entries)
        while kept and kept[-1][0] in NO_PAINT:
            kept.pop()
        if any(channel in NO_PAINT for channel, _ in kept):
            raise RuntimeError(f"{name}: Lack-Ebene {NO_PAINT} vor anderen Ebenen, die Tint-Nummern würden sich verschieben")
        groups[name] = kept
    fresh = paintable.summary_json(groups)
    fresh["channels"] = {key: value for key, value in fresh["channels"].items() if key not in NO_PAINT}
    fresh["slots"] = [slot for slot in fresh["slots"] if slot not in NO_PAINT]
    return fresh


def owned_keys(category):
    folder = paths.PARTS / category
    return [file.stem for file in folder.glob("*.json")] if folder.is_dir() else []


def merge_json(path, fresh, section):
    data = {}
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
    for key, value in fresh.items():
        if key == section:
            merged = dict(data.get(key, {}))
            merged.update(value)
            data[key] = dict(sorted(merged.items()))
        elif key not in data:
            data[key] = value
    if section == "motor_seat":
        data.setdefault("frames", {})
    kit.dump_json(path, data)
