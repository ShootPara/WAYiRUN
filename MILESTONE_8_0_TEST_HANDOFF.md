# 1 Milestone 8.0 test handoff

## 1.1 Status and boundary

September 26: Milestone 8.0 Phase 2 verification is complete. Preserve all prior milestone edits. No phone install, deployment, commit or push occurred.

This milestone adds only the pure motion policy and controller pause-reason/checkpoint support. It does not activate auto-pause in the app. Milestone 8.1 owns sensor observation, timers, the default-on gear setting captured at start, automatic cue wording and service integration.

## 1.2 Prepared implementation

New `domain/AutoPausePolicy.kt` has no Android dependencies. `evaluate` consumes monotonic time, run state/reason, enabled flag, independently maintained step registration health, latest cumulative step observation and optional GPS speed evidence. Create one policy per run and reset it on registration change, recovery and manual transitions. No evidence is persisted or replayed.

GPS requires age at most 2500 ms, horizontal accuracy at most 10 m, speed accuracy at most 0.5 m/s, and finite speed from 0 to 12 m/s. Stationary speed is at most 0.3 m/s; movement is at least 1 m/s. Values between are inconclusive. These are conservative initial engineering thresholds requiring later device acceptance, not claimed sensor accuracy.

Steps are usable based on registration/permission health, even when no event arrives. Only newer nonnegative cumulative observations aged at most 3000 ms are used. Counter decreases reset movement evidence. A cumulative jump counts as one observation, so one stray step or delayed batch cannot establish sustained movement. At least two increasing observations within the rolling three-second window establish movement; the condition must persist for two seconds before resume. This deliberately adds evidence acquisition time before the resume dwell. Stationary evidence must persist five seconds. Both usable sources must agree; otherwise a single usable source suffices. Changing the usable source set restarts dwell. No usable source produces no transition. Poll at least once per second; a gap over 1500 ms or backwards monotonic time discards old evidence.

`RunSnapshot.pauseReason` is optional with a null JSON default for old archives. New reasons are MANUAL, AUTOMATIC and INTERRUPTED. Existing `pause()` stays manual; `autoPause()` uses the same accounting closure. Manual pause during automatic pause takes ownership without repeating the pause event. `autoResume()` only resumes an automatic pause. Every unfinished recovered run becomes INTERRUPTED and requires explicit resume. Resume and finish clear the reason. Existing PAUSED/RESUMED event types remain unchanged; automatic speech is intentionally deferred until 8.1 wires the feature.

## 1.3 Test execution

Read REQUIREMENTS.md, TASKS.md, the then-current working guide and this handoff. Inspect the current diff and new files. Run `git diff --check`, then from `android/`:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Read actual JVM XML and lint reports rather than copying prior totals. Ten tests were added in `AutoPausePolicyTest` and `AutoPauseAccountingTest`. They cover five-second thresholds, GPS quality/freshness, hysteresis, both-source agreement, single-source fallback, sustained resume, isolated batches, disabled policy, source changes, timer gaps, manual override, JSON compatibility/recovery, paused distance/time exclusion, active intervals and fresh resume baselines. Existing announcement, controller and tracking tests must continue passing. Add bounded tests or corrections if review exposes a gap; do not relax failed assertions.

If an emulator is available, install only on an explicitly selected emulator and run the existing `RunDatabaseTest` and `TrackingReliabilityTest` classes to check persisted snapshots and pause/recreation regressions. Inspect instrumentation summaries, not adb exit status. No new visual checks are required because this milestone changes no UI. Do not run unchanged Worker tests or install to a phone.

The previous gate encountered locked generated output on Windows. A stale generated tree is preserved under `android/app/build/stale-locked-20260925/`. Do not recursively clean it as a routine prerequisite. Try the normal gate first and diagnose any actual lock failure before choosing an isolated build output path. Keep generated artifacts ignored.

## 1.4 Integration requirements for 8.1

Current `GpsFix` lacks speed and speed accuracy. The sensor adapter must provide reliable speed evidence (with explicit handling for devices lacking speed accuracy); never treat unavailable speed as zero. Current step registration depends on stride entry, although motion detection does not require stride. Separate observation availability from distance availability when wiring 8.1.

The service must retain sensor registrations while automatically paused, feed policy observations without passing them to distance/route accounting, reset measurement baselines and start a new route segment on resume, and process decisions serially once. Persist before cue playback. Preserve manual override and explicit recovery resume. Add service tests for permissions, source changes, batched events, screen-off timer behavior, announcement continuity and Health Connect interval export. Real-phone acceptance remains separate.

## 1.5 Completion

Record actual build/test results and artifact paths in this handoff, TASKS.md, REMAINING_WORK.md and the issue plan. Keep 8.0 pending until its verification passes. Milestone 8.1 remains a separate coding/test boundary; full auto-pause is not complete until its service and accounting checks pass.

## 1.6 Phase 2 result - September 26

`git diff --check` passed with line-ending warnings only. The first Android gate reached Kotlin compilation and then encountered the known Windows file handle in generated `packageDebug` output. After stopping Gradle and moving the verified generated `app/build` tree aside, the same gate completed successfully from a fresh output tree.

All 88 JVM tests passed with zero failures, errors or skips, including the ten new `AutoPausePolicyTest` and `AutoPauseAccountingTest` cases. Debug and Android-test APK assembly passed. Lint reported zero errors and 19 warnings. The internal APK is `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-26_10-26-28_EDT.apk`.

Both APKs installed successfully on the explicitly selected `emulator-5554`. All 13 focused cases in `RunDatabaseTest` and `TrackingReliabilityTest` passed, covering stored checkpoint behavior and paused-service recreation regressions. No UI changed, so no new visual check was required. No physical-phone install occurred.

Milestone 8.0 is complete. Auto-pause remains inactive until Milestone 8.1 connects sensor observations, settings and cues and verifies service-level accounting. Real-phone GPS, steps and screen-off behavior remain later acceptance.
