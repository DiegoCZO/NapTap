# Nap Tap

Double tap to sleep for Android.

A small app (Kotlin) that turns the screen off with a double tap on the **home screen**
and/or the **lock screen**, using an accessibility service. Requires Android 9 or later.

## Features
- Double tap on the home screen and/or the lock screen to turn the screen off.
- Ignored while the app drawer or the recent apps screen is open, and while the lock
  screen PIN pad is showing.
- **Pocket protection**: does nothing if the proximity sensor is covered.
- Optional vibration.
- Adjustable double tap speed.
- **In-app updates**: a popup shows the new version and its changelog, downloads the
  APK and opens Android's update confirmation. The changelog is built from the commit
  messages since the previous tag.
- English and Spanish, with an in-app language switch (Android 13+).
- Material 3 settings screen with the wallpaper's dynamic colours.
- Themed icon (Android 13+).
- Optional hidden app icon: settings stay reachable from Settings → Apps → Nap Tap →
  Additional settings in the app, or from Accessibility → Nap Tap.

## First install
Android 10+ only lets an app hide its icon completely if the icon was disabled from the
start, so **Nap Tap installs without an icon**. Turn it on in Settings → Accessibility →
Nap Tap (you have to do that anyway) and the icon appears in the app drawer.

## How it works
- A 1×1 px accessibility overlay with `FLAG_WATCH_OUTSIDE_TOUCH` receives an
  `ACTION_OUTSIDE` event for every touch on the screen without blocking it, so the
  launcher keeps working normally. Android doesn't expose coordinates, only the time of
  each touch.
- Two touches within N ms (configurable, 300 ms by default) trigger the following checks:
  - if the device is locked and the PIN/password pad is not showing → screen off;
  - if the device is unlocked, the active window is the default launcher and neither
    the app drawer nor the recent apps screen is open → screen off.
- If the first tap opens an icon, a notification, a widget, etc. (a *click* event) or
  scrolls something (a *scroll* event), the double tap is cancelled.
- With pocket protection on, the proximity sensor is read once before turning the
  screen off.
- The screen is turned off with `GLOBAL_ACTION_LOCK_SCREEN`, just like the power button,
  so fingerprint and face unlock keep working.

## Known limitations
- Since there are no coordinates, a double tap on a part of a widget that doesn't react
  to touches (no *click* or *scroll*) will still turn the screen off.
- On the lock screen, PIN detection relies on common view IDs (AOSP, Pixel, Samsung,
  Xiaomi…). If typing your PIN quickly turns the screen off, add the right ID to
  `BOUNCER_ID_HINTS` in `DoubleTapService.kt`.
- App drawer and recent apps detection uses Launcher3 / Pixel Launcher view IDs
  (`LAUNCHER_OVERLAY_IDS`). Other launchers may need their own IDs.
- The update check needs this repository to be public.
