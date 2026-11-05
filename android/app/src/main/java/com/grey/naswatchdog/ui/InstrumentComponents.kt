package com.grey.naswatchdog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grey.naswatchdog.Alert
import com.grey.naswatchdog.FeedStatus
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.charts.ChartPoint
import com.grey.naswatchdog.freshnessLabel
import com.grey.naswatchdog.num
import com.grey.naswatchdog.pct

@Composable
fun SignalPlate(
    stats: Stats,
    status: FeedStatus,
    receivedAt: Long,
    now: Long,
    points: List<ChartPoint>,
    onOpenCpu: () -> Unit,
    modifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
) {
    val critical = stats.alerts.any { it.severity == "critical" } || stats.poolRo
    val warning = stats.alerts.any { it.severity == "warning" }
    val signal = if (critical) Red else if (warning) Amber else Green
    val statement = when {
        stats.alerts.isEmpty() -> "No active watchdog alerts"
        else -> {
            val count = stats.alerts.size
            "$count active ${if (count == 1) "alert" else "alerts"}" +
                if (critical) " · critical attention required" else if (warning) " · review recommended" else ""
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 326.dp).semantics {
            contentDescription = "CPU ${pct(stats.cpu)}. Open CPU details."
        },
        onClick = onOpenCpu,
        color = Surface,
        border = BorderStroke(1.dp, Line.copy(alpha = 0.74f)),
        shape = SignalPlateShape,
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stats.hostname.ifBlank { "NAS SERVER" }.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = MachineFont,
                    letterSpacing = 0.6.sp,
                    color = TextMain,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Dim,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                freshnessLabel(status, receivedAt, now),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MachineFont,
                color = if (status == FeedStatus.LIVE) Green else if (critical) Red else Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                statement,
                style = MaterialTheme.typography.titleMedium,
                color = if (stats.alerts.isEmpty()) TextMain else signal,
                modifier = Modifier.semantics { heading() },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (stats.poolRo) "Pool is read-only. Writes are failing." else "Pool writable · ${stats.cores} CPU threads",
                style = MaterialTheme.typography.bodySmall,
                color = if (stats.poolRo) Red else Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    num(stats.cpu),
                    modifier = Modifier.wrapContentSize().then(valueModifier),
                    style = NumberTextStyle,
                    fontSize = heroNumberSize(58),
                    lineHeight = heroNumberSize(60),
                    fontWeight = FontWeight.Medium,
                    color = TextMain,
                )
                Text(
                    "%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Dim,
                    modifier = Modifier.padding(start = 5.dp, bottom = 5.dp),
                )
                if (LocalDensity.current.fontScale <= 1.3f) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "CPU\nutilisation",
                        style = MaterialTheme.typography.labelMedium,
                        color = Dim,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (points.any { it.value != null }) "LAST HOUR" else "WAITING FOR HISTORY",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MachineFont,
                    color = Dim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Load ${num(stats.load1, 2)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dim,
                )
            }
            Spacer(Modifier.height(6.dp))
            MiniTrend(
                points = points,
                color = signal,
                domainMin = 0.0,
                domainMax = 100.0,
                modifier = Modifier.fillMaxWidth().height(42.dp),
            )
        }
    }
}

@Composable
fun MiniTrend(
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    domainMin: Double? = null,
    domainMax: Double? = null,
) {
    Canvas(modifier = modifier) {
        val values = points.mapNotNull { it.value }
        if (values.isEmpty()) {
            drawLine(Line.copy(alpha = 0.8f), androidx.compose.ui.geometry.Offset.Zero,
                androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            return@Canvas
        }
        val min = domainMin ?: minOf(values.minOrNull() ?: 0.0, 0.0)
        val max = domainMax ?: maxOf(values.maxOrNull() ?: 1.0, 1.0)
        val span = (max - min).takeIf { it > 0.001 } ?: 1.0
        val first = points.first().epochMillis
        val last = points.last().epochMillis
        val timeSpan = (last - first).coerceAtLeast(1L).toFloat()
        var path = Path()
        var drawing = false
        points.forEach { point ->
            val value = point.value
            if (value == null) {
                if (drawing) drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                path = Path()
                drawing = false
                return@forEach
            }
            val x = (point.epochMillis - first) / timeSpan * size.width
            val y = size.height - ((value - min) / span).toFloat() * (size.height - 5.dp.toPx()) - 2.dp.toPx()
            if (!drawing) path.moveTo(x, y) else path.lineTo(x, y)
            drawing = true
        }
        if (drawing) drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun Readout(
    label: String,
    value: String,
    supporting: String,
    progress: Float? = null,
    color: Color = TextMain,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Dim)
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            style = NumberTextStyle,
            fontSize = 27.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            softWrap = true,
        )
        Spacer(Modifier.height(4.dp))
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = Dim, softWrap = true)
        if (progress != null) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Blue,
                trackColor = Line,
            )
        }
    }
}

@Composable
fun SectionHeading(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Dim) }
    }
}

@Composable
fun ActiveIssue(alert: Alert) {
    val color = if (alert.severity == "critical") Red else if (alert.severity == "warning") Amber else Blue
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
        Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
            Icon(
                Icons.Default.Warning, contentDescription = "${alert.severity} alert",
                tint = color, modifier = Modifier.padding(8.dp).width(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(alert.title, style = MaterialTheme.typography.titleSmall, color = TextMain)
            Text(
                alert.detail, style = MaterialTheme.typography.bodySmall, color = Dim,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            relativeTimestamp(parseTimestampEpoch(alert.since)).let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
fun Notice(title: String, detail: String, color: Color = Amber) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = color)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Dim)
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.6f))
}
