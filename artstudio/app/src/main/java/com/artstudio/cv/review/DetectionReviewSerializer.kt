package com.artstudio.cv.review

import com.artstudio.document.io.ArtJson
import com.artstudio.cv.pathfit.FittedPathCandidate
import com.artstudio.cv.region.RegionMask

/**
 * Lossless persistence serializer for [DetectionReviewSession] state.
 *
 * In accordance with Prompt 56:
 * - Persists all artist corrections, resolution decisions, and uncertainty inspection states.
 * - Restores review sessions across close and reopen lifecycles.
 */
object DetectionReviewSerializer {

    /**
     * Serializes a [DetectionReviewSession] into formatted JSON string.
     */
    fun toJson(session: DetectionReviewSession, indent: Int = 2): String {
        val root = mutableMapOf<String, Any?>()
        root["schemaVersion"] = 1
        root["width"] = session.width
        root["height"] = session.height
        root["overlayMode"] = session.overlayMode.name
        root["splitPosition"] = session.splitPosition
        root["blendAlpha"] = session.blendAlpha

        root["corrections"] = session.corrections.map { it.toMap() }
        root["conflicts"] = session.conflicts.map { it.toMap() }
        root["uncertaintyMarkers"] = session.uncertaintyMarkers.map { it.toMap() }

        val summary = session.getSummary()
        root["summary"] = mapOf(
            "pathCount" to summary.pathCount,
            "regionCount" to summary.regionCount,
            "totalUncertainties" to summary.totalUncertainties,
            "resolvedUncertainties" to summary.resolvedUncertainties,
            "activeCorrections" to summary.activeCorrections,
            "pendingConflicts" to summary.pendingConflicts
        )

        return ArtJson.stringify(root, indent)
    }

    /**
     * Restores a [DetectionReviewSession] from a serialized JSON string.
     */
    fun fromJson(
        json: String,
        sourcePixels: IntArray,
        initialPaths: List<FittedPathCandidate> = emptyList(),
        initialRegions: List<RegionMask> = emptyList()
    ): DetectionReviewSession {
        @Suppress("UNCHECKED_CAST")
        val root = ArtJson.parse(json) as? Map<String, Any?>
            ?: throw IllegalArgumentException("Invalid JSON root, expected Map object")

        val width = (root["width"] as? Number)?.toInt() ?: 100
        val height = (root["height"] as? Number)?.toInt() ?: 100

        @Suppress("UNCHECKED_CAST")
        val rawCorrections = (root["corrections"] as? List<*>)
            ?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val corrections = rawCorrections.map { DetectionCorrection.fromMap(it) }

        val session = DetectionReviewSession(
            width = width,
            height = height,
            sourcePixels = sourcePixels,
            initialPaths = initialPaths,
            initialRegions = initialRegions,
            initialCorrections = corrections
        )

        // Restore display settings
        val modeName = root["overlayMode"]?.toString() ?: OverlayDisplayMode.SPLIT_COMPARE.name
        session.overlayMode = runCatching { OverlayDisplayMode.valueOf(modeName) }
            .getOrDefault(OverlayDisplayMode.SPLIT_COMPARE)
        session.splitPosition = (root["splitPosition"] as? Number)?.toFloat() ?: 0.5f
        session.blendAlpha = (root["blendAlpha"] as? Number)?.toFloat() ?: 0.5f

        // Restore conflicts
        @Suppress("UNCHECKED_CAST")
        val rawConflicts = (root["conflicts"] as? List<*>)
            ?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        for (cMap in rawConflicts) {
            val conflict = CorrectionIdentityConflict.fromMap(cMap)
            if (session.conflicts.none { it.id == conflict.id }) {
                // If it was already resolved, resolve it
                if (conflict.isResolved) {
                    session.resolveConflict(conflict.id, conflict.resolution, conflict.chosenTargetId)
                }
            }
        }

        // Restore marker resolved status
        @Suppress("UNCHECKED_CAST")
        val rawMarkers = (root["uncertaintyMarkers"] as? List<*>)
            ?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        for (mMap in rawMarkers) {
            val marker = UncertaintyMarker.fromMap(mMap)
            if (marker.isResolved) {
                session.resolveUncertainty(marker.id)
            }
        }

        return session
    }
}
