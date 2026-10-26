package com.example.runningapp.account

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun interruptedAtomicWriteRestoresBackupWithoutSigningOut() {
        val store = SessionStore(context, "session-backup-test")
        val session = AccountSession("owner", "Runner", null, "token", 1000)
        val file = File(context.noBackupFilesDir, "session-backup-test.bin")
        try {
            store.write(session)
            assertTrue(file.renameTo(File(file.path + ".bak")))
            assertEquals(session, store.read())
        } finally { store.clear() }
    }

    @Test fun unavailableStorageDoesNotDeleteSavedSession() {
        val store = SessionStore(context, "session-io-test")
        val file = File(context.noBackupFilesDir, "session-io-test.bin")
        try {
            assertTrue(file.mkdir())
            assertNull(store.read())
            assertTrue(file.isDirectory)
        } finally { file.delete() }
    }

    @Test fun encryptedRoundTripRetainsExpiredIdentityAndClearLeavesOtherPreferencesAlone() {
        val store = SessionStore(context, "session-test")
        val session = AccountSession("test-owner", "Runner", null, "secret-session-token", 1)
        val prefs = context.getSharedPreferences("session-test-sentinel", Context.MODE_PRIVATE)
        prefs.edit().putString("run-owner", "original-owner").commit()
        try {
            store.write(session)
            assertEquals(session, SessionStore(context, "session-test").read())
            assertTrue(store.read()!!.expired())
            val raw = File(context.noBackupFilesDir, "session-test.bin").readBytes().decodeToString()
            assertFalse(raw.contains(session.token)); assertFalse(raw.contains(session.ownerId))
            store.clear(); assertNull(store.read())
            assertEquals("original-owner", prefs.getString("run-owner", null))
            assertFalse(session.toString().contains(session.token))
        } finally { store.clear(); prefs.edit().clear().commit() }
    }
    @Test fun tamperedCiphertextCannotRestoreAnAccount() {
        val store = SessionStore(context, "session-tamper-test")
        try {
            store.write(AccountSession("owner", "Runner", null, "token", 1000))
            val file = File(context.noBackupFilesDir, "session-tamper-test.bin")
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertNull(store.read()); assertFalse(file.exists())
        } finally { store.clear() }
    }
}
