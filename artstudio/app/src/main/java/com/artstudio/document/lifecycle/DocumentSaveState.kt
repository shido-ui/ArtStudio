package com.artstudio.document.lifecycle

import java.io.File

/**
 * Explicit observable states for document saving, autosave, and crash recovery.
 */
sealed class DocumentSaveState {

    /**
     * Document is clean and idle; no unsaved edits pending.
     */
    object Idle : DocumentSaveState()

    /**
     * Document has uncommitted edits.
     * @param dirtyEdits Number of uncommitted edit revisions.
     */
    data class Unsaved(val dirtyEdits: Int) : DocumentSaveState()

    /**
     * Document save or autosave is currently writing to disk on a background thread.
     * @param progress Progress between 0.0 and 1.0.
     * @param isAutosave True if triggered by the background bounded autosave engine.
     */
    data class Saving(val progress: Float = 0f, val isAutosave: Boolean = false) : DocumentSaveState()

    /**
     * Document write has been verified and committed to disk.
     * @param timestamp Epoch timestamp of successful write verification.
     * @param file Target file committed to disk.
     * @param revision Document revision committed.
     */
    data class Saved(val timestamp: Long, val file: File, val revision: Long) : DocumentSaveState()

    /**
     * Save failed due to an I/O error, insufficient storage, permission denial, or cancellation.
     * The last valid document on disk remains intact.
     * @param reason Human-readable explanation.
     * @param cause Underlying exception if available.
     * @param canRetry True if the user can retry saving.
     */
    data class SaveFailed(
        val reason: String,
        val cause: Throwable? = null,
        val canRetry: Boolean = true
    ) : DocumentSaveState()

    /**
     * Uncommitted recovery material was discovered from a previous session or process termination.
     * @param timestamp Timestamp of the recovery snapshot.
     * @param recoveryFile The shadow .ffart file containing the unsaved work.
     * @param backupRevision The revision stored in the recovery journal.
     */
    data class RecoveryAvailable(
        val timestamp: Long,
        val recoveryFile: File,
        val backupRevision: Long
    ) : DocumentSaveState()
}
