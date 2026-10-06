package com.example.runningapp.photos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.runningapp.account.AccountSession
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import com.example.runningapp.sync.SyncApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

internal fun weatherFixture(): String = """{"version":1,"source":"open-meteo","latitude":40.8,"longitude":-74,"observedUtcMs":0,"endpoint":"archive","weatherCode":2,"emoji":"\u26c5","temperatureC":20,"temperatureF":68,"retrievedUtcMs":3600000,"attribution":{"label":"Weather data by Open-Meteo.com","url":"https://open-meteo.com/","licenseUrl":"https://creativecommons.org/licenses/by/4.0/","changes":"Rounded hourly model estimate; emoji representation."}}"""

internal class WeatherFakeApi : SyncApi {
    var calls=0
    var respond: suspend () -> JSONObject = {JSONObject().put("weather",JSONObject(weatherFixture()))}
    override suspend fun request(session: AccountSession, path: String, method: String, body: ByteArray?, contentType: String, match: String?): JSONObject {
        calls++;assertTrue(path.startsWith("/api/weather/"));assertEquals("GET",method);return respond()
    }
}

class PhotoWeatherTest {
    private lateinit var db: RunDatabase
    private val dao get()=db.runs()
    private var account: AccountSession?=AccountSession("alice",null,null,"alice-token",Long.MAX_VALUE)
    @Before fun setup() { db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RunDatabase::class.java).build() }
    @After fun close() {db.close()}
    private suspend fun run(synced: Boolean=true, owner: String?="alice"): String {
        val id=UUID.randomUUID().toString()
        val run=RunController(id,RunSettings(RunMode.OUTDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(0,0)})
        run.start();run.finish();RunRepository(dao).save(run.checkpoint(),"local","UTC",0,false,cloudOwnerId=owner)
        if(synced&&owner!=null)dao.putOperation(dao.operation(id)!!.copy(status="SYNCED"))
        return id
    }

    @Test fun parsedWeatherAndUploadReceiptPreserveExactSnapshotAndRejectTampering() {
        val weather=PhotoWeather.parse(weatherFixture())!!
        val recipe=PhotoRecipe(listOf(true,true,true,true,true),weather)
        val photo=RunPhoto("run","revision",byteArrayOf(-1,-40,-1,-39),recipe.options,false,weather=recipe.selectedWeather!!.json)
        val headers=photoUploadHeaders(photo)
        assertEquals(weather.json,String(java.util.Base64.getDecoder().decode(headers["X-Photo-Weather"]),Charsets.UTF_8))
        assertEquals("false",headers["X-Photo-Public"])
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(photo.jpeg).joinToString("") {"%02x".format(it)}
        val receipt=JSONObject().put("revision",photo.revision).put("options",JSONObject(photo.options))
            .put("weather",JSONObject(photo.weather!!)).put("bytes",photo.jpeg.size).put("sha256",digest)
        assertTrue(photoReceiptMatches(photo,receipt))
        receipt.getJSONObject("weather").put("temperatureC",19)
        assertFalse(photoReceiptMatches(photo,receipt))
        assertNull(PhotoWeather.parse(weatherFixture().replace("https://open-meteo.com/","https://wrong.test/")))
        assertNull(PhotoWeather.parse(weatherFixture().replace("40.8","40.81234")))
        assertNull(PhotoWeather.parse(weatherFixture().replace("\"weatherCode\":2","\"weatherCode\":4")))
        val thunderstorm=PhotoWeather.parse(weatherFixture().replace("\"weatherCode\":2","\"weatherCode\":97")
            .replace("\\u26c5","\\u26c8\\ufe0f"))!!
        assertEquals(97,thunderstorm.snapshot.weatherCode);assertEquals("⛈️",thunderstorm.snapshot.emoji)
    }

    @Test fun failureStateAcceptsOnlyBoundedCategoriesAndRetryTimes() {
        val now=1_000_000L
        for(reason in listOf("provider_unavailable","provider_timeout","provider_invalid_response","provider_throttled","retry_later")) {
            val result=parsePhotoWeatherResponse(JSONObject().put("reason",reason).put("retryAfter",now+30000),now)
            assertEquals(reason,result.reason);assertEquals(now+30000,result.retryAfter)
            assertFalse(result.canRetry(now));assertFalse(result.canRetry(now+29999));assertTrue(result.canRetry(now+30000))
            assertTrue(result.message(now).contains("Retry in 30s"))
        }
        val invalid=parsePhotoWeatherResponse(JSONObject().put("reason","secret URL or body").put("retryAfter","99999999999999"),now)
        assertEquals("provider_unavailable",invalid.reason);assertEquals(0L,invalid.retryAfter)
        assertFalse(invalid.message(now).contains("secret"))
        assertEquals(now+86400000,parsePhotoWeatherResponse(JSONObject().put("retryAfter",Long.MAX_VALUE),now).retryAfter)
        for(value in listOf(-1,1.5)) assertEquals(0L,parsePhotoWeatherResponse(JSONObject().put("retryAfter",value),now).retryAfter)
        assertFalse(PhotoWeatherResult(waitingForSync=true).canRetry(now))
        assertFalse(parsePhotoWeatherResponse(JSONObject().put("reason","no_recorded_weather_location_or_time"),now).canRetry(now))
        val success=parsePhotoWeatherResponse(JSONObject().put("weather",JSONObject(weatherFixture())).put("retryAfter",now+30000),now)
        assertNotNull(success.weather);assertEquals(0L,success.retryAfter);assertFalse(success.canRetry(now))
    }

    @Test fun lookupPreservesFailureEligibilityThenAcceptsSuccessfulRetry() = runBlocking {
        val id=run();val api=WeatherFakeApi();val retryAt=System.currentTimeMillis()+30000
        api.respond={JSONObject().put("weather",JSONObject.NULL).put("reason","provider_timeout").put("retryAfter",retryAt)}
        val failed=lookupPhotoWeather(id,dao,{account},api)
        assertEquals("provider_timeout",failed.reason);assertEquals(retryAt,failed.retryAfter)
        assertFalse(failed.canRetry(retryAt-1));assertTrue(failed.canRetry(retryAt))
        api.respond={JSONObject().put("weather",JSONObject(weatherFixture()))}
        assertNotNull(lookupPhotoWeather(id,dao,{account},api).weather);assertEquals(2,api.calls)
    }

    @Test fun disabledOrUnavailableWeatherCannotLeakIntoUpload() {
        for(recipe in listOf(PhotoRecipe(listOf(true,true,true,false,false),PhotoWeather.parse(weatherFixture())),PhotoRecipe(listOf(true,true,true,false,true),null))) {
            assertFalse(JSONObject(recipe.options).getBoolean("weather"));assertNull(recipe.selectedWeather)
            assertFalse(photoUploadHeaders(RunPhoto("run","revision",byteArrayOf(),recipe.options,false)).containsKey("X-Photo-Weather"))
        }
        val legacy=RunPhoto("run","revision",byteArrayOf(),"{\"time\":true,\"distance\":true,\"pace\":true,\"route\":false}",false)
        assertFalse(photoUploadHeaders(legacy).containsKey("X-Photo-Weather"))
    }

    @Test fun lookupWaitsForSyncAndUsesCurrentOwnerOnly() = runBlocking {
        val id=run(synced=false);val api=WeatherFakeApi()
        assertTrue(lookupPhotoWeather(id,dao,{account},api).waitingForSync);assertEquals(0,api.calls)
        dao.putOperation(dao.operation(id)!!.copy(status="SYNCED"))
        assertNotNull(lookupPhotoWeather(id,dao,{account},api).weather);assertEquals(1,api.calls)
        account=account!!.copy(ownerId="bob",token="bob-token")
        assertNull(lookupPhotoWeather(id,dao,{account},api).weather);assertEquals(1,api.calls)
    }

    @Test fun logoutDeletionAndReplacementDiscardDelayedResponse() = runBlocking {
        for(change in 0..2) {
            account=AccountSession("alice",null,null,"alice-token",Long.MAX_VALUE)
            val id=run();val api=WeatherFakeApi()
            api.respond={
                when(change) {
                    0 -> account=null
                    1 -> dao.discard(id,"local")
                    2 -> dao.putPhoto(RunPhoto(id,"replacement",byteArrayOf(),"{}",false))
                }
                JSONObject().put("weather",JSONObject(weatherFixture()))
            }
            assertNull(lookupPhotoWeather(id,dao,{account},api).weather)
        }
    }

    @Test fun providerFailureLeavesPhotoKeepUsableAndSnapshotSurvivesStorage() = runBlocking {
        val id=run();val api=WeatherFakeApi();api.respond={throw java.io.IOException("synthetic offline")}
        assertNull(lookupPhotoWeather(id,dao,{account},api).weather)
        val weather=PhotoWeather.parse(weatherFixture())!!;val recipe=PhotoRecipe(listOf(true,true,true,false,true),weather)
        val photo=RunPhoto(id,"first",byteArrayOf(1,2,3),recipe.options,false,weather=weather.json)
        assertTrue(dao.keepPhoto(photo,"local","alice",null))
        assertEquals(photo.weather,dao.photo(id)!!.weather)
        assertFalse(dao.keepPhoto(photo.copy(revision="stale"),"local","alice",null))
        assertFalse(dao.keepPhoto(photo.copy(revision="foreign"),"local","bob","first"))
        assertTrue(dao.keepPhoto(photo.copy(revision="second",weather=null,options=PhotoRecipe(listOf(true,true,true,false,false),null).options),"local","alice","first"))
        assertNull(dao.photo(id)!!.weather)
    }
}
