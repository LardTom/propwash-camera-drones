# Kamera-Drohnen – Anleitung für Spieler

**Propwash: Kamera-Drohnen** (englisch *Propwash: Camera Drones*) ist ein Add-on für
[Propwash FPV](https://propwashfpv.com). Es bringt eine zweite Drohnenart: Kamera-Drohnen, die selbst fliegen. Sie
halten per GPS die Position, bremsen, wenn man die Knüppel loslässt, tragen eine stabilisierte 3-Achsen-Gimbal-Kamera
und lassen sich mit einem Tablet programmieren, dem **Flugplaner**: Wegpunkte, Position halten, Follow me, Point of
Interest und Course Lock. Ein zweiter Spieler kann die Kamera mit seiner eigenen Funke steuern.

English version: [`guide.md`](guide.md).

- [Was ist anders als FPV?](#was-ist-anders-als-fpv)
- [Erste Schritte](#erste-schritte)
- [Flugmodi](#flugmodi)
- [Flugplaner](#flugplaner)
- [Intelligente Funktionen](#intelligente-funktionen)
- [Gimbal und Zwei-Piloten](#gimbal-und-zwei-piloten)
- [Bedienung](#bedienung)
- [OSD und Warnungen](#osd-und-warnungen)
- [Befehle](#befehle)
- [Welt-Regeln](#welt-regeln)
- [Auf Servern](#auf-servern)
- [FAQ](#faq)
- [Kompatibilität](#kompatibilität)

---

## Was ist anders als FPV?

| | FPV-Quad (Propwash) | Kamera-Drohne (dieses Add-on) |
|---|---|---|
| Knüppel | Drehraten bzw. Winkel (Acro, Angle, Horizon) | Tempo: loslassen, und die Drohne bremst und hält die Position |
| Ortung | GPS nur für Rescue und OSD | GPS, Barometer und Kompass auf dem Nav-FC; ohne GPS-Fix fliegt sie ATTI und treibt mit dem Wind |
| Kamera | fest mit Uptilt, wackelt mit dem Rahmen | zwei Kameras: Du fliegst mit einer festen FPV-Kamera am Rahmen; die 3-Achsen-Gimbal-Kamera (Horizont bleibt gerade, Neigung per Drehrad oder Tasten) filmt |
| Video | 20–35 ms Latenz | 100–120 ms Latenz, sauberes HD-Bild, große Reichweite |
| Flugzeit | 3–6 min | etwa 26–41 min |
| Tempo | 30–45 m/s, sehr agil | 3 m/s (Kino) bis 26 m/s (Sport), weich |
| Sicherheit | keine | Hindernis-Sensoren, Höhengrenze, Rückkehr bei knappem Akku |
| Programmierbar | nein | Wegpunkte, Halten, Follow me, Point of Interest, Course Lock |

Die Physik ist Propwashs Flugmodell (Wind, Böen, Prop-Wash, Akku-Kurve). Das Add-on bringt Teile mit echten Werten und
die Flughilfe, die Propwashs Höhen- und Positionshaltung steuert.

---

## Erste Schritte

### Installieren

Das Add-on neben Propwash FPV 0.6.0 oder neuer in den Ordner `mods` legen, mit Fabric oder NeoForge für Minecraft 26.3.
Ein Server braucht es auch, und ebenso jeder Spieler, der dort mitspielt.

### Eine Kamera-Drohne bekommen

- **Kreativ:** Der Kreativ-Tab *Propwash: Kamera-Drohnen* enthält drei flugfertige Drohnen (*Kompakt · 2S*,
  *Tour · 4S*, *Kino · 6S*), alle Teile, die drei Objektive, den Flugplaner und den Landeplatz.
- **Überleben:** Jedes Teil und jedes Objektiv hat ein Rezept aus Propwashs Zwischenteilen (Platine, HD-Chip,
  Carbonplatte, Kupferspule, LiPo-Zelle) und Vanilla-Items; das Rezeptbuch zeigt sie. Gebaut wird an Propwashs Werkbank
  wie jede andere Drohne. Eine Kompakt-Drohne liegt manchmal in Dorf-Truhen.
- **Flugplaner und Landeplatz** entstehen an der Werkbank (Vanilla): der Planer aus sechs Eisenbarren, einer
  Glasscheibe, einer Karte und einer Platine (das Rezept wird mit dem ersten Propwash-Teil frei), der Landeplatz aus zwei
  grauen Teppichen, weißem Farbstoff und drei glatten Steinstufen (frei, sobald du einen Flugplaner hast).

| Klasse | Abflugmasse | Akku | Schweben | Kino / Normal / Sport | Höchsttempo | Hindernis-Sensoren |
|---|---|---|---|---|---|---|
| **Kompakt** | 244 g | 2S Li-Ion 3850 mAh | etwa 30 min | 3 / 10 / 16 m/s | etwa 66 km/h | nur unten |
| **Tour** | 720 g | 4S LiHV 4240 mAh | etwa 41 min | 3 / 12 / 20 m/s | etwa 81 km/h | rundum |
| **Kino** | 3,8 kg | 6S LiHV 8560 mAh | etwa 26 min | 4 / 15 / 26 m/s | etwa 105 km/h | rundum, 360°-Gimbal mit Objektiven |

Werte aus der Werkbank-Analyse von Propwash für die Presets; das Höchsttempo ist, was der Aufbau schaffen könnte, in
Sport hört die Flughilfe beim Sport-Tempo der Klasse auf. Zur Kamera-Drohne macht eine Drohne der Nav-FC
(*Nav-FC Kompakt* oder *Pro*): Er schaltet die Flughilfe ein. Kamera-Rahmen haben zwei Halterungen: vorn sitzt der
Gimbal, oben passt eine Action-Cam. Jeder der drei Gimbals passt an alle drei Kamera-Rahmen (Kompakt, Tour und Kino),
auch der Kino-Gimbal: Die Kompakt hebt seine 420 g aber nicht, und die Tour schwebt damit etwa ein Drittel kürzer. An
anderen Rahmen nimmt die Werkbank die Gimbals nur, wenn Propwashs Welt-Regel *Teile müssen zusammenpassen* aus ist.
Akkus, Motoren und Props von Propwash und anderen Add-ons passen nach Propwashs üblichen Regeln.

Der Werkbank-Knopf *Kamera-Drohne prüfen* (bei deutscher Spielsprache unter *⋯* im Kopf der Werkbank, auf Englisch
direkt als *Camera*) zeigt, ob der Bau eine vollständige Kamera-Drohne ist: Nav-FC, Gimbal, Akku und Kamera-Funk. Fehlende Teile nennt er passend zum
Kamera-Rahmen; ein anderer Akku oder Video-Sender ist in Ordnung, die Prüfung empfiehlt dann nur das Kamera-Teil.

### Erster Flug

1. Drohne abstellen und fliegen wie jede Propwash-Drohne (Brille auf, scharf schalten).
2. Gas geben zum Starten. Knüppel bewegen: Die Drohne fliegt das Tempo, das der Knüppel vorgibt.
3. **Knüppel loslassen:** Die Drohne bremst, bleibt dort stehen, wo sie anhält, ohne zurückzuziehen, und hält Position
   und Höhe, auch bei Wind. Beim Start und bei jedem Moduswechsel zeigt das OSD 5 s lang, was der Modus bedeutet.
4. Die Kamera mit dem Gimbal-Drehrad der Funke oder mit Bild↑ / Bild↓ neigen. Du fliegst mit der festen FPV-Kamera;
   **Entf** zeigt die Gimbal-Kamera und wieder zurück. Aufnahmen und Fotos kommen immer von der Gimbal-Kamera.

### Flugplaner koppeln

Flugplaner in die Hand nehmen, **schleichen und mit Rechtsklick auf die eigene, abgestellte Drohne**. Der Planer merkt
sich die Drohne (ihre Seriennummer bleibt beim Aufheben an der Drohne). Koppeln lässt sich nur die eigene Drohne und nur,
solange niemand sie fliegt.

### Die erste Mission

1. Mit dem gekoppelten Planer in der Hand **Blöcke rechtsklicken**, etwa rund um ein Bauwerk: Jeder Klick setzt einen
   Wegpunkt über dem Block (Standard 10 m, *Klickhöhe* im Editor). Solange man den Planer hält, erscheinen nummerierte
   Marker und die Fluglinie in der Welt.
2. **Rechtsklick in die Luft** öffnet den Editor. Wegpunkte prüfen, *Ende* auf *landen* oder *zum Start* stellen.
3. **Senden** drücken: Die Mission liegt jetzt an der Drohne.
4. **▶ Start** drücken. Eine Drohne am Boden startet, steigt auf die Höhe des ersten Wegpunkts und fliegt die Mission,
   ohne dass jemand die Brille aufhat.

### Landeplatz

Der Landeplatz ist ein flacher Block mit einem *H*. Leg ihn dorthin, wo die Drohne landen soll, etwa unter den
Startpunkt. Landet eine Kamera-Drohne ohne Pilot (Missionsende *zum Start* oder *landen*, oder *■ Stopp*)
und liegt unter ihr ein Landeplatz, richtet sie sich über ihm aus und setzt genau in der Mitte auf. Abbauen geht mit
jedem Werkzeug, er droppt sich selbst.

### Flugschule

Vier Kapitel in Propwashs Flugschule zeigen das Add-on in kurzen Szenen: *Kamera-Drohne fliegen* (Position halten,
Kino/Normal/Sport, Gimbal, ATTI, Hindernis-Stopp), *Erste Mission* (koppeln, Wegpunkte, Senden und Start, Landeplatz),
*Follow me und Point of Interest* und *Aufnahmen: Flashback oder direkt* (Aufnahmemodus, Aufnahme im Hintergrund,
Livebild). Halte die Flugschul-Taste (Standard M) im Inventar über einem Kamera-Teil, dem
Flugplaner oder dem Landeplatz, oder öffne sie aus der Liste der Flugschule.

### Fortschritte

Das Add-on hat einen eigenen Tab bei den Fortschritten, *Kamera-Drohnen*. Er öffnet sich, sobald du einen Gimbal oder
den Flugplaner dabeihast, und enthält drei Fortschritte:

| Fortschritt | So bekommst du ihn |
|---|---|
| *Zu zweit in der Luft* | Fliege eine Kamera-Drohne zu zweit: einer steuert, der andere führt die Kamera. Beide bekommen ihn, sobald die scharf geschaltete Drohne mindestens einen Block über dem Boden ist. |
| *Kamera läuft* | Nimm mit einer Kamera-Drohne ein Video auf: Starte und beende eine Aufnahme als Pilot, als Kameramann oder im Livebild vom Flugplaner. Direkte Aufnahme und Flashback-Aufnahme zählen beide. |
| *Flugplan erfüllt* | Schließe eine Mission aus dem Flugplaner ab (keine Schleife). Ihn bekommt der Besitzer der Drohne, dazu der Pilot, falls jemand sie geflogen hat. |

---

## Flugmodi

| Modus | Auslösen | Verhalten |
|---|---|---|
| **N – Normal** | Standard; Propwashs Flugmodus *Angle* | Knüppel = Tempo bis zur Normal-Grenze, Gas = Steigrate, Gieren 90°/s; loslassen = bremsen und halten |
| **C – Kino** | Propwashs Flugmodus *Horizon* | wie N mit geglätteten Knüppeln, Kino-Tempo und langsamem Gieren (30°/s) für ruhige Aufnahmen |
| **S – Sport** | Propwashs Flugmodus *Acro* | manuell mit 35° Winkelgrenze bis zum Sport-Tempo der Klasse (16, 20 oder 26 m/s), ohne Hindernis-Bremse; der schnellste Modus |
| **ATTI** | automatisch ohne GPS-Fix (Höhlen, unter Blöcken) oder bei Kompassfehler | hält nur die Höhe und treibt mit dem Wind, ohne Hindernis-Bremse; *NO GPS ATTI* bzw. *KOMPASSFEHLER* im OSD, mit Hindernis-Sensoren auch *ATTI - HINDERNISERKENNUNG AUS* und ein Ton |

Umschalten wie bei Propwashs Flugmodi: Die Flugmodus-Taste (Standard R) schaltet N → C → S weiter, der
Flugmodus-Schalter der Funke wählt sie auf demselben Kanal wie bisher: Angle = N, Horizon = C, Acro = S (mit Propwashs
Standard-Belegung der Funke Kanal 6 hoch = N, Mitte = C, niedrig = S). Propwashs Einblendung beim Umschalten nennt
dieselben Modi wie das OSD: N, C oder S (ohne GPS-Fix ATTI statt N). Beim Start fliegt die Drohne den Modus, für den
Propwashs Flugmodus steht (mit der Tastatur Angle, also N). Zusätzlich lassen sich *Flugmodus N*, *C* und *S* auf die
Stellungen eines beliebigen 3-Stufen-Schalters legen, und *Nächster Flugmodus* (Ziffernblock /) schaltet ebenfalls
N → C → S weiter. Es gilt die letzte Änderung, ob von der Funke oder einer Taste. In Propwashs Konfigurator (K),
Tab *Modi*, Seite *Add-ons*, nennt die Beschreibung dieser Funktionen die Flugmodus-Taste und die Stellungen des Schalters.

- **Für Umsteiger vom FPV:** Die Knüppel geben Tempo vor, nicht Neigung oder Drehrate. N: GPS hält Position und
  Horizont; voller Ausschlag bringt das Höchsttempo des Modus in etwa 2 s, hinter einem Totband von 5 % ohne Expo.
  C: langsam und weich für Aufnahmen. S: schnell, ohne Hindernis-Bremse; voller Ausschlag neigt die Drohne bis 35° und
  bringt 90 % des Sport-Tempos in etwa 3,5 s (Kompakt), 4,5 s (Tour) bzw. 6 s (Kino). ATTI: ohne GPS, die Drohne treibt
  mit dem Wind. Beim Start und bei jedem Wechsel zeigt das OSD den Modus 5 s lang mit einer kurzen Erklärung (Element
  *Modus-Hinweis*, z. B. *N: GPS HAELT POS. + HORIZONT*). Mit der Tastatur bestimmt Propwashs *Tastatur-Ausschlag*
  (Tab *Empfänger*, Standard 60 %) das Tempo: Für das volle Tempo dort 100 % einstellen.
- **Halten und Loslassen:** Die Position hält sie erst, wenn die Knüppel in der Mitte stehen. Losgelassen bremst sie und
  bleibt stehen, ohne zurückzuziehen, auch bei starkem Wind und nach S oder ATTI: Die Flughilfe behält den gelernten
  Wind und lernt ihn neu, sobald die Drohne nicht mehr bremst. Aus dem Halten (*Anhalten und schweben*) fliegt sie schon
  bei kleinem Ausschlag wie in N weiter.

- **Hindernis-Sensoren** (Nav-FC Pro, sechs Richtungen, 15 m): In N, C, Missionen und Follow me wird die Drohne zu einem
  Hindernis hin langsamer und bleibt etwa 1,5 m davor stehen (*OBSTACLE*, der Warnton piept schneller, je näher sie
  kommt). Missionen können stattdessen übersteigen (Tour und Kino). Die Kompakt-Drohne hat nur einen Sensor nach unten.
  Mit Sensoren fliegt sie nur so schnell, dass sie innerhalb ihrer Reichweite anhalten kann (die Kino im Modus N etwa
  13 m/s). Dicht über dem Boden neigt sie sich nur so weit, dass die Sensoren eine Wand noch vor dem Boden sehen, und ist
  deshalb dort langsamer (in 3 bis 4 m Höhe etwa 10 bis 12 m/s). Beim Bremsen hält sie die Höhe, und die Sensoren tasten
  auch etwas über und unter der Flughöhe; so bleibt sie auch vor einer Wand stehen, deren Oberkante knapp über ihr liegt.
  Eine gesehene Wand bleibt erkannt, bis du seitlich ausweichst, wegdrehst, zurückfliegst oder mit Gas steigst, solange
  GPS da ist; ohne GPS (ATTI) ist die Hinderniserkennung aus. Das OSD zeigt dann *ATTI - HINDERNISERKENNUNG AUS*, und
  einmal ertönt ein Ton. Fällt das GPS aus, während die Drohne eine Wand sieht und du noch zu ihr hin steuerst, bleibt
  sie stehen, bis der Knüppel einmal in der Mitte war; danach fliegt sie ATTI ohne Hindernis-Bremse.
- **Winkelgrenze:** Die Kamera-Drohnen neigen sich höchstens 40°. Stellst du im PID-Tuning von Propwash eine kleinere
  *Angle-Grenze* ein, neigt und bremst die Drohne nur so weit, wie diese Grenze erlaubt: Sie wird zu einem Hindernis hin
  früher langsamer und fliegt mit Sensoren langsamer (die Kino bei 30° im Modus N etwa 11 m/s), bleibt aber weiter etwa
  1,5 m davor stehen. Sport neigt höchstens 35° oder die kleinere Grenze, ATTI höchstens 25°. In Sport stellt die
  Flughilfe das Gas selbst und hält die Höhe bis zum Vollgas; erst wenn bei hohem Tempo auch Vollgas nicht mehr reicht,
  neigt sie sich weniger, bis sie die Höhe wieder hält.
- **Höhengrenze:** Standard 120 m über dem Startpunkt (Welt-Regel). Darüber steigt die Drohne nicht, *ALT LIMIT* im OSD.
  Eine Abstandsgrenze lässt sich ebenfalls setzen (*DIST LIMIT*).
- **Kompassfehler:** Ab und zu würfelt Propwash für einen Flug einen gestörten Kompass. Die Drohne fliegt dann ATTI und
  zeigt *KOMPASSFEHLER*, bis der Kompass wieder stimmt. Bleibt er, landen und neu starten.
- **Rückkehr:** Propwashs GPS-Rescue-Schalter holt die Drohne heim wie jede Propwash-Drohne. Im Pilotenflug startet die
  Flughilfe den Heimflug bei 20 % Akku einmal selbst; brichst du ihn ab, startet sie ihn erst mit einem neuen Akku
  wieder. Unter 10 % landet die Drohne an Ort und Stelle (Modus *LAND* im OSD), Gas hoch hilft dann nicht mehr,
  waagrecht bleibt sie steuerbar. Wird der Akku bei einem Flug ohne Pilot knapp, fliegt sie selbst heim oder landet.
- **Crash:** Ein Aufprall, der die Drohne entschärft, beendet Course Lock, Follow me, Point of Interest, Halten und eine
  laufende Mission; nach dem Scharfschalten fliegst du wieder im eingestellten Modus.

---

## Flugplaner

Der Flugplaner ist ein Tablet, das man in der Hand hält. Die Funke bleibt Propwashs Funke.

| In der Welt | Wirkung |
|---|---|
| Schleichen + Rechtsklick auf die eigene, abgestellte Drohne | koppeln |
| Rechtsklick auf einen Block | Wegpunkt über dem Block |
| Schleichen + Rechtsklick auf einen Marker | diesen Wegpunkt löschen |
| Rechtsklick in die Luft | Editor öffnen |

**Wegpunkte im Flug:** *Wegpunkt hier setzen* (Einfg) speichert beim Fliegen Position, Höhe, Blickrichtung und
Gimbal-Neigung; der gekoppelte Planer muss im Inventar sein. **Aufzeichnen:** im Editor *Aufzeichnen* einschalten und
fliegen; alle 10 m und bei jedem Richtungswechsel kommt ein Wegpunkt dazu.

### Editor

- **Karte** (links): Karte der geladenen Umgebung von oben mit Wegpunkten, Points of Interest, Startpunkt und der
  Live-Position der Drohne. Klick: Wegpunkt. Rechtsklick: Point of Interest. Punkt ziehen verschiebt ihn, leere Fläche
  ziehen verschiebt die Karte, Mausrad zoomt, *Mission einpassen* zeigt alles. *Karte aus* gibt der Liste in kleinen
  Fenstern mehr Platz.
- **Liste und Details** (rechts): je Wegpunkt *Höhe* (über dem Startpunkt), *Tempo*, *Warten*, *Neigung* des Gimbals,
  *Blick* (Flugrichtung, fest, zu einem Point of Interest oder frei für den Kamera-Operator), *Richtung* beim festen
  Blick, *Aktion* (keine, Foto, Aufnahme an, Aufnahme aus) und die Wegpunkt-Aktionen *Licht*, *Zubehör* und
  *Wiederholen* (siehe [Wegpunkt-Aktionen](#wegpunkt-aktionen)). Points of Interest haben Höhe und Radius.
- **Werte ändern:** Klick auf die linke Hälfte eines Werts senkt ihn, auf die rechte Hälfte hebt ihn, das Mausrad geht
  auch; mit Umschalt kommt der große Schritt. Doppelklick auf den Wert selbst (nicht auf die Pfeile) öffnet die
  Texteingabe: Enter übernimmt, Esc bricht ab, Werte außerhalb der Grenzen werden auf die nächste Grenze gesetzt.
- **Mehrere Wegpunkte auf einmal:** Strg-Klick (am Mac Cmd-Klick) nimmt einen Wegpunkt in der Liste oder auf der Karte
  dazu oder heraus, Umschalt-Klick wählt den Bereich ab dem letzten Klick, Strg+A (Cmd+A) wählt alle. Ein Wert gilt dann
  für alle gewählten Wegpunkte und setzt alle auf denselben Wert: die Eingabe auf die getippte Zahl (etwa alle auf
  12 m), ein Klick auf den Wert des zuletzt angeklickten Wegpunkts plus Schritt. Mit gehaltener Alt-Taste (am Mac
  Wahltaste) ändert sich stattdessen jeder um den Schritt, die Abstände bleiben. Unterschiedliche Werte zeigt das Feld
  als „–“. Entf löscht alle gewählten Wegpunkte; die Pfeile zum Verschieben brauchen einen einzelnen Wegpunkt.
- **Mission** (oben): Name, *Grundtempo*, *Kurven* (eckig: an jedem Wegpunkt anhalten; weich: eine durchgehende Bahn mit
  weiten Bögen, die bei 45 % des kürzeren Beins beginnen und enden; die Drohne hält ihr Reisetempo und wird nur so weit
  langsamer, wie ihre Klasse es für die Kurve braucht, Steigen und Sinken gehen weich ineinander über, Aktionen lösen am
  Punkt des Bogens aus, der dem Wegpunkt am nächsten liegt; sie hält weiter an Punkten mit Wartezeit, am letzten Punkt und
  vor Haarnadelkurven über 150°), *Ende* (schweben, zum Start, landen, Schleife), *Hindernis* (stoppen oder übersteigen),
  *Klickhöhe*, *Aufzeichnen*. Im Modus weich zeigen die Karte und die Marker in der Welt die Bögen.
- **Verlassen** (bei den Missions-Einstellungen): was die gekoppelte Drohne tut, wenn du die FPV-Ansicht verlässt –
  *schweben* (Standard), *heim* oder *landen*. Die Einstellung liegt an der Drohne und gilt sofort.
- **Ansicht** (neben *Verlassen*): was du im FPV mit der gekoppelten Drohne siehst – *OSD* (Propwashs OSD, Standard)
  oder *Kamera* (die Kamera-Ansicht, siehe [OSD und Warnungen](#osd-und-warnungen)). Liegt an der Drohne, gilt sofort.
- **Vorlagen:** *Kreis um Punkt* (12 Wegpunkte im 20-m-Kreis mit Blick zur Mitte), *Enthüllung* (rückwärts und steigend
  vom Motiv weg, Kamera bleibt auf dem Motiv), *Strecke* (alle 10 m ein Punkt zwischen den letzten beiden Wegpunkten).
- **Vorschau:** Länge, Dauer und Akkubedarf der Mission; rechts daneben die Zahl der Wegpunkt-Aktionen und wie viele
  davon übersprungen würden, oder ein Hinweis, wenn die gekoppelte Drohne gerade kein GPS hat.
- **Knöpfe:** *Senden* (Mission an die Drohne), *▶ Start*, *⏸ Pause*, *■ Stopp*, *Automatik* (Automatik-Flüge) und
  *Follow me* (siehe [Intelligente Funktionen](#intelligente-funktionen)), *Speichern*. Bis zu 8 Missionen je
  Planer, 50 Wegpunkte und 4 Points of Interest je Mission.
- **Livebild** (im Kopf): zeigt das Kamerabild der gekoppelten Drohne wie beim Zuschauen. Dafür muss die Drohne fliegen
  (auch ohne Pilot) und in Videoreichweite sein, und du brauchst eine aufgesetzte Brille; sonst kommt eine Meldung.
  Fliegt die Drohne ohne Pilot, steuerst du im Livebild den Gimbal selbst (siehe [Kamera allein](#kamera-allein)).

Der Editor funktioniert auch in kleinen Fenstern und bei großer GUI-Skalierung; bei 320×240 klappt die Karte ein, und
das Detailfeld eines Wegpunkts teilt sich in die Seiten *Flug* und *Aktionen* (Umschalter *Aktionen ›* bzw. *‹ Flug*
unter den Feldern).

**Vorschau-Marker abschalten:** In den Einstellungen (K) im Tab *Kamera-Drohnen* schaltet *Vorschau-Marker* die
nummerierten Marker und die Fluglinie in der Welt aus; im Editor bleiben die Punkte. Gespeichert wird in
`config/camdrones-client.json`.

**Wartung:** In der Wartung einer Kamera-Drohne (Schleichen + Rechtsklick mit leerer Hand) stehen im Kopf die Knöpfe
*Mission* und *Crew*. *Mission* zeigt die Mission an der Drohne und ihren Zustand, stellt *Verlassen* und *Ansicht* ein und hat
*▶ Start*, *⏸ Pause* und *■ Stopp*.

### Was während einer Mission passiert

- **Drohne am Boden:** *Start* hebt ab, steigt auf die Höhe des ersten Wegpunkts und fliegt die Mission.
- **Du fliegst die Drohne:** Die Mission läuft in deiner Flughilfe. Knüppel bewegen pausiert sie; *Mission starten oder
  fortsetzen* (Ziffernblock *) fliegt weiter.
- **Du setzt die Brille ab:** Der Server fliegt weiter. Die Drohne behält ihre Mission, auch wenn du weggehst, solange du
  online und in Funkreichweite bist.
- **Du setzt die Brille wieder auf:** Du übernimmst, die Mission pausiert.
- Die aktive Mission liegt an der Drohne: Sie übersteht Aufheben, erneutes Abstellen und einen Neustart des Servers.
- Der Besitzer bekommt eine Chat-Meldung, wenn eine Mission startet, endet oder nicht starten kann.

### Wegpunkt-Aktionen

Zusätzlich zu Foto und Aufnahme kann jeder Wegpunkt zwei Aktionen auslösen. Sie laufen, sobald die Drohne den Punkt
erreicht (auf 1,5 m genau oder beim Weiterschalten in einer weichen Kurve), einmal je Durchgang, in einer Schleife in
jeder Runde wieder. Die Zubehör-Aktion kann außerdem im Takt laufen, alle X Ticks.

| Feld | Werte | Wirkung |
|---|---|---|
| *Licht* | unverändert, an, aus, Dauerhaft, Blinken, Scharf-Status, Akku-Warnung, Regenbogen | schaltet die LEDs der Drohne bzw. wählt den Modus wie im Tab „LED-Streifen“; braucht einen LED-Streifen |
| *Zubehör* | keine, Aktion 1 bis 4, Wiederholung aus, Greifer auslösen | drückt die Zubehör-Aktion wie die Tasten *Zubehör-Aktion 1–4* beim Fliegen, etwa den Werfer eines Add-ons; *Greifer auslösen* lässt die Fracht von Propwashs Greifer fallen |
| *Wiederholen* | nur hier, ab hier, alle … s | *nur hier*: einmal an diesem Wegpunkt; *ab hier*: an diesem und an jedem folgenden Wegpunkt; *alle … s* (im Takt): ab diesem Wegpunkt alle X Ticks, hier in Sekunden angezeigt |
| *Takt* | 1 bis 1200 Ticks, etwa „20 Ticks“ | nur im Takt: Abstand zwischen zwei Auslösern in Ticks (20 Ticks = 1 s, die Sekunden stehen bei *Wiederholen*); Klick ±1 Tick, mit Umschalt ±20 Ticks |

- **Ab Wegpunkt X wiederholen:** Eine Wiederholung hält, bis ein späterer Punkt eine andere Zubehör-Aktion hat oder
  *Wiederholung aus*. Wiederholte Punkte zeigen im Editor „Aktion 1 ab 3“, die Marker in der Welt „Zubehör 1 erneut“.
- **Im Takt (alle X Ticks):** Die Aktion löst aus, sobald die Drohne den Wegpunkt erreicht, und danach alle X Ticks,
  auch unterwegs und während sie an einem Punkt wartet – bis zu einem Wegpunkt mit anderer Zubehör-Einstellung, bis
  *Wiederholung aus* oder bis zum Ende der Mission. Am ersten Wegpunkt gilt der Takt also für die ganze Mission. Ein
  späterer Punkt mit genau derselben Einstellung lässt den Takt ohne zusätzlichen Auslöser weiterlaufen. Während einer
  Pause löst nichts aus. Punkte im Takt zeigen im Editor „Aktion 1 im Takt“, der Marker am Startpunkt „Zubehör 1 alle
  20 Ticks (1 s)“. Vorschau und Akkuzeit ändern sich dadurch nicht.
- **Der Takt endet am letzten Wegpunkt:** Bei jedem *Ende* außer *Schleife* endet die Mission dort. Der Takt läuft
  weiter, solange die Drohne an diesem Punkt wartet, und endet mit der Wartezeit – gezählt ab der Ankunft am Punkt,
  genau wie bei der Drohne selbst. Nach einer Pause dort wartet die Drohne die volle Zeit noch einmal, der Takt läuft
  mit. Spätestens endet er, wenn die Drohne mehr als 3 m vom Punkt entfernt ist. Auf dem Rückflug zum Start, beim
  Schweben und beim Landen danach löst nichts aus. In einer Schleife läuft der Takt von Runde zu Runde weiter.
- **Höchstens so oft, wie das Zubehör erlaubt:** Eine Mindestzeit zwischen zwei Auslösern legt das Zubehör selbst fest,
  etwa die Abklingzeit des Werfers von Mob Defense (Welt-Regel, Standard 4 Ticks). Ist der Takt kürzer, löst das Zubehör
  trotzdem nie schneller aus: Bei 2 Ticks feuert der Werfer von Mob Defense alle 4 Ticks. Der Editor sagt das im
  Tooltip von *Wiederholen* und *Takt*.
- **Fehlt das Zubehör,** wird die Aktion übersprungen: Der Editor markiert Feld und Wegpunkt mit „!“ und nennt im
  Tooltip den Grund, im Flug kommt einmal je Punkt eine Chat-Meldung. Hat das Zubehör einen Namen für die Aktion (etwa
  „Feuern“), steht er im Tooltip und in der Aktionsleiste.
- **Rechte und Regeln:** Ausgelöst wird im Namen des Besitzers der Drohne. Es gelten Propwashs Rechte (etwa Spawnschutz
  und Abenteuermodus beim Werfen) und die Regeln des Add-ons, dem das Zubehör gehört (etwa Abklingzeit und Munition beim
  Werfer von Mob Defense). Ist der Besitzer offline, löst nichts aus.
- **Greifer:** Mit Propwashs Greifer unten an der Drohne ist der Greifer während einer Mission immer bereit, solange er
  leer ist – so einfach wie beim Fliegen mit der Brille: Fliegt die Drohne tief genug über ein Item (der Fuß höchstens
  etwa 0,9 Blöcke darüber), nimmt sie es auf, und das Gewicht fliegt mit. Plane dafür einen Wegpunkt knapp über dem Item,
  etwa 0,5 m hoch mit kurzer Wartezeit. *Greifer auslösen* an einem späteren Wegpunkt lässt die Fracht dort fallen; was
  sie gerade abgelegt hat, nimmt die Drohne nicht gleich wieder auf. *Greifer auslösen* ersetzt wie jede andere
  Zubehör-Einstellung eine Wiederholung von einem früheren Punkt. Nach dem Ende der Mission, beim Heimflug und bei
  Flügen ohne Mission (Halten, Follow me, Automatik) greift der Greifer nicht von selbst. Ohne Greifer an der Drohne
  markiert der Editor den Wegpunkt mit „!“ und nennt im Tooltip „kein Greifer an der Drohne“.
- **Noch nicht möglich:** *Licht* wird übersprungen, bis Propwash Add-ons die LEDs schalten lässt. Der Editor sagt das
  beim Wegpunkt.
- Kamera-Drohnen führen die Aktionen auch im Pilotenflug aus, sobald der Pilot den Wegpunkt erreicht.

### Normale FPV-Drohnen

Der Flugplaner fliegt auch Freestyle-, Race-, Long-Range- und andere FPV-Drohnen, auch mit Rahmen aus Just More Parts.
Koppeln wie bei einer Kamera-Drohne.

- **Ausrüstung:** Jede Propwash-Drohne hat das GPS ihres Flight Controllers, dasselbe, das Propwashs GPS-Rescue nutzt.
  Ein eigenes Teil braucht es nicht. Bedingung ist ein GPS-Fix: mindestens 5 Satelliten, also freie Sicht zum Himmel,
  nicht unter Blöcken, in Höhlen oder im Nether. Ohne Fix steht im Kopf des Editors „kein GPS“ in Orange, die
  Statuszeile und die Vorschau nennen die Satelliten, und *Start* meldet „kein GPS-Fix“.
- **Nur ohne Pilot:** Eine FPV-Drohne fliegt Missionen als Flug ohne Pilot über Propwashs Autonomie, genau wie eine
  Kamera-Drohne ohne Pilot. Brille absetzen, dann *Start*. Fliegst du sie selbst, bleibt sie eine normale FPV-Drohne,
  *Start* meldet „nur ohne Pilot“. Setzt du während einer Mission die Brille auf, übernimmst du, die Mission pausiert.
- **Keine Hinderniserkennung:** Ohne Nav-FC fliegt die Mission die geplante Linie; *Hindernis* wirkt nicht.
- **Verlassen:** Ohne laufende Mission bleibt beim Absetzen der Brille Propwashs Verhalten (Landung bzw. GPS-Rescue);
  die Einstellung *Verlassen* gilt nur für Kamera-Drohnen.
- Der Kopf des Editors zeigt „FPV“; der Tooltip darüber nennt Rahmen-Rolle, GPS und diese Regeln.

---

## Intelligente Funktionen

Alle Funktionen laufen auch ohne Pilot weiter, sobald man die Brille absetzt (Welt-Regel *Autonome Flüge erlauben*).

- **Halten:** *Anhalten und schweben* (Ziffernblock −) oder einfach die FPV-Ansicht verlassen, während die Drohne in der
  Luft ist: Sie schwebt als Luftstativ auf der Stelle, die Kamera bleibt frei. Mit *Verlassen* auf *heim* oder *landen*
  im Flugplaner fliegt sie stattdessen heim oder landet; bei *heim* steigt sie erst 1 m und nimmt dann Propwashs Heimflug
  zum Homepoint. `/camdrones hold` macht dasselbe ohne Brille.
- **Follow me:** *Follow me* im Flugplaner bietet drei Stile: *Hinterher* (etwa 8 m hinter dir und 5 m hoch), *Parallel*
  (8 m neben dir) und *Blick* (bleibt an ihrem Platz und dreht sich nur). Fliegt niemand die gekoppelte Drohne, hebt sie
  ab und folgt dir ohne Pilot. Fliegst du sie selbst, gilt der Stil für den Schalter *Follow me*: einschalten und die
  Brille absetzen. Der Flugplaner merkt sich den Stil je Drohne, bis du das Spiel beendest; ohne Wahl folgt der Schalter
  hinterher. Per Befehl: `/camdrones follow me [Drohne] [behind|parallel|watch]`. Die Kamera zielt immer auf dich.
  Eigenen Tieren und Reittieren folgen geht immer, anderen Spielern nur mit Welt-Regel und ihrer Zustimmung
  (siehe [Auf Servern](#auf-servern)).
- **Point of Interest:** *Point of Interest in der Bildmitte* setzt den Punkt in die Bildmitte (bis 64 m entfernt) und
  kreist im aktuellen Abstand darum. Rollen-Knüppel = Tempo und Richtung, Nicken-Knüppel = Radius (2–200 m), Gas = Höhe.
  Trifft die Bildmitte bis 64 m nichts (Himmel, Ziel zu weit), kreist die Drohne nicht: Die Aktionsleiste meldet „kein
  Ziel in der Bildmitte bis 64 m“, das OSD zeigt zwei Sekunden *NO POI*.
  In Missionen kommen Points of Interest von der Karte; Wegpunkte mit *Blick: zu P1* drehen Nase und Kamera zum Punkt.
- **Course Lock:** *Course Lock* speichert die aktuelle Richtung. Vorwärts fliegt immer diese Richtung, Gieren dreht nur
  Nase und Kamera. Ideal für seitliche Kamerafahrten.
- **Automatik-Flüge:** *Automatik* im Flugplaner (gekoppelte Drohne, niemand fliegt sie) bietet vier Kamerafahrten um das
  Motiv 10 m vor der Nase der Drohne: *Rückzug* (15 s rückwärts und steigend, die Kamera bleibt auf dem Motiv),
  *Aufstieg* (12 s senkrecht nach oben, die Kamera schwenkt nach unten), *Kreis* (ein Kreis in 20 s, Nase zur Mitte) und
  *Spirale* (20 s im Kreis, 10 m weiter und steigend). Sie starten vom Boden oder aus dem Schweben und laufen ohne Pilot;
  danach hält die Drohne die Position. *■ Stopp* landet sie. Während einer Mission pausieren ein Automatik-Flug
  und *Follow me* die Mission: Das OSD zeigt *AUTO* bzw. *FOLLOW* und *PAUSED*, *▶ Start* setzt die Mission an dem
  Wegpunkt fort, an dem sie stand.

---

## Gimbal und Zwei-Piloten

### Gimbal

- **Zwei Kameras:** Als Pilot fliegst du mit einer festen FPV-Kamera am Rahmen wie bei einer FPV-Drohne: Sie blickt im
  *Kamerawinkel* aus der Wartung der Drohne entlang des Rahmens, mit Propwashs eigenem Sichtfeld, und zeigt die Props
  dort, wo sie am Rahmen sitzen. Gefilmt wird mit der Gimbal-Kamera, und sie zeigt keine Props: Operator, *Livebild*,
  Aufnahmen, Fotos und der Flashback-Kamerapfad sehen den Gimbal. Ohne Operator schaltet *Pilotenbild FPV/Gimbal* (Entf)
  dein Bild zwischen FPV-Kamera und Gimbal-Kamera um; jeder Flug beginnt mit der FPV-Kamera. Solange ein Operator
  gebunden ist, siehst du immer die FPV-Kamera, und seine Kamerabewegungen bewegen dein Bild nie.
- **Neigung** −90° (senkrecht nach unten) bis +30°, Kino-Gimbal +60°: Gimbal-Drehrad an der Funke oder Bild↑ / Bild↓.
- **Flüssig in jedem Bild:** Neigung und Schwenk folgen Missionen, Points of Interest und Follow me in jedem Bild
  flüssig, im *Livebild* genauso wie in Aufnahmen und Fotos. Die Neigung, die ein Operator (oder der Pilot) einstellt,
  kommt bei Zuschauern und im *Livebild* etwa 0,15 s später an und gleitet dort, statt zu springen.
- **Schwenk** (nur Kino-Gimbal, 360°): eigene Achse an der Funke.
- **Folgen / Sperre** (allein fliegen): Bei *Folgen* dreht die Kamera mit, wenn die Drohne giert, bei *Sperre* hält sie
  ihre Richtung. Mit Operator hält die Kamera immer ihre Richtung (siehe *Zwei-Piloten*). Neigen und Rollen im Flug
  gleicht der Gimbal in jedem Bild aus, auch schräg nach vorn und zur Seite: Das Bild schwenkt dabei nicht mit und zieht
  nach dem Bremsen nicht nach.
- **FPV:** Mit dem Schalter *Gimbal FPV* rollt das Bild wie bei einer FPV-Kamera mit dem Rahmen und dreht mit der
  Drohne; nur die Neigung bleibt stabilisiert. Der Schalter geht vor Folgen / Sperre.
- **Zentrieren:** Komma-Taste des Ziffernblocks; zweimal gedrückt zeigt die Kamera senkrecht nach unten.
- **Lautlos:** Bewegen macht kein Geräusch, wie bei einem echten Gimbal. Nur Zentrieren und die Übernahme der Kamera
  durch einen Operator spielen einen kurzen Servo-Ton an der Drohne (*Gimbal-Surren* in den Einstellungen, siehe *OSD
  und Warnungen*).
- **Zoom:** Gimbal Kompakt 24–48 mm, Gimbal Tour 24–70 mm (Weitwinkel- und Tele-Kamera), Kino-Gimbal mit dem eingesetzten
  Objektiv bis zur doppelten Brennweite. Ein Objektiv (24, 50 oder 85 mm) kommt über das Inventar des Zubehörs in der
  Wartung der Drohne in den Kino-Gimbal.
- **Aufnahme ohne Operator:** Sobald eine Aufnahme startet – mit Propwashs Taste oder Schalter *Aufnahme* oder an einem
  Wegpunkt –, wechselt dein Bild bis zum Stopp auf die Gimbal-Kamera und danach zurück auf die Kamera, die du gewählt
  hast. Für ein Foto an einem Wegpunkt wechselt es kurz. So kommen Aufnahmen und Fotos immer vom Gimbal: Propwash nimmt
  das Bild auf deinem Bildschirm auf und zeichnet die Welt nicht doppelt. Während einer Aufnahme schaltet *Pilotenbild
  FPV/Gimbal* nicht weg. Ist im Tab *Action-Cam* die Aufnahme *Beim Scharfschalten* an, fliegst du deshalb jeden
  scharfen Flug mit der Gimbal-Kamera.
- **Aufnahme mit Operator:** Der Operator nimmt auf (Aktionen 3 und 4), und die Wegpunkt-Aktionen nehmen bei ihm auf,
  beides mit dem Gimbal-Bild. Dein eigenes Bild bleibt die FPV-Kamera; was du selbst aufnimmst, ist dein FPV-Bild.
- **Stabilisierte Aufnahme:** Aufnahmen und Fotos zeigen das Bild des Gimbals mit geradem Horizont, nicht den
  wackelnden Rahmen. Der Flashback-Kamerapfad folgt Lage und Zoom des Gimbals, auch während du die FPV-Kamera siehst.
- **Wo die Aufnahmen landen:** Mit *Direkte Aufnahme* (siehe *Aufnahmemodus* unten) speichert Propwashs Action-Cam
  Videos und Fotos – von Hand, vom Operator oder aus den Wegpunkt-Aktionen – im Ordner `propwash/recordings` des
  Spielordners, genau wie Propwashs eigene Action-Cam-Aufnahmen; der Chat meldet jede Datei mit einem Link zum Öffnen.
  Direkte Wegpunkt-Aktionen (Foto, Aufnahme an, Aufnahme aus) brauchen ein Kamerabild: Sie nehmen auf, solange du die
  Drohne fliegst, als Operator steuerst oder eine Mission ohne Pilot im *Livebild* verfolgst. Mit *Flashback-Aufnahme*
  gehen sie stattdessen in Flashback, auch im Hintergrund.
- In Missionen kommt die Neigung aus den Wegpunkten; bei Point of Interest und Follow me zielt der Gimbal auf das Ziel.
  Alle Zuschauer sehen dieselbe Kamera-Lage.

### Aufnahmemodus

K-Menü, Tab *Kamera-Drohnen*, Abschnitt *Aufnahme*: Der **Aufnahmemodus** legt fest, wie die Gimbal-Kamera aufnimmt.
Er gilt für Videos, Fotos und Wegpunkt-Aufnahmen von Kamera-Drohnen.

| Modus | Was aufgenommen wird | Wann |
|---|---|---|
| **Flashback-Aufnahme** | der Kamerapfad des Gimbals (Position, Lage, Zoom) als Szene in der laufenden Flashback-Aufzeichnung; ein Foto wird zu einem Marker und einer Szene mit einem Kamerabild | immer, auch im Hintergrund ohne *Livebild*; keine FPS-Kosten, das Bild wird nicht doppelt gezeichnet |
| **Direkte Aufnahme** | das Gimbal-Bild als Video oder Foto in `propwash/recordings` | nur in der Live-Ansicht: während du die Drohne fliegst, ihre Kamera steuerst oder sie im Livebild des Flugplaners siehst |

- **Standard:** *Flashback-Aufnahme*, wenn die Mod Flashback installiert ist (Fabric), sonst *Direkte Aufnahme*. Ohne
  Flashback ist der Flashback-Wert ausgegraut mit dem Hinweis *Flashback-Aufnahme braucht die Mod Flashback (Fabric)*.
  *Zurücksetzen* folgt wieder Flashback; die Wahl steht als `recording_mode` in `config/camdrones-client.json`.
- **Flashback-Aufnahme:** Nimmt Flashback noch nicht auf, startet das Add-on die Aufzeichnung und beendet sie danach
  wieder (Speichern-Dialog von Flashback). In Flashback ist jede Aufnahme eine Szene *Kamera-Drohne · Aufnahme N* (mit
  dem Namen der Drohne, wenn sie einen hat) mit den Spuren *Gimbal-Kamera* und *Gimbal-Sichtfeld* und einem Marker am
  Anfang; den Film rechnest du mit dem Export von Flashback aus. Missionen deiner eigenen Drohnen – ein mit der Drohne
  gekoppelter Flugplaner liegt in deinem Inventar – nehmen weiter auf, während du etwas anderes tust. Fliegt die Drohne
  aus deiner Sichtweite, bittet das Add-on den Server, die Chunks und Entities um sie mitzuschicken (Welt-Regel *Chunks
  um Kamera-Drohnen*, Standard 4), damit das Replay ihre Umgebung zeigt; am Ende der Mission gibt es sie wieder frei.
- **Direkte Aufnahme ohne Livebild:** Eine Mission mit Kamera-Aktionen zeigt *Direkte Aufnahme braucht die Live-Ansicht –
  Flashback-Modus nimmt im Hintergrund auf* und nimmt nichts auf.
- Propwashs eigene Taste und der Schalter *Aufnahme* nehmen immer direkt auf, was du als Pilot siehst.

### Zwei-Piloten

1. Der Pilot lädt einen Spieler in höchstens 16 Blöcken ein: Knopf **Crew** in der Wartung der Drohne oder
   `/camdrones crew invite <Spieler>`.
2. Der Eingeladene bekommt eine Anfrage im Chat und klickt *[Annehmen]* (oder `/camdrones crew accept`).
3. Der **Operator** sieht jetzt das Kamerabild und steuert den Gimbal mit seiner eigenen Funke: rechter Knüppel schwenkt
   und neigt, linker Knüppel hoch und runter zoomt. Aktionen: 2 zentrieren, 3 Aufnahme, 4 Foto; Aktion 1 hat für den
   Operator keine Funktion. **Aktion 4 lang drücken** gibt die Kamera ab (oder `/camdrones crew leave`).
   - **Die Kamera hält ihre Richtung in der Welt**, wenn der Pilot die Drohne dreht: Schaut sie nach Norden, bleibt sie
     im Norden, egal wie die Drohne giert. Wohin sie schaut, bestimmt allein der Operator: Der Schwenk-Knüppel dreht
     diese Richtung, Aktion 2 richtet sie wieder nach Drohnen-Vorne. *Folgen* gibt es für den Operator nicht; der
     Gimbal beginnt mit dem Blick, den er bei der Übernahme gezeigt hat.
   - **Gimbal Kompakt und Tour** schwenken nur ±25° gegen den Rahmen. Sie halten die Richtung bis zu diesem Anschlag;
     dreht der Pilot weiter, bleibt die Kamera am Anschlag und dreht mit der Drohne, und das OSD des Operators zeigt
     *GIMBAL LIMIT*. Innerhalb der ±25° schwenkt der Operator frei, vom Anschlag sofort zurück. Der **Kino-Gimbal**
     schwenkt rundum (360°) und hält jede Richtung.
4. Solange ein Operator gebunden ist, hat der Pilot keine Gimbal-Steuerung und fliegt mit der festen FPV-Kamera; die
   Kamerabewegungen des Operators bewegen das Bild des Piloten nie. Mit *Zurücknehmen* im Crew-Fenster (oder
   `/camdrones crew release`) holt sich der Pilot die Kamera zurück.
5. Trennt sich der Operator (Verbindung weg, Spiel verlassen), bleibt die Crew **2 Minuten** bestehen: Der Pilot liest im
   Chat *Operator … getrennt – die Crew wartet 2:00 auf die Rückkehr*, die Aktionsleiste zählt die Zeit herunter. Kommt
   der Operator in dieser Zeit zurück, steuert er automatisch wieder die Kamera. Kommt er außer Videoreichweite, in einer
   anderen Dimension oder mit einer anderen Drohne in der Luft zurück, lesen beide einen Hinweis im Chat und die
   Aktionsleiste zählt weiter; sobald er die Kamera übernehmen kann, ist die Crew wiederhergestellt, sonst endet sie
   nach den 2 Minuten. Ein Neustart des Servers beendet jede Crew.

Das geht auch während einer Mission ohne Pilot: Der Besitzer lädt mit `/camdrones crew invite <Spieler>` ein, während er
den gekoppelten Flugplaner hält oder neben der Drohne steht, und der Operator filmt, während die Drohne die Mission
fliegt. Das Crew-Fenster hat außerdem *Livebild*: Es zeigt das Kamerabild der Drohne, solange man in Videoreichweite ist.

### Kamera allein

Startest du allein eine Mission, fliegt die Drohne selbst – sie übernimmt die Rolle des Piloten, und du kannst die
Kamera wie ein Kameramann führen:

1. Öffne im Flugplaner (oder im Crew-Fenster der Wartung) das **Livebild**, während die Drohne ohne Pilot fliegt
   (Mission, Halten, Automatik-Flug, Follow me). Du siehst das Bild der Gimbal-Kamera, das OSD zeigt *OPERATOR*.
2. Solange du die Knüppel nicht bewegst, folgt der Gimbal weiter dem Flugplan (Neigung und Blickrichtung der
   Wegpunkte, Point of Interest). Ab der ersten Bewegung führst du, und zwar vom Bild aus, das du gerade siehst:
   rechter Knüppel schwenkt und neigt, linker Knüppel hoch und runter zoomt, Aktion 2 richtet die Kamera nach
   Drohnen-Vorne. Wie beim Kameramann hält die Kamera ihre Richtung in der Welt; Kompakt und Tour schwenken bis ±25°
   und zeigen am Anschlag *GIMBAL LIMIT*. Aktion 3 nimmt auf, Aktion 4 macht ein Foto.
3. **Aktion 1** pausiert die Mission und setzt sie mit dem nächsten Druck fort; **Aktion 1 lang drücken** bricht sie ab
   wie *■ Stopp*, die Drohne landet.
4. **Esc** oder **Aktion 4 lang drücken** bringt dich zurück in die normale Ansicht; die Mission fliegt weiter und der
   Gimbal folgt wieder dem Flugplan. Endet der Flug ohne Pilot, etwa mit der Landung, bist du ebenfalls wieder in der
   normalen Ansicht.

Nur im Flug ohne Pilot: Fliegst du die Drohne selbst, bleibt alles wie bisher (Gimbal über Drehrad und Tasten, Bild per
Taste umschalten). Ein zweiter Spieler als Kameramann hat immer Vorrang: Ist einer gebunden, zeigt das Livebild nur das
Bild; nimmt er eine Einladung an, während du allein steuerst, übernimmt er die Kamera und du bist zurück in der
normalen Ansicht.

---

## Bedienung

Jede Funktion lässt sich in Propwashs Konfigurator im Tab *Modi* auf der Seite *Add-ons* belegen wie die eingebauten
Schalter: Schalter auf einen Bereich eines Kanals, Achsen auf einen analogen Kanal (Drehrad, Schieber oder Knüppel). Die
Tastatur-Tasten stehen in der Minecraft-Steuerung unter *Propwash: Kamera-Drohnen*. Standardtasten liegen nur auf
Tasten, die weder Propwash noch Minecraft benutzen; seltene Funktionen haben keine.

| Funktion | Art | Standardtaste |
|---|---|---|
| Propwashs *Flugmodus* (Taste und Schalter der Funke): N → C → S | Taster, 3-Stufen-Schalter | R |
| Flugmodus N, C, S | Schalter (3-Stufen-Kanal) | – |
| Nächster Flugmodus | Taster | Ziffernblock / |
| Anhalten und schweben | Taster | Ziffernblock − |
| Mission starten oder fortsetzen | Taster | Ziffernblock * |
| Wegpunkt hier setzen | Taster | Einfg |
| Follow me | Schalter | – |
| Point of Interest in der Bildmitte | Taster | – |
| Course Lock | Schalter | – |
| Gimbal Folgen/Sperre | Schalter | – |
| Gimbal zentrieren | Taster | Ziffernblock , |
| Gimbal FPV | Schalter | – |
| Pilotenbild FPV/Gimbal | Taster | Entf |
| Gimbal-Neigung | Achse | Bild↑ / Bild↓ |
| Gimbal-Schwenk (Kino) | Achse | – |
| Zoom (Brennweite) | Achse | – |

Aufnahme und Rückkehr nutzen Propwashs eigene Funktionen *Aufnahme* und *GPS Rescue*.

---

## OSD und Warnungen

Der OSD-Editor hat in der Gruppe *Add-ons* sechs Elemente: *Nav-FC-Modus* (der Flugmodus: *N*, *C*, *S*, *ATTI*, *POI*,
*NO POI*, *FOLLOW*, *WP 3/12*), *Gimbal-Neigung* (*G -45°*), *Mission* (Fortschritt mit Restzeit), *Hindernis* (das
nächste, nur näher als 8 m), *Restflugzeit* und *Modus-Hinweis* (beim Start und bei jedem Wechsel zwischen N, C, S und
ATTI 5 s lang, was der Modus bedeutet). Warnungen: *OBSTACLE*, *ALT LIMIT*, *DIST LIMIT*, *NO GPS ATTI*,
*ATTI - HINDERNISERKENNUNG AUS*, *KOMPASSFEHLER*, *PAUSED*, *HIGH WIND*, *OPERATOR* (ein Operator hat die Kamera),
*GIMBAL LIMIT* (nur beim Operator: der Gimbal Kompakt oder Tour steht am Schwenk-Anschlag ±25°). Die
OSD-Texte bleiben wie Propwashs OSD großgeschriebenes Englisch, nur der Kompassfehler, die abgeschaltete
Hinderniserkennung und der Modus-Hinweis sind übersetzt. Nahe an Hindernissen piept ein Warnton, je näher, desto
schneller, wie eine Einparkhilfe. Erreicht die Drohne die Höhen- oder Abstandsgrenze (*ALT LIMIT*, *DIST LIMIT*), ertönt
einmal ein Warnton; erneut erst, wenn sie eine Sekunde lang von der Grenze weg war. Wechselt eine Drohne mit
Hindernis-Sensoren in ATTI, ertönt einmal ein tieferer Ton (Untertitel *ATTI – Hinderniserkennung aus*).

**Missions-Timer:** *WP 3/12 0:42* zeigt den Wegpunkt, den die Drohne anfliegt, und die Zeit bis zur Ankunft am
*letzten* Wegpunkt, Wartezeiten unterwegs eingerechnet. Rückflug zum Start und Landung zählen nicht mit. Der Timer
läuft Sekunde für Sekunde herunter; ist die Drohne schneller oder langsamer als erwartet (Wind, enge Kurven), holt er
das sanft nach, höchstens zwei Sekunden je Sekunde und nie nach oben, er springt also nicht. Ab der Ankunft am letzten
Wegpunkt steht er auf *0:00*, auch während die Drohne dort wartet, zum Start zurückfliegt oder landet. In einer Pause
bleibt er stehen. Schleifen haben keinen Timer.

**Kamera-Ansicht:** Statt des OSD kann eine Kamera-Drohne ihre eigene Kamera-Ansicht zeigen: oben Modus, Satelliten,
Akku mit Restzeit und Signal; unten links Höhe, Entfernung und Tempo; unten rechts eine Mini-Karte mit Heimpfeil; am
rechten Rand eine Neigungsskala; rote Ränder bei Hindernissen und ein Punkt mit Zeit beim Aufnehmen. Umgeschaltet wird
je Drohne mit *Ansicht* (*OSD* oder *Kamera*) im Editor des Flugplaners oder im Fenster *Mission* der Wartung.

**Tempo-Einheit, Warnton-Lautstärke und Gimbal-Surren:** In den Einstellungen (K) im Tab *Kamera-Drohnen*, Abschnitt
*Kamera-Ansicht und Warntöne*: *Tempo-Einheit* stellt H.S. und V.S. in der Kamera-Ansicht zwischen *m/s* (Standard) und
*km/h* um; Höhe und Entfernung bleiben in Metern. *Warnton-Lautstärke* (Standard 60 %) bestimmt, wie laut das Piepen vor
Hindernissen, der Ton an der Fluggrenze und der ATTI-Ton sind, 0 % schaltet alle stumm. *Gimbal-Surren* (Standard 15 %)
ist der kurze Servo-Ton an der Drohne, wenn der Gimbal zentriert oder ein Operator die Kamera übernimmt; Bewegen bleibt
stumm, 0 % schaltet den Ton aus. Gespeichert wird mit *Speichern* in `config/camdrones-client.json` (`speed_unit`,
`warning_volume`, `gimbal_servo_volume`).

---

## Befehle

Alle Befehle stehen unter `/camdrones`. `<Drohne>` ist Name oder Seriennummer der Drohne; ohne Angabe nimmt der Befehl
die gekoppelte Drohne.

| Befehl | Wirkung |
|---|---|
| `/camdrones mission list` | eigene Kamera-Drohnen mit Mission und Zustand |
| `/camdrones mission start [Drohne] [Wegpunkt]` | Mission starten, wahlweise ab einem Wegpunkt |
| `/camdrones mission pause\|resume\|stop [Drohne]` | Mission anhalten, fortsetzen oder beenden |
| `/camdrones hold [Drohne]` | ohne Pilot auf der Stelle schweben |
| `/camdrones follow me [Drohne] [behind\|parallel\|watch]` | dir folgen |
| `/camdrones follow <Ziel> [Drohne]` | deinem Tier, deinem Reittier oder einem zustimmenden Spieler folgen |
| `/camdrones follow accept\|deny` | Anfrage beantworten, dir zu folgen |
| `/camdrones follow stop` | alle Drohnen stoppen, die dir folgen |
| `/camdrones crew invite <Spieler>` | Kamera-Operator einladen |
| `/camdrones crew accept\|decline` | Einladung beantworten |
| `/camdrones crew leave` | Kamera abgeben (Operator) |
| `/camdrones crew release` | Kamera zurücknehmen (Pilot oder Besitzer) |

Operatoren mit Rechtestufe 2 dürfen Missionen anderer Spieler anhalten und beenden.

---

## Welt-Regeln

Abschnitt *Kamera-Drohnen* im Tab *Welt-Regeln* von Propwash. Keine davon wirkt auf FPV-Rennen: Rennzeiten bleiben
gültig.

| Regel | Standard | Bereich | Wirkung |
|---|---|---|---|
| Autonome Flüge erlauben | an | | Flüge ohne Pilot: Halten, Missionen, Point of Interest und Follow me, auch nach dem Verlassen der FPV-Ansicht; aus: Die Drohne landet oder fliegt heim wie ohne Add-on |
| Anderen Spielern folgen | aus | | Follow me auf andere Spieler, immer mit ihrer Zustimmung; aus: nur Besitzer, eigene Tiere und Reittiere |
| Zweiter Pilot für den Gimbal | an | | Zwei-Piloten-Kamera |
| Höhengrenze (m) | 120 | 0–512 | größte Höhe über dem Startpunkt, 0 = aus |
| Max. Abstand (Blöcke) | 0 | 0–4096 | größter Abstand vom Startpunkt, 0 = nur Propwashs Funkreichweite |
| Höchstdauer ohne Pilot (min) | 60 | 1–60 | längster Flug ohne Pilot; danach fliegt die Drohne heim oder landet, bei knappem Akku früher |

---

## Auf Servern

- Der Server entscheidet alles: Missionen, Starts, Follow-me-Ziele und die Crew. Jede Anfrage wird auf Besitzer,
  Abstand, Welt-Regeln und Häufigkeit geprüft.
- **Wie viele Flüge ohne Pilot gleichzeitig laufen**, stellen Propwashs Welt-Regeln
  *Autonome Flüge je Spieler gleichzeitig* (Standard 1, 0 = keine) und *Autonome Flüge gesamt* (Standard 6) ein;
  daneben hält Propwash immer Platz für Heimflüge frei. Ein Start darüber hinaus meldet *kein Budget*: Propwash nennt
  über der Schnellleiste die Grenze mit ihrem Wert, der Chat nennt beide Welt-Regeln.
- Ein Flug ohne Pilot endet, wenn der Besitzer offline geht (die Drohne landet oder fliegt heim), wenn die Drohne die
  Funkreichweite verlässt (sie fliegt heim, ohne GPS-Empfang landet sie an Ort und Stelle), wenn der Akku knapp wird
  oder die Höchstdauer erreicht ist. Der Besitzer bekommt eine Meldung. Ist Propwashs Welt-Regel *Drohnen erzeugen neues
  Gelände* aus, endet der Flug auch am Rand der bisher erzeugten Welt: Die Drohne fliegt heim, ohne GPS-Empfang landet
  sie an Ort und Stelle.
- **Privatsphäre:** Einem anderen Spieler folgen braucht die Welt-Regel *Anderen Spielern folgen* und seine Zustimmung.
  Er bekommt eine Anfrage mit *[Erlauben]* und *[Ablehnen]*, sieht über der Schnellleiste, wer ihm folgt, und kann es
  jederzeit mit `/camdrones follow stop` beenden. Die Zustimmung endet beim Ausloggen.
- Kamera-Drohnen tragen keine Waffen: nur die Halterungen vorn (Gimbal) und oben.

---

## FAQ

**Was ist anders als FPV?**
Eine Kamera-Drohne fliegt nach Tempo, nicht nach Drehraten: Knüppel loslassen, und sie steht in der Luft. Sie fliegt
langsamer, viel länger und filmt mit einer stabilisierten Kamera. Für Akrobatik und Rennen nimmt man einen FPV-Quad.

**Warum steht ATTI im OSD?**
Die Drohne hat keine nutzbare Position: kein GPS-Fix unter Blöcken oder in Höhlen, oder ein Kompassfehler. In ATTI hält
sie nur die Höhe und treibt mit dem Wind, und die Hindernis-Sensoren bremsen nicht (*ATTI - HINDERNISERKENNUNG AUS*).
Zurück ins Freie fliegen; bei Kompassfehler landen und neu starten.

**Warum endet die Mission?**
Die Chat-Meldung nennt den Grund: Du hast die Brille aufgesetzt (Übernahme), die Drohne ist gelandet, der Akku wurde
knapp, du bist offline gegangen, die Drohne hat die Funkreichweite verlassen, die Höchstdauer der Welt-Regel ist
erreicht, die Drohne hat den Weltrand erreicht und Drohnen dürfen kein neues Gelände erzeugen, jemand hat sie gestoppt,
oder ein anderer Flug ohne Pilot hat sie abgelöst.

**Die Mission startet nicht.**
Erst den Planer koppeln und die Mission *Senden*. Die Drohne braucht einen Akku mit mindestens 15 % Ladung und
GPS-Fix (freie Sicht zum Himmel, nicht unter Blöcken), darf nicht von jemand anderem geflogen werden, und die Welt-Regel
*Autonome Flüge erlauben* muss an sein. Wie viele Flüge ohne Pilot gleichzeitig laufen, stellen Propwashs Welt-Regeln
*Autonome Flüge je Spieler gleichzeitig* und *Autonome Flüge gesamt* ein. Die Meldung nennt den Grund.

**Kann ich den Flugplaner mit meiner Freestyle-Drohne nutzen?**
Ja. Jede Propwash-Drohne hat GPS im Flight Controller; sie braucht freie Sicht zum Himmel und fliegt Missionen nur ohne
Pilot. Siehe [Normale FPV-Drohnen](#normale-fpv-drohnen).

**Warum wurde eine Wegpunkt-Aktion übersprungen?**
Die Chat-Meldung und der Tooltip im Editor nennen den Grund: kein Zubehör mit Aktionen an der Drohne, kein Greifer an
der Drohne (bei *Greifer auslösen*) oder eine Licht-Aktion, solange Propwash Add-ons die LEDs nicht schalten lässt. Wirft ein Werfer nicht, obwohl die Aktion ausgelöst wurde, gelten dessen Regeln: leeres Fach, Abklingzeit,
Spawnschutz oder Abenteuermodus des Besitzers.

**Meine Drohne hält nicht vor der Wand.**
Nur der *Nav-FC Pro* (Tour, Kino) hat Hindernis-Sensoren vorn und seitlich, und im Sport-Modus bremst sie nicht vor
Hindernissen.

**Kann ich eine Kamera-Drohne als Sharecode teilen?**
Ja, wie jede Propwash-Drohne (`/propwash code`, Konfigurator und Website). Missionen gehören nicht zum Sharecode. Wer das
Add-on nicht hat, bekommt Name und Link des Add-ons und einen Ersatz aus Propwash-Teilen.

**Muss das Add-on auf den Server?**
Ja, auf den Server und auf jeden Client. Ein Spieler ohne Add-on bekommt statt eines Fehlers Propwashs Beitrittsmeldung
mit Name und Version des fehlenden Add-ons.

---

## Kompatibilität

- Minecraft 26.3, Fabric (Loader 0.19.5+, Fabric API) oder NeoForge 26.3, Java 25.
- Propwash FPV **0.6.0 oder neuer** (Add-on-API 1.8); mit einem älteren Propwash nennt der Loader beim Start die
  fehlende Version.
- **Just More Parts:** verträglich; seine Akkus, Motoren und Props passen nach Propwashs Regeln auf Kamera-Rahmen.
- **Mob Defense:** verträglich; beide nutzen die Halterung vorn, eine Drohne trägt also entweder einen Gimbal oder ein
  Zubehör von Mob Defense. Auf einer FPV-Drohne lösen Wegpunkt-Aktionen den Werfer von Mob Defense aus, mit dessen
  Regeln, auch im Takt – höchstens so oft, wie seine Abklingzeit erlaubt.
- **Flashback:** wahlweise; der Kamerapfad folgt Lage und Zoom des Gimbals, auch im FPV-Bild. Mit dem Aufnahmemodus
  *Flashback-Aufnahme* nimmt der Gimbal auch im Hintergrund für Flashback auf (siehe *Aufnahmemodus*).
- **Sharecodes (PW3):** Teile der Kamera-Drohnen haben eigene Kurznummern in Propwashs Sharecodes.
- Add-on später entfernt: Propwash behält die Drohnen-Daten unverändert, die Teile werden zu Propwashs Platzhaltern.

Von lardtom. Kein offizielles Minecraft-Produkt. Nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.
