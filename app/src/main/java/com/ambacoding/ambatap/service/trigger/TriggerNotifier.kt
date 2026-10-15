package com.ambacoding.ambatap.service.trigger

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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Memberi tahu saat macro terjadwal tidak bisa dijalankan. */
@Singleton
class TriggerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Jadwal macro",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Tampil bila macro terjadwal batal dijalankan" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifySkipped(macroId: Long, macroName: String, reason: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jadwal \"$macroName\" dilewati")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        // Satu notifikasi per macro; id 1 dipakai notifikasi status pemutaran.
        manager.notify(NOTIFICATION_TAG, macroId.toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "triggers"
        const val NOTIFICATION_TAG = "trigger"
    }
}
