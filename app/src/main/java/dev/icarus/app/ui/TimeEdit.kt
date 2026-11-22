package dev.icarus.app.ui

import java.time.LocalTime

/** Pure time arithmetic shared by the editable time view and its tests. */
object TimeEdit {

    fun shift(time: LocalTime, minutes: Int): LocalTime? {
        val total = time.hour * 60 + time.minute + minutes
        if (total !in 0..1439) return null
        return LocalTime.of(total / 60, total % 60)
    }

    fun validate(start: LocalTime, end: LocalTime): String? =
        if (!end.isAfter(start)) "End must be after start" else null

    fun durationLabel(start: LocalTime, end: LocalTime): String {
        val minutes = end.hour * 60 + end.minute - (start.hour * 60 + start.minute)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}
