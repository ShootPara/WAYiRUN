package com.example.runningapp.photos

import android.content.Context
import com.example.runningapp.account.SessionStore
import com.example.runningapp.storage.RunDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Ownership comes from the saved run, never a caller-selected account. */
class PhotoSync(private val context: Context) {
    suspend fun runOnce(): Boolean = withContext(Dispatchers.IO) {
        val store=SessionStore(context);val session=store.read() ?: return@withContext false
        val dao=RunDatabase.get(context).runs();var pending=false
        for(photo in dao.pendingPhotos(session.ownerId)) {
            if(store.read()?.token!=session.token) return@withContext false
            val run=dao.get(photo.runId) ?: continue
            if(run.cloudOwnerId!=session.ownerId)continue
            if(dao.operation(photo.runId)?.status!="SYNCED") {pending=true;continue}
            val connection=URL("https://wayirun-dev.unopenedparachute.workers.dev/api/photos/${photo.runId}").openConnection() as HttpsURLConnection
            try {
                connection.requestMethod="PUT";connection.connectTimeout=15000;connection.readTimeout=30000
                connection.instanceFollowRedirects=false;connection.useCaches=false;connection.doOutput=true
                connection.setRequestProperty("Authorization","Bearer ${session.token}")
                connection.setRequestProperty("Content-Type","image/jpeg")
                connection.setRequestProperty("X-Photo-Revision",photo.revision)
                connection.setRequestProperty("X-Photo-Public",photo.public.toString())
                connection.setRequestProperty("X-Photo-Options",photo.options)
                connection.setFixedLengthStreamingMode(photo.jpeg.size)
                connection.outputStream.use {it.write(photo.jpeg)}
                if(connection.responseCode==200) {
                    val data=connection.inputStream.use { stream ->
                        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                        while(true) {val n=stream.read(buffer);if(n<0)break;require(output.size()+n<=65536);output.write(buffer,0,n)}
                        output.toByteArray()
                    }
                    val receipt=JSONObject(data.toString(Charsets.UTF_8)).getJSONObject("photo")
                    require(receipt.getString("revision")==photo.revision)
                    if(store.read()?.token==session.token && dao.get(photo.runId)?.cloudOwnerId==session.ownerId)
                        dao.photoSynced(photo.runId,photo.revision,if(receipt.isNull("publicUrl"))null else receipt.getString("publicUrl"))
                } else pending=true
            } catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_:Exception) {pending=true}
            finally {connection.disconnect()}
        }
        pending || dao.pendingPhotos(session.ownerId).isNotEmpty()
    }
}
