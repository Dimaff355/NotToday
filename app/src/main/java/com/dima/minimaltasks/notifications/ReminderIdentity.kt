package com.dima.minimaltasks.notifications

import java.nio.ByteBuffer
import java.security.MessageDigest

/** Stable IDs for system-owned objects. Namespaces prevent action collisions. */
object ReminderIdentity {
    fun alarmRequestCode(taskId: String): Int = stableInt("alarm:$taskId")

    fun notificationId(taskId: String): Int = stableInt("notification:$taskId")

    fun contentRequestCode(taskId: String): Int = stableInt("content:$taskId")

    fun completeRequestCode(taskId: String): Int = stableInt("complete:$taskId")

    fun snoozeRequestCode(taskId: String): Int = stableInt("snooze:$taskId")

    private fun stableInt(value: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val raw = ByteBuffer.wrap(digest).int and Int.MAX_VALUE
        return if (raw == 0) 1 else raw
    }
}
