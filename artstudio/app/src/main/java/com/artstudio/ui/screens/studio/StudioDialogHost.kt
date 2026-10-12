package com.artstudio.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.artstudio.export.*
import com.artstudio.importer.ImportDestination
import com.artstudio.importer.RasterImageImporter
import com.artstudio.project.ProjectRepository
import com.artstudio.ui.ArtStudioDrawingView
import com.artstudio.ui.components.StudioButton
import com.artstudio.ui.components.StudioButtonVariant
import com.artstudio.ui.components.StudioDialog
import com.artstudio.ui.components.StudioExportDialog
import com.artstudio.ui.components.StudioRasterImportDialog
import com.artstudio.ui.theme.StudioTheme
import com.artstudio.ui.viewmodel.StudioViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Hosts all modals and alert dialogs for the Studio canvas.
 * Decomposed into small sub-composables (< 80 lines each) to strictly guarantee <= 150 lines.
 */
@Composable
fun StudioDialogHost(
    viewModel: StudioViewModel,
    drawingView: ArtStudioDrawingView?,
    projectRepository: ProjectRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClearConfirmationDialogHost(viewModel, drawingView, projectRepository)
    RasterImportDialogHost(viewModel, drawingView, projectRepository, onNavigateBack)
    ExportDialogHost(viewModel, drawingView)
    NoticeDialogHost(viewModel)
}

@Composable
private fun ClearConfirmationDialogHost(
    viewModel: StudioViewModel,
    drawingView: ArtStudioDrawingView?,
    projectRepository: ProjectRepository
) {
    if (viewModel.showClearConfirmationDialog) {
        StudioDialog(
            title = "Clear Active Layer",
            body = "Are you sure you want to clear the active layer? This action is undoable.",
            confirmText = "Clear",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                drawingView?.clearActiveLayerUndoably()
                projectRepository.recordStrokes(viewModel.projectId, 0)
                viewModel.showClearConfirmationDialog = false
            },
            onDismiss = { viewModel.showClearConfirmationDialog = false }
        )
    }
}

@Composable
private fun RasterImportDialogHost(
    viewModel: StudioViewModel,
    drawingView: ArtStudioDrawingView?,
    projectRepository: ProjectRepository,
    onNavigateBack: () -> Unit
) {
    val pending = viewModel.pendingImportedImage ?: return
    val doc = drawingView?.document?.authoritativeDocument
    val canvasW = doc?.canvas?.width ?: 2048
    val canvasH = doc?.canvas?.height ?: 2048

    StudioRasterImportDialog(
        decodedImage = pending,
        canvasWidth = canvasW,
        canvasHeight = canvasH,
        onConfirmImport = { destination, placement ->
            when (destination) {
                ImportDestination.IMPORT_AS_LAYER -> {
                    drawingView?.document?.layerManager?.importRasterLayer(pending, placement)
                    drawingView?.invalidate()
                }
                ImportDestination.ADD_AS_REFERENCE -> {
                    drawingView?.referenceManager?.let { refMgr ->
                        RasterImageImporter.addAsReference(refMgr, pending, placement)
                        drawingView.invalidate()
                    }
                }
                ImportDestination.OPEN_AS_NEW_PROJECT -> {
                    val newDoc = RasterImageImporter.openAsNewProject(pending)
                    projectRepository.createNewProject(newDoc.title, newDoc.canvas.width, newDoc.canvas.height)
                    onNavigateBack()
                }
            }
            viewModel.pendingImportedImage = null
        },
        onDismissRequest = { viewModel.pendingImportedImage = null }
    )
}

@Composable
private fun ExportDialogHost(
    viewModel: StudioViewModel,
    drawingView: ArtStudioDrawingView?
) {
    if (!viewModel.showExportDialog) return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exportService = remember { ArtworkExportService() }
    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<ExportResult?>(null) }
    var currentToken by remember { mutableStateOf<ExportCancellationToken?>(null) }

    val doc = drawingView?.document?.authoritativeDocument ?: com.artstudio.document.model.ArtDocument()
    val activeLayer = doc.getSelectedLayer()
    val targetDir = File(context.filesDir, "exports").apply { mkdirs() }

    StudioExportDialog(
        document = doc,
        selectedLayerName = activeLayer?.name,
        isExporting = isExporting,
        exportResult = exportResult,
        onConfirmExport = { config, filename ->
            val token = ExportCancellationToken()
            currentToken = token
            isExporting = true
            exportResult = null
            val targetFile = File(targetDir, filename)
            val layerBuffers = (drawingView?.document as? com.artstudio.core.DefaultDocumentState)?.getAllLayerPixelBuffers() ?: emptyMap()
            coroutineScope.launch {
                val res = exportService.exportArtwork(
                    document = doc,
                    config = config,
                    targetFile = targetFile,
                    layerPixelBuffers = layerBuffers,
                    cancellationToken = token
                )
                if (res is ExportResult.Success) {
                    val mediaStoreRes = PlatformExportIntegration.saveToMediaStore(
                        context = context,
                        sourceFile = res.outputFile,
                        mimeType = config.format.mimeType,
                        displayName = filename
                    )
                    exportResult = if (mediaStoreRes.isSuccess) {
                        val dest = mediaStoreRes.getOrNull()
                        res.copy(
                            mediaStoreUri = dest?.uri,
                            displayLocation = dest?.displayLocation ?: res.outputFile.absolutePath
                        )
                    } else {
                        res.copy(displayLocation = res.outputFile.absolutePath)
                    }
                } else {
                    exportResult = res
                }
                isExporting = false
            }
        },
        onCancelExport = {
            currentToken?.cancel()
            isExporting = false
        },
        onDismissRequest = {
            viewModel.showExportDialog = false
            exportResult = null
        }
    )
}

@Composable
private fun NoticeDialogHost(viewModel: StudioViewModel) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    viewModel.importErrorMessage?.let { errMsg ->
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 440.dp)
                    .clip(RoundedCornerShape(cornerRadius.l))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                    .padding(spacing.l),
                verticalArrangement = Arrangement.spacedBy(spacing.m)
            ) {
                Text(text = "Import Notice", style = typography.titleMedium, color = colors.textPrimary)
                Text(text = errMsg, style = typography.bodyMedium, color = colors.textPrimary)
                StudioButton(
                    text = "Dismiss",
                    variant = StudioButtonVariant.Primary,
                    onClick = { viewModel.importErrorMessage = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    viewModel.studioErrorMessage?.let { msg ->
        StudioDialog(
            title = "Notice",
            body = msg,
            confirmText = "OK",
            onConfirm = { viewModel.studioErrorMessage = null },
            onDismiss = { viewModel.studioErrorMessage = null }
        )
    }
}
