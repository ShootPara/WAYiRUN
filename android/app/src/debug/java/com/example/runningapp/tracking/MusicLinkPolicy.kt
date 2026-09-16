package com.example.runningapp.tracking

import com.example.runningapp.domain.RunState

internal enum class PlayerStatus { PLAYING, PAUSED, UNAVAILABLE }
internal enum class MusicAction { PAUSE_RUN, RESUME_RUN, PAUSE_PLAYER, RESUME_PLAYER }

/** A fresh session can only become linked while the run is running and music is playing. */
internal class MusicLinkPolicy {
    var linked = false
        private set
    private var run: RunState? = null
    private var player = PlayerStatus.UNAVAILABLE
    private var expected: PlayerStatus? = null
    private var cue = false

    fun detach() { linked = false; player = PlayerStatus.UNAVAILABLE; expected = null; cue = false }

    fun runChanged(state: RunState?): List<MusicAction> {
        val previous = run
        run = state
        if (state == previous) return emptyList()
        if (state == RunState.FINISHED || state == null || state == RunState.READY) {
            val stop = linked && player == PlayerStatus.PLAYING
            detach()
            return if (stop) listOf(MusicAction.PAUSE_PLAYER) else emptyList()
        }
        if (state == RunState.RUNNING) {
            if (linked && player == PlayerStatus.PAUSED) {
                expected = PlayerStatus.PLAYING
                return listOf(MusicAction.RESUME_PLAYER)
            }
            if (player == PlayerStatus.PLAYING) linked = true
        }
        if (state == RunState.PAUSED && linked && player == PlayerStatus.PLAYING) {
            expected = PlayerStatus.PAUSED
            return listOf(MusicAction.PAUSE_PLAYER)
        }
        return emptyList()
    }

    fun playerChanged(state: PlayerStatus): List<MusicAction> {
        if (state == PlayerStatus.UNAVAILABLE) { detach(); return emptyList() }
        if (expected != null) {
            if (state != expected) return emptyList() // Ignore stale callbacks while a command is in flight.
            expected = null; player = state
            return emptyList()
        }
        val previous = player
        player = state
        if (run == RunState.RUNNING && state == PlayerStatus.PLAYING) linked = true
        if (!linked || state == previous) return emptyList()
        if (state == PlayerStatus.PAUSED && run == RunState.RUNNING) {
            if (cue) return emptyList()
            run = RunState.PAUSED
            return listOf(MusicAction.PAUSE_RUN)
        }
        if (state == PlayerStatus.PLAYING && run == RunState.PAUSED) {
            run = RunState.RUNNING
            return listOf(MusicAction.RESUME_RUN)
        }
        return emptyList()
    }

    fun cueStarted() { cue = true }
    // Explicit user choice: even a persistent/manual pause during a cue leaves tracking running.
    fun cueSettled(): List<MusicAction> {
        cue = false
        return emptyList()
    }

    fun commandExpired(state: PlayerStatus): List<MusicAction> {
        if (expected != null && state != expected) {
            detach()
            return emptyList()
        }
        expected = null
        // Missing acknowledgement is not permission to reverse the user's run command.
        player = state
        return emptyList()
    }
}
