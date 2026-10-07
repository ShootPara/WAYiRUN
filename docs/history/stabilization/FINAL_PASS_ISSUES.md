# 1 Final pass issue ledger

## 1.1 Test session record

Record date, APK version, phone/Android version, website version if relevant, test IDs and pass/fail/not-run. All new comprehensive manual cases initially remain not run. the owner previously confirmed login repair, API-key setup and photos working; the specific coaching playback correction still benefits from CO-01 acceptance.

| Date/build/device | Test IDs | Result | Notes |
|---|---|---|---|
| Pending: setup | SET-01, HC-01, HC-03 | Not run | Day 1; history preserved and no duplicate health records. |
| Pending: indoor | RUN-01, CO-01, PH-02–03, HC-02, HC-04 | Not run | Day 2; disposable run only for deletion. |
| Pending: outdoor | RUN-02, RUN-06, AU-01–02, PH-01 | Not run | Day 3; normal run and practical usability. |
| Pending: desk/offline | WEB-01–03, CO-02, RUN-05, REC-02 | Not run | Day 4 or later. |
| Pending: remaining coverage | Other comprehensive cases | Not run | Schedule advanced/device-specific checks separately. |

## 1.2 Bugs and usability findings

Use BUG-001 onward. Include reproducible steps, expected/actual result, frequency, impact, evidence and relevant test ID. Exclude private credentials. Do not mark an issue fixed until its acceptance case is rerun.

| ID | Test | Type/priority | Expected vs actual / reproduction | Status |
|---|---|---|---|---|
| BUG-001 | Issue-plan M2 | Launch/focus | Completed run reopens on launch/focus; expected New Run except active recovery or current finish/photo return. | Implemented; emulator regression passed; physical-device acceptance pending |
| BUG-002 | Issue-plan M3 | Route display | Basemap behavior rejected; expected stored route graphics with gaps and city/state, without tiles. | Implemented; Worker and desktop/mobile checks passed; not deployed |
| BUG-003 | Issue-plan M9 | Coaching quality | Test/anomalous recordings receive ordinary performance feedback; expected deterministic quality context and appropriate wording. | Complete: deterministic classifier/prompt integration passed compile, dry-run and all 118 Worker tests; real generated tone remains acceptance |
| BUG-004 | REC-04 / RunEntryTest | P2, picker return | Missing photo editor after cancelling picker in M5.1, including isolated run. Expected current finish/photo workflow retained. | Resolved in M11 test harness: wait for actual picker window before Back; class and repeated isolated lifecycle runs passed; production unchanged |
| BUG-005 | PH-02 / PhotoEditorTest | P2, intermittent instrumentation exception | Dark 200-percent restoration once threw CalledFromWrongThreadException during dialog recomposition; subsequent runs passed. | Closed as non-reproduced after full suite plus six focused light/dark normal/200% restoration runs; reopen if device acceptance reproduces it |
| BUG-006 | M12-M14 / indoor auto-pause | P1, physical tracking | the owner reports automatic pause after roughly ten seconds while running, then another about five seconds after manual resume. Registered step-counter silence was incorrectly treated as stillness. | Combined build passes 118 JVM tests, build/lint and 26 targeted emulator cases; OPEN until carried-phone indoor and screen-off acceptance pass |
| BUG-007 | PH-01–03 / photo sync | P1, app/backend compatibility | New app always includes weather flag; old live Worker rejects upload with 400. Silent single-photo retry can block later images. | Matching backend and migrations deployed; authenticated live canary passes; two additional current-format photos observed. Android queue/status hardening and verification in PHOTO_SYNC_REPAIR.md; the owner confirmed website photos visible on September 28; reported regression resolved |

## 1.3 Deferred enhancements and release work

The September 22 issue plan authorizes the items marked accepted below. Other deferred/release items remain outside this implementation scope.

| ID | Item | Decision/status |
|---|---|---|
| ENH-01 | Announcement interval settings: 5/10 active minutes, 0.5/1 selected distance unit and on/off | M6 complete: continues past goal; 78 JVM tests, Android build/lint, focused emulator coverage and light/dark/200-percent visuals passed. Physical audio acceptance remains. |
| ENH-06 | Keep Open playlist and cue ducking only | M7 complete: transport/listener/access UI removed; 68 JVM and 22 focused emulator tests passed. Real-player ducking/independence remains phone acceptance. |
| ENH-07 | Auto-pause after 5 seconds stopped, resume after 2 seconds movement | Complete in M8.0/M8.1: policy, accounting, service/settings/cues, 95 JVM tests, 26 focused emulator tests and light/dark/200-percent visuals passed. Physical movement and screen-off acceptance remain. |
| ENH-08 | Square emoji setup controls; left run type/right connectivity colors | M10 complete: 68 JVM and 17 focused emulator tests plus normal/200% visual checks passed; phone acceptance deferred. |
| ENH-09 | Private default, any share attempt publishes, short links, unshare, independent photo display | Complete in M4.0/M4.1: 125 Worker tests/browser checks, 95 JVM tests, 49 focused emulator cases and light/dark/200-percent visuals passed. Phone acceptance deferred. |
| ENH-10 | Lower-right route overlay without dark box; optional weather emoji/F/C | Complete in M5.0/M5.1: 146 Worker and 95 JVM tests, Android build/lint, new emulator cases, dark 200% visuals and mobile/desktop browser checks passed; no retroactive photo changes. Phone acceptance deferred. |
| ENH-02 | Desktop week/month/year/lifetime stats, sorting/display options and charts | User deferred. |
| ENH-03 | Coaching voice selection | User deferred; Cedar retained. |
| ENH-04 | Broader achievement PR calculations for paused/source-changing runs | Separate calculation/verification work. |
| ENH-05 | Automatic photo restoration onto a new phone | Not implemented; cloud photos remain on website. Decide whether required. |
| REL-01 | Production identity/OAuth, release app variant/signing, privacy/Health Connect declarations, capacity/config and production verification | Required release planning; no production authorization yet. |

## 1.4 Fix acceptance

For each accepted fix, record change, automated checks, rebuilt APK when applicable, manual retest and disposition. Preserve working behavior and keep each correction bounded. Issue-plan M1 document alignment is complete; no runtime issue is marked fixed by that documentation change. Existing device-test rows remain not run.

BUG-001 verification: issue-plan M2 passed 78 JVM tests, debug build/lint, and 15 distinct focused emulator cases (see TASKS.md 1.2 for the two test batches). Actual picker cancel/return preserves the photo dialog; cold/warm reopen clears the summary without deleting its run. Camera/share-target returns and external-activity process death still need device acceptance. Test APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-22_09-46-00_EDT.apk`. No personal phone was installed or tested.

BUG-002 verification: issue-plan M3 passed the 107-test Worker suite followed by 24 focused tests including one additional public-coordinate regression. Desktop/mobile private/public screenshots and browser assertions passed with zero external requests. Raw geographic coordinates no longer appear in public data; city/state is cached separately with small-print attribution. Provider calls were stubbed; real deployment/provider checks remain for a separately authorized rollout. No APK is handed off for this milestone.

ENH-06 verification: issue-plan M7 removed automatic media transport, notification-listener registration and music-access setup while retaining playlist opening and cue ducking. The Android build/JVM/lint gate passed with 68 JVM tests, zero lint errors and 19 warnings. All 22 focused emulator tests passed, including listener absence, player/run independence, settings and playlist regressions. Real YouTube Music/headphone behavior and audible focus ducking remain physical-device acceptance. No phone install or APK handoff.

ENH-08 verification: issue-plan M10 added square emoji setup selectors and separate left/right run-type and connectivity labels with requested colors. The Android build/JVM/lint gate passed with 68 JVM tests, zero lint errors and 19 warnings. Seventeen focused emulator cases passed across the new selectors and adjacent setup/run flows. Normal and 200-percent text screenshots were visually inspected; an initially cramped large-text layout was corrected and reverified. Physical-phone visual acceptance remains deferred. No phone install or APK handoff.
