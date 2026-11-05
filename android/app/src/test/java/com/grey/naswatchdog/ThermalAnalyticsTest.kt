package com.grey.naswatchdog

import com.grey.naswatchdog.feature.SensorCategory
import com.grey.naswatchdog.feature.ThermalHealth
import com.grey.naswatchdog.feature.buildThermalModel
import com.grey.naswatchdog.feature.contiguousThresholdRun
import com.grey.naswatchdog.feature.criticalHeadroom
import com.grey.naswatchdog.feature.eventTimestamp
import com.grey.naswatchdog.feature.fanHistorySummary
import com.grey.naswatchdog.feature.groupNvmeSensors
import com.grey.naswatchdog.feature.inferSensorCategory
import com.grey.naswatchdog.feature.selectThermalSensor
import com.grey.naswatchdog.feature.sensorHealth
import com.grey.naswatchdog.feature.smartForSensor
import com.grey.naswatchdog.feature.sortedThermalEvents
import com.grey.naswatchdog.feature.temperatureThresholdRun
import com.grey.naswatchdog.feature.thermalHistoryStats
import com.grey.naswatchdog.feature.toThermalSensor
import com.grey.naswatchdog.charts.ChartPoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalAnalyticsTest {
    @Test
    fun `legacy temperature fields have safe optional defaults`() {
        val stats = parseStatsJson(
            """
            {
              "host": {"hostname": "legacy"},
              "temps": [{"key":"cpu","label":"CPU","c":52.5,"warn":90,"crit":100}],
              "fans": [{"key":"fan1","label":"Rear fan","rpm":840,"pwm":96}],
              "smart": [{"device":"/dev/sda","temperature":39}],
              "thermal_zones": [{"key":"zone_0","label":"acpitz","c":41}]
            }
            """.trimIndent(),
        )

        val cpu = stats.temps.single()
        assertEquals("", cpu.category)
        assertEquals("", cpu.coreType)
        assertTrue(cpu.cpus.isEmpty())
        assertTrue(cpu.alarms.isEmpty())
        assertNull(cpu.limits)
        assertEquals("fan1", stats.fans.single().key)
        assertNull(stats.smart.single().temperatureMinC)
        assertNull(stats.smart.single().temperatureMaxC)
        assertTrue(stats.thermalZones.single().trips.isEmpty())
        assertNull(stats.throttle)
        assertTrue(parseHistoryJson("""{"samples":[{"ts":"1970-01-01T00:00:00Z","temps":{"cpu":51}}]}""")
            .single().fans.isEmpty())
    }

    @Test
    fun `enhanced payload retains normalized metadata zones and smart bounds`() {
        val stats = parseStatsJson(
            """
            {
              "cpu": {"throttle":{"package_count":3,"package_total_ms":1200,"core_count":5,"core_total_ms":1800}},
              "temps": [{
                "key":"cpu_core_2", "label":"Core 2", "c":62.25, "warn":90, "crit":100,
                "category":"cpu_core", "source":"hwmon", "chip":"coretemp-isa-0000",
                "sensor":"temp3", "core_type":"performance", "cpus":[2,3,2,-1],
                "limits":{"min_c":10,"max_c":105,"lcrit_c":null,"crit_c":105,"emergency_c":"invalid"},
                "alarms":["temp3_alarm"]
              }, {"key":"bad","c":"invalid"}, {"label":"missing key","c":45}],
              "smart": [{"device":"/dev/sdb","temperature":40,"temperature_min_c":34,"temperature_max_c":47}],
              "thermal_zones": [{"key":"zone_thermal_zone0","label":"x86_pkg_temp","c":61.5,
                "mode":"enabled","policy":"step_wise","trips":[
                  {"index":0,"type":"passive","temp_c":85.5,"hyst_c":2},
                  {"index":1,"type":"disabled","hyst_c":-2},
                  {"index":-3,"type":"bad","temp_c":20}
                ]}],
              "fans": [{"key":"zettos_pwm_fan_fan1","label":"CPU fan","rpm":1260,"pwm":144}]
            }
            """.trimIndent(),
        )

        val core = stats.temps.single()
        assertEquals("cpu_core", core.category)
        assertEquals("performance", core.coreType)
        assertEquals(listOf(2, 3), core.cpus)
        assertEquals(105.0, core.limits?.critC!!, 0.0)
        assertNull(core.limits?.emergencyC)
        assertEquals(listOf("temp3_alarm"), core.alarms)
        assertEquals(34.0, stats.smart.single().temperatureMinC!!, 0.0)
        assertEquals(47.0, stats.smart.single().temperatureMaxC!!, 0.0)
        assertEquals(3L, stats.throttleCount)
        assertEquals(1800L, stats.throttle?.coreTotalMs)
        assertEquals(2, stats.thermalZones.single().trips.size)
        assertNull(stats.thermalZones.single().trips[1].tempC)
        assertNull(stats.thermalZones.single().trips[1].hystC)
    }

    @Test
    fun `history parsing keeps valid fan readings and rejects incomplete or invalid ones`() {
        val samples = parseHistoryJson(
            """
            {"samples":[{
              "ts":"1970-01-01T00:00:00Z",
              "temps":{"cpu":51,"dimm0":"bad"},
              "fans":{
                "fan1":{"rpm":800,"pwm":100},
                "fan_bad_rpm":{"rpm":-1,"pwm":100},
                "fan_bad_pwm":{"rpm":500,"pwm":300},
                "fan_missing":{"rpm":500}
              }
            }]}
            """.trimIndent(),
        )

        val sample = samples.single()
        assertEquals(mapOf("cpu" to 51.0), sample.temps)
        assertEquals(FanHistory(800, 100), sample.fans["fan1"])
        assertEquals(1, sample.fans.size)
    }

    @Test
    fun `old sensors are grouped without inventing missing category metadata`() {
        assertEquals(SensorCategory.CPU_PACKAGE, inferSensorCategory(sensor("cpu", "CPU")))
        assertEquals(SensorCategory.CPU_CORE, inferSensorCategory(sensor("cpu_core_7", "Core 7")))
        assertEquals(SensorCategory.MEMORY, inferSensorCategory(sensor("dimm1", "DIMM 1")))
        assertEquals(SensorCategory.NVME, inferSensorCategory(sensor("nvme0_sensor_1", "Sensor 1")))
        assertEquals(SensorCategory.SATA, inferSensorCategory(sensor("sda", "/dev/sda", source = "smart")))
        assertEquals(SensorCategory.BOARD, inferSensorCategory(sensor("board", "Board")))
        assertEquals(SensorCategory.OTHER, inferSensorCategory(sensor("mystery", "Unknown sensor")))
    }

    @Test
    fun `model excludes firmware zones from physical extrema and computes CPU headroom spread`() {
        val stats = statsWithSensors(
            sensor("cpu", "CPU package", 60.0),
            sensor("cpu_core_0", "Core 0", 72.0, category = "cpu_core", cpus = listOf(0, 1)),
            sensor("cpu_core_1", "Core 1", 66.0, category = "cpu_core", cpus = listOf(2, 3)),
            sensor("dimm0", "DIMM 0", 50.0),
        )
        val model = buildThermalModel(stats)

        assertEquals("cpu_core_0", model.hottest?.key)
        assertEquals("cpu", model.cpuPackage?.key)
        assertEquals(6.0, model.coreSpreadC!!, 0.0)
        assertEquals(28.0, model.criticalHeadroom?.headroomC!!, 0.0)
        assertEquals(5, model.sensors.size)
        assertEquals(ThermalHealth.NORMAL, model.health)
    }

    @Test
    fun `empty model and invalid temperatures do not fabricate component readings`() {
        val empty = buildThermalModel(statsWithSensors())
        assertEquals(ThermalHealth.NO_DATA, empty.health)
        assertNull(empty.hottest)
        assertNull(empty.criticalHeadroom)
        assertNull(empty.coreSpreadC)

        assertNull(sensor("bad", "Bad", Double.NaN).toThermalSensor())
        assertNull(criticalHeadroom(sensor("cpu", "CPU").toThermalSensor()!!.copy(c = Double.NaN)))
        val noThreshold = sensor("unknown", "Unknown", 42.0).toThermalSensor()!!
            .copy(warn = 0.0, crit = Double.NaN)
        assertNull(criticalHeadroom(noThreshold))
        assertNull(selectThermalSensor(emptyList(), "missing"))
    }

    @Test
    fun `headroom uses most conservative finite hardware or configured critical limit`() {
        val sensor = sensor("cpu", "CPU", 80.0).toThermalSensor()!!.copy(
            crit = 100.0,
            limits = TemperatureLimits(critC = 95.0, emergencyC = 110.0),
        )
        assertEquals(95.0, criticalHeadroom(sensor)?.limitC!!, 0.0)
        assertEquals(15.0, criticalHeadroom(sensor)?.headroomC!!, 0.0)
        val backstop = sensor("dimm0", "DIMM", 62.0).toThermalSensor()!!.copy(
            warn = 60.0,
            crit = 0.0,
            limits = TemperatureLimits(critC = 70.0),
        )
        assertEquals(ThermalHealth.WARNING, com.grey.naswatchdog.feature.sensorHealth(backstop))
        assertEquals(8.0, criticalHeadroom(backstop)?.headroomC!!, 0.0)
    }

    @Test
    fun `P and E core summaries use only explicitly identified physical cores`() {
        val stats = statsWithSensors(
            sensor("cpu_core_0", "Core 0", 70.0, category = "cpu_core", coreType = "performance"),
            sensor("cpu_core_1", "Core 1", 80.0, category = "cpu_core", coreType = "performance"),
            sensor("cpu_core_2", "Core 2", 50.0, category = "cpu_core", coreType = "efficiency"),
            sensor("cpu_core_3", "Core 3", 60.0, category = "cpu_core"),
        )
        val model = buildThermalModel(stats)

        assertEquals("cpu_core_1", model.performanceCores?.hottest?.key)
        assertEquals(75.0, model.performanceCores?.averageC!!, 0.0)
        assertEquals("cpu_core_2", model.efficiencyCores?.hottest?.key)
        assertEquals(50.0, model.efficiencyCores?.averageC!!, 0.0)
    }

    @Test
    fun `core load and frequency are associated through logical cpu ids`() {
        val root = JSONObject().put("cpu", JSONObject().put("per_core_detail", JSONArray()
            .put(JSONObject().put("id", 0).put("usage", 20.0).put("freq_mhz", 1200))
            .put(JSONObject().put("id", 1).put("usage", 60.0).put("freq_mhz", 2400))))
            .put("temps", JSONArray().put(JSONObject()
                .put("key", "cpu_core_0").put("label", "Core 0").put("c", 60.0)
                .put("category", "cpu_core").put("cpus", JSONArray().put(0).put(1))))
        val core = buildThermalModel(parseStatsJson(root.toString())).cores.single()

        assertEquals(40.0, core.loadPct!!, 0.0)
        assertEquals(1200L, core.minFreqMHz)
        assertEquals(2400L, core.maxFreqMHz)
        assertEquals(listOf(0, 1), core.logicalCpus)
    }

    @Test
    fun `NVMe channels group by device and legacy controller prefix`() {
        val groups = groupNvmeSensors(
            listOf(
                sensor("nvme0", "NVMe 0", 45.0).toThermalSensor()!!,
                sensor("nvme0_sensor_1", "Sensor 1", 48.0).toThermalSensor()!!,
                sensor("nvme1_sensor_1", "Sensor 1", 51.0).toThermalSensor()!!.copy(device = "/dev/nvme1"),
                sensor("nvme1", "Composite", 50.0).toThermalSensor()!!.copy(device = "/dev/nvme1"),
            ),
        )

        assertEquals(listOf("/dev/nvme1", "nvme0"), groups.map { it.id }.sorted())
        assertEquals(2, groups.size)
        assertEquals(listOf("nvme0", "nvme0_sensor_1"), groups.single { it.id == "nvme0" }.sensors.map { it.key })
    }

    @Test
    fun `history statistics ignore absent and invalid readings`() {
        val sensor = sensor("cpu", "CPU", 55.0).toThermalSensor()!!
        val samples = listOf(
            sample(0, mapOf("cpu" to 40.0)),
            sample(10, mapOf("cpu" to 50.0)),
            sample(20, mapOf("cpu" to Double.NaN)),
            sample(30, emptyMap()),
            sample(40, mapOf("cpu" to 60.0)),
        )
        val stats = thermalHistoryStats(sensor, samples)

        assertEquals(3, stats.sampleCount)
        assertEquals(50.0, stats.averageC!!, 0.0)
        assertEquals(40.0, stats.minimumC!!, 0.0)
        assertEquals(60.0, stats.maximumC!!, 0.0)
        assertEquals(20.0, stats.changeC!!, 0.0)
        assertEquals(45.0, stats.headroomC!!, 0.0)
        assertEquals(55.0, stats.currentC!!, 0.0)
    }

    @Test
    fun `threshold runs exclude missing values and backwards-time gaps`() {
        val points = listOf(
            ChartPoint(0, 91.0),
            ChartPoint(10, 92.0),
            ChartPoint(20, null),
            ChartPoint(30, 93.0),
            ChartPoint(25, 94.0),
            ChartPoint(40, 94.0),
            ChartPoint(50, 50.0),
            ChartPoint(60, 95.0),
            ChartPoint(70, 50.0),
        )
        val run = contiguousThresholdRun(points, 90.0)

        assertEquals(10L, run.longestMillis)
        assertNull(run.currentMillis)
        assertNull(run.currentStartMillis)

        val endedAbove = contiguousThresholdRun(points.dropLast(1), 90.0)
        assertEquals(0L, endedAbove.currentMillis)
        assertEquals(60L, endedAbove.currentStartMillis)
        assertNull(temperatureThresholdRun(emptyList(), "cpu", null))
    }

    @Test
    fun `fan history produces selectable points and extrema only for keyed readings`() {
        val samples = listOf(
            sample(0, emptyMap(), mapOf("fan1" to FanHistory(800, 100))),
            sample(10, emptyMap(), mapOf("fan1" to FanHistory(1200, 180))),
            sample(20),
        )
        val history = fanHistorySummary(samples, "fan1")

        assertEquals(800, history.minimumRpm)
        assertEquals(1200, history.maximumRpm)
        assertEquals(listOf(800.0, 1200.0, null), history.points.map { it.value })
        assertTrue(fanHistorySummary(samples, "").points.isEmpty())
    }

    @Test
    fun `events parse optional timestamps and active state additively`() {
        val events = parseEventsJson(
            """
            {"events":[
              {"kind":"resolved","ts":"1970-01-01T00:00:30Z","alert":{
                "id":"temp_cpu","severity":"warning","title":"CPU temperature","since":"1970-01-01T00:00:10Z","active":false}},
              {"kind":"fired","alert":{"id":"temp_nvme0","severity":"warning","title":"NVMe temperature","since":"1970-01-01T00:00:20Z"}}
            ]}
            """.trimIndent(),
        )

        assertEquals(2, events.size)
        assertEquals("1970-01-01T00:00:30Z", events[0].ts)
        assertFalse(events[0].alert.active)
        assertEquals("", events[1].ts)
        assertTrue(events[1].alert.active)
    }

    @Test
    fun `event timestamp prefers event time and falls back to alert start`() {
        val alert = Alert("temp_cpu", "warning", "CPU temperature", "Hot", "1970-01-01T00:00:10Z")
        val modern = AlertEvent("resolved", alert, "1970-01-01T00:00:30Z")
        val legacy = AlertEvent("resolved", alert)

        assertEquals("1970-01-01T00:00:30Z", eventTimestamp(modern))
        assertEquals("1970-01-01T00:00:10Z", eventTimestamp(legacy))
        assertEquals(listOf(modern, legacy), sortedThermalEvents(listOf(legacy, modern)))
        assertTrue(alert.active)
    }

    @Test
    fun `generic NVMe alarm is critical and maximum alarm is a warning`() {
        val normal = sensor("nvme0", "NVMe 0", 30.0, category = "nvme").toThermalSensor()!!
        assertEquals(ThermalHealth.CRITICAL, sensorHealth(normal.copy(alarms = listOf("alarm"))))
        assertEquals(ThermalHealth.WARNING, sensorHealth(normal.copy(alarms = listOf("max"))))
    }

    @Test
    fun `NVMe controller associates only its own SMART namespace identity`() {
        val drive = Smart(
            device = "/dev/nvme0n1", health = "PASSED", model = "RS256GSSD510", serial = "drive-serial",
            temp = 38.0, hours = 0, realloc = 0, pending = 0, uncorrect = 0, crc = 0, wear = 0,
            mediaErrors = 0, error = "",
        )
        val other = drive.copy(device = "/dev/nvme10n1", model = "Other controller")
        val controller = sensor("nvme0", "NVMe 0", 38.0, category = "nvme").toThermalSensor()!!
            .copy(device = "/dev/nvme0")
        assertEquals("drive-serial", smartForSensor(controller, listOf(other, drive))?.serial)
    }

    private fun sensor(
        key: String,
        label: String,
        c: Double = 50.0,
        category: String = "",
        source: String = "",
        coreType: String = "",
        cpus: List<Int> = emptyList(),
    ) = Temp(key, label, c, 90.0, 100.0, category = category, source = source, coreType = coreType, cpus = cpus)

    private fun statsWithSensors(vararg sensors: Temp): Stats {
        val root = JSONObject().put("temps", JSONArray())
        val array = root.getJSONArray("temps")
        sensors.forEach { temp ->
            array.put(JSONObject()
                .put("key", temp.key).put("label", temp.label).put("c", temp.c)
                .put("warn", temp.warn).put("crit", temp.crit).put("category", temp.category)
                .put("source", temp.source).put("core_type", temp.coreType)
                .put("cpus", JSONArray(temp.cpus)))
        }
        if (sensors.isNotEmpty()) {
            root.put("thermal_zones", JSONArray().put(JSONObject()
                .put("key", "zone_0").put("label", "acpitz").put("c", 120.0)))
        }
        return parseStatsJson(root.toString())
    }

    private fun sample(
        seconds: Long,
        temps: Map<String, Double> = emptyMap(),
        fans: Map<String, FanHistory> = emptyMap(),
    ) = Sample(
        ts = "1970-01-01T00:00:${seconds.toString().padStart(2, '0')}Z",
        cpu = null,
        memPct = null,
        load1 = null,
        netRx = null,
        netTx = null,
        diskRead = null,
        diskWrite = null,
        temps = temps,
        poolPct = null,
        fans = fans,
    )
}
