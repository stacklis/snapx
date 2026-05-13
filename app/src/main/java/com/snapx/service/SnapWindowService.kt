package com.snapx.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class SnapWindowService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun getVisibleWindowBounds(): List<Rect> {
        return windows
            .filter { it.type != AccessibilityWindowInfo.TYPE_SYSTEM }
            .mapNotNull { window ->
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                if (bounds.isEmpty || bounds.width() < 100 || bounds.height() < 100) null
                else bounds
            }
    }

    companion object {
        @Volatile
        var instance: SnapWindowService? = null
            private set
    }
}
