package com.example.runningapp.storage

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SyncMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RunDatabase::class.java)
    @Test fun versionOneMigrationPreservesEveryTableAndNeverClaimsLegacyRuns() {
        val name = "sync-migration-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO runs VALUES ('old', 'local-owner', 'FINISHED', NULL, 'checkpoint-exact', 'UTC', 0, 1000, 0)")
            execSQL("INSERT INTO route_points VALUES (1, 'old', 1, 100, 45, -75, 2)")
            execSQL("INSERT INTO measurements VALUES (1, 'old', 1, 100, 'GPS', 1, 1, 100, 'reading-exact')")
            execSQL("INSERT INTO splits VALUES ('old', 1, 1, 100, 1)")
            execSQL("INSERT INTO active_intervals VALUES ('old', 1, 'interval-exact')")
            execSQL("INSERT INTO source_segments VALUES ('old', 1, 'segment-exact')")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, RunDatabase.MIGRATION_1_2).apply {
            query("SELECT ownerId, checkpoint, cloudOwnerId FROM runs").use {
                assertTrue(it.moveToFirst()); assertEquals("local-owner", it.getString(0)); assertEquals("checkpoint-exact", it.getString(1)); assertTrue(it.isNull(2))
            }
            for (table in listOf("route_points", "measurements", "splits", "active_intervals", "source_segments")) {
                query("SELECT count(*) FROM $table").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            }
            query("SELECT count(*) FROM run_sync").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            close()
        }
    }
    @Test fun versionTwoMigrationPreservesQueuesAndAddsEmptyAccountPullState() {
        val name = "sync2-migration-test"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO run_sync VALUES ('run', 'alice', 'op', 'DELETE', 'PENDING', 2, 123, 'offline')")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, RunDatabase.MIGRATION_2_3).apply {
            query("SELECT action, attempts, nextAttemptMs FROM run_sync").use {
                assertTrue(it.moveToFirst()); assertEquals("DELETE", it.getString(0)); assertEquals(2, it.getInt(1)); assertEquals(123, it.getInt(2))
            }
            query("SELECT count(*) FROM sync_pull").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            close()
        }
    }

}
