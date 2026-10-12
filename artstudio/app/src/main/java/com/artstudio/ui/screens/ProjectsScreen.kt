package com.artstudio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.project.*
import com.artstudio.importer.RasterImageImporter
import com.artstudio.ui.components.*
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class ProjectsTab {
    MY_PROJECTS,
    SAMPLES
}

/**
 * Real Home / Projects screen providing entry points for:
 * 1. New Canvas (with dimensions preset picker)
 * 2. Open Project (selected from recent projects)
 * 3. Import Image (via Android Document SAF APIs)
 * 4. Recent Projects List with live metadata, search, sort, duplicate, rename, recovery prompts, and safe delete.
 * 5. Official CC0 Sample Projects tab.
 */
@Composable
fun ProjectsScreen(
    projectRepository: ProjectRepository,
    onOpenStudio: (projectId: String, projectTitle: String, isReadOnlySample: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    var showNewCanvasDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectMetadata?>(null) }
    var projectToRename by remember { mutableStateOf<ProjectMetadata?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var lastDeletedProjectId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(ProjectSortOrder.ModifiedDescending) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var canvasErrorMessage by remember { mutableStateOf<String?>(null) }
    var currentTab by remember { mutableStateOf(ProjectsTab.MY_PROJECTS) }

    // SAF Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            projectRepository.safelyRetainUriPermission(context.contentResolver, uri)
            var w = 2048
            var h = 2048
            var displayName = "Imported Image"
            when (val result = com.artstudio.importer.UriImportValidator.validateAndRead(context, uri)) {
                is com.artstudio.importer.UriImportValidator.ValidationResult.Valid -> {
                    displayName = result.displayName
                    val decoded = RasterImageImporter.decodeRasterImage(result.bytes, result.displayName)
                    if (decoded != null) {
                        w = decoded.width
                        h = decoded.height
                    } else {
                        android.util.Log.w("ProjectsScreen", "Failed to decode raster image format from $uri")
                    }
                }
                is com.artstudio.importer.UriImportValidator.ValidationResult.Rejected -> {
                    android.util.Log.w("ProjectsScreen", "URI import rejected: ${result.reason}")
                }
            }
            val imported = projectRepository.importFromDocumentUri(
                uri = uri.toString(),
                displayName = displayName,
                width = w,
                height = h
            )
            onOpenStudio(imported.id, imported.title, false)
        }
    }


    val allProjects = remember(projectRepository, refreshTrigger) {
        projectRepository.projects
    }

    val filteredProjects = remember(allProjects, searchQuery, selectedFolder, sortOrder) {
        allProjects.applyFilterAndSort(
            filter = ProjectSearchFilter(query = searchQuery, folder = selectedFolder),
            sortOrder = sortOrder
        )
    }

    val availableFolders = remember(allProjects) {
        allProjects.mapNotNull { it.folder }.distinct().sorted()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(spacing.l)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Art Studio Projects",
                    style = typography.headlineLarge,
                    color = colors.textPrimary
                )
                Text(
                    text = "${allProjects.size} project${if (allProjects.size == 1) "" else "s"} on device",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            // Quick New Canvas action button
            StudioButton(
                text = "+ New Canvas",
                onClick = { showNewCanvasDialog = true },
                variant = StudioButtonVariant.Primary
            )
        }

        Spacer(modifier = Modifier.height(spacing.s))

        StudioSegmentedSelector(
            options = listOf(ProjectsTab.MY_PROJECTS, ProjectsTab.SAMPLES),
            selectedOption = currentTab,
            onOptionSelected = { currentTab = it },
            optionLabel = { tab ->
                when (tab) {
                    ProjectsTab.MY_PROJECTS -> "My Projects"
                    ProjectsTab.SAMPLES -> "Sample Artworks"
                }
            }
        )

        Spacer(modifier = Modifier.height(spacing.m))

        if (currentTab == ProjectsTab.SAMPLES) {
            SamplesListSection(
                context = context,
                projectRepository = projectRepository,
                onOpenStudio = onOpenStudio,
                modifier = Modifier.weight(1f)
            )
        } else {
            UserProjectsListSection(
                filteredProjects = filteredProjects,
                allProjectsEmpty = allProjects.isEmpty(),
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                availableFolders = availableFolders,
                selectedFolder = selectedFolder,
                onFolderSelected = { selectedFolder = it },
                lastDeletedProjectId = lastDeletedProjectId,
                sortOrder = sortOrder,
                onCycleSortOrder = {
                    val nextIndex = (sortOrder.ordinal + 1) % ProjectSortOrder.values().size
                    sortOrder = ProjectSortOrder.values()[nextIndex]
                },
                onUndoDelete = {
                    lastDeletedProjectId?.let { projectRepository.undoDelete(it) }
                    lastDeletedProjectId = null
                    refreshTrigger++
                },
                onShowNewCanvas = { showNewCanvasDialog = true },
                onLaunchImagePicker = { imagePickerLauncher.launch("image/*") },
                onOpenProject = { proj ->
                    projectRepository.openProject(proj.id)
                    onOpenStudio(proj.id, proj.title, false)
                },
                onDuplicateProject = { proj ->
                    projectRepository.duplicateProject(proj.id)
                    refreshTrigger++
                },
                onRenameProject = { proj ->
                    projectToRename = proj
                    renameInput = proj.title
                },
                onDeleteProject = { proj ->
                    projectToDelete = proj
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // New Canvas Configuration Dialog
    if (showNewCanvasDialog) {
        NewCanvasPresetDialog(
            onDismiss = { showNewCanvasDialog = false },
            onCreate = { title, width, height ->
                try {
                    val newProject = projectRepository.createNewProject(title, width, height)
                    refreshTrigger++
                    showNewCanvasDialog = false
                    onOpenStudio(newProject.id, newProject.title, false)
                } catch (e: IllegalArgumentException) {
                    canvasErrorMessage = e.message
                }
            }
        )
    }

    if (canvasErrorMessage != null) {
        StudioDialog(
            title = "Canvas Exceeds Memory Limits",
            body = canvasErrorMessage ?: "Requested canvas dimensions exceed available device memory.",
            confirmText = "OK",
            onConfirm = { canvasErrorMessage = null },
            onDismiss = { canvasErrorMessage = null }
        )
    }

    // Rename Dialog
    if (projectToRename != null) {
        val proj = projectToRename!!
        StudioDialog(
            title = "Rename Project",
            body = "Enter a new title for '${proj.title}':",
            confirmText = "Rename",
            cancelText = "Cancel",
            onConfirm = {
                if (renameInput.isNotBlank()) {
                    projectRepository.renameProject(proj.id, renameInput.trim())
                    refreshTrigger++
                }
                projectToRename = null
            },
            onDismiss = { projectToRename = null }
        ) {
            OutlinedTextField(
                value = renameInput,
                onValueChange = { renameInput = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Delete Confirmation Dialog
    if (projectToDelete != null) {
        val proj = projectToDelete!!
        StudioDialog(
            title = "Delete Project?",
            body = "Are you sure you want to delete '${proj.title}'? You can undo this action immediately after.",
            confirmText = "Delete",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                val deletedId = proj.id
                projectRepository.deleteProject(deletedId, softDelete = true)
                lastDeletedProjectId = deletedId
                refreshTrigger++
                projectToDelete = null
            },
            onDismiss = { projectToDelete = null }
        )
    }
}

/**
 * Individual Project Item Card with Thumbnail, Metadata, Duplicate, Rename, and Delete
 */
@Composable
private fun ProjectItemCard(
    project: ProjectMetadata,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(project.updatedAt) { dateFormat.format(Date(project.updatedAt)) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(spacing.m)
            .semantics {
                this.role = Role.Button
                this.contentDescription = "Project ${project.title}, ${project.dimensionsLabel}, ${project.strokeCount} strokes, modified $formattedDate"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        // Thumbnail Graphic Placeholder (never decodes full resolution document)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(cornerRadius.s))
                .background(Color(project.thumbnailColor))
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = project.title.take(1).uppercase(),
                style = typography.titleMedium,
                color = Color.White
            )
        }

        // Project Info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Text(
                    text = project.title,
                    style = typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1
                )
                if (project.hasRecoveryAvailable) {
                    Text(
                        text = "⚠️ Recovery",
                        style = typography.labelSmall,
                        color = colors.warning
                    )
                }
                if (!project.isAccessible) {
                    Text(
                        text = "🚫 Inaccessible",
                        style = typography.labelSmall,
                        color = colors.error
                    )
                }
                if (project.isDirty) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.warning)
                    )
                }
            }

            val folderTag = if (project.folder != null) "[${project.folder}] " else ""
            Text(
                text = "$folderTag${project.dimensionsLabel} • ${project.strokeCount} strokes • $formattedDate",
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }

        // Action Buttons Row: Duplicate, Rename, Delete
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            // Duplicate Button
            Box(
                modifier = Modifier
                    .size(StudioAccessibility.MinTouchTarget)
                    .clip(CircleShape)
                    .clickable(onClick = onDuplicate),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📋", style = typography.titleSmall)
            }

            // Rename Button
            Box(
                modifier = Modifier
                    .size(StudioAccessibility.MinTouchTarget)
                    .clip(CircleShape)
                    .clickable(onClick = onRename),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✏️", style = typography.titleSmall)
            }

            // Delete Button
            Box(
                modifier = Modifier
                    .size(StudioAccessibility.MinTouchTarget)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🗑️", style = typography.titleSmall)
            }
        }
    }
}

/**
 * Modal dialog for choosing a canvas preset or custom dimensions.
 */
@Composable
private fun NewCanvasPresetDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, width: Int, height: Int) -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    var title by remember { mutableStateOf("New Drawing") }
    var selectedPresetIndex by remember { mutableIntStateOf(0) }

    val presets = listOf(
        Triple("Square Studio (2048 x 2048)", 2048, 2048),
        Triple("HD Canvas (1920 x 1080)", 1920, 1080),
        Triple("4K Ultra (3840 x 2160)", 3840, 2160),
        Triple("Mobile Portrait (1080 x 2400)", 1080, 2400)
    )

    StudioDialog(
        title = "Create New Canvas",
        body = "Choose a canvas dimension preset or enter custom bounds:",
        confirmText = "Create Canvas",
        cancelText = "Cancel",
        onConfirm = {
            val chosen = presets[selectedPresetIndex]
            onCreate(title, chosen.second, chosen.third)
        },
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Project Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "Preset Dimensions:",
                style = typography.labelMedium,
                color = colors.textSecondary
            )

            presets.forEachIndexed { index, (presetName, _, _) ->
                val isSelected = index == selectedPresetIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.accent.container else colors.surfaceOverlay)
                        .border(
                            1.dp,
                            if (isSelected) colors.accent.primary else colors.outlineSubtle,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedPresetIndex = index }
                        .padding(spacing.m)
                ) {
                    Text(
                        text = presetName,
                        style = typography.bodyMedium,
                        color = if (isSelected) colors.accent.primary else colors.textPrimary
                    )
                }
            }
        }
    }
}

/**
 * Section rendering the user's project library, search, filters, and cards.
 */
@Composable
private fun UserProjectsListSection(
    filteredProjects: List<ProjectMetadata>,
    allProjectsEmpty: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    availableFolders: List<String>,
    selectedFolder: String?,
    onFolderSelected: (String?) -> Unit,
    lastDeletedProjectId: String?,
    sortOrder: ProjectSortOrder,
    onCycleSortOrder: () -> Unit,
    onUndoDelete: () -> Unit,
    onShowNewCanvas: () -> Unit,
    onLaunchImagePicker: () -> Unit,
    onOpenProject: (ProjectMetadata) -> Unit,
    onDuplicateProject: (ProjectMetadata) -> Unit,
    onRenameProject: (ProjectMetadata) -> Unit,
    onDeleteProject: (ProjectMetadata) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // Quick Entry Points Bar (New Canvas, Open, Import Image)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            StudioButton(
                text = "New Canvas",
                onClick = onShowNewCanvas,
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.weight(1f)
            )
            StudioButton(
                text = "Import Image",
                onClick = onLaunchImagePicker,
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.weight(1f)
            )
        }

        // Search & Filter Row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search by title, folder, or tags...", style = typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Folder Chips Row (if folders exist)
        if (availableFolders.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isAll = selectedFolder == null
                    StudioButton(
                        text = "All",
                        onClick = { onFolderSelected(null) },
                        variant = if (isAll) StudioButtonVariant.Primary else StudioButtonVariant.Ghost
                    )
                }
                items(availableFolders, key = { it }) { folderName ->
                    val isSelected = selectedFolder.equals(folderName, ignoreCase = true)
                    StudioButton(
                        text = folderName,
                        onClick = { onFolderSelected(folderName) },
                        variant = if (isSelected) StudioButtonVariant.Primary else StudioButtonVariant.Ghost
                    )
                }
            }
        }

        // Recover Deleted Project Banner (if available)
        if (lastDeletedProjectId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(colors.accent.container)
                    .padding(spacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Project deleted.",
                    style = typography.bodySmall,
                    color = colors.accent.primary
                )
                StudioButton(
                    text = "Undo Delete",
                    onClick = onUndoDelete,
                    variant = StudioButtonVariant.Ghost
                )
            }
        }

        // Recent Projects Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Projects",
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            // Sort toggle button
            Text(
                text = "Sort: ${sortOrder.name}",
                style = typography.labelSmall,
                color = colors.accent.primary,
                modifier = Modifier.clickable(onClick = onCycleSortOrder)
            )
        }

        if (filteredProjects.isEmpty()) {
            if (allProjectsEmpty) {
                StudioEmptyState(
                    title = "No Projects Yet",
                    description = "Create a blank canvas or import a reference image to begin drawing.",
                    actionText = "Create First Canvas",
                    onAction = onShowNewCanvas,
                    iconSymbol = "🎨"
                )
            } else {
                StudioEmptyState(
                    title = "No Matching Projects",
                    description = "No projects found matching '${searchQuery}'. Clear filter to see all projects.",
                    actionText = "Clear Search",
                    onAction = {
                        onSearchQueryChange("")
                        onFolderSelected(null)
                    },
                    iconSymbol = "🔍"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                items(filteredProjects, key = { it.id }) { project ->
                    ProjectItemCard(
                        project = project,
                        onClick = { onOpenProject(project) },
                        onDuplicate = { onDuplicateProject(project) },
                        onRename = { onRenameProject(project) },
                        onDelete = { onDeleteProject(project) }
                    )
                }
            }
        }
    }
}

/**
 * Metadata descriptor for bundled onboarding sample artworks.
 */
private data class SampleArtworkInfo(
    val id: String,
    val title: String,
    val filename: String,
    val subtitle: String,
    val description: String,
    val tag: String,
    val color: Long
)

/**
 * Section displaying the 3 official CC0 sample projects.
 */
@Composable
private fun SamplesListSection(
    context: android.content.Context,
    projectRepository: ProjectRepository,
    onOpenStudio: (projectId: String, projectTitle: String, isReadOnlySample: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val colors = StudioTheme.colors

    val samplePacks = remember {
        listOf(
            SampleArtworkInfo(
                id = "sample_project_doodle",
                title = "Quick Sketch Doodle",
                filename = "sample_doodle.ffart",
                subtitle = "3 raster layers • linework & warm tonal grade",
                description = "Multi-layer sketch with linework, background paper, and non-destructive adjustment layer.",
                tag = "Linework & Inks",
                color = 0xFFD84315L
            ),
            SampleArtworkInfo(
                id = "sample_project_comic",
                title = "4-Panel Comic Strip",
                filename = "sample_comic_panel.ffart",
                subtitle = "4 panels • speech bubbles • screentones",
                description = "Complete manga layout with 4 panels, speech balloons, screentone patterns, and vector text.",
                tag = "Comic & Manga",
                color = 0xFF1565C0L
            ),
            SampleArtworkInfo(
                id = "sample_project_trace",
                title = "CV AutoDraw Reference & Trace",
                filename = "sample_auto_trace.ffart",
                subtitle = "Reference photo • CV vector paths • baked cel",
                description = "Full CV AutoDraw pipeline with photo reference, vectorized Bezier contours, and baked Colour Lab shading.",
                tag = "AutoDraw & Colour Lab",
                color = 0xFF2E7D32L
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        Column {
            Text(
                text = "Official CC0 Sample Projects",
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = "Tap any sample to inspect its layers, comic panels, or vector strokes in read-only mode.",
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            items(samplePacks, key = { it.id }) { sample ->
                SampleArtworkCard(
                    sample = sample,
                    onClick = {
                        val targetDir = File(context.filesDir, "projects").apply { mkdirs() }
                        val targetFile = File(targetDir, sample.filename)
                        if (!targetFile.exists()) {
                            try {
                                context.assets.open("samples/${sample.filename}").use { input ->
                                    targetFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ProjectsScreen", "Error unpacking sample ${sample.filename}", e)
                            }
                        }
                        val registered = projectRepository.registerExistingProject(targetFile, "Samples")
                        val openId = registered?.id ?: sample.id
                        val openTitle = registered?.title ?: sample.title
                        onOpenStudio(openId, openTitle, true)
                    }
                )
            }
        }
    }
}

/**
 * Individual card representing a sample artwork with quick entry button.
 */
@Composable
private fun SampleArtworkCard(
    sample: SampleArtworkInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(spacing.m)
            .semantics {
                this.role = Role.Button
                this.contentDescription = "Sample artwork: ${sample.title}. ${sample.description}. Tap to open in Studio."
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(cornerRadius.s))
                .background(Color(sample.color))
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = sample.title.take(1).uppercase(),
                style = typography.titleMedium,
                color = Color.White
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Text(
                    text = sample.title,
                    style = typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(cornerRadius.xs))
                        .background(colors.accent.container)
                        .padding(horizontal = spacing.xs, vertical = 2.dp)
                ) {
                    Text(
                        text = sample.tag,
                        style = typography.labelSmall,
                        color = colors.accent.primary
                    )
                }
            }

            Text(
                text = sample.subtitle,
                style = typography.bodySmall,
                color = colors.textSecondary
            )

            Text(
                text = sample.description,
                style = typography.labelSmall,
                color = colors.textTertiary,
                maxLines = 2
            )
        }

        StudioButton(
            text = "Open Sample",
            onClick = onClick,
            variant = StudioButtonVariant.Secondary
        )
    }
}

/**
 * Backward-compatible 2-argument overload for ProjectsScreen.
 */
@Composable
fun ProjectsScreen(
    projectRepository: ProjectRepository,
    onOpenStudio: (projectId: String, projectTitle: String) -> Unit,
    modifier: Modifier = Modifier
) {
    ProjectsScreen(
        projectRepository = projectRepository,
        onOpenStudio = { id, title, _ -> onOpenStudio(id, title) },
        modifier = modifier
    )
}

