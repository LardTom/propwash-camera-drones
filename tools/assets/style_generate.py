import argparse
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from camstyle import check, extras, paths, sounds, templates, writer


def generate(out, categories, only, icons=True, with_sounds=True, wipe=False):
    w = writer.Writer(out, icons=icons)
    full = not only and set(categories) == set(templates.CATEGORIES)
    if wipe and full:
        w.wipe()
    parts = templates.load(paths.PARTS, categories, set(only) if only else None)
    for category, key, definition in parts:
        if not full:
            w.clean(category, key)
        templates.build_into(w, category, key, definition)
    if full or only:
        extras.build_all(w, with_sounds=with_sounds and full, only=set(only) if only else None)
    report = w.finish(merge=not full)
    return w, parts, report, full


def print_problems(problems):
    for line in problems:
        print(line)
    print(f"STIL-PRÜFUNG {'OK' if not problems else f'{len(problems)} Befunde'}")
    return 1 if problems else 0


def main(argv=None):
    parser = argparse.ArgumentParser(description="Erzeugt Modelle, Texturen, Item-Symbole, Lack-Varianten und Sounds der "
                                                 "Kamera-Drohnen im Propwash-Stil (Stil-API camstyle).")
    parser.add_argument("--category", action="append", choices=templates.CATEGORIES,
                        help="nur diese Teile-Kategorie (mehrfach möglich), Standard: alle samt Objektiven, Tablet, "
                             "Landeplatz und Sounds")
    parser.add_argument("--only", action="append", help="nur diese Id (Teil, lens_*, flight_planner, landing_pad)")
    parser.add_argument("--out", default=str(paths.PREVIEW_ASSETS),
                        help="Ziel-Ordner assets (Standard: build/style-preview/assets, --write: Mod-Ressourcen)")
    parser.add_argument("--write", action="store_true", help="in common/src/main/resources/assets schreiben")
    parser.add_argument("--no-sounds", action="store_true", help="Sounds nicht erzeugen (braucht soundfile mit Vorbis)")
    parser.add_argument("--check-only", metavar="ORDNER", help="nichts erzeugen, nur einen assets-Ordner prüfen")
    parser.add_argument("--verify", action="store_true",
                        help="neu in einen Zwischenordner erzeugen, Byte für Byte mit den Mod-Ressourcen vergleichen "
                             "und die Ressourcen prüfen")
    args = parser.parse_args(argv)
    with_sounds = not args.no_sounds
    if args.check_only:
        return print_problems(check.vendored_intact() + check.check(args.check_only, with_sounds=with_sounds))
    if args.verify:
        with tempfile.TemporaryDirectory(prefix="camstyle-") as tmp:
            generate(Path(tmp), templates.CATEGORIES, None, with_sounds=with_sounds and sounds.available(), wipe=True)
            problems = check.vendored_intact()
            folders = ("models", "textures", "items", "sounds") if with_sounds else ("models", "textures", "items")
            files = ("sounds.json", "drone_paint.json", "drone_layout.json") if with_sounds else ("drone_paint.json", "drone_layout.json")
            problems += check.same_tree(tmp, paths.ASSETS_ROOT, folders=folders, files=files)
            problems += [f"frisch erzeugt: {line}" for line in check.check_front_fit(tmp)]
            problems += check.check(paths.ASSETS_ROOT, with_sounds=with_sounds)
            print(f"{paths.NS}: Mod-Ressourcen gegen frisch erzeugte Dateien verglichen")
            return print_problems(problems)
    out = paths.ASSETS_ROOT if args.write else Path(args.out)
    categories = tuple(args.category) if args.category else templates.CATEGORIES
    w, parts, report, full = generate(out, categories, args.only, with_sounds=with_sounds, wipe=True)
    for category, key, note in report:
        if note != "modell":
            print(f"{category}/{key}: {note}")
    print(f"{paths.NS}: {len(parts)} Teile und {len(report) - len(parts)} weitere Einträge im Propwash-Stil nach {out} "
          f"({len(w.out.written)} Dateien, {len(w.out.paint_groups)} Lack-Varianten)")
    problems = check.vendored_intact() + check.check(out, with_sounds=with_sounds and sounds.available(), full=full)
    return print_problems(problems)


if __name__ == "__main__":
    sys.exit(main())
