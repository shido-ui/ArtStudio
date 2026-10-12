package com.artstudio.autodraw.schedule

import com.artstudio.cv.CvPoint
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.vector.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite verifying deterministic drawing schedule synthesis,
 * ordering strategies, stroke direction optimization, dependency resolution,
 * timing monotonicity, and document persistence.
 */
class DrawingScheduleTest {

    private fun createSamplePath(
        id: String,
        layerId: String = "layer_1",
        start: CvPoint,
        end: CvPoint,
        length: Float = 100f,
        isClosed: Boolean = false,
        role: StrokeSemanticRole = StrokeSemanticRole.OUTLINE,
        tag: AnatomicalTag? = null,
        deps: Set<String> = emptySet(),
        userPriority: Int? = null
    ): SchedulablePath {
        return SchedulablePath(
            id = id,
            layerId = layerId,
            regionId = "region_main",
            startPoint = start,
            endPoint = end,
            pathLengthPx = length,
            isClosed = isClosed,
            isContour = isClosed || role == StrokeSemanticRole.OUTLINE,
            semanticRole = role,
            anatomicalTag = tag,
            brushParams = ScheduledBrushParams(sizePx = 3.0f),
            prerequisitePathIds = deps,
            userPriority = userPriority
        )
    }

    // -------------------------------------------------------------------------
    // 1. Deterministic Output & Hash Repeatability
    // -------------------------------------------------------------------------
    @Test
    fun testDeterministicScheduleHash_identicalInputsYieldIdenticalHash() {
        val paths = listOf(
            createSamplePath("p1", start = CvPoint(0f, 0f), end = CvPoint(50f, 0f), length = 50f),
            createSamplePath("p2", start = CvPoint(50f, 0f), end = CvPoint(100f, 0f), length = 50f),
            createSamplePath("p3", start = CvPoint(0f, 50f), end = CvPoint(50f, 50f), length = 50f)
        )
        val config = DrawingScheduleConfig(orderingStrategy = ScheduleOrderingStrategy.CONTOUR_FIRST)

        val sched1 = DrawingScheduleGenerator.generateSchedule(paths, config, "doc_1")
        val sched2 = DrawingScheduleGenerator.generateSchedule(paths, config, "doc_1")

        assertEquals(sched1.scheduleHash, sched2.scheduleHash)
        assertEquals(sched1.entries.size, sched2.entries.size)
        for (i in sched1.entries.indices) {
            val e1 = sched1.entries[i]
            val e2 = sched2.entries[i]
            assertEquals(e1.pathId, e2.pathId)
            assertEquals(e1.direction, e2.direction)
            assertEquals(e1.timing.startTimestampMs, e2.timing.startTimestampMs)
            assertEquals(e1.timing.estimatedDurationMs, e2.timing.estimatedDurationMs)
        }
        assertTrue(sched1.scheduleHash.isNotBlank())
    }

    // -------------------------------------------------------------------------
    // 2. Contour-First Ordering Strategy
    // -------------------------------------------------------------------------
    @Test
    fun testContourFirstOrdering_outlinesScheduledBeforeInternalDetails() {
        val paths = listOf(
            createSamplePath("hatching_1", role = StrokeSemanticRole.HATCHING, start = CvPoint(20f, 20f), end = CvPoint(30f, 30f)),
            createSamplePath("detail_1", role = StrokeSemanticRole.INTERNAL_DETAIL, start = CvPoint(10f, 10f), end = CvPoint(15f, 15f)),
            createSamplePath("outline_1", role = StrokeSemanticRole.OUTLINE, start = CvPoint(0f, 0f), end = CvPoint(100f, 100f)),
            createSamplePath("shadow_1", role = StrokeSemanticRole.SHADOW_ACCENT, start = CvPoint(25f, 25f), end = CvPoint(35f, 35f))
        )
        val config = DrawingScheduleConfig(orderingStrategy = ScheduleOrderingStrategy.CONTOUR_FIRST)
        val schedule = DrawingScheduleGenerator.generateSchedule(paths, config)

        val orderedRoles = schedule.entries.map { it.semanticRole }
        assertEquals(StrokeSemanticRole.OUTLINE, orderedRoles[0])
        val outlineIndex = schedule.entries.indexOfFirst { it.semanticRole == StrokeSemanticRole.OUTLINE }
        val detailIndex = schedule.entries.indexOfFirst { it.semanticRole == StrokeSemanticRole.INTERNAL_DETAIL }
        val hatchingIndex = schedule.entries.indexOfFirst { it.semanticRole == StrokeSemanticRole.HATCHING }
        val shadowIndex = schedule.entries.indexOfFirst { it.semanticRole == StrokeSemanticRole.SHADOW_ACCENT }

        assertTrue("Outline must precede detail", outlineIndex < detailIndex)
        assertTrue("Detail must precede hatching", detailIndex < hatchingIndex)
        assertTrue("Hatching must precede shadow accent", hatchingIndex < shadowIndex)
    }

    // -------------------------------------------------------------------------
    // 3. Layer-By-Layer Ordering Strategy
    // -------------------------------------------------------------------------
    @Test
    fun testLayerByLayerOrdering_lowerLayersPrecedeUpperLayers() {
        val paths = listOf(
            createSamplePath("fg_1", layerId = "layer_foreground", start = CvPoint(10f, 10f), end = CvPoint(20f, 20f)),
            createSamplePath("bg_1", layerId = "layer_background", start = CvPoint(0f, 0f), end = CvPoint(100f, 100f)),
            createSamplePath("mid_1", layerId = "layer_character", start = CvPoint(50f, 50f), end = CvPoint(60f, 60f)),
            createSamplePath("bg_2", layerId = "layer_background", start = CvPoint(5f, 5f), end = CvPoint(95f, 95f))
        )
        val config = DrawingScheduleConfig(orderingStrategy = ScheduleOrderingStrategy.LAYER_BY_LAYER)
        val schedule = DrawingScheduleGenerator.generateSchedule(paths, config)

        val scheduledLayers = schedule.entries.map { it.layerId }
        val lastBgIndex = scheduledLayers.lastIndexOf("layer_background")
        val firstMidIndex = scheduledLayers.indexOf("layer_character")
        val firstFgIndex = scheduledLayers.indexOf("layer_foreground")

        assertTrue("All background strokes must precede character layer", lastBgIndex < firstMidIndex)
        assertTrue("Character layer must precede foreground layer", firstMidIndex < firstFgIndex)
    }

    // -------------------------------------------------------------------------
    // 4. Spatially Efficient Ordering
    // -------------------------------------------------------------------------
    @Test
    fun testSpatiallyEfficientOrdering_penTravelDistanceOptimized() {
        val paths = listOf(
            createSamplePath("start_p", start = CvPoint(0f, 0f), end = CvPoint(10f, 0f), length = 10f),
            createSamplePath("far_p", start = CvPoint(500f, 500f), end = CvPoint(510f, 500f), length = 10f),
            createSamplePath("near_p", start = CvPoint(12f, 0f), end = CvPoint(20f, 0f), length = 8f)
        )
        val config = DrawingScheduleConfig(orderingStrategy = ScheduleOrderingStrategy.SPATIALLY_EFFICIENT)
        val schedule = DrawingScheduleGenerator.generateSchedule(paths, config)

        // Starting at (0, 0) -> start_p ends at (10, 0). Next closest is near_p at (12, 0)
        assertEquals("start_p", schedule.entries[0].pathId)
        assertEquals("near_p", schedule.entries[1].pathId)
        assertEquals("far_p", schedule.entries[2].pathId)
    }

    // -------------------------------------------------------------------------
    // 5. Anatomical Ordering Strategy
    // -------------------------------------------------------------------------
    @Test
    fun testAnatomicalOrdering_headAndFacePrecedeTorsoAndLimbs() {
        val paths = listOf(
            createSamplePath("limbs_1", tag = AnatomicalTag.LIMBS, start = CvPoint(10f, 100f), end = CvPoint(20f, 100f)),
            createSamplePath("eyes_1", tag = AnatomicalTag.EYES, start = CvPoint(10f, 20f), end = CvPoint(20f, 20f)),
            createSamplePath("head_1", tag = AnatomicalTag.HEAD, start = CvPoint(0f, 0f), end = CvPoint(50f, 50f)),
            createSamplePath("bg_1", tag = AnatomicalTag.BACKGROUND, start = CvPoint(0f, 200f), end = CvPoint(200f, 200f)),
            createSamplePath("torso_1", tag = AnatomicalTag.TORSO, start = CvPoint(10f, 60f), end = CvPoint(30f, 60f))
        )
        val config = DrawingScheduleConfig(orderingStrategy = ScheduleOrderingStrategy.ANATOMICAL)
        val schedule = DrawingScheduleGenerator.generateSchedule(paths, config)

        val tags = schedule.entries.mapNotNull { it.anatomicalTag }
        assertEquals(AnatomicalTag.HEAD, tags[0])
        assertEquals(AnatomicalTag.EYES, tags[1])
        assertEquals(AnatomicalTag.TORSO, tags[2])
        assertEquals(AnatomicalTag.LIMBS, tags[3])
        assertEquals(AnatomicalTag.BACKGROUND, tags[4])
    }

    // -------------------------------------------------------------------------
    // 6. Direction Reversal Optimization
    // -------------------------------------------------------------------------
    @Test
    fun testStrokeDirectionOptimization_openPathReversesWhenCloser() {
        // Path 1 starts at (0, 0) and ends at (10, 10).
        // Path 2 has start at (100, 100) and end at (11, 10).
        // Drawing Path 2 in REVERSE starts at (11, 10), which is only 1px away from Path 1's end!
        val p1 = createSamplePath("p1", start = CvPoint(0f, 0f), end = CvPoint(10f, 10f), isClosed = false)
        val p2 = createSamplePath("p2", start = CvPoint(100f, 100f), end = CvPoint(11f, 10f), isClosed = false)

        val config = DrawingScheduleConfig(
            orderingStrategy = ScheduleOrderingStrategy.SPATIALLY_EFFICIENT,
            allowDirectionReversal = true
        )
        val schedule = DrawingScheduleGenerator.generateSchedule(listOf(p1, p2), config)

        assertEquals("p1", schedule.entries[0].pathId)
        assertEquals(StrokeDirection.FORWARD, schedule.entries[0].direction)

        assertEquals("p2", schedule.entries[1].pathId)
        assertEquals(StrokeDirection.REVERSE, schedule.entries[1].direction)
        assertEquals(11f, schedule.entries[1].startPoint.x, 0.01f)
        assertEquals(10f, schedule.entries[1].startPoint.y, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 7. Closed Loop Path Direction Invariant
    // -------------------------------------------------------------------------
    @Test
    fun testClosedLoopPath_neverReversed() {
        val p1 = createSamplePath("p1", start = CvPoint(0f, 0f), end = CvPoint(10f, 10f), isClosed = false)
        // Closed loop has end closer to p1's end, but must NOT be reversed
        val pClosed = createSamplePath("pClosed", start = CvPoint(50f, 50f), end = CvPoint(11f, 10f), isClosed = true)

        val config = DrawingScheduleConfig(allowDirectionReversal = true)
        val schedule = DrawingScheduleGenerator.generateSchedule(listOf(p1, pClosed), config)

        val closedEntry = schedule.entries.find { it.pathId == "pClosed" }!!
        assertEquals(StrokeDirection.FORWARD, closedEntry.direction)
        assertTrue(closedEntry.isClosed)
    }

    // -------------------------------------------------------------------------
    // 8. Explicit User Priority Overrides
    // -------------------------------------------------------------------------
    @Test
    fun testUserPriorityOverrides_supersedesDefaultStrategy() {
        // Normally, outline would be drawn before hatching
        val hatching = createSamplePath("p_hatch", role = StrokeSemanticRole.HATCHING, start = CvPoint(0f, 0f), end = CvPoint(10f, 10f))
        val outline = createSamplePath("p_outline", role = StrokeSemanticRole.OUTLINE, start = CvPoint(50f, 50f), end = CvPoint(100f, 100f))

        // User explicitly marks p_hatch with highest priority (priority index 0)
        val config = DrawingScheduleConfig(
            orderingStrategy = ScheduleOrderingStrategy.CONTOUR_FIRST,
            userPriorityOverrides = mapOf("p_hatch" to 0, "p_outline" to 10)
        )
        val schedule = DrawingScheduleGenerator.generateSchedule(listOf(outline, hatching), config)

        assertEquals("p_hatch", schedule.entries[0].pathId)
        assertEquals("p_outline", schedule.entries[1].pathId)
    }

    // -------------------------------------------------------------------------
    // 9. Topological Dependency Satisfaction
    // -------------------------------------------------------------------------
    @Test
    fun testTopologicalDependencies_noEntryStartsBeforePrerequisites() {
        // p3 depends on p2, p2 depends on p1
        val p3 = createSamplePath("p3", start = CvPoint(0f, 0f), end = CvPoint(10f, 0f), deps = setOf("p2"))
        val p2 = createSamplePath("p2", start = CvPoint(10f, 0f), end = CvPoint(20f, 0f), deps = setOf("p1"))
        val p1 = createSamplePath("p1", start = CvPoint(20f, 0f), end = CvPoint(30f, 0f))

        val config = DrawingScheduleConfig(orderingStrategy = ScheduleOrderingStrategy.SPATIALLY_EFFICIENT)
        val schedule = DrawingScheduleGenerator.generateSchedule(listOf(p3, p2, p1), config)

        val order = schedule.entries.map { it.pathId }
        val idx1 = order.indexOf("p1")
        val idx2 = order.indexOf("p2")
        val idx3 = order.indexOf("p3")

        assertTrue("p1 must precede p2", idx1 < idx2)
        assertTrue("p2 must precede p3", idx2 < idx3)
    }

    // -------------------------------------------------------------------------
    // 10. Single Pen Cursor Timeline Monotonicity
    // -------------------------------------------------------------------------
    @Test
    fun testSinglePenCursorTimeline_monotonicityAndNonOverlapping() {
        val paths = (1..6).map { i ->
            createSamplePath("p_$i", start = CvPoint(i * 10f, 0f), end = CvPoint(i * 10f + 5f, 0f), length = 50f)
        }
        val config = DrawingScheduleConfig(nominalPenSpeedPxPerSec = 200f, penLiftSpeedPxPerSec = 800f)
        val schedule = DrawingScheduleGenerator.generateSchedule(paths, config)

        for (i in 0 until schedule.entries.size - 1) {
            val curr = schedule.entries[i]
            val next = schedule.entries[i + 1]
            assertTrue(
                "Entry ${next.sequenceIndex} start (${next.timing.startTimestampMs}) must be >= entry ${curr.sequenceIndex} end (${curr.timing.endTimestampMs})",
                next.timing.startTimestampMs >= curr.timing.endTimestampMs
            )
        }
        assertEquals(schedule.entries.last().timing.endTimestampMs, schedule.totalDurationMs)
    }

    // -------------------------------------------------------------------------
    // 11. Randomization Toggle & Seed Reproducibility
    // -------------------------------------------------------------------------
    @Test
    fun testRandomizationToggle_producesVariedOrdersWithDifferentSeeds() {
        val paths = (1..10).map { i ->
            createSamplePath("p_$i", start = CvPoint(i * 20f, i * 10f), end = CvPoint(i * 20f + 10f, i * 10f), length = 30f)
        }
        val configRandomA = DrawingScheduleConfig(randomizeOrder = true, seed = 12345L)
        val configRandomB = DrawingScheduleConfig(randomizeOrder = true, seed = 67890L)
        val configRandomC = DrawingScheduleConfig(randomizeOrder = true, seed = 12345L)

        val schedA = DrawingScheduleGenerator.generateSchedule(paths, configRandomA)
        val schedB = DrawingScheduleGenerator.generateSchedule(paths, configRandomB)
        val schedC = DrawingScheduleGenerator.generateSchedule(paths, configRandomC)

        // Same seed yields identical hash
        assertEquals(schedA.scheduleHash, schedC.scheduleHash)
        // Different seeds yield different orders/hashes
        assertNotEquals(schedA.scheduleHash, schedB.scheduleHash)
    }

    // -------------------------------------------------------------------------
    // 12. Non-Historical Fidelity Disclaimer & Document Round-Trip Persistence
    // -------------------------------------------------------------------------
    @Test
    fun testDocumentPersistenceRoundTrip_preservesScheduleAndDisclaimer() {
        val paths = listOf(
            createSamplePath("p1", start = CvPoint(10f, 10f), end = CvPoint(50f, 50f), length = 56.5f),
            createSamplePath("p2", start = CvPoint(50f, 50f), end = CvPoint(100f, 100f), length = 70.7f)
        )
        val schedule = DrawingScheduleGenerator.generateSchedule(paths, documentId = "doc_test_123")

        // Invariant check: Disclaimer must be explicitly present and truthful
        assertTrue(
            schedule.disclaimer.contains("does not claim to reproduce the original artist's real human stroke history")
        )

        val doc = ArtDocument(
            id = "doc_test_123",
            canvas = CanvasConfig(1080, 1920),
            drawingSchedule = schedule
        )

        val jsonStr = DocumentSerializer.toJsonString(doc)
        assertTrue(jsonStr.contains("\"drawingSchedule\""))
        assertTrue(jsonStr.contains("\"scheduleHash\": \"${schedule.scheduleHash}\""))
        assertTrue(jsonStr.contains("does not claim to reproduce"))

        val restoredDoc = DocumentSerializer.fromJsonString(jsonStr)
        assertNotNull(restoredDoc.drawingSchedule)
        val restoredSched = restoredDoc.drawingSchedule!!

        assertEquals(schedule.id, restoredSched.id)
        assertEquals(schedule.scheduleHash, restoredSched.scheduleHash)
        assertEquals(schedule.entries.size, restoredSched.entries.size)
        assertEquals(schedule.disclaimer, restoredSched.disclaimer)
        assertEquals(schedule.totalDurationMs, restoredSched.totalDurationMs)

        val e0Orig = schedule.entries[0]
        val e0Rest = restoredSched.entries[0]
        assertEquals(e0Orig.pathId, e0Rest.pathId)
        assertEquals(e0Orig.direction, e0Rest.direction)
        assertEquals(e0Orig.timing.startTimestampMs, e0Rest.timing.startTimestampMs)
    }

    // -------------------------------------------------------------------------
    // 13. Generation from Vector Layers in ArtDocument
    // -------------------------------------------------------------------------
    @Test
    fun testScheduleGenerationFromArtDocument() {
        val vo1 = VectorObject(
            id = "vo_outline",
            shapeType = VectorShapeType.BEZIER_PATH,
            anchors = listOf(
                VectorAnchor(0f, 0f),
                VectorAnchor(100f, 0f),
                VectorAnchor(100f, 100f)
            ),
            isClosed = true,
            style = VectorStyle(strokeColor = 0xFF000000L, strokeWidth = 3.0f),
            metadata = mapOf("semanticRole" to "OUTLINE")
        )
        val vo2 = VectorObject(
            id = "vo_hatch",
            shapeType = VectorShapeType.BEZIER_PATH,
            anchors = listOf(
                VectorAnchor(20f, 20f),
                VectorAnchor(40f, 40f)
            ),
            isClosed = false,
            style = VectorStyle(strokeColor = 0xFF333333L, strokeWidth = 1.5f),
            metadata = mapOf("semanticRole" to "HATCHING")
        )

        val vectorLayer = LayerNode.VectorPathLayer(
            id = "vec_layer_1",
            name = "Inked Linework",
            vectorObjects = listOf(vo1, vo2)
        )
        val doc = ArtDocument(
            id = "doc_vector_test",
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(vectorLayer)
            )
        )

        val schedule = DrawingScheduleGenerator.generateScheduleFromDocument(doc)
        assertEquals(2, schedule.entries.size)
        assertEquals("vo_outline", schedule.entries[0].pathId)
        assertTrue(schedule.entries[0].isClosed)
        assertEquals("vo_hatch", schedule.entries[1].pathId)
        assertFalse(schedule.entries[1].isClosed)
    }

    // -------------------------------------------------------------------------
    // 14. Deadlock / Circular Dependency Resilience
    // -------------------------------------------------------------------------
    @Test
    fun testDeadlockBreaker_resolvesCircularPrerequisites() {
        // pA depends on pB, and pB depends on pA (cycle)
        val pA = createSamplePath("pA", start = CvPoint(0f, 0f), end = CvPoint(10f, 0f), deps = setOf("pB"))
        val pB = createSamplePath("pB", start = CvPoint(10f, 0f), end = CvPoint(20f, 0f), deps = setOf("pA"))

        val schedule = DrawingScheduleGenerator.generateSchedule(listOf(pA, pB))
        // Must successfully schedule both without hanging or throwing
        assertEquals(2, schedule.entries.size)
        val pathIds = schedule.entries.map { it.pathId }.toSet()
        assertEquals(setOf("pA", "pB"), pathIds)
    }
}
