package com.artstudio.history

import com.artstudio.document.model.ArtDocument

/**
 * Authoritative, bounded-memory undo/redo history manager.
 * Supports reversible edit commands, branch truncation, decoupled in-flight cancellation,
 * save checkpoints, and strict memory ceiling enforcement.
 */
class HistoryManager(
    initialDocument: ArtDocument,
    val maxHistoryCount: Int = 100,
    val maxMemoryCeilingBytes: Long = com.artstudio.core.DeviceMemoryPolicy.maxHistoryBytes,
    val checkpointInterval: Int = 20,
    val maxCheckpoints: Int = 5
) {
    var currentDocument: ArtDocument = initialDocument
        private set

    private val undoStack = mutableListOf<EditCommand>()
    private val redoStack = mutableListOf<EditCommand>()
    private val checkpoints = mutableListOf<CheckpointSnapshot>()

    // Save point tracking
    private var lastSavedCommandId: String? = null
    private var initialDocumentSaved: Boolean = true

    // In-flight transient state for cancellation decoupling
    private var inFlightOperation: String? = null

    // State change callback
    var onStateChanged: ((HistoryState) -> Unit)? = null

    init {
        // Initial checkpoint
        checkpoints.add(CheckpointSnapshot(0, "initial", initialDocument))
    }

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    val totalCommandCount: Int
        get() = undoStack.size + redoStack.size

    val isDirty: Boolean
        get() {
            return if (undoStack.isEmpty()) {
                !initialDocumentSaved
            } else {
                undoStack.last().id != lastSavedCommandId
            }
        }

    val lastUndoDescription: String?
        get() = undoStack.lastOrNull()?.description

    val nextRedoDescription: String?
        get() = redoStack.lastOrNull()?.description

    val hasInFlightOperation: Boolean
        get() = inFlightOperation != null

    /**
     * Executes a new command, applies it to the document, truncates any redo branch,
     * and enforces memory and history count ceilings.
     */
    fun execute(command: EditCommand): ArtDocument {
        // Branch truncation: any new action invalidates existing redo history
        if (redoStack.isNotEmpty()) {
            redoStack.clear()
        }

        // Apply command to current document
        currentDocument = command.apply(currentDocument)
        undoStack.add(command)

        // Checkpoint snapshot triggering
        if (undoStack.size % checkpointInterval == 0) {
            checkpoints.add(
                CheckpointSnapshot(
                    commandIndex = undoStack.size,
                    commandId = command.id,
                    document = currentDocument
                )
            )
            while (checkpoints.size > maxCheckpoints) {
                checkpoints.removeAt(0)
            }
        }

        // Enforce max history count (FIFO eviction of oldest undo command)
        while (undoStack.size > maxHistoryCount) {
            undoStack.removeAt(0)
        }

        // Enforce hard memory ceiling
        while (getEstimatedMemoryBytes() > maxMemoryCeilingBytes && undoStack.size > 1) {
            undoStack.removeAt(0)
        }

        notifyStateChanged()
        return currentDocument
    }

    /**
     * Reverts the most recent command on the undo stack and moves it to the redo stack.
     * Returns the reverted command, or null if nothing to undo.
     */
    fun undo(): EditCommand? {
        if (undoStack.isEmpty()) return null

        val command = undoStack.removeLast()
        currentDocument = command.revert(currentDocument)
        redoStack.add(command)

        notifyStateChanged()
        return command
    }

    /**
     * Reapplies the most recent undone command on the redo stack and moves it back to undo stack.
     * Returns the reapplied command, or null if nothing to redo.
     */
    fun redo(): EditCommand? {
        if (redoStack.isEmpty()) return null

        val command = redoStack.removeLast()
        currentDocument = command.apply(currentDocument)
        undoStack.add(command)

        notifyStateChanged()
        return command
    }

    /**
     * Registers an in-flight operation (e.g. pointer stroke currently being drawn).
     */
    fun beginInFlight(operationDescription: String) {
        inFlightOperation = operationDescription
    }

    /**
     * Distinct Cancellation: Cancels an active in-flight operation without modifying the undo stack.
     * Guaranteed to be decoupled from committed undo/redo state.
     */
    fun cancelInFlight(): Boolean {
        if (inFlightOperation != null) {
            inFlightOperation = null
            notifyStateChanged()
            return true
        }
        return false
    }

    /**
     * Clears in-flight status upon successful commit.
     */
    fun completeInFlight() {
        inFlightOperation = null
    }

    /**
     * Marks the current history position as clean/saved.
     */
    fun markSaved() {
        if (undoStack.isEmpty()) {
            initialDocumentSaved = true
            lastSavedCommandId = null
        } else {
            lastSavedCommandId = undoStack.last().id
            initialDocumentSaved = false
        }
        notifyStateChanged()
    }

    /**
     * Reinitializes history manager when a saved document is reopened.
     */
    fun reinitializeForReopen(reopenedDocument: ArtDocument, preserveHistory: Boolean = false) {
        currentDocument = reopenedDocument
        if (!preserveHistory) {
            undoStack.clear()
            redoStack.clear()
            checkpoints.clear()
            lastSavedCommandId = null
            initialDocumentSaved = true
            checkpoints.add(CheckpointSnapshot(0, "initial", reopenedDocument))
        } else {
            markSaved()
        }
        notifyStateChanged()
    }

    /**
     * Returns estimated heap memory in bytes consumed by all commands and snapshots.
     */
    fun getEstimatedMemoryBytes(): Long {
        val undoMem = undoStack.sumOf { it.estimatedMemoryBytes }
        val redoMem = redoStack.sumOf { it.estimatedMemoryBytes }
        val checkpointMem = checkpoints.size * 2048L // Lightweight structural metadata
        return undoMem + redoMem + checkpointMem + 1024L // Stack object overhead
    }

    /**
     * Returns immutable current [HistoryState] snapshot.
     */
    fun getState(): HistoryState {
        return HistoryState(
            canUndo = canUndo,
            canRedo = canRedo,
            undoCount = undoCount,
            redoCount = redoCount,
            isDirty = isDirty,
            lastUndoDescription = lastUndoDescription,
            nextRedoDescription = nextRedoDescription,
            totalCommandCount = totalCommandCount,
            estimatedMemoryBytes = getEstimatedMemoryBytes()
        )
    }

    fun getUndoCommands(): List<EditCommand> = undoStack.toList()

    fun getRedoCommands(): List<EditCommand> = redoStack.toList()

    fun getCheckpoints(): List<CheckpointSnapshot> = checkpoints.toList()

    /**
     * Responds to system onTrimMemory callbacks by trimming undo/redo history to target budget.
     */
    fun onTrimMemory(level: com.artstudio.core.MemoryTrimLevel) {
        when (level) {
            com.artstudio.core.MemoryTrimLevel.MODERATE -> {
                trimToMemoryBytes(maxMemoryCeilingBytes / 2L)
            }
            com.artstudio.core.MemoryTrimLevel.CRITICAL -> {
                trimToMemoryBytes(maxMemoryCeilingBytes / 4L)
            }
        }
        notifyStateChanged()
    }

    /**
     * Trims undo and redo stacks so total estimated history memory stays within [targetBytes].
     */
    fun trimToMemoryBytes(targetBytes: Long) {
        while (getEstimatedMemoryBytes() > targetBytes && undoStack.size > 1) {
            undoStack.removeAt(0)
        }
        while (getEstimatedMemoryBytes() > targetBytes && redoStack.isNotEmpty()) {
            redoStack.removeAt(0)
        }
        while (checkpoints.size > 1 && getEstimatedMemoryBytes() > targetBytes) {
            checkpoints.removeAt(0)
        }
    }

    private fun notifyStateChanged() {
        onStateChanged?.invoke(getState())
    }
}
