package com.snapx.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.MainThread

class SnapWindowService : AccessibilityService() {

    @Volatile
    private var cachedWindowBounds: List<Rect> = emptyList()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Snapshot window bounds on every window-change event (runs on main thread).
        // Cached value is read by ScreenshotWatcherService from Dispatchers.IO.
        cachedWindowBounds = snapshotWindowBounds()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun getVisibleWindowBounds(): List<Rect> = cachedWindowBounds

    @MainThread
    private fun snapshotWindowBounds(): List<Rect> {
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
