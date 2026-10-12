package com.artstudio.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.artstudio.project.ProjectRepository

/**
 * Manages the navigation back stack, route arguments, Android back transitions,
 * unsaved-work safeguards, and state restoration.
 */
class StudioNavigationManager(
    val projectRepository: ProjectRepository,
    initialRoute: StudioNavRoute = StudioNavRoute.HomeProjects
) {
    private val backStack = mutableListOf<StudioNavRoute>()

    var currentRoute: StudioNavRoute by mutableStateOf(initialRoute)
        private set

    /**
     * Active unsaved work safeguard dialog flag.
     * Set when user triggers back while current project is dirty.
     */
    var showUnsavedDialog: Boolean by mutableStateOf(false)
        private set

    private var pendingNavigation: (() -> Unit)? = null

    init {
        backStack.add(initialRoute)
    }

    val canGoBack: Boolean
        get() = backStack.size > 1

    val backStackDepth: Int
        get() = backStack.size

    /**
     * Navigates forward to a new route.
     * If the current route is Studio and has unsaved work, guards navigation.
     */
    fun navigateTo(destination: StudioNavRoute) {
        if (destination == currentRoute) return

        if (isCurrentProjectDirty()) {
            pendingNavigation = { performNavigate(destination) }
            showUnsavedDialog = true
            return
        }

        performNavigate(destination)
    }

    private fun performNavigate(destination: StudioNavRoute) {
        if (destination == StudioNavRoute.HomeProjects) {
            // Popping back all the way to HomeProjects root
            backStack.clear()
            backStack.add(StudioNavRoute.HomeProjects)
            currentRoute = StudioNavRoute.HomeProjects
            return
        }

        if (isPrimaryTab(destination)) {
            // For primary tabs, always preserve HomeProjects as the back root
            if (backStack.size > 1 && isPrimaryTab(backStack.last()) && backStack.last() !is StudioNavRoute.ManualStudio) {
                backStack.removeAt(backStack.lastIndex)
            }
            backStack.add(destination)
            currentRoute = destination
            return
        }

        backStack.add(destination)
        currentRoute = destination
    }

    /**
     * Handles Android back behavior (system gesture or back button).
     * Intercepts back when unsaved work is present.
     * Returns true if back transition was handled, false if at root.
     */
    fun handleBackPress(): Boolean {
        if (showUnsavedDialog) {
            // Dismiss dialog on back
            showUnsavedDialog = false
            pendingNavigation = null
            return true
        }

        if (isCurrentProjectDirty()) {
            pendingNavigation = { performPopBack() }
            showUnsavedDialog = true
            return true
        }

        return performPopBack()
    }

    private fun performPopBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            currentRoute = backStack.last()
            return true
        }
        return false // At root (HomeProjects), allow system default (exit app)
    }

    /**
     * User resolves unsaved dialog by discarding changes.
     */
    fun confirmDiscardUnsavedWork() {
        showUnsavedDialog = false
        // Mark project clean
        val activeProj = projectRepository.activeProject
        if (activeProj != null) {
            projectRepository.markClean(activeProj.id)
        }
        val action = pendingNavigation
        pendingNavigation = null
        action?.invoke()
    }

    /**
     * User resolves unsaved dialog by saving changes.
     */
    fun confirmSaveAndProceed() {
        showUnsavedDialog = false
        val activeProj = projectRepository.activeProject
        if (activeProj != null) {
            projectRepository.markClean(activeProj.id)
        }
        val action = pendingNavigation
        pendingNavigation = null
        action?.invoke()
    }

    /**
     * User dismisses unsaved dialog to stay on the canvas.
     */
    fun cancelUnsavedDialog() {
        showUnsavedDialog = false
        pendingNavigation = null
    }

    /**
     * Saves the current navigation backstack to a list of tokens.
     */
    fun saveNavigationState(): List<String> {
        return backStack.map { it.serialize() }
    }

    /**
     * Restores safe navigation state from saved tokens.
     * Validates project existence for Studio routes.
     */
    fun restoreNavigationState(savedTokens: List<String>) {
        if (savedTokens.isEmpty()) return
        backStack.clear()
        for (token in savedTokens) {
            val route = StudioNavRoute.deserialize(token)
            if (route is StudioNavRoute.ManualStudio) {
                // Validate project exists
                val exists = projectRepository.projects.any { it.id == route.projectId }
                if (exists) {
                    backStack.add(route)
                    projectRepository.openProject(route.projectId)
                } else {
                    // Fallback safely to HomeProjects if project no longer exists
                    backStack.add(StudioNavRoute.HomeProjects)
                }
            } else {
                backStack.add(route)
            }
        }
        if (backStack.isEmpty()) {
            backStack.add(StudioNavRoute.HomeProjects)
        }
        currentRoute = backStack.last()
    }

    private fun isCurrentProjectDirty(): Boolean {
        if (currentRoute !is StudioNavRoute.ManualStudio) return false
        val activeProj = projectRepository.activeProject ?: return false
        return activeProj.isDirty
    }

    private fun isPrimaryTab(route: StudioNavRoute): Boolean {
        return route !is StudioNavRoute.ManualStudio &&
               route !is StudioNavRoute.Onboarding &&
               route !is StudioNavRoute.InAppManual
    }
}
