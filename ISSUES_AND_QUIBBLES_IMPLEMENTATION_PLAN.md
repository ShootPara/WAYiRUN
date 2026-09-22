# 1 WAYiRUN issues and quibbles implementation plan

Version: 0.1
Status: Planning artifact only; no feature implementation in this document
Date: 2026-09-22
FILE: <repository-root>\ISSUES_AND_QUIBBLES_IMPLEMENTATION_PLAN.md (NEW)

## 1.1 Purpose

This plan converts the current Wayirun Issues & Quibbles list into small, committable Codex execution milestones. It is based on the pasted conversation, the `Wayirun Issue List` ChatGPT conversation, repository documents, and inspection of the current code.

This is not an implementation pass. The first execution milestone must update the product docs so the new decisions supersede older requirements cleanly before code changes begin.

## 1.2 Decisions to preserve

- Remove Leaflet/OpenStreetMap basemap behavior entirely.
- Do not integrate Mapbox or another basemap provider in this pass.
- Preserve GPS route data independently from all map rendering.
- Render route graphics directly from stored GPS points.
- Remove the behavior where pausing music pauses the run.
- Add auto-pause when stopped, based on whichever motion signals are available.
- Implement milestone announcements and continue them beyond a selected goal.
- Add deterministic test/anomaly classification before AI feedback.
- Move the shared-photo route noodle to the lower-right and remove its dark background box.
- Add weather emoji plus temperature in F and C to photo overlays, with a toggle.
- Keep weather attribution out of primary running UI; place it unobtrusively in settings and on public/shared pages.
- Add shared-run controls for photo visibility and unsharing.
- Add short run-share links and copy/share the short version.
- Fix the last-completed-run reopening on app launch and app focus return.
- Make the New Run Outdoor, Indoor, None, Time, and Distance controls square with appropriate emojis.
- Put Outdoor/Indoor status on the left and Online/Fallback status on the right.
- Use green for Outdoor, blue for Indoor, green for Online, and red for Fallback.
- Replace exact coordinate text on public pages with the matching city and state when a recorded route location exists.

## 1.3 Current repo findings

The current working tree already contains substantial uncommitted implementation work after commit `0c1ad96` on `codex/account-sessions`: achievements, photos/public pages, Health Connect, coaching history/export, and updated plans/tests. Preserve it.

Map code is currently provider-dependent in the web layer:

- `worker/src/browser.ts` serves `/map.js`, `/leaflet.js`, and `/leaflet.css`, and its CSP allows `https://tile.openstreetmap.org`.
- `worker/web/map.browserjs` loads local Leaflet and requests OpenStreetMap raster tiles.
- `worker/web/app.browserjs` imports `showRouteMap` and renders a map panel for private run details.
- `worker/src/photos.ts` public shared-run page includes Leaflet CSS, a map container, a Fit Route button, and CSP permission for OpenStreetMap tiles.
- `worker/web/public-photo.browserjs` imports `showRouteMap` and prints an exact start latitude/longitude.
- `worker/web/route.browserjs` already contains useful provider-independent route geometry validation and segmentation. Keep this concept.

Photo overlay code exists on Android:

- `android/app/src/debug/java/com/example/runningapp/photos/PhotoFlow.kt` renders time/distance/pace and route overlays.
- The current route overlay draws near the lower-right, but it also draws a dark rounded rectangle behind the route. Remove that box.
- Photo overlay options are serialized as `time`, `distance`, `pace`, and `route`; weather requires schema/API expansion.

Music-linked run pause still exists:

- `android/app/src/debug/java/com/example/runningapp/tracking/MusicLinkPolicy.kt` converts player pause while the run is running into `MusicAction.PAUSE_RUN`.
- `TrackingService.performMusic()` converts `PAUSE_RUN` and `RESUME_RUN` actions into run commands.
- `WayirunApp.kt` still displays music-control setup text and a YouTube Music control-access button in settings.

Last-completed-run reopening is likely caused by service loading behavior:

- `TrackingService.load()` falls back from `repository.active()` to `RunDatabase.get(this).runs().latestVisible(selected)`.
- Opening the app or sending `OPEN` can therefore restore a finished run instead of showing the normal New Run screen.

Milestone-announcement infrastructure is incomplete:

- `RunCueQueue` ignores `RunEventType.SPLIT_COMPLETED`.
- `RunSettings` does not currently include a user-selected announcement interval/on-off setting.
- Existing split events are full-distance split events, not the requested 5/10-minute or 0.5/1-mile announcement schedule.

Public sharing currently conflates photo keep and publication:

- Android photo keep sends `X-Photo-Public` and stores one `publicUrl`.
- Worker `run_photos.public_token` is either present or null.
- There is no separate "shared run exists", "display photo on shared run", or owner-controlled unshare state.
- The public URL is long because it uses the 64-hex public token directly.

AI coaching prompt currently has no anomaly quality field:

- `worker/src/coaching-context.ts` verifies and serializes the current run plus previous run.
- `worker/src/coaching-provider.ts` instructions tell the model to avoid unsupported claims, but do not provide deterministic run-quality flags.

## 1.4 Execution guardrails

Work one milestone at a time. Each milestone must be independently buildable and committable.

Do not combine Android UI polish, Worker schema changes, AI prompt changes, and tracking behavior in one commit unless explicitly directed. Do not deploy production. Do not install on a phone unless that check is the active task.

After any Android source or schema change, run from `android/`:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

After Worker or web changes, run from `worker/`:

```powershell
npm.cmd test
```

Use focused emulator/browser checks where a milestone changes UI, Room migrations, photo rendering, Health Connect, or public pages. A compiled APK is not evidence of real sensor/audio behavior.

## 2 Milestone 0 - Commit current baseline

### 2.1 Goal

Preserve the currently implemented achievements/photos/Health Connect/coaching-history state before starting new issue-list work.

### 2.2 Steps

1. Inspect `git status --short --branch`.
2. Review staged/untracked files for generated artifacts or secrets.
3. Run `git diff --check`.
4. Run the normal Android and Worker verification commands if time and environment permit.
5. Commit all current source, docs, tests, migrations, and schema files that represent implemented work.
6. Push `codex/account-sessions` to origin.

### 2.3 Acceptance

- No secrets, local SDK paths, build outputs, or transient APK/test output files are committed.
- Existing uncommitted implementation work is preserved in Git.
- The next issue-list work starts from a clean or intentionally documented working tree.

## 3 Milestone 1 - Document alignment

### 3.1 Goal

Update source-of-truth planning docs so the new user decisions are not contradicted by older map/music/publication requirements.

### 3.2 Files

- `REQUIREMENTS.md`
- `TASKS.md`
- `REMAINING_WORK.md`
- `FINAL_PASS_ISSUES.md`
- This plan, if implementation order changes during review

### 3.3 Required changes

Record that route-only graphics replace basemap maps for this pass. Public and private pages should show route graphics from stored GPS data, not Leaflet/OpenStreetMap/Mapbox. GPS data remains stored.

Record that music-player pause no longer pauses the run. Remove automatic media-session transport control in both directions. Preserve only opening the saved YouTube playlist and ducking audio during WAYiRUN cues.

Record that sharing is explicit: a run remains private until the user deliberately attempts to share run results, which creates/enables a shared run link. Photo display on the shared page is a separate owner-controlled preference.

Add the new stabilization/features as planned follow-ups: route-only rendering, link shortening, share controls, milestone announcements, weather overlay, auto-pause, anomaly-aware coaching, New Run UI polish, status indicator colors, and last-completed-run launch fix.

### 3.4 Verification

- Decimal headings remain valid.
- No duplicate or conflicting map/music/publication rules remain.
- `rg -n "OpenStreetMap|Leaflet|Mapbox|pausing music pauses|music pause.*run|public.*default"` finds only historical notes or the new superseding decisions.

## 4 Milestone 2 - Stop opening the last completed run

### 4.1 Goal

Opening WAYiRUN or returning focus to WAYiRUN must show the normal pre-run/New Run screen unless there is an unfinished active or paused run to recover.

### 4.2 Files and components

- `android/app/src/debug/java/com/example/runningapp/tracking/TrackingService.kt`
- `android/app/src/debug/java/com/example/runningapp/storage/RunDatabase.kt`
- `android/app/src/debug/java/com/example/runningapp/ui/WayirunApp.kt`
- `android/app/src/androidTest/java/com/example/runningapp/ui/RunScreenTest.kt`
- `android/app/src/androidTest/java/com/example/runningapp/storage/RunDatabaseTest.kt`

### 4.3 Required behavior

`TrackingService.load()` should load `active()` for unfinished recovery. It must not fall back to `latestVisible()` for normal `OPEN`.

Finished runs remain saved and accessible through summary immediately after finishing, through the website, and through any future phone history path. Starting a new run must not delete the last completed run.

`NEW` should clear the finished in-memory controller and return to setup. App reopen after a finished run should also show setup.

### 4.4 Acceptance

- Reopen after a finished run shows setup, not the last summary.
- Reopen during a paused or interrupted unfinished run still shows the recovery/paused run.
- Account switching does not reveal another account's finished summary.
- Existing discard behavior still deletes only the selected finished run.

### 4.5 Verification

Add focused storage/service/UI tests for finished-vs-active load selection, then run Android unit/build/lint and focused emulator UI tests.

## 5 Milestone 3 - Remove basemaps and render route-only graphics

### 5.1 Goal

Remove the broken Leaflet/OpenStreetMap basemap dependency and replace private/public run maps with provider-independent route graphics.

### 5.2 Files and components

- `worker/src/browser.ts`
- `worker/src/photos.ts`
- `worker/web/app.browserjs`
- `worker/web/public-photo.browserjs`
- `worker/web/map.browserjs`
- `worker/web/route.browserjs`
- `worker/web/index.html`
- `worker/web/style.css`
- `worker/scripts/serve-map-fixture.mjs`
- `worker/test/browser.test.mjs`
- `worker/test/photos.test.mjs`
- `worker/web/vendor/*` if no longer referenced

### 5.3 Required behavior

Do not request tiles from `tile.openstreetmap.org` or any other basemap provider. Remove Leaflet script/style serving and CSP allowances if no longer needed.

Keep `prepareRoute()` or an equivalent provider-independent validator. Render route graphics as inline SVG or Canvas using stored GPS points and existing segment boundaries. Preserve gaps across pauses, missing GPS, and step-only intervals.

Private desktop run details should show a route panel when GPS exists and the existing honest no-route message for indoor/no-GPS runs.

Public shared-run pages should show the route graphic if GPS exists, but must not print exact starting latitude/longitude. If a GPS route exists, display the matching city and state instead of coordinates. If reverse geocoding fails or no route exists, use labels such as "Outdoor route recorded" or "No GPS route recorded."

Fit the route to the container. Show start and finish markers. Do not invent streets, parks, labels, or locations.

### 5.4 Acceptance

- `rg -n "tile.openstreetmap|Leaflet|leaflet|L\\.map|/leaflet"` finds no active runtime dependencies.
- Private and public pages render routes from stored GPS data without external map requests.
- Pauses/gaps remain visually disconnected.
- Indoor/no-GPS runs remain map-free and honest.
- Public page no longer exposes exact coordinate text and shows city/state when available.

### 5.5 Verification

Run Worker tests and browser fixture checks at desktop and 390px mobile width. Use network inspection or code checks to confirm no third-party map requests occur.

## 6 Milestone 4 - Shared-run controls and short links

### 6.1 Goal

Make run sharing explicit and owner-controlled, add short share links, and let the owner toggle photo visibility separately from sharing.

### 6.2 Files and components

- `worker/migrations/0008_public_runs.sql` or next numbered migration
- `worker/src/photos.ts`
- `worker/src/browser.ts`
- `worker/web/app.browserjs`
- `worker/web/public-photo.browserjs`
- `worker/web/index.html`
- `worker/web/style.css`
- `worker/test/photos.test.mjs`
- `worker/test/browser.test.mjs`
- `android/app/src/debug/java/com/example/runningapp/photos/PhotoFlow.kt`
- `android/app/src/debug/java/com/example/runningapp/photos/PhotoSync.kt`
- `android/app/src/debug/java/com/example/runningapp/storage/RunDatabase.kt`
- Room schema JSON for the next version if Android persistence changes

### 6.3 Required behavior

Introduce a server-side publication state separate from the existence of a kept photo. Default new runs/photos to private until the user explicitly attempts to share the run results and a link is created.

Add owner-authenticated controls:

- Create or enable shared-run link on any intentional run-result sharing action.
- Unshare a run, disabling public access through the shared link.
- Toggle "Display photo with shared run" without unintentionally changing whether the run is shared.

Add a short internal share path, default `/r/{token}`, for run-share links only. The short token maps to the public run token/server-side publication record. Do not use an external shortening service.

When Android shares or copies a run link, use the short URL. If a run has not synced yet, show pending sync honestly and do not invent a link.

### 6.4 Acceptance

- Private is the default for new photo keeps after this change.
- Creating a share link is a deliberate action.
- Unsharing disables both long and short public access.
- Photo display can be toggled off while the run page remains shareable.
- Toggling photo display does not delete the kept private photo.
- Link sharing uses the short URL.
- Deletion still revokes public access and removes associated public/photo state.

### 6.5 Verification

Add Worker tests for create/share/unshare/photo-visible/short-link redirect/access boundaries. Add Android tests for pending/shared/private UI states where practical. Run Worker and Android checks.

## 7 Milestone 5 - Photo overlay route noodle and weather

### 7.1 Goal

Improve the rendered share image: route noodle lower-right with no extra dark rectangle, plus optional weather overlay.

### 7.2 Files and components

- `android/app/src/debug/java/com/example/runningapp/photos/PhotoFlow.kt`
- `android/app/src/androidTest/java/com/example/runningapp/photos/PhotoRenderTest.kt`
- `android/app/src/debug/java/com/example/runningapp/photos/PhotoSync.kt`
- `android/app/src/debug/java/com/example/runningapp/storage/RunDatabase.kt`
- `worker/src/photos.ts`
- `worker/migrations/0008_public_runs.sql` or a separate next migration if needed
- `worker/test/photos.test.mjs`
- `worker/web/export.browserjs` if CSV photo metadata includes weather

### 7.3 Weather default

Default implementation: use Open-Meteo as the first weather source because it has no required API key for non-commercial/free usage and historical weather support. At execution time, re-check current terms. The app must attribute the data unobtrusively: small print in settings for the Android app and out-of-the-way text on public/shared pages. Do not put attribution in the main running UI or inside the photo overlay unless required by updated provider terms.

To reduce location exposure, query with rounded coordinates sufficient for weather, not full-precision route points. Use the first recorded GPS point and run start time for outdoor GPS runs. For indoor/no-GPS runs, leave weather unavailable unless the user later chooses a manual location feature.

Store the resolved weather code/emoji, temperature F/C, source, attribution label/URL, approximate query coordinates, and observation time with the kept photo/public metadata so the overlay and public page are stable. Do not refetch every time the photo is viewed.

### 7.4 Required behavior

Route noodle:

- Draw in the lower-right of the photo overlay area.
- Remove the route-specific dark rounded rectangle.
- Keep the existing lower stats band.
- Preserve segment gaps and do not draw across pauses or missing GPS.

Weather:

- Add a Weather checkbox next to Time/Distance/Pace/Route.
- Show a small weather emoji with small temperature text underneath in both F and C.
- If weather is unavailable, disable or hide the checkbox with short non-alarming text.
- Weather failure must not block keeping, saving, sharing, syncing, or publishing the photo.
- Weather attribution must be present but visually out of the way.

### 7.5 Acceptance

- Route overlay has no separate dark box.
- Weather can be toggled independently.
- Indoor/no-GPS runs do not fabricate weather from route data.
- Offline/weather-provider failure keeps the photo flow usable.
- Export/public metadata either includes stable weather fields or clearly omits them.

### 7.6 Verification

Add/extend photo rendering tests to inspect overlay options, route placement, absence of route-background rectangle, weather on/off, and unavailable weather. Run Android checks and Worker photo/export tests.

## 8 Milestone 6 - Milestone announcements

### 8.1 Goal

Implement user-selectable run announcements at 5 minutes, 10 minutes, 0.5 mile, or 1 mile, continuing beyond a selected goal.

### 8.2 Files and components

- `android/app/src/main/java/com/example/runningapp/domain/RunModels.kt`
- `android/app/src/main/java/com/example/runningapp/domain/RunController.kt`
- `android/app/src/debug/java/com/example/runningapp/tracking/RunCueQueue.kt`
- `android/app/src/debug/java/com/example/runningapp/ui/WayirunApp.kt`
- `android/app/src/debug/java/com/example/runningapp/storage/RunDatabase.kt`
- Room migration/schema if `RunSettings` persistence changes in a way older checkpoints need compatibility handling
- `android/app/src/test/java/com/example/runningapp/domain/RunControllerTest.kt`
- `android/app/src/testDebug/java/com/example/runningapp/tracking/RunCueQueueTest.kt`

### 8.3 Required behavior

Add announcement settings behind the gear, not on the main pre-run screen:

- Off/on switch.
- Exactly one interval choice: 5 minutes, 10 minutes, 0.5 mile, or 1 mile.

Snapshot the selected announcement settings at run start. Do not let later settings edits change an active run.

Generate announcement events whenever active time or distance crosses the configured interval. Continue after the selected goal is reached. Do not duplicate an announcement after pause/resume, recovery, process restart, or source handoff.

Announcement content must be in this order: elapsed time, distance, average pace. Use selected units honestly. Unavailable pace must not be spoken as zero or infinity.

### 8.4 Acceptance

- Time intervals fire at 5- or 10-minute active-time boundaries.
- Distance intervals fire at 0.5- or 1-mile boundaries by default; see question 11.1 for kilometer behavior.
- Goal reached still fires once and does not stop the run.
- Announcements continue beyond the goal.
- Recovery does not replay already emitted announcements.
- Off means no interval announcements while state/goal/finish cues still work.

### 8.5 Verification

Add deterministic controller tests for multiple interval crossings, pause exclusion, resume, goal-plus-post-goal announcements, source gaps, and recovery. Add cue-text tests. Run Android checks.

## 9 Milestone 7 - Remove music-pause-controls-run

### 9.1 Goal

Remove automatic behavior where music pause/resume controls pause/resume the run, and remove associated settings/UI copy.

### 9.2 Files and components

- `android/app/src/debug/java/com/example/runningapp/tracking/MusicLinkPolicy.kt`
- `android/app/src/debug/java/com/example/runningapp/tracking/MusicSessionAdapter.kt`
- `android/app/src/debug/java/com/example/runningapp/tracking/TrackingService.kt`
- `android/app/src/debug/java/com/example/runningapp/ui/WayirunApp.kt`
- `android/app/src/testDebug/java/com/example/runningapp/tracking/MusicLinkPolicyTest.kt`
- `android/app/src/androidTest/java/com/example/runningapp/tracking/MusicSessionAdapterTest.kt`
- `REQUIREMENTS.md`

### 9.3 Required behavior

Player pause/resume events must not issue run pause/resume commands.

Default: remove all automatic media-session transport control, including app-pause-player/app-resume-player, unless the user explicitly keeps that direction. Preserve:

- Open YouTube playlist button.
- Audio ducking for WAYiRUN cues.
- Tracking independence when music is off/unavailable.

Remove settings text/buttons that imply WAYiRUN needs YouTube Music control access. Do not request notification-listener access only for a removed feature.

### 9.4 Acceptance

- Pausing music never pauses the run.
- Resuming music never resumes a paused run.
- Pausing/resuming the run does not require music-control permission.
- The pre-run playlist open flow still works.
- WAYiRUN cues still duck other audio when Android honors audio focus.

### 9.5 Verification

Replace or remove `MusicLinkPolicyTest` assertions that encode the old feature. Add tests proving player events are ignored for run state. Run Android checks.

## 10 Milestone 8 - Auto-pause when stopped

### 10.1 Goal

Add an optional setting that automatically pauses a run after the runner stops moving, and resumes after sustained movement returns.

### 10.2 Files and components

- `android/app/src/main/java/com/example/runningapp/domain/RunModels.kt`
- `android/app/src/main/java/com/example/runningapp/domain/RunController.kt`
- New pure Kotlin auto-pause policy file under `android/app/src/main/java/com/example/runningapp/domain/`
- `android/app/src/main/java/com/example/runningapp/domain/TrackingInput.kt`
- `android/app/src/debug/java/com/example/runningapp/tracking/TrackingService.kt`
- `android/app/src/debug/java/com/example/runningapp/tracking/SensorAdapters.kt`
- `android/app/src/debug/java/com/example/runningapp/ui/WayirunApp.kt`
- `android/app/src/debug/java/com/example/runningapp/storage/RunDatabase.kt`
- Unit and instrumentation tests for the policy and service wiring

### 10.3 Default policy

Treat auto-pause as sensor-availability based, not indoor/outdoor based.

Stationary:

- Preferred threshold: about 5 seconds.
- If steps and GPS are both reliable, require no detected steps plus near-zero GPS movement/speed.
- If only one reliable signal is available, use that signal alone.
- Ignore a single stray step or GPS wobble.

Resume:

- Resume after about 2 seconds of sustained movement/steps.
- Resume only if the current pause was auto-paused, not manually paused.

Unavailable:

- If neither GPS nor step/motion data is available, auto-pause is unavailable and must not invent distance or block the run.

### 10.4 Required behavior

Add an Auto-pause setting behind the gear. It defaults on for new and existing installs, and the user can turn it off for testing or preference. Snapshot it at run start.

Represent automatic pause state distinctly enough that manual pause remains manual and cannot be auto-resumed.

Use existing controller pause/resume semantics so active time and distance freeze correctly. Add separate events/copy only if needed to avoid confusing manual pause history.

Do not use network availability as a movement signal.

### 10.5 Acceptance

- Indoor with steps: stops after about 5 seconds without steps and resumes after sustained steps.
- Outdoor with GPS only: stops after sustained near-zero GPS movement and resumes after movement.
- Outdoor with both: requires both signals to agree unless one becomes unreliable/unavailable.
- Manual pause is never auto-resumed.
- GPS jitter and one stray step do not cause repeated pause/resume flapping.
- No sensor source means auto-pause unavailable, not a crash.

### 10.6 Verification

Add pure policy tests for stationary/movement thresholds, both-signal agreement, single-signal fallback, jitter, and manual pause protection. Add service/controller tests for saved intervals and recovery. Real-phone GPS/step acceptance remains required before release.

## 11 Milestone 9 - Anomaly-aware AI feedback

### 11.1 Goal

Add a deterministic run-quality classifier before AI coaching so obvious test runs, vehicle-like recordings, and GPS anomalies are acknowledged instead of praised as normal performance.

### 11.2 Files and components

- `worker/src/coaching-context.ts`
- `worker/src/coaching-provider.ts`
- New `worker/src/run-quality.ts`
- `worker/test/coaching-jobs.test.mjs`
- `worker/test/coaching.test.mjs` or new focused test file
- `worker/COACHING_JOBS_CONTRACT.md`
- `worker/COACHING_PLAN.md`

### 11.3 Default classification rules

Flag likely test/incomplete run when:

- Active duration is under 90 seconds, or
- Distance is under 0.05 miles / 80 meters.

Flag likely vehicle/non-running when:

- Sustained pace is faster than 3:30 per mile for at least 20-30 seconds, or
- Sustained GPS-derived speed exceeds about 8 m/s / 18 mph.

Flag GPS anomaly when:

- A distance jump occurs without plausible elapsed active time or matching route/measurement progression.

The classifier should emit machine-readable fields such as `quality.label`, `quality.reasons`, and `quality.metrics`. The model should receive those fields as data, not instructions.

### 11.4 Required behavior

Add the classification to the current-run coaching input. Do not classify the previous run as the main subject unless useful for avoiding misleading comparisons.

Update `COACHING_INSTRUCTIONS` so the AI acknowledges anomalies plainly and avoids normal performance praise when the run is likely a test, vehicle recording, or GPS anomaly.

Do not block coaching solely because a run is suspicious. The user asked the AI to recognize the edge case, not skip feedback.

### 11.5 Acceptance

- One-minute/near-zero runs produce a likely-test classification.
- Vehicle-like samples produce a likely-vehicle classification.
- GPS jumps produce a GPS-anomaly classification.
- Normal plausible runs remain normal.
- Provider prompt tests prove the quality field is present and instructions prevent "amazing pace" style praise for suspicious runs.

### 11.6 Verification

Run Worker tests. Add provider-stub tests that inspect request JSON/instructions without making paid API calls.

## 12 Milestone 10 - New Run screen controls and status indicators

### 12.1 Goal

Apply the requested New Run visual polish without expanding the main screen.

### 12.2 Files and components

- `android/app/src/debug/java/com/example/runningapp/ui/WayirunApp.kt`
- `android/app/src/androidTest/java/com/example/runningapp/ui/RunScreenTest.kt`
- Existing Compose UI tests for settings/setup

### 12.3 Required behavior

Replace pre-run `FilterChip` rows for Outdoor/Indoor and None/Time/Distance with square controls that include appropriate emojis. Keep controls readable and stable at phone width and large text.

Keep labels sparse; no new explanatory setup panel.

Move status indicators into a left/right row:

- Left: Outdoor in green or Indoor in blue.
- Right: Online in green or Fallback in red.

Active-run status should remain readable. Do not introduce clutter during a run.

### 12.4 Acceptance

- Outdoor, Indoor, None, Time, and Distance controls are square and tappable.
- Text/emoji fit without clipping at normal and large font settings.
- Indicators are left/right and use the requested colors.
- Gear/settings behavior and autosave remain unchanged.
- Main screen still has profile, selectors, Open playlist, indicators, and START RUNNING only.

### 12.5 Verification

Add/adjust Compose tests for control selection, persistence, indicator colors/tags, and layout survival. Use screenshot/manual inspection on emulator and phone before release.

## 13 Milestone 11 - Final stabilization and acceptance update

### 13.1 Goal

Consolidate verification evidence and prepare a new test APK or web deployment only after the bounded milestones are complete.

### 13.2 Files

- `TASKS.md`
- `REMAINING_WORK.md`
- `FINAL_PASS_ISSUES.md`
- `COMPREHENSIVE_TEST_PLAN.md`
- Feature-specific phone test docs if needed

### 13.3 Required behavior

Record what changed, what was verified automatically, what needs real-phone acceptance, and what remains deferred.

Do not claim real sensor, weather, audio, or public-sharing behavior passed until it was tested on the relevant device/browser.

### 13.4 Verification

Run full Android/Worker checks. Build one timestamped handoff APK if Android changed and remove older handoff APKs according to project convention.

## 14 Suggested execution order

1. Milestone 0: Commit/push current baseline.
2. Milestone 1: Document alignment.
3. Milestone 2: Stop opening last completed run.
4. Milestone 3: Remove basemaps and render route-only graphics.
5. Milestone 7: Remove music-pause-controls-run.
6. Milestone 10: New Run UI polish/status indicators.
7. Milestone 6: Milestone announcements.
8. Milestone 8: Auto-pause.
9. Milestone 9: Anomaly-aware AI feedback.
10. Milestone 4: Shared-run controls and short links.
11. Milestone 5: Photo route/weather overlay.
12. Milestone 11: Final stabilization.

The ordering front-loads the likely bugs and removals before larger schema/API work. Public sharing and weather are later because they require more product precision and migrations.

## 15 Accepted answers and remaining defaults

### 15.1 Music controls

Decision: WAYiRUN removes all automatic media-session control.

Implementation default: Keep only Open YouTube playlist and audio ducking during WAYiRUN cues. This eliminates notification-listener setup and the fragile Nike-style linkage.

### 15.2 Kilometer announcements

Decision: Distance-announcement choices use the selected unit.

Implementation default: 0.5 mile and 1 mile when miles are selected; 0.5 km and 1 km when kilometers are selected.

### 15.3 Auto-pause cues

Decision: Auto-pause/resume should speak distinct cues.

Implementation default: Use "Auto-paused" and "Resumed." Do not use the manual "Run paused" wording for auto-pause.

### 15.4 Auto-pause default setting

Decision: Auto-pause defaults on.

Implementation default: Enable it by default for new and existing installs, and keep the settings toggle easy to turn off for testing.

### 15.5 Weather provider and privacy

Decision: The Worker may query weather using rounded start-location coordinates and run time.

Implementation default: Use Open-Meteo after re-checking current terms at execution, round coordinates before querying, store stable weather metadata with the photo, and show attribution in small print in settings and unobtrusively on public/shared pages. If no GPS exists, weather is unavailable.

### 15.6 Weather for existing photos

Decision: Existing kept photos do not get weather retroactively.

Implementation default: Apply weather only when creating or replacing a run photo after the feature ships.

### 15.7 Public sharing default

Decision: Replace the current default-checked "Make this run public" behavior.

Implementation default: New runs/photos stay private. Any intentional attempt to share run results creates/enables a public shared-run link because that action is deliberate.

### 15.8 Photo visibility on shared pages

Decision: If a run is shared but photo display is off, the public page still shows stats/splits/route.

Implementation default: Keep the public run page available, but hide the photo and any photo-derived image.

### 15.9 Short link length

Decision: Use internal short links.

Implementation default: Use a collision-checked 10-character base62 token under `/r/{token}`. It is short enough to share and large enough for this app.

### 15.10 AI anomaly thresholds

Decision: The default suspicious-run thresholds are acceptable to start.

Implementation default: Under 90 seconds or under 0.05 miles for likely test/incomplete, faster than 3:30/mile sustained or over 18 mph GPS speed for likely vehicle/non-running, and obvious distance/time jumps for GPS anomaly. Adjust after seeing real examples.

### 15.11 Public route location

Decision: Public pages must not show exact coordinate text.

Implementation default: Replace coordinates with city and state matching the recorded route location. Keep exact coordinates internal. If city/state lookup fails, show a generic route-location message rather than coordinates.

### 15.12 Blank response

Decision: The user's item 12 was blank; no implementation change is attached to it.

## 16 External source note

For weather planning, Open-Meteo was checked on 2026-09-22. Its public docs describe no required API key for non-commercial/free use and historical weather support, with attribution requirements. Re-check before implementation because service terms can change.
