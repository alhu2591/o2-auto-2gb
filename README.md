# O2 Auto 2GB 📱

Eine Android-App, die automatisch auf O2-SMS antwortet, um das 2GB-Datenpaket zu verlängern.

## Funktionsweise

1. O2 sendet eine SMS mit dem Wort **"Weiter"** an deine Nummer (von +4980112 oder 80112)
2. Diese App erkennt die SMS automatisch im Hintergrund
3. Die App antwortet sofort mit **"Weiter"**
4. Dein 2GB-Paket wird automatisch verlängert ✅

Die SMS-Benachrichtigung wird unterdrückt – sie erscheint **nicht** in deiner normalen SMS-App.

## Voraussetzungen

- Android 8.0 (Oreo) oder höher
- SMS-Berechtigungen (werden beim ersten Start angefragt)
- Benachrichtigungs-Berechtigung (Android 13+)

## Installation

### Option A: APK direkt installieren
1. Lade die APK aus den [GitHub Actions Artifacts](../../actions) herunter
2. Aktiviere "Unbekannte Quellen" in den Android-Einstellungen
3. Installiere die APK
4. Starte die App und erteile alle Berechtigungen

### Option B: Selbst bauen
```bash
git clone https://github.com/dein-repo/o2-auto-2gb
cd o2-auto-2gb
./gradlew assembleDebug
# APK unter: app/build/outputs/apk/debug/app-debug.apk
```

## Technische Details

| Eigenschaft | Wert |
|---|---|
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Sprache | Kotlin |
| Build-System | Gradle 8.9 |
| AGP | 8.7.3 |

### Komponenten

- **MainActivity** – UI, Berechtigungsanfragen, Service-Steuerung
- **SmsReceiver** – BroadcastReceiver (Priority 999) für eingehende SMS
- **SmsReplyWorker** – WorkManager-Worker zum zuverlässigen Senden der Antwort
- **SmsService** – Foreground Service (bleibt im Hintergrund aktiv)
- **BootReceiver** – Startet den Service nach dem Neustart automatisch

## Berechtigungen

| Berechtigung | Zweck |
|---|---|
| RECEIVE_SMS | Eingehende SMS empfangen |
| READ_SMS | SMS lesen |
| SEND_SMS | Automatische Antwort senden |
| RECEIVE_BOOT_COMPLETED | Autostart nach Neustart |
| FOREGROUND_SERVICE | Hintergrundservice |
| WAKE_LOCK | Gerät aktiv halten |
| POST_NOTIFICATIONS | Benachrichtigung (Android 13+) |

## Lizenz

MIT License – Details in [LICENSE](LICENSE)
