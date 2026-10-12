package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artstudio.deformation.DeformToolMode
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

enum class LiquifySubMode(val displayName: String) {
    PUSH("Push"),
    EXPAND("Expand"),
    PINCH("Pinch"),
    TWIRL_CW("Twirl CW"),
    TWIRL_CCW("Twirl CCW"),
    RECONSTRUCT("Reconstruct")
}

/**
 * Interactive Deformation panel providing:
 * - Mode toggle: Mesh Warp vs Liquify
 * - Liquify brush modes (Push, Expand, Pinch, Twirl, Reconstruct)
 * - Radius and Strength sliders
 * - Commit, Reset, and Cancel actions
 * Split into modular composables strictly <= 150 lines.
 */
@Composable
fun StudioDeformationPanel(
    toolMode: DeformToolMode,
    onToolModeChanged: (DeformToolMode) -> Unit,
    liquifySubMode: LiquifySubMode,
    onLiquifySubModeChanged: (LiquifySubMode) -> Unit,
    brushRadius: Float,
    onBrushRadiusChanged: (Float) -> Unit,
    brushStrength: Float,
    onBrushStrengthChanged: (Float) -> Unit,
    isDirty: Boolean,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = cornerRadius.l, topEnd = cornerRadius.l))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(topStart = cornerRadius.l, topEnd = cornerRadius.l)),
        color = colors.surfaceElevated
    ) {
        Column(
            modifier = Modifier
                .padding(spacing.m)
                .fillMaxWidth()
        ) {
            DeformationHeader(isDirty = isDirty, onReset = onReset, onCancel = onCancel)
            Spacer(modifier = Modifier.height(spacing.s))
            DeformationModeSelector(toolMode = toolMode, onToolModeChanged = onToolModeChanged)
            Spacer(modifier = Modifier.height(spacing.s))
            if (toolMode != DeformToolMode.WARP_MESH) {
                LiquifySubModeSelector(liquifySubMode = liquifySubMode, onLiquifySubModeChanged = onLiquifySubModeChanged)
                Spacer(modifier = Modifier.height(spacing.s))
                LiquifySliders(
                    brushRadius = brushRadius,
                    onBrushRadiusChanged = onBrushRadiusChanged,
                    brushStrength = brushStrength,
                    onBrushStrengthChanged = onBrushStrengthChanged
                )
            } else {
                Text(
                    text = "Drag grid control points on the canvas to warp layer geometry.",
                    style = StudioTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = spacing.s)
                )
            }
            Spacer(modifier = Modifier.height(spacing.s))
            StudioButton(
                text = "Commit Deformation",
                onClick = onCommit,
                variant = StudioButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth().height(StudioAccessibility.MinTouchTarget)
            )
        }
    }
}

@Composable
private fun DeformationHeader(
    isDirty: Boolean,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Deformation & Warp",
            style = typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StudioButton(
                text = "Reset",
                onClick = onReset,
                enabled = isDirty,
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.height(StudioAccessibility.MinTouchTarget)
            )
            StudioButton(
                text = "Cancel",
                onClick = onCancel,
                variant = StudioButtonVariant.Ghost,
                modifier = Modifier.height(StudioAccessibility.MinTouchTarget)
            )
        }
    }
}

@Composable
private fun DeformationModeSelector(
    toolMode: DeformToolMode,
    onToolModeChanged: (DeformToolMode) -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        listOf(DeformToolMode.WARP_MESH to "Mesh Warp", DeformToolMode.LIQUIFY_PUSH to "Liquify").forEach { (mode, label) ->
            val isSelected = (toolMode == mode) || (mode == DeformToolMode.LIQUIFY_PUSH && toolMode != DeformToolMode.WARP_MESH)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(StudioAccessibility.MinTouchTarget)
                    .clip(RoundedCornerShape(cornerRadius.m))
                    .background(if (isSelected) colors.accent.primary else colors.surfaceBase)
                    .clickable { onToolModeChanged(mode) }
                    .padding(horizontal = spacing.m, vertical = spacing.xs)
                    .semantics { contentDescription = "Mode $label" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) colors.accent.onPrimary else colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun LiquifySubModeSelector(
    liquifySubMode: LiquifySubMode,
    onLiquifySubModeChanged: (LiquifySubMode) -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(LiquifySubMode.entries, key = { it.name }) { sub ->
            val isSelected = sub == liquifySubMode
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(if (isSelected) colors.accent.container else colors.surfaceBase)
                    .clickable { onLiquifySubModeChanged(sub) }
                    .padding(horizontal = spacing.s, vertical = spacing.xs)
                    .semantics { contentDescription = "Liquify ${sub.displayName}" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sub.displayName,
                    style = typography.bodySmall,
                    color = if (isSelected) colors.accent.onContainer else colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun LiquifySliders(
    brushRadius: Float,
    onBrushRadiusChanged: (Float) -> Unit,
    brushStrength: Float,
    onBrushStrengthChanged: (Float) -> Unit
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Radius (${brushRadius.toInt()}px)", style = typography.bodySmall, color = colors.textSecondary, modifier = Modifier.width(100.dp))
        Slider(value = brushRadius, onValueChange = onBrushRadiusChanged, valueRange = 10f..300f, modifier = Modifier.weight(1f).height(StudioAccessibility.MinTouchTarget))
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Strength (${(brushStrength * 100).toInt()}%)", style = typography.bodySmall, color = colors.textSecondary, modifier = Modifier.width(100.dp))
        Slider(value = brushStrength, onValueChange = onBrushStrengthChanged, valueRange = 0.05f..1.0f, modifier = Modifier.weight(1f).height(StudioAccessibility.MinTouchTarget))
    }
}
