package com.ambacoding.ambatap.engine.player

import com.ambacoding.ambatap.domain.model.GlobalType

/** Kemampuan menyuntikkan input ke sistem; diimplementasikan oleh accessibility service. */
interface InputController {
    val screenSize: ScreenSize

    /** Menjalankan gesture dan menunggu hingga selesai. `false` bila dibatalkan sistem. */
    suspend fun dispatch(gesture: GestureSpec): Boolean

    fun performGlobal(type: GlobalType): Boolean

    fun launchApp(packageName: String): Boolean

    /** Mengisi teks pada field yang sedang fokus. */
    fun inputText(text: String): Boolean
}
