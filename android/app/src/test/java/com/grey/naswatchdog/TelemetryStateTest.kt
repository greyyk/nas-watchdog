package com.grey.naswatchdog

import com.grey.naswatchdog.ui.parseTimestampEpoch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryStateTest {
    @Test
    fun `connection cache keys are isolated and do not contain credentials`() {
        val first = telemetryConnectionKey("http://nas.local", "secret")
        assertEquals(64, first.length)
        assertEquals(first, telemetryConnectionKey("http://nas.local", "secret"))
        assertNotEquals(first, telemetryConnectionKey("http://nas.local", "different"))
        assertNotEquals(first, telemetryConnectionKey("http://other.local", "secret"))
        assertTrue(!first.contains("secret"))
    }

    @Test
    fun `epoch parser preserves fractional milliseconds and real timezone offsets`() {
        assertEquals(0L, parseTimestampEpoch("1970-01-01T00:00:00Z"))
        assertEquals(999L, parseTimestampEpoch("1970-01-01T00:00:00.999999999Z"))
        assertEquals(
            parseTimestampEpoch("1970-01-01T00:00:00Z"),
            parseTimestampEpoch("1970-01-01T01:30:00+01:30"),
        )
        assertEquals(
            parseTimestampEpoch("1970-01-01T00:00:00Z"),
            parseTimestampEpoch("1969-12-31T18:00:00-06:00"),
        )
    }

    @Test
    fun `invalid timestamps are never guessed`() {
        assertNull(parseTimestampEpoch("2026-02-30T00:00:00Z"))
        assertNull(parseTimestampEpoch("yesterday"))
        assertNull(parseTimestampEpoch("2026-11-05T12:00:00"))
    }

    @Test
    fun `freshness reports connection provenance and age separately`() {
        val receivedAt = 1_000_000L
        val now = receivedAt + 240_000L
        assertEquals("LIVE · RECEIVED 4 MIN AGO", freshnessLabel(FeedStatus.LIVE, receivedAt, now))
        assertEquals("CACHED SNAPSHOT · 4 MIN AGO", freshnessLabel(FeedStatus.CACHED, receivedAt, now))
        assertEquals("RECONNECTING · DATA FROM 4 MIN AGO", freshnessLabel(FeedStatus.RECONNECTING, receivedAt, now))
        assertEquals("CONNECTING", freshnessLabel(FeedStatus.CONNECTING, null, now))
        assertEquals("NO CONNECTION", freshnessLabel(FeedStatus.FAILED, null, now))
    }
}
