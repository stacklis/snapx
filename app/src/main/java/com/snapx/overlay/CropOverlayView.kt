package com.snapx.overlay

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import androidx.annotation.MainThread
import com.snapx.snap.SnapEngine
import com.snapx.snap.SnapZone
import com.snapx.snap.ZoneType
import kotlin.math.abs

class CropOverlayView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var snapZones: List<SnapZone> = emptyList()
    private var snapEngine: SnapEngine? = null
    private var activeSnapZone: SnapZone? = null

    var onConfirm: ((Rect) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val selectionRect = RectF()
    private var touchMode = TouchMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var density      = 1f
    private var cornerRadius = 0f
    private var edgeShort    = 0f
    private var edgeLong     = 0f
    private var touchSlop    = 0f

    private val dimPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val snapHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB74D")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val labelBgPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private enum class TouchMode {
        NONE, MOVE,
        RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR,
        RESIZE_LEFT, RESIZE_RIGHT, RESIZE_TOP, RESIZE_BOTTOM
    }

    @MainThread
    fun setBitmap(b: Bitmap) {
        bitmap = b
        if (width > 0 && height > 0) initSelection()
        invalidate()
    }

    @MainThread
    fun setSnapZones(zones: List<SnapZone>) {
        snapZones = zones
        invalidate()
    }

    @MainThread
    fun updateEdgeZones(zones: List<SnapZone>) {
        snapZones = snapZones.filter { it.type != ZoneType.EDGE } + zones
        invalidate()
    }

    fun setSnapEngine(engine: SnapEngine) { snapEngine = engine }

    @MainThread
    fun snapTo(zone: SnapZone) {
        selectionRect.set(RectF(zone.rect))
        clampSelection()
        invalidate()
    }

    @MainThread
    fun setSelectionRect(rect: Rect) {
        selectionRect.set(RectF(rect))
        clampSelection()
        invalidate()
    }

    @MainThread
    fun release() {
        bitmap = null
        invalidate()
    }

    @MainThread
    fun confirmCrop() {
        val bmp = bitmap ?: return
        val scaleX = bmp.width.toFloat() / width
        val scaleY = bmp.height.toFloat() / height
        val cropRect = Rect(
            (selectionRect.left   * scaleX).toInt(),
            (selectionRect.top    * scaleY).toInt(),
            (selectionRect.right  * scaleX).toInt(),
            (selectionRect.bottom * scaleY).toInt()
        )
        onConfirm?.invoke(cropRect)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        density      = resources.displayMetrics.density
        cornerRadius = 28f * density
        edgeShort    = 20f * density
        edgeLong     = 40f * density
        touchSlop    = 44f * density
        borderPaint.strokeWidth      = 2f * density
        handleBorderPaint.strokeWidth = 2f * density
        snapHintPaint.strokeWidth    = 1.5f * density
        labelPaint.textSize          = 12f * density
        if (bitmap != null && selectionRect.isEmpty) initSelection()
    }

    private fun initSelection() {
        val m = 0.15f
        selectionRect.set(width * m, height * m, width * (1f - m), height * (1f - m))
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return

        canvas.drawBitmap(bmp, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)

        val save = canvas.save()
        canvas.clipOutRect(selectionRect)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.restoreToCount(save)

        activeSnapZone?.let { zone ->
            val hintRect = RectF(zone.rect)
            canvas.drawRect(hintRect, snapHintPaint)
            val lx = hintRect.centerX()
            val ly = hintRect.top - 16f
            val lw = labelPaint.measureText(zone.label) + 24f
            canvas.drawRoundRect(lx - lw / 2, ly - 36f, lx + lw / 2, ly + 4f, 12f, 12f, labelBgPaint)
            canvas.drawText(zone.label, lx, ly - 8f, labelPaint)
        }

        canvas.drawRect(selectionRect, borderPaint)

        val pw = ((selectionRect.width() / width.toFloat()) * bmp.width).toInt()
        val ph = ((selectionRect.height() / height.toFloat()) * bmp.height).toInt()
        val label = "$pw × $ph"
        val lx = selectionRect.centerX()
        val ly = selectionRect.centerY()
        val lw = labelPaint.measureText(label) + 24f
        canvas.drawRoundRect(lx - lw / 2, ly - 22f, lx + lw / 2, ly + 22f, 11f, 11f, labelBgPaint)
        canvas.drawText(label, lx, ly + 8f, labelPaint)

        drawCorner(canvas, selectionRect.left,  selectionRect.top)
        drawCorner(canvas, selectionRect.right, selectionRect.top)
        drawCorner(canvas, selectionRect.left,  selectionRect.bottom)
        drawCorner(canvas, selectionRect.right, selectionRect.bottom)
        drawEdgeHandle(canvas, selectionRect.centerX(), selectionRect.top,    horizontal = true)
        drawEdgeHandle(canvas, selectionRect.centerX(), selectionRect.bottom, horizontal = true)
        drawEdgeHandle(canvas, selectionRect.left,  selectionRect.centerY(),  horizontal = false)
        drawEdgeHandle(canvas, selectionRect.right, selectionRect.centerY(),  horizontal = false)
    }

    private fun drawCorner(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, cornerRadius, handleFillPaint)
        canvas.drawCircle(x, y, cornerRadius, handleBorderPaint)
    }

    private fun drawEdgeHandle(canvas: Canvas, x: Float, y: Float, horizontal: Boolean) {
        val hw = if (horizontal) edgeLong / 2 else edgeShort / 2
        val hh = if (horizontal) edgeShort / 2 else edgeLong / 2
        canvas.drawRoundRect(x - hw, y - hh, x + hw, y + hh, 8f, 8f, handleFillPaint)
        canvas.drawRoundRect(x - hw, y - hh, x + hw, y + hh, 8f, 8f, handleBorderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchMode = detectTouchMode(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
                return touchMode != TouchMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                applyDrag(event.x - lastTouchX, event.y - lastTouchY)
                lastTouchX = event.x
                lastTouchY = event.y
                activeSnapZone = snapEngine?.findNearestZone(selectionRect.toIntRect(), snapZones, density)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                activeSnapZone?.let { snapTo(it); activeSnapZone = null }
                touchMode = TouchMode.NONE
                return true
            }
        }
        return false
    }

    private fun detectTouchMode(x: Float, y: Float): TouchMode {
        val sl = selectionRect.left;  val st = selectionRect.top
        val sr = selectionRect.right; val sb = selectionRect.bottom
        fun near(a: Float, b: Float) = abs(a - b) < touchSlop
        return when {
            near(x, sl) && near(y, st) -> TouchMode.RESIZE_TL
            near(x, sr) && near(y, st) -> TouchMode.RESIZE_TR
            near(x, sl) && near(y, sb) -> TouchMode.RESIZE_BL
            near(x, sr) && near(y, sb) -> TouchMode.RESIZE_BR
            near(x, sl) -> TouchMode.RESIZE_LEFT
            near(x, sr) -> TouchMode.RESIZE_RIGHT
            near(y, st) -> TouchMode.RESIZE_TOP
            near(y, sb) -> TouchMode.RESIZE_BOTTOM
            selectionRect.contains(x, y) -> TouchMode.MOVE
            else -> TouchMode.NONE
        }
    }

    private fun applyDrag(dx: Float, dy: Float) {
        when (touchMode) {
            TouchMode.MOVE        -> selectionRect.offset(dx, dy)
            TouchMode.RESIZE_TL   -> { selectionRect.left += dx;  selectionRect.top += dy }
            TouchMode.RESIZE_TR   -> { selectionRect.right += dx; selectionRect.top += dy }
            TouchMode.RESIZE_BL   -> { selectionRect.left += dx;  selectionRect.bottom += dy }
            TouchMode.RESIZE_BR   -> { selectionRect.right += dx; selectionRect.bottom += dy }
            TouchMode.RESIZE_LEFT   -> selectionRect.left   += dx
            TouchMode.RESIZE_RIGHT  -> selectionRect.right  += dx
            TouchMode.RESIZE_TOP    -> selectionRect.top    += dy
            TouchMode.RESIZE_BOTTOM -> selectionRect.bottom += dy
            else -> {}
        }
        clampSelection()
    }

    private fun clampSelection() {
        val minSize = 48f * density
        val w = width.toFloat(); val h = height.toFloat()
        selectionRect.left   = selectionRect.left.coerceIn(0f, selectionRect.right  - minSize)
        selectionRect.top    = selectionRect.top.coerceIn(0f,  selectionRect.bottom - minSize)
        selectionRect.right  = selectionRect.right.coerceIn(selectionRect.left + minSize, w)
        selectionRect.bottom = selectionRect.bottom.coerceIn(selectionRect.top + minSize, h)
    }

    private fun RectF.toIntRect() = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}
