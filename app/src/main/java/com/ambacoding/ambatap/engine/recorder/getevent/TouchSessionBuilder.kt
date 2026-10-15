package com.ambacoding.ambatap.engine.recorder.getevent

import com.ambacoding.ambatap.engine.player.PxPoint
import com.ambacoding.ambatap.engine.recorder.TouchSample

/**
 * Mengumpulkan frame menjadi satu sentuhan utuh: dari jari pertama turun sampai semua
 * jari diangkat. Hasilnya satu daftar sampel per jari (urut saat turun), dalam piksel layar.
 */
class TouchSessionBuilder(private val toScreen: (Contact) -> PxPoint) {

    private val fingers = linkedMapOf<Int, MutableList<TouchSample>>()
    private var lastPositions = mapOf<Int, PxPoint>()

    val isTouching: Boolean get() = lastPositions.isNotEmpty()

    /** Posisi jari yang sedang menyentuh, untuk jejak langsung di layar. */
    val activeTrails: Map<Int, List<TouchSample>>
        get() = fingers.filterKeys { it in lastPositions }

    /** @return sampel semua jari saat sentuhan selesai, selain itu `null`. */
    fun onFrame(frame: TouchFrame): List<List<TouchSample>>? {
        val positions = frame.contacts.associate { it.id to toScreen(it) }
        // Jari yang hilang dicatat terangkat di posisi terakhirnya pada waktu frame ini.
        for ((id, last) in lastPositions) {
            if (id !in positions) fingers[id]?.add(TouchSample(last.x, last.y, frame.timeMs))
        }
        for ((id, p) in positions) {
            fingers.getOrPut(id) { mutableListOf() }.add(TouchSample(p.x, p.y, frame.timeMs))
        }
        lastPositions = positions
        if (positions.isNotEmpty() || fingers.isEmpty()) return null
        val done = fingers.values.map { it.toList() }
        fingers.clear()
        return done
    }

    fun reset() {
        fingers.clear()
        lastPositions = emptyMap()
    }
}
