package com.dima.minimaltasks.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.dima.minimaltasks.data.local.TaskEntity
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = NotificationManagerCompat.from(context)

    fun schedule(task: TaskEntity, nowMillis: Long = System.currentTimeMillis()): Boolean {
        cancelAlarm(task.id)
        if (!ReminderScheduling.shouldSchedule(task, nowMillis)) return false
        return scheduleAt(task.id, task.dueAt!!, nowMillis)
    }

    fun scheduleSnooze(
        taskId: String,
        triggerAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        cancelAlarm(taskId)
        if (triggerAtMillis <= nowMillis) return false
        return scheduleAt(taskId, triggerAtMillis, nowMillis)
    }

    /** Daily day-before digest alarm at the given local minute of day. */
    fun scheduleDayBefore(minuteOfDay: Int, nowMillis: Long = System.currentTimeMillis()) {
        setAlarmAt(
            dayBeforePendingIntent(),
            ReminderScheduling.nextDayBeforeAt(minuteOfDay, nowMillis, ZoneId.systemDefault()),
        )
    }

    fun cancelDayBefore() {
        alarmManager.cancel(dayBeforePendingIntent())
        notificationManager.cancel(ReminderIdentity.dayBeforeNotificationId())
    }

    fun cancelAlarm(taskId: String) {
        alarmManager.cancel(alarmPendingIntent(taskId))
    }

    fun cancelNotification(taskId: String) {
        notificationManager.cancel(ReminderIdentity.notificationId(taskId))
    }

    fun cancel(taskId: String) {
        cancelAlarm(taskId)
        cancelNotification(taskId)
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun alarmAccuracy(): AlarmAccuracy =
        if (canScheduleExactAlarms()) AlarmAccuracy.EXACT else AlarmAccuracy.FALLBACK

    /**
     * Rebuilds alarms from [schedulable] (active, timed, still ahead — see
     * [com.dima.minimaltasks.data.TaskRepository.findSchedulable]) and the digest; runs on every
     * start and resume, so its cost follows the pending tasks, not the whole history.
     * Past-due alarms are left alone: an inexact one may still be pending, and cancelling
     * posted notifications here would race with [ReminderAlarmReceiver] on a cold start. Stale
     * alarms of tasks that stopped being eligible are harmless: receivers re-check the task.
     * With reminders off, pending alarms and every posted notification are removed.
     */
    fun reconcile(
        schedulable: List<TaskEntity>,
        notificationsEnabled: Boolean,
        dayBeforeMinuteOfDay: Int? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (notificationsEnabled && dayBeforeMinuteOfDay != null) {
            scheduleDayBefore(dayBeforeMinuteOfDay, nowMillis)
        } else {
            cancelDayBefore()
        }
        if (notificationsEnabled) {
            schedulable.forEach { schedule(it, nowMillis) }
        } else {
            schedulable.forEach { cancelAlarm(it.id) }
            notificationManager.cancelAll()
        }
    }

    private fun scheduleAt(taskId: String, triggerAtMillis: Long, nowMillis: Long): Boolean {
        setAlarmAt(alarmPendingIntent(taskId), triggerAtMillis)
        return triggerAtMillis > nowMillis
    }

    /** Exact whenever allowed (before API 31 that needs no grant), otherwise idle-tolerant inexact. */
    private fun setAlarmAt(pendingIntent: PendingIntent, triggerAtMillis: Long) {
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (_: SecurityException) {
                // The exact-alarm grant can change between the capability check and the call.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun alarmPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderIntents.ACTION_FIRE)
            .setData(Uri.parse("minimal-tasks://alarm/$taskId"))
            .putExtra(ReminderIntents.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            ReminderIdentity.alarmRequestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dayBeforePendingIntent(): PendingIntent {
        val intent = Intent(context, DayBeforeAlarmReceiver::class.java)
            .setAction(ReminderIntents.ACTION_DAY_BEFORE_FIRE)
            .setData(Uri.parse("minimal-tasks://alarm/day-before"))
        return PendingIntent.getBroadcast(
            context,
            ReminderIdentity.dayBeforeAlarmRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
