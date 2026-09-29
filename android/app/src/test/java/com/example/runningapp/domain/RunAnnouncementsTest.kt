package com.example.runningapp.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RunAnnouncementsTest {
    private class Clock(var ms: Long = 0) : RunClock {
        override fun read() = RunTime(ms, 1_700_000_000_000L + ms)
    }
    private fun settings(interval: AnnouncementInterval, units: RunUnits = RunUnits.KILOMETERS,
        enabled: Boolean = true, goal: RunGoal = RunGoal.None, countdown: Int = 0) =
        RunSettings(RunMode.OUTDOOR, units, countdown, goal, 1.0, enabled, interval)
    private fun RunUpdate.announcements() = events.filter { it.type == RunEventType.ANNOUNCEMENT }

    @Test fun bothTimeChoicesUseActiveTimeAndContinuePastGoalWithoutDuplicates() {
        for (choice in listOf(AnnouncementInterval.FIVE_MINUTES, AnnouncementInterval.TEN_MINUTES)) {
            val clock = Clock()
            val interval = choice.timeMs!!
            val run = RunController("time", settings(choice, goal = RunGoal.Time(interval)), clock)
            run.start()
            clock.ms = interval - 1
            assertTrue(run.tick().announcements().isEmpty())
            clock.ms++
            val first = run.tick()
            assertEquals(1, first.events.count { it.type == RunEventType.GOAL_REACHED })
            assertEquals(interval, first.announcements().single().activeDurationMs)
            assertEquals(RunState.RUNNING, first.snapshot.state)
            assertTrue(run.tick().events.isEmpty())
            run.pause()
            clock.ms += 9_000_000
            assertTrue(run.tick().events.isEmpty())
            run.resume()
            clock.ms += interval * 2
            val later = run.tick()
            assertEquals(listOf(interval * 2, interval * 3), later.announcements().map { it.activeDurationMs })
            assertFalse(later.events.any { it.type == RunEventType.GOAL_REACHED })
            assertTrue(run.tick().events.isEmpty())
        }
    }

    @Test fun countdownIsExcludedAndPauseBoundaryEmitsBeforePause() {
        val clock = Clock()
        val run = RunController("countdown", settings(AnnouncementInterval.FIVE_MINUTES, countdown = 10), clock)
        run.start()
        clock.ms = 309_999
        assertTrue(run.tick().announcements().isEmpty())
        clock.ms++
        assertEquals(listOf(RunEventType.ANNOUNCEMENT, RunEventType.PAUSED), run.pause().events.map { it.type })
        clock.ms += 600_000
        assertTrue(run.pause().events.isEmpty())
    }

    @Test fun halfAndWholeDistancesUseCapturedMilesOrKilometersAndKeepSplits() {
        for (units in RunUnits.entries) for (choice in listOf(AnnouncementInterval.HALF_UNIT, AnnouncementInterval.ONE_UNIT)) {
            val clock = Clock()
            val interval = units.metersPerUnit * choice.distanceUnits!!
            val run = RunController("distance", settings(choice, units, goal = RunGoal.Distance(interval)), clock)
            run.start()
            val segment = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
            run.record(RunMeasurement.Gps(segment, 0, 0.0))
            clock.ms = 900_000
            val update = run.record(RunMeasurement.Gps(segment, clock.ms, interval * 3))
            assertEquals(listOf(interval, interval * 2, interval * 3), update.announcements().map { it.distanceMeters })
            assertEquals(listOf(300_000L, 600_000L, 900_000L), update.announcements().map { it.activeDurationMs })
            assertEquals(1, update.events.count { it.type == RunEventType.GOAL_REACHED })
            assertEquals(if (choice == AnnouncementInterval.HALF_UNIT) 1 else 3, update.snapshot.splits.size)
            assertEquals(RunState.RUNNING, update.snapshot.state)
            assertTrue(run.record(RunMeasurement.Gps(segment, clock.ms, interval * 3)).events.isEmpty())
            clock.ms += 300_000
            assertEquals(interval * 4, run.record(RunMeasurement.Gps(segment, clock.ms, interval * 4))
                .announcements().single().distanceMeters, 0.000001)
        }
    }

    @Test fun distanceSourceChangesAndPausesDoNotBridgeGapsOrRepeatThresholds() {
        val clock = Clock()
        val run = RunController("gaps", settings(AnnouncementInterval.HALF_UNIT), clock)
        run.start()
        var segment = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
        run.record(RunMeasurement.Gps(segment, 0, 0.0))
        clock.ms = 300_000
        assertEquals(1, run.record(RunMeasurement.Gps(segment, clock.ms, 500.0)).announcements().size)
        run.pause()
        clock.ms = 900_000
        assertTrue(run.record(RunMeasurement.Gps(segment, clock.ms, 9000.0)).announcements().isEmpty())
        run.resume()
        segment = run.selectSource(DistanceSource.STEPS).snapshot.currentSegmentId!!
        run.record(RunMeasurement.Steps(segment, clock.ms, 10_000))
        clock.ms += 300_000
        val next = run.record(RunMeasurement.Steps(segment, clock.ms, 10_500)).announcements().single()
        assertEquals(1000.0, next.distanceMeters, 0.0)
        assertEquals(600_000L, next.activeDurationMs)
        run.clearSource()
        clock.ms += 300_000
        segment = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
        assertTrue(run.record(RunMeasurement.Gps(segment, clock.ms, 50_000.0)).announcements().isEmpty())
        clock.ms += 300_000
        assertEquals(1500.0, run.record(RunMeasurement.Gps(segment, clock.ms, 50_500.0))
            .announcements().single().distanceMeters, 0.0)
    }

    @Test fun persistedTimeThresholdsDoNotReplayAfterRebootOrRepeatedRecovery() {
        val clock = Clock()
        var run = RunController("reboot", settings(AnnouncementInterval.FIVE_MINUTES), clock)
        run.start(); clock.ms = 600_000
        assertEquals(2, run.tick().announcements().size)
        val saved = Json.decodeFromString<RunCheckpoint>(Json.encodeToString(run.checkpoint()))
        clock.ms = 10
        run = RunController.recover(saved, clock)
        assertTrue(run.tick().events.isEmpty())
        assertTrue(run.resume().announcements().isEmpty())
        clock.ms += 299_999
        assertTrue(run.tick().announcements().isEmpty())
        clock.ms++
        val next = run.tick().announcements().single()
        assertEquals(900_000L, next.activeDurationMs)
        assertTrue(next.id.sequence > saved.eventSequence)
        run = RunController.recover(run.checkpoint(), clock)
        assertTrue(run.resume().announcements().isEmpty())
    }

    @Test fun persistedDistanceThresholdsResumeFromFreshSensorBaseline() {
        val clock = Clock()
        var run = RunController("distance-reboot", settings(AnnouncementInterval.HALF_UNIT), clock)
        run.start()
        var segment = run.selectSource(DistanceSource.STEPS).snapshot.currentSegmentId!!
        run.record(RunMeasurement.Steps(segment, 0, 0))
        clock.ms = 300_000
        run.record(RunMeasurement.Steps(segment, clock.ms, 500))
        run = RunController.recover(Json.decodeFromString(Json.encodeToString(run.checkpoint())), clock)
        run.resume()
        segment = run.selectSource(DistanceSource.STEPS).snapshot.currentSegmentId!!
        assertTrue(run.record(RunMeasurement.Steps(segment, clock.ms, 9000)).announcements().isEmpty())
        clock.ms += 300_000
        assertEquals(1000.0, run.record(RunMeasurement.Steps(segment, clock.ms, 9500))
            .announcements().single().distanceMeters, 0.0)
    }

    @Test fun offKeepsStateGoalAndFinishEventsAndFinishedRunStaysSilent() {
        val clock = Clock()
        val run = RunController("off", settings(AnnouncementInterval.FIVE_MINUTES, enabled = false,
            goal = RunGoal.Time(300_000)), clock)
        assertEquals(RunEventType.STARTED, run.start().events.single().type)
        clock.ms = 600_000
        assertEquals(listOf(RunEventType.GOAL_REACHED), run.tick().events.map { it.type })
        assertEquals(RunEventType.PAUSED, run.pause().events.single().type)
        assertEquals(RunEventType.RESUMED, run.resume().events.single().type)
        assertEquals(RunEventType.FINISHED, run.finish().events.single().type)
        clock.ms += 600_000
        assertTrue(run.tick().events.isEmpty())
    }

    @Test fun oldSettingsJsonDecodesWithoutTurningOnAnnouncements() {
        val json = """{"mode":"OUTDOOR","units":"MILES","countdownSeconds":0,"goal":{"type":"com.example.runningapp.domain.RunGoal.None"},"strideLengthMeters":null}"""
        val settings = Json.decodeFromString<RunSettings>(json)
        assertFalse(settings.announcementsEnabled)
        assertEquals(AnnouncementInterval.FIVE_MINUTES, settings.announcementInterval)
    }
}
