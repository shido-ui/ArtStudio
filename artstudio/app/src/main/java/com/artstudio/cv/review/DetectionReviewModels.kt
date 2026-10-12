package com.artstudio.cv.review

import java.util.UUID

/**
 * Display modes for visual source-versus-detection inspection.
 */
enum class OverlayDisplayMode(val displayName: String) {
    /** Show original imported reference image only */
    SOURCE_ONLY("Source Reference"),

    /** Show extracted vector paths and segmented regions only */
    DETECTION_ONLY("Detected Vectors & Masks"),

    /** Split view comparing source and detection */
    SPLIT_COMPARE("Split Compare"),

    /** Alpha-blended composite with adjustable opacity */
    ALPHA_BLEND("Alpha Blend Overlay"),

    /** Color delta difference heatmap highlighting uncertain or leaking areas */
    DIFFERENCE_HEATMAP("Difference Heatmap")
}

/**
 * Categorization of detection uncertainties that require artist inspection or repair.
 */
enum class UncertaintyKind(val displayName: String, val icon: String) {
    /** Geometric vector path confidence is below threshold */
    LOW_CONFIDENCE_PATH("Low Confidence Path", "⚠️"),

    /** Skeleton junction with 3+ diverging branches */
    AMBIGUOUS_FORK("Ambiguous Fork", "🔀"),

    /** Broken stroke gap between proximate endpoints */
    POTENTIAL_STROKE_BREAK("Stroke Break Gap", "⚡"),

    /** Unclosed region boundary permitting color spill */
    LEAKY_BOUNDARY("Leaky Boundary Gap", "💧"),

    /** Neural AI proposal contradicts classical CV boundary */
    CONTRADICTORY_AI_PROPOSAL("AI Contradiction", "🤖")
}

/**
 * Explicit uncertainty marker positioned on canvas coordinates for visual artist inspection.
 */
data class UncertaintyMarker(
    val id: String = UUID.randomUUID().toString(),
    val kind: UncertaintyKind,
    val location: Pair<Float, Float>,
    val severity: Float, // 0.0 (minor) to 1.0 (critical)
    val description: String,
    val suggestedAction: String,
    val targetEntityId: String,
    val isResolved: Boolean = false
) {
    init {
        require(severity in 0.0f..1.0f) { "Severity must be in [0, 1], got $severity" }
    }

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["id"] = id
        map["kind"] = kind.name
        map["locX"] = location.first
        map["locY"] = location.second
        map["severity"] = severity
        map["description"] = description
        map["suggestedAction"] = suggestedAction
        map["targetEntityId"] = targetEntityId
        map["isResolved"] = isResolved
        return map
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): UncertaintyMarker {
            return UncertaintyMarker(
                id = map["id"]?.toString() ?: UUID.randomUUID().toString(),
                kind = runCatching { UncertaintyKind.valueOf(map["kind"]?.toString() ?: "") }
                    .getOrDefault(UncertaintyKind.LOW_CONFIDENCE_PATH),
                location = Pair(
                    (map["locX"] as? Number)?.toFloat() ?: 0f,
                    (map["locY"] as? Number)?.toFloat() ?: 0f
                ),
                severity = (map["severity"] as? Number)?.toFloat() ?: 0.5f,
                description = map["description"]?.toString() ?: "",
                suggestedAction = map["suggestedAction"]?.toString() ?: "",
                targetEntityId = map["targetEntityId"]?.toString() ?: "",
                isResolved = (map["isResolved"] as? Boolean) ?: false
            )
        }
    }
}

/**
 * Summary metrics of detection review session.
 */
data class ReviewSessionSummary(
    val pathCount: Int,
    val regionCount: Int,
    val totalUncertainties: Int,
    val resolvedUncertainties: Int,
    val activeCorrections: Int,
    val pendingConflicts: Int
)
