package com.example.runningapp.domain

import org.junit.Assert.*
import org.junit.Test

class AutoPausePolicyTest {
    private fun gps(ms: Long, speed: Double) = MotionGps(ms, speed, 5.0, 0.2)

    @Test fun healthySilentStepsNeverProveStillness() {
        val policy = AutoPausePolicy()
        for (ms in 0L..20_000L step 1_000L) {
            val result = policy.evaluate(ms, RunState.RUNNING, null, true, true,
                MotionSteps(ms, if (ms < 3_000) 100 else 101))
            assertEquals(AutoPauseDecision.NONE, result)
        }
    }

    @Test fun gpsAloneRequiresFreshAccurateSlowEvidenceAndRestartsAfterGap() {
        val policy = AutoPausePolicy()
        for (ms in 0L..4_000L step 1_000L) {
            assertEquals(AutoPauseDecision.NONE,
                policy.evaluate(ms, RunState.RUNNING, null, true, false, gps = gps(ms, 0.3)))
        }
        assertEquals(AutoPauseDecision.NONE,
            policy.evaluate(5_000, RunState.RUNNING, null, true, false, gps = gps(0, 0.0)))
        for (ms in 6_000L..11_000L step 1_000L) {
            assertEquals(if (ms == 11_000L) AutoPauseDecision.PAUSE else AutoPauseDecision.NONE,
                policy.evaluate(ms, RunState.RUNNING, null, true, false, gps = gps(ms, 0.0)))
        }
    }

    @Test fun conflictingSignalsAndSpeedDeadBandCannotPause() {
        for (speed in listOf(0.31, 0.9, 1.0)) {
            val policy = AutoPausePolicy()
            for (ms in 0L..10_000L step 1_000L) {
                assertEquals(AutoPauseDecision.NONE,
                    policy.evaluate(ms, RunState.RUNNING, null, true, true, gps = gps(ms, speed)))
            }
        }
    }

    @Test fun staleInvalidOrMissingGpsIsUnavailable() {
        val invalid = listOf(gps(0, Double.NaN), gps(0, 13.0), gps(0, -1.0),
            gps(0, 0.0).copy(accuracyMeters = 10.01),
            gps(0, 0.0).copy(speedAccuracyMetersPerSecond = 0.51))
        for (fix in invalid) {
            val policy = AutoPausePolicy()
            for (ms in 0L..6_000L step 1_000L) {
                assertEquals(AutoPauseDecision.NONE,
                    policy.evaluate(ms, RunState.RUNNING, null, true, false, gps = fix.copy(monotonicMs = ms)))
            }
        }
    }

    @Test fun freshGpsResumesWithoutWaitingForCounterAndConflictingStepsCannotResume() {
        val policy = AutoPausePolicy()
        for (ms in 0L..2_000L step 1_000L) {
            assertEquals(if (ms == 2_000L) AutoPauseDecision.RESUME else AutoPauseDecision.NONE,
                policy.evaluate(ms, RunState.PAUSED, PauseReason.AUTOMATIC, true, true,
                    MotionSteps(ms, ms / 1_000), gps(ms, 1.0)))
        }
        val stationaryGps = AutoPausePolicy()
        for (ms in 0L..6_000L step 1_000L) {
            assertEquals(AutoPauseDecision.NONE,
                stationaryGps.evaluate(ms, RunState.PAUSED, PauseReason.AUTOMATIC, true, true,
                    MotionSteps(ms, ms / 1_000), gps(ms, 0.0)))
        }
    }

    @Test fun isolatedBatchManualPauseAndRecoveryNeverAutoResume() {
        for (reason in PauseReason.entries) {
            val policy = AutoPausePolicy()
            for (ms in 0L..8_000L step 1_000L) {
                assertEquals(AutoPauseDecision.NONE,
                    policy.evaluate(ms, RunState.PAUSED, reason, true, true,
                        MotionSteps(ms, if (ms == 0L) 0 else 100)))
            }
        }
        for (reason in listOf(PauseReason.MANUAL, PauseReason.INTERRUPTED, null)) {
            val policy = AutoPausePolicy()
            for (ms in 0L..5_000L step 1_000L) {
                assertEquals(AutoPauseDecision.NONE,
                    policy.evaluate(ms, RunState.PAUSED, reason, true, false, gps = gps(ms, 2.0)))
            }
        }
    }

    @Test fun sourceChangesDelayedPollingAndDisabledPolicyResetDwell() {
        val policy = AutoPausePolicy()
        for (ms in 0L..4_000L step 1_000L) policy.evaluate(ms, RunState.RUNNING, null, true, true)
        assertEquals(AutoPauseDecision.NONE,
            policy.evaluate(5_000, RunState.RUNNING, null, true, false, gps = gps(5_000, 0.0)))
        assertEquals(AutoPauseDecision.NONE,
            policy.evaluate(20_000, RunState.RUNNING, null, true, true))
        assertEquals(AutoPauseDecision.NONE,
            policy.evaluate(21_000, RunState.RUNNING, null, false, true))
        assertEquals(AutoPauseDecision.NONE,
            policy.evaluate(22_000, RunState.RUNNING, null, true, true))
    }

    @Test fun freshQuietWindowsPauseButStepsBetweenTicksVetoStillness() {
        for (withStep in listOf(false, true)) {
            val policy = AutoPausePolicy()
            for (ms in 1_000L..6_000L step 1_000L) {
                if (withStep) policy.observeStep(ms, ms - 500)
                val result = policy.evaluate(ms, RunState.RUNNING, null, true, false,
                    acceleration = MotionWindow(ms - 1_000, ms, MotionState.STATIONARY))
                assertEquals(if (!withStep && ms == 6_000L) AutoPauseDecision.PAUSE else AutoPauseDecision.NONE, result)
            }
        }
    }

    @Test fun staleUnknownAndIncompleteWindowsCannotPause() {
        for (window in listOf(MotionWindow(0, 1_000, MotionState.STATIONARY),
            MotionWindow(0, 1_000, MotionState.UNKNOWN), MotionWindow(900, 1_000, MotionState.STATIONARY))) {
            val policy = AutoPausePolicy()
            for (ms in 1_000L..12_000L step 1_000L) assertEquals(AutoPauseDecision.NONE,
                policy.evaluate(ms, RunState.RUNNING, null, true, true, acceleration = window))
        }
    }

    @Test fun counterCallbacksBetweenTicksAreRetainedAndResetDoesNotInventMovement() {
        val policy = AutoPausePolicy()
        policy.observeSteps(0, MotionSteps(0, 100))
        for (ms in 1_000L..3_000L step 1_000L) {
            policy.observeSteps(ms, MotionSteps(ms - 500, 100 + ms / 500 - 1))
            policy.observeSteps(ms, MotionSteps(ms, 100 + ms / 500))
            assertEquals(if (ms == 3_000L) AutoPauseDecision.RESUME else AutoPauseDecision.NONE,
                policy.evaluate(ms, RunState.PAUSED, PauseReason.AUTOMATIC, true, true))
        }
        policy.reset()
        policy.observeSteps(4_000, MotionSteps(4_000, 999))
        policy.observeSteps(4_500, MotionSteps(4_500, 0))
        policy.observeSteps(4_500, MotionSteps(4_000, 999))
        assertEquals(AutoPauseDecision.NONE,
            policy.evaluate(4_500, RunState.PAUSED, PauseReason.AUTOMATIC, true, true))
    }
}
