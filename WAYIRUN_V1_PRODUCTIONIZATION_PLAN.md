# 1 WAYiRUN 1.0 productionization and data-migration plan

Version: 1.0  
Status: Controlling execution plan; Milestones 1 through 6 complete; Milestone 7 not started
Authority: `REQUIREMENTS.md` remains the product-behavior source of truth. Later explicit user decisions override this plan.

## 1.1 Objective

Move the completed WAYiRUN feature baseline into a durable production system without redesigning the product, losing existing user data, weakening owner isolation, or destroying the development environment.

The required end state is:

- a real, non-debuggable Android release application at version `1.0.0`;
- permanent Android identity and signing with recoverable owner-controlled credentials;
- separate development and production Android, Google OAuth, Worker, D1, secrets, URLs, and rate-limit namespaces;
- existing durable development user data migrated to production and reconciled;
- a signed production APK and, if distribution requires it, AAB;
- production acceptance evidence, operating documentation, backup/restore instructions, an accepted commit, and tag `v1.0.0`;
- the development environment preserved for future development.

This is productionization, migration, and verification work. It MUST NOT add product features, redesign screens, reopen superseded product choices, or tune tracking behavior during acceptance.

## 1.2 Conversation and execution discipline

At the start of every subsequent WAYiRUN V1 turn, the working agent MUST:

1. Read this file, `AGENTS.md`, and any authoritative document changed since the previous turn.
2. Inspect Git status and identify the current milestone and its entry gate.
3. State the current milestone and the smallest bounded action being performed.
4. Complete, verify, and record one milestone before beginning another.
5. Re-read this file before proposing the next action so a conversational detour cannot silently change the sequence.

Milestone status MUST use exactly `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, or `COMPLETE`. A milestone becomes `COMPLETE` only when its exit evidence exists. Update this plan's status table and evidence references as work progresses.

Planning and execution remain separate. Creating this plan does not authorize remote resource creation, deployment, data migration, physical-device installation, committing, pushing, or tagging. Those actions occur only inside their named milestone after the user authorizes that milestone. The user controls commits, pushes, and tags unless explicitly delegated.

If a gate fails, stop advancement, diagnose within the current milestone, and record the failure. Do not weaken tests or skip forward.

## 1.3 Non-negotiable guardrails

- Inspect before changing. The live repository and authenticated provider state override stale conversation details.
- Preserve all user edits and the existing Git history. Do not create another repository.
- Never print, log, commit, export into the repository, or place in an APK any secret value, signing password, private key, OAuth secret, session token, API key, personal archive, or production backup.
- Never point production at the development D1 database. Never point debug builds at production.
- Applied D1 migrations `0001` through `0011` are immutable. Future schema changes are additive numbered migrations.
- Production data is never reset for testing. Destructive production tests require isolated synthetic records and explicit approval.
- Development remains usable after cutover. Its data is not automatically authoritative after the cutover boundary.
- Runs and photos remain private by default. Owner isolation and deletion tombstones must survive migration.
- Release acceptance failures become bounded release defects; acceptance is not permission to improvise product changes.
- A successful build is not evidence of physical GPS, steps, background, audio, camera, Health Connect, or end-to-end behavior.

## 1.4 Verified planning baseline — October 6, 2026

These are planning facts and MUST be rechecked in Milestone 1 before execution:

- Canonical branch: `main`; accepted Settings checkpoint `1e00e8e` was integrated without product-content changes by history-preserving merge `897cac5`.
- Detector/GPS auto-pause and weather reliability are committed in HEAD `cadecf2`; the route-noodle placement correction is committed in ancestor `7c88edb`.
- The Settings overhaul and reconciled V1 documentation are committed in accepted checkpoint `1e00e8e` and reachable from canonical `main`.
- Android namespace/application ID is provisional `com.example.runningapp`; debug adds `.debug`.
- Android version is `versionCode 1`, `versionName 0.1.0`; debug adds `-dev-photo-sync1`.
- The functional Android product exists only in `src/debug`; `src/release` is a name-only shell.
- The debug Google Web client ID is compiled into `BuildConfig`; production OAuth does not yet exist in the release variant.
- Android service code contains several hard-coded `https://wayirun-dev.unopenedparachute.workers.dev` origins and development-specific link validators.
- Worker config is `worker/wrangler.jsonc`, Worker `wayirun-dev`, D1 binding `DB`, database `wayirun-dev-db`, and `APP_ENV=development`.
- `deploy-dev.mjs` and `deploy-guard.mjs` intentionally reject alternate environments, routes, Workers, and databases. Production needs an equally strict separate path; the development guard must remain intact.
- The Worker/browser also contains hard-coded development origin/link rules that must become build/environment-bound without accepting caller-selected origins.
- D1 migrations are `0001` through `0011`.
- Current cloud storage is D1 only. Run archives, JPEG photos, coaching WAV chunks, weather, locations, publication state, and encrypted OpenAI key envelopes are stored in D1. No current R2, KV, or Durable Object binding was found; do not invent one during V1.
- D1 contains durable source data mixed with transient operational data. Authentication sessions and login challenges are not equivalent to durable user history.
- OpenAI keys are encrypted with the `COACHING_KEYRING` secret. Migrating ciphertext without compatible keyring material makes saved keys unusable.
- Physical acceptance and the real-device auto-pause bug remain open in `OPEN_WORK.md`.

## 1.5 Required product-owner decisions

Milestone 2 must resolve and record these decisions before permanent identities or resources are created:

1. Permanent Android application ID. The provisional `com.example.runningapp` MUST NOT be registered for production OAuth.
2. Public production origin: custom domain/subdomain or a deliberately accepted permanent `workers.dev` name.
3. Distribution channel: V1 uses an owner-controlled signed APK. Google Play remains a deferred future option and is not a V1 gate.
4. Signing-key owner, secure storage locations, backup/recovery custodians, and password-handling method.
5. Google Cloud project/account ownership and whether development and production clients share a project or use separate projects.
6. Cloudflare account ownership, production resource naming, and billing/limits acceptance.
7. Privacy-policy URL, support/contact details, retention/deletion policy, Health Connect declarations, and required store disclosures.
8. Production telemetry/observability and redacted incident-log policy. Do not enable sensitive request/body logging.
9. Maintenance-window and cutover timing, including how old debug builds will be retired from real use.
10. Encrypted OpenAI-key continuity: securely reuse compatible keyring material, perform a controlled re-encryption, or require users to re-enter keys. This decision must never expose the keyring or plaintext keys.

## 1.6 Milestone status

| Milestone | Scope | Status | Exit evidence |
| --- | --- | --- | --- |
| 0 | Approve controlling plan | COMPLETE | This file created from live repository review |
| 1 | Reconcile and checkpoint source | COMPLETE | Accepted checkpoint `1e00e8e` integrated into canonical `main`; repository normalized and verified |
| 2 | Resolve production decisions and compliance prerequisites | COMPLETE | `V1_MILESTONE_2_DECISION_RECORD.md`; owner identity/domain/distribution choices and technical policies recorded |
| 3 | Design exact environment/resource map | COMPLETE | `V1_ENVIRONMENT_RESOURCE_MAP.md`; exact boundaries approved and `slopcopy.com` ownership verified read-only |
| 4 | Implement environment-safe Worker configuration | COMPLETE | Fail-closed runtime/freeze configuration, isolated guarded production config, and 165 passing Worker tests |
| 5 | Promote the functional Android app into release | COMPLETE | Shared functional product builds in debug/release; variant and artifact gates pass with production OAuth intentionally pending |
| 6 | Establish owner-controlled production signing | COMPLETE | Ignored two-copy custody, verified certificate fingerprints and signed APK |
| 7 | Configure production Google OAuth | NOT STARTED | Verified production clients and real sign-in preconditions |
| 8 | Create production Cloudflare resources and schema | NOT STARTED | Bound empty migrated schema; no production traffic |
| 9 | Build and rehearse migration | NOT STARTED | Repeatable data-only tool and successful disposable rehearsal |
| 10 | Take final backup and perform controlled migration | NOT STARTED | Frozen source boundary, immutable backup, production reconciliation |
| 11 | Deploy and smoke-test production Worker/browser | NOT STARTED | Deployment ID, route, bindings, and safe smoke evidence |
| 12 | Build signed WAYiRUN 1.0 artifacts | NOT STARTED | Verified APK/AAB metadata, hashes, signature, and endpoint audit |
| 13 | Perform physical and end-to-end production acceptance | NOT STARTED | Recorded pass/fail matrix; all blockers resolved |
| 14 | Cut over operations and document recovery | NOT STARTED | Canonical-production declaration and operator runbook |
| 15 | Finalize version control and V1 marker | NOT STARTED | Accepted commit, push, `v1.0.0` tag, clean status |

Google Play publication is explicitly deferred outside the V1 milestone sequence. If later authorized, a dedicated milestone must handle Play App Signing, AAB upload, the Play signing certificate/OAuth addition, listing metadata, Data Safety, Health-app declarations, account-deletion/store requirements, testing tracks and then-current verification rules.

## 1.7 Milestone 1 — reconcile and checkpoint source

### 1.7.1 Entry

Milestone 0 is complete. No remote mutation is permitted.

### 1.7.2 Work

- Re-read all authoritative documents and the complete current Git diff.
- Verify the Settings overhaul from the prior milestone, including its generated APK/test evidence.
- Resolve or document any unrelated user edits without overwriting them.
- Run the normal Android and Worker baseline gates appropriate to the current tree.
- Record branch, HEAD, upstream status, Git status, tool versions, Android IDs/versions/source sets, API origins, Worker/D1 configuration, bindings, migration list, secret names only, signing state, OAuth IDs represented in the repository, and remote-storage classes.
- Update authoritative documents if their stated current state is now stale.
- Present the exact commit scope to the user. Commit and push only if the user explicitly delegates them.

### 1.7.3 Exit

- The accepted feature baseline is clean and recoverable in Git.
- The source-state record contains no secret values.
- Production work begins from a known commit, not the current dirty tree.

### 1.7.4 Pass 1A and 1B evidence — October 6, 2026

Pass 1A established that the branch and upstream are synchronized at `cadecf2`; detector/GPS and weather work are committed in that HEAD, route-noodle placement is committed in `7c88edb`, and the only dirty implementation scope is the Settings overhaul. Android remains a debug-only functional product with provisional identity and hard-coded development origins. Worker configuration remains development-only, uses D1 plus rate-limit bindings, has migrations `0001` through `0011`, and expects the secret name `COACHING_KEYRING`. Pass 1A Worker verification passed TypeScript compilation, Wrangler dry-run and 156 local tests with zero failures or skips and no remote contact.

Pass 1B repaired only ignored generated Android output by clearing its Windows `ReadOnly` attribute. The required Android gate then passed: 125 unit/debug-unit tests, zero failures/errors/skips, successful debug assembly, and lint with zero errors and 21 warnings. The generated APK is `android/app/build/outputs/apk/debug/WAYiRUN-2026-10-06_09-39-19_EDT.apk`, 40,355,568 bytes, SHA-256 `773482503562053C368A137161C93BDAACFEC638A6A988A485421C449A231F83`. No instrumentation, device action, remote mutation, deployment, schema/runtime change, commit or push occurred during that pass.

The approved checkpoint was committed as `1e00e8e`. GitHub's canonical/default branch was confirmed as `main`; the checkpoint was integrated without conflicts or product-content changes by normal merge `897cac5`. The old `development` line was explicitly reconciled as superseded rather than merged because its unique Leaflet/OpenStreetMap basemap behavior conflicts with the accepted provider-independent route-noodle design. Canonical `main` was pushed and verified before the accepted feature branches were retired. Milestone 1 completed before Milestone 2 began.

## 1.8 Milestone 2 — resolve production decisions and compliance prerequisites

### 1.8.1 Work

- Resolve every item in Section 1.5 with exact values or explicit policies.
- Confirm package-name availability before creating OAuth clients or publishing artifacts.
- Determine Play/App Signing requirements before key generation.
- Produce a privacy/data-flow inventory covering Google identity, precise route data, photos, OpenAI transfer, Health Connect writes, public sharing, exports, and deletion.
- Define retention for authentication sessions, incomplete uploads, deletion markers, coaching, photos, backups, and operational logs.
- Confirm Cloudflare and Google account ownership/recovery access.

### 1.8.2 Exit

An approved decision record exists. No permanent resource is created with a guessed name, domain, package ID, credential owner, or legal URL.

### 1.8.3 Completion evidence — October 6, 2026

`V1_MILESTONE_2_DECISION_RECORD.md` records the owner-approved package `com.unopenedparachute.wayirun`, production origin `https://wayirun.slopcopy.com`, current owner-controlled signed-APK distribution, deferred optional Google Play publication, and public identity `UnopenedParachute` / `unopenedparachute@gmail.com`. It also fixes separate production OAuth and Cloudflare resources, privacy/account-deletion/Health Connect obligations, minimal redacted telemetry, data-class retention policy, compatible `COACHING_KEYRING` continuity, and an immutable-backup/rehearsal/reconciliation cutover policy that treats existing phone and development data as irreplaceable until production acceptance passes.

No permanent identity, key, OAuth client, DNS route, Worker, D1 database, secret, deployment, migration, build, or device action occurred. Milestone 2 completed before Milestone 3 began.

## 1.9 Milestone 3 — design the exact environment and resource map

### 1.9.1 Work

Produce a reviewed table with exact values for:

| Concern | Development | Production |
| --- | --- | --- |
| Android application ID and variant | Existing debug identity | Approved permanent release identity |
| API/web origin | Existing development Worker | Approved production origin |
| Google Web/Android OAuth clients | Existing development clients | Dedicated production clients |
| Worker name/config/deploy command | `wayirun-dev` and guarded dev path | Dedicated guarded production path |
| D1 database and binding | `wayirun-dev-db` / `DB` | Dedicated production D1 / `DB` |
| Rate-limit namespaces | Existing development IDs | Distinct production namespace IDs |
| Secrets | Development secret set | Independently configured production secret set |
| Backups | Non-canonical development backup | Durable restricted production backup policy |

Inventory actual provider state with authenticated read-only tooling. List secret names, never values. Confirm that D1 remains the only storage binding unless implementation changed after this plan.

Define rollback boundaries:

- Before production accepts writes: production may be discarded and recreated from the preserved source backup.
- After production accepts writes: do not blindly roll back to development or an old snapshot. Freeze writes, preserve both states, and use a forward repair/reconciliation plan.

### 1.9.2 Exit

The resource map, cost/limit assumptions, maintenance boundary, and rollback rules are approved before implementation.

### 1.9.3 Completion evidence — October 6, 2026

`V1_ENVIRONMENT_RESOURCE_MAP.md` records the verified development inventory and exact production names, paths, Custom Domain configuration, fail-closed deployment guards, Android variant boundary, OAuth/D1 placeholders, data-migration classification, phone-local reconciliation gate, encrypted two-copy backup policy, retention periods, V1 Open-Meteo/Nominatim posture and pre-write/post-write rollback rules.

Authenticated read-only Cloudflare verification confirmed `slopcopy.com` is an active, unpaused full zone in account `6bf560a8b86852196c9898023e3b8d6b`. No production resource, DNS record, Custom Domain, OAuth client, key, secret, deployment, migration, Android configuration, build or device action occurred. Milestone 3 is complete; Milestone 4 has not started.

### 1.9.1 Pre-Milestone 4 coaching durability prerequisite — COMPLETE

The Android client now records selected cloud coaching as a durable, owner-scoped Room request before any coaching POST. Room migration 9→10 is additive: it creates `coaching_requests` with a run foreign key and cascade deletion while preserving all existing run, sync, photo and related data. Each request owns one stable operation ID; transport failures and 408/429/5xx responses retain it for bounded backoff and WorkManager recovery, 401 waits for renewed authentication, and only a validated server receipt marks it acknowledged. A local fallback neither acknowledges nor deletes the server request. Explicit sync retry and sign-in resume blocked/auth requests. The existing Worker `(owner_id, run_id)` and `(owner_id, operation_id)` constraints remain the server-side idempotency authority, so no Worker change was required.

Verification passed for debug compilation/assembly, lint and all 125 JVM tests. Thirteen bounded instrumentation tests passed on the explicitly selected `Pixel_7` emulator with zero failures or skips; they cover complete version-9 fixture preservation, schema validation, cascade cleanup, account isolation, repository recreation, fallback independence, acknowledgement convergence, stable operation reuse and 503/429/timeout/offline recovery. No physical phone, personal run data, production resource, Worker, remote database, paid OpenAI request, deployment, commit or push was used. This prerequisite does not start or complete Milestone 4.

## 1.10 Milestone 4 — implement environment-safe Worker configuration

### 1.10.1 Work

- Preserve the existing development config and deployment guard.
- Add a separate explicit production configuration and production deployment guard. Do not accept arbitrary caller-provided database IDs, routes, or environments.
- Replace hard-coded development origins with immutable environment/build bindings used consistently by browser origin checks, public links, export validation, geocoder identification, smoke scripts, and tests.
- Keep same-origin browser protections strict; do not introduce wildcard CORS or request-selected origins.
- Use separate production rate-limit namespaces.
- Add production dry-run and target-verification scripts. A production deploy command must fail closed if name, account, D1 ID, binding, environment, route, or required variables differ from the reviewed map.
- Add a bounded maintenance/write-freeze mechanism or an equivalent proven cutover procedure. It must block server mutations safely while allowing health/read-only checks and locally durable mobile retry behavior.
- Test both environments and negative cross-binding cases.

### 1.10.2 Verification

Run Worker TypeScript compilation, full local tests, deployment dry-runs for both exact configs, and guard tests proving that dev cannot deploy to prod and prod cannot deploy to dev. Do not deploy remotely in this milestone.

### 1.10.3 Completion evidence — October 6, 2026

The Worker now resolves only immutable `development` and `production` runtime pairings, centralizes canonical origins and provider endpoints, and rejects missing, placeholder, mismatched or cross-environment values. A centralized pre-handler write policy blocks native and browser mutations—including mutating GET routes—when `WRITE_MODE=frozen`, while health, readiness and proven read-only routes remain available. Frozen responses are stable 503 JSON with `Retry-After: 60` and `Cache-Control: no-store`; Miniflare verification proves blocked requests leave seeded D1 state unchanged.

`wrangler.production.jsonc` records the approved Worker, Custom Domain, D1 name/binding, provider URLs and four rate-limit bindings while deliberately retaining unresolved production D1, namespace and OAuth placeholders. Separate production deploy and smoke entry points are hard-bound to that config/origin. The production guard rejects placeholders, development overlap, alternate accounts/workers/routes/bindings and caller overrides; the development guard retains its exact existing resource identity and now rejects production values explicitly.

TypeScript compilation, development and synthetic-production Wrangler dry-runs, the production-config verifier, all 165 Worker/browser tests, and the nine focused environment/deployment-guard/write-freeze tests passed with zero failures or skips. No production resource, remote D1 operation, DNS action, Custom Domain creation, deployment, Android change, data migration, commit or push occurred. Milestone 4 is complete; Milestone 5 is not started.

## 1.11 Milestone 5 — promote the functional Android app into release

### 1.11.1 Work

- Replace the release shell by packaging the same functional product behavior in release without duplicating business logic.
- Keep one Android app module and preserve Android-independent domain logic.
- Move functionality from debug-only source placement into appropriate shared/main code or variant-safe source sets. Keep test/debug-only harnesses out of release.
- Bind debug to development and release to production at build time. No runtime environment switch is permitted in release.
- Centralize the API origin and public-link validation so all account, sync, coaching, photo, publication, and sharing paths use the variant-bound origin.
- Configure production OAuth client identifiers by build variant without committing secrets. OAuth client IDs are identifiers, not secrets, but must still be environment-correct.
- Set `versionName` to `1.0.0` and select a monotonic `versionCode` consistent with distribution history.
- Remove development-only labels/diagnostics from release and replace the debug-only foreground-service explanation with release-appropriate policy text.
- Ensure release is non-debuggable, minification/shrinking decisions are explicit, and backup/data-extraction rules remain intentional.
- Do not change UI, tracking algorithms, database schemas, or product behavior.

### 1.11.2 Verification

Add variant/configuration tests and run unit, lint, debug assembly, unsigned release compilation, and release manifest/config inspection. Prove by artifact inspection that release contains no development hostname, development OAuth client, debug application ID, test harness, or development-only control.

### 1.11.3 Completion evidence — October 6, 2026

The functional Android product, manifest components, fallback audio, photo paths and Room implementation now live in the shared `main` source set; the obsolete release shell is removed. Kotlin namespace remains `com.example.runningapp`. Debug retains exact application ID `com.example.runningapp.debug`, label `WAYiRUN Dev`, version `1.0.0-dev`, development origin and existing development OAuth identifiers. Release uses application ID `com.unopenedparachute.wayirun`, label `WAYiRUN`, version code `1`, version `1.0.0`, production origin `https://wayirun.slopcopy.com`, and explicit Milestone 7 OAuth placeholders. Authentication detects those placeholders locally and makes no Google or Worker request. A separate production-readiness task rejects the placeholders and is deliberately not an `assembleRelease` dependency.

Both debug and release JVM suites passed 126 tests with zero failures, errors or skips. Debug and unsigned release assembly passed. Debug and release lint each completed with zero errors and the same 21 existing warnings. Twenty-three focused synthetic instrumentation tests passed on explicitly selected `Pixel_7` emulator `emulator-5554`, covering Room 9→10 preservation, schema validation, stored runs and dependent state, and durable coaching request recovery/idempotency. Room remains version 10, database name `wayirun-local.db`, migrations and operation semantics are unchanged, schemas 1–10 remain intact, and no schema diff was produced.

Deterministic APK inspection proved release package/version/debuggability, production FileProvider authority and functional components; all product packages and the production origin are present, while the development hostname, both development OAuth IDs, debug package/label and test harness are absent. The unsigned structural release retains only the two expected production OAuth placeholders and is `STRUCTURALLY VALID — NOT PRODUCTION READY` until Milestone 7. Debug artifact inspection reconfirmed its exact package, development configuration and functional database/tracking classes. No physical phone, personal data, signing key, OAuth client, production service, deployment, data migration, commit or push was used. Milestone 5 is complete; Milestone 6 is not started.

## 1.12 Milestone 6 — establish owner-controlled production signing

### 1.12.1 Work

- Inspect for an existing permanent key without exposing it.
- If none exists, create one permanent owner-controlled release key only after its two encrypted custody locations and password-handling method are established. Do not create it inside the repository or silently substitute a debug key.
- Keep keystore and passwords outside Git. Configure Gradle through ignored local properties or environment-backed CI secrets.
- Record certificate SHA-256 and SHA-1 fingerprints, alias, creation/expiry metadata, custody, and recovery locations without recording passwords or private material.
- Produce and verify a signed direct-distribution release artifact before using the identity in OAuth. It remains non-production-ready until Milestone 7 replaces OAuth placeholders.

Milestone 6 does not enroll in Play App Signing, create or modify a Play Console application, upload an APK/AAB, create a listing, complete store declarations, enter a testing track, or pursue Play verification. A future Play milestone may evaluate this owner-controlled key as an upload key while separately recording any Play app-signing certificate required for OAuth.

### 1.12.2 Exit

The signing identity is backed up and recoverable. Losing one workstation cannot make future updates impossible.

### 1.12.3 Completion evidence — October 7, 2026

The permanent owner-controlled key uses alias `wayirun-release`, a 4096-bit RSA key and SHA256withRSA certificate. The password-protected keystore, AES-256-GCM/PBKDF2 encrypted backup and local signing properties live only in ignored `private-signing/`; the owner's Google Drive mirror of the repository folder provides the second copy. The backup was decrypted in memory and verified byte-for-byte during creation. The tracked recovery script documents and implements the backup format without containing credentials. Git ignores the whole directory plus common keystore and signing-property names.

Gradle loads signing only from the ignored properties. Structural `assembleRelease` remains usable without credentials, while `assembleSignedRelease` fails clearly if configuration or the keystore is absent and never falls back to debug signing. The signed APK verifies with exactly one signer and APK Signature Scheme v2. Certificate SHA-1 is `89:77:30:BD:C5:CA:5B:DA:0C:6E:B7:6F:EB:B7:77:A4:12:F3:9A:84`; SHA-256 is `C4:B6:54:9A:3B:A9:C1:7F:91:0F:49:65:DB:44:50:75:A3:F1:2B:52:A3:B3:A0:06:58:20:F6:27:15:AA:BA:5F`. These fingerprints are the Milestone 7 Android OAuth inputs for directly distributed V1 APKs.

The signed artifact is package `com.unopenedparachute.wayirun`, version code `1`, version `1.0.0`, non-debuggable, and contains the production FileProvider authority. Artifact scanning found no development hostname, development OAuth identifier or debug application ID. The explicit readiness gate rejected only the two expected production OAuth placeholders. Debug and release each passed 126 JVM tests; both assemblies and both lint tasks passed with zero lint errors and 21 existing warnings. No physical device, Play Console, Play App Signing, AAB upload, OAuth client, production service, deployment, data migration, commit or push was used. Milestone 6 is complete; Milestone 7 is not started. The artifact is `SIGNED — STRUCTURALLY VALID — NOT PRODUCTION READY`.

## 1.13 Milestone 7 — configure production Google OAuth

### 1.13.1 Work

- Use the approved production package ID and signing certificate fingerprints.
- Inspect the actual native and browser token flow before specifying clients, origins, or redirects.
- Create/update the exact Android and Web OAuth clients in the owner-controlled Google project. Keep development clients intact.
- Confirm how Google `sub` values and the existing account ownership model behave across the chosen Web client/project. Do not assume a changed OAuth audience maps existing users identically; prove it before migration acceptance.
- Configure the production Worker audience and Android production client identifiers.
- Verify consent-screen, branding, authorized JavaScript origins, test/production publication state, and required policy URLs.

### 1.13.2 Exit

Real production sign-in can be tested with an authorized release-test account, and the ownership mapping for migrated accounts is proven. If stable identity cannot be preserved, stop and design an explicit account-link migration rather than orphaning history.

## 1.14 Milestone 8 — create production Cloudflare resources and schema

### 1.14.1 Work

- Create the reviewed production D1 database, Worker identity, route/domain, rate-limit namespaces, non-secret variables, and required secret names.
- Configure secrets through Cloudflare secret mechanisms. Never copy or display remote secret values.
- Resolve `COACHING_KEYRING` continuity according to Section 1.5 before importing encrypted envelopes.
- Apply the complete repository migration chain to the new D1 database. Never reconstruct schema manually or import development schema definitions over it.
- Verify migrations, tables, indexes, constraints, bindings, readiness behavior, and an otherwise empty application dataset.
- Do not deploy live production traffic or import user data yet.

### 1.14.2 Exit

Production has a verified empty schema and exact bindings, while development remains unchanged.

## 1.15 Milestone 9 — build and rehearse the migration

### 1.15.1 Classification

Inspect every live D1 table and classify each row class, not merely each table, because some tables mix durable and operational state:

- `MIGRATE`: accounts, completed immutable run manifests/chunks, durable deletion tombstones, usable encrypted key envelopes under the approved key strategy, completed coaching results/audio, retained photos, retained location/weather snapshots, effective publication state/tokens, and other durable data discovered live.
- `REGENERATE`: service/migration metadata created by migrations and safely derived caches when deliberately excluded.
- `DO NOT MIGRATE`: expired login challenges, expired/revoked auth sessions, provider throttle gates, disposable canaries, and abandoned staging rows after explicit review.
- `REVIEW`: incomplete uploads, in-progress coaching jobs, publication operations, pending weather/location attempts, and any row whose finality is ambiguous.

This list is a hypothesis from the current schema. The live data and code decide the final manifest.

### 1.15.2 Work

- Create a full remote development D1 export outside the repository. Record timestamp, database name/ID, Wrangler version, size, and SHA-256 without exposing contents.
- Build a repeatable, reviewed data-only transformation/import tool. It must preserve IDs, foreign relationships, hashes, stable public tokens, owner isolation, and deletion meaning while excluding schema DDL and explicitly excluded operational rows.
- Never place real exports, transformed SQL, photos, audio, tokens, or reports containing personal data in Git.
- Rehearse against a disposable production-shaped D1 database, not the real production database.
- Verify per-table counts, canonical ordered hashes where safe, foreign-key/orphan checks, uniqueness, archive manifest/chunk hashes, media byte counts/hashes, owner/run relationships, public/private state, and several synthetic or user-approved end-to-end records.
- Test the keyring decision: migrated saved OpenAI-key status must remain usable without revealing or invoking the key, or be deliberately marked for user re-entry.
- Destroy only the explicitly disposable rehearsal database after evidence is retained safely.

### 1.15.3 Exit

The same tool can reproduce the migration from a source export into a fresh migrated schema, and reconciliation passes before real production data is touched.

## 1.16 Milestone 10 — final backup and controlled migration

### 1.16.1 Entry

Milestones 4 through 9 are complete. Production has not accepted user writes. A maintenance window is approved.

### 1.16.2 Work

1. Announce/enter the approved development mutation freeze or equivalent proven quiescence boundary.
2. Verify no unresolved upload, deletion, coaching, photo, publication, weather, or migration operation will be silently dropped; resolve or explicitly classify it.
3. Record the exact freeze timestamp and source deployment/database IDs.
4. Create a fresh final D1 export outside Git and record size/SHA-256.
5. Preserve the earlier rehearsal export separately.
6. Apply the rehearsed data-only import to the empty migrated production schema.
7. Run full automated reconciliation: table/row-class counts, hashes, orphan checks, uniqueness, archive/media integrity, per-owner run counts, deletion/publication state, and selected end-to-end record graphs.
8. Keep the source development database and immutable exports unchanged. Do not reset or repurpose it.

### 1.16.3 Failure rule

Before production writes begin, a failed migration may be discarded and repeated from the final source export after diagnosis. Do not patch production data ad hoc merely to make counts match.

## 1.17 Milestone 11 — deploy and smoke-test production Worker/browser

### 1.17.1 Work

- Run the full Worker test gate, production dry-run, and exact target guard.
- Deploy only the production Worker/configuration and record deployment/version ID.
- Verify bindings, variables, secret presence, route, TLS, security headers, readiness, and browser asset origin behavior.
- Use a dedicated authorized release-test account and isolated synthetic records for mutations. Use migrated real data read-only unless the owner explicitly approves otherwise.
- Smoke-test authentication, migrated account lookup, history/archive retrieval, sync, weather eligibility, coaching status without an unapproved paid call, photos, publication/private behavior, public page, export, and authorization isolation.
- Test deletion only on isolated synthetic production data.
- Confirm the development deployment and database remain intact.

### 1.17.2 Exit

Production API/browser behavior passes with the exact production bindings; migration reconciliation remains unchanged except for documented synthetic test rows, which are removed and verified absent.

## 1.18 Milestone 12 — build signed WAYiRUN 1.0 artifacts

### 1.18.1 Work

- Run the repository's complete Android unit/build/lint gate and authorized release instrumentation coverage.
- Build the signed production APK and, when required by the approved distribution channel, AAB.
- Verify signature/certificate, non-debuggable status, version name/code, package ID, manifest permissions/components, native libraries, and release source composition.
- Search/decompile sufficiently to prove the artifact contains the production origin and expected production client IDs, and contains no development hostname, development database/resource identifiers, secret material, or debug-only controls.
- Record filenames, absolute paths, sizes, SHA-256, signing fingerprint, build tools, source commit, variant, and production deployment ID.

### 1.18.2 Exit

Artifacts are reproducibly tied to an accepted source commit and exact production backend, with secrets absent.

## 1.19 Milestone 13 — physical and end-to-end production acceptance

### 1.19.1 Entry

Physical-device installation is separately authorized. Record phone model, Android version, prior installed identity/version, upgrade-versus-clean-install path, permissions, and battery settings.

### 1.19.2 Acceptance matrix

Test and record, without tuning during acceptance:

1. Production Google sign-in and account switching/sign-out.
2. Migrated history, details, archives, splits, route noodles, photos, coaching replay, achievements, weather, publication state, export, and privacy.
3. Outdoor GPS distance/pace/route against an independent reference.
4. Indoor steps/stride distance and the open auto-pause regression cases from `OPEN_WORK.md` and `TEST_PLAN.md 1.9.2`.
5. GPS loss/reacquisition and step fallback without jumps/double counting.
6. Long screen-off/background tracking, recovery, notification controls, and battery behavior.
7. Audio cues and real YouTube Music ducking/restoration without transport control.
8. Goal/milestone arbitration and continuation after goal.
9. Finish, achievements, selected/unchecked coaching, camera/picker/rotation/retake/save/share, weather overlay, and route overlay.
10. Cloud sync/retry across connectivity loss and restart.
11. Health Connect consent, exported records, retries, account scope, and safe synthetic deletion cleanup.
12. Explicit publication, private default, public page, photo visibility, and unshare.
13. Phone/web reconciliation and deletion only with a safe synthetic run or separately backed-up user-approved record.
14. Upgrade path where applicable, plus clean-install sign-in/restore boundaries. Automatic photo restoration is not expected.

Paid OpenAI testing requires explicit approval and the user's own key. Do not use real-user deletion as a release test.

### 1.19.3 Exit

Every required case is PASS, deliberately NOT APPLICABLE with justification, or a separately tracked release blocker. No blocker remains for V1 acceptance.

## 1.20 Milestone 14 — cut over operations and document recovery

### 1.20.1 Work

- Declare the production D1/origin canonical only after Milestone 13 passes.
- Publish/distribute through the approved channel and record the released artifact/version.
- Decide when to lift the development mutation freeze. Once lifted, development data is explicitly non-production and may diverge.
- Retire real-user use of old debug builds; document how queued operations on an old build are handled so they cannot be mistaken for production writes.
- Preserve the final development export under restricted durable storage with retention and recovery ownership.
- Create/update operator documentation for environment mapping, guarded deployment, forward D1 migrations, backups, restore rehearsal, incident response, secret rotation, signing recovery, OAuth rotation, and production access control.
- Establish the production backup schedule and perform a non-production restore rehearsal from a production-format backup.
- Update `REQUIREMENTS.md`, `CURRENT_STATE.md`, `ARCHITECTURE.md`, `DATA_MODEL.md`, `OPEN_WORK.md`, `TEST_PLAN.md`, and `README.md` to reflect actual production state without erasing history.

### 1.20.2 Exit

Production ownership, backup/restore, deployment, and future migration procedures are executable by an authorized maintainer. Normal development cannot reset or deploy over production.

## 1.21 Milestone 15 — finalize version control and V1 marker

### 1.21.1 Work

- Review the complete diff and generated-artifact exclusions.
- Re-run final Android and Worker gates from the exact candidate commit/tree.
- Confirm production resource identifiers that are safe to commit are accurate and no secrets/personal exports are present.
- Present the exact commit, push, and tag operations to the user.
- Only after explicit delegation: commit, push the authoritative branch, create annotated tag `v1.0.0` on the accepted production commit, and push the tag.
- Verify the remote branch/tag and final clean Git status.

### 1.21.2 Final report

Record:

- accepted commit and `v1.0.0` tag;
- production Worker name, deployment ID, URL/domain, D1 name/ID, and binding;
- preserved development Worker/D1;
- final source export timestamp/size/SHA-256 and secure storage owner;
- migration manifest and reconciliation results;
- production OAuth project/client identities and proven owner mapping;
- Android package ID, version code/name, signing fingerprint, APK/AAB paths/sizes/SHA-256;
- automated, emulator, physical, and end-to-end acceptance results separately;
- production privacy/distribution state;
- backup/restore rehearsal and operator-runbook locations;
- final Git status and any post-V1 follow-up explicitly excluded from release.

## 1.22 Stop conditions

Stop the current milestone and give the user one exact next action when:

- a product-owner decision in Section 1.5 is unresolved;
- permanent signing material must be created, supplied, or backed up;
- Google, Cloudflare, domain/DNS, Play Console, policy, billing, or account-owner action is required;
- a secret must be configured;
- remote state materially contradicts the reviewed map;
- an operation could overwrite, delete, expose, or irreversibly transform user/production data;
- OAuth identity continuity, encrypted-key continuity, migration reconciliation, or a verification gate fails;
- source data changes after the recorded migration freeze;
- a production write occurs before acceptance establishes the rollback boundary.

When stopped, provide exact commands or exact console values derived from the accepted decisions. Never ask the user to paste a secret into chat.
