package com.example.runningapp.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.DistanceRecord
import com.example.runningapp.domain.HealthRun
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/** Explicitly run only on the owned emulator with write permissions granted for synthetic data. */
class HealthProviderTest {
    @Test fun realProviderUsesSameIdsOnRetryAndDeletesOnlySyntheticRecords()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        assertTrue(HealthAdapter.available(context))
        val adapter=HealthAdapter(context);assertTrue(adapter.permitted())
        val client=HealthConnectClient.getOrCreate(context)
        val start=System.currentTimeMillis()-60000
        val run=HealthRun(UUID.randomUUID().toString(),start,start+30000,20000,25.0,true,listOf(start+10000 to start+20000))
        try {
            val records=adapter.records(run,"America/New_York")
            val session=records[0] as ExerciseSessionRecord
            assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,session.exerciseType)
            assertEquals(1,session.segments.size)
            assertEquals(25.0,(records[1] as DistanceRecord).distance.inMeters,0.0)
            val first=client.insertRecords(records);val second=client.insertRecords(records)
            assertEquals(first.recordIdsList,second.recordIdsList)
            assertEquals(2,first.recordIdsList.size)
            adapter.delete(run.id);adapter.delete(run.id)
        }finally{adapter.delete(run.id)}
    }
}
