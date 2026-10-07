# 1 Milestone 12: indoor auto-pause correction

## 1.1 Scope and authority

September 28, 2026. the owner approved AUTO_PAUSE_AND_MILESTONES_PLAN.md and subsequently authorized proceeding through automated tests without the model-switch pause. This milestone changes only motion evidence and its regression coverage. Independent milestone selections and goal-audio priority remain Milestones 13 and 14. No physical-phone installation, deployment, paid provider calls, commit or push.

## 1.2 Implementation

AutoPausePolicy no longer treats registered/silent step counters as stationary. TrackingInput ingests counter changes and detector observations at callback time; one batch is one observation, not invented per-step timing. Positive steps veto stillness. Silent/unavailable counters do not prevent usable GPS-only movement. Unknown/ambiguous/conflicting evidence cannot produce a transition.

MotionWindow.kt adds a pure Kotlin bounded acceleration classifier. SensorAdapters requests approximately 20 Hz acceleration with zero batching latency and optional step detection only for auto-pause-enabled observation. A one-second rolling window retains at most 32 samples, requires at least 15 samples and 900 ms coverage, and rejects gaps above 150 ms. Vector variation removes the constant gravity component without integrating acceleration into distance. RMS at or below 0.12 m/s^2 is quiet, at or above 0.8 is moving, and between is unknown. These are conservative starting values, not phone-calibrated guarantees. Summaries are emitted at most twice per second; raw samples are ephemeral, never saved or logged. No new permissions or dependency.

Fresh complete stationary evidence starts the existing five-second dwell. Two seconds of sustained movement resumes only an automatic pause. Between-tick steps, acceleration movement and coverage interruptions reset candidates. Service messages retain registration generations; motion observation continues through automatic pauses and stops on manual pause, finish and teardown. Detection never increments distance. Registration changes and transitions reset motion and distance baselines. Disabled auto-pause does not register detector/acceleration listeners.

## 1.3 Automated gate

From android/, run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

The first packaging and test attempts hit existing read-only generated build directories. Cleared only their ReadOnly attributes; no source edits or deletions were used for recovery. The subsequent full gate passed with 101 JVM tests. A final between-tick coverage regression and correction were then added; final gate results are recorded below.

Tests cover no initial callbacks, manual resume followed by sparse batches, preserved batch distance, incomplete/stale/ambiguous acceleration, quiet/moving synthetic traces, callback gaps, multiple callbacks between ticks, counter reset/out-of-order delivery, GPS-only motion, conflicts, disabled behavior, manual and interrupted protection, and pause/time/distance/route/Health Connect accounting.

Emulator verification installs only on explicitly selected emulator-5554. AutoPauseServiceTest uses synthetic motion through the actual serial service, background timer, storage and lifecycle. It first asserts eleven seconds of callback silence leaves the run active, then supplies quiet evidence, resumes via steps, checks frozen distance and stored reasons, repeats stopping, and verifies manual shutdown/recovery. The fake overrides motion registration so real emulator acceleration cannot mask a regression.

## 1.4 Physical acceptance remains open

BUG-006 must remain open until actual carried-phone running confirms the correction. Record phone model, Android version and placement. Run indoors for several minutes including the original first-ten-seconds and manual-resume sequence; no false pause. Stop/resume repeatedly; confirm cues and frozen paused time/distance. Repeat screen-off and auto-pause disabled. Missing sensors or delayed callbacks must leave tracking active rather than assume a stop. Check sensor battery impact. A phone resting on equipment cannot represent the runner's motion.

Do not ask the owner to install this intermediate artifact solely for the milestone. Preserve the agreed combined Milestone 14 APK handoff. Time-announcement phone feedback is positive; this milestone does not alter announcements.

## 1.5 Final results

The final application source passed the full Gradle gate with 102 JVM tests, zero failures/errors/skips, successful app/test assembly, and lint at zero errors/20 warnings. Diff whitespace checks passed. Windows recovery was limited to generated-directory attributes.

Both initial 11-case emulator batches passed the auto-pause service test and nine other cases, but the notification finish check timed out while the run was FINISHED. The old test selected coaching and allowed only 30 seconds for foreground ownership to end, despite intentional audio/coaching retention. Its harness now uses FINISH_WITHOUT_COACHING and allows 90 seconds for cue completion; all notification assertions remain. The changed test APK and lint passed, followed by all four TrackingReliabilityTest cases in 32.712 seconds. All 11 distinct targeted cases therefore have passing final results across the batch and focused rerun, not a claimed clean 11-case first pass. No production audio/coaching behavior was changed to satisfy this test.

Internal verification APK: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-28_16-28-01_EDT.apk, 40,224,492 bytes; SHA256 6112EA9022223AC390EECF7558CD4E0C84B34CDD9EAEA295A9D05330810F5BD6. This is not the combined Milestone 14 phone handoff.

Milestone 12 implementation and local automated verification are complete. BUG-006 remains open for physical acceptance. Next bounded milestone is 13, independent time/distance selections; milestone 14 then adds goal-audio arbitration and the combined handoff. No UI or Worker changes required visual/browser testing in this milestone.
