package com.example.runningapp.photos

import com.example.runningapp.account.AccountSession
import com.example.runningapp.storage.RunDao
import com.example.runningapp.sync.CloudSyncApi
import com.example.runningapp.sync.SyncApi
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
            require(s.weatherCode in listOf(0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99))
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

internal data class PhotoWeatherResult(val weather: PhotoWeather? = null, val waitingForSync: Boolean = false)

internal suspend fun lookupPhotoWeather(id: String, dao: RunDao, readSession: () -> AccountSession?,
    api: SyncApi = CloudSyncApi()): PhotoWeatherResult {
    return try {
        val session = readSession() ?: return PhotoWeatherResult()
        val run = dao.get(id) ?: return PhotoWeatherResult()
        if (run.cloudOwnerId != session.ownerId || run.state != "FINISHED") return PhotoWeatherResult()
        val op = dao.operation(id)
        if (op?.action == "DELETE") return PhotoWeatherResult()
        if (op?.status != "SYNCED") return PhotoWeatherResult(waitingForSync = true)
        val revision = dao.photo(id)?.revision
        val response = api.request(session, "/api/weather/$id")
        val weather = PhotoWeather.parse(response.optJSONObject("weather")?.toString())
        if (readSession()?.token != session.token || dao.get(id)?.cloudOwnerId != session.ownerId ||
            dao.photo(id)?.revision != revision || dao.operation(id)?.action == "DELETE") PhotoWeatherResult()
        else PhotoWeatherResult(weather)
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { PhotoWeatherResult() }
}
