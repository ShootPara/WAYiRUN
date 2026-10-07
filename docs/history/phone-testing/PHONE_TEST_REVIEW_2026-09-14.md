# WAYiRUN — Phone test review and next-step plan

## 1 Scope and authority

Update: the user subsequently authorized the bounded reliability work. Section 7 records its delivered result; Sections 2–6 retain the review and original plan for context.

Reviewed on 2026-09-14 against the current repository source and [submitted report](WAYiRUN_Phone_Test_Report_2026-09-14.md). The request authorizes document updates and planning, not app implementation. The report is preserved unchanged as evidence. Its instructions to accept the milestone, waive gates, and implement features are recorded as recommendations and proposed changes, not automatically executed or promoted to accepted requirements.

## 2 Evidence and corrections

### 2.1 Submitted phone results

Moto G 2025, Android 16; install date September 13; battery saver off, no app restrictions; precise location, Physical activity, and notifications allowed. The tester used miles and an 80 cm distance-per-step setting. That value is test context, not a product default or independently verified calibration. The exact installed APK revision was not recorded.

The report lists 11 functional areas as passing and two as deferred, with no explicitly filed failures. It reports successful basic controls, steps, screen-off operation, outdoor tracking, pause/finish, return to the app, force-stop recovery, and reboot recovery. These are tester-reported observations, not independently repeated tests.

Accuracy is unverified: there are no counted-step or measured-route figures. The GPS handoff result is inconclusive because airplane mode does not establish GPS loss. Permission changes and the dedicated offline/audio test were not tested. UI return/rotation does not by itself prove activity recreation. Device-specific battery restrictions beyond the reported settings remain untested.

### 2.2 Audio is implemented, but its phone result is unresolved

`RunCues.kt` attempts offline speech for start, pause, resume, finish, and goal reached, with a tone fallback for unavailable speech or an immediate speech error. `TrackingService.kt` invokes it after saving events. The report's statement that audio is not implemented is incorrect for current source. The reported silence is an issue to investigate, even though the tester classified it as expected.

Periodic time/distance announcements, spoken split announcements, coaching, and music linkage are not implemented. Photos are also not implemented and belong to the later photo milestone.

### 2.3 Paused notification and GPS priority

Current service code retains the foreground notification on ordinary pause, updates its text to “Run paused,” and removes it at finish. Sensors and the wake lock stop on pause. The request for a persistent paused notification therefore needs reproduction against the installed build before assuming the behavior is absent. The report does not explicitly document when a notification disappeared.

Outdoor source selection already prefers usable GPS over configured steps. Clearing stride is unnecessary for normal outdoor tracking; it was a test-isolation instruction. Future routine phone checks should retain the user's stride and use explicit source evidence when testing handoff.

## 3 Feedback disposition

| Feedback | Disposition |
| --- | --- |
| Square setup buttons; paired Label / Button / Button rows; upper-right settings gear | Proposed UI follow-up. Preserve three goal choices; do not force them into a two-choice row. Verify small screens and large text. |
| Center countdown and “Tracking Run”; prominent goal state; familiar colored icons | Proposed UI follow-up. Preserve numeric readability; pair color with text/icons. |
| Live GPS, server, and changing tracking-mode indicators | Proposed UI follow-up. Distinguish GPS capability/fix availability from the active distance source. Server status must honestly indicate unavailable/not configured until cloud integration exists; Internet access is not server connectivity. No new network service is needed for this UI pass. |
| Persistent paused notification | Investigate existing behavior and add regression coverage; fix only a reproduced lifecycle defect. |
| No audible messages | Investigate implemented state/goal cues and verify the installed artifact. Do not defer this as a wholly unimplemented feature. |
| Photo prompt, then photo/statistics Run Summary, with New Run at bottom | Proposed change for the photo milestone. Keep Take Photo / Choose Existing Photo / Skip. Reconcile ordering with existing coaching/achievement flow and the current summary-first photo entry point before implementation. |
| Time-only distance at 10:00/mile, then personal pace after 10 miles | Separate calculation proposal. Conflicts with REQUIREMENTS 5.10, which currently forbids inventing missing distance. Not part of the next reliability fix. |
| Accept and continue; deferred tests non-blocking | Report recommendation recorded. Recommend continuing the bounded reliability investigation without waiting for quantitative accuracy tests, while keeping those tests visibly unverified. Do not claim the original full verification gate passed or automatically start Milestone 4. |

### 3.1 Time-estimation decisions for its own plan

The report proposes sorting eligible one-mile split durations, finding the contiguous group of five with lowest variance, and using its mean after at least 10 accumulated miles; use 10:00/mile beforehand and recalculate as history grows. That is a candidate algorithm, not an implemented or approved calculation contract.

Before adopting it, resolve: which measured runs/splits qualify; whether steps qualify; whether estimated distance counts toward the 10-mile threshold or training set; how kilometer runs contribute exact one-mile intervals; whether paused or mixed-source splits qualify; whether estimation covers every missing-source interval or only entirely time-only runs; and when the chosen pace is frozen for a run. Avoid a feedback loop in which generated distances train the estimator. Deterministic tie-breaking and numeric precision can be proposed as engineering decisions once eligibility is settled. Missing GPS must never generate route points, and interruption downtime remains excluded.

## 4 Next bounded plan — Milestone 3 reliability follow-up

### 4.1 Outcome and guardrails

Reconcile reported silence and paused-notification expectations with the installed prototype, then fix demonstrated defects. This is the recommended next implementation task, pending review/authorization of this plan. Preserve current run calculations, saved data, debug/release isolation, and unrelated edits. No UI redesign, distance estimator, photo flow, music integration, cloud, authentication, migration, commit, push, or deployment in this task.

### 4.2 Files and investigation

All application paths below are relative to `android/app/`.

| File | Planned work |
| --- | --- |
| `src/debug/java/com/example/runningapp/tracking/RunCues.kt` | Trace initialization, offline voice selection, output route, immediate and asynchronous speech failure, fallback playback, and shutdown. Make the smallest evidenced fix. |
| `src/debug/java/com/example/runningapp/tracking/TrackingService.kt` | Verify each committed transition reaches audio once; investigate service/notification lifetime on running, pause, resume, finish, and recovery. Preserve paused sensor/wake-lock release. |
| `src/debug/AndroidManifest.xml` | Inspect existing TTS/service declarations; change only if required by the diagnosed defect. |
| `src/androidTest/java/com/example/runningapp/tracking/RunCuesTest.kt` (new if needed) | Exercise cue dispatch and failure handling with a small injectable playback seam if necessary. Assertions concern outcomes, not audibility inferred from mocks. |
| `src/androidTest/java/com/example/runningapp/tracking/TrackingServiceTest.kt` (new if needed) | Verify notification state/lifetime through pause/resume/finish without duplicating a session. |
| `TASKS.md`, `android/README.md`, this review | Record actual changes, verification, artifact identity, and outstanding device findings. |

First correlate installed version/build with the tested source. On the phone, check media volume/output route and the installed offline TTS voice, then reproduce all five cue types. Capture only relevant sanitized diagnostics. A source inspection cannot determine the cause of silence. Reproduce the notification case with notification permission granted and the reported battery settings before changing lifecycle behavior.

### 4.3 Meaningful verification

- Speech-ready success; not-ready/no offline voice fallback; immediate speech failure; asynchronous failure; rapid successive cues; finish playback before teardown.
- Start, pause, resume, goal, and finish dispatch once per real transition. Duplicate commands and recovered checkpoints do not replay cues. Durable save precedes playback.
- Notification remains associated with the same run on pause, shows paused text, and opens that run; resume updates it; finish removes it. Pausing still freezes metrics and stops sensor work/wake-lock use.
- Existing controller/Room/Compose tests remain passing. Run `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` from `android/`, plus relevant instrumentation tests on an awake emulator. Verify release isolation if manifest/source-set changes occur.
- On the Moto G, explicitly record speech/tone results for all five cues, including no Internet and screen off, and notification visibility while paused. Automated playback assertions do not prove audible phone output.

### 4.4 Blockers and routine decisions

No missing product decision blocks investigating existing audio and notification behavior. Phone access or a targeted tester retest is needed to close device findings; source fixes and emulator work can proceed once authorized. Exact installed-build identity and audio conditions are investigation inputs, not assumed facts. No additional credentials are needed.

Logging placement, minimal test seams, icon implementation later, and deterministic test fixtures are routine engineering decisions. Time-estimation eligibility and changing the final post-run sequence are product decisions, deliberately outside this fix.

## 5 Subsequent sequence

After the reliability follow-up, review a separate bounded UI plan for Section 3's setup/live-status changes. Plan the estimator separately if its changed product behavior is accepted and the eligibility questions are settled. Photos remain with the photo milestone. Milestone 4 remains the next numbered feature milestone, with its own playlist/player decisions and plan; this review does not authorize it.

Retain quantitative accuracy, real GPS-to-steps handoff, permission recovery, and device battery behavior as open validation. A waiver of a milestone gate is a scope decision, not evidence that an unperformed test passed.

## 6 Targeted phone retest record

Use screenshots for screen captures. Keep configured stride for ordinary outdoor testing.

| Check | Record |
| --- | --- |
| Installed build/version and install date | |
| Start / pause / resume / goal / finish cue | Speech, tone, or silence for each; output device and media volume |
| Paused notification | Visible before pause / after pause / after returning from Home; screenshot |
| Indoor steps | Counted active steps × measured per-step distance; recorded distance; difference |
| Outdoor route | Reference distance and method; recorded distance; duration/pace; full and partial splits |
| GPS handoff | Reception conditions and timing; active-source evidence; distance before/after; no airplane-mode-only conclusion |
| Permission/offline checks | Tested permission changes, connectivity, recovery, and audible results; otherwise Not tested |

Numeric comparison fields are required to claim accuracy validation. Blank fields remain unverified. Stored source intervals/route gaps need follow-up inspection; the current UI has no map or source diagnostics.

## 7 Authorized reliability result and phone retest

Implemented and verified on 2026-09-14: media-volume speech and volume-button control, serialized speech/tone output, asynchronous failure/timeout fallback, safer offline voice initialization, immediate notification display, and restored notification on reopening a recovered paused run. Build identity: `0.1.0-dev-audio1`. No database migration or later features. See TASKS.md Section 6.8 for commands, artifacts, 40 passing JVM tests, eight passing emulator tests, and lint results.

The previous adapter checked only the immediate return from speech submission. Android documents speech as asynchronous and provides completion/error callbacks; it also distinguishes media usage from UI sonification. These support the fixes, not a claim to have diagnosed this specific phone's output. [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [AudioAttributes](https://developer.android.com/reference/android/media/AudioAttributes).

ADB continued to show only the emulator after the user connected the phone. The installed phone build and audio conditions were not inspected; no phone update or data change occurred. Update to the debug APK without uninstalling/clearing data, then record:

1. With phone speaker selected and audible media volume, start an Indoor, blank-stride, one-minute time-goal run. Listen for start, pause, resume, goal, and finish. A short tone is the fallback when offline speech is unavailable.
2. Confirm the goal cue occurs once and tracking continues. Repeat pause/resume and confirm one cue per actual transition.
3. Check the paused notification remains visible and opens the paused run. Reopening after an interruption should restore a paused notification; finishing removes it.
4. Repeat cues offline and with the screen locked; record any difference. If using headphones, record that route separately.
5. If silent, record build version, media volume, output device, and whether speech or tones ever play. Connect with USB debugging authorized for targeted `WAYiRUN.Audio` diagnostics.

This is a targeted reliability retest, not a claim that measured GPS/step accuracy or deferred permission checks passed. The remaining product proposals are unchanged.
