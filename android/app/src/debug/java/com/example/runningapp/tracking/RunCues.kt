package com.example.runningapp.tracking

import android.content.Context
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import com.example.runningapp.domain.*
import java.util.Locale

/** State and goal cues only; no music transport or announcement-interval policy. */
class RunCues(context: Context) {
    private var ready = false
    private var closed = false
    private val tones = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    private val tts = TextToSpeech(context.applicationContext) { status ->
        if (!closed && status == TextToSpeech.SUCCESS) configure()
    }
    private fun configure() {
        val voice = tts.voices?.firstOrNull { it.locale.language == Locale.getDefault().language && !it.isNetworkConnectionRequired }
        ready = voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
    }
    fun play(events: List<RunEvent>) {
        events.forEach { event ->
            val text = when (event.type) {
                RunEventType.STARTED -> "Run started"
                RunEventType.PAUSED -> "Run paused"
                RunEventType.RESUMED -> "Run resumed"
                RunEventType.FINISHED -> "Run complete"
                RunEventType.GOAL_REACHED -> "Goal reached. Keep going until you finish."
                RunEventType.SPLIT_COMPLETED -> return@forEach
            }
            if (!ready || tts.speak(text, TextToSpeech.QUEUE_ADD, null, "${event.id.runId}:${event.id.sequence}") == TextToSpeech.ERROR) {
                tones.startTone(if (event.type == RunEventType.GOAL_REACHED || event.type == RunEventType.FINISHED)
                    ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 250)
            }
        }
    }
    fun close() { closed = true; tts.stop(); tts.shutdown(); tones.release() }
}
