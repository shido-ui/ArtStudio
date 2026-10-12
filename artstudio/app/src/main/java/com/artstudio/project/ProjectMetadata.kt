package com.artstudio.project

import java.util.UUID

/**
 * Metadata record for a local Art Studio document / project.
 * Separates lightweight project metadata from expensive full-resolution preview decoding.
 */
data class ProjectMetadata(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val width: Int,
    val height: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val strokeCount: Int = 0,
    val layerCount: Int = 1,
    val thumbnailColor: Long = 0xFF37474FL,
    val fileUri: String? = null,
    val filePath: String? = null,
    val folder: String? = null,
    val tags: List<String> = emptyList(),
    val hasRecoveryAvailable: Boolean = false,
    val isAccessible: Boolean = true,
    val missingAssetsWarning: String? = null,
    val isDirty: Boolean = false
) {
    val dimensionsLabel: String
        get() = "${width}x${height} px"

    fun copyWithUpdatedStrokes(newStrokeCount: Int, isDirty: Boolean = true): ProjectMetadata {
        return this.copy(
            strokeCount = newStrokeCount,
            isDirty = isDirty,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun markClean(): ProjectMetadata {
        return this.copy(isDirty = false, updatedAt = System.currentTimeMillis())
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "width" to width,
        "height" to height,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "strokeCount" to strokeCount,
        "layerCount" to layerCount,
        "thumbnailColor" to thumbnailColor,
        "fileUri" to fileUri,
        "filePath" to filePath,
        "folder" to folder,
        "tags" to tags,
        "hasRecoveryAvailable" to hasRecoveryAvailable,
        "isAccessible" to isAccessible,
        "missingAssetsWarning" to missingAssetsWarning,
        "isDirty" to isDirty
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ProjectMetadata {
            @Suppress("UNCHECKED_CAST")
            val rawTags = (map["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            return ProjectMetadata(
                id = map["id"]?.toString() ?: UUID.randomUUID().toString(),
                title = map["title"]?.toString() ?: "Untitled",
                width = (map["width"] as? Number)?.toInt() ?: 2048,
                height = (map["height"] as? Number)?.toInt() ?: 2048,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                strokeCount = (map["strokeCount"] as? Number)?.toInt() ?: 0,
                layerCount = (map["layerCount"] as? Number)?.toInt() ?: 1,
                thumbnailColor = (map["thumbnailColor"] as? Number)?.toLong() ?: 0xFF37474FL,
                fileUri = map["fileUri"]?.toString(),
                filePath = map["filePath"]?.toString(),
                folder = map["folder"]?.toString(),
                tags = rawTags,
                hasRecoveryAvailable = (map["hasRecoveryAvailable"] as? Boolean) ?: false,
                isAccessible = (map["isAccessible"] as? Boolean) ?: true,
                missingAssetsWarning = map["missingAssetsWarning"]?.toString(),
                isDirty = (map["isDirty"] as? Boolean) ?: false
            )
        }
    }
}
