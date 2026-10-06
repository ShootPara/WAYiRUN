package com.example.runningapp.coaching

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** One playback, bounded externally; every completion, error and cancellation releases focus. */
class CoachingAudio(private val context: Context) {
    suspend fun play(file: File?, fallback: Int): Boolean = suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(AudioManager::class.java)
        val player = MediaPlayer()
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val handler = Handler(Looper.getMainLooper())
        var done = false
        var focus: AudioFocusRequest? = null
        fun finish(success: Boolean) {
            if (done) return
            done = true
            runCatching { player.release() }
            focus?.let { runCatching { manager.abandonAudioFocusRequest(it) } }
            if (continuation.isActive) continuation.resume(success)
        }
        continuation.invokeOnCancellation { handler.post { finish(false) } }
        try {
            player.setAudioAttributes(attributes)
            if (file != null) player.setDataSource(file.absolutePath)
            else context.resources.openRawResourceFd(fallback).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            player.setOnErrorListener { _, _, _ -> finish(false); true }
            player.setOnCompletionListener { finish(true) }
            player.setOnPreparedListener {
                if (!continuation.isActive) finish(false)
                else {
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ change -> if (change < 0) finish(false) }, handler).build()
                    focus = request
                    if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        runCatching { player.start() }.onFailure { finish(false) }
                    } else finish(false)
                }
            }
            player.prepareAsync()
        } catch (_: Exception) { finish(false) }
    }
}
