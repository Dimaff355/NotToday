package com.dima.minimaltasks.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithAttachments(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val attachments: List<AttachmentEntity>,
)
