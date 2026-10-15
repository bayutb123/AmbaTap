package com.ambacoding.ambatap.service.shizuku

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Layanan yang dijalankan Shizuku di proses terpisah dengan hak shell (bukan proses aplikasi:
 * tanpa Application, Hilt, atau Context). Hanya menjalankan `getevent` dan meneruskan barisnya.
 */
class TouchEventUserService : ITouchEventService.Stub() {

    private var process: Process? = null
    private var reader: Thread? = null

    override fun destroy() {
        stop()
        exitProcess(0)
    }

    override fun describeDevices(): String {
        val proc = ProcessBuilder("getevent", "-lp").redirectErrorStream(true).start()
        return try {
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(DESCRIBE_TIMEOUT_S, TimeUnit.SECONDS)
            output
        } finally {
            proc.destroy()
        }
    }

    @Synchronized
    override fun start(devicePath: String, listener: ITouchEventListener) {
        stop()
        // Hanya path device input yang diterima, agar argumen tidak bisa disisipi perintah lain.
        require(DEVICE_PATH.matches(devicePath)) { "invalid device path" }
        val proc = ProcessBuilder("getevent", "-lt", devicePath).start()
        process = proc
        reader = Thread({ pump(proc, listener) }, "ambatap-getevent").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    override fun stop() {
        process?.destroy()
        process = null
        reader?.interrupt()
        reader = null
    }

    /** Mengirim baris per frame (sampai `SYN_REPORT`) agar panggilan binder tidak terlalu sering. */
    private fun pump(proc: Process, listener: ITouchEventListener) {
        val batch = ArrayList<String>(BATCH_CAPACITY)
        var reason = "selesai"
        try {
            proc.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    batch += line
                    if ("SYN_REPORT" in line || batch.size >= BATCH_CAPACITY) {
                        listener.onLines(batch.toTypedArray())
                        batch.clear()
                    }
                }
            }
        } catch (e: IOException) {
            reason = e.message ?: "gagal membaca getevent"
        } catch (e: android.os.RemoteException) {
            // Aplikasi sudah tidak mendengarkan.
            reason = "pendengar terputus"
        }
        runCatching { listener.onClosed(reason) }
    }

    private companion object {
        const val DESCRIBE_TIMEOUT_S = 5L
        const val BATCH_CAPACITY = 64
        val DEVICE_PATH = Regex("""/dev/input/event\d+""")
    }
}
