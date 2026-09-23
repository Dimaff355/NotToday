package com.dima.minimaltasks

import com.dima.minimaltasks.data.backup.BackupArchivePaths
import com.dima.minimaltasks.data.backup.BackupFormat
import com.dima.minimaltasks.data.backup.BackupManager
import com.dima.minimaltasks.data.backup.BackupManifest
import com.dima.minimaltasks.data.backup.BackupManifestAttachment
import com.dima.minimaltasks.data.backup.BackupValidationException
import com.dima.minimaltasks.data.local.AttachmentEntity
import com.dima.minimaltasks.data.local.RecurrenceUnit
import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.data.local.TaskWithAttachments
import com.dima.minimaltasks.data.settings.SettingsState
import com.dima.minimaltasks.data.settings.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    @Test
    fun backupImportApiIsCallableDespiteKeywordName() {
        assertNotNull(BackupManager::`import`)
    }

    @Test
    fun manifestRoundTripsTasksRecurrenceAttachmentsAndSettings() {
        val task = TaskEntity(
            id = "task_1",
            title = "Prepare release",
            description = "Check the backup flow",
            dueAt = 1_700_000_000_000,
            dueHasTime = true,
            completed = false,
            isPriority = true,
            createdAt = 1_699_000_000_000,
            updatedAt = 1_699_500_000_000,
            recurrenceUnit = RecurrenceUnit.WEEK,
            recurrenceInterval = 2,
            recurrenceWeekdayMask = 0b0000010,
            recurrenceZoneId = "UTC",
        )
        val attachment = AttachmentEntity(
            id = "attachment_1",
            taskId = task.id,
            displayName = "notes.txt",
            mimeType = "text/plain",
            relativePath = "task_1/data.bin",
            sizeBytes = 5,
            createdAt = 1_699_000_000_000,
        )
        val settings = SettingsState(
            themeMode = ThemeMode.DARK,
            completionSoundEnabled = false,
            vibrationEnabled = true,
            notificationsEnabled = false,
            notificationPermissionAsked = true,
            dayBeforeEnabled = false,
            dayBeforeMinuteOfDay = 18 * 60 + 30,
        )

        val manifest = BackupFormat.manifestFor(
            listOf(TaskWithAttachments(task, listOf(attachment))),
            settings,
            createdAt = 123,
        )
        val restored = BackupFormat.decode(BackupFormat.encode(manifest))

        assertEquals(manifest, restored)
        assertEquals("attachments/task_1/attachment_1.bin", restored.attachments.single().archivePath)
    }

    @Test
    fun manifestValidationRejectsDuplicateTaskIds() {
        val task = TaskEntity(id = "same", title = "Task", createdAt = 1, updatedAt = 1)
        val invalid = BackupManifest(
            createdAt = 1,
            tasks = listOf(task, task.copy(title = "Another")),
            attachments = emptyList(),
            settings = SettingsState(),
        )

        assertThrows(BackupValidationException::class.java) { BackupFormat.validate(invalid) }
    }

    @Test
    fun manifestValidationRejectsAttachmentForUnknownTask() {
        val invalid = BackupManifest(
            createdAt = 1,
            tasks = listOf(TaskEntity(id = "task", title = "Task", createdAt = 1, updatedAt = 1)),
            attachments = listOf(
                BackupManifestAttachment(
                    id = "attachment",
                    taskId = "missing",
                    displayName = "file.txt",
                    mimeType = "text/plain",
                    sizeBytes = 0,
                    createdAt = 1,
                    archivePath = "attachments/missing/attachment.bin",
                ),
            ),
            settings = SettingsState(),
        )

        assertThrows(BackupValidationException::class.java) { BackupFormat.validate(invalid) }
    }

    @Test
    fun manifestValidationRejectsAnOccurrenceWhoseParentWasDeleted() {
        val orphan = TaskEntity(
            id = "orphan",
            title = "Task",
            createdAt = 1,
            updatedAt = 1,
            recurrenceParentTaskId = "missing",
        )
        val invalid = BackupManifest(
            createdAt = 1,
            tasks = listOf(orphan),
            attachments = emptyList(),
            settings = SettingsState(),
        )

        assertThrows(BackupValidationException::class.java) { BackupFormat.validate(invalid) }
    }

    @Test
    fun zipSlipAndUnexpectedPathsAreRejected() {
        listOf(
            "../escape.bin",
            "/absolute.bin",
            "attachments/task/../escape.bin",
            "attachments/task\\escape.bin",
            "attachments/task/attachment.bin/extra",
            "other/file.bin",
        ).forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                BackupArchivePaths.validateEntryName(path)
            }
        }
    }

    @Test
    fun malformedOrEmptyManifestIsRejected() {
        assertThrows(BackupValidationException::class.java) { BackupFormat.decode(ByteArray(0)) }
        assertThrows(BackupValidationException::class.java) {
            BackupFormat.decode("{\"format\":\"minimal_tasks_backup\",\"version\":99}".toByteArray())
        }
    }

    @Test
    fun olderManifestsWithoutDayBeforeFieldsRestoreItsDefaults() {
        val settings = SettingsState(dayBeforeEnabled = false, dayBeforeMinuteOfDay = 8 * 60)
        val encoded = JSONObject(BackupFormat.encode(BackupFormat.manifestFor(emptyList(), settings, createdAt = 5)).toString(Charsets.UTF_8))
        encoded.getJSONObject("settings").apply {
            remove("dayBeforeEnabled")
            remove("dayBeforeMinuteOfDay")
        }

        val restored = BackupFormat.decode(encoded.toString().toByteArray())

        assertTrue(restored.settings.dayBeforeEnabled)
        assertEquals(SettingsState.DEFAULT_DAY_BEFORE_MINUTE_OF_DAY, restored.settings.dayBeforeMinuteOfDay)
    }

    @Test
    fun outOfRangeDayBeforeMinuteIsRejected() {
        val invalid = BackupManifest(
            createdAt = 1,
            tasks = emptyList(),
            attachments = emptyList(),
            settings = SettingsState(dayBeforeMinuteOfDay = 24 * 60),
        )

        assertThrows(BackupValidationException::class.java) { BackupFormat.validate(invalid) }
    }
}
