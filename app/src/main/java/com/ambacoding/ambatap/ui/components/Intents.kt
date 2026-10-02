package com.ambacoding.ambatap.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** Membuka layar lain tanpa crash bila tidak ada aplikasi yang bisa menanganinya (umum di ROM OEM). */
fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
