package com.example.runningapp.sync

import com.example.runningapp.account.AccountSession
import com.example.runningapp.storage.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/** Only immutable completed runs enter this engine. It never owns a tracking clock or controller. */
class SyncEngine(private val dao: RunDao, private val api: SyncApi,
    private val session: suspend () -> AccountSession?, private val now: () -> Long = System::currentTimeMillis) {
    private class Changed : Exception()
    private suspend fun request(op: SyncOperation, path: String, method: String = "GET", body: ByteArray? = null,
        contentType: String = "application/json", match: String? = null): JSONObject {
        currentCoroutineContext().ensureActive()
        val current = dao.operation(op.runId)
        if (current?.ownerId != op.ownerId || current.action != op.action || current.status != "PENDING") throw Changed()
        val account = session()
        if (account?.ownerId != op.ownerId) throw Changed()
        if (account.expired(now())) throw SyncHttpException(401, "unauthorized")
        return api.request(account, path, method, body, contentType, match)
    }
    /** True requests another bounded WorkManager attempt; pending rows remain durable on every failure. */
    suspend fun runOnce(): Boolean {
        val account = session() ?: return false
        var retry = false
        for (op in dao.pending(account.ownerId).take(4)) {
            if (op.nextAttemptMs > now()) { retry = true; continue }
            try {
                if (op.action == "DELETE") {
                    val result = request(op, "/api/runs/${op.runId}", "DELETE")
                    require(result.getString("runId") == op.runId && result.getBoolean("deleted"))
                    dao.mark(op.runId, op.ownerId, op.action, "DELETED", 0, 0, null)
                } else upload(op)
            } catch (changed: Changed) { /* Discard/account change wins over stale in-flight work. */ }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                val http = error as? SyncHttpException
                if (http?.status == 410 && http.code == "run_deleted") {
                    dao.remoteDeleted(op.runId, op.ownerId)
                    continue
                }
                val attempts = (dao.operation(op.runId)?.attempts ?: op.attempts) + 1
                val transient = error is java.io.IOException || http?.status in listOf(408, 429, 500, 502, 503, 504) ||
                    http?.code in listOf("upload_expired", "upload_incomplete")
                val state = if (http?.status == 401) "AUTH" else if (transient && attempts < 8) "PENDING" else "BLOCKED"
                val message = when {
                    state == "AUTH" -> "Sign in again to synchronize this account."
                    transient -> "Connection unavailable. Your run is safe on this phone."
                    http?.status == 409 -> "Cloud data conflicts with this run. The local run is unchanged."
                    else -> "This run could not synchronize. Its local data is preserved."
                }
                val delay = maxOf((http?.retrySeconds ?: 60) * 1000, (60000L shl (attempts - 1).coerceIn(0, 8)))
                val changed = dao.mark(op.runId, op.ownerId, op.action, state, attempts, now() + delay, message)
                if (changed > 0 && state == "PENDING") retry = true
                if (http?.status == 429) return true
            }
        }
        return retry || dao.pending(account.ownerId).isNotEmpty()
    }
    private suspend fun upload(op: SyncOperation) {
        val archive = requireNotNull(dao.archive(op.runId))
        require(archive.run.cloudOwnerId == op.ownerId)
        val data = archive.encode()
        val chunks = (data.indices step RunArchive.CHUNK_BYTES).map { data.copyOfRange(it, minOf(it + RunArchive.CHUNK_BYTES, data.size)) }
        val s = archive.run.decode().snapshot
        val summary = JSONObject().put("state", "FINISHED").put("startedUtcMs", s.startedUtcMs).put("endedUtcMs", s.endedUtcMs)
            .put("activeDurationMs", s.activeDurationMs).put("distanceMeters", s.distanceMeters).put("mode", s.settings.mode.name).put("units", s.settings.units.name)
        val descriptors = JSONArray()
        chunks.forEach { descriptors.put(JSONObject().put("sha256", RunArchive.sha(it)).put("bytes", it.size)) }
        val manifest = JSONObject().put("schemaVersion", 1).put("runId", op.runId).put("operationId", op.operationId)
            .put("summary", summary).put("chunks", descriptors)
        val begin = request(op, "/api/run-uploads", "POST", manifest.toString().toByteArray(Charsets.UTF_8))
        require(begin.getString("runId") == op.runId && begin.getString("operationId") == op.operationId)
        val hash = begin.getString("manifestHash"); require(hash.matches(Regex("[0-9a-f]{64}")))
        if (begin.isNull("completedAt")) {
            val state = request(op, "/api/run-uploads/${op.runId}")
            require(state.getString("manifestHash") == hash)
            val received = state.getJSONArray("received")
            val indexes = (0 until received.length()).map { received.getInt(it).also { index -> require(index in chunks.indices) } }.toSet()
            chunks.forEachIndexed { index, chunk ->
                if (index !in indexes) {
                    val result = request(op, "/api/run-uploads/${op.runId}/chunks/$index", "PUT", chunk, "application/octet-stream", hash)
                    require(result.getInt("index") == index && result.getString("sha256") == RunArchive.sha(chunk))
                    // Progress resets consecutive failures, without overwriting a discard that raced the request.
                    dao.mark(op.runId, op.ownerId, op.action, "PENDING", 0, 0, null)
                }
            }
            val done = request(op, "/api/run-uploads/${op.runId}/complete", "POST", match = hash)
            require(done.getString("runId") == op.runId && done.getString("operationId") == op.operationId &&
                done.getString("manifestHash") == hash && done.getLong("completedAt") > 0)
        } else require(begin.getLong("completedAt") > 0)
        dao.mark(op.runId, op.ownerId, op.action, "SYNCED", 0, 0, null)
    }
}
