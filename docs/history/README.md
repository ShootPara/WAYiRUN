# 1 WAYiRUN historical material

The documents indexed here are retained as implementation evidence. They are not current product authority and must not override `REQUIREMENTS.md`, `CURRENT_STATE.md`, `ARCHITECTURE.md`, `DATA_MODEL.md`, `OPEN_WORK.md`, or `TEST_PLAN.md`.

## 1.1 Historical execution records

- `TASKS.md` — cumulative milestone diary and verification history.
- `MILESTONE_*_TEST_HANDOFF.md` — point-in-time handoff evidence.
- `PHOTO_SYNC_REPAIR.md` — September 28 development repair and deployment record.
- `CODE_REVIEW_TRIAGE_2026-09-16.md` — point-in-time review disposition.
- `a temporary handover record` — conversation handover/scratch record.

## 1.2 Historical phone records

- `WAYiRUN_Phone_Test_Report_2026-09-14.md`
- `PHONE_TEST_*.md`
- `MUSIC1_PHONE_RETEST.md`
- `android/PHONE_TEST_COACHING.md`
- `android/PHONE_TEST_PHOTOS.md`

These describe specific development builds. They do not establish current behavior unless current source and `TEST_PLAN.md` agree.

## 1.3 Historical plans and contracts

- `NEXT_AUDIO_MEDIA_PLAN.md` — superseded media-control planning.
- `PHOTOS_PLAN.md` — historical photo contract; its keep-implies-public behavior is superseded.
- `AUTO_PAUSE_AND_MILESTONES_PLAN.md`, `ACHIEVEMENTS_PLAN.md`, and `HEALTH_CONNECT_PLAN.md` — implemented milestone plans with useful decision evidence.
- `ISSUES_AND_QUIBBLES_IMPLEMENTATION_PLAN.md` — accepted historical execution sequence; current results are summarized elsewhere.
- Worker `*_CONTRACT.md` and `*_PLAN.md` files — implementation-level evidence and API invariants. Current product authority remains at the repository root.

## 1.4 Explicitly superseded behavior

Historical references to the following behavior are not open work:

- Leaflet/OpenStreetMap or other basemaps;
- automatic music/player transport;
- notification-listener access;
- keeping a photo automatically making it public;
- prompting for setup or permissions on every foreground return;
- automatically claiming local runs during sign-in.

The current replacements are route-noodle rendering, playlist-opening-only music integration, private-by-default publication, startup-only permission requests, and explicit account import.
