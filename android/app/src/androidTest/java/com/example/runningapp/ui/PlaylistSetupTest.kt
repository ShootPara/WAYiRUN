package com.example.runningapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistSetupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opensAndSavesCanonicalLinkAndClearRemovesIt() {
        val saved = mutableStateOf("")
        var opened: String? = null
        compose.setContent { MaterialTheme { Column {
            PlaylistSetup(saved.value, false, { saved.value = it }, { opened = it; null })
        } } }
        compose.onNodeWithTag("open-playlist").assertIsNotEnabled()
        compose.onNodeWithTag("playlist-link").performTextInput("https://music.youtube.com/playlist?list=PL123&si=abc")
        compose.onNodeWithTag("open-playlist").performClick()
        compose.runOnIdle { assertEquals("https://music.youtube.com/playlist?list=PL123", saved.value); assertEquals(saved.value, opened) }
        compose.onNodeWithText("Clear playlist").performClick()
        compose.runOnIdle { assertEquals("", saved.value) }
        compose.onNodeWithTag("open-playlist").assertIsNotEnabled()
    }

    @Test fun launchFailureKeepsSavedLinkAndDisplaysMessage() {
        var saved = ""
        compose.setContent { MaterialTheme { Column {
            PlaylistSetup("https://music.youtube.com/playlist?list=PL123", false, { saved = it }, { "YouTube Music unavailable" })
        } } }
        compose.onNodeWithTag("open-playlist").performClick()
        compose.onNodeWithText("YouTube Music unavailable").assertExists()
        compose.runOnIdle { assertTrue(saved.endsWith("PL123")) }
    }
}
