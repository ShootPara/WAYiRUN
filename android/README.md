# WAYiRUN — Android

## 1 Current scope

The debug build is a local tracking prototype with setup/settings, GPS/steps or time-only tracking, pause/resume, swipe finish, recovery, metric speech with ducking, optional active-session YouTube Music controls, and completed-run discard. It supports optional Google sign-in; run sync, maps, photos, AI, and Health Connect are not implemented. Earlier cues are user-confirmed audible; the new music controls and quantitative accuracy need phone verification. The release source set preserves the original name-only shell and excludes prototype services and runtime permissions.

The debug application ID is `com.example.runningapp.debug`. It is provisional; do not register production OAuth or publish it under this identity.

The launch icon is a simple provisional route graphic. Automatic backup and device transfer are explicitly disabled for this development shell; account synchronization will be implemented separately.

## 2 Build

Use JDK 17 or a compatible newer runtime, Android SDK platform 36, and the Gradle wrapper. Local verification uses Android Studio's bundled JDK 21. Set your own SDK path in the ignored `local.properties`, or configure the Android SDK environment through your development tools. Do not commit machine-specific paths.

From this directory on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

On macOS/Linux, use `sh ./gradlew` with the same arguments. The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`; the lint report is `app/build/reports/lint-results-debug.html`; the unit-test report is `app/build/reports/tests/testDebugUnitTest/index.html`.

On 2026-09-12 the command succeeded: 34 JVM tests passed, the debug APK built, and lint reported zero errors with eight version-update advisories. `:app:connectedDebugAndroidTest` also passed six tests on the Pixel 7 AVD (Android 15/API 35). Its report is `app/build/reports/androidTests/connected/debug/index.html`. `:app:assembleRelease` passed separately in the same command to verify source-set isolation; its unsigned shell APK is not a product release. No physical phone was tested, by the user's choice.

For emulator tests, start and unlock an AVD first, then run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

UI tests need an awake/unlocked screen. Database tests use isolated in-memory databases. The debug app requests implemented runtime permissions on first open, shows actual status, and remembers setup completion. Denied location or missing stride never prevent time-only starting. Stride starts blank and means distance per counted step. The app saves all runs locally but exposes only the most recent summary in this milestone.

Local verification encountered read-only attributes on generated Windows build directories. Clearing those attributes within `app/build` resolved the resource-merger failure. This is a local build-output issue, not a reason to change app permissions or disable lint. The local SDK path must escape the drive colon in Java properties format.

## 3 Pinned baseline

| Tool or dependency | Version |
| --- | --- |
| Gradle | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin and Compose compiler plugin | 2.2.20 |
| Compose BOM | 2025.08.01 |
| Activity Compose | 1.10.1 |
| Compile/target SDK | 36 |
| Minimum SDK | 28 |
| Java/Kotlin bytecode target | 17 |
| JUnit (local unit tests) | 4.13.2 |
| KSP | 2.2.20-2.0.3 |
| Room (debug only) | 2.8.4 |
| Kotlin serialization plugin / JSON | 2.2.20 / 1.9.0 |
| Coroutines Android (debug only) | 1.10.2 |
| AndroidX Test runner / rules / JUnit extension | 1.7.0 / 1.7.0 / 1.3.0 |

These are explicit compatible baseline choices, not claims to use the newest releases. AGP 8.13 documents Gradle 8.13 and JDK 17 compatibility; the Compose compiler plugin version follows Kotlin. [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Compose compiler setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).

## 4 Next development boundary

Previous work: **0.1.0-dev-controls1**, timestamped APK under `app/build/outputs/apk/debug/`. The original Android splash remains; startup requests only missing runtime permissions, without setup UI on focus/background return or active-run restoration. A fixed gear opens and closes autosaving settings. Optional Google Credential Manager sign-in uses the development client and encrypted no-backup session storage; local runs retain their original owner. See TASKS.md for actual verification and the final dated APK. Previous test totals belong to historical builds.

Historical build was **0.1.0-dev-playlist1**. The pre-run screen saves a validated YouTube Music playlist link when Open playlist is pressed and offers Clear playlist. Opening uses YouTube Music's Android activity without requiring notification access, then the user chooses playback and returns before starting. No run starts as a side effect. See TASKS.md Section 7.6 for current results; earlier builds below are historical. Next is Milestone 5 accounts/cloud planning.

The pure Kotlin controller and measurement policy remain in `src/main/java/com/example/runningapp/domain`. Platform, UI, and Room code are under `src/debug/java/com/example/runningapp/`. The Room v1 schema is exported to `app/schemas/` and must stay in source control; generated build reports and local SDK configuration remain ignored. Checkpoint/event data is saved before cues or the finished summary are exposed. Recovery excludes all time after the last successful checkpoint and requires deliberate resume.

The authorized reliability follow-up is delivered as `0.1.0-dev-audio1`: speech and tones use media volume, speech failures/timeouts fall back through a serial cue queue, and paused notifications return after service recovery with immediate display requested. Forty JVM tests, eight emulator instrumentation tests, debug/release builds, and lint pass; lint retains eight version advisories and zero errors. Latest direct-ADB instrumentation results are in `app/build/verification/audio1-instrumentation.txt`, not the older connected-test HTML report. See TASKS.md Section 6.8 and [the targeted phone retest](../PHONE_TEST_REVIEW_2026-09-14.md).

Latest build: **0.1.0-dev-permissions1**, with 55 JVM tests and 17 emulator tests passing, plus debug/release builds and lint (zero errors, eight version advisories). Final instrumentation transcript: `app/build/verification/permissions1-instrumentation.txt`. First-open setup requests tracking permissions and offers direct music access. The debug notification listener obtains active media sessions only after user-granted access; no notification content is read or stored. Android's restricted-installation policy cannot be removed by this APK. Music off before starting never inhibits a run. All player pauses during cues leave the run running, per the user's explicit choice. Local discard cascades through Room v1 without a migration.

The user accepts music controls as implemented and defers [phone verification](../MUSIC1_PHONE_RETEST.md); it is not a development blocker. Playlist setup now follows the user's choice to save a link and open it before the run. Actual YouTube Music/headphone/ducking behavior, measured accuracy, GPS handoff, and deferred permission checks remain unverified. Routine outdoor tests retain configured stride; UI/estimation proposals and photos remain later.

## 5 APK handoff naming

The debug package task produces WAYiRUN plus the date/time stamp directly. There is no export-copy task and no generic app-debug.apk in the handoff directory. AGP metadata points at that same artifact, so install tooling uses the correct filename. Two consecutive builds verified a single APK output. The pinned AGP 8.13 bridge uses BaseVariantOutputImpl because that version's public VariantOutput does not expose outputFileName; revisit this bridge when upgrading AGP. The accepted controls1 app contents are unchanged by this packaging fix.

## 6 Current sync1 handoff - September 16, 2026

Current build is **0.1.0-dev-sync1**. Completed account-owned runs queue for resumable cloud upload. Existing runs remain local until the explicit import action in gear settings; discard removes local data immediately and queues protected cloud deletion. Tracking remains offline-capable. Startup splash, permissions and sparse main-screen controls are preserved. Settings hold sync status, retry and import. Download/restore is the next Milestone 5 slice.

58 JVM tests, 34 emulator tests, debug build and lint passed (zero errors, 11 version advisories); 29 backend tests passed. Phone sync is not yet verified. The sole handoff APK is `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-16_07-57-16_EDT.apk` relative to the repository root. See root PHONE_TEST_SYNC1.md and TASKS.md Section 8.0.5. Room schemas v1 and v2 must both remain in source control.
