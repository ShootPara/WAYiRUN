# WAYiRUN

An Android running tracker with a planned Cloudflare-backed desktop history application. The current implementation includes the Android shell and a pure Kotlin run controller. The shell displays WAYiRUN; device tracking and the other product features are not integrated yet.

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

Milestone 2 passed `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug` on 2026-09-12. All 27 controller tests passed. Lint reported zero errors and three dependency/tool version advisories. No phone launch, sensor, media, or screen-off behavior has been verified. See TASKS.md Section 5.6 for the completed checks and artifact paths.

From `android/` on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

The local SDK path belongs in ignored `android/local.properties`. Build outputs, caches, credentials, and signing material must remain untracked. The Gradle wrapper JAR is required source tooling and must be included in the baseline commit.

## 3 Next milestone

Milestones 0–2 and repository alignment are complete. Next is Milestone 3: the durable local tracking prototype. Read TASKS.md Sections 6 and 10 for its scope, entry decisions, and verification gates. Planning and execution authorization come before implementation.

## 4 Git handoff

The existing branch, origin, and commit history were preserved when the redundant nested folder was removed. Open this root folder as the repository in your Git client and as the local project in Codex. If a client still points to the old nested path, locate the repository here; do not initialize another repository.

The user will review, commit, and push the baseline. No commit or push was performed during consolidation.
