# ShizuBox – Projektstand

All-in-One-Toolbox für Android, die über **Shizuku** (ADB-Rechte, kein Root) Systemeinstellungen steuert.
Glas-Design (Haze) mit Material You, 5 Sprachen (de, en, tr, es, fr).

| | |
|---|---|
| Paketname | `de.emil.shizubox` |
| Version | **0.7.0** (versionCode 6, veröffentlicht 2026-10-16) |
| Ziel-Gerät | Pixel 11 Pro, Android 17 (SDK 37), Shizuku-Fork „Shevery“ |
| minSdk / targetSdk | 31 / 37 |
| Repo | https://github.com/jackderganove-sudo/ShizuBox (öffentlich, GPL-3.0) |

Legende: ✅ fertig & getestet · 🟡 teilweise · ⬜ Platzhalter

## Funktionen

### Schulmodus (`school/`) – nicht verändern!
| Funktion | Status |
|---|---|
| Aktivieren per Knopf, Startseiten-Karte und Schnelleinstellungs-Kachel | ✅ |
| Snapshot aller geänderten Einstellungen, exakte Wiederherstellung | ✅ |
| Flugmodus, WLAN/BT/Daten aus, DND, kein Ton/Vibration, AOD/Sperrbildschirm-Benachrichtigungen/Kamera-Doppeldruck/Gemini/Fingerabdruck aus | ✅ |
| Apps pausieren (`pm suspend`) außer Telefon/Notruf, Einstellungen, Shizuku, Launcher | ✅ |
| Schwarzer Bildschirm (`BlackActivity` + Foreground-Service), auch über dem Sperrbildschirm | ✅ |
| Ausstieg 3× Power (Polling), Auto-Ende nach fester Zeit | ✅ |
| Neustart: Zustand im geräte­geschützten Speicher, „Sofort schwarz“ oder „Erst mit Shizuku“, Anleitung ohne Shizuku | ✅ |
| Zeitplan (Wochentage + Uhrzeit, einmal aussetzen) | ✅ |
| Notausstieg per adb (`AdbExitReceiver`) | ✅ |

### Customization (`customization/`)
| Funktion | Status |
|---|---|
| Animationsgeschwindigkeit, Bildwiederholrate, Schriftgröße, Fettschrift | ✅ |
| DPI und Render-Auflösung mit 15-s-Rückfrage (Einhandmodus wird währenddessen aus) | ✅ |
| Statusleisten-Gruppen ausblenden (System-Icons, Uhr, Benachrichtigungen) | ✅ |
| Immersive-Modus pro App | ⬜ unmöglich seit Android 11 (Hinweis in der App) |

### Apps (`apps/`, `ui/apps/`)
| Funktion | Status |
|---|---|
| App-Liste mit Größe, Datum, Split, Filter, Sortierung | ✅ |
| Mehrfachauswahl (lange drücken): einfrieren, auftauen, beenden, deinstallieren, zu Profil | ✅ |
| Einfrieren/Auftauen, Profile, Deinstallieren (System-Apps nur einfrieren) | ✅ |
| Laufzeit- und Entwickler-Berechtigungen, AppOps, Akku-Modus, Standby-Gruppe | ✅ |
| App-Sprache pro App, Aktivitäten starten / Verknüpfung, technische Details mit Signatur | ✅ |
| Berechtigungsübersicht nach Kategorie mit „Entziehen“ | ✅ |
| Activity Launcher (`ActivityLauncher`): alle Apps, Suche nach Activity, Favoriten, Shizuku-Fallback, Cache | ✅ |
| APK teilen/extrahieren/neu installieren, VirusTotal | ✅ |
| Debloat-Vorschläge (`Debloat`, Apps → Debloat-Vorschläge): Basis UAD-ng-Liste (GPL-3.0, Auszug in `assets/debloat_uad.json`, in NOTICE), Einstufung sicher/Vorsicht/nicht anfassen mit Pixel-Overrides, eigene Erklärungen für 32 Apps (5 Sprachen) + UAD-Details, Mehrfachauswahl, nur einfrieren, Protokoll mit Einzel-/Komplett-Rückgängig, gesperrt: Telefon, Einstellungen, System UI, Shizuku, Launcher, Tastatur, Play-Dienste/Store, Shell u. a. | ✅ getestet (Osterei einfrieren + rückgängig) |
| Internet-Sperre pro App ohne VPN (`NetBlock`): Firewall-Kette OEM_DENY_3 (`cmd connectivity set-chain3-enabled` + `set-package-networking-enabled`, Android 17 ✓ – gesperrte App hat gar kein Netz), Schalter in App-Details, Filter „Kein Internet“ + Badge, Mehrfachauswahl, Wiederherstellung sobald Shizuku läuft (Regeln sind nach Neustart weg), System-UIDs und ShizuBox gesperrt, Kette aus wenn nichts gesperrt | ✅ getestet (Rechner-App, ShizuBox selbst, simulierter Neustart) |

### Updates (`updates/`, `ui/updates/`) – nach Obtainium/ObtainX
| Funktion | Status |
|---|---|
| Quellen: GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid, APKPure | ✅ |
| APKMirror (nur Versionen, Download über In-App-Browser) | 🟡 keine automatischen Downloads möglich |
| Suche, Massenimport, URL-Liste, GitHub-Sterne, ObtainX-Import/Export | ✅ |
| Hintergrundprüfung, „Alle aktualisieren“ als Vordergrund-Arbeit mit Fortschritt | ✅ |
| Sicherheit: VirusTotal-Sperre, Signatur-Sperre | ✅ |
| Version überspringen, Sortierung, Anheften | ✅ |
| Selbst-Update: ShizuBox trägt `github.com/jackderganove-sudo/ShizuBox` automatisch ein (`Updates.ensureSelfTracked`), eigenes Update ohne VirusTotal-Sperre, wenn die Signatur passt; `InstallGuard` erkennt das Ende der eigenen Installation am neuen Versionsnamen | ✅ Quelle getestet (findet Releases + APK) |
| VirusTotal-Upload/Analyse app-weit (`VirusTotal.startUpload`, `scans`), bis 10 min, Datei-Bericht als Abkürzung | ✅ getestet (4-KB-Testdatei: queued → clean in 28 s) |
| Benachrichtigung mit „Installieren“/„Alle installieren“/„Überspringen“ (`UpdateNotifications`) | ✅ Darstellung getestet (Test-Einträge), Installation nutzt den getesteten Update-Pfad |

### System (`system/`, `ui/system/`)
| Funktion | Status |
|---|---|
| Geräteinfo (Akku, RAM, Speicher, CPU pro Kern, System, Display) | ✅ |
| Prozesse (RAM pro App aus `dumpsys meminfo`, beenden, Hintergrund aufräumen) | ✅ |
| Bildschirmzeit (UsageStats, Zugriff per Shizuku selbst erteilt) | ✅ |
| Terminal mit Verlauf und Bausteinen | ✅ |
| Schnelle Schalter (wach bleiben, Energiesparen, Berührungen, Zeiger, dunkles Design, Timeout) | ✅ |
| Gaming-Modus mit exakter Wiederherstellung | ✅ |
| Universal Installer (APK/APKS/XAPK/APKM, Split-Auswahl, Signatur, VirusTotal, Öffnen-mit) | ✅ |
| Schnell-Installer, Logcat-Viewer | ✅ |
| Lautstärke-Boost (`VolumeBoost`, `BoostService`): `LoudnessEnhancer` auf Session 0 (Android 17: Steuerung ✓, liegt im Output-Mix), bis +100 % (= +10 dB), Obergrenze, getrennte Stufen Lautsprecher/Kopfhörer mit Auto-Umschaltung, Kopfhörer-Warnung, Kachel + Schnellaktion, startet nach Neustart/Update wieder | ✅ Ein/Aus/Service getestet; Lautstärke per Ohr noch vom Nutzer zu prüfen |
| Benachrichtigungs-Verlauf (`notifications/NotifHistory`, `HistoryListener`, eigene SQLite-DB, nicht im Backup): Zugriff per `cmd notification allow_listener` (Android 17 ✓, sonst Einstellungs-Dialog), chronologisch / nach App / „Entfernt“, Suche, App-Filter, Unterhaltungen über mehrere Benachrichtigungen zusammengeführt, „Gelöscht“ (Nachricht verschwand aus aktualisiertem Chat) und „Zurückgezogen“ (App hat entfernt, ohne dass die App geöffnet wurde – via UsageStats), Bilder (BigPicture; MessagingStyle-Bilder nur falls lesbar), Apps ausschließen, Auto-Löschen nach 7/30/90 Tagen, alles löschen, Fingerabdruck-Sperre | ✅ mit Shell-Test-Benachrichtigungen + simuliertem Chat getestet; mit echtem WhatsApp noch vom Nutzer zu prüfen |
| Cache aufräumen (`CacheCleaner`, System → Cache aufräumen): Cache pro App nach Größe (StorageStatsManager), „Alles aufräumen“ = eigener Cache + `pm trim-caches 999G`, freigegebener Platz mit rollenden Ziffern, Warnung bei KI-Modellen (AICore), Schnellaktion + Kachel „Cache aufräumen“ | 🟡 eigener Cache getestet (912 MB); `trim-caches 999G` noch nicht voll ausgeführt (würde 7,8 GB AICore-Modelle löschen). Einzel-App-Cache per Shell unmöglich: `pm clear --cache-only` hängt auf Android 17 und löscht nichts → „Öffnen“ führt in die App-Einstellungen |
| Gehörschutz: Schalldosis (CSD) zurücksetzen/wiederherstellen (`cmd audio set-sound-dose-value`); abschalten geht per Shell nicht | ✅ |

### Automationen (`automation/`, `ui/automation/`)
| Funktion | Status |
|---|---|
| Regeln Wenn → Dann, an/aus, Liste + Editor, Vorlagen (Brawl Stars → Gaming, Kopfhörer → Boost aus, Laden → wach bleiben, Akku < 15 % → 60 Hz + Animationen aus, 22:30 Nachtruhe, 07:00 Guten Morgen), im Backup | ✅ |
| Auslöser: App geöffnet/geschlossen (UsageEvents, Abfrage nur bei Bildschirm an + App-Regel), Uhrzeit + Wochentage (exakter Wecker), Kopfhörer/Bluetooth-Gerät (AudioDeviceCallback, Gerätename ohne BT-Berechtigung), Ladekabel + Akku unter X % (ACTION_BATTERY_CHANGED), WLAN (NetworkCallback + `cmd wifi status` für den Namen) | ✅ Akku-, App- (simuliert) und Zeit-Regel auf dem Pixel getestet |
| Aktionen: Gaming, Medienlautstärke, Boost, Bildwiederholrate, Animationen, Graustufen (`accessibility_display_daltonizer*`), Bitte nicht stören (`cmd notification set_dnd`), wach bleiben, Internet-Sperre, App/Activity starten (per `am start`), Shell | ✅ Internet-Sperre + Shell getestet; Graustufen/DND-Befehle laufen, Wirkung vom Nutzer zu prüfen |
| „Danach zurücksetzen“: vorheriger Zustand wird beim Start gemerkt und beim Ende rückwärts wiederhergestellt (auch nach Neustart gespeichert) | ✅ getestet |
| Schulmodus: keine Aktion kann ihn starten/beenden; solange er aktiv ist, pausieren alle Regeln | ✅ |
| Foreground-Service nur, wenn eine Nicht-Zeit-Regel aktiv ist; startet nach Neustart/Update wieder | ✅ |

### Downloader (`downloader/`, `ui/downloader/`) – ohne Shizuku
| Funktion | Status |
|---|---|
| URL-Download mit Format-Dialog (Video bis 2160p/MP4/MKV/WebM, Audio MP3/M4A/Opus, Thumbnail/Metadaten einbetten) | ✅ getestet (MP4, MP3 mit Cover) |
| Warteschlange (Läuft/Wartend/Fehlgeschlagen, Pause/Fortsetzen/Abbrechen/Erneut, parallele Downloads) im Foreground-Service mit Benachrichtigung | ✅ |
| Teilen-Menü „Mit ShizuBox herunterladen“ (`DownloadShareActivity`) | ✅ getestet |
| Downloads-Liste (Suche, Sortierung, Filter Audio/Video, Abspielen/Teilen/Löschen/Ordner) | ✅ |
| Suche und Trends über NewPipe Extractor, Kanäle abonnieren (lokal), Abo-Feed (RSS) | ✅ getestet |
| „Alle herunterladen“ für Feed, Suche, Playlists, Kanäle | ✅ |
| Mehr: Standard-Format, temporäre Dateien, Video→Audio (FFmpeg), yt-dlp-Update (2025.11.12 → 2026.08.19), Abos | ✅ getestet |
| Mehr: eigener Ordner (SAF), Cookies-Import | ✅ vom Nutzer bestätigt |
| Warteschlange: Pause/Fortsetzen bei langen Downloads | ✅ vom Nutzer bestätigt |

### Kacheln & Widget (`tiles/`)
| Funktion | Status |
|---|---|
| Kacheln: Schulmodus, Gaming, Animationen, Bildwiederholrate, Wach bleiben (Caffeine: Timeout „nie“, unabhängig vom Laden) | ✅ |
| Schnellaktionen auf der Startseite (gleiche Logik `QuickActions`) | ✅ |
| Homescreen-Widget (RemoteViews, Material-You-Farben) | ✅ |

### Animationen (`ui/motion/`)
| Funktion | Status |
|---|---|
| Zentrale Federn (`Motion`), Expressive MotionScheme, ButtonShapes-Morphing | ✅ |
| Shared-Element-Übergang Startseite → Bereich, Unterseiten federnd | ✅ |
| Navigations-Pille (gleiten + strecken), Seitenwechsel seitlich | ✅ |
| Listen gestaffelt + animateItem, rollende Ziffern, Fortschritt, leere Zustände | ✅ |

### Einstellungen (`settings/`, `ui/settings/`)
| Funktion | Status |
|---|---|
| Sprache (per-App-Locale), Design, AMOLED, Akzentfarben/Paletten (MaterialKolor), Glas, Hintergrundanimation | ✅ |
| Startseite: Startbereich, Bereiche ein-/ausblenden, Schulmodus-Karte, Schnellaktionen | ✅ |
| Installationsart, Standard-Optionen, VirusTotal-Schlüssel, Signatur-Sperre | ✅ |
| Sichern & Wiederherstellen (JSON, Secrets optional) | ✅ |
| ShizuBox selbst aktualisieren (verfolgt sich in Updates) | ✅ |

## Projektstruktur

```
app/src/main/java/de/emil/shizubox/
  ShizuBoxApp.kt        Init aller Singletons (Reihenfolge wichtig: Str zuerst)
  MainActivity.kt       Navigation (Section-Enum + Unterseiten-States), Fenster-Blur
  Str.kt                t()/tp() – Strings über den App-Kontext (Sprachwechsel live)
  shizuku/              ShizukuManager (Status, Bindung, exec/writeFile), ShellService (UserService)
  school/               Schulmodus: Mode, Commands, Store (DataStore), Service, BlackActivity,
                        ExitGuide, Schedule, Receivers, Kachel, Log
  customization/        Animationen, Refresh, Schrift, DPI/Auflösung, Statusleiste
  apps/                 Apps (Liste, Details, Aktionen), Profiles, PermissionOverview, ApkExport, ActivityLauncher
  installer/            Universal Installer (Ackpine), ApkParser, SignatureCheck, VirusTotal
  updates/              Sources, Updates (Zustand/Download/Install), UpdateWorker, Search,
                        BulkImport, TrackedApp, DebugReceiver (adb-Diagnose)
  system/               Gaming, Tweaks, DeviceInfo, Processes, ScreenTime, Logcat, ApkInstaller
  tiles/                QuickActions, TileServices, Widget
  settings/             AppSettings, Language, Backup
  debug/                ScreenRenderer (Offscreen-Screenshots per adb)
  downloader/           Ytdl (yt-dlp/FFmpeg), DownloadQueue, DownloadService, DlStorage, Discover (NewPipe), Settings, ShareActivity
  ui/motion/            Motion (Federn, pressScale, RollingText, Stagger), Shared (Container-Übergang), EmptyState
  ui/                   Compose-Screens je Bereich, ui/glass (GlassCard, GlassBackground), ui/theme
tools/i18n/             Übersetzungen (strings.json + tr_<lang>.tsv) und Generator
```

## Technik
- **Kotlin + Jetpack Compose + Material 3**, AGP 9.4.1 (eingebautes Kotlin 2.4.21), Compose-BOM 2026.09.00
- Downloader: youtubedl-android 0.18.1 (yt-dlp + Python + FFmpeg, nur arm64-v8a, `useLegacyPackaging`), NewPipe Extractor v0.26.5 (JitPack). APK ≈ 61 MB.
- Material3 **1.5.0-beta01** (Expressive-APIs: MotionScheme, ButtonShapes, MaterialShapes)
- Libraries: Shizuku API 13.1.5, Haze 2.0.1 (Glas), Ackpine 0.25.4 (Installer), WorkManager 2.12.0, MaterialKolor 5.0.1, DataStore
- **Shizuku-Befehle**: `ShizukuManager.exec(cmd)` → AIDL `IShellService` (UserService im Shell-Prozess) führt `sh -c` aus,
  Rückgabe `ShellResult(code, out, err)`. `writeFile(fd, path)` kopiert Dateien als Shell-Nutzer.
  Jeder Befehl wurde vorher auf dem Pixel getestet.
- **Speicher**: alles im geräte­geschützten Speicher (Direct Boot), SharedPreferences bzw. DataStore (Schulmodus)
- **Build**: JDK unter `~/Android/jdk`, `JAVA_HOME=$HOME/Android/jdk ./gradlew assembleRelease`; R8 auch im Debug.
  Signierung über `~/Android/keys/shizubox-release.properties` (außerhalb des Repos).
- **i18n**: siehe `tools/i18n/README.md`; Enums speichern `@StringRes` und lösen über `t()` auf.
- **Tests ohne Entsperren**: `DebugReceiver` (DUMP-geschützt): `--es test …` für Backend-Prüfungen, `--es shot …` rendert
  Bildschirme auf einem privaten virtuellen Display nach `/data/local/tmp/shot_<name>.png`.

## Bekannte Probleme & Einschränkungen
- Android 17: `pm uninstall --user 0` für System-Apps verweigert → nur einfrieren
- `icon_blacklist` (einzelne Statusleisten-Icons) wirkt nicht mehr, nur Gruppen
- `cmd display set-user-preferred-display-mode` und SurfaceFlinger-Overlays sind für die Shell gesperrt
- `wm size` kann die SystemUI der Beta crashen, wenn der Einhandmodus an ist (wird abgefangen)
- Pixel meldet Akku-Zustand 11 (herstellerspezifisch) → Zeile wird ausgeblendet
- Kachel-Tipps bei gesperrtem Gerät hält SystemUI bis zum Entsperren zurück
- Nicht exportierte Activities (z. B. Settings-Testmenü unter Android 17) kann nur die App selbst starten – auch nicht per Shizuku
- APKPure-CDN wird von manchen DNS-Filtern (NextDNS) und VPNs (Google-VPN) blockiert: ShizuBox versucht es ein zweites Mal und zeigt dann einen Hinweis mit Ursache (VPN aktiv / privates DNS mit Namen / sonst Netz). Genaue Ursache beim Google-VPN noch zu messen (`--es test apkpure` bei eingeschaltetem VPN)
- Stille XAPK-Installationen ließen das Handy 2026-10-16 zweimal hängen (Crunchyroll, Google Home) → `InstallGuard` (Journal, Pause der Version, keine Wiederholung nach Neustart, nur bei Bildschirm aus, Paketverwaltungs-Check, 6-min-Wächter), Fortschritt gedrosselt, harte Shell-Zeitlimits (`execLimited`, Code 124). Diagnose: `--es test updstate` (offene Installationen, liegengebliebene Dateien), `--es test ig`
- `pm clear --cache-only` hängt auf Android 17 dauerhaft (auch `cmd package clear --user 0 …`) und löscht nichts
- APKMirror erlaubt keine automatischen Downloads
- R8: commons-compress (youtubedl-android) und NewPipe/Rhino brauchen Keep-Regeln, sonst scheitert das Entpacken bzw. die Extraktion
- uiautomator wird durch die pulsierende Shizuku-Animation nie „idle“ → Koordinaten per Screenshot bestimmen

## Offene Ideen
- Schulmodus-Statistik, Debloat-Vorschläge mit Rückgängig, weitere Kacheln, Update-Benachrichtigung mit „Installieren“
