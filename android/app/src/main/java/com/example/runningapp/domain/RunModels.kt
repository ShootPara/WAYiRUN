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
enum class PauseReason { MANUAL, AUTOMATIC, INTERRUPTED }
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

@Serializable
enum class AnnouncementInterval(val timeMs: Long? = null, val distanceUnits: Double? = null) {
    FIVE_MINUTES(timeMs = 300_000), TEN_MINUTES(timeMs = 600_000),
    HALF_UNIT(distanceUnits = 0.5), ONE_UNIT(distanceUnits = 1.0),
}

@Serializable
data class AnnouncementSelection(
    val version: Int = 1,
    val timeEnabled: Boolean = true,
    val timeInterval: AnnouncementInterval = AnnouncementInterval.FIVE_MINUTES,
    val distanceEnabled: Boolean = false,
    val distanceInterval: AnnouncementInterval = AnnouncementInterval.ONE_UNIT,
) {
    init {
        require(version == 1)
        require(timeInterval.timeMs != null && distanceInterval.distanceUnits != null)
    }

    companion object {
        fun legacy(interval: AnnouncementInterval) = AnnouncementSelection(
            timeEnabled = interval.timeMs != null,
            timeInterval = interval.takeIf { it.timeMs != null } ?: AnnouncementInterval.FIVE_MINUTES,
            distanceEnabled = interval.distanceUnits != null,
            distanceInterval = interval.takeIf { it.distanceUnits != null } ?: AnnouncementInterval.ONE_UNIT,
        )
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
    // Missing fields in pre-announcement archives retain their original silent-interval behavior.
    val announcementsEnabled: Boolean = false,
    val announcementInterval: AnnouncementInterval = AnnouncementInterval.FIVE_MINUTES,
    // Old checkpoints retain their captured behavior; new setup explicitly defaults this on.
    val autoPauseEnabled: Boolean = false,
    val announcementSelection: AnnouncementSelection? = null,
) {
    fun effectiveAnnouncements(): AnnouncementSelection {
        val selected = announcementSelection ?: AnnouncementSelection.legacy(announcementInterval)
        return if (announcementsEnabled) selected else selected.copy(timeEnabled = false, distanceEnabled = false)
    }

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

enum class RunEventType { STARTED, PAUSED, RESUMED, FINISHED, GOAL_REACHED, SPLIT_COMPLETED, ANNOUNCEMENT }
@Serializable
data class RunEventId(val runId: String, val sequence: Long)
@Serializable
enum class AnnouncementChannel { TIME, DISTANCE }
@Serializable
data class RunEvent(
    val id: RunEventId,
    val type: RunEventType,
    val activeDurationMs: Long,
    val distanceMeters: Double,
    val split: FullSplit? = null,
    val pauseReason: PauseReason? = null,
    val occurrenceActiveMs: Long? = null,
    val announcementChannel: AnnouncementChannel? = null,
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
    val pauseReason: PauseReason? = null,
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
