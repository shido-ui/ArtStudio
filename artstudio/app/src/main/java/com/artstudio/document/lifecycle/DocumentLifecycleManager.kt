package com.artstudio.document.lifecycle

import com.artstudio.document.io.*
import com.artstudio.document.model.ArtDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * Authoritative document lifecycle manager governing native save, Save As, open,
 * dirty-state tracking, bounded autosave, temporary-write recovery, and fault resilience.
 *
 * Guarantees:
 * - All disk I/O runs off the UI thread via [dispatcher].
 * - Atomic shadow writes prevent corrupting the last valid document.
 * - Recovery material is never deleted silently.
 * - Success is never reported before verifying the write on disk.
 */
class DocumentLifecycleManager(
    val recoveryRootDir: File,
    val storageDriver: StorageDriver = DefaultStorageDriver(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    autosavePolicy: AutosavePolicy = AutosavePolicy()
) {
    val recoveryManager = DocumentRecoveryManager(recoveryRootDir, storageDriver)

    private val _state = MutableStateFlow<DocumentSaveState>(DocumentSaveState.Idle)
    val state: StateFlow<DocumentSaveState> = _state.asStateFlow()

    var currentDocument: ArtDocument? = null
        private set
    val currentAssets = mutableMapOf<String, ByteArray>()
    var currentFile: File? = null
        private set

    var cleanRevision: Long = 0L
        private set
    var currentRevision: Long = 0L
        private set

    val isDirty: Boolean
        get() = currentRevision != cleanRevision

    private var activeSaveJob: Job? = null

    val autosaveManager: AutosaveManager = AutosaveManager(
        policy = autosavePolicy,
        dispatcher = dispatcher,
        performAutosave = { _, revision ->
            val doc = currentDocument ?: return@AutosaveManager
            val file = currentFile ?: File(recoveryRootDir, "${doc.id}.ffart")
            _state.value = DocumentSaveState.Saving(progress = 0.5f, isAutosave = true)
            try {
                recoveryManager.writeRecoverySnapshot(
                    document = doc,
                    assets = currentAssets.toMap(),
                    originalFile = file,
                    revision = revision
                )
                _state.value = DocumentSaveState.Unsaved(dirtyEdits = (currentRevision - cleanRevision).toInt())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Background autosave failure preserves state without crashing
                _state.value = DocumentSaveState.Unsaved(dirtyEdits = (currentRevision - cleanRevision).toInt())
            }
        }
    )

    /**
     * Initializes or loads a new blank document.
     */
    fun initNewDocument(document: ArtDocument, targetFile: File? = null) {
        currentDocument = document
        currentAssets.clear()
        currentFile = targetFile
        cleanRevision = 0L
        currentRevision = 0L
        _state.value = DocumentSaveState.Idle
    }

    /**
     * Opens an existing project file (.ffart or legacy v1 .json) from disk.
     * Checks for crash recovery material before opening.
     */
    suspend fun open(file: File): Result<ArtDocument> = withContext(dispatcher) {
        if (!file.exists()) {
            val err = IOException("File does not exist: ${file.absolutePath}")
            _state.value = DocumentSaveState.SaveFailed(err.message ?: "File not found", err)
            return@withContext Result.failure(err)
        }

        try {
            // 1. Load package or legacy JSON
            val isJson = file.name.endsWith(".json", ignoreCase = true)
            val doc: ArtDocument
            val assets: Map<String, ByteArray>

            if (isJson) {
                val jsonString = file.readText(Charsets.UTF_8)
                doc = DocumentSerializer.fromJsonString(jsonString)
                assets = emptyMap()
            } else {
                val packageData = DocumentPackage.load(file)
                doc = packageData.document
                assets = packageData.assets
            }

            currentDocument = doc
            currentAssets.clear()
            currentAssets.putAll(assets)
            currentFile = file
            cleanRevision = 0L
            currentRevision = 0L

            // 2. Check if uncommitted recovery material exists for doc.id or file name
            val recoveryState = recoveryManager.checkRecovery(doc.id, file)
                ?: recoveryManager.checkRecovery(file.nameWithoutExtension, file)

            if (recoveryState != null) {
                _state.value = recoveryState
            } else if (isJson && doc.schemaVersion == ArtDocument.CURRENT_SCHEMA_VERSION) {
                // Migrated from legacy v1 -> marked dirty so user can save as v2 .ffart
                currentRevision = 1L
                _state.value = DocumentSaveState.Unsaved(dirtyEdits = 1)
            } else {
                _state.value = DocumentSaveState.Saved(System.currentTimeMillis(), file, 0L)
            }

            Result.success(doc)
        } catch (e: Exception) {
            _state.value = DocumentSaveState.SaveFailed("Failed to open document: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Saves the active document to [currentFile].
     * Uses atomic shadow writes via [storageDriver] and verifies the write before reporting success.
     */
    suspend fun save(): Result<File> = withContext(dispatcher) {
        val doc = currentDocument ?: return@withContext Result.failure(IllegalStateException("No active document"))
        val file = currentFile ?: return@withContext Result.failure(IllegalStateException("No target file specified for save"))

        saveInternal(doc, file, isSaveAs = false)
    }

    /**
     * Saves the active document to a new file location with an optional new title.
     */
    suspend fun saveAs(newFile: File, newTitle: String? = null): Result<File> = withContext(dispatcher) {
        val doc = currentDocument ?: return@withContext Result.failure(IllegalStateException("No active document"))
        val targetDoc = if (newTitle != null && newTitle.isNotBlank()) {
            doc.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis())
        } else {
            doc
        }

        saveInternal(targetDoc, newFile, isSaveAs = true)
    }

    private suspend fun saveInternal(
        document: ArtDocument,
        targetFile: File,
        isSaveAs: Boolean
    ): Result<File> = withContext(dispatcher) {
        _state.value = DocumentSaveState.Saving(progress = 0.1f, isAutosave = false)

        // 1. Check storage space (minimum 1 MB required for project write)
        val parentDir = targetFile.parentFile ?: File(".")
        val availableBytes = storageDriver.getFreeSpaceBytes(parentDir)
        if (availableBytes > 0 && availableBytes < 1024 * 1024L) {
            val err = IOException("Insufficient storage: only $availableBytes bytes free, minimum 1MB required")
            _state.value = DocumentSaveState.SaveFailed(err.message ?: "Storage full", err)
            return@withContext Result.failure(err)
        }

        try {
            val revisionToCommit = currentRevision

            // 2. Perform atomic write with verification
            storageDriver.writeAtomic(targetFile) { tempFile ->
                DocumentPackage.save(
                    document = document,
                    assets = currentAssets.toMap(),
                    outputFile = tempFile
                )
            }

            // 3. Update clean revision and commit recovery journal
            cleanRevision = revisionToCommit
            currentDocument = document
            currentFile = targetFile
            autosaveManager.markSaved(revisionToCommit)
            recoveryManager.markCommitted(document.id, revisionToCommit)

            val timestamp = System.currentTimeMillis()
            _state.value = DocumentSaveState.Saved(timestamp, targetFile, revisionToCommit)
            Result.success(targetFile)
        } catch (e: CancellationException) {
            _state.value = DocumentSaveState.SaveFailed("Save cancelled", e)
            throw e
        } catch (e: Exception) {
            // Target file remains untouched!
            _state.value = DocumentSaveState.SaveFailed("Save failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Notifies the manager of user edits (e.g., drawn strokes or layer transforms).
     */
    fun notifyEdit(strokeCount: Int = 1, dirtyAssetKey: String? = null) {
        currentRevision += strokeCount
        _state.value = DocumentSaveState.Unsaved(dirtyEdits = (currentRevision - cleanRevision).toInt())
        autosaveManager.notifyEdit(dirtyAssetKey)
    }

    /**
     * Restores artwork from an available recovery snapshot.
     */
    suspend fun restoreFromRecovery(): Result<ArtDocument> = withContext(dispatcher) {
        val doc = currentDocument ?: return@withContext Result.failure(IllegalStateException("No active document"))
        try {
            val packageData = recoveryManager.restoreRecovery(doc.id)
            currentDocument = packageData.document
            currentAssets.clear()
            currentAssets.putAll(packageData.assets)
            currentRevision++
            _state.value = DocumentSaveState.Unsaved(dirtyEdits = (currentRevision - cleanRevision).toInt())
            Result.success(packageData.document)
        } catch (e: Exception) {
            _state.value = DocumentSaveState.SaveFailed("Failed to restore recovery: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Explicitly discards recovery material.
     */
    fun discardRecovery(): Boolean {
        val doc = currentDocument ?: return false
        val result = recoveryManager.discardRecovery(doc.id)
        if (result) {
            if (isDirty) {
                _state.value = DocumentSaveState.Unsaved((currentRevision - cleanRevision).toInt())
            } else if (currentFile != null) {
                _state.value = DocumentSaveState.Saved(System.currentTimeMillis(), currentFile!!, cleanRevision)
            } else {
                _state.value = DocumentSaveState.Idle
            }
        }
        return result
    }

    /**
     * Cancels active background save operation.
     */
    fun cancelSave() {
        activeSaveJob?.cancel()
        _state.value = DocumentSaveState.SaveFailed("Save cancelled by user")
    }

    /**
     * Flushes pending autosaves and shuts down workers.
     */
    suspend fun close() {
        autosaveManager.flush()
        autosaveManager.cancel()
    }
}
