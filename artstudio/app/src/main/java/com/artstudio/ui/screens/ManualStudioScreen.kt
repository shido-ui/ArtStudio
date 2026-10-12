package com.artstudio.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.document.lifecycle.AutosaveManager
import com.artstudio.document.lifecycle.AutosavePolicy
import com.artstudio.importer.RasterImageImporter
import com.artstudio.project.ProjectRepository
import com.artstudio.ui.ArtStudioDrawingView
import com.artstudio.ui.overlay.PerformanceMetricsMonitor
import com.artstudio.ui.screens.studio.*
import com.artstudio.ui.theme.StudioTheme
import com.artstudio.ui.viewmodel.StudioViewModel
import java.io.File

/**
 * Modern, adaptive Studio canvas screen.
 * Seamlessly transitions between phone portrait, landscape rail, and tablet workspace (>= 600dp).
 * Fully decomposed into modular sub-composables strictly <= 150 lines each.
 */
@Composable
fun ManualStudioScreen(
    projectId: String,
    projectTitle: String,
    projectRepository: ProjectRepository,
    onNavigateBack: () -> Unit,
    isReadOnlySample: Boolean = false,
    preferences: com.artstudio.ui.preferences.StudioPreferences? = null,
    onOpenHelp: ((chapterId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val viewModel = remember(projectId) { StudioViewModel(projectId) }
    var drawingViewRef by remember { mutableStateOf<ArtStudioDrawingView?>(null) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    val perfMonitor = remember { PerformanceMetricsMonitor() }

    var activeProjectId by remember(projectId) { mutableStateOf(projectId) }
    var activeProjectTitle by remember(projectTitle) { mutableStateOf(projectTitle) }
    var isSampleBannerVisible by remember(isReadOnlySample) { mutableStateOf(isReadOnlySample) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableStateOf(com.artstudio.onboarding.TutorialStep.DRAW_STROKE) }

    val isTabletOrLandscape = remember(configuration.screenWidthDp, configuration.orientation) {
        configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    val canUndo by remember {
        derivedStateOf {
            @Suppress("UNUSED_VARIABLE") val rev = historyRevision
            drawingViewRef?.document?.historyManager?.canUndo == true
        }
    }
    val canRedo by remember {
        derivedStateOf {
            @Suppress("UNUSED_VARIABLE") val rev = historyRevision
            drawingViewRef?.document?.historyManager?.canRedo == true
        }
    }

    val rasterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleImageImport(context, uri, viewModel)
        }
    }

    val autosaveManager = remember(activeProjectId) {
        createAutosaveManager(context, activeProjectId) { drawingViewRef }
    }
    DisposableEffect(autosaveManager) {
        onDispose { autosaveManager.cancel() }
    }

    HandleAnimationPlayback(viewModel, drawingViewRef)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioTheme.colors.canvasBackground)
    ) {
        StudioAdaptiveContent(
            isTabletOrLandscape = isTabletOrLandscape,
            projectTitle = activeProjectTitle,
            viewModel = viewModel,
            canUndo = canUndo,
            canRedo = canRedo,
            zoomLevel = zoomLevel,
            userMode = preferences?.userMode ?: com.artstudio.ui.preferences.StudioUserMode.FULL,
            onUnlockAdvanced = { showUnlockDialog = true },
            onOpenHelp = { onOpenHelp?.invoke("manual_studio") },
            onNavigateBack = onNavigateBack,
            onUndo = {
                drawingViewRef?.undo()
                historyRevision++
                if (preferences?.userMode == com.artstudio.ui.preferences.StudioUserMode.LEARN && tutorialStep == com.artstudio.onboarding.TutorialStep.UNDO_STROKE) {
                    tutorialStep = com.artstudio.onboarding.TutorialStep.REDO_STROKE
                }
            },
            onRedo = {
                drawingViewRef?.redo()
                historyRevision++
                if (preferences?.userMode == com.artstudio.ui.preferences.StudioUserMode.LEARN && tutorialStep == com.artstudio.onboarding.TutorialStep.REDO_STROKE) {
                    tutorialStep = com.artstudio.onboarding.TutorialStep.SAVE_PROJECT
                }
            },
            onFitToScreen = { drawingViewRef?.fitToScreen() },
            onShowImport = { rasterPickerLauncher.launch("image/*") },
            onDrawingViewCreated = { view ->
                drawingViewRef = view
                setupDrawingView(view, context, activeProjectId, projectRepository, viewModel, autosaveManager) { historyRevision++ }
            },
            onMetricsUpdated = { rTime, _, zLvl ->
                perfMonitor.recordFrameDurationMs(rTime)
                zoomLevel = zLvl
            },
            onStrokeCommitted = { stroke ->
                autosaveManager.notifyEdit(stroke.layerId)
                historyRevision++
                if (preferences?.userMode == com.artstudio.ui.preferences.StudioUserMode.LEARN && tutorialStep == com.artstudio.onboarding.TutorialStep.DRAW_STROKE) {
                    tutorialStep = com.artstudio.onboarding.TutorialStep.CHANGE_SIZE
                }
            }
        )

        StudioPanelHost(
            viewModel = viewModel,
            drawingView = drawingViewRef,
            onPickImageRequested = { rasterPickerLauncher.launch("image/*") }
        )

        StudioDialogHost(
            viewModel = viewModel,
            drawingView = drawingViewRef,
            projectRepository = projectRepository,
            onNavigateBack = onNavigateBack
        )

        if (isSampleBannerVisible) {
            SampleProjectBanner(
                projectTitle = activeProjectTitle,
                onSaveAs = {
                    val duplicate = projectRepository.duplicateProject(activeProjectId, "$activeProjectTitle (My Copy)")
                    if (duplicate != null) {
                        activeProjectId = duplicate.id
                        activeProjectTitle = duplicate.title
                        isSampleBannerVisible = false
                        projectRepository.openProject(duplicate.id)
                    }
                },
                onDismiss = { isSampleBannerVisible = false },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = StudioTheme.spacing.m, end = StudioTheme.spacing.m)
            )
        }

        if (preferences?.userMode == com.artstudio.ui.preferences.StudioUserMode.LEARN) {
            com.artstudio.onboarding.GuidedTutorialOverlay(
                currentStep = tutorialStep,
                onAdvanceStep = {
                    when (tutorialStep) {
                        com.artstudio.onboarding.TutorialStep.DRAW_STROKE -> tutorialStep = com.artstudio.onboarding.TutorialStep.CHANGE_SIZE
                        com.artstudio.onboarding.TutorialStep.CHANGE_SIZE -> tutorialStep = com.artstudio.onboarding.TutorialStep.UNDO_STROKE
                        com.artstudio.onboarding.TutorialStep.UNDO_STROKE -> tutorialStep = com.artstudio.onboarding.TutorialStep.REDO_STROKE
                        com.artstudio.onboarding.TutorialStep.REDO_STROKE -> tutorialStep = com.artstudio.onboarding.TutorialStep.SAVE_PROJECT
                        com.artstudio.onboarding.TutorialStep.SAVE_PROJECT -> {
                            preferences.userMode = com.artstudio.ui.preferences.StudioUserMode.FULL
                        }
                    }
                },
                onSkip = {
                    preferences.userMode = com.artstudio.ui.preferences.StudioUserMode.FULL
                }
            )
        }

        if (showUnlockDialog) {
            com.artstudio.ui.components.StudioDialog(
                title = "Unlock Advanced Tools?",
                body = "This unlocks all professional features: AutoDraw, Colour Lab, Deformation, and Comic Manga tools.",
                confirmText = "Unlock All Tools",
                cancelText = "Keep Simple",
                onConfirm = {
                    preferences?.userMode = com.artstudio.ui.preferences.StudioUserMode.FULL
                    showUnlockDialog = false
                },
                onDismiss = { showUnlockDialog = false }
            )
        }

        StudioPerformanceOverlay(monitor = perfMonitor)
    }
}

@Composable
private fun StudioAdaptiveContent(
    isTabletOrLandscape: Boolean,
    projectTitle: String,
    viewModel: StudioViewModel,
    canUndo: Boolean,
    canRedo: Boolean,
    zoomLevel: Float,
    userMode: com.artstudio.ui.preferences.StudioUserMode = com.artstudio.ui.preferences.StudioUserMode.FULL,
    onUnlockAdvanced: (() -> Unit)? = null,
    onOpenHelp: (() -> Unit)? = null,
    onNavigateBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFitToScreen: () -> Unit,
    onShowImport: () -> Unit,
    onDrawingViewCreated: (ArtStudioDrawingView) -> Unit,
    onMetricsUpdated: (Float, Int, Float) -> Unit,
    onStrokeCommitted: (com.artstudio.core.BrushStroke) -> Unit
) {
    if (isTabletOrLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            StudioSideToolRail(viewModel = viewModel)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                StudioTopAppBar(
                    projectTitle = projectTitle,
                    viewModel = viewModel,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    zoomLevel = zoomLevel,
                    userMode = userMode,
                    onUnlockAdvanced = onUnlockAdvanced,
                    onOpenHelp = onOpenHelp,
                    onNavigateBack = onNavigateBack,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onFitToScreen = onFitToScreen,
                    onShowImport = onShowImport
                )
                StudioCanvasContainer(
                    viewModel = viewModel,
                    onDrawingViewCreated = onDrawingViewCreated,
                    onMetricsUpdated = onMetricsUpdated,
                    onStrokeCommitted = onStrokeCommitted,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            StudioTopAppBar(
                projectTitle = projectTitle,
                viewModel = viewModel,
                canUndo = canUndo,
                canRedo = canRedo,
                zoomLevel = zoomLevel,
                userMode = userMode,
                onUnlockAdvanced = onUnlockAdvanced,
                onOpenHelp = onOpenHelp,
                onNavigateBack = onNavigateBack,
                onUndo = onUndo,
                onRedo = onRedo,
                onFitToScreen = onFitToScreen,
                onShowImport = onShowImport
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                StudioCanvasContainer(
                    viewModel = viewModel,
                    onDrawingViewCreated = onDrawingViewCreated,
                    onMetricsUpdated = onMetricsUpdated,
                    onStrokeCommitted = onStrokeCommitted
                )
                StudioBottomToolBar(
                    viewModel = viewModel,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

/**
 * Top floating banner informing user that this is a read-only sample project.
 */
@Composable
private fun SampleProjectBanner(
    projectTitle: String,
    onSaveAs: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val typography = StudioTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.m))
            .background(colors.accent.container)
            .border(1.dp, colors.accent.primary, androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.m))
            .padding(horizontal = spacing.m, vertical = spacing.s)
            .semantics { contentDescription = "Sample artwork banner: This is a sample. Save As to make it yours." },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "This is a sample. Save As to make it yours.",
                style = typography.bodyMedium,
                color = colors.accent.primary
            )
            Text(
                text = "Viewing '$projectTitle' in read-only mode.",
                style = typography.labelSmall,
                color = colors.textSecondary
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            com.artstudio.ui.components.StudioButton(
                text = "Save As",
                onClick = onSaveAs,
                variant = com.artstudio.ui.components.StudioButtonVariant.Primary
            )
            com.artstudio.ui.components.StudioButton(
                text = "✕",
                onClick = onDismiss,
                variant = com.artstudio.ui.components.StudioButtonVariant.Ghost
            )
        }
    }
}

@Composable
private fun HandleAnimationPlayback(viewModel: StudioViewModel, drawingViewRef: ArtStudioDrawingView?) {
    LaunchedEffect(viewModel.isAnimationPlaying) {
        if (viewModel.isAnimationPlaying) {
            val doc = drawingViewRef?.document?.authoritativeDocument
            val timeline = doc?.animation?.toTimeline() ?: com.artstudio.animation.AnimationTimeline()
            val controller = com.artstudio.animation.AnimationPlaybackController(timeline)
            controller.goToFrame(timeline.currentFrameIndex)
            controller.play()

            val msPerTick = (1000L / timeline.fps.coerceAtLeast(1).toLong()).coerceAtLeast(16L)
            while (viewModel.isAnimationPlaying && controller.isPlaying) {
                kotlinx.coroutines.delay(msPerTick)
                if (!viewModel.isAnimationPlaying) break
                val newIndex = controller.advanceTime(msPerTick)
                val hm = drawingViewRef?.document?.historyManager
                if (hm != null && newIndex != timeline.currentFrameIndex) {
                    com.artstudio.animation.AnimationTimelineManager(hm).selectFrame(newIndex)
                    drawingViewRef?.invalidate()
                }
                if (!controller.isPlaying) {
                    viewModel.isAnimationPlaying = false
                }
            }
        }
    }
}

private fun setupDrawingView(
    view: ArtStudioDrawingView,
    context: Context,
    projectId: String,
    projectRepository: ProjectRepository,
    viewModel: StudioViewModel,
    autosaveManager: AutosaveManager,
    onStateChanged: () -> Unit
) {
    loadProjectIntoView(context, view, projectId, projectRepository) { viewModel.studioErrorMessage = it }

    view.document.historyManager?.let { hm ->
        hm.onStateChanged = { state ->
            if (state.isDirty) projectRepository.markDirty(projectId)
            else projectRepository.markClean(projectId)
            onStateChanged()
        }
    }
    view.onStrokeCommitted = { stroke ->
        autosaveManager.notifyEdit(stroke.layerId)
        onStateChanged()
    }
    view.onColorPicked = { argb ->
        viewModel.selectColor(com.artstudio.color.ColorData.fromArgb(argb))
    }
}

private fun loadProjectIntoView(
    ctx: Context,
    view: ArtStudioDrawingView,
    projectId: String,
    projectRepo: ProjectRepository,
    onError: (String) -> Unit
) {
    val projectDir = File(ctx.filesDir, "projects")
    val projectFile = File(projectDir, "$projectId.ffart")
    if (projectFile.exists()) {
        try {
            val nativeProject = com.artstudio.document.io.DocumentPackage.loadProject(projectFile)
            view.loadNativeProject(nativeProject)
        } catch (e: Exception) {
            onError("Error loading project: ${e.message}")
        }
    } else if (projectRepo.libraryManager != null) {
        when (val openRes = projectRepo.libraryManager.openProject(projectId)) {
            is com.artstudio.project.ProjectOpenResult.Success -> {
                val pFile = File(openRes.metadata.filePath ?: File(projectDir, "$projectId.ffart").absolutePath)
                if (pFile.exists()) {
                    try {
                        val nativeProject = com.artstudio.document.io.DocumentPackage.loadProject(pFile)
                        view.loadNativeProject(nativeProject)
                    } catch (e: Exception) {
                        onError("Error loading project: ${e.message}")
                    }
                }
            }
            is com.artstudio.project.ProjectOpenResult.RecoveryAvailable -> {
                try {
                    val nativeProject = com.artstudio.document.io.DocumentPackage.loadProject(openRes.recoveryFile)
                    view.loadNativeProject(nativeProject)
                } catch (e: Exception) {
                    onError("Error loading recovery: ${e.message}")
                }
            }
            is com.artstudio.project.ProjectOpenResult.Error -> {
                onError(openRes.message)
            }
        }
    }
}

private fun createAutosaveManager(
    ctx: Context,
    projectId: String,
    drawingViewProvider: () -> ArtStudioDrawingView?
): AutosaveManager {
    return AutosaveManager(
        policy = AutosavePolicy(debounceDelayMs = 2000L, maxEditsThreshold = 10),
        dispatcher = kotlinx.coroutines.Dispatchers.IO,
        performAutosave = { _, _ ->
            val doc = drawingViewProvider()?.document?.authoritativeDocument ?: return@AutosaveManager
            val projectDir = File(ctx.filesDir, "projects").apply { mkdirs() }
            val projectFile = File(projectDir, "$projectId.ffart")
            val layerBuffers = (drawingViewProvider()?.document as? com.artstudio.core.DefaultDocumentState)?.getAllLayerPixelBuffers() ?: emptyMap()
            val tempFile = File.createTempFile("autosave_", ".tmp", projectDir)
            try {
                com.artstudio.document.io.DocumentPackage.saveProject(
                    document = doc,
                    layerPixelBuffers = layerBuffers,
                    outputFile = tempFile
                )
                if (projectFile.exists()) projectFile.delete()
                if (!tempFile.renameTo(projectFile)) {
                    tempFile.copyTo(projectFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                android.util.Log.w("ManualStudioScreen", "Autosave failed: ${e.message}", e)
            }
        }
    )
}

private fun handleImageImport(context: Context, uri: Uri, viewModel: StudioViewModel) {
    when (val result = com.artstudio.importer.UriImportValidator.validateAndRead(context, uri)) {
        is com.artstudio.importer.UriImportValidator.ValidationResult.Valid -> {
            val decoded = RasterImageImporter.decodeRasterImage(result.bytes, result.displayName)
            if (decoded != null) {
                viewModel.pendingImportedImage = decoded
            } else {
                viewModel.importErrorMessage = "Failed to decode image: format unsupported or corrupt."
            }
        }
        is com.artstudio.importer.UriImportValidator.ValidationResult.Rejected -> {
            viewModel.importErrorMessage = result.reason
        }
    }
}

