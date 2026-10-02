package com.bayutb123.ambatap.service

/** Mendeteksi tombol volume turun ditekan dua kali dalam [windowMs]. */
class EmergencyStopDetector(private val windowMs: Long = 600) {
    private var lastPressMs: Long? = null

    /** @return `true` bila tekanan ini melengkapi tekan-dua-kali. */
    fun onPress(timeMs: Long): Boolean {
        val last = lastPressMs
        return if (last != null && timeMs - last <= windowMs) {
            lastPressMs = null
            true
        } else {
            lastPressMs = timeMs
            false
        }
    }
}
