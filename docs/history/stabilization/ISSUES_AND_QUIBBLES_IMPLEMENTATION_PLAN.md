# 1 WAYiRUN issues and quibbles implementation plan

> **HISTORICAL RECORD:** This document is preserved as evidence of the project's development. Statements describing it as controlling or authoritative applied during that phase and do not override current repository documentation or source.

Version: 0.2
Status: Accepted execution plan; milestone completion evidence tracked in Section 14.1 and TASKS.md
Date: 2026-09-22
FILE: <repository-root>\ISSUES_AND_QUIBBLES_IMPLEMENTATION_PLAN.md

## 1.1 Purpose

This plan converts the current Wayirun Issues & Quibbles list into small, committable Codex execution milestones. It is based on the pasted conversation, the `Wayirun Issue List` ChatGPT conversation, repository documents, and inspection of the current code.

The user authorized execution on September 22. Document alignment precedes source changes; milestone completion records distinguish implemented behavior from remaining acceptance checks.

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

## 1.3 Pre-implementation repo findings

The achievements, photos/public pages, Health Connect, coaching history/export, and updated plans/tests were checkpointed and pushed in `e772ea2` on `codex/account-sessions`. Accepted planning decisions were pushed in `9a864c1`. Preserve this baseline. Milestone 0 is complete.

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
- The `OPEN` handler also retains a finished controller in memory. Fixing the database fallback alone does not cover warm reopen.

Auto-pause requires service lifecycle changes:

- Manual pause stops sensors; `syncSensors()` also stops them for paused runs.
- Periodic ticks currently run only during countdown/running. Auto-paused runs need motion observation and policy timing without accumulating run time or distance.

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

Commits and pushes are not milestone requirements. The initial checkpoint was explicitly delegated and is complete; leave subsequent work uncommitted unless requested. At each milestone boundary, record the changed files, acceptance results, actual verification evidence, and any remaining device checks before proceeding to the next authorized milestone. Do not mark a milestone complete merely because it compiles.

Phone handoffs are batched: the user keeps the existing working APK until milestone announcements and a broader batch are ready. Build and test intermediate artifacts internally without presenting each as a phone-install request.

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

## 2 Milestone 0 - Baseline checkpoint (complete)

### 2.1 Goal

Preserve the currently implemented achievements/photos/Health Connect/coaching-history state before starting new issue-list work.

### 2.2 Completed evidence

Baseline commit `e772ea2` and decision revision `9a864c1` were pushed to `origin/codex/account-sessions`. Android debug assembly/lint and 100 Worker tests passed for the baseline. The working tree was clean and synced before this plan revision. Do not repeat this checkpoint or treat its verification as evidence for later changes.

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

Fresh launch or ordinary reopen after a completed run must show New Run unless there is an unfinished run to recover. Returning from an external activity belonging to the current finish/photo workflow must preserve that workflow.

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

Handle cold database recovery and warm `OPEN` independently. Trace Activity lifecycle and navigation entry points before editing; do not clear a finished controller on every focus event. Represent the current finish/photo workflow explicitly, including pending camera, gallery, save-document, and share-sheet results. Persist pending workflow identity through recreation, scope it to the current account/run, and clear it on explicit exit, new run, or account switch. Resume that workflow only for its own return/restoration; an ordinary app entry must not resurrect a stale summary. Preserve immediate finish coaching and photo editing.

### 4.4 Acceptance

- Reopen after a finished run shows setup, not the last summary.
- Reopen during a paused or interrupted unfinished run still shows the recovery/paused run.
- Account switching does not reveal another account's finished summary.
- Existing discard behavior still deletes only the selected finished run.
- Cold launch and warm reopen both show setup after the finish workflow is exited.
- Camera/gallery/save/share returns and Activity recreation preserve the current finish/photo workflow without replaying coaching.

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

### 5.6 Milestone 3.1 - City/state lookup

Complete route rendering as Milestone 3.0, then city/state resolution as bounded Milestone 3.1. Milestone 3 is complete only after both pass. Generic location text is the failure fallback, not a substitute for implementing lookup.

Before implementation, check primary provider documentation and select a reverse-geocoding service that supports coordinate-to-locality lookup, caching, and unobtrusive attribution. Do not assume the weather provider's city-search API supports reverse geocoding. Record the selected endpoint, terms, attribution, quota, timeout, and cache policy here before wiring it. Prefer a service needing no new paid account; if none meets the constraints, report that concrete dependency.

Selected and implemented September 22: Nominatim reverse, configured by LOCATION_LOOKUP_URL; see worker/MAP_CONTRACT.md. Endpoint https://nominatim.openstreetmap.org/reverse with city-level zoom=10, jsonv2, identifying User-Agent and OpenStreetMap attribution. Shared D1 gate permits at most one attempt per ten seconds (below the provider's one-per-second limit); timeout is three seconds. Cache successful city/region labels per run, retry failures only after 24 hours on a later public-page request, and return generic labels when unavailable. No browser-to-provider calls, bulk job, or live personal-coordinate testing. Server configuration can disable or replace the endpoint. Initial lookup is lazy on the published page; Milestone 4 must preserve that behavior or initiate the same cached lookup on explicit publication.

Resolve the first valid recorded GPS location through the Worker, never through the public viewer's browser. Store city, state/region, source, and attribution as run metadata independent of photos. Do not round to weather-grid precision before locality lookup. Outside regions with states, use the available administrative region; do not fabricate one. Resolve on publication and reuse stored results, including for legacy public runs through bounded lazy backfill. Provider failure returns the generic label and permits a later bounded retry.

Keep exact coordinates in owner data. Public route rendering should receive normalized, segmented geometry rather than raw latitude/longitude; public JSON, HTML, image metadata, and location labels must not expose exact coordinate fields. Route shape itself remains public as requested.

Verify successful locality lookup, missing region, provider timeout, no GPS, caching, and public payloads with fixtures. Milestone 4 must preserve this lookup when it introduces the new publication model.

## 6 Milestone 4 - Shared-run controls and short links

### 6.1 Goal

Make run sharing explicit and owner-controlled, add short share links, and let the owner toggle photo visibility separately from sharing.

### 6.2 Files and components

- `worker/migrations/0009_public_runs.sql` or next numbered migration (0008 is used by run locations)
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

### 6.6 Migration and publication contract

Preserve already-public runs and their existing long links during migration; keep existing private photos private. Backfill an independent publication record for each previously public run. Generate short tokens with a cryptographically secure source, a database uniqueness constraint, and collision retry. New runs can be shared without any photo; deleting or replacing a photo must not implicitly unshare the run.

All public routes, including legacy links, short links, data, images, thumbnails, and preview metadata, must check current publication state. Hiding a photo disables its public image endpoints and preview images while leaving stats/splits/route available. Unsharing disables every public endpoint for that run. Use revocation-aware response/cache behavior; do not use permanent redirects or public image caching that bypasses current visibility. Previously downloaded copies cannot be recalled.

Keep publication mutations separate from photo uploads. Reject stale owner mutations using a server revision; retries are idempotent. Old `X-Photo-Public` uploads must not republish an unshared run or override photo visibility. Migrate queued Android work and handle older clients explicitly. Deletion wins over pending publication/upload jobs; queue entries remain scoped to their original owner and run. Default resharing re-enables the existing token deliberately.

### 6.7 Sharing entry points and offline behavior

Share image, Share run link, and Copy run link all record explicit publication intent before opening the chooser or clipboard action. Cancelling the chooser does not undo that intent, consistent with the user's decision that any sharing attempt publishes. Keeping a photo, saving a local image, toggling photo display, or ordinary sync does not publish.

When offline or unsynced, persist owner-scoped publication intent and show pending status. Local image sharing may proceed immediately; the public page becomes available after run sync and publication succeed. Link copy/share remains pending until a real URL exists; never invent a URL or reopen a chooser automatically after background sync. Unshare cancels pending publication locally and queues revocation if offline; show pending revocation until server confirmation. A newer unshare must defeat an older queued share.

### 6.8 Bounded delivery and verification

Milestone 4.0 implements and tests the migration, independent publication API, public access checks, and web controls. Milestone 4.1 adds Android share entry points, durable intent, and status handling against that contract. Both must pass before Milestone 4 is complete.

Test legacy public/private migration, photo-free sharing, direct image access after hiding/unsharing, stale upload after unshare, duplicate and out-of-order mutations, offline share then unshare, account switching, and deletion during sync. Verify the web and Android flows agree on publication state.

September 27: Milestone 4.0 is complete. Migration 0009, independent publication API, revision/operation receipts, stable short links, current-state public checks and web controls passed TypeScript compilation, deployment dry-run, all 125 Worker tests, and desktop/mobile local browser verification. Conflict refresh, failed mutation and delayed-navigation behavior also passed through browser interception. See MILESTONE_4_0_TEST_HANDOFF.md and worker/PUBLICATION_CONTRACT.md. Android durable intent remains 4.1 and rollout remains deferred until both are verified.

September 27: Milestone 4.1 and Milestone 4 are complete. Room v7 separates durable publication intent from photos; Android summary/photo controls, offline intent, revision conflicts and stale-response guards passed 95 JVM tests, debug/test APK assembly, lint, 49 distinct focused emulator cases and light/dark/200-percent visual inspection. One known long-suite RunEntry timeout passed in isolation. See MILESTONE_4_1_TEST_HANDOFF.md. No deployment or phone install occurred; Milestone 5.0 followed this checkpoint and is recorded complete in Section 7.8.

## 7 Milestone 5 - Photo overlay route noodle and weather

### 7.1 Goal

Improve the rendered share image: route noodle lower-right with no extra dark rectangle, plus optional weather overlay.

### 7.2 Files and components

- `android/app/src/debug/java/com/example/runningapp/photos/PhotoFlow.kt`
- `android/app/src/androidTest/java/com/example/runningapp/photos/PhotoRenderTest.kt`
- `android/app/src/debug/java/com/example/runningapp/photos/PhotoSync.kt`
- `android/app/src/debug/java/com/example/runningapp/storage/RunDatabase.kt`
- `worker/src/photos.ts`
- A separate next numbered migration after public runs if needed
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

### 7.7 Weather retrieval before Android rendering

Milestone 5.0 defines and tests the Worker weather contract; Milestone 5.1 integrates Android preview/render/persistence and the route overlay change. Milestone 4 must be complete first. Select and document the appropriate provider endpoint for recent runs and older runs, timestamp matching, coordinate rounding, timeout, cache lifetime, and attribution after checking current documentation. Do not substitute today's conditions for a historical run.

Use an owner-authenticated Worker lookup for a synced run. The Worker derives location/time from that owned run and returns a bounded weather snapshot with provenance. Android requests this while the photo editor is open, before rendering the kept JPEG. Unsynced/offline runs show weather unavailable; after sync, the open editor may retry. Keeping/saving/sharing never waits for weather.

Hold the returned snapshot in editor state and persist it with the chosen photo revision. Render both preview and final JPEG from that same snapshot; the upload includes the matching snapshot and overlay selection. Ignore late results after Keep, editor exit, account switch, or revision replacement. Do not silently rerender a kept image. Existing photos receive weather only through explicit replacement.

Verify delayed replies, editor recreation, unsynced run becoming available, missing historical data, timeout, weather toggle off, and identical weather values in preview/JPEG/upload metadata. Provider lookups must not change publication state.

### 7.8 Milestone 5.0 implementation checkpoint

September 28: Milestone 5.0 is complete. Added authenticated GET /api/weather/{runId}, verified archive derivation, 0.1-degree rounding, exact UTC start-hour selection, recent/archive endpoint split, bounded timeout/body, stable owner/run cache and revocation/deletion/late-attempt guards. Migration 0010 is not applied remotely. See worker/WEATHER_CONTRACT.md and MILESTONE_5_0_TEST_HANDOFF.md.

Provider terms rechecked: Open-Meteo attribution guidance calls for credit alongside displayed data. Under the accepted provider-terms exception, 5.1 must preserve unobtrusive credit on weather-bearing standalone images as well as settings/public pages; do not assume settings-only credit suffices. TypeScript compilation, Cloudflare deployment dry-run and all 142 Worker tests passed with zero failures/skips/cancellations; the built router contains the weather endpoint and `git diff --check` passed. Photo/API expansion and route rendering remain 5.1. No Android change, deployment, remote migration, real provider request or phone installation occurred.

### 7.9 Milestone 5.1 completion

September 28: locally complete. Added optional Android weather editing/rendering and captured JPEG/options/snapshot persistence; route lower-right without its dark rectangle; small provider credit in settings, exported image and public page; Room v8 and Worker migration 0011; exact owner-snapshot upload validation; private CSV weather and public coordinate omission. Existing kept images are unchanged. Render results include source/editor identity so late lookup or source changes cannot mix metadata with old JPEG bytes.

Worker compile/dry-run and 146 tests, 95 JVM tests, Android build/lint, Room schema 8, all 13 new emulator cases, dark 200-percent visuals and 390/1365-pixel browser checks passed. One existing RunEntry lifecycle/photo-editor case remains for Milestone 11. `git diff --check` passed. No deployment, remote migration, real weather query, physical-phone install, commit or push. Milestone 11 follows.

## 8 Milestone 6 - Milestone announcements

### 8.1 Goal

Implement user-selectable run announcements at 5 minutes, 10 minutes, or 0.5/1 selected distance unit (miles or kilometers), continuing beyond a selected goal.

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
- Exactly one interval choice: 5 minutes, 10 minutes, 0.5 selected distance unit, or 1 selected distance unit. Display miles or kilometers according to settings.

Snapshot the selected announcement settings at run start. Do not let later settings edits change an active run.

Generate announcement events whenever active time or distance crosses the configured interval. Continue after the selected goal is reached. Do not duplicate an announcement after pause/resume, recovery, process restart, or source handoff.

Announcement content must be in this order: elapsed time, distance, average pace. Use selected units honestly. Unavailable pace must not be spoken as zero or infinity.

### 8.4 Acceptance

- Time intervals fire at 5- or 10-minute active-time boundaries.
- Distance intervals fire at 0.5/1 mile or 0.5/1 kilometer boundaries according to the units snapshotted at run start.
- Goal reached still fires once and does not stop the run.
- Announcements continue beyond the goal.
- Recovery does not replay already emitted announcements.
- Off means no interval announcements while state/goal/finish cues still work.

### 8.5 Verification

Add deterministic controller tests for multiple interval crossings, pause exclusion, resume, goal-plus-post-goal announcements, source gaps, and recovery. Add cue-text tests. Run Android checks.

Milestone 6 uses the continuing two-phase workflow: prepare edits/tests, then pause for the user's model switch and explicit continuation before executing MILESTONE_6_TEST_HANDOFF.md. Implementation defaults: new setup on at five minutes; older saved runs missing the fields retain intervals off. Existing JSON checkpoints and saved totals support recovery without a Room migration. No extra interval choices, notifications or adjacent milestone features are included.

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

Remove all automatic media-session transport control, including app-pause-player/app-resume-player, as accepted by the user. Preserve:

- Open YouTube playlist button.
- Audio ducking for WAYiRUN cues.
- Tracking independence when music is off/unavailable.

Remove settings text/buttons that imply WAYiRUN needs YouTube Music control access. Do not request notification-listener access only for a removed feature.

### 9.4 Acceptance

For this milestone, the user requested a two-phase experiment: prepare coding/edits and an exact test plan first, then pause for a model switch. Run tests/builds only after explicit continuation. MILESTONE_7_TEST_HANDOFF.md defines the execution-only second phase and failure-reporting boundary. Do not mark the milestone complete from source edits alone.

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

Use existing controller accounting semantics so active time and distance freeze correctly. Emit distinct automatic pause/resume events with the accepted spoken cues "Auto-paused" and "Resumed."

Do not reuse manual pause's sensor shutdown for automatic pause. Keep motion observations and monotonic policy timing alive while auto-paused, including with the screen off. These observations must not accumulate distance, active time, splits, or route points. Reset distance/step baselines on resume and begin a new route segment so movement during the pause is not counted retroactively.

Define source freshness and reliability explicitly in the pure policy. Missing/stale GPS callbacks are not evidence of being stationary; an event-driven step sensor can legitimately emit no events while stopped, so assess its registration/permission health separately. Specify numeric speed/accuracy/hysteresis thresholds in the policy and test boundaries before service integration. Persist pause reason; a manual pause while auto-paused changes it to manual. After process death, use the existing interrupted-run recovery flow and require explicit resume rather than trusting old movement evidence. Loss of every usable sensor while auto-paused leaves the run paused with manual Resume available.

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

### 10.7 Bounded delivery

Milestone 8.1 is complete: sensor observations, TrackingInput policy integration, service timing/accounting, captured default-on setting, automatic cues and manual override passed the 95-test JVM gate, Android build/lint, 26 focused emulator tests and repeated real-service runs. Light, dark and 200-percent settings/active-state visuals passed. Physical movement thresholds and screen-off acceptance remain deferred.

September 26 status: 8.0 and 8.1 are complete. Pure policy, optional snapshot pause reason, guarded controller transitions, service integration, settings, cues and recovery passed 95 JVM tests, debug/test APK assembly, lint and 26 focused emulator tests. See TASKS.md 1.7-1.8 and the milestone handoffs. Milestone 9 subsequently completed on September 27; Milestone 4.0 is next.

Milestone 8.0 adds the pure policy, pause reason/checkpoint compatibility, and deterministic tests. Milestone 8.1 connects service sensors/timers, default-on settings, cues, and recovery. Milestone 8 is complete only when automatic resume works through service-level tests and accounting excludes paused observations. Record screen-off/device checks separately.

Test manual override during auto-pause, stale GPS, permission loss, source handoff, delayed/batched steps, process recreation, repeated stops, and Health Connect pause-interval export. Assert no duplicate announcements or distance jumps after automatic resume.

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

September 27: Milestone 9 is complete. `run-quality.ts`, coaching context/provider and classifier/context/job tests use the exact authoritative 0.05-mile threshold, 25-second sustained windows and bounded same-segment observations; all retained run data stays in context. TypeScript compilation, the local Cloudflare deployment dry-run and all 118 Worker tests passed. All provider calls were stubs. See MILESTONE_9_TEST_HANDOFF.md. Real generated tone and heuristic thresholds remain acceptance with eventual user recordings. Milestone 4.0 is next.

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

Continue the user's coding/test split for this milestone: Phase 1 prepares UI edits and regression tests; pause for the model switch before executing MILESTONE_10_TEST_HANDOFF.md. Phase 2 includes emulator screenshots and actual full-screen inspection at normal and large text, not only compilation. Phone acceptance remains deferred until the broader handoff.

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

Milestone 11 complete locally, September 28: acceptance checklist aligned, picker-return synchronization corrected, and consolidated Worker/Android/emulator/browser/visual gates passed. All 105 distinct emulator cases passed across the explicit Health Connect permission split. The Compose wrong-thread exception did not recur in the full suite or six focused editor runs. One timestamped APK remains in the repository build tree. See MILESTONE_11_TEST_HANDOFF.md. Deployment, migrations and physical-phone acceptance remain separate.

1. Milestone 0: Baseline checkpoint complete; do not repeat.
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

The ordering front-loads the likely bugs and removals before larger schema/API work. Preserve these milestone IDs when recording progress. Milestone 1 precedes source edits; Milestone 3.0 precedes 3.1; Milestone 4.0 precedes 4.1; Milestone 4 precedes 5.0/5.1; Milestone 8.0 precedes 8.1. Every submilestone has its own implementation and verification boundary. Passing a boundary does not require a commit or push.

### 14.1 Milestone status and completion record

Milestones 0-3 are complete. Milestone 1 aligned REQUIREMENTS.md, TASKS.md, REMAINING_WORK.md, and FINAL_PASS_ISSUES.md with accepted decisions and marked PHOTOS_PLAN.md as historical. Documentation checks covered decimal headings, local references, superseded rules, and `git diff --check`.

Milestone 2 separates ordinary Activity entry from refresh/external returns, removes latest-finished-run startup recovery, and preserves exact owned finish/photo workflows through recreation. All 78 JVM tests, debug assembly/lint, and 15 distinct focused emulator checks passed; detailed batches and artifact paths are in TASKS.md 1.2. Physical camera/share-target and external-activity process-death acceptance remain unverified.

Milestones 3.0 and 3.1 replace basemaps with route graphics, normalize public geometry, and add cached city/state metadata with provider attribution. The 107-test full Worker gate and a subsequent 24-test focused pass succeeded; desktop/mobile browser checks and screenshots passed. See TASKS.md 1.3. Migration 0008 is local source only; no remote migration, deployment, physical-phone install, commit, or push. Phone handoff remains deferred.

Milestone 7 is complete after the requested two-phase/model-switch workflow. Automatic music transport, listener registration and access UI are removed; playlist opening and cue ducking remain. The source gate, Android build/JVM/lint gate and all 22 focused emulator tests passed. TASKS.md 1.4 records commands, counts, artifacts and physical-device limits. No phone install, deployment, commit or push occurred. Milestone 10 is next; later milestones remain planned.

Milestone 10 is complete: square emoji setup selectors, font-scale-aware wrapping, left/right colored status labels, and focused UI regression/screenshot coverage are implemented. The Android build/JVM/lint gate, 17 focused emulator cases and normal/200-percent visual inspection passed after correcting spacing identified in the first large-text screenshots. See TASKS.md 1.5 and MILESTONE_10_TEST_HANDOFF.md. No phone install, deployment, commit or push occurred. Announcements (Milestone 6) are next.

Milestone 6 is complete: four interval choices/on-off control, captured settings, time/distance crossing events, recovery, and existing speech/focus integration are implemented. The 78-test JVM gate, debug/test APK assembly and lint passed; focused emulator coverage passed except one existing run-entry lifecycle timeout that passed immediately in isolation. Light, dark and 200-percent settings visuals passed. See TASKS.md 1.6 and MILESTONE_6_TEST_HANDOFF.md. Physical audio acceptance remains deferred. Milestones 8 and 9 are also complete; Milestone 4.0 is next.

At the start of each authorized milestone, read the current source and required project docs, confirm prerequisites, and identify the bounded files to edit. At completion, update this status and `TASKS.md` with the implemented behavior, commands actually run and their outcomes, artifact paths when applicable, and outstanding device acceptance. If a required check fails, keep the milestone in progress and resolve it before moving forward. No deployment is authorized by this plan.

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
