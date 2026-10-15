package com.ambacoding.ambatap.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.graphics.get
import com.ambacoding.ambatap.domain.model.GlobalType
import com.ambacoding.ambatap.domain.model.Macro
import com.ambacoding.ambatap.domain.model.TextMatch
import com.ambacoding.ambatap.domain.repository.MacroRepository
import com.ambacoding.ambatap.domain.repository.SettingsRepository
import com.ambacoding.ambatap.engine.player.GestureSpec
import com.ambacoding.ambatap.engine.player.InputController
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.engine.player.PlaybackError
import com.ambacoding.ambatap.engine.player.PxPoint
import com.ambacoding.ambatap.engine.player.ScreenSize
import com.ambacoding.ambatap.engine.player.TextMatcher
import com.ambacoding.ambatap.engine.recorder.MacroRecorder
import com.ambacoding.ambatap.service.notification.PlaybackNotifier
import com.ambacoding.ambatap.service.overlay.FloatingPanelState
import com.ambacoding.ambatap.service.overlay.PanelController
import com.ambacoding.ambatap.service.overlay.PointPickerController
import com.ambacoding.ambatap.service.overlay.PointPickerRequests
import com.ambacoding.ambatap.service.overlay.RecordingController
import com.ambacoding.ambatap.service.overlay.TouchIndicatorController
import com.ambacoding.ambatap.service.trigger.ForegroundAppTracker
import com.ambacoding.ambatap.service.trigger.TriggerLauncher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@AndroidEntryPoint
class AmbaTapAccessibilityService : AccessibilityService(), InputController {

    @Inject lateinit var bridge: ServiceBridge
    @Inject lateinit var player: MacroPlayer
    @Inject lateinit var panelState: FloatingPanelState
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notifier: PlaybackNotifier
    @Inject lateinit var recorder: MacroRecorder
    @Inject lateinit var macroRepository: MacroRepository
    @Inject lateinit var pickerRequests: PointPickerRequests
    @Inject lateinit var triggerLauncher: TriggerLauncher

    private val emergencyStop = EmergencyStopDetector()
    private val foregroundApps by lazy { ForegroundAppTracker(ignored = setOf(packageName)) }

    /** Macro dengan pemicu buka aplikasi aktif, menurut nama paket aplikasinya. */
    private var appOpenMacros: Map<String, Macro> = emptyMap()

    private var scope: CoroutineScope? = null
    private var panel: PanelController? = null
    private var recording: RecordingController? = null
    private var picker: PointPickerController? = null
    private var touchIndicator: TouchIndicatorController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val scope = MainScope().also { scope = it }
        val recording = RecordingController(
            context = this,
            scope = scope,
            recorder = recorder,
            player = player,
            repository = macroRepository,
            settingsRepository = settingsRepository,
            input = this,
            bringPanelToFront = { panel?.bringToFront() },
        ).also { recording = it }
        panel = PanelController(this, scope, player, recorder, recording, panelState, settingsRepository)
            .also { it.start() }
        recording.start()
        picker = PointPickerController(this, scope, pickerRequests, player, macroRepository, settingsRepository, input = this)
            .also { it.start() }
        touchIndicator = TouchIndicatorController(this, scope, player, settingsRepository).also { it.start() }
        scope.launch {
            player.state
                .distinctUntilChangedBy { listOf(it.status, it.loop, it.macroName, (it.countdownMs + 999) / 1000) }
                .collect(notifier::update)
        }
        scope.launch {
            macroRepository.observeMacros().collect { macros ->
                appOpenMacros = macros
                    .mapNotNull { macro -> macro.triggers.appOpen?.takeIf { it.isActive }?.let { it.packageName to macro } }
                    .toMap()
            }
        }
        scope.launch {
            player.state
                .map { it.error to it.errorActionNumber }
                .distinctUntilChanged()
                .collect { (error, number) -> stopMessage(error, number)?.let(::toast) }
        }
        bridge.attach(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    private fun detach() {
        player.stop()
        bridge.detach(this)
        recording?.destroy()
        recording = null
        picker?.destroy()
        picker = null
        touchIndicator?.destroy()
        touchIndicator = null
        panel?.destroy()
        panel = null
        scope?.cancel()
        scope = null
        notifier.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Fokus jendela kadang baru berpindah setelah TYPE_WINDOW_STATE_CHANGED, jadi
        // TYPE_WINDOWS_CHANGED juga diperiksa; tracker mengabaikan paket yang sama.
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (appOpenMacros.isEmpty()) return
        val opened = foregroundApp()?.let(foregroundApps::onForeground) ?: return
        val macro = appOpenMacros[opened] ?: return
        scope?.launch { triggerLauncher.launch(macro, notifyOnSkip = false) }
    }

    /**
     * Paket aplikasi di jendela aplikasi yang sedang fokus. Keyboard, panel notifikasi, dan
     * overlay bukan jendela aplikasi sehingga tidak dianggap berpindah aplikasi.
     */
    private fun foregroundApp(): String? = runCatching {
        windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
            ?.root?.packageName?.toString()
    }.getOrNull()

    private fun stopMessage(error: PlaybackError?, actionNumber: Int?): String? = when (error) {
        PlaybackError.CONDITION_TIMEOUT -> "Macro berhenti: kondisi aksi #$actionNumber tidak terpenuhi"
        PlaybackError.SCREEN_CAPTURE_UNSUPPORTED -> "Macro berhenti: aksi #$actionNumber butuh Android 11+"
        else -> null
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onInterrupt() = Unit

    /** Volume turun 2× menghentikan macro; tombol hanya ditahan selama macro aktif. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN || !player.state.value.isActive) {
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            emergencyStop.onPress(event.eventTime)
        ) {
            player.stop()
        }
        return true
    }

    // region InputController

    override val screenSize: ScreenSize get() = realScreenSize()

    override suspend fun dispatch(gesture: GestureSpec): Boolean {
        val panel = panel ?: return dispatchNow(gesture)
        return panel.passThrough(gesture) { dispatchNow(gesture) }
    }

    private suspend fun dispatchNow(gesture: GestureSpec): Boolean =
        suspendCancellableCoroutine { cont ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val accepted = runCatching {
                dispatchGesture(gesture.toGestureDescription(), callback, null)
            }.getOrDefault(false)
            if (!accepted && cont.isActive) cont.resume(false)
        }

    override fun performGlobal(type: GlobalType): Boolean = performGlobalAction(
        when (type) {
            GlobalType.BACK -> GLOBAL_ACTION_BACK
            GlobalType.HOME -> GLOBAL_ACTION_HOME
            GlobalType.RECENTS -> GLOBAL_ACTION_RECENTS
            GlobalType.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
        },
    )

    override fun launchApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    override fun inputText(text: String): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun findText(query: String, match: TextMatch): PxPoint? {
        if (query.isBlank()) return null
        // Jendela paling atas dulu (dialog, pop-up), tanpa overlay milik AmbaTap sendiri.
        val roots = runCatching { windows }.getOrDefault(emptyList())
            .filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
            .sortedByDescending { it.layer }
            .mapNotNull { it.root }
            .ifEmpty { listOfNotNull(rootInActiveWindow) }
        val bounds = Rect()
        for (root in roots) {
            val node = findNode(root) { node ->
                node.isVisibleToUser &&
                    (
                        TextMatcher.matches(node.text, query, match) ||
                            TextMatcher.matches(node.contentDescription, query, match)
                        )
            } ?: continue
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) return PxPoint(bounds.exactCenterX(), bounds.exactCenterY())
        }
        return null
    }

    /** Penelusuran melebar dengan batas jumlah node agar layar yang sangat besar tidak membekukan service. */
    private fun findNode(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_NODES) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    override val canReadScreen: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override suspend fun pixelColor(x: Int, y: Int): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val bitmap = captureScreen() ?: return null
        return try {
            bitmap[x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1)]
        } finally {
            bitmap.recycle()
        }
    }

    /** Tangkapan layar sebagai bitmap software (piksel bitmap hardware tidak bisa dibaca). */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { cont ->
        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                val bitmap = runCatching {
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                    hardware?.copy(Bitmap.Config.ARGB_8888, false).also { hardware?.recycle() }
                }.getOrNull()
                buffer.close()
                if (cont.isActive) cont.resume(bitmap) else bitmap?.recycle()
            }

            override fun onFailure(errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
        }
        runCatching { takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback) }
            .onFailure { if (cont.isActive) cont.resume(null) }
    }

    // endregion

    private companion object {
        const val MAX_NODES = 5_000
    }
}
