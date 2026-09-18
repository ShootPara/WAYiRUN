package com.example.runningapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.view.WindowManager
import com.example.runningapp.account.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class CoachingKeyPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepTestActivityVisible() {
        compose.activityRule.scenario.onActivity {
            it.setTurnScreenOn(true); it.setShowWhenLocked(true)
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    private fun account(owner: String) = AccountView(AccountSession(owner, owner, null, "test-token-$owner", Long.MAX_VALUE))
    private class Fake : CoachingKeys {
        var value = CoachingKeyStatus(false, "none", true, null)
        var saved: String? = null
        var calls = 0
        var pending: CompletableDeferred<CoachingKeyStatus>? = null
        override suspend fun status(session: AccountSession) = value
        override suspend fun change(session: AccountSession, revision: String, key: String?): CoachingKeyStatus {
            calls++; pending?.let { return it.await() }
            saved = key
            value = CoachingKeyStatus(key != null, "changed", true, if (key != null) 1L else null)
            return value
        }
    }
    @Test fun maskedEntrySavesAndReplacementStartsEmptyAndRemovalRequiresConfirmation() {
        val api = Fake()
        compose.setContent { MaterialTheme { Column { CoachingKeyPanel(account("alice"), true, api) } } }
        compose.onNodeWithText("Add API key").assertIsEnabled().performClick()
        compose.onNodeWithTag("coaching-key-input").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .performTextInput("sk-test-" + "x".repeat(30))
        compose.onNodeWithText("Save key").performClick()
        compose.waitUntil { api.calls == 1 }
        compose.onNodeWithText("OpenAI key saved ••••••••").assertExists()
        compose.onNodeWithText("Replace key").performClick()
        compose.onNodeWithTag("coaching-key-input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Remove key").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, api.calls); assertNotNull(api.saved) }
        compose.onNodeWithText("Remove key").performClick()
        compose.onNodeWithText("Remove", substring = false).performClick()
        compose.waitUntil { api.calls == 2 }
        compose.onNodeWithText("No OpenAI key saved").assertExists()
        compose.runOnIdle { assertNull(api.saved) }
    }
    @Test fun dismissAndAccountSwitchClearEntryAndPendingOldAccountResults() {
        val api = Fake(); val view = mutableStateOf(account("alice"))
        compose.setContent { MaterialTheme { Column { CoachingKeyPanel(view.value, true, api) } } }
        compose.onNodeWithText("Add API key").performClick()
        compose.onNodeWithTag("coaching-key-input").performTextInput("sk-test-" + "x".repeat(30))
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Add API key").performClick()
        compose.onNodeWithText("Save key").assertIsNotEnabled()
        api.pending = CompletableDeferred()
        compose.onNodeWithTag("coaching-key-input").performTextInput("sk-test-" + "x".repeat(30))
        compose.onNodeWithText("Save key").performClick(); compose.waitUntil { api.calls == 1 }
        compose.runOnIdle { view.value = account("bob") }
        compose.onNodeWithTag("coaching-key-input").assertDoesNotExist()
        api.pending!!.complete(CoachingKeyStatus(true, "old", true, 1))
        compose.onNodeWithText("No OpenAI key saved").assertExists()
        compose.onNodeWithText("OpenAI key saved ••••••••").assertDoesNotExist()
    }
}
