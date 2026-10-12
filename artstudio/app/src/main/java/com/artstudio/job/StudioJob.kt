package com.artstudio.job

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Execution context provided to a job during runtime.
 */
data class JobExecutionContext(
    val updateProgress: (progress: Float, step: String, stepIndex: Int, totalSteps: Int) -> Unit,
    val cancellationToken: JobCancellationToken,
    val registerTempFile: (File) -> Unit,
    val unregisterTempFile: (File) -> Unit
)

/**
 * Authoritative, cancellable, memory-aware asynchronous background job contract.
 */
abstract class StudioJob(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lane: WorkLane,
    val estimatedMemoryBytes: Long = 16 * 1024 * 1024L, // 16 MB default estimate
    val retryPolicy: RetryPolicy = RetryPolicy.NO_RETRY
) {
    val cancellationToken: JobCancellationToken = JobCancellationToken()

    protected val _state = MutableStateFlow(JobState.QUEUED)
    val state: StateFlow<JobState> = _state.asStateFlow()

    protected val _progress = MutableStateFlow(JobProgress())
    val progress: StateFlow<JobProgress> = _progress.asStateFlow()

    protected val _error = MutableStateFlow<JobError?>(null)
    val error: StateFlow<JobError?> = _error.asStateFlow()

    var retryCount: Int = 0
        internal set

    @Volatile
    var isPaused: Boolean = false
        protected set

    private val tempFiles = CopyOnWriteArrayList<File>()

    /**
     * Registers a temporary shadow or intermediate file that should be deleted on cancellation or failure.
     */
    fun registerTempFile(file: File) {
        tempFiles.add(file)
    }

    /**
     * Unregisters a temporary file once it has been promoted to a permanent output artifact.
     */
    fun unregisterTempFile(file: File) {
        tempFiles.remove(file)
    }

    /**
     * Cleans up all registered temporary files safely without throwing exceptions.
     */
    fun cleanTempFilesSafely() {
        for (file in tempFiles) {
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Throwable) {
                // Ignore failure during best-effort temp file cleanup
            }
        }
        tempFiles.clear()
    }

    /**
     * Requests cooperative cancellation of this job.
     */
    open fun cancel(reason: String = "User requested cancellation") {
        cancellationToken.cancel(reason)
        if (_state.value == JobState.QUEUED || _state.value == JobState.PAUSED) {
            _state.value = JobState.CANCELLED
            cleanTempFilesSafely()
        }
    }

    /**
     * Pauses the job if supported by its retry/resume policy.
     * Returns true if successfully transitioned to PAUSED.
     */
    open fun pause(): Boolean {
        if (!retryPolicy.isResumable) return false
        if (_state.value == JobState.RUNNING || _state.value == JobState.QUEUED) {
            isPaused = true
            _state.value = JobState.PAUSED
            return true
        }
        return false
    }

    /**
     * Resumes the job from PAUSED state.
     * Returns true if successfully transitioned back to QUEUED / ready to run.
     */
    open fun resume(): Boolean {
        if (!retryPolicy.isResumable) return false
        if (_state.value == JobState.PAUSED) {
            isPaused = false
            _state.value = JobState.QUEUED
            return true
        }
        return false
    }

    /**
     * Updates internal progress and emits to observers.
     */
    fun setProgress(progress: Float, step: String, stepIndex: Int = 0, totalSteps: Int = 1) {
        val clampedProgress = progress.coerceIn(0.0f, 1.0f)
        _progress.value = JobProgress(
            progress = clampedProgress,
            currentStep = step,
            stepIndex = stepIndex,
            totalSteps = totalSteps
        )
    }

    /**
     * Internal transition mutators used by the scheduler.
     */
    internal fun transitionTo(newState: JobState) {
        _state.value = newState
    }

    internal fun setError(err: JobError) {
        _error.value = err
    }

    /**
     * Serializes checkpoint state to string for process death recovery if resumable.
     */
    open fun serializeCheckpoint(): String? = null

    /**
     * Restores checkpoint state from string after cold restart or process death.
     */
    open fun restoreCheckpoint(data: String) {}

    /**
     * Executes the job payload on the designated work lane.
     */
    abstract suspend fun execute(context: JobExecutionContext): JobResult

    /**
     * Takes an immutable snapshot of this job's current status.
     */
    fun snapshot(): JobSnapshot {
        return JobSnapshot(
            id = id,
            name = name,
            lane = lane,
            state = _state.value,
            progress = _progress.value,
            error = _error.value,
            retryCount = retryCount,
            isResumable = retryPolicy.isResumable
        )
    }
}
