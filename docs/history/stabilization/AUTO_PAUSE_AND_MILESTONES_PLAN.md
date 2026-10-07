# 1 Auto-pause correction and milestone announcements

## 1.1 Status and authority

Approved September 28, 2026, including the recommended defaults. Based on the owner's physical indoor-run report and latest announcement decisions. Milestone 12 implementation and automated verification are complete; physical BUG-006 acceptance remains open. See MILESTONE_12_TEST_HANDOFF.md. the owner subsequently authorized continuing through tests without a model-switch pause because paid credits are available. Milestones 13 and 14 remain separate pending implementation; no deployment or physical-phone installation is authorized here.

## 1.2 Required outcomes

- Indoor running must not auto-pause because step callbacks are delayed, batched or absent. Indoor tracking continues to avoid GPS.
- Auto-pause remains default-on. Reliable stillness should pause after about five seconds, sustained movement should resume after about two seconds, and a manual pause must never auto-resume.
- All milestone configuration belongs exclusively in gear settings. Goal selection/target remains on New Run; a goal is distinct from repeating milestones.
- Time and distance milestones can be enabled separately or together. Preserve the existing 5/10 active-minute and 0.5/1 selected-distance-unit choices.
- Distance labels and spoken values use the captured Miles/Kilometers setting. No separate hidden distance-unit preference.
- When a milestone coincides with the run goal, play only goal audio. Tracking and later repeating milestones continue after the goal.

# 2 Findings from current code

## 2.1 Auto-pause evidence defect

SensorAdapters.kt registers TYPE_STEP_COUNTER with zero requested batching latency and returns successful registration as stepsUsable. Successful registration is not proof of timely delivery. TrackingInput.kt retains only the latest motion counter sample; samples older than three seconds are excluded from motion evidence. AutoPausePolicy.kt counts counter-change observations inside a three-second window, treats zero or one change as stationary, and considers stepsHealthy true even without an initial sample. Consequently callback silence alone starts the five-second pause timer; a large counter delta is still one observation. Reset on resume empties evidence and can restart the same false-pause cycle. This mechanism is consistent with the reported timing, but the actual phone's sensor delivery pattern has not been measured.

The existing healthySilentStepsPauseAtFiveSecondsAndIgnoreOneStrayStep test encodes this unsafe assumption. Passing synthetic service tests did not exercise realistic hardware batching.

## 2.2 Announcement limitations

RunModels.kt stores one announcementInterval enum, and AnnouncementSettings.kt renders one radio group containing both time and distance options. WayirunApp.kt already calls that component only inside showSettings. RunController.kt emits time and distance milestones through separate paths, but both read the same selection. RunCueQueue.kt currently enqueues every ANNOUNCEMENT and GOAL_REACHED event without collision arbitration.

# 3 Milestone 12: reliable indoor auto-pause

## 3.1 Implementation approach

1. Introduce explicit motion evidence states in the pure domain package: moving, stationary and unknown, including sample age and observation coverage. Separate adapter availability from usable evidence. A missing initial sample, callback gap, stale timestamp, registration failure or permission loss means unknown, never stationary.
2. Keep the cumulative step counter for distance. Treat positive counter deltas as movement evidence even when delivered in a batch; preserve event timestamps and do not manufacture per-step times from one batch. Ingest observations when callbacks arrive rather than retaining only the newest sample until a timer tick.
3. Add TYPE_STEP_DETECTOR support for timely movement evidence when available, without adding its steps to distance a second time. Step-counter or detector silence alone cannot establish stationary evidence.
4. For indoor stop detection, add a small accelerometer adapter feeding bounded rolling motion summaries to the domain policy. Use fresh continuous sample coverage and gravity-independent variation to distinguish quiet from motion; do not store raw acceleration in runs or introduce a general activity-classification framework. Do not use accelerometer integration to calculate distance.
5. Require a full fresh observation window before considering stillness. Positive steps veto a stationary decision. Ambiguous vibration/motion, missing coverage or conflicting evidence resets the pause candidate. Unknown evidence leaves the current state unchanged. Outdoor mode keeps fresh accurate GPS evidence; stale step registration must not veto otherwise valid GPS movement or falsely establish a stop.
6. Preserve manual-pause and interrupted-recovery rules, sensor-generation rejection, foreground-service lifecycle and fresh distance baselines after resume. Clear stale motion evidence on registration changes, but never interpret cleared evidence as stillness.
7. Keep existing approximately five-second pause and two-second sustained-resume durations. Do not hide the bug with a longer initial grace period or by globally disabling auto-pause. If a phone lacks adequate motion evidence, tracking continues and the auto-pause setting remains available; no invented transitions.

## 3.2 Calibration and boundaries

Acceleration thresholds are implementation tuning values, not proven product constants. During implementation inspect available hardware and collect only bounded synthetic/development motion summaries: sensor type, delivery/event age, counter delta, window coverage and decision reason. No GPS coordinates, accounts, keys or raw indefinite logging. Verify the thresholds against moving and stationary traces before declaring indoor acceptance. Counter silence cannot be fixed by merely increasing the three-second window.

Use a modest sampling rate and bounded in-memory window only while motion observation is needed for an enabled active/automatically paused run. Unregister on manual pause, finish, teardown and disabled auto-pause as appropriate. Confirm screen-off delivery and battery impact on the real phone. A phone left on a treadmill console may not represent the runner's movement; phone placement must be recorded during acceptance.

## 3.3 Files and tests

Primary files: domain/AutoPausePolicy.kt, domain/TrackingInput.kt, tracking/SensorAdapters.kt and tracking/TrackingService.kt. Extend the existing small adapter; keep Android types out of domain code. Update AutoPausePolicyTest, AutoPauseTrackingTest, AutoPauseAccountingTest and AutoPauseServiceTest.

Required regressions: no sensor callbacks after registration; first callback delayed beyond ten seconds; counter batches several seconds apart with large deltas; multiple callbacks between ticks; stale/out-of-order timestamps; counter reset; detector unavailable; accelerometer coverage loss; motion conflicting with quiet acceleration; reliable stationary window; resume followed by continued batched running; manual pause; interrupted recovery; permission loss; outdoor GPS-only operation; and unchanged distance/time/route accounting during pauses. Replace the test that expects silent registered steps to prove stillness.

## 3.4 Exit gate

the owner waived the model-switch pause for this milestone and authorized completing automated verification. Test phase runs the Android unit/build/lint gate and targeted emulator service tests. Phone acceptance: with the phone carried normally, run indoors continuously for several minutes including the original first-ten-seconds/manual-resume sequence; no false pause. Stop and resume repeatedly with expected cues and frozen paused accounting; repeat screen-off and auto-pause-off. Do not mark the phone bug fixed solely from emulator results.

# 4 Milestone 13: independent milestone selections

September 28 status: implementation and verification complete. the owner used the requested implementation/test model-switch pause. Android, Worker compatibility, emulator and visual gates passed; see MILESTONE_13_TEST_HANDOFF.md. Milestone 14 remains unimplemented and should begin after switching back to the implementation model.

## 4.1 Settings and persistence

1. Retain the master announcement switch. Under it put independent Time and Distance enable switches/checkboxes, each with its own existing interval choices. Both may be selected; neither means no repeating announcement. Master off preserves the selected choices. All controls remain inside gear settings.
2. Preserve 5/10 minutes and 0.5/1 mile or kilometer. Default new installs to time enabled at five minutes, distance disabled, matching the existing default. Do not add custom intervals, voices or another settings page.
3. Migrate existing preferences deterministically: old time selection enables only that time interval; old distance selection enables only that distance interval; old master off stays off. Apply migration once and persist the result so later reopens cannot overwrite new choices.
4. Extend captured RunSettings with an optional versioned dual-interval configuration and a single effective-settings resolver. Missing configuration resolves the legacy master/enum exactly; present configuration is authoritative. Preserve legacy fields for decoding old records. New settings apply only to future runs; active/recovered runs retain captured settings.
5. Inspect all serialized settings consumers before editing: checkpoint/Room JSON, sync archive validation, Worker coaching context, browser settings rendering and CSV reconstruction. Use additive JSON compatibility where possible; no speculative Room table migration. Add fixtures for old, new time-only, distance-only, both and disabled captures. Keep old archive bytes unchanged.

## 4.2 Controller behavior

Use independent time and distance progress. Preserve active-time counting, units captured at start, interpolation within accepted distance intervals, no milestones from paused motion, and no replay after restore. Advance each due threshold even when its audio is suppressed. Large accepted updates crossing several thresholds must remain deterministic and must not skip retained splits. Existing time-only behavior and speech wording remain unchanged apart from goal collision handling.

## 4.3 Verification

Test both channels individually and together, miles/km, settings reopen and one-time migration, old checkpoint decoding, pause/resume, process recovery, source changes, unavailable distance, continuation past goal and changing global units during an active run. UI assertions must prove milestone controls are absent from New Run and active/summary screens, present only in settings, and readable in light/dark at 200-percent font. Build/JVM/lint and archive/Worker tests run after the implementation-phase pause.

# 5 Milestone 14: goal-audio priority and final handoff

September 28 status: implementation and verification complete after the owner's requested model switch. The Android gate, 26 targeted emulator cases and combined visual checks passed. MILESTONE_14_TEST_HANDOFF.md records exact evidence and the final combined APK. Physical BUG-006 and audible-device acceptance remain open.

## 5.1 Collision semantics

Accepted default: use event occurrence time in accumulated active-run time, not the time speech eventually plays. Exact aligned boundaries always collide. Treat different-channel events within one second of active time as coincident, allowing timer/sensor callbacks for the same moment to arrive separately. the owner approved this default on September 28.

Determine goal crossing time from the configured time boundary or the existing accepted-distance interpolation; do not classify every event in a large sensor batch as simultaneous. Preserve the existing goal recap metrics. When both milestone channels coincide without a goal, speak one combined metrics recap rather than duplicate recaps.

## 5.2 Implementation

1. Compute eligible goal/milestone events before dispatching the update to audio. Suppress coincident milestone audio when a goal is present; retain split records and consumed milestone progress.
2. Carry sufficient occurrence metadata into cue arbitration to handle time ticks and distance callbacks arriving separately. Keep milestone cues pending for a bounded one-second coalescing interval while leaving state/goal/finish cues prompt. A goal removes a matching pending milestone. Do not assume matching events are always in one play(events) call.
3. Keep unrelated older/later milestones and different runs independent. Finish, cancellation, account/run replacement and teardown clear pending arbitration state and any scheduled callbacks. Preserve audio focus release, TTS fallback and completion/coaching sequencing. Do not stop unrelated speech merely because a goal occurs.
4. Test the policy with a fake monotonic clock/scheduler and fake CueOutput. No wall-clock sleeps or paid speech calls in unit tests.

## 5.3 Required examples

- Five-minute time goal with five-minute milestone: goal cue only, then the ten-minute milestone if the run continues.
- One-mile goal with one-mile milestone: goal cue only; same for one kilometer.
- Time goal and distance milestone in the same active-time window, or distance goal and time milestone: goal cue only.
- Time and distance milestones coincide without a goal: one recap, both thresholds consumed.
- Adjacent callbacks in either order, delayed/batched sensor data, thresholds outside the collision window, already queued unrelated audio, process recovery and finish during coalescing: no duplicate, lost future milestone, leaked timer or stuck focus.

## 5.4 Final verification and delivery

After the model-switch pause, run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain` from android/. Run Worker tests if serialized settings/contracts change; otherwise use targeted compatibility fixtures. Run relevant instrumentation and inspect settings screenshots. Prepare one timestamped APK for the combined correction after automated checks; no install or deployment as part of planning.

Update REQUIREMENTS.md, TASKS.md, the issue ledger and the acceptance checklist as implementation proceeds. Record the indoor bug as open until real-phone running confirms the correction. Record the user's working time-announcement test as phone evidence and preserve that behavior. Do not expand into health metrics, new sensors beyond the motion correction, coaching, maps, sharing changes or production release work.

# 6 Defaults and optional missing information

Proceed using existing interval choices, the global miles/km setting, master on/off plus independent time/distance controls, time-only defaults for new installs and preserved legacy selections. Use one recap for simultaneous time/distance milestones and a one-second active-time collision tolerance with goal priority unless the owner changes these defaults.

For the phone acceptance record, capture phone model/Android version, whether it was carried or resting on equipment, and whether step distance increased before false pauses. These details help validate the sensor hypothesis but do not block implementation of the demonstrated unknown-versus-stationary defect.
