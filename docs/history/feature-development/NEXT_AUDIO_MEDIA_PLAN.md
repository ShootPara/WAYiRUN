# WAYiRUN — Audio, music, notification, and discard follow-up

> **HISTORICAL RECORD:** This document is preserved as evidence of the project's development. Statements describing it as controlling or authoritative applied during that phase and do not override current repository documentation or source.

## 1 Latest user findings and accepted changes

Latest disposition: the user accepts active-session music controls as implemented and defers phone testing. Do not reopen the restricted-setting investigation as a prerequisite for development. Playlist setup/launch remains separate work; historical device-check requirements below are retained as future verification, not a current blocker.

Execution update: the three bounded passes below have been implemented and regression-tested in `0.1.0-dev-music1`; see TASKS Section 7.4 and MUSIC1_PHONE_RETEST.md. The later explicit decision is to keep the run going after **any** music pause during a cue, even if music stays paused. That supersedes the narrower pause-cause distinction in this original plan. Music off at startup remains independent; playlist launch is not implemented.

The user confirms audible cues through the phone speaker and headphones. The exact installed APK was not independently inspected. This resolves the earlier report of complete silence at the functional level; it does not verify every offline/error path. Music currently continues at full volume during cues, and music/run pauses are not linked. Audio1 never implemented audio focus or media-session linkage, so those are missing milestone capabilities rather than regressions in an existing music adapter.

Accepted changes are now in REQUIREMENTS 7.1 and 7.6–7.9 and 11.6: detailed goal/completion speech; ducking with a cue-caused pause exception; prominent ongoing notifications; and actual summary discard behind the exact requested confirmation and a slider. Previously deferred photo and estimation proposals are unchanged.

## 2 First bounded implementation — Cue content, ducking, and prominence

### 2.1 Files and behavior

- `android/app/src/debug/java/com/example/runningapp/tracking/RunCueQueue.kt`: format goal and completion from event active time/distance and run units, in the requested order. Preserve one-shot events and queued fallback behavior.
- `RunCues.kt` in the same directory: own transient `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` across each speech/tone lifecycle, including asynchronous failures and timeout. Release focus reliably without manipulating master/media volume. Handle denied/lost focus explicitly. Review the existing 15-second timeout for longer metric announcements so valid speech is not truncated.
- `TrackingService.kt`: supply the run's unit snapshot to cues and keep the service eligible for focus while completion audio drains, including screen-off finish. Coordinate notification lifetime with terminal playback without keeping an active-run notification indefinitely after finish.
- Notification channel: replace the prototype's LOW default with an appropriate prominent channel policy. Existing channel importance cannot be raised by merely calling create again; offer the channel settings path for user-controlled settings and evaluate a one-time channel migration without bypassing a user mute. Test actual Moto G grouping. Do not promise a fixed top position or spam heads-up updates.
- Tests under `src/testDebug/` and `src/androidTest/`: known time/distance/pace examples in miles and kilometers; zero distance; focus grant/denial/loss and cleanup; rapid cues; asynchronous failure; notification behavior for fresh and existing installs.

### 2.2 Verification boundary

Run unit tests, debug build, lint, and relevant emulator tests. On the phone, verify duck/restore with YouTube Music on speaker and headphones, screen off, and on finish; verify notification grouping and expanded metric speech. Audio focus requests cannot compel every audio producer to duck. Record observed player behavior.

No media-session linkage, deletion, photos, estimator, cloud, or schema changes in this first pass. The missing two-way pause remains visibly pending until Section 3 is implemented.

## 3 Next bounded implementation — Milestone 4 linked playback

Add a small debug YouTube Music media-session adapter and user-enabled notification-listener access. Wire UI/player/headphone pause/resume through the existing controller, suppress command echoes, and detach after terminal finish. Never attach unrelated players automatically. Session loss must not fabricate resumed playback or restart a finished run.

The cue exception needs deliberate coordination with audio focus. A raw playback callback does not establish why a player paused; a broad “ignore pauses while speaking” rule would swallow manual headphone pauses. Test that case explicitly and report any unresolved player limitation before claiming the accepted behavior is complete.

Playlist configuration and absent-player behavior remain the Milestone 4 product entry decisions. Proposed minimal first scope: control an already active YouTube Music session, explain missing access/session, and keep standalone tracking usable. This proposal does not silently remove the later requested playlist-start capability or mark all of Milestone 4 complete.

Verify pauses from app, player, notification shade, and headphones; resume; repeated callbacks; cue focus; phone interruptions; service recovery; and player resume after finish. Android notification-listener access requires the user's system-settings grant at execution time.

## 4 Separate bounded implementation — Discard completed local run

Add Discard run to `ui/WayirunApp.kt`'s completed summary, with the exact uppercase confirmation, cancel action, and full-swipe confirmation. Add a serialized discard command in `TrackingService.kt`; verify the requested ID is still the completed run and disable duplicate submissions while saving.

In `storage/RunDatabase.kt`, transactionally delete the run and all owned rows (check every foreign-key cascade), preserving unrelated runs and preferences. Clear matching controller/checkpoint/UI references only after successful deletion so no stale writer resurrects the run. On storage failure, retain the summary and report failure honestly. Return to setup after success. Future cloud and asset deletion remain part of their own integration milestones.

Tests: tap/short swipe/cancel preserve data; full swipe deletes precisely the selected run; duplicate confirmation is safe; unrelated records survive; deletion failure keeps the run; reopening and delayed callbacks do not restore it. Do not delete any real user run during development verification without its explicit selection/confirmation; use test-owned records.

## 5 Guardrails and outstanding decisions

Implement and verify each bounded pass separately. Preserve the repository and user changes; no commit, push, remote changes, or deployment. Keep requirements authoritative and update TASKS with actual results. Notification rank is platform-controlled; media pause-cause attribution and actual ducking are device/player validation issues. Playlist behavior needs a product decision before its implementation. The user-confirmed cue audibility should not be reopened as an assumption of total silence.

Platform references: [Android audio focus](https://developer.android.com/media/optimize/audio-focus), [notification channels](https://developer.android.com/develop/ui/compose/notifications/channels).
