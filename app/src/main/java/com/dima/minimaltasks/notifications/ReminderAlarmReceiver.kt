package com.dima.minimaltasks.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dima.minimaltasks.MinimalTasksApplication
import kotlinx.coroutines.flow.first

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderIntents.EXTRA_TASK_ID) ?: return
        runBoundedAsync {
            val app = context.applicationContext as MinimalTasksApplication
            if (!app.settingsRepository.state.first().notificationsEnabled) {
                app.reminderScheduler.cancel(taskId)
                return@runBoundedAsync
            }
            val task = app.repository.findTask(taskId)
            if (task == null || task.completed) {
                app.reminderScheduler.cancel(taskId)
                return@runBoundedAsync
            }
            ReminderNotifications.post(context.applicationContext, task)
        }
    }
}
