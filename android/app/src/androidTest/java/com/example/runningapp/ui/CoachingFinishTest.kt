package com.example.runningapp.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.runningapp.coaching.CoachingView
import com.example.runningapp.domain.*
import com.example.runningapp.tracking.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CoachingFinishTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun wake() { compose.activityRule.scenario.onActivity {
        it.setTurnScreenOn(true); it.setShowWhenLocked(true)
        it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }
    private fun paused(): RunController = RunController("coaching-ui",
        RunSettings(RunMode.INDOOR, RunUnits.MILES, 0, RunGoal.None, null), RunClock { RunTime(0, 0) }).also { it.start(); it.pause() }

    @Test fun coachingDefaultsCheckedAndOptOutUsesDistinctFinishCommand() {
        val run = paused(); var received: String? = null
        compose.setContent { WayirunApp(TrackingView(run.snapshot(), ready = true), { action, _ -> received = action }) {} }
        compose.onNodeWithTag("finish-coaching").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("finish-slider").performScrollTo().performTouchInput {
            swipe(Offset(width * 0.05f, height / 2f), Offset(width * 0.98f, height / 2f), 600)
        }
        compose.runOnIdle { assertEquals(TrackingService.FINISH_WITHOUT_COACHING, received) }
    }
    @Test fun animationDismissalShowsSummaryWhileCoachingRemainsBusy() {
        val run = paused(); run.finish()
        val view = mutableStateOf(TrackingView(run.snapshot(), ready = true, coaching = CoachingView(true, true, "Your coaching")))
        var dismissed = false
        compose.setContent { WayirunApp(view.value, { action, _ ->
            if (action == TrackingService.DISMISS_COACHING) {
                dismissed = true; view.value = view.value.copy(coaching = view.value.coaching.copy(visible = false))
            }
        }) {} }
        compose.onNodeWithTag("coaching-animation").performClick()
        compose.onNodeWithText("Run saved").assertExists()
        compose.runOnIdle { assertTrue(dismissed); assertTrue(view.value.coaching.busy) }
    }
}
