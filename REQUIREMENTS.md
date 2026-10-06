# 1 WAYiRUN requirements

Version: 1.0
Status: Accepted feature-development requirements
Baseline date: 2026-09-29

## 1.1 Authority

This document is the product source of truth for the feature-development baseline. Later explicit user decisions supersede it. `CURRENT_STATE.md` records delivery, `OPEN_WORK.md` records remaining work by category, and historical plans are evidence only.

MUST and MUST NOT identify required behavior. Unsettled or deferred ideas are not permission to invent behavior.

## 1.2 Product boundary

WAYiRUN MUST provide an Android running tracker and a desktop-accessible private history application. The phone experience MUST stay run-focused; detailed history and management MAY remain primarily on the web.

The product MUST NOT expand into calorie tracking, a general health application, social feeds, clubs, stores, advertising, or interactions between users. A watch application and additional sensor families are outside the baseline.

## 1.3 Identity, ownership, and secrets

Authentication MUST use Google identity. Profiles MUST remain basic and use display name and profile picture.

Runs, photos, achievements, settings, coaching, and operational queues MUST remain isolated by owner. Public access MUST be limited to an explicitly published individual run.

Pre-account runs MUST remain local until the user explicitly adds existing runs to the signed-in account. Signing in or switching accounts MUST NOT silently claim or reassign runs. New runs belong to the account selected when they start, including offline runs for an established account.

Each user MUST supply their own OpenAI API key for AI coaching. Keys MUST be encrypted server-side, masked after entry, associated with the account, and absent from the distributed app, logs, public pages, and exports. There MUST be no shared fallback key or development authentication bypass.

## 1.4 Setup and settings

The new-run screen MUST show the current profile, indoor/outdoor selection, none/time/distance goal selection, applicable target, playlist-open action, run-type and connectivity indicators, and a prominent START RUNNING action.

Units, countdown, stride, announcements, auto-pause, appearance, permissions, account actions, API-key controls, synchronization, and Health Connect MUST stay behind the gear. Settings MUST save automatically without a Save button. Settings changes MUST NOT alter an active or recovered run's captured behavior.

Stride MUST accept centimeters or inches per counted step, start blank, and never substitute an invented value. Time-only or GPS tracking MUST remain possible without stride.

Dark appearance MUST be selectable. Units MUST support miles and kilometers.

Startup MUST request only applicable location, physical-activity, and notification permissions. Permission requests MUST NOT repeat on focus changes, activity returns, or configuration recreation. Denial MUST NOT block time-only running. Manual request and system-settings actions MUST remain available in settings.

## 1.5 Run goals and entry

Goal types MUST be exactly None, Time, and Distance. Reaching a goal MUST announce current active time, distance, and average pace, then continue tracking until the user finishes.

The app MUST permit starting without internet, GPS, stride, or a usable distance sensor. When no distance source is usable, the timer MUST continue, distance MUST remain honest, and the active UI MUST show that distance is unavailable.

## 1.6 Tracking and retained data

Runs MUST support countdown, start, pause, resume, finish, and deliberate discard.

Outdoor mode MUST record usable GPS route points and preserve route/source gaps. It MUST use step count multiplied by captured stride as fallback when GPS is unavailable. If GPS later becomes available, a new GPS segment MUST begin without double counting or joining missing portions.

Indoor mode MUST use counted steps multiplied by captured stride and MUST NOT request GPS.

Pausing MUST freeze active time, distance, route, and split progress. Average pace MUST use active time divided by retained distance. Full splits MUST use one selected unit; a nonzero final remainder MUST appear as Partial with its distance, time, and pace.

Each run MUST retain identifiers and ownership, start/end time, time zone/offset, active duration, distance, pace, captured settings, state/pause reason, splits, active intervals, source segments, detailed measurements, and any GPS route. Associated coaching, achievements, photo, weather, publication, synchronization, and Health Connect state MUST remain linked where applicable.

## 1.7 Recovery and background operation

Active tracking MUST use an ongoing foreground service and remain usable with the screen off. An interrupted run MUST recover from its last committed checkpoint and require explicit resume. Stale sensor callbacks or UI controls MUST NOT mutate another run or replay acknowledged events.

Ordinary warm/cold reopen after a completed run MUST show New Run rather than the previous summary. Returns from the current finish, coaching, camera, picker, save, or share workflow MUST preserve that exact owned run where applicable.

## 1.8 Automatic pause

Auto-pause MUST default on and be captured at run start. Indoor decisions MUST use TYPE_STEP_DETECTOR; outdoor decisions MUST use detector evidence and reported GPS speed. The cumulative step counter MUST remain an accounting input only. Raw accelerometer classification MUST NOT participate.

A successfully registered/permitted detector MUST first deliver two timely steps occurring within five seconds to arm silence-based stop detection. Five seconds with zero subsequent detected steps permits an indoor pause. Every timely detected step restarts that clock, measured from callback receipt. Detector events older than 2.5 seconds invalidate arming; duplicate, obsolete and future events MUST NOT create movement. Initial silence MUST NOT pause a run.

Outdoor pause MUST require five seconds of qualifying stationary GPS concurrently with five seconds of armed detector silence. A confirmed missing/denied/failed detector permits GPS-only pause; a registered but unarmed or delivery-unreliable detector does not. Stale/unavailable GPS MUST prevent a new outdoor pause. GPS qualifies only with finite speed in 0–12 m/s, horizontal accuracy in 0–30 m, speed accuracy in 0–0.5 m/s, and age/inter-fix gap at most 2.5 seconds. Stationary means speed + speed accuracy <= 0.5 m/s; moving means speed - speed accuracy >= 1.0 m/s. Deadband/invalid evidence resets GPS dwell. Dwell requires distinct fixes spanning five stationary or two moving seconds, never repeated evaluation of one fix.

An automatic pause MUST resume on two timely post-pause detector steps occurring within two seconds, without an additional dwell, OR (outdoors) two seconds of qualifying GPS movement. Steps may resume despite stationary/stale GPS. A policy tick gap above 2.5 seconds invalidates accumulated evidence. Registration changes, manual transitions and recovery MUST discard old evidence. Detector arming is ephemeral and MUST NOT be inferred from counter delivery. Missing evidence leaves the current state unchanged; manual Resume remains available.

Manual pauses MUST never auto-resume. Automatic pauses MUST continue observing movement without accumulating active metrics. Resume MUST establish fresh measurement baselines. Automatic transitions MUST announce “Auto-paused” and “Resumed.”

Transitions MUST NOT be backdated. Existing accounting excludes late counter batches received after a measurement segment closes, even if they contain pre-pause steps; no per-step timestamps or retrospective distance may be invented. Detector silence cannot distinguish a previously working sensor's silent failure from standing still. These limitations MUST remain explicit in acceptance evidence. No personalized calibration or new persisted policy state is required.

## 1.9 Active and finish interactions

The active screen MUST show active time, distance or honest unavailability, average pace, and split information with a large pause/resume control. An active basemap is not required.

Finishing MUST require a swipe confirmation. A checked-by-default control MUST decide whether coaching is requested for that finish. Completion and selected coaching MUST not prevent the run from being saved.

Finish MUST support achievement celebration, coaching animation/audio, summary, optional photo, sharing controls, discard, and New Run. Dismissing an animation MUST NOT stop coaching playback.

## 1.10 Audio and music

WAYiRUN MUST provide run-state, goal, milestone, and completion cues. Spoken summaries MUST report active time, distance, and average pace honestly in captured units.

Milestone settings MUST include a master switch plus independent Time and Distance channels. Time MUST offer 5 or 10 active minutes; distance MUST offer 0.5 or 1 selected unit. Announcements MUST continue after a goal and MUST NOT replay after recovery. Goal audio MUST take priority over a milestone occurring within one second; simultaneous milestone channels MUST form one recap.

Each cue MUST request temporary audio focus allowing other audio to duck, then release it without changing global volume or issuing a player resume command.

Music integration MUST be limited to saving, clearing, validating, and opening a YouTube Music playlist. Starting, pausing, resuming, finishing, or receiving media/headphone events MUST NOT control the other app or change run state. WAYiRUN MUST NOT request notification-listener access.

## 1.11 Coaching

Selected coaching MUST use only the signed-in user's key and verified retained data from the current run and, when available, the previous retained run. It MUST retain a durable job/result and avoid duplicate paid attempts after interruption or reopen.

Deterministic quality evidence MUST identify likely test runs, implausibly fast/vehicle-like recordings, and GPS anomalies so coaching can respond appropriately without changing the saved record. Unchecked coaching MUST not generate AI or play fallback encouragement.

Offline, missing-key, failed, or timed-out selected coaching MAY use bundled encouragement. Normal completion audio MUST remain independent. Saved cloud coaching MUST be replayable from private web history when present.

OpenAI MUST remain the only AI provider in this baseline. Cedar remains the selected generated voice until voice selection is separately approved.

## 1.12 Achievements

Achievements MUST be deterministic projections of one owner's retained history. They MUST support the accepted distance, count, lifetime, week/streak, month, Sunday, holiday, anniversary, personal-best, negative-split, and progression catalog.

Eligible finish-time awards MUST be celebratory and dismissible without stopping coaching. History, phone results, and export MUST use stable award identities. Deleting or importing runs MUST recompute affected history-derived awards.

Performance awards MUST use only continuous reliable measurement curves. Broader calculation across paused/source-changing runs is deferred, not required for this baseline.

## 1.13 Photos, weather, and overlays

The finish summary MUST allow camera, system image picker, cancel/skip, retake, preview, keep, save, and standard Android share actions. Image decoding MUST be orientation-aware and bounded. Source metadata MUST not be copied into the rendered JPEG.

Selectable overlays MUST support time, distance, pace, actual recorded route, and available run-start weather. Route overlays MUST preserve gaps and MUST NOT use a basemap or dark map rectangle. Indoor/no-route/weather-unavailable cases MUST remain usable without invented content.

Weather MUST use cached archive-derived start context with visible provider/license attribution. Keeping or saving a photo MUST NOT publish the run.

## 1.14 History, route graphics, publication, export, and deletion

Private web history MUST provide completed runs, loaded-scope totals, unit selection, details, settings, splits, route, photo, coaching replay, achievements, export, and deliberate deletion.

Route displays MUST use provider-independent route noodles with preserved gaps and start/finish markers. Leaflet, OpenStreetMap tiles, Mapbox, and other basemaps MUST NOT be restored. Coarse city/region MAY be shown with appropriate source attribution; public raw coordinates MUST NOT be exposed.

Runs MUST remain private by default. An explicit run-link or image-share attempt MUST record publication intent even if an Android chooser is later cancelled. A public short link MUST be offered only after confirmed publication. Public pages MUST expose only the selected run. Photo visibility and unsharing MUST be independent; deletion MUST revoke public access.

Export MUST produce one complete CSV containing retained run summaries and reconstructable detailed records, including settings, samples, route, splits, intervals, segments, coaching metadata/audio, achievements, photos, and integrity fields where present. Secrets MUST never be exported.

Deletion MUST remove only the selected owned run and reconcile dependent photo, publication, coaching, achievements, Health Connect, and phone/cloud state. A stale upload MUST NOT resurrect deleted data.

## 1.15 Health Connect

Health Connect support MUST be explicit, account/local-scope aware, and write only completed exercise sessions and distance. It MUST NOT import health data, export routes/photos/coaching, or request unrelated health permissions.

Retries MUST use stable record IDs. Offline export MUST not require internet. Deletion cleanup MUST remain pending if permission is unavailable and resume after reconnection. WAYiRUN's retained run remains the source of truth.

## 1.16 Reliability and privacy

Errors MUST be contextual, bounded, and avoid exposing secrets, identifiers, headers, archives, or stack traces. Network failure MUST not discard local run data. Existing valid records MUST not be overwritten by restore.

The app MUST use styled application controls rather than browser `alert()` or `confirm()`. Destructive actions MUST require deliberate confirmation.

Generated outputs, credentials, API keys, signing material, local SDK paths, Wrangler state, personal data, and sensitive logs MUST remain outside source control.

## 1.17 Deliberate deferrals and exclusions

The following are not required for the feature-development baseline:

- week/month/year/lifetime statistics selectors, sorting choices, or charts;
- coaching voice selection;
- broader achievement performance calculations across discontinuous evidence;
- automatic photo restoration onto a new phone;
- production Android identity, release implementation, signing, distribution, policy declarations, production Cloudflare configuration, or production deployment;
- comprehensive physical-device or release acceptance.

Deferred items MUST remain separate from bugs and unverified behavior in `OPEN_WORK.md`.
