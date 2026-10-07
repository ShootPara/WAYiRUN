# 1 WAYiRUN data model

Version: 1.0
Status: Current persistence contract

## 1.1 Ownership and identity

Every cloud-owned run and dependent record belongs to one verified account. Stable run IDs link archives, coaching, photos, publication, achievements and Health Connect state. Server APIs enforce owner isolation rather than trusting Android-supplied ownership claims.

Pre-account runs have no cloud owner. Signing in does not claim them; explicit import assigns the selected account. A run started while an account is selected retains that owner even when upload is delayed.

## 1.2 Room database

Database: `wayirun-local.db`

Schema version: 10

Checked-in schemas: 1–10 under `android/app/schemas/com.example.runningapp.storage.RunDatabase/`

Current entity groups include:

- Run summary, captured settings and lifecycle state.
- Route points and detailed measurements.
- Splits, active intervals and source segments.
- Durable synchronization operations and owner pull state.
- Durable coaching requests.
- Owner-scoped achievement cache.
- Run photo content and synchronization/weather metadata.
- Health Connect export state.
- Publication intent, known server state and revision data.

Room migrations are additive from 1→10. Schema 10 adds durable coaching-request state. Applied migration implementations and exported historical schemas must not be edited; future changes require a new schema and migration.

## 1.3 Run record

A retained run includes stable identity and ownership, start/end time and time-zone context, active duration, distance, average pace, captured preferences, run state, pause reason, splits, active intervals, measurement/source segments and route points when available.

Source gaps are data. They must not be joined, interpolated or represented as continuous GPS. Paused time and measurements excluded by the controller remain excluded from reconstructed totals.

## 1.4 Archive format

Completed-run archive version is 1. It contains the retained run plus route, measurement, split, interval and source-segment collections required for reconstruction.

Uploads use a manifest and bounded immutable chunks. The Worker validates ownership, shape, size, completeness and content hashes before accepting a completed archive. Existing valid local records are not overwritten blindly during restore.

## 1.5 Worker/D1 schema

Applied migrations are:

1. `0001_bootstrap.sql`
2. `0002_accounts.sql`
3. `0003_run_uploads.sql`
4. `0004_run_deletions.sql`
5. `0005_openai_keys.sql`
6. `0006_coaching_jobs.sql`
7. `0007_run_photos.sql`
8. `0008_run_locations.sql`
9. `0009_public_runs.sql`
10. `0010_run_weather.sql`
11. `0011_photo_weather.sql`

These cover accounts/sessions, resumable run storage, deletion protection, encrypted OpenAI-key envelopes, coaching jobs/results/audio, photos, coarse location, publication and weather state. Applied SQL files are immutable; future schema changes use a new numbered migration.

## 1.6 Synchronization and deletion

Synchronization operations use stable identifiers and retry state. Completed uploads return immutable receipts. Deletion tombstones prevent stale or racing uploads from resurrecting a removed run.

Deleting a run reconciles its archive, chunks, coaching, photo, publication and related states within the owning scope. Health Connect cleanup may remain pending when device permission is unavailable; the WAYiRUN record remains the authority.

## 1.7 Coaching data

Android persists durable coaching-request state in Room. The Worker persists jobs, bounded attempts, results and audio chunks. Idempotency keys prevent interruption from creating duplicate paid attempts.

User OpenAI keys are encrypted server-side. Plaintext keys, keyring material and authorization headers are not stored in run archives, exports or logs.

## 1.8 Photos, weather and publication

Photos are bounded rendered JPEGs associated with a stable run and revision. Source image metadata is not part of the retained rendered output. Weather is cached run-start context with provider attribution and may be absent.

Publication state is independent from photo retention. A run remains private unless explicit intent requests publication. Shared/unshared state and photo visibility are independently revisioned. Public responses expose only the selected public representation, not private archives or raw coordinate APIs.

## 1.9 Settings

User preferences include units, countdown, stride, announcements, auto-pause, appearance, playlist and integration controls. Active runs store the behavior-affecting settings captured at start; later preference changes apply only to later runs.

Stride may be absent. No default stride is invented. Secret and account-session material is not a run setting and is excluded from exports.

## 1.10 Export and sensitive data

CSV export contains retained summaries and reconstructable detailed records, including related metadata where present. It excludes API keys, keyring material, session secrets, authorization headers and signing material.

Local databases, migration packages, private backups, generated media evidence and signing files remain outside Git.
