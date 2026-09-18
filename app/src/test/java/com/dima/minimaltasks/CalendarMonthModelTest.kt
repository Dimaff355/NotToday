package com.dima.minimaltasks

import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.ui.CalendarDotKind
import com.dima.minimaltasks.ui.CalendarMonthModel
import com.dima.minimaltasks.ui.CalendarTaskGrouping
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthModelTest {
    @Test
    fun every_month_length_has_a_six_row_grid() {
        assertMonthHasAllDays(YearMonth.of(2025, Month.FEBRUARY), 28)
        assertMonthHasAllDays(YearMonth.of(2024, Month.FEBRUARY), 29)
        assertMonthHasAllDays(YearMonth.of(2026, Month.APRIL), 30)
        assertMonthHasAllDays(YearMonth.of(2026, Month.MAY), 31)
    }

    @Test
    fun first_day_offset_uses_locale_week_fields() {
        val us = CalendarMonthModel.forMonth(YearMonth.of(2026, 2), Locale.US)
        val russian = CalendarMonthModel.forMonth(YearMonth.of(2026, 2), Locale.forLanguageTag("ru-RU"))

        assertEquals(LocalDate.of(2026, 2, 1), us.cells.first().date)
        assertEquals(LocalDate.of(2026, 1, 26), russian.cells.first().date)
        assertEquals(7, us.weekdayLabels.size)
        assertEquals(7, russian.weekdayLabels.size)
    }

    @Test
    fun outside_month_cells_are_marked_but_remain_real_dates() {
        val model = CalendarMonthModel.forMonth(YearMonth.of(2026, 2), Locale.forLanguageTag("ru-RU"))

        assertFalse(model.cells.first().isInCurrentMonth)
        assertTrue(model.cells.first().date.isBefore(YearMonth.of(2026, 2).atDay(1)))
        assertTrue(model.cells.last().date.isAfter(YearMonth.of(2026, 2).atEndOfMonth()))
    }

    @Test
    fun grouping_uses_the_requested_local_timezone_and_ignores_undated_tasks() {
        val zone = ZoneId.of("America/Los_Angeles")
        val localDate = LocalDate.of(2026, 3, 1)
        val crossingMidnight = localDate.atStartOfDay(ZoneId.of("UTC")).plusMinutes(30).toInstant().toEpochMilli()
        val task = task("crossing", crossingMidnight)
        val undated = task("undated", null)

        val grouped = CalendarTaskGrouping.byLocalDate(listOf(task, undated), zone)

        assertEquals(listOf(task), grouped[LocalDate.of(2026, 2, 28)])
        assertFalse(grouped.containsKey(localDate))
    }

    @Test
    fun summary_prioritizes_active_red_dots_and_subdues_completed_only_days() {
        val date = LocalDate.of(2026, 9, 17)
        val tasks = listOf(
            task("normal", date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()),
            task("priority", date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), priority = true),
        )
        val summary = CalendarTaskGrouping.summaryFor(date, CalendarTaskGrouping.byLocalDate(tasks, ZoneId.of("UTC")))

        assertEquals(CalendarDotKind.ACTIVE_PRIORITY, summary.dots.first())
        assertFalse(summary.completedOnly)

        val completed = task("done", date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), completed = true)
        val completedSummary = CalendarTaskGrouping.summaryFor(
            date,
            CalendarTaskGrouping.byLocalDate(listOf(completed), ZoneId.of("UTC")),
        )
        assertEquals(listOf(CalendarDotKind.COMPLETED), completedSummary.dots)
        assertTrue(completedSummary.completedOnly)
    }

    private fun assertMonthHasAllDays(month: YearMonth, expectedDays: Int) {
        val model = CalendarMonthModel.forMonth(month, Locale.US)
        assertEquals(42, model.cells.size)
        assertEquals(expectedDays, model.cells.count { it.isInCurrentMonth })
    }

    private fun task(
        id: String,
        dueAt: Long?,
        priority: Boolean = false,
        completed: Boolean = false,
    ) = TaskEntity(
        id = id,
        title = id,
        dueAt = dueAt,
        dueHasTime = false,
        isPriority = priority,
        completed = completed,
        completedAt = if (completed) dueAt else null,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
