package com.example.runningapp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionSetupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deniedPermissionsAllowContinuationAndMusicIsSeparate() {
        var continued = 0
        var music = 0
        compose.setContent {
            PermissionSetup(listOf("Location" to "Not allowed", "YouTube Music controls" to "Not enabled"),
                false, null, {}, {}, { music++ }, { continued++ })
        }
        compose.onNodeWithText("Not enabled").assertExists()
        compose.onNodeWithText("Enable YouTube Music controls").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, music); assertEquals(0, continued) }
        compose.onNodeWithText("Continue to WAYiRUN").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, continued) }
    }

    @Test fun pendingSystemPromptPreventsDuplicateRequestsAndSettingsNavigation() {
        compose.setContent { PermissionSetup(emptyList(), true, null, {}, {}, {}, {}) }
        listOf("Allow tracking permissions", "App permissions", "Enable YouTube Music controls", "Continue to WAYiRUN")
            .forEach { compose.onNodeWithText(it).performScrollTo().assertIsNotEnabled() }
    }
}
