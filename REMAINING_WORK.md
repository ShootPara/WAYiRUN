# 1 Remaining WAYiRUN work

Current execution authority: REQUIREMENTS.md, ISSUES_AND_QUIBBLES_IMPLEMENTATION_PLAN.md version 0.2, and subsequent explicit user decisions. Issue-plan Milestones 1-14 are locally complete. the owner authorized the September 28 photo-sync repair and matching development deployment; the Worker and migrations 0008-0011 are now live. PHOTO_SYNC_REPAIR.md records recovery, Android queue/status hardening, verification and the current handoff. Physical-phone acceptance remains. No automatic commit/push or production deployment is authorized.

Milestone 7 is complete: automatic media transport/listener/access UI is removed while playlist opening and cue ducking remain. The Android build/JVM/lint gate and 22 focused emulator tests passed. Real YouTube Music/headphone behavior and audible ducking remain phone acceptance; no phone APK was installed or handed off.

Milestone 10 is complete: square emoji controls, responsive large-text layout and colored left/right statuses passed Android build/lint, 17 focused emulator tests and normal/200-percent visual inspection. Phone visual acceptance remains deferred.

Milestone 6 is complete: interval events, gear settings, audio wiring and recovery behavior passed 78 JVM tests, the Android build/lint gate, focused emulator coverage and light/dark/200-percent visual inspection. One run-entry lifecycle case timed out after that combined emulator suite and passed in isolation; its later M5.1 failure remains unresolved. Real speaker/headphone ducking and screen-off delivery remain phone acceptance.

Milestone 8 is complete: pure policy, controller accounting, sensor/service integration, default-on captured setting, automatic cues, manual override and interrupted recovery passed 95 JVM tests, Android build/lint, 26 focused emulator tests and repeated real-service visual runs. Light, dark and 200-percent screens passed inspection. Real movement thresholds, GPS accuracy, step batching, permission changes, audible cues and screen-off behavior remain phone acceptance.

Milestone 9 is complete: deterministic current-run quality evidence and anomaly-aware coaching instructions passed Worker compilation, deployment dry-run and all 118 tests. Suspicious runs still use the existing durable coaching flow. Real generated tone and heuristic thresholds remain acceptance with eventual user recordings; no paid call was made.

Milestone 4 is complete. The 4.0 Worker/web contract passed all 125 Worker tests and browser checks; 4.1 Android durable intent, migration and controls passed the 95-test JVM gate, 49 distinct focused emulator cases and light/dark/200-percent visual inspection. The matching backend is deployed as part of PHOTO_SYNC_REPAIR.md; its live photo/publication canary passed.

Milestone 5 is complete: private archive-derived weather, stable owner/run cache, photo rendering/editor, Room v8, Worker metadata, attribution and CSV integration passed 146 Worker tests, 95 JVM tests, Android build/lint, focused emulator cases, dark 200-percent visuals and mobile/desktop browser checks. The existing RunEntry lifecycle/photo-editor failure remains for Milestone 11. Physical camera/picker/share and real-provider acceptance are deferred.

## 1.1 Ready for phone acceptance: photos and achievements

TASKS 9.5 photos are implemented: camera/picker/skip, selectable overlays, preview/retake/keep, Android save/share, queued cloud storage, public individual-run pages and CSV photo records. Test with achievements on the next run. See PHOTOS_PLAN.md and android/PHONE_TEST_PHOTOS.md. New-phone automatic photo downloads are not included; cloud photos remain available on the website.

## 1.2 Ready for acceptance: Health Connect export

TASKS.md 9.6 is implemented: explicit account-scoped connection, exercise/distance export, stable IDs, durable retries and deletion cleanup. See HEALTH_CONNECT_PLAN.md. Real-phone consent/display remains to test alongside the authorized issue milestones. No import or watch app.

## 1.3 Remaining feature refinements

- Authorized issue milestones: completed-run reopening; route-only graphics and city/state; removal of linked music transport; square emoji setup controls/status colors; announcements; default-on auto-pause; anomaly-aware coaching; private-by-default sharing, short links, photo visibility/unshare; route/photo weather overlays. See the issue plan for execution order, submilestones, and acceptance.
- Announcement settings use 5/10 active minutes or 0.5/1 selected distance unit (miles or kilometers), with on/off controls and continuation beyond goals. Milestone 6 implementation and emulator verification are complete; physical audio acceptance remains deferred.
- Desktop week/month/year/lifetime selections, sorting/display choices and charts: deferred; they do not precede photos.
- Coaching voice selection: deferred by the user; Cedar remains selected.
- Achievement performance-window refinement: initial PR/pacing awards require continuous reliable samples; broader paused/source-changing runs need a separately verified calculation extension.

## 1.4 Final verification and release preparation

Milestone 11 is locally complete; see MILESTONE_11_TEST_HANDOFF.md. The picker regression was corrected in test synchronization and passed repeatedly; the transient Compose wrong-thread exception did not recur in the full suite or six focused restoration runs. Consolidated Worker/Android/emulator/browser/visual gates passed and one timestamped APK remains. COMPREHENSIVE_TEST_PLAN.md reflects accepted behavior. Physical accuracy, GPS/steps, audio, recovery, battery, upgrade and combined deployed-service acceptance remain pending. Production identity/OAuth, release signing, the shell release variant, Cloudflare capacity/configuration and deployment remain separate release work.

## 1.5 What is already implemented

The checkpoint contains tracking/recovery, basic audio, legacy linked music controls and basemaps (scheduled for removal), Google login, account-owned sync/restore/deletion, desktop history, complete CSV export, per-user OpenAI keys, finish coaching/fallback/replay, achievements, photos/public pages, and Health Connect. See dated TASKS.md entries for evidence and limits. Existing default-public photo behavior is legacy behavior to replace, not the accepted target. End-to-end physical-phone acceptance remains separate.
