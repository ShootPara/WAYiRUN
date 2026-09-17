# Running App — Data Model

Version: 0.2
Status: Local Room schema implemented for the debug prototype; cloud schema remains a logical design
FILE: <repository-root>\DATA_MODEL.md (NEW)

## 1 Scope and conventions

[REQUIREMENTS.md](REQUIREMENTS.md) owns user-visible behavior. [ARCHITECTURE.md](ARCHITECTURE.md) owns storage and synchronization choices. This document defines records and invariants; exact SQL, Room entities, indexes, and migrations belong to their implementation milestones.

Use opaque stable identifiers. Store distances in meters, durations as integer milliseconds, timestamps in UTC, and the run's original time-zone information separately. Display units do not change stored measurements. Treat missing values as missing, not zero. Reject non-finite or negative distance/duration values. Do not store calculated pace as an independent authoritative measurement.

## 2 Core local run records

### 2.1 Run

| Field | Meaning and rule |
| --- | --- |
| id | Client-generated stable run identifier, unchanged on retry |
| owner_id | Account that owns the run; immutable after assignment |
| state | Running, Paused, or Finished; countdown is pre-run UI state |
| started_at, ended_at | UTC timestamps; ended_at exists only when finished |
| time_zone_id, start_offset | Preserve the start-time context; holiday interpretation still needs its product rule |
| active_duration_ms | Accumulated running time excluding pauses |
| distance_m | Accepted accumulated distance excluding paused movement |
| mode | Indoor or Outdoor |
| display_units | Snapshot of miles or kilometers at start |
| stride_length_m | Configured stride snapshot; unknown is not replaced by an invented personal value |
| goal_type, goal_value | None, time in milliseconds, or distance in meters; only the matching value is valid |
| countdown_seconds | Selected pre-run setting |
| start_music | Selected pre-run music setting |
| announcement_selection | Selected supported interval; kilometer mapping remains unresolved |
| goal_announced | Records whether the goal event has been emitted |
| ai_requested | Final checkbox value, persisted at finish |
| measurement_version | Calculation version used to produce the recorded metrics |
| local_revision, server_revision | Synchronization metadata, not user-visible statistics |

Persist a completed run and its final measurements atomically. A completed run cannot return to Running. At most one local run may be Running or Paused at a time. Do not silently change an active run's measurement settings when account preferences change.

### 2.2 Active interval

Store `run_id`, sequence, start/end monotonic clock values, start/end UTC timestamps, and accumulated active duration. A monotonic clock value is comparable only within the same device boot. The interval is closed on pause/finish; a new interval begins on resume. A restart must not count an unobserved interval as verified running time.

### 2.3 Measurement interval

Store `run_id`, sequence, active interval reference, source (`gps` or `steps`), time bounds, accepted distance increment, and cumulative active duration/distance. This links metric changes to exactly one source. Baseline step counters are device-cumulative values; only accepted differences within the active interval count for this run. Counter resets and backward readings invalidate the old baseline rather than producing negative distance.

### 2.4 Route point

Store `run_id`, segment identifier, sequence, measurement timestamp, latitude, longitude, and accuracy when available. Constrain coordinates to valid ranges and enforce uniqueness of sequence per run. Route segment boundaries mark pauses and location gaps; maps must not connect separate segments. Step-only intervals contain no fabricated route points.

### 2.5 Split

Store `run_id`, sequence, start/end cumulative distance, actual split distance, active duration, unit snapshot, and full/partial classification. Full split length follows requirements Section 5.9. Average pace is active duration divided by actual distance, converted for presentation. Do not present a zero-distance pace as zero or infinity. A nonzero final remainder is shown as a Partial row with its actual distance, active duration, and pace, as approved in requirements Section 5.9.

### 2.6 Controller checkpoint

Persist enough to restore the known state: run ID, active-duration accumulator, distance accumulator, source baseline, last accepted measurement, split progress, and emitted event identifiers. Commit the state needed to prevent duplicate goals/cues alongside the transition. Do not restore a stale Android media session token as if it were still valid.

### 2.7 Proposed time estimation — not in the current schema

The September 14 report proposes time-derived distance and a history-derived pace. This has not changed the accepted missing-source rule or Room v1. Before implementation, agree split eligibility and kilometer-history handling, then plan how to retain estimation provenance and the pace used for a run without treating estimated samples as measured training data. Preserve existing records with a tested migration if schema changes become necessary. Do not retrofit invented distances into saved runs or add route points for estimated intervals. See PHONE_TEST_REVIEW_2026-09-14.md Section 3.1 for unresolved product decisions.

## 3 Accounts and credentials

Phone-summary discard is implemented against Room v1 with no schema change. A transaction checks the selected run's owner and Finished state, then deletes its row; all five child tables cascade. Missing rows are an idempotent success; active runs and owner mismatches are refused. The serialized service validates the confirmation's run ID and clears its controller/checkpoint only after success. Stale media commands cannot revive the discarded run. Unrelated records/preferences survive, and no hidden run record is retained. Future remote deletion synchronization remains Section 5.3's separate concern.

### 3.1 Account

Store an internal ID, unique verified Google subject, display name, profile image reference, and creation/update timestamps. A profile name or email is never the authorization key. Keep authentication sessions separate from the public profile representation.

### 3.2 Preferences

Store owner ID and versioned settings for units, stride length, dark mode, announcement selection, and music configuration once specified. Run snapshots preserve historical settings after preferences change. Do not include secrets in the preferences response or public run page.

### 3.3 AI credential

Server-only record: owner ID, provider (`openai`), ciphertext, nonce, encryption-key version, and update timestamp. A unique constraint allows one current OpenAI credential per account. The encryption key itself is not a database field. Reads to the client report configured/masked status only, never the original key.

### 3.4 Authentication session

Store a hashed session token, owner ID, expiry, and revocation status. Never store raw session tokens in diagnostics. Google token verification and session policy are specified during the account milestone.

Worker migration `0002_accounts.sql` now implements accounts, auth_sessions, and login_challenges. Sessions store SHA-256 hashes of 256-bit random tokens, last one hour, and support per-session revocation. Challenge hashes expire after five minutes and are consumed atomically with session insertion. Google subject is unique; profile changes retain the same internal account UUID. Migration 0003 adds owner-scoped immutable run upload manifests and binary chunks; Android run/owner import is not connected yet. Details and activation limits are in `worker/AUTH_CONTRACT.md`.

Android signin-settings1 stores the verified account profile/internal ID and session token/expiry in one AES-GCM encrypted atomic file under noBackupFilesDir; the key stays in Android Keystore. Tampered/unreadable records restore no account. Expiry retains the known local profile while requiring fresh sign-in for authenticated server use. Sign-out removes the phone session and attempts server revocation; offline server sessions expire within one hour. This store never writes the existing local-settings owner or Room run rows. Preference edits (including goal/target and unfinished playlist entry) save as they change; active-run checkpoints retain their original settings snapshots.

## 4 Run attachments and presentation

### 4.1 Photo

Store photo ID, owner/run references, private object key, MIME type, dimensions, selected overlay fields, route-overlay choice, and upload state. Do not persist an entire camera library or upload source photographs merely because the user opened the picker. The selected finished image is the required stored artifact.

### 4.2 Publication

Store run ID, owner ID, an opaque public-link token, publication state, referenced confirmed photo, and timestamps. Enforce one publication state per run. Publishing requires completed data and the accepted photo confirmation action. A public projection is explicitly constructed from allowed run fields; it is not serialization of the account or raw database row.

### 4.3 Coaching result

Store coaching ID, owner/run references, request identity, state, result text when available, private audio-object reference, provider/model metadata, and a sanitized failure category. Unchecked AI produces no coaching request. Distinguish failed, pending, succeeded, and unknown-outcome requests so retries cannot silently issue duplicate billable calls.

### 4.4 Achievement definition and award

A definition contains a stable ID, rule version, condition parameters, display text, and animation reference. An award links owner, achievement/rule version, qualifying run or period, earned time, and reveal state. Define the idempotency key after repeatability and calendar rules are approved. Do not invent achievement thresholds in schema defaults.

## 5 Synchronization and deletion

### 5.1 Pending operation

Local record: operation ID, owner ID, entity ID/type, operation type, payload version/hash, attempt count, last sanitized error, and next attempt time. Operations include completed-run upload, confirmed-photo upload, publication, and Health Connect export. Each has its own state so a failed photo upload cannot invalidate a saved run.

### 5.2 Server operation receipt

Store operation ID, authenticated owner, target ID, payload hash, and result/version. Enforce unique operation identity per owner. The same ID and payload returns the same result; reuse with different content fails. Do not trust an owner field supplied by the client.

Migration 0003 implements the transport receipt in `run_uploads` and exact ordered binary data in `run_chunks`. The manifest hash binds the immutable summary and chunk descriptors to run/operation IDs. A compound foreign key keeps every chunk under the same owner/run; only fully acknowledged archives have a completion timestamp and appear in history. Drafts expire after 24 hours and are cleaned on that owner's next upload reservation; completed receipts do not expire. Android archive encoding/decoding and measurement validation are implemented; sync2 adds authenticated restore validation. See `worker/RUN_STORAGE_CONTRACT.md` for limits and API details.

### 5.3 Deletion marker and cleanup

Retain only the minimum owner/run identifier, deletion revision, and timestamp needed to stop stale synchronization from recreating deleted content. Remove run metrics, route points, attachments, and coaching contents. A cleanup job may temporarily hold private object identifiers until removal succeeds. The marker is not hidden run history.

Immediately prevent public access and reject new uploads/coaching/publication for a deleted run. Synchronize deletion to clients on reconnection. Exact marker retention and treatment of Health Connect copies must be resolved in the deletion milestone. Retry cleanup until objects are removed; D1 and R2 deletion is not one cross-service transaction.

### 5.4 Health Connect export state

Store run ID, owner ID, stable client record ID, destination record IDs where returned, export state, last attempt, and sanitized error category. Duplicate exports of the same run must use the same identity. Permission denial is not run failure.

## 6 Relationships and access constraints

An account owns runs and preferences. A run owns its intervals, measurements, route, splits, photos, coaching, and publication. Awards reference their qualifying runs/periods. Each private server query is scoped by authenticated owner and target identifier. Use owner-consistent foreign-key relationships or equivalent validated constraints so records from different accounts cannot be joined accidentally.

The implementation must verify query plans for owner/date history and ordered per-run measurements. Large route payloads need bounded batches with sequence validation. CSV representation must be defined before export code so that “all run data” is not silently reduced to summary rows.

## 7 Initial implementation subset

The local tracking milestone needs Run, Active interval, Measurement interval, Route point, Split, and Controller checkpoint. Use a development-only local identity while cloud authentication is absent, confined to a debug build with no cloud access. Production builds must not ship an authentication bypass. Do not create all future cloud tables during the local prototype milestone.

### 7.1 Implemented Room subset

Schema version 1 is exported under `android/app/schemas/com.example.runningapp.storage.RunDatabase/1.json`. Debug-only tables are `runs`, `active_intervals`, `source_segments`, `measurements`, `route_points`, and `splits`. A unique nullable active-slot column enforces at most one unfinished run. Child rows have run foreign keys. The DAO transaction saves the checkpoint, intervals, source segments, splits, and any new measurement/route point together; it rejects writes to a completed run and changes of local ownership.

The run checkpoint uses Kotlin serialization and holds the unit/stride/goal snapshot, active totals, full splits, measurement baseline, active intervals and clock epochs, source segments, and event sequence. The run row separately preserves zone/offset and interruption state. Pace is derived. Final partial splits store actual distance and duration. Measurements retain accepted cumulative readings and deltas; route points exist only for accepted GPS samples. No GPS points are fabricated for step-only or missing intervals.

The locally generated owner identifier has no cloud account meaning and is confined to debug preferences/database records. This original v1 subset did not implement production identity, authentication bypasses, network calls or cloud tables; Sections 7.2 onward describe subsequent additions. Tests use isolated in-memory Room databases. Schema migrations will be required before changing persisted tables in a subsequent milestone; no destructive migration fallback is enabled.

### 7.2 Implemented synchronization subset - Room v2

Migration 1 to 2 preserves every existing row and adds nullable cloudOwnerId to runs. The original ownerId remains immutable acquisition identity; explicit import assigns cloudOwnerId only to completed unassigned runs. New runs capture the known account at start, even when its session has expired. A finished save and its upload operation are one transaction.

run_sync stores runId, ownerId, stable operationId, UPLOAD/DELETE action, PENDING/AUTH/BLOCKED/SYNCED/DELETED status, attempts, nextAttemptMs and a sanitized error. It deliberately has no run foreign key so deletion intent survives removal of measurements. Conditional acknowledgements cannot replace a newer DELETE action. Archives preserve all six run tables with version, size and structural validation; chunks have SHA-256 descriptors and stable ordering.

Cloud migration 0004 retains only owner/run ID/deletion time after removing manifest and chunk data. A database trigger prevents resurrection by stale uploads. Marker retention policy remains future work; sync2 implements download-side reconciliation; no metrics are retained in deletion markers.

### 7.3 Restore progress - Room v3

Additive migration 2 to 3 creates sync_pull keyed by ownerId: phase (DELETIONS/RUNS), cursor, status, attempts, nextAttemptMs and sanitized error. Existing runs and upload/delete operations are unchanged. No token appears in this table. Restored records retain original acquisition/cloud ownership and every archived metric while regenerating local autoincrement child IDs. Restore never upserts a conflicting existing run. Minimal local tombstones are created even when a deleted remote run was never downloaded, preventing stale-list restoration. Partial archives remain outside Room and cannot appear as finished runs.
