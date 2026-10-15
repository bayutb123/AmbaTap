package com.ambacoding.ambatap.engine.recorder.getevent

import com.ambacoding.ambatap.engine.player.PxPoint
import com.ambacoding.ambatap.engine.player.ScreenSize

/**
 * Mengubah koordinat mentah layar sentuh ke piksel layar pada rotasi saat ini, mengikuti
 * cara `TouchInputMapper` Android: skala ke ukuran layar alami, lalu diputar.
 */
class RawTouchMapper(private val device: TouchDevice) {

    /**
     * @param screen ukuran layar saat ini (sudah mengikuti rotasi).
     * @param rotation salah satu `Surface.ROTATION_*` (0..3).
     */
    fun map(rawX: Int, rawY: Int, screen: ScreenSize, rotation: Int): PxPoint {
        val sideways = rotation == ROTATION_90 || rotation == ROTATION_270
        val naturalW = if (sideways) screen.heightPx else screen.widthPx
        val naturalH = if (sideways) screen.widthPx else screen.heightPx
        val x = (rawX - device.minX).toFloat() * naturalW / (device.maxX - device.minX + 1)
        val y = (rawY - device.minY).toFloat() * naturalH / (device.maxY - device.minY + 1)
        val (sx, sy) = when (rotation) {
            ROTATION_90 -> y to naturalW - x
            ROTATION_180 -> naturalW - x to naturalH - y
            ROTATION_270 -> naturalH - y to x
            else -> x to y
        }
        return PxPoint(
            sx.coerceIn(0f, (screen.widthPx - 1).toFloat()),
            sy.coerceIn(0f, (screen.heightPx - 1).toFloat()),
        )
    }

    private companion object {
        const val ROTATION_90 = 1
        const val ROTATION_180 = 2
        const val ROTATION_270 = 3
    }
}
