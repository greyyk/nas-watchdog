package com.grey.naswatchdog

import com.grey.naswatchdog.charts.ChartGeometry
import com.grey.naswatchdog.charts.ChartPlotBounds
import com.grey.naswatchdog.charts.ChartPoint
import com.grey.naswatchdog.charts.ChartSeries
import com.grey.naswatchdog.charts.HistoryRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartGeometryTest {
    private val bounds = ChartPlotBounds(left = 0f, top = 0f, right = 100f, bottom = 100f)

    @Test
    fun `flat percentage series uses explicit percentage domain`() {
        val geometry = geometry(point(1_000, 50.0), point(2_000, 50.0))

        assertEquals(0.0, geometry.domainMin, 0.0)
        assertEquals(100.0, geometry.domainMax, 0.0)
        assertEquals(listOf(50f, 50f), geometry.paths.single().map { it.y })
        assertEquals(1, geometry.paths.size)
    }

    @Test
    fun `empty series has no fabricated samples`() {
        val geometry = ChartGeometry(ChartSeries("Empty", emptyList()), bounds)

        assertTrue(geometry.samples.isEmpty())
        assertTrue(geometry.paths.isEmpty())
        assertNull(geometry.latestPoint)
        assertNull(geometry.minimumValue)
        assertNull(geometry.maximumValue)
        assertNull(geometry.nearestPoint(50f))
        assertEquals(0.0, geometry.domainMin, 0.0)
        assertEquals(100.0, geometry.domainMax, 0.0)
    }

    @Test
    fun `one point gets a trailing one hour range and remains selectable`() {
        val timestamp = 3_600_000L
        val geometry = geometry(point(timestamp, 75.0))

        assertEquals(0L, geometry.startMillis)
        assertEquals(timestamp, geometry.endMillis)
        assertEquals(100f, geometry.samples.single().x, 0.0001f)
        assertEquals(25f, geometry.samples.single().y, 0.0001f)
        assertEquals(1, geometry.paths.single().size)
        assertEquals(point(timestamp, 75.0), geometry.nearestPoint(0f))
        assertEquals(point(timestamp, 75.0), geometry.nearestPoint(100f))
    }

    @Test
    fun `null and non finite values split paths without zero filling`() {
        val geometry = geometry(
            point(1_000, 10.0),
            point(2_000, null),
            point(3_000, Double.NaN),
            point(4_000, Double.POSITIVE_INFINITY),
            point(5_000, 30.0),
            point(6_000, 40.0),
        )

        assertEquals(listOf(1, 2), geometry.paths.map { it.size })
        assertEquals(listOf(1_000L, 5_000L, 6_000L), geometry.samples.map { it.point.epochMillis })
        assertEquals(10.0, geometry.minimumValue!!, 0.0)
        assertEquals(40.0, geometry.maximumValue!!, 0.0)
        assertNull(geometry.samples.firstOrNull { it.point.epochMillis == 2_000L })
    }

    @Test
    fun `percentage domain remains zero to one hundred`() {
        val geometry = geometry(point(0, 0.0), point(100_000, 100.0))

        assertEquals(0f, geometry.samples.first().x, 0.0001f)
        assertEquals(100f, geometry.samples.last().x, 0.0001f)
        assertEquals(100f, geometry.samples.first().y, 0.0001f)
        assertEquals(0f, geometry.samples.last().y, 0.0001f)
    }

    @Test
    fun `explicit domain overrides data extrema and percentage default`() {
        val percent = geometry(
            point(0, 100.0),
            unit = "%",
            domainMin = 0.0,
            domainMax = 200.0,
        )
        val signed = geometry(
            point(0, 0.0),
            unit = "ms",
            domainMin = -10.0,
            domainMax = 10.0,
        )

        assertEquals(200.0, percent.domainMax, 0.0)
        assertEquals(50f, percent.samples.single().y, 0.0001f)
        assertEquals(-10.0, signed.domainMin, 0.0)
        assertEquals(10.0, signed.domainMax, 0.0)
        assertEquals(50f, signed.samples.single().y, 0.0001f)
    }

    @Test
    fun `flat non percentage series receives drawable display bounds`() {
        val geometry = geometry(point(1_000, 20.0), point(2_000, 20.0), unit = "ms")

        assertTrue(geometry.domainMin < 20.0)
        assertTrue(geometry.domainMax > 20.0)
        assertEquals(50f, geometry.samples.first().y, 0.0001f)
        assertEquals(50f, geometry.samples.last().y, 0.0001f)
    }

    @Test
    fun `nearest selection returns actual samples and skips gaps`() {
        val geometry = geometry(
            point(0, 10.0),
            point(50_000, null),
            point(100_000, 30.0),
        )

        assertEquals(point(0, 10.0), geometry.nearestPoint(42f))
        assertEquals(point(0, 10.0), geometry.nearestPoint(50f))
        assertEquals(point(100_000, 30.0), geometry.nearestPoint(51f))
        assertEquals(point(100_000, 30.0), geometry.nearestPointAtTime(60_000))
        assertEquals(point(0, 10.0), geometry.adjacentPoint(point(100_000, 30.0), -1))
        assertEquals(point(100_000, 30.0), geometry.adjacentPoint(point(0, 10.0), 1))
        assertNull(geometry.adjacentPoint(point(0, 10.0), -1))
    }

    @Test
    fun `invalid and backwards timestamps break paths while nearest uses chronology`() {
        val geometry = geometry(
            point(1_000, 10.0),
            point(-1, 20.0),
            point(2_000, 30.0),
            point(3_000, null),
            point(4_000, 40.0),
            point(3_500, 50.0),
            point(5_000, 60.0),
        )

        assertEquals(listOf(1, 1, 1, 2), geometry.paths.map { it.size })
        assertEquals(
            listOf(1_000L, 2_000L, 3_500L, 4_000L, 5_000L),
            geometry.samples.map { it.point.epochMillis },
        )
        assertEquals(point(3_500, 50.0), geometry.nearestPointAtTime(3_700))
        assertNull(geometry.samples.firstOrNull { it.point.epochMillis < 0 })
    }

    @Test
    fun `duplicate timestamps retain unique row identities without merging samples`() {
        val geometry = geometry(point(1_000, 10.0), point(1_000, 20.0))

        assertEquals(2, geometry.samples.size)
        assertEquals(2, geometry.samples.map { it.sourceIndex }.distinct().size)
        assertEquals(listOf(0, 1), geometry.samples.map { it.sourceIndex })
    }

    @Test
    fun `an expired selection is absent and accessibility resumes from an edge`() {
        val previousRange = point(1_000, 50.0)
        val geometry = geometry(point(5_000, 20.0), point(6_000, 30.0))

        assertNull(geometry.plotPoint(previousRange))
        assertEquals(point(6_000, 30.0), geometry.adjacentPoint(null, -1))
        assertEquals(point(5_000, 20.0), geometry.adjacentPoint(null, 1))
    }

    @Test
    fun `API ranges retain their wire values`() {
        assertEquals(HistoryRange.FIVE_MINUTES, HistoryRange.rangeForApiValue("5m"))
        assertEquals(HistoryRange.TWENTY_FOUR_HOURS, HistoryRange.rangeForApiValue("24h"))
        assertNull(HistoryRange.rangeForApiValue("7d"))
    }

    private fun point(timestamp: Long, value: Double?) = ChartPoint(timestamp, value)

    private fun geometry(
        vararg points: ChartPoint,
        unit: String = "%",
        domainMin: Double? = null,
        domainMax: Double? = null,
    ) = ChartGeometry(
        ChartSeries("Test", points.toList(), unit, domainMin, domainMax),
        bounds,
    )
}
