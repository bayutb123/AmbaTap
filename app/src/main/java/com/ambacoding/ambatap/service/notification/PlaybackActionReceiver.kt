package com.ambacoding.ambatap.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ambacoding.ambatap.engine.player.MacroPlayer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Menerima tombol di notifikasi status. */
@AndroidEntryPoint
class PlaybackActionReceiver : BroadcastReceiver() {

    @Inject lateinit var player: MacroPlayer

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> player.pause()
            ACTION_RESUME -> player.resume()
            ACTION_STOP -> player.stop()
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.ambacoding.ambatap.action.PAUSE"
        const val ACTION_RESUME = "com.ambacoding.ambatap.action.RESUME"
        const val ACTION_STOP = "com.ambacoding.ambatap.action.STOP"
    }
}
