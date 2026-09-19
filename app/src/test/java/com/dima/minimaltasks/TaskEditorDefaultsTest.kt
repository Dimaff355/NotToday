package com.dima.minimaltasks

import com.dima.minimaltasks.data.local.RecurrenceUnit
import com.dima.minimaltasks.ui.TaskEditorDefaults
import com.dima.minimaltasks.ui.TaskEditorState
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskEditorDefaultsTest {
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val today: LocalDate = LocalDate.of(2026, 9, 19)

    private fun millis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun state(
        dueAt: Long? = null,
        hasTime: Boolean = false,
        recurrenceEnabled: Boolean = false,
        dueDateAutoAssigned: Boolean = false,
    ) = TaskEditorState(
        taskId = "task",
        sourceTask = null,
        title = "Read",
        description = "",
        dueAt = dueAt,
        hasTime = hasTime,
        isPriority = false,
        recurrenceEnabled = recurrenceEnabled,
        recurrenceUnit = RecurrenceUnit.DAY,
        recurrenceInterval = "1",
        recurrenceWeekdayMask = 0,
        attachments = emptyList(),
        dueDateAutoAssigned = dueDateAutoAssigned,
    )

    @Test
    fun newTaskStartsOnTodayAndOwnsThatDate() {
        val due = TaskEditorDefaults.newTaskDue(prefilledDate = null, zoneId = zone)

        assertEquals(millis(LocalDate.now(zone)), due.millis)
        assertTrue(due.autoAssigned)
    }

    @Test
    fun newTaskFromCalendarKeepsThePickedDateUnowned() {
        val due = TaskEditorDefaults.newTaskDue(prefilledDate = today, zoneId = zone)

        assertEquals(millis(today), due.millis)
        assertFalse(due.autoAssigned)
    }

    @Test
    fun enablingRecurrenceWithoutDateSetsToday() {
        val result = TaskEditorDefaults.applyRecurrenceToggle(state(), enabled = true, todayMillis = millis(today))

        assertTrue(result.recurrenceEnabled)
        assertEquals(millis(today), result.dueAt)
        assertTrue(result.dueDateAutoAssigned)
    }

    @Test
    fun enablingRecurrenceKeepsAnExistingDate() {
        val result = TaskEditorDefaults.applyRecurrenceToggle(
            state(dueAt = millis(today), recurrenceEnabled = false),
            enabled = true,
            todayMillis = millis(today),
        )

        assertEquals(millis(today), result.dueAt)
        assertFalse(result.dueDateAutoAssigned)
    }

    @Test
    fun disablingRecurrenceRemovesTheAutoAssignedDateAndTime() {
        val result = TaskEditorDefaults.applyRecurrenceToggle(
            state(dueAt = millis(today), hasTime = true, recurrenceEnabled = true, dueDateAutoAssigned = true),
            enabled = false,
            todayMillis = millis(today),
        )

        assertFalse(result.recurrenceEnabled)
        assertNull(result.dueAt)
        assertFalse(result.hasTime)
        assertFalse(result.dueDateAutoAssigned)
    }

    @Test
    fun disablingRecurrenceKeepsADateTheUserPicked() {
        val picked = LocalDate.of(2026, 10, 5)
        val result = TaskEditorDefaults.applyRecurrenceToggle(
            state(dueAt = millis(picked), recurrenceEnabled = true),
            enabled = false,
            todayMillis = millis(today),
        )

        assertFalse(result.recurrenceEnabled)
        assertEquals(millis(picked), result.dueAt)
    }
}
