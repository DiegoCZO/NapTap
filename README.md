# Tap2Nap

Double tap to turn off the screen on Android. Works on the home screen and on the lock
screen. Small, open source, no ads and no tracking.

Android 9 or newer. Available in English and Spanish.

<p align="center">
  <img src="docs/screenshots/settings.png" width="260" alt="Settings" />
  <img src="docs/screenshots/onboarding.png" width="260" alt="Setup" />
  <img src="docs/screenshots/update.png" width="260" alt="Update" />
</p>

## Download
Grab the latest APK from [Releases](../../releases/latest). If you use
[Obtainium](https://github.com/ImranR98/Obtainium), you can add this repo there and get
updates automatically.

## What it does
- Double tap the home screen or the lock screen to turn the screen off. Each one can be
  turned on or off.
- It won't trigger while the app drawer or recent apps are open, or while you're typing
  your PIN.
- Pocket protection: if the proximity sensor is covered, nothing happens.
- Optional vibration, light, medium or strong. It works even with the phone on silent.
- You can change how fast the double tap has to be.
- When there's a new version the app tells you, shows what changed and installs it.
- Light, dark or system theme, and English or Spanish (language switch needs Android 13+).

## Setup
1. Install the APK and open Tap2Nap. It walks you through the setup.
2. Turn on Tap2Nap in Settings > Accessibility.
3. If Android says the setting is restricted, go to Settings > Apps > Tap2Nap, tap the
   three dots menu, choose "Allow restricted settings" and try again.

## Privacy
Tap2Nap needs the Accessibility permission to know when you tap the screen and which
screen is showing, and to turn the screen off. It only gets the moment of each tap, not
where you tapped, and it never reads what you type. Nothing is collected or sent anywhere.
The only connection it makes is to this repo, to check for updates.

## How it works
The service adds an invisible 1x1 pixel window that gets notified of every touch on the
screen (`FLAG_WATCH_OUTSIDE_TOUCH`) without blocking it. Android doesn't say where the
touch was, only when. If two touches come close enough together, the app checks which
screen is showing and then locks the phone with `GLOBAL_ACTION_LOCK_SCREEN`, the same as
the power button. That's why fingerprint and face unlock keep working.

If the first tap opens or scrolls something, it doesn't count.

## Known issues
- Since the app doesn't know where you tapped, a double tap on a part of a widget that
  doesn't react to touches will still turn the screen off.
- Detecting the PIN pad, the app drawer and recent apps depends on view names that change
  between phones and launchers. It's tested on the Pixel Launcher. For others you may
  need to add names to `BOUNCER_ID_HINTS` or `LAUNCHER_OVERLAY_IDS` in
  `DoubleTapService.kt`.

## Building
Open the project in Android Studio, or run `./gradlew assembleRelease` with JDK 17 or 21.

Pushing a tag like `v1.13` builds the APK on GitHub and publishes a release. The release
notes are the commit messages since the previous tag.

## License
[MIT](LICENSE), 2026 DiegoCZO
