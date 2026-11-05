package com.grey.naswatchdog.ui

import com.grey.naswatchdog.Sample
import com.grey.naswatchdog.charts.ChartPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone

private val isoTimestamp = Regex(
    "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})$"
)

fun parseTimestampEpoch(value: String): Long? {
    val match = isoTimestamp.matchEntire(value) ?: return null
    val parts = match.groupValues
    val zoneText = parts[8]
    val zone = if (zoneText == "Z") {
        TimeZone.getTimeZone("UTC")
    } else {
        val direction = if (zoneText.startsWith('-')) -1 else 1
        val offset = direction * (zoneText.substringBefore(':').drop(1).toInt() * 60 +
            zoneText.substringAfter(':').toInt()) * 60_000
        SimpleTimeZone(offset, "ISO-offset")
    }
    val milliseconds = parts[7].padEnd(3, '0').take(3).toIntOrNull() ?: 0
    val calendar = GregorianCalendar(zone, Locale.US).apply {
        isLenient = false
        clear()
        set(
            parts[1].toInt(), parts[2].toInt() - 1, parts[3].toInt(),
            parts[4].toInt(), parts[5].toInt(), parts[6].toInt(),
        )
        set(Calendar.MILLISECOND, milliseconds)
    }
    return try {
        calendar.timeInMillis
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun chartPoints(samples: List<Sample>, value: (Sample) -> Double?): List<ChartPoint> = samples.mapNotNull {
    val timestamp = parseTimestampEpoch(it.ts) ?: return@mapNotNull null
    ChartPoint(timestamp, value(it))
}

fun formatClock(epochMillis: Long, withSeconds: Boolean = false): String =
    SimpleDateFormat(if (withSeconds) "HH:mm:ss" else "HH:mm", Locale.getDefault()).format(Date(epochMillis))

fun formatDateTime(epochMillis: Long): String =
    SimpleDateFormat("MMM d · HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))

fun relativeTimestamp(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
    if (epochMillis == null) return "Time unavailable"
    val seconds = ((now - epochMillis) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3600} hr ago"
        else -> formatDateTime(epochMillis)
    }
}
