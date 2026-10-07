package com.example.runningapp.account

import kotlinx.serialization.Serializable

@Serializable
data class AccountSession(val ownerId: String, val displayName: String?, val pictureUrl: String?,
    val token: String, val expiresAtMs: Long) {
    fun expired(nowMs: Long = System.currentTimeMillis()) = nowMs >= expiresAtMs
    // Do not expose bearer credentials through incidental object logging.
    override fun toString() = "AccountSession(redacted)"
}

data class AccountView(val session: AccountSession? = null, val busy: Boolean = false, val message: String? = null)
