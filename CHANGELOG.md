# Changelog

## 0.7.0 (in Arbeit) – Sitzung 2026-10-15 (Abend)
- Update-Benachrichtigungen: einzelnes Update mit App-Icon und alt → neu, mehrere gebündelt (aufklappbar);
  Knöpfe „Installieren“/„Alle installieren“ (still per Shizuku mit Fortschritt, sonst System-Dialog) und „Überspringen“;
  VirusTotal- und Signatur-Sperre gelten; Abschluss-Benachrichtigung öffnet den Updater; Schalter in den Update-Einstellungen
- Downloads laufen nach einem App-Update automatisch weiter; hängengebliebene Fortschritts-Benachrichtigung wird entfernt
- Lautstärke-Boost (System): bis +100 % über das Maximum (`LoudnessEnhancer` auf dem globalen Output-Mix), Foreground-Service,
  einstellbare Obergrenze, getrennte Stufen für Lautsprecher/Kopfhörer (automatisch), Warnung bei viel Boost mit Kopfhörern,
  Gehörschutz-Schalldosis zurücksetzen und wiederherstellen, Kachel „Boost“ und Schnellaktion auf der Startseite, im Backup
- Benachrichtigungs-Verlauf (System → Benachrichtigungen): speichert alle Benachrichtigungen lokal (eigene Datenbank, nicht im Backup),
  Zugriff erteilt sich ShizuBox per Shizuku selbst; Ansichten „Alle“, „Nach App“ (Unterhaltungen zusammengeführt) und „Entfernt“,
  Suche und App-Filter, gelöschte/zurückgezogene Nachrichten markiert, Bilder gespeichert, Apps ausschließen,
  automatisches Löschen nach X Tagen, alles löschen, optionale Fingerabdruck-Sperre
- Automationen (neuer Startseiten-Bereich): Regeln „Wenn → Dann“ mit Editor und Vorlagen; Auslöser App geöffnet/geschlossen,
  Uhrzeit/Wochentage, Kopfhörer, Bluetooth-Gerät, Ladekabel, Akku unter X %, WLAN; Aktionen Gaming, Lautstärke, Boost,
  Bildwiederholrate, Animationen, Graustufen, Bitte nicht stören, wach bleiben, Internet-Sperre, App/Activity starten, Shell;
  „Danach zurücksetzen“, akkuschonend (Callbacks, Wecker), pausiert während des Schulmodus
- Internet-Sperre pro App ohne VPN (Apps): Schalter in den App-Details, Filter „Kein Internet“, Mehrfachauswahl
  „Internet sperren/erlauben“; Sperren werden nach einem Neustart automatisch wiederhergestellt, sobald Shizuku läuft
- Fix: Texte mit „%“ ohne Platzhalter stürzten in `t()` ab

## 0.6.0 – Sitzung 2026-10-15 (Nachmittag) – vom Nutzer auf dem Pixel bestätigt
- Doku: PROJEKTSTAND.md, CHANGELOG.md, CLAUDE.md
- Animationen (Material 3 Expressive, Material3 1.5.0-beta01):
  - zentrale Federn in `ui/motion/Motion.kt`, `MotionScheme.expressive()` im Theme
  - Startseiten-Karten wachsen beim Öffnen zum Bildschirm (Shared Element), Unterseiten öffnen federnd
  - Navigations-Pille in Updates gleitet und streckt sich, Icons hüpfen, Seiten gleiten seitlich
  - alle Buttons ändern beim Drücken ihre Form (ButtonShapes), Karten federn ein, haptisches Feedback
  - Listen erscheinen gestaffelt und gleiten bei Änderungen (App-Liste, Updates, Prozesse, Bildschirmzeit)
  - Schnellaktionen: Farbe, Form (Kreis ↔ abgerundetes Quadrat) und Wert animiert
  - Schulmodus-Karte morpht beim Aktivieren (nur UI), Shizuku-Punkt pulsiert
  - rollende Ziffern (Akku, CPU-Takt, Bildschirmzeit, Update-Zähler, Auswahl-Zähler)
  - weiche Fortschrittsbalken, Häkchen-Pop bei erfolgreicher Installation, animierte leere Zustände
  - „Animationen entfernen“ wird respektiert
  - Navigations-Pille und Seitenwechsel auf Wunsch deutlich schneller (~0,15 s)
- Activity Launcher (Apps → Activity Launcher): alle Apps inkl. System, Suche auch nach Activity-Namen,
  Filter System/startbar, Favoriten, Start mit Shizuku-Fallback (`am start -n`), Verknüpfungen, Namen kopieren,
  Cache; App-Details nutzen dieselbe Logik
- Updater-Karten im gleichen Glas-Look wie der Rest der App
- „Wach bleiben“ ist jetzt ein echtes Caffeine (Bildschirm bleibt an, auch ohne Ladekabel)
- Downloader (neuer Startseiten-Bereich, ohne Shizuku): yt-dlp + FFmpeg + NewPipe Extractor, Download-Dialog,
  Warteschlange im Foreground-Service, Teilen-Menü, Downloads-Liste, Suche, Trends, lokale Abos mit Feed,
  „Alle herunterladen“, Ordnerwahl, Cookies, Video→Audio, yt-dlp-Update; APK jetzt nur arm64-v8a (~61 MB)
- Fix: R8 entfernte Teile von commons-compress → yt-dlp konnte Python nicht entpacken (Keep-Regeln ergänzt)

## 0.5.1 – 2026-10-15
- Bildschirmzeit pro App (heute / 7 Tage), Nutzungszugriff per Shizuku
- Homescreen-Widget mit Schnellaktionen

## 0.5.0 – 2026-10-15 (Nachtsitzung)
- Sprachen: Deutsch, Englisch, Türkisch, Spanisch, Französisch mit Sprachwahl; Übersetzungs-Werkzeug `tools/i18n`
- Schnelleinstellungs-Kacheln (Gaming, Animationen, Bildwiederholrate, Wach bleiben), Karte „Schnelle Schalter“
- Geräteinfo, Prozesse/RAM, Terminal mit Bausteinen; Werkzeug-Raster im System-Bereich
- App-Details: App-Sprache, Aktivitäten starten/Verknüpfung, technische Details, Entwickler-Berechtigungen
- Berechtigungsübersicht, Mehrfachauswahl in der App-Liste
- Sichern & Wiederherstellen aller Einstellungen
- Startseite anpassbar (Startbereich, Bereiche ausblenden, Schnellaktionen)
- Updates: Version überspringen, Sortierung
- Schulmodus-Zeitplan
- Offscreen-Screenshot-Renderer für Tests per adb

## 0.4.0 – 2026-10-14
- Updates-Bereich nach Obtainium/ObtainX (GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid, APKPure, APKMirror),
  Suche, Massenimport, ObtainX-Import/Export, Hintergrundprüfung, „Alle aktualisieren“ mit Fortschritt
- App-Einstellungen: Design, Akzentfarben, AMOLED, Glas, Installationsart, VirusTotal, Signatur-Sperre
- Universal Installer (APK/APKS/XAPK/APKM) mit VirusTotal-Scan, ShizuBox als Standard-Installer
- App-Manager, Gaming-Modus, APK-Installer, Logcat
- Customization: Animationen, Bildwiederholrate, Schrift, DPI, Auflösung, Statusleiste
- Schulmodus: Snapshot/Restore, Apps pausieren, schwarzer Bildschirm, 3× Power, Auto-Ende, Kachel, Neustart-Verhalten
- Grundgerüst: Shizuku-Anbindung, Glas-Startbildschirm, Release-Signierung
