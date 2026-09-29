package com.example.runningapp.sharing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.account.AccountSession
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import com.example.runningapp.sync.SyncApi
import com.example.runningapp.sync.SyncHttpException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.util.UUID

class PublicationSyncTest {
    private lateinit var db: RunDatabase
    private val dao get() = db.runs()
    private var account: AccountSession? = AccountSession("alice", "Alice", null, "alice-token", Long.MAX_VALUE)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RunDatabase::class.java).build()
    }
    @After fun close() { db.close() }
    private suspend fun run(owner: String? = "alice", synced: Boolean = true): String {
        val id = UUID.randomUUID().toString()
        val controller = RunController(id, RunSettings(RunMode.INDOOR, RunUnits.MILES, 0, RunGoal.None, null), RunClock { RunTime(0, 1700000000000) })
        controller.start(); controller.finish()
        RunRepository(dao).save(controller.checkpoint(), "local", "UTC", 0, false, cloudOwnerId = owner)
        if (synced && owner != null) dao.putOperation(dao.operation(id)!!.copy(status = "SYNCED"))
        return id
    }
    private fun engine(api: SyncApi) = PublicationSync(dao, api, { account })
    private class FakeApi : SyncApi {
        var revision = 0L
        var shared = false
        var photo = true
        var loseResponse = false
        var failNetwork = false
        var calls = 0
        val bodies = mutableListOf<String>()
        val receipts = mutableMapOf<String, String>()
        var afterPut: suspend () -> Unit = {}
        private fun response() = JSONObject().put("publication", JSONObject().put("shared", shared)
            .put("photoVisible", photo).put("revision", revision)
            .put("publicUrl", if (shared) "https://wayirun-dev.unopenedparachute.workers.dev/r/${"a".repeat(32)}" else JSONObject.NULL))
        override suspend fun request(session: AccountSession, path: String, method: String, body: ByteArray?, contentType: String, match: String?): JSONObject {
            assertEquals("alice", session.ownerId); assertTrue(path.startsWith("/api/publications/")); calls++
            if (failNetwork) throw IOException("synthetic offline")
            if (method == "GET") return response()
            assertEquals("PUT", method)
            val text = body!!.toString(Charsets.UTF_8); bodies += text
            val input = JSONObject(text); val op = input.getString("operationId")
            val previous = receipts[op]
            if (previous == null) {
                if (input.getLong("expectedRevision") != revision) throw SyncHttpException(409, "revision_conflict")
                when (input.getString("action")) {
                    "share" -> shared = true
                    "unshare" -> shared = false
                    "photo" -> photo = input.getBoolean("photoVisible")
                    else -> error("Unexpected action")
                }
                revision++; receipts[op] = text
            } else assertEquals(previous, text)
            afterPut()
            if (loseResponse) { loseResponse = false; throw IOException("synthetic lost receipt") }
            return response()
        }
    }

    @Test fun photoFreeShareAndIndependentPhotoVisibility() = runBlocking {
        val id = run(); val api = FakeApi()
        dao.preparePublication(id, "alice"); engine(api).runOnce()
        assertFalse(dao.publication(id)!!.shared); assertTrue(api.bodies.isEmpty())
        dao.queuePublication(id, "alice", photo = false); engine(api).runOnce()
        assertFalse(api.shared); assertFalse(api.photo); assertNull(dao.photo(id))
        dao.queuePublication(id, "alice", shared = true); engine(api).runOnce()
        val row = dao.publication(id)!!
        assertTrue(row.shared); assertFalse(row.photoVisible); assertNull(row.wantShared)
        assertTrue(row.publicUrl!!.contains("/r/"))
    }

    @Test fun offlineShareThenUnshareNeverSendsTheSupersededShare() = runBlocking {
        val id = run(synced = false); val api = FakeApi()
        dao.queuePublication(id, "alice", shared = true); assertTrue(engine(api).runOnce()); assertEquals(0, api.calls)
        dao.queuePublication(id, "alice", shared = false)
        dao.putOperation(dao.operation(id)!!.copy(status = "SYNCED")); engine(api).runOnce()
        assertEquals(listOf("unshare"), api.bodies.map { JSONObject(it).getString("action") })
        assertFalse(dao.publication(id)!!.shared); assertNull(dao.publication(id)!!.wantShared)
    }

    @Test fun uncertainRetryRetainsExactBodyAcrossEngineRecreation() = runBlocking {
        val id = run(); val api = FakeApi().apply { loseResponse = true }
        dao.queuePublication(id, "alice", shared = true); engine(api).runOnce()
        val body = dao.publication(id)!!.requestJson
        assertNotNull(body); assertTrue(api.shared)
        engine(api).runOnce()
        assertEquals(listOf(body, body), api.bodies); assertEquals(1L, api.revision)
        assertNull(dao.publication(id)!!.requestJson)
    }

    @Test fun replayAfterRemoteUnshareDoesNotRepublish() = runBlocking {
        val id = run(); val api = FakeApi().apply { loseResponse = true }
        dao.queuePublication(id, "alice", shared = true); engine(api).runOnce()
        api.shared = false; api.revision++
        engine(api).runOnce()
        assertFalse(api.shared); assertFalse(dao.publication(id)!!.shared)
        assertNull(dao.publication(id)!!.wantShared); assertEquals(2L, api.revision)
    }

    @Test fun newUnshareDefeatsInFlightShareAndStaleAcknowledgement() = runBlocking {
        val id = run(); val api = FakeApi()
        dao.queuePublication(id, "alice", shared = true)
        api.afterPut = { dao.queuePublication(id, "alice", shared = false) }
        engine(api).runOnce()
        assertEquals(false, dao.publication(id)!!.wantShared)
        api.afterPut = {}
        engine(api).runOnce() // Rebase revocation after the previous request committed.
        engine(api).runOnce()
        assertFalse(api.shared); assertNull(dao.publication(id)!!.wantShared)
    }

    @Test fun staleShareRefreshesAndRequiresAnotherExplicitAction() = runBlocking {
        val id = run(); val api = FakeApi()
        dao.preparePublication(id, "alice"); engine(api).runOnce()
        api.revision = 4
        dao.queuePublication(id, "alice", shared = true); engine(api).runOnce()
        val row = dao.publication(id)!!
        assertEquals("CONFLICT", row.error); assertNull(row.wantShared); assertFalse(api.shared)
        val count = api.calls; engine(api).runOnce(); assertEquals(count, api.calls)
        dao.queuePublication(id, "alice", shared = true); engine(api).runOnce(); assertTrue(api.shared)
    }

    @Test fun hiddenPhotoIsAppliedBeforeShareAndConflictDoesNotContinueShare() = runBlocking {
        val id = run(); val api = FakeApi()
        dao.preparePublication(id, "alice"); engine(api).runOnce()
        dao.queuePublication(id, "alice", photo = false)
        dao.queuePublication(id, "alice", shared = true)
        api.revision++
        engine(api).runOnce(); engine(api).runOnce()
        assertFalse(api.shared); assertNull(dao.publication(id)!!.wantShared)
        assertEquals("photo", JSONObject(api.bodies.single()).getString("action"))
    }

    @Test fun accountChangeCannotAcknowledgeOrSendAnotherOwnersWork() = runBlocking {
        val id = run(); val api = FakeApi()
        dao.queuePublication(id, "alice", shared = true)
        api.afterPut = { account = AccountSession("bob", "Bob", null, "bob-token", Long.MAX_VALUE) }
        engine(api).runOnce()
        assertEquals(true, dao.publication(id)!!.wantShared)
        val calls = api.calls; engine(api).runOnce(); assertEquals(calls, api.calls)
        try { dao.queuePublication(id, "bob", shared = true); fail("Foreign intent") } catch (_: IllegalArgumentException) { }
    }

    @Test fun deletionDuringRequestCannotRecreateState() = runBlocking {
        val id = run(); val api = FakeApi()
        dao.queuePublication(id, "alice", shared = true)
        api.afterPut = { dao.discard(id, "local") }
        engine(api).runOnce()
        assertNull(dao.publication(id)); assertEquals("DELETE", dao.operation(id)!!.action)
    }

    @Test fun localIntentNeedsExplicitImportAndNeverFollowsSignInAlone() = runBlocking {
        val id = run(owner = null); val api = FakeApi()
        dao.queuePublication(id, null, shared = true)
        engine(api).runOnce(); assertEquals(0, api.calls); assertNull(dao.publication(id)!!.ownerId)
        dao.importLocal("alice")
        assertEquals("alice", dao.publication(id)!!.ownerId)
        dao.putOperation(dao.operation(id)!!.copy(status = "SYNCED"))
        engine(api).runOnce(); assertTrue(api.shared)
    }

    @Test fun failedFetchRetainsIntentWithoutInventingPublicUrl() = runBlocking {
        val id = run(); val api = FakeApi().apply { failNetwork = true }
        dao.queuePublication(id, "alice", shared = true); engine(api).runOnce()
        val row = dao.publication(id)!!
        assertNull(row.publicUrl); assertEquals(true, row.wantShared); assertEquals("PENDING", row.error)
        api.failNetwork = false; engine(api).runOnce(); assertTrue(api.shared)
    }

    @Test fun expiredSessionRetainsWorkWithoutNetworkRequests() = runBlocking {
        val id = run(); val api = FakeApi()
        dao.queuePublication(id, "alice", shared = true)
        account = account!!.copy(expiresAtMs = 0)
        engine(api).runOnce()
        assertEquals(0, api.calls); assertEquals(true, dao.publication(id)!!.wantShared)
    }

    @Test fun reopeningDatabaseRetainsUncertainRequestBytes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "publication-reopen-${UUID.randomUUID()}.db"
        db.close()
        db = Room.databaseBuilder(context, RunDatabase::class.java, name).build()
        try {
            val id = run(); val api = FakeApi().apply { loseResponse = true }
            dao.queuePublication(id, "alice", shared = true); engine(api).runOnce()
            val pending = dao.publication(id)!!
            db.close()
            db = Room.databaseBuilder(context, RunDatabase::class.java, name).build()
            assertEquals(pending, dao.publication(id))
            engine(api).runOnce()
            assertEquals(1L, api.revision); assertEquals(api.bodies[0], api.bodies[1])
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
