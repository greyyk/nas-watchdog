package com.grey.naswatchdog

import android.content.Context
import android.util.Log
import com.grey.naswatchdog.charts.HistoryRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal data class CachedStats(val response: StatsResponse, val savedAt: Long)

internal fun telemetryConnectionKey(baseUrl: String, token: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest("$baseUrl\u0000$token".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private class TelemetryCache(context: Context) {
    private val directory = File(context.noBackupFilesDir, "telemetry-cache-v1")

    suspend fun read(baseUrl: String, token: String): CachedStats? = withContext(Dispatchers.IO) {
        val file = fileFor(baseUrl, token)
        if (!file.isFile) return@withContext null
        try {
            val raw = file.readText()
            val cached = JSONObject(raw)
            val stats = cached.getJSONObject("stats").toString()
            CachedStats(StatsResponse(parseStatsJson(stats), stats), cached.getLong("savedAt"))
        } catch (e: Exception) {
            Log.w("NASWatchdog", "Ignoring unreadable telemetry cache", e)
            null
        }
    }

    suspend fun write(baseUrl: String, token: String, response: StatsResponse, savedAt: Long) =
        withContext(Dispatchers.IO) {
            try {
                directory.mkdirs()
                val stats = redact(JSONObject(response.rawJson))
                val payload = JSONObject().put("savedAt", savedAt).put("stats", stats).toString()
                val target = fileFor(baseUrl, token)
                val temp = File(target.parentFile, "${target.name}.tmp")
                FileOutputStream(temp).use { stream ->
                    stream.write(payload.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                if (!temp.renameTo(target)) {
                    throw IllegalStateException("Could not atomically replace telemetry cache")
                }
            } catch (e: Exception) {
                Log.w("NASWatchdog", "Could not save telemetry cache", e)
            }
        }

    private fun fileFor(baseUrl: String, token: String) =
        File(directory, "${telemetryConnectionKey(baseUrl, token)}.json")

    private fun redact(stats: JSONObject): JSONObject {
        stats.optJSONArray("smart")?.forEachObject { it.remove("serial") }
        stats.optJSONArray("net")?.forEachObject { it.remove("mac") }
        stats.optJSONArray("processes")?.forEachObject { it.remove("cmdline") }
        return stats
    }

    private fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (index in 0 until length()) optJSONObject(index)?.let(block)
    }
}

internal class TelemetryRepository(context: Context) {
    private val cache = TelemetryCache(context.applicationContext)
    private var lastWriteAt = 0L

    suspend fun fetchStats(baseUrl: String, token: String, now: Long = System.currentTimeMillis()): StatsResponse =
        fetchStatsResponse(baseUrl, token).also { response ->
            if (now - lastWriteAt >= 45_000L) {
                cache.write(baseUrl, token, response, now)
                lastWriteAt = now
            }
        }

    suspend fun fetchHistory(baseUrl: String, token: String, range: HistoryRange): List<Sample> =
        fetchHistory(baseUrl, token, range.apiValue)

    suspend fun cachedStats(baseUrl: String, token: String): CachedStats? = cache.read(baseUrl, token)

    suspend fun persistNow(baseUrl: String, token: String, response: StatsResponse, now: Long = System.currentTimeMillis()) {
        cache.write(baseUrl, token, response, now)
        lastWriteAt = now
    }
}
