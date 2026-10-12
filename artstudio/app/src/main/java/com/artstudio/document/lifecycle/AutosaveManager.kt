package com.artstudio.document.lifecycle

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Configuration parameters for bounded autosaving.
 * Prevents full-document disk serialization on high-frequency pointer samples.
 */
data class AutosavePolicy(
    val debounceDelayMs: Long = 2000L,
    val maxEditsThreshold: Int = 10,
    val minIntervalMs: Long = 3000L
)

/**
 * Coordinates debounced and threshold-bounded background autosave operations.
 * Guarantees zero blocking of the Android UI/rendering thread.
 */
class AutosaveManager(
    private val policy: AutosavePolicy = AutosavePolicy(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val performAutosave: suspend (dirtyAssets: Set<String>, revision: Long) -> Unit
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val mutex = Mutex()

    private var debounceJob: Job? = null
    private var lastAutosaveTimestamp: Long = 0L

    var currentRevision: Long = 0L
        private set
    var lastAutosavedRevision: Long = 0L
        private set

    private val _dirtyAssets = mutableSetOf<String>()
    val dirtyAssets: Set<String>
        get() = synchronized(_dirtyAssets) { _dirtyAssets.toSet() }

    var onError: ((Throwable) -> Unit)? = null

    /**
     * Notifies the engine of user edit activity (e.g., completed stroke or layer transform).
     * High-frequency pointer move samples must NOT call this; only completed stroke events.
     */
    fun notifyEdit(dirtyAssetKey: String? = null) {
        synchronized(_dirtyAssets) {
            currentRevision++
            if (dirtyAssetKey != null) {
                _dirtyAssets.add(dirtyAssetKey)
            }
        }

        val uncommittedEdits = currentRevision - lastAutosavedRevision

        // If threshold exceeded, trigger immediate bounded autosave without waiting for debounce
        if (uncommittedEdits >= policy.maxEditsThreshold) {
            debounceJob?.cancel()
            scheduleAutosave(delayMs = 0L)
        } else {
            // Otherwise reset debounce timer
            debounceJob?.cancel()
            scheduleAutosave(delayMs = policy.debounceDelayMs)
        }
    }

    private fun scheduleAutosave(delayMs: Long) {
        debounceJob = scope.launch {
            if (delayMs > 0) {
                delay(delayMs)
            }

            // Enforce minimum interval between consecutive disk writes
            val elapsed = System.currentTimeMillis() - lastAutosaveTimestamp
            if (elapsed < policy.minIntervalMs) {
                delay(policy.minIntervalMs - elapsed)
            }

            executeAutosave()
        }
    }

    private suspend fun executeAutosave() {
        mutex.withLock {
            val revisionToSave: Long
            val assetsToSave: Set<String>

            synchronized(_dirtyAssets) {
                if (currentRevision == lastAutosavedRevision) {
                    return@withLock // Nothing new to autosave
                }
                revisionToSave = currentRevision
                assetsToSave = _dirtyAssets.toSet()
            }

            try {
                performAutosave(assetsToSave, revisionToSave)
                lastAutosavedRevision = revisionToSave
                lastAutosaveTimestamp = System.currentTimeMillis()
                synchronized(_dirtyAssets) {
                    _dirtyAssets.removeAll(assetsToSave)
                }
            } catch (e: CancellationException) {
                // Cancelled cleanly; preserve dirty assets
                throw e
            } catch (e: Exception) {
                // Background autosave failure: log and notify error callback without swallowing
                android.util.Log.w("AutosaveManager", "Background autosave failed: ${e.message}", e)
                onError?.invoke(e)
            }
        }
    }

    /**
     * Forces immediate execution of any pending autosave.
     */
    suspend fun flush() {
        debounceJob?.cancel()
        executeAutosave()
    }

    /**
     * Marks the state clean after a full manual save.
     */
    fun markSaved(revision: Long) {
        synchronized(_dirtyAssets) {
            lastAutosavedRevision = revision
            _dirtyAssets.clear()
        }
        debounceJob?.cancel()
    }

    /**
     * Cancels all pending autosave jobs.
     */
    fun cancel() {
        debounceJob?.cancel()
        scope.coroutineContext.cancelChildren()
    }
}
