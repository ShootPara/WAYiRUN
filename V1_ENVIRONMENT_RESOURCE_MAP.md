# 1 WAYiRUN V1 environment and resource map

Version: 1.0
Status: Approved Milestone 3 environment and resource design
Decision date: October 6, 2026

## 1.1 Authority and scope

This document is the authoritative development/production boundary for WAYiRUN V1. It implements the owner decisions in `V1_MILESTONE_2_DECISION_RECORD.md` without creating or changing an Android identity, signing key, OAuth client, Worker, D1 database, rate-limit namespace, DNS record, Custom Domain, secret, deployment, or user-data migration.

Names, paths, bindings, origins and deterministic configuration values below are exact. Values assigned later by a provider use milestone-specific placeholders and MUST NOT be fabricated. `REQUIREMENTS.md` remains the product-behavior authority, and `WAYIRUN_V1_PRODUCTIONIZATION_PLAN.md` controls execution order.

## 1.2 Verified development inventory

### 1.2.1 Android

| Concern | Verified development value |
| --- | --- |
| Module | Single application module `android/app` |
| Namespace | `com.example.runningapp` |
| Base application ID | `com.example.runningapp` |
| Effective debug application ID | `com.example.runningapp.debug` |
| Version | `versionCode 1`; `versionName 0.1.0-dev-photo-sync1` |
| API/public origin | `https://wayirun-dev.unopenedparachute.workers.dev` |
| OAuth build value | Existing development Google Web client ID in debug `BuildConfig` |
| Source sets | `main`, `debug`, `release`, `test`, `testDebug`, `androidTest` |
| Variant boundary | Functional product in `debug`; `release` is a name-only shell |
| Local database | Room `wayirun-local.db`, schema version 9 |

Development API origins are currently hard-coded in the debug account, sync, coaching and photo adapters. Milestone 5 must replace them with compile-time variant configuration without making the environment runtime-selectable.

### 1.2.2 Worker and routing

Read-only repository and Cloudflare verification established:

| Concern | Verified development value |
| --- | --- |
| Cloudflare account ID | `6bf560a8b86852196c9898023e3b8d6b` |
| Worker | `wayirun-dev` |
| Configuration | `worker/wrangler.jsonc` |
| Main module | `worker/src/index.ts` |
| Compatibility date | `2026-02-17` |
| Environment | `APP_ENV=development` |
| Exposure | `workers_dev=true`; no configured custom route |
| Origin | `https://wayirun-dev.unopenedparachute.workers.dev` |
| Deployment path | `npm run deploy:dev` → `worker/scripts/deploy-dev.mjs` |
| Guard | `worker/scripts/deploy-guard.mjs` |
| Current live version | `5b3b8fc8-f10a-47b0-8cd7-ae0a38c03dd7`, 100% traffic when verified |

The development deploy script accepts no target arguments, performs a dry run, applies remote D1 migrations and deploys only after its exact-name/account/database guard passes. This path and its resources MUST remain intact.

### 1.2.3 D1 and bindings

| Concern | Verified development value |
| --- | --- |
| Database | `wayirun-dev-db` |
| Database ID | `04bf8339-386b-4a03-80d7-12b4d1f99ffb` |
| Binding | `DB` |
| Migrations directory | `worker/migrations` |
| Migration chain | Immutable `0001` through `0011` |

| Rate-limit binding | Development namespace ID | Limit |
| --- | --- | --- |
| `AUTH_RATE_LIMIT` | `786531901` | 30 per 60 seconds |
| `AUTH_TOTAL_LIMIT` | `786531902` | 300 per 60 seconds |
| `RUN_RATE_LIMIT` | `786531903` | 300 per 60 seconds |
| `RUN_TOTAL_LIMIT` | `786531904` | 3,000 per 60 seconds |

### 1.2.4 OAuth

Development uses the existing dedicated Web and Android clients in the owner-controlled Google Cloud project `wayirun-development`. Their checked-in identifiers remain unchanged. The Worker requires the exact Web audience and accepts only the configured Web or Android authorized party. Development browser JavaScript origin remains the development Worker origin.

### 1.2.5 Secrets and storage

The only verified Worker secret name is `COACHING_KEYRING`. Its value was not read or displayed. Current cloud durable storage is D1 only: there is no WAYiRUN R2, KV, Durable Object, Queue or other durable binding.

D1 contains accounts; authentication challenges/sessions; completed and staged run manifests/chunks; deletion tombstones; encrypted OpenAI-key envelopes; coaching jobs/audio; photos; location and weather caches/gates; and publication state/operations. Android Room holds runs, recovery checkpoints, route points, measurements, splits, intervals, source segments, sync queues, achievements, photos, publication state and Health Connect work.

## 1.3 Exact production resource map

| Concern | Development | Production |
| --- | --- | --- |
| Android application ID | `com.example.runningapp.debug` | `com.unopenedparachute.wayirun` |
| Kotlin namespace | `com.example.runningapp` | Retain unless implementation proves a refactor necessary |
| Android API/public origin | `https://wayirun-dev.unopenedparachute.workers.dev` | `https://wayirun.slopcopy.com` |
| Google Android OAuth | Existing development client | `<TO_BE_CREATED_IN_MILESTONE_7>` |
| Google Web OAuth | Existing development client | `<TO_BE_CREATED_IN_MILESTONE_7>` |
| Worker | `wayirun-dev` | `wayirun-prod` |
| Worker config | `worker/wrangler.jsonc` | `worker/wrangler.production.jsonc` |
| Cloudflare account | `6bf560a8b86852196c9898023e3b8d6b` | Same owner-controlled account |
| Environment | `APP_ENV=development` | `APP_ENV=production` |
| Exposure | Development `workers.dev` hostname | Custom Domain `wayirun.slopcopy.com`; `workers_dev=false` |
| D1 database | `wayirun-dev-db` | `wayirun-prod-db` |
| D1 database ID | `04bf8339-386b-4a03-80d7-12b4d1f99ffb` | `<TO_BE_CREATED_IN_MILESTONE_8>` |
| D1 binding | `DB` | `DB` |
| Rate-limit bindings | Existing four development namespaces | Same four binding names with separate production namespace IDs |
| Secret | Development `COACHING_KEYRING` | Separate production secret named `COACHING_KEYRING` |
| Deployment command | `npm run deploy:dev` | `npm run deploy:prod` |
| Backups | Development source plus protected exports | Encrypted 30-day rolling backups plus separately retained cutover backup |

Production Custom Domain configuration must use exactly:

```json
"routes": [
  {
    "pattern": "wayirun.slopcopy.com",
    "custom_domain": true
  }
]
```

and `workers_dev=false`. This is a Worker Custom Domain, not an ordinary route. Cloudflare will create the required DNS record and certificate only in the later authorized resource-creation milestone; no DNS record is manually pre-created.

Read-only verification confirmed `slopcopy.com` is an active, unpaused full zone in Cloudflare account `6bf560a8b86852196c9898023e3b8d6b`. The absent `wayirun.slopcopy.com` DNS record is expected before the Custom Domain exists.

## 1.4 Repository configuration layout

The smallest safe Worker layout is:

```text
worker/
  wrangler.jsonc
  wrangler.production.jsonc
  scripts/
    deploy-dev.mjs
    deploy-guard.mjs
    deploy-prod.mjs
    deploy-prod-guard.mjs
    smoke-dev.mjs
    smoke-prod.mjs
```

Checked-in configuration may contain resource names/IDs, origins, OAuth client identifiers, rate limits, non-secret provider endpoints and deployment guards. Provider-generated placeholders remain explicit until their named milestone replaces them.

Secret values, signing material/passwords, Cloudflare/Google credentials, personal exports, encryption keys and private backup locations MUST remain outside Git. `COACHING_KEYRING` is configured through Cloudflare secrets. Android signing configuration uses ignored local properties or environment-backed CI secrets. Android origins and OAuth identifiers are compile-time variant values and MUST NOT be user-selectable or runtime-switchable.

## 1.5 Deployment guards and commands

The existing development guard remains unchanged and must continue rejecting production names, IDs, routes and environments.

`worker/scripts/deploy-prod.mjs` will accept no additional arguments and will use only `worker/wrangler.production.jsonc`. Before any dry run, migration or deployment, `worker/scripts/deploy-prod-guard.mjs` must require:

- account ID `6bf560a8b86852196c9898023e3b8d6b`;
- Worker name `wayirun-prod`;
- `APP_ENV=production`;
- D1 name `wayirun-prod-db`, binding `DB`, and the reviewed production database ID;
- exactly one Custom Domain entry with pattern `wayirun.slopcopy.com` and `custom_domain=true`;
- `workers_dev=false`;
- all four expected production rate-limit bindings and reviewed namespace IDs;
- the expected production OAuth identifiers and non-secret provider variables;
- no unresolved placeholder and no development resource name, ID, origin or OAuth client.

The guard must reject `env` blocks, caller-selected routes/configs/databases, extra D1 bindings, ordinary routes, development IDs and any target overlap. `npm run deploy:prod` must run tests, production dry-run/guard checks, exact production migrations and deployment, then production smoke checks. Tests must prove both cross-environment directions fail closed before remote use is authorized.

## 1.6 Android variant boundary

The installed development app must retain effective application ID `com.example.runningapp.debug` throughout productionization so it continues to access its current Android sandbox and Room data. The production release application ID is `com.unopenedparachute.wayirun`.

Changing the public application ID does not require a Kotlin namespace/package refactor. Preserve namespace `com.example.runningapp` for V1 unless compilation or a verified platform requirement proves a refactor necessary. Milestone 5 moves the functional product into shared/variant-safe source sets, keeps test harnesses out of release, binds development and production origins/OAuth at compile time, and introduces no runtime environment selector.

Because different application IDs use different Android sandboxes, the production package cannot inherit the installed debug app's Room database. Section 1.9 is therefore a release gate, not optional cleanup.

## 1.7 OAuth topology and pending generated values

Development clients remain unchanged in the same owner-controlled Google Cloud project chosen in Milestone 2.

Production Android OAuth:

- package: `com.unopenedparachute.wayirun`;
- certificate SHA-1/SHA-256: `<TO_BE_ESTABLISHED_IN_MILESTONE_6>`;
- client ID: `<TO_BE_CREATED_IN_MILESTONE_7>`.

Production Web OAuth:

- authorized JavaScript origin: `https://wayirun.slopcopy.com`;
- client ID: `<TO_BE_CREATED_IN_MILESTONE_7>`.

No client secret belongs in Android or repository configuration. Before migration acceptance, Milestone 7 must prove that the production clients preserve the existing Google account-to-`sub` ownership mapping. If identity continuity fails, stop and design explicit account linking rather than orphaning or reassigning history.

## 1.8 D1 schema and migration boundary

Production uses `wayirun-prod-db`, bound only as `DB`, with ID `<TO_BE_CREATED_IN_MILESTONE_8>`. Its schema is created in an empty database by applying repository migrations `0001` through `0011` in order. Development schema internals, DDL exports and edited historical migrations are not substitutes.

Migrate:

- accounts;
- completed run manifests and chunks;
- durable deletion tombstones;
- encrypted OpenAI-key envelopes;
- completed coaching results and audio;
- retained photos;
- retained location and weather snapshots;
- effective publication state and stable tokens.

Do not migrate routine transient state:

- expired/revoked authentication sessions;
- login challenges;
- provider throttle gates;
- recreated service/migration metadata;
- disposable canaries;
- abandoned staging rows after explicit review.

Individually classify incomplete uploads, in-progress coaching jobs, pending publication operations, pending enrichment attempts and any ambiguous row before final migration.

The production `COACHING_KEYRING` must be compatible with migrated encrypted envelopes. Provision it without displaying or exporting its value, then prove envelope usability through a non-revealing status check without an unapproved paid provider request. Failure is a stop condition.

## 1.9 Phone-local and cloud-data reconciliation

Existing phone and development data are irreplaceable source data until production and physical acceptance pass. A cloud migration alone is insufficient because the production application ID cannot read the current debug sandbox.

Before cutover, classify every phone run as account-owned and completely synchronized, local-only/pre-account, queued/partially synchronized, or deleted locally while awaiting reconciliation. Verify that every durable cloud candidate has its complete owner, manifest, chunks, measurements, route, media and dependent state. Local-only records require an explicitly verified export/import or synchronization path before the debug app is retired. Do not assume cloud completeness from run-summary counts.

Keep the debug app, local Room database, development D1 and immutable exports unchanged until per-owner counts, stable IDs, canonical hashes where safe, foreign relationships, archive chunks, media bytes, deletion/publication state, encrypted-key continuity and selected complete record graphs all reconcile. No destructive acceptance test may use a real run.

## 1.10 Backup and retention map

Two encrypted owner-controlled copies are required:

1. Primary: encrypted local/offline owner-controlled archive.
2. Secondary: encrypted cloud storage in an account/location independent of the production Cloudflare account.

Exact filesystem paths, provider URLs, credentials and encryption keys are operator-configured outside Git.

| Evidence | Retention |
| --- | --- |
| Immutable pre-cutover source backup | Through all V1 migration/release acceptance and one year after production acceptance |
| Cutover backup | Exempt from ordinary rolling deletion for the full required retention period |
| Routine production backups | Encrypted 30-day rolling retention |
| Redacted operational logs | 30 days |

Backups must record source identity, timestamp, size and SHA-256 without exposing contents. Milestone 14 must document and rehearse restore from production-format backup. Loss of either workstation or Cloudflare access must not eliminate both copies.

## 1.11 Weather and geocoding provider posture

Open-Meteo remains the V1 weather provider for the current personal, non-commercial, non-ad-supported deployment. Preserve attribution and centralize provider-specific endpoint/configuration. The present free/open-access posture must be re-reviewed before any commercial, subscription or ad-supported use, or if terms/limits change; move to an appropriate paid Open-Meteo customer endpoint or another reviewed provider when required. Milestone 3 does not redesign weather behavior.

The current public Nominatim endpoint remains the V1 coarse reverse geocoder at current scale. Preserve sparse cached lookup, application-identifying User-Agent behavior and attribution; perform no systematic/grid queries and remain below the public-service limit of one request per second. Keep the endpoint centralized so a paid or self-hosted compatible provider can replace it without redesign. Commercialization, scale or policy change requires provider review before continued use.

## 1.12 Pre-write and post-write rollback rules

Before production accepts writes:

1. Freeze development mutations and resolve/classify queued operations.
2. Preserve the phone database and development D1 unchanged.
3. Create and hash the immutable source export in both approved backup destinations.
4. Import only with the rehearsed data-only process into a migration-created empty production schema.
5. Run complete automated reconciliation.
6. If verification fails, preserve evidence, discard only the unaccepted production state, diagnose, and recreate from the immutable source. Never patch data merely to force counts to agree.

After production accepts writes:

1. Freeze both sides and preserve fresh exports of both states.
2. Do not restore development or an old snapshot over production.
3. Identify records written on each side after cutover.
4. Use a reviewed idempotent forward-reconciliation process that preserves ownership, deletions and newest valid intent.
5. Resume writes only after integrity and owner checks pass.

Any source mutation after the freeze, count/hash mismatch, orphan, missing media/chunk, ownership uncertainty, unusable key envelope or unclassified pending operation stops cutover.

## 1.13 Provider-generated placeholders

| Value | Placeholder | Resolution milestone |
| --- | --- | --- |
| Production D1 database ID | `<TO_BE_CREATED_IN_MILESTONE_8>` | 8 |
| `AUTH_RATE_LIMIT` namespace ID | `<TO_BE_CREATED_IN_MILESTONE_8>` | 8 |
| `AUTH_TOTAL_LIMIT` namespace ID | `<TO_BE_CREATED_IN_MILESTONE_8>` | 8 |
| `RUN_RATE_LIMIT` namespace ID | `<TO_BE_CREATED_IN_MILESTONE_8>` | 8 |
| `RUN_TOTAL_LIMIT` namespace ID | `<TO_BE_CREATED_IN_MILESTONE_8>` | 8 |
| Android signing fingerprints | `<TO_BE_ESTABLISHED_IN_MILESTONE_6>` | 6 |
| Production Android OAuth client ID | `<TO_BE_CREATED_IN_MILESTONE_7>` | 7 |
| Production Web OAuth client ID | `<TO_BE_CREATED_IN_MILESTONE_7>` | 7 |
| Production Worker deployment/version ID | `<TO_BE_CREATED_IN_MILESTONE_11>` | 11 |
| Custom Domain DNS record/TLS certificate | `<TO_BE_CREATED_BY_CLOUDFLARE_IN_MILESTONE_8>` | 8 |

These placeholders do not block this design milestone. Their named milestones must replace them with verified values before any dependent action.

## 1.14 Milestone 3 acceptance checklist

- [x] Existing Android identity, source sets, origin and variant boundary inventoried.
- [x] Existing Worker, D1, bindings, rate limits, secret name and deployment guard inventoried.
- [x] Development resources are preserved and never repurposed for production.
- [x] Exact production names, paths, origin, Custom Domain shape and guard behavior approved.
- [x] Android debug sandbox preservation and production-ID separation approved.
- [x] OAuth topology and milestone-specific placeholders approved.
- [x] D1 schema/data classification and keyring continuity boundary approved.
- [x] Phone-local/cloud reconciliation is an explicit release gate.
- [x] Two backup destinations and retention periods approved.
- [x] Open-Meteo and Nominatim V1 posture approved.
- [x] Pre-write and post-write rollback rules approved.
- [x] `slopcopy.com` verified active and unpaused in the intended Cloudflare account.
- [x] No resource, DNS, identity, secret, deployment, migration, Android configuration or signing material created or changed.

Milestone 3 is complete. Milestone 4 is not started.
