package com.lck07.lumen.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup, Home, Series and the Guide, driven with the remote's keys. Signs in to the fake
 * Xtream server (tools/testserver, reachable from the emulator at 10.0.2.2:8089) when needed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.lck07.lumen", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        val d = device
        if (d.wait(Until.hasObject(By.text("Server Address")), 5_000)) signIn(d)
        d.wait(Until.hasObject(By.text("Series")), 180_000)
        d.waitForIdle()

        // Home rows.
        repeat(4) { d.key("DPAD_DOWN") }
        repeat(6) { d.key("DPAD_RIGHT") }
        repeat(4) { d.key("DPAD_UP") }
        // Series tab.
        d.key("BACK"); d.key("DPAD_RIGHT"); d.key("DPAD_CENTER", 1_500)
        d.key("DPAD_DOWN"); repeat(6) { d.key("DPAD_RIGHT") }; repeat(2) { d.key("DPAD_DOWN") }
        // Guide.
        d.key("BACK"); repeat(3) { d.key("DPAD_RIGHT") }; d.key("DPAD_CENTER", 2_000)
        repeat(4) { d.key("DPAD_DOWN") }; repeat(4) { d.key("DPAD_RIGHT") }; repeat(3) { d.key("DPAD_UP") }
        d.key("BACK")
    }

    private fun signIn(d: UiDevice) {
        d.key("DPAD_CENTER"); d.executeShellCommand("input text http://10.0.2.2:8089"); d.key("ENTER")
        d.key("DPAD_CENTER"); d.executeShellCommand("input text demo"); d.key("ENTER")
        d.key("DPAD_CENTER"); d.executeShellCommand("input text demo"); d.key("ENTER")
        d.key("DPAD_CENTER")
    }

    private fun UiDevice.key(name: String, settle: Long = 400) {
        executeShellCommand("input keyevent $name")
        Thread.sleep(settle)
    }
}
