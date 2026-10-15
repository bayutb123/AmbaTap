package com.ambacoding.ambatap.service.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ambacoding.ambatap.di.ApplicationScope
import com.ambacoding.ambatap.domain.repository.MacroRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Alarm jadwal berbunyi: putar macronya lalu pasang alarm berikutnya. Tidak diekspor. */
@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: MacroRepository
    @Inject lateinit var launcher: TriggerLauncher
    @Inject lateinit var scheduler: MacroScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val macroId = intent.getLongExtra(EXTRA_MACRO_ID, 0)
        val pending = goAsync()
        scope.launch {
            try {
                val macro = repository.getMacro(macroId)
                val schedule = macro?.triggers?.schedule
                if (macro != null && schedule != null && schedule.enabled) {
                    if (schedule.isOneShot) {
                        // Jadwal sekali jalan dimatikan; penyimpanan ini juga memicu sinkronisasi alarm.
                        repository.save(macro.copy(triggers = macro.triggers.copy(schedule = schedule.copy(enabled = false))))
                    }
                    launcher.launch(macro, notifyOnSkip = true)
                }
                scheduler.syncNow()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.ambacoding.ambatap.action.SCHEDULE_FIRE"
        const val EXTRA_MACRO_ID = "macro_id"
    }
}

/**
 * Alarm hilang saat perangkat dinyalakan ulang atau aplikasi diperbarui, dan perlu dihitung
 * ulang saat jam/zona waktu atau izin alarm tepat berubah. Hanya menerima siaran sistem.
 */
@AndroidEntryPoint
class ScheduleRestoreReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: MacroScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RestoreActions) return
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.syncNow()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val RestoreActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
