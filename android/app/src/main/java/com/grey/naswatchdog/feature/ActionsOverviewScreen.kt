package com.grey.naswatchdog.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grey.naswatchdog.Container
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.pct
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Bg
import com.grey.naswatchdog.ui.Blue
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.Notice
import com.grey.naswatchdog.ui.Red
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.SignalPlateShape
import com.grey.naswatchdog.ui.TextMain

@Composable
fun ActionsOverviewScreen(
    stats: Stats,
    onRequestAction: (action: String, target: String, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rebootAllowed = stats.actionsEnabled

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "actions_intro", contentType = "summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = com.grey.naswatchdog.ui.Surface,
                border = BorderStroke(1.dp, Line.copy(alpha = 0.74f)),
                shape = SignalPlateShape,
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        "CONTROL SURFACE",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = MachineFont,
                        color = Dim,
                        letterSpacing = 0.8.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Deliberate action.\nMeasured response.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = TextMain,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Maintenance requests can interrupt access to this NAS. " +
                            "The app asks for confirmation before sending each request.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dim,
                    )
                }
            }
        }

        if (!stats.actionsEnabled) {
            item(key = "actions_disabled", contentType = "notice") {
                Notice(
                    title = "Actions are disabled",
                    detail = "Set WD_ALLOW_ACTIONS=true on the watchdog to enable filesystem, host, and container controls.",
                )
            }
        }

        item(key = "filesystem_heading", contentType = "heading") {
            SectionHeading("Filesystem", "Btrfs maintenance")
        }
        item(key = "filesystem_controls", contentType = "controls") {
            ActionGroup {
                ActionStatus(
                    label = "Current scrub status",
                    value = stats.scrub.status.ifBlank { "Not reported" },
                )
                Text(
                    "A scrub verifies data against checksums without modifying file contents. " +
                        "It is read-only, but can add substantial disk I/O while running.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
                if (stats.scrub.status.isNotBlank() || stats.scrub.progress > 0.0) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            "Reported progress · ${pct(stats.scrub.progress)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMain,
                        )
                        LinearProgressIndicator(
                            progress = { (stats.scrub.progress / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = Blue,
                            trackColor = Line,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { onRequestAction("scrub_start", "", "Start filesystem scrub") },
                    enabled = stats.actionsEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Start filesystem scrub")
                }
                OutlinedButton(
                    onClick = { onRequestAction("scrub_cancel", "", "Cancel filesystem scrub") },
                    enabled = stats.actionsEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Cancel filesystem scrub")
                }
            }
        }

        item(key = "host_heading", contentType = "heading") {
            SectionHeading("Host", "Power controls")
        }
        item(key = "host_controls", contentType = "controls") {
            ActionGroup {
                Text(
                    "Rebooting interrupts all services, file transfers, and active connections. " +
                        "Cancel an automatic reboot to clear the watchdog's pending reboot request.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
                if (!stats.rebootEnabled) {
                    Text(
                        "Automatic recovery reboot is off (WD_ALLOW_REBOOT=false). Manual reboot here still requires WD_ALLOW_ACTIONS=true and your explicit confirmation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                    )
                }
                Button(
                    onClick = { onRequestAction("reboot", "", "Reboot NAS") },
                    enabled = rebootAllowed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Red,
                        contentColor = Bg,
                        disabledContainerColor = com.grey.naswatchdog.ui.SurfaceHigh,
                        disabledContentColor = Dim,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Reboot NAS")
                }
                OutlinedButton(
                    onClick = {
                        onRequestAction("reboot_cancel", "", "Cancel pending automatic reboot")
                    },
                    enabled = stats.actionsEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Cancel pending automatic reboot")
                }
            }
        }

        item(key = "containers_heading", contentType = "heading") {
            SectionHeading("Containers", "Restart individual services")
        }
        if (stats.containers.isEmpty()) {
            item(key = "containers_empty", contentType = "empty") {
                Text(
                    "No containers are reported in this snapshot.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Dim,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        } else {
            items(
                count = stats.containers.size,
                key = { stats.containers[it].name },
                contentType = { "container_action" },
            ) { index ->
                ContainerRestartControl(
                    container = stats.containers[index],
                    enabled = stats.actionsEnabled,
                    onRequestAction = onRequestAction,
                )
            }
        }
    }
}

@Composable
private fun ActionGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = com.grey.naswatchdog.ui.Surface,
        border = BorderStroke(1.dp, Line.copy(alpha = 0.68f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun ActionStatus(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Dim)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextMain)
    }
}

@Composable
private fun ContainerRestartControl(
    container: Container,
    enabled: Boolean,
    onRequestAction: (action: String, target: String, label: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            container.name.ifBlank { "Unnamed container" },
            style = MaterialTheme.typography.titleMedium,
            color = TextMain,
        )
        Text(
            "State · ${container.state.ifBlank { "not reported" }}",
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
        )
        OutlinedButton(
            onClick = {
                onRequestAction(
                    "container_restart",
                    container.name,
                    "Restart ${container.name} container",
                )
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Restart ${container.name.ifBlank { "container" }}")
        }
        HorizontalDivider(color = Line.copy(alpha = 0.6f))
    }
}
