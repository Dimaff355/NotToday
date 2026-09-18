package com.dima.minimaltasks.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dima.minimaltasks.MinimalTasksApplication

class ReminderReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        runBoundedAsync {
            val app = context.applicationContext as MinimalTasksApplication
            app.reconcileReminders()
        }
    }
}
