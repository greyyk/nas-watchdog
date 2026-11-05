package com.grey.naswatchdog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class MonitoringInstrumentationTest {
    @get:Rule
    val permission = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var activity: MainActivity
    private val target get() = instrumentation.targetContext
    private val prefs get() = target.getSharedPreferences("naswatchdog", Context.MODE_PRIVATE)

    @Before
    fun launchSyntheticDashboard() {
        // Use only inert, in-process demo identity. The application's actual server
        // sync function is still exercised against the API 36 foreground service.
        prefs.edit().clear()
            .putBoolean("background_defaults_migrated", true)
            .putBoolean("notifications", false)
            .putBoolean("live_monitoring", false)
            .putString("baseUrl", DEMO_SERVER_URL)
            .putString("token", "synthetic-demo")
            .commit()
        syncAlertMonitoring(target)
        device.pressHome()
        device.pressBack()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        target.startActivity(target.packageManager.getLaunchIntentForPackage(target.packageName)!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        activity = monitor.waitForActivityWithTimeout(10_000) as MainActivity
        instrumentation.removeMonitor(monitor)
        assertTrue(device.wait(Until.hasObject(By.text("System")), 15_000))
    }

    @After
    fun stopMonitoring() {
        prefs.edit().putBoolean("notifications", false).putBoolean("live_monitoring", false).commit()
        syncAlertMonitoring(target)
        assertFalse(serviceDump().contains("isForeground=true"))
    }

    @Test
    fun explicitMonitoringOptInStartsServiceAndOptOutStopsIt() {
        assertFalse(serviceDump().contains("isForeground=true"))
        prefs.edit().putBoolean("notifications", true).putBoolean("live_monitoring", true).commit()
        syncAlertMonitoring(target)
        awaitMonitoringState(true)
        assertTrue(shell("dumpsys notification --noredact").contains("Background monitoring"))
        assertTrue(shell("dumpsys notification --noredact").contains("NAS Watchdog"))

        prefs.edit().putBoolean("live_monitoring", false).commit()
        syncAlertMonitoring(target)
        awaitMonitoringState(false)
    }

    @Test
    fun packageReplacementReceiverDoesNotStartServiceWhenUserOptedOut() {
        // Protected system broadcasts cannot be spoofed by an application UID.
        // Invoke the actual callback using the ordinary package replacement action.
        StartupReceiver().onReceive(target, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        instrumentation.waitForIdleSync()
        assertFalse(prefs.getBoolean("live_monitoring", true))
        assertFalse(serviceDump().contains("isForeground=true"))
    }

    @Test
    fun pausedSnapshotCannotReappearAfterDisconnect() {
        val model = ViewModelProvider(activity)[TelemetryViewModel::class.java]
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (model.state.value.snapshot == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
        }
        assertTrue(model.state.value.snapshot?.cached == false)
        model.setForegroundActive(false)
        runBlocking { model.disconnectAndClearLocalData() }
        assertFalse(File(target.noBackupFilesDir, "telemetry-cache-v1").exists())

        // Reconnecting at the same identity must not resurrect the cleared data.
        model.configure(DEMO_SERVER_URL, "synthetic-demo", 10_000)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(250)
        assertNull(model.state.value.snapshot)
    }

    private fun awaitMonitoringState(expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val dump = serviceDump()
            if ((expected && dump.contains("isForeground=true")) ||
                (!expected && !dump.contains("isForeground=true"))) return
            SystemClock.sleep(100)
        }
        throw AssertionError(
            "Expected foreground=$expected; enabled=${prefs.getBoolean("live_monitoring", false)}; " +
                "lastError=${prefs.getString("last_background_error", null)}. " + serviceDump(),
        )
    }

    private fun serviceDump(): String = shell("dumpsys activity services com.grey.naswatchdog.AlertMonitorService")

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
}
