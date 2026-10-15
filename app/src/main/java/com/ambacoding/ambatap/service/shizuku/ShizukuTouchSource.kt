package com.ambacoding.ambatap.service.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.ambacoding.ambatap.BuildConfig
import com.ambacoding.ambatap.engine.recorder.getevent.MultiTouchDecoder
import com.ambacoding.ambatap.engine.recorder.getevent.TouchDevice
import com.ambacoding.ambatap.engine.recorder.getevent.TouchDeviceParser
import com.ambacoding.ambatap.engine.recorder.getevent.TouchFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class ShizukuStatus {
    /** Aplikasi Shizuku tidak terpasang. */
    NOT_INSTALLED,

    /** Terpasang tetapi layanannya belum dijalankan (lewat ADB nirkabel atau root). */
    NOT_RUNNING,

    /** Versi Shizuku terlalu lama (sebelum API 11). */
    UNSUPPORTED,
    NO_PERMISSION,
    READY,
}

/** Sesi baca layar sentuh: device yang dipakai dan aliran frame-nya. */
class PrecisionStream(val device: TouchDevice, val frames: Flow<TouchFrame>)

class PrecisionUnavailableException(message: String) : Exception(message)

/**
 * Sumber sentuhan mentah lewat Shizuku untuk mode rekam presisi. Sentuhan dibaca dari
 * `getevent` tanpa lapisan penangkap, sehingga aplikasi menerima sentuhan secara langsung.
 */
@Singleton
class ShizukuTouchSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _status = MutableStateFlow(computeStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, TouchEventUserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("touch")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
    }

    fun refresh() {
        _status.value = computeStatus()
    }

    fun requestPermission() {
        if (_status.value == ShizukuStatus.NO_PERMISSION) runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }
    }

    private fun computeStatus(): ShizukuStatus = when {
        !isInstalled() -> ShizukuStatus.NOT_INSTALLED
        !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
        Shizuku.isPreV11() -> ShizukuStatus.UNSUPPORTED
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NO_PERMISSION
        else -> ShizukuStatus.READY
    }

    private fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
    }.isSuccess

    /**
     * Menyambung ke layanan shell dan menyiapkan aliran frame layar sentuh. Aliran berjalan
     * selama dikoleksi; layanan dilepas saat koleksi berhenti.
     * @throws PrecisionUnavailableException bila Shizuku tidak siap atau layar sentuh tidak ditemukan.
     */
    suspend fun open(): PrecisionStream {
        refresh()
        if (_status.value != ShizukuStatus.READY) throw PrecisionUnavailableException("Shizuku belum siap")
        val (service, connection) = bind() ?: throw PrecisionUnavailableException("Layanan Shizuku tidak merespons")
        val device = try {
            withContext(Dispatchers.IO) { TouchDeviceParser.pickTouchscreen(TouchDeviceParser.parse(service.describeDevices())) }
        } catch (e: Exception) {
            unbind(connection)
            throw PrecisionUnavailableException("Gagal membaca daftar input: ${e.message}")
        } ?: run {
            unbind(connection)
            throw PrecisionUnavailableException("Layar sentuh multi-touch tidak ditemukan")
        }

        val frames = callbackFlow {
            val decoder = MultiTouchDecoder()
            val listener = object : ITouchEventListener.Stub() {
                override fun onLines(lines: Array<String>) {
                    // Dipanggil di thread binder, berurutan karena oneway dari satu pengirim.
                    synchronized(decoder) { lines.forEach { line -> decoder.feed(line)?.let { trySend(it) } } }
                }

                override fun onClosed(reason: String) {
                    close(PrecisionUnavailableException("getevent berhenti: $reason"))
                }
            }
            service.start(device.path, listener)
            awaitClose {
                runCatching { service.stop() }
                unbind(connection)
            }
        }
        return PrecisionStream(device, frames)
    }

    private suspend fun bind(): Pair<ITouchEventService, ServiceConnection>? = withTimeoutOrNull(BIND_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val service = binder?.takeIf { it.pingBinder() }?.let(ITouchEventService.Stub::asInterface)
                    if (cont.isActive) cont.resume(service?.let { it to this })
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }
            runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
                .onFailure { if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { unbind(connection) }
        }
    }

    private fun unbind(connection: ServiceConnection) {
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST = 7
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
