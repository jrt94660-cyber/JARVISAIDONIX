package com.jarvis.aid

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

object GameAccessibilityBridge {
    @Volatile private var activeService: GameAccessibilityService? = null

    internal fun attach(service: GameAccessibilityService) { activeService = service }
    internal fun detach(service: GameAccessibilityService) {
        if (activeService === service) activeService = null
    }

    fun isEnabled(): Boolean = activeService != null
    fun analyzeVisibleUi(): String = activeService?.describeVisibleUi()
        ?: "Game assistance is not enabled. Enable the J.A.R.V.I.S accessibility service in Android Accessibility settings."

    fun tap(x: Float, y: Float): Boolean = activeService?.tapAt(x, y) ?: false
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean =
        activeService?.swipe(x1, y1, x2, y2, durationMs) ?: false
}

class GameAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        GameAccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        GameAccessibilityBridge.detach(this)
        super.onDestroy()
    }

    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(100, 2000)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun describeVisibleUi(): String {
        val root = rootInActiveWindow ?: return "No readable game UI is exposed by the current app."
        val lines = mutableListOf<String>()
        collectText(root, lines, 0)
        root.recycle()
        return lines.take(40).joinToString("\n").ifBlank {
            "The app exposes no readable accessibility labels. Visual game analysis requires the screen-capture module, which is not installed yet."
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (out.size >= 40 || depth > 12) return
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val value = listOf(text, description).filter { it.isNotBlank() }.distinct().joinToString(" — ")
        if (value.isNotBlank() && out.size < 40) out += value
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out, depth + 1)
            child.recycle()
        }
    }
}
