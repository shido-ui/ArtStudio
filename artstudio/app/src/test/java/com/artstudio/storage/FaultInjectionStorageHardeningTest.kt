package com.artstudio.storage

import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.lifecycle.*
import com.artstudio.document.model.*
import com.artstudio.export.*
import com.artstudio.importer.ImageDownsampler
import com.artstudio.job.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FaultInjectionStorageHardeningTest {

    private lateinit var rootTestDir: File
    private lateinit var projectsDir: File
    private lateinit var recoveryDir: File
    private lateinit var tempDir: File
    private lateinit var tempFileManager: BoundedTempFileManager
    private lateinit var faultDriver: FaultInjectingStorageDriver
    private lateinit var spaceManager: StorageSpaceManager

    @Before
    fun setUp() {
        rootTestDir = File.createTempFile("artstudio_fault_test_", "_dir")
        rootTestDir.delete()
        rootTestDir.mkdirs()

        projectsDir = File(rootTestDir, "projects").apply { mkdirs() }
        recoveryDir = File(rootTestDir, "recovery").apply { mkdirs() }
        tempDir = File(rootTestDir, "scratch_temp").apply { mkdirs() }

        tempFileManager = BoundedTempFileManager(tempDir, maxQuotaBytes = 32 * 1024 * 1024L) // 32 MB quota
        faultDriver = FaultInjectingStorageDriver()
        spaceManager = StorageSpaceManager(tempFileManager, faultDriver)
    }

    @After
    fun tearDown() {
        rootTestDir.deleteRecursively()
    }

    private fun createSampleDoc(id: String = "fault_doc_1", title: String = "Test Artwork"): ArtDocument {
        val layer = LayerNode.RasterLayer(
            id = "layer_base",
            name = "Base Ink",
            width = 1024,
            height = 1024
        )
        return ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = id,
            title = title,
            canvas = CanvasConfig(1024, 1024),
            rootGroup = LayerNode.LayerGroup(
                id = "root",
                name = "Root",
                children = listOf(layer)
            ),
            palettes = emptyList(),
            referenceImages = emptyList(),
            animation = null,
            autoDrawProvenance = null
        )
    }

    // -------------------------------------------------------------------------
    // 1. Insufficient Storage Check: Aborts Before Write & Warns
    // -------------------------------------------------------------------------
    @Test
    fun test1_insufficientStorage_abortsBeforeWrite_andWarnsUser() {
        val doc = createSampleDoc()
        val targetFile = File(projectsDir, "low_storage_project.ffart")
        val estimate = spaceManager.estimateSave(doc)

        // Simulate critically low storage (500 KB available, but 11 MB required)
        faultDriver.freeSpaceOverrideBytes = 500 * 1024L

        val health = spaceManager.checkStorageHealth(targetFile.parentFile ?: projectsDir, estimate)
        assertEquals("State must be CRITICAL_INSUFFICIENT", StorageHealthState.CRITICAL_INSUFFICIENT, health.state)
        assertFalse("Cannot proceed safely when storage critical", health.canProceedSafely)
        assertNotNull("Warning message must be populated", health.warningMessage)

        var writeAttempted = false
        val result = spaceManager.executeWithStorageGuard(targetFile.parentFile ?: projectsDir, estimate) {
            writeAttempted = true
            targetFile.writeText("should_never_be_written")
        }

        assertTrue("Execution guard must fail", result.isFailure)
        assertTrue("Exception must be InsufficientStorageException", result.exceptionOrNull() is InsufficientStorageException)
        assertFalse("Write must NEVER be attempted on insufficient storage", writeAttempted)
        assertFalse("Target file must not be created", targetFile.exists())
    }

    // -------------------------------------------------------------------------
    // 2. Low Storage Warning: Warns & Allows User Decision
    // -------------------------------------------------------------------------
    @Test
    fun test2_lowStorageWarning_triggersWarning_andAllowsUserDecision() {
        val doc = createSampleDoc()
        val targetFile = File(projectsDir, "tight_storage_project.ffart")
        val estimate = spaceManager.estimateSave(doc) // ~11 MB needed

        // Simulate tight storage (20 MB free, which is < 50 MB warning floor and < 3x estimate)
        faultDriver.freeSpaceOverrideBytes = 20 * 1024 * 1024L

        val health = spaceManager.checkStorageHealth(targetFile.parentFile ?: projectsDir, estimate)
        assertEquals("State must be LOW_STORAGE_WARNING", StorageHealthState.LOW_STORAGE_WARNING, health.state)
        assertTrue("Can technically proceed if user acknowledges", health.canProceedSafely)

        // Case A: User declines warning -> operation cancels gracefully
        var warningInvoked = false
        val cancelResult = spaceManager.executeWithStorageGuard(
            directory = targetFile.parentFile ?: projectsDir,
            estimate = estimate,
            onWarning = {
                warningInvoked = true
                false // User declines
            }
        ) {
            targetFile.writeText("data")
        }
        assertTrue("Warning callback must be invoked", warningInvoked)
        assertTrue("Operation must be cancelled", cancelResult.isFailure)
        assertFalse("Target file must not be written if user declines", targetFile.exists())

        // Case B: User accepts warning -> operation completes
        val acceptResult = spaceManager.executeWithStorageGuard(
            directory = targetFile.parentFile ?: projectsDir,
            estimate = estimate,
            onWarning = { true } // User accepts
        ) {
            targetFile.writeText("acknowledged_data")
            "success"
        }
        assertTrue("Operation must succeed when user accepts", acceptResult.isSuccess)
        assertEquals("success", acceptResult.getOrNull())
        assertTrue(targetFile.exists())
    }

    // -------------------------------------------------------------------------
    // 3. Failure Recovery Options: Exposes Retry, Reduce-Quality & Free-Space
    // -------------------------------------------------------------------------
    @Test
    fun test3_failureRecoveryOptions_exposesRetryReduceQualityAndFreeSpace() {
        val estimate = spaceManager.estimateExport(width = 2048, height = 2048, format = "PNG")
        faultDriver.freeSpaceOverrideBytes = 1 * 1024 * 1024L // 1 MB free
        tempFileManager.registerTempFile(File(tempDir, "scratch.tmp"), JobType.ANALYSIS_PROCESSING, knownSizeBytes = 15 * 1024 * 1024L)

        val report = spaceManager.checkStorageHealth(tempDir, estimate)
        val actions = report.availableRecoveryActions

        assertTrue("Must expose RETRY option", actions.any { it.type == RecoveryActionType.RETRY })
        assertTrue("Must expose REDUCE_QUALITY option", actions.any { it.type == RecoveryActionType.REDUCE_QUALITY })
        assertTrue("Must expose FREE_TEMP_SPACE option", actions.any { it.type == RecoveryActionType.FREE_TEMP_SPACE })

        val reduceQualityOption = actions.first { it.type == RecoveryActionType.REDUCE_QUALITY }
        assertTrue("Reduce quality must offer substantial byte savings", (reduceQualityOption.estimatedSavingsBytes ?: 0L) > 0L)

        val reducedEstimate = spaceManager.resolveReducedQualityEstimate(estimate, scaleMultiplier = 0.5f)
        assertTrue("Reduced estimate must require fewer bytes", reducedEstimate.estimatedBytesNeeded < estimate.estimatedBytesNeeded)
    }

    // -------------------------------------------------------------------------
    // 4. Atomic Write Commit Failure: Rolls Back & Preserves Last Valid Project
    // -------------------------------------------------------------------------
    @Test
    fun test4_atomicWriteCommitFailure_rollsBackAndPreservesLastValidProject() = runTest {
        val targetFile = File(projectsDir, "critical_project.ffart")
        val doc = createSampleDoc(title = "Original Masterpiece")

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)

        // 1. Initial valid save
        val firstSave = manager.save()
        assertTrue("Initial save must succeed", firstSave.isSuccess)
        val originalBytes = targetFile.readBytes()
        assertTrue("Valid project file must exist", targetFile.exists() && originalBytes.isNotEmpty())

        // 2. Modify document and attempt save with commit failure simulation
        manager.notifyEdit(strokeCount = 5)
        faultDriver.simulateCommitFailure = true

        val failedSave = manager.save()
        assertTrue("Save must report failure on commit error", failedSave.isFailure)
        assertTrue("State must be SaveFailed (no false success)", manager.state.value is DocumentSaveState.SaveFailed)

        // 3. INVARIANT: Last valid project must be 100% intact with zero byte corruption!
        assertTrue("Original project must survive rollback", targetFile.exists())
        assertArrayEquals("Original file bytes must be bit-exact preserved", originalBytes, targetFile.readBytes())

        // Verify package can still be loaded without errors
        val packageData = DocumentPackage.load(targetFile)
        assertEquals("Original Masterpiece", packageData.document.title)
    }

    // -------------------------------------------------------------------------
    // 5. Permission Loss: Preserves Existing Project & Allows Retry
    // -------------------------------------------------------------------------
    @Test
    fun test5_permissionLoss_preservesExistingProjectAndAllowsRetry() = runTest {
        val targetFile = File(projectsDir, "permission_project.ffart")
        val doc = createSampleDoc(title = "Initial Project")

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)
        manager.save()
        val originalBytes = targetFile.readBytes()

        // Inject permission loss
        faultDriver.simulatePermissionDenied = true
        manager.notifyEdit(strokeCount = 3)

        val deniedSave = manager.save()
        assertTrue("Save must fail on permission denial", deniedSave.isFailure)
        assertTrue("State must reflect failure", manager.state.value is DocumentSaveState.SaveFailed)
        assertArrayEquals("Original file must be untouched", originalBytes, targetFile.readBytes())

        // Restore permission and retry
        faultDriver.simulatePermissionDenied = false
        val retrySave = manager.save()
        assertTrue("Retry after permission restored must succeed", retrySave.isSuccess)
        assertTrue("State must transition to Saved", manager.state.value is DocumentSaveState.Saved)
    }

    // -------------------------------------------------------------------------
    // 6. Corrupt Document: Open Fails Gracefully & Preserves Recovery Snapshot
    // -------------------------------------------------------------------------
    @Test
    fun test6_corruptDocument_openFailsGracefully_andPreservesRecoverySnapshot() = runTest {
        val corruptFile = File(projectsDir, "corrupted_file.ffart")
        // Write invalid truncated payload
        corruptFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x01, 0x02, 0x00, 0x00))

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        // Stage a valid recovery snapshot for this document ID
        val doc = createSampleDoc(id = "corrupted_file", title = "Saved Before Corruption")
        manager.recoveryManager.writeRecoverySnapshot(
            document = doc,
            assets = emptyMap(),
            originalFile = corruptFile,
            revision = 12L
        )

        // Attempt open corrupt file
        val openResult = manager.open(corruptFile)
        assertTrue("Opening corrupt file must return failure", openResult.isFailure)

        // Invariant: Corrupt file is never deleted or overwritten
        assertTrue("Corrupt file must remain on disk for forensics", corruptFile.exists())

        // Invariant: Recovery snapshot is safely detected and available
        val recoveryState = manager.recoveryManager.checkRecovery("corrupted_file", corruptFile)
        assertNotNull("Recovery material must be preserved", recoveryState)
        assertEquals(12L, recoveryState?.backupRevision)
    }

    // -------------------------------------------------------------------------
    // 7. Large Import Under Storage/Memory Pressure: Downsamples Gracefully
    // -------------------------------------------------------------------------
    @Test
    fun test7_largeImport_underStoragePressure_downsamplesGracefully() {
        val originalW = 2048
        val originalH = 2048
        val fakePixels = IntArray(originalW * originalH) { 0xFF112233.toInt() }

        // When storage or memory is constrained, downsample with budget of 512
        val budget = 512
        val (finalW, finalH, finalPixels) = ImageDownsampler.downsampleIfNeeded(
            pixels = fakePixels,
            width = originalW,
            height = originalH,
            maxDimension = budget
        )

        assertEquals(budget, finalW)
        assertEquals(budget, finalH)
        assertEquals(budget * budget, finalPixels.size)
        // Memory reduced from 16 MB to 1 MB (93.75% reduction)
        assertTrue(finalPixels.size < fakePixels.size / 10)
    }

    // -------------------------------------------------------------------------
    // 8. Job Cancellation: Cleans Temporary Shadow Files Immediately
    // -------------------------------------------------------------------------
    @Test
    fun test8_jobCancellation_cleansTemporaryShadowFilesImmediately() = runTest {
        val scheduler = JobScheduler(dispatcher = StandardTestDispatcher(testScheduler))
        val tempWorkFile = tempFileManager.createTempFile("synth_job_", ".tmp", JobType.ANALYSIS_PROCESSING)
        tempWorkFile.writeText("intermediate_analysis_data")

        val job = SyntheticLongRunningJob(
            name = "Cancellable Analysis Job",
            totalSteps = 20,
            stepDelayMs = 100L,
            tempFileDirectory = tempDir
        )
        job.registerTempFile(tempWorkFile)
        tempFileManager.registerTempFile(tempWorkFile, JobType.ANALYSIS_PROCESSING)

        scheduler.enqueue(job)
        // Cancel job
        scheduler.cancelJob(job.id, "User aborted operation")
        testScheduler.advanceUntilIdle()

        assertEquals("Job state must be CANCELLED", JobState.CANCELLED, job.state.value)
        assertFalse("Registered temp file must be cleaned up on cancellation", tempWorkFile.exists())
    }

    // -------------------------------------------------------------------------
    // 9. App Backgrounding: Pauses Running Job & Resumes From Checkpoint
    // -------------------------------------------------------------------------
    @Test
    fun test9_appBackgrounding_pausesRunningJobAndResumesFromCheckpoint() = runTest {
        val journalFile = File(tempDir, "scheduler_journal.txt")
        val scheduler = JobScheduler(
            dispatcher = StandardTestDispatcher(testScheduler),
            journalFile = journalFile
        )

        val job = SyntheticLongRunningJob(
            name = "Resumable Filter Job",
            totalSteps = 10,
            stepDelayMs = 10L,
            retryPolicy = RetryPolicy(maxRetries = 1, isResumable = true)
        )

        scheduler.enqueue(job)
        testScheduler.advanceTimeBy(35L) // Allow partial execution

        // Simulate app backgrounding
        val pausedCount = scheduler.pauseAll()
        testScheduler.advanceUntilIdle()

        assertEquals(1, pausedCount)
        assertEquals(JobState.PAUSED, job.state.value)
        assertTrue("Completed steps should be > 0 and < 10", job.completedSteps in 1..9)
        val pausedStep = job.completedSteps

        // Simulate app foregrounding
        val resumedCount = scheduler.resumeAll()
        assertEquals(1, resumedCount)
        testScheduler.advanceUntilIdle()

        assertEquals("Job must complete successfully after resume", JobState.SUCCEEDED, job.state.value)
        assertEquals(10, job.completedSteps)
        assertTrue("Job resumed from checkpoint rather than restart", job.completedSteps >= pausedStep)
    }

    // -------------------------------------------------------------------------
    // 10. Interrupted Export: Cleans Temp Files & Does Not Report False Success
    // -------------------------------------------------------------------------
    @Test
    fun test10_interruptedExport_cleansTempFiles_andDoesNotReportFalseSuccess() = runTest {
        val targetExportFile = File(projectsDir, "interrupted_export.png")
        val doc = createSampleDoc()

        val exportService = ArtworkExportService(dispatcher = StandardTestDispatcher(testScheduler))
        val cancelToken = ExportCancellationToken()

        // Cancel token before completion to simulate interruption
        cancelToken.cancel()

        val result = exportService.exportArtwork(
            document = doc,
            config = ExportConfig(format = ExportFormat.PNG),
            targetFile = targetExportFile,
            cancellationToken = cancelToken
        )

        assertEquals("Result must be Cancelled", ExportResult.Cancelled, result)
        assertFalse("Destination file must not exist (no false success / partial writes)", targetExportFile.exists())

        // Verify no leftover .tmp files in target directory
        val tempArtifacts = projectsDir.listFiles { _, name -> name.contains(".tmp") } ?: emptyArray()
        assertEquals("Temporary export artifacts must be cleaned up", 0, tempArtifacts.size)
    }

    // -------------------------------------------------------------------------
    // 11. Interrupted Analysis: Cleans Intermediate Masks & Preserves Canvas
    // -------------------------------------------------------------------------
    @Test
    fun test11_interruptedAnalysis_cleansIntermediateMasks_andPreservesCanvas() {
        val analysisMask = tempFileManager.createTempFile("cv_mask_", ".tmp", JobType.ANALYSIS_PROCESSING)
        analysisMask.writeBytes(ByteArray(1024 * 512)) // 512 KB mask buffer
        tempFileManager.updateTempFileSize(analysisMask)

        assertTrue("Mask file must exist initially", analysisMask.exists())
        assertEquals(512 * 1024L, tempFileManager.currentUsageBytes)

        // Simulate interruption -> clean
        val cleanup = tempFileManager.cleanTempFiles(forceAll = true)
        assertEquals(1, cleanup.deletedFilesCount)
        assertEquals(512 * 1024L, cleanup.freedBytes)
        assertEquals(0L, tempFileManager.currentUsageBytes)
        assertFalse("Intermediate mask must be deleted", analysisMask.exists())
    }

    // -------------------------------------------------------------------------
    // 12. Safe Cleanup: NEVER Deletes Last Valid Project or Recovery Snapshot
    // -------------------------------------------------------------------------
    @Test
    fun test12_safeCleanup_neverDeletesLastValidProjectOrRecoverySnapshot() {
        // Place critical protected assets alongside temporary scratch files
        val validProject = File(tempDir, "masterpiece.ffart").apply { writeText("VALID_PROJECT_DATA") }
        val recoveryDirSub = File(tempDir, "recovery").apply { mkdirs() }
        val recoverySnapshot = File(recoveryDirSub, "recovery_snapshot.ffart").apply { writeText("RECOVERY_SNAPSHOT_DATA") }
        val recoveryManifest = File(recoveryDirSub, "recovery_manifest.json").apply { writeText("{}") }
        val projectIndex = File(tempDir, "projects_index.json").apply { writeText("[]") }
        val projectBackup = File(tempDir, "artwork.ffart.bak_12345").apply { writeText("BACKUP_DATA") }

        // Orphaned scratch files
        val orphan1 = File(tempDir, "scratch_tile_1.tmp").apply { writeText("TEMP1") }
        val orphan2 = File(tempDir, "scratch_tile_2.tmp").apply { writeText("TEMP2") }

        val cleanupResult = tempFileManager.cleanTempFiles(forceAll = true)

        // Invariant: Orphaned temporary files deleted
        assertFalse("Orphan 1 must be deleted", orphan1.exists())
        assertFalse("Orphan 2 must be deleted", orphan2.exists())
        assertEquals(2, cleanupResult.deletedFilesCount)

        // CRITICAL INVARIANT: Protected project and recovery materials 100% PRESERVED!
        assertTrue("Valid project MUST NOT be deleted!", validProject.exists())
        assertTrue("Recovery snapshot MUST NOT be deleted!", recoverySnapshot.exists())
        assertTrue("Recovery manifest MUST NOT be deleted!", recoveryManifest.exists())
        assertTrue("Projects index MUST NOT be deleted!", projectIndex.exists())
        assertTrue("Project backup MUST NOT be deleted!", projectBackup.exists())
        assertTrue("Protected files preserved count must be >= 5", cleanupResult.protectedFilesPreservedCount >= 5)
    }

    // -------------------------------------------------------------------------
    // 13. Bounded Temporary Storage: Enforces Quota & Tracks Peak Usage
    // -------------------------------------------------------------------------
    @Test
    fun test13_boundedTemporaryStorage_enforcesQuotaAndTracksPeakUsage() {
        val smallManager = BoundedTempFileManager(tempDir, maxQuotaBytes = 5 * 1024 * 1024L) // 5 MB quota

        // Allocate for IMPORT (1 MB)
        val f1 = smallManager.createTempFile("import_", ".tmp", JobType.RASTER_IMPORT)
        f1.writeBytes(ByteArray(1024 * 1024))
        smallManager.updateTempFileSize(f1)

        // Allocate for PROCESSING (2 MB)
        val f2 = smallManager.createTempFile("proc_", ".tmp", JobType.ANALYSIS_PROCESSING)
        f2.writeBytes(ByteArray(2 * 1024 * 1024))
        smallManager.updateTempFileSize(f2)

        // Allocate for EXPORT (1 MB)
        val f3 = smallManager.createTempFile("export_", ".tmp", JobType.ARTWORK_EXPORT)
        f3.writeBytes(ByteArray(1024 * 1024))
        smallManager.updateTempFileSize(f3)

        assertEquals(4 * 1024 * 1024L, smallManager.currentUsageBytes)
        assertEquals(4 * 1024 * 1024L, smallManager.peakUsageBytes)

        val peaks = smallManager.peakUsageByJob
        assertEquals(1024 * 1024L, peaks[JobType.RASTER_IMPORT])
        assertEquals(2 * 1024 * 1024L, peaks[JobType.ANALYSIS_PROCESSING])
        assertEquals(1024 * 1024L, peaks[JobType.ARTWORK_EXPORT])

        // Attempting to allocate 3 MB more exceeds 5 MB quota -> triggers quota violation
        try {
            smallManager.createTempFile("overflow_", ".tmp", JobType.ARTWORK_EXPORT, estimatedInitialBytes = 3 * 1024 * 1024L)
            fail("Must throw IOException when temporary storage quota is exceeded")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("quota exceeded", ignoreCase = true) == true)
        }
    }

    // -------------------------------------------------------------------------
    // 14. Free Space Action: Reclaims Temp Space Enabling Successful Operation
    // -------------------------------------------------------------------------
    @Test
    fun test14_freeSpaceRecoveryAction_reclaimsTempDisk_enablingSuccessfulSave() = runTest {
        val targetFile = File(projectsDir, "reclaimed_save.ffart")
        val doc = createSampleDoc()

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)

        // Seed 10 MB of temporary clutter
        val clutterFile = File(tempDir, "clutter_cache.tmp").apply { writeBytes(ByteArray(10 * 1024 * 1024)) }
        tempFileManager.registerTempFile(clutterFile, JobType.ANALYSIS_PROCESSING, knownSizeBytes = 10 * 1024 * 1024L)

        // Storage is tight (5 MB free, need 11 MB)
        faultDriver.freeSpaceOverrideBytes = 5 * 1024 * 1024L
        val estimate = spaceManager.estimateSave(doc)
        val report = spaceManager.checkStorageHealth(projectsDir, estimate)
        assertEquals(StorageHealthState.CRITICAL_INSUFFICIENT, report.state)

        // Execute FREE_TEMP_SPACE action
        val cleanResult = tempFileManager.cleanTempFiles(forceAll = true)
        assertEquals(10 * 1024 * 1024L, cleanResult.freedBytes)

        // Space is now freed -> update free space to 15 MB
        faultDriver.freeSpaceOverrideBytes = 15 * 1024 * 1024L
        val postCleanReport = spaceManager.checkStorageHealth(projectsDir, estimate)
        assertTrue("Storage must be safe to proceed after cleanup", postCleanReport.canProceedSafely)

        // Save succeeds
        val saveResult = manager.save()
        assertTrue("Save must succeed after freeing space", saveResult.isSuccess)
        assertTrue("Target file must exist", targetFile.exists())
    }
}
