# WAYiRUN sync1 phone checklist

## 1 Install and preserve data

Install `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-16_07-57-16_EDT.apk` over the existing app. Do not uninstall or clear data. Confirm the gear settings show version `0.1.0-dev-sync1`. Existing runs and saved settings should remain. Splash and startup permissions should behave as before, without a settings overlay on return to an active run.

## 2 New run upload

Sign in if the session has expired, finish a short test run, then open the gear. With internet available, pending should become synced. Recording must work with music off. Synced means a server completion receipt was verified; Online alone does not mean backed up.

## 3 Existing-run import

If local runs exist, select Add existing runs to this account. Cancel first: nothing should change. Repeat and confirm the displayed account: those runs should queue and synchronize. Repeating must not create duplicates. No import/account switching should be available during an active run.

## 4 Offline and retry

After signing in, disable connectivity, record and finish a short run. It should remain saved and pending. Reconnect and allow background work to run; it should synchronize. If authentication expires, sign in again. Recording must still work while authentication is expired. Retry sync is available in settings for blocked/authentication failures.

## 5 Discard

Use only disposable test runs. Discard a completed run with the existing confirmation slider. Its local summary should disappear immediately. Repeat offline: settings should report pending cloud removal, then clear it after reconnect/sign-in. A late upload must not bring the run back. Cloud restore/history is not present in this build, so restoration is not a phone acceptance check yet.

## 6 Report

Record pass/fail for each section, build version, whether connectivity/session expired, and the visible sync message. Do not send tokens or private route data. Automated checks passed; these physical-phone scenarios remain unverified until you try them.

## 7 Device result - September 16, 2026

The user reports these device checks pass and the app functions as expected. A read-only development D1 query confirms two completed cloud runs. This acceptance applies to sync1; subsequent sync2 restore behavior has separate automated verification.
