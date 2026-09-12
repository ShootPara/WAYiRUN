package com.example.runningapp.storage

import android.content.Context
import androidx.room.*
import com.example.runningapp.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "runs", indices = [Index(value = ["activeSlot"], unique = true)])
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
)

@Entity(tableName = "route_points", foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("runId")])
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String, val segmentId: Long, val monotonicMs: Long,
    val latitude: Double, val longitude: Double, val accuracyMeters: Float,
)

@Entity(tableName = "measurements", foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)], indices = [Index("runId")])
data class StoredMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String, val segmentId: Long, val monotonicMs: Long, val source: String,
    val deltaMeters: Double, val totalMeters: Double, val activeMs: Long, val reading: String,
)

@Entity(tableName = "splits", primaryKeys = ["runId", "number"], foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)])
data class StoredSplit(val runId: String, val number: Int, val meters: Double, val durationMs: Long, val partial: Boolean)

@Entity(tableName = "active_intervals", primaryKeys = ["runId", "number"], foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)])
data class StoredActiveInterval(val runId: String, val number: Int, val value: String)

@Entity(tableName = "source_segments", primaryKeys = ["runId", "number"], foreignKeys = [ForeignKey(
    entity = StoredRun::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE,
)])
data class StoredSegment(val runId: String, val number: Long, val value: String)

@Dao
abstract class RunDao {
    @Query("SELECT * FROM runs WHERE activeSlot = 1 LIMIT 1") abstract suspend fun active(): StoredRun?
    @Query("SELECT * FROM runs WHERE id = :id") abstract suspend fun get(id: String): StoredRun?
    @Query("SELECT * FROM runs ORDER BY updatedUtcMs DESC LIMIT 1") abstract fun latest(): Flow<StoredRun?>
    @Query("SELECT * FROM route_points WHERE runId = :id ORDER BY id") abstract suspend fun route(id: String): List<RoutePoint>
    @Query("SELECT * FROM splits WHERE runId = :id ORDER BY number") abstract suspend fun splits(id: String): List<StoredSplit>
    @Query("SELECT * FROM measurements WHERE runId = :id ORDER BY id") abstract suspend fun measurements(id: String): List<StoredMeasurement>
    @Upsert abstract suspend fun putRun(run: StoredRun)
    @Insert abstract suspend fun putRoute(point: RoutePoint)
    @Insert abstract suspend fun putMeasurement(value: StoredMeasurement)
    @Upsert abstract suspend fun putSplits(values: List<StoredSplit>)
    @Upsert abstract suspend fun putIntervals(values: List<StoredActiveInterval>)
    @Upsert abstract suspend fun putSegments(values: List<StoredSegment>)

    @Transaction
    open suspend fun save(
        run: StoredRun, splits: List<StoredSplit>, intervals: List<StoredActiveInterval>,
        segments: List<StoredSegment>, route: RoutePoint?, measurement: StoredMeasurement?,
    ) {
        val old = get(run.id)
        check(old?.state != RunState.FINISHED.name) { "A completed run is immutable" }
        check(old == null || old.ownerId == run.ownerId)
        putRun(run)
        putSplits(splits)
        putIntervals(intervals)
        putSegments(segments)
        if (route != null) putRoute(route)
        if (measurement != null) putMeasurement(measurement)
    }
}

@Database(
    entities = [StoredRun::class, RoutePoint::class, StoredMeasurement::class, StoredSplit::class,
        StoredActiveInterval::class, StoredSegment::class], version = 1, exportSchema = true,
)
abstract class RunDatabase : RoomDatabase() {
    abstract fun runs(): RunDao
    companion object {
        @Volatile private var instance: RunDatabase? = null
        fun get(context: Context): RunDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RunDatabase::class.java, "wayirun-local.db")
                .build().also { instance = it }
        }
    }
}

/** Local debug records never represent a Google account and are never uploaded. */
class RunRepository(private val dao: RunDao) {
    val latest = dao.latest()
    suspend fun active() = dao.active()
    suspend fun save(
        checkpoint: RunCheckpoint, ownerId: String, zoneId: String, offset: Int,
        interrupted: Boolean, route: RoutePoint? = null, measurement: StoredMeasurement? = null,
    ) {
        val s = checkpoint.snapshot
        val partial = s.partialSplit()
        val splits = s.splits.map { StoredSplit(s.runId, it.number, s.settings.units.metersPerUnit, it.durationMs, false) } +
            listOfNotNull(partial?.let { StoredSplit(s.runId, s.splits.size + 1, it.distanceMeters, it.durationMs, true) })
        dao.save(
            StoredRun(s.runId, ownerId, s.state.name, if (s.state == RunState.FINISHED) null else 1,
                Json.encodeToString(checkpoint), zoneId, offset, checkpoint.lastUtcMs, interrupted),
            splits, s.activeIntervals.map { StoredActiveInterval(s.runId, it.number, Json.encodeToString(it)) },
            s.segments.map { StoredSegment(s.runId, it.id, Json.encodeToString(it)) }, route, measurement,
        )
    }
}

fun StoredRun.decode(): RunCheckpoint = Json.decodeFromString(checkpoint)
