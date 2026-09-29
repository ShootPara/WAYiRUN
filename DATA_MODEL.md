# 1 WAYiRUN data and settings model

Version: 1.0
Status: Current feature-development contract

## 1.1 Classification

Persisted information is classified as follows:

- **SOURCE** — retained observation or user/account input that cannot be reconstructed safely from another stored field.
- **DERIVED** — reproducible from source data and safe to rebuild.
- **DUPLICATED** — intentionally repeated to support recovery, integrity validation, transport, or efficient reads; copies must agree.
- **OPERATIONAL** — queue, cursor, retry, receipt, cache, or workflow state rather than product history.
- **LEGACY** — retained only to read or migrate earlier installations/archives.

A field may have more than one classification. Duplication documented here is intentional and must not be removed as generic cleanup.

## 1.2 Run archive and checkpoint

The cloud-transfer archive format is version 1. Android stores the current recovery checkpoint as JSON inside each Room run row and sends an integrity-validated archive containing the run plus normalized detail rows.

### 1.2.1 Captured run settings

`RunSettings` contains:

| Field | Meaning | Classification |
| --- | --- | --- |
| `mode` | Indoor or outdoor | SOURCE |
| `units` | Miles or kilometers captured at start | SOURCE |
| `countdownSeconds` | 0 through 10 | SOURCE |
| `goal` | None, positive distance in meters, or positive duration | SOURCE |
| `strideLengthMeters` | Positive measured stride or null | SOURCE |
| `announcementsEnabled` | Master milestone switch | SOURCE |
| `announcementSelection` | Independent time/distance channel selections | SOURCE |
| `autoPauseEnabled` | Captured automatic-pause choice | SOURCE |
| `announcementInterval` | Earlier single-channel capture | LEGACY; used when the newer selection is absent |

Changing preferences later does not mutate this capture.

### 1.2.2 Run snapshot

`RunSnapshot` retains:

- run ID and state;
- captured settings;
- countdown remainder;
- UTC start/end times;
- active duration;
- total distance;
- average pace in milliseconds per captured unit;
- goal-reached state;
- current source-segment ID;
- measurement-source segments;
- completed full splits;
- active intervals;
- pause reason.

State, duration, distance, pace, goal state and splits are **DUPLICATED/DERIVED** within the durable checkpoint so recovery never requires replaying all samples. Source segments and active intervals are also stored as normalized Room rows for archive integrity.

### 1.2.3 Recovery checkpoint

`RunCheckpoint` adds the last monotonic and UTC clocks, emitted-event sequence, current measurement baseline and recovery epoch. These are **SOURCE/OPERATIONAL** recovery data. Wall time is metadata; monotonic time drives duration.

## 1.3 Room database

Database: `wayirun-local.db`
Schema version: 9
Checked-in schemas: 1 through 9
Migration chain: explicit 1→2 through 8→9 migrations

### 1.3.1 `runs`

| Field | Purpose | Classification |
| --- | --- | --- |
| `id` | Stable run UUID | SOURCE |
| `ownerId` | Immutable local acquisition owner | SOURCE |
| `cloudOwnerId` | Explicit account ownership, null for unimported local run | SOURCE |
| `state` | READY/RUNNING/PAUSED/FINISHED | DUPLICATED from checkpoint for queries |
| `activeSlot` | Unique unfinished-run marker; null when finished | OPERATIONAL/DUPLICATED |
| `checkpoint` | Serialized `RunCheckpoint` | SOURCE plus recovery duplication |
| `zoneId` | Start/run calendar zone | SOURCE |
| `startOffsetSeconds` | Start UTC offset | SOURCE |
| `updatedUtcMs` | Last committed checkpoint time | OPERATIONAL |
| `interrupted` | Whether recovery requires interrupted presentation | OPERATIONAL |

Completed records are immutable except for associated operational/dependent tables. Import changes cloud ownership deliberately; it does not rewrite the original local acquisition owner.

### 1.3.2 `route_points`

Each outdoor GPS point retains run ID, source segment, monotonic timestamp, latitude, longitude and accuracy meters. These are **SOURCE** observations. IDs are local database identities and are remapped safely during restore.

GPS speed and speed-accuracy evidence used by live auto-pause policy is not retained in route rows. Raw accelerometer and step-detector motion samples are also not retained.

### 1.3.3 `measurements`

Each accepted distance measurement retains run ID, segment, monotonic time, GPS/steps source, delta meters, total meters, active time and the serialized cumulative GPS/step reading.

- serialized cumulative reading and timestamps: **SOURCE**;
- delta, total and active time: **DUPLICATED/DERIVED** integrity and coaching/export evidence.

Step-counter values are retained only when accepted as distance measurements. Motion-only evidence never adds distance.

### 1.3.4 `splits`

Stores run ID, split number, actual meters, duration and partial flag. Splits are **DERIVED/DUPLICATED** from the checkpoint. Full split distance equals one captured unit; a final partial row stores its actual distance.

### 1.3.5 `active_intervals` and `source_segments`

These tables store serialized active intervals and measurement-source segments already present in the checkpoint. They are **DUPLICATED** deliberately so archive validation can prove normalized records match recovery state.

### 1.3.6 Synchronization tables

| Table | Stored state | Classification |
| --- | --- | --- |
| `run_sync` | Upload/delete operation ID, owner, status, attempts, next retry and error | OPERATIONAL |
| `sync_pull` | Owner pull phase, cursor, status, attempts, next retry and error | OPERATIONAL |

Operation IDs and deletion state provide idempotency and resurrection protection. They are not user run metrics.

### 1.3.7 `achievement_cache`

Stores serialized awards for a local/account scope. It is **DERIVED** from retained owned history and is rebuilt after relevant finish/import/delete operations. Stable award identities remain part of exported history, but the cache is not the source of run facts.

### 1.3.8 `run_photos`

| Field | Purpose | Classification |
| --- | --- | --- |
| `runId` | One retained photo per run | SOURCE relationship |
| `revision` | Immutable photo edit/version identity | SOURCE/OPERATIONAL |
| `jpeg` | Rendered bounded JPEG | SOURCE artifact |
| `options` | Selected time/distance/pace/route/weather flags | SOURCE |
| `weather` | Exact selected cached snapshot JSON | SOURCE enrichment |
| `synced`, `publicUrl`, `syncError`, `lastAttemptMs` | Upload receipt/retry state | OPERATIONAL |
| `public` | Earlier keep/public choice | LEGACY; current publication is controlled elsewhere |

Migration 6→7 explicitly retires earlier visibility semantics without deleting JPEGs.

### 1.3.9 `run_publications`

Stores the last known server publication state, revision and URL separately from pending desired share/photo intent, intent ID, request body and error. Server observation and local intent are both **OPERATIONAL**. The separation prevents stale acknowledgements from overwriting a newer choice.

Publication defaults private. A retained photo does not itself publish a run.

### 1.3.10 `health_exports`

Stores stable run-keyed PENDING/DONE/DELETE/DELETED/error state. This is **OPERATIONAL**. The source run remains authoritative; provider records can be recreated or removed using stable client IDs.

## 1.4 Cloud D1 model

D1 migrations are append-only files 0001 through 0011. Applied migration files must not be edited.

| Migration / tables | Purpose | Classification |
| --- | --- | --- |
| 0001 `service_metadata` | Application/schema readiness | OPERATIONAL |
| 0002 `accounts`, `login_challenges`, `auth_sessions` | Google identity and hashed sessions | SOURCE identity / OPERATIONAL challenge/session |
| 0003 `run_uploads`, `run_chunks` | Validated manifest and bounded archive chunks | SOURCE archive plus OPERATIONAL staging/receipt |
| 0004 `run_deletions` | Owner/run tombstones and stale-upload guard | OPERATIONAL with durable deletion meaning |
| 0005 `openai_keys` | Encrypted key envelope, revision/action/check time | SOURCE secret envelope / OPERATIONAL metadata |
| 0006 `coaching_jobs`, `coaching_audio` | Durable generation state, recap and WAV chunks | SOURCE result plus OPERATIONAL job state |
| 0007 `run_photos` | JPEG, options, revision and legacy public token | SOURCE artifact plus LEGACY token |
| 0008 `run_locations`, `location_lookup_gate` | Coarse city/region cache and provider throttle | DERIVED cache / OPERATIONAL gate |
| 0009 `public_runs`, `publication_operations` | Private/public state, photo visibility, stable tokens and idempotency | OPERATIONAL publication state |
| 0010 `run_weather`, `weather_lookup_gate` | Cached run-start weather and provider retry state | DERIVED cache / OPERATIONAL gate |
| 0011 `run_photos.weather_json` | Weather snapshot rendered into retained photo | SOURCE artifact metadata |

The completed archive is the detailed cloud run source of truth. D1 JSON summaries and manifests intentionally duplicate bounded fields so ownership, integrity, listing and retrieval can be validated without trusting arbitrary archive content.

## 1.5 Coaching data

Cloud coaching jobs retain current/previous manifest hashes, key revision, state, generated message, safe error code, audio byte count and WAV chunks. The exact verified archives are used to build the provider input but are not copied into a separate permanent context table.

The Android app retains only a small `coaching-attempt` guard with run ID, selected flag and state so reopening cannot repeat a paid attempt. Downloaded audio is a temporary cache file and is removed after playback/teardown. Private web history reads saved cloud recap/audio.

API key plaintext is not retained. D1 stores only an encrypted envelope and metadata; authentication tokens are stored hashed in D1 and encrypted locally on Android.

## 1.6 Settings inventory

General preferences use Android SharedPreferences file `local-settings` unless stated otherwise.

| Displayed setting/action | Key or location | Type / values | Default | Runtime consumer and effect | Status |
| --- | --- | --- | --- | --- | --- |
| Indoor / Outdoor | `mode` | `INDOOR` / `OUTDOOR` | `OUTDOOR` | Captured in `RunSettings`; controls GPS registration and Health exercise type | ACTIVE |
| Goal | `goal` | None / Time / Distance | None | Builds captured `RunGoal` | ACTIVE |
| Goal target | `goal-target`, `goal-target-Time`, `goal-target-Distance` | Positive decimal minutes or selected units | Blank | Captured positive duration/meters; per-type keys remember prior text | ACTIVE |
| Distance units | `units` | Miles / Kilometers | Blank until selected | Captured units for goals, split length, pace, display and speech | ACTIVE |
| Auto-pause | `auto-pause-enabled` | Boolean | true | Captured at start; enables policy and motion observation | ACTIVE; device acceptance pending |
| Run announcements | `announcements-enabled` | Boolean | true | Master milestone switch; state/goal/completion cues remain | ACTIVE |
| Time milestones | `announcement-time-enabled` | Boolean | true | Enables active-time milestones | ACTIVE |
| Time interval | `announcement-time-interval` | `FIVE_MINUTES` / `TEN_MINUTES` | five minutes | Captured threshold | ACTIVE |
| Distance milestones | `announcement-distance-enabled` | Boolean | false | Enables distance milestones | ACTIVE |
| Distance interval | `announcement-distance-interval` | `HALF_UNIT` / `ONE_UNIT` | one unit | Captured threshold in selected units | ACTIVE |
| Countdown | `countdown` | Integer 0–10 seconds | 0 | Captured pre-start countdown | ACTIVE |
| YouTube playlist | `music-playlist` | Editable string; recognized YouTube Music playlist URL required to open | Blank | Opens external playlist only; never starts a run/player automatically | ACTIVE |
| Stride unit | `stride-unit` | cm / inches | cm | Converts entry while preserving physical length | ACTIVE |
| Distance per step | `stride-entry` | Blank or positive decimal | Blank | Captured in meters; indoor and outdoor step fallback distance | ACTIVE |
| Dark mode | `dark` | Boolean | System dark state on first read | Selects saved light/dark Compose scheme | ACTIVE; not a three-state system selector |
| Request missing permissions | Android permission state | Action | N/A | Requests applicable location/activity/notification grants | ACTIVE |
| App permissions | Android Settings | Action | N/A | Opens system permission page | ACTIVE |
| Tracking notification settings | Android notification channel | Action | N/A | Opens `run-tracking` channel settings | ACTIVE |
| Google account | Encrypted `SessionStore` | Sign in/out | Signed out | Chooses cloud owner for new runs and private data | ACTIVE |
| Add existing runs | Room transaction | Explicit action | Never automatic | Assigns eligible local runs to current cloud account | ACTIVE |
| Retry synchronization | Room queue reset/action | Action | N/A | Resets eligible retries and schedules sync/pull/photo/publication work | ACTIVE |
| OpenAI API key | D1 `openai_keys` | Add/replace/remove secret | None | Enables selected account coaching with user's credits | ACTIVE |
| Health Connect | `health-connect/scope` plus OS permissions | Connect/retry/settings actions | Disconnected | Enables account/local-scope export and cleanup | ACTIVE; device acceptance pending |

## 1.7 Internal and legacy preferences

| Key | Purpose | Classification |
| --- | --- | --- |
| `local-owner` | Stable anonymous/local acquisition owner | SOURCE internal identity |
| `announcement-selection-version` | Marks completed preference migration | OPERATIONAL |
| `announcement-interval` | Earlier single-channel selection read only when new keys are absent | LEGACY |
| `health-connect/status` | Last user-readable export status | OPERATIONAL |
| `health-connect/scope` | Connected local/account scope | OPERATIONAL |
| `coaching-attempt/run`, `selected`, `state` | Prevents repeated finish coaching attempt | OPERATIONAL |

## 1.8 Code policy values that are not user settings

GPS interval, sensor thresholds, auto-pause evidence windows, route-gap limits, photo bounds/quality, coaching timeouts, fixed voice, notification-channel behavior, sync batch/retry limits, weather rounding/provider, and achievement catalog thresholds are product/implementation policy. Their presence as constants does not imply a missing user-facing setting.

## 1.9 Migration and compatibility rules

- Room schema changes require a checked-in exported schema and explicit migration.
- D1 changes require a new additive numbered migration; applied migrations are immutable.
- Archive changes require an explicit version and compatibility plan.
- Legacy fields must remain until every supported retained record can be read or migrated safely.
- Derived caches may be rebuilt; source artifacts and deliberate recovery/integrity duplication must not be discarded as cleanup.
- Schema redesign is outside the feature-baseline reconciliation.
