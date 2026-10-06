package com.example.runningapp.coaching

import com.example.runningapp.account.AccountSession
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** No automatic POST retries. Cancellation disconnects the request; the server keeps its receipt. */
class CoachingNetwork {
    suspend fun generate(session: AccountSession, runId: String, operationId: String): JSONObject =
        JSONObject(request(session, runId, false, JSONObject().put("operationId", operationId).toString()).toString(Charsets.UTF_8))
    suspend fun status(session: AccountSession, runId: String): JSONObject =
        JSONObject(request(session, runId, false, null).toString(Charsets.UTF_8))
    suspend fun audio(session: AccountSession, runId: String): ByteArray = request(session, runId, true, null)

    private suspend fun request(session: AccountSession, runId: String, audio: Boolean, body: String?): ByteArray = suspendCancellableCoroutine { continuation ->
        require(runId.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
        val connection = URL("https://wayirun-dev.unopenedparachute.workers.dev/api/coaching/$runId${if (audio) "/audio" else ""}")
            .openConnection() as HttpsURLConnection
        continuation.invokeOnCancellation { connection.disconnect() }
        executor.execute {
            try {
                if (!continuation.isActive) return@execute
                connection.requestMethod = if (body == null) "GET" else "POST"
                connection.connectTimeout = 10000; connection.readTimeout = if (body == null) 15000 else 95000
                connection.instanceFollowRedirects = false; connection.useCaches = false
                connection.setRequestProperty("Authorization", "Bearer ${session.token}")
                body?.let {
                    connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { output -> output.write(it.toByteArray(Charsets.UTF_8)) }
                }
                check(connection.responseCode == 200)
                check(connection.contentType?.startsWith(if (audio) "audio/wav" else "application/json") == true)
                val result = java.io.ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        check(result.size() + count <= if (audio) 4194304 else 32768)
                        result.write(buffer, 0, count)
                    }
                }
                check(result.size() > 0)
                if (continuation.isActive) continuation.resume(result.toByteArray())
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resumeWithException(java.io.IOException("Coaching unavailable"))
            } finally { connection.disconnect() }
        }
    }
    companion object { private val executor = Executors.newFixedThreadPool(2) }
}
