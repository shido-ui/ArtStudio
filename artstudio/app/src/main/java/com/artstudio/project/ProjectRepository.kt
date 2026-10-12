package com.artstudio.project

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Repository managing real project documents, recent projects list,
 * active project selection, search/filter, and Android Document Storage Access Framework (SAF) URI permissions.
 */
class ProjectRepository(
    initialProjects: List<ProjectMetadata> = emptyList(),
    val libraryManager: ProjectLibraryManager? = null
) {
    private val _projects = mutableListOf<ProjectMetadata>()
    val projects: List<ProjectMetadata>
        get() = if (libraryManager != null) libraryManager.projects else _projects.toList()

    var activeProject: ProjectMetadata? = null
        private set

    val thumbnailCache: ThumbnailCache = libraryManager?.thumbnailCache ?: ThumbnailCache()

    init {
        _projects.addAll(initialProjects)
        if (libraryManager != null && initialProjects.isNotEmpty()) {
            initialProjects.forEach { libraryManager.updateMetadata(it) }
        }
    }

    /**
     * Creates a new blank project and sets it as the active document.
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

        if (libraryManager != null) {
            val created = libraryManager.createNewProject(title, width, height, folder, tags)
            activeProject = created
            return created
        }

        val sanitizedTitle = if (title.isBlank()) "Untitled Artwork" else title.trim()
        val newProj = ProjectMetadata(
            title = sanitizedTitle,
            width = width.coerceIn(128, 8192),
            height = height.coerceIn(128, 8192),
            folder = folder,
            tags = tags,
            thumbnailColor = selectThumbnailColor(_projects.size)
        )
        _projects.add(0, newProj)
        activeProject = newProj
        return newProj
    }

    /**
     * Opens an existing project by ID and sets it as active.
     */
    fun openProject(id: String): ProjectMetadata? {
        if (libraryManager != null) {
            return when (val res = libraryManager.openProject(id)) {
                is ProjectOpenResult.Success -> {
                    activeProject = res.metadata
                    res.metadata
                }
                is ProjectOpenResult.RecoveryAvailable -> {
                    activeProject = res.metadata
                    res.metadata
                }
                is ProjectOpenResult.Error -> null
            }
        }

        val proj = _projects.find { it.id == id } ?: return null
        _projects.remove(proj)
        val updated = proj.copy(updatedAt = System.currentTimeMillis())
        _projects.add(0, updated)
        activeProject = updated
        return updated
    }

    /**
     * Duplicates an existing project.
     */
    fun duplicateProject(id: String, newTitle: String? = null): ProjectMetadata? {
        if (libraryManager != null) {
            return libraryManager.duplicateProject(id, newTitle).getOrNull()
        }

        val original = _projects.find { it.id == id } ?: return null
        val title = newTitle ?: "${original.title} (Copy)"
        val duplicated = original.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isDirty = false
        )
        val index = _projects.indexOf(original)
        _projects.add(index + 1, duplicated)
        return duplicated
    }

    /**
     * Renames an existing project.
     */
    fun renameProject(id: String, newTitle: String): ProjectMetadata? {
        if (newTitle.isBlank()) return null
        if (libraryManager != null) {
            return libraryManager.renameProject(id, newTitle).getOrNull()
        }

        val original = _projects.find { it.id == id } ?: return null
        val updated = original.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis())
        updateProject(updated)
        return updated
    }

    /**
     * Moves a project to a folder.
     */
    fun moveToFolder(id: String, folderName: String?): ProjectMetadata? {
        if (libraryManager != null) {
            return libraryManager.moveToFolder(id, folderName).getOrNull()
        }

        val original = _projects.find { it.id == id } ?: return null
        val updated = original.copy(folder = folderName?.trim()?.ifBlank { null }, updatedAt = System.currentTimeMillis())
        updateProject(updated)
        return updated
    }

    /**
     * Deletes a project by ID with soft-delete / undo support.
     */
    fun deleteProject(id: String, softDelete: Boolean = true): Boolean {
        if (libraryManager != null) {
            val res = libraryManager.deleteProject(id, softDelete)
            if (activeProject?.id == id) activeProject = null
            return res
        }

        val removed = _projects.removeAll { it.id == id }
        if (activeProject?.id == id) {
            activeProject = null
        }
        return removed
    }

    /**
     * Restores a deleted project from trash.
     */
    fun undoDelete(id: String): ProjectMetadata? {
        if (libraryManager != null) {
            val res = libraryManager.undoDelete(id).getOrNull()
            if (res != null) activeProject = res
            return res
        }
        return null
    }

    /**
     * Registers an existing project archive file (e.g. sample or imported .ffart).
     */
    fun registerExistingProject(file: java.io.File, folder: String? = null): ProjectMetadata? {
        if (libraryManager != null) {
            val res = libraryManager.registerExistingProject(file, folder).getOrNull()
            if (res != null) activeProject = res
            return res
        }
        val pkg = com.artstudio.document.io.DocumentPackage.load(file)
        val meta = ProjectMetadata(
            id = pkg.document.id,
            title = pkg.document.title,
            width = pkg.document.canvas.width,
            height = pkg.document.canvas.height,
            filePath = file.absolutePath,
            folder = folder
        )
        _projects.removeAll { it.id == meta.id }
        _projects.add(0, meta)
        activeProject = meta
        return meta
    }

    /**
     * Queries and sorts projects by query string, folder, and sort order.
     */
    fun queryProjects(
        filter: ProjectSearchFilter = ProjectSearchFilter(),
        sortOrder: ProjectSortOrder = ProjectSortOrder.ModifiedDescending
    ): List<ProjectMetadata> {
        return projects.applyFilterAndSort(filter, sortOrder)
    }

    /**
     * Updates project metadata (e.g., when strokes are added or dimensions change).
     */
    fun updateProject(updated: ProjectMetadata) {
        if (libraryManager != null) {
            libraryManager.updateMetadata(updated)
            if (activeProject?.id == updated.id) {
                activeProject = updated
            }
            return
        }

        val index = _projects.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            _projects[index] = updated
        }
        if (activeProject?.id == updated.id) {
            activeProject = updated
        }
    }

    /**
     * Records new stroke activity, marking the project dirty (unsaved work).
     */
    fun recordStrokes(projectId: String, additionalStrokes: Int) {
        val current = projects.find { it.id == projectId } ?: activeProject ?: return
        val updated = current.copyWithUpdatedStrokes(
            newStrokeCount = current.strokeCount + additionalStrokes,
            isDirty = true
        )
        updateProject(updated)
    }

    /**
     * Marks the active project clean (after autosave or save action).
     */
    fun markClean(projectId: String) {
        val current = projects.find { it.id == projectId } ?: return
        updateProject(current.markClean())
    }

    /**
     * Marks the project dirty (after uncommitted stroke or edit).
     */
    fun markDirty(projectId: String) {
        val current = projects.find { it.id == projectId } ?: activeProject ?: return
        updateProject(current.copy(isDirty = true))
    }

    /**
     * Creates a project imported from an external Android document URI (PNG, JPEG, ORA).
     */
    fun importFromDocumentUri(
        uri: String,
        displayName: String?,
        width: Int = 2048,
        height: Int = 2048
    ): ProjectMetadata {
        val scheme = if (uri.contains("://")) {
            uri.substringBefore("://").lowercase()
        } else {
            try { Uri.parse(uri)?.scheme?.lowercase() } catch (_: Exception) { null }
        }
        require(scheme == "content" || scheme == "file") {
            "Invalid or untrusted URI scheme '$scheme'. Only 'content' and 'file' are allowed."
        }
        val title = displayName?.substringBeforeLast('.') ?: "Imported Layer"
        val importedProj = ProjectMetadata(
            title = title,
            width = width,
            height = height,
            fileUri = uri,
            layerCount = 1,
            strokeCount = 0,
            thumbnailColor = 0xFF1976D2L,
            isDirty = false
        )
        if (libraryManager != null) {
            libraryManager.updateMetadata(importedProj)
        } else {
            _projects.add(0, importedProj)
        }
        activeProject = importedProj
        return importedProj
    }

    /**
     * Safely retains persistable URI permissions using Android document APIs
     * only when supported by the provider, without throwing SecurityExceptions.
     */
    fun safelyRetainUriPermission(contentResolver: ContentResolver?, uri: Uri?): Boolean {
        if (contentResolver == null || uri == null) return false
        val uriStr = uri.toString()
        val scheme = try { uri.scheme?.lowercase() } catch (_: Exception) { null }
            ?: if (uriStr.contains("://")) uriStr.substringBefore("://").lowercase() else null
        if (scheme != "content") return false
        return try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (se: SecurityException) {
            Log.w("ProjectRepository", "Persistable URI permission not granted for $uri: ${se.message}")
            false
        } catch (e: Exception) {
            Log.w("ProjectRepository", "Failed to retain URI permission: ${e.message}")
            false
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
