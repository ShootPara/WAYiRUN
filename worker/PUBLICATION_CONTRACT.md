# 1 Run publication

## 1.1 Status and migration

Milestone 4.0 is implemented and locally verified. TypeScript compilation, deployment dry-run, all 125 Worker tests and desktop/mobile browser checks passed; no remote migration or deployment occurred. Migration 0009 follows run locations (0008). It creates independent public_runs and publication_operations tables, both tied to the original owner/run with deletion cascades. Previously public photos backfill enabled publication records with the exact old 64-hex token. Private photos do not publish. The first authenticated publication read/mutation initializes a private record for an otherwise unpublished synced run and mints its 32-hex short token using 16 random Web Crypto bytes, database uniqueness and bounded collision retry. Long tokens use 32 random bytes. Tokens remain stable through hide, unshare and reshare.

## 1.2 Owner API

GET /api/publications/{runId} returns `{publication:{shared,photoVisible,revision,publicUrl}}`. The run must be completed, synced, undeleted and owned by the authenticated account. Initial state is shared=false, photoVisible=true, revision=0, publicUrl=null. Photo visibility is a preference and never implicitly publishes. Enabled publication returns the short URL `/r/{32-hex-token}` on the fixed development host.

PUT on the same path accepts exactly `{operationId,expectedRevision,action}` for action share/unshare, or `{operationId,expectedRevision,action:"photo",photoVisible}`. Operation IDs are UUIDs; expectedRevision is a nonnegative integer; photoVisible is boolean. One action changes one property and increments the revision even if its value was already selected. The revision comparison and operation receipt are atomic. A stale revision returns 409 with current publication state. Reusing an operation ID with changed input conflicts. Repeating an applied operation returns CURRENT state without replaying it, so replay of an earlier share cannot undo a newer unshare. Store each successful receipt until run deletion.

Native calls require bearer authentication and reject Origin/Cookie. The /web-api/publications/{runId} bridge requires the existing same-origin session cookie; PUT additionally requires exact Origin and X-WAYIRUN-Request: 1. Both paths use existing request size limits, authentication and rate limits. No caller-supplied owner is accepted. Deletion tombstones and missing runs prevent initialization, mutation and public reads.

## 1.3 Public routes and photos

GET /r/{token} performs a current-publication check and returns a nonpermanent 302 to /p/{longToken}, with no-store. Existing /p/{token}, /data and /image access uses public_runs, never the legacy photo public_token. Every path checks publication, completed-run existence and deletion. Data rechecks after archive/location work; images recheck photo visibility before response. Images, pages, data and redirects use no-store. No thumbnail or preview-image endpoint is introduced; unknown suffixes return 404.

Public data adds photoVisible, true only when enabled and an image exists. Photo-free and hidden-photo pages retain stats/splits/settings/normalized route/city-state and omit image requests. Owner image access stays private and available regardless of photo visibility. Removing or replacing an image does not unshare. Unsharing disables both token paths, data and image. Re-sharing deliberately re-enables the same tokens. Previously downloaded copies remain outside revocation.

Photo PUT still accepts the legacy X-Photo-Public header for queue compatibility but ignores it for publication and visibility. It cannot publish, unshare or reveal a hidden image. Photo revision idempotency compares JPEG/options, not the obsolete visibility header. Metadata retains its existing publicUrl field, derived from independent publication; export accepts either historical long links or new short links on the fixed host. No archive or CSV schema changes.

## 1.4 Browser behavior and rollout boundary

Run details expose Share run link, Copy run link, Unshare and Display photo with shared run. Share/copy sends explicit share intent before chooser/clipboard. Cancelling the chooser leaves the run shared. A clipboard/chooser failure leaves the real link visible. No-photo runs can share. Conflict refreshes state for review, never automatically reapplies stale intent. An uncertain retry within the open run reuses its operation ID; navigation/account changes clear local state and ignore late responses. Web mutations require connectivity and report unconfirmed changes honestly; durable Android offline intent belongs to 4.1.

Older phone builds may still show their obsolete public-photo control or a cached URL. Their uploads no longer alter publication and they cannot create new public links. Do not deploy 4.0 alone to the user's working phone workflow. Milestone 4.1 must migrate queued visibility work, implement owner/run-scoped publication intent and revision conflict reconciliation, and update all sharing entry points before the combined rollout. A revoked stale local URL still fails on the server. No production identity or deployment is part of this milestone.

## 1.5 Android integration status

Milestone 4.1 is implemented and locally verified. Room v7 stores server observations, pending shared/photo preferences and exact mutation JSON. New intent supersedes local acknowledgements; uncertain retries retain the original operation body. Sharing/photo conflicts refresh state and require another explicit action; revocation may rebase until confirmed. Local-only intent stays local until explicit account import. Migration clears obsolete photo-public fields and cached photo links without converting old Keep Photo into share intent; existing server publication is fetched independently. The Android gate passed 95 JVM tests, 49 focused emulator cases and light/dark/200-percent visual checks. No deployment or physical-phone acceptance occurred.
