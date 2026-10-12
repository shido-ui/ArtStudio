package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioTheme
import com.artstudio.vector.StrokeCap
import com.artstudio.vector.StrokeJoin
import com.artstudio.vector.VectorStyle
import com.artstudio.vector.VectorToolMode

/**
 * Accessible configuration drawer panel for Vector Path & Shape editing mode.
 * Provides controls for:
 * - Active Vector Tool: Select, Anchor Edit, Pen, Line, Rectangle, Ellipse, Polygon
 * - Stroke Width Slider (0.5 to 64 px)
 * - Stroke Cap: Butt, Round, Square
 * - Stroke Join: Miter, Round, Bevel
 * - Anchor Actions: Toggle Smooth, Delete Anchor
 * - Explicit Rasterization Action: Convert Vector Layer to Raster
 */
@Composable
fun StudioVectorPanel(
    currentTool: VectorToolMode,
    onToolSelected: (VectorToolMode) -> Unit,
    activeStyle: VectorStyle,
    onStyleChanged: (VectorStyle) -> Unit,
    hasSelectedAnchor: Boolean = false,
    isAnchorSmooth: Boolean = false,
    onToggleSmooth: () -> Unit = {},
    onDeleteAnchor: () -> Unit = {},
    onRasterizeRequested: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .width(320.dp)
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated.copy(alpha = 0.95f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
            .padding(spacing.m)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Vector Geometry Tools",
                style = typography.titleSmall,
                color = colors.textPrimary
            )
            StudioButton(
                text = "Done",
                onClick = onDismissRequest,
                modifier = Modifier.height(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Tool Mode Selector
        Text(
            text = "Active Vector Mode",
            style = typography.labelMedium,
            color = colors.textSecondary
        )
        val row1Tools = listOf(
            VectorToolMode.SELECT,
            VectorToolMode.ANCHOR_EDIT,
            VectorToolMode.PEN,
            VectorToolMode.LINE
        )
        val row2Tools = listOf(
            VectorToolMode.RECTANGLE,
            VectorToolMode.ELLIPSE,
            VectorToolMode.POLYGON
        )

        StudioSegmentedSelector(
            options = row1Tools,
            selectedOption = if (currentTool in row1Tools) currentTool else row1Tools.first(),
            onOptionSelected = onToolSelected,
            optionLabel = { it.label }
        )
        StudioSegmentedSelector(
            options = row2Tools,
            selectedOption = if (currentTool in row2Tools) currentTool else row2Tools.first(),
            onOptionSelected = onToolSelected,
            optionLabel = { it.label }
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        // Stroke Width Slider
        StudioSlider(
            value = activeStyle.strokeWidth,
            onValueChange = { w -> onStyleChanged(activeStyle.copy(strokeWidth = w)) },
            valueRange = 0.5f..64.0f,
            label = "Stroke Width",
            valueFormatter = { "${it.toInt()} px" }
        )

        // Stroke Cap Selector
        Text(
            text = "Stroke Cap",
            style = typography.labelMedium,
            color = colors.textSecondary
        )
        val capOptions = listOf(StrokeCap.BUTT, StrokeCap.ROUND, StrokeCap.SQUARE)
        StudioSegmentedSelector(
            options = capOptions,
            selectedOption = activeStyle.strokeCap,
            onOptionSelected = { cap -> onStyleChanged(activeStyle.copy(strokeCap = cap)) },
            optionLabel = { it.id.replaceFirstChar { c -> c.uppercase() } }
        )

        // Stroke Join Selector
        Text(
            text = "Stroke Join",
            style = typography.labelMedium,
            color = colors.textSecondary
        )
        val joinOptions = listOf(StrokeJoin.MITER, StrokeJoin.ROUND, StrokeJoin.BEVEL)
        StudioSegmentedSelector(
            options = joinOptions,
            selectedOption = activeStyle.strokeJoin,
            onOptionSelected = { join -> onStyleChanged(activeStyle.copy(strokeJoin = join)) },
            optionLabel = { it.id.replaceFirstChar { c -> c.uppercase() } }
        )

        // Anchor Editing Actions (enabled when an anchor is selected)
        if (hasSelectedAnchor) {
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                text = "Selected Anchor",
                style = typography.labelMedium,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                StudioButton(
                    text = if (isAnchorSmooth) "Make Corner" else "Make Smooth",
                    onClick = onToggleSmooth,
                    modifier = Modifier.weight(1f)
                )
                StudioButton(
                    text = "Delete",
                    onClick = onDeleteAnchor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // Explicit Rasterization Command
        Text(
            text = "Destructive Conversion",
            style = typography.labelMedium,
            color = colors.textSecondary
        )
        StudioButton(
            text = "Rasterize Vector Layer",
            onClick = onRasterizeRequested,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
