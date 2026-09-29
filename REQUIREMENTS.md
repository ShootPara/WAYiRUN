# WAYiRUN — Requirements

Version: 0.5

Status: Accepted requirements including September 22 issues-and-quibbles decisions; implementation progress is tracked separately in TASKS.md

Date: 2026-09-22

FILE: REQUIREMENTS.md (REPLACE)

## 1 Authority and scope

This document records the product decisions in [Running App First Convo](chatgpt-conversation://6aa08df7-de0c-83ea-9468-a00d5e2b6162), subsequent decisions in this task, and the applicable rules in the project's `the then-current working guide`. Later explicit user decisions supersede earlier suggestions. The edited feature inventory is included where later decisions do not supersede it. Assistant implementation proposals and illustrative examples are not automatically approved requirements.

MUST and MUST NOT identify required behavior. Section 15 identifies details not yet settled; these are not permission to invent product behavior. This document is the product source of truth. Acceptance of the baseline does not imply approval of unspecified later-feature details.

The user has authorized the bounded milestones in `ISSUES_AND_QUIBBLES_IMPLEMENTATION_PLAN.md` in the existing repository at `<repository-root>`. The initial baseline checkpoint is committed and pushed. Subsequent milestones do not require automatic commits or pushes. No deployment is authorized by this plan. Historical implementation notes describe prior behavior and do not override these requirements.

## 2 Product purpose and boundaries

The app's display name MUST be **WAYiRUN** (user decision, 2026-09-12). This naming decision does not change the repository path or establish a production application identifier.

### 2.1 Platforms

The product MUST provide an Android running tracker and a desktop-accessible web application for the user's run history.

### 2.2 Core features

The app MUST support tracking, spoken milestones, playlist opening and cue ducking, post-run coaching, achievements, and optional photo sharing. Nike Run Club is a reference for core running functionality, not a specification for the interaction design.

### 2.3 Cloud storage

Run information, historical queries, and shared images MUST use the user's Cloudflare infrastructure. Specific Cloudflare services and implementation technologies remain for later planning.

### 2.4 Phone and desktop roles

The phone experience MUST stay sparse and run-focused. Detailed history and statistics management MUST be available on the desktop web app.

### 2.5 Excluded features

The product MUST NOT expand into calorie tracking, a general health application, or interactions between users. Social networks, clubs, stores, advertising, and unrelated NRC ecosystem features are outside the requested scope.

### 2.6 AI provider and hardware scope

OpenAI MUST be the only AI provider implemented in this scope. Other providers are a stated future interest, not a current feature. A standalone watch application and additional sensor integrations have not been requested for implementation.

## 3 Accounts, identity, and secrets

### 3.1 Authentication and profiles

Authentication MUST use Google OAuth. Profiles MUST remain basic and include a profile picture and display name.

### 3.2 User data isolation

Runs, photos, achievements, preferences, and coaching history MUST belong to the authenticated user and remain separate from other users' private data. Public access is limited to the individual-run publication behavior in Section 11.

Latest user decision, September 15: pre-account local runs MUST remain local until the user chooses an explicit **Add existing runs to this account** action in gear settings. New runs belong to the account selected when they start, including offline runs with a previously established identity. Signing in or changing accounts MUST NOT silently claim or reassign existing runs. First-time offline tracking remains local until deliberate import; it MUST NOT invent a Google identity.

### 3.3 Identity display

The UI MUST use display names rather than expose sensitive identifiers. Internal identity MUST remain stable.

### 3.4 Per-user API keys

Each user MUST supply their own OpenAI API key as a per-user setting for AI coaching. The application MUST NOT use the owner's key for another user or provide a shared fallback key.

### 3.5 API key storage

API keys MUST be encrypted server-side, masked after entry, and absent from the distributed Android application. The stored key MUST remain associated with the account when the user changes phones.

### 3.6 Secret protection

Secrets MUST NOT appear in source control, logs, public pages, or exported user-facing artifacts. Deployment secrets and real configuration values MUST use appropriate environment or platform storage rather than documentation.

## 4 Settings and pre-run screen

### 4.1 Screen layout

Opening the run setup screen MUST show the logged-in user's display name and profile picture at the top and a **START RUNNING** button at the bottom.

### 4.2 Run parameters

Latest user decision, September 15: the main pre-run screen MUST contain unlabeled Indoor/Outdoor and None/Time/Distance selectors, the selected goal target, an Open YouTube playlist button, and Indoor/Outdoor plus Online/Fallback indicators. Keep this screen sparse: no build diagnostics, setup headings, configuration summaries, or explanatory account/music copy. Profile and START RUNNING remain. The playlist URL, units, countdown, stride, appearance, permissions, account actions, and all additional settings MUST stay behind the top-right gear. Future milestone intervals and the milestone on/off switch MUST be in settings. The same gear MUST close settings. Changes MUST save automatically and survive closing/reopening the app without a Save button. An active run retains its captured settings; pre-run selectors are not shown during a run.

The Indoor/Outdoor and None/Time/Distance selectors MUST use square, readable controls with appropriate emojis. Controls MUST remain usable at large text sizes without clipping.

### 4.3 Goals

Goal types MUST be exactly **None**, **Distance**, and **Time**. Distance and time goals MUST allow the corresponding target to be set.

Reaching the selected goal MUST trigger an audible completion announcement and MUST NOT automatically stop the run. Tracking MUST continue until the user finishes it.

### 4.4 Pre-run indicators

The START RUNNING area MUST display the run type on the left and connectivity on the right: Outdoor green, Indoor blue, Online green, Fallback red. Text labels MUST remain readable independently of color. The exact service checks for connectivity remain a review detail in Section 15.

### 4.5 User settings

User settings MUST include stride length, miles/kilometers, selectable dark mode, the user's OpenAI API key, and the announcement selection defined in Section 7.

Stride entry MUST accept centimeters or inches as distance per counted step and retain the entered setting. It MUST start blank and MUST NOT substitute an invented stride. Explain that step-based distance requires this value; GPS or time-only tracking can start without it.

### 4.6 Initial music support

Initial music support MUST target YouTube Music. Save the playlist entry automatically as it is edited in gear settings, retaining even unfinished input for later correction. Offer **Open playlist** for valid links before starting a run. Playback is chosen in YouTube Music before returning to WAYiRUN. Provide a way to clear the saved link. Invalid/missing links and an unavailable player MUST NOT inhibit running. Opening a playlist MUST NOT start a run or claim playback began. No automatic playlist playback on START RUNNING is required.

### 4.7 Startup permissions

Latest user correction, September 15: retain the original Android launcher splash, then check Location, Physical Activity, and Notifications at startup only. Request missing applicable runtime grants without a setup/configuration overlay or a Continue button. Do not automatically present settings or permission requests on focus changes, background returns, permission-dialog returns, or configuration recreation. Reopening an active/restored paused run MUST remain uninterrupted. Request precise/coarse location together, Physical Activity on supported Android versions, and Notifications on Android 13+, without waiting for stride entry. Granted permissions MUST NOT prompt again. Denial MUST NOT block time-only running. Manual permission requests and App permissions shortcuts belong in gear settings; explain suppressed/denied grants there. Do not request permissions for unimplemented features. This replaces the September 14 every-foreground setup behavior, which the user rejected.

APK handoffs MUST use WAYiRUN plus a date/time stamp only, without change descriptions. Keep only the current handoff APK in the repository after verification, removing older and temporary test/tool APKs. Show the installed build version in gear settings. The debug package task MUST name its actual output WAYiRUN plus the timestamp, rather than make a generic app-debug.apk and copy it. Test tooling may generate separate instrumentation APKs while verifying.

The app MUST NOT request notification-listener access or present music-control setup for the removed media-session feature. Notification display permission for tracking remains separate.

September 22 handoff decision: keep the user's currently working phone build in place. Intermediate milestone APKs are internal verification artifacts only. Defer the next phone handoff until milestone announcements and a broader batch of accepted changes are ready; do not ask the user to install or troubleshoot after every small change.

### 4.8 Completed-run reopen behavior

Cold launch and ordinary warm reopen after finishing MUST show New Run, not restore the last completed summary. Unfinished runs MUST retain recovery behavior. The immediate finish/coaching/photo workflow and returns from its camera, gallery, save, or share activities MUST preserve their current state through recreation. Starting a new run MUST NOT delete completed records, and account switching MUST NOT expose another account's summary.

## 5 Tracking behavior and retained run information

### 5.1 Run controls

A run MUST support start, pause, resume, and finish.

### 5.2 Outdoor GPS tracking

Outdoor mode MUST record and preserve GPS route data independently of rendering. Private and public history MUST render route-only graphics with segment gaps and start/finish markers. Leaflet, OpenStreetMap tiles, Mapbox, and other basemaps MUST NOT be used in this scope. Core tracking MUST support a run without requiring the user to watch the phone throughout it.

### 5.3 Indoor tracking

Indoor mode MUST calculate distance as `run steps × configured stride length` and MUST NOT attempt location tracking.

### 5.4 GPS fallback

An outdoor run without GPS MUST use step count multiplied by configured stride length as its distance fallback. If GPS becomes available, the app MUST begin recording the route from that point forward.

### 5.5 Starting without GPS or internet

Lack of GPS or internet MUST NOT cause the app to refuse to start a run. Fallback portions MUST NOT receive visible “estimated” warnings or special estimated-run labels.

### 5.6 Live metrics

The app MUST track elapsed run time, distance, average pace, and splits/distance milestones. Pausing MUST freeze accumulated run time and distance. Average pace MUST use accumulated active run time and distance, excluding paused time and movement.

### 5.7 Retained run information

Each completed run MUST retain its date, start and end times, elapsed time, distance, average pace, splits, applicable run settings, and indoor/outdoor mode. GPS route data MUST be retained when recorded; shared photos and coaching results MUST remain associated with their run when present.

### 5.8 Historical data

Historical detail MUST be retained sufficiently to display past routes, query runs, compare runs, and provide relevant history to AI coaching.

### 5.9 Units

Distances and pace presentation MUST support the user's miles/kilometers setting. Full splits MUST use one mile or one kilometer, matching the selected units. A nonzero final remainder MUST appear in the summary as a **Partial** row with its actual distance, active duration, and pace. Distance announcements MUST use the selected units as specified in Section 7.2.

### 5.10 Unavailable distance sources

If no usable GPS/step source or required permission is available, the timer MUST still be allowed to run. Show **Distance unavailable**, retain any already recorded distance, and begin measuring once a usable source becomes available. Do not invent distance for the missing interval. This status is not an estimated-run label.

## 6 Active, paused, and finishing interactions

### 6.0 Automatic pause

Auto-pause MUST default on for new and existing installs, with a gear-setting toggle captured at run start. It MUST pause after about five seconds of reliable stationary evidence and resume after about two seconds of sustained movement. Use GPS and steps when both are reliable, otherwise the usable signal; network availability MUST NOT indicate movement. No usable sensors means auto-pause is unavailable, without preventing time-only tracking.

September 28 correction: successful sensor registration and step-counter/detector silence MUST NOT imply stationary. Indoor stillness requires fresh continuous accelerometer coverage; delayed or batched counters remain distance inputs and positive motion evidence, never fabricated per-step timing. Recent steps veto stillness. Missing, stale, ambiguous or conflicting evidence MUST NOT invent a transition. Motion-only sensors MUST NOT add distance, and MUST stop on manual pause, finish or teardown; do not register them when auto-pause is disabled. Indoor runs MUST NOT request GPS.

Manual pauses MUST never auto-resume. Automatic pauses MUST preserve motion observation without adding active time, distance, or route points. Resume MUST start fresh measurement baselines. Sensor jitter, stale GPS, or isolated steps MUST NOT cause flapping. Persist pause reason and preserve interrupted-run recovery requiring explicit resume after process death. Automatic transitions MUST speak "Auto-paused" and "Resumed."

### 6.1 Active-run screen

The active-run screen MUST display time, distance, splits, and average pace, with a large pause button at the bottom. It MUST remain minimal; an active-run map was suggested earlier but is not part of the user's final minimal-screen specification.

### 6.2 Pause, resume, and finish

The user MUST be able to resume a paused run and deliberately finish a run. Finishing MUST require a swipe-to-confirm interaction rather than an immediate stop tap.

### 6.3 AI coaching checkbox

A checkbox near the finish slider MUST control whether AI coaching is called for that run. It MUST default to checked.

### 6.4 Post-run sequence

Finishing MUST lead to spoken coaching when selected and available, accompanied by a display animation, followed by the run summary. Achievement celebrations MUST be shown at this end-of-run stage.

### 6.5 Animation dismissal

Tapping the animation MUST dismiss the animation and MUST NOT stop the spoken coaching.

### 6.6 Photo entry point

The run summary MUST place a take-photo button at the top and lead into the optional flow in Section 10.

## 7 Audio and music

### 7.1 Run state cues

The app MUST provide audible cues for starting, pausing, resuming, and completing a run.

User update following the phone audio retest: goal speech MUST say “Goal reached. Time: [active time]. Distance: [distance]. Average pace: [pace]. Keep going until you're finished.” Completion speech MUST say “Run complete. Time: [active time]. Distance: [distance]. Average pace: [pace].” Values MUST use the event's accumulated active metrics and selected units; unavailable pace MUST be spoken honestly rather than as zero or infinity. These are spoken announcements, not a requirement to create additional Android notifications.

### 7.2 Announcement intervals

September 28 decision: milestone controls MUST appear only in gear settings. Retain the master announcement switch and provide independently enabled Time and Distance channels, allowing either, both, or neither. Time offers **5 minutes** or **10 minutes**; distance offers **0.5** or **1** selected distance unit. Distance labels and speech MUST use miles or kilometers from the run's captured settings. Time intervals MUST use active time. Announcements MUST continue beyond the selected goal and MUST NOT replay after recovery or pause/resume. Master off MUST preserve selections and leave state, goal, and completion cues enabled. New installs default to five-minute time milestones only. Existing preferences MUST migrate once to the matching single channel; old run captures retain their original behavior. Later settings changes MUST NOT alter an active or recovered run.

Accepted for Milestone 14: when goal and milestone occurrences are within one second of active-run time, goal audio alone MUST play. Simultaneous time/distance milestones without a goal produce one recap. Suppressed announcements MUST still consume their thresholds. Milestone 13 prepares independent selections only; goal-audio arbitration remains a separate implementation gate.

### 7.3 Announcement contents

Each milestone announcement MUST report elapsed time, distance, and average pace in that order. It MUST occur at the selected type of milestone: a time interval for time announcements or a distance interval for distance announcements.

### 7.4 Announcement phrasing

Announcement phrasing MUST follow the requested labeled pattern, for example: “Time. Ten minutes, thirty-seven seconds. Distance. One mile. Average pace. Ten thirty-seven per mile.” This example specifies the pattern, not fixed run values.

### 7.5 Run-to-music controls

September 22 decision: remove automatic media-session transport control in both directions. Starting, pausing, resuming, or finishing a run MUST NOT issue player transport commands. Keep only the playlist-open behavior in Section 4.6 and cue ducking in Section 7.8.

### 7.6 Independent player controls

Music/headphone pause and resume MUST NOT change run state, including during cues. Music being off or unavailable MUST NOT inhibit tracking. Finished runs MUST remain terminal.

### 7.7 Media event handling

Media-session events MUST NOT generate run commands or repeated run cues. Remove transport-only listeners, settings, and access requests while preserving audio focus for cues.

### 7.8 Audio ducking

During each cue, WAYiRUN MUST request temporary audio focus allowing other audio to duck, then release it on completion, failure, cancellation, or teardown so normal playback volume can return. Do not alter the user's global volume settings. Android and the other player govern their actual response; verify with YouTube Music on speaker and headphones. Releasing focus MUST NOT issue a player resume command.

### 7.9 Ongoing notification prominence

The running and paused notification MUST remain easy to reach, with prominent notification importance rather than the prototype's low-importance Silent category. The user prefers it at the top. Android and user channel settings control final placement; an absolute first position cannot be guaranteed. Keep notification updates from generating repeated alerts. Respect user settings and handle existing installs explicitly.

## 8 AI coaching and fallback audio

### 8.1 Coaching selection

The finish checkbox in Section 6.3 MUST determine whether the app requests AI coaching. When unchecked, the app MUST NOT make the coaching request or play fallback encouragement. The normal run-completion cue MUST still play.

### 8.2 Coaching inputs

A coaching request MUST use only that user's API key. User decision, September 18, 2026: the initial coaching context MUST contain all stored run data for the just-completed run and the immediately preceding completed run belonging to the same user, when one exists. This includes retained GPS, measurements, splits, intervals, segments and settings; do not silently replace these with summary-only context. Credentials and session records are not run data. If there is no previous completed run, use the current run alone without mentioning missing history. Longer history, trend databases and PR databases are outside this initial slice.

### 8.3 Run analysis

Initial coaching MUST give a brief, conversational motivational message primarily about the current run, using the immediately preceding completed run only for meaningful comparisons supported by those two runs. It MUST NOT claim lifetime records or longer-term trends from this limited context. It MUST provide personalized encouragement rather than unsupported claims presented as analysis. This September 18 decision narrows the earlier broader history-analysis scope.

Before calling AI, a deterministic classifier MUST attach quality labels, reasons, and supporting metrics for likely test/incomplete runs, sustained vehicle-like movement, and GPS anomalies. Initial accepted thresholds are under 90 active seconds or under 0.05 miles for likely test/incomplete, sustained pace faster than 3:30/mile or GPS speed above about 18 mph for likely vehicle movement, and implausible distance/time jumps for GPS anomalies. Feedback MUST acknowledge suspect data without treating it as normal performance or a record. Classification MUST NOT block requested coaching. Exact sustained-window and evidence handling are specified and tested in the implementation milestone.

### 8.4 Voice and tone

September 18 user decision: the current coaching update tests OK. A selectable voice setting is deferred to a later milestone; keep Cedar for now.

Feedback MUST be converted to speech and played at the end of the run. The tone MUST be natural and conversational, matching the existing assistant interaction without constructing a special coach personality.

User-selected voice: **Cedar** (API voice `cedar`), using **`gpt-4o-mini-tts`**. Voice audition is complete; do not ask the user to select a voice again. This is the speech model, not a decision about the separate text-generation model. The pasted comparison script is an audition example, not factual run history or required output. Third-party commentary about audio failures is not an established project finding.

### 8.5 Fallback recordings

The app MUST include onboard general encouragement recordings for coaching failure, such as a brief congratulation for getting out to run. Internet or AI failure MUST NOT prevent completing the run or reaching its summary.

### 8.6 Unavailable API keys

A missing or unusable user key MUST NOT result in using someone else's key. The run MUST remain usable under the fallback requirements.

## 9 Achievements

### 9.1 Award timing and history

The app MUST track and award achievements as soon as they are earned and retain achievement history. Their visual reveal and associated animations MUST occur when the run finishes.

### 9.2 Holiday runs

Running on a holiday MUST trigger the applicable holiday-run achievement.

### 9.3 Achievement categories

The feature inventory includes personal records, distance, cumulative distance, streaks, pace/performance, first-time distance completions, negative-split/performance patterns, and holiday/special-event achievements. Exact achievement entries, thresholds, and calendar rules remain to be defined; examples in the discussion do not establish a complete catalog.

### 9.4 Achievement definitions

Achievement definitions SHOULD be data-driven so adding achievements/events does not require individually engineering each one. This records the inventory's explicit preference without prescribing a data model.

September 18 direction: use Nike Run Club as achievement research, include major-holiday awards, and give WAYiRUN achievements original names. See ACHIEVEMENTS_PLAN.md for the researched catalog, proposed thresholds/calendar rules and implementation boundaries. The user accepted that catalog and authorized implementation; Section 1.8 records the implemented calculation limits. Photos remain the next feature milestone after achievements.

## 10 Photo, overlay, save, and share

### 10.1 Optional photo choices

The photo flow MUST be optional after every run and offer **Take Photo**, **Choose Existing Photo**, and **Skip**.

### 10.2 Camera selection

Taking a photo MUST support front and rear cameras.

### 10.3 Run overlays

The selected photo MUST serve as the background for a finished run image. The user MUST be able to select time, distance, pace, route when available, and weather when available independently. The route MUST appear lower-right with segment gaps and no route-specific dark background box; retain the stats band.

Weather MUST show a small emoji and temperatures in F and C. Use the recorded outdoor start location/time, with rounded coordinates for weather lookup; indoor/no-GPS runs MUST NOT acquire fabricated weather. Provider failure MUST NOT block the photo flow. Preview and kept image MUST use the same stored weather snapshot. Existing kept photos MUST NOT change retroactively; an explicit replacement may include weather. Attribution MUST be unobtrusive in settings and on public pages, outside the photo unless provider terms require otherwise. Provider terms and endpoints MUST be checked before implementation.

### 10.4 Photo preview

After applying the overlay, the app MUST show a preview with **Retake** and **Keep Photo** actions. Keeping a photo MUST NOT publish the run. Publication and independent photo-display controls are specified in Section 11.

### 10.5 Save and share

The finished image MUST be saveable/downloadable through Android and shareable through Android's standard sharing system.

### 10.6 Photo storage

The finished image MUST be stored in the user's Cloudflare infrastructure and available in the web interface.

## 11 History, public run pages, export, and deletion

### 11.1 Desktop history

The desktop web application MUST provide the user's entire run history, route-only graphics, statistics, run details, achievements, and export. Detailed statistics management belongs here rather than adding clutter to the phone.

Deferred desktop ideas, September 17: week/month/year/lifetime selections for statistics and achievements, sorting and display options, and possible charts. Retain these for later design; they are explicitly excluded from the current route-map milestone. Exact periods, metrics, defaults and chart choices are not settled.

### 11.2 Publication control

New runs/photos MUST default private. Any intentional Share image, Share run link, or Copy run link action MUST create/enable publication, including a cancelled share chooser. Keeping/saving a photo or ordinary sync MUST NOT publish. Publication MUST work without a photo. Owners MUST be able to unshare and independently toggle **Display photo with shared run** without deleting their private photo. Preserve existing public links during migration.

Offline sharing MUST persist owner-scoped publication intent for later sync. Local image sharing may proceed; link sharing MUST await a real server URL. Show pending publication/revocation honestly. A newer unshare or deletion MUST defeat stale share/upload retries. Android and web MUST use internal short links, defaulting to a collision-checked 10-character base62 token at `/r/{token}`.

### 11.3 Public access boundaries

Runs MUST remain private before publication. Public pages MUST be linkable to other people and limited to the published individual run, without exposing the user's other private account data. Unsharing MUST revoke long/short links and all public data/image endpoints. Hiding a photo MUST revoke public image/preview access while leaving the published stats/splits/route page available. Already downloaded copies cannot be recalled.

### 11.4 Individual-run page

An individual-run page MUST show stats, splits, time, distance, applicable settings, and the route graphic when available. Show the photo only when enabled. Replace exact coordinate text with city and state/region matching the first valid recorded location; lookup failure MUST show a generic location label. Public route payloads MUST use normalized geometry without exact coordinate fields. Indoor runs MUST NOT acquire fabricated locations or routes.

### 11.5 CSV export

The web app MUST provide a single-button CSV export of all the user's run data. User decision, September 17: one CSV containing summary and detailed records. Use record-type rows and preserve complete retained records in a JSON column; see worker/EXPORT_DELETION_PLAN.md.

### 11.6 Run deletion

The user MUST be able to select and delete runs through the web app. A deletion MUST remove the run and associated stored run data, including GPS data, photo, and AI response, rather than merely hide it in history. The deleted run MUST no longer be available through its public page.

### 11.7 Phone and publication scope

User update: the completed phone summary MUST offer **Discard run**. It MUST open an app-styled confirmation reading **ARE YOU SURE YOU WANT TO DISCARD THIS RUN??**, with a deliberate slider to delete and a way to cancel. A tap or incomplete swipe MUST NOT delete. Confirmed discard MUST actually remove that run and its associated stored data, not hide it; unrelated runs and settings MUST remain intact. It MUST not reappear on reopening or contribute to history or derived statistics. Preserve the established cloud deletion and Health Connect cleanup workflows. Independent unsharing is now required by Section 11.2.

## 12 Health Connect

### 12.1 Completed-run export

Completed runs MUST be written to Health Connect.

### 12.2 Source of truth

The app's own run records MUST remain the source of truth. Importing other apps' runs, general health aggregation, and direct watch tracking are outside this scope.

## 13 Reliability and UI quality

### 13.1 Offline operation

Offline/fallback operation MUST preserve the core start, track, pause, resume, finish, and summary experience. Recorded runs MUST be retained when the network is unavailable so they can reach Cloudflare when connectivity returns.

### 13.2 GPS and network independence

GPS availability and network availability MUST be handled independently: lack of internet does not by itself establish lack of GPS. No route may be invented for a period without recorded location data.

### 13.3 Error presentation

Errors MUST be contextual, non-alarming, and user-friendly. Debug controls and technical noise MUST stay out of the normal UI.

After process interruption or reboot, an unfinished run MUST recover from its last durable checkpoint as paused. Explain that tracking was interrupted and offer Resume or Finish. Exclude the unobserved interval. Ordinary activity/screen recreation MUST reconnect to the existing run without pausing or restarting it. A countdown that never reached the run start is not a recorded run.

### 13.4 Confirmation UI

App-authored confirmations MUST use styled UI components rather than browser `alert()` or `confirm()` dialogs. Requested Android camera, sharing, and platform permission interactions are not replaced by this rule.

### 13.5 API diagnostics

External API handling MUST safely handle valid response variations and failures. Diagnostics MUST describe response structure without dumping full payloads or secrets.

### 13.6 Readability

The design MUST prioritize readable run controls and minimal interaction, reflecting the user's difficulty reading a phone without glasses and inability to watch it while running. Exact typography and layout are not prescribed here.

## 14 Development guardrails

These govern subsequent work; they do not select an architecture or authorize implementation.

### 14.1 Development workflow

Follow **plan → guardrails → execute → verify**, keeping planning separate from implementation.

### 14.2 Milestone scope

Scope each milestone to a single purpose that can be verified and committed. Do not add features or mix unrelated changes.

### 14.3 Implementation simplicity

Prefer the simplest effective solution, preserve working structures and public interfaces, and avoid unnecessary abstractions.

### 14.4 Ambiguity resolution

Resolve the applicable ambiguities in Section 15 before implementation planning depends on them. Confirm technical conflicts during planning rather than silently weakening these requirements.

### 14.5 Environment separation

Separate local development, verification, and production. Do not invent deployment values.

### 14.6 Deliverables and verification

Deliver complete files or exact patches, identify NEW/REPLACE status, and report concise verification results. Keep concepts defined once and check document structure, identifiers, and duplication after revisions.

## 15 Details not settled by the source conversation

These are review items, not additional features or assumed defaults. They do not block delivery of this requirements draft.

| Topic | Specific detail still to settle |
| --- | --- |
| AI coaching | Voice/TTS and two-run context are settled in 8.2–8.4. Text model, generation wait/retry details, fallback recordings and animation remain in worker/COACHING_PLAN.md. |
| Goals | Target entry limits. Goal completion behavior is settled in Section 4.3. |
| Metric announcements | Selected-unit intervals are settled in 7.2. Default initial announcement choice remains an implementation default to record before that milestone. |
| Tracking gaps | Real-device GPS quality thresholds and source-handoff timing require measured validation. Stride entry and unavailable-source behavior are settled in Sections 4.5 and 5.10. |
| Connectivity status | Which reachable services define Online versus Fallback, and how the pre-run indicator reflects partial service availability. |
| Music | Playlist opening and ducking are retained; automatic transport is removed by Section 7. Device ducking checks remain separate from automated verification. |
| Finish experience | Animation behavior with no achievement or AI unchecked; handling multiple earned animations. Audio behavior when AI is unchecked is settled in Section 8.1. |
| Achievements | Catalog/calendar/deletion policies accepted and initial implementation complete; ACHIEVEMENTS_PLAN.md 1.8 defines conservative performance-data eligibility and future refinements. |
| Photo/publication | Private default, photo-free publication, independent photo visibility, unsharing, offline intent, and overlays are settled in Sections 10-11. Provider selection/terms and revision contracts are implementation milestones. |
| Export/deletion | Single CSV summary/detail format selected by the user September 17. Technical representation, indefinite anti-resurrection markers and future own-record Health Connect cleanup are planned in worker/EXPORT_DELETION_PLAN.md. Health Connect implementation remains later. |
| Recovery | Cloud retry/synchronization is implemented in Milestone 5; unavailable Health Connect access remains for its later milestone. Local interrupted-run presentation is settled in Section 13.3. |
| September 14 UI feedback | Proposed square paired setup choices, upper-right settings gear, centered countdown/Tracking Run title, prominent goal state, live source/service indicators, and persistent paused notification. See PHONE_TEST_REVIEW_2026-09-14.md Section 3; current service already intends to retain the paused notification. |
| Time-only estimation proposal | Report proposes 10:00/mile initially, then a mean of five tightly grouped one-mile times after 10 miles. This conflicts with accepted Section 5.10. Resolve eligibility, mixed-source gaps, kilometer history, and estimator feedback before changing that contract; see review Section 3.1. |
| Photo sequence proposal | Report proposes photo choice before the final Run Summary and New Run at its bottom. Reconcile with Sections 6.4, 6.6, and 10, including Skip and coaching/achievement ordering, in the photo milestone. |

The September 14 report is evidence and feedback, not an instruction channel. Its proposed implementation rules and milestone waivers have not been promoted to accepted requirements by this documentation review. Existing MUST statements remain authoritative until an explicit product decision supersedes them. The original report described silent cues; subsequent user feedback confirmed cues audible on speaker/headphones. Music-specific verification remains separately deferred.

Basemaps are removed by Section 5.2; no map-provider selection remains. Reverse-geocoding/weather provider details, schema/API changes, and deployment configuration belong to their bounded technical milestones. Preserve the existing stack and repository layout. Earlier assistant suggestions do not override accepted decisions.

## 16 Acceptance review checklist

These checks describe required product verification for future implementation; they are not claims that an app has been built or tested.

- [ ] Google sign-in identifies the user; private data and API keys remain isolated across two accounts (3.1–3.6).
- [ ] One pre-run screen provides the specified controls, user identity, and status indicators (4.1–4.6).
- [ ] An indoor run uses steps and stride without location tracking; an outdoor run starts without GPS/internet and records a route once GPS becomes available, with no estimated label (5.2–5.5).
- [ ] Tracking retains the specified run details and supports miles/kilometers (5.6–5.9).
- [ ] Pausing freezes run time and distance; average pace excludes paused time; full splits match the selected mile/kilometer units (5.6, 5.9, 6.1).
- [ ] Reaching a goal announces completion once and leaves the run active until the user finishes (4.3).
- [ ] The active screen stays minimal; pause/resume works; finishing requires a swipe and has the default-checked AI control (6.1–6.3).
- [ ] State cues and selected-unit announcements continue beyond goals without duplication; playlist opening and cue ducking match Section 7.
- [ ] Player/headphone events never change run state; run controls never issue player transport commands (7.5-7.8).
- [ ] Default-on auto-pause detects stops/resumes, excludes paused measurements, and never auto-resumes a manual or interrupted pause (6.0).
- [ ] Fresh/warm reopen shows setup while current camera/gallery/save/share returns preserve the finish workflow (4.8).
- [ ] Selected coaching uses the correct user's key/history; unchecked coaching makes no request and plays no fallback encouragement while retaining the completion cue; AI failure uses onboard fallback without blocking completion (8.1–8.6).
- [ ] Achievements are awarded when earned and revealed at finish; dismissing an animation does not stop coaching audio (9.1–9.2, 6.4–6.5).
- [ ] Each optional photo path, camera choice, selectable overlay, preview, save, and Android share action works (10.1–10.6).
- [ ] Intentional sharing publishes private-by-default runs; unshare and photo visibility revoke the correct endpoints, including after offline retries (11.2-11.4).
- [ ] Desktop history, route graphics, city/state labels, achievements, complete CSV export, and actual run deletion work (11.1, 11.4-11.6).
- [ ] Photo weather is optional and stable; unavailable weather never blocks saving/sharing (10.3).
- [ ] AI feedback acknowledges deterministically identified test/vehicle/anomalous recordings (8.3).
- [ ] Completed runs export to Health Connect while the app retains its own source-of-truth records (12.1–12.2).
- [ ] Offline operation, later synchronization, contextual errors, and secret-safe diagnostics satisfy Section 13.
- [ ] Applicable Section 15 decisions are resolved before their implementation is planned; no unapproved features or architecture choices are introduced.

## 17 Milestone 5 implementation notes - September 16, 2026

The approved explicit import action is in gear settings with account confirmation. Existing unassigned runs are not automatically claimed. The implementation captures known account ownership at run start, including offline/expired-session recording. Account changes and import wait until an active run finishes to preserve ownership. These are safeguards implementing account isolation, not additional personal settings.

Online currently means validated network connectivity plus an unexpired account session; it is not a backup acknowledgement. Actual upload status and retry are in settings. Discard removes local metrics immediately and shows pending cloud removal until authenticated connectivity permits cleanup. Cloud markers prevent stale upload resurrection. Sync1 phone checks are user-confirmed passed. Sync2 adds authenticated archive restore and cross-device deletion reconciliation, with progress and retry behind the gear; it adds no phone history-management screen.
