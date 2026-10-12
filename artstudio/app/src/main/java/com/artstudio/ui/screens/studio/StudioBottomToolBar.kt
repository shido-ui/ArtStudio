package com.artstudio.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.R
import com.artstudio.ui.components.StudioIconButton
import com.artstudio.ui.model.ActivePanel
import com.artstudio.ui.model.StudioTool
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioIcons
import com.artstudio.ui.theme.StudioTheme
import com.artstudio.ui.viewmodel.StudioViewModel

/**
 * Accessible bottom toolbar for mobile portrait canvas.
 * Exposes all 13 Studio tools with >= 48dp touch targets and vector iconography.
 * Strictly decomposed so every composable is well under 150 lines.
 */
@Composable
fun StudioBottomToolBar(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(colors.surfaceBase.copy(alpha = 0.95f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(topStart = cornerRadius.m, topEnd = cornerRadius.m))
            .horizontalScroll(scrollState)
            .padding(horizontal = spacing.s, vertical = spacing.xs)
            .semantics { contentDescription = "Studio Tools Toolbar" },
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomBarPrimaryTools(viewModel)
        Spacer(modifier = Modifier.width(spacing.xs))
        BottomBarSecondaryTools(viewModel)
    }
}

@Composable
private fun BottomBarPrimaryTools(viewModel: StudioViewModel) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_brush),
        active = viewModel.activeTool == StudioTool.BRUSH,
        onClick = {
            if (viewModel.activeTool == StudioTool.BRUSH) viewModel.togglePanel(ActivePanel.Brush)
            else viewModel.selectTool(StudioTool.BRUSH)
        },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Brush, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_eraser),
        active = viewModel.activeTool == StudioTool.ERASER,
        onClick = { viewModel.selectTool(StudioTool.ERASER) },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Eraser, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    Box(
        modifier = Modifier
            .size(StudioAccessibility.MinTouchTarget)
            .padding(spacing.xs)
            .clip(CircleShape)
            .background(Color(viewModel.colorState.activeColor.argbInt))
            .border(2.dp, colors.outlineStrong, CircleShape)
            .semantics { contentDescription = "Active color picker" }
    ) {
        StudioIconButton(
            contentDescription = stringResource(R.string.tool_color),
            active = viewModel.activePanel == ActivePanel.Color,
            onClick = { viewModel.togglePanel(ActivePanel.Color) },
            modifier = Modifier.fillMaxSize()
        ) { /* transparent over color */ }
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_eyedropper),
        active = viewModel.activeTool == StudioTool.EYEDROPPER,
        onClick = {
            if (viewModel.activeTool == StudioTool.EYEDROPPER) viewModel.selectTool(StudioTool.BRUSH)
            else viewModel.selectTool(StudioTool.EYEDROPPER)
        },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Eyedropper, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_fill),
        active = viewModel.activeTool == StudioTool.FILL || viewModel.activePanel == ActivePanel.Fill,
        onClick = {
            viewModel.selectTool(StudioTool.FILL)
            viewModel.togglePanel(ActivePanel.Fill)
        },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.FloodFill, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_pixel_ops),
        active = viewModel.activeTool == StudioTool.SMUDGE_BLUR || viewModel.activePanel == ActivePanel.PixelOps,
        onClick = {
            viewModel.selectTool(StudioTool.SMUDGE_BLUR)
            viewModel.togglePanel(ActivePanel.PixelOps)
        },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Smudge, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BottomBarSecondaryTools(viewModel: StudioViewModel) {
    StudioIconButton(
        contentDescription = stringResource(R.string.tool_filters),
        active = viewModel.activePanel == ActivePanel.Filters,
        onClick = { viewModel.togglePanel(ActivePanel.Filters) },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Filter, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_adjustments),
        active = viewModel.activePanel == ActivePanel.Adjustments,
        onClick = { viewModel.togglePanel(ActivePanel.Adjustments) },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Adjustment, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_vector),
        active = viewModel.activeTool == StudioTool.VECTOR || viewModel.activePanel == ActivePanel.Vector,
        onClick = {
            viewModel.selectTool(StudioTool.VECTOR)
            viewModel.togglePanel(ActivePanel.Vector)
        },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.VectorPath, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_shapes),
        active = viewModel.activeTool == StudioTool.SHAPES || viewModel.activePanel == ActivePanel.Shapes,
        onClick = {
            viewModel.selectTool(StudioTool.SHAPES)
            viewModel.togglePanel(ActivePanel.Shapes)
        },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Shapes, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_text),
        active = viewModel.activeTool == StudioTool.TEXT || viewModel.activePanel == ActivePanel.Text,
        onClick = {
            viewModel.selectTool(StudioTool.TEXT)
            viewModel.togglePanel(ActivePanel.Text)
        },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Text, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_comic),
        active = viewModel.activePanel == ActivePanel.Comic,
        onClick = { viewModel.togglePanel(ActivePanel.Comic) },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Comic, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_timeline),
        active = viewModel.activePanel == ActivePanel.Timeline,
        onClick = { viewModel.togglePanel(ActivePanel.Timeline) },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Timeline, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_selection),
        active = viewModel.activeTool == StudioTool.SELECTION || viewModel.activePanel == ActivePanel.Selection,
        onClick = {
            viewModel.selectTool(StudioTool.SELECTION)
            viewModel.togglePanel(ActivePanel.Selection)
        },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Selection, null, tint = tint, modifier = Modifier.size(20.dp))
    }

    StudioIconButton(
        contentDescription = stringResource(R.string.tool_transform),
        active = viewModel.activeTool == StudioTool.TRANSFORM || viewModel.activePanel == ActivePanel.Transform,
        onClick = {
            viewModel.selectTool(StudioTool.TRANSFORM)
            viewModel.togglePanel(ActivePanel.Transform)
        },
        modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
    ) { tint ->
        androidx.compose.material3.Icon(StudioIcons.Transform, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
