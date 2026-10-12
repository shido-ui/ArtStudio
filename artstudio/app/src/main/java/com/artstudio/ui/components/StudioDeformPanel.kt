package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artstudio.deformation.DeformInterpolation
import com.artstudio.deformation.DeformToolMode
import com.artstudio.deformation.DeformationSession
import com.artstudio.deformation.EdgeBehavior
import com.artstudio.ui.theme.StudioTheme

/**
 * Interactive deformation controls panel for mesh/warp and brush-based liquify workflows.
 * Operates on a duplicated preview surface; requires user confirmation before committing.
 */
@Composable
fun StudioDeformPanel(
    session: DeformationSession,
    onToolModeChange: (DeformToolMode) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onInterpolationChange: (DeformInterpolation) -> Unit,
    onEdgeBehaviorChange: (EdgeBehavior) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onConfirmCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceBase, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
            .testTag("studio_deform_panel")
    ) {
        // --- 1. Header & Title ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Advanced Deformation",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium
            )

            // Adaptive preview resolution badge
            if (session.isAdaptivePreview) {
                Surface(
                    color = colors.accent.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Preview: ${session.previewWidth}x${session.previewHeight}",
                        color = colors.accent.primary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 2. Tool Mode Selector ---
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tools = listOf(
                Pair(DeformToolMode.WARP_MESH, "Warp Mesh"),
                Pair(DeformToolMode.LIQUIFY_PUSH, "Push"),
                Pair(DeformToolMode.LIQUIFY_EXPAND, "Expand"),
                Pair(DeformToolMode.LIQUIFY_PINCH, "Pinch"),
                Pair(DeformToolMode.LIQUIFY_TWIRL_CW, "Twirl CW"),
                Pair(DeformToolMode.LIQUIFY_RECONSTRUCT, "Reconstruct")
            )

            tools.forEach { (mode, label) ->
                val isSelected = session.activeToolMode == mode
                StudioButton(
                    text = label,
                    onClick = { onToolModeChange(mode) },
                    variant = if (isSelected) StudioButtonVariant.Primary else StudioButtonVariant.Secondary,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 3. Brush Sliders (Only visible for Liquify tools) ---
        if (session.activeToolMode != DeformToolMode.WARP_MESH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Radius: ${session.brush.radius.toInt()} px",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "Strength: ${(session.brush.strength * 100).toInt()}%",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            Slider(
                value = session.brush.radius,
                onValueChange = onRadiusChange,
                valueRange = 10f..200f,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Deformation Brush Radius" }
            )

            Slider(
                value = session.brush.strength,
                onValueChange = onStrengthChange,
                valueRange = 0.05f..1.0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Deformation Brush Strength" }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- 4. Interpolation & Edge Selectors ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interpolation toggle
            StudioButton(
                text = "Filter: ${session.config.interpolation.name.take(4)}",
                onClick = {
                    val next = when (session.config.interpolation) {
                        DeformInterpolation.BILINEAR -> DeformInterpolation.BICUBIC
                        DeformInterpolation.BICUBIC -> DeformInterpolation.NEAREST_NEIGHBOR
                        DeformInterpolation.NEAREST_NEIGHBOR -> DeformInterpolation.BILINEAR
                    }
                    onInterpolationChange(next)
                },
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
            )

            // Edge Behavior toggle
            StudioButton(
                text = "Edge: ${session.config.edgeBehavior.name.take(5)}",
                onClick = {
                    val next = when (session.config.edgeBehavior) {
                        EdgeBehavior.CLAMP_TO_EDGE -> EdgeBehavior.TRANSPARENT_ZERO
                        EdgeBehavior.TRANSPARENT_ZERO -> EdgeBehavior.WRAP
                        EdgeBehavior.WRAP -> EdgeBehavior.CLAMP_TO_EDGE
                    }
                    onEdgeBehaviorChange(next)
                },
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 5. Action Row: Reset, Cancel, Commit ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioButton(
                text = "Reset",
                onClick = onReset,
                variant = StudioButtonVariant.Secondary,
                enabled = session.isDirty,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
            )

            StudioButton(
                text = "Cancel",
                onClick = onCancel,
                variant = StudioButtonVariant.Ghost,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
            )

            StudioButton(
                text = "Commit",
                onClick = { showConfirmDialog = true },
                variant = StudioButtonVariant.Primary,
                enabled = session.isDirty,
                modifier = Modifier.weight(1.2f).defaultMinSize(minHeight = 48.dp)
            )
        }
    }

    // --- 6. Explicit Confirmation Modal ---
    if (showConfirmDialog) {
        StudioDialog(
            title = "Commit Deformation",
            body = "Commit resampled deformation to the active layer? This operation is fully reversible via Edit Undo.",
            confirmText = "Commit",
            cancelText = "Keep Editing",
            onConfirm = {
                showConfirmDialog = false
                onConfirmCommit()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}
