# Changelog

## 0.9.0 – 2026-10-17 – Reiniger
- Neuer Startseiten-Bereich „Reiniger“ (nach Vorbild von SD Maid) mit Speicherübersicht
- Papierkorb für alle Reiniger-Werkzeuge: nichts wird sofort gelöscht, 7 Tage (einstellbar) wiederherstellbar,
  dann automatisch endgültig gelöscht; einzeln oder alles wiederherstellen/löschen, Protokoll, Hinweis bei knappem Speicher
- Schutzregeln: System-/Standardordner, Daten installierter Apps, Shizuku und der Papierkorb sind fest gesperrt;
  eigene Ausschlüsse für Pfade und Apps; KI-Modelle (AICore) standardmäßig ausgeschlossen; Einstellungen im Backup
- Shell-Dienst: Ausgabe als Datenstrom ohne Größenlimit und reines Umbenennen (nie Kopieren – ein kopierendes `mv`
  nach Android/data brachte den Speicher-Dienst von Android zum Absturz)
- Speicher-Analyse: Kategorien als animierter Balken, Ordner-Browser sortiert nach Größe, die 50 größten Dateien,
  Platz pro App (APK, Daten, Cache, Android/data, obb); öffnen, teilen, im Ordner zeigen, in den Papierkorb, ausschließen
- System-Reiniger: Thumbnails, leere Ordner, Log-/Temp-Dateien, abgebrochene Downloads, alte APKs im Download-Ordner
  (mit Hinweis auf die installierte Version), Papierkörbe anderer Apps, ShizuBox-Reste; Kategorien einzeln schaltbar,
  aufklappbar bis zur Datei, sichere Kategorien vorausgewählt, Vorschau vor dem Verschieben
- Leichen-Finder: Ordner deinstallierter Apps in Android/data, obb, media und bekannte App-Ordner im Speicher;
  Apps anderer Nutzer/Profile zählen als installiert, im Zweifel wird nichts angezeigt
- App-Cleaner: „Cache aufräumen“ ist in den Reiniger umgezogen; zusätzlich pro App den Cache in Android/data
  in den Papierkorb (laufende Apps überspringen oder vorher beenden), KI-Modelle (AICore) standardmäßig ausgenommen
- Duplikat-Finder: exakt (Größe → Teil-Hash → SHA-256) und optional ähnliche Bilder (Serienfotos, andere Auflösung),
  Vorschaubilder, automatisch behalten (Kamera-Ordner, älteste, beste Auflösung), immer mindestens eine Datei pro Gruppe
- „Alles scannen“ führt alle Werkzeuge nacheinander aus und zeigt pro Werkzeug den Fund
- Geplante Reinigung (standardmäßig aus): Wochentage, Uhrzeit, nur beim Laden, nur bei ausgeschaltetem Bildschirm,
  pro Kategorie wählbar (Standard: nur sichere), nie im Schulmodus, Benachrichtigung „X aufgeräumt“ mit Link zum Papierkorb
- Kachel, Schnellaktion und Automations-Aktion „Reinigen“
- Scans laufen im Hintergrund weiter (Benachrichtigung mit Fortschritt und „Abbrechen“)
- CHANGELOG sortiert (neueste Version oben)

## 0.8.0 – 2026-10-16
- Privatsphäre-Protokoll (System): welche App wann Kamera, Mikrofon, Standort, Kontakte, Zwischenablage nutzte –
  Zeitleiste und pro App, Hervorhebung „Hintergrund“, „Dienst“ und „Bildschirm aus“, Filter, aus jedem Eintrag
  Berechtigung entziehen / App-Details / Internet sperren, optionale Benachrichtigung bei Kamera/Mikrofon im Hintergrund,
  stündliche Sammlung, Aufbewahrung 7–365 Tage, nicht im Backup
- Akku-Report (System): Verbrauch pro App in mAh und % (Vordergrund/Hintergrund/Dienst), Wakelocks, mobile Daten,
  Weckrufe; „Seit Laden“, „24 Std.“, „Über Nacht“ (Zeitraum einstellbar), Ladestand-Diagramm, Direkt-Aktionen
  (einschränken, beenden, einfrieren, Internet sperren), optionale Morgen-Zusammenfassung; ShizuBox ehrlich mit aufgeführt
- Schulmodus-Statistik (Schulmodus-Bereich + Homescreen-Widget): heute/Woche/Monat/insgesamt, 14-Tage-Balken,
  Durchschnitt, längste Sitzung, Art des Beendens, Bildschirm-Einschaltungen – nur lesend aus dem Schulmodus-Protokoll
- Zwischenablage-Verlauf (System + Kachel): speichert kopierte Texte und Links auch im Hintergrund (über Shizuku,
  ohne Dauerabfrage), Suche, Anheften, erneut kopieren mit einem Tipp, Teilen, Löschen; sensible Inhalte und
  Passwort-Manager werden nie gespeichert, Apps ausschließen, Auto-Löschen, Fingerabdruck-Sperre, nicht im Backup;
  optional Androids „… hat eingefügt“-Hinweis ausblenden (systemweit, wiederherstellbar)
- Lautstärke pro App (System + Automations-Aktion): eigener Pegel je App über der Medienlautstärke, echtes Mischen
  per Shizuku (`PlayerProxy.setVolume` auf jeden Player der App), gerade spielende Apps oben, wird bei jedem neuen
  Player automatisch wieder angewendet; Aktion „Lautstärke pro App“ mit „Danach zurücksetzen“, im Backup
- Shell-Dienst kürzt sehr lange Ausgaben (> 512 KB) statt am Binder-Limit zu scheitern (sah aus wie „Shizuku getrennt“)

## 0.7.2 – Stabilitätsrunde (2026-10-16, nicht einzeln veröffentlicht, in 0.8.0 enthalten)
- Akku: offene App brauchte durch den animierten Hintergrund 70–100 % eines CPU-Kerns, jetzt 7–11 %
- Fehlerprotokoll unter Einstellungen (Abstürze, ANRs, Hänger; Kopieren/Teilen; 30 Tage; nicht im Backup)
- Zeitlimits für Massen-Aktionen, Installationen und FFmpeg passend; FFmpeg wird beim Abbrechen beendet
- Temporäre Dateien werden aufgeräumt; Speicherprüfung vor großen Downloads
- Download-Liste geht nach einem Neustart nicht mehr verloren (Start vor dem ersten Entsperren)
- Benachrichtigungen von Boost und Downloads gedrosselt; Download-Dienst übersteht das Android-Zeitbudget
- Benachrichtigungs-Verlauf mit Größenbremse und Größenanzeige
- App-Namen und Icons werden nicht mehr beim Zeichnen von der Platte geladen
- Automationen: Schleifenschutz; keine Installationen während des Schulmodus
- Details und Messwerte: Stabilitätsbericht in PROJEKTSTAND.md

## 0.7.1 – 2026-10-16 – Selbst-Update 0.7.0 → 0.7.1 vom Nutzer bestätigt
- Update-Dateien für den System-Installer werden nach 1 Stunde gelöscht (vorher 1 Tag → über 1 GB nach mehreren
  manuellen Updates), zusätzlich beim App-Start
- Pausierte Update-Version wird automatisch freigegeben, sobald sie (von Hand) installiert ist
- „Alles aufräumen“: falsche Warnung zu KI-Modellen entfernt – gemessen: AICore (7,6 GB) bleibt unangetastet, ~4 GB App-Caches frei

## 0.7.0 – Sitzung 2026-10-15 (Abend) / 2026-10-16 – vom Nutzer auf dem Pixel bestätigt
- ShizuBox verfolgt seine eigenen GitHub-Releases automatisch: neue Version → Benachrichtigung mit „Installieren“,
  Download und Installation in der App (eigenes, gleich signiertes Update wird nicht von der VirusTotal-Sperre aufgehalten)
- Fix: VirusTotal-Scan blieb bei „In Warteschlange“ hängen – Upload und Analyse laufen jetzt im Hintergrund weiter,
  auch wenn der Bildschirm gewechselt wird; bis zu 10 min Wartezeit, zwischendurch Abfrage des fertigen Datei-Berichts
- Fix: „Aktualisieren“ in der App brach beim Bildschirmwechsel ab („rememberCoroutineScope left the composition“) –
  Download läuft jetzt app-weit
- **Fix Handy-Hänger bei Updates (2026-10-16):** zwei erzwungene Neustarts während stiller XAPK-Installationen
  (Crunchyroll 3.125.0, 123 MB; danach automatische Wiederholung mit Google Home, 78 MB). Schutz:
  - Fortschritts-Benachrichtigung höchstens 2× pro Sekunde (vorher bei jedem 64-KB-Block, ~2000 Updates pro XAPK)
  - Installations-Journal (`InstallGuard`): bleibt eine Installation hängen, erkennt ShizuBox das beim nächsten Start,
    pausiert diese Version für automatische Installationen, ignoriert die WorkManager-Wiederholung nach dem Neustart
    und meldet sich mit einer Benachrichtigung; erst ein neues „Installieren“ versucht es erneut
  - automatische Installationen nur bei ausgeschaltetem Bildschirm und ohne Speichermangel
  - vor jeder Installation Prüfung, ob die Paketverwaltung antwortet; 3 s Pause zwischen Installationen
  - Wächter: hängende Installations-Session wird nach 6 min abgebrochen
  - Shell-Befehle haben ein hartes Zeitlimit (Standard 60 s), danach wird der ganze Prozessbaum beendet
    (vorher konnte ein hängender Befehl Threads unbegrenzt blockieren – 149 Threads im Moment des Hängers)
- Fix: ShizuBox stürzte bei jedem Neustart vor dem ersten Entsperren ab (WorkManager im Direct-Boot), seit 0.4.0
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
- Debloat-Vorschläge (Apps): vorinstallierte Pixel-/Google-Apps mit Erklärung und Einstufung (sicher · mit Vorsicht ·
  nicht anfassen) auf Basis der Universal-Android-Debloater-Liste, Mehrfachauswahl, Einfrieren, Protokoll mit Rückgängig
  (einzeln oder alles), wichtige Systemteile gesperrt
- Internet-Sperre pro App ohne VPN (Apps): Schalter in den App-Details, Filter „Kein Internet“, Mehrfachauswahl
  „Internet sperren/erlauben“; Sperren werden nach einem Neustart automatisch wiederhergestellt, sobald Shizuku läuft
- Cache aufräumen (System): Cache pro App nach Größe, „Alles aufräumen“ über Shizuku, freigegebener Platz mit rollenden Ziffern,
  Schnellaktion und Kachel; Einzel-Apps öffnen ihre Speicher-Einstellungen (Android 17 erlaubt es per Shell nicht)
- APKPure: automatische Wiederholung und verständliche Fehlermeldung statt kryptischer Fehler – erkennt aktives VPN
  („VPN kurz pausieren“) und privates DNS wie NextDNS (Domains zum Freigeben werden genannt)
- Fix: Update-APKs blieben nach der stillen Installation im Cache liegen (bei mir fast 900 MB) – werden jetzt sofort gelöscht
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
