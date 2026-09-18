# WAYiRUN — Android

## 1 Current scope

The debug build is a local tracking prototype with setup/settings, GPS/steps or time-only tracking, pause/resume, swipe finish, recovery, metric speech with ducking, optional active-session YouTube Music controls, and completed-run discard. It supports Google sign-in, account-owned upload/import/discard, and sync2 download/restore. Maps, photos, AI and Health Connect remain later milestones. Earlier cues are user-confirmed audible; the new music controls and quantitative accuracy need phone verification. The release source set preserves the original name-only shell and excludes prototype services and runtime permissions.

The debug application ID is `com.example.runningapp.debug`. It is provisional; do not register production OAuth or publish it under this identity.

The launch icon is a simple provisional route graphic. Automatic backup and device transfer are explicitly disabled for this development shell; account synchronization uses the authenticated development Worker.

## 2 Build

Use JDK 17 or a compatible newer runtime, Android SDK platform 36, and the Gradle wrapper. Local verification uses Android Studio's bundled JDK 21. Set your own SDK path in the ignored `local.properties`, or configure the Android SDK environment through your development tools. Do not commit machine-specific paths.

From this directory on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

On macOS/Linux, use `sh ./gradlew` with the same arguments. The APK is generated at `app/build/outputs/apk/debug/WAYiRUN-<date_time_zone>.apk`; the lint report is `app/build/reports/lint-results-debug.html`; the unit-test report is `app/build/reports/tests/testDebugUnitTest/index.html`.

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

Current work is **0.1.0-dev-sync2**. Sync1 phone checks are user-confirmed passed. The original Android splash and startup-only permission behavior remain; all sync status/retry/import controls stay behind the gear. Pure Kotlin run calculations remain in the domain package; platform/account/storage/sync integration remains in the debug source set. Room schemas 1, 2 and 3 are exported and migrations are additive. See root TASKS.md for dated verification and the current APK.

## 5 APK handoff naming

The debug package task produces WAYiRUN plus the date/time stamp directly. There is no export-copy task and no generic app-debug.apk in the handoff directory. AGP metadata points at that same artifact, so install tooling uses the correct filename. Two consecutive builds verified a single APK output. The pinned AGP 8.13 bridge uses BaseVariantOutputImpl because that version's public VariantOutput does not expose outputFileName; revisit this bridge when upgrading AGP. The accepted controls1 app contents are unchanged by this packaging fix.

## 6 Current sync2 handoff - September 16, 2026

Current build: **0.1.0-dev-sync2**, adding authenticated download/restore and deletion reconciliation. Upload/import/discard phone checks already passed; two completed cloud runs are confirmed. Restore verifies exact manifest/chunk hashes, account ownership and archive contents before transactional local insertion. Existing local records are never overwritten. Main-screen controls, splash and permissions remain unchanged; sync status/retry/import stay behind the gear.

Install `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-18_08-58-18_EDT.apk` (repository-relative) over the existing app. Do not uninstall. See root TASKS.md Section 8.0.6 for actual gates and PHONE_TEST_SYNC2.md for optional separate-device checks. Room schemas 1, 2 and 3 must remain in source control. Desktop history/maps is the next sequential development step; production remains untouched.

## 7 Login compatibility repair - September 18, 2026

The latest APK accepts the server's 90-day session duration; the old one-hour parser bound rejected otherwise successful Google sign-in. Assembly and lint passed. Install over the current app and test Google sign-in; do not uninstall. Coaching settings are planned separately and are not in this APK.

## 8 Per-account coaching key settings - September 18, 2026

The current APK adds masked Add/Replace/Remove OpenAI key controls behind the gear, backed by encrypted account-scoped server storage. Login repair is user-confirmed. Assembly/lint and two emulator key-panel tests pass. Personal-key validation remains a user check; coaching speech generation is not enabled in this slice. See worker/KEY_STORAGE_CONTRACT.md and root TASKS.md Section 12.

## 9 Current coaching handoff - September 18, 2026

This supersedes the historical APK references above. Current APK: app/build/outputs/apk/debug/WAYiRUN-2026-09-18_12-43-46_EDT.apk (34516894 bytes). Install over the existing app, without uninstalling. Key setup is user-confirmed. Selected finishes now request current/previous-run coaching with Cedar playback, a dismissible animation and onboard fallback recordings; unchecked finishes play only the normal completion cue. See PHONE_TEST_COACHING.md for the real-phone check and remaining limitations.

Normal assembly/lint and 58 JVM tests pass; seven focused emulator tests pass, including fallback playback completion. Generated build directories had ReadOnly attributes that blocked incremental Gradle cleanup; clearing those attributes only under app/build restored the normal build. The temporary build-location override is not needed. The version label remains 0.1.0-dev-sync2; identify this handoff by its APK timestamp. Source is uncommitted by user preference.
