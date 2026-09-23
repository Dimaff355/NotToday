package com.dima.minimaltasks.notifications

import com.dima.minimaltasks.data.local.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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

    /**
     * Next day-before digest fire time: today at [minuteOfDay] when that moment is still ahead,
     * otherwise the same time tomorrow. DST gaps/overlaps are left to java.time, which shifts
     * the local time into the valid range of the resulting day.
     */
    fun nextDayBeforeAt(minuteOfDay: Int, nowMillis: Long, zoneId: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis)
        val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        var next = now.atZone(zoneId).toLocalDate()
            .atTime(LocalTime.of(minute / 60, minute % 60))
            .atZone(zoneId)
        if (!next.toInstant().isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    /** Active tasks whose local due date is [date], by due time then creation. */
    fun tasksDueOn(tasks: List<TaskEntity>, date: LocalDate, zoneId: ZoneId): List<TaskEntity> =
        tasks.asSequence()
            .filter { !it.completed && it.dueAt != null }
            .filter { Instant.ofEpochMilli(it.dueAt!!).atZone(zoneId).toLocalDate() == date }
            .sortedWith(compareBy({ it.dueAt!! }, { it.createdAt }))
            .toList()

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
