package com.artstudio.cv.review

import com.artstudio.cv.CvPoint
import java.util.UUID

/**
 * Explicit repair and override operations available to the artist during detection review.
 */
enum class CorrectionOpType(val displayName: String, val icon: String) {
    /** Translate a vector path by delta (dx, dy) */
    MOVE_PATH("Move Path", "↔️"),

    /** Mark a vector path as deleted/suppressed */
    DELETE_PATH("Delete Path", "🗑️"),

    /** Split a vector path into two sub-paths at anchor index or split point */
    SPLIT_PATH("Split Path", "✂️"),

    /** Merge two open vector paths end-to-end into a single continuous path */
    MERGE_PATHS("Merge Paths", "🔗"),

    /** Translate a segmented region mask by pixel offset (dx, dy) */
    MOVE_REGION("Move Region", "📦"),

    /** Delete/suppress a segmented region mask */
    DELETE_REGION("Delete Region", "❌"),

    /** Split a segmented region mask into two parts along a dividing cut */
    SPLIT_REGION("Split Region", "🪓"),

    /** Union/merge two segmented region masks into one */
    MERGE_REGIONS("Merge Regions", "🧩"),

    /** Bridge an open boundary gap between two endpoints to seal leaks */
    BRIDGE_GAP("Bridge Gap", "🌉"),

    /** Update or override the semantic tag / color attribute of a region */
    TAG_REGION("Tag Region", "🏷️")
}

/**
 * Immutable record of an artist's explicit correction or repair operation.
 *
 * In accordance with Prompt 56:
 * - Corrections are preserved as explicit override operations.
 * - Reanalysis replays corrections onto updated detections.
 * - If target entity identity cannot be mapped, an explicit conflict is raised
 *   instead of silently discarding the artist's work.
 */
data class DetectionCorrection(
    val id: String = UUID.randomUUID().toString(),
    val opType: CorrectionOpType,
    val targetId: String,
    val secondaryTargetId: String? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val timestampMs: Long = System.currentTimeMillis(),
    val description: String = "",
    val isActive: Boolean = true
) {
    // Typed parameter helpers
    val dx: Float get() = (parameters["dx"] as? Number)?.toFloat() ?: 0f
    val dy: Float get() = (parameters["dy"] as? Number)?.toFloat() ?: 0f
    val splitIndex: Int get() = (parameters["splitIndex"] as? Number)?.toInt() ?: 1
    val splitX: Int get() = (parameters["splitX"] as? Number)?.toInt() ?: 0
    val splitY: Int get() = (parameters["splitY"] as? Number)?.toInt() ?: 0
    val isVerticalSplit: Boolean get() = (parameters["isVertical"] as? Boolean) ?: true
    val strokeThickness: Int get() = (parameters["thickness"] as? Number)?.toInt() ?: 2
    val tag: String get() = parameters["tag"]?.toString() ?: ""

    val bridgeStart: CvPoint?
        get() {
            val sx = (parameters["startX"] as? Number)?.toFloat() ?: return null
            val sy = (parameters["startY"] as? Number)?.toFloat() ?: return null
            return CvPoint(sx, sy)
        }

    val bridgeEnd: CvPoint?
        get() {
            val ex = (parameters["endX"] as? Number)?.toFloat() ?: return null
            val ey = (parameters["endY"] as? Number)?.toFloat() ?: return null
            return CvPoint(ex, ey)
        }

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["id"] = id
        map["opType"] = opType.name
        map["targetId"] = targetId
        secondaryTargetId?.let { map["secondaryTargetId"] = it }
        map["parameters"] = parameters
        map["timestampMs"] = timestampMs
        map["description"] = description
        map["isActive"] = isActive
        return map
    }

    companion object {
        fun movePath(pathId: String, dx: Float, dy: Float): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.MOVE_PATH,
                targetId = pathId,
                parameters = mapOf("dx" to dx, "dy" to dy),
                description = "Translate path $pathId by (%.1f, %.1f)".format(dx, dy)
            )

        fun deletePath(pathId: String): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.DELETE_PATH,
                targetId = pathId,
                description = "Delete detected path $pathId"
            )

        fun splitPath(pathId: String, splitIndex: Int): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.SPLIT_PATH,
                targetId = pathId,
                parameters = mapOf("splitIndex" to splitIndex),
                description = "Split path $pathId at anchor $splitIndex"
            )

        fun mergePaths(pathId1: String, pathId2: String): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.MERGE_PATHS,
                targetId = pathId1,
                secondaryTargetId = pathId2,
                description = "Merge paths $pathId1 and $pathId2"
            )

        fun moveRegion(regionId: String, dx: Float, dy: Float): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.MOVE_REGION,
                targetId = regionId,
                parameters = mapOf("dx" to dx, "dy" to dy),
                description = "Translate region $regionId by (${dx.toInt()}, ${dy.toInt()})"
            )

        fun deleteRegion(regionId: String): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.DELETE_REGION,
                targetId = regionId,
                description = "Delete detected region $regionId"
            )

        fun splitRegion(regionId: String, splitCoord: Int, isVertical: Boolean = true): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.SPLIT_REGION,
                targetId = regionId,
                parameters = mapOf("splitX" to splitCoord, "isVertical" to isVertical),
                description = "Split region $regionId along ${if (isVertical) "X=$splitCoord" else "Y=$splitCoord"}"
            )

        fun mergeRegions(regionId1: String, regionId2: String): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.MERGE_REGIONS,
                targetId = regionId1,
                secondaryTargetId = regionId2,
                description = "Merge regions $regionId1 and $regionId2"
            )

        fun bridgeGap(
            regionId: String,
            start: CvPoint,
            end: CvPoint,
            thickness: Int = 2
        ): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.BRIDGE_GAP,
                targetId = regionId,
                parameters = mapOf(
                    "startX" to start.x,
                    "startY" to start.y,
                    "endX" to end.x,
                    "endY" to end.y,
                    "thickness" to thickness
                ),
                description = "Bridge gap in $regionId from (${start.x.toInt()}, ${start.y.toInt()}) to (${end.x.toInt()}, ${end.y.toInt()})"
            )

        fun tagRegion(regionId: String, tag: String): DetectionCorrection =
            DetectionCorrection(
                opType = CorrectionOpType.TAG_REGION,
                targetId = regionId,
                parameters = mapOf("tag" to tag),
                description = "Tag region $regionId as '$tag'"
            )

        fun fromMap(map: Map<String, Any?>): DetectionCorrection {
            val opName = map["opType"]?.toString() ?: CorrectionOpType.MOVE_PATH.name
            val opType = runCatching { CorrectionOpType.valueOf(opName) }
                .getOrDefault(CorrectionOpType.MOVE_PATH)

            @Suppress("UNCHECKED_CAST")
            val params = (map["parameters"] as? Map<String, Any>) ?: emptyMap()

            return DetectionCorrection(
                id = map["id"]?.toString() ?: UUID.randomUUID().toString(),
                opType = opType,
                targetId = map["targetId"]?.toString() ?: "",
                secondaryTargetId = map["secondaryTargetId"]?.toString(),
                parameters = params,
                timestampMs = (map["timestampMs"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                description = map["description"]?.toString() ?: "",
                isActive = (map["isActive"] as? Boolean) ?: true
            )
        }
    }
}
