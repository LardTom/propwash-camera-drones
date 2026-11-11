# Propwash: Kamera-Drohnen

Add-on für [Propwash FPV](https://modrinth.com/mod/propwash-fpv): programmierbare Kamera-Drohnen mit GPS-Positionshaltung, Wegpunkten,
Follow me, Point of Interest, Course Lock und einer stabilisierten Gimbal-Kamera, die ein zweiter Pilot steuern kann.
Mod-ID `camdrones`, englischer Name *Propwash: Camera Drones*.

English: [`README.md`](README.md).

- **Drei Kamera-Drohnen:** Kompakt (244 g, etwa 30 min), Tour (720 g, etwa 41 min) und Kino (3,8 kg, 360°-Gimbal mit
  Objektiven), aus Teilen an Propwashs Werkbank gebaut oder flugfertig aus dem Kreativ-Tab.
- **Loslassen, und sie steht:** Die Knüppel geben das Tempo vor, die Drohne bremst und hält per GPS Position und Höhe,
  auch bei Wind. Modi Normal, Kino und Sport, ATTI ohne GPS-Fix, Hindernis-Sensoren, Höhengrenze.
- **Tablet „Flugplaner“:** Wegpunkte in die Welt klicken, Missionen auf einer Karte mit Höhe, Tempo, Wartezeit,
  Kamera-Neigung und Foto- oder Aufnahme-Aktionen planen, senden und starten. Die Drohne fliegt weiter, wenn man die
  Brille absetzt.
- **Intelligente Funktionen:** Halten als Luftstativ, Follow me, Kreisen um einen Point of Interest, Course Lock.
- **Zwei Piloten:** einen Kameramann einladen, der den Gimbal mit seiner eigenen Funke steuert. Allein steuert man den
  Gimbal im Livebild des Flugplaners, während die Drohne die Mission fliegt.
- **Für Server gemacht:** Der Server entscheidet; Welt-Regeln für Flüge ohne Pilot, das Folgen anderer Spieler (nur mit
  Zustimmung), den zweiten Piloten sowie Höhen- und Abstandsgrenze.

Version **1.0.0** · Download: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/propwash-camera-drones) ·
Öffentlicher Server: `mc.propwashfpv.com` (Fabric und NeoForge) · Unterstützen: [Ko-fi](https://ko-fi.com/propwashfpv)

## Voraussetzungen

- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API) oder NeoForge 26.3 · Java 25.
- Propwash FPV 0.6.0 oder neuer (Add-on-API 1.8), auf dem Server und auf jedem Client.

## Doku

- Anleitung für Spieler: [`docs/guide.de.md`](docs/guide.de.md) (deutsch), [`docs/guide.md`](docs/guide.md) (englisch)
- Änderungen für Spieler: [`CHANGELOG.md`](CHANGELOG.md) (englisch)
- Tests: [`docs/testing.md`](docs/testing.md), Sharecode-Tabelle: [`docs/sharecode.md`](docs/sharecode.md), Release:
  [`docs/RELEASING.md`](docs/RELEASING.md)
- Entwurf: [`docs/design.md`](docs/design.md), Konventionen: [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md)

## Bauen

```
./gradlew build
```

Propwash muss vorher im lokalen Maven-Repository liegen (im Propwash-Repo: `./gradlew publishToMavenLocal`); die Nummer
steht in `gradle.properties` unter `propwash_version`. `build` prüft auch die Sharecode-Tabelle mit Propwashs
`ShareTableCheck`. Web-Export für die Website: `./gradlew :fabric:runWebExport`, danach `python3 tools/web_export.py`.

## Lizenz

Code unter der PolyForm Shield License 1.0.0 mit zusätzlichen Erlaubnissen ([`LICENSE`](LICENSE): Modpacks erlaubt,
Ausstiegsklausel nach 12 Monaten ohne Release und Pflege), Doku unter MIT ([`LICENSE-MIT`](LICENSE-MIT)), siehe
[`NOTICE`](NOTICE). Der Namensraum `propwash` gehört Propwash, dieses Add-on nutzt `camdrones`.

Von lardtom. Kein offizielles Minecraft-Produkt. Nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.
