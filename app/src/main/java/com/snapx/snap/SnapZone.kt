package com.snapx.snap

import android.graphics.Rect

enum class ZoneType { FULL, HALF_LEFT, HALF_RIGHT, HALF_TOP, HALF_BOTTOM, WINDOW, EDGE }

data class SnapZone(
    val type: ZoneType,
    val rect: Rect,
    val label: String,
    val priority: Int
)
