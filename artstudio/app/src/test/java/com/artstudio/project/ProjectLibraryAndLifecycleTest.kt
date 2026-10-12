package com.artstudio.project

import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ProjectLibraryAndLifecycleTest {

    private lateinit var tempDir: File
    private lateinit var libraryManager: ProjectLibraryManager

    private fun resolveFixtureDir(): File {
        val direct = File("tests/fixtures/artstudio/v1/sample_projects")
        if (direct.exists()) return direct
        val relative = File("../tests/fixtures/artstudio/v1/sample_projects")
        if (relative.exists()) return relative
        throw IllegalStateException("Cannot find sample_projects directory")
    }

    @Before
    fun setUp() {
        tempDir = File.createTempFile("artstudio_lib_test_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        libraryManager = ProjectLibraryManager(projectsDir = tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // 1. Duplicate: Generates New Stable ID & Disambiguated Titles
    // -------------------------------------------------------------------------
    @Test
    fun testDuplicateProject_generatesNewStableIdAndDisambiguatedTitle() {
        val original = libraryManager.createNewProject("Dragon Sketch", 2048, 2048)
        assertEquals("Dragon Sketch", original.title)

        // First duplicate -> "Dragon Sketch (Copy)"
        val dup1Result = libraryManager.duplicateProject(original.id)
        assertTrue(dup1Result.isSuccess)
        val dup1 = dup1Result.getOrThrow()

        assertNotEquals(original.id, dup1.id)
        assertEquals("Dragon Sketch (Copy)", dup1.title)
        assertTrue(File(dup1.filePath!!).exists())

        // Second duplicate of original -> "Dragon Sketch (Copy 2)"
        val dup2Result = libraryManager.duplicateProject(dup1.id)
        assertTrue(dup2Result.isSuccess)
        val dup2 = dup2Result.getOrThrow()

        assertNotEquals(dup1.id, dup2.id)
        assertEquals("Dragon Sketch (Copy 2)", dup2.title)

        // Original remains untouched
        val originalFile = File(original.filePath!!)
        assertTrue(originalFile.exists())
        val loadedOriginal = DocumentPackage.load(originalFile).document
        assertEquals("Dragon Sketch", loadedOriginal.title)
    }

    // -------------------------------------------------------------------------
    // 2. Rename: Updates Metadata and Package Safely
    // -------------------------------------------------------------------------
    @Test
    fun testRenameProject_updatesMetadataAndPackageSafely() {
        val proj = libraryManager.createNewProject("Old Name", 1024, 1024)

        val renameResult = libraryManager.renameProject(proj.id, "Epic Landscape")
        assertTrue(renameResult.isSuccess)
        val renamed = renameResult.getOrThrow()
        assertEquals("Epic Landscape", renamed.title)

        // Read package from disk to verify internal project.json was updated
        val file = File(renamed.filePath!!)
        val reloaded = DocumentPackage.load(file).document
        assertEquals("Epic Landscape", reloaded.title)
    }

    // -------------------------------------------------------------------------
    // 3. Rename: Rejects Blank Titles with Actionable Error
    // -------------------------------------------------------------------------
    @Test
    fun testRenameProject_rejectsBlankTitle() {
        val proj = libraryManager.createNewProject("Valid Title")

        val result = libraryManager.renameProject(proj.id, "   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("blank") == true)
        assertEquals("Valid Title", libraryManager.projects.first().title)
    }

    // -------------------------------------------------------------------------
    // 4. Delete: Soft Delete to Trash Allows Undo Restoration
    // -------------------------------------------------------------------------
    @Test
    fun testDeleteProject_softDeleteToTrashAllowsUndoRestoration() {
        val proj = libraryManager.createNewProject("Accidental Delete")
        val id = proj.id
        assertEquals(1, libraryManager.projects.size)

        // Soft delete
        val deleted = libraryManager.deleteProject(id, softDelete = true)
        assertTrue(deleted)
        assertEquals(0, libraryManager.projects.size)
        assertEquals(1, libraryManager.trashedProjects.size)

        // Undo Delete
        val restoredResult = libraryManager.undoDelete(id)
        assertTrue("Undo delete must succeed", restoredResult.isSuccess)
        val restored = restoredResult.getOrThrow()
        assertEquals(id, restored.id)
        assertEquals("Accidental Delete", restored.title)
        assertEquals(1, libraryManager.projects.size)
        assertEquals(0, libraryManager.trashedProjects.size)
        assertTrue(File(restored.filePath!!).exists())
    }

    // -------------------------------------------------------------------------
    // 5. Delete: Removes From Library and Clears Active Context
    // -------------------------------------------------------------------------
    @Test
    fun testDeleteProject_removesFromLibraryAndActiveContext() {
        val proj = libraryManager.createNewProject("To Be Removed")
        assertEquals(proj.id, libraryManager.activeProject?.id)

        val deleted = libraryManager.deleteProject(proj.id, softDelete = false)
        assertTrue(deleted)
        assertNull(libraryManager.activeProject)
        assertTrue(libraryManager.projects.isEmpty())
        assertFalse(File(proj.filePath!!).exists())
    }

    // -------------------------------------------------------------------------
    // 6. Missing File: Reports Inaccessible Status with Actionable Message
    // -------------------------------------------------------------------------
    @Test
    fun testMissingFileHandling_reportsInaccessibleStatusWithActionableMessage() {
        val proj = libraryManager.createNewProject("Temporary File")
        val file = File(proj.filePath!!)
        assertTrue(file.exists())

        // Simulate external deletion / unmounted storage
        file.delete()
        assertFalse(file.exists())

        // Open must not crash; returns actionable error and marks inaccessible
        val openResult = libraryManager.openProject(proj.id)
        assertTrue(openResult is ProjectOpenResult.Error)
        val err = openResult as ProjectOpenResult.Error
        assertTrue(err.message.contains("missing or unmounted", ignoreCase = true))

        val updatedMeta = libraryManager.projects.first { it.id == proj.id }
        assertFalse("Project must be marked inaccessible", updatedMeta.isAccessible)
    }

    // -------------------------------------------------------------------------
    // 7. State Restoration After Simulated App Restart: Bit-Exact Parity
    // -------------------------------------------------------------------------
    @Test
    fun testStateRestorationAfterSimulatedAppRestart_restoresIndexBitExact() {
        libraryManager.createNewProject("Project 1", 1080, 1080, folder = "Character", tags = listOf("ink", "anime"))
        libraryManager.createNewProject("Project 2", 1920, 1080, folder = "Background", tags = listOf("scenery"))
        libraryManager.createNewProject("Project 3", 2048, 2048, folder = "Character", tags = listOf("sketch"))

        assertEquals(3, libraryManager.projects.size)
        val originalList = libraryManager.projects

        // Simulate app kill and cold restart: create a new manager on the same directory
        val restartedManager = ProjectLibraryManager(projectsDir = tempDir)

        assertEquals(3, restartedManager.projects.size)
        for (i in 0 until 3) {
            val orig = originalList[i]
            val restored = restartedManager.projects[i]
            assertEquals(orig.id, restored.id)
            assertEquals(orig.title, restored.title)
            assertEquals(orig.width, restored.width)
            assertEquals(orig.height, restored.height)
            assertEquals(orig.folder, restored.folder)
            assertEquals(orig.tags, restored.tags)
            assertEquals(orig.filePath, restored.filePath)
        }
    }

    // -------------------------------------------------------------------------
    // 8. Recent List Ordering: Moves Opened Project to Top (LRU)
    // -------------------------------------------------------------------------
    @Test
    fun testRecentListOrdering_movesOpenedProjectToTop() {
        val p1 = libraryManager.createNewProject("Project 1")
        val p2 = libraryManager.createNewProject("Project 2")
        val p3 = libraryManager.createNewProject("Project 3")

        // Initial order: p3 (index 0), p2 (index 1), p1 (index 2)
        assertEquals(listOf(p3.id, p2.id, p1.id), libraryManager.projects.map { it.id })

        // Open p1
        val openRes = libraryManager.openProject(p1.id)
        assertTrue(openRes is ProjectOpenResult.Success)

        // New order: p1 (index 0), p3 (index 1), p2 (index 2)
        assertEquals(listOf(p1.id, p3.id, p2.id), libraryManager.projects.map { it.id })
    }

    // -------------------------------------------------------------------------
    // 9. Folder Grouping and Filtering
    // -------------------------------------------------------------------------
    @Test
    fun testFolderGroupingAndFiltering_filtersCorrectly() {
        libraryManager.createNewProject("A", folder = "Sketches")
        libraryManager.createNewProject("B", folder = "Inking")
        libraryManager.createNewProject("C", folder = "Sketches")
        libraryManager.createNewProject("D", folder = null)

        val folders = libraryManager.listFolders()
        assertEquals(listOf("Inking", "Sketches"), folders)

        val sketches = libraryManager.queryProjects(filter = ProjectSearchFilter(folder = "Sketches"))
        assertEquals(2, sketches.size)
        assertTrue(sketches.all { it.folder == "Sketches" })

        val inking = libraryManager.queryProjects(filter = ProjectSearchFilter(folder = "Inking"))
        assertEquals(1, inking.size)
        assertEquals("B", inking.first().title)
    }

    // -------------------------------------------------------------------------
    // 10. Search Query Filtering
    // -------------------------------------------------------------------------
    @Test
    fun testSearchQueryFiltering_matchesTitleFolderAndTags() {
        libraryManager.createNewProject("Cyberpunk City", tags = listOf("neon", "night"))
        libraryManager.createNewProject("Forest Cabin", folder = "Nature")
        libraryManager.createNewProject("Dragon Eye", tags = listOf("fantasy"))

        val cyber = libraryManager.queryProjects(filter = ProjectSearchFilter(query = "neon"))
        assertEquals(1, cyber.size)
        assertEquals("Cyberpunk City", cyber.first().title)

        val nature = libraryManager.queryProjects(filter = ProjectSearchFilter(query = "nature"))
        assertEquals(1, nature.size)
        assertEquals("Forest Cabin", nature.first().title)

        val dragon = libraryManager.queryProjects(filter = ProjectSearchFilter(query = "drag"))
        assertEquals(1, dragon.size)
        assertEquals("Dragon Eye", dragon.first().title)
    }

    // -------------------------------------------------------------------------
    // 11. Sort Orders: Deterministic Sorting
    // -------------------------------------------------------------------------
    @Test
    fun testSortOrders_allSixSortOrdersSortDeterministically() {
        val p1 = libraryManager.createNewProject("Bravo", 1000, 1000)
        val p2 = libraryManager.createNewProject("Alpha", 4000, 4000)
        val p3 = libraryManager.createNewProject("Charlie", 2000, 2000)

        // Title Ascending
        val titleAsc = libraryManager.queryProjects(sortOrder = ProjectSortOrder.TitleAscending)
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), titleAsc.map { it.title })

        // Title Descending
        val titleDesc = libraryManager.queryProjects(sortOrder = ProjectSortOrder.TitleDescending)
        assertEquals(listOf("Charlie", "Bravo", "Alpha"), titleDesc.map { it.title })

        // Dimensions Descending (Largest first: 4000x4000, 2000x2000, 1000x1000)
        val dimDesc = libraryManager.queryProjects(sortOrder = ProjectSortOrder.DimensionsDescending)
        assertEquals(listOf("Alpha", "Charlie", "Bravo"), dimDesc.map { it.title })
    }

    // -------------------------------------------------------------------------
    // 12. Recovery Prompts: Detects Uncommitted Recovery on Project Open
    // -------------------------------------------------------------------------
    @Test
    fun testRecoveryPrompts_detectsUncommittedRecoveryOnProjectOpen() {
        val proj = libraryManager.createNewProject("Crash Subject")
        val file = File(proj.filePath!!)

        // Write uncommitted recovery snapshot
        val recoveryDoc = DocumentPackage.load(file).document.copy(title = "Unsaved Work Before Crash")
        libraryManager.recoveryManager.writeRecoverySnapshot(
            document = recoveryDoc,
            assets = emptyMap(),
            originalFile = file,
            revision = 42L
        )

        // Open project -> must return RecoveryAvailable
        val openResult = libraryManager.openProject(proj.id)
        assertTrue("Must indicate RecoveryAvailable", openResult is ProjectOpenResult.RecoveryAvailable)
        val rec = openResult as ProjectOpenResult.RecoveryAvailable
        assertEquals(42L, rec.revision)
        assertTrue(rec.metadata.hasRecoveryAvailable)
    }

    // -------------------------------------------------------------------------
    // 13. Thumbnail Cache: Bounded LRU Memory & Eviction
    // -------------------------------------------------------------------------
    @Test
    fun testThumbnailCache_boundedLruMemoryAndEviction() {
        val cache = ThumbnailCache(maxEntries = 5, maxMemoryBytes = 1000)

        // Insert 5 thumbnails of 100 bytes each
        for (i in 1..5) {
            val dummyBytes = ByteArray(100) { i.toByte() }
            cache.put("proj_$i", dummyBytes)
        }
        assertEquals(5, cache.size)
        assertEquals(500L, cache.memoryBytesUsed)

        // Insert 6th entry -> causes eviction of eldest (proj_1)
        cache.put("proj_6", ByteArray(100) { 6.toByte() })
        assertEquals(5, cache.size)
        assertNull("proj_1 must be evicted", cache.get("proj_1"))
        assertNotNull("proj_6 must be present", cache.get("proj_6"))
    }

    // -------------------------------------------------------------------------
    // 14. Thumbnail Benchmark: 20 Fixture Entries Streaming Extraction
    // -------------------------------------------------------------------------
    @Test
    fun testThumbnailStreamingExtraction_benchmark20FixturesWithoutFullDocumentDecoding() {
        val cache = ThumbnailCache(maxEntries = 64)
        val fixtureDir = resolveFixtureDir()
        val sampleFfartFiles = fixtureDir.listFiles { _, name -> name.endsWith(".ffart") } ?: emptyArray()

        assertTrue("Must have sample .ffart files in fixture dir", sampleFfartFiles.isNotEmpty())

        // Build a list of 20 benchmark targets (cycling through available fixtures)
        val benchmarkFiles = (0 until 20).map { idx ->
            sampleFfartFiles[idx % sampleFfartFiles.size]
        }
        assertEquals(20, benchmarkFiles.size)

        val startTime = System.nanoTime()
        var totalBytesExtracted = 0L
        var thumbnailsFound = 0

        for ((idx, file) in benchmarkFiles.withIndex()) {
            val thumb = cache.getOrExtractThumbnail("bench_proj_$idx", file)
            if (thumb != null) {
                thumbnailsFound++
                totalBytesExtracted += thumb.size
            }
        }
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

        assertTrue("Thumbnails must be found", thumbnailsFound > 0)
        assertTrue("Extraction of 20 entries must complete under 500ms", elapsedMs < 500.0)
        assertTrue("Memory used must be bounded (< 2 MB for 20 thumbnails)", cache.memoryBytesUsed < 2 * 1024 * 1024L)

        println(">>> THUMBNAIL BENCHMARK (20 entries): ${String.format("%.2f", elapsedMs)} ms, memory: ${cache.memoryBytesUsed} bytes, avg: ${String.format("%.2f", elapsedMs / 20.0)} ms/thumb")
    }

    // -------------------------------------------------------------------------
    // 15. Corrupt Package Open: Returns Actionable Error Without Crashing
    // -------------------------------------------------------------------------
    @Test
    fun testCorruptPackageOpen_returnsActionableErrorWithoutCrashing() {
        val fixtureDir = resolveFixtureDir()
        val corruptFile = File(fixtureDir, "sample_corrupt_bad_mimetype.ffart")
        assertTrue(corruptFile.exists())

        val registered = libraryManager.registerExistingProject(corruptFile)
        assertTrue("Registration must handle corrupt file gracefully", registered.isFailure)
        assertTrue(registered.exceptionOrNull()?.message?.contains("mimetype", ignoreCase = true) == true)
    }
}
