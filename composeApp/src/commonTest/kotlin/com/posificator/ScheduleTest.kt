package com.posificator

import com.posificator.model.Dates
import com.posificator.model.LocalNow
import com.posificator.model.Place
import com.posificator.model.isActiveAt
import com.posificator.model.isExpiredAt
import com.posificator.model.nextActivation
import com.posificator.model.arrivalMessage
import com.posificator.model.arrivalTitle
import com.posificator.model.normalizedLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleTest {
    private val base = Place("a", "Home", 0.0, 0.0)
    private fun at(date: Int, h: Int, m: Int = 0) = LocalNow(date, h * 60 + m)

    @Test
    fun noScheduleIsAlwaysActive() {
        assertTrue(base.isActiveAt(at(20261011, 3)))
    }

    @Test
    fun dateRangeIsInclusive() {
        val p = base.copy(startDate = 20261010, endDate = 20261012)
        assertFalse(p.isActiveAt(at(20261009, 12)))
        assertTrue(p.isActiveAt(at(20261010, 0)))
        assertTrue(p.isActiveAt(at(20261012, 23, 59)))
        assertFalse(p.isActiveAt(at(20261013, 0)))
        assertTrue(p.isExpiredAt(at(20261013, 0)))
        assertFalse(p.isExpiredAt(at(20261012, 0)))
    }

    @Test
    fun dailyHours() {
        val day = base.copy(startMinute = 8 * 60, endMinute = 18 * 60)
        assertFalse(day.isActiveAt(at(20261011, 7, 59)))
        assertTrue(day.isActiveAt(at(20261011, 8)))
        assertTrue(day.isActiveAt(at(20261011, 18)))
        assertFalse(day.isActiveAt(at(20261011, 18, 1)))

        val night = base.copy(startMinute = 22 * 60, endMinute = 6 * 60)
        assertTrue(night.isActiveAt(at(20261011, 23)))
        assertTrue(night.isActiveAt(at(20261011, 5)))
        assertFalse(night.isActiveAt(at(20261011, 12)))
    }

    @Test
    fun dateConversionsRoundTrip() {
        assertEquals(19_000L * 86_400_000L, Dates.toUtcMillis(Dates.fromEpochDays(19_000)))
        assertEquals(20240229, Dates.fromUtcMillis(Dates.toUtcMillis(20240229)))
        assertEquals(19700101, Dates.fromEpochDays(0))
        assertEquals(20261011, Dates.fromUtcMillis(1_791_729_000_000L))
        assertEquals("2026-01-05", Dates.format(20260105))
        assertEquals("07:05", Dates.formatMinute(425))
    }

    @Test
    fun weekdays() {
        // 2026-10-12 is a Monday.
        assertEquals(1, Dates.dayOfWeek(20261012))
        assertEquals(7, Dates.dayOfWeek(20261011))
        val weekdays = base.copy(weekdays = listOf(1, 2, 3, 4, 5))
        assertTrue(weekdays.isActiveAt(at(20261012, 9)))
        assertFalse(weekdays.isActiveAt(at(20261011, 9)))
        assertEquals(LocalNow(20261012, 0), weekdays.nextActivation(at(20261010, 9)))

        // Friday night 22:00–06:00 still counts at 02:00 on Saturday, but not on Friday at 02:00.
        val fridayNight = base.copy(weekdays = listOf(5), startMinute = 22 * 60, endMinute = 6 * 60)
        assertTrue(fridayNight.isActiveAt(at(20261017, 2)))
        assertFalse(fridayNight.isActiveAt(at(20261016, 2)))
        assertTrue(fridayNight.isActiveAt(at(20261016, 23)))
    }

    @Test
    fun customMessageAndLink() {
        assertEquals("You're now at Home.", base.arrivalMessage())
        val custom = base.copy(message = "  Buy bread  ", link = "example.com/list")
        assertEquals("Buy bread", custom.arrivalMessage())
        assertEquals("Home", custom.arrivalTitle())
        assertEquals("https://example.com/list", custom.normalizedLink())
        assertEquals("http://x.org", base.copy(link = "http://x.org").normalizedLink())
        assertEquals(null, base.copy(link = "  ").normalizedLink())
    }
}
