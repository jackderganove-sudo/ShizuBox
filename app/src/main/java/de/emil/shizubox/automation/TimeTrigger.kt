package de.emil.shizubox.automation

import java.util.Calendar

/**
 * Which scheduled occurrence of a time rule is due. Before 0.11.1 every rule within 2 minutes of
 * "now" fired on each alarm, so a rule at 10:00 fired again with a second rule at 10:01 (Daniel's
 * review). Now the occurrence (date + time) is the unit: it fires once, remembered per rule.
 */
internal object TimeTrigger {
    /** An alarm may come a little late in doze. */
    const val TOLERANCE_MS = 2 * 60_000L

    /** The latest occurrence of [minute] on one of [days] (bit 0 = Monday) at or before [now]. */
    fun lastOccurrence(days: Int, minute: Int, now: Calendar): Long? {
        if (days and 0b1111111 == 0) return null
        for (d in 0..7) {
            val c = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -d)
                set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis > now.timeInMillis) continue
            val bit = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
            if (days shr bit and 1 == 1) return c.timeInMillis
        }
        return null
    }

    /** The occurrence to fire now, or null if none is due or it already ran ([lastFired]). */
    fun due(days: Int, minute: Int, now: Calendar, lastFired: Long): Long? {
        val occ = lastOccurrence(days, minute, now) ?: return null
        if (now.timeInMillis - occ > TOLERANCE_MS) return null
        return occ.takeIf { it != lastFired }
    }
}
