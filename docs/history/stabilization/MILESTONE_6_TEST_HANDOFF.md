# 1 Milestone 6 test handoff

## 1.1 Status and boundary

Milestone 6 Phase 2 is complete. This milestone implements only configurable interval announcements; no auto-pause, coaching, sharing, weather, notification or screen-redesign scope was added. Milestones 1-3, 7 and 10 remain preserved.

No physical-phone install, APK handoff, deployment, commit or push occurred. The user's working phone build remains in place pending a broader verified batch.

## 1.2 Implementation decisions

RunSettings adds announcementsEnabled and announcementInterval. Gear settings expose an on/off switch and exactly four radio choices: 5 minutes, 10 minutes, 0.5 mile/kilometer and 1 mile/kilometer. New setup defaults to enabled at five minutes; saved switch/interval choices override that. Disabled intervals retain the selected choice. A run captures these settings at start; later edits affect the next run only.

Older JSON checkpoints omit these fields and decode with intervals off, preserving an existing or recovered run's behavior. RunRepository already stores the checkpoint as JSON, so no Room migration, table/column, Worker endpoint or archive format change is required. Existing run archives continue to decode in the updated app. New nondefault settings require the updated app to restore; downgrading to the older strict JSON decoder is not a supported compatibility claim.

Time announcements use active-time boundaries, excluding countdown/pauses. Each crossed boundary produces an event; the event contains that boundary time and the last accepted distance, with no invented movement during a delayed tick. Distance announcements interpolate crossings only within the accepted measurement interval, using the run's captured unit. Full splits remain separate and silent; state, goal and completion cues are unchanged and stay enabled when intervals are off. Interval cues reuse the existing ordered Time/Distance/Average pace speech and audio-focus/fallback pipeline.

Recovery reconstructs the time boundary index from saved active time; distance crossings use saved distance as their starting point. The service's existing save-before-play path persists totals and event sequence before playback. A crash can omit an in-flight cue but cannot replay saved thresholds. No additional persistent cursor is required.

## 1.3 Changed files

- domain/RunModels.kt: interval enum, backward-compatible settings defaults, announcement event type.
- domain/RunController.kt: time and distance crossing events, recovery time cursor.
- tracking/RunCueQueue.kt: interval speech via existing metric formatting, finite pace guard.
- tracking/RunCues.kt: corrected class documentation only.
- ui/WayirunApp.kt and new ui/AnnouncementSettings.kt: gear controls, autosave, start capture.
- New domain/RunAnnouncementsTest.kt and ui/AnnouncementSettingsTest.kt; extended RunCueQueueTest.kt and RunDatabaseTest.kt.

All paths are under android/app/src in their existing main/debug/test/testDebug/androidTest source sets. TrackingService, database schema, cloud APIs and run calculations outside interval detection remain unchanged.

## 1.4 Source and build checks

Read REQUIREMENTS.md, TASKS.md, the then-current working guide and this handoff. Inspect `git status --short` and scoped changes, then run `git diff --check`. Treat untracked files as part of the implementation when reading the source. Preserve prior milestone changes.

From `<repository-root>\android`:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Read actual test XML in app/build/test-results/testDebugUnitTest and lint reports in app/build/reports. Record actual counts, skips/failures and warnings. There are eight new domain test methods and two new queue test methods; do not reuse the previous 68-test result as evidence. Do not rerun the unchanged Worker suite.

Domain tests cover both time choices, all unit/distance combinations, exact and multiple crossings, countdown exclusion, pause/resume, goal continuation, source gaps, persisted JSON recovery/reboot, event sequence continuity, off behavior and old settings JSON. Existing controller/tracking tests must still pass. Queue tests cover ordered metric speech, zero-distance pace, kilometer/mile units, fallback and focus release. Inspect finite/zero pace handling without changing the existing goal/completion wording.

## 1.5 Emulator checks

Use only an explicitly selected emulator. Discover devices with SDK adb; previous target was emulator-5554 (Pixel_7). If needed start the existing AVD with Start-Process -WindowStyle Hidden and wait for boot. Never clear user data or discard an unfinished user run. Install fresh APKs from output metadata, checking each Success before continuing.

From android/:

```powershell
$adb = '<android-sdk>\platform-tools\adb.exe'
& $adb devices -l
$serial = 'emulator-5554' # Confirm this is the discovered emulator.
$appDir = Join-Path $PWD 'app/build/outputs/apk/debug'
$testDir = Join-Path $PWD 'app/build/outputs/apk/androidTest/debug'
$appMeta = Get-Content (Join-Path $appDir 'output-metadata.json') -Raw | ConvertFrom-Json
$testMeta = Get-Content (Join-Path $testDir 'output-metadata.json') -Raw | ConvertFrom-Json
& $adb -s $serial install -r (Join-Path $appDir $appMeta.elements[0].outputFile)
& $adb -s $serial install -r (Join-Path $testDir $testMeta.elements[0].outputFile)
& $adb -s $serial shell input keyevent KEYCODE_WAKEUP
& $adb -s $serial shell wm dismiss-keyguard
$classes = 'com.example.runningapp.ui.AnnouncementSettingsTest,com.example.runningapp.ui.SettingsExperienceTest,com.example.runningapp.storage.RunDatabaseTest,com.example.runningapp.tracking.TrackingReliabilityTest,com.example.runningapp.tracking.MusicIndependenceTest,com.example.runningapp.tracking.RunEntryTest'
& $adb -s $serial shell am instrument -w -r -e class $classes com.example.runningapp.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Inspect each test result and the final instrumentation summary, not just adb's process exit status. The storage test uses an in-memory database; the UI test uses a synthetic controller and restores its edited preferences. RunDatabaseTest verifies the actual save/decode/recover path, including the retained choice and no replay.

Inspect actual gear controls at normal and 200-percent font settings in both themes. Confirm four choices, selected state, mile/kilometer labels, disabled state with choice retained, readable wrapping and reachable gear. Record and restore original emulator font scale and settings. Save screenshots using adb shell screencap plus adb pull under android/app/build/verification/milestone-6 and inspect them with view_image. No announcement controls belong on the pre-run screen.

An emulator integration check may use a synthetic time-only run to hear the five-minute cue and verify continued tracking beyond a shorter goal. Do not use production API keys or external music services for this check. Report real speaker/headphone ducking and screen-off sensor/audio behavior as deferred phone acceptance, not as proven by compilation or fake output tests.

## 1.6 Failure handling and completion

If a build/test/visual check fails, record the command, failure and file/line; return to a bounded coding correction rather than suppressing assertions or adding adjacent features. Keep milestone status in progress until required gates pass. Do not reclassify failed tests as harmless without evidence.

After success update TASKS.md, REMAINING_WORK.md, FINAL_PASS_ISSUES.md, the issue plan and this handoff with actual commands/counts/artifacts and remaining device acceptance. No phone APK handoff follows automatically. The next bounded milestone in the accepted sequence is 8.0, auto-pause policy, and is outside this execution phase.

## 1.7 Phase 2 result - September 25

`git diff --check` passed with line-ending warnings only. The first gate attempts encountered Windows file handles retained in generated build output, not source failures. After stopping Gradle and moving the verified stale `app/build` tree aside, the requested command completed successfully from a fresh build tree. All 78 JVM tests passed with zero failures, errors or skips; debug and Android-test APK assembly passed; lint reported zero errors and 19 warnings.

Both APKs installed successfully on `emulator-5554`. The focused six-class run executed 21 cases: 20 passed and the existing `RunEntryTest.finishedRunSurvivesRecreationButNotWarmOrColdReopen` timed out waiting for tracking state after the long combined suite. That exact case passed immediately when rerun alone. All announcement settings, persistence/start capture, stored recovery, cue, tracking-reliability and music-independence coverage passed.

Actual gear controls were inspected in light and dark themes at normal font scale and in light theme at 200-percent font scale. The enabled five-minute default, all four choices, selected state, unit labels, readable wrapping, gear reachability and scrolling were intact without overlap. Evidence is under `android/app/build/verification/milestone-6/`; the emulator was restored to light theme and font scale 1.0. The internal APK is `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-25_19-46-41_EDT.apk`.

Real speaker/headphone ducking, screen-off cue delivery and sensor behavior remain physical-phone acceptance. Milestone 8.0 is next and was not started.
