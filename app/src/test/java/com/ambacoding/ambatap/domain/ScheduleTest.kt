package com.ambacoding.ambatap.domain

import com.ambacoding.ambatap.domain.model.Schedule
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTest {

    private val zone = ZoneId.of("Asia/Jakarta")

    /** 15 Oktober 2026 adalah hari Kamis. */
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, minute, second, 0, zone)

    @Test
    fun `one shot fires later today or tomorrow`() {
        val schedule = Schedule(hour = 8, minute = 30)
        assertEquals(at(15, 8, 30), schedule.nextFire(at(15, 7, 0)))
        assertEquals(at(16, 8, 30), schedule.nextFire(at(15, 9, 0)))
    }

    @Test
    fun `exact fire time moves to the next occurrence`() {
        val schedule = Schedule(hour = 8, minute = 30)
        assertEquals(at(16, 8, 30), schedule.nextFire(at(15, 8, 30)))
        assertEquals(at(15, 8, 30), schedule.nextFire(at(15, 8, 29, 59)))
    }

    @Test
    fun `repeating schedule skips days that are not selected`() {
        val weekdays = Schedule(hour = 6, minute = 0, days = Schedule.Weekdays)
        // Jumat 16 Okt pukul 07:00 → Senin 19 Okt.
        assertEquals(at(19, 6, 0), weekdays.nextFire(at(16, 7, 0)))
        // Kamis pagi sebelum jamnya → hari ini.
        assertEquals(at(15, 6, 0), weekdays.nextFire(at(15, 5, 0)))
    }

    @Test
    fun `a single weekday waits a full week once passed`() {
        val thursday = Schedule(hour = 20, minute = 0, days = setOf(4))
        assertEquals(at(22, 20, 0), thursday.nextFire(at(15, 21, 0)))
    }

    @Test
    fun `out of range values are clamped or rejected`() {
        assertEquals(at(15, 23, 59), Schedule(hour = 30, minute = 99).nextFire(at(15, 12, 0)))
        assertNull(Schedule(hour = 8, minute = 0, days = setOf(0, 9)).nextFire(at(15, 7, 0)))
    }

    @Test
    fun `nonexistent local time during a DST gap is shifted forward`() {
        val newYork = ZoneId.of("America/New_York")
        // 8 Maret 2026, jam 02:00–03:00 tidak ada.
        val before = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, newYork)
        val next = Schedule(hour = 2, minute = 30).nextFire(before)
        assertEquals(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, newYork), next)
    }
}
