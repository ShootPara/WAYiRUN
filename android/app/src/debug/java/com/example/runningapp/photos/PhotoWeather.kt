package com.example.runningapp.photos

import com.example.runningapp.account.AccountSession
import com.example.runningapp.storage.RunDao
import com.example.runningapp.sync.CloudSyncApi
import com.example.runningapp.sync.SyncApi
import com.example.runningapp.sync.SyncHttpException
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.abs
import kotlin.math.round

@Serializable
internal data class WeatherAttribution(val label: String, val url: String, val licenseUrl: String, val changes: String)

@Serializable
internal data class WeatherSnapshot(
    val version: Int, val source: String, val latitude: Double, val longitude: Double,
    val observedUtcMs: Long, val endpoint: String, val weatherCode: Int, val emoji: String,
    val temperatureC: Double, val temperatureF: Double, val retrievedUtcMs: Long, val attribution: WeatherAttribution,
)

internal data class PhotoWeather(val json: String, val snapshot: WeatherSnapshot) {
    val header: String get() = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
    companion object {
        fun parse(raw: String?): PhotoWeather? = try {
            require(raw != null && raw.toByteArray(Charsets.UTF_8).size <= 3072)
            val s = Json.decodeFromString<WeatherSnapshot>(raw)
            require(s.version == 1 && s.source == "open-meteo" && s.endpoint in listOf("forecast", "archive"))
            require(s.latitude.isFinite() && abs(s.latitude) <= 90 && s.longitude.isFinite() && abs(s.longitude) <= 180)
            require(abs(s.latitude * 10 - round(s.latitude * 10)) < .000001 && abs(s.longitude * 10 - round(s.longitude * 10)) < .000001)
            require(s.temperatureC.isFinite() && s.temperatureC in -100.0..70.0 && s.temperatureF.isFinite())
            require(abs(s.temperatureF - (s.temperatureC * 1.8 + 32)) <= .2)
            require(s.observedUtcMs >= 0 && s.observedUtcMs % 3_600_000 == 0L && s.retrievedUtcMs >= s.observedUtcMs)
            require(s.weatherCode in listOf(0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,97,99))
            require(s.emoji.isNotBlank() && s.emoji.length <= 16)
            require(s.attribution.url == "https://open-meteo.com/" && s.attribution.licenseUrl == "https://creativecommons.org/licenses/by/4.0/")
            require(s.attribution.label.length <= 100 && s.attribution.changes.length <= 300)
            PhotoWeather(raw, s)
        } catch (_: Exception) { null }
    }
}

/** Captured with rendered bytes: Keep never reconstructs options from a newer editor state. */
internal data class PhotoRecipe(val flags: List<Boolean>, val weather: PhotoWeather?) {
    val selectedWeather: PhotoWeather? get() = weather.takeIf { flags.getOrNull(4) == true }
    val options: String get() = buildJsonObject {
        listOf("time", "distance", "pace", "route").forEachIndexed { index, name -> put(name, flags[index]) }
        put("weather", selectedWeather != null)
    }.toString()
}

internal fun photoSessionIdentity(session: AccountSession?): String = session?.let {
    it.ownerId + ":" + MessageDigest.getInstance("SHA-256").digest(it.token.toByteArray()).joinToString("") { b -> "%02x".format(b) }
} ?: "local"

internal data class PhotoWeatherResult(
    val weather: PhotoWeather? = null, val waitingForSync: Boolean = false,
    val reason: String = "provider_unavailable", val retryAfter: Long = 0,
) {
    fun canRetry(now: Long): Boolean = weather == null && !waitingForSync && now >= retryAfter &&
        reason !in listOf("no_recorded_weather_location_or_time", "sign_in_required", "run_unavailable")

    fun message(now: Long): String {
        if (waitingForSync) return "Weather available after sync."
        if (reason == "sign_in_required") return "Sign in to get weather."
        if (reason == "run_unavailable") return "Weather needs a synced account run."
        if (reason == "no_recorded_weather_location_or_time") return "No recorded location or time for weather."
        val detail = if (reason == "provider_timeout") "Weather request timed out." else "Weather unavailable."
        return if (now < retryAfter) "$detail Retry in ${(retryAfter - now + 999) / 1000}s." else detail
    }
}

/** Accept only fixed categories and bounded numeric timestamps, never raw server messages. */
internal fun parsePhotoWeatherResponse(response: JSONObject, now: Long = System.currentTimeMillis()): PhotoWeatherResult {
    val weather = PhotoWeather.parse(response.optJSONObject("weather")?.toString())
    if (weather != null) return PhotoWeatherResult(weather)
    val reason = response.optString("reason").takeIf { it in listOf("provider_unavailable", "provider_timeout",
        "provider_invalid_response", "provider_throttled", "retry_later", "no_recorded_weather_location_or_time") }
        ?: "provider_unavailable"
    val raw = (response.opt("retryAfter") as? Number)?.toDouble()
    val retryAfter = raw?.takeIf { it.isFinite() && it >= 0 && it == kotlin.math.floor(it) }
        ?.coerceAtMost((now + 86_400_000L).toDouble())?.toLong() ?: 0L
    return PhotoWeatherResult(reason = reason, retryAfter = retryAfter)
}

internal suspend fun lookupPhotoWeather(id: String, dao: RunDao, readSession: () -> AccountSession?,
    api: SyncApi = CloudSyncApi()): PhotoWeatherResult {
    return try {
        val session = readSession() ?: return PhotoWeatherResult(reason = "sign_in_required")
        val run = dao.get(id) ?: return PhotoWeatherResult(reason = "run_unavailable")
        if (run.cloudOwnerId != session.ownerId || run.state != "FINISHED") return PhotoWeatherResult(reason = "run_unavailable")
        val op = dao.operation(id)
        if (op?.action == "DELETE") return PhotoWeatherResult()
        if (op?.status != "SYNCED") return PhotoWeatherResult(waitingForSync = true)
        val revision = dao.photo(id)?.revision
        val response = api.request(session, "/api/weather/$id")
        val result = parsePhotoWeatherResponse(response)
        if (readSession()?.token != session.token || dao.get(id)?.cloudOwnerId != session.ownerId ||
            dao.photo(id)?.revision != revision || dao.operation(id)?.action == "DELETE") PhotoWeatherResult()
        else result
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: SyncHttpException) {
        when (error.status) {
            401, 403 -> PhotoWeatherResult(reason = "sign_in_required")
            404 -> PhotoWeatherResult(reason = "run_unavailable")
            429 -> PhotoWeatherResult(reason = "provider_throttled",
                retryAfter = System.currentTimeMillis() + error.retrySeconds.coerceIn(30, 86_400) * 1000)
            else -> PhotoWeatherResult(retryAfter = System.currentTimeMillis() + 30_000)
        }
    }
    catch (_: Exception) { PhotoWeatherResult(retryAfter = System.currentTimeMillis() + 30_000) }
}
