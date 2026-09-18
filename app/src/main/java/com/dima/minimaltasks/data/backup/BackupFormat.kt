package com.dima.minimaltasks.data.backup

import com.dima.minimaltasks.data.AttachmentStore
import com.dima.minimaltasks.data.local.AttachmentEntity
import com.dima.minimaltasks.data.local.RecurrenceUnit
import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.data.local.TaskWithAttachments
import com.dima.minimaltasks.data.settings.SettingsState
import com.dima.minimaltasks.data.settings.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(
    val tasks: List<TaskEntity>,
    val attachments: List<AttachmentEntity>,
    val settings: SettingsState,
)

data class BackupManifestAttachment(
    val id: String,
    val taskId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val archivePath: String,
)

data class BackupManifest(
    val createdAt: Long,
    val tasks: List<TaskEntity>,
    val attachments: List<BackupManifestAttachment>,
    val settings: SettingsState,
)

class BackupValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupArchivePaths {
    const val MANIFEST_ENTRY = "manifest.json"
    private const val ATTACHMENTS_PREFIX = "attachments/"
    private const val MAX_ENTRY_NAME_LENGTH = 512
    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,128}")

    fun attachmentEntry(taskId: String, attachmentId: String): String {
        require(SAFE_ID.matches(taskId)) { "Unsafe task id" }
        require(SAFE_ID.matches(attachmentId)) { "Unsafe attachment id" }
        return "$ATTACHMENTS_PREFIX$taskId/$attachmentId.bin"
    }

    /** Validates a ZIP entry before it is ever used as a filesystem path. */
    fun validateEntryName(name: String): String {
        require(name.length in 1..MAX_ENTRY_NAME_LENGTH) { "Invalid ZIP entry name" }
        require(name == name.trim()) { "Invalid ZIP entry name" }
        require(!name.startsWith('/') && !name.contains('\\')) { "Unsafe ZIP entry path" }
        val parts = name.split('/')
        require(parts.none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe ZIP entry path" }
        if (name == MANIFEST_ENTRY) return name
        require(parts.size == 3 && parts[0] == ATTACHMENTS_PREFIX.removeSuffix("/")) {
            "Unexpected ZIP entry"
        }
        require(SAFE_ID.matches(parts[1])) { "Unsafe task id" }
        require(parts[2].endsWith(".bin") && parts[2].length > 4) { "Invalid attachment entry" }
        require(SAFE_ID.matches(parts[2].removeSuffix(".bin"))) { "Unsafe attachment id" }
        return name
    }

    fun taskId(entryName: String): String = validateEntryName(entryName).split('/')[1]

    fun attachmentId(entryName: String): String =
        validateEntryName(entryName).split('/')[2].removeSuffix(".bin")
}

object BackupFormat {
    const val FORMAT = "minimal_tasks_backup"
    const val VERSION = 1

    const val MAX_TASKS = 10_000
    const val MAX_ATTACHMENTS = 10_000
    const val MAX_TOTAL_ATTACHMENT_BYTES = 200L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES = 10L * 1024L * 1024L
    const val MAX_TEXT_LENGTH = 100_000
    const val MAX_DISPLAY_NAME_LENGTH = 255
    const val MAX_MIME_TYPE_LENGTH = 255
    const val MAX_ARCHIVE_ENTRIES = MAX_TASKS + MAX_ATTACHMENTS + 1
    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,128}")

    fun manifestFor(
        tasksWithAttachments: List<TaskWithAttachments>,
        settings: SettingsState,
        createdAt: Long = System.currentTimeMillis(),
    ): BackupManifest {
        val tasks = tasksWithAttachments.map(TaskWithAttachments::task)
        val attachments = tasksWithAttachments.flatMap { taskWithAttachments ->
            taskWithAttachments.attachments.map { attachment ->
                BackupManifestAttachment(
                    id = attachment.id,
                    taskId = attachment.taskId,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                    sizeBytes = attachment.sizeBytes,
                    createdAt = attachment.createdAt,
                    archivePath = BackupArchivePaths.attachmentEntry(attachment.taskId, attachment.id),
                )
            }
        }
        return validate(BackupManifest(createdAt, tasks, attachments, settings))
    }

    fun snapshotManifest(snapshot: BackupSnapshot, createdAt: Long = System.currentTimeMillis()): BackupManifest {
        val byTask = snapshot.attachments.groupBy(AttachmentEntity::taskId)
        val taskSnapshots = snapshot.tasks.map { task ->
            TaskWithAttachments(task, byTask[task.id].orEmpty())
        }
        return manifestFor(taskSnapshots, snapshot.settings, createdAt)
    }

    fun encode(manifest: BackupManifest): ByteArray {
        val validated = validate(manifest)
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", validated.createdAt)
            .put("tasks", JSONArray().also { array -> validated.tasks.forEach { array.put(taskToJson(it)) } })
            .put("attachments", JSONArray().also { array -> validated.attachments.forEach { array.put(attachmentToJson(it)) } })
            .put("settings", settingsToJson(validated.settings))
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_MANIFEST_BYTES) throw BackupValidationException("Manifest is too large")
        return bytes
    }

    fun decode(bytes: ByteArray): BackupManifest {
        if (bytes.isEmpty() || bytes.size > MAX_MANIFEST_BYTES) {
            throw BackupValidationException("Manifest is missing or too large")
        }
        return try {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            requiredString(root, "format", 64) { it == FORMAT }
            requireInt(root, "version", VERSION)
            val tasksArray = root.optJSONArray("tasks") ?: fail("Missing tasks")
            val attachmentsArray = root.optJSONArray("attachments") ?: fail("Missing attachments")
            val settingsObject = root.optJSONObject("settings") ?: fail("Missing settings")
            if (tasksArray.length() > MAX_TASKS) fail("Too many tasks")
            if (attachmentsArray.length() > MAX_ATTACHMENTS) fail("Too many attachments")

            val tasks = buildList(tasksArray.length()) {
                for (index in 0 until tasksArray.length()) {
                    val objectValue = tasksArray.optJSONObject(index) ?: fail("Invalid task at $index")
                    add(taskFromJson(objectValue))
                }
            }
            val attachments = buildList(attachmentsArray.length()) {
                for (index in 0 until attachmentsArray.length()) {
                    val objectValue = attachmentsArray.optJSONObject(index) ?: fail("Invalid attachment at $index")
                    add(attachmentFromJson(objectValue))
                }
            }
            validate(
                BackupManifest(
                    createdAt = requiredLong(root, "createdAt"),
                    tasks = tasks,
                    attachments = attachments,
                    settings = settingsFromJson(settingsObject),
                ),
            )
        } catch (error: BackupValidationException) {
            throw error
        } catch (error: Throwable) {
            throw BackupValidationException("Invalid backup manifest", error)
        }
    }

    fun validate(manifest: BackupManifest): BackupManifest {
        if (manifest.tasks.size > MAX_TASKS) fail("Too many tasks")
        if (manifest.attachments.size > MAX_ATTACHMENTS) fail("Too many attachments")
        val taskIds = manifest.tasks.map { it.id }
        if (taskIds.size != taskIds.toSet().size || taskIds.any { !safeId(it) }) fail("Invalid or duplicate task id")
        if (manifest.tasks.any { it.title.length > MAX_TEXT_LENGTH || (it.description?.length ?: 0) > MAX_TEXT_LENGTH }) {
            fail("Task text is too long")
        }
        if (manifest.tasks.any { it.dueHasTime && it.dueAt == null }) fail("Task time requires a due date")
        val parentIds = manifest.tasks.mapNotNull(TaskEntity::recurrenceParentTaskId)
        if (parentIds.size != parentIds.toSet().size || parentIds.any { it !in taskIds } ||
            manifest.tasks.any { it.recurrenceParentTaskId == it.id }
        ) {
            fail("Invalid recurrence parent")
        }

        val attachmentIds = manifest.attachments.map { it.id }
        if (attachmentIds.size != attachmentIds.toSet().size || attachmentIds.any { !safeId(it) }) {
            fail("Invalid or duplicate attachment id")
        }
        val archivePaths = manifest.attachments.map { it.archivePath }
        if (archivePaths.size != archivePaths.toSet().size) fail("Duplicate attachment path")
        val attachmentCountByTask = manifest.attachments.groupingBy(BackupManifestAttachment::taskId).eachCount()
        if (attachmentCountByTask.values.any { it > AttachmentStore.MAX_ATTACHMENTS_PER_TASK }) {
            fail("Too many attachments for a task")
        }
        var totalBytes = 0L
        manifest.attachments.forEach { attachment ->
            if (attachment.taskId !in taskIds || !safeId(attachment.taskId)) fail("Attachment references unknown task")
            if (attachment.displayName.isBlank() || attachment.displayName.length > MAX_DISPLAY_NAME_LENGTH) {
                fail("Invalid attachment name")
            }
            if (attachment.mimeType.isBlank() || attachment.mimeType.length > MAX_MIME_TYPE_LENGTH) {
                fail("Invalid attachment MIME type")
            }
            if (attachment.sizeBytes !in 0L..AttachmentStore.MAX_FILE_BYTES) {
                fail("Invalid attachment size")
            }
            totalBytes = safeAdd(totalBytes, attachment.sizeBytes)
            val expectedPath = BackupArchivePaths.attachmentEntry(attachment.taskId, attachment.id)
            if (attachment.archivePath != expectedPath) fail("Invalid attachment archive path")
        }
        if (totalBytes > MAX_TOTAL_ATTACHMENT_BYTES) fail("Backup is too large")
        return manifest
    }

    private fun taskToJson(task: TaskEntity): JSONObject = JSONObject()
        .put("id", task.id)
        .put("title", task.title)
        .put("description", task.description ?: JSONObject.NULL)
        .put("dueAt", task.dueAt ?: JSONObject.NULL)
        .put("dueHasTime", task.dueHasTime)
        .put("completed", task.completed)
        .put("completedAt", task.completedAt ?: JSONObject.NULL)
        .put("isPriority", task.isPriority)
        .put("createdAt", task.createdAt)
        .put("updatedAt", task.updatedAt)
        .put("recurrenceUnit", task.recurrenceUnit?.name ?: JSONObject.NULL)
        .put("recurrenceInterval", task.recurrenceInterval)
        .put("recurrenceWeekdayMask", task.recurrenceWeekdayMask)
        .put("recurrenceZoneId", task.recurrenceZoneId)
        .put("recurrenceParentTaskId", task.recurrenceParentTaskId ?: JSONObject.NULL)

    private fun taskFromJson(value: JSONObject): TaskEntity = try {
        TaskEntity(
            id = requiredString(value, "id", 128) { safeId(it) },
            title = requiredString(value, "title", MAX_TEXT_LENGTH) { it.isNotBlank() },
            description = nullableString(value, "description", MAX_TEXT_LENGTH),
            dueAt = nullableLong(value, "dueAt"),
            dueHasTime = requiredBoolean(value, "dueHasTime"),
            completed = requiredBoolean(value, "completed"),
            completedAt = nullableLong(value, "completedAt"),
            isPriority = requiredBoolean(value, "isPriority"),
            createdAt = requiredLong(value, "createdAt"),
            updatedAt = requiredLong(value, "updatedAt"),
            recurrenceUnit = nullableString(value, "recurrenceUnit", 16)?.let {
                runCatching { RecurrenceUnit.valueOf(it) }.getOrElse { fail("Invalid recurrence unit") }
            },
            recurrenceInterval = requiredInt(value, "recurrenceInterval"),
            recurrenceWeekdayMask = requiredInt(value, "recurrenceWeekdayMask"),
            recurrenceZoneId = requiredString(value, "recurrenceZoneId", 128),
            recurrenceParentTaskId = nullableString(value, "recurrenceParentTaskId", 128),
        )
    } catch (error: BackupValidationException) {
        throw error
    } catch (error: Throwable) {
        throw BackupValidationException("Invalid task", error)
    }

    private fun attachmentToJson(attachment: BackupManifestAttachment): JSONObject = JSONObject()
        .put("id", attachment.id)
        .put("taskId", attachment.taskId)
        .put("displayName", attachment.displayName)
        .put("mimeType", attachment.mimeType)
        .put("sizeBytes", attachment.sizeBytes)
        .put("createdAt", attachment.createdAt)
        .put("archivePath", attachment.archivePath)

    private fun attachmentFromJson(value: JSONObject): BackupManifestAttachment = BackupManifestAttachment(
        id = requiredString(value, "id", 128) { safeId(it) },
        taskId = requiredString(value, "taskId", 128) { safeId(it) },
        displayName = requiredString(value, "displayName", MAX_DISPLAY_NAME_LENGTH) { it.isNotBlank() },
        mimeType = requiredString(value, "mimeType", MAX_MIME_TYPE_LENGTH) { it.isNotBlank() },
        sizeBytes = requiredLong(value, "sizeBytes"),
        createdAt = requiredLong(value, "createdAt"),
        archivePath = requiredString(value, "archivePath", 512) {
            runCatching { BackupArchivePaths.validateEntryName(it) }.isSuccess
        },
    )

    private fun settingsToJson(settings: SettingsState): JSONObject = JSONObject()
        .put("themeMode", settings.themeMode.name)
        .put("completionSoundEnabled", settings.completionSoundEnabled)
        .put("vibrationEnabled", settings.vibrationEnabled)
        .put("notificationsEnabled", settings.notificationsEnabled)
        .put("notificationPermissionAsked", settings.notificationPermissionAsked)

    private fun settingsFromJson(value: JSONObject): SettingsState {
        val themeName = requiredString(value, "themeMode", 16)
        val themeMode = runCatching { ThemeMode.valueOf(themeName) }.getOrElse { fail("Invalid theme mode") }
        return SettingsState(
            themeMode = themeMode,
            completionSoundEnabled = requiredBoolean(value, "completionSoundEnabled"),
            vibrationEnabled = requiredBoolean(value, "vibrationEnabled"),
            notificationsEnabled = requiredBoolean(value, "notificationsEnabled"),
            notificationPermissionAsked = requiredBoolean(value, "notificationPermissionAsked"),
        )
    }

    private fun safeId(value: String): Boolean = SAFE_ID.matches(value)

    private fun requiredString(value: JSONObject, name: String, maxLength: Int, predicate: (String) -> Boolean = { true }): String {
        val raw = value.opt(name)
        if (raw !is String || raw.length > maxLength || !predicate(raw)) fail("Invalid $name")
        return raw
    }

    private fun nullableString(value: JSONObject, name: String, maxLength: Int): String? {
        if (!value.has(name) || value.isNull(name)) return null
        return requiredString(value, name, maxLength)
    }

    private fun requiredBoolean(value: JSONObject, name: String): Boolean =
        (value.opt(name) as? Boolean) ?: fail("Invalid $name")

    private fun requiredLong(value: JSONObject, name: String): Long {
        val raw = value.opt(name)
        if (raw !is Number) fail("Invalid $name")
        val result = raw.toLong()
        if (raw.toDouble() != result.toDouble()) fail("Invalid $name")
        return result
    }

    private fun nullableLong(value: JSONObject, name: String): Long? =
        if (!value.has(name) || value.isNull(name)) null else requiredLong(value, name)

    private fun requiredInt(value: JSONObject, name: String): Int {
        val result = requiredLong(value, name)
        if (result !in Int.MIN_VALUE..Int.MAX_VALUE) fail("Invalid $name")
        return result.toInt()
    }

    private fun requireInt(value: JSONObject, name: String, expected: Int) {
        if (requiredLong(value, name) != expected.toLong()) fail("Unsupported $name")
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (right > Long.MAX_VALUE - left) fail("Backup is too large")
        return left + right
    }

    private fun fail(message: String): Nothing = throw BackupValidationException(message)
}
