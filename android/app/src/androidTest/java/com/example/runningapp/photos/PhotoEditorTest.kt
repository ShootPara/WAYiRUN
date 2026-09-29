package com.example.runningapp.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.runningapp.account.AccountSession
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class PhotoEditorTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: RunDatabase
    private lateinit var draft: File
    private lateinit var snapshot: RunSnapshot
    private val visible=mutableStateOf(true)
    private var account: AccountSession?=AccountSession("alice",null,null,"test-session",Long.MAX_VALUE)
    private val dao get()=db.runs()
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,RunDatabase::class.java).build()
        val id=UUID.randomUUID().toString()
        val run=RunController(id,RunSettings(RunMode.OUTDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(0,0)})
        run.start();run.finish();snapshot=run.snapshot()
        runBlocking {
            RunRepository(dao).save(run.checkpoint(),"photo-editor-test","UTC",0,false,cloudOwnerId="alice")
            dao.putOperation(dao.operation(id)!!.copy(status="SYNCED"))
            dao.putRoute(RoutePoint(0,id,1,0,40.0,-74.0,5f));dao.putRoute(RoutePoint(0,id,1,1000,40.001,-73.999,5f))
        }
        draft=File(File(context.cacheDir,"photos").apply {mkdirs()},"draft-$id.jpg")
        val image=Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
        draft.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,95,it)};image.recycle()
        compose.activityRule.scenario.onActivity {it.setTurnScreenOn(true);it.setShowWhenLocked(true)}
    }
    @After fun close() {
        compose.runOnIdle {visible.value=false};compose.waitForIdle()
        draft.delete();db.close()
    }
    private fun show(api: WeatherFakeApi, restoration: StateRestorationTester?=null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            MaterialTheme(colorScheme=if(androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                if(visible.value)PhotoDialog(snapshot,{visible.value=false},dao,api,{account},{visible.value=false})
            }
        }
        if(restoration!=null)restoration.setContent(content) else compose.setContent(content)
    }
    private fun ready() {
        compose.waitUntil(15000) {compose.onAllNodesWithText("Keep Photo").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }}
    }
    private fun saved(): RunPhoto {
        compose.waitUntil(15000) {runBlocking {dao.photo(snapshot.runId)!=null}}
        return runBlocking {dao.photo(snapshot.runId)!!}
    }
    private fun capture(name: String) {
        val directory=File(ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null),"milestone-5-1").apply {mkdirs()}
        val configuration=compose.activity.resources.configuration
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {image ->
            File(directory,"$name-${configuration.uiMode}-${configuration.fontScale}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }

    @Test fun keepWhileWeatherPendingIgnoresLateResult() {
        val gate=CompletableDeferred<JSONObject>();val api=WeatherFakeApi().apply {respond={gate.await()}}
        show(api);ready();compose.waitUntil(15000) {api.calls==1}
        compose.onNodeWithText("Keep Photo").performScrollTo().performClick()
        val first=saved();assertNull(first.weather);assertFalse(JSONObject(first.options).getBoolean("weather"))
        gate.complete(JSONObject().put("weather",JSONObject(weatherFixture())))
        compose.waitForIdle()
        val later=runBlocking {dao.photo(snapshot.runId)!!}
        assertEquals(first.revision,later.revision);assertArrayEquals(first.jpeg,later.jpeg);assertNull(later.weather)
    }

    @Test fun snapshotSurvivesEditorRestorationAndMatchesSavedUpload() {
        val api=WeatherFakeApi();val restoration=StateRestorationTester(compose)
        show(api,restoration)
        compose.waitUntil(15000) {compose.onAllNodesWithTag("photo-overlay-Weather").fetchSemanticsNodes().any {!it.config.contains(SemanticsProperties.Disabled)}}
        ready();restoration.emulateSavedInstanceStateRestore();ready()
        compose.onNodeWithText("Your run photo").performScrollTo();capture("weather-editor-preview")
        compose.onNodeWithTag("photo-overlay-Weather").performScrollTo().assertIsOn();capture("weather-editor-controls")
        compose.onNodeWithText("Keep Photo").performScrollTo();capture("weather-editor-keep")
        compose.onNodeWithText("Keep Photo").performScrollTo().performClick()
        val photo=saved();assertEquals(1,api.calls)
        assertEquals(JSONObject(weatherFixture()).toString(),JSONObject(photo.weather!!).toString())
        assertEquals(photo.weather,String(java.util.Base64.getDecoder().decode(photoUploadHeaders(photo)["X-Photo-Weather"]),Charsets.UTF_8))
        assertTrue(JSONObject(photo.options).getBoolean("weather"))
    }

    @Test fun openEditorCanResolveAfterSyncThenKeepWeatherOff() {
        runBlocking {dao.putOperation(dao.operation(snapshot.runId)!!.copy(status="PENDING"))}
        val api=WeatherFakeApi();show(api);ready()
        compose.onNodeWithTag("photo-overlay-Weather").performScrollTo().assertIsNotEnabled()
        assertEquals(0,api.calls)
        runBlocking {dao.putOperation(dao.operation(snapshot.runId)!!.copy(status="SYNCED"))}
        compose.waitUntil(15000) {api.calls==1};ready()
        compose.onNodeWithTag("photo-overlay-Weather").performScrollTo().performClick()
        ready();compose.onNodeWithText("Keep Photo").performScrollTo().performClick()
        val photo=saved();assertNull(photo.weather);assertFalse(JSONObject(photo.options).getBoolean("weather"))
    }

    @Test fun keptPhotoShowsUploadFailureThenConfirmedAccountSave() {
        draft.delete()
        runBlocking {dao.putPhoto(RunPhoto(snapshot.runId,"saved",byteArrayOf(-1,-40,-1,-39),"{}",false,syncError="REJECTED"))}
        show(WeatherFakeApi())
        compose.waitUntil(10000) {compose.onAllNodesWithText(photoSyncMessage("REJECTED")).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(photoSyncMessage("REJECTED")).performScrollTo().assertIsDisplayed()
        runBlocking {dao.photoUploadResult(snapshot.runId,"saved","alice",true,null)}
        compose.waitUntil(10000) {compose.onAllNodesWithText("Saved on this phone and in your account.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(photoSyncMessage("REJECTED")).assertDoesNotExist()
    }

    @Test fun accountChangeClosesEditorAndDiscardsLateReply() {
        val gate=CompletableDeferred<JSONObject>();val api=WeatherFakeApi().apply {respond={gate.await()}}
        show(api);ready();compose.waitUntil(15000) {api.calls==1}
        compose.runOnIdle {account=account!!.copy(ownerId="bob",token="different")}
        gate.complete(JSONObject().put("weather",JSONObject(weatherFixture())))
        compose.waitUntil(15000) {!visible.value}
        assertNull(runBlocking {dao.photo(snapshot.runId)})
    }

    @Test fun exitingEditorDiscardsPendingWeatherWithoutSavingPhoto() {
        val gate=CompletableDeferred<JSONObject>();val api=WeatherFakeApi().apply {respond={gate.await()}}
        show(api);ready();compose.waitUntil(15000) {api.calls==1}
        compose.onNodeWithText("Skip").performScrollTo().performClick()
        gate.complete(JSONObject().put("weather",JSONObject(weatherFixture())))
        compose.waitForIdle();assertFalse(visible.value);assertFalse(draft.exists())
        assertNull(runBlocking {dao.photo(snapshot.runId)})
    }
}
