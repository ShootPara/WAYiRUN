package com.example.runningapp.storage

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.Serializable
import java.util.UUID
import com.example.runningapp.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "runs", indices = [Index(value = ["activeSlot"], unique = true)])
@Serializable
data class StoredRun(
    @PrimaryKey val id: String,
    val ownerId: String,
    val state: String,
    val activeSlot: Int?,
    val checkpoint: String,
    val zoneId: String,
    val startOffsetSeconds: Int,
    val updatedUtcMs: Long,
    val interrupted: Boolean,
    val cloudOwnerId: String? = null,
)

@Entity(tableName = "route_points", foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("runId")])
@Serializable
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String, val segmentId: Long, val monotonicMs: Long,
    val latitude: Double, val longitude: Double, val accuracyMeters: Float,
)

@Entity(tableName = "measurements", foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("runId")])
@Serializable
data class StoredMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String, val segmentId: Long, val monotonicMs: Long, val source: String,
    val deltaMeters: Double, val totalMeters: Double, val activeMs: Long, val reading: String,
)

@Entity(tableName = "splits", primaryKeys = ["runId", "number"], foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)])
@Serializable
data class StoredSplit(val runId: String, val number: Int, val meters: Double, val durationMs: Long, val partial: Boolean)

@Entity(tableName = "active_intervals", primaryKeys = ["runId", "number"], foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)])
@Serializable
data class StoredActiveInterval(val runId: String, val number: Int, val value: String)

@Entity(tableName = "source_segments", primaryKeys = ["runId", "number"], foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)])
@Serializable
data class StoredSegment(val runId: String, val number: Long, val value: String)

@Entity(tableName = "run_sync", indices = [Index(value = ["ownerId", "status"])])
data class SyncOperation(
    @PrimaryKey val runId: String, val ownerId: String, val operationId: String,
    val action: String = "UPLOAD", val status: String = "PENDING", val attempts: Int = 0,
    val nextAttemptMs: Long = 0, val error: String? = null,
)

@Entity(tableName = "sync_pull")
data class PullState(@PrimaryKey val ownerId: String, val phase: String = "DELETIONS", val cursor: String? = null,
    val status: String = "PENDING", val attempts: Int = 0, val nextAttemptMs: Long = 0, val error: String? = null)

@Entity(tableName = "achievement_cache")
data class AchievementCache(@PrimaryKey val owner: String,val awards: String)

@Entity(tableName = "run_photos", foreignKeys = [ForeignKey(entity = StoredRun::class,
    parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE)])
data class RunPhoto(@PrimaryKey val runId: String, val revision: String, val jpeg: ByteArray,
    val options: String, val public: Boolean, val synced: Boolean = false, val publicUrl: String? = null)

@Entity(tableName = "health_exports")
data class HealthExport(@PrimaryKey val runId: String, val state: String = "PENDING", val error: String? = null)

@Dao
abstract class RunDao {
    @Query("INSERT OR IGNORE INTO health_exports(runId,state,error) SELECT id,'PENDING',NULL FROM runs WHERE state='FINISHED' AND (cloudOwnerId IS NULL OR cloudOwnerId=:owner)")
    abstract suspend fun seedHealth(owner: String?)
    @Query("SELECT h.* FROM health_exports h LEFT JOIN runs r ON r.id=h.runId WHERE h.state='DELETE' OR (h.state='PENDING' AND r.state='FINISHED' AND (r.cloudOwnerId IS NULL OR r.cloudOwnerId=:owner)) ORDER BY CASE h.state WHEN 'DELETE' THEN 0 ELSE 1 END, h.runId LIMIT 20")
    abstract suspend fun pendingHealth(owner: String?): List<HealthExport>
    @Query("UPDATE health_exports SET state='PENDING',error=NULL WHERE state='DONE' AND EXISTS(SELECT 1 FROM runs r WHERE r.id=health_exports.runId AND (r.cloudOwnerId IS NULL OR r.cloudOwnerId=:owner))")
    abstract suspend fun retryHealth(owner: String?)
    @Query("SELECT * FROM health_exports WHERE runId=:id") abstract suspend fun health(id: String): HealthExport?
    @Query("UPDATE health_exports SET state=:state,error=:error WHERE runId=:id AND state=:expected")
    abstract suspend fun markHealth(id: String, expected: String, state: String, error: String? = null): Int
    @Query("UPDATE health_exports SET state='DELETE',error=NULL WHERE runId=:id AND state<>'DELETED'")
    abstract suspend fun deleteHealth(id: String)
    @Query("SELECT * FROM health_exports") abstract suspend fun healthStatus(): List<HealthExport>

    @Query("SELECT * FROM run_photos WHERE runId=:id") abstract suspend fun photo(id: String): RunPhoto?
    @Upsert abstract suspend fun putPhoto(photo: RunPhoto)
    @Query("SELECT p.* FROM run_photos p JOIN runs r ON r.id=p.runId WHERE r.cloudOwnerId=:owner AND p.synced=0 LIMIT 1")
    abstract suspend fun pendingPhotos(owner: String): List<RunPhoto>
    @Query("UPDATE run_photos SET synced=1, publicUrl=:url WHERE runId=:id AND revision=:revision")
    abstract suspend fun photoSynced(id: String, revision: String, url: String?)

    private val achievementInputs = mutableMapOf<String,Pair<String,AchievementRun>>()
    @Query("SELECT * FROM runs WHERE (cloudOwnerId = :cloud OR (:cloud IS NULL AND cloudOwnerId IS NULL AND ownerId = :local)) AND state IN ('FINISHED','RUNNING','PAUSED')")
    abstract suspend fun achievementRuns(cloud: String?, local: String): List<StoredRun>
    @Query("SELECT * FROM achievement_cache WHERE owner = :owner") abstract suspend fun achievementCache(owner: String): AchievementCache?
    @Upsert abstract suspend fun putAchievements(value: AchievementCache)
    @Transaction
    open suspend fun rebuildAchievements(cloud: String?, local: String): List<Achievement> {
        val rows=achievementRuns(cloud,local)
        achievementInputs.keys.retainAll(rows.map { it.id }.toSet())
        val live=rows.any { it.state!="FINISHED" }
        val inputs=rows.map { row ->
            val cached=achievementInputs[row.id]
            val input=if(cached?.first==row.checkpoint) cached.second else achievementInput(row.decode().snapshot,row.zoneId,
                measurements(row.id).map { AchievementSample(it.deltaMeters,it.totalMeters,it.activeMs,it.segmentId) }).also { achievementInputs[row.id]=row.checkpoint to it }
            if(live) input.copy(points=emptyList()) else input
        }
        val retainedPerformance = if(live) achievementCache(cloud?:"local:$local")?.let { cache ->
            Json.decodeFromString<List<Achievement>>(cache.awards).filter { a ->
                (a.id.startsWith("pr-") || a.id in listOf("negative-split","progression")) && rows.any { it.id==a.runId && it.state=="FINISHED" }
            }
        }.orEmpty() else emptyList()
        val earned=Achievements.evaluate(inputs)+retainedPerformance
        putAchievements(AchievementCache(cloud?:"local:$local",Json.encodeToString(earned)))
        return earned
    }
    @Query("SELECT * FROM sync_pull WHERE ownerId = :owner") abstract suspend fun pull(owner: String): PullState?
    @Query("SELECT * FROM sync_pull WHERE ownerId = :owner") abstract fun pullStatus(owner: String): Flow<PullState?>
    @Upsert abstract suspend fun putPull(state: PullState)
    @Query("UPDATE sync_pull SET status = 'PENDING', attempts = 0, nextAttemptMs = 0, error = NULL WHERE ownerId = :owner")
    abstract suspend fun retryPull(owner: String)
    @Transaction
    open suspend fun restore(archive: RunArchive, owner: String, operationId: String): Boolean {
        archive.validate()
        require(archive.run.cloudOwnerId == owner)
        val id = archive.run.id
        val op = operation(id)
        if (op?.action == "DELETE") return false
        val old = get(id)
        if (old != null) {
            require(old.cloudOwnerId == owner) { "Local identity conflict" }
            return false // Never replace an existing or active local run.
        }
        require(op == null || op.ownerId == owner)
        putRun(archive.run)
        // Device-local autogenerated row IDs must never collide with another restored run.
        archive.route.forEach { putRoute(it.copy(id = 0)) }
        archive.measurements.forEach { putMeasurement(it.copy(id = 0)) }
        putSplits(archive.splits); putIntervals(archive.intervals); putSegments(archive.segments)
        putOperation(SyncOperation(id, owner, operationId, status = "SYNCED"))
        rebuildAchievements(owner,archive.run.ownerId)
        return true
    }
    @Query("SELECT * FROM runs WHERE activeSlot = 1 LIMIT 1") abstract suspend fun active(): StoredRun?
    @Query("SELECT * FROM runs WHERE id = :id") abstract suspend fun get(id: String): StoredRun?
    @Query("SELECT * FROM runs ORDER BY updatedUtcMs DESC LIMIT 1") abstract fun latest(): Flow<StoredRun?>
    @Query("SELECT * FROM runs WHERE cloudOwnerId IS NULL OR cloudOwnerId = :owner ORDER BY updatedUtcMs DESC LIMIT 1")
    abstract suspend fun latestVisible(owner: String?): StoredRun?
    @Query("SELECT * FROM active_intervals WHERE runId = :id ORDER BY number") abstract suspend fun intervals(id: String): List<StoredActiveInterval>
    @Query("SELECT * FROM source_segments WHERE runId = :id ORDER BY number") abstract suspend fun segments(id: String): List<StoredSegment>
    @Query("SELECT * FROM runs WHERE cloudOwnerId IS NULL AND state = 'FINISHED'") abstract suspend fun localCompleted(): List<StoredRun>
    @Query("SELECT COUNT(*) FROM runs WHERE cloudOwnerId IS NULL AND state = 'FINISHED'") abstract fun localCount(): Flow<Int>
    @Query("SELECT * FROM run_sync WHERE runId = :id") abstract suspend fun operation(id: String): SyncOperation?
    @Query("SELECT * FROM run_sync WHERE ownerId = :owner") abstract fun syncStatus(owner: String): Flow<List<SyncOperation>>
    @Query("SELECT * FROM run_sync WHERE ownerId = :owner AND status = 'PENDING' ORDER BY CASE action WHEN 'DELETE' THEN 0 ELSE 1 END, runId")
    abstract suspend fun pending(owner: String): List<SyncOperation>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun addOperation(value: SyncOperation)
    @Upsert abstract suspend fun putOperation(value: SyncOperation)
    @Query("UPDATE run_sync SET status = :status, attempts = :attempts, nextAttemptMs = :next, error = :error WHERE runId = :id AND ownerId = :owner AND action = :action AND status = 'PENDING'")
    abstract suspend fun mark(id: String, owner: String, action: String, status: String, attempts: Int, next: Long, error: String?): Int
    @Query("UPDATE run_sync SET status = 'PENDING', attempts = 0, nextAttemptMs = 0, error = NULL WHERE ownerId = :owner AND status IN ('AUTH', 'BLOCKED')")
    abstract suspend fun retry(owner: String)
    @Transaction
    open suspend fun importLocal(owner: String): Int {
        check(active() == null) { "Finish the active run before importing" }
        val rows = localCompleted()
        rows.forEach { row ->
            putRun(row.copy(cloudOwnerId = owner))
            addOperation(SyncOperation(row.id, owner, UUID.randomUUID().toString()))
        }
        rows.map { it.ownerId }.distinct().forEach { rebuildAchievements(null,it) }
        if(rows.isNotEmpty()) rebuildAchievements(owner,rows.first().ownerId)
        return rows.size
    }
    @Transaction
    open suspend fun archive(id: String): RunArchive? {
        val row = get(id) ?: return null
        return RunArchive(run = row, route = route(id), measurements = measurements(id), splits = splits(id),
            intervals = intervals(id), segments = segments(id))
    }
    @Query("SELECT * FROM route_points WHERE runId = :id ORDER BY id") abstract suspend fun route(id: String): List<RoutePoint>
    @Query("SELECT * FROM splits WHERE runId = :id ORDER BY number") abstract suspend fun splits(id: String): List<StoredSplit>
    @Query("SELECT * FROM measurements WHERE runId = :id ORDER BY id") abstract suspend fun measurements(id: String): List<StoredMeasurement>
    @Upsert abstract suspend fun putRun(run: StoredRun)
    @Insert abstract suspend fun putRoute(point: RoutePoint)
    @Insert abstract suspend fun putMeasurement(value: StoredMeasurement)
    @Upsert abstract suspend fun putSplits(values: List<StoredSplit>)
    @Upsert abstract suspend fun putIntervals(values: List<StoredActiveInterval>)
    @Upsert abstract suspend fun putSegments(values: List<StoredSegment>)
    @Query("DELETE FROM runs WHERE id = :id AND ownerId = :owner AND state = 'FINISHED'")
    protected abstract suspend fun deleteCompleted(id: String, owner: String): Int

    @Transaction
    open suspend fun remoteDeleted(id: String, cloudOwner: String) {
        val row = get(id)
        if (row != null && row.cloudOwnerId == cloudOwner && row.state == "FINISHED") { deleteHealth(id); deleteCompleted(id, row.ownerId) }
        val op = operation(id)
        if (op?.ownerId == cloudOwner) putOperation(op.copy(action = "DELETE", status = "DELETED", error = null))
        else if (op == null && (row == null || row.cloudOwnerId == cloudOwner))
            putOperation(SyncOperation(id, cloudOwner, UUID.randomUUID().toString(), action = "DELETE", status = "DELETED"))
        rebuildAchievements(cloudOwner,row?.ownerId?:"")
    }

    @Transaction
    open suspend fun discard(id: String, owner: String): Boolean {
        val stored = get(id) ?: return true
        if (stored.ownerId != owner || stored.state != RunState.FINISHED.name) return false
        stored.cloudOwnerId?.let { cloudOwner ->
            val previous = operation(id)
            putOperation(SyncOperation(id, cloudOwner, previous?.operationId ?: UUID.randomUUID().toString(), action = "DELETE"))
        }
        // Queue deletion and remove all measurements in the same local transaction.
        deleteHealth(id)
        val deleted=deleteCompleted(id, owner)==1
        rebuildAchievements(stored.cloudOwnerId,owner)
        return deleted
    }

    @Transaction
    open suspend fun save(
        run: StoredRun, splits: List<StoredSplit>, intervals: List<StoredActiveInterval>,
        segments: List<StoredSegment>, route: RoutePoint?, measurement: StoredMeasurement?,
    ) {
        val old = get(run.id)
        check(old?.state != RunState.FINISHED.name) { "A completed run is immutable" }
        check(old == null || (old.ownerId == run.ownerId && old.cloudOwnerId == run.cloudOwnerId))
        putRun(run)
        putSplits(splits)
        putIntervals(intervals)
        putSegments(segments)
        if (route != null) putRoute(route)
        if (measurement != null) putMeasurement(measurement)
        val prior=achievementInputs[run.id]
        if(run.state!="FINISHED" && old!=null && prior?.first==old.checkpoint) {
            val s=run.decode().snapshot
            val delta=achievementInput(s,run.zoneId,listOfNotNull(measurement?.let { AchievementSample(it.deltaMeters,it.totalMeters,it.activeMs,it.segmentId) }))
            val dates=prior.second.movement.toMutableMap()
            delta.movement.forEach { (date,meters) -> dates[date]=(dates[date]?:0.0)+meters }
            achievementInputs[run.id]=run.checkpoint to delta.copy(movement=dates,points=emptyList())
        }
        if (run.state == "FINISHED" && run.cloudOwnerId != null) {
            addOperation(SyncOperation(run.id, run.cloudOwnerId, UUID.randomUUID().toString()))
        }
        if(run.state=="FINISHED" || (measurement?.deltaMeters?:0.0)>0) rebuildAchievements(run.cloudOwnerId,run.ownerId)
    }
}

@Database(
    entities = [StoredRun::class, RoutePoint::class, StoredMeasurement::class, StoredSplit::class,
        StoredActiveInterval::class, StoredSegment::class, SyncOperation::class, PullState::class, AchievementCache::class, RunPhoto::class, HealthExport::class], version = 6, exportSchema = true,
)
abstract class RunDatabase : RoomDatabase() {
    abstract fun runs(): RunDao
    companion object {
        val MIGRATION_5_6 = object : Migration(5,6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS health_exports (runId TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, error TEXT)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4,5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS run_photos (runId TEXT NOT NULL PRIMARY KEY, revision TEXT NOT NULL, jpeg BLOB NOT NULL, options TEXT NOT NULL, public INTEGER NOT NULL, synced INTEGER NOT NULL, publicUrl TEXT, FOREIGN KEY(runId) REFERENCES runs(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3,4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS achievement_cache (owner TEXT NOT NULL PRIMARY KEY, awards TEXT NOT NULL)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE runs ADD COLUMN cloudOwnerId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS run_sync (runId TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, operationId TEXT NOT NULL, action TEXT NOT NULL, status TEXT NOT NULL, attempts INTEGER NOT NULL, nextAttemptMs INTEGER NOT NULL, error TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_run_sync_ownerId_status ON run_sync(ownerId, status)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_pull (ownerId TEXT NOT NULL PRIMARY KEY, phase TEXT NOT NULL, cursor TEXT, status TEXT NOT NULL, attempts INTEGER NOT NULL, nextAttemptMs INTEGER NOT NULL, error TEXT)")
            }
        }
        @Volatile private var instance: RunDatabase? = null
        fun get(context: Context): RunDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RunDatabase::class.java, "wayirun-local.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
        }
    }
}

/** Local acquisition owner stays immutable; explicit cloud ownership controls the sync queue. */
class RunRepository(private val dao: RunDao) {
    suspend fun discard(id: String, ownerId: String) = dao.discard(id, ownerId)
    val latest = dao.latest()
    suspend fun active() = dao.active()
    suspend fun save(
        checkpoint: RunCheckpoint, ownerId: String, zoneId: String, offset: Int,
        interrupted: Boolean, route: RoutePoint? = null, measurement: StoredMeasurement? = null, cloudOwnerId: String? = null,
    ) {
        val s = checkpoint.snapshot
        val partial = s.partialSplit()
        val splits = s.splits.map { StoredSplit(s.runId, it.number, s.settings.units.metersPerUnit, it.durationMs, false) } +
            listOfNotNull(partial?.let { StoredSplit(s.runId, s.splits.size + 1, it.distanceMeters, it.durationMs, true) })
        dao.save(
            StoredRun(s.runId, ownerId, s.state.name, if (s.state == RunState.FINISHED) null else 1,
                Json.encodeToString(checkpoint), zoneId, offset, checkpoint.lastUtcMs, interrupted, cloudOwnerId),
            splits, s.activeIntervals.map { StoredActiveInterval(s.runId, it.number, Json.encodeToString(it)) },
            s.segments.map { StoredSegment(s.runId, it.id, Json.encodeToString(it)) }, route, measurement,
        )
    }
}

fun StoredRun.decode(): RunCheckpoint = Json.decodeFromString(checkpoint)
