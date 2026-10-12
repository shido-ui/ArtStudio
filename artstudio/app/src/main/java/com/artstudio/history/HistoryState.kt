package com.artstudio.history

import com.artstudio.document.model.ArtDocument

/**
 * Bounded checkpoint snapshot taken at periodic command intervals
 * to accelerate state reconstruction without storing full bitmaps.
 */
data class CheckpointSnapshot(
    val commandIndex: Int,
    val commandId: String,
    val document: ArtDocument,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Immutable view of the current undo/redo history state.
 */
data class HistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoCount: Int = 0,
    val redoCount: Int = 0,
    val isDirty: Boolean = false,
    val lastUndoDescription: String? = null,
    val nextRedoDescription: String? = null,
    val totalCommandCount: Int = 0,
    val estimatedMemoryBytes: Long = 0L
)
