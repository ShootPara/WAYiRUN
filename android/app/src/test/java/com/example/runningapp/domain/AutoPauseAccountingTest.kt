package com.example.runningapp.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AutoPauseAccountingTest {
    private class Clock(var ms: Long = 0) : RunClock {
        override fun read() = RunTime(ms, 1_700_000_000_000 + ms)
    }
    private fun run(clock: Clock) = RunController("auto", RunSettings(
        RunMode.INDOOR, RunUnits.KILOMETERS, 0, RunGoal.None, 1.0,
        true, AnnouncementInterval.FIVE_MINUTES,
    ), clock).also { it.start() }

    @Test fun automaticPauseFreezesAccountingAndResumeStartsFreshSegment() {
        val clock = Clock()
        val run = run(clock)
        val first = run.selectSource(DistanceSource.STEPS).snapshot.currentSegmentId!!
        run.record(RunMeasurement.Steps(first, 0, 100))
        clock.ms = 1_000
        run.record(RunMeasurement.Steps(first, clock.ms, 110))
        val paused = run.autoPause().snapshot
        assertEquals(PauseReason.AUTOMATIC, paused.pauseReason)
        clock.ms = 601_000
        val ignored = run.record(RunMeasurement.Steps(first, clock.ms, 10_000))
        assertEquals(paused.activeDurationMs, ignored.snapshot.activeDurationMs)
        assertEquals(10.0, ignored.snapshot.distanceMeters, 0.0)
        assertTrue(ignored.events.isEmpty())
        val resumed = run.autoResume()
        assertNull(resumed.snapshot.pauseReason)
        assertEquals(listOf(RunEventType.RESUMED), resumed.events.map { it.type })
        val second = resumed.snapshot.currentSegmentId!!
        assertNotEquals(first, second)
        run.record(RunMeasurement.Steps(second, clock.ms, 10_000))
        clock.ms++
        assertEquals(11.0, run.record(RunMeasurement.Steps(second, clock.ms, 10_001)).snapshot.distanceMeters, 0.0)
        assertEquals(1_000L, run.snapshot().activeIntervals.first().endActiveMs)
        assertEquals(601_000L, run.snapshot().activeIntervals.last().startMonotonicMs)
    }

    @Test fun manualOverrideAndInterruptedRecoveryRequireExplicitResume() {
        val clock = Clock()
        val run = run(clock)
        run.autoPause()
        assertTrue(run.pause().events.isEmpty())
        assertEquals(PauseReason.MANUAL, run.autoResume().snapshot.pauseReason)
        run.resume()
        run.autoPause()
        val json = Json.encodeToString(run.checkpoint())
        val saved = Json.decodeFromString<RunCheckpoint>(json)
        assertEquals(PauseReason.AUTOMATIC, saved.snapshot.pauseReason)
        val recovered = RunController.recover(saved, Clock())
        assertEquals(PauseReason.INTERRUPTED, recovered.autoResume().snapshot.pauseReason)
        assertEquals(RunState.RUNNING, recovered.resume().snapshot.state)
        assertNull(recovered.finish().snapshot.pauseReason)
        assertEquals(RunState.FINISHED, recovered.autoResume().snapshot.state)
    }

    @Test fun legacyCheckpointWithoutPauseReasonStillDecodesAndRecoversSafely() {
        val run = run(Clock())
        run.pause()
        val root = Json.parseToJsonElement(Json.encodeToString(run.checkpoint())).jsonObject
        val legacy = JsonObject(root + ("snapshot" to JsonObject(root.getValue("snapshot").jsonObject - "pauseReason")))
        val saved = Json.decodeFromString<RunCheckpoint>(legacy.toString())
        assertNull(saved.snapshot.pauseReason)
        val recovered = RunController.recover(saved, Clock())
        assertEquals(PauseReason.INTERRUPTED, recovered.autoResume().snapshot.pauseReason)
        assertEquals(RunState.PAUSED, recovered.snapshot().state)
    }
}
