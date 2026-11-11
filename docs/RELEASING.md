# Release – Propwash: Camera Drones

Versionen wie bei Propwash und Just More Parts: `1.0.0-dev.N` (interne Stände), `1.0.0-rc.N`, Release `1.0.0`. Die
Nummer steht nur in `gradle.properties` (`version`). Hochgeladen wird nur ein Release. Name „Propwash: Camera Drones“ und Lizenz (PolyForm Shield 1.0.0 mit zusätzlichen
Erlaubnissen, Doku MIT, `NOTICE`) hat der Besitzer am 10.11.2026 festgelegt (`design.md` §1.3, E19).

## Ablauf

1. `gradle.properties`: `version`, dazu `propwash_version` auf den Propwash-Stand, gegen den geprüft wird. Bis zum Release
   bleiben die Bereiche `propwash_version_range=[0.6.0-alpha,)` und `propwash_fabric_range=>=0.6.0-` (nehmen
   `0.6.0-dev.N`, `0.6.0-rc.N` und `0.6.0` an); im Release gegen Propwash 0.6.0 gilt die saubere Untergrenze
   `[0.6.0,)` bzw. `>=0.6.0`, weil der Release-Publisher Vorab-Kennungen wie `-alpha` in Abhängigkeiten sperrt.
2. `./gradlew build` grün (JUnit, `checkShareTable`, beide Loader).
3. Voller Selbsttest beider Loader, Fotos auf Fabric (`-PcamdronesScreenshots`), Server-Rauchtest und Server-Tests
   (`docs/testing.md` §3).
4. `./gradlew :fabric:runWebExport` und `python3 tools/web_export.py`: Ergebnis `PASS`.
5. `CHANGELOG.md`: Abschnitt der Version fertig, Datum im Titel. `docs/modrinth/` an die Version anpassen.
6. Jars ohne `-sources` aus `fabric/build/libs/` und `neoforge/build/libs/` sowie `build/web-export/` nach
   `release/<version>/` mit `SHA256SUMS`; interne Stände nach `release/internal/<version>/`.
7. Nach dem Release: `ShareTableCheck freeze` schreibt `common/sharecode-baseline.json` (`docs/sharecode.md`), Commit.
   Ab dann prüft `checkShareTable` gegen diese Datei.
8. Für die Website: Katalog des Add-ons aus dem Web-Export bzw. aus dem Jar, Aufnahme ins Add-on-Verzeichnis mit
   Kurzkennung (Propwashs `tools/sharecode/sync_directory.py`); bis zur Zuteilung bleibt `short_id` in der Tabelle leer.
