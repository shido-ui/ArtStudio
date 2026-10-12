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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.artstudio.core.BrushStroke
import com.artstudio.ui.ArtStudioDrawingView
import com.artstudio.ui.components.StudioSlider
import com.artstudio.ui.model.ActivePanel
import com.artstudio.ui.model.StudioTool
import com.artstudio.ui.theme.StudioTheme
import com.artstudio.ui.viewmodel.StudioViewModel

/**
 * Encapsulates the hardware-accelerated ArtStudioDrawingView surface and HUD sliders.
 * Strictly under 150 lines.
 */
@Composable
fun StudioCanvasContainer(
    viewModel: StudioViewModel,
    onDrawingViewCreated: (ArtStudioDrawingView) -> Unit,
    onMetricsUpdated: (renderTimeMs: Float, strokeCount: Int, zoomLevel: Float) -> Unit,
    onStrokeCommitted: (BrushStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .semantics { contentDescription = "Canvas drawing viewport" }
    ) {
        // Native Drawing View Surface
        AndroidView(
            factory = { ctx ->
                ArtStudioDrawingView(ctx).apply {
                    this.onMetricsUpdated = onMetricsUpdated
                    this.onStrokeCommitted = onStrokeCommitted
                    this.onColorPicked = { argb ->
                        viewModel.selectColor(com.artstudio.color.ColorData.fromArgb(argb))
                    }
                    onDrawingViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.isEyedropperActive = (viewModel.activeTool == StudioTool.EYEDROPPER)
                view.isFillToolActive = (viewModel.activeTool == StudioTool.FILL || viewModel.activePanel == ActivePanel.Fill)
                view.fillConfig = viewModel.fillConfig.copy(fillColor = viewModel.colorState.activeColor.argbInt)
                view.isPixelOpsActive = (viewModel.activeTool == StudioTool.SMUDGE_BLUR || viewModel.activePanel == ActivePanel.PixelOps)
                view.pixelOpsConfig = viewModel.pixelOpsConfig
                view.isSelectionToolActive = (viewModel.activeTool == StudioTool.SELECTION || viewModel.activePanel == ActivePanel.Selection)
                view.drawingAidsConfig = viewModel.drawingAidsConfig
            }
        )

        // Floating Quick Brush Size & Opacity HUD Sliders
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = spacing.s)
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(colors.surfaceElevated.copy(alpha = 0.90f))
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
                .padding(spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            StudioSlider(
                value = viewModel.brushConfig.size,
                onValueChange = { viewModel.updateBrushSize(it) },
                valueRange = 1f..250f,
                label = "Size",
                valueFormatter = { "${it.toInt()}px" },
                modifier = Modifier.width(110.dp)
            )

            StudioSlider(
                value = viewModel.brushConfig.opacity,
                onValueChange = { viewModel.updateBrushOpacity(it) },
                valueRange = 0.05f..1f,
                label = "Opacity",
                valueFormatter = { "${(it * 100f).toInt()}%" },
                modifier = Modifier.width(110.dp)
            )
        }
    }
}
