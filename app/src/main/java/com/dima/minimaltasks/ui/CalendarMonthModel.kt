package com.dima.minimaltasks.ui

import com.dima.minimaltasks.data.local.TaskEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

data class CalendarDayCell(
    val date: LocalDate,
    val isInCurrentMonth: Boolean,
)

data class CalendarMonthModel(
    val month: YearMonth,
    val firstDayOfWeek: DayOfWeek,
    val weekdayLabels: List<String>,
    val cells: List<CalendarDayCell>,
) {
    init {
        require(weekdayLabels.size == 7) { "A calendar week must have seven labels" }
        require(cells.size == 42) { "A compact month calendar must have 42 cells" }
    }

    companion object {
        fun forMonth(month: YearMonth, locale: Locale): CalendarMonthModel {
            val firstDay = WeekFields.of(locale).firstDayOfWeek
            val monthStart = month.atDay(1)
            val offset = Math.floorMod(monthStart.dayOfWeek.value - firstDay.value, 7)
            val gridStart = monthStart.minusDays(offset.toLong())
            val labels = List(7) { index ->
                firstDay.plus(index.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale)
            }
            val cells = List(42) { index ->
                val date = gridStart.plusDays(index.toLong())
                CalendarDayCell(date = date, isInCurrentMonth = date.month == month.month && date.year == month.year)
            }
            return CalendarMonthModel(month, firstDay, labels, cells)
        }
    }
}

enum class CalendarDotKind {
    ACTIVE_PRIORITY,
    ACTIVE,
    COMPLETED,
}

data class CalendarDaySummary(
    val tasks: List<TaskEntity>,
    val dots: List<CalendarDotKind>,
    val completedOnly: Boolean,
)

object CalendarTaskGrouping {
    fun byLocalDate(tasks: Iterable<TaskEntity>, zoneId: java.time.ZoneId): Map<LocalDate, List<TaskEntity>> =
        tasks.filter { it.dueAt != null }
            .groupBy { task -> Instant.ofEpochMilli(task.dueAt!!).atZone(zoneId).toLocalDate() }

    fun summaryFor(date: LocalDate, tasksByDate: Map<LocalDate, List<TaskEntity>>): CalendarDaySummary {
        val tasks = tasksByDate[date].orEmpty()
        val active = tasks.filterNot(TaskEntity::completed)
        val completedOnly = tasks.isNotEmpty() && active.isEmpty()
        val dots = buildList {
            addAll(active.filter(TaskEntity::isPriority).map { CalendarDotKind.ACTIVE_PRIORITY })
            addAll(active.filterNot(TaskEntity::isPriority).map { CalendarDotKind.ACTIVE })
            addAll(tasks.filter(TaskEntity::completed).map { CalendarDotKind.COMPLETED })
        }.take(3)
        return CalendarDaySummary(tasks = tasks, dots = dots, completedOnly = completedOnly)
    }
}
