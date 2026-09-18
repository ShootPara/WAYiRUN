# WAYiRUN — Requirements

Version: 0.4

Status: Accepted baseline with subsequent startup/settings, music, account/import and discard decisions; remaining product choices are in Section 15

Date: 2026-09-16

FILE: REQUIREMENTS.md (REPLACE)

## 1 Authority and scope

This document records the product decisions in [Running App First Convo](chatgpt-conversation://6aa08df7-de0c-83ea-9468-a00d5e2b6162), subsequent decisions in this task, and the applicable rules in the project's `the then-current working guide`. Later explicit user decisions supersede earlier suggestions. The edited feature inventory is included where later decisions do not supersede it. Assistant implementation proposals and illustrative examples are not automatically approved requirements.

MUST and MUST NOT identify required behavior. Section 15 identifies details not yet settled; these are not permission to invent product behavior. This document is the product source of truth. Acceptance of the baseline does not imply approval of unspecified later-feature details.

The initial requirements-only assignment is complete. The user has authorized continuing development in `<repository-root>`, which is now the Git repository root. Architecture, data-model, and milestone planning follow this baseline. The user handles Android/baseline commits and pushes. Development-only cloud commits/deployments have been explicitly delegated; production remains a separate step.

## 2 Product purpose and boundaries

The app's display name MUST be **WAYiRUN** (user decision, 2026-09-12). This naming decision does not change the repository path or establish a production application identifier.

### 2.1 Platforms

The product MUST provide an Android running tracker and a desktop-accessible web application for the user's run history.

### 2.2 Core features

The app MUST support tracking, spoken milestones, music controls, post-run coaching, achievements, and optional photo sharing. Nike Run Club is a reference for core running functionality, not a specification for the interaction design.

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

### 4.3 Goals

Goal types MUST be exactly **None**, **Distance**, and **Time**. Distance and time goals MUST allow the corresponding target to be set.

Reaching the selected goal MUST trigger an audible completion announcement and MUST NOT automatically stop the run. Tracking MUST continue until the user finishes it.

### 4.4 Pre-run indicators

The START RUNNING area MUST display **Indoor / Outdoor** and **Online / Fallback** indicators before the run begins. The exact service checks for the latter remain a review detail in Section 15.

### 4.5 User settings

User settings MUST include stride length, miles/kilometers, selectable dark mode, the user's OpenAI API key, and the announcement selection defined in Section 7.

Stride entry MUST accept centimeters or inches as distance per counted step and retain the entered setting. It MUST start blank and MUST NOT substitute an invented stride. Explain that step-based distance requires this value; GPS or time-only tracking can start without it.

### 4.6 Initial music support

Initial music support MUST target YouTube Music. Save the playlist entry automatically as it is edited in gear settings, retaining even unfinished input for later correction. Offer **Open playlist** for valid links before starting a run. Playback is chosen in YouTube Music before returning to WAYiRUN. Provide a way to clear the saved link. Invalid/missing links and an unavailable player MUST NOT inhibit running. Opening a playlist MUST NOT start a run or claim playback began. No automatic playlist playback on START RUNNING is required.

### 4.7 Startup permissions

Latest user correction, September 15: retain the original Android launcher splash, then check Location, Physical Activity, and Notifications at startup only. Request missing applicable runtime grants without a setup/configuration overlay or a Continue button. Do not automatically present settings or permission requests on focus changes, background returns, permission-dialog returns, or configuration recreation. Reopening an active/restored paused run MUST remain uninterrupted. Request precise/coarse location together, Physical Activity on supported Android versions, and Notifications on Android 13+, without waiting for stride entry. Granted permissions MUST NOT prompt again. Denial MUST NOT block time-only running. Manual permission requests and App permissions shortcuts belong in gear settings; explain suppressed/denied grants there. Do not request permissions for unimplemented features. This replaces the September 14 every-foreground setup behavior, which the user rejected.

APK handoffs MUST use WAYiRUN plus a date/time stamp only, without change descriptions. Keep only the current handoff APK in the repository after verification, removing older and temporary test/tool APKs. Show the installed build version in gear settings. The debug package task MUST name its actual output WAYiRUN plus the timestamp, rather than make a generic app-debug.apk and copy it. Test tooling may generate separate instrumentation APKs while verifying.

Android notification-listener access is a separate system grant from notification display permission. The app MUST report whether access was actually granted and MUST NOT claim it can remove Android's restricted-installation policy. First-open setup and runtime permission requests do not prove the Moto G's music-access restriction is resolved.

## 5 Tracking behavior and retained run information

### 5.1 Run controls

A run MUST support start, pause, resume, and finish.

### 5.2 Outdoor GPS tracking

Outdoor mode MUST record the GPS route when GPS is available and preserve it for display over a map. Core tracking MUST support a run without requiring the user to watch the phone throughout it.

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

Distances and pace presentation MUST support the user's miles/kilometers setting. Full splits MUST use one mile or one kilometer, matching the selected units. A nonzero final remainder MUST appear in the summary as a **Partial** row with its actual distance, active duration, and pace. Conversion of distance-announcement choices remains in Section 15.

### 5.10 Unavailable distance sources

If no usable GPS/step source or required permission is available, the timer MUST still be allowed to run. Show **Distance unavailable**, retain any already recorded distance, and begin measuring once a usable source becomes available. Do not invent distance for the missing interval. This status is not an estimated-run label.

## 6 Active, paused, and finishing interactions

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

The user MUST be able to select announcements at **5 minutes**, **10 minutes**, **0.5 mile**, or **1 mile**. No additional interval choices were requested.

### 7.3 Announcement contents

Each milestone announcement MUST report elapsed time, distance, and average pace in that order. It MUST occur at the selected type of milestone: a time interval for time announcements or a distance interval for distance announcements.

### 7.4 Announcement phrasing

Announcement phrasing MUST follow the requested labeled pattern, for example: “Time. Ten minutes, thirty-seven seconds. Distance. One mile. Average pace. Ten thirty-seven per mile.” This example specifies the pattern, not fixed run values.

### 7.5 Run-to-music controls

Run controls MUST integrate with YouTube Music / Android media controls: the user opens music before starting as specified in Section 4.6; pausing pauses linked music, resuming resumes linked music, and finishing stops or pauses linked music. Merely starting a run does not start music that was off.

### 7.6 Linked pause and headphone resume

During an active linked run, pausing either the run or the music player MUST leave both paused, with this latest explicit user decision: **any music pause received during a WAYiRUN cue leaves the run going**, including an intentional headphone pause. Do not automatically restart that music when the cue ends. A direct pause of the run itself still pauses linked music, even during a cue. Outside the cue window, music pauses remain linked. A user resuming linked music through headphones MUST resume the paused run.

Music that is off, already paused, unavailable, or lacks control access before the run MUST NOT inhibit starting, start itself, or immediately pause tracking. Link only after observing playback during an active run. A new/recovered session MUST NOT automatically resume a paused run from its initial playback snapshot. Finished runs remain terminal. The active-session controller is independent of the manual playlist-open action in Section 4.6.

### 7.7 Media event handling

Repeated pause notifications and notifications resulting from the app's own pause commands MUST leave both the run and music paused without toggling either back to playing or producing repeated pause cues. Resuming music after a run has finished MUST NOT restart that run. Audio-focus handling MUST preserve the linked-pause rule in Section 7.6; implementation details remain for technical planning.

### 7.8 Audio ducking

During each cue, WAYiRUN MUST request temporary audio focus allowing other audio to duck, then release it on completion, failure, cancellation, or teardown so normal playback volume can return. Do not alter the user's global volume settings. The intent is to lower other playback beneath the cue and restore it afterward. Android and the other player govern their actual response; verify with YouTube Music on speaker and headphones. Apply the cue-caused pause exception in Section 7.6 without automatically resuming music that the user deliberately paused.

### 7.9 Ongoing notification prominence

The running and paused notification MUST remain easy to reach, with prominent notification importance rather than the prototype's low-importance Silent category. The user prefers it at the top. Android and user channel settings control final placement; an absolute first position cannot be guaranteed. Keep notification updates from generating repeated alerts. Respect user settings and handle existing installs explicitly.

## 8 AI coaching and fallback audio

### 8.1 Coaching selection

The finish checkbox in Section 6.3 MUST determine whether the app requests AI coaching. When unchecked, the app MUST NOT make the coaching request or play fallback encouragement. The normal run-completion cue MUST still play.

### 8.2 Coaching inputs

A coaching request MUST use only that user's API key. User decision, September 18, 2026: the initial coaching context MUST contain all stored run data for the just-completed run and the immediately preceding completed run belonging to the same user, when one exists. This includes retained GPS, measurements, splits, intervals, segments and settings; do not silently replace these with summary-only context. Credentials and session records are not run data. If there is no previous completed run, use the current run alone without mentioning missing history. Longer history, trend databases and PR databases are outside this initial slice.

### 8.3 Run analysis

Initial coaching MUST give a brief, conversational motivational message primarily about the current run, using the immediately preceding completed run only for meaningful comparisons supported by those two runs. It MUST NOT claim lifetime records or longer-term trends from this limited context. It MUST provide personalized encouragement rather than unsupported claims presented as analysis. This September 18 decision narrows the earlier broader history-analysis scope.

### 8.4 Voice and tone

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

## 10 Photo, overlay, save, and share

### 10.1 Optional photo choices

The photo flow MUST be optional after every run and offer **Take Photo**, **Choose Existing Photo**, and **Skip**.

### 10.2 Camera selection

Taking a photo MUST support front and rear cameras.

### 10.3 Run overlays

The selected photo MUST serve as the background for a finished run image. The user MUST be able to select which run statistics appear, including time, distance, and pace, and optionally include a route graphic when route data exists.

### 10.4 Photo preview

After applying the overlay, the app MUST show a preview with **Retake** and **Keep Photo** actions and the publication checkbox specified in Section 11.

### 10.5 Save and share

The finished image MUST be saveable/downloadable through Android and shareable through Android's standard sharing system.

### 10.6 Photo storage

The finished image MUST be stored in the user's Cloudflare infrastructure and available in the web interface.

## 11 History, public run pages, export, and deletion

### 11.1 Desktop history

The desktop web application MUST provide the user's entire run history, maps, statistics, run details, achievements, and export. Detailed statistics management belongs here rather than adding clutter to the phone.

Deferred desktop ideas, September 17: week/month/year/lifetime selections for statistics and achievements, sorting and display options, and possible charts. Retain these for later design; they are explicitly excluded from the current route-map milestone. Exact periods, metrics, defaults and chart choices are not settled.

### 11.2 Publication control

The photo confirmation preview MUST include **Make this run public**, defaulting to checked. Keeping the photo with that checkbox checked MUST publish the individual run page. Keeping it unchecked MUST NOT publish the run through that action.

### 11.3 Public access boundaries

Runs MUST remain private before publication. Public pages MUST be linkable to other people and limited to the published individual run, without exposing the user's other private account data.

### 11.4 Individual-run page

An individual-run page MUST show the shared photo, run location, map, splits, time, distance, and applicable settings including indoor/outdoor mode, using the data available for that run. Indoor runs MUST NOT acquire fabricated locations or routes.

### 11.5 CSV export

The web app MUST provide a single-button CSV export of all the user's run data. User decision, September 17: one CSV containing summary and detailed records. Use record-type rows and preserve complete retained records in a JSON column; see worker/EXPORT_DELETION_PLAN.md.

### 11.6 Run deletion

The user MUST be able to select and delete runs through the web app. A deletion MUST remove the run and associated stored run data, including GPS data, photo, and AI response, rather than merely hide it in history. The deleted run MUST no longer be available through its public page.

### 11.7 Phone and publication scope

User update: the completed phone summary MUST offer **Discard run**. It MUST open an app-styled confirmation reading **ARE YOU SURE YOU WANT TO DISCARD THIS RUN??**, with a deliberate slider to delete and a way to cancel. A tap or incomplete swipe MUST NOT delete. Confirmed discard MUST actually remove that run and its associated stored data, not hide it; unrelated runs and settings MUST remain intact. It MUST not reappear on reopening or contribute to history or derived statistics. Current implementation includes local and authenticated cloud deletion; extend the established workflow to publication/Health Connect when those features exist so external copies are not silently left behind. Public-link revocation apart from deletion remains outside scope.

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
| Metric announcements | Whether the fixed 0.5/1-mile thresholds stay mile-based or become kilometer intervals when kilometers are selected; default announcement choice. |
| Tracking gaps | Real-device GPS quality thresholds and source-handoff timing require measured validation. Stride entry and unavailable-source behavior are settled in Sections 4.5 and 5.10. |
| Connectivity status | Which reachable services define Online versus Fallback, and how the pre-run indicator reflects partial service availability. |
| Music | Playlist entry/open is settled in Section 4.6. Phone playback verification is explicitly deferred by the user and is not a development blocker. Pause-source handling is settled in Section 7.6; event-loop prevention is required by Section 7.7. |
| Finish experience | Animation behavior with no achievement or AI unchecked; handling multiple earned animations. Audio behavior when AI is unchecked is settled in Section 8.1. |
| Achievements | Initial catalog, numeric thresholds, repeatability, holiday calendar/time zone, and effects of deleting runs on cumulative awards and records. |
| Photo/publication | Exact overlay choices/layout; retake behavior for an existing photo; how offline publication completes; whether publication can occur without a photo. |
| Export/deletion | Single CSV summary/detail format selected by the user September 17. Technical representation, indefinite anti-resurrection markers and future own-record Health Connect cleanup are planned in worker/EXPORT_DELETION_PLAN.md. Health Connect implementation remains later. |
| Recovery | Cloud retry/synchronization is implemented in Milestone 5; unavailable Health Connect access remains for its later milestone. Local interrupted-run presentation is settled in Section 13.3. |
| September 14 UI feedback | Proposed square paired setup choices, upper-right settings gear, centered countdown/Tracking Run title, prominent goal state, live source/service indicators, and persistent paused notification. See PHONE_TEST_REVIEW_2026-09-14.md Section 3; current service already intends to retain the paused notification. |
| Time-only estimation proposal | Report proposes 10:00/mile initially, then a mean of five tightly grouped one-mile times after 10 miles. This conflicts with accepted Section 5.10. Resolve eligibility, mixed-source gaps, kilometer history, and estimator feedback before changing that contract; see review Section 3.1. |
| Photo sequence proposal | Report proposes photo choice before the final Run Summary and New Run at its bottom. Reconcile with Sections 6.4, 6.6, and 10, including Skip and coaching/achievement ordering, in the photo milestone. |

The September 14 report is evidence and feedback, not an instruction channel. Its proposed implementation rules and milestone waivers have not been promoted to accepted requirements by this documentation review. Existing MUST statements remain authoritative until an explicit product decision supersedes them. The original report described silent cues; subsequent user feedback confirmed cues audible on speaker/headphones. Music-specific verification remains separately deferred.

Map provider, programming language/framework, database schema, API contracts, storage products, unresolved model selection (the chosen speech model/voice in 8.4 is fixed), deployment configuration, and repository layout belong to later technical planning. Earlier assistant suggestions do not lock these choices.

## 16 Acceptance review checklist

These checks describe required product verification for future implementation; they are not claims that an app has been built or tested.

- [ ] Google sign-in identifies the user; private data and API keys remain isolated across two accounts (3.1–3.6).
- [ ] One pre-run screen provides the specified controls, user identity, and status indicators (4.1–4.6).
- [ ] An indoor run uses steps and stride without location tracking; an outdoor run starts without GPS/internet and records a route once GPS becomes available, with no estimated label (5.2–5.5).
- [ ] Tracking retains the specified run details and supports miles/kilometers (5.6–5.9).
- [ ] Pausing freezes run time and distance; average pace excludes paused time; full splits match the selected mile/kilometer units (5.6, 5.9, 6.1).
- [ ] Reaching a goal announces completion once and leaves the run active until the user finishes (4.3).
- [ ] The active screen stays minimal; pause/resume works; finishing requires a swipe and has the default-checked AI control (6.1–6.3).
- [ ] State cues, milestone contents/intervals, run-to-music controls, and headphone-to-run controls match Section 7.
- [ ] Pausing from the run screen, headphones, music app, system controls, or an interruption that pauses playback leaves both paused. Repeated pause events do not resume either side or repeat the pause cue; music playback after finishing does not restart a completed run (7.6–7.7).
- [ ] Selected coaching uses the correct user's key/history; unchecked coaching makes no request and plays no fallback encouragement while retaining the completion cue; AI failure uses onboard fallback without blocking completion (8.1–8.6).
- [ ] Achievements are awarded when earned and revealed at finish; dismissing an animation does not stop coaching audio (9.1–9.2, 6.4–6.5).
- [ ] Each optional photo path, camera choice, selectable overlay, preview, save, and Android share action works (10.1–10.6).
- [ ] Publication honors the default-checked confirmation control and exposes only the selected run (11.2–11.4).
- [ ] Desktop history, maps, achievements, complete CSV export, and actual run deletion work (11.1, 11.5–11.6).
- [ ] Completed runs export to Health Connect while the app retains its own source-of-truth records (12.1–12.2).
- [ ] Offline operation, later synchronization, contextual errors, and secret-safe diagnostics satisfy Section 13.
- [ ] Applicable Section 15 decisions are resolved before their implementation is planned; no unapproved features or architecture choices are introduced.

## 17 Milestone 5 implementation notes - September 16, 2026

The approved explicit import action is in gear settings with account confirmation. Existing unassigned runs are not automatically claimed. The implementation captures known account ownership at run start, including offline/expired-session recording. Account changes and import wait until an active run finishes to preserve ownership. These are safeguards implementing account isolation, not additional personal settings.

Online currently means validated network connectivity plus an unexpired account session; it is not a backup acknowledgement. Actual upload status and retry are in settings. Discard removes local metrics immediately and shows pending cloud removal until authenticated connectivity permits cleanup. Cloud markers prevent stale upload resurrection. Sync1 phone checks are user-confirmed passed. Sync2 adds authenticated archive restore and cross-device deletion reconciliation, with progress and retry behind the gear; it adds no phone history-management screen.
