package com.example.runningapp.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.domain.*
import com.example.runningapp.tracking.TrackingView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsExperienceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun settingsRetainPlaylistAndPermissionsWithoutMusicControlSetup() {
        compose.setContent { WayirunApp(viewOverride = TrackingView(ready = true)) {} }
        compose.onNodeWithTag("settings-gear").performClick()
        compose.onNodeWithTag("playlist-link").performScrollTo().assertExists()
        compose.onNodeWithText("Request missing permissions").performScrollTo().assertExists()
        compose.onNodeWithText("App permissions", substring = false).assertExists()
        compose.onNodeWithText("Weather data by Open-Meteo.com").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("CC BY 4.0", substring = true).performScrollTo().assertHasClickAction()
        compose.onNodeWithText("YouTube Music control access").assertDoesNotExist()
        compose.onNodeWithText("Optional music controls", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Music controls off", substring = true).assertDoesNotExist()
    }
    @Test fun gearClosesAndEveryEditedSettingSurvivesCompositionReopenWithoutSaving() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("local-settings", Context.MODE_PRIVATE)
        val visible = mutableStateOf(true)
        compose.setContent { if (visible.value) WayirunApp(viewOverride = TrackingView(ready = true)) {} }
        compose.onNodeWithTag("settings-screen").assertDoesNotExist()
        compose.onNodeWithTag("playlist-link").assertDoesNotExist()
        compose.onNodeWithTag("run-mode-Indoor").performScrollTo().performClick()
        compose.onNodeWithTag("run-goal-Time").performScrollTo().performClick()
        compose.onNodeWithTag("goal-target").performScrollTo().performTextReplacement("25")
        compose.onNodeWithTag("settings-gear").performClick()
        compose.onNodeWithText("Miles").performScrollTo().performClick()
        compose.onNodeWithText("Time").assertDoesNotExist()
        compose.onNodeWithTag("countdown-setting").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        compose.onNodeWithTag("playlist-link").performScrollTo().performTextReplacement("https://music.youtube.com/playlist?list=PLpersist")
        compose.onNodeWithTag("stride").performScrollTo().performTextReplacement("73")
        val wasDark = prefs.getBoolean("dark", false)
        compose.onNodeWithTag("dark-setting").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("INDOOR", prefs.getString("mode", null))
            assertEquals("Miles", prefs.getString("units", null))
            assertEquals("Time", prefs.getString("goal", null))
            assertEquals("25", prefs.getString("goal-target", null))
            assertEquals("73", prefs.getString("stride-entry", null))
            assertEquals(7, prefs.getInt("countdown", -1))
            assertEquals(!wasDark, prefs.getBoolean("dark", false))
            assertEquals("https://music.youtube.com/playlist?list=PLpersist", prefs.getString("music-playlist", null))
        }
        compose.onNodeWithTag("settings-gear").performClick()
        compose.onNodeWithTag("settings-screen").assertDoesNotExist()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("settings-screen").assertDoesNotExist()
        compose.onNodeWithTag("goal-target").assertTextContains("25")
        compose.onNodeWithTag("run-mode-Indoor").assertIsSelected()
        compose.onNodeWithTag("open-playlist").assertIsEnabled()
        compose.onNodeWithTag("settings-gear").performClick()
        compose.onNodeWithTag("playlist-link").assertTextContains("PLpersist", substring = true)
        compose.onNodeWithTag("stride").assertTextContains("73")
    }
    @Test fun openingSettingsDuringRunNeverSendsPauseOrChangesRunSnapshot() {
        val controller = RunController("settings-active", RunSettings(RunMode.INDOOR, RunUnits.KILOMETERS, 0, RunGoal.None, null), RunClock { RunTime(0, 0) })
        val snapshot = controller.start().snapshot
        var commands = 0
        compose.setContent { WayirunApp(viewOverride = TrackingView(snapshot, ready = true), onCommand = { _, _ -> commands++ }) {} }
        compose.onNodeWithTag("settings-gear").performClick()
        compose.onNodeWithText("Miles").performScrollTo().performClick()
        compose.onNodeWithTag("settings-gear").performClick()
        compose.onNodeWithTag("pause").assertExists()
        compose.runOnIdle { assertEquals(0, commands); assertEquals(RunUnits.KILOMETERS, snapshot.settings.units) }
    }
}
