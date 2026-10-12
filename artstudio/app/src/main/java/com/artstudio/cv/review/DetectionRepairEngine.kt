package com.artstudio.cv.review

import com.artstudio.ai.model.RegionSource
import com.artstudio.cv.CvPoint
import com.artstudio.cv.CvRect
import com.artstudio.cv.pathfit.FittedPathCandidate
import com.artstudio.cv.pathfit.PathProvenance
import com.artstudio.cv.pathfit.SourcePixelMapping
import com.artstudio.cv.region.RegionMask
import com.artstudio.vector.VectorAnchor
import kotlin.math.*

/**
 * Result of sequentially replaying explicit artist corrections.
 */
data class ReplayResult(
    val effectivePaths: List<FittedPathCandidate>,
    val effectiveRegions: List<RegionMask>,
    val conflicts: List<CorrectionIdentityConflict>
)

/**
 * Pure-Kotlin, deterministic repair engine executing artist corrections and uncertainty evaluation.
 */
object DetectionRepairEngine {

    /**
     * Translates a fitted vector path by offset (dx, dy).
     */
    fun movePath(path: FittedPathCandidate, dx: Float, dy: Float): FittedPathCandidate {
        val movedAnchors = path.anchors.map { anchor ->
            anchor.translated(dx, dy)
        }
        val origBox = path.sourceMapping.boundingBox
        val updatedBox = CvRect(
            minX = origBox.minX + dx,
            minY = origBox.minY + dy,
            maxX = origBox.maxX + dx,
            maxY = origBox.maxY + dy
        )
        val updatedMapping = path.sourceMapping.copy(
            boundingBox = updatedBox,
            sampledPixels = path.sourceMapping.sampledPixels.map { p ->
                CvPoint(p.x + dx, p.y + dy)
            }
        )
        return path.copy(
            anchors = movedAnchors,
            sourceMapping = updatedMapping
        )
    }

    /**
     * Splits an open or closed path candidate into two sub-paths at the specified anchor index.
     */
    fun splitPath(
        path: FittedPathCandidate,
        splitIndex: Int
    ): Pair<FittedPathCandidate, FittedPathCandidate> {
        val total = path.anchors.size
        require(total >= 2) { "Path must contain at least 2 anchors to split, got $total" }
        val idx = splitIndex.coerceIn(1, total - 1)

        val anchors1 = path.anchors.subList(0, idx + 1)
        val anchors2 = path.anchors.subList(idx, total)

        val id1 = "${path.id}_part1"
        val id2 = "${path.id}_part2"

        val part1 = path.copy(
            id = id1,
            isClosed = false,
            anchors = anchors1.toList(),
            provenance = path.provenance.copy(algorithm = "${path.provenance.algorithm}+UserSplit")
        )
        val part2 = path.copy(
            id = id2,
            isClosed = false,
            anchors = anchors2.toList(),
            provenance = path.provenance.copy(algorithm = "${path.provenance.algorithm}+UserSplit")
        )
        return Pair(part1, part2)
    }

    /**
     * Merges two path candidates end-to-end into a single continuous path.
     */
    fun mergePaths(path1: FittedPathCandidate, path2: FittedPathCandidate): FittedPathCandidate {
        val combinedAnchors = path1.anchors + path2.anchors
        val minX = min(path1.sourceMapping.boundingBox.minX, path2.sourceMapping.boundingBox.minX)
        val minY = min(path1.sourceMapping.boundingBox.minY, path2.sourceMapping.boundingBox.minY)
        val maxX = max(path1.sourceMapping.boundingBox.maxX, path2.sourceMapping.boundingBox.maxX)
        val maxY = max(path1.sourceMapping.boundingBox.maxY, path2.sourceMapping.boundingBox.maxY)

        val combinedMapping = SourcePixelMapping(
            pixelCount = path1.sourceMapping.pixelCount + path2.sourceMapping.pixelCount,
            boundingBox = CvRect(minX, minY, maxX, maxY),
            sampledPixels = path1.sourceMapping.sampledPixels + path2.sourceMapping.sampledPixels
        )

        return FittedPathCandidate(
            id = "${path1.id}_merged_${path2.id}",
            regionId = path1.regionId,
            isHole = path1.isHole && path2.isHole,
            isClosed = false,
            anchors = combinedAnchors,
            sourceMapping = combinedMapping,
            provenance = PathProvenance(
                algorithm = "UserMerge(${path1.provenance.algorithm}, ${path2.provenance.algorithm})",
                timestampMs = System.currentTimeMillis()
            ),
            confidence = ((path1.confidence + path2.confidence) / 2f).coerceIn(0f, 1f),
            maxContourDeviation = max(path1.maxContourDeviation, path2.maxContourDeviation),
            meanContourDeviation = (path1.meanContourDeviation + path2.meanContourDeviation) / 2f,
            cornersDetected = path1.cornersDetected + path2.cornersDetected
        )
    }

    /**
     * Translates a region mask by (dx, dy) within the canvas bounds.
     */
    fun moveRegion(region: RegionMask, dx: Int, dy: Int): RegionMask {
        val w = region.width
        val h = region.height
        val newMask = ByteArray(w * h)

        for (y in 0 until h) {
            val srcY = y - dy
            if (srcY !in 0 until h) continue
            for (x in 0 until w) {
                val srcX = x - dx
                if (srcX in 0 until w) {
                    newMask[y * w + x] = region.mask[srcY * w + srcX]
                }
            }
        }

        return region.copy(mask = newMask)
    }

    /**
     * Splits a region mask into two parts along a vertical or horizontal line.
     */
    fun splitRegion(
        region: RegionMask,
        splitCoord: Int,
        isVertical: Boolean = true
    ): Pair<RegionMask, RegionMask> {
        val w = region.width
        val h = region.height
        val mask1 = ByteArray(w * h)
        val mask2 = ByteArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val pixelVal = region.mask[idx]
                if (isVertical) {
                    if (x <= splitCoord) {
                        mask1[idx] = pixelVal
                    } else {
                        mask2[idx] = pixelVal
                    }
                } else {
                    if (y <= splitCoord) {
                        mask1[idx] = pixelVal
                    } else {
                        mask2[idx] = pixelVal
                    }
                }
            }
        }

        val r1 = region.copy(regionId = "${region.regionId}_part1", mask = mask1)
        val r2 = region.copy(regionId = "${region.regionId}_part2", mask = mask2)
        return Pair(r1, r2)
    }

    /**
     * Merges two region masks via boolean union (max alpha).
     */
    fun mergeRegions(region1: RegionMask, region2: RegionMask): RegionMask {
        require(region1.width == region2.width && region1.height == region2.height) {
            "Region dimensions must match: (${region1.width}x${region1.height}) vs (${region2.width}x${region2.height})"
        }
        val total = region1.width * region1.height
        val mergedMask = ByteArray(total)
        for (i in 0 until total) {
            val v1 = region1.mask[i].toInt() and 0xFF
            val v2 = region2.mask[i].toInt() and 0xFF
            mergedMask[i] = max(v1, v2).toByte()
        }

        return RegionMask(
            regionId = "${region1.regionId}_merged_${region2.regionId}",
            width = region1.width,
            height = region1.height,
            mask = mergedMask,
            semanticTag = region1.semanticTag,
            confidence = ((region1.confidence + region2.confidence) / 2f).coerceIn(0f, 1f),
            source = region1.source
        )
    }

    /**
     * Bridges an open gap in a region mask between two endpoints to seal leaks.
     */
    fun bridgeGap(
        region: RegionMask,
        start: CvPoint,
        end: CvPoint,
        thickness: Int = 2
    ): RegionMask {
        val w = region.width
        val h = region.height
        val newMask = region.mask.copyOf()

        val x0 = start.x.roundToInt().coerceIn(0, w - 1)
        val y0 = start.y.roundToInt().coerceIn(0, h - 1)
        val x1 = end.x.roundToInt().coerceIn(0, w - 1)
        val y1 = end.y.roundToInt().coerceIn(0, h - 1)

        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        var cx = x0
        var cy = y0
        val r = (thickness / 2).coerceAtLeast(0)

        while (true) {
            for (ty in (cy - r).coerceAtLeast(0)..(cy + r).coerceAtMost(h - 1)) {
                for (tx in (cx - r).coerceAtLeast(0)..(cx + r).coerceAtMost(w - 1)) {
                    newMask[ty * w + tx] = 255.toByte()
                }
            }

            if (cx == x1 && cy == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                cx += sx
            }
            if (e2 < dx) {
                err += dx
                cy += sy
            }
        }

        return region.copy(mask = newMask)
    }

    /**
     * Scans paths and regions to generate actionable visual uncertainty markers.
     */
    fun evaluateUncertainties(
        paths: List<FittedPathCandidate>,
        regions: List<RegionMask>
    ): List<UncertaintyMarker> {
        val markers = mutableListOf<UncertaintyMarker>()

        // 1. Evaluate Paths
        for (path in paths) {
            // Low confidence path
            if (path.confidence < 0.70f) {
                val box = path.sourceMapping.boundingBox
                markers.add(
                    UncertaintyMarker(
                        kind = UncertaintyKind.LOW_CONFIDENCE_PATH,
                        location = Pair((box.minX + box.maxX) / 2f, (box.minY + box.maxY) / 2f),
                        severity = (1.0f - path.confidence).coerceIn(0.1f, 1.0f),
                        description = "Path ${path.id} has low fitting confidence (%.2f)".format(path.confidence),
                        suggestedAction = "Inspect stroke trajectory; adjust fitting tolerance or smooth anchors.",
                        targetEntityId = path.id
                    )
                )
            }

            // Ambiguous forks (many anchors with sharp corners)
            if (path.cornersDetected.size >= 4) {
                val corner = path.cornersDetected.first()
                markers.add(
                    UncertaintyMarker(
                        kind = UncertaintyKind.AMBIGUOUS_FORK,
                        location = Pair(corner.x, corner.y),
                        severity = 0.65f,
                        description = "Path ${path.id} contains ${path.cornersDetected.size} sharp corners/forks",
                        suggestedAction = "Consider splitting path at junction.",
                        targetEntityId = path.id
                    )
                )
            }
        }

        // Endpoint gap evaluation between open paths (stroke breaks)
        val openPaths = paths.filter { !it.isClosed && it.anchors.size >= 2 }
        for (i in openPaths.indices) {
            val p1 = openPaths[i]
            val end1 = p1.anchors.last()
            for (j in i + 1 until openPaths.size) {
                val p2 = openPaths[j]
                val start2 = p2.anchors.first()
                val dist = hypot(end1.x - start2.x, end1.y - start2.y)
                if (dist in 1.0f..15.0f) {
                    markers.add(
                        UncertaintyMarker(
                            kind = UncertaintyKind.POTENTIAL_STROKE_BREAK,
                            location = Pair((end1.x + start2.x) / 2f, (end1.y + start2.y) / 2f),
                            severity = ((15.0f - dist) / 15.0f).coerceIn(0.3f, 0.9f),
                            description = "Potential stroke break between ${p1.id} and ${p2.id} (gap: %.1f px)".format(dist),
                            suggestedAction = "Merge paths to close stroke gap.",
                            targetEntityId = p1.id
                        )
                    )
                }
            }
        }

        // 2. Evaluate Regions
        for (region in regions) {
            val pixelCount = region.selectedPixelCount
            // Leaky or tiny region
            if (pixelCount in 1..30) {
                markers.add(
                    UncertaintyMarker(
                        kind = UncertaintyKind.LEAKY_BOUNDARY,
                        location = Pair(region.width / 2f, region.height / 2f),
                        severity = 0.8f,
                        description = "Region ${region.regionId} is tiny ($pixelCount px), possible leak or speckle",
                        suggestedAction = "Bridge boundary gap or delete region.",
                        targetEntityId = region.regionId
                    )
                )
            }

            // AI contradiction
            if (region.source == RegionSource.AI_MODEL && region.confidence < 0.60f) {
                markers.add(
                    UncertaintyMarker(
                        kind = UncertaintyKind.CONTRADICTORY_AI_PROPOSAL,
                        location = Pair(region.width / 2f, region.height / 2f),
                        severity = 0.7f,
                        description = "AI proposal for ${region.regionId} has low confidence (%.2f)".format(region.confidence),
                        suggestedAction = "Compare with classical CV boundary before accepting.",
                        targetEntityId = region.regionId
                    )
                )
            }
        }

        return markers
    }

    /**
     * Sequentially executes active corrections on top of raw detection results.
     *
     * Detects identity mapping conflicts when a targeted entity cannot be found or
     * mapped cleanly in the current detections.
     */
    fun replayCorrections(
        rawPaths: List<FittedPathCandidate>,
        rawRegions: List<RegionMask>,
        corrections: List<DetectionCorrection>
    ): ReplayResult {
        val currentPaths = rawPaths.toMutableList()
        val currentRegions = rawRegions.toMutableList()
        val conflicts = mutableListOf<CorrectionIdentityConflict>()

        for (correction in corrections) {
            if (!correction.isActive) continue

            when (correction.opType) {
                CorrectionOpType.MOVE_PATH -> {
                    val idx = currentPaths.indexOfFirst { it.id == correction.targetId }
                    if (idx >= 0) {
                        currentPaths[idx] = movePath(currentPaths[idx], correction.dx, correction.dy)
                    } else {
                        val candidates = findCandidatePaths(rawPaths, correction.targetId)
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = if (candidates.isEmpty()) ConflictReason.TARGET_DISAPPEARED else ConflictReason.AMBIGUOUS_REMAP,
                                candidateTargetIds = candidates.map { it.id },
                                explanation = "Cannot move path '${correction.targetId}': entity not found in current detections."
                            )
                        )
                    }
                }

                CorrectionOpType.DELETE_PATH -> {
                    val removed = currentPaths.removeAll { it.id == correction.targetId }
                    if (!removed) {
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = ConflictReason.TARGET_DISAPPEARED,
                                candidateTargetIds = emptyList(),
                                explanation = "Cannot delete path '${correction.targetId}': already absent."
                            )
                        )
                    }
                }

                CorrectionOpType.SPLIT_PATH -> {
                    val idx = currentPaths.indexOfFirst { it.id == correction.targetId }
                    if (idx >= 0) {
                        val target = currentPaths.removeAt(idx)
                        if (target.anchors.size >= 2) {
                            val (p1, p2) = splitPath(target, correction.splitIndex)
                            currentPaths.add(idx, p2)
                            currentPaths.add(idx, p1)
                        } else {
                            currentPaths.add(idx, target)
                            conflicts.add(
                                CorrectionIdentityConflict(
                                    correction = correction,
                                    reason = ConflictReason.GEOMETRY_DRASTICALLY_CHANGED,
                                    candidateTargetIds = listOf(target.id),
                                    explanation = "Path '${correction.targetId}' has fewer than 2 anchors, cannot split."
                                )
                            )
                        }
                    } else {
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = ConflictReason.TARGET_DISAPPEARED,
                                candidateTargetIds = emptyList(),
                                explanation = "Cannot split path '${correction.targetId}': not found."
                            )
                        )
                    }
                }

                CorrectionOpType.MERGE_PATHS -> {
                    val idx1 = currentPaths.indexOfFirst { it.id == correction.targetId }
                    val idx2 = currentPaths.indexOfFirst { it.id == correction.secondaryTargetId }
                    if (idx1 >= 0 && idx2 >= 0) {
                        val p1 = currentPaths[idx1]
                        val p2 = currentPaths[idx2]
                        val merged = mergePaths(p1, p2)
                        currentPaths.removeAll { it.id == p1.id || it.id == p2.id }
                        currentPaths.add(merged)
                    } else {
                        val missing = mutableListOf<String>()
                        if (idx1 < 0) missing.add(correction.targetId)
                        if (idx2 < 0) correction.secondaryTargetId?.let { missing.add(it) }
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = if (idx1 < 0) ConflictReason.TARGET_DISAPPEARED else ConflictReason.SECONDARY_TARGET_MISSING,
                                candidateTargetIds = emptyList(),
                                explanation = "Cannot merge paths: missing ${missing.joinToString(", ")}"
                            )
                        )
                    }
                }

                CorrectionOpType.MOVE_REGION -> {
                    val idx = currentRegions.indexOfFirst { it.regionId == correction.targetId }
                    if (idx >= 0) {
                        currentRegions[idx] = moveRegion(currentRegions[idx], correction.dx.toInt(), correction.dy.toInt())
                    } else {
                        val candidates = findCandidateRegions(rawRegions, correction.targetId)
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = if (candidates.isEmpty()) ConflictReason.TARGET_DISAPPEARED else ConflictReason.AMBIGUOUS_REMAP,
                                candidateTargetIds = candidates.map { it.regionId },
                                explanation = "Cannot move region '${correction.targetId}': not found."
                            )
                        )
                    }
                }

                CorrectionOpType.DELETE_REGION -> {
                    val removed = currentRegions.removeAll { it.regionId == correction.targetId }
                    if (!removed) {
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = ConflictReason.TARGET_DISAPPEARED,
                                candidateTargetIds = emptyList(),
                                explanation = "Cannot delete region '${correction.targetId}': already absent."
                            )
                        )
                    }
                }

                CorrectionOpType.SPLIT_REGION -> {
                    val idx = currentRegions.indexOfFirst { it.regionId == correction.targetId }
                    if (idx >= 0) {
                        val target = currentRegions.removeAt(idx)
                        val coord = if (correction.isVerticalSplit) correction.splitX else correction.splitY
                        val (r1, r2) = splitRegion(target, coord, correction.isVerticalSplit)
                        currentRegions.add(idx, r2)
                        currentRegions.add(idx, r1)
                    } else {
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = ConflictReason.TARGET_DISAPPEARED,
                                candidateTargetIds = emptyList(),
                                explanation = "Cannot split region '${correction.targetId}': not found."
                            )
                        )
                    }
                }

                CorrectionOpType.MERGE_REGIONS -> {
                    val idx1 = currentRegions.indexOfFirst { it.regionId == correction.targetId }
                    val idx2 = currentRegions.indexOfFirst { it.regionId == correction.secondaryTargetId }
                    if (idx1 >= 0 && idx2 >= 0) {
                        val r1 = currentRegions[idx1]
                        val r2 = currentRegions[idx2]
                        val merged = mergeRegions(r1, r2)
                        currentRegions.removeAll { it.regionId == r1.regionId || it.regionId == r2.regionId }
                        currentRegions.add(merged)
                    } else {
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = if (idx1 < 0) ConflictReason.TARGET_DISAPPEARED else ConflictReason.SECONDARY_TARGET_MISSING,
                                candidateTargetIds = emptyList(),
                                explanation = "Cannot merge regions: one or both targets not found."
                            )
                        )
                    }
                }

                CorrectionOpType.BRIDGE_GAP -> {
                    val idx = currentRegions.indexOfFirst { it.regionId == correction.targetId }
                    val start = correction.bridgeStart
                    val end = correction.bridgeEnd
                    if (idx >= 0 && start != null && end != null) {
                        currentRegions[idx] = bridgeGap(
                            currentRegions[idx],
                            start,
                            end,
                            correction.strokeThickness
                        )
                    } else {
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = ConflictReason.TARGET_DISAPPEARED,
                                candidateTargetIds = emptyList(),
                                explanation = "Cannot bridge gap for region '${correction.targetId}': target not found."
                            )
                        )
                    }
                }

                CorrectionOpType.TAG_REGION -> {
                    val idx = currentRegions.indexOfFirst { it.regionId == correction.targetId }
                    if (idx >= 0) {
                        val tagVal = runCatching {
                            com.artstudio.ai.model.RegionSemanticTag.valueOf(correction.tag)
                        }.getOrDefault(com.artstudio.ai.model.RegionSemanticTag.UNKNOWN)
                        currentRegions[idx] = currentRegions[idx].copy(semanticTag = tagVal)
                    } else {
                        conflicts.add(
                            CorrectionIdentityConflict(
                                correction = correction,
                                reason = ConflictReason.TARGET_DISAPPEARED,
                                candidateTargetIds = emptyList(),
                                explanation = "Cannot tag region '${correction.targetId}': not found."
                            )
                        )
                    }
                }
            }
        }

        return ReplayResult(
            effectivePaths = currentPaths,
            effectiveRegions = currentRegions,
            conflicts = conflicts
        )
    }

    private fun findCandidatePaths(
        paths: List<FittedPathCandidate>,
        targetId: String
    ): List<FittedPathCandidate> {
        return paths.filter { it.id.contains(targetId) || targetId.contains(it.id) }
    }

    private fun findCandidateRegions(
        regions: List<RegionMask>,
        targetId: String
    ): List<RegionMask> {
        return regions.filter { it.regionId.contains(targetId) || targetId.contains(it.regionId) }
    }
}
