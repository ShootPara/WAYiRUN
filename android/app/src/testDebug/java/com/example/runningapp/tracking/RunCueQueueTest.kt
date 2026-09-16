package com.example.runningapp.tracking

import com.example.runningapp.domain.*
import org.junit.Assert.*
import org.junit.Test

class RunCueQueueTest {
    private class Output : CueOutput {
        val spoken = mutableListOf<AudibleCue>()
        val tones = mutableListOf<AudibleCue>()
        var accept = true
        var throwSpeech = false
        var speechDone: (Boolean) -> Unit = {}
        var toneDone: () -> Unit = {}
        var closed = false
        var grantFocus = true
        var focusHeld = false
        var releases = 0
        override fun begin(): Boolean { focusHeld = grantFocus; return grantFocus }
        override fun end() { if (focusHeld) releases++; focusHeld = false }
        override fun stop() {}
        override fun speak(cue: AudibleCue, completed: (Boolean) -> Unit): Boolean {
            spoken += cue; speechDone = completed
            if (throwSpeech) error("Unavailable engine")
            return accept
        }
        override fun tone(cue: AudibleCue, completed: () -> Unit) { tones += cue; toneDone = completed }
        override fun close() { closed = true }
    }
    private fun event(type: RunEventType, id: Long = 1) = RunEvent(RunEventId("run", id), type, 0, 0.0)

    @Test fun successfulSpeechNeedsNoTone() {
        val output = Output(); val queue = RunCueQueue(output)
        queue.play(listOf(event(RunEventType.STARTED)))
        output.speechDone(true)
        assertEquals("Run started", output.spoken.single().text)
        assertTrue(output.tones.isEmpty())
    }

    @Test fun asynchronousFailureFallsBackOnceAndDoesNotOverlapNextCue() {
        val output = Output(); val queue = RunCueQueue(output)
        queue.play(listOf(event(RunEventType.PAUSED), event(RunEventType.RESUMED, 2)))
        val staleCallback = output.speechDone
        staleCallback(false); staleCallback(false)
        assertEquals(1, output.tones.size)
        assertEquals(1, output.spoken.size)
        output.toneDone()
        staleCallback(true)
        assertEquals("Run resumed", output.spoken.last().text)
        assertEquals(2, output.spoken.size)
        output.speechDone(true)
        assertEquals(1, output.tones.size)
    }

    @Test fun unavailableSpeechSerializesGoalAndFinishTones() {
        val output = Output().apply { accept = false }; val queue = RunCueQueue(output)
        queue.play(listOf(event(RunEventType.GOAL_REACHED), event(RunEventType.FINISHED, 2)))
        assertEquals(1, output.tones.size)
        output.toneDone()
        assertEquals(2, output.tones.size)
        assertTrue(output.tones.all { it.acknowledgement })
        assertTrue(output.tones.last().text.startsWith("Run complete. Time:"))
    }

    @Test fun engineExceptionCannotEscapeIntoRunSaving() {
        val output = Output().apply { throwSpeech = true }; val queue = RunCueQueue(output)
        queue.play(listOf(event(RunEventType.STARTED)))
        assertEquals(1, output.tones.size)
        assertFalse(output.tones.single().acknowledgement)
    }

    @Test fun closeIgnoresLateFailureAndQueuedEvents() {
        val output = Output(); val queue = RunCueQueue(output)
        queue.play(listOf(event(RunEventType.PAUSED), event(RunEventType.RESUMED, 2)))
        queue.close(); output.speechDone(false)
        queue.play(listOf(event(RunEventType.FINISHED, 3)))
        assertTrue(output.closed)
        assertEquals(1, output.spoken.size)
        assertTrue(output.tones.isEmpty())
    }

    @Test fun controllerDuplicatesDoNotProduceExtraCuesAndSplitsStaySilent() {
        val output = Output(); val queue = RunCueQueue(output)
        val controller = RunController("run", RunSettings(RunMode.INDOOR, RunUnits.MILES, 0, RunGoal.None, null),
            RunClock { RunTime(0, 0) })
        queue.play(controller.start().events); output.speechDone(true)
        queue.play(controller.pause().events); output.speechDone(true)
        queue.play(controller.pause().events)
        queue.play(listOf(event(RunEventType.SPLIT_COMPLETED)))
        queue.play(controller.resume().events); output.speechDone(true)
        queue.play(controller.resume().events)
        queue.play(controller.finish().events); output.speechDone(true)
        queue.play(controller.finish().events)
        queue.play(controller.resume().events)
        assertEquals(listOf("Run started", "Run paused", "Run resumed", "Run complete"), output.spoken.map { it.text.substringBefore('.') })
        assertTrue(output.tones.isEmpty())
    }

    @Test fun deniedFocusSkipsSpeechAndToneAndDrainsQueue() {
        val output = Output().apply { grantFocus = false }; var idle = false
        val queue = RunCueQueue(output) { idle = true }
        queue.play(listOf(event(RunEventType.STARTED)))
        assertTrue(output.spoken.isEmpty()); assertTrue(output.tones.isEmpty())
        assertFalse(queue.isPlaying); assertTrue(idle)
    }

    @Test fun focusHeldThroughAsyncFailureAndReleasedAfterFallback() {
        val output = Output(); val queue = RunCueQueue(output)
        queue.play(listOf(event(RunEventType.STARTED)))
        assertTrue(output.focusHeld)
        output.speechDone(false)
        assertTrue(output.focusHeld); assertEquals(0, output.releases)
        output.toneDone()
        assertFalse(output.focusHeld); assertEquals(1, output.releases)
    }

    @Test fun focusLossCancelsWithoutPlayingFallbackOrQueuedSpeech() {
        val output = Output(); val queue = RunCueQueue(output)
        queue.play(listOf(event(RunEventType.STARTED), event(RunEventType.PAUSED, 2)))
        queue.cancel(); output.speechDone(false)
        assertFalse(output.focusHeld); assertFalse(queue.isPlaying)
        assertEquals(1, output.releases); assertEquals(1, output.spoken.size)
        assertTrue(output.tones.isEmpty())
    }

    @Test fun goalSpeaksKnownMilesMetricsInRequestedOrder() {
        val output = Output(); val queue = RunCueQueue(output)
        queue.play(listOf(RunEvent(RunEventId("run", 1), RunEventType.GOAL_REACHED, 1_200_000, 3218.688)), RunUnits.MILES)
        assertEquals("Goal reached. Time: 20 minutes. Distance: 2.00 miles. Average pace: 10 minutes per mile. Keep going until you're finished.", output.spoken.single().text)
    }

    @Test fun finishSpeaksKilometersAndUnavailablePaceHonestly() {
        val output = Output(); val queue = RunCueQueue(output)
        queue.play(listOf(RunEvent(RunEventId("run", 1), RunEventType.FINISHED, 330_000, 1000.0)), RunUnits.KILOMETERS)
        assertEquals("Run complete. Time: 5 minutes, 30 seconds. Distance: 1.00 kilometer. Average pace: 5 minutes, 30 seconds per kilometer.", output.spoken.single().text)
        output.speechDone(true)
        queue.play(listOf(RunEvent(RunEventId("run", 2), RunEventType.FINISHED, 30_000, 0.0)), RunUnits.MILES)
        assertEquals("Run complete. Time: 30 seconds. Distance: 0.00 miles. Average pace: unavailable.", output.spoken.last().text)
    }
}
