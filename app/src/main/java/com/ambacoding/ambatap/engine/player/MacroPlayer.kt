package com.ambacoding.ambatap.engine.player

import com.ambacoding.ambatap.di.ApplicationScope
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.OnTimeout
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.engine.player.PlaybackState.Status
import com.ambacoding.ambatap.service.ServiceBridge
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Memutar [Macro] lewat [InputController] yang tersambung di [ServiceBridge].
 *
 * Hanya satu macro berjalan pada satu waktu; [play] menghentikan pemutaran sebelumnya.
 * Pause berlaku di antara aksi: gesture yang sedang dikirim tetap diselesaikan.
 */
@Singleton
class MacroPlayer(
    private val bridge: ServiceBridge,
    private val scope: CoroutineScope,
    private val random: Random,
    private val timeSource: TimeSource.WithComparableMarks,
) {
    @Inject
    constructor(bridge: ServiceBridge, @ApplicationScope scope: CoroutineScope) :
        this(bridge, scope, Random.Default, TimeSource.Monotonic)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _lastMacro = MutableStateFlow<Macro?>(null)

    /** Macro terakhir yang berhasil dimulai; dipakai tombol putar di panel melayang. */
    val lastMacro: StateFlow<Macro?> = _lastMacro.asStateFlow()

    private val _gestures = MutableSharedFlow<GestureSpec>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Setiap gesture yang dikirim, untuk indikator sentuhan. */
    val gestures: SharedFlow<GestureSpec> = _gestures.asSharedFlow()

    private val paused = MutableStateFlow(false)
    private var job: Job? = null

    /** Naik setiap play/stop, agar job lama yang selesai belakangan tidak menimpa state baru. */
    private var generation = 0

    fun play(macro: Macro, startDelayMs: Long = 0) {
        stop()
        val controller = bridge.controller.value
        val error = when {
            controller == null -> PlaybackError.SERVICE_NOT_CONNECTED
            macro.actions.isEmpty() -> PlaybackError.EMPTY_MACRO
            else -> null
        }
        if (error != null || controller == null) {
            _state.value = PlaybackState(error = error)
            return
        }

        val gen = ++generation
        paused.value = false
        _lastMacro.value = macro
        _state.value = PlaybackState(
            status = if (startDelayMs > 0) Status.COUNTDOWN else Status.PLAYING,
            macroId = macro.id,
            macroName = macro.name,
            totalLoops = (macro.config.repeat as? RepeatMode.Count)?.times?.coerceAtLeast(1),
            actionCount = macro.actions.size,
            countdownMs = startDelayMs,
            orientationMismatch = macro.screen.isLandscape != controller.screenSize.isLandscape,
        )
        job = scope.launch {
            var failure: Failure? = null
            try {
                countdown(startDelayMs)
                failure = run(macro, controller)
            } finally {
                if (gen == generation) {
                    _state.value = PlaybackState(error = failure?.error, errorActionNumber = failure?.actionNumber)
                }
            }
        }
    }

    /** Menjadikan [macro] sasaran tombol putar di panel tanpa memutarnya. */
    fun load(macro: Macro) {
        _lastMacro.value = macro
    }

    fun pause() {
        if (_state.value.status != Status.PLAYING) return
        paused.value = true
        _state.update { it.copy(status = Status.PAUSED) }
    }

    fun resume() {
        if (_state.value.status != Status.PAUSED) return
        paused.value = false
        _state.update { it.copy(status = Status.PLAYING) }
    }

    fun stop() {
        generation++
        job?.cancel()
        job = null
        paused.value = false
        if (_state.value.isActive) _state.value = PlaybackState()
    }

    private suspend fun countdown(totalMs: Long) {
        var remaining = totalMs
        while (remaining > 0) {
            _state.update { it.copy(countdownMs = remaining) }
            val step = minOf(COUNTDOWN_TICK_MS, remaining)
            delay(step)
            remaining -= step
        }
        _state.update { it.copy(status = Status.PLAYING, countdownMs = 0) }
    }

    /** Alasan pemutaran berhenti sebelum selesai. */
    private class Failure(val error: PlaybackError, val actionNumber: Int)

    private suspend fun run(macro: Macro, controller: InputController): Failure? {
        val config = macro.config
        val repeat = config.repeat
        val started = timeSource.markNow()
        fun timeUp() = repeat is RepeatMode.Duration &&
            started.elapsedNow().inWholeMilliseconds >= repeat.millis

        var loop = 0
        while (true) {
            loop++
            if (repeat is RepeatMode.Count && loop > repeat.times.coerceAtLeast(1)) return null
            if (timeUp()) return null
            if (loop > 1) pausableDelay(scaledDelay(config.delayBetweenLoopsMs, config))

            for ((index, action) in macro.actions.withIndex()) {
                if (timeUp()) return null
                _state.update { it.copy(loop = loop, actionIndex = index) }
                pausableDelay(scaledDelay(action.delayBeforeMs, config))
                awaitResumed()
                perform(action, controller, config)?.let { return Failure(it, index + 1) }
            }
        }
    }

    /** @return alasan macro harus berhenti, atau `null` untuk lanjut ke aksi berikutnya. */
    private suspend fun perform(action: MacroAction, controller: InputController, config: PlaybackConfig): PlaybackError? {
        when (action) {
            is MacroAction.Wait -> pausableDelay(GestureFactory.scale(action.durationMs, config.speed))
            is MacroAction.GlobalAction -> controller.performGlobal(action.action)
            is MacroAction.LaunchApp -> controller.launchApp(action.packageName)
            is MacroAction.InputText -> controller.inputText(action.text)
            is MacroAction.Tap,
            is MacroAction.LongPress,
            is MacroAction.Swipe,
            is MacroAction.MultiTouch,
            -> {
                val gesture = GestureFactory.create(
                    action = action,
                    screen = controller.screenSize,
                    speed = config.speed,
                    jitterPx = config.randomOffsetPx,
                    random = random,
                ) ?: return null
                send(gesture, controller)
            }
            is MacroAction.WaitForText -> {
                var found: PxPoint? = null
                val met = awaitCondition(action.timeoutMs, TEXT_POLL_MS) {
                    found = controller.findText(action.text, action.match)
                    (found != null) == action.present
                }
                if (!met) return timeoutResult(action.onTimeout)
                val target = found
                if (action.present && action.tapOnFound && target != null) send(tapAt(target), controller)
            }
            is MacroAction.WaitForColor -> {
                if (!controller.canReadScreen) return PlaybackError.SCREEN_CAPTURE_UNSUPPORTED
                val screen = controller.screenSize
                val x = (action.x * screen.widthPx).toInt().coerceIn(0, screen.widthPx - 1)
                val y = (action.y * screen.heightPx).toInt().coerceIn(0, screen.heightPx - 1)
                val met = awaitCondition(action.timeoutMs, COLOR_POLL_MS) {
                    // Tangkapan yang gagal (mis. terlalu sering) dianggap belum terpenuhi.
                    val color = controller.pixelColor(x, y) ?: return@awaitCondition false
                    ColorMatcher.matches(color, action.color, action.tolerance) == action.present
                }
                if (!met) return timeoutResult(action.onTimeout)
                if (action.present && action.tapOnFound) send(tapAt(PxPoint(x.toFloat(), y.toFloat())), controller)
            }
        }
        return null
    }

    private suspend fun send(gesture: GestureSpec, controller: InputController) {
        _gestures.tryEmit(gesture)
        // Gesture yang dibatalkan sistem (mis. user menyentuh layar) dilewati saja.
        controller.dispatch(gesture)
    }

    private fun tapAt(point: PxPoint) = GestureSpec(listOf(StrokeSpec(listOf(point), startMs = 0, durationMs = TAP_MS)))

    private fun timeoutResult(onTimeout: OnTimeout): PlaybackError? =
        if (onTimeout == OnTimeout.STOP) PlaybackError.CONDITION_TIMEOUT else null

    /**
     * Memeriksa [check] berulang setiap [intervalMs] hingga bernilai `true` atau [timeoutMs] habis.
     * Batas waktu tidak ikut dipercepat [PlaybackConfig.speed] dan tidak berjalan selama pause.
     */
    private suspend fun awaitCondition(timeoutMs: Long, intervalMs: Long, check: suspend () -> Boolean): Boolean {
        var waited = 0L
        while (true) {
            awaitResumed()
            val mark = timeSource.markNow()
            if (check()) return true
            waited += mark.elapsedNow().inWholeMilliseconds
            if (waited >= timeoutMs) return false
            val step = minOf(intervalMs, timeoutMs - waited)
            pausableDelay(step)
            waited += step
        }
    }

    private fun scaledDelay(baseMs: Long, config: PlaybackConfig): Long {
        val jitter = config.randomDelayMs
        val withJitter = if (jitter > 0) baseMs + random.nextLong(-jitter, jitter + 1) else baseMs
        return GestureFactory.scale(withJitter.coerceAtLeast(0), config.speed)
    }

    /** Seperti [delay], tetapi waktu selama pause tidak dihitung. */
    private suspend fun pausableDelay(ms: Long) {
        var remaining = ms
        while (remaining > 0) {
            awaitResumed()
            val mark = timeSource.markNow()
            val pausedMidway = withTimeoutOrNull(remaining) { paused.first { it } } != null
            remaining -= mark.elapsedNow().inWholeMilliseconds
            if (!pausedMidway) return
        }
    }

    private suspend fun awaitResumed() {
        paused.first { !it }
    }

    private val ScreenInfo.isLandscape get() = widthPx > heightPx
    private val ScreenSize.isLandscape get() = widthPx > heightPx

    private companion object {
        const val COUNTDOWN_TICK_MS = 1_000L
        const val TEXT_POLL_MS = 250L

        /** Sistem menolak tangkapan layar yang lebih rapat dari ±333 ms. */
        const val COLOR_POLL_MS = 500L
        const val TAP_MS = 50L
    }
}
