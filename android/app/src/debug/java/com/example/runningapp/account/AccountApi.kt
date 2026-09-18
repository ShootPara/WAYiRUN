package com.example.runningapp.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class AccountApi {
    private val origin = "https://wayirun-dev.unopenedparachute.workers.dev"
    suspend fun challenge(): String = request("/api/auth/challenge", "POST").getString("nonce")
        .also { require(it.matches(Regex("[0-9a-f]{64}"))) }

    suspend fun exchange(idToken: String, nonce: String): AccountSession {
        val issuedAt = System.currentTimeMillis()
        val result = request("/api/auth/google", "POST", JSONObject().put("idToken", idToken).put("nonce", nonce))
        val token = result.getString("accessToken")
        require(token.matches(Regex("[0-9a-f]{64}")))
        // The service issues 90-day sessions; retain a bound before converting to milliseconds.
        val expiry = issuedAt + result.getLong("expiresIn").also { require(it in 1L..90L * 24 * 60 * 60) } * 1000
        try {
            val profile = request("/api/account", "GET", token = token).getJSONObject("account")
            return AccountSession(profile.getString("id"), profile.optionalString("displayName"),
                profile.optionalString("pictureUrl"), token, expiry)
        } catch (error: Exception) {
            try { logout(token) } catch (_: Exception) { /* Session expires server-side. */ }
            throw error
        }
    }
    suspend fun logout(token: String) { request("/api/auth/logout", "POST", token = token) }
    private fun JSONObject.optionalString(name: String) = if (isNull(name)) null else getString(name)

    private suspend fun request(path: String, method: String, body: JSONObject? = null, token: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL(origin + path).openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15000; connection.readTimeout = 15000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                body?.let {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { stream -> stream.write(it.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status !in 200..299) throw AccountRequestException(status)
                val bytes = connection.inputStream.use { it.readBytesBounded() }
                JSONObject(bytes.toString(Charsets.UTF_8))
            } finally { connection.disconnect() }
        }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer); if (count < 0) break
            require(output.size() + count <= 32768)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

class AccountRequestException(val status: Int) : Exception("Account request failed ($status)")
