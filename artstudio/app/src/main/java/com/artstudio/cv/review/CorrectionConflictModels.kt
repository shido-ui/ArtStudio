package com.artstudio.cv.review

import java.util.UUID

/**
 * Root cause for an identity mapping mismatch following reanalysis.
 */
enum class ConflictReason(val displayName: String) {
    /** The targeted entity ID was completely removed or not found in new detections */
    TARGET_DISAPPEARED("Target Entity Disappeared"),

    /** The target entity's geometry, bounds, or area changed drastically (>75% shift) */
    GEOMETRY_DRASTICALLY_CHANGED("Geometry Drastically Changed"),

    /** Multiple new entities overlap the previous target with similar probability */
    AMBIGUOUS_REMAP("Ambiguous Remap Candidates"),

    /** Secondary target for a merge operation is missing */
    SECONDARY_TARGET_MISSING("Secondary Target Missing")
}

/**
 * Artist-selected or suggested resolution strategy for a correction identity conflict.
 */
enum class ConflictResolutionStrategy(val displayName: String, val description: String) {
    /** Awaiting explicit artist decision; correction is suspended */
    PENDING_USER_REVIEW("Pending Artist Review", "Requires artist decision before applying."),

    /** Reapply the correction to the best matching candidate entity */
    REAPPLY_CLOSEST_MATCH("Reapply to Closest Match", "Apply original edit to closest new entity."),

    /** Accept the new detection output as authoritative and discard the old correction */
    KEEP_NEW_DETECTION("Keep New Detection", "Discard previous override and use new detection."),

    /** Explicitly remove this correction from the project history */
    DISCARD_CORRECTION("Discard Override", "Permanently discard the conflicting edit.")
}

/**
 * Visual conflict record surfaced to the artist when reanalysis invalidates an existing correction.
 *
 * In accordance with Prompt 56:
 * "Reanalysis must preserve corrections, selected regions and written requirements wherever
 * their source identities still map; if mapping becomes invalid, ask the user instead of
 * silently discarding them."
 */
data class CorrectionIdentityConflict(
    val id: String = UUID.randomUUID().toString(),
    val correction: DetectionCorrection,
    val reason: ConflictReason,
    val candidateTargetIds: List<String> = emptyList(),
    val explanation: String,
    val resolution: ConflictResolutionStrategy = ConflictResolutionStrategy.PENDING_USER_REVIEW,
    val chosenTargetId: String? = null,
    val isResolved: Boolean = false,
    val detectedAtMs: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["id"] = id
        map["correction"] = correction.toMap()
        map["reason"] = reason.name
        map["candidateTargetIds"] = candidateTargetIds
        map["explanation"] = explanation
        map["resolution"] = resolution.name
        chosenTargetId?.let { map["chosenTargetId"] = it }
        map["isResolved"] = isResolved
        map["detectedAtMs"] = detectedAtMs
        return map
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): CorrectionIdentityConflict {
            @Suppress("UNCHECKED_CAST")
            val corrMap = map["correction"] as? Map<String, Any?> ?: emptyMap()
            val correction = DetectionCorrection.fromMap(corrMap)

            val reasonName = map["reason"]?.toString() ?: ConflictReason.TARGET_DISAPPEARED.name
            val reason = runCatching { ConflictReason.valueOf(reasonName) }
                .getOrDefault(ConflictReason.TARGET_DISAPPEARED)

            @Suppress("UNCHECKED_CAST")
            val candidates = (map["candidateTargetIds"] as? List<*>)
                ?.mapNotNull { it?.toString() } ?: emptyList()

            val resName = map["resolution"]?.toString() ?: ConflictResolutionStrategy.PENDING_USER_REVIEW.name
            val resolution = runCatching { ConflictResolutionStrategy.valueOf(resName) }
                .getOrDefault(ConflictResolutionStrategy.PENDING_USER_REVIEW)

            return CorrectionIdentityConflict(
                id = map["id"]?.toString() ?: UUID.randomUUID().toString(),
                correction = correction,
                reason = reason,
                candidateTargetIds = candidates,
                explanation = map["explanation"]?.toString() ?: "",
                resolution = resolution,
                chosenTargetId = map["chosenTargetId"]?.toString(),
                isResolved = (map["isResolved"] as? Boolean) ?: false,
                detectedAtMs = (map["detectedAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
