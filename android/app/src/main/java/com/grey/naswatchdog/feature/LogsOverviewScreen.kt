package com.grey.naswatchdog.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.grey.naswatchdog.LogSource
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.connectionError
import com.grey.naswatchdog.fetchLog
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.Notice
import com.grey.naswatchdog.ui.Red
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.TextMain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private data class LogRequest(
    val revision: Long,
    val source: String,
    val lines: Int,
    val filter: String,
)

private data class LoadedLogSnapshot(
    val request: LogRequest,
    val lines: List<String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsOverviewScreen(
    stats: Stats,
    baseUrl: String,
    token: String,
    modifier: Modifier = Modifier,
) {
    val sources = remember(stats.logSources) {
        (listOf(
            LogSource("journal", "systemd journal", 0),
            LogSource("dmesg", "kernel dmesg", 0),
        ) + stats.logSources.filter { it.id != "journal" && it.id != "dmesg" }).distinctBy { it.id }
    }
    var selectedSource by rememberSaveable { mutableStateOf("journal") }
    var lineCount by rememberSaveable { mutableStateOf(300) }
    var filter by rememberSaveable { mutableStateOf("") }
    var requestSerial by remember { mutableLongStateOf(0L) }
    var request by remember { mutableStateOf(LogRequest(0L, "journal", 300, "")) }
    var snapshot by remember(baseUrl, token) { mutableStateOf<LoadedLogSnapshot?>(null) }
    var loading by remember(baseUrl, token) { mutableStateOf(true) }
    var error by remember(baseUrl, token) { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(sources) {
        if (sources.none { it.id == selectedSource }) selectedSource = "journal"
    }

    fun submit() {
        requestSerial += 1L
        request = LogRequest(requestSerial, selectedSource, lineCount, filter)
    }

    LaunchedEffect(baseUrl, token, request) {
        val activeRequest = request
        loading = true
        error = null
        try {
            val returnedLines = fetchLog(
                baseUrl = baseUrl,
                token = token,
                source = activeRequest.source,
                lines = activeRequest.lines,
                q = activeRequest.filter,
            )
            currentCoroutineContext().ensureActive()
            if (request == activeRequest) {
                snapshot = LoadedLogSnapshot(activeRequest, returnedLines)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (request == activeRequest) error = connectionError(e)
        } finally {
            if (request == activeRequest) loading = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "query_heading", contentType = "heading") {
            SectionHeading("Log query", "On-demand snapshot")
            Text(
                "Choose a source, filter, and line limit, then load. Log rows do not stream automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        }
        item(key = "source_heading", contentType = "heading") {
            Text("Source", style = MaterialTheme.typography.labelLarge, color = TextMain)
        }
        item(key = "source_selector", contentType = "source_controls") {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                itemsIndexed(
                    items = sources,
                    key = { index, source -> "source:${source.id}:$index" },
                    contentType = { _, _ -> "log_source" },
                ) { _, source ->
                    FilterChip(
                        selected = selectedSource == source.id,
                        onClick = { selectedSource = source.id },
                        label = { Text(source.id, fontFamily = MachineFont) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        item(key = "filter_input", contentType = "query_control") {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Filter regex") },
                placeholder = { Text("Optional regex") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        submit()
                        keyboardController?.hide()
                    },
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
        }
        item(key = "line_count", contentType = "query_control") {
            Text("Line limit", style = MaterialTheme.typography.labelLarge, color = TextMain)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(100, 300, 500, 1_000).forEach { count ->
                    FilterChip(
                        selected = lineCount == count,
                        onClick = { lineCount = count },
                        label = { Text("$count lines") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        item(key = "load_action", contentType = "query_control") {
            Button(
                onClick = ::submit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (loading) "Load again" else "Load logs")
            }
            if (selectedSource != request.source || lineCount != request.lines || filter != request.filter) {
                Text(
                    "Query changes are waiting. Load to apply them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (error != null) {
            item(key = "request_error", contentType = "notice") {
                Notice(
                    title = "Log request failed",
                    detail = error + if (snapshot == null) {
                        " No previous snapshot is available."
                    } else {
                        " The previous loaded lines are preserved below."
                    },
                    color = Red,
                )
            }
        }
        if (loading) {
            item(key = "loading", contentType = "loading") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (snapshot == null) "Loading logs…" else "Loading a new snapshot · previous lines remain visible",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dim,
                    )
                }
            }
        }

        val loaded = snapshot
        if (loaded != null) {
            item(key = "result_heading_${loaded.request.revision}", contentType = "heading") {
                SectionHeading("Loaded snapshot", "${loaded.lines.size} lines returned")
                Text(
                    loaded.request.source + " · up to ${loaded.request.lines} lines" +
                        if (loaded.request.filter.isBlank()) "" else " · filter: ${loaded.request.filter}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MachineFont,
                    color = Dim,
                )
            }
            if (loaded.lines.isEmpty()) {
                item(key = "empty_${loaded.request.revision}", contentType = "empty") {
                    Text(
                        "No lines returned for this query. Adjust the source, line limit, or filter and load again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dim,
                    )
                }
            } else {
                itemsIndexed(
                    items = loaded.lines,
                    key = { index, _ -> "log:${loaded.request.revision}:$index" },
                    contentType = { _, _ -> "log_line" },
                ) { index, line ->
                    LogLine(index = index, line = line)
                }
            }
        } else if (!loading && error == null) {
            item(key = "not_loaded", contentType = "empty") {
                Text(
                    "No log snapshot loaded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            }
        }
    }
}

@Composable
private fun LogLine(index: Int, line: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            (index + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = MachineFont,
            color = Dim,
            modifier = Modifier.widthIn(min = 28.dp).padding(end = 12.dp),
        )
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = MachineFont,
            color = TextMain,
            softWrap = true,
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider(color = Line.copy(alpha = 0.45f))
}
