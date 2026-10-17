# Hinweise für Claude-Sessions

Zuerst **PROJEKTSTAND.md** lesen (Funktionen, Struktur, Technik, bekannte Probleme). Antworten an den Nutzer auf Deutsch.

## Build & Installation
```sh
JAVA_HOME=$HOME/Android/jdk ./gradlew assembleRelease -q   # Zeitstempel der APK prüfen!
export PATH=$HOME/Android/Sdk/platform-tools:$PATH
adb mdns services && adb connect <ip:port>                   # kabelloses Debugging
adb -s <ip:port> install -r app/build/outputs/apk/release/app-release.apk
```
- Release-Signierung kommt automatisch aus `~/Android/keys/shizubox-release.properties` (nie committen).
- Debug-signierte Builds können die installierte Release-Version nicht ersetzen.
- Push/Release: `git -c credential.helper= -c "credential.helper=!gh auth git-credential" push origin main`, `gh release create`.

## Regeln
- **Schulmodus (`school/`) nicht kaputtmachen.** Auf dem Handy nie den Schulmodus aktivieren, keine Netzwerk-/adb_wifi-/DPI-Änderungen, keine Neustarts – die adb-Verbindung kann abbrechen.
- Jeden neuen Shell-Befehl zuerst per `adb shell` auf dem Pixel testen (Android 17), Ergebnis im Code-Kommentar vermerken.
- Shell-Befehle nur über `ShizukuManager.exec()`; Einstellungen im geräte­geschützten Speicher.
- UI: vorhandene Bausteine nutzen (`GlassCard`, `GlassBackground`, Theme, Strings über `t()`/`tp()`).
- Neue Einstellungen in `Backup` aufnehmen; neue Startseiten-Bereiche über `Section` (ein-/ausblendbar).
- **Neue Funktionen in den Such-Index aufnehmen**: Eintrag in `search/SearchIndex.kt` (Ziel, Symbol, ggf. Schnellaktion) + Stichwörter `sk_<id>` in 5 Sprachen über `tools/i18n`; Karten, zu denen gesprungen wird, bekommen `Modifier.highlight("<key>")`, ihre Liste `HighlightScroll`.
- Nach jedem Schritt: bauen, auf dem Pixel testen, committen, PROJEKTSTAND.md + CHANGELOG.md aktualisieren.

## i18n
Alle Texte in 5 Sprachen über `tools/i18n` (README dort): Skript mit `S5(key, en, de, tr, es, fr)` + `save()`,
dann `python3 tsv2json.py tr es fr && python3 gen.py`. Fehlermeldungen beginnen mit „⚠“, Erfolg endet mit „✓“.

## Testen bei gesperrtem Handy
- `adb shell am broadcast -n de.emil.shizubox/.updates.DebugReceiver --es test tweaks` (zuerst einmal zum Aufwärmen)
- `--es shot home|system|apps|… [--ei h 3200]` → `/data/local/tmp/shot_<name>.png` per `adb pull`
