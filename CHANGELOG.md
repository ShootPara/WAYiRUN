# 1 Changelog

This file records user-visible and architecture-level changes. Detailed milestone evidence remains under `docs/history/`.

## 1.1 Unreleased

- Reorganized repository documentation into current authority and indexed historical evidence.
- Reconciled current-facing documentation with the Version 1.0 Android and Worker source.

## 1.2 Version 1.0.0

Release date: 2026-10-07

### 1.2.1 Added

- Android outdoor GPS, indoor step/stride and time-only run tracking.
- Foreground tracking, recovery, pause, auto-pause, goals, splits and announcements.
- Google identity with explicit ownership of local and synchronized runs.
- Private browser history, detailed archives, CSV export and deliberate deletion.
- Per-account AI coaching with durable requests, saved results and audio replay.
- Achievements, end-of-run photos, weather context and route overlays.
- Private-by-default run publication with unsharing and independent photo visibility.
- Health Connect export of completed exercise sessions and distance.
- Separate guarded development and production Worker/D1 environments.
- Owner-controlled Android release identity and signing.

### 1.2.2 Changed

- Replaced basemap-backed route presentation with provider-independent route geometry.
- Removed automatic control of external music playback; WAYiRUN opens a playlist and only ducks audio for its own cues.
- Replaced earlier auto-pause candidates with detector/GPS evidence rules.
- Separated production resources from the preserved development environment.

### 1.2.3 Distribution

Version 1.0 is prepared for owner-signed direct APK distribution. Google Play publication is deferred. The release date remains unset until the owner chooses to mark the release.
