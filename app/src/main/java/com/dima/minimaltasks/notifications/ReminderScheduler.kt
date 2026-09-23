package com.dima.minimaltasks.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.dima.minimaltasks.data.local.TaskEntity

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

    fun cancelAll(tasks: Iterable<TaskEntity>) {
        tasks.forEach { cancel(it.id) }
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun alarmAccuracy(): AlarmAccuracy = ReminderScheduling.alarmAccuracy(
        apiLevel = Build.VERSION.SDK_INT,
        canScheduleExactAlarms = canScheduleExactAlarms(),
    )

    /**
     * Rebuilds future alarms. A past-due eligible alarm may still be pending (inexact alarms
     * can fire late), so do not cancel it. Also never cancel posted notifications here:
     * a cold-start reconcile races with [ReminderAlarmReceiver].
     */
    fun reconcile(
        tasks: List<TaskEntity>,
        notificationsEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!notificationsEnabled) {
            tasks.forEach { cancel(it.id) }
            return
        }
        tasks.forEach { task ->
            if (ReminderScheduling.shouldSchedule(task, nowMillis)) {
                schedule(task, nowMillis)
            } else if (!ReminderScheduling.isEligible(task)) {
                cancelAlarm(task.id)
            }
        }
    }

    private fun scheduleAt(taskId: String, triggerAtMillis: Long, nowMillis: Long): Boolean {
        val mode = ReminderScheduling.scheduleMode(
            apiLevel = Build.VERSION.SDK_INT,
            canScheduleExactAlarms = canScheduleExactAlarms(),
        )
        val pendingIntent = alarmPendingIntent(taskId)
        try {
            when (mode) {
                AlarmScheduleMode.EXACT_ALLOW_IDLE ->
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )

                AlarmScheduleMode.ALLOW_IDLE ->
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )

                AlarmScheduleMode.INEXACT ->
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (error: SecurityException) {
            // The exact-alarm grant can change between the capability check and the call.
            // Keep reminders useful by degrading to the idle-tolerant inexact API.
            if (mode != AlarmScheduleMode.EXACT_ALLOW_IDLE) throw error
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
        return triggerAtMillis > nowMillis
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
}
