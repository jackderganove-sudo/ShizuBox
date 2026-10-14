# ShizuBox

Eine All-in-One-Toolbox für Android, die über [Shizuku](https://shizuku.rikka.app/) (ADB-Rechte, **kein Root**) Systemeinstellungen steuert. Glas-Design mit Material You, Hell- und Dunkelmodus.

Entwickelt und getestet auf einem Pixel mit Android 17.

## Funktionen

### Schulmodus
Das Handy bleibt an, aber es passiert absolut nichts – als wäre es aus.
- Flugmodus, WLAN/Bluetooth/mobile Daten aus, „Bitte nicht stören“ ohne Ausnahmen, kein Ton, keine Vibration
- Always-on-Display, Sperrbildschirm-Benachrichtigungen, Kamera-Doppeldruck, Gemini-Geste, Fingerabdruck-Entsperren aus
- Alle Apps pausiert (außer Telefon/Notruf, Einstellungen, Shizuku)
- Komplett schwarzer Bildschirm, auch über dem Sperrbildschirm
- **Ausstieg: 3× schnell die Power-Taste** – danach wird jede Einstellung exakt wiederhergestellt
- Optional automatisches Ende nach einer festen Zeit, Schnelleinstellungs-Kachel
- Überlebt Neustarts; ist Shizuku danach aus, führt eine kurze Anleitung durch die Wiederherstellung

### Customization
Animationsgeschwindigkeit, Bildwiederholrate, Schriftgröße und Fettschrift, Anzeigegröße (DPI), Render-Auflösung (mit 15-s-Rückfrage), Statusleisten-Gruppen ausblenden.

### Apps
- App-Liste mit Größe, Installations-/Update-Datum, Split-Kennzeichnung, Sortierung
- Einfrieren/Auftauen, Profile zum gemeinsamen Einfrieren, Deinstallieren
- Laufzeit-Berechtigungen und AppOps bearbeiten, Akku-Einschränkung (uneingeschränkt/optimiert/eingeschränkt)
- APK teilen, extrahieren (APK bzw. APKS), neu installieren, VirusTotal-Prüfung

### System
- **Universal Installer**: APK, APKS, XAPK, APKM mit Vorschau, Versionsvergleich, Signaturprüfung, automatischer Split-Auswahl und VirusTotal-Scan; stille Installation über Shizuku; als Standard-Installer nutzbar
- Gaming-Modus (Benachrichtigungen, Bildwiederholrate, Helligkeit, Hintergrund-Apps – wird exakt zurückgesetzt)
- Logcat-Viewer mit Filtern, Shell-Konsole

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

## Lizenz
GPL-3.0, siehe [LICENSE](LICENSE). Der Installer (`app/src/main/java/de/emil/shizubox/installer/`) basiert auf [Universal Installer](https://github.com/pass-with-high-score/universal-installer) von pass-with-high-score (GPL-3.0). Weitere Hinweise in [NOTICE](NOTICE).
