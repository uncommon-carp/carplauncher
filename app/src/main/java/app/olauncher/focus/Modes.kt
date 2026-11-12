package app.olauncher.focus

const val DEFAULT_MODE_ID = "default"
const val MINUTES_PER_DAY = 24 * 60

/**
 * A daily window on the given ISO weekdays (1 = Monday .. 7 = Sunday). If [endMinute] <= [startMinute]
 * the window runs past midnight into the next day; equal start and end means a full 24 hours.
 * A window belongs to the weekday it starts on.
 */
data class ModeSchedule(
    val days: Set<Int>,
    val startMinute: Int,
    val endMinute: Int,
)

data class FocusMode(
    val id: String,
    val name: String,
    val schedule: ModeSchedule? = null,
)

/** A manual switch, held until the scheduled mode next changes. */
data class ModeOverride(val modeId: String, val setAtLocalMinutes: Long)

data class ModeResolution(val modeId: String, val overrideActive: Boolean)

/** Local wall-clock minutes since the epoch: (epochMillis + zone offset) / 60_000. */
fun dayOfWeek(localMinutes: Long): Int = (Math.floorDiv(localMinutes, MINUTES_PER_DAY.toLong()) + 3).mod(7) + 1

fun minuteOfDay(localMinutes: Long): Int = localMinutes.mod(MINUTES_PER_DAY.toLong()).toInt()

private fun previousDay(day: Int): Int = if (day == 1) 7 else day - 1

/** Minutes since this window opened, or null if it isn't open at [localMinutes]. */
fun ModeSchedule.minutesSinceStart(localMinutes: Long): Int? {
    if (days.isEmpty()) return null
    val day = dayOfWeek(localMinutes)
    val minute = minuteOfDay(localMinutes)
    return if (startMinute < endMinute) {
        if (day in days && minute >= startMinute && minute < endMinute) minute - startMinute else null
    } else {
        when {
            day in days && minute >= startMinute -> minute - startMinute
            previousDay(day) in days && minute < endMinute -> minute + MINUTES_PER_DAY - startMinute
            else -> null
        }
    }
}

/**
 * The mode the schedule picks at [localMinutes]. If several windows are open, the one that opened
 * most recently wins (so a short "Focus" block inside a long "Work" block takes over); ties go to
 * list order. No open window means the default mode.
 */
fun scheduledModeAt(modes: List<FocusMode>, localMinutes: Long): String =
    modes.withIndex()
        .mapNotNull { (i, mode) -> mode.schedule?.minutesSinceStart(localMinutes)?.let { Triple(mode.id, it, i) } }
        .minWithOrNull(compareBy<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
        ?.first ?: DEFAULT_MODE_ID

/**
 * True if the scheduled mode changes at any point in (from, to]. The schedule repeats weekly, so
 * only the first 8 days need checking, and only at window edges, where a change can happen.
 */
fun scheduleChangesBetween(modes: List<FocusMode>, from: Long, to: Long): Boolean {
    if (to <= from) return false
    val edges = modes.mapNotNull { it.schedule }.flatMap { listOf(it.startMinute, it.endMinute) }.toSortedSet()
    if (edges.isEmpty()) return false
    val initial = scheduledModeAt(modes, from)
    val end = minOf(to, from + 8L * MINUTES_PER_DAY)
    var dayStart = Math.floorDiv(from, MINUTES_PER_DAY.toLong()) * MINUTES_PER_DAY
    while (dayStart <= end) {
        for (edge in edges) {
            val t = dayStart + edge
            if (t in (from + 1)..end && scheduledModeAt(modes, t) != initial) return true
        }
        dayStart += MINUTES_PER_DAY
    }
    return false
}

/**
 * Which mode should be active now. A manual override wins until the scheduled mode changes after
 * it was set; an override pointing at a deleted mode is dropped.
 */
fun resolveMode(modes: List<FocusMode>, nowLocalMinutes: Long, override: ModeOverride?): ModeResolution {
    if (override != null && modes.any { it.id == override.modeId } &&
        !scheduleChangesBetween(modes, override.setAtLocalMinutes, nowLocalMinutes)
    ) return ModeResolution(override.modeId, overrideActive = true)
    val scheduled = scheduledModeAt(modes, nowLocalMinutes)
    val id = if (modes.any { it.id == scheduled }) scheduled else DEFAULT_MODE_ID
    return ModeResolution(id, overrideActive = false)
}

/** Next mode for the home-screen toggle, wrapping around. */
fun nextModeId(modes: List<FocusMode>, currentId: String): String {
    if (modes.isEmpty()) return DEFAULT_MODE_ID
    val i = modes.indexOfFirst { it.id == currentId }
    return modes[(i + 1).mod(modes.size)].id
}
