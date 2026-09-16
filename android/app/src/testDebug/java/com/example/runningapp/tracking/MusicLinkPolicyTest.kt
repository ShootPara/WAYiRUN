package com.example.runningapp.tracking

import com.example.runningapp.domain.RunState
import org.junit.Assert.*
import org.junit.Test

class MusicLinkPolicyTest {
    private fun playing() = MusicLinkPolicy().also {
        it.playerChanged(PlayerStatus.PLAYING); it.runChanged(RunState.RUNNING)
    }
    @Test fun musicOffAtStartNeverPausesRunOrStartsMusic() {
        for (state in listOf(PlayerStatus.PAUSED, PlayerStatus.UNAVAILABLE)) {
            val policy = MusicLinkPolicy()
            assertTrue(policy.playerChanged(state).isEmpty())
            assertTrue(policy.runChanged(RunState.RUNNING).isEmpty())
            repeat(10) { assertTrue(policy.playerChanged(state).isEmpty()) }
            assertFalse(policy.linked)
        }
    }
    @Test fun musicStartingLaterEstablishesLinkWithoutRestartingRun() {
        val policy = MusicLinkPolicy(); policy.runChanged(RunState.RUNNING)
        assertTrue(policy.playerChanged(PlayerStatus.PLAYING).isEmpty())
        assertTrue(policy.linked)
        assertEquals(listOf(MusicAction.PAUSE_RUN), policy.playerChanged(PlayerStatus.PAUSED))
        repeat(10) { assertTrue(policy.playerChanged(PlayerStatus.PAUSED).isEmpty()) }
        assertEquals(listOf(MusicAction.RESUME_RUN), policy.playerChanged(PlayerStatus.PLAYING))
        assertTrue(policy.runChanged(RunState.RUNNING).isEmpty())
    }
    @Test fun runPauseEchoAndStalePlayingCannotResumeRun() {
        val policy = playing()
        assertEquals(listOf(MusicAction.PAUSE_PLAYER), policy.runChanged(RunState.PAUSED))
        repeat(10) { assertTrue(policy.playerChanged(PlayerStatus.PLAYING).isEmpty()) }
        assertTrue(policy.playerChanged(PlayerStatus.PAUSED).isEmpty())
        assertTrue(policy.runChanged(RunState.PAUSED).isEmpty())
        assertEquals(listOf(MusicAction.RESUME_RUN), policy.playerChanged(PlayerStatus.PLAYING))
    }
    @Test fun runResumeEchoCannotToggleRunAndFailedAckDoesNotReverseIntent() {
        val policy = playing(); policy.runChanged(RunState.PAUSED); policy.playerChanged(PlayerStatus.PAUSED)
        assertEquals(listOf(MusicAction.RESUME_PLAYER), policy.runChanged(RunState.RUNNING))
        assertTrue(policy.playerChanged(PlayerStatus.PAUSED).isEmpty())
        assertTrue(policy.commandExpired(PlayerStatus.PAUSED).isEmpty())
        assertFalse(policy.linked)
        assertTrue(policy.playerChanged(PlayerStatus.PAUSED).isEmpty())
        assertTrue(policy.playerChanged(PlayerStatus.PLAYING).isEmpty())
    }
    @Test fun allPausesDuringCueRemainExemptEvenWhenMusicStaysPaused() {
        val policy = playing(); policy.cueStarted()
        assertTrue(policy.playerChanged(PlayerStatus.PAUSED).isEmpty())
        assertTrue(policy.cueSettled().isEmpty())
        assertTrue(policy.playerChanged(PlayerStatus.PAUSED).isEmpty())
        assertTrue(policy.playerChanged(PlayerStatus.PLAYING).isEmpty())
        assertEquals(listOf(MusicAction.PAUSE_RUN), policy.playerChanged(PlayerStatus.PAUSED))
    }
    @Test fun explicitRunPauseDuringCueStillPausesMusic() {
        val policy = playing(); policy.cueStarted()
        assertEquals(listOf(MusicAction.PAUSE_PLAYER), policy.runChanged(RunState.PAUSED))
        assertTrue(policy.playerChanged(PlayerStatus.PAUSED).isEmpty())
        assertTrue(policy.cueSettled().isEmpty())
    }
    @Test fun finishedRunCannotRestartAndNextSilentRunStaysIndependent() {
        val policy = playing()
        assertEquals(listOf(MusicAction.PAUSE_PLAYER), policy.runChanged(RunState.FINISHED))
        assertTrue(policy.playerChanged(PlayerStatus.PLAYING).isEmpty())
        policy.runChanged(null); policy.playerChanged(PlayerStatus.PAUSED)
        assertTrue(policy.runChanged(RunState.RUNNING).isEmpty()); assertFalse(policy.linked)
    }
    @Test fun sessionLossAndRecoveryCannotResumePausedRun() {
        val policy = playing(); policy.runChanged(RunState.PAUSED)
        policy.detach()
        assertTrue(policy.playerChanged(PlayerStatus.PLAYING).isEmpty()); assertFalse(policy.linked)
        val recovered = MusicLinkPolicy(); recovered.runChanged(RunState.PAUSED)
        assertTrue(recovered.playerChanged(PlayerStatus.PLAYING).isEmpty()); assertFalse(recovered.linked)
    }
    @Test fun countdownWithMusicOffDoesNotArmLink() {
        val policy = MusicLinkPolicy(); policy.playerChanged(PlayerStatus.PAUSED)
        assertTrue(policy.runChanged(RunState.COUNTDOWN).isEmpty())
        assertTrue(policy.runChanged(RunState.RUNNING).isEmpty()); assertFalse(policy.linked)
    }

    @Test fun cueFromOldSessionCannotSuppressPauseInReplacementSession() {
        val policy = playing(); policy.cueStarted(); policy.detach()
        assertTrue(policy.playerChanged(PlayerStatus.PLAYING).isEmpty())
        assertEquals(listOf(MusicAction.PAUSE_RUN), policy.playerChanged(PlayerStatus.PAUSED))
    }
}
