# 1 WAYiRUN

WAYiRUN is an Android running tracker with a Cloudflare-backed private desktop history application. This repository is currently a **feature-development baseline**, not a production release.

The complete Android application is in the `debug` source set. The `release` source set remains a name-only shell and is intentionally outside the feature-baseline reconciliation. The development product includes local run tracking and recovery, account-owned cloud synchronization, desktop history and export, coaching, achievements, photos, private-by-default publication, route-noodle graphics, weather overlays, and Health Connect export.

## 1.1 Current product direction

- Route displays are provider-independent route noodles with preserved gaps and start/finish markers. Basemap, Leaflet, and map-tile behavior is superseded.
- Music integration opens a saved YouTube Music playlist and ducks other audio during WAYiRUN cues. The app does not send player transport commands or request notification-listener access.
- Runs and photos are private by default. Publication requires an explicit share/copy/image-share intent; photo visibility and unsharing remain independently controllable.
- The Android debug application and Cloudflare Worker are development systems. Production identity, signing, configuration, deployment, and release acceptance are separate later work.

## 1.2 Authoritative documentation

| Document | Purpose |
| --- | --- |
| [REQUIREMENTS.md](REQUIREMENTS.md) | Current accepted product behavior, exclusions, and deliberate deferrals |
| [CURRENT_STATE.md](CURRENT_STATE.md) | Factual inventory of delivered behavior and current environment boundaries |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Current Android, Worker, browser, and persistence structure |
| [DATA_MODEL.md](DATA_MODEL.md) | Room, archive, D1, settings, secret, and data-classification contract |
| [OPEN_WORK.md](OPEN_WORK.md) | Genuine bugs, unverified behavior, deferred features, and release work kept as separate categories |
| [TEST_PLAN.md](TEST_PLAN.md) | Reproducible automated baseline and later acceptance boundaries |
| [AGENTS.md](AGENTS.md) | Repository-specific agent instructions |
| [the then-current working guide](User%20Preferences%20LLM%20Guide.md) | Collaboration and milestone workflow |

Historical plans, handoffs, reports, and implementation contracts remain evidence, not current authority. Their disposition is indexed in [docs/history/README.md](docs/history/README.md).

## 1.3 Workspace

This directory is the Git repository root. Do not create a nested repository.

| Path | Purpose |
| --- | --- |
| `android/` | One-module Android application, Gradle wrapper, tests, and exported Room schemas |
| `worker/` | Cloudflare Worker, D1 migrations, browser application, tests, and development deployment tooling |
| `testdata/` | Checked-in deterministic test fixtures |
| `docs/history/` | Index for non-authoritative historical implementation material |

Generated build directories, local SDK paths, Wrangler state, dependencies, credentials, signing material, and secrets must remain untracked.

## 1.4 Baseline verification

Android, from `android/` on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

Worker, from `worker/`:

```powershell
npm.cmd test
```

These commands verify the feature-development baseline. They do not establish physical GPS/step accuracy, screen-off behavior, real audio ducking, camera/share behavior, Health Connect behavior on a user phone, deployment parity, or production readiness.

## 1.5 Git baseline

`codex/account-sessions` is the candidate authoritative development lineage. It contains the still-valid behavior from the divergent `development` lineage and later replacements. The old `development` route-map implementation uses the rejected Leaflet/OpenStreetMap basemap direction and must not be restored.

The user controls commits, pushes, branch consolidation, and deployment unless explicitly delegated.
