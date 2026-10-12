package com.artstudio.reference.region

import com.artstudio.document.io.ArtJson
import com.artstudio.reference.ReconstructionMode
import java.io.File
import java.util.Base64

/**
 * Pure-Kotlin serialization engine for [ReferenceAnalysisProject].
 *
 * Persists region masks, instructions, settings, and provenance metadata
 * without mutating or altering the preserved reference source file on disk.
 */
object ReferenceAnalysisProjectSerializer {

    /**
     * Serializes [project] into an auditable JSON string.
     */
    fun toJson(project: ReferenceAnalysisProject, indent: Int = 2): String {
        val rootMap = mutableMapOf<String, Any?>()
        rootMap["schemaVersion"] = 1
        rootMap["projectId"] = project.projectId
        rootMap["projectName"] = project.projectName
        rootMap["createdAt"] = project.createdAt
        rootMap["updatedAt"] = project.updatedAt
        project.activeRegionId?.let { rootMap["activeRegionId"] = it }

        // 1. Reference Source Metadata
        val src = project.referenceSource
        rootMap["referenceSource"] = mapOf(
            "sourceName" to src.sourceName,
            "sourceSha256" to src.sourceSha256,
            "rawWidth" to src.rawWidth,
            "rawHeight" to src.rawHeight,
            "normalizedWidth" to src.normalizedWidth,
            "normalizedHeight" to src.normalizedHeight,
            "exifOrientation" to src.exifOrientation
        )

        // 2. Crop Bounds (if active)
        project.cropBounds?.let { crop ->
            rootMap["cropBounds"] = mapOf(
                "x" to crop.x,
                "y" to crop.y,
                "width" to crop.width,
                "height" to crop.height
            )
        }

        // 3. Named Focus Regions
        rootMap["regions"] = project.regions.map { reg ->
            val regMap = mutableMapOf<String, Any?>()
            regMap["id"] = reg.id
            regMap["name"] = reg.name
            regMap["instructions"] = reg.instructions
            regMap["width"] = reg.width
            regMap["height"] = reg.height
            regMap["colorRgba"] = reg.colorRgba
            regMap["overlayOpacity"] = reg.overlayOpacity
            regMap["isVisible"] = reg.isVisible
            regMap["isLocked"] = reg.isLocked
            regMap["createdAt"] = reg.createdAt
            regMap["updatedAt"] = reg.updatedAt

            // Focus settings
            val set = reg.settings
            val setMap = mutableMapOf<String, Any?>()
            setMap["priority"] = set.priority.name
            set.targetReconstructionMode?.let { setMap["targetReconstructionMode"] = it.name }
            setMap["detailSensitivity"] = set.detailSensitivity
            setMap["preserveExactColor"] = set.preserveExactColor
            setMap["edgeDilationRadius"] = set.edgeDilationRadius
            setMap["customParameters"] = set.customParameters
            regMap["settings"] = setMap

            // Base64-encoded 8-bit mask buffer
            regMap["maskBase64"] = Base64.getEncoder().encodeToString(reg.mask)
            regMap
        }

        return ArtJson.stringify(rootMap, indent)
    }

    /**
     * Deserializes a [ReferenceAnalysisProject] from JSON text.
     */
    @Suppress("UNCHECKED_CAST")
    fun fromJson(jsonStr: String): ReferenceAnalysisProject {
        val root = ArtJson.parse(jsonStr) as? Map<String, Any?>
            ?: throw IllegalArgumentException("Invalid project JSON: root must be an object")

        val projectId = root["projectId"] as? String ?: throw IllegalArgumentException("Missing projectId")
        val projectName = root["projectName"] as? String ?: "Untitled Analysis"
        val createdAt = (root["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = (root["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val activeRegionId = root["activeRegionId"] as? String

        // Reference source
        val srcMap = root["referenceSource"] as? Map<String, Any?>
            ?: throw IllegalArgumentException("Missing referenceSource metadata")
        val refSource = ReferenceSourceMetadata(
            sourceName = srcMap["sourceName"] as? String ?: "unknown",
            sourceSha256 = srcMap["sourceSha256"] as? String ?: "",
            rawWidth = (srcMap["rawWidth"] as? Number)?.toInt() ?: 1,
            rawHeight = (srcMap["rawHeight"] as? Number)?.toInt() ?: 1,
            normalizedWidth = (srcMap["normalizedWidth"] as? Number)?.toInt() ?: 1,
            normalizedHeight = (srcMap["normalizedHeight"] as? Number)?.toInt() ?: 1,
            exifOrientation = (srcMap["exifOrientation"] as? Number)?.toInt() ?: 1
        )

        // Crop bounds
        val cropBounds = (root["cropBounds"] as? Map<String, Any?>)?.let { cMap ->
            ReferenceCropBounds(
                x = (cMap["x"] as? Number)?.toInt() ?: 0,
                y = (cMap["y"] as? Number)?.toInt() ?: 0,
                width = (cMap["width"] as? Number)?.toInt() ?: 1,
                height = (cMap["height"] as? Number)?.toInt() ?: 1
            )
        }

        // Regions
        val regionList = (root["regions"] as? List<Map<String, Any?>>)?.map { rMap ->
            val id = rMap["id"] as? String ?: throw IllegalArgumentException("Missing region id")
            val name = rMap["name"] as? String ?: "Region"
            val instructions = rMap["instructions"] as? String ?: ""
            val w = (rMap["width"] as? Number)?.toInt() ?: 1
            val h = (rMap["height"] as? Number)?.toInt() ?: 1
            val colorRgba = (rMap["colorRgba"] as? Number)?.toInt() ?: 0xFF00E5FF.toInt()
            val overlayOpacity = (rMap["overlayOpacity"] as? Number)?.toFloat() ?: 0.45f
            val isVisible = rMap["isVisible"] as? Boolean ?: true
            val isLocked = rMap["isLocked"] as? Boolean ?: false
            val regCreatedAt = (rMap["createdAt"] as? Number)?.toLong() ?: createdAt
            val regUpdatedAt = (rMap["updatedAt"] as? Number)?.toLong() ?: updatedAt

            // Settings
            val setMap = rMap["settings"] as? Map<String, Any?> ?: emptyMap()
            val priorityName = setMap["priority"] as? String ?: FocusPriority.NORMAL.name
            val priority = try { FocusPriority.valueOf(priorityName) } catch (_: Exception) { FocusPriority.NORMAL }
            val modeName = setMap["targetReconstructionMode"] as? String
            val mode = modeName?.let { try { ReconstructionMode.valueOf(it) } catch (_: Exception) { null } }
            val sensitivity = (setMap["detailSensitivity"] as? Number)?.toFloat() ?: 0.5f
            val exactColor = setMap["preserveExactColor"] as? Boolean ?: false
            val dilation = (setMap["edgeDilationRadius"] as? Number)?.toInt() ?: 0
            val customParams = (setMap["customParameters"] as? Map<String, String>) ?: emptyMap()

            val settings = ReferenceFocusSettings(
                priority = priority,
                targetReconstructionMode = mode,
                detailSensitivity = sensitivity,
                preserveExactColor = exactColor,
                edgeDilationRadius = dilation,
                customParameters = customParams
            )

            // Decode mask
            val maskBase64 = rMap["maskBase64"] as? String
                ?: throw IllegalArgumentException("Missing maskBase64 for region '$name'")
            val maskBytes = Base64.getDecoder().decode(maskBase64)
            require(maskBytes.size == w * h) {
                "Decoded mask length (${maskBytes.size}) does not match $w x $h"
            }

            NamedReferenceRegion(
                id = id,
                name = name,
                instructions = instructions,
                settings = settings,
                width = w,
                height = h,
                mask = maskBytes,
                colorRgba = colorRgba,
                overlayOpacity = overlayOpacity,
                isVisible = isVisible,
                isLocked = isLocked,
                createdAt = regCreatedAt,
                updatedAt = regUpdatedAt
            )
        } ?: emptyList()

        return ReferenceAnalysisProject(
            projectId = projectId,
            projectName = projectName,
            referenceSource = refSource,
            cropBounds = cropBounds,
            regions = regionList,
            activeRegionId = activeRegionId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Saves the analysis project to [targetFile].
     * Never mutates or touches the preserved reference image source file.
     */
    fun saveToFile(project: ReferenceAnalysisProject, targetFile: File) {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val json = toJson(project, indent = 2)
        targetFile.writeText(json)
    }

    /**
     * Loads a project from [sourceFile].
     */
    fun loadFromFile(sourceFile: File): ReferenceAnalysisProject {
        require(sourceFile.exists()) { "Project file does not exist: ${sourceFile.path}" }
        val json = sourceFile.readText()
        return fromJson(json)
    }
}
