package com.example.runningapp.coaching

import android.content.Context
import com.example.runningapp.R
import com.example.runningapp.account.SessionStore
import com.example.runningapp.storage.RunDatabase
import com.example.runningapp.sync.SyncScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

data class CoachingView(val busy: Boolean = false, val visible: Boolean = false, val label: String = "")

/** Owned by the tracking service, never by the dismissible animation. */
class PostRunCoaching(private val context: Context, private val scope: CoroutineScope,
    private val cuesPlaying: () -> Boolean, private val changed: (CoachingView) -> Unit) {
    private val preferences = context.getSharedPreferences("coaching-attempt", Context.MODE_PRIVATE)
    private var job: Job? = null
    private var generation = 0L
    var view = CoachingView(); private set
    private fun publish(label: String) { view = view.copy(label = label); changed(view) }
    fun dismiss() { view = view.copy(visible = false); changed(view) }
    fun cancel() { generation++; job?.cancel(); job = null; view = CoachingView(); changed(view) }

    fun start(runId: String, owner: String?, selected: Boolean) {
        cancel()
        // A crash may omit coaching, but reopening a finished run never repeats a paid attempt.
        if (preferences.getString("run", null) == runId) return
        val saved = preferences.edit().putString("run", runId).putBoolean("selected", selected).putString("state", "selected").commit()
        if (!selected) return
        val currentGeneration = generation
        view = CoachingView(busy = true, visible = true, label = "Preparing your coaching…"); changed(view)
        job = scope.launch {
            var file: File? = null
            val dao = RunDatabase.get(context).runs()
            suspend fun guard() {
                currentCoroutineContext().ensureActive()
                val stored = dao.get(runId) ?: throw CancellationException("Run removed")
                if (stored.cloudOwnerId != owner) throw CancellationException("Run changed")
                if (owner != null && withContext(Dispatchers.IO) { SessionStore(context).read()?.ownerId } != owner) throw CancellationException("Account changed")
            }
            val attempt = currentCoroutineContext()[Job]!!
            val monitor = launch {
                while (isActive) {
                    delay(500)
                    try { guard() } catch (_: Exception) { attempt.cancel(); return@launch }
                }
            }
            try {
                try {
                    withTimeout(110000) {
                        check(saved && owner != null)
                        val session = withContext(Dispatchers.IO) { SessionStore(context).read() }
                        check(session?.ownerId == owner && !session.expired())
                        SyncScheduler.enqueue(context)
                        withTimeout(20000) {
                            dao.syncStatus(owner).first { ops ->
                                ops.any { it.runId == runId && it.action == "UPLOAD" && it.status == "SYNCED" } &&
                                    ops.all { it.status in listOf("SYNCED", "DELETED") }
                            }
                        }
                        guard()
                        val api = CoachingNetwork()
                        preferences.edit().putString("state", "requested").commit().also { check(it) }
                        var result = api.generate(requireNotNull(session), runId, UUID.randomUUID().toString())
                        while (result.optString("state") in listOf("preparing", "text_pending", "speech_pending")) {
                            delay(1500); guard(); result = api.status(session, runId)
                        }
                        check(result.getString("runId") == runId && result.getString("state") == "ready")
                        guard()
                        val bytes = api.audio(session, runId)
                        check(bytes.size == result.getInt("audioBytes"))
                        guard()
                        file = withContext(Dispatchers.IO) {
                            File.createTempFile("coaching-", ".wav", context.cacheDir).also { it.writeBytes(bytes) }
                        }
                    }
                } catch (_: TimeoutCancellationException) { /* Selected coaching falls back; never resend POST. */ }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* Offline, missing key, failed generation: use onboard audio. */ }
                guard()
                withTimeout(65000) { while (cuesPlaying()) delay(100) }
                guard()
                val fallback = if (runId.last().code % 2 == 0) R.raw.coaching_fallback_one else R.raw.coaching_fallback_two
                publish(if (file == null) "A little encouragement" else "Your coaching")
                preferences.edit().putString("state", "playing").commit()
                val output = CoachingAudio(context)
                val played = withTimeoutOrNull(60000) { output.play(file, fallback) } == true
                if (!played && file != null) {
                    guard(); publish("A little encouragement")
                    withTimeoutOrNull(20000) { output.play(null, fallback) }
                }
                preferences.edit().putString("state", if (played) "played" else "finished").commit()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Coaching must never crash the tracking service or hide the saved summary. */ }
            finally {
                monitor.cancel()
                file?.delete()
                if (generation == currentGeneration) { view = CoachingView(); changed(view) }
            }
        }
    }
}
