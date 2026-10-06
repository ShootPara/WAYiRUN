package com.example.runningapp.tracking

import com.example.runningapp.domain.RunEvent
import com.example.runningapp.domain.RunEventType
import com.example.runningapp.domain.RunUnits
import com.example.runningapp.domain.PauseReason
import com.example.runningapp.domain.AnnouncementChannel
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

/** Monotonic scheduling kept separate from Android so arbitration tests never sleep. */
internal interface CueScheduler {
    fun nowMs(): Long
    fun schedule(delayMs: Long, action: () -> Unit): () -> Unit
}

/** One cue at a time, including fallback tones after asynchronous speech failures. */
internal class RunCueQueue(
    private val output: CueOutput, private val scheduler: CueScheduler, private val onIdle: () -> Unit = {},
) {
    private data class Pending(val event: RunEvent, val units: RunUnits, val readyAt: Long,
        val members: List<RunEvent> = emptyList())
    private val pending = mutableListOf<Pending>()
    private var active: AudibleCue? = null
    private var fallingBack = false
    private var closed = false
    private var runId: String? = null
    private var goal: RunEvent? = null
    private var finishedRun = false
    private var cancelTimer: (() -> Unit)? = null
    private var timerGeneration = 0L
    private val spokenMilestones = mutableMapOf<AnnouncementChannel, RunEvent>()

    val isPlaying: Boolean get() = active != null || pending.isNotEmpty()

    private fun coincides(a: RunEvent, b: RunEvent): Boolean = a.id.runId == b.id.runId &&
        kotlin.math.abs((a.occurrenceActiveMs ?: a.activeDurationMs) -
            (b.occurrenceActiveMs ?: b.activeDurationMs)) <= 1_000

    fun play(events: List<RunEvent>, units: RunUnits = RunUnits.MILES) {
        if (closed) return
        // Preload the goal before handling any milestones from the same update.
        val incomingGoals = events.filter { it.type == RunEventType.GOAL_REACHED }
        events.forEach { event ->
            if (runId != event.id.runId) {
                if (runId != null) cancel()
                runId = event.id.runId
            }
            incomingGoals.firstOrNull { it.id.runId == runId }?.let { goal = it }
            when (event.type) {
                RunEventType.SPLIT_COMPLETED -> return@forEach
                RunEventType.GOAL_REACHED -> {
                    goal = event
                    pending.removeAll { it.members.any { member -> coincides(member, event) } }
                }
                RunEventType.FINISHED -> {
                    finishedRun = true
                    pending.removeAll { it.members.isNotEmpty() }
                    clearTimer()
                    spokenMilestones.clear()
                }
                RunEventType.ANNOUNCEMENT -> {
                    if (finishedRun || goal?.let { coincides(it, event) } == true) return@forEach
                    val channel = event.announcementChannel
                    val alreadySpoken = if (channel == null) null else spokenMilestones.entries.firstOrNull {
                        it.key != channel && coincides(it.value, event)
                    }?.key
                    if (alreadySpoken != null) {
                        spokenMilestones.remove(alreadySpoken)
                        return@forEach
                    }
                    val match = if (channel == null) -1 else pending.indexOfFirst { item ->
                        item.members.isNotEmpty() && item.members.none { it.announcementChannel == channel } &&
                            item.members.all { coincides(it, event) }
                    }
                    if (match >= 0) {
                        val previous = pending[match]
                        // Distance crossings carry a known boundary rather than a timer's last distance total.
                        val recap = if (channel == AnnouncementChannel.DISTANCE) event else previous.event
                        pending[match] = previous.copy(event = recap, members = previous.members + event)
                    } else pending += Pending(event, units, scheduler.nowMs() + 1_000, listOf(event))
                    return@forEach
                }
                else -> Unit
            }
            pending += Pending(event, units, scheduler.nowMs())
        }
        next()
    }

    private fun audible(event: RunEvent, units: RunUnits): AudibleCue {
        val text = when (event.type) {
            RunEventType.STARTED -> "Run started"
            RunEventType.PAUSED -> if (event.pauseReason == PauseReason.AUTOMATIC) "Auto-paused" else "Run paused"
            RunEventType.RESUMED -> if (event.pauseReason == PauseReason.AUTOMATIC) "Resumed." else "Run resumed"
            RunEventType.FINISHED -> "Run complete. ${spokenMetrics(event, units)}"
            RunEventType.GOAL_REACHED -> "Goal reached. ${spokenMetrics(event, units)} Keep going until you're finished."
            RunEventType.ANNOUNCEMENT -> spokenMetrics(event, units)
            RunEventType.SPLIT_COMPLETED -> error("Splits have no audio")
        }
        return AudibleCue("${event.id.runId}:${event.id.sequence}", text,
            event.type == RunEventType.FINISHED || event.type == RunEventType.GOAL_REACHED)
    }

    private fun next() {
        clearTimer()
        if (closed || active != null || pending.isEmpty()) return
        val index = pending.indexOfFirst { it.readyAt <= scheduler.nowMs() }
        if (index < 0) {
            val generation = timerGeneration
            cancelTimer = scheduler.schedule((pending.minOf { it.readyAt } - scheduler.nowMs()).coerceAtLeast(0)) {
                if (!closed && generation == timerGeneration) next()
            }
            return
        }
        val item = pending.removeAt(index)
        item.members.forEach { event -> event.announcementChannel?.let {
            if (item.members.size == 1) spokenMilestones[it] = event else spokenMilestones.remove(it)
        } }
        val cue = audible(item.event, item.units)
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
        if (!isPlaying) onIdle()
    }

    private fun clearTimer() {
        timerGeneration++
        cancelTimer?.invoke()
        cancelTimer = null
    }

    fun cancel() {
        clearTimer()
        pending.clear(); active = null
        runId = null; goal = null; finishedRun = false; spokenMilestones.clear()
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
    val paceMs = (event.activeDurationMs / distance).takeIf { distance > 0.0 && it.isFinite() && it >= 0 }
    val pace = paceMs?.let { "${spokenDuration(it.roundToLong())} per $unit" } ?: "unavailable"
    return "Time: ${spokenDuration(event.activeDurationMs)}. Distance: $distanceText ${if (distanceText == "1.00") unit else "${unit}s"}. Average pace: $pace."
}

private fun spokenDuration(ms: Long): String {
    val seconds = (ms / 1000.0).roundToLong()
    val parts = mutableListOf<String>()
    fun add(value: Long, label: String) { if (value > 0) parts += "$value $label${if (value == 1L) "" else "s"}" }
    add(seconds / 3600, "hour"); add(seconds % 3600 / 60, "minute"); add(seconds % 60, "second")
    return parts.joinToString(", ").ifEmpty { "0 seconds" }
}
