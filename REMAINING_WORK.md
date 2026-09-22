# 1 Remaining WAYiRUN work

## 1.1 Ready for phone acceptance: photos and achievements

TASKS 9.5 photos are implemented: camera/picker/skip, selectable overlays, preview/retake/keep, Android save/share, queued cloud storage, public individual-run pages and CSV photo records. Test with achievements on the next run. See PHOTOS_PLAN.md and android/PHONE_TEST_PHOTOS.md. New-phone automatic photo downloads are not included; cloud photos remain available on the website.

## 1.2 Ready for acceptance: Health Connect export

TASKS.md 9.6 is implemented: explicit account-scoped connection, exercise/distance export, stable IDs, durable retries and deletion cleanup. See HEALTH_CONNECT_PLAN.md. Real-phone consent/display remains to test. Next is COMPREHENSIVE_TEST_PLAN.md with findings in FINAL_PASS_ISSUES.md, then the agreed final stabilization pass. No import or watch app.

## 1.3 Remaining feature refinements

- Announcement settings: selectable 5/10-minute or 0.5/1-mile cadence and the requested on/off controls; these were explicitly deferred. Existing state/goal/full-split cues are already implemented. Resolve metric equivalents before implementing that selector.
- Desktop week/month/year/lifetime selections, sorting/display choices and charts: deferred; they do not precede photos.
- Coaching voice selection: deferred by the user; Cedar remains selected.
- Achievement performance-window refinement: initial PR/pacing awards require continuous reliable samples; broader paused/source-changing runs need a separately verified calculation extension.

## 1.4 Final verification and release preparation

TASKS.md 9.7. Finish actual-phone accuracy, GPS/step handoff, permission/offline/recovery and battery checks; music/headphone integration tests remain deferred, not unimplemented. Validate cross-device reconciliation and new feature acceptance. Finalize production application identity/OAuth, release signing, product release variant (currently a shell), Cloudflare capacity and production configuration, then separately authorize production deployment.

## 1.5 What is already implemented

Tracking/recovery, basic audio cues, music controls (device verification deferred), Google login, account-owned sync/restore/deletion, desktop history/maps, complete CSV export, per-user OpenAI keys, finish coaching/fallback/replay, and the initial achievement catalog/holiday calendar/finish celebration/history/export. See dated TASKS.md entries and ACHIEVEMENTS_PLAN.md 1.8 for evidence and limits. Photos and public run pages are now implemented; real camera/gallery and end-to-end phone acceptance remain.
