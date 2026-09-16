package com.example.runningapp.tracking

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow

/** No notification contents are read or retained; this grant permits active media sessions. */
class MusicAccessService : NotificationListenerService() {
    override fun onListenerConnected() { connected.value = true }
    override fun onListenerDisconnected() { connected.value = false }
    companion object { val connected = MutableStateFlow(false) }
}

internal class MusicSessionAdapter(private val context: Context,
    private val targetPackage: String = "com.google.android.apps.youtube.music",
    private val accessOverride: (() -> Boolean)? = null,
    private val changed: (Long, PlayerStatus) -> Unit) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MusicAccessService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var watching = false
    private var selected: MediaController? = null
    private var callback: MediaController.Callback? = null
    var generation = 0L
        private set
    val allowed: Boolean get() = accessOverride?.invoke() ?: context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
    val available: Boolean get() = selected != null
    val current: PlayerStatus get() = status(selected?.playbackState) ?: PlayerStatus.UNAVAILABLE
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions -> choose(sessions.orEmpty()) }

    fun refresh() {
        try {
            if (!allowed) { disconnect(); return }
            if (!watching) { manager.addOnActiveSessionsChangedListener(listener, component, handler); watching = true }
            choose(manager.getActiveSessions(component))
            selected?.let { status(it.playbackState)?.let { value -> changed(generation, value) } }
        } catch (_: RuntimeException) { disconnect() }
    }

    private fun choose(sessions: List<MediaController>) {
        val candidates = sessions.filter { it.packageName == targetPackage }
        val next = candidates.firstOrNull { it.sessionToken == selected?.sessionToken }
            ?: candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull()
        if (next?.sessionToken == selected?.sessionToken) return
        callback?.let { selected?.unregisterCallback(it) }
        selected = next; generation++
        val token = generation
        changed(token, PlayerStatus.UNAVAILABLE) // A new session must earn a fresh link.
        callback = if (next == null) null else object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (token == generation) status(state)?.let { changed(token, it) }
            }
            override fun onSessionDestroyed() { if (token == generation) choose(emptyList()) }
        }.also { next.registerCallback(it, handler) }
        next?.let { status(it.playbackState)?.let { state -> changed(token, state) } }
    }

    fun command(action: MusicAction): Boolean {
        val player = selected ?: return false
        return try {
            val actions = player.playbackState?.actions ?: 0
            when (action) {
                MusicAction.PAUSE_PLAYER -> {
                    if (actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) == 0L) return false
                    player.transportControls.pause()
                }
                MusicAction.RESUME_PLAYER -> {
                    if (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) == 0L) return false
                    player.transportControls.play()
                }
                else -> return false
            }
            true
        } catch (_: RuntimeException) { false }
    }

    private fun disconnect() {
        if (watching) runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
        watching = false
        choose(emptyList())
    }
    fun close() { disconnect(); handler.removeCallbacksAndMessages(null) }

    private fun status(state: PlaybackState?): PlayerStatus? {
        if (state == null) return PlayerStatus.UNAVAILABLE
        return when (state.state) {
        PlaybackState.STATE_PLAYING -> PlayerStatus.PLAYING
        PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED -> PlayerStatus.PAUSED
        PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR -> PlayerStatus.UNAVAILABLE
        else -> null // Buffering/seeking is not a pause.
    }
    }
}
