# Propwash: Kamera-Drohnen

**Kamera-Drohnen, die selbst fliegen, für Propwash FPV.** Knüppel loslassen, und die Drohne steht in der Luft. Sie hält
per GPS ihre Position, trägt eine stabilisierte 3-Achsen-Gimbal-Kamera und fliegt Missionen, die man auf einem Tablet
plant: Wegpunkte, Follow me, Point of Interest und Course Lock. Ein zweiter Spieler kann die Kamera mit seiner eigenen
Funke steuern.

> **Braucht [Propwash FPV](https://modrinth.com/mod/propwash-fpv) 0.6.0 oder neuer** auf Client und Server. Das ist ein
> Add-on: Es bringt die Drohnen, die Teile und die Flughilfe; geflogen wird mit Propwash.

---

## Öffentlicher Server

Spiel Propwash mit anderen auf unserem öffentlichen Server **`mc.propwashfpv.com`**. Seit Propwash 0.6.0 trittst du mit
**Fabric oder NeoForge** bei. Trag die Adresse in deine Serverliste ein; fehlt dir eine Mod, nennt sie der
Beitrittsbildschirm.

## Die andere Art Drohne

FPV-Quads sind schnell und wild. Kamera-Drohnen sind die ruhigen:

| | FPV-Quad | Kamera-Drohne |
|---|---|---|
| Knüppel | Drehraten | Tempo: loslassen, und sie bremst und hält |
| Kamera | fest, wackelt mit dem Rahmen | 3-Achsen-Gimbal, Horizont gerade |
| Flugzeit | 3 bis 6 min | etwa 26 bis 41 min |
| Programmierbar | nein | Wegpunkte, Halten, Follow me, Point of Interest, Course Lock |

Die Physik bleibt Propwashs eigenes Flugmodell mit Wind, Böen, Prop-Wash und Akku-Kurve. Das Add-on bringt nur Teile mit
echten Werten und die Flughilfe obendrauf.

## Was drin ist

| | |
|---|---|
| Kamera-Drohnen | **Kompakt** (244 g, etwa 30 min Schweben), **Tour** (720 g, etwa 41 min, Weitwinkel- und Tele-Kamera) und **Kino** (3,8 kg, etwa 26 min, 360°-Gimbal mit Wechselobjektiven), flugfertig im eigenen Kreativ-Tab |
| Teile | 19: Faltrahmen, Nav-FCs mit GPS, Kompass und Barometer, Motoren mit niedriger KV, Falt-Props, Smart-Akkus, digitaler Kamera-Funk, 3-Achsen-Gimbals |
| Objektive | 24, 50 und 85 mm für den Kino-Gimbal |
| Flugplaner | ein Tablet zum Koppeln mit der Drohne und zum Planen von Missionen |

- **Überleben:** Jedes Teil und jedes Objektiv hat ein Rezept aus Propwashs Zwischenteilen; gebaut wird an Propwashs
  Werkbank. Eine Kompakt-Drohne liegt manchmal in Dorf-Truhen.
- **Eigene Modelle im Propwash-Stil** mit Lack, Faltarmen, Lade-LEDs an den Akkus und beweglichen Gimbals.
- Komplett auf **Deutsch und Englisch**.

## Fliegen

- **Normal, Kino und Sport** auf einem 3-Stufen-Schalter: Tempo-Steuerung mit Bremse, geglättete Knüppel für ruhige
  Aufnahmen oder manuell für Tempo.
- **ATTI** ohne GPS-Fix: unter Blöcken, in Höhlen oder bei gestörtem Kompass. Die Drohne hält die Höhe und treibt mit
  dem Wind.
- **Hindernis-Sensoren** am Nav-FC Pro halten die Drohne vor Wänden an, mit Warnton wie eine Einparkhilfe.
- **Höhengrenze**, OSD-Elemente und Warnungen im OSD-Editor von Propwash, Rückkehr mit Propwashs GPS-Rescue.

## Flugplaner

Blöcke anklicken setzt Wegpunkte in die Welt, ebenso im Flug oder per Flugaufzeichnung. Der Editor zeigt eine Karte von
oben, die Wegpunkte mit Höhe, Tempo, Wartezeit, Gimbal-Neigung, Blickrichtung und Foto- oder Aufnahme-Aktionen, Vorlagen
für Kreis, Enthüllung und Strecke und eine Vorschau von Länge, Dauer und Akkubedarf. Mission **senden** und **Start**
drücken: Die Drohne hebt selbst ab, fliegt die Mission und fliegt weiter, wenn man die Brille absetzt. Brille wieder
auf, und man übernimmt.

## Intelligente Funktionen und Zwei-Piloten

- **Halten:** ein Luftstativ für Minuten.
- **Follow me:** hinterher, seitlich oder nur zuschauend.
- **Point of Interest:** um das kreisen, was in der Bildmitte ist; die Knüppel ändern Radius und Tempo.
- **Course Lock:** geradeaus fliegen, während die Kamera dreht.
- **Zwei Piloten:** einen Spieler als Kameramann einladen: Er schwenkt, neigt, zoomt, nimmt auf und macht Fotos
  mit seiner eigenen Funke, während man selbst fliegt.

## Für Server gemacht

- Der Server prüft jede Mission, jedes Follow-me-Ziel und jede Crew-Anfrage.
- Welt-Regeln: Flüge ohne Pilot, anderen Spielern folgen (standardmäßig aus und immer mit Zustimmung), zweiter Pilot,
  Höhen- und Abstandsgrenze, Höchstdauer für Flüge ohne Pilot. Keine davon berührt FPV-Rennen.
- Ein Flug ohne Pilot je Spieler; er endet, wenn der Besitzer offline geht oder der Akku knapp wird.
- `/camdrones` für Missionen, Halten, Follow me und die Crew.

## Kompatibilität

- Minecraft 26.3 · Fabric (mit Fabric API) oder NeoForge · Java 25 · auf dem Server **und** jedem Client.
- Propwash FPV 0.6.0 oder neuer.
- Verträglich mit **Just More Parts** und **Mob Defense**. Kamera-Drohnen gehen wie jede Drohne in Propwashs Sharecodes.

---

Von lardtom. Kein offizielles Minecraft-Produkt. Nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.

Propwash und seine Add-ons sind kostenlos. Wenn sie dir gefallen, kannst du das Projekt auf
[Ko-fi](https://ko-fi.com/propwashfpv) unterstützen. Danke!
