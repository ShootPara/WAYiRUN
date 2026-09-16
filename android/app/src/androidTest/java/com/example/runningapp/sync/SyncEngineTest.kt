package com.example.runningapp.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.account.AccountSession
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.util.UUID

class SyncEngineTest {
    private lateinit var db: RunDatabase
    private val dao get() = db.runs()
    private var now = 100000L
    private var account: AccountSession? = AccountSession("alice", "Alice", null, "test-token", Long.MAX_VALUE)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RunDatabase::class.java).build() }
    @After fun close() { db.close() }
    private suspend fun run(owner: String? = "alice"): String {
        var time = 0L
        val c = RunController(UUID.randomUUID().toString(), RunSettings(RunMode.OUTDOOR, RunUnits.KILOMETERS, 0, RunGoal.None, null), RunClock { RunTime(time, 1700000000000 + time) })
        c.start(); val repo = RunRepository(dao)
        repo.save(c.checkpoint(), owner ?: "local", "UTC", 0, false, cloudOwnerId = owner)
        c.selectSource(DistanceSource.GPS); val segment = c.snapshot().currentSegmentId!!
        c.record(RunMeasurement.Gps(segment, 0, 0.0)); time = 10000
        val reading = RunMeasurement.Gps(segment, time, 125.0)
        c.record(reading); c.finish()
        val id = c.snapshot().runId
        repo.save(c.checkpoint(), owner ?: "local", "UTC", 0, false,
            RoutePoint(runId = id, segmentId = segment, monotonicMs = time, latitude = 45.0, longitude = -75.0, accuracyMeters = 2f),
            StoredMeasurement(runId = id, segmentId = segment, monotonicMs = time, source = "GPS", deltaMeters = 125.0,
                totalMeters = 125.0, activeMs = time, reading = Json.encodeToString<RunMeasurement>(reading)), owner)
        return id
    }
    private fun engine(api: SyncApi) = SyncEngine(dao, api, { account }, { now })
    private class FakeApi : SyncApi {
        var manifest: JSONObject? = null
        val chunks = mutableMapOf<Int, ByteArray>()
        var done = false; var deleted = false; var loseCompletion = false
        var calls = 0
        var hook: suspend (String, String) -> Unit = { _, _ -> }
        val owners = mutableListOf<String>()
        private val hash = "c".repeat(64)
        private fun receipt() = JSONObject().put("runId", manifest!!.getString("runId")).put("operationId", manifest!!.getString("operationId"))
            .put("manifestHash", hash).put("completedAt", if (done) 50 else JSONObject.NULL)
        override suspend fun request(session: AccountSession, path: String, method: String, body: ByteArray?, contentType: String, match: String?): JSONObject {
            calls++; owners.add(session.ownerId); hook(path, method)
            if (method == "DELETE") { deleted = true; chunks.clear(); done = false; return JSONObject().put("runId", path.substringAfterLast('/')).put("deleted", true) }
            if (deleted) throw SyncHttpException(410, "run_deleted")
            if (path == "/api/run-uploads") { manifest = JSONObject(body!!.toString(Charsets.UTF_8)); return receipt() }
            if (method == "GET") return receipt().put("received", JSONArray(chunks.keys.toList()))
            if (method == "PUT") {
                assertEquals(hash, match)
                val index = path.substringAfterLast('/').toInt(); chunks[index] = body!!
                return JSONObject().put("index", index).put("sha256", RunArchive.sha(body))
            }
            done = true
            if (loseCompletion) { loseCompletion = false; throw IOException("response lost") }
            return receipt()
        }
    }
    @Test fun archiveRoundTripIncludesAllTablesAndRejectsCorruption() = runBlocking {
        val id = run(); val archive = dao.archive(id)!!
        assertEquals(archive, RunArchive.decode(archive.encode()))
        assertTrue(archive.route.isNotEmpty() && archive.measurements.isNotEmpty() && archive.splits.isNotEmpty())
        assertTrue(archive.intervals.isNotEmpty() && archive.segments.isNotEmpty())
        for (bad in listOf(archive.copy(version = 2), archive.copy(route = archive.route.map { it.copy(latitude = 100.0) }),
            archive.copy(measurements = archive.measurements.map { it.copy(runId = "foreign") }), archive.copy(splits = emptyList()))) {
            try { bad.encode(); fail("Must reject invalid archive") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun explicitImportClaimsOnlyUnassignedRunsOnceAndKeepsExistingOwner() = runBlocking {
        val local = run(null); val foreign = run("bob")
        assertNull(dao.operation(local))
        assertEquals(1, dao.importLocal("alice")); val operation = dao.operation(local)!!
        assertEquals("local", dao.get(local)!!.ownerId)
        assertEquals("alice", dao.get(local)!!.cloudOwnerId)
        assertEquals(0, dao.importLocal("bob"))
        assertEquals(operation, dao.operation(local))
        assertEquals("bob", dao.operation(foreign)!!.ownerId)
        assertNotEquals(foreign, dao.latestVisible("alice")?.id)
    }
    @Test fun failedFinishQueueWriteRollsBackRunAndImportFailureRollsBackOwnership() = runBlocking {
        val local = run(null)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER refuse_queue BEFORE INSERT ON run_sync BEGIN SELECT RAISE(ABORT, 'test'); END")
        try { dao.importLocal("alice"); fail("Expected rollback") } catch (_: android.database.sqlite.SQLiteException) { }
        assertNull(dao.get(local)!!.cloudOwnerId); assertNull(dao.operation(local))
        var time = 0L
        val c = RunController(UUID.randomUUID().toString(), RunSettings(RunMode.INDOOR, RunUnits.MILES, 0, RunGoal.None, null), RunClock { RunTime(time, time) })
        c.start(); val repo = RunRepository(dao)
        repo.save(c.checkpoint(), "alice", "UTC", 0, false, cloudOwnerId = "alice")
        time = 1000; c.finish()
        try { repo.save(c.checkpoint(), "alice", "UTC", 0, false, cloudOwnerId = "alice"); fail("Expected rollback") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals("RUNNING", dao.get(c.snapshot().runId)!!.state)
    }
    @Test fun lostCompletionResponseRetriesSameOperationWithoutLosingData() = runBlocking {
        val id = run(); val api = FakeApi().apply { loseCompletion = true }
        val identity = dao.operation(id)!!.operationId
        assertTrue(engine(api).runOnce()); assertTrue(api.done)
        assertEquals("PENDING", dao.operation(id)!!.status)
        now += 1000000
        assertFalse(engine(api).runOnce())
        assertEquals(identity, dao.operation(id)!!.operationId)
        assertEquals("SYNCED", dao.operation(id)!!.status)
        val restored = RunArchive.decode(api.chunks.toSortedMap().values.fold(byteArrayOf()) { a, b -> a + b })
        assertEquals(dao.archive(id), restored)
        assertNotNull(dao.get(id))
    }
    @Test fun discardDuringUploadCannotBeOverwrittenByLateAcknowledgement() = runBlocking {
        val id = run(); val api = FakeApi()
        api.hook = { _, method -> if (method == "PUT") assertTrue(dao.discard(id, "alice")) }
        assertTrue(engine(api).runOnce())
        assertNull(dao.get(id)); assertTrue(dao.route(id).isEmpty()); assertTrue(dao.measurements(id).isEmpty())
        assertEquals("DELETE", dao.operation(id)!!.action)
        api.hook = { _, _ -> }
        assertFalse(engine(api).runOnce())
        assertTrue(api.deleted); assertTrue(api.chunks.isEmpty()); assertEquals("DELETED", dao.operation(id)!!.status)
    }
    @Test fun accountSwitchStopsNetworkWorkAndSameOwnerCanResume() = runBlocking {
        val id = run(); val api = FakeApi()
        api.hook = { _, method -> if (method == "PUT") account = account!!.copy(ownerId = "bob") }
        engine(api).runOnce()
        assertEquals(setOf("alice"), api.owners.toSet())
        val calls = api.calls; engine(api).runOnce(); assertEquals(calls, api.calls)
        account = account!!.copy(ownerId = "alice"); api.hook = { _, _ -> }
        engine(api).runOnce(); assertEquals("SYNCED", dao.operation(id)!!.status)
    }
    @Test fun expiredSessionAndRepeatedNetworkFailureKeepTheRunAndBoundRetries() = runBlocking {
        val id = run(); val api = FakeApi()
        account = account!!.copy(expiresAtMs = 1)
        assertFalse(engine(api).runOnce()); assertEquals(0, api.calls)
        assertEquals("AUTH", dao.operation(id)!!.status)
        account = account!!.copy(expiresAtMs = Long.MAX_VALUE); dao.retry("alice")
        api.hook = { _, _ -> throw IOException("offline") }
        repeat(8) { engine(api).runOnce(); now += 100000000 }
        assertEquals(8, api.calls); assertEquals("BLOCKED", dao.operation(id)!!.status)
        engine(api).runOnce(); assertEquals(8, api.calls); assertNotNull(dao.get(id))
        dao.retry("bob"); assertEquals("BLOCKED", dao.operation(id)!!.status)
        dao.retry("alice"); assertEquals("PENDING", dao.operation(id)!!.status)
    }
    @Test fun deletionOfCloudCopyIsDurableAcrossOfflineFailure() = runBlocking {
        val id = run(); val api = FakeApi(); engine(api).runOnce()
        dao.discard(id, "alice"); api.hook = { _, _ -> throw IOException("offline") }
        engine(api).runOnce(); assertNull(dao.get(id)); assertEquals("DELETE", dao.operation(id)!!.action)
        now += 1000000; api.hook = { _, _ -> }; engine(api).runOnce()
        assertEquals("DELETED", dao.operation(id)!!.status); assertTrue(api.deleted)
    }
}
