# ShizuBox

Eine All-in-One-Toolbox für Android, die über [Shizuku](https://shizuku.rikka.app/) (ADB-Rechte, **kein Root**) Systemeinstellungen steuert. Glas-Design mit Material You, Hell- und Dunkelmodus. Sprachen: Deutsch, English, Türkçe, Español, Français.

Entwickelt und getestet auf einem Pixel mit Android 17.

## Funktionen

### Schulmodus
Das Handy bleibt an, aber es passiert absolut nichts – als wäre es aus.
- Flugmodus, WLAN/Bluetooth/mobile Daten aus, „Bitte nicht stören“ ohne Ausnahmen, kein Ton, keine Vibration
- Always-on-Display, Sperrbildschirm-Benachrichtigungen, Kamera-Doppeldruck, Gemini-Geste, Fingerabdruck-Entsperren aus
- Alle Apps pausiert (außer Telefon/Notruf, Einstellungen, Shizuku)
- Komplett schwarzer Bildschirm, auch über dem Sperrbildschirm
- **Ausstieg: 3× schnell die Power-Taste** – danach wird jede Einstellung exakt wiederhergestellt
- **Statistik**: Zeit im Schulmodus heute/Woche/Monat, 14-Tage-Verlauf, Art des Beendens – auch als Homescreen-Widget
- Automatisches Ende nach einer festen Zeit, **Zeitplan** (z. B. Mo–Fr um 7:50 automatisch starten, einmal aussetzen), Schnelleinstellungs-Kachel
- Überlebt Neustarts; ist Shizuku danach aus, führt eine kurze Anleitung durch die Wiederherstellung

### Customization
Animationsgeschwindigkeit, Bildwiederholrate, Schriftgröße und Fettschrift, Anzeigegröße (DPI), Render-Auflösung (mit 15-s-Rückfrage), Statusleisten-Gruppen ausblenden.

### Apps
- App-Liste mit Größe, Installations-/Update-Datum, Split-Kennzeichnung, Sortierung
- **Mehrfachauswahl** (lange drücken): mehrere Apps auf einmal einfrieren, auftauen, beenden, deinstallieren oder zu einem Profil hinzufügen
- Einfrieren/Auftauen, Profile zum gemeinsamen Einfrieren, Deinstallieren
- Laufzeit- und Entwickler-Berechtigungen (z. B. `WRITE_SECURE_SETTINGS`), AppOps, Akku-Einschränkung
- **App-Sprache** pro App, **Aktivitäten** starten oder als Verknüpfung anlegen, technische Details (SDK, UID, Signatur)
- **Activity Launcher**: alle Activities aller Apps (auch System-Apps) durchsuchen – auch nach Activity-Namen –, starten (bei Bedarf über Shizuku), Favoriten, Verknüpfungen, Namen kopieren; ideal für versteckte Einstellungsmenüs
- **Internet-Sperre pro App ohne VPN** (WLAN und mobile Daten, verträgt sich mit jedem VPN): Schalter in den App-Details, Filter „Kein Internet“, Mehrfachauswahl; wird nach einem Neustart automatisch wiederhergestellt
- **Debloat-Vorschläge**: vorinstallierte Pixel-/Google-Apps mit verständlicher Erklärung und Einstufung (sicher · mit Vorsicht · nicht anfassen) auf Basis der Universal-Android-Debloater-Liste; Mehrfachauswahl, Einfrieren, Protokoll mit Rückgängig; wichtige Systemteile sind gesperrt
- **Berechtigungsübersicht**: welche Apps dürfen Kamera, Mikrofon, Standort, Kontakte, … – mit „Entziehen“
- APK teilen, extrahieren (APK bzw. APKS), neu installieren, VirusTotal-Prüfung

### Updates (wie Obtainium/ObtainX)
- Apps aus GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid, APKPure und APKMirror verfolgen
- Suche, Massenimport installierter Apps, Import/Export im ObtainX-/Obtainium-Format
- Alle aktualisieren im Hintergrund mit Fortschrittsbenachrichtigung, optional nur nach sauberem VirusTotal-Ergebnis
- Version überspringen, Sortierung nach Name/Veröffentlichung/Quelle
- **Benachrichtigung bei neuen Updates** mit „Installieren“ / „Alle installieren“ / „Überspringen“ – still über Shizuku, sonst System-Dialog
- **ShizuBox aktualisiert sich selbst**: neue Version auf GitHub → Benachrichtigung, Download und Installation direkt in der App
- Schutz vor Hängern: Installationen werden protokolliert, eine hängengebliebene Version wird nicht automatisch wiederholt; automatische Installationen nur bei ausgeschaltetem Bildschirm
- Verständliche Hinweise, wenn ein VPN oder DNS-Filter (NextDNS) APKPure blockiert

### Automationen
Regeln nach dem Schema **Wenn → Dann**, mit Editor und Vorlagen.
- Auslöser: App geöffnet/geschlossen, Uhrzeit + Wochentage, Kopfhörer, Bluetooth-Gerät, Ladekabel, Akku unter X %, WLAN (bestimmtes Netz)
- Aktionen: Gaming-Modus, Lautstärke, Lautstärke pro App, Boost, Bildwiederholrate, Animationen, Graustufen, Bitte nicht stören, Wach bleiben, Internet-Sperre, App oder Activity starten, Shell-Befehl
- „Danach zurücksetzen“, wenn der Auslöser endet; akkuschonend (Callbacks statt Dauerabfrage); pausiert während des Schulmodus

### System
- **Geräteinfo**: Akku (Temperatur, Spannung, Ladezyklen), RAM, Speicher, CPU-Takt pro Kern, System
- **Prozesse**: RAM-Verbrauch pro App, Apps beenden, Hintergrund aufräumen
- **Bildschirmzeit** pro App (heute / 7 Tage) – den Nutzungszugriff erteilt sich ShizuBox selbst über Shizuku
- **Terminal** mit Verlauf und eigenen Befehls-Bausteinen
- **Schnelle Schalter**: beim Laden wach bleiben, Energiesparmodus, Berührungen anzeigen, Zeigerposition, dunkles Design, Bildschirm-Timeout
- **Universal Installer**: APK, APKS, XAPK, APKM mit Vorschau, Versionsvergleich, Signaturprüfung, automatischer Split-Auswahl und VirusTotal-Scan; stille Installation über Shizuku; als Standard-Installer nutzbar
- **Lautstärke-Boost** bis +100 % über das Maximum, getrennte Stufen für Lautsprecher und Kopfhörer, einstellbare Obergrenze, Gehörschutz-Schalldosis zurücksetzen
- **Benachrichtigungs-Verlauf**: alle Benachrichtigungen lokal gespeichert (nicht im Backup), nach App oder chronologisch, Suche, **gelöschte/zurückgezogene Nachrichten** markiert, Bilder, Fingerabdruck-Sperre
- **Cache aufräumen**: Cache pro App nach Größe, „Alles aufräumen“ mit rollender Anzeige des freien Platzes
- **Privatsphäre-Protokoll**: welche App wann Kamera, Mikrofon, Standort, Kontakte oder die Zwischenablage nutzte – Zeitleiste und pro App, Hinweis auf Zugriffe im Hintergrund oder bei ausgeschaltetem Bildschirm, Berechtigung direkt entziehen, optionale Warnung bei Kamera/Mikrofon im Hintergrund
- **Akku-Report**: Verbrauch pro App (mAh und %, Vorder-/Hintergrund), Wakelocks, mobile Daten und Weckrufe – seit dem Laden, 24 Stunden oder über Nacht, Ladestand-Diagramm, direkte Aktionen (einschränken, beenden, einfrieren, Internet sperren), optionale Morgen-Zusammenfassung
- **Zwischenablage-Verlauf** (auch im Hintergrund, ohne Dauerabfrage): Suche, Anheften, erneut kopieren, Apps ausschließen, Auto-Löschen, Fingerabdruck-Sperre; Passwörter und Passwort-Manager werden nie gespeichert
- **Lautstärke pro App**: jede App bekommt einen eigenen Pegel über der Medienlautstärke (echtes Mischen über Shizuku), auch als Automations-Aktion
- Gaming-Modus (Benachrichtigungen, Bildwiederholrate, Helligkeit, Hintergrund-Apps – wird exakt zurückgesetzt)
- Logcat-Viewer mit Filtern

### Downloader (ohne Shizuku)
- Videos und Musik von YouTube und vielen anderen Seiten über **yt-dlp** und **FFmpeg**
- „Suchen oder URL einfügen“, Trends und lokal abonnierte Kanäle (über **NewPipe Extractor**, kein Konto, keine API-Keys)
- Download-Dialog: Video (bis 2160p, MP4/MKV/WebM) oder nur Audio (MP3/M4A/Opus), Thumbnail und Metadaten einbetten
- Warteschlange mit Pause/Fortsetzen/Erneut, parallele Downloads, läuft im Hintergrund mit Fortschritts-Benachrichtigung
- Downloads-Liste mit Suche, Sortierung, Filter, Abspielen/Teilen/Löschen; „Alle herunterladen“ für Playlists, Kanäle und den Feed
- Teilen-Menü: „Mit ShizuBox herunterladen“ direkt aus YouTube oder dem Browser
- Speicherort wählbar (Standard `Download/ShizuBox`), Cookies für altersbeschränkte Videos, Video→Audio offline, yt-dlp-Update in der App

### Kacheln & Widget
Schnelleinstellungs-Kacheln für Schulmodus, Gaming-Modus, Animationen an/aus, Bildwiederholrate, Wach bleiben (Caffeine), Lautstärke-Boost, Cache aufräumen und Zwischenablage-Verlauf. Gaming bis Cache gibt es auch als Schnellaktionen auf der Startseite, die ersten vier zusätzlich als **Homescreen-Widget** (Material-You-Farben).

### Einstellungen
Sprache, Design (System/Hell/Dunkel, AMOLED, Farben, Paletten, Glas-Effekt), Startseite (Startbereich, Bereiche ein-/ausblenden), Installationsart, VirusTotal-Schlüssel, **Sichern & Wiederherstellen** aller Einstellungen in einer Datei, **Fehlerprotokoll** (Abstürze, Hänger; kopieren/teilen) für Fehlerberichte.

Ohne Shizuku funktionieren: App-Updates, Universal Installer (über den System-Dialog), App-Liste mit Details, APK teilen/extrahieren, VirusTotal und Geräteinfo.

## Voraussetzungen
- Android 12 oder neuer, 64-Bit-ARM (arm64-v8a)
- [Shizuku](https://shizuku.rikka.app/) (oder ein Fork) über kabelloses Debugging gestartet

## Installation
APK aus den [Releases](../../releases) laden und installieren, ShizuBox öffnen und den Shizuku-Zugriff erlauben.

## Bauen
```bash
./gradlew assembleDebug
```
Für signierte Release-Builds wird eine Datei `~/Android/keys/shizubox-release.properties` mit `storeFile`, `storePassword`, `keyAlias` und `keyPassword` erwartet; ohne sie entsteht ein unsigniertes Release.

Übersetzungen liegen in `tools/i18n` (siehe dortige README).

## Lizenz
GPL-3.0, siehe [LICENSE](LICENSE). Der Installer (`app/src/main/java/de/emil/shizubox/installer/`) basiert auf [Universal Installer](https://github.com/pass-with-high-score/universal-installer) von pass-with-high-score (GPL-3.0). Weitere Hinweise in [NOTICE](NOTICE).
