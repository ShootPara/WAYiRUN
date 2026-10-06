# 1 WAYiRUN test plan

Status: Feature-development baseline verification

## 1.1 Verification boundaries

This document separates the reproducible automated feature baseline from later deployment and physical-device acceptance.

Milestone 6 MUST run only local automated checks. It MUST NOT deploy, install an APK, launch a connected device, call a paid model, use personal run data, or claim physical behavior from compilation/emulation.

## 1.2 Prerequisites

- Windows PowerShell.
- JDK 17 or a compatible newer runtime; the project commonly uses Android Studio JDK 21.
- Android SDK platform 36 configured through ignored `android/local.properties` or the environment.
- Node.js 22 through 24 for the Worker.
- Checked-in Gradle wrapper and npm lockfile.
- No credentials are required for local unit/Worker verification.

Generated `build`, `.gradle`, `.kotlin`, `.wrangler`, `node_modules`, local SDK configuration and secrets remain ignored.

## 1.3 Required feature-baseline gate

### 1.3.1 Android

From `android/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

Required results:

- all JVM/debug-unit tests pass;
- the debug APK is assembled using the timestamped WAYiRUN filename;
- lint completes with zero errors;
- Room schema output does not introduce an unexplained tracked change.

The APK path is `android/app/build/outputs/apk/debug/WAYiRUN-<timestamp>.apk`. Test and lint reports are generated beneath `android/app/build/reports/`.

### 1.3.2 Worker and browser modules

From `worker/`:

```powershell
npm.cmd test
```

The script compiles strict TypeScript, performs a Wrangler deployment dry run into ignored local output, and runs all Node/Miniflare/workerd tests in `worker/test`.

Required results:

- TypeScript compilation passes;
- deployment dry run passes;
- every Worker test passes;
- no remote deployment or database is contacted;
- no tracked file changes are generated.

## 1.4 Baseline evidence to record

After the gate, record:

- current branch and HEAD;
- clean/modified/untracked Git state;
- Android task result and test count;
- lint error/warning counts;
- generated APK filename, size and SHA-256;
- Worker test count and pass/fail result;
- any warning that affects reproducibility or feature work.

Do not commit generated reports or APKs unless a later explicit decision changes repository artifact policy. The current build output remains an ignored local handoff artifact.

## 1.5 Optional emulator regression gate

Instrumentation coverage exists for account/session state, setup/settings, tracking service behavior, recovery, synchronization, Room migrations, coaching, photos, publication and Health Connect. It requires an explicitly started and unlocked emulator.

Do not install to or launch a connected emulator or phone merely as part of the feature baseline. A later bounded emulator milestone may run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Its result is separate from the required Milestone 6 gate.

## 1.6 Later deployment verification

Development deployment parity is intentionally postponed. When authorized, it must separately verify the exact Worker version, applied D1 migrations, health/readiness/auth behavior, Android compatibility, and cleanup of any guarded synthetic records.

No deployment result is required for the feature-development baseline.

## 1.7 Later physical-device acceptance

Comprehensive physical acceptance is intentionally postponed to the release phase. `OPEN_WORK.md` lists the implemented-but-unverified behaviors. Historical detailed cases remain in `COMPREHENSIVE_TEST_PLAN.md`, but that file is evidence/checklist material rather than a current baseline gate.

At minimum, later acceptance must cover real GPS/steps, indoor auto-pause, screen-off/background behavior, audio ducking, camera/picker/share targets, coaching playback, Health Connect, recovery, and phone/web deletion reconciliation.

## 1.8 Failure handling

A failing required command blocks the feature-development baseline until the failure is understood and either corrected in a separately scoped implementation milestone or documented as an environmental blocker. Do not weaken tests, lint, validation, ownership checks, or privacy guards to make the gate pass.

## 1.9 Approved detector/GPS auto-pause remediation

This bounded October 2026 remediation authorizes Android implementation, local gates, explicitly selected emulator regression and an APK handoff for physical acceptance. It does not authorize physical-phone installation, deployment, or commit/push. REQUIREMENTS.md 1.8 defines the policy, including concurrent outdoor timers. Historical acceleration tests/handoffs are superseded for the decision algorithm.

### 1.9.1 Automated acceptance

Pure tests must cover initial detector silence, two-step arming, every-step silence reset, exact five-second boundary, receipt-time conservatism, timely batched steps, delayed hardware/application delivery, duplicate/out-of-order/future events, re-registration and tick gaps. Resume requires two post-pause steps within two seconds without extra dwell. Counter batches never cause a policy transition.

GPS tests must cover quality/uncertainty boundaries, deadband and invalid samples between ticks, distinct timestamps, stale fixes/gaps, concurrent five-second windows, confirmed missing-detector fallback, registered-but-unarmed suppression, GPS loss and step-based resume despite stationary GPS. Integration tests must preserve paused accounting, source gaps, fresh baselines, manual/interrupted protection, repeated stops, announcement progress and Health Connect intervals. A delayed pre-pause counter batch received after segment closure remains excluded; quantify this existing limitation rather than rewriting distance.

Run the required Android gate plus `:app:assembleDebugAndroidTest`, then focused auto-pause/service/storage/settings/announcement/photo-weather instrumentation on an explicitly selected emulator. Synthetic callbacks and screen-off emulator tests establish integration behavior, not hardware sensitivity or OEM delivery.

### 1.9.2 Physical acceptance handoff

Record phone model, Android version, installed build, normal carried placement, permissions and battery mode. Use the same defaults for all testers; do not calibrate per person.

1. Indoors, walk slowly, walk normally and jog for several minutes, including initial startup. No false pause.
2. Stop for 15 seconds, then move again; repeat three times. Confirm state/cues, frozen paused time/distance and fresh resumed measurement. Five seconds of detector silence can mean roughly five to eight seconds after the last physical step because delivery and timer evaluation take time.
3. While stopped, look at or gently reposition the phone. Ordinary handling should no longer require accelerometer quiet.
4. Manually pause, then walk for at least 15 seconds. No automatic resume.
5. Repeat movement and two stop/resume cycles with the screen locked and phone unplugged. Include one locked interval longer than ten minutes to exercise wake-lock renewal.
6. Outdoors, repeat walking/jogging/stops with usable GPS, then poor reception. GPS loss must not create a pause. GPS-only fallback cannot promise resume below the configured movement threshold.
7. Verify auto-pause off and interrupted recovery requiring explicit resume. Finish and inspect pause intervals, distance, splits and cues.

For failures, capture only bounded detector-registration/event-age/count, GPS speed/uncertainty, tick-gap and decision evidence if instrumentation is needed; no coordinates, identities or indefinite raw logging. A detector silently failing after arming cannot be distinguished from stopping with detector-only input. A phone left on treadmill equipment cannot represent its runner.

Keep OPEN_WORK.md's physical bug open until this acceptance passes. Report emulator and phone results separately. One phone validates that device; it does not establish population-wide reliability.
