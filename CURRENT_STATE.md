# 1 WAYiRUN current state

Status: Feature-development baseline candidate
Repository authority: current implementation plus accepted decisions in `REQUIREMENTS.md`
Candidate lineage: `codex/account-sessions`

## 1.1 Baseline boundary

WAYiRUN is substantially functional as a development system. The complete Android product is compiled only in the `debug` variant; the `release` variant intentionally remains a name-only shell. This baseline describes delivered feature behavior without claiming production readiness or comprehensive physical-device acceptance.

The Cloudflare Worker is configured only for the development environment. Development deployment history is evidence but deployment parity is not part of this feature-baseline gate.

## 1.2 Branch reconciliation

The current lineage and `origin/development` share commit `5b5e7ed`. The seven commits unique to `origin/development` cover restore/deletion reconciliation, desktop history, Google styling, Leaflet route maps, CSV export, session/transfer limits, and desktop deletion. Current HEAD contains the still-valid restore, history, authentication, export, rate/limit, and deletion behavior plus later tests and features.

The only material behavior unique to that older line is its Leaflet/OpenStreetMap basemap implementation. That behavior is explicitly superseded by provider-independent route noodles. No still-valid functionality was identified only on `origin/development`; it must not be merged wholesale.

## 1.3 Delivered Android behavior

| Area | Current implementation | Verification boundary |
| --- | --- | --- |
| Identity | Google sign-in, encrypted local session, display name/photo, sign-out, explicit local-run import | Account isolation is automated; multi-account phone acceptance remains later |
| Setup | Indoor/outdoor, none/time/distance goal, target, units, stride, countdown, announcements, auto-pause, playlist opening, connectivity/run indicators | Compose and instrumentation coverage exists |
| Tracking | GPS, steps, stride distance, outdoor step fallback, time-only mode, source segments and route gaps | Quantitative sensor accuracy requires later device acceptance |
| Run control | Countdown, pause, resume, automatic pause/resume, swipe finish, discard confirmation, new run | Automatic-pause device threshold remains unverified after the latest correction |
| Recovery | Room checkpoints, interrupted pause, explicit resume, completed-run reopen rules | Process/reboot and external-activity acceptance remains later |
| Metrics | Active time, distance, average pace, full splits and final partial split | Pure calculation coverage exists |
| Audio | State, goal, milestone and completion cues with serialized playback and transient audio focus | Real speaker/headphone ducking remains later acceptance |
| Music | Saved YouTube Music playlist opening only | No transport commands or notification-listener access |
| Finish | Optional coaching, achievement celebration, summary, photo, sharing and discard/new-run controls | Combined physical flow remains later acceptance |
| Coaching | Per-account OpenAI key, verified current/previous run context, anomaly classification, saved cloud recap/audio and onboard fallback | Paid-call tone and device playback remain later acceptance |
| Achievements | Historical deterministic awards, finish celebration and deletion recomputation | Broader paused/source-changing performance curves are deliberately deferred |
| Photos | Camera/picker/skip, bounded decode, time/distance/pace/route/weather overlays, preview/retake/keep/save/share | Real camera and share-target acceptance remains later |
| Publication | Private default, durable publication intent, short links, photo visibility and unshare | Automated state-machine coverage exists |
| Health Connect | Explicit write-only connection, exercise/distance export, stable IDs, retries and deletion cleanup | User-phone consent/display acceptance remains later |

## 1.4 Delivered Worker and browser behavior

- Google native and browser authentication with owner isolation.
- Resumable immutable completed-run archives with integrity-checked chunks.
- Pull/restore and deletion-marker reconciliation.
- Private paginated desktop history, loaded-scope totals, detail, splits and settings.
- Provider-independent route-noodle rendering with gaps and start/finish markers.
- Coarse city/region display derived separately from route graphics; OpenStreetMap attribution applies to geocoding data, not a basemap.
- Complete CSV export including retained detail and reconstructable media records.
- Account-scoped encrypted OpenAI keys, durable coaching jobs and saved audio.
- Deterministic run-quality context for likely tests, vehicles, or GPS anomalies.
- Achievements calculated from retained owner history.
- Photo storage, weather cache, public-run pages and private-by-default publication controls.
- Authenticated run deletion with dependent-data cleanup.

## 1.5 Persistence

- Room database version 9 with checked-in schemas 1 through 9 and explicit migrations.
- Run archive format version 1 with validated source, timing, route, measurement, split, interval, and segment records.
- D1 migrations 0001 through 0011 for accounts, sessions, archives, deletion markers, keys, coaching, photos, locations, publications, and weather.
- SharedPreferences for ordinary local settings and small workflow/connection state.
- Android Keystore plus an encrypted no-backup atomic file for the Google session.

The detailed classification of source, derived, duplicated, operational, and legacy data is in `DATA_MODEL.md`.

## 1.6 Verification state

The repository contains JVM, debug-unit, Android instrumentation, Worker, browser, and guarded development-rollout tests. Historical generated reports were removed during baseline cleanup and are not treated as fresh evidence. `TEST_PLAN.md` defines the clean verification required for this baseline.

The clean local baseline gate passed on September 29, 2026:

- Android: 118 unit/debug-unit tests passed with zero failures or skips; debug assembly and lint completed successfully.
- Android lint: zero errors and 21 warnings (nine dependency updates, five KTX suggestions, three SharedPreferences apply suggestions, two newer-version notices, one Android Gradle Plugin version notice, and one obsolete-SDK check).
- APK: `WAYiRUN-2026-09-29_08-41-13_EDT.apk`, 40,290,032 bytes, SHA-256 `dbe02e896306c4c7671577c281b0fcd8131df48a90b33b7a980d59ae1e015ffa`.
- Worker: strict TypeScript compilation, Wrangler deployment dry run, and all 148 local tests passed.
- Documentation: all authoritative files exist, local Markdown links resolve, and `git diff --check` reports no content errors.

The clean npm install reports three moderate development-tool advisories through `undici` in `miniflare`/`wrangler`. No high or critical advisory is reported. The available automated fix proposes downgrading pinned tooling and is not applied as baseline cleanup; review it in a separately verified dependency-maintenance milestone.

Automated verification does not prove physical GPS/step accuracy, carried-phone auto-pause, screen-off longevity, real audio focus behavior, camera/share targets, user-phone Health Connect behavior, or production readiness.

## 1.7 Deliberate exclusions and supersessions

The following are not missing baseline features:

- Leaflet, map tiles, or any basemap;
- automatic music playback or media transport;
- notification-listener access;
- automatic publication when keeping a photo;
- social feeds, clubs, stores, advertisements, or user interactions;
- calorie tracking or general health aggregation;
- Health Connect imports;
- a watch application;
- automatic photo restoration to a new phone;
- deferred statistics periods/charts or coaching voice selection.

## 1.8 Baseline limitations

- The release Android variant is not the product implementation.
- The application ID, OAuth clients, Worker URL, secrets/configuration, signing, policy declarations, and deployment process remain development/release concerns.
- Comprehensive physical-device acceptance and development-deployment parity are intentionally outside this reconciliation sequence.
