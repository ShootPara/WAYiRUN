# 1 Milestone 8.1 test handoff

## 1.1 Status and scope

Phase 2 verification is complete. Preserve earlier milestone work. No phone install, deployment, commit or push was performed.

This phase connects the existing policy to sensor observations, service timing, persisted settings and cues. It adds Auto-pause behind the gear, default on when the preference is missing. The setting is captured at run start; editing it during a run affects the next run. Old archived/checkpoint RunSettings decode with auto-pause off to preserve an already captured run's behavior. New setup defaults on for both new and existing installs.

## 1.2 Implementation

TrackingInput owns ephemeral motion evidence and evaluates it on the service's one-second tick. GPS callbacks now carry optional Android speed and speed accuracy. Missing speed/accuracy does not mean stopped. Existing GPS distance/route filtering remains independent. Step registration no longer requires stride: steps can inform motion while distance remains unavailable without stride. The adapter still checks permission and registration success.

Automatic pauses retain sensors, one-second ticks and the bounded renewable wake lock. Motion callbacks update policy evidence but do not return stored measurements or route points while paused. Transitions reset motion/distance baselines, and the controller opens a fresh measurement segment on resume. Manual pauses stop observation, release the wake lock and cannot auto-resume. Permission/provider changes invalidate sensor generation and evidence before queued callbacks are accepted. Loss of all sources during automatic pause leaves manual Resume available. Recovery treats saved automatic pauses as interrupted and requires explicit resume.

PAUSED/RESUMED events now carry optional pauseReason, allowing the existing queue to speak exactly `Auto-paused` and `Resumed.` for automatic transitions while preserving manual wording. Events are still persisted before playback. No new event enum, Room schema, Worker API, permission, or production authentication path was added.

Active UI distinguishes Auto-paused and provides Keep paused to take manual ownership. The new setting is outside the pre-run screen and autosaves. The policy's step resume condition was tightened: the most recent increasing observation must be at most one second old, so two old observations cannot satisfy the later resume dwell. Existing stationary thresholds and both-source agreement remain.

SensorAdapters has overridable IO methods, and the debug service exposes an internal sensor factory for instrumentation. AutoPauseServiceTest replaces only sensor IO; the actual Android service, serial consumer, real clock, timer, persistence and lifecycle run normally. There is no intent-accessible synthetic sensor endpoint. The test restores the factory in finally and only removes its own run.

## 1.3 Source and JVM gate

Read REQUIREMENTS.md, TASKS.md, the then-current working guide and this handoff. Inspect the current source and new files, including AutoPauseTrackingTest and AutoPauseServiceTest. Then run `git diff --check` and, from android/:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Read actual XML counts and lint results. Seven JVM cases were added: six AutoPauseTrackingTest cases and one automatic cue case. The previous gate had 88 tests; use actual results rather than claiming an expected total. The new cases cover step and GPS resume, paused route/distance exclusion, fresh baselines, missing speed, no stride, source loss, manual protection, disabled behavior, stale steps, announcement recovery and Health Connect pause intervals. Existing 8.0 policy/controller, tracking, cue and export tests must remain green.

Windows packaging locks have occurred on the normal build tree. Try the gate first. If it fails on generated output handles, stop Gradle and diagnose the exact path before isolating generated output. Preserve existing source and keep stale generated trees ignored. Do not recursively clean the nested stale output tree as a routine step.

## 1.4 Emulator gate

Discover devices and explicitly select the emulator, previously emulator-5554. Refuse an unfinished user run. Install fresh app and test APKs from output-metadata.json and confirm both Success results. Run these classes:

```text
com.example.runningapp.tracking.AutoPauseServiceTest
com.example.runningapp.ui.AnnouncementSettingsTest
com.example.runningapp.storage.RunDatabaseTest
com.example.runningapp.tracking.TrackingReliabilityTest
com.example.runningapp.tracking.MusicIndependenceTest
com.example.runningapp.ui.RunScreenTest
com.example.runningapp.health.HealthEngineTest
```

The new service case exercises real background timer transitions using controlled steps, verifies sensors stay registered during automatic pause, checks stored pause reason/settings and frozen distance, then verifies a second stop, manual override, sensor shutdown and service recreation requiring explicit resume. This is background lifecycle evidence, not physical screen-off sensor proof. The extended settings case checks the missing preference defaults on, saved off survives reopening, run-start capture and later editing independence. Storage and health cases protect checkpoint/export behavior.

Inspect individual instrumentation results and final summaries rather than adb exit status. Resolve failures with bounded corrections; do not suppress assertions. Do not run unchanged Worker suites. If service timing or a platform permission assumption fails, inspect that failure before expanding scope.

## 1.5 Visual and device checks

Inspect actual gear Auto-pause controls and Auto-paused/Keep paused/Resume states in light/dark themes and at 200-percent font scale. Confirm labels and controls remain reachable without overlap and the setting does not appear on New Run. Save screenshots under android/app/build/verification/milestone-8-1 and inspect with view_image. Restore emulator settings. If an auto-paused screenshot needs controlled input, extend the existing instrumentation fixture without adding product-facing test commands.

Phone acceptance remains deferred: actual GPS accuracy, step-counter batching, walking/running onset timing, repeated stationary stops, permission changes and screen-off audio/sensor behavior. Initial thresholds are conservative and require real movement testing; GPS lacking usable speed accuracy falls back to steps or makes automatic motion unavailable. Keep the user's current APK in place.

## 1.6 Completion

`git diff --check` passed with line-ending warnings only. A known Windows generated-output lock affected the first gate; isolating that exact build tree allowed a clean rerun. The full gate passed with 95 JVM tests and zero failures/errors/skips, successful debug/test APK assembly, and lint at zero errors/20 warnings. A missing import in the new instrumentation test and one redundant null condition were corrected before the passing gate.

Fresh app and test APKs installed on `emulator-5554`. All 26 focused tests passed, including the real-service background auto-pause case. That case passed twice more while capturing visuals. Settings and actual Auto-paused screens passed dark, light and 200-percent-font inspection; controls remained reachable without overlap. Screenshots are under `android/app/build/verification/milestone-8-1/`. Artifact: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-26_22-29-19_EDT.apk` (internal verification only). Font scale was restored to 1.0.

Milestone 8 is complete. Physical-phone acceptance remains for real GPS accuracy, step-counter batching and onset timing, repeated stationary stops, permission changes, audible cue behavior and screen-off sensor continuity. Milestone 9 (anomaly-aware coaching) is next and remains outside this phase.
