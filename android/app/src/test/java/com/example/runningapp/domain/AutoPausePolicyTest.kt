package com.example.runningapp.domain

import org.junit.Assert.*
import org.junit.Test

class AutoPausePolicyTest {
    private fun policy(mode: RunMode = RunMode.INDOOR, detector: Boolean = true) =
        AutoPausePolicy(mode).apply { detectorAvailability(detector, 0) }
    private fun gps(ms: Long, speed: Double = 0.0, error: Double = 0.2) = MotionGps(ms, speed, 5.0, error)
    private fun active(p: AutoPausePolicy, ms: Long) = p.evaluate(ms, RunState.RUNNING, null, true)
    private fun paused(p: AutoPausePolicy, ms: Long) = p.evaluate(ms, RunState.PAUSED, PauseReason.AUTOMATIC, true)
    private fun arm(p: AutoPausePolicy) {
        p.observeStep(0, 0); active(p, 0)
        p.observeStep(1_000, 1_000); active(p, 1_000)
    }

    @Test fun initialSilenceAndOneStepNeverArmIndoorPause() {
        for (detector in listOf(false, true)) {
            val p = policy(detector = detector)
            p.observeStep(0, 0)
            for (ms in 0L..20_000L step 1_000) assertEquals(AutoPauseDecision.NONE, active(p, ms))
        }
    }

    @Test fun fiveSecondQuietBoundaryAndSingleStepRestart() {
        val p = policy(); arm(p)
        for (ms in 2_000L..5_000L step 1_000) assertEquals(AutoPauseDecision.NONE, active(p, ms))
        assertEquals(AutoPauseDecision.NONE, active(p, 5_999))
        assertEquals(AutoPauseDecision.PAUSE, active(p, 6_000))
        p.observeStep(6_100, 6_100)
        for (ms in 7_000L..11_000L step 1_000) assertEquals(AutoPauseDecision.NONE, active(p, ms))
        assertEquals(AutoPauseDecision.PAUSE, active(p, 11_100))
    }

    @Test fun receiptTimeMakesStopConservativeWithDelayedButTimelySteps() {
        val p = policy()
        p.observeStep(2_000, 0); active(p, 2_000)
        p.observeStep(3_000, 1_000); active(p, 3_000)
        for (ms in 4_000L..7_000L step 1_000) assertEquals(AutoPauseDecision.NONE, active(p, ms))
        assertEquals(AutoPauseDecision.PAUSE, active(p, 8_000))
    }

    @Test fun twoStepsMustOccurWithinArmingWindow() {
        val p = policy()
        for (ms in 0L..12_000L step 1_000) {
            if (ms == 0L || ms == 6_000L) p.observeStep(ms, ms)
            assertEquals(AutoPauseDecision.NONE, active(p, ms))
        }
    }

    @Test fun lateDeliveryDisarmsUntilTwoNewTimelySteps() {
        val p = policy(); arm(p)
        for (ms in 2_000L..4_000L step 1_000) active(p, ms)
        p.observeStep(5_000, 2_000)
        for (ms in 5_000L..10_000L step 1_000) assertEquals(AutoPauseDecision.NONE, active(p, ms))
        p.observeStep(10_100, 10_100)
        p.observeStep(11_000, 11_000)
        for (ms in 11_000L..15_000L step 1_000) assertEquals(AutoPauseDecision.NONE, active(p, ms))
        assertEquals(AutoPauseDecision.PAUSE, active(p, 16_000))
    }

    @Test fun staleApplicationQueueDisarmsEvenWhenHardwareDeliveryWasTimely() {
        val p = policy(); arm(p)
        for (ms in 2_000L..4_000L step 1_000) active(p, ms)
        p.observeStep(5_000, 2_000, receivedMs = 2_000)
        for (ms in 5_000L..12_000L step 1_000) assertEquals(AutoPauseDecision.NONE, active(p, ms))
    }

    @Test fun resumeUsesEventTimesAndDoesNotAddAnotherDwell() {
        val p = policy()
        paused(p, 0)
        p.observeStep(2_000, 500)
        assertEquals(AutoPauseDecision.NONE, paused(p, 2_000))
        p.observeStep(2_000, 1_500)
        assertEquals(AutoPauseDecision.RESUME, paused(p, 2_000))
    }

    @Test fun duplicatesOutOfOrderFutureAndOldBatchesCannotResume() {
        val p = policy()
        for (ms in 0L..6_000L step 1_000) paused(p, ms)
        p.observeStep(6_000, 1_000); p.observeStep(6_000, 2_000)
        p.observeStep(6_000, 7_000); p.observeStep(6_000, 6_000)
        p.observeStep(6_000, 6_000); p.observeStep(6_000, 5_999)
        assertEquals(AutoPauseDecision.NONE, paused(p, 6_000))
    }

    @Test fun stepsOutsideTwoSecondResumeWindowDoNotResume() {
        val p = policy(); paused(p, 0)
        p.observeStep(0, 0); paused(p, 1_000); paused(p, 2_000)
        p.observeStep(2_001, 2_001)
        assertEquals(AutoPauseDecision.NONE, paused(p, 2_001))
    }

    @Test fun outdoorStopWindowsRunConcurrently() {
        val p = policy(RunMode.OUTDOOR); arm(p)
        for (ms in 1_000L..6_000L step 1_000) {
            p.observeGps(ms, gps(ms, 0.3))
            assertEquals(if (ms == 6_000L) AutoPauseDecision.PAUSE else AutoPauseDecision.NONE, active(p, ms))
        }
    }

    @Test fun gpsOnlyFallbackButUnarmedRegisteredDetectorBlocksPause() {
        for (detector in listOf(false, true)) {
            val p = policy(RunMode.OUTDOOR, detector)
            for (ms in 0L..5_000L step 1_000) {
                p.observeGps(ms, gps(ms))
                assertEquals(if (!detector && ms == 5_000L) AutoPauseDecision.PAUSE else AutoPauseDecision.NONE, active(p, ms))
            }
        }
    }

    @Test fun gpsLossCannotFallBackToDetectorSilence() {
        val p = policy(RunMode.OUTDOOR); arm(p)
        for (ms in 2_000L..12_000L step 1_000) {
            if (ms < 5_000) p.observeGps(ms, gps(ms))
            assertEquals(AutoPauseDecision.NONE, active(p, ms))
        }
    }

    @Test fun badGpsBetweenTicksRestartsDwellAndCachedFixCannotCompleteIt() {
        val p = policy(RunMode.OUTDOOR, false)
        for (ms in 0L..4_000L step 1_000) { p.observeGps(ms, gps(ms)); active(p, ms) }
        p.observeGps(4_500, gps(4_500, Double.NaN))
        for (ms in 5_000L..10_000L step 1_000) {
            p.observeGps(ms, gps(ms))
            assertEquals(if (ms == 10_000L) AutoPauseDecision.PAUSE else AutoPauseDecision.NONE, active(p, ms))
        }
        p.reset(11_000)
        p.observeGps(11_000, gps(11_000))
        for (ms in 11_000L..20_000L step 1_000) {
            p.observeGps(ms, gps(11_000))
            assertEquals(AutoPauseDecision.NONE, active(p, ms))
        }
    }

    @Test fun gpsQualityAndUncertaintyDeadbandAreConservative() {
        val invalid = listOf(gps(0, 0.31, 0.2), gps(0, 0.6, 0.0), gps(0, -0.1),
            gps(0, 12.1), gps(0, Double.NaN), gps(0).copy(accuracyMeters = 30.01),
            gps(0).copy(accuracyMeters = -1.0), gps(0, error = 0.51), gps(0, error = Double.NaN))
        for (fix in invalid) {
            val p = policy(RunMode.OUTDOOR, false)
            for (ms in 0L..10_000L step 1_000) {
                p.observeGps(ms, fix.copy(monotonicMs = ms))
                assertEquals(AutoPauseDecision.NONE, active(p, ms))
            }
        }
    }

    @Test fun gpsResumeRequiresDistinctConfidentFixesAndStepsOverrideStationaryGps() {
        for (speed in listOf(1.19, 1.2)) {
            val p = policy(RunMode.OUTDOOR, false)
            for (ms in 0L..2_000L step 1_000) {
                p.observeGps(ms, gps(ms, speed))
                assertEquals(if (speed == 1.2 && ms == 2_000L) AutoPauseDecision.RESUME else AutoPauseDecision.NONE, paused(p, ms))
            }
        }
        val p = policy(RunMode.OUTDOOR)
        p.observeGps(0, gps(0)); p.observeStep(0, 0); paused(p, 0)
        p.observeGps(1_000, gps(1_000)); p.observeStep(1_000, 1_000)
        assertEquals(AutoPauseDecision.RESUME, paused(p, 1_000))
    }

    @Test fun pollGapPermissionChangesAndEpochResetRequireFreshArming() {
        val p = policy(); arm(p)
        assertEquals(AutoPauseDecision.NONE, active(p, 5_000))
        for (ms in 6_000L..12_000L step 1_000) assertEquals(AutoPauseDecision.NONE, active(p, ms))
        p.detectorAvailability(false, 12_000); p.detectorAvailability(true, 12_000)
        p.observeStep(12_000, 10_000); p.observeStep(12_000, 11_000)
        for (ms in 12_000L..20_000L step 1_000) assertEquals(AutoPauseDecision.NONE, active(p, ms))
    }

    @Test fun manualInterruptedDisabledAndFinishedNeverTransition() {
        for (state in listOf(RunState.PAUSED, RunState.FINISHED)) {
            for (reason in listOf(PauseReason.MANUAL, PauseReason.INTERRUPTED, null)) {
                val p = policy(RunMode.OUTDOOR)
                for (ms in 0L..8_000L step 1_000) {
                    p.observeStep(ms, ms); p.observeGps(ms, gps(ms, 2.0))
                    assertEquals(AutoPauseDecision.NONE, p.evaluate(ms, state, reason, true))
                }
            }
        }
        val p = policy(); arm(p)
        for (ms in 2_000L..10_000L step 1_000)
            assertEquals(AutoPauseDecision.NONE, p.evaluate(ms, RunState.RUNNING, null, false))
    }
}
