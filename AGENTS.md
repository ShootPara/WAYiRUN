# 1 Running App project instructions

## 1.1 Working directory and authority

Work in `<repository-root>`. `REQUIREMENTS.md` is the product contract, `CURRENT_STATE.md` is the implementation snapshot, `OPEN_WORK.md` contains unresolved work, and later explicit user decisions override earlier proposals. Historical material under `docs/history/` is evidence only.

Keep decimal heading and subheading numbering in authored project documents.

## 1.2 Scope discipline

Follow plan → guardrails → execute → verify. Keep planning and implementation distinct. Implement only the bounded task requested by the user and preserve unrelated edits. This directory is the Git repository root; do not create a nested repository.

`main` is canonical. The user handles commits, pushes, tags, deployments and repository visibility unless explicitly delegated. Do not deploy, install to a device or mutate production resources without task-specific authorization.

## 1.3 Implementation boundaries

Use one Android application module. Pure run calculations belong in the Kotlin package `com.example.runningapp.domain` without Android dependencies. The production application ID is `com.unopenedparachute.wayirun`; the Kotlin namespace remains `com.example.runningapp`; debug uses `com.example.runningapp.debug`.

Keep GPS, steps, audio, storage and network access behind their existing small adapters. Do not add speculative abstractions, development authentication bypasses or embedded credentials. Development and production configurations must remain separate and fail closed.

Applied Room and D1 migrations are immutable. Add forward migrations rather than editing migrations that may already have run.

## 1.4 Verification

For ordinary Android verification, run from `android/` on Windows:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

Run release, instrumentation, Worker, browser, device or deployed-service checks only when relevant to the active task. A compiled APK is not evidence of sensor, media, background or physical-device behavior; report those evidence classes separately.

## 1.5 Secrets and generated output

Keep local SDK paths in ignored `android/local.properties`. Keep signing material, passwords, cloud secrets, API keys, private backups, personal exports, sensitive logs and generated build directories out of Git. Owner signing and migration material under ignored `private-signing/` must remain outside source control.

Never echo secret values in reports. Preserve applied migration records and production/development guard boundaries.

## 1.6 Documentation and history

Current-facing documents must describe the checked-in source, not a historical milestone. Preserve completed plans, phone reports and handoffs under `docs/history/` and keep them explicitly non-authoritative. Use `git mv` when reorganizing tracked history.
