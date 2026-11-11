from . import lens, pad, planner, sounds

LENS_KEYS = tuple(lens.LENSES)
ITEM_KEYS = LENS_KEYS + (planner.KEY, pad.KEY)


def build_lenses(writer, only=None):
    for key in LENS_KEYS:
        if only and key not in only:
            continue
        model = lens.build(key)
        model_id, texture_id = lens.ids(key)
        writer.item(model, model_id, texture_id, key=key)


def build_planner(writer):
    model = planner.build()
    model_id = f"{writer.namespace}:item/{planner.KEY}"
    writer.item(model, model_id, model_id, key=planner.KEY, display_json=planner.display_json())


def build_pad(writer):
    ns = writer.namespace
    for name, img in pad.textures().items():
        writer.out.texture(f"{ns}:block/{pad.KEY}_{name}", img)
    writer.out.raw_model(f"{ns}:block/{pad.KEY}", pad.model_json(ns))
    writer.out.icon(pad.KEY, pad.item_json(ns))
    writer.report.append(("item", pad.KEY, "Blockmodell"))


def build_sounds(writer):
    if not sounds.available():
        writer.report.append(("sound", "-", "soundfile mit Vorbis fehlt, Sounds nicht erzeugt"))
        return False
    for rel, name in sounds.files():
        writer.out.binary(rel, sounds.encode(sounds.samples(name)))
    writer.out.json("sounds.json", sounds.sounds_json(writer.namespace))
    writer.report.append(("sound", "-", f"{len(sounds.EVENTS)} Sounds"))
    return True


def build_all(writer, with_sounds=True, only=None):
    build_lenses(writer, only)
    if not only or planner.KEY in only:
        build_planner(writer)
    if not only or pad.KEY in only:
        build_pad(writer)
    if with_sounds and not only:
        build_sounds(writer)
