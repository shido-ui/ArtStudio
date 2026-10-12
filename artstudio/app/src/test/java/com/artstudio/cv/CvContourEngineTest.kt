package com.artstudio.cv

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

/**
 * Robustness and topological verification suite for [CvContourEngine].
 *
 * Verifies:
 * 1. Stack safety of iterative Ramer-Douglas-Peucker (RDP) on a 100,000-point contour.
 * 2. Boundary tracing around 1-pixel thin spurs without premature truncation.
 * 3. Topological traversal across touching strokes (figure-8 junctions) without cycles.
 * 4. Concentric holes (donut shapes) properly classified with outer isHole=false and inner isHole=true.
 */
class CvContourEngineTest {

    // -------------------------------------------------------------------------
    // 1. Stack-Safety Test with 100k-Point Contours
    // -------------------------------------------------------------------------
    @Test
    fun testRdpStackSafety_100kPoints() {
        val count = 100_000
        val points = ArrayList<CvPoint>(count)

        // Generate a complex polyline with 100k points: base linear trend with sinusoidal perturbation
        for (i in 0 until count) {
            val x = i.toFloat() * 0.1f
            val y = sin(i.toDouble() * 0.05).toFloat() * 10f + (if (i % 2 == 0) 0.5f else -0.5f)
            points.add(CvPoint(x, y))
        }

        val startTime = System.currentTimeMillis()

        // Recursive RDP would throw StackOverflowError on 100k points.
        // Iterative RDP must complete cleanly and safely.
        val simplified = CvContourEngine.rdpSimplify(points, epsilon = 1.0f)
        val elapsed = System.currentTimeMillis() - startTime

        assertNotNull("Simplified contour must not be null", simplified)
        assertTrue("Simplification must reduce point count", simplified.size < count)
        assertTrue("Simplified contour must retain essential structure", simplified.size > 10)
        assertEquals(points.first().x, simplified.first().x, 0.001f)
        assertEquals(points.first().y, simplified.first().y, 0.001f)
        assertTrue("100k RDP simplification must complete within reasonable time (< 3000ms)", elapsed < 3000L)
    }

    // -------------------------------------------------------------------------
    // 2. 1-Pixel Thin Spurs and Peninsulas
    // -------------------------------------------------------------------------
    @Test
    fun testContourTracingThinSpur_doesNotTruncatePrematurely() {
        val width = 60
        val height = 60
        val mask = IntArray(width * height)

        // Draw a 20x20 solid block from (10, 20) to (29, 39)
        for (y in 20..39) {
            for (x in 10..29) {
                mask[y * width + x] = 255
            }
        }

        // Draw a 1-pixel wide, 15-pixel long horizontal spur projecting out to the right:
        // from (30, 30) to (44, 30)
        for (x in 30..44) {
            mask[30 * width + x] = 255
        }

        val contours = CvContourEngine.extractContours(mask, width, height, minArea = 10f, rdpEpsilon = 1.0f)
        assertEquals("Must discover exactly 1 contour for the connected block with spur", 1, contours.size)

        val contour = contours.first()
        assertFalse("Block with spur is an outer contour, not a hole", contour.isHole)

        // The contour must reach the tip of the spur at x = 44
        val maxX = contour.points.maxOf { it.x }
        val minX = contour.points.minOf { it.x }
        assertEquals(44f, maxX, 0.5f)
        assertEquals(10f, minX, 0.5f)
        assertTrue("Area must enclose the main block (~400 px)", contour.area >= 300.0)
    }

    // -------------------------------------------------------------------------
    // 3. Touching Strokes & Figure-8 Junctions
    // -------------------------------------------------------------------------
    @Test
    fun testContourTracingTouchingStrokesFigure8_noInfiniteLoopAndCoversBothLobes() {
        val width = 50
        val height = 50
        val mask = IntArray(width * height)

        // Square A: (5, 5) to (15, 15)
        for (y in 5..15) {
            for (x in 5..15) {
                mask[y * width + x] = 255
            }
        }

        // Square B: (15, 15) to (25, 25) touching at vertex (15, 15)
        for (y in 15..25) {
            for (x in 15..25) {
                mask[y * width + x] = 255
            }
        }

        val contours = CvContourEngine.extractContours(mask, width, height, minArea = 10f, rdpEpsilon = 1.0f)
        assertTrue("Must discover contour covering figure-8 junction", contours.isNotEmpty())

        val minX = contours.minOf { it.boundingBox.minX }
        val maxX = contours.maxOf { it.boundingBox.maxX }
        val minY = contours.minOf { it.boundingBox.minY }
        val maxY = contours.maxOf { it.boundingBox.maxY }

        assertEquals(5f, minX, 0.5f)
        assertEquals(25f, maxX, 0.5f)
        assertEquals(5f, minY, 0.5f)
        assertEquals(25f, maxY, 0.5f)
    }

    // -------------------------------------------------------------------------
    // 4. Concentric Holes (Donut Shapes)
    // -------------------------------------------------------------------------
    @Test
    fun testContourTracingConcentricHolesDonut_classifiesOuterAndInnerCorrectly() {
        val width = 60
        val height = 60
        val mask = IntArray(width * height)

        // Outer square from (10, 10) to (39, 39) - 30x30
        for (y in 10..39) {
            for (x in 10..39) {
                mask[y * width + x] = 255
            }
        }

        // Carve hole in the center from (20, 20) to (29, 29) - 10x10
        for (y in 20..29) {
            for (x in 20..29) {
                mask[y * width + x] = 0
            }
        }

        val contours = CvContourEngine.extractContours(mask, width, height, minArea = 10f, rdpEpsilon = 1.0f)
        assertEquals("Donut shape must discover exactly 2 contours: outer border and hole border", 2, contours.size)

        val outer = contours.find { !it.isHole }
        val hole = contours.find { it.isHole }

        assertNotNull("Outer border must be present", outer)
        assertNotNull("Hole border must be present", hole)

        // Outer bounding box is (10, 10, 39, 39)
        assertEquals(10f, outer!!.boundingBox.minX, 1.5f)
        assertEquals(10f, outer.boundingBox.minY, 1.5f)
        assertEquals(39f, outer.boundingBox.maxX, 1.5f)
        assertEquals(39f, outer.boundingBox.maxY, 1.5f)

        // Hole bounding box is approximately (20, 20, 29, 29)
        assertTrue("Hole minX should be near 20", hole!!.boundingBox.minX in 19f..21f)
        assertTrue("Hole maxX should be near 29", hole.boundingBox.maxX in 28f..30f)

        // Outer area should be substantially larger than hole area
        assertTrue("Outer area (${outer.area}) must exceed hole area (${hole.area})", outer.area > hole.area)
    }
}
