# WAYiRUN — Android

## 1 Current scope

The Android shell shows WAYiRUN and requests no runtime permissions. Milestone 2 adds a pure Kotlin run controller with deterministic tests, without connecting it to the shell. Device tracking, accounts, media, maps, photos, AI, and Health Connect are not implemented yet.

The debug application ID is `com.example.runningapp.debug`. It is provisional; do not register production OAuth or publish it under this identity.

The launch icon is a simple provisional route graphic. Automatic backup and device transfer are explicitly disabled for this development shell; account synchronization will be implemented separately.

## 2 Build

Use JDK 17 or a compatible newer runtime, Android SDK platform 36, and the Gradle wrapper. Local verification uses Android Studio's bundled JDK 21. Set your own SDK path in the ignored `local.properties`, or configure the Android SDK environment through your development tools. Do not commit machine-specific paths.

From this directory on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

On macOS/Linux, use `sh ./gradlew` with the same arguments. The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`; the lint report is `app/build/reports/lint-results-debug.html`; the unit-test report is `app/build/reports/tests/testDebugUnitTest/index.html`.

On 2026-09-12 this command succeeded: 27 tests passed, the debug APK built, and lint reported zero errors with three version-update advisories. No actual-device verification was performed.

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

These are explicit compatible baseline choices, not claims to use the newest releases. AGP 8.13 documents Gradle 8.13 and JDK 17 compatibility; the Compose compiler plugin version follows Kotlin. [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Compose compiler setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).

## 4 Next development boundary

The pure Kotlin run controller under `com.example.runningapp.domain` is complete for Milestone 2. Its clock and measurements are injected; it contains no platform integrations or persistence. See TASKS.md Sections 5.5–5.6 for the calculation contract and limitations. Next is Milestone 3, the durable local tracking prototype, after its plan and entry decisions are reviewed. No further milestone is authorized by this verification result.
