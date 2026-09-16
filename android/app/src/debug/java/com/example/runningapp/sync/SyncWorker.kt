package com.example.runningapp.sync

import android.content.Context
import androidx.work.*
import com.example.runningapp.account.SessionStore
import com.example.runningapp.storage.RunDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val store = SessionStore(applicationContext)
                val dao = RunDatabase.get(applicationContext).runs()
                val api = CloudSyncApi()
                val more = SyncEngine(dao, api, { store.read() }).runOnce()
                val pullMore = RestoreEngine(dao, api, DownloadCache(java.io.File(applicationContext.noBackupFilesDir, "run-downloads")), { store.read() }).runOnce()
                if (more || pullMore) {
                    val owner = store.read()?.ownerId
                    val next = owner?.let { id ->
                        val uploadNext = dao.pending(id).minOfOrNull { op -> op.nextAttemptMs }
                        val pullNext = dao.pull(id)?.takeIf { pullMore && it.status == "PENDING" }?.nextAttemptMs
                        listOfNotNull(uploadNext, pullNext).minOrNull()
                    }
                    if (next != null) SyncScheduler.continueLater(applicationContext, maxOf(60000, next - System.currentTimeMillis()))
                }
                Result.success()
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { if (runAttemptCount < 7) Result.retry() else Result.failure() }
        }
    }
    companion object { private val lock = Mutex() }
}

object SyncScheduler {
    fun continueLater(context: Context, delayMs: Long) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork("wayirun-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build())
        }
    }

    fun enqueue(context: Context) {
        // Scheduling failure must never turn a successfully saved run into a tracking failure.
        runCatching {
            val manager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            // Room is the durable queue. Wake it promptly for new deletions instead of waiting behind an old backoff.
            manager.enqueueUniqueWork("wayirun-sync", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
            manager.enqueueUniquePeriodicWork("wayirun-sync-recovery", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
        }
    }
}
