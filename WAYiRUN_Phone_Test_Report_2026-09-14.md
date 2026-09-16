# WAYiRUN Phone Test Report

**Test date:** September 14, 2026  
**Installed build/install date:** September 13, 2026  
**Device:** Moto G 2025, Android 16  
**Battery saver / app restrictions:** Off / None  
**Permissions:** Precise location allowed; Physical activity allowed; Notifications on  
**Distance unit:** miles  
**Configured distance per step:** 80 cm

## Development disposition

**Milestone 3 result: ACCEPT AND CONTINUE.**

- 11 of 13 functional test areas: **Pass**
- 2 of 13 test areas: **Not tested / explicitly deferred**
- Reported failures: **0**
- Overall submitted result: **Pass**

The response supports continuing development. Keep the deferred validation items below as non-blocking follow-up work; they do not hold the milestone open.

## Implementation requirements from tester notes

### Active UI pass

1. Make the setup choice buttons square at a 1:1 aspect ratio.
2. Lay out paired setup choices as `Label | Button 1 | Button 2`.
3. Use a gear icon for Run Settings and place it at the upper-right of the setup screen.
4. Center the countdown on screen.
5. Replace **Keep Moving** with **Tracking Run** and center it.
6. Make the **Goal reached — keep going** state substantially more prominent.
7. Use clear yellow, red, and green status colors and familiar icons where helpful, including thumbs-up and pause symbols.
8. Add live run-screen indicators for:
   - GPS availability
   - Server connectivity
   - Other tracking modes whose state can change during a run
9. Keep the tracking notification present while a run is paused, matching Nike Run Club behavior.

### Finish and summary flow

1. After the user finishes a run, open the photo step instead of ending at the saved-run screen.
2. Follow the Nike Run Club-style sequence already planned for the app:
   - Prompt for the run photo.
   - Show the selected photo with the run statistics overlay/summary.
   - Title the final screen **Run Summary**.
   - Put **New Run** at the bottom of that screen.

This belongs with the planned photo milestone; the current prototype was not expected to contain photo capture yet.

### Time-only distance estimation

Time-only runs should receive an estimated distance instead of remaining at zero:

1. Use a default pace of **10:00 per mile** until enough personal run data exists.
2. Once the runner has accumulated at least **10 miles**, derive the estimate from the average of the **five most statistically well-grouped one-mile times**.
3. A deterministic implementation matching that request:
   - Gather eligible completed one-mile split times.
   - Sort them by duration.
   - Examine every contiguous group of five.
   - Select the group with the lowest variance.
   - Use that group's mean pace for time-only distance calculations.
4. Recalculate the personal estimate as additional eligible mile splits are saved.

## Test-by-test results and notes

| Test | Result | Tester notes / development meaning |
| --- | --- | --- |
| 2.1 Setup and readability | Pass | Prefer 1:1 square buttons; use `Label | Button 1 | Button 2`; replace Run Settings text with a gear at upper-right. |
| 2.2 Countdown and time-only run | Pass | Center countdown. Replace Keep Moving with centered Tracking Run. Make goal reached much more obvious; colored status icons are welcome. |
| 2.3 Finish and saved summary | Pass | Add time-only distance estimation. After finish, prompt for photo, then show photo and summary on a Run Summary screen with New Run at bottom. |
| 3.1 Counted-step comparison | Pass | No quantitative comparison was entered. Functional step tracking passed, but accuracy was not documented. |
| 3.2 Background and locked screen | Pass | No anomaly reported. |
| 4.1 Measured-route comparison | Pass | Outdoor tracking must not require clearing stride length. Use **screenshot** when referring to screen capture; photo capture is not implemented yet. No route-distance/pace figures were entered. |
| 4.2 Outdoor pause movement and distance goal | Pass | Audio was absent as expected because it is not implemented. Keep the tracking notification visible while paused. |
| 5.1 GPS loss and return with working steps | Pass, method inconclusive | Tested using airplane mode. Add GPS and server-connection status indicators. Airplane mode did not prove the intended GPS-loss/source-switch path. |
| 5.2 Permission denial and revocation | Not tested; waived for now | Tester requested that this be treated as acceptable and deferred rather than blocking continued development. |
| 5.3 No Internet and audible cues | Not tested; waived for now | Tester requested continuing without this check. Repeat after audible cues exist. |
| 6.1 Returning to the running app | Pass | No anomaly reported. |
| 6.2 Force-stop recovery | Pass | No anomaly reported. |
| 6.3 Reboot recovery | Pass | No anomaly reported. |
| 7 Overall result | Pass | No failure report submitted. |

## Non-blocking follow-up verification

1. **Indoor step accuracy:** repeat 3.1 and record counted steps, expected distance, recorded distance, and difference.
2. **Outdoor GPS accuracy:** repeat 4.1 and record reference distance, recorded distance, pace, splits, partial split, and any screen-off gaps.
3. **GPS-to-step fallback:** repeat 5.1 in a location that actually degrades GPS while steps remain available; airplane mode alone does not establish a GPS source transition.
4. **Permission recovery:** run 5.2 during a later robustness pass.
5. **Offline audio:** run 5.3 after speech/fallback tones are implemented.

## Checklist corrections

- Do not instruct the tester to clear stride length for an outdoor run. GPS should remain primary when available, with the configured stride retained for fallback.
- Say **take a screenshot** for screen captures. Reserve **take a photo** for the future run-photo workflow.
- Do not test audio as an implemented feature until audible cues are present.
- Add explicit numeric result fields or make them required for the counted-step and measured-route comparison tests.

## Original notes preserved

> I would prefer the buttons to be 1:1 square. I'd like them to be Label | Button1 | Button2 Use gear icon for run settings and put it in the top right corner of the screen.

> Countdown should be centered on the screen if possible. Ok so replace Keep Moving text with Tracking Run, centered. Goal reached - keep going should be much more obvious. It's ok to use yellow, red, and green icons and such here, thumbs up, pause icon, it's ok to use those to make the UI experience better.

> Calculate distance for time only runs starting with a 10 minute mile. When there are 10 miles of runs, take the average of the 5 most statistically well grouped mile times and use that instead. Instead of saving the run and going to new run, I want to be asked for the photo here like Nike Run Club, then I want to see the photo along with that summary, title it run summary, and put the new run button at the bottom.

> I should not have to clear the stride length to use satellite tracking. I'm sure you didn't make the app that way, but that's how the test is set up so I'm mentioning it. Also don't say photograph when you mean screenshot. I don't think we've implemented photos yet.

> I'm not hearing any audio messages yet and I don't think you've implemented them so that's expected. Also while the app is paused I want it to remain persistent in the notifications area at the top like Nike Run Club.

> Tested by putting the device in airplane mode. Add icons to the run tracking screen that indicate whether GPS is available and the app is connected to the server and any other modes that can change during a run.

> I assume this is fine. Maybe later we can test modes where people would break the app on purpose but as for now just assume this is a pass result.

> I feel like this is probably ok and we should just continue.
