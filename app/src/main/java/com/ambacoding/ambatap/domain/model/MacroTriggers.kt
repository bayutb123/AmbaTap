package com.ambacoding.ambatap.domain.model

import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable

/** Cara macro dijalankan otomatis, selain diputar manual. */
@Serializable
data class MacroTriggers(
    val schedule: Schedule? = null,
    val appOpen: AppOpenTrigger? = null,
) {
    val hasActive: Boolean
        get() = schedule?.enabled == true || appOpen?.isActive == true
}

/** Jadwal harian pada jam lokal perangkat. */
@Serializable
data class Schedule(
    val enabled: Boolean = true,
    val hour: Int,
    val minute: Int,
    /** Hari ISO (1 = Senin … 7 = Minggu). Kosong berarti sekali saja, lalu jadwal dimatikan. */
    val days: Set<Int> = emptySet(),
) {
    val isOneShot: Boolean get() = days.isEmpty()

    /**
     * Waktu jalan berikutnya yang lebih lambat dari [after], di zona waktu [after].
     * Jam yang tidak ada karena pergantian DST digeser maju oleh `ZonedDateTime`.
     */
    fun nextFire(after: ZonedDateTime): ZonedDateTime? {
        val validDays = days.filter { it in 1..7 }.toSet()
        if (days.isNotEmpty() && validDays.isEmpty()) return null
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        // 8 hari cukup: hari ini (bila jamnya belum lewat) ditambah satu minggu penuh.
        for (offset in 0L..7L) {
            val date = after.toLocalDate().plusDays(offset)
            val candidate = after.with(date).withHour(h).withMinute(m).withSecond(0).withNano(0)
            if (!candidate.isAfter(after)) continue
            if (validDays.isEmpty() || candidate.dayOfWeek.value in validDays) return candidate
        }
        return null
    }

    companion object {
        val Weekdays: Set<Int> = (DayOfWeek.MONDAY.value..DayOfWeek.FRIDAY.value).toSet()
        val EveryDay: Set<Int> = (1..7).toSet()
    }
}

/** Memutar macro saat aplikasi [packageName] masuk ke depan layar. */
@Serializable
data class AppOpenTrigger(
    val enabled: Boolean = true,
    val packageName: String = "",
) {
    val isActive: Boolean get() = enabled && packageName.isNotBlank()
}
