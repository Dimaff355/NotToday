package com.dima.minimaltasks.notifications

internal object ReminderIntents {
    const val ACTION_FIRE = "com.dima.minimaltasks.action.REMINDER_FIRE"
    const val ACTION_DAY_BEFORE_FIRE = "com.dima.minimaltasks.action.DAY_BEFORE_FIRE"
    const val ACTION_COMPLETE = "com.dima.minimaltasks.action.REMINDER_COMPLETE"
    const val ACTION_SNOOZE = "com.dima.minimaltasks.action.REMINDER_SNOOZE"
    const val EXTRA_TASK_ID = "com.dima.minimaltasks.extra.TASK_ID"

    /** Content URI of a reminder notification: tapping it opens this task in the editor. */
    private const val TASK_URI_PREFIX = "minimal-tasks://task/"
    private val TASK_ID = Regex("[A-Za-z0-9_-]{1,128}")

    fun taskUri(taskId: String): String = TASK_URI_PREFIX + taskId

    /** The task id of a [taskUri], or null for anything else (MainActivity is exported). */
    fun taskIdFromUri(uri: String?): String? =
        uri?.takeIf { it.startsWith(TASK_URI_PREFIX) }?.removePrefix(TASK_URI_PREFIX)?.takeIf(TASK_ID::matches)
}
