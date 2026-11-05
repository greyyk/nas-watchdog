package com.grey.naswatchdog.feature

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grey.naswatchdog.Stats
import com.grey.naswatchdog.canNotify
import com.grey.naswatchdog.notifyAlert
import com.grey.naswatchdog.syncAlertMonitoring
import com.grey.naswatchdog.ui.Amber
import com.grey.naswatchdog.ui.Dim
import com.grey.naswatchdog.ui.Green
import com.grey.naswatchdog.ui.Line
import com.grey.naswatchdog.ui.MachineFont
import com.grey.naswatchdog.ui.SectionHeading
import com.grey.naswatchdog.ui.TextMain
import com.grey.naswatchdog.ui.formatDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsOverviewScreen(
    s: Stats?,
    currentUrl: String,
    currentToken: String,
    prefs: android.content.SharedPreferences,
    onSave: (String, String, Long) -> Unit,
    onEditConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var token by remember(currentToken) { mutableStateOf(currentToken) }
    var showUrlError by remember { mutableStateOf(false) }
    var notifications by remember(prefs) { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var liveMonitoring by remember(prefs) { mutableStateOf(prefs.getBoolean("live_monitoring", true)) }
    var interval by remember(prefs) { mutableLongStateOf(prefs.getLong("refresh_ms", 10_000L)) }
    val ctx = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(canNotify(ctx)) }
    var lastBackgroundSuccess by remember(prefs) {
        mutableLongStateOf(prefs.getLong("last_background_success", 0L))
    }
    var backgroundError by remember(prefs) {
        mutableStateOf(prefs.getString("last_background_error", null))
    }
    val notificationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notificationsAllowed = canNotify(ctx)
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "notifications" -> notifications = prefs.getBoolean("notifications", true)
                "live_monitoring" -> liveMonitoring = prefs.getBoolean("live_monitoring", true)
                "last_background_success" -> {
                    lastBackgroundSuccess = prefs.getLong("last_background_success", 0L)
                }
                "last_background_error" -> {
                    backgroundError = prefs.getString("last_background_error", null)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun save() {
        if (url.isBlank()) {
            showUrlError = true
            return
        }
        showUrlError = false
        onSave(url.trim(), token.trim(), interval)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "connection_heading", contentType = "heading") {
            SectionHeading("Connection")
            Text(
                "Applied immediately after saving.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        }
        item(key = "connection_fields", contentType = "settings_fields") {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    showUrlError = false
                },
                label = { Text("Server address") },
                placeholder = { Text("http://nas.local:8090") },
                supportingText = if (showUrlError) {
                    { Text("Enter the watchdog server address.") }
                } else {
                    null
                },
                isError = showUrlError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("API token (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 56.dp),
            )
        }
        item(key = "connection_actions", contentType = "settings_actions") {
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Save changes")
            }
            OutlinedButton(
                onClick = onEditConnection,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
            ) {
                Text("Test a different connection")
            }
            SettingsSectionDivider()
        }

        item(key = "alerts_heading", contentType = "heading") {
            SectionHeading("Alerts & background monitoring")
            Text(
                "Background alert delivery is independent of the foreground dashboard refresh setting.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        }
        item(key = "alert_switches", contentType = "settings_switches") {
            SettingsSwitchRow(
                title = "Alert notifications",
                detail = "Notify me about new NAS issues",
                checked = notifications,
                onCheckedChange = { checked ->
                    notifications = checked
                    prefs.edit().putBoolean("notifications", checked).apply()
                    syncAlertMonitoring(ctx)
                },
            )
            SettingsSwitchRow(
                title = "Live background alerts",
                detail = "Reconnects automatically when the app is closed",
                checked = liveMonitoring,
                enabled = notifications,
                onCheckedChange = { checked ->
                    liveMonitoring = checked
                    prefs.edit().putBoolean("live_monitoring", checked).apply()
                    syncAlertMonitoring(ctx)
                },
            )
            Text(
                if (liveMonitoring && notifications) {
                    "Uses a quiet ongoing notification to keep the live alert stream open. Periodic WorkManager checks remain as a fallback."
                } else {
                    "Battery-saver checks run about every 15 minutes and may be delayed by Android."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item(key = "notification_diagnostic", contentType = "settings_diagnostics") {
            SettingsReadout(
                label = "Android notification access",
                value = if (notificationsAllowed) "Allowed" else "Off",
                valueColor = if (notificationsAllowed) Green else Amber,
            )
            if (!notificationsAllowed) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        notificationSettings.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Enable in system settings")
                }
            }
        }
        item(key = "background_diagnostics", contentType = "settings_diagnostics") {
            SettingsReadout(
                label = "Last successful alert check",
                value = if (lastBackgroundSuccess > 0L) {
                    formatDateTime(lastBackgroundSuccess)
                } else {
                    "No successful check recorded"
                },
                valueColor = if (lastBackgroundSuccess > 0L) Green else Dim,
            )
            if (backgroundError != null) {
                Text(
                    "Background status: $backgroundError",
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            OutlinedButton(
                onClick = {
                    notifyAlert(
                        ctx,
                        "Test notification",
                        "NAS Watchdog alerts are working, including when the app is closed.",
                    )
                },
                enabled = notifications && notificationsAllowed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Send test notification")
            }
            SettingsSectionDivider()
        }

        item(key = "refresh_heading", contentType = "heading") {
            SectionHeading("Foreground dashboard refresh")
            Text(
                "While open, refresh telemetry every",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        }
        item(key = "refresh_interval", contentType = "settings_control") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5_000L to "5s", 10_000L to "10s", 30_000L to "30s", 60_000L to "60s").forEach { (ms, label) ->
                    FilterChip(
                        selected = interval == ms,
                        onClick = { interval = ms },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Text(
                "Choose Save changes in Connection to apply. This does not change background alert monitoring.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                modifier = Modifier.padding(top = 8.dp),
            )
            SettingsSectionDivider()
        }

        item(key = "about_heading", contentType = "heading") {
            SectionHeading("About")
        }
        item(key = "about", contentType = "settings_about") {
            SettingsReadout(
                label = "App version",
                value = com.grey.naswatchdog.BuildConfig.VERSION_NAME,
            )
            if (s == null) {
                Text(
                    "Server details will appear after the next successful connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            } else {
                SettingsReadout(
                    label = "Watchdog version",
                    value = s.version.ifBlank { "Not reported" },
                )
                SettingsReadout(label = "Host", value = s.hostname.ifBlank { "Not reported" })
                SettingsReadout(label = "OS", value = s.os.ifBlank { "Not reported" })
                SettingsReadout(
                    label = "NAS actions",
                    value = if (s.actionsEnabled) "Enabled" else "Disabled",
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextMain)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Dim)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingsReadout(
    label: String,
    value: String,
    valueColor: Color = TextMain,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
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
}

@Composable
private fun SettingsSectionDivider() {
    HorizontalDivider(
        color = Line.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 12.dp),
    )
}
