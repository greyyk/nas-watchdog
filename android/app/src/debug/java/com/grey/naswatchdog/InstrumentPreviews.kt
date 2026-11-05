package com.grey.naswatchdog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.grey.naswatchdog.charts.HistoryRange
import com.grey.naswatchdog.feature.CpuDetailScreen
import com.grey.naswatchdog.feature.WatchdogOverview
import com.grey.naswatchdog.ui.Bg
import com.grey.naswatchdog.ui.WatchdogTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.sin

private fun previewIso(epoch: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(epoch))

private fun previewTemp(
    key: String,
    label: String,
    c: Double,
    warn: Double,
    crit: Double,
    category: String,
    source: String = "hwmon",
    chip: String = "",
    sensor: String = "",
    device: String = "",
    coreType: String = "",
    cpus: List<Int> = emptyList(),
    hardwareCrit: Double? = null,
    alarms: List<String> = emptyList(),
): JSONObject = JSONObject()
    .put("key", key).put("label", label).put("c", c).put("warn", warn).put("crit", crit)
    .put("category", category).put("source", source).put("chip", chip).put("sensor", sensor)
    .put("device", device).put("core_type", coreType).put("cpus", JSONArray(cpus))
    .put("limits", JSONObject()
        .put("min_c", 0.0).put("max_c", hardwareCrit ?: crit)
        .put("crit_c", hardwareCrit ?: crit))
    .put("alarms", JSONArray(alarms))

private fun previewTemperatures(state: String): JSONArray {
    val packageTemp = when (state) {
        "critical" -> 104.0
        "warning" -> 92.0
        else -> 51.8
    }
    val temps = JSONArray()
        .put(previewTemp(
            "cpu", "CPU package", packageTemp, 90.0, 100.0, "cpu_package",
            chip = "coretemp-isa-0000", sensor = "temp1", hardwareCrit = 105.0,
            alarms = if (state == "critical") listOf("crit_alarm") else emptyList(),
        ))
    for (core in 0 until 14) {
        val coreType = if (core < 6) "performance" else "efficiency"
        val offset = if (core == 0) 3.0 else -(core % 7) * 1.3
        temps.put(previewTemp(
            "cpu_core_$core", "Core $core", packageTemp + offset, 90.0, 100.0, "cpu_core",
            chip = "coretemp-isa-0000", sensor = "temp${core + 2}", coreType = coreType,
            cpus = listOf(core), hardwareCrit = 105.0,
            alarms = if (state == "critical" && core == 0) listOf("crit_alarm") else emptyList(),
        ))
    }
    temps.put(previewTemp("dimm0", "DIMM 0", if (state == "warning") 58.5 else 43.5, 60.0, 70.0, "dimm", chip = "spd5118-i2c-0-50", sensor = "temp1"))
    temps.put(previewTemp("dimm1", "DIMM 1", if (state == "warning") 61.0 else 41.8, 60.0, 70.0, "dimm", chip = "spd5118-i2c-0-51", sensor = "temp1"))
    temps.put(previewTemp("nvme0", "NVMe 0 composite", if (state == "warning") 72.0 else 49.2, 70.0, 85.0, "nvme", chip = "nvme-pci-0100", sensor = "temp1", device = "/dev/nvme0", hardwareCrit = 89.0))
    temps.put(previewTemp("nvme0_sensor_1", "NVMe 0 sensor 1", if (state == "warning") 74.0 else 51.4, 70.0, 85.0, "nvme", chip = "nvme-pci-0100", sensor = "temp2", device = "/dev/nvme0", hardwareCrit = 89.0))
    temps.put(previewTemp("nvme1", "NVMe 1 composite", 47.6, 70.0, 85.0, "nvme", chip = "nvme-pci-0200", sensor = "temp1", device = "/dev/nvme1", hardwareCrit = 89.0))
    temps.put(previewTemp("nvme1_sensor_1", "NVMe 1 sensor 1", 46.3, 70.0, 85.0, "nvme", chip = "nvme-pci-0200", sensor = "temp2", device = "/dev/nvme1", hardwareCrit = 89.0))
    temps.put(previewTemp("board", "Board", 39.0, 60.0, 75.0, "board", chip = "acpitz", sensor = "temp1"))
    for (disk in 0 until 3) {
        temps.put(previewTemp(
            "sd${'a' + disk}", "SATA /dev/sd${'a' + disk}", 38.0 + disk * 1.4, 50.0, 60.0, "hdd",
            source = "smart", device = "/dev/sd${'a' + disk}",
        ))
    }
    return temps
}

private fun previewZones(state: String): JSONArray = JSONArray().apply {
    val labels = listOf("x86_pkg_temp", "acpitz", "pch_cometlake", "iwlwifi_1", "nvme")
    labels.forEachIndexed { index, label ->
        val disabled = index == 3
        put(JSONObject()
            .put("key", "zone_thermal_zone$index").put("label", label)
            .put("c", if (state == "critical" && index == 0) 102.0 else 43.0 + index * 2.3)
            .put("mode", if (disabled) "disabled" else "enabled")
            .put("policy", if (index % 2 == 0) "step_wise" else "user_space")
            .put("trips", if (disabled) JSONArray().put(JSONObject().put("index", 0).put("type", "disabled")) else JSONArray()
                .put(JSONObject().put("index", 0).put("type", "passive").put("temp_c", 85.0 + index).put("hyst_c", 2.0))
                .put(JSONObject().put("index", 1).put("type", "critical").put("temp_c", 100.0 + index).put("hyst_c", 0.0))))
    }
}

private fun previewFans(): JSONArray = JSONArray()
    .put(JSONObject().put("key", "zettos_pwm_fan_fan1").put("label", "CPU fan").put("rpm", 1260).put("pwm", 144))
    .put(JSONObject().put("key", "zettos_pwm_fan_fan2").put("label", "Intake fan").put("rpm", 940).put("pwm", 112))
    .put(JSONObject().put("key", "zettos_pwm_fan_fan3").put("label", "Exhaust fan").put("rpm", 1105).put("pwm", 132))

internal fun previewStats(state: String = "healthy", sensors: Boolean = true): Stats {
    val now = System.currentTimeMillis()
    val alerts = when (state) {
        "critical" -> JSONArray()
            .put(JSONObject()
                .put("id", "pool_readonly").put("severity", "critical")
                .put("title", "Pool filesystem is READ-ONLY")
                .put("detail", "The storage pool is mounted read-only. Applications writing to the pool are failing. A clean reboot is the safe recovery.")
                .put("since", previewIso(now - 240_000)).put("active", true))
            .put(JSONObject()
                .put("id", "temp_cpu").put("severity", "critical")
                .put("title", "CPU package is 104.0°C")
                .put("detail", "Temperature crossed the 100°C critical threshold. Inspect cooling immediately.")
                .put("since", previewIso(now - 90_000)).put("active", true))
        "warning" -> JSONArray().put(JSONObject()
            .put("id", "temp_cpu").put("severity", "warning")
            .put("title", "CPU package is 92.0°C")
            .put("detail", "Temperature crossed the 90°C warning threshold. Improve airflow and inspect the cooling system.")
            .put("since", previewIso(now - 600_000)).put("active", true))
        else -> JSONArray()
    }
    val root = JSONObject()
        .put("host", JSONObject().put("hostname", "zettos-nas").put("os", "ZettOS 2026.11")
            .put("uptime_s", 1_546_520).put("load1", 1.26).put("load5", 1.08).put("load15", 0.91))
        .put("cpu", JSONObject().put("usage_pct", if (state == "warning") 84.3 else 18.4)
            .put("cores", 14).put("governor", "schedutil").put("max_freq_mhz", 4_800)
            .put("throttle", JSONObject().put("package_count", if (state == "healthy") 2 else 19)
                .put("package_total_ms", if (state == "healthy") 340 else 18_200)
                .put("core_count", if (state == "healthy") 4 else 43)
                .put("core_total_ms", if (state == "healthy") 820 else 27_500))
            .put("per_core_detail", JSONArray().apply {
                for (i in 0 until 14) put(JSONObject().put("id", i).put("usage", 8.0 + i * 4.1).put("freq_mhz", if (i < 6) 4_200 else 1_800))
            }))
        .put("memory", JSONObject().put("used_pct", 56.2).put("used", 18_500_000_000L)
            .put("total", 33_000_000_000L).put("available", 14_000_000_000L))
        .put("pool", JSONObject().put("read_only", state == "critical").put("used_pct", 61.2)
            .put("used_bytes", 20_600_000_000_000L).put("total_bytes", 33_700_000_000_000L))
        .put("temps", if (sensors) previewTemperatures(state) else JSONArray())
        .put("thermal_zones", if (sensors) previewZones(state) else JSONArray())
        .put("fans", if (sensors) previewFans() else JSONArray())
        .put("smart", JSONArray().apply {
            for (disk in 0 until 3) put(JSONObject()
                .put("device", "/dev/sd${'a' + disk}").put("model", "SATA HDD ${disk + 1}")
                .put("health", "PASSED").put("temperature", 38.0 + disk * 1.4)
                .put("temperature_min_c", 31.0 + disk).put("temperature_max_c", 44.0 + disk))
        })
        .put("net", JSONArray().put(JSONObject().put("name", "eth0").put("rx_rate", 18_400_000.0).put("tx_rate", 3_600_000.0)))
        .put("disks", JSONArray().put(JSONObject().put("name", "nvme0n1").put("read_rate", 7_400_000.0).put("write_rate", 9_200_000.0)))
        .put("containers", JSONArray().put(JSONObject().put("name", "watchdog").put("state", "running").put("health", "healthy"))
            .put(JSONObject().put("name", "media").put("state", "running").put("health", "healthy")))
        .put("power", JSONObject().put("package_watts", if (state == "warning") 88.0 else 29.4))
        .put("docker", JSONObject().put("version", "29.2.1"))
        .put("watchdog", JSONObject().put("actions_enabled", true))
        .put("alerts", alerts)
    return parseStatsJson(root.toString())
}

private fun previewHistory(range: HistoryRange, state: String = "healthy"): HistoryUiState {
    val now = System.currentTimeMillis()
    val duration = when (range) {
        HistoryRange.FIVE_MINUTES -> 5L
        HistoryRange.ONE_HOUR -> 60L
        HistoryRange.SIX_HOURS -> 360L
        HistoryRange.TWENTY_FOUR_HOURS -> 1_440L
    } * 60_000L
    val packageBase = when (state) {
        "critical" -> 103.0
        "warning" -> 91.0
        else -> 51.0
    }
    val tempBases = buildMap {
        put("cpu", packageBase)
        for (core in 0 until 14) put("cpu_core_$core", packageBase - (core % 7) * 1.2)
        put("dimm0", if (state == "warning") 58.0 else 43.0)
        put("dimm1", if (state == "warning") 60.0 else 41.0)
        put("nvme0", 49.0)
        put("nvme0_sensor_1", 51.0)
        put("nvme1", 47.0)
        put("nvme1_sensor_1", 46.0)
        put("board", 39.0)
        put("sda", 38.0)
        put("sdb", 39.4)
        put("sdc", 40.8)
        for (zone in 0 until 5) put("zone_thermal_zone$zone", 43.0 + zone * 2.3)
    }
    val fanBases = mapOf(
        "zettos_pwm_fan_fan1" to FanHistory(1260, 144),
        "zettos_pwm_fan_fan2" to FanHistory(940, 112),
        "zettos_pwm_fan_fan3" to FanHistory(1105, 132),
    )
    val samples = (0..60).map { index ->
        val wave = sin(index / 5.2)
        val cpu = if (state != "healthy") 76.0 + wave * 12.0 else 18.0 + wave * 7.0 + (index % 9) * 1.2
        val epoch = now - duration + (duration * index / 60)
        val temperatures = tempBases.mapValues { (key, value) ->
            val multiplier = if (key.startsWith("zone_")) 0.5 else 1.0
            value + wave * 1.8 * multiplier
        }.toMutableMap()
        val fans = fanBases.mapValues { (_, value) ->
            FanHistory((value.rpm + wave * 90).toInt(), (value.pwm + wave * 12).toInt().coerceIn(0, 255))
        }.toMutableMap()
        if (index == 31) {
            temperatures.remove("nvme0_sensor_1")
            fans.remove("zettos_pwm_fan_fan2")
        }
        Sample(
            ts = previewIso(epoch),
            cpu = cpu,
            memPct = 55.0 + wave,
            load1 = 1.0 + wave / 3,
            netRx = 12_000_000.0 + wave * 3_000_000,
            netTx = 4_000_000.0 + wave,
            diskRead = 5_000_000.0 + wave * 2_000_000,
            diskWrite = 9_000_000.0 + wave * 2_000_000,
            temps = temperatures,
            poolPct = 61.2,
            fans = fans,
        )
    }
    return HistoryUiState(samples = samples, receivedAt = now)
}

internal fun previewTelemetry(
    state: String = "healthy",
    feedStatus: FeedStatus = FeedStatus.LIVE,
    hasHistory: Boolean = true,
    sensors: Boolean = true,
): TelemetryUiState {
    val stats = previewStats(state, sensors)
    val history = if (hasHistory && sensors) previewHistory(HistoryRange.ONE_HOUR, state) else HistoryUiState()
    return TelemetryUiState(
        snapshot = TelemetrySnapshot(stats, System.currentTimeMillis(), cached = feedStatus == FeedStatus.CACHED),
        feedStatus = feedStatus,
        error = if (feedStatus != FeedStatus.LIVE) "The server took too long to respond. Showing retained telemetry." else null,
        histories = mapOf(HistoryRange.ONE_HOUR to history),
    )
}

@Composable
internal fun PreviewFrame(content: @Composable () -> Unit) {
    WatchdogTheme {
        Surface(Modifier.fillMaxSize(), color = Bg) { content() }
    }
}

@Preview(name = "Overview · live", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun OverviewPreview() {
    PreviewFrame { WatchdogOverview(previewTelemetry(), {}, {}, {}) }
}

@Preview(name = "Overview · warning", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun OverviewWarningPreview() {
    PreviewFrame { WatchdogOverview(previewTelemetry("warning"), {}, {}, {}) }
}

@Preview(name = "Overview · critical cached", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun OverviewCriticalPreview() {
    PreviewFrame { WatchdogOverview(previewTelemetry("critical", FeedStatus.CACHED), {}, {}, {}) }
}

@Preview(name = "CPU instrument · live", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun CpuDetailPreview() {
    val telemetry = previewTelemetry()
    PreviewFrame {
        CpuDetailScreen(previewStats(), telemetry, HistoryRange.ONE_HOUR, {})
    }
}

@Preview(name = "CPU instrument · large text", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
private fun CpuDetailLargeFontPreview() {
    val telemetry = previewTelemetry("warning")
    PreviewFrame { CpuDetailScreen(previewStats("warning"), telemetry, HistoryRange.ONE_HOUR, {}) }
}
