package com.snapx.snap

import android.graphics.Bitmap
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnapEngineTest {

    private lateinit var engine: SnapEngine

    @Before
    fun setup() {
        engine = SnapEngine(1080, 2400)
    }

    @Test
    fun `computeStaticZones includes FULL zone at screen bounds`() {
        val zones = engine.computeStaticZones(emptyList())
        val full = zones.first { it.type == ZoneType.FULL }
        assertEquals(Rect(0, 0, 1080, 2400), full.rect)
    }

    @Test
    fun `computeStaticZones includes all four HALF zones`() {
        val zones = engine.computeStaticZones(emptyList())
        val types = zones.map { it.type }.toSet()
        assertTrue(types.containsAll(setOf(
            ZoneType.HALF_LEFT, ZoneType.HALF_RIGHT,
            ZoneType.HALF_TOP, ZoneType.HALF_BOTTOM
        )))
    }

    @Test
    fun `HALF_LEFT covers left half of screen`() {
        val zones = engine.computeStaticZones(emptyList())
        val halfLeft = zones.first { it.type == ZoneType.HALF_LEFT }
        assertEquals(Rect(0, 0, 540, 2400), halfLeft.rect)
    }

    @Test
    fun `HALF_BOTTOM covers bottom half of screen`() {
        val zones = engine.computeStaticZones(emptyList())
        val halfBottom = zones.first { it.type == ZoneType.HALF_BOTTOM }
        assertEquals(Rect(0, 1200, 1080, 2400), halfBottom.rect)
    }

    @Test
    fun `computeStaticZones includes passed window bounds as WINDOW zones`() {
        val windowRect = Rect(0, 120, 1080, 2280)
        val zones = engine.computeStaticZones(listOf(windowRect))
        val win = zones.first { it.type == ZoneType.WINDOW }
        assertEquals(windowRect, win.rect)
    }

    @Test
    fun `WINDOW zones have higher priority than FULL`() {
        val zones = engine.computeStaticZones(listOf(Rect(0, 100, 1080, 2300)))
        val windowPriority = zones.first { it.type == ZoneType.WINDOW }.priority
        val fullPriority = zones.first { it.type == ZoneType.FULL }.priority
        assertTrue(windowPriority > fullPriority)
    }

    @Test
    fun `findNearestZone returns null when drag rect far from all zones`() {
        val zones = engine.computeStaticZones(emptyList())
        val dragRect = Rect(200, 300, 700, 1500)
        assertNull(engine.findNearestZone(dragRect, zones, 1f))
    }

    @Test
    fun `findNearestZone snaps to FULL when drag rect edges are within threshold`() {
        val zones = engine.computeStaticZones(emptyList())
        val nearFull = Rect(10, 10, 1070, 2390)
        val result = engine.findNearestZone(nearFull, zones, 1f)
        assertEquals(ZoneType.FULL, result?.type)
    }

    @Test
    fun `findNearestZone prefers WINDOW over FULL when both in threshold`() {
        val windowRect = Rect(0, 100, 1080, 2300)
        val zones = engine.computeStaticZones(listOf(windowRect))
        val dragRect = Rect(10, 110, 1070, 2290)
        val result = engine.findNearestZone(dragRect, zones, 1f)
        assertEquals(ZoneType.WINDOW, result?.type)
    }

    @Test
    fun `findNearestZone respects density scaling for threshold`() {
        val zones = engine.computeStaticZones(emptyList())
        val dragRect = Rect(50, 50, 1030, 2350)
        val result = engine.findNearestZone(dragRect, zones, 3f)
        assertEquals(ZoneType.FULL, result?.type)
    }

    @Test
    fun `findNearestZone does not snap when distance just outside threshold`() {
        val zones = engine.computeStaticZones(emptyList())
        val dragRect = Rect(25, 25, 1055, 2375)
        assertNull(engine.findNearestZone(dragRect, zones, 1f))
    }

    @Test
    fun `computeEdgeZones returns EDGE zone for bitmap with clear rectangular boundary`() {
        // White rect on black background — strong edges at the rect border
        val bmp = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.BLACK)
        // Draw a white rectangle in the center with a clear boundary
        for (y in 50 until 350) {
            for (x in 30 until 170) {
                bmp.setPixel(x, y, android.graphics.Color.WHITE)
            }
        }
        val zones = engine.computeEdgeZones(bmp)
        bmp.recycle()
        assertTrue("Expected at least one EDGE zone from a bitmap with clear rectangular boundary", zones.isNotEmpty())
        assertTrue("All returned zones should be of type EDGE", zones.all { it.type == ZoneType.EDGE })
    }
}
