# 1 WAYiRUN test plan

Status: Current verification strategy

## 1.1 Evidence classes

Report the following independently. Passing one class does not imply another:

1. Kotlin/JVM and Worker unit/integration tests.
2. Android build and lint.
3. Android instrumentation and emulator behavior.
4. Browser and Worker local verification.
5. Deployed-service and production-configuration checks.
6. Physical-device behavior.

Historical test counts and milestone-specific commands are retained under `docs/history/`; they are evidence for those exact checkpoints, not permanent expected counts.

## 1.2 Android baseline

From `android/` on Windows:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

For release-affecting changes, also run the relevant release unit, lint, assembly and production-readiness tasks. Owner-signed assembly requires ignored signing configuration and should be run only when the task requires a signed artifact.

Database changes require a new forward migration, an exported Room schema and migration tests from every supported predecessor. Applied migration code and checked-in historical schemas must not be rewritten.

## 1.3 Worker baseline

From `worker/`:

```powershell
npm.cmd ci
npm.cmd test
```

The suite compiles TypeScript, creates deploy output and exercises Worker/D1 behavior in local isolated runtimes. Migration work must additionally test deterministic export/import/reconciliation and preserve applied SQL files.

Deployment dry-runs, target guards, smoke tests and live canaries are required only for an explicitly authorized deployment task. Local tests must not contact or mutate remote resources.

## 1.4 Focused regression coverage

Changes should add or select coverage for the affected contract, including as applicable:

- Run-controller accounting, pause/recovery, goals and split behavior.
- Auto-pause evidence timing and stale-callback rejection.
- Room migrations, ownership and synchronization queues.
- Coaching durability and paid-attempt idempotency.
- Photo, weather, publication and deletion reconciliation.
- Browser privacy, export completeness and authorization isolation.
- Environment and deployment guards.

## 1.5 Instrumentation and emulator checks

Use an explicitly selected emulator. Confirm that it contains no real user account or private run data before destructive test setup. Never clear or replace data on a physical device as part of an emulator test workflow.

Record the classes run, failures, reruns and any visual conditions such as dark mode or enlarged text. Do not convert a transient failure into a pass without recording the cause or successful focused reproduction.

## 1.6 Physical-device checks

Physical installation and operation require an active task that includes them. Record the device, Android version, build identity, permissions, battery settings and exact scenario. Sensor, background, audio, camera and Health Connect conclusions apply only to the tested conditions.

Remaining physical observations are listed in `OPEN_WORK.md` without blocking unrelated documentation or maintenance work.

## 1.7 Documentation-only changes

For changes limited to Markdown and tracked-file organization:

1. Verify all relative Markdown links.
2. Search current-facing documents for superseded product and environment claims.
3. Run `git diff --check`.
4. Confirm no runtime source, schema, dependency or configuration changed.
5. Review the complete rename/delete/rewrite diff.
