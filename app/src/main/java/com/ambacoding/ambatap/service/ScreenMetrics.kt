package com.ambacoding.ambatap.service

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.ambacoding.ambatap.engine.player.ScreenSize

/** Ukuran layar fisik penuh (termasuk status & navigation bar) pada orientasi saat ini. */
fun Context.realScreenSize(): ScreenSize {
    val windowManager = getSystemService(WindowManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.maximumWindowMetrics.bounds
        ScreenSize(bounds.width(), bounds.height())
    } else {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        ScreenSize(metrics.widthPixels, metrics.heightPixels)
    }
}
