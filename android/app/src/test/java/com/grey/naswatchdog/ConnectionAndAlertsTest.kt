package com.grey.naswatchdog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionAndAlertsTest {
    @Test
    fun `normalizes common server addresses`() {
        assertEquals("http://192.0.2.10:8090", normalizeServerUrl("192.0.2.10:8090"))
        assertEquals("http://nas.local:8090", normalizeServerUrl(" http://nas.local:8090/ "))
        assertEquals("https://nas.example.com/health", normalizeServerUrl("https://nas.example.com/health/"))
    }

    @Test
    fun `rejects unusable server addresses`() {
        assertThrows(IllegalArgumentException::class.java) { normalizeServerUrl("") }
        assertThrows(IllegalArgumentException::class.java) { normalizeServerUrl("ftp://nas.local") }
        assertThrows(IllegalArgumentException::class.java) { normalizeServerUrl("http://") }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerUrl("http://nas.local:8090/?token=secret")
        }
    }

    @Test
    fun `alert delivery retains overflow for the next check`() {
        val alerts = (1..6).map { alert("alert-$it") }
        val first = alertDeliveryPlan(alerts, emptySet())

        assertEquals(listOf("alert-1", "alert-2", "alert-3", "alert-4", "alert-5"), first.alerts.map { it.id })
        assertEquals(5, first.nextSeen.size)

        val second = alertDeliveryPlan(alerts, first.nextSeen)
        assertEquals(listOf("alert-6"), second.alerts.map { it.id })
        assertEquals(alerts.map { it.id }.toSet(), second.nextSeen)
        assertTrue(alertDeliveryPlan(alerts, second.nextSeen).alerts.isEmpty())
    }

    @Test
    fun `resolved alerts can notify again if they return`() {
        val current = listOf(alert("pool"), alert("disk"))
        val initial = alertDeliveryPlan(current, emptySet())
        val afterResolution = alertDeliveryPlan(current.filter { it.id == "disk" }, initial.nextSeen)

        assertEquals(setOf("disk"), afterResolution.nextSeen)
        assertEquals(listOf("pool"), alertDeliveryPlan(current, afterResolution.nextSeen).alerts.map { it.id })
    }

    private fun alert(id: String) = Alert(id, "critical", "Title $id", "Detail $id", "")
}
