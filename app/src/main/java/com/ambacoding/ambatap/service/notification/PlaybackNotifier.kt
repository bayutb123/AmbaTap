package com.ambacoding.ambatap.service.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ambacoding.ambatap.MainActivity
import com.ambacoding.ambatap.R
import com.ambacoding.ambatap.engine.player.PlaybackState
import com.ambacoding.ambatap.engine.player.PlaybackState.Status
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notifikasi status pemutaran dengan tombol Jeda/Lanjut dan Berhenti. */
@Singleton
class PlaybackNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Status macro",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Tampil selama macro berjalan, dengan tombol untuk menghentikannya"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun update(state: PlaybackState) {
        if (!state.isActive) {
            cancel()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(NOTIFICATION_ID, build(state))
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun build(state: PlaybackState) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(
            when (state.status) {
                Status.COUNTDOWN -> "Mulai dalam ${(state.countdownMs + 999) / 1000} dtk"
                Status.PAUSED -> "Dijeda · ${state.macroName}"
                else -> "Memutar · ${state.macroName}"
            },
        )
        .setContentText(
            if (state.status == Status.COUNTDOWN) {
                state.macroName
            } else {
                // Sengaja tanpa nomor aksi: update per aksi bisa melewati batas laju notifikasi sistem.
                "Loop ${state.loop} / ${state.totalLoops ?: "∞"} · ${state.actionCount} aksi"
            },
        )
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .apply {
            when (state.status) {
                Status.PLAYING -> addAction(0, "Jeda", actionIntent(PlaybackActionReceiver.ACTION_PAUSE))
                Status.PAUSED -> addAction(0, "Lanjut", actionIntent(PlaybackActionReceiver.ACTION_RESUME))
                else -> Unit
            }
        }
        .addAction(0, "Berhenti", actionIntent(PlaybackActionReceiver.ACTION_STOP))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .build()

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, PlaybackActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1
    }
}
