package com.grey.naswatchdog

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

const val CHANNEL_ALERTS = "alerts"
private const val CHANNEL_MONITOR = "monitor"
private const val MONITOR_NOTIFICATION_ID = 1001
private const val PREFS = "naswatchdog"
private const val ALERT_WORK = "alert-poll"
private val alertDeliveryLock = Any()

fun ensureAlertChannel(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm?.createNotificationChannel(
        NotificationChannel(CHANNEL_ALERTS, "NAS alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Critical and warning alerts from your NAS"
        }
    )
    nm?.createNotificationChannel(
        NotificationChannel(CHANNEL_MONITOR, "Background monitoring", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows when live NAS alerts are active"
            setShowBadge(false)
        }
    )
}

private fun canPostToChannel(ctx: Context, channelId: String): Boolean {
    val runtimePermission = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (!runtimePermission || !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
    val channel = ctx.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channelId)
    return channel?.importance != NotificationManager.IMPORTANCE_NONE
}

fun canNotify(ctx: Context): Boolean = canPostToChannel(ctx, CHANNEL_ALERTS)

private fun openAppIntent(ctx: Context): PendingIntent {
    val intent = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun monitorNotification(ctx: Context, status: String): Notification =
    NotificationCompat.Builder(ctx, CHANNEL_MONITOR)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("NAS Watchdog")
        .setContentText(status)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(openAppIntent(ctx))
        .addAction(0, "Open NAS Watchdog", openAppIntent(ctx))
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

/** Returns false when the alert remains eligible for a later notification attempt. */
fun notifyAlert(
    ctx: Context,
    title: String,
    body: String,
    id: Int = (System.currentTimeMillis() % 100000).toInt(),
): Boolean {
    if (!canNotify(ctx)) return false
    ensureAlertChannel(ctx)
    val notification = NotificationCompat.Builder(ctx, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(ctx))
        .build()
    return try {
        NotificationManagerCompat.from(ctx).notify(id, notification)
        true
    } catch (e: SecurityException) {
        // Permission can be revoked between the check and posting the notification.
        android.util.Log.w("NASWatchdog", "Notification permission was revoked", e)
        false
    }
}

internal data class AlertDeliveryPlan(val alerts: List<Alert>, val nextSeen: Set<String>)

internal fun alertDeliveryPlan(active: List<Alert>, seen: Set<String>, canDeliver: Boolean = true): AlertDeliveryPlan {
    val currentIds = active.mapTo(HashSet()) { it.id }.apply { remove("") }
    val retainedSeen = currentIds.filterTo(HashSet()) { it in seen }
    if (!canDeliver) return AlertDeliveryPlan(emptyList(), retainedSeen)
    val toDeliver = active.filter { it.id.isNotBlank() && it.id !in seen }.take(5)
    val deliveredIds = toDeliver.mapTo(HashSet()) { it.id }
    return AlertDeliveryPlan(toDeliver, retainedSeen + deliveredIds)
}

private fun deliverActiveAlerts(ctx: Context, alerts: List<Alert>) {
    synchronized(alertDeliveryLock) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen_alerts", emptySet())?.toSet() ?: emptySet()
        val plan = alertDeliveryPlan(alerts, seen, canNotify(ctx))
        val failed = plan.alerts.filterNot { alert ->
            notifyAlert(ctx, alert.title, alert.detail, alert.id.hashCode())
        }.mapTo(HashSet()) { it.id }
        val nextSeen = plan.nextSeen - failed
        // Keep denied/failed and overflow alerts queued for the next check. Do not
        // write the preference on every SSE event when no delivery state changed.
        if (seen != nextSeen) prefs.edit().putStringSet("seen_alerts", nextSeen).apply()
    }
}

private fun recordBackgroundSuccess(ctx: Context) {
    val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val previous = prefs.getLong("last_background_success", 0L)
    val error = prefs.getString("last_background_error", null)
    if (previous > 0L && now - previous < 60_000L && error == null) return
    prefs.edit().putLong("last_background_success", now).remove("last_background_error").apply()
}

private fun recordBackgroundError(ctx: Context, error: Throwable) {
    val message = connectionError(error).take(300)
    val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getString("last_background_error", null) == message) return
    prefs.edit().putString("last_background_error", message).apply()
}

class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications", false)) return Result.success()
        val base = prefs.getString("baseUrl", "").orEmpty()
        if (base.isBlank()) return Result.success()
        val token = prefs.getString("token", "").orEmpty()

        val stats = try {
            fetchStats(base, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordBackgroundError(applicationContext, e)
            return Result.retry()
        }

        deliverActiveAlerts(applicationContext, stats.alerts)
        recordBackgroundSuccess(applicationContext)
        return Result.success()
    }
}

class AlertMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var lastStatus = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications", false) ||
            !prefs.getBoolean("live_monitoring", false) ||
            prefs.getString("baseUrl", "").isNullOrBlank()
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureAlertChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                MONITOR_NOTIFICATION_ID,
                monitorNotification(this, "Connecting to your NAS…"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: RuntimeException) {
            android.util.Log.w("NASWatchdog", "Could not start foreground monitoring", e)
            recordBackgroundError(this, e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        updateStatus("Connecting to your NAS…")

        monitorJob?.cancel()
        monitorJob = serviceScope.launch { monitorAlerts() }
        return START_STICKY
    }

    private suspend fun monitorAlerts() {
        var failures = 0
        while (true) {
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notifications", false) ||
                !prefs.getBoolean("live_monitoring", false)
            ) {
                stopSelf()
                return
            }
            val base = prefs.getString("baseUrl", "").orEmpty()
            val token = prefs.getString("token", "").orEmpty()
            if (base.isBlank()) {
                stopSelf()
                return
            }

            try {
                streamAlerts(base, token) { alerts ->
                    deliverActiveAlerts(this, alerts)
                    recordBackgroundSuccess(this)
                    failures = 0
                    updateStatus(
                        when (alerts.size) {
                            0 -> "Live · all systems healthy"
                            1 -> "Live · 1 active alert"
                            else -> "Live · ${alerts.size} active alerts"
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                recordBackgroundError(this, e)
                updateStatus("Connection lost · reconnecting")
                delay(minOf(30_000L, 2_000L * failures))
            }
        }
    }

    private fun updateStatus(status: String) {
        if (status == lastStatus) return
        lastStatus = status
        if (canPostToChannel(this, CHANNEL_MONITOR)) {
            try {
                NotificationManagerCompat.from(this)
                    .notify(MONITOR_NOTIFICATION_ID, monitorNotification(this, status))
            } catch (_: SecurityException) {
                // The foreground service remains active if notification access is revoked.
            }
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        ensureAlertChannel(ctx)
        scheduleAlertWorker(ctx)
        syncAlertMonitoring(ctx)
    }
}

fun scheduleAlertWorker(ctx: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
        ALERT_WORK, ExistingPeriodicWorkPolicy.UPDATE, request
    )
}

fun syncAlertMonitoring(ctx: Context) {
    val app = ctx.applicationContext
    val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val shouldRun = prefs.getBoolean("notifications", false) &&
        prefs.getBoolean("live_monitoring", false) &&
        !prefs.getString("baseUrl", "").isNullOrBlank()
    if (shouldRun) {
        try {
            ContextCompat.startForegroundService(app, Intent(app, AlertMonitorService::class.java))
        } catch (e: RuntimeException) {
            recordBackgroundError(app, e)
        }
    } else {
        app.stopService(Intent(app, AlertMonitorService::class.java))
    }
}
