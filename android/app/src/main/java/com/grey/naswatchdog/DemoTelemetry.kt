package com.grey.naswatchdog

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.sin

/** Only this reserved, unroutable URL selects synthetic data; ordinary URLs never enter this path. */
const val DEMO_SERVER_URL = "https://demo.naswatchdog.invalid"
private const val DEMO_TOKEN = "synthetic-demo"

fun isDemoConnection(baseUrl: String): Boolean = baseUrl == DEMO_SERVER_URL

private fun demoIso(epoch: Long): String = SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US,
).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epoch))

private fun demoTemp(
    key: String,
    label: String,
    c: Double,
    warn: Double,
    crit: Double,
    category: String,
    device: String = "",
    coreType: String = "",
    core: Int? = null,
) = JSONObject()
    .put("key", key).put("label", label).put("c", c)
    .put("warn", warn).put("crit", crit).put("category", category)
    .put("source", if (category == "hdd") "smart" else "hwmon")
    .put("chip", "demo-sensors").put("sensor", key)
    .put("device", device).put("core_type", coreType)
    .put("cpus", JSONArray().apply { core?.let(::put) })
    .put("limits", JSONObject().put("max_c", crit))
    .put("alarms", JSONArray())

private fun demoTemperatures(overheat: Boolean) = JSONArray().apply {
    val cpu = if (overheat) 92.4 else 53.0
    put(demoTemp("cpu", "CPU package", cpu, 90.0, 100.0, "cpu_package"))
    for (core in 0 until 14) {
        put(demoTemp(
            "cpu_core_$core", "Core $core", cpu - (core % 7) * 1.2, 90.0, 100.0,
            "cpu_core", coreType = if (core < 6) "performance" else "efficiency", core = core,
        ))
    }
    put(demoTemp("dimm0", "DIMM 0", if (overheat) 58.0 else 43.5, 60.0, 70.0, "dimm"))
    put(demoTemp("dimm1", "DIMM 1", 42.0, 60.0, 70.0, "dimm"))
    for (controller in 0 until 2) {
        put(demoTemp("nvme$controller", "NVMe $controller composite", if (overheat) 74.0 else 49.0 + controller, 70.0, 85.0, "nvme", "/dev/nvme$controller"))
        put(demoTemp("nvme${controller}_sensor_1", "NVMe $controller sensor 1", if (overheat) 72.0 else 51.0 + controller, 70.0, 85.0, "nvme", "/dev/nvme$controller"))
    }
    put(demoTemp("board", "Board", 39.0, 60.0, 75.0, "board"))
    for (disk in 0 until 6) {
        put(demoTemp("sd${'a' + disk}", "SATA /dev/sd${'a' + disk}", 36.0 + disk, 50.0, 60.0, "hdd", "/dev/sd${'a' + disk}"))
    }
}

private fun demoAlerts() = JSONArray().apply {
    put(JSONObject()
        .put("id", "demo_temperature_warning")
        .put("severity", "warning")
        .put("title", "DEMO · Example thermal warning")
        .put("detail", "This is a synthetic notification from the in-app demo, not a real NAS health event.")
        .put("since", demoIso(System.currentTimeMillis() - 120_000))
        .put("active", true))
}

private fun demoRoot(): JSONObject {
    val now = System.currentTimeMillis()
    val overheat = true // Deterministic synthetic warning; never a host reading.
    val root = JSONObject()
        .put("ts", demoIso(now))
        .put("host", JSONObject().put("hostname", "Demo NAS · synthetic data")
            .put("os", "Synthetic demo (no NAS contacted)").put("uptime_s", 1_250_000)
            .put("load1", 1.2).put("load5", 0.94).put("load15", 0.83))
        .put("cpu", JSONObject().put("model", "Synthetic processor")
            .put("usage_pct", if (overheat) 84.0 else 26.0).put("cores", 14)
            .put("governor", "demo").put("max_freq_mhz", 4_800)
            .put("throttle", JSONObject().put("package_count", if (overheat) 12 else 0).put("core_count", 0))
            .put("per_core_detail", JSONArray().apply {
                for (core in 0 until 14) put(JSONObject().put("id", core).put("usage", 10.0 + core * 3).put("freq_mhz", 2000 + core * 100))
            }))
        .put("memory", JSONObject().put("used_pct", 54.6).put("used", 18_000_000_000L)
            .put("total", 33_000_000_000L).put("available", 15_000_000_000L))
        .put("pool", JSONObject().put("read_only", false).put("used_pct", 61.2)
            .put("used_bytes", 20_600_000_000_000L).put("total_bytes", 33_700_000_000_000L))
        .put("btrfs", JSONObject().put("corrupt_count", 0)
            .put("df", JSONArray()).put("scrub", JSONObject().put("state", "idle")))
        .put("temps", demoTemperatures(overheat))
        .put("fans", JSONArray()
            .put(JSONObject().put("key", "fan_cpu").put("label", "Demo CPU fan").put("rpm", 1260).put("pwm", 144))
            .put(JSONObject().put("key", "fan_intake").put("label", "Demo intake fan").put("rpm", 940).put("pwm", 112))
            .put(JSONObject().put("key", "fan_exhaust").put("label", "Demo exhaust fan").put("rpm", 1105).put("pwm", 132)))
        .put("thermal_zones", JSONArray().put(JSONObject().put("key", "zone_demo").put("label", "Example thermal zone")
            .put("c", if (overheat) 91.0 else 52.0).put("warn", 90.0).put("crit", 100.0)
            .put("mode", "enabled").put("policy", "demo").put("trips", JSONArray()
                .put(JSONObject().put("index", 0).put("type", "passive").put("temp_c", 90.0).put("hyst_c", 2.0)))))
        .put("smart", JSONArray().apply {
            for (disk in 0 until 6) put(JSONObject().put("device", "/dev/sd${'a' + disk}")
                .put("model", "Synthetic SATA ${disk + 1}").put("health", "PASSED")
                .put("temperature", 36.0 + disk))
            for (disk in 0 until 2) put(JSONObject().put("device", "/dev/nvme$disk")
                .put("model", "Synthetic NVMe ${disk + 1}").put("health", "PASSED")
                .put("temperature", 49.0 + disk))
        })
        .put("disks", JSONArray().put(JSONObject().put("name", "sda").put("read_rate", 7_400_000.0).put("write_rate", 9_200_000.0)))
        .put("net", JSONArray().put(JSONObject().put("name", "demo-lan").put("rx_rate", 18_400_000.0).put("tx_rate", 3_600_000.0)))
        .put("net_stats", JSONObject())
        .put("containers", JSONArray()
            .put(JSONObject().put("name", "demo-media").put("state", "running").put("health", "healthy")
                .put("image", "example.invalid/media:synthetic").put("cpu_pct", 4.2).put("mem_used", 340_000_000L))
            .put(JSONObject().put("name", "demo-stopped").put("state", "exited").put("health", "none")
                .put("image", "example.invalid/other:synthetic")))
        .put("docker", JSONObject().put("version", "Synthetic inventory"))
        .put("power", JSONObject().put("package_watts", if (overheat) 88.0 else 29.4))
        .put("logs", JSONArray().put(JSONObject().put("id", "demo").put("path", "/synthetic/demo.log").put("hits", 4)))
        .put("watchdog", JSONObject().put("actions_enabled", false).put("reboot_enabled", false))
        .put("version", "1.5.0 · synthetic demo")
        .put("alerts", demoAlerts())
    return root
}

private fun demoHistory(): JSONObject {
    val now = System.currentTimeMillis()
    val overheat = true // Deterministic synthetic warning; never a host reading.
    return JSONObject().put("samples", JSONArray().apply {
        for (index in 0..90) {
            val wave = sin(index / 6.0)
            val temperatures = JSONObject().apply {
                put("cpu", (if (overheat) 91.0 else 52.0) + wave * 2)
                for (core in 0 until 14) put("cpu_core_$core", 51.0 - (core % 7) * 1.2 + wave * 2)
                put("dimm0", 43.0 + wave)
                put("dimm1", 42.0 + wave)
                put("nvme0", (if (overheat) 73.0 else 49.0) + wave)
                put("nvme0_sensor_1", 51.0 + wave)
                put("nvme1", 50.0 + wave)
                put("nvme1_sensor_1", 52.0 + wave)
                put("board", 39.0)
                for (disk in 0 until 6) put("sd${'a' + disk}", 36.0 + disk)
                if (index == 41) remove("nvme0_sensor_1")
            }
            put(JSONObject().put("ts", demoIso(now - (90 - index) * 30_000L))
                .put("cpu", 28.0 + wave * 8).put("mem_pct", 55.0 + wave)
                .put("load1", 1.0 + wave / 3).put("net_rx", 12_000_000.0 + wave * 2_000_000)
                .put("net_tx", 4_000_000.0 + wave * 200_000)
                .put("disk_read", 5_000_000.0).put("disk_write", 9_000_000.0)
                .put("temps", temperatures)
                .put("fans", JSONObject().put("fan_cpu", JSONObject().put("rpm", 1230 + (90 * wave).toInt()).put("pwm", 144))
                    .put("fan_intake", JSONObject().put("rpm", 960 + (70 * wave).toInt()).put("pwm", 112))
                    .put("fan_exhaust", JSONObject().put("rpm", 1100 + (60 * wave).toInt()).put("pwm", 132)))
                .put("pool_pct", 61.2))
        }
    })
}

private fun demoLogLines() = JSONObject().put("lines", JSONArray()
    .put("DEMO: synthetic service ready; no host log was read")
    .put("DEMO: actions are disabled; nothing can be sent to a NAS")
    .put("DEMO: this entry demonstrates searchable log text")
    .put("DEMO: all device names and readings are synthetic"))

internal fun demoApiResponse(url: String, token: String): String? {
    if (!url.startsWith("$DEMO_SERVER_URL/")) return null
    if (token != DEMO_TOKEN) throw ApiException(401, "In-app demo authentication was not selected")
    return when {
        url.startsWith("$DEMO_SERVER_URL/api/v1/stats") -> demoRoot().toString()
        url.startsWith("$DEMO_SERVER_URL/api/v1/history") -> demoHistory().toString()
        url.startsWith("$DEMO_SERVER_URL/api/v1/events") -> demoRoot().let { stats ->
            JSONObject().put("events", JSONArray().apply {
                for (index in 0 until stats.getJSONArray("alerts").length()) put(JSONObject()
                    .put("kind", "fired").put("ts", demoIso(System.currentTimeMillis()))
                    .put("alert", stats.getJSONArray("alerts").getJSONObject(index)))
            }).toString()
        }
        url.startsWith("$DEMO_SERVER_URL/api/v1/containers/logs") -> JSONObject()
            .put("text", "DEMO: no Docker container was read. Synthetic logs for a UI preview only.").toString()
        url.startsWith("$DEMO_SERVER_URL/api/v1/logs") ||
            url.startsWith("$DEMO_SERVER_URL/api/v1/journal") ||
            url.startsWith("$DEMO_SERVER_URL/api/v1/dmesg") -> demoLogLines().toString()
        url.startsWith("$DEMO_SERVER_URL/api/v1/actions") -> JSONObject()
            .put("ok", false).put("message", "Synthetic demo: actions are disabled; no NAS was contacted.").toString()
        else -> throw ApiException(404, "Not part of the synthetic demo")
    }
}

internal suspend fun demoAlertStream(token: String, onAlerts: (List<Alert>) -> Unit) {
    if (token != DEMO_TOKEN) throw ApiException(401, "In-app demo authentication was not selected")
    while (currentCoroutineContext().isActive) {
        onAlerts(parseStatsJson(demoRoot().toString()).alerts)
        delay(5_000)
    }
}
