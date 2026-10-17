# Changelog

## 0.11.1 (in Arbeit) – Bugfixes nach Code-Review von Daniel
- Updates: Paketname der geladenen APK muss zur verfolgten App passen (sonst Abbruch); stille Installation nur für
  installierte Apps mit gleicher Signatur; Self-Update nur bei Paket ShizuBox + gleicher Signatur; VirusTotal-Sperre
  ohne Schlüssel wird angezeigt und hält automatische Installationen zurück (`UpdateGate`, 10 Unit-Tests)
- Schulmodus-Wiederherstellung: jeder Schritt einzeln mit eigenem Zeitlimit (hängender Befehl blockiert die anderen
  nicht), Erfolg = Exit-Code 0; nur Wiederhergestelltes verlässt den Snapshot, gelöscht wird er erst, wenn alles zurück
  ist; sonst Benachrichtigung „X Einstellungen konnten nicht zurückgesetzt werden“ mit „Erneut versuchen“ + Eintrag im
  Fehlerprotokoll. Fehlende Snapshot-Schlüssel werden nie mehr gelöscht; eine während des Schulmodus deinstallierte App
  blockiert das Entsperren der anderen nicht mehr (`RestorePlan`, 11 Unit-Tests)
- Duplikate: identisch heißt immer gleicher SHA-256 über die ganze Datei – Dateien mit 64–128 KB wurden nach den
  ersten 64 KB als gleich gewertet (`DuplicatesTest`, 4 Unit-Tests; live: zwei 110-KB-Dateien mit anderem Ende
  werden nicht mehr gruppiert)
- Kein automatisches Google-/Geräte-Backup mehr (`allowBackup="false"` + `data_extraction_rules.xml`, alle Bereiche
  ausgeschlossen): Verläufe, Datenschutz-Protokoll, Schulmodus-Snapshot, VirusTotal-Schlüssel und Papierkorb-Index
  gehören nicht in die Cloud oder auf ein anderes Handy; ShizuBox hat ein eigenes Backup nur für Einstellungen.
  Geprüft: `ALLOW_BACKUP` fehlt jetzt in `dumpsys package`
- Papierkorb: Wiederherstellen überschreibt nie mehr – der Shell-Dienst prüft direkt vor jedem Umbenennen, ob das
  Ziel existiert (sonst „Name (2)“); zwei Einträge mit gleichem Originalpfad kommen beide zurück. Schlägt die
  Existenzprüfung fehl, wird nichts aus dem Index gelöscht (vorher hielt ein Fehler alles für „weg“)
  (`TrashRestoreTest`, 5 Unit-Tests; live auf dem Pixel geprüft)
- Automationen: Zeit-Regeln feuern pro Termin (Datum + Uhrzeit) nur noch einmal – eine Regel um 10:00 lief mit einer
  zweiten um 10:01 doppelt (2-Minuten-Toleranz ohne Merker); Verspätung nach Mitternacht zählt zum richtigen Tag
  (`TimeTrigger`, 5 Unit-Tests)
- Zwischenablage- und Benachrichtigungsverlauf: Sperre greift wieder, sobald ShizuBox verlassen wird (Home, andere App,
  Display aus); beide Seiten mit FLAG_SECURE (keine Screenshots, keine Vorschau in „Letzte Apps“)
- Backup-Import schaltet keine Datenschutz-Funktionen mehr ein (Zwischenablage-, Benachrichtigungsverlauf,
  Datenschutz-Protokoll, ausgeblendeter Zwischenablage-Hinweis): das Backup merkt sich nur, dass sie an waren, danach
  fragt ein Dialog mit „Öffnen“; `toast_before` (Systemwert dieses Handys) gehört nicht mehr ins Backup
  (live geprüft mit einem Backup im alten Format, in dem alles eingeschaltet war)
- Kacheln/Schnellaktionen „Cache leeren“ und „Aufräumen“ laufen app-weit über die Cleaner-Aufgaben (Vordergrunddienst
  mit Fortschritt) – vorher brach das Schließen der Schnelleinstellungen sie ab
- Schulmodus nach Neustart: die Statusleisten-Sperre (lebt nur im Speicher von system_server) wird wieder gesetzt,
  sobald Shizuku läuft; Aktivieren, Schwarzbild und 3× Power unverändert
- „Öffnen mit ShizuBox“: geteilte Dateien werden im Hintergrund geprüft (Ladeanzeige), nicht mehr im Main-Thread
- VirusTotal-Upload: Länge in UTF-8-Bytes statt Zeichen (Dateinamen mit Umlauten scheiterten), Anführungszeichen und
  Zeilenumbrüche im Namen werden ersetzt (`VirusTotalUploadTest` gegen lokalen HTTP-Server, 2 Unit-Tests)
- Cleaner-Schutz: eine ausgenommene App schützt jetzt auch ihre bekannten Ordner im Hauptspeicher (z. B. /WhatsApp,
  /Telegram) und einen gleichnamigen Paket-Ordner, nicht nur Android/… (`GuardExclusionTest`, 4 Unit-Tests)
- Kleinkram: Dateideskriptor-Leck beim Verschieben eigener Dateien in den Papierkorb; der Installer bricht bei einem
  fehlgeschlagenen Kopieren einer Split-APK ab, statt `pm` einen unvollständigen Satz zu geben
- Lautstärke-Boost startet pro Dienst nur noch einen Beobachter (vorher einen mehr bei jedem Start-Befehl);
  Datenschutz-Protokoll nutzt beim Sammeln eine einzige, geschlossene SQL-Anweisung

## 0.11.0 – 2026-10-17 – Suche + Startseite ordnen
- Suche oben auf der Startseite: findet Funktionen, Unterseiten, Einstellungen, Schnellaktionen und installierte Apps,
  Stichwörter und Synonyme in allen 5 Sprachen, tippfehlertolerant, Ergebnisse beim Tippen; Tippen springt direkt
  zur Seite und lässt die Einstellung kurz aufleuchten; Schnellaktionen direkt im Ergebnis schaltbar; zuletzt geöffnet;
  ausgeblendete Bereiche mit Hinweis; Stern heftet als Favorit an
- Startseite bearbeiten: Karten per Ziehen sortieren (federnd, mit Vibration), ein-/ausblenden, halbe oder ganze Breite,
  Favoriten (z. B. Papierkorb, Boost, Activity Launcher) als kleine Karten, auf Standard zurücksetzen; im Backup
- Regel für künftige Funktionen: in den Such-Index aufnehmen (CLAUDE.md)

## 0.10.1 – 2026-10-17 – Stabilitätsrunde 2
- Fix: Starb der Shell-Dienst (z. B. Speichermangel), verband sich ShizuBox bis zum App-Neustart nicht wieder –
  jetzt nach < 2 s automatisch, alle Listener melden sich neu an
- Fix HiLight: Pausen zwischen Effekten zählten als „an“, die LEDs blieben dadurch fälschlich dunkel; kein Ruckeln mehr
  durch die Sitzungs-Prüfung; erste Unit-Tests für die Sicherheitsgrenzen
- Weniger Aufwachen: Privatsphäre-Protokoll und Papierkorb laufen im 15-Minuten-Worker mit (2 statt 4 Jobs)
- Reiniger: Protokoll wird immer nach 90 Tagen gekürzt, Bild-Hash-Cache begrenzt, abgebrochene Verschiebungen werden repariert
- Startseite: Schnellaktionen lesen nicht mehr auf dem UI-Thread
- Details und Messwerte: Stabilitätsbericht 2 in PROJEKTSTAND.md

## 0.10.0 – 2026-10-17 – HiLight (Pixel 11 Pro)
- Neue Karte „HiLight“ unter System (nur Pixel 11 Pro / Pro XL / Pro Fold mit Android 17): steuert die 8 LEDs der
  Kameraleiste über ShizuBox' Shell-Dienst (Shell-Nutzer hat `CONTROL_DEVICE_LIGHTS`), Code/Logik aus HiLight Studio
  übernommen (MIT, Lizenz in NOTICE und in den Dateien)
- Fortschritt als Balken (3 von 8 LEDs ≈ 37 %): App-Updates, Downloads, Reiniger-Scans sowie Fortschritts-Benachrichtigungen
  anderer Apps (Standard: Android-Systemupdate und Play Store, pro App schaltbar); nur kurz beim Start, bei jedem 10-%-Schritt
  und höchstens alle 90 s, unbestimmter Fortschritt als Scanner; am Ende grün bzw. rot
- Farbe und Muster pro App und pro Kontakt (Absender der Nachricht) bei Benachrichtigungen; Muster: Dauerlicht, Pulsieren,
  Atmen, Blinken, Lauflicht, Komet, Welle, Herzschlag, Regenbogen; Farbvorschläge aus dem Hintergrundbild; Vorschau in der App
- Laden (Akkustand als Balken, grün bei 100 %), Kamera/Mikrofon-Hinweis (10 s an / 10 s aus, nur in der ersten Minute)
- Nur mit Display nach unten (global und pro Regel), Ruhezeiten (Bitte nicht stören, nachts, Energiesparmodus, niedriger Akku),
  im Schulmodus immer aus
- Sicherheit (nicht abschaltbar): Benachrichtigungs-Effekte ≤ 1 Min., sonst ≤ 5 Min., nach 10 s gedimmt, höchstens die Hälfte
  jeder 10 Minuten an, nur eine Session (belegt eine andere App die LEDs, bleibt ShizuBox dunkel), sauberes Schwarz +
  Freigabe beim Ende, beim App-Start/Shizuku-Verbinden und per „LEDs zurücksetzen“; Fehler ins Fehlerprotokoll
- Automations-Aktionen „HiLight-Muster abspielen“ und „HiLight aus“, Kachel „HiLight“, Einstellungen und Regeln im Backup
- Fix: „Abspielen“ in der Vorschau tat bei aktivem „Bitte nicht stören“ scheinbar nichts – die Vorschau ignoriert jetzt
  Ruhezeiten (Schulmodus und Sicherheitsgrenzen gelten weiter), und ein blockierter Versuch nennt den Grund; mit
  „Nur bei Display nach unten“ beendete der Hochheben-Wächter die Vorschau sofort – gilt jetzt nicht mehr für die Vorschau

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
