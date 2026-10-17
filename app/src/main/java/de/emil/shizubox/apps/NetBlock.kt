package de.emil.shizubox.apps

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Internet block per app without a VPN, through the firewall chain OEM_DENY_3
 * (tested on Android 17, Pixel):
 * - `cmd connectivity set-chain3-enabled true` → "chain:enabled"
 * - `cmd connectivity set-package-networking-enabled false <pkg>` → the app has no network at all
 *   (no DNS, no active network), Wi-Fi and mobile data; `true` lifts it again.
 * The rules live in memory only: after a reboot they are applied again once Shizuku runs.
 */
object NetBlock {
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("net_block", Context.MODE_PRIVATE)
        load()
        // Every time Shizuku becomes ready (boot, Shizuku restart): make sure the rules are in place.
        scope.launch {
            ShizukuManager.status.filter { it == ShizukuStatus.Ready }.collect { runCatching { restore() } }
        }
    }

    fun load() {
        _blocked.value = prefs.getStringSet("blocked", emptySet()).orEmpty()
    }

    private fun save(set: Set<String>) {
        _blocked.value = set
        prefs.edit().putStringSet("blocked", set).apply()
    }

    /**
     * Apps that may not be blocked: system UIDs share one ID (blocking one would cut off the whole
     * system) and ShizuBox itself.
     */
    fun canBlock(pkg: String): Boolean {
        if (pkg == appContext.packageName) return false
        val uid = runCatching { appContext.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull() ?: return false
        return uid % 100_000 >= Process.FIRST_APPLICATION_UID
    }

    fun isBlocked(pkg: String) = pkg in _blocked.value

    /** Blocks or allows [pkgs]; returns the packages that were actually changed. */
    suspend fun set(pkgs: Collection<String>, block: Boolean): List<String> = lock.withLock {
        val targets = if (block) pkgs.filter(::canBlock) else pkgs.toList()
        if (targets.isEmpty()) return emptyList()
        val next = if (block) _blocked.value + targets else _blocked.value - targets.toSet()
        val cmds = buildList {
            if (next.isNotEmpty()) add("cmd connectivity set-chain3-enabled true")
            targets.forEach { add("cmd connectivity set-package-networking-enabled ${!block} $it") }
            // Nothing blocked any more: switch the chain off again, like before.
            if (next.isEmpty()) add("cmd connectivity set-chain3-enabled false")
        }
        val r = ShizukuManager.exec(cmds.joinToString("; "), 10_000, limitMs = 20_000L + cmds.size * 500L)
        // "a; b; c" only reports the exit code of c (Daniel's review, 0.11.1): read back what the
        // system really has (Android 17: "com.android.chrome:allow" / ":deny", "chain:enabled")
        // and remember exactly that.
        val back = ShizukuManager.exec(
            "cmd connectivity get-chain3-enabled; for p in ${targets.joinToString(" ")}; do cmd connectivity get-package-networking-enabled \$p; done",
            10_000, limitMs = 20_000L + targets.size * 500L,
        ).out
        val state = parseNetState(back)
        val chainOn = state[CHAIN] == true
        val actual = _blocked.value - targets.toSet() + targets.filter { state[it] == true && chainOn }
        save(actual)
        val wrong = targets.filter { (it in actual) != block }
        if (wrong.isNotEmpty()) error((r.err.ifBlank { r.out }.trim() + "\n" + wrong.joinToString()).trim())
        targets
    }

    suspend fun toggle(pkg: String) = set(listOf(pkg), !isBlocked(pkg))

    /** Re-applies all blocks if the chain was reset (reboot) – cheap check first. */
    suspend fun restore(force: Boolean = false) = lock.withLock {
        val set = _blocked.value
        if (set.isEmpty() || !ShizukuManager.isReady) return@withLock
        val chain = ShizukuManager.exec("cmd connectivity get-chain3-enabled").out
        val first = ShizukuManager.exec("cmd connectivity get-package-networking-enabled ${set.first()}").out
        if (!force && chain.contains("enabled") && !chain.contains("disabled") && first.contains("deny")) return@withLock
        val cmds = listOf("cmd connectivity set-chain3-enabled true") +
            set.map { "cmd connectivity set-package-networking-enabled false $it" }
        ShizukuManager.exec(cmds.joinToString("; "), 15_000, limitMs = 20_000L + cmds.size * 500L)
    }
}

internal const val CHAIN = "chain"

/**
 * Output of `get-chain3-enabled` and `get-package-networking-enabled` → name to "blocked"
 * ([CHAIN] → chain enabled). Lines that are not a state (errors) are left out = unknown.
 */
internal fun parseNetState(out: String): Map<String, Boolean> = out.lines().mapNotNull { line ->
    val name = line.substringBeforeLast(':', "").trim()
    when (line.substringAfterLast(':').trim()) {
        "deny" -> name to true
        "allow" -> name to false
        "enabled" -> name to true
        "disabled" -> name to false
        else -> null
    }?.takeIf { it.first.isNotEmpty() && ' ' !in it.first }
}.toMap()
