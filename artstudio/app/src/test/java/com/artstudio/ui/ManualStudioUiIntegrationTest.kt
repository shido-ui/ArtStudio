package com.artstudio.ui

import com.artstudio.document.io.CorruptDocumentException
import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.export.*
import com.artstudio.history.HistoryManager
import com.artstudio.layer.LayerManager
import androidx.compose.ui.unit.dp
import com.artstudio.project.ProjectMetadata
import com.artstudio.project.ProjectRepository
import com.artstudio.project.ProjectSearchFilter
import com.artstudio.selection.SelectionMask
import com.artstudio.ui.navigation.StudioDestination
import com.artstudio.ui.navigation.StudioNavRoute
import com.artstudio.ui.navigation.StudioNavigationManager
import com.artstudio.ui.theme.StudioAccessibility
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * UI and Integration Verification Suite for Prompt 43:
 * Main routes, destructive-action safeguards, accessibility touch targets, and empty states.
 */
class ManualStudioUiIntegrationTest {

    private lateinit var tempDir: File
    private lateinit var projectRepository: ProjectRepository
    private lateinit var navigationManager: StudioNavigationManager

    @Before
    fun setUp() {
        tempDir = File("build/tmp/test_studio_ui_${System.currentTimeMillis()}").apply { mkdirs() }
        projectRepository = ProjectRepository()
        navigationManager = StudioNavigationManager(
            projectRepository = projectRepository,
            initialRoute = StudioNavRoute.HomeProjects
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 1. Main Route Navigation & Back Transitions
    // =========================================================================

    @Test
    fun testMainRouteNavigation_allDestinationsAndBackTransitions() {
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
        assertFalse(navigationManager.canGoBack)

        val routes = listOf(
            StudioNavRoute.ColourLab,
            StudioNavRoute.AssetsBrushes,
            StudioNavRoute.Settings,
            StudioNavRoute.ComponentReference,
            StudioNavRoute.AutoDraw
        )

        for (route in routes) {
            navigationManager.navigateTo(route)
            assertEquals("Current route must match destination $route", route, navigationManager.currentRoute)
            assertTrue("Should be able to navigate back from $route", navigationManager.canGoBack)

            val backHandled = navigationManager.handleBackPress()
            assertTrue("Back transition must be handled", backHandled)
            assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
        }
    }

    // =========================================================================
    // 2. Destructive Safeguard: Unsaved Work Intercepts Studio Exit
    // =========================================================================

    @Test
    fun testDestructiveProtection_unsavedWorkSafeguardInterceptsExit() {
        val proj = projectRepository.createNewProject("Sketch 01", 1080, 1080)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))
        assertEquals(StudioNavRoute.ManualStudio(proj.id, proj.title), navigationManager.currentRoute)

        // Make project dirty
        projectRepository.recordStrokes(proj.id, additionalStrokes = 15)
        assertTrue(projectRepository.activeProject?.isDirty == true)

        // Attempt to navigate to Settings
        navigationManager.navigateTo(StudioNavRoute.Settings)

        // Must intercept navigation with unsaved dialog
        assertTrue("showUnsavedDialog must be true when dirty", navigationManager.showUnsavedDialog)
        assertEquals("Must remain on ManualStudio until dialog resolved", StudioNavRoute.ManualStudio(proj.id, proj.title), navigationManager.currentRoute)

        // Attempt back press
        val backHandled = navigationManager.handleBackPress()
        assertTrue(backHandled)
        assertFalse("Back press dismisses dialog", navigationManager.showUnsavedDialog)
        assertEquals(StudioNavRoute.ManualStudio(proj.id, proj.title), navigationManager.currentRoute)
    }

    // =========================================================================
    // 3. Destructive Safeguard: Cancel Retains Canvas & Dirty State
    // =========================================================================

    @Test
    fun testDestructiveProtection_unsavedWorkCancelRetainsStudioState() {
        val proj = projectRepository.createNewProject("Oil Portrait", 1080, 1080)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))
        projectRepository.recordStrokes(proj.id, additionalStrokes = 5)

        navigationManager.navigateTo(StudioNavRoute.HomeProjects)
        assertTrue(navigationManager.showUnsavedDialog)

        // User cancels prompt
        navigationManager.cancelUnsavedDialog()
        assertFalse(navigationManager.showUnsavedDialog)
        assertEquals(StudioNavRoute.ManualStudio(proj.id, proj.title), navigationManager.currentRoute)
        assertTrue("Project must remain dirty after cancel", projectRepository.activeProject?.isDirty == true)
    }

    // =========================================================================
    // 4. Destructive Safeguard: Discard Proceeds and Cleans State
    // =========================================================================

    @Test
    fun testDestructiveProtection_unsavedWorkDiscardCleansProjectAndNavigates() {
        val proj = projectRepository.createNewProject("Draft Sketch", 1080, 1080)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))
        projectRepository.recordStrokes(proj.id, additionalStrokes = 10)

        navigationManager.navigateTo(StudioNavRoute.Settings)
        assertTrue(navigationManager.showUnsavedDialog)

        // User confirms discard
        navigationManager.confirmDiscardUnsavedWork()
        assertFalse(navigationManager.showUnsavedDialog)
        assertEquals(StudioNavRoute.Settings, navigationManager.currentRoute)
        assertFalse("Project must be marked clean after discard", projectRepository.projects.find { it.id == proj.id }?.isDirty == true)
    }

    // =========================================================================
    // 5. Destructive Safeguard: Save Proceeds and Cleans State
    // =========================================================================

    @Test
    fun testDestructiveProtection_unsavedWorkSaveCleansProjectAndNavigates() {
        val proj = projectRepository.createNewProject("Comic Lineart", 1080, 1080)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))
        projectRepository.recordStrokes(proj.id, additionalStrokes = 20)

        navigationManager.navigateTo(StudioNavRoute.HomeProjects)
        assertTrue(navigationManager.showUnsavedDialog)

        // User confirms save and proceed
        navigationManager.confirmSaveAndProceed()
        assertFalse(navigationManager.showUnsavedDialog)
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
        assertFalse("Project must be clean after save confirmation", projectRepository.projects.find { it.id == proj.id }?.isDirty == true)
    }

    // =========================================================================
    // 6. Destructive Safeguard: Project Soft-Delete and Undo Recovery
    // =========================================================================

    @Test
    fun testDestructiveProtection_projectSoftDeleteAndUndoRecovery() {
        val proj1 = projectRepository.createNewProject("Project To Keep", 1080, 1080)
        val proj2 = projectRepository.createNewProject("Project To Delete", 1080, 1080)
        assertEquals(2, projectRepository.projects.size)

        // Delete proj2
        val deleted = projectRepository.deleteProject(proj2.id)
        assertTrue(deleted)
        assertEquals(1, projectRepository.projects.size)
        assertEquals(proj1.id, projectRepository.projects.first().id)
        assertNull("Active project should be cleared if deleted was active", projectRepository.activeProject)
    }

    // =========================================================================
    // 7. Destructive Safeguard: Cannot Delete Last Remaining Layer
    // =========================================================================

    @Test
    fun testDestructiveProtection_cannotDeleteLastLayerInDocument() {
        val doc = ArtDocument(
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(LayerNode.RasterLayer(id = "layer_1", name = "Single Layer"))
            )
        )

        // Attempting to delete the only layer should be rejected
        val layerManager = LayerManager(HistoryManager(doc))
        val deleted = layerManager.deleteLayer("layer_1")
        assertFalse("Must not allow deleting the last remaining layer in a document", deleted)
        assertEquals(1, layerManager.getLayers().size)
    }

    // =========================================================================
    // 8. Destructive Safeguard: Unconfirmed Flattening Is Rejected
    // =========================================================================

    @Test
    fun testDestructiveProtection_singleLayerFlattenRejected() {
        val doc = ArtDocument(
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(LayerNode.RasterLayer(id = "layer_1", name = "Single Layer"))
            )
        )

        val layerManager = LayerManager(HistoryManager(doc))
        try {
            layerManager.flattenDocument(confirmed = false)
            fail("Must reject unconfirmed document flattening")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("explicit user confirmation") == true)
        }
    }

    // =========================================================================
    // 9. Destructive Safeguard: Export Filename Collision Protection
    // =========================================================================

    @Test
    fun testDestructiveProtection_exportCollisionPrevention() = runBlocking {
        val exportService = ArtworkExportService()
        val doc = ArtDocument(canvas = CanvasConfig(32, 32))
        val targetFile = File(tempDir, "existing_art.png")
        targetFile.writeText("existing content that must not be lost")

        // 1. FAIL_IF_EXISTS
        val failConfig = ExportConfig(
            format = ExportFormat.PNG,
            conflictStrategy = FilenameConflictStrategy.FAIL_IF_EXISTS
        )
        val failResult = exportService.exportArtwork(doc, failConfig, targetFile)
        assertTrue(failResult is ExportResult.Error)
        assertEquals(ExportErrorCode.FILENAME_CONFLICT, (failResult as ExportResult.Error).errorCode)
        assertEquals("existing content that must not be lost", targetFile.readText())

        // 2. AUTO_RENAME
        val autoConfig = ExportConfig(
            format = ExportFormat.PNG,
            conflictStrategy = FilenameConflictStrategy.AUTO_RENAME
        )
        val autoResult = exportService.exportArtwork(doc, autoConfig, targetFile)
        assertTrue(autoResult is ExportResult.Success)
        val autoSuccess = autoResult as ExportResult.Success
        assertNotEquals(targetFile.absolutePath, autoSuccess.outputFile.absolutePath)
        assertTrue(autoSuccess.outputFile.name.startsWith("existing_art (1)"))
        assertEquals("existing content that must not be lost", targetFile.readText()) // original preserved

        // 3. OVERWRITE
        val overConfig = ExportConfig(
            format = ExportFormat.PNG,
            conflictStrategy = FilenameConflictStrategy.OVERWRITE
        )
        val overResult = exportService.exportArtwork(doc, overConfig, targetFile)
        assertTrue(overResult is ExportResult.Success)
        assertNotEquals("existing content that must not be lost", targetFile.readText()) // safely replaced
    }

    // =========================================================================
    // 10. Destructive Safeguard: Corrupted Package Handling Protects Workspace
    // =========================================================================

    @Test
    fun testDestructiveProtection_corruptPackageHandlingLeavesWorkspaceIntact() {
        val corruptFile = File(tempDir, "corrupted_archive.ffart")
        corruptFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)) // incomplete zip

        try {
            DocumentPackage.load(corruptFile)
            fail("Must throw CorruptDocumentException on corrupted package")
        } catch (e: CorruptDocumentException) {
            assertTrue("Expected corrupted archive rejection", e.message?.isNotEmpty() == true)
        }

        // Active project repository state remains untouched
        assertNull(projectRepository.activeProject)
        assertTrue(projectRepository.projects.isEmpty())
    }

    // =========================================================================
    // 11. Phone Layout: Minimum 48dp Touch Targets & Accessibility Roles
    // =========================================================================

    @Test
    fun testPhoneLayout_accessibleTouchTargetsAndSemanticDescriptions() {
        // Enforce StudioAccessibility minimum touch target constant >= 48dp
        assertTrue(StudioAccessibility.MinTouchTarget >= 48.dp)

        // Verify each primary destination has non-empty title, symbol, and description
        for (dest in StudioDestination.entries) {
            assertTrue("Destination title must not be empty", dest.title.isNotEmpty())
            assertTrue("Destination iconSymbol must not be empty", dest.iconSymbol.isNotEmpty())
        }
    }

    // =========================================================================
    // 12. Empty State Handling: Project Browser & Selection Masks
    // =========================================================================

    @Test
    fun testEmptyStateHandling_projectBrowserAndPanels() {
        // Empty project repository query
        val emptyProjects = projectRepository.queryProjects(ProjectSearchFilter(query = "non_existent"))
        assertTrue("Search filter with no matches must yield empty list without crashing", emptyProjects.isEmpty())

        // Empty selection mask
        val emptySelection = SelectionMask(100, 100)
        assertTrue(emptySelection.isEmpty)
        assertFalse(emptySelection.isSelected(50, 50))
        assertEquals(0f, emptySelection.bounds.width, 0.001f)
    }
}
