package com.example.runningapp.domain

import kotlin.math.sqrt

enum class MotionState { UNKNOWN, STATIONARY, MOVING }

data class MotionWindow(val startedMs: Long, val monotonicMs: Long, val state: MotionState)

/** One second of bounded, gravity-independent vector variation; never distance. */
class AccelerationWindow {
    private data class Sample(val time: Long, val x: Double, val y: Double, val z: Double)
    private val samples = ArrayDeque<Sample>()
    private var lastEmission: Long? = null

    fun add(timeMs: Long, x: Double, y: Double, z: Double): MotionWindow? {
        if (timeMs < 0 || !x.isFinite() || !y.isFinite() || !z.isFinite()) {
            samples.clear()
            return null
        }
        val previous = samples.lastOrNull()
        if (previous != null && timeMs <= previous.time) return null
        if (previous != null && timeMs - previous.time > 150) samples.clear()
        samples.addLast(Sample(timeMs, x, y, z))
        while (samples.size > 32 || timeMs - samples.first().time > 1_000) samples.removeFirst()
        if (lastEmission?.let { timeMs - it < 500 } == true) return null
        lastEmission = timeMs
        val start = samples.first().time
        if (timeMs - start < 900 || samples.size < 15)
            return MotionWindow(start, timeMs, MotionState.UNKNOWN)
        val mx = samples.map { it.x }.average()
        val my = samples.map { it.y }.average()
        val mz = samples.map { it.z }.average()
        val rms = sqrt(samples.sumOf {
            (it.x - mx) * (it.x - mx) + (it.y - my) * (it.y - my) + (it.z - mz) * (it.z - mz)
        } / samples.size)
        // Conservative initial thresholds in m/s^2; carried-phone acceptance is required.
        val state = when {
            rms <= 0.12 -> MotionState.STATIONARY
            rms >= 0.8 -> MotionState.MOVING
            else -> MotionState.UNKNOWN
        }
        return MotionWindow(start, timeMs, state)
    }
}
