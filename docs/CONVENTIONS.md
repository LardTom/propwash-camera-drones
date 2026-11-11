# Konventionen — Propwash: Camera Drones

Vorbild: die Konventionen von Propwash FPV und Just More Parts. Abweichungen und Ergänzungen stehen hier.

## Code
- **Keine Kommentare** in `common/src`, `fabric/src`, `neoforge/src` (auch Tests und `devtest`): keine Javadoc-, Block-
  oder Zeilenkommentare. Erklärungen gehören nach `docs/`, in Commit-Nachrichten oder in sprechende Namen.
- Pakete wie in `docs/design.md` §4.1. Logik für Flug, Missionen und Gimbal nur in `de.lardtom.camdrones.logic`:
  reines Java, keine Importe aus Minecraft, Loadern, Propwash, Gson oder SLF4J (`logic.PortabilityTest`). Grund: der
  Backport 1.21.1 übernimmt das Paket unverändert.
- **Nur die öffentliche Propwash-API** `de.lardtom.propwash.api.**` und die freigegebenen Theme-Klassen (Liste in den
  JMP-Konventionen). Keine Mixins, keine Reflection auf Propwash. Fehlt etwas: API-Wunsch in `docs/design.md` §3.3.
- Namensraum nur `camdrones`. Der Namensraum `propwash` ist reserviert, auch für Befehle, Tasten, Pakete und Texte.
- Minecraft-, Fabric- und NeoForge-APIs nur aus den 26.3-Quellen, keine APIs aus dem Gedächtnis älterer Versionen.
- NeoForge ruft die gemeinsame Init auf einem Worker-Thread: dort nur Anmeldungen.

## Sprache und Namen
- Texte für Spieler nur über `common/src/lang/<modul>/de_de.json` und `en_us.json`; `:common:mergeLang` erzeugt
  `assets/camdrones/lang/*.json` (eingecheckt, nie von Hand ändern).
- Deutsch mit Umlauten und ß, typografische Anführungszeichen „…“, Dezimalkomma (`core.LangFilesTest`).
- Keine Markennamen von Drohnenherstellern, keine fremden Namen, Mails oder lokalen Pfade in Code, Texten, Assets, Doku
  und Commits (`core.RepoTextTest`). Autor und Marke: nur „lardtom“.

## Tests
- JUnit ohne Spielstart: `./gradlew :common:test`. Selbsttest-Schritte heißen `<modul>: <was>` und räumen auf.
- Test-Instanzen sind stumm (`prepareRuns`), Selbsttest-Welten flach und kreativ.

## Git
- Branch `main` ist die Integration; Stränge arbeiten in eigenen Worktrees auf `feature/<name>`.
- Commit-Nachrichten deutsch, Autor ist die Repo-Einstellung (`lardtom`), keine Zeilen über Mitautoren oder Sitzungen.
  **Nie pushen, kein Remote.**
- Nur eigene Dateien ändern (Besitz-Tabelle in `docs/design.md` §11).

## Versionen
- Schema wie Propwash und JMP: `1.0.0-dev.N`, `1.0.0-rc.N`, Release `1.0.0`. Die Nummer steht nur in
  `gradle.properties`.

## Changelog
- Tages-Changelog `changelogs/changelog-YYYY-MM-DD.md` (Neue Features, UI-Änderungen, Bugfixes, Sonstiges), deutsch,
  kurz.
