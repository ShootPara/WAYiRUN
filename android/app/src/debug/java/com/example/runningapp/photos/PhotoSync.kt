package com.example.runningapp.photos

import android.content.Context
import com.example.runningapp.account.SessionStore
import com.example.runningapp.storage.RunDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import com.example.runningapp.storage.RunPhoto
import com.example.runningapp.storage.RunDao
import com.example.runningapp.account.AccountSession
import com.example.runningapp.sync.SyncHttpException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal fun photoUploadHeaders(photo: RunPhoto): Map<String,String> = buildMap {
    put("X-Photo-Revision",photo.revision)
    put("X-Photo-Public","false")
    put("X-Photo-Options",photo.options)
    val selected=JSONObject(photo.options).optBoolean("weather",false)
    require(selected==(photo.weather!=null))
    photo.weather?.let {put("X-Photo-Weather",requireNotNull(PhotoWeather.parse(it)).header)}
}

internal fun photoReceiptMatches(photo: RunPhoto, receipt: JSONObject): Boolean = runCatching {
    val digest=java.security.MessageDigest.getInstance("SHA-256").digest(photo.jpeg).joinToString("") { "%02x".format(it) }
    receipt.getString("revision")==photo.revision && receipt.getString("sha256")==digest && receipt.getInt("bytes")==photo.jpeg.size &&
        Json.parseToJsonElement(receipt.getJSONObject("options").toString())==Json.parseToJsonElement(photo.options) &&
        receipt.optJSONObject("weather")?.toString()?.let {Json.parseToJsonElement(it)}==photo.weather?.let {Json.parseToJsonElement(it)}
}.getOrDefault(false)

internal fun photoSyncMessage(error: String?): String = when(error) {
    "AUTH" -> "Sign in again to upload your saved photos."
    "REJECTED" -> "The website could not accept a photo. It is still saved on this phone."
    "RECEIPT" -> "Photo upload could not be confirmed. Your saved photo will retry."
    "SERVER", "RATE_LIMIT" -> "Photo upload is temporarily unavailable. Your saved photos will retry."
    else -> "Photo upload is waiting for a connection. Your photos are saved on this phone."
}

internal fun interface PhotoUploader {
    suspend fun upload(session: AccountSession, photo: RunPhoto): JSONObject
}

/** Small transport boundary: the queue can be tested with real Room and synthetic HTTP outcomes. */
internal class CloudPhotoUploader : PhotoUploader {
    override suspend fun upload(session: AccountSession, photo: RunPhoto): JSONObject = withContext(Dispatchers.IO) {
            val connection=URL("https://wayirun-dev.unopenedparachute.workers.dev/api/photos/${photo.runId}").openConnection() as HttpsURLConnection
            try {
                connection.requestMethod="PUT";connection.connectTimeout=15000;connection.readTimeout=30000
                connection.instanceFollowRedirects=false;connection.useCaches=false;connection.doOutput=true
                connection.setRequestProperty("Authorization","Bearer ${session.token}")
                connection.setRequestProperty("Content-Type","image/jpeg")
                photoUploadHeaders(photo).forEach { (name,value) -> connection.setRequestProperty(name,value) }
                connection.setFixedLengthStreamingMode(photo.jpeg.size)
                connection.outputStream.use {it.write(photo.jpeg)}
                val status=connection.responseCode
                if(status==200) {
                    val data=connection.inputStream.use { stream ->
                        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                        while(true) {val n=stream.read(buffer);if(n<0)break;require(output.size()+n<=65536);output.write(buffer,0,n)}
                        output.toByteArray()
                    }
                    JSONObject(data.toString(Charsets.UTF_8)).getJSONObject("photo")
                } else throw SyncHttpException(status,null)
            }
            finally {connection.disconnect()}
    }
}

/** Ownership comes from the saved run. A failed photo cannot monopolize the durable queue. */
class PhotoSync internal constructor(private val dao: RunDao, private val readSession: () -> AccountSession?,
    private val uploader: PhotoUploader = CloudPhotoUploader(), private val now: () -> Long = System::currentTimeMillis) {
    constructor(context: Context) : this(RunDatabase.get(context).runs(), SessionStore(context)::read)

    suspend fun runOnce(): Boolean = lock.withLock {
        val session=readSession() ?: return@withLock false
        fun current() = readSession()?.let {it.token==session.token && it.ownerId==session.ownerId} == true
        // IDs only; load at most one JPEG at a time, and rotate failed items behind untouched work.
        for(id in dao.photoUploadBatch(session.ownerId)) {
            if(!current()) return@withLock false
            val photo=dao.photo(id) ?: continue
            if(dao.photoUploadAttempt(id,photo.revision,session.ownerId,now())!=1)continue
            var error: String? = null
            try {
                if(session.expired()) throw SyncHttpException(401,null)
                val receipt=uploader.upload(session,photo)
                if(!photoReceiptMatches(photo,receipt)) error="RECEIPT"
            } catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(failure: Exception) {
                error=when(failure) {
                    is SyncHttpException -> when(failure.status) {
                        401,403 -> "AUTH"
                        429 -> "RATE_LIMIT"
                        in 400..499 -> "REJECTED"
                        else -> "SERVER"
                    }
                    is java.io.IOException -> "NETWORK"
                    else -> "RECEIPT"
                }
            }
            if(!current()) return@withLock false
            dao.photoUploadResult(id,photo.revision,session.ownerId,error==null,error)
        }
        current() && dao.pendingPhotoCount(session.ownerId)>0
    }
    companion object { private val lock=Mutex() }
}
