# 1 WAYiRUN

WAYiRUN is a private-by-default Android running tracker with a desktop-accessible web history. It records outdoor GPS routes, indoor stride-based distance, goals, splits, announcements, achievements, photos, coaching, publication state, and Health Connect exports while preserving owner isolation and offline operation.

Version: 1.0.0

[Homepage](https://wayirun.slopcopy.com) · [Privacy](https://wayirun.slopcopy.com/privacy) · [Support](https://wayirun.slopcopy.com/support) · [Project history](docs/PROJECT_HISTORY.md)

## 1.1 Current status

The functional Android product is shared by the debug and release variants. The release variant uses the permanent application ID `com.unopenedparachute.wayirun`, the production origin `https://wayirun.slopcopy.com`, dedicated production OAuth configuration, and owner-controlled signing material stored outside Git.

Development and production Cloudflare Workers and D1 databases are separate. Production contains the migrated retained history; development remains a non-production environment and is currently write-frozen.

Physical-device observations and deliberately deferred work are recorded separately in `OPEN_WORK.md`. They do not change the source-state description above.

## 1.2 Major capabilities

- Outdoor GPS, indoor steps and honest time-only tracking.
- Pause, auto-pause, recovery, splits, goals and configurable spoken milestones.
- Owner-isolated Google account synchronization and browser history.
- Private-by-default run publication with independent photo visibility and unsharing.
- Achievements, run photos, route overlays, weather context and CSV export.
- Per-account OpenAI coaching with durable request/result handling.
- Completed-session and distance export to Health Connect.

## 1.3 Architecture

The repository contains one Android application module under `android/app` and one Cloudflare Worker/browser application under `worker`. Android retains the authoritative local run record in Room and synchronizes completed archives through guarded adapters. The Worker provides authentication, owner-isolated storage, coaching, photos, publication, export and the browser interface using D1.

See `ARCHITECTURE.md` and `DATA_MODEL.md` for the current implementation and persistence contracts.

## 1.4 Build and test

Android prerequisites are JDK 17 and an Android SDK configured in ignored `android/local.properties`.

```powershell
cd android
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

Worker prerequisites are Node.js 22 or 24 and npm.

```powershell
cd worker
npm.cmd ci
npm.cmd test
```

Release signing and deployment require owner-controlled configuration that is intentionally absent from Git. See `docs/DEVELOPMENT.md`, `android/README.md`, and `worker/README.md`.

## 1.5 Documentation

| Document | Purpose |
| --- | --- |
| `REQUIREMENTS.md` | Product behavior, boundaries and deliberate exclusions |
| `CURRENT_STATE.md` | Current implementation and environment snapshot |
| `ARCHITECTURE.md` | Android, Worker and browser architecture |
| `DATA_MODEL.md` | Persistence, synchronization and data ownership |
| `OPEN_WORK.md` | Unresolved verification and post-1.0 deferrals |
| `TEST_PLAN.md` | Current verification strategy |
| `CHANGELOG.md` | Release-level change history |
| `docs/DEVELOPMENT.md` | Developer setup and repository workflow |
| [`docs/PROJECT_HISTORY.md`](docs/PROJECT_HISTORY.md) | Curated development history |
| `docs/history/README.md` | Index of historical plans and handoffs |

## 1.6 Product boundaries

WAYiRUN is not a social network or general health platform. It deliberately excludes social feeds, calorie tracking, advertisements, automatic control of external music players, basemap-backed route displays, Health Connect imports and a watch application.

## 1.7 Distribution and license

Version 1.0 uses an owner-signed direct APK. Google Play distribution is deferred.

No license has been granted by this repository. All rights are reserved unless the owner later adds a license file.
