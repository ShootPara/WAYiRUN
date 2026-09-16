package com.example.runningapp.tracking

import com.example.runningapp.domain.RunEvent
import com.example.runningapp.domain.RunEventType
import com.example.runningapp.domain.RunUnits
import java.util.Locale
import kotlin.math.roundToLong

internal data class AudibleCue(val id: String, val text: String, val acknowledgement: Boolean)

/** Output and callbacks are serialized on the main thread by the Android adapter. */
internal interface CueOutput {
    fun begin(): Boolean = true
    fun end() {}
    fun stop() {}
    fun speak(cue: AudibleCue, completed: (Boolean) -> Unit): Boolean
    fun tone(cue: AudibleCue, completed: () -> Unit)
    fun close()
}

/** One cue at a time, including fallback tones after asynchronous speech failures. */
internal class RunCueQueue(private val output: CueOutput, private val onIdle: () -> Unit = {}) {
    private val pending = ArrayDeque<AudibleCue>()
    private var active: AudibleCue? = null
    private var fallingBack = false
    private var closed = false

    val isPlaying: Boolean get() = active != null

    fun play(events: List<RunEvent>, units: RunUnits = RunUnits.MILES) {
        if (closed) return
        events.forEach { event ->
            val text = when (event.type) {
                RunEventType.STARTED -> "Run started"
                RunEventType.PAUSED -> "Run paused"
                RunEventType.RESUMED -> "Run resumed"
                RunEventType.FINISHED -> "Run complete. ${spokenMetrics(event, units)}"
                RunEventType.GOAL_REACHED -> "Goal reached. ${spokenMetrics(event, units)} Keep going until you're finished."
                RunEventType.SPLIT_COMPLETED -> return@forEach
            }
            pending.addLast(AudibleCue("${event.id.runId}:${event.id.sequence}", text,
                event.type == RunEventType.FINISHED || event.type == RunEventType.GOAL_REACHED))
        }
        next()
    }

    private fun next() {
        if (closed || active != null || pending.isEmpty()) return
        val cue = pending.removeFirst()
        active = cue; fallingBack = false
        if (!runCatching { output.begin() }.getOrDefault(false)) { finished(cue); return }
        val accepted = runCatching { output.speak(cue) { success ->
            if (!closed && active === cue && !fallingBack) {
                if (success) finished(cue) else fallback(cue)
            }
        } }.getOrDefault(false)
        if (!accepted) fallback(cue)
    }

    private fun fallback(cue: AudibleCue) {
        if (closed || active !== cue || fallingBack) return
        fallingBack = true
        try { output.tone(cue) { finished(cue) } }
        catch (_: RuntimeException) { finished(cue) }
    }

    private fun finished(cue: AudibleCue) {
        if (closed || active !== cue) return
        output.end()
        active = null
        next()
        if (active == null) onIdle()
    }

    fun cancel() {
        pending.clear(); active = null
        output.stop(); output.end()
        onIdle()
    }

    fun close() {
        closed = true
        cancel()
        output.close()
    }
}

internal fun spokenMetrics(event: RunEvent, units: RunUnits): String {
    val distance = event.distanceMeters / units.metersPerUnit
    val unit = if (units == RunUnits.MILES) "mile" else "kilometer"
    val distanceText = String.format(Locale.US, "%.2f", distance)
    val pace = if (distance > 0.0) "${spokenDuration((event.activeDurationMs / distance).roundToLong())} per $unit" else "unavailable"
    return "Time: ${spokenDuration(event.activeDurationMs)}. Distance: $distanceText ${if (distanceText == "1.00") unit else "${unit}s"}. Average pace: $pace."
}

private fun spokenDuration(ms: Long): String {
    val seconds = (ms / 1000.0).roundToLong()
    val parts = mutableListOf<String>()
    fun add(value: Long, label: String) { if (value > 0) parts += "$value $label${if (value == 1L) "" else "s"}" }
    add(seconds / 3600, "hour"); add(seconds % 3600 / 60, "minute"); add(seconds % 60, "second")
    return parts.joinToString(", ").ifEmpty { "0 seconds" }
}
