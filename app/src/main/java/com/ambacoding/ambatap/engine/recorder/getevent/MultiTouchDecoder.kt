package com.ambacoding.ambatap.engine.recorder.getevent

/** Satu jari pada satu frame, koordinat mentah device. */
data class Contact(val id: Int, val rawX: Int, val rawY: Int)

/** Keadaan semua jari setelah satu `SYN_REPORT`. Waktu dalam milidetik jam kernel. */
data class TouchFrame(val timeMs: Long, val contacts: List<Contact>)

/**
 * Mengubah baris `getevent -lt <device>` menjadi [TouchFrame] dengan protokol multi-touch B
 * (slot + tracking id), yang dipakai hampir semua layar sentuh Android modern.
 */
class MultiTouchDecoder {

    private class Slot(var trackingId: Int, var x: Int? = null, var y: Int? = null)

    private val slots = sortedMapOf<Int, Slot>()
    private var slot = 0

    // Contoh: "[   73414.582170] /dev/input/event2: EV_ABS       ABS_MT_POSITION_X    000002a3"
    private val line = Regex("""^\[\s*(\d+)\.(\d+)]\s+\S+:\s+(\S+)\s+(\S+)\s+(\S+)""")

    /** @return frame baru saat baris berisi `SYN_REPORT`, selain itu `null`. */
    fun feed(text: String): TouchFrame? {
        val match = line.find(text) ?: return null
        val (sec, frac, type, code, value) = match.destructured
        return when (type) {
            "EV_ABS" -> {
                onAbs(code, value)
                null
            }
            "EV_SYN" -> when (code) {
                "SYN_REPORT" -> TouchFrame(toMs(sec, frac), activeContacts())
                "SYN_DROPPED" -> {
                    // Event hilang karena buffer penuh; keadaan tidak lagi bisa dipercaya.
                    slots.clear()
                    null
                }
                else -> null
            }
            else -> null
        }
    }

    private fun onAbs(code: String, value: String) {
        val v = parseValue(value) ?: return
        when (code) {
            "ABS_MT_SLOT" -> slot = v
            "ABS_MT_TRACKING_ID" -> if (v == -1) slots.remove(slot) else slots[slot] = Slot(v)
            "ABS_MT_POSITION_X" -> current().x = v
            "ABS_MT_POSITION_Y" -> current().y = v
        }
    }

    /** Beberapa driver mengirim posisi sebelum tracking id; slot dibuat dengan id slot. */
    private fun current(): Slot = slots.getOrPut(slot) { Slot(trackingId = slot) }

    private fun activeContacts(): List<Contact> = slots.values.mapNotNull { s ->
        val x = s.x ?: return@mapNotNull null
        val y = s.y ?: return@mapNotNull null
        Contact(s.trackingId, x, y)
    }

    private fun toMs(sec: String, frac: String): Long {
        val micros = frac.padEnd(6, '0').take(6).toLong()
        return sec.toLong() * 1_000 + micros / 1_000
    }

    /** Nilai heksadesimal 32-bit bertanda (`ffffffff` = -1). */
    private fun parseValue(value: String): Int? = value.toLongOrNull(16)?.toInt()
}
