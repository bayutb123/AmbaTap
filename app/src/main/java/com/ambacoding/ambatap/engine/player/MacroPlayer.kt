package com.ambacoding.ambatap.engine.player

import com.ambacoding.ambatap.di.ApplicationScope
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.engine.player.PlaybackState.Status
import com.ambacoding.ambatap.service.ServiceBridge
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
        )
        job = scope.launch {
            try {
                countdown(startDelayMs)
                run(macro, controller)
            } finally {
                if (gen == generation) _state.value = PlaybackState()
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

    private suspend fun run(macro: Macro, controller: InputController) {
        val config = macro.config
        val repeat = config.repeat
        val started = timeSource.markNow()
        fun timeUp() = repeat is RepeatMode.Duration &&
            started.elapsedNow().inWholeMilliseconds >= repeat.millis

        var loop = 0
        while (true) {
            loop++
            if (repeat is RepeatMode.Count && loop > repeat.times.coerceAtLeast(1)) return
            if (timeUp()) return
            if (loop > 1) pausableDelay(scaledDelay(config.delayBetweenLoopsMs, config))

            for ((index, action) in macro.actions.withIndex()) {
                if (timeUp()) return
                _state.update { it.copy(loop = loop, actionIndex = index) }
                pausableDelay(scaledDelay(action.delayBeforeMs, config))
                awaitResumed()
                perform(action, controller, config)
            }
        }
    }

    private suspend fun perform(action: MacroAction, controller: InputController, config: PlaybackConfig) {
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
                ) ?: return
                // Gesture yang dibatalkan sistem (mis. user menyentuh layar) dilewati saja.
                controller.dispatch(gesture)
            }
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

    private companion object {
        const val COUNTDOWN_TICK_MS = 1_000L
    }
}
