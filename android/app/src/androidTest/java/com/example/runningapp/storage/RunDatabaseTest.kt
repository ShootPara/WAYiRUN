package com.example.runningapp.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.runningapp.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunDatabaseTest {
    @Test fun photoQueueFollowsOwnerAndNeverAcknowledgesAReplacementAsSynced() = runBlocking {
        val r=run("photo");time=1000;r.finish()
        repo.save(r.checkpoint(),"test-owner","UTC",0,false,cloudOwnerId="alice")
        val old=RunPhoto("photo","first",byteArrayOf(1,2,3),"{}",true)
        db.runs().putPhoto(old)
        assertEquals(1,db.runs().pendingPhotos("alice").size)
        assertTrue(db.runs().pendingPhotos("bob").isEmpty())
        db.runs().putPhoto(old.copy(revision="second",public=false))
        db.runs().photoSynced("photo","first","https://old")
        assertFalse(db.runs().photo("photo")!!.synced)
        db.runs().photoSynced("photo","second",null)
        assertTrue(db.runs().photo("photo")!!.synced)
        repo.discard("photo","test-owner")
        assertNull(db.runs().photo("photo"))
    }

    @Test fun achievementsAreDurableOwnerScopedAndRecomputedOnDeletion() = runBlocking {
        val r=run("award");r.selectSource(DistanceSource.STEPS)
        val segment=r.snapshot().currentSegmentId!!
        r.record(RunMeasurement.Steps(segment,0,100))
        time=10000;r.record(RunMeasurement.Steps(segment,time,1100));r.finish()
        repo.save(r.checkpoint(),"test-owner","UTC",0,false,cloudOwnerId="alice")
        assertTrue(db.runs().achievementCache("alice")!!.awards.contains("Kicking It Off"))
        assertNull(db.runs().achievementCache("bob"))
        assertEquals(db.runs().rebuildAchievements("alice","test-owner"),db.runs().rebuildAchievements("alice","test-owner"))
        assertTrue(repo.discard("award","test-owner"))
        assertEquals("[]",db.runs().achievementCache("alice")!!.awards)
    }
    private lateinit var db: RunDatabase
    private lateinit var repo: RunRepository
    private var time = 0L
    private val clock = RunClock { RunTime(time, 1_700_000_000_000L + time) }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RunDatabase::class.java).build()
        repo = RunRepository(db.runs())
    }
    @After fun close() { db.close() }
    private fun run(id: String = "one") = RunController(id,
        RunSettings(RunMode.INDOOR, RunUnits.KILOMETERS, 0, RunGoal.None, 1.0), clock).also { it.start() }
    private suspend fun save(run: RunController) = repo.save(run.checkpoint(), "test-owner", "UTC", 0, false)

    @Test fun finishAndSummarySurviveReloadWithPartialSplit() = runBlocking {
        val run = run()
        save(run)
        run.selectSource(DistanceSource.STEPS)
        val id = run.snapshot().currentSegmentId!!
        run.record(RunMeasurement.Steps(id, 0, 1_000))
        time = 750_000
        run.record(RunMeasurement.Steps(id, time, 2_250)); run.finish(); save(run)
        val loaded = db.runs().get("one")!!.decode()
        assertEquals(run.snapshot(), loaded.snapshot)
        assertNull(repo.active())
        val splits = db.runs().splits("one")
        assertEquals(2, splits.size)
        assertTrue(splits.last().partial)
        assertEquals(250.0, splits.last().meters, 0.0)
        assertTrue(db.runs().route("one").isEmpty())
    }

    @Test fun failedFinishTransactionLeavesOriginalRunAndSplitsIntact() = runBlocking {
        val run = run(); save(run)
        time = 1_000; run.finish()
        try {
            repo.save(run.checkpoint(), "test-owner", "UTC", 0, false,
                route = RoutePoint(runId = "missing-parent", segmentId = 1, monotonicMs = 1_000, latitude = 0.0, longitude = 0.0, accuracyMeters = 1f))
            fail("Expected foreign-key failure")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(RunState.RUNNING.name, db.runs().get("one")!!.state)
        assertNotNull(repo.active())
        assertTrue(db.runs().splits("one").isEmpty())
    }

    @Test fun onlyOneActiveRunAndFinishedRunCannotBeReopened() = runBlocking {
        val first = run(); save(first)
        try { save(run("two")); fail("Expected single active run constraint") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertNull(db.runs().get("two"))
        first.finish(); save(first)
        try { save(first); fail("Expected terminal guard") } catch (_: IllegalStateException) { }
        save(run("two"))
        assertEquals("two", repo.active()!!.id)
    }

    @Test fun recoveryPersistsPausedCheckpointWithoutGap() = runBlocking {
        val run = run(); time = 1_000; run.tick(); save(run)
        time = 500_000
        val recovered = RunController.recover(repo.active()!!.decode(), clock)
        repo.save(recovered.checkpoint(), "test-owner", "UTC", 0, true)
        val stored = repo.active()!!
        assertTrue(stored.interrupted)
        assertEquals(RunState.PAUSED, stored.decode().snapshot.state)
        assertEquals(1_000L, stored.decode().snapshot.activeDurationMs)
    }

    @Test fun discardRemovesEveryOwnedTableAndPreservesOtherRuns() = runBlocking {
        val first = run(); first.selectSource(DistanceSource.STEPS); first.finish(); save(first)
        db.runs().putRoute(RoutePoint(runId = "one", segmentId = 1, monotonicMs = 0, latitude = 0.0, longitude = 0.0, accuracyMeters = 1f))
        db.runs().putMeasurement(StoredMeasurement(runId = "one", segmentId = 1, monotonicMs = 0, source = "STEPS", deltaMeters = 1.0, totalMeters = 1.0, activeMs = 0, reading = "test"))
        db.runs().putSplits(listOf(StoredSplit("one", 1, 1.0, 1000, true)))
        val other = run("two"); other.finish(); save(other)
        val tables = listOf("active_intervals", "source_segments", "route_points", "measurements", "splits")
        fun rows(table: String): Long = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table WHERE runId = ?", arrayOf<Any>("one")).use { it.moveToFirst(); it.getLong(0) }
        tables.forEach { assertTrue("Fixture has $it", rows(it) > 0) }
        assertFalse(repo.discard("one", "different-owner"))
        assertTrue(repo.discard("one", "test-owner"))
        assertTrue(repo.discard("one", "test-owner"))
        assertNull(db.runs().get("one")); assertNotNull(db.runs().get("two"))
        tables.forEach { assertEquals("No remaining $it", 0L, rows(it)) }
    }

    @Test fun discardRefusesActiveRunAndRollsBackStorageFailure() = runBlocking {
        val first = run(); save(first)
        assertFalse(repo.discard("one", "test-owner"))
        first.finish(); save(first)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_discard BEFORE DELETE ON runs BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repo.discard("one", "test-owner"); fail("Expected deletion failure") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertNotNull(db.runs().get("one"))
        assertEquals(RunState.FINISHED.name, db.runs().get("one")!!.state)
    }
}
