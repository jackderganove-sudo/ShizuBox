package de.emil.shizubox.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class NetBlockParseTest {
    /** Output as on the Pixel (Android 17), including an error line for a missing package. */
    @Test fun readsRealStateAndIgnoresErrors() {
        val out = """
            chain:enabled
            com.android.chrome:deny
            org.example.app:allow
            android.content.pm.PackageManager${'$'}NameNotFoundException: com.does.not.exist
        """.trimIndent()
        assertEquals(
            mapOf(CHAIN to true, "com.android.chrome" to true, "org.example.app" to false),
            parseNetState(out),
        )
    }

    @Test fun chainOff() {
        assertEquals(mapOf(CHAIN to false), parseNetState("chain:disabled"))
    }
}
