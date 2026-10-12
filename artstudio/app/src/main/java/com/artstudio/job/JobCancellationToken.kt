package com.artstudio.job

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe cooperative cancellation token for Studio background jobs.
 */
class JobCancellationToken {

    @Volatile
    var isCancelled: Boolean = false
        private set

    @Volatile
    var cancelReason: String? = null
        private set

    private val cancelCallbacks = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Requests cooperative cancellation of the associated job.
     */
    fun cancel(reason: String = "User requested cancellation") {
        if (!isCancelled) {
            isCancelled = true
            cancelReason = reason
            for (callback in cancelCallbacks) {
                try {
                    callback()
                } catch (_: Throwable) {
                    // Suppress callback exceptions to ensure all callbacks execute
                }
            }
        }
    }

    /**
     * Throws [JobCancelledException] if cancellation was requested.
     */
    fun throwIfCancelled() {
        if (isCancelled) {
            throw JobCancelledException(cancelReason ?: "Job was cancelled")
        }
    }

    /**
     * Registers a callback invoked immediately when cancelled (or synchronously if already cancelled).
     */
    fun onCancel(callback: () -> Unit) {
        if (isCancelled) {
            callback()
        } else {
            cancelCallbacks.add(callback)
        }
    }
}

/**
 * Cooperative cancellation exception thrown when a job observes cancellation.
 */
class JobCancelledException(message: String) : Exception(message)
