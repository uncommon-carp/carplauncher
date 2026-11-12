package app.olauncher.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesTest {
    // 1970-01-05 was a Monday; day index 4 since the epoch
    private fun at(isoDay: Int, hour: Int, minute: Int = 0, weekOffset: Int = 0): Long =
        ((4L + (isoDay - 1) + 7L * weekOffset) * MINUTES_PER_DAY) + hour * 60 + minute

    private val weekdays = setOf(1, 2, 3, 4, 5)
    private val default = FocusMode(DEFAULT_MODE_ID, "Default")
    private val work = FocusMode("work", "Work", ModeSchedule(weekdays, 9 * 60, 17 * 60))
    private val evening = FocusMode("evening", "Evening", ModeSchedule((1..7).toSet(), 20 * 60, 1 * 60))
    private val weekend = FocusMode("weekend", "Weekend", ModeSchedule(setOf(6, 7), 0, 0))
    private val modes = listOf(default, work, evening, weekend)

    @Test
    fun `local minute helpers`() {
        assertEquals(4, dayOfWeek(0)) // 1970-01-01 was a Thursday
        assertEquals(1, dayOfWeek(at(1, 0)))
        assertEquals(7, dayOfWeek(at(7, 23, 59)))
        assertEquals(9 * 60 + 30, minuteOfDay(at(3, 9, 30)))
        assertEquals(3, dayOfWeek(-1)) // just before the epoch: Wednesday 23:59
    }

    @Test
    fun `same-day window is start-inclusive, end-exclusive and weekday filtered`() {
        assertEquals("work", scheduledModeAt(modes, at(1, 9)))
        assertEquals("work", scheduledModeAt(modes, at(5, 16, 59)))
        assertEquals(DEFAULT_MODE_ID, scheduledModeAt(modes, at(5, 17)))
        assertEquals(DEFAULT_MODE_ID, scheduledModeAt(modes, at(1, 8, 59)))
    }

    @Test
    fun `overnight window belongs to the day it starts`() {
        assertEquals("evening", scheduledModeAt(modes, at(2, 23)))
        assertEquals("evening", scheduledModeAt(modes, at(3, 0, 30)))
        assertEquals(DEFAULT_MODE_ID, scheduledModeAt(modes, at(3, 1)))

        val fridayNight = FocusMode("fri", "Friday", ModeSchedule(setOf(5), 22 * 60, 2 * 60))
        assertEquals("fri", scheduledModeAt(listOf(fridayNight), at(6, 1))) // Saturday 01:00
        assertEquals(DEFAULT_MODE_ID, scheduledModeAt(listOf(fridayNight), at(5, 1))) // Friday 01:00
        assertEquals(DEFAULT_MODE_ID, scheduledModeAt(listOf(fridayNight), at(1, 1, weekOffset = 1)))
    }

    @Test
    fun `equal start and end is all day`() {
        assertEquals(0, weekend.schedule!!.minutesSinceStart(at(6, 0)))
        assertNull(weekend.schedule!!.minutesSinceStart(at(5, 23, 59)))
    }

    @Test
    fun `most recently opened window wins on overlap`() {
        // Saturday 21:00: weekend has been open since 00:00, evening since 20:00
        assertEquals("evening", scheduledModeAt(modes, at(6, 21)))
        assertEquals("weekend", scheduledModeAt(modes, at(6, 12)))
        // Sunday 00:30: Saturday's evening window (opened 20:00 Sat) vs Sunday's weekend window (opened 00:00)
        assertEquals("weekend", scheduledModeAt(modes, at(7, 0, 30)))
    }

    @Test
    fun `empty weekday set never matches`() {
        val never = FocusMode("never", "Never", ModeSchedule(emptySet(), 0, 0))
        assertEquals(DEFAULT_MODE_ID, scheduledModeAt(listOf(never), at(1, 12)))
    }

    @Test
    fun `manual override holds until the schedule next changes`() {
        val override = ModeOverride("evening", setAtLocalMinutes = at(1, 10)) // during Work
        assertEquals(ModeResolution("evening", true), resolveMode(modes, at(1, 16, 59), override))
        // Work ends at 17:00, so the schedule takes over again
        assertEquals(ModeResolution(DEFAULT_MODE_ID, false), resolveMode(modes, at(1, 17), override))
    }

    @Test
    fun `override is dropped after a change even if the schedule returns to the same mode`() {
        val override = ModeOverride("weekend", setAtLocalMinutes = at(1, 10))
        // Tuesday 10:00 is Work again, but Monday 17:00 was a change in between
        assertEquals(ModeResolution("work", false), resolveMode(modes, at(2, 10), override))
    }

    @Test
    fun `override persists indefinitely without schedules`() {
        val plain = listOf(default, FocusMode("deep", "Deep work"))
        val override = ModeOverride("deep", setAtLocalMinutes = at(1, 10))
        assertEquals(ModeResolution("deep", true), resolveMode(plain, at(3, 10, weekOffset = 5), override))
        assertFalse(scheduleChangesBetween(plain, at(1, 10), at(1, 10, weekOffset = 3)))
    }

    @Test
    fun `override for a deleted mode falls back to the schedule`() {
        val override = ModeOverride("gone", setAtLocalMinutes = at(1, 10))
        assertEquals(ModeResolution("work", false), resolveMode(modes, at(1, 11), override))
    }

    @Test
    fun `schedule change detection only looks at window edges in range`() {
        assertTrue(scheduleChangesBetween(modes, at(1, 8), at(1, 9)))
        assertFalse(scheduleChangesBetween(modes, at(1, 9), at(1, 16, 59)))
        assertFalse(scheduleChangesBetween(modes, at(1, 10), at(1, 10)))
    }

    @Test
    fun `next mode wraps around`() {
        assertEquals("work", nextModeId(modes, DEFAULT_MODE_ID))
        assertEquals(DEFAULT_MODE_ID, nextModeId(modes, "weekend"))
        assertEquals(DEFAULT_MODE_ID, nextModeId(modes, "unknown"))
    }
}
