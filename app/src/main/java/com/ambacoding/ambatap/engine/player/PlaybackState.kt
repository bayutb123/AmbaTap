package com.ambacoding.ambatap.engine.player

data class PlaybackState(
    val status: Status = Status.IDLE,
    /** Id macro yang diputar; 0 untuk macro yang belum disimpan. */
    val macroId: Long? = null,
    val macroName: String? = null,
    /** Loop yang sedang berjalan, mulai dari 1. */
    val loop: Int = 0,
    /** Jumlah loop; `null` untuk mode tak terbatas atau berbasis durasi. */
    val totalLoops: Int? = null,
    /** Indeks aksi yang sedang berjalan, mulai dari 0. */
    val actionIndex: Int = 0,
    val actionCount: Int = 0,
    val countdownMs: Long = 0,
    /** Orientasi layar (potret/lanskap) berbeda dengan saat macro direkam; posisi bisa meleset. */
    val orientationMismatch: Boolean = false,
    /** Alasan pemutaran terakhir gagal dimulai atau berhenti lebih awal; dibersihkan saat play berikutnya. */
    val error: PlaybackError? = null,
    /** Nomor aksi (mulai 1) tempat pemutaran berhenti karena [error]. */
    val errorActionNumber: Int? = null,
) {
    enum class Status { IDLE, COUNTDOWN, PLAYING, PAUSED }

    val isActive: Boolean get() = status != Status.IDLE
}

enum class PlaybackError {
    SERVICE_NOT_CONNECTED,
    EMPTY_MACRO,

    /** Aksi bersyarat tidak terpenuhi sampai batas waktu dan diatur untuk menghentikan macro. */
    CONDITION_TIMEOUT,

    /** Aksi tunggu warna butuh tangkapan layar, yang baru ada di Android 11. */
    SCREEN_CAPTURE_UNSUPPORTED,
}
