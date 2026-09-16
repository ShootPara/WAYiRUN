# WAYiRUN — Cloud foundation handoff

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

Implemented private Google browser sessions, history pagination, loaded-set totals, units and validated detail/splits/settings. Native bearer behavior is preserved behind a separate browser cookie/CSRF boundary. All 33 bundled Worker/D1 tests pass. Browser fixture checks passed for sign-in flow, totals/units, literal account text, corrupt archive rejection, detail, narrow layout and logout clearing; rendered desktop and narrow screenshots were inspected. No migration or Android change. Real Google browser login awaits the authorized-origin setup explained to the user. Development deployment verification follows below.
