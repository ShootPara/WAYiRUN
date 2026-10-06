package com.example.runningapp.storage

import com.example.runningapp.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.ZoneId
import kotlin.math.abs

@Serializable
data class RunArchive(
    val version: Int = 1, val run: StoredRun, val route: List<RoutePoint>,
    val measurements: List<StoredMeasurement>, val splits: List<StoredSplit>,
    val intervals: List<StoredActiveInterval>, val segments: List<StoredSegment>,
) {
    fun validate() {
        require(version == 1 && run.state == "FINISHED" && run.activeSlot == null)
        require(run.id.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
        ZoneId.of(run.zoneId); require(run.startOffsetSeconds in -64800..64800)
        val cp = run.decode(); val s = cp.snapshot
        require(s.runId == run.id && s.state == RunState.FINISHED && s.startedUtcMs != null && s.endedUtcMs != null)
        require(s.startedUtcMs >= 0 && s.endedUtcMs >= 0 && s.activeDurationMs >= 0)
        require(s.distanceMeters.isFinite() && s.distanceMeters >= 0)
        require(s.averagePaceMsPerUnit == null || (s.averagePaceMsPerUnit.isFinite() && s.averagePaceMsPerUnit >= 0))
        val expectedPace = if (s.distanceMeters > 0) s.activeDurationMs / s.distanceMeters * s.settings.units.metersPerUnit else null
        require(expectedPace == null && s.averagePaceMsPerUnit == null ||
            expectedPace != null && s.averagePaceMsPerUnit != null && abs(expectedPace - s.averagePaceMsPerUnit) < 0.001)
        require(s.segments.all { it.distanceMeters.isFinite() && it.distanceMeters >= 0 })
        require(s.activeIntervals.all { it.endActiveMs != null && it.endActiveMs >= it.startActiveMs && it.endActiveMs <= s.activeDurationMs })
        require(intervals.all { it.runId == run.id } && segments.all { it.runId == run.id })
        require(intervals.map { Json.decodeFromString<ActiveInterval>(it.value) } == s.activeIntervals)
        require(segments.map { Json.decodeFromString<MeasurementSegment>(it.value) } == s.segments)
        require(intervals.map { it.number } == s.activeIntervals.map { it.number })
        require(segments.map { it.number } == s.segments.map { it.id })
        require(route.map { it.id }.distinct().size == route.size && measurements.map { it.id }.distinct().size == measurements.size)
        route.forEach {
            require(it.runId == run.id && s.settings.mode == RunMode.OUTDOOR)
            require(it.latitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude.isFinite() && it.longitude in -180.0..180.0)
            require(it.accuracyMeters.isFinite() && it.accuracyMeters >= 0)
            require(s.segments.any { segment -> segment.id == it.segmentId && segment.source == DistanceSource.GPS })
        }
        measurements.forEach {
            require(it.runId == run.id && it.deltaMeters.isFinite() && it.deltaMeters >= 0 && it.totalMeters.isFinite())
            require(it.totalMeters in 0.0..(s.distanceMeters + 0.000001) && it.activeMs in 0..s.activeDurationMs)
            val reading = Json.decodeFromString<RunMeasurement>(it.reading)
            val source = if (reading is RunMeasurement.Gps) "GPS" else "STEPS"
            require(it.source == source && reading.segmentId == it.segmentId && reading.monotonicMs == it.monotonicMs)
            require(s.segments.any { segment -> segment.id == it.segmentId && segment.source.name == source })
        }
        val expected = s.splits.map { StoredSplit(s.runId, it.number, s.settings.units.metersPerUnit, it.durationMs, false) } +
            listOfNotNull(s.partialSplit()?.let { StoredSplit(s.runId, s.splits.size + 1, it.distanceMeters, it.durationMs, true) })
        require(splits == expected)
    }
    fun encode(): ByteArray {
        validate()
        return format.encodeToString(this).toByteArray(Charsets.UTF_8).also { require(it.size in 1..MAX_BYTES) }
    }
    companion object {
        const val CHUNK_BYTES = 131072
        const val MAX_BYTES = CHUNK_BYTES * 128
        private val format = Json { encodeDefaults = true }
        fun decode(bytes: ByteArray): RunArchive {
            require(bytes.size in 1..MAX_BYTES)
            return format.decodeFromString<RunArchive>(bytes.decodeToString(throwOnInvalidSequence = true)).also { it.validate() }
        }
        fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
