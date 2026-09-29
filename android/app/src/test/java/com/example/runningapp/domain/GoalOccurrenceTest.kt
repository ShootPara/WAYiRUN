package com.example.runningapp.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GoalOccurrenceTest {
    @Test fun delayedDistanceGoalUsesCrossingTimeWhileKeepingRecapMetricsAndAllSplits() {
        for (units in RunUnits.entries) {
            var now = 0L
            val clock = RunClock { RunTime(now, 1_700_000_000_000 + now) }
            val settings = RunSettings(RunMode.OUTDOOR, units, 0, RunGoal.Distance(units.metersPerUnit), null,
                announcementsEnabled = true, announcementSelection = AnnouncementSelection(distanceEnabled = true))
            val run = RunController("distance-goal", settings, clock)
            run.start()
            val segment = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
            run.record(RunMeasurement.Gps(segment, 0, 0.0))
            now = 900_000
            val events = run.record(RunMeasurement.Gps(segment, now, units.metersPerUnit * 3)).events
            val goal = events.single { it.type == RunEventType.GOAL_REACHED }
            assertEquals(300_000L, goal.occurrenceActiveMs)
            assertEquals(900_000L, goal.activeDurationMs)
            assertEquals(units.metersPerUnit * 3, goal.distanceMeters, 0.0)
            assertEquals(3, run.snapshot().splits.size)
            assertEquals(listOf(300_000L, 600_000L, 900_000L), events.filter {
                it.announcementChannel == AnnouncementChannel.DISTANCE }.map { it.occurrenceActiveMs })
            assertTrue(run.tick().events.isEmpty())
            val recovered = RunController.recover(Json.decodeFromString(Json.encodeToString(run.checkpoint())), clock)
            assertTrue(recovered.resume().events.none { it.type in listOf(RunEventType.ANNOUNCEMENT, RunEventType.GOAL_REACHED) })
            now += 300_000
            assertEquals(1, recovered.tick().events.count { it.type == RunEventType.ANNOUNCEMENT })
        }
    }

    @Test fun delayedTimeGoalUsesConfiguredActiveBoundaryAndLegacyEventsStillDecode() {
        var now = 0L
        val run = RunController("time-goal", RunSettings(RunMode.INDOOR, RunUnits.MILES, 0,
            RunGoal.Time(300_000), null, announcementsEnabled = true), RunClock { RunTime(now, now) })
        run.start(); now = 900_000
        val update = run.tick()
        val goal = update.events.single { it.type == RunEventType.GOAL_REACHED }
        assertEquals(300_000L, goal.occurrenceActiveMs)
        assertEquals(900_000L, goal.activeDurationMs)
        assertEquals(3, update.events.count { it.announcementChannel == AnnouncementChannel.TIME })
        val legacy = RunEvent(RunEventId("old", 1), RunEventType.ANNOUNCEMENT, 300_000, 0.0)
        val json = Json.encodeToString(legacy)
        assertFalse(json.contains("occurrenceActiveMs"))
        assertEquals(legacy, Json.decodeFromString<RunEvent>(json))
    }
}
