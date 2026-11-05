package com.grey.naswatchdog

import com.grey.naswatchdog.feature.selectCpuTemperature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuSensorTest {
    @Test
    fun `actual CPU key takes priority over similarly labelled sensors`() {
        val sensors = listOf(
            Temp("package_0", "CPU package 0", 52.0, 90.0, 100.0),
            Temp("cpu", "CPU core sensor", 55.0, 90.0, 100.0),
        )
        assertEquals("cpu", selectCpuTemperature(sensors)?.key)
    }

    @Test
    fun `CPU sensor can be matched by label and absence is explicit`() {
        val sensors = listOf(Temp("package_0", "CPU package 0", 52.0, 90.0, 100.0))
        assertEquals("package_0", selectCpuTemperature(sensors)?.key)
        assertNull(selectCpuTemperature(listOf(Temp("dimm0", "DIMM 0", 42.0, 60.0, 70.0))))
        assertNull(selectCpuTemperature(emptyList()))
    }
}
