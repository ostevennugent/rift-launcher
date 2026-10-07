# RIFT Launcher (Android)

A native Android home-screen launcher in Kotlin + Jetpack Compose. Cyberpunk look: near-black grid stage, neon plates, Oxanium + IBM Plex Mono type, scanlines and a glowing clock (ported from the original RIFT prototype).

## What it does

- **Brief page**: modules you choose and order (clock, date, status plate, quick apps).
- **Apps page**: every installed app, A-Z, with search. Press the keyboard's search key to launch the top match.
- **Config page** (third tab, or the gear in the top strip): everything below is configurable.
  - Show, hide and reorder Brief modules; clock format, size and seconds; which status lines show.
  - Top strip: time, title text, battery. The gear is always visible so Config is always reachable.
  - Apps per row, icon size, app names, dock, tab bar, hidden apps.
  - Neon scheme (Teal, Signal, Amber, Acid), grid background, scanlines, clock glow.
  - Which page opens first, and the set-as-home reminder.
- **Long-press an app**: pin to dock, pin to home, hide, App info, uninstall.
- **Dock**: up to 5 pinned apps, visible on every page.
- **Navigation**: swipe between pages or use the tabs. Back returns to Brief. Pressing Home returns to Brief.
- **Look**: cyberpunk grid stage, neon plates, Oxanium + IBM Plex Mono type (ported from the original RIFT prototype).

## Updates

RIFT checks the `latest-apk` GitHub release on start (at most every 6 hours) and shows a notice on Brief when a newer build exists. Open **Config > Updates** to check now, download and install. Android asks once to let RIFT install apps.

Every push to the `android` branch builds a new APK and replaces the release. All builds are signed with the key in `app/rift.keystore`, so each one installs over the last without losing settings. Because the key is public in this repo, only install updates from a repo you trust.

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
