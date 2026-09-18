package com.dima.minimaltasks

import com.dima.minimaltasks.ui.TaskFormatters
import com.dima.minimaltasks.ui.TaskEditorState
import com.dima.minimaltasks.ui.TaskEditorValidation
import com.dima.minimaltasks.ui.EditorError
import com.dima.minimaltasks.data.local.RecurrenceUnit
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFormattersTest {
    private val zone = ZoneId.of("UTC")
    private val locale = Locale("ru")

    @Test
    fun sameDayDueUsesTimeOnly() {
        val date = LocalDate.of(2026, 9, 17)
        val due = date.atTime(9, 5).atZone(zone).toInstant().toEpochMilli()
        val now = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()

        val result = TaskFormatters.duePresentation(due, true, now, locale, zone)

        assertEquals("09:05", result?.text)
        assertFalse(result?.overdue ?: true)
    }

    @Test
    fun pastDueIsMarkedOverdue() {
        val date = LocalDate.of(2026, 9, 17)
        val due = date.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli()

        assertTrue(TaskFormatters.duePresentation(due, true, now, locale, zone)?.overdue == true)
    }

    @Test
    fun dateOnlyDueIsNotOverdueDuringItsLocalDayAndNeverShowsMidnight() {
        val date = LocalDate.of(2026, 9, 17)
        val due = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val midday = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val nextDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val today = TaskFormatters.duePresentation(due, false, midday, locale, zone)
        val tomorrow = TaskFormatters.duePresentation(due, false, nextDay, locale, zone)

        assertFalse(today?.overdue ?: true)
        assertTrue(tomorrow?.overdue == true)
        assertFalse(today?.text?.contains("00:00") == true)
    }

    @Test
    fun recurrenceWithoutDueDateIsRejected() {
        val state = TaskEditorState(
            taskId = "task",
            sourceTask = null,
            title = "Read",
            description = "",
            dueAt = null,
            hasTime = false,
            isPriority = false,
            recurrenceEnabled = true,
            recurrenceUnit = RecurrenceUnit.DAY,
            recurrenceInterval = "1",
            recurrenceWeekdayMask = 0,
            recurrenceZoneId = zone.id,
            attachments = emptyList(),
        )

        assertEquals(EditorError.RECURRENCE_REQUIRES_DUE_DATE, TaskEditorValidation.errorFor(state))
    }

    @Test
    fun datePickerUsesSelectedZoneStartOfDay() {
        val date = LocalDate.of(2026, 9, 17)

        val result = TaskFormatters.datePickerMillis(date, zone)

        assertEquals(date.atStartOfDay(zone).toInstant().toEpochMilli(), result)
    }
}
