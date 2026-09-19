package com.dima.minimaltasks.ui

import java.time.LocalDate
import java.time.ZoneId

/** A due date value produced by the editor defaults, with the flag telling who owns it. */
data class EditorDueDate(val millis: Long, val autoAssigned: Boolean)

/**
 * Date defaults of the task editor. A new task starts on today, and the repetition switch owns
 * that auto-assigned date: turning repetition off takes it away again. A date the user picked
 * (or one that came from the calendar) is never touched by the switch.
 */
object TaskEditorDefaults {
    fun newTaskDue(prefilledDate: LocalDate?, zoneId: ZoneId = ZoneId.systemDefault()): EditorDueDate {
        val date = prefilledDate ?: LocalDate.now(zoneId)
        return EditorDueDate(
            millis = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            autoAssigned = prefilledDate == null,
        )
    }

    fun todayStartMillis(zoneId: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zoneId)): Long =
        today.atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun applyRecurrenceToggle(state: TaskEditorState, enabled: Boolean, todayMillis: Long): TaskEditorState = when {
        enabled && state.dueAt == null -> state.copy(
            recurrenceEnabled = true,
            dueAt = todayMillis,
            dueDateAutoAssigned = true,
        )
        !enabled && state.dueDateAutoAssigned -> state.copy(
            recurrenceEnabled = false,
            dueAt = null,
            hasTime = false,
            dueDateAutoAssigned = false,
        )
        else -> state.copy(recurrenceEnabled = enabled)
    }
}
