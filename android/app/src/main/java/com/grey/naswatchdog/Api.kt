package com.grey.naswatchdog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

// ---------- models ----------

data class TemperatureLimits(
    val minC: Double? = null,
    val maxC: Double? = null,
    val lcritC: Double? = null,
    val critC: Double? = null,
    val emergencyC: Double? = null,
)

data class Temp(
    val key: String,
    val label: String,
    val c: Double,
    val warn: Double,
    val crit: Double,
    val category: String = "",
    val source: String = "",
    val chip: String = "",
    val sensor: String = "",
    val device: String = "",
    val coreType: String = "",
    val cpus: List<Int> = emptyList(),
    val limits: TemperatureLimits? = null,
    val alarms: List<String> = emptyList(),
)

data class ThermalTrip(
    val index: Int,
    val type: String,
    val tempC: Double?,
    val hystC: Double?,
)

data class ThermalZone(
    val key: String,
    val label: String,
    val c: Double?,
    val warn: Double = 0.0,
    val crit: Double = 0.0,
    val mode: String = "",
    val policy: String = "",
    val trips: List<ThermalTrip> = emptyList(),
)

data class Fan(val label: String, val rpm: Int, val pwm: Int, val key: String = "")
data class FanHistory(val rpm: Int, val pwm: Int)
data class ThrottleDetails(
    val packageCount: Long,
    val packageTotalMs: Long,
    val coreCount: Long,
    val coreTotalMs: Long,
)
data class Raid(val name: String, val level: String, val members: String, val degraded: Boolean, val sync: String)
data class Disk(
    val name: String, val model: String, val size: Long,
    val readRate: Double, val writeRate: Double, val util: Double,
    val readIops: Double, val writeIops: Double,
)
data class Smart(
    val device: String, val health: String, val model: String, val serial: String,
    val temp: Double, val hours: Long, val realloc: Long, val pending: Long,
    val uncorrect: Long, val crc: Long, val wear: Long, val mediaErrors: Long,
    val error: String,
    val temperatureMinC: Double? = null,
    val temperatureMaxC: Double? = null,
)
data class Container(
    val name: String, val state: String, val health: String, val image: String,
    val cpu: Double, val mem: Long, val memPct: Double, val netRx: Long, val netTx: Long,
    val blockRead: Long, val blockWrite: Long, val ports: List<String>,
)
data class Alert(
    val id: String,
    val severity: String,
    val title: String,
    val detail: String,
    val since: String,
    val active: Boolean = true,
)
data class AlertEvent(val kind: String, val alert: Alert, val ts: String = "")
data class Bcache(
    val present: Boolean, val mode: String, val state: String,
    val hits: Long, val misses: Long, val hitRatio: Double, val available: Double,
)
data class Power(val packageWatts: Double, val parts: Map<String, Double>)
data class Gpu(val card: String, val freq: Long, val max: Long, val rc6: Long)
data class NetStats(
    val established: Long, val timeWait: Int, val inUse: Int, val sockets: Int,
    val retransPct: Double, val retrans: Long, val conntrack: Int, val conntrackMax: Int,
    val arp: Int, val routes: Int, val listenOverflows: Long,
)
data class SystemInfo(
    val ntp: Boolean, val timezone: String, val failedUnits: List<String>,
    val ctxt: Long, val running: Int, val blocked: Int,
)
data class Core(val id: Int, val usage: Double, val freq: Long)
data class CState(val name: String, val timeMs: Long, val latency: Long)
data class Proc(val pid: Int, val name: String, val cpu: Double, val mem: Double, val rss: Long, val cmd: String)
data class NetIface(
    val name: String, val state: String, val speed: Int, val rxRate: Double, val txRate: Double,
    val rxBytes: Long, val txBytes: Long, val errors: Long, val drops: Long, val virtual: Boolean,
    val mac: String,
)
data class Mount(val target: String, val device: String, val fstype: String, val ro: Boolean, val usedPct: Double)
data class BtrfsDf(val profile: String, val total: Long, val used: Long)
data class LogSource(val id: String, val path: String, val hits: Int)
data class Scrub(
    val status: String, val started: String, val progress: Double,
    val csumErrors: Long, val uncorrectable: Long, val corrected: Long, val readErrors: Long,
)

data class Stats(
    val hostname: String, val os: String, val kernel: String,
    val uptime: Double, val load1: Double, val load5: Double, val load15: Double,
    val cpu: Double, val cores: Int, val governor: String, val maxFreq: Long,
    val throttleCount: Long, val perCore: List<Core>, val cStates: List<CState>,
    val memPct: Double, val memTotal: Long, val memUsed: Long, val memAvail: Long,
    val cached: Long, val buffers: Long, val swapUsed: Long, val swapTotal: Long,
    val oomKills: Long, val dirty: Long, val writeback: Long,
    val poolRo: Boolean, val poolTotal: Long, val poolUsed: Long, val poolPct: Double,
    val poolCorruption: Long, val poolDevice: String, val poolStats: Map<String, Long>,
    val temps: List<Temp>, val fans: List<Fan>, val raid: List<Raid>, val disks: List<Disk>,
    val smart: List<Smart>, val containers: List<Container>, val alerts: List<Alert>,
    val bcache: Bcache, val power: Power, val gpus: List<Gpu>, val netStats: NetStats,
    val system: SystemInfo, val edacCe: Long, val edacUe: Long,
    val dockerVersion: String, val dockerRunning: Int, val dockerStopped: Int, val images: Int,
    val psiCpu: Double, val psiMem: Double, val psiIo: Double,
    val processes: List<Proc>, val net: List<NetIface>, val mounts: List<Mount>,
    val btrfsDf: Map<String, BtrfsDf>, val scrub: Scrub, val subvolumes: Int,
    val actionsEnabled: Boolean, val rebootEnabled: Boolean, val version: String,
    val logSources: List<LogSource>,
    val thermalZones: List<ThermalZone> = emptyList(),
    val throttle: ThrottleDetails? = null,
)

data class Sample(
    val ts: String, val cpu: Double?, val memPct: Double?, val load1: Double?,
    val netRx: Double?, val netTx: Double?, val diskRead: Double?, val diskWrite: Double?,
    val temps: Map<String, Double>, val poolPct: Double?,
    val fans: Map<String, FanHistory> = emptyMap(),
)

data class StatsResponse(val stats: Stats, val rawJson: String)

// ---------- http ----------

class ApiException(val statusCode: Int, message: String) : Exception(message)

fun normalizeServerUrl(input: String): String {
    val trimmed = input.trim()
    require(trimmed.isNotEmpty()) { "Enter the watchdog server address." }
    val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    val uri = try {
        URI(withScheme)
    } catch (e: Exception) {
        throw IllegalArgumentException("Enter a valid server address, for example http://nas.local:8090.")
    }
    require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
        "The server address must use http:// or https://."
    }
    require(!uri.host.isNullOrBlank()) { "The server address is missing a hostname or IP address." }
    require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "Use only the server address, without credentials or a query string."
    }
    return uri.toString().trimEnd('/')
}

fun connectionError(error: Throwable): String = when (error) {
    is ApiException -> when (error.statusCode) {
        401, 403 -> "Access denied. Check the API token."
        404 -> "The watchdog API was not found at this address."
        503 -> "The watchdog is starting or has not collected telemetry yet."
        else -> error.message ?: "The server returned an error."
    }
    is UnknownHostException -> "Server not found. Check the address, Wi-Fi, and VPN."
    is ConnectException -> "Could not connect. Check that the watchdog is running and reachable."
    is SocketTimeoutException -> "The server took too long to respond. Trying again shortly."
    is SSLException -> "Secure connection failed. Check the HTTPS certificate. Use HTTP only on a trusted private LAN or VPN; it does not encrypt credentials."
    is JSONException -> "The server returned an unreadable response."
    is IllegalArgumentException -> error.message ?: "Check the server address."
    else -> error.message?.take(120) ?: "Connection failed. Trying again shortly."
}

private fun HttpURLConnection.authorize(token: String) {
    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
}

private fun HttpURLConnection.throwIfNotSuccessful() {
    val code = responseCode
    if (code in 200..299) return
    val detail = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
    throw ApiException(code, "HTTP $code${if (detail.isNotBlank()) ": ${detail.take(120)}" else ""}")
}

private suspend fun http(url: String, token: String, method: String = "GET", body: String? = null): String {
    demoApiResponse(url, token)?.let { return it }
    return withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            requestMethod = method
            authorize(token)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
            conn.throwIfNotSuccessful()
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

suspend fun streamAlerts(baseUrl: String, token: String, onAlerts: (List<Alert>) -> Unit): Unit {
    if (isDemoConnection(baseUrl)) return demoAlertStream(token, onAlerts)
    return withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl.trimEnd('/') + "/api/v1/stream").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
            authorize(token)
        }
        try {
            conn.throwIfNotSuccessful()
            conn.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val payload = line.substringAfter(':').trim()
                    if (payload.isEmpty()) return@forEach
                    try {
                        onAlerts(parseAlerts(JSONObject(payload)))
                    } catch (_: JSONException) {
                        // Ignore an incomplete event and keep the healthy stream open.
                    }
                }
            }
            throw ConnectException("The live stream closed")
        } finally {
            conn.disconnect()
        }
    }
}


suspend fun fetchStatsResponse(baseUrl: String, token: String): StatsResponse {
    val raw = http(baseUrl.trimEnd('/') + "/api/v1/stats", token)
    return withContext(Dispatchers.Default) {
        StatsResponse(parseStats(JSONObject(raw)), raw)
    }
}

suspend fun fetchStats(baseUrl: String, token: String): Stats = fetchStatsResponse(baseUrl, token).stats

internal fun parseStatsJson(raw: String): Stats = parseStats(JSONObject(raw))

internal fun parseHistoryJson(raw: String): List<Sample> = parseSamples(JSONObject(raw))

internal fun parseEventsJson(raw: String): List<AlertEvent> = parseEvents(JSONObject(raw))

suspend fun fetchHistory(baseUrl: String, token: String, range: String = "1h"): List<Sample> =
    withContext(Dispatchers.Default) {
        val root = JSONObject(http(baseUrl.trimEnd('/') + "/api/v1/history?range=$range", token))
        parseSamples(root)
    }

suspend fun fetchEvents(baseUrl: String, token: String): List<AlertEvent> =
    withContext(Dispatchers.Default) {
        val root = JSONObject(http(baseUrl.trimEnd('/') + "/api/v1/events", token))
        parseEvents(root)
    }

suspend fun fetchLog(baseUrl: String, token: String, source: String, lines: Int, q: String): List<String> {
    val base = baseUrl.trimEnd('/')
    val query = if (q.isBlank()) "" else "&q=" + java.net.URLEncoder.encode(q, "UTF-8")
    val url = when (source) {
        "journal" -> "$base/api/v1/journal?lines=$lines$query"
        "dmesg" -> "$base/api/v1/dmesg?lines=$lines$query"
        else -> "$base/api/v1/logs?source=" + java.net.URLEncoder.encode(source, "UTF-8") + "&lines=$lines$query"
    }
    val root = JSONObject(http(url, token))
    return parseLines(root)
}

suspend fun fetchContainerLogs(baseUrl: String, token: String, name: String, tail: Int = 300): String {
    val url = baseUrl.trimEnd('/') + "/api/v1/containers/logs?name=" +
        java.net.URLEncoder.encode(name, "UTF-8") + "&tail=$tail"
    return JSONObject(http(url, token)).optString("text")
}

data class ActionResult(val ok: Boolean, val message: String)

suspend fun postAction(baseUrl: String, token: String, action: String, target: String): ActionResult {
    val body = JSONObject()
        .put("action", action)
        .put("target", target)
        .put("confirm", true)
        .toString()
    val root = JSONObject(http(baseUrl.trimEnd('/') + "/api/v1/actions", token, "POST", body))
    return ActionResult(root.optBoolean("ok"), root.optString("message"))
}

// ---------- parsing ----------

private fun JSONObject.optionalDouble(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    return optDouble(name, Double.NaN).takeIf { it.isFinite() }
}

private fun JSONObject.optionalInt(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    val value = when (val raw = opt(name)) {
        is Number -> raw.toDouble().takeIf {
            it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE && it % 1.0 == 0.0
        }?.toInt()
        is String -> raw.toIntOrNull()
        else -> null
    }
    return value
}

private fun parseSamples(root: JSONObject): List<Sample> {
    val arr = root.optJSONArray("samples") ?: return emptyList()
    val out = ArrayList<Sample>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val temps = HashMap<String, Double>()
        o.optJSONObject("temps")?.let { t ->
            t.keys().forEach { k -> t.optionalDouble(k)?.let { value -> temps[k] = value } }
        }
        val fans = HashMap<String, FanHistory>()
        o.optJSONObject("fans")?.let { f ->
            f.keys().forEach { k ->
                val reading = f.optJSONObject(k) ?: return@forEach
                val rpm = reading.optionalInt("rpm")?.takeIf { it >= 0 }
                val pwm = reading.optionalInt("pwm")?.takeIf { it in 0..255 }
                if (rpm != null && pwm != null) fans[k] = FanHistory(rpm, pwm)
            }
        }
        out.add(
            Sample(
                ts = o.optString("ts"),
                cpu = o.optionalDouble("cpu"),
                memPct = o.optionalDouble("mem_pct"),
                load1 = o.optionalDouble("load1"),
                netRx = o.optionalDouble("net_rx"),
                netTx = o.optionalDouble("net_tx"),
                diskRead = o.optionalDouble("disk_read"),
                diskWrite = o.optionalDouble("disk_write"),
                temps = temps,
                poolPct = o.optionalDouble("used_pct"),
                fans = fans,
            )
        )
    }
    return out
}

private fun parseLines(root: JSONObject): List<String> {
    val arr = root.optJSONArray("lines") ?: return emptyList()
    val out = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) out.add(arr.optString(i))
    return out
}

private fun jArray(o: JSONObject, name: String): List<JSONObject> {
    val arr = o.optJSONArray(name) ?: return emptyList()
    val out = ArrayList<JSONObject>(arr.length())
    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(it) }
    return out
}

private fun parseAlert(o: JSONObject): Alert = Alert(
    id = o.optString("id"),
    severity = o.optString("severity"),
    title = o.optString("title"),
    detail = o.optString("detail"),
    since = o.optString("since"),
    active = o.optBoolean("active", true),
)

private fun parseAlerts(root: JSONObject): List<Alert> = jArray(root, "alerts").map(::parseAlert)

private fun parseEvents(root: JSONObject): List<AlertEvent> = jArray(root, "events").mapNotNull {
    val alert = it.optJSONObject("alert") ?: return@mapNotNull null
    AlertEvent(kind = it.optString("kind"), alert = parseAlert(alert), ts = it.optString("ts"))
}

private fun parseTemp(o: JSONObject): Temp? {
    val key = o.optString("key").trim()
    val current = o.optionalDouble("c") ?: return null
    if (key.isEmpty()) return null
    val limits = o.optJSONObject("limits")?.let {
        TemperatureLimits(
            minC = it.optionalDouble("min_c"),
            maxC = it.optionalDouble("max_c"),
            lcritC = it.optionalDouble("lcrit_c"),
            critC = it.optionalDouble("crit_c"),
            emergencyC = it.optionalDouble("emergency_c"),
        )
    }
    val cpus = o.optJSONArray("cpus")?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optInt(index, -1)
                if (value >= 0) add(value)
            }
        }
    }.orEmpty().distinct()
    val alarms = o.optJSONArray("alarms")?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }.orEmpty()
    return Temp(
        key = key,
        label = o.optString("label").ifBlank { key },
        c = current,
        warn = o.optionalDouble("warn") ?: 0.0,
        crit = o.optionalDouble("crit") ?: 0.0,
        category = o.optString("category"),
        source = o.optString("source"),
        chip = o.optString("chip"),
        sensor = o.optString("sensor"),
        device = o.optString("device"),
        coreType = o.optString("core_type"),
        cpus = cpus,
        limits = limits,
        alarms = alarms,
    )
}

private fun parseThermalZone(o: JSONObject): ThermalZone? {
    val key = o.optString("key").trim()
    if (key.isEmpty()) return null
    val trips = jArray(o, "trips").mapNotNull {
        val index = it.optionalInt("index")?.takeIf { index -> index >= 0 } ?: return@mapNotNull null
        ThermalTrip(
            index = index,
            type = it.optString("type"),
            tempC = it.optionalDouble("temp_c"),
            hystC = it.optionalDouble("hyst_c")?.takeIf { hyst -> hyst >= 0.0 },
        )
    }
    return ThermalZone(
        key = key,
        label = o.optString("label").ifBlank { key },
        c = o.optionalDouble("c"),
        warn = o.optionalDouble("warn") ?: 0.0,
        crit = o.optionalDouble("crit") ?: 0.0,
        mode = o.optString("mode"),
        policy = o.optString("policy"),
        trips = trips,
    )
}

private fun parseStats(root: JSONObject): Stats {
    val host = root.optJSONObject("host") ?: JSONObject()
    val cpu = root.optJSONObject("cpu") ?: JSONObject()
    val mem = root.optJSONObject("memory") ?: JSONObject()
    val pool = root.optJSONObject("pool") ?: JSONObject()
    val bc = root.optJSONObject("bcache") ?: JSONObject()
    val power = root.optJSONObject("power") ?: JSONObject()
    val ns = root.optJSONObject("net_stats") ?: JSONObject()
    val system = root.optJSONObject("system") ?: JSONObject()
    val docker = root.optJSONObject("docker") ?: JSONObject()
    val ps = root.optJSONObject("pressure") ?: JSONObject()
    val edac = root.optJSONObject("edac") ?: JSONObject()
    val watchdog = root.optJSONObject("watchdog") ?: JSONObject()
    val kernel = root.optJSONObject("kernel") ?: JSONObject()
    val throttle = cpu.optJSONObject("throttle")?.let {
        ThrottleDetails(
            packageCount = it.optLong("package_count"),
            packageTotalMs = it.optLong("package_total_ms"),
            coreCount = it.optLong("core_count"),
            coreTotalMs = it.optLong("core_total_ms"),
        )
    }

    val temps = jArray(root, "temps").mapNotNull(::parseTemp)
    val thermalZones = jArray(root, "thermal_zones").mapNotNull(::parseThermalZone)
    val fans = jArray(root, "fans").map {
        Fan(
            label = it.optString("label"),
            rpm = it.optionalInt("rpm")?.takeIf { rpm -> rpm >= 0 } ?: 0,
            pwm = it.optionalInt("pwm")?.coerceIn(0, 255) ?: 0,
            key = it.optString("key"),
        )
    }
    val raid = jArray(root, "raid").map {
        Raid(it.optString("name"), it.optString("level"), it.optString("members"),
            it.optBoolean("degraded"), it.optString("sync"))
    }
    val disks = jArray(root, "disks").map {
        Disk(it.optString("name"), it.optString("model"), it.optLong("size_bytes"),
            it.optDouble("read_rate", 0.0), it.optDouble("write_rate", 0.0),
            it.optDouble("util_pct", 0.0), it.optDouble("read_iops", 0.0), it.optDouble("write_iops", 0.0))
    }
    val smart = jArray(root, "smart").map {
        Smart(it.optString("device"), it.optString("health"), it.optString("model"), it.optString("serial"),
            it.optionalDouble("temperature") ?: 0.0, it.optLong("power_on_hours"), it.optLong("reallocated"),
            it.optLong("pending"), it.optLong("uncorrectable"), it.optLong("crc_errors"),
            it.optLong("percentage_used"), it.optLong("media_errors"), it.optString("error"),
            temperatureMinC = it.optionalDouble("temperature_min_c"),
            temperatureMaxC = it.optionalDouble("temperature_max_c"),
        )
    }
    val containers = jArray(root, "containers").map {
        val ports = ArrayList<String>()
        it.optJSONArray("ports")?.let { p -> for (i in 0 until p.length()) ports.add(p.optString(i)) }
        Container(it.optString("name"), it.optString("state"), it.optString("health"), it.optString("image"),
            it.optDouble("cpu_pct", 0.0), it.optLong("mem_usage"), it.optDouble("mem_pct", 0.0),
            it.optLong("net_rx"), it.optLong("net_tx"), it.optLong("block_read"), it.optLong("block_write"), ports)
    }
    val alerts = parseAlerts(root)
    val gpus = jArray(root, "gpu").map {
        Gpu(it.optString("card"), it.optLong("freq_mhz"), it.optLong("max_mhz"), it.optLong("rc6_ms"))
    }
    val perCore = jArray(cpu, "per_core_detail").map {
        Core(it.optInt("id"), it.optDouble("usage", 0.0), it.optLong("freq_mhz"))
    }
    val cStates = jArray(cpu, "c_states").map {
        CState(it.optString("name"), it.optLong("time_ms"), it.optLong("latency_us"))
    }
    val processes = jArray(root, "processes").map {
        Proc(it.optInt("pid"), it.optString("name"), it.optDouble("cpu_pct", 0.0),
            it.optDouble("mem_pct", 0.0), it.optLong("rss"), it.optString("cmdline"))
    }
    val net = jArray(root, "net").map {
        NetIface(it.optString("name"), it.optString("state"), it.optInt("speed_mbps"),
            it.optDouble("rx_rate", 0.0), it.optDouble("tx_rate", 0.0), it.optLong("rx_bytes"),
            it.optLong("tx_bytes"), it.optLong("rx_errs") + it.optLong("tx_errs"),
            it.optLong("rx_drops") + it.optLong("tx_drops"), it.optBoolean("virtual"), it.optString("mac"))
    }
    val mounts = jArray(root, "mounts").map {
        Mount(it.optString("target"), it.optString("device"), it.optString("fstype"),
            it.optBoolean("ro"), it.optDouble("used_pct", 0.0))
    }
    val btrfsDf = HashMap<String, BtrfsDf>()
    root.optJSONObject("btrfs")?.optJSONObject("df")?.let { df ->
        df.keys().forEach { k ->
            val o = df.optJSONObject(k) ?: return@forEach
            btrfsDf[k] = BtrfsDf(o.optString("profile"), o.optLong("total"), o.optLong("used"))
        }
    }
    val scrubObj = root.optJSONObject("btrfs")?.optJSONObject("scrub") ?: JSONObject()
    val scrub = Scrub(
        scrubObj.optString("status"), scrubObj.optString("started"),
        scrubObj.optDouble("progress_pct", 0.0), scrubObj.optLong("csum_errors"),
        scrubObj.optLong("uncorrectable_errors"), scrubObj.optLong("corrected_errors"),
        scrubObj.optLong("read_errors"),
    )
    val poolStats = HashMap<String, Long>()
    pool.optJSONObject("device_stats")?.let { ds ->
        ds.keys().forEach { k -> poolStats[k] = ds.optLong(k) }
    }
    val failed = ArrayList<String>()
    system.optJSONArray("failed_units")?.let { a -> for (i in 0 until a.length()) failed.add(a.optString(i)) }
    val powerParts = HashMap<String, Double>()
    power.optJSONObject("parts")?.let { p -> p.keys().forEach { k -> powerParts[k] = p.optDouble(k) } }

    val logSources = jArray(root, "logs").map {
        val matches = it.optJSONObject("matches")
        var hits = 0
        matches?.let { m -> m.keys().forEach { k -> hits += m.optInt(k) } }
        LogSource(it.optString("source"), it.optString("path"), hits)
    }

    fun psi(key: String): Double = ps.optJSONObject(key)?.optDouble("some10", 0.0) ?: 0.0

    return Stats(
        hostname = host.optString("hostname"), os = host.optString("os"), kernel = host.optString("kernel"),
        uptime = host.optDouble("uptime_s", 0.0), load1 = host.optDouble("load1", 0.0),
        load5 = host.optDouble("load5", 0.0), load15 = host.optDouble("load15", 0.0),
        cpu = cpu.optDouble("usage_pct", 0.0), cores = cpu.optInt("cores"),
        governor = cpu.optString("governor"), maxFreq = cpu.optLong("max_freq_mhz"),
        throttleCount = throttle?.packageCount ?: 0L,
        perCore = perCore, cStates = cStates,
        memPct = mem.optDouble("used_pct", 0.0), memTotal = mem.optLong("total"),
        memUsed = mem.optLong("used"), memAvail = mem.optLong("available"),
        cached = mem.optLong("cached"), buffers = mem.optLong("buffers"),
        swapUsed = mem.optLong("swap_used"), swapTotal = mem.optLong("swap_total"),
        oomKills = mem.optLong("oom_kills"), dirty = mem.optLong("dirty"),
        writeback = mem.optLong("writeback"),
        poolRo = pool.optBoolean("read_only"), poolTotal = pool.optLong("total_bytes"),
        poolUsed = pool.optLong("used_bytes"), poolPct = pool.optDouble("used_pct", 0.0),
        poolCorruption = pool.optLong("corruption_total"), poolDevice = pool.optString("device"),
        poolStats = poolStats, temps = temps, fans = fans, raid = raid, disks = disks, smart = smart,
        containers = containers, alerts = alerts,
        bcache = Bcache(
            bc.optBoolean("present"), bc.optString("cache_mode"), bc.optString("state"),
            bc.optLong("cache_hits"), bc.optLong("cache_misses"),
            bc.optDouble("hit_ratio", 0.0), bc.optDouble("available_percent", 0.0),
        ),
        power = Power(power.optDouble("package_watts", 0.0), powerParts),
        gpus = gpus,
        netStats = NetStats(
            ns.optLong("curr_estab"), ns.optInt("tcp_time_wait"), ns.optInt("tcp_in_use"),
            ns.optInt("sockets_used"), ns.optDouble("retrans_pct", 0.0), ns.optLong("retrans_segs"),
            ns.optInt("conntrack_used"), ns.optInt("conntrack_max"), ns.optInt("arp_entries"),
            ns.optInt("routes"), ns.optLong("listen_overflows"),
        ),
        system = SystemInfo(
            system.optBoolean("ntp_synced"), system.optString("timezone"), failed,
            system.optLong("ctxt"), system.optInt("procs_running"), system.optInt("procs_blocked"),
        ),
        edacCe = edac.optLong("corrected"), edacUe = edac.optLong("uncorrected"),
        dockerVersion = docker.optString("version"), dockerRunning = docker.optInt("running"),
        dockerStopped = docker.optInt("stopped"), images = docker.optInt("images"),
        psiCpu = psi("cpu"), psiMem = psi("memory"), psiIo = psi("io"),
        processes = processes, net = net, mounts = mounts, btrfsDf = btrfsDf, scrub = scrub,
        subvolumes = root.optInt("subvolumes"),
        actionsEnabled = watchdog.optBoolean("actions_enabled"),
        rebootEnabled = watchdog.optBoolean("reboot_enabled"),
        version = watchdog.optString("version"),
        logSources = logSources,
        thermalZones = thermalZones,
        throttle = throttle,
    )
}
