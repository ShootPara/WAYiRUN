# WAYiRUN — Cloud foundation handoff

## 0.1 Current deployment — October 5 weather repair

Current development Worker: `5b3b8fc8-f10a-47b0-8cd7-ae0a38c03dd7`. The bounded weather-reliability repair replaces the unsupported Workers `redirect: "error"` request with manual redirect rejection, supports WMO code 97, uses an eight-second deadline, and exposes meaningful bounded retry eligibility. No migration was required and none was pending. All 156 Worker tests and deployment dry-run passed; all 27 live smoke checks passed after deployment. A disposable authenticated outdoor-run canary completed a real Open-Meteo forecast lookup, verified immutable cached reuse, and confirmed complete cleanup. No personal coordinates or records were used or changed.

## 0.2 Previous deployment — September 28 photo repair

The historical sections below describe earlier releases. Previous development Worker: `ce8a67a6-ce47-4d49-9559-9314014d6efc`, deployed under the owner's explicit photo-repair authorization. Migrations 0008-0011 are applied, with none pending. All 148 local Worker tests, compile/dry-run, 27 live smoke checks and the authenticated synthetic photo/publication canary passed. The private database backup restored successfully in isolated SQLite. After synthetic cleanup, cloud photos increased from six in the backup to eight, including two current-format uploads. See ../PHOTO_SYNC_REPAIR.md for safeguards, recovery evidence, Android hardening and the required deployed-compatibility handoff gate. No production release or Git commit/push occurred.

## 1 Completed scope

Guide Section 4 was authorized after the user completed local GitHub and Cloudflare login. Implemented and deployed the development Worker, bound a dedicated D1 database, added a bootstrap migration, pinned dependencies/lockfile, tests, and reproducible deployment/smoke commands. This is Milestone 5 environment preparation, not completed account synchronization.

## 2 Verified resources

| Resource | Value |
| --- | --- |
| Git repository | `ShootPara/RunningApp` (private; existing main/history retained) |
| Feature branch | `codex/cloud-foundation` |
| Deployment branch | `development` |
| Cloudflare account | `6bf560a8b86852196c9898023e3b8d6b` |
| Worker | `wayirun-dev` |
| D1 database | `wayirun-dev-db` |
| D1 ID | `04bf8339-386b-4a03-80d7-12b4d1f99ffb` |
| Base URL | `https://wayirun-dev.unopenedparachute.workers.dev` |
| First deployed version | `1af54cf8-52e3-4e09-8003-c89ad419b2cd` |
| Deployment time | 2026-09-15 01:16 UTC / September 14, 9:16 PM EDT |

The database contains deployment metadata only. No existing Cloudflare application/database was modified. No Android build, phone install, production OAuth registration, custom domain, R2 bucket, or real-user upload was performed.

## 3 Verification

- Clean `npm ci` succeeds; npm audit reports zero vulnerabilities for the pinned package tree.
- TypeScript compiles and all six Node/Miniflare integration tests pass, exercising real workerd/D1 bindings.
- Local migration applies; a second application finds no pending migrations.
- Deployment guard, bundle dry run, remote migration, and Worker upload succeed.
- Remote migration ledger reports no pending migrations.
- Seven live HTTP checks pass: health/readiness 200, HEAD with no body, missing route 404, incorrect health method 405, and GET/POST run API both 503. Responses use no-store.

The initial older tool versions had audit findings. Final dependencies were updated and locked, including the Miniflare version shipped by Wrangler 4.131.2. The test harness uses its exported V4-options converter. Initial migration-test/API mismatches were corrected before deployment; final tests passed on a clean install. No authentication behavior is claimed from these tests: data APIs remain unavailable.

## 4 Git and next step

Only `worker/` and the environment setup guide belong to this cloud commit. Earlier Android changes, phone reports, and cumulative planning-document edits remain preserved locally and uncommitted. Main has not been replaced or force-pushed.

The user completed guide Section 5 on September 15. The screenshot shows the intended repository, development branch, worker root, build command, and deploy command. Non-production builds are also enabled with version upload; preview URLs remain disabled in configuration. A push-triggered deployment still needs observation independently of the previous local Wrangler deployment.

Then implement Google verification, authenticated ownership and sessions, and retry-safe completed-run synchronization. Real OAuth audiences/application identity and the disposition of pre-account local runs need their own explicit contract. The user's announcement-selector and phone music-testing deferrals remain in effect.

## 5 Account-session slice — September 15, 2026

Implemented Google RS256/claim verification using pinned jose 6.2.12, single-use five-minute challenges, one-hour hashed bearer sessions, account profile lookup, and session logout. Migration 0002 is additive and leaves existing metadata compatible. See AUTH_CONTRACT.md for the exact implemented contract and GOOGLE_SIGN_IN_SETUP.md in the repository root for user setup.

All 12 local tests pass in the actual bundled Worker with Miniflare/workerd and D1, including generated-key signature rejection, two-account separation, replay/concurrent exchange, session expiry/revocation, HTTP boundaries, and storage failure. This is backend verification only; no real Google sign-in, phone install, or Android change is claimed. GOOGLE_WEB_CLIENT_ID is deliberately unset. Public login rate controls and Android authorized-party configuration remain activation gates; private run APIs remain closed.

## 6 Deployment verification

Account-session commit `9a57b3e` was pushed to development. Workers Builds picked it up, applied migration 0002, and deployed version `8c43dd57-8109-42ff-b956-4b4824000ed7`, but its GitHub check reported failure. The user supplied logs confirming that the auth smoke check received the previous Worker's api_not_available response less than a second after deployment. Compilation, all tests, migration, and upload succeeded. Builds logs return 403 through the available CLI authentication, but GitHub check status and Wrangler deployment inspection are available.

The same guarded deployment completed locally on September 15 at 11:50 UTC, version `f0b1504d-ad16-41b8-83e2-1321c47cf1dd`. All nine live HTTP checks passed, and the remote migration ledger contains both migrations with no pending work. Local migration applied successfully; repeating it found no pending work. No remote account records were created by these checks.

Post-deployment smoke checks now retry the complete set up to six times with five-second gaps to tolerate rollout propagation. Two additional tests verify recovery from a transient response and failure after exhausted attempts; total 14 tests pass. Fix commit `48b805f` was pushed to development; its Git build verifies the pipeline separately.

Follow-up build `b2ba6e80-c1db-429d-96d5-70e4927d2afe` completed successfully for `48b805f`. GitHub reports Workers Builds conclusion success, confirming the configured tests, migration/deploy command, and all nine smoke checks completed in Cloudflare. The Git connection is verified end-to-end. Main remains at `895a2a4`; cumulative Android/root-document edits remain local. These final verification notes are retained locally after the successful deployment to avoid triggering a documentation-only redeployment.

Latest deployed version: `c45414bd-db09-4696-b6b3-1a5da5b3abb1`, September 15 at 11:53:09 UTC, serving 100% of development traffic.

## 7 Google client configuration and phone integration

The user supplied Web and development Android client IDs in project wayirun-development. Wrangler now binds those exact IDs and two auth rate limiters (30 per minute per client, 300 combined per location). Missing/failed limiters fail closed. Exact audience checking remains enforced, with only the configured Android authorized party additionally permitted. All 16 backend tests pass, including rate exhaustion and unavailable limiters. Smoke checks now expect missing account authentication to be rejected with 401 and malformed token exchange with 400; they do not create user accounts.

The Android signin-settings1 work remains local alongside the earlier Android baseline. It adds optional Google Credential Manager sign-in, encrypted no-backup session storage, and preserves original local run ownership. Run synchronization is not implemented. The user's newest launch/settings correction supersedes the old every-foreground setup wizard: retain the original Android splash, check runtime grants only on startup without interrupting restored active runs, and put editable settings behind one persistent top-right gear. Google device sign-in still needs an actual user test.

Cloud commit `2044f5b` passed Workers Builds end-to-end with both configured Google IDs and auth limiters. Android final verification: 58 JVM and 25 emulator tests pass, debug build and lint pass (zero errors, nine version advisories). Timestamped APK and focused phone checklist are recorded in TASKS.md Section 8.0.2. These final verification notes remain local after the successful cloud push.

## 8 Completed-run storage transport - September 15, 2026

Implemented owner-scoped resumable uploads, chunk hashes and bounds, immutable completion receipts, draft expiry/limits, and private paginated retrieval. Additive migration 0003 preserves accounts/sessions. Shared session verification and rate guards protect account and run routes. All 27 bundled Worker/workerd/D1 tests pass, including migration preservation, concurrent retries, account isolation, failed writes, exact binary round-trips, and expired-session resume. Test processes now have a 60-second deadline. No remote test accounts or run data are created.

This is server transport only: it preserves opaque archive bytes and validates manifest fields/integrity, not the measurement archive schema. Android encoding/decoding, explicit import, ownership/queue migration, and discard reconciliation remain required before enabling phone uploads. The user confirmed Google phone sign-in and chose explicit import in settings. Existing Android run records remain untouched.

Commit e257bd0 was pushed to development. Workers Builds 5a805e01-d39d-4ba7-85ee-79edeef6af4a completed successfully, including tests, migration, deployment and smoke checks. All nine live smoke checks also passed independently. A read-only remote ledger query confirms migrations 0001, 0002 and 0003. No production resources are used. These final deployment results remain local to avoid a documentation-only redeploy.

## 9 Discard protection for Android synchronization - September 16, 2026

Added migration 0004 and authenticated idempotent DELETE for cloud runs. Atomic deletion removes manifest/chunks while retaining only owner/run ID/time; a database trigger prevents old or racing uploads from resurrecting the run. Tests use the actual migration and cover deletion before begin, after completion, repeated deletion, racing completion, and foreign-owner isolation. All 29 bundled Worker/workerd/D1 tests pass. Android integration and its final test results are recorded in root TASKS.md; Android source remains local under the existing commit boundary.

Cloud-only commit `5b5e7ed` passed Workers Builds on development. All nine live smoke checks passed independently, and a read-only remote ledger query confirms migrations 0001 through 0004. Production is untouched. Final Android gates passed: 58 JVM tests, 34 emulator tests, debug build and lint (zero errors, 11 version advisories). Physical-phone sync remains unverified. These final results remain local to avoid a documentation-only redeployment.

## 10 Restore transport support - September 16, 2026

Added owner-scoped, validated, paginated deletion-ID retrieval and exact canonical manifest text for Android hash verification. No database migration or data rewrite is required. All 30 bundled Worker/D1 tests pass, including feed pagination, foreign-owner isolation, invalid cursors and exact manifest hash verification. User confirms sync1 phone checks pass; a read-only D1 query confirms two completed cloud runs. Cloud-only commit `944338a` passed Workers Builds on development. All nine live smoke checks passed independently; a repeat read-only count still shows two completed runs. No server migration or user-data mutation was needed. Production remains untouched. The user committed the working baseline as `4306728`; that commit was preserved and not pushed by this pass. Deployment used a separate scoped worktree/branch so only Worker files reached development.

Final sync2 Android verification: 58 JVM and 45 emulator tests passed, debug build/lint passed (zero errors, 11 version advisories), and APK signature verified. Real Google cross-device restoration remains a device check; no user run data was changed by verification.

## 11 Desktop history foundation - September 16, 2026

Implemented private Google browser sessions, history pagination, loaded-set totals, units and validated detail/splits/settings. Native bearer behavior is preserved behind a separate browser cookie/CSRF boundary. All 33 bundled Worker/D1 tests pass. Browser fixture checks passed for sign-in flow, totals/units, literal account text, corrupt archive rejection, detail, narrow layout and logout clearing; rendered desktop and narrow screenshots were inspected. No migration or Android change. Cloud-only commit `83fbf98` passed Workers Builds; all 14 live smoke checks passed independently. A read-only remote count still confirms two completed runs. The live page loaded the Google sign-in frame without JavaScript errors. The user confirmed authorized-origin setup; actual Google browser login remains unverified. Final verification notes remain local to avoid a documentation-only redeployment. Production and the existing phone APK are unchanged.

## 12 Desktop Google sign-in repair - September 17, 2026

Cloud-only commit bd9bd8a passed Workers Builds on development. Corrected the widget's style nonce and origin referrer policy; capped its container at 260px and icon at 20px. All 34 Worker/D1 tests and 14 independent live checks passed. Actual Google rendering was verified at desktop and 390px widths with no fresh browser errors or horizontal overflow. The browser tool refused pointer activation inside the fractional-position iframe, so real-account sign-in completion remains a user check. No user run data, Android APK, Google Console configuration or production resources were changed. See TASKS.md Section 9.1.2.

## 13 Desktop route maps - September 17, 2026

User confirmed real browser sign-in and the two-run history. Cloud-only commit 08f6fa7 passed Workers Builds on development. All 45 Worker/D1/geometry/lifecycle tests and 18 independent live smoke checks passed. Local browser fixtures verified separate GPS sections, zoom/Fit route, indoor/no-GPS/single-point behavior, corrupt archive rejection, failed tile handling, logout clearing and 390px layout. Leaflet 1.9.4 is self-hosted; OpenStreetMap Standard supplies only base-map tiles. Real route alignment is the user visual check. See MAP_CONTRACT.md for provider limits and privacy. No Android build/install, database migration, user-data mutation or production deployment occurred. Stats periods/sorting/display/charts remain explicitly deferred.

## 11 Complete CSV export - September 17, 2026

User accepted maps and chose one CSV containing summary and detailed records. Development-only commit 6cee9e0 passed Workers Builds; all 53 automated tests and 19 live smoke checks pass. Browser fixture export downloaded a 9,710-byte CSV containing four RUN, seven GPS_POINT, four SOURCE_SEGMENT and four SPLIT rows despite only two history rows being loaded. Independent CSV parsing confirmed records; automated reconstruction also covers measurements/active intervals, exact values and multiline Unicode. Cancel, sign-out and corrupt archive checks produced no further downloads. Browser download-event notification timed out, but the actual file appeared in Downloads and was parsed successfully. Controls were visually checked at 390px. Fixture server/tab/worktree were cleaned up.

Next: desktop selection/deletion as a separate bounded slice. User check: refresh development, Export all runs, confirm both saved runs are in the CSV. No Android changes or new APK, production deployment, or real run deletion. Root documents/Android commits remain user-owned.


## 12 Export throttling and persistent sessions - September 17, 2026

Repair e5e45a5 is live on development; Workers Builds, 57 automated tests and 19 live smoke checks passed. Four real runs exposed shared 30/min authentication throttling that one-chunk fixtures missed. Transfers now use independent 300/client and 3000/global limits per minute, and CSV export honors Retry-After with up to three cancellable retries. Browser fixture confirmed automatic recovery and complete downloaded CSV. Forty-chunk Worker regression passed with an exhausted sign-in budget.

Session duration is now 90 days, with valid browser sessions renewed on account access. Expired/revoked credentials remain invalid; already-expired users need one sign-in. Native apps receive longer sessions at their next sign-in without a new APK. Retest the user's four-run export before continuing deletion. Temporary server/tab/worktree cleaned up; no real-run changes or production deployment.


## 13 Real CSV acceptance and desktop deletion - September 17, 2026

Verified the user-provided export: four RUN records, 6720 GPS_POINT, 6722 MEASUREMENT, 11 SPLIT, four ACTIVE_INTERVAL and eight SOURCE_SEGMENT records. Record indexes/IDs, checkpoint summaries, split distance/time sums and nested intervals/segments match. No raw private route data was copied into the repository.

Desktop deletion is live as 2dcd76a. All 58 tests pass, Workers Builds succeeded, 19 live smoke checks passed and the deployed HTML contains the deletion interface. Browser disposable fixtures verified selected-only scope, confirmation cancel/Escape, sequential partial failure/retry, totals, unselected runs, sign-out cleanup and narrow modal layout. No real user runs were deleted; no Android changes/install or production deployment. Test infrastructure was cleaned up. User can inspect confirmation then Cancel, or use a disposable run for actual deletion and subsequent phone-sync reconciliation. Next milestone is AI coaching planning after acceptance; charts/statistics expansion remain deferred.


## 14 Per-user OpenAI key storage - September 18, 2026

Deployed directly without Git commit/push per user preference. Worker version 1905ec88-bad5-411e-b20e-a0515cf043de, migration 0005; COACHING_KEYRING verified present. All 65 Worker tests and 20 live smoke checks pass. Android assembly/lint and two focused emulator tests pass. Personal-key provider validation remains the user's check. See KEY_STORAGE_CONTRACT.md for secret recovery limitations and rotation procedure. No real API key was used in tests and no paid coaching generated. Preserve uncommitted source; later coaching execution is not yet implemented.

## 15 September 18 coaching integration update

Development migration 0006 and Worker version 88261b78-b67b-4993-b3e8-6c388c3c7aff add authenticated coaching request/status/audio routes and durable single-attempt jobs. Current/previous run deletion removes associated coaching audio; a dependent-job receipt prevents regeneration after previous-run deletion. All 80 automated tests and 22 live smoke checks pass. See COACHING_JOBS_CONTRACT.md. Android assembly/lint, 58 JVM tests and seven focused emulator tests pass; actual Cedar quality/model access awaits phone acceptance. No personal API key used by the agent, no production deployment and no commit/push.

## 16 Saved desktop coaching and CSV v2 - September 18, 2026

The user accepted the phone coaching update. Development Worker 200767ab-9aa3-4476-b68d-844cc5bd9f79 now serves saved recap/manual audio replay and complete CSV coaching metadata/audio. All 86 automated tests and 24 live smoke checks pass. No new migration or APK. An initial Cloudflare D1 authorization error was transient: the unchanged guarded deployment succeeded on retry with the existing login, without changing permissions.

Local browser verification covered literal recap text, WAV playback, empty coaching, navigation cleanup and an actual four-run CSV download. Independent parsing reconstructed 1,440,044 audio bytes from 88 chunks with the expected SHA-256. The fixture used synthetic runs and silent test audio; no personal key/provider call was made. Real saved Cedar replay is the user's development-site check. Voice selection, expanded statistics and charts remain deferred; achievements planning is next. This slice is uncommitted per the user's preference.

## 17 Achievements and CSV v3 - September 18, 2026

Development version e192834c-8396-41b4-82d8-e77273d2d972 adds authenticated-history-derived achievement cards and complete CSV award records; 93 automated tests and 25 live smoke checks pass. No cloud database migration or paid provider calls. Initial D1 authorization error recovered after checking the existing login and retrying the unchanged guarded deployment; no permissions changed. Phone implementation and artifact are documented in ACHIEVEMENTS_PLAN.md 1.8. Photos/public run pages are next. No commit/push.

## 1.20 September 19 photos deployment

Migration 0007 and Worker 13864438-3308-4c93-95ea-0e47619f0b4e deployed to existing development only. Photos/public-page/privacy/deletion/CSV v4 are implemented; 29 live checks pass. Full suite passed 99 tests; final duplicate-upload receipt fix passed 20 focused photo/export tests. Photos use existing D1, max 1,000,000 JPEG bytes/run, with run foreign-key cascade. No production, real-user image publication or Git backup performed. See PHOTOS_PLAN.md and TASKS.md 17.
