package com.grey.naswatchdog.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.grey.naswatchdog.FeedStatus
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.TelemetryUiState
import com.grey.naswatchdog.bytes
import com.grey.naswatchdog.charts.HistoryRange
import com.grey.naswatchdog.num
import com.grey.naswatchdog.pct
import com.grey.naswatchdog.rate
import com.grey.naswatchdog.ui.ActiveIssue
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Blue
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.Notice
import com.grey.naswatchdog.ui.Readout
import com.grey.naswatchdog.ui.Red
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.SignalPlate
import com.grey.naswatchdog.ui.TextMain
import com.grey.naswatchdog.ui.chartPoints
import kotlinx.coroutines.delay

@Composable
fun WatchdogOverview(
    state: TelemetryUiState,
    onOpenCpu: () -> Unit,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    cpuValueModifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val snapshot = state.snapshot
    val stats = snapshot?.stats
    val history = state.histories[HistoryRange.ONE_HOUR]

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.feedStatus != FeedStatus.LIVE && stats != null) {
            item(key = "connection_notice", contentType = "notice") {
                Notice(
                    title = if (snapshot.cached) "Cached snapshot" else "Reconnecting to your NAS",
                    detail = (state.error ?: "The last known values are shown.") + " Controls may be unavailable.",
                )
            }
        }
        if (stats != null) {
            item(key = "signal_plate", contentType = "signal") {
                SignalPlate(
                    stats = stats,
                    status = if (state.refreshing && snapshot.cached) FeedStatus.RECONNECTING else state.feedStatus,
                    receivedAt = snapshot.receivedAt,
                    now = now,
                    points = chartPoints(history?.samples.orEmpty()) { it.cpu },
                    onOpenCpu = onOpenCpu,
                    modifier = modifier,
                    valueModifier = cpuValueModifier,
                )
            }
            if (stats.alerts.isNotEmpty()) {
                item(key = "alerts_heading", contentType = "heading") {
                    SectionHeading("Needs attention", "${stats.alerts.size} active")
                }
                items(
                    count = stats.alerts.take(3).size,
                    key = { stats.alerts[it].id },
                    contentType = { "alert" },
                ) { index -> ActiveIssue(stats.alerts[index]) }
                item(key = "alerts_link", contentType = "navigation") {
                    TextButton(onClick = { onNavigate("alerts") }) { Text("View active alerts") }
                }
            }
            item(key = "core_heading", contentType = "heading") {
                SectionHeading("Core telemetry", "Current")
            }
            item(key = "core_metrics", contentType = "metrics") {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Readout(
                            "Memory",
                            pct(stats.memPct),
                            "${bytes(stats.memUsed)} of ${bytes(stats.memTotal)}",
                            progress = (stats.memPct / 100).toFloat(),
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Readout(
                            "Pool",
                            pct(stats.poolPct),
                            "${bytes(stats.poolUsed)} used",
                            progress = (stats.poolPct / 100).toFloat(),
                            color = if (stats.poolRo) Red else TextMain,
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = Line.copy(alpha = 0.6f))
            }
            item(key = "load", contentType = "metric") {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Readout("Load average", num(stats.load1, 2), "5m ${num(stats.load5, 2)} · 15m ${num(stats.load15, 2)}")
                    }
                    Column(Modifier.weight(1f)) {
                        Readout("Host uptime", durationLabel(stats.uptime), "Power ${num(stats.power.packageWatts)} W")
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = Line.copy(alpha = 0.6f))
            }
            item(key = "io_heading", contentType = "heading") {
                SectionHeading("I/O & thermals")
            }
            item(key = "io_metrics", contentType = "metrics") {
                val physical = stats.net.filter { !it.virtual }
                val networkRate = physical.sumOf { it.rxRate + it.txRate }
                val diskRate = stats.disks.filter {
                    !it.name.startsWith("md") && !it.name.startsWith("dm-") && !it.name.startsWith("bcache")
                }.sumOf { it.readRate + it.writeRate }
                val cpuTemp = selectCpuTemperature(stats.temps)
                val dimmTemp = stats.temps.filter {
                    it.key.contains("dimm", true) || it.label.contains("DIMM", true)
                }.maxByOrNull { it.c }
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Readout("Network", rate(networkRate), "Physical rx + tx")
                    }
                    Column(Modifier.weight(1f)) {
                        Readout("Disk I/O", rate(diskRate), "Read + write")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onNavigate("metric/thermal") },
                ) {
                    Column(Modifier.weight(1f)) {
                        Readout(
                            "CPU temperature",
                            cpuTemp?.let { "${num(it.c)}°C" } ?: "Not reported",
                            cpuTemp?.label ?: "No CPU sensor matched",
                            color = temperatureColor(cpuTemp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Readout(
                            "DIMM temperature",
                            dimmTemp?.let { "${num(it.c)}°C" } ?: "Not reported",
                            dimmTemp?.label ?: "No memory sensor matched",
                            color = temperatureColor(dimmTemp),
                        )
                    }
                }
                TextButton(onClick = { onNavigate("metric/thermal") }) { Text("Inspect thermal telemetry") }
                androidx.compose.material3.HorizontalDivider(color = Line.copy(alpha = 0.6f))
            }
            item(key = "storage_summary", contentType = "summary") {
                SectionHeading("Storage", if (stats.poolRo) "Read-only" else "Writable")
                Text(
                    "${bytes(stats.poolUsed)} of ${bytes(stats.poolTotal)} in use",
                    style = MaterialTheme.typography.bodyMedium, color = TextMain,
                )
                Text(
                    "${stats.disks.size} block devices · ${stats.smart.size} SMART devices",
                    style = MaterialTheme.typography.bodySmall, color = Dim,
                )
                TextButton(onClick = { onNavigate("storage") }) { Text("Inspect storage") }
            }
            item(key = "services_summary", contentType = "summary") {
                val running = stats.containers.count { it.state == "running" }
                val unhealthy = stats.containers.count { it.health == "unhealthy" }
                SectionHeading("Services", if (unhealthy > 0) "$unhealthy unhealthy" else "$running running")
                Text(
                    "$running of ${stats.containers.size} containers running",
                    style = MaterialTheme.typography.bodyMedium, color = TextMain,
                )
                Text(
                    "Docker ${stats.dockerVersion.ifBlank { "reported by watchdog" }}",
                    style = MaterialTheme.typography.bodySmall, color = Dim,
                )
                TextButton(onClick = { onNavigate("containers") }) { Text("View containers") }
            }
        } else {
            item(key = "no_stats", contentType = "empty") {
                Spacer(Modifier.height(12.dp))
                Notice(
                    title = if (state.feedStatus == FeedStatus.FAILED) "Watchdog unavailable" else "Connecting to watchdog",
                    detail = state.error ?: "Waiting for the first live telemetry snapshot.",
                    color = if (state.feedStatus == FeedStatus.FAILED) Red else Blue,
                )
                TextButton(onClick = onRetry) { Text(if (state.refreshing) "Refreshing…" else "Try again") }
            }
        }
    }
}

internal fun selectCpuTemperature(temps: List<com.grey.naswatchdog.Temp>) = temps.firstOrNull { it.key == "cpu" }
    ?: temps.firstOrNull {
        it.label.contains("CPU", true) || it.label.contains("package", true) || it.key.contains("package", true)
    }

private fun temperatureColor(temp: com.grey.naswatchdog.Temp?) = when {
    temp == null -> TextMain
    temp.crit > 0 && temp.c >= temp.crit -> Red
    temp.warn > 0 && temp.c >= temp.warn -> Amber
    else -> TextMain
}

private fun durationLabel(seconds: Double): String {
    val days = (seconds / 86_400).toLong()
    val hours = ((seconds % 86_400) / 3_600).toLong()
    return if (days > 0) "${days}d ${hours}h" else "${hours}h ${((seconds % 3_600) / 60).toLong()}m"
}
