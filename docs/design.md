# Entwurf: Propwash-Add-on „Kamera-Drohnen“ (Propwash: Camera Drones, Mod-ID `camdrones`)

| | |
|---|---|
| Stand | 17.10.2026, Entwurf 1 (Design und Repo-Gerüst, noch keine Funktionen) |
| Repo | lokal `propwash-camera`, kein Remote, nie pushen |
| Gilt für | Add-on 1.0.0 auf Propwash FPV 0.6.0 (Add-on-API 1.8), Minecraft 26.3, Fabric und NeoForge, Java 25 |
| Gerüst | baut heute gegen Propwash 0.5.2 (API 1.6) aus mavenLocal; Umstieg auf `0.6.0-dev.N`, sobald API-Stand A dort liegt |
| Ersetzt | den älteren Kino-Entwurf im Propwash-Repo (`docs/design/addon-cinematic.md`, API 1.6) |
| Hinweis | Interne Planung. Vor einem öffentlichen Export die wörtlichen Zitate in §1.2 entfernen oder die Datei ausnehmen. |

---

## 1. Ziel und Owner-Zitate

### 1.1 Ziel

Propwash bekommt neben den FPV-Quads eine zweite Drohnenart: **Kamera-Drohnen, die selbst fliegen.** Sie halten per GPS
die Position, bremsen, wenn man die Knüppel loslässt, tragen eine stabilisierte 3-Achsen-Gimbal-Kamera und lassen sich
programmieren: Wegpunkte, Position halten, Follow me, Point of Interest, Course Lock und kurze Automatik-Flüge. Eine
zweite Person kann mit ihrer eigenen Funke den Gimbal steuern. Gebaut wird wie bei Propwash aus Teilen an der Werkbank,
programmiert mit einem Tablet („Flugplaner“), das man in der Hand hält.

Für wen: Spieler, die Bauwerke, Server-Events oder Rennen filmen wollen, Creator (ruhige Aufnahmen ohne FPV-Können) und
Teams (Pilot plus Kamera). Das Add-on kommt ohne einen einzigen Mixin in Propwash aus und nutzt nur
`de.lardtom.propwash.api`.

### 1.2 Owner-Zitate und Vorgaben

- Zu Follow me, Point of Interest, Course Lock und Zwei-Piloten-Gimbal (Discord-Auswahl, 17.10.):
  **„ja das soll mit in das kameradrohnen addon“**
- Damalige Antwort im Discord auf denselben Wunsch: „Yeah this would be cool“. Zu Cinelifter-Rigs: „there is a addon
  in the making“ (ein Community-Add-on, deshalb hier keine Cinelifter).
- Zur Reihenfolge (17.10. abends): **„parallelisier alles so weit wie möglich“**. Das Add-on startet jetzt mit Design und
  Gerüst, gebaut wird ab API-Stand A von 0.6.0, die Schlussprüfung läuft gegen das fertige 0.6.0.
- Zum separaten GPS-Teil aus dem Fork-Vorschlag (Discord-Auswahl Punkt 31): „bleibt draußen“.
- Zum Code: „der code der mod soll nicht kommentiert sein, orientiere dich an den anderen mods“.
- Zu Versionen: „Du organisierst deine Versionsnummern selbst, ich will nur vernünftige Versionsnummern haben.“
- Vorgaben sinngemäß (Roadmap 02.10., 15.10., Plan nach 0.5.2 vom 17.10.):
  - Kino- bzw. Kamera-Drohnen werden ein eigenes, offizielles Add-on („Propwash: …“), kein Teil der Basis.
  - Kein Markenname eines Drohnenherstellers im Namen oder irgendwo im Add-on (Texte, Ids, Assets, Doku, Commits).
  - Umfang: Wegpunkte, Position halten, Gimbal; dazu Follow me, Point of Interest und Zwei-Piloten-Gimbal.
  - Backport 1.21.1 ohne API, aber mit **einer** programmierbaren Drohne aus diesem Add-on fest eingebaut.
  - Pro Add-on eine Testliste und eine Shotliste (Reel pro Feature, YouTube-Vorstellung); Website mitziehen.
  - Der Namensraum `propwash` ist reserviert; das Add-on nutzt nur seinen eigenen.
  - Marke „lardtom“, keine fremden Namen, Mails oder Pfade im Repo.

### 1.3 Name

Entschieden (Owner, 10.11.2026): **Propwash: Camera Drones**. Die Vorschläge, aus denen er gewählt hat:

Alle nach dem Schema der offiziellen Add-ons, ohne Markennamen. Vor der Entscheidung kurz auf Modrinth, CurseForge und im
Markenregister prüfen.

| Nr. | Name | Warum |
|---|---|---|
| 1 | **Propwash: Camera Drones** (Empfehlung) | sagt genau, was drin ist, wie „Mob Defense“ (der Owner fand den beschreibenden Namen am besten); passt zur Mod-ID `camdrones`; wird in der Suche gefunden |
| 2 | Propwash: Flight Plan | stellt das Programmieren in den Mittelpunkt; passt zum Tablet „Flugplaner“ |
| 3 | Propwash: Camera Crew | betont Pilot plus Kamera-Operator, klingt nach Filmset; gut für Server und Creator |
| 4 | Propwash: Hands Off | augenzwinkernd wie „Just More Parts“: Knüppel loslassen, die Drohne fliegt allein, das Gegenteil von FPV |
| 5 | Propwash: Aerial Cinema | sagt, wofür man sie nimmt (Filmaufnahmen); Nachfolger des alten Arbeitsnamens „Propwash: Cinema“ |

Die Mod-ID `camdrones` bleibt bei jedem dieser Namen gleich (Entscheidung E1).

---

## 2. Inhalt im Detail

### 2.1 Kamera-Drohne gegen FPV-Quad

| | FPV-Quad (Propwash) | Kamera-Drohne (dieses Add-on) |
|---|---|---|
| Steuerung | Acro/Angle/Horizon, Knüppel = Drehraten bzw. Winkel | Knüppel = Geschwindigkeit, loslassen = bremsen und Position halten |
| Ortung | GPS nur für Rescue und OSD | GPS, Barometer und Kompass sind Pflicht; ohne GPS-Fix ATTI (driftet mit dem Wind) |
| Kamera | fest, mit Uptilt, wackelt mit dem Rahmen | 3-Achsen-Gimbal, Horizont immer gerade, Neigung per Drehrad |
| Video | 20–35 ms Latenz | 90–130 ms Latenz, dafür sauberes HD-Bild und große Reichweite |
| Flugzeit | 3–6 min | 25–45 min |
| Tempo | 30–45 m/s, sehr agil | 3 m/s (Kino) bis 26 m/s (Sport), weich |
| Sicherheit | keine | Hinderniserkennung, Höhengrenze, Rückkehr bei Akku knapp |
| Programmierbar | nein | Wegpunkte, Halten, Follow me, Point of Interest, Course Lock, Automatik-Flüge |

Physik bleibt Propwashs Flugmodell (Wind, Böen, Prop-Wash, Akku-Kurve). Das Add-on liefert Teile mit realistischen
Werten und die Flughilfe (den „Flight-Controller“), die über `AssistOutput` Propwashs Regelkreise ALTHOLD/POSHOLD
ansteuert.

### 2.2 Drei Klassen

Richtwerte; die Teile-Daten werden so gewählt, dass Propwashs Prüfstand (`BuildStats`) diese Werte trifft (§6.1).
Kodiert in `logic.nav.FlightClass`.

| Klasse | Abflugmasse | Akku | Schweben | Kino / Normal / Sport | Steigen / Sinken | Hindernis-Sensoren | Besonderheit |
|---|---|---|---|---|---|---|---|
| **Kompakt** | 249 g | 2S Li-Ion 3850 mAh (28 Wh) | 26–32 min | 3 / 10 / 16 m/s | 5 / 3,5 m/s | nur unten | Einstieg, leise, windanfällig |
| **Tour** | 720 g | 4S LiHV 4240 mAh (63 Wh) | 38–46 min | 3 / 12 / 20 m/s | 6 / 5 m/s | rundum | Weitwinkel + Tele, längste Flugzeit |
| **Kino** | 3,95 kg | 6S LiHV Doppelpack 8560 mAh (190 Wh) | 23–29 min | 4 / 15 / 26 m/s | 8 / 6 m/s | rundum | 360°-Gimbal, Wechselobjektive, Zwei-Piloten |

Grenzen der API 1.6: geschwindigkeitsgeregelte Modi höchstens 20 m/s (`AssistOutput.MAX_SPEED`), Steigen höchstens
8 m/s (`MAX_CLIMB`). Sport fliegt deshalb mit `sticks` + `mode(ANGLE)` und begrenztem Neigungswinkel; das Tempo ergibt
sich dann aus Schub und Luftwiderstand.

### 2.3 Teile (Propwash-Teile-API, Namensraum `camdrones`)

Alle als JSON unter `data/camdrones/propwash_parts/<kategorie>/<id>.json` (`"format": 1`), Texte in
`common/src/lang/parts/`. Massen und Werte sind Richtwerte, der Prüfstand legt sie fest.

| Kategorie | Teile | Hinweise |
|---|---|---|
| Rahmen | `compact_fold_frame` (250 mm, 6″), `tour_fold_frame` (380 mm, 9″), `cine_frame` (695 mm, 15″) | geschlossene Schale statt offener Platten, Faltarme; Halterungen nur `front` (Gimbal) und `top` (Action-Cam, LEDs); Rolle `LONG_RANGE` (Kompakt, Tour) bzw. `CINELIFTER` (Kino), eine eigene Rolle kommt nicht in API 1.7 (G11) |
| Stack | `nav_fc_compact`, `nav_fc_pro` | Flight-Controller mit GPS, Kompass, Barometer; schaltet die Flughilfe ein; Sensor-Abdeckung steht in der Add-on-Tabelle `part.SensorTable` |
| Motoren | `m1404_1900`, `m2008_1100`, `m4114_400` | niedrige KV, hoher Wirkungsgrad im Schweben |
| Props | `fold_6`, `fold_9`, `fold_15` | Zweiblatt, faltbar, leise |
| Akkus | `smart_2s_3850_lion`, `smart_4s_4240_hv`, `smart_6s_8560_hv` | Zellchemie über `CellChemistry`; Ladeanzeige als Textur |
| Video | `cam_link_compact`, `cam_link_pro` | digital, 90–130 ms, große Reichweite, saubere Bildqualität |
| Zubehör vorne | `gimbal_compact`, `gimbal_tour`, `gimbal_cine` | 3-Achsen-Gimbal mit Action-Cam-Daten (ab 1.7 vorne erlaubt, B9); `gimbal_cine` hat ein Objektiv-Fach (1 Slot, `AccessoryBehavior`) |
| Objektive (Items) | `lens_24`, `lens_50`, `lens_85` | nur für `gimbal_cine`; Brennweite wirkt nur mit G7, sonst feste 24 mm |

Fertige Bauten (`DronePreset`): „Kompakt“, „Tour“, „Kino“ im Kreativ-Tab des Add-ons (`ExtensionTab`) und als seltene
Beute in Dorf-Truhen (`LootInjections`, nur Kompakt). Im Survival baut man an Propwashs Werkbank.

### 2.4 Weitere Items

| Item | Zweck |
|---|---|
| **Flugplaner** (`flight_planner`) | Tablet: Drohne koppeln, Missionen planen, starten, pausieren; Live-Status. Funke bleibt Propwashs Funke. |
| Landeplatz (`landing_pad`, Block, Stufe 2) | Startpunkt und Ziel für „Präzise landen“; flacher Block mit H-Markierung |

### 2.5 Rezepte im Propwash-Stil

Geformte 3×3-Rezepte, Reiter `equipment`, Gruppen `propwash_<kategorie>` wie bei Propwash und JMP, aus Propwashs
Zwischenteilen (Platine, HD-Chip, Carbonplatte, Kupferspule, LiPo-Zelle) und Vanilla-Items mit sprechender Bedeutung.
Eindeutig gegen Propwash und JMP (JUnit, §6.1).

| Ergebnis | Muster | Schlüssel |
|---|---|---|
| Nav-FC Kompakt | `BCB` `RGR` `B B` | B Platine, C Kompass (GPS/Kompass), R Redstone, G Goldbarren |
| Nav-FC Pro | `OCO` `BHB` `RGR` | O Beobachter (Hindernis-Sensoren), C Kompass, B Platine, H HD-Chip, R Redstone, G Gold |
| Faltrahmen Kompakt | `P P` `KDK` `P P` | P Carbonplatte, K Kette (Faltgelenk), D hellgrauer Farbstoff |
| Faltrahmen Tour | `PIP` `KDK` `PIP` | wie oben, I Eisenbarren, D grauer Farbstoff |
| Kino-Rahmen | `PIP` `IDI` `S S` | P Carbonplatte, I Eisenbarren, D schwarzer Farbstoff, S Eisengitter (Landebeine) |
| Gimbal Kompakt | ` C ` `NTN` ` H ` | C Kupferspule (Gimbal-Motoren), N Eisennugget, T getöntes Glas, H HD-Chip |
| Gimbal Tour | `CSC` `NTN` ` H ` | wie oben, S Fernrohr (Tele) |
| Kino-Gimbal | `CCC` `ITI` `HBH` | C Kupferspule, I Eisenbarren, T getöntes Glas, H HD-Chip, B Platine |
| Objektiv 24/50/85 | `NGN` / `NSN` / `SGS` je Zeile mit `N` | N Eisennugget, G Glasscheibe, S Fernrohr |
| Smart-Akku | `LKL` `LBL` `LLL` (Zellzahl je Klasse) | L LiPo-Zelle, K Komparator (Ladeanzeige), B Platine |
| Kamera-Funk | ` A ` `HBH` | A Blitzableiter (Antenne), H HD-Chip, B Platine |
| Falt-Props | wie Propwash-Props, mittig Kette | Kette = Faltgelenk |
| Flugplaner | `IGI` `IMI` `IBI` | I Eisenbarren, G Glasscheibe, M Karte, B Platine |

Endgültige Muster, Mengen und Rezept-Fortschritte legt Strang S2 fest.

### 2.6 Flugmodi und intelligente Funktionen

Die Flughilfe `camdrones:nav` ist aktiv, wenn der Bau einen Nav-FC hat. Sie läuft im Pilotenflug mit 100 Hz auf dem
Client (API 1.6) und ohne Pilot auf dem Server (B7), immer mit derselben Logik aus `logic.nav`.

| Modus | Auslösen | Verhalten | Ausgaben (`AssistOutput`) |
|---|---|---|---|
| **N – Normal** | Standard | Knüppel = Tempo bis Normal-Grenze, Gas = Steigrate, Gieren 90 °/s; loslassen = bremsen und halten | `velocity`, `maxSpeed`, `climb`, `yawRate`, `holdPosition` |
| **C – Kino** | Modus-Schalter | wie N, Knüppel geglättet (0,8 s), Kino-Tempo, Gieren 30 °/s | wie N |
| **S – Sport** | Modus-Schalter | manuell mit Winkelgrenze 35°, keine Hindernis-Bremse | `sticks`, `mode(ANGLE)` |
| **ATTI** | automatisch ohne GPS-Fix (Höhlen, unter Blöcken) | hält nur die Höhe, driftet mit dem Wind; Warnung im OSD | `sticks`, `mode(ANGLE)`, `holdAltitude` |
| **Halten** | Pause-Taste, beim Verlassen der FPV-Ansicht (Einstellung) | schwebt auf der Stelle, Gimbal frei | `holdPosition`, `holdAltitude` |
| **Follow me** | Schalter oder Tablet | folgt dem Besitzer: Hinterher (Abstand 8 m, Höhe 5 m), Parallel (seitlich) oder Blick (bleibt stehen, dreht nur) | `velocity` mit Vorhalt, `heading`, Gimbal zielt auf das Ziel |
| **Point of Interest** | Schalter (Punkt in der Bildmitte, Strahl bis 64 m) oder Tablet | kreist mit Radius, Höhe, Tempo und Richtung; Rollen-Knüppel = Tempo, Nicken = Radius | `velocity` tangential + radiale Korrektur, `holdAltitude`, `heading` zur Mitte |
| **Course Lock** | Schalter | Vorwärts fliegt immer die gespeicherte Richtung, Gieren dreht nur Nase und Kamera | `velocity` in fester Richtung, `yawRate` frei |
| **Mission** | Tablet oder Schalter „Mission starten“ | fliegt die Wegpunkte ab (§2.7) | alle obigen |
| **Automatik-Flüge** | Tablet | Rückzug (rückwärts und steigend weg), Aufstieg (senkrecht, Kamera schwenkt nach unten), Kreis, Spirale (Kreis und steigen); 10–20 s, danach Halten | wie Mission |
| **Rückkehr** | Propwashs GPS-Rescue-Schalter, Akku knapp | Propwashs Heimflug | `requestReturnHome` |

**Hinderniserkennung** (`sensorRange()` 15 m, sechs Strahlen): in N, C, Mission und Follow me wird das Tempo in
Richtung eines Hindernisses auf `max(0, (d − 1,5 m) · 1,2)` begrenzt, unter 1,5 m steht die Drohne („STOP“). Option
„Übersteigen“ (Tour, Kino): vorn blockiert, oben frei → bis 10 m steigen, dann weiter. Seitliches Umfliegen nicht
(sechs Strahlen sind zu grob, E13). Kompakt hat nur den Sensor nach unten.

**Höhengrenze**: Welt-Regel, Standard 120 m über dem Startpunkt, 0 = aus. Darüber steigt die Drohne nicht, OSD-Warnung.

**Flugdauer ohne Pilot**: so lange, wie der Akku reicht. Wird er kritisch, beendet Propwash den Flug mit Heimflug bzw.
Landung (API 1.7, B7: Ende mit dem Akku, harte Grenze 60 min je Flug). Das Add-on setzt kein eigenes `maxDuration` und
hat keine Welt-Regel für die Dauer; Server-Betreiber schalten Flüge ohne Pilot mit `allow_autonomy` ab. Je Spieler ist
höchstens ein Flug ohne Pilot gleichzeitig möglich, weil Propwash den zweiten Platz für den Heimflug freihält.

### 2.7 Programmieren mit dem Flugplaner

**Koppeln:** Schleichen + Rechtsklick mit dem Tablet auf die eigene, abgestellte Drohne.
Das Tablet merkt sich die Seriennummer der Drohne (Drohnen-Daten `camdrones:serial`, bleibt beim Aufheben erhalten).

**Wegpunkte setzen**, drei Wege:
1. **Zu Fuß:** Rechtsklick mit dem Tablet auf einen Block setzt einen Wegpunkt darüber (Höhe über Grund im Tablet
   einstellbar, Standard 10 m). In der Welt erscheinen, solange man das Tablet hält, nummerierte Marker und die
   Fluglinie (nur beim Halter sichtbar). Schleichen + Rechtsklick auf einen Marker löscht ihn.
2. **Im Flug:** Schalter „Wegpunkt setzen“ speichert Position, Höhe, Blickrichtung und Gimbal-Neigung.
3. **Aufzeichnen:** „Flug aufzeichnen“ legt beim Fliegen alle 10 m bzw. bei Richtungswechsel einen Punkt an.

**Editor** (Rechtsklick in die Luft, Propwash-Theme, auch bei GUI 320×240 bedienbar):
- links eine Karte von oben (Kartenfarben der geladenen Chunks, zoombar), Wegpunkte, Points of Interest, Startpunkt und
  Live-Position der Drohne;
- rechts die Liste; je Wegpunkt: Höhe, Tempo, Wartezeit, Gimbal-Neigung, Blickrichtung (Flugrichtung, fest, zum Point of
  Interest, frei für den Operator), Aktion (keine, Foto, Aufnahme an, Aufnahme aus);
- unten die Mission: Name, Grundtempo, Kurven (eckig oder weich), Ende (schweben, zum Start, landen, Schleife),
  bei Hindernis (stoppen oder übersteigen), Vorschau mit Länge, Dauer und Akkubedarf; Knöpfe Senden, Start, Pause, Stopp,
  Speichern.
- Vorlagen: Kreis um Punkt, Enthüllung (rückwärts steigend vor einem Bauwerk), Strecke zwischen zwei Punkten.

**Grenzen:** 50 Wegpunkte, 4 Points of Interest je Mission, 8 gespeicherte Missionen je Tablet
(`logic.mission.MissionLimits`). Die aktive Mission liegt an der Drohne (B6), also auch nach dem Aufheben, Ablegen oder
Neustart des Servers.

**Ablauf beim Start:** Drohne am Boden → Start, steigen auf die Höhe des ersten Punkts (G3), abfliegen. Drohne in der
Luft mit Pilot → die Mission läuft in der Flughilfe des Piloten, Knüppel bewegen pausiert sie. Pilot verlässt die
FPV-Ansicht → der Server fliegt sie weiter (G2). Pilot setzt die Brille wieder auf → er übernimmt, die Mission pausiert.

### 2.8 Gimbal und Zwei-Piloten

- `PropwashClientApi.registerGimbal("camdrones:gimbal", …)` mit `GimbalModel`: Folgen (Standard), Sperre (Richtung fest),
  FPV (Rollen folgt dem Rahmen). Neigung −90…+30° (Kino −90…+60°), mechanisch relativ zum Rahmen begrenzt; Kino-Gimbal
  schwenkt 360°.
- Neigung über die Achse `camdrones:gimbal_tilt` (Drehrad bzw. Poti an der Funke, sonst Tasten), Schwenk beim Kino-Gimbal
  über `camdrones:gimbal_pan`.
- **Stabilisierte Aufnahme:** Action-Cam-Daten am Gimbal vorne und `stabilizesRecording() = true` (B9): Aufnahme und
  Flashback-Kamerapfad zeigen die Gimbal-Lage, nicht die Rahmenlage.
- In Missionen kommt die Neigung aus den Wegpunkten (weich interpoliert), im Point-of-Interest- und Follow-me-Modus zielt
  der Gimbal auf das Ziel. Alle Clients rechnen dieselbe Lage aus synchronisierten Daten (B6, B8). Den sichtbaren
  Gimbal-Kopf dreht Propwash über `GimbalProvider#modelPose` (G5); die Neigung des Piloten schickt das Add-on dafür mit
  10 Hz über ein eigenes Paket an den Server, der sie als Drohnen-Datum `camdrones:gimbal` (`Sync.TRACKING`) verteilt.
- **Zwei-Piloten:** Der Pilot lädt in der Wartung (Knopf „Crew“, B10) oder mit `/camdrones crew invite <Spieler>` einen
  Spieler in höchstens 16 Blöcken ein; der bekommt eine Anfrage mit „Annehmen“. Danach ist er Operator (B8): Er sieht
  das Bild, seine Funke fliegt nicht. Rechter Knüppel schwenkt und neigt, linker Knüppel hoch/runter zoomt (G7);
  Aktionen: 2 zentrieren (auf Drohnen-Vorne), 3 Aufnahme (G6), 4 Foto (G6); Aktion 1 ohne Funktion. Lang drücken auf
  4 = Kamera abgeben. Solange ein Operator gebunden ist, hat der Pilot keine Gimbal-Steuerung. Auch während einer
  Mission ohne Pilot möglich.
- **Weltbezogener Schwenk mit Operator** (Owner 09.11.): Mit gebundenem Operator hält der Gimbal seine Weltrichtung
  (`GimbalSetting.Mode.LOCK`, `heading`), die Drehung der Drohne geht nicht in den Blick ein; kein Folgen für den
  Operator, Folgen/Sperre bleibt dem Solo-Piloten. Kompakt und Tour halten bis zum mechanischen Anschlag ±25°
  (`GimbalLimits`), der Operator schwenkt innerhalb der Grenze, sein OSD zeigt am Anschlag `GIMBAL LIMIT`; Kino 360°.
- **Ton:** Bewegen ist lautlos; `gimbal.calibrate` spielt nur beim Zentrieren und bei der Übernahme durch den Operator
  (Owner 09.11.).
- **Kamera allein** (Owner rc.4.1): Fliegt die Drohne ohne Pilot (B7, Flug im Namensraum `camdrones`), öffnet das
  *Livebild* des Flugplaners bzw. des Crew-Fensters für den Besitzer statt des Zuschauens die Kamera wie für einen
  Kameramann: Das Paket `crew` (Aktion `SOLO`) bindet ihn über B8 an seine eigene Drohne, `camdrones:gimbal` trägt
  `solo` und `steered`. Bis zur ersten Knüppel-Bewegung folgt der Gimbal dem Flugplan; danach gilt für alle Clients
  die Einstellung des Besitzers (weltbezogen wie beim Kameramann, `GIMBAL LIMIT`), die Automatik aus Wegpunkten, Point
  of Interest und Follow me ruht, bis er abgibt. Aktion 1 pausiert bzw. setzt fort, lang bricht sie die Mission ab
  (`mission_command`), Aktion 4 lang oder Esc führt zurück. Endet der Flug ohne Pilot, wird die Drohne geflogen oder
  nimmt ein zweiter Spieler als Kameramann an (Vorrang), endet die Kamera allein. Im Handflug unverändert; keine neue
  Propwash-API.

### 2.9 Welt-Regeln (B5, Abschnitt „Kamera-Drohnen“ im Tab „Welt-Regeln“)

| Id `camdrones:` | Typ | Standard | Bereich | Wirkung |
|---|---|---|---|---|
| `allow_autonomy` | Schalter | an | | Flüge ohne Pilot (Halten, Missionen, Follow me) |
| `allow_follow_players` | Schalter | aus | | Follow me auf andere Spieler, immer mit Zustimmung des Ziels; aus = nur Besitzer, eigene Tiere, Reittiere |
| `allow_operator` | Schalter | an | | Zwei-Piloten-Gimbal |
| `max_altitude` | Zahl | 120 | 0–512 | Höhengrenze in m über dem Startpunkt, 0 = aus |
| `max_distance` | Zahl | 0 | 0–4096 | Abstand vom Startpunkt in Blöcken, 0 = nur Propwashs Funkreichweite |

### 2.10 Tasten, Schalter und Achsen (A1)

Alle im Tab „Modi“ auf der Seite „Add-ons“ belegbar, alles frei änderbar; gedacht ist die Bedienung über die Funke
und das Tablet. Tastatur-Standards nur auf Tasten, die Propwash und Vanilla nicht belegen: Propwash nutzt auf dem
Ziffernblock schon 0–9, Enter und + (Flugmodi, 3D, Turtle, Blackbox, Zubehör-Aktionen, `KeyDefaults`). Frei sind dort
nur /, *, − und das Komma; dazu Einfg, Bild↑ und Bild↓. Seltene Funktionen haben keinen Standard. Kein Standard doppelt
(Selbsttest Schritt 19).

| Id `camdrones:` | Art | Tastatur-Standard | Server-Sync | Zweck |
|---|---|---|---|---|
| `mode_normal`, `mode_cine`, `mode_sport` | Schalter (Bereiche eines 3-Stufen-Kanals wie bei eingebauten Funktionen, G8) | – | ja | Flugmodus an der Funke |
| `mode_cycle` | Taster | Ziffernblock / | ja | Flugmodus reihum N → C → S; die letzte Änderung (Funke oder Taste) gilt |
| `pause` | Taster | Ziffernblock − | ja | Mission oder Funktion anhalten, schweben |
| `mission_start` | Taster | Ziffernblock * | ja | aktive Mission starten oder fortsetzen |
| `waypoint_add` | Taster | Einfg | ja | Wegpunkt an der aktuellen Position |
| `follow_me` | Schalter | – | ja | Follow me an/aus |
| `poi_here` | Taster | – | ja | Point of Interest in der Bildmitte setzen und kreisen |
| `course_lock` | Schalter | – | ja | Course Lock |
| `gimbal_lock` | Schalter | – | nein | Gimbal Folgen/Sperre |
| `gimbal_recenter` | Taster | Ziffernblock , | nein | Gimbal zentrieren, zweimal = senkrecht nach unten |
| `gimbal_tilt` | Achse | Bild↑ / Bild↓ | nein | Neigung |
| `gimbal_pan` | Achse | – | nein | Schwenk (Kino) |
| `zoom` | Achse | – | nein | Brennweite (G7) |

Aufnahme und Rückkehr nutzen Propwashs eingebaute Funktionen „Aufnahme“ und „GPS Rescue“.

### 2.11 OSD und HUD

- **OSD-Elemente (A2)**, im OSD-Editor in der Gruppe „Add-ons“: `camdrones:mode` („N“, „C“, „S“, „ATTI“, „POI“,
  „FOLLOW“, „WP 3/12“), `camdrones:gimbal` („G −45°“), `camdrones:mission` („WP 3/12 0:42“), `camdrones:obstacle`
  („◀ 4,2 m“ nur unter 8 m), `camdrones:flight_left` (Restflugzeit aus Akku und Verbrauch).
- **Warnungen (A2)**: „OBSTACLE“, „ALT LIMIT“, „NO GPS ATTI“, „PAUSED“, „HIGH WIND“, „OPERATOR“ (Gimbal beim Operator),
  „GIMBAL LIMIT“ (beim Operator: Kompakt oder Tour am Schwenk-Anschlag, Owner 09.11.).
  OSD-Texte bleiben Englisch in Großbuchstaben wie Propwashs OSD.
- **Kamera-Ansicht (HUD, 1.6 `HudElement`, optional statt OSD):** oben Modus, Satelliten, Akku in % und Restzeit, Signal;
  unten links H, D, H.S., V.S.; unten rechts Mini-Karte mit Heimpfeil; Neigungsskala am rechten Rand; rote Ränder bei
  Hindernissen; Aufnahmepunkt mit Zeit. Umschalter im Tablet.
- **Warnton** bei Hindernissen: Piepen schneller, je näher (wie eine Einparkhilfe).

### 2.12 Verhalten auf Servern

- Add-on auf Server **und** Client nötig (Teile, Items, Rezepte). Propwashs Beitritts-Übersicht nennt Name, Version und
  Homepage (§1.3 des Add-on-Handbuchs); ohne Add-on gibt es eine klare Meldung statt eines Fehlers.
- Der Server entscheidet alles: Missionen, Start, Follow-me-Ziele, Crew. Jede Anfrage wird auf Besitzer, Abstand,
  Welt-Regeln und Rate geprüft.
- Flüge ohne Pilot zählen zu Propwashs Autonomie-Budget (8 je Server, 2 je Spieler; Add-on-Flüge höchstens 1 je Spieler
  und 6 je Server, der Rest bleibt für Heimflüge), halten wie der Heimflug Chunks
  geladen und enden, wenn der Besitzer offline geht oder außer Funkreichweite ist (Landung bzw. Heimflug).
- **Privatsphäre:** Follow me auf andere Spieler nur mit Welt-Regel und Zustimmung; die Zustimmung endet beim Ausloggen.
  Ein verfolgter Spieler sieht im Action-Bar-Text, wer ihm folgt, und kann jederzeit „Stopp“ sagen (`/camdrones follow stop`).
- Kamera-Drohnen tragen keine Waffen: nur Halterungen `front` (Gimbal) und `top`.
- Befehle unter `/camdrones` (nie unter `/propwash`): `mission list|start|pause|stop`, `crew invite|accept|leave`,
  `follow stop`; Operatoren mit Rechtestufe 2 dürfen fremde Missionen stoppen.

---

## 3. Benötigte Propwash-API

### 3.1 Vorhanden in API 1.6 (Propwash 0.5.2)

| Baustein | Nutzung |
|---|---|
| Teile-API (JSON, `PartRegistry`, `PartItems`, `DronePreset`, `ExtensionTab`, `LootInjections`) | alle Teile, Presets, Kreativ-Tab |
| `AccessorySlot.FRONT`, `AccessoryBehavior` (Inventar) | Gimbal vorne, Objektiv-Fach |
| `PropwashApi.flight().registerAssist`, `FlightAssist`, `AssistOutput`, `FlightState` (GPS-Fix, Satelliten, Hindernis-Strahlen) | gesamte Flughilfe im Pilotenflug |
| `PropwashClientApi.registerGimbal`, `GimbalModel`, `GimbalContext` | Gimbal-Kamera |
| `registerHudElement` (`replacesOsd`), `FlightTelemetry` (Heimabstand, -höhe, -richtung) | Kamera-Ansicht |
| `registerFlightSchoolScene`, `linkFlightSchoolItems` | Flugschul-Kapitel „Kamera-Drohnen“ |
| `registerConfigTab`, `WorkbenchExtension`, Theme-Klassen | Einstellungen, Werkbank-Knopf „Kamera-Drohne prüfen“ |
| `BuildAccess`, `BuildStats` | Prüfstand-Tests für Flugzeit und Tempo |
| `PropwashEvents` (`DRONE_DAMAGED` usw.) | Crash beendet Mission |

### 3.2 Neu im 0.6.0-Entwurf (API 1.7, `addon-api-namespace.md`)

| Nr. | Baustein | Nutzung im Add-on |
|---|---|---|
| A1 | Add-on-Schalter und -Achsen | alle Funktionen aus §2.10 statt der vier Zubehör-Aktionen |
| A2 | OSD-Elemente und Warnungen | §2.11 |
| B5 | Add-on-Welt-Regeln | §2.9 |
| B6 | Daten an der Drohne | `serial`, `mission`, `mission_state`, `smart`, `settings` (§4.3) |
| B7 | Autonomer Flug mit Add-on-Flughilfe | Halten, Follow me, Point of Interest, Missionen ohne Pilot |
| B8 | Zweiter Operator | Zwei-Piloten-Gimbal |
| B9 | Action-Cam vorne, stabilisierte Aufnahme | Gimbal-Aufnahme, Flashback-Kamerapfad |
| B10 | `PILOT_CHANGED`, Knopf in der Wartung | Übergabe, Knöpfe „Mission“ und „Crew“ |
| C1 | Namensraum-Regeln | nur `camdrones:` |

### 3.3 Lücken für API 1.7 und ihr Stand

Gemeldet an den 0.6.0-Strang API 1.7; seit 17.10. stehen die Ergebnisse im 0.6.0-Entwurf (`addon-api-namespace.md`
§3.19, dortige Entscheidungen E21, E37–E39). Priorität: **muss** = ohne das fehlt eine Kernfunktion; **soll** = deutlich
schlechter ohne; **kann** = schöner; **prüfen** = vielleicht schon abgedeckt.

| Nr. | Prio | Lücke | Ergebnis in API 1.7 | Ohne das / Ausweg |
|---|---|---|---|---|
| G1 | muss | B7 hatte feste 10 min Höchstdauer; Kamera-Drohnen fliegen 25–45 min | übernommen, aber ohne Welt-Regel (0.6.0-E21): Ein Add-on-Flug endet mit dem Akku; `AutonomyOptions#maxDuration(Duration)` kürzt (1 s bis 60 min, Standard und harte Grenze 60 min). Heimflüge haben Vorrang im Budget (Add-on-Flüge höchstens 1 von 2 je Spieler, 6 von 8 je Server) | – |
| G2 | muss | Beim Verlassen der FPV-Ansicht startet Propwash Heimflug bzw. Landung | übernommen: `autonomy().registerExitHandler(Identifier id, ExitHandler handler)`, `ExitHandler#onPilotExit(Entity drone, ServerPlayer pilot)` → `Optional<AutonomyRequest>` (`AutonomyRequest.of(FlightAssist, AutonomyOptions)`); vor Propwashs Verhalten gefragt, erster Treffer gewinnt, nahtlos ohne Absacken | – |
| G3 | muss | Kein Start vom Boden, keine Landung als Ausgabe | übernommen: `AutonomyOptions#takeoff(double metres)` (1–30 m), neues Ergebnis `AutonomyResult.NOT_ARMABLE`, `AssistOutput#land()` mit Ende `LANDED` | – |
| G4 | muss | Kein Ereignis, wenn ein autonomer Flug endet | übernommen: `PropwashEvents.AUTONOMY_ENDED` (`AutonomyEnded`: drone, owner, id, reason), `AutonomyEnd` = `PILOT`, `LANDED`, `BATTERY`, `OWNER_OFFLINE`, `OUT_OF_RANGE`, `STOPPED`, `TIMEOUT`, `ERROR`, `REPLACED`; `autonomy().active(Entity)` → `Optional<Identifier>`. Jeder Flug trägt eine Id: `AutonomyOptions.of(Identifier)` | – |
| G5 | soll | Modell des Gimbal-Kopfs bleibt starr | übernommen: `GimbalProvider#modelPose(GimbalContext context, Quaternionf rotation)` → `boolean`, Propwash dreht das Element `gimbal_head`; Daten für andere Clients gleicht das Add-on über B6 ab (§2.8) | – |
| G6 | soll | Aufnahme nur per Taste des Piloten | übernommen: `PropwashClientApi.recording()` → `RecordingControl` (`start(Entity)`, `stop()`, `photo(Entity)`, `recording()`), für Pilot, Operator und zuschauenden Besitzer | – |
| G7 | kann | keine Brennweite je Gimbal | übernommen: `GimbalProvider#fovDegrees(GimbalContext)` (`NaN` = Kamera-Standard, 20–150° horizontal) | – |
| G8 | prüfen | Drei Add-on-Schalter auf einem 3-Stufen-Kanal | bestätigt: Add-on-Schalter werden wie eingebaute Funktionen per Kanal und Bereich belegt; keine neue Signatur. Tastatur über `mode_cycle` (§2.10) | – |
| G9 | prüfen | Livebild der eigenen Drohne vom Tablet | übernommen: `PropwashClientApi.spectate(Entity drone)` → `boolean` (bestehende Zuschauer-Sitzung, gleiche Prüfungen) | – |
| G10 | kann | Regelkreise nur gegen ein Ersatzmodell testbar | **nicht übernommen** (0.6.0-E39: das interne Flugmodell wird keine API) | Punktmassen-Modell in JUnit plus Selbsttest |
| G11 | kann | keine Rahmen-Rolle für Kamera-Drohnen | **nicht übernommen** (0.6.0-E39: neue Rolle bräuchte abgestimmte Werte im Energie- und Kamera-Modell und bricht vollständige `switch`-Anweisungen) | `LONG_RANGE` bzw. `CINELIFTER` |
| G12 | kann | Autonomie endet außer Funkreichweite | **nicht übernommen** (0.6.0-E39: Funkverlust bleibt ein Sicherheitsgrund) | Heimflug bei Funkverlust |
| G13 | prüfen | Zonen im autonomen Flug | bestätigt: Zonen wirken wie im Heimflug; Selbsttest im Strang `addon-autonomy` | – |
| G14 | kosmetisch | Beispiele im API-Entwurf nutzten `skycam` | erledigt: Beispiele nutzen `camdrones` | – |

---

## 4. Architektur, Datenformate, Netzwerk, Kompatibilität

### 4.1 Pakete (`de.lardtom.camdrones`)

| Paket | Inhalt | Seite |
|---|---|---|
| `CamDrones`, `platform` | Init, `requireVersion`, Loader-Dienste | beide |
| **`logic.**`** | **reines Java, portabel** (§8): `mission` (Mission, Waypoint, PointOfInterest, MissionCodec, MissionLimits), `nav` (FlightClass, NavInput, NavOutput, NormalMode, CineMode, SportMode, AttiMode, HoldController, OrbitController, FollowController, CourseLock, WaypointFollower, AutoShots, ObstacleGuard, HeightLimit), `gimbal` (GimbalAim), `estimate` (FlightTimeEstimate) | beide |
| `part` | Teile-Tabelle (Sensoren je FC), Presets, Loot | beide |
| `integration` | Adapter auf die Propwash-API: `NavAssist` (FlightAssist ↔ NavInput/NavOutput), Autonomie, Drohnen-Daten-Schlüssel, Welt-Regeln, Schalter | beide |
| `planner` | Tablet-Item, Komponenten, Server-Logik der Missionen | beide |
| `crew` | Einladung, Bindung über B8 | Server |
| `net` | eigene Pakete (§4.4) | beide |
| `command` | `/camdrones` | Server |
| `client.planner` | Editor, Karte, Welt-Vorschau der Wegpunkte | Client |
| `client.gimbal` | `GimbalProvider` | Client |
| `client.hud` | OSD-Elemente, Warnungen, Kamera-Ansicht, Warnton | Client |
| `client.input` | `KeyMapping`s, Bindung an A1 | Client |
| `fabric`, `neoforge` | Loader-Glue | je Loader |

Regeln: keine Kommentare, keine Mixins, nur `de.lardtom.propwash.api` plus die freigegebenen Theme-Klassen
(wie in JMP), `logic` importiert nichts aus Minecraft, Loadern oder Propwash (JUnit `PortabilityTest`). NeoForge ruft die
gemeinsame Init auf einem Worker-Thread: dort nur Anmeldungen.

### 4.2 Datenfluss

1. Tablet (Client) → `mission_upload` → Server prüft → `droneData().set(drone, MISSION, …)`.
2. Start: Drohne mit Pilot → der Pilot-Client führt die Mission in `NavAssist` aus (B6-Daten sind dort synchron);
   ohne Pilot → `autonomy().start(drone, owner, new NavAssist(mission), …)` mit `takeoff` (G3).
3. Fortschritt: Der Pilot-Client meldet Wechsel des Wegpunkts (`mission_progress`); bei der Übergabe (G2) sucht der
   Server zusätzlich das nächstgelegene Segment ab dem gemeldeten Index.
4. `mission_state` (B6, TRACKING) trägt Zustand und Index; Zwischenwerte (Gimbal-Neigung im Segment) rechnet jeder
   Client aus der Position. So sehen Pilot, Operator und Zuschauer dieselbe Gimbal-Lage.

### 4.3 Datenformate

**Drohnen-Daten (B6)**, Grenze 2 KiB je Schlüssel:

| Schlüssel | Sync | Inhalt |
|---|---|---|
| `camdrones:serial` | TRACKING | UUID, beim ersten Bau vergeben |
| `camdrones:mission` | TRACKING | gepacktes `IntArray` (unten) |
| `camdrones:mission_state` | TRACKING | Zustand (`IDLE`, `RUNNING`, `PAUSED`, `DONE`, `ABORTED`), Index, Epoche |
| `camdrones:smart` | TRACKING | aktive Funktion (Halten, Follow, POI, Course Lock), Ziel-UUID, Punkt, Radius, Tempo, Richtung |
| `camdrones:settings` | TRACKING | Verhalten beim Verlassen (schweben, heim, landen), Hindernis-Modus, Kamera-Ansicht |

**Mission, Format 1** (`IntArray`, Koordinaten in Dezimetern absolut, passt bis zur Weltgrenze in `int`):
- Kopf (6): Version, Flags (weich, Schleife), Ende-Aktion, Grundtempo (dm/s), Hindernis-Modus, Anzahl Wegpunkte.
- Je Wegpunkt (6): x, y, z, `tempo_dm_s << 16 | warten_ds`, `(neigung + 128) << 8 | blickmodus`, `aktion << 16 | richtung_grad`.
- Je Point of Interest (4): x, y, z, `radius_dm`.
- 50 Wegpunkte und 4 Points of Interest = 1288 Byte plus NBT-Kopf < 2 KiB (`MissionLimits.fits`, JUnit).
- Unbekannte Version → Mission wird nicht geladen, Daten bleiben unverändert (Abwärtskompatibilität).

**Tablet-Komponenten:** `camdrones:planner_link` (Seriennummer, Name), `camdrones:planner_missions` (bis 8 benannte
Missionen im selben Format), `camdrones:planner_settings`.

**Client-Einstellungen:** `config/camdrones-client.json` (Kamera-Ansicht, Einheiten m/s oder km/h, Vorschau-Marker,
Warnton-Lautstärke). Server-Einstellungen gibt es nicht, alles läuft über Welt-Regeln.

### 4.4 Netzwerk (eigene Pakete, Namensraum `camdrones`)

| Paket | Richtung | Inhalt | Prüfung |
|---|---|---|---|
| `mission_upload` | C2S | Seriennummer, Mission | Besitzer oder Rechtestufe 2, Grenzen, Welt-Regeln, ≤ 2/s |
| `mission_command` | C2S | Seriennummer, `START`, `PAUSE`, `RESUME`, `STOP`, `HOLD`, Startindex | Besitzer, ≤ 5/s |
| `mission_progress` | C2S | Seriennummer, Index, Epoche | nur der Pilot |
| `smart_mode` | C2S | Funktion und Parameter | Pilot oder Besitzer, Welt-Regeln |
| `crew` | C2S/S2C | Einladen, Annehmen, Ablehnen, Verlassen | Abstand ≤ 16, `allow_operator`, B8-Regeln |
| `follow_consent` | S2C/C2S | Anfrage an den Zielspieler, Antwort | `allow_follow_players` |
| `status` | S2C | übersetzbare Meldung an den Besitzer (Start, Ende mit Grund aus G4) | – |

Eigene Protokollnummer `camdrones` 1; Propwashs Beitrittsprüfung vergleicht die Add-on-Versionen.

### 4.5 Kompatibilität

| Bereich | Verhalten |
|---|---|
| Propwash | mindestens **0.6.0** (API 1.8, seit dem Greifer im Flugplaner): `PropwashApi.requireVersion("camdrones", "1.8")`, Stände mit API 1.7 (bis `0.6.0-rc.3`) lehnt das Add-on beim Start ab (`core.ApiLevelTest`); Fabric `"propwash": ">=0.6.0-"`, NeoForge `versionRange = "[0.6.0-alpha,)"`, ab dem Umstieg unverändert bis zum Release (nehmen `0.6.0-dev.N`, `0.6.0-rc.N` und `0.6.0` an, 0.6.0-Übersicht §3.2; nicht `>=0.6.0`). Das Gerüst verlangt bis dahin 1.6. |
| Minecraft, Loader | 26.3, Fabric (Loader 0.19.5+, Fabric API) und NeoForge 26.3; Java 25 |
| Server/Client | beide brauchen das Add-on |
| Just More Parts | verträglich; JMP-Akkus, -Motoren und -Props passen nach Propwashs Regeln auf Kamera-Rahmen |
| Mob Defense | verträglich; beide nutzen die Halterung vorn, also nie gleichzeitig an einer Drohne |
| Flashback | optional; Kamerapfad mit Gimbal-Lage (B9) |
| Sharecodes (PW3) | Teile über die Tabelle des Add-ons (nur hinten erweitern); Missionen sind kein Teil von Codes |
| Fehlt das Add-on später | Propwash behält die Drohnen-Daten unverändert (B6); Teile werden zu Propwashs Platzhaltern |

---

## 5. Assets und Sounds

- **Pipeline wie JMP:** unveränderte Kopie von Propwashs `tools/assets/parts` als `tools/assets/propwash_style/`
  (README mit Stand, `SHA256SUMS`), darauf `tools/assets/camstyle/` mit neuen Vorlagen und
  `tools/assets/style_generate.py`, das Modelle, Texturen, Item-Symbole und Lack-Varianten aus
  `data/camdrones/propwash_parts/**` erzeugt. Vergleichsbilder mit `compare_render.py` gegen Propwash.
- **Neue Vorlagen:** geschlossene Rumpfschale mit Faltarmen und Motorgondeln, Landebeine (Kino), 3-Achsen-Gimbal
  (Schwenkmotor, Rollmotor, Neigemotor, Kamerablock; Kopf als eigenes Element `gimbal_head` für G5), Smart-Akku mit vier
  LED-Punkten, Falt-Props, Tablet in der Hand mit Bildschirm, Landeplatz.
- **Stil:** Texeldichte, Texturgröße, Schattierung und Anzeige-Transformationen wie Propwash; matte Kunststoffe in Hellgrau
  (Kompakt), Dunkelgrau (Tour) und Schwarz (Kino), eine Akzentfarbe des Add-ons für Gimbal-Sperre und Prop-Spitzen.
  Lackierbar über `PaintSpec`. Keine Logos, keine Herstellernamen, keine Beschriftung außer „lardtom“-Typenschild.
- **Sounds** (selbst erzeugt wie bei Propwash, keine Fremd-Samples, `.ogg`): `planner.click`, `planner.waypoint`,
  `mission.start` (drei Töne), `mission.done`, `gimbal.calibrate` (Servo-Ton beim Zentrieren und bei der Übernahme),
  `warning.obstacle` (Piepen, Rate nach Abstand), `warning.limit`, `crew.request`. Motorgeräusch kommt von Propwash (niedrige Drehzahl =
  leiser, tiefer).

---

## 6. Tests

Ziel: Der Owner muss nichts selbst prüfen; jeder Punkt der Testliste (§7.2) hat einen automatischen Test oder ein
Bildschirmfoto aus dem Selbsttest.

### 6.1 JUnit (ohne Spielstart)

| Test | Prüft |
|---|---|
| `core.ModInfoTest`, `core.LangFilesTest`, `core.RepoTextTest` (vorhanden) | Mod-ID = Build, nicht `propwash`; API-Version; DE und EN mit gleichen Schlüsseln, Umlaute statt Umschreibung; keine verbotenen Namen im ganzen Repo (Liste als SHA-256-Werte) |
| `logic.PortabilityTest`, `logic.FlightClassTest` (vorhanden) | `logic` ohne Minecraft/Loader/Propwash; Klassenwerte innerhalb der API-Grenzen; volle Mission passt in 2 KiB |
| `logic.MissionCodecTest` | Hin und zurück, Grenzen, falsche Version, kaputte Daten |
| `logic.WaypointFollowerTest` | Punktmassen-Modell mit Wind: alle Punkte ≤ 1 m, Tempo-Grenze, weiche Kurven ohne Überschwingen > 1 m, Wartezeiten |
| `logic.OrbitTest` | Radius ± 0,5 m, Höhe ± 0,3 m, Nase zur Mitte ± 3° |
| `logic.FollowTest` | Abstand 8 ± 1 m, steht, wenn das Ziel steht, kein Pendeln |
| `logic.CourseLockTest`, `logic.AutoShotsTest` | Richtung fest bei Gieren; Bahnen der Automatik-Flüge |
| `logic.ObstacleGuardTest` | Stopp ≥ 1,5 m vor einer Wand aus 12 m/s; Übersteigen |
| `logic.AttiFallbackTest`, `logic.HeightLimitTest` | Wechsel ohne GPS-Fix, Höhengrenze |
| `logic.GimbalAimTest` | Neigung/Schwenk auf Point of Interest und Ziel |
| `bench.FlightTimeTest` | `BuildStats` der drei Presets: Schweben und Tempo im Bereich aus §2.2 |
| `part.PartFilesTest`, `part.RecipeTest` | Teile über Propwashs Builder gültig; Rezepte eindeutig gegen Propwash und JMP, nur vorhandene Items |
| `integration.NavAssistTest` | Adapter setzt `NavOutput` korrekt in `AssistOutput` um (Fake-Ausgabe) |

### 6.2 Selbsttest (Fabric und NeoForge, Quell-Set `devtest`, Bildschirmfotos)

Schritte heißen `<modul>: <was>`, räumen auf, Fotos unter `docs/screenshots/` (EN und DE):

1. `teile: Werkbank zeigt die drei Presets` (Foto)
2. `rezepte: alle Rezepte im Rezeptbuch und herstellbar`
3. `flug: Knüppel los, Drohne hält ≤ 1 m in 5 s` (Foto OSD)
4. `flug: Kino- und Sport-Tempo im Bereich`
5. `flug: unter Blöcken ATTI mit Warnung` (Foto)
6. `hindernis: Stopp vor der Wand` (Foto)
7. `gimbal: Horizont gerade bei 30° Rollen` (Bildauswertung wie Propwash B9, Foto)
8. `gimbal: Neigung −90° per Achse` (Foto)
9. `planer: Editor bei 320×240, 640×480, 854×480 in EN und DE ganz sichtbar` (Fotos, ui-scale-Prüfung wie Propwash)
10. `planer: Wegpunkte per Block-Klick, Vorschau-Linie` (Foto)
11. `mission: Start am Boden, fünf Punkte, Landung` (Foto je Punkt)
12. `mission: Pilot verlässt FPV, Server fliegt weiter` (Foto)
13. `poi: Kreis um ein Leuchtfeuer` (Foto)
14. `follow: Spieler läuft eine Bahn, Abstand gehalten` (Foto)
15. `osd: Add-on-Elemente im OSD-Editor, Warnungen` (Fotos)
16. `hud: Kamera-Ansicht` (Foto)
17. `welt-regeln: Abschnitt „Kamera-Drohnen“, Höhengrenze greift` (Foto)
18. `aufnahme: stabilisierte Aufnahme, Rollwinkel im Bild < 2°`
19. `tasten: keine doppelten Standardtasten` – jede Standardtaste des Add-ons gegen alle `KeyMapping`-Standards im
    laufenden Spiel (Propwash, Vanilla, andere geladene Mods) und gegen die eigenen; ein Treffer ist ein Fehler
20. `aufräumen`

### 6.3 Dedizierter Server (beide Loader)

- **Rauchtest:** Start, Registries, Welt-Regeln, keine ERROR-Zeilen (`runServerSmoke`, Ergebnisdatei wie JMP).
- **Beitritt:** Client mit Add-on tritt bei; Client ohne Add-on bekommt die Meldung.
- **Mission am Server:** Besitzer verbunden, nicht im FPV → Mission läuft; Besitzer trennt → Landung bzw. Heimflug;
  dritte Mission eines Spielers → Meldung „kein Budget“.
- **Zwei-Piloten:** (a) Client ist Operator, ein am Server simulierter Spieler fliegt; (b) Client fliegt, simulierter
  Operator schickt Eingaben (Muster `dev.ZoneJoinCheck` aus Propwash); der Client protokolliert die Gimbal-Lage.
- **Follow me fremd:** Anfrage, Ablehnung, Annahme mit simuliertem Spieler; Welt-Regel aus → verweigert.
- **Dauer:** Mission an der Drohne übersteht Neustart, Aufheben und Ablegen.

### 6.4 Prüfablauf

Pro Strang: JUnit grün, Kompilieren beider Loader, eigener Selbsttest-Ausschnitt. Am Ende: voller Selbsttest beider
Loader mit Fotos, Server-Rauchtests, die Server-Tests aus §6.3, ein unabhängiger Prüfer gegen die Testliste.

---

## 7. Website, Doku, Testliste, Shotliste

### 7.1 Website und Doku

- Seite im Add-on-Verzeichnis auf propwashfpv.com (0.6.0-Strang `site-addons`), EN und DE; Download nur über Modrinth und
  CurseForge.
- Doku-Abschnitt „Kamera-Drohnen“: Erste Schritte (bauen, koppeln, erster Flug), Flugmodi, Flugplaner, intelligente
  Funktionen, Zwei-Piloten, Welt-Regeln, FAQ („Was ist anders als FPV?“, „Warum ATTI?“, „Warum endet die Mission?“),
  Kompatibilität.
- Web-Konfigurator: Teile über den Web-Export wie bei JMP (PW3-Tabelle), Kennzahlen Flugzeit und Tempo.
- Roadmap-Eintrag, Modrinth-/CurseForge-Texte (`docs/modrinth/`), kurzer Spieler-Changelog in `CHANGELOG.md`.
- Flugschul-Kapitel im Spiel: „Kamera-Drohne fliegen“ und „Erste Mission“.

### 7.2 Testliste (Entwurf)

- [ ] Drei Kamera-Drohnen an der Werkbank bauen, Werte im Bereich (Flugzeit, Tempo)
- [ ] Alle Rezepte herstellbar
- [ ] Normal: Knüppel los → bremst und hält
- [ ] Kino und Sport fühlen sich deutlich anders an
- [ ] Unter Blöcken: ATTI-Warnung, Drift mit Wind
- [ ] Hindernis vorn: Stopp; Übersteigen bei Tour und Kino
- [ ] Höhengrenze 120 m greift, Welt-Regel änderbar
- [ ] Gimbal: Horizont gerade, Neigung per Drehrad, Sperre
- [ ] Stabilisierte Aufnahme und Flashback-Kamerapfad
- [ ] Tablet koppeln, Wegpunkte zu Fuß, im Flug und per Aufzeichnung
- [ ] Editor in kleinen Fenstern und beiden Sprachen
- [ ] Mission vom Boden starten, abfliegen, landen
- [ ] FPV verlassen: Mission läuft weiter; Brille auf: Übernahme
- [ ] Halten als Luftstativ über mehrere Minuten
- [ ] Follow me zu Fuß, im Boot, auf dem Pferd
- [ ] Point of Interest mit Radius- und Tempo-Änderung
- [ ] Course Lock
- [ ] Automatik-Flüge: Rückzug, Aufstieg, Kreis, Spirale
- [ ] Zwei-Piloten: einladen, steuern, abgeben, auch während einer Mission
- [ ] OSD-Elemente und Warnungen, Kamera-Ansicht
- [ ] Server: Budget, Besitzer offline, fremdes Follow me nur mit Zustimmung
- [ ] Mit Just More Parts und Mob Defense zusammen

### 7.3 Shotliste (Entwurf)

**Reels** (9:16, 15–30 s, je ein Feature):

| Nr. | Titel | Inhalt |
|---|---|---|
| R1 | „Hands off“ | FPV-Quad stürzt beim Loslassen, Kamera-Drohne bleibt stehen |
| R2 | Mission zu Fuß | Wegpunkte mit dem Tablet um ein Dorf klicken, Drohne fliegt sie ab |
| R3 | Point of Interest | Kreis um eine Burg bei Sonnenuntergang |
| R4 | Follow me | Drohne folgt beim Reiten und Bootfahren; Elytra hängt sie ab (Gag) |
| R5 | Zwei-Piloten | geteiltes Bild: Pilot und Operator, Gimbal schwenkt unabhängig |
| R6 | Enthüllung | Rückzug und Spirale enthüllen ein großes Bauwerk |
| R7 | Hinderniserkennung | Vollgas auf eine Wand, Stopp mit Piep-Warnung |
| R8 | ATTI in der Höhle | GPS weg, Drohne driftet |
| R9 | Gimbal | nebeneinander: FPV-Bild wackelt, Gimbal-Bild ruhig |
| R10 | 40 Minuten | Zeitraffer eines Tour-Flugs gegen 4 Minuten FPV |

**YouTube-Vorstellung** (8–12 min): Einstieg mit R1; was das Add-on ist; Bauen an der Werkbank; Unterschied zu FPV;
Flugmodi; Flugplaner und erste Mission; Follow me, Point of Interest, Course Lock, Automatik-Flüge; Zwei-Piloten;
Server und Welt-Regeln; Kompatibilität und Download.

---

## 8. Backport 1.21.1

**Was hinein kommt:** genau **eine** programmierbare Drohne, die **Tour** (alle Kernfunktionen, beste Flugzeit), mit
Nav-FC Pro, Gimbal Tour (nur Weitwinkel), Smart-Akku, Kamera-Funk, Falt-Props, Motor und dem Flugplaner. Funktionen:
Normal/Kino/Sport, ATTI, Halten, Wegpunkte (Block-Klick, im Flug, Editor), Point of Interest, Follow me (nur Besitzer),
Course Lock, Hindernis-Stopp, Höhengrenze, Gimbal mit Neigung. **Nicht:** Kompakt und Kino, Zwei-Piloten,
Wechselobjektive, Automatik-Flüge, Aufzeichnen, Kamera-Ansicht (optional später), Flashback-Pfad.

**Wie der Code portabel bleibt:**
- Die ganze Flug-, Missions- und Gimbal-Logik liegt in `logic.**`, reines Java ohne Minecraft, Loader oder Propwash
  (`PortabilityTest` erzwingt das). Der Backport kopiert das Paket und die JUnit-Tests unverändert.
- Adapter sind dünn und getrennt: `integration.NavAssist` (Propwash-API ↔ `NavInput`/`NavOutput`) wird im Backport
  durch einen Adapter auf die eingebaute Flughilfe bzw. den Heimflug-Regler ersetzt; Autonomie (B7) baut der Backport
  intern auf seinem Heimflug auf.
- Missionsformat ist ein reines `int[]` (`MissionCodec` in `logic`); die Codec-Hülle für Minecraft ist eine Zeile je
  Version (Data Components gibt es auch in 1.21.1).
- Editor: Layout und Zustand in `logic`-nahen Modellklassen, Zeichnen in einer dünnen Bildschirm-Klasse (26.3 und
  1.21.1 haben andere GUI-Klassen).
- Namen, die sich zwischen den Versionen unterscheiden (z. B. `Identifier` gegenüber dem älteren Namen), nur in Adaptern.
- Teile-JSON, Texte, Modelle und Sounds werden 1:1 übernommen; der Backport-Strang meldet Abweichungen zurück.

---

## 9. Risiken

| Risiko | Folge | Gegenmaßnahme |
|---|---|---|
| Verhalten zu G1–G4 kommt spät (Signaturen ab API-Stand A, Verhalten mit `addon-autonomy` ab Stand B) | Missionen ohne Pilot erst spät testbar | Logik gegen die Signaturen bauen, Selbsttest ab Stand B; Umfang 1.0 notfalls „Missionen mit Pilot“ |
| API-Stand A ändert sich bis Stand B | Umbau in Adaptern | alle API-Aufrufe in `integration`, Änderungen laut `docs/api/changes.md` |
| Server-Last durch autonome Flüge (100 Hz) | Lags | Propwashs Budget, Welt-Regeln, Messung im Server-Test |
| Missbrauch: Ausspähen, Verfolgen | Ärger auf Servern | Follow me fremd nur mit Regel und Zustimmung, Abstandsregel, Zonen (G13) |
| Gimbal-Lage unterschiedlich je Client | Zuschauer sehen anderes Bild | deterministische Berechnung aus B6/B8-Daten, Server-Test (b) |
| Flugzeiten wirken zu lang | Langeweile | realistisch bleiben (Vorgabe); Propwashs Akku-Welt-Regel skaliert |
| Name oder Id mit Markenrecht | Umbenennung nach Release | Mod-ID neutral (E1), Namen vor Entscheidung prüfen, Repo-Scan auf Markennamen |
| Mod-ID ändert sich nach Release | Sharecodes und gespeicherte Drohnen brechen (Namensraum) | Mod-ID vor dem ersten öffentlichen Build einfrieren |
| GUI zu voll bei 320×240 | Editor unbedienbar | Karte einklappbar, Selbsttest Schritt 9 |
| Überschneidung mit dem Community-Cinelifter | Doppelarbeit, Streit | keine Cinelifter-Rigs; Kino-Klasse ist eine GPS-Drohne, kein FPV-Rig |
| Tastenkonflikte (Ziffernblock, Laptops) | Funktionen unerreichbar | Standards nur auf freien Tasten (§2.10), seltene Funktionen ohne Standard, alles frei belegbar, Funke und Tablet bevorzugt, Selbsttest Schritt 19 |
| MC-Testzeit knapp (Slots) | späte Fehler | Logik-Tests in JUnit, MC nur für Integration (§11) |

---

## 10. Entscheidungen (was – warum – verworfen)

| Nr. | Was | Warum | Verworfen |
|---|---|---|---|
| E1 | Mod-ID `camdrones`, Paket `de.lardtom.camdrones` | neutral, beschreibt den Inhalt, passt zu allen Namensvorschlägen | `skycam` (Beispiel im API-Entwurf): Name eines bekannten Seilkamera-Systems; Markennamen von Herstellern |
| E2 | Drohnen aus Teilen an der Werkbank, dazu Presets | Propwash-Prinzip, Web-Konfigurator und Sharecodes funktionieren | fertige Drohnen-Items ohne Teile |
| E3 | GPS im Nav-FC, kein eigenes GPS-Teil | Owner zum GPS-Teil: „bleibt draußen“; einfacher | separates GPS-Modul |
| E4 | Tablet als eigenes Item, Funke bleibt Propwashs | API baut bewusst keine Add-on-Funken; Tablet ist zum Planen ohnehin nötig | eigene Kamera-Funke mit Bildschirm |
| E5 | Programmieren über Block-Klick, Flug und Aufzeichnung plus Karten-Editor | Minecraft-typisch und wie in echt | Koordinaten-Eingabe allein; Skriptsprache oder Programmierblöcke (zu komplex, unrealistisch) |
| E6 | Aktive Mission an der Drohne (B6), Kopien im Tablet | Drohne ist selbstständig, übersteht Aufheben und Neustart | nur im Tablet; Server-Datei |
| E7 | Eine Logik für Pilotenflug (Client) und Autonomie (Server) | weniger Code, gleiches Verhalten | eigenes Flugmodell im Add-on |
| E8 | Drei Quad-Klassen, keine Cinelifter, keine Hexa/Okto | Cinelifter macht die Community; API kennt genau 4 Motoren | Cinelifter-Rig; Sechs- oder Achtmotorige |
| E9 | Follow me fremd nur mit Welt-Regel und Zustimmung | Privatsphäre auf Servern | frei für alle |
| E10 | Nur Halterungen `front` und `top` | Kamera-Drohnen sollen keine Waffenträger sein | Halterung unten |
| E11 | Höhengrenze 120 m als Welt-Regel, Standard an | realistisch, abschaltbar | fest eingebaut; keine Grenze |
| E12 | Vier Automatik-Flüge in 1.0 | kleine Liste, gut für Reels | großer Katalog sofort |
| E13 | Hindernis: Stoppen und Übersteigen | sechs Strahlen reichen dafür | seitliches Umfliegen |
| E14 | OSD-Elemente als Standard, Kamera-Ansicht optional | einheitlich mit Propwash | Kamera-Ansicht als Zwang |
| E15 | Low-Light-Kamera nicht in 1.0 | ohne Owner-Entscheidung (Discord Punkt 24 offen); Wärmebild bleibt beim Community-Add-on | sofort einbauen |
| E16 | Missionen nicht in Sharecodes | API-Entwurf: Add-on-Daten nicht in PW3 | eigener Missionscode in 1.0 (später möglich) |
| E17 | Gerüst gegen 0.5.2 bauen, Release verlangt 0.6.0 | heute baubar, 1.7-Artefakte gibt es noch nicht | warten bis 0.6.0 |
| E18 | Version `1.0.0-dev.N`, erstes Release 1.0.0 | Schema aus `RELEASING.md` von Propwash und JMP | öffentliche Nummern für Zwischenstände |
| E19 | Lizenz wie Mob Defense: PolyForm Shield 1.0.0 mit zusätzlichen Erlaubnissen, Doku MIT, NOTICE (Owner-Entscheidung 10.11.2026; bis dahin „All Rights Reserved“) | Lizenzwahl ist Owner-Sache | Lizenz ohne Rückfrage festlegen |
| E20 | Verbotene Namen im Repo-Test nur als SHA-256 | der Test selbst darf die Namen nicht enthalten | Klartext-Liste |
| E21 | Tastatur-Standards nur auf von Propwash und Vanilla freien Tasten (Ziffernblock / * − ,; Einfg; Bild↑/↓), seltene Funktionen ohne Standard | Propwash belegt Ziffernblock 0–9, Enter und +; ein doppelter Standard löst zwei Funktionen aus (etwa Mission starten und 3D-Modus) | Ziffernblock 1–7 wie im ersten Entwurf |
| E22 | Flug ohne Pilot dauert so lange, wie der Akku reicht; keine eigene Welt-Regel für die Dauer | wie bei echten Kamera-Drohnen; der 0.6.0-Entwurf begrenzt mit dem Akku und hart mit 60 min (E21 dort); `allow_autonomy` reicht Server-Betreibern | `max_autonomy_minutes` (30 min, 1–120); Propwash-Welt-Regel für die Dauer |

---

## 11. Arbeitspakete und Stränge

Konto laut Plan: MAX (Kamera-Add-on). Jeder Strang in eigenem Worktree `feature/<name>`, Merge in `main`, nur eigene
Dateien. MC-Zeit = Minecraft-Slot-Stunden.

| Strang | Phase | Abhängig von | Dateibesitz | MC-Zeit |
|---|---|---|---|---|
| S0 `scaffold` | erledigt | – | Gradle, Metadaten, Loader-Glue, `CamDrones`, `platform`, Test-Gerüst, `docs/design.md` | 0 |
| S1 `logic-core` | jetzt | – | `logic/**`, `test/.../logic/**` | 0 |
| S2 `parts-data` | jetzt (gegen 1.6) | – | `data/camdrones/propwash_parts/**`, `data/camdrones/recipe/**`, `part/**`, `common/src/lang/parts/**`, `test/.../bench/**`, `test/.../part/**` | 0,5 h |
| S3 `assets` | jetzt | S2 (Teile-JSON) | `tools/assets/**`, `assets/camdrones/models|textures|items|sounds/**`, `sounds.json` | 1 h |
| S4 `planner-ui` | jetzt (Editor und Item ohne Drohnen-Daten) | S1 | `planner/**`, `client/planner/**`, `net/**` (Planer-Pakete), `common/src/lang/planner/**` | 1,5 h |
| S5 `api17-adapters` | ab API-Stand A | S1, 0.6.0-dev in mavenLocal | `integration/**`, `client/input/**`, `common/src/lang/core/**`, Umstellung `gradle.properties` und `REQUIRED_API` | 1 h |
| S6 `autonomy` | ab Stand A, Test ab Stand B | S5, G1–G4 | `integration/autonomy/**`, `command/**`, `net/**` (Missions-Pakete) | 3 h |
| S7 `gimbal-crew` | ab Stand A | S5 | `client/gimbal/**`, `crew/**`, `net/**` (Crew-Pakete) | 2 h (zwei Clients am Server) |
| S8 `hud` | ab Stand A | S5 | `client/hud/**`, `common/src/lang/hud/**` | 1 h |
| S9 `selftest` | ab Stand B | S2–S8 | `common/src/devtest/**`, `docs/screenshots/**` | 3 h |
| S10 `docs-site` | ab Stand B | S9 | `README*.md`, `docs/**` außer `design.md`, `CHANGELOG.md`, `tools/web_export*`, Website-Texte als Zulieferung | 0,5 h |
| S11 `release` | gegen fertiges 0.6.0 | alle | Version, Release-Jars unter `release/`, Prüfprotokoll | 2 h |
| S12 `backport-handover` | nach S6 | S1, S6 | Übergabe-Notiz an den Backport-Strang, keine Dateien hier | 0 |

`net/**` wird zwischen S4, S6 und S7 nach Paketen aufgeteilt (je eine Datei pro Paket, gemeinsame Anmeldung
`net/CamNetwork.java` gehört S4, die anderen hängen nur eine Zeile an). Summe MC-Zeit etwa 15,5 Slot-Stunden.
