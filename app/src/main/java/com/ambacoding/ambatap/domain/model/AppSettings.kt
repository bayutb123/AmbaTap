package com.ambacoding.ambatap.domain.model

data class AppSettings(
    val countdownSeconds: Int = 3,
    val showTouchIndicator: Boolean = true,
    /** Opasitas panel melayang saat idle, 0.2..1. */
    val panelIdleOpacity: Float = 0.6f,
    /** Rekam lewat Shizuku (getevent) tanpa lapisan penangkap, bila Shizuku siap. */
    val precisionRecording: Boolean = false,
)
