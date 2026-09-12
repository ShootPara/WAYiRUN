package com.example.runningapp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.runningapp.domain.*
import com.example.runningapp.tracking.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun run(): RunController = RunController("ui-test",
        RunSettings(RunMode.INDOOR, RunUnits.KILOMETERS, 0, RunGoal.None, null), RunClock { RunTime(0, 0) })

    @Test fun tappingFinishDoesNotFinishButFullSwipeDoes() {
        val run = run(); run.start(); run.pause()
        val view = mutableStateOf(TrackingView(run.snapshot(), ready = true))
        var finishes = 0
        compose.setContent { WayirunApp(view.value, { action, _ ->
            if (action == TrackingService.FINISH) { finishes++; view.value = TrackingView(run.finish().snapshot, ready = true) }
        }) { } }
        compose.onNodeWithTag("finish-slider").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, finishes) }
        compose.onNodeWithTag("finish-slider").performTouchInput {
            swipe(Offset(width * 0.05f, height / 2f), Offset(width * 0.98f, height / 2f), 600)
        }
        compose.onNodeWithText("Run saved").assertExists()
        compose.runOnIdle { assertEquals(1, finishes) }
    }

    @Test fun shortSwipeCancelsAndInterruptedRunCanResume() {
        val run = run(); run.start(); run.pause()
        val view = mutableStateOf(TrackingView(run.snapshot(), interrupted = true, ready = true))
        var command: String? = null
        compose.setContent { WayirunApp(view.value, { action, _ ->
            command = action
            if (action == TrackingService.RESUME) view.value = TrackingView(run.resume().snapshot, ready = true)
        }) { } }
        compose.onNodeWithText("Tracking was interrupted.", substring = true).assertExists()
        compose.onNodeWithTag("finish-slider").performScrollTo().performTouchInput {
            swipe(Offset(width * 0.1f, height / 2f), Offset(width * 0.4f, height / 2f), 300)
        }
        compose.runOnIdle { assertNull(command) }
        compose.onNodeWithTag("resume").performScrollTo().performClick()
        compose.onNodeWithTag("pause").assertExists()
        compose.onNodeWithText("Distance unavailable").assertExists()
    }
}
