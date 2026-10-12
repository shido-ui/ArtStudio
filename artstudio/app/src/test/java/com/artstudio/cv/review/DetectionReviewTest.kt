package com.artstudio.cv.review

import com.artstudio.cv.CvPoint
import com.artstudio.cv.CvRect
import com.artstudio.cv.pathfit.FittedPathCandidate
import com.artstudio.cv.pathfit.PathProvenance
import com.artstudio.cv.pathfit.SourcePixelMapping
import com.artstudio.cv.region.RegionMask
import com.artstudio.vector.VectorAnchor
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class DetectionReviewTest {

    private fun createTestPath(
        id: String,
        anchors: List<VectorAnchor>,
        confidence: Float = 0.95f,
        isClosed: Boolean = false
    ): FittedPathCandidate {
        val minX = anchors.minOfOrNull { it.x } ?: 0f
        val minY = anchors.minOfOrNull { it.y } ?: 0f
        val maxX = anchors.maxOfOrNull { it.x } ?: 10f
        val maxY = anchors.maxOfOrNull { it.y } ?: 10f
        return FittedPathCandidate(
            id = id,
            regionId = "region_1",
            isClosed = isClosed,
            anchors = anchors,
            sourceMapping = SourcePixelMapping(
                pixelCount = anchors.size * 5,
                boundingBox = CvRect(minX, minY, maxX, maxY),
                sampledPixels = anchors.map { CvPoint(it.x, it.y) }
            ),
            provenance = PathProvenance(),
            confidence = confidence,
            maxContourDeviation = 0.5f,
            meanContourDeviation = 0.2f
        )
    }

    private fun createTestRegion(
        id: String,
        width: Int = 100,
        height: Int = 100,
        rectX: Int = 20,
        rectY: Int = 20,
        rectW: Int = 30,
        rectH: Int = 30,
        confidence: Float = 0.95f
    ): RegionMask {
        val mask = ByteArray(width * height)
        for (y in rectY until (rectY + rectH).coerceAtMost(height)) {
            for (x in rectX until (rectX + rectW).coerceAtMost(width)) {
                mask[y * width + x] = 255.toByte()
            }
        }
        return RegionMask(
            regionId = id,
            width = width,
            height = height,
            mask = mask,
            confidence = confidence
        )
    }

    // -------------------------------------------------------------------------
    // 1. Move Path Correction
    // -------------------------------------------------------------------------
    @Test
    fun testMovePathCorrection() {
        val path = createTestPath(
            id = "path_1",
            anchors = listOf(VectorAnchor(10f, 10f), VectorAnchor(20f, 30f))
        )
        val moved = DetectionRepairEngine.movePath(path, 15f, 25f)

        assertEquals(25f, moved.anchors[0].x, 0.01f)
        assertEquals(35f, moved.anchors[0].y, 0.01f)
        assertEquals(35f, moved.anchors[1].x, 0.01f)
        assertEquals(55f, moved.anchors[1].y, 0.01f)
        assertEquals(25f, moved.sourceMapping.boundingBox.minX, 0.01f)
        assertEquals(35f, moved.sourceMapping.boundingBox.minY, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 2. Delete Path Correction
    // -------------------------------------------------------------------------
    @Test
    fun testDeletePathCorrection() {
        val path1 = createTestPath("path_1", listOf(VectorAnchor(0f, 0f), VectorAnchor(10f, 10f)))
        val path2 = createTestPath("path_2", listOf(VectorAnchor(20f, 20f), VectorAnchor(30f, 30f)))

        val session = DetectionReviewSession(
            width = 100,
            height = 100,
            sourcePixels = IntArray(100 * 100) { 0xFFFFFFFF.toInt() },
            initialPaths = listOf(path1, path2)
        )

        assertEquals(2, session.effectivePaths.size)
        session.applyCorrection(DetectionCorrection.deletePath("path_1"))
        assertEquals(1, session.effectivePaths.size)
        assertEquals("path_2", session.effectivePaths[0].id)
    }

    // -------------------------------------------------------------------------
    // 3. Split Path Correction
    // -------------------------------------------------------------------------
    @Test
    fun testSplitPathCorrection() {
        val path = createTestPath(
            id = "stroke_curve",
            anchors = listOf(
                VectorAnchor(0f, 0f),
                VectorAnchor(10f, 15f),
                VectorAnchor(20f, 25f),
                VectorAnchor(30f, 35f)
            )
        )
        val (part1, part2) = DetectionRepairEngine.splitPath(path, 2)

        assertEquals(3, part1.anchors.size)
        assertEquals(2, part2.anchors.size)
        assertEquals("stroke_curve_part1", part1.id)
        assertEquals("stroke_curve_part2", part2.id)
        assertFalse(part1.isClosed)
        assertFalse(part2.isClosed)
    }

    // -------------------------------------------------------------------------
    // 4. Merge Paths Correction
    // -------------------------------------------------------------------------
    @Test
    fun testMergePathsCorrection() {
        val p1 = createTestPath("p1", listOf(VectorAnchor(0f, 0f), VectorAnchor(10f, 10f)))
        val p2 = createTestPath("p2", listOf(VectorAnchor(12f, 12f), VectorAnchor(20f, 20f)))

        val merged = DetectionRepairEngine.mergePaths(p1, p2)
        assertEquals(4, merged.anchors.size)
        assertEquals("p1_merged_p2", merged.id)
        assertEquals(0f, merged.sourceMapping.boundingBox.minX, 0.01f)
        assertEquals(20f, merged.sourceMapping.boundingBox.maxX, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 5. Move Region Correction
    // -------------------------------------------------------------------------
    @Test
    fun testMoveRegionCorrection() {
        val reg = createTestRegion("reg_1", width = 100, height = 100, rectX = 20, rectY = 20, rectW = 10, rectH = 10)
        assertTrue(reg.contains(25, 25))
        assertFalse(reg.contains(35, 35))

        val moved = DetectionRepairEngine.moveRegion(reg, dx = 10, dy = 10)
        assertFalse(moved.contains(25, 25))
        assertTrue(moved.contains(35, 35))
    }

    // -------------------------------------------------------------------------
    // 6. Delete Region Correction
    // -------------------------------------------------------------------------
    @Test
    fun testDeleteRegionCorrection() {
        val r1 = createTestRegion("reg_1", width = 50, height = 50, rectX = 5, rectY = 5, rectW = 10, rectH = 10)
        val r2 = createTestRegion("reg_2", width = 50, height = 50, rectX = 25, rectY = 25, rectW = 10, rectH = 10)

        val session = DetectionReviewSession(
            width = 50,
            height = 50,
            sourcePixels = IntArray(50 * 50) { 0xFFFFFFFF.toInt() },
            initialRegions = listOf(r1, r2)
        )

        assertEquals(2, session.effectiveRegions.size)
        session.applyCorrection(DetectionCorrection.deleteRegion("reg_1"))
        assertEquals(1, session.effectiveRegions.size)
        assertEquals("reg_2", session.effectiveRegions[0].regionId)
    }

    // -------------------------------------------------------------------------
    // 7. Split Region Correction
    // -------------------------------------------------------------------------
    @Test
    fun testSplitRegionCorrection() {
        val reg = createTestRegion("fill_body", width = 100, height = 100, rectX = 10, rectY = 10, rectW = 40, rectH = 40)
        val (left, right) = DetectionRepairEngine.splitRegion(reg, splitCoord = 25, isVertical = true)

        assertTrue(left.contains(15, 20))
        assertFalse(left.contains(35, 20))

        assertFalse(right.contains(15, 20))
        assertTrue(right.contains(35, 20))
    }

    // -------------------------------------------------------------------------
    // 8. Merge Regions Correction
    // -------------------------------------------------------------------------
    @Test
    fun testMergeRegionsCorrection() {
        val r1 = createTestRegion("reg_a", width = 50, height = 50, rectX = 5, rectY = 5, rectW = 10, rectH = 10)
        val r2 = createTestRegion("reg_b", width = 50, height = 50, rectX = 20, rectY = 20, rectW = 10, rectH = 10)

        val merged = DetectionRepairEngine.mergeRegions(r1, r2)
        assertEquals("reg_a_merged_reg_b", merged.regionId)
        assertTrue(merged.contains(8, 8))
        assertTrue(merged.contains(25, 25))
    }

    // -------------------------------------------------------------------------
    // 9. Bridge Gap Correction
    // -------------------------------------------------------------------------
    @Test
    fun testBridgeGapCorrection() {
        val reg = createTestRegion("reg_leaky", width = 50, height = 50, rectX = 0, rectY = 0, rectW = 0, rectH = 0)
        assertFalse(reg.contains(15, 15))

        val bridged = DetectionRepairEngine.bridgeGap(
            region = reg,
            start = CvPoint(10f, 10f),
            end = CvPoint(20f, 20f),
            thickness = 3
        )
        assertTrue(bridged.contains(10, 10))
        assertTrue(bridged.contains(15, 15))
        assertTrue(bridged.contains(20, 20))
    }

    // -------------------------------------------------------------------------
    // 10. Reanalysis Preserves Valid Corrections and Regions
    // -------------------------------------------------------------------------
    @Test
    fun testReanalysisPreservesValidCorrectionsAndRegions() {
        val p1 = createTestPath("path_10", listOf(VectorAnchor(10f, 10f), VectorAnchor(20f, 20f)))
        val session = DetectionReviewSession(
            width = 100,
            height = 100,
            sourcePixels = IntArray(100 * 100) { 0xFFFFFFFF.toInt() },
            initialPaths = listOf(p1)
        )

        // Apply a move correction
        session.applyCorrection(DetectionCorrection.movePath("path_10", 5f, 5f))
        assertEquals(15f, session.effectivePaths[0].anchors[0].x, 0.01f)

        // Rerun reanalysis with updated detection preserving identity
        val p1Fresh = createTestPath("path_10", listOf(VectorAnchor(10f, 10f), VectorAnchor(22f, 22f)))
        val conflicts = session.reanalyzeFull(listOf(p1Fresh), emptyList())

        assertTrue("Expected 0 conflicts when identity still maps", conflicts.isEmpty())
        assertEquals(1, session.effectivePaths.size)
        // Correction must remain applied
        assertEquals(15f, session.effectivePaths[0].anchors[0].x, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 11. Identity Mismatch Raises Visible Conflict (Regression Test)
    // -------------------------------------------------------------------------
    @Test
    fun testIdentityMismatchRaisesVisibleConflict() {
        val p1 = createTestPath("path_a", listOf(VectorAnchor(10f, 10f), VectorAnchor(20f, 20f)))
        val session = DetectionReviewSession(
            width = 100,
            height = 100,
            sourcePixels = IntArray(100 * 100) { 0xFFFFFFFF.toInt() },
            initialPaths = listOf(p1)
        )

        // Artist moved path_a
        session.applyCorrection(DetectionCorrection.movePath("path_a", 10f, 10f))

        // Reanalysis runs: path_a is gone, completely new path_b is detected instead
        val p2 = createTestPath("path_b", listOf(VectorAnchor(80f, 80f), VectorAnchor(90f, 90f)))
        val conflicts = session.reanalyzeFull(listOf(p2), emptyList())

        // Must NOT silently discard correction; must raise visible conflict!
        assertEquals(1, conflicts.size)
        assertEquals(ConflictReason.TARGET_DISAPPEARED, conflicts[0].reason)
        assertEquals("path_a", conflicts[0].correction.targetId)
        assertFalse(conflicts[0].isResolved)
        assertEquals(1, session.conflicts.size)
    }

    // -------------------------------------------------------------------------
    // 12. Conflict Resolution Strategies
    // -------------------------------------------------------------------------
    @Test
    fun testConflictResolutionStrategies() {
        val p1 = createTestPath("path_x", listOf(VectorAnchor(10f, 10f), VectorAnchor(20f, 20f)))
        val session = DetectionReviewSession(
            width = 100,
            height = 100,
            sourcePixels = IntArray(100 * 100) { 0xFFFFFFFF.toInt() },
            initialPaths = listOf(p1)
        )

        session.applyCorrection(DetectionCorrection.movePath("path_x", 15f, 15f))

        // Reanalysis creates candidate path_x_refined
        val pCand = createTestPath("path_x_refined", listOf(VectorAnchor(10f, 10f), VectorAnchor(22f, 22f)))
        session.reanalyzeFull(listOf(pCand), emptyList())

        val conflict = session.conflicts.first()
        assertFalse(conflict.isResolved)

        // Artist chooses to reapply to candidate
        val resolved = session.resolveConflict(
            conflictId = conflict.id,
            strategy = ConflictResolutionStrategy.REAPPLY_CLOSEST_MATCH,
            chosenTargetId = "path_x_refined"
        )
        assertTrue(resolved)
        assertTrue(session.conflicts.first().isResolved)
        assertEquals(25f, session.effectivePaths[0].anchors[0].x, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 13. Targeted Reanalysis (Current Region) Latency
    // -------------------------------------------------------------------------
    @Test
    fun testTargetedReanalysisCurrentRegionLatency() {
        val w = 200
        val h = 200
        val pixels = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        // Draw a black square at (20, 20) to (50, 50)
        for (y in 20..50) {
            for (x in 20..50) {
                pixels[y * w + x] = 0xFF000000.toInt()
            }
        }

        val reg = createTestRegion("square_region", width = w, height = h, rectX = 20, rectY = 20, rectW = 30, rectH = 30)
        val session = DetectionReviewSession(
            width = w,
            height = h,
            sourcePixels = pixels,
            initialRegions = listOf(reg)
        )

        val result = session.reanalyzeCurrentRegion("square_region")
        assertNotNull(result.subPatchBounds)
        assertTrue("Subpatch pixels must be smaller than full canvas", result.pixelsAnalyzed < w * h)
        assertTrue("Targeted latency should be small", result.elapsedMs < 2000L)
        assertTrue("Speedup ratio should be positive", result.speedupRatio >= 1.0)
    }

    // -------------------------------------------------------------------------
    // 14. Targeted Reanalysis (Selection) Preserves Outside Entities
    // -------------------------------------------------------------------------
    @Test
    fun testTargetedReanalysisSelectionPreservesOutside() {
        val w = 100
        val h = 100
        val pixels = IntArray(w * h) { 0xFFFFFFFF.toInt() }

        // Far outside path at (80, 80)
        val outsidePath = createTestPath("outside_path", listOf(VectorAnchor(80f, 80f), VectorAnchor(90f, 90f)))
        val outsideRegion = createTestRegion("outside_region", width = w, height = h, rectX = 75, rectY = 75, rectW = 15, rectH = 15)

        val session = DetectionReviewSession(
            width = w,
            height = h,
            sourcePixels = pixels,
            initialPaths = listOf(outsidePath),
            initialRegions = listOf(outsideRegion)
        )

        // Selection mask in top-left (10, 10) to (30, 30)
        val selMask = ByteArray(w * h)
        for (y in 10..30) {
            for (x in 10..30) selMask[y * w + x] = 255.toByte()
        }

        val result = session.reanalyzeSelection(selMask)
        assertTrue(result.updatedPaths.any { it.id == "outside_path" })
        assertTrue(result.updatedRegions.any { it.regionId == "outside_region" })
    }

    // -------------------------------------------------------------------------
    // 15. Review Session Serialization Round-Trip
    // -------------------------------------------------------------------------
    @Test
    fun testReviewSessionSerializationRoundTrip() {
        val p = createTestPath("path_persisted", listOf(VectorAnchor(10f, 10f), VectorAnchor(30f, 30f)))
        val r = createTestRegion("reg_persisted", width = 80, height = 80, rectX = 10, rectY = 10, rectW = 20, rectH = 20)

        val session = DetectionReviewSession(
            width = 80,
            height = 80,
            sourcePixels = IntArray(80 * 80) { 0xFFFFFFFF.toInt() },
            initialPaths = listOf(p),
            initialRegions = listOf(r)
        )

        session.overlayMode = OverlayDisplayMode.DIFFERENCE_HEATMAP
        session.splitPosition = 0.65f
        session.blendAlpha = 0.8f
        session.applyCorrection(DetectionCorrection.movePath("path_persisted", 8f, 12f))

        val json = DetectionReviewSerializer.toJson(session)
        assertTrue(json.contains("path_persisted"))
        assertTrue(json.contains("DIFFERENCE_HEATMAP"))

        val restored = DetectionReviewSerializer.fromJson(
            json = json,
            sourcePixels = session.sourcePixels,
            initialPaths = listOf(p),
            initialRegions = listOf(r)
        )

        assertEquals(OverlayDisplayMode.DIFFERENCE_HEATMAP, restored.overlayMode)
        assertEquals(0.65f, restored.splitPosition, 0.01f)
        assertEquals(0.8f, restored.blendAlpha, 0.01f)
        assertEquals(1, restored.corrections.size)
        assertEquals(18f, restored.effectivePaths[0].anchors[0].x, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 16. Overlay Modes Rendering
    // -------------------------------------------------------------------------
    @Test
    fun testOverlayModesRendering() {
        val w = 50
        val h = 50
        val pixels = IntArray(w * h) { 0xFF123456.toInt() }
        val p = createTestPath("p", listOf(VectorAnchor(5f, 5f), VectorAnchor(25f, 25f)))
        val r = createTestRegion("r", width = w, height = h, rectX = 10, rectY = 10, rectW = 10, rectH = 10)

        for (mode in OverlayDisplayMode.entries) {
            val rendered = DetectionOverlayRenderer.render(
                sourcePixels = pixels,
                width = w,
                height = h,
                paths = listOf(p),
                regions = listOf(r),
                mode = mode,
                splitRatio = 0.5f,
                blendAlpha = 0.5f
            )
            assertNotNull("Rendered buffer for $mode must not be null", rendered)
            assertEquals(w * h, rendered.size)
        }
    }
}
