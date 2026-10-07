# 1 WAYiRUN open work

Status: Current unresolved and deferred work

## 1.1 Known physical-device verification

The following behavior is implemented and has automated or emulator evidence but may still benefit from representative physical-device observation:

- Detector/GPS auto-pause behavior while carried, stationary and screen-off.
- Quantitative outdoor distance, pace and route accuracy against an independent reference.
- Indoor step delivery and stride-derived distance on representative hardware.
- GPS loss, reacquisition and step fallback without jumps or double counting.
- Long-running foreground-service, notification, recovery and battery behavior.
- Speaker/headphone cue ducking and restoration with YouTube Music.
- Camera, image picker, rotation, save and platform share targets.
- Health Connect consent, displayed records, retry and deletion cleanup.

These are evidence limitations, not permission to redesign implemented behavior. Any reproduced defect should be recorded separately with the exact build, device and conditions.

## 1.2 Deferred product work

- Desktop week/month/year/lifetime statistics selectors, sorting and charts.
- Coaching voice selection; Cedar remains fixed.
- Broader performance-achievement calculations across discontinuous measurement evidence.
- Automatic restoration of cloud photos onto a new phone.
- Google Play publication and its signing, listing and policy workflow.

## 1.3 Explicit exclusions

The following are not open work for Version 1.0:

- Basemaps or map-tile providers.
- Automatic external-player transport control or notification-listener access.
- Social feeds, clubs, advertisements or user-to-user interactions.
- Calorie tracking or general health aggregation.
- Health Connect imports.
- A standalone watch application.

## 1.4 Repository and release administration

Repository cleanup, licensing, tagging, publishing and visibility are owner-directed administrative actions rather than product defects. Their absence must not be represented as missing runtime behavior.
