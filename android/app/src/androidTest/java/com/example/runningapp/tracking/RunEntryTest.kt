package com.example.runningapp.tracking

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.runningapp.MainActivity
import com.example.runningapp.domain.*
import com.example.runningapp.storage.RunDatabase
import com.example.runningapp.storage.RunRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunEntryTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val grants: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun finishedRunSurvivesRecreationButNotWarmOrColdReopen() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            await { TrackingService.view.value.ready && !TrackingService.view.value.busy }
            check(TrackingService.view.value.snapshot == null) { "Use an emulator without an unfinished user run" }
            TrackingService.send(context, TrackingService.START,
                RunSettings(RunMode.INDOOR, RunUnits.MILES, 0, RunGoal.None, null))
            await { TrackingService.view.value.snapshot?.state == RunState.RUNNING }
            TrackingService.send(context, TrackingService.PAUSE)
            await { TrackingService.view.value.snapshot?.state == RunState.PAUSED }
            TrackingService.send(context, TrackingService.FINISH_WITHOUT_COACHING)
            await { TrackingService.view.value.snapshot?.state == RunState.FINISHED && !TrackingService.view.value.busy }
            val id = TrackingService.view.value.snapshot!!.runId
            try {
                activity.recreate()
                await { TrackingService.view.value.ready && !TrackingService.view.value.busy }
                assertEquals(id, TrackingService.view.value.snapshot?.runId)
                TrackingService.send(context, TrackingService.DISMISS_COACHING)
                await { !TrackingService.view.value.busy && TrackingService.view.value.achievements.isEmpty() }
                compose.onNodeWithText("Take / choose a run photo").performScrollTo().performClick()
                val pickerIntent = ActivityResultContracts.PickVisualMedia().createIntent(context,
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                val pickerPackage = requireNotNull(pickerIntent.resolveActivity(context.packageManager)).packageName
                compose.onNodeWithText("Choose Existing Photo").performScrollTo().performClick()
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                // Losing focus precedes the picker window becoming ready to receive Back.
                await { activity.state != Lifecycle.State.RESUMED &&
                    automation.rootInActiveWindow?.packageName?.toString() == pickerPackage }
                automation.waitForIdle(500, 10_000)
                assertTrue(automation.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                await { activity.state == Lifecycle.State.RESUMED && !TrackingService.view.value.busy }
                assertEquals(id, TrackingService.view.value.snapshot?.runId)
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("Your run photo").fetchSemanticsNodes().size == 1
                }
                compose.onNodeWithText("Your run photo").assertExists()
                compose.onNodeWithText("Skip").performScrollTo().performClick()
                activity.moveToState(Lifecycle.State.CREATED)
                activity.moveToState(Lifecycle.State.RESUMED)
                await { TrackingService.view.value.snapshot == null && !TrackingService.view.value.busy }
                runBlocking { assertNotNull(RunDatabase.get(context).runs().get(id)) }

                context.stopService(Intent(context, TrackingService::class.java))
                await { !TrackingService.view.value.ready }
                TrackingService.send(context, TrackingService.OPEN)
                await { TrackingService.view.value.ready && !TrackingService.view.value.busy }
                assertNull(TrackingService.view.value.snapshot)

                TrackingService.send(context, TrackingService.ENTER, runId = id)
                await { TrackingService.view.value.snapshot?.runId == id && !TrackingService.view.value.busy }
                assertFalse(TrackingService.view.value.coaching.visible)
                TrackingService.send(context, TrackingService.ENTER, runId = "missing-run")
                await { TrackingService.view.value.snapshot == null && !TrackingService.view.value.busy }
                runBlocking { assertNotNull(RunDatabase.get(context).runs().get(id)) }
            } finally {
                TrackingService.send(context, TrackingService.ENTER, runId = id)
                await { TrackingService.view.value.snapshot?.runId == id && !TrackingService.view.value.busy }
                TrackingService.send(context, TrackingService.DISCARD, runId = id)
                await { TrackingService.view.value.snapshot == null && !TrackingService.view.value.busy }
                runBlocking { assertNull(RunDatabase.get(context).runs().get(id)) }
            }
        }
    }

    @Test fun externalReturnCannotRestoreAnotherAccountsRun() {
        val id = "entry-foreign-${System.nanoTime()}"
        val dao = RunDatabase.get(context).runs()
        val run = RunController(id, RunSettings(RunMode.INDOOR, RunUnits.MILES, 0, RunGoal.None, null),
            RunClock { RunTime(0, 0) }).also { it.start(); it.finish() }
        runBlocking { RunRepository(dao).save(run.checkpoint(), "entry-test-owner", "UTC", 0, false,
            cloudOwnerId = "entry-test-foreign-account") }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                await { TrackingService.view.value.ready && !TrackingService.view.value.busy }
                check(TrackingService.view.value.snapshot == null) { "Use an emulator without an unfinished user run" }
                TrackingService.send(context, TrackingService.ENTER, runId = id)
                await { !TrackingService.view.value.busy }
                assertNull(TrackingService.view.value.snapshot)
                runBlocking { assertNotNull(dao.get(id)) }
            }
        } finally { runBlocking { dao.discard(id, "entry-test-owner") } }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Expected tracking state; error=${TrackingService.view.value.error}", condition())
    }
}
