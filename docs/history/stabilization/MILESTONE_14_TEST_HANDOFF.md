# 1 Milestone 14: goal priority and combined handoff

## 1.1 Status and scope

Code and regression tests prepared September 28, 2026. the owner requested separate implementation and test phases with a model-switch pause. No builds, tests, emulator runs or APK packaging were performed in this phase. Static review and diff whitespace checks only. Preserve all existing working-tree edits. No commit/push, remote deployment, physical-phone installation or paid provider calls.

Read REQUIREMENTS.md, TASKS.md, the then-current working guide and AUTO_PAUSE_AND_MILESTONES_PLAN.md. The current milestone completes goal/milestone audio arbitration, followed by local verification and one combined APK handoff containing Milestones 12-14. BUG-006 stays open for physical running acceptance.

## 1.2 Implementation prepared

RunEvent gains optional occurrenceActiveMs and announcementChannel fields. The controller records actual time boundaries and interpolates distance-goal occurrence inside the accepted measurement interval, while preserving the existing goal recap metrics. Split records, accumulated distance, goal state and threshold progress remain independent of audible suppression. Optional metadata retains decoding compatibility for legacy events.

RunCueQueue now receives a monotonic CueScheduler. The Android adapter uses the main Handler; tests use TestCueScheduler without sleeps. Milestones wait one second from their first arrival, without holding audio focus. State/goal/finish cues are eligible immediately. Queue busy state includes waiting milestones so idle/coaching logic cannot treat held work as complete.

Goal matching uses occurrence active time, with inclusive +/-1,000 ms tolerance, scoped to the run. Goals in an incoming batch are known before any batch milestone is accepted. A later goal removes matching queued milestones, including merged or already eligible items waiting behind other speech. Goal occurrence remains available for subsequently delivered matching milestones.

Opposite announcement channels within tolerance merge one-to-one, preferring the distance crossing's known metrics. Same-channel crossings remain separate. A bounded history of unmatched spoken channel occurrences suppresses a late opposite-channel duplicate only once. Run replacement, finish, cancellation and teardown clear held arbitration/timers as appropriate; generation checks reject cancelled callbacks. Finish discards pending milestone audio before the completion cue. Existing speech/fallback/focus behavior is retained.

The one-second arrival hold is bounded: a goal arriving after a milestone has already begun speaking cannot retract audible words. Matching still removes queued audio regardless of delivery delay, and a previously delivered goal suppresses matching late milestones. Do not describe this as unlimited tolerance for arbitrarily late sensors. Physical acceptance should include ordinary adjacent timer/sensor delivery.

## 1.3 Source and compatibility checks

Changes are RunModels.kt, RunController.kt, RunCueQueue.kt and RunCues.kt; tests extend RunCueQueueTest and add GoalOccurrenceTest, GoalCuePriorityTest and TestCueScheduler. Existing cue tests now advance fake time for interval speech. No settings, Room schema or production Worker/browser files changed in this milestone.

Repository save/archive code retains checkpoints containing snapshots and event sequence, not transient RunEvent queues. These metadata fields therefore require no archive/schema migration. Milestone 13 already passed settings preservation checks. New GoalOccurrenceTest explicitly checks legacy event JSON and recovered progress. Run the ordinary storage regressions because controller events feed the same service/persistence path.

## 1.4 Automated test gate

From android/:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Inspect actual XML results/counts and lint severity. Previous baseline was 107 JVM tests; never substitute an expected count for executed evidence. Verify all RunCueQueueTest, GoalCuePriorityTest, GoalOccurrenceTest, RunAnnouncementsTest, DualAnnouncementsTest and auto-pause/accounting regressions. Fix failures narrowly without removing assertions.

Coverage prepared: both callback orders and batch orders, inclusive tolerance endpoints and outside-window events, occurrence versus delayed recap times, miles/km and time/distance goals through actual controller output, later milestones after goal, independent same-channel batch crossings, coalesced pair removal, preservation of unrelated speech, finish/cancel/close/run replacement, cancelled callbacks, pending busy/idle behavior, fallback/focus regressions, delayed distance interpolation and checkpoint recovery. Test execution may add a targeted case if review exposes a real missing boundary.

Known Windows generated-folder ReadOnly problems should be diagnosed at the exact build path before scoped repair. Do not recursively clean source or discard user changes.

## 1.5 Emulator and final acceptance gate

Discover devices and explicitly select the emulator; previously emulator-5554. Install app/test APKs from output metadata only after successful assembly. Refuse unfinished user runs; do not clear app data. Run AutoPauseServiceTest, TrackingReliabilityTest, CoachingFinishTest, AnnouncementPreferencesTest, AnnouncementSettingsTest, RunDatabaseTest and RunScreenTest. This checks retained background observation, notification and finish/coaching sequencing, captured settings and storage/UI behavior. The notification test uses FINISH_WITHOUT_COACHING and a bounded cue-completion wait as documented in M12.

Milestone 13 already inspected both channel groups in light/dark normal/200-percent text. Reuse its visual fixture on the combined build and inspect generated screenshots; no new UI design was added. Keep provider/cloud calls stubbed or absent. Worker suites need not repeat unless verification changes a serialized stored contract.

After all required gates pass, record the actual final APK name, byte count and SHA256, along with test totals, lint warnings and screenshots. Retain one clearly identified timestamped phone handoff; archive stale generated APKs recoverably if needed, preserving source and history. Do not install on the owner's physical phone. Give him the combined artifact and a short acceptance list.

Phone checks: carried indoor running through initial startup and manual resume without false pause; repeated stop/resume and screen-off; auto-pause disabled; time-only, distance-only and both settings in miles/km; aligned time goal/time milestone and distance goal/distance milestone play goal only; simultaneous time/distance without goal gives one recap; continued milestones after goal; finish/coaching completes normally. Real movement and audible behavior cannot be certified by compilation/emulator fixtures.

## 1.6 Completion record

the owner switched models before verification as requested. The full Android gate passed: 118 JVM tests with zero failures/errors/skips, successful debug app/test assembly, and lint with zero errors/21 warnings. Git diff whitespace checks passed. The 11 new JVM cases include deterministic scheduler/arbitration coverage and goal occurrence interpolation; all existing announcement, auto-pause, accounting, cue, persistence and UI unit regressions also passed.

Fresh app and test APKs installed successfully on emulator-5554 only. All 26 targeted instrumentation cases passed in 198.834 seconds: AutoPauseServiceTest, TrackingReliabilityTest, CoachingFinishTest, AnnouncementPreferencesTest, AnnouncementSettingsTest, RunDatabaseTest and RunScreenTest. This verifies service/background motion behavior, notification cleanup, completion/coaching sequencing, preference migration, settings capture, archive round trips, run UI, finish/photo flows and audio-independent achievement dismissal. No physical phone was installed.

The combined build regenerated the four announcement-setting screenshots and they were pulled to android/app/build/verification/milestone-14. Light/dark at normal/200-percent text were inspected: both channel controls and all choices remained readable, distinct and reachable without overlap. The Compose-local fixture does not alter emulator-wide font/theme settings.

No serialized settings contract changed after Milestone 13's passing 28-test Worker compatibility gate, so Worker tests were not repeated. RunEvent occurrence/channel metadata is transient and optional; stored checkpoints do not contain event queues. GoalOccurrenceTest verifies legacy event JSON still decodes and recovery does not replay goal/milestone events.

Final combined phone handoff: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-28_17-23-55_EDT.apk, 40,257,260 bytes, SHA256 2802BFD58E27BD2654A990D319291625E58EFE3E367FC6637EA4822DE84A6016. The debug output contains one application APK plus the instrumentation APK in its separate androidTest output. Install the application APK over the existing app; do not uninstall or clear data.

Milestones 12-14 are locally complete. BUG-006 remains open until carried-phone indoor and screen-off testing passes. Physical audio checks remain for exact audible collision behavior and headset/music ducking. No deployment, commit or push occurred.
