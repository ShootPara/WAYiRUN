# Running App — Data Model

Version: 0.1  
Status: Logical design; no database or migrations created  
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

Store `run_id`, sequence, start/end cumulative distance, actual split distance, active duration, unit snapshot, and full/partial classification. Full split length follows requirements Section 5.9. Average pace is active duration divided by actual distance, converted for presentation. Do not present a zero-distance pace as zero or infinity. Final partial-split presentation must be decided before exposing it in the UI.

### 2.6 Controller checkpoint

Persist enough to restore the known state: run ID, active-duration accumulator, distance accumulator, source baseline, last accepted measurement, split progress, and emitted event identifiers. Commit the state needed to prevent duplicate goals/cues alongside the transition. Do not restore a stale Android media session token as if it were still valid.

## 3 Accounts and credentials

### 3.1 Account

Store an internal ID, unique verified Google subject, display name, profile image reference, and creation/update timestamps. A profile name or email is never the authorization key. Keep authentication sessions separate from the public profile representation.

### 3.2 Preferences

Store owner ID and versioned settings for units, stride length, dark mode, announcement selection, and music configuration once specified. Run snapshots preserve historical settings after preferences change. Do not include secrets in the preferences response or public run page.

### 3.3 AI credential

Server-only record: owner ID, provider (`openai`), ciphertext, nonce, encryption-key version, and update timestamp. A unique constraint allows one current OpenAI credential per account. The encryption key itself is not a database field. Reads to the client report configured/masked status only, never the original key.

### 3.4 Authentication session

Store a hashed session token, owner ID, expiry, and revocation status. Never store raw session tokens in diagnostics. Google token verification and session policy are specified during the account milestone.

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
