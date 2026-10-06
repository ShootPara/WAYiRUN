package com.example.runningapp.storage

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SyncMigrationTest {
    @Test fun photoRetryMigrationPreservesBytesRevisionWeatherAndPendingState() {
        val name="photo-retry-migration-test"
        helper.createDatabase(name,8).apply {
            execSQL("INSERT INTO runs VALUES ('run','local','FINISHED',NULL,'checkpoint','UTC',0,1000,0,'alice')")
            execSQL("INSERT INTO run_photos VALUES ('run','revision',X'FFD8FFD9','{}',0,0,NULL,'saved-weather')")
            close()
        }
        helper.runMigrationsAndValidate(name,9,true,RunDatabase.MIGRATION_8_9).apply {
            query("SELECT hex(jpeg),revision,synced,weather,syncError,lastAttemptMs FROM run_photos").use {
                assertTrue(it.moveToFirst());assertEquals("FFD8FFD9",it.getString(0));assertEquals("revision",it.getString(1))
                assertEquals(0,it.getInt(2));assertEquals("saved-weather",it.getString(3));assertTrue(it.isNull(4));assertEquals(0L,it.getLong(5))
            }
            close()
        }
    }
    @Test fun weatherMigrationPreservesLegacyPhotoWithoutRetroactiveWeather() {
        val name="weather-migration-test"
        helper.createDatabase(name,7).apply {
            execSQL("INSERT INTO runs VALUES ('run','local','FINISHED',NULL,'checkpoint','UTC',0,1000,0,'alice')")
            execSQL("INSERT INTO run_photos VALUES ('run','revision',X'FFD8FFD9','{}',0,1,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name,8,true,RunDatabase.MIGRATION_7_8).apply {
            query("SELECT hex(jpeg),revision,synced,weather FROM run_photos").use {
                assertTrue(it.moveToFirst());assertEquals("FFD8FFD9",it.getString(0));assertEquals("revision",it.getString(1))
                assertEquals(1,it.getInt(2));assertTrue(it.isNull(3))
            }
            execSQL("PRAGMA foreign_keys=ON")
            execSQL("DELETE FROM runs WHERE id='run'")
            query("SELECT COUNT(*) FROM run_photos").use {it.moveToFirst();assertEquals(0,it.getInt(0))}
            close()
        }
    }
    @Test fun publicationMigrationKeepsPhotoBytesAndRetiresLegacyKeepVisibility() {
        val name="publication-migration-test"
        helper.createDatabase(name,6).apply {
            execSQL("INSERT INTO runs VALUES ('run','local','FINISHED',NULL,'checkpoint','UTC',0,1000,0,'alice')")
            execSQL("INSERT INTO run_photos VALUES ('run','revision',X'FFD8FFD9','{}',1,0,'https://old-link')")
            execSQL("INSERT INTO run_sync VALUES ('run','alice','operation','UPLOAD','PENDING',1,10,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name,7,true,RunDatabase.MIGRATION_6_7).apply {
            query("SELECT hex(jpeg),public,publicUrl,synced,revision FROM run_photos").use {
                assertTrue(it.moveToFirst());assertEquals("FFD8FFD9",it.getString(0));assertEquals(0,it.getInt(1))
                assertTrue(it.isNull(2));assertEquals(0,it.getInt(3));assertEquals("revision",it.getString(4))
            }
            query("SELECT COUNT(*) FROM run_publications").use {it.moveToFirst();assertEquals(0,it.getInt(0))}
            query("SELECT ownerId,status FROM run_sync").use {it.moveToFirst();assertEquals("alice",it.getString(0));assertEquals("PENDING",it.getString(1))}
            close()
        }
    }
    @Test fun healthMigrationPreservesPhotosAndQueues() {
        val name="health-migration-test"
        helper.createDatabase(name,5).apply {
            execSQL("INSERT INTO runs VALUES ('run','local','FINISHED',NULL,'checkpoint','UTC',0,1000,0,'alice')")
            execSQL("INSERT INTO run_photos VALUES ('run','revision',X'FFD8FFD9','{}',0,0,NULL)")
            execSQL("INSERT INTO run_sync VALUES ('run','alice','operation','UPLOAD','PENDING',1,10,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name,6,true,RunDatabase.MIGRATION_5_6).apply {
            for(table in listOf("runs","run_photos","run_sync"))query("SELECT COUNT(*) FROM $table").use {it.moveToFirst();assertEquals(1,it.getInt(0))}
            query("SELECT COUNT(*) FROM health_exports").use {it.moveToFirst();assertEquals(0,it.getInt(0))};close()
        }
    }

    @Test fun photoMigrationPreservesRunsAndCascadesPhotoDeletion() {
        val name="photo-migration-test"
        helper.createDatabase(name,4).apply {
            execSQL("INSERT INTO runs VALUES ('run', 'local-owner', 'FINISHED', NULL, 'checkpoint', 'UTC', 0, 1000, 0, 'alice')")
            execSQL("INSERT INTO achievement_cache VALUES ('alice','[]')")
            close()
        }
        helper.runMigrationsAndValidate(name,5,true,RunDatabase.MIGRATION_4_5).apply {
            query("SELECT checkpoint FROM runs").use {it.moveToFirst();assertEquals("checkpoint",it.getString(0))}
            execSQL("PRAGMA foreign_keys=ON")
            execSQL("INSERT INTO run_photos VALUES ('run','revision',X'FFD8FFD9','{}',0,0,NULL)")
            execSQL("DELETE FROM runs WHERE id='run'")
            query("SELECT COUNT(*) FROM run_photos").use {it.moveToFirst();assertEquals(0,it.getInt(0))}
            close()
        }
    }

    @Test fun achievementMigrationPreservesRunsAndQueues() {
        val name="achievement-migration-test"
        helper.createDatabase(name,3).apply {
            execSQL("INSERT INTO run_sync VALUES ('run', 'alice', 'op', 'DELETE', 'PENDING', 2, 123, 'offline')")
            close()
        }
        helper.runMigrationsAndValidate(name,4,true,RunDatabase.MIGRATION_3_4).apply {
            query("SELECT COUNT(*) FROM run_sync").use { it.moveToFirst();assertEquals(1,it.getInt(0)) }
            query("SELECT COUNT(*) FROM achievement_cache").use { it.moveToFirst();assertEquals(0,it.getInt(0)) }
            close()
        }
    }
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
