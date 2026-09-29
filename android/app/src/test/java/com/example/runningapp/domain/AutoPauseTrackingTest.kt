package com.example.runningapp.domain

import org.junit.Assert.*
import org.junit.Test

class AutoPauseTrackingTest {
    private var now = 0L
    private val clock = RunClock { RunTime(now, 1_700_000_000_000L + now) }
    private fun input(mode: RunMode = RunMode.INDOOR, stride: Double? = 1.0, enabled: Boolean = true): TrackingInput {
        val run = RunController("motion", RunSettings(mode, RunUnits.KILOMETERS, 0, RunGoal.None, stride,
            announcementsEnabled = true, autoPauseEnabled = enabled), clock)
        run.start()
        return TrackingInput(run, clock)
    }
    private fun fix(speed: Double?, accuracy: Double? = 0.2) =
        GpsFix(now, 40.0, -75.0, 5f, speed, accuracy)
    private fun quiet(input: TrackingInput) {
        input.acceleration(MotionWindow(now - 1_000, now, MotionState.STATIONARY))
    }

    @Test fun stepPauseResumeExcludesPausedReadingsAndExportsPauseInterval() {
        val input = input().apply { stepsUsable = true }
        input.steps(0, 100)
        var pauseEvent: RunEvent? = null
        for (ms in 0L..6_000L step 1_000L) {
            now = ms
            quiet(input)
            pauseEvent = input.tick().update.events.firstOrNull { it.type == RunEventType.PAUSED } ?: pauseEvent
        }
        assertEquals(PauseReason.AUTOMATIC, pauseEvent!!.pauseReason)
        val paused = input.controller.snapshot()
        for (ms in 7_000L..11_000L step 1_000L) {
            now = ms
            val result = input.steps(now, 1_000 + ms / 1_000)
            assertNull(result.measurement)
            assertNull(result.fix)
            val tick = input.tick()
            if (ms < 11_000) assertEquals(RunState.PAUSED, tick.update.snapshot.state)
            else assertEquals(PauseReason.AUTOMATIC, tick.update.events.single().pauseReason)
        }
        assertEquals(RunState.RUNNING, input.controller.snapshot().state)
        assertEquals(paused.distanceMeters, input.controller.snapshot().distanceMeters, 0.0)
        assertEquals(paused.activeDurationMs, input.controller.snapshot().activeDurationMs)
        now = 12_000
        input.steps(now, 2_000)
        now = 13_000
        input.steps(now, 2_001)
        assertEquals(1.0, input.controller.snapshot().distanceMeters, 0.0)
        val exported = healthRun(input.controller.finish().snapshot)
        assertEquals(listOf(1_700_000_006_000L to 1_700_000_011_000L), exported.pauses)
    }

    @Test fun gpsPauseKeepsMotionButNeverRecordsPausedRouteAndStartsNewSegment() {
        val input = input(RunMode.OUTDOOR, null)
        for (ms in 0L..5_000L step 1_000L) { now = ms; input.gps(fix(0.0)); input.tick() }
        val paused = input.controller.snapshot()
        assertEquals(PauseReason.AUTOMATIC, paused.pauseReason)
        for (ms in 6_000L..8_000L step 1_000L) {
            now = ms
            assertNull(input.gps(fix(2.0)).fix)
            input.tick()
        }
        assertEquals(RunState.RUNNING, input.controller.snapshot().state)
        now = 9_000
        val fresh = input.gps(fix(2.0))
        assertNotEquals(paused.segments.last().id, fresh.measurement!!.segmentId)
        assertEquals(paused.distanceMeters, fresh.update.snapshot.distanceMeters, 0.0)
    }

    @Test fun missingSpeedNeverMeansStoppedAndNoStrideStillAllowsStepMotion() {
        val gpsOnly = input(RunMode.OUTDOOR, null)
        for (ms in 0L..10_000L step 1_000L) { now = ms; gpsOnly.gps(fix(null)); gpsOnly.tick() }
        assertEquals(RunState.RUNNING, gpsOnly.controller.snapshot().state)
        now = 0
        val steps = input(stride = null).apply { stepsUsable = true }
        for (ms in 0L..6_000L step 1_000L) { now = ms; quiet(steps); steps.tick() }
        assertEquals(PauseReason.AUTOMATIC, steps.controller.snapshot().pauseReason)
        assertFalse(steps.distanceAvailable)
        assertEquals(0.0, steps.controller.snapshot().distanceMeters, 0.0)
    }

    @Test fun lossOfSensorsAndManualOverrideKeepAutoPausedRunPaused() {
        val input = input().apply { stepsUsable = true }
        for (ms in 0L..6_000L step 1_000L) { now = ms; quiet(input); input.tick() }
        input.stepsUsable = false
        for (ms in 7_000L..10_000L step 1_000L) { now = ms; input.tick() }
        assertEquals(PauseReason.AUTOMATIC, input.controller.snapshot().pauseReason)
        input.controller.pause()
        input.reset()
        input.stepsUsable = true
        for (ms in 11_000L..20_000L step 1_000L) { now = ms; input.steps(now, ms); input.tick() }
        assertEquals(PauseReason.MANUAL, input.controller.snapshot().pauseReason)
    }

    @Test fun disabledPolicyKeepsTimeOnlyRunActiveAndTwoOldStepsDoNotResume() {
        val disabled = input(enabled = false).apply { stepsUsable = true }
        for (ms in 0L..10_000L step 1_000L) { now = ms; disabled.tick() }
        assertEquals(RunState.RUNNING, disabled.controller.snapshot().state)
        now = 0
        val input = input().apply { stepsUsable = true }
        input.controller.autoPause()
        for (ms in 0L..6_000L step 1_000L) {
            now = ms
            if (ms <= 2_000) input.steps(now, ms / 1_000)
            input.tick()
        }
        assertEquals(RunState.PAUSED, input.controller.snapshot().state)
    }

    @Test fun announcementBoundaryIsNotReplayedAcrossAutomaticPauseOrRecovery() {
        val input = input()
        now = 300_000
        assertEquals(1, input.tick().update.events.count { it.type == RunEventType.ANNOUNCEMENT })
        input.stepsUsable = true
        for (ms in 301_000L..306_000L step 1_000L) { now = ms; quiet(input); input.tick() }
        assertEquals(PauseReason.AUTOMATIC, input.controller.snapshot().pauseReason)
        val recovered = RunController.recover(input.controller.checkpoint(), clock)
        assertEquals(PauseReason.INTERRUPTED, recovered.snapshot().pauseReason)
        assertEquals(RunState.PAUSED, recovered.autoResume().snapshot.state)
        now += 500_000
        assertTrue(recovered.resume().events.none { it.type == RunEventType.ANNOUNCEMENT })
        now += 294_000
        assertEquals(1, recovered.tick().events.count { it.type == RunEventType.ANNOUNCEMENT })
    }

    @Test fun reportedInitialAndPostResumeSilenceDoesNotPauseAndBatchesKeepDistance() {
        val input = input().apply { stepsUsable = true }
        for (ms in 0L..20_000L step 1_000L) { now = ms; input.tick() }
        assertEquals(RunState.RUNNING, input.controller.snapshot().state)
        input.controller.pause()
        input.reset()
        input.controller.resume()
        for (ms in 21_000L..40_000L step 1_000L) {
            now = ms
            input.acceleration(MotionWindow(ms - 1_000, ms, MotionState.MOVING))
            if (ms % 5_000 == 0L) input.steps(ms, ms / 500)
            input.tick()
            assertEquals(RunState.RUNNING, input.controller.snapshot().state)
        }
        assertEquals(30.0, input.controller.snapshot().distanceMeters, 0.0)
    }

    @Test fun coverageLossBetweenTicksRestartsFiveSecondDwell() {
        val input = input()
        for (ms in 1_000L..5_000L step 1_000L) { now = ms; quiet(input); input.tick() }
        now = 5_500
        input.acceleration(MotionWindow(5_000, now, MotionState.UNKNOWN))
        for (ms in 6_000L..11_000L step 1_000L) {
            now = ms
            quiet(input)
            input.tick()
            assertEquals(if (ms == 11_000L) RunState.PAUSED else RunState.RUNNING, input.controller.snapshot().state)
        }
    }
}
