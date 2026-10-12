package com.artstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.artstudio.project.ProjectRepository
import com.artstudio.ui.components.StudioDialog
import com.artstudio.ui.navigation.*
import com.artstudio.ui.screens.*
import com.artstudio.ui.theme.ArtStudioTheme
import com.artstudio.ui.theme.StudioAccentOption
import com.artstudio.ui.theme.StudioTheme

import com.artstudio.project.ProjectLibraryManager
import java.io.File

class MainActivity : ComponentActivity() {
    private val projectRepository by lazy {
        val projectsDir = File(filesDir, "projects").apply { if (!exists()) mkdirs() }
        val libraryManager = ProjectLibraryManager(projectsDir)
        ProjectRepository(libraryManager = libraryManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.artstudio.core.ArtStudioCrashHandler.install(this)
        com.artstudio.platform.PlatformWindowAndInputIntegration.setupEdgeToEdgeWindow(this)
        setContent {
            ArtStudioApp(projectRepository = projectRepository)
        }
    }
}

@Composable
fun ArtStudioApp(
    projectRepository: ProjectRepository? = null,
    initialRoute: StudioNavRoute = StudioNavRoute.HomeProjects
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val effectiveRepo = projectRepository ?: remember {
        val projectsDir = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }
        val libraryManager = ProjectLibraryManager(projectsDir)
        ProjectRepository(libraryManager = libraryManager)
    }
    val preferences = remember { com.artstudio.ui.preferences.StudioPreferences(context) }
    val effectiveInitialRoute = remember {
        if (!preferences.hasCompletedOnboarding && initialRoute == StudioNavRoute.HomeProjects) {
            StudioNavRoute.Onboarding
        } else {
            initialRoute
        }
    }
    var isDarkTheme by remember { mutableStateOf(true) }
    var currentAccent by remember { mutableStateOf(StudioAccentOption.StudioBlue) }
    var reducedMotion by remember {
        mutableStateOf(
            try {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                ) == 0f
            } catch (_: Throwable) {
                false
            }
        )
    }

    val navigationManager = remember(effectiveRepo) {
        StudioNavigationManager(
            projectRepository = effectiveRepo,
            initialRoute = effectiveInitialRoute
        )
    }

    // Intercept Android hardware / gesture back navigation (with predictive back support)
    BackHandler(enabled = navigationManager.canGoBack || navigationManager.showUnsavedDialog) {
        navigationManager.handleBackPress()
    }

    ArtStudioTheme(
        darkTheme = isDarkTheme,
        accentOption = currentAccent,
        reducedMotion = reducedMotion
    ) {
        val configuration = LocalConfiguration.current
        val isTablet = configuration.screenWidthDp >= 600
        val currentRoute = navigationManager.currentRoute

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = StudioTheme.colors.surfaceBase
        ) {
            val destinationForBar = when (currentRoute) {
                is StudioNavRoute.Onboarding -> StudioDestination.HomeProjects
                is StudioNavRoute.HomeProjects -> StudioDestination.HomeProjects
                is StudioNavRoute.ManualStudio -> StudioDestination.ManualStudio
                is StudioNavRoute.InAppManual -> StudioDestination.Settings
                is StudioNavRoute.AutoDraw -> StudioDestination.AutoDraw
                is StudioNavRoute.ColourLab -> StudioDestination.ColourLab
                is StudioNavRoute.AssetsBrushes -> StudioDestination.AssetsBrushes
                is StudioNavRoute.Settings -> StudioDestination.Settings
                is StudioNavRoute.ComponentReference -> StudioDestination.ComponentReference
                is StudioNavRoute.InputDiagnostics -> StudioDestination.Settings
                is StudioNavRoute.ProLibrary -> StudioDestination.Settings
            }

            fun onBarNavigate(dest: StudioDestination) {
                val targetRoute = when (dest) {
                    StudioDestination.HomeProjects -> StudioNavRoute.HomeProjects
                    StudioDestination.ManualStudio -> {
                        val active = effectiveRepo.activeProject
                        if (active != null) {
                            StudioNavRoute.ManualStudio(active.id, active.title)
                        } else {
                            val newProj = effectiveRepo.createNewProject("Untitled Studio Canvas")
                            StudioNavRoute.ManualStudio(newProj.id, newProj.title)
                        }
                    }
                    StudioDestination.AutoDraw -> StudioNavRoute.AutoDraw
                    StudioDestination.ColourLab -> StudioNavRoute.ColourLab
                    StudioDestination.AssetsBrushes -> StudioNavRoute.AssetsBrushes
                    StudioDestination.Settings -> StudioNavRoute.Settings
                    StudioDestination.ComponentReference -> StudioNavRoute.ComponentReference
                }
                navigationManager.navigateTo(targetRoute)
            }

            if (isTablet) {
                // Tablet layout: Navigation rail on left
                Row(modifier = Modifier.fillMaxSize()) {
                    StudioTabletNavigationRail(
                        currentDestination = destinationForBar,
                        onNavigate = { onBarNavigate(it) }
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        StudioRouteHost(
                            currentRoute = currentRoute,
                            projectRepository = effectiveRepo,
                            navigationManager = navigationManager,
                            preferences = preferences,
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = { isDarkTheme = it },
                            currentAccent = currentAccent,
                            onSelectAccent = { currentAccent = it },
                            reducedMotion = reducedMotion,
                            onToggleReducedMotion = { reducedMotion = it }
                        )
                    }
                }
            } else {
                // Phone layout: Canvas-dominant with compact bottom bar
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        StudioRouteHost(
                            currentRoute = currentRoute,
                            projectRepository = effectiveRepo,
                            navigationManager = navigationManager,
                            preferences = preferences,
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = { isDarkTheme = it },
                            currentAccent = currentAccent,
                            onSelectAccent = { currentAccent = it },
                            reducedMotion = reducedMotion,
                            onToggleReducedMotion = { reducedMotion = it }
                        )
                    }
                    // Hide bottom bar in ManualStudio on phones to keep canvas completely dominant
                    if (currentRoute !is StudioNavRoute.ManualStudio && currentRoute !is StudioNavRoute.Onboarding) {
                        StudioPhoneBottomBar(
                            currentDestination = destinationForBar,
                            onNavigate = { onBarNavigate(it) }
                        )
                    }
                }
            }

            // Unsaved Work Warning Dialog Safeguard
            if (navigationManager.showUnsavedDialog) {
                val dirtyTitle = effectiveRepo.activeProject?.title ?: "Artwork"
                StudioDialog(
                    title = "Unsaved Changes",
                    body = "You have unsaved strokes in '$dirtyTitle'. Would you like to save before exiting?",
                    confirmText = "Save & Exit",
                    cancelText = "Discard",
                    isDestructive = false,
                    onConfirm = { navigationManager.confirmSaveAndProceed() },
                    onDismiss = { navigationManager.confirmDiscardUnsavedWork() }
                )
            }
        }
    }
}

@Composable
private fun StudioRouteHost(
    currentRoute: StudioNavRoute,
    projectRepository: ProjectRepository,
    navigationManager: StudioNavigationManager,
    preferences: com.artstudio.ui.preferences.StudioPreferences,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    currentAccent: StudioAccentOption,
    onSelectAccent: (StudioAccentOption) -> Unit,
    reducedMotion: Boolean,
    onToggleReducedMotion: (Boolean) -> Unit
) {
    when (currentRoute) {
        is StudioNavRoute.Onboarding -> com.artstudio.onboarding.OnboardingFlow(
            preferences = preferences,
            onStartJustDraw = {
                val newProj = projectRepository.createNewProject("Untitled Studio Canvas")
                navigationManager.navigateTo(StudioNavRoute.ManualStudio(newProj.id, newProj.title))
            },
            onStartTutorial = {
                val newProj = projectRepository.createNewProject("Tutorial Canvas")
                navigationManager.navigateTo(StudioNavRoute.ManualStudio(newProj.id, newProj.title))
            },
            onStartFullPower = {
                navigationManager.navigateTo(StudioNavRoute.HomeProjects)
            }
        )
        is StudioNavRoute.InAppManual -> com.artstudio.manual.InAppManualScreen(
            initialChapterId = currentRoute.chapterId,
            onNavigateBack = { navigationManager.handleBackPress() },
            onTryItNow = { _ ->
                navigationManager.handleBackPress()
            }
        )
        is StudioNavRoute.HomeProjects -> ProjectsScreen(
            projectRepository = projectRepository,
            onOpenStudio = { id, title, isReadOnlySample ->
                navigationManager.navigateTo(StudioNavRoute.ManualStudio(id, title, isReadOnlySample))
            }
        )
        is StudioNavRoute.ManualStudio -> ManualStudioScreen(
            projectId = currentRoute.projectId,
            projectTitle = currentRoute.projectTitle,
            projectRepository = projectRepository,
            onNavigateBack = { navigationManager.handleBackPress() },
            isReadOnlySample = currentRoute.isReadOnlySample,
            preferences = preferences,
            onOpenHelp = { chapterId ->
                navigationManager.navigateTo(StudioNavRoute.InAppManual(chapterId))
            }
        )
        is StudioNavRoute.AutoDraw -> AutoDrawScreen()
        is StudioNavRoute.ColourLab -> ColourLabScreen()
        is StudioNavRoute.AssetsBrushes -> AssetsBrushesScreen()
        is StudioNavRoute.Settings -> SettingsScreen(
            isDarkTheme = isDarkTheme,
            onToggleDarkTheme = onToggleDarkTheme,
            currentAccent = currentAccent,
            onSelectAccent = onSelectAccent,
            reducedMotion = reducedMotion,
            onToggleReducedMotion = onToggleReducedMotion,
            onOpenComponentReference = {
                navigationManager.navigateTo(StudioNavRoute.ComponentReference)
            },
            onOpenInputDiagnostics = {
                navigationManager.navigateTo(StudioNavRoute.InputDiagnostics)
            },
            onOpenProLibrary = {
                navigationManager.navigateTo(StudioNavRoute.ProLibrary)
            },
            onOpenManual = {
                navigationManager.navigateTo(StudioNavRoute.InAppManual(null))
            },
            preferences = preferences
        )
        is StudioNavRoute.ComponentReference -> ComponentReferenceScreen(
            isDarkTheme = isDarkTheme,
            onToggleDarkTheme = onToggleDarkTheme,
            currentAccent = currentAccent,
            onSelectAccent = onSelectAccent,
            reducedMotion = reducedMotion,
            onToggleReducedMotion = onToggleReducedMotion
        )
        is StudioNavRoute.InputDiagnostics -> InputDiagnosticsScreen(
            onNavigateBack = { navigationManager.handleBackPress() }
        )
        is StudioNavRoute.ProLibrary -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            val dm = remember {
                com.artstudio.assets.download.AssetDownloadManager(context.filesDir)
            }
            val brushRepo = remember { com.artstudio.brush.BrushPresetRepository(File(context.filesDir, "brushes")) }
            val texRepo = remember { com.artstudio.texture.TextureRepository(File(context.filesDir, "textures")) }
            val comicRepo = remember { com.artstudio.comic.ComicAssetRepository(File(context.filesDir, "comic")) }
            ProLibraryScreen(
                downloadManager = dm,
                onNavigateBack = { navigationManager.handleBackPress() },
                brushRepo = brushRepo,
                textureRepo = texRepo,
                comicRepo = comicRepo
            )
        }
    }
}
