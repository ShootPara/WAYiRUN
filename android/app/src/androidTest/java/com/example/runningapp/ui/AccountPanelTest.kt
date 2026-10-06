package com.example.runningapp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.runningapp.account.AccountSession
import com.example.runningapp.account.AccountView
import com.example.runningapp.tracking.TrackingView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountPanelTest {
    @get:Rule val compose = createComposeRule()
    @Test fun missingBusyFailedAndExpiredSignInNeverDisableStartingALocalRun() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("local-settings", Context.MODE_PRIVATE)
        prefs.edit().putString("mode", "INDOOR").putString("units", "Kilometers").commit()
        val account = mutableStateOf(AccountView())
        var starts = 0
        compose.setContent { WayirunApp(viewOverride = TrackingView(ready = true), accountView = account.value) { starts++ } }
        for (state in listOf(AccountView(), AccountView(busy = true), AccountView(message = "Sign-in cancelled"),
            AccountView(AccountSession("private-owner", "Runner", null, "secret-token", 1)))) {
            compose.runOnIdle { account.value = state }
            compose.onNodeWithText("START RUNNING").performScrollTo().assertIsEnabled().performClick()
        }
        compose.runOnIdle { assertEquals(4, starts) }
        compose.onNodeWithText("private-owner").assertDoesNotExist()
        compose.onNodeWithText("secret-token").assertDoesNotExist()
    }
}
