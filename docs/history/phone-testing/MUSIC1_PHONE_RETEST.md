# WAYiRUN — music1 phone retest

Status: deferred by the user on September 14. Music controls are implemented; this checklist is retained for future verification and does not block ongoing development. Resume these tests when the user can enable access or chooses to revisit them.

## 1 Install and enable optional music controls

Install `android/app/build/outputs/apk/debug/app-debug.apk`, version **0.1.0-dev-playlist1**, over the current debug build. Do not uninstall or clear storage to update it. This build replaces music1 for the same tests below. The pre-run screen now saves a playlist link through **Open playlist** and can clear it. Opening goes to YouTube Music; choose Play there and return before starting. This manual open action does not require music-control access.

Latest handoff supersedes the version above: use the dated **WAYiRUN-permissions2** APK under the debug output folder and confirm **Build 0.1.0-dev-permissions2** appears in the app. Every opening now keeps the splash/setup screen and requests missing Location (Precise, while using the app), Physical Activity, and Notifications permissions. Already-granted permissions do not prompt again. Repeated denial can cause Android to suppress its prompt; the app then provides App permissions. Choose **Continue to WAYiRUN** after reviewing status. Playlist entry is on the pre-run screen, not inside Run settings. Music special-access testing remains deferred.

The Moto G previously blocked music access as a restricted setting. That restriction remains unverified; this APK cannot remove Android's installation policy. Do not repeat the previously suggested missing overflow-menu instructions. If Android still denies access, record music linkage as blocked; tracking, cues, and discard remain testable. WAYiRUN does not read or retain notification contents. Do not mark player-link tests passed without access.

This build controls an active YouTube Music session. It does not choose or launch a playlist. Start music in YouTube Music when wanted. The app must never start music just because you started a run.

## 2 Start with music off

- [ ] Leave YouTube Music paused or closed. Start an Indoor time-only test run.
- [ ] Confirm the timer advances normally and no music starts.
- [ ] Pause/resume the run; music should remain off because no link was established.
- [ ] Finish. No later music playback should restart this completed run.

Result / notes:

## 3 Linked controls

- [ ] Play YouTube Music, start a new run, and check the app says **YouTube Music linked**.
- [ ] After the start cue finishes, pause the run. Music should pause once; neither should bounce back to playing.
- [ ] Resume the run. Music should resume once.
- [ ] After the cue, pause music using headphones or YouTube Music. The run should pause once.
- [ ] Resume music using headphones. The same linked paused run should resume.
- [ ] Repeat with the screen locked. Test several pause/resume cycles, allowing playback to settle between commands.
- [ ] Finish, then resume music separately. The run must stay finished.
- [ ] In a separate run started with music off, start YouTube Music later. It should link once playback is observed, without restarting the timer.

If the player does not acknowledge a command within about two seconds, WAYiRUN detaches the link and reports that music did not respond rather than reversing your run command. Initial playback from a replaced session or after app recovery must not resume a paused run; resume the run deliberately to establish the link again.

Result / notes:

## 4 Ducking and the agreed cue exception

- [ ] With music playing, reach a short time goal. Music should lower under the spoken time, distance, and average pace, then return to normal volume.
- [ ] Check completion speech includes the same three metrics in order. Test speaker and headphones.
- [ ] Pause music during a cue. **The run should keep going**, and WAYiRUN should not restart the music. This is the explicit behavior you selected, including intentional headphone pauses.
- [ ] Start music again, let the cue finish, then pause music. Outside the cue window, the run should pause.
- [ ] Directly pause the run during a cue. That should still pause linked music.

The cue window includes a short 600 ms settling period after audio drains to cover delayed player callbacks. Android and the player determine actual ducking. A denied audio-focus request skips that cue; losing focus stops it. No global volume setting is changed. Playlist launch, periodic interval announcements, and coaching remain unimplemented.

Result / notes:

## 5 Notifications and discard

- [ ] Check the running/paused notification is easy to reach. Android controls its exact order. Run settings → **Tracking notification settings** opens its channel settings if your previous Silent/muted preference was preserved.
- [ ] On a disposable completed test run, select **Discard run**. Confirm the prompt reads **ARE YOU SURE YOU WANT TO DISCARD THIS RUN??**.
- [ ] Cancel, then reopen confirmation. Tap or make a short swipe: the run must remain saved.
- [ ] Fully slide to delete. The run should disappear and setup should return.
- [ ] Reopen the app. That discarded run must not return. Older saved runs may still appear as the most recent remaining summary.

Discard is permanent local deletion of the selected run and its route/measurements/splits. It does not clear other runs or settings. Cloud, photos, and Health Connect copies do not exist in this build.

Result / notes:

## 6 Report a problem

Record test section, build version, speaker/headphones, whether music was playing before starting, notification-access state, the exact pause/resume sequence, and what the timer and player did. Include a screenshot when useful. A short sequence that reproduces a problem is more useful than repeatedly testing the entire checklist.
