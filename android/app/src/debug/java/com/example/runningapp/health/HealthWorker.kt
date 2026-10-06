package com.example.runningapp.health

import android.content.Context
import androidx.work.*
import com.example.runningapp.account.SessionStore
import com.example.runningapp.storage.RunDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object HealthConnection {
    fun owner(context: Context)=SessionStore(context).read()?.ownerId
    fun scope(context: Context)=owner(context) ?: "local"
    fun enabled(context: Context)=context.getSharedPreferences("health-connect",0).getString("scope",null)==scope(context)
    fun connect(context: Context) {context.getSharedPreferences("health-connect",0).edit().putString("scope",scope(context)).apply();HealthScheduler.enqueue(context)}
    fun message(context: Context, value: String) {context.getSharedPreferences("health-connect",0).edit().putString("status",value).apply()}
}
class HealthWorker(context: Context,params: WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=lock.withLock {withContext(Dispatchers.IO) {
        val context=applicationContext
        try {
            if(!HealthAdapter.available(context)) {HealthConnection.message(context,"Health Connect is unavailable or needs an update.");return@withContext Result.success()}
            val adapter=HealthAdapter(context)
            if(!adapter.permitted()) {HealthConnection.message(context,"Connect Health Connect to allow run export and pending deletion cleanup.");return@withContext Result.success()}
            val dao=RunDatabase.get(context).runs()
            val more=HealthEngine(dao,adapter,{HealthConnection.owner(context)},{HealthConnection.enabled(context)}).runOnce()
            val blocked=dao.healthStatus().any {it.state=="BLOCKED"}
            HealthConnection.message(context,if(blocked) "Some runs have times Health Connect cannot accept. Your WAYiRUN history is unchanged." else if(!HealthConnection.enabled(context)) "Connect this account to export its runs." else if(more) "Exporting saved runs…" else "Completed runs are up to date.")
            if(more)Result.retry() else Result.success()
        } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(_:SecurityException){HealthConnection.message(context,"Health Connect permission is off. Reconnect to finish export or deletion cleanup.");Result.success()}
        catch(_:Exception){HealthConnection.message(context,"Health Connect will retry. Your runs are safe; pending deletions are retained.");Result.retry()}
    }}
    companion object {private val lock=Mutex()}
}
object HealthScheduler {
    fun enqueue(context: Context) {runCatching {
        val manager=WorkManager.getInstance(context)
        manager.enqueueUniqueWork("wayirun-health",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<HealthWorker>().setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
        manager.enqueueUniquePeriodicWork("wayirun-health-recovery",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<HealthWorker>(15,TimeUnit.MINUTES).build())
    }}
}
