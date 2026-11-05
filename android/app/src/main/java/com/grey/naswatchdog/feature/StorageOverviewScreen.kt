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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grey.naswatchdog.BtrfsDf
import com.grey.naswatchdog.Disk
import com.grey.naswatchdog.Smart
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.TelemetryUiState
import com.grey.naswatchdog.bytes
import com.grey.naswatchdog.num
import com.grey.naswatchdog.pct
import com.grey.naswatchdog.rate
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Bg
import com.grey.naswatchdog.ui.Blue
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Green
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.NumberTextStyle
import com.grey.naswatchdog.ui.Red
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.SignalPlateShape
import com.grey.naswatchdog.ui.Surface
import com.grey.naswatchdog.ui.TextMain
import com.grey.naswatchdog.ui.heroNumberSize

@Composable
fun StorageOverviewScreen(
    stats: Stats,
    telemetry: TelemetryUiState,
    onRequestAction: (action: String, target: String, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allocations = remember(stats.btrfsDf) { stats.btrfsDf.entries.sortedBy { it.key } }
    val disks = remember(stats.disks) { stats.disks.sortedBy { it.name } }
    val smartDevices = remember(stats.smart) { stats.smart.sortedBy { it.device } }
    val poolStats = remember(stats.poolStats) { stats.poolStats.entries.sortedBy { it.key } }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "pool_hero", contentType = "instrument") {
            StoragePoolHero(stats)
        }
        item(key = "pool_details_heading", contentType = "heading") {
            SectionHeading("Pool details", "${stats.subvolumes} subvolumes")
        }
        item(key = "pool_details", contentType = "metrics") {
            Column(Modifier.fillMaxWidth()) {
                StorageMetricLine("Device", stats.poolDevice.ifBlank { "Not reported" })
                StorageMetricLine("Subvolumes", stats.subvolumes.toString())
                StorageMetricLine(
                    label = "Corruption total",
                    value = stats.poolCorruption.toString(),
                    supporting = "Reported cumulative corruption count",
                    color = if (stats.poolCorruption > 0L) Red else TextMain,
                )
            }
        }
        if (poolStats.isNotEmpty()) {
            item(key = "pool_counters_heading", contentType = "heading") {
                Text(
                    "Device counters",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain,
                )
            }
            items(
                count = poolStats.size,
                key = { "pool_counter:${poolStats[it].key}" },
                contentType = { "pool_counter_row" },
            ) { index ->
                val entry = poolStats[index]
                StorageMetricLine(
                    label = entry.key.replace('_', ' '),
                    value = entry.value.toString(),
                    color = if (entry.value > 0L && entry.key.contains("err", ignoreCase = true)) Amber else TextMain,
                )
            }
        }

        if (stats.bcache.present) {
            item(key = "bcache_heading", contentType = "heading") {
                SectionHeading("bcache", stats.bcache.state.ifBlank { "Present" })
            }
            item(key = "bcache_details", contentType = "metrics") {
                Column(Modifier.fillMaxWidth()) {
                    StorageMetricLine("Mode", stats.bcache.mode.ifBlank { "Not reported" })
                    StorageMetricLine("State", stats.bcache.state.ifBlank { "Not reported" })
                    StorageMetricLine("Hit ratio", pct(stats.bcache.hitRatio), color = Blue)
                    StorageMetricLine("Cache available", pct(stats.bcache.available, 0))
                    StorageMetricLine("Hits", stats.bcache.hits.toString(), "Cumulative cache hits")
                    StorageMetricLine("Misses", stats.bcache.misses.toString(), "Cumulative cache misses")
                }
            }
        }

        item(key = "scrub_heading", contentType = "heading") {
            SectionHeading("Filesystem scrub", storageScrubStatus(stats.scrub.status))
        }
        item(key = "scrub_status", contentType = "metrics") {
            StorageScrubStatus(stats)
        }
        item(key = "scrub_controls", contentType = "controls") {
            if (stats.actionsEnabled) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onRequestAction("scrub_start", "", "Start filesystem scrub") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start filesystem scrub")
                    }
                    OutlinedButton(
                        onClick = { onRequestAction("scrub_cancel", "", "Cancel filesystem scrub") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel filesystem scrub")
                    }
                    Text(
                        "Actions are confirmed and run by the watchdog app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                    )
                }
            } else {
                Text(
                    "Scrub controls are not enabled by this watchdog.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            }
        }

        item(key = "allocation_heading", contentType = "heading") {
            SectionHeading("btrfs allocation", "${allocations.size} reported")
        }
        if (allocations.isEmpty()) {
            item(key = "allocation_empty", contentType = "empty") {
                StorageEmptyText("No btrfs allocation details are available in this snapshot.")
            }
        } else {
            items(
                count = allocations.size,
                key = { "allocation:${allocations[it].key}" },
                contentType = { "allocation_row" },
            ) { index ->
                StorageAllocationRow(allocations[index].key, allocations[index].value)
            }
        }

        item(key = "disks_heading", contentType = "heading") {
            SectionHeading("Disks & I/O", "${disks.size} devices")
        }
        if (disks.isEmpty()) {
            item(key = "disks_empty", contentType = "empty") {
                StorageEmptyText("No block-device details are available in this snapshot.")
            }
        } else {
            items(
                count = disks.size,
                key = { "disk:${disks[it].name}" },
                contentType = { "disk_row" },
            ) { index ->
                StorageDiskRow(disks[index])
            }
        }

        item(key = "smart_heading", contentType = "heading") {
            SectionHeading("SMART devices", "${smartDevices.size} reported")
        }
        if (smartDevices.isEmpty()) {
            item(key = "smart_empty", contentType = "empty") {
                StorageEmptyText("No SMART device details are available in this snapshot.")
            }
        } else {
            items(
                count = smartDevices.size,
                key = { "smart:${smartDevices[it].device}" },
                contentType = { "smart_row" },
            ) { index ->
                StorageSmartRow(smartDevices[index])
            }
        }
    }
}

@Composable
private fun StoragePoolHero(stats: Stats) {
    val free = (stats.poolTotal - stats.poolUsed).coerceAtLeast(0L)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface,
        border = BorderStroke(1.dp, Line.copy(alpha = 0.74f)),
        shape = SignalPlateShape,
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                "POOL CAPACITY",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = MachineFont,
                color = Dim,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                pct(stats.poolPct),
                modifier = Modifier.fillMaxWidth(),
                style = NumberTextStyle,
                fontSize = heroNumberSize(56),
                lineHeight = heroNumberSize(64),
                fontWeight = FontWeight.Medium,
                color = TextMain,
            )
            Text(
                "Storage pool used",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
            )
            Text(
                stats.poolDevice.ifBlank { "Pool device not reported" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = MachineFont,
                color = Dim,
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { storageProgress(stats.poolPct) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = Blue,
                trackColor = Line,
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Line.copy(alpha = 0.7f))
            StorageMetricLine("Used", bytes(stats.poolUsed), pct(stats.poolPct) + " of pool capacity")
            StorageMetricLine("Total", bytes(stats.poolTotal))
            StorageMetricLine("Free", bytes(free))
            StorageMetricLine(
                label = "Write access",
                value = if (stats.poolRo) "Read-only" else "Writable",
                supporting = if (stats.poolRo) "The pool is mounted read-only" else "The pool accepts writes",
                color = if (stats.poolRo) Red else Green,
            )
        }
    }
}

@Composable
private fun StorageScrubStatus(stats: Stats) {
    val scrub = stats.scrub
    Column(Modifier.fillMaxWidth()) {
        StorageMetricLine("Status", storageScrubStatus(scrub.status))
        StorageMetricLine("Started", scrub.started.ifBlank { "Not reported" })
        StorageMetricLine(
            label = "Progress",
            value = if (scrub.progress.isFinite()) pct(scrub.progress) else "Not reported",
        )
        LinearProgressIndicator(
            progress = { storageProgress(scrub.progress) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Blue,
            trackColor = Line,
        )
        Spacer(Modifier.height(8.dp))
        StorageMetricLine(
            "Checksum errors",
            scrub.csumErrors.toString(),
            color = if (scrub.csumErrors > 0L) Amber else TextMain,
        )
        StorageMetricLine(
            "Read errors",
            scrub.readErrors.toString(),
            color = if (scrub.readErrors > 0L) Amber else TextMain,
        )
        StorageMetricLine(
            "Corrected errors",
            scrub.corrected.toString(),
            color = if (scrub.corrected > 0L) Blue else TextMain,
        )
        StorageMetricLine(
            "Uncorrectable errors",
            scrub.uncorrectable.toString(),
            color = if (scrub.uncorrectable > 0L) Red else TextMain,
        )
    }
}

@Composable
private fun StorageAllocationRow(name: String, allocation: BtrfsDf) {
    val usedFraction = if (allocation.total > 0L) {
        (allocation.used.toDouble() / allocation.total.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            name.replace('_', ' ').ifBlank { "Unnamed allocation" },
            style = MaterialTheme.typography.titleMedium,
            color = TextMain,
        )
        StorageMetricLine("Profile", allocation.profile.ifBlank { "Not reported" })
        StorageMetricLine("Used", bytes(allocation.used))
        StorageMetricLine("Total", bytes(allocation.total))
        usedFraction?.let {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { it },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Blue,
                trackColor = Line,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.55f))
}

@Composable
private fun StorageDiskRow(disk: Disk) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            disk.name.ifBlank { "Unnamed device" },
            style = MaterialTheme.typography.titleLarge,
            color = TextMain,
        )
        Text(
            disk.model.ifBlank { "Model not reported" },
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
        )
        Spacer(Modifier.height(4.dp))
        StorageMetricLine("Capacity", bytes(disk.size))
        StorageMetricLine(
            "Read",
            "${rate(disk.readRate)} · ${num(disk.readIops)} IOPS",
            "Current throughput and operations",
        )
        StorageMetricLine(
            "Write",
            "${rate(disk.writeRate)} · ${num(disk.writeIops)} IOPS",
            "Current throughput and operations",
        )
        StorageMetricLine("Device utilisation", pct(disk.util))
        LinearProgressIndicator(
            progress = { storageProgress(disk.util) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Blue,
            trackColor = Line,
        )
        Spacer(Modifier.height(12.dp))
    }
    HorizontalDivider(color = Line.copy(alpha = 0.55f))
}

private enum class StorageSmartHealth { PASSED, FAILED, UNAVAILABLE }

private fun storageSmartHealth(smart: Smart): StorageSmartHealth {
    if (smart.error.isNotBlank()) return StorageSmartHealth.UNAVAILABLE
    return when (smart.health.trim().uppercase()) {
        "PASSED", "PASS", "OK", "HEALTHY", "GOOD" -> StorageSmartHealth.PASSED
        "FAILED", "FAIL", "BAD", "UNHEALTHY", "CRITICAL" -> StorageSmartHealth.FAILED
        else -> StorageSmartHealth.UNAVAILABLE
    }
}

@Composable
private fun StorageSmartRow(smart: Smart) {
    val health = storageSmartHealth(smart)
    val healthText = when (health) {
        StorageSmartHealth.PASSED -> "Passed"
        StorageSmartHealth.FAILED -> "Failed"
        StorageSmartHealth.UNAVAILABLE -> "Unavailable"
    }
    val healthColor = when (health) {
        StorageSmartHealth.PASSED -> Green
        StorageSmartHealth.FAILED -> Red
        StorageSmartHealth.UNAVAILABLE -> Dim
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            smart.device.ifBlank { "Unnamed SMART device" },
            style = MaterialTheme.typography.titleLarge,
            color = TextMain,
        )
        Text(
            smart.model.ifBlank { "Model not reported" },
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
        )
        Spacer(Modifier.height(4.dp))
        StorageMetricLine("Health", healthText, color = healthColor)
        if (smart.error.isNotBlank()) {
            Text(
                "SMART query unavailable: ${smart.error}",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
            HorizontalDivider(color = Line.copy(alpha = 0.45f))
        } else if (health == StorageSmartHealth.UNAVAILABLE && smart.health.isNotBlank()) {
            StorageMetricLine("Reported health", smart.health, color = Dim)
        }
        StorageMetricLine("Model", smart.model.ifBlank { "Not reported" })
        if (smart.serial.isNotBlank()) {
            StorageMetricLine("Serial", smart.serial)
        }
        StorageMetricLine(
            "Temperature",
            if (smart.temp > 0.0 && smart.temp.isFinite()) "${num(smart.temp)}°C" else "Not reported",
        )
        StorageMetricLine("Power-on hours", "${smart.hours} h", "${smart.hours / 24L} days")
        StorageMetricLine(
            "Reallocated sectors",
            smart.realloc.toString(),
            color = storageCounterColor(smart.realloc),
        )
        StorageMetricLine(
            "Pending sectors",
            smart.pending.toString(),
            color = storageCounterColor(smart.pending),
        )
        StorageMetricLine(
            "Uncorrectable errors",
            smart.uncorrect.toString(),
            color = storageCounterColor(smart.uncorrect, severe = true),
        )
        StorageMetricLine("CRC errors", smart.crc.toString(), color = storageCounterColor(smart.crc))
        StorageMetricLine("Wear used", "${smart.wear}%", color = if (smart.wear > 0L) Blue else TextMain)
        StorageMetricLine(
            "Media errors",
            smart.mediaErrors.toString(),
            color = storageCounterColor(smart.mediaErrors, severe = true),
        )
    }
    HorizontalDivider(color = Line.copy(alpha = 0.55f))
}

private fun storageCounterColor(value: Long, severe: Boolean = false): Color = when {
    value <= 0L -> TextMain
    severe -> Red
    else -> Amber
}

private fun storageProgress(percent: Double): Float =
    if (percent.isFinite()) (percent / 100.0).toFloat().coerceIn(0f, 1f) else 0f

private fun storageScrubStatus(status: String): String =
    status.trim().replace('_', ' ').ifBlank { "Status not reported" }

@Composable
private fun StorageMetricLine(
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
private fun StorageEmptyText(value: String) {
    Text(
        value,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = Dim,
    )
}
