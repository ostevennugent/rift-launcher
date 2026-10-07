# RIFT Launcher (Android)

A native Android home-screen launcher in Kotlin + Jetpack Compose. Cyberpunk look: near-black grid stage, neon plates, Oxanium + IBM Plex Mono type, scanlines and a glowing clock (ported from the original RIFT prototype).

## What it does

- **Brief page**: clock, date, battery, next alarm, and shortcuts.
- **Apps page**: every installed app, A-Z, with search. Press the keyboard's search key to launch the top match.
- **Dock**: up to 5 pinned apps, visible on both pages.
- **Long-press an app**: pin or unpin it from the dock, open App info, or uninstall it.
- **Settings**: neon scheme (Teal, Signal, Amber, Acid), scanlines and clock glow toggles, apps per row (3, 4 or 5), show or hide names, and a shortcut to pick the default home app.
- **Navigation**: swipe between pages or use the Brief / Apps tabs. Back steps up one level. Pressing Home returns to Brief.

## Get the APK

### Option A: GitHub builds it for you (no Android Studio)

1. Push this project to a GitHub repo.
2. Open the repo's **Actions** tab, then the latest **Build APK** run (or click **Run workflow**).
3. Download **rift-launcher-apk** from the run's Artifacts and unzip it to get `app-debug.apk`.

### Option B: Android Studio

1. Open this folder in Android Studio (Koala or newer) and let Gradle sync.
2. **Build > Build Bundle(s) / APK(s) > Build APK(s)**, or press Run with a phone attached.

## Install and set as your launcher

1. Copy `app-debug.apk` to the phone and open it. Allow "install unknown apps" for your file manager or browser if asked.
2. Press Home. Android asks which home app to use: pick **RIFT**, then **Always**. If it doesn't ask, open RIFT, tap the banner on the Brief page, and pick it under **Home app**.

To go back, choose your old launcher the same way.

## Notes

- The APK is a debug build signed with a debug key. It installs fine on your own devices.
- Requires Android 8.0 (API 26) or newer.
