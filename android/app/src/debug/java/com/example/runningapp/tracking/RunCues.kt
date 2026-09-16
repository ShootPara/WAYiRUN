package com.example.runningapp.tracking

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.runningapp.domain.RunEvent
import com.example.runningapp.domain.RunUnits
import java.util.Locale

/** State/goal cues only. Speech and fallback both respect the user's media volume. */
class RunCues(context: Context, onIdle: () -> Unit = {}) {
    private val output = AndroidCueOutput(context)
    private val queue = RunCueQueue(output, onIdle)
    init { output.onFocusLost = { queue.cancel() } }
    val isPlaying: Boolean get() = queue.isPlaying
    fun play(events: List<RunEvent>, units: RunUnits) = queue.play(events, units)
    fun cancel() = queue.cancel()
    fun close() = queue.close()
}

internal fun cueAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

private class AndroidCueOutput(context: Context) : CueOutput {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    var onFocusLost: () -> Unit = {}
    private var focus: AudioFocusRequest? = null

    override fun begin(): Boolean {
        lateinit var request: AudioFocusRequest
        request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(cueAudioAttributes()).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0 && focus === request) onFocusLost()
            }, handler).build()
        focus = request
        val granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) { end(); Log.w(TAG, "Audio focus denied; cue skipped") }
        else Log.i(TAG, "Transient ducking focus granted")
        return granted
    }

    override fun end() {
        val request = focus
        focus = null
        if (request != null) {
            runCatching { audio.abandonAudioFocusRequest(request) }
            Log.i(TAG, "Cue focus released")
        }
    }
    private var closed = false
    private var ready = false
    private var tones: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var utterance: String? = null
    private var completion: ((Boolean) -> Unit)? = null
    private val timeout = Runnable {
        Log.w(TAG, "Speech timed out; using tone")
        val callback = completion
        completion = null; utterance = null
        runCatching { tts?.stop() }
        callback?.invoke(false)
    }

    init {
        // Post callbacks so immediate initialization failure cannot read an unassigned engine.
        try {
            tts = TextToSpeech(context.applicationContext) { status -> handler.post {
                if (!closed && status == TextToSpeech.SUCCESS) configure()
                else if (!closed) Log.w(TAG, "Speech initialization failed; using tones")
            } }
        } catch (_: RuntimeException) { Log.w(TAG, "Speech engine unavailable; using tones") }
    }

    private fun configure() {
        val engine = tts ?: return
        ready = runCatching {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { handler.post { complete(id, true) } }
                @Deprecated("Required legacy callback")
                override fun onError(id: String?) { handler.post { complete(id, false) } }
                override fun onError(id: String?, errorCode: Int) {
                    handler.post { complete(id, false) }
                }
                override fun onStop(id: String?, interrupted: Boolean) {
                    handler.post { complete(id, false) }
                }
            })
            val locale = Locale.getDefault()
            val voice = engine.voices.orEmpty()
                .filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
                .sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale == locale }.thenBy { it.name })
                .firstOrNull()
            voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS &&
                engine.setAudioAttributes(cueAudioAttributes()) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        Log.i(TAG, if (ready) "Offline speech ready on media volume" else "Offline voice unavailable; using tones")
    }

    override fun speak(cue: AudibleCue, completed: (Boolean) -> Unit): Boolean {
        if (closed || !ready) return false
        utterance = cue.id; completion = completed
        val accepted = runCatching {
            tts?.speak(cue.text, TextToSpeech.QUEUE_FLUSH, null, cue.id) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (accepted) handler.postDelayed(timeout, 60_000)
        else { utterance = null; completion = null; Log.w(TAG, "Speech rejected; using tone") }
        return accepted
    }

    private fun complete(id: String?, success: Boolean) {
        if (closed || id == null || id != utterance) return
        handler.removeCallbacks(timeout)
        val callback = completion
        utterance = null; completion = null
        if (!success) Log.w(TAG, "Speech playback failed; using tone")
        callback?.invoke(success)
    }

    override fun tone(cue: AudibleCue, completed: () -> Unit) {
        if (closed) return
        try {
            val player = tones ?: ToneGenerator(AudioManager.STREAM_MUSIC, 80).also { tones = it }
            val started = player.startTone(if (cue.acknowledgement) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 350)
            if (!started) Log.w(TAG, "Fallback tone could not start")
        } catch (_: RuntimeException) { Log.w(TAG, "Fallback audio unavailable") }
        handler.postDelayed({ if (!closed) completed() }, 450)
    }

    override fun close() {
        closed = true
        stop(); end()
        handler.removeCallbacksAndMessages(null)
        completion = null; utterance = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        runCatching { tones?.release() }
    }

    override fun stop() {
        handler.removeCallbacksAndMessages(null)
        completion = null; utterance = null
        runCatching { tts?.stop() }
        runCatching { tones?.stopTone() }
    }

    private companion object { const val TAG = "WAYiRUN.Audio" }
}
