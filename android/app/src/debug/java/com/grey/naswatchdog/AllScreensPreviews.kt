package com.grey.naswatchdog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.grey.naswatchdog.charts.HistoryRange
import com.grey.naswatchdog.feature.ActionsOverviewScreen
import com.grey.naswatchdog.feature.AlertsOverviewScreen
import com.grey.naswatchdog.feature.ContainersOverviewScreen
import com.grey.naswatchdog.feature.MoreOverviewScreen
import com.grey.naswatchdog.feature.NetworkOverviewScreen
import com.grey.naswatchdog.feature.StorageOverviewScreen
import com.grey.naswatchdog.feature.SystemOverviewScreen
import com.grey.naswatchdog.feature.ThermalDetailScreen
// Preview frames mirror the production shell's dark background.

@Preview(name = "System instrument", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun SystemScreenPreview() {
    PreviewFrame { SystemOverviewScreen(previewStats(), previewTelemetry(), {}, Modifier.fillMaxSize()) }
}

@Preview(name = "Storage instrument", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun StorageScreenPreview() {
    PreviewFrame { StorageOverviewScreen(previewStats(), previewTelemetry(), { _, _, _ -> }, Modifier.fillMaxSize()) }
}

@Preview(name = "Containers instrument", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun ContainersScreenPreview() {
    PreviewFrame {
        ContainersOverviewScreen(previewStats(), "http://preview.invalid", "", { _, _, _ -> }, Modifier.fillMaxSize())
    }
}

@Preview(name = "Active alert instrument", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun AlertScreenPreview() {
    PreviewFrame { AlertsOverviewScreen(previewStats("warning"), Modifier.fillMaxSize()) }
}

@Preview(name = "Guarded control surface", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun ActionScreenPreview() {
    PreviewFrame { ActionsOverviewScreen(previewStats(), { _, _, _ -> }, Modifier.fillMaxSize()) }
}

@Preview(name = "Network instrument", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun NetworkScreenPreview() {
    PreviewFrame { NetworkOverviewScreen(previewStats(), previewTelemetry(), Modifier.fillMaxSize()) }
}

@Preview(name = "Destination index", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun MoreScreenPreview() {
    PreviewFrame { MoreOverviewScreen({}, 2, Modifier.fillMaxSize()) }
}

@Preview(name = "Thermal instrument · normal", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun ThermalScreenPreview() {
    PreviewFrame { ThermalDetailScreen(previewStats(), previewTelemetry(), HistoryRange.ONE_HOUR, {}) }
}

@Preview(name = "Thermal instrument · warning", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun ThermalWarningScreenPreview() {
    PreviewFrame {
        ThermalDetailScreen(previewStats("warning"), previewTelemetry("warning"), HistoryRange.ONE_HOUR, {})
    }
}

@Preview(name = "Thermal instrument · critical", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun ThermalCriticalScreenPreview() {
    PreviewFrame {
        ThermalDetailScreen(previewStats("critical"), previewTelemetry("critical"), HistoryRange.ONE_HOUR, {})
    }
}

@Preview(name = "Thermal instrument · empty", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun ThermalEmptyScreenPreview() {
    PreviewFrame {
        ThermalDetailScreen(
            previewStats(sensors = false),
            previewTelemetry(hasHistory = false, sensors = false),
            HistoryRange.ONE_HOUR,
            {},
        )
    }
}

@Preview(name = "Thermal instrument · large text", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
private fun ThermalLargeTextScreenPreview() {
    PreviewFrame {
        ThermalDetailScreen(previewStats("warning"), previewTelemetry("warning"), HistoryRange.ONE_HOUR, {})
    }
}
