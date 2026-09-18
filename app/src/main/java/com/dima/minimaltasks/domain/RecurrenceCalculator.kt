package com.dima.minimaltasks.domain

import com.dima.minimaltasks.data.local.RecurrenceUnit
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Computes the next local-calendar occurrence while retaining the task's wall-clock time. */
object RecurrenceCalculator {
    const val DEFAULT_MAX_ITERATIONS = 10_000

    fun nextOccurrence(
        dueAtMillis: Long,
        unit: RecurrenceUnit,
        interval: Int,
        weekdayMask: Int,
        zoneId: String,
        nowMillis: Long,
        maxIterations: Int = DEFAULT_MAX_ITERATIONS,
    ): Long? {
        require(interval in 1..999) { "Recurrence interval must be between 1 and 999" }
        require(maxIterations > 0) { "maxIterations must be positive" }
        require(weekdayMask in 0..WEEKDAY_MASK) { "weekdayMask must contain only seven weekday bits" }

        val zone = ZoneId.of(zoneId)
        val due = Instant.ofEpochMilli(dueAtMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis)
        return when (unit) {
            RecurrenceUnit.DAY -> nextDay(due.toLocalDate(), due.toLocalTime(), interval, weekdayMask, zone, now, maxIterations)
            RecurrenceUnit.WEEK -> nextWeek(due.toLocalDate(), due.toLocalTime(), interval, weekdayMask, zone, now, maxIterations)
            RecurrenceUnit.MONTH -> nextMonth(due.toLocalDate(), due.toLocalTime(), interval, zone, now, maxIterations)
            RecurrenceUnit.YEAR -> nextYear(due.toLocalDate(), due.toLocalTime(), interval, zone, now, maxIterations)
        }
    }

    private fun nextDay(
        anchorDate: LocalDate,
        anchorTime: java.time.LocalTime,
        interval: Int,
        weekdayMask: Int,
        zone: ZoneId,
        now: Instant,
        maxIterations: Int,
    ): Long? {
        var date = anchorDate
        repeat(maxIterations) {
            date = date.plusDays(interval.toLong())
            if (weekdayMask == 0 || weekdayMask.includes(date.dayOfWeek)) {
                val candidate = date.atTime(anchorTime).atZone(zone)
                if (candidate.toInstant().isAfter(now)) return candidate.toInstant().toEpochMilli()
            }
        }
        return null
    }

    private fun nextWeek(
        anchorDate: LocalDate,
        anchorTime: java.time.LocalTime,
        interval: Int,
        weekdayMask: Int,
        zone: ZoneId,
        now: Instant,
        maxIterations: Int,
    ): Long? {
        if (weekdayMask == 0) {
            var date = anchorDate
            repeat(maxIterations) {
                date = date.plusWeeks(interval.toLong())
                val candidate = date.atTime(anchorTime).atZone(zone)
                if (candidate.toInstant().isAfter(now)) return candidate.toInstant().toEpochMilli()
            }
            return null
        }

        val anchorMonday = anchorDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var date = anchorDate
        repeat(maxIterations) {
            date = date.plusDays(1)
            val monday = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeksFromAnchor = Duration.between(anchorMonday.atStartOfDay(), monday.atStartOfDay()).toDays() / 7
            if (weeksFromAnchor >= 0 && weeksFromAnchor % interval == 0L && weekdayMask.includes(date.dayOfWeek)) {
                val candidate = date.atTime(anchorTime).atZone(zone)
                if (candidate.toInstant().isAfter(now)) return candidate.toInstant().toEpochMilli()
            }
        }
        return null
    }

    private fun nextMonth(
        anchorDate: LocalDate,
        anchorTime: java.time.LocalTime,
        interval: Int,
        zone: ZoneId,
        now: Instant,
        maxIterations: Int,
    ): Long? {
        val preserveEndOfMonth = anchorDate.dayOfMonth == anchorDate.lengthOfMonth()
        repeat(maxIterations) { index ->
            val target = YearMonth.from(anchorDate).plusMonths(interval.toLong() * (index + 1L))
            val day = if (preserveEndOfMonth) target.lengthOfMonth() else minOf(anchorDate.dayOfMonth, target.lengthOfMonth())
            val candidate = LocalDateTime.of(target.year, target.month, day, anchorTime.hour, anchorTime.minute, anchorTime.second, anchorTime.nano)
                .atZone(zone)
            if (candidate.toInstant().isAfter(now)) return candidate.toInstant().toEpochMilli()
        }
        return null
    }

    private fun nextYear(
        anchorDate: LocalDate,
        anchorTime: java.time.LocalTime,
        interval: Int,
        zone: ZoneId,
        now: Instant,
        maxIterations: Int,
    ): Long? {
        repeat(maxIterations) { index ->
            val year = anchorDate.year.toLong() + interval.toLong() * (index + 1L)
            if (year !in -999_999_999L..999_999_999L) return null
            val day = minOf(anchorDate.dayOfMonth, YearMonth.of(year.toInt(), anchorDate.month).lengthOfMonth())
            val candidate = LocalDateTime.of(year.toInt(), anchorDate.month, day, anchorTime.hour, anchorTime.minute, anchorTime.second, anchorTime.nano)
                .atZone(zone)
            if (candidate.toInstant().isAfter(now)) return candidate.toInstant().toEpochMilli()
        }
        return null
    }

    private fun Int.includes(day: DayOfWeek): Boolean = and(1 shl (day.value - 1)) != 0

    private const val WEEKDAY_MASK = 0b1111111
}
