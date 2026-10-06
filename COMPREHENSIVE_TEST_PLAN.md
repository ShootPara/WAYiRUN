# 1 WAYiRUN comprehensive acceptance plan

## 1.1 Purpose and recording results

Use this development build to collect bugs, usability quibbles and enhancement requests before the final stabilization pass. These checks are not yet claimed as passed. Record each result in FINAL_PASS_ISSUES.md with test ID, build, phone/Android version, steps, expected/actual behavior and a screenshot when useful. Never include API keys or authentication tokens. Requirements sections below provide traceability.

Upgrade in place; do not uninstall or clear storage on the real phone. Export all runs first and keep the CSV. Use disposable runs for deletion, interrupted saves and recovery tests. Leave valuable runs and other apps' health records alone. Do not change the real phone clock to test holidays. Agent fixtures cover synthetic calendar cases.

## 1.2 Suggested sessions

1. Desk setup: upgrade, account/settings, Health Connect consent, website and existing history (sections 2.1, 2.5, 2.6).
2. Short disposable indoor runs: pause/resume, coaching, photos, Health Connect retries/deletion (sections 2.2–2.5). One paid coaching example is sufficient initially; use opt-out/fallback for repeated checks.
3. Normal outdoor run: GPS/steps, screen off, headphones/music, goals and finish (sections 2.2–2.3). Stop safely before operating controls; no need to provoke failures while running.
4. Controlled recovery and second account/device checks (section 2.7), then rerun failed cases after corrections.

After Milestone 11 verification and the combined rollout, start with SET-01, REC-04, AU-02, RUN-08 and PH-03. Then use normal runs for audio, sensors and coaching. The remaining checklist can span several sessions.

## 1.3 Practical schedule for the owner

Take as many days as needed. Use the verified timestamped handoff build throughout so results remain comparable; record its version from settings. Keep the current phone build until that handoff is ready. You can report results together.

| Session | Actions | Record |
|---|---|---|
| Day 1: setup, about 15 minutes | Export a backup CSV, upgrade in place, confirm history/login/photos remain, connect Health Connect and inspect one older run. Retry export once and check for duplicates. | SET-01, HC-01, HC-03; phone model and Android version. |
| Day 2: short indoor test | Make a disposable run: move, pause, resume, then finish with coaching checked. Compare spoken recap with the website. Keep a test photo and confirm it stays private until a share action. Inspect the Health Connect entry, then delete this disposable run and check cleanup. | RUN-01, CO-01, PH-02–03, HC-02, HC-04. Note active versus elapsed time. |
| Day 3: your normal outdoor run | Use your usual music/headphones. Check a pause/resume, screen-off tracking, distance/pace plausibility, finish, coaching and photo. Review the saved map and sync afterward. | RUN-02, RUN-06, AU-01–02, PH-01; any practical annoyance, even if it technically works. |
| Day 4 or later: desk checks, about 20 minutes | Browse website on phone/desktop, change units, replay recap, export CSV, try a short offline run with coaching unchecked, reconnect and check sync. Exercise cancellation and partial finish swipe on a disposable run. | WEB-01–03, CO-02, RUN-05, REC-02. |

The remaining detailed cases below are a coverage backlog. Permission revocation, reboot/process termination, second-account/device isolation and synthetic calendar/provider cases can be scheduled separately with Codex. Mark unavailable hardware or an untested case “not tested”; it is not a failure or a pass. Do not create extra accounts, buy equipment or perform advanced recovery experiments merely to complete this first round.

## 1.4 Low-effort result report

Send plain notes or screenshots whenever convenient. Test IDs are optional; Codex can map your observations into the ledger. One report can cover several days. A useful template is:

```text
Build: timestamp shown in settings
Phone / Android:
What I tried:
What worked:
What happened unexpectedly (and what I expected):
Approximately when / which run:
Things that felt awkward:
Ideas for later:
Not tested yet:
```

For an intermittent problem, record whether it happened once or repeatedly and whether music, screen lock or offline mode was involved. No need to rerun an expensive coaching call just to prove it twice. If you encounter lost runs or the wrong account's private data, stop that particular test and report it before repeating it.

# 2 Test cases

## 2.1 Upgrade, settings and usability — requirements 3–4, 13

| ID | Check and expected result |
|---|---|
| SET-01 | Upgrade over installed app. Saved runs, photos, signed-in account and key status remain; no repeated login required solely by upgrade. |
| SET-02 | Change miles/km, stride and each supported goal. Valid choices persist across reopen; blank, zero, negative and malformed entries have clear handling. Confirm settings cannot corrupt an active run. |
| SET-03 | Check countdown, indicators, back navigation, keyboard, portrait/landscape, large text, light/dark themes and narrow screens. Controls remain readable and reachable without clipping. |
| SET-04 | Deny optional permissions, then enable them through settings. Explain missing capabilities; tracking remains usable with available sources, without repeated prompts or crashes. |
| AC-01 | Sign out/in, reopen after a day, and refresh website. Correct profile and owned history appear. Expired sessions give a recoverable sign-in flow; no silent loss. |
| AC-02 | Use two test accounts. No other account's private runs, photos, recap, API key status or queued uploads leak across the switch. Explicit local-run import remains intentional. |

## 2.2 Tracking and run controls — requirements 5–6

| ID | Check and expected result |
|---|---|
| RUN-01 | Short indoor run: steps/stride drive distance; GPS is not required. Pause freezes active time and distance; resume continues once. |
| RUN-02 | Outdoor run on a known route: compare time/distance/pace/splits with observed route and an independent reference. Record numerical discrepancies and conditions; agree accuracy thresholds before release rather than declaring all differences bugs. |
| RUN-03 | Start without GPS/internet, acquire GPS later and lose it again in a controlled setting. Source transitions do not double count, jump backwards or connect map gaps with invented tracking. |
| RUN-04 | Time and distance goals each trigger once; continuing beyond goal works. Unit conversion does not change underlying totals or trigger duplicate milestones. |
| RUN-05 | Tap/partially swipe finish, then fully swipe. Only full confirmation finishes. Cancel discard preserves data; confirmed discard removes only the intended disposable run. |
| RUN-06 | Screen off/background for a meaningful portion of a run. Resume UI and notification show consistent state, active time, distance and splits. Record battery percentage and elapsed time. |
| RUN-07 | No usable sensor/source, denied location/steps, or source recovery: clear status, no invented distance, no crash, and valid available metrics continue. |
| RUN-08 | Auto-pause defaults on. With reliable motion evidence, stop about five seconds and move about two seconds: automatic pause/resume, distinct cues and frozen paused accounting. Manual pause never auto-resumes. Turn the setting off for a separate run and confirm it stays off; changing settings does not alter an active run. |

## 2.3 Audio, music, coaching and achievements — requirements 7–9

| ID | Check and expected result |
|---|---|
| AU-01 | Speaker and available headphones with YouTube Music: state/goal/completion cues play once; music ducks and recovers. Select each announcement interval across suitable runs: 5/10 active minutes or 0.5/1 selected distance unit, continuing beyond the goal. Announcement off preserves state/goal/completion cues. |
| AU-02 | Open playlist, then choose playback in YouTube Music. App start/pause/resume/finish never sends music transport commands. Music/headphone pause/resume never changes run state, including during cues. No notification-listener access request or music-control setup remains. |
| AU-03 | Notification controls, rapid repeated commands, phone call, Bluetooth disconnect/reconnect: state stays consistent; no duplicate run/service or stuck audio focus. |
| CO-01 | One new run with coaching checked and working key/network: spoken coaching matches this run's saved recap. Completion cue precedes it. Dismissing animation does not cut off speech. Website replay matches too. |
| CO-02 | Coaching unchecked: no AI generation or encouragement; normal completion cue still works. Offline/missing-key checked run gets onboard encouragement with a clear outcome. Reopening history does not generate/bill again. |
| CO-03 | Key add/replace/remove and account switch: masked input, correct account status and recoverable validation errors. No credentials appear in UI diagnostics/export. Do not repeat paid calls just to exercise screens. |
| AW-01 | Finish with eligible award(s): celebration can browse/dismiss without stopping coaching. Phone/history/CSV agree; deleting a qualifying disposable run recomputes affected awards. |
| AW-02 | Agent fixtures verify distance thresholds, calendar week/streak boundaries and named holidays. Record any missing expected award with date/zone/run evidence. Do not manipulate phone time or expect broader paused-run PR calculations currently deferred. |

## 2.4 Photos and public pages — requirements 10–11

| ID | Check and expected result |
|---|---|
| PH-01 | Rear/front camera, image picker, cancel and Skip. Saved run survives all choices; photo orientation is correct for portrait/landscape. |
| PH-02 | Toggle overlays, preview, retake, rotate screen and Keep. Chosen time/distance/pace/actual route match run and units; no route is invented for indoor/no-GPS runs. |
| PH-03 | New run/photo is private. Keep and Save image preserve privacy. Any Share/Copy link or image-share attempt records public intent, even if the chooser is cancelled. Offline intent queues; a confirmed short link is offered only after publication succeeds. |
| PH-04 | Share a disposable run with and without a photo. Logged-out short link shows only that run. Hide photo: stats/splits/route remain, photo and weather metadata disappear. Unshare: short link, public page/data/image become inaccessible. Reshare follows the stable-link contract. Deletion revokes access. |
| PH-05 | Keep offline, reconnect and refresh website: upload recovers without duplicates or retaking the photo. Gear settings distinguish synced runs from photos waiting to upload; a failed photo has a safe explanation and cannot block other eligible photos. Retry sync clears the pending/error state after a verified receipt. Deletion during pending upload cannot resurrect the photo. Website retains cloud photo access; automatic photo download on a new phone is currently not implemented. |
| PH-06 | Outdoor synced run: optional weather shows emoji and F/C from the run start. Toggle off/on, recreate editor and Keep: preview, JPEG and saved metadata agree. Route has no dark rectangle and preserves gaps. Indoor/no GPS/offline/unavailable weather never blocks Keep. Old kept photos remain unchanged. Credit appears in settings, weather-bearing JPEG and public page. |

## 2.5 Health Connect — requirements 11.6, 12

| ID | Check and expected result |
|---|---|
| HC-01 | Gear → Health Connect → Connect / retry export. Request only exercise and distance write access. Deny or grant only one: no export/crash; clear permission status. Grant both: retained completed runs export. |
| HC-02 | Finish one short indoor run with a deliberate pause. Inspect Health Connect: running/treadmill session, correct distance/start/end and pause information; original active seconds also appear in notes where supported. No GPS route/photo/coaching/heart rate import or export. |
| HC-03 | Tap retry, reopen app and wait for background processing. Same runs remain single records rather than duplicates. Finish offline with permissions already granted: local Health Connect export does not require internet. |
| HC-04 | Delete a disposable exported run in WAYiRUN. Its session and distance disappear from Health Connect; unrelated entries remain. Repeat with a website deletion after phone reconciliation. |
| HC-05 | Revoke write permissions, then delete a disposable exported run. Cleanup stays pending; reconnect/grant both and confirm cleanup completes. Source run remains deleted throughout. |
| HC-06 | Switch test accounts. New exports wait for explicitly connecting that account; old-account cleanup may still complete. Runs are not exported under an unrelated account's connection. |
| HC-07 | On a separate test device/emulator, exercise missing/outdated provider and manual deletion of WAYiRUN's Health Connect copy. Unsupported state does not break tracking; Connect / retry restores eligible copies using stable IDs. |
| HC-08 | Agent fixtures: interrupted partial insert, delete during export, denied cleanup, Room migration and invalid zero/backwards timing. Bad timestamps stay safely unexported with visible explanation; source archive is unchanged. |

## 2.6 Website, archive and deletion — requirements 3, 11, 13

| ID | Check and expected result |
|---|---|
| WEB-01 | History/detail/achievements at desktop and phone width: correct counts, units, loading/empty/error states and accessible buttons. Clearly distinguish loaded-history totals from full-history awards. |
| WEB-02 | Route with pauses/gaps: no basemap or tile requests, no invented connecting path, start/finish markers and readable details. Public location shows city/state or a generic unavailable message, never coordinate text. |
| WEB-03 | Export all runs: one CSV includes summaries and retained detailed records, GPS/splits/settings, coaching/audio, achievements and photos. Agent reconstruction validates embedded media hashes and safe spreadsheet cells. Cancel/failure never offers a misleading partial success. |
| WEB-04 | Select/delete disposable runs, cancel confirmation, retry network failure and refresh. Deleted runs/photos/public pages/coaching stay deleted across phone/web; unrelated records remain. |
| WEB-05 | Logout/back navigation/second account: private content and in-progress requests cannot reappear from stale browser state. Network/rate-limit/auth failures give actionable messages without losing runs. |

## 2.7 Recovery and release gates — requirements 13–16

| ID | Check and expected result |
|---|---|
| REC-01 | Disposable active/paused run: controlled process termination or reboot. Reopen and verify explicit recovery, preserved checkpoints and no silent duplicate run. Record exact lost interval, if any. |
| REC-02 | Finish/save/upload around connectivity loss. Reconnect, restart and reconcile another test device: one complete owned run; pending work resumes, deletion wins over stale upload. |
| REC-03 | Interrupt photo/coaching/export operations and rotate/back out. No crash, stale playback or cross-run result; settled billing outcomes are not blindly retried. |
| REC-04 | Finish, open photo editor and cancel camera/picker/save/share: current workflow survives return and rotation. Ordinary warm/cold reopen shows New Run; completed data remains in history. Unfinished recovery still requires explicit resume. |
| REL-01 | Complete a longer realistic outdoor and indoor session after fixes, covering screen-off battery, GPS/steps, headphones, finish/photo and cloud/Health Connect reconciliation. |
| REL-02 | Production identity/OAuth, functional release variant, signing/recovery, privacy policy and Health Connect declarations, capacity/configuration and release checks require their own bounded release work. Production deployment requires separate authorization. |

# 3 Final pass decisions

## 3.1 Triage

P0: data loss, credential exposure or account isolation failure. Stop affected path and investigate. P1: core tracking/login/save/export or completion broken. P2: incorrect secondary behavior or serious usability issue. P3: cosmetic quibble. Enhancements are separately categorized, not automatically bugs or approved scope.

## 3.2 Exit criteria

No unresolved P0/P1 issues; rerun each fixed case plus relevant regressions. Review P2/P3 and enhancement scope with the owner before the last implementation pass. Record real-device results separately from automated evidence. Resolve explicitly deferred requirements or agree a release scope before production; a successful APK build alone is not acceptance.

## 3.3 Current evidence

Milestone 14 combined-build automated gate passed: 118 JVM tests, build/lint, 26 targeted emulator cases and light/dark normal/200-percent settings visuals. Physical acceptance remains: at a five-minute goal with five-minute milestones, hear only the goal recap, then a normal ten-minute milestone if continuing. Repeat a matching one-mile and one-kilometer goal/distance milestone. With both channels enabled and no goal collision, simultaneous thresholds produce one recap. Check adjacent timer/distance callbacks, carried indoor motion, screen-off playback, pause/resume and finish with coaching. Milestone settings remain in the gear menu.

September 28 physical feedback supersedes synthetic auto-pause confidence: BUG-006 reproduces false indoor pauses while running. After Milestone 12, record phone model/Android version and placement. Carry the phone normally for several minutes, including the initial ten seconds and manual pause/resume; no false pause is acceptable. Stop and resume repeatedly, confirming cues and frozen paused time/distance. Repeat screen-off and with auto-pause disabled. A phone resting on treadmill equipment is not evidence of the runner's motion. Time announcements already worked in the owner's phone test; preserve that regression. Do not close BUG-006 from emulator results alone.

Through Milestone 11: 146 Worker tests, 95 JVM tests, Android build/lint, all 105 distinct emulator cases across the explicit Health Connect permission split, local browser checks and visual gates passed. The picker-return failure was corrected as test synchronization and passed repeatedly; the transient Compose exception did not recur in the complete suite or six focused editor runs. Artifact and evidence are recorded in TASKS.md and MILESTONE_11_TEST_HANDOFF.md. Physical sensor/audio/camera, real provider, upgrade and combined deployed-service acceptance remain pending. Automated synthetic results do not substitute for those checks.
