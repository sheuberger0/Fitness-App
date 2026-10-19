package com.sheuberger.fitness.data

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * SQLite kennt keinen Datumstyp. Room nutzt diese Funktionen, um ein [LocalDate]
 * als Zahl (Tage seit dem 1.1.1970) zu speichern und wieder zurückzuwandeln.
 * Dadurch lässt sich in SQL auch ganz normal nach Datum sortieren.
 */
class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}
