package com.artstudio.core

import org.junit.Assert.*
import org.junit.Test

class BrushStrokeTest {

    @Test
    fun testEmptyStrokeBoundingBox() {
        val stroke = BrushStroke("empty", 0xFF000000.toInt(), 10f)
        val box = stroke.computeBoundingBox()
        assertEquals(0f, box.left, 0.001f)
        assertEquals(0f, box.top, 0.001f)
        assertEquals(0f, box.right, 0.001f)
        assertEquals(0f, box.bottom, 0.001f)
    }

    @Test
    fun testSinglePointBoundingBox() {
        val stroke = BrushStroke("single", 0xFF000000.toInt(), 20f)
        stroke.points.add(StrokePoint(100f, 200f, 0.5f))

        val padding = 20f * 1.5f // 30f
        val box = stroke.computeBoundingBox()

        assertEquals(70f, box.left, 0.001f)
        assertEquals(170f, box.top, 0.001f)
        assertEquals(130f, box.right, 0.001f)
        assertEquals(230f, box.bottom, 0.001f)
    }

    @Test
    fun testMultiplePointsBoundingBox() {
        val stroke = BrushStroke("multi", 0xFFFF0000.toInt(), 10f)
        stroke.points.add(StrokePoint(50f, 50f))
        stroke.points.add(StrokePoint(150f, 80f))
        stroke.points.add(StrokePoint(200f, 300f))

        val padding = 10f * 1.5f // 15f
        val box = stroke.computeBoundingBox()

        assertEquals(35f, box.left, 0.001f)
        assertEquals(35f, box.top, 0.001f)
        assertEquals(215f, box.right, 0.001f)
        assertEquals(315f, box.bottom, 0.001f)
    }
}
