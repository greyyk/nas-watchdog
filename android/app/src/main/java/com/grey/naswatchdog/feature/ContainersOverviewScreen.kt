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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grey.naswatchdog.Container
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.bytes
import com.grey.naswatchdog.connectionError
import com.grey.naswatchdog.fetchContainerLogs
import com.grey.naswatchdog.pct
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Green
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.Notice
import com.grey.naswatchdog.ui.NumberTextStyle
import com.grey.naswatchdog.ui.Red
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.SignalPlateShape
import com.grey.naswatchdog.ui.TextMain
import com.grey.naswatchdog.ui.heroNumberSize
import kotlinx.coroutines.CancellationException

private enum class ContainerStateFilter(val label: String) {
    ALL("All states"),
    RUNNING("Running only"),
    STOPPED("Stopped and other states"),
}

@Composable
fun ContainersOverviewScreen(
    stats: Stats,
    baseUrl: String,
    token: String,
    onRequestAction: (action: String, target: String, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(ContainerStateFilter.ALL.name) }
    val filter = ContainerStateFilter.entries.firstOrNull { it.name == filterName }
        ?: ContainerStateFilter.ALL
    var expandedName by rememberSaveable { mutableStateOf<String?>(null) }

    var logName by rememberSaveable { mutableStateOf<String?>(null) }
    var logText by remember { mutableStateOf<String?>(null) }
    var logError by remember { mutableStateOf<String?>(null) }
    var logsLoading by remember { mutableStateOf(false) }
    var logRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(baseUrl, token) {
        logName = null
        logText = null
        logError = null
        logsLoading = false
    }
    LaunchedEffect(logName, logRevision) {
        val name = logName ?: return@LaunchedEffect
        logsLoading = true
        logError = null
        try {
            logText = fetchContainerLogs(baseUrl, token, name, tail = 300)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logError = connectionError(error)
        } finally {
            logsLoading = false
        }
    }

    logName?.let { name ->
        ContainerLogDialog(
            name = name,
            text = logText,
            error = logError,
            loading = logsLoading,
            onRetry = { logRevision++ },
            onDismiss = { logName = null },
        )
    }

    val running = stats.containers.count { it.isRunning() }
    val unhealthy = stats.containers.count { it.health.equals("unhealthy", ignoreCase = true) }
    val visibleContainers = remember(stats.containers, query, filterName) {
        val needle = query.trim()
        stats.containers.filter { container ->
            val matchesState = when (filter) {
                ContainerStateFilter.ALL -> true
                ContainerStateFilter.RUNNING -> container.isRunning()
                ContainerStateFilter.STOPPED -> !container.isRunning()
            }
            matchesState && (
                needle.isEmpty() || container.name.contains(needle, ignoreCase = true) ||
                    container.image.contains(needle, ignoreCase = true)
                )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "container_summary", contentType = "summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = com.grey.naswatchdog.ui.Surface,
                border = BorderStroke(1.dp, Line.copy(alpha = 0.74f)),
                shape = SignalPlateShape,
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        "CONTAINER INVENTORY",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = MachineFont,
                        color = Dim,
                        letterSpacing = 0.8.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "$running / ${stats.containers.size}",
                        style = NumberTextStyle,
                        fontSize = heroNumberSize(42),
                        lineHeight = heroNumberSize(48),
                        fontWeight = FontWeight.Medium,
                        color = TextMain,
                    )
                    Text(
                        "containers running",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextMain,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$unhealthy unhealthy · ${stats.containers.size - running} stopped or in another state",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unhealthy > 0) Amber else Dim,
                    )
                    Text(
                        "Docker ${stats.dockerVersion.ifBlank { "version not reported" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (!stats.actionsEnabled) {
            item(key = "actions_disabled", contentType = "notice") {
                Notice(
                    title = "Container controls are disabled",
                    detail = "Set WD_ALLOW_ACTIONS=true on the watchdog to enable start, stop, and restart requests.",
                )
            }
        }

        item(key = "container_heading", contentType = "heading") {
            SectionHeading("Containers", "${visibleContainers.size} shown")
        }

        if (stats.containers.isNotEmpty()) {
            item(key = "container_filters", contentType = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        label = { Text("Find a container") },
                        placeholder = { Text("Name or image") },
                        singleLine = true,
                    )
                    Box(Modifier.fillMaxWidth()) {
                        var filterMenuOpen by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { filterMenuOpen = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("State filter: ${filter.label}")
                        }
                        DropdownMenu(
                            expanded = filterMenuOpen,
                            onDismissRequest = { filterMenuOpen = false },
                        ) {
                            ContainerStateFilter.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        filterName = option.name
                                        filterMenuOpen = false
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        when {
            stats.containers.isEmpty() -> {
                item(key = "containers_empty", contentType = "empty") {
                    Text(
                        "No containers are reported in this watchdog snapshot.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Dim,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            visibleContainers.isEmpty() -> {
                item(key = "containers_no_match", contentType = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "No containers match the current search and state filter.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Dim,
                        )
                        OutlinedButton(
                            onClick = {
                                query = ""
                                filterName = ContainerStateFilter.ALL.name
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Reset container filters")
                        }
                    }
                }
            }
            else -> {
                items(
                    count = visibleContainers.size,
                    key = { visibleContainers[it].name },
                    contentType = { "container" },
                ) { index ->
                    val container = visibleContainers[index]
                    ContainerInstrumentRow(
                        container = container,
                        actionsEnabled = stats.actionsEnabled,
                        expanded = expandedName == container.name,
                        onToggleExpanded = {
                            expandedName = if (expandedName == container.name) null else container.name
                        },
                        onShowLogs = {
                            logText = null
                            logError = null
                            logsLoading = true
                            logName = container.name
                            logRevision++
                        },
                        onRequestAction = onRequestAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContainerInstrumentRow(
    container: Container,
    actionsEnabled: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onShowLogs: () -> Unit,
    onRequestAction: (action: String, target: String, label: String) -> Unit,
) {
    Surface(
        onClick = onToggleExpanded,
        modifier = Modifier.fillMaxWidth(),
        color = com.grey.naswatchdog.ui.Surface,
        border = BorderStroke(1.dp, Line.copy(alpha = 0.68f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                container.name.ifBlank { "Unnamed container" },
                style = MaterialTheme.typography.titleLarge,
                color = TextMain,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "State · ${container.state.ifBlank { "not reported" }}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (container.isRunning()) Green else Dim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Health · ${container.health.ifBlank { "not reported" }}",
                    style = MaterialTheme.typography.labelLarge,
                    color = containerHealthColor(container.health),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
            if (container.isRunning()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    ContainerMetric(
                        label = "CPU",
                        value = pct(container.cpu),
                        modifier = Modifier.weight(1f),
                    )
                    ContainerMetric(
                        label = "Memory",
                        value = "${bytes(container.mem)} · ${pct(container.memPct)}",
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
            }
            Text(
                container.image.ifBlank { "Image not reported" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MachineFont,
                color = Dim,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                if (expanded) "Hide container details" else "Show container details",
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (expanded) {
                HorizontalDivider(
                    color = Line.copy(alpha = 0.65f),
                    modifier = Modifier.padding(vertical = 14.dp),
                )
                ContainerDetail(
                    label = "Network snapshot",
                    value = "Received ${bytes(container.netRx)} · Sent ${bytes(container.netTx)}",
                )
                ContainerDetail(
                    label = "Block I/O snapshot",
                    value = "Read ${bytes(container.blockRead)} · Written ${bytes(container.blockWrite)}",
                )
                ContainerDetail(
                    label = "Published ports",
                    value = if (container.ports.isEmpty()) "None reported" else container.ports.joinToString("\n"),
                )
            }

            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onShowLogs,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("View ${container.name.ifBlank { "container" }} logs")
            }
            if (actionsEnabled) {
                ContainerControls(
                    container = container,
                    onRequestAction = onRequestAction,
                )
            }
        }
    }
}

@Composable
private fun ContainerControls(
    container: Container,
    onRequestAction: (action: String, target: String, label: String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val name = container.name
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Container controls")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Restart $name container") },
                onClick = {
                    open = false
                    onRequestAction("container_restart", name, "Restart $name container")
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            if (container.isRunning()) {
                DropdownMenuItem(
                    text = { Text("Stop $name container") },
                    onClick = {
                        open = false
                        onRequestAction("container_stop", name, "Stop $name container")
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Start $name container") },
                    onClick = {
                        open = false
                        onRequestAction("container_start", name, "Start $name container")
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun ContainerMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Dim)
        Text(value, style = NumberTextStyle, fontSize = 20.sp, color = TextMain)
    }
}

@Composable
private fun ContainerDetail(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Dim)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = MachineFont,
            color = TextMain,
        )
    }
}

@Composable
private fun ContainerLogDialog(
    name: String,
    text: String?,
    error: String?,
    loading: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.grey.naswatchdog.ui.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge, color = TextMain)
                Text(
                    "LOG SNAPSHOT · UP TO 300 LINES",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MachineFont,
                    color = Dim,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (loading) {
                    if (text == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(
                                "Loading a log snapshot…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Dim,
                            )
                        }
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                    }
                }
                if (error != null) {
                    Text(
                        "Could not refresh the log snapshot. $error" +
                            if (text != null) " Showing the previous snapshot." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Red,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                when {
                    text == null && !loading -> Text(
                        if (error == null) "Preparing a log snapshot…" else "No log snapshot is available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dim,
                    )
                    text != null -> {
                        if (text.isBlank()) {
                            Text(
                                "The server returned an empty log snapshot for this container.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Dim,
                            )
                        } else {
                            SelectionContainer {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = MachineFont,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = TextMain,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Close logs")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onRetry,
                enabled = !loading,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (error == null) "Refresh snapshot" else "Retry loading logs")
            }
        },
    )
}

private fun Container.isRunning(): Boolean = state.equals("running", ignoreCase = true)

private fun containerHealthColor(health: String): Color = when {
    health.equals("healthy", ignoreCase = true) -> Green
    health.equals("unhealthy", ignoreCase = true) -> Red
    health.equals("starting", ignoreCase = true) -> Amber
    else -> Dim
}
