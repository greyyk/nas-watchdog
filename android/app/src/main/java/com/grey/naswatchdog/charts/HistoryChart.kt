package com.grey.naswatchdog.charts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryChart(
    series: ChartSeries,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    selectedPoint: ChartPoint? = null,
    onSelect: ((ChartPoint) -> Unit)? = null,
) {
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val geometry = remember(series, canvasSize, density) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@remember null
        val width = canvasSize.width.toFloat()
        val canvasHeight = canvasSize.height.toFloat()
        val left = with(density) { 48.dp.toPx() }.coerceAtMost(width * 0.3f)
        val right = (width - with(density) { 8.dp.toPx() }).coerceIn(left, width)
        val top = with(density) { 14.dp.toPx() }.coerceAtMost(canvasHeight * 0.2f)
        val bottom = (canvasHeight - with(density) { 22.dp.toPx() })
            .coerceIn(top, canvasHeight)
        ChartGeometry(series, ChartPlotBounds(left, top, right, bottom))
    }
    val selection = remember(geometry, selectedPoint) { geometry?.plotPoint(selectedPoint) }
    val summary = remember(geometry, selection) {
        geometry?.let { chartSummary(it, selection?.point) } ?: series.label
    }
    val accessibilitySummary = remember(geometry) {
        geometry?.let { accessibilitySummary(it) } ?: "${series.label}. Loading chart."
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val labelPaint = remember(textColor, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Text(series.label, style = MaterialTheme.typography.titleSmall)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { canvasSize = it }
                .pointerInput(geometry) {
                    if (geometry == null || currentOnSelect == null) return@pointerInput
                    detectTapGestures { offset ->
                        geometry.nearestPoint(offset.x)?.let { currentOnSelect?.invoke(it) }
                    }
                }
                .pointerInput(geometry) {
                    if (geometry == null || currentOnSelect == null) return@pointerInput
                    var lastPoint: ChartPoint? = null
                    fun select(x: Float) {
                        val point = geometry.nearestPoint(x) ?: return
                        if (point != lastPoint) {
                            lastPoint = point
                            currentOnSelect?.invoke(point)
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { lastPoint = null; select(it.x) },
                        onDragEnd = { lastPoint = null },
                        onDragCancel = { lastPoint = null },
                    ) { change, _ ->
                        change.consume()
                        select(change.position.x)
                    }
                }
                .semantics {
                    contentDescription = accessibilitySummary
                    if (selection != null) {
                        stateDescription = selectedDescription(selection.point, series.unit)
                    }
                    val chart = geometry
                    val callback = onSelect
                    if (chart != null && callback != null && chart.samples.isNotEmpty()) {
                        customActions = listOf(
                            CustomAccessibilityAction("Previous sample") {
                                chart.adjacentPoint(selection?.point, -1)?.let(callback) != null
                            },
                            CustomAccessibilityAction("Next sample") {
                                chart.adjacentPoint(selection?.point, 1)?.let(callback) != null
                            },
                        )
                    }
                },
        ) {
            val chart = geometry ?: return@Canvas
            val bounds = chart.bounds
            val canvas = drawContext.canvas.nativeCanvas
            for (tick in 0..4) {
                val fraction = tick / 4f
                val y = bounds.top + (bounds.bottom - bounds.top) * fraction
                drawLine(gridColor, Offset(bounds.left, y), Offset(bounds.right, y))
                if (chart.samples.isNotEmpty()) {
                    val value = chart.domainMax - (chart.domainMax - chart.domainMin) * fraction
                    labelPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(formatChartValue(value, ""), 0f, y - 3.dp.toPx(), labelPaint)
                }
            }

            series.thresholds.forEach { threshold ->
                val y = chart.yForValue(threshold.value)
                if (threshold.value.isFinite() && y in bounds.top..bounds.bottom) {
                    val color = when (threshold.kind) {
                        ThresholdKind.WARNING -> Color(0xFFF2C078)
                        ThresholdKind.CRITICAL -> Color(0xFFFF8589)
                    }
                    drawLine(color, Offset(bounds.left, y), Offset(bounds.right, y), 1.dp.toPx())
                }
            }

            clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                chart.paths.forEach { path ->
                    if (path.size == 1) {
                        val point = path.first()
                        drawCircle(lineColor, 3.dp.toPx(), Offset(point.x, point.y))
                    } else {
                        for (index in 1 until path.size) {
                            val from = path[index - 1]
                            val to = path[index]
                            drawLine(
                                lineColor,
                                Offset(from.x, from.y),
                                Offset(to.x, to.y),
                                2.dp.toPx(),
                                StrokeCap.Round,
                            )
                        }
                    }
                }
            }

            if (chart.samples.isEmpty()) {
                labelPaint.textAlign = Paint.Align.CENTER
                val baseline = (bounds.top + bounds.bottom) / 2 -
                    (labelPaint.ascent() + labelPaint.descent()) / 2
                canvas.drawText("No samples", (bounds.left + bounds.right) / 2, baseline, labelPaint)
            } else {
                val baseline = size.height - labelPaint.descent()
                labelPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(formatTime(chart.startMillis), bounds.left, baseline, labelPaint)
                labelPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(formatTime(chart.endMillis), bounds.right, baseline, labelPaint)
            }

            selection?.let { selected ->
                val x = selected.x.coerceIn(bounds.left, bounds.right)
                val y = selected.y.coerceIn(bounds.top, bounds.bottom)
                drawLine(lineColor, Offset(x, bounds.top), Offset(x, bounds.bottom), 1.dp.toPx())
                drawCircle(lineColor, 5.dp.toPx(), Offset(x, y))
                drawCircle(surfaceColor, 2.dp.toPx(), Offset(x, y))
            }
        }
        // Threshold annotations wrap as ordinary text below the plot, so large
        // fonts and equivalent hardware limits cannot overlap chart samples.
        series.thresholds.forEach { threshold ->
            if (threshold.value.isFinite()) {
                Text(
                    "— ${threshold.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (threshold.kind) {
                        ThresholdKind.WARNING -> Color(0xFFF2C078)
                        ThresholdKind.CRITICAL -> Color(0xFFFF8589)
                    },
                )
            }
        }
        if (selection != null) {
            Text(
                selectedDescription(selection.point, series.unit),
                style = MaterialTheme.typography.labelMedium,
                color = lineColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun ChartSummary(
    series: ChartSeries,
    modifier: Modifier = Modifier,
    selectedPoint: ChartPoint? = null,
) {
    val text = remember(series, selectedPoint) {
        val geometry = ChartGeometry(series)
        chartSummary(geometry, geometry.plotPoint(selectedPoint)?.point)
    }
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall)
}

private fun chartSummary(geometry: ChartGeometry, selected: ChartPoint?): String {
    if (geometry.samples.isEmpty()) return "${geometry.series.label}: no samples"
    val unit = geometry.series.unit
    val base = "${formatTime(geometry.startMillis)}–${formatTime(geometry.endMillis)} · " +
        "latest ${formatChartValue(geometry.latestPoint!!.value!!, unit)} · " +
        "min ${formatChartValue(geometry.minimumValue!!, unit)} · " +
        "max ${formatChartValue(geometry.maximumValue!!, unit)}"
    return if (selected == null) base else "$base · ${selectedDescription(selected, unit)}"
}

private fun accessibilitySummary(geometry: ChartGeometry): String {
    val unit = geometry.series.unit
    val range = if (geometry.samples.isEmpty()) "No range" else
        "Range ${formatDateTime(geometry.startMillis)} to ${formatDateTime(geometry.endMillis)}"
    val thresholds = geometry.series.thresholds.joinToString(". ") {
        "${it.kind.name.lowercase(Locale.getDefault()).replaceFirstChar(Char::uppercase)} ${it.label}, " +
            formatChartValue(it.value, unit)
    }
    return "${geometry.series.label}. $range. Latest ${geometry.latestPoint?.value?.let { formatChartValue(it, unit) } ?: "unavailable"}. " +
        "Minimum ${geometry.minimumValue?.let { formatChartValue(it, unit) } ?: "unavailable"}. " +
        "Maximum ${geometry.maximumValue?.let { formatChartValue(it, unit) } ?: "unavailable"}." +
        if (thresholds.isEmpty()) "" else " $thresholds."
}

private fun selectedDescription(point: ChartPoint, unit: String) =
    "${formatDateTime(point.epochMillis)}, ${point.value?.let { formatChartValue(it, unit) } ?: "missing"}"

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

private fun formatDateTime(epochMillis: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMillis))
