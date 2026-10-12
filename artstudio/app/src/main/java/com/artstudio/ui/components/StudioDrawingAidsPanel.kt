package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.artstudio.guides.*
import com.artstudio.ui.theme.StudioTheme

/**
 * Contextual panel exposing non-destructive Drawing Aids:
 * - Guides: Straight Line, Circle/Ellipse, Radial Rays, Perspective
 * - Snapping: Toggle & distance threshold slider
 * - Symmetry: Mirror Vertical, Mirror Horizontal, Dual, Radial with sector count
 * - Reference Image: Opacity, Move/Resize, Hide/Show, Color Sampling, and explicit "Import to Layer"
 */
@Composable
fun StudioDrawingAidsPanel(
    config: DrawingAidsConfig,
    onConfigChanged: (DrawingAidsConfig) -> Unit,
    referenceManager: ReferenceImageManager,
    onReferenceUpdated: () -> Unit = {},
    onImportReferenceRequested: (String) -> Unit = {},
    onPickReferenceImageRequested: (() -> Unit)? = null,
    onDismissRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    var selectedTab by remember { mutableStateOf(0) } // 0: Guides, 1: Symmetry, 2: Reference
    val tabs = listOf("Guides", "Symmetry", "Reference")

    Column(
        modifier = modifier
            .width(340.dp)
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated.copy(alpha = 0.96f))
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
                text = "Drawing Aids & Reference",
                style = typography.titleSmall,
                color = colors.textPrimary
            )
            StudioIconButton(
                contentDescription = "Close drawing aids panel",
                onClick = onDismissRequest,
                icon = { Text("✕", style = typography.titleSmall) }
            )
        }

        // Tab Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.s))
                .background(colors.surfaceBase)
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(if (isSelected) colors.accent.primary else colors.surfaceBase)
                        .clickable { selectedTab = index }
                        .padding(vertical = spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = typography.labelMedium,
                        color = if (isSelected) colors.surfaceBase else colors.textSecondary
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                // GUIDES TAB
                Text(
                    text = "Guide Mode",
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    GuideType.values().forEach { mode ->
                        val isSelected = config.guideType == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(cornerRadius.s))
                                .background(if (isSelected) colors.accent.primary else colors.surfaceBase)
                                .border(1.dp, if (isSelected) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                                .clickable { onConfigChanged(config.copy(guideType = mode)) }
                                .padding(vertical = spacing.xs),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (mode) {
                                    GuideType.NONE -> "Off"
                                    GuideType.STRAIGHT_LINE -> "Line"
                                    GuideType.CIRCLE_ELLIPSE -> "Ellipse"
                                    GuideType.RADIAL -> "Radial"
                                    GuideType.PERSPECTIVE -> "Persp"
                                },
                                style = typography.labelSmall,
                                color = if (isSelected) colors.surfaceBase else colors.textPrimary
                            )
                        }
                    }
                }

                // Snapping toggle & distance slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Magnetic Snapping",
                        style = typography.bodySmall,
                        color = colors.textPrimary
                    )
                    StudioButton(
                        text = if (config.snappingEnabled) "Enabled" else "Disabled",
                        variant = if (config.snappingEnabled) StudioButtonVariant.Primary else StudioButtonVariant.Secondary,
                        onClick = { onConfigChanged(config.copy(snappingEnabled = !config.snappingEnabled)) }
                    )
                }

                if (config.snappingEnabled && config.guideType != GuideType.NONE) {
                    StudioSlider(
                        value = config.snapDistance,
                        onValueChange = { onConfigChanged(config.copy(snapDistance = it)) },
                        valueRange = 8f..64f,
                        label = "Snap Distance: ${config.snapDistance.toInt()} px"
                    )
                }

                // Mode-specific parameters
                when (config.guideType) {
                    GuideType.STRAIGHT_LINE -> {
                        StudioSlider(
                            value = config.lineGuide.angleDegrees,
                            onValueChange = { angle ->
                                onConfigChanged(config.copy(lineGuide = config.lineGuide.copy(angleDegrees = angle)))
                            },
                            valueRange = -180f..180f,
                            label = "Line Angle: ${config.lineGuide.angleDegrees.toInt()}°"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            listOf(0f to "0° (H)", 45f to "45°", 90f to "90° (V)").forEach { (ang, txt) ->
                                StudioButton(
                                    text = txt,
                                    variant = StudioButtonVariant.Secondary,
                                    onClick = {
                                        onConfigChanged(config.copy(lineGuide = config.lineGuide.copy(angleDegrees = ang)))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    GuideType.CIRCLE_ELLIPSE -> {
                        StudioSlider(
                            value = config.ellipseGuide.radiusX,
                            onValueChange = { rx ->
                                onConfigChanged(config.copy(ellipseGuide = config.ellipseGuide.copy(radiusX = rx)))
                            },
                            valueRange = 50f..800f,
                            label = "Radius X: ${config.ellipseGuide.radiusX.toInt()} px"
                        )
                        StudioSlider(
                            value = config.ellipseGuide.radiusY,
                            onValueChange = { ry ->
                                onConfigChanged(config.copy(ellipseGuide = config.ellipseGuide.copy(radiusY = ry)))
                            },
                            valueRange = 50f..800f,
                            label = "Radius Y: ${config.ellipseGuide.radiusY.toInt()} px"
                        )
                        StudioButton(
                            text = "Make Circle (Lock Rx=Ry)",
                            variant = StudioButtonVariant.Secondary,
                            onClick = {
                                val avg = (config.ellipseGuide.radiusX + config.ellipseGuide.radiusY) / 2f
                                onConfigChanged(config.copy(ellipseGuide = config.ellipseGuide.copy(radiusX = avg, radiusY = avg)))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    GuideType.RADIAL -> {
                        StudioSlider(
                            value = config.radialGuide.rayCount.toFloat(),
                            onValueChange = { count ->
                                onConfigChanged(config.copy(radialGuide = config.radialGuide.copy(rayCount = count.toInt())))
                            },
                            valueRange = 4f..24f,
                            steps = 19,
                            label = "Ray Count: ${config.radialGuide.rayCount} rays"
                        )
                    }
                    GuideType.PERSPECTIVE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            PerspectiveType.values().forEach { pType ->
                                val isSelected = config.perspectiveGuide.type == pType
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(cornerRadius.s))
                                        .background(if (isSelected) colors.accent.primary else colors.surfaceBase)
                                        .clickable {
                                            onConfigChanged(config.copy(perspectiveGuide = config.perspectiveGuide.copy(type = pType)))
                                        }
                                        .padding(vertical = spacing.xs),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${pType.vanishingPointsCount}-Point",
                                        style = typography.labelSmall,
                                        color = if (isSelected) colors.surfaceBase else colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            1 -> {
                // SYMMETRY TAB
                Text(
                    text = "Symmetry Mode",
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    SymmetryType.values().forEach { sType ->
                        val isSelected = config.symmetry.type == sType
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(cornerRadius.s))
                                .background(if (isSelected) colors.accent.primary else colors.surfaceBase)
                                .border(1.dp, if (isSelected) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                                .clickable {
                                    onConfigChanged(config.copy(symmetry = config.symmetry.copy(type = sType)))
                                }
                                .padding(vertical = spacing.xs),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (sType) {
                                    SymmetryType.NONE -> "Off"
                                    SymmetryType.MIRROR_VERTICAL -> "Vert"
                                    SymmetryType.MIRROR_HORIZONTAL -> "Horiz"
                                    SymmetryType.MIRROR_DUAL -> "Dual"
                                    SymmetryType.RADIAL -> "Radial"
                                },
                                style = typography.labelSmall,
                                color = if (isSelected) colors.surfaceBase else colors.textPrimary
                            )
                        }
                    }
                }

                if (config.symmetry.type == SymmetryType.RADIAL) {
                    StudioSlider(
                        value = config.symmetry.radialSectors.toFloat(),
                        onValueChange = { sectors ->
                            onConfigChanged(config.copy(symmetry = config.symmetry.copy(radialSectors = sectors.toInt())))
                        },
                        valueRange = 2f..16f,
                        steps = 13,
                        label = "Sectors: ${config.symmetry.radialSectors}"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Symmetry Axes",
                        style = typography.bodySmall,
                        color = colors.textPrimary
                    )
                    StudioButton(
                        text = if (config.symmetry.showAxes) "Visible" else "Hidden",
                        variant = if (config.symmetry.showAxes) StudioButtonVariant.Primary else StudioButtonVariant.Secondary,
                        onClick = {
                            onConfigChanged(config.copy(symmetry = config.symmetry.copy(showAxes = !config.symmetry.showAxes)))
                        }
                    )
                }
            }

            2 -> {
                // REFERENCE IMAGE TAB
                val activeRef = referenceManager.activeReference
                if (activeRef == null) {
                    Text(
                        text = "No reference image loaded.",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                    if (onPickReferenceImageRequested != null) {
                        StudioButton(
                            text = "Import Reference from Device",
                            variant = StudioButtonVariant.Primary,
                            onClick = onPickReferenceImageRequested,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    StudioButton(
                        text = "Create Synthetic Reference",
                        variant = if (onPickReferenceImageRequested != null) StudioButtonVariant.Secondary else StudioButtonVariant.Primary,
                        onClick = {
                            // Add a sample test reference pattern
                            val testW = 256
                            val testH = 256
                            val pxs = IntArray(testW * testH) { idx ->
                                val x = idx % testW
                                val y = idx / testW
                                val r = (x * 255 / testW)
                                val g = (y * 255 / testH)
                                val b = 200
                                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            }
                            referenceManager.addReference(
                                ReferenceImageOverlay(
                                    name = "Sample Color Reference",
                                    x = 120f,
                                    y = 120f,
                                    width = 400f,
                                    height = 400f,
                                    opacity = 0.8f,
                                    visible = true,
                                    pixels = pxs,
                                    bitmapWidth = testW,
                                    bitmapHeight = testH
                                )
                            )
                            onReferenceUpdated()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeRef.name,
                            style = typography.bodyMedium,
                            color = colors.textPrimary
                        )
                        StudioButton(
                            text = if (activeRef.visible) "Visible" else "Hidden",
                            variant = if (activeRef.visible) StudioButtonVariant.Primary else StudioButtonVariant.Secondary,
                            onClick = {
                                referenceManager.toggleVisibility(activeRef.id)
                                onReferenceUpdated()
                            }
                        )
                    }

                    StudioSlider(
                        value = activeRef.opacity,
                        onValueChange = { op ->
                            referenceManager.updateOpacity(activeRef.id, op)
                            onReferenceUpdated()
                        },
                        valueRange = 0.05f..1.0f,
                        label = "Overlay Opacity: ${(activeRef.opacity * 100).toInt()}%"
                    )

                    Text(
                        text = "Bounds: (${activeRef.x.toInt()}, ${activeRef.y.toInt()}) [${activeRef.width.toInt()}×${activeRef.height.toInt()} px]",
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )

                    var sampledColorText by remember { mutableStateOf<String?>(null) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        StudioButton(
                            text = "Sample Color",
                            variant = StudioButtonVariant.Secondary,
                            onClick = {
                                val c = referenceManager.sampleColor(activeRef.id, activeRef.x + activeRef.width / 2f, activeRef.y + activeRef.height / 2f)
                                sampledColorText = c?.hexRgb ?: "Out of bounds"
                            },
                            modifier = Modifier.weight(1f)
                        )

                        StudioButton(
                            text = "Import to Layer",
                            variant = StudioButtonVariant.Primary,
                            onClick = {
                                onImportReferenceRequested(activeRef.id)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    sampledColorText?.let {
                        Text(
                            text = "Sampled Center: $it",
                            style = typography.labelSmall,
                            color = colors.accent.primary
                        )
                    }
                }
            }
        }
    }
}
