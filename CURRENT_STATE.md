# 1 WAYiRUN current state

Status: Accepted feature-development baseline; V1 productionization Milestone 1 complete
Repository authority: current implementation plus accepted decisions in `REQUIREMENTS.md`
Canonical lineage: `main`; accepted Settings checkpoint `1e00e8e`; history-preserving integration merge `897cac5`

## 1.1 Baseline boundary

WAYiRUN is substantially functional as a development system. The complete Android product is compiled only in the `debug` variant; the `release` variant intentionally remains a name-only shell. This baseline describes delivered feature behavior without claiming production readiness or comprehensive physical-device acceptance.

The Cloudflare Worker is configured only for the development environment. Development deployment history is evidence but deployment parity is not part of this feature-baseline gate.

## 1.2 Branch reconciliation

The canonical `main` lineage and the retired `development` lineage share commit `5b5e7ed`. The seven commits unique to the old `development` line cover restore/deletion reconciliation, desktop history, Google styling, Leaflet route maps, CSV export, session/transfer limits, and desktop deletion. Canonical `main` contains the still-valid restore, history, authentication, export, rate/limit, and deletion behavior plus later tests and features.

The only material behavior unique to that older line is its Leaflet/OpenStreetMap basemap implementation. That behavior is explicitly superseded by provider-independent route noodles. No still-valid functionality was identified only on the retired `development` line, so it was not merged wholesale.

## 1.3 Delivered Android behavior

| Area | Current implementation | Verification boundary |
| --- | --- | --- |
| Identity | Google sign-in, encrypted local session, display name/photo, sign-out, explicit local-run import | Account isolation is automated; multi-account phone acceptance remains later |
| Setup | Indoor/outdoor, none/time/distance goal, target, units, stride, countdown, announcements, auto-pause, playlist opening, connectivity/run indicators | Compose and instrumentation coverage exists |
| Tracking | GPS, steps, stride distance, outdoor step fallback, time-only mode, source segments and route gaps | Quantitative sensor accuracy requires later device acceptance |
| Run control | Countdown, pause, resume, detector-armed automatic pause/resume, concurrent outdoor GPS/step stop windows, swipe finish, discard confirmation, new run | Detector/GPS remediation requires the focused phone acceptance in TEST_PLAN.md 1.9; no raw accelerometer classification |
| Recovery | Room checkpoints, interrupted pause, explicit resume, completed-run reopen rules | Process/reboot and external-activity acceptance remains later |
| Metrics | Active time, distance, average pace, full splits and final partial split | Pure calculation coverage exists |
| Audio | State, goal, milestone and completion cues with serialized playback and transient audio focus | Real speaker/headphone ducking remains later acceptance |
| Music | Saved YouTube Music playlist opening only | No transport commands or notification-listener access |
| Finish | Optional coaching, achievement celebration, summary, photo, sharing and discard/new-run controls | Combined physical flow remains later acceptance |
| Coaching | Per-account OpenAI key, verified current/previous run context, anomaly classification, saved cloud recap/audio and onboard fallback | Paid-call tone and device playback remain later acceptance |
| Achievements | Historical deterministic awards, finish celebration and deletion recomputation | Broader paused/source-changing performance curves are deliberately deferred |
| Photos | Camera/picker/skip, bounded decode, time/distance/pace/route/weather overlays, preview/retake/keep/save/share; optional run-start weather has bounded meaningful retry | Real camera, share-target and physical-phone weather acceptance remain later |
| Publication | Private default, durable publication intent, short links, photo visibility and unshare | Automated state-machine coverage exists |
| Health Connect | Explicit write-only connection, exercise/distance export, stable IDs, retries and deletion cleanup | User-phone consent/display acceptance remains later |

## 1.4 Delivered Worker and browser behavior

- Google native and browser authentication with owner isolation.
- Resumable immutable completed-run archives with integrity-checked chunks.
- Pull/restore and deletion-marker reconciliation.
- Private paginated desktop history, loaded-scope totals, detail, splits and settings.
- Provider-independent route-noodle rendering with gaps and start/finish markers.
- Coarse city/region display derived separately from route graphics; OpenStreetMap attribution applies to geocoding data, not a basemap.
- Complete CSV export including retained detail and reconstructable media records.
- Account-scoped encrypted OpenAI keys, durable coaching jobs and saved audio.
- Deterministic run-quality context for likely tests, vehicles, or GPS anomalies.
- Achievements calculated from retained owner history.
- Photo storage, weather cache, public-run pages and private-by-default publication controls.
- Authenticated run deletion with dependent-data cleanup.

## 1.5 Persistence

- Room database version 9 with checked-in schemas 1 through 9 and explicit migrations.
- Run archive format version 1 with validated source, timing, route, measurement, split, interval, and segment records.
- D1 migrations 0001 through 0011 for accounts, sessions, archives, deletion markers, keys, coaching, photos, locations, publications, and weather.
- SharedPreferences for ordinary local settings and small workflow/connection state.
- Android Keystore plus an encrypted no-backup atomic file for the Google session.

The detailed classification of source, derived, duplicated, operational, and legacy data is in `DATA_MODEL.md`.

## 1.6 Verification state

The repository contains JVM, debug-unit, Android instrumentation, Worker, browser, and guarded development-rollout tests. Historical generated reports were removed during baseline cleanup and are not treated as fresh evidence. `TEST_PLAN.md` defines the clean verification required for this baseline.

The clean local baseline gate passed on September 29, 2026:

- Android: 118 unit/debug-unit tests passed with zero failures or skips; debug assembly and lint completed successfully.
- Android lint: zero errors and 21 warnings (nine dependency updates, five KTX suggestions, three SharedPreferences apply suggestions, two newer-version notices, one Android Gradle Plugin version notice, and one obsolete-SDK check).
- APK: `WAYiRUN-2026-09-29_08-41-13_EDT.apk`, 40,290,032 bytes, SHA-256 `dbe02e896306c4c7671577c281b0fcd8131df48a90b33b7a980d59ae1e015ffa`.
- Worker: strict TypeScript compilation, Wrangler deployment dry run, and all 148 local tests passed.
- Documentation: all authoritative files exist, local Markdown links resolve, and `git diff --check` reports no content errors.

The clean npm install reports three moderate development-tool advisories through `undici` in `miniflare`/`wrangler`. No high or critical advisory is reported. The available automated fix proposes downgrading pinned tooling and is not applied as baseline cleanup; review it in a separately verified dependency-maintenance milestone.

Automated verification does not prove physical GPS/step accuracy, carried-phone auto-pause, screen-off longevity, real audio focus behavior, camera/share targets, user-phone Health Connect behavior, or production readiness.

## 1.7 Deliberate exclusions and supersessions

The following are not missing baseline features:

- Leaflet, map tiles, or any basemap;
- automatic music playback or media transport;
- notification-listener access;
- automatic publication when keeping a photo;
- social feeds, clubs, stores, advertisements, or user interactions;
- calorie tracking or general health aggregation;
- Health Connect imports;
- a watch application;
- automatic photo restoration to a new phone;
- deferred statistics periods/charts or coaching voice selection.

## 1.8 Baseline limitations

- The release Android variant is not the product implementation.
- The application ID, OAuth clients, Worker URL, secrets/configuration, signing, policy declarations, and deployment process remain development/release concerns.
- Comprehensive physical-device acceptance and development-deployment parity are intentionally outside this reconciliation sequence.

## 1.9 Detector/GPS auto-pause remediation — October 5, 2026

The approved replacement removes raw accelerometer classification and counter-driven motion decisions. A separately registered step detector must arm from two timely steps before five seconds of silence can pause indoors. Outdoor detector silence and uncertainty-qualified GPS stop evidence run concurrently. Two post-pause steps within two seconds or qualifying outdoor GPS movement resume only an automatic pause. REQUIREMENTS.md 1.8 defines exact thresholds and fallback behavior.

The change retains Room v9, archive/settings compatibility, controller accounting and interrupted recovery. MotionWindow.kt and its classifier-only test were removed after reference tracing; Git history retains both. Detector evidence is ephemeral. Existing late counter batches received after segment closure remain excluded: the regression fixture demonstrates ten pre-pause steps arriving too late are not retrospectively counted.

Android unit/build/lint and test-APK assembly passed: 125 unit tests, no failures/errors/skips, zero lint errors and 21 existing warnings. All 40 focused instrumentation tests passed on explicitly selected emulator-5554, including both auto-pause service tests, screen-off synthetic pause/resume, missing-detector GPS fallback, stale-generation manual protection, recovery, storage, announcements, music independence and photo/weather/noodle regressions. No physical phone was installed or tested; OPEN_WORK.md's bug remains open for TEST_PLAN.md 1.9.2 acceptance.

Handoff APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-10-05_19-46-23_EDT.apk`, 40,290,032 bytes. SHA-256: `1779663DAF0CC88A365E231BA142A99EC9984899351C452A1E96CD27BBB64DFC`. Android signature verification passed. It includes the weather reliability repair and prior noodle placement correction. Upgrade in place; preserve app data. Generated APK/tests/reports remain ignored. No schema, release-variant or production-deployment change was made by this remediation.

The detector/GPS remediation, weather reliability repair and route-noodle placement correction are committed in the current upstream-synchronized lineage: detector/weather commit `cadecf2` follows noodle-placement commit `7c88edb`. The development weather Worker deployment recorded in `worker/WEATHER_CONTRACT.md` remains distinct from production deployment.

## 1.10 Settings overhaul and V1 checkpoint preparation — October 6, 2026

The Settings overhaul is committed in accepted checkpoint `1e00e8e` and reachable from canonical `main`. It reorganizes the existing controls into six initially collapsed, independently expandable, emoji-anchored categories without changing preference keys, defaults, captured run behavior, integrations, schemas or backend behavior. The implementation is in `SettingsScreen.kt` and the corresponding `WayirunApp.kt` integration; focused Compose tests and `docs/SETTINGS_INFORMATION_ARCHITECTURE.md` record the interaction and persistence contract.

Fresh local verification passed after clearing a Windows `ReadOnly` attribute from ignored generated Android build output: 125 unit/debug-unit tests passed with zero failures, errors or skips; debug assembly succeeded; lint completed with zero errors and 21 warnings. APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-10-06_09-39-19_EDT.apk`, 40,355,568 bytes, SHA-256 `773482503562053C368A137161C93BDAACFEC638A6A988A485421C449A231F83`. Pass 1A Worker verification also passed strict TypeScript compilation, Wrangler deployment dry-run and all 156 local tests with zero failures or skips; it contacted no remote resource.

Physical auto-pause and the other device acceptance in `OPEN_WORK.md` remain open. V1 productionization is governed by `WAYIRUN_V1_PRODUCTIONIZATION_PLAN.md`; no production resource, release configuration, device installation, deployment, commit or push occurred during checkpoint preparation.

## 1.11 V1 Milestone 2 decisions — October 6, 2026

`V1_MILESTONE_2_DECISION_RECORD.md` fixes the permanent application ID, production domain, Play distribution and public support identity, and establishes the privacy, retention, telemetry, signing, provider-separation, encrypted-key-continuity and live-data migration policies. Existing phone and development records are irreplaceable source data until a rehearsed migration, immutable backup, complete reconciliation and production acceptance have all passed. No production resource, identity, key, deployment, database operation, build or device action occurred in Milestone 2. Milestone 2 completed before Milestone 3 began.

## 1.12 V1 Milestone 3 environment map — October 6, 2026

`V1_ENVIRONMENT_RESOURCE_MAP.md` fixes exact development/production names, configuration paths, compile-time Android boundaries, Custom Domain shape, deployment guards, OAuth/D1 placeholders, migration classification, phone-local reconciliation, backup retention, provider posture and rollback rules. Read-only Cloudflare verification confirmed `slopcopy.com` is active and unpaused in account `6bf560a8b86852196c9898023e3b8d6b`; the absent production-host DNS record is expected before later Custom Domain creation.

The installed debug identity remains `com.example.runningapp.debug` so its current Android sandbox and Room data remain accessible. Production will use `com.unopenedparachute.wayirun`, which cannot inherit that sandbox; local-only and queued records must be explicitly reconciled before debug retirement. No Worker, D1 database, namespace, DNS record, Custom Domain, OAuth client, key, secret, deployment, migration, Android configuration, build or device state changed in Milestone 3. Milestone 4 has not started.

## 1.13 Pre-Milestone 4 coaching durability repair — October 6, 2026

Selected cloud coaching now has a durable Room-backed request before the first network call. Schema 10 adds only `coaching_requests`; migration 9→10 preserves existing user runs, checkpoints, routes, measurements, sync state, photos, publications, achievements and Health Connect state. The request is owner-scoped, cascades only with deletion of its parent run, and retains one operation ID across process death, offline/timeout/408/429/5xx retry and WorkManager recovery. A validated Worker receipt acknowledges the request; fallback audio does not. Authentication and manual synchronization retry can resume AUTH/BLOCKED requests.

Debug assembly, lint and all 125 JVM tests pass. Thirteen bounded instrumentation tests passed on the explicitly selected `Pixel_7` emulator with zero failures or skips, including complete version-9 fixture preservation, schema validation, stable operation reuse across 503/429/timeout/offline failures, repository recreation, fallback independence, acknowledgement convergence, cascade deletion and account isolation. No physical phone, personal run data, Worker, production resource, live database, paid OpenAI request, deployment, commit or push was used. Milestone 4 remains not started.
