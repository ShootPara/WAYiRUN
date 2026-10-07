# 1 WAYiRUN Android application

## 1.1 Module and identities

The repository contains one Android application module: `app`.

| Concern | Debug | Release |
| --- | --- | --- |
| Application ID | `com.example.runningapp.debug` | `com.unopenedparachute.wayirun` |
| Version | `1.0.0-dev` | `1.0.0` / code 1 |
| Environment | Development | Production |
| Signing | Standard debug signing | Owner-controlled configuration outside Git |

The Kotlin namespace is `com.example.runningapp` for both variants. Functional code and resources live in `app/src/main`; variant differences are supplied through build configuration and variant resources.

## 1.2 Requirements

- JDK 17.
- Android SDK with compile/target SDK 36.
- Local SDK path in ignored `local.properties`.
- Windows commands below use the committed Gradle wrapper.

## 1.3 Build and test

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

For a release-affecting change, select the relevant additional tasks from:

```powershell
./gradlew.bat :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease :app:verifyProductionReadiness --console=plain
```

Instrumentation tests require an explicitly selected emulator or an expressly authorized device. Do not treat compilation or emulator results as proof of GPS, steps, audio, background, camera or Health Connect behavior on physical hardware.

## 1.4 Signing

Release signing reads ignored owner configuration from `../private-signing/signing.local.properties`. `assembleRelease` remains useful for structural builds when signing is unavailable. `assembleSignedRelease` fails unless the complete owner-controlled signing configuration and keystore are present.

Never add keystores, signing properties, passwords, certificates containing private material or generated APK/AAB files to Git.

## 1.5 Room schemas and migrations

Room database `wayirun-local.db` is schema version 10. Exported schemas 1–10 are committed under `app/schemas/com.example.runningapp.storage.RunDatabase/` and are used by migration tests.

Do not edit an applied migration or hand-edit a generated historical schema. Add a new forward migration, generate the new schema through the build and test supported migration paths.

## 1.6 Generated artifacts

Build output under `app/build` is ignored. Debug APK names include a local timestamp. Canonical signed artifacts and their custody records remain under ignored owner-controlled storage outside source control.

## 1.7 Further documentation

See the root `ARCHITECTURE.md`, `DATA_MODEL.md`, `TEST_PLAN.md`, and `docs/DEVELOPMENT.md`. Historical phone reports and APK-specific handoffs are indexed under `docs/history/`.
