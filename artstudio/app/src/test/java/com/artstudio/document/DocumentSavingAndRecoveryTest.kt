package com.artstudio.document

import com.artstudio.document.io.*
import com.artstudio.document.lifecycle.*
import com.artstudio.document.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentSavingAndRecoveryTest {

    private lateinit var tempDir: File
    private lateinit var recoveryDir: File
    private lateinit var faultDriver: FaultInjectingStorageDriver

    private fun resolveFixtureDir(): File {
        val direct = File("tests/fixtures/artstudio/v1/sample_projects")
        if (direct.exists()) return direct
        val relative = File("../tests/fixtures/artstudio/v1/sample_projects")
        if (relative.exists()) return relative
        throw IllegalStateException("Cannot find sample_projects directory")
    }

    @Before
    fun setUp() {
        tempDir = File.createTempFile("artstudio_test_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        recoveryDir = File(tempDir, "recovery")
        recoveryDir.mkdirs()

        faultDriver = FaultInjectingStorageDriver()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createSampleDoc(id: String = "doc_test_1", title: String = "Test Art"): ArtDocument {
        val layer1 = LayerNode.RasterLayer(
            id = "layer_base",
            name = "Base Ink",
            width = 1024,
            height = 1024,
            assetPath = "assets/lineart.png"
        )
        val layer2 = LayerNode.RasterLayer(
            id = "layer_shade",
            name = "Shadows",
            width = 1024,
            height = 1024,
            blendMode = BlendModeType.Multiply,
            isClippingMask = true,
            opacity = 0.7f
        )
        val rootGroup = LayerNode.LayerGroup(
            id = "root",
            name = "Root",
            children = listOf(layer1, layer2)
        )
        return ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = id,
            title = title,
            canvas = CanvasConfig(1024, 1024),
            rootGroup = rootGroup,
            palettes = emptyList(),
            referenceImages = emptyList(),
            animation = null,
            autoDrawProvenance = null
        )
    }

    // -------------------------------------------------------------------------
    // 1. Native Save & Reopen: Layer Structure & Exact Parity
    // -------------------------------------------------------------------------
    @Test
    fun testNativeSaveAndReopen_layerStructureAndPropertiesExactParity() = runTest {
        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val doc = createSampleDoc()
        val targetFile = File(tempDir, "project.ffart")
        manager.initNewDocument(doc, targetFile)

        val saveResult = manager.save()
        assertTrue("Save must succeed", saveResult.isSuccess)
        assertTrue("File must exist on disk", targetFile.exists())
        assertTrue("File size must be > 0", targetFile.length() > 0)

        // Verify Saved state
        val state = manager.state.value
        assertTrue("State must be Saved", state is DocumentSaveState.Saved)
        assertEquals(targetFile, (state as DocumentSaveState.Saved).file)

        // Reopen in a clean manager
        val manager2 = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val openResult = manager2.open(targetFile)
        assertTrue("Open must succeed", openResult.isSuccess)
        val reopened = openResult.getOrThrow()

        assertEquals(doc.id, reopened.id)
        assertEquals(doc.title, reopened.title)
        assertEquals(2, reopened.rootGroup.children.size)
        assertEquals("layer_base", reopened.rootGroup.children[0].id)
        assertEquals("layer_shade", reopened.rootGroup.children[1].id)
        assertTrue(reopened.rootGroup.children[1].isClippingMask)
        assertEquals(BlendModeType.Multiply, reopened.rootGroup.children[1].blendMode)
        assertFalse(manager2.isDirty)
    }

    // -------------------------------------------------------------------------
    // 2. Save As: Clones to New File & Preserves Original Intact
    // -------------------------------------------------------------------------
    @Test
    fun testSaveAs_createsNewFileAndPreservesOriginalIntact() = runTest {
        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val fileA = File(tempDir, "original.ffart")
        val fileB = File(tempDir, "cloned.ffart")
        val doc = createSampleDoc(title = "Original Title")
        manager.initNewDocument(doc, fileA)
        manager.save()

        val lengthA = fileA.length()
        assertTrue(lengthA > 0)

        // Modify and Save As fileB
        manager.notifyEdit(strokeCount = 5)
        val saveAsResult = manager.saveAs(fileB, "Cloned Title")
        assertTrue("Save As must succeed", saveAsResult.isSuccess)

        // Both files must exist
        assertTrue(fileA.exists())
        assertTrue(fileB.exists())
        assertEquals(lengthA, fileA.length()) // Original unchanged

        // fileB contains new title
        val reopenedB = DocumentPackage.load(fileB).document
        assertEquals("Cloned Title", reopenedB.title)
    }

    // -------------------------------------------------------------------------
    // 3. Dirty-State Tracking Across Edits and Saves
    // -------------------------------------------------------------------------
    @Test
    fun testDirtyStateTracking_transitionsCorrectlyAcrossEditsAndSaves() = runTest {
        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val targetFile = File(tempDir, "dirty_test.ffart")
        manager.initNewDocument(createSampleDoc(), targetFile)
        assertFalse(manager.isDirty)
        assertEquals(0L, manager.currentRevision)

        // Make edits
        manager.notifyEdit(strokeCount = 3)
        assertTrue(manager.isDirty)
        assertEquals(3L, manager.currentRevision)
        val unsavedState = manager.state.value as DocumentSaveState.Unsaved
        assertEquals(3, unsavedState.dirtyEdits)

        // Save
        manager.save()
        assertFalse(manager.isDirty)
        assertEquals(3L, manager.cleanRevision)
        assertTrue(manager.state.value is DocumentSaveState.Saved)
    }

    // -------------------------------------------------------------------------
    // 4. Bounded Autosave: Debouncing and Threshold Trigger
    // -------------------------------------------------------------------------
    @Test
    fun testBoundedAutosave_debouncingAndThresholdTrigger() = runTest {
        var autosaveCallCount = 0
        var savedRevision = 0L

        val autosavePolicy = AutosavePolicy(
            debounceDelayMs = 50L,
            maxEditsThreshold = 5,
            minIntervalMs = 0L
        )

        val autosaveManager = AutosaveManager(
            policy = autosavePolicy,
            dispatcher = StandardTestDispatcher(testScheduler)
        ) { _, rev ->
            autosaveCallCount++
            savedRevision = rev
        }

        // 1. Single edit: debounces
        autosaveManager.notifyEdit()
        testScheduler.advanceTimeBy(30L)
        assertEquals(0, autosaveCallCount) // Not fired yet (debounce is 50ms)

        testScheduler.advanceTimeBy(30L)
        assertEquals(1, autosaveCallCount) // Debounce expired and fired
        assertEquals(1L, savedRevision)

        // 2. Rapid burst exceeding maxEditsThreshold (5 edits)
        repeat(5) {
            autosaveManager.notifyEdit()
        }
        testScheduler.advanceUntilIdle()
        assertTrue("Must trigger autosave on threshold", autosaveCallCount >= 2)
        assertEquals(6L, savedRevision)

        autosaveManager.cancel()
    }

    // -------------------------------------------------------------------------
    // 5. Repeated Autosave Does Not Corrupt or Leak Files
    // -------------------------------------------------------------------------
    @Test
    fun testRepeatedAutosave_doesNotCorruptOrLeakFiles() = runTest {
        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler),
            autosavePolicy = AutosavePolicy(debounceDelayMs = 10L, maxEditsThreshold = 3, minIntervalMs = 0L)
        )

        val doc = createSampleDoc()
        val targetFile = File(tempDir, "burst_test.ffart")
        manager.initNewDocument(doc, targetFile)

        // Simulate 20 edits
        repeat(20) {
            manager.notifyEdit()
            testScheduler.advanceTimeBy(15L)
        }
        testScheduler.advanceUntilIdle()

        // Verify recovery snapshot is valid
        val snapshotFile = manager.recoveryManager.getSnapshotFile(doc.id)
        assertTrue("Recovery snapshot must exist", snapshotFile.exists())
        assertTrue("Snapshot must have valid package integrity", faultDriver.verifyIntegrity(snapshotFile))

        val loaded = DocumentPackage.load(snapshotFile)
        assertEquals(doc.id, loaded.document.id)

        manager.close()
    }

    // -------------------------------------------------------------------------
    // 6. Process Death / Interrupted Session: Recovery Available Detected
    // -------------------------------------------------------------------------
    @Test
    fun testProcessDeathAndInterruptedSession_recoveryAvailableDetected() = runTest {
        val targetFile = File(tempDir, "crash_test.ffart")
        val doc = createSampleDoc()

        // Session 1: initial save
        val session1 = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        session1.initNewDocument(doc, targetFile)
        session1.save()

        // Unsaved edits written to recovery
        session1.recoveryManager.writeRecoverySnapshot(
            document = doc.copy(title = "Unsaved Crashed Title"),
            assets = emptyMap(),
            originalFile = targetFile,
            revision = 10L
        )

        // Simulate sudden process termination (session1 abandoned without commit)
        // Session 2: newly launched app opens targetFile
        val session2 = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        session2.open(targetFile)
        val state = session2.state.value
        assertTrue("State must be RecoveryAvailable", state is DocumentSaveState.RecoveryAvailable)
        val recState = state as DocumentSaveState.RecoveryAvailable
        assertEquals(10L, recState.backupRevision)
        assertTrue(recState.recoveryFile.exists())
    }

    // -------------------------------------------------------------------------
    // 7. Restore From Recovery: Recovers Unsaved Edits Accurately
    // -------------------------------------------------------------------------
    @Test
    fun testRestoreFromRecovery_recoversUnsavedEditsAccurately() = runTest {
        val targetFile = File(tempDir, "restore_test.ffart")
        val doc = createSampleDoc(title = "Original Saved Title")

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)
        manager.save()

        // Write recovery snapshot with modified title
        manager.recoveryManager.writeRecoverySnapshot(
            document = doc.copy(title = "Recovered Work After Crash"),
            assets = emptyMap(),
            originalFile = targetFile,
            revision = 5L
        )

        // Restore
        val restoreResult = manager.restoreFromRecovery()
        assertTrue("Restore must succeed", restoreResult.isSuccess)
        val restored = restoreResult.getOrThrow()
        assertEquals("Recovered Work After Crash", restored.title)
        assertTrue(manager.isDirty)
    }

    // -------------------------------------------------------------------------
    // 8. Discard Recovery: Only Deletes On Explicit User Command
    // -------------------------------------------------------------------------
    @Test
    fun testDiscardRecovery_onlyDeletesOnExplicitCommand() = runTest {
        val targetFile = File(tempDir, "discard_test.ffart")
        val doc = createSampleDoc()

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)

        manager.recoveryManager.writeRecoverySnapshot(doc, emptyMap(), targetFile, 2L)
        val snapshot = manager.recoveryManager.getSnapshotFile(doc.id)
        assertTrue("Snapshot must exist before discard", snapshot.exists())

        // Discard
        val discarded = manager.discardRecovery()
        assertTrue(discarded)
        assertFalse("Snapshot must be removed after explicit discard", snapshot.exists())
    }

    // -------------------------------------------------------------------------
    // 9. Storage Failure Seam: Disk Full Preserves Last Valid File
    // -------------------------------------------------------------------------
    @Test
    fun testStorageFailureSeam_diskFullPreservesLastValidFile() = runTest {
        val targetFile = File(tempDir, "disk_full_test.ffart")
        val doc = createSampleDoc()

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)
        manager.save()
        val originalBytes = targetFile.readBytes()

        // Inject Disk Full error
        faultDriver.simulateDiskFull = true
        manager.notifyEdit(strokeCount = 2)

        val saveResult = manager.save()
        assertTrue("Save must fail on disk full", saveResult.isFailure)
        assertTrue("State must be SaveFailed", manager.state.value is DocumentSaveState.SaveFailed)

        // Original file must be 100% intact!
        assertTrue("Original file must survive", targetFile.exists())
        assertArrayEquals("Original file bytes must not be corrupted", originalBytes, targetFile.readBytes())
    }

    // -------------------------------------------------------------------------
    // 10. Storage Failure Seam: Permission Denied Preserves Last Valid File
    // -------------------------------------------------------------------------
    @Test
    fun testStorageFailureSeam_permissionDeniedPreservesLastValidFile() = runTest {
        val targetFile = File(tempDir, "perm_test.ffart")
        val doc = createSampleDoc()

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)
        manager.save()
        val originalBytes = targetFile.readBytes()

        // Inject Permission Denied
        faultDriver.simulatePermissionDenied = true
        manager.notifyEdit(strokeCount = 1)

        val saveResult = manager.save()
        assertTrue("Save must fail on permission denial", saveResult.isFailure)
        assertTrue("State must be SaveFailed", manager.state.value is DocumentSaveState.SaveFailed)
        assertArrayEquals("Original file must be preserved", originalBytes, targetFile.readBytes())
    }

    // -------------------------------------------------------------------------
    // 11. Storage Failure Seam: Interrupted Write Rolls Back Cleanly
    // -------------------------------------------------------------------------
    @Test
    fun testStorageFailureSeam_interruptedWriteRollsBackCleanly() = runTest {
        val targetFile = File(tempDir, "interrupted_test.ffart")
        val doc = createSampleDoc()

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)
        manager.save()
        val originalBytes = targetFile.readBytes()

        // Inject Interrupted Write
        faultDriver.simulateInterruptedWrite = true
        manager.notifyEdit(strokeCount = 4)

        val saveResult = manager.save()
        assertTrue("Save must fail on interrupted write", saveResult.isFailure)
        assertTrue("State must be SaveFailed", manager.state.value is DocumentSaveState.SaveFailed)
        assertArrayEquals("Original file must remain valid", originalBytes, targetFile.readBytes())
        assertTrue("Temp files must be cleaned up", faultDriver.cleanedUpTempFiles > 0)
    }

    // -------------------------------------------------------------------------
    // 12. Write Verification Seam: Checksum Failure Aborts & Preserves File
    // -------------------------------------------------------------------------
    @Test
    fun testWriteVerificationSeam_verificationFailureAbortsAndPreservesFile() = runTest {
        val targetFile = File(tempDir, "verify_fail_test.ffart")
        val doc = createSampleDoc()

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)
        manager.save()
        val originalBytes = targetFile.readBytes()

        // Inject Verification Failure
        faultDriver.simulateVerificationFailure = true
        manager.notifyEdit()

        val saveResult = manager.save()
        assertTrue("Save must fail if write verification fails", saveResult.isFailure)
        assertTrue("State must be SaveFailed", manager.state.value is DocumentSaveState.SaveFailed)
        assertArrayEquals("Original file must remain untouched", originalBytes, targetFile.readBytes())
    }

    // -------------------------------------------------------------------------
    // 13. Insufficient Storage Check: Aborts Before Write When Space Low
    // -------------------------------------------------------------------------
    @Test
    fun testInsufficientStorageCheck_abortsBeforeWriteWhenSpaceLow() = runTest {
        val targetFile = File(tempDir, "low_space_test.ffart")
        val doc = createSampleDoc()

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        manager.initNewDocument(doc, targetFile)

        // Set free space to 500 KB (< 1 MB minimum required)
        faultDriver.freeSpaceOverrideBytes = 500 * 1024L
        faultDriver.resetStats()

        val saveResult = manager.save()
        assertTrue("Save must fail due to insufficient space", saveResult.isFailure)
        val state = manager.state.value as DocumentSaveState.SaveFailed
        assertTrue(state.reason.contains("Insufficient storage", ignoreCase = true))
        assertEquals("Zero write attempts should be made", 0, faultDriver.writeAttempts)
    }

    // -------------------------------------------------------------------------
    // 14. Legacy Migration On Open: Migrates v1 to v2 and Marks Dirty
    // -------------------------------------------------------------------------
    @Test
    fun testLegacyMigrationOnOpen_migratesV1ToV2AndMarksDirty() = runTest {
        val fixtureDir = resolveFixtureDir()
        val legacyJson = File(fixtureDir, "sample_legacy_v1.json")
        assertTrue(legacyJson.exists())

        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val openResult = manager.open(legacyJson)
        assertTrue("Open legacy JSON must succeed", openResult.isSuccess)
        val doc = openResult.getOrThrow()

        // Migrated to v2
        assertEquals(ArtDocument.CURRENT_SCHEMA_VERSION, doc.schemaVersion)
        assertEquals("root_group", doc.rootGroup.id)
        assertTrue("Migrated document must be marked dirty so user can save as v2", manager.isDirty)

        // Now save to .ffart
        val newFfart = File(tempDir, "migrated.ffart")
        val saveResult = manager.saveAs(newFfart)
        assertTrue("Saving migrated document as .ffart must succeed", saveResult.isSuccess)

        val reloaded = DocumentPackage.load(newFfart)
        assertEquals(ArtDocument.CURRENT_SCHEMA_VERSION, reloaded.document.schemaVersion)
    }

    // -------------------------------------------------------------------------
    // 15. User Cancellation: Leaves Document Untouched
    // -------------------------------------------------------------------------
    @Test
    fun testSaveCancellation_preservesLastValidFile() = runTest {
        val manager = DocumentLifecycleManager(
            recoveryRootDir = recoveryDir,
            storageDriver = faultDriver,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        val targetFile = File(tempDir, "cancel_test.ffart")
        manager.initNewDocument(createSampleDoc(), targetFile)
        manager.save()

        manager.cancelSave()
        val state = manager.state.value
        assertTrue("State must reflect cancellation", state is DocumentSaveState.SaveFailed)
        assertTrue((state as DocumentSaveState.SaveFailed).reason.contains("cancelled", ignoreCase = true))
    }
}
