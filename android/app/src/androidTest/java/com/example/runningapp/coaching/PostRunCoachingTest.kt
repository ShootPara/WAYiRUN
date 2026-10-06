package com.example.runningapp.coaching

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class PostRunCoachingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun wake() { compose.activityRule.scenario.onActivity {
        it.setTurnScreenOn(true); it.setShowWhenLocked(true)
        it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }
    @Test fun optOutDoesNotStartPlaybackOrNetworkAndPersistsSelection() = runBlocking {
        val context = compose.activity
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val id = UUID.randomUUID().toString()
        try {
            withContext(Dispatchers.Main) {
                val coach = PostRunCoaching(context, scope, { false }) {}
                coach.start(id, null, false)
                assertFalse(coach.view.busy)
                coach.start(id, null, true)
                assertFalse(coach.view.busy)
            }
            val saved = context.getSharedPreferences("coaching-attempt", 0)
            assertEquals(id, saved.getString("run", null)); assertFalse(saved.getBoolean("selected", true))
        } finally { scope.cancel() }
    }
    @Test fun selectedLocalRunWaitsForCompletionCueAndPlaysOnboardAudioAfterDismissal() = runBlocking {
        val context = compose.activity
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val id = UUID.randomUUID().toString()
        val repository = RunRepository(RunDatabase.get(context).runs())
        val run = RunController(id, RunSettings(RunMode.INDOOR, RunUnits.MILES, 0, RunGoal.None, null), RunClock { RunTime(0, 0) })
        run.start(); run.finish(); repository.save(run.checkpoint(), "coaching-test", "UTC", 0, false)
        val cue = AtomicBoolean(true)
        lateinit var coach: PostRunCoaching
        try {
            withContext(Dispatchers.Main) {
                coach = PostRunCoaching(context, scope, { cue.get() }) {}
                coach.start(id, null, true)
                assertTrue(coach.view.busy); coach.dismiss()
                assertFalse(coach.view.visible); assertTrue(coach.view.busy)
            }
            delay(500)
            withContext(Dispatchers.Main) { assertTrue(coach.view.busy) }
            cue.set(false)
            withTimeout(25000) { while (withContext(Dispatchers.Main) { coach.view.busy }) delay(100) }
            assertEquals("played", context.getSharedPreferences("coaching-attempt", 0).getString("state", null))
            withContext(Dispatchers.Main) { coach.start(id, null, true); assertFalse(coach.view.busy) }
        } finally {
            withContext(Dispatchers.Main) { runCatching { coach.cancel() } }
            scope.cancel(); repository.discard(id, "coaching-test")
        }
    }
}
