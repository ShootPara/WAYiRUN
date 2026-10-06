# 1 Milestone 7 test handoff

## 1.1 Status and authorization

Phase 1 (coding and test preparation) and Phase 2 (test execution) are complete. This two-phase workflow was the user's experiment for this milestone and is not authorization to advance through later milestones.

Phase 2 ran this checklist on September 22. The existing dirty tree, including completed Milestones 1-3, was preserved. No commit, push, deployment, physical-phone install, app-data clearing or Milestone 10 work occurred. The user keeps the existing phone APK until a broader verified batch is ready.

## 1.1.1 Recorded result

The production-source scan found no removed music-link symbols and `git diff --check` passed with line-ending notices only. The combined Gradle build, JVM, lint and Android-test assembly command succeeded: 68 JVM tests passed with zero failures, errors or skips; lint reported zero errors and 19 warnings. Both APKs installed successfully on emulator-5554. The focused instrumentation run passed all 22 tests in 86.508 seconds. Verification artifacts are `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-22_18-25-34_EDT.apk` and `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

## 1.2 Prepared changes

Removed MusicLinkPolicy.kt, MusicSessionAdapter.kt (including MusicAccessService), their obsolete tests, the notification-listener manifest registration, service media hooks/status, and music-control-access settings. Updated TrackingService.kt, MainActivity.kt, WayirunApp.kt and the debug manifest. Preserved playlist opening/preferences, run commands, cue audio focus/ducking, and audio-idle foreground-notification cleanup.

Added MusicIndependenceTest.kt for installed-service registration and synthetic player/run independence. Extended SettingsExperienceTest.kt for retained playlist/permission controls and absent music-link setup. RunCues, RunCueQueue and PlaylistLink remain unchanged. No Worker/schema changes belong to this phase.

## 1.3 Source checks

From `<repository-root>`:

```powershell
git status --short
git diff --check
rg -n 'MusicLinkPolicy|MusicSessionAdapter|MusicAccessService|musicStatus|onMusicAccess|mediaGeneration|BIND_NOTIFICATION_LISTENER_SERVICE' android/app/src/debug
```

The production-source scan should have no matches (rg exit 1 is expected). Negative test assertions and historical documents legitimately mention removed names. Review scoped diffs for accidental removal of AudioIdle handling, normal run controls, playlist opening, or cue focus release. Do not treat preexisting changes as new work to revert.

## 1.4 Build and JVM gate

From `<repository-root>\android`:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Read actual test XML/reports and lint results. Report failures, errors, skipped tests and warnings. Do not reuse the previous 78-test count: MusicLinkPolicyTest was intentionally retired. Existing RunCueQueue and playlist tests must still pass. Reports are under `app/build/reports/tests/testDebugUnitTest/` and `app/build/reports/lint-results-debug.html`.

## 1.5 Emulator gate

Use only an explicit emulator serial. SDK tools are under `<android-sdk>`. Discover targets with:

```powershell
$adb = '<android-sdk>\platform-tools\adb.exe'
& $adb devices -l
```

The previous target was emulator-5554 (Pixel_7). If absent, start the existing Pixel_7 AVD with Start-Process and -WindowStyle Hidden, wait for boot, and rediscover its serial. Never select a physical device. Wake/unlock the emulator as needed. Do not clear data; if an unfinished user run is present, stop and report the precondition rather than discard it.

Resolve the fresh APK names from output metadata, not a previous timestamp. From `android/`, after confirming the serial:

```powershell
$serial = 'emulator-5554' # Replace only with the discovered emulator serial.
$appDir = Join-Path $PWD 'app/build/outputs/apk/debug'
$testDir = Join-Path $PWD 'app/build/outputs/apk/androidTest/debug'
$appMeta = Get-Content (Join-Path $appDir 'output-metadata.json') -Raw | ConvertFrom-Json
$testMeta = Get-Content (Join-Path $testDir 'output-metadata.json') -Raw | ConvertFrom-Json
$appApk = Join-Path $appDir $appMeta.elements[0].outputFile
$testApk = Join-Path $testDir $testMeta.elements[0].outputFile
& $adb -s $serial install -r $appApk
& $adb -s $serial install -r $testApk
$classes = 'com.example.runningapp.tracking.MusicIndependenceTest,com.example.runningapp.tracking.TrackingReliabilityTest,com.example.runningapp.tracking.RunEntryTest,com.example.runningapp.ui.SettingsExperienceTest,com.example.runningapp.ui.PlaylistSetupTest,com.example.runningapp.ui.StartupPermissionsTest,com.example.runningapp.ui.RunScreenTest,com.example.runningapp.ui.CoachingFinishTest'
& $adb -s $serial shell am instrument -w -r -e class $classes com.example.runningapp.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Check each install result before proceeding. Inspect instrumentation output for failures and final test count; adb exit status alone is not enough. If needed, run classes separately to localize a failure without changing scope. MusicIndependenceTest cleans up only its own synthetic run. Existing lifecycle, notification, entry/photo, settings and playlist tests protect the adjacent behavior touched by removal.

## 1.6 Limits, failure handling and completion

Synthetic MediaSession tests plus absence of the listener do not prove real YouTube Music/headphone behavior or audible ducking. Real-device acceptance remains deferred: playlist launch, cues temporarily ducking music and restoring volume, player/headphone pause not pausing the run, run pause not pausing the player, and no notification-listener setup. Do not install a phone APK for those checks now.

Do not rerun the unchanged Worker suite for this Android-only milestone. If a build/test/source check fails, record the exact command, relevant failure and file/line, keep Milestone 7 in progress, and return for a coding phase. Do not redesign, suppress tests, or broaden scope during the execution-only phase.

After successful execution, update TASKS.md, REMAINING_WORK.md, FINAL_PASS_ISSUES.md and the issue-plan status with actual counts, commands, artifact paths and outstanding device checks. Clearly separate the prior Milestones 2-3 evidence from this run. Report the milestone result without handing off an APK or advancing to the next milestone automatically.
