package com.example.runningapp.photos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.account.AccountSession
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import com.example.runningapp.sync.SyncHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class PhotoSyncTest {
    private lateinit var db: RunDatabase
    private val dao get()=db.runs()
    private var session: AccountSession?=AccountSession("alice",null,null,"token",Long.MAX_VALUE)
    @Before fun setup() {db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RunDatabase::class.java).build()}
    @After fun close() {db.close()}
    private suspend fun photo(owner: String?="alice", syncedRun: Boolean=true): RunPhoto {
        val id=UUID.randomUUID().toString()
        val controller=RunController(id,RunSettings(RunMode.INDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(0,0)})
        controller.start();controller.finish()
        RunRepository(dao).save(controller.checkpoint(),"local","UTC",0,false,cloudOwnerId=owner)
        if(syncedRun && owner!=null)dao.putOperation(dao.operation(id)!!.copy(status="SYNCED"))
        val result=RunPhoto(id,UUID.randomUUID().toString(),byteArrayOf(-1,-40,-1,-39),PhotoRecipe(listOf(true,true,true,false,false),null).options,false)
        dao.putPhoto(result);return result
    }
    private fun receipt(photo: RunPhoto)=JSONObject().put("revision",photo.revision).put("options",JSONObject(photo.options))
        .put("bytes",photo.jpeg.size).put("sha256",java.security.MessageDigest.getInstance("SHA-256").digest(photo.jpeg).joinToString("") {"%02x".format(it)})
    private fun engine(upload: suspend (AccountSession,RunPhoto)->JSONObject)=PhotoSync(dao,{session},PhotoUploader(upload),{1000L})

    @Test fun rejectedFirstPhotoDoesNotBlockLaterPhotosAndRetryClearsError()=runBlocking {
        repeat(3){photo()};val first=dao.photoUploadBatch("alice").first();val calls=mutableListOf<String>()
        val failed=engine {_,p->calls+=p.runId;if(p.runId==first)throw SyncHttpException(400,"invalid_photo");receipt(p)}
        assertTrue(failed.runOnce());assertEquals(3,calls.size)
        assertEquals("REJECTED",dao.photo(first)!!.syncError);assertEquals(1,dao.pendingPhotoCount("alice"))
        assertEquals("REJECTED",dao.photoErrorFlow("alice").first())
        assertFalse(engine {_,p->receipt(p)}.runOnce())
        assertTrue(dao.photo(first)!!.synced);assertNull(dao.photo(first)!!.syncError)
        assertEquals(0,dao.pendingPhotoCountFlow("alice").first())
    }
    @Test fun boundedBatchRotatesFailuresAndDoesNotLoadUnsyncedOrForeignRuns()=runBlocking {
        repeat(7){photo()};val waiting=photo(syncedRun=false);val foreign=photo("bob");photo(null)
        val calls=mutableListOf<String>();var tick=0L
        val sync=PhotoSync(dao,{session},PhotoUploader {_,p->calls+=p.runId;throw java.io.IOException("offline")},{++tick})
        assertTrue(sync.runOnce());assertEquals(5,calls.size)
        assertTrue(sync.runOnce());assertEquals(7,calls.toSet().size)
        assertFalse(calls.contains(waiting.runId));assertFalse(calls.contains(foreign.runId))
        assertEquals(8,dao.pendingPhotoCount("alice"))
    }
    @Test fun replacementDeletionAndAccountSwitchRejectLateReceipts()=runBlocking {
        for(change in 0..2) {
            session=AccountSession("alice",null,null,"token",Long.MAX_VALUE)
            val saved=photo()
            engine {_,p->
                when(change) {
                    0->dao.putPhoto(p.copy(revision="replacement"))
                    1->dao.discard(p.runId,"local")
                    2->session=session!!.copy(ownerId="bob",token="other")
                }
                receipt(p)
            }.runOnce()
            val result=dao.photo(saved.runId)
            if(change==1)assertNull(result) else {assertFalse(result!!.synced);assertNull(result.syncError)}
            // Isolate each case without clearing the shared application database.
            dao.discard(saved.runId,"local")
        }
    }
    @Test fun badReceiptAuthAndServerErrorsRemainDurableAndPrivate()=runBlocking {
        val saved=photo()
        assertTrue(engine {_,p->receipt(p).put("sha256","wrong")}.runOnce())
        assertEquals("RECEIPT",dao.photo(saved.runId)!!.syncError)
        for((status,code) in listOf(401 to "AUTH",429 to "RATE_LIMIT",503 to "SERVER")) {
            assertTrue(engine {_,_->throw SyncHttpException(status,"sensitive text must not be stored")}.runOnce())
            assertEquals(code,dao.photo(saved.runId)!!.syncError);assertFalse(dao.photo(saved.runId)!!.synced)
        }
        assertNull(dao.publication(saved.runId));assertNull(dao.photo(saved.runId)!!.publicUrl)
    }
    @Test fun expiredSessionAndCancellationDoNotAcknowledgeOrLosePhotos()=runBlocking {
        val saved=photo();session=session!!.copy(expiresAtMs=0)
        var called=false
        assertTrue(engine {_,p->called=true;receipt(p)}.runOnce());assertFalse(called)
        assertEquals("AUTH",dao.photo(saved.runId)!!.syncError)
        session=session!!.copy(expiresAtMs=Long.MAX_VALUE)
        try {engine {_,_->throw CancellationException("cancelled")}.runOnce();fail("Cancellation swallowed")}
        catch(_:CancellationException) {assertFalse(dao.photo(saved.runId)!!.synced)}
    }
}
