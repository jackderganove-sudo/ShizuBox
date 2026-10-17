/*
 * Pattern maths ported from HiLight Studio's Renderer.java and SafetyGuard.java
 * (https://github.com/DhananjayBhosale/hilight-studio).
 *
 * MIT License – Copyright (c) 2026 HiLight Studio contributors.
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions: The above copyright notice and this
 * permission notice shall be included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND. (Full text: NOTICE)
 *
 * Changes for ShizuBox: Kotlin, a subset of the patterns, plus "progress" (a bar for a known
 * fraction) and "scan" (running light for unknown progress).
 */
package de.emil.shizubox.hilight

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Patterns the LEDs can play; [shown] = offered in the UI for notifications. */
enum class Pattern(val key: String, val shown: Boolean = true) {
    Solid("solid"), Pulse("pulse"), Breathe("breathe"), Blink("blink"), Chase("chase"), Comet("comet"),
    Wave("wave"), Heartbeat("heartbeat"), Rainbow("rainbow"),
    Progress("progress", false), Scan("scan", false),
    ;
    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Solid
    }
}

object Patterns {
    const val BLACK = 0xFF000000.toInt()

    /**
     * One frame for [n] LEDs at animation time [t] (ms since the start).
     * [progress] 0..1 for [Pattern.Progress].
     */
    fun frame(p: Pattern, colors: IntArray, speedMs: Long, t: Long, n: Int, progress: Double = 0.0): IntArray {
        val out = IntArray(n) { BLACK }
        val pal = if (colors.isEmpty()) intArrayOf(0xFFFFFFFF.toInt()) else IntArray(colors.size) { colors[it] or BLACK }
        val speed = max(60L, speedMs)
        val phase = (t % speed) / speed.toDouble()
        fun c(i: Int) = pal[i % pal.size]
        when (p) {
            Pattern.Solid -> for (i in 0 until n) out[i] = c(i)
            Pattern.Breathe -> {
                val k = (1 - cos(phase * 2 * PI)) / 2
                for (i in 0 until n) out[i] = scale(c(i), 0.05 + 0.95 * k)
            }
            Pattern.Blink -> if (phase < 0.5) for (i in 0 until n) out[i] = c(i)
            Pattern.Pulse -> {
                // sharp attack, exponential decay – reads well as a notification
                val k = if (phase < 0.12) phase / 0.12 else exp(-(phase - 0.12) * 5)
                for (i in 0 until n) out[i] = scale(c(i), k)
            }
            Pattern.Chase -> {
                val head = ((t / max(1L, speed / n)) % n).toInt()
                out[head] = pal[0]
            }
            Pattern.Comet -> {
                val pos = phase * n
                for (i in 0 until n) {
                    var d = pos - i
                    if (d < 0) d += n
                    out[i] = scale(c(i), max(0.0, 1 - d / 3.0))
                }
            }
            Pattern.Wave -> for (i in 0 until n) {
                val k = (1 + kotlin.math.sin(2 * PI * (phase + i.toDouble() / n))) / 2
                out[i] = scale(c(i), 0.08 + 0.92 * k)
            }
            Pattern.Heartbeat -> {
                val k = when {
                    phase < 0.06 -> phase / 0.06 * 0.75
                    phase < 0.22 -> 0.75 * exp(-(phase - 0.06) * 16.0)
                    phase < 0.28 -> (phase - 0.22) / 0.06
                    phase < 0.60 -> exp(-(phase - 0.28) * 9.0)
                    else -> 0.0
                }
                for (i in 0 until n) out[i] = scale(c(i), k)
            }
            Pattern.Rainbow -> for (i in 0 until n) out[i] = hsv(((phase + i.toDouble() / n) * 360.0) % 360, 1f, 1f)
            Pattern.Progress -> {
                // A plain bar: 3 of 8 LEDs ≈ 37 %, at least one LED once anything has happened.
                val lit = (progress.coerceIn(0.0, 1.0) * n).roundToInt().let { if (progress > 0) it.coerceAtLeast(1) else it }
                for (i in 0 until lit) out[i] = c(i)
            }
            Pattern.Scan -> {
                // bounce: a soft dot running back and forth – progress of unknown length
                val pos = (if (phase < 0.5) phase * 2 else (1 - phase) * 2) * (n - 1)
                for (i in 0 until n) out[i] = scale(c(i), max(0.0, 1 - abs(pos - i) / 1.5))
            }
        }
        return out
    }

    fun visible(frame: IntArray) = frame.any { ((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF) > 12 }

    fun scale(color: Int, k: Double): Int {
        val f = k.coerceIn(0.0, 1.0)
        val r = (((color shr 16) and 0xFF) * f).toInt()
        val g = (((color shr 8) and 0xFF) * f).toInt()
        val b = ((color and 0xFF) * f).toInt()
        return BLACK or (r shl 16) or (g shl 8) or b
    }

    fun hsv(h: Double, s: Float, v: Float): Int {
        val c = (v * s).toDouble()
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60).toInt() % 6) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        return BLACK or (((r + m) * 255).toInt() shl 16) or (((g + m) * 255).toInt() shl 8) or ((b + m) * 255).toInt()
    }
}

/**
 * HiLight Studio's safety limiter: after 10 s of continuous light the brightness ramps down to 55 %
 * over another 10 s, and within every 10-minute window the LEDs may be lit at most half the time –
 * after that they stay dark until the window ends. Continuous use of the LEDs is untested hardware
 * territory, so these limits are not configurable.
 */
class SafetyGuard {
    private var windowStart = Long.MIN_VALUE
    private var lastAt = Long.MIN_VALUE
    private var litInWindow = 0L
    private var continuousLit = 0L
    private var lastVisible = false
    var resting = false
        private set

    fun apply(frame: IntArray, now: Long): IntArray {
        account(now)
        if (!Patterns.visible(frame)) { note(false); return frame }
        if (resting || litInWindow >= WINDOW_MS * MAX_DUTY) {
            resting = true
            note(false)
            return IntArray(frame.size) { Patterns.BLACK }
        }
        if (continuousLit <= TAPER_AFTER_MS) { note(true); return frame }
        val over = min(1.0, (continuousLit - TAPER_AFTER_MS) / TAPER_RAMP_MS.toDouble())
        val k = 1.0 - (1.0 - TAPER_FLOOR) * over
        val out = IntArray(frame.size) { Patterns.scale(frame[it], k) }
        note(Patterns.visible(out))
        return out
    }

    private fun account(nowIn: Long) {
        var now = nowIn
        if (lastAt != Long.MIN_VALUE && now < lastAt) now = lastAt
        if (windowStart == Long.MIN_VALUE) windowStart = now
        if (lastAt == Long.MIN_VALUE) { lastAt = now; return }
        val delta = now - lastAt
        if (lastVisible) continuousLit += delta
        if (now - windowStart >= WINDOW_MS) {
            val windows = (now - windowStart) / WINDOW_MS
            val newStart = windowStart + windows * WINDOW_MS
            // A stalled renderer that overran the last window must not get a fresh budget at once.
            val overrun = lastVisible && (windows > 1 || litInWindow + max(0, min(now, windowStart + WINDOW_MS) - lastAt) > WINDOW_MS * MAX_DUTY)
            litInWindow = if (lastVisible) now - max(lastAt, newStart) else 0
            windowStart = newStart
            resting = overrun
        } else if (lastVisible) {
            litInWindow += delta
        }
        lastAt = now
    }

    private fun note(visible: Boolean) {
        lastVisible = visible
        if (!visible) continuousLit = 0
    }

    val dutyPercent get() = (100.0 * litInWindow / (WINDOW_MS * MAX_DUTY)).toInt()

    companion object {
        const val WINDOW_MS = 10 * 60_000L
        const val MAX_DUTY = 0.5
        const val TAPER_AFTER_MS = 10_000L
        const val TAPER_RAMP_MS = 10_000L
        const val TAPER_FLOOR = 0.55
    }
}
