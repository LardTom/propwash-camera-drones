#!/usr/bin/env python3
import argparse
import json
import re
import sys
from pathlib import Path

NAMESPACE = "camdrones"
KINDS = ("frame", "stack", "motor", "prop", "video", "battery", "accessory")
LANGUAGES = ("de_de", "en_us")
CLASS_PATTERN = re.compile(
    r"^\s*([A-Z]+)\(\"([a-z]+)\",\s*(\d+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*(\d+),\s*(\d+),",
    re.MULTILINE,
)


def load(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def properties(root):
    values = {}
    for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def flight_classes(root):
    source = root / "common/src/main/java/de/lardtom/camdrones/logic/nav/FlightClass.java"
    classes = {}
    for match in CLASS_PATTERN.finditer(source.read_text(encoding="utf-8")):
        classes[match.group(2)] = {
            "grams": int(match.group(3)),
            "sport_ms": float(match.group(6)),
            "hover_min": int(match.group(9)),
            "hover_max": int(match.group(10)),
        }
    return classes


def own_parts(root):
    base = root / "common/src/main/resources/data" / NAMESPACE / "propwash_parts"
    parts = {}
    for kind in KINDS:
        folder = base / kind
        parts[kind] = sorted(p.stem for p in folder.glob("*.json")) if folder.is_dir() else []
    return parts


def check(root, export, loader):
    errors = []
    notes = []
    props = properties(root)
    version = props.get("version")
    manifest = load(export / "manifest.json")
    if manifest.get("format") != 2:
        errors.append(f"manifest.json: format {manifest.get('format')} statt 2")
    if NAMESPACE not in manifest.get("namespaces", []):
        errors.append(f"manifest.json: Namensraum {NAMESPACE} fehlt in namespaces")
        return errors, notes
    if manifest.get("mods", {}).get(NAMESPACE) != version:
        errors.append(f"manifest.json: mods.{NAMESPACE} = {manifest.get('mods', {}).get(NAMESPACE)}, erwartet {version}")
    loader = loader or manifest.get("loader")
    notes.append(f"Propwash {manifest.get('propwash')} (API {manifest.get('propwash_api')}), Loader {loader}, Auswahl {manifest.get('selection')}")

    problems = load(export / "problems.json")
    for problem in problems:
        text = f"{problem.get('level')}: {problem.get('where')}: {problem.get('message')}"
        if problem.get("level") == "error" or NAMESPACE in json.dumps(problem):
            errors.append(f"problems.json: {text}")
        else:
            notes.append(f"Hinweis außerhalb des Add-ons: {text}")

    folder = export / NAMESPACE
    own = load(folder / "manifest.json")
    expected = own_parts(root)
    total = sum(len(ids) for ids in expected.values())
    for kind in KINDS:
        if own.get("counts", {}).get(kind) != len(expected[kind]):
            errors.append(f"{NAMESPACE}/manifest.json: counts.{kind} = {own.get('counts', {}).get(kind)}, im Repo {len(expected[kind])}")
    if own.get("table_source") != "mod":
        errors.append(f"{NAMESPACE}/manifest.json: table_source {own.get('table_source')} statt mod (Teile-Tabelle nicht geladen)")
    if own.get("table_entries") != total:
        errors.append(f"{NAMESPACE}/manifest.json: table_entries {own.get('table_entries')}, Teile im Repo {total}")
    range_key = "propwash_fabric_range" if loader == "fabric" else "propwash_version_range"
    wanted_range = props.get(range_key)
    if own.get("requires", {}).get("propwash") != wanted_range:
        errors.append(f"{NAMESPACE}/manifest.json: requires.propwash {own.get('requires', {}).get('propwash')}, gradle.properties {range_key}={wanted_range}")

    table_file = root / "common/src/main/resources/data" / NAMESPACE / "propwash_share/table.json"
    table = load(table_file)
    exported_table = folder / "share_table.json"
    if not exported_table.is_file():
        errors.append(f"{NAMESPACE}/share_table.json fehlt")
    elif load(exported_table) != table:
        errors.append(f"{NAMESPACE}/share_table.json weicht von {table_file.relative_to(root)} ab")
    if table.get("short_id") != own.get("short_id"):
        errors.append(f"{NAMESPACE}/manifest.json: short_id {own.get('short_id')}, Tabelle {table.get('short_id')}")

    parts = load(folder / "parts.json")
    by_id = {part["id"]: part for part in parts}
    for kind in KINDS:
        for path in expected[kind]:
            part = by_id.get(f"{NAMESPACE}:{path}")
            if part is None:
                errors.append(f"{NAMESPACE}/parts.json: {NAMESPACE}:{path} fehlt")
                continue
            if part.get("category") != kind:
                errors.append(f"{NAMESPACE}/parts.json: {NAMESPACE}:{path} hat Kategorie {part.get('category')} statt {kind}")
            for language in LANGUAGES:
                if not (part.get("name") or {}).get(language):
                    errors.append(f"{NAMESPACE}/parts.json: {NAMESPACE}:{path} ohne Namen in {language}")
            for asset in asset_paths(part.get("model")):
                if not (folder / asset).is_file():
                    errors.append(f"{NAMESPACE}/parts.json: {NAMESPACE}:{path} verweist auf fehlende Datei {asset}")
    extra = sorted(set(by_id) - {f"{NAMESPACE}:{p}" for ids in expected.values() for p in ids})
    if extra:
        errors.append(f"{NAMESPACE}/parts.json: Teile ohne Datei im Repo: {extra}")

    classes = flight_classes(root)
    presets = load(folder / "presets.json")
    if len(presets) != len(classes):
        errors.append(f"{NAMESPACE}/presets.json: {len(presets)} Presets, Flugklassen {len(classes)}")
    for preset in presets:
        pid = preset["id"]
        path = pid.split(":", 1)[1]
        code = preset.get("sharecode") or ""
        if not code.startswith("PW3-"):
            errors.append(f"{NAMESPACE}/presets.json: {pid} ohne PW3-Code")
        check_result = preset.get("check") or {}
        if check_result.get("problems") or check_result.get("warnings"):
            errors.append(f"{NAMESPACE}/presets.json: {pid} nicht sauber baubar: {check_result}")
        if not (folder / "analysis" / f"{path}.json").is_file():
            errors.append(f"{NAMESPACE}/analysis/{path}.json fehlt")
        analysis = preset.get("analysis") or {}
        hover = analysis.get("hover_flight_time_min")
        top = analysis.get("top_speed_kmh")
        grams = analysis.get("mass_grams")
        flight_class = classes.get(path)
        if flight_class is None:
            errors.append(f"{NAMESPACE}/presets.json: {pid} ohne Flugklasse in FlightClass")
        elif hover is None or top is None:
            errors.append(f"{NAMESPACE}/presets.json: {pid} ohne Schwebezeit oder Höchsttempo")
        else:
            if not flight_class["hover_min"] <= hover <= flight_class["hover_max"]:
                errors.append(f"{NAMESPACE}/presets.json: {pid} schwebt {hover:.1f} min, Klasse {flight_class['hover_min']}–{flight_class['hover_max']} min")
            if top / 3.6 < flight_class["sport_ms"]:
                errors.append(f"{NAMESPACE}/presets.json: {pid} Höchsttempo {top / 3.6:.1f} m/s unter Sport {flight_class['sport_ms']:.0f} m/s")
            notes.append(f"{pid}: {grams:.0f} g, Schweben {hover:.1f} min, Höchsttempo {top:.0f} km/h ({top / 3.6:.1f} m/s), Code {len(code)} Zeichen")

    for language in LANGUAGES:
        lang = load(folder / "lang" / f"{language}.json")
        for kind in KINDS:
            for path in expected[kind]:
                if not lang.get(f"part.{NAMESPACE}.{path}"):
                    errors.append(f"{NAMESPACE}/lang/{language}.json: part.{NAMESPACE}.{path} fehlt")
    notes.append(f"{total} Teile, {len(presets)} Presets, Tabelle {own.get('table_entries')} Einträge, CRC {own.get('table_crc')}, {own.get('counts', {}).get('asset_files')} Asset-Dateien")
    return errors, notes


def asset_paths(model):
    if not isinstance(model, dict):
        return []
    found = []
    for key in ("item", "drone"):
        value = model.get(key)
        if isinstance(value, str):
            found.append(value)
    for key in ("variants", "textures"):
        value = model.get(key)
        if isinstance(value, dict):
            found.extend(v for v in value.values() if isinstance(v, str))
        elif isinstance(value, list):
            found.extend(v for v in value if isinstance(v, str))
    return [path for path in found if path.startswith("assets/")]


def main():
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="Prüft den Web-Export des Kamera-Drohnen-Add-ons (Format 2) gegen das Repo.")
    parser.add_argument("export", nargs="?", default=str(root / "build/web-export"), help="Ordner des Web-Exports (Standard build/web-export)")
    parser.add_argument("--loader", choices=("fabric", "neoforge"), help="Loader des Exports (Standard aus manifest.json)")
    args = parser.parse_args()
    export = Path(args.export)
    if not (export / "manifest.json").is_file():
        print(f"web_export: FAIL kein manifest.json in {export} (zuerst ./gradlew :fabric:runWebExport)")
        return 2
    errors, notes = check(root, export, args.loader)
    for note in notes:
        print(f"  {note}")
    for error in errors:
        print(f"  FEHLER {error}")
    if errors:
        print(f"web_export: FAIL {len(errors)} Befunde in {export}")
        return 1
    print(f"web_export: PASS {export}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
