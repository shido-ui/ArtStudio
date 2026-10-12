package com.artstudio.project

import com.artstudio.ui.navigation.StudioNavRoute
import com.artstudio.ui.navigation.StudioNavigationManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Validates project workflows:
 * - Empty project state
 * - Project selection context
 * - Unsaved-work safeguards and dialog resolution
 * - Route arguments and safe state restoration
 */
class ProjectWorkflowAndSafeguardsTest {

    private lateinit var projectRepository: ProjectRepository
    private lateinit var navigationManager: StudioNavigationManager

    @Before
    fun setup() {
        projectRepository = ProjectRepository()
        navigationManager = StudioNavigationManager(
            projectRepository = projectRepository,
            initialRoute = StudioNavRoute.HomeProjects
        )
    }

    @Test
    fun testEmptyProjectStateInitialCondition() {
        assertTrue("Initial project list must be empty", projectRepository.projects.isEmpty())
        assertNull("Initial active project must be null", projectRepository.activeProject)
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
    }

    @Test
    fun testCreateNewProjectSetsSelectedContext() {
        val created = projectRepository.createNewProject("Manga Panel 01", 2048, 2048)

        assertEquals("Manga Panel 01", created.title)
        assertEquals(2048, created.width)
        assertEquals(2048, created.height)
        assertFalse("New canvas starts clean", created.isDirty)
        assertEquals(0, created.strokeCount)

        assertEquals(1, projectRepository.projects.size)
        assertEquals(created.id, projectRepository.activeProject?.id)
        assertEquals(created, projectRepository.projects.first())
    }

    @Test
    fun testOpenProjectSelectsAndReordersRecentList() {
        val projA = projectRepository.createNewProject("Project A", 1920, 1080)
        val projB = projectRepository.createNewProject("Project B", 2048, 2048)

        // Currently projB is most recent (index 0)
        assertEquals(projB.id, projectRepository.projects[0].id)
        assertEquals(projA.id, projectRepository.projects[1].id)

        // Opening projA moves it to top of recent projects
        val opened = projectRepository.openProject(projA.id)
        assertNotNull(opened)
        assertEquals(projA.id, opened?.id)
        assertEquals(projA.id, projectRepository.activeProject?.id)
        assertEquals(projA.id, projectRepository.projects[0].id)
        assertEquals(projB.id, projectRepository.projects[1].id)
    }

    @Test
    fun testDeleteProjectRemovesFromListAndClearsActive() {
        val proj = projectRepository.createNewProject("Temporary Sketch", 1080, 1080)
        assertEquals(1, projectRepository.projects.size)
        assertEquals(proj.id, projectRepository.activeProject?.id)

        val deleted = projectRepository.deleteProject(proj.id)
        assertTrue(deleted)
        assertTrue(projectRepository.projects.isEmpty())
        assertNull("Active project must be cleared after deletion", projectRepository.activeProject)
    }

    @Test
    fun testRecordStrokesMarksProjectDirtyUnsavedWork() {
        val proj = projectRepository.createNewProject("Inking Artwork", 2048, 2048)
        assertFalse(proj.isDirty)
        assertEquals(0, proj.strokeCount)

        projectRepository.recordStrokes(proj.id, 5)

        val active = projectRepository.activeProject
        assertNotNull(active)
        assertTrue("Project with new strokes must be marked dirty", active!!.isDirty)
        assertEquals(5, active.strokeCount)
    }

    @Test
    fun testUnsavedWorkSafeguardBlocksBackTransition() {
        val proj = projectRepository.createNewProject("Watercolour Study", 2048, 2048)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))

        // Record unsaved strokes
        projectRepository.recordStrokes(proj.id, 3)
        assertTrue(projectRepository.activeProject!!.isDirty)

        // Trigger back
        assertFalse(navigationManager.showUnsavedDialog)
        val handled = navigationManager.handleBackPress()

        assertTrue(handled)
        assertTrue("Safeguard dialog must be displayed when back is pressed with dirty work", navigationManager.showUnsavedDialog)
        // Must NOT have popped back yet
        assertTrue("Must remain on ManualStudio until dialog is resolved", navigationManager.currentRoute is StudioNavRoute.ManualStudio)
    }

    @Test
    fun testConfirmDiscardUnsavedWorkPopsAndCleans() {
        val proj = projectRepository.createNewProject("Concept Art", 2048, 2048)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))
        projectRepository.recordStrokes(proj.id, 4)

        navigationManager.handleBackPress()
        assertTrue(navigationManager.showUnsavedDialog)

        // Discard
        navigationManager.confirmDiscardUnsavedWork()

        assertFalse("Dialog must be dismissed", navigationManager.showUnsavedDialog)
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
        assertFalse("Project must be marked clean after discard", projectRepository.projects.first().isDirty)
    }

    @Test
    fun testConfirmSaveAndProceedPopsAndCleans() {
        val proj = projectRepository.createNewProject("Final Cel Shading", 3840, 2160)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))
        projectRepository.recordStrokes(proj.id, 8)

        navigationManager.handleBackPress()
        assertTrue(navigationManager.showUnsavedDialog)

        // Save & Exit
        navigationManager.confirmSaveAndProceed()

        assertFalse(navigationManager.showUnsavedDialog)
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
        assertFalse("Project must be marked clean after save", projectRepository.projects.first().isDirty)
    }

    @Test
    fun testCancelUnsavedDialogKeepsUserInStudio() {
        val proj = projectRepository.createNewProject("Oil Portrait", 2048, 2048)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))
        projectRepository.recordStrokes(proj.id, 2)

        navigationManager.handleBackPress()
        assertTrue(navigationManager.showUnsavedDialog)

        // User cancels modal to continue drawing
        navigationManager.cancelUnsavedDialog()

        assertFalse(navigationManager.showUnsavedDialog)
        assertTrue("Must remain in ManualStudio", navigationManager.currentRoute is StudioNavRoute.ManualStudio)
        assertTrue("Project must remain dirty", projectRepository.activeProject!!.isDirty)
    }

    @Test
    fun testNavigationStateSerializationAndRestoration() {
        val proj = projectRepository.createNewProject("Restoration Target", 2048, 2048)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))

        val savedTokens = navigationManager.saveNavigationState()
        assertTrue(savedTokens.size >= 2)
        assertEquals("home_projects", savedTokens[0])
        assertTrue(savedTokens[1].startsWith("manual_studio|${proj.id}"))

        // Create new manager and restore
        val newManager = StudioNavigationManager(projectRepository)
        newManager.restoreNavigationState(savedTokens)

        assertTrue(newManager.currentRoute is StudioNavRoute.ManualStudio)
        val restoredStudio = newManager.currentRoute as StudioNavRoute.ManualStudio
        assertEquals(proj.id, restoredStudio.projectId)
        assertEquals("Restoration Target", restoredStudio.projectTitle)
    }

    @Test
    fun testRouteRestorationSafelyFallsBackWhenProjectDeleted() {
        val nonExistentProjectId = "00000000-0000-0000-0000-000000000000"
        val savedTokens = listOf("home_projects", "manual_studio|$nonExistentProjectId|Ghost Project")

        val newManager = StudioNavigationManager(projectRepository)
        newManager.restoreNavigationState(savedTokens)

        // Deleted or missing project must safely fall back to HomeProjects without crashing
        assertEquals(StudioNavRoute.HomeProjects, newManager.currentRoute)
    }

    @Test
    fun testImportImageFromDocumentUriRetainsContext() {
        val testUri = "content://com.android.providers.media.documents/document/image%3A1024"
        val imported = projectRepository.importFromDocumentUri(
            uri = testUri,
            displayName = "comic_strip_page_01.png",
            width = 1920,
            height = 1080
        )

        assertEquals("comic_strip_page_01", imported.title)
        assertEquals(testUri, imported.fileUri)
        assertEquals(1920, imported.width)
        assertEquals(1080, imported.height)
        assertFalse(imported.isDirty)
        assertEquals(imported.id, projectRepository.activeProject?.id)
    }
}
