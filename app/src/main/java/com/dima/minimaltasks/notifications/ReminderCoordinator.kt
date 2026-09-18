package com.dima.minimaltasks.notifications

import com.dima.minimaltasks.data.CompletionResult
import com.dima.minimaltasks.data.TaskRepository
import com.dima.minimaltasks.data.UndoCompletionResult
import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

data class ReminderMutationPlan(
    val cancelTaskIds: Set<String>,
    val scheduleTasks: List<TaskEntity>,
)

object ReminderMutationDecisions {
    fun afterTaskSaved(task: TaskEntity, notificationsEnabled: Boolean, nowMillis: Long): ReminderMutationPlan =
        ReminderMutationPlan(
            cancelTaskIds = setOf(task.id),
            scheduleTasks = if (notificationsEnabled && ReminderScheduling.shouldSchedule(task, nowMillis)) listOf(task) else emptyList(),
        )

    fun afterCompletion(result: CompletionResult, notificationsEnabled: Boolean, nowMillis: Long): ReminderMutationPlan =
        ReminderMutationPlan(
            cancelTaskIds = setOf(result.completedTask.id),
            scheduleTasks = result.generatedTask
                ?.takeIf { notificationsEnabled && ReminderScheduling.shouldSchedule(it, nowMillis) }
                ?.let(::listOf)
                ?: emptyList(),
        )

    fun afterUndoCompletion(result: UndoCompletionResult, notificationsEnabled: Boolean, nowMillis: Long): ReminderMutationPlan =
        ReminderMutationPlan(
            cancelTaskIds = setOfNotNull(result.removedChildId, result.restoredTask.id),
            scheduleTasks = if (notificationsEnabled && ReminderScheduling.shouldSchedule(result.restoredTask, nowMillis)) {
                listOf(result.restoredTask)
            } else {
                emptyList()
            },
        )
}

class ReminderCoordinator(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend fun onTaskSaved(task: TaskEntity, nowMillis: Long = System.currentTimeMillis()) {
        apply(ReminderMutationDecisions.afterTaskSaved(task, notificationsEnabled(), nowMillis), nowMillis)
    }

    suspend fun onTaskDeleted(taskId: String) {
        scheduler.cancel(taskId)
    }

    suspend fun onTaskRestored(task: TaskEntity, nowMillis: Long = System.currentTimeMillis()) {
        apply(ReminderMutationDecisions.afterTaskSaved(task, notificationsEnabled(), nowMillis), nowMillis)
    }

    suspend fun onCompletion(result: CompletionResult, nowMillis: Long = System.currentTimeMillis()) {
        apply(ReminderMutationDecisions.afterCompletion(result, notificationsEnabled(), nowMillis), nowMillis)
    }

    suspend fun onUndoCompletion(result: UndoCompletionResult, nowMillis: Long = System.currentTimeMillis()) {
        apply(ReminderMutationDecisions.afterUndoCompletion(result, notificationsEnabled(), nowMillis), nowMillis)
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        settingsRepository.setNotificationsEnabled(enabled)
        val tasks = repository.snapshot()
        if (!enabled) {
            scheduler.cancelAll(tasks)
        } else {
            scheduler.reconcile(tasks, notificationsEnabled = true)
        }
    }

    suspend fun reconcile(nowMillis: Long = System.currentTimeMillis()) {
        scheduler.reconcile(repository.snapshot(), notificationsEnabled(), nowMillis)
    }

    private suspend fun notificationsEnabled(): Boolean = settingsRepository.state.first().notificationsEnabled

    private fun apply(plan: ReminderMutationPlan, nowMillis: Long) {
        plan.cancelTaskIds.forEach(scheduler::cancel)
        plan.scheduleTasks.forEach { scheduler.schedule(it, nowMillis) }
    }
}
