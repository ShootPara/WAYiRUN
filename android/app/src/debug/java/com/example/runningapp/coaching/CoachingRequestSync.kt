package com.example.runningapp.coaching

import com.example.runningapp.account.AccountSession
import com.example.runningapp.storage.RunDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlin.math.min

class CoachingRequestSync(
    private val dao: RunDao,
    private val api: CoachingApi,
    private val session: suspend () -> AccountSession?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun runOnce(): Boolean = lock.withLock {
        val account = session() ?: return false
        val due = dao.pendingCoaching(account.ownerId, now())
        due.forEach { submitUnlocked(it.runId, account) }
        dao.pendingCoaching(account.ownerId, Long.MAX_VALUE).isNotEmpty()
    }

    suspend fun submit(runId: String, account: AccountSession): JSONObject? = lock.withLock {
        submitUnlocked(runId, account)
    }

    private suspend fun submitUnlocked(runId: String, account: AccountSession): JSONObject? {
        val request = dao.coachingRequest(runId) ?: return null
        if (request.ownerId != account.ownerId || request.status != "PENDING") return null
        return try {
            val result = api.generate(account, runId, request.operationId)
            check(result.optString("runId") == runId && result.optString("operationId") == request.operationId)
            val state = result.optString("state")
            check(state in setOf("preparing", "text_pending", "speech_pending", "ready", "failed", "unknown"))
            dao.markCoaching(runId, request.ownerId, request.operationId, "ACKNOWLEDGED", request.attempts + 1,
                0, null, state, now())
            result
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (error: Exception) {
            val attempts = request.attempts + 1
            val http = error as? CoachingHttpException
            val transient = error is java.io.IOException || http?.status in setOf(408, 429, 500, 502, 503, 504)
            val status = when { http?.status == 401 -> "AUTH"; transient && attempts < 8 -> "PENDING"; else -> "BLOCKED" }
            val delay = min(3_600_000L, 15_000L shl min(attempts - 1, 8))
            dao.markCoaching(runId, request.ownerId, request.operationId, status, attempts,
                if (status == "PENDING") now() + delay else 0, error.message?.take(240), null, null)
            null
        }
    }
    companion object { private val lock = Mutex() }
}
