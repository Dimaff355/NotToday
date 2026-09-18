package com.dima.minimaltasks.data.local

import androidx.room.TypeConverter

class RoomConverters {
    @TypeConverter
    fun recurrenceUnitToStorage(value: RecurrenceUnit?): String? = value?.name

    @TypeConverter
    fun recurrenceUnitFromStorage(value: String?): RecurrenceUnit? =
        value?.let { runCatching { RecurrenceUnit.valueOf(it) }.getOrNull() }
}
