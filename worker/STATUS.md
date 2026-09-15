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
