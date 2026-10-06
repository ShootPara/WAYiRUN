package com.example.runningapp.coaching

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.runningapp.account.AccountSession
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.SocketTimeoutException

@RunWith(AndroidJUnit4::class)
class CoachingRequestSyncTest {
    private lateinit var db: RunDatabase
    private val account=AccountSession("alice",null,null,"token",Long.MAX_VALUE)
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RunDatabase::class.java).build()}
    @After fun close(){db.close()}

    @Test fun transientFailureRetainsStableOperationUntilAcknowledged()=runBlocking {
        val id=readyRun(db,"11111111-1111-1111-1111-111111111111")
        val queued=db.runs().queueCoaching(id,"alice")
        val seen=mutableListOf<String>();var failure:Exception?=CoachingHttpException(503)
        val api=object:CoachingApi{override suspend fun generate(session:AccountSession,runId:String,operationId:String):JSONObject{
            assertNotNull(db.runs().coachingRequest(runId));seen+=operationId;failure?.let { throw it }
            return JSONObject().put("runId",runId).put("operationId",operationId).put("state","ready")
        }}
        val sync=CoachingRequestSync(db.runs(),api,{account},{1_000})
        for(error in listOf<Exception>(CoachingHttpException(503),CoachingHttpException(429),SocketTimeoutException("timeout"),java.io.IOException("offline"))) {
            failure=error;assertNull(sync.submit(id,account));assertEquals("PENDING",db.runs().coachingRequest(id)!!.status)
        }
        failure=null
        assertEquals("ready",sync.submit(id,account)!!.getString("state"))
        val saved=db.runs().coachingRequest(id)!!
        assertEquals("ACKNOWLEDGED",saved.status);assertEquals(queued.operationId,saved.operationId)
        assertTrue(seen.all { it==queued.operationId });assertEquals(5,seen.size)
        assertNull(sync.submit(id,account));assertEquals(5,seen.size) // Duplicate acknowledgement is locally converged.
    }

    @Test fun pendingRequestSurvivesRepositoryRecreationAndFallbackMarker()=runBlocking {
        db.close()
        val context=ApplicationProvider.getApplicationContext<Context>();val name="coaching-recreation-${System.nanoTime()}"
        fun open()=Room.databaseBuilder(context,RunDatabase::class.java,name).build()
        var fileDb=open();val id=readyRun(fileDb,"22222222-2222-2222-2222-222222222222")
        val request=fileDb.runs().queueCoaching(id,"alice");fileDb.close()
        context.getSharedPreferences("coaching-attempt",0).edit().putString("run",id).putString("state","playing").commit()
        fileDb=open()
        assertEquals(request,fileDb.runs().coachingRequest(id))
        assertEquals("playing",context.getSharedPreferences("coaching-attempt",0).getString("state",null))
        assertEquals("PENDING",fileDb.runs().coachingRequest(id)!!.status)
        fileDb.close();context.deleteDatabase(name);context.getSharedPreferences("coaching-attempt",0).edit().clear().commit()
        db=Room.inMemoryDatabaseBuilder(context,RunDatabase::class.java).build()
    }

    @Test fun deletionAndAccountIsolationProtectPendingIntent()=runBlocking {
        val id=readyRun(db,"33333333-3333-3333-3333-333333333333")
        db.runs().queueCoaching(id,"alice")
        assertTrue(db.runs().pendingCoaching("bob",Long.MAX_VALUE).isEmpty())
        try { db.runs().queueCoaching(id,"bob");fail("Owner mismatch must fail") } catch(_:IllegalArgumentException){}
        assertTrue(RunRepository(db.runs()).discard(id,"local"));assertNull(db.runs().coachingRequest(id))
    }

    private suspend fun readyRun(database:RunDatabase,id:String):String {
        val run=RunController(id,RunSettings(RunMode.INDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock{RunTime(0,0)})
        run.start();run.finish();RunRepository(database.runs()).save(run.checkpoint(),"local","UTC",0,false,cloudOwnerId="alice")
        val upload=requireNotNull(database.runs().operation(id));database.runs().putOperation(upload.copy(status="SYNCED"));return id
    }
}
