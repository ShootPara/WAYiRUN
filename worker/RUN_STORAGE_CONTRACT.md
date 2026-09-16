# WAYiRUN - Completed-run storage transport

## 1 Scope and boundary

This Milestone 5 slice provides authenticated, resumable storage and retrieval of immutable run archives. It does not yet connect Android's local database to the server. It does not implement Android upload scheduling, importing old runs, download reconciliation, remote discard, or desktop history UI. Those must be verified before phone synchronization is enabled. Existing phone tracking and records remain untouched.

The user approved an explicit **Add existing runs to this account** action in settings. Old local records must remain local until that action is chosen. New runs will retain the account selected at their start, including offline recording. Account changes must never transfer an existing run or its queued operation. A first-time offline user can continue local tracking and later choose import; authentication is not fabricated offline. The import action and queue are the next Android slice, not present in this build.

## 2 Authentication and privacy

All endpoints require the existing verified, unexpired bearer session. Owner identity comes exclusively from the session. Request-body owner fields are invalid. Cookie-only requests fail; Origin-bearing browser requests are refused. Existing IP/aggregate rate limits apply before D1 access. Responses are no-store; archive data is application/octet-stream with nosniff, never executable HTML. Errors do not disclose database/provider details or log run contents.

Unauthenticated requests return 401; missing configuration or unavailable storage returns 503. Unsupported methods return 405 after authentication. Foreign and absent IDs both return 404. The same run/operation IDs may exist under different owners without sharing data. Release/production remains outside this development Worker.

## 3 Manifest and archive format

POST `/api/run-uploads` accepts JSON with exactly:

- `schemaVersion`: 1 (transport version).
- `runId`, `operationId`: lowercase UUIDs, persisted by the client before upload.
- `summary`: exactly `state` (FINISHED), `startedUtcMs`, `endedUtcMs`, `activeDurationMs`, `distanceMeters`, `mode` (INDOOR/OUTDOOR), `units` (MILES/KILOMETERS).
- `chunks`: ordered `{sha256, bytes}` descriptors. SHA-256 is lowercase hex of the exact bytes.

Timestamps/duration are nonnegative safe integers; distance is finite and nonnegative. Wall-clock ordering is not used to validate active duration because the device wall clock can change during a run. No pace is independently stored.

The manifest has a 16 KiB request cap. Each chunk contains 1-131,072 bytes; a run has 1-128 chunks, at most 16 MiB. Oversized data is refused, never silently truncated. The Android exporter must retain the complete local record if these limits are exceeded. Chunk boundaries may split UTF-8 characters; only the reassembled archive is decoded.

The transport stores archive bytes losslessly. It validates summary fields, descriptor sizes, completeness, and cryptographic integrity; it does **not** yet decode/validate the archive's measurement schema or prove its contents match the submitted summary. Before Android integration, define and test the versioned archive encoder/decoder covering checkpoint/settings, time-zone metadata, all intervals, source segments, measurements, route points, and splits. No production statistics or map projections may treat unvalidated archive bytes as verified measurements. Future readers must validate the decoded data and never render raw content as HTML.

The server canonicalizes manifest property order and returns a `manifestHash` over its UTF-8 JSON. That hash binds summary, run/operation IDs, ordered chunk hashes, and sizes. Begin retries with the same canonical manifest return the existing receipt; conflicting run or operation identities return 409.

## 4 Resumable upload

| Endpoint | Behavior |
| --- | --- |
| POST `/api/run-uploads` | Reserve or resume the identical manifest; return runId, operationId, manifestHash, completedAt, expiresAt |
| GET `/api/run-uploads/{runId}` | Return receipt, expiry and received chunk indexes for this owner |
| PUT `/api/run-uploads/{runId}/chunks/{index}` | Upload exact binary bytes matching the descriptor |
| POST `/api/run-uploads/{runId}/complete` | Empty body; atomically finalize only when every declared chunk exists |

Both mutation endpoints on an existing upload require `If-Match: "<manifestHash>"`. This prevents a stale client from completing or appending to a replacement draft after expiry. Chunk Content-Type is application/octet-stream. Request streams are bounded while reading; Content-Length is not trusted.

Chunks may arrive out of order. Identical retries are successful, including after completion. Wrong bytes, length, or hash return 400; indexes outside the manifest return 404. Missing chunks block completion with 409. Atomic completion uses the immutable manifest and chunk count. Concurrent completion calls return one stable completion receipt; a lost HTTP response can be retried safely. Completed archives cannot be overwritten.

At most four incomplete uploads per account may be reserved, enforced inside the atomic D1 insert; excess returns 429. Drafts expire 24 hours after reservation. Status/mutation on expired drafts returns 410. Starting an upload cleans up that account's expired drafts and their cascading chunks; inactive accounts may retain expired drafts until their next begin request. Rebegin after expiry requires sending the full manifest/chunks again. Completed records and receipts do not expire in this slice.

Clients must keep local runs and durable operation IDs until acknowledgement, use bounded retry/backoff, respect Retry-After, and require fresh same-owner authentication after 401. Upload expiry does not justify deleting the local run. Changing accounts must not change the operation owner. This client behavior remains to be implemented.

## 5 Retrieval

GET `/api/runs` returns only completed runs owned by the session: receipt plus summary, 20 per page. `next` is a cursor for `?after=...`, ordered by completion time and run ID. No route data or archive bytes appear in list responses. A cursor cannot change ownership scope.

GET `/api/runs/{runId}` returns the completion receipt and manifest. GET `/api/runs/{runId}/chunks/{index}` returns the exact binary chunk. Drafts never appear through these completed-run endpoints. Clients must verify downloaded chunk hashes, assemble in index order, validate the versioned archive, and commit restored records atomically before claiming successful restoration.

## 6 Storage and verification

Additive migration 0003 creates owner-keyed `run_uploads` and `run_chunks`. An operation ID is unique per owner. Compound foreign keys prevent cross-owner attachment of chunks. Incomplete draft cleanup cascades; completion receipts are retained. Existing account/session tables and bootstrap metadata are unchanged.

Tests use the deployed bundle under local workerd/D1 and actual Google-signature verification with test-generated keys supplied solely by the harness. Tests cover two-account isolation, concurrent retries, conflicts, expired sessions/drafts, resume, incomplete completion, exact byte downloads, request bounds, pagination, pending limits, and storage failure. No test runs or test accounts are created remotely.

The 128 KiB chunk bound is below D1's 2 MB row limit. [D1 limits](https://developers.cloudflare.com/d1/platform/limits/). The atomic operations use D1's transaction semantics. [D1 batch API](https://developers.cloudflare.com/d1/worker-api/d1-database/#batch).

## 7 Next integration gates

Implement the Android archive encoder/decoder and migration, immutable ownership, explicit import, durable upload queue and same-account retries. Couple discard with queue cancellation and remote deletion markers before enabling uploads, so a retry cannot resurrect a discarded run. Full deletion reconciliation remains its own later milestone, but the already implemented phone discard must remain correct when sync begins. Do not mark Milestone 5 complete until offline recording/reconnection, account switching, restoration, and phone checks pass.
