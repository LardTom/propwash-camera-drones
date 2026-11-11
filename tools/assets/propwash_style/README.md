# propwash_style — unveränderte Kopie des Propwash-Teilegenerators

Quelle: **Propwash FPV 0.6.0 in Entwicklung**, Ordner `tools/assets/parts` auf dem Integrationszweig `integration` (Stand
`2b3ce3c5` vom 2026-10-19). Die Dateien sind byte-gleich mit dem Stand von `0.6.0-dev.2` (`4e405378`, gegen den das
Add-on baut), bis auf `stacks.py`: dort kam danach mit `2aa4ffda` (2026-10-18) die Variante `_mounted` für Stacks mit
Stecker dazu (gesteckter Akku, Propwash `docs/ASSETS.md` „Akku gesteckt“). Die Kamera-Drohnen nutzen sie nicht: Nav-FCs
und Smart-Akkus haben keine Stecker-Elemente, Propwash zeichnet deshalb kein Kabel.

Letzte Änderung je Datei in der Quelle:

| Datei | Commit | Datum |
|---|---|---|
| `accessories.py` | `60b12f73` | 2026-10-03 |
| `addon.py` | `4bbcbb0a` | 2026-10-08 |
| `addon_preview.py` | `66fbf79e` | 2026-09-29 |
| `batteries.py` | `287b6ff6` | 2026-10-09 |
| `ducts.py` | `4bbcbb0a` | 2026-10-08 |
| `frames.py` | `287b6ff6` | 2026-10-09 |
| `kit.py` | `8ef51fb5` | 2026-10-06 |
| `layout.py` | `c1c1806c` | 2026-10-14 |
| `motors.py` | `4fc1597b` | 2026-09-24 |
| `paint.py` | `82c7d303` | 2026-09-24 |
| `paintable.py` | `287b6ff6` | 2026-10-09 |
| `props.py` | `4dda8ba3` | 2026-10-11 |
| `render.py` | `dcce1438` | 2026-09-24 |
| `stacks.py` | `2aa4ffda` | 2026-10-18 |
| `video.py` | `81c0d27a` | 2026-09-23 |

Die Dateien sind **byte-gleich** mit der Quelle (Prüfsummen in `SHA256SUMS`, geprüft von
`tools/assets/style_generate.py` bei jedem Lauf). Hier wird nichts geändert: Regeln der Kamera-Drohnen stehen
ausschließlich in `tools/assets/camstyle/` (Stil-API, siehe `tools/assets/README.md`).

| Datei | Inhalt |
|---|---|
| `kit.py` | Modell-Baukasten: `Model`, `box`, `cbox`, `bar`, `cylinder` (Achteck aus zwei um 45° versetzten Kastensätzen), `cylinder_x`, `ring`, Textur-Packer (64 × 64, Dichte in Texel je 8 mm), `compute_display` (Anzeige-Transformationen aller Item-Kontexte), `item_definition`, JSON- und Blockbench-Ausgabe |
| `paint.py` | Palette und Maler: `carbon`, `alu`, `plastic`, `prop_material`, `shrink`, `wire`, `heatsink`, Schrift, Rauschen, Kanten |
| `paintable.py` | Lack-Varianten (`_paint`), Kanäle und Referenz-Grau, `drone_paint.json` |
| `motors.py`, `props.py`, `batteries.py`, `stacks.py`, `frames.py`, `video.py`, `accessories.py`, `ducts.py` | Vorlagen und Teile der eingebauten Kategorien |
| `layout.py` | Rahmen-Layout und Sitzhöhen der eingebauten Teile |
| `render.py` | Software-Rasterer der Vorschauen (nutzen `tools/assets/preview.py` und `compare_render.py`) |
| `addon.py`, `addon_preview.py` | Addon-Generator der Basis (Referenz; die Kamera-Drohnen nutzen daraus `Output`, `write_paint`, `write_props`, `register_materials`) |

Nicht kopiert: `generate.py`, `preview.py`, `paint_preview.py`, `validate.py`, `part_bases.py` und `addon_example/`
(schreiben oder prüfen nur Propwash-eigene Ressourcen).

## Aktualisieren

1. Die Dateien der Tabelle aus dem neuen Propwash-Stand unverändert hierher kopieren.
2. `shasum -a 256 *.py > SHA256SUMS` in diesem Ordner, Quelle und Stand oben anpassen.
3. `python3 tools/assets/style_generate.py --write`, danach `--verify`, `python3 tools/assets/preview.py` und
   `python3 tools/assets/compare_render.py` laufen lassen und die Bilder ansehen; brechen Aufrufe aus `camstyle`, dort
   anpassen (nie hier).

Benötigt ein `python3` mit numpy und Pillow (wie der Generator in Propwash), für die Sounds zusätzlich `soundfile` mit
Ogg-Vorbis.
