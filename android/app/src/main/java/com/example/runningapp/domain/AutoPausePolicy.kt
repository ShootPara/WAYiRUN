package com.example.runningapp.domain

/** Reported speed, never coordinate differencing or callback silence. */
data class MotionGps(
    val monotonicMs: Long, val speedMetersPerSecond: Double,
    val accuracyMeters: Double, val speedAccuracyMetersPerSecond: Double,
)

enum class AutoPauseDecision { NONE, PAUSE, RESUME }

/** Ephemeral detector evidence; cumulative distance counters never enter this policy. */
class AutoPausePolicy(private val mode: RunMode = RunMode.INDOOR) {
    private var detectorAvailable = false
    private var armed = false
    private var epochMs = 0L
    private var lastTick: Long? = null
    private var lastStepTime: Long? = null
    private var lastStepReceipt: Long? = null
    private val steps = ArrayDeque<Long>()
    private var lastGpsTime: Long? = null
    private var gpsStopSince: Long? = null
    private var gpsMoveSince: Long? = null

    fun detectorAvailability(available: Boolean, nowMs: Long) {
        if (detectorAvailable != available) {
            detectorAvailable = available
            reset(nowMs)
        }
    }

    fun reset(nowMs: Long = 0, keepArming: Boolean = false) {
        armed = keepArming && armed && detectorAvailable
        epochMs = nowMs
        lastTick = nowMs
        lastStepTime = null
        lastStepReceipt = if (armed) nowMs else null
        steps.clear()
        lastGpsTime = null
        gpsStopSince = null
        gpsMoveSince = null
    }

    fun observeStep(nowMs: Long, timeMs: Long, receivedMs: Long = nowMs) {
        if (!detectorAvailable || timeMs < epochMs || timeMs > receivedMs || receivedMs > nowMs ||
            lastStepTime?.let { timeMs <= it } == true) return
        lastStepTime = timeMs
        if (receivedMs - timeMs > MAX_EVENT_AGE_MS || nowMs - receivedMs > MAX_EVENT_AGE_MS) {
            armed = false
            steps.clear()
            lastStepReceipt = null
            return
        }
        lastStepReceipt = receivedMs
        steps.addLast(timeMs)
        while (steps.size > 32 || timeMs - steps.first() > PAUSE_MS) steps.removeFirst()
        if (steps.size >= 2) armed = true
    }

    /** Consume every fix, including invalid fixes between timer ticks. */
    fun observeGps(nowMs: Long, gps: MotionGps) {
        if (mode != RunMode.OUTDOOR || gps.monotonicMs < epochMs ||
            lastGpsTime?.let { gps.monotonicMs <= it } == true) return
        val valid = nowMs - gps.monotonicMs in 0..GPS_FRESH_MS &&
            gps.accuracyMeters.isFinite() && gps.accuracyMeters in 0.0..30.0 &&
            gps.speedAccuracyMetersPerSecond.isFinite() && gps.speedAccuracyMetersPerSecond in 0.0..0.5 &&
            gps.speedMetersPerSecond.isFinite() && gps.speedMetersPerSecond in 0.0..12.0
        if (!valid) {
            gpsStopSince = null
            gpsMoveSince = null
            if (gps.monotonicMs <= nowMs) lastGpsTime = gps.monotonicMs
            return
        }
        val gap = lastGpsTime?.let { gps.monotonicMs - it > GPS_FRESH_MS } ?: true
        if (gap) { gpsStopSince = null; gpsMoveSince = null }
        lastGpsTime = gps.monotonicMs
        if (gps.speedMetersPerSecond + gps.speedAccuracyMetersPerSecond <= 0.5) {
            if (gpsStopSince == null) gpsStopSince = gps.monotonicMs
        } else gpsStopSince = null
        if (gps.speedMetersPerSecond - gps.speedAccuracyMetersPerSecond >= 1.0) {
            if (gpsMoveSince == null) gpsMoveSince = gps.monotonicMs
        } else gpsMoveSince = null
    }

    fun evaluate(nowMs: Long, state: RunState, pauseReason: PauseReason?, enabled: Boolean,
        timerTick: Boolean = true): AutoPauseDecision {
        require(nowMs >= 0)
        if (!enabled || (state != RunState.RUNNING &&
                !(state == RunState.PAUSED && pauseReason == PauseReason.AUTOMATIC))) {
            reset(nowMs)
            return AutoPauseDecision.NONE
        }
        if (lastTick?.let { nowMs < it || nowMs - it > MAX_POLL_GAP_MS } == true) reset(nowMs)
        if (timerTick) lastTick = nowMs
        val gpsFresh = lastGpsTime?.let { nowMs - it in 0..GPS_FRESH_MS } == true
        if (!gpsFresh) { gpsStopSince = null; gpsMoveSince = null }
        if (state == RunState.RUNNING) {
            val detectorQuiet = armed && lastStepReceipt?.let { nowMs - it >= PAUSE_MS } == true
            val gpsStopped = gpsFresh && gpsStopSince?.let { lastGpsTime!! - it >= PAUSE_MS } == true
            val stopped = if (mode == RunMode.INDOOR) detectorAvailable && detectorQuiet
                else gpsStopped && (!detectorAvailable || detectorQuiet)
            return if (stopped) AutoPauseDecision.PAUSE else AutoPauseDecision.NONE
        }
        val recentPair = detectorAvailable && steps.size >= 2 &&
            steps.last() - steps.elementAt(steps.size - 2) <= RESUME_MS &&
            nowMs - steps.last() in 0..MAX_EVENT_AGE_MS
        val gpsMoving = mode == RunMode.OUTDOOR && gpsFresh &&
            gpsMoveSince?.let { lastGpsTime!! - it >= RESUME_MS } == true
        return if (recentPair || gpsMoving) AutoPauseDecision.RESUME else AutoPauseDecision.NONE
    }

    companion object {
        const val PAUSE_MS = 5_000L
        const val RESUME_MS = 2_000L
        const val MAX_EVENT_AGE_MS = 2_500L
        const val GPS_FRESH_MS = 2_500L
        const val MAX_POLL_GAP_MS = 2_500L
    }
}
