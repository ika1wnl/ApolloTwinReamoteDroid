# UAD Apollo Twin Remote (Android App + Windows WLAN Bridge)

Eine Open-Source-Fernsteuerung für dein **Universal Audio Apollo Twin** unter Windows – speziell entwickelt, um direkt vor dem Mikrofon über dein Android-Handy den **Unison Preamp** (Gain, +48V, Pad, Low-Cut, Phase), die **Unison-Plugin-Regler** (Drive, Output, EQ) sowie die **Console-Fader** (Mute, Solo, Level) zu steuern.

---

## Warum gibt es zwei Teile (`android-app` & `windows-server`)?
Die **UA Mixer Engine** von Universal Audio kommuniziert über ein unverschlüsseltes JSON/TCP-Protokoll auf **Port 4710** (jede Nachricht endet mit einem Null-Byte `\0`). Unter Windows nimmt dieser Prozess aus Sicherheitsgründen jedoch nur Verbindungen vom selben PC (`127.0.0.1:4710`) an.

Damit dein Android-Handy über WLAN mit der UAD Console sprechen kann, enthält dieses Projekt:
1. **`android-app/`**: Das vollständige, native Android-Studio-Projekt (Kotlin & Jetpack Compose) inkl. **GitHub Actions Workflow**, der dir bei jedem Upload auf GitHub automatisch die fertige `.apk`-Datei baut.
2. **`windows-server/`**: Ein schlankes Python-Skript (`apollo_windows_bridge.py`), das Port `47100` im WLAN für die Android-App auf `127.0.0.1:4710` weiterleitet **und** gleichzeitig auf Port `8080` eine sofort nutzbare Web-App bereitstellt.

---

## Anleitung: So lädst du das Projekt auf GitHub hoch & holst dir die `.apk`

1. **Neues GitHub-Repository erstellen:**
   * Gehe auf [github.com/new](https://github.com/new) und erstelle ein neues Repository (z. B. `apollo-twin-remote`).
2. **Dateien hochladen:**
   * Entpacke diese ZIP-Datei auf deinem PC.
   * Klicke in deinem neuen GitHub-Repository auf **"uploading an existing file"** und ziehe alle Ordner (`.github`, `android-app`, `windows-server`, `README.md`) hinein. Klicke unten auf **"Commit changes"**.
3. **Fertige Android-APK herunterladen:**
   * Sobald die Dateien hochgeladen sind, klicke oben in deinem GitHub-Repository auf den Reiter **"Actions"**.
   * Dort läuft automatisch der Workflow **"Build Android APK"** (dauert ca. 2–3 Minuten).
   * Klicke auf den abgeschlossenen Durchlauf und lade unten unter **Artifacts** die Datei **`Apollo-Twin-Remote-APK`** herunter.

---

## Einrichtung unter Windows (am Studio-PC)

1. Stelle sicher, dass **Python 3** auf deinem Windows-PC installiert ist (keine externen Pakete nötig!).
2. Öffne den Ordner `windows-server/` und doppelklicke auf **`START_WINDOWS_BRIDGE.bat`**.
3. Im Fenster wird dir deine lokale WLAN-IP angezeigt (z. B. `192.168.178.45`).
   * *Hinweis:* Wenn die Windows-Firewall beim ersten Start fragt, erlaube den Zugriff für *Private Netzwerke (Heimnetzwerk)*.
4. Öffne die **Apollo Twin Remote App** auf deinem Android-Smartphone, gib die angezeigte IP-Adresse ein und tippe auf **Verbinden**!
   * *Alternativ ohne APK-Installation:* Öffne einfach Chrome auf deinem Android-Handy und rufe `http://<DEINE-PC-IP>:8080` auf.

---

## Unterstützte UAD-Kommandos (Auszug)

| Funktion | UAD Mixer Engine TCP-Pfad |
| :--- | :--- |
| **Unison Preamp Gain** | `set /devices/0/inputs/{ch}/preamps/0/GainTapered/value {0.0 - 1.0}` |
| **+48V Phantomspeisung** | `set /devices/0/inputs/{ch}/preamps/0/48V/value {true/false}` |
| **Pad (-20 dB)** | `set /devices/0/inputs/{ch}/preamps/0/Pad/value {true/false}` |
| **Low-Cut Filter** | `set /devices/0/inputs/{ch}/preamps/0/LowCut/value {true/false}` |
| **Phase (Polarität)** | `set /devices/0/inputs/{ch}/preamps/0/Phase/value {true/false}` |
| **Unison Plugin Parameter** | `set /devices/0/inputs/{ch}/preamps/0/plugins/0/parameters/{id}/Value/value {0.0 - 1.0}` |
| **Console Kanal-Fader** | `set /devices/0/inputs/{ch}/FaderLevelTapered/value {0.0 - 1.0}` |
| **Mute / Solo** | `set /devices/0/inputs/{ch}/Mute/value {true/false}` |
