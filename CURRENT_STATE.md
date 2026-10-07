# 1 WAYiRUN current state

Version: 1.0
Status: Current source and environment snapshot
Snapshot date: October 7, 2026

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

The configurations use distinct OAuth clients, rate-limit namespaces and D1 databases. Production deploy scripts require exact-target guards. Applied D1 migrations are `0001` through `0011` and remain immutable.

## 1.4 Persistence

- Room database: `wayirun-local.db`, schema version 10.
- Checked-in Room schemas: versions 1–10.
- Run archive format: version 1.
- D1 migrations: `0001_bootstrap.sql` through `0011_photo_weather.sql`.
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
