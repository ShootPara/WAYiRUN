# 1 WAYiRUN development guide

## 1.1 Repository layout

| Path | Purpose |
| --- | --- |
| `android/` | Android application, tests and Room schemas |
| `worker/` | Cloudflare Worker, browser assets, tests and D1 migrations |
| `docs/` | Current supporting documentation and historical records |
| `testdata/` | Shared deterministic fixtures |

The repository uses one Android application module. Do not create nested repositories or commit generated output.

## 1.2 Android setup

Install JDK 17 and an Android SDK supporting compile/target SDK 36. Store the local SDK path in ignored `android/local.properties`.

From `android/`:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

Debug uses the development account/API boundary. Release uses the permanent production identity and production configuration. There is no runtime environment selector.

Owner-signed builds require ignored files under `private-signing/`. Never create placeholder secrets or commit signing configuration merely to make a release task pass.

## 1.3 Worker setup

Install Node.js 22 or 24. From `worker/`:

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run db:migrate:local
npm.cmd run dev
```

Local tests use isolated Miniflare/workerd/D1 state. Wrangler credentials and `.dev.vars*` remain local and ignored.

Development and production have separate configuration files and guarded deployment scripts. Do not deploy either environment without explicit task authority. Never use production as a development fixture.

## 1.4 Persistence changes

Room and D1 migrations are forward-only:

1. Do not edit an applied migration.
2. Add the next schema/migration number.
3. Preserve existing data and ownership boundaries.
4. Add migration and regression coverage.
5. Generate and commit the new Room schema through the build.
6. Verify D1 changes locally before any authorized remote application.

Run archive changes must remain backward-aware and must not make existing retained records unrecoverable.

## 1.5 Configuration and secrets

Public origins, OAuth client IDs and cloud resource identifiers required at runtime may be tracked. Authentication secrets, private keys, signing passwords, user API keys, session tokens, private backups and personal exports may not.

Ignored local areas include:

- `private-signing/`
- `.env*` and `.dev.vars*`
- `.wrangler/`
- `node_modules/`
- Gradle/Kotlin/build output
- `android/local.properties`

Do not print secret values while diagnosing configuration.

## 1.6 Change workflow

1. Read the current authority documents relevant to the task.
2. Inspect `git status` and preserve unrelated edits.
3. Make the smallest bounded change.
4. Run verification proportional to the change.
5. Report automated, emulator, deployed and physical evidence separately.
6. Leave commit, push, tag, deployment and device installation to the owner unless explicitly delegated.

## 1.7 Documentation

Keep current status in the root authority documents. Put completed milestone plans, handoffs and point-in-time reports under `docs/history/`. Do not let a historical checklist become a competing source of current truth.
