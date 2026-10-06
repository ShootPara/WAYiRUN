package com.example.runningapp.domain

import org.junit.Assert.*
import org.junit.Test

class RunControllerTest {
    private class Clock : RunClock {
        var now = RunTime(0, 1_700_000_000_000)
        override fun read() = now
        fun at(ms: Long) {
            now = RunTime(ms, now.utcMs + ms - now.monotonicMs)
        }
    }

    private class Fixture(
        units: RunUnits = RunUnits.KILOMETERS,
        countdown: Int = 0,
        goal: RunGoal = RunGoal.None,
        mode: RunMode = RunMode.OUTDOOR,
        stride: Double? = null,
    ) {
        val clock = Clock()
        val run = RunController("test-run", RunSettings(mode, units, countdown, goal, stride), clock)
        fun gpsStart(value: Double = 0.0): Long {
            run.start()
            val id = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
            run.record(RunMeasurement.Gps(id, clock.now.monotonicMs, value))
            return id
        }
        fun gps(ms: Long, meters: Double, id: Long = run.snapshot().currentSegmentId!!): RunUpdate {
            clock.at(ms)
            return run.record(RunMeasurement.Gps(id, ms, meters))
        }
        fun steps(ms: Long, count: Long, id: Long = run.snapshot().currentSegmentId!!): RunUpdate {
            clock.at(ms)
            return run.record(RunMeasurement.Steps(id, ms, count))
        }
    }

    @Test fun zeroCountdownStartsImmediatelyAndOnlyOnce() {
        val f = Fixture()
        assertEquals(RunState.READY, f.run.snapshot().state)
        val result = f.run.start()
        assertEquals(RunState.RUNNING, result.snapshot.state)
        assertEquals(0L, result.snapshot.activeDurationMs)
        assertEquals(listOf(RunEventType.STARTED), result.events.map { it.type })
        assertTrue(f.run.start().events.isEmpty())
        assertTrue(f.run.tick().events.isEmpty())
    }

    @Test fun countdownUsesDeadlineEvenWhenTickArrivesLate() {
        val f = Fixture(countdown = 10)
        assertEquals(10_000L, f.run.start().snapshot.countdownRemainingMs)
        f.clock.at(9_999)
        assertEquals(1L, f.run.tick().snapshot.countdownRemainingMs)
        assertTrue(f.run.start().events.isEmpty())
        f.clock.at(12_000)
        val result = f.run.tick()
        assertEquals(RunState.RUNNING, result.snapshot.state)
        assertEquals(2_000L, result.snapshot.activeDurationMs)
        assertEquals(1_700_000_010_000L, result.snapshot.startedUtcMs)
        assertEquals(0L, result.events.single().activeDurationMs)
    }

    @Test fun countdownExactBoundaryAndPreRunControls() {
        val f = Fixture(countdown = 1)
        assertTrue(f.run.pause().events.isEmpty())
        assertTrue(f.run.resume().events.isEmpty())
        assertTrue(f.run.finish().events.isEmpty())
        f.run.start()
        assertTrue(f.run.pause().events.isEmpty())
        assertTrue(f.run.resume().events.isEmpty())
        assertTrue(f.run.finish().events.isEmpty())
        f.clock.at(1_000)
        val result = f.run.tick()
        assertEquals(RunState.RUNNING, result.snapshot.state)
        assertEquals(0L, result.snapshot.activeDurationMs)
        assertEquals(1, result.events.size)
    }

    @Test fun paceExcludesPauseTimeAndMovement() {
        val f = Fixture()
        val oldId = f.gpsStart(50.0)
        f.gps(300_000, 550.0)
        f.run.pause()
        assertEquals(500.0, f.gps(600_000, 9_000.0, oldId).snapshot.distanceMeters, 0.0)
        assertEquals(300_000L, f.run.tick().snapshot.activeDurationMs)
        val newId = f.run.resume().snapshot.currentSegmentId!!
        assertNotEquals(oldId, newId)
        f.gps(600_000, 9_000.0, newId)
        val result = f.gps(900_000, 9_500.0, newId).snapshot
        assertEquals(600_000L, result.activeDurationMs)
        assertEquals(1_000.0, result.distanceMeters, 0.0)
        assertEquals(600_000.0, result.averagePaceMsPerUnit!!, 0.001)
        assertEquals(600_000L, result.splits.single().durationMs)
    }

    @Test fun wallClockJumpsDoNotChangeDurationOrPace() {
        val f = Fixture()
        f.gpsStart()
        f.clock.now = RunTime(300_000, 1_000)
        f.run.tick()
        f.clock.now = RunTime(600_000, -2_000)
        val id = f.run.snapshot().currentSegmentId!!
        f.run.record(RunMeasurement.Gps(id, 600_000, 1_000.0))
        val done = f.run.finish().snapshot
        assertEquals(600_000L, done.activeDurationMs)
        assertEquals(600_000.0, done.averagePaceMsPerUnit!!, 0.001)
        assertEquals(-2_000L, done.endedUtcMs)
    }

    @Test fun zeroDistanceHasNoPaceAndCanFinish() {
        val f = Fixture()
        f.run.start()
        f.clock.at(60_000)
        val done = f.run.finish().snapshot
        assertEquals(60_000L, done.activeDurationMs)
        assertNull(done.averagePaceMsPerUnit)
        assertTrue(done.splits.isEmpty())
        assertFalse(done.goalReached)
    }

    @Test fun mileConversionAndExactBoundary() {
        val f = Fixture(units = RunUnits.MILES)
        f.gpsStart()
        assertTrue(f.gps(599_999, 1_609.343).snapshot.splits.isEmpty())
        val result = f.gps(600_000, 1_609.344)
        assertEquals(600_000.0, result.snapshot.averagePaceMsPerUnit!!, 0.001)
        assertEquals(1_609.344, result.snapshot.splits.single().endDistanceMeters, 0.0)
        assertEquals(RunUnits.MILES, result.snapshot.splits.single().units)
        assertEquals(600_000L, result.snapshot.splits.single().durationMs)
        assertTrue(f.run.tick().events.isEmpty())
    }

    @Test fun oneMeasurementCrossesSeveralSplitsWithInterpolatedTimes() {
        val f = Fixture()
        f.gpsStart()
        val result = f.gps(1_500_000, 2_500.0)
        assertEquals(listOf(600_000L, 1_200_000L), result.snapshot.splits.map { it.endActiveMs })
        assertEquals(listOf(600_000L, 600_000L), result.snapshot.splits.map { it.durationMs })
        assertEquals(listOf(1, 2), result.events.map { it.split!!.number })
        assertEquals(listOf(1_000.0, 2_000.0), result.events.map { it.distanceMeters })
        assertEquals(2, f.run.finish().snapshot.splits.size) // No invented partial-split policy.
    }

    @Test fun splitInterpolationStartsAtPreviousMeasurementNotPreviousTick() {
        val f = Fixture()
        val id = f.gpsStart()
        f.gps(400_000, 800.0)
        f.clock.at(800_000)
        f.run.tick()
        val result = f.run.record(RunMeasurement.Gps(id, 600_000, 1_200.0))
        assertEquals(500_000L, result.snapshot.splits.single().durationMs)
        assertEquals(800_000L, result.snapshot.activeDurationMs)
    }

    @Test fun timeGoalFiresOnceExcludesCountdownAndPauseAndDoesNotStop() {
        val f = Fixture(countdown = 1, goal = RunGoal.Time(60_000))
        f.run.start()
        f.clock.at(31_000)
        f.run.pause()
        f.clock.at(131_000)
        assertFalse(f.run.tick().snapshot.goalReached)
        f.run.resume()
        f.clock.at(161_000)
        val reached = f.run.tick()
        assertEquals(listOf(RunEventType.GOAL_REACHED), reached.events.map { it.type })
        assertEquals(RunState.RUNNING, reached.snapshot.state)
        f.clock.at(171_000)
        assertTrue(f.run.tick().events.isEmpty())
        assertEquals(70_000L, f.run.snapshot().activeDurationMs)
        assertEquals(listOf(RunEventType.FINISHED), f.run.finish().events.map { it.type })
    }

    @Test fun distanceGoalFiresOnceAndDistanceContinuesGrowing() {
        val f = Fixture(goal = RunGoal.Distance(500.0))
        f.gpsStart()
        assertFalse(f.gps(100_000, 499.0).snapshot.goalReached)
        val reached = f.gps(101_000, 500.0)
        assertEquals(listOf(RunEventType.GOAL_REACHED), reached.events.map { it.type })
        assertEquals(RunState.RUNNING, reached.snapshot.state)
        assertTrue(f.gps(102_000, 600.0).events.isEmpty())
        assertEquals(600.0, f.run.snapshot().distanceMeters, 0.0)
    }

    @Test fun goalReachedOnFinishingTickPrecedesFinish() {
        val f = Fixture(goal = RunGoal.Time(1_000))
        f.run.start()
        f.clock.at(1_000)
        assertEquals(listOf(RunEventType.GOAL_REACHED, RunEventType.FINISHED), f.run.finish().events.map { it.type })
    }

    @Test fun stepBaselineAndCounterResetNeverProduceNegativeDistance() {
        val f = Fixture(mode = RunMode.INDOOR, stride = 0.75)
        f.run.start()
        f.run.selectSource(DistanceSource.STEPS)
        assertEquals(0.0, f.steps(0, 10_000).snapshot.distanceMeters, 0.0)
        assertEquals(75.0, f.steps(1_000, 10_100).snapshot.distanceMeters, 0.0)
        assertEquals(75.0, f.steps(2_000, 2).snapshot.distanceMeters, 0.0)
        assertEquals(82.5, f.steps(3_000, 12).snapshot.distanceMeters, 0.0)
    }

    @Test fun stepBaselineResetsAcrossPauseEvenWithoutPausedSamples() {
        val f = Fixture(stride = 1.0)
        f.run.start()
        val old = f.run.selectSource(DistanceSource.STEPS).snapshot.currentSegmentId!!
        f.steps(0, 100)
        f.steps(1_000, 110)
        f.run.pause()
        f.clock.at(10_000)
        f.run.resume()
        f.steps(10_000, 1_000)
        f.steps(11_000, 1_010)
        assertEquals(20.0, f.run.snapshot().distanceMeters, 0.0)
        assertEquals(20.0, f.steps(12_000, 9_000, old).snapshot.distanceMeters, 0.0)
    }

    @Test fun switchingSourcesUsesOnlyNewBaselinesAndRetainsSegments() {
        val f = Fixture(stride = 1.0)
        f.run.start()
        val stepId = f.run.selectSource(DistanceSource.STEPS).snapshot.currentSegmentId!!
        f.steps(0, 100)
        f.steps(1_000, 150)
        val gpsId = f.run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
        f.gps(1_000, 10_000.0)
        f.gps(2_000, 10_100.0)
        f.steps(3_000, 500, stepId) // A late reading from the old source must not count.
        val nextStepId = f.run.selectSource(DistanceSource.STEPS).snapshot.currentSegmentId!!
        f.steps(3_000, 500)
        f.steps(4_000, 525)
        val done = f.run.finish().snapshot
        assertEquals(175.0, done.distanceMeters, 0.0)
        assertEquals(listOf(stepId, gpsId, nextStepId), done.segments.map { it.id })
        assertEquals(listOf(50.0, 100.0, 25.0), done.segments.map { it.distanceMeters })
        assertEquals(listOf(1_000L, 3_000L, 4_000L), done.segments.map { it.endedMonotonicMs })
        assertNull(done.currentSegmentId)
    }

    @Test fun duplicateSourceSelectionDoesNotResetBaseline() {
        val f = Fixture()
        val id = f.gpsStart()
        f.gps(1_000, 10.0)
        assertEquals(id, f.run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId)
        assertEquals(20.0, f.gps(2_000, 20.0).snapshot.distanceMeters, 0.0)
        assertEquals(1, f.run.snapshot().segments.size)
    }

    @Test fun staleDuplicateFutureAndWrongSourceSamplesAreIgnored() {
        val f = Fixture(stride = 1.0)
        val id = f.gpsStart()
        f.gps(2_000, 20.0)
        listOf(
            RunMeasurement.Gps(id, 1_000, 500.0),
            RunMeasurement.Gps(id, 2_000, 500.0),
            RunMeasurement.Gps(id, 3_000, 500.0),
            RunMeasurement.Steps(id, 2_000, 500),
        ).forEach { assertEquals(20.0, f.run.record(it).snapshot.distanceMeters, 0.0) }
        assertEquals(30.0, f.gps(3_000, 30.0).snapshot.distanceMeters, 0.0)
    }

    @Test fun newSegmentRejectsReadingsCapturedBeforeItsStart() {
        val f = Fixture()
        f.gpsStart()
        f.gps(1_000, 10.0)
        f.run.pause()
        f.clock.at(5_000)
        val id = f.run.resume().snapshot.currentSegmentId!!
        f.run.record(RunMeasurement.Gps(id, 4_000, 100.0))
        assertEquals(10.0, f.gps(5_000, 500.0).snapshot.distanceMeters, 0.0)
        assertEquals(20.0, f.gps(6_000, 510.0).snapshot.distanceMeters, 0.0)
    }

    @Test fun sourceGapDoesNotInterpolateSplitTimeAcrossUnmeasuredInterval() {
        val f = Fixture(stride = 1.0)
        f.gpsStart()
        f.gps(400_000, 800.0)
        f.clock.at(500_000)
        f.run.selectSource(DistanceSource.STEPS)
        f.steps(600_000, 10_000)
        val result = f.steps(800_000, 10_400)
        assertEquals(700_000L, result.snapshot.splits.single().endActiveMs)
    }

    @Test fun duplicateCommandsEmitOneCuePerTransitionWithStableIds() {
        val f = Fixture()
        val events = mutableListOf<RunEvent>()
        events += f.run.start().events
        f.clock.at(1_000)
        events += f.run.pause().events
        assertTrue(f.run.pause().events.isEmpty())
        f.clock.at(2_000)
        events += f.run.resume().events
        assertTrue(f.run.resume().events.isEmpty())
        events += f.run.finish().events
        assertTrue(f.run.finish().events.isEmpty())
        assertEquals(listOf(1L, 2L, 3L, 4L), events.map { it.id.sequence })
        assertTrue(events.all { it.id.runId == "test-run" })
        assertEquals(listOf(RunEventType.STARTED, RunEventType.PAUSED, RunEventType.RESUMED, RunEventType.FINISHED), events.map { it.type })
    }

    @Test fun finishedRunCannotRestartFromPlayerResumeOrOtherCommands() {
        val f = Fixture()
        val id = f.gpsStart()
        f.gps(1_000, 10.0)
        f.run.pause()
        f.clock.at(5_000)
        val done = f.run.finish().snapshot
        f.clock.at(100_000)
        val attempts = listOf(
            f.run.resume(), f.run.start(), f.run.pause(), f.run.finish(), f.run.tick(),
            f.run.selectSource(DistanceSource.GPS),
            f.run.record(RunMeasurement.Gps(id, 100_000, 999.0)),
        )
        attempts.forEach {
            assertEquals(done, it.snapshot)
            assertTrue(it.events.isEmpty())
        }
        assertEquals(1_000L, done.activeDurationMs)
        assertEquals(1_700_000_005_000L, done.endedUtcMs)
    }

    @Test fun invalidSettingsGoalsAndMeasurementsAreRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RunGoal.Distance(value) }
            assertThrows(IllegalArgumentException::class.java) { Fixture(stride = value) }
        }
        listOf(-1L, 0L).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RunGoal.Time(value) }
        }
        listOf(-1, 11).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { Fixture(countdown = value) }
        }
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { RunMeasurement.Gps(1, 0, value) }
        }
        assertThrows(IllegalArgumentException::class.java) { RunMeasurement.Steps(1, 0, -1) }
    }

    @Test fun absentStrideDoesNotPreventStartingButCannotSupplyStepDistance() {
        val f = Fixture()
        assertEquals(RunState.RUNNING, f.run.start().snapshot.state)
        assertThrows(IllegalArgumentException::class.java) { f.run.selectSource(DistanceSource.STEPS) }
        assertNull(f.run.snapshot().currentSegmentId)
        assertNull(f.run.snapshot().settings.strideLengthMeters)
        val indoor = Fixture(mode = RunMode.INDOOR, stride = 0.8)
        indoor.run.start()
        assertThrows(IllegalArgumentException::class.java) { indoor.run.selectSource(DistanceSource.GPS) }
    }

    @Test fun backwardsMonotonicClockIsRejectedWithoutChangingState() {
        val f = Fixture()
        f.run.start()
        f.clock.at(100)
        val before = f.run.tick().snapshot
        f.clock.at(99)
        assertThrows(IllegalArgumentException::class.java) { f.run.pause() }
        assertEquals(before, f.run.snapshot())
    }

    @Test fun gpsCounterResetRebaselinesAndZeroDeltasPreserveStationaryTime() {
        val f = Fixture()
        f.gpsStart(50.0)
        f.gps(100_000, 550.0)
        f.gps(200_000, 550.0)
        f.gps(300_000, 0.0)
        val result = f.gps(400_000, 500.0)
        assertEquals(1_000.0, result.snapshot.distanceMeters, 0.0)
        assertEquals(400_000L, result.snapshot.splits.single().durationMs)
    }

    @Test fun overflowingStepDistanceDoesNotPoisonValidBaseline() {
        val f = Fixture(stride = Double.MAX_VALUE)
        f.run.start()
        f.run.selectSource(DistanceSource.STEPS)
        f.steps(0, 0)
        assertEquals(0.0, f.steps(1_000, 2).snapshot.distanceMeters, 0.0)
        assertEquals(0.0, f.steps(2_000, 0).snapshot.distanceMeters, 0.0)
        assertEquals(0.0, f.steps(3_000, 1).snapshot.distanceMeters, 0.0) // Finite but corrupt-sized input.
    }

    @Test fun previouslyReturnedSnapshotDoesNotChangeWithController() {
        val f = Fixture()
        f.gpsStart()
        val before = f.run.snapshot()
        f.gps(600_000, 1_000.0)
        f.run.finish()
        assertTrue(before.splits.isEmpty())
        assertNull(before.segments.single().endedMonotonicMs)
        assertEquals(0.0, before.segments.single().distanceMeters, 0.0)
    }
}
