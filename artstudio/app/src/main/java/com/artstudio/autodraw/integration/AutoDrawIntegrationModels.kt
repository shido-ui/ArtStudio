package com.artstudio.autodraw.integration

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.BlendModeType
import com.artstudio.vector.VectorObject
import java.util.UUID

/**
 * Functional role classification for AutoDraw generated layers in manual art documents.
 */
enum class AutoDrawLayerRole(val defaultName: String, val description: String) {
    BASE_COLOUR("Base Colour", "Flat base fills mapped from source palette regions"),
    SHADOWS("Shadows", "Cel or soft shading tonal pass"),
    HIGHLIGHTS("Highlights", "Specular rim or soft highlights pass"),
    TEXTURES("Textures", "Medium texture, screentone, or grain pass"),
    LINEWORK("Reconstructed Linework", "Raster linework generated from detected contours"),
    PATHS("Editable Paths", "Vector paths with editable Bezier anchors and style properties"),
    MANUAL_EDITS("Manual Edits", "Dedicated user brush/eraser/color edit layer");

    companion object {
        fun fromString(value: String): AutoDrawLayerRole {
            return values().find { it.name.equals(value, ignoreCase = true) } ?: MANUAL_EDITS
        }
    }
}

/**
 * Reconstruction configuration recording source parameters and algorithms.
 */
data class AutoDrawReconstructionConfig(
    val sourceImageHash: String? = null,
    val algorithm: String = "autodraw_hybrid_v1",
    val stylePresetId: String = "classic_cel",
    val edgeThresholdLow: Float = 50f,
    val edgeThresholdHigh: Float = 150f,
    val timestampMs: Long = System.currentTimeMillis(),
    val customParameters: Map<String, String> = emptyMap()
)

/**
 * Provenance tracking linking document elements back to detected source regions.
 */
data class RegionProvenanceInfo(
    val regionId: String,
    val regionName: String,
    val colorRgb: Int,
    val semanticRole: String,
    val layerIds: List<String> = emptyList(),
    val pathIds: List<String> = emptyList(),
    val isLocked: Boolean = false
)

/**
 * Category of manual correction applied by the artist.
 */
enum class UserOverrideType {
    BRUSH_EDIT,
    PATH_ANCHOR_EDIT,
    RECOLOUR,
    ERASE,
    LAYER_REORDER,
    CUSTOM
}

/**
 * Explicit record of an artist's manual override or correction.
 */
data class UserOverrideRecord(
    val overrideId: String = UUID.randomUUID().toString(),
    val regionId: String,
    val overrideType: UserOverrideType,
    val description: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val layerId: String,
    val details: Map<String, String> = emptyMap()
)

/**
 * Strategy for reconciling new region reanalysis with existing user manual overrides.
 */
enum class ConflictResolutionStrategy(val displayName: String) {
    /** Preserves artist's manual overrides without overwriting (default) */
    PRESERVE_USER_OVERRIDES("Preserve User Overrides"),

    /** Replaces region artwork with fresh reanalysis results */
    OVERWRITE_WITH_REANALYSIS("Overwrite with Fresh Reanalysis"),

    /** Retains user edits on existing layer and outputs reanalysis into a dedicated new layer */
    MERGE_INTO_NEW_LAYER("Merge into New Parallel Layer")
}

/**
 * Conflict event raised when reanalysis encounters existing manual overrides.
 */
data class ConflictResolutionEvent(
    val regionId: String,
    val conflictDescription: String,
    val conflictingOverrides: List<UserOverrideRecord>,
    val resolvedStrategy: ConflictResolutionStrategy? = null
)

/**
 * Input definition for a single layer within an approved AutoDraw result.
 */
data class AutoDrawLayerInput(
    val role: AutoDrawLayerRole,
    val name: String? = null,
    val pixels: IntArray? = null,
    val vectorObjects: List<VectorObject> = emptyList(),
    val blendMode: BlendModeType = BlendModeType.Normal,
    val opacity: Float = 1.0f,
    val regionIds: List<String> = emptyList()
) {
    init {
        // Strict Invariant: Never represent a flattened raster effect as editable geometry
        if (role == AutoDrawLayerRole.PATHS) {
            require(pixels == null) {
                "Vector PATHS layer must not contain flattened raster pixel buffers"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AutoDrawLayerInput) return false
        return role == other.role &&
                name == other.name &&
                blendMode == other.blendMode &&
                opacity == other.opacity &&
                regionIds == other.regionIds &&
                vectorObjects == other.vectorObjects &&
                ((pixels == null && other.pixels == null) || (pixels != null && other.pixels != null && pixels.contentEquals(other.pixels)))
    }

    override fun hashCode(): Int {
        var result = role.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + blendMode.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + regionIds.hashCode()
        result = 31 * result + vectorObjects.hashCode()
        result = 31 * result + (pixels?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Approved AutoDraw result container ready for integration into a manual document.
 */
data class AutoDrawResult(
    val config: AutoDrawReconstructionConfig,
    val regions: List<RegionProvenanceInfo>,
    val layers: List<AutoDrawLayerInput>,
    val width: Int = 2048,
    val height: Int = 2048
)

/**
 * Output of document integration including updated document, group ID, and telemetry.
 */
data class IntegrationResult(
    val document: ArtDocument,
    val autoDrawGroupId: String,
    val createdLayerIds: List<String>,
    val roleToLayerId: Map<AutoDrawLayerRole, String>,
    val conflicts: List<ConflictResolutionEvent> = emptyList()
)
