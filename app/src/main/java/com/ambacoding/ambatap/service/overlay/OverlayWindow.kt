package com.ambacoding.ambatap.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Jendela overlay berisi Compose, untuk dipakai dari Service (bukan Activity).
 *
 * Service tidak menyediakan lifecycle, saved-state, dan ViewModel store yang dibutuhkan
 * Compose, jadi kelas ini menjadi owner ketiganya untuk [view]. Semua method harus
 * dipanggil di main thread.
 */
class OverlayWindow(
    context: Context,
    private val windowManager: WindowManager,
    val params: WindowManager.LayoutParams,
    content: @Composable () -> Unit,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    val view = ComposeView(context).apply {
        setViewTreeLifecycleOwner(this@OverlayWindow)
        setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
        setViewTreeViewModelStoreOwner(this@OverlayWindow)
        setContent(content)
    }

    var isShowing = false
        private set

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun show() {
        if (isShowing) return
        windowManager.addView(view, params)
        isShowing = true
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun hide() {
        if (!isShowing) return
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager.removeView(view)
        isShowing = false
    }

    fun update(block: WindowManager.LayoutParams.() -> Unit) {
        params.block()
        if (isShowing) windowManager.updateViewLayout(view, params)
    }

    /** Jendela tetap terlihat, tetapi sentuhan (termasuk gesture suntikan) menembus ke bawah. */
    fun setTouchable(touchable: Boolean) = update {
        flags = if (touchable) {
            flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }

    fun boundsOnScreen(): Rect? {
        if (!isShowing || view.width == 0) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    fun destroy() {
        hide()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        view.disposeComposition()
    }

    companion object {
        /** Jendela overlay aksesibilitas yang menutup seluruh layar, termasuk area poni. */
        fun fullScreenParams(extraFlags: Int = 0) = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                extraFlags,
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
    }
}
