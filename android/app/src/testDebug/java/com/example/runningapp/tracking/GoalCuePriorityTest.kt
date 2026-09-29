package com.example.runningapp.tracking

import com.example.runningapp.domain.*
import org.junit.Assert.*
import org.junit.Test

class GoalCuePriorityTest {
    private class Output : CueOutput {
        val spoken = mutableListOf<AudibleCue>()
        var complete: (Boolean) -> Unit = {}
        var focus = false
        override fun begin(): Boolean { focus = true; return true }
        override fun end() { focus = false }
        override fun speak(cue: AudibleCue, completed: (Boolean) -> Unit): Boolean {
            spoken += cue; complete = completed; return true
        }
        override fun tone(cue: AudibleCue, completed: () -> Unit) = completed()
        override fun close() = Unit
    }
    private fun event(type: RunEventType, at: Long, id: Long = 1, channel: AnnouncementChannel? = null,
        run: String = "run") = RunEvent(RunEventId(run, id), type, at, 1_000.0,
        occurrenceActiveMs = at, announcementChannel = channel)
    private fun milestone(at: Long = 300_000, id: Long = 1, channel: AnnouncementChannel = AnnouncementChannel.TIME) =
        event(RunEventType.ANNOUNCEMENT, at, id, channel)

    @Test fun alignedGoalsWinInEitherBatchOrderAndEitherCallbackOrder() {
        for (reverse in listOf(false, true)) for (separate in listOf(false, true)) {
            val output = Output(); val timer = TestCueScheduler(); val queue = RunCueQueue(output, timer)
            val events = listOf(milestone(), event(RunEventType.GOAL_REACHED, 300_000, 2))
                .let { if (reverse) it.reversed() else it }
            if (separate) {
                queue.play(listOf(events[0]))
                timer.advance(500)
                queue.play(listOf(events[1]))
            } else queue.play(events)
            timer.advance(2_000)
            assertEquals(1, output.spoken.size)
            assertTrue(output.spoken.single().text.startsWith("Goal reached."))
            output.complete(true)
            assertFalse(queue.isPlaying)
            assertEquals(0, timer.pendingCount)
            queue.play(listOf(milestone(600_000, 3)))
            timer.advance(1_000)
            assertEquals(2, output.spoken.size)
        }
    }

    @Test fun collisionUsesOccurrenceRatherThanDeliveryOrRecapTime() {
        for (offset in listOf(-1_001L, -1_000L, 1_000L, 1_001L)) {
            val output = Output(); val timer = TestCueScheduler(); val queue = RunCueQueue(output, timer)
            queue.play(listOf(milestone(), event(RunEventType.GOAL_REACHED, 300_000 + offset, 2)
                .copy(activeDurationMs = 900_000)))
            assertTrue(output.spoken.single().text.contains("15 minutes"))
            output.complete(true)
            timer.advance(1_000)
            assertEquals(if (kotlin.math.abs(offset) <= 1_000) 1 else 2, output.spoken.size)
        }
    }

    @Test fun bothChannelsProduceOneDistanceBoundaryRecapWithoutHoldingFocusDuringDelay() {
        for (reverse in listOf(false, true)) {
            val output = Output(); val timer = TestCueScheduler(); val queue = RunCueQueue(output, timer)
            val time = milestone().copy(distanceMeters = 999.0)
            val distance = milestone(300_500, 2, AnnouncementChannel.DISTANCE)
            val events = listOf(time, distance).let { if (reverse) it.reversed() else it }
            queue.play(listOf(events[0]), RunUnits.KILOMETERS)
            assertTrue(queue.isPlaying)
            assertFalse(output.focus)
            timer.advance(500)
            queue.play(listOf(events[1]), RunUnits.KILOMETERS)
            timer.advance(500)
            assertEquals(1, output.spoken.size)
            assertTrue(output.spoken.single().text.contains("1.00 kilometer"))
            output.complete(true)
            assertFalse(queue.isPlaying)
        }
    }

    @Test fun goalRemovesMergedOrReadyQueuedMilestonesAndPreservesUnrelatedSpeech() {
        val output = Output(); val timer = TestCueScheduler(); val queue = RunCueQueue(output, timer)
        queue.play(listOf(event(RunEventType.STARTED, 0)))
        queue.play(listOf(milestone(), milestone(300_500, 3, AnnouncementChannel.DISTANCE), milestone(600_000, 4)))
        timer.advance(2_000)
        queue.play(listOf(event(RunEventType.GOAL_REACHED, 300_000, 5)))
        assertEquals("Run started", output.spoken.single().text)
        output.complete(true)
        assertTrue(output.spoken.last().text.startsWith("Time: 10 minutes"))
        output.complete(true)
        assertTrue(output.spoken.last().text.startsWith("Goal reached."))
        output.complete(true)
        assertEquals(3, output.spoken.size)
        assertFalse(output.focus)
    }

    @Test fun sameChannelBatchRetainsSeparateCrossingsEvenWithinTolerance() {
        val output = Output(); val timer = TestCueScheduler(); val queue = RunCueQueue(output, timer)
        queue.play(listOf(milestone(300_000, 1, AnnouncementChannel.DISTANCE),
            milestone(300_500, 2, AnnouncementChannel.DISTANCE)))
        timer.advance(1_000)
        output.complete(true)
        assertEquals(2, output.spoken.size)
    }

    @Test fun finishCancelCloseAndRunReplacementInvalidateWaitingMilestones() {
        for (action in listOf("finish", "cancel", "close", "replace")) {
            val output = Output(); val timer = TestCueScheduler(); val queue = RunCueQueue(output, timer)
            queue.play(listOf(milestone()))
            when (action) {
                "finish" -> queue.play(listOf(event(RunEventType.FINISHED, 300_100, 2)))
                "cancel" -> queue.cancel()
                "close" -> queue.close()
                "replace" -> queue.play(listOf(event(RunEventType.STARTED, 0, run = "other")))
            }
            timer.fireCancelledCallbacks()
            timer.advance(2_000)
            assertEquals(if (action in listOf("finish", "replace")) 1 else 0, output.spoken.size)
            assertEquals(0, timer.pendingCount)
            output.complete(true)
            assertFalse(queue.isPlaying)
            assertFalse(output.focus)
        }
    }

    @Test fun waitingMilestoneDoesNotSignalIdleUntilItsSpeechCompletes() {
        val output = Output(); val timer = TestCueScheduler(); var idle = 0
        val queue = RunCueQueue(output, timer) { idle++ }
        queue.play(listOf(event(RunEventType.STARTED, 0), milestone()))
        output.complete(true)
        assertEquals(0, idle)
        assertTrue(queue.isPlaying)
        timer.advance(1_000)
        output.complete(true)
        assertEquals(1, idle)
    }

    @Test fun actualControllerGoalsSuppressBothChannelsAndFutureMilestonesContinue() {
        for (units in RunUnits.entries) for (timeGoal in listOf(false, true)) {
            var now = 0L
            val run = RunController("run", RunSettings(RunMode.OUTDOOR, units, 0,
                if (timeGoal) RunGoal.Time(300_000) else RunGoal.Distance(units.metersPerUnit), null,
                announcementsEnabled = true, announcementSelection = AnnouncementSelection(distanceEnabled = true)),
                RunClock { RunTime(now, now) })
            val output = Output(); val timer = TestCueScheduler(); val queue = RunCueQueue(output, timer)
            queue.play(run.start().events, units); output.complete(true)
            val segment = run.selectSource(DistanceSource.GPS).snapshot.currentSegmentId!!
            run.record(RunMeasurement.Gps(segment, 0, 0.0))
            now = 300_000
            queue.play(run.record(RunMeasurement.Gps(segment, now, units.metersPerUnit)).events, units)
            assertEquals(2, output.spoken.size)
            assertTrue(output.spoken.last().text.startsWith("Goal reached."))
            output.complete(true); timer.advance(2_000)
            assertEquals(2, output.spoken.size)
            now = 600_000
            queue.play(run.record(RunMeasurement.Gps(segment, now, units.metersPerUnit * 2)).events, units)
            timer.advance(1_000)
            assertEquals(3, output.spoken.size)
            assertTrue(output.spoken.last().text.startsWith("Time: 10 minutes"))
        }
    }

    @Test fun lateOppositeChannelPairsOnlyOnceAndDoesNotSwallowFurtherSameChannelCrossings() {
        val output = Output(); val timer = TestCueScheduler(); val queue = RunCueQueue(output, timer)
        queue.play(listOf(milestone())); timer.advance(1_000); output.complete(true)
        queue.play(listOf(milestone(300_000, 2, AnnouncementChannel.DISTANCE)))
        assertEquals(1, output.spoken.size)
        queue.play(listOf(milestone(300_500, 3, AnnouncementChannel.DISTANCE)))
        timer.advance(1_000)
        assertEquals(2, output.spoken.size)
    }
}
