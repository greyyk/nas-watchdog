package com.grey.naswatchdog.charts

import java.util.Locale
import kotlin.math.abs

enum class HistoryRange(val apiValue: String, val label: String) {
    FIVE_MINUTES("5m", "5m"),
    ONE_HOUR("1h", "1h"),
    SIX_HOURS("6h", "6h"),
    TWENTY_FOUR_HOURS("24h", "24h");

    companion object {
        fun rangeForApiValue(apiValue: String): HistoryRange? =
            entries.firstOrNull { it.apiValue == apiValue }
    }
}

data class ChartPoint(val epochMillis: Long, val value: Double?)

enum class ThresholdKind { WARNING, CRITICAL }

data class ChartThreshold(val value: Double, val label: String, val kind: ThresholdKind)

data class ChartSeries(
    val label: String,
    val points: List<ChartPoint>,
    val unit: String = "%",
    val domainMin: Double? = null,
    val domainMax: Double? = null,
    val thresholds: List<ChartThreshold> = emptyList(),
)

/** Bounds in pixels when drawing, or arbitrary normalized units on the JVM. */
data class ChartPlotBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
)

data class ChartPlotPoint(
    val point: ChartPoint,
    val sourceIndex: Int,
    val x: Float,
    val y: Float,
)

/**
 * Chronological samples and disconnected line segments, independent of Compose and Android.
 * Missing/non-finite values, invalid timestamps, and backwards timestamps never create lines.
 */
class ChartGeometry(
    val series: ChartSeries,
    val bounds: ChartPlotBounds = ChartPlotBounds(),
) {
    val samples: List<ChartPlotPoint>
    val paths: List<List<ChartPlotPoint>>
    val startMillis: Long
    val endMillis: Long
    val domainMin: Double
    val domainMax: Double
    val latestPoint: ChartPoint?
    val minimumValue: Double?
    val maximumValue: Double?

    init {
        val actual = series.points.mapIndexedNotNull { index, point ->
            if (point.isPlottable()) IndexedValue(index, point) else null
        }.sortedBy { it.value.epochMillis }

        latestPoint = actual.lastOrNull()?.value
        minimumValue = actual.minOfOrNull { it.value.value!! }
        maximumValue = actual.maxOfOrNull { it.value.value!! }
        endMillis = latestPoint?.epochMillis ?: 1L
        startMillis = when {
            actual.isEmpty() -> 0L
            actual.first().value.epochMillis == endMillis -> endMillis - MILLIS_PER_HOUR
            else -> actual.first().value.epochMillis
        }

        val overrideMin = series.domainMin?.takeIf { it.isFinite() }
        val overrideMax = series.domainMax?.takeIf { it.isFinite() }
        val percentage = series.unit == "%"
        var low = overrideMin ?: if (percentage) 0.0 else minimumValue ?: 0.0
        var high = overrideMax ?: if (percentage) 100.0 else maximumValue ?: 1.0
        if (high < low) {
            val swap = low
            low = high
            high = swap
        }
        if (high == low) {
            val padding = if (low == 0.0) 1.0 else abs(low) * 0.1
            low -= padding
            high += padding
        }
        domainMin = low
        domainMax = high
        samples = actual.map { plotPoint(it.index, it.value) }
        paths = buildPaths()
    }

    fun xForMillis(epochMillis: Long): Float {
        val width = (bounds.right - bounds.left).coerceAtLeast(0f)
        val fraction = (epochMillis - startMillis).toDouble() / (endMillis - startMillis).toDouble()
        return bounds.left + width * fraction.toFloat()
    }

    fun yForValue(value: Double): Float {
        val height = (bounds.bottom - bounds.top).coerceAtLeast(0f)
        val fraction = (value - domainMin) / (domainMax - domainMin)
        return bounds.bottom - height * fraction.toFloat()
    }

    fun nearestPoint(x: Float): ChartPoint? = nearest(x.toDouble()) { it.x.toDouble() }

    fun nearestPointAtTime(epochMillis: Long): ChartPoint? =
        nearest(epochMillis.toDouble()) { it.point.epochMillis.toDouble() }

    fun plotPoint(point: ChartPoint?): ChartPlotPoint? =
        point?.let { selected -> samples.firstOrNull { it.point == selected } }

    /** Returns null at an edge, or selects the last/first sample when nothing is selected. */
    fun adjacentPoint(point: ChartPoint?, direction: Int): ChartPoint? {
        if (samples.isEmpty() || direction == 0) return null
        if (point == null) return (if (direction < 0) samples.last() else samples.first()).point
        val index = samples.indexOfFirst { it.point == point }
        if (index < 0) return null
        return samples.getOrNull(index + if (direction < 0) -1 else 1)?.point
    }

    private fun plotPoint(index: Int, point: ChartPoint) = ChartPlotPoint(
        point = point,
        sourceIndex = index,
        x = xForMillis(point.epochMillis),
        y = yForValue(point.value!!),
    )

    private fun buildPaths(): List<List<ChartPlotPoint>> {
        val result = mutableListOf<List<ChartPlotPoint>>()
        var segment = mutableListOf<ChartPlotPoint>()
        var previousTimestamp: Long? = null

        fun finishSegment() {
            if (segment.isNotEmpty()) result += segment
            segment = mutableListOf()
        }

        series.points.forEachIndexed { index, point ->
            val ordered = previousTimestamp?.let { point.epochMillis > it } ?: true
            if (!point.isPlottable() || !ordered) {
                finishSegment()
                previousTimestamp = null
                if (!point.isPlottable()) return@forEachIndexed
            }
            segment += plotPoint(index, point)
            previousTimestamp = point.epochMillis
        }
        finishSegment()
        return result
    }

    private fun nearest(
        target: Double,
        position: (ChartPlotPoint) -> Double,
    ): ChartPoint? {
        if (samples.isEmpty()) return null
        var low = 0
        var high = samples.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (position(samples[middle]) < target) low = middle + 1 else high = middle
        }
        if (low == 0) return samples.first().point
        val left = samples[low - 1]
        val right = samples[low]
        return if (abs(target - position(left)) <= abs(position(right) - target)) {
            left.point
        } else {
            right.point
        }
    }

    private fun ChartPoint.isPlottable() =
        epochMillis >= 0L && value?.isFinite() == true

    private companion object {
        const val MILLIS_PER_HOUR = 60L * 60L * 1_000L
    }
}

fun formatChartValue(value: Double, unit: String): String {
    val number = String.format(Locale.getDefault(), "%.1f", value)
    return when {
        unit == "%" -> "$number%"
        unit.isBlank() -> number
        else -> "$number $unit"
    }
}
