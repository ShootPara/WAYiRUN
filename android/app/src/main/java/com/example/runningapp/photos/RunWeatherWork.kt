package com.example.runningapp.photos

import android.content.Context
import androidx.work.*
import com.example.runningapp.account.SessionStore
import com.example.runningapp.storage.RunDatabase
import com.example.runningapp.storage.RunWeatherContext
import com.example.runningapp.sync.CloudSyncApi
import com.example.runningapp.sync.SyncApi
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.round

internal fun coarseWeatherContext(id: String, latitude: Double, longitude: Double, started: Long) =
    RunWeatherContext(id, round(latitude * 10) / 10, round(longitude * 10) / 10, started)

internal suspend fun requestEarlyWeather(context: RunWeatherContext,
    session: com.example.runningapp.account.AccountSession, api: SyncApi): PhotoWeatherResult {
    val body = JSONObject().put("latitude", context.latitude).put("longitude", context.longitude)
        .put("startedUtcMs", context.startedUtcMs).toString().toByteArray(Charsets.UTF_8)
    return parsePhotoWeatherResponse(api.request(session, "/api/weather/${context.runId}/start", "POST", body))
}

class RunWeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("run") ?: return Result.failure()
        val dao = RunDatabase.get(applicationContext).runs()
        val context = dao.weatherContext(id) ?: return Result.success()
        if (context.weather == null && System.currentTimeMillis() - context.startedUtcMs > 7 * 86_400_000L) return Result.success()
        val store = SessionStore(applicationContext)
        val session = store.read() ?: return Result.success()
        val run = dao.get(id) ?: return Result.success()
        if (run.cloudOwnerId != session.ownerId || dao.operation(id)?.action == "DELETE") return Result.success()
        return try {
            if (context.weather != null) {
                if (run.state == "FINISHED" && dao.operation(id)?.status == "SYNCED")
                    CloudSyncApi().request(session, "/api/weather/$id")
                return Result.success()
            }
            val result = requestEarlyWeather(context, session, CloudSyncApi())
            if (store.read()?.token != session.token || dao.get(id)?.cloudOwnerId != session.ownerId ||
                dao.operation(id)?.action == "DELETE") return Result.success()
            result.weather?.let { dao.cacheWeather(id, it.json); return Result.success() }
            if (runAttemptCount < 8) Result.retry() else Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { if (runAttemptCount < 8) Result.retry() else Result.success() }
    }
}

object RunWeatherScheduler {
    fun enqueue(context: Context, id: String) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork("run-weather-$id", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RunWeatherWorker>().setInputData(workDataOf("run" to id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
}
