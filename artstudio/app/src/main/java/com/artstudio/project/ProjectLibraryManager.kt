package com.artstudio.project

import com.artstudio.document.io.ArtJson
import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.io.PackageData
import com.artstudio.document.lifecycle.DefaultStorageDriver
import com.artstudio.document.lifecycle.DocumentRecoveryManager
import com.artstudio.document.lifecycle.StorageDriver
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import java.io.File
import java.io.IOException
import java.util.UUID

sealed class ProjectOpenResult {
    data class Success(val metadata: ProjectMetadata, val packageData: PackageData) : ProjectOpenResult()
    data class RecoveryAvailable(val metadata: ProjectMetadata, val recoveryFile: File, val revision: Long) : ProjectOpenResult()
    data class Error(val message: String, val cause: Throwable? = null) : ProjectOpenResult()
}

/**
 * Manages project indexing, lifecycle operations (duplicate, rename, soft-delete, undo),
 * folder grouping, recent files, and recovery detection.
 */
class ProjectLibraryManager(
    val projectsDir: File,
    val storageDriver: StorageDriver = DefaultStorageDriver(),
    val thumbnailCache: ThumbnailCache = ThumbnailCache()
) {
    private val indexFile = File(projectsDir, "projects_index.json")
    val recoveryDir = File(projectsDir, "recovery").apply { if (!exists()) mkdirs() }
    val trashDir = File(projectsDir, "trash").apply { if (!exists()) mkdirs() }
    val recoveryManager = DocumentRecoveryManager(recoveryDir, storageDriver)

    private val _projects = mutableListOf<ProjectMetadata>()
    val projects: List<ProjectMetadata>
        get() = synchronized(_projects) { _projects.toList() }

    private val _trashedProjects = mutableListOf<Pair<ProjectMetadata, File>>()
    val trashedProjects: List<ProjectMetadata>
        get() = synchronized(_trashedProjects) { _trashedProjects.map { it.first } }

    var activeProject: ProjectMetadata? = null
        private set

    init {
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }
        loadIndex()
    }

    /**
     * Creates a new blank project package and registers it in the library.
     */
    fun createNewProject(
        title: String,
        width: Int = 2048,
        height: Int = 2048,
        folder: String? = null,
        tags: List<String> = emptyList()
    ): ProjectMetadata {
        val validation = com.artstudio.core.CanvasMemoryBudgetValidator.validateCanvas(width, height)
        if (validation is com.artstudio.core.CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected) {
            throw IllegalArgumentException(validation.rejectionReason)
        }

        val sanitizedTitle = if (title.isBlank()) "Untitled Artwork" else title.trim()
        val newId = UUID.randomUUID().toString()
        val projectFile = File(projectsDir, "$newId.ffart")

        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = newId,
            title = sanitizedTitle,
            canvas = CanvasConfig(width, height),
            rootGroup = LayerNode.LayerGroup(
                id = "root_group",
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = "layer_base_1",
                        name = "Background",
                        width = width,
                        height = height
                    )
                )
            ),
            palettes = emptyList(),
            referenceImages = emptyList(),
            animation = null,
            autoDrawProvenance = null
        )

        // Write initial package to disk atomically
        storageDriver.writeAtomic(projectFile) { tempFile ->
            DocumentPackage.save(doc, emptyMap(), tempFile)
        }

        val metadata = ProjectMetadata(
            id = newId,
            title = sanitizedTitle,
            width = width,
            height = height,
            filePath = projectFile.absolutePath,
            folder = folder,
            tags = tags,
            thumbnailColor = selectThumbnailColor(_projects.size),
            isDirty = false
        )

        synchronized(_projects) {
            _projects.add(0, metadata)
            activeProject = metadata
        }
        saveIndex()
        return metadata
    }

    /**
     * Registers an existing project file (e.g. from fixtures or imports).
     */
    fun registerExistingProject(file: File, folder: String? = null): Result<ProjectMetadata> {
        if (!file.exists()) {
            return Result.failure(IOException("File does not exist: ${file.absolutePath}"))
        }

        return try {
            val packageData = DocumentPackage.load(file)
            val doc = packageData.document
            val metadata = ProjectMetadata(
                id = doc.id,
                title = doc.title,
                width = doc.canvas.width,
                height = doc.canvas.height,
                createdAt = doc.createdAt,
                updatedAt = doc.updatedAt,
                filePath = file.absolutePath,
                folder = folder,
                thumbnailColor = selectThumbnailColor(_projects.size),
                isDirty = false
            )

            // Cache thumbnail if available
            packageData.thumbnail?.let {
                thumbnailCache.put(doc.id, it)
            }

            synchronized(_projects) {
                _projects.removeAll { it.id == doc.id }
                _projects.add(0, metadata)
            }
            saveIndex()
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Opens a project safely, checking for accessibility, missing assets, and uncommitted recovery material.
     */
    fun openProject(id: String): ProjectOpenResult {
        val metadata = synchronized(_projects) { _projects.find { it.id == id } }
            ?: return ProjectOpenResult.Error("Project with ID $id not found in library")

        val targetFile = if (metadata.filePath != null) File(metadata.filePath) else File(projectsDir, "$id.ffart")

        // Inaccessible file / missing URI check
        if (!targetFile.exists()) {
            val updated = metadata.copy(isAccessible = false)
            updateMetadata(updated)
            return ProjectOpenResult.Error("Project file is missing or unmounted: ${targetFile.absolutePath}")
        }

        // Check for recovery availability
        val recovery = recoveryManager.checkRecovery(id, targetFile)
        if (recovery != null) {
            val updated = metadata.copy(hasRecoveryAvailable = true)
            updateMetadata(updated)
            return ProjectOpenResult.RecoveryAvailable(updated, recovery.recoveryFile, recovery.backupRevision)
        }

        return try {
            val packageData = DocumentPackage.load(targetFile)

            // Reorder recent list to index 0
            synchronized(_projects) {
                _projects.remove(metadata)
                val refreshed = metadata.copy(
                    updatedAt = System.currentTimeMillis(),
                    isAccessible = true,
                    hasRecoveryAvailable = false
                )
                _projects.add(0, refreshed)
                activeProject = refreshed
            }
            saveIndex()

            ProjectOpenResult.Success(activeProject!!, packageData)
        } catch (e: Exception) {
            ProjectOpenResult.Error("Failed to open project package: ${e.message}", e)
        }
    }

    /**
     * Duplicates a project, generating a new stable ID and unique title.
     * Preserves original project intact.
     */
    fun duplicateProject(id: String, customTitle: String? = null): Result<ProjectMetadata> {
        val original = synchronized(_projects) { _projects.find { it.id == id } }
            ?: return Result.failure(IllegalArgumentException("Original project $id not found"))

        val sourceFile = if (original.filePath != null) File(original.filePath) else File(projectsDir, "$id.ffart")
        if (!sourceFile.exists()) {
            return Result.failure(IOException("Original project file not found: ${sourceFile.absolutePath}"))
        }

        return try {
            val packageData = DocumentPackage.load(sourceFile)
            val newId = UUID.randomUUID().toString()
            val newTitle = customTitle ?: generateDisambiguatedTitle(original.title)

            val duplicatedDoc = packageData.document.copy(
                id = newId,
                title = newTitle,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val destFile = File(projectsDir, "$newId.ffart")
            storageDriver.writeAtomic(destFile) { tempFile ->
                DocumentPackage.save(
                    document = duplicatedDoc,
                    assets = packageData.assets,
                    outputFile = tempFile,
                    thumbnail = packageData.thumbnail
                )
            }

            // Cache thumbnail for duplicate
            packageData.thumbnail?.let { thumbnailCache.put(newId, it) }

            val duplicatedMetadata = original.copy(
                id = newId,
                title = newTitle,
                createdAt = duplicatedDoc.createdAt,
                updatedAt = duplicatedDoc.updatedAt,
                filePath = destFile.absolutePath,
                hasRecoveryAvailable = false,
                isDirty = false
            )

            synchronized(_projects) {
                val originalIndex = _projects.indexOfFirst { it.id == id }
                if (originalIndex != -1) {
                    _projects.add(originalIndex + 1, duplicatedMetadata)
                } else {
                    _projects.add(0, duplicatedMetadata)
                }
            }
            saveIndex()
            Result.success(duplicatedMetadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Renames a project safely. Updates metadata and the internal package document.
     */
    fun renameProject(id: String, newTitle: String): Result<ProjectMetadata> {
        if (newTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Project title cannot be blank"))
        }
        val target = synchronized(_projects) { _projects.find { it.id == id } }
            ?: return Result.failure(IllegalArgumentException("Project $id not found"))

        val trimmedTitle = newTitle.trim()
        val file = if (target.filePath != null) File(target.filePath) else File(projectsDir, "$id.ffart")

        if (file.exists()) {
            try {
                val pkg = DocumentPackage.load(file)
                val updatedDoc = pkg.document.copy(title = trimmedTitle, updatedAt = System.currentTimeMillis())
                storageDriver.writeAtomic(file) { tempFile ->
                    DocumentPackage.save(updatedDoc, pkg.assets, tempFile, pkg.thumbnail)
                }
            } catch (e: Exception) {
                return Result.failure(IOException("Failed to rename document within package: ${e.message}", e))
            }
        }

        val updated = target.copy(title = trimmedTitle, updatedAt = System.currentTimeMillis())
        updateMetadata(updated)
        saveIndex()
        return Result.success(updated)
    }

    /**
     * Moves a project to a folder.
     */
    fun moveToFolder(id: String, folderName: String?): Result<ProjectMetadata> {
        val target = synchronized(_projects) { _projects.find { it.id == id } }
            ?: return Result.failure(IllegalArgumentException("Project $id not found"))

        val updated = target.copy(folder = folderName?.trim()?.ifBlank { null }, updatedAt = System.currentTimeMillis())
        updateMetadata(updated)
        saveIndex()
        return Result.success(updated)
    }

    /**
     * Deletes a project. If [softDelete] is true, moves to trash with undo capability.
     */
    fun deleteProject(id: String, softDelete: Boolean = true): Boolean {
        val target = synchronized(_projects) { _projects.find { it.id == id } } ?: return false
        val file = if (target.filePath != null) File(target.filePath) else File(projectsDir, "$id.ffart")

        if (softDelete && file.exists()) {
            val trashFile = File(trashDir, "${id}_${file.name}")
            try {
                storageDriver.copy(file, trashFile)
                storageDriver.delete(file)
                synchronized(_trashedProjects) {
                    _trashedProjects.add(target to trashFile)
                }
            } catch (_: Exception) {
                // If soft delete copy fails, fallback to direct deletion
                storageDriver.delete(file)
            }
        } else if (file.exists()) {
            storageDriver.delete(file)
        }

        // Clean recovery and thumbnail caches
        recoveryManager.discardRecovery(id)
        thumbnailCache.remove(id)

        synchronized(_projects) {
            _projects.remove(target)
            if (activeProject?.id == id) {
                activeProject = null
            }
        }
        saveIndex()
        return true
    }

    /**
     * Restores a soft-deleted project from trash.
     */
    fun undoDelete(id: String): Result<ProjectMetadata> {
        val trashedEntry = synchronized(_trashedProjects) {
            val entry = _trashedProjects.find { it.first.id == id }
            if (entry != null) {
                _trashedProjects.remove(entry)
                entry
            } else null
        } ?: return Result.failure(IllegalStateException("No soft-deleted project found with ID $id"))

        val (metadata, trashFile) = trashedEntry
        val restoredFile = if (metadata.filePath != null) File(metadata.filePath) else File(projectsDir, "$id.ffart")

        if (trashFile.exists()) {
            try {
                storageDriver.copy(trashFile, restoredFile)
                storageDriver.delete(trashFile)
            } catch (e: Exception) {
                return Result.failure(IOException("Failed to restore project from trash: ${e.message}", e))
            }
        }

        synchronized(_projects) {
            _projects.add(0, metadata)
            activeProject = metadata
        }
        saveIndex()
        return Result.success(metadata)
    }

    /**
     * Lists all distinct folders in use.
     */
    fun listFolders(): List<String> {
        return synchronized(_projects) {
            _projects.mapNotNull { it.folder }.distinct().sorted()
        }
    }

    /**
     * Queries and sorts projects by query string, folder, tags, and sort order.
     */
    fun queryProjects(
        filter: ProjectSearchFilter = ProjectSearchFilter(),
        sortOrder: ProjectSortOrder = ProjectSortOrder.ModifiedDescending
    ): List<ProjectMetadata> {
        return projects.applyFilterAndSort(filter, sortOrder)
    }

    fun updateMetadata(updated: ProjectMetadata) {
        synchronized(_projects) {
            val index = _projects.indexOfFirst { it.id == updated.id }
            if (index != -1) {
                _projects[index] = updated
            }
            if (activeProject?.id == updated.id) {
                activeProject = updated
            }
        }
    }

    private fun generateDisambiguatedTitle(baseTitle: String): String {
        val copyRegex = Regex("""^(.*?)\s*\(Copy(?: (\d+))?\)$""")
        val match = copyRegex.matchEntire(baseTitle)

        return if (match != null) {
            val prefix = match.groupValues[1]
            val numStr = match.groupValues[2]
            val nextNum = if (numStr.isEmpty()) 2 else (numStr.toIntOrNull() ?: 1) + 1
            "$prefix (Copy $nextNum)"
        } else {
            "$baseTitle (Copy)"
        }
    }

    /**
     * Persists project index to JSON on disk.
     */
    fun saveIndex() {
        val listMap = synchronized(_projects) { _projects.map { it.toMap() } }
        val rootMap = mapOf(
            "version" to 1,
            "updatedAt" to System.currentTimeMillis(),
            "projects" to listMap
        )
        val json = ArtJson.stringify(rootMap)
        storageDriver.writeAtomic(indexFile) { tempFile ->
            tempFile.writeText(json, Charsets.UTF_8)
        }
    }

    /**
     * Loads project index from JSON on disk (state restoration after app restart).
     */
    fun loadIndex() {
        if (!indexFile.exists()) return

        try {
            val json = indexFile.readText(Charsets.UTF_8)
            val parsed = ArtJson.parse(json) as? Map<String, Any?> ?: return
            @Suppress("UNCHECKED_CAST")
            val rawList = parsed["projects"] as? List<*> ?: emptyList<Any?>()

            val loadedProjects = rawList.mapNotNull { item ->
                val itemMap = item as? Map<String, Any?> ?: return@mapNotNull null
                ProjectMetadata.fromMap(itemMap)
            }

            synchronized(_projects) {
                _projects.clear()
                _projects.addAll(loadedProjects)
                activeProject = _projects.firstOrNull()
            }
        } catch (_: Exception) {
            // Index loading error; keep in-memory list
        }
    }

    private fun selectThumbnailColor(index: Int): Long {
        val colors = listOf(
            0xFF37474FL,
            0xFF1E88E5L,
            0xFF00897BL,
            0xFF8E24AAL,
            0xFFD81B60L,
            0xFFFB8C00L
        )
        return colors[index % colors.size]
    }
}
