package com.artstudio.autodraw.schedule

import com.artstudio.cv.CvPoint
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import com.artstudio.vector.VectorObject
import java.util.UUID
import kotlin.math.*

/**
 * Intermediate schedulable path descriptor adapting vector objects,
 * centreline paths, or fitted contour candidates for drawing schedule synthesis.
 */
data class SchedulablePath(
    val id: String,
    val layerId: String,
    val regionId: String? = null,
    val startPoint: CvPoint,
    val endPoint: CvPoint,
    val waypoints: List<CvPoint> = emptyList(),
    val pathLengthPx: Float,
    val isClosed: Boolean = false,
    val isContour: Boolean = false,
    val semanticRole: StrokeSemanticRole = StrokeSemanticRole.OUTLINE,
    val anatomicalTag: AnatomicalTag? = null,
    val brushParams: ScheduledBrushParams = ScheduledBrushParams(),
    val prerequisitePathIds: Set<String> = emptySet(),
    val userPriority: Int? = null
) {
    init {
        require(pathLengthPx >= 0f) { "Path length must be non-negative, got $pathLengthPx" }
    }

    companion object {
        /**
         * Adapts an authoritative [VectorObject] into a [SchedulablePath].
         */
        fun fromVectorObject(
            vo: VectorObject,
            layerId: String,
            regionId: String? = null,
            userPriority: Int? = null
        ): SchedulablePath {
            val anchors = vo.anchors
            val start = if (anchors.isNotEmpty()) CvPoint(anchors.first().x, anchors.first().y) else CvPoint(0f, 0f)
            val end = if (anchors.isNotEmpty()) CvPoint(anchors.last().x, anchors.last().y) else CvPoint(0f, 0f)

            // Approximate Euclidean path length from anchors
            var length = 0f
            for (i in 0 until anchors.size - 1) {
                val p1 = anchors[i]
                val p2 = anchors[i + 1]
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                length += sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            }
            if (vo.isClosed && anchors.size > 2) {
                val p1 = anchors.last()
                val p2 = anchors.first()
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                length += sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            }
            val approxLength = max(1.0f, length)

            // Extract metadata if available
            val roleStr = vo.metadata["semanticRole"]
            val semanticRole = when (roleStr?.uppercase()) {
                "INTERNAL_DETAIL" -> StrokeSemanticRole.INTERNAL_DETAIL
                "HATCHING" -> StrokeSemanticRole.HATCHING
                "SHADOW_ACCENT" -> StrokeSemanticRole.SHADOW_ACCENT
                "FREEHAND" -> StrokeSemanticRole.FREEHAND
                else -> if (vo.isClosed) StrokeSemanticRole.OUTLINE else StrokeSemanticRole.INTERNAL_DETAIL
            }

            val tagStr = vo.metadata["anatomicalTag"]
            val anatomicalTag = tagStr?.let {
                try {
                    AnatomicalTag.valueOf(it.uppercase())
                } catch (_: Exception) {
                    null
                }
            }

            val brushWidth = vo.style.strokeWidth.takeIf { it > 0f } ?: 2.0f
            val brushColor = vo.style.strokeColor ?: 0xFF000000L
            val waypoints = anchors.map { CvPoint(it.x, it.y) }

            return SchedulablePath(
                id = vo.id,
                layerId = layerId,
                regionId = regionId ?: vo.metadata["regionId"],
                startPoint = start,
                endPoint = end,
                waypoints = waypoints,
                pathLengthPx = approxLength,
                isClosed = vo.isClosed,
                isContour = vo.isClosed || semanticRole == StrokeSemanticRole.OUTLINE,
                semanticRole = semanticRole,
                anatomicalTag = anatomicalTag,
                brushParams = ScheduledBrushParams(
                    sizePx = brushWidth,
                    colorArgb = brushColor
                ),
                userPriority = userPriority
            )
        }
    }
}

/**
 * Deterministic drawing schedule generator.
 *
 * Synthesizes an optimized, ordered sequence of stroke actions from reconstructed paths
 * with support for anatomical, contour-first, layer-by-layer, and spatially efficient ordering.
 * Enforces topological dependency ordering, single pen cursor simulation, and deterministic output hashing.
 */
object DrawingScheduleGenerator {

    /**
     * Generates a deterministic drawing schedule from a list of schedulable paths.
     */
    fun generateSchedule(
        paths: List<SchedulablePath>,
        config: DrawingScheduleConfig = DrawingScheduleConfig(),
        documentId: String? = null
    ): DrawingSchedule {
        if (paths.isEmpty()) {
            return DrawingSchedule(
                documentId = documentId,
                entries = emptyList(),
                config = config,
                totalDurationMs = 0L,
                totalPenTravelPx = 0f,
                totalDrawingLengthPx = 0f,
                scheduleHash = DrawingSchedule.computeScheduleHash(emptyList())
            )
        }

        // 1. Build Comprehensive Dependency DAG
        // Path -> set of prerequisite path IDs
        val dependencyGraph = mutableMapOf<String, MutableSet<String>>()
        for (path in paths) {
            dependencyGraph[path.id] = path.prerequisitePathIds.toMutableSet()
        }

        // Enforce user priority overrides: priority A < priority B implies A must precede B
        for (pA in paths) {
            val prioA = config.userPriorityOverrides[pA.id] ?: pA.userPriority
            if (prioA != null) {
                for (pB in paths) {
                    if (pA.id != pB.id) {
                        val prioB = config.userPriorityOverrides[pB.id] ?: pB.userPriority
                        if (prioB != null && prioA < prioB) {
                            dependencyGraph[pB.id]?.add(pA.id)
                        }
                    }
                }
            }
        }

        // Enforce strategy-specific prerequisite dependencies:
        when (config.orderingStrategy) {
            ScheduleOrderingStrategy.CONTOUR_FIRST -> {
                // Outlines must precede internal details and hatching within the same region or layer
                val outlines = paths.filter { it.semanticRole == StrokeSemanticRole.OUTLINE }
                val nonOutlines = paths.filter { it.semanticRole != StrokeSemanticRole.OUTLINE }
                for (out in outlines) {
                    for (nonOut in nonOutlines) {
                        if (out.layerId == nonOut.layerId && (out.regionId == null || out.regionId == nonOut.regionId)) {
                            val outPrio = config.userPriorityOverrides[out.id] ?: out.userPriority
                            val nonOutPrio = config.userPriorityOverrides[nonOut.id] ?: nonOut.userPriority
                            if (outPrio == null || nonOutPrio == null || outPrio <= nonOutPrio) {
                                dependencyGraph[nonOut.id]?.add(out.id)
                            }
                        }
                    }
                }
            }
            ScheduleOrderingStrategy.LAYER_BY_LAYER -> {
                // Discover distinct layers and enforce bottom-to-top layer dependencies
                val layerOrder = paths.map { it.layerId }.distinct().sorted()
                for (i in 0 until layerOrder.size - 1) {
                    val lowerLayer = layerOrder[i]
                    val upperLayer = layerOrder[i + 1]
                    val lowerPaths = paths.filter { it.layerId == lowerLayer }
                    val upperPaths = paths.filter { it.layerId == upperLayer }
                    for (lp in lowerPaths) {
                        for (up in upperPaths) {
                            val lpPrio = config.userPriorityOverrides[lp.id] ?: lp.userPriority
                            val upPrio = config.userPriorityOverrides[up.id] ?: up.userPriority
                            if (lpPrio == null || upPrio == null || lpPrio <= upPrio) {
                                dependencyGraph[up.id]?.add(lp.id)
                            }
                        }
                    }
                }
            }
            ScheduleOrderingStrategy.ANATOMICAL -> {
                // Anatomical rank order (lower rank precedes higher rank)
                for (pA in paths) {
                    val tagA = pA.anatomicalTag
                    if (tagA != null) {
                        for (pB in paths) {
                            val tagB = pB.anatomicalTag
                            if (tagB != null && tagA.rank < tagB.rank) {
                                val pAPrio = config.userPriorityOverrides[pA.id] ?: pA.userPriority
                                val pBPrio = config.userPriorityOverrides[pB.id] ?: pB.userPriority
                                if (pAPrio == null || pBPrio == null || pAPrio <= pBPrio) {
                                    dependencyGraph[pB.id]?.add(pA.id)
                                }
                            }
                        }
                    }
                }
            }
            ScheduleOrderingStrategy.SPATIALLY_EFFICIENT -> {
                // Spatially efficient optimizes travel distance without adding artificial dependencies
            }
        }

        // 2. Greedy Topological Scheduling with Strategy Selection
        val completed = mutableSetOf<String>()
        val remaining = paths.toMutableList()
        val entries = mutableListOf<DrawingScheduleEntry>()

        var currentPen = if (paths.isNotEmpty()) paths.first().startPoint else CvPoint(0f, 0f)
        var currentTimeMs = 0L
        var totalPenTravelPx = 0f
        var totalDrawingLengthPx = 0f

        var sequenceIndex = 0

        var lcgSeed = config.seed

        while (remaining.isNotEmpty()) {
            // Find candidates whose dependencies are all satisfied
            val available = remaining.filter { p ->
                val deps = dependencyGraph[p.id] ?: emptySet()
                deps.all { it in completed }
            }

            // Deadlock / cycle breaker: if no candidate is available due to cycle, pick candidate with fewest unfulfilled dependencies
            val candidatePool = if (available.isNotEmpty()) available else {
                listOf(remaining.minByOrNull { p ->
                    val deps = dependencyGraph[p.id] ?: emptySet()
                    deps.count { it !in completed }
                }!!)
            }

            // Pick next candidate based on strategy and tie-breakers
            val chosen = if (config.randomizeOrder) {
                lcgSeed = lcgSeed * 6364136223846793005L + 1442695040888963407L
                val pickIndex = abs((lcgSeed ushr 32).toInt()) % candidatePool.size
                candidatePool[pickIndex]
            } else {
                when (config.orderingStrategy) {
                    ScheduleOrderingStrategy.SPATIALLY_EFFICIENT -> {
                        candidatePool.minWithOrNull(
                            compareBy<SchedulablePath> { p ->
                                minPenDistance(currentPen, p, config.allowDirectionReversal)
                            }.thenBy { it.id }
                        )!!
                    }
                    ScheduleOrderingStrategy.CONTOUR_FIRST -> {
                        candidatePool.minWithOrNull(
                            compareBy<SchedulablePath> { p ->
                                // Outlines first (0), then details (1), then hatching (2), then shadow (3)
                                when (p.semanticRole) {
                                    StrokeSemanticRole.OUTLINE -> 0
                                    StrokeSemanticRole.INTERNAL_DETAIL -> 1
                                    StrokeSemanticRole.HATCHING -> 2
                                    StrokeSemanticRole.SHADOW_ACCENT -> 3
                                    StrokeSemanticRole.FREEHAND -> 4
                                }
                            }.thenBy { p ->
                                minPenDistance(currentPen, p, config.allowDirectionReversal)
                            }.thenBy { it.id }
                        )!!
                    }
                    ScheduleOrderingStrategy.ANATOMICAL -> {
                        candidatePool.minWithOrNull(
                            compareBy<SchedulablePath> { p ->
                                p.anatomicalTag?.rank ?: 99
                            }.thenBy { p ->
                                minPenDistance(currentPen, p, config.allowDirectionReversal)
                            }.thenBy { it.id }
                        )!!
                    }
                    ScheduleOrderingStrategy.LAYER_BY_LAYER -> {
                        candidatePool.minWithOrNull(
                            compareBy<SchedulablePath> { it.layerId }
                                .thenBy { p -> minPenDistance(currentPen, p, config.allowDirectionReversal) }
                                .thenBy { it.id }
                        )!!
                    }
                }
            }

            // 3. Direction Determination (Forward vs Reverse)
            val chosenDirection: StrokeDirection
            val chosenStart: CvPoint
            val chosenEnd: CvPoint

            if (config.allowDirectionReversal && !chosen.isClosed) {
                val dStart = euclideanDist(currentPen, chosen.startPoint)
                val dEnd = euclideanDist(currentPen, chosen.endPoint)
                if (dEnd < dStart) {
                    chosenDirection = StrokeDirection.REVERSE
                    chosenStart = chosen.endPoint
                    chosenEnd = chosen.startPoint
                } else {
                    chosenDirection = StrokeDirection.FORWARD
                    chosenStart = chosen.startPoint
                    chosenEnd = chosen.endPoint
                }
            } else {
                chosenDirection = StrokeDirection.FORWARD
                chosenStart = chosen.startPoint
                chosenEnd = chosen.endPoint
            }

            // 4. Timing & Pen Transport Dynamics
            val airDistance = euclideanDist(currentPen, chosenStart)
            val penLiftSpeed = config.penLiftSpeedPxPerSec
            val drawSpeed = config.nominalPenSpeedPxPerSec

            val delayMs = if (sequenceIndex == 0) 0L else (airDistance / (penLiftSpeed / 1000f)).roundToLong().coerceAtLeast(0L)
            val durationMs = max(1L, (chosen.pathLengthPx / (drawSpeed / 1000f)).roundToLong())

            val startTimestamp = currentTimeMs + delayMs
            val timing = ScheduleTiming(
                estimatedDurationMs = durationMs,
                delayBeforeMs = delayMs,
                startTimestampMs = startTimestamp,
                speedFactor = 1.0f
            )

            currentTimeMs = timing.endTimestampMs
            currentPen = chosenEnd
            totalPenTravelPx += airDistance
            totalDrawingLengthPx += chosen.pathLengthPx

            val orderedWaypoints = when (chosenDirection) {
                StrokeDirection.REVERSE -> if (chosen.waypoints.isNotEmpty()) chosen.waypoints.reversed() else listOf(chosenStart, chosenEnd)
                else -> if (chosen.waypoints.isNotEmpty()) chosen.waypoints else listOf(chosenStart, chosenEnd)
            }

            val entry = DrawingScheduleEntry(
                id = "sched_${chosen.id}_$sequenceIndex",
                pathId = chosen.id,
                regionId = chosen.regionId,
                layerId = chosen.layerId,
                sequenceIndex = sequenceIndex,
                direction = chosenDirection,
                brushParams = chosen.brushParams,
                timing = timing,
                dependencies = dependencyGraph[chosen.id]?.toSet() ?: emptySet(),
                startPoint = chosenStart,
                endPoint = chosenEnd,
                waypoints = orderedWaypoints,
                pathLengthPx = chosen.pathLengthPx,
                isContour = chosen.isContour,
                isClosed = chosen.isClosed,
                semanticRole = chosen.semanticRole,
                anatomicalTag = chosen.anatomicalTag
            )

            entries.add(entry)
            completed.add(chosen.id)
            remaining.remove(chosen)
            sequenceIndex++
        }

        val totalDuration = entries.lastOrNull()?.timing?.endTimestampMs ?: 0L
        val scheduleHash = DrawingSchedule.computeScheduleHash(entries)

        return DrawingSchedule(
            documentId = documentId,
            entries = entries,
            config = config,
            totalDurationMs = totalDuration,
            totalPenTravelPx = totalPenTravelPx,
            totalDrawingLengthPx = totalDrawingLengthPx,
            scheduleHash = scheduleHash
        )
    }

    /**
     * Generates a drawing schedule directly from vector layers within an [ArtDocument].
     */
    fun generateScheduleFromDocument(
        document: ArtDocument,
        config: DrawingScheduleConfig = DrawingScheduleConfig()
    ): DrawingSchedule {
        val paths = mutableListOf<SchedulablePath>()

        fun extractFromLayer(layer: LayerNode) {
            when (layer) {
                is LayerNode.VectorPathLayer -> {
                    val vectorObjects = layer.allVectorObjects()
                    for (vo in vectorObjects) {
                        paths.add(SchedulablePath.fromVectorObject(vo, layer.id))
                    }
                }
                is LayerNode.LayerGroup -> {
                    for (child in layer.children) {
                        extractFromLayer(child)
                    }
                }
                else -> {
                    // Raster layers do not contribute vector stroke paths directly
                }
            }
        }

        extractFromLayer(document.rootGroup)
        return generateSchedule(paths, config, document.id)
    }

    private fun minPenDistance(pen: CvPoint, path: SchedulablePath, allowReversal: Boolean): Float {
        val dStart = euclideanDist(pen, path.startPoint)
        if (allowReversal && !path.isClosed) {
            val dEnd = euclideanDist(pen, path.endPoint)
            return min(dStart, dEnd)
        }
        return dStart
    }

    private fun euclideanDist(p1: CvPoint, p2: CvPoint): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }
}
