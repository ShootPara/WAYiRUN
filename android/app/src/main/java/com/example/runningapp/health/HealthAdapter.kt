package com.example.runningapp.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.units.Length
import com.example.runningapp.domain.HealthRun
import java.time.Instant
import java.time.ZoneId

interface HealthPort {
    suspend fun write(run: HealthRun, zone: String)
    suspend fun delete(runId: String)
}
class HealthAdapter(context: Context) : HealthPort {
    private val client=HealthConnectClient.getOrCreate(context)
    suspend fun permitted()=client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    override suspend fun write(run: HealthRun, zone: String) {client.insertRecords(records(run,zone))}
    internal fun records(run: HealthRun, zone: String): List<Record> {
        val start=Instant.ofEpochMilli(run.startMs);val end=Instant.ofEpochMilli(run.endMs)
        val rules=ZoneId.of(zone).rules
        return listOf<Record>(
            ExerciseSessionRecord(startTime=start,startZoneOffset=rules.getOffset(start),endTime=end,endZoneOffset=rules.getOffset(end),
                metadata=Metadata.activelyRecorded(device=Device(type=Device.TYPE_PHONE),clientRecordId=run.sessionId,clientRecordVersion=1),
                exerciseType=if(run.indoor) ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL else ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                title=if(run.indoor) "WAYiRUN indoor run" else "WAYiRUN outdoor run",
                notes="Active time: ${run.activeMs/1000} seconds. Distance: ${run.meters} meters.",
                segments=run.pauses.map { (a,b)->ExerciseSegment(Instant.ofEpochMilli(a),Instant.ofEpochMilli(b),ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE) }),
            DistanceRecord(startTime=start,startZoneOffset=rules.getOffset(start),endTime=end,endZoneOffset=rules.getOffset(end),
                distance=Length.meters(run.meters),metadata=Metadata.activelyRecorded(device=Device(type=Device.TYPE_PHONE),clientRecordId=run.distanceId,clientRecordVersion=1))
        )
    }
    override suspend fun delete(runId: String) {
        client.deleteRecords(ExerciseSessionRecord::class,emptyList(),listOf("wayirun:$runId:session"))
        client.deleteRecords(DistanceRecord::class,emptyList(),listOf("wayirun:$runId:distance"))
    }
    companion object {
        val PERMISSIONS=setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class),HealthPermission.getWritePermission(DistanceRecord::class))
        fun available(context: Context)=HealthConnectClient.getSdkStatus(context)==HealthConnectClient.SDK_AVAILABLE
    }
}
