# Sharecodes (PW3) – Teile-Tabelle der Kamera-Drohnen

Seit Propwash 0.6.0 gehören Sharecodes Propwash (`/propwash code`, Format PW3). Das Add-on liefert nur seine
**Teile-Tabelle** `common/src/main/resources/data/camdrones/propwash_share/table.json`. Damit bekommen seine Teile kurze
Nummern im Code, und Propwash erkennt beim Import, ob der Code zur installierten Version passt, welche Version nötig ist
und wo man das Add-on bekommt. Die Anleitung für Add-ons steht im Propwash-Repo (`docs/sharecode/tables.md`).

## Inhalt

- `format` 1, `namespace` `camdrones`, noch ohne `short_id`: Kurzkennungen vergibt das offizielle Add-on-Verzeichnis auf
  propwashfpv.com; bis zur Aufnahme geht der Namensraum ausgeschrieben in den Code.
- 19 Einträge, je Art in der Reihenfolge, die `ShareTableCheck init` angelegt hat (alphabetisch), alle mit `since`
  `1.0.0`: 3 Rahmen, 2 Nav-FCs, 3 Motoren, 3 Props, 2 Kamera-Funk, 3 Smart-Akkus, 3 Gimbals. Objektive und Flugplaner
  sind Items, keine Teile, und stehen nicht in der Tabelle.
- Missionen gehören nicht zum Sharecode (Entwurf E16); sie liegen als Drohnen-Daten an der Drohne.

## Ersatzteile (`fallback`)

Öffnet jemand einen Code ohne das Add-on, ersetzt Propwash fehlende Teile durch ihren `fallback`, wenn der Bauplan damit
baubar ist, sonst durch ein Propwash-Preset.

| Art | Ersatz | Grund |
|---|---|---|
| Rahmen, Stack, Motor, Prop, Video, Akku | Teile des Propwash-Presets Long Range 7 (`long_range_7`, `stack_30`, `m2807_1300`, `p7_2b`, `digital_hd`, `b6s_4000_lion`) | eine Kamera-Drohne ohne Gimbal wird so zu einem baubaren Long-Range-Quad mit GPS; die Einzelvorschläge des Werkzeugs (14xx-Motoren, 20×20-Stack, 5″-Props im 7″-Rahmen) passten nicht zusammen |
| Gimbal Kompakt, Tour | `action_cam_light` | Propwash hat kein eingebautes Zubehör für die Halterung vorn |
| Kino-Gimbal | `action_cam_heavy` | stabilisierte Action-Cam, am nächsten am Gimbal |

Weil keine Propwash-Action-Cam vorn passt, scheitert der Ersatz einer Drohne mit Gimbal am Platz des Zubehörs; Propwash
nimmt dann ein eigenes Preset.

## Regeln

- Einträge kommen **nur hinten** dazu: nie umsortieren, nie löschen, nie eine Nummer wiederverwenden. Ein umbenanntes Teil
  bekommt einen neuen Eintrag am Ende, der alte `deprecated` und `replaced_by`.
- Einträge mit `since` gleich der Version, an der gerade gearbeitet wird, sind unveröffentlicht und dürfen noch umgestellt
  werden. Mit dem Release 1.0.0 ist die Reihenfolge eingefroren.
- Neues Teil: JSON unter `data/camdrones/propwash_parts/<art>/` anlegen, dann

  ```sh
  java -cp <propwash-common-jar>:<gson-jar> de.lardtom.propwash.api.share.ShareTableCheck add \
      --resources common/src/main/resources --version <neue version>
  ```

  und den vorgeschlagenen `fallback` prüfen.
- `./gradlew :common:checkShareTable` (läuft in jedem `build`) prüft Tabelle und Teile, nach dem ersten Release auch gegen
  `common/sharecode-baseline.json`. Die Vergleichsdatei entsteht nach jedem Release mit
  `ShareTableCheck freeze --resources common/src/main/resources --out common/sharecode-baseline.json`.
- Der Web-Export enthält die Tabelle als `camdrones/share_table.json` mit Prüfsumme (`table_crc` im Manifest);
  `tools/web_export.py` vergleicht sie mit der Datei im Repo.
