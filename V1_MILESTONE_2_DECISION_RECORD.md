# 1 WAYiRUN V1 Milestone 2 decision record

Version: 1.0
Status: Approved owner decisions and production policy
Decision date: October 6, 2026

## 1.1 Authority and boundary

This record resolves WAYiRUN V1 productionization Milestone 2. the owner's explicit choices control the permanent application ID, production domain, distribution model, and public identity. The remaining entries convert the accepted recommendations into technical policy.

This milestone creates no Android identity, signing key, OAuth client, domain route, Cloudflare resource, database, secret, deployment, migration, or release artifact. Milestone 3 must inventory provider state and turn these decisions into an exact environment map before implementation begins.

## 1.2 Owner decisions

| Decision | Approved value |
| --- | --- |
| Permanent Android application ID | `com.unopenedparachute.wayirun` |
| Canonical production origin | `https://wayirun.slopcopy.com` |
| Canonical distribution | Google Play using Play App Signing |
| Direct artifacts | Signed APKs may be retained for owner testing and archive; they use the same package and signing identity and are not a separate production identity |
| Public developer name | `UnopenedParachute` |
| Public support/privacy email | `unopenedparachute@gmail.com` |
| Public privacy-policy URL | `https://wayirun.slopcopy.com/privacy` |
| Public support URL | `https://wayirun.slopcopy.com/support` |
| Public account-deletion URL | `https://wayirun.slopcopy.com/delete-account` |

The domain and public URLs are approved intended values, not claims that DNS, TLS, or pages already exist. Milestone 3 must verify control of the `slopcopy.com` Cloudflare zone read-only. Later authorized milestones create and verify the route and pages.

## 1.3 Identity, signing, and provider ownership

- Google Play is the canonical distribution channel. Play App Signing will hold the app-signing key; an owner-controlled upload key will be used for uploads.
- `UnopenedParachute` is the signing and publishing owner. Permanent signing material must never be silently created by an agent or stored in Git.
- Before key creation, the owner must designate two independently recoverable encrypted storage locations for the upload key and recovery material. Passwords belong in the owner's password manager or equivalent encrypted secret store, never in repository files, documentation, chat, build logs, or ordinary cloud-drive files.
- Development and production OAuth clients will remain separate but use the same owner-controlled Google Cloud project, subject to Milestone 3 read-only ownership/recovery verification.
- Development and production will use the same owner-controlled Cloudflare account but entirely separate Workers, D1 databases, rate-limit namespaces, non-secret configuration, routes, and secret instances.
- Production OAuth must use `com.unopenedparachute.wayirun`, the production signing certificate fingerprints, and `https://wayirun.slopcopy.com`. The provisional package must not be registered as production.

## 1.4 Privacy, Play, and Health Connect policy

- The privacy policy must be publicly accessible, non-geofenced, non-PDF, linked from the Play listing, and linked or presented within the app before release.
- The Play Data safety answers must match actual collection, transmission, retention, deletion, encryption, and sharing behavior.
- WAYiRUN must retain its in-app account deletion path and provide the external deletion-request page at the approved URL. Deletion must cover associated user data, subject only to clearly disclosed legitimate retention.
- The Play Health apps declaration will classify WAYiRUN as activity and fitness. Only the Health Connect exercise-session and distance permissions required by accepted functionality may be declared or requested.
- WAYiRUN is a fitness tracker, not a medical device, and must not claim to diagnose, treat, cure, or prevent a medical condition.
- Public disclosures must cover Google identity, precise route/location data, photos, user-supplied OpenAI keys and coaching transfer, Health Connect writes, publication, exports, deletion, and operational processing.

Policy references, verified October 6, 2026:

- Google Play account deletion: <https://support.google.com/googleplay/android-developer/answer/13327111>
- Google Play health-app policy: <https://support.google.com/googleplay/android-developer/answer/18258653>
- Google Play Health apps declaration: <https://support.google.com/googleplay/android-developer/answer/14738291>
- Cloudflare Workers Custom Domains: <https://developers.cloudflare.com/workers/configuration/routing/custom-domains/>

## 1.5 Data retention and deletion policy

| Data class | Policy |
| --- | --- |
| Runs, measurements, routes, splits, photos, weather, achievements, publication state, and completed coaching results | Retain until the owner deletes the run or account, except for bounded backup retention disclosed below |
| Encrypted OpenAI key envelope | Retain until replaced, explicitly removed, or the account is deleted |
| Deletion tombstones | Retain only for the bounded period necessary to prevent resurrection and reconcile all clients/backups; choose the exact duration from measured retry/offline behavior before production launch |
| Authentication sessions and login challenges | Short-lived operational data with automatic expiry; do not migrate expired or revoked records |
| Incomplete uploads and in-progress operations | Retain for a bounded recovery window, then purge only after finality and non-resurrection rules are proven |
| Weather/location caches | Retain with the associated run or until unreferenced; do not retain a detached personal-location history |
| Operational logs | Short retention, access-restricted and redacted; never record coordinates, photos, run archives/payloads, OpenAI keys or envelopes, tokens, authorization headers, request bodies, or sensitive identifiers |
| Backups | Encrypted, access-restricted rolling backups with documented expiry; preserve the final migration source backup separately until production reconciliation and restore rehearsal are accepted |

Exact time durations are operational parameters to be proposed from verified provider capabilities and retry behavior in Milestones 3 and 14. They may not be guessed during implementation, and production launch is blocked until they are recorded in the public policy and operator runbook.

## 1.6 Telemetry and incident policy

Production uses minimal operational telemetry: availability, bounded error categories, deployment identity, aggregate latency and rate-limit behavior. Logs must not contain precise or coarse location, run content, photos, coaching content/audio, OpenAI key material, Google tokens, archives, request/response bodies, or personal database exports. Diagnostic access is owner-controlled and time-bounded. Sensitive failures use opaque correlation identifiers rather than payload logging.

## 1.7 Cutover and live-data protection

Existing development data is live user data and must be treated as irreplaceable source data until production is fully reconciled. No milestone may reset, repurpose, overwrite, prune, or make destructive test mutations against the development D1 database or the phone's local Room database.

The approved cutover model is:

1. Rehearse the exact data-only migration against a disposable production-shaped database.
2. Verify the complete row-class manifest, relationships, owner isolation, counts, hashes, archive chunks, media bytes, deletion/publication state, and encrypted-key continuity.
3. At an owner-approved maintenance window, freeze development mutations and resolve or classify every queued upload, deletion, coaching, photo, publication, weather, and sync operation.
4. Create an immutable final development export outside Git and record its source identifiers, timestamp, size, and SHA-256 without exposing its contents.
5. Import with the rehearsed tool into an empty, migration-created production schema.
6. Reconcile per-table and per-owner counts, stable IDs, ordered hashes where safe, foreign keys/orphans, uniqueness, archive/media integrity, and selected complete record graphs.
7. Keep the source database, the final export, and the phone's local records unchanged until production and physical acceptance pass.
8. Before production accepts writes, a failed import may be discarded and repeated from the immutable source. After production accepts writes, preserve both states and use forward reconciliation; never roll back blindly.

No migration is successful merely because an export/import command completes. Any mismatch, source mutation after freeze, uncertain owner mapping, missing media, invalid archive, or unusable encrypted key is a stop condition.

## 1.8 OpenAI key continuity

Production will preserve saved keys by securely provisioning compatible `COACHING_KEYRING` material into the separate production secret store. Secret values must not be displayed, copied through chat, written to disk in the repository, or included in migration exports. Milestone 9 must prove that migrated envelopes remain decryptable through a non-revealing status check without making an unapproved paid request. If compatibility cannot be proven, stop and obtain an explicit owner decision between controlled re-encryption and user re-entry; never silently discard or expose keys.

## 1.9 Compliance and implementation gates

Before production identity or resource creation, Milestone 3 must verify owner/recovery access to the Google Cloud project, Play Console, Cloudflare account, and `slopcopy.com` zone; inventory actual development resources; and produce the exact separate production resource map and cost/limit assumptions.

Before publication, the approved policy/support/deletion pages must be live, the app must link to the privacy policy and deletion path, Play declarations must match the final artifact, signing custody must be recoverable, OAuth account identity continuity must be proven, and the migration/restore evidence must pass. These are gates, not permission to begin Milestone 3 in this milestone.

## 1.10 Milestone 2 conclusion

All product-owner decisions required to design the production environment are fixed. Operational durations and concrete resource identifiers remain deliberately deferred to their named planning and implementation milestones. Milestone 2 is complete; Milestone 3 has not started.
