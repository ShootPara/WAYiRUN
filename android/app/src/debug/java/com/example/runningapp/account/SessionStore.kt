package com.example.runningapp.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One encrypted, atomic file, excluded from backup. This never touches the run database. */
class SessionStore(context: Context, name: String = "google-session") {
    private val file = AtomicFile(File(context.noBackupFilesDir, "$name.bin"))
    private val alias = "wayirun.account.session.v1.$name"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): AccountSession? = synchronized(lock) {
        if (!file.baseFile.exists()) return@synchronized null
        try {
            val bytes = file.readFully()
            require(bytes.size in 29..32768)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            Json.decodeFromString<AccountSession>(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
        } catch (_: Exception) { file.delete(); null }
    }
    fun write(session: AccountSession) = synchronized(lock) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(Json.encodeToString(session).toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    fun clear() = synchronized(lock) { file.delete() }
    companion object { private val lock = Any() }
}
