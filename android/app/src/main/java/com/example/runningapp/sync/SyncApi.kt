package com.example.runningapp.sync

import com.example.runningapp.BuildConfig
import com.example.runningapp.account.AccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

interface SyncApi {
    suspend fun download(session: AccountSession, path: String): ByteArray = error("Binary download unsupported")
    suspend fun request(session: AccountSession, path: String, method: String = "GET", body: ByteArray? = null,
        contentType: String = "application/json", match: String? = null): JSONObject
}
class SyncHttpException(val status: Int, val code: String?, val retrySeconds: Long = 60) : Exception("Sync request failed ($status)")

class CloudSyncApi : SyncApi {
    override suspend fun request(session: AccountSession, path: String, method: String, body: ByteArray?, contentType: String, match: String?): JSONObject =
        JSONObject(exchange(session, path, method, body, contentType, match, 65536).decodeToString(throwOnInvalidSequence = true))
    override suspend fun download(session: AccountSession, path: String): ByteArray =
        exchange(session, path, "GET", null, "application/octet-stream", null, 131072)
    private suspend fun exchange(session: AccountSession, path: String, method: String, body: ByteArray?,
        contentType: String, match: String?, limit: Int): ByteArray = withContext(Dispatchers.IO) {
            require(path.startsWith("/api/") && !path.contains(".."))
            val connection = URL("${BuildConfig.API_ORIGIN}$path").openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15000; connection.readTimeout = 15000
                connection.instanceFollowRedirects = false; connection.useCaches = false
                connection.setRequestProperty("Authorization", "Bearer ${session.token}")
                match?.let { connection.setRequestProperty("If-Match", "\"$it\"") }
                body?.let {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", contentType)
                    connection.setFixedLengthStreamingMode(it.size)
                    connection.outputStream.use { stream -> stream.write(it) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val data = stream?.use {
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while (true) {
                        val count = it.read(buffer); if (count < 0) break
                        require(output.size() + count <= limit)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: byteArrayOf()
                if (status !in 200..299) {
                    val code = runCatching { JSONObject(data.toString(Charsets.UTF_8)).optString("error") }.getOrNull()
                    throw SyncHttpException(status, code, connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(60, 86400) ?: 60)
                }
                if (limit == 131072) require(connection.contentType?.substringBefore(';') == "application/octet-stream")
                data
            } finally { connection.disconnect() }
        }
}
