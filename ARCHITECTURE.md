# 1 WAYiRUN architecture

Version: 1.0
Status: Current feature-development architecture

## 1.1 Authority and boundary

This document describes the implementation on the candidate `codex/account-sessions` baseline. Product behavior is governed by `REQUIREMENTS.md`; persisted fields are governed by `DATA_MODEL.md`.

WAYiRUN has one Android application module and one Cloudflare Worker/browser application. The complete Android product remains in the debug source set. No production release architecture is implied.

## 1.2 Android structure

### 1.2.1 Pure domain

`android/app/src/main/java/com/example/runningapp/domain` contains Android-independent run behavior:

- run settings, goals, states, pause reasons and units;
- elapsed-time, distance, pace and split accounting;
- source transitions and recovery checkpoints;
- announcement and goal occurrences;
- auto-pause evidence policy;
- achievement calculation;
- Health Connect export projection.

Wall-clock time is metadata. Monotonic time drives durations.

### 1.2.2 Debug product integration

`android/app/src/debug` contains the functional application:

- Compose screens and settings;
- Google account sessions and per-account key controls;
- the foreground tracking service;
- GPS, step-counter and step-detector adapters;
- Room storage and recovery;
- authenticated cloud synchronization and restoration;
- audio cues and coaching playback;
- photo editing, sharing and publication state;
- Health Connect export workers.

All run commands and sensor messages pass through one serialized tracking-service consumer. The service commits a recovery checkpoint before playing newly emitted cues, preventing a crash from replaying acknowledged run events.

### 1.2.3 Release shell

`android/app/src/release` displays only the WAYiRUN name and excludes development services and integrations. Promoting the functional application into a release variant is later production work and is not part of this baseline.

## 1.3 Tracking flow

1. Setup creates an immutable `RunSettings` capture.
2. `TrackingService` creates or recovers a `RunController`.
3. `SensorAdapters` provide GPS, cumulative accounting steps and separately registered step detections. Detector occurrence and callback-receipt times remain distinct.
4. `TrackingInput` validates source evidence and passes measurements to the controller.
5. The controller calculates active time, distance, pace, splits, goals, announcements and pause transitions.
6. `RunRepository` atomically stores the checkpoint and any new route/measurement record.
7. New event IDs are sent to the cue queue only after persistence.
8. A finished account-owned run is queued for cloud synchronization, coaching/publication/photo work as applicable, and Health Connect export.

Indoor mode never registers location. Outdoor mode can use step fallback and later start a separate GPS segment. Pause/source/recovery boundaries remain explicit; route renderers never join separate segments.

Auto-pause uses ephemeral detector arming/silence and GPS speed/uncertainty windows, as specified in REQUIREMENTS.md 1.8. Outdoor stop windows run concurrently. Counter callbacks never drive pause/resume. No raw accelerometer listener or general motion classifier is needed. Automatic pauses retain sensor observation, foreground service and the existing partial wake lock; manual pauses release observation. Recovery requires explicit resume and fresh evidence.

## 1.4 Route-display architecture

Private and public routes are rendered as provider-independent polylines on an app-owned surface. Coordinates are normalized for public output. Renderers preserve source gaps and show start/finish markers.

No Leaflet, OpenStreetMap tiles, Mapbox, or other basemap is part of the current direction. The Worker may use a separately attributed OpenStreetMap-compatible geocoding result for coarse city/region text; that does not authorize a basemap or public raw coordinates.

## 1.5 Audio and music boundary

WAYiRUN owns its spoken/tone cue queue and requests temporary audio focus that allows other audio to duck. It releases focus after completion, failure, cancellation, or teardown.

Music integration only validates and opens a saved YouTube Music playlist. Run commands never issue player transport events, and external player/headphone events never change run state. No notification-listener access is requested.

## 1.6 Local persistence and ownership

Room stores active and completed runs, detailed samples, recovery state, sync queues, photos, publication state, achievements, and Health Connect work. A stable local owner identifies pre-account runs. A separate immutable cloud owner determines account synchronization.

Signing in never silently claims old runs. Explicit import is transactional. Account switching must not expose another account's completion workflow or private data.

## 1.7 Cloud architecture

The Cloudflare Worker uses D1 and exposes bounded routes for:

- Google authentication and sessions;
- account metadata and encrypted OpenAI-key state;
- staged run upload, immutable completion, download and deletion;
- coaching generation/status/audio/history;
- photos and cached weather;
- publication state, short public links and public run data;
- the authenticated browser application.

Run archives remain the detailed source of truth. D1 tables around them provide identity, operational state, publication, cached enrichment, coaching, and deletion behavior.

## 1.8 Browser application

The Worker serves a dependency-light browser UI for authenticated history, details, achievements, export and deletion. Browser sessions use cookies and CSRF protection rather than native bearer-token behavior.

The browser validates archives before displaying or exporting them. Public pages resolve a single published run and expose normalized route geometry rather than raw coordinates.

## 1.9 Offline and retry behavior

Tracking, timing, available distance sources, recovery, onboard coaching fallback, and local Health Connect export do not require internet. Account-owned cloud operations are durable queues with bounded retry state. Deletion markers and intent IDs prevent stale uploads or acknowledgements from resurrecting removed data.

## 1.10 Environment separation

The checked-in Worker target and Android debug configuration are development-only. Production identity, signing, OAuth, domains, secrets, capacity and deployment require a separate approved release milestone.

Local SDK configuration belongs in ignored `android/local.properties`. Wrangler state, dependencies, generated builds, credentials and secrets remain untracked.
