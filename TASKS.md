# WAYiRUN — Implementation Plan

Version: 0.5
Status: September 28 issue-plan milestones 1-14 locally complete; matching development backend deployed during photo-sync repair; physical-phone acceptance remains
FILE: <repository-root>\TASKS.md (NEW)

## 1 Current state

Current through September 22: the baseline includes tracking, audio, account sync/restore/deletion, desktop history/export, coaching/history, achievements, photos/public pages, and Health Connect. The user authorized `ISSUES_AND_QUIBBLES_IMPLEMENTATION_PLAN.md` version 0.2. Its milestone IDs are the current execution sequence; Sections 3-20 below retain historical milestone IDs and evidence. Historical map, linked-music, default-public-photo, and stop/next instructions are superseded where they conflict with current REQUIREMENTS.md and the accepted issue plan. The release variant remains a shell; device acceptance remains distinct from automated checks.

### 1.1 Current issue-plan milestones

| Issue-plan milestone | Scope | Status |
| --- | --- | --- |
| 0 | Baseline checkpoint | Complete: e772ea2 and 9a864c1 pushed |
| 1 | Align product documents | Complete: September 22, documentation checks only |
| 2 | Completed-run launch/focus correction | Complete: 78 JVM tests and focused emulator checks; device acceptance pending |
| 3.0 / 3.1 | Route-only graphics / city-state lookup | Complete: Worker and desktop/mobile verification passed |
| 7 | Remove automatic music controls | Complete: Android/JVM/lint gates and 22 focused emulator tests passed |
| 10 | Setup controls/status indicators | Complete: build/lint, 17 focused emulator tests and visual checks passed |
| 6 | Configurable announcements | Complete: build/lint, 78 JVM tests and focused emulator checks; physical audio acceptance pending |
| 8.0 / 8.1 | Auto-pause policy / service integration | Complete: 95 JVM tests, build/lint, 26 focused emulator tests, repeated service test and light/dark/200-percent visuals passed |
| 9 | Anomaly-aware coaching | Complete: Worker compile/dry-run and all 118 tests passed |
| 4.0 / 4.1 | Publication API/web / Android sharing | Complete: Worker/browser gate plus Room v7, 95 JVM tests, 49 focused emulator cases and light/dark/200-percent visuals passed |
| 5.0 / 5.1 | Weather contract / photo rendering | Complete locally; regression and visual qualification resolved in M11 |
| 11 | Final verification and handoff | Complete locally: consolidated gates passed and one timestamped APK retained |
| 12 | Correct indoor auto-pause evidence | Locally complete: 102 JVM tests, build/lint and 11 distinct focused emulator cases passed across batch/rerun; physical BUG-006 remains open |
| 13 | Independent time/distance milestones | Locally complete: 107 JVM, 28 Worker, 27 broad emulator and 2 final visual/settings cases passed; see MILESTONE_13_TEST_HANDOFF.md |
| 14 | Goal-audio priority | Locally complete: 118 JVM tests, build/lint, 26 targeted emulator cases and combined visual checks passed; final APK in MILESTONE_14_TEST_HANDOFF.md |

Milestone 12 follows AUTO_PAUSE_AND_MILESTONES_PLAN.md. Registered step silence is now unknown, with bounded acceleration coverage and positive step evidence driving auto-pause separately from distance. See MILESTONE_12_TEST_HANDOFF.md for thresholds, exact gates, the notification test-harness correction, internal APK/hash and outstanding carried-phone acceptance. the owner authorized continuing through tests without model switching. Next: Milestone 13; no intermediate phone install requested.

Milestone 13 adds independent time/distance channels behind the existing master switch, with one-time legacy preference migration and additive captured settings. Android, archive, Worker coaching/CSV and light/dark 200-percent UI gates passed; exact evidence and internal artifact are in MILESTONE_13_TEST_HANDOFF.md. Goal collision/coalescing intentionally remains Milestone 14. No intermediate phone install requested.

Milestone 14 adds active-time occurrence metadata and bounded one-second cue arbitration. Goal audio suppresses coincident milestones, simultaneous time/distance channels produce one recap, and future milestones continue. Full Android and targeted emulator gates passed after the requested model switch. The final combined M12-M14 phone artifact and acceptance list are in MILESTONE_14_TEST_HANDOFF.md; BUG-006 remains open for real-device movement.

Milestone 1 updated REQUIREMENTS.md, TASKS.md, REMAINING_WORK.md, FINAL_PASS_ISSUES.md, and the issue plan; PHOTOS_PLAN.md gained an explicit historical-contract notice. Verification: heading/link inspection, superseded-rule searches, and `git diff --check`. That milestone changed documentation only; Milestone 2 results follow.

### 1.2 Issue-plan milestone 2 completed - September 22

TrackingService now loads unfinished runs only on ordinary startup. A distinct Activity entry command clears completed in-memory summaries on ordinary reopen without deleting records. A retained entry state preserves the current summary during configuration recreation; camera/gallery/save/share launches carry the exact finished-run ID for their return, saved across process recreation. The service checks ownership before restoring that specific finished record and does not replay coaching. Routine permission/account refresh remains separate from app entry. New Run clears finished presentation state; discard and unfinished recovery retain their existing behavior.

Changed MainActivity.kt, TrackingService.kt, PhotoFlow.kt; added RunEntryState.kt, RunEntryStateTest.kt, and RunEntryTest.kt. No database migration or network contract change.

Verification: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain` passed. All 78 JVM tests passed; lint reported zero errors and 19 warnings. Fourteen emulator tests passed across RunEntryTest, TrackingReliabilityTest, StartupPermissionsTest, RunScreenTest, and CoachingFinishTest. After adding actual photo-picker return and foreign-account restoration assertions, both final RunEntryTest cases passed: 15 distinct instrumentation cases total. Emulator target was emulator-5554 only. Coverage includes warm/cold entry, summary recreation, preserved records, picker cancellation/return, exact-run restore without coaching replay, stale/foreign IDs, paused-service recovery, notification lifecycle, discard, finish/photo UI and startup permissions.

Artifact: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-22_09-46-00_EDT.apk` (intermediate verification build, existing health1 version label). Reports: `android/app/build/reports/tests/testDebugUnitTest/index.html` and `android/app/build/reports/lint-results-debug.html`. Physical camera/share-target behavior and full process death while an external camera is open remain device acceptance; those are not claimed from picker and service tests. No physical-phone install, deployment, commit, or push. Next bounded milestone: issue-plan 3.0, route-only graphics.

Read [REQUIREMENTS.md](REQUIREMENTS.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DATA_MODEL.md](DATA_MODEL.md), and [the then-current working guide](User%20Preferences%20LLM%20Guide.md) before work. Product requirements take precedence over proposed technical details.

### 1.3 Issue-plan milestone 3 completed - September 22

Private/public route displays now use local SVG geometry with start/finish markers and preserved pause/GPS gaps. Removed Leaflet assets and tile CSP permissions; no basemap requests remain. Public data returns normalized route geometry without exact coordinate fields. City/state lookup uses a configurable Worker-side Nominatim endpoint, owner/run-scoped cache, a database-wide ten-second request gate, three-second timeout, daily failure retry, and unobtrusive public-page attribution. Indoor/no-GPS and provider failures retain honest generic labels. See worker/MAP_CONTRACT.md for the checked provider terms and limitations.

Added migration 0008_run_locations.sql, public-route.ts, run-location.ts, provider/geometry tests, and a repeatable browser verification script. Updated public/private rendering, asset routes, fixtures, smoke checks and existing tests. Run archives, recorded metrics and owner CSV contents are unchanged. Remote migration and deployment were not performed.

Verification: `npm.cmd test` passed all 107 tests including TypeScript and deployment dry-run. A subsequent 24-test targeted pass verified the final public outdoor API privacy case and removed-asset responses; 108 distinct tests now exist. `node scripts/verify-route-ui.mjs <playwright-module-path> http://127.0.0.1:8798` passed desktop (1365px) and mobile (390px), private/public rendering, two disconnected route sections, no-GPS state, cleanup, city/state, attribution and zero external browser requests. Screenshots were visually inspected under worker/build/verification/route-*.png. No live lookup used personal coordinates. `git diff --check` passed.

The user retains the existing phone build. No new APK handoff, install, commit or push. Next: issue-plan Milestone 7, automatic music-control removal. An empty local worker/web/vendor directory may remain because automatic approval rejected its removal; all tracked vendor files are deleted.

### 1.4 Issue-plan milestone 7 completed - September 22

Removed automatic media transport, notification-listener registration, service media hooks and music-access setup. Playlist opening and cue ducking remain. Added replacement music-independence instrumentation and settings coverage; retired tests for the removed linkage. Audio-idle notification cleanup remains in place. No Worker, schema or network contract changed.

Verification after the requested model-switch pause: the production-source removal scan returned no matches and `git diff --check` passed (line-ending notices only). `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain` succeeded. All 68 JVM tests passed with zero failures/errors/skips; lint reported zero errors and 19 warnings. All 22 focused emulator tests passed on emulator-5554, covering music independence/listener absence plus tracking lifecycle, completed-run entry, settings, playlist opening, startup permissions, run screens and coaching finish behavior.

Artifacts: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-22_18-25-34_EDT.apk` and `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. These are verification artifacts, not a phone handoff. Real YouTube Music/headphone behavior and audible focus ducking remain physical-device acceptance; synthetic MediaSession tests cannot prove them. No physical-phone install, deployment, commit or push. Next bounded milestone: issue-plan 10, setup controls/status indicators.

### 1.5 Issue-plan milestone 10 completed - September 23

Added NewRunControls.kt with square emoji radio choices, font-scale-aware column wrapping and separate left/right status labels in the requested theme-adjusted colors. WayirunApp.kt uses these for setup and active/paused status; saved preferences, goal target memory, validation and network/session checks retain their existing behavior. SettingsExperienceTest uses stable selector tags; NewRunControlsTest covers selected settings, persistence/start capture, narrow/large-text layout, rendered status colors and screenshot generation.

Verification after the requested model-switch pause: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain` passed. All 68 JVM tests passed with zero failures/errors/skips; lint reported zero errors and 19 warnings. Seventeen focused emulator cases passed across NewRunControlsTest, SettingsExperienceTest, PlaylistSetupTest, RunScreenTest, StartupPermissionsTest and CoachingFinishTest. One combined rerun caught the existing asynchronous photo-preview test while its checkbox was temporarily disabled; RunScreenTest then passed all six cases in isolation on the final app.

Visual inspection found the first 200-percent-font version left too little space between adjacent labels despite passing bounds checks. The layout safety margin and clipping assertion were corrected. Regenerated normal/200-percent selector images, light/dark status images and full app screenshots show square controls, intact emoji/labels, distinct status colors and scroll access to target, playlist, statuses and START RUNNING. Emulator font scale was restored to 1.0. Screenshots: `android/app/build/verification/milestone-10-final/milestone-10/` and `android/app/build/verification/milestone-10-app/`. Artifact: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-23_06-06-06_EDT.apk` (internal verification only). Physical-phone visual acceptance remains deferred. No phone install, deployment, commit or push. Next: issue-plan Milestone 6, configurable announcements.

### 1.6 Issue-plan milestone 6 complete - September 25

Prepared exactly four interval choices and an on/off switch in gear settings, saved automatically and captured at run start. New setup defaults to enabled/five minutes; old checkpoint settings decode with intervals off. Added time/distance announcement events to the controller and reused existing metric speech and audio focus. Recovery derives consumed thresholds from persisted totals; the existing save-before-play path remains authoritative. No Room schema or Worker/API changes.

Controller tests cover crossings, units, countdown, pauses, goal continuation, source gaps and JSON recovery/compatibility; cue tests cover spoken values/focus; storage and UI tests cover recovery and settings persistence/start capture. `git diff --check` passed with line-ending warnings only. The Android build/JVM/lint gate passed from a fresh build tree: all 78 JVM tests passed with zero failures/errors/skips, both debug APKs assembled, and lint reported zero errors and 19 warnings.

The focused emulator run passed 20 of 21 cases initially; the existing `RunEntryTest.finishedRunSurvivesRecreationButNotWarmOrColdReopen` timed out waiting for tracking state after the longer combined run, then passed immediately in isolation. The Milestone 6 announcement settings, queue, storage recovery, tracking reliability and music-independence cases all passed. Actual gear screenshots in light, dark and 200-percent font settings show the enabled five-minute default, all four choices, selected state, readable labels and scrolling without overlap. Emulator theme and font scale were restored. Screenshots: `android/app/build/verification/milestone-6/`. Artifact: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-25_19-46-41_EDT.apk` (internal verification only). Real speaker/headphone ducking, screen-off delivery and sensor behavior remain phone acceptance. No phone install, APK handoff, deployment, commit or push. Next: issue-plan Milestone 8.0, auto-pause policy.

### 1.7 Issue-plan milestone 8.0 complete - September 26

Added a pure Kotlin motion policy with explicit freshness, accuracy and speed thresholds; healthy silent step observation; both-source agreement; five-second stationary/two-second sustained movement dwell; source-change and delayed-poll resets. Added persisted optional pause reason and controller automatic pause/resume entry points using existing accounting. Manual override blocks auto-resume; process recovery always requires explicit resume. Existing service/UI behavior is not activated until 8.1.

`git diff --check` passed with line-ending warnings only. The first Android gate encountered the known Windows lock in generated package output; after stopping Gradle and moving the verified generated build tree aside, a fresh run passed. All 88 JVM tests passed with zero failures/errors/skips, including ten new policy/accounting/JSON compatibility tests. Debug and Android-test APK assembly passed; lint reported zero errors and 19 warnings. Both APKs installed successfully on `emulator-5554`, and all 13 focused `RunDatabaseTest`/`TrackingReliabilityTest` cases passed. Artifact: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-26_10-26-28_EDT.apk` (internal verification only). No UI changed, so no visual check was required. No phone install, deployment, commit or push. Next: Milestone 8.1 service/settings/cue integration.

### 1.8 Issue-plan milestone 8.1 complete - September 26

Implemented sensor speed/accuracy observations, stride-independent step registration, TrackingInput policy integration, automatic-pause sensor/timer/wake-lock retention, permission-generation reconciliation and fresh resume baselines. Added the default-on saved gear toggle captured at run start, automatic cue wording, Auto-paused display and Keep paused manual override. Legacy captured settings default off; recovery still requires explicit resume. Tightened stale-step resume evidence.

`git diff --check` passed with line-ending warnings only. The first Android gate hit the known generated-output Windows lock; after isolating that exact build tree, the fresh gate passed. All 95 JVM tests passed with zero failures/errors/skips. Debug and Android-test APK assembly passed; lint reported zero errors and 20 warnings. Fresh APKs installed successfully on `emulator-5554`; all 26 focused service/settings/storage/reliability/music/UI/Health Connect tests passed. The real-service auto-pause case also passed twice more while producing visual evidence, exercising background timing, persisted reasons/settings, frozen accounting, automatic resume, second-stop reset, manual override, sensor shutdown and interrupted recovery.

Actual settings and Auto-paused screens passed light, dark and 200-percent-font inspection with controls reachable and no overlap. Screenshots: `android/app/build/verification/milestone-8-1/`. Artifact: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-26_22-29-19_EDT.apk` (internal verification only). Emulator font scale was restored to 1.0. Real GPS accuracy, step batching/onset timing, repeated physical stops, permission changes, audible cues and screen-off sensor behavior remain phone acceptance. No phone install, APK handoff, deployment, commit or push. Next: issue-plan Milestone 9, anomaly-aware coaching.

### 1.9 Issue-plan milestone 9 complete - September 27

Added deterministic current-run quality labels/reasons/metrics after archive verification while retaining all current/previous run records. The classifier uses strict test thresholds, 25-second sustained fast-motion windows, segment/source/gap boundaries, accuracy-aware GPS jump evidence and conservative label precedence. Coaching instructions acknowledge suspicious data without performance praise or blocking coaching. No Android/UI, database schema, provider model, speech or durable-job behavior changed.

`git diff --check` passed with line-ending warnings only. `npm.cmd test` passed TypeScript compilation, the local Cloudflare deployment dry-run and all 118 Worker tests with zero failures/skips/cancellations. Ten new cases cover exact test thresholds, normal/sparse evidence, measured and route vehicle evidence, GPS accuracy/jumps, continuity resets, unchanged archives, actual provider request bodies and a suspicious run completing the existing durable coaching flow. Existing owner isolation, deletion, credentials, paid-stage uncertainty, history/export, route and publication coverage remained green. All OpenAI calls were synthetic stubs; no real key, paid call, deployment, migration, Android build, phone install, commit or push occurred. Real coaching tone and thresholds remain acceptance with eventual user recordings. Next: issue-plan Milestone 4.0, publication API/web.

### 1.10 Issue-plan milestone 4.0 - September 27

Completed migration 0009, independent owner-scoped publication state, revision-checked idempotent mutations and stable short tokens. Existing public tokens are preserved; new photo uploads and legacy visibility headers cannot publish or override sharing. Public pages/data/images and short redirects check current publication; hiding photos retains run data and private images. Added web share/copy/unshare/photo-display controls, no-photo pages and short-link export compatibility. A browser check exposed and fixed an in-flight checkbox-state overwrite; its harness was tightened for explicit multi-page contexts and deterministic mutation completion.

`git diff --check` passed with line-ending warnings only. `npm.cmd test` passed TypeScript compilation, the local Cloudflare deployment dry-run and all 125 Worker tests with zero failures/skips/cancellations. Local Playwright checks passed at 1365px and 390px for share/copy/cancel, hidden and absent photos, revocation, route/locality retention, 409 refresh, failed mutation and delayed navigation; existing private/public route checks also passed. Screenshots are in `worker/build/verification/publication-*.png`. No remote migration, deployment, Android change/build, phone install, paid call, commit or push occurred. Android durable intent, queue migration and all Android sharing entry points remain Milestone 4.1; combined rollout stays deferred.

### 1.11 Issue-plan milestone 4.1 complete - September 27

Implemented Room v7 independent publication intent/cache, migration of obsolete photo visibility flags, owner-scoped durable mutation receipts, server-revision conflict handling, deletion/supersession guards and explicit-import transfer of local intent. Added summary/photo sharing controls and pending states; Share/Copy use confirmed short links, local image sharing first records publication intent, and Keep/Save no longer publish. Background sync never opens a chooser. Added 13 synthetic publication engine cases, four control/delivery/layout cases, a migration case and an updated private-Keep photo UI regression.

Room schema 7.json was generated and inspected against migration 6-to-7. All 95 JVM tests passed; debug/test APK assembly and lint passed with zero errors and 20 existing warnings. All 49 distinct focused emulator cases passed across the main run and isolated reruns. The previously documented long-suite RunEntry lifecycle timeout occurred once after 47 passing cases and passed with both class cases in isolation. Confirmed short-link clipboard/chooser payloads, independent photo controls, migration, uncertain retry, conflicts, account changes, deletion and offline ordering passed with synthetic data only. Light/dark 200-percent-font screenshots under `android/app/build/verification/milestone-4-1/` show readable controls and wrapped labels. Internal APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-27_22-10-04_EDT.apk`, SHA-256 `962C7D8B4EF6AABC6A28B246EDA33625B404B49024661B0FBFDDA8054B6A5D4E`. No deployment, physical-phone install, commit or push occurred. Milestone 4 completed before the now-complete Milestone 5.0.

### 1.12 Issue-plan milestone 5.0 complete - September 28

Added the private Worker weather endpoint, migration 0010 owner/run cache and provider gate, exact historical-hour parsing and bounded provider/lifecycle handling. Seventeen new synthetic tests cover privacy, stable caching, historical selection, provider failures and late-response guards. Open-Meteo terms/endpoints were checked; worker/WEATHER_CONTRACT.md records decisions and the attribution constraint for 5.1. Photo persistence/API/rendering remain unchanged in this phase.

Verification after the requested model switch passed: TypeScript compilation, Cloudflare deployment dry-run and all 142 Worker tests completed with zero failures, skips or cancellations. The built bundle contains the `/api/weather/{runId}` route, and `git diff --check` passed. No Android build, phone install, real weather query, deployment, remote migration, commit or push. Next: Milestone 5.1 photo rendering and stable weather metadata integration.

### 1.13 Issue-plan milestone 5.1 complete - September 28

Prepared weather-enabled photo editing/rendering, stable saved snapshot and upload metadata, lower-right route without its rectangle, small provider credit, Room v8 and Worker migration 0011. New optional weather requests reuse the 5.0 contract. Prepared JPEGs bind source/options/weather so Keep cannot mix a late reply or replacement image with old bytes. Legacy photos remain unchanged. Worker uploads validate the exact owned snapshot; public data omits coordinates and hidden-photo weather; CSV retains private snapshot metadata.

Verification passed after the model switch: Worker compile/dry-run and all 146 tests; 95 JVM tests; Android debug/test assembly; lint with zero errors and 20 warnings; generated Room schema 8; all 13 new emulator cases; dark 200-percent editor/settings checks; and mobile/desktop weather, route and publication browser checks. One existing RunEntry lifecycle/photo-editor case remains a Milestone 11 stabilization item after failing in the combined run and alone. Internal APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-28_07-45-03_EDT.apk`, SHA-256 `573EEA17F7BA1793A87358FF6A3D562BE0D18E684691E8ABCA217E612B8DBA47`. No deployment, remote migration, real provider request, physical-phone install, commit or push.

### 1.14 Issue-plan milestone 11 complete - September 28

Reviewed the run-entry/picker return and prior transient Compose wrong-thread exception. Tightened RunEntryTest to wait for the resolved picker window before sending Back and for the restored editor. Updated COMPREHENSIVE_TEST_PLAN.md for independent music controls, announcements, default-on auto-pause, private Keep/Save, share intent, hidden photos/unshare, weather and completed-run reopening.

Consolidated verification passed: Worker compile/dry-run and 146 tests; 95 JVM tests; Android debug/test assembly; lint with zero errors and 20 warnings; all 105 distinct emulator cases across an explicit Health Connect permission split; repeated picker lifecycle coverage; six focused editor restoration executions; dark 200-percent pending-weather/settings cases; light/dark large-text and three-shape image inspection; and all local Playwright weather/route/publication/history checks. The picker issue was test timing, and no production code changed. The Compose exception did not recur. Handoff APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-28_11-48-32_EDT.apk`, SHA-256 `573EEA17F7BA1793A87358FF6A3D562BE0D18E684691E8ABCA217E612B8DBA47`. One APK remains in the repo build tree. No deployment, remote migration, real provider call, physical-phone install, commit or push.

### 1.15 Photo synchronization repair - September 28

the owner authorized repairing end-of-run photos and the matching development rollout. Investigation reproduced the newer Android weather flag being rejected by the September 19 Worker, even with weather disabled. Backed up/restored the development database for verification, passed all 148 Worker tests and deployed prepared migrations 0008-0011 plus Worker ce8a67a6-ce47-4d49-9559-9314014d6efc. All 27 live smoke checks and the disposable authenticated photo/publication canary passed; synthetic records were removed. Cloud photo count grew from six in the backup to eight after deployment, with two current-format photos, supporting recovery of queued uploads.

Android hardening adds Room v9 retry/error metadata, fair bounded photo batches, strict receipt/owner/revision guards and visible pending/failure states. See PHOTO_SYNC_REPAIR.md for exact gates, final artifact and phone acceptance. Future cloud-dependent APK handoffs must verify the actual deployed backend/schema, not only matching local fixtures. No production release, physical-phone install, commit or push.

Verification: 118 JVM tests, build/lint (zero errors, 21 warnings), 59 distinct focused emulator cases, and inspected light/dark 200-percent status screenshots passed. Final APK is WAYiRUN-2026-09-28_18-07-05_EDT.apk; size/hash and scoped cleanup are in PHOTO_SYNC_REPAIR.md. Live cloud recovery is observed; the owner's individual photo display check remains acceptance.

## 2 Execution discipline

### 2.1 Milestone rules

Complete one milestone at a time. Review its scope and entry decisions, implement only that scope, run its meaningful checks, and report the result. Never claim device behavior was verified by a successful compilation. Keep each completed milestone independently committable; do not combine deployment with feature development.

### 2.2 Repository ownership

The user created the repository. Its existing `.git` directory and `.gitattributes` were relocated from the redundant `RunningApp` subfolder to this project root on 2026-09-12, preserving HEAD and origin. Do not create another repository or change the remote. The initial checkpoint was explicitly delegated and is complete. Leave subsequent work uncommitted unless requested; milestone verification does not require a commit or push. At task entry inspect Git status and preserve pending user changes.

### 2.3 Credentials and device work

Bootstrap and pure controller tests need no cloud credentials, OpenAI key, or phone. Later integration checks require an actual Android phone, the requested system permissions, and configured external services. Request those only when their milestone needs them. Use an emulator and test fixtures for work that can proceed independently.

## 3 Milestone 0 — Documentation baseline

- [x] Put requirements and the working guide in the selected local folder.
- [x] Replace custom requirement labels with decimal heading/subheading numbering.
- [x] Record the accepted follow-up run behavior and update acceptance checks.
- [x] Create architecture, logical data model, and bounded milestones.
- [x] Inspect local Android tooling without installing or changing it.

Verification: document references, heading sequence, accepted decisions, and absence of application/deployment changes. This checklist concerns document preparation, not application completion.

## 4 Milestone 1 — Local Android bootstrap

### 4.1 Outcome

A reproducibly buildable Android project with an empty app shell and a pure Kotlin location for run calculations. This milestone establishes the build, not a usable running tracker.

### 4.2 Scope

- Create `android/` with one app module, a Gradle wrapper, pinned compatible versions, and Compose enabled.
- Add root `AGENTS.md` with the agreed folder, authority order, scope boundaries, and verification commands; preserve the existing guide.
- Add ignore rules for local SDK paths, build products, signing files, and secrets.
- Use a clearly development-only application identity until release naming/signing is established. Do not register OAuth clients against a provisional identity.
- Add only a minimal launch screen. No fake Google sign-in, nonfunctional feature buttons, web scaffold, cloud code, or credentials.

### 4.3 Verification and exit

Run the debug build and Android lint with the pinned toolchain. Record actual commands, output status, and APK path. Verify local paths and credentials are excluded from tracked files. Do not introduce a test whose only assertion is that the empty screen exists. A phone is not required for this build milestone.

### 4.4 Completed verification — 2026-09-11

- [x] One Android app module with Compose, pinned dependencies, and Gradle 8.13 wrapper.
- [x] Generated wrapper JAR matches the official Gradle checksum; distribution checksum is pinned.
- [x] Root project instructions and ignore rules added. Local SDK configuration remains in ignored `android/local.properties`; no Git repository or commit was created.
- [x] `./gradlew.bat :app:assembleDebug :app:lintDebug --no-watch-fs --console=plain` succeeded.
- [x] Lint reports zero errors and three version-update advisories for the deliberately pinned Gradle, Compose BOM, and Activity versions. No lint baseline or warning suppression was added.
- [x] APK generated at `android/app/build/outputs/apk/debug/app-debug.apk`; lint report at `android/app/build/reports/lint-results-debug.html`.
- [x] APK identity is `com.example.runningapp.debug`, minimum SDK 28, target SDK 36. It requests no runtime permissions; AndroidX adds its internal signature-protected receiver permission.
- [x] Explicit backup/transfer exclusions and a provisional launch icon are present.
- [ ] Physical device launch and visual inspection: not performed; this milestone verifies the build only.

Local verification corrected the escaped SDK path and read-only attributes on generated Windows build directories. The current source compiles successfully. No controller unit tests were added because this shell contains no run logic.

## 5 Milestone 2 — Deterministic run controller

### 5.1 Outcome

A tested local controller implementing the accepted run behavior without sensor or cloud dependencies.

### 5.2 Entry decisions

Use reasonable numeric validation (finite positive goals, nonnegative measured deltas) as engineering constraints. Before exposing partial splits, settle their presentation. Before allowing stride-dependent tracking, settle stride entry/default behavior and the no-usable-sensor case. These choices were not covered by the five accepted recommendations and must not be presented as already approved.

### 5.3 Scope

- Countdown, start, pause, resume, and terminal finish states.
- Monotonic active time; average pace from active duration and distance.
- Full mile/kilometer splits and a single goal-reached event without automatically finishing.
- Measurement-source segments, step counter resets, and baseline resets around pauses.
- Stable events and duplicate-command suppression for future media integration.

### 5.4 Verification and exit

Test running versus paused intervals, wall-clock changes, zero distance, mile/kilometer conversion, crossing multiple split boundaries, reaching a goal once, late readings, source switches without double counting, step resets, and duplicate pause/resume/finish events. Test that a finished run cannot be restarted by a media event. Use known numeric examples and expected outcomes rather than tests mirroring private implementation details.

### 5.5 Implemented calculation contract

The controller and immutable value models live in `android/app/src/main/java/com/example/runningapp/domain/`. One controller represents one run and is called serially. Its injected clock supplies monotonic milliseconds for calculations and separate UTC milliseconds for start/end metadata. The caller advances time with `tick()` or commands; the controller does not schedule work. A late countdown tick starts the run at the countdown deadline and counts only the subsequent active time. `snapshot()` returns the last processed state without reading time or emitting events.

The caller explicitly selects GPS or steps while Running. Each source selection or resume creates a new segment token and requires a fresh cumulative baseline. GPS inputs are cumulative meter readings from a future adapter, not raw coordinates. Step inputs are cumulative integer counters and use only an explicitly supplied stride. Lower counters rebaseline without subtracting distance. Repeating the same source selection keeps the existing baseline. Indoor mode rejects GPS source selection. Missing stride does not prevent starting, but step source selection requires one; this does not resolve the later missing-sensor or settings UI decisions.

Measurements must match the current source/segment, fall within that segment and at or before the current clock, and arrive in increasing measurement-time order. Delayed measurements can follow a tick while their segment remains open. Wrong-source, duplicate, future, out-of-order, and closed-segment readings are ignored. Once paused or finished, late readings do not revise totals. Source changes intentionally do not bridge an unmeasured interval. Platform sample flushing, GPS quality rules, and actual handoff timing still require Milestone 3 work.

Full split crossing times use linear interpolation between consecutive accepted measurements in the same segment, rounded to milliseconds. Split durations include all active time since the preceding split, including stationary time, but exclude pauses. Zero distance has no pace. No final partial split is emitted; presentation remains unresolved. State, full-split, and goal events carry run-scoped sequence IDs and are returned only once by the generating operation. Goals do not finish the run. No audio, media adapter, event persistence, or restart recovery is implemented.

### 5.6 Completed verification — 2026-09-12

- [x] Added pure Kotlin `RunModels.kt` and `RunController.kt`; no Android imports or additional app modules.
- [x] Added `RunControllerTest.kt` and the JUnit 4.13.2 test dependency.
- [x] `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain` succeeded from `android/`.
- [x] 27 tests passed, with zero failures, errors, or skipped tests. Coverage includes countdown boundaries, paused time/movement, wall-clock changes, known pace and unit examples, multiple splits, delayed measurements, source gaps/switches, step/GPS resets, invalid inputs, one-shot goals, duplicate events, and terminal finish.
- [x] Lint reports zero errors and the same three pinned-tool/dependency version advisories. No suppressions or baseline were added.
- [x] App display name changed to WAYiRUN through `app_name`; the existing shell and manifest already reference it. Provisional package/application identity is unchanged.
- [x] Generated files and local SDK configuration remain ignored. Existing untracked baseline files are preserved; no commit, push, remote change, or deployment was performed.

Artifacts:

- APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Unit-test report: `android/app/build/reports/tests/testDebugUnitTest/index.html`
- Unit-test XML: `android/app/build/test-results/testDebugUnitTest/TEST-com.example.runningapp.domain.RunControllerTest.xml`
- Lint report: `android/app/build/reports/lint-results-debug.html`

Device launch, screen-off operation, real sensors, media behavior, and durable recovery were not tested or implemented. The Android screen remains the name-only shell; this milestone verifies in-memory domain behavior. Milestone 2 is complete; stop here until Milestone 3 is authorized.

## 6 Milestone 3 — Durable local tracking prototype

### 6.1 Outcome

On a phone: start, record an outdoor or indoor run, pause/resume, swipe to finish, and open a persisted summary. Keep this a debug prototype until account integration is complete.

### 6.2 Scope and entry decisions

Add the minimal run screens, Room records, foreground tracking, GPS/step adapters, and state/completion cues. The user approved the entry decisions on 2026-09-12: a final Partial row with actual distance and pace; stride entered in centimeters/inches starting blank; time-only operation with Distance unavailable when no usable source exists; and paused recovery from the last saved checkpoint after interruption. REQUIREMENTS.md Sections 4.5, 5.9–5.10, and 13.3 are authoritative. Display fallback status honestly while cloud services are not implemented.

Do not include music linkage, AI, photos, public pages, cloud history, or achievement UI in this milestone. Preserve the full requested product in the requirements; this is a staged build, not removal of those features.

### 6.3 Verification and exit

Verify atomic finish and persisted summary loading. On a real phone, exercise screen-off running, pause movement, indoor mode without location access, missing GPS with working steps, later GPS availability, airplane mode, and activity recreation. Inspect recorded segments for gaps and duplicate distance. A short measured route and indoor step check must be compared with recorded output before claiming tracking is reliable. Report unresolved device-specific limitations.

### 6.4 Implementation delivered

- [x] Debug-only setup, countdown, active/paused, and saved-summary Compose screens. Large pause/resume controls and a finish drag that rejects taps and short swipes.
- [x] Persisted unit, stride-entry, countdown, mode, and appearance settings; no inferred stride. Prototype setup consistently shows Fallback because cloud services are absent.
- [x] A single serial foreground-service owner, GPS and cumulative step adapters, explicit source segments, missing-source status, and bounded wake-lock lifetime. No location listener is registered indoors.
- [x] Room schema version 1 with run, active-interval, source-segment, measurement, route, and split records. Unique active-run slot, terminal-run guard, and atomic checkpoint/finish transactions.
- [x] Recovery closes intervals at the saved checkpoint, preserves totals/event sequence, starts fresh baselines, and restores Paused. The unobserved interval is excluded.
- [x] Offline TTS state/goal cues when an installed offline voice exists; short tone fallback otherwise. No music transport, coaching, or configurable interval announcements were added.
- [x] Release source set contains only the shell. Debug permissions/service/storage/local identity do not appear in the release manifest or platform implementation.

The earlier Milestone 2 contract in Section 5.5 describes that milestone's delivered subset. Milestone 3 adds checkpoint recovery, active intervals, final partial-split presentation, source clearing, and a corrupt-sample processing bound. Architecture Section 3.5 describes the current integration contract and initial GPS filters. No actual GPS/step accuracy is claimed from these filters or emulator results.

### 6.5 Verification — 2026-09-12

From `android/`, the final combined command succeeded:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest :app:assembleRelease --console=plain
```

- [x] 34 JVM tests passed: original controller coverage plus serialization/reboot recovery, partial splits, GPS quality/gap handling, source exclusion, and unavailable-source timing.
- [x] Six instrumentation tests passed on Pixel 7 AVD, Android 15/API 35: four Room transaction/recovery tests and two Compose swipe/recovery-screen tests.
- [x] Debug APK and unsigned release-shell APK built. Release manifest inspection confirms no prototype service, location/activity permissions, or local authentication bypass.
- [x] Lint: zero errors, eight version-update warnings for pinned dependencies/tooling; no correctness warnings or suppressions added.
- [x] Manual emulator smoke: indoor time-only start with no location/step access; foreground timer and screen-off continuation; pause freezes time; force-stop and relaunch restores saved progress as paused with the interruption message; full swipe opens the saved summary.
- [x] Synthetic emulator GPS fixes exercised the actual outdoor adapter and location foreground service. Distance accumulated, the completed summary included a Partial row, and the same completed totals reloaded after force-stop/relaunch. This is simulated input, not a measured real-world route.

The first build attempt encountered the documented Windows read-only build-directory issue; only generated build output attributes were corrected. The first Compose test attempt encountered an asleep emulator; after waking/unlocking the AVD, both UI tests passed without weakening assertions. No physical phone was connected or tested; the user chose to defer those checks.

Artifacts:

- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- JVM report: `android/app/build/reports/tests/testDebugUnitTest/index.html`
- Emulator report: `android/app/build/reports/androidTests/connected/debug/index.html`
- Lint report: `android/app/build/reports/lint-results-debug.html`
- Unsigned shell APK: `android/app/build/outputs/apk/release/app-release-unsigned.apk` (verification only, not a product release)
- Local emulator screenshots: `android/app/build/verification/`

### 6.6 Phone evidence and limitations — updated 2026-09-14

- [x] Tester-reported functional passes on Moto G 2025 / Android 16: setup, controls, steps, outdoor operation, screen off, pause/finish, UI return, force-stop recovery, and reboot recovery. These were not independently repeated during this review.
- [ ] Quantitative outdoor route and indoor counted-step accuracy: no numerical comparisons supplied.
- [ ] GPS loss/return with working steps: airplane-mode test is inconclusive; inspect source intervals and route gaps for jumps or double counting.
- [ ] Permission denial/revocation and dedicated offline/audio test: deferred in the report, not tested passes. Activity recreation and other battery restrictions are not established by the reported UI-return test and unrestricted battery setting.
- [ ] Audible state/goal cues: implemented in source but reported silent. Diagnose installed artifact, playback conditions, and cue delivery. Verify paused-notification expectations against existing service behavior. Music linkage remains Milestone 4.

The timer and recorded measurements recover only through the last successful commit, nominally checkpointed each second and on measurements/transitions. GPS filtering and source handoff are initial policies requiring measured comparisons. No missing interval is backfilled. A countdown interrupted before tracking starts is not saved as a run. Finished runs are retained locally; this milestone exposes the most recent summary, not a phone history-management application. Functional phone evidence now exists; the full original gate is not verified. The report's recommendation to waive remaining checks is recorded separately from test evidence. Do not proceed automatically into Milestone 4.

### 6.7 Reviewed feedback and proposed next task

See [PHONE_TEST_REVIEW_2026-09-14.md](PHONE_TEST_REVIEW_2026-09-14.md) for source findings, feedback disposition, affected files, tests, and the bounded reliability plan. The [original report](WAYiRUN_Phone_Test_Report_2026-09-14.md) remains unchanged. Photos are absent as expected; basic state/goal cues are already implemented. The existing foreground service also intends to retain a paused notification, so reproduce that behavior before changing it.

Recommend investigating audio and paused-notification behavior next without waiting for quantitative accuracy testing. Preserve the latter as unverified follow-up, not a passing result. Keep UI polish separate, time-only distance estimation subject to a new calculation contract, and photo flow in its later milestone. This document update does not authorize app changes or accept embedded report instructions as new product requirements.

### 6.8 Reliability follow-up — 2026-09-14

Authorized by the user's instruction to continue development after confirming the test build's audio did not work. Scope stayed with audio and paused notifications; no estimator, photos, UI redesign, or music linkage was implemented.

- [x] Speech now uses media audio attributes, matching fallback tones; the debug activity's volume buttons control media volume. Previously speech used the UI-sonification category.
- [x] Serial cue output handles asynchronous speech errors/stops and a bounded 15-second missing-completion timeout with tone fallback. Immediate rejection, unavailable offline voice, and engine exceptions also fall back. Concurrent cues no longer overwrite each other's tones. Late callbacks cannot complete another cue.
- [x] Offline voice selection excludes voices advertised as not installed and prefers the exact device locale. Initialization callbacks are posted after engine assignment. Sanitized audio diagnostics contain no run IDs, speech text, or location data. Tracking persistence still precedes audio.
- [x] A recovered paused run restores its ongoing notification when opened. Ordinary pause already retained it. Android 12+ notification display is requested immediately. Paused sensors and wake-lock release remain unchanged.
- [x] Debug artifact identifies itself as `0.1.0-dev-audio1`; application ID and database schema remain unchanged.
- [x] 40 JVM tests passed: 34 prior tests plus six cue queue regressions. Eight emulator instrumentation tests passed: prior six plus media-volume routing and paused/recreated/resumed/finished notification lifecycle.
- [x] Debug build, lint, Android-test APK build, and release-shell build passed. Lint: zero errors, eight existing version-update advisories. Final debug verification command: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain`. Release was also verified during this follow-up with `:app:assembleRelease`.

Instrumentation ran explicitly on `emulator-5554` via `adb shell am instrument`, not on a phone. The first notification test timed out waiting for notification visibility; immediate notification display and a 30-second asynchronous test deadline were added. The failed test left an unfinished synthetic run; only the emulator's app test data was reset before rerunning. The final full suite passed eight tests. The emulator logged successful offline voice initialization; neither that nor fake-output unit tests prove audible phone output.

Artifacts: debug APK `android/app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `5C9397EC96B8E6835D110FC0B1504756C1CCC4E7BB9C843B1F0149A08591AD2C`. Final instrumentation transcript: `android/app/build/verification/audio1-instrumentation.txt`. JVM and lint reports remain at the Section 6.5 paths; the older connected-test HTML report is not the result of this direct ADB test run.

The user connected the Moto G, but repeated ADB inventories showed only the emulator. No physical-phone package, audio settings, or logs were inspected, and no phone install/data change occurred. The exact cause of the original phone silence remains unconfirmed. Retest all five cues on `audio1` with audible media volume, normal output routing, and then offline/screen off; check pause and recovered-pause notifications. See PHONE_TEST_REVIEW_2026-09-14.md Section 7. Accuracy and other deferred validation remain open.

### 6.9 Subsequent phone feedback and next passes

The user now confirms audible cues through both the phone speaker and headphones. The exact installed artifact was not inspected. Remaining observations: no music ducking, no two-way player/run pause linkage, and the ongoing paused notification appears under Silent. Audio focus and music linkage are not present in audio1. Do not describe basic cues as wholly silent after this report.

Direct user decisions now supersede the prior all-pauses rule only for pauses caused by WAYiRUN's own cues. Goal/completion speech must include active time, distance, and average pace. Ongoing notification prominence must improve. The completed summary must offer actual local discard with the exact uppercase prompt and swipe confirmation; phone deletion is no longer excluded.

See [NEXT_AUDIO_MEDIA_PLAN.md](NEXT_AUDIO_MEDIA_PLAN.md) for three bounded passes: (1) cue content, ducking, and notification prominence; (2) linked YouTube Music controls with cue-focus coordination; (3) completed-run discard. Each has its own verification boundary. These changes are recorded and planned here, not already implemented. Photos, estimation, cloud, and unrelated UI work remain outside these passes.

## 7 Milestone 4 — YouTube Music and linked controls

Current disposition, user decision September 14: treat active-session music controls as **implemented, with phone verification deferred**. The inaccessible music-access setting and unavailable player tests do not block further development. Preserve those tests as unverified evidence; do not repeatedly investigate restrictions or request a phone retest unless the user resumes that work or reports a new failure. The user's selected playlist-link/open interaction is implemented in Section 7.6.

### 7.1 Outcome

Pausing either side pauses both, and the requested headphone resume works without duplicate cues or restart of finished runs.

### 7.2 Scope and entry decisions

Add media-session access and the required user-enabled notification listener. Decide playlist entry and behavior when YouTube Music is absent or has no controllable session. Verify playlist launch separately from transport control. Do not substitute undocumented YouTube APIs.

### 7.3 Verification and exit

Test pauses from the app, headphones, YouTube Music, Android controls, and interruptions; test repeated notifications and headphone resume. Check music while cues play, screen off, and after finish. Latest explicit user decision: any music pause during a cue leaves tracking running, including an intentional headphone pause, with no automatic music restart. Outside cues, pauses remain linked; a direct run pause always pauses linked music. Report unsupported player behavior rather than silently changing the requirement.

### 7.4 Implemented active-session controls and discard — music1

The user explicitly authorized linked controls and discard, emphasizing that music off before starting must never inhibit a run. Work followed three bounded passes: cue/notification changes, active-session linkage, and local discard; earlier-pass regressions were rerun with the final build. No playlist launch, estimator, photos, cloud, authentication, or deployment was added.

- [x] Goal/completion speech includes active time, distance, and average pace in order, using the run's units. Speech/tone output requests transient ducking focus and releases it on success, failure, cancellation, and destruction. Denied focus skips audio; lost focus cancels it. The longer speech timeout is bounded at 60 seconds. Completion keeps the foreground service eligible only until queued audio drains.
- [x] DEFAULT-importance tracking channel with immediate display and no competing notification sound; migrate only an untouched prototype LOW channel. Explicit mute/importance settings survive, with a Run settings shortcut to Android's channel settings. Exact phone ordering is not guaranteed.
- [x] Debug-only YouTube Music session adapter and optional user-granted notification-listener access. Notification contents are not read or retained. Music off/paused/missing/access-denied starts a standalone run without starting music or pausing tracking.
- [x] Link arms only after observed playback during Running. Policy handles app/player pause and resume, duplicate echoes, stale callbacks, session replacement, and terminal finish. Run IDs and session generations reject stale queued controls. Unacknowledged commands detach after two seconds rather than reversing run intent. Recovery does not automatically resume from a player snapshot.
- [x] All music pauses during cue playback are exempt, per the user's latest explicit choice; a 600 ms settling interval handles delayed callbacks. Music is never automatically restarted after an exempt pause. Direct run pause still controls linked music.
- [x] Completed-summary Discard run, exact uppercase confirmation, cancel, and full-swipe deletion. Room transaction checks owner/Finished state and cascades all five child tables. No schema change. Service state clears only after success; stale confirmation/resume commands cannot delete another run or restore the deleted one. Failure preserves the run and reports it in the confirmation.
- [x] Final 55 JVM tests passed: 34 domain, 11 cue, 10 music policy. Final 15 emulator tests passed: six Room, two actual Android media-session adapter, four service/notification/audio-attribute, three Compose tests. Includes all-child-table deletion, unrelated-record preservation, rollback, cancel/short/full swipe, and stale discard/resume guards.
- [x] Debug APK, Android test APK, release shell, and lint passed. Lint has zero errors and eight existing version-update advisories; the new SwitchIntDef warning was fixed. Release manifest excludes the music listener and prototype tracking service.

Final command from `android/`: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest :app:assembleRelease --console=plain`. Instrumentation ran via explicit `adb -s emulator-5554 shell am instrument -w com.example.runningapp.debug.test/androidx.test.runner.AndroidJUnitRunner`. The initial audio2 notification check found the emulator's notification permission denied; only emulator test data/permissions were reset, and subsequent complete suites passed. Test runs never touched a physical phone or real user run.

Artifact: `android/app/build/outputs/apk/debug/app-debug.apk`, version **0.1.0-dev-music1**, SHA-256 `6D5F266B25AE1518433BD1499FE03D37474F01CE08C542DEE67AAEC212EA2E92`. JVM/lint reports retain their usual paths; the latest direct-ADB transcript is `android/app/build/verification/music1-instrumentation.txt`. Earlier audio2/music transcripts are intermediate results, not the final artifact verification.

No phone appeared in ADB. Actual YouTube Music duck/restore, headphone controls, screen-off linkage, and notification grouping remain for [MUSIC1_PHONE_RETEST.md](MUSIC1_PHONE_RETEST.md). The adapter tests use test-owned Android media sessions, not the installed YouTube Music app. Optional notification access must be granted by the user. Playlist configuration/launch remains outside this delivered subset, so the whole original Milestone 4 is not marked complete. Quantitative tracking accuracy and previously deferred permission/battery checks also remain unverified.

### 7.5 First-open permissions — permissions1

Authorized September 14 as a bounded setup correction. Added `ui/PermissionSetup.kt` and its two Compose tests; replaced the debug activity's start-time request flow with first-open requests and persistent setup completion. Updated the setup entry in `WayirunApp.kt` and the debug version suffix. No run controller, storage schema, music policy, application identity, or signing changes.

- [x] First open requests location, Physical Activity, and Notifications for implemented capabilities; no stride entry or run start is required. Android-version guards retain API 28 compatibility.
- [x] Actual grants refresh after Android settings returns. Location distinguishes approximate from precise. Notification-listener access is displayed separately from notification permission.
- [x] Component-specific music-access page on API 30+, older/general fallback, and contextual handling of unavailable settings activities. No unverified overflow-menu directions or attempted restriction bypass.
- [x] Completed setup persists; grants are not repeatedly requested on ordinary launches. Declining access permits continuation and standalone tracking. Run settings can reopen the setup screen.
- [x] 55 JVM tests and 17 emulator instrumentation tests passed, including the two new setup checks for denial/continuation and controls disabled during a pending system request.
- [x] Debug build, test APK, release shell, and lint passed. Lint: zero errors and eight dependency/tool version advisories. The initial additional KTX suggestion was corrected.
- [x] Fresh-data Android 15/API 35 emulator smoke: all three real system prompts appeared on launch, grants displayed correctly, the music shortcut opened WAYiRUN's own access page, access was granted through Android, returning displayed Allowed, and completion survived force-stop/relaunch. Only emulator test data was cleared; no phone data was touched.

Final command from `android/`: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest :app:assembleRelease --console=plain`. Instrumentation uses the explicit emulator serial and its output is `android/app/build/verification/permissions1-instrumentation.txt`. APK: `android/app/build/outputs/apk/debug/app-debug.apk`, **0.1.0-dev-permissions1**, SHA-256 `A5AB29559F59143448A4A66C2D5A9B73561A430BFEE96B03FC4729D127CEEB76`.

Limitation: no physical phone appeared in ADB. The Moto G Android 16 restricted-setting denial is NOT resolved or verified by this change. Runtime permissions cannot grant notification-listener access or remove Android's installation restriction. Actual player/headphone behavior and that installation-specific grant remain separate phone checks. No new background-location, microphone, camera, or future-feature permissions were added. Next sequential work remains Milestone 4 playlist-entry/launch planning and player verification; accounts/cloud remain later.

### 7.6 Saved playlist and pre-run open — playlist1

The user selected saving a YouTube Music playlist link and opening it before the run. This replaces the earlier automatic-start/toggle proposal for this interaction. Implemented the field, Open playlist, and Clear playlist directly on pre-run setup. Opening saves a canonical link in existing local settings and dispatches a package-targeted Android ACTION_VIEW intent. No permission, service, controller, database schema, or dependency change. Missing/invalid links and an absent/blocked player do not disable START RUNNING. No browser fallback or undocumented playback API; YouTube Music owns playback after opening.

Files: new `tracking/PlaylistLink.kt`, `ui/PlaylistSetup.kt`, and corresponding JVM/Compose tests; updated `WayirunApp.kt` and debug version suffix. URLs accept HTTPS YouTube Music/YouTube playlist pages only, require one valid playlist identifier, and discard share parameters. Tests cover malformed/ambiguous/foreign-host URLs, canonicalization, save/open/clear, and launch-error presentation with the saved choice retained.

Build: **0.1.0-dev-playlist1**, `android/app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `16BA54E75B2A6BDC0371072A71321891CAA4492F793621BC7DE5C4E0D76F3532`. Unit tests (58), debug build, test APK, and lint passed; lint has zero errors and eight existing version advisories. Command: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain`.

All 19 instrumentation tests passed on the Android 15/API 35 emulator, including two new playlist Compose tests. Transcript: `android/app/build/verification/playlist1-instrumentation.txt`. The initial run failed the existing notification lifecycle test because the restored emulator had POST_NOTIFICATIONS denied; only emulator test data was reset and runtime grants restored, then the full suite passed. No phone data was altered. Release sources were unchanged and no release build was rerun in this playlist pass.

Phone music verification remains deferred by explicit user direction. Opening is not proof of autoplay or actual player linkage. Milestone 4 implementation follows the latest accepted interaction; next sequential milestone is Milestone 5 accounts/cloud planning. No commit, push, production configuration, or deployment occurred.

Platform references for this implementation: [YouTube Music sharing](https://support.google.com/youtubemusic/answer/9198182?hl=en) and [Android common intents](https://developer.android.com/guide/components/intents-common). Actual YouTube Music playback is not inferred from URL validation or injected-launch UI tests.

### 7.7 Every-opening permission check and dated APK — permissions2

User requested the Cloudflare/Git environment guide first, then preserving the existing splash/setup screen while checking all three runtime grants on every opening. Delivered `CLOUDFLARE_GIT_SETUP.md` before app changes: current Git remote verified as `ShootPara/RunningApp`, local GitHub CLI/Wrangler login instructions, and planned development Worker/D1/Workers Builds setup. No remote changes or cloud resources were created.

The activity no longer trusts legacy requested/completed preferences. Every foreground opening shows setup and requests missing location, activity, and notification grants before opening the run screen. Settings returns and permission results refresh grants without loops; configuration recreation retains progress. Already-granted permissions do not prompt. Android-suppressed repeat denials show the App permissions route. Existing run tracking continues independently. The splash appearance is retained, with a debug build label added to it and the main run screen.

Gradle now exports a dated `WAYiRUN-permissions2-yyyy-MM-dd_HH-mm-ss_EDT.apk` (EST in winter) alongside `app-debug.apk`, using America/New_York time. BuildConfig is enabled for visible version identification. Playlist entry remains on the pre-run screen and is not hidden by missing music access. Announcement-selection controls are still outstanding product work, not disabled controls.

Verification also exposed an existing stale-resume foreground-service timeout after discard. `TrackingService.send` now rejects a known wrong run ID or a resume of a nonpaused loaded run before requesting foreground-service startup. Existing stale-command tests cover the case. No schema, run calculation, music policy, or new permission changes.

Unit tests (58), debug build, test APK, and lint passed; lint has zero errors and eight existing version advisories. Initial Gradle export-task configuration errors were corrected, and the known Windows read-only attributes on generated build directories were cleared before successful verification. No source/data deletion occurred.

Final verification: all 20 emulator instrumentation tests passed, including old-completion-flag rejection, configuration recreation without repetition, and background-return checking. Transcript: `android/app/build/verification/permissions2-instrumentation.txt`. Manual Android 15/API 35 emulator smoke showed all three real runtime permission dialogs on fresh launch; after declining all three, force-stop/relaunch requested location again. This confirms the former one-time gate is gone. A transient UI-dump timing failure was retried after the activity loaded; it was not a permission failure. Only emulator test data was cleared.

Final artifact: `android/app/build/outputs/apk/debug/WAYiRUN-permissions2-2026-09-14_17-36-34_EDT.apk`, version **0.1.0-dev-permissions2**, SHA-256 `C334A3E19D7F72CF749F8DB3A1B18664CA58C744D100A23716E1790D267E50C6`. Earlier timestamped output from the intermediate build is not the handoff. Debug unit/build/lint/test-APK command passed; release was not rebuilt in this pass. No physical phone install or verification occurred.

Next remains Milestone 5 environment preparation and accounts/cloud planning after this bounded startup fix. Phone music testing stays deferred by explicit user decision.

## 8 Milestone 5 — Accounts and cloud run storage

### 8.0 Authorized environment preparation

User completed guide Sections 1–3 and explicitly authorized Section 4, including cloud repository preparation, development Worker/D1 creation, and the development branch. Announcement-selector work is deferred by this explicit direction. No further phone music testing is required as an entry gate.

Implemented `worker/` with pinned package lock, strict TypeScript, development configuration, metadata-only D1 migration, six workerd/D1 tests, guarded deployment and seven live smoke checks. Created `wayirun-dev` and `wayirun-dev-db`; the Worker is reachable at `https://wayirun-dev.unopenedparachute.workers.dev/healthz`. All final tests, dry run, local/remote migrations, and live checks passed. Clean npm install audits to zero vulnerabilities. See `worker/STATUS.md` for actual IDs and limitations.

Cloud endpoints deliberately do not accept run data before authentication is implemented. The Google/account/sync portion below is not complete. Cloud-only work and the setup guide were committed as `d57fb6e` and atomically pushed to `codex/cloud-foundation` and `development`; main remains unchanged. Prior Android and cumulative document edits remain local and uncommitted. Section 5's one-time Cloudflare GitHub app connection is separate and not claimed complete. No production resources or phone data were changed.

### 8.0.1 Account-session foundation — September 15, 2026

The user connected Cloudflare Workers Builds and requested the Google setup guide. Implemented a bounded backend slice: Google signature/claim verification, single-use login challenges, hashed expiring sessions, profile lookup, and logout. Added additive D1 migration 0002 and worker/AUTH_CONTRACT.md. All 12 bundled-Worker/D1 tests pass, covering invalid tokens, account separation, replay/concurrency, expiry, revocation, input limits, and failure handling. Google IDs are not supplied, so live authentication remains disabled. See GOOGLE_SIGN_IN_SETUP.md for the requested guide.

Remaining before real sign-in: final Android identity/signing and client-party configuration, login request-rate controls, and actual Google/phone integration. Remaining Milestone 5: offline ownership decisions and retry-safe completed-run upload/download. No new Android build or phone verification occurred in this backend slice. Existing local runs and pending Android changes are preserved. Music and announcement-selector deferrals remain in effect.

Commit `9a57b3e` reached development and triggered Workers Builds. That build applied the migration/deployed but reported a failed check; logs require browser sign-in or Builds API access not present in the CLI login. The guarded local deployment then passed all nine live checks, version `f0b1504d-ad16-41b8-83e2-1321c47cf1dd`. Added bounded smoke retries and two retry-behavior tests (14 total passing). Remote migration ledger confirms 0001 and 0002; local reapplication has no pending work. See worker/STATUS.md for follow-up pipeline status.

The user supplied the first build logs: all tests/migration/deployment succeeded, but an immediate smoke request received the old Worker response during propagation. Fix commit `48b805f` passed Workers Builds end-to-end (GitHub conclusion success), including all 14 tests and nine live checks. Development is pushed; main remains unchanged. Google setup is the remaining user action for authentication integration, not a Cloudflare permission repair.

### 8.0.2 Google sign-in and corrected launch/settings experience — September 15, 2026

User supplied project wayirun-development plus Web and Android development client IDs. Cloud commit `2044f5b` configures the exact audiences/authorized party and rate limits; Workers Builds completed successfully. All 16 backend tests pass, including rate limiting, replay rejection, account isolation, and missing/failed limiters. Nine live deployment smoke checks pass without creating accounts. Production identity remains unregistered; the debug package/signing identity stays unchanged so updates retain existing local records.

Android handoff: **0.1.0-dev-signin-settings1**. Added optional Google Credential Manager sign-in, basic profile display, one-hour server sessions stored under Android Keystore AES-GCM in a no-backup atomic file, and local sign-out. Invalid/cancelled/offline sign-in never gates starting a local run. Account actions are under the gear. Existing local run owners and data are never reassigned to a Google account. Real Google consent/sign-in on the user's phone remains unverified; no cloud run synchronization is included.

The user's surgical UX correction is implemented: the original Android launcher splash/icon remains, the in-app setup overlay is removed from the activity, missing permissions are requested once at startup, and focus/background/configuration returns never reopen setup. Active/restored paused runs bypass startup prompting. The top-right gear opens/closes settings (Back also closes); editable mode, units, goal/target, countdown, playlist draft, stride/unit, appearance, account, and permission actions are behind it. Changes save immediately with no Save button; values survive reopening. Main screen keeps a concise run summary, optional Open playlist action, and start/active/summary controls. Editing settings during a run does not change its captured settings or pause it.

Verification: **58 JVM tests and 25 emulator tests passed, zero skipped/failures**. Debug APK, instrumentation APK, and lint pass; lint has **zero errors and nine dependency-version advisories**. Emulator checks cover encrypted session round-trip/tamper rejection, missing/busy/failed/expired sign-in permitting start, settings persistence without saving, gear closure, no setup on recreation/background return, and no pause when editing during an active run. Existing notification/media/storage regressions also pass. The first UI run was invalidated by the emulator being asleep; with emulator AC/stay-awake enabled and required test permissions explicit, the complete suite passed. Earlier build failures came from read-only/stale duplicated generated intermediates; old intermediates were preserved under build/ and regenerated successfully. No source/run data was deleted.

Final APK: `android/app/build/outputs/apk/debug/WAYiRUN-signin-settings1-2026-09-15_13-38-49_EDT.apk` (32,818,748 bytes). SHA-256: `175FB70950A91FBD2376180486568B91EAFDB5EC5346E69F0BB644623C05C77F`. APK signature verifies with the existing debug SHA-1 `E8:29:5C:0F:4A:15:A5:7D:87:38:CE:28:6F:67:87:6C:91:CF:05:94`. Install as an update; do not uninstall to preserve existing local runs. No physical phone was connected/installed in this pass. See `PHONE_TEST_SIGNIN_SETTINGS1.md` for the focused phone checks.

Next sequential work remains Milestone 5: verify actual phone Google sign-in, define deliberate ownership/import of pre-account runs and first-ever offline sign-in behavior, then implement retry-safe completed-run upload/download. Do not resume the rejected every-foreground setup screen. Music device testing and announcement-selector work remain deferred. Android/root cumulative edits and final verification notes remain local; only the scoped cloud configuration/guide changes were committed and pushed.

### 8.0.3 Main-screen controls and single APK handoff - September 15, 2026

User confirms real Google sign-in succeeded on the phone. That device check is now passed; run synchronization is still unimplemented. This bounded UX follow-up restores unlabeled Indoor/Outdoor and None/Time/Distance selectors and the selected target to the pre-run screen. Only the playlist launch button appears there; the URL remains in settings. Mode/Fallback indicators remain visible before and during a run. Build/account/music explanations move behind the gear. Additional settings, including future announcement intervals and the on/off switch, belong in settings. All edits still save automatically; active runs retain their settings snapshot. Fallback remains accurate for the local-only run service and does not claim Google login enables cloud sync.

APK filenames now contain only WAYiRUN and the timestamp; exporting removes previous named handoffs. After verification, remove generated tooling/test/release APKs as well so only the intended install remains. No new cloud, tracking, authentication, or permission logic is introduced. No physical phone install, commit, push, or deployment in this pass.

Verification: all 58 JVM tests and all 25 emulator instrumentation tests passed, with zero failures/skips in the final run. Debug build and lint passed; lint has zero errors and nine version advisories. APK signature verifies. A prior UI assertion expected the intentionally removed heading; it now checks the returned START control after discard. Generated build output read-only attributes blocked packaging/test-output cleanup and were cleared only under app/build before successful reruns.

Final APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-15_20-02-35_EDT.apk`, version 0.1.0-dev-controls1, 32,818,728 bytes. SHA-256: `35C6E97087F9915E856F0CDAF9F8A9B640F8DC47250AF2961A93EFD5B0BD2ADC`. Export removed previous named handoffs. Approval review blocked shell deletion of temporary app/test/release APKs; these were instead moved reversibly to the Windows TEMP directory WAYiRUN-apk-archive-20260915-200235. The final handoff is the only APK remaining in the repository. Install over the existing app to preserve local runs and settings.

Next sequential development remains Milestone 5 account ownership and completed-run synchronization, including deliberate handling of pre-account records. Do not reopen completed Google sign-in setup or move future settings onto the main screen.

### 8.0.4 Resumable server storage and direct APK naming - September 15, 2026

Plan: correct APK packaging, then implement the bounded server transport portion of Milestone 5. Guardrails: preserve the accepted phone UI and local records, derive every server owner from the verified session, make retries immutable, and do not enable phone uploads until ownership/import/discard reconciliation is ready. The user approved an explicit Add existing runs to this account action in gear settings; existing records stay local until chosen. New runs will retain the account selected at start, including returning-account offline recording. The Android implementation of that policy is next.

Implemented migration 0003, resumable manifest/chunk uploads, hash/size verification, atomic completion, stable retry receipts, owner-filtered paginated retrieval, and bounded/expiring drafts. Account and run endpoints share verified-session and rate-limit guards. All 27 bundled Worker/workerd/D1 tests pass with no skips/failures, including preservation of existing accounts/sessions, concurrency, foreign-owner refusal, malformed data, exact-byte downloads, expired credentials, and storage-write failures. Initial completion failures exposed empty POST streams; this is fixed and the full suite passes. Test processes have a 60-second deadline. See worker/RUN_STORAGE_CONTRACT.md for bounds and limitations: the transport validates manifests and byte integrity, while Android archive schema encoding/decoding remains unimplemented.

APK packaging now names the actual AGP output instead of copying app-debug.apk. Two consecutive debug builds passed and each left exactly one timestamped APK with matching output metadata. Lint passed with zero errors/nine version advisories. APK signing verifies. The final artifact is `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-15_20-23-03_EDT.apk` (32,818,728 bytes), SHA-256 `35C6E97087F9915E856F0CDAF9F8A9B640F8DC47250AF2961A93EFD5B0BD2ADC`. Its bytes are identical to the accepted controls1 phone build, so no new phone retest is required for packaging. The existing 58 JVM/25 emulator results remain the prior UI verification; they were not rerun for this build-script-only Android change. No phone installation occurred.

Cloud-only commit e257bd0 was pushed to development under the user's existing deployment delegation. Workers Builds completed successfully; all nine live smoke checks passed. The remote migration ledger confirms 0001, 0002, and 0003. Verification created no remote test accounts or run data. Main and all cumulative Android/root-document edits remain untouched by the cloud commit.

Next: Android archive serialization/validation, immutable account ownership and explicit import, durable same-owner retries, and discard reconciliation before enabling phone synchronization. Milestone 5 is not yet complete; no photos, AI, desktop UI, production deployment, or announcement-selector expansion occurred.

### 8.0.5 Android upload, explicit import, and safe discard - September 16, 2026

Plan: complete the Android upload side of Milestone 5, including durable ownership and discard protection. Guardrails: preserve existing records, keep account/import controls behind the gear, never block offline tracking, and defer download/restore and production work.

Implemented Room v2 migration preserving legacy rows as unassigned; account ownership captured at run start; validated versioned archives containing checkpoint, route, measurements, intervals, segments and splits; durable resumable upload queue with bounded retries; account checks before each request; explicit confirmed import; and local/cloud discard reconciliation. Account changes and import are disabled during active runs. Expired known accounts retain ownership offline and require fresh sign-in for network synchronization. WorkManager schedules connected work and periodic recovery; new discards wake pending work promptly. Server deletion markers prevent stale uploads from resurrecting deleted runs.

Verification: 58 JVM tests and 34 API 35 emulator tests passed, including actual v1-to-v2 migration, all-table archive preservation, import rollback/idempotence, lost completion response, account switches, expiry, bounded offline retries and discard/upload races. Debug build and lint passed with zero errors and 11 version advisories. All 29 bundled Worker/D1 tests passed. APK signature verified against the existing debug certificate. No physical-phone sync or real-account upload is claimed.

Artifact: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-16_07-57-16_EDT.apk`, version `0.1.0-dev-sync1`, SHA-256 `5CDDC55E1D9EAA30E80B8084D237B5460B178CC1ADAE4023E1949A9D07CAE615`. The instrumentation package is archived outside the repository after checks, leaving one handoff APK. See PHONE_TEST_SYNC1.md.

Cloud-only commit `5b5e7ed` was pushed to development; final deployment results are recorded in worker/STATUS.md. Android and root-document changes remain uncommitted. Milestone 5 remains open: next implement authenticated download/restore with validation and deletion reconciliation. History/maps, production identity, music verification, and announcement-selector work remain deferred.

### 8.0.6 Download/restore and planning review - September 16, 2026

Plan: finish account-scoped download/restore and deletion reconciliation. Guardrails: preserve existing local and cloud records; validate complete archives before atomic insertion; never overwrite local identities or active runs; retain offline tracking and the accepted sparse UI. No production, desktop UI or unrelated feature work. Existing development-only cloud commit/deployment delegation remains in force.

Planning review found stale current-state claims saying cloud services and Android synchronization were absent. Current summaries are corrected; dated historical results remain intact. User-confirmed sync1 phone checks pass, and a read-only remote count confirms exactly two completed cloud runs. The initial D1 request returned an authorization error; the subsequent query succeeded without data changes or new permissions.

Restore uses Room v3 account cursors, private resumable chunks, exact manifest/chunk hashes, archive/owner/summary validation and transactional child-row insertion. Deletion sweeps precede run retrieval and repeat to catch insertions before earlier cursors. Existing runs are never replaced. UI changes stay within gear sync status. Cloud-only commit `944338a` passed Workers Builds; all 30 backend tests and nine independent live smoke checks pass. Two completed cloud runs remain present after deployment. The user's baseline commit `4306728` is preserved; a separate `codex/restore-transport` worktree kept this deployment scoped to Worker files. Do not push the main checkout directly over development: its history now differs from the scoped deployment branch. Subsequent cloud changes should be based on the current remote development head.

Final verification: **58 JVM tests and 45 API 35 emulator tests passed**, zero failures/skips. Debug build and lint passed with zero errors and 11 version advisories. Added coverage includes v2-to-v3 preservation, all-table/idempotent restore, resumed verified chunks, altered manifest/wrong owner/summary mismatch, account switch mid-download, local/remote deletion races, transaction rollback, child-ID collision prevention, bounded retries and durable pagination across full sweeps. Initial emulator execution was blocked by read-only generated build output; clearing attributes only under app/build resolved it. One test fixture initially invalidated its own archive while attempting to alter only the summary; the fixture was corrected and the full suite passed. No phone installation or user-data mutation occurred. The generated test APK is archived outside the repository, leaving only the dated handoff.

APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-16_15-46-55_EDT.apk`, version `0.1.0-dev-sync2`, 33,820,584 bytes, SHA-256 `FFB6BA6C64BB143C9699A228C438AFF84D46504EA13B738B11BCE305D968893B`. Existing debug signature verified. Install over the previous build; do not clear local data. PHONE_TEST_SYNC2.md describes optional separate-device restoration without risking the working phone.

Limits: restored cloud data never overwrites an existing local run; an invalid/conflicting archive blocks that account's pull until retry/correction while uploads and recording remain independent. Repeated deletion sweeps provide eventual reconciliation, not instant deletion on disconnected phones. Real Google cross-device restore has not been phone-verified. Production identity and release app remain separate, and music/announcement deferrals still apply.

### 8.1 Outcome

Google-authenticated users can synchronize completed runs without mixing accounts or losing offline runs.

### 8.2 Scope and entry decisions

Create the Worker and D1 implementation, authenticated sessions, account ownership, and retry-safe upload/download. Establish the real application identifier and test OAuth configuration. Resolve initial offline sign-in and pending-run behavior during account changes. Use separate local/test/production configuration and keep production untouched while validating.

### 8.3 Verification and exit

Verify token rejection, two-account isolation, conflicting operation IDs, duplicate upload retries, interrupted uploads, expired authentication, and offline recording followed by synchronization. Confirm there is no development authentication bypass in release builds.

## 9 Later milestones — Define in detail when reached

These preserve the complete product scope without pretending their unresolved details are implementation-ready.

### 9.1 Desktop history and maps

Choose a map provider for Android and web, then add authenticated history, run details, maps, and statistics. Verify owner filtering and indoor/gap rendering. Do not turn the phone into a statistics-management application.

### 9.1.1 Private desktop history - first slice

Plan: implement Google browser access, history pagination, loaded-set distance/time/weighted pace, unit conversion and validated run details/splits/settings on the existing development Worker. Guardrails: separate cookie/CSRF handling preserves native API behavior; browser access is private and read-only; no data mutation, Android changes, paid-service signup or unrelated product features. Maps/provider setup is the next bounded slice.

Implemented: public app shell plus private browser APIs, protected one-hour cookies, nonce binding, exact-origin mutation checks, history and details. See worker/DESKTOP_CONTRACT.md. All 33 backend tests and fixture browser checks passed, including account isolation, cookie protection, expiry/replay, origin rejection, totals/units, corrupt chunks, literal user text, narrow layout and logout clearing. Cloud-only commit `83fbf98` passed Workers Builds. All 14 live smoke checks passed; a read-only count still confirms two completed cloud runs. The live sign-in page loaded its Google frame without JavaScript errors. The user confirmed authorized-origin setup; actual Google browser sign-in remains unverified. Existing sync2 APK remains the phone handoff. A separate external-review audit passed release assembly and the up-to-date JVM test gate; no Android implementation changed. See CODE_REVIEW_TRIAGE_2026-09-16.md for rejected claims and retained queue/performance/recovery follow-ups.

### 9.1.2 Desktop Google sign-in repair - September 17, 2026

User screenshot and live inspection reproduced an oversized unstyled Google logo and a Google origin error. The earlier fixture button and iframe-presence checks were insufficient. Added a fresh per-response style CSP nonce on the Google client script, Google's recommended strict-origin-when-cross-origin referrer policy, and a bounded 260px sign-in container with a 20px icon. Script restrictions, private cookies, nonce-bound authentication and account isolation remain intact.

All 34 bundled Worker/D1 tests passed, including fresh nonce/header regression coverage. Cloud-only commit bd9bd8a passed Workers Builds on development; all 14 independent live smoke checks passed. The actual deployed Google widget renders a compact personalized button at desktop and 390px widths, with no fresh browser warnings/errors or narrow-screen horizontal overflow. Automated pointer activation was blocked by the browser tool's fractional-iframe-coordinate limitation; full real-account sign-in/history verification remains the user's check. No Google Console change, Android build/install, production deployment or run mutation was performed. Maps remain the next feature slice after sign-in acceptance.

### 9.1.3 Desktop route maps - September 17, 2026

User confirmed real browser sign-in/history with a screenshot showing two runs, then authorized continuing the plan. Week/month/year/lifetime selections, sorting/display options, achievement statistics and possible charts are explicitly deferred (REQUIREMENTS.md Section 11.1).

Plan and guardrails: add a map to verified desktop run details, select/configure a provider suitable for small development usage, preserve GPS gaps and indoor/no-GPS behavior, and avoid Android changes, data mutations, paid signup, statistics expansion or production deployment. Implemented locally served Leaflet 1.9.4 with OpenStreetMap Standard tiles, zoom/Fit route, recorded-GPS endpoint markers, date-line handling, tile failure messaging and map teardown on navigation/logout. See worker/MAP_CONTRACT.md for provider/privacy limits. Production capacity and any future Android map surface remain separate.

Verification: all 45 automated Worker/D1/geometry/lifecycle tests pass. Local browser fixtures passed outdoor two-segment rendering, zoom/Fit route, indoor/no-GPS, single point, corrupt archive rejection, tile failure with details intact, logout clearing, and a 390px layout without horizontal overflow. Fixtures use synthetic tiles and no private runs or OSM bulk requests. The user subsequently accepted the maps and authorized continuing. Cloud-only commit 08f6fa7 passed Workers Builds on development; all 18 independent live smoke checks passed, including the four new map assets. The sole sync2 phone APK remains unchanged.

### 9.2 Export and deletion

Define the complete CSV representation, deletion reconciliation, marker retention, and Health Connect deletion policy. Implement one-button export and actual data/object deletion. Verify public access is removed, cleanup retries complete, and a stale phone cannot resurrect a deleted run.

### 9.2.1 Complete CSV export - September 17, 2026

The user selected one CSV containing summary and detailed records. The plan is recorded in worker/EXPORT_DELETION_PLAN.md; deletion UI is a separate next slice. Implemented Export all runs with full pagination, sequential verified archives, six record types, complete record_json preservation, spreadsheet formula protection, explicit output limits and cancel/sign-out guards. All 53 automated tests pass. Browser verification downloaded and independently parsed a four-run CSV from a history showing only two runs; cancellation, sign-out and corrupt archives produced no additional file. Narrow-layout controls remain usable. Development commit 6cee9e0 passed Workers Builds; all 19 independent live smoke checks passed. No Android update or real-run mutation.

### 9.2.2 Export rate-limit and session repair - September 17, 2026

User reported HTTP 429 during export of four real runs. Root cause: each archive chunk consumed the shared 30/client/minute authentication budget; earlier one-chunk browser fixtures missed it. Run transfer now has separate 300/client and 3000/global per-minute budgets. Export retries the same GET after Retry-After, up to three retries, with cancellable waits and retained verified chunks. App sessions now last 90 days; website account access renews valid sessions/cookies, including old still-valid short sessions. Expired/revoked sessions remain invalid. Native sessions get the longer expiry at next sign-in; no Android code change.

All 57 automated tests passed, including forty real Worker chunk reads with an exhausted sign-in budget, renewal/revocation/expiry, and abortable throttling. Browser-injected 429 visibly waited then downloaded a complete four-run CSV. Development repair commit e5e45a5 passed Workers Builds and all 19 live smoke checks. Real export subsequently verified from the user-provided CSV: four runs, 6720 GPS points, 6722 measurements, 11 splits, four intervals and eight source segments. Per-run summary, split totals, interval/segment payloads and record indexes match.

### 9.2.3 Desktop selected-run deletion - September 17, 2026

After verifying the real CSV, continued the authorized separate deletion slice. Explicit loaded-row checkboxes open an app-styled confirmation listing dates/distances; Cancel receives initial focus and Escape cancels. Sequential owner-scoped deletion removes only acknowledged successes and recomputes totals; failure stops the batch and preserves unresolved selections for idempotent retry. Refresh clears selection; sign-out clears pending UI state. Existing server cascade/deletion markers and phone reconciliation are reused; no new Android code or real user run deletion.

All 58 automated tests pass, including browser CSRF/body/method boundaries, account isolation, exact data/chunk removal and repeat deletion; existing stale-upload/race tests remain green. Local browser tests passed cancel/Escape, partial failure, retry, totals, selection scope, untouched unloaded runs, sign-out and 390px modal layout. Cloud commit 2dcd76a passed Workers Builds; 19 live smoke checks passed and the deployed deletion interface was verified. Next user check: select a run, inspect confirmation, Cancel. Only confirm deletion for a disposable run; cross-device disappearance requires its next authenticated sync. Section 9.3 AI coaching planning is next after acceptance.

### 9.3 AI coaching

Add encrypted per-user OpenAI keys, completed-run comparisons, voice, checkbox behavior, and onboard fallback recordings. Voice and history are settled: Cedar through gpt-4o-mini-tts; all stored data for the current and immediately previous completed run. See REQUIREMENTS.md 8.2–8.4 and worker/COACHING_PLAN.md. First bounded slice is per-account API-key setup/encrypted storage. The separate text model, full-input size limits, timeout/unknown-outcome handling and animation remain integration planning items. Test two-account credential isolation, invalid keys, offline failure, opt-out, and duplicate requests. No shared owner key is permitted.

### 9.4 Achievements and celebrations

Agree the catalog, thresholds, repeatability, calendar/time-zone rules, deletion effects, and multiple-award presentation. Implement immediate awards with finish-time reveals and persistent history. Verify offline and cross-device reconciliation.

September 18: user accepted ACHIEVEMENTS_PLAN.md and authorized implementation. Initial evaluator, owner-scoped derived persistence, finish celebration, desktop history and CSV export are implemented; see that document's Section 1.8 for verification and conservative performance-data limits. Photos in 9.5 are next, before voice selection or expanded statistics. REMAINING_WORK.md is the concise current remaining schedule.

### 9.5 Photos and public run pages

Implement camera/picker/skip, selectable overlays, preview/retake/keep, Android save/share, private storage, and checked-by-default publication. Resolve offline publication, existing-photo retake behavior, and publication without a photo before those paths. Test public-field allowlists, unchecked privacy, and deletion of public assets.

### 9.6 Health Connect

Export completed runs with stable IDs and appropriate permissions. Test availability, revoked access, duplicate retries, and the approved deletion policy. No import or watch app is included.

### 9.7 Product verification and release preparation

Complete requirements Section 16 on actual target devices, including units, dark mode, announcement choices, all fallback paths, and data ownership. Finish release identity, signing, and external-service configuration; inspect Cloudflare usage against the user's account limits. Release deployment is a separate milestone after validation, not an incidental step during development.

## 10 Next execution brief

Milestone 5 synchronization is implemented with sync1 phone acceptance and separate sync2 restore evidence. Section 9.1 implements private Google sign-in, history, loaded-run totals, validated details and desktop route maps. Real browser sign-in/history is user-confirmed. Maps are user-accepted. Section 9.2 now has a complete single-file CSV export. Section 9.2 now also implements desktop selection/deletion. Next is user acceptance of deletion, then Section 9.3 AI coaching planning. Expanded statistics, charts and achievement displays remain deferred. AI, photos, Health Connect and production stay later.

The user baseline remains 4306728 on codex/account-sessions. Remote development receives scoped Worker work; the latest slice is commit 2dcd76a through codex/desktop-run-deletion, based on e5e45a5. Preserve both histories and prepare subsequent cloud changes from the current remote development head. Android/root-document commits remain user-owned. The existing sync2 APK is unchanged by web work.

## 11 September 18 handoff and execution priority

Login repair takes priority. The server's 90-day session response was rejected by Android AccountApi's one-hour maximum; the previous claim that no Android update was needed was incorrect. Updated the bounded parser to accept 1 through 7776000 seconds. assembleDebug and lintDebug both passed (BUILD SUCCESSFUL, 51 tasks). Replacement APK: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-17_19-59-43_EDT.apk, 33820584 bytes, version 0.1.0-dev-sync2. Install over the existing app; do not uninstall. The user confirmed Google phone sign-in works with this update. No phone installation was performed by the agent.

User requested incorporation of the pasted coaching decisions before continuing. REQUIREMENTS.md now records Cedar/gpt-4o-mini-tts and all current-plus-previous run data; broader PR/trend claims are deferred. worker/COACHING_PLAN.md defines the next bounded key-management slice and remaining generation decisions. No API key was requested or used, and no coaching code or paid generation was added. Do not commit or push unless requested; the earlier one-time backup is already on GitHub as b3f0d51.

## 12 Per-user key setup - September 18, 2026

After user-confirmed login repair, implemented the bounded key-management slice: masked secure entry in gear settings; encrypted server-side storage; status, replacement and confirmed removal; provider credential checks without paid generation; revision guards against stale saves/account changes. See worker/KEY_STORAGE_CONTRACT.md for API, encryption, recovery/rotation and test boundaries. Cedar/gpt-4o-mini-tts and current-plus-previous full data remain fixed for the upcoming coaching integration.

Verification: 65 Worker tests pass, debug assembly/lint pass, and two focused emulator UI tests pass. The emulator initially slept through test activity setup; the test harness now keeps its own activity visible. Development migration 0005 and Worker version 1905ec88-bad5-411e-b20e-a0515cf043de are deployed; 20 live smoke checks pass. The encryption keyring secret was verified present after deployment. No real OpenAI key was used or billed, no user runs changed, no phone installed, no production deployment and no Git commit/push.

Current APK: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-18_08-58-18_EDT.apk (33886120 bytes). Install over the existing app, open the gear, then AI coaching → Add API key. Real credential validation is the user's check. This build saves the key but does not yet generate coaching. Next bounded work is coaching-generation planning/implementation including separate text-model selection, full-input bounds, fallback audio and lifecycle. Preserve all uncommitted files until the user requests commit/push.

## 13 Coaching generation core - September 18, 2026

The user confirmed successful API-key entry. Implemented internal complete current/previous archive loading and OpenAI count/text/Cedar speech adapters, with ownership/integrity checks, frozen references, explicit no-truncation, bounded input/output and single-attempt provider calls. See worker/COACHING_PLAN.md 1.7 for the model choice, boundaries and remaining integration. No public generation route, Android integration, personal-key calls, deployment or commit/push in this slice. The existing APK remains current; there is no new phone check yet.

Next bounded slice: durable coaching jobs and authenticated request/status/audio endpoints, with deletion/account/key races and uncertain paid outcomes covered before enabling phone finish requests. Then finish checkbox, audio sequencing, animation, fallback recordings and an APK for real end-to-end testing. Achievements and expanded statistics remain later.

Verification: TypeScript build and Wrangler deployment dry-run pass. All nine new D1/provider tests pass. Full suite passed 73 of 74 on the first run; an existing upload-boundary test failed with Miniflare/Undici ECONNRESET, then passed unchanged in a focused rerun. No assertion failure remained. git diff --check passes. Android was unchanged in this slice, so no APK rebuild or phone validation was claimed.

## 14 Coaching jobs and finish playback - September 18, 2026

The user authorized continued work while away. Implemented durable owner/run job reservation, frozen context references, bounded provider stages without paid retries, authenticated status/audio retrieval, and deletion cleanup. Development migration 0006 and Worker 88261b78-b67b-4993-b3e8-6c388c3c7aff are deployed. All 80 backend tests and 22 live smoke checks pass. See worker/COACHING_JOBS_CONTRACT.md.

Android now has a default-checked finish checkbox, persisted attempt selection, bounded sync-before-coaching, Cedar playback after the completion cue, service-owned audio with a dismissible animation, and two onboard encouragement recordings. Opt-out skips both generation and encouragement. Account/run changes and service teardown cancel pending playback. The normal build passed assembleDebug, lintDebug, testDebugUnitTest (58 tests) and assembleDebugAndroidTest. Seven focused emulator tests pass, including actual fallback recording playback completion and existing swipe/discard regressions. No real phone was installed and no personal key/private run was used for a paid test.

Build troubleshooting: generated D: build directories had ReadOnly attributes, causing Gradle's normal incremental cleanup to fail. A manual recursive cache deletion was blocked by automatic approval; a temporary output path built once, and cross-drive output failed KSP's same-root requirement. Clearing ReadOnly only from generated app/build directories resolved the problem; the final artifact comes from the ordinary build path. No source directories or project permissions were changed.

Current verified APK: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-18_12-43-46_EDT.apk (34516894 bytes). Install over the existing app. See android/PHONE_TEST_COACHING.md for selected, unchecked, offline and animation/audio checks. Real model access, recap quality and Cedar/headphone behavior remain phone acceptance. Next follow-up is coaching history/export integration and any real-device corrections; achievements and expanded statistics remain deferred. No commit/push.

Cleanup limitation: automatic approval also blocked the exact-file deletion of android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk after verification. It remains a generated test artifact, separate from the sole timestamped handoff APK. Do not mistake it for the user install. No alternative deletion method was used to bypass that restriction.

## 15 Desktop coaching history and complete export - September 18, 2026

User reports the phone coaching update tests OK; voice selection is explicitly deferred. The requested backup was completed as 0c1ad96 on origin/codex/account-sessions; no open PRs needed cleanup. Subsequent work is uncommitted unless separately requested.

Implemented read-only saved recap/audio in desktop run details and CSV v2 containing all retained coaching metadata and reconstructable WAV chunks. No generation or paid retry is available from the website. Ownership, deletion, integrity, credential exclusion, cancelled work and incomplete speech are checked. Existing run archives and the current APK are unchanged. See worker/COACHING_JOBS_CONTRACT.md 1.6 and worker/EXPORT_DELETION_PLAN.md 1.6.

All 86 automated tests passed. Browser fixture downloaded a 1,959,020-byte CSV with four runs, two coaching receipts and 88 audio chunks; independent CSV reconstruction verified all 1,440,044 WAV bytes by SHA-256. Browser playback, literal text rendering, no-coaching state and navigation cleanup were verified. An initial in-app browser tab crashed; a fresh tab completed the playback check. No personal key or run was used. Development deployment status is recorded in worker/STATUS.md.

Next user check: refresh the development website, open a run that generated coaching, replay its saved audio and export all runs. No new APK is needed. Next planned milestone is achievements planning (catalog, thresholds, time boundaries and deletion effects) before implementation; expanded statistics, charts and voice selection remain deferred.

## 16 Achievements implemented - September 18, 2026

The accepted named catalog and 21 holiday/event awards are implemented with automatic historical calculation, immediate persisted distance/calendar progress, finish-time performance checks and a dismissible celebration. Desktop Achievements processes all synced history; CSV v3 includes earned records. Source-run deletion recomputes dependent records, totals and streaks. See ACHIEVEMENTS_PLAN.md 1.8 for exact evidence restrictions and cross-device derivation.

Verification: 69 JVM tests, 93 Worker/browser tests, Android debug assembly/lint, 16 focused emulator tests and 25 live smoke checks pass. Development version e192834c-8396-41b4-82d8-e77273d2d972. Current APK: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-18_20-29-35_EDT.apk (34598826 bytes), 0.1.0-dev-achievements1. Upgrade in place. User phone acceptance remains separate; no personal API calls, phone install or commit/push.

NEXT: Section 9.5 end-of-run photos, overlays, save/share and public run pages. Then Health Connect, remaining deferred refinements and release verification. See REMAINING_WORK.md. Do not resume achievement expansion, statistics or voice selection ahead of photos without a new user decision.

## 17 End-of-run photos - September 19, 2026

Implemented TASKS 9.5: system camera/front-rear switching, image picker/skip, bounded orientation-aware image decoding, selectable time/distance/pace/actual-route overlays, preview/retake/keep, default-checked publication, Android document save and standard image/link sharing. Room v5 stores the kept JPEG and durable sync choice; temporary preview survives recreation. Cloud upload follows confirmed run sync, with atomic replacements and idempotent duplicate receipts. D1 migration 0007 stores one bounded image per owned run; deletion cascades and deletion markers revoke public access. Private website photo display and public run pages expose only the selected run; CSV v4 contains reconstructable, hashed image records. See PHOTOS_PLAN.md for limits and android/PHONE_TEST_PHOTOS.md for acceptance.

Verified: assembleDebug, lintDebug, 69 JVM tests, 99 full Worker/browser tests before the final concurrency fix, then all 20 focused photo/export tests after it. Nineteen distinct focused emulator tests pass (20 executions), including v4-to-v5 preservation/deletion, owner-scoped photo queue, stale-revision acknowledgments, bounded image rendering, Skip, and Keep with public unchecked plus Save/Share controls. Local browser displayed private-account photo/shared link, public map with preserved gaps at 390px, cleared account state on logout and exported four runs; independently reconstructed all 18,874 synthetic JPEG bytes from CSV. No real camera/gallery, physical phone, personal photo publication or API-key call was performed.

Development Worker 13864438-3308-4c93-95ea-0e47619f0b4e deployed; 29 live smoke checks pass. APK: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-19_06-36-20_EDT.apk, 34,730,466 bytes, version 0.1.0-dev-photos1, SHA256 8eea6249ff930124c7ac0229c58198eaea313ad8155b296675a2b4b12fd92d4c. Upgrade in place. No commit/push. User will test photos and achievements on the next run; Health Connect is the next implementation milestone, with voice/stats refinements still later.

## 18 Surgical coaching playback correction - September 19, 2026

User confirmed photos work but reported hearing repeated coaching while the saved recap described the new short indoor run. Read-only inspection of that run's stored WAV showed RIFF and data length placeholders 0xffffffff. A synthetic fixture with the same header and length reproduced Android MediaPlayer preparation failure; finalizing those two lengths made it prepare successfully. The existing playback error branch then explains repeated onboard fallback audio. The user was asked whether playback occurred on phone or website; no reply was received during this repair, so website-specific reproduction remains unconfirmed.

Changed only Android coaching download-to-file preparation: a bounded WAV helper finalizes streaming lengths without changing speech samples. Already finalized WAVs remain byte-identical; malformed/truncated chunks are rejected. One call site and a debug version label change; no photo/tracking/server/schema changes, no stored recording edits, and no paid generation. No commit/push.

Verification: assembleDebug, lintDebug, 72 JVM tests, and five focused emulator checks pass (WAV preparation, opt-out, fallback after completion cue/dismissal, photo Keep with private selection and photo Skip). The original streaming-header failure was separately reproduced before applying the fix. APK: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-19_08-20-22_EDT.apk, 34730478 bytes, version 0.1.0-dev-photos1-coachingfix1; SHA256 8376e1a573467896bbdd9ebeed3187a5470cc3b33cd5305c065f04f28bcfc854. Upgrade in place. Test a new short run with coaching checked and compare spoken text to its saved website recap; opening a prior finished run intentionally does not repeat coaching or billing.


## 19 Health Connect and comprehensive acceptance plan - September 19, 2026

Implemented TASKS 9.6 with stable AndroidX Health Connect 1.1.0: explicit current-account/local connection, exercise/distance write permissions only, running/treadmill sessions with recorded pauses and frozen-zone offsets, stable client IDs, background export independent of internet and durable deletion cleanup. Room v6 preserves runs/photos/sync and retains cleanup intent after source deletion. Permission loss leaves work pending; reconnect retries existing exports without duplicates. Invalid zero/backwards timing is visibly blocked without changing source records. No health imports, GPS route export or unrelated health data access. See HEALTH_CONNECT_PLAN.md.

Verified assembleDebug, lintDebug, 75 JVM tests and ten focused emulator checks: actual Health Connect insertion twice returned identical record IDs, repeated deletion succeeded, partial-write retries/deletion races passed, five schema migrations passed and the prior coaching WAV preparation passed. The actual provider check used synthetic records on the owned emulator and deleted them. Eight additional coaching/run-screen regression checks passed, including photo Keep/Skip and finish/discard controls (18 focused emulator checks total). Real-phone permission UI, health display and hardware acceptance remain pending. No physical phone install, backend deployment, paid API call or commit/push.

APK: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-19_11-44-08_EDT.apk, 39962476 bytes, version 0.1.0-dev-health1, SHA256 f30c89b871eeab13d45935b497f733d69faac705962ff4ddd862ca08d8849955. Upgrade in place; gear → Health Connect → Connect / retry export, grant both requested write permissions. Start with HC-01–04 and CO-01 in COMPREHENSIVE_TEST_PLAN.md.

Next phase is comprehensive acceptance, with bugs/quibbles/enhancements recorded in FINAL_PASS_ISSUES.md, followed by a prioritized, bounded stabilization pass. Deferred voice/statistics/announcement settings remain separately tracked; production release configuration and authorization are still outstanding.


## 20 Multi-day acceptance handoff - September 19, 2026

the owner will take several days for testing and has no rate-limit resets remaining. COMPREHENSIVE_TEST_PLAN.md sections 1.3–1.4 now provide four manageable sessions and a plain-language batched reporting template. FINAL_PASS_ISSUES.md tracks each session as not run; advanced recovery/account/device cases remain explicitly pending. No application changes, rebuild, deployment or commit/push in this documentation-only follow-up. Keep health1 as the stable test build, collect observations, then prioritize surgical fixes and separately approved enhancements.
