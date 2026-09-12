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
}
