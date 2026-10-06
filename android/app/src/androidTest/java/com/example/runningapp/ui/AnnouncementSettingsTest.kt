package com.example.runningapp.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.domain.*
import com.example.runningapp.tracking.TrackingView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AnnouncementSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choicesFollowUnitsPersistAndDoNotChangeTheStartedRun() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("local-settings", Context.MODE_PRIVATE)
        val keys = listOf("units", "goal", "stride-entry", "mode", "stride-unit", "countdown",
            "announcements-enabled", "announcement-interval", "auto-pause-enabled") + AnnouncementPreferences.keys
        val before = keys.associateWith { prefs.all[it] }
        val visible = mutableStateOf(true)
        val view = mutableStateOf(TrackingView(ready = true))
        var captured: RunSettings? = null
        try {
            prefs.edit().apply { AnnouncementPreferences.keys.forEach { remove(it) } }.commit()
            prefs.edit().putString("units", "Miles").putString("goal", "None")
                .putString("stride-entry", "").putString("mode", "INDOOR").putInt("countdown", 0)
                .remove("announcements-enabled").remove("announcement-interval").remove("auto-pause-enabled").commit()
            compose.setContent {
                if (visible.value) WayirunApp(viewOverride = view.value, onCommand = { _, _ -> }) { settings ->
                    captured = settings
                    view.value = TrackingView(RunController("announcement-ui", settings, RunClock { RunTime(0, 0) })
                        .start().snapshot, ready = true)
                }
            }
            compose.onNodeWithTag("announcements-enabled").assertDoesNotExist()
            compose.onNodeWithTag("auto-pause-enabled").assertDoesNotExist()
            compose.onNodeWithTag("settings-gear").performClick()
            compose.onNodeWithTag("settings-category-run-tracking-header").performClick()
            compose.onNodeWithTag("auto-pause-enabled").performScrollTo().assertIsOn().performClick().assertIsOff()
            compose.onNodeWithTag("settings-category-audio-milestones-header").performScrollTo().performClick()
            compose.onNodeWithTag("announcements-enabled").performScrollTo().assertIsOn()
            compose.onNodeWithTag("announcement-FIVE_MINUTES").assertIsSelected()
            compose.onNodeWithTag("announcement-time-enabled").assertIsOn()
            compose.onNodeWithTag("announcement-distance-enabled").performScrollTo().assertIsOff().performClick().assertIsOn()
            compose.onNodeWithText("0.5 mile").assertExists()
            compose.onNodeWithText("1 mile").assertExists()
            compose.onNodeWithTag("settings-category-run-display-header").performScrollTo().performClick()
            compose.onNodeWithText("Kilometers").performScrollTo().performClick()
            compose.onNodeWithText("0.5 kilometer").assertExists()
            compose.onNodeWithText("1 kilometer").assertExists()
            compose.onNodeWithTag("announcement-HALF_UNIT").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("announcements-enabled").performScrollTo().performClick().assertIsOff()
            compose.onNodeWithTag("announcement-HALF_UNIT").assertIsNotEnabled().assertIsSelected()
            compose.onNodeWithTag("announcements-enabled").performClick().assertIsOn()
            compose.onNodeWithTag("settings-gear").performClick()
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            compose.runOnIdle { visible.value = true }
            compose.onNodeWithTag("settings-gear").performClick()
            compose.onNodeWithTag("settings-category-run-tracking-header").performClick()
            compose.onNodeWithTag("settings-category-audio-milestones-header").performScrollTo().performClick()
            compose.onNodeWithTag("announcement-HALF_UNIT").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("auto-pause-enabled").performScrollTo().assertIsOff()
            compose.onNodeWithTag("announcements-enabled").performScrollTo().assertIsOn()
            compose.onNodeWithTag("settings-gear").performClick()
            compose.onNodeWithTag("start").performScrollTo().performClick()
            compose.runOnIdle {
                assertTrue(captured!!.announcementsEnabled)
                assertFalse(captured!!.autoPauseEnabled)
                assertEquals(AnnouncementSelection(distanceEnabled = true, distanceInterval = AnnouncementInterval.HALF_UNIT),
                    captured!!.effectiveAnnouncements())
                assertEquals(RunUnits.KILOMETERS, captured!!.units)
            }
            compose.onNodeWithTag("announcement-time-enabled").assertDoesNotExist()
            compose.onNodeWithTag("announcement-distance-enabled").assertDoesNotExist()
            compose.onNodeWithTag("settings-gear").performClick()
            compose.onNodeWithTag("settings-category-run-display-header").performClick()
            compose.onNodeWithText("Miles").performScrollTo().performClick()
            compose.onNodeWithTag("settings-category-run-tracking-header").performScrollTo().performClick()
            compose.onNodeWithTag("auto-pause-enabled").performScrollTo().performClick().assertIsOn()
            compose.onNodeWithTag("settings-category-audio-milestones-header").performScrollTo().performClick()
            compose.onNodeWithTag("announcement-TEN_MINUTES").performScrollTo().performClick()
            compose.onNodeWithTag("announcements-enabled").performScrollTo().performClick()
            compose.runOnIdle {
                assertFalse(prefs.getBoolean("announcements-enabled", true))
                assertEquals(AnnouncementInterval.TEN_MINUTES, AnnouncementPreferences.read(prefs).timeInterval)
                assertTrue(view.value.snapshot!!.settings.announcementsEnabled)
                assertTrue(prefs.getBoolean("auto-pause-enabled", false))
                assertFalse(view.value.snapshot!!.settings.autoPauseEnabled)
                assertEquals(RunUnits.KILOMETERS, view.value.snapshot!!.settings.units)
                assertEquals(AnnouncementSelection(distanceEnabled = true, distanceInterval = AnnouncementInterval.HALF_UNIT),
                    view.value.snapshot!!.settings.effectiveAnnouncements())
            }
            compose.onNodeWithTag("settings-gear").performClick()
            compose.runOnIdle { view.value = view.value.copy(snapshot = view.value.snapshot!!.copy(state = RunState.FINISHED)) }
            compose.onNodeWithTag("announcement-time-enabled").assertDoesNotExist()
            compose.onNodeWithTag("announcement-distance-enabled").assertDoesNotExist()
        } finally {
            prefs.edit().apply {
                before.forEach { (key, value) -> when (value) {
                    null -> remove(key)
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                } }
            }.commit()
        }
    }

    @Test fun dualControlsRemainReachableAtTwoHundredPercentInBothThemes() {
        val dark = mutableStateOf(false)
        val scale = mutableStateOf(1f)
        val selection = mutableStateOf(AnnouncementSelection(distanceEnabled = true))
        compose.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides androidx.compose.ui.unit.Density(density.density, scale.value)
            ) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Surface(Modifier.verticalScroll(rememberScrollState())) {
                        AnnouncementSettings(true, selection.value, false, {}, { selection.value = it })
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (fontScale in listOf(1f, 2f)) {
            compose.runOnIdle { dark.value = night; scale.value = fontScale }
            for (tag in listOf("announcements-enabled", "announcement-time-enabled", "announcement-FIVE_MINUTES",
                "announcement-TEN_MINUTES", "announcement-distance-enabled", "announcement-HALF_UNIT", "announcement-ONE_UNIT")) {
                compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            }
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = File(context.getExternalFilesDir(null), "milestone-13").apply { mkdirs() }
            File(directory, "announcements-$night-$fontScale.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
