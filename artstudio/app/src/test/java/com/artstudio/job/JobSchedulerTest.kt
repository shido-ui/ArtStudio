package com.artstudio.job

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit & Concurrency Verification Suite for Prompt 46:
 * Cancellable, bounded jobs for analysis, large transforms, and export.
 */
class JobSchedulerTest {

    private lateinit var tempDir: File
    private lateinit var scheduler: JobScheduler

    @Before
    fun setUp() {
        tempDir = File("build/tmp/test_job_scheduler_${System.currentTimeMillis()}").apply { mkdirs() }
        scheduler = JobScheduler(
            dispatcher = Dispatchers.Default,
            journalFile = File(tempDir, "job_journal.txt")
        )
    }

    @After
    fun tearDown() {
        scheduler.cancelAll()
        scheduler.scope.cancel()
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 1. Initial State Is Queued
    // =========================================================================

    @Test
    fun testInitialStateIsQueued() {
        val job = SyntheticLongRunningJob(name = "Initial Queue Test", stepDelayMs = 100L)
        assertEquals(JobState.QUEUED, job.state.value)
        val snapshot = scheduler.enqueue(job)
        assertEquals(JobState.QUEUED, snapshot.state)
        assertEquals(job.id, snapshot.id)
    }

    // =========================================================================
    // 2. State Transition: QUEUED -> RUNNING -> SUCCEEDED
    // =========================================================================

    @Test
    fun testTransitionQueuedToRunningToSucceeded() = runBlocking {
        val job = SyntheticLongRunningJob(totalSteps = 3, stepDelayMs = 10L)
        scheduler.enqueue(job)

        // Wait for running or succeeded
        withTimeout(3000L) {
            job.state.first { it == JobState.SUCCEEDED }
        }

        assertEquals(JobState.SUCCEEDED, job.state.value)
        assertEquals(1.0f, job.progress.value.progress, 0.001f)
        assertEquals(3, job.completedSteps)
    }

    // =========================================================================
    // 3. Real Progress Updates During Execution
    // =========================================================================

    @Test
    fun testProgressUpdatesDuringExecution() = runBlocking {
        val job = SyntheticLongRunningJob(totalSteps = 5, stepDelayMs = 20L)
        val observedSteps = mutableListOf<Int>()

        val collector = launch {
            job.progress.collect { p ->
                if (p.stepIndex > 0 && !observedSteps.contains(p.stepIndex)) {
                    observedSteps.add(p.stepIndex)
                }
            }
        }

        scheduler.enqueue(job)

        withTimeout(3000L) {
            job.state.first { it == JobState.SUCCEEDED }
        }
        collector.cancel()

        assertTrue("Must observe intermediate progress steps", observedSteps.isNotEmpty())
        assertEquals(1.0f, job.progress.value.progress, 0.001f)
    }

    // =========================================================================
    // 4. Zero Fake Completion State Invariant
    // =========================================================================

    @Test
    fun testZeroFakeCompletionState() = runBlocking {
        val job = SyntheticLongRunningJob(
            totalSteps = 10,
            stepDelayMs = 15L,
            failAtStep = 4
        )
        scheduler.enqueue(job)

        withTimeout(3000L) {
            job.state.first { it == JobState.FAILED }
        }

        assertEquals(JobState.FAILED, job.state.value)
        // Invariant: Failed job must NEVER report 1.0f progress or SUCCEEDED
        assertTrue("Failed job must not claim 100% progress", job.progress.value.progress < 1.0f)
        assertNotNull(job.error.value)
    }

    // =========================================================================
    // 5. Cooperative Cancellation Stops Execution
    // =========================================================================

    @Test
    fun testCooperativeCancellationStopsExecution() = runBlocking {
        val job = SyntheticLongRunningJob(totalSteps = 20, stepDelayMs = 30L)
        scheduler.enqueue(job)

        delay(50L) // Allow job to start running
        scheduler.cancelJob(job.id, "Test cancellation")

        withTimeout(3000L) {
            job.state.first { it == JobState.CANCELLED }
        }

        assertEquals(JobState.CANCELLED, job.state.value)
        assertTrue("Cancellation should halt steps before 20", job.completedSteps < 20)
    }

    // =========================================================================
    // 6. Cancellation from QUEUED State
    // =========================================================================

    @Test
    fun testCancellationFromQueuedState() = runBlocking {
        val job = SyntheticLongRunningJob(totalSteps = 5, stepDelayMs = 50L)
        job.cancel("Immediate cancel")
        assertEquals(JobState.CANCELLED, job.state.value)

        scheduler.enqueue(job)
        delay(50L)
        assertEquals(JobState.CANCELLED, job.state.value)
        assertEquals(0, job.completedSteps)
    }

    // =========================================================================
    // 7. Cancellation Cleans Up Temporary Files Safely
    // =========================================================================

    @Test
    fun testCancellationCleansUpTemporaryFilesSafely() = runBlocking {
        val workDir = File(tempDir, "temp_cleanup_test").apply { mkdirs() }
        val permanentFile = File(workDir, "keep_me.txt").apply { writeText("original") }

        val job = SyntheticLongRunningJob(
            totalSteps = 20,
            stepDelayMs = 25L,
            tempFileDirectory = workDir
        )

        scheduler.enqueue(job)
        delay(50L) // Allow temp file creation
        scheduler.cancelJob(job.id, "Cancel temp file test")

        withTimeout(3000L) {
            job.state.first { it == JobState.CANCELLED }
        }

        val leftoverTempFiles = workDir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("Registered temporary files must be cleaned up on cancel", 0, leftoverTempFiles.size)
        assertTrue("Non-temp files must not be touched", permanentFile.exists())
    }

    // =========================================================================
    // 8. Job Failure Transitions to FAILED State
    // =========================================================================

    @Test
    fun testJobFailureTransitionsToFailedState() = runBlocking {
        val job = SyntheticLongRunningJob(
            totalSteps = 5,
            stepDelayMs = 10L,
            failAtStep = 2,
            failureError = JobError("DISK_FULL", "No space left on device", isRecoverable = false)
        )

        scheduler.enqueue(job)

        withTimeout(3000L) {
            job.state.first { it == JobState.FAILED }
        }

        assertEquals(JobState.FAILED, job.state.value)
        assertEquals("DISK_FULL", job.error.value?.code)
        assertEquals("No space left on device", job.error.value?.message)
    }

    // =========================================================================
    // 9. Automatic Retry Policy on Recoverable Failure
    // =========================================================================

    @Test
    fun testAutomaticRetryPolicyOnRecoverableFailure() = runBlocking {
        val attempts = AtomicInteger(0)

        // Custom job that fails once with recoverable error, then succeeds on retry
        val retryingJob = object : StudioJob(
            name = "Retry Test Job",
            lane = WorkLane.EXPORT,
            retryPolicy = RetryPolicy(maxRetries = 2, initialBackoffMs = 20L)
        ) {
            override suspend fun execute(context: JobExecutionContext): JobResult {
                val currentAttempt = attempts.incrementAndGet()
                return if (currentAttempt == 1) {
                    JobResult.Failure(JobError("IO_TRANSIENT", "Temporary I/O glitch", isRecoverable = true))
                } else {
                    JobResult.Success("Recovered on attempt $currentAttempt")
                }
            }
        }

        scheduler.enqueue(retryingJob)

        withTimeout(3000L) {
            retryingJob.state.first { it == JobState.SUCCEEDED }
        }

        assertEquals(JobState.SUCCEEDED, retryingJob.state.value)
        assertEquals(1, retryingJob.retryCount)
        assertEquals(2, attempts.get())
    }

    // =========================================================================
    // 10. Max Retries Exceeded Transitions to Terminal Failure
    // =========================================================================

    @Test
    fun testMaxRetriesExceededTransitionsToTerminalFailure() = runBlocking {
        val retryingJob = object : StudioJob(
            name = "Exceed Retries Job",
            lane = WorkLane.EXPORT,
            retryPolicy = RetryPolicy(maxRetries = 2, initialBackoffMs = 10L)
        ) {
            override suspend fun execute(context: JobExecutionContext): JobResult {
                return JobResult.Failure(JobError("PERSISTENT_ERROR", "Persistent network timeout", isRecoverable = true))
            }
        }

        scheduler.enqueue(retryingJob)

        withTimeout(3000L) {
            retryingJob.state.first { it == JobState.FAILED }
        }

        assertEquals(JobState.FAILED, retryingJob.state.value)
        assertEquals(2, retryingJob.retryCount)
    }

    // =========================================================================
    // 11. Pause and Resume for Supported Resumable Job
    // =========================================================================

    @Test
    fun testPauseAndResumeForSupportedJob() = runBlocking {
        val job = SyntheticLongRunningJob(
            totalSteps = 10,
            stepDelayMs = 25L,
            retryPolicy = RetryPolicy.RESUMABLE_BATCH
        )

        scheduler.enqueue(job)
        delay(60L) // Allow job to process 1 or 2 steps

        val paused = scheduler.pauseJob(job.id)
        assertTrue("Pause must return true for resumable job", paused)

        withTimeout(3000L) {
            job.state.first { it == JobState.PAUSED }
        }

        val stepAtPause = job.completedSteps
        assertTrue("Job must have paused after at least 1 step", stepAtPause in 1..9)

        // Resume execution
        val resumed = scheduler.resumeJob(job.id)
        assertTrue("Resume must return true for paused job", resumed)

        withTimeout(3000L) {
            job.state.first { it == JobState.SUCCEEDED }
        }

        assertEquals(JobState.SUCCEEDED, job.state.value)
        assertEquals(10, job.completedSteps)
    }

    // =========================================================================
    // 12. Non-Resumable Job Rejects Pause
    // =========================================================================

    @Test
    fun testNonResumableJobRejectsPause() = runBlocking {
        val job = SyntheticLongRunningJob(
            totalSteps = 5,
            stepDelayMs = 20L,
            retryPolicy = RetryPolicy.NO_RETRY // isResumable = false
        )

        scheduler.enqueue(job)
        delay(15L)

        val paused = scheduler.pauseJob(job.id)
        assertFalse("Non-resumable job must reject pause", paused)
        assertNotEquals(JobState.PAUSED, job.state.value)
    }

    // =========================================================================
    // 13. Lane Separation: Canvas Input Never Blocked by Heavy Lanes
    // =========================================================================

    @Test
    fun testLaneSeparationEnsuresCanvasInputNeverBlocked() = runBlocking {
        // Saturate the TRANSFORM lane (concurrency limit = 1) with a long job
        val heavyJob = SyntheticLongRunningJob(
            lane = WorkLane.TRANSFORM,
            totalSteps = 10,
            stepDelayMs = 100L
        )
        scheduler.enqueue(heavyJob)
        delay(20L)
        assertEquals(JobState.RUNNING, heavyJob.state.value)

        // Enqueue a high-priority CANVAS_INPUT job
        val inputExecuted = AtomicBoolean(false)
        val canvasInputJob = object : StudioJob(
            name = "Canvas Input Dispatch",
            lane = WorkLane.CANVAS_INPUT
        ) {
            override suspend fun execute(context: JobExecutionContext): JobResult {
                inputExecuted.set(true)
                return JobResult.Success("Dispatched input")
            }
        }

        scheduler.enqueue(canvasInputJob)

        // Must complete immediately without waiting for heavyJob
        withTimeout(500L) {
            canvasInputJob.state.first { it == JobState.SUCCEEDED }
        }

        assertTrue("Canvas input must execute immediately despite busy transform lane", inputExecuted.get())
        assertEquals(JobState.RUNNING, heavyJob.state.value)
    }

    // =========================================================================
    // 14. Memory-Aware Scheduling Defers Jobs Exceeding Budget
    // =========================================================================

    @Test
    fun testMemoryAwareSchedulingDefersHighMemoryJob() = runBlocking {
        val memoryCappedScheduler = JobScheduler(
            dispatcher = Dispatchers.Default,
            maxMemoryCeilingBytes = 20 * 1024 * 1024L // 20 MB ceiling
        )

        // Job 1 uses 16 MB of 20 MB ceiling
        val job1 = SyntheticLongRunningJob(
            name = "Mem Job 1",
            estimatedMemoryBytes = 16 * 1024 * 1024L,
            totalSteps = 5,
            stepDelayMs = 50L
        )

        // Job 2 needs 12 MB (16 + 12 = 28 MB > 20 MB ceiling -> must be deferred)
        val job2Started = AtomicBoolean(false)
        val job2 = object : StudioJob(
            name = "Mem Job 2",
            lane = WorkLane.TRANSFORM,
            estimatedMemoryBytes = 12 * 1024 * 1024L
        ) {
            override suspend fun execute(context: JobExecutionContext): JobResult {
                job2Started.set(true)
                return JobResult.Success("Ran Job 2")
            }
        }

        memoryCappedScheduler.enqueue(job1)
        delay(20L)
        assertEquals(JobState.RUNNING, job1.state.value)

        memoryCappedScheduler.enqueue(job2)
        delay(50L)

        // While job1 is running, job2 must be waiting in QUEUED state
        assertEquals(JobState.RUNNING, job1.state.value)
        assertEquals(JobState.QUEUED, job2.state.value)
        assertFalse("Job 2 must be deferred due to memory budget", job2Started.get())

        // Once job1 finishes and frees memory, job2 executes
        withTimeout(3000L) {
            job2.state.first { it == JobState.SUCCEEDED }
        }

        assertTrue("Job 2 must succeed once memory is released", job2Started.get())
        memoryCappedScheduler.scope.cancel()
    }

    // =========================================================================
    // 15. Process Death Recovery Restores Journal
    // =========================================================================

    @Test
    fun testProcessDeathRecoveryRestoresJournal() = runBlocking {
        val journal = File(tempDir, "process_death_journal.txt")
        val deathScheduler = JobScheduler(
            dispatcher = Dispatchers.Default,
            journalFile = journal
        )

        val resumableJob = SyntheticLongRunningJob(
            id = "resumable_job_001",
            name = "Resumable Batch",
            totalSteps = 10,
            stepDelayMs = 25L,
            retryPolicy = RetryPolicy.RESUMABLE_BATCH
        )

        val nonResumableJob = SyntheticLongRunningJob(
            id = "non_resumable_002",
            name = "One-Shot Export",
            totalSteps = 10,
            stepDelayMs = 25L,
            retryPolicy = RetryPolicy.NO_RETRY
        )

        deathScheduler.enqueue(resumableJob)
        deathScheduler.enqueue(nonResumableJob)
        delay(60L) // Let both start

        // Persist journal state
        deathScheduler.persistJournal()
        assertTrue("Journal file must exist on disk", journal.exists() && journal.length() > 0)

        // Simulate process death: cancel existing scheduler
        deathScheduler.scope.cancel()

        // Create new scheduler after cold restart
        val recoveredScheduler = JobScheduler(
            dispatcher = Dispatchers.Default,
            journalFile = journal
        )

        val restoredJobs = recoveredScheduler.restoreJobsAfterProcessDeath { id, name, lane, checkpoint ->
            when (id) {
                "resumable_job_001" -> SyntheticLongRunningJob(
                    id = id,
                    name = name,
                    lane = lane,
                    totalSteps = 10,
                    stepDelayMs = 15L,
                    retryPolicy = RetryPolicy.RESUMABLE_BATCH
                )
                "non_resumable_002" -> SyntheticLongRunningJob(
                    id = id,
                    name = name,
                    lane = lane,
                    totalSteps = 10,
                    stepDelayMs = 15L,
                    retryPolicy = RetryPolicy.NO_RETRY
                )
                else -> null
            }
        }

        assertEquals(2, restoredJobs.size)
        val restoredResumable = restoredJobs.first { it.id == "resumable_job_001" }
        val restoredNonResumable = restoredJobs.first { it.id == "non_resumable_002" }

        // Non-resumable marked failed with process death error
        assertEquals(JobState.FAILED, restoredNonResumable.state.value)
        assertEquals("PROCESS_DEATH_INTERRUPT", restoredNonResumable.error.value?.code)

        // Resumable finishes successfully
        withTimeout(3000L) {
            restoredResumable.state.first { it == JobState.SUCCEEDED }
        }
        assertEquals(JobState.SUCCEEDED, restoredResumable.state.value)
        recoveredScheduler.scope.cancel()
    }

    // =========================================================================
    // 16. Concurrency Test: Configured Worker Limit Is Never Exceeded
    // =========================================================================

    @Test
    fun testConcurrencyLimitNeverExceededUnderLoad() = runBlocking {
        val maxExportLimit = 2
        val exportBoundedScheduler = JobScheduler(
            dispatcher = Dispatchers.Default,
            concurrencyOverrides = mapOf(WorkLane.EXPORT to maxExportLimit)
        )

        val jobCount = 12
        val jobs = (1..jobCount).map { i ->
            SyntheticLongRunningJob(
                name = "Concurrent Export $i",
                lane = WorkLane.EXPORT,
                totalSteps = 4,
                stepDelayMs = 30L
            )
        }

        // Launch all 12 jobs simultaneously
        jobs.forEach { exportBoundedScheduler.enqueue(it) }

        // Periodically verify that activeWorkers <= maxExportLimit at all times
        val checkJob = launch {
            while (isActive) {
                val currentActive = exportBoundedScheduler.activeWorkers[WorkLane.EXPORT]?.get() ?: 0
                assertTrue(
                    "Active workers ($currentActive) must NEVER exceed limit ($maxExportLimit)",
                    currentActive <= maxExportLimit
                )
                delay(5L)
            }
        }

        // Wait for all jobs to complete
        withTimeout(5000L) {
            jobs.forEach { j -> j.state.first { it == JobState.SUCCEEDED } }
        }
        checkJob.cancel()

        val peak = exportBoundedScheduler.peakActiveWorkers[WorkLane.EXPORT]?.get() ?: 0
        assertTrue("Peak workers ($peak) must be <= max limit ($maxExportLimit)", peak <= maxExportLimit)
        assertTrue("Scheduler must have utilized available workers", peak > 0)
        exportBoundedScheduler.scope.cancel()
    }

    // =========================================================================
    // 17. UI Thread Responsiveness Baseline Test
    // =========================================================================

    @Test
    fun testUiRemainsInteractiveDuringSyntheticLongRunningJob() = runBlocking {
        // Launch a long background export job (250 ms)
        val heavyJob = SyntheticLongRunningJob(
            name = "Heavy Background Job",
            lane = WorkLane.EXPORT,
            totalSteps = 10,
            stepDelayMs = 25L
        )
        scheduler.enqueue(heavyJob)

        // Measure dispatch latency on simulated UI thread checks
        val latencySamples = mutableListOf<Long>()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < 200L) {
            val t0 = System.nanoTime()
            // Simulate UI event dispatch / frame draw
            yield()
            val dtNs = System.nanoTime() - t0
            latencySamples.add(dtNs)
            delay(10L)
        }

        withTimeout(3000L) {
            heavyJob.state.first { it == JobState.SUCCEEDED }
        }

        assertTrue("Must have captured UI latency samples", latencySamples.isNotEmpty())
        val avgLatencyMs = (latencySamples.average() / 1_000_000.0)
        // Baseline criteria: UI dispatch latency must remain low (< 10 ms) during background work
        assertTrue("UI dispatch latency must remain interactive (< 10 ms), was $avgLatencyMs ms", avgLatencyMs < 10.0)
    }
}
