package com.grey.naswatchdog.feature

import com.grey.naswatchdog.Alert
import com.grey.naswatchdog.AlertEvent
import com.grey.naswatchdog.Core
import com.grey.naswatchdog.Sample
import com.grey.naswatchdog.Smart
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.Temp
import com.grey.naswatchdog.TemperatureLimits
import com.grey.naswatchdog.ThermalZone
import com.grey.naswatchdog.charts.ChartPoint
import com.grey.naswatchdog.ui.chartPoints
import com.grey.naswatchdog.ui.parseTimestampEpoch

/** Physical component categories only. Firmware zones deliberately have a separate category. */
enum class SensorCategory(val label: String) {
    CPU_PACKAGE("CPU package"),
    CPU_CORE("CPU cores"),
    MEMORY("Memory"),
    NVME("NVMe"),
    SATA("SATA / SMART"),
    BOARD("Board"),
    OTHER("Other components"),
    FIRMWARE_ZONE("Firmware thermal zones"),
}

data class ThermalSensor(
    val key: String,
    val label: String,
    val c: Double?,
    val warn: Double,
    val crit: Double,
    val category: SensorCategory,
    val source: String = "",
    val chip: String = "",
    val sensor: String = "",
    val device: String = "",
    val coreType: String = "",
    val logicalCpus: List<Int> = emptyList(),
    val limits: TemperatureLimits? = null,
    val alarms: List<String> = emptyList(),
)

enum class ThermalHealth { NO_DATA, NORMAL, WARNING, CRITICAL }

data class CriticalHeadroom(
    val sensor: ThermalSensor,
    val limitC: Double,
    val headroomC: Double,
)

data class CoreTemperature(
    val sensor: ThermalSensor,
    val physicalCoreId: Int?,
    val logicalCpus: List<Int>,
    val loadPct: Double?,
    val minFreqMHz: Long?,
    val maxFreqMHz: Long?,
)

data class CoreTypeTemperature(
    val coreType: String,
    val hottest: ThermalSensor,
    val averageC: Double,
)

data class NvmeTemperatureGroup(
    val id: String,
    val label: String,
    val sensors: List<ThermalSensor>,
)

data class SensorOptionGroup(
    val category: SensorCategory,
    val sensors: List<ThermalSensor>,
)

data class ThermalModel(
    val physical: List<ThermalSensor>,
    val zones: List<ThermalZone>,
    val cores: List<CoreTemperature>,
    val nvmeGroups: List<NvmeTemperatureGroup>,
    val hottest: ThermalSensor?,
    val cpuPackage: ThermalSensor?,
    val hottestCore: ThermalSensor?,
    val coreSpreadC: Double?,
    val criticalHeadroom: CriticalHeadroom?,
    val performanceCores: CoreTypeTemperature?,
    val efficiencyCores: CoreTypeTemperature?,
    val health: ThermalHealth,
) {
    val sensors: List<ThermalSensor> = physical + zones.mapNotNull(ThermalZone::toThermalSensor)

    val pickerGroups: List<SensorOptionGroup>
        get() = SensorCategory.entries.mapNotNull { category ->
            sensors.filter { it.category == category }
                .takeIf { it.isNotEmpty() }
                ?.let { SensorOptionGroup(category, it) }
        }
}

fun buildThermalModel(stats: Stats): ThermalModel {
    val physical = stats.temps.mapNotNull(Temp::toThermalSensor)
    val cores = physical.filter { it.category == SensorCategory.CPU_CORE }
        .sortedWith(compareBy({ physicalCoreId(it) ?: Int.MAX_VALUE }, { it.key }))
        .map { coreTemperature(it, stats.perCore) }
    val headroom = physical.mapNotNull(::criticalHeadroom).minByOrNull { it.headroomC }
    val activeThermalAlerts = stats.alerts.filter { it.active && isThermalAlert(it) }
    val sensorHealth = when {
        physical.any { sensorHealth(it) == ThermalHealth.CRITICAL } -> ThermalHealth.CRITICAL
        physical.any { sensorHealth(it) == ThermalHealth.WARNING } -> ThermalHealth.WARNING
        else -> ThermalHealth.NORMAL
    }
    val alertHealth = when {
        activeThermalAlerts.any { it.severity.equals("critical", true) } -> ThermalHealth.CRITICAL
        activeThermalAlerts.any { it.severity.equals("warning", true) } -> ThermalHealth.WARNING
        else -> ThermalHealth.NORMAL
    }
    return ThermalModel(
        physical = physical,
        zones = stats.thermalZones,
        cores = cores,
        nvmeGroups = groupNvmeSensors(physical.filter { it.category == SensorCategory.NVME }),
        hottest = physical.maxByOrNull { it.c ?: Double.NEGATIVE_INFINITY },
        cpuPackage = physical.firstOrNull { it.key == "cpu" }
            ?: physical.firstOrNull { it.category == SensorCategory.CPU_PACKAGE },
        hottestCore = cores.map { it.sensor }.maxByOrNull { it.c ?: Double.NEGATIVE_INFINITY },
        coreSpreadC = cores.mapNotNull { it.sensor.c }.let { values ->
            if (values.size >= 2) values.max() - values.min() else null
        },
        criticalHeadroom = headroom,
        performanceCores = summarizeCoreType(cores, "performance"),
        efficiencyCores = summarizeCoreType(cores, "efficiency"),
        health = when {
            physical.isEmpty() && stats.thermalZones.isEmpty() && activeThermalAlerts.isEmpty() -> ThermalHealth.NO_DATA
            sensorHealth == ThermalHealth.CRITICAL || alertHealth == ThermalHealth.CRITICAL -> ThermalHealth.CRITICAL
            sensorHealth == ThermalHealth.WARNING || alertHealth == ThermalHealth.WARNING -> ThermalHealth.WARNING
            else -> ThermalHealth.NORMAL
        },
    )
}

fun Temp.toThermalSensor(): ThermalSensor? {
    if (key.isBlank() || !c.isFinite()) return null
    return ThermalSensor(
        key = key,
        label = label.ifBlank { key },
        c = c,
        warn = warn,
        crit = crit,
        category = inferSensorCategory(this),
        source = source,
        chip = chip,
        sensor = sensor,
        device = device,
        coreType = coreType,
        logicalCpus = cpus.filter { it >= 0 }.distinct().sorted(),
        limits = limits,
        alarms = alarms.filter { it.isNotBlank() },
    )
}

fun ThermalZone.toThermalSensor(): ThermalSensor? {
    if (key.isBlank()) return null
    return ThermalSensor(
        key = key,
        label = label.ifBlank { key },
        c = c?.takeIf { it.isFinite() },
        warn = warn,
        crit = crit,
        category = SensorCategory.FIRMWARE_ZONE,
    )
}

fun inferSensorCategory(temp: Temp): SensorCategory {
    when (temp.category.trim().lowercase()) {
        "cpu_package" -> return SensorCategory.CPU_PACKAGE
        "cpu_core" -> return SensorCategory.CPU_CORE
        "dimm", "memory" -> return SensorCategory.MEMORY
        "nvme" -> return SensorCategory.NVME
        "hdd", "sata" -> return SensorCategory.SATA
        "board" -> return SensorCategory.BOARD
        "other" -> return SensorCategory.OTHER
    }
    val identity = "${temp.key} ${temp.label} ${temp.device}".lowercase()
    return when {
        temp.key == "cpu" || identity.contains("package") -> SensorCategory.CPU_PACKAGE
        identity.contains("cpu_core") || (identity.contains("core") && identity.contains("cpu")) ->
            SensorCategory.CPU_CORE
        identity.contains("dimm") || identity.contains("memory") -> SensorCategory.MEMORY
        identity.contains("nvme") -> SensorCategory.NVME
        temp.source.equals("smart", true) || identity.contains("/dev/sd") || identity.contains("hdd") ->
            SensorCategory.SATA
        identity.contains("board") || identity.contains("acpitz") -> SensorCategory.BOARD
        else -> SensorCategory.OTHER
    }
}

fun warningThreshold(sensor: ThermalSensor): Double? = sensor.warn.validThreshold()

/** The most conservative configured or hardware critical threshold reported for a sensor. */
fun criticalThreshold(sensor: ThermalSensor): Double? = listOfNotNull(
    sensor.crit.validThreshold(),
    sensor.limits?.critC.validThreshold(),
    sensor.limits?.emergencyC.validThreshold(),
).minOrNull()

fun hardwareCriticalThresholds(sensor: ThermalSensor): List<Pair<String, Double>> = listOfNotNull(
    sensor.limits?.critC.validThreshold()?.let { "Hardware critical" to it },
    sensor.limits?.emergencyC.validThreshold()?.let { "Hardware emergency" to it },
).distinctBy { it.second }

fun criticalHeadroom(sensor: ThermalSensor): CriticalHeadroom? {
    val current = sensor.c?.takeIf { it.isFinite() } ?: return null
    val threshold = criticalThreshold(sensor) ?: return null
    return CriticalHeadroom(sensor, threshold, threshold - current)
}

fun sensorHealth(sensor: ThermalSensor): ThermalHealth {
    val current = sensor.c
    val criticalAlarm = sensor.alarms.any {
        val name = it.lowercase()
        name == "alarm" || name == "temp_alarm" || name.contains("crit") || name.contains("emerg")
    }
    return when {
        criticalAlarm || (current != null && criticalThreshold(sensor)?.let { current >= it } == true) ->
            ThermalHealth.CRITICAL
        sensor.alarms.isNotEmpty() ||
            (current != null && warningThreshold(sensor)?.let { current >= it } == true) -> ThermalHealth.WARNING
        else -> ThermalHealth.NORMAL
    }
}

fun selectThermalSensor(sensors: List<ThermalSensor>, requestedKey: String): ThermalSensor? =
    sensors.firstOrNull { it.key == requestedKey }
        ?: sensors.firstOrNull { it.category == SensorCategory.CPU_PACKAGE }
        ?: sensors.firstOrNull { it.category != SensorCategory.FIRMWARE_ZONE }
        ?: sensors.firstOrNull()

fun physicalCoreId(sensor: ThermalSensor): Int? =
    sensor.key.removePrefix("cpu_core_").toIntOrNull()
        ?: Regex("(?:^|\\D)(\\d+)$").find(sensor.sensor)?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex("(?:^|\\D)(\\d+)$").find(sensor.label)?.groupValues?.get(1)?.toIntOrNull()

fun coreTemperature(sensor: ThermalSensor, logicalCores: List<Core>): CoreTemperature {
    val associated = sensor.logicalCpus.mapNotNull { cpu -> logicalCores.firstOrNull { it.id == cpu } }
    val loads = associated.map { it.usage }.filter { it.isFinite() }
    val frequencies = associated.map { it.freq }.filter { it > 0L }
    return CoreTemperature(
        sensor = sensor,
        physicalCoreId = physicalCoreId(sensor),
        logicalCpus = sensor.logicalCpus,
        loadPct = loads.takeIf { it.isNotEmpty() }?.average()?.takeIf { it.isFinite() },
        minFreqMHz = frequencies.minOrNull(),
        maxFreqMHz = frequencies.maxOrNull(),
    )
}

fun groupNvmeSensors(sensors: List<ThermalSensor>): List<NvmeTemperatureGroup> = sensors
    .groupBy { sensor ->
        sensor.device.ifBlank { sensor.key.substringBefore("_sensor_") }.ifBlank { sensor.key }
    }
    .map { (id, readings) ->
        val index = id.substringAfter("nvme", "").filter(Char::isDigit)
        NvmeTemperatureGroup(
            id = id,
            label = when {
                id.startsWith("/dev/") -> id
                index.isNotEmpty() -> "NVMe $index"
                else -> id
            },
            sensors = readings.sortedBy { if (it.key.contains("_sensor_")) 1 else 0 },
        )
    }
    .sortedBy { it.id }

fun smartForSensor(sensor: ThermalSensor, smart: List<Smart>): Smart? =
    sensor.device.takeIf { it.isNotBlank() }?.let { device ->
        smart.firstOrNull {
            it.device == device ||
                sensor.category == SensorCategory.NVME && it.device.startsWith("${device}n")
        }
    }

fun summarizeCoreType(cores: List<CoreTemperature>, coreType: String): CoreTypeTemperature? {
    val matching = cores.filter { it.sensor.coreType.equals(coreType, true) }.map { it.sensor }
    val hottest = matching.maxByOrNull { it.c ?: Double.NEGATIVE_INFINITY } ?: return null
    val average = matching.mapNotNull { it.c }.takeIf { it.isNotEmpty() }?.average() ?: return null
    return CoreTypeTemperature(coreType, hottest, average)
}

data class ThermalHistoryStats(
    val currentC: Double?,
    val firstC: Double?,
    val latestC: Double?,
    val minimumC: Double?,
    val averageC: Double?,
    val maximumC: Double?,
    val changeC: Double?,
    val headroomC: Double?,
    val sampleCount: Int,
)

fun thermalHistoryStats(sensor: ThermalSensor, samples: List<Sample>): ThermalHistoryStats {
    val values = chartPoints(samples) { it.temps[sensor.key] }
        .asSequence()
        .filter { it.epochMillis >= 0L && it.value?.isFinite() == true }
        .sortedBy { it.epochMillis }
        .mapNotNull { it.value }
        .toList()
    val current = sensor.c?.takeIf { it.isFinite() }
    return ThermalHistoryStats(
        currentC = current,
        firstC = values.firstOrNull(),
        latestC = values.lastOrNull(),
        minimumC = values.minOrNull(),
        averageC = values.takeIf { it.isNotEmpty() }?.average()?.takeIf { it.isFinite() },
        maximumC = values.maxOrNull(),
        changeC = if (values.size >= 2) values.last() - values.first() else null,
        headroomC = criticalThreshold(sensor)?.let { threshold -> current?.let { threshold - it } },
        sampleCount = values.size,
    )
}

data class ThresholdRun(
    val currentMillis: Long?,
    val longestMillis: Long?,
    val currentStartMillis: Long?,
)

/**
 * Measures contiguous qualifying samples. Missing/non-finite values and backwards time split runs;
 * no duration is inferred across those gaps.
 */
fun contiguousThresholdRun(points: List<ChartPoint>, threshold: Double): ThresholdRun {
    if (!threshold.isFinite()) return ThresholdRun(null, null, null)
    var runStart: Long? = null
    var runEnd = 0L
    var longest: Long? = null
    var previousTimestamp: Long? = null

    fun finishRun() {
        val start = runStart ?: return
        val duration = (runEnd - start).coerceAtLeast(0L)
        longest = maxOf(longest ?: 0L, duration)
        runStart = null
    }

    points.forEach { point ->
        val value = point.value
        val connected = point.epochMillis >= 0L && value?.isFinite() == true &&
            previousTimestamp?.let { point.epochMillis > it } != false
        if (!connected) {
            finishRun()
            previousTimestamp = null
            return@forEach
        }
        if (value!! >= threshold) {
            if (runStart == null) runStart = point.epochMillis
            runEnd = point.epochMillis
            longest = maxOf(longest ?: 0L, (runEnd - runStart!!).coerceAtLeast(0L))
        } else {
            finishRun()
        }
        previousTimestamp = point.epochMillis
    }
    val currentStart = runStart
    return ThresholdRun(
        currentMillis = currentStart?.let { (runEnd - it).coerceAtLeast(0L) },
        longestMillis = longest,
        currentStartMillis = currentStart,
    )
}

fun temperatureThresholdRun(samples: List<Sample>, key: String, threshold: Double?): ThresholdRun? {
    val validThreshold = threshold?.takeIf { it.isFinite() } ?: return null
    return contiguousThresholdRun(chartPoints(samples) { it.temps[key] }, validThreshold)
}

data class FanHistorySummary(
    val points: List<ChartPoint>,
    val minimumRpm: Int?,
    val maximumRpm: Int?,
)

fun fanHistorySummary(samples: List<Sample>, key: String): FanHistorySummary {
    if (key.isBlank()) return FanHistorySummary(emptyList(), null, null)
    val points = chartPoints(samples) { sample ->
        sample.fans[key]?.rpm?.takeIf { it >= 0 }?.toDouble()
    }
    val values = points.mapNotNull { it.value?.takeIf(Double::isFinite) }
    return FanHistorySummary(
        points = points,
        minimumRpm = values.minOrNull()?.toInt(),
        maximumRpm = values.maxOrNull()?.toInt(),
    )
}

fun isThermalAlert(alert: Alert): Boolean {
    val id = alert.id.lowercase()
    val prose = "${alert.title} ${alert.detail}".lowercase()
    return id.startsWith("temp") || id.startsWith("thermal") || id.startsWith("fan") ||
        listOf("temperature", "thermal", "overheat", "throttl", "cooling", "fan rpm").any(prose::contains)
}

fun eventTimestamp(event: AlertEvent): String = event.ts.ifBlank { event.alert.since }

fun sortedThermalEvents(events: List<AlertEvent>): List<AlertEvent> = events
    .filter { isThermalAlert(it.alert) }
    .sortedWith(
        compareByDescending<AlertEvent> { parseTimestampEpoch(eventTimestamp(it)) ?: Long.MIN_VALUE }
            .thenByDescending { it.alert.id },
    )

private fun Double?.validThreshold(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }
