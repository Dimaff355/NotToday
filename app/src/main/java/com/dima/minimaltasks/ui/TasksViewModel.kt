package com.dima.minimaltasks.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dima.minimaltasks.data.AttachmentStore
import com.dima.minimaltasks.data.AttachmentTooLargeException
import com.dima.minimaltasks.data.CompletionResult
import com.dima.minimaltasks.data.TaskRepository
import com.dima.minimaltasks.data.TaskSaveRequest
import com.dima.minimaltasks.data.UndoCompletionResult
import com.dima.minimaltasks.data.local.AttachmentEntity
import com.dima.minimaltasks.data.local.RecurrenceUnit
import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.notifications.ReminderCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class EditorError {
    REQUIRED_TITLE,
    INVALID_RECURRENCE,
    RECURRENCE_REQUIRES_DUE_DATE,
    ATTACHMENT_LIMIT,
    ATTACHMENT_TOO_LARGE,
    ATTACHMENT_ERROR,
    SAVE_ERROR,
}

data class StagedAttachmentState(val value: AttachmentStore.StagedAttachment)

data class TaskEditorState(
    val taskId: String,
    val sourceTask: TaskEntity?,
    val title: String,
    val description: String,
    val dueAt: Long?,
    val hasTime: Boolean,
    val isPriority: Boolean,
    val recurrenceEnabled: Boolean,
    val recurrenceUnit: RecurrenceUnit,
    val recurrenceInterval: String,
    val recurrenceWeekdayMask: Int,
    val recurrenceZoneId: String,
    val attachments: List<AttachmentEntity>,
    val removedAttachmentIds: Set<String> = emptySet(),
    val stagedAttachments: List<StagedAttachmentState> = emptyList(),
    val error: EditorError? = null,
)

data class DeletedTaskSnapshot(
    val task: TaskEntity,
    val attachments: List<AttachmentEntity>,
)

class TasksViewModel(
    private val repository: TaskRepository,
    private val attachmentStore: AttachmentStore,
    private val reminderCoordinator: ReminderCoordinator,
) : ViewModel() {
    val tasks: StateFlow<List<TaskEntity>> = repository.observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val _editor = MutableStateFlow<TaskEditorState?>(null)
    val editor: StateFlow<TaskEditorState?> = _editor.asStateFlow()

    private val deletedTasks = mutableMapOf<String, DeletedTaskSnapshot>()

    fun openNewTask() = openNewTaskInternal(null)

    fun openNewTaskForDate(date: LocalDate) = openNewTaskInternal(date)

    private fun openNewTaskInternal(prefilledDate: LocalDate?) {
        val id = UUID.randomUUID().toString()
        _editor.value = TaskEditorState(
            taskId = id,
            sourceTask = null,
            title = "",
            description = "",
            dueAt = prefilledDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
            hasTime = false,
            isPriority = false,
            recurrenceEnabled = false,
            recurrenceUnit = RecurrenceUnit.DAY,
            recurrenceInterval = "1",
            recurrenceWeekdayMask = 0,
            recurrenceZoneId = ZoneId.systemDefault().id,
            attachments = emptyList(),
        )
    }

    fun observeTasksForCalendarRange(
        visibleStart: LocalDate,
        visibleEndInclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<TaskEntity>> {
        require(!visibleEndInclusive.isBefore(visibleStart)) { "Calendar range must be ordered" }
        val start = visibleStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = visibleEndInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return repository.observeDateRange(start, end)
    }

    fun openExistingTask(taskId: String) {
        viewModelScope.launch {
            val task = repository.findTask(taskId) ?: return@launch
            _editor.value = TaskEditorState(
                taskId = task.id,
                sourceTask = task,
                title = task.title,
                description = task.description.orEmpty(),
                dueAt = task.dueAt,
                hasTime = task.dueHasTime,
                isPriority = task.isPriority,
                recurrenceEnabled = task.recurrenceUnit != null,
                recurrenceUnit = task.recurrenceUnit ?: RecurrenceUnit.DAY,
                recurrenceInterval = task.recurrenceInterval.toString(),
                recurrenceWeekdayMask = task.recurrenceWeekdayMask,
                recurrenceZoneId = task.recurrenceZoneId,
                attachments = repository.findAttachments(task.id),
            )
        }
    }

    fun updateEditor(transform: (TaskEditorState) -> TaskEditorState) {
        _editor.value = _editor.value?.let { transform(it).copy(error = null) }
    }

    fun setDueDate(date: LocalDate) {
        updateEditor { state ->
            val time = if (state.hasTime) dueLocalTime(state.dueAt) else LocalTime.MIDNIGHT
            state.copy(dueAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    fun setDueTime(hour: Int, minute: Int) {
        updateEditor { state ->
            val date = state.dueAt?.let(::dueLocalDate) ?: LocalDate.now()
            state.copy(
                dueAt = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                hasTime = true,
            )
        }
    }

    fun clearDueDate() = updateEditor { it.copy(dueAt = null, hasTime = false) }

    fun removeAttachment(attachmentId: String) {
        updateEditor { it.copy(removedAttachmentIds = it.removedAttachmentIds + attachmentId) }
    }

    fun restoreAttachment(attachmentId: String) {
        updateEditor { it.copy(removedAttachmentIds = it.removedAttachmentIds - attachmentId) }
    }

    fun stageAttachment(contentResolver: ContentResolver, uri: Uri) {
        val state = _editor.value ?: return
        val remaining = state.attachments.count { it.id !in state.removedAttachmentIds } + state.stagedAttachments.size
        if (remaining >= AttachmentStore.MAX_ATTACHMENTS_PER_TASK) {
            _editor.value = state.copy(error = EditorError.ATTACHMENT_LIMIT)
            return
        }
        viewModelScope.launch {
            try {
                val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment.orEmpty()
                val mimeType = contentResolver.getType(uri).orEmpty()
                contentResolver.openInputStream(uri)?.use { input ->
                    val staged = attachmentStore.stage(
                        taskId = state.taskId,
                        source = input,
                        displayName = displayName,
                        mimeType = mimeType,
                        existingCount = remaining,
                    )
                    _editor.value = _editor.value?.copy(
                        stagedAttachments = _editor.value!!.stagedAttachments + StagedAttachmentState(staged),
                        error = null,
                    )
                } ?: run { _editor.value = _editor.value?.copy(error = EditorError.ATTACHMENT_ERROR) }
            } catch (_: AttachmentTooLargeException) {
                _editor.value = _editor.value?.copy(error = EditorError.ATTACHMENT_TOO_LARGE)
            } catch (_: Throwable) {
                _editor.value = _editor.value?.copy(error = EditorError.ATTACHMENT_ERROR)
            }
        }
    }

    fun removeStagedAttachment(staged: StagedAttachmentState) {
        attachmentStore.discard(staged.value)
        updateEditor { it.copy(stagedAttachments = it.stagedAttachments - staged) }
    }

    suspend fun saveEditor(): Boolean {
        val state = _editor.value ?: return false
        TaskEditorValidation.errorFor(state)?.let { error ->
            _editor.value = state.copy(error = error)
            return false
        }
        val title = state.title.trim()
        val interval = state.recurrenceInterval.toIntOrNull()

        val now = System.currentTimeMillis()
        val task = (state.sourceTask ?: TaskEntity(
            id = state.taskId,
            title = title,
            createdAt = now,
            updatedAt = now,
        )).copy(
            title = title,
            description = state.description.trim().ifEmpty { null },
            dueAt = state.dueAt,
            dueHasTime = state.dueAt != null && state.hasTime,
            isPriority = state.isPriority,
            recurrenceUnit = state.recurrenceUnit.takeIf { state.recurrenceEnabled },
            recurrenceInterval = if (state.recurrenceEnabled) interval ?: 1 else 1,
            recurrenceWeekdayMask = if (state.recurrenceEnabled && state.recurrenceUnit == RecurrenceUnit.WEEK) state.recurrenceWeekdayMask else 0,
            recurrenceZoneId = if (state.recurrenceEnabled) ZoneId.systemDefault().id else ZoneId.systemDefault().id,
            updatedAt = now,
        )

        val committed = mutableListOf<String>()
        val newAttachments = mutableListOf<AttachmentEntity>()
        try {
            state.stagedAttachments.forEach { stagedState ->
                val staged = stagedState.value
                val path = attachmentStore.commit(staged)
                committed += path
                newAttachments += AttachmentEntity(
                    taskId = task.id,
                    displayName = staged.displayName,
                    mimeType = staged.mimeType,
                    relativePath = path,
                    sizeBytes = staged.sizeBytes,
                    createdAt = now,
                )
            }
            repository.saveTaskWithAttachments(
                TaskSaveRequest(
                    task = task,
                    isNew = state.sourceTask == null,
                    removedAttachments = state.attachments.filter { it.id in state.removedAttachmentIds },
                    newAttachments = newAttachments,
                ),
            )
        } catch (_: Throwable) {
            val restoredStaged = newAttachments.mapNotNull { attachment ->
                val path = attachment.relativePath
                if (path !in committed) return@mapNotNull null
                runCatching {
                    StagedAttachmentState(
                        attachmentStore.restoreCommitted(
                            relativePath = path,
                            taskId = task.id,
                            displayName = attachment.displayName,
                            mimeType = attachment.mimeType,
                            sizeBytes = attachment.sizeBytes,
                        ),
                    )
                }.getOrNull()
            }
            committed.forEach { path ->
                runCatching { attachmentStore.delete(path) }
            }
            state.stagedAttachments.forEach { attachmentStore.discard(it.value) }
            _editor.value = _editor.value?.copy(stagedAttachments = restoredStaged, error = EditorError.SAVE_ERROR)
            return false
        }
        reminderCoordinator.onTaskSaved(task, now)
        state.attachments.filter { it.id in state.removedAttachmentIds }.forEach { attachment ->
            runCatching { attachmentStore.delete(attachment.relativePath) }
        }
        _editor.value = null
        return true
    }

    fun cancelEditor() {
        _editor.value?.stagedAttachments?.forEach { attachmentStore.discard(it.value) }
        _editor.value = null
    }

    suspend fun completeTask(taskId: String): CompletionResult? {
        val result = repository.completeTask(taskId) ?: return null
        reminderCoordinator.onCompletion(result)
        return result
    }

    suspend fun undoCompletion(taskId: String): UndoCompletionResult? {
        val result = repository.undoCompletion(taskId) ?: return null
        reminderCoordinator.onUndoCompletion(result)
        return result
    }

    suspend fun toggleCompleted(task: TaskEntity): Boolean = if (task.completed) {
        undoCompletion(task.id) != null
    } else {
        completeTask(task.id) != null
    }

    suspend fun togglePriority(task: TaskEntity) {
        repository.updateTask(task.copy(isPriority = !task.isPriority, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTask(taskId: String): Boolean {
        val task = repository.findTask(taskId) ?: return false
        val snapshot = DeletedTaskSnapshot(task, repository.findAttachments(taskId))
        if (!repository.deleteTask(taskId)) return false
        reminderCoordinator.onTaskDeleted(taskId)
        deletedTasks[taskId] = snapshot
        return true
    }

    suspend fun undoDelete(taskId: String): Boolean {
        val snapshot = deletedTasks.remove(taskId) ?: return false
        repository.insertTask(snapshot.task)
        repository.insertAttachments(snapshot.attachments)
        reminderCoordinator.onTaskRestored(snapshot.task)
        return true
    }

    fun finalizeDelete(taskId: String) {
        val snapshot = deletedTasks.remove(taskId) ?: return
        attachmentStore.cleanupTask(snapshot.task.id)
    }

    private fun dueLocalDate(millis: Long): LocalDate = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun dueLocalTime(millis: Long?): LocalTime = millis?.let {
        java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
    } ?: LocalTime.MIDNIGHT
}

class TasksViewModelFactory(
    private val repository: TaskRepository,
    private val attachmentStore: AttachmentStore,
    private val reminderCoordinator: ReminderCoordinator,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TasksViewModel::class.java))
        return TasksViewModel(repository, attachmentStore, reminderCoordinator) as T
    }
}
