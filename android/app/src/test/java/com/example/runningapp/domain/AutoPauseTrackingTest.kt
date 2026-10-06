package com.example.runningapp.domain

import org.junit.Assert.*
import org.junit.Test

class AutoPauseTrackingTest {
    private var now = 0L
    private val clock = RunClock { RunTime(now, 1_700_000_000_000L + now) }
    private fun input(mode: RunMode = RunMode.INDOOR, stride: Double? = 1.0, enabled: Boolean = true) =
        TrackingInput(RunController("motion", RunSettings(mode, RunUnits.KILOMETERS, 0, RunGoal.None,
            stride, announcementsEnabled = true, autoPauseEnabled = enabled), clock).also { it.start() }, clock)
    private fun fix(speed: Double?, accuracy: Double? = 0.2) = GpsFix(now, 40.0, -75.0, 5f, speed, accuracy)
    private fun arm(input: TrackingInput) {
        input.detectorUsable = true
        input.detectedStep(now); input.tick()
        now += 1_000; input.detectedStep(now); input.tick()
    }
    private fun quietUntil(input: TrackingInput, end: Long) {
        while (now < end) { now = minOf(now + 1_000, end); input.tick() }
    }

    @Test fun detectorPauseResumeFreezesAccountingAndStartsFreshCounterBaseline() {
        val input = input().apply { stepsUsable = true }
        input.steps(0, 100); arm(input)
        input.steps(now, 110)
        quietUntil(input, 6_000)
        val paused = input.controller.snapshot()
        assertEquals(PauseReason.AUTOMATIC, paused.pauseReason)
        now = 7_000
        assertNull(input.steps(now, 1_000).measurement)
        input.detectedStep(now); input.tick()
        now = 8_000
        val resumed = input.detectedStep(now)
        assertEquals(RunState.RUNNING, resumed.update.snapshot.state)
        assertEquals(PauseReason.AUTOMATIC, resumed.update.events.single().pauseReason)
        assertEquals(paused.activeDurationMs, resumed.update.snapshot.activeDurationMs)
        assertEquals(10.0, resumed.update.snapshot.distanceMeters, 0.0)
        now = 9_000; input.steps(now, 2_000)
        now = 10_000; input.steps(now, 2_001)
        assertEquals(11.0, input.controller.snapshot().distanceMeters, 0.0)
        assertEquals(listOf(1_700_000_006_000L to 1_700_000_008_000L), healthRun(input.controller.finish().snapshot).pauses)
    }

    @Test fun counterSilenceAndLargeBatchesNeverDrivePauseOrResume() {
        val input = input().apply { stepsUsable = true }
        for (ms in 0L..20_000L step 1_000) {
            now = ms
            if (ms % 5_000 == 0L) input.steps(now, ms / 500)
            input.tick()
        }
        assertEquals(RunState.RUNNING, input.controller.snapshot().state)
        assertEquals(40.0, input.controller.snapshot().distanceMeters, 0.0)
        input.controller.autoPause(); input.reset(automaticTransition = true)
        for (ms in 21_000L..30_000L step 1_000) { now = ms; input.steps(now, ms); input.tick() }
        assertEquals(PauseReason.AUTOMATIC, input.controller.snapshot().pauseReason)
    }

    @Test fun gpsOnlyPauseKeepsMotionWithoutRecordingPausedRoute() {
        val input = input(RunMode.OUTDOOR, null)
        for (ms in 0L..5_000L step 1_000) { now = ms; input.gps(fix(0.0)); input.tick() }
        val paused = input.controller.snapshot()
        assertEquals(PauseReason.AUTOMATIC, paused.pauseReason)
        for (ms in 6_000L..8_000L step 1_000) {
            now = ms; assertNull(input.gps(fix(2.0)).fix); input.tick()
        }
        assertEquals(RunState.RUNNING, input.controller.snapshot().state)
        now = 9_000
        val fresh = input.gps(fix(2.0))
        assertNotEquals(paused.segments.last().id, fresh.measurement!!.segmentId)
        assertEquals(paused.distanceMeters, fresh.update.snapshot.distanceMeters, 0.0)
    }

    @Test fun missingSpeedAndInvalidFixBetweenTicksCannotProveStillness() {
        val input = input(RunMode.OUTDOOR, null)
        for (ms in 0L..10_000L step 1_000) {
            now = ms; input.gps(fix(null)); input.tick()
        }
        for (ms in 11_000L..20_000L step 1_000) {
            now = ms; input.gps(fix(0.0)); input.tick()
            now = ms + 500; input.gps(fix(null))
        }
        assertEquals(RunState.RUNNING, input.controller.snapshot().state)
    }

    @Test fun detectorWorksWithoutCounterOrStrideAndRearmsAfterManualResume() {
        val input = input(stride = null); arm(input); quietUntil(input, 6_000)
        assertEquals(PauseReason.AUTOMATIC, input.controller.snapshot().pauseReason)
        assertFalse(input.distanceAvailable)
        input.controller.pause(); input.reset()
        input.detectorUsable = false
        now = 7_000; input.controller.resume(); input.reset(); input.detectorUsable = true
        quietUntil(input, 20_000)
        assertEquals(RunState.RUNNING, input.controller.snapshot().state)
        arm(input); quietUntil(input, 26_000)
        assertEquals(PauseReason.AUTOMATIC, input.controller.snapshot().pauseReason)
    }

    @Test fun repeatedAutomaticStopsPreserveArmingButNotResumeEvidence() {
        val input = input(); arm(input); quietUntil(input, 6_000)
        now = 7_000; input.detectedStep(now); input.tick()
        now = 8_000; input.detectedStep(now)
        quietUntil(input, 13_000)
        assertEquals(PauseReason.AUTOMATIC, input.controller.snapshot().pauseReason)
        quietUntil(input, 16_000)
        assertEquals(RunState.PAUSED, input.controller.snapshot().state)
    }

    @Test fun manualAndInterruptedRecoveryRequireExplicitResume() {
        val input = input(); arm(input); quietUntil(input, 6_000)
        input.controller.pause(); input.reset()
        for (ms in 7_000L..10_000L step 1_000) { now = ms; input.detectedStep(now); input.tick() }
        assertEquals(PauseReason.MANUAL, input.controller.snapshot().pauseReason)
        val recovered = RunController.recover(input.controller.checkpoint(), clock)
        val restored = TrackingInput(recovered, clock).apply { detectorUsable = true }
        for (ms in 11_000L..15_000L step 1_000) { now = ms; restored.detectedStep(now); restored.tick() }
        assertEquals(PauseReason.INTERRUPTED, recovered.snapshot().pauseReason)
    }

    @Test fun disabledPolicyNeverPausesEvenWithWorkingDetector() {
        val input = input(enabled = false); arm(input); quietUntil(input, 20_000)
        assertEquals(RunState.RUNNING, input.controller.snapshot().state)
    }

    @Test fun latePrePauseCounterBatchRemainsExcludedByExistingAccountingContract() {
        val input = input().apply { stepsUsable = true }
        input.steps(0, 100); arm(input); input.steps(1_000, 110)
        quietUntil(input, 6_000)
        now = 7_000
        // Ten real pre-pause steps arrive after the segment closed: no retrospective rewrite.
        assertNull(input.steps(2_000, 120).measurement)
        assertEquals(10.0, input.controller.snapshot().distanceMeters, 0.0)
        input.detectedStep(now); input.tick()
        now = 8_000; input.detectedStep(now)
        input.steps(now, 120)
        now = 9_000; input.steps(now, 121)
        assertEquals(11.0, input.controller.snapshot().distanceMeters, 0.0)
    }

    @Test fun announcementProgressSurvivesAutomaticPauseAndRecovery() {
        val input = input()
        now = 300_000
        assertEquals(1, input.tick().update.events.count { it.type == RunEventType.ANNOUNCEMENT })
        arm(input); quietUntil(input, 306_000)
        val recovered = RunController.recover(input.controller.checkpoint(), clock)
        now += 500_000
        assertTrue(recovered.resume().events.none { it.type == RunEventType.ANNOUNCEMENT })
        now += 294_000
        assertEquals(1, recovered.tick().events.count { it.type == RunEventType.ANNOUNCEMENT })
    }
}
