package com.grey.naswatchdog.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grey.naswatchdog.HistoryUiState
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.TelemetryUiState
import com.grey.naswatchdog.charts.ChartGeometry
import com.grey.naswatchdog.charts.ChartPoint
import com.grey.naswatchdog.charts.ChartSeries
import com.grey.naswatchdog.charts.ChartThreshold
import com.grey.naswatchdog.charts.HistoryChart
import com.grey.naswatchdog.charts.HistoryRange
import com.grey.naswatchdog.charts.ThresholdKind
import com.grey.naswatchdog.charts.formatChartValue
import com.grey.naswatchdog.freshnessLabel
import com.grey.naswatchdog.num
import com.grey.naswatchdog.pct
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.Notice
import com.grey.naswatchdog.ui.NumberTextStyle
import com.grey.naswatchdog.ui.Readout
import com.grey.naswatchdog.ui.Red
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.SignalPlateShape
import com.grey.naswatchdog.ui.Surface
import com.grey.naswatchdog.ui.TextMain
import com.grey.naswatchdog.ui.chartPoints
import com.grey.naswatchdog.ui.formatDateTime
import com.grey.naswatchdog.ui.heroNumberSize

@Composable
fun CpuDetailScreen(
    stats: Stats,
    telemetry: TelemetryUiState,
    range: HistoryRange,
    onRangeChange: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier,
    cpuValueModifier: Modifier = Modifier,
) {
    val history = telemetry.histories[range] ?: HistoryUiState(loading = true)
    val cpuSeries = remember(history.samples) {
        ChartSeries(
            label = "CPU utilisation · ${range.label}",
            points = chartPoints(history.samples) { it.cpu },
            unit = "%",
            domainMin = 0.0,
            domainMax = 100.0,
        )
    }
    var showSamples by rememberSaveable(range) { mutableStateOf(false) }
    val cpuGeometry = remember(cpuSeries) { ChartGeometry(cpuSeries) }
    val cores = remember(stats.perCore) { stats.perCore.sortedBy { it.id } }
    val cpuTemperature = selectCpuTemperature(stats.temps)
    val thermalSeries = remember(cpuTemperature, history.samples) {
        cpuTemperature?.let { sensor ->
            val points = chartPoints(history.samples) { it.temps[sensor.key] }
            val values = points.mapNotNull { it.value }
            val upper = maxOf(sensor.crit, sensor.warn, values.maxOrNull() ?: sensor.c, sensor.c)
            ChartSeries(
                label = "${sensor.label} · ${range.label}",
                points = points,
                unit = "°C",
                domainMin = 0.0,
                domainMax = upper + 5.0,
                thresholds = listOfNotNull(
                    sensor.warn.takeIf { it > 0 }?.let {
                        ChartThreshold(it, "Warning · ${num(it, 0)}°C", ThresholdKind.WARNING)
                    },
                    sensor.crit.takeIf { it > 0 }?.let {
                        ChartThreshold(it, "Critical · ${num(it, 0)}°C", ThresholdKind.CRITICAL)
                    },
                ),
            )
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "cpu_header", contentType = "instrument") {
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = Surface,
                border = BorderStroke(1.dp, Line.copy(alpha = 0.74f)),
                shape = SignalPlateShape,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "CPU UTILISATION",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = MachineFont,
                        color = Dim,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        Text(
                            num(stats.cpu),
                            modifier = Modifier.wrapContentSize().then(cpuValueModifier),
                            style = NumberTextStyle,
                            fontSize = heroNumberSize(64),
                            lineHeight = heroNumberSize(66),
                            fontWeight = FontWeight.Medium,
                            color = TextMain,
                        )
                        Text(
                            "%", style = MaterialTheme.typography.headlineSmall,
                            color = Dim, modifier = Modifier.padding(start = 5.dp, bottom = 7.dp),
                        )
                    }
                    Text(
                        "${stats.cores} threads · ${stats.governor.ifBlank { "governor not reported" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        freshnessLabel(telemetry.feedStatus, telemetry.snapshot?.receivedAt),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MachineFont,
                        color = if (telemetry.error == null) Dim else Red,
                    )
                }
            }
        }
        if (telemetry.error != null) {
            item(key = "telemetry_warning", contentType = "notice") {
                Notice("Telemetry refresh delayed", telemetry.error)
            }
        }
        item(key = "ranges", contentType = "controls") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HistoryRange.entries.forEach { option ->
                    FilterChip(
                        selected = range == option,
                        onClick = { onRangeChange(option) },
                        label = {
                            Text(option.label, maxLines = 1)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item(key = "cpu_chart", contentType = "chart") {
            key(range) {
                var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }
                if (history.loading && history.samples.isEmpty()) {
                    HistoryLoading(cpuSeries.label)
                } else {
                    HistoryChart(
                        series = cpuSeries,
                        selectedPoint = selectedPoint,
                        onSelect = { selectedPoint = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item(key = "range_summary", contentType = "metrics") {
            val pending = history.loading && history.samples.isEmpty()
            val latest = if (pending) "Loading" else cpuGeometry.latestPoint?.value?.let { pct(it) } ?: "No samples"
            val minimum = if (pending) "Loading" else cpuGeometry.minimumValue?.let { pct(it) } ?: "No samples"
            val maximum = if (pending) "Loading" else cpuGeometry.maximumValue?.let { pct(it) } ?: "No samples"
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { Readout("Latest", latest, "Within selected range") }
                Column(Modifier.weight(1f)) { Readout("Minimum", minimum, "Within selected range") }
                Column(Modifier.weight(1f)) { Readout("Peak", maximum, "Within selected range") }
            }
            Text(
                if (pending) "Loading samples · ${range.label}" else "${cpuGeometry.samples.size} samples · ${range.label}",
                style = MaterialTheme.typography.bodySmall, color = Dim,
            )
        }
        if (history.error != null) {
            item(key = "history_error", contentType = "notice") {
                Notice("History refresh delayed", history.error)
            }
        }
        item(key = "table_toggle", contentType = "controls") {
            OutlinedButton(
                onClick = { showSamples = !showSamples },
                enabled = cpuGeometry.samples.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showSamples) "Hide timestamped samples" else "Read timestamped samples")
            }
        }
        if (showSamples) {
            item(key = "table_description", contentType = "heading") {
                Text(
                    "Latest samples first · Tap and scrub the chart to compare peaks",
                    style = MaterialTheme.typography.labelMedium, color = Dim,
                )
            }
            val samples = cpuGeometry.samples.asReversed()
            items(
                count = samples.size,
                key = { "sample:${samples[it].sourceIndex}" },
                contentType = { "sample_row" },
            ) { index -> SampleReadout(samples[index].point) }
        }
        if (thermalSeries?.points?.any { it.value != null } == true && cpuTemperature != null) {
            item(key = "thermal_heading", contentType = "heading") {
                SectionHeading("Thermal envelope", "${num(cpuTemperature.c)}°C now")
            }
            item(key = "thermal_chart", contentType = "chart") {
                var selectedPoint by remember(range) { mutableStateOf<ChartPoint?>(null) }
                HistoryChart(
                    series = thermalSeries,
                    selectedPoint = selectedPoint,
                    onSelect = { selectedPoint = it },
                )
                Text(
                    "Thresholds are supplied by the watchdog.",
                    style = MaterialTheme.typography.bodySmall, color = Dim,
                )
            }
        }
        item(key = "cores_heading", contentType = "heading") {
            SectionHeading("Per-thread utilization", "${stats.perCore.size} reported")
        }
        if (stats.perCore.isEmpty()) {
            item(key = "cores_empty", contentType = "empty") {
                Text("Per-thread details are not available in this snapshot.", color = Dim, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(
                count = cores.size,
                key = { "core:${cores[it].id}" },
                contentType = { "core_row" },
            ) { index -> CoreReadout(cores[index]) }
        }
    }
}

@Composable
private fun HistoryLoading(label: String) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Waiting for the first samples in this range…", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SampleReadout(point: ChartPoint) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            formatDateTime(point.epochMillis),
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
            modifier = Modifier.weight(1f),
        )
        point.value?.let { value ->
            Text(
                formatChartValue(value, "%"),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = MachineFont,
            )
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.5f))
}

@Composable
private fun CoreReadout(core: com.grey.naswatchdog.Core) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            "CPU ${core.id}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MachineFont,
            modifier = Modifier.weight(0.25f),
        )
        LinearProgressIndicator(
            progress = { (core.usage / 100).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(4.dp),
            trackColor = Line,
        )
        Text(
            pct(core.usage),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MachineFont,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
