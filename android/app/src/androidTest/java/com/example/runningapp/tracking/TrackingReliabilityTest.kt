package com.example.runningapp.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.runningapp.MainActivity
import com.example.runningapp.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import androidx.test.rule.GrantPermissionRule
import android.Manifest

@RunWith(AndroidJUnit4::class)
class TrackingReliabilityTest {
    @get:Rule val grants: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS)
    @Test fun notificationMigrationRaisesOnlyUntouchedPrototypeChannel() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 29)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefix = "test-${System.nanoTime()}"
        val old = "$prefix-old"; val newer = "$prefix-new"
        val muted = "$prefix-muted"; val mutedNew = "$prefix-muted-new"
        try {
            manager.createNotificationChannel(NotificationChannel(old, "Test", NotificationManager.IMPORTANCE_LOW))
            assertEquals(newer, ensureTrackingChannel(context, old, newer))
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel(newer).importance)
            manager.createNotificationChannel(NotificationChannel(muted, "Muted", NotificationManager.IMPORTANCE_NONE))
            assertEquals(muted, ensureTrackingChannel(context, muted, mutedNew))
            assertNull(manager.getNotificationChannel(mutedNew))
        } finally { listOf(old, newer, muted, mutedNew).forEach { manager.deleteNotificationChannel(it) } }
    }

    @Test fun freshNotificationChannelIsProminentAndExistingCurrentMuteIsPreserved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefix = "test-${System.nanoTime()}"
        try {
            assertEquals(prefix, ensureTrackingChannel(context, "$prefix-absent", prefix))
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel(prefix).importance)
            manager.createNotificationChannel(NotificationChannel(prefix, "Test", NotificationManager.IMPORTANCE_NONE))
            assertEquals(prefix, ensureTrackingChannel(context, "$prefix-absent", prefix))
            assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(prefix).importance)
        } finally { manager.deleteNotificationChannel(prefix) }
    }

    @Test fun speechUsesMediaVolumeRatherThanUiSoundVolume() {
        val attributes = cueAudioAttributes()
        assertEquals(AudioAttributes.USAGE_MEDIA, attributes.usage)
        assertEquals(AudioManager.STREAM_MUSIC, attributes.volumeControlStream)
    }

    @Test fun notificationSurvivesPauseAndPausedServiceRecreationThenLeavesAtFinish() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notifications = context.getSystemService(NotificationManager::class.java)
        ActivityScenario.launch(MainActivity::class.java).use {
            await { TrackingService.view.value.ready && !TrackingService.view.value.busy }
            check(TrackingService.view.value.snapshot?.state !in listOf(RunState.RUNNING, RunState.PAUSED)) {
                "Use an emulator without an unfinished user run"
            }
            TrackingService.send(context, TrackingService.NEW)
            await { TrackingService.view.value.snapshot == null && !TrackingService.view.value.busy }
            TrackingService.send(context, TrackingService.START,
                RunSettings(RunMode.INDOOR, RunUnits.MILES, 0, RunGoal.None, null))
            await { TrackingService.view.value.snapshot?.state == RunState.RUNNING && notifications.activeNotifications.isNotEmpty() }
            val runId = TrackingService.view.value.snapshot!!.runId
            TrackingService.send(context, TrackingService.PAUSE)
            await { TrackingService.view.value.snapshot?.state == RunState.PAUSED &&
                notifications.activeNotifications.any { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("Run paused") } }
            val paused = TrackingService.view.value.snapshot!!
            SystemClock.sleep(1100)
            assertEquals(paused.activeDurationMs, TrackingService.view.value.snapshot!!.activeDurationMs)
            assertEquals(paused.distanceMeters, TrackingService.view.value.snapshot!!.distanceMeters, 0.0)
            val entry = notifications.activeNotifications.single()
            assertTrue(entry.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNotNull(entry.notification.contentIntent)
            context.stopService(Intent(context, TrackingService::class.java))
            await { !TrackingService.view.value.ready }
            TrackingService.send(context, TrackingService.OPEN)
            await { TrackingService.view.value.ready && notifications.activeNotifications.isNotEmpty() }
            assertEquals(RunState.PAUSED, TrackingService.view.value.snapshot!!.state)
            assertEquals(runId, TrackingService.view.value.snapshot!!.runId)
            TrackingService.send(context, TrackingService.RESUME)
            await { TrackingService.view.value.snapshot?.state == RunState.RUNNING }
            assertEquals(runId, TrackingService.view.value.snapshot!!.runId)
            TrackingService.send(context, TrackingService.PAUSE)
            await { TrackingService.view.value.snapshot?.state == RunState.PAUSED }
            TrackingService.send(context, TrackingService.FINISH)
            await { TrackingService.view.value.snapshot?.state == RunState.FINISHED && notifications.activeNotifications.isEmpty() }
            TrackingService.send(context, TrackingService.DISCARD, runId = "stale-confirmation")
            await { !TrackingService.view.value.busy }
            assertEquals(runId, TrackingService.view.value.snapshot!!.runId)
            TrackingService.send(context, TrackingService.DISCARD, runId = runId)
            await { TrackingService.view.value.snapshot == null && !TrackingService.view.value.busy }
            TrackingService.send(context, TrackingService.DISCARD, runId = runId)
            TrackingService.send(context, TrackingService.RESUME, runId = runId)
            await { !TrackingService.view.value.busy }
            SystemClock.sleep(1100)
            assertNull(TrackingService.view.value.snapshot)
            assertTrue(notifications.activeNotifications.isEmpty())
            kotlinx.coroutines.runBlocking {
                assertNull(com.example.runningapp.storage.RunDatabase.get(context).runs().get(runId))
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Expected service state before timeout; state=${TrackingService.view.value.snapshot?.state}, error=${TrackingService.view.value.error}", condition())
    }
}
