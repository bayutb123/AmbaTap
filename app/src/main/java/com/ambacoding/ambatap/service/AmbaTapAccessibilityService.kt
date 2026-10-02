package com.ambacoding.ambatap.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ambacoding.ambatap.domain.model.GlobalType
import com.ambacoding.ambatap.engine.player.GestureSpec
import com.ambacoding.ambatap.engine.player.InputController
import com.ambacoding.ambatap.engine.player.MacroPlayer
import com.ambacoding.ambatap.engine.player.ScreenSize
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@AndroidEntryPoint
class AmbaTapAccessibilityService : AccessibilityService(), InputController {

    @Inject lateinit var bridge: ServiceBridge
    @Inject lateinit var player: MacroPlayer

    private val emergencyStop = EmergencyStopDetector()

    override fun onServiceConnected() {
        super.onServiceConnected()
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

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

    override suspend fun dispatch(gesture: GestureSpec): Boolean =
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

    // endregion
}
