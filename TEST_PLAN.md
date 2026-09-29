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
