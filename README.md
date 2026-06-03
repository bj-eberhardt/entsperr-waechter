# EntsperrWächter

- [Deutsche Version](#entsperrwächter)
- [English Version](#english-version)

![](assets/markting_banner_fit.png)

Android-App für eigene Geräte zur transparenten Erfassung von Entsperrereignissen mit optionaler Frontkamera-Aufnahme.

## Tech-Stack

- Android `minSdk 21`, `targetSdk 35`
- Kotlin, Jetpack Compose, DataStore
- Device Admin Receiver für Entsperr-Callbacks, sofern vom System unterstützt
- CameraX im Foreground Service
- Lokale Speicherung über MediaStore

## Entwicklung

1. Projekt in Android Studio öffnen.
2. Gradle-Sync ausführen.
3. Auf einem echten Gerät testen; Emulatoren bilden Sperrbildschirm-, Device-Admin- und Kameraverhalten nur eingeschränkt ab.
4. In der App Setup abschließen:
   - Berechtigungen erteilen
   - Benachrichtigungen erlauben
   - Geräteadministrator aktivieren

## Sprachen

- Deutsch ist die Basisressource unter `app/src/main/res/values/strings.xml`.
- Englisch liegt unter `app/src/main/res/values-en/strings.xml`.
- Neue sichtbare Texte gehören immer in String-Ressourcen, nicht direkt in Compose oder Kotlin.

## Privacy Page auf GitHub Pages

- Die Privacy-Templates liegen unter `assets/privacy.html` und `assets/privacy.en.html`.
- Die GitHub-Action unter `.github/workflows/privacy-pages.yml` veröffentlicht bei jedem Push auf `master` automatisch auf GitHub Pages.
- Auf der Pages-Root liegt eine kleine Startseite mit Google-Play-Link sowie Links zur deutschen und englischen Privacy Page.
- Die Ausgabe wird lokal über `scripts/build-privacy-pages.ps1` und auf GitHub über `scripts/build-privacy-pages.sh` erzeugt.
- Ziel-URLs:
  - Deutsch: `/privacy/`
  - Englisch: `/privacy/en/`

### Benötigte GitHub-Variable

Lege in GitHub unter Repository Settings → Secrets and variables → Actions eine Repository Variable mit diesem Namen an:

- `PRIVACY_CONTACT_EMAIL`

Diese Variable wird in beide Privacy-Seiten als sichtbare Kontaktadresse eingefügt. Fehlt sie, schlägt der Workflow bewusst fehl.

### Lokaler Test der Privacy-Seiten

```powershell
.\scripts\build-privacy-pages.ps1 -ContactEmail kontakt@example.com
```

Danach liegen die generierten Seiten unter:

- `pages-dist/index.html`
- `pages-dist/privacy/index.html`
- `pages-dist/privacy/en/index.html`

## Release-Signierung

Die Release-Signierung läuft über Gradle und den Wrapper, nicht über den Android-Studio-Exportdialog.

Erforderliche Variablen oder Gradle-Properties:

- `UC_STORE_FILE`
- `UC_STORE_PASSWORD`
- `UC_KEY_ALIAS`
- `UC_KEY_PASSWORD`

Diese Werte können entweder als Umgebungsvariablen gesetzt oder lokal in `%USERPROFILE%\.gradle\gradle.properties` beziehungsweise `~/.gradle/gradle.properties` hinterlegt werden.

Beispiel für `~/.gradle/gradle.properties`:

```properties
UC_STORE_FILE=/abs/path/to/upload-keystore.jks
UC_STORE_PASSWORD=...
UC_KEY_ALIAS=upload
UC_KEY_PASSWORD=...
```

## Release ausführen

Windows:

```powershell
.\release.ps1
.\release.ps1 -AssembleApk
```

Linux/macOS:

```bash
./release.sh
./release.sh --assemble-apk
```

Direkt über Gradle Wrapper:

```bash
./gradlew bundleRelease
```

Die Skripte setzen standardmäßig ein lokales `GRADLE_USER_HOME` auf `.gradle-user-home`, damit globale Wrapper-Locks das Release nicht blockieren.

Standard-Ausgabepfade:

- AAB: `app/build/outputs/bundle/release/app-release.aab`
- APK: `app/build/outputs/apk/release/app-release.apk`

## Hinweise

- Android zeigt während einer Kameraaufnahme bewusst eine sichtbare Foreground-Service-Benachrichtigung.
- Je nach Hersteller können Boot-Receiver, Hintergrundstarts und Device-Admin-Callbacks eingeschränkt sein.
- Für eine Veröffentlichung im Store sind transparente Offenlegung, Zustimmung und eine Datenschutzerklärung erforderlich.

---

# English Version

![](assets/markting_banner_english_fit.png)

Android app for your own devices to transparently capture unlock events with optional front camera recording.

## Tech Stack

- Android `minSdk 21`, `targetSdk 35`
- Kotlin, Jetpack Compose, DataStore
- Device Admin Receiver for unlock callbacks where supported by the system
- CameraX in a foreground service
- Local storage via MediaStore

## Development

1. Open the project in Android Studio.
2. Run Gradle sync.
3. Test on a real device; emulators do not reliably reflect lockscreen, device admin, and camera behavior.
4. Complete the in-app setup:
   - Grant permissions
   - Allow notifications
   - Enable device admin

## Languages

- German is the base resource under `app/src/main/res/values/strings.xml`.
- English is provided under `app/src/main/res/values-en/strings.xml`.
- New user-facing text should always go into string resources, not directly into Compose or Kotlin code.

## Privacy Page on GitHub Pages

- The privacy templates live in `assets/privacy.html` and `assets/privacy.en.html`.
- The GitHub Action in `.github/workflows/privacy-pages.yml` publishes automatically to GitHub Pages on every push to `master`.
- The Pages root contains a small landing page with a Google Play link and links to the German and English privacy pages.
- Output is generated locally by `scripts/build-privacy-pages.ps1` and on GitHub by `scripts/build-privacy-pages.sh`.
- Target URLs:
  - German: `/privacy/`
  - English: `/privacy/en/`

### Required GitHub Variable

Create a repository variable in GitHub under Repository Settings → Secrets and variables → Actions with this name:

- `PRIVACY_CONTACT_EMAIL`

This variable is inserted into both privacy pages as the visible contact address. If it is missing, the workflow fails intentionally.

### Local Privacy Page Test

```powershell
.\scripts\build-privacy-pages.ps1 -ContactEmail contact@example.com
```

The generated pages are then available at:

- `pages-dist/index.html`
- `pages-dist/privacy/index.html`
- `pages-dist/privacy/en/index.html`

## Release Signing

Release signing is handled through Gradle and the wrapper, not through the Android Studio export dialog.

Required variables or Gradle properties:

- `UC_STORE_FILE`
- `UC_STORE_PASSWORD`
- `UC_KEY_ALIAS`
- `UC_KEY_PASSWORD`

You can define these either as environment variables or locally in `%USERPROFILE%\.gradle\gradle.properties` or `~/.gradle/gradle.properties`.

Example for `~/.gradle/gradle.properties`:

```properties
UC_STORE_FILE=/abs/path/to/upload-keystore.jks
UC_STORE_PASSWORD=...
UC_KEY_ALIAS=upload
UC_KEY_PASSWORD=...
```

## Running a Release

Windows:

```powershell
.\release.ps1
.\release.ps1 -AssembleApk
```

Linux/macOS:

```bash
./release.sh
./release.sh --assemble-apk
```

Directly via the Gradle wrapper:

```bash
./gradlew bundleRelease
```

The scripts set a local `GRADLE_USER_HOME` to `.gradle-user-home` by default so global wrapper locks do not block the release run.

Default output paths:

- AAB: `app/build/outputs/bundle/release/app-release.aab`
- APK: `app/build/outputs/apk/release/app-release.apk`

## Notes

- Android intentionally shows a visible foreground-service notification during camera recording.
- Depending on the manufacturer, boot receivers, background starts, and device admin callbacks may be restricted.
- For store publication, transparent disclosure, consent, and a privacy policy are required.
