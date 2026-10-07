# 1 Photo synchronization repair — September 28, 2026

## 1.1 Scope and diagnosis

the owner authorized the repair, required cleanup, and matching backend deployment after the investigation. Preserve existing runs, photos, credentials, public links, repository history and unrelated working-tree edits. No production release, commit/push, or physical-phone installation is included.

Cloudflare still served September 19 Worker `13864438-3308-4c93-95ea-0e47619f0b4e`, with migrations 0008–0011 pending. The current Android PhotoRecipe always includes a fifth `weather` flag, even when false. The old handler required exactly four flags. An isolated reproduction using the historical handler and synthetic DB/auth adapters returned 200 for the old format and 400 invalid_photo for both new weather=false and weather=true formats. The rejection occurs before saving image bytes.

Local matching-version tests did not exercise the old deployed backend. Android PhotoSync silently retained failed photos and selected only one pending image, allowing a rejected image to obstruct later work. Private website storage and intentional public sharing remain separate requirements.

## 1.2 Backup and deployed recovery

Exported the development database outside the repository to `%LOCALAPPDATA%/WAYiRUN/backups/2026-09-28-photo-repair/before-photo-repair.sql` (33,268,814 bytes). Restored it into an isolated in-memory SQLite database; integrity_check returned ok and six existing photos were present. This backup contains private data and must remain outside Git and user-facing artifacts.

All 148 Worker tests, TypeScript compilation and deployment dry-run passed. The existing guarded deployment script applied additive migrations 0008_run_locations, 0009_public_runs, 0010_run_weather and 0011_photo_weather, then deployed Worker `ce8a67a6-ce47-4d49-9559-9314014d6efc` to the existing WAYiRUN development site. The deployed bundle includes the already-prepared route, publication, weather and coaching changes matching the current APK; no new feature work was added here. Remote migration listing now reports none pending. All 27 existing live smoke checks passed.

The deployed authenticated photo check passed legacy and current weather-disabled uploads, idempotent duplicate receipts, exact image-byte hashes, unauthorized image rejection, private-by-default storage, explicit sharing, short redirects, public data/image access, hidden-photo revocation with private retention, and complete unsharing. It creates a unique synthetic development account/session/run and uses the repository's synthetic JPEG, then deletes that account and verifies cascading removal of its records. It never uses a real account, personal photo or paid provider call. Its first execution exposed CLI import progress mixed into JSON output; the harness now parses only read-query JSON and the exact initial synthetic cleanup was independently verified before rerunning successfully.

After synthetic cleanup, aggregate cloud counts showed eight photos, two with the newly supported weather flag, versus six in the pre-deployment backup. This is evidence of queued uploads recovering after deployment. Individual user-visible photo acceptance remains distinct from these aggregate/API observations.

Do not blindly roll back to the September 19 Worker: it rejects current photo metadata and uses obsolete publication semantics. Keep additive schema changes and prefer a forward repair that preserves unshare/photo-visibility decisions. Do not restore the database backup over later successful uploads without a separately reviewed data-recovery procedure.

## 1.3 Android hardening

PhotoSync now selects a bounded batch of five eligible run IDs and loads one JPEG at a time. Eligibility requires a finished run, matching cloud owner and confirmed UPLOAD operation. Failed attempts rotate behind untouched work using a persisted lastAttemptMs. One rejected or unsynced run no longer monopolizes the queue. Existing scheduler retry and periodic recovery remain in use.

Room v9 adds nullable syncError and default-zero lastAttemptMs without rewriting photos. Success still requires an exact revision, byte count, SHA-256, options and weather receipt. Result updates check revision, owner and upload eligibility; late replies cannot acknowledge a replacement or recreate a deleted photo. Session changes suppress acknowledgments, cancellation remains cancellation, and failures retain all local image data. Error storage contains only fixed safe categories, never response bodies, credentials or image contents.

Gear settings distinguish run synchronization from pending photo counts and show a contextual photo failure. The kept-photo dialog shows the same safe explanation and updates when synchronization succeeds. Retry sync remains the existing action. Build label: `0.1.0-dev-photo-sync1`.

## 1.4 Verification and handoff

Android `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest` passed. The final UI wording build passed again: 118 JVM tests, zero failures/errors/skips; lint has zero errors and 21 existing warnings. Room schema 9 was generated with the expected additive columns/default.

The first focused emulator batch passed all 28 cases: PhotoSyncTest (5), PhotoSyncStatusTest (1), PhotoWeatherTest (5), PhotoEditorTest (6), PhotoRenderTest (3), SyncMigrationTest (8). This covers failed-first-photo progress, bounded fair retries, pending/foreign/local runs, bad receipts, HTTP categories, cancellation/expired sessions, replacement/deletion/account-switch races, error clearing, stable weather, editor lifecycle and migration preservation. The final wording and adjacent regression batch passed all 32 cases: PhotoSyncStatusTest (1), RunDatabaseTest (10), PublicationSyncTest (13), RunScreenTest (6), RunEntryTest (2). Across both batches, 59 distinct instrumentation cases passed. Reports are in android/app/build/verification/photo-sync-repair/.

Final application APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-28_18-07-05_EDT.apk`, 40,290,032 bytes, SHA-256 `5E66E346D08A99D8221664F994E99A8F051F3130BCD7625136D50882C5267FEE`. This is an in-place update with the existing debug application identity/signature. The database upgrade preserves pending photos; it does not require uninstalling or clearing data.

Final light/dark 200-percent text screenshots were inspected: run/photo counts, the failure explanation and Retry sync remain readable and reachable. The visual harness was changed from an unsynchronized system screenshot to Compose captureToImage, then its case passed again and both theme images were confirmed. Screenshots and the additional test transcript are in the same verification directory. The application APK signature verifies. The instrumentation APK was archived to `%TEMP%/wayirun-photo-repair-test-apks-20260928/`, leaving only the current application APK beneath the repository. Temporary cloud-test records and recovery files are removed; the private pre-repair backup is retained outside Git.

Only emulator-5554 is used. Real camera/gallery hardware, physical sensors and audible behavior are not inferred from emulator/API tests. Existing physical-device acceptance items remain separate.

## 1.5 Future cloud-dependent APK handoff gate

Before labeling a cloud-dependent APK ready for the phone, record its build identity, actual deployed Worker version and remote migration state. Matching local fixtures are insufficient. If the matching backend is not deployed, keep the APK an internal verification artifact and state the dependency explicitly; never silently hand it off against the old API.

For an authorized development rollout, run the full Worker test/dry-run gate, existing target-guarded deployment and smoke checks, then from worker/ run:

```powershell
node scripts/verify-photo-deployment.mjs --confirm-development-write
```

This command deliberately writes disposable synthetic records only to the hard-guarded development target. It requires existing administrative CLI access, keeps its short-lived bearer token in process memory, and removes its synthetic data in finally. A cleanup failure preserves exact scoped recovery SQL in the reported temporary directory and must be resolved before handoff. It is not part of ordinary local tests and must not target production. For changes to weather-enabled photo metadata, retain the exact-snapshot local tests and extend deployed verification as appropriate without billing a real user's provider.

## 1.6 Phone acceptance

the owner confirmed on September 28 that the photos are now visible on the website. This confirms the reported missing-photo problem is resolved; separate camera, sensor and audio acceptance items remain as documented.

Upgrade in place using the final application APK; do not uninstall or clear storage. Open gear settings, confirm the owning account and use Retry sync if photos remain pending. Refresh the signed-in website and check the formerly missing photos. Then keep a new photo and verify its private website copy; test public sharing separately only when intended. No retake should be necessary for a photo still retained in the local queue.

## 1.7 Repository checkpoint

After confirming recovery, the owner explicitly authorized committing and pushing the completed workspace. The checkpoint includes the completed issue-plan milestones, photo repair, tests and handoff documentation on `codex/account-sessions`. Generated APKs, local test output, credentials and the private database backup remain outside Git. The existing `main` default and separate `development` deployment branch are preserved. The latter has diverged history and is older than the manually verified deployed Worker; reconcile and test it before any future deployment-branch push to avoid reverting the repair. This checkpoint does not promote a new deployment.
