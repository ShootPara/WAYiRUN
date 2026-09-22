package com.example.runningapp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.runningapp.domain.*
import com.example.runningapp.tracking.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunScreenTest {
    @Test fun keptPhotoHasSelectedPrivacyAndSaveShareActions() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val dao=com.example.runningapp.storage.RunDatabase.get(context).runs()
        val id=java.util.UUID.randomUUID().toString()
        val r=RunController(id,RunSettings(RunMode.INDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(0,0)})
        r.start();r.finish()
        kotlinx.coroutines.runBlocking {com.example.runningapp.storage.RunRepository(dao).save(r.checkpoint(),"photo-ui","UTC",0,false)}
        val draft=java.io.File(java.io.File(context.cacheDir,"photos").apply {mkdirs()},"draft-$id.jpg")
        val image=android.graphics.Bitmap.createBitmap(600,800,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        draft.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it)};image.recycle()
        try {
            compose.setContent { WayirunApp(TrackingView(r.snapshot(),ready=true),{_,_->}) {} }
            compose.onNodeWithText("Take / choose a run photo").performScrollTo().performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithText("Keep Photo").fetchSemanticsNodes().isNotEmpty()}
            compose.onAllNodes(isToggleable()).onLast().performScrollTo().assertIsOn().performClick().assertIsOff()
            compose.onNodeWithText("Keep Photo").performScrollTo().performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithText("Save photo").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Share photo").assertExists()
            kotlinx.coroutines.runBlocking {val saved=dao.photo(id)!!;assertFalse(saved.public);assertTrue(saved.jpeg.size>100);assertFalse(saved.synced)}
            assertFalse(draft.exists())
        } finally {kotlinx.coroutines.runBlocking {dao.discard(id,"photo-ui")};draft.delete()}
    }

    @Test fun photoOptionsCanBeSkippedWithoutChangingSavedRun() {
        val r=run();r.start();r.finish()
        compose.setContent { WayirunApp(TrackingView(r.snapshot(),ready=true),{_,_->fail("Skip must not mutate run")}) {} }
        compose.onNodeWithText("Take / choose a run photo").performScrollTo().performClick()
        compose.onNodeWithText("Take Photo").assertExists()
        compose.onNodeWithText("Choose Existing Photo").assertExists()
        compose.onNodeWithText("Skip").performScrollTo().performClick()
        compose.onNodeWithText("Run saved").assertExists()
    }

    @Test fun multipleAchievementsBrowseAndDismissToSummaryWithoutAudioCancellation() {
        val r=run();r.start();r.finish()
        val awards=listOf(Achievement("one","once",r.snapshot().runId,"First Footprint","First run","2026-09-18"),Achievement("two","once",r.snapshot().runId,"Five Alive","First 5K","2026-09-18"))
        val view=mutableStateOf(TrackingView(r.snapshot(),ready=true,achievements=awards))
        val actions=mutableListOf<String>()
        compose.setContent { WayirunApp(view.value,{action,_->actions.add(action);if(action==TrackingService.DISMISS_COACHING)view.value=view.value.copy(achievements=emptyList())}) {} }
        compose.onNodeWithText("First Footprint").assertExists()
        compose.onNodeWithText("Next",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Five Alive").assertExists()
        compose.onNodeWithText("Continue to summary").performScrollTo().performClick()
        compose.onNodeWithText("Run saved").assertExists()
        compose.runOnIdle { assertEquals(listOf(TrackingService.DISMISS_COACHING),actions) }
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun wake() { compose.activityRule.scenario.onActivity {
        it.setTurnScreenOn(true);it.setShowWhenLocked(true)
        it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }
    private fun run(): RunController = RunController("ui-test",
        RunSettings(RunMode.INDOOR, RunUnits.KILOMETERS, 0, RunGoal.None, null), RunClock { RunTime(0, 0) })

    @Test fun discardRequiresFullSwipeAndCancelPreservesSummary() {
        val run = run(); run.start(); run.finish()
        val view = mutableStateOf(TrackingView(run.snapshot(), ready = true))
        var deletes = 0
        compose.setContent { WayirunApp(view.value, { action, _ ->
            if (action == TrackingService.DISCARD) { deletes++; view.value = TrackingView(ready = true) }
        }) {} }
        compose.onNodeWithTag("discard-run").performScrollTo().performClick()
        compose.onNodeWithText("ARE YOU SURE YOU WANT TO DISCARD THIS RUN??").assertExists()
        compose.onNodeWithTag("discard-slider").performTouchInput { click() }
        compose.onNodeWithTag("discard-slider").performTouchInput {
            swipe(Offset(width * 0.1f, height / 2f), Offset(width * 0.35f, height / 2f), 300)
        }
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Run saved").assertExists()
        compose.onNodeWithTag("discard-run").performScrollTo().performClick()
        compose.onNodeWithTag("discard-slider").performTouchInput {
            swipe(Offset(width * 0.05f, height / 2f), Offset(width * 0.98f, height / 2f), 600)
        }
        compose.runOnIdle { assertEquals(1, deletes) }
        compose.onNodeWithTag("start").assertExists()
    }

    @Test fun tappingFinishDoesNotFinishButFullSwipeDoes() {
        val run = run(); run.start(); run.pause()
        val view = mutableStateOf(TrackingView(run.snapshot(), ready = true))
        var finishes = 0
        compose.setContent { WayirunApp(view.value, { action, _ ->
            if (action == TrackingService.FINISH) { finishes++; view.value = TrackingView(run.finish().snapshot, ready = true) }
        }) { } }
        compose.onNodeWithTag("finish-slider").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, finishes) }
        compose.onNodeWithTag("finish-slider").performTouchInput {
            swipe(Offset(width * 0.05f, height / 2f), Offset(width * 0.98f, height / 2f), 600)
        }
        compose.onNodeWithText("Run saved").assertExists()
        compose.runOnIdle { assertEquals(1, finishes) }
    }

    @Test fun shortSwipeCancelsAndInterruptedRunCanResume() {
        val run = run(); run.start(); run.pause()
        val view = mutableStateOf(TrackingView(run.snapshot(), interrupted = true, ready = true))
        var command: String? = null
        compose.setContent { WayirunApp(view.value, { action, _ ->
            command = action
            if (action == TrackingService.RESUME) view.value = TrackingView(run.resume().snapshot, ready = true)
        }) { } }
        compose.onNodeWithText("Tracking was interrupted.", substring = true).assertExists()
        compose.onNodeWithTag("finish-slider").performScrollTo().performTouchInput {
            swipe(Offset(width * 0.1f, height / 2f), Offset(width * 0.4f, height / 2f), 300)
        }
        compose.runOnIdle { assertNull(command) }
        compose.onNodeWithTag("resume").performScrollTo().performClick()
        compose.onNodeWithTag("pause").assertExists()
        compose.onNodeWithText("Distance unavailable").assertExists()
    }
}
