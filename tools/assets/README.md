# Assets der Kamera-Drohnen (Stil-API `camstyle`)

Alle Modelle, Texturen, Item-Definitionen, Lack-Varianten und Sounds unter `common/src/main/resources/assets/camdrones/`
kommen aus diesem Ordner. Nichts davon ist von Hand gezeichnet, aus Vanilla oder aus anderen Mods kopiert: Texturen malt
der Generator mit den Malern der Basis, Sounds synthetisiert er. `style_generate.py --verify` erzeugt alles neu und
vergleicht Byte für Byte mit den Ressourcen; jede fremde oder von Hand geänderte Datei ist ein Befund.

| Pfad | Inhalt |
|---|---|
| `propwash_style/` | unveränderte Kopie des Propwash-Teilegenerators (`README.md` mit Quelle und Stand, `SHA256SUMS`) |
| `camstyle/` | Stil-API der Kamera-Drohnen auf dieser Kopie (Tabelle unten) |
| `style_generate.py` | erzeugt alles aus `data/camdrones/propwash_parts/**` plus Objektive, Tablet, Landeplatz und Sounds; prüft danach |
| `preview.py` | Vorschaubilder: jedes Teil aus vier Richtungen und die drei Presets zusammengesetzt |
| `compare_render.py` | Vergleichsbilder gegen Propwash je Kategorie (GUI, Hände, dritte Person, Boden, Item-Rahmen, gemeinsamer Maßstab) |

## Aufrufe

```sh
python3 tools/assets/style_generate.py               # Vorschau nach build/style-preview/assets, mit Prüfung
python3 tools/assets/style_generate.py --write       # in die Mod-Ressourcen (löscht vorher die eigenen Ordner)
python3 tools/assets/style_generate.py --verify      # Ressourcen = Generator? plus Prüfung, ändert nichts
python3 tools/assets/style_generate.py --check-only common/src/main/resources/assets
python3 tools/assets/preview.py                      # build/style-preview/parts.png, presets.png
python3 tools/assets/compare_render.py               # build/style-compare/compare_<kategorie>.png
```

Benötigt `python3` mit numpy und Pillow, für die Sounds `soundfile` mit Ogg-Vorbis (libsndfile). Ohne `soundfile` lässt
der Generator die Sounds weg und meldet das; `--no-sounds` schaltet sie bewusst ab. Die Vergleichsbilder lesen die
Propwash-Assets aus dem Jar in mavenLocal (Nummer aus `gradle.properties`), sonst `--propwash-assets`.

## `camstyle`

| Modul | Inhalt |
|---|---|
| `frame` | Rumpfschale mit Faltarmen, Gelenken und Motorgondeln; Füße unter den Gondeln (Kompakt, Tour), Landebeine mit Kufen (Kino) |
| `motor` | Motoren über `motors.build_motor` der Basis (Maße aus dem Stator, Glocke in `visual.accent`) |
| `prop` | Falt-Props: Nabe mit zwei Blattgriffen und Gelenkschrauben, schlanke Blätter aus den Bausteinen der Basis, Spitzen in der Add-on-Akzentfarbe; `_ccw`, `_blur`, Schadensstufen wie die Basis |
| `battery` | Smart-Akku: geschlossene Schale, Kontakte vorn, vier LED-Punkte und Taste oben hinten und hinten, Zellband seitlich, Griffrillen, Rastknöpfe; Ladeanzeige über die Modelle `_led0` bis `_led3` (Abschnitt unten) |
| `stack` | Nav-FC: Platine (AIO oder Turm), Abschirmblech, GPS-Patch, Kompass, Status-LED, Sensoranschluss |
| `video` | Kamera-Funk: Nav-Kamera vorn, flaches Funkmodul mit zwei Antennen (nur im Inventar-Symbol: an der Drohne lässt Propwash Elemente `antenna*` aus, die Teile haben `"antenna": "none"`, also auch keine Propwash-Antennen) |
| `gimbal` | 3-Achsen-Gimbal (Halterung vorn): Dämpferplatte, Schwenkmotor mit Sperrring, Rollmotor, Neigemotor, Kamerakopf `gimbal_head`; Kino mit zweiseitigem Bügel und Objektiv-Fach |
| `lens` | Objektive `lens_24`, `lens_50`, `lens_85` (Items) |
| `planner` | Tablet „Flugplaner“ in der Hand (`flight_planner`), Bildschirm mit Karte und Wegpunkten |
| `pad` | Landeplatz (`landing_pad`): Blockmodell mit H-Markierung (Block und Blockzustände kommen später mit dem Block) |
| `sounds` | acht Sounds, synthetisiert (Mono, 48 kHz, Ogg Vorbis, feste Stream-Nummer, damit die Dateien reproduzierbar sind) |
| `display`, `writer`, `templates`, `extras`, `decals`, `palette`, `common`, `paths` | Anzeige-Transformationen, Ausgabe, Zuordnung Teil → Vorlage, Malereien, Farbregeln |
| `check` | Stil- und Modellprüfung (unten) |
| `itemview` | Nachbildung der Vanilla-Item-Kontexte für die Vergleichsbilder (wie in Just More Parts) |

## Stilregeln

- Maßstab und Anbaupunkte wie Propwash (`docs/ASSETS.md` der Basis): 1 Modell-Einheit = 8 mm, Anbaupunkt (8, 8, 8), oben
  +Y, Nase −Z. Jedes Drohnen-Modell zeigt die echte Größe; der Kino-Rahmen liegt außerhalb der Minecraft-Grenzen und wird
  wie die X-Class-Rahmen in Just More Parts verkleinert gespeichert und über `transformation` der Render-Definition
  (Faktor 0,578) auf echte Größe gebracht.
- Nur `kit.Model` mit Textur 64 × 64; runde Teile sind Achtecke aus `kit.cylinder`/`cylinder_x`. Texeldichte nach Größe
  wie die Basis (der Packer der Basis verringert sie, bis alles passt): Rahmen 3,2/2,6/1,6 Texel je 8 mm nach Radstand,
  große Schalenflächen mit 0,6–0,7 der Dichte; Motoren 5/4/3, Props 3,6/2,6, Akkus 300 / längste Kante, Gimbals
  330 / größte Kante (3–10), Nav-FC 8 bzw. 5,5, Kamera-Funk 10 bzw. 7.
- Schattierung, Kanten und Rauschen nur aus den Malern der Basis (`paint.plastic`, `alu`, `prop_material`, …).
- Palette: Schale und Teile in `visual.accent` der Definition (Kompakt Hellgrau `#c9cdd2`, Tour Dunkelgrau `#5a6068`,
  Kino Schwarz `#1b1d20`), abgedunkelt auf die Lack-Referenz des Kanals; Akzentfarbe des Add-ons `#2ab3d4` nur für die
  Gimbal-Sperre, die Prop-Spitzen, Ringe der Motorgondeln, Armlichter, Kufen-Enden und Markierungen. Keine Logos, keine
  Herstellernamen; einzige Beschriftung ist das Typenschild „LARDTOM“ am Heck, wo die Auflösung reicht.
- Anzeige-Transformationen aller Teile und Objektive aus `kit.compute_display` der Basis (GUI, Boden, Item-Rahmen, Regal,
  Hände nach echter Größe, Kopf); das Tablet hat feste Werte nach dem Vorbild der Funke, in der dritten Person hält
  die Hand es an der Ecke quer vor dem Bauch, 55° geneigt, Bildschirm zum Spieler (Selbsttest 612), der Landeplatz die
  Vanilla-Blockwerte.
- Lackierbar wie Propwash: Schale, Arme und Gondeln im Kanal `frame`, Akzentteile im Kanal `tpu`, Motoren `motor`,
  Props `prop`, Akkus `battery`, Gimbal `accessory`, Kamera `camera` (`drone_paint.json`). Den Kanal `antenna` schreibt
  der Generator nicht (`writer.NO_PAINT`): Kamera-Drohnen haben keine Antennen und keinen Lack-Platz „Antennenspitzen“;
  die Antennen-Ebene am Ende einer Lack-Variante fällt weg, ihr Tint bleibt beim Standard (Texturfarbe).

## Gimbal-Kopf für `GimbalProvider#modelPose`

Jedes Gimbal-Modell (`models/part/accessory/gimbal_*.json` samt `_paint`) hat **genau ein** Element `gimbal_head`: den
Kamerakopf mit dem Objektiv auf der Vorderseite (−Z). Sein Drehpunkt ist `rotation.origin` (Winkel 0, Achse x) und liegt
in der Mitte des Elements, auf der Neigeachse: Kompakt (0, −18, −19,2) mm, Tour (0, −26,4, −27,6) mm, Kino
(0, −75, −66) mm, jeweils relativ zum Anbaupunkt vorn. Propwash dreht dieses Element beim Zeichnen um diesen Punkt;
alles andere (Dämpferplatte, Motoren, Bügel) bleibt starr.

## Ladeanzeige des Smart-Akkus

Die vier LED-Punkte zeigen im Inventar, in der Hand, am Boden und im Rahmen den Ladestand: eine LED je angefangenem
Viertel (leer 0, bis 25 % 1, bis 50 % 2, bis 75 % 3, darüber 4). Das volle Modell `part/battery/<id>` bleibt das Modell
an der Drohne und für die Lackierung; dazu erzeugt `battery.led_stages` je Akku die Modelle `<id>_led0` bis `<id>_led3`
mit eigener Textur, die sich nur in den LED-Punkten unterscheidet. `items/<id>.json` wählt außerhalb des Kontexts `none`
mit `minecraft:select`, Eigenschaft `minecraft:component`, nach Propwashs stabiler Komponente `propwash:battery_charge`
(`float` 0…1, fehlt = voll). Propwash speichert den Ladestand auf 1/1000 gerundet und ab 0,999 als 1,0, deshalb listet
jede Stufe ihre Werte (0,000 bis 0,750) einzeln; alles andere, auch ein voller oder per Befehl ungerundet gesetzter
Ladestand, zeigt vier LEDs. An der Drohne zeichnet Propwash die Render-Definition ohne den Akku-Stapel, dort leuchten
immer alle vier.

## Prüfung (`camstyle/check.py`)

- Kopie der Basis unverändert (`SHA256SUMS`), keine Datei ohne Prüfsumme.
- Modelle: Elemente innerhalb −16…32, nur eigene Texturen (Namensraum `camdrones`), Texturen quadratisch 32/64
  (Blöcke 16/32), alle Anzeige-Kontexte, Skalen ≤ 4, `gui_light: side`, Eltern nur `minecraft:` oder eigene.
- Items: jedes referenzierte Modell vorhanden, nur eigene Modelle.
- Je Teil-Definition (wie der Web-Export der Basis sie sucht): `items/<id>.json`, `items/drone_render/<kategorie>/<id>.json`,
  `models/part/<kategorie>/<id>.json`, Props mit `_ccw`, `_blur` und allen Schadensstufen, Motoren mit `motor_seat`,
  Lack-Eintrag in `drone_paint.json` mit Render-Definition `_paint`, Lack-Referenzen nicht zu hell.
- Gimbals: genau ein `gimbal_head` mit Drehpunkt in der Mitte, im Modell ungedreht.
- Zubehör vorne: an jedem Rahmen mit Anker vorne sitzen die Quader 62, 34, 12, 44 und 30 mm sowie alle Gimbals
  (`size_mm`) am gezeichneten Modell (Render-Definition samt `transformation`), gemessen wie JUnit
  `part.FrontAnchorFitTest`: Abstand höchstens 1 mm, Überschneidung höchstens 2 mm. `--verify` misst auch die frisch
  erzeugten Modelle („frisch erzeugt: …“).
- Objektive, Tablet und Landeplatz vorhanden; keine Textur, die kein Modell benutzt.
- Sounds: Ereignisse wie `camstyle/sounds.py`, Dateien vorhanden, Ogg Vorbis Mono 48 kHz, Untertitel in Deutsch und
  Englisch (`common/src/lang/assets/`), keine Datei ohne Ereignis.
- `--verify`: Ressourcen Byte für Byte gleich einem frischen Lauf.

## Sounds

| Ereignis | Klang |
|---|---|
| `camdrones:planner.click` | kurzer Klick (Tablet) |
| `camdrones:planner.waypoint` | zwei steigende Töne |
| `camdrones:mission.start` | drei steigende Töne |
| `camdrones:mission.done` | zwei fallende Töne und ein hoher Abschluss |
| `camdrones:gimbal.calibrate` | Servo-Surren in vier Schritten (Abklingen nach 12 Blöcken) |
| `camdrones:warning.obstacle` | einzelner Piepton 75 ms (die Rate nach Abstand bestimmt der Aufrufer) |
| `camdrones:warning.limit` | zwei tiefe Warntöne |
| `camdrones:crew.request` | Hinweiston für eine Einladung |
