package de.emil.shizubox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeTriggerTest {
    private val everyDay = 0b1111111

    /** 2026-10-19 is a Monday. */
    private fun at(h: Int, m: Int, s: Int = 0, day: Int = 19) = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin")).apply {
        clear(); set(2026, Calendar.OCTOBER, day, h, m, s)
    }

    /** Simulates the alarm loop: [alarms] go off, each rule fires at most once per occurrence. */
    private fun run(rules: Map<String, Int>, alarms: List<Calendar>): List<String> {
        val last = HashMap<String, Long>()
        val fired = ArrayList<String>()
        for (now in alarms) for ((id, minute) in rules) {
            val occ = TimeTrigger.due(everyDay, minute, now, last[id] ?: 0) ?: continue
            last[id] = occ
            fired += id
        }
        return fired
    }

    /** Daniel's case: rules at 10:00 and 10:01 – the 10:01 alarm must not fire the 10:00 rule again. */
    @Test fun twoRulesOneMinuteApart_eachFiresOnce() {
        val fired = run(mapOf("a" to 600, "b" to 601), listOf(at(10, 0, 1), at(10, 1, 1)))
        assertEquals(listOf("a", "b"), fired)
    }

    @Test fun lateAlarm_withinTolerance_stillFires() {
        assertNotNull(TimeTrigger.due(everyDay, 600, at(10, 1, 50), 0))
        assertNull(TimeTrigger.due(everyDay, 600, at(10, 3), 0))
    }

    @Test fun nextDay_firesAgain() {
        val fired = run(mapOf("a" to 600), listOf(at(10, 0), at(10, 1), at(10, 0, day = 20)))
        assertEquals(listOf("a", "a"), fired)
    }

    @Test fun lateAfterMidnight_usesYesterdaysDay() {
        // Rule 23:59 only on Monday; the alarm comes at 00:00:30 on Tuesday → still Monday's occurrence.
        val monday = 0b0000001
        assertNotNull(TimeTrigger.due(monday, 23 * 60 + 59, at(0, 0, 30, day = 20), 0))
        // Rule 00:00 only on Monday does not fire on Tuesday.
        assertNull(TimeTrigger.due(monday, 0, at(0, 0, 30, day = 20), 0))
    }

    @Test fun wrongDay_doesNotFire() {
        val tuesday = 0b0000010
        assertNull(TimeTrigger.due(tuesday, 600, at(10, 0), 0))
    }
}
