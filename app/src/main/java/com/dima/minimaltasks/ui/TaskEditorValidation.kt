package com.dima.minimaltasks.ui

object TaskEditorValidation {
    fun errorFor(state: TaskEditorState): EditorError? = when {
        state.title.trim().isEmpty() -> EditorError.REQUIRED_TITLE
        state.recurrenceEnabled && state.dueAt == null -> EditorError.RECURRENCE_REQUIRES_DUE_DATE
        state.recurrenceEnabled && state.recurrenceInterval.toIntOrNull() !in 1..999 -> EditorError.INVALID_RECURRENCE
        else -> null
    }
}
