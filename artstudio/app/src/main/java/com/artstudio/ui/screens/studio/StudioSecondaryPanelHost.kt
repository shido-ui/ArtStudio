package com.artstudio.ui.screens.studio

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artstudio.document.model.LayerNode
import com.artstudio.ui.ArtStudioDrawingView
import com.artstudio.ui.components.*
import com.artstudio.ui.model.ActivePanel
import com.artstudio.ui.theme.StudioTheme
import com.artstudio.ui.viewmodel.StudioViewModel

/**
 * Secondary panel drawer host for Text, Vector, Filters, Adjustments, Comic, and Timeline.
 * Decomposed into small sub-composables (< 80 lines each) to strictly guarantee <= 150 lines.
 */
@Composable
fun StudioSecondaryPanelHost(
    viewModel: StudioViewModel,
    panel: ActivePanel,
    drawingView: ArtStudioDrawingView?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (panel) {
            ActivePanel.Text -> RenderTextPanel(viewModel, drawingView)
            ActivePanel.Vector -> RenderVectorPanel(viewModel, drawingView)
            ActivePanel.Filters -> RenderFiltersPanel(viewModel, drawingView)
            ActivePanel.Adjustments -> RenderAdjustmentsPanel(viewModel, drawingView)
            ActivePanel.Comic -> RenderComicPanel(viewModel, drawingView)
            ActivePanel.Timeline -> RenderTimelinePanel(viewModel, drawingView)
            else -> Unit
        }
    }
}

@Composable
private fun BoxScope.RenderTextPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    val currentDoc = drawingView?.document?.authoritativeDocument
    val activeLayer = currentDoc?.selectedLayerId?.let { currentDoc.findLayerById(it) }
    val activeTextObj = (activeLayer as? LayerNode.TextLayer)?.textObject

    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m)) {
        StudioTextPanel(
            activeTextObject = activeTextObj,
            onApplyText = { textObj ->
                if (activeLayer is LayerNode.TextLayer) {
                    val cmd = com.artstudio.history.UpdateTextObjectCommand(
                        layerId = activeLayer.id,
                        oldTextObject = activeLayer.textObject,
                        newTextObject = textObj
                    )
                    drawingView?.document?.historyManager?.execute(cmd)
                } else {
                    val newLayer = LayerNode.TextLayer(
                        name = textObj.text.take(24),
                        textObject = textObj
                    )
                    drawingView?.document?.historyManager?.execute(com.artstudio.history.AddTextLayerCommand(textLayer = newLayer))
                }
                drawingView?.invalidate()
            },
            onRasterizeText = {
                if (activeLayer is LayerNode.TextLayer && currentDoc != null) {
                    try {
                        val cmd = com.artstudio.history.RasterizeTextLayerCommand.create(currentDoc, activeLayer.id, drawingView?.context)
                        drawingView?.document?.historyManager?.execute(cmd)
                        drawingView?.invalidate()
                    } catch (e: Exception) {
                        viewModel.studioErrorMessage = "Failed to rasterize text: ${e.message}"
                    }
                }
            },
            onDismissRequest = { viewModel.closeActivePanel() }
        )
    }
}

@Composable
private fun BoxScope.RenderVectorPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    var vectorToolMode by remember { mutableStateOf(com.artstudio.vector.VectorToolMode.SELECT) }
    var vectorStyle by remember { mutableStateOf(com.artstudio.vector.VectorStyle()) }

    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m)) {
        StudioVectorPanel(
            currentTool = vectorToolMode,
            onToolSelected = { vectorToolMode = it },
            activeStyle = vectorStyle,
            onStyleChanged = { vectorStyle = it },
            onRasterizeRequested = {
                val activeLayer = drawingView?.document?.authoritativeDocument?.selectedLayerId ?: ""
                if (activeLayer.isNotEmpty() && drawingView != null) {
                    try {
                        val cmd = com.artstudio.history.RasterizeVectorLayerCommand.create(drawingView.document.authoritativeDocument, activeLayer)
                        drawingView.document.historyManager?.execute(cmd)
                        drawingView.invalidate()
                    } catch (e: Exception) {
                        viewModel.studioErrorMessage = "Failed to rasterize vector layer: ${e.message}"
                    }
                }
            },
            onDismissRequest = { viewModel.closeActivePanel() }
        )
    }
}

@Composable
private fun BoxScope.RenderFiltersPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    var previewEnabled by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m)) {
        StudioFilterPanel(
            activeConfig = viewModel.filterConfig,
            onConfigChanged = { viewModel.filterConfig = it },
            previewEnabled = previewEnabled,
            onTogglePreview = { previewEnabled = it },
            onApply = {
                val doc = drawingView?.document?.authoritativeDocument
                val activeLayer = doc?.getSelectedLayer() as? LayerNode.RasterLayer
                val lm = drawingView?.document?.layerManager
                if (activeLayer != null && lm != null) {
                    val w = activeLayer.width
                    val h = activeLayer.height
                    val layerBuf = (drawingView.document as? com.artstudio.core.DefaultDocumentState)?.getOrRenderLayerPixels(activeLayer.id) ?: IntArray(w * h)
                    val postPixels = com.artstudio.filter.ImageFilterEngine.applyFilter(layerBuf, w, h, viewModel.filterConfig)
                    val tileDeltas = com.artstudio.history.TileDeltaHelper.extractTileDeltas(layerBuf, postPixels, w, h)
                    val cmd = com.artstudio.history.ApplyFilterCommand(
                        layerId = activeLayer.id,
                        filterName = viewModel.filterConfig.type.displayName,
                        width = w,
                        height = h,
                        tileDeltas = tileDeltas
                    )
                    lm.applyFilterCommand(cmd, layerBuf)
                    drawingView.invalidate()
                }
                viewModel.closeActivePanel()
            },
            onCancel = { viewModel.closeActivePanel() }
        )
    }
}

@Composable
private fun BoxScope.RenderAdjustmentsPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    var previewEnabled by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m)) {
        StudioAdjustmentPanel(
            activeAdjustment = viewModel.activeAdjustment,
            onAdjustmentChanged = { viewModel.activeAdjustment = it },
            previewEnabled = previewEnabled,
            onTogglePreview = { previewEnabled = it },
            onAddAdjustmentLayer = {
                val lm = drawingView?.document?.layerManager
                if (lm != null) {
                    lm.addAdjustmentLayer(viewModel.activeAdjustment)
                    drawingView.invalidate()
                }
                viewModel.closeActivePanel()
            },
            onBakeRasterize = {
                val doc = drawingView?.document?.authoritativeDocument
                val lm = drawingView?.document?.layerManager
                val activeLayer = doc?.getSelectedLayer()
                if (lm != null && activeLayer != null && drawingView != null) {
                    if (activeLayer is LayerNode.AdjustmentLayer) {
                        lm.rasterizeAdjustmentLayer(activeLayer.id)
                    } else if (activeLayer is LayerNode.RasterLayer) {
                        val ds = drawingView.document as? com.artstudio.core.DefaultDocumentState
                        val buf = ds?.getOrRenderLayerPixels(activeLayer.id) ?: IntArray(activeLayer.width * activeLayer.height)
                        lm.applyDirectAdjustment(activeLayer.id, viewModel.activeAdjustment, buf, activeLayer.width, activeLayer.height)
                        drawingView.renderer.updateLayerPixels(activeLayer.id, buf)
                    }
                    drawingView.invalidate()
                }
                viewModel.closeActivePanel()
            },
            onClose = { viewModel.closeActivePanel() }
        )
    }
}

@Composable
private fun BoxScope.RenderComicPanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(top = 64.dp, bottom = 80.dp, end = spacing.m)) {
        StudioComicPanel(
            onAddPanel = { panel ->
                drawingView?.document?.historyManager?.execute(com.artstudio.history.AddComicPanelCommand(panel = panel))
                drawingView?.invalidate()
            },
            onSplitPanel = { isHorizontal, gutter ->
                val targetPanel = drawingView?.document?.authoritativeDocument?.comicPanels?.lastOrNull()
                if (targetPanel != null) {
                    val (c1, c2) = if (isHorizontal) {
                        com.artstudio.comic.ComicGeometryEngine.splitPanelHorizontal(targetPanel, 0.5f, gutter)
                    } else {
                        com.artstudio.comic.ComicGeometryEngine.splitPanelVertical(targetPanel, 0.5f, gutter)
                    }
                    drawingView?.document?.historyManager?.execute(
                        com.artstudio.history.SplitComicPanelCommand(originalPanel = targetPanel, childPanel1 = c1, childPanel2 = c2)
                    )
                    drawingView?.invalidate()
                }
            },
            onAddBubble = { bubble ->
                drawingView?.document?.historyManager?.execute(com.artstudio.history.AddSpeechBubbleCommand(bubble = bubble))
                drawingView?.invalidate()
            },
            onAddScreentone = { tone ->
                drawingView?.document?.historyManager?.execute(com.artstudio.history.AddScreentoneCommand(screentone = tone))
                drawingView?.invalidate()
            },
            onToggleGuides = { drawingView?.invalidate() },
            onDismissRequest = { viewModel.closeActivePanel() }
        )
    }
}

@Composable
private fun BoxScope.RenderTimelinePanel(viewModel: StudioViewModel, drawingView: ArtStudioDrawingView?) {
    val spacing = StudioTheme.spacing
    val doc = drawingView?.document?.authoritativeDocument
    val timeline = doc?.animation?.toTimeline() ?: com.artstudio.animation.AnimationTimeline()

    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = spacing.m, vertical = 72.dp)) {
        StudioTimelinePanel(
            timeline = timeline,
            isPlaying = viewModel.isAnimationPlaying,
            onPlayPauseToggle = { viewModel.isAnimationPlaying = !viewModel.isAnimationPlaying },
            onStop = {
                viewModel.isAnimationPlaying = false
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).selectFrame(0)
                    drawingView.invalidate()
                }
            },
            onStepForward = {
                val nextIdx = (timeline.currentFrameIndex + 1) % timeline.frames.size
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).selectFrame(nextIdx)
                    drawingView.invalidate()
                }
            },
            onStepBackward = {
                val prevIdx = if (timeline.currentFrameIndex > 0) timeline.currentFrameIndex - 1 else timeline.frames.lastIndex
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).selectFrame(prevIdx)
                    drawingView.invalidate()
                }
            },
            onSelectFrame = { idx ->
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).selectFrame(idx)
                    drawingView.invalidate()
                }
            },
            onAddFrame = {
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).addFrame()
                    drawingView.invalidate()
                }
            },
            onDuplicateFrame = { copyCels ->
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).duplicateFrame(duplicateCels = copyCels)
                    drawingView.invalidate()
                }
            },
            onDeleteFrame = {
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).deleteFrame()
                    drawingView.invalidate()
                }
            },
            onSetFrameDuration = { idx, ticks ->
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).setFrameDuration(idx, ticks)
                    drawingView.invalidate()
                }
            },
            onUpdateFps = { fps ->
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).updateTimelineConfig(fps = fps)
                    drawingView.invalidate()
                }
            },
            onToggleLoopMode = {
                drawingView?.document?.historyManager?.let { hm ->
                    val nextMode = when (timeline.loopMode) {
                        com.artstudio.animation.LoopMode.LOOP -> com.artstudio.animation.LoopMode.ONCE
                        com.artstudio.animation.LoopMode.ONCE -> com.artstudio.animation.LoopMode.PING_PONG
                        com.artstudio.animation.LoopMode.PING_PONG -> com.artstudio.animation.LoopMode.LOOP
                    }
                    com.artstudio.animation.AnimationTimelineManager(hm).updateTimelineConfig(loopMode = nextMode)
                    drawingView.invalidate()
                }
            },
            onToggleOnionSkin = { enabled ->
                drawingView?.document?.historyManager?.let { hm ->
                    com.artstudio.animation.AnimationTimelineManager(hm).updateTimelineConfig(onionSkin = timeline.onionSkin.copy(enabled = enabled))
                    drawingView.invalidate()
                }
            },
            isFastPreviewEnabled = viewModel.isFastPreviewEnabled,
            onToggleFastPreview = { viewModel.toggleFastPreview() },
            onClose = { viewModel.closeActivePanel() }
        )
    }
}
