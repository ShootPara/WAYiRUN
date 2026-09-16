package com.example.runningapp.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.runningapp.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.rule.GrantPermissionRule
import android.Manifest

@RunWith(AndroidJUnit4::class)
class StartupPermissionsTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val grants: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS)

    @Test fun startupRecreationAndBackgroundReturnNeverOpenSettingsOrSetup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A previous build's completed wizard must never suppress the new launch check.
        context.getSharedPreferences("permission-setup", Context.MODE_PRIVATE).edit()
            .putBoolean("completed", true).putBoolean("requested", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            compose.onNodeWithTag("settings-gear").assertExists()
            compose.onNodeWithText("Set up WAYiRUN").assertDoesNotExist()
            activity.recreate()
            compose.onNodeWithText("Set up WAYiRUN").assertDoesNotExist()
            activity.moveToState(Lifecycle.State.CREATED)
            activity.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("Set up WAYiRUN").assertDoesNotExist()
            compose.onNodeWithTag("settings-screen").assertDoesNotExist()
        }
    }
}
