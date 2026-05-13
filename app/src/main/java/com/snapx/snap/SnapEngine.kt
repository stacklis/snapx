package com.snapx.snap

import android.graphics.*
import kotlin.math.*

class SnapEngine(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    companion object {
        const val SNAP_THRESHOLD_DP = 24f
        const val EDGE_DETECT_MAX_WIDTH = 400f
        const val MAX_EDGE_ZONES = 1  // current impl returns bounding rect of all strong edges
    }

    fun computeStaticZones(windowBounds: List<Rect>): List<SnapZone> {
        val zones = mutableListOf<SnapZone>()

        zones += SnapZone(ZoneType.FULL, Rect(0, 0, screenWidth, screenHeight), "Full screen", 1)
        zones += SnapZone(ZoneType.HALF_LEFT,   Rect(0, 0, screenWidth / 2, screenHeight), "Left half", 2)
        zones += SnapZone(ZoneType.HALF_RIGHT,  Rect(screenWidth / 2, 0, screenWidth, screenHeight), "Right half", 2)
        zones += SnapZone(ZoneType.HALF_TOP,    Rect(0, 0, screenWidth, screenHeight / 2), "Top half", 2)
        zones += SnapZone(ZoneType.HALF_BOTTOM, Rect(0, screenHeight / 2, screenWidth, screenHeight), "Bottom half", 2)

        windowBounds.forEach { rect ->
            zones += SnapZone(ZoneType.WINDOW, Rect(rect), "App window", 4)
        }

        return zones
    }

    fun computeEdgeZones(bitmap: Bitmap): List<SnapZone> {
        val scale = minOf(1f, EDGE_DETECT_MAX_WIDTH / bitmap.width)
        val scaledW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, false)

        val edges = sobelEdges(scaled)
        scaled.recycle()

        return findRectCandidates(edges, scaledW, scaledH, scale)
            .take(MAX_EDGE_ZONES)
            .map { rect -> SnapZone(ZoneType.EDGE, rect, "Detected edge", 3) }
    }

    fun findNearestZone(dragRect: Rect, zones: List<SnapZone>, densityDp: Float): SnapZone? {
        val threshold = (SNAP_THRESHOLD_DP * densityDp).toInt()
        return zones
            .filter { minEdgeDistance(dragRect, it.rect) < threshold }
            .minWithOrNull(compareBy({ totalEdgeDistance(dragRect, it.rect) }, { -it.priority }))
    }

    private fun minEdgeDistance(a: Rect, b: Rect): Int = minOf(
        abs(a.left - b.left),
        abs(a.top - b.top),
        abs(a.right - b.right),
        abs(a.bottom - b.bottom)
    )

    private fun totalEdgeDistance(a: Rect, b: Rect): Int =
        abs(a.left - b.left) + abs(a.top - b.top) +
        abs(a.right - b.right) + abs(a.bottom - b.bottom)

    private fun sobelEdges(bitmap: Bitmap): Array<FloatArray> {
        val w = bitmap.width
        val h = bitmap.height
        val gray = Array(h) { y ->
            FloatArray(w) { x ->
                val p = bitmap.getPixel(x, y)
                (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)) / 255f
            }
        }
        return Array(h) { y ->
            FloatArray(w) { x ->
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) 0f
                else {
                    val gx = (-gray[y-1][x-1] + gray[y-1][x+1]
                            - 2*gray[y][x-1]   + 2*gray[y][x+1]
                            - gray[y+1][x-1]   + gray[y+1][x+1])
                    val gy = (-gray[y-1][x-1] - 2*gray[y-1][x] - gray[y-1][x+1]
                            +  gray[y+1][x-1] + 2*gray[y+1][x] + gray[y+1][x+1])
                    sqrt(gx * gx + gy * gy)
                }
            }
        }
    }

    private fun findRectCandidates(
        edges: Array<FloatArray>,
        w: Int,
        h: Int,
        scale: Float
    ): List<Rect> {
        val threshold = 0.25f
        val minFraction = 0.08f

        val rowStrong = BooleanArray(h) { y ->
            edges[y].count { it > threshold } > w * minFraction
        }
        val colStrong = BooleanArray(w) { x ->
            (0 until h).count { y -> edges[y][x] > threshold } > h * minFraction
        }

        var top = -1; var bottom = -1
        for (y in 0 until h) {
            if (rowStrong[y]) { if (top == -1) top = y; bottom = y }
        }
        var left = -1; var right = -1
        for (x in 0 until w) {
            if (colStrong[x]) { if (left == -1) left = x; right = x }
        }

        if (top == -1 || left == -1) return emptyList()

        val rect = Rect(
            (left / scale).toInt(),
            (top / scale).toInt(),
            (right / scale).toInt(),
            (bottom / scale).toInt()
        )
        return listOf(rect)
    }
}
