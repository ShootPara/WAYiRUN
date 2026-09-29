package com.example.runningapp.sharing

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.storage.RunPublication
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SharingControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun sharingCommandsAreIndependentOfPhotoAndUnshareRemainsReachableWhileBusy() {
        val busy = mutableStateOf(false)
        val commands = mutableListOf<Triple<Boolean?, Boolean?, String?>>()
        compose.setContent {
            MaterialTheme { SharingControls(RunPublication("run", "alice", known = true), null, busy.value) { shared, photo, delivery ->
                commands += Triple(shared, photo, delivery)
            } }
        }
        compose.onNodeWithText("Private").assertExists()
        compose.onNodeWithText("Share run link").performClick()
        compose.onNodeWithText("Copy run link").performClick()
        compose.onNodeWithTag("publication-photo").assertIsOn().performClick()
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithText("Share run link").assertIsNotEnabled()
        compose.onNodeWithText("Unshare").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(Triple(true,null,"share"), Triple(true,null,"copy"), Triple(null,false,null), Triple(false,null,null)), commands)
        }
    }

    @Test fun pendingAndConflictStatesNeverClaimConfirmedPrivateOrShared() {
        val row = mutableStateOf(RunPublication("run", "alice", known = true, shared = true, wantShared = false))
        compose.setContent { MaterialTheme { SharingControls(row.value, null, false) { _, _, _ -> } } }
        compose.onNodeWithText("Unshare pending confirmation").assertExists()
        compose.runOnIdle { row.value = row.value.copy(wantShared = null, error = "CONFLICT") }
        compose.onNodeWithText("Sharing changed elsewhere. Review and try again.").assertExists()
        compose.runOnIdle { row.value = row.value.copy(ownerId = null, shared = false, wantShared = true, error = null) }
        compose.onNodeWithText("Share pending. Add this run to your account in settings.").assertExists()
    }

    @Test fun narrowDoubleFontKeepsPhotoLabelAndCommandsWithinPanel() {
        val dark = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) { Surface {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        SharingControls(RunPublication("run", "alice", known = true), null, false) { _, _, _ -> }
                    }
                } }
            }
        }
        for (text in listOf("Share run link", "Copy run link", "Unshare", "Display photo with shared run")) {
            val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            val panel = compose.onNodeWithTag("run-sharing").fetchSemanticsNode().boundsInRoot
            assertTrue(node.boundsInRoot.left >= panel.left)
            assertTrue(node.boundsInRoot.right <= panel.right)
        }
        saveScreenshot("sharing-light-200")
        compose.runOnIdle { dark.value = true }
        saveScreenshot("sharing-dark-200")
    }

    @Test fun confirmedShortLinkUsesClipboardAndAndroidChooser() {
        val context = compose.activity
        val link = "https://wayirun-dev.unopenedparachute.workers.dev/r/${"a".repeat(32)}"
        deliverPublication(context, "copy", link, {}, "run")
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        assertEquals(link, clip!!.getItemAt(0).text.toString())

        val chooser = publicationShareIntent(link)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("text/plain", send.type)
        assertEquals(link, send.getStringExtra(Intent.EXTRA_TEXT))
    }

    private fun saveScreenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(null), "milestone-4-1").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onNodeWithTag("run-sharing").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
