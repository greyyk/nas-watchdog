package com.grey.naswatchdog.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grey.naswatchdog.HistoryUiState
import com.grey.naswatchdog.NetIface
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.TelemetryUiState
import com.grey.naswatchdog.bytes
import com.grey.naswatchdog.charts.ChartPoint
import com.grey.naswatchdog.charts.HistoryRange
import com.grey.naswatchdog.pct
import com.grey.naswatchdog.rate
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Blue
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Green
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.MiniTrend
import com.grey.naswatchdog.ui.Notice
import com.grey.naswatchdog.ui.Readout
import com.grey.naswatchdog.ui.Red
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.TextMain
import com.grey.naswatchdog.ui.chartPoints

@Composable
fun NetworkOverviewScreen(
    stats: Stats,
    telemetry: TelemetryUiState,
    modifier: Modifier = Modifier,
) {
    val physical = remember(stats.net) { stats.net.filter { !it.virtual } }
    val virtual = remember(stats.net) { stats.net.filter { it.virtual } }
    val history = telemetry.histories[HistoryRange.ONE_HOUR]
    val historySamples = history?.samples.orEmpty()
    val receivePoints = remember(historySamples) { chartPoints(historySamples) { it.netRx } }
    val transmitPoints = remember(historySamples) { chartPoints(historySamples) { it.netTx } }
    var showVirtual by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (telemetry.error != null) {
            item(key = "telemetry_notice", contentType = "notice") {
                Notice("Telemetry refresh delayed", telemetry.error)
            }
        }
        item(key = "aggregate_heading", contentType = "heading") {
            SectionHeading("Aggregate traffic", "One-hour history")
        }
        item(key = "aggregate_receive", contentType = "network_trend") {
            NetworkTrend(
                label = "Aggregate receive rate",
                direction = "RX",
                currentRate = physical.sumOf { it.rxRate },
                cumulativeBytes = physical.sumOf { it.rxBytes },
                points = receivePoints,
                history = history,
                color = Blue,
            )
        }
        item(key = "aggregate_transmit", contentType = "network_trend") {
            NetworkTrend(
                label = "Aggregate transmit rate",
                direction = "TX",
                currentRate = physical.sumOf { it.txRate },
                cumulativeBytes = physical.sumOf { it.txBytes },
                points = transmitPoints,
                history = history,
                color = Green,
            )
        }
        if (history?.error != null) {
            item(key = "history_notice", contentType = "notice") {
                Notice("History refresh delayed", history.error)
            }
        }

        item(key = "physical_heading", contentType = "heading") {
            SectionHeading("Physical interfaces", "${physical.size} reported")
        }
        if (physical.isEmpty()) {
            item(key = "physical_empty", contentType = "empty") {
                Text(
                    "No physical interfaces are reported in this snapshot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            }
        } else {
            items(
                items = physical,
                key = { "physical:${it.name}" },
                contentType = { "physical_interface" },
            ) { iface ->
                InterfaceReadout(iface = iface, virtual = false)
            }
        }

        item(key = "virtual_heading", contentType = "heading") {
            SectionHeading("Virtual interfaces", "${virtual.size} reported")
        }
        if (virtual.isEmpty()) {
            item(key = "virtual_empty", contentType = "empty") {
                Text(
                    "No virtual interfaces are reported in this snapshot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            }
        } else {
            item(key = "virtual_toggle", contentType = "control") {
                OutlinedButton(
                    onClick = { showVirtual = !showVirtual },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        if (showVirtual) "Hide virtual interfaces" else "Show ${virtual.size} virtual interfaces",
                        modifier = Modifier.weight(1f),
                    )
                    Text(if (showVirtual) "−" else "+", fontFamily = MachineFont)
                }
            }
            if (showVirtual) {
                items(
                    items = virtual,
                    key = { "virtual:${it.name}" },
                    contentType = { "virtual_interface" },
                ) { iface ->
                    InterfaceReadout(iface = iface, virtual = true)
                }
            }
        }

        item(key = "tcp_heading", contentType = "heading") {
            SectionHeading("TCP & connection tracking", "Current snapshot")
        }
        item(key = "tcp_connections", contentType = "network_metrics") {
            NetworkMetricRow("Established TCP connections", stats.netStats.established.toString())
            NetworkMetricRow("TCP time wait", stats.netStats.timeWait.toString())
            NetworkMetricRow("TCP sockets in use", stats.netStats.inUse.toString())
            NetworkMetricRow("Total sockets in use", stats.netStats.sockets.toString())
            NetworkMetricRow(
                "TCP retransmits",
                "${pct(stats.netStats.retransPct, 3)} · ${stats.netStats.retrans} segments",
            )
        }
        item(key = "network_stack", contentType = "network_metrics") {
            NetworkMetricRow(
                "Conntrack entries",
                "${stats.netStats.conntrack} / ${stats.netStats.conntrackMax} max",
            )
            NetworkMetricRow("ARP entries", stats.netStats.arp.toString())
            NetworkMetricRow("Routes", stats.netStats.routes.toString())
            NetworkMetricRow(
                "Listen overflows",
                stats.netStats.listenOverflows.toString(),
                valueColor = if (stats.netStats.listenOverflows > 0L) Amber else TextMain,
            )
        }
    }
}

@Composable
private fun NetworkTrend(
    label: String,
    direction: String,
    currentRate: Double,
    cumulativeBytes: Long,
    points: List<ChartPoint>,
    history: HistoryUiState?,
    color: Color,
) {
    val hasSamples = points.any { it.value != null }
    Readout(
        label = "$label · $direction",
        value = rate(currentRate),
        supporting = "Current physical $direction · cumulative total ${bytes(cumulativeBytes)}",
        color = color,
    )
    Text(
        "One-hour aggregate history",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = MachineFont,
        color = Dim,
    )
    MiniTrend(
        points = points,
        color = color,
        domainMin = 0.0,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    )
    Text(
        when {
            history?.loading == true && !hasSamples -> "Loading one-hour history…"
            !hasSamples -> "No samples reported for this range."
            else -> "Missing samples remain as gaps."
        },
        style = MaterialTheme.typography.bodySmall,
        color = Dim,
    )
    HorizontalDivider(color = Line.copy(alpha = 0.6f))
}

@Composable
private fun InterfaceReadout(iface: NetIface, virtual: Boolean) {
    val state = iface.state.ifBlank { "unknown" }
    val isUp = state.equals("up", ignoreCase = true)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    iface.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = MachineFont,
                    color = TextMain,
                )
                Text(
                    if (virtual) "VIRTUAL INTERFACE" else "PHYSICAL INTERFACE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dim,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                state.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = MachineFont,
                fontWeight = FontWeight.Medium,
                color = if (isUp) Green else if (state.equals("down", true)) Red else Amber,
            )
        }
        Text(
            if (iface.speed > 0) "Link speed · ${iface.speed} Mb/s" else "Link speed · not reported",
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            DirectionReadout(
                title = "Receive",
                rate = rate(iface.rxRate),
                total = bytes(iface.rxBytes),
                color = Blue,
                modifier = Modifier.weight(1f),
            )
            DirectionReadout(
                title = "Transmit",
                rate = rate(iface.txRate),
                total = bytes(iface.txBytes),
                color = Green,
                modifier = Modifier.weight(1f),
            )
        }
        NetworkMetricRow(
            label = "Errors · RX + TX",
            value = iface.errors.toString(),
            valueColor = if (iface.errors > 0L) Amber else TextMain,
        )
        NetworkMetricRow(
            label = "Drops · RX + TX",
            value = iface.drops.toString(),
            valueColor = if (iface.drops > 0L) Amber else TextMain,
        )
        if (iface.mac.isNotBlank()) {
            NetworkMetricRow("MAC address", iface.mac)
        }
        HorizontalDivider(color = Line.copy(alpha = 0.65f))
    }
}

@Composable
private fun DirectionReadout(
    title: String,
    rate: String,
    total: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Dim)
        Text(rate, style = MaterialTheme.typography.titleMedium, fontFamily = MachineFont, color = color)
        Text(
            "Cumulative · $total",
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
        )
    }
}

@Composable
private fun NetworkMetricRow(
    label: String,
    value: String,
    valueColor: Color = TextMain,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = MachineFont,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider(color = Line.copy(alpha = 0.45f))
}
