package com.example.runningapp.tracking

import android.Manifest
import android.content.pm.PackageManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.runningapp.MainActivity
import com.example.runningapp.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class MusicIndependenceTest {
    @get:Rule val grants: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS)

    @Test fun installedAppHasNoMusicNotificationListener() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val services = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES).services.orEmpty()
        assertFalse(services.any { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" })
        assertFalse(services.any { it.name.endsWith("MusicAccessService") })
    }

    @Test fun playerChangesDoNotChangeRunStateAndRunCommandsDoNotControlPlayer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val commands = AtomicInteger()
        var session: MediaSession? = null
        fun playerState(value: Int) = instrumentation.runOnMainSync {
            session!!.setPlaybackState(PlaybackState.Builder().setState(value, 0, 1f)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE).build())
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            await { TrackingService.view.value.ready && !TrackingService.view.value.busy }
            check(TrackingService.view.value.snapshot == null) { "Use an emulator without an unfinished user run" }
            var runId: String? = null
            try {
                instrumentation.runOnMainSync {
                    session = MediaSession(context, "wayirun-independence-test").apply {
                        setCallback(object : MediaSession.Callback() {
                            override fun onPause() { commands.incrementAndGet() }
                            override fun onPlay() { commands.incrementAndGet() }
                            override fun onStop() { commands.incrementAndGet() }
                        }, Handler(Looper.getMainLooper()))
                        isActive = true
                    }
                }
                playerState(PlaybackState.STATE_PAUSED)
                TrackingService.send(context, TrackingService.START,
                    RunSettings(RunMode.INDOOR, RunUnits.MILES, 0, RunGoal.None, null))
                await { TrackingService.view.value.snapshot?.state == RunState.RUNNING && !TrackingService.view.value.busy }
                runId = TrackingService.view.value.snapshot!!.runId
                playerState(PlaybackState.STATE_PLAYING)
                playerState(PlaybackState.STATE_PAUSED)
                remains(RunState.RUNNING)
                TrackingService.send(context, TrackingService.PAUSE)
                await { TrackingService.view.value.snapshot?.state == RunState.PAUSED && !TrackingService.view.value.busy }
                playerState(PlaybackState.STATE_PLAYING)
                remains(RunState.PAUSED)
                TrackingService.send(context, TrackingService.RESUME)
                await { TrackingService.view.value.snapshot?.state == RunState.RUNNING && !TrackingService.view.value.busy }
                TrackingService.send(context, TrackingService.FINISH_WITHOUT_COACHING)
                await { TrackingService.view.value.snapshot?.state == RunState.FINISHED && !TrackingService.view.value.busy }
                playerState(PlaybackState.STATE_PAUSED)
                playerState(PlaybackState.STATE_PLAYING)
                remains(RunState.FINISHED)
                assertEquals(0, commands.get())
            } finally {
                instrumentation.runOnMainSync { session?.release() }
                val id = runId
                if (id != null && TrackingService.view.value.snapshot?.runId == id) {
                    if (TrackingService.view.value.snapshot?.state != RunState.FINISHED) {
                        TrackingService.send(context, TrackingService.FINISH_WITHOUT_COACHING)
                        await { TrackingService.view.value.snapshot?.state == RunState.FINISHED && !TrackingService.view.value.busy }
                    }
                    TrackingService.send(context, TrackingService.DISCARD, runId = id)
                    await { TrackingService.view.value.snapshot == null && !TrackingService.view.value.busy }
                }
            }
        }
    }

    private fun remains(state: RunState) {
        repeat(20) {
            SystemClock.sleep(50)
            assertEquals(state, TrackingService.view.value.snapshot?.state)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Expected tracking state; error=${TrackingService.view.value.error}", condition())
    }
}
