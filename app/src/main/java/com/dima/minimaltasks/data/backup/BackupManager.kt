package com.dima.minimaltasks.data.backup

import com.dima.minimaltasks.data.AttachmentStore
import com.dima.minimaltasks.data.TaskRepository
import com.dima.minimaltasks.data.local.AttachmentEntity
import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.data.local.TaskWithAttachments
import com.dima.minimaltasks.data.settings.SettingsRepository
import com.dima.minimaltasks.data.settings.SettingsState
import com.dima.minimaltasks.notifications.ReminderCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val taskCount: Int,
    val attachmentCount: Int,
)

class BackupManager(
    private val repository: TaskRepository,
    private val attachmentStore: AttachmentStore,
    private val settingsRepository: SettingsRepository,
    private val reminderCoordinator: ReminderCoordinator,
) {
    suspend fun export(output: OutputStream): BackupSummary = withContext(Dispatchers.IO) {
        val taskSnapshots = repository.snapshotWithAttachments()
        val settings = settingsRepository.state.first()
        val manifest = BackupFormat.manifestFor(taskSnapshots, settings)
        val manifestBytes = BackupFormat.encode(manifest)
        var totalBytes = 0L
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchivePaths.MANIFEST_ENTRY))
            zip.write(manifestBytes)
            zip.closeEntry()
            manifest.attachments.forEach { metadata ->
                zip.putNextEntry(ZipEntry(metadata.archivePath))
                attachmentStore.open(
                    taskSnapshots.asSequence()
                        .flatMap { it.attachments.asSequence() }
                        .first { it.id == metadata.id }
                        .relativePath,
                ).use { input ->
                    val copied = copyLimited(
                        input = input,
                        output = zip,
                        maxBytes = AttachmentStore.MAX_FILE_BYTES,
                        totalBefore = totalBytes,
                    )
                    if (copied != metadata.sizeBytes) throw BackupValidationException("Attachment size changed")
                    totalBytes += copied
                }
                zip.closeEntry()
            }
        }
        BackupSummary(manifest.tasks.size, manifest.attachments.size)
    }

    suspend fun `import`(input: InputStream): BackupSummary = withContext(Dispatchers.IO) {
        val previous = repository.snapshotWithAttachments()
        val previousTasks = previous.map(TaskWithAttachments::task)
        val previousAttachments = previous.flatMap(TaskWithAttachments::attachments)
        val previousSettings = settingsRepository.state.first()
        importBlocking(input, previousTasks, previousAttachments, previousSettings)
    }

    private suspend fun importBlocking(
        input: InputStream,
        previousTasks: List<TaskEntity>,
        previousAttachments: List<AttachmentEntity>,
        previousSettings: SettingsState,
    ): BackupSummary {
        val staged = linkedMapOf<String, AttachmentStore.StagedAttachment>()
        val committedPaths = mutableListOf<String>()
        var databaseReplaced = false
        var settingsReplaced = false
        try {
            var manifestBytes: ByteArray? = null
            var entryCount = 0
            var totalBytes = 0L
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > BackupFormat.MAX_ARCHIVE_ENTRIES) throw BackupValidationException("Too many ZIP entries")
                    if (entry.isDirectory) throw BackupValidationException("Directories are not allowed in backup")
                    val name = try {
                        BackupArchivePaths.validateEntryName(entry.name)
                    } catch (error: IllegalArgumentException) {
                        throw BackupValidationException("Unsafe ZIP entry path", error)
                    }
                    if (name == BackupArchivePaths.MANIFEST_ENTRY) {
                        if (manifestBytes != null) throw BackupValidationException("Duplicate manifest")
                        manifestBytes = readLimited(zip, BackupFormat.MAX_MANIFEST_BYTES)
                    } else {
                        if (staged.containsKey(name)) throw BackupValidationException("Duplicate attachment entry")
                        if (staged.size >= BackupFormat.MAX_ATTACHMENTS) throw BackupValidationException("Too many attachments")
                        val taskId = BackupArchivePaths.taskId(name)
                        val stagedAttachment = attachmentStore.stage(
                            taskId = taskId,
                            source = zip,
                            displayName = BackupArchivePaths.attachmentId(name),
                            mimeType = AttachmentStore.DEFAULT_MIME_TYPE,
                            existingCount = staged.values.count { it.taskId == taskId },
                        )
                        totalBytes += stagedAttachment.sizeBytes
                        if (totalBytes > BackupFormat.MAX_TOTAL_ATTACHMENT_BYTES) {
                            throw BackupValidationException("Backup is too large")
                        }
                        staged[name] = stagedAttachment
                    }
                    zip.closeEntry()
                }
            }
            val manifest = manifestBytes?.let(BackupFormat::decode)
                ?: throw BackupValidationException("Manifest is missing")
            val expectedPaths = manifest.attachments.mapTo(linkedSetOf(), BackupManifestAttachment::archivePath)
            if (expectedPaths != staged.keys) throw BackupValidationException("Attachment entries do not match manifest")
            manifest.attachments.forEach { metadata ->
                val stagedAttachment = staged.getValue(metadata.archivePath)
                if (stagedAttachment.sizeBytes != metadata.sizeBytes) {
                    throw BackupValidationException("Attachment size does not match manifest")
                }
            }

            val importedAttachments = manifest.attachments.map { metadata ->
                val path = attachmentStore.commit(staged.getValue(metadata.archivePath))
                committedPaths += path
                AttachmentEntity(
                    id = metadata.id,
                    taskId = metadata.taskId,
                    displayName = metadata.displayName,
                    mimeType = metadata.mimeType,
                    relativePath = path,
                    sizeBytes = metadata.sizeBytes,
                    createdAt = metadata.createdAt,
                )
            }

            repository.replaceAll(manifest.tasks, importedAttachments)
            databaseReplaced = true
            settingsRepository.replace(manifest.settings)
            settingsReplaced = true
            reminderCoordinator.reconcile()

            previousAttachments.forEach { attachment ->
                runCatching { attachmentStore.delete(attachment.relativePath) }
            }
            return BackupSummary(manifest.tasks.size, manifest.attachments.size)
        } catch (error: Throwable) {
            if (databaseReplaced) {
                try {
                    repository.replaceAll(previousTasks, previousAttachments)
                } catch (_: Throwable) {
                    // The previous snapshot is known to satisfy the same database constraints.
                }
            }
            if (settingsReplaced) {
                try {
                    settingsRepository.replace(previousSettings)
                } catch (_: Throwable) {
                    // Best-effort rollback of the separate DataStore after a Room rollback.
                }
            }
            if (databaseReplaced || settingsReplaced) {
                try {
                    reminderCoordinator.reconcile()
                } catch (_: Throwable) {
                    // Alarm reconciliation is retried on the next app start/resume.
                }
            }
            committedPaths.forEach { path -> runCatching { attachmentStore.delete(path) } }
            throw error
        } finally {
            staged.values.forEach { stagedAttachment -> runCatching { attachmentStore.discard(stagedAttachment) } }
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024L).toInt())
        copyLimited(input, output, maxBytes, 0L)
        return output.toByteArray()
    }

    private fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long, totalBefore: Long): Long {
        var copied = 0L
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            copied += read
            if (copied > maxBytes || totalBefore > BackupFormat.MAX_TOTAL_ATTACHMENT_BYTES - copied) {
                throw BackupValidationException("Backup entry is too large")
            }
            output.write(buffer, 0, read)
        }
        return copied
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 32 * 1024
    }
}
