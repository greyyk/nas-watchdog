package com.grey.naswatchdog

import com.grey.naswatchdog.charts.HistoryRange
import com.grey.naswatchdog.charts.ThresholdKind
import com.grey.naswatchdog.feature.SensorCategory
import com.grey.naswatchdog.feature.ThermalSensor
import com.grey.naswatchdog.feature.thermalChartSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalChartThresholdTest {
    @Test
    fun `equivalent limits share the strongest line and preserve other limits`() {
        val sensor = ThermalSensor(
            key = "cpu", label = "CPU", c = 93.0,
            warn = 90.0, crit = 100.0, category = SensorCategory.CPU_PACKAGE,
            limits = TemperatureLimits(maxC = 100.0, critC = 100.0, emergencyC = 105.0),
        )
        val thresholds = thermalChartSeries(sensor, emptyList(), HistoryRange.ONE_HOUR).thresholds
        assertEquals(listOf(90.0, 100.0, 105.0), thresholds.map { it.value })
        assertEquals(ThresholdKind.CRITICAL, thresholds.single { it.value == 100.0 }.kind)
        assertTrue(thresholds.single { it.value == 100.0 }.label.startsWith("Critical"))
    }

    @Test
    fun `unique hardware maximum is retained`() {
        val sensor = ThermalSensor(
            key = "dimm", label = "DIMM", c = null,
            warn = 70.0, crit = 85.0, category = SensorCategory.MEMORY,
            limits = TemperatureLimits(maxC = 75.0, critC = 85.0),
        )
        val thresholds = thermalChartSeries(sensor, emptyList(), HistoryRange.ONE_HOUR).thresholds
        assertEquals(listOf(70.0, 75.0, 85.0), thresholds.map { it.value })
        assertTrue(thresholds.any { it.label.startsWith("Hardware max") })
    }
}
