# WAYiRUN — Sign-in and settings phone check

## 1 Install

Install `android/app/build/outputs/apk/debug/WAYiRUN-signin-settings1-2026-09-15_13-38-49_EDT.apk` over the existing app. Do not uninstall; your local runs/settings should remain. The build label should read `0.1.0-dev-signin-settings1`.

## 2 Launch and settings

1. Open WAYiRUN. The original Android splash leads to the app. Only missing Android permissions should prompt; there is no setup page or Continue button.
2. Tap the top-right gear. Change a goal/target, countdown, playlist, or appearance. Tap the same gear to close settings. There is no Save button.
3. Close and reopen the app. Open the gear and confirm your values returned.
4. Start a short run. Switch away and return, then lock/unlock the phone. Your run should be visible immediately, without a setup/settings overlay. Opening and closing the gear during a run must not pause it; changed run settings apply to the next run.

## 3 Google sign-in

Open the gear and tap **Sign in with Google**. Choose your Google test account. On success, your display name/profile picture should appear. Cancelling or losing connectivity must still allow local running. Sign out under the gear; existing runs must remain.

Google sign-in is included but not yet verified with your actual phone/account. Report the on-screen message if it fails. Do not send passwords, Google tokens, or session tokens.

## 4 Current limit

Runs are still stored only on this phone. Signing in does not upload runs, change their owner, or enable photos/AI. Run synchronization is the next development slice.
