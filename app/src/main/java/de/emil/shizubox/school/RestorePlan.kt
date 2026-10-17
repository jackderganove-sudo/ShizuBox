package de.emil.shizubox.school

import org.json.JSONObject

/**
 * The restore after school mode as single steps (Daniel's review, 0.11.1): before, everything ran
 * as one shell script, failures were only logged and the snapshot – the only copy of the user's
 * settings – was deleted anyway. Now every step runs on its own with its own time limit, and only
 * what was really restored leaves the snapshot. Pure logic, unit-tested ([run] takes the executor).
 */
object RestorePlan {
    /** Marker for the suspended apps (not a snapshot key). */
    const val SUSPENDED = "#suspended"

    /** Hard limit per step; a hanging command is killed after it (code 124) and counts as failed. */
    const val STEP_LIMIT_MS = 15_000L

    /**
     * [keys]: snapshot entries this step restores; [command] null = nothing to run (e.g. Wi-Fi was off).
     * A step without keys (status bar release) is still repeated on every retry.
     */
    data class Step(val keys: List<String>, val command: String?, val limitMs: Long = STEP_LIMIT_MS)

    fun steps(snapshot: JSONObject, suspended: Set<String>, settings: List<Pair<String, String>>): List<Step> = buildList {
        fun has(k: String) = snapshot.has(k)
        fun v(k: String) = snapshot.optString(k, "null")
        // Releases the status bar lock; harmless to repeat.
        add(Step(emptyList(), "cmd statusbar send-disable-flag none"))
        if (suspended.isNotEmpty()) add(Step(listOf(SUSPENDED), unsuspend(suspended), limitMs = 60_000))
        // Airplane mode first, then the individual radios.
        if (has("global/airplane_mode_on")) add(Step(listOf("global/airplane_mode_on"), "cmd connectivity airplane-mode ${if (v("global/airplane_mode_on") == "1") "enable" else "disable"}"))
        if (has("global/wifi_on")) add(Step(listOf("global/wifi_on"), if (v("global/wifi_on") in setOf("1", "2")) "cmd wifi set-wifi-enabled enabled" else null))
        if (has("global/bluetooth_on")) add(Step(listOf("global/bluetooth_on"), if (v("global/bluetooth_on") in setOf("1", "2")) "cmd bluetooth_manager enable" else null))
        if (has("global/mobile_data")) add(Step(listOf("global/mobile_data"), if (v("global/mobile_data") == "1") "svc data enable" else null))
        // DND off before touching the ringer, it blocks ringer changes.
        if (has("global/zen_mode")) add(Step(listOf("global/zen_mode"), "cmd notification set_dnd ${zenName(v("global/zen_mode"))}"))
        if (has("global/mode_ringer")) add(Step(listOf("global/mode_ringer"), "cmd audio set-ringer-mode ${ringerName(v("global/mode_ringer"))}"))
        for ((ns, key) in settings) {
            val k = "$ns/$key"
            if (!has(k)) continue
            val value = v(k)
            add(Step(listOf(k), if (value == "null") "settings delete $ns $key" else "settings put $ns $key ${quote(value)}"))
        }
    }

    /**
     * All at once; if that fails, one by one: an app uninstalled during school mode made the joint
     * call fail with exit code 1 forever (tested on the Pixel), so a package that is gone counts as done.
     */
    private fun unsuspend(pkgs: Set<String>): String {
        val all = pkgs.joinToString(" ")
        return "pm unsuspend $all >/dev/null 2>&1 || { f=0; for p in $all; do " +
            "pm unsuspend \$p >/dev/null 2>&1 || ! pm path \$p >/dev/null 2>&1 || f=1; done; exit \$f; }"
    }

    /** [done]: nothing left, the snapshot may be deleted. */
    data class Outcome(val restored: List<Step>, val failed: List<Step>, val snapshot: JSONObject, val suspended: Set<String>) {
        val done get() = failed.isEmpty()
    }

    /**
     * Runs every step on its own; a failing or hanging step (the executor enforces the time limit)
     * does not stop the following ones. [exec] returns true on success (exit code 0); it may throw [Abort] when
     * running anything further is pointless (Shizuku gone) – the rest then counts as failed.
     */
    suspend fun run(snapshot: JSONObject, suspended: Set<String>, steps: List<Step>, exec: suspend (Step) -> Boolean): Outcome {
        val done = ArrayList<Step>()
        val failed = ArrayList<Step>()
        var aborted = false
        for (s in steps) {
            if (aborted) { failed += s; continue }
            val ok = s.command == null || try {
                exec(s)
            } catch (_: Abort) {
                aborted = true
                false
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (ok) done += s else failed += s
        }
        val left = JSONObject(snapshot.toString())
        val restoredKeys = done.flatMap { it.keys }.toSet()
        restoredKeys.forEach { left.remove(it) }
        return Outcome(done, failed, left, if (SUSPENDED in restoredKeys) emptySet() else suspended)
    }

    class Abort : Exception()

    private fun zenName(v: String) = when (v) {
        "1" -> "priority"
        "2" -> "none"
        "3" -> "alarms"
        else -> "off"
    }

    private fun ringerName(v: String) = when (v) {
        "0" -> "SILENT"
        "1" -> "VIBRATE"
        else -> "NORMAL"
    }

    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}
