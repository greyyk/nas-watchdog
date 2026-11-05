package com.grey.naswatchdog.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grey.naswatchdog.Alert
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Blue
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Green
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.NumberTextStyle
import com.grey.naswatchdog.ui.Red
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.SignalPlateShape
import com.grey.naswatchdog.ui.TextMain
import com.grey.naswatchdog.ui.formatDateTime
import com.grey.naswatchdog.ui.parseTimestampEpoch
import com.grey.naswatchdog.ui.relativeTimestamp
import com.grey.naswatchdog.ui.heroNumberSize

@Composable
fun AlertsOverviewScreen(
    stats: Stats,
    modifier: Modifier = Modifier,
) {
    val alerts = stats.alerts
    val critical = alerts.count { it.severity.equals("critical", ignoreCase = true) }
    val warnings = alerts.count { it.severity.equals("warning", ignoreCase = true) }
    val other = alerts.size - critical - warnings
    val summaryColor = when {
        critical > 0 -> Red
        warnings > 0 -> Amber
        alerts.isNotEmpty() -> Blue
        else -> Green
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "alert_summary", contentType = "summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = com.grey.naswatchdog.ui.Surface,
                border = BorderStroke(1.dp, summaryColor.copy(alpha = 0.42f)),
                shape = SignalPlateShape,
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        "WATCHDOG ALERTS · ACTIVE SNAPSHOT",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = MachineFont,
                        color = Dim,
                        letterSpacing = 0.8.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        alerts.size.toString(),
                        style = NumberTextStyle,
                        fontSize = heroNumberSize(50),
                        lineHeight = heroNumberSize(56),
                        fontWeight = FontWeight.Medium,
                        color = summaryColor,
                    )
                    Text(
                        if (alerts.size == 1) "active alert" else "active alerts",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextMain,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            alerts.isEmpty() -> "No configured watchdog alert condition is currently met."
                            else -> buildList {
                                if (critical > 0) add("$critical critical")
                                if (warnings > 0) add("$warnings warning")
                                if (other > 0) add("$other other")
                            }.joinToString(" · ")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (alerts.isEmpty()) Dim else summaryColor,
                    )
                }
            }
        }

        if (alerts.isEmpty()) {
            item(key = "alerts_empty", contentType = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "No active watchdog alerts",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextMain,
                    )
                    Text(
                        "This only means no configured watchdog alert condition is currently met. " +
                            "It does not certify that all hardware or services are healthy.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Dim,
                    )
                    Text(
                        "Only active conditions supplied in this snapshot are shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                    )
                }
            }
        } else {
            item(key = "alerts_heading", contentType = "heading") {
                SectionHeading("Active issues", "Server priority order")
            }
            items(
                count = alerts.size,
                key = { alerts[it].id },
                contentType = { "alert" },
            ) { index ->
                AlertInstrumentRow(alerts[index])
            }
            item(key = "alerts_scope", contentType = "footnote") {
                Text(
                    "These are active watchdog conditions, not an event history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AlertInstrumentRow(alert: Alert) {
    val color = alertSeverityColor(alert.severity)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .width(3.dp)
                    .height(52.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    alert.severity.ifBlank { "notice" }.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = MachineFont,
                    color = color,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    alert.title.ifBlank { "Untitled watchdog alert" },
                    style = MaterialTheme.typography.titleLarge,
                    color = TextMain,
                    modifier = Modifier.padding(top = 6.dp).semantics { heading() },
                )
                Text(
                    alert.detail.ifBlank { "No additional detail was provided by the watchdog." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Dim,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    alertSinceLabel(alert),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        HorizontalDivider(color = Line.copy(alpha = 0.6f))
    }
}

private fun alertSeverityColor(severity: String): Color = when {
    severity.equals("critical", ignoreCase = true) -> Red
    severity.equals("warning", ignoreCase = true) -> Amber
    else -> Blue
}

private fun alertSinceLabel(alert: Alert): String {
    if (alert.since.isBlank()) return "Start time not reported"
    val epoch = parseTimestampEpoch(alert.since)
        ?: return "Reported start: ${alert.since} · Timestamp not recognized"
    return "Since ${formatDateTime(epoch)} · ${relativeTimestamp(epoch)}"
}
