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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grey.naswatchdog.Alert
import com.grey.naswatchdog.AlertEvent
import com.grey.naswatchdog.Fan
import com.grey.naswatchdog.FeedStatus
import com.grey.naswatchdog.HistoryUiState
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.TelemetryUiState
import com.grey.naswatchdog.ThermalTrip
import com.grey.naswatchdog.ThermalZone
import com.grey.naswatchdog.charts.ChartPoint
import com.grey.naswatchdog.charts.ChartSeries
import com.grey.naswatchdog.charts.ChartThreshold
import com.grey.naswatchdog.charts.HistoryChart
import com.grey.naswatchdog.charts.HistoryRange
import com.grey.naswatchdog.charts.ThresholdKind
import com.grey.naswatchdog.connectionError
import com.grey.naswatchdog.fetchEvents
import com.grey.naswatchdog.freshnessLabel
import com.grey.naswatchdog.num
import com.grey.naswatchdog.pct
import com.grey.naswatchdog.ui.ActiveIssue
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Blue
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Green
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.MiniTrend
import com.grey.naswatchdog.ui.Notice
import com.grey.naswatchdog.ui.NumberTextStyle
import com.grey.naswatchdog.ui.Readout
import com.grey.naswatchdog.ui.Red
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.SignalPlateShape
import com.grey.naswatchdog.ui.SurfaceHigh
import com.grey.naswatchdog.ui.TextMain
import com.grey.naswatchdog.ui.chartPoints
import com.grey.naswatchdog.ui.formatDateTime
import com.grey.naswatchdog.ui.heroNumberSize
import com.grey.naswatchdog.ui.parseTimestampEpoch
import com.grey.naswatchdog.ui.relativeTimestamp
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalDetailScreen(
    stats: Stats,
    telemetry: TelemetryUiState,
    range: HistoryRange,
    onRangeChange: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier,
    baseUrl: String = "",
    token: String = "",
) {
    val model = remember(stats) { buildThermalModel(stats) }
    val history = telemetry.histories[range] ?: HistoryUiState(loading = true)
    var selectedKey by rememberSaveable { mutableStateOf("cpu") }
    val selected = remember(model, selectedKey) { selectThermalSensor(model.sensors, selectedKey) }
    LaunchedEffect(selected?.key, selectedKey) {
        val fallback = selected?.key.orEmpty()
        if (selectedKey != fallback) selectedKey = fallback
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showSamples by rememberSaveable(range, selected?.key) { mutableStateOf(false) }
    val points = remember(selected, history.samples) {
        selected?.let { sensor -> chartPoints(history.samples) { it.temps[sensor.key] } }.orEmpty()
    }
    val series = remember(selected, points, range) { selected?.let { sensor ->
        thermalChartSeries(sensor, points, range)
    } }
    val historyStats = remember(selected, history.samples) {
        selected?.let { thermalHistoryStats(it, history.samples) }
    }
    val warningRun = remember(selected, history.samples) {
        selected?.let { temperatureThresholdRun(history.samples, it.key, warningThreshold(it)) }
    }
    val criticalRun = remember(selected, history.samples) {
        selected?.let { temperatureThresholdRun(history.samples, it.key, criticalThreshold(it)) }
    }

    var events by remember(baseUrl, token) { mutableStateOf<List<AlertEvent>>(emptyList()) }
    var eventsLoading by remember(baseUrl, token) { mutableStateOf(false) }
    var eventsError by remember(baseUrl, token) { mutableStateOf<String?>(null) }
    LaunchedEffect(baseUrl, token, telemetry.snapshot?.receivedAt, telemetry.feedStatus) {
        if (baseUrl.isBlank() || telemetry.feedStatus != FeedStatus.LIVE) {
            eventsLoading = false
            return@LaunchedEffect
        }
        eventsLoading = events.isEmpty()
        try {
            events = fetchEvents(baseUrl, token)
            eventsError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            eventsError = connectionError(e)
        } finally {
            eventsLoading = false
        }
    }
    val exactSamples = remember(points) {
        points.mapIndexedNotNull { index, point ->
            if (point.value?.isFinite() == true) IndexedValue(index, point) else null
        }.sortedByDescending { it.value.epochMillis }
    }
    val recentEvents = remember(events) { sortedThermalEvents(events).take(8) }
    val activeAlerts = remember(stats.alerts) {
        stats.alerts.filter { it.active && isThermalAlert(it) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "thermal_hero", contentType = "instrument") {
            ThermalHero(model, telemetry)
        }
        if (telemetry.error != null) {
            item(key = "telemetry_warning", contentType = "notice") {
                Notice("Thermal telemetry refresh delayed", telemetry.error)
            }
        }
        item(key = "history_heading", contentType = "heading") {
            SectionHeading("Temperature history", selected?.label ?: "No sensors")
        }
        item(key = "ranges", contentType = "controls") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HistoryRange.entries.forEach { option ->
                    FilterChip(
                        selected = range == option,
                        onClick = { onRangeChange(option) },
                        label = { Text(option.label, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item(key = "sensor_selector", contentType = "controls") {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (model.sensors.isNotEmpty()) expanded = it },
            ) {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = model.sensors.isNotEmpty(),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        selected?.let { "${it.category.label} · ${it.label}" } ?: "No temperature sensors reported",
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                }
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    model.pickerGroups.forEach { group ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    group.category.label.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Dim,
                                    modifier = Modifier.semantics { heading() },
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                        group.sensors.forEach { sensor ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(sensor.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${temperature(sensor.c)}${sensor.device.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Dim,
                                        )
                                    }
                                },
                                onClick = {
                                    selectedKey = sensor.key
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }
        }
        item(key = "selected_summary", contentType = "metrics") {
            SelectedSensorSummary(selected, historyStats, history, range)
        }
        item(key = "thermal_chart", contentType = "chart") {
            key(range, selected?.key) {
                var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }
                when {
                    selected == null || series == null -> ThermalEmptyState("No normalized temperatures or firmware zones are present in this snapshot.")
                    history.loading && history.samples.isEmpty() -> ThermalHistoryLoading(series.label)
                    else -> HistoryChart(
                        series = series,
                        selectedPoint = selectedPoint,
                        onSelect = { selectedPoint = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (history.error != null) {
            item(key = "history_error", contentType = "notice") {
                Notice("History refresh delayed", history.error)
            }
        }
        if (selected != null) {
            item(key = "threshold_runs", contentType = "metrics") {
                Text(
                    "Contiguous threshold periods exclude missing samples; they do not extrapolate through gaps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        ThresholdRunReadout("Above warning", warningThreshold(selected), warningRun)
                    }
                    Column(Modifier.weight(1f)) {
                        ThresholdRunReadout("Above critical", criticalThreshold(selected), criticalRun)
                    }
                }
            }
            item(key = "samples_toggle", contentType = "controls") {
                OutlinedButton(
                    onClick = { showSamples = !showSamples },
                    enabled = historyStats?.sampleCount?.let { it > 0 } == true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showSamples) "Hide timestamped samples" else "Read timestamped samples")
                }
            }
            if (showSamples) {
                item(key = "samples_description", contentType = "heading") {
                    Text(
                        "Latest exact samples first · ${historyStats?.sampleCount ?: 0} readings",
                        style = MaterialTheme.typography.labelMedium,
                        color = Dim,
                    )
                }
                items(
                    count = exactSamples.size,
                    key = { "sample:${exactSamples[it].value.epochMillis}:${exactSamples[it].index}" },
                    contentType = { "sample_row" },
                ) { index -> ThermalSampleRow(exactSamples[index].value) }
            }
        }

        item(key = "cpu_heading", contentType = "heading") {
            SectionHeading("CPU core analytics", "${model.cores.size} physical ${if (model.cores.size == 1) "core" else "cores"}")
        }
        item(key = "cpu_summary", contentType = "metrics") {
            CpuThermalSummary(model)
        }
        addCoreClassSummary(model.performanceCores, "performance_summary", "Performance cores")
        addCoreClassSummary(model.efficiencyCores, "efficiency_summary", "Efficiency cores")
        if (model.cores.isEmpty()) {
            item(key = "cores_empty", contentType = "empty") {
                ThermalEmptyState("Per-core temperatures are not reported. The CPU package sensor remains available when present.")
            }
        } else {
            items(
                count = model.cores.size,
                key = { "core:${model.cores[it].sensor.key}" },
                contentType = { "thermal_sensor" },
            ) { index ->
                val core = model.cores[index]
                ThermalSensorCard(
                    sensor = core.sensor,
                    title = "Physical core ${core.physicalCoreId?.toString() ?: "ID unavailable"}",
                    supporting = buildList {
                        if (core.sensor.coreType.isNotBlank()) add(core.sensor.coreType.capitalized())
                        if (core.logicalCpus.isNotEmpty()) add("Logical CPUs ${core.logicalCpus.joinToString(", ")}")
                        core.loadPct?.let { add("Load ${pct(it)}") }
                        core.minFreqMHz?.let { minimum ->
                            add(if (core.maxFreqMHz == minimum) "$minimum MHz" else "$minimum–${core.maxFreqMHz} MHz")
                        }
                        hardwareLimits(core.sensor)?.let(::add)
                    }.joinToString(" · ").ifBlank { "Logical CPU association not reported" },
                    selected = selected?.key == core.sensor.key,
                    onClick = { selectedKey = core.sensor.key },
                )
            }
        }

        addSensorSection(
            model = model,
            category = SensorCategory.MEMORY,
            heading = "Memory",
            selectedKey = selected?.key,
            emptyText = "No DIMM temperature sensors are reported.",
            onSelect = { selectedKey = it },
        )

        item(key = "nvme_heading", contentType = "heading") {
            SectionHeading("NVMe", "${model.nvmeGroups.size} physical ${if (model.nvmeGroups.size == 1) "device" else "devices"}")
        }
        if (model.nvmeGroups.isEmpty()) {
            item(key = "nvme_empty", contentType = "empty") { ThermalEmptyState("No NVMe temperatures are reported.") }
        } else {
            model.nvmeGroups.forEach { group ->
                item(key = "nvme_group:${group.id}", contentType = "sensor_group") {
                    val drive = group.sensors.firstNotNullOfOrNull { smartForSensor(it, stats.smart) }
                    Text(
                        listOfNotNull(group.label, drive?.model?.takeIf(String::isNotBlank)).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMain,
                    )
                    Text(
                        listOfNotNull(
                            if (group.id.startsWith("/dev/")) null else "Composite and additional controller sensors",
                            drive?.serial?.takeIf(String::isNotBlank)?.let { "S/N $it" },
                        ).joinToString(" · ").ifBlank { "SMART identity not reported" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                    )
                }
                items(
                    count = group.sensors.size,
                    key = { "nvme_sensor:${group.sensors[it].key}" },
                    contentType = { "thermal_sensor" },
                ) { index ->
                    val sensor = group.sensors[index]
                    ThermalSensorCard(
                        sensor = sensor,
                        title = sensor.label,
                        supporting = listOfNotNull(
                            sensor.sensor.takeIf(String::isNotBlank)?.let { "Channel $it" },
                            sensor.chip.takeIf(String::isNotBlank),
                            hardwareLimits(sensor),
                        ).joinToString(" · ").ifBlank { "NVMe temperature channel" },
                        selected = selected?.key == sensor.key,
                        onClick = { selectedKey = sensor.key },
                    )
                }
            }
        }

        addSensorSection(
            model = model,
            category = SensorCategory.SATA,
            heading = "SATA / SMART",
            selectedKey = selected?.key,
            emptyText = "No normalized SATA SMART temperatures are reported.",
            supporting = { sensor ->
                val smart = smartForSensor(sensor, stats.smart)
                listOfNotNull(
                    sensor.device.takeIf(String::isNotBlank),
                    smart?.model?.takeIf(String::isNotBlank),
                    smart?.let(::smartTemperatureBounds),
                ).joinToString(" · ").ifBlank { "SMART temperature" }
            },
            onSelect = { selectedKey = it },
        )
        addSensorSection(
            model = model,
            category = SensorCategory.BOARD,
            heading = "Board",
            selectedKey = selected?.key,
            emptyText = "No board temperature sensor is reported.",
            onSelect = { selectedKey = it },
        )
        addSensorSection(
            model = model,
            category = SensorCategory.OTHER,
            heading = "Other components",
            selectedKey = selected?.key,
            emptyText = null,
            onSelect = { selectedKey = it },
        )

        item(key = "zones_heading", contentType = "heading") {
            SectionHeading("Firmware thermal envelope", "${model.zones.size} zones")
        }
        if (model.zones.isEmpty()) {
            item(key = "zones_empty", contentType = "empty") {
                ThermalEmptyState("No firmware thermal zones are exposed by this watchdog.")
            }
        } else {
            items(
                count = model.zones.size,
                key = { "zone:${model.zones[it].key}" },
                contentType = { "thermal_zone" },
            ) { index ->
                val zone = model.zones[index]
                ThermalZoneCard(
                    zone = zone,
                    selected = selected?.key == zone.key,
                    onClick = { selectedKey = zone.key },
                )
            }
        }

        item(key = "fans_heading", contentType = "heading") {
            SectionHeading("Cooling response", "${stats.fans.size} fans")
        }
        if (stats.fans.isEmpty()) {
            item(key = "fans_empty", contentType = "empty") { ThermalEmptyState("No fan telemetry is reported.") }
        } else {
            items(
                count = stats.fans.size,
                key = { "fan:${stats.fans[it].key}:${stats.fans[it].label}:$it" },
                contentType = { "fan" },
            ) { index ->
                val fan = stats.fans[index]
                val fanHistory = remember(history.samples, fan.key) { fanHistorySummary(history.samples, fan.key) }
                FanCoolingCard(fan, fanHistory, range)
            }
        }
        item(key = "power_throttle", contentType = "metrics") { PowerAndThrottle(stats) }

        item(key = "active_alerts_heading", contentType = "heading") {
            SectionHeading("Active thermal alerts", "${activeAlerts.size} active")
        }
        if (activeAlerts.isEmpty()) {
            item(key = "active_alerts_empty", contentType = "empty") {
                Text("No active temperature or cooling alerts.", style = MaterialTheme.typography.bodyMedium, color = Dim)
            }
        } else {
            items(
                count = activeAlerts.size,
                key = { "thermal_alert:${activeAlerts[it].id}:$it" },
                contentType = { "alert" },
            ) { index -> ActiveIssue(activeAlerts[index]) }
        }

        item(key = "events_heading", contentType = "heading") {
            SectionHeading("Recent temperature events", "Server event buffer")
        }
        when {
            baseUrl.isBlank() -> item(key = "events_preview", contentType = "empty") {
                Text("Live event history is unavailable in previews.", style = MaterialTheme.typography.bodyMedium, color = Dim)
            }
            telemetry.feedStatus != FeedStatus.LIVE -> item(key = "events_offline", contentType = "empty") {
                Text("Reconnect to refresh the event history. Active alerts above remain from this snapshot.", style = MaterialTheme.typography.bodyMedium, color = Dim)
            }
            eventsLoading && recentEvents.isEmpty() -> item(key = "events_loading", contentType = "loading") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Loading recent thermal events…", style = MaterialTheme.typography.bodyMedium, color = Dim)
                }
            }
            else -> {
                eventsError?.let { error ->
                    item(key = "events_error", contentType = "notice") { Notice("Event history unavailable", error) }
                }
                if (recentEvents.isEmpty() && eventsError == null) {
                    item(key = "events_empty", contentType = "empty") {
                        Text("No recent temperature or cooling events were reported.", style = MaterialTheme.typography.bodyMedium, color = Dim)
                    }
                }
                items(
                    count = recentEvents.size,
                    key = { "event:${recentEvents[it].kind}:${recentEvents[it].alert.id}:${eventTimestamp(recentEvents[it])}:$it" },
                    contentType = { "event" },
                ) { index -> ThermalEventRow(recentEvents[index]) }
            }
        }
    }
}

@Composable
private fun ThermalHero(model: ThermalModel, telemetry: TelemetryUiState) {
    val color = healthColor(model.health)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceHigh,
        border = BorderStroke(1.dp, color.copy(alpha = if (model.health == ThermalHealth.NO_DATA) 0.4f else 0.65f)),
        shape = SignalPlateShape,
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "THERMAL TELEMETRY · ${healthLabel(model.health).uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = MachineFont,
                color = color,
                letterSpacing = 0.7.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    model.hottest?.c?.let { num(it) } ?: "—",
                    style = NumberTextStyle,
                    fontSize = heroNumberSize(64),
                    lineHeight = heroNumberSize(66),
                    fontWeight = FontWeight.Medium,
                    color = color,
                )
                if (model.hottest != null) {
                    Text(
                        "°C",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Dim,
                        modifier = Modifier.padding(start = 5.dp, bottom = 7.dp),
                    )
                }
            }
            Text(
                model.hottest?.let { "Hottest component · ${it.label}" } ?: "No physical component temperature is available",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                buildList {
                    model.cpuPackage?.c?.let { add("Package ${temperature(it)}") }
                    model.hottestCore?.c?.let { add("Hot core ${temperature(it)} (${model.hottestCore.label})") }
                }.joinToString(" · ").ifBlank { "Firmware zones are not counted as additional physical components." },
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                freshnessLabel(telemetry.feedStatus, telemetry.snapshot?.receivedAt),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MachineFont,
                color = if (telemetry.error == null) Dim else Red,
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Line.copy(alpha = 0.65f))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Readout(
                        "Critical headroom",
                        model.criticalHeadroom?.headroomC?.let { signedTemperature(it) } ?: "Not reported",
                        model.criticalHeadroom?.let { "${it.sensor.label} → ${temperature(it.limitC)} limit" }
                            ?: "No component critical limit reported",
                        color = if (model.criticalHeadroom?.headroomC?.let { it <= 0.0 } == true) Red else TextMain,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Readout(
                        "Physical core spread",
                        model.coreSpreadC?.let { signedTemperature(it) } ?: "Not reported",
                        if (model.cores.size >= 2) "Hottest minus coolest physical core" else "At least two core sensors are required",
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedSensorSummary(
    sensor: ThermalSensor?,
    stats: ThermalHistoryStats?,
    history: HistoryUiState,
    range: HistoryRange,
) {
    val pending = history.loading && history.samples.isEmpty()
    fun formatted(value: Double?, signed: Boolean = false): String = when {
        pending -> "Loading"
        value == null -> "No samples"
        signed -> signedTemperature(value)
        else -> temperature(value)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Readout("Current", sensor?.c?.let(::temperature) ?: "Not reported", sensor?.label ?: "Select a sensor")
            }
            Column(Modifier.weight(1f)) {
                Readout("Latest sample", formatted(stats?.latestC), "${stats?.sampleCount ?: 0} samples · ${range.label}")
            }
        }
        ThermalMetricGrid(
            listOf(
                ThermalMetric("Minimum", formatted(stats?.minimumC), "Within selected range"),
                ThermalMetric("Average", formatted(stats?.averageC), "Mean of valid samples"),
                ThermalMetric("Maximum", formatted(stats?.maximumC), "Within selected range"),
                ThermalMetric("Change", formatted(stats?.changeC, signed = true), "Latest minus first sample"),
                ThermalMetric(
                    "Critical headroom",
                    stats?.headroomC?.let(::signedTemperature) ?: "Not reported",
                    sensor?.let(::criticalThreshold)?.let { "To most conservative ${temperature(it)} limit" }
                        ?: "No critical limit for this sensor",
                ),
            ),
        )
    }
}

private data class ThermalMetric(val label: String, val value: String, val supporting: String)

@Composable
private fun ThermalMetricGrid(metrics: List<ThermalMetric>) {
    metrics.chunked(2).forEach { rowMetrics ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            rowMetrics.forEach { metric ->
                Column(Modifier.weight(1f)) { Readout(metric.label, metric.value, metric.supporting) }
            }
            if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ThresholdRunReadout(label: String, threshold: Double?, run: ThresholdRun?) {
    val value = when {
        threshold == null -> "Not reported"
        run?.longestMillis == null -> "None observed"
        run.currentMillis != null -> compactDuration(run.currentMillis)
        else -> "Not active now"
    }
    val supporting = when {
        threshold == null -> "Threshold missing for this sensor"
        run?.longestMillis == null -> "No contiguous samples at or above ${temperature(threshold)}"
        else -> "Longest contiguous period ${compactDuration(run.longestMillis)} at or above ${temperature(threshold)}"
    }
    Readout(label, value, supporting, color = if (run?.currentMillis != null) Amber else TextMain)
}

@Composable
private fun CpuThermalSummary(model: ThermalModel) {
    Surface(color = SurfaceHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Readout(
                        "CPU package",
                        model.cpuPackage?.c?.let(::temperature) ?: "Not reported",
                        model.cpuPackage?.label ?: "No CPU package sensor",
                        color = model.cpuPackage?.let(::sensorColor) ?: TextMain,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Readout(
                        "Hottest core",
                        model.hottestCore?.c?.let(::temperature) ?: "Not reported",
                        model.hottestCore?.let { core ->
                            model.cpuPackage?.c?.let { packageC ->
                                core.c?.let { "${core.label} · ${signedTemperature(it - packageC)} vs package" }
                            } ?: core.label
                        } ?: "No physical core sensors",
                        color = model.hottestCore?.let(::sensorColor) ?: TextMain,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.addCoreClassSummary(
    summary: CoreTypeTemperature?,
    key: String,
    title: String,
) {
    if (summary == null) return
    item(key = key, contentType = "core_class") {
        Surface(color = SurfaceHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text("${summary.hottest.label} is hottest", style = MaterialTheme.typography.bodySmall, color = Dim)
                }
                Text(
                    "Peak ${temperature(summary.hottest.c)}\nAverage ${temperature(summary.averageC)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = MachineFont,
                    color = sensorColor(summary.hottest),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.addSensorSection(
    model: ThermalModel,
    category: SensorCategory,
    heading: String,
    selectedKey: String?,
    emptyText: String?,
    supporting: (ThermalSensor) -> String = { sensor ->
        listOfNotNull(
            sensor.chip.takeIf(String::isNotBlank),
            sensor.device.takeIf(String::isNotBlank),
            hardwareLimits(sensor),
            sensor.alarms.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Alarms: $it" },
        ).joinToString(" · ").ifBlank { category.label }
    },
    onSelect: (String) -> Unit,
) {
    val sensors = model.physical.filter { it.category == category }
    if (sensors.isEmpty() && emptyText == null) return
    item(key = "section:${category.name}", contentType = "heading") {
        SectionHeading(heading, "${sensors.size} ${if (sensors.size == 1) "sensor" else "sensors"}")
    }
    if (sensors.isEmpty()) {
        item(key = "empty:${category.name}", contentType = "empty") { ThermalEmptyState(emptyText.orEmpty()) }
    } else {
        items(
            count = sensors.size,
            key = { "sensor:${category.name}:${sensors[it].key}" },
            contentType = { "thermal_sensor" },
        ) { index ->
            val sensor = sensors[index]
            ThermalSensorCard(
                sensor = sensor,
                title = sensor.label,
                supporting = supporting(sensor),
                selected = selectedKey == sensor.key,
                onClick = { onSelect(sensor.key) },
            )
        }
    }
}

@Composable
private fun ThermalSensorCard(
    sensor: ThermalSensor,
    title: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = sensorColor(sensor)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        color = SurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) Green else Line.copy(alpha = 0.65f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
                    Text(
                        supporting + if (selected) " · Showing history" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) Green else Dim,
                    )
                }
                Text(
                    temperature(sensor.c),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = MachineFont,
                    color = color,
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { temperatureProgress(sensor) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = color,
                trackColor = Line,
            )
        }
    }
}

@Composable
private fun ThermalZoneCard(zone: ThermalZone, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = SurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) Green else Line.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(zone.label, style = MaterialTheme.typography.titleMedium, color = TextMain)
                    Text(zone.key, style = MaterialTheme.typography.labelSmall, fontFamily = MachineFont, color = Dim)
                }
                Text(temperature(zone.c), style = MaterialTheme.typography.headlineSmall, fontFamily = MachineFont)
            }
            Text(
                "Mode: ${zone.mode.ifBlank { "Not reported" }.displayName()} · Policy: ${zone.policy.ifBlank { "Not reported" }.displayName()}" +
                    if (selected) " · Showing history" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (zone.mode.equals("disabled", true)) Dim else Green,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (zone.trips.isEmpty()) {
                Text("No valid trip points reported.", style = MaterialTheme.typography.bodySmall, color = Dim)
            } else {
                zone.trips.forEach { trip -> ThermalTripRow(trip) }
            }
        }
    }
}

@Composable
private fun ThermalTripRow(trip: ThermalTrip) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Trip ${trip.index}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MachineFont,
            color = Dim,
            modifier = Modifier.weight(0.45f),
        )
        Column(Modifier.weight(1f)) {
            Text(trip.type.ifBlank { "Type not reported" }.displayName(), style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    trip.tempC == null && trip.hystC == null -> "Temperature and hysteresis not reported"
                    trip.tempC == null -> "Temperature not reported · Hysteresis ${temperature(trip.hystC)}"
                    trip.hystC == null -> "${temperature(trip.tempC)} · Hysteresis not reported"
                    else -> "${temperature(trip.tempC)} · Hysteresis ${temperature(trip.hystC)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        }
    }
}

@Composable
private fun FanCoolingCard(fan: Fan, history: FanHistorySummary, range: HistoryRange) {
    Surface(color = SurfaceHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(fan.label.ifBlank { fan.key.ifBlank { "Unnamed fan" } }, style = MaterialTheme.typography.titleMedium)
            Text(
                fan.key.ifBlank { "Fan key not reported · history cannot be correlated" },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MachineFont,
                color = Dim,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { Readout("Current speed", "${fan.rpm} RPM", "Current fan tachometer") }
                Column(Modifier.weight(1f)) { Readout("PWM command", "${fan.pwm} / 255", "${pct(fan.pwm / 255.0 * 100.0)} duty") }
            }
            Text(
                if (history.minimumRpm == null) "No keyed fan samples in ${range.label}"
                else "${range.label} speed range ${history.minimumRpm}–${history.maximumRpm} RPM",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
            Spacer(Modifier.height(8.dp))
            MiniTrend(
                points = history.points,
                color = Blue,
                domainMin = 0.0,
                domainMax = history.maximumRpm?.toDouble()?.plus(100.0),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )
        }
    }
}

@Composable
private fun PowerAndThrottle(stats: Stats) {
    Surface(color = SurfaceHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Power & throttle context", style = MaterialTheme.typography.titleLarge)
            ThermalMetricLine(
                "CPU package power",
                if (stats.power.packageWatts.isFinite() && stats.power.packageWatts > 0.0) "${num(stats.power.packageWatts)} W" else "Not reported",
            )
            val throttle = stats.throttle
            if (throttle == null) {
                ThermalMetricLine("CPU throttle counters", "Not reported by this watchdog")
            } else {
                ThermalMetricLine(
                    "Package thermal throttling",
                    "${throttle.packageCount} events · ${compactDuration(throttle.packageTotalMs)} cumulative",
                )
                ThermalMetricLine(
                    "Core thermal throttling",
                    "${throttle.coreCount} events · ${compactDuration(throttle.coreTotalMs)} cumulative",
                )
            }
            Text(
                "Counters are cumulative since boot; a nonzero value does not necessarily indicate current throttling.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ThermalMetricLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Dim, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = MachineFont, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ThermalEventRow(event: AlertEvent) {
    val alert = event.alert
    val fired = event.kind.equals("fired", true)
    val color = if (!fired) Green else alertColor(alert)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "${event.kind.ifBlank { "event" }.displayName().uppercase()} · ${alert.title.ifBlank { "Thermal alert" }}",
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        if (alert.detail.isNotBlank()) Text(alert.detail, style = MaterialTheme.typography.bodySmall, color = Dim)
        val timestamp = eventTimestamp(event)
        val epoch = parseTimestampEpoch(timestamp)
        Text(
            when {
                timestamp.isBlank() -> "Event time not reported"
                epoch == null -> "Unrecognized event timestamp: $timestamp"
                else -> "${formatDateTime(epoch)} · ${relativeTimestamp(epoch)}" +
                    if (event.ts.isBlank()) " · legacy event uses alert start" else ""
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = MachineFont,
            color = color,
            modifier = Modifier.padding(top = 4.dp),
        )
        HorizontalDivider(Modifier.padding(top = 10.dp), color = Line.copy(alpha = 0.55f))
    }
}

@Composable
private fun ThermalSampleRow(point: ChartPoint) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDateTime(point.epochMillis), style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.weight(1f))
        Text(temperature(point.value), style = MaterialTheme.typography.labelMedium, fontFamily = MachineFont)
    }
    HorizontalDivider(color = Line.copy(alpha = 0.5f))
}

@Composable
private fun ThermalHistoryLoading(label: String) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Waiting for samples in this range…", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ThermalEmptyState(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Dim, modifier = Modifier.padding(vertical = 8.dp))
}

private fun thermalChartSeries(sensor: ThermalSensor, points: List<ChartPoint>, range: HistoryRange): ChartSeries {
    val values = points.mapNotNull { it.value?.takeIf(Double::isFinite) }
    val thresholds = buildList {
        warningThreshold(sensor)?.let {
            add(ChartThreshold(it, "Warning · ${num(it, 0)}°C", ThresholdKind.WARNING))
        }
        sensor.limits?.maxC?.takeIf { it.isFinite() && it > 0.0 }?.let {
            add(ChartThreshold(it, "Hardware max · ${num(it, 0)}°C", ThresholdKind.WARNING))
        }
        sensor.crit.takeIf { it.isFinite() && it > 0.0 }?.let {
            add(ChartThreshold(it, "Critical · ${num(it, 0)}°C", ThresholdKind.CRITICAL))
        }
        hardwareCriticalThresholds(sensor).forEach { (label, value) ->
            add(ChartThreshold(value, "$label · ${num(value, 0)}°C", ThresholdKind.CRITICAL))
        }
    }.distinctBy { it.value to it.kind }
    val upper = (values + thresholds.map { it.value } + listOfNotNull(sensor.c)).maxOrNull() ?: 100.0
    val lower = minOf(values.minOrNull() ?: 0.0, 0.0)
    return ChartSeries(
        label = "${sensor.label} · ${range.label}",
        points = points,
        unit = "°C",
        domainMin = if (lower < 0.0) lower - 5.0 else 0.0,
        domainMax = upper + 5.0,
        thresholds = thresholds,
    )
}

private fun hardwareLimits(sensor: ThermalSensor): String? {
    val limits = sensor.limits ?: return null
    return listOfNotNull(
        limits.minC?.takeIf { it.isFinite() }?.let { "min ${num(it)}°C" },
        limits.maxC?.takeIf { it.isFinite() }?.let { "max ${num(it)}°C" },
        limits.lcritC?.takeIf { it.isFinite() }?.let { "low critical ${num(it)}°C" },
        limits.critC?.takeIf { it.isFinite() }?.let { "critical ${num(it)}°C" },
        limits.emergencyC?.takeIf { it.isFinite() }?.let { "emergency ${num(it)}°C" },
    ).joinToString(" · ").ifBlank { null }?.let { "Hardware $it" }
}

private fun smartTemperatureBounds(smart: com.grey.naswatchdog.Smart): String? {
    val minimum = smart.temperatureMinC?.takeIf { it.isFinite() }
    val maximum = smart.temperatureMaxC?.takeIf { it.isFinite() }
    return when {
        minimum != null && maximum != null -> "SMART observed ${num(minimum)}–${num(maximum)}°C"
        minimum != null -> "SMART observed minimum ${num(minimum)}°C"
        maximum != null -> "SMART observed maximum ${num(maximum)}°C"
        else -> null
    }
}

private fun temperature(value: Double?): String = value?.takeIf { it.isFinite() }?.let { "${num(it)}°C" } ?: "Not reported"

private fun signedTemperature(value: Double): String = when {
    !value.isFinite() -> "Not reported"
    value > 0.0 -> "+${num(value)}°C"
    else -> "${num(value)}°C"
}

private fun temperatureProgress(sensor: ThermalSensor): Float {
    val value = sensor.c?.takeIf { it.isFinite() } ?: return 0f
    val maximum = criticalThreshold(sensor) ?: warningThreshold(sensor)?.times(1.25) ?: 100.0
    return ((value - minOf(0.0, value)) / (maximum - minOf(0.0, value)).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
}

private fun compactDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return when {
        milliseconds in 0..999 -> "<1 s"
        seconds < 60L -> "$seconds s"
        seconds < 3600L -> "${seconds / 60L}m ${seconds % 60L}s"
        seconds < 86_400L -> "${seconds / 3600L}h ${seconds % 3600L / 60L}m"
        else -> "${seconds / 86_400L}d ${seconds % 86_400L / 3600L}h"
    }
}

private fun String.displayName(): String = replace('_', ' ').replaceFirstChar {
    if (it.isLowerCase()) it.titlecase() else it.toString()
}

private fun String.capitalized(): String = displayName()

private fun healthColor(health: ThermalHealth): Color = when (health) {
    ThermalHealth.NO_DATA -> Dim
    ThermalHealth.NORMAL -> Green
    ThermalHealth.WARNING -> Amber
    ThermalHealth.CRITICAL -> Red
}

private fun healthLabel(health: ThermalHealth): String = when (health) {
    ThermalHealth.NO_DATA -> "No thermal data"
    ThermalHealth.NORMAL -> "Within thresholds"
    ThermalHealth.WARNING -> "Warning threshold active"
    ThermalHealth.CRITICAL -> "Critical attention required"
}

private fun sensorColor(sensor: ThermalSensor): Color = when (sensorHealth(sensor)) {
    ThermalHealth.CRITICAL -> Red
    ThermalHealth.WARNING -> Amber
    else -> TextMain
}

private fun alertColor(alert: Alert): Color = when {
    alert.severity.equals("critical", true) -> Red
    alert.severity.equals("warning", true) -> Amber
    else -> Blue
}
