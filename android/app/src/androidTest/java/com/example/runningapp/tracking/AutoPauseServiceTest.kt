package com.example.runningapp.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.runningapp.MainActivity
import com.example.runningapp.domain.*
import com.example.runningapp.storage.RunDatabase
import com.example.runningapp.storage.decode
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AutoPauseServiceTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private class Sensors(context: Context) : SensorAdapters(context) {
        var observing = false
        var callback: ((Long, Long, Long) -> Unit)? = null
        var motion: ((Long, MotionWindow) -> Unit)? = null
        override fun startMotion(onStep: (Long, Long) -> Unit, onMotion: (Long, MotionWindow) -> Unit) {
            motion = onMotion
        }
        override fun start(mode: RunMode, stride: Double?, onGps: (Long, GpsFix) -> Unit,
            onSteps: (Long, Long, Long) -> Unit): Boolean {
            stop()
            observing = true
            callback = onSteps
            return true
        }
        override fun stop() { super.stop(); observing = false; motion = null }
        fun step(count: Long) { if (observing) callback?.invoke(generation, SystemClock.elapsedRealtime(), count) }
        fun quiet() {
            val now = SystemClock.elapsedRealtime()
            if (observing) motion?.invoke(generation, MotionWindow(now - 1_000, now, MotionState.STATIONARY))
        }
    }

    @Test fun realServiceAutoResumesInBackgroundPersistsReasonsAndHonorsManualOverride() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var sensor: Sensors? = null
        var runId: String? = null
        val factory = TrackingService.sensorFactory
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            await { TrackingService.view.value.ready && !TrackingService.view.value.busy }
            check(TrackingService.view.value.snapshot?.state !in listOf(RunState.RUNNING, RunState.PAUSED, RunState.COUNTDOWN)) {
                "Use an emulator without an unfinished user run"
            }
            try {
                context.stopService(Intent(context, TrackingService::class.java))
                await { !TrackingService.view.value.ready }
                instrumentation.runOnMainSync {
                    TrackingService.sensorFactory = { Sensors(it).also { created -> sensor = created } }
                }
                TrackingService.send(context, TrackingService.OPEN)
                await { TrackingService.view.value.ready && !TrackingService.view.value.busy }
                TrackingService.send(context, TrackingService.START, RunSettings(
                    RunMode.INDOOR, RunUnits.KILOMETERS, 0, RunGoal.None, 1.0, autoPauseEnabled = true))
                await { TrackingService.view.value.snapshot?.state == RunState.RUNNING }
                runId = TrackingService.view.value.snapshot!!.runId
                activity.moveToState(Lifecycle.State.CREATED)
                // Reproduce the reported initial silence: registration alone must not pause.
                SystemClock.sleep(11_000)
                assertEquals(RunState.RUNNING, TrackingService.view.value.snapshot?.state)
                awaitQuiet(instrumentation, sensor!!)
                captureVisualIfRequested(instrumentation, context, activity)
                assertTrue(sensor!!.observing)
                val paused = TrackingService.view.value.snapshot!!
                val stored = runBlocking { RunDatabase.get(context).runs().get(runId!!)!!.decode() }
                assertEquals(PauseReason.AUTOMATIC, stored.snapshot.pauseReason)
                assertTrue(stored.snapshot.settings.autoPauseEnabled)
                var count = 100L
                val deadline = SystemClock.elapsedRealtime() + 12_000
                while (TrackingService.view.value.snapshot?.state == RunState.PAUSED && SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync { sensor!!.step(count++) }
                    SystemClock.sleep(500)
                }
                await { TrackingService.view.value.snapshot?.state == RunState.RUNNING }
                val resumed = TrackingService.view.value.snapshot!!
                assertEquals(paused.distanceMeters, resumed.distanceMeters, 0.0)
                assertTrue(resumed.activeDurationMs - paused.activeDurationMs < 1_500)
                assertEquals(2, resumed.activeIntervals.size)
                // A second stop proves policy evidence was reset after the first resume.
                awaitQuiet(instrumentation, sensor!!)
                TrackingService.send(context, TrackingService.PAUSE, runId = runId)
                await { TrackingService.view.value.snapshot?.pauseReason == PauseReason.MANUAL && !TrackingService.view.value.busy }
                assertFalse(sensor!!.observing)
                val manual = TrackingService.view.value.snapshot!!
                repeat(6) { instrumentation.runOnMainSync { sensor!!.step(count++) }; SystemClock.sleep(500) }
                assertEquals(manual, TrackingService.view.value.snapshot)
                context.stopService(Intent(context, TrackingService::class.java))
                await { !TrackingService.view.value.ready }
                TrackingService.send(context, TrackingService.OPEN)
                await { TrackingService.view.value.ready }
                assertEquals(PauseReason.INTERRUPTED, TrackingService.view.value.snapshot!!.pauseReason)
                assertFalse(sensor!!.observing)
            } finally {
                try {
                    if (runId != null) {
                        TrackingService.send(context, TrackingService.FINISH_WITHOUT_COACHING, runId = runId)
                        await { TrackingService.view.value.snapshot?.state == RunState.FINISHED && !TrackingService.view.value.busy }
                        TrackingService.send(context, TrackingService.DISCARD, runId = runId)
                        await { TrackingService.view.value.snapshot == null && !TrackingService.view.value.busy }
                    }
                } finally {
                    context.stopService(Intent(context, TrackingService::class.java))
                    instrumentation.runOnMainSync { TrackingService.sensorFactory = factory }
                }
            }
        }
    }

    private fun awaitQuiet(instrumentation: android.app.Instrumentation, sensor: Sensors) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (TrackingService.view.value.snapshot?.state == RunState.RUNNING && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { sensor.quiet() }
            SystemClock.sleep(200)
        }
        assertEquals(PauseReason.AUTOMATIC, TrackingService.view.value.snapshot?.pauseReason)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Expected auto-pause service state; error=${TrackingService.view.value.error}", condition())
    }

    private fun captureVisualIfRequested(
        instrumentation: android.app.Instrumentation,
        context: Context,
        activity: ActivityScenario<MainActivity>
    ) {
        val name = InstrumentationRegistry.getArguments().getString("autoPauseScreenshot") ?: return
        activity.moveToState(Lifecycle.State.RESUMED)
        SystemClock.sleep(1_000)
        FileOutputStream(context.getExternalFilesDir(null)!!.resolve(name)).use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        activity.moveToState(Lifecycle.State.CREATED)
    }
}
