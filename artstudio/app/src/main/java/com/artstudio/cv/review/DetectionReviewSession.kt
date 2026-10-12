package com.artstudio.cv.review

import com.artstudio.cv.pathfit.FittedPathCandidate
import com.artstudio.cv.region.RegionMask

/**
 * Authoritative state manager coordinating visual inspection, explicit artist repairs,
 * conflict resolution, uncertainty tracking, and targeted reanalysis passes.
 */
class DetectionReviewSession(
    val width: Int,
    val height: Int,
    val sourcePixels: IntArray,
    initialPaths: List<FittedPathCandidate> = emptyList(),
    initialRegions: List<RegionMask> = emptyList(),
    initialCorrections: List<DetectionCorrection> = emptyList()
) {
    init {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(sourcePixels.size == width * height) { "Source pixel buffer size must equal width * height" }
    }

    // Baseline detections (preserved across edits)
    private var baselinePaths: List<FittedPathCandidate> = initialPaths.toList()
    private var baselineRegions: List<RegionMask> = initialRegions.toList()

    // Explicit override operations log
    private val _corrections = initialCorrections.toMutableList()
    val corrections: List<DetectionCorrection> get() = _corrections.toList()

    // Conflicts resulting from reanalysis identity mismatches
    private val _conflicts = mutableListOf<CorrectionIdentityConflict>()
    val conflicts: List<CorrectionIdentityConflict> get() = _conflicts.toList()

    // Active effective paths and regions (after replaying corrections)
    private var _effectivePaths: List<FittedPathCandidate> = emptyList()
    val effectivePaths: List<FittedPathCandidate> get() = _effectivePaths

    private var _effectiveRegions: List<RegionMask> = emptyList()
    val effectiveRegions: List<RegionMask> get() = _effectiveRegions

    // Active uncertainty markers
    private val _uncertaintyMarkers = mutableListOf<UncertaintyMarker>()
    val uncertaintyMarkers: List<UncertaintyMarker> get() = _uncertaintyMarkers.toList()

    // Display configuration
    var overlayMode: OverlayDisplayMode = OverlayDisplayMode.SPLIT_COMPARE
    var splitPosition: Float = 0.5f
    var blendAlpha: Float = 0.5f

    init {
        recomputeEffectiveState()
    }

    /**
     * Applies a new explicit artist correction override and refreshes the effective document state.
     */
    fun applyCorrection(correction: DetectionCorrection) {
        _corrections.add(correction)
        recomputeEffectiveState()
    }

    /**
     * Undoes or removes a specific correction by ID.
     */
    fun undoCorrection(correctionId: String): Boolean {
        val idx = _corrections.indexOfFirst { it.id == correctionId }
        if (idx >= 0) {
            _corrections.removeAt(idx)
            recomputeEffectiveState()
            return true
        }
        return false
    }

    /**
     * Resolves an outstanding identity conflict with an artist-chosen strategy.
     */
    fun resolveConflict(
        conflictId: String,
        strategy: ConflictResolutionStrategy,
        chosenTargetId: String? = null
    ): Boolean {
        val idx = _conflicts.indexOfFirst { it.id == conflictId }
        if (idx < 0) return false

        val conflict = _conflicts[idx]
        val updatedConflict = conflict.copy(
            resolution = strategy,
            chosenTargetId = chosenTargetId,
            isResolved = true
        )
        _conflicts[idx] = updatedConflict

        when (strategy) {
            ConflictResolutionStrategy.REAPPLY_CLOSEST_MATCH -> {
                val target = chosenTargetId ?: conflict.candidateTargetIds.firstOrNull()
                if (target != null) {
                    val corrIdx = _corrections.indexOfFirst { it.id == conflict.correction.id }
                    if (corrIdx >= 0) {
                        _corrections[corrIdx] = _corrections[corrIdx].copy(targetId = target)
                    }
                }
            }
            ConflictResolutionStrategy.KEEP_NEW_DETECTION,
            ConflictResolutionStrategy.DISCARD_CORRECTION -> {
                val corrIdx = _corrections.indexOfFirst { it.id == conflict.correction.id }
                if (corrIdx >= 0) {
                    _corrections[corrIdx] = _corrections[corrIdx].copy(isActive = false)
                }
            }
            ConflictResolutionStrategy.PENDING_USER_REVIEW -> {
                // Keep pending
            }
        }

        recomputeEffectiveState()
        return true
    }

    /**
     * Marks an uncertainty marker as inspected and resolved.
     */
    fun resolveUncertainty(markerId: String): Boolean {
        val idx = _uncertaintyMarkers.indexOfFirst { it.id == markerId }
        if (idx >= 0) {
            _uncertaintyMarkers[idx] = _uncertaintyMarkers[idx].copy(isResolved = true)
            return true
        }
        return false
    }

    /**
     * Executes localized sub-patch reanalysis for the specified region without recomputing
     * the rest of the canvas.
     */
    fun reanalyzeCurrentRegion(
        regionId: String,
        boundaryMargin: Int = 8
    ): TargetedReanalysisResult {
        val result = TargetedReanalysisEngine.reanalyzeCurrentRegion(
            pixels = sourcePixels,
            width = width,
            height = height,
            currentPaths = baselinePaths,
            currentRegions = baselineRegions,
            targetRegionId = regionId,
            corrections = _corrections,
            boundaryMargin = boundaryMargin
        )

        // Update baseline with reanalyzed outputs
        baselinePaths = result.updatedPaths
        baselineRegions = result.updatedRegions

        // Record any new conflicts
        for (c in result.conflicts) {
            if (_conflicts.none { it.id == c.id }) {
                _conflicts.add(c)
            }
        }

        recomputeEffectiveState()
        return result
    }

    /**
     * Executes localized sub-patch reanalysis for an active selection mask without recomputing
     * untouched areas of the canvas.
     */
    fun reanalyzeSelection(
        selectionMask: ByteArray,
        boundaryMargin: Int = 8
    ): TargetedReanalysisResult {
        val result = TargetedReanalysisEngine.reanalyzeSelection(
            pixels = sourcePixels,
            width = width,
            height = height,
            currentPaths = baselinePaths,
            currentRegions = baselineRegions,
            selectionMask = selectionMask,
            corrections = _corrections,
            boundaryMargin = boundaryMargin
        )

        baselinePaths = result.updatedPaths
        baselineRegions = result.updatedRegions

        for (c in result.conflicts) {
            if (_conflicts.none { it.id == c.id }) {
                _conflicts.add(c)
            }
        }

        recomputeEffectiveState()
        return result
    }

    /**
     * Executes full reanalysis across the canvas, verifying that valid corrections are preserved
     * and identity mismatches raise visible conflicts.
     */
    fun reanalyzeFull(newPaths: List<FittedPathCandidate>, newRegions: List<RegionMask>): List<CorrectionIdentityConflict> {
        baselinePaths = newPaths
        baselineRegions = newRegions

        val replay = DetectionRepairEngine.replayCorrections(
            rawPaths = baselinePaths,
            rawRegions = baselineRegions,
            corrections = _corrections
        )

        _effectivePaths = replay.effectivePaths
        _effectiveRegions = replay.effectiveRegions

        // Update conflicts
        for (c in replay.conflicts) {
            if (_conflicts.none { it.correction.id == c.correction.id && !it.isResolved }) {
                _conflicts.add(c)
            }
        }

        updateUncertaintyMarkers()
        return replay.conflicts
    }

    /**
     * Renders current visual overlay inspection buffer.
     */
    fun renderOverlay(): IntArray {
        return DetectionOverlayRenderer.render(
            sourcePixels = sourcePixels,
            width = width,
            height = height,
            paths = _effectivePaths,
            regions = _effectiveRegions,
            mode = overlayMode,
            splitRatio = splitPosition,
            blendAlpha = blendAlpha
        )
    }

    /**
     * Returns statistical summary of the review session.
     */
    fun getSummary(): ReviewSessionSummary {
        val pendingCount = _conflicts.count { !it.isResolved }
        val totalUncertain = _uncertaintyMarkers.size
        val resolvedUncertain = _uncertaintyMarkers.count { it.isResolved }
        val activeCorr = _corrections.count { it.isActive }

        return ReviewSessionSummary(
            pathCount = _effectivePaths.size,
            regionCount = _effectiveRegions.size,
            totalUncertainties = totalUncertain,
            resolvedUncertainties = resolvedUncertain,
            activeCorrections = activeCorr,
            pendingConflicts = pendingCount
        )
    }

    private fun recomputeEffectiveState() {
        val replay = DetectionRepairEngine.replayCorrections(
            rawPaths = baselinePaths,
            rawRegions = baselineRegions,
            corrections = _corrections
        )

        _effectivePaths = replay.effectivePaths
        _effectiveRegions = replay.effectiveRegions

        // Preserve previous resolved status on known conflicts
        for (c in replay.conflicts) {
            if (_conflicts.none { it.correction.id == c.correction.id }) {
                _conflicts.add(c)
            }
        }

        updateUncertaintyMarkers()
    }

    private fun updateUncertaintyMarkers() {
        val fresh = DetectionRepairEngine.evaluateUncertainties(_effectivePaths, _effectiveRegions)
        // Retain resolved state if marker id matches
        val resolvedIds = _uncertaintyMarkers.filter { it.isResolved }.map { it.id }.toSet()
        _uncertaintyMarkers.clear()
        for (m in fresh) {
            _uncertaintyMarkers.add(if (m.id in resolvedIds) m.copy(isResolved = true) else m)
        }
    }
}
