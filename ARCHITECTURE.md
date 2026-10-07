# 1 WAYiRUN architecture

Version: 1.0
Status: Current implementation architecture

## 1.1 System boundary

WAYiRUN consists of one Android application module and one Cloudflare Worker/browser application. Android owns run capture and the authoritative local record. The Worker supplies account identity, owner-isolated synchronization, coaching, browser history, media and explicit public-run pages.

Development and production use separate Android identities, OAuth clients, Worker configurations, rate-limit namespaces and D1 databases. Environment selection is compile-time/configuration-time; the shipped app has no runtime environment switch.

## 1.2 Android source structure

The functional product lives in `android/app/src/main`. The Kotlin namespace is `com.example.runningapp`; the production application ID is `com.unopenedparachute.wayirun`, while debug is explicitly set to `com.example.runningapp.debug`.

Important packages are:

- `domain`: pure Kotlin run state, accounting, goals, splits, achievements and auto-pause policy.
- `tracking`: foreground service, sensor adapters, notifications, cues and playlist opening.
- `storage`: Room entities, migrations, run archives and publication state.
- `sync`: upload, restore, download cache and background synchronization.
- `account`: Google identity, sessions and per-account coaching-key controls.
- `coaching`: durable request synchronization, generated/fallback audio and finish behavior.
- `photos`: camera/picker flow, rendering, weather context and photo synchronization.
- `sharing`: publication intent and Android sharing.
- `health`: Health Connect adapter, export engine and retry worker.
- `ui`: Compose screens and settings.

Unit tests live in `src/test`, variant configuration tests in `src/testDebug` and `src/testRelease`, and instrumentation tests in `src/androidTest`.

## 1.3 Run domain and tracking

`RunController` is the central state machine for countdown, running, manual/automatic pause, recovery, finish and discard. It operates on domain inputs rather than Android sensors directly. Captured run settings prevent later preference changes from rewriting an active or recovered run.

`TrackingService` owns active background tracking and the ongoing notification. GPS, detector and cumulative step-counter callbacks enter through adapters. `AutoPausePolicy` evaluates bounded detector/GPS evidence; the cumulative counter remains a distance-accounting input rather than a silence detector.

Route and distance accounting preserve source changes and gaps. Pause transitions close measurement intervals without inventing retrospective samples.

## 1.4 Local persistence

Room database `wayirun-local.db` is schema version 10. It stores runs, route points, measurements, splits, active intervals, source segments, synchronization operations, coaching requests, pull state, achievements, photos, Health Connect state and publication state.

Migrations 1→10 are explicit and registered in `RunDatabase`. Exported schemas 1–10 are checked in under `android/app/schemas`. Applied migrations and historical schemas are immutable.

Completed runs are serialized into archive format version 1 for upload and reconstruction. Android remains the source of truth for captured run behavior; cloud copies support synchronization, browsing and recovery.

## 1.5 Account and synchronization

Google identity is exchanged for a bounded Worker session. Data APIs require an authenticated owner and enforce ownership server-side. Pre-account local runs remain local until explicitly imported. New runs capture the selected owner at start.

Synchronization uses stable run and operation identifiers, immutable archive chunks, receipts and deletion tombstones. Restore validates complete archives before writing local records. Account changes invalidate stale work so callbacks cannot mutate another owner's state.

## 1.6 Coaching, photos and publication

Per-account OpenAI keys are encrypted by the Worker and never included in Android, exports or public pages. Coaching requests are durable and idempotent; saved results and audio can be replayed from private history.

Photo rendering is local, orientation-aware and bounded. Overlays use retained time, distance, pace, route geometry and available cached weather. Photo synchronization and publication are separate state machines.

Runs are private by default. Explicit sharing records publication intent. Public pages expose only the selected run and use provider-independent route geometry without raw-coordinate APIs or basemap dependencies.

## 1.7 Health Connect

Health Connect is isolated behind `HealthAdapter`. WAYiRUN writes completed exercise sessions and distance only. Stable record identifiers support retry and deletion cleanup. Health Connect is not a source for run imports.

## 1.8 Worker and browser

The Worker entry point is `worker/src/index.ts`. Supporting modules cover authentication, environment guards, runs, coaching, photos, location/weather, publication, public routes and browser history.

D1 schema is defined by immutable migrations `0001`–`0011`. Browser assets under `worker/web` are bundled by Wrangler and served from the same origin as the API. Styled application controls are used instead of native browser alert/confirm flows.

Development configuration is `worker/wrangler.jsonc`; production is `worker/wrangler.production.jsonc`. Deployment scripts validate the exact account, Worker, database and environment before allowing remote mutation.

## 1.9 Secrets and generated artifacts

Signing properties, keystores, keyring material, private backups, migration evidence, Wrangler state, local SDK paths, build output and node modules are ignored. Public client IDs and resource identifiers required for configuration are not credentials; private keys, passwords, session secrets and user OpenAI keys must never enter Git.
