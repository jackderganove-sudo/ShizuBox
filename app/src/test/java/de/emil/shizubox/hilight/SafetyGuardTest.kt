package de.emil.shizubox.hilight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The HiLight safety limits (ported from HiLight Studio) without waiting 10 minutes on the phone. */
class SafetyGuardTest {
    private val white = intArrayOf(0xFFFFFFFF.toInt())
    private val black = intArrayOf(Patterns.BLACK)
    private fun red(c: Int) = (c shr 16) and 0xFF

    /** Lit frames every 33 ms from [from] for [ms]; returns the last output. */
    private fun lit(g: SafetyGuard, from: Long, ms: Long): IntArray {
        var out = white
        var t = from
        while (t <= from + ms) { out = g.apply(white, t); t += 33 }
        return out
    }

    @Test fun fullBrightnessForTenSecondsThenDimmedTo55Percent() {
        val g = SafetyGuard()
        assertEquals(255, red(lit(g, 0, 9_000)[0]))
        val after = red(lit(g, 9_033, 12_000)[0]) // 21 s continuous
        assertEquals((255 * 0.55).toInt(), after)
    }

    @Test fun atMostHalfOfTenMinutesLit() {
        val g = SafetyGuard()
        val out = lit(g, 0, 301_000) // just over 5 minutes
        assertTrue(g.resting)
        assertFalse(Patterns.visible(out))
    }

    @Test fun darkPauseBetweenEffectsDoesNotCountAsLit() {
        val g = SafetyGuard()
        lit(g, 0, 30_000)
        g.apply(black, 30_033) // effect ends: the engine reports dark
        lit(g, 200_000, 3_000) // next effect much later
        assertFalse(g.resting)
        assertTrue(g.dutyPercent < 15)
    }

    @Test fun progressBarLightsThreeOfEightAt37Percent() {
        val f = Patterns.frame(Pattern.Progress, intArrayOf(0xFF00FF00.toInt()), 1000, 0, 8, 0.37)
        assertEquals(3, f.count { Patterns.visible(intArrayOf(it)) })
    }
}
