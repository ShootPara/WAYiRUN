package com.example.runningapp.coaching

import com.example.runningapp.BuildConfig
import com.example.runningapp.account.AccountSession
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface CoachingApi {
    suspend fun generate(session: AccountSession, runId: String, operationId: String): JSONObject
}
class CoachingHttpException(val status: Int) : java.io.IOException("Coaching request failed ($status)")

/** Retry policy lives in the durable queue; this transport performs one HTTP attempt. */
class CoachingNetwork : CoachingApi {
    override suspend fun generate(session: AccountSession, runId: String, operationId: String): JSONObject =
        JSONObject(request(session, runId, false, JSONObject().put("operationId", operationId).toString()).toString(Charsets.UTF_8))
    suspend fun status(session: AccountSession, runId: String): JSONObject =
        JSONObject(request(session, runId, false, null).toString(Charsets.UTF_8))
    suspend fun audio(session: AccountSession, runId: String): ByteArray = request(session, runId, true, null)

    private suspend fun request(session: AccountSession, runId: String, audio: Boolean, body: String?): ByteArray = suspendCancellableCoroutine { continuation ->
        require(runId.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
        val connection = URL("${BuildConfig.API_ORIGIN}/api/coaching/$runId${if (audio) "/audio" else ""}")
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
                val responseCode = connection.responseCode
                if (responseCode != 200) throw CoachingHttpException(responseCode)
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
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(if (error is CoachingHttpException) error else java.io.IOException("Coaching unavailable", error))
            } finally { connection.disconnect() }
        }
    }
    companion object { private val executor = Executors.newFixedThreadPool(2) }
}
