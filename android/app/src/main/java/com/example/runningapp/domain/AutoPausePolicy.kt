package com.example.runningapp.domain

/** Speed must be measured by the adapter, never inferred from network or callback silence. */
data class MotionGps(
    val monotonicMs: Long, val speedMetersPerSecond: Double,
    val accuracyMeters: Double, val speedAccuracyMetersPerSecond: Double,
)

data class MotionSteps(val monotonicMs: Long, val cumulativeSteps: Long)

enum class AutoPauseDecision { NONE, PAUSE, RESUME }

/** Ephemeral evidence: create/reset for each run, recovery, registration or manual transition. */
class AutoPausePolicy {
    private var lastNow: Long? = null
    private var stepBaseline: MotionSteps? = null
    private val stepChanges = ArrayDeque<Long>()
    private var sources = 0
    private var candidateSince: Long? = null
    private var candidate: AutoPauseDecision = AutoPauseDecision.NONE
    private var previousState: Pair<RunState, PauseReason?>? = null

    fun observeSteps(nowMs: Long, steps: MotionSteps) {
        if (steps.cumulativeSteps < 0 || nowMs - steps.monotonicMs !in 0..STEP_WINDOW_MS) return
        val prior = stepBaseline
        if (prior != null && steps.monotonicMs <= prior.monotonicMs) return
        if (prior != null && steps.cumulativeSteps < prior.cumulativeSteps) {
            stepChanges.clear()
            candidateSince = null
        } else if (prior != null && steps.cumulativeSteps > prior.cumulativeSteps) {
            observeStep(nowMs, steps.monotonicMs)
        }
        stepBaseline = steps
    }

    fun observeStep(nowMs: Long, timeMs: Long) {
        if (nowMs - timeMs !in 0..STEP_WINDOW_MS ||
            stepChanges.lastOrNull()?.let { timeMs <= it } == true) return
        stepChanges.addLast(timeMs)
        while (stepChanges.size > 32) stepChanges.removeFirst()
        // Positive movement between timer ticks also interrupts a stillness candidate.
        if (candidate == AutoPauseDecision.PAUSE) candidateSince = null
    }

    fun observeAcceleration(window: MotionWindow) {
        val complete = window.monotonicMs - window.startedMs in 900..1_000
        if (!complete || (candidate == AutoPauseDecision.PAUSE && window.state != MotionState.STATIONARY) ||
            (candidate == AutoPauseDecision.RESUME && window.state != MotionState.MOVING)) candidateSince = null
    }

    fun reset() {
        lastNow = null
        stepBaseline = null
        stepChanges.clear()
        sources = 0
        candidateSince = null
        candidate = AutoPauseDecision.NONE
        previousState = null
    }

    /** Call at least once per second. Registration and callback silence never prove stillness. */
    fun evaluate(
        nowMs: Long, state: RunState, pauseReason: PauseReason?, enabled: Boolean,
        stepsHealthy: Boolean, steps: MotionSteps? = null, gps: MotionGps? = null,
        acceleration: MotionWindow? = null,
    ): AutoPauseDecision {
        require(nowMs >= 0)
        val previousNow = lastNow
        if (previousNow != null && (nowMs < previousNow || nowMs - previousNow > MAX_POLL_GAP_MS)) reset()
        lastNow = nowMs
        val stateKey = state to pauseReason
        if (!enabled || (state != RunState.RUNNING &&
                !(state == RunState.PAUSED && pauseReason == PauseReason.AUTOMATIC))) {
            reset()
            return AutoPauseDecision.NONE
        }
        if (previousState != stateKey) {
            candidateSince = null
            candidate = AutoPauseDecision.NONE
        }
        previousState = stateKey

        if (!stepsHealthy) {
            stepBaseline = null
        } else if (steps != null) observeSteps(nowMs, steps)
        while (stepChanges.isNotEmpty() && nowMs - stepChanges.first() > STEP_WINDOW_MS) stepChanges.removeFirst()
        val gpsUsable = gps != null && nowMs - gps.monotonicMs in 0..GPS_FRESH_MS &&
            gps.accuracyMeters.isFinite() && gps.accuracyMeters in 0.0..10.0 &&
            gps.speedAccuracyMetersPerSecond.isFinite() && gps.speedAccuracyMetersPerSecond in 0.0..0.5 &&
            gps.speedMetersPerSecond.isFinite() && gps.speedMetersPerSecond in 0.0..12.0
        val accelerationUsable = acceleration != null &&
            nowMs - acceleration.monotonicMs in 0..1_000 &&
            acceleration.monotonicMs - acceleration.startedMs in 900..1_000
        val currentSources = (if (accelerationUsable) 1 else 0) + (if (gpsUsable) 2 else 0)
        if (sources != currentSources) candidateSince = null
        sources = currentSources
        val sustainedSteps = stepChanges.size >= 2 &&
            stepChanges.last() - stepChanges.first() >= 500 && nowMs - stepChanges.last() <= 1_000
        val stationary = currentSources != 0 && stepChanges.isEmpty() &&
            (!accelerationUsable || acceleration!!.state == MotionState.STATIONARY) &&
            (!gpsUsable || gps!!.speedMetersPerSecond <= 0.3)
        val moving = (currentSources != 0 || sustainedSteps) &&
            (!accelerationUsable || acceleration!!.state == MotionState.MOVING) &&
            (!gpsUsable || gps!!.speedMetersPerSecond >= 1.0)
        val next = when {
            state == RunState.RUNNING && stationary -> AutoPauseDecision.PAUSE
            state == RunState.PAUSED && moving -> AutoPauseDecision.RESUME
            else -> AutoPauseDecision.NONE
        }
        if (next != candidate || next == AutoPauseDecision.NONE) candidateSince = null
        candidate = next
        if (next == AutoPauseDecision.NONE) return next
        val since = candidateSince ?: nowMs.also { candidateSince = it }
        val threshold = if (next == AutoPauseDecision.PAUSE) 5_000 else 2_000
        return if (nowMs - since >= threshold) next else AutoPauseDecision.NONE
    }

    companion object {
        const val GPS_FRESH_MS = 2_500L
        const val STEP_WINDOW_MS = 3_000L
        const val MAX_POLL_GAP_MS = 1_500L
    }
}
