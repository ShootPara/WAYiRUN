package com.example.runningapp.tracking

import org.junit.Assert.*
import org.junit.Test

class RunEntryStateTest {
    @Test fun ordinaryBackgroundReturnDoesNotRestoreFinishedRun() {
        val state = RunEntryState()
        assertTrue(state.needsEntry)
        assertNull(state.resumed())
        state.stopped(false, "completed")
        assertTrue(state.needsEntry)
        assertNull(state.resumed())
    }

    @Test fun recreationPreservesCurrentSummaryOnce() {
        val state = RunEntryState()
        state.resumed()
        state.stopped(true, "completed")
        assertEquals("completed", state.resumed())
        assertNull(state.resumed())
        state.stopped(false, "completed")
        assertNull(state.resumed())
    }

    @Test fun externalReturnRestoresOnlyItsRunAndConsumesTheMarker() {
        val state = RunEntryState()
        state.externalRunId = "photo-run"
        state.stopped(false, "photo-run")
        assertEquals("photo-run", state.resumed())
        assertNull(state.externalRunId)
        state.stopped(false, "photo-run")
        assertNull(state.resumed())
    }
}
