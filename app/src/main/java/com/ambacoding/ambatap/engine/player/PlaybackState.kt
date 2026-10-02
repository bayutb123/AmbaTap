package com.ambacoding.ambatap.engine.player

data class PlaybackState(
    val status: Status = Status.IDLE,
    val macroName: String? = null,
    /** Loop yang sedang berjalan, mulai dari 1. */
    val loop: Int = 0,
    /** Jumlah loop; `null` untuk mode tak terbatas atau berbasis durasi. */
    val totalLoops: Int? = null,
    /** Indeks aksi yang sedang berjalan, mulai dari 0. */
    val actionIndex: Int = 0,
    val actionCount: Int = 0,
    val countdownMs: Long = 0,
    /** Alasan pemutaran terakhir gagal dimulai; dibersihkan saat play berikutnya. */
    val error: PlaybackError? = null,
) {
    enum class Status { IDLE, COUNTDOWN, PLAYING, PAUSED }

    val isActive: Boolean get() = status != Status.IDLE
}

enum class PlaybackError { SERVICE_NOT_CONNECTED, EMPTY_MACRO }
