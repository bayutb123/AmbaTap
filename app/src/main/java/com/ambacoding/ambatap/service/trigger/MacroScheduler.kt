package com.ambacoding.ambatap.service.trigger

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ambacoding.ambatap.di.ApplicationScope
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.repository.MacroRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Menyamakan alarm sistem dengan jadwal macro di database. Satu alarm per macro, berisi
 * waktu jalan berikutnya; setelah alarm berbunyi, alarm berikutnya dipasang ulang.
 */
@Singleton
class MacroScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MacroRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /** Id macro yang alarmnya dipasang proses ini; alarm sisa proses lama diabaikan penerima. */
    private var scheduled = emptySet<Long>()

    /** Alarm tepat waktu diizinkan; tanpa izin ini alarm bisa mundur beberapa menit. */
    val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun start() {
        scope.launch { repository.observeMacros().collect { sync(it) } }
    }

    suspend fun syncNow() = sync(repository.observeMacros().first())

    private suspend fun sync(macros: List<Macro>) = mutex.withLock {
        val now = ZonedDateTime.now()
        val wanted = macros.mapNotNull { macro ->
            val next = macro.triggers.schedule?.takeIf { it.enabled }?.nextFire(now) ?: return@mapNotNull null
            macro.id to next.toInstant().toEpochMilli()
        }.toMap()
        (scheduled - wanted.keys).forEach { alarmManager.cancel(alarmIntent(it)) }
        wanted.forEach { (id, atMs) -> setAlarm(id, atMs) }
        scheduled = wanted.keys
    }

    private fun setAlarm(macroId: Long, atMs: Long) {
        val intent = alarmIntent(macroId)
        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent)
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, atMs, INEXACT_WINDOW_MS, intent)
            }
        } catch (_: SecurityException) {
            // Izin alarm tepat dicabut di antara pemeriksaan dan pemasangan.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, atMs, INEXACT_WINDOW_MS, intent)
        }
    }

    private fun alarmIntent(macroId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        macroId.toInt(),
        Intent(context, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_FIRE)
            .putExtra(ScheduleAlarmReceiver.EXTRA_MACRO_ID, macroId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val INEXACT_WINDOW_MS = 5 * 60_000L
    }
}
