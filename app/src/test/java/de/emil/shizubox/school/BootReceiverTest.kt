package de.emil.shizubox.school

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test fun onlyBootBroadcasts() {
        assertTrue(isBootAction("android.intent.action.BOOT_COMPLETED"))
        assertTrue(isBootAction("android.intent.action.LOCKED_BOOT_COMPLETED"))
        assertFalse(isBootAction(null))
        assertFalse(isBootAction(""))
        assertFalse(isBootAction("android.intent.action.MY_PACKAGE_REPLACED"))
        assertFalse(isBootAction("de.evil.FAKE_BOOT"))
    }
}
