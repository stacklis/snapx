package com.snapx.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.MainThread
import com.snapx.snap.SnapZone
import com.snapx.snap.ZoneType

class SnapToolbar(context: Context) : FrameLayout(context) {

    var onSnapRequested: ((SnapZone) -> Unit)? = null
    var onHalfCycle:     ((Rect) -> Unit)? = null
    var onConfirm:       (() -> Unit)? = null
    var onCancel:        (() -> Unit)? = null

    private var snapZones: List<SnapZone> = emptyList()
    private var halfIndex = 0
    private val halfOrder = listOf(
        ZoneType.HALF_LEFT, ZoneType.HALF_RIGHT,
        ZoneType.HALF_TOP,  ZoneType.HALF_BOTTOM
    )

    init {
        setBackgroundColor(Color.argb(230, 0, 0, 0))
        val dp = context.resources.displayMetrics.density
        setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        buildLayout(dp)
    }

    private fun buildLayout(dp: Float) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val snapParam = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        row.addView(makeSnapButton("⊞", "Win")  { snapToType(ZoneType.WINDOW) }, snapParam)
        row.addView(makeSnapButton("◧", "Half") { cycleHalf() },                  snapParam)
        row.addView(makeSnapButton("⬜", "Edge") { snapToType(ZoneType.EDGE) },   snapParam)
        row.addView(makeSnapButton("⬛", "Full") { snapToType(ZoneType.FULL) },   snapParam)

        row.addView(View(context), LinearLayout.LayoutParams((8 * dp).toInt(), 1))
        row.addView(makeActionButton("✕", Color.parseColor("#C62828")) { onCancel?.invoke() })
        row.addView(View(context), LinearLayout.LayoutParams((8 * dp).toInt(), 1))
        row.addView(makeActionButton("✓", Color.parseColor("#2E7D32")) { onConfirm?.invoke() })

        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun makeSnapButton(icon: String, label: String, onClick: () -> Unit): LinearLayout {
        val dp = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            setBackgroundColor(Color.argb(60, 255, 255, 255))
            addView(TextView(context).apply {
                text = icon; textSize = 18f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = label; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(Color.argb(180, 255, 255, 255))
            })
            setOnClickListener { onClick() }
        }
    }

    private fun makeActionButton(icon: String, bg: Int, onClick: () -> Unit): TextView {
        val dp = context.resources.displayMetrics.density
        val size = (48 * dp).toInt()
        return TextView(context).apply {
            text = icon; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(bg)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setOnClickListener { onClick() }
        }
    }

    @MainThread
    fun setSnapZones(zones: List<SnapZone>) { snapZones = zones }

    @MainThread
    fun updateEdgeZones(zones: List<SnapZone>) {
        snapZones = snapZones.filter { it.type != ZoneType.EDGE } + zones
    }

    private fun snapToType(type: ZoneType) {
        val zone = snapZones.filter { it.type == type }
            .minByOrNull { it.rect.width() * it.rect.height() } ?: return
        onSnapRequested?.invoke(zone)
    }

    private fun cycleHalf() {
        val type = halfOrder[halfIndex % halfOrder.size]
        halfIndex++
        val zone = snapZones.firstOrNull { it.type == type } ?: return
        onHalfCycle?.invoke(zone.rect)
    }
}
