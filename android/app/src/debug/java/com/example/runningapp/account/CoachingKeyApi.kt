package com.example.runningapp.account

import org.json.JSONObject
import java.util.UUID

data class CoachingKeyStatus(val configured: Boolean, val revision: String, val available: Boolean, val checkedAt: Long?)

interface CoachingKeys {
    suspend fun status(session: AccountSession): CoachingKeyStatus
    suspend fun change(session: AccountSession, revision: String, key: String?): CoachingKeyStatus
}

class CoachingKeyApi : CoachingKeys {
    private val api = AccountApi()
    override suspend fun status(session: AccountSession) = read(api.request("/api/account/openai-key", "GET", token = session.token))
    override suspend fun change(session: AccountSession, revision: String, key: String?): CoachingKeyStatus {
        val body = JSONObject().put("revision", revision).put("operationId", UUID.randomUUID().toString())
        key?.let { body.put("key", it) }
        return read(api.request("/api/account/openai-key", if (key == null) "DELETE" else "PUT", body, session.token))
    }
    private fun read(json: JSONObject) = CoachingKeyStatus(json.getBoolean("configured"), json.getString("revision"),
        json.getBoolean("available"), if (json.isNull("checkedAt")) null else json.getLong("checkedAt") * 1000)
}

fun coachingKeyError(error: Exception): String = when ((error as? AccountRequestException)?.code) {
    "key_invalid", "key_format" -> "That key wasn't accepted. Check the key and try again."
    "key_permission_denied" -> "This key cannot access OpenAI's model list. Enable read access to Models in its OpenAI permissions, then retry."
    "key_quota_exceeded" -> "OpenAI reports no available API quota. Check your API billing before retrying."
    "key_rate_limited", "too_many_requests" -> "Too many requests. Wait a minute, then retry."
    "key_check_unavailable" -> "OpenAI couldn't be reached to check the key. Try again later."
    "key_storage_unavailable" -> "Secure key storage is temporarily unavailable. Try again later."
    "key_changed" -> "Key settings changed or the result is uncertain. Refresh the status before retrying."
    "unauthorized" -> "Sign in again to manage your OpenAI key."
    else -> "Couldn't confirm the change. Refresh status before retrying; your existing key may still be saved."
}
