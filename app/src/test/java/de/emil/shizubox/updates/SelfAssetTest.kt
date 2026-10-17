package de.emil.shizubox.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class SelfAssetTest {
    // In the order GitHub returns them (by name, case-insensitive).
    private val names = listOf("ShizuBox-0.12.0-full.apk", "ShizuBox-0.12.0-lite.apk", "ShizuBox-0.12.0.apk")

    @Test fun eachVariantGetsItsOwnApk() {
        assertEquals(0, selfAssetIndex(names, "full"))
        assertEquals(1, selfAssetIndex(names, "lite"))
    }

    /** Updaters up to 0.11.2 take the last APK: that must be the full one, never lite. */
    @Test fun oldUpdaters_getFull() {
        assertEquals("ShizuBox-0.12.0.apk", names.sortedWith(String.CASE_INSENSITIVE_ORDER).last())
    }

    @Test fun olderReleaseWithoutVariants() {
        assertEquals(-1, selfAssetIndex(listOf("ShizuBox-0.11.2.apk"), "lite"))
    }
}
