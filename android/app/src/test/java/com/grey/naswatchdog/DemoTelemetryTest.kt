package com.grey.naswatchdog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DemoTelemetryTest {
    @Test
    fun `demo exercises parser without contacting a host`(): Unit = runBlocking {
        val stats = fetchStats(DEMO_SERVER_URL, "synthetic-demo")
        assertTrue(isDemoConnection(DEMO_SERVER_URL))
        assertFalse(isDemoConnection("https://nas.example.test"))
        assertTrue(stats.hostname.contains("Demo"))
        assertTrue(stats.os.contains("Synthetic"))
        assertTrue(stats.temps.size >= 25)
        assertTrue(stats.temps.any { it.category == "dimm" })
        assertTrue(stats.temps.any { it.category == "nvme" })
        assertEquals(8, stats.smart.size)
        assertEquals(3, stats.fans.size)
        assertEquals(2, stats.containers.size)
        assertFalse(stats.actionsEnabled)
        assertFalse(fetchHistory(DEMO_SERVER_URL, "synthetic-demo").isEmpty())
    }

    @Test
    fun `demo does not issue host actions even when called directly`(): Unit = runBlocking {
        val result = postAction(DEMO_SERVER_URL, "synthetic-demo", "reboot", "")
        assertFalse(result.ok)
        assertTrue(result.message.contains("disabled"))
    }

    @Test
    fun `demo settings are separated from actual credentials`(): Unit = runBlocking {
        assertThrows(ApiException::class.java) {
            runBlocking { fetchStats(DEMO_SERVER_URL, "wrong") }
        }
        assertThrows(ApiException::class.java) {
            runBlocking { demoApiResponse("$DEMO_SERVER_URL/api/v1/not-real", "synthetic-demo") }
        }
    }
}
