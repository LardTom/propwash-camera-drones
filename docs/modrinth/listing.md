# Modrinth- und CurseForge-Listing — Propwash: Camera Drones 1.0.0

Alles, was die Projektseiten brauchen. Beschreibung: [`description.md`](description.md) (Englisch, als
Projektbeschreibung einfügen) und [`description_de.md`](description_de.md) (Deutsch, für eine übersetzte Seite oder als
Spoiler-Abschnitt). Changelog für den Versions-Upload: [`CHANGELOG.md`](../../CHANGELOG.md), Abschnitt 1.0.0.

**Stand 1.0.0:** Name „Propwash: Camera Drones“ und Lizenz hat der Besitzer am 10.11.2026 festgelegt. Das
CurseForge-Projekt gibt es schon: 1796829, `propwash-camera-drones`
(https://www.curseforge.com/minecraft/mc-mods/propwash-camera-drones); dorthin zeigen auch die Metadaten
(`homepage_url` in `gradle.properties`, im Spiel in den Sharecode-Hinweisen anklickbar). Das Modrinth-Projekt
`propwash-camera-drones` entsteht beim ersten Hochladen als Entwurf.

## Projekt

| Feld | Wert |
|---|---|
| Name | Propwash: Camera Drones |
| Slug | `propwash-camera-drones` (Modrinth und CurseForge) |
| Projekttyp | Mod |
| Icon | `common/src/main/resources/assets/camdrones/icon.png` |
| Lizenz | PolyForm Shield 1.0.0 mit zusätzlichen Erlaubnissen (Modrinth: eigene Lizenz `LicenseRef-PolyForm-Shield-1.0.0`, Link auf `LICENSE`), Doku MIT (`LICENSE-MIT`), siehe `NOTICE` |
| Client | Pflicht |
| Server | Pflicht |
| Links | Quellcode `https://github.com/LardTom/propwash-camera-drones`, Issues `https://github.com/LardTom/propwash-camera-drones/issues`, Wiki `https://propwashfpv.com/docs/`, Discord `https://discord.gg/bNhkSXYSgZ`, Spenden (Ko-fi) `https://ko-fi.com/propwashfpv`; CurseForge `https://www.curseforge.com/minecraft/mc-mods/propwash-camera-drones` (Startseite in den Metadaten) |

### Kurzbeschreibung (≤ 256 Zeichen)

Englisch (215 Zeichen):

> Programmable camera drones for Propwash FPV: GPS position hold, a Flight Planner tablet for waypoint missions, follow
> me, point of interest, course lock and a stabilized 3-axis gimbal that a second player can steer.

Deutsch (234 Zeichen, falls eine übersetzte Kurzbeschreibung gewünscht ist):

> Programmierbare Kamera-Drohnen für Propwash FPV: GPS-Positionshaltung, Tablet „Flugplaner“ für Wegpunkt-Missionen,
> Follow me, Point of Interest, Course Lock und ein stabilisierter 3-Achsen-Gimbal, den ein zweiter Spieler steuern kann.

### Kategorien

| | |
|---|---|
| Hauptkategorien (max. 3) | Technology, Transportation, Utility |
| Weitere Kategorien | Game Mechanics |

Suchbegriffe, die Kurzbeschreibung und Beschreibung abdecken: drone, camera drone, quadcopter, gimbal, waypoint, follow
me, cinematic, filming, Propwash, FPV, add-on.

## Versionen

Je Loader eine Version hochladen; beide entstehen aus demselben Commit.

| Feld | Fabric | NeoForge |
|---|---|---|
| Datei | `fabric/build/libs/camdrones-fabric-26.3-1.0.0.jar` | `neoforge/build/libs/camdrones-neoforge-26.3-1.0.0.jar` |
| Versionsnummer (Vorschlag) | `1.0.0+fabric` | `1.0.0+neoforge` |
| Versionsname (Vorschlag) | Camera Drones 1.0.0 (Fabric) | Camera Drones 1.0.0 (NeoForge) |
| Loader | Fabric | NeoForge |
| Spielversionen | 26.3 | 26.3 |
| Abhängigkeiten | Propwash FPV ≥ 0.6.0 (`0.6.0+fabric`) — Pflicht; Fabric API — Pflicht | Propwash FPV ≥ 0.6.0 (`0.6.0+neoforge`) — Pflicht |
| Kanal (Vorschlag) | Beta, solange Propwash Beta ist | Beta |
| Changelog | `CHANGELOG.md`, Abschnitt 1.0.0 | ebenso |

Die `-sources`-Jars nicht als Hauptdatei hochladen. Mindestversionen aus `gradle.properties`: Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, NeoForge 26.3.0.10-beta, Java 25, Propwash FPV 0.6.0 (Add-on-API 1.8). Die Jar-Namen folgen
`mod_id` und `version`; vor dem Upload mit `ls fabric/build/libs neoforge/build/libs` prüfen.

## Galerie

Die Bildschirmfotos in `docs/screenshots/` stammen aus dem Selbsttest (flache Testwelt, 1920 × 1080) und belegen die
Funktionen; für die Projektseite taugen davon nur die Fenster. Die übrigen Galeriebilder entstehen nach der Shotliste
(Lieferung außerhalb des Repos) in einer gebauten Welt.

| # | Datei | Titel (en) | Beschreibung (en) |
|---|---|---|---|
| 1 ★ hervorgehoben | *neu: Kino-Drohne vor einem Bauwerk bei Sonnenuntergang* | Camera drones for Propwash | Compact, Tour and Cine side by side – folding frames, smart batteries and gimbal cameras in Propwash's style. |
| 2 | `planner_editor_854x480_map.png` | Plan a mission | The Flight Planner: waypoints on a top-down map, a point of interest in the middle, height, speed, tilt and look per waypoint, length, time and battery of the mission. |
| 3 | `workbench_cine.png` | Built at the workbench | The Cine drone at Propwash's workbench: 3.8 kg, thrust-to-weight 3.5 : 1, 105 km/h and ready to fly. |
| 4 | *neu: Kreis um einen Turm, Blick aus der Gimbal-Kamera* | Point of interest | The drone circles a tower on its own, the gimbal keeps the tower in the middle of the picture. |
| 5 | *neu: Follow me beim Reiten* | Follow me | The drone follows you on foot, on horseback or by boat, the camera always on you. |
| 6 | *neu: geteiltes Bild Pilot und Operator* | Two pilots | One player flies, the other steers the camera with their own radio. |
| 7 | `gimbal_horizon_roll.png` | Level horizon | Even at 30° roll the gimbal keeps the horizon level – recordings and photos are stabilized. |
| 8 | *neu: Hindernis-Stopp mit OSD* | Stops before walls | Obstacle sensors stop the drone 1.5 m in front of a wall, the OSD shows OBSTACLE and the distance. |

Die Bilder mit `_de` im Namen zeigen dieselben Fenster auf Deutsch (Prüfung der Übersetzung und Beschriftungsbreiten);
sie gehören nicht in die Galerie.

Bilder in der Beschreibung: Banner, Überschriften und GIFs liegen im öffentlichen Repository unter `.github/listing/`;
die Beschreibung verlinkt sie als `https://raw.githubusercontent.com/LardTom/propwash-camera-drones/main/.github/listing/<datei>`.
Sie erscheinen, sobald das Repository öffentlich ist (beim Release kommt GitHub vor Modrinth). Text für CurseForge:
[`../curseforge/description.md`](../curseforge/description.md).
