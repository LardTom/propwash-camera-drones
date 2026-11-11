# Tests – Propwash: Camera Drones

Ziel aus dem Entwurf (`design.md` §6): Jeder Punkt der Testliste hat einen automatischen Test oder ein Bildschirmfoto aus
dem Selbsttest. Alle Befehle laufen im Wurzelverzeichnis des Repos; Propwash muss vorher im lokalen Maven-Repository
liegen (Nummer in `gradle.properties`, `propwash_version`).

## 1. Ohne Spielstart

| Befehl | Prüft |
|---|---|
| `./gradlew :common:test` | JUnit: Fluglogik (`logic`, Punktmassen-Modell mit Wind), Missionsformat, Teile und Rezepte, Prüfstand der Presets (`bench.FlightTimeTest`), Adapter auf die Propwash-API, Planer- und Crew-Modelle, Sprachdateien, Repo-Texte |
| `./gradlew :common:checkShareTable` | PW3-Teile-Tabelle mit Propwashs `ShareTableCheck` (§4) |
| `./gradlew build` | beide Loader kompilieren, JUnit und `checkShareTable` (hängt an `check`) |

`core.RepoTextTest` sucht im ganzen Repo nach verbotenen Namen (als SHA-256-Werte im Test), `core.LangFilesTest`
verlangt gleiche Schlüssel in DE und EN und Umlaute statt Umschreibungen.

## 2. Selbsttest (Fabric und NeoForge)

```sh
./gradlew :fabric:runClientSelftest
./gradlew :neoforge:runClientSelftest
./gradlew :fabric:runClientSelftest -PcamdronesSelftestOnly='mission:,poi:'
./gradlew :fabric:runClientSelftest -PcamdronesScreenshots
```

- Quell-Set `common/src/devtest`, flache Kreativwelt mit Wind 1, Ergebnis in
  `<loader>/runs/selftest-client/camdrones-selftest-result.txt`; der Task scheitert ohne `PASS`.
- `-PcamdronesSelftestOnly=<präfixe>` (kommagetrennt) wählt Schritte nach dem Anfang ihres Namens.
- `-PcamdronesScreenshots` schreibt die Fotos nach `docs/screenshots/` (1920×1080, EN und DE); nur auf Fabric.
- 35 Schritte, je `<modul>: <was>`: Werkbank mit drei Presets, Rezepte, Halten ≤ 1 m in 5 s, Kino- und Sport-Tempo,
  Halten bei starkem Wind, ATTI unter Blöcken, Stopp vor der Wand, Übersteigen in der Mission (Tour und Kino), Horizont
  beim Rollen, Neigung −90°, Gimbal-Sperre und Folgen beim Gieren (Tour und Kino), Editor bei 320×240, 640×480 und
  854×480 in EN und DE, Wegpunkte per Block-Klick, Koppeln per Rechtsklick mit dem Flugplaner, Wegpunkt im Flug per
  Taste mit der Gimbal-Neigung des Piloten (Gimbal vorher unter −40° geneigt) und „Flug aufzeichnen“, Mission vom Boden
  mit Landung, Übergabe an den Server beim Verlassen der FPV-Ansicht, Brille wieder auf (Mission pausiert, Fortsetzen
  per Taste), Kreis um ein Leuchtfeuer, Follow me zu Fuß, im Boot und auf dem Pferd, Luftstativ über drei Minuten,
  OSD-Elemente und Warnungen, Kamera-Ansicht, Welt-Regeln mit Höhengrenze, stabilisierte Aufnahme, keine doppelten
  Standardtasten, Kompassfehler, Welt-Regeln für autonome Flüge, Course Lock, Automatik-Flüge über das Tablet (Rückzug,
  Aufstieg, Kreis, Spirale), Follow me über das Tablet (Hinterher, Parallel und Blick ohne Pilot, danach folgt der
  Schalter Follow me im Pilotenflug im zuletzt gewählten Stil, Schritt 40), Aufräumen.
- Flashback (nur Fabric): Liegt das Flashback-Jar in `fabric/runs/selftest-client/mods/`, läuft zusätzlich
  „aufnahme: Flashback-Kamerapfad folgt dem Gimbal“ (Tour mit Propwashs Action-Cam (Flashback) oben, der Bildschirm
  bleibt die FPV-Kamera; Roll des Kamerapfads unter 2° bei 35° Rollen, Neigung folgt dem Gimbal, Sichtfeld höchstens 1°
  neben der Gimbal-Kamera, §17.1); ohne Flashback ist der Schritt nicht angemeldet. Ebenso nur mit Flashback:
  „aufnahme: Flashback-Modus nimmt im Hintergrund auf …“ (802, §26).
- Zusammenspiel mit anderen Propwash-Add-ons: Jars in `<loader>/runs/selftest-client/mods/` legen und
  `CAMDRONES_SELFTEST_COMPANIONS=<mod-id>,<mod-id>` setzen; dann prüft „zusammenspiel: …“, dass jedes Add-on geladen und
  bei Propwash aktiv ist, Teile mitbringt und keine Standardtaste mit einer eigenen zusammenfällt. Gezielt etwa
  `-PcamdronesSelftestOnly=1,2,3,11,19,35`; danach die Jars wieder entfernen.
- Gesperrt (ohne Anmeldung, bis Propwash die Übernahme nahtlos macht): `TakeoverSteps.registerSeamless()` – die
  Drohne bleibt beim Aufsetzen der Brille während eines Flugs ohne Pilot scharf in der Luft.

## 3. Dedizierter Server

```sh
./gradlew :fabric:runServerSmoke
./gradlew :neoforge:runServerSmoke
```

Rauchtest: Start, Teile, Presets, Welt-Regeln, Drohnen-Daten, Flugplaner, Flüge ohne Pilot, `/camdrones`, die vier
Fortschritte im eigenen Tab, keine `ERROR`-Zeilen im Log; Ergebnis in `<loader>/runs/smoke-server/camdrones-selftest-result.txt`.

**Server-Tests mit Client** (Port aus der Server-Ausgabe, `25631 + PROPWASH_MC_SLOT`):

```sh
CAMDRONES_SERVERTEST=<fall> CAMDRONES_JOINTEST_HOLD=1800 ./gradlew :fabric:runServerSmoke
CAMDRONES_JOINTEST=127.0.0.1:<port> CAMDRONES_SERVERTEST=<fall> CAMDRONES_JOINTEST_LABEL=<fall> \
  CAMDRONES_JOINTEST_EXPECT=<erwartete zeilen> ./gradlew :fabric:runClientSmoke
```

| Fall | Prüft |
|---|---|
| `mission` | Mission am Server, Besitzer trennt: Landung an Ort und Stelle; hängt die Drohne 10 s reglos in der Luft oder wird sie entladen, ist das FAIL; zweiter Flug desselben Spielers meldet „kein Budget“ |
| `crew` | Zwei-Piloten: (a) die Drohne eines simulierten Spielers fliegt eine Mission am Server, der Client nimmt die Einladung über den Chat-Link an, ist Operator, gibt mit Esc ab, die Mission fliegt weiter; (b) der Client lädt den simulierten Operator über den Knopf „Crew“ in der Wartung ein, fliegt selbst, der Operator schickt Neigungen bis −60° (das Pilotenbild bleibt dabei höchstens 5° neben der FPV-Kamera am Rahmen, OSD zeigt OPERATOR) und gibt per Befehl ab; (c) die Drohne des Clients fliegt allein eine Mission am Server, der Client steuert im Livebild die Kamera allein, der simulierte Spieler nimmt eine Einladung an und hat Vorrang (§50) |
| `crew-rejoin` | Crew übersteht das Trennen (§37, §38): (a) der Client ist Operator der Drohne eines simulierten Spielers, trennt mitten in der Bindung und kommt nach 5 s zurück (`JoinProbe.rejoin`); die Crew wartet 2:00, stellt sich nach der Rückkehr binnen 5 s von selbst wieder her (Propwash-Operator ist der neue Client, Operator-Bild, „Crew wiederhergestellt“), dann gibt er per Befehl ab; (c) der simulierte Spieler ist Operator der Drohne des Clients, verlässt das Spiel und kommt nach 5 s im Nether zurück: Propwash lehnt die Neubindung ab, er liest binnen 5 s genau einmal „… kannst die Kamera noch nicht übernehmen …“ und danach jede Sekunde die Restzeit, der Client liest „… ist zurück, kann die Kamera aber noch nicht übernehmen …“ und sieht die Restzeit weiterlaufen, nach 2:00 endet die Crew mit „… konnte die Kamera nicht rechtzeitig übernehmen“; (b) der Client lädt den simulierten Spieler als Operator seiner Drohne ein, der verlässt das Spiel: Hinweis im Chat, Restzeit in der Aktionsleiste, die Crew wartet nach 60 s noch und endet nach 2:00 mit „nicht rechtzeitig zurückgekommen“ |
| `follow` | Follow me auf einen simulierten Spieler: Anfrage, Ablehnung, Annahme, Abstand beim Gehen, Welt-Regel aus |
| `persist-write`, danach `persist-check` | Mission an der Drohne übersteht Aufheben, Ablegen und Neustart (`persist-check` mit `-x prepareServerSmoke`) |
| `awards` | Fortschritte (§46): Gimbal im Inventar öffnet den Tab, der Client fliegt mit einem simulierten Kameramann (beide bekommen „zu zweit“), danach fliegt dieselbe Drohne eine Mission am Server, der Client nimmt im Livebild ein Video auf (Paket `camdrones:video_recorded`); der Client sieht im Tab alle vier erledigt (Erwartung `awards tab root=done crew_flight=done video=done mission=done`) |

**Beitritt ohne Add-on:** `./gradlew :fabric:runClientNoAddon` (und `:neoforge:`) verbindet einen Client mit Propwash,
aber ohne das Add-on, und erwartet Propwashs Beitritts-Übersicht mit Name und Version des Add-ons statt eines Fehlers.

## 4. PW3-Teile-Tabelle

`:common:checkShareTable` ruft `de.lardtom.propwash.api.share.ShareTableCheck check` aus dem Propwash-Jar mit
`--resources common/src/main/resources --version <version>` auf, ab dem ersten Release zusätzlich mit
`--baseline common/sharecode-baseline.json`. Befunde brechen den Build ab (Regeln in [`sharecode.md`](sharecode.md)).

## 5. Web-Export

```sh
./gradlew :fabric:runWebExport
python3 tools/web_export.py
```

- `runWebExport` startet einen dedizierten Fabric-Server mit dem Add-on (ohne `devtest`), schickt über die Konsole
  `propwash webexport all` und `stop`, sobald der Server bereit ist, und scheitert ohne `PASS`-Zeile oder mit
  `ERROR`-Zeilen im Log. Ergebnis in `fabric/runs/web-export/propwash/web-export/`, Kopie in `build/web-export/`.
  `-PcamdronesWebExportSelection=camdrones` exportiert nur den Namensraum des Add-ons (ohne `base/`).
- `tools/web_export.py [<ordner>]` prüft den Export gegen das Repo: Format 2 und Add-on-Version, `problems.json` ohne
  Fehler und ohne Hinweis zum Add-on, alle Teile je Art mit Namen in DE und EN und vorhandenen Modell- und
  Texturdateien, `share_table.json` gleich der PW3-Tabelle, Versionsbereich wie in `gradle.properties`, drei Presets mit
  PW3-Code, sauber baubar, Schwebezeit im Bereich der Flugklasse (`logic.nav.FlightClass`) und Höchsttempo über dem
  Sport-Tempo. Exit-Code 1 bei Befunden.
- Aufbau und Felder des Exports beschreibt Propwash (`docs/web-export.md` im Propwash-Repo). Die Website liest daraus den
  Katalog des Add-ons (Teile, Presets, Kennzahlen, Texte, Modelle).

Stand gegen Propwash 0.6.0-dev.7: 19 Teile, 3 Presets, Tabelle mit 19 Einträgen, 0 Warnungen; Kompakt 244 g, 29,7 min
Schweben, 66 km/h; Tour 720 g, 41,4 min, 81 km/h; Kino 3805 g, 26,0 min, 105 km/h.

## 6. Nicht automatisch geprüft

Aus der Testliste (`design.md` §7.2) bleiben für Menschen: das Gefühl der Flugmodi, die Bedienung mit echter Funke
bzw. echtem Gamepad als Operator (Operator-Knüppel und Aktion 4 lang lassen sich ohne Gerät nicht erzeugen) und das
Zusammenspiel mit anderen Add-ons in einer längeren Spielsitzung.

## 7. Abdeckung cov-cam-a

Prüfplan 0.6.0, Teil 8.1–8.6 (Teile, Werkbank, Flug, Gimbal, Planer, Missionen). Alle Klassen tragen das Präfix `CovCamA`.

- JUnit: `part.CovCamAPaintTest` (Grundfarben Hellgrau, Dunkelgrau und Schwarz, Lack auf Schale, Motoren und Gimbal,
  eine Akzentfarbe an allen Gimbals und Prop-Spitzen) und `logic.CovCamABatteryReturnTest#fullBatteryDoesNotReturn`.
  Seit dem Fix von cov-cam-a-1 frei: `part.CovCamABuildCheckTest` (Werkbank-Erweiterung angemeldet), dazu
  `part.CameraCheckTest` (Presets vollständig, je fehlendes Teil genau ein benannter Fehler, Texte EN/DE).
  Gesperrt mit `@Disabled("cov-cam-a-<n>")`, bis der Befund behoben ist:
  `part.CovCamASmartBatteryTest`, `logic.CovCamABatteryReturnTest#lowBatteryRequestsReturnHomeOnceWithWarning`,
  `integration.CovCamAExitSettingTest`, `integration.CovCamACrashTest`, `client.gimbal.CovCamAGimbalFpvTest`,
  `planner.CovCamAPreviewSettingTest`. Seit dem Fix von cov-cam-a-5 und cov-cam-a-10 wieder aktiv:
  `integration.CovCamAExitSettingTest` (Verhalten beim Verlassen wird an der Drohne gesetzt) und
  `planner.CovCamAPreviewSettingTest` (Client-Einstellung `config/camdrones-client.json`, `WaypointPreview` fragt sie ab);
  dazu `planner.ExitSettingTest`, `planner.PlannerInteractionsTest`, `planner.MissionWindowLayoutTest`,
  `client.planner.CamClientConfigTest` und die erweiterten `planner.EditorLayoutTest` und `net.PlannerPacketsTest`. Davon seit dem Fix von cov-cam-a-2 wieder aktiv: `part.CovCamASmartBatteryTest`
  (jede Ladestufe wählt in jeder Item-Ansicht das Modell mit einer LED je angefangenem Viertel, weniger LEDs leuchten
  mit weniger Pixeln); Schritt 303 prüft dasselbe im Spiel bei 100, 75, 50, 25 und 0 % in GUI und Hand. Seit den
  Fixes von cov-cam-a-3 und cov-cam-a-6 wieder aktiv: `logic.CovCamABatteryReturnTest#lowBatteryRequestsReturnHomeOnceWithWarning`
  und `integration.CovCamACrashTest`, dazu `logic.BatteryReturnTest` (Rückkehr unter 20 %, Landung unter 10 %),
  `integration.NavAssistBatteryTest`, `integration.NavAssistCrashTest` und `integration.CamCrashTest`; für cov-cam-a-15
  und cov-cam-a-19 `integration.NavAssistAltitudeTest` (Höhenregler wie Propwash mit danebenliegender Höhen- und
  Tempo-Schätzung), `integration.NavAssistGroundRayTest` (Bodentreffer der geneigten Sensoren) und `logic.VerticalControlTest`.
  Seit dem Fix von cov-cam-a-7 wieder aktiv: `client.gimbal.CovCamAGimbalFpvTest` (im Gimbal-Modus FPV rollt das Bild
  mit dem Rahmen, die Neigung bleibt stabilisiert).
- Selbsttest (Nummern 301–353, angemeldet in `dev/client/CovCamASteps`): gezielt
  `'-PcamdronesSelftestOnly=301-305,311-313,315,317-325,330,331,344,350-353'` (etwa 23 min, davon 4 min Luftstativ). Mit
  `-PcamdronesScreenshots=true` entstehen die Bilder als `docs/screenshots/covcama_*.png` (nicht einchecken); Prüfbilder
  der Schritte liegen in `runs/selftest-client/covcama/`.
- 331 Gimbal-Modus FPV: Taste Scroll Lock für den Schalter *Gimbal FPV* belegen, Schalter an, Sport-Modus, nach
  rechts rollen; das Bild muss bis auf 5° mit dem Rahmen rollen; danach Schalter aus (Gimbal wieder *Folgen*), Taste
  zurück. Foto `covcama_gimbal_fpv_roll`.
- 302 Werkbank-Knopf „Kamera-Drohne prüfen“: Knopf im Kopf der Werkbank (englisch „Camera“; deutsch unter ⋯, weil der
  Kopf neben „Lackierung“ zu schmal ist), Tour vollständig, Tour ohne Gimbal nennt das fehlende Gimbal; Fotos
  `covcama_check_button`, `covcama_check_tour` und `covcama_check_no_gimbal` (EN und DE).
- 311–313 Normal-Tempo, Steigen und Sinken je Klasse (Wind aus, ±15 %, Gas Mitte hält 5 s auf 0,3 m), 317 Rückkehr bei
  knappem Akku (Akkulaufzeit 2 %, GPS Rescue ab 20 %), 318 Luftstativ 3 min (je Richtung höchstens 1 m), 323 Crash
  beendet Course Lock: seit den Fixes von cov-cam-a-15, -3, -19 und -6 angemeldet; seit GCCA3-2 schaltet der Schritt
  nach dem Crash wirklich wieder scharf (liegt die Drohne schief oder auf dem Rücken, zuerst Propwashs Taste Aufrichten;
  dann Gas ganz herunter, sonst sperrt Propwash mit THROTTLE_NOT_LOW, dann die Arm-Taste) und verlangt danach 5 s lang
  Funktion NONE, keine laufende Mission und Drohnen-Daten NONE, während der Schalter Course Lock weiter an steht;
  gelingt das Scharfschalten nicht, ist der Schritt FAIL; neu 325 Schaden am Server beendet eine Mission ohne Pilot
  (abgebrochen, Drohne landet; Klasse `DamageSteps`). Das Kino-Steigen erreicht mit Propwashs Höhenregler (Steigrate
  höchstens 8 m/s, Integral ±4 m/s²) je nach Flug 6,8 bis 7,6 m/s (Soll 8 m/s, Grenze 6,8 m/s) und liegt damit knapp an
  der Grenze.
- Seit dem Fix von cov-cam-a-5, -8, -9, -10 und -11 angemeldet (Filter `'-PcamdronesSelftestOnly=planer:,wartung:'`):
  340 Koppeln mit Schleichen + Rechtsklick (danach öffnet Schleichen + Rechtsklick mit leerer Hand weiter die Wartung),
  341 Wartungsknöpfe *Mission* und *Crew*, beide öffnen ein Fenster im Propwash-Stil, 342 Vorschau-Marker im Tab
  *Kamera-Drohnen* der Einstellungen ausschalten (Strg+S), danach keine Marker in der Welt und alle Punkte im Editor,
  343 Livebild im Planer (abgestellte Drohne: Meldung statt schwarzem Bild; schwebende Drohne mit Brille: Kamera auf der
  Drohne), 345 Verhalten beim Verlassen im Editor auf heim, landen, schweben und zurück stellen, jeweils am Server an der
  Drohne geprüft, und im Fenster *Mission* der Wartung derselbe Wert. Fotos `covcama_settings_preview`,
  `covcama_live_button`, `covcama_live_view`, `covcama_exit_setting` und `covcama_mission_window`.
- Fertig, aber ohne Anmeldung bis zum Fix: 314 Windversatz, 316 Restflugzeit bei Akkulaufzeit 10 % (im Lauf nach 315
  auf Fabric unzuverlässig). Freigeben: Aufruf in `CovCamASteps` ergänzen.
- Seit dem Fix von cov-cam-a-13, -14 und -17 angemeldet: 321 FPV verlassen mit „heim“ (Übergabe ohne Absacken, 1 m
  steigen, dann Propwashs Heimflug mit Landung am Homepoint), 352 Start ohne GPS unter einer Steindecke (Status
  NOT_READY, Text nennt den GPS-Fix, Drohne bleibt am Boden und unscharf), 353 Mission mit leerem Akku (NOT_ARMABLE, Text
  nennt den leeren Akku). Die Prüfungen dazu ohne Spiel: `integration.autonomy.StartCheckTest`, `ExitAndEndDecisionTest`,
  `AutonomyAssistTest`.
- Seit GCCA12C-1 angemeldet: 351 präzise Landung auf dem Block `camdrones:landing_pad` (Tour auf dem Landeplatz,
  Mission mit Ende „zum Start“, Aufsetzen höchstens 0,3 m neben der Mitte); Einzelheiten in §19.
- Beitrittstest-Fall `covcama` (fremde Drohne koppeln, Marker nur beim Halter, Gimbal-Kopf am Modell mit simuliertem
  Operator): Server mit `CAMDRONES_SERVERTEST=covcama CAMDRONES_JOINTEST_HOLD=1800 ./gradlew :<loader>:runServerSmoke`,
  Client mit `CAMDRONES_JOINTEST=127.0.0.1:<port> CAMDRONES_SERVERTEST=covcama CAMDRONES_JOINTEST_LABEL=covcama-<loader>`
  und `CAMDRONES_JOINTEST_EXPECT='joined|pair foreign not_owner|pair foreign no_link|markers none 0|markers own 3|gimbal photos 5'`
  über `./gradlew :<loader>:runClientSmoke`. Fünf Bilder `covcama_gimbal_01…05.png` in `runs/smoke-client/jointest/`.

## 8. Lücken aus der Doku: Rezepte, Landeplatz, Ansicht, Flugschule

Befunde des Doku-Strangs (Flugplaner ohne Rezept, kein Umschalter für die Kamera-Ansicht, Landeplatz und
Flugschul-Kapitel fehlen), geschlossen mit diesen Tests:

- JUnit: `part.RecipeTest` (Flugplaner `IGI`/`IMI`/`IBI` und Landeplatz `KWK`/`SSS` wie im Entwurf §2.5, eindeutig gegen
  Propwash, Vanilla, Just More Parts und Mob Defense; Mob Defense als Schnappschuss `part.MobDefenseRecipeSnapshot`, gegen
  einen Checkout mit `CAMDRONES_MD_RECIPES=<pfad>/data/mobdefense/recipe`), `pad.LandingPadFilesTest` (Blockzustand,
  Modell 2 Pixel hoch, Beute, Spitzhacke, Namen EN/DE), `planner.ViewSettingTest` (Umschalter OSD/Kamera, Paket
  `camdrones:view_setting`, Server-Prüfungen, Texte), `planner.MissionWindowLayoutTest` (Zeile *Ansicht* im Fenster
  *Mission* bei 320×240 bis 854×480 in EN und DE), `client.school.CamSchoolTest` (drei Kapitel im Namensraum
  `camdrones`, alle Texte in EN und DE innerhalb der Längen der Flugschule, Tastenhinweise auf vorhandene Tasten).
- Selbsttest 401–405 (`dev/client/SurvivalCraftSteps`, `LandingPadSteps`, `ViewToggleSteps`, `SchoolSteps`), gezielt
  `'-PcamdronesSelftestOnly=2,9,301,401-405'` (etwa 2 min):
  - 401 `rezepte: Flugplaner und Landeplatz im Überleben über das Rezeptbuch` – Überleben, leeres Inventar, beide Rezepte
    gesperrt; das erste Propwash-Teil schaltet den Flugplaner frei, das Rezeptbuch legt die Zutaten in die Werkbank, das
    Ergebnis kommt ins Inventar; der Flugplaner schaltet den Landeplatz frei; danach keine Zutat übrig.
  - 402 `landeplatz: setzen, Drohne darauf, abbauen` – Block und Item angemeldet, im Kreativ-Tab, im Überleben gesetzt
    (Kollision 0,125 m), Tour steht darauf, Abbauen droppt ihn, ohne Block darunter fällt er ab.
  - 403 `wartung: Ansicht OSD/Kamera umschalten` – Fenster *Mission* der Wartung, Klicks auf *Ansicht* speichern
    `camdrones:settings` (view) an der Drohne, *Verlassen* bleibt; danach im FPV die Kamera-Ansicht statt des OSD.
  - 404 `planer: Ansicht im Editor umschalten` – Feld *Ansicht* neben *Verlassen*, Klicks speichern an der Drohne.
  - 405 `flugschule: Kapitel der Kamera-Drohnen` – Flugschul-Taste bietet die Kapitel an Flugplaner, Landeplatz,
    Rahmen, Nav-FC und Gimbal an; alle drei Kapitel öffnen und laufen.
- Bildschirmfotos (EN und DE, `-PcamdronesScreenshots=true`): `survival_recipe_flight_planner`,
  `survival_recipe_landing_pad`, `landing_pad_drone`, `view_setting_service`, `view_setting_hud`, `view_setting_editor`,
  `flight_school_camera_drone`, `flight_school_camera_drone_obstacle` (Schritt 5/5, §36), `flight_school_first_mission`,
  `flight_school_follow_poi`.
- Die präzise Landung auf diesem Block prüft Schritt 351 (§7 und §19).

## 9. Namen der OSD-Elemente bei GUI 320×240

Befund cov-combo-2: Propwash kürzt im OSD-Editor einen Namen, der auch auf 70 % gestaucht nicht in die Liste passt. Bei
GUI 320×240 ist die Liste schmal (mit dem Tab *Kamera-Drohnen* ist die Seitenleiste breiter), und die Namen „Camera
drone: …“ bzw. „Kamera-Drohne: …“ waren alle auf denselben Anfang gekürzt.

- JUnit `client.hud.CamOsdNamesTest`: jeder Name der fünf OSD-Elemente passt in EN und DE ungekürzt in die schmalste
  Liste des OSD-Editors (mindestens 112 px, davon 75 px für den Namen; gemessen mit der Vanilla-Schrift) und gleicht
  keinem Element-Namen von Propwash.
- Selbsttest 39 `osd: Namen der Add-on-Elemente bei GUI 320×240 in EN und DE ungekürzt`: Fenster 640×480 mit
  GUI-Skala 2, OSD-Tab, Gruppe *Add-ons*; jeder Name muss genau so zu sehen sein, gekürzte Namen nennt der Schritt.
  Gezielt mit den Schritten 15, 16 und 319: `'-PcamdronesSelftestOnly=osd:,hud:'` (etwa 1 min).

## 10. Abdeckung cov-cam-b

Prüfplan 0.6.0, Teil 8.7–8.14 (intelligente Funktionen, Akku, Zwei-Piloten, Tasten, OSD und Töne, Welt-Regeln, Server,
Zusammenspiel) und Teil 6.1 (Seite „Add-ons“ im Tab „Modi“). Alle Klassen tragen das Präfix `CovCamB`.

- JUnit: `integration.CovCamBControlsTest` (Follow me und Course Lock rasten am Gamepad ein und melden jeden Wechsel an den
  Server, Gimbal zentrieren ist ein Taster), `integration.CovCamBPoiRangeTest#pointOfInterestBeyondSixtyFourMetresOrSkyIsRefused`
  (Strahl bis 64 m, ohne Treffer bzw. Treffer erst in 80 m kein Kreis) und `#refusedPointOfInterestTellsThePilot` (danach
  zwei Sekunden *NO POI* im Status), `client.hud.CovCamBSoundsTest#everyDesignSoundIsDeclaredWithAnOggFile`
  (acht Töne aus dem Entwurf mit Ogg-Datei und Untertitel), `bench.CovCamBJmpBuildTest` (Tour mit Akku, Motor und Prop aus Just More
  Parts baubar, Kennzahlen plausibel, größerer Akku gleicher Zellzahl schwerer und länger in der Luft; läuft nur mit
  `CAMDRONES_JMP_PARTS=<JMP-Checkout>/common/src/main/resources/data/justmoreparts/propwash_parts`, dann einzeln mit
  `--tests '*CovCamBJmpBuildTest'`, weil die JMP-Teile in Propwashs Teile-Register landen).
  Nichts mehr gesperrt: `client.hud.CovCamBUnitsTest` ist seit dem Fix von cov-cam-b-5 wieder aktiv (§11),
  `CovCamBSoundsTest#everyDeclaredSoundIsPlayedSomewhere` seit den Fixes von cov-cam-b-5 (`warning.limit`, cov-cam-b-11) und
  cov-cam-b-6 (`gimbal.calibrate`). Seit dem Fix von cov-cam-b-6:
  `CovCamBSoundsTest#gimbalCalibrateIsPlayedByTheGimbal` und `client.gimbal.GimbalServoToneTest` (seit FB1C-1, §47: kein Ton
  beim Bewegen, `gimbal.calibrate` nur beim Zentrieren und bei der Übernahme durch den Kameramann, höchstens einmal je
  16 Ticks).
- Selbsttest (Nummern 501–552, angemeldet in `dev/client/CovCamBSteps`), gezielt
  `'-PcamdronesSelftestOnly=501-505,511-518,521,531-539,541'` (etwa 19 min, am besten in vier Teilen):
  - 501 Seite „Add-ons“ im Tab „Modi“ in EN und DE: alle 15 Funktionen aus `design.md` §2.10 mit Namen, die drei Achsen mit
    „Analoger Kanal“, kein roher Schlüssel.
  - 502 jede Standardtaste (Ziffernblock / − * und Komma, Einfg, Bild↑/↓) im Flug: genau ihre Funktion bzw. Achse ändert sich,
    keine Propwash-Taste ist gedrückt; 503 Ziffernblock 0–9, Enter und + (je zweimal, damit Propwashs Schalter zurückstehen):
    keine Kamera-Funktion, die Propwash-Taste feuert, danach steigt die Drohne normal.
  - 504 Tastatur ohne Ziffernblock: die Kamera-Tasten auf freie Tasten (alle Buchstaben sind durch Vanilla, Debug-Kürzel und
    Propwash belegt, frei bleiben acht Zeichentasten), jede wirkt, Schalter rasten beim zweiten Druck aus; Course Lock auf der
    Taste von „Scharf schalten“ ergibt einen erkannten Konflikt (Foto der Tastenbelegung). 505 Follow me und Course Lock je
    dreimal: je Druck genau ein Wechsel und genau ein `ADDON_SWITCH_CHANGED` am Server; Gimbal zentrieren nur, solange gedrückt.
  - 511 Follow me Parallel (seitlich 8 ± 2 m) und Blick (bewegt sich unter 1 m, Nase ± 5° auf den Spieler), gestartet über das
    Paket `smart_mode` mit Stil; 512 im Boot und auf dem Pferd (Abstand 8 ± 1,5 m, Gimbal im 95-%-Wert ± 5°, kein Zielverlust);
    513 Ziel 5 s mit 30 m/s: Drohne fällt zurück, kein Absturz, holt auf, außer Funkreichweite Ende `OUT_OF_RANGE` mit Meldung.
  - 514 Point of Interest in den Himmel und auf eine Wand in 80 m: kein Kreis, Gegenprobe Wand in 40 m kreist; 515 wie 514,
    dazu je Ablehnung eine Meldung in der Aktionsleiste.
  - 516 Automatik-Flüge über das Tablet: Bahn je Flug in `runs/selftest-client/covcamb/auto_<flug>.txt`, Vergleich mit dem Soll
    aus `AutoShots`, Dauer 10–20 s, danach Halten auf 0,5 m.
  - 517 Mission ohne Pilot, Besitzer im Überleben 1400 m weg: Ende `OUT_OF_RANGE`, Meldung `ended_out_of_range`.
    518 (seit dem Fix von cov-cam-b-3) dasselbe mit der Mission 25 m vom Start, Besitzer erst weg, wenn die Drohne mindestens
    20 m vom Start ist: Die Meldung muss zum Verhalten danach passen (landet an Ort und Stelle → „Landung“, fliegt heim →
    „Heimflug“). Seit Propwash 0.6.0-dev.8 fliegt die Drohne nach `OUT_OF_RANGE` mit GPS-Empfang heim, ohne landet sie an
    Ort und Stelle; die Meldung lautet seit dem Fix von AUTO1-1 wie am Weltrand „außer Funkreichweite – Heimflug bzw.
    Landung“ (vorher „Landung an Ort und Stelle“: 518 FAIL auf beiden Loadern, die Drohne flog 20,6 m heim). JUnit dazu:
    `integration.autonomy.AutonomyLangTest#endMessagesPromiseOnlyWhatPropwashDoesAfterwards` (jede Meldung zu
    `AUTONOMY_ENDED` gegen Propwashs Verhalten danach aus `docs/addon-autonomy.md` §4) und
    `#outOfRangeTellsTheOwnerTheDroneFliesHomeOrLands`.
  - 521 Crew je Klasse: Knopf „Crew“ in der Wartung (bei der Kino unter „…“) genau dann, wenn `/camdrones crew invite` geht;
    Tour ohne Gimbal ohne Knopf, der Befehl lehnt ab.
  - 531 Kamera-Ansicht mit Hindernis und laufender Aufnahme (Fotos); 533 Töne: Planer-Klick, Wegpunkt, Mission-Start (drei Töne
    in der Datei) und -Ende, Crew-Anfrage, Liste der gespielten Ereignisse in `runs/selftest-client/covcamb/sound_events.txt`;
    536 Töne für Gimbal und Höhengrenze: Gimbal zentrieren spielt `gimbal.calibrate` (seit §47, vorher Neigen), Vollgas bis zur
    Höhengrenze 6 m spielt `warning.limit`; 538 Gimbal bewegen bleibt stumm (seit §47): Neige-Rad erst auf Mitte (das Rad gilt
    für den ganzen Client, eine neue Drohne übernimmt seine Stellung), 3 s ruhig ohne Ton, 1,5 s Bild↓ (Neigung 0° auf −90°)
    ohne Ton, wieder 3 s ohne Ton, 3 Ticks Bild↑ antippen ohne Ton, Zentrieren mit genau einem `gimbal.calibrate` an der
    Drohne in 15 %, danach 3 s ohne Ton und Zentrieren bei Gimbal-Surren 0 % ohne Ton; 539 dasselbe nach einem Vorflug, der das Neige-Rad am unteren Anschlag lässt (AUTO2-1: ohne „auf Mitte“ neigte sich
    der Gimbal nach 536 nicht, 0° auf 0°);
    534 Flugschul-Kapitel „Kamera-Drohne fliegen“ und „Erste Mission“ Schritt für Schritt bis „Fertig“ in EN und DE;
    535 Fotos des Abschnitts im Tab Welt-Regeln bei GUI 320×240 auf Deutsch, 537 dort jeder Name ganz lesbar (nicht gekürzt); 541 PW3-Code einer Tour mit Mission und Umlack:
    gleiche Teile und Lack, Tabelle `camdrones` erkannt, die importierte Drohne ohne `camdrones:mission`.
  - 551 und 552 nur mit den Jars im Modordner: Tour mit Akku, Motor und Prop aus Just More Parts in der Werkbank bauen und fliegen;
    Mob Defense: Dispenser-Halterung erst nach dem Gimbal, Speer-Halterung baut keine Tour (Halterung unten fehlt).
- Fertig, aber ohne Anmeldung bis zum Fix: 519 (Ziel 20 s mit 30 m/s, die Drohne wird entladen), 522 (Befehl meldet
  `NO_GIMBAL`). Freigeben: Aufruf in `CovCamBSteps` ergänzen. 532 (Einheit km/h und Warnton-Lautstärke) ist seit dem Fix von
  cov-cam-b-5 angemeldet (§11), 536 (Töne für Gimbal und Höhengrenze) seit dem Fix von cov-cam-b-6.
- Beitrittstest-Fall `covcamb` (Server mit `CAMDRONES_SERVERTEST=covcamb CAMDRONES_JOINTEST_HOLD=1800`, Client mit
  `CAMDRONES_SERVERTEST=covcamb CAMDRONES_JOINTEST_LABEL=covcamb-<loader>`), Signale vom Server über die Erfahrungsstufe, Antworten
  des Clients über den gewählten Hotbar-Platz: `/camdrones mission start|pause|stop` mit Vorschlägen und ohne Kamera-Befehle unter
  `/propwash`; Welt-Regeln ohne OP gesperrt, `/gamerule` abgelehnt, nach `/op` kommt die Änderung an (Fotos); Zuschauen per F7 bei
  einer zweiten Drohne, die der Server ohne Pilot im engen Kreis fliegt (Horizont im Bild unter 2°); Welt-Regel
  `allow_operator` aus beendet die Bindung binnen 1 s, neue Einladung `RULE_OFF`; Operator trennt, die Crew wartet binnen 1 s
  2:00 auf ihn (seit PTC-3, §37; vorher endete die Bindung binnen 1 s).
  Erwartung `CAMDRONES_JOINTEST_EXPECT='joined|cmd start running|cmd pause paused|cmd stop|rules not_op locked=true|rules op change arrived|spectate roll max|crew rule_off notice released|crew leave while bound'`.
  Fertig, aber ohne Anmeldung: `covcamb-weit` (Mission ohne Pilot 300 Blöcke weit, Besitzer am Start).

## 11. Tempo-Einheit, Warnton-Lautstärke und Ton an der Fluggrenze

Client-Einstellungen aus `design.md` §4.3 und §2.11 in `config/camdrones-client.json`: `speed_unit` (`m/s` oder `km/h`,
Standard `m/s`), `warning_volume` (0 bis 1, Standard 0,6) und seit PTC-3 `gimbal_servo_volume` (Gimbal-Surren, 0 bis 1,
Standard 0,15, §37), im Tab „Kamera-Drohnen“ der Einstellungen (K), Abschnitt „Kamera-Ansicht und Warntöne“. Der Ton `warning.limit` ertönt einmal beim Erreichen der Höhen- oder Abstandsgrenze und erst
wieder, wenn die Warnung eine Sekunde weg war.

- JUnit: `client.hud.SpeedUnitTest` (km/h ganzzahlig, m/s unverändert, beide passen in den Infoblock),
  `client.hud.HudSettingsConfigTest` (Standardwerte, Neuladen, alte Datei nur mit `preview_markers` wird ergänzt, kaputte
  Werte fallen auf den Standard), `client.hud.HudSettingsKeysTest`, `client.hud.LimitChimeTest` (ein Ton je Erreichen,
  kein Ton bei kurzem Flackern, wieder nach 1 s), `client.hud.WarningLimitToneTest`; `client.hud.CovCamBUnitsTest` ist
  wieder aktiv.
- Selbsttest 47 „hud: Tempo-Einheit, Warnton-Lautstärke und Gimbal-Surren in den Einstellungen bei GUI 320×240 in EN und
  DE“ (Texte ungekürzt, Speichern mit Strg+S, Verwerfen mit Esc, Standardwerte, seit §47 auch der Tooltip von
  „Gimbal-Surren“ mit Zentrieren und Übernahme statt Bewegen; Fotos `hud_settings_320x240` EN und DE) und 48
  „töne: Warnton an der Höhengrenze in der eingestellten Lautstärke“ (Höhengrenze 6 m: ein Ton in 35 %, zweiter Ton
  nach dem Sinken, stumm bei 0 %), angemeldet in `dev/client/HudSettingsSteps`. Schritt 532 aus §10 ist angemeldet und
  speichert mit Strg+S. Gezielt: `-PcamdronesSelftestOnly='47-48,532'` oder die Gruppen `'osd:,hud:,töne:'`.
- Schritt 536 und `CovCamBSoundsTest#everyDeclaredSoundIsPlayedSomewhere` sind seit dem Fix von cov-cam-b-6 (`gimbal.calibrate`)
  angemeldet bzw. aktiv; `warning.limit` spielt seit dem Fix oben (cov-cam-b-11).

## 12. Aufnahmen im Livebild und Props im Gimbal-Bild (Owner-Playtest 23.10., OWNC-2)

Befund OWNC-2: Die Kino flog Missionen ohne Pilot mit Wegpunkt-Aktionen, Aufnahmen gab es keine. Die Aktionen Foto,
Aufnahme an und Aufnahme aus liefen nur am Client von Pilot oder Operator; wer im *Livebild* zuschaute, bekam nichts.
Seit dem Fix nimmt auch der Zuschauer im Livebild auf (Propwash erlaubt das dem Besitzer einer Drohne ohne Pilot), und
zwar mit Propwashs Action-Cam nach `propwash/recordings`; der Chat meldet jede Datei mit Pfad.

- JUnit `client.gimbal.ShotSourceTest`: Pilot oder Operator, Zuschauer im Livebild (ohne eigenen Flug) und sonst
  niemand; Zuschauer ohne Aufnahmerecht sehen keine Ablehnung.
- Selbsttest 601 `mission: Aufnahme und Foto der Kino im Livebild ohne Pilot, mit Pfad im Chat, ohne Props`
  (`dev/client/LiveShotSteps`): Kino mit Ansicht *Kamera*, Mission ohne Pilot mit Aufnahme an Punkt 2, Foto an 3,
  Aufnahme aus an 4, der Besitzer schaut im Livebild zu; verlangt genau ein neues Video und ein neues Foto unter
  `propwash/recordings` und beide als Link mit Pfad im Chat. Seit AUTO19-1 prüft der Schritt danach Props im Livebild:
  im Foto und in zwei Bildern des Videos (1,0 s und 4,0 s, mit ffmpeg) zählen im oberen Drittel (Himmel über dem
  Horizont) die Pixel, die mindestens 15 % dunkler sind als der Himmel in der Mitte derselben Zeile, erlaubt sind
  höchstens 0,3 %. Ohne `showsProps` in `CamGimbal` FAIL mit 7,45 % im Foto und 5,04 % bzw. 5,73 % im Video, mit 0,00 %
  (Fabric und NeoForge). Fehlt ffmpeg, prüft der Schritt nur das Foto. Gezielt `'-PcamdronesSelftestOnly=601'`
  (etwa 1 min); Foto und Videobilder liegen danach in `runs/selftest-client/ownc2/`.
- Selbsttest 602 `hud: Foto im FPV-Bild kommt aus der Gimbal-Kamera der Kino, ohne Props`
  (`LiveShotSteps.registerCleanView()`, angemeldet seit OWNC4B-1, §17.1): Kino mit Ansicht *Kamera*, der Pilot sieht die
  FPV-Kamera, Gimbal 25° in den Himmel, Foto wie an einem Foto-Wegpunkt; Props sind Pixel, die mindestens 15 % dunkler
  sind als der Himmel in der Mitte derselben Zeile, erlaubt sind höchstens 0,3 %.

## 13. Ende am Weltrand (GCB2CAM-1)

Befund GCB2CAM-1: Propwash beendet einen Flug ohne Pilot seit dem Stand nach 0.6.0-dev.7 auch mit
`AutonomyEnd.TERRAIN_LIMIT`, wenn die Welt-Regel „Drohnen erzeugen neues Gelände“ aus ist und die Drohne den Rand des
erzeugten Geländes erreicht; danach fliegt die Drohne heim, ohne GPS-Empfang landet sie an Ort und Stelle. Das Add-on
kannte den Grund nicht: Es kompilierte gegen diesen Stand nicht, und ein älteres Jar warf beim Ende `MatchException`, die
Mission blieb auf RUNNING ohne Meldung, später angemeldete Listener von `AUTONOMY_ENDED` bekamen das Ende nicht.

- Seit dem Fix: Meldung `ended_terrain_limit` („Weltrand erreicht – Drohnen dürfen hier kein neues Gelände erzeugen,
  Heimflug bzw. Landung“) in Chat und Aktionsleiste, die Mission wird abgebrochen. Gründe, die das Add-on beim Bauen noch
  nicht kennt, ordnet `AutonomyStatus.endedByName` nach dem Namen zu (sonst neutral `ended_stopped`); der Listener läuft
  über `EndGuard` und lässt keine Ausnahme zu Propwash durch.
- JUnit `integration.autonomy.AutonomyEndTest` (jeder Grund hat seinen Status `ENDED_<Grund>`, Zuordnung nach Namen,
  spätere Listener bekommen jedes Ende), `AutonomyLangTest#worldEdgeTellsThePilotWhyAndThatTheDroneFliesHomeOrLands`
  und `ExitAndEndDecisionTest#pilotTakeoverPausesAndOtherEndsAbortTheMission` (jeder Grund außer `PILOT` bricht ab).
- Gegen einen neueren Propwash-Stand prüfen, ohne die Nummer zu ändern:
  `./gradlew :common:test -Ppropwash_version=0.6.0-dev.8` (Propwash muss im lokalen Maven-Repository liegen).
- Im Spiel prüft das der Fall `covcamb-weit` mit der Regel aus, sobald das Add-on auf einen Propwash-Stand mit
  `TERRAIN_LIMIT` wechselt (§10, heute ohne Anmeldung).

## 14. Wegpunkt-Aktionen und FPV-Drohnen im Flugplaner (cam-wp-actions)

Owner-Wunsch 23.10. für den ersten Release: Der Flugplaner fliegt auch normale FPV-Drohnen, und jeder Wegpunkt kann
außer Foto und Aufnahme das Licht schalten und eine Zubehör-Aktion 1–4 auslösen, wahlweise „ab hier wiederholen“.

- **Bedingung für FPV-Drohnen:** Jede Propwash-Drohne hat das GPS ihres Flight Controllers (Propwash simuliert den
  Empfänger in jedem Flug, Fix ab 5 Satelliten aus der Himmelssicht, dieselbe Messung wie beim GPS-Rescue). Ein eigenes
  Teil gibt es nicht. Das Add-on misst die Himmelssicht wie Propwash (`StartProbes`, `StartCheck`): ohne Fix Hinweis im
  Planer und Meldung `not_ready.gps` beim Start. FPV-Drohnen (Stack ohne Nav-FC) fliegen Missionen nur ohne Pilot über
  `autonomy().start` mit derselben `AutonomyAssist` wie Kamera-Drohnen, ohne Hindernis-Sensoren; Start mit Pilot meldet
  `piloted.fpv`; beim Verlassen der FPV-Ansicht ohne laufende Mission bleibt Propwashs Verhalten (`FpvMissions`).
- **Missionsformat:** Missionen mit Aktionen schreibt `MissionCodec` als Format 2 (Licht, Zubehör und Wiederholen in den
  oberen Bits der sechsten Zahl je Wegpunkt), Missionen ohne Aktionen bleiben Format 1, Byte für Byte wie bisher
  (Format 3 für den Takt §14.1, Format 4 für „Greifer auslösen“ §48).
- **Ausführung:** `WaypointTaskServer` (Server-Tick beider Loader) beobachtet jede Drohne mit aktiver Mission, egal ob
  der Server oder der Pilot sie fliegt, und meldet je Punkt einmal je Durchgang (`TaskTracker`). Zubehör-Aktionen gehen
  über `PropwashApi.accessories().triggerAction(drone, n, true/false, besitzer)`; Propwash prüft beim Werfen die Rechte
  des Besitzers, das Zubehör-Add-on seine eigenen Regeln. Licht-Aktionen überspringt `LightControl`, bis Propwash eine
  Schnittstelle dafür hat (API-Befund des Strangs).
- JUnit: `logic.WaypointTasksTest` (Wiederholen, „Wiederholung aus“, Tracker mit Ankunft, weicher Kurve, Schleife,
  Pause, Neustart und Sprung), `logic.MissionCodecTasksTest` (Format 1 unverändert ladbar und Byte für Byte gleich,
  Format 2 mit allen Kombinationen, kaputte Bits, volle Mission unter 2 KiB), `planner.DroneSupportTest`
  (Kamera-Drohne gegen FPV, GPS-Hinweise, Gründe fürs Überspringen, Felder Licht/Zubehör/Wiederholen,
  `FpvMissions`), `planner.DetailPagesTest` (Seiten *Flug* und *Aktionen* bei 320×240, alles auf einer Seite ab
  640×480).
- Selbsttest (`dev/client/WaypointTaskSteps`, gezielt `'-PcamdronesSelftestOnly=701,702,703,704,705'`, etwa 2 min):
  - 701 Freestyle-5″ (Propwash-Preset) mit Beispiel-Werfer vorn (`ExampleDropper`, Namensraum `camdrones_example`,
    nur im Selbsttest angemeldet) und LED-Streifen unten fliegt ohne Pilot eine Mission: Licht an Punkt 2 und 5
    übersprungen mit Grund `NO_LIGHT_API`, Zubehör-Aktion 1 ab Punkt 3 bis Punkt 4 genau zweimal als Besitzer ohne
    Pilot, zwei Schneebälle geworfen.
  - 702 Editor mit gekoppelter Freestyle: Kopf „FPV“, Markierungen, „Aktion 1 ab 3“, Seiten bei 320×240 per Klick auf
    den Umschalter, Marker mit Aktionen in der Welt; Fotos `planner_waypoint_actions*`, `planner_waypoint_light*` und
    `planner_waypoint_actions_world*`.
  - 703 Freestyle unter einem Steindach: Kopf „kein GPS“, Hinweis in Statuszeile und Vorschau, Start meldet kein
    GPS-Fix; danach mit Pilot: Start meldet „nur ohne Pilot“, kein Flug ohne Pilot; Foto `planner_fpv_no_gps*`.
  - 704 Tour im Pilotenflug: Mission per Taste, der Server meldet die Licht-Aktionen der Punkte 2 und 3 aus dem
    Pilotenflug.
  - 705 nur mit Mob Defense im mods-Ordner (Fabric): Freestyle mit Dispenser-Halterung, Pfeile im Fach, Zubehör-Aktion 1
    an Punkt 3 schießt einen Pfeil („Fire dispenser“).

### 14.1 Zubehör-Aktion im Takt (Owner 27.10., OWNC5-1)

Befund OWNC5-1: Wegpunkt-Aktionen kannten nur „einmal“ und „an jedem Wegpunkt“. Neu ist *Wiederholen* im Takt mit dem
Feld *Takt* (1–1200 Ticks, Standard 20; *Takt* zeigt die Ticks, *Wiederholen* dieselbe Zeit in Sekunden, etwa „alle 1 s“,
damit das Detailfeld bei 320×240 nicht breiter wird als bisher): Auslöser bei Ankunft und danach alle X Ticks, bis zu
einem Wegpunkt mit anderer Zubehör-Einstellung, *Wiederholung aus* oder zum Ende der Mission; gleiche Einstellung an einem
späteren Punkt läuft ohne Extra-Auslöser weiter, Pause hält den Takt, Abbruch, Neustart und Sprung beenden ihn,
Schleifen laufen durch (`logic.mission.TaskTracker` mit der Spielzeit des Servers, `WaypointTasks.timedAt`).

- **Mindestzeit des Zubehörs:** Die Propwash-API 1.7 nennt keine Mindestzeit einer Zubehör-Aktion (`AccessoryBehavior`
  hat nur `onAction`, `tick`, `actionNameKey`; `AccessoryRegistry#triggerAction` nur gedrückt/losgelassen). Deshalb
  begrenzt der Planer X nicht auf das Zubehör, sondern das Zubehör erzwingt seine Zeit selbst (der Werfer von Mob Defense
  über `FireGate` und die Welt-Regel `mobdefense:dispenser_cooldown_ticks`, Standard 4), und der Editor zeigt im Tooltip
  von *Wiederholen*, *Takt* und *Zubehör* „höchstens so oft, wie das Zubehör erlaubt“. Keine Änderung an Propwash.
- **Missionsformat:** Missionen mit Takt schreibt `MissionCodec` als Format 3 (Takt in den oberen 16 Bit der fünften Zahl
  je Wegpunkt, gleiche Größe); Format 1 und 2 bleiben Byte für Byte gleich und laden mit „nur hier“ bzw. „ab hier“.
  Format 2 mit gesetzten Takt-Bits, Takt ohne Zubehör-Aktion oder über 1200 Ticks gilt als beschädigt.
- **Am Server:** Takt-Auslöser gehen wie Ankunfts-Auslöser über `triggerAction` im Namen des Besitzers, aber ohne
  Overlay-Meldung und nur der erste je Punkt ins Log; Berichte (`WaypointTaskServer.Report`) tragen Spielzeit und
  `timed`.
- JUnit `logic.WaypointEveryTest` (Takt ab dem Wegpunkt, bis zur anderen Einstellung und zum Missionsende, beim Warten,
  gleiche Einstellung, Pause, Abbruch, Rücksprung, Schleife, Abstand nie kürzer als X für 1, 2, 3, 7 und 20 Ticks,
  Grenzen, Format 3 und unveränderte Formate 1 und 2, kaputte Werte, Vorschau und Akkuzeit unverändert) und
  `planner.EveryFieldTest` (Wiederholen in drei Stufen, Takt in Schritten zu 1 und 20 Ticks mit Grenzen 1 und 1200,
  Zubehör-Wechsel behält den Takt, Anzeige „20 Ticks“ und „alle 1 s“ bzw. „1 Tick“ und „alle 0,05 s“, kein Wert im Takt
  breiter als der bisher breiteste Wert, Hinweis in beiden Sprachen, Zeile Takt auf der Seite *Aktionen* bei 320×240 und
  alles auf einer Seite ab 640×480). Vor dem Fix 17 von 20 rot.
- Selbsttest (`dev/client/WaypointEverySteps`, gezielt `'-PcamdronesSelftestOnly=706,707,708'`, etwa 2 min):
  - 706 Freestyle-5″ mit Beispiel-Werfer (Mindestabstand 6 Ticks, Pfeile in beiden Fächern) fliegt ohne Pilot: Punkt 2
    alle 10 Ticks mit 1 s Warten, Punkt 4 alle 2 Ticks, Punkt 5 *Wiederholung aus*. Auslöser am Server genau im Abstand
    10 bzw. 2 Ticks, der erste je bei Ankunft, ab Punkt 5 und nach dem Ende keiner; im 10er-Takt jeder Druck ein Wurf, im
    2er-Takt Würfe im Abstand 6 Ticks und abgelehnte Drücke dazwischen.
  - 707 Editor: Wiederholen „alle 0,5 s“, Takt „10 Ticks“, Klick rechts +1, mit Umschalt +20, links zurück; Hinweis
    im Tooltip von *Takt* und *Wiederholen*, Punkt 3 „Aktion 1 im Takt“ mit Tooltip; bei 320×240 Seite *Aktionen* mit der
    ganz sichtbaren Zeile *Takt*; Marker „Zubehör 1 alle 10 Ticks (0,5 s)“; Fotos `planner_waypoint_every*` (auch mit
    Tooltip von *Takt*).
  - 708 nur mit Mob Defense im mods-Ordner (beide Loader): Dispenser-Halterung mit 64 Pfeilen, dieselbe Mission; im
    10er-Takt ein Pfeil je Auslöser im Abstand 10 Ticks, im 2er-Takt Pfeile nur im Abstand der Abklingzeit (4 Ticks) und
    halb so viele wie Auslöser; verbrauchte Pfeile gleich Pfeilen in der Welt.
- Stand 27.10.: `:common:test` 704 Tests grün (die 20 neuen vor dem Fix 17 rot), Build beider Loader grün; Selbsttest
  701–708 auf Fabric und NeoForge je 9 Schritte PASS (708 mit Mob Defense), `planer:,planner:` auf Fabric 14 Schritte
  PASS; Server-Rauchtest und Beitrittstest `mission` auf beiden Loadern PASS.

### 14.2 Takt endet am letzten Wegpunkt (OWNC5B-2)

Befund OWNC5B-2 (aus G-OWNC5-1, OWNC5-3/-4): Bei Ende *zum Start* lief der Takt über den ganzen Rückflug bis über den
Startpunkt weiter, weil der Rückflug am Server noch als laufende Mission am letzten Wegpunkt zählt (Phase `RETURN` des
`WaypointFollower`, Zustand `RUNNING`). Entscheidung der Steuerung: Der Takt endet bei allen Endarten am letzten
Wegpunkt, der Rückflug gehört nicht mehr zur Mission.

- **Regel** (`logic.mission.TaskTracker`, ohne Schleife): Nach der Ankunft am letzten Wegpunkt (1,5 m, wie jede
  Wegpunkt-Aktion) läuft der Takt noch dessen Wartezeit lang und endet dann, spätestens sobald die Drohne mehr als 3 m
  (`LEAVE_RADIUS`) davon entfernt ist. Eine Pause dort hält die restliche Wartezeit. Der Server kennt nur Zustand,
  Index und Position der Drohne, nicht die Phase des Folgers; die Wartezeit des Folgers beginnt erst bei seiner Ankunft
  (0,5 m), der Takt endet also vor dem Abflug; läuft die Uhr eines Piloten-Clients anders als die des Servers, greift
  die 3-m-Grenze. Schweben, zum Start und landen lösen damit gleich aus; bei Ende ohne
  Warten endet der Takt an der Ankunft wie an einem Punkt mit anderer Zubehör-Einstellung. Schleifen unverändert.
- **Texte:** Tooltips von *Wiederholen* und *Takt* „längstens bis zum letzten Wegpunkt“ statt „bis zum Ende der
  Mission“; englischer Hinweis an Punkten im Takt „Continues every %2$s ticks (%3$s s) from waypoint %1$s“ (vorher „Runs
  on timed from waypoint …“); Anleitung EN/DE mit dem Punkt „Der Takt endet am letzten Wegpunkt“; Fotos
  `planner_waypoint_every_tooltip*` neu.
- JUnit `logic.WaypointEveryTest`: dieselbe Mission (Tour, drei Punkte, Aktion 1 alle 20 Ticks ab Punkt 1, Wind 3 m/s)
  mit Ende schweben, zum Start und landen, in der Luft und vom Boden, ohne und mit 3 s Warten am letzten Punkt
  (`PointMass` und `NavLogic`): kein Auslöser nach dem Verlassen des letzten Punkts, alle drei Enden mit denselben
  Auslösern (16 bzw. 19), beim Warten am letzten Punkt weiter im Takt; dazu Schluss nach der Wartezeit, beim Verlassen
  vor Ende der Wartezeit und Pause am letzten Punkt. `planner.EveryFieldTest`: Wortlaut des Hinweises und „letzter
  Wegpunkt“ in beiden Tooltips. Vor dem Fix 5 von 26 rot (in der Luft ohne Warten: zum Start 26 Auslöser, davon 8 auf
  dem Rückflug, schweben 18; jetzt alle drei 16, die zwei fehlenden lagen zwischen 1,5 m und der Ankunft des Folgers).
- Selbsttest wie §14 und §14.1 (`'-PcamdronesSelftestOnly=701,702,703,704,705,706,707,708'`, 706–708 haben Ende
  *schweben* und *Wiederholung aus* an Punkt 5, ändern sich also nicht).
- Stand 27.10.: `:common:test` 721 Tests grün (die 5 neuen vor dem Fix rot), Build beider Loader grün; Selbsttest
  701–708 mit Mob Defense auf Fabric (mit Fotos) und NeoForge je 9 Schritte PASS; Server-Rauchtest beider Loader PASS.
- Wartezeit ab 1,5 m und „Pause hält den Rest“ gelten nicht mehr, siehe §14.3.

### 14.3 Takt läuft die ganze Wartezeit am letzten Wegpunkt (AUTO8-1)

Befund AUTO8-1 (aus G-OWNC5B-2): Der Takt endete am letzten Wegpunkt 22 bis 29 Ticks vor dem Ende der sichtbaren
Wartezeit, auch bei Ende *schweben* und *landen*: `TaskTracker` zählte die Wartezeit ab 1,5 m, der `WaypointFollower`
erst ab seiner Ankunft. Nach einer Pause am letzten Punkt startet `NavLogic.resumeMission` einen neuen Folger, der die
volle Wartezeit noch einmal wartet; `TaskTracker` gab nur den Rest. Der JUnit-Fall dazu ging von einem Folger aus, der
nur den Rest wartet.

- **Regel** (`logic.mission.TaskTracker`, ohne Schleife): Die Wartezeit des letzten Punkts beginnt am Server mit der
  Ankunft wie beim Folger: höchstens 0,5 m vom Punkt (`WaypointFollower.ARRIVE_RADIUS`) oder höchstens 1,0 m
  (`PASS_RADIUS`) und waagrecht schon am Punkt vorbei, gemessen in Richtung vom vorigen Wegpunkt (nach Start am letzten
  Punkt oder Wiederaufnahme von der Position dort, wie beim Folger). Das umfasst die Bedingung des Folgers (0,5 m und
  langsamer als 0,5 m/s, 1,0 m vorbei oder 0,2 m); der Server sieht dieselben Positionen und erkennt die Ankunft nie
  später. Danach läuft der Takt die Wartezeit lang und endet mit dem Abflug. Nach einer Pause (`PAUSED` → `RUNNING`) am
  letzten Punkt beginnt die Ankunft neu. Die 3-m-Grenze bleibt als Sicherung; nach einer Wiederaufnahme gilt sie erst
  wieder, wenn die Drohne zurück auf 1,5 m ist (in der Pause weggeflogen: auf dem Weg zurück weiter im Takt).
- Simulation (`PointMass` und `NavLogic`, Tour aus §14.2, Wind 3 m/s): Der Takt endet höchstens 1 Tick vor dem Abflug,
  nie danach. Mit 3 s Warten, alle 20 Ticks: 3 Auslöser beim Warten (vorher 1), alle drei Enden je 21 (vorher 19);
  ohne Warten je 18 (vorher 16), 10 s Warten 10 beim Warten (vorher 8), in der Luft und vom Boden, keiner auf dem
  Rückflug. Pause 5 s nach 1 s Warten (4 s, alle 5 Ticks): im neuen Warten 15 Auslöser (vorher 6); der neue Folger
  kommt erst einen Schritt nach der Wiederaufnahme an, der Takt endet dort 1 Tick früher.
- JUnit `logic.WaypointEveryTest`: `timedActionRunsThroughTheWholeVisibleWaitAtTheLastWaypoint` (drei Enden, Luft und
  Boden, 3 und 10 s Warten, alle 5 und 20 Ticks: letzter Auslöser höchstens einen Takt plus 2 Ticks vor dem Abflug),
  `resumeAtTheLastWaypointRunsTheTimerThroughTheWholeNewWait` (`NavLogic` mit Pause, drei Enden gleich),
  `resumeAtTheLastWaypointStartsTheWholeWaitAgain` (ersetzt `pauseAtTheLastWaypointKeepsTheRestOfTheWait`),
  `resumeAwayFromTheLastWaypointKeepsTheTimerUntilTheNewWaitIsOver`. Vor dem Fix 4 von 22 rot.
- Anleitung EN/DE: Punkt „Der Takt endet am letzten Wegpunkt“ neu; englisch „except *loop*“ statt „*start over*“.
- Stand 27.10.: `:common:test` 724 Tests grün (die 4 neuen vor dem Fix rot), Build beider Loader grün; Selbsttest
  701–708 mit Mob Defense auf Fabric und NeoForge je 9 Schritte PASS (706–708 haben Ende *schweben* und
  *Wiederholung aus* an Punkt 5, ändern sich nicht); Server-Rauchtest beider Loader PASS.

## 15. Karte im Flugplaner beim Ziehen und Zoomen (Owner-Playtest 25.10., OWNC3-1)

Befund OWNC3-1: Beim Verschieben laggte die Karte und zeigte eine Naht. Jede Änderung von Mittelpunkt oder Maßstab
setzte den Aufbau auf Zeile 0 zurück und schrieb 64 Zeilen je Tick in die angezeigte Textur: oben der neue, unten der
alte Ausschnitt, beim Ziehen nie fertig, je Tick die ganze Textur hochgeladen.

Seit dem Fix (`planner.model.MapCanvas`, `MapColumns`, `client.planner.MapImage`):

- Das Kartenbild liegt als Ringpuffer auf ganzen Kartenpixeln der Welt (Karte + 1 Pixel je Richtung). Ziehen verschiebt
  nur den Ursprung; abgetastet werden die neu sichtbaren Spalten und Zeilen im selben Bild, der Rest des Mittelpunkts ist
  der Unterpixel-Versatz beim Zeichnen. Hochgeladen werden nur geänderte Rechtecke.
- Zoom, neue Kartengröße und Weltwechsel bauen ein zweites Bild im Hintergrund; bis es fertig ist, bleibt das alte
  skaliert und versetzt an seiner Weltstelle sichtbar. Nie halb alt, halb neu.
- Blocksäulen (Kartenfarbe, Höhe, Oberfläche) werden je Chunk als 16×16-Kachel gespeichert (höchstens 4096 Kacheln,
  solange der Editor offen ist). Nicht geladene Chunks bleiben dunkel und werden im Leerlauf jede Sekunde neu gefragt;
  ob ein Chunk geladen ist, fragt die Karte die Chunk-Quelle (`ClientLevel.hasChunk` ist am Client immer wahr).
- Budget: 4 ms je Bild und je Tick für Abtasten und Hochladen.
- Karten über 512 GUI-Pixel (etwa GUI 1) bekommen ganzzahlig gröbere Kartenpixel (bei 1386×960 drei Blöcke je Pixel)
  und bleiben an ihrer Weltstelle; bisher wurde ein 512er-Bild gestreckt, Bild und Markierungen lagen gegeneinander
  verschoben.

Tests:

- JUnit `planner.MapCanvasTest` (nur neue Streifen, eine Spalte Upload je Pixel, Ziehen mit kleinem Budget zeigt nie
  eine andere Stelle, Zoom und Größenwechsel tauschen erst fertig, Sprung weit weg ohne alte Stelle, gleiche Blöcke und
  gleicher Nachbar im Norden wie bisher) und `planner.MapColumnsTest` (Säule je Block einmal, nicht geladene neu,
  Schattierung, Speichergrenze).
- Selbsttest 611 `planer: Karte beim Ziehen und Zoomen ohne Naht, nur neue Streifen, Bildzeit gemessen`
  (`dev/client/MapDragSteps`): Farbmuster aus Wolle (8×8-Felder, 192×192 Blöcke) um den Spawn, leeres Tablet, Fenster
  1920×1080 mit GUI 2, Sichtweite 12 Chunks, Bildrate ohne Grenze und ohne VSync nur für die Messung. Messfahrt:
  112 Ticks Ziehen (64 bei 1 Block je Pixel, dann Zoom heraus, zweimal hinein, wieder heraus, je weitergezogen) mit
  Bildzeit, Kartenarbeit je Bild und Tick sowie abgetasteten und hochgeladenen Kartenpixeln gegen die neu sichtbaren
  Streifen (höchstens das 1,5-Fache). Bildfahrt: 35 Aufnahmen des Bildpuffers während des Ziehens; jede geprüfte Stelle
  muss die Kartenfarbe der Welt unter genau diesem Pixel zeigen (höchstens 0,5 % falsch, beim Ziehen ohne Zoom höchstens
  2 % leer). Danach GUI 1 mit einer Karte über 512 Pixel, gezogen und dreimal aufgenommen. Bericht in
  `<loader>/runs/selftest-client/camdrones-map-drag.txt`, Fotos in `runs/selftest-client/ownc3/`. Gezielt
  `'-PcamdronesSelftestOnly=611'` (etwa 45 s) oder die Gruppe `'planer:'`.
- Gemessen 25.10. (Fabric/NeoForge, Bildrate frei): vorher bis 75,9 %/72,9 % falsche Kartenstellen, 1 744 896
  abgetastete und 10 960 128 hochgeladene Kartenpixel beim Ziehen, Kartenarbeit 0,60/0,63 ms je Tick; nachher 0 % in
  allen 38 Aufnahmen, 159 360 abgetastete (= neu sichtbare) und rund 116 000 hochgeladene, Kartenarbeit 0,01 ms je Bild.

## 16. Gimbals nur an Kamera-Rahmen (Owner-Playtest 23.10., OWNC-1)

Befund OWNC-1: Der Gimbal ließ sich an jede Drohne mit Halterung vorne bauen (`freestyle_5`, `race_5`, `long_range_7`,
`cinewhoop_3` und Rahmen anderer Add-ons). Seit Propwash 0.6.0-dev.9 nennen `gimbal_compact`, `gimbal_tour` und
`gimbal_cine` unter `"frames"` die drei Kamera-Rahmen `compact_fold_frame`, `tour_fold_frame` und `cine_frame`; an jedem
anderen Rahmen meldet Propwashs Bauprüfung `accessory_frame`, die Werkbank nimmt den Gimbal dort nicht an, und der
Tooltip „Passt zu“ nennt nur die Kamera-Rahmen.

Tests:

- JUnit `part.GimbalFramesTest`: jeder Gimbal nennt genau die drei Kamera-Rahmen; `freestyle_5` ohne Gimbal ist baubar,
  mit jedem Gimbal genau das Problem `accessory_frame`; dasselbe an jedem Propwash-Preset mit Halterung vorne
  (mindestens vier Rahmen); jeder Gimbal baut an Kompakt, Tour und Kino. Vor dem Fix 3 von 4 rot.
- Web-Export (§5): `data.frames` der drei Gimbals steht in `camdrones/parts.json`, für den Konfigurator der Website
  dieselbe Regel `accessory_frame` wie im Spiel; `tools/web_export.py` PASS.

## 17. Zwei Kameras: feste FPV-Kamera für den Pilot, Gimbal-Kamera für Operator und Aufnahmen (Owner-Playtest 27.10., OWNC4)

Befund OWNC4-1: Bewegte der Operator den Gimbal, kippte das Bild des Piloten mit, denn `CamGimbal.orient` schrieb die
Gimbal-Lage in jedes Bild, auch in das FPV-Bild des Piloten. Seit dem Fix rechnet `orient` den Gimbal weiter für Kopf,
OSD und Zuschauer, setzt Propwashs Kamera aber nur für Zuschauer und Operator (Livebild) und für den Piloten ohne
Operator nach seiner Wahl. Sonst bleibt die Kamera die FPV-Kamera am Rahmen mit dem Kamerawinkel aus der Wartung und
Propwashs eigener Brennweite (`fovDegrees` liefert dann `NaN`). Der Taster `camdrones:pilot_view` „Pilotenbild
FPV/Gimbal“ (Standardtaste Entf) schaltet ohne Operator zwischen FPV und Gimbal um; jeder Flug beginnt mit FPV, solange
ein Operator gebunden ist, bleibt das Pilotenbild FPV. Gilt für Kompakt, Tour und Kino.

Tests:

- JUnit `client.gimbal.PilotViewTest`: Operator und Zuschauer sehen immer den Gimbal, der Pilot mit Operator nie (auch
  nicht nach dem Abgeben, dann wieder FPV), ohne Operator schaltet jeder Druck genau einmal um, jeder neue Flug und jede
  andere Drohne beginnt mit FPV; `CamControlsTest` und `CamKeyDefaultsTest` mit 14 Schaltern und Entf.
- Selbsttest 41–43 `gimbal: Kino|Tour|Kompakt – Pilot fliegt mit fester FPV-Kamera, Gimbal-Bild per Taste`
  (`dev/client/PilotViewSteps`): nach dem Start und mit dem Gimbal auf −60° liegt das Pilotenbild höchstens 5° neben
  Propwashs Kamera-Pose der Drohne (`drones().cameraPose`), die Taste Pilotenbild schaltet auf das Gimbal-Bild (Bild
  blickt so weit nach unten wie der Gimbal) und zurück. Vor dem Fix FAIL: „Bild blickt 63.0° nach unten, FPV-Kamera
  −0.2°, Abstand zur FPV-Kamera 63.2°“.
- Beitrittstest `crew` (b): das Pilotenbild bleibt bei −56° des simulierten Operators auf der FPV-Kamera
  (`crew b ok: operator tilt -56.0, pilot view fpv 0.0 off, osd OPERATOR`); vorher verlangte der Fall das Gegenteil
  („pilot view 56.0 down“).
- Selbsttests, die das Bild auf dem Schirm messen (7, 8, 36, 38, 331), schalten das Pilotenbild zuerst mit der Taste
  auf die Gimbal-Kamera (`PilotViewSteps.gimbalImage()`); 18, 26 und 602 prüfen die Aufnahmen ohne Umschalten (§17.1).

### 17.1 Aufnahmen immer vom Gimbal, keine Props im Gimbal-Bild (OWNC4B-1)

Befund OWNC4B-1 (zweiter Teil von OWNC4-1 und OWNC4-2): Propwash nimmt Videos und Fotos vom Bildschirm auf. Ein Pilot
ohne Operator sieht standardmäßig die FPV-Kamera, Aufnahme, Foto und Wegpunkt-Foto zeigten deshalb das FPV-Bild mit
Props und Rahmenrollen; der Flashback-Kamerapfad folgte der Bildschirm-Kamera, und Propwash zeichnete die Props auch ins
Gimbal-Bild. Entscheidung der Steuerung 27.10.: Ohne Operator wechselt das Pilotenbild für Aufnahmen automatisch auf
die Gimbal-Kamera, ohne die Welt doppelt zu zeichnen; mit Operator nimmt dessen Client das Gimbal-Bild auf. Seit dem
Fix (Propwash 0.6.0-dev.10, API 1.7):

- Ohne Operator zeigt das Pilotenbild während einer Aufnahme (`RecordingControl.recording()`, egal ob mit Propwashs
  Taste, Funk-Schalter oder Wegpunkt gestartet) bis zum Stopp die Gimbal-Kamera, danach wieder die gewählte Kamera
  (Hinweis über der Hotbar `camdrones.crew.view.recording`, nach dem Stopp „Pilotenbild: FPV-Kamera“). Ein Foto des
  Add-ons (Wegpunkt-Foto über `CameraActions`) hält die Gimbal-Kamera mindestens 1 s und 3 Bilder, höchstens 5 s.
  Die Taste Pilotenbild schaltet während einer Aufnahme nicht um. Mit Operator bleibt das Pilotenbild immer FPV.
- `CamGimbal.showsProps`: keine Props und Ducts, solange das Bild die Gimbal-Kamera zeigt (Pilot im Gimbal-Bild,
  Operator, Livebild, Fotos, Videos); im FPV-Bild sitzen sie am Rahmen.
- `CamGimbal.gimbalRotation` und `gimbalFovDegrees`: der Flashback-Kamerapfad nimmt Lage und Brennweite (mit Zoom) der
  Gimbal-Kamera, egal was der Schirm zeigt (Lage aus dem Kamera-Modell von `orient`, ohne FPV-Ansicht aus dem Kopf von
  `modelPose`, jeweils nur frisch, höchstens 1 s alt).

Tests (vorher und nachher auf Fabric, nachher auch NeoForge; Flashback gibt es nur für Fabric):

- JUnit `client.gimbal.PilotViewTest`: Aufnahme bis zum Stopp mit Hinweis, Taste während der Aufnahme schaltet nicht,
  gewähltes Gimbal bleibt nach der Aufnahme, laufende Aufnahme beim Flugstart, mit Operator FPV auch bei Aufnahme und
  Foto, Foto kurz (1 s), Foto wartet auf 3 Bilder, aber höchstens 5 s, Ende des Flugs löscht das Foto.
- Selbsttest 18 `aufnahme: stabilisierte Aufnahme, Rollwinkel im Bild < 2°`: Pilotenbild vor der Aufnahme FPV, Aufnahme
  mit Propwashs Taste, Foto bei 28° Rollen, das Pilotenbild beim Foto muss die Gimbal-Kamera sein, nach dem Stopp wieder
  FPV. Vorher FAIL „Horizont −27,01° geneigt“; nachher Fabric 0,03° im Foto und höchstens 0,03° im Video, NeoForge
  0,03° und 0,05°.
- Selbsttest 26 `aufnahme: Flashback-Kamerapfad folgt dem Gimbal` (nur Fabric): der Schirm bleibt FPV. Vorher FAIL
  „Kamerapfad höchstens 35,55° gerollt, Neigung −0,82° bis 0,10°, Sichtfeld höchstens 7,98° neben der Gimbal-Kamera
  (45,75° senkrecht)“; nachher 0,00° gerollt, Neigung bis 47,98° (Gimbal −48°), Sichtfeld 0,00° daneben.
- Selbsttest 602 (§12): Foto aus dem FPV-Bild wie an einem Foto-Wegpunkt, Pilotenbild vor und nach dem Foto FPV. Vorher
  FAIL „5,80 % Prop-Pixel, Pilotenbild beim Foto FPV, Bild blickt 0,5° nach oben“; nachher Fabric und NeoForge 0,00 %,
  Pilotenbild beim Foto GIMBAL, Bild blickt 26,0° nach oben.
- Selbsttest 350 `mission: Aktionen Aufnahme und Foto` (Pilot fliegt die Mission mit Aufnahme, Foto, Aufnahme aus) und
  Beitrittstest `crew` (Pilotenbild mit Operator bleibt FPV) unverändert PASS auf beiden Loadern.

## 18. Vollgas gegen die Wand (GCCA3-1)

Befund GCCA3-1: Die Kino unterschritt im Modus N mit Vollgas vor einer Wand den Stopp-Abstand von 1,5 m (Fabric
0,44–0,67 m, NeoForge Aufprall). Der Wächter plante mit 40° Bremsneigung, der Regler neigte nur 35°; in 3 m Höhe trafen
die am Rahmen festen Sensoren vorn bei Fahrtneigung erst den Boden und sahen die Wand bei 8–11 m; beim Bremsen mit
gehobener Nase ging die Wand wieder verloren. Seit dem Fix:

- eine Neigung für Regler und Bremsplan (`HorizontalControl.MAX_TILT` 40°, `ObstacleGuard.BRAKE_ANGLE` und
  `BRAKE_ACCEL` daraus, Reaktion 0,25 s);
- Sensorabstände waagrecht (Abstand mal Kosinus der Strahlneigung), Reichweite des Strahls waagrecht an den Wächter;
- der Bodenfilter erkennt nur den Boden; dicht über dem Boden neigt sich die Drohne zur Flugrichtung hin nur so weit,
  dass der Sensor eine Wand vor dem Bodentreffer im Bremsweg plus 1,5 m sieht (mindestens 5°);
- eine gesehene Wand bleibt gemerkt (abzüglich der Strecke dorthin), bis ein Strahl auf Flughöhe sie frei sieht; seit
  GCCA3A-1 ohne Zeitgrenze und nicht mehr, wenn die Drohne beim Bremsen gestiegen ist (§20).

Kompakt hat laut Design nur den Sensor nach unten und bremst nicht vor Wänden; Sport bremst nicht (beides unverändert).

Tests:

- JUnit `integration.NavAssistWallStopTest`: Modell mit Propwashs Winkelmodus (60°, Verstärkung 10,7, höchstens
  500 °/s), Sensoren am Rahmen, flachem Boden und 12 m hoher Wand 45 m voraus; Tour und Kino in N und C in 1,5, 3, 4,5,
  6 und 10 m Höhe bleiben mindestens 1,5 m vor der Wand und stehen danach; ohne Wand erreichen beide in 6 m Höhe in 8 s
  mindestens 85 % ihres Normal-Tempos. `logic.ObstacleGuardTest#plansTheBrakeWithTheTiltTheControllerFlies` (gemeinsame
  Bremsneigung), `logic.HorizontalControlTest#nearTheGroundTheTiltTowardsTheFlightKeepsWallsInSight`,
  `integration.NavAssistGroundRayTest` (Bodentreffer ist kein Hindernis, kürzt nur die Reichweite).
- Selbsttest 361–366 „hindernis: Vollgas gegen die Wand“ (Klasse `dev/client/WallStopSteps`, Filter
  `'-PcamdronesSelftestOnly=361-366'`, etwa 3 min): Wind aus, Knüppel 100 %, Wand 45 m voraus; Tour und Kino in N in 3
  und 6 m, in C in 3 m; der nächste Abstand muss mindestens 1,5 m sein, die Drohne muss danach stehen und vorher
  schnell gewesen sein (N mindestens 8 m/s, C mindestens 70 % des Kino-Tempos). Vor dem Fix auf Fabric Kino N 3 m
  1,44 m und 6 m 1,30 m FAIL; nach dem Fix auf Fabric und NeoForge alle sechs PASS mit 2,11 bis 2,35 m nächstem Abstand
  (Tour N 3 m mit 10,3–10,4 m/s, Kino N 3 m mit 11,6–11,7 m/s, Kino N 6 m mit 13,0–13,2 m/s). Die Schritte 311–313
  (Normal-Tempo) bleiben im Bereich: Kino 13,1–13,3 m/s, Tour 12,1–12,3 m/s.

## 19. Präzise Landung auf dem Landeplatz (GCCA12C-1)

Befund GCCA12C-1: Schritt 351 war nicht angemeldet, und die JUnit-Fälle zum Landeplatz waren grün, obwohl die Drohne im
Spiel rund 2 m neben dem Landeplatz aufsetzte. Der Fake `integration.autonomy.FakeServerFlight` landete bei `land()`
senkrecht und folgte dabei weiter `holdPosition` der Flughilfe; Propwash fliegt die Landung nach `land()` allein und
sucht eine andere Stelle, wenn die unter der Drohne nicht eben ist.

- Der Fake landet jetzt wie Propwash: Nach `land()` fragt er die Flughilfe nicht mehr. Ist die Stelle unter der Drohne
  eben (0,05 Blöcke Rand, Oberseiten höchstens 0,25 Blöcke auseinander, keine unebenen Blöcke), setzt er dort auf, wo
  `land()` kam; sonst auf der ersten Stelle in Ringen von 1 bis 8 m, deren Boden mit 0,4 Blöcken Rand ganz gleich hoch
  ist. Die Oberseiten je Block beschreibt `FakeServerFlight.Terrain` (Landeplatz mit 2 Pixeln, unebene Blöcke wie
  Treppen). Das ist Propwashs Regel ab dem Fix GCCA12P-1; Propwash 0.6.0-dev.9 zählt Blöcke in Teilhöhe noch nicht als
  eben, diesen Unterschied deckt nur Schritt 351 auf.
- JUnit `integration.autonomy.AutonomyAssistTest`: Die Landeplatz-Fälle legen den Landeplatz als Block mit 2 Pixeln auf
  den Boden (statt den ganzen Boden anzuheben). Neu: `landingOverUnevenGroundEndsOnTheLevelSpotPropwashPicks` (Treppe
  unter der Drohne: Landung mindestens 1,5 m daneben auf ebenem Boden),
  `afterLandTheAssistNoLongerSteersAndPropwashLandsWhereItWasCalled` (nach Ablauf der Ausrichtzeit höchstens 0,3 m
  neben dem Punkt von `land()`, nicht mehr auf der Mitte) und `padBesideUnevenGroundIsLandedOnOnceTheDroneIsCentred`
  (Landeplatz neben Treppen, Anflug über den Treppen: erst über die Mitte, dann höchstens 0,1 m daneben auf der
  Oberseite). Vor dem Fix des Fakes 2 von 112 Tests im Paket rot.
- Selbsttest 351 `mission: präzise Landung`, gezielt `'-PcamdronesSelftestOnly=351'` (etwa 1 min). Grün erst mit dem
  Propwash-Fix GCCA12P-1 (eben sind auch Blöcke mit ebener Oberseite in Teilhöhe); mit Propwash 0.6.0-dev.9 scheitert
  der Schritt mit „setzt 2,05 m neben der Mitte des Landeplatzes auf (Grenze 0,30 m)“.

## 20. Wand mit Oberkante knapp über der Flughöhe (GCCA3A-1)

Befund GCCA3A-1: Mit Vollgas in N gegen eine Wand, deren Oberkante etwa 1 m über der Flughöhe lag, stieg die Drohne
beim Bremsen 0,7–1,1 m. Mit gehobener Nase bei Fahrt geben die Rotoren mehr Schub, Propwashs Höhenhalter holt das nur
langsam zurück, und die Flughilfe glich nur nach oben aus. Der Sensor vorn ging über die Kante, das Gedächtnis vergaß
die Wand, weil der Strahl auf Flughöhe dort frei war, und die Drohne fuhr mit 2–3 m/s wieder an: Stopp-Abstand verfehlt
oder Flug über die Wand. Seit dem Fix:

- beim Bremsen vor einem Hindernis hält die Flughilfe die Höhe: Solange `ObstacleGuard.braking()` gilt (Notbremse oder
  begrenztes Tempo zum Hindernis, nicht beim Übersteigen), gleicht `VerticalControl.settle` das Steigen in beide
  Richtungen aus (Verstärkung 2, höchstens 2 m/s); sonst wie bisher nur nach oben (`catchUp`);
- die Sensoren vorn, hinten und seitlich tasten zusätzlich waagrecht auf Flughöhe sowie 0,5 m darüber und darunter
  (`NavContext.probe`, am Client über Propwashs `DroneAccess.obstacleDistance` ohne Entities, einmal je Tick; über Grund
  und unter der Decke höchstens bis 0,5 m Abstand). Der kleinere Wert aus Rahmensensor und Strahlen geht an den Wächter;
- eine gesehene Wand bleibt ohne Zeitgrenze gemerkt, bis die Drohne seitlich mehr als 1,5 m ausweicht, sich um mehr als
  20° wegdreht, mehr als 1 m an der Stelle vorbei oder außer Reichweite (15 m) ist, oder bis die Strahlen die Stelle
  frei sehen (ohne Strahlen der Welt, etwa am Server: der Rahmensensor mit höchstens 0,25 m Abstand zur Flughöhe an der
  Stelle). Ist die Drohne seit dem Sehen ohne Gas mehr als 0,5 m gestiegen, löscht ein freier Blick die Wand nicht. Mit
  Gas, in Automatik-Funktionen, beim Übersteigen und ohne Pilot zählt die neue Höhe (Ausweichen nach oben).

Tests:

- JUnit `integration.NavAssistWallTopTest`: Modell mit veränderlicher Höhe (Propwashs Höhenhalter mit P 0,9, D 2,2,
  I 0,9 und höchstens 1,5 m/s Steigen, Motor 0,05 s, Schub abhängig vom Luftstrom durch die Rotoren), sonst wie
  `NavAssistWallStopTest`, gegen eine 12 m hohe Wand. Tour und Kino in N und C mit Oberkante 0,3, 0,6, 1,0 und 1,5 m über
  der Flughöhe, mit und ohne Strahlen der Welt, stehen mindestens 1,5 m davor, ohne über die Wand zu fliegen; beim
  Bremsen in 3, 6 und 10 m steigen sie höchstens 0,35 m; ohne Wand halten sie die Höhe und erreichen 85 % des
  Normal-Tempos. Vor dem Fix stieg das Modell 0,60–1,11 m, mit Oberkante 0,3 m darüber flog es über die Wand, mit 0,6 m
  prallte es auf, mit 1,0 m Tour Aufprall und Kino 0,44 m; nach dem Fix 2,10–2,31 m und höchstens 0,29 m Steigen.
- JUnit `integration.NavAssistWallMemoryTest`: Die Wand bleibt, wenn die Drohne ohne Gas 1,1 m steigt und der Blick
  auf Flughöhe frei ist (mit und ohne Strahlen der Welt); sie endet beim seitlichen Ausweichen, Wegdrehen, Entfernen und
  beim Steigen mit Gas; auf gleicher Höhe löscht ein freier Blick sie; der Sensor vorn tastet waagrecht über und unter
  der Flughöhe. Vor dem Fix 3 von 4 rot.
- `logic.ObstacleGuardTest#reportsBrakingWhileItLimitsTheSpeedTowardsAnObstacle`,
  `logic.VerticalControlTest#whileBrakingForAnObstacleARiseIsPulledBack`.
- Selbsttest 367–370 „hindernis: Vollgas gegen die Wand“ (`'-PcamdronesSelftestOnly=361-370'` mit §18, etwa 5 min):
  Tour und Kino in N in 11,0 und 11,6 m, Oberkante 1,0 bzw. 0,4 m darüber; die Höhe wird nach dem Start mit wenig Gas
  auf ±0,25 m eingestellt. Flug über die Wand ist FAIL, das Ergebnis nennt die Höhe beim Start und die höchste Höhe.
  Vor dem Fix auf Fabric 368 und 370 FAIL mit 1,40 m (Steigen 1,32 bzw. 0,71 m). Nach dem Fix auf Fabric und NeoForge
  alle zehn Schritte 361–370 PASS; 367–370 mit 2,17–2,21 m nächstem Abstand und höchstens 0,27 m Steigen.

## 21. Winkelgrenze aus dem PID-Tab (GCCA3B-1)

Befund GCCA3B-1 (aus G-GCCA3-1-2): Regler und Bremsplan rechneten fest mit Propwashs Winkelgrenze 60°
(`NavInput.DEFAULT_ANGLE_LIMIT`). Im PID-Tab von Propwash lässt sich die *Angle-Grenze* (`pid.angleLimitDeg`) zwischen
10° und 85° einstellen, und Propwash teilt den Knüppel durch genau diesen Wert. Bei kleinerer Grenze neigte die Drohne
weniger als geplant (bei 30° statt 40° nur 20°, bei 45° nur 30°), bremste zu schwach und kam der Wand zu nahe (in der
Prüfung Kino N bei 30° in 6 m 0,9 m, in 10 m bei 45° Aufprall). Propwash liefert die wirksame Grenze ab 0.6.0-dev.10 über
`FlightState#angleLimitDeg()` (F-GCCA3P-1). Seit dem Fix:

- `NavAssist` übernimmt `FlightState#angleLimitDeg()` in jedem Tick nach `NavInput.angleLimit` (am Client und im
  Add-on-Flug am Server); Knüppel = Neigung / Grenze stimmt damit auch bei kleinerer Grenze;
- Regler und Bremsplan nehmen das Minimum aus der eigenen Höchstneigung 40° und der Grenze
  (`HorizontalControl.maxTilt(in)` und `maxAcceleration(in)`, `ObstacleGuard.brakeAngle(in)` und
  `brakeAcceleration(in)`): Notbremse, Tempo zum Hindernis (`commandLimit`, `allowedToward`, `emergencyLimit`), nötige
  Sichtweite dicht über dem Boden und das Ende der Notbremse; die Folgeverzögerung `LOOP_ACCEL` (3 m/s²) höchstens so
  groß wie die Bremsverzögerung (wirkt erst unter 17°). Ohne Tune (60°) und ab 40° ist alles wie bisher;
- Sport neigt höchstens 35° oder die kleinere Grenze, ATTI höchstens 25° oder die kleinere Grenze (vorher bei 30° nur
  17,5° bzw. 12,5°).

Mit 30° fliegt die Kino in N mit Sensoren vorn etwa 11 m/s statt 13 m/s (Bremsweg innerhalb der 15 m Sensorreichweite).

Tests:

- JUnit `integration.NavAssistAngleLimitTest` mit 10°, 20°, 30°, 45°, 60° und 85°: die Flughilfe übernimmt die Grenze
  aus `FlightState` (ohne Tune 60°); bei vollem Knüppel kommandiert sie genau das Minimum aus 40° und Grenze, die im
  Modell geflogene Neigung liegt höchstens 3° daneben; Sport neigt `min(35°, Grenze)`; Vollgas gegen die Wand in beiden
  Modellen (`NavAssistWallStopTest` mit fester Höhe: Tour und Kino in N und C in 1,5, 3, 6 und 10 m;
  `NavAssistWallTopTest` mit veränderlicher Höhe: Tour und Kino in N in 3, 6, 10 und 11 m, mit und ohne Strahlen der
  Welt) bleibt mindestens 1,5 m vor der Wand und steht danach. Die Wandmodelle und `FakeFlight.State` haben dafür eine
  einstellbare Grenze. Vor dem Fix alle 6 Fälle rot (Kino N bei 30° in 10 m Aufprall, Tour N bei 30° in 10 m 1,09 m,
  Kino N bei 45° in 10 m 1,24 m, Neigung bei 30° höchstens 21,3°, Sport bei 20° 11,7°); nach dem Fix 2,15–2,34 m.
- `logic.ObstacleGuardTest#plansTheBrakeWithTheSmallerTiltOfItsOwnLimitAndTheAngleLimit` (bei 30° Notbremse mit 8 m/s
  auf 8 m und voller Grenze, bei 60° keine), `logic.HorizontalControlTest#nearTheGroundTheTiltKeepsWallsInSightWithTheBrakeOfASmallerAngleLimit`.
- Selbsttest 371–376 „hindernis: Vollgas gegen die Wand“ mit Winkelgrenze (`'-PcamdronesSelftestOnly=371-376'`, etwa
  3 min): die Drohne wird mit `DroneData.withTune(pid.angleLimitDeg)` gesetzt, der Schritt prüft die Grenze am Client
  (`DroneView#angleLimitDeg`) und nennt die größte Neigung; Kino N in 6 m mit 30°, 45° und 60°, Kino N in 10 m mit 30°
  und 45°, Tour N in 10 m mit 30°, sonst wie §18. Vor dem Fix auf Fabric 371, 374, 375 und 376 FAIL mit 1,44–1,49 m
  (größte Neigung bei 30° 20,8–24,1°). Nach dem Fix auf Fabric und NeoForge alle sechs PASS mit 2,17–2,24 m nächstem
  Abstand, größte Neigung 30,0° bzw. 40,0°; 361–370 bleiben PASS.

## 22. GPS weg vor der Wand: ATTI ohne Hindernis-Bremse (GCCA3C-1)

Befund GCCA3C-1 (aus G-GCCA3A-1): Vor einer hohen Wand stand die Drohne in etwa 1 m Höhe richtig 2,3 m davor, verlor
nach 5–9 s im Schatten der Wand das GPS, und der Wechsel in ATTI setzte den Hindernis-Wächter zurück. Mit weiter
gedrücktem Knüppel flog sie gegen die Wand. ATTI ohne Hindernis-Bremse bleibt (wie bei echten GPS-Drohnen,
Entscheidung der Steuerung 28.10.), der Übergang ist aber sicher und sichtbar. Seit dem Fix:

- `AttiWallHold` (in `NavLogic`): Sieht beim Wechsel in ATTI (GPS oder Kompass) ein Sensor vorn, hinten oder seitlich
  eine Wand (endlicher Abstand unter 15 m, auch aus dem Gedächtnis von §20) und zeigt der Knüppel zu ihr (Nick bzw. Roll
  über 0,05), bremst die Drohne und hält ihre Stelle (`HoldController` und `HorizontalControl` wie beim Halten, das OSD
  zeigt *OBSTACLE* rot, Warnung `OBSTACLE_STOP`), bis Nick und Roll einmal in der Mitte (höchstens 0,05) waren; danach
  freies ATTI. Gilt nur, wenn der Wächter vorher aktiv war (nicht in Sport ohne Funktion) und die Drohne Sensoren rundum
  hat, und nur im Pilotenflug. Höhe, Gieren und Gas bleiben wie in ATTI;
- ATTI mit Hindernis-Sensoren (Tour und Kino mit Sensoren rundum) meldet `NavOutput.Warning.OBSTACLE_OFF`: das OSD und die
  Kamera-Ansicht zeigen rot *ATTI - OBSTACLE SENSING OFF* (deutsch *ATTI - HINDERNISERKENNUNG AUS*, Schlüssel
  `camdrones.osd.warning.obstacle_off`), und beim Wechsel ertönt einmal der Ton `warning.atti` (Grenzton eine Stufe
  tiefer, Untertitel *ATTI – Hinderniserkennung aus*, Lautstärke wie die übrigen Warntöne, erneut erst nach 1 s ohne
  Warnung). Die Kompakt-Drohne (nur Sensor nach unten) meldet das nicht.

Tests:

- JUnit `logic.AttiWallTest` (Punktmasse): Tour und Kino stehen mit Vollgas 2,31 m vor der Wand, nach dem GPS-Verlust
  15 s weiter Vollgas: Abstand bleibt 2,31 m, mit 3 m/s Wind zur Wand nächster Abstand 2,16 m; nach der Mitte fliegt
  ATTI wieder frei zur Wand; zeigt der Knüppel beim Verlust nicht zur Wand, gilt sofort freies ATTI; Warnung nur mit
  Sensoren rundum und nur in ATTI.
- JUnit `integration.NavAssistAttiWallTest` (Modell aus `NavAssistWallTopTest` mit Strahlen der Welt): Tour und Kino in
  1 und 3 m, GPS-Verlust nach 5 s Stand, 15 s weiter Vollgas: 2,30 m vor der Wand, ATTI mit `OBSTACLE_OFF` und
  `OBSTACLE_STOP`; GPS-Verlust im Anflug 12, 8 und 5 m vor der Wand: Stillstand 3,9–7,7 m davor; nach der Mitte freies
  ATTI. Vor dem Fix 4 von 6 Fällen rot (Aufprall nach 0,27 m bzw. Flug durch die Wand, keine Warnung).
- `client.hud.CamOsdTest`, `CamOsdElementsTest` (neun Warnungen), `CovCamBSoundsTest` (`warning.atti` angemeldet und
  gespielt).
- Selbsttest 377–378 „hindernis: GPS weg vor der Wand“ (`'-PcamdronesSelftestOnly=377,378'`, etwa 2 min): Kino bzw.
  Tour in N in 1,0 m, Vollgas gegen die 12 m hohe Wand bis zum Stillstand, dann ein Steindach 6 m über dem Boden
  (±12 m) über die Drohne (GPS weg), Vollgas weiter bis ATTI und dann 15 s in ATTI: kein Aufprall, nächster Abstand
  mindestens 1,5 m, in jedem Tick Halt und `OBSTACLE_OFF`, das OSD zeigt *ATTI - OBSTACLE SENSING OFF*, genau ein Ton
  `warning.atti`; danach Knüppel in die Mitte: ATTI mit `OBSTACLE_OFF`, ohne Halt.
  Auf Fabric und NeoForge je 2 von 2 PASS: nach dem GPS-Verlust (0,8–1,4 s unter dem Dach) nächster Abstand
  2,46–2,60 m, nach 15 s Vollgas 2,50–2,65 m vor der Wand, 301 von 301 Ticks mit Halt und Warnung, ein Ton. Die
  Gruppe `hindernis:` (6, 33, 34, 361–378) bleibt auf beiden Loadern 22 von 22 PASS; `flug:`, `kompass:`, `osd:` und
  `hud:` auf Fabric 22 von 22 PASS, auf NeoForge 5, 15, 16, 22, 39 und 47 PASS. Steckt eine Funke am Rechner, übernimmt
  Propwash sie im Selbsttest statt der Tastatur; dann den Lauf mit `SDL_JOYSTICK_IOKIT=0 SDL_JOYSTICK_HIDAPI=0
  SDL_JOYSTICK_MFI=0` starten.

## 23. Tempo-Antwort auf Knüppel-Sprung, Loslassen ohne Zurückziehen, Modus-Hinweis (Owner-Playtest 28.10., OWNC6-3)

Befund OWNC6-3: Beim manuellen Fliegen „will die Drohne nur schwer ihre Position verlassen“. Gemessen mit vollem
Ausschlag (Tastatur-Ausschlag 100 %, Wind aus, 8 m Höhe) vor dem Fix auf Fabric:

- Anfahren in N: 90 % des Höchsttempos nach 1,70 s (Kompakt), 1,95 s (Tour); Kino nur 13,0 m/s statt 15 m/s, weil sie
  mit Sensoren nur so schnell fliegt, dass sie innerhalb der 15 m anhalten kann (§21, Anleitung). Kino in C 1,85 s,
  Kompakt und Tour in C 1,95 s. Totband 5 %, dahinter linear, kein Expo. Das Anfahren war also nicht zu träge.
- Loslassen: Die Drohne zog nach dem Bremsen 0,34–0,60 m (N) bzw. 0,20–0,37 m (C) zurück. Ursache: Der Wind-Integrator
  von `HorizontalControl` lernte beim Bremsen die Bremsverzögerung und im Flug den Fehler der Luftwiderstands-Vorsteuerung
  mit und schob die Drohne nach dem Stillstand zurück.
- Aus dem Halten (*Anhalten und schweben*) brauchte es mehr als 25 % Ausschlag, bis die Drohne losflog
  (`NavLogic.STICK_OVERRIDE`); kleinere Ausschläge ließ die Positionshaltung stehen.
- Sport mit vollem Ausschlag: Tour und Kino sanken bei hohem Tempo und schlugen nach 6–7 s aus 8 m Höhe auf dem Boden
  auf (bei 18 bzw. 22 m/s), weil der Schub die Höhe bei 35° Neigung nicht mehr hielt.

Seit dem Fix:

- `HorizontalControl` trennt den Windanteil (gelernt nur beim Halten und langsamen Kommandos) vom Anteil im Flug (gelernt
  nur bei Tempo-Kommandos ab 0,5 m/s, klingt danach in 0,3 s ab); nach einem Tempo-Kommando oder bei mehr als 1,5 m/s
  Fehler ruht der Windanteil, bis der Fehler unter 0,3 m/s liegt;
- aus dem Halten fliegt die Drohne wie in N schon hinter dem Totband von 5 % los (`NavLogic.sticksMoved`), Gas weiter
  erst ab 30 %, damit ein nicht mittiges Tastatur-Gas das Halten nicht beendet; Missionen und Automatik-Flüge brechen
  weiter erst ab 25 % ab;
- Sport neigt weniger (20°/s, bis 10°), solange die Drohne schneller als 0,3 m/s sinkt oder mehr als 1 m unter der
  gehaltenen Höhe liegt, und wieder voll (5°/s), sobald sie die Höhe hält;
- das OSD-Element *Modus-Hinweis* (`camdrones:mode_hint`, Spalte 15, Zeile 5, 28 Zeichen, Standard an) zeigt beim Start
  eines Flugs und bei jedem Wechsel zwischen N, C, S und ATTI 5 s lang, was der Modus bedeutet (EN/DE aus
  `common/src/lang/hud/`, Umlaute als AE/OE/UE); das Flugschul-Kapitel *Kamera-Drohne fliegen* erklärt N, C, S und ATTI.

Tests:

- JUnit `logic.StickResponseTest`: Loslassen nach 5 s Vollgas bzw. 60 % in N und C für alle Klassen (Grenze 0,1 m, vor
  dem Fix Kompakt N 0,56 m), dasselbe bei leichtem Wind (Grenze 0,2 m, vor dem Fix 0,56 m), von S nach N bei Vollgas
  (Grenze 0,1 m, ohne das Ruhen bei großem Fehler 0,41 m), kleiner Ausschlag (8, 15, 20 %) aus dem Halten fliegt mit dem
  Tempo hinter dem Totband los (vor dem Fix blieb das Halten), Knüppel linear hinter dem Totband ohne Expo, Sport neigt
  beim Sinken weniger und danach wieder voll. `client.hud.ModeHintTest`: Zeitpunkte und 5 s des Hinweises, Texte EN und
  DE passen ungekürzt in 28 Zeichen. `CamOsdElementsTest`: sechs Elemente.
- Selbsttest 621–623 „flug: Tempo-Antwort auf Knüppel-Sprung“ je Klasse (`'-PcamdronesSelftestOnly=621-623'`, etwa
  3 min): Tastatur-Ausschlag 100 %, Wind aus, Start mit OSD-Hinweis (vor dem GPS-Fix ATTI, mit Fix N), N vorwärts,
  loslassen, C rückwärts, loslassen, S vorwärts bis 90 % des Sport-Tempos oder 8 s, zurück nach N und bremsen; nach jedem
  Wechsel der OSD-Hinweis. Grenzen: 90 % des Höchsttempos (Kino N mit Sensoren 12,99 m/s) in N nach höchstens 2,5 s, in
  C nach 3 s, in S 90 % des Normal-Tempos nach 3 s; nach dem Loslassen höchstens 0,3 m zurück; mit vollem Ausschlag
  höchstens 1,5 m tiefer; Anzeige während des Ausschlags nur der Modus. Vor dem Fix auf Fabric alle drei FAIL (N
  0,34–0,60 m zurück, C Kino 0,37 m, S Tour und Kino Aufprall). Nach dem Fix auf Fabric und NeoForge alle drei PASS:
  N 1,60/1,90/2,05 s, C 1,95–2,05 s, S 2,05–2,50 s; zurück nach dem Loslassen 0,05–0,12 m (N, auch von S nach N) und
  0,04–0,05 m (C); in S höchstens 0,6–1,1 m tiefer bei 14,7–19,7 m/s Spitze.
- Selbsttest 39 „osd: Namen der Add-on-Elemente bei GUI 320×240“ scrollt die Liste nun auch zurück nach oben, weil die
  sechs Elemente bei 320×240 nicht mehr in eine Ansicht passen.

## 24. Flugmodus-Taste und Flugmodus-Schalter wählen N, C und S (Owner-Playtest 28.10., OWNC7-1)

Befund OWNC7-1: Die Flugmodus-Taste R öffnete bei der Kamera-Drohne nur Propwashs Einblendung Acro/Angle/Horizon, der
Modus der Drohne blieb N. `NavAssist` las `FlightState#pilotMode()` nie aus; umschalten ging nur mit den eigenen
Funktionen *Flugmodus N/C/S* (auf einem Kanal belegt) und *Nächster Flugmodus* (Ziffernblock /, auf Tastaturen
ohne Ziffernblock nicht erreichbar). Seit dem Fix:

- `NavAssist` übernimmt im Pilotenflug jede Änderung von Propwashs Flugmodus (`FlightState#pilotMode()`, Taste R,
  Tasten Acro/Angle/Horizon und Flugmodus-Schalter der Funke) als N, C oder S: Angle = N, Horizon = C, Acro = S
  (`NavAssist.stickMode`). Propwashs Taste schaltet Acro → Angle → Horizon → Acro, für die Kamera-Drohne also
  N → C → S → N. Mit Propwashs Standard-Belegung der Funke (Kanal 6) ist hoch N, Mitte C, niedrig S.
- Beim Start der Flughilfe gilt die Stellung von Propwashs Flugmodus (Tastatur startet in Angle, also N), danach gilt
  die letzte Änderung: Propwashs Flugmodus oder die eigenen Funktionen *Flugmodus N/C/S* und *Nächster Flugmodus*.
  Eigene Schalter, die beim Start schon an sind, gelten vor der Stellung von Propwashs Flugmodus.
- Zurücksetzen der Flughilfe (etwa nach GPS Rescue) behält den Modus, bis Propwashs Flugmodus sich ändert. Ohne
  Pilot (Flug am Server) bleibt Propwashs Flugmodus außen vor.
- Propwashs Einblendung nannte damals weiter Acro, Angle oder Horizon (kein Haken in der API), das OSD zeigt N, C
  oder S; seit AUTO43-1 nennt auch die Einblendung N, C oder S (§35). Die Beschreibungen von *Flugmodus N/C/S* und
  *Nächster Flugmodus* im Konfigurator (K), Tab *Modi*, Seite *Add-ons*, nennen die Flugmodus-Taste (Standard R) und
  die Stellungen; die Flugschule zeigt im Kapitel *Kamera-Drohne fliegen* beim Flugmodus Propwashs Flugmodus-Taste
  statt Ziffernblock /.

Tests:

- JUnit `integration.NavAssistFlightModeTest`: Zuordnung Angle/Horizon/Acro → N/C/S; Propwashs Taste schaltet je
  Klasse (Kompakt, Tour, Kino) sechsmal N → C → S → N mit Anzeige N, C, S; jede Schalterstellung wählt ihren Modus,
  auch beim Start; Sport aus Acro fliegt Knüppel in Angle; eigene Schalter und *Nächster Flugmodus* wirken weiter, es
  gilt die letzte Änderung; Zurücksetzen behält den Modus; ohne Pilot bleibt der Modus N. `FakeFlight.State` hat dafür
  einen einstellbaren Flugmodus (`mode`, `flies`); Prüfstände mit festem Modus (`NavAssistWallStopTest`,
  `NavAssistWallTopTest`, `NavAssistAngleLimitTest` für Sport) stellen Propwashs Flugmodus passend. Vor dem Fix 5 der
  6 Fälle rot (Modus blieb N), nach dem Fix 7 von 7 grün; `:common:test` 754 Tests grün.
- Selbsttest 381–383 „flug: Flugmodus-Taste von Propwash schaltet N → C → S“ (Kompakt, Tour, Kino in 5 m Höhe):
  Start N, dann viermal Propwashs Flugmodus-Taste mit N → C → S → N → C; geprüft werden Modus und Anzeige der
  Flughilfe und der Text des OSD-Elements *Nav-FC-Modus*. 384 „flug: Flugmodus-Schalter der Funke wählt N, C und S“:
  Propwashs Test-Funke (`PropwashClientApi.simulateRadio`, Standard-Belegung), Kanal 6 niedrig, Mitte, hoch, niedrig,
  hoch, Mitte ergibt S, C, N, S, N, C. 385 „tasten: K-Menü nennt die Flugmodus-Taste für N, C und S“: Tab *Modi*, Seite
  *Add-ons*, Zeile *Nächster Flugmodus* zeigt die Beschreibung mit der Flugmodus-Taste (EN und DE). Filter
  `'-PcamdronesSelftestOnly=381-385'`, etwa 2 min. Vor dem Fix auf Fabric 381–384 FAIL („Modus bleibt NORMAL (N), OSD
  N“), nach dem Fix auf Fabric und NeoForge 381–385 PASS; die Gruppen `flug:` und `tasten:` bleiben auf Fabric und
  NeoForge PASS, `flugschule:` auf Fabric.

## 25. Automatik während einer Mission ohne Pilot (AUTO13-1)

Befund AUTO13-1 (aus G-P-CAM-2): *Automatik* im Flugplaner während einer laufenden Mission ohne Pilot ersetzte die
Mission, aber `camdrones:mission_state` blieb `RUNNING`. Die Flughilfe pausiert die Mission beim Wechsel (`NavLogic`
setzt sie auf `PAUSED`), der `FlightListener` verwarf diese Meldung aber, weil der Plan schon `AUTO_SHOT` war. Folgen:
*⏸ Pause* meldete „Mission pausiert“ ohne Wirkung (der Automatik-Flug lief weiter), *▶ Start* begann wieder bei
Wegpunkt 1, das OSD ohne Pilot zeigte den Wegpunkt mit Restzeit statt *AUTO*. Dasselbe galt für Follow me und Point of
Interest über das Paket `smart_mode`.

- **Regel** (`integration.autonomy.ProgressCheck#fromFlight`): Fortschritt eines Flugs ohne Pilot gilt wie bisher mit
  geladener Mission; zusätzlich gilt `PAUSED`, solange die Drohnen-Daten noch `RUNNING` zeigen (eine andere Funktion
  hat die Mission unterbrochen), mit dem Wegpunkt, an dem sie stand. Danach lehnt *⏸ Pause* ab („läuft nicht“),
  *▶ Start* und Fortsetzen fliegen ab diesem Wegpunkt weiter. Endet der Flug danach, bleibt `PAUSED` stehen (wie bei
  jedem Flug ohne Mission, `EndDecision`).
- **OSD ohne Pilot** (`client.hud.CamOsdSource#label`): Bei pausierter Mission zeigt die Modusanzeige die laufende
  Funktion aus `camdrones:smart` (*AUTO*, *POI*, *FOLLOW*, *HOLD*), ohne Funktion weiter *WP n/m*. Das Element
  *Mission* zeigt den Wegpunkt weiter als pausiert, dazu *PAUSED*. So sieht es auch der Pilot.
- JUnit `integration.autonomy.MissionInterruptTest` (echte `AutonomyAssist` im Flugmodell `FakeServerFlight`,
  Drohnen-Daten über `ProgressCheck.fromFlight`, Befehle über `CommandDecision`): Automatik *Kreis*, Point of Interest
  und Follow me an Wegpunkt 2 ergeben `PAUSED` an Wegpunkt 2, Pause wird abgelehnt (`NOT_RUNNING`), Fortsetzen und
  Start gehen an Wegpunkt 2, nach dem Fortsetzen wieder `RUNNING`; ein Flug ohne Mission lässt `DONE` stehen; dazu die
  Regelfälle von `fromFlight`. `client.hud.CamOsdSourceTest#labelShowsTheFunctionThatInterruptedTheMission`. Vor dem
  Fix 5 von 11 rot (Drohnen-Daten blieben `RUNNING 1`, OSD *WP 2/4* statt *AUTO*).
- Stand 28.10.: `:common:test` 753 Tests grün (die 5 roten vor dem Fix eingeschlossen), Build beider Loader grün;
  Selbsttest `'-PcamdronesSelftestOnly=osd:,hud:,automatik:,11,12,29'` auf Fabric (375 s) und NeoForge (381 s) je
  13 Schritte PASS; Server-Rauchtest beider Loader und Client-Rauchtest Fabric PASS.

## 26. Aufnahmemodus: Flashback-Aufnahme oder direkte Aufnahme (Owner 28.10., cam-rec-modes)

Owner 28.10.: Im Tab *Kamera-Drohnen* des K-Menüs eine Einstellung *Aufnahmemodus*. *Flashback-Aufnahme* schreibt den
Kamerapfad des Gimbals in die Flashback-Aufzeichnung, auch im Hintergrund ohne Live-Ansicht und ohne FPS-Kosten, und
meldet außer Sichtweite die Kamera-Lage über Propwashs Kamera-Chunks an (`PropwashClientApi.cameraChunks()`, seit
0.6.0-dev.11). *Direkte Aufnahme* nimmt das Gimbal-Bild nur in der Live-Ansicht als Video auf (Hintergrund-Video hätte
Probleme mit Shadern); ohne Live-Ansicht kommt der Hinweis „Direkte Aufnahme braucht die Live-Ansicht – Flashback-Modus
nimmt im Hintergrund auf“. Gilt für Videos, Fotos und Wegpunkt-Aufnahmen.

- **Modell** (`client.record.model`): `RecordingMode` (Standard `FLASHBACK` nur mit Flashback, ohne Flashback nicht
  wählbar und wirksam `DIRECT`), `RecordingRoute.of(Wahl, Flashback bereit, Live-Ansicht)` → `FLASHBACK`, `DIRECT` oder
  `NEEDS_LIVE_VIEW`; `CameraAngles` (Winkel wie Flashbacks Kamera-Keyframes, Rahmen aus `DroneView#forward/up`,
  senkrechtes Sichtfeld für 16:9), `PathThinner` (lineare Prüfung mit 5 cm und 1°, spätestens alle 100 Ticks ein
  Keyframe, höchstens 800, sonst Toleranz × 1,5; Sichtfeld-Keyframes ab 0,25° Änderung, `HOLD`).
- **Einstellung:** `recording_mode` in `config/camdrones-client.json`, nur geschrieben, wenn der Spieler wählt; ohne
  Eintrag folgt der Standard Flashback. Tab *Kamera-Drohnen*, Abschnitt *Aufnahme*: zwei Knöpfe, ohne Flashback ist
  *Flashback-Aufnahme* ausgegraut, darunter der Hinweis.
- **Flashback-Anbindung** (`client.record.FlashbackLink`, nur Reflection auf Flashback, nicht auf Propwash; aufgelöst
  beim ersten `status()`: `ABSENT`, `READY`, `INCOMPATIBLE`): Aufzeichnung starten und beenden, Marker, Szene in
  Flashbacks Editorzustand schreiben (`EditorStateManager`). `GimbalPathRecorder`: je Drohne eine Aufnahme; im
  Client-Takt eine Probe, solange Flashback schreibt (Zeit = geschriebene Ticks), Lage aus einem eigenen `GimbalModel`
  mit denselben Eingaben wie das Gimbal-Bild (auch ohne gezeichnete Drohne), Position = Schwerpunkt; am Ende Szene
  *<Name> · Aufnahme N* mit den Spuren *Gimbal-Kamera* (`SMOOTH`) und *Gimbal-Sichtfeld*; Foto = Marker und Szene mit
  einem Keyframe. Hat das Add-on Flashback gestartet, beendet es die Aufzeichnung 2 s nach der letzten Aufnahme.
- **Hintergrund** (`client.record.RecordingRouter`): eigene Drohnen = Seriennummer eines gekoppelten Flugplaners im
  Inventar; ohne Live-Ansicht (nicht geflogen, nicht als Operator gesteuert, kein Livebild) laufen deren Wegpunkt-Aktionen
  über den Aufnahmemodus. Außer Sichtweite (Abstand > Sichtweite − 1 Chunk, zurück unter Sichtweite − 2 Chunks)
  `request(id, 4)`, am Ende der Mission oder Aufnahme `release(id)`. Ein Ende der Mission ohne *Aufnahme aus* beendet
  auch die Aufnahme.
- **Grenze:** Propwashs eigene Aufnahme-Taste und der Schalter *Aufnahme* gehören Propwash und nehmen immer direkt auf
  (kein API-Weg, sie umzuleiten; im Bericht als API-Befund).
- **Selbsttests nehmen standardmäßig direkt auf** (`RecModeSteps.register` setzt `recording_mode` auf `direct`), damit die
  älteren Aufnahme-Schritte mit Flashback-Jar unverändert laufen; die Schritte 801–805 setzen den Modus selbst.
- JUnit `client.record.RecordingModeTest`, `RecordingModeConfigTest`, `CameraPathTest`, `RecordingSceneTest` (27 Tests).
- Selbsttest `'-PcamdronesSelftestOnly=801-805'`:
  - 801 Tab *Kamera-Drohnen*: Standard je nach Flashback, ohne Flashback Wert ausgegraut mit Hinweis, Klicks und Strg+S
    speichern (Fabric mit Flashback: `flashback`, NeoForge: Klick ohne Wirkung, `direct`).
  - 802 (nur Fabric mit Flashback-Jar): Kino mit gekoppeltem Flugplaner im Inventar, Mission ohne Pilot und ohne
    Live-Ansicht mit *Aufnahme an* nach 12 m, *Foto* und *Aufnahme aus* etwa 300 Blöcke weit, Landung am Ende. Geprüft:
    Aufnahme startet im Hintergrund und startet Flashback, die Drohne ist weiter weg als die Sichtweite und weiter am
    Client, Kamera-Chunks `OPENED` mit Radius 4, der Pfad wächst weit weg weiter, Foto und Szene übergeben, nach der
    Mission keine Lage mehr und Flashback beendet und gespeichert, die Szene steht mit gleich vielen Kamera-Keyframes in
    `flashback/editor_states/<Aufnahme>.json`, der Flug landet.
  - 803 Livebild einer schwebenden Kino, Direkt-Modus: Aufnahme über `GimbalClient.camera` startet Propwashs Video, nach
    3 s Stopp, neue `propwash_*.mp4` im Aufnahme-Ordner.
  - 804 Direkt-Modus, Mission mit Aufnahme ohne Live-Ansicht: ein Hinweis, keine Aufnahme, keine Datei.
  - 805 Flugschul-Kapitel *Aufnahmen: Flashback oder direkt* öffnet.
- Stand 28.10.: `:common:test` 797 Tests grün, Build beider Loader grün; Selbsttest Fabric mit Flashback 801–805 PASS
  (802: 845 Proben, 191 Kamera-Keyframes, Kamera-Chunks `OPENED` mit Radius 4 bei 272 Blöcken, Sichtweite 256), dazu
  17, 18, 26, 601, 602, 47, 342, 343, 532, 611 PASS; NeoForge 801, 803–805, 17, 601 PASS (802 und 26 ohne Flashback
  nicht angemeldet); Server-Rauchtest beider Loader PASS.

## 27. Loslassen im Sturm nach S und ATTI, Sport bis zum Sport-Tempo (Gegenprüfung G-OWNC6-3, AUTO23-1 und AUTO23-2)

Befund AUTO23-1: In starkem Wind (Wind 2 mit Regen, etwa 9 m/s, im Schweben 9–18° Neigung) zog die Drohne nach dem
Loslassen eines Flugs gegen den Wind 1,5–2,8 m zurück. Ursache: `HorizontalControl` setzte den gelernten Windanteil in
S und ATTI auf 0, und nach einem Bremsvorgang ruhte er, bis der Fehler unter 0,3 m/s lag. Im Sturm bleibt ohne Windanteil
ein Fehler von 0,5–1 m/s stehen, das Ruhen endete also nie (auch nach dem Wechsel von ATTI zu N beim GPS-Fix am Start).
Seit dem Fix:

- der Windanteil bleibt über S und ATTI erhalten (gelöscht nur am Boden und beim Zurücksetzen der Flughilfe);
- das Ruhen beginnt, wenn der Fehler über 1,5 m/s springt oder ein Tempo-Kommando endet, und endet unter 0,3 m/s Fehler
  oder sobald die gemessene Beschleunigung 0,5 s lang unter 0,8 m/s² liegt: Die Drohne bremst dann nicht mehr, der Rest
  ist Wind, den der Windanteil wieder lernt.

Befund AUTO23-2: Sport erreichte das Sport-Tempo nicht (Tour höchstens 16,8 statt 20 m/s, Kino höchstens 19,2 statt
26 m/s, Kompakt 90 % erst nach 6,5–7,4 s). Ursache: Propwashs Höhenhaltung begrenzt das Gas (bei der Schwebe-Drossel der
Kamera-Drohnen auf etwa 0,7), bei hohem Tempo verlieren die Props aber Schub; die Drohne sank, und Sport neigte sie
weniger (16–28°). Seit dem Fix:

- in Sport stellt die Flughilfe das Gas selbst (`logic.nav.SportThrottle`, `NavInput.hoverThrottle` aus
  `FlightState.hoverThrottle()`): Steigrate bzw. gehaltene Höhe aus dem Gas-Knüppel wie bisher, daraus Gas bis 100 %;
  Propwash bekommt in Sport nur Knüppel und Angle-Modus (Design §2.6: `sticks`, `mode(ANGLE)`). Ohne Schwebe-Gas (ältere
  Prüfstände) bleibt Propwashs Höhenhaltung;
- Sport neigt sich erst dann weniger, wenn auch Vollgas die Höhe nicht mehr hält (Gas über 98 %);
- Sport hört beim Sport-Tempo der Klasse auf: Die Neigung in Flugrichtung wird begrenzt (P 3°/(m/s) mit 1 s Vorhalt aus
  der Beschleunigung, I 4°/s je m/s), Bremsen und Neigung quer zur Flugrichtung bleiben frei.

Tests:

- JUnit `logic.WindReleaseTest` (Punktmasse, Sturm 15 m/s, Windanteil 2,25 m/s²): nach S→N bzw. ATTI→N wird der Wind
  in 10 s wieder gelernt (± 0,3 m/s²), Loslassen gegen und quer zum Sturm, auch direkt nach S und mit Böen, höchstens
  0,2 m zurück (mit Böen 0,3 m). Vor dem Fix 4 von 5 rot („Windanteil ruht noch“, 0,73 m zurück), danach grün.
- JUnit `logic.SportSpeedTest`: Sport mit vollem Knüppel bleibt bei quadratischem Luftwiderstand (frei 1,2-mal Sport-Tempo)
  unter 110 % und hält das Sport-Tempo auf ± 5 % (vor dem Fix Kompakt 19,2 m/s bei 16 m/s); über dem Sport-Tempo bleiben
  Bremsen und Neigung quer frei; mit Schwebe-Gas gibt S keine Höhe an Propwash, im Schweben Gas = Schwebe-Gas, N wieder
  mit Propwashs Höhenhaltung; Höhenmodell mit Schubverlust durch Anströmung: Propwashs Gasgrenze sinkt über 3 m, das
  eigene Gas hält die Höhe auf 1,5 m (über Propwashs Grenze, nie über 100 %), Steigen mit Vollgas; reicht auch Vollgas
  nicht, neigt Sport weniger.
- Selbsttest 621–623 prüft S jetzt gegen das Sport-Tempo: 90 % nach höchstens 0,3 s je m/s (Kompakt 4,8 s, Tour 6 s,
  Kino 7,8 s, längstens 10 s), Spitze höchstens 110 %, höchstens 1,5 m tiefer. Das Tempo kommt aus einem Fenster von
  10 Ticks und gilt für dessen Mitte (vorher 2 Ticks, dadurch zählten Positionssprünge des Clients als Tempo). Vor dem
  Fix auf Fabric alle drei FAIL (Kompakt 90 % nach 5,30 s, Tour und Kino nie, Spitze 14,61 / 16,86 / 21,69 m/s),
  danach auf Fabric und NeoForge PASS: 90 % nach 3,45–3,65 / 4,40–4,55 / 5,80–5,95 s, Spitze 15,73–16,01 /
  19,52–19,66 / 24,89–24,94 m/s, höchstens 0,65 m tiefer.
- Selbsttest 624 „flug: Loslassen im Sturm, auch nach S“ (Tour, Wind 2 mit Regen, `'-PcamdronesSelftestOnly=624'`, etwa
  1,5 min): 12 s schweben, Windrichtung aus der Neigung, N gegen den Wind 3 s und loslassen, S mit dem Wind 2,5 s, N,
  10 s halten, N gegen und quer zum Wind und jeweils loslassen; Grenze 0,5 m oder die Abweichung beim Halten im selben
  Sturm, wenn sie größer ist; danach klares Wetter und ruhiger Wind. Vor dem Fix auf Fabric FAIL (0,81 m bzw. 0,64 m
  gegen den Wind, nach S 0,62 m), danach auf Fabric und NeoForge PASS in acht Läufen mit 0,07–0,43 m (Abweichung beim
  Halten 0,18–0,58 m).

## 28. Flugplaner in der dritten Person (Owner-Playtest 28.10., OWN2810C-1)

Vorher hatte das Item-Modell `flight_planner` in der dritten Person die Werte der Funke (`thirdperson_righthand` und
`_lefthand`: Drehung 60°, Verschiebung (0; 3,5; 2,2), Maßstab 0,5). Propwash zeichnet die Funke mit einer eigenen Haltung
in beiden Händen, dort wirken diese Werte nicht; am Tablet stand es damit hochkant (78°) seitlich neben der Hüfte, gut
5 px neben der Körpermitte, Bildschirm nach hinten.

Seit dem Fix: Drehung 37°, Verschiebung (−4,5; 2,8; 1,3), Maßstab 0,5 (Generator `tools/assets/camstyle/planner.py`,
Modell aus `style_generate.py`). Die Hand in der Vanilla-Haltepose (Arm 18° nach vorn) hält das Tablet an der Ecke; es
liegt quer vor dem Bauch, 55° geneigt, Bildschirm zum Gesicht; in der Nebenhand spiegelt Minecraft die Werte. Erste
Person, Inventar, Boden, Rahmen, Regal und Kopf bleiben unverändert. Beim Gehen bleibt das Tablet vor dem Rumpf (bis 23°
Armschwung nach hinten); nur im äußersten Rückschwung beim Sprinten berührt die hintere Kante den Rumpf um weniger als
1 px, wie bei anderen Items in der Hand.

Tests:

- Selbsttest 612 „planer: Tablet in der dritten Person vor dem Körper, Bildschirm zum Spieler“
  (`'-PcamdronesSelftestOnly=612'`, etwa 10 s in der Welt): Flugplaner erst in der Haupt-, dann in der Nebenhand. Der
  Schritt baut wie das Spiel den Zustand des Spielers und das Spielermodell mit Haltepose, setzt die Haltung aus
  `ItemInHandLayer` an (−90° um x, 180° um y, Versatz 1/2/−10 px) und bestimmt die Anzeige-Transformation des geladenen
  Item-Modells aus dessen Eckpunkten (`visitExtents` ohne Kontext und in der dritten Person). Gemessen 20 Ticks lang im
  Rumpf-System (px, y nach unten, −z nach vorn): Mitte höchstens 3 px seitlich, mindestens 4 px vor der Körpermitte und
  2–12 px unter den Schultern, Bildschirm zum Auge mindestens 0,6, Hochachse höchstens 0,9 senkrecht (nicht hochkant),
  Breite quer mindestens 0,9, kein Punkt im Rumpf, Nebenhand spiegelbildlich bis 0,6 px (ohne Armwippen gemessen).
  Prüfbilder von hinten (20° von oben) und von vorn je Hand bei 2,5 m Kamera-Abstand in
  `<loader>/runs/selftest-client/own2810c/`.
- Vor dem Fix auf Fabric und NeoForge FAIL: Haupthand Mitte x −5,39 bzw. −5,40 px, zum Auge 0,44, Hochachse 0,97 bzw.
  0,98; Nebenhand x 5,77 bzw. 5,79 px, 0,45, 0,97. Nach dem Fix PASS: Fabric Haupthand x −1,38, y 8,67, z −7,32 px, zum
  Auge 0,76, Hochachse 0,81, Nebenhand x 1,75 px, 0,76, 0,79; NeoForge x −0,91 bzw. 1,29 px, 0,76 bzw. 0,77, 0,81 bzw.
  0,79 (die Mitte wandert mit dem zufälligen Standard-Skin um 0,5 px: schmale Arme sitzen weiter innen); Abweichung vom
  Spiegelbild 0,30 px (das Modell liegt 0,5 Einheiten neben der Mitte).
- Nach dem Fix: Gruppe `planer:` (zwölf Schritte samt 612, dazu Aufräumen) auf Fabric und NeoForge je 13 von 13 PASS,
  Build beider Loader grün, `:common:test` 776 Tests grün (Stand Propwash 0.6.0-dev.11), Server-Rauchtest beider Loader
  PASS;
  `python3 tools/assets/style_generate.py --verify` findet am Modell keine Abweichung vom Generator.

## 29. Kein Budget: Propwashs Meldung bleibt, der Chat nennt die Welt-Regeln (AUTO24-1)

Befund AUTO24-1: Seit Propwash das Budget für Flüge ohne Pilot über die Welt-Regeln „Autonome Flüge je Spieler
gleichzeitig“ (0–8, Standard 1) und „Autonome Flüge gesamt“ (0–32, Standard 6) einstellt, schickt Propwash bei
`NO_BUDGET` selbst eine Meldung mit dem Wert in die Aktionsleiste. Das Add-on überschrieb sie im selben Tick mit dem
festen Text „kein Budget – es läuft schon ein Flug ohne Pilot (höchstens einer je Spieler)“ und schrieb ihn in den Chat;
bei Regel ≠ 1, vollem Gesamt-Budget und 0 war der falsch, die Anleitung nannte ebenfalls fest einen Flug je Spieler.

- Seit dem Fix: `NO_BUDGET` überschreibt die Aktionsleiste nicht mehr (`AutonomyStatus.overlay`, wie `NOT_ARMABLE` ohne
  eigenen Grund). Die Chat-Zeile bleibt und nennt beide Welt-Regeln, aber keine Zahl: Den Wert kennt das Add-on nicht
  (die API gibt nur `NO_BUDGET` zurück, `WorldRules.find` findet nur Regeln von Add-ons). Gegen Propwash 0.6.0-dev.11
  gibt es die Regeln und Propwashs Meldung noch nicht; dort bleibt die Aktionsleiste bei `NO_BUDGET` leer.
- JUnit `integration.autonomy.NoBudgetMessageTest`: Aktionsleiste, Text EN/DE mit beiden Regeln ohne feste Anzahl,
  Anleitung EN/DE (vor dem Fix 3 von 3 rot).
- Selbsttest 631 `welt-regeln: kein Budget – Aktionsleiste bleibt bei Propwash, Chat nennt die Welt-Regeln`
  (`dev/client/BudgetSteps`, gezielt `'-PcamdronesSelftestOnly=631'`, unter 1 min): die erste Tour hält ohne Pilot,
  die zweite bekommt `NO_BUDGET`; danach darf die Aktionsleiste nicht die Meldung des Add-ons zeigen, die Chat-Zeile muss
  beide Welt-Regeln und keine Zahl nennen. Vor dem Fix Fabric FAIL („Add-on überschreibt Propwashs Meldung in der
  Aktionsleiste: … at most one per player“).
- Stand 28.10. nach dem Rebase auf main: `:common:test` 810 Tests grün, Build beider Loader grün; Selbsttest
  `welt-regeln:` (5 Schritte mit 23 und 631) Fabric und NeoForge PASS; Server-Rauchtest Fabric PASS.

## 30. Missions-Timer bis zum letzten Wegpunkt (Owner-Playtest 28.10., OWNC6-2)

Befund OWNC6-2: Der Timer neben „WP n/N“ zählte nicht im Sekundentakt herunter (mal schneller, mal langsamer) und
zeigte am letzten Wegpunkt noch etwa 10 s. Ursache: `client.hud.MissionEta` rechnete jede Sekunde neu mit dem Modell der
Planer-Vorschau (`FlightTimeEstimate.mission`: Reisetempo ohne Kurventempo, ohne die Bremskurve des Folgers, angefangenes
Warten voll gezählt), hielt den Wert eine Sekunde und sprang dann auf den neuen; dazu zählte er Rückflug und Landung
mit. Entscheidung der Steuerung: Restzeit bis zum letzten Wegpunkt (ohne Rückflug und Landung), in echten Sekunden
gleichmäßig, sanft nachgeführt (keine Sprünge über 1 s außer bei Planänderung), am letzten Wegpunkt 0:00.

- **Uhr** `logic.estimate.MissionClock` (statt `MissionEta`, aufgerufen von `CamOsdSource` mit Zustand, Index,
  Position, Spielzeit und „Drohne unter 1 m über dem Boden“):
  - Ziel alle 0,5 s neu: der `WaypointFollower` selbst fliegt die restlichen Punkte ab der aktuellen Position und
    Geschwindigkeit (aus den Positionen der letzten Ticks) gegen ein Modell der Regelung von Propwash für Flüge ohne Pilot
    (seit AUTO26-1 die Regelung des Server-Flugs selbst, siehe Nachtrag unten). Den Rest ab dem nächsten Punkt rechnet die
    Uhr einmal je Wegpunkt, danach nur noch das laufende Teilstück. Start vom Boden: zuerst der Aufstieg von Propwash auf
    die Höhe des Punkts.
    Wartezeiten zählen ab der Ankunft am Punkt (0,5 m, oder 1,0 m und vorbei), nach einer Pause wieder voll (der neue
    Folger wartet neu).
  - Anzeige: läuft in Spielsekunden (20 Ticks) herunter; weicht sie mehr als 0,5 s vom Ziel ab, zählt sie schneller oder
    langsamer (Zeitkonstante 8 s, zwischen 0 und 2 s je Sekunde; vor AUTO26-1 0,2 s und 3 s), nie nach oben. In der Pause steht sie. Neue Mission,
    anderer Startpunkt oder ein übersprungener Punkt (Planänderung) und Lücken über 2 s setzen sie auf das Ziel.
  - Ab der Ankunft am letzten Wegpunkt 0:00, auch beim Warten dort, beim Rückflug und bei der Landung. Schleifen haben
    keinen Timer.
- JUnit `logic.MissionTimerTest`: sechs Missionen im Punktmassen-Modell (`NavLogic`), Tick für Tick gegen die Ankunft
  des Folgers am letzten Punkt: Tour eckig mit Teilstücken von 3 bis 40 m und 2 bis 12 m/s, 2 s Warten, Ende zum Start
  (ohne und mit Wind), Kino vom Boden mit Landung, Tour und Compact weich im Zickzack mit 3 bis 10,5 m/s (Compact mit
  Wind), Pause 5 s. Geprüft: nie steigend, kein Schritt über 1 s (die Ankunft im fremden Modell höchstens 1,5 s), höchstens
  2 s je Sekunde, ab der Ankunft 0:00, in der Pause stehend, Abweichung höchstens 2 s + 15 % der Restzeit (das
  Punktmassen-Modell fliegt rund 8 % schneller als Propwash). Dazu Einzelfälle: 0:00 am letzten Punkt und auf dem
  Rückflug, Warten zählt genau eine Sekunde je Sekunde, Pause hält, sanftes Nachführen, Planänderung, kein Timer bei
  Schleife, IDLE und DONE. Vorher 6 von 6 rot (Sprünge bis 2,33 s, bis 27 s daneben, Rückflug mitgezählt).
- JUnit `logic.MissionTimerReplayTest`: spielt die zwei aufgezeichneten Flüge aus 386 und 387 (Fabric,
  `common/src/test/resources/mission-timer/`, je Tick Index und Position am Client, Ankunft am Server) durch die Uhr:
  Abweichung höchstens 1 s, nie steigend, höchstens 2 s je Sekunde, ab der Ankunft 0:00.
- Selbsttest 386 und 387 (`'-PcamdronesSelftestOnly=386,387'`, etwa 3 min): Kino eckig vom Boden mit Ende zum Start
  (sechs Punkte, 3 bis 40 m, 2 bis 12 m/s, 2 s Warten) und Tour weich vom Boden mit Ende schweben (acht Punkte im Zickzack,
  3 bis 10,5 m/s), beide ohne Pilot bei Wind 1. Der Schritt ruft jeden Tick `CamOsdSource` wie das OSD auf und vergleicht
  den Timer mit der Ankunft am Server (Position höchstens 0,5 m vom letzten Punkt; zum Vergleich der Wechsel der Anzeige
  des Folgers): Abweichung höchstens 1 s gegen die Spielzeit und, wenn der Server in Echtzeit lief, gegen die Uhr; nie
  steigend, kein Schritt über 1 s, ab der Ankunft „0:00“ (Rückflug in 386). Ergebnis und Spur in
  `<loader>/runs/selftest-client/camdrones-mission-timer-386.csv` bzw. `-387.csv`.
- Stand 28.10.: `:common:test` 756 grün, Build beider Loader grün; Selbsttest `'-PcamdronesSelftestOnly=mission:,hud:'`
  auf Fabric und NeoForge je 25 von 25 PASS (24 Schritte und Aufräumen), darin 386 mit größter Abweichung +0,49 s bzw. +0,48 s und 387 mit −0,38 s
  bzw. −0,31 s, größter Schritt höchstens 0,12 s, Spielzeit gleich Echtzeit (Abweichung zur Uhr höchstens 0,48 s).
- Beim Merge in main am 28.10. von 381/382 auf 386/387 umnummeriert, weil 381–385 seit OWNC7-1 (§24) belegt sind.

**Nachtrag AUTO26-1 (Gegenprüfung G-OWNC6-2):** Bei einer unabhängigen Kino-Mission (11 Punkte, eckig, 2 bis 15 m/s, Ende
zum Start, 118 s) lag der Timer bis −2,7 s neben der Ankunft (Fabric und NeoForge), weil das Modell nur an 386/387
abgeglichen war und auch nur diese Flüge auf 1 s geprüft wurden. Ursache: Das Modell bildete die Regelung des Server-Flugs
nicht nach (Geschwindigkeitsregler 3,0/s statt 1,6/s, ohne Vorsteuerung und ohne den Widerstand der Rotoren, der linear mit
dem Tempo wächst) und war so beim Beschleunigen zu schnell und auf kurzen Stücken zu langsam; dazu flog jede neue Schätzung
ab der aktuellen Position statt auf der Linie vom vorigen Punkt (senkrechte Teilstücke wurden schräg) und ein Punkt mit
Wartezeit wurde in weichen Missionen ohne Halt durchflogen.

- `MissionClock` rechnet jetzt mit der Regelung, die Propwash für Flüge ohne Pilot nutzt: Geschwindigkeitsregler 1,6/s
  mit Integral 0,8/s (höchstens 1,5 m/s²), Vorsteuerung des Luftwiderstands, Neigung bis 35°, Positionshalten 0,9/s mit
  Bremsweg 2,5 m/s², Höhe 1,2/s, Steigregler 2,5/s mit Integral 1,2/s (nur bei Fehlern unter 1 m/s), Schub 0,3 g bis
  0,9 × Schub-Gewicht, Steigrate des Aufstiegs 1 + 0,6 × Schub-Gewicht (1,5 bis 4 m/s). Je Klasse Schub-Gewicht und
  Luftwiderstand der Presets (Werkbank-Analyse: Compact 2,93, Tour 3,50, Kino 3,47), dazu linearer und quadratischer
  Widerstand, Steig- und Sinkverlust und Trägheit der Höhe, im Schritt von 25 ms. Diese Werte sind an Flügen ohne Pilot in
  der Physik-Simulation von Propwash angepasst (58 zufällige Missionen aller Klassen, eckig und weich, Teilstücke 0 bis
  60 m, 1 bis 15 m/s, Warten, Wind 0 und 1, dazu die Missionen aus 386/387 und der Gegenprüfung) und an 60 weiteren
  zufälligen Missionen geprüft; die Flüge aus dem Spiel dienten nur zur Kontrolle.
- Jede Schätzung beginnt auf der Linie vom vorigen Wegpunkt (wie der Folger am Server), die Integratoren im Gleichgewicht
  zur aktuellen Geschwindigkeit; ein Punkt mit Wartezeit bremst auch in weichen Missionen bis zum Halt. Der Aufstieg vom
  Boden zählt, bis Propwash ihn beendet (Höhe auf 0,3 m und unter 0,5 m/s); liegt der erste Durchgang durch die Zielhöhe
  knapp an dieser Grenze (unter 0,15 m), mittelt die Uhr zwischen beiden möglichen Enden, weil Böen entscheiden, ob er
  reicht. Springt die Spielzeit am Client um bis zu 1 s zurück (Zeitabgleich mit dem Server), beginnt die Uhr nicht neu.
- JUnit `logic.MissionTimerReplayTest` spielt neun Flugspuren aus dem Spiel nach (Grenze 1 s): 386/387 Fabric (wie bisher)
  und NeoForge, die Gegenprüfungs-Spuren Kino eckig 11 Punkte (Fabric −2,72 s, NeoForge −2,66 s vorher), Compact weich
  landen und Kino weich schweben, dazu Selbsttest 389 mit einem Rücksprung der Spielzeit um zwei Ticks (vorher stieg der
  Timer um 1,8 s). `logic.MissionTimerTest`: Die Punktmasse bildet die Halteregelung mit Pilot nach und kommt am letzten
  Punkt bis 2,8 s früher an als ein Server-Flug; Grenze dort 2,5 s + 15 %, Schritt an der Ankunft bis 2,5 s. Die Grenze
  von 1 s prüfen die Spuren und die Selbsttests.
- Selbsttests 388 bis 391 (`'-PcamdronesSelftestOnly=386,387,388,389,390,391'`, etwa 9 min) mit derselben Prüfung wie
  386/387: 388 Kino eckig, 11 Punkte, 2 bis 15 m/s, 3 s Warten, Ende zum Start (die Mission der Gegenprüfung); 389 Compact
  weich, 10 Punkte, Ende landen; 390 Kino weich, 8 bis 15 m/s, Ende schweben; 391 Tour eckig, neun Punkte, 2,5 s Warten,
  10 m Sinkflug, Ende landen (neu, nirgends zum Abgleich verwendet). Spuren in
  `<loader>/runs/selftest-client/camdrones-mission-timer-<nr>.csv`.
- Grenze: Bei Tour und Compact mit einem Aufstieg vom Boden auf 16 m und mehr überschwingt die Drohne um 0,3 bis 0,5 m;
  ob Propwash den Aufstieg beim ersten Durchgang beendet oder erst rund 1,7 s später, entscheiden Böen. Die Uhr mittelt
  beide Enden, bis sie den Ausgang sieht; in den ersten etwa 8 s kann sie dann bis 1,3 s daneben liegen. In der
  Simulation waren so 8 der 128 Missionen über 1 s (alle mit hohem Aufstieg), dazu eine Mission aus vier senkrecht
  übereinander liegenden Punkten (1,9 s); vorher 64 von 128 (bis 5,1 s). Kino-Drohnen überschwingen weniger und sind nicht
  betroffen.
- Stand 28.10. (AUTO26-1): `:common:test` 834 grün, Build beider Loader grün; Selbsttest 386 bis 391 auf Fabric und
  NeoForge je 7 von 7 PASS, größte Abweichung 386 −0,30/−0,28 s, 387 +0,32/+0,42 s, 388 +0,78/+0,52 s (vorher −2,72 s),
  389 −0,25/−0,21 s, 390 +0,18/+0,20 s, 391 +0,68/+0,55 s (Fabric/NeoForge), gegen die Uhr höchstens 0,82 s, größter Schritt
  höchstens 0,11 s; Selbsttest `'-PcamdronesSelftestOnly=mission:,hud:,osd:'` auf Fabric und NeoForge je 32 von 32 PASS
  (darin 386 bis 391 mit höchstens +0,63 bzw. +0,79 s); Server-Rauchtest beider Loader PASS.

**Nachtrag MCLOCK3-1 (Gegenprüfung G-AUTO26-1):** Bei Compact und Tour mit einem Aufstieg vom Boden ab 20 m lag der Timer
in 9 von 9 Läufen 1,06 bis 1,57 s neben der Ankunft und bis 16,6 s lang über 1 s; die Grenze oben war zu knapp
beschrieben. Ursache: Am Ende des Aufstiegs bremst die Server-Flugsteuerung weicher als das Modell (der Steigverlust sinkt
mit dem Schub, der Schub fällt langsamer, als er steigt). Wenn die Steigrate unter 0,5 m/s fällt, steht die Drohne rund
0,1 m (Compact) bzw. 0,04 m (Tour) höher als im Modell. Ab etwa 20 m (Compact) bzw. 18 m (Tour) liegt sie dann über der
Toleranz von 0,3 m, und Propwash beendet den Aufstieg erst nach dem Zurücksinken, 1,5 bis 2,3 s später; das Modell blieb
darunter und rechnete mit dem ersten Durchgang. Dazu rechnete die Uhr den Aufstieg alle 0,5 s aus dem Zwischenstand neu,
ohne den Integrator der Steigregelung zu kennen, und fiel so während des Steigens bis 2,4 s unter die Restzeit.

- Der Aufstieg vom Boden hat eigene Kennwerte: Schub steigt in 0,024 s und fällt in 0,082 s, der Steigverlust wächst mit
  (Schub / Schub im Steigflug)^0,485, Antwort beim Bremsen unter 1 g Compact 0,744, Tour 0,949, Kino 0,868. Angepasst an
  Aufstiegen von 2 bis 30 m aller drei Klassen mit der Server-Flugsteuerung und Physik von Propwash ohne Wind: Höhe beim
  Unterschreiten von 0,5 m/s auf etwa 0,01 m, Zeitpunkt auf 0,05 s, Dauer des zweiten Durchgangs auf 0,1 s. Flüge, die
  in der Luft beginnen, und alle Teilstücke nach dem Aufstieg rechnen unverändert.
- Grenzbereich: Wie hoch die Drohne beim Bremsen steht, streut mit Böen (Wind 1,2 m/s: 0,02 bis 0,05 m). Compact etwa
  17 bis 21 m, Tour 16 bis 19 m und Kino 26 bis 30 m liegen deshalb mal unter, mal über der Toleranz (je 16 Flüge in der
  Simulation: Compact 19 und 20 m je 9 mit zweitem Durchgang, Tour 17 m 9, Kino 26 bis 30 m 2 bis 4). Die Uhr gewichtet
  den zweiten Durchgang nach der Höhe über der Toleranz beim ersten Halt: sicher ab 0,035 m darüber, 65 % bei 0,02 m,
  50 % an der Grenze, 35 % bis 0,05 m darunter, keiner ab 0,065 m darunter (Dauer × 1,05, aus dem Weiterflug nach einem
  Halt in der Toleranz × 1,25).
- Während des Aufstiegs folgt die Schätzung dem beim Start vorausberechneten Aufstieg nach der erreichten Höhe, statt ihn
  aus jedem Zwischenstand neu zu rechnen. Erst ab 0,3 m über dem Ziel rechnet sie vom aktuellen Stand; dafür führt sie den
  Integrator der Steigregelung aus Höhe und Steigrate mit. Eine Pause verwirft den vorausberechneten Aufstieg.
- JUnit `logic.MissionTimerReplayTest` spielt sieben weitere Spuren aus G-AUTO26-1 nach (Grenze 1 s), vorher → nachher:
  Compact weich 20 m Ende zum Start (Fabric) −1,41 → −0,71 s, Compact weich 25 m landen (NeoForge) −1,57 → −0,19 s, Tour
  weich 24 m landen (NeoForge) −1,56 → −0,22 s, Tour eckig 30 m schweben −1,06 → +0,29 s (Fabric) und −1,44 → −0,20 s
  (NeoForge), dazu Compact eckig 16 m (+0,45 s) und Tour eckig 18 m Ende zum Start (−0,14 s) knapp unter bzw. an der
  Grenze (Zahlen mit AUTO33-1 auf den Stand des fertigen Fixes berichtigt). Nach der Ankunft prüft das Replay wie der Selbsttest: sofort „0:00“, ab 10 Ticks genau 0 (Spuren mit Ende
  schweben oder landen enden mit dem Ankunftstick, an dem die Position am Client noch bis 0,51 m vom Punkt liegt).
- Selbsttests 392 bis 395 (`'-PcamdronesSelftestOnly=392,393,394,395'`, etwa 4 min) mit derselben Prüfung wie 386 bis 391,
  bewusst außerhalb des Grenzbereichs: 392 Compact weich, Aufstieg 25 m, Ende landen; 393 Tour weich, 24 m, Ende landen;
  394 Tour eckig, 30 m, Ende schweben; 395 Compact eckig, 30 m, Ende schweben (vier Punkte wie die Sonden der
  Gegenprüfung). 392 prüft seit AUTO35-1 im Aufstieg mit eigener Grenze, siehe Nachtrag AUTO35-1.
- Grenze: Im Grenzbereich kann der Timer weiter bis etwa 1,3 s danebenliegen, wenn eine Böe gegen die gewichtete Erwartung
  entscheidet. In der Simulation (669 Flüge mit Aufstieg 2 bis 30 m, Wind 0, 0,6, 1 und 1,2 m/s) lagen 33 über 1 s,
  alle im Grenzbereich, vorher 170 (bis 1,8 s); die 120 Missionen aus Abgleich und Prüfung von AUTO26-1 vorher 9, jetzt 6.
  Kino zeigt bei Aufstiegen ab 24 m während des Aufstiegs rund 0,4 s mehr an, weil der zweite Durchgang dort mit 35 %
  zählt.
- Läuft beim Selbsttest eine Funke oder ein Gamepad am Rechner, fragt Propwash nach der Einrichtung und pausiert das Spiel;
  die Spielzeit steht dann, und die Flug-Schritte scheitern mit „kommt nicht am letzten Wegpunkt an“. Abhilfe: die GUID des
  Geräts in `<loader>/runs/selftest-client/config/propwash/input.json` unter `neverAsk` eintragen.
- Stand 29.10. (MCLOCK3-1): `:common:test` 849 grün, Build beider Loader grün; Selbsttest
  `'-PcamdronesSelftestOnly=mission:,hud:,osd:'` auf Fabric und NeoForge je 36 von 36 PASS, größte Abweichung
  (Fabric/NeoForge) 386 +0,51/+0,41 s, 387 +0,20/−0,19 s, 388 +0,51/+0,50 s, 389 −0,09/+0,10 s, 390 +0,19/−0,17 s,
  391 +0,48/+0,58 s, 392 −0,39/−0,54 s, 393 −0,26/+0,10 s, 394 −0,22/−0,29 s, 395 −0,16/+0,14 s, gegen die Uhr höchstens
  0,59 s, größter Schritt höchstens 0,35 s; die Läufe der Sonden 997 und 1005 bis 1007 aus G-AUTO26-1 im Replay
  höchstens −0,86 s (vorher −1,57 s; berichtigt mit AUTO33-1); Server-Rauchtest beider Loader PASS.

**Nachtrag AUTO33-1 (Gegenprüfung G-MCLOCK3-1):** Bei Compact mit einem Aufstieg vom Boden auf 20 bis 21 m lag der Timer auf
Fabric in 4 von 6 Läufen 1,17 bis 1,49 s neben der Ankunft (zu lang) und bis 20,1 s lang über 1 s; die Grenze oben („bis
etwa 1,3 s“) stimmte nicht. Ursachen:

- Dieselbe Mission endet je nach Böe mit oder ohne zweiten Durchgang des Aufstiegs, die beiden Ausgänge liegen 1,3 bis 2,1 s
  auseinander (Sonde 1012, Compact eckig 20 m: 40,2 und 40,5 s ohne, 42,3 s mit). Die Uhr zählte den zweiten Durchgang bei
  20 m mit 61 %, bei 21 m mit 81 %; im Spiel kam er bei 21 m in 3 von 5 Läufen.
- Am Ende des Aufstiegs rechnete die Uhr aus dem aktuellen Stand neu. Nach einem frühen Ende schwingt die Drohne im
  Weiterflug noch bis 0,5 m über die Zielhöhe; das hielt die Uhr für den zweiten Durchgang.
- Nach dem Aufstieg holte die Anzeige den Unterschied nur mit der Zeitkonstante 8 s auf.

Seit dem Fix (`logic.estimate.MissionClock`):

- Im Grenzbereich (Höhe beim ersten Halt 0,035 m über bis 0,04 m unter der Toleranz) zählt der zweite Durchgang zur
  Hälfte, sicher erst ab 0,045 m darüber, keiner ab 0,065 m darunter (Compact etwa 19 bis 22,5 m, Tour 17 bis 18 m, Kino
  ab etwa 24 m zur Hälfte). Die Mitte zwischen beiden Ausgängen hält die größte Abweichung vor der Entscheidung klein.
- Am Ende des Aufstiegs (Steigrate unter 0,5 m/s nahe der Zielhöhe) steht die Schätzung, bis der Ausgang feststeht.
  Zweiter Durchgang heißt: Die Drohne war mehr als 0,35 m über dem Ziel, ist zurück in der Toleranz und steht noch über dem
  Startpunkt (unter 0,3 m daneben). Sonst gilt der erste: Wechsel des Wegpunkts, 0,5 m Abstand vom Startpunkt nahe der
  Zielhöhe (sonst 1 m) oder Halt in der Toleranz ohne vorheriges Überschwingen.
- Steht der Ausgang fest, läuft die Anzeige mit 2 s je Sekunde oder steht, bis sie auf der beim Start vorausberechneten
  Restzeit dieses Ausgangs liegt; danach wie bisher. Das gilt bei jedem Aufstieg vom Boden, auch wenn der Ausgang der
  Erwartung widerspricht (Selbsttest 392 endete in einem Lauf auf 25 m ohne zweiten Durchgang).
- Proben, in denen die Position am Client während der Bewegung gleich bleibt (Ruckler, danach wiederholte Ticks), ändern
  Geschwindigkeit und Steig-Integrator nicht; sonst hielt die Uhr einen Ruckler kurz vor dem Ziel für das Ende des
  Aufstiegs.
- Grenze: Bis zum Ende des Aufstiegs kann die Uhr nicht wissen, welcher Ausgang kommt. Liegen beide mehr als 2 s
  auseinander, ist einer von ihnen zwangsläufig über 1 s daneben. Im Grenzbereich liegt der Timer deshalb während des
  Aufstiegs bis etwa 1,3 s daneben (gemessen bis 1,26 s), ab etwa 1 s nach dem Ende des Aufstiegs unter 1 s. Kommt der
  unerwartete Ausgang außerhalb des Grenzbereichs, gilt das ebenso mit der ganzen Streuung (392 auf 25 m: +1,76 s bis zum
  Ende des Aufstiegs, danach +0,46 s). Ganz unter 1 s käme der Timer nur, wenn der Flug selbst nicht mehr zwei Ausgänge
  hätte.
- JUnit `logic.MissionTimerReplayTest` mit sieben weiteren Spuren aus dem Spiel, vorher → nachher (bis 1,5 s nach dem
  Aufstieg / danach): Compact eckig 20 m Ende schweben ohne zweiten Durchgang, Fabric +1,18 → +0,96 / +0,17 s (Grenze 1 s)
  und zweiter Lauf +1,49 → +1,26 / +0,47 s, mit zweitem Durchgang auf NeoForge −0,61 → −0,79 / −0,15 s (beide mit Grenze
  1,3 s bis 1,5 s nach dem Aufstieg, danach 1 s); Compact weich 21 m Ende landen ohne zweiten Durchgang +1,42 → +0,90 /
  +0,07 s, mit zweitem auf NeoForge −0,34 → −0,85 / +0,11 s (Grenze 1 s); Compact weich 20 m Ende landen mit zweitem
  Durchgang auf NeoForge −0,94 → −1,11 / −0,35 s (Grenze 1,3 s); Compact weich 25 m Ende landen ohne zweiten Durchgang
  (Selbsttest 392 dieses Fixes, Grenze 1,8 s bis 1,5 s nach dem Aufstieg) nach dem Aufstieg +1,60 → +0,46 s. Den Beginn
  des Weiterflugs liest das Replay aus der Spur (Wegpunktwechsel oder 0,25 m vom Startpunkt). Die bisherigen Spuren bleiben
  unter 1 s; geändert haben sich Compact weich 20 m Ende zum Start −0,71 → −0,89 s und Tour eckig 18 m −0,14 → +0,51 s.
- Selbsttest 396 `hud: Missions-Timer im Grenzbereich des Aufstiegs – Compact, eckig, Aufstieg 20 m vom Boden, Ende
  schweben` (die Mission der Sonde 1012, etwa 1 min): bis 1,5 s nach dem Ende des Aufstiegs am Server (erste Anzeige des
  Folgers) höchstens 1,5 s, danach 1 s, sonst wie 386 bis 395. Die Grenze von 1,5 s lässt Platz für die Streuung der
  Ausgänge; ohne den Fix bleibt ein Lauf ohne zweiten Durchgang bis 20 s über 1 s. Seit AUTO38-1 prüft 396 den Aufstieg
  wie 392, siehe Nachtrag AUTO38-1.
- Alle 106 Spuren aus G-AUTO26-1, G-MCLOCK3-1 und F-MCLOCK3-1 durch die Uhr gespielt: vorher 4 Läufe über 1 s (bis 1,49 s,
  zusammen 69,2 s), nachher 4 (bis 1,26 s, zusammen 31,5 s, nur während des Aufstiegs); drei davon lagen vorher knapp unter
  1 s (−0,86, −0,94 und −0,86 s, alle mit zweitem Durchgang; einer jetzt nur 0,05 s lang über 1 s). Die beiden ersten Spuren dieses Fixes (392 und 396 auf Fabric, beide
  ohne zweiten Durchgang) vorher 19,0 bzw. 18,6 s über 1 s, nachher 12,6 bzw. 8,9 s.
- Stand 29.10. (AUTO33-1): `:common:test` 855 grün, Build beider Loader grün; Selbsttest
  `'-PcamdronesSelftestOnly=mission:,hud:,osd:'` auf Fabric und NeoForge je 37 von 37 PASS, größte Abweichung
  (Fabric/NeoForge) 386 +0,37/+0,36 s, 387 +0,20/+0,30 s, 388 +0,82/+0,76 s, 389 +0,05/+0,25 s, 390 +0,19/+0,23 s,
  391 −0,24/+0,33 s, 392 −0,27/−0,27 s, 393 +0,08/+0,28 s, 394 +0,36/+0,31 s, 395 −0,22/−0,16 s, 396 −1,04/−0,74 s (beide
  mit zweitem Durchgang, ab 1,5 s nach dem Aufstieg −0,26/−0,11 s), größter Schritt höchstens 0,15 s; Server-Rauchtest
  beider Loader PASS. Ein erster Lauf auf Fabric mit angeschlossener Funke (Propwash nahm sie als Eingabegerät, 13
  Schritte mit Pilot scheiterten an „lässt sich nicht scharf schalten“ bzw. „steigt nicht, Gas 0,00“) brachte 396 ohne
  zweiten Durchgang (+1,11 s, danach unter 1 s, PASS) und 392 auf 25 m ohne zweiten Durchgang (+1,76 s, damals FAIL; seit
  AUTO35-1 gilt für 392 während des Aufstiegs die Grenze 1,8 s, siehe Nachtrag AUTO35-1). Abhilfe für die Funke: in
  `<loader>/runs/selftest-client/config/propwash/input.json` zusätzlich `"selectedGuid": "keyboard"`.

**Nachtrag AUTO35-1 (Entscheidung des Owners nach G-AUTO33-1):** Endet der lange weiche Aufstieg (Compact, 25 m vom Boden)
ohne den als sicher geltenden zweiten Durchgang, darf der Missions-Timer während des Aufstiegs bis 1,8 s neben der
Ankunft liegen; außerhalb des Aufstiegs bleibt die Vorgabe 1 s. `logic.estimate.MissionClock` bleibt unverändert, geändert
ist nur die Prüfung von Selbsttest 392 `hud: Missions-Timer im Aufstieg höchstens 1,8 s, danach 1 s neben der Ankunft –
Compact, weich, Aufstieg 25 m vom Boden, Ende landen`:

- Bis 1,5 s nach dem Ende des Aufstiegs höchstens 1,8 s, danach 1 s gegen die Spielzeit, wie die Replay-Grenze der Spur
  `compact-smooth-climb25-land-first-pass.csv`. Das Ende des Aufstiegs liest 392 wie das Replay aus der Spur am Client
  (Wegpunktwechsel oder 0,25 m vom Startpunkt), nicht wie 396 bis AUTO38-1 aus der ersten Anzeige des Folgers am Server. Der Server
  meldet den Weiterflug rund 1 s früher; die Uhr erkennt den ersten Durchgang dort aber erst 0,5 m neben dem Startpunkt
  und holt danach mit 2 s je Sekunde auf. 1,5 s nach der Meldung am Server läge sie noch bis +1,7 s daneben (bei 1 s
  Vorlauf der Meldung +1,46 s).
- Gegen die Uhr prüft 392 erst ab 1,5 s nach dem Ende des Aufstiegs, mit 1 s. In 229 aufgezeichneten Läufen lag die
  größte Abweichung gegen die Uhr wegen Rucklern am Client im Mittel 0,03 s, in 95 % bis 0,12 s über der gegen die
  Spielzeit und in 103 Läufen mehr als 0,04 s darüber; im Lauf mit +1,76 s waren es 1,84 s. Eine Grenze von 1,8 s gegen
  die Uhr während des Aufstiegs wäre dort also fast jedes zweite Mal rot.
- 386 bis 391, 393 bis 395 und 396 prüfen unverändert (396 seit AUTO38-1 wie 392, siehe Nachtrag AUTO38-1).
- Nachgespielt mit der Spur des Laufs ohne zweiten Durchgang (Positionen und Uhrzeiten aus dem Spiel, Anzeige aus dem
  MissionClock von heute): bisherige Prüfung FAIL (+1,76 s bei Grenze 1 s, gegen die Uhr 1,84 s); nur die Grenze 1,8 s
  mit dem Ende am Server wie bei 396 ebenfalls FAIL (ab 1,5 s danach +0,96 bis +1,71 s je nach Zeitpunkt der Meldung,
  gegen die Uhr 1,84 s); neue Prüfung PASS (im Aufstieg +1,76 s, ab 1,5 s nach dem Weiterflug +0,46 s, gegen die Uhr
  0,51 s). Die fünf Läufe von 392 mit zweitem Durchgang seit AUTO33-1 bleiben PASS (höchstens −0,27 s).
- Stand 30.10. (AUTO35-1): `:common:test` 855 grün, Build beider Loader grün; Selbsttest
  `'-PcamdronesSelftestOnly=mission:,hud:,osd:'` auf Fabric und NeoForge je 37 von 37 PASS, größte Abweichung
  (Fabric/NeoForge) 386 +0,46/+0,51 s, 387 +0,30/−0,11 s, 388 +0,51/+0,78 s, 389 −0,07/−0,06 s, 390 +0,18/+0,18 s,
  391 +0,33/+0,28 s, 392 −0,22/−0,44 s (beide mit zweitem Durchgang; Ende am Server 12,4/12,8 s, Weiterflug am Client
  13,5/13,8 s), 393 +0,09/−0,10 s, 394 +0,21/+0,36 s, 395 +0,44/−0,16 s, 396 −0,79/−0,59 s, gegen die Uhr höchstens
  0,83 s, größter Schritt höchstens 0,16 s; Server-Rauchtest beider Loader PASS.

**Nachtrag AUTO38-1 (Steuerung 30.10. nach G-AUTO35-1):** Selbsttest 396 prüft den Aufstieg genauso wie 392 seit
AUTO35-1. `logic.estimate.MissionClock` bleibt unverändert, Name und Mission von 396 ebenso.

- Bisher nahm 396 das Ende des Aufstiegs von der ersten Anzeige des Folgers am Server und prüfte bis 1,5 s danach mit
  1,5 s, danach mit 1 s, gegen die Uhr ebenso. Endet der Aufstieg ohne zweiten Durchgang, kommt diese Meldung rund 1 s vor
  dem Weiterflug am Client; die Uhr erkennt den ersten Durchgang erst 0,5 m neben dem Startpunkt und holt danach auf. Die
  Prüfung mit 1 s begann so mitten im Aufholen (G-AUTO35-1 auf NeoForge: Ende am Server 8,75 s, Weiterflug am Client
  9,75 s, bei 10,25 s +0,91 s gegen die Spielzeit und 1,01 s gegen die Uhr, FAIL; 1 von 5 Läufen).
- Jetzt wie 392: Ende des Aufstiegs am Client (Wegpunktwechsel oder 0,25 m vom Startpunkt), bis 1,5 s danach höchstens
  1,8 s gegen die Spielzeit, danach 1 s; gegen die Uhr erst ab 1,5 s nach dem Weiterflug, mit 1 s (Gründe wie im Nachtrag
  AUTO35-1). Die Logzeile nennt Ende am Server und Weiterflug am Client. Die bisherige Prüfart von 396 (1,5 s, Uhr auch
  während des Aufstiegs) entfällt; kein anderer Schritt nutzte sie.
- Nachgespielt mit allen elf aufgezeichneten Läufen von 396 seit AUTO33-1 (Anzeige aus dem Spiel; mit der Anzeige neu aus
  dem MissionClock von heute dieselben Urteile): der Lauf aus G-AUTO35-1 vorher FAIL (ab 1,5 s nach dem Ende am Server
  gegen die Uhr 1,01 s), nachher PASS (im Aufstieg +1,16 s, ab 1,5 s nach dem Weiterflug +0,38 s, gegen die Uhr 0,48 s);
  die zehn übrigen (zwei davon ebenfalls ohne zweiten Durchgang) vorher und nachher PASS.
- Im Spiel brachte ein Nachlauf auf NeoForge den Ausgang ohne zweiten Durchgang (Ende am Server 8,8 s, Weiterflug am
  Client 9,9 s): PASS mit +1,10 s im Aufstieg, ab 1,5 s nach dem Weiterflug +0,33 s, gegen die Uhr 0,38 s. Die bisherige
  Prüfung hätte hier mit +0,85 s und gegen die Uhr 0,90 s nur 0,1 s Abstand zur Grenze gehabt. In allen 16 Läufen kam der
  Weiterflug am Client 0,8 bis 1,1 s nach der Meldung am Server.
- Stand 30.10. (AUTO38-1): `:common:test` 855 grün, Build beider Loader grün; Selbsttest
  `'-PcamdronesSelftestOnly=mission:,hud:,osd:'` auf Fabric und NeoForge je 37 von 37 PASS, größte Abweichung
  (Fabric/NeoForge) 386 +0,47/+0,46 s, 387 −0,23/+0,25 s, 388 +0,71/+0,66 s, 389 +0,06/−0,04 s, 390 +0,25/+0,21 s,
  391 −0,34/+0,43 s, 392 −0,18/−0,44 s (beide mit zweitem Durchgang), 393 +0,19/−0,37 s, 394 +0,31/+0,44 s,
  395 +0,24/+0,14 s, 396 −0,94/−0,59 s (beide mit zweitem Durchgang; Ende am Server 10,7/10,5 s, Weiterflug am Client
  11,7/11,4 s, danach −0,20/+0,20 s), gegen die Uhr höchstens 0,91 s, größter Schritt höchstens 0,17 s; drei Nachläufe
  von 396 auf NeoForge PASS (+0,40, +1,10 und −0,65 s); Server-Rauchtest beider Loader PASS.

## 31. Gimbal im Livebild ohne 20-Hz-Stufen (Owner-Playtest 28.10., OWNC6-1)

Befund OWNC6-1: In der Aufnahme einer Mission ohne Pilot (Kino, 60 Bilder/s) ruckelte die Gimbal-Kamera wie leichtes
Rubber-Banding. Die Bildverschiebung (Phasenkorrelation von Bild zu Bild) war waagerecht glatt, senkrecht sprang sie in
jedem dritten Bild (20 Hz) und lief dazwischen zurück. Ursache: `GimbalScenes.scene` rechnete Missions-Neigung, Point of
Interest und Follow-me-Ziel aus der Tick-Position der Drohne (`getX/Y/Z`), Propwash zeichnet die Kamera aber an der mit
`partialTick` interpolierten Position. Die Soll-Neigung sprang so einmal je Tick, und das Gimbal-Modell holt einen
Neigungssprung im nächsten Bild nach (die Neigung ist nur im Tempo begrenzt, das Gieren folgt dagegen weich, deshalb war
waagerecht nichts zu sehen). Zwischen den Ticks wanderte die Kamera weiter, ohne nachzuneigen. Zweitens kam die Neigung
eines Kameramanns oder des Piloten bei Zuschauern nur alle 100–150 ms über die Drohnen-Daten an und sprang dort ebenso.

Seit dem Fix:

- `GimbalScenes.scene` nimmt die Position der Drohne und des Follow-me-Ziels mit dem `partialTick` des Bildes
  (`Entity#getPosition`, `DeltaTracker#getGameTimeDeltaPartialTick(true)` wie Propwashs Livebild). Das gilt für Kamera
  (`orient`), Gimbal-Kopf (`modelPose`) und die Neigung im OSD.
- Neigung von Hand, die über die Drohnen-Daten kommt (Kameramann, Pilot bei Zuschauern), gleitet in 150 ms linear zum
  neuen Wert (`client.gimbal.model.TiltGlide`, 1,5 × Sendetakt von `SendGate`, überbrückt also einen Abstand von
  150 ms, wenn der Server einen Tick später verteilt). Wer selbst steuert, sieht seine Neigung sofort; automatische Ziele
  gleiten nicht, sie sind schon pro Bild glatt.

Tests:

- JUnit `client.gimbal.TiltGlideTest`: ohne Gleiten stehen bei 60 Bildern/s und Werten alle 100 ms fünf von sechs
  Bildern, mit Gleiten bewegt sich jedes Bild gleichmäßig (1° je 100 ms, Schwankung unter 5 %); auch bei Abständen von
  88–114 ms oder abwechselnd 100 und 150 ms kein stehendes Bild und kein Rücklauf; ein einzelner Wert ist nach 150 ms
  da, ein neuer Wert während des Gleitens startet dort, wo die Kamera gerade ist; nur Neigung von Hand, die nicht selbst
  gesteuert wird, gleitet.
- Selbsttest 625 `gimbal: Livebild der Kino in der Mission ohne Pilot, Neigung und Schwenk ohne 20-Hz-Stufen`
  (`dev/client/GimbalSmoothSteps`, etwa 35 s): Kino mit Mission ohne Pilot, Strecke 1 neigt der Gimbal auf 24 m von 0°
  auf −60°, Strecke 2 schwenkt und neigt er auf einen Point of Interest, während die Drohne 4 m steigt; Aufnahme von
  Punkt 1 bis 3, der Besitzer schaut im Livebild zu. Ein HUD-Element des Selbsttests misst in jedem Bild Lage und Ort der
  Kamera (`mainCamera`) mit Spielzeit und `partialTick`. Verlangt je Strecke (ab 1 s nach dem Wechsel): die Neigung
  läuft zwischen den Ticks mindestens halb so schnell wie über die Tickgrenze (20-Hz-Stufen ergeben fast 0 %), und das
  Bild des Ziels (Punkt unter dem Wegpunkt bzw. Point of Interest) läuft in höchstens 25 % der Ticks zwischen den Ticks
  gegen die Bewegung über die Tickgrenze zurück; gezählt werden nur Ticks, in denen es je mehr als 0,02° wandert (etwa
  0,6 Pixel bei 1080 Zeilen). Dazu stehen im Ergebnis Bilder/s, der Anteil stehender Bilder, der größte Schritt (am Ende
  einer Strecke dreht der Gimbal mit 90°/s auf das nächste Ziel), Gieren und Höhe der Kamera (Höhe beim Steigflug
  ebenfalls mindestens 50 %). Vorher auf Fabric FAIL: Neigung zwischen den Ticks 1 % bzw. 2 % der Bewegung über die
  Tickgrenze, Rücklauf 66 % bzw. 98 %, Neigung steht in 85 % bzw. 83 % der Bilder (Rücklauf noch mit Schwelle 0,004°
  gemessen). Nachher in drei Läufen auf Fabric 98–109 %, Rücklauf 0–3 %, in zwei Läufen auf NeoForge 101–105 %,
  Rücklauf 0–7 %, jeweils etwa 118 Bilder/s.
- Selbsttest 626 `gimbal: Livebild der Kino, Neigung vom Kameramann alle 100 ms ohne Stufen` (etwa 20 s): Mission mit
  freiem Gimbal, der Server setzt die Neigung wie ein Kameramann alle 2 Ticks 1° tiefer bis −60°. Die Werte kommen
  mitten im Tick an, verlangt wird deshalb, dass die Neigung in höchstens 20 % der Bilder steht (weniger als ein Zehntel
  des mittleren Schritts). Vorher Fabric FAIL: steht in 83 % der Bilder, größter Schritt 11,9-fach des Mittels;
  nachher auf Fabric und NeoForge in je zwei Läufen 0 %, größter Schritt höchstens 3,3-fach. Beitrittstest `crew`
  unverändert PASS auf beiden Loadern (die Neigung des simulierten Operators kommt beim Piloten gleitend an: −5,7°,
  −25,7°, −45,6°, −55,7° statt −8°, −28°, −48°, −56°).
- Dieselbe Messung wie im Befund an der Aufnahme von 625 (1080p60, Phasenkorrelation von Bild zu Bild, senkrecht, je
  Sekunde): vorher Vorzeichenwechsel 60–67 % und 20-Hz-Spitze 9- bis 21-mal über dem Median, in jedem dritten Bild
  ein Sprung (Faltung über drei Bilder etwa −12 / +0,6 / +0,8 Pixel); nachher Vorzeichenwechsel 0–22 %, 20-Hz-Spitze
  0,7- bis 4,4-fach, Faltung gleichmäßig (etwa −3,5 / −3,0 / −3,4 Pixel).
- Beim Merge in main am 28.10. von 621/622 auf 625/626 umnummeriert, weil 621–623 seit OWNC6-3 (§23) und 624 seit
  AUTO23-1 (§27) belegt sind.

## 32. Gimbal-Ausgleich im Handflug ohne Nachziehen (Owner-Playtest 28.10., OWNC7B-1)

Befund OWNC7B-1: Nach F-OWNC6-1 laggte noch die Ausgleichsbewegung des Gimbals, wenn die Drohne im Handflug neigt und
rollt. Messung im Selbsttest 627 (unten): Neigung und Rollen gleicht der Gimbal schon in jedem Bild aus (Horizont
0,00°, Neigung 0,00°, Bild des Bezugspunkts zwischen den Ticks so schnell wie über die Tickgrenze, gemessen in echter
Zeit, weil Propwash den Flug des Piloten im Bildtakt rechnet), F-OWNC6-1 war dafür nicht nötig. Falsch war das
Gieren: Fliegt die Drohne schräg (vorwärts und seitwärts zugleich), kippt sie um eine waagerechte Achse schräg zur
Flugrichtung. Ihre Vorwärtsachse zeigt dann bis zu 10° neben die Gierrichtung, ohne dass die Drohne giert. Der Modus
*Folgen* folgte dieser Vorwärtsachse mit seiner Glättung (0,6 s): Das Bild schwenkte während des Schrägflugs um etwa
6° zur Seite und nach dem Bremsen über 1,5 s zurück.

Seit dem Fix:

- `GimbalPose.heading` ist die Gierrichtung der Drohne, also die Drehung des Rahmens um die Senkrechte
  (`2·atan2(q.y, q.w)`), wie Propwashs Lageregelung sie hält (`AttitudeController.twistHeading`). Neigen in jede
  Richtung ändert sie nicht. Die alte Richtung der Vorwärtsachse bleibt als `GimbalPose.forwardHeading`.
- `GimbalPose.step` gibt dem Gimbal-Modell aus der Propwash-API einen Rahmen, der um die Senkrechte so gedreht ist,
  dass seine Vorwärtsachse in diese Gierrichtung zeigt (`GimbalPose.steady`). Nick- und Rollwinkel bleiben gleich, die
  mechanischen Grenzen und der Modus FPV wirken also wie bisher. Das gilt für Kamera, Gimbal-Kopf, Aufnahmen und den
  Flashback-Kamerapfad (`GimbalPathRecorder` nutzt dieselben Aufrufe).
- `GimbalScenes.heading` (Drohnen-Daten) nimmt dieselbe Gierrichtung.

Tests:

- JUnit `client.gimbal.GimbalTiltYawTest` (8 Fälle): Gierrichtung bei 10–45° Neigung in sieben Richtungen und vier
  Gierwinkeln auf 0,01° genau; Folgen schwenkt beim schrägen Neigen und Aufrichten (45°) um weniger als 0,05° und hält
  Horizont und Neigung auf 0,01°; Folgen mit Schwenk (Kino) und Tour in sechs Neigerichtungen ebenso; echtes Gieren
  um 30° folgt die Kamera weiter (auf 0,5° nach 4 s); Sperre hält ihre Richtung; FPV rollt mit dem Rahmen und folgt
  dem Gieren; Kopf mal Rahmen ergibt die Weltlage. Vorher 5 von 8 FAIL (Kamera schwenkt 8,8°, Kino mit Schwenk 3,7°,
  FPV 36,0° statt 40°, Kopf 193,9° statt 200°, Gierrichtung 0,38° daneben schon bei 10°).
- `GimbalPoseTest.frameHeadingMatchesCompass`: Rahmen mit Neigen und Rollen zugleich prüft jetzt `forwardHeading`;
  für `heading` gilt ein Rahmen ohne Rollen (bei Gieren·Nicken·Rollen weicht die Drehung um die Senkrechte um 0,44°
  von der Vorwärtsachse ab).
- Selbsttest 627 `gimbal: Handflug der Kino in S, Ausgleich von Neigen und Rollen in jedem Bild ohne 20-Hz-Stufen`
  (`dev/client/GimbalHoldSteps`, etwa 40 s): Kino in S auf 8 m, Aufnahme mit Propwashs Taste (Pilotenbild auf dem
  Gimbal), dann je 2,5 s voll vorwärts, rechts und schräg vorwärts links, dazwischen je 2 s loslassen und bremsen.
  Ein HUD-Element misst in jedem Bild Lage und Ort der Kamera und das Bild eines Bezugspunkts 40 m voraus am Boden,
  dazu die Gierrichtung der Drohne. Verlangt je Abschnitt: Gimbal-Bild, Rahmen mindestens 20° geneigt (bei Knüppel),
  Horizont höchstens 0,5°, Neigung der Kamera Spanne höchstens 0,5°, Gieren der Kamera höchstens 1,5° mehr als das
  Gieren der Drohne, Bild des Bezugspunkts zwischen den Ticks mindestens 75 % so schnell wie über die Tickgrenze und
  höchstens 25 % Rücklauf. Messung als `camdrones-gimbal-hold.csv` im Spielordner. Vorher Fabric und NeoForge FAIL:
  im Schrägflug schwenkt die Kamera 6,06° bzw. 6,08°, die Drohne giert nur 3,36° bzw. 3,22°; beim Bremsen danach
  3,73° bzw. 3,70° bei 0,08° Gieren. Nachher Fabric (zwei Läufe) schräg 2,36° bzw. 2,04° bei 3,28° bzw. 3,40°
  echtem Gieren der Drohne, Bremsen 0,80° bzw. 0,88° (Rest der Glättung), NeoForge 2,79° bei 3,61°, Bremsen 0,90°;
  Horizont und Neigung überall 0,00°, Bild 87–103 % ohne Rücklauf (höchstens 3 %), Rahmen bis 47,8° geneigt.
- Dieselbe Messung wie bei OWNC6-1 an den Aufnahmen von 627 (1080p60, Phasenkorrelation, je 2 s): vorher wie nachher
  keine 20-Hz-Stufen (Spitze 0,9- bis 3,5-fach über dem Median, Faltung über drei Bilder gleichmäßig, keine
  Vorzeichenwechsel bei schneller Bewegung); das Nachziehen zeigt sich nur in der Lage (oben), nicht als Ruckeln.

## 33. Hilfetexte und Anleitung passend zur frames-Liste der Gimbals (Prüfplan-Durchsicht 28.10., DEC2810-3)

Befund DEC2810-3: Der Hilfetext des Kino-Gimbals sagte „− 420 g und 7 W: nur am Kino-Rahmen“ („only on the cine
frame“), der Gimbal nennt unter `"frames"` aber alle drei Kamera-Rahmen und baut an Kompakt, Tour und Kino (§16). Der
Text heißt jetzt „− 420 g und 7 W: zu schwer für die Kompakt“ („too heavy for the compact“): Laut Werkbank-Analyse wiegt
die Kompakt damit 638 g bei Schub/Gewicht 1,11 und schwebt nicht (`cannot_hover`); die Tour schwebt 27,8 statt
41,4 min, die Kino mit ihrem eigenen Gimbal unverändert. Die Anleitung (EN/DE, Abschnitt Klassen) sagt, dass jeder der
drei Gimbals an alle drei Kamera-Rahmen passt und an anderen Rahmen nur mit Propwashs Welt-Regel „Teile müssen
zusammenpassen“ aus; im Abschnitt Gimbal stehen Neigung bis +60° und 360°-Schwenk beim Kino-Gimbal statt bei „Kino“.

Tests:

- JUnit `part.GimbalHelpTest` (4 Fälle): kein Hilfetext eines Gimbals nennt einen Rahmen außerhalb seiner frames-Liste
  oder schränkt mit „nur“/„only“ auf weniger Rahmen ein (EN und DE, Rahmen-Namen aus `camdrones.frame_short.*`); der
  Absatz zu den Halterungen in `docs/guide.md` und `docs/guide.de.md` nennt alle Rahmen der frames-Listen und die
  Welt-Regel mit ihrem Namen aus Propwashs Sprachdatei; im Abschnitt „### Gimbal“ steht das Wort der Kino-Klasse nur
  als „Kino-Gimbal“ bzw. „cine gimbal“; der Kino-Gimbal baut an allen drei Presets, nur die Kompakt schwebt damit nicht,
  die Tour schwebt 60–75 % so lange. Vorher 3 von 4 rot, nachher 4 von 4 grün.
- Selbsttest der Gruppen `gimbal:` und `crew:` (`'-PcamdronesSelftestOnly=gimbal:,crew:'`, etwa 5 min) auf Fabric und
  NeoForge je 13 von 13 PASS.

## 34. Sport unter Last ohne Ruckeln (Playtest-Forum rc.1, PTC-1)

Befund PTC-1 (PT-1): Die 6S-Kameradrohne (Kino) ruckelt im Sport-Modus ab etwa 50 % Gas. Messung im Selbsttest 641–643
(unten) mit den Werten der Flughilfe in jedem Schritt: Bei 50 % Gas hatte das eigene Gas der Kino Reserve (0,78–0,84),
die Neigungsgrenze aus §27 blieb bei 35°. Die Neigung kippte trotzdem mit etwa 1,4 s um ±1° (33,4° – 34,2° – 32,3° –
33,1° – 31,0°), die Steigrate schwang mit (−0,1 bis +0,3 m/s). Ursache war die Tempo-Bremse beim Sport-Tempo
(P 3° je m/s, 1 s Vorhalt aus der Beschleunigung): Weniger Neigung gibt bei hohem Tempo mehr Auftrieb, das eigene Gas
nimmt Schub zurück, die Drohne wird langsamer, die Bremse gibt Neigung frei, und umgekehrt. Dazu kam die
Neigungsgrenze bei knappem Schub: Ab 98 % Gas und Sinken über 0,3 m/s (oder 1 m zu tief) fiel die Neigung mit 20°/s, sonst
stieg sie mit 5°/s. Wo auch Vollgas nicht reicht (schwacher Akku, Steigen bei hohem Tempo), ergab das einen Sägezahn
(Modell: 6 bis 16 Wechsel in 14 s mit 20°/s).

Seit dem Fix (Verhalten wie entschieden: Steigen kostet Vorwärtsneigung, aber stetig):

- Die Neigungsgrenze folgt einem stetigen Regler: Ab 90 % Gas (geglättet 0,2 s, weicher Übergang bis 100 %) nimmt ein
  I-Anteil Neigung weg (4°/s je m/s Höhenfehler, höchstens 1 m/s plus Sinkrate), ein P-Anteil nur bei ungewolltem Sinken
  (6° je m/s); unter 90 % Gas kehrt die Neigung stetig zurück (höchstens 5°/s), nie unter 10°. Ohne eigenes Gas
  (Prüfstände ohne Schwebe-Gas) dasselbe stetig aus Sinkrate und Höhenfehler. Die Schwellen aus §27 („Gas über 98 %“,
  0,3 m/s, 1 m) gibt es nicht mehr.
- Die Tempo-Bremse greift sanfter: P 1,5° je m/s, Vorhalt 0,5 s, I unverändert 4°/s je m/s; Bremsen und Neigung quer
  zur Flugrichtung bleiben frei.

Tests:

- JUnit `logic.SportLoadTest` (3 Fälle), Modell mit Schubverlust durch Anströmung (Props 6 und 9,6 m/s, Schwebe-Gas
  0,5, Lage 0,1 s, Motor 0,05 s): alle Klassen mit voller Vorwärtsneigung bei 50, 75 und 100 % Gas höchstens zwei
  Wechsel der Neigung um 0,5°, höchstens 12°/s, bei 50 % höchstens 1,5 m tiefer, sonst mindestens ein Viertel der
  Steigrate; die Kino am Gas-Anschlag neigt beim Steigen mit Vollgas weniger als beim Halten, die Kompakt mit Reserve
  nicht; eine Tempo-Schwankung von ±0,06 m/s mit 0,7 Hz beim Anflug aufs Sport-Tempo kippt die Neigung nicht. Vorher
  3 von 3 rot (Tour 13 Wechsel, Kino 32,0° statt unter 23,0°, Kompakt 4 Wechsel), nachher grün.
- `StickResponseTest.sportLeansLessWhileItSinksAndFullyAgainOnceTheHeightHolds` (ohne eigenes Gas) prüft stetiges
  Nachgeben: nach 1 s Sinken mit 1 m/s mindestens 8° weniger, 1 m zu tief weiter weniger, nach dem Aufholen wieder voll,
  leichtes Sinken mit 0,25 m/s kostet höchstens 3°.
- Selbsttest 641–643 `flug: Sport unter Last ohne Ruckeln` je Klasse (`dev/client/SportLoadSteps`,
  `'-PcamdronesSelftestOnly=641-643'`, etwa 3,5 min): Wind aus, auf 8 m, S, je 10 s voll vorwärts bei 50 % Gas, 8 s
  voll rückwärts bei 75 % und 6 s voll vorwärts bei 100 %, dazwischen N und bremsen. Neigungsverlauf (alle 0,1 s) und
  Höhenverlauf (alle 0,5 s) stehen im Protokoll. Verlangt ab 1,5 s: höchstens zwei Wechsel der Neigung um 0,5° oder
  mehr, höchstens 12°/s Änderung über 0,1 s (Ticks, in denen die Drohne am Client stillsteht, und der Tick danach
  zählen nicht), bei 50 % höchstens 1,5 m tiefer, bei 75 und 100 % mindestens ein Viertel der Steigrate. Vorher Fabric
  und NeoForge FAIL (Kino 50 %: je 3 Wechsel, Kompakt und Tour PASS), nachher Fabric und NeoForge PASS:

  | Fabric / NeoForge | 50 % | 75 % | 100 % |
  |---|---|---|---|
  | Kompakt Wechsel, °/s | 1 / 1, 3,7 / 5,3 | 0 / 1, 6,6 / 5,2 | 0 / 0, 4,7 / 6,7 |
  | Tour Wechsel, °/s | 1 / 1, 4,7 / 3,4 | 0 / 0, 4,8 / 4,8 | 0 / 0, 5,8 / 4,7 |
  | Kino Wechsel, °/s | 0 / 0, 5,3 / 4,1 | 0 / 0, 6,1 / 4,2 | 0 / 0, 6,0 / 4,7 |
  | Kino Neigung, Höhe | 30,4–35,0° / 30,6–35,0°, −0,6 m | 29,7–35,0° / 29,5–35,0°, +19,2 / +19,2 m | 23,7–35,0° / 22,1–34,5°, +33,3 / +36,8 m |

  Vorher stieg die Kino bei 100 % mit 33,6–35,0° Neigung (Fabric), nachher mit 23,7–35,0°: Steigen kostet
  Vorwärtsneigung jetzt stetig.
- Selbsttest 621–624 (Sport-Tempo und Sturm) auf Fabric und NeoForge PASS: Kompakt 90 % nach 3,35–3,50 s, Spitze
  16,17–16,18 m/s, Tour 90 % nach 4,40–4,50 s, Spitze 19,77–19,92 m/s, Kino 90 % nach 5,95–6,00 s, Spitze
  24,87–25,14 m/s. 623 (Kino) scheiterte im Sammellauf mit 641–643 je einmal vor dem Sport-Abschnitt am OSD-Hinweis
  bzw. an der Modus-Taste (Client unter Last, „gezeigt: “ leer); allein auf beiden Loadern PASS.

## 35. Propwashs Einblendung nennt N, C und S (Folgebefund AUTO43-1 zu RC2-1)

Befund AUTO43-1: Propwash fragt seit Befund RC2-1 die Flughilfe über `FlightAssist#modeNameKey(StabilizationMode)`
nach dem Namen eines Flugmodus, für die Einblendung beim Moduswechsel und den Hinweis „per Taste gewählt“ im Tab
*Modi*. `NavAssist` setzte die Methode nicht um; die Kamera-Drohne meldete deshalb weiter „Mode: Horizon (key – radio
switch: Acro)“ statt „Mode: C (key – radio switch: S)“. Seit dem Fix:

- `NavAssist#modeNameKey(StabilizationMode)` liefert für Angle `camdrones.mode.n`, für Horizon `camdrones.mode.c` und
  für Acro `camdrones.mode.s` (Zuordnung über `NavAssist.stickMode`). Ist ATTI aktiv (kein GPS-Fix oder
  Kompassfehler, `NavLogic#atti`), heißt Angle `camdrones.mode.atti`. Die Texte im Modul `core` sind die Buchstaben des
  OSD: „N“, „C“, „S“ und „ATTI“, auf Englisch und Deutsch gleich.
- Die Methode trägt kein `@Override`, weil `gradle.properties` noch auf `0.6.0-rc.1` steht und dessen `FlightAssist`
  sie nicht kennt. Gleicher Name und gleiche Signatur reichen, damit die JVM sie wählt, sobald Propwash mit
  `modeNameKey` läuft. Mit rc.1 ruft Propwash sie nicht auf, die Einblendung bleibt dort wie bisher. Im Spiel wirkt
  der Fix also erst mit der Propwash-Version, die der Integrator einträgt.

Tests:

- JUnit `integration.NavAssistModeNameTest` (4 Fälle):
  - Sucht `modeNameKey(StabilizationMode)` wie Propwash über die Klasse der Flughilfe: öffentlich, nicht statisch,
    `Optional<String>`, von `NavAssist` selbst.
  - Angle, Horizon und Acro ergeben N, C und S, in jeder Klasse vor dem ersten Schritt und im Flug.
  - Ohne GPS ergibt Angle ATTI statt N (Horizon C, Acro S), nach der Rückkehr des Fixes wieder N.
  - Die Texte sind auf Englisch und Deutsch die Buchstaben, die das OSD im jeweiligen Modus zeigt.
  - Mit Propwashs Texten aus seiner Sprachdatei ergeben sich „Mode: C (key – radio switch: S)“, „Modus: N (Taste –
    Funkenschalter: S)“ und „Mode: S“.
  - Vor dem Fix 4 von 4 rot („NavAssist hat kein modeNameKey(StabilizationMode) …“), nach dem Fix 4 von 4 grün.
    `:common:test` 866 Tests grün, `./gradlew build` grün.
- Selbsttest der Gruppen `flug:` und `tasten:` (`'-PcamdronesSelftestOnly=flug:,tasten:'`, etwa 21 min, gegen
  0.6.0-rc.1) auf Fabric und NeoForge je 30 von 30 PASS. Einen Schritt für den Text der Einblendung gibt es hier
  nicht, weil er gegen rc.1 rot bliebe. Propwashs Selbsttest „addon-api-17: A1 – Meldung beim Moduswechsel …“ prüft
  diesen Weg mit einer Probe-Flughilfe. Nach dem Rebase auf `main` mit F-PTC-1 liefen 381–385 noch einmal auf Fabric
  und NeoForge, je 6 von 6 PASS.

## 36. Flugschule: Hindernis-Stopp mit sichtbarer Drohne (Playtest-Forum rc.1, PTCC-1)

Befund PTCC-1: Im Kapitel „Kamera-Drohne fliegen“ (`client/school/CameraDroneScene`) zeigte Schritt 5/5
„Hindernis-Stopp“ die Drohne fast nicht. Die Kamera stand mit yaw 40 hinter der Wand (Blöcke bei x = 3, y 1–4,
z −1–2) und schaute durch sie hindurch auf die Drohne am Stopp (x = 1,6); `SceneFraming` hält nur Drohne, Hinweis und
Markierung im Rahmen, verdeckt werden dürfen sie trotzdem. Jetzt steht die Kamera mit yaw −30 (pitch 24, Zoom 1,25,
Ziel unverändert) auf der Flugseite: Die Drohne fliegt im Bild von links auf die Wand rechts zu, Stopp-Markierung und
Wand sind ganz zu sehen, der Hinweis „Stoppt vor der Wand“ bleibt links der Drohne. Am unteren linken Rand ist bei
16:9 der Kopf des Piloten angeschnitten; ein Bildausschnitt mit ganzem Piloten zeigt die Drohne etwa 30 % kleiner und
wurde deshalb nicht genommen.

Tests:

- JUnit `client.school.ObstacleStopViewTest` (5 Fälle), alle 2 Ticks ab 30 Ticks nach Schrittbeginn (Kamera-Schwenk
  fertig) bis Schrittende: Die Kamera schaut auf die der Drohne zugewandte Seite der Wand (Blickrichtung gegen die Wand
  mindestens 0,3, also nicht von hinten und nicht auf die Kante); kein Block liegt zwischen Kamera und Drohne (Strahl in
  Blickrichtung von der Mitte und 8 Punkten am halben Rahmenradius); die Stopp-Markierung (Mitte und 8 Ecken) und die
  Vorderseite der Wand (16 Punkte) sind frei; Drohne, Wandmitte und Markierung liegen nach `SceneFraming` bei 300×170,
  420×236 und 600×300 im Bild; die Wand steht hinter dem Stopp in Flugrichtung. Vorher 3 von 5 rot (yaw 40: Wand von
  hinten, −0,59; Drohne am Stopp zu 8 von 9 Punkten verdeckt, 61 Zeitpunkte; Markierung ganz verdeckt), nachher 5 von 5
  grün.
- Selbsttest 405 `flugschule: Kapitel der Kamera-Drohnen` springt im Kapitel `camera_drone` mit 4× Pfeil rechts zu
  Schritt 5/5, wartet, bis Schrittname und Hinweis zu sehen sind, und hält 12 Ticks später an (Drohne am Stopp).
  Bildschirmfotos `flight_school_camera_drone_obstacle` (EN und DE). Gezielt
  `'-PcamdronesSelftestOnly=2,9,301,401-405'` auf Fabric und NeoForge je 9 von 9 PASS.

## 37. Crew übersteht das Trennen, leiseres Gimbal-Surren (Playtest-Forum rc.1, PTC-3)

Befund PTC-3 (PT-8 und PT-9 aus dem Playtest-Forum zu rc.1):

- **Trennen:** Trennte sich der Operator (Verbindung weg, Spiel verlassen), beendete `CrewService.tick` die Crew bei der
  nächsten Prüfung (jede Sekunde) mit `End.OFFLINE`; die Bindung lag nur im Speicher (`CrewBook`) und war danach weg.
- **Surren:** `GimbalClient.servo()` spielte `gimbal.calibrate` als UI-Ton in 0,5 ohne Ort, und `GimbalServoTone` löste
  bei jeder Bewegung über der Sende-Schwelle aus (auch Stupser und kleine Korrekturen, aufaddiert), höchstens alle
  16 Ticks, bei Pilot wie Operator.

Seit dem Fix:

- Trennt sich der Operator, hält `CrewService` die Crew 2:00 (`CrewBook.GRACE_MILLIS`, `CrewBook.Wait`): Bindung im
  `CrewBook`, Einladender und Gimbal beim Operator bleiben, Propwashs Operator-Bindung ist gelöst. Pilot, Besitzer und
  Einladender lesen im Chat „Operator … getrennt – die Crew wartet 2:00 auf die Rückkehr“, die Aktionsleiste zeigt jede
  Sekunde „Operator getrennt – wartet m:ss“. Kommt der Operator in der Zeit zurück (auch zwischen zwei Prüfungen, erkannt
  am neuen Spieler-Objekt), bindet `CrewService` ihn über Propwash neu, schickt `crew_notice` BOUND fürs Livebild und
  meldet „Crew wiederhergestellt“; lehnt Propwash die Bindung ab (etwa außer Reichweite), versucht es `CrewService` jede
  Sekunde neu, bis die Frist abläuft. Sonst endet die Crew nach 2:00 mit „… ist nicht rechtzeitig zurückgekommen“. Welt-Regel
  `allow_operator` aus, Drohne weg, Zurücknehmen und Pilotenwechsel beenden sie auch während der Frist; ein Neustart des
  Servers beendet jede Crew (nur im Speicher).
- `gimbal.calibrate` spielt als `EntityBoundSoundInstance` an der Drohne (Quelle Spieler, Abklingen laut `sounds.json`,
  12 Blöcke) in der Lautstärke des neuen Reglers „Gimbal-Surren“ (`gimbal_servo_volume`, 0 bis 1, Standard 0,15,
  0 = aus; Tab „Kamera-Drohnen“, Abschnitt „Kamera-Ansicht und Warntöne“, §11). `GimbalServoTone` zählt erst nach
  10 Ticks durchgehender Bewegung (je Tick mehr als 0,15° bzw. 0,004 Zoom, bis zu 2 ruhige Ticks unterbrechen nicht)
  und mindestens 4° (bzw. 0,05 Zoom) Weg in eine Richtung, danach höchstens alle 16 Ticks. Stupser, Kriechen und
  Hin-und-her-Korrekturen bleiben stumm.

Tests:

- JUnit `client.gimbal.GimbalServoToneTest` (17 Fälle, neu: kurzer Stupser, erste Wiedergabe nach 10 Ticks, Kriechen
  0,05°/Tick, Hin-und-her-Korrekturen, Schwenk über die Rückseite, kurze und lange Pause, 0 % = aus) und
  `client.gimbal.GimbalServoSoundTest` (2 Fälle: Ton an der Drohne statt UI-Ton, neue Konfiguration mit
  `gimbal_servo_volume` 0,15). Vorher 6 von 16 rot (beide aus `GimbalServoSoundTest`, Stupser, erste Wiedergabe nach
  10 Ticks, Kriechen, Hin-und-her), nachher 19 von 19 grün. `crew.CrewBookTest` +4 (Frist 2:00, Rückkehr und Ende der
  Frist durch Zurücknehmen oder Abgeben, `CrewService.watch` für Halten, Warten, Wiederherstellen und Ablauf, Uhr m:ss).
- Beitrittstest-Fall `crew-rejoin` (§3), Server mit `CAMDRONES_SERVERTEST=crew-rejoin CAMDRONES_JOINTEST_HOLD=1800`,
  Client mit `CAMDRONES_SERVERTEST=crew-rejoin CAMDRONES_JOINTEST_LABEL=crew-rejoin` und
  `CAMDRONES_JOINTEST_EXPECT='joined|crew-rejoin a operating|left for rejoin|rejoined|crew-rejoin a restored operating|crew-rejoin a restored notice|crew-rejoin a left|crew-rejoin b waiting notice|crew-rejoin b countdown|crew-rejoin b expired notice'`.
  `JoinProbe.rejoin(<Ticks>)` trennt den Client mitten im Ablauf und verbindet ihn nach der Wartezeit neu. Vorher
  (Fabric, Stand vor dem Fix) FAIL: „Kamera-Operator … beendet (OFFLINE)“ eine Sekunde nach dem Trennen, „a: Crew endet
  beim Trennen des Operators sofort (Crew false, Gimbal false)“. Nachher Fabric und NeoForge PASS: nach 22 Ticks
  besteht die Crew mit Rest 119,75 bzw. 119,45 s, nach der Rückkehr (4,2 bzw. 4,4 s) nach 11 bzw. 1 Tick
  wiederhergestellt, Client liest „Crew restored“; (b) Chat „Operator camdrones_guest disconnected – the crew waits
  2:00 …“, Aktionsleiste 2:00 | 1:59 | 1:58, nach 60 s wartet die Crew noch, endet nach 121,5 bzw. 121,0 s mit freiem
  Gimbal und „… did not return in time“.
- Beitrittstest `covcamb` (§10): „Operator trennt“ verlangt jetzt binnen 1 s die Frist statt des Endes; Fabric PASS
  (nach 10 Ticks wartet die Crew 2:00, Gimbal beim Operator, Propwash-Bindung gelöst). Beitrittstest `crew` Fabric
  unverändert PASS.
- Selbsttest 538 und 539 (§10) prüfen zusätzlich: `gimbal.calibrate` spielt an der Drohne (nicht relativ, nicht UI,
  höchstens 2 m daneben) in 15 %, 3 Ticks Antippen bleibt stumm, bei Gimbal-Surren 0 % bleibt Neigen stumm. Selbsttest 47
  (§11) mit dem Regler „Gimbal-Surren“: bei GUI 320×240 in EN und DE ganz zu sehen, 15 %, 0 % gespeichert und wieder
  angezeigt, Standardwerte zurück. `'-PcamdronesSelftestOnly=gimbal:,crew:,47,538,539'` (etwa 6 min): NeoForge 16 von
  16 PASS; Fabric 15 von 16, 625 (Livebild ohne 20-Hz-Stufen, Schwenk-Rücklauf 50 % statt höchstens 25 % bei einer
  senkrechten Bildbewegung von 0,04°/Tick) allein wiederholt PASS (Rücklauf 7 %), unabhängig von PTC-3. 538/539 auf
  beiden Loadern: Neigen 0° auf −90° mit 2 Tönen in 0,15, Quelle PLAYERS, 0,00 m neben der Drohne; Antippen −90° auf
  −81° ohne Ton.

## 38. Abgelehnte Neubindung nach der Rückkehr des Operators (Gegenprüfung G-PTC-3, AUTO48-1)

Befund AUTO48-1 (aus der Gegenprüfung zu PTC-3, §37):

- Kam der Operator während der Frist zurück und lehnte Propwash die Neubindung ab (Drohne außer Videoreichweite, andere
  Dimension, Operator fliegt eine andere Drohne; `OperatorAccess.bind` liefert nur `false`), rief `CrewService.restore`
  nur `CrewBook.hold` auf (ohne Wirkung, die Frist lief schon) und kehrte ohne Meldung zurück. Der Operator bekam bis
  zu 2:00 keinen Hinweis, Pilot, Besitzer und Einladender keine Restzeit mehr (die Aktionsleiste kam nur im Fall WAIT,
  mit dem Operator online gilt RESTORE), und nach Ablauf endete die Crew mit `End.OFFLINE` und „… ist nicht rechtzeitig
  zurückgekommen“, obwohl er zurück war.

Seit dem Fix:

- `CrewBook.refuse` merkt sich im `CrewBook.Wait` (`back`), dass der Operator zurück ist und Propwash abgelehnt hat. Die
  Frist läuft weiter und beginnt nicht neu; kam er zwischen zwei Prüfungen zurück (noch keine Frist), beginnt sie mit
  der Ablehnung. Beim ersten Mal liest der Operator im Chat „Du bist zurück, kannst die Kamera aber noch nicht
  übernehmen: Komm in Videoreichweite der Drohne und in ihre Dimension und flieg keine andere Drohne. Die Crew wartet
  noch m:ss.“, Pilot, Besitzer und Einladender „… ist zurück, kann die Kamera aber noch nicht übernehmen – die Crew
  wartet noch m:ss.“ Danach zeigt die Aktionsleiste jede Sekunde die Restzeit: beim Operator „Kamera nicht erreichbar –
  die Crew wartet noch m:ss“, bei den anderen „Operator zurück, Kamera nicht erreichbar – wartet m:ss“. `CrewService`
  fragt Propwash weiter jede Sekunde; klappt die Bindung, meldet er wie bisher „Crew wiederhergestellt“. Trennt sich der
  Operator wieder, gilt wieder „Operator getrennt – wartet m:ss“, und bei der nächsten Rückkehr kommt der Hinweis erneut
  (`CrewBook.away`).
- Läuft die Frist ab, während der Operator online ist, endet die Crew mit `End.UNREACHABLE`: die anderen lesen „… konnte
  die Kamera nicht rechtzeitig übernehmen – die Crew ist beendet.“, der Operator „Du konntest die Kamera nicht
  rechtzeitig übernehmen – die Crew ist beendet.“ Ist er offline, bleibt es bei `End.OFFLINE` und „… ist nicht
  rechtzeitig zurückgekommen“ (`CrewService.expired`, `pilotText`, `operatorText`).

Tests:

- JUnit `crew.CrewBookTest` +3: `refusedRebindKeepsTheGraceAndTellsOncePerReturn` (Frist läuft weiter, Hinweis einmal je
  Rückkehr, danach weiter RESTORE, `away` setzt zurück), `refusedQuickRejoinStartsTheGrace`,
  `expiryTextTellsWhetherTheOperatorCameBack`. `:common:test` 887 Tests grün (vorher 884).
- Beitrittstest-Fall `crew-rejoin` (§3), neuer Teil (c) zwischen (a) und (b): der Client lädt den simulierten Spieler als
  Operator seiner Drohne ein, der verlässt das Spiel und kommt nach 5 s im Nether zurück; Propwash lehnt die Bindung ab
  (andere Dimension). Der Server schreibt die Nachrichten an den simulierten Spieler mit (`ServerSetup.GUEST_MESSAGES`)
  und verlangt binnen 5 s den Hinweis, danach genau einen Hinweis, mindestens drei verschiedene Restzeiten in seiner
  Aktionsleiste und nach 2:00 (−1 s bis +4 s) das Ende mit freiem Gimbal und „Du konntest die Kamera nicht rechtzeitig
  übernehmen …“; der Client liest „… ist zurück, kann die Kamera aber noch nicht übernehmen …“, sieht die Restzeit
  weiterlaufen und am Ende „… konnte die Kamera nicht rechtzeitig übernehmen“, nie „… ist nicht rechtzeitig
  zurückgekommen“. Client mit
  `CAMDRONES_JOINTEST_EXPECT='joined|crew-rejoin a operating|left for rejoin|rejoined|crew-rejoin a restored operating|crew-rejoin a restored notice|crew-rejoin a left|crew-rejoin c blocked notice|crew-rejoin c countdown|crew-rejoin c unreachable notice|crew-rejoin b waiting notice|crew-rejoin b countdown|crew-rejoin b expired notice'`,
  Laufzeit etwa 4,5 min. Vorher (Fabric, Stand vor dem Fix) FAIL: „crew-rejoin c: kein Hinweis an den Operator nach der
  Rückkehr (Propwash-Operator false, wartet true, Nachrichten an camdrones_guest: [])“. Nachher Fabric PASS: Hinweis
  12 Ticks nach der Rückkehr („… The crew waits 1:55 more.“), Restzeit beim Operator 114-mal (1:55 | 1:54 | 1:53 …),
  beim Client „Operator back, camera out of reach – waiting 1:55 | 1:54 | 1:53“, Ende nach 120,60 s mit freiem Gimbal,
  „camdrones_guest could not take over the camera in time – the crew has ended.“; NeoForge PASS: Hinweis nach 16 Ticks
  (1:56), Restzeit beim Operator 89-mal (bei unter 20 Ticks pro Sekunde fehlen einzelne Sekunden), Ende nach 120,80 s;
  (a) und (b) auf beiden Loadern unverändert PASS.

## 39. Weiche Kurven ohne Abbremsen an jedem Wegpunkt (Owner 04.11., rc.3, SMOOTH1-1)

Befund SMOOTH1-1: Im Kurvenmodus „weich“ hielt die Drohne gefühlt an jedem Wegpunkt. Die Kurve durfte nur 0,5 m vom
Wegpunkt abweichen, das Tempo darin war sqrt(2 m/s² · Radius): bei 90° etwa 1,5 m/s, bei 45° etwa 3,5 m/s.

Seit dem Fix:

- `logic.nav.CurvedTurn` beschreibt jede weiche Kurve als waagrechten Kreisbogen, der bei 45 % des kürzeren waagrechten
  Beins vor dem Wegpunkt beginnt und nach ihm endet; die Höhe geht im Bogen als kubische Kurve mit den Steigungen beider
  Beine über. Das Tempo im Bogen ist das Reisetempo, gesenkt nur auf die Querbeschleunigung der Klasse
  (`FlightClass#curveAccel`: Compact und Tour 2,2 m/s², Kino 2,0 m/s²), die Steig- und Sinkgrenzen und den Bremsweg bis
  zur nächsten Kurve bzw. zum nächsten Halt (Vorausschau über bis zu acht Kurven, `CurvedTurn#plannedSpeed`).
- `WaypointFollower` fliegt den Bogen mit Vorsteuerung der Zentripetalbeschleunigung (0,6 s) und wechselt am Scheitel
  zum nächsten Punkt; dort lösen Aktion und Aufgaben aus. Flugrichtung folgt der Bahntangente, feste Blickrichtung und
  Gimbal-Neigung gehen von Scheitel zu Scheitel über und springen nicht. Halt weiter bei Wartezeit, am letzten Punkt, bei
  Haarnadeln über 150° und bei Beinen unter 1 m; Modus eckig unverändert. Der Hindernisschutz wirkt danach wie bisher.
- `FlightTimeEstimate` rechnet weiche Missionen mit demselben Modell; die Bahnvorschau (Karte im Planer ab dem
  Startpunkt, Marker in der Welt ab Punkt 1) zeichnet die Bögen (`CurvedTurn#path`, `#loopPath`).

Tests:

- JUnit `WaypointFollowerTest.smoothTurnsKeepTheCruiseSpeedAndStillFireTheActions` (vier Punkte, 45°/90°/45°, Beine
  40 m, 8 m/s, alle Klassen, ohne und mit Wind): kein Einbruch unter 70 % zwischen Reisetempo und Bremsen zum letzten
  Punkt, Aktionen je einmal in Reihenfolge. Vorher rot (Compact 0,86 m/s vor Punkt 3). Dazu `tiltAndHeadingStay…`
  (Neigung höchstens 0,5°, Richtung höchstens die Drehrate je 10 ms), `obstacleGuardStillStopsInsideASmoothTurn`
  (Pfeiler im Scheitel des 90°-Bogens: Stopp ≥ 1 m davor, Warnung OBSTACLE_STOP) und `CurvedTurnTest` (Geometrie,
  Tempo, Bahn). Die bisherigen Tests für weiche Kurven prüfen 1 m Abweichung von der geplanten Bogenbahn statt vom
  Wegpunkt (Wind 5 m/s mit Böen aus acht Richtungen: höchstens 0,80 m).
- JUnit `FlightTimeEstimateTest.smoothTurnsAreEstimatedLikeTheyAreFlown`: dieselbe Mission bei 5, 8 und 12 m/s für alle
  Klassen auf 10 % (gemessen höchstens 1 %); vorher 11 % daneben.
- JUnit `MissionTimerReplayTest`: die Spuren der weichen Missionen sind mit dem neuen Folger neu aufgezeichnet (387,
  389, 392, 393 und die 20/21-m-Fälle auf Fabric und NeoForge, Rücksprung der Spielzeit aus 389 NeoForge); die Kino-Spur
  aus 390 entfällt, solange die Kino-Drohne in der Testumgebung beim Start NOT_READY bekommt (auch eckig in 386). Seit
  RC3C2-2 ist sie wieder aufgenommen (§43).
- Selbsttest 553 `mission: weiche Kurven ohne Abbremsen` (Tour vom Boden, dieselbe Mission auf 12 m, Flug ohne Pilot):
  langsamstes Tempo über 0,5 s mindestens 70 %, waagrechte Abweichung von der geplanten Bahn höchstens 1,5 m, danach Bild
  der Bahn in der Welt-Vorschau (`docs/screenshots/mission_smooth_curves.png`, `_de`). Fabric 6,36 m/s (80 %),
  0,56 m; NeoForge 6,36 m/s (80 %), 0,60 m; geflogen 28,2 s, Schätzung 26,1 s.
- Ist am Rechner eine Funke angeschlossen, fragt Propwash im Selbsttest nach ihrer Einrichtung (das Spiel pausiert) und
  nimmt sie statt der Tastatur: In `<loader>/runs/selftest-client/config/propwash/input.json` die GUID unter
  `neverAsk` eintragen und `selectedGuid` auf `keyboard` setzen.

## 40. Kino-Drohne startet nicht vom Boden, Flugzeit an Haarnadeln, kurzen und steilen Beinen (Gegenprüfung G-SMOOTH1-1, AUTO54-1, AUTO54-2, RC3C-1, AUTO55-1, RC3C3-1, AUTO57-1)

Befund AUTO54-1: Die Kino-Drohne startet vom Boden keinen Flug ohne Pilot, Propwash antwortet `NOT_READY`.

- Ursache in Propwash: Seit 0.6.0-rc.2 steht eine abgestellte Drohne auf Zubehör, das unter den Rahmen ragt. Die Kino
  steht auf ihrem 125 mm hohen Gimbal, ihre Hitbox liegt dann 0,162 m über dem Boden (Compact 0,039 m, Tour 0,059 m).
  Einen Start vom Boden (`AutonomyOptions#takeoff`) nimmt Propwash aber nur an, wenn unter der Hitbox weniger als 0,08 m
  frei sind. Dabei meldet Propwash die Drohne selbst als ruhend (`onGround`, `DroneView#flying` false). Im Add-on lässt
  sich das nicht beheben; der Fehler ist an Propwash gemeldet.
- Folge: Mission, Follow me, Halten und POI der Kino lassen sich nicht vom Boden starten. Rot sind deshalb die
  Selbsttests 386, 388, 390 und 601 (Kino) und 351 (Tour auf einem in den Boden gesetzten Landeplatz, der Gimbal liegt
  auf dem Rand der Grube).
- Selbsttest 554 `mission: Start vom Boden für Compact, Tour und Kino (Kino steht auf dem Gimbal)`
  (`dev/client/GroundStartSteps`): setzt die drei Klassen nacheinander auf den Boden, nennt Höhe der Hitbox, `onGround`
  und `DroneView#flying` und startet eine Mission mit einem Punkt 12 m hoch. Erwartet werden `MISSION_STARTED` und der
  Flug `camdrones:mission` am Server. Stand mit rc.2 auf Fabric und NeoForge: Compact und Tour starten, die Kino bekommt
  `NOT_READY`.
- Der Schritt ist gesperrt, bis das Add-on gegen eine Propwash-Version mit Fix baut. Freigabe: in
  `DevClientBootstrap#init` nach `CurveSteps.register();` die Zeile `GroundStartSteps.register();` einfügen. Danach die
  Kino-Spur aus 390 für `MissionTimerReplayTest` neu aufnehmen. Seit RC3C2-2 freigeschaltet, die Spur ist aufgenommen
  (§43).

Befund AUTO54-2: Im Modus weich hat die Flugzeit-Schätzung an Haarnadeln über 150° nicht angehalten, der Folger hält
dort aber.

- Fix: `FlightTimeEstimate` rechnet jetzt einen Halt an jedem Punkt ohne Bogen, an dem auch die Ecke des Folgers fehlt.
  Beide rufen dafür dieselbe Methode `WaypointFollower#corner` auf. Die Ecke fehlt bei einer Haarnadel über 150° und
  bei einem Bein unter 0,1 m. Bis RC3C-1 galt das nur ohne Höhenwechsel: Die Ecke hat den Winkel im Raum gemessen, der
  Bogen waagrecht. Seitdem entscheidet für beide `CurvedTurn#hairpin` (siehe unten).
- JUnit `FlightTimeEstimateTest.smoothHairpinsAreEstimatedWithTheStopTheFollowerMakes`: zwei Missionen (Haarnadel; hin
  und zurück), alle Klassen, 5, 8 und 12 m/s, Punktmasse gegen Schätzung, Toleranz 10 %. Vorher −14,6 % bis −33,2 %,
  nachher −3,8 % bis −6,0 %. `curves()` bleibt unverändert bei −1,1 % bis +0,4 %.
- Offen: Steile oder senkrechte Beine (waagrecht unter 1 m) schätzt die Vorschau in beiden Modi grob. Im Versuch mit
  einem 15-m-Steigbein lag sie weich bis −27 % und eckig bis +19 % daneben. Das gehört nicht zu diesem Befund.

Befund RC3C-1 (Gegenprüfung G-AUTO54-1, Restfall von AUTO54-2): An Haarnadeln, die waagrecht über 150° drehen und im
zweiten Bein steigen oder sinken, hat die Schätzung im Modus weich weiter mit Reisetempo gerechnet. Der Bogen
(`CurvedTurn#of`) hat den Winkel waagrecht gemessen (über 150°, also kein Bogen), die Ecke des Folgers
(`WaypointFollower#corner`) im Raum (durch den Höhenwechsel unter 150°, also eine Ecke ohne Halt). Die Schätzung hat
deshalb weder Bogen noch Halt gerechnet. Der Folger hat an der Ecke trotzdem fast bis zum Stand gebremst (0,31 bis
0,76 m/s).

- Fix: Ob ein Punkt eine Haarnadel ist, entscheidet nur noch `CurvedTurn#hairpin`. Bogen und Ecke rufen es auf, damit
  auch Folger, Bahnvorschau (`CurvedTurn#path`, `#loopPath`) und Schätzung. Gemessen wird waagrecht, wie der Bogen
  liegt und der Folger lenkt: Drehen die beiden Beine in der Draufsicht um mehr als 150°, ist es eine Haarnadel, egal
  wie steil ein Bein steigt oder sinkt. Nur wenn ein Bein waagrecht kürzer als 1 m ist (steiles oder senkrechtes Bein,
  dort gibt es keinen Bogen und in der Draufsicht keine Richtung), zählt der Winkel im Raum wie bisher bei der Ecke. An
  einer Haarnadel hält der Folger am Punkt und fährt aus dem Stand an, die Schätzung rechnet genauso, die Bahnvorschau
  zeigt dort die Spitze.
- Folge für den Flug: An solchen Punkten hält die Drohne jetzt, statt mit 0,3 bis 0,8 m/s um die Ecke zu fahren. Die
  beiden Missionen unten dauern dadurch 0,7 bis 1,1 s länger. Bögen, Ecken an steilen Beinen und Haarnadeln ohne
  Höhenwechsel fliegt der Folger wie bisher.
- JUnit `FlightTimeEstimateTest.smoothHairpinsWithAHeightChangeAreEstimatedWithTheStopTheFollowerMakes`: Start
  (0, 30, 0), Punkte (40, 30, 0), (30, 38, 2), (30, 38, 42) und (40, 30, 0), (30, 50, 2), (30, 50, 42), alle Klassen,
  5, 8 und 12 m/s, Punktmasse gegen Schätzung, Toleranz 10 %. Vorher −7,9 % bis −19,7 % (Tour 8 m/s: 24,10 s geflogen,
  19,64 s geschätzt) bzw. −10,0 % bis −18,5 %, nachher −5,4 % bis −6,6 % bzw. −4,8 % bis −6,5 %. Dieselbe Haarnadel mit
  8 m Sinken statt Steigen: vorher −12,8 % bis −22,1 %, nachher −5,4 % bis −6,6 %.
- JUnit `CurvedTurnTest.arcCornerAndPathShareTheHairpinDecision`: An Haarnadeln über 150° (flach, mit 8 m und 20 m
  Steigen, mit einem Steigbein von waagrecht 1,5 m) fehlen Bogen und Ecke, und die Bahnvorschau geht genau durch den
  Punkt. Bei 149° gibt es beides. Ein Bein waagrecht unter 1 m bekommt eine Ecke, steil hinab und gleich wieder hinauf keine. Vorher rot
  (Ecke bei 8 m Steigen mit 0,71 m/s).
- Modus eckig: Die Schätzung hat für jedes Bein Reisetempo plus Anfahren und Bremsen gerechnet, auch wenn das Bein zu
  kurz ist, um das Reisetempo zu erreichen (bei 12 m/s braucht das 96 m, bei 8 m/s 43 m). Jetzt rechnet sie dann wie
  im Modus weich nur Anfahren und Bremsen (`FlightTimeEstimate#ramp`). Beine, die das Reisetempo erreichen oder vom
  Steigen begrenzt sind, rechnet sie wie bisher. Dasselbe gilt für den Rückflug zum Start und im Modus weich für Beine,
  die waagrecht unter 1 m lang sind; Bögen und die Beine dazwischen rechnet der Modus weich wie bisher.
- JUnit `FlightTimeEstimateTest.sharpMissionsWithShortLegsAreEstimatedLikeTheyAreFlown`: Haarnadel mit 3-m-Bein
  (40, 30, 0), (37, 30, 0), (37, 30, 40), dazu die Haarnadel aus AUTO54-2 und die beiden Missionen oben, alle eckig,
  alle Klassen, 5, 8 und 12 m/s, Toleranz 10 %. Vorher −6,3 % bis +22,2 % (3-m-Bein bei 12 m/s), nachher −7,2 % bis
  +7,0 %.
- Weiter offen: steile oder senkrechte Beine (waagrecht unter 1 m), siehe AUTO54-2. Mit einem 15-m-Steigbein, das
  waagrecht 0,5 m lang ist ((40, 30, 0), (40,5, 45, 0), (0, 45, 0)), liegt die Schätzung weich bei −30,9 % bis −43,3 %
  (unverändert) und eckig bei −13,4 % bis −24,6 % (vorher −7,9 % bis −23,7 %: Bei 12 m/s haben die zu lang geschätzten
  flachen Beine das zu kurz geschätzte Steigbein ausgeglichen).

Befund AUTO55-1 (Gegenprüfung G-RC3C-1): Seit RC3C-1 rechnet die Schätzung im Modus eckig Beine, die zu kurz für das
Reisetempo sind, nur mit Anfahren und Bremsen. Der Halt am Punkt fehlte dabei: Der Folger zählt einen Punkt erst als
erreicht, wenn die Drohne höchstens 0,5 m entfernt (`WaypointFollower#ARRIVE_RADIUS`) und langsamer als 0,5 m/s ist,
und nach dem Bremsen schwingt sie bis dahin noch ein. In der Punktmasse dauert jedes Bein dadurch etwa 0,5 bis 0,6 s
länger als Anfahren und Bremsen über die ganze Länge. Bei vielen kurzen Beinen (Quadrat, Zickzack, Fotoraster)
summierte sich das auf über 10 %, im Standardfall (eckig, 5 m/s) schlechter als vor RC3C-1.

- Fix: An jedem Halt rechnet `FlightTimeEstimate` Anfahren und Bremsen bis 0,5 m vor dem Punkt und dazu
  `HALT_SECONDS` (0,75 s) für das Einschwingen. Gemessen an einzelnen Beinen von 2 bis 20 m sind es 0,56 bis 0,79 s.
  Wird die Drohne auf dem Bein nicht schneller als `HALT_FULL_SPEED` (1,2 m/s, Beine um 1 m), zählt davon nur der
  Anteil ihres Spitzentempos. Das gilt für jedes Bein im Modus eckig, für den Rückflug zum Start und im Modus weich für
  jeden Halt (Warten, Haarnadel, letzter Punkt), dem dort dasselbe fehlte. Senkrechte Beine (waagrecht unter 0,3 m,
  `WaypointFollower#MIN_HORIZONTAL`) schwingen nicht ein, der Folger prüft nur das waagrechte Tempo.
- Beine, die vom Steigen begrenzt sind (G-RC3C-1-2, dieselbe Stelle): Anfahren und Bremsen rechnet die Schätzung jetzt
  mit dem Tempo, das die Drohne dort fliegt (Länge durch Steigzeit), statt mit dem vollen Bremszuschlag des
  Reisetempos (bei 12 m/s 8 s je Bein).
- JUnit `FlightTimeEstimateTest.sharpMissionsWithManyShortLegsAreEstimatedWithTheHaltAtEveryPoint`: Start (0, 30, 0),
  Quadrat (L, 30, 0), (L, 30, L), (0, 30, L), (0, 30, 0) mit L = 3, 5 und 8 m, Zickzack (5, 30, 0), (5, 30, 5),
  (10, 30, 5), (10, 30, 10), (15, 30, 10), alle eckig, alle Klassen, 5, 8 und 12 m/s, Toleranz 5 %. Vorher −15,3 %,
  −13,7 % (16,92 s geflogen, 14,61 s geschätzt), −10,6 % und −14,4 %, nachher −0,2 %, −0,4 % (16,86 s geschätzt),
  +1,1 % und −1,2 %.
- JUnit `FlightTimeEstimateTest.photoGridsWithWaitsAreEstimatedWithTheHaltAtEveryPoint`: Fotoraster 5 m mit 2 s Warten
  an jedem Punkt, eckig und weich, Toleranz 5 %. Vorher je −9,5 %, nachher je −0,5 %.
- JUnit `FlightTimeEstimateTest.sharpLegsLimitedByClimbingAreEstimatedWithTheSpeedTheyAreFlown`: (3, 40, 0), (6, 30, 0)
  eckig, Toleranz 10 %. Vorher −7,4 % bis +72,5 % (Tour 12 m/s: 11,40 s geflogen, 19,67 s geschätzt), nachher +3,5 %
  bis +6,5 %.
- JUnit `FlightTimeEstimateTest.oneMetreLegsGetAShorterHaltBecauseTheDroneIsSlow`: Quadrat und Zickzack wie oben mit
  1-m-Beinen, Toleranz 10 %. Vorher −9,0 % und +2,7 %, nachher −5,5 % und +6,7 % (mit vollem Einschwingen +6,1 % und
  +19,8 %).
- Die übrigen Missionen der Tests: eckige Haarnadeln aus RC3C-1 vorher −7,2 % bis +7,0 %, nachher −0,1 % bis +1,9 %;
  weiche Haarnadeln vorher −6,6 % bis −3,8 %, nachher −1,1 % bis +0,4 %; `curves()` vorher −1,1 % bis +0,4 %, nachher
  +0,8 % bis +3,3 %; `previewMatchesAFlownMissionRoughly` vorher −7,4 % bis −5,0 %, nachher −2,3 % bis −1,0 %.
  `missionPreviewSumsLengthTimeAndCharge` erwartet für den senkrechten 60-m-Steigflug 0,5 m weniger Weg bis zum Halt.
- Gegenprobe mit den echten Flügen aus `MissionTimerReplayTest` (11 eckige Spuren auf Fabric und NeoForge, 49 Beine
  ohne Warten): Im Spiel dauern die Beine noch länger als in der Punktmasse. Geschätzt sind jetzt 414,9 s statt 384,8 s
  für 467,9 s Flug; Beine unter 8 m liegen höchstens 0,63 s daneben (vorher 0,80 s, beide Male das senkrechte
  6-m-Bein der Kino). Selbsttest 553 (weich, Tour, 8 m/s) auf Fabric und NeoForge: 28,30 s geflogen, Schätzung
  26,78 s (vor dem Fix 28,10 s und 26,09 s).
- Weiter offen: Das 15-m-Steigbein von oben liegt jetzt eckig bei −18,6 % bis −20,4 % und weich bei −28,9 % bis
  −41,0 %. Einen senkrechten 60-m-Steigflug schätzt die Vorschau um +3,6 % bis +13,4 % zu lang (vorher +8,8 % bis
  +39,0 %).

Befund RC3C3-1 (Gegenprüfung G-AUTO55-1, Vorgabe: Schätzung in allen Klassen und bei 1 bis 12 m/s höchstens 10 % neben
dem Flug): Seit AUTO55-1 lag die Schätzung an steilen Beinen weiter daneben als vorher.

- Ursache 1 (G-AUTO55-1-1): Im Modus eckig rechnete die Schätzung steile Beine räumlich, mit 1,5 m/s² Anfahren und
  Bremsen, und gab jedem Bein mit mindestens 0,3 m waagrecht das volle Einschwingen von 0,75 s. Der Folger fährt aber
  waagrecht mit 1,5 m/s² an (räumlich also schneller), bremst räumlich mit 1,5 m/s² und prüft am Punkt nur das
  waagrechte Tempo. (2, 35, 2), (4, 30, 4) lag dadurch bei +17,5 % (7,64 s geflogen, 8,98 s geschätzt).
- Ursache 2 (G-AUTO55-1-2): Im Modus weich zog die Schätzung den räumlichen Ankunftsradius von 0,5 m von der
  waagrechten Strecke ab und bremste waagrecht mit 1,5 m/s². Auf einer Geraden mit Steigung s bremst der Folger
  waagrecht aber nur mit 1,5/√(1+s²) m/s². Kommt die Drohne aus einem Bogen, ist sie dort oft zu schnell, um vor dem
  Punkt zu halten, und braucht länger. (3, 40, 0), (6, 30, 0) lag bei −10,2 % (1 m/s) bis −23,2 % (Kino 8 m/s: 6,49 s
  geflogen, 4,99 s geschätzt).
- Fix (`FlightTimeEstimate#halt`, `#ramp`, `#leg`, `#curved`): Die Schätzung rechnet jedes Bein waagrecht, so wie der
  Folger es fliegt:
  - Anfahren mit `RAMP_ACCEL`, Bremsen mit `BRAKE_ACCEL/√(1+s²)`.
  - Reisetempo waagrecht mit Steig- und Sinkgrenze (`CurvedTurn#legSpeed`).
  - Ankunftsradius auf die Waagrechte projiziert (`ARRIVE_RADIUS/√(1+s²)`).
  - Das Einschwingen richtet sich nach dem räumlichen Spitzentempo.
  - Ab 2,5 m/s Steig- oder Sinktempo (`VERTICAL_SETTLE_SPEED`) kommt bis zu `HALT_SECONDS` für das Nachziehen der Höhe
    dazu, voll ab 5 m/s. Ohne diesen Zuschlag kamen einzelne eckige Beine mit 4,5 m/s Steigtempo und mehr in der
    Punktmasse bis zu 1,1 s später an als geschätzt, weil die Höhe hinter der Strecke zurückbleibt.
  - Ist die Drohne an einer steilen letzten Geraden nach einem Bogen zu schnell, rechnet die Schätzung gleichmäßiges
    Bremsen über die Gerade. Käme die Drohne dabei waagrecht über den Punkt hinaus (Tempo × `HALT_SECONDS` mindestens
    die Gerade), gilt der Punkt für den Folger schon im Abstand bis 1 m als erreicht. Das Nachziehen der Höhe entfällt
    dann.
  - Flache Beine rechnen unverändert.
- G-RC3C-1-2 (dieselbe Stelle): Seit AUTO55-1 bremsen vom Steigen begrenzte eckige Beine mit dem geflogenen Tempo.
  Jetzt steht das ausdrücklich in der Rechnung: Gebremst wird vom erreichten waagrechten Tempo mit der waagrechten
  Bremsbeschleunigung.
- JUnit in `FlightTimeEstimateTest`, Punktmasse gegen Schätzung, alle Klassen, Toleranz 10 %. Alle vier Tests sind mit
  der Schätzung von main rot.

  | Test | Missionen | vorher | nachher |
  |---|---|---|---|
  | `steepSharpLegsSettleOnlyAsLongAsTheFollowerNeeds` | (2, 35, 2), (4, 30, 4) und (3, 40, 0), (6, 30, 0), eckig, 1, 2, 3, 5, 8 und 12 m/s | −0,1 % bis +17,5 % bzw. +0,1 % bis +10,7 % | −2,7 % bis +6,8 % bzw. −5,2 % bis +3,3 % |
  | `steepSmoothLegsStopWithTheArrivalRadiusOnTheGround` | dieselben, weich | −12,3 % bis −5,0 % bzw. −23,2 % bis −10,2 % | −5,0 % bis +4,0 % bzw. −2,3 % bis +5,1 % |
  | `sharpLegsLimitedByClimbingBrakeFromTheHorizontalSpeedTheyReach` | dieselben und (10, 50, 0), (20, 30, 0) sowie (20, 50, 0), (40, 30, 0), eckig, 8 und 12 m/s | −0,1 % bis +17,5 % | −3,3 % bis +6,8 % |
  | `reviewerScenariosStayWithinTenPercentInEveryClassAndSpeed` | Quadrat 3, 5 und 8 m, Zickzack 5 m und beide steilen Missionen, eckig und weich, 1 bis 12 m/s | −23,2 % bis +17,5 % | −5,2 % bis +6,8 % |

  Quadrat und Zickzack bleiben eckig und weich unverändert bei −1,2 % bis +3,5 %. Die übrigen Tests der Klasse liegen
  fast wie vorher: eckige Haarnadeln −0,2 % bis +1,1 % (vorher −0,1 % bis +1,9 %), (3, 40, 0), (6, 30, 0) eckig bei 5
  bis 12 m/s −3,3 % bis −2,1 % (vorher +3,5 % bis +6,5 %), alle anderen unverändert.
- Erweiterte Messung (Wegwerf-Tests, nicht im Repo), alle Klassen, 1 bis 12 m/s:
  - 990 einzelne eckige Beine, waagrecht 2 bis 20 m, Höhenwechsel bis ±20 m: vorher −2,2 % bis +19,1 %, nachher
    −6,8 % bis +6,5 %.
  - Zwei Beine mit 2 bis 10 m waagrecht und 3 bis 20 m Höhe, eckig (hoch und runter, Kurve, weiter steigen): vorher
    −2,2 % bis +20,9 %, nachher −6,8 % bis +8,2 %.
  - Dieselben weich, hoch und runter: vorher −43,2 % bis +8,6 %, nachher −10,3 % bis +8,7 %.
- Gegenprobe mit den echten Flügen aus `MissionTimerReplayTest` (11 eckige Spuren auf Fabric und NeoForge, 54 Beine
  nach dem Start): 527,3 s geflogen, Schätzung vorher 466,6 s, nachher 465,4 s. Im Spiel sind die Beine kaum steil;
  dass sie dort länger dauern als in der Punktmasse, gehört nicht zu diesem Befund.
- Selbsttests auf Fabric und NeoForge: 9–12, 27–29, 320–322, 340–345 und 553 PASS (553 weich, Tour, 8 m/s: 28,30 s
  bzw. 28,25 s geflogen, Schätzung 26,77 s bzw. 26,78 s). 386, 388 und 390 sind mit Propwash 0.6.0-rc.2 weiter rot wie
  seit AUTO54-1 (`NOT_READY`, die Kino startet nicht vom Boden). Propwash hat das mit RC3P-4 behoben, das
  veröffentlichte rc.2 enthält den Fix aber noch nicht. Gegen einen Propwash-Build mit dem Fix (Integrationsstand vom
  04.11.) sind 386, 388 und 390 auf beiden Loadern PASS, größte Abweichung des Timers Fabric +0,31 s, +0,43 s und
  +0,22 s, NeoForge −0,28 s, +0,36 s und +0,12 s. Der Missions-Timer (`MissionClock`) rechnet ohne die
  Flugzeit-Schätzung, der Fix ändert ihn nicht.
- Weiter offen (gemessen, nicht in den Szenarien des Prüfers):
  - Im Modus weich steile Kurven mit kurzen Beinen, z. B. 90° nach 2 m waagrecht und 10 bis 20 m Steigen: bis
    −22,8 % (unverändert).
  - Gerade weiter steigende Missionen mit Zwischenpunkt (weich): Bei Steigung ab 2 und 8 oder 12 m/s bis −17,8 %
    (vorher bis −45,4 %). Mit 2 bis 3 m kurzen Beinen und 3 bis 5 m Steigen bis +13,3 % (vorher bis +11,8 %).
    Dort erreicht die Drohne den Punkt, ohne an der Kurve die Höhe umzukehren, schneller als gerechnet.
  - Das 15-m-Steigbein von oben (waagrecht 0,5 m) liegt bei 5 bis 12 m/s eckig bei −20,8 % bis −22,7 % (vorher
    −18,6 % bis −20,4 %) und weich unverändert bei −28,9 % bis −41,0 %. Der Folger schleicht dort waagrecht auf wenige
    Zentimeter an den Punkt heran, weil die Höhe an den waagrechten Fortschritt gekoppelt ist. Das bildet die Schätzung
    nicht ab.
  - Senkrechter 60-m-Steigflug: bei 1 bis 12 m/s unverändert +0,8 % bis +13,4 %.

Befund AUTO57-1 (Gegenprüfung G-RC3C3-1): Seit RC3C3-1 hat die Schätzung im Modus weich kurze oder steile letzte
Geraden nach einem Bogen oder Zwischenpunkt bis zu 32 % zu lang gerechnet, wo sie vorher unter 10 % lag. Im Raster des
Prüfers waren 220 Zeilen neu über 10 %.

- Ursache 1 (`FlightTimeEstimate#halt` nach einem Bogen): Kommt die Drohne zu schnell aus dem Bogen, um mit dem Bremsen
  des Folgers vor dem Punkt zu halten, rechnete die Schätzung gleichmäßiges Bremsen über die ganze Gerade
  (2 · Gerade / Tempo) und volles Einschwingen. War sie knapp langsamer, rechnete sie Bremsen bis zum Stand mit
  `BRAKE_ACCEL/√(1+s²)` und volles Einschwingen. Der Folger zählt einen Punkt aber schon als erreicht, sobald die Drohne
  ihm bis auf 0,2 m nahekommt (`ARRIVE_RADIUS` × 0,4 in `WaypointFollower#fly`), egal wie schnell sie ist. Mit Schwung
  aus dem Bogen fliegt sie in diesen Bereich hinein, statt vorher anzuhalten. Beispiel (2, 33, 0), (2, 36, 2), Tour
  5 m/s: Die Gerade nach dem Bogen ist 1,10 m lang, die Drohne kommt mit 1,41 m/s, Steigung 1,5. Geflogen sind ab dem
  Bogenende 0,92 s, geschätzt waren 2,31 s.
- Ursache 2 (G-RC3C3-1-2, `FlightTimeEstimate#curved`, dieselbe Rechnung): Reicht eine kurze Gerade nicht, um mit
  `RAMP_ACCEL` das Bogentempo zu erreichen, rechnete `ramp()` trotzdem die volle Anfahrzeit bis zum Bogentempo. Das
  nächste Bein begann dann mit vollem Tempo. Beispiel (2, 30, 0), (12, 40, 0), Tour 8 m/s: Für die erste Gerade von
  1,10 m waren 3,48 s gerechnet (Anfahren von 0 auf 5,22 m/s). Die Drohne erreicht dort aber nur 1,82 m/s und braucht
  1,21 s. Zusammen mit dem Höhen-Zuschlag an der langen Schlussgeraden ergab das +17,3 %. Der Zuschlag selbst passt dort:
  Die Schlussgerade ist ab dem Bogenende 4,94 s geflogen und 4,98 s geschätzt.
- Fix:
  - Nach einem Bogen rechnet die Schätzung im Modus weich mit dem Vorbeiflug am Punkt, wenn drei Bedingungen gelten:
    - Die Drohne kommt mit dem räumlichen Bremsen des Folgers (`BRAKE_ACCEL`) noch bis auf 0,2 m an den Punkt heran.
    - Mit ihrem Tempo aus dem Bogen wäre sie in höchstens 1 s am Punkt.
    - Sie steigt oder sinkt dabei langsamer als `VERTICAL_SETTLE_SPEED` (2,5 m/s).

    Dann zählt nur das Bremsen bis zu diesem Abstand plus `HALT_SECONDS` Einschwingen.
  - Reicht eine Gerade nicht für das Bogentempo, fährt die Drohne in der Rechnung mit dem erreichten Tempo in den Bogen.
    Dort beschleunigt sie weiter mit `RAMP_ACCEL`, und die nächste Gerade beginnt mit dem Tempo am Bogenende.
  - Unverändert bleiben:
    - lange Geraden und steile Geraden ab 2,5 m/s Steigtempo (Einschwingen und Nachziehen der Höhe wie seit RC3C3-1)
    - der Modus eckig: alle gemessenen eckigen Zeilen sind gleich wie vor dem Fix
- JUnit in `FlightTimeEstimateTest`, Punktmasse gegen Schätzung, alle Klassen, Toleranz 10 %. Beide Tests sind mit der
  Schätzung von main rot.

  | Test | Missionen | vorher | nachher |
  |---|---|---|---|
  | `shortSmoothLastLegsAfterACurveAreNotEstimatedTooLong` | (2, 33, 0), (2, 36, 2) und (5, 20, 0), (5, 17, 2), weich, 3, 5, 8 und 12 m/s | +12,2 % bis +19,5 % | +2,1 % bis +7,4 % |
  | `smoothLegsAfterAShortFirstLegStartWithTheSpeedTheDroneReaches` | (2, 30, 0), (12, 40, 0); (2, 33, 0), (12, 43, 0); (2, 30, 0), (22, 30, 0); (5, 30, 0), (15, 30, 0); Slalom (5, 30, 0), (15, 30, 2), (25, 30, 0), (35, 30, 2); weich, 1, 2, 3, 5, 8 und 12 m/s | −4,7 % bis +29,8 % | −4,7 % bis +1,3 % |

  Von den übrigen Tests der Klasse ändert sich nur `smoothTurnsAreEstimatedLikeTheyAreFlown` (`curves()`): vorher +0,8 %
  bis +3,3 %, nachher +0,3 % bis +0,8 %. Haarnadeln, Fotoraster, die steilen Missionen aus RC3C3-1 (weich −5,0 % bis
  +5,1 %) und alle eckigen Tests bleiben gleich.
- Erweiterte Messung (Wegwerf-Tests, nicht im Repo): Start (0, 30, 0), weich, alle Klassen, 1, 2, 3, 4, 5, 6, 8, 10 und
  12 m/s. Raster mit 800 Missionen aus zwei Beinen (waagrecht 2, 5, 10 und 20 m, Höhenwechsel −10, −5, 0, 5 und 10 m,
  90° und gerade) und 50 steigenden bzw. sägenden Quadraten (Seite 2 bis 10 m, 1 bis 8 m je Ecke), zusammen
  22 950 Zeilen:

  | Zeilen | vor RC3C3-1 (f1f88ec) | RC3C3-1 (dd05bf3) | jetzt |
  |---|---|---|---|
  | über 10 % | 6131 | 1573 | 761 |
  | neu über 10 % gegenüber f1f88ec | – | 254 | 48 |
  | wieder unter 10 % gegenüber f1f88ec | – | 4812 | 5418 |

  - Gegenüber dd05bf3 sind 827 Zeilen neu unter 10 % und 15 neu darüber (siehe unten).
  - Mit Einzelbeinen, Zickzack und Treppen mit Steigen, den Szenarien des Prüfers und den Test-Missionen sind es 29 430
    Zeilen, davon über 10 %: f1f88ec 8925, dd05bf3 2027, jetzt 1023.
  - Genau dieselbe Schätzung wie vor dem Fix ergeben: Missionen mit Ende START oder LAND, Warten nach einem Bogen,
    Fotoraster mit Steigen, lange Missionen mit sechs Punkten, enge Spiralen und flache Zickzacks mit kurzen Beinen.
- Gegenproben:
  - Aufgenommene weiche Flüge aus `MissionTimerReplayTest` (10 Spuren): Schätzung unverändert, 378,5 s für 457,0 s Flug.
  - Selbsttest 553 (weich, Tour, 8 m/s) auf Fabric und NeoForge: Schätzung jetzt 26,27 s (vorher 26,77 s) für 28,25 s
    Flug. Dieselbe Mission in der Punktmasse: 26,17 s geflogen, Schätzung jetzt 26,27 s (vorher 26,78 s). Im Spiel
    dauern die Beine länger als in der Punktmasse (siehe AUTO55-1).
- Weiter offen (gemessen):
  - Steile letzte Geraden nach einem Bogen mit Steigtempo ab 2,5 m/s: In der Punktmasse entscheiden wenige Zentimeter
    Höhe beim Abbremsen, ob die Drohne in den Kreis von 0,5 m kommt oder davor stehen bleibt. Im zweiten Fall kriecht
    sie mit der Höhe noch 2 bis 3 s nach. Dieselbe Mission (2, 40, 0), (2, 50, 2) dauert deshalb je nach Klasse und
    Tempo sehr verschieden: Kino 8 m/s 4,54 s, Tour 8 m/s 7,47 s, Kino 5 m/s 7,18 s. Die Schätzung rechnet dort wie
    seit RC3C3-1 mit dem Nachkriechen. Offen bleiben:
    - (2, 40, 0), (2, 50, 2) Kino 6 bis 12 m/s: +31,9 % bzw. +32,1 % (6,00 s geschätzt für 4,54 s), alle anderen
      Klassen und Tempi −8,5 % bis +4,6 %
    - (3, 38, 0), (3, 46, 3), (0, 54, 3), (0, 62, 0) Kino 5 bis 12 m/s: +17,1 % bis +17,2 %, alle anderen −6,3 % bis
      +3,2 %
    - im Raster 48 Zeilen, z. B. 90° nach 2 m mit je 5 m Steigen Tour 4 bis 12 m/s +19,8 % bis +20,6 %, steigendes
      Quadrat 2 m mit 5 m je Ecke alle Klassen 4 bis 12 m/s +11,6 % bis +14,4 %

    Eine Regel nach der Steigreserve der Klasse holt einen Teil davon zurück, etwa (2, 40, 0), (2, 50, 2). Sie macht
    aber andere Missionen bis −23 % zu kurz und ist deshalb verworfen.
  - (2, 33, 0), (2, 36, 2) bei 2 m/s: unverändert +11,7 % (vor RC3C3-1 −1,2 %). Die Drohne kommt dort zu langsam aus dem
    Bogen für den Vorbeiflug. Der Folger zählt den Punkt aber schon, sobald sie im Kreis von 0,5 m langsamer als 0,5 m/s
    ist. Die Schätzung rechnet dort weiter Bremsen bis zum Stand mit `BRAKE_ACCEL/√(1+s²)` und volles Einschwingen.
  - 15 Zeilen neu unter −10 %: zwei steile Beine, bei denen die erste Gerade aus dem Stand zu kurz für das Bogentempo
    ist. Beispiel (2, 35, 0), (7, 40, 0) Tour 6 bis 12 m/s: vorher −6,6 % bis −8,4 %, jetzt −12,1 % bis −12,3 %. Beim
    steilen Anfahren aus dem Stand bleibt die Höhe zurück, und die Drohne kommt langsamer in Gang. Die alte Rechnung hat
    das mit der zu langen Anfahrzeit zufällig ausgeglichen.

## 41. Kamera-Drohnen ohne Antennen, Zubehör vorne bündig am Rahmen (Owner 04.11., rc.3, RC3C2-1)

Befund RC3C2-1: Kamera-Drohnen sollen keine Antennen haben. Propwash zeichnete an jeder Drohne mit `cam_link_compact`
oder `cam_link_pro` abgeleitete VTX-Antennen hinten am Rahmen (Anzahl, Länge und Kappe aus den Videodaten), und die
Werkbank bot den Lack-Platz „Antennenspitzen“ an, weil `drone_paint.json` für beide Funkstrecken eine Lack-Ebene im
Kanal `antenna` führte. Zubehör vorne hing am `cine_frame` 4,5 mm unter der Nase (Kombi-Lauf von Propwashs RC3P-5).
Seit Propwash 0.6.0-dev.13 (`VideoAntenna.none()`, Teile-JSON `"antenna": "none"`):

- `cam_link_compact.json` und `cam_link_pro.json` haben `"antenna": "none"`: Kompakt, Tour, Kino und jede frei gebaute
  Drohne mit einem Kamera-Funk (auch auf Rahmen von Propwash oder anderen Add-ons) zeigen keine Propwash-Antennen und
  keinen Halter. Masse, Reichweite und Videolink bleiben. Die zwei Stummelantennen im Modell des Funkmoduls
  (`antenna`, `antenna_tee`) lässt Propwash an der Drohne aus; sie bleiben nur im Inventar-Symbol.
- `drone_paint.json` ohne Kanal `antenna` (keine Lack-Ebene, nicht in `channels` und `slots`). Die Lack-Varianten der
  Funkstrecken behalten drei Tints, der dritte bleibt beim Standard (Texturfarbe). Der Generator schreibt dasselbe
  (`tools/assets/camstyle/writer.py`, `NO_PAINT`).
- Anker vorne am `cine_frame` (0; −35,5; −150) statt (0; −40; −150): Werfer und Gimbals sitzen bündig an der Nase.
  `compact_fold_frame` (0; −12; −70) und `tour_fold_frame` (0; −18; −106) waren schon bündig.

Tests:

- JUnit `part.NoAntennaTest`: beide Funkstrecken mit `"antenna": "none"` und `VideoAntenna#isNone`, alle drei Presets
  ohne abgeleitete Antennen, kein Kanal `antenna` in `drone_paint.json`, die Tint-Nummern der Lack-Varianten bleiben.
  Vor dem Fix 3 von 4 rot.
- JUnit `part.FrontAnchorFitTest`: Quader 62, 34, 12, 44 und 30 mm sowie die drei Gimbals am Anker vorne jedes
  Kamera-Rahmens gegen das gezeichnete Rahmenmodell (Render-Definition samt `transformation`); Abstand höchstens 1 mm,
  Überschneidung höchstens 2 mm wie in Propwashs Selbsttest „addon-api-17: Zubehör am Rahmen“. Vor dem Fix rot:
  `cine_frame` 4,5 mm bei allen acht Quadern, nachher 0,0 mm.
- Selbsttest 571 `teile: Kamera-Drohnen ohne Antennen, Werfer vorne bündig am Rahmen` (Fabric und NeoForge): setzt
  Kompakt, Tour und Kino aus den Presets und `freestyle_5` mit `cam_link_compact`, alle mit Werfer vorne
  (`mobdefense:dispenser_mount`, wenn Mob Defense als Begleiter geladen ist, sonst `camdrones_example:dropper`), auf
  Säulen. Prüfbilder in `<loader>/runs/selftest-client/rc3c2/`: jede Drohne von der Seite (`no_antenna_<preset>.png`),
  die Nase der drei Kamera-Drohnen (`front_gap_<preset>.png`) und alle vier (`no_antenna_all.png`). Geprüft wird im
  laufenden Spiel: `VideoAntenna#isNone` beider Funkstrecken, `camdrones:drone_paint.json` ohne Kanal `antenna` und der
  Sitz von Werfer und Gimbals vorne (`FrontFitProbe`, dieselbe Messung wie im JUnit-Test, aus den geladenen
  Ressourcen). Vorher FAIL (beide Funkstrecken „abgeleitet“, zwei Lack-Ebenen `antenna`, `cine_frame` 4,5 mm), nachher
  Fabric und NeoForge PASS mit 0,0 mm an allen drei Rahmen, ebenso mit Mob Defense als Begleiter auf Fabric. Mit
  `-PcamdronesScreenshots` zusätzlich `docs/screenshots/cam_no_antenna_*.png` und `cam_front_gap_*.png`; eingecheckt
  sind die Seitenansichten von Kompakt, Tour und Kino und die Nase der Kino.

## 42. Generator legt die Nase auf den Anker vorne (Gegenprüfung G-RC3C2-1, AUTO59-1)

Befund AUTO59-1: Der Fix zu RC3C2-1 hatte nur den Anker vorne am `cine_frame` auf (0; −35,5; −150) gesetzt. Der
Asset-Generator legte den Rumpfboden auf die Höhe dieses Ankers (`tools/assets/camstyle/frame.py`,
`bottom = min(front[1], …)`), die Unterkante der Nase liegt aber durch die Fase 4,5 mm über dem Boden. Ein neu
erzeugtes Modell hob den Rumpf deshalb um 4,5 mm, Zubehör vorne hing wieder 4,5 mm unter der Nase, und
`style_generate.py --verify` meldete sechs abweichende Dateien des `cine_frame`.

- `frame.params` leitet den Rumpfboden aus dem Anker ab: Die Unterseite des Rumpfs über dem Anker liegt auf seiner
  Höhe. Liegt der Anker unter dem Kinn (`compact_fold_frame`, `tour_fold_frame`), ist das der Boden selbst; unter der
  vorderen Nase (`cine_frame`) liegt sie eine halbe Fase über dem Boden, an der Nasenspitze eine ganze. Die Fase
  (`chamfer`) ist dieselbe wie in `hull`. Am `cine_frame` ergibt das wieder den Boden bei −40 mm; Modelle, Texturen,
  Item-Symbole und Lack-Varianten bleiben byte-gleich.

Tests:

- Stilprüfung `camstyle/check.py` (`check_front_fit`): die Quader 62, 34, 12, 44 und 30 mm und alle Gimbals am Anker
  vorne jedes Kamera-Rahmens gegen das gezeichnete Modell samt `transformation`, gemessen wie JUnit
  `part.FrontAnchorFitTest` (Abstand höchstens 1 mm, Überschneidung höchstens 2 mm). Sie läuft bei jedem
  Generator-Lauf; `--verify` misst zusätzlich die frisch erzeugten Modelle. Vor dem Fix rot: „frisch erzeugt:
  frame/cine_frame: Zubehör vorne 62×58×64 nicht am Rahmen: Abstand 4.5 mm …“ bei allen acht Quadern und sechs
  abweichende `cine_frame`-Dateien; nachher keine.
- JUnit `part.FrontAnchorFitTest` und Selbsttest 571 bleiben grün, auch nach `style_generate.py --write`.

## 43. Ablehnung mit Grund und Drohnennamen, Kino startet vom Boden (Owner 04.11., rc.3, RC3C2-2)

Befund RC3C2-2: Der Flugplaner meldete „95C60636 is not ready (battery, GPS fix or not in the air)“. Die Kino-Drohne
(6S) und Drohnen mit 360°-Gimbal starteten nicht vom Boden.

- Ursache Start (Propwash, §40): Bis 0.6.0-rc.2 nahm Propwash einen Start vom Boden nur an, wenn unter der Hitbox
  weniger als 0,08 m frei waren. Seit 0.6.0-dev.13 (F-RC3P-4) heißt „am Boden“: Kontakt der ganzen Kollisionsform
  (Hitbox, Bodenpunkte, Zubehör samt Gimbal, Standbeine) unter 2 cm und Ruhe unter 0,25 m/s. Das Add-on baut seit
  4d193a8 gegen dev.13. Selbsttest 554 ist freigeschaltet.
- Ursache Meldung (Add-on): Bei `NOT_READY` zeigte das Add-on nur den allgemeinen Text, der Akku, GPS-Fix und „nicht in
  der Luft“ rät. Eine Drohne ohne eigenen Namen hieß darin nach den ersten acht Zeichen ihrer Seriennummer.
- Fix Grund: Ergibt `autonomy().start` `NOT_READY`, fragt `AutonomyServer#startFlight` mit denselben Optionen
  `AutonomyAccess#notReadyReason` ab. Den Grund schickt `AutonomyServer#refusal` als Detail der Statusmeldung.

  | Propwash `AutonomyNotReady` | Schlüssel `camdrones.autonomy.status.…` | Text (EN) |
  |---|---|---|
  | `NOT_ON_GROUND` | `not_ready.not_on_ground` | „… is not resting on the ground – let it land, then press Start“ |
  | `NO_TAKEOFF` | `not_ready.no_takeoff` | „… stands on the ground, but the flight came without a take-off – press Start again“ |
  | `NO_BATTERY` (Übergabe in der Luft) | `not_ready.no_battery` | „… has no battery for a flight without pilot“ |
  | `REMOVED` | `not_ready.removed` | „… is gone – picked up or destroyed“ |

  Ein unbekannter Grund (`switch` mit `default`), ein leerer Grund oder eine Ausnahme ergeben die allgemeine Meldung
  „… is not ready for a flight without pilot“. Akku und GPS-Fix prüft weiter das Add-on vor dem Start (`StartCheck`,
  unverändert, eigene Meldungen).
- Fix Name: Alle Meldungen der Flüge ohne Pilot nennen die Drohne mit ihrem eigenen Namen oder wie Propwash mit dem
  Kurznamen des Rahmens, z. B. „Kino 15″“ oder „Tour 9″“ (`DroneNames`). Das gilt für das Statuspaket, `/camdrones`,
  den Hinweis an Gefolgte und die Wegpunkt-Aufgaben.
  - Schlüssel: `<ns>.frame_short.<rahmen>`, für Propwash-Rahmen `propwash.content.frame_short.<rahmen>`. Fehlt der
    Kurzname bei fremden Rahmen, gilt `part.<ns>.<rahmen>`. Nur ohne bekannten Rahmen bleibt die ID.
  - Das Statuspaket ist unverändert. Sein Drohnen-Feld trägt den Namen oder den Sprachschlüssel des Rahmens, der
    Client übersetzt ihn.
  - Logs und die Auswahl im Befehl nennen weiter die ID.

Tests:

- JUnit `integration.autonomy.NotReadyMessageTest` (5 Tests):
  - eigener Schlüssel je Grund, Name vorn, Grund im Text (EN „ground“, „take-off“, „battery“, „gone“; DE „Boden“,
    „Start“, „Akku“, „nicht mehr da“);
  - die allgemeine Meldung rät nicht mehr;
  - Grund nur bei `NOT_READY`;
  - Name statt ID, Übersetzung am Client.

  Vorher 0 von 5 (Gerüst mit dem alten Verhalten, z. B. „expected: <camdrones.frame_short.cine_frame> but was:
  <95C60636>“), nachher 5 von 5.
- Selbsttest 557 `mission: Ablehnung nennt Drohnennamen und Grund (leerer Akku, nicht am Boden)`
  (`dev/client/NotReadyMessageSteps`, etwa 20 s):
  - Tour mit leerem Akku am Boden: erwartet `NOT_ARMABLE` mit `battery_empty` und „Tour 9″ cannot be armed: the battery
    is empty – charge it first“.
  - Kino fällt von einem 40 m hohen Pfeiler: erwartet `NOT_READY` mit `not_on_ground` und „Cine 15″ is not resting on
    the ground – let it land, then press Start“.
  - Mit `-PcamdronesScreenshots` je ein Bild auf Englisch und Deutsch: `docs/screenshots/mission_refused_battery_empty.png`
    und `mission_refused_not_on_ground.png` (`_de`).
  - Vorher (Fabric) FAIL: „Drohnenname „Tour 9″“ fehlt, Meldung nennt die ID 6BECAA62“. Nachher PASS auf Fabric und
    NeoForge.
- Selbsttest 554: gegen Propwash 0.6.0-rc.2 FAIL („cine: … keine Meldung MISSION_STARTED (bekommen: NOT_READY); cine
  Hitbox 0.162 m über dem Boden, onGround true, fliegt false“). Gegen dev.13 PASS auf Fabric und NeoForge (Compact
  0,039 m, Tour 0,059 m, Kino 0,162 m, jeweils `MISSION_STARTED` und Flug `camdrones:mission`).
- Selbsttest 558 `mission: weiche Kurven ohne Abbremsen – Kino` (`CurveSteps`): dieselbe Mission wie 553 mit der Kino.
  - Grenze: 85 % des geplanten Tempos im engsten Bogen (`CurvedTurn#plannedSpeed`), statt 70 % des Reisetempos wie
    bei 553. Die Kino fährt den 90°-Bogen mit ihrer Querbeschleunigung von 2,0 m/s² geplant mit 6,0 m/s, das sind
    75 % von 8 m/s (Tour 6,29 m/s). Die Grenze für die Kino liegt damit bei 5,10 m/s. Ein Halt am Wegpunkt wie vor
    SMOOTH1-1 (etwa 1,5 m/s im 90°-Bogen) bleibt rot.
  - Gemessen: 5,44 bis 6,04 m/s (68 bis 76 % von 8 m/s, 91 bis 101 % des Plans), Abweichung von der Bahn 0,62 bis
    0,65 m, 28,70 bis 28,85 s geflogen, Schätzung 26,74 s. Mit der alten Grenze von 70 % war ein Gruppenlauf auf
    NeoForge rot (5,44 m/s).
  - 553 (Tour) prüft unverändert gegen 70 % von 8 m/s (5,60 m/s). Beide nennen jetzt geplantes Tempo und Grenze.
- Selbsttests 386, 388 und 390 auf Fabric und NeoForge PASS. Größte Abweichung des Timers Fabric −0,32 s, +0,58 s und
  +0,23 s, NeoForge −0,27 s, +0,44 s und −0,13 s.
- `MissionTimerReplayTest`: Kino-Spur aus 390 neu aufgenommen (`cine-smooth-hover.csv`, `-neoforge.csv`), 23 von 23.
- Selbsttests 353 und 553 unverändert PASS; 353 nennt jetzt „Tour 9″“ statt der ID.
- Nach dem Rebase auf F-RC3C2-1: `:common:test` 921 grün, Build beider Loader grün. 353, 386, 388, 390, 553, 554, 557,
  558 und 601 auf Fabric und NeoForge je 10 von 10 PASS (mit Aufräumen), Server-Rauchtest beider Loader PASS.
- Gruppen der Flüge ohne Pilot (`mission:`, `follow:`, `poi:`, `auto:`, `automatik:`, `course-lock:`, `welt-regeln:`,
  `halten:`, `heim:`, `landen:`, 40 Schritte) vor dem Rebase: Fabric 39 von 40, NeoForge 38 von 40. Rot jeweils 351,
  auf NeoForge zusätzlich 558 mit der alten Grenze (siehe oben).
- Selbsttest 351 `mission: präzise Landung` war hier noch rot. Die Ursache lag im Test, nicht in Propwash (Korrektur aus
  der Gegenprüfung G-RC3C2-2, Fix in §44). Es gibt dazu keinen API-Befund an Propwash; F-RC3P-4 ist erledigt.
  - Der Test startete die Mission fest 10 Ticks nach dem Setzen. Die Tour hing da noch 0,15 m unter dem Rand der Grube
    über dem in den Boden gesetzten Landeplatz. Propwash meldete zu Recht `NOT_ON_GROUND`.
  - Die Tour sinkt danach in Stufen bis auf den Landeplatz (−0,816 m nach etwa 100 Ticks). Wartet der Test, bis sie
    ruhig liegt, startet die Mission auf Fabric und NeoForge.

## 44. Zustimmungsfrage mit Drohnennamen, präzise Landung, Log mit ID (Gegenprüfung G-RC3C2-2, RC3C4-1)

Befunde der Gegenprüfung zu §43:

- G-RC3C2-2-1 (mittel): Die Zustimmungsfrage bei Follow me auf einen anderen Spieler zeigte bei einer Drohne ohne
  eigenen Namen den rohen Schlüssel, z. B. „… with the camera drone camdrones.frame_short.tour_fold_frame …“.
- G-RC3C2-2-2 (mittel): Selbsttest 351 `mission: präzise Landung` war auf beiden Loadern rot (siehe Korrektur in §43).
- G-RC3C2-2-3 (niedrig): Das Log nach einem Schaden nannte den Sprachschlüssel statt der ID.

Ursachen und Fixes:

- Zustimmungsfrage: `AutonomyServer#follow` gab seit §43 den Namen als String an die Chat-Zeile und an das Paket
  `follow_consent` weiter. Nur das Statuspaket übersetzte ihn.
  - Jetzt nimmt der Server den Namen mit seiner Herkunft (`AutonomyServer#nameText`, wie der Hinweis an Gefolgte). Ein
    Kurzname des Rahmens wird ein übersetzbarer Text, ein eigener Name und die ID bleiben Klartext.
  - Die Chat-Zeile (`consentMessage`) trägt diesen Text, der Client übersetzt ihn.
  - Das Paket `follow_consent` trägt Name bzw. Schlüssel und neu das Feld „ist Schlüssel“ (`FollowConsent.Request#of`).
    Das Bestätigungsfenster baut den Text mit `AutonomyServer#consentQuestion` und übersetzt am Client.
  - Ein eigener Name, der wie ein Sprachschlüssel aussieht (z. B. „gui.yes“), wird beim Gefragten nicht übersetzt.
  - Das Paket ist damit nicht mehr mit dem Stand vor diesem Fix verträglich. Client und Server brauchen ohnehin dieselbe
    Version des Add-ons (Design §4.4).
- Selbsttest 351: Der Test wartet jetzt, bis die Tour ruhig liegt, statt fest 10 Ticks.
  - Gemessen am Server sinkt die Tour mit `onGround` true und Tempo 0 in Stufen von 0,08 m alle 10 Ticks vom Rand der
    Grube (−0,07 m) auf den Landeplatz (−0,816 m nach etwa 100 Ticks). Eine Stufe dauert höchstens 8 Ticks.
  - „Ruhig“ heißt: `onGround`, Tempo unter 0,05 m/s und Höhe 20 Proben lang (etwa 2 s) unverändert. Zeitlimit 30 s.
- Log: `CamCrash#onDamaged` nennt die Drohne mit `AutonomyServer#label` (eigener Name oder ID) wie die übrigen Logs.

Tests:

- JUnit `integration.autonomy.FollowConsentTest`: zwei neue Tests (Kurzname eigener und fremder Rahmen als übersetzbarer
  Text in Chat, Paket und Fenster; eigener Name, Namen wie „gui.yes“ und die ID als Klartext). Vorher 2 von 9 rot
  („Chat: Drohnenname als übersetzbarer Text statt des Schlüssels camdrones.frame_short.tour_fold_frame ==> expected:
  <Component> but was: <String>“), nachher 9 von 9. `AutonomyPacketsTest` prüft das Paket mit Schlüssel.
- Selbsttest 559 `follow: Zustimmungsfrage nennt die Drohne beim Kurznamen des Rahmens (EN und DE)`
  (`dev/client/ConsentNameSteps`, etwa 2 s):
  - Ein Gast mit eigener Tour fragt bei Regel „Spielern folgen“, ob seine Drohne dem Spieler folgen darf.
  - Fenster und Chat-Zeile müssen auf Englisch und Deutsch „Tour 9″“ nennen, ohne Sprachschlüssel und ohne ID. Danach
    Ablehnen mit der Meldung `CONSENT_REFUSED`.
  - Mit `-PcamdronesScreenshots` je ein Bild: `docs/screenshots/follow_consent_question.png` und `_de.png`.
  - Vorher (Fabric) FAIL: „Fenster ohne „Tour 9″“, Fenster zeigt den Sprachschlüssel, Chat ohne „Tour 9″“ …“.
    Nachher PASS auf Fabric und NeoForge.
- Selbsttest 351: vorher (Fabric) FAIL „Mission nicht geflogen: höchstens -0,15 m über dem Landeplatz (… NOT_READY)“,
  nachher PASS („liegt nach 6,95 s ruhig: -0.816 m zum Rand der Grube …; setzt 0,08 m neben der Mitte des Landeplatzes
  auf“).
- Selbsttest 325 `flug: Schaden am Server beendet die Mission ohne Pilot` prüft jetzt auch das Log (Hilfe
  `dev/client/LogProbe`). Vorher (Fabric) FAIL „Drohne camdrones.frame_short.tour_fold_frame beschädigt …“ statt der ID,
  nachher PASS („Drohne 7E0DB2D8 beschädigt (generic), Mission bzw. Flug beendet: LAND_STARTED“).
- `:common:test` 923 Tests grün, Build beider Loader grün.
- Gruppen der Flüge ohne Pilot (`mission:`, `follow:`, `poi:`, `auto:`, `automatik:`, `course-lock:`, `welt-regeln:`,
  `halten:`, `heim:`, `landen:`, 40 Schritte mit 559) dazu 325, 386, 388 und 390: Fabric 44 von 44, NeoForge 44 von 44
  PASS. 351 setzt 0,15 bzw. 0,14 m neben der Mitte auf, 554 und die Timer-Schritte unverändert grün (größte Abweichung
  Fabric −0,27 / +0,37 / +0,20 s, NeoForge −0,34 / +0,49 / −0,16 s).
- Server-Rauchtest und Beitrittstest `follow` auf Fabric und NeoForge PASS (Anfrage, Ablehnung, Annahme, Abstand,
  Welt-Regel aus).

## 45. Mehrere Wegpunkte auf einmal ändern, Werte eintippen (Owner 09.11., FB1C-3)

Befund: Im Flugplaner ließ sich immer nur ein Wegpunkt ändern. Werte gingen nur per Klick auf die linke oder rechte
Hälfte bzw. per Mausrad, eintippen ging nicht.

Ursache:

- `EditorState` kannte nur eine Auswahl (`Selection`), `stepWaypoint` änderte nur den gewählten Wegpunkt.
- Die Detailzeilen in `PlannerScreen` sind gezeichnet und keine Widgets. Ein Doppelklick auf einen Wert war nur ein
  zweiter Schritt.

Fix (Auslegung des Owners):

- Mehrfachauswahl in der Liste und auf der Karte: Strg/Cmd-Klick nimmt einen Wegpunkt dazu oder heraus (einer bleibt
  immer gewählt), Umschalt-Klick wählt den Bereich ab dem Anker (letzter Klick ohne Umschalt), Strg/Cmd+A alle
  Wegpunkte. Der zuletzt geklickte Wegpunkt führt. Liste und Karte heben alle gewählten hervor, der Titel heißt
  „3 Wegpunkte“.
- Gemischte Werte zeigt die Detailzeile als „–“. Ein Feld ist bedienbar, wenn es bei einem der gewählten Wegpunkte
  gilt (etwa *Richtung*, sobald einer fest blickt).
- Ohne Alt setzt ein Schritt alle gewählten auf denselben Wert: Wert des führenden Wegpunkts plus Schritt
  (`WaypointField#copy`). Mit gehaltener Alt-Taste ändert sich jeder um den Schritt (`stepWaypoint(…, each)`), jeder an
  seinen eigenen Grenzen. Ein einzelner Wegpunkt verhält sich wie bisher.
- Doppelklick auf den Wert (zwischen den Pfeilen, `EditorLayout#onValueText`) öffnet eine Texteingabe
  (`TextField` aus den freigegebenen Theme-Klassen). Der erste Klick des Doppelklicks wird zurückgenommen
  (`EditorState#revert`).
  - Die Texteingabe verhält sich wie Propwashs `NumberEditor` (`planner.model.ValueEntry`): Enter übernimmt, Esc bricht
    ab (der Editor bleibt offen), leere Eingabe bricht ab, eine ungültige Zahl bleibt rot offen.
  - Werte außerhalb der Grenzen werden auf die Grenze gesetzt. Die Statuszeile meldet „Wert auf … begrenzt“.
  - Die Grenzen sind dieselben wie beim Schritt: Höhe in der Welt, Tempo 0–20 m/s (0 = Grundtempo), Warten 0–300 s,
    Neigung bis zur Grenze der Klasse, Richtung läuft um, Takt 1–1200 Ticks.
  - Unter dem Feld steht ein Hinweis mit Bereich, „Für alle 3 gewählten Wegpunkte“ und den Tasten.
  - Tippbar sind Höhe, Tempo, Warten, Neigung, Richtung, Takt sowie Höhe und Radius eines Point of Interest. Der Wert
    gilt für alle gewählten Wegpunkte.
- Doppelklick auf eine Listenzeile zentriert weiter die Karte. Entf löscht alle gewählten Wegpunkte, ▲/▼ brauchen einen
  einzelnen.
- Das Protokoll bleibt. Gespeichert wird wie bisher die ganze Mission (`PlannerSave`).
- `NumberEditor` selbst steht nicht in der Liste der für Add-ons freigegebenen Klassen (Propwash `addon-guide.md` §16).
  Darum hat das Add-on eine eigene, gleich arbeitende Prüfung im Modell (rein Java, für den Backport).

Tests:

- JUnit `planner.MultiEditTest` (19 Tests): Auswahl mit Strg/Cmd, Umschalt und Strg/Cmd+A, „–“ bei gemischten Werten,
  Schritt für alle und mit Alt je Wegpunkt, Eingabe mit Grenzen (Tempo 99 → 20 m/s, Neigung Tour +30°, Richtung −90 →
  270°, Takt nur im Takt), Point of Interest, Hinweiszeilen, Löschen und Verschieben, Zurücknehmen des ersten Klicks,
  `ValueEntry` wie `NumberEntry`. `EditorLayoutTest` prüft den neuen Titel „50 Wegpunkte“ in allen Größen.
- Selbsttest 709 `planer: mehrere Wegpunkte auf einmal ändern …` (`dev/client/MultiEditSteps`, etwa 1 s), echte
  Maus- und Tastenereignisse an `PlannerScreen`:
  1. Tablet mit vier Wegpunkten auf 6, 9, 13 und 20 m.
  2. Strg/Cmd-Klick auf 1 und 3 ergibt „2 Wegpunkte“, ein zweiter Klick nimmt 3 wieder heraus.
  3. Umschalt-Klick 1 bis 3 ergibt „3 Wegpunkte“, Strg/Cmd+A „4 Wegpunkte“.
  4. Höhe zeigt „–“, Warten „keine“.
  5. Alt+Umschalt-Klick ergibt 11 / 14 / 18 / 20 m. Ein Klick ohne Alt ergibt 19 / 19 / 19 / 20 m.
  6. Doppelklick öffnet die Eingabe ohne Änderung. „99“ und Esc lassen alles, wie es war.
  7. Strg/Cmd+A, „1x2“ (x wird verworfen) und Enter ergeben 12 / 12 / 12 / 20 m.
  8. Alt+Umschalt-Klick (+5 m) ergibt 17 / 17 / 17 / 20 m.
  9. Doppelklick auf Zeile 4 zentriert die Karte.
  10. Nach dem Schließen trägt das Tablet am Client und am Server 17 / 17 / 17 / 20 m.
  - Mit `-PcamdronesScreenshots` entstehen `docs/screenshots/planner_multi_select.png` und
    `planner_multi_entry.png`, je mit `_de`.
  - Vorher (Fabric) FAIL: „Strg/Cmd-Klick: Wegpunkt 1 und 3 gewählt: Titel „Waypoint 3/4“ statt
    „camdrones.planner.detail.waypoints““. Nachher PASS auf Fabric und NeoForge.
- `:common:test` 942 Tests grün (923 + 19), `./gradlew build` beider Loader grün.
- Gruppen `planer:` und `planner:` (15 Schritte mit 709, dazu Aufräumen): Fabric 16 von 16 PASS, NeoForge 16 von 16
  PASS. Darin auch der Editor bei 320×240, 640×480 und 854×480 in EN und DE ohne Befund, mit dem neuen Titel.

## 46. Eigener Fortschritts-Tab „Kamera-Drohnen“ (Owner 09.11., FB1C-4)

Befund: Das Add-on hatte nur Rezept-Freischaltungen, keine eigenen Fortschritte. Vorgabe: eigener Tab wie bei Mob
Defense (`minecraft:impossible`, vergeben im Code wie `PayloadAwards`), Texte DE und EN, gegen den aktuellen Kern ohne
neue Kern-API.

Umsetzung:

- `data/camdrones/advancement/root.json` öffnet den Tab (Hintergrund `minecraft:gui/advancements/backgrounds/adventure`,
  Symbol Tour-Gimbal) mit `minecraft:inventory_changed` für die drei Gimbals und den Flugplaner. Darunter
  `crew_flight`, `video` und `mission` mit je einem Kriterium `done` (`minecraft:impossible`), Symbole Kino-Gimbal,
  Objektiv 50 mm und Flugplaner. Texte in `common/src/lang/awards/` (`camdrones.advancement.<id>.title` und
  `.description`).
- `award.CamAwards#award` vergibt wie `PayloadAwards`. Die Regeln stehen ohne Minecraft in `award.AwardRules`:
  - zu zweit: `CrewService#tick` prüft jede gebundene Crew einmal je Sekunde; ist ein anderer Spieler Pilot, die Drohne
    scharf und mindestens 1 m frei über dem Boden (`noCollision` nach unten), bekommen Pilot und Kameramann den
    Fortschritt.
  - Mission: `AutonomyServer#missionStatus` ist die einzige Stelle, die `camdrones:mission_state` schreibt; wechselt der
    Zustand von RUNNING oder PAUSED auf DONE (mit Pilot über `mission_progress`, ohne Pilot über `AutonomyAssist`),
    bekommen der Besitzer und ein Pilot den Fortschritt.
  - Video: Aufnahmen laufen nur am Client. `award.client.VideoAwards` (im Tick von `RecordingRouter`) merkt sich die
    Kamera-Drohne, solange Propwashs direkte Aufnahme läuft (Pilot, Kameramann oder Livebild), und meldet beim Ende
    der Aufnahme bzw. bei jeder Übergabe einer Flashback-Aufnahme (`GimbalPathRecorder#lastHandover`) die Drohne mit dem
    neuen C2S-Paket `camdrones:video_recorded` (Entity-ID). Der Server vergibt, wenn die Drohne in der Welt des Spielers
    einen Gimbal trägt (höchstens 1 je Sekunde, 3 auf einmal). Ältere Server kennen das Paket nicht, der Client sendet
    es dann nicht; die Protokollnummer bleibt 1.
  - Flashback-Weg (Befund AUTO62-1): `VideoAwards.Handovers` meldet eine neue Übergabe nur, wenn `VideoAwards#counts`
    sie als Aufnahme gelten lässt: kein Foto (`photo()`) und mindestens eine Probe (`samples() > 0`). Ein Foto mit der
    Foto-Taste im Flashback-Modus und eine leere Flashback-Aufnahme („Flashback hat in der Zeit nichts aufgezeichnet“)
    vergeben „Kamera läuft“ nicht mehr, wie ein Foto im Direkt-Modus. Eine Übergabe von vor dem Betreten der Welt
    zählt weiterhin nicht.

Tests:

- JUnit `award.CamAdvancementFilesTest` (Dateien, Eltern, Kriterien, Symbole, Texte DE und EN): vorher 4 von 4 rot
  (`NoSuchFileException … advancement/root.json`), nachher grün. `award.AwardRulesTest` (Regeln, einmal je Aufnahme,
  Paket nur Richtung Server) grün.
- JUnit `award.client.VideoAwardsTest` (Flashback-Weg mit Übergaben wie aus `GimbalPathRecorder`: Aufnahme mit
  Kamerapfad einmal gemeldet, Foto und leere Aufnahme nicht, danach zählt die nächste Aufnahme; Übergabe von vor dem
  Betreten der Welt nicht): vor dem Fix 3 von 5 rot (Foto und leere Aufnahme meldeten Drohne 42), nachher 5 von 5 grün.
  Einen Selbsttest für den Flashback-Weg gibt es ohne Flashback-Jar nicht (wie 802). Mit dem Fix weiter PASS:
  `fortschritte:` und `aufnahme:` auf Fabric und NeoForge (je 6 von 6), Beitrittstest `awards` auf beiden Loadern
  (`reported=1` im Direkt-Modus).
- Selbsttest Gruppe `fortschritte:` (`dev/client/AwardSteps`, Fabric und NeoForge je 5 Schritte und Aufräumen):
  - 901 Tab mit Wurzel über einen Gimbal im Inventar, drei Fortschritte unter der Wurzel sichtbar;
  - 902 kurze Mission ohne Pilot über `mission_command` bis DONE;
  - 903 direkte Aufnahme im Livebild einer Tour, Ende an den Server gemeldet;
  - 904 Spieler fliegt eine Tour, `camdrones_guest` ist Kameramann, beide bekommen „zu zweit“;
  - 905 alle vier im Tab, Bildschirmfoto `docs/screenshots/advancements_camdrones.png` und `_de.png`.
  Jeder Schritt nimmt seinen Fortschritt vorher zurück (`dev/AwardProbe`).
- Server-Rauchtest: Zeile „4 Fortschritte geladen (Tab camdrones:root mit crew_flight, video, mission)“, Fabric und
  NeoForge PASS ohne `ERROR`-Zeilen.
- Beitrittstest `awards` am dedizierten Server (§3), Fabric und NeoForge PASS: „zu zweit“ für Client und
  `camdrones_guest` bei 4,1 m (Fabric) bzw. 3,0 m (NeoForge) Höhe, Mission der Drohne nach dem FPV-Flug
  (`camdrones:hold` → Mission), Video im Livebild (`reported=1`), Client-Zeile
  `awards tab root=done crew_flight=done video=done mission=done`.
- `:common:test` 933 Tests grün (923 + 10), Build beider Loader grün; `aufnahme:`, `crew:` und `fortschritte:` auf Fabric
  und NeoForge je 12 von 12 PASS.

## 47. Lautloser Gimbal und Weltrichtung mit Kameramann (Owner 09.11., FB1C-1)

Befunde aus der Fix-Sammlung (Owner 06.11. und 09.11.):

- **Ton:** `GimbalClient.tick` → `servo()` spielte `gimbal.calibrate`, sobald `GimbalServoTone` 10 Ticks Bewegung und
  4° Weg sah, danach höchstens alle 16 Ticks. Hören konnte das nur der steuernde Client, mit Kameramann also der
  Kameramann, der mit der Gimbal-Kamera direkt an der Tonquelle sitzt; der leisere Ton aus PTC-3 (§37) half ihm nicht.
- **Weltrichtung:** Der Kameramann übernahm den Modus des Piloten (`OperatorGimbalInput`), meist Folgen: Der Schwenk war
  relativ zum Rahmen, die Kamera drehte beim Gieren des Piloten mit 0,6 s Glättung mit (`GimbalPose`). Eine Weltrichtung
  hielt nur die Sperre über Aktion 1. Kompakt und Tour ignorierten Schwenk-Eingaben des Kameramanns.

Seit dem Fix:

- Bewegen (Neigen, Schwenken, Zoomen, Gieren) ist für alle Rollen lautlos. `GimbalServoTone` spielt nur noch, wenn der
  Kameramann die Kamera übernimmt oder jemand zentriert (Taste „Gimbal zentrieren“ des Piloten, Aktion 2 des
  Kameramanns; `PilotGimbalInput#recentered`, `GimbalControl#drainRecentered`), höchstens einmal je 16 Ticks. Der Regler
  „Gimbal-Surren“ gilt für diesen Ton, Tooltip angepasst.
- Mit gebundenem Kameramann ist der Schwenk immer weltbezogen: `OperatorGimbalInput` macht aus jeder Einstellung eine
  Sperre (`world`: Folgen → Rahmen + Schwenk, FPV → Rahmen), der Schwenk-Knüppel dreht `heading`, Aktion 2 setzt es auf
  Drohnen-Vorne, Aktion 1 hat für den Kameramann keine Funktion (Folgen/Sperre bleibt dem Solo-Piloten). Das Netz ist
  unverändert, `heading` wird schon abgeglichen; Pilot und Zuschauer rechnen dieselbe Sperre.
- Kompakt und Tour (±25°, `GimbalLimits#heading`, `#atStop`): ohne Knüppel bleibt die Weltrichtung gespeichert, der Gimbal
  hält bis zum Anschlag und kommt zurück, wenn der Pilot zurückdreht; mit Knüppel schwenkt der Kameramann vom
  tatsächlichen Blick aus innerhalb der Grenze. Am Anschlag zeigt sein OSD die Warnung `GIMBAL LIMIT`
  (`camdrones:warning/gimbal_limit`, auch in der Kamera-Ansicht; `HudHooks#gimbalStop`, nur für den Kameramann). Kino 360°.
- Der Chat-Hinweis beim Binden nennt das Halten der Richtung und Aktion 2.

Tests:

- JUnit `client.gimbal.OperatorWorldHeadingTest` (neu, 5 Fälle: Kameramann am Gimbal-Modell von Propwash, Pilot giert
  90°, 180° und 270° zurück, Schwenk nach Osten und Gieren, Übernahme mit Schwenk 40°, Kompakt bis zum Anschlag und
  zurück, Kompakt-Schwenk nur innerhalb ±25°), `OperatorGimbalInputTest` (+4, zwei alte Fälle zu Aktion 1 und FPV ersetzt),
  `GimbalServoToneTest` (neu, 6 Fälle: Bewegen als Pilot und Kameramann stumm, Zentrieren, Übernahme, kein Doppelton,
  Taste und Aktion 2), `GimbalServoSoundTest#sliderTooltipNamesRecenterAndTakeOverInsteadOfMoving`, `CamOsdTest` und
  `CamOsdElementsTest` mit zehn Warnungen. Vorher (Stand vor dem Fix, Tests gegen die alte Schnittstelle) 7 von 33 rot:
  `GimbalServoToneTest#movingTheGimbalNeverPlays` („expected: <[]> but was: <[10, 26, 42]>“),
  `OperatorGimbalInputTest#actionOneKeepsTheWorldHeading` („Aktion 1 … [MODE]“), alle 5 Fälle von
  `OperatorWorldHeadingTest` (etwa „Pilot giert 90°: Kamera 89,71° statt 0,0°“, Kompakt „Kamera 0,0° statt 18,0°“).
  Nachher `:common:test` 920 Tests grün.
- Selbsttests 561 `gimbal: Kameramann – Kino hält die Weltrichtung, wenn der Pilot giert, Schwenk nach Osten bleibt im
  Osten` und 562 `gimbal: Kameramann – Kompakt hält die Richtung bis zum Anschlag 25° und zeigt GIMBAL LIMIT`
  (`dev/client/CrewHeadingSteps`, je etwa 25 s): Der Spieler wird am Server als Kameramann gebunden (`CrewBook` und
  Propwashs Operator-Bindung), die Drohne fliegt ohne Pilot über eine Test-Flughilfe, die nur die Nase dreht; die
  Eingaben des Kameramanns kommen über `propwash.test.operatorInput`.
  - Kino: Übernahme mit Blick nach Drohnen-Vorne und genau einem `gimbal.calibrate`; Gieren um 90° und auf 180°: Bild
    0,00° neben der Weltrichtung (auch während der Drehung höchstens 1°); Schwenk nach Osten (90,4°); Gieren auf 225°:
    Bild bleibt im Osten; kein Ton beim Gieren und Schwenken; Neigen und Aktion 2: Bild auf Drohnen-Vorne (0,2°), Neigung
    0°, genau ein Ton.
  - Kompakt: Gieren 15°: Bild hält; 40°: Bild 25,1° neben der Nase am Anschlag, `GIMBAL LIMIT` im OSD (Foto
    `crew_gimbal_limit` mit `-PcamdronesScreenshots`); Schwenk 27° vom Anschlag zurück in die Grenze (Warnung aus) und bis
    zum anderen Anschlag (25,0°, Warnung an); kein Ton beim Gieren und Schwenken.
- Selbsttests 536, 538, 539 und 47 angepasst (§10, §11).
- Gruppen `gimbal:` (13 Schritte mit 561 und 562) Fabric 13 von 13 und NeoForge 13 von 13 PASS; `crew:`, 47, 536, 538,
  539 Fabric 5 von 5 und NeoForge 5 von 5 PASS (538/539: Neigen 0° auf −90° und Antippen ohne Ton, Zentrieren −81° auf 0°
  mit einem Ton in 0,15, Quelle PLAYERS, 0,00 m neben der Drohne, bei 0 % ohne Ton).
- Beitrittstest `crew` (§3) Fabric und NeoForge PASS.

## 48. Greifer im Flugplaner (Owner 09.11., FB2C-1)

Befund: Der Flugplaner konnte Propwashs Greifer nicht nutzen, und zwar mit Absicht. Der Greifer ist fest im Kern
eingebaut und hat kein `AccessoryBehavior`, `triggerAction` erreichte ihn nicht; Wegpunkte mit Zubehör-Aktion an einer
Drohne, die nur einen Greifer trägt, übersprang der Planer mit `GRIPPER_ONLY` („an add-on cannot trigger the gripper in
this Propwash version“). Propwashs Greifer nahm zudem nur mit Pilot auf. Seit Propwash 0.6.0-dev.14 (Add-on-API 1.8.0,
F-FB1-10) gibt es `AccessoryRegistry#grabCargo(Entity, ServerPlayer)` (Griff 5 s offen, auch ohne Pilot),
`#releaseCargo(Entity, ServerPlayer)` und `#cargo(Entity)`. Vorgabe des Owners (09.11.): so einfach wie beim Piloten –
die Drohne fliegt über ein Item, der Greifer nimmt es auf, fertig; dazu eine Wegpunkt-Aktion „Greifer auslösen“ in der
vorhandenen Zubehör-Liste, keine eigene Spalte.

Umsetzung:

- **Immer bereit in der Mission:** `WaypointTaskServer` (Server-Tick beider Loader, beobachtet jede Drohne mit aktiver
  Mission) ruft, solange die Mission läuft (`RUNNING`) und `cargo()` leer ist, jede Sekunde `grabCargo(drohne, besitzer)`
  neu auf (`integration.autonomy.MissionGripper`, `RENEW_TICKS` 20, Griff der API 100 Ticks). Fliegt die Drohne tief genug
  über ein Item (Propwashs Greifer: Fuß höchstens 0,9 Blöcke darüber, 0,6 Blöcke zur Seite, freie Sicht), nimmt
  Propwash es auf; das Flugmodell rechnet die Fracht mit. Mit Fracht ruft das Add-on nicht mehr. Hört die Mission auf zu
  laufen (Pause, Ende, Abbruch), schließt `releaseCargo` einen noch offenen Griff ohne Fracht; Heimflug, Schweben und
  Landen nach der Mission, Halten, Follow me und Automatik greifen also nicht von selbst. Der Besitzer muss online und in
  derselben Welt sein, wie bei den Zubehör-Aktionen.
- **„Greifer auslösen“:** neuer letzter Wert `Waypoint.Accessory.GRIPPER_RELEASE` im Feld *Zubehör* (nach „Wiederholung
  aus“, kein Wiederholen und kein Takt). `WaypointTasks.Due#release` meldet ihn einmal je Durchgang an seinem Wegpunkt,
  `WaypointTaskServer` ruft `releaseCargo(drohne, besitzer)`; Propwash lässt die Fracht fallen und nimmt sie 60 s lang
  nicht wieder auf. Wie jede andere Zubehör-Einstellung beendet der Wert „ab hier“ und den Takt eines früheren Punkts.
  Bericht `gripper.release`, Meldung „Greifer auslösen“ wie bei den Zubehör-Aktionen.
- **Sperre weg, Hinweis neu:** `DroneSupport.Skip.GRIPPER_ONLY` und sein Text entfallen; Zubehör-Aktion 1 bis 4 ohne
  Zubehör mit Aktionen ist `NO_ACCESSORY`. „Greifer auslösen“ an einer Drohne ohne Greifer ist `NO_GRIPPER`: Feld und
  Listenzeile mit „!“, Tooltip im Wegpunkt-Detail „Wird übersprungen: kein Greifer an der Drohne“, im Flug einmal je
  Punkt eine Chat-Meldung (`DroneSupport#release`, `#accessory(Mission, int)`). Marker in der Welt „Greifer auslösen“.
  Der neue Wert ist in EN und DE nicht breiter als „Wiederholung aus“, das Layout des Editors bleibt gleich.
- **Missionsformat 4:** Missionen mit „Greifer auslösen“ schreibt `MissionCodec` als Format 4 (Wert 6 im Zubehör-Feld,
  sonst wie Format 3). Format 1 bis 3 bleiben Byte für Byte gleich (`EVERY_FORMAT_VERSION` 3, `GRIPPER_FORMAT_VERSION` 4,
  `FORMAT_VERSION` 4); Format 2 und 3 mit dem Wert 6 gelten als kaputt, ältere Stände melden Format 4 als neuere Version
  und lassen die Daten unverändert.

Tests:

- JUnit `logic.WaypointGripperTest` (7), `planner.GripperActionTest` (4), `integration.autonomy.MissionGripperTest` (4);
  `DroneSupportTest` und `WaypointEveryTest` auf die neuen Namen. Vorher (Gerüst ohne Verhalten) 963 Tests, 25 rot:
  12 der 14 neuen, `DroneSupportTest#missingEquipmentIsSkippedWithReason` („expected: <NO_ACCESSORY> but was:
  <GRIPPER_ONLY>“) und 12 Layout- und Texttests ohne den Text des neuen Werts. Nachher `:common:test` 964 Tests grün.
- Selbsttest (`dev/client/GripperMissionSteps`, gezielt `'-PcamdronesSelftestOnly=710,711,712'`, etwa 1 min):
  - 710 Freestyle-5″ (Propwash-Preset) mit Propwashs Greifer unten fliegt ohne Pilot: Punkt 1 in 3 m, Punkt 2 in 0,5 m
    über 4 Bruchstein mit 2 s Warten, Punkt 3 in 3 m mit „Greifer auslösen“. Aufgenommen in 0,7 m Höhe, im Flug ohne
    Pilot, Flugmodell 0,543 → 0,743 kg (Fabric und NeoForge); an Punkt 3 liegen die 4 Bruchstein am Boden, Flugmodell
    wieder 0,543 kg; Bericht `3:gripper.release:DONE`; 2 s nach dem Ende Greifer leer, das abgelegte Bruchstein bleibt
    liegen (einen offenen Griff erkennt dieser Schritt nicht, das prüft 713, §49).
  - 711 dieselbe Mission ohne Item fliegt normal zu Ende: schwebt 0,1 m neben Punkt 3 in 3,0 m Höhe, Greifer leer,
    Flugmodell unverändert, Bericht `3:gripper.release:DONE`.
  - 712 Freestyle mit LED-Streifen ohne Greifer, Flugplaner mit derselben Mission: Punkt 3 `NO_GRIPPER`, „!“ in Feld und
    Liste, Tooltip mit dem Hinweis; Foto `planner_gripper_release` (EN, DE).
- Gruppen `mission:`, `planer:`, `planner:` (39 Schritte) auf Fabric und NeoForge je 39 von 39 PASS; Server-Rauchtest
  Fabric und NeoForge PASS, Client-Rauchtest Fabric PASS.

## 49. Add-on-API 1.8 verlangt, offener Griff nach Pause und Missionsende (Gegenprüfung G-FB2C-1, AUTO64-1, AUTO64-2)

Befund AUTO64-1: Der Greifer im Flugplaner (§48) ruft `AccessoryRegistry#cargo`, `#grabCargo` und `#releaseCargo` aus der
Add-on-API 1.8.0 auf, `CamDrones.REQUIRED_API` und `propwash_api_version` standen aber weiter auf 1.7. Die
Versionsbereiche (Fabric `>=0.6.0-`, NeoForge `[0.6.0-alpha,)`) und `requireVersion("camdrones", "1.7")` ließen
Propwash bis 0.6.0-rc.3 zu; dort fehlen die drei Methoden (API 1.7.0), und die erste laufende Mission warf im
Server-Tick `NoSuchMethodError` (`WaypointTaskServer` fängt nur `RuntimeException`).

- `REQUIRED_API` und `propwash_api_version` sind 1.8. Propwash mit API 1.7 lehnt `PropwashApi.requireVersion` jetzt beim
  Start ab („camdrones braucht die Propwash-API 1.8, vorhanden ist 1.7.0“), statt dass ein Server mitten im Spiel
  abbricht. Die Versionsbereiche bleiben (sie nehmen alle 0.6.0-Stände an, die Grenze setzt `requireVersion`). README,
  Anleitung und `docs/design.md` nennen API 1.8.
- JUnit `core.ApiLevelTest` liest die Klassen des Add-ons (Konstantenpool mit `java.lang.classfile`) und verlangt, dass
  `REQUIRED_API` jedes aufgerufene API-Mitglied abdeckt. Die Tabelle der Neuerungen nach 1.7 ist der Unterschied der
  öffentlichen API von `0.6.0-rc.3` (1.7.0) zu `0.6.0-dev.15` (1.8.0), gemessen mit `javap`: die drei Fracht-Methoden,
  `AccessoryDefinition#recessMm`, `#dropMm`, der neue kanonische Konstruktor und `Builder#recess`, `#drop`. Ein
  zweiter Test prüft, dass jedes Mitglied der Tabelle in der eingebundenen API existiert. Kommt mit einer neuen
  API-Version etwas dazu, gehört es in die Tabelle. Vorher rot: „REQUIRED_API 1.7 lässt Propwash mit API 1.7.x zu,
  dort fehlen [API 1.8: AccessoryRegistry.cargo, .grabCargo, .releaseCargo in WaypointTaskServer]“; nachher grün.

Befund AUTO64-2: Kein Test erkannte einen offenen Griff nach dem Missionsende oder in der Pause über die Verdrahtung.
Der letzte Schritt von 710 schwebt an der Stelle, an der die Drohne gerade losgelassen hat; Propwash greift dort erst
1,5 Blöcke weiter wieder zu und ignoriert das Abgelegte 60 s. Ohne den Zweig `close()` in `WaypointTaskServer.grip`
blieb 710 grün.

- Selbsttest 713 (`dev/client/GripperMissionSteps`, gezielt `'-PcamdronesSelftestOnly=713'`, etwa 40 s): Freestyle-5″
  mit Propwashs Greifer fliegt ohne Pilot Punkt 1 in 3 m, Punkt 2 in 0,5 m mit 4 s Warten, Punkt 3 in 0,5 m mit
  „Greifer auslösen“, Punkt 4 in 0,5 m, Ende schweben. Tief über Punkt 2 pausiert der Test die Mission am Server
  (`AutonomyServer.execute`, `PAUSE`); 20 Ticks nach `PAUSED` liegen 4 frische Bruchstein direkt unter der Drohne,
  sie müssen 110 Ticks liegen bleiben (länger als der 5-s-Griff der API, `MissionGripper.GRAB_WINDOW_TICKS`), der
  Greifer bleibt leer. Gegenprobe nach `RESUME`: der Greifer nimmt dieselben Bruchstein sofort auf (sie lagen also in
  Reichweite), lässt sie an Punkt 3 fallen. Nach dem Ende (`DONE`) schwebt die Drohne 0,5 m über Punkt 4; wieder
  4 frische Bruchstein darunter, 110 Ticks liegen. Verlässt die Drohne die Stelle (mehr als 0,6 m zur Seite oder
  1 m Höhe), scheitert der Schritt als wirkungslos. Ein Heimflug beendet den Add-on-Flug, die Mission steht danach auf
  `ABORTED` (`PAUSED`, wenn der Pilot übernimmt) und läuft durch denselben Zweig wie das Ende bzw. die Pause; den
  Heimflug selbst zu prüfen trennt nicht, weil die Drohne sofort steigt und wegfliegt.
- Vorher (Mutation, nicht eingecheckt): `WaypointTaskServer.grip` ohne den Zweig `close()` → 713 FAIL „4 Bruchstein
  2 Ticks nach dem Hinlegen aufgenommen (22 Ticks nach PAUSED): der offene Griff aus der Mission ist nicht
  geschlossen“; `close()` nur bei `PAUSED` → FAIL „4 Bruchstein 4 Ticks nach dem Hinlegen aufgenommen (24 Ticks nach
  DONE)“. Mit dem Code: PASS auf Fabric (Pause 0,62 m, Ende 0,46 m unter der Drohne, je 110 Ticks liegen) und
  NeoForge (0,64 m und 0,46 m).
- Der letzte Schritt von 710 heißt jetzt „nach der Mission Greifer leer, abgelegtes Bruchstein bleibt an Punkt 3
  liegen“, weil er nur das prüft.
- Gruppen `mission:`, `planer:`, `planner:` (40 Schritte mit 713) auf Fabric und NeoForge je 40 von 40 PASS, in vier
  Teilen; `:common:test` 971 Tests grün; Server-Rauchtest Fabric und NeoForge PASS („Propwash-API 1.8.0“).

## 50. Kamera allein im Flug ohne Pilot (Owner rc.4.1, OT4C-1)

Befund OT4C-1: Ein einzelner Spieler, der eine Mission startet, konnte den Gimbal nicht steuern, während die Drohne
selbst fliegt. Das *Livebild* des Flugplaners und des Crew-Fensters rief nur Propwashs Zuschauen auf
(`PropwashClientApi.spectate`); Gimbal-Steuerung gab es nur für den Piloten im FPV-Flug und für einen gebundenen
Kameramann (`GimbalControl.roleFor`), im Livebild war die Rolle `NONE`. Zudem zielte der Gimbal in einer laufenden Mission
immer nach dem Flugplan (`GimbalResolver.automatic`), auch für einen Kameramann (außer an Wegpunkten mit freier
Blickrichtung).

Seit dem Fix:

- Fliegt die Drohne ohne Pilot (Propwash-Flug im Namensraum `camdrones`: Mission, Halten, Automatik, Follow me), schickt
  das *Livebild* (`CrewClient.cameraView`, aufgerufen von `PlannerClient.liveView` und dem Crew-Fenster) die Anfrage
  `crew`/`SOLO`, wenn die Drohne scharf ist, keinen Piloten und keinen Kameramann der Crew hat (`SoloCamera.offered`).
  Der Server prüft Besitzer, Flug ohne Pilot und Crew (`SoloCamera.check`) und bindet den Besitzer über Propwashs
  Operator-Bindung (B8) an seine eigene Drohne; `camdrones:gimbal` trägt `solo` (und `steered`). Propwash öffnet dafür
  das Kameramann-Bild, das OSD zeigt `OPERATOR` und am Anschlag `GIMBAL LIMIT` wie beim Kameramann. Lehnt der Server ab,
  öffnet der Client das bisherige Livebild (`Notice` `SOLO_REFUSED`); eine abgestellte Drohne zeigt weiter die Meldung
  im Flugplaner.
- Bis zur ersten Knüppel-Bewegung (Schwenk oder Neigung außerhalb der Totzone, Zoom um mehr als 0,03, Aktion 2;
  `SoloPickup`) folgt der Gimbal dem Flugplan, der Client schickt nichts. Ab dann führt der Besitzer vom sichtbaren Blick
  aus (`GimbalResolver.automaticSetting`, `OperatorGimbalInput#adopt`), der Server setzt `steered`, und alle Clients
  rechnen die Lage ohne Flugplan-Automatik (`GimbalScenes.shown`, `Scene#manualOnly`), bis er abgibt.
- Aktion 1 (nur für die Kamera allein, `OperatorGimbalInput#missionControl`): kurz pausiert bzw. setzt fort, lang
  (0,8 s) bricht ab wie *■ Stopp* (`SoloMission`, Paket `mission_command`). Für den Kameramann der Crew bleibt Aktion 1
  ohne Funktion.
- Ende: Aktion 4 lang (`/camdrones crew leave`) oder Esc, Ende des Flugs ohne Pilot, ein Pilot übernimmt, die Drohne
  ist weg oder der Spieler offline (`SoloCamera.keep`, jede Sekunde in `CrewService.tick`). Nimmt ein zweiter Spieler
  eine Einladung als Kameramann an oder kehrt ein getrennter Kameramann zurück, endet die Kamera allein vorher
  (`CrewService.yieldSolo`), der Kameramann hat Vorrang. `AutonomyServer.startFlight` räumt alte `solo`-Werte ab.
- Im Handflug unverändert; keine neue Propwash-API.

Tests:

- JUnit `crew.SoloCameraTest` (5 Fälle), `client.gimbal.SoloPickupTest` (4 Fälle: Übernahme erst ab der ersten
  Bewegung, Übernahme ohne Sprung vom Flugplan-Blick −30°, Aktion 1 kurz/lang nur für die Kamera allein, beim Öffnen schon
  gedrückt löst nichts aus, Befehle je Missionszustand), `GimbalResolverTest` (+2), `GimbalDataTest` (+2: `solo` und
  `steered` im Codec, Daten ohne Kamera allein kodieren wie bisher). `:common:test` 984 Tests grün.
- Selbsttest 563 `gimbal: Kameramann allein – Livebild aus dem Flugplaner während der Mission ohne Pilot, Knüppel
  schwenken und neigen, GIMBAL LIMIT, Aktion 2, Pause, Stopp und zurück` (`dev/client/SoloCameraSteps`, etwa 30 s): Tour
  fliegt ohne Pilot eine gerade Mission mit Neigung −30° an jedem Punkt, der Test öffnet das Livebild über
  `PlannerClient.liveView` (Weg des Knopfs), die Eingaben kommen über `propwash.test.operatorInput`. Verlangt:
  Kameramann-Bild der eigenen Drohne mit `OPERATOR` im OSD; ohne Knüppel −30°; Knüppel neigt unter −45° und `steered`
  kommt am Client an; Schwenk bis zum Anschlag 25° mit `GIMBAL LIMIT` (Fotos `solo_gimbal_limit`, EN und DE, mit
  `-PcamdronesScreenshots`); Aktion 2 nach vorne (höchstens 2°, die Drohne fliegt) und Neigung 0°; Aktion 1 kurz
  `PAUSED`, noch einmal `RUNNING`; Aktion 4 lang zurück in die normale Ansicht, Mission läuft weiter, Flugplan zielt
  wieder −30°; Livebild erneut; Aktion 1 lang `ABORTED`, nach der Landung zurück in der normalen Ansicht.
  Vorher auf Fabric FAIL „Kameramann-Bild der eigenen Drohne: Livebild ohne Gimbal-Steuerung (Bild der Drohne true,
  operating false, Rolle NONE, OSD [])“. Nachher Fabric und NeoForge PASS (Neigung −75,6° bzw. −74,8°, Anschlag 24,8°,
  Aktion 2 1,6° neben der Nase).
- Beitrittstest `crew` (§3) um Teil (c) erweitert: Die Drohne des Clients fliegt allein eine Mission am Server, der
  Client öffnet das Livebild (`CrewClient.cameraView`) und steuert die Kamera allein; nach 5 s lädt er den simulierten
  Spieler ein, der annimmt: Kamera allein beendet, Crew- und Propwash-Operator ist der simulierte Spieler, `solo` aus,
  der Client ist zurück in der normalen Ansicht. Erwartung
  `CAMDRONES_JOINTEST_EXPECT='joined|crew a operating|crew a released by escape|crew b released|crew c solo operating|crew c solo yields to crew'`;
  Fabric und NeoForge PASS.
- Gruppen `gimbal:`, `crew:`, 47, 536, 538, 539, 343 (`planer: Livebild`, jetzt mit Kamera allein im Halten) und 601
  auf Fabric und NeoForge je 22 Schritte PASS (mit Aufräumen, je etwa 9 min).

## 51. Banner des Livebilds über der OSD-Warnzeile (Gegenprüfung G-OT4C-1, AUTO67-1)

Befund AUTO67-1: Im Kameramann-Bild, bei der Kamera allein wie beim Kameramann der Crew, liegt die OSD-Warnzeile unter
dem Banner des Livebilds. `GIMBAL LIMIT` und `OPERATOR` sind kaum zu lesen (Fotos `solo_gimbal_limit`, EN und DE).

- Ursache in Propwash: Das Standard-Preset MINIMAL legt die Warnungen in Zeile 3, das dauerhafte Banner des Zuschauens
  steht ebenfalls in Zeile 3. Das Kameramann-Bild läuft über dieselbe Zuschauer-Sitzung, deshalb erscheint das Banner
  auch dort. Das Add-on kann über die API weder das Banner noch die Warnzeile verschieben; das Layout des Spielers zu
  überschreiben, ist keine Lösung. Der Fehler wird in Propwash behoben.
- Selbsttest 564 `gimbal: Kameramann-Bild – das Banner des Livebilds verdeckt keine OSD-Anzeige (GIMBAL LIMIT, OPERATOR),
  Kamera allein und Kameramann der Crew` (`dev/client/OperatorBannerSteps`, etwa 40 s): Die Tour fliegt ohne Pilot eine
  gerade Mission mit freier Blickrichtung. Der Test öffnet das Livebild über `PlannerClient.liveView` und schwenkt als
  Kamera allein bis zum Anschlag. Danach bindet der Server den Spieler als Kameramann der Crew, der Test schwenkt bis zum
  anderen Anschlag. In beiden Ansichten wartet der Test 3 s, bis der Hinweis im Banner verschwunden ist. Dann zeichnet er
  100 Bilder des HUD auf (`Hud#extractRenderState` in einen eigenen `GuiRenderState`). Als Banner zählt jede Fläche, auf
  der ein Text steht. Kein anderes Element darf diese Fläche um mindestens 3×2 px überdecken; Flächen und Texte des
  Banners selbst sind ausgenommen. Verlangt werden außerdem OSD-Elemente im Bild sowie `OPERATOR` und `GIMBAL LIMIT`
  unter den Warnungen.
- Stand mit Propwash 0.6.0-rc.4, Fabric und NeoForge gleich, Fenster 854×480: FAIL. Das Banner
  „CH1 · 5669 MHz · MISSION · Tour 9″“ (121,46 184×15) liegt in beiden Ansichten über der Warnung; je nachdem, welche
  gerade angezeigt wird, sind das 188,48 49×9 (`OPERATOR`) oder 180,48 65×9 (`GIMBAL LIMIT`), in 100 von 100 Bildern.
  Gegenprobe ohne Commit: Mit der Warnzeile über `PropwashClientApi.osd()` auf Zeile 10 ist in 100 Bildern nichts
  verdeckt.
- Der Schritt ist gesperrt, bis das Add-on gegen eine Propwash-Version mit Fix baut. Freigabe: in
  `DevClientBootstrap#init` nach `SoloCameraSteps.register();` die Zeile `OperatorBannerSteps.register();` einfügen.
  Danach die Fotos `solo_gimbal_limit` (563) mit `-PcamdronesScreenshots` neu aufnehmen.
