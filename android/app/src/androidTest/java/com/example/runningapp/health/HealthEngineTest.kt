package com.example.runningapp.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class HealthEngineTest {
    private lateinit var db:RunDatabase
    private var now=0L
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RunDatabase::class.java).build()}
    @After fun close(){db.close()}
    private suspend fun saved(id:String,owner:String?=null) {
        now=0
        val r=RunController(id,RunSettings(RunMode.INDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock{RunTime(now,1700000000000+now)})
        r.start();now=10000;r.finish();RunRepository(db.runs()).save(r.checkpoint(),"local","UTC",0,false,cloudOwnerId=owner)
    }
    @Test fun partialInsertRetriesSameIdsAndDoesNotExportForeignAccounts()=runBlocking {
        saved("one","alice");saved("two","bob")
        val records=mutableSetOf<String>();var fail=true;var writes=0
        val port=object:HealthPort {
            override suspend fun write(run:HealthRun,zone:String){writes++;records+=run.sessionId;if(fail){fail=false;throw java.io.IOException()};records+=run.distanceId}
            override suspend fun delete(runId:String){}
        }
        val engine=HealthEngine(db.runs(),port,{"alice"},{true})
        try {engine.runOnce();fail("Expected interrupted insertion")}catch(_:java.io.IOException){}
        assertEquals("PENDING",db.runs().health("one")!!.state)
        assertFalse(engine.runOnce());assertFalse(engine.runOnce())
        assertEquals(2,writes);assertEquals(setOf("wayirun:one:session","wayirun:one:distance"),records)
        assertNull(db.runs().health("two"))
    }
    @Test fun deletionDuringInsertWinsAndCleanupSurvivesLostConnectionConsent()=runBlocking {
        saved("race","alice");val deleted=mutableListOf<String>();var enabled=true
        val port=object:HealthPort {
            override suspend fun write(run:HealthRun,zone:String){db.runs().discard(run.id,"local");enabled=false}
            override suspend fun delete(runId:String){deleted+=runId}
        }
        val engine=HealthEngine(db.runs(),port,{"alice"},{enabled})
        assertTrue(engine.runOnce());assertEquals("DELETE",db.runs().health("race")!!.state)
        assertFalse(engine.runOnce());assertEquals(listOf("race"),deleted);assertEquals("DELETED",db.runs().health("race")!!.state)
    }
    @Test fun remoteDeletionKeepsCleanupIntentWhenProviderFails()=runBlocking {
        saved("remote","alice");var failDelete=true
        val port=object:HealthPort {
            override suspend fun write(run:HealthRun,zone:String){}
            override suspend fun delete(runId:String){if(failDelete)throw SecurityException()}
        }
        val engine=HealthEngine(db.runs(),port,{"alice"},{true});engine.runOnce()
        db.runs().remoteDeleted("remote","alice")
        try{engine.runOnce();fail("Expected revoked permission")}catch(_:SecurityException){}
        assertNull(db.runs().get("remote"));assertEquals("DELETE",db.runs().health("remote")!!.state)
        failDelete=false;engine.runOnce();assertEquals("DELETED",db.runs().health("remote")!!.state)
    }
}
