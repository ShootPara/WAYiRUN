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
import java.io.File
import java.io.IOException
import java.util.UUID

class RestoreEngineTest {
    private lateinit var db: RunDatabase
    private val dao get() = db.runs()
    private lateinit var directory: File
    private var now = 100000L
    private var account: AccountSession? = AccountSession("alice", "Alice", null, "test", Long.MAX_VALUE)
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RunDatabase::class.java).build()
        directory = File(context.cacheDir, "restore-test-${UUID.randomUUID()}").also { it.mkdirs() }
    }
    @After fun close() { db.close(); directory.deleteRecursively() }
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

    private suspend fun fixture(): RunArchive {
        val id = run(); val archive = dao.archive(id)!!
        db.openHelper.writableDatabase.execSQL("DELETE FROM runs WHERE id = ?", arrayOf(id))
        db.openHelper.writableDatabase.execSQL("DELETE FROM run_sync WHERE runId = ?", arrayOf(id))
        return archive
    }
    private fun engine(api: SyncApi) = RestoreEngine(dao, api, DownloadCache(directory), { account }, { now })
    private class FakeApi(val archive: RunArchive) : SyncApi {
        var data = archive.encode()
        var chunks = data.toList().chunked(600).map { it.toByteArray() }
        val operation = UUID.randomUUID().toString()
        var deletes = emptyList<String>()
        var detailCalls = 0; var binaryCalls = 0
        var corrupt = false; var gone = false
        var hook: suspend (String) -> Unit = { }
        val owners = mutableListOf<String>()
        fun manifest(): JSONObject {
            val descriptors = JSONArray(); chunks.forEach { descriptors.put(JSONObject().put("sha256", RunArchive.sha(it)).put("bytes", it.size)) }
            return JSONObject().put("schemaVersion", 1).put("runId", archive.run.id).put("operationId", operation)
                .put("summary", summary()).put("chunks", descriptors)
        }
        val hash get() = RunArchive.sha(manifest().toString().toByteArray())
        fun summary(): JSONObject {
            val s = archive.run.decode().snapshot
            return JSONObject().put("state", "FINISHED").put("startedUtcMs", s.startedUtcMs).put("endedUtcMs", s.endedUtcMs)
                .put("activeDurationMs", s.activeDurationMs).put("distanceMeters", s.distanceMeters).put("mode", s.settings.mode.name).put("units", s.settings.units.name)
        }
        fun receipt() = JSONObject().put("runId", archive.run.id).put("operationId", operation).put("manifestHash", hash).put("completedAt", 10L)
        override suspend fun request(session: AccountSession, path: String, method: String, body: ByteArray?, contentType: String, match: String?): JSONObject {
            owners.add(session.ownerId); hook(path)
            if (path.startsWith("/api/run-deletions")) return JSONObject().put("deleted", JSONArray(deletes)).put("next", JSONObject.NULL)
            if (path == "/api/runs") return JSONObject().put("runs", JSONArray().put(receipt().put("summary", summary()))).put("next", JSONObject.NULL)
            if (path.startsWith("/api/runs?")) return JSONObject().put("runs", JSONArray()).put("next", JSONObject.NULL)
            detailCalls++
            if (gone) throw SyncHttpException(404, "not_found")
            return receipt().put("manifest", manifest()).put("manifestJson", manifest().toString())
        }
        override suspend fun download(session: AccountSession, path: String): ByteArray {
            owners.add(session.ownerId); binaryCalls++; hook(path)
            return chunks[path.substringAfterLast('/').toInt()].let { if (corrupt) it + 1.toByte() else it }
        }
    }
    @Test fun restorePreservesAllTablesWithoutReuploadAndIsIdempotent() = runBlocking {
        val archive = fixture(); val api = FakeApi(archive)
        engine(api).runOnce(); engine(api).runOnce()
        val restored = dao.archive(archive.run.id)!!
        assertEquals(archive.run, restored.run); assertEquals(archive.splits, restored.splits)
        assertEquals(archive.intervals, restored.intervals); assertEquals(archive.segments, restored.segments)
        assertEquals(archive.route.map { it.copy(id = 0) }, restored.route.map { it.copy(id = 0) })
        assertEquals(archive.measurements.map { it.copy(id = 0) }, restored.measurements.map { it.copy(id = 0) })
        assertEquals("SYNCED", dao.operation(archive.run.id)!!.status); assertTrue(dao.pending("alice").isEmpty())
        val calls = api.binaryCalls
        dao.putPull(PullState("alice")); engine(api).runOnce(); engine(api).runOnce()
        assertEquals(calls, api.binaryCalls)
    }
    @Test fun interruptedDownloadResumesVerifiedChunksAcrossEngineRecreation() = runBlocking {
        val archive = fixture(); val api = FakeApi(archive)
        engine(api).runOnce()
        api.hook = { if (it.endsWith("chunks/1")) throw IOException("offline") }
        engine(api).runOnce(); assertNull(dao.get(archive.run.id)); assertEquals(2, api.binaryCalls)
        api.hook = { }; now += 1000000
        engine(api).runOnce()
        assertEquals(api.chunks.size + 1, api.binaryCalls); assertNotNull(dao.get(archive.run.id))
        assertTrue(directory.walkTopDown().none { it.isFile })
    }
    @Test fun corruptedChunkAndWrongArchiveOwnerNeverCommit() = runBlocking {
        val archive = fixture(); val api = FakeApi(archive).apply { corrupt = true }
        engine(api).runOnce(); engine(api).runOnce()
        assertEquals("BLOCKED", dao.pull("alice")!!.status); assertNull(dao.get(archive.run.id))
        dao.retryPull("alice")
        api.corrupt = false
        api.chunks = archive.copy(run = archive.run.copy(cloudOwnerId = "bob")).encode().toList().chunked(600).map { it.toByteArray() }
        engine(api).runOnce(); assertNull(dao.get(archive.run.id)); assertEquals("BLOCKED", dao.pull("alice")!!.status)
    }
    @Test fun accountSwitchDuringDownloadStopsRequestsAndCommit() = runBlocking {
        val archive = fixture(); val api = FakeApi(archive)
        engine(api).runOnce()
        api.hook = { if (it.endsWith("chunks/0")) account = account!!.copy(ownerId = "bob") }
        engine(api).runOnce(); assertNull(dao.get(archive.run.id)); assertEquals(setOf("alice"), api.owners.toSet())
        account = account!!.copy(ownerId = "alice"); api.hook = { }
        engine(api).runOnce(); assertNotNull(dao.get(archive.run.id))
    }
    @Test fun deletionDuringDownloadAndLocalTombstonePreventResurrection() = runBlocking {
        val archive = fixture(); val api = FakeApi(archive)
        engine(api).runOnce()
        api.hook = { if (it.endsWith("chunks/0")) dao.remoteDeleted(archive.run.id, "alice") }
        engine(api).runOnce(); assertNull(dao.get(archive.run.id)); assertEquals("DELETED", dao.operation(archive.run.id)!!.status)
        dao.putPull(PullState("alice")); api.hook = { }; val calls = api.binaryCalls
        engine(api).runOnce(); engine(api).runOnce(); assertEquals(calls, api.binaryCalls)
    }
    @Test fun remoteDeletionDuringFinalReadNeverCommitsAndFeedDeletesOnlyMatchingOwner() = runBlocking {
        val archive = fixture(); val api = FakeApi(archive)
        engine(api).runOnce()
        api.hook = { if (it.endsWith("chunks/${api.chunks.lastIndex}")) api.gone = true }
        engine(api).runOnce(); assertNull(dao.get(archive.run.id))
        val alice = run(); val bob = run("bob")
        dao.putPull(PullState("alice")); api.deletes = listOf(alice, bob).sorted(); api.hook = { }
        engine(api).runOnce(); assertNull(dao.get(alice)); assertNotNull(dao.get(bob))
        assertEquals("bob", dao.operation(bob)!!.ownerId)
    }
    @Test fun restoreWriteFailureRollsBackAndAutogeneratedIdsNeverCollide() = runBlocking {
        val archive = fixture(); val local = run(null)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER refuse_restore BEFORE INSERT ON run_sync BEGIN SELECT RAISE(ABORT, 'test'); END")
        try { dao.restore(archive, "alice", UUID.randomUUID().toString()); fail("must rollback") } catch (_: android.database.sqlite.SQLiteException) { }
        assertNull(dao.get(archive.run.id)); assertTrue(dao.route(archive.run.id).isEmpty()); assertNotNull(dao.get(local))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_restore")
        assertTrue(dao.restore(archive, "alice", UUID.randomUUID().toString()))
        assertNotEquals(dao.route(local).first().id, dao.route(archive.run.id).first().id)
    }
    @Test fun expiredSessionAndBoundedRetriesPreserveCursorAndLocalRuns() = runBlocking {
        val archive = fixture(); val api = FakeApi(archive)
        account = account!!.copy(expiresAtMs = 1); engine(api).runOnce()
        assertTrue(api.owners.isEmpty()); assertEquals("AUTH", dao.pull("alice")!!.status)
        account = account!!.copy(expiresAtMs = Long.MAX_VALUE); dao.retryPull("alice")
        api.hook = { throw IOException("offline") }
        repeat(8) { engine(api).runOnce(); now += 100000000 }
        assertEquals("BLOCKED", dao.pull("alice")!!.status); val calls = api.owners.size
        engine(api).runOnce(); assertEquals(calls, api.owners.size)
        dao.retryPull("bob"); assertEquals("BLOCKED", dao.pull("alice")!!.status)
    }
}
