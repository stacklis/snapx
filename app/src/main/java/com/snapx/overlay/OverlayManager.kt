package com.snapx.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import com.snapx.snap.SnapEngine
import com.snapx.snap.SnapZone

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayRoot: FrameLayout? = null
    private var cropView: CropOverlayView? = null
    private var toolbar: SnapToolbar? = null

    fun show(
        displayBitmap: Bitmap,
        originalUri: Uri,
        engine: SnapEngine,
        staticZones: List<SnapZone>
    ) {
        if (overlayRoot != null) return

        val root = FrameLayout(context)

        val crop = CropOverlayView(context).apply {
            setBitmap(displayBitmap)
            setSnapZones(staticZones)
            setSnapEngine(engine)
            onConfirm = { rect ->
                dismiss()
                com.snapx.service.CropSaveService.start(context, originalUri, rect)
            }
            onCancel = { dismiss() }
        }

        val bar = SnapToolbar(context).apply {
            onSnapRequested = { zone -> crop.snapTo(zone) }
            onHalfCycle = { rect -> crop.setSelectionRect(rect) }
            onConfirm = { crop.confirmCrop() }
            onCancel = { dismiss() }
            setSnapZones(staticZones)
        }

        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )

        root.addView(crop)
        root.addView(bar, barParams)

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(root, wmParams)
        overlayRoot = root
        cropView = crop
        toolbar = bar
    }

    fun updateEdgeZones(zones: List<SnapZone>) {
        cropView?.updateEdgeZones(zones)
        toolbar?.updateEdgeZones(zones)
    }

    fun dismiss() {
        overlayRoot?.let { windowManager.removeView(it) }
        overlayRoot = null
        cropView = null
        toolbar = null
    }

    fun isShowing() = overlayRoot != null
}
