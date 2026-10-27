package com.nusu.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** The "hands" of the assistant: read the screen, tap, type, scroll, press system keys. */
class NusuAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: NusuAccessibilityService? = null
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Compact text dump of the current screen. "[tap]" marks clickable items. */
    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "no active window"
        val sb = StringBuilder()
        var count = 0
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || count >= 120) return
            val label = (n.text ?: n.contentDescription)?.toString()
            if (!label.isNullOrBlank()) {
                val clickable = n.isClickable || n.parent?.isClickable == true
                sb.append(if (clickable) "[tap] " else "").append(label.take(80)).append('\n')
                count++
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return sb.toString().ifBlank { "screen has no readable text" }
    }

    fun tapText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (n in nodes) {
            var cur: AccessibilityNodeInfo? = n
            while (cur != null) {
                if (cur.isClickable && cur.isEnabled) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                cur = cur.parent
            }
        }
        // nothing clickable in the tree: tap the middle of the first match
        nodes.firstOrNull()?.let {
            val r = Rect(); it.getBoundsInScreen(r)
            return tapXY(r.centerX().toFloat(), r.centerY().toFloat())
        }
        return false
    }

    fun tapXY(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        return dispatchGesture(g, null, null)
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(down: Boolean): Boolean {
        val s = findScrollable(rootInActiveWindow) ?: return false
        return s.performAction(
            if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    fun press(key: String): Boolean = when (key.lowercase()) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        else -> false
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) findEditable(n.getChild(i))?.let { return it }
        return null
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) findScrollable(n.getChild(i))?.let { return it }
        return null
    }
}
