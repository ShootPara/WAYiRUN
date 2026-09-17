# WAYiRUN

An Android running tracker with a Cloudflare-backed private desktop history application. The current debug build supports local GPS/step tracking, recovery, spoken metrics with ducking, optional active-session YouTube Music controls, and confirmed completed-run discard. Earlier cues are user-confirmed audible on speaker/headphones; the new music behavior and quantitative tracking accuracy still need phone verification. The release build remains a name-only shell.

## 1 Workspace layout

This directory is the Git repository root: `<repository-root>`. There is no nested `RunningApp` repository.

| Path | Purpose |
| --- | --- |
| [AGENTS.md](AGENTS.md) | Instructions for coding agents and milestone boundaries |
| [REQUIREMENTS.md](REQUIREMENTS.md) | Accepted product behavior and remaining product decisions |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Technical structure and platform boundaries |
| [DATA_MODEL.md](DATA_MODEL.md) | Logical records and persistence invariants |
| [TASKS.md](TASKS.md) | Ordered milestones, verification history, and next execution brief |
| [the then-current working guide](User%20Preferences%20LLM%20Guide.md) | Working style and plan → guardrails → execute → verify workflow |
| [android/README.md](android/README.md) | Android setup, pinned tooling, and build outputs |
| `android/` | Gradle wrapper and single-module Android app |
| `.git/` | Existing repository metadata and history; do not recreate |

Keep the project documents at this root. Add future application directories only when their milestone begins.

## 2 Current verification

The sync1 phone checklist is user-confirmed passed, with two completed runs independently counted in development D1. Current work is sync2 authenticated restore and deletion reconciliation. See TASKS.md Section 8.0.6 for final automated results and the current handoff; earlier build reports are retained there as history. Google sign-in works on the user's phone. Music-specific device verification remains deferred.

From `android/` on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

The local SDK path belongs in ignored `android/local.properties`. Build outputs, caches, credentials, and signing material must remain untracked. The Gradle wrapper JAR is required source tooling and must be included in the baseline commit.

## 3 Next milestone

The first desktop-history slice adds private Google browser sign-in, run history, totals and validated details. See worker/DESKTOP_CONTRACT.md and GOOGLE_SIGN_IN_SETUP.md Section 7. Section 9.1 now includes desktop GPS route maps using Leaflet/OpenStreetMap. Section 9.2 adds one complete CSV export of all synced runs. Desktop run selection/deletion is also implemented using worker/EXPORT_DELETION_PLAN.md. Next is deletion acceptance, then AI coaching planning. Production release, AI, achievements, photos and Health Connect remain later work. Announcement-selector work and phone music checks remain deferred by user direction. The existing sync2 APK remains current.

## 4 Git handoff

The existing branch, origin, and commit history were preserved when the redundant nested folder was removed. Open this root folder as the repository in your Git client and as the local project in Codex. If a client still points to the old nested path, locate the repository here; do not initialize another repository.

The user will review, commit, and push the baseline. No commit or push was performed during consolidation.

## 5 Current sync2 handoff - September 16, 2026

Current build: **0.1.0-dev-sync2**, adding authenticated download/restore and deletion reconciliation. Upload/import/discard phone checks already passed; two completed cloud runs are confirmed. Restore verifies exact manifest/chunk hashes, account ownership and archive contents before transactional local insertion. Existing local records are never overwritten. Main-screen controls, splash and permissions remain unchanged; sync status/retry/import stay behind the gear.

Install `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-16_15-46-55_EDT.apk` (repository-relative) over the existing app. Do not uninstall. See root TASKS.md Section 8.0.6 for actual gates and PHONE_TEST_SYNC2.md for optional separate-device checks. Room schemas 1, 2 and 3 must remain in source control. Desktop history/maps now exist; production remains untouched.
