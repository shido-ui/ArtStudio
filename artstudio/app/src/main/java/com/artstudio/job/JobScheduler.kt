package com.artstudio.job

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance, concurrency-bounded, lane-segregated, memory-aware background Job Scheduler.
 *
 * Guarantees:
 * 1. UI Thread Independence: Execution strictly runs off the UI thread on configurable dispatchers.
 * 2. Lane Segregation: Canvas input is isolated and never starved by heavy export or analysis tasks.
 * 3. Strict Concurrency Bounds: Workers per lane never exceed configured concurrency limits.
 * 4. Memory-Aware Admission: Heavyweight jobs are throttled when available memory is below budget.
 * 5. Safe Resource Cleanup: Intermediate temporary files are cleaned up on cancellation or failure.
 * 6. Zero Fake Success: Jobs only enter SUCCEEDED state after complete and verified execution.
 */
class JobScheduler(
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    concurrencyOverrides: Map<WorkLane, Int> = emptyMap(),
    val maxMemoryCeilingBytes: Long = 64 * 1024 * 1024L, // 64 MB heap budget for concurrent background jobs
    val memoryProvider: () -> Long = {
        val rt = Runtime.getRuntime()
        rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
    },
    val journalFile: File? = null,
    val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
) {

    // Concurrency limits and semaphores per work lane
    val concurrencyLimits: Map<WorkLane, Int> = WorkLane.entries.associateWith { lane ->
        concurrencyOverrides[lane] ?: lane.defaultMaxConcurrency
    }

    private val laneSemaphores = ConcurrentHashMap<WorkLane, Semaphore>().apply {
        for (lane in WorkLane.entries) {
            put(lane, Semaphore(concurrencyLimits[lane] ?: lane.defaultMaxConcurrency))
        }
    }

    // Active worker counters for runtime concurrency auditing
    val activeWorkers = ConcurrentHashMap<WorkLane, AtomicInteger>().apply {
        for (lane in WorkLane.entries) {
            put(lane, AtomicInteger(0))
        }
    }

    val peakActiveWorkers = ConcurrentHashMap<WorkLane, AtomicInteger>().apply {
        for (lane in WorkLane.entries) {
            put(lane, AtomicInteger(0))
        }
    }

    // Active memory consumption tracking
    val allocatedMemoryBytes = AtomicLong(0L)

    // Job registry
    private val jobs = ConcurrentHashMap<String, StudioJob>()
    private val _jobSnapshots = MutableStateFlow<List<JobSnapshot>>(emptyList())
    val jobSnapshots: StateFlow<List<JobSnapshot>> = _jobSnapshots.asStateFlow()

    /**
     * Enqueues a job for execution in its designated work lane.
     */
    fun enqueue(job: StudioJob): JobSnapshot {
        jobs[job.id] = job
        updateSnapshots()
        persistJournal()

        scope.launch {
            scheduleJob(job)
        }

        return job.snapshot()
    }

    /**
     * Cancels a job by ID.
     */
    fun cancelJob(jobId: String, reason: String = "User requested cancellation") {
        val job = jobs[jobId] ?: return
        job.cancel(reason)
        updateSnapshots()
        persistJournal()
    }

    /**
     * Cancels all active jobs, optionally filtered by lane.
     */
    fun cancelAll(lane: WorkLane? = null, reason: String = "Mass cancellation requested") {
        for (job in jobs.values) {
            if (lane == null || job.lane == lane) {
                job.cancel(reason)
            }
        }
        updateSnapshots()
        persistJournal()
    }

    /**
     * Pauses a job if supported by its retry/resume policy.
     */
    fun pauseJob(jobId: String): Boolean {
        val job = jobs[jobId] ?: return false
        val paused = job.pause()
        if (paused) {
            updateSnapshots()
            persistJournal()
        }
        return paused
    }

    /**
     * Resumes a paused job.
     */
    fun resumeJob(jobId: String): Boolean {
        val job = jobs[jobId] ?: return false
        val resumed = job.resume()
        if (resumed) {
            updateSnapshots()
            persistJournal()
            scope.launch {
                scheduleJob(job)
            }
        }
        return resumed
    }

    /**
     * Pauses all resumable running jobs (e.g. when app moves to background).
     */
    fun pauseAll(): Int {
        var count = 0
        for (job in jobs.values) {
            if (job.state.value == JobState.RUNNING && job.pause()) {
                count++
            }
        }
        updateSnapshots()
        persistJournal()
        return count
    }

    /**
     * Resumes all paused jobs (e.g. when app returns to foreground).
     */
    fun resumeAll(): Int {
        var count = 0
        for (job in jobs.values) {
            if (job.state.value == JobState.PAUSED && resumeJob(job.id)) {
                count++
            }
        }
        return count
    }

    /**
     * Retrieves a job by ID.
     */
    fun getJob(jobId: String): StudioJob? = jobs[jobId]

    /**
     * Retrieves the snapshot of a job by ID.
     */
    fun getSnapshot(jobId: String): JobSnapshot? = jobs[jobId]?.snapshot()

    private suspend fun scheduleJob(job: StudioJob) {
        val lane = job.lane
        val semaphore = laneSemaphores[lane] ?: return

        // 1. Check initial cancellation
        if (job.cancellationToken.isCancelled) {
            job.transitionTo(JobState.CANCELLED)
            job.cleanTempFilesSafely()
            updateSnapshots()
            return
        }

        // 2. Memory-aware scheduling admission check (exempt CANVAS_INPUT)
        if (lane != WorkLane.CANVAS_INPUT) {
            while (allocatedMemoryBytes.get() + job.estimatedMemoryBytes > maxMemoryCeilingBytes) {
                if (job.cancellationToken.isCancelled) {
                    job.transitionTo(JobState.CANCELLED)
                    job.cleanTempFilesSafely()
                    updateSnapshots()
                    return
                }
                delay(20L) // Yield to allow executing jobs to complete and free memory
            }
        }

        // 3. Acquire lane semaphore (bounds concurrency strictly to lane limit)
        semaphore.withPermit {
            val workerCount = activeWorkers[lane]!!.incrementAndGet()
            peakActiveWorkers[lane]!!.updateAndGet { currentPeak -> maxOf(currentPeak, workerCount) }
            allocatedMemoryBytes.addAndGet(job.estimatedMemoryBytes)

            try {
                executeWithRetryLoop(job)
            } finally {
                activeWorkers[lane]!!.decrementAndGet()
                allocatedMemoryBytes.addAndGet(-job.estimatedMemoryBytes)
                updateSnapshots()
                if (scope.isActive) {
                    persistJournal()
                }
            }
        }
    }

    private suspend fun executeWithRetryLoop(job: StudioJob) {
        var shouldRetry = true

        while (shouldRetry) {
            if (job.cancellationToken.isCancelled) {
                job.transitionTo(JobState.CANCELLED)
                job.cleanTempFilesSafely()
                return
            }

            if (job.isPaused) {
                job.transitionTo(JobState.PAUSED)
                return
            }

            job.transitionTo(JobState.RUNNING)
            updateSnapshots()

            val context = JobExecutionContext(
                updateProgress = { progress, step, stepIndex, totalSteps ->
                    job.setProgress(progress, step, stepIndex, totalSteps)
                    updateSnapshots()
                },
                cancellationToken = job.cancellationToken,
                registerTempFile = { file -> job.registerTempFile(file) },
                unregisterTempFile = { file -> job.unregisterTempFile(file) }
            )

            try {
                val result = job.execute(context)
                when (result) {
                    is JobResult.Success -> {
                        job.setProgress(1.0f, result.message)
                        job.transitionTo(JobState.SUCCEEDED)
                        shouldRetry = false
                    }
                    is JobResult.Cancelled -> {
                        job.transitionTo(JobState.CANCELLED)
                        job.cleanTempFilesSafely()
                        shouldRetry = false
                    }
                    is JobResult.Paused -> {
                        job.transitionTo(JobState.PAUSED)
                        shouldRetry = false
                    }
                    is JobResult.Failure -> {
                        if (result.error.isRecoverable && job.retryCount < job.retryPolicy.maxRetries) {
                            job.retryCount++
                            val backoff = (job.retryPolicy.initialBackoffMs * Math.pow(job.retryPolicy.backoffMultiplier, (job.retryCount - 1).toDouble())).toLong()
                            job.setProgress(job.progress.value.progress, "Retrying (attempt ${job.retryCount}/${job.retryPolicy.maxRetries})...")
                            delay(backoff)
                            shouldRetry = true
                        } else {
                            job.setError(result.error)
                            job.transitionTo(JobState.FAILED)
                            job.cleanTempFilesSafely()
                            shouldRetry = false
                        }
                    }
                }
            } catch (ce: JobCancelledException) {
                job.transitionTo(JobState.CANCELLED)
                job.cleanTempFilesSafely()
                shouldRetry = false
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val err = JobError(code = "UNHANDLED_EXCEPTION", message = t.message ?: "Unknown error", cause = t, isRecoverable = false)
                job.setError(err)
                job.transitionTo(JobState.FAILED)
                job.cleanTempFilesSafely()
                shouldRetry = false
            }
        }
    }

    private fun updateSnapshots() {
        _jobSnapshots.value = jobs.values.map { it.snapshot() }
    }

    /**
     * Persists active jobs to disk journal for crash and process death recovery.
     */
    fun persistJournal() {
        if (!scope.isActive) return
        val file = journalFile ?: return
        try {
            val nonTerminalJobs = jobs.values.filter {
                it.state.value == JobState.QUEUED || it.state.value == JobState.RUNNING || it.state.value == JobState.PAUSED
            }
            val sb = java.lang.StringBuilder()
            for (j in nonTerminalJobs) {
                val checkpoint = j.serializeCheckpoint() ?: ""
                sb.appendLine("${j.id}|${j.name}|${j.lane.name}|${j.state.value.name}|${j.retryCount}|${j.retryPolicy.isResumable}|$checkpoint")
            }
            file.parentFile?.mkdirs()
            file.writeText(sb.toString())
        } catch (_: Throwable) {
            // Ignore journal write failures
        }
    }

    /**
     * Simulates process death recovery: restores jobs from journal, marking non-resumable interrupted jobs as failed,
     * and resuming persistable jobs.
     */
    fun restoreJobsAfterProcessDeath(
        jobFactory: (id: String, name: String, lane: WorkLane, checkpoint: String?) -> StudioJob?
    ): List<StudioJob> {
        val file = journalFile ?: return emptyList()
        if (!file.exists()) return emptyList()

        val restored = mutableListOf<StudioJob>()
        try {
            val lines = file.readLines()
            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split("|")
                if (parts.size >= 6) {
                    val id = parts[0]
                    val name = parts[1]
                    val lane = WorkLane.valueOf(parts[2])
                    val stateName = parts[3]
                    val retryCount = parts[4].toIntOrNull() ?: 0
                    val isResumable = parts[5].toBoolean()
                    val checkpoint = if (parts.size >= 7 && parts[6].isNotEmpty()) parts[6] else null

                    val job = jobFactory(id, name, lane, checkpoint)
                    if (job != null) {
                        job.retryCount = retryCount
                        if (checkpoint != null) {
                            job.restoreCheckpoint(checkpoint)
                        }

                        if (isResumable) {
                            job.transitionTo(JobState.QUEUED)
                            enqueue(job)
                        } else {
                            // Non-resumable interrupted job marked failed with process death error
                            job.setError(JobError("PROCESS_DEATH_INTERRUPT", "Job interrupted by process termination", isRecoverable = true))
                            job.transitionTo(JobState.FAILED)
                            jobs[job.id] = job
                        }
                        restored.add(job)
                    }
                }
            }
        } catch (_: Throwable) {
            // Best effort recovery
        }
        updateSnapshots()
        return restored
    }
}
