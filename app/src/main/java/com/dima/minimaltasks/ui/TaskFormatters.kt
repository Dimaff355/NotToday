package com.dima.minimaltasks.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DuePresentation(val text: String, val overdue: Boolean)

object TaskFormatters {
    fun duePresentation(
        dueAt: Long?,
        dueHasTime: Boolean,
        nowMillis: Long,
        locale: Locale,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DuePresentation? {
        dueAt ?: return null
        val due = Instant.ofEpochMilli(dueAt).atZone(zoneId)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val pattern = when {
            !dueHasTime -> "d MMMM"
            due.toLocalDate() == now.toLocalDate() -> "HH:mm"
            else -> "EEE, d MMM · HH:mm"
        }
        return DuePresentation(
            text = due.format(DateTimeFormatter.ofPattern(pattern, locale)),
            overdue = if (dueHasTime) {
                !due.toInstant().isAfter(Instant.ofEpochMilli(nowMillis))
            } else {
                due.toLocalDate().isBefore(now.toLocalDate())
            },
        )
    }

    fun datePickerMillis(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()

}
