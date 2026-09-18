package com.dima.minimaltasks.notifications

import com.dima.minimaltasks.data.local.TaskEntity

enum class AlarmScheduleMode {
    EXACT_ALLOW_IDLE,
    ALLOW_IDLE,
    INEXACT,
}

enum class AlarmAccuracy {
    EXACT,
    FALLBACK,
    STANDARD,
}

object ReminderScheduling {
    const val SNOOZE_MILLIS: Long = 10 * 60 * 1000L

    fun isEligible(task: TaskEntity): Boolean =
        !task.completed && task.dueAt != null && task.dueHasTime

    fun shouldSchedule(task: TaskEntity, nowMillis: Long): Boolean =
        isEligible(task) && task.dueAt!! > nowMillis

    fun scheduleMode(apiLevel: Int, canScheduleExactAlarms: Boolean): AlarmScheduleMode =
        when {
            apiLevel >= 31 && canScheduleExactAlarms -> AlarmScheduleMode.EXACT_ALLOW_IDLE
            apiLevel >= 23 -> AlarmScheduleMode.ALLOW_IDLE
            else -> AlarmScheduleMode.INEXACT
        }

    fun alarmAccuracy(apiLevel: Int, canScheduleExactAlarms: Boolean): AlarmAccuracy =
        when {
            apiLevel < 31 -> AlarmAccuracy.STANDARD
            canScheduleExactAlarms -> AlarmAccuracy.EXACT
            else -> AlarmAccuracy.FALLBACK
        }

    fun snoozeAt(nowMillis: Long): Long =
        if (nowMillis > Long.MAX_VALUE - SNOOZE_MILLIS) Long.MAX_VALUE
        else nowMillis + SNOOZE_MILLIS
}
