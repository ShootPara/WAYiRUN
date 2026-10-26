# 1 WAYiRUN current state

Version: 1.0
Status: Current source and environment snapshot
Snapshot date: October 21, 2026

## 1.1 Repository

`main` is the canonical branch. The historical signed-artifact checkpoint is recorded in the productionization history; the durable public source marker is the annotated `v1.0.0` tag. APKs, AABs, signing keys and private migration evidence are not tracked.

Historical milestone plans, test handoffs and phone reports are stored under `docs/history/`. They do not override the current requirements or source.

## 1.2 Android product

The repository contains one application module, `android/app`. Functional application code, manifest components and resources live in the shared `main` source set.

| Concern | Development | Production |
| --- | --- | --- |
| Application ID | `com.example.runningapp.debug` | `com.unopenedparachute.wayirun` |
| Version | `1.0.0-dev` | `1.0.0` (`versionCode 1`) |
| Label | `WAYiRUN Dev` | `WAYiRUN` |
| API origin | Development Worker | `https://wayirun.slopcopy.com` |
| OAuth | Dedicated development clients | Dedicated production clients |
| Signing | Debug signing | Owner-controlled configuration outside Git |

The Kotlin namespace remains `com.example.runningapp`. Release builds are non-debuggable and the production-readiness task rejects development identity, endpoints and OAuth configuration in release output.

Delivered Android behavior includes run setup, outdoor GPS, indoor steps, time-only fallback, foreground tracking, pause/resume/finish/discard, recovery, splits, goals, announcements, detector/GPS auto-pause, Google account workflows, synchronization, coaching, achievements, photos, weather overlays, private-by-default publication and Health Connect export.

## 1.3 Worker and browser

The Worker implements Google account sessions, owner-isolated run storage, deletion tombstones, encrypted per-account OpenAI-key storage, durable coaching jobs/results/audio, photos, location/weather context, publication, CSV export and private browser history.

| Concern | Development | Production |
| --- | --- | --- |
| Worker | `wayirun-dev` | `wayirun-prod` |
| D1 | `wayirun-dev-db` | `wayirun-prod-db` |
| Public origin | workers.dev development origin | `https://wayirun.slopcopy.com` |
| Write mode | `frozen` | `normal` |

The configurations use distinct OAuth clients, rate-limit namespaces and D1 databases. Production deploy scripts require exact-target guards. Production D1 migrations are applied through `0012`; development remains preserved and write-frozen. Applied migrations remain immutable.

## 1.4 Persistence

- Room database: `wayirun-local.db`, schema version 11.
- Checked-in Room schemas: versions 1–11.
- Run archive format: version 1.
- D1 migrations: `0001_bootstrap.sql` through `0012_early_weather.sql`, applied through 0012 in production.
- Completed archives retain detailed run records and source gaps.
- Account ownership, deletion tombstones, coaching, photos, publication and Health Connect state remain linked to stable run IDs.

`DATA_MODEL.md` describes the current persistence contract.

## 1.5 Production data boundary

Production was created separately from development rather than converting the development resources in place. The reconciled migration recorded one owner and 21 retained runs with their included archive, coaching, media, deletion and publication records. Development remains preserved and write-frozen. Private migration packages and backups remain outside Git.

The checked-in source contains resource identifiers and public OAuth client IDs where required by Android and Worker configuration. It contains no signing passwords, private keys, OpenAI API keys, Cloudflare credentials or private data exports.

## 1.6 Verification state

Automated Android, Worker, browser, migration, deployment-guard and artifact-readiness checks are recorded in the archived handoffs and productionization record. The production Worker/browser and signed production artifact were verified during the recorded productionization work.

Automated, emulator, deployed-service and physical-device evidence are separate categories. Remaining physical observations and post-1.0 work are listed in `OPEN_WORK.md`; they are not restated as missing implementation here.

## 1.7 Distribution

The current distribution model is an owner-signed direct APK. Google Play publication, Play App Signing and store-listing work are deferred. Generated release artifacts and all signing material remain outside Git.

## 1.8 October 21 reliability follow-up

The working source preserves the exact finish/photo run across repeated activity returns and saved-state recreation. New Run offers View previous run for the most recent completed run in the selected account or local scope; ordinary reopening still starts on New Run. Encrypted session reads allow AtomicFile backup recovery and retain the saved file after transient storage or keystore failures. These entry/session changes require no migration; the separate weather changes below introduce new migrations.

JVM tests (127), debug assembly, instrumentation assembly and lint (zero errors, 21 warnings) passed. All 18 targeted emulator tests passed across the session/storage run and final lifecycle rerun, including repeated photo-editor returns, explicit previous-run reopening and owner isolation. The initial emulator System UI hang and a photo-close timing regression were resolved before the final lifecycle pass. Physical confirmation of the reported camera/login interruption and access to the affected phone's local database remain outstanding; see `OPEN_WORK.md` section 1.1.1. The Android changes have not been installed on the owner's phone; the separate server rollout is recorded below.

## 1.9 Run-start weather

Weather capture starts independently of tracking after the first committed running checkpoint. Both indoor and outdoor runs can retain a recent permission-derived location, rounded to 0.1 degrees. Indoor capture uses a cached or single network-provider fix and does not register GPS route tracking. Missing permission/location leaves weather unavailable; tracking continues.

Room migration 10→11 adds a cascading run-weather context table. Network-constrained background work requests the authenticated early-weather API and persists successful snapshots for offline photo use, with bounded retries while connectivity allows. Synchronization promotes the same server snapshot to completed-run weather. Existing outdoor archives retain their first-route-point fallback. The photo weather checkbox controls rendering, and the icon has a semitransparent white circle.

Worker migration 0012 adds an account-scoped early-weather cache and deletion cleanup. It was applied to production on October 21, 2026, and Worker version `0c8c3e4d-cf9e-43ef-b7a8-350b559efb0d` was deployed through the exact-target guarded script. A private pre-migration D1 export is retained under ignored `private-signing/production/weather-2026-10-21/`. Live health/readiness and early-endpoint authentication/method/origin checks passed. Before/after counts remained one account, 30 completed runs, 176 archive chunks, 21 photos, three completed weather entries and 15 deletion tombstones. Development was not changed.

Verification passed: 176 Worker tests and production-target dry-run, 127 Android JVM tests, 34 focused emulator tests across weather/render/migration/start-capture and editor suites, debug/release assembly, release production-readiness checks and APK signature verification. Lint reports zero errors and 21 warnings. The owner-signed candidate is generated under ignored `android/app/build/outputs/apk/release/WAYiRUN-2026-10-21-weather.apk`; it includes the earlier photo/session changes. Physical-phone installation, actual home-Wi-Fi lookup timing and network-location availability remain unverified. Deployment evidence is under `docs/history/releases/WEATHER_ROLLOUT_2026-10-21.md`.

## 1.10 Photo route stroke correction

The supplied photo exposed a rendering defect on dense/retraced routes: drawing each segment's black outline immediately before its green center allowed later outlines to cover earlier green strokes. The renderer now completes the entire outline pass before drawing any green route strokes. Gap handling, scale and placement are unchanged. Existing rendered JPEGs are not rewritten; correcting one requires rendering again from its source photo.

Verification passed: 127 JVM tests, debug build/lint, all six emulator photo-rendering tests (including a 2,001-point out-and-back regression), signed release build, production-readiness checks and signature verification. Latest signed candidate: ignored `android/app/build/outputs/apk/release/WAYiRUN-2026-10-21-route-fix.apk`, including all preceding fixes. No additional server deployment or database change is needed. Physical-phone installation and rerendering the supplied photo remain unverified.
