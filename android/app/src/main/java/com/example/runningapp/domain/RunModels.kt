package com.example.runningapp.domain

import kotlinx.serialization.Serializable

/** Both clocks are supplied by the caller; wall time is metadata, never a duration source. */
@Serializable
data class RunTime(val monotonicMs: Long, val utcMs: Long)

fun interface RunClock {
    fun read(): RunTime
}

@Serializable

enum class RunState { READY, COUNTDOWN, RUNNING, PAUSED, FINISHED }
@Serializable
enum class RunMode { INDOOR, OUTDOOR }
@Serializable
enum class DistanceSource { GPS, STEPS }
@Serializable
enum class RunUnits(val metersPerUnit: Double) {
    KILOMETERS(1_000.0), MILES(1_609.344)
}

@Serializable

sealed interface RunGoal {
    @Serializable
    data object None : RunGoal
    @Serializable
    data class Distance(val meters: Double) : RunGoal {
        init { require(meters.isFinite() && meters > 0) }
    }
    @Serializable
    data class Time(val durationMs: Long) : RunGoal {
        init { require(durationMs > 0) }
    }
}

/** No personal stride or unit preference is inferred. Null stride permits time/GPS tracking. */
@Serializable
data class RunSettings(
    val mode: RunMode,
    val units: RunUnits,
    val countdownSeconds: Int,
    val goal: RunGoal,
    val strideLengthMeters: Double?,
) {
    init {
        require(countdownSeconds in 0..10)
        require(strideLengthMeters == null ||
            (strideLengthMeters.isFinite() && strideLengthMeters > 0))
    }
}

/** A segment token prevents queued samples from an old pause/source interval being reused. */
@Serializable
sealed interface RunMeasurement {
    val segmentId: Long
    val monotonicMs: Long

    /** Adapter-supplied cumulative distance, not coordinates or a GPS quality decision. */
    @Serializable
    data class Gps(
        override val segmentId: Long,
        override val monotonicMs: Long,
        val cumulativeMeters: Double,
    ) : RunMeasurement {
        init { require(cumulativeMeters.isFinite() && cumulativeMeters >= 0) }
    }

    @Serializable

    data class Steps(
        override val segmentId: Long,
        override val monotonicMs: Long,
        val cumulativeSteps: Long,
    ) : RunMeasurement {
        init { require(cumulativeSteps >= 0) }
    }
}

@Serializable

data class MeasurementSegment(
    val id: Long,
    val source: DistanceSource,
    val startedMonotonicMs: Long,
    val startedActiveMs: Long,
    val endedMonotonicMs: Long?,
    val distanceMeters: Double,
)

@Serializable

data class FullSplit(
    val number: Int,
    val units: RunUnits,
    val endDistanceMeters: Double,
    val endActiveMs: Long,
    val durationMs: Long,
)

@Serializable

enum class RunEventType { STARTED, PAUSED, RESUMED, FINISHED, GOAL_REACHED, SPLIT_COMPLETED }
@Serializable
data class RunEventId(val runId: String, val sequence: Long)
@Serializable
data class RunEvent(
    val id: RunEventId,
    val type: RunEventType,
    val activeDurationMs: Long,
    val distanceMeters: Double,
    val split: FullSplit? = null,
)

@Serializable

data class RunSnapshot(
    val runId: String,
    val settings: RunSettings,
    val state: RunState,
    val countdownRemainingMs: Long,
    val startedUtcMs: Long?,
    val endedUtcMs: Long?,
    val activeDurationMs: Long,
    val distanceMeters: Double,
    /** Milliseconds per selected mile/kilometer; unavailable at zero distance. */
    val averagePaceMsPerUnit: Double?,
    val goalReached: Boolean,
    val currentSegmentId: Long?,
    val segments: List<MeasurementSegment>,
    val splits: List<FullSplit>,
    val activeIntervals: List<ActiveInterval> = emptyList(),
)

@Serializable
data class ActiveInterval(
    val number: Int, val epoch: Int, val startMonotonicMs: Long, val endMonotonicMs: Long?,
    val startUtcMs: Long, val endUtcMs: Long?, val startActiveMs: Long, val endActiveMs: Long?,
)

@Serializable
data class RunCheckpoint(
    val snapshot: RunSnapshot, val lastMonotonicMs: Long, val lastUtcMs: Long,
    val eventSequence: Long, val baseline: RunMeasurement?, val epoch: Int,
)

data class PartialSplit(val distanceMeters: Double, val durationMs: Long, val paceMsPerUnit: Double)

fun RunSnapshot.partialSplit(): PartialSplit? {
    if (state != RunState.FINISHED) return null
    val remaining = distanceMeters - (splits.lastOrNull()?.endDistanceMeters ?: 0.0)
    if (remaining <= 0.000001) return null
    val duration = activeDurationMs - (splits.lastOrNull()?.endActiveMs ?: 0L)
    return PartialSplit(remaining, duration, duration / remaining * settings.units.metersPerUnit)
}

/** Only newly emitted events are returned. Reading a snapshot never replays cues. */
@Serializable
data class RunUpdate(val snapshot: RunSnapshot, val events: List<RunEvent>)
