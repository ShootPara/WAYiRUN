package com.example.runningapp.domain

import kotlin.math.*

data class GpsFix(val monotonicMs: Long, val latitude: Double, val longitude: Double, val accuracyMeters: Float)
data class TrackingResult(val update: RunUpdate, val measurement: RunMeasurement? = null, val fix: GpsFix? = null)

/** Prototype quality rules. Real route comparisons are required before claiming accuracy. */
class TrackingInput(val controller: RunController, private val clock: RunClock) {
    var stepsUsable = false
    private var lastGpsMs: Long? = null
    private var previousFix: GpsFix? = null
    private var gpsMeters = 0.0
    private var segmentId: Long? = null
    val distanceAvailable: Boolean get() = gpsFresh() || stepsUsable

    private fun gpsFresh() = lastGpsMs?.let { clock.read().monotonicMs - it in 0..10_000 } == true

    fun reset() {
        lastGpsMs = null
        previousFix = null
        segmentId = null
        gpsMeters = 0.0
    }

    fun tick(): TrackingResult {
        val events = controller.tick().events.toMutableList()
        chooseSource(events)
        return TrackingResult(RunUpdate(controller.snapshot(), events))
    }

    fun gps(fix: GpsFix): TrackingResult {
        val s = controller.snapshot()
        if (s.state != RunState.RUNNING || s.settings.mode != RunMode.OUTDOOR ||
            !fix.latitude.isFinite() || fix.latitude !in -90.0..90.0 ||
            !fix.longitude.isFinite() || fix.longitude !in -180.0..180.0 ||
            !fix.accuracyMeters.isFinite() || fix.accuracyMeters !in 0f..30f ||
            clock.read().monotonicMs - fix.monotonicMs !in 0..10_000 ||
            (lastGpsMs != null && fix.monotonicMs <= lastGpsMs!!)
        ) return unchanged()
        // Expire before comparing points so a newly usable fix cannot bridge a GPS gap.
        val expired = !gpsFresh()
        if (expired) previousFix = null
        val prior = previousFix
        val increment = if (prior == null) 0.0 else metersBetween(prior, fix)
        if (prior != null && increment / ((fix.monotonicMs - prior.monotonicMs) / 1_000.0) > 12.0) return unchanged()
        lastGpsMs = fix.monotonicMs
        val events = mutableListOf<RunEvent>()
        if (expired && controller.snapshot().segments.lastOrNull()?.source == DistanceSource.GPS) {
            events += controller.clearSource().events
        }
        chooseSource(events)
        val segment = controller.snapshot().segments.lastOrNull() ?: return unchanged()
        if (fix.monotonicMs < segment.startedMonotonicMs) return RunResult(events)
        val delta = previousFix?.let { metersBetween(it, fix) } ?: 0.0
        val measurement = RunMeasurement.Gps(segment.id, fix.monotonicMs, gpsMeters + delta)
        events += controller.record(measurement).events
        val accepted = controller.checkpoint().baseline == measurement
        if (accepted) { gpsMeters += delta; previousFix = fix }
        return TrackingResult(RunUpdate(controller.snapshot(), events), measurement.takeIf { accepted }, fix.takeIf { accepted })
    }

    fun steps(timeMs: Long, count: Long): TrackingResult {
        if (!stepsUsable || count < 0 || controller.snapshot().state != RunState.RUNNING) return unchanged()
        val events = mutableListOf<RunEvent>()
        chooseSource(events)
        val s = controller.snapshot()
        val segment = s.segments.lastOrNull()
        if (segment?.source != DistanceSource.STEPS || s.currentSegmentId != segment.id) return RunResult(events)
        val measurement = RunMeasurement.Steps(segment.id, timeMs, count)
        if (controller.checkpoint().baseline == measurement) return RunResult(events)
        events += controller.record(measurement).events
        return TrackingResult(RunUpdate(controller.snapshot(), events), measurement.takeIf { controller.checkpoint().baseline == it })
    }

    private fun chooseSource(events: MutableList<RunEvent>) {
        val s = controller.snapshot()
        if (s.state != RunState.RUNNING) return
        val source = when {
            s.settings.mode == RunMode.OUTDOOR && gpsFresh() -> DistanceSource.GPS
            stepsUsable && s.settings.strideLengthMeters != null -> DistanceSource.STEPS
            else -> null
        }
        val current = s.segments.lastOrNull()?.takeIf { it.id == s.currentSegmentId }?.source
        if (source != current) {
            events += if (source == null) controller.clearSource().events else controller.selectSource(source).events
        }
        if (segmentId != controller.snapshot().currentSegmentId) {
            segmentId = controller.snapshot().currentSegmentId
            previousFix = null
            gpsMeters = 0.0
        }
    }

    private fun unchanged() = TrackingResult(RunUpdate(controller.snapshot(), emptyList()))
    private fun RunResult(events: List<RunEvent>) = TrackingResult(RunUpdate(controller.snapshot(), events))

    companion object {
        fun metersBetween(a: GpsFix, b: GpsFix): Double {
            val lat = Math.toRadians(b.latitude - a.latitude)
            val lon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
            return 6_371_000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}
