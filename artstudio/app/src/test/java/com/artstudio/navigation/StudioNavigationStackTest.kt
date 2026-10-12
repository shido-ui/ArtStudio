package com.artstudio.navigation

import com.artstudio.project.ProjectRepository
import com.artstudio.ui.navigation.StudioNavRoute
import com.artstudio.ui.navigation.StudioNavigationManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Validates navigation across every primary destination and all back transitions.
 */
class StudioNavigationStackTest {

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
    fun testInitialRouteIsHomeProjects() {
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
        assertEquals(1, navigationManager.backStackDepth)
        assertFalse("At root, cannot go back", navigationManager.canGoBack)
    }

    @Test
    fun testNavigateToEveryPrimaryDestination() {
        val primaryDestinations = listOf(
            StudioNavRoute.AutoDraw,
            StudioNavRoute.ColourLab,
            StudioNavRoute.AssetsBrushes,
            StudioNavRoute.Settings,
            StudioNavRoute.ComponentReference,
            StudioNavRoute.HomeProjects
        )

        for (dest in primaryDestinations) {
            navigationManager.navigateTo(dest)
            assertEquals("Current route must be $dest", dest, navigationManager.currentRoute)
        }
    }

    @Test
    fun testBackTransitionPopsBackToPreviousDestination() {
        navigationManager.navigateTo(StudioNavRoute.Settings)
        assertEquals(StudioNavRoute.Settings, navigationManager.currentRoute)
        assertTrue(navigationManager.canGoBack)

        val handled = navigationManager.handleBackPress()
        assertTrue("Back transition must be handled", handled)
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
        assertFalse(navigationManager.canGoBack)
    }

    @Test
    fun testBackTransitionAtRootReturnsFalse() {
        assertFalse(navigationManager.canGoBack)
        val handled = navigationManager.handleBackPress()
        assertFalse("At root, back press must return false to let system handle it", handled)
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
    }

    @Test
    fun testNavigateToManualStudioWithRouteArguments() {
        val proj = projectRepository.createNewProject("Test Landscape", 1920, 1080)
        val studioRoute = StudioNavRoute.ManualStudio(proj.id, proj.title)

        navigationManager.navigateTo(studioRoute)

        val current = navigationManager.currentRoute
        assertTrue(current is StudioNavRoute.ManualStudio)
        val studio = current as StudioNavRoute.ManualStudio
        assertEquals(proj.id, studio.projectId)
        assertEquals("Test Landscape", studio.projectTitle)
        assertEquals(proj.id, projectRepository.activeProject?.id)
    }

    @Test
    fun testBackFromManualStudioReturnsToHome() {
        val proj = projectRepository.createNewProject("Portrait Sketch", 1080, 2400)
        navigationManager.navigateTo(StudioNavRoute.ManualStudio(proj.id, proj.title))

        assertTrue(navigationManager.canGoBack)
        val handled = navigationManager.handleBackPress()
        assertTrue(handled)
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
    }

    @Test
    fun testDeepBackStackNavigationAndPopSequence() {
        // Home -> AutoDraw -> ColourLab -> Settings
        navigationManager.navigateTo(StudioNavRoute.AutoDraw)
        navigationManager.navigateTo(StudioNavRoute.ColourLab)
        navigationManager.navigateTo(StudioNavRoute.Settings)

        assertEquals(StudioNavRoute.Settings, navigationManager.currentRoute)

        assertTrue(navigationManager.handleBackPress())
        assertEquals(StudioNavRoute.HomeProjects, navigationManager.currentRoute)
    }
}
