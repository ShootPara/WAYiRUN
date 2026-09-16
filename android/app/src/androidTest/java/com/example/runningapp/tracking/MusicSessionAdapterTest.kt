package com.example.runningapp.tracking

import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class MusicSessionAdapterTest {
    @Test fun realSessionCallbacksAndTransportAreOrderedAndDetached() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
        val readings = CopyOnWriteArrayList<PlayerStatus>()
        val pauses = AtomicInteger(); val plays = AtomicInteger()
        lateinit var session: MediaSession
        lateinit var adapter: MusicSessionAdapter
        fun state(value: Int) = PlaybackState.Builder().setState(value, 0, 1f)
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE).build()
        try {
            instrumentation.runOnMainSync {
                session = MediaSession(context, "test-music")
                session.setCallback(object : MediaSession.Callback() {
                    override fun onPause() { pauses.incrementAndGet(); session.setPlaybackState(state(PlaybackState.STATE_PAUSED)) }
                    override fun onPlay() { plays.incrementAndGet(); session.setPlaybackState(state(PlaybackState.STATE_PLAYING)) }
                }, Handler(Looper.getMainLooper()))
                session.setPlaybackState(state(PlaybackState.STATE_PAUSED)); session.isActive = true
                adapter = MusicSessionAdapter(context, context.packageName, { true }) { _, status -> readings += status }
                adapter.refresh()
            }
            await { PlayerStatus.PAUSED in readings }
            assertEquals(0, plays.get()) // Merely observing paused music must not start it.
            instrumentation.runOnMainSync { session.setPlaybackState(state(PlaybackState.STATE_PLAYING)) }
            await { readings.lastOrNull() == PlayerStatus.PLAYING }
            instrumentation.runOnMainSync { assertTrue(adapter.command(MusicAction.PAUSE_PLAYER)) }
            await { readings.lastOrNull() == PlayerStatus.PAUSED && pauses.get() == 1 }
            instrumentation.runOnMainSync { assertTrue(adapter.command(MusicAction.RESUME_PLAYER)) }
            await { readings.lastOrNull() == PlayerStatus.PLAYING && plays.get() == 1 }
            instrumentation.runOnMainSync { session.release() }
            await { readings.lastOrNull() == PlayerStatus.UNAVAILABLE }
            instrumentation.runOnMainSync { assertFalse(adapter.command(MusicAction.RESUME_PLAYER)) }
        } finally {
            instrumentation.runOnMainSync { adapter.close(); session.release() }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test fun missingAccessNeverControlsOrBlocksTracking() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val adapter = MusicSessionAdapter(instrumentation.targetContext, accessOverride = { false }) { _, _ -> }
            adapter.refresh()
            assertFalse(adapter.available); assertFalse(adapter.allowed)
            assertFalse(adapter.command(MusicAction.RESUME_PLAYER)); adapter.close()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Expected media callback", condition())
    }
}
