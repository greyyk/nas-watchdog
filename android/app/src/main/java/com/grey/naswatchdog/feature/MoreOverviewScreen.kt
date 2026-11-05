package com.grey.naswatchdog.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Bg
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.TextMain

private data class MoreDestination(
    val route: String,
    val title: String,
    val description: String,
)

@Composable
fun MoreOverviewScreen(
    onNavigate: (String) -> Unit,
    alertCount: Int,
    modifier: Modifier = Modifier,
) {
    val destinations = listOf(
        MoreDestination("alerts", "Alerts", "Current watchdog alerts"),
        MoreDestination("metric/thermal", "Thermal", "Component sensors, firmware zones, history, and cooling"),
        MoreDestination("network", "Network", "Interfaces, throughput, and TCP health"),
        MoreDestination("logs", "Logs", "Journal, kernel, and application log snapshots"),
        MoreDestination("actions", "Actions", "Scrub, reboot, and container controls"),
        MoreDestination("settings", "Settings", "Connection, alerts, and foreground refresh"),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = destinations,
            key = { it.route },
            contentType = { "destination" },
        ) { destination ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .clickable(role = Role.Button) { onNavigate(destination.route) }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            destination.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMain,
                        )
                        if (destination.route == "alerts" && alertCount > 0) {
                            Spacer(Modifier.width(10.dp))
                            Badge(
                                containerColor = Amber,
                                contentColor = Bg,
                                modifier = Modifier.semantics {
                                    contentDescription = "$alertCount active ${if (alertCount == 1) "alert" else "alerts"}"
                                },
                            ) {
                                Text(alertCount.toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(
                        destination.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                        modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Dim,
                )
            }
            HorizontalDivider(color = Line.copy(alpha = 0.6f))
        }
    }
}
