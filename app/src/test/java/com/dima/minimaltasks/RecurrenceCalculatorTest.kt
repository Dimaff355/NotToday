package com.dima.minimaltasks

import com.dima.minimaltasks.data.local.RecurrenceUnit
import com.dima.minimaltasks.domain.RecurrenceCalculator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceCalculatorTest {
    @Test
    fun monthlyEndOfMonthStaysAtEndOfMonth() {
        val zone = ZoneId.of("UTC")
        val january = millis("2024-01-31T09:00", zone)
        val february = RecurrenceCalculator.nextOccurrence(
            january, RecurrenceUnit.MONTH, 1, 0, zone.id, january,
        )!!
        assertEquals("2024-02-29T09:00", local(february, zone))

        val march = RecurrenceCalculator.nextOccurrence(
            february, RecurrenceUnit.MONTH, 1, 0, zone.id, february,
        )!!
        assertEquals("2024-03-31T09:00", local(march, zone))
    }

    @Test
    fun leapDayYearlyOccurrenceClampsToFebruaryTwentyEighth() {
        val zone = ZoneId.of("UTC")
        val leapDay = millis("2024-02-29T08:15", zone)
        val next = RecurrenceCalculator.nextOccurrence(
            leapDay, RecurrenceUnit.YEAR, 1, 0, zone.id, leapDay,
        )!!
        assertEquals("2025-02-28T08:15", local(next, zone))
    }

    @Test
    fun weeklySelectedWeekdaysReturnTheNextSelectedDay() {
        val zone = ZoneId.of("UTC")
        val monday = millis("2025-01-06T09:00", zone)
        val tuesdayAndThursday = (1 shl (DayOfWeek.TUESDAY.value - 1)) or
            (1 shl (DayOfWeek.THURSDAY.value - 1))
        val tuesday = RecurrenceCalculator.nextOccurrence(
            monday, RecurrenceUnit.WEEK, 1, tuesdayAndThursday, zone.id, monday,
        )!!
        assertEquals("2025-01-07T09:00", local(tuesday, zone))

        val thursday = RecurrenceCalculator.nextOccurrence(
            tuesday, RecurrenceUnit.WEEK, 1, tuesdayAndThursday, zone.id, tuesday,
        )!!
        assertEquals("2025-01-09T09:00", local(thursday, zone))
    }

    @Test
    fun overdueDailyOccurrenceSkipsPastInstances() {
        val zone = ZoneId.of("UTC")
        val due = millis("2025-01-01T10:00", zone)
        val now = millis("2025-01-10T10:00", zone)
        val next = RecurrenceCalculator.nextOccurrence(
            due, RecurrenceUnit.DAY, 2, 0, zone.id, now,
        )!!
        assertEquals("2025-01-11T10:00", local(next, zone))
    }

    @Test
    fun springDstGapIsResolvedInTheTaskTimezone() {
        val zone = ZoneId.of("America/New_York")
        val due = millis("2025-03-08T02:30", zone)
        val next = RecurrenceCalculator.nextOccurrence(
            due, RecurrenceUnit.DAY, 1, 0, zone.id, due,
        )!!
        assertEquals("2025-03-09T03:30", local(next, zone))
        assertTrue(Instant.ofEpochMilli(next).atZone(zone).offset.id == "-04:00")
    }

    @Test
    fun boundedSearchReturnsNullWhenNoCandidateFitsTheBudget() {
        val zone = ZoneId.of("UTC")
        val due = millis("2025-01-01T10:00", zone)
        val now = millis("2028-01-01T10:00", zone)
        assertNull(
            RecurrenceCalculator.nextOccurrence(
                due, RecurrenceUnit.YEAR, 1, 0, zone.id, now, maxIterations = 2,
            ),
        )
    }

    private fun millis(value: String, zone: ZoneId): Long =
        LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()

    private fun local(value: Long, zone: ZoneId): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(value), zone).toString()
}
