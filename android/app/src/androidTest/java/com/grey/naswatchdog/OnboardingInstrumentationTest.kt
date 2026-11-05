package com.grey.naswatchdog

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val target get() = instrumentation.targetContext
    private val prefs get() = target.getSharedPreferences("naswatchdog", Context.MODE_PRIVATE)

    @Before
    fun launchCleanApp() {
        prefs.edit().clear().commit()
        syncAlertMonitoring(target)
        device.pressHome()
        device.pressBack()
        target.startActivity(target.packageManager.getLaunchIntentForPackage(target.packageName)!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        device.wait(Until.hasObject(By.pkg(target.packageName)), 10_000)
    }

    @Test
    fun setupOffersDemoWithoutAutomaticNotificationPrompt() {
        assertTrue(device.wait(Until.hasObject(By.text("Connect")), 10_000))
        assertTrue(device.wait(Until.hasObject(By.text("Explore synthetic demo")), 5_000))
        assertTrue("initial preferences must not silently enable alerts", prefs.getBoolean("background_defaults_migrated", false))
        assertFalse(prefs.getBoolean("notifications", true))
        assertFalse(prefs.getBoolean("live_monitoring", true))

        device.findObject(By.text("Explore synthetic demo")).click()
        assertTrue(device.wait(Until.hasObject(By.text("System")), 15_000))
        assertTrue(device.hasObject(By.text("Storage")))
        assertTrue(device.hasObject(By.text("Containers")))
        assertTrue(device.hasObject(By.text("DEMO")) || device.hasObject(By.textContains("Demo NAS")))
    }
}
