# 1 WAYiRUN open work

Status: Current unresolved and deferred work

## 1.1 Known physical-device verification

The following behavior is implemented and has automated or emulator evidence but may still benefit from representative physical-device observation:

- Detector/GPS auto-pause behavior while carried, stationary and screen-off.
- Quantitative outdoor distance, pace and route accuracy against an independent reference.
- Indoor step delivery and stride-derived distance on representative hardware.
- GPS loss, reacquisition and step fallback without jumps or double counting.
- Long-running foreground-service, notification, recovery and battery behavior.
- Speaker/headphone cue ducking and restoration with YouTube Music.
- Camera, image picker, rotation, save and platform share targets.
- Health Connect consent, displayed records, retry and deletion cleanup.

These are evidence limitations, not permission to redesign implemented behavior. Any reproduced defect should be recorded separately with the exact build, device and conditions.

### 1.1.1 October 21 camera-return incident

The owner reported returning to New Run and needing to sign in again immediately after confirming an end-of-run camera capture. Read-only production inspection found the October 21 run completed with all 17 archive chunks (start 19:45:41 UTC, 3,945,697 ms active, 9,170.433 m), without an uploaded photo. Recent server sessions were neither expired nor revoked. No cloud record was modified. The physical phone is unavailable over USB, so its local database and historical process/crash evidence remain uninspected; the precise incident cause is unconfirmed.

The working source now retains the photo workflow's exact run across repeated activity returns, preserves encrypted session files on transient read failures, permits AtomicFile backup recovery, and offers an owner-scoped View previous run action from New Run. Physical camera confirmation, process recreation, account persistence and reopening the affected local run still need verification on the owner's phone. These changes have not been installed on that phone.

### 1.1.2 Run-start weather physical verification

The source and signed candidate support early weather lookup for indoor/outdoor runs and a translucent white icon background. Automated evidence includes 176 Worker tests, 127 JVM tests and 34 focused emulator tests. Production migration 0012 and Worker deployment completed October 21; live health/readiness and early-endpoint access checks passed, with retained record counts unchanged. The candidate APK has not been installed on the owner's phone. Remaining observations are lookup timing while still on home Wi-Fi, indoor network-location availability, loss/recovery of connectivity, cached weather at finish, and camera-return behavior on that phone. Missing permission or a recent usable location must remain an unavailable-weather case rather than inventing context. The new authenticated provider-fetch path has local synthetic coverage, not a new live physical-phone canary.

### 1.1.3 Dense photo route visibility

The owner-provided image showed a nearly black route with a small remaining green stroke. The segment-by-segment outline order was corrected to whole-route outline then whole-route green passes, with six emulator render tests passing including a dense/retraced regression. The updated signed candidate is `WAYiRUN-2026-10-21-route-fix.apk` under ignored build output. Verify the candidate build and rerender from the source photo on the owner's phone; the existing JPEG contains baked-in pixels and is not changed by installation. No cloud data repair is required for this rendering defect.

## 1.2 Deferred product work

- Desktop week/month/year/lifetime statistics selectors, sorting and charts.
- Coaching voice selection; Cedar remains fixed.
- Broader performance-achievement calculations across discontinuous measurement evidence.
- Automatic restoration of cloud photos onto a new phone.
- Google Play publication and its signing, listing and policy workflow.

## 1.3 Explicit exclusions

The following are not open work for Version 1.0:

- Basemaps or map-tile providers.
- Automatic external-player transport control or notification-listener access.
- Social feeds, clubs, advertisements or user-to-user interactions.
- Calorie tracking or general health aggregation.
- Health Connect imports.
- A standalone watch application.

## 1.4 Repository and release administration

Repository cleanup, licensing, tagging, publishing and visibility are owner-directed administrative actions rather than product defects. Their absence must not be represented as missing runtime behavior.
