# Nap Tap

Double tap to sleep for Android.

A minimal app (Kotlin, no external libraries) that turns the screen off with a
double tap on the **home screen** and/or the **lock screen**, using an accessibility
service. Requires Android 9 or later.

## How it works
- A 1×1 px accessibility overlay with `FLAG_WATCH_OUTSIDE_TOUCH` receives an
  `ACTION_OUTSIDE` event for every touch on the screen without blocking it, so the
  launcher keeps working normally. Android doesn't expose coordinates, only the time of
  each touch.
- Two touches within N ms (configurable, 300 ms by default) trigger the following checks:
  - if the device is locked and the PIN/password pad is not showing → screen off;
  - if the device is unlocked, the active window is the default launcher and the app
    drawer is closed → screen off.
- If the first tap opens an icon, a notification, etc. (a *click* event), the double tap
  is cancelled.
- The screen is turned off with `GLOBAL_ACTION_LOCK_SCREEN`, just like the power button,
  so fingerprint and face unlock keep working.

## Known limitations
- Since there are no coordinates, a double tap on a home screen widget that doesn't fire
  a *click* event will also turn the screen off.
- On the lock screen, PIN detection relies on common view IDs (AOSP, Pixel, Samsung,
  Xiaomi…). If typing your PIN quickly turns the screen off, add the right ID to
  `BOUNCER_ID_HINTS` in `DoubleTapService.kt`.
- App drawer detection uses Launcher3 / Pixel Launcher view IDs (`DRAWER_IDS`). Other
  launchers may need their own IDs.
- The APK is signed with the *debug* key: fine for personal use, not for the Play Store.
