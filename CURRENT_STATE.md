# 1 WAYiRUN current state

Status: Accepted feature-development baseline; V1 productionization Milestones 1 through 9 complete; Milestone 10 not started
Repository authority: current implementation plus accepted decisions in `REQUIREMENTS.md`
Canonical lineage: `main`; accepted Settings checkpoint `1e00e8e`; history-preserving integration merge `897cac5`

## 1.1 Baseline boundary

WAYiRUN is substantially functional as a development system, and the complete Android product now compiles in both debug and release variants. The release variant has permanent package identity, owner-controlled signing and production OAuth configuration, but production Worker resources, migration, deployment and physical acceptance remain later milestones. This baseline does not claim comprehensive production or physical-device acceptance.

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

## 1.14 V1 Milestone 4 environment-safe Worker configuration — October 6, 2026

Worker runtime configuration accepts only the reviewed development and production environment/origin pairs and centralizes OAuth identifiers, provider endpoints and write mode. The early write-freeze gate blocks every classified native/browser mutation, including cache-, session- and job-mutating GET routes, before handler execution while retaining health/readiness and verified read-only access. Development deployment remains pinned to its existing Worker, D1 and rate-limit resources. The checked-in production config is pinned to `wayirun-prod` and the `wayirun.slopcopy.com` Custom Domain; Milestone 7 resolved its OAuth identifiers, while Milestone 8 D1 and rate-limit placeholders keep it intentionally undeployable.

All 165 Worker/browser tests and nine focused environment/guard/freeze tests pass. Development and synthetic-production Wrangler dry-runs pass; the real production config is proven to fail closed on placeholders. No production resource, deployment, remote migration, DNS change, Android change, data migration, commit or push occurred. Milestone 4 is complete and Milestone 5 is not started.

## 1.15 V1 Milestone 5 functional release promotion — October 6, 2026

The complete Android product now compiles from shared `main` code in both variants. The debug identity remains exactly `com.example.runningapp.debug`, preserving access compatibility with the existing Android sandbox and `wayirun-local.db`; it is labeled `WAYiRUN Dev` and uses only the development origin/OAuth identifiers. The non-debuggable unsigned release uses `com.unopenedparachute.wayirun`, label `WAYiRUN`, version code `1`, version `1.0.0`, and `https://wayirun.slopcopy.com`. The old name-only release activity is removed. Release OAuth remains deliberately represented by two Milestone 7 placeholders; sign-in fails closed locally and the explicit production-readiness task rejects the artifact until they are replaced.

Debug and release each passed 126 JVM tests with zero failures, errors or skips; both assemblies passed; both lint runs had zero errors and 21 existing warnings. Twenty-three focused Room/database/coaching instrumentation tests passed with synthetic data on explicitly selected `Pixel_7` emulator `emulator-5554`. Room remains version 10 with unchanged database name, migrations and schemas 1–10; no schema diff exists. APK inspection confirmed exact variant identities, versions, labels, authorities, components and environment separation. The release contains all functional product packages and the production origin, but none of the development hostname, development OAuth IDs, debug package/label or test harness. It is `STRUCTURALLY VALID — NOT PRODUCTION READY` pending Milestone 7 OAuth. No physical phone, personal data, signing material, provider resource, deployment, migration, commit or push was used. Milestone 5 is complete; Milestone 6 is not started.

## 1.16 V1 distribution-policy amendment — October 7, 2026

The owner changed V1 distribution from Google Play canonical publication to an owner-controlled signed APK. Google Play remains technically possible but is not a V1 acceptance gate. Play App Signing enrollment, Play Console application/listing work, AAB upload, Data Safety and Health-app declarations, testing tracks and store verification are deferred to a separately authorized future Google Play publication milestone. The permanent package remains `com.unopenedparachute.wayirun`; privacy, support, deletion, Health Connect, security, migration and physical-acceptance requirements remain in force independent of Play.

Milestone 6 therefore establishes one permanent owner-controlled release certificate for directly distributed APKs. The same key may later be evaluated as a Play upload key; a future Play app-signing certificate may differ and would require a separate OAuth fingerprint. No Play action is authorized now. The owner subsequently approved ignored repository-local custody with the repository folder's Google Drive mirror as the second copy; Section 1.17 records completion.

## 1.17 V1 Milestone 6 owner-controlled signing — October 7, 2026

The owner approved ignored repository-local custody because the repository folder is mirrored through the owner's Google Drive setup. `private-signing/` contains the password-protected permanent keystore, an AES-256-GCM/PBKDF2 encrypted backup and ignored local signing properties; GitHub receives none of them. Backup decryption was verified byte-for-byte during creation. Alias is `wayirun-release`; the certificate uses RSA-4096/SHA256withRSA. SHA-1 is `89:77:30:BD:C5:CA:5B:DA:0C:6E:B7:6F:EB:B7:77:A4:12:F3:9A:84`; SHA-256 is `C4:B6:54:9A:3B:A9:C1:7F:91:0F:49:65:DB:44:50:75:A3:F1:2B:52:A3:B3:A0:06:58:20:F6:27:15:AA:BA:5F`.

Gradle now keeps ordinary structural release assembly available but requires complete external signing configuration for `assembleSignedRelease`, with no debug-key fallback. The resulting APK is package `com.unopenedparachute.wayirun`, version code `1`, version `1.0.0`, non-debuggable, signed by exactly the intended owner certificate, and free of development host/OAuth/package identifiers. Debug and release each passed 126 JVM tests; assembly and lint passed for both variants with zero lint errors and 21 existing warnings. The explicit production-readiness gate fails only for the two expected Milestone 7 OAuth placeholders. No physical device, Play action, OAuth creation, deployment, migration, commit or push occurred. Milestone 6 is complete; Milestone 7 is not started. The APK is `SIGNED — STRUCTURALLY VALID — NOT PRODUCTION READY`.

## 1.18 V1 Milestone 7 production Google OAuth — October 7, 2026

The owner-controlled Google Cloud project `wayirun-development` retains its development Android and Web clients unchanged. Dedicated production clients now bind `com.unopenedparachute.wayirun` to the Milestone 6 direct-APK SHA-1 and authorize only the browser origin `https://wayirun.slopcopy.com`; the browser client has no redirect URI. Android and Worker production configuration contain only the resulting non-secret production client IDs. No OAuth secret is required or tracked.

The shared-project topology follows Google's cross-client identity model, and WAYiRUN continues to key accounts by Google's stable OIDC `sub`. Because the production Worker/origin does not yet exist, the exact same-owner Android/browser/development-account comparison is deferred as a no-write acceptance check to Milestones 11 and 13; any mismatch blocks migration and requires explicit linking. The consent configuration remains in Testing status and is sufficient for the current owner-controlled use case; public policy/support URLs and any later external publication remain later V1 gates.

Android debug and release each passed 126 JVM tests; debug assembly, owner-signed release assembly, both lint tasks and the production-readiness gate passed. The signed APK uses the production identity, origin and clients, contains no development OAuth identifiers and verifies with the owner certificate. All 165 Worker tests passed. The real production Worker config remains intentionally undeployable only because Milestone 8 D1 and rate-limit resource IDs are unresolved; the synthetic guarded production dry-run passed. No Worker deployment, data migration, personal-data access, physical-device install, Play action, commit or push occurred. Milestone 7 is complete; Milestone 8 is not started.

## 1.19 V1 Milestone 8 production Cloudflare foundation — October 7, 2026

After D1 capacity was freed, Cloudflare created the isolated production database `wayirun-prod-db` (`e4624be3-14f5-4cbc-939c-90009d377102`) in ENAM. Migrations `0001` through `0011` applied in order. Every user-data table is empty; only the expected migration/service metadata and singleton location/weather gate rows exist. Four distinct production rate-limit namespace IDs are fixed in the production configuration without development overlap. The real production guard and dry run pass.

The owner deliberately chose a fresh production `COACHING_KEYRING` and one-time OpenAI API-key re-entry after cutover. Existing development encrypted key envelopes are not part of production migration and remain preserved in the untouched development D1. The fresh keyring is provisioned only to production and backed up under ignored `private-signing/production/`; production `openai_keys` remains empty.

`wayirun-prod` version `2b46e490-7aa7-4bd4-b290-1c8ce26964f3` is deployed with `workers_dev=false`, the exact production D1/OAuth/rate-limit bindings and Custom Domain `https://wayirun.slopcopy.com`. The Custom Domain resolves through Cloudflare with valid TLS. Health, readiness, browser assets and OAuth configuration smoke checks pass. A live check exposed and corrected the remaining same-project OAuth prefix rejection so runtime validation now rejects only the exact development clients. Production remains empty of migrated user data, and development resources remain unchanged. Milestone 8 is `COMPLETE`; Milestone 9 is `NOT STARTED`.

## 1.20 V1 Milestone 9 migration rehearsal — October 7, 2026

The deterministic migration tools are `worker/scripts/migration-core.mjs`, `migration-cli.mjs`, `migration-export.mjs`, `migration-import.mjs` and `migration-reconcile.mjs`. Sensitive JSONL, replay SQL and manifests stay under ignored `private-signing/migration/`. The final package identifies and hashes its source and tool, preserves stable IDs/ownership/archive chunks/deletions/completed coaching and audio/photos/publication state, and deliberately excludes authentication state, encrypted OpenAI-key envelopes, provider gates and unresolved operational rows.

Development contained 1 owner, 21 completed runs, 103 chunks, 13 deletion tombstones, 12 completed coaching results with 72 audio chunks, 13 photos and 13 publication states. It had no incomplete upload or active coaching job. One result-free failed coaching attempt, one already-applied publication operation and one unresolved weather attempt were explicitly omitted as operational; no owner decision remains unresolved.

Two independently created `wayirun-m9-rehearsal` databases received migrations `0001`–`0011`, the same package and complete reconciliation. Counts, canonical hashes, chunk hashes, binary byte totals, stable IDs and relationships matched; excluded tables/classes stayed empty. A post-rehearsal export matched all original source counts and canonical hashes. The disposable D1 was deleted. Production remained empty, development and production deployments/configuration remained unchanged, and all 172 Worker tests passed. Milestone 9 is `COMPLETE`; Milestone 10 is `NOT STARTED`.
