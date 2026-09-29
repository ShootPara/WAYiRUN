package com.example.runningapp.domain

import org.junit.Assert.*
import org.junit.Test

class AccelerationWindowTest {
    @Test fun completeQuietAndMovingTracesClassifyWithoutGravityOrientationDependence() {
        for (moving in listOf(false, true)) {
            val window = AccelerationWindow()
            var result: MotionWindow? = null
            for (ms in 0L..2_000L step 50L) {
                val x = if (moving) (if (ms % 100 == 0L) 2.0 else -2.0) else 0.0
                result = window.add(ms, x, 9.81, 0.0) ?: result
            }
            assertEquals(if (moving) MotionState.MOVING else MotionState.STATIONARY, result!!.state)
            assertEquals(1_000L, result.monotonicMs - result.startedMs)
        }
    }

    @Test fun sparseMissingInvalidAndAmbiguousSamplesNeverProveStillness() {
        val sparse = AccelerationWindow()
        for (ms in 0L..10_000L step 500L)
            assertEquals(MotionState.UNKNOWN, sparse.add(ms, 0.0, 0.0, 9.81)!!.state)
        val window = AccelerationWindow()
        for (ms in 0L..1_000L step 50L) window.add(ms, 0.0, 0.0, 9.81)
        assertNull(window.add(1_000, 0.0, 0.0, 9.81))
        assertEquals(MotionState.UNKNOWN, window.add(2_000, 0.0, 0.0, 9.81)!!.state)
        assertNull(window.add(2_050, Double.NaN, 0.0, 0.0))
        var result: MotionWindow? = null
        for (ms in 2_100L..4_000L step 50L)
            result = window.add(ms, if (ms % 100 == 0L) 0.3 else -0.3, 0.0, 9.81) ?: result
        assertEquals(MotionState.UNKNOWN, result!!.state)
    }
}
