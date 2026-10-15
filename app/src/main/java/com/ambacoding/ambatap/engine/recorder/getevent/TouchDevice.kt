package com.ambacoding.ambatap.engine.recorder.getevent

/** Layar sentuh dari `getevent -lp`: path device dan rentang koordinat mentahnya. */
data class TouchDevice(
    val path: String,
    val name: String,
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
    /** Punya `INPUT_PROP_DIRECT`, yaitu layar sentuh (bukan touchpad). */
    val direct: Boolean,
)

/** Mengurai keluaran `getevent -lp` (atau `-p` tanpa label). */
object TouchDeviceParser {

    private val addDevice = Regex("""^add device \d+:\s*(\S+)""")
    private val name = Regex("""^\s*name:\s*"(.*)"""")
    private val axis = Regex("""(ABS_MT_POSITION_X|ABS_MT_POSITION_Y|\b0035\b|\b0036\b)\s*:\s*value\s+-?\d+,\s*min\s+(-?\d+),\s*max\s+(-?\d+)""")

    fun parse(output: String): List<TouchDevice> {
        val devices = mutableListOf<TouchDevice>()
        var path: String? = null
        var deviceName = ""
        var x: IntRange? = null
        var y: IntRange? = null
        var direct = false

        fun flush() {
            val p = path ?: return
            val rx = x ?: return
            val ry = y ?: return
            if (rx.last > rx.first && ry.last > ry.first) {
                devices += TouchDevice(p, deviceName, rx.first, rx.last, ry.first, ry.last, direct)
            }
        }

        for (line in output.lineSequence()) {
            addDevice.find(line)?.let { match ->
                flush()
                path = match.groupValues[1]
                deviceName = ""
                x = null
                y = null
                direct = false
                return@let
            }
            name.find(line)?.let { deviceName = it.groupValues[1] }
            axis.find(line)?.let { match ->
                val range = match.groupValues[2].toInt()..match.groupValues[3].toInt()
                when (match.groupValues[1]) {
                    "ABS_MT_POSITION_X", "0035" -> x = range
                    else -> y = range
                }
            }
            if ("INPUT_PROP_DIRECT" in line) direct = true
        }
        flush()
        return devices
    }

    /** Layar sentuh utama: utamakan device `INPUT_PROP_DIRECT`. */
    fun pickTouchscreen(devices: List<TouchDevice>): TouchDevice? =
        devices.firstOrNull { it.direct } ?: devices.firstOrNull()
}
