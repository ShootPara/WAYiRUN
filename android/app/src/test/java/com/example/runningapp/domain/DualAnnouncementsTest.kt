package com.example.runningapp.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DualAnnouncementsTest {
    private class Clock(var ms: Long = 0) : RunClock {
        override fun read() = RunTime(ms, 1_700_000_000_000 + ms)
    }
    private fun settings(selection: AnnouncementSelection?, enabled: Boolean = true, units: RunUnits = RunUnits.MILES) =
        RunSettings(RunMode.OUTDOOR, units, 0, RunGoal.None, null, enabled,
            announcementSelection = selection)

    @Test fun independentChannelsMasterAndUnitsCoverAllCombinations() {
        for (units in RunUnits.entries) for (master in listOf(false, true))
            for (time in listOf(false, true)) for (distance in listOf(false, true)) {
                val clock = Clock()
                val run = RunController("dual", settings(AnnouncementSelection(timeEnabled = time,
                    distanceEnabled = distance), master, units), clock)
                run.start()
                val segment = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
                run.record(RunMeasurement.Gps(segment, 0, 0.0))
                clock.ms = 300_000
                val update = run.record(RunMeasurement.Gps(segment, clock.ms, units.metersPerUnit))
                val cues = update.events.filter { it.type == RunEventType.ANNOUNCEMENT }
                assertEquals((if (master && time) 1 else 0) + (if (master && distance) 1 else 0), cues.size)
                assertEquals(1, update.snapshot.splits.size)
                if (master && distance) assertEquals(units.metersPerUnit, cues.last().distanceMeters, 0.0)
                assertTrue(run.tick().events.isEmpty())
            }
    }

    @Test fun dualProgressSurvivesSerializationPauseRecoveryAndSourceChange() {
        val clock = Clock()
        val run = RunController("restore-dual", settings(AnnouncementSelection(distanceEnabled = true)), clock)
        run.start()
        val first = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
        run.record(RunMeasurement.Gps(first, 0, 0.0))
        clock.ms = 300_000
        assertEquals(2, run.record(RunMeasurement.Gps(first, clock.ms, RunUnits.MILES.metersPerUnit))
            .events.count { it.type == RunEventType.ANNOUNCEMENT })
        run.pause()
        clock.ms += 100_000
        assertTrue(run.record(RunMeasurement.Gps(first, clock.ms, 99_000.0)).events.isEmpty())
        val saved = Json.decodeFromString<RunCheckpoint>(Json.encodeToString(run.checkpoint()))
        val recovered = RunController.recover(saved, clock)
        assertTrue(recovered.resume().events.none { it.type == RunEventType.ANNOUNCEMENT })
        recovered.clearSource()
        val next = recovered.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
        recovered.record(RunMeasurement.Gps(next, clock.ms, 0.0))
        clock.ms += 300_000
        val update = recovered.record(RunMeasurement.Gps(next, clock.ms, RunUnits.MILES.metersPerUnit))
        assertEquals(2, update.events.count { it.type == RunEventType.ANNOUNCEMENT })
        assertEquals(600_000L, update.snapshot.activeDurationMs)
        assertEquals(2, update.snapshot.splits.size)
        assertTrue(recovered.tick().events.isEmpty())
    }

    @Test fun oldSettingsStayLegacyAndNewCapturesAreAuthoritativeAndRoundTrip() {
        for (interval in AnnouncementInterval.entries) {
            val legacy = settings(null).copy(announcementInterval = interval)
            val json = Json.encodeToString(legacy)
            assertFalse(json.contains("announcementSelection"))
            assertEquals(AnnouncementSelection.legacy(interval), Json.decodeFromString<RunSettings>(json).effectiveAnnouncements())
        }
        for (selection in listOf(AnnouncementSelection(), AnnouncementSelection(timeEnabled = false, distanceEnabled = true),
            AnnouncementSelection(distanceEnabled = true), AnnouncementSelection(timeEnabled = false))) {
            for (enabled in listOf(false, true)) {
                val captured = settings(selection, enabled).copy(announcementInterval = AnnouncementInterval.HALF_UNIT)
                val restored = Json.decodeFromString<RunSettings>(Json.encodeToString(captured))
                assertEquals(captured, restored)
                assertEquals(if (enabled) selection else selection.copy(timeEnabled = false, distanceEnabled = false),
                    restored.effectiveAnnouncements())
            }
        }
    }

    @Test fun noDistanceStillAnnouncesTimeAndLargeDistanceUpdatesRetainEveryBoundary() {
        val clock = Clock()
        val run = RunController("batch", settings(AnnouncementSelection(distanceEnabled = true,
            distanceInterval = AnnouncementInterval.HALF_UNIT), units = RunUnits.KILOMETERS), clock)
        run.start()
        clock.ms = 300_000
        assertEquals(1, run.tick().events.count { it.type == RunEventType.ANNOUNCEMENT })
        val segment = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
        run.record(RunMeasurement.Gps(segment, clock.ms, 0.0))
        clock.ms += 900_000
        val events = run.record(RunMeasurement.Gps(segment, clock.ms, 3_000.0)).events
        assertEquals(9, events.count { it.type == RunEventType.ANNOUNCEMENT })
        assertEquals(3, events.count { it.type == RunEventType.SPLIT_COMPLETED })
        assertTrue(run.tick().events.isEmpty())
    }

    @Test fun bothChannelsContinueAfterGoalAndWrongIntervalKindsAreRejected() {
        val clock = Clock()
        val run = RunController("goal-dual", settings(AnnouncementSelection(distanceEnabled = true),
            units = RunUnits.KILOMETERS).copy(goal = RunGoal.Time(300_000)), clock)
        run.start()
        val segment = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
        run.record(RunMeasurement.Gps(segment, 0, 0.0))
        clock.ms = 300_000
        assertEquals(1, run.record(RunMeasurement.Gps(segment, clock.ms, 1_000.0))
            .events.count { it.type == RunEventType.GOAL_REACHED })
        clock.ms = 600_000
        val later = run.record(RunMeasurement.Gps(segment, clock.ms, 2_000.0))
        assertEquals(2, later.events.count { it.type == RunEventType.ANNOUNCEMENT })
        assertFalse(later.events.any { it.type == RunEventType.GOAL_REACHED })
        assertThrows(IllegalArgumentException::class.java) { AnnouncementSelection(timeInterval = AnnouncementInterval.ONE_UNIT) }
        assertThrows(IllegalArgumentException::class.java) { AnnouncementSelection(distanceInterval = AnnouncementInterval.FIVE_MINUTES) }
        assertThrows(IllegalArgumentException::class.java) { AnnouncementSelection(version = 2) }
    }
}
