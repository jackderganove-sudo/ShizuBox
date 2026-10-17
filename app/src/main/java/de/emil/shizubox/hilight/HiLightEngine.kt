/*
 * Renderer for the HiLight LEDs, running in ShizuBox' shell service (uid 2000).
 * Structure and safety rules follow HiLight Studio's Engine/SafetyGuard/LightsBackend
 * (MIT License, Copyright (c) 2026 HiLight Studio contributors, see NOTICE).
 */
package de.emil.shizubox.hilight

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * One effect at a time on one light session:
 * - an effect always has an end: alerts ≤ 1 min, ambient ≤ 5 min (app default 30 s), previews ≤ 15 s,
 *   progress flashes ≤ 1 min – enforced here, whatever the app sends
 * - every frame goes through [SafetyGuard] (dimming after 10 s, ≤ 50 % lit per 10 minutes)
 * - only one renderer: before opening a session ShizuBox counts the sessions in `dumpsys lights`;
 *   if anyone else holds one (HiLight Studio, a call, Gemini, Android's contact colours), nothing
 *   is shown (fail closed)
 * - when an effect ends or is stopped: HiLight Studio's black clear, then the session is closed;
 *   if this process dies, LightsService closes the session with the token
 */
object HiLightEngine {
    private const val FRAME_MS = 33L
    private const val PRIORITY = 0
    private const val CLEANUP_PRIORITY = -1000

    private val kindLimit = mapOf("alert" to 60_000L, "ambient" to 300_000L, "preview" to 15_000L, "progress" to 60_000L)

    private class Effect(
        val id: String,
        val kind: String,
        val pattern: Pattern,
        val colors: IntArray,
        val speedMs: Long,
        val brightness: Double,
        @Volatile var progress: Double,
        val start: Long,
        val until: Long,
    )

    private val lock = Object()
    private var io: LightsIo? = null
    private var effect: Effect? = null
    private var thread: Thread? = null
    private val guard = SafetyGuard()
    private var lastFrame: IntArray? = null
    private var lastError = ""

    private fun io(): LightsIo = io ?: LightsIo().also { io = it }

    /** Sessions held by anyone else right now (`dumpsys lights`, "Session token=" lines). */
    fun foreignSessions(): Int {
        val own = if (io?.isOpen == true) 1 else 0
        val n = runCatching {
            val p = ProcessBuilder("dumpsys", "lights").redirectErrorStream(true).start()
            val c = p.inputStream.bufferedReader().useLines { l -> l.count { it.contains("Session token=") } }
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            c
        }.getOrDefault(-1)
        return if (n < 0) 99 else (n - own).coerceAtLeast(0)
    }

    fun handle(cmd: JSONObject): String = when (cmd.getString("op")) {
        "info" -> io().describe()
        "play" -> play(cmd)
        "progress" -> synchronized(lock) {
            val e = effect
            if (e != null && e.id == cmd.optString("id")) { e.progress = cmd.optDouble("progress", e.progress); "ok" } else "none"
        }
        "off" -> { stop(cmd.optString("id").ifEmpty { null }); "ok" }
        "reset" -> { reset(); status().toString() }
        "status" -> status().toString()
        else -> "unknown op"
    }

    private fun play(cmd: JSONObject): String {
        val kind = cmd.optString("kind", "alert")
        val limit = kindLimit[kind] ?: 60_000L
        val duration = cmd.optLong("durationMs", 10_000L).coerceIn(500L, limit)
        val colors = cmd.optJSONArray("colors")?.let { a -> IntArray(a.length()) { a.optLong(it).toInt() } } ?: intArrayOf(cmd.optLong("color", 0xFFFFFFFFL).toInt())
        // `dumpsys` takes ~0.1–0.3 s: outside the lock the render thread needs every frame.
        if (io?.isOpen != true && foreignSessions() > 0) return "busy"
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            effect = Effect(
                cmd.optString("id", "x"), kind, Pattern.of(cmd.optString("pattern", "solid")), colors,
                cmd.optLong("speedMs", 1500L), cmd.optDouble("brightness", 1.0).coerceIn(0.05, 1.0),
                cmd.optDouble("progress", 0.0), now, now + duration,
            )
            if (thread == null) {
                thread = Thread(::loop, "hilight").apply { isDaemon = true; start() }
            }
        }
        return "ok"
    }

    /** Stops the current effect (only the one with [id], if given); the loop clears and closes. */
    fun stop(id: String? = null) {
        synchronized(lock) {
            if (id == null || effect?.id == id) effect = null
        }
    }

    /**
     * The only render thread. It ends only while holding [lock] and seeing no effect, so an effect
     * that arrives during the final clear is never lost (play() starts a thread only when none runs).
     */
    private fun loop() {
        try {
            while (true) {
                val now = SystemClock.elapsedRealtime()
                val e = synchronized(lock) { effect?.takeIf { now < it.until } ?: run { effect = null; null } }
                if (e == null) {
                    // Tell the safety guard the LEDs are dark now – otherwise the pause until the
                    // next effect would count as lit time (seen: duty 162 %, LEDs wrongly resting).
                    guard.apply(intArrayOf(Patterns.BLACK), now)
                    runCatching { io?.clearAndClose() }
                    lastFrame = null
                    synchronized(lock) {
                        if (effect == null) {
                            thread = null
                            return
                        }
                    }
                    continue
                }
                val io = io()
                val n = io.ids.size
                var frame = Patterns.frame(e.pattern, e.colors, e.speedMs, now - e.start, n, e.progress)
                if (e.brightness < 0.999) frame = IntArray(n) { Patterns.scale(frame[it], e.brightness) }
                frame = guard.apply(frame, now)
                if (Patterns.visible(frame)) {
                    if (!io.isOpen) io.open(PRIORITY)
                    if (!frame.contentEquals(lastFrame)) {
                        io.set(frame)
                        lastFrame = frame
                    }
                } else if (io.isOpen) {
                    // A dark moment of an animation, or the safety rest: release the LEDs meanwhile.
                    io.clearAndClose()
                    lastFrame = null
                }
                Thread.sleep(FRAME_MS)
            }
        } catch (t: Throwable) {
            lastError = t.toString()
            guard.apply(intArrayOf(Patterns.BLACK), SystemClock.elapsedRealtime())
            runCatching { io?.clearAndClose() }
            lastFrame = null
            synchronized(lock) {
                thread = null
                effect = null
            }
        }
    }

    /**
     * "Reset LEDs": stop everything, then – only when nobody else holds a session – one cleanup pass
     * on a session below every normal priority (black, black, close), as HiLight Studio does at start.
     */
    fun reset() {
        stop()
        synchronized(lock) { thread }?.join(2000)
        runCatching { io?.clearAndClose() }
        if (foreignSessions() == 0) runCatching {
            val io = io()
            io.open(CLEANUP_PRIORITY)
            io.clearAndClose()
        }.onFailure { lastError = it.toString() }
    }

    fun status(): JSONObject {
        val foreign = foreignSessions()
        return synchronized(lock) { statusLocked(foreign) }
    }

    private fun statusLocked(foreign: Int): JSONObject {
        val e = effect
        return JSONObject()
            .put("active", e != null)
            .put("id", e?.id)
            .put("kind", e?.kind)
            .put("pattern", e?.pattern?.key)
            .put("remainingMs", e?.let { (it.until - SystemClock.elapsedRealtime()).coerceAtLeast(0) } ?: 0)
            .put("session", io?.isOpen == true)
            .put("foreignSessions", foreign)
            .put("duty", guard.dutyPercent)
            .put("resting", guard.resting)
            .put("leds", runCatching { JSONArray(io().ids.map { Integer.toHexString(io().get(it) ?: 0) }) }.getOrNull())
            .put("error", lastError)
    }
}
