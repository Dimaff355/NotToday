package com.dima.minimaltasks

import com.dima.minimaltasks.data.CompletionResult
import com.dima.minimaltasks.data.UndoCompletionResult
import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.notifications.ReminderMutationDecisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderMutationDecisionsTest {
    private val now = 1_700_000_000_000L

    @Test
    fun saving_without_notifications_only_cancels_task_id() {
        val task = task("task", now + 60_000)

        val plan = ReminderMutationDecisions.afterTaskSaved(task, notificationsEnabled = false, nowMillis = now)

        assertEquals(setOf("task"), plan.cancelTaskIds)
        assertTrue(plan.scheduleTasks.isEmpty())
    }

    @Test
    fun completion_cancels_original_and_schedules_generated_child() {
        val original = task("original", now + 60_000).copy(completed = true, completedAt = now)
        val child = task("child", now + 120_000).copy(recurrenceParentTaskId = original.id)

        val plan = ReminderMutationDecisions.afterCompletion(
            CompletionResult(original, child),
            notificationsEnabled = true,
            nowMillis = now,
        )

        assertEquals(setOf("original"), plan.cancelTaskIds)
        assertEquals(listOf(child), plan.scheduleTasks)
    }

    @Test
    fun undo_completion_cancels_the_generated_chain_and_reschedules_restored_task() {
        val restored = task("original", now + 60_000)
        val result = UndoCompletionResult(restoredTask = restored, removedChildIds = listOf("child", "grandchild"))

        val plan = ReminderMutationDecisions.afterUndoCompletion(result, notificationsEnabled = true, nowMillis = now)

        assertEquals(setOf("original", "child", "grandchild"), plan.cancelTaskIds)
        assertEquals(listOf(restored), plan.scheduleTasks)
    }

    private fun task(id: String, dueAt: Long) = TaskEntity(
        id = id,
        title = id,
        dueAt = dueAt,
        dueHasTime = true,
        createdAt = now,
        updatedAt = now,
    )
}
