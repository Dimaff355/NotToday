package com.dima.minimaltasks

import android.app.Application
import com.dima.minimaltasks.data.AttachmentStore
import com.dima.minimaltasks.data.TaskRepository
import com.dima.minimaltasks.data.backup.BackupManager
import com.dima.minimaltasks.data.local.AppDatabase
import com.dima.minimaltasks.data.settings.SettingsRepository
import com.dima.minimaltasks.notifications.ReminderNotifications
import com.dima.minimaltasks.notifications.ReminderCoordinator
import com.dima.minimaltasks.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MinimalTasksApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: TaskRepository by lazy { TaskRepository(database) }
    val attachmentStore: AttachmentStore by lazy { AttachmentStore(filesDir) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(this) }
    val reminderCoordinator: ReminderCoordinator by lazy {
        ReminderCoordinator(repository, settingsRepository, reminderScheduler)
    }
    val backupManager: BackupManager by lazy {
        BackupManager(repository, attachmentStore, settingsRepository, reminderCoordinator)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.ensureChannel(this)
        applicationScope.launch { reconcileReminders() }
    }

    suspend fun reconcileReminders(nowMillis: Long = System.currentTimeMillis()) {
        reminderCoordinator.reconcile(nowMillis)
    }
}
