package com.ambacoding.ambatap.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ambacoding.ambatap.MainActivity
import com.ambacoding.ambatap.service.overlay.FloatingPanelState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Tile Quick Settings: tampilkan/sembunyikan panel melayang; buka aplikasi bila layanan mati. */
@AndroidEntryPoint
class PanelTileService : TileService() {

    @Inject lateinit var bridge: ServiceBridge
    @Inject lateinit var panelState: FloatingPanelState

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (bridge.controller.value == null) {
            openApp()
            return
        }
        if (panelState.requested.value) panelState.hide() else panelState.show()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val connected = bridge.controller.value != null
        tile.state = when {
            !connected -> Tile.STATE_UNAVAILABLE
            panelState.requested.value -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (connected) null else "Layanan belum aktif"
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // Varian Intent hanya dipakai di bawah API 34.
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
