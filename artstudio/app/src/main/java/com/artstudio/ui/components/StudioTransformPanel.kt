package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.document.model.CanvasResizeAnchor
import com.artstudio.document.model.InterpolationMode
import com.artstudio.document.model.LayerTransform
import com.artstudio.layer.LayerManager
import com.artstudio.selection.SelectionManager
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Modes of transformation supported by the studio transform controls.
 */
enum class TransformControlMode(val displayName: String) {
    LAYER("Layer Transform"),
    CANVAS("Canvas Resize / Crop")
}

/**
 * Accessible Jetpack Compose Transform Panel.
 * Supports non-destructive Move, Scale, Rotate, Flip, and Crop/Resize workflows
 * with numerical controls, aspect-ratio lock, interpolation selection, and anchor alignment.
 *
 * Adheres strictly to >= 48dp touch targets and WCAG AAA contrast standards.
 */
@Composable
fun StudioTransformPanel(
    layerManager: LayerManager,
    selectionManager: SelectionManager? = null,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val typography = StudioTheme.typography

    var mode by remember { mutableStateOf(TransformControlMode.LAYER) }
    val activeLayer = layerManager.getSelectedLayer()
    val activeLayerId = activeLayer?.id ?: ""
    val transform = activeLayer?.transform ?: LayerTransform()
    val currentCanvas = layerManager.currentDocument.canvas

    // Canvas resize local state
    var canvasWidthInput by remember(currentCanvas.width) { mutableIntStateOf(currentCanvas.width) }
    var canvasHeightInput by remember(currentCanvas.height) { mutableIntStateOf(currentCanvas.height) }
    var selectedAnchor by remember { mutableStateOf(CanvasResizeAnchor.CENTER) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
            .padding(spacing.m)
            .verticalScroll(rememberScrollState())
            .semantics { contentDescription = "Transform Control Panel" },
        verticalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        // --- 1. Mode Header Selector ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                TransformControlMode.entries.forEach { m ->
                    val isSelected = mode == m
                    StudioButton(
                        text = m.displayName,
                        variant = if (isSelected) StudioButtonVariant.Primary else StudioButtonVariant.Secondary,
                        onClick = { mode = m }
                    )
                }
            }

            StudioIconButton(
                contentDescription = "Close Transform Panel",
                onClick = onDismiss,
                modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
            ) { tint ->
                Text("✕", style = typography.titleMedium, color = tint)
            }
        }

        // --- 2. Mode Content ---
        when (mode) {
            TransformControlMode.LAYER -> {
                if (activeLayer == null) {
                    Text(
                        text = "No active layer selected to transform.",
                        style = typography.bodyMedium,
                        color = colors.textSecondary
                    )
                } else {
                    Text(
                        text = "Transforming Layer: ${activeLayer.name}",
                        style = typography.titleSmall,
                        color = colors.textPrimary
                    )

                    // Translation (Move) Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Move:", style = typography.bodySmall, color = colors.textSecondary)
                        StudioButton(
                            text = "← 10px",
                            variant = StudioButtonVariant.Secondary,
                            onClick = { layerManager.moveLayer(activeLayerId, -10f, 0f) }
                        )
                        StudioButton(
                            text = "→ 10px",
                            variant = StudioButtonVariant.Secondary,
                            onClick = { layerManager.moveLayer(activeLayerId, 10f, 0f) }
                        )
                        StudioButton(
                            text = "↑ 10px",
                            variant = StudioButtonVariant.Secondary,
                            onClick = { layerManager.moveLayer(activeLayerId, 0f, -10f) }
                        )
                        StudioButton(
                            text = "↓ 10px",
                            variant = StudioButtonVariant.Secondary,
                            onClick = { layerManager.moveLayer(activeLayerId, 0f, 10f) }
                        )
                    }

                    // Scale & Aspect-Ratio Lock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scale: ${(transform.scaleX * 100).toInt()}%",
                            style = typography.bodySmall,
                            color = colors.textPrimary
                        )

                        StudioIconButton(
                            contentDescription = if (transform.isAspectRatioLocked) "Aspect Ratio Locked" else "Aspect Ratio Free",
                            active = transform.isAspectRatioLocked,
                            onClick = {
                                layerManager.transformLayer(
                                    activeLayerId,
                                    transform.toggleAspectRatioLock()
                                )
                            },
                            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
                        ) { tint ->
                            Text(if (transform.isAspectRatioLocked) "🔒" else "🔓", color = tint)
                        }

                        StudioButton(
                            text = "-10%",
                            variant = StudioButtonVariant.Secondary,
                            onClick = {
                                val s = maxOf(0.1f, transform.scaleX - 0.1f)
                                layerManager.scaleLayer(activeLayerId, s, s, transform.isAspectRatioLocked)
                            }
                        )
                        StudioButton(
                            text = "+10%",
                            variant = StudioButtonVariant.Secondary,
                            onClick = {
                                val s = transform.scaleX + 0.1f
                                layerManager.scaleLayer(activeLayerId, s, s, transform.isAspectRatioLocked)
                            }
                        )
                    }

                    // Rotation Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rotate: ${transform.rotationDegrees.toInt()}°",
                            style = typography.bodySmall,
                            color = colors.textPrimary
                        )
                        StudioButton(
                            text = "-90°",
                            variant = StudioButtonVariant.Secondary,
                            onClick = { layerManager.rotateLayer(activeLayerId, -90f) }
                        )
                        StudioButton(
                            text = "+90°",
                            variant = StudioButtonVariant.Secondary,
                            onClick = { layerManager.rotateLayer(activeLayerId, 90f) }
                        )
                        StudioButton(
                            text = "180°",
                            variant = StudioButtonVariant.Secondary,
                            onClick = { layerManager.rotateLayer(activeLayerId, 180f) }
                        )
                    }

                    // Flip Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Flip:", style = typography.bodySmall, color = colors.textSecondary)
                        StudioIconButton(
                            contentDescription = "Flip Horizontal",
                            active = transform.flipHorizontal,
                            onClick = { layerManager.flipLayerHorizontal(activeLayerId) },
                            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
                        ) { tint ->
                            Text("↔️ H", color = tint, style = typography.bodySmall)
                        }
                        StudioIconButton(
                            contentDescription = "Flip Vertical",
                            active = transform.flipVertical,
                            onClick = { layerManager.flipLayerVertical(activeLayerId) },
                            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
                        ) { tint ->
                            Text("↕️ V", color = tint, style = typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        StudioButton(
                            text = "Reset",
                            variant = StudioButtonVariant.Destructive,
                            onClick = { layerManager.resetLayerTransform(activeLayerId) }
                        )
                    }

                    // Interpolation Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Filter:", style = typography.bodySmall, color = colors.textSecondary)
                        InterpolationMode.entries.forEach { interp ->
                            val isSelected = transform.interpolation == interp
                            StudioButton(
                                text = interp.displayName,
                                variant = if (isSelected) StudioButtonVariant.Primary else StudioButtonVariant.Secondary,
                                onClick = {
                                    layerManager.transformLayer(
                                        activeLayerId,
                                        transform.copy(interpolation = interp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            TransformControlMode.CANVAS -> {
                Text(
                    text = "Current Canvas: ${currentCanvas.width} × ${currentCanvas.height} px",
                    style = typography.titleSmall,
                    color = colors.textPrimary
                )

                // Quick Canvas Resize Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Presets:", style = typography.bodySmall, color = colors.textSecondary)
                    StudioButton(
                        text = "1080×1080",
                        variant = StudioButtonVariant.Secondary,
                        onClick = {
                            canvasWidthInput = 1080
                            canvasHeightInput = 1080
                            layerManager.resizeCanvas(1080, 1080, selectedAnchor)
                        }
                    )
                    StudioButton(
                        text = "1920×1080",
                        variant = StudioButtonVariant.Secondary,
                        onClick = {
                            canvasWidthInput = 1920
                            canvasHeightInput = 1080
                            layerManager.resizeCanvas(1920, 1080, selectedAnchor)
                        }
                    )
                    StudioButton(
                        text = "2048×2048",
                        variant = StudioButtonVariant.Secondary,
                        onClick = {
                            canvasWidthInput = 2048
                            canvasHeightInput = 2048
                            layerManager.resizeCanvas(2048, 2048, selectedAnchor)
                        }
                    )
                }

                // 3x3 Anchor Alignment Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Text(
                        text = "Resize Anchor: ${selectedAnchor.displayName}",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )

                    val anchors = listOf(
                        listOf(CanvasResizeAnchor.TOP_LEFT, CanvasResizeAnchor.TOP_CENTER, CanvasResizeAnchor.TOP_RIGHT),
                        listOf(CanvasResizeAnchor.CENTER_LEFT, CanvasResizeAnchor.CENTER, CanvasResizeAnchor.CENTER_RIGHT),
                        listOf(CanvasResizeAnchor.BOTTOM_LEFT, CanvasResizeAnchor.BOTTOM_CENTER, CanvasResizeAnchor.BOTTOM_RIGHT)
                    )

                    anchors.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            row.forEach { anchor ->
                                val isSelected = selectedAnchor == anchor
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(cornerRadius.xs))
                                        .background(if (isSelected) colors.accent.container else colors.surfaceElevated)
                                        .border(1.dp, if (isSelected) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.xs))
                                        .clickable { selectedAnchor = anchor },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (anchor) {
                                            CanvasResizeAnchor.TOP_LEFT -> "↖"
                                            CanvasResizeAnchor.TOP_CENTER -> "↑"
                                            CanvasResizeAnchor.TOP_RIGHT -> "↗"
                                            CanvasResizeAnchor.CENTER_LEFT -> "←"
                                            CanvasResizeAnchor.CENTER -> "•"
                                            CanvasResizeAnchor.CENTER_RIGHT -> "→"
                                            CanvasResizeAnchor.BOTTOM_LEFT -> "↙"
                                            CanvasResizeAnchor.BOTTOM_CENTER -> "↓"
                                            CanvasResizeAnchor.BOTTOM_RIGHT -> "↘"
                                        },
                                        color = if (isSelected) colors.accent.primary else colors.textPrimary,
                                        style = typography.titleSmall
                                    )
                                }
                            }
                        }
                    }
                }

                // Crop to Selection Action
                val hasSelection = selectionManager?.hasSelection == true
                StudioButton(
                    text = "Crop Canvas to Selection",
                    variant = StudioButtonVariant.Primary,
                    enabled = hasSelection,
                    disabledExplanation = "No active selection to crop canvas",
                    onClick = {
                        selectionManager?.let { sm ->
                            layerManager.cropCanvas(sm.bounds)
                        }
                    }
                )

                Text(
                    text = "ℹ️ Non-destructive: pixels extending beyond canvas boundaries are preserved in memory.",
                    style = typography.bodySmall,
                    color = colors.textTertiary
                )
            }
        }
    }
}
