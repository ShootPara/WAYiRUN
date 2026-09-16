package com.example.runningapp.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RecoveryAndTrackingTest {
    private class Clock(var time: Long = 0) : RunClock {
        override fun read() = RunTime(time, 1_700_000_000_000L + time)
    }
    private fun run(clock: Clock, mode: RunMode = RunMode.OUTDOOR, stride: Double? = 1.0, goal: RunGoal = RunGoal.None) =
        RunController("recovery-test", RunSettings(mode, RunUnits.KILOMETERS, 0, goal, stride), clock)

    @Test fun checkpointRoundTripRecoversPausedAfterRebootWithoutCountingGap() {
        val clock = Clock(100_000)
        val run = run(clock, goal = RunGoal.Time(1_000))
        run.start(); run.selectSource(DistanceSource.STEPS)
        val id = run.snapshot().currentSegmentId!!
        run.record(RunMeasurement.Steps(id, clock.time, 1_000))
        clock.time = 101_000
        run.record(RunMeasurement.Steps(id, clock.time, 1_100))
        val saved = Json.decodeFromString<RunCheckpoint>(Json.encodeToString(run.checkpoint()))
        clock.time = 5 // A new boot: old monotonic values must not affect duration.
        val recovered = RunController.recover(saved, clock)
        assertEquals(RunState.PAUSED, recovered.snapshot().state)
        assertEquals(1_000L, recovered.snapshot().activeDurationMs)
        assertEquals(100.0, recovered.snapshot().distanceMeters, 0.0)
        assertTrue(recovered.snapshot().goalReached)
        assertEquals(101_000L, recovered.snapshot().activeIntervals.single().endMonotonicMs)
        assertTrue(recovered.tick().events.isEmpty())
        val resumed = recovered.resume()
        assertTrue(resumed.events.single().id.sequence > saved.eventSequence)
        assertNull(recovered.checkpoint().baseline)
        recovered.selectSource(DistanceSource.STEPS)
        assertNotEquals(id, recovered.snapshot().currentSegmentId)
        clock.time = 1_005
        val done = recovered.finish().snapshot
        assertEquals(2_000L, done.activeDurationMs)
        assertEquals(listOf(0, 1), done.activeIntervals.map { it.epoch })
    }

    @Test fun finishedCheckpointRemainsTerminalAndPartialSplitUsesActualDistance() {
        val clock = Clock()
        val run = run(clock)
        run.start(); run.selectSource(DistanceSource.STEPS)
        val id = run.snapshot().currentSegmentId!!
        run.record(RunMeasurement.Steps(id, 0, 0))
        clock.time = 750_000
        run.record(RunMeasurement.Steps(id, clock.time, 1_250))
        run.finish()
        val restored = RunController.recover(Json.decodeFromString(Json.encodeToString(run.checkpoint())), Clock(9_000_000))
        assertEquals(run.snapshot(), restored.resume().snapshot)
        assertTrue(restored.finish().events.isEmpty())
        val partial = restored.snapshot().partialSplit()!!
        assertEquals(250.0, partial.distanceMeters, 0.0)
        assertEquals(150_000L, partial.durationMs)
        assertEquals(600_000.0, partial.paceMsPerUnit, 0.001)
    }

    @Test fun exactFullSplitHasNoPartialRow() {
        val clock = Clock()
        val run = run(clock)
        run.start(); run.selectSource(DistanceSource.STEPS)
        val id = run.snapshot().currentSegmentId!!
        run.record(RunMeasurement.Steps(id, 0, 0))
        clock.time = 600_000
        run.record(RunMeasurement.Steps(id, clock.time, 1_000))
        assertNull(run.snapshot().partialSplit())
        assertNull(run.finish().snapshot.partialSplit())
    }

    @Test fun gpsAndStepsDoNotOverlapAndGpsGapStartsNewSegment() {
        val clock = Clock()
        val run = run(clock)
        run.start()
        val input = TrackingInput(run, clock).apply { stepsUsable = true }
        input.tick(); input.steps(0, 100)
        clock.time = 1_000; input.steps(1_000, 110)
        clock.time = 2_000; input.gps(GpsFix(2_000, 0.0, 0.0, 5f))
        clock.time = 3_000
        input.steps(3_000, 120)
        input.gps(GpsFix(3_000, 0.0, 0.000045, 5f))
        assertEquals(15.00377, run.snapshot().distanceMeters, 0.01)
        val oldGpsSegment = run.snapshot().currentSegmentId
        clock.time = 14_000; input.tick(); input.steps(14_000, 200)
        clock.time = 15_000; input.steps(15_000, 210)
        clock.time = 16_000; input.gps(GpsFix(16_000, 1.0, 1.0, 5f))
        assertNotEquals(oldGpsSegment, run.snapshot().currentSegmentId)
        assertEquals(25.00377, run.snapshot().distanceMeters, 0.01)
    }

    @Test fun unusableLocationsCannotCreateDistanceOrRoute() {
        val clock = Clock()
        val run = run(clock)
        run.start()
        val input = TrackingInput(run, clock)
        listOf(GpsFix(0, Double.NaN, 0.0, 1f), GpsFix(0, 0.0, 181.0, 1f),
            GpsFix(0, 0.0, 0.0, 31f), GpsFix(100, 0.0, 0.0, 1f), GpsFix(-11_000, 0.0, 0.0, 1f))
            .forEach { assertNull(input.gps(it).fix) }
        input.gps(GpsFix(0, 0.0, 0.0, 1f))
        clock.time = 1_000
        assertNull(input.gps(GpsFix(1_000, 1.0, 1.0, 1f)).fix) // Implausible jump.
        assertEquals(0.0, run.snapshot().distanceMeters, 0.0)
    }

    @Test fun indoorModeIgnoresGpsAndPermissionLossKeepsTimer() {
        val clock = Clock()
        val run = run(clock, RunMode.INDOOR)
        run.start()
        val input = TrackingInput(run, clock).apply { stepsUsable = true }
        input.tick(); input.steps(0, 100)
        assertNull(input.gps(GpsFix(0, 0.0, 0.0, 1f)).fix)
        clock.time = 1_000; input.steps(1_000, 110)
        input.stepsUsable = false
        clock.time = 2_000; input.tick()
        assertNull(run.snapshot().currentSegmentId)
        assertFalse(input.distanceAvailable)
        assertEquals(10.0, run.snapshot().distanceMeters, 0.0)
        assertEquals(2_000L, run.snapshot().activeDurationMs)
    }

    @Test fun gpsGapWithoutAnInterveningTickStillBreaksRouteSegment() {
        val clock = Clock()
        val run = run(clock)
        run.start()
        val input = TrackingInput(run, clock)
        input.gps(GpsFix(0, 0.0, 0.0, 1f))
        val oldSegment = run.snapshot().currentSegmentId
        clock.time = 20_000
        input.gps(GpsFix(20_000, 1.0, 1.0, 1f))
        assertNotEquals(oldSegment, run.snapshot().currentSegmentId)
        assertEquals(0.0, run.snapshot().distanceMeters, 0.0)
    }
}
