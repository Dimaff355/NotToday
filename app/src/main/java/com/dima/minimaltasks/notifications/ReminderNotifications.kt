package com.dima.minimaltasks.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dima.minimaltasks.MainActivity
import com.dima.minimaltasks.R
import com.dima.minimaltasks.data.local.TaskEntity

object ReminderNotifications {
    const val CHANNEL_ID = "task_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun post(context: Context, task: TaskEntity) {
        if (!canPost(context)) return
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("minimal-tasks://task/${task.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            ReminderIdentity.contentRequestCode(task.id),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body, task.title))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .addAction(
                android.R.drawable.ic_menu_save,
                context.getString(R.string.reminder_complete),
                actionPendingIntent(context, task.id, ReminderIntents.ACTION_COMPLETE, ReminderIdentity.completeRequestCode(task.id)),
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                context.getString(R.string.reminder_snooze),
                actionPendingIntent(context, task.id, ReminderIntents.ACTION_SNOOZE, ReminderIdentity.snoozeRequestCode(task.id)),
            )
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ReminderIdentity.notificationId(task.id), notification)
        } catch (_: SecurityException) {
            // The notification permission can be revoked between the check and notify().
        }
    }

    /** Day-before digest: one notification titled «due tomorrow» with a line per task. */
    fun postDayBefore(context: Context, tasks: List<TaskEntity>) {
        if (tasks.isEmpty() || !canPost(context)) return
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("minimal-tasks://day-before"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            ReminderIdentity.dayBeforeContentRequestCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val titles = tasks.joinToString(", ") { it.title }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.day_before_title))
            .setContentText(titles)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        if (tasks.size > 1) {
            builder.setStyle(
                NotificationCompat.InboxStyle()
                    .setBigContentTitle(context.getString(R.string.day_before_title))
                    .setSummaryText(titles)
                    .also { style -> tasks.forEach { style.addLine(it.title) } },
            )
        }
        try {
            NotificationManagerCompat.from(context)
                .notify(ReminderIdentity.dayBeforeNotificationId(), builder.build())
        } catch (_: SecurityException) {
            // The notification permission can be revoked between the check and notify().
        }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun actionPendingIntent(
        context: Context,
        taskId: String,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("minimal-tasks://${action.substringAfterLast('.').lowercase()}/$taskId"))
            .putExtra(ReminderIntents.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

}
