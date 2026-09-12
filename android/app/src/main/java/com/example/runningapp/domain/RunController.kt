package com.example.runningapp.domain

import kotlin.math.roundToLong

/**
 * One in-memory run, called serially by its owner. No Android, scheduling, IO or media APIs.
 * Call tick to advance time (including countdown and time goals). Commands also advance time.
 * Measurements may arrive after ticks, but must be ordered within the current segment and
 * no later than the injected clock. Pause/finish close the segment: later deliveries are ignored.
 */
class RunController(
    private val runId: String,
    private val settings: RunSettings,
    private val clock: RunClock,
) {
    init { require(runId.isNotBlank()) }

    private var state = RunState.READY
    private var lastNow: Long? = null
    private var lastUtc = 0L
    private var epoch = 0
    private val activeIntervals = mutableListOf<ActiveInterval>()
    private var countdownEnd = 0L
    private var activeMs = 0L
    private var startedUtcMs: Long? = null
    private var endedUtcMs: Long? = null
    private var distanceMeters = 0.0
    private var goalReached = false
    private var eventSequence = 0L
    private var selectedSource: DistanceSource? = null
    private var currentSegment: MeasurementSegment? = null
    private var baseline: RunMeasurement? = null
    private val segments = mutableListOf<MeasurementSegment>()
    private val splits = mutableListOf<FullSplit>()

    fun start(): RunUpdate = update { now, events ->
        if (state == RunState.READY) {
            countdownEnd = Math.addExact(now.monotonicMs, settings.countdownSeconds * 1_000L)
            state = RunState.COUNTDOWN
            advanceCountdown(now, events)
        }
    }

    fun tick(): RunUpdate = update { _, _ -> }

    fun pause(): RunUpdate = update { now, events ->
        if (state == RunState.RUNNING) {
            closeSegment(now.monotonicMs)
            closeActive(now)
            state = RunState.PAUSED
            emit(events, RunEventType.PAUSED)
        }
    }

    fun resume(): RunUpdate = update { now, events ->
        if (state == RunState.PAUSED) {
            state = RunState.RUNNING
            openActive(now)
            selectedSource?.let { openSegment(it, now.monotonicMs) }
            emit(events, RunEventType.RESUMED)
        }
    }

    fun finish(): RunUpdate = update { now, events ->
        if (state == RunState.RUNNING || state == RunState.PAUSED) {
            closeSegment(now.monotonicMs)
            closeActive(now)
            state = RunState.FINISHED
            endedUtcMs = now.utcMs
            emit(events, RunEventType.FINISHED)
        }
    }

    /** Selection is explicit; a future adapter decides GPS availability independently of network. */
    fun selectSource(source: DistanceSource): RunUpdate {
        require(source != DistanceSource.GPS || settings.mode == RunMode.OUTDOOR)
        require(source != DistanceSource.STEPS || settings.strideLengthMeters != null)
        return update { now, _ ->
            if (state == RunState.RUNNING && currentSegment?.source != source) {
                closeSegment(now.monotonicMs)
                selectedSource = source
                openSegment(source, now.monotonicMs)
            }
        }
    }

    fun record(measurement: RunMeasurement): RunUpdate = update { now, events ->
        val segment = currentSegment
        val previous = baseline
        if (state == RunState.RUNNING && segment != null &&
            measurement.segmentId == segment.id &&
            measurement.monotonicMs >= segment.startedMonotonicMs &&
            measurement.monotonicMs <= now.monotonicMs &&
            (previous == null || measurement.monotonicMs > previous.monotonicMs) &&
            matchesSource(measurement, segment.source)
        ) {
            // The first sample, or a decreasing counter, establishes a baseline only.
            val delta = when {
                measurement is RunMeasurement.Gps && previous is RunMeasurement.Gps ->
                    (measurement.cumulativeMeters - previous.cumulativeMeters).coerceAtLeast(0.0)
                measurement is RunMeasurement.Steps && previous is RunMeasurement.Steps ->
                    (measurement.cumulativeSteps - previous.cumulativeSteps).coerceAtLeast(0L) *
                        requireNotNull(settings.strideLengthMeters)
                else -> 0.0
            }
            // An unusable numeric sample must not poison either totals or the valid baseline.
            if (delta.isFinite() && (distanceMeters + delta).isFinite()) {
                if (previous != null && delta > 0) {
                    val fromActive = segment.startedActiveMs +
                        (previous.monotonicMs - segment.startedMonotonicMs)
                    val toActive = segment.startedActiveMs +
                        (measurement.monotonicMs - segment.startedMonotonicMs)
                    addDistance(delta, fromActive, toActive, events)
                    currentSegment = segment.copy(distanceMeters = segment.distanceMeters + delta)
                }
                baseline = measurement
            }
        }
    }

    fun clearSource(): RunUpdate = update { now, _ ->
        closeSegment(now.monotonicMs)
        selectedSource = null
    }

    fun checkpoint(): RunCheckpoint = RunCheckpoint(snapshot(), lastNow ?: 0, lastUtc, eventSequence, baseline, epoch)

    companion object {
        /** Recovery never bridges a process interruption or a device reboot. No cues are replayed. */
        fun recover(saved: RunCheckpoint, clock: RunClock): RunController {
            val s = saved.snapshot
            require(s.state in listOf(RunState.RUNNING, RunState.PAUSED, RunState.FINISHED))
            return RunController(s.runId, s.settings, clock).apply {
                state = if (s.state == RunState.FINISHED) RunState.FINISHED else RunState.PAUSED
                activeMs = s.activeDurationMs
                distanceMeters = s.distanceMeters
                startedUtcMs = s.startedUtcMs
                endedUtcMs = s.endedUtcMs
                goalReached = s.goalReached
                eventSequence = saved.eventSequence
                epoch = saved.epoch + 1
                segments += s.segments.map { it.copy(endedMonotonicMs = it.endedMonotonicMs ?: saved.lastMonotonicMs) }
                splits += s.splits
                activeIntervals += s.activeIntervals.map {
                    if (it.endMonotonicMs != null) it else it.copy(
                        endMonotonicMs = saved.lastMonotonicMs, endUtcMs = saved.lastUtcMs, endActiveMs = activeMs,
                    )
                }
                // Leave lastNow unset: monotonic values from the previous process/boot are metadata only.
                lastUtc = saved.lastUtcMs
            }
        }
    }

    /** A detached value at the most recent command/tick time; this method does not read the clock. */
    fun snapshot(): RunSnapshot = RunSnapshot(
        runId, settings, state,
        if (state == RunState.COUNTDOWN) countdownEnd - requireNotNull(lastNow) else 0,
        startedUtcMs, endedUtcMs, activeMs, distanceMeters,
        if (distanceMeters > 0) (activeMs.toDouble() / distanceMeters * settings.units.metersPerUnit)
            .takeIf { it.isFinite() } else null,
        goalReached, currentSegment?.id,
        segments.toList() + listOfNotNull(currentSegment), splits.toList(), activeIntervals.toList(),
    )

    private fun update(action: (RunTime, MutableList<RunEvent>) -> Unit): RunUpdate {
        // Finished is immutable, including when a future player sends another resume command.
        if (state == RunState.FINISHED) return RunUpdate(snapshot(), emptyList())
        val now = clock.read()
        require(now.monotonicMs >= 0 && (lastNow == null || now.monotonicMs >= lastNow!!)) {
            "The monotonic clock must not move backwards"
        }
        val events = mutableListOf<RunEvent>()
        if (state == RunState.RUNNING) {
            activeMs = Math.addExact(activeMs, now.monotonicMs - requireNotNull(lastNow))
        }
        lastNow = now.monotonicMs
        lastUtc = now.utcMs
        advanceCountdown(now, events)
        checkGoal(events)
        action(now, events)
        checkGoal(events)
        return RunUpdate(snapshot(), events.toList())
    }

    private fun advanceCountdown(now: RunTime, events: MutableList<RunEvent>) {
        if (state == RunState.COUNTDOWN && now.monotonicMs >= countdownEnd) {
            state = RunState.RUNNING
            activeMs = now.monotonicMs - countdownEnd
            startedUtcMs = now.utcMs - activeMs
            openActive(RunTime(countdownEnd, requireNotNull(startedUtcMs)), 0)
            emit(events, RunEventType.STARTED, 0, 0.0)
        }
    }

    private fun openActive(now: RunTime, startActive: Long = activeMs) {
        activeIntervals += ActiveInterval(activeIntervals.size + 1, epoch, now.monotonicMs, null, now.utcMs, null, startActive, null)
    }

    private fun closeActive(now: RunTime) {
        val last = activeIntervals.lastOrNull() ?: return
        if (last.endMonotonicMs == null) activeIntervals[activeIntervals.lastIndex] = last.copy(
            endMonotonicMs = now.monotonicMs, endUtcMs = now.utcMs, endActiveMs = activeMs,
        )
    }

    private fun openSegment(source: DistanceSource, nowMs: Long) {
        currentSegment = MeasurementSegment(segments.size.toLong() + 1, source, nowMs, activeMs, null, 0.0)
        baseline = null
    }

    private fun closeSegment(nowMs: Long) {
        currentSegment?.let { segments += it.copy(endedMonotonicMs = nowMs) }
        currentSegment = null
        baseline = null
    }

    private fun matchesSource(measurement: RunMeasurement, source: DistanceSource): Boolean =
        when (measurement) {
            is RunMeasurement.Gps -> source == DistanceSource.GPS
            is RunMeasurement.Steps -> source == DistanceSource.STEPS
        }

    private fun addDistance(delta: Double, fromActive: Long, toActive: Long, events: MutableList<RunEvent>) {
        val oldDistance = distanceMeters
        distanceMeters += delta
        val unit = settings.units.metersPerUnit
        var boundary = (splits.size.toDouble() + 1) * unit
        while (boundary <= distanceMeters) {
            // Linear interpolation only inside this accepted interval, never across a pause/gap.
            val fraction = ((boundary - oldDistance) / delta).coerceIn(0.0, 1.0)
            val endActive = fromActive + ((toActive - fromActive) * fraction).roundToLong()
            val split = FullSplit(
                splits.size + 1, settings.units, boundary, endActive,
                endActive - (splits.lastOrNull()?.endActiveMs ?: 0),
            )
            splits += split
            emit(events, RunEventType.SPLIT_COMPLETED, endActive, boundary, split)
            boundary = (splits.size.toDouble() + 1) * unit
        }
    }

    private fun checkGoal(events: MutableList<RunEvent>) {
        if (goalReached || state != RunState.RUNNING) return
        val reached = when (val goal = settings.goal) {
            RunGoal.None -> false
            is RunGoal.Distance -> distanceMeters >= goal.meters
            is RunGoal.Time -> activeMs >= goal.durationMs
        }
        if (reached) {
            goalReached = true
            emit(events, RunEventType.GOAL_REACHED)
        }
    }

    private fun emit(
        events: MutableList<RunEvent>, type: RunEventType,
        timeMs: Long = activeMs, meters: Double = distanceMeters, split: FullSplit? = null,
    ) {
        events += RunEvent(RunEventId(runId, ++eventSequence), type, timeMs, meters, split)
    }
}
