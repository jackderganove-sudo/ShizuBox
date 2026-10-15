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
- **Berechtigungsübersicht**: welche Apps dürfen Kamera, Mikrofon, Standort, Kontakte, … – mit „Entziehen“
- APK teilen, extrahieren (APK bzw. APKS), neu installieren, VirusTotal-Prüfung

### Updates (wie Obtainium/ObtainX)
- Apps aus GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid, APKPure und APKMirror verfolgen
- Suche, Massenimport installierter Apps, Import/Export im ObtainX-/Obtainium-Format
- Alle aktualisieren im Hintergrund mit Fortschrittsbenachrichtigung, optional nur nach sauberem VirusTotal-Ergebnis
- Version überspringen, Sortierung nach Name/Veröffentlichung/Quelle

### System
- **Geräteinfo**: Akku (Temperatur, Spannung, Ladezyklen), RAM, Speicher, CPU-Takt pro Kern, System
- **Prozesse**: RAM-Verbrauch pro App, Apps beenden, Hintergrund aufräumen
- **Bildschirmzeit** pro App (heute / 7 Tage) – den Nutzungszugriff erteilt sich ShizuBox selbst über Shizuku
- **Terminal** mit Verlauf und eigenen Befehls-Bausteinen
- **Schnelle Schalter**: beim Laden wach bleiben, Energiesparmodus, Berührungen anzeigen, Zeigerposition, dunkles Design, Bildschirm-Timeout
- **Universal Installer**: APK, APKS, XAPK, APKM mit Vorschau, Versionsvergleich, Signaturprüfung, automatischer Split-Auswahl und VirusTotal-Scan; stille Installation über Shizuku; als Standard-Installer nutzbar
- Gaming-Modus (Benachrichtigungen, Bildwiederholrate, Helligkeit, Hintergrund-Apps – wird exakt zurückgesetzt)
- Logcat-Viewer mit Filtern

### Kacheln & Widget
Schnelleinstellungs-Kacheln für Schulmodus, Gaming-Modus, Animationen an/aus, Bildwiederholrate und Wach bleiben. Die letzten vier gibt es auch als Schnellaktionen auf der Startseite und als **Homescreen-Widget** (Material-You-Farben).

### Einstellungen
Sprache, Design (System/Hell/Dunkel, AMOLED, Farben, Paletten, Glas-Effekt), Startseite (Startbereich, Bereiche ein-/ausblenden), Installationsart, VirusTotal-Schlüssel, **Sichern & Wiederherstellen** aller Einstellungen in einer Datei.

Ohne Shizuku funktionieren: App-Updates, Universal Installer (über den System-Dialog), App-Liste mit Details, APK teilen/extrahieren, VirusTotal und Geräteinfo.

## Voraussetzungen
- Android 12 oder neuer
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
