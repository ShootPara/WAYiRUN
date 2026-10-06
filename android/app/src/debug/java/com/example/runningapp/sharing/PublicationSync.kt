package com.example.runningapp.sharing

import com.example.runningapp.account.AccountSession
import com.example.runningapp.storage.RunDao
import com.example.runningapp.storage.RunPublication
import com.example.runningapp.sync.SyncApi
import com.example.runningapp.sync.SyncHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

class PublicationSync(private val dao: RunDao, private val api: SyncApi,
    private val session: () -> AccountSession?) {
    suspend fun runOnce(onlyRun: String? = null): Boolean = lock.withLock {
        val account = session() ?: return@withLock false
        if (account.expired()) return@withLock false
        val work = if (onlyRun == null) dao.pendingPublications(account.ownerId) else
            listOfNotNull(dao.publication(onlyRun)).filter { it.ownerId == account.ownerId && (!it.known || it.wantShared != null || it.wantPhoto != null) }
        for (initial in work) {
            var row = initial
            fun current() = session()?.token == account.token && session()?.ownerId == account.ownerId
            suspend fun usable() = current() && dao.get(row.runId)?.cloudOwnerId == account.ownerId &&
                dao.operation(row.runId)?.let { it.ownerId == account.ownerId && it.action == "UPLOAD" && it.status == "SYNCED" } == true &&
                dao.publication(row.runId) == row
            if (!usable()) continue
            val path = "/api/publications/${row.runId}"
            try {
                if (!row.known && row.requestJson == null) {
                    val fresh = observed(row, api.request(account, path))
                    if (!current() || !dao.replacePublication(row, fresh)) continue
                    row = fresh
                }
                val action = when {
                    row.wantShared == false -> "unshare"
                    row.wantPhoto != null -> "photo"
                    row.wantShared == true -> "share"
                    else -> continue
                }
                if (row.requestJson == null) {
                    val body = JSONObject().put("operationId", UUID.randomUUID().toString())
                        .put("expectedRevision", row.revision).put("action", action)
                    if (action == "photo") body.put("photoVisible", row.wantPhoto)
                    val prepared = row.copy(requestJson = body.toString(), error = null)
                    if (!current() || !dao.replacePublication(row, prepared)) continue
                    row = prepared
                }
                if (!usable()) continue
                // Persist the exact operation before transmission; uncertain retries never invent a new receipt.
                val sentAction = JSONObject(row.requestJson!!).getString("action")
                val result = api.request(account, path, "PUT", row.requestJson!!.toByteArray(Charsets.UTF_8))
                val fresh = observed(row, result).copy(requestJson = null,
                    wantShared = if (sentAction == "photo") row.wantShared else null,
                    wantPhoto = if (sentAction == "photo") null else row.wantPhoto)
                if (current()) dao.replacePublication(row, fresh)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (!current()) continue
                if (error is SyncHttpException && error.status == 409) {
                    try {
                        val fresh = observed(row, api.request(account, path))
                        val action = row.requestJson?.let { JSONObject(it).getString("action") }
                        // Revocation may rebase; sharing must require a new explicit action after conflict.
                        val reconciled = fresh.copy(requestJson = null,
                            wantShared = if (action == "unshare") false else null,
                            wantPhoto = if (action == "unshare") row.wantPhoto else null,
                            error = if (action == "unshare") null else "CONFLICT")
                        if (current()) dao.replacePublication(row, reconciled)
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { if (current()) dao.replacePublication(row, row.copy(error = "PENDING")) }
                } else dao.replacePublication(row, row.copy(error = "PENDING"))
            }
        }
        currentPending(account.ownerId, onlyRun)
    }

    private suspend fun currentPending(owner: String, id: String?) =
        dao.pendingPublications(owner).any { id == null || it.runId == id }

    private fun observed(row: RunPublication, result: JSONObject): RunPublication {
        val value = result.getJSONObject("publication")
        require(value.get("shared") is Boolean && value.get("photoVisible") is Boolean)
        require(value.get("revision") is Int || value.get("revision") is Long)
        val revision = value.getLong("revision")
        require(revision >= row.revision && revision <= 9007199254740991L)
        val shared = value.getBoolean("shared")
        val link = if (value.isNull("publicUrl")) null else value.getString("publicUrl")
        require(if (shared) link != null && PUBLIC_LINK.matches(link) else link == null)
        return row.copy(known = true, shared = shared, photoVisible = value.getBoolean("photoVisible"),
            revision = revision, publicUrl = link, error = null)
    }

    companion object {
        private val lock = Mutex()
        private val PUBLIC_LINK = Regex("https://wayirun-dev\\.unopenedparachute\\.workers\\.dev/r/[0-9a-f]{32}")
    }
}
