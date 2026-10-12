package com.artstudio.ui.screens.studio

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artstudio.brush.BrushPresetRepository
import com.artstudio.document.model.LayerNode
import com.artstudio.guides.ReferenceImageManager
import com.artstudio.shape.ShapeCreationParams
import com.artstudio.ui.ArtStudioDrawingView
import com.artstudio.ui.components.*
import com.artstudio.ui.model.ActivePanel
import com.artstudio.ui.model.StudioTool
import com.artstudio.ui.theme.StudioTheme
import com.artstudio.ui.viewmodel.StudioViewModel

/**
 * Hosts all primary tool drawers, panels, and toolbars based on ActivePanel state.
 * Strictly decomposed so every composable is well under 150 lines.
 */
@Composable
fun StudioPanelHost(
    viewModel: StudioViewModel,
    drawingView: ArtStudioDrawingView?,
    onPickImageRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val panel = viewModel.activePanel ?: return
    Box(modifier = modifier.fillMaxSize()) {
        when (panel) {
            ActivePanel.Brush -> RenderBrushPanel(viewModel, drawingView)
            ActivePanel.Color -> RenderColorPanel(viewModel, drawingView)
            ActivePanel.Layers -> RenderLayersPanel(viewModel, drawingView, onPickImageRequested)
            ActivePanel.Selection -> RenderSelectionPanel(drawingView)
            ActivePanel.Transform -> RenderTransformPanel(viewModel, drawingView)
            ActivePanel.Fill -> RenderFillPanel(viewModel, drawingView)
            ActivePanel.PixelOps -> RenderPixelOpsPanel(viewModel, drawingView)
            ActivePanel.DrawingAids -> RenderDrawingAidsPanel(viewModel, drawingView, onPickImageRequested)
            ActivePanel.Shapes -> RenderShapesPanel(viewModel, drawingView)
            ActivePanel.AutoDraw -> RenderAutoDrawDialog(viewModel, drawingView)
            ActivePanel.ColorStack -> RenderColorStackPanel(viewModel, drawingView)
            ActivePanel.Deformation -> RenderDeformationPanel(viewModel, drawingView)
            ActivePanel.Text, ActivePanel.Vector, ActivePanel.Filters,
            ActivePanel.Adjustments, ActivePanel.Comic, ActivePanel.Timeline -> {
                StudioSecondaryPanelHost(viewModel, panel, drawingView)
            }
        }
    }
}

@Composable
private fun RenderBrushPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val presetRepo = remember { BrushPresetRepository() }
    BrushPresetDialog(
        presetRepository = presetRepo,
        selectedPresetId = "preset_studio_pen",
        onPresetSelected = { preset ->
            viewModel.updateBrushSize(preset.config.size)
            viewModel.updateBrushOpacity(preset.config.opacity)
            drawingView?.setBrushConfig(preset.config)
            viewModel.closeActivePanel()
        },
        onDismissRequest = { viewModel.closeActivePanel() }
    )
}

@Composable
private fun BoxScope.RenderColorPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m)) {
        StudioColorPanel(
            colorState = viewModel.colorState,
            onColorSelected = { color ->
                viewModel.selectColor(color)
                drawingView?.setActiveColor(color.argbInt)
            },
            onEyedropperToggle = {
                viewModel.selectTool(StudioTool.EYEDROPPER)
                drawingView?.isEyedropperActive = true
            },
            isEyedropperActive = viewModel.activeTool == StudioTool.EYEDROPPER
        )
    }
}

@Composable
private fun BoxScope.RenderLayersPanel(
    viewModel: StudioViewModel,
    drawingView: ArtStudioDrawingView?,
    onPickImageRequested: () -> Unit
) {
    val spacing = StudioTheme.spacing
    val lm = drawingView?.document?.layerManager ?: return
    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m)) {
        StudioLayerPanel(
            layerManager = lm,
            onDismissRequest = { viewModel.closeActivePanel() },
            onImportRasterRequested = onPickImageRequested
        )
    }
}

@Composable
private fun BoxScope.RenderSelectionPanel(drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    val sm = drawingView?.document?.selectionManager ?: return
    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp, start = spacing.m, end = spacing.m)) {
        StudioSelectionBar(
            selectionManager = sm,
            onExpandClicked = { sm.expand(5); drawingView.invalidate() },
            onContractClicked = { sm.contract(5); drawingView.invalidate() },
            onFeatherClicked = { sm.feather(3f); drawingView.invalidate() }
        )
    }
}

@Composable
private fun BoxScope.RenderTransformPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    val lm = drawingView?.document?.layerManager ?: return
    val sm = drawingView.document.selectionManager
    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m).widthIn(max = 380.dp)) {
        StudioTransformPanel(layerManager = lm, selectionManager = sm, onDismiss = { viewModel.closeActivePanel() })
    }
}

@Composable
private fun BoxScope.RenderFillPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp, start = spacing.m, end = spacing.m)) {
        StudioFillPanel(
            config = viewModel.fillConfig.copy(fillColor = viewModel.colorState.activeColor.argbInt),
            onConfigChanged = {
                viewModel.fillConfig = it
                drawingView?.fillConfig = it
            },
            onDismissRequest = { viewModel.closeActivePanel() }
        )
    }
}

@Composable
private fun BoxScope.RenderPixelOpsPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp, start = spacing.m, end = spacing.m)) {
        StudioPixelOpsPanel(
            config = viewModel.pixelOpsConfig,
            onConfigChanged = {
                viewModel.pixelOpsConfig = it
                drawingView?.pixelOpsConfig = it
            },
            onDismissRequest = { viewModel.closeActivePanel() }
        )
    }
}

@Composable
private fun BoxScope.RenderDrawingAidsPanel(
    viewModel: StudioViewModel,
    drawingView: ArtStudioDrawingView?,
    onPickImageRequested: () -> Unit
) {
    val spacing = StudioTheme.spacing
    val refMgr = drawingView?.referenceManager ?: remember { ReferenceImageManager() }
    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m)) {
        StudioDrawingAidsPanel(
            config = viewModel.drawingAidsConfig,
            onConfigChanged = {
                viewModel.drawingAidsConfig = it
                drawingView?.drawingAidsConfig = it
                drawingView?.invalidate()
            },
            referenceManager = refMgr,
            onReferenceUpdated = { drawingView?.invalidate() },
            onPickReferenceImageRequested = onPickImageRequested,
            onDismissRequest = { viewModel.closeActivePanel() }
        )
    }
}

@Composable
private fun BoxScope.RenderShapesPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m)) {
        StudioShapePanel(
            onCreateShape = { params ->
                handleShapeCreation(params, drawingView)
                viewModel.closeActivePanel()
            },
            onDismissRequest = { viewModel.closeActivePanel() }
        )
    }
}

private fun handleShapeCreation(
    params: ShapeCreationParams,
    drawingView: ArtStudioDrawingView?
) {
    val currentDoc = drawingView?.document?.authoritativeDocument ?: return
    val shapeObj = com.artstudio.shape.ShapeGeometryEngine.createShape(params)
    when (params.targetMode) {
        com.artstudio.shape.ShapeTargetMode.NEW_VECTOR_LAYER -> {
            val newLayer = LayerNode.VectorPathLayer(name = shapeObj.name, vectorObjects = listOf(shapeObj))
            drawingView.document.historyManager?.execute(com.artstudio.history.AddLayerCommand(layer = newLayer))
        }
        com.artstudio.shape.ShapeTargetMode.ACTIVE_VECTOR_LAYER -> {
            val selId = currentDoc.selectedLayerId
            if (selId != null) {
                drawingView.document.historyManager?.execute(
                    com.artstudio.history.AddVectorObjectCommand(targetLayerId = selId, vectorObject = shapeObj)
                )
            }
        }
        com.artstudio.shape.ShapeTargetMode.STAMP_TO_RASTER_LAYER -> {
            val selId = currentDoc.selectedLayerId
            if (selId != null) {
                val activeLayer = currentDoc.findLayerById(selId) as? LayerNode.RasterLayer
                if (activeLayer != null) {
                    val w = activeLayer.width
                    val h = activeLayer.height
                    val ds = drawingView.document as? com.artstudio.core.DefaultDocumentState
                    val buf = ds?.getOrRenderLayerPixels(selId) ?: IntArray(w * h)
                    val stamped = com.artstudio.shape.ShapeGeometryEngine.stampShapeToRasterBuffer(buf, w, h, shapeObj)
                    val deltas = com.artstudio.history.TileDeltaHelper.extractTileDeltas(buf, stamped, w, h)
                    System.arraycopy(stamped, 0, buf, 0, w * h)
                    val cmd = com.artstudio.history.ApplyFilterCommand(
                        layerId = selId,
                        filterName = "Stamp Shape ${shapeObj.name}",
                        width = w,
                        height = h,
                        tileDeltas = deltas
                    )
                    drawingView.document.historyManager?.execute(cmd)
                    drawingView.renderer.updateLayerPixels(selId, buf)
                }
            }
        }
    }
    drawingView.invalidate()
}

@Composable
private fun RenderAutoDrawDialog(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    StudioAutoDrawDialog(
        drawingView = drawingView,
        onDismissRequest = { viewModel.closeActivePanel() }
    )
}

@Composable
private fun BoxScope.RenderColorStackPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp, start = spacing.m, end = spacing.m)) {
        StudioColorStackPanel(
            selectedStyleId = viewModel.selectedColorStyleId,
            onStyleSelected = { viewModel.selectedColorStyleId = it },
            strength = viewModel.colorStackStrength,
            onStrengthChanged = { viewModel.colorStackStrength = it },
            opacity = viewModel.colorStackOpacity,
            onOpacityChanged = { viewModel.colorStackOpacity = it },
            onBakeToLayers = {
                val doc = drawingView?.document?.authoritativeDocument
                if (doc != null && drawingView != null) {
                    val recipe = com.artstudio.colorstack.ColorStackPresets.getPresetForStyle(viewModel.selectedColorStyleId)
                    val result = com.artstudio.colorstack.ColorStackPipeline.execute(
                        recipe = recipe,
                        linework = null,
                        regionMasks = emptyMap(),
                        regionColors = emptyMap(),
                        width = doc.canvas.width,
                        height = doc.canvas.height
                    )
                    val bakedGroup = com.artstudio.colorstack.ColorStackPipeline.bakeToLayers(doc, result)
                    drawingView.document.historyManager?.execute(com.artstudio.history.AddLayerCommand(layer = bakedGroup))
                    drawingView.invalidate()
                }
                viewModel.closeActivePanel()
            },
            onDismissRequest = { viewModel.closeActivePanel() }
        )
    }
}

@Composable
private fun BoxScope.RenderDeformationPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    // Auto-start deformation session if not active
    LaunchedEffect(Unit) {
        if (drawingView != null && drawingView.deformationSession == null) {
            drawingView.startDeformation()
        }
    }
    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp, start = spacing.m, end = spacing.m)) {
        StudioDeformationPanel(
            toolMode = viewModel.deformationToolMode,
            onToolModeChanged = {
                viewModel.deformationToolMode = it
                drawingView?.deformationSession?.activeToolMode = it
                drawingView?.invalidate()
            },
            liquifySubMode = viewModel.deformationLiquifySubMode,
            onLiquifySubModeChanged = { viewModel.deformationLiquifySubMode = it },
            brushRadius = viewModel.deformationBrushRadius,
            onBrushRadiusChanged = {
                viewModel.deformationBrushRadius = it
                drawingView?.deformationSession?.brush = drawingView?.deformationSession?.brush?.copy(radius = it) ?: com.artstudio.deformation.LiquifyBrush(radius = it)
                drawingView?.invalidate()
            },
            brushStrength = viewModel.deformationBrushStrength,
            onBrushStrengthChanged = {
                viewModel.deformationBrushStrength = it
                drawingView?.deformationSession?.brush = drawingView?.deformationSession?.brush?.copy(strength = it) ?: com.artstudio.deformation.LiquifyBrush(strength = it)
                drawingView?.invalidate()
            },
            isDirty = viewModel.isDeformationDirty || (drawingView?.deformationSession?.isDirty == true),
            onCommit = {
                drawingView?.commitDeformation()
                viewModel.isDeformationDirty = false
                viewModel.closeActivePanel()
            },
            onReset = {
                drawingView?.resetDeformation()
                viewModel.isDeformationDirty = false
            },
            onCancel = {
                drawingView?.cancelDeformation()
                viewModel.isDeformationDirty = false
                viewModel.closeActivePanel()
            }
        )
    }
}

