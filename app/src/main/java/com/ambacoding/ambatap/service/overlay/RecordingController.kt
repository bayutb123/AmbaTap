package com.ambacoding.ambatap.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambacoding.ambatap.R
import com.ambacoding.ambatap.domain.model.AppSettings
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.ScreenInfo
import com.ambacoding.ambatap.domain.repository.MacroRepository
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import com.ambacoding.ambatap.engine.player.GestureFactory
import com.ambacoding.ambatap.engine.player.InputController
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.engine.recorder.GestureClassifier
import com.ambacoding.ambatap.engine.recorder.MacroRecorder
import com.ambacoding.ambatap.engine.recorder.RecordingState
import com.ambacoding.ambatap.engine.recorder.TouchSample
import com.ambacoding.ambatap.service.realScreenSize
import com.ambacoding.ambatap.service.screenRotation
import com.ambacoding.ambatap.ui.components.defaultMacroName
import com.ambacoding.ambatap.ui.theme.AmbaTapTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Perekaman lewat lapisan layar penuh: sentuhan ditangkap, diklasifikasi, lalu
 * diteruskan ke aplikasi di bawahnya dengan `dispatchGesture` sambil lapisan dibuat
 * tembus sentuh sementara. Setelah selesai, sheet simpan ditampilkan.
 */
class RecordingController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val recorder: MacroRecorder,
    private val player: MacroPlayer,
    private val repository: MacroRepository,
    settingsRepository: SettingsRepository,
    private val input: InputController,
    private val bringPanelToFront: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_AmbaTap)
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val simplifyEpsilonPx = SIMPLIFY_EPSILON_DP * context.resources.displayMetrics.density

    private val settings = settingsRepository.settings
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val liveTrail = mutableStateListOf<Offset>()
    private val forwardLock = Mutex()
    private var samples = mutableListOf<TouchSample>()
    private var pointerId = NO_POINTER

    private var layer: OverlayWindow? = null
    private var sheet: OverlayWindow? = null

    // Isian sheet disimpan di sini agar tidak hilang saat sheet disembunyikan untuk uji putar.
    private var draftName by mutableStateOf("")
    private var draftRepeat by mutableStateOf<RepeatMode>(RepeatMode.Count(1))

    fun start() {
        scope.launch {
            recorder.state.map { it.status }.distinctUntilChanged().collect { status ->
                when (status) {
                    RecordingState.Status.RECORDING -> showLayer(touchable = true)
                    RecordingState.Status.PAUSED -> showLayer(touchable = false)
                    RecordingState.Status.REVIEW -> {
                        hideLayer()
                        draftName = defaultName()
                        draftRepeat = RepeatMode.Count(1)
                    }
                    RecordingState.Status.IDLE -> hideLayer()
                }
            }
        }
        scope.launch {
            combine(
                recorder.state.map { it.status == RecordingState.Status.REVIEW },
                player.state.map { it.isActive },
            ) { review, playing -> review && !playing }
                .distinctUntilChanged()
                .collect { visible -> if (visible) showSheet() else sheet?.hide() }
        }
    }

    fun startRecording() {
        if (player.state.value.isActive) return
        val size = context.realScreenSize()
        recorder.start(ScreenInfo(size.widthPx, size.heightPx, context.screenRotation()), now())
    }

    fun pause() = recorder.pause(now())

    fun resume() = recorder.resume(now())

    fun finish() = recorder.finish(now())

    fun cancel() = recorder.reset()

    fun destroy() {
        layer?.destroy()
        sheet?.destroy()
        layer = null
        sheet = null
        recorder.reset()
    }

    // region Lapisan perekaman

    private fun showLayer(touchable: Boolean) {
        val window = layer ?: createLayer().also { layer = it }
        if (!window.isShowing) {
            window.show()
            // Jendela yang ditambahkan belakangan berada di atas; panel harus tetap bisa ditekan.
            bringPanelToFront()
        }
        window.setTouchable(touchable)
    }

    private fun hideLayer() {
        layer?.hide()
        liveTrail.clear()
        samples = mutableListOf()
        pointerId = NO_POINTER
    }

    @SuppressLint("ClickableViewAccessibility") // Lapisan ini bukan kontrol; sentuhan diteruskan apa adanya.
    private fun createLayer(): OverlayWindow {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        return OverlayWindow(themedContext, windowManager, params) {
            AmbaTapTheme(darkTheme = false) {
                val state by recorder.state.collectAsStateWithLifecycle()
                RecordLayer(state, liveTrail)
            }
        }.also { window ->
            window.view.setOnTouchListener { _, event ->
                onTouch(event)
                true
            }
        }
    }

    /** Mengumpulkan sampel jari pertama; jari tambahan diabaikan (multi-touch belum didukung). */
    private fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                samples = mutableListOf()
                liveTrail.clear()
                addSamples(event)
            }
            MotionEvent.ACTION_MOVE -> addSamples(event)
            MotionEvent.ACTION_UP -> {
                addSamples(event)
                completeTouch()
            }
            MotionEvent.ACTION_POINTER_UP ->
                if (event.getPointerId(event.actionIndex) == pointerId) {
                    addSamples(event)
                    completeTouch()
                }
            MotionEvent.ACTION_CANCEL -> {
                samples = mutableListOf()
                pointerId = NO_POINTER
                liveTrail.clear()
            }
        }
    }

    private fun addSamples(event: MotionEvent) {
        if (pointerId == NO_POINTER) return
        val index = event.findPointerIndex(pointerId)
        if (index < 0) return
        // rawX/rawY hanya untuk pointer pertama; selisihnya = posisi jendela di layar.
        val offsetX = event.rawX - event.getX(0)
        val offsetY = event.rawY - event.getY(0)
        for (h in 0 until event.historySize) {
            val x = event.getHistoricalX(index, h)
            val y = event.getHistoricalY(index, h)
            samples += TouchSample(x + offsetX, y + offsetY, event.getHistoricalEventTime(h))
            liveTrail += Offset(x, y)
        }
        val x = event.getX(index)
        val y = event.getY(index)
        samples += TouchSample(x + offsetX, y + offsetY, event.eventTime)
        liveTrail += Offset(x, y)
    }

    private fun completeTouch() {
        val captured = samples.toList()
        samples = mutableListOf()
        pointerId = NO_POINTER
        if (captured.isEmpty()) return
        scope.launch { forward(captured) }
    }

    private suspend fun forward(captured: List<TouchSample>) = forwardLock.withLock {
        val screen = context.realScreenSize()
        val action = GestureClassifier.classify(captured, screen, touchSlopPx, longPressMs, simplifyEpsilonPx)
        val gesture = GestureFactory.create(action, screen) ?: return@withLock

        layer?.setTouchable(false)
        delay(PASS_THROUGH_SETTLE_MS)
        val startMs = now()
        input.dispatch(gesture)
        recorder.append(action, startMs, endMs = now())
        liveTrail.clear()
        if (recorder.state.value.status == RecordingState.Status.RECORDING) layer?.setTouchable(true)
    }

    // endregion

    // region Sheet simpan

    private fun showSheet() {
        val window = sheet ?: createSheet().also { sheet = it }
        window.show()
    }

    private fun createSheet(): OverlayWindow {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Tanpa FLAG_NOT_FOCUSABLE agar kolom nama bisa memunculkan keyboard.
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            dimAmount = SHEET_DIM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        return OverlayWindow(themedContext, windowManager, params) {
            AmbaTapTheme(darkTheme = false) {
                val state by recorder.state.collectAsStateWithLifecycle()
                SaveSheet(
                    state = state,
                    name = draftName,
                    onNameChange = { draftName = it },
                    repeat = draftRepeat,
                    onRepeatChange = { draftRepeat = it },
                    onSave = ::save,
                    onTestPlay = ::testPlay,
                    onDiscard = recorder::reset,
                )
            }
        }
    }

    private fun buildDraft(): Macro? = recorder.buildDraft(
        name = draftName.trim().ifEmpty { defaultName() },
        config = PlaybackConfig(repeat = draftRepeat),
        createdAt = System.currentTimeMillis(),
    )

    private fun save() {
        val draft = buildDraft() ?: return
        scope.launch {
            val id = repository.save(draft)
            player.load(draft.copy(id = id))
            recorder.reset()
        }
    }

    private fun testPlay() {
        val draft = buildDraft() ?: return
        player.play(draft, startDelayMs = settings.value.countdownSeconds * 1_000L)
    }

    // endregion

    private fun now() = SystemClock.uptimeMillis()

    private fun defaultName(): String = defaultMacroName("Rekaman")

    private companion object {
        const val NO_POINTER = -1
        const val SIMPLIFY_EPSILON_DP = 1.5f
        const val PASS_THROUGH_SETTLE_MS = 50L
        const val SHEET_DIM = 0.5f
    }
}
