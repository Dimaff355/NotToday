package com.dima.minimaltasks.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dima.minimaltasks.MinimalTasksApplication
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fires the daily day-before digest: one notification listing the active tasks due tomorrow,
 * then re-arms itself for the next day (even when today turned out empty, the alarm must
 * keep running).
 */
class DayBeforeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderIntents.ACTION_DAY_BEFORE_FIRE) return
        runBoundedAsync {
            val app = context.applicationContext as MinimalTasksApplication
            val settings = app.settingsRepository.state.first()
            if (!settings.notificationsEnabled || !settings.dayBeforeEnabled) {
                app.reminderScheduler.cancelDayBefore()
                return@runBoundedAsync
            }
            val zoneId = ZoneId.systemDefault()
            val dueTomorrow = ReminderScheduling.tasksDueOn(
                app.repository.snapshot(),
                LocalDate.now(zoneId).plusDays(1),
                zoneId,
            )
            if (dueTomorrow.isNotEmpty()) {
                ReminderNotifications.postDayBefore(context.applicationContext, dueTomorrow)
            }
            app.reminderScheduler.scheduleDayBefore(settings.dayBeforeMinuteOfDay)
        }
    }
}
