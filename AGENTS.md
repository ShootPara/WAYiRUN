# Running App — Project instructions

## 1 Working directory and authority

Work in `<repository-root>`. Read `REQUIREMENTS.md`, `TASKS.md`, and `the then-current working guide` before starting a milestone. Product requirements and subsequent explicit user decisions override technical proposals. Keep decimal heading/subheading numbering in authored documents.

## 2 Scope discipline

Follow plan → guardrails → execute → verify. Keep planning and implementation as distinct phases. Implement only the current bounded milestone. Preserve the existing files and user edits. This directory is the Git repository root; do not create another nested repository. The existing origin and history must be preserved. The user handles committing and pushing unless explicitly delegated. Do not deploy or add unrequested features.

## 3 Implementation boundaries

Use one Android app module. Keep future run calculations in the Kotlin package `com.example.runningapp.domain` without Android dependencies. Keep GPS, steps, audio, and storage behind small adapters when their milestones begin; do not create speculative abstractions now. Never ship a development authentication bypass or embed credentials in the app. The current application ID is provisional and must not be registered for production OAuth.

## 4 Verification

From `android/` on Windows, use `./gradlew.bat :app:assembleDebug :app:lintDebug --console=plain`. Add meaningful controller tests in their milestone and run `:app:testDebugUnitTest` then. A compiled APK is not evidence of sensor, media, or screen-off behavior; report device checks separately. Do not install to or launch on a connected device without that step being within the active task.

## 5 Output and secrets

Report actual build results and artifact paths. Keep local SDK paths in ignored `android/local.properties`. Keep signing material, cloud secrets, API keys, logs containing sensitive data, and generated build directories out of source control. Do not show raw tool logs in user-facing summaries.
