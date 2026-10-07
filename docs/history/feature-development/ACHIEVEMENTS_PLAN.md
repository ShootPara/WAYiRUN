# 1 WAYiRUN achievements and celebrations

## 1.1 Authority and current scope

September 18, 2026: user authorized researching Nike Run Club, adding major-holiday achievements and giving achievements original names. Reviewed the full relevant history in Running App First Convo (6aa08df7-de0c-83ea-9468-a00d5e2b6162), REQUIREMENTS.md Sections 6, 9–11 and TASKS.md 9.4–9.5. The original conversation specifies immediate earning, finish-time reveal/animation, holiday-date awards and retained history. It does not specify a complete catalog, numerical thresholds or deletion/calendar policy.

The user accepted this catalog and authorized implementation on September 18. Sections 1.3–1.5 are the accepted design baseline; Section 1.8 records implementation and conservative measurement limits. U.S. holidays are the initial calendar. No social challenges, leaderboard, weather service, new AI calls or voice selection are included.

## 1.2 Nike reference research

Checked September 18, 2026:

- Nike's app page confirms personal records, mileage accomplishments and run-day streaks: https://www.nike.com/nrc-app
- Nike Help confirms completing challenges awards trophies: https://www.nike.com/help/a/nrc-challenges . WAYiRUN does not need Nike's challenge/social infrastructure to award personal milestones.
- Nike's lifetime-distance levels are Yellow, Orange, Green, Blue, Purple, Black and Volt; published kilometer boundaries are 0, 50, 250, 1,000, 2,500, 5,000 and approximately 15,000: https://www.nike.com/ca/help/a/nrc-run-level . Use original WAYiRUN names and artwork.
- Apple's NRC editorial describes longest-run, fastest-mile and fastest-5K records: https://apps.apple.com/gb/story/id1444931010
- Community lists report fastest-distance records, weekly-frequency badges, monthly-distance badges, Sunday-distance trophies, holiday trophies and anniversary/birthday awards. These are historical/community evidence, not a verified complete current Nike catalog: https://www.reddit.com/r/nikerunclub/comments/cuav1x and https://www.reddit.com/r/nikerunclub/comments/1c54gcy/updated_list_of_trophies/
- Reported holiday names include Resolution, True Runmance, Run Fourth, Ghost Runner and Turkey Trot. Availability and required guided runs have varied by region/year: https://www.reddit.com/r/nikerunclub/comments/1c9wsrw . WAYiRUN's own published rules must be stable and testable.

## 1.3 Core catalog

Distances use exact meter thresholds internally; changing displayed miles/km never creates another award. One mile is 1,609.344 meters. First-distance and lifetime tiers are earned once per account; all reached tiers count, including when a single run crosses several. Indoor and outdoor runs count. Time-based goals do not require a distance goal to earn awards.

| Family | Award names | Proposed trigger |
| --- | --- | --- |
| First run | First Footprint | First completed run with positive recorded distance and active time |
| First distances | Kicking It Off; Mile Marker; Five Alive; Double Digits; Going the Distance; Halfway to Legendary; The Long Game; Marathon Mindset; Beyond the Finish | First single run reaching 1 km; 1 mile; 5 km; 10 km; 10 miles; half marathon (21,097.5 m); 20 miles; marathon (42,195 m); 50 km, respectively |
| Lifetime distance | Finding Your Stride; Making Tracks; Road Regular; Horizon Hunter; Distance Devourer; Long Haul Legend; Beyond the Horizon | 50; 100; 250; 500; 1,000; 2,500; 5,000 cumulative km, respectively |
| Run count | Showing Up; Regular Fixture; Triple-Digit Club; A Thousand Starts | 10; 50; 100; 1,000 completed qualifying runs |
| Weekly consistency | Three's a Stride; High Five; Full House | Run on 3; 5; 7 distinct local dates in one Monday–Sunday week; repeat each week |
| Week-to-week consistency | Finding a Rhythm; On a Roll; Seasoned Strider; Year in Motion | At least one qualifying run per week for 4; 8; 13; 52 consecutive weeks; each tier once per uninterrupted streak |
| Monthly distance | Making Headway; Month in Motion; Triple-Digit Month; Going Places | 25; 50; 100; 200 km in one calendar month; repeat each month |
| Sunday distance | Sunday Strollout; Sunday Fiver; Sunday Long Game | A single Sunday run reaches 1; 5; 10 km; each tier once per Sunday |
| Personal records | Raising the Bar | Strictly longer than the prior longest completed run; repeat for each new record |
| Personal records | Personal Best: 1K / Mile / 5K / 10K / Half / Marathon | Strictly faster recorded effort at that exact distance than the previous valid best; first effort establishes a baseline, first-distance badge celebrates it |
| Pacing | Strong Finish | A run of at least 2 km whose second distance half takes at least 2% less active time than its first; repeat per run |
| Pacing | Building Steam | At least three complete consecutive 1 km splits, each at least 2% faster than its predecessor; repeat per run |
| Personal anniversary | Another Lap Around the Sun | Qualifying run on the anniversary of the first retained completed run, from year two onward; repeat annually |

These are achievement criteria, not the deferred week/month/year statistics dashboard. No calorie, heart-rate, elevation or weather achievements are inferred from data the app does not collect. Birthday awards are deferred because the profile does not collect a birth date.

## 1.4 Holiday catalog

One award per holiday per year, automatically earned by recorded running on that holiday; no arbitrary 5K requirement. Require positive distance and active time, so opening the app or a zero-distance test does not earn an award. Indoor runs count. Actual dates are used, not substitute federal-office observance dates.

| Holiday/event | Original WAYiRUN award | Calendar rule |
| --- | --- | --- |
| New Year's Day | Fresh Tracks | January 1 |
| Martin Luther King Jr. Day | Strides Toward the Dream | Third Monday in January |
| Valentine's Day | Heart & Sole | February 14 |
| Presidents' Day | Executive Pace | Third Monday in February |
| St. Patrick's Day | Shamrock & Roll | March 17 |
| Easter | Spring in Your Step | Western Gregorian Easter Sunday |
| Earth Day | Leave Only Footprints | April 22 |
| Mother's Day | Miles of Appreciation | Second Sunday in May |
| Memorial Day | Miles of Remembrance | Last Monday in May |
| Global Running Day | One World, Many Strides | First Wednesday in June |
| Juneteenth | Freedom in Motion | June 19 |
| Father's Day | A Run for the Long Haul | Third Sunday in June |
| Independence Day | Stars, Stripes & Strides | July 4 |
| Labor Day | Putting in the Legwork | First Monday in September |
| Indigenous Peoples' Day | Honoring the Path | Second Monday in October |
| Halloween | Sole Survivor | October 31 |
| Veterans Day | Strides of Honor | November 11 |
| Thanksgiving | Gravy Train | Fourth Thursday in November |
| Christmas Eve | Silent Night Strider | December 24 |
| Christmas Day | Sleigh the Miles | December 25 |
| New Year's Eve | The Final Lap | December 31 |

Keep holiday names visible alongside creative award names so the meaning is clear. Respectful iconography for commemorative days; original seasonal graphics for playful holidays. Multiple holidays on one day can award independently. More regional/religious calendars can be added as data later; this list is not a worldwide holiday claim.

## 1.5 Calendar, persistence and record guardrails

Accepted deterministic rules for the implementation contract:

- Use the run's recorded IANA time zone, never the server's current zone. Keep the zone frozen for that run. Date changes caused by later device settings must not rewrite history.
- Calendar awards follow dates with actual recorded movement, excluding paused time. A midnight-crossing run can qualify on both dates only if movement is recorded on both. Where old archives cannot establish the date of movement reliably, do not infer it from paused wall-clock span; document the conservative historical rule before implementing backfill.
- Compute progress locally without internet; persist earned events with the run before display. At finish reveal the new awards in one dismissible celebration with a count and browsable cards. Do not queue dozens of blocking animations, speak extra announcements, or stop coaching audio when dismissed. AI opt-out does not disable achievements.
- Discarding/deleting a run removes its attributable achievement events. Recompute records, cumulative totals and streaks from retained runs; never leave deleted metrics or GPS in achievement evidence. This policy must be explained in deletion copy. Reconciliation must not resurrect a deleted run or replay celebrations.
- Award keys use stable definition IDs/version, owner ID and occurrence (lifetime, period, streak or run as appropriate); repeated saves/sync/downloads must not duplicate awards. Account switching clears all private cached award UI.
- Historical completed runs count on initial backfill, without replaying old celebrations. Evaluate them in stable completion-time/run-ID order. Label incomplete local history honestly until cloud reconciliation finishes; server results must not silently fabricate evidence unavailable offline.
- Fixed-distance PRs must come from measured distance/time progression, not whole-run average pace or GPS straight-line shortcuts. Preserve pause/source/clock boundaries; interpolate only within valid recorded intervals. Indoor efforts use step-based measurements. Keep calculation semantics explicit and testable before exposing PRs.
- Negative-split awards need complete valid distance/time coverage and a resolved active-time boundary. Missing data yields no performance-pattern award, not an invented result. No pressure-driven daily streak notifications or fitness advice is added.
- Export retained award definitions/version and earned occurrences alongside run data; keep credentials and private internal account metadata out. Award badges are private until the user chooses a public run/photo flow.

## 1.6 Bounded execution and verification

1. Implement the pure Kotlin catalog/calendar/evaluator in com.example.runningapp.domain, with stable IDs and deterministic fixtures. Start with first-distance, lifetime, count, calendar and weekly/monthly awards; define PR measurement semantics separately before enabling them. No Android dependency in calculation code.
2. Add durable owner-scoped events and reconciliation with deletion/backfill tests. Verify two accounts, offline finish, midnight/DST, leap years/Easter, intersecting holidays, unit switching, duplicate upload, same-time runs and missing history. Do not require paid AI generation.
3. Add finish-time celebration and desktop achievement history/export. Verify multiple awards, AI unchecked, animation dismissal with audio continuing, recreation/restart, cancelled run and a deleted award source. Run Android build/lint/JVM tests and focused emulator checks; report phone acceptance separately.
4. Complete this bounded achievement milestone, then implement end-of-run photos. Do not slip voice selection or expanded statistics/chart controls ahead of photos.

## 1.7 End-of-run photos remain next

The original user flow is finish/coaching and celebration → summary → photo button at the top. Photo choices are Take Photo / Choose Existing Photo / Skip; front/rear camera; selectable time/distance/pace and optional actual route overlay; preview with Retake / Keep Photo; Android save/share; private cloud image accessible from the website. At Keep Photo, Make this run public defaults checked and publishes that individual run only when checked. Preserve this explicit decision and keep the checkbox visible.

Photos are a core original feature, not optional backlog polish. TASKS.md 9.5 is the next feature milestone after achievements. Photo implementation must settle only genuinely outstanding details such as offline upload/publication and replacing an existing image; do not re-ask the already settled camera/overlay/share questions. No photo or publication implementation is claimed by this document.

## 1.8 Implemented contract and evidence

Implemented the catalog in pure Kotlin and browser JavaScript with stable definition/occurrence/run keys. A shared 370-run fixture verifies identical 392 award identities, names and dates. Room migration 3→4 adds a derived account-owned achievement cache; measurement/finish transactions persist earned progress. Completed archives remain version 1. Restoring/importing/deleting runs recomputes awards from retained same-owner history. Explicit import also clears/rebuilds the former local-owner cache. Existing historical runs count without replaying old animations.

The cloud's existing immutable run archives are the durable award evidence; the desktop calculates the version-1 projection from all verified archives, not just loaded history rows. No new cloud achievement mutation API or duplicate event upload is introduced. This makes backfill and deletion deterministic on another device. The phone computes from its currently restored local history and labels that scope in the celebration; it converges after restore. Future rule revisions must preserve the version-1 evaluator or define an explicit migration, not silently reinterpret existing version labels.

Date allocation uses each positive measurement's active-time position inside its recorded active interval, translated through the run's frozen time zone. An increment belongs to its recorded endpoint's local day; unknown movement between samples is not fabricated. Historical records with no measurements qualify for calendar awards only if start and finish share one local date. Midnight-spanning records lacking that evidence receive no guessed calendar awards. February 29 anniversaries occur in leap years only.

Performance awards use piecewise-linear measured distance/active-time curves and search candidate boundary offsets for the fastest exact-distance effort. The initial conservative eligibility rule requires one source segment, one active interval, strictly increasing positive measurements and no positive-sample gap over ten active seconds. Paused, source-switched, incomplete or sparse runs still earn distance/holiday/consistency awards, but do not establish performance records. This is a deliberate evidence limit; expanding valid performance windows across pauses or source changes is a future refinement. PR/pacing results are evaluated at finish; distance/calendar/count progress persists during the run. No new in-run announcements are added.

Finish displays a pulsing star and browsable named award cards. Tapping the celebration or Continue to summary dismisses the visual without cancelling service-owned coaching audio; AI unchecked still shows earned awards. Reopening a finished run after process loss does not replay celebrations. Desktop Achievements explicitly reads all synced runs, handles cancellation/sign-out and has a 128 MiB input bound. CSV version 3 adds ACHIEVEMENT records containing rule version, definition ID, occurrence, source run, name, criterion text and date; existing run/coaching/audio records remain intact. Local deletion text explains recalculation.

Verification: 69 JVM tests, 93 web/backend tests, Android assembly/lint and 16 focused emulator tests pass (storage, migrations, run-screen gestures/celebration and actual fallback audio playback). The first UI test pass encountered a sleeping activity; the test activity now requests visible/awake state, and all six UI/audio checks passed on rerun. Shared parity, holiday movable dates/overlap, distinct-day weeks, month boundaries, streak breaks, zero-distance exclusion, duplicate IDs, deletion, exact threshold and PR ties are covered. Local browser verified all-page history, three synthetic awards, matching downloaded CSV records and 390px cards without page overflow. No real phone installed or personal OpenAI call made.

Final edge-case correction preserves distance/count awards when an old midnight-spanning run has no reliable calendar evidence. Both evaluators have a regression check. The final targeted browser/export suite passes all 21 tests after the earlier full 93-test suite; final Android gates pass all 69 JVM tests, assembly and lint. Historical calendar uncertainty does not discard verified distance.

Development website version: e192834c-8396-41b4-82d8-e77273d2d972; 25 live smoke checks pass. No cloud migration. Phone APK: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-18_20-29-35_EDT.apk (34,598,826 bytes), version 0.1.0-dev-achievements1. SHA-256: 5505B1A1AB08FF4A4DCF6D58C967F2E04E151FF39929B2F2A547C26DE5AB4281. Install over the existing app, never uninstall. Real finish animation/voice coexistence remains user acceptance; calculations, migration and gestures have emulator/unit evidence. No commit/push in this milestone.
