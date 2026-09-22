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
| — | — | — | No new manual findings recorded yet. | — |

## 1.3 Deferred enhancements and release work

These are known items, not newly approved implementation scope.

| ID | Item | Decision/status |
|---|---|---|
| ENH-01 | Announcement interval settings: 5/10 minutes, 0.5/1 mile and on/off controls | Deferred; resolve metric equivalents before implementation. |
| ENH-02 | Desktop week/month/year/lifetime stats, sorting/display options and charts | User deferred. |
| ENH-03 | Coaching voice selection | User deferred; Cedar retained. |
| ENH-04 | Broader achievement PR calculations for paused/source-changing runs | Separate calculation/verification work. |
| ENH-05 | Automatic photo restoration onto a new phone | Not implemented; cloud photos remain on website. Decide whether required. |
| REL-01 | Production identity/OAuth, release app variant/signing, privacy/Health Connect declarations, capacity/config and production verification | Required release planning; no production authorization yet. |

## 1.4 Fix acceptance

For each accepted fix, record change, automated checks, rebuilt APK, manual retest and disposition. Preserve working behavior and keep each correction bounded. Final feature selection follows comprehensive testing and the owner's prioritization.
