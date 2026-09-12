# WAYiRUN — Implementation Plan

Version: 0.2  
Status: Milestones 0–2 complete; durable local tracking prototype is next  
FILE: <repository-root>\TASKS.md (NEW)

## 1 Current state

The requirements baseline includes the user's accepted active-time, average-pace, unit-based splits, non-stopping goals, AI opt-out, and source-independent linked-pause behavior. Architecture and the logical data model are documented. The Android shell displays WAYiRUN. A pure Kotlin run controller passes 27 deterministic unit tests; the debug build and lint pass. The Git repository is rooted directly in `<repository-root>`. Device tracking, databases, cloud resources, and deployment are not implemented.

Read [REQUIREMENTS.md](REQUIREMENTS.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DATA_MODEL.md](DATA_MODEL.md), and [the then-current working guide](User%20Preferences%20LLM%20Guide.md) before work. Product requirements take precedence over proposed technical details.

## 2 Execution discipline

### 2.1 Milestone rules

Complete one milestone at a time. Review its scope and entry decisions, implement only that scope, run its meaningful checks, and report the result. Never claim device behavior was verified by a successful compilation. Keep each completed milestone independently committable; do not combine deployment with feature development.

### 2.2 Repository ownership

The user created the repository. Its existing `.git` directory and `.gitattributes` were relocated from the redundant `RunningApp` subfolder to this project root on 2026-09-12, preserving HEAD and origin. Do not create another repository or change the remote. The user will commit and push this baseline. At task entry inspect Git status and preserve pending user changes; do not treat an uncommitted baseline as disposable. Committing and pushing require explicit delegation.

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

Add the minimal run screens, Room records, foreground tracking, GPS/step adapters, and state/completion cues. Resolve partial-split presentation, stride configuration, missing-sensor/permission behavior, and restart presentation before those paths ship. Use no invented personal stride calibration. Display fallback status honestly while cloud services are not implemented.

Do not include music linkage, AI, photos, public pages, cloud history, or achievement UI in this milestone. Preserve the full requested product in the requirements; this is a staged build, not removal of those features.

### 6.3 Verification and exit

Verify atomic finish and persisted summary loading. On a real phone, exercise screen-off running, pause movement, indoor mode without location access, missing GPS with working steps, later GPS availability, airplane mode, and activity recreation. Inspect recorded segments for gaps and duplicate distance. A short measured route and indoor step check must be compared with recorded output before claiming tracking is reliable. Report unresolved device-specific limitations.

## 7 Milestone 4 — YouTube Music and linked controls

### 7.1 Outcome

Pausing either side pauses both, and the requested headphone resume works without duplicate cues or restart of finished runs.

### 7.2 Scope and entry decisions

Add media-session access and the required user-enabled notification listener. Decide playlist entry and behavior when YouTube Music is absent or has no controllable session. Verify playlist launch separately from transport control. Do not substitute undocumented YouTube APIs.

### 7.3 Verification and exit

Test pauses from the app, headphones, YouTube Music, Android controls, and interruptions; test repeated notifications and headphone resume. Check music while run cues play, screen off, and after finish. If music actually pauses during speech, both must pause under the accepted rule. Report unsupported player behavior rather than silently changing the requirement.

## 8 Milestone 5 — Accounts and cloud run storage

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

### 9.2 Export and deletion

Define the complete CSV representation, deletion reconciliation, marker retention, and Health Connect deletion policy. Implement one-button export and actual data/object deletion. Verify public access is removed, cleanup retries complete, and a stale phone cannot resurrect a deleted run.

### 9.3 AI coaching

Add encrypted per-user OpenAI keys, completed-run comparisons, voice, checkbox behavior, and onboard fallback recordings. Resolve model/voice, context limits, timeout/unknown-outcome handling, and animation behavior. Test two-account credential isolation, invalid keys, offline failure, opt-out, and duplicate requests. No shared owner key is permitted.

### 9.4 Achievements and celebrations

Agree the catalog, thresholds, repeatability, calendar/time-zone rules, deletion effects, and multiple-award presentation. Implement immediate awards with finish-time reveals and persistent history. Verify offline and cross-device reconciliation.

### 9.5 Photos and public run pages

Implement camera/picker/skip, selectable overlays, preview/retake/keep, Android save/share, private storage, and checked-by-default publication. Resolve offline publication, existing-photo retake behavior, and publication without a photo before those paths. Test public-field allowlists, unchecked privacy, and deletion of public assets.

### 9.6 Health Connect

Export completed runs with stable IDs and appropriate permissions. Test availability, revoked access, duplicate retries, and the approved deletion policy. No import or watch app is included.

### 9.7 Product verification and release preparation

Complete requirements Section 16 on actual target devices, including units, dark mode, announcement choices, all fallback paths, and data ownership. Finish release identity, signing, and external-service configuration; inspect Cloudflare usage against the user's account limits. Release deployment is a separate milestone after validation, not an incidental step during development.

## 10 Next execution brief

Next is Milestone 3 in this file, in `<repository-root>`. Begin with planning only: re-read the current requirements, project instructions, and working guide; inspect Git status and preserve the existing baseline and controller. Resolve the entry decisions in Section 6.2 before implementing dependent paths. Propose a bounded plan for the durable local tracking prototype and obtain execution authorization. Reuse the tested controller and its calculation contract in Section 5.5; do not restart completed milestones. No music linkage, AI, photos, publication, cloud history, authentication, or deployment belongs to Milestone 3. Run the controller tests, debug build, and lint, then separately report the required actual-device checks. Do not commit, push, or change the remote without explicit delegation.
