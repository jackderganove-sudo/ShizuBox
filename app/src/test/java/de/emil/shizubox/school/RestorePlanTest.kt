package de.emil.shizubox.school

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Restore after school mode with simulated failing and hanging commands (never on the phone). */
class RestorePlanTest {
    private val settings = listOf("secure" to "doze_enabled", "system" to "screen_off_timeout", "secure" to "enabled_accessibility_services")

    private fun snapshot() = JSONObject()
        .put("secure/doze_enabled", "1")
        .put("system/screen_off_timeout", "60000")
        .put("secure/enabled_accessibility_services", "null")
        .put("global/airplane_mode_on", "0")
        .put("global/wifi_on", "1")
        .put("global/bluetooth_on", "0")
        .put("global/mobile_data", "1")
        .put("global/zen_mode", "0")
        .put("global/mode_ringer", "2")

    private val apps = setOf("com.a", "com.b")

    private fun run(snap: JSONObject = snapshot(), suspended: Set<String> = apps, exec: suspend (RestorePlan.Step) -> Boolean) = runBlocking {
        RestorePlan.run(snap, suspended, RestorePlan.steps(snap, suspended, settings), exec)
    }

    @Test fun allSucceed_nothingLeft() {
        val out = run { true }
        assertTrue(out.done)
        assertEquals(0, out.snapshot.length())
        assertTrue(out.suspended.isEmpty())
    }

    @Test fun oneFails_onlyThatStays_followingStepsStillRun() {
        val ran = ArrayList<String>()
        val out = run { s -> ran += s.command!!; !s.command!!.startsWith("settings put secure doze_enabled") }
        assertFalse(out.done)
        assertEquals(1, out.failed.size)
        assertEquals(setOf("secure/doze_enabled"), out.snapshot.keyNames())
        assertEquals("1", out.snapshot.getString("secure/doze_enabled"))
        assertTrue(out.suspended.isEmpty())
        // The steps after the failing one ran too.
        assertTrue(ran.any { it.startsWith("settings put system screen_off_timeout") })
        assertTrue(ran.any { it.startsWith("settings delete secure enabled_accessibility_services") })
    }

    @Test fun hangingStep_doesNotBlockTheOthers() {
        // The real executor kills a command after its limit (exit 124 → false); simulated with a timeout.
        val out = run { s ->
            withTimeoutOrNull(50) {
                if (s.command!!.contains("airplane-mode")) delay(10_000)
                true
            } ?: false
        }
        assertEquals(listOf(listOf("global/airplane_mode_on")), out.failed.map { it.keys })
        assertEquals(setOf("global/airplane_mode_on"), out.snapshot.keyNames())
    }

    @Test fun exceptionInStep_countsAsFailed() {
        val out = run { s -> if (s.command!!.startsWith("cmd audio")) error("boom") else true }
        assertEquals(setOf("global/mode_ringer"), out.snapshot.keyNames())
    }

    @Test fun shizukuGone_restIsKeptForLater() {
        var n = 0
        val out = run { if (++n == 3) throw RestorePlan.Abort() else true }
        assertFalse(out.done)
        // Status bar and unsuspend went through, everything from the third step on stays.
        assertTrue(out.suspended.isEmpty())
        assertEquals(snapshot().length(), out.snapshot.length())
        assertEquals(2, out.restored.size)
        // Nothing was run after Shizuku was gone.
        assertEquals(3, n)
    }

    @Test fun unsuspendFails_appsStaySaved() {
        val out = run { s -> !s.command!!.startsWith("pm unsuspend") }
        assertEquals(apps, out.suspended)
        assertEquals(0, out.snapshot.length())
        assertFalse(out.done)
    }

    @Test fun statusBarFails_notDoneAlthoughSnapshotIsEmpty() {
        val out = run { s -> !s.command!!.contains("statusbar") }
        assertEquals(0, out.snapshot.length())
        assertFalse(out.done)
    }

    @Test fun retry_onlyRunsWhatIsLeft() {
        val first = run { s -> !s.command!!.startsWith("settings put system") }
        val ran = ArrayList<String>()
        val second = run(first.snapshot, first.suspended) { s -> ran += s.command!!; true }
        assertTrue(second.done)
        assertEquals(listOf("cmd statusbar send-disable-flag none", "settings put system screen_off_timeout '60000'"), ran)
    }

    @Test fun missingKeys_areNeverDeleted() {
        // Before, a key missing from the snapshot became "null" → `settings delete`.
        val steps = RestorePlan.steps(JSONObject().put("secure/doze_enabled", "1"), emptySet(), settings)
        val cmds = steps.mapNotNull { it.command }
        assertEquals(listOf("cmd statusbar send-disable-flag none", "settings put secure doze_enabled '1'"), cmds)
    }

    @Test fun radiosThatWereOff_needNoCommandButCountAsRestored() {
        val out = run { s -> !s.command!!.contains("bluetooth") }
        // bluetooth_on was 0: no command, nothing to fail.
        assertTrue(out.done)
    }

    @Test fun unsuspend_toleratesUninstalledApps() {
        val step = RestorePlan.steps(JSONObject(), apps, settings).single { it.keys == listOf(RestorePlan.SUSPENDED) }
        assertTrue(step.command!!.contains("! pm path \$p"))
        assertTrue(step.limitMs > RestorePlan.STEP_LIMIT_MS)
    }
}

private fun JSONObject.keyNames() = keys().asSequence().toSet()
