package com.ambacoding.ambatap.engine.player

import com.ambacoding.ambatap.domain.model.GlobalType
import com.ambacoding.ambatap.domain.model.TextMatch

/** Kemampuan menyuntikkan input ke sistem; diimplementasikan oleh accessibility service. */
interface InputController {
    val screenSize: ScreenSize

    /** Menjalankan gesture dan menunggu hingga selesai. `false` bila dibatalkan sistem. */
    suspend fun dispatch(gesture: GestureSpec): Boolean

    fun performGlobal(type: GlobalType): Boolean

    fun launchApp(packageName: String): Boolean

    /** Mengisi teks pada field yang sedang fokus. */
    fun inputText(text: String): Boolean

    /** Pusat elemen terlihat pertama yang teks/deskripsinya cocok, dalam piksel; `null` bila tidak ada. */
    fun findText(query: String, match: TextMatch): PxPoint?

    /** Bisa membaca warna piksel layar (butuh Android 11+). */
    val canReadScreen: Boolean

    /** Warna ARGB piksel layar saat ini; `null` bila gagal atau tidak didukung. */
    suspend fun pixelColor(x: Int, y: Int): Int?
}
