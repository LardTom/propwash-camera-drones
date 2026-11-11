import copy
import math

import numpy as np

import kit

MODEL_MIN = -16.0
MODEL_MAX = 32.0
ICON_PREFIX = "item/icon"

GUI = {
    "motor": ((25, 225, 0), 14.0),
    "prop": ((58, 225, 0), 15.5),
    "battery": ((30, 225, 0), 15.0),
    "stack": ((50, 225, 0), 15.0),
    "video": ((20, 205, 0), 15.0),
    "frame": ((40, 200, 0), 15.0),
    "accessory": ((20, 205, 0), 15.0),
    "lens": ((20, 205, 0), 14.0),
}


def gui(kind):
    return GUI[kind]


def compute(model):
    return kit.compute_display(model)


def item_definition(model_id, pre, extra_cases=None, stages=None):
    return kit.item_definition(model_id, pre, extra_cases, stages=stages)


def component_item_definition(model_id, pre, component, cases):
    data = kit.item_definition(model_id, pre)
    plain = dict(data["model"]["fallback"])
    transformation = plain.pop("transformation", None)
    node = {"type": "minecraft:select", "property": "minecraft:component", "component": component,
            "cases": [{"when": values, "model": kit.model_node(mid)} for values, mid in cases], "fallback": plain}
    if transformation is not None:
        node["transformation"] = transformation
    data["model"]["fallback"] = node
    return data


def fits(model):
    for el in model.elements:
        for value in list(el.frm) + list(el.to):
            if value < MODEL_MIN or value > MODEL_MAX:
                return False
    return True


def reach(model):
    worst = 0.0
    for el in model.elements:
        for value in list(el.frm) + list(el.to):
            worst = max(worst, abs(value - kit.ANCHOR))
    return worst


def shrink_factor(model, margin=0.25):
    limit = min(kit.ANCHOR - MODEL_MIN, MODEL_MAX - kit.ANCHOR) - margin
    worst = reach(model)
    return 1.0 if worst <= limit else limit / worst


def scaled_copy(model, factor, model_id):
    out = kit.Model(model.category, model.key, tex=model.tex, density=model.density, gui=model.gui,
                    gui_fill=model.gui_fill, translucent=model.translucent, seed=model.seed)
    anchor = np.full(3, kit.ANCHOR)
    for el in model.elements:
        frm = (np.array(el.frm, dtype=float) - anchor) * factor + anchor
        to = (np.array(el.to, dtype=float) - anchor) * factor + anchor
        rotation = None
        if el.rotation:
            origin, rx, ry, rz = el.rotation
            o = (np.array(origin, dtype=float) - anchor) * factor + anchor
            rotation = ([round(v, 4) for v in o], rx, ry, rz)
        faces = {d: copy.copy(fs) for d, fs in el.faces.items()}
        out.elements.append(kit.Element(el.name, [round(v, 4) for v in frm], [round(v, 4) for v in to], faces, rotation))
    out.extra_textures = dict(model.texture_map())
    out.image = model.image
    out.override_id = model_id
    return out


def render_factor(model):
    factor = shrink_factor(model)
    if factor >= 1.0:
        return 1.0
    return 1.0 / (math.ceil(100.0 / factor) / 100.0)


def icon_model(model):
    factor = shrink_factor(model)
    if factor >= 1.0:
        return None, 1.0
    model_id = f"{kit.NAMESPACE}:{ICON_PREFIX}/{model.category}/{model.key}"
    return scaled_copy(model, factor, model_id), factor


def scaled_render_node(model_id, factor, tints=None):
    k = 1.0 / factor
    t = round(0.5 - 0.5 * k, 5)
    node = kit.model_node(model_id, tints)
    node["transformation"] = {"left_rotation": [0.0, 0.0, 0.0, 1.0], "right_rotation": [0.0, 0.0, 0.0, 1.0],
                              "scale": [round(k, 5)] * 3, "translation": [t, t, t]}
    return node
