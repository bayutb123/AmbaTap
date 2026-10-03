package com.ambacoding.ambatap.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import com.ambacoding.ambatap.R
import com.ambacoding.ambatap.domain.model.AppSettings
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.MacroAction
import com.ambacoding.ambatap.domain.model.PlaybackConfig
import com.ambacoding.ambatap.domain.model.RepeatMode
import com.ambacoding.ambatap.domain.model.TimedPoint
import com.ambacoding.ambatap.domain.model.withEndpoints
import com.ambacoding.ambatap.domain.repository.MacroRepository
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.engine.player.ScreenSize
import com.ambacoding.ambatap.service.realScreenSize
import com.ambacoding.ambatap.ui.theme.AmbaTapTheme
import com.ambacoding.ambatap.ui.theme.PlayBlue
import com.ambacoding.ambatap.ui.theme.RecordOrange
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Overlay pemilih titik untuk auto clicker manual dan untuk mengatur posisi aksi macro
 * langsung di atas aplikasi tujuan.
 *
 * Supaya aplikasi di bawahnya tetap bisa dipakai selama mengatur titik, tidak ada jendela
 * layar penuh yang bisa disentuh: jalur swipe digambar di lapisan tembus sentuh, setiap
 * penanda punya jendela kecilnya sendiri, dan bar atas/bawah hanya selebar isinya.
 * Semua jendela disembunyikan sementara selama uji putar.
 */
class PointPickerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val requests: PointPickerRequests,
    private val player: MacroPlayer,
    private val repository: MacroRepository,
    settingsRepository: SettingsRepository,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_AmbaTap)
    private val density = context.resources.displayMetrics.density
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val settings = settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private var session: Macro? = null
    private val actions = mutableStateListOf<MacroAction>()
    private var config by mutableStateOf(PlaybackConfig())
    private var selected by mutableStateOf<Int?>(null)
    private var screen by mutableStateOf(ScreenSize(1, 1))

    private var visible = false
    private var pathLayer: OverlayWindow? = null
    private var topBar: OverlayWindow? = null
    private var bottomBar: OverlayWindow? = null
    private val markers = mutableListOf<MarkerWindow>()

    fun start() {
        scope.launch {
            requests.session.collect { macro -> if (macro != null && macro !== session) load(macro) }
        }
        scope.launch {
            combine(
                requests.session.map { it != null },
                player.state.map { it.isActive },
            ) { open, playing -> open && !playing }
                .distinctUntilChanged()
                .collect { show -> if (show) showAll() else hideAll() }
        }
    }

    fun destroy() {
        hideAll()
        pathLayer?.destroy()
        topBar?.destroy()
        bottomBar?.destroy()
        markers.forEach { it.window.destroy() }
        pathLayer = null
        topBar = null
        bottomBar = null
        markers.clear()
        requests.close()
    }

    private fun load(macro: Macro) {
        session = macro
        actions.clear()
        actions.addAll(macro.actions)
        config = macro.config
        selected = null
        syncMarkers()
    }

    private fun current(): Macro? = session?.copy(actions = actions.toList(), config = config)

    // region Tampil / sembunyi

    private fun showAll() {
        visible = true
        screen = context.realScreenSize()
        (pathLayer ?: createPathLayer().also { pathLayer = it }).show()
        syncMarkers()
        // Bar ditambahkan terakhir agar berada di atas penanda.
        (topBar ?: createTopBar().also { topBar = it }).show()
        (bottomBar ?: createBottomBar().also { bottomBar = it }).show()
    }

    private fun hideAll() {
        visible = false
        pathLayer?.hide()
        markers.forEach { it.window.hide() }
        topBar?.hide()
        bottomBar?.hide()
    }

    private fun bringBarsToFront() {
        listOfNotNull(topBar, bottomBar).filter { it.isShowing }.forEach {
            it.hide()
            it.show()
        }
    }

    // endregion

    // region Penanda

    private enum class End { SINGLE, START, END }

    private data class Handle(val index: Int, val end: End)

    private fun handles(): List<Handle> = buildList {
        actions.forEachIndexed { i, action ->
            when (action) {
                is MacroAction.Tap, is MacroAction.LongPress -> add(Handle(i, End.SINGLE))
                is MacroAction.Swipe -> if (action.points.isNotEmpty()) {
                    add(Handle(i, End.START))
                    add(Handle(i, End.END))
                }
                else -> Unit
            }
        }
    }

    private fun position(handle: Handle): Pair<Float, Float>? = when (val action = actions.getOrNull(handle.index)) {
        is MacroAction.Tap -> action.x to action.y
        is MacroAction.LongPress -> action.x to action.y
        is MacroAction.Swipe -> when (handle.end) {
            End.END -> action.points.lastOrNull()?.let { it.x to it.y }
            else -> action.points.firstOrNull()?.let { it.x to it.y }
        }
        else -> null
    }

    /** Menyamakan jendela penanda dengan daftar aksi: jumlah, posisi, label, dan pilihan. */
    private fun syncMarkers() {
        val wanted = handles()
        while (markers.size > wanted.size) markers.removeAt(markers.lastIndex).window.destroy()
        var created = false
        wanted.forEachIndexed { k, handle ->
            val marker = markers.getOrNull(k) ?: MarkerWindow().also {
                markers += it
                created = true
            }
            marker.bind(handle)
        }
        if (!visible) return
        markers.forEach { if (!it.window.isShowing) it.window.show() }
        if (created) bringBarsToFront()
    }

    private fun moveHandle(handle: Handle, nx: Float, ny: Float) {
        val x = nx.coerceIn(0f, 1f)
        val y = ny.coerceIn(0f, 1f)
        val updated = when (val action = actions.getOrNull(handle.index)) {
            is MacroAction.Tap -> action.copy(x = x, y = y)
            is MacroAction.LongPress -> action.copy(x = x, y = y)
            is MacroAction.Swipe -> {
                val start = action.points.first()
                val end = action.points.last()
                if (handle.end == End.END) {
                    action.withEndpoints(start.x, start.y, x, y)
                } else {
                    action.withEndpoints(x, y, end.x, end.y)
                }
            }
            else -> return
        }
        actions[handle.index] = updated
        syncMarkers()
    }

    private fun select(index: Int?) {
        selected = index
        syncMarkers()
    }

    /** Satu penanda = satu jendela kecil; sentuhan di luar penanda diteruskan sistem ke aplikasi. */
    @SuppressLint("ClickableViewAccessibility") // Penanda digeser, bukan diklik; label ada di semantics.
    private inner class MarkerWindow {
        private var handle: Handle? = null
        private val ui = mutableStateOf(MarkerUi("", PlayBlue, selected = false, size = MarkerSize))

        val window = OverlayWindow(themedContext, windowManager, markerParams(MarkerSize)) {
            AmbaTapTheme(darkTheme = false) { PickerMarker(ui.value) }
        }.also { overlay -> overlay.view.setOnTouchListener { _, event -> onTouch(event) } }

        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0f
        private var startY = 0f
        private var dragging = false

        fun bind(newHandle: Handle) {
            handle = newHandle
            val (nx, ny) = position(newHandle) ?: return
            val action = actions[newHandle.index]
            val size = if (newHandle.end == End.END) SmallMarkerSize else MarkerSize
            ui.value = MarkerUi(
                label = if (newHandle.end == End.END) "›" else "${newHandle.index + 1}",
                color = if (action is MacroAction.LongPress) RecordOrange else PlayBlue,
                selected = selected == newHandle.index,
                size = size,
            )
            val sizePx = (size.value * density).roundToInt()
            val x = (nx * screen.widthPx - sizePx / 2f).roundToInt()
            val y = (ny * screen.heightPx - sizePx / 2f).roundToInt()
            val params = window.params
            if (params.x != x || params.y != y || params.width != sizePx) {
                window.update {
                    this.x = x
                    this.y = y
                    width = sizePx
                    height = sizePx
                }
            }
        }

        /** Memakai koordinat mentah layar: posisi lokal ikut bergeser saat jendelanya dipindah. */
        private fun onTouch(event: MotionEvent): Boolean {
            val current = handle ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    val (x, y) = position(current) ?: return false
                    startX = x
                    startY = y
                    dragging = false
                    if (selected != current.index) select(current.index)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && hypot(dx, dy) > touchSlopPx) dragging = true
                    if (dragging) moveHandle(current, startX + dx / screen.widthPx, startY + dy / screen.heightPx)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }
    }

    private fun markerParams(size: Dp) = WindowManager.LayoutParams(
        (size.value * density).roundToInt(),
        (size.value * density).roundToInt(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    // endregion

    // region Lapisan jalur & bar

    private fun createPathLayer() = OverlayWindow(
        themedContext,
        windowManager,
        OverlayWindow.fullScreenParams(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
    ) {
        PickerPaths(actions, screen)
    }

    private fun barParams(gravity: Int, width: Int, yDp: Int) = WindowManager.LayoutParams(
        width,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        y = (yDp * density).roundToInt()
    }

    private fun createTopBar() = OverlayWindow(
        themedContext,
        windowManager,
        barParams(Gravity.TOP or Gravity.CENTER_HORIZONTAL, WindowManager.LayoutParams.WRAP_CONTENT, yDp = 32),
    ) {
        AmbaTapTheme(darkTheme = false) {
            PickerTopBar(
                name = session?.name.orEmpty(),
                positional = actions.count { it.isPositional },
                repeat = config.repeat,
                onClose = requests::close,
            )
        }
    }

    /**
     * Bar bawah (panel titik + toolbar) mengambang dan bisa digeser lewat gagangnya, agar tidak
     * menutupi bagian bawah aplikasi. Awalnya di tengah bawah; posisinya diingat selama sesi.
     */
    private fun createBottomBar() = OverlayWindow(
        themedContext,
        windowManager,
        WindowManager.LayoutParams(
            bottomBarWidthPx(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screen.widthPx - width) / 2
            y = screen.heightPx
        },
    ) {
        AmbaTapTheme(darkTheme = false) {
            PickerBottomBar(
                actions = actions,
                selected = selected,
                repeat = config.repeat,
                screen = screen,
                onDragBy = ::moveBottomBar,
                callbacks = PickerActions(
                    onDeselect = { select(null) },
                    onChange = { index, action ->
                        if (index in actions.indices) actions[index] = action
                        syncMarkers()
                    },
                    onDelete = { index ->
                        if (index in actions.indices) actions.removeAt(index)
                        select(null)
                    },
                    onAddTap = {
                        actions += MacroAction.Tap(0.5f, 0.5f, delayBeforeMs = DEFAULT_INTERVAL_MS)
                        select(actions.lastIndex)
                    },
                    onAddSwipe = {
                        actions += MacroAction.Swipe(
                            points = listOf(TimedPoint(0.5f, 0.65f, 0), TimedPoint(0.5f, 0.35f, 300)),
                            durationMs = 300,
                            delayBeforeMs = DEFAULT_INTERVAL_MS,
                        )
                        select(actions.lastIndex)
                    },
                    onCycleRepeat = {
                        val index = RepeatCycle.indexOf(config.repeat)
                        config = config.copy(repeat = RepeatCycle[(index + 1) % RepeatCycle.size])
                    },
                    onTestPlay = ::testPlay,
                    onSave = ::save,
                    onClose = requests::close,
                ),
            )
        }
    }.also { bar ->
        // Saat tinggi berubah (panel titik muncul/hilang) atau pertama kali tampil, jaga tetap di layar.
        bar.view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> moveBottomBar(0f, 0f) }
    }

    private var bottomBarPlaced = false

    private fun bottomBarWidthPx(): Int =
        minOf(screen.widthPx - (BAR_MARGIN_DP * 2 * density).roundToInt(), (BAR_MAX_WIDTH_DP * density).roundToInt())

    private fun moveBottomBar(dx: Float, dy: Float) {
        val bar = bottomBar ?: return
        val height = bar.view.height
        if (height == 0) return
        val params = bar.params
        val margin = (BAR_MARGIN_DP * density).roundToInt()
        val maxX = (screen.widthPx - params.width).coerceAtLeast(0)
        val maxY = (screen.heightPx - height - margin).coerceAtLeast(0)
        val baseY = if (bottomBarPlaced) params.y else maxY
        bottomBarPlaced = true
        val newX = (params.x + dx.roundToInt()).coerceIn(0, maxX)
        val newY = (baseY + dy.roundToInt()).coerceIn(0, maxY)
        // Hanya update bila berubah: dipanggil dari layout listener, update memicu layout lagi.
        if (newX == params.x && newY == params.y) return
        bar.update {
            x = newX
            y = newY
        }
    }

    // endregion

    private fun testPlay() {
        val macro = current() ?: return
        player.play(macro, startDelayMs = settings.value.countdownSeconds * 1_000L)
    }

    private fun save() {
        val macro = current() ?: return
        scope.launch {
            val saved = macro.copy(updatedAt = System.currentTimeMillis())
            val id = repository.save(saved)
            player.load(saved.copy(id = id))
            session = null
            requests.close()
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 100L
        const val BAR_MARGIN_DP = 12
        const val BAR_MAX_WIDTH_DP = 400

        /** Urutan pilihan tombol "Ulang" di toolbar. */
        val RepeatCycle = listOf(RepeatMode.Count(1), RepeatMode.Count(10), RepeatMode.Count(100), RepeatMode.Infinite)
    }
}
