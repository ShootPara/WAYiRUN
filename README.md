# WAYiRUN

An Android running tracker with a planned Cloudflare-backed desktop history application. The current debug build supports local GPS/step tracking, recovery, spoken metrics with ducking, optional active-session YouTube Music controls, and confirmed completed-run discard. Earlier cues are user-confirmed audible on speaker/headphones; the new music behavior and quantitative tracking accuracy still need phone verification. The release build remains a name-only shell.

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

Previous work was **0.1.0-dev-controls1**: original Android splash, startup-only missing-permission checks, no foreground setup overlay, and a top-right gear that opens/closes autosaving settings. Phone-tested Google sign-in stores sessions encrypted and leaves existing local runs unchanged; synchronization remains later work. `assembleDebug` names its actual package `WAYiRUN-<date/time>.apk`; no generic duplicate is copied. See TASKS.md for verified handoff results; earlier build records below are historical.

Historical APK: **0.1.0-dev-playlist1** adds the saved playlist link, Open playlist, and Clear playlist on the pre-run screen. Unit tests (58), debug build, and lint pass with zero errors and eight version advisories. See TASKS.md Section 7.6 for this build's verification. Phone music checks are deferred by user decision; earlier build records below remain historical.

Previous build **0.1.0-dev-permissions1** added first-open runtime permission prompts, actual grant status, persistent setup completion, and a direct music-access entry. It passed 55 JVM tests, 17 emulator tests, debug/release builds, and lint (zero errors, eight version advisories). See TASKS.md Section 7.5 for artifacts and limitations. The Moto G's restricted music-access setting remains unverified; this APK cannot remove Android's installation restriction. [Phone retest](MUSIC1_PHONE_RETEST.md) includes updated setup instructions.

Milestone 3 implementation passed 34 JVM tests, six emulator tests, debug/release builds, and lint on 2026-09-12. Lint reports zero errors and eight dependency/tool version advisories. The [phone report](WAYiRUN_Phone_Test_Report_2026-09-14.md) records functional passes on a Moto G 2025 running Android 16. The [review and next-step plan](PHONE_TEST_REVIEW_2026-09-14.md) distinguishes those results from unverified accuracy, deferred tests, and reported silence in implemented state/goal audio. See TASKS.md Sections 6.5–6.7. These historical build results were not rerun for the documentation review.

From `android/` on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

The local SDK path belongs in ignored `android/local.properties`. Build outputs, caches, credentials, and signing material must remain untracked. The Gradle wrapper JAR is required source tooling and must be included in the baseline commit.

## 3 Next milestone

Cloud setup guide Section 4 is now completed: the development Worker/D1 foundation is deployed and cloud-only commit `d57fb6e` is pushed to `development` and `codex/cloud-foundation`. See [worker/STATUS.md](worker/STATUS.md) for resources, six local tests, seven live checks, and remaining scope. Next is the Section 5 Git connection and authenticated account/run implementation; the existing Android work remains preserved locally.

[Cloudflare/Git environment setup](CLOUDFLARE_GIT_SETUP.md) describes the local GitHub/Wrangler logins and planned development-only Git integration. The guide does not itself create cloud resources or push code.

Music controls and the saved playlist/open action are **implemented, with phone verification deferred**, per the user's explicit decision. That verification is not a development blocker. Next is Milestone 5 accounts/cloud planning. UI polish, estimation, and photos remain later. The existing phone checklist is retained for when testing becomes practical.

## 4 Git handoff

The existing branch, origin, and commit history were preserved when the redundant nested folder was removed. Open this root folder as the repository in your Git client and as the local project in Codex. If a client still points to the old nested path, locate the repository here; do not initialize another repository.

The user will review, commit, and push the baseline. No commit or push was performed during consolidation.

## 6 Current sync1 handoff - September 16, 2026

Current build is **0.1.0-dev-sync1**. Completed account-owned runs queue for resumable cloud upload. Existing runs remain local until the explicit import action in gear settings; discard removes local data immediately and queues protected cloud deletion. Tracking remains offline-capable. Startup splash, permissions and sparse main-screen controls are preserved. Settings hold sync status, retry and import. Download/restore is the next Milestone 5 slice.

58 JVM tests, 34 emulator tests, debug build and lint passed (zero errors, 11 version advisories); 29 backend tests passed. Phone sync is not yet verified. The sole handoff APK is `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-16_07-57-16_EDT.apk` relative to the repository root. See root PHONE_TEST_SYNC1.md and TASKS.md Section 8.0.5. Room schemas v1 and v2 must both remain in source control.
