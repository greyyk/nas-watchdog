package com.grey.naswatchdog.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grey.naswatchdog.Core
import com.grey.naswatchdog.Gpu
import com.grey.naswatchdog.Proc
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.TelemetryUiState
import com.grey.naswatchdog.bytes
import com.grey.naswatchdog.charts.ChartPoint
import com.grey.naswatchdog.charts.HistoryRange
import com.grey.naswatchdog.duration
import com.grey.naswatchdog.num
import com.grey.naswatchdog.pct
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Bg
import com.grey.naswatchdog.ui.Blue
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Green
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.MiniTrend
import com.grey.naswatchdog.ui.NumberTextStyle
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.SignalPlateShape
import com.grey.naswatchdog.ui.Surface
import com.grey.naswatchdog.ui.TextMain
import com.grey.naswatchdog.ui.chartPoints
import com.grey.naswatchdog.ui.formatClock
import com.grey.naswatchdog.ui.heroNumberSize

@Composable
fun SystemOverviewScreen(
    stats: Stats,
    telemetry: TelemetryUiState,
    onOpenCpu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val samples = telemetry.histories[HistoryRange.ONE_HOUR]?.samples.orEmpty()
    val cpuHistory = remember(samples) { chartPoints(samples) { it.cpu } }
    val memoryHistory = remember(samples) { chartPoints(samples) { it.memPct } }
    val cores = remember(stats.perCore) { stats.perCore.sortedBy { it.id } }
    val processes = remember(stats.processes) { stats.processes.take(12) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "compute_hero", contentType = "instrument") {
            SystemComputeHero(stats = stats, onOpenCpu = onOpenCpu)
        }
        item(key = "one_hour_instrument", contentType = "history") {
            SystemHistoryInstrument(stats = stats, cpuPoints = cpuHistory, memoryPoints = memoryHistory)
        }
        item(key = "cores_heading", contentType = "heading") {
            SectionHeading("Per-thread utilisation", "${cores.size} reported")
        }
        if (cores.isEmpty()) {
            item(key = "cores_empty", contentType = "empty") {
                SystemEmptyText("Per-thread details are not available in this snapshot.")
            }
        } else {
            items(
                count = cores.size,
                key = { "core:${cores[it].id}" },
                contentType = { "core_row" },
            ) { index ->
                SystemCoreRow(cores[index])
            }
        }

        item(key = "memory_heading", contentType = "heading") {
            SectionHeading("Memory & swap", bytes(stats.memTotal) + " total")
        }
        item(key = "memory_details", contentType = "metrics") {
            Column(Modifier.fillMaxWidth()) {
                SystemMetricLine(
                    label = "Memory used",
                    value = "${bytes(stats.memUsed)} / ${bytes(stats.memTotal)}",
                    supporting = "${pct(stats.memPct)} used · ${bytes(stats.memAvail)} available",
                )
                LinearProgressIndicator(
                    progress = { (stats.memPct / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Blue,
                    trackColor = Line,
                )
                Spacer(Modifier.height(6.dp))
                SystemMetricLine("Cached", bytes(stats.cached))
                SystemMetricLine("Buffers", bytes(stats.buffers))
                SystemMetricLine("Dirty", bytes(stats.dirty))
                SystemMetricLine("Writeback", bytes(stats.writeback))
                if (stats.swapTotal > 0L) {
                    SystemMetricLine(
                        label = "Swap used",
                        value = "${bytes(stats.swapUsed)} / ${bytes(stats.swapTotal)}",
                        supporting = pct(stats.swapUsed.toDouble() * 100.0 / stats.swapTotal.toDouble()) + " of swap",
                    )
                } else {
                    SystemMetricLine("Swap", "No swap capacity reported")
                }
                SystemMetricLine(
                    label = "OOM kills",
                    value = stats.oomKills.toString(),
                    supporting = "Cumulative out-of-memory kills",
                    color = if (stats.oomKills > 0L) Amber else TextMain,
                )
            }
        }

        item(key = "pressure_heading", contentType = "heading") {
            SectionHeading("Pressure", "PSI · avg10")
        }
        item(key = "pressure_details", contentType = "metrics") {
            Column(Modifier.fillMaxWidth()) {
                SystemMetricLine("CPU", pct(stats.psiCpu, 2), "Time some tasks stalled on CPU")
                SystemMetricLine("Memory", pct(stats.psiMem, 2), "Time some tasks stalled on memory")
                SystemMetricLine("I/O", pct(stats.psiIo, 2), "Time some tasks stalled on I/O")
            }
        }

        if (stats.gpus.isNotEmpty()) {
            item(key = "gpu_heading", contentType = "heading") {
                SectionHeading("Graphics", "${stats.gpus.size} reported")
            }
            items(
                count = stats.gpus.size,
                key = { "gpu:${stats.gpus[it].card}:${it}" },
                contentType = { "gpu_row" },
            ) { index ->
                SystemGpuRow(stats.gpus[index])
            }
        }

        item(key = "processes_heading", contentType = "heading") {
            SectionHeading("Processes", if (processes.isEmpty()) "None reported" else "Top ${processes.size}")
        }
        if (processes.isEmpty()) {
            item(key = "processes_empty", contentType = "empty") {
                SystemEmptyText("Process details are not available in this snapshot.")
            }
        } else {
            items(
                count = processes.size,
                key = { "process:${processes[it].pid}" },
                contentType = { "process_row" },
            ) { index ->
                SystemProcessRow(processes[index])
            }
        }

        item(key = "host_heading", contentType = "heading") {
            SectionHeading("Kernel & host", stats.system.timezone.takeIf { it.isNotBlank() })
        }
        item(key = "host_details", contentType = "metrics") {
            Column(Modifier.fillMaxWidth()) {
                SystemMetricLine("Operating system", stats.os.ifBlank { "Not reported" })
                SystemMetricLine("Kernel", stats.kernel.ifBlank { "Not reported" })
                SystemMetricLine("Uptime", duration(stats.uptime))
                SystemMetricLine(
                    label = "NTP",
                    value = if (stats.system.ntp) "Synchronized" else "Not synchronized",
                    color = if (stats.system.ntp) Green else Amber,
                )
                SystemMetricLine("Time zone", stats.system.timezone.ifBlank { "Not reported" })
                SystemMetricLine("Scheduler", "${stats.system.running} running · ${stats.system.blocked} blocked")
                SystemMetricLine("Context switches", stats.system.ctxt.toString(), "Cumulative since boot")
                SystemMetricLine(
                    label = "CPU throttle events",
                    value = stats.throttleCount.toString(),
                    supporting = "Cumulative package events, not an alert state",
                )
                if (stats.system.failedUnits.isEmpty()) {
                    SystemMetricLine("Failed units", "None reported")
                }
            }
        }
        if (stats.system.failedUnits.isNotEmpty()) {
            item(key = "failed_units_heading", contentType = "heading") {
                Text(
                    "Failed systemd units",
                    style = MaterialTheme.typography.titleMedium,
                    color = Amber,
                )
            }
            items(
                count = stats.system.failedUnits.size,
                key = { "failed_unit:${stats.system.failedUnits[it]}" },
                contentType = { "failed_unit_row" },
            ) { index ->
                Text(
                    stats.system.failedUnits[index],
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = MachineFont,
                    color = TextMain,
                )
                HorizontalDivider(color = Line.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun SystemComputeHero(stats: Stats, onOpenCpu: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        border = BorderStroke(1.dp, Line.copy(alpha = 0.74f)),
        shape = SignalPlateShape,
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SYSTEM COMPUTE",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = MachineFont,
                    color = Dim,
                )
                TextButton(onClick = onOpenCpu) {
                    Text("CPU details")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = pct(stats.cpu),
                modifier = Modifier.fillMaxWidth(),
                style = NumberTextStyle,
                fontSize = heroNumberSize(56),
                lineHeight = heroNumberSize(64),
                fontWeight = FontWeight.Medium,
                color = TextMain,
            )
            Text(
                "CPU utilisation",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
            )
            Text(
                "${stats.cores} threads · ${stats.governor.ifBlank { "governor not reported" }}" +
                    if (stats.maxFreq > 0L) " · max ${stats.maxFreq} MHz" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Line.copy(alpha = 0.7f))
            SystemMetricLine(
                label = "Load average",
                value = num(stats.load1, 2),
                supporting = "5 min ${num(stats.load5, 2)} · 15 min ${num(stats.load15, 2)}",
            )
            SystemMetricLine("Package power", "${num(stats.power.packageWatts)} W")
            Spacer(Modifier.height(12.dp))
            Text(
                "MEMORY",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = MachineFont,
                color = Dim,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                pct(stats.memPct),
                style = NumberTextStyle,
                fontSize = heroNumberSize(34),
                lineHeight = heroNumberSize(42),
                fontWeight = FontWeight.Medium,
                color = TextMain,
            )
            Text(
                "${bytes(stats.memUsed)} used of ${bytes(stats.memTotal)}",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMain,
            )
            Text(
                "${bytes(stats.memAvail)} available",
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (stats.memPct / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Blue,
                trackColor = Line,
            )
        }
    }
}

@Composable
private fun SystemHistoryInstrument(
    stats: Stats,
    cpuPoints: List<ChartPoint>,
    memoryPoints: List<ChartPoint>,
) {
    val timestamps = remember(cpuPoints, memoryPoints) {
        (cpuPoints + memoryPoints).map { it.epochMillis }
    }
    Column(Modifier.fillMaxWidth()) {
        SectionHeading(
            "One-hour instrument",
            if (timestamps.isEmpty()) "No samples" else "${formatClock(timestamps.min())}–${formatClock(timestamps.max())}",
        )
        Text(
            "Watchdog history · fixed 0–100% scale",
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
        )
        Spacer(Modifier.height(8.dp))
        SystemTrend("CPU", pct(stats.cpu), cpuPoints)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Line.copy(alpha = 0.55f),
        )
        SystemTrend("Memory", pct(stats.memPct), memoryPoints)
    }
}

@Composable
private fun SystemTrend(label: String, currentValue: String, points: List<ChartPoint>) {
    val sampleCount = points.count { it.value != null }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = TextMain,
        )
        Text(
            "Now $currentValue",
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = MachineFont,
            color = Blue,
        )
    }
    Text(
        if (sampleCount == 0) "No $label samples in this one-hour range" else "$sampleCount valid samples",
        style = MaterialTheme.typography.bodySmall,
        color = Dim,
    )
    Spacer(Modifier.height(6.dp))
    MiniTrend(
        points = points,
        color = Blue,
        domainMin = 0.0,
        domainMax = 100.0,
        modifier = Modifier.fillMaxWidth().height(46.dp),
    )
}

@Composable
private fun SystemCoreRow(core: Core) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CPU ${core.id}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMain,
            )
            Text(
                pct(core.usage),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = MachineFont,
                color = TextMain,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { (core.usage / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(4.dp),
                color = Blue,
                trackColor = Line,
            )
            Text(
                if (core.freq > 0L) "${core.freq} MHz" else "Frequency not reported",
                modifier = Modifier.padding(start = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MachineFont,
                color = Dim,
                textAlign = TextAlign.End,
            )
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.55f))
}

@Composable
private fun SystemGpuRow(gpu: Gpu) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            gpu.card.ifBlank { "Unnamed GPU" },
            style = MaterialTheme.typography.titleMedium,
            color = TextMain,
        )
        Spacer(Modifier.height(4.dp))
        SystemMetricLine("Frequency", if (gpu.freq > 0L) "${gpu.freq} MHz" else "Not reported")
        SystemMetricLine("Maximum frequency", if (gpu.max > 0L) "${gpu.max} MHz" else "Not reported")
        SystemMetricLine("RC6 time", if (gpu.rc6 > 0L) duration(gpu.rc6 / 1000.0) else "Not reported")
    }
}

@Composable
private fun SystemProcessRow(process: Proc) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                process.name.ifBlank { "Process ${process.pid}" },
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
            )
            Text(
                "${num(process.cpu)}% CPU",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = MachineFont,
                color = Blue,
            )
        }
        Text(
            "PID ${process.pid} · ${bytes(process.rss)} RSS · ${num(process.mem)}% memory",
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
        )
    }
    HorizontalDivider(color = Line.copy(alpha = 0.55f))
}

@Composable
private fun SystemMetricLine(
    label: String,
    value: String,
    supporting: String? = null,
    color: Color = TextMain,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(0.8f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Dim)
            supporting?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Dim)
            }
        }
        Text(
            value,
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = MachineFont,
            color = color,
            textAlign = TextAlign.End,
        )
    }
    HorizontalDivider(color = Line.copy(alpha = 0.45f))
}

@Composable
private fun SystemEmptyText(value: String) {
    Text(
        value,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = Dim,
    )
}
