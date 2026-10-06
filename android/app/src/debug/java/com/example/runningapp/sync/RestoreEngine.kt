package com.example.runningapp.sync

import com.example.runningapp.account.AccountSession
import com.example.runningapp.storage.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

/** Separate pull cursor: a failed download cannot prevent local uploads or tracking. */
class RestoreEngine(private val dao: RunDao, private val api: SyncApi, private val cache: DownloadCache,
    private val session: suspend () -> AccountSession?, private val now: () -> Long = System::currentTimeMillis) {
    private class Changed : Exception()
    private suspend fun account(owner: String): AccountSession {
        currentCoroutineContext().ensureActive()
        val value = session()
        if (value?.ownerId != owner) throw Changed()
        if (value.expired(now())) throw SyncHttpException(401, "unauthorized")
        return value
    }
    private suspend fun request(owner: String, path: String) = api.request(account(owner), path)
    suspend fun runOnce(): Boolean {
        val owner = session()?.ownerId ?: return false
        var state = dao.pull(owner) ?: PullState(owner).also { dao.putPull(it) }
        if (state.status != "PENDING" || state.nextAttemptMs > now()) return false
        try {
            if (state.phase == "DELETIONS") {
                val page = request(owner, path("/api/run-deletions", state.cursor))
                val rows = page.getJSONArray("deleted"); require(rows.length() <= 20)
                if (rows.length() > 0) cache.clearOwner(owner)
                var previous = state.cursor.orEmpty()
                for (i in 0 until rows.length()) {
                    val id = rows.getString(i); require(uuid.matches(id) && id > previous); previous = id
                    account(owner); dao.remoteDeleted(id, owner)
                }
                val next = if (page.isNull("next")) null else page.getString("next")
                require(next == null || (rows.length() == 20 && next == previous))
                state = if (next == null) PullState(owner, phase = "RUNS") else PullState(owner, cursor = next)
                dao.putPull(state)
                return true
            }
            val page = request(owner, path("/api/runs", state.cursor))
            val rows = page.getJSONArray("runs"); require(rows.length() <= 20)
            if (rows.length() == 0) {
                // Periodic full sweeps also catch insertions before an earlier pagination cursor.
                dao.putPull(PullState(owner, nextAttemptMs = now() + 15 * 60000))
                return false
            }
            for (i in 0 until minOf(4, rows.length())) {
                val receipt = rows.getJSONObject(i)
                val id = receipt.getString("runId"); require(uuid.matches(id))
                val completed = receipt.getLong("completedAt"); require(completed > 0)
                val cursor = "$completed:$id"
                state.cursor?.let {
                    val oldTime = it.substringBefore(':').toLong(); val oldId = it.substringAfter(':')
                    require(completed > oldTime || (completed == oldTime && id > oldId))
                }
                val local = dao.get(id); val op = dao.operation(id)
                require(local == null || local.cloudOwnerId == owner)
                require(op == null || op.ownerId == owner)
                if (local == null && op?.action != "DELETE") {
                    try { restore(owner, receipt) }
                    catch (http: SyncHttpException) { if (http.status != 404) throw http; cache.clearOwner(owner) } // Concurrent deletion; never infer deletion from absence.
                }
                account(owner)
                state = PullState(owner, phase = "RUNS", cursor = cursor)
                dao.putPull(state)
            }
            return true
        } catch (_: Changed) { return false }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            val http = error as? SyncHttpException
            val attempts = (dao.pull(owner)?.attempts ?: state.attempts) + 1
            val transient = error is java.io.IOException || http?.status in listOf(408, 429, 500, 502, 503, 504)
            val status = if (http?.status == 401) "AUTH" else if (transient && attempts < 8) "PENDING" else "BLOCKED"
            val message = when {
                status == "AUTH" -> "Sign in again to restore this account's runs."
                transient -> "Restore will retry when connected. Existing runs are safe."
                else -> "A cloud run could not be verified. Existing runs are unchanged."
            }
            val delay = maxOf((http?.retrySeconds ?: 60) * 1000, 60000L shl (attempts - 1).coerceIn(0, 8))
            dao.putPull(state.copy(status = status, attempts = attempts, nextAttemptMs = now() + delay, error = message))
            return status == "PENDING"
        }
    }
    private suspend fun restore(owner: String, listed: JSONObject) {
        val id = listed.getString("runId")
        val detail = request(owner, "/api/runs/$id")
        for (field in listOf("runId", "operationId", "manifestHash", "completedAt")) require(detail.get(field) == listed.get(field))
        val operation = detail.getString("operationId"); require(uuid.matches(operation))
        val hash = detail.getString("manifestHash"); require(digest.matches(hash))
        val manifestText = detail.getString("manifestJson")
        require(RunArchive.sha(manifestText.toByteArray(Charsets.UTF_8)) == hash)
        val manifest = JSONObject(manifestText)
        require(manifest.getInt("schemaVersion") == 1 && manifest.getString("runId") == id && manifest.getString("operationId") == operation)
        val descriptors = manifest.getJSONArray("chunks"); require(descriptors.length() in 1..128)
        val key = cache.prepare(owner, "$id:$hash")
        val output = ByteArrayOutputStream()
        for (index in 0 until descriptors.length()) {
            account(owner)
            if (dao.operation(id)?.action == "DELETE") { cache.clear(key); return }
            val descriptor = descriptors.getJSONObject(index)
            val size = descriptor.getInt("bytes"); val chunkHash = descriptor.getString("sha256")
            require(size in 1..RunArchive.CHUNK_BYTES && digest.matches(chunkHash))
            val bytes = cache.read(key, index, size, chunkHash) ?: api.download(account(owner), "/api/runs/$id/chunks/$index").also {
                require(it.size == size && RunArchive.sha(it) == chunkHash)
                cache.write(key, index, it)
                dao.pull(owner)?.let { progress -> dao.putPull(progress.copy(attempts = 0)) }
            }
            require(output.size() + bytes.size <= RunArchive.MAX_BYTES); output.write(bytes)
        }
        val archive = RunArchive.decode(output.toByteArray())
        require(archive.run.id == id && archive.run.cloudOwnerId == owner)
        val snapshot = archive.run.decode().snapshot
        val summary = manifest.getJSONObject("summary")
        require(summary.getString("state") == "FINISHED" && summary.getLong("startedUtcMs") == snapshot.startedUtcMs &&
            summary.getLong("endedUtcMs") == snapshot.endedUtcMs && summary.getLong("activeDurationMs") == snapshot.activeDurationMs &&
            summary.getDouble("distanceMeters") == snapshot.distanceMeters && summary.getString("mode") == snapshot.settings.mode.name &&
            summary.getString("units") == snapshot.settings.units.name)
        // Detect deletion during download before committing the fully validated archive.
        val fresh = request(owner, "/api/runs/$id")
        require(fresh.getString("manifestHash") == hash && fresh.getString("operationId") == operation)
        account(owner)
        dao.restore(archive, owner, operation)
        cache.clear(key)
    }
    private fun path(root: String, cursor: String?) = root + (cursor?.let { "?after=" + URLEncoder.encode(it, "UTF-8") } ?: "")
    companion object {
        private val uuid = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
        private val digest = Regex("[0-9a-f]{64}")
    }
}
