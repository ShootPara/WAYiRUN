package com.example.runningapp.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.domain.*
import com.example.runningapp.tracking.TrackingView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class NewRunControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedModeAndGoalPersistAndAreCapturedWhenStarting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("local-settings", Context.MODE_PRIVATE)
        val keys = listOf("mode", "units", "goal", "goal-target", "goal-target-Time", "goal-target-Distance", "stride-entry")
        val before = keys.associateWith { prefs.getString(it, null) }
        val visible = mutableStateOf(true)
        var started: RunSettings? = null
        try {
            prefs.edit().putString("mode", "OUTDOOR").putString("units", "Miles")
                .putString("goal", "None").putString("stride-entry", "").commit()
            compose.setContent {
                if (visible.value) WayirunApp(viewOverride = TrackingView(ready = true)) { started = it }
            }
            compose.onNodeWithTag("run-mode-Outdoor").assertIsSelected()
            compose.onNodeWithTag("run-goal-None").assertIsSelected()
            compose.onNodeWithTag("run-mode-Indoor").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("run-type-status").assertTextEquals("Indoor")
            compose.onNodeWithTag("run-goal-Time").performScrollTo().performClick()
            compose.onNodeWithTag("goal-target").performScrollTo().performTextReplacement("25")
            compose.onNodeWithTag("run-goal-Distance").performScrollTo().performClick()
            compose.onNodeWithTag("goal-target").performScrollTo().performTextReplacement("3")
            compose.onNodeWithTag("run-goal-None").performScrollTo().performClick()
            compose.onNodeWithTag("goal-target").assertDoesNotExist()
            compose.onNodeWithTag("run-goal-Time").performScrollTo().performClick()
            compose.onNodeWithTag("goal-target").assertTextContains("25")
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            compose.runOnIdle { visible.value = true }
            compose.onNodeWithTag("run-mode-Indoor").assertIsSelected()
            compose.onNodeWithTag("run-goal-Time").assertIsSelected()
            compose.onNodeWithTag("start").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(RunMode.INDOOR, started?.mode)
                assertEquals(RunUnits.MILES, started?.units)
                assertEquals(RunGoal.Time(25 * 60_000L), started?.goal)
            }
        } finally {
            prefs.edit().apply {
                before.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
            }.commit()
        }
    }

    @Test fun narrowLayoutKeepsEveryChoiceSquareReadableAndSelectableAtLargeText() {
        val scale = mutableStateOf(1f)
        val chosen = mutableStateOf("None")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                MaterialTheme {
                    Surface {
                        Column(Modifier.width(272.dp).height(560.dp).testTag("controls-fixture")
                            .verticalScroll(rememberScrollState())) {
                            RunSetupChoices(listOf("Outdoor" to "🌳", "Indoor" to "🏠"), "Outdoor", "run-mode") {}
                            Spacer(Modifier.height(18.dp))
                            RunSetupChoices(listOf("None" to "♾️", "Time" to "⏱️", "Distance" to "📏"),
                                chosen.value, "run-goal") { chosen.value = it }
                        }
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            compose.runOnIdle { scale.value = fontScale }
            for ((tag, label) in listOf("run-mode-Outdoor" to "Outdoor", "run-mode-Indoor" to "Indoor",
                "run-goal-None" to "None", "run-goal-Time" to "Time", "run-goal-Distance" to "Distance")) {
                val tile = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                val bounds = tile.fetchSemanticsNode().boundsInRoot
                assertEquals("$tag is square at $fontScale", bounds.width, bounds.height, 1f)
                val text = compose.onNodeWithText(label, useUnmergedTree = true)
                text.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    assertTrue(action(layouts))
                    assertTrue("$label must stay on one unellipsized line at $fontScale",
                        layouts.isNotEmpty() && layouts.all { it.lineCount == 1 && !it.isLineEllipsized(0) })
                }
                val textBounds = text.fetchSemanticsNode().boundsInRoot
                assertTrue(textBounds.left >= bounds.left && textBounds.right <= bounds.right)
                assertTrue(textBounds.top >= bounds.top && textBounds.bottom <= bounds.bottom)
                if (tag.startsWith("run-goal")) tile.performClick().assertIsSelected()
                saveScreenshot("controls-$fontScale-$tag", "controls-fixture")
            }
        }
    }

    @Test fun indicatorsHaveIndependentLabelsPositionsAndThemeReadableColors() {
        val dark = mutableStateOf(false)
        val indoor = mutableStateOf(false)
        val online = mutableStateOf(true)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.width(272.dp)) {
                    RunStatusIndicators(if (indoor.value) RunMode.INDOOR else RunMode.OUTDOOR, online.value, dark.value)
                }
            }
        }
        for (night in listOf(false, true)) {
            for (inside in listOf(false, true)) {
                compose.runOnIdle { dark.value = night; indoor.value = inside; online.value = !inside }
                val type = compose.onNodeWithTag("run-type-status").assertTextEquals(if (inside) "Indoor" else "Outdoor")
                val network = compose.onNodeWithTag("connectivity-status").assertTextEquals(if (inside) "Fallback" else "Online")
                assertTrue(type.fetchSemanticsNode().boundsInRoot.right < network.fetchSemanticsNode().boundsInRoot.left)
                val green = if (night) Color(0xFF82D998) else Color(0xFF176B36)
                assertTextColor(type, if (!inside) green else if (night) Color(0xFF91C7FF) else Color(0xFF175DA8))
                assertTextColor(network, if (!inside) green else if (night) Color(0xFFFFAAA5) else Color(0xFFB3261E))
                saveScreenshot("status-$night-$inside", "run-status")
            }
        }
    }

    private fun assertTextColor(node: SemanticsNodeInteraction, expected: Color) {
        val pixels = node.captureToImage().toPixelMap()
        var matches = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val pixel = pixels[x, y]
            if (abs(pixel.red - expected.red) < 0.04f && abs(pixel.green - expected.green) < 0.04f &&
                abs(pixel.blue - expected.blue) < 0.04f) matches++
        }
        assertTrue("Expected rendered status color $expected", matches > 5)
    }

    private fun saveScreenshot(name: String, tag: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(null), "milestone-10").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
