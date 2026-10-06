package com.example.runningapp.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.PowerManager
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

    private class Sensors(context: Context, val hasDetector: Boolean = true, val hasCounter: Boolean = true) : SensorAdapters(context) {
        var observing = false
        var gps: ((Long, GpsFix) -> Unit)? = null
        var motionRegistrations = 0
        var callback: ((Long, Long, Long) -> Unit)? = null
        var detector: ((Long, Long, Long) -> Unit)? = null
        override fun startMotion(onStep: (Long, Long, Long) -> Unit): Boolean {
            motionRegistrations++
            detector = onStep.takeIf { hasDetector }
            return hasDetector
        }
        override fun start(mode: RunMode, stride: Double?, onGps: (Long, GpsFix) -> Unit,
            onSteps: (Long, Long, Long) -> Unit): Boolean {
            stop()
            observing = true
            callback = onSteps
            gps = onGps.takeIf { mode == RunMode.OUTDOOR }
            return hasCounter
        }
        override fun stop() { super.stop(); observing = false; detector = null; gps = null }
        fun step(count: Long) { if (observing) callback?.invoke(generation, SystemClock.elapsedRealtime(), count) }
        fun detected() {
            val now = SystemClock.elapsedRealtime()
            if (observing) detector?.invoke(generation, now, now)
        }
        fun location(speed: Double) {
            val now = SystemClock.elapsedRealtime()
            if (observing) gps?.invoke(generation, GpsFix(now, 40.0, -75.0, 5f, speed, 0.2))
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
                instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
                await { !context.getSystemService(PowerManager::class.java).isInteractive }
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
                // Counter callbacks alone must not resume an automatic pause.
                repeat(6) {
                    instrumentation.runOnMainSync { sensor!!.step(count++) }
                    SystemClock.sleep(500)
                }
                assertEquals(PauseReason.AUTOMATIC, TrackingService.view.value.snapshot?.pauseReason)
                val deadline = SystemClock.elapsedRealtime() + 12_000
                while (TrackingService.view.value.snapshot?.state == RunState.PAUSED && SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync { sensor!!.detected() }
                    SystemClock.sleep(500)
                }
                await { TrackingService.view.value.snapshot?.state == RunState.RUNNING }
                val resumed = TrackingService.view.value.snapshot!!
                assertEquals(paused.distanceMeters, resumed.distanceMeters, 0.0)
                assertTrue(resumed.activeDurationMs - paused.activeDurationMs < 1_500)
                assertEquals(2, resumed.activeIntervals.size)
                // A second stop proves policy evidence was reset after the first resume.
                awaitQuiet(instrumentation, sensor!!)
                val oldDetector = sensor!!.detector!!
                val oldGeneration = sensor!!.generation
                TrackingService.send(context, TrackingService.PAUSE, runId = runId)
                await { TrackingService.view.value.snapshot?.pauseReason == PauseReason.MANUAL && !TrackingService.view.value.busy }
                assertFalse(sensor!!.observing)
                val manual = TrackingService.view.value.snapshot!!
                repeat(6) {
                    instrumentation.runOnMainSync {
                        val now = SystemClock.elapsedRealtime()
                        oldDetector(oldGeneration, now, now)
                        sensor!!.step(count++)
                    }
                    SystemClock.sleep(500)
                }
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
                    instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
                    instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
                    context.stopService(Intent(context, TrackingService::class.java))
                    instrumentation.runOnMainSync { TrackingService.sensorFactory = factory }
                }
            }
        }
    }

    @Test fun missingDetectorKeepsIndoorActiveAndOutdoorUsesGpsOnly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val factory = TrackingService.sensorFactory
        ActivityScenario.launch(MainActivity::class.java).use {
            await { TrackingService.view.value.ready && !TrackingService.view.value.busy }
            check(TrackingService.view.value.snapshot?.state !in listOf(RunState.RUNNING, RunState.PAUSED, RunState.COUNTDOWN))
            var runId: String? = null
            try {
                for (mode in RunMode.entries) {
                    context.stopService(Intent(context, TrackingService::class.java))
                    await { !TrackingService.view.value.ready }
                    lateinit var sensor: Sensors
                    instrumentation.runOnMainSync {
                        TrackingService.sensorFactory = { Sensors(it, hasDetector = false, hasCounter = false).also { sensor = it } }
                    }
                    TrackingService.send(context, TrackingService.OPEN)
                    await { TrackingService.view.value.ready }
                    TrackingService.send(context, TrackingService.START,
                        RunSettings(mode, RunUnits.KILOMETERS, 0, RunGoal.None, null, autoPauseEnabled = true))
                    await { TrackingService.view.value.snapshot?.state == RunState.RUNNING }
                    runId = TrackingService.view.value.snapshot!!.runId
                    repeat(8) {
                        instrumentation.runOnMainSync { sensor.location(0.0) }
                        SystemClock.sleep(1_000)
                    }
                    if (mode == RunMode.INDOOR) {
                        assertEquals(RunState.RUNNING, TrackingService.view.value.snapshot!!.state)
                        assertFalse(TrackingService.view.value.distanceAvailable)
                    } else {
                        assertEquals(PauseReason.AUTOMATIC, TrackingService.view.value.snapshot!!.pauseReason)
                        repeat(4) {
                            instrumentation.runOnMainSync { sensor.location(2.0) }
                            SystemClock.sleep(1_000)
                        }
                        assertEquals(RunState.RUNNING, TrackingService.view.value.snapshot!!.state)
                    }
                    assertEquals(1, sensor.motionRegistrations)
                    TrackingService.send(context, TrackingService.FINISH_WITHOUT_COACHING, runId = runId)
                    await { TrackingService.view.value.snapshot?.state == RunState.FINISHED && !TrackingService.view.value.busy }
                    assertFalse(sensor.observing)
                    TrackingService.send(context, TrackingService.DISCARD, runId = runId)
                    await { TrackingService.view.value.snapshot == null && !TrackingService.view.value.busy }
                    runId = null
                }
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
        // Demonstrate detector delivery, then stop sending events. No fake stillness sensor.
        instrumentation.runOnMainSync { sensor.detected() }
        SystemClock.sleep(500)
        instrumentation.runOnMainSync { sensor.detected() }
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (TrackingService.view.value.snapshot?.state == RunState.RUNNING && SystemClock.elapsedRealtime() < deadline) {
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
