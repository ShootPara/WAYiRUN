# 1 Milestone 10 test handoff

## 1.1 Status and scope

Phase 1 edits and Phase 2 verification are complete. All pending changes from Milestones 1-3 and 7 were preserved. No commit, push, deployment or physical-phone APK handoff occurred.

### 1.1.1 Recorded result

The combined Gradle JVM/build/lint/test-APK gate passed: 68 JVM tests, zero failures/errors/skips, zero lint errors and 19 warnings. Seventeen focused emulator cases passed across the new controls and adjacent setup/run flows. A timing failure in the existing photo-preview test appeared once during a combined rerun; the final app passed all six RunScreenTest cases in isolation.

The first 200-percent-font screenshots exposed cramped adjacent labels even though the bounds assertion passed. The selector safety margin and one-line clipping assertion were corrected; NewRunControlsTest then passed all three cases. Final selector/status screenshots are under `android/app/build/verification/milestone-10-final/milestone-10/`; actual app normal/200-percent screenshots are under `android/app/build/verification/milestone-10-app/`. Visual inspection confirmed square controls, readable emoji/labels, requested status colors and scroll access to START RUNNING. Emulator font scale was restored to 1.0. Final internal artifact: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-23_06-06-06_EDT.apk`.

## 1.2 Implementation

NewRunControls.kt supplies square emoji radio choices and left/right status labels. Choice layout measures labels and emojis at the current font scale and wraps into fewer columns as necessary. Touch targets are at least 48dp; selected state is available to accessibility services, and decorative emojis do not duplicate spoken labels. Normal settings chips remain unchanged.

WayirunApp.kt uses the controls for Outdoor/Indoor and None/Time/Distance, preserving preference keys, per-goal target memory, validation and captured RunSettings. Setup and active/paused screens share the status row: Outdoor/Online green, Indoor blue, Fallback red, with separate light/dark colors. Connectivity still means a non-expired account session plus a validated Android network; this milestone changes presentation only.

SettingsExperienceTest.kt now selects mode/goal by stable tags because the separate run-type status text legitimately duplicates the selected mode's label. NewRunControlsTest.kt adds selection/persistence/start capture, narrow-screen/200-percent-font layout, rendered status colors and screenshot output. No tracking/domain/storage/Worker changes belong to this milestone.

## 1.3 Source and build gate

Read REQUIREMENTS.md, TASKS.md and the then-current working guide. From the repository root, inspect `git status --short`, run `git diff --check`, and review only this milestone's UI/test changes, retaining earlier work. The previous APK/test evidence belongs to Milestone 7, not these edits.

From `<repository-root>\android`:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Read actual JVM XML and lint reports under app/build. Record counts, failures/skips and warnings; do not copy prior counts. Source inspection should confirm that mode/goal handlers, target validation, playlist opening and active-run commands still use the existing paths.

## 1.4 Emulator tests

Discover devices using the SDK adb at `<android-sdk>\platform-tools\adb.exe`. Select an explicit emulator serial only; the previous target was emulator-5554. If necessary, launch the existing Pixel_7 AVD using Start-Process with -WindowStyle Hidden. Do not clear app data or discard an unfinished user run.

From `android/`, once the emulator serial is confirmed:

```powershell
$adb = '<android-sdk>\platform-tools\adb.exe'
& $adb devices -l
$serial = 'emulator-5554' # Use the discovered emulator serial.
$appDir = Join-Path $PWD 'app/build/outputs/apk/debug'
$testDir = Join-Path $PWD 'app/build/outputs/apk/androidTest/debug'
$appMeta = Get-Content (Join-Path $appDir 'output-metadata.json') -Raw | ConvertFrom-Json
$testMeta = Get-Content (Join-Path $testDir 'output-metadata.json') -Raw | ConvertFrom-Json
& $adb -s $serial install -r (Join-Path $appDir $appMeta.elements[0].outputFile)
# Confirm Success before installing/running the next artifact.
& $adb -s $serial install -r (Join-Path $testDir $testMeta.elements[0].outputFile)
& $adb -s $serial shell input keyevent KEYCODE_WAKEUP
& $adb -s $serial shell wm dismiss-keyguard
$classes = 'com.example.runningapp.ui.NewRunControlsTest,com.example.runningapp.ui.SettingsExperienceTest,com.example.runningapp.ui.PlaylistSetupTest,com.example.runningapp.ui.RunScreenTest,com.example.runningapp.ui.StartupPermissionsTest,com.example.runningapp.ui.CoachingFinishTest'
& $adb -s $serial shell am instrument -w -r -e class $classes com.example.runningapp.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Read the final instrumentation result and each failure, not just adb's exit status. These tests exercise captured settings and finish controls without changing tracking policy. Do not rerun unrelated Worker tests.

## 1.5 Visual acceptance

NewRunControlsTest writes PNGs beneath the app's external files/milestone-10 folder. Retrieve them using the explicit emulator serial:

```powershell
$screens = Join-Path $PWD 'app/build/verification/milestone-10'
New-Item -ItemType Directory -Force -Path $screens
& $adb -s $serial pull /sdcard/Android/data/com.example.runningapp.debug/files/milestone-10 $screens
```

Inspect screenshots with view_image: normal and 200-percent text, all five labels/emojis, square borders, selection appearance and status colors. Every label must fit; inspect for overlap and missing emoji glyphs. Passing pixel/color assertions alone does not establish visual quality.

Also inspect the actual app's New Run and active/paused layouts at normal and 200-percent Android font settings, in light and dark mode. Record and restore the emulator's original font_scale. Use only synthetic runs and clean up only records created by this verification. Capture screenshots with adb shell screencap to an explicit emulator path and adb pull; avoid PowerShell binary-output redirection. Check that the profile, selectors, target, playlist, statuses and START RUNNING remain reachable by scrolling; the gear must stay usable. Confirm left run type/right connectivity, no ambiguous status colors, no clipped target/start labels and no setup selectors during an active run. Do not alter production connectivity logic merely to force Online; the isolated test covers both states deterministically.

## 1.6 Failure and completion

If a build/test/layout check fails, report the exact command or image, relevant file/line and observed behavior, keep Milestone 10 in progress, and return for the coding phase. Do not suppress assertions or broaden the design during test execution.

When all gates pass, update TASKS.md, REMAINING_WORK.md, FINAL_PASS_ISSUES.md, this handoff and the issue-plan status with actual counts, APK/report/screenshot paths and remaining phone acceptance. Physical-phone visual acceptance stays deferred with the broader batch. Milestone 6 (announcements) follows Milestone 10; do not advance to it automatically during this execution phase.
