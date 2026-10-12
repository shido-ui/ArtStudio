package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artstudio.cv.CvDebugOverlayMode
import com.artstudio.cv.CvPipelineParameters
import com.artstudio.cv.CvPipelineResult
import com.artstudio.cv.DenoiseMode
import com.artstudio.cv.EdgeMode
import com.artstudio.cv.ThresholdMode
import com.artstudio.ui.theme.StudioTheme

/**
 * Interactive Jetpack Compose panel for toggling and parameterizing the deterministic CV debug overlay.
 * Guaranteed to operate as a transient overlay that never becomes part of the artwork.
 */
@Composable
fun StudioCvDebugOverlayPanel(
    isOverlayActive: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
    activeMode: CvDebugOverlayMode,
    onSelectMode: (CvDebugOverlayMode) -> Unit,
    parameters: CvPipelineParameters,
    onParametersChanged: (CvPipelineParameters) -> Unit,
    lastResult: CvPipelineResult? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceBase)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
            .padding(spacing.m)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Toggle overlay switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CV Analysis Debug Overlay",
                        style = typography.titleSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Transient overlay (does not modify artwork)",
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )
                }

                Switch(
                    checked = isOverlayActive,
                    onCheckedChange = onToggleOverlay,
                    modifier = Modifier.semantics {
                        contentDescription = "Toggle CV Debug Overlay"
                    }
                )
            }

            if (isOverlayActive) {
                Spacer(modifier = Modifier.height(spacing.m))

                // Visualization Mode Chips
                Text(
                    text = "Display Mode",
                    style = typography.labelLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(spacing.xs))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    CvDebugOverlayMode.entries.forEach { mode ->
                        val isSelected = (mode == activeMode)
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(cornerRadius.s))
                                .background(if (isSelected) colors.accent.primary else colors.surfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accent.primary else colors.outlineSubtle,
                                    RoundedCornerShape(cornerRadius.s)
                                )
                                .clickable { onSelectMode(mode) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .semantics {
                                    role = Role.RadioButton
                                    contentDescription = "Switch to ${mode.name} overlay"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.name.replace("_", " "),
                                fontSize = 10.sp,
                                color = if (isSelected) colors.accent.onPrimary else colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.m))

                // Parameter Controls
                Text(
                    text = "Pipeline Parameters",
                    style = typography.labelLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                // 1. Denoise Selector
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(text = "Denoise Filter: ${parameters.denoiseMode.name}", style = typography.bodySmall, color = colors.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    DenoiseMode.entries.forEach { dMode ->
                        val isSel = (dMode == parameters.denoiseMode)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(cornerRadius.xs))
                                .background(if (isSel) colors.accent.primary.copy(alpha = 0.2f) else colors.surfaceElevated)
                                .border(1.dp, if (isSel) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.xs))
                                .clickable { onParametersChanged(parameters.copy(denoiseMode = dMode)) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dMode.name.substring(0, minOf(7, dMode.name.length)),
                                fontSize = 10.sp,
                                color = if (isSel) colors.accent.primary else colors.textSecondary,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 2. Edge Algorithm Selector
                Spacer(modifier = Modifier.height(spacing.s))
                Text(text = "Edge Detector: ${parameters.edgeMode.name}", style = typography.bodySmall, color = colors.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    EdgeMode.entries.forEach { eMode ->
                        val isSel = (eMode == parameters.edgeMode)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(cornerRadius.xs))
                                .background(if (isSel) colors.accent.primary.copy(alpha = 0.2f) else colors.surfaceElevated)
                                .border(1.dp, if (isSel) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.xs))
                                .clickable { onParametersChanged(parameters.copy(edgeMode = eMode)) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = eMode.name,
                                fontSize = 11.sp,
                                color = if (isSel) colors.accent.primary else colors.textSecondary,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 3. Canny High Threshold Slider
                if (parameters.edgeMode == EdgeMode.CANNY) {
                    Spacer(modifier = Modifier.height(spacing.s))
                    Text(
                        text = "Canny High Threshold: ${parameters.cannyHighThreshold.toInt()}",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Slider(
                        value = parameters.cannyHighThreshold,
                        onValueChange = { onParametersChanged(parameters.copy(cannyHighThreshold = it)) },
                        valueRange = 40f..180f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Contour Simplification Epsilon
                Spacer(modifier = Modifier.height(spacing.s))
                Text(
                    text = "Contour Decimation Epsilon: ${"%.1f".format(parameters.contourRdpEpsilon)} px",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
                Slider(
                    value = parameters.contourRdpEpsilon,
                    onValueChange = { onParametersChanged(parameters.copy(contourRdpEpsilon = it)) },
                    valueRange = 0.5f..5.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                // Stats Section
                if (lastResult != null) {
                    Spacer(modifier = Modifier.height(spacing.m))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cornerRadius.s))
                            .background(colors.surfaceElevated)
                            .padding(spacing.s)
                    ) {
                        Column {
                            Text(text = "Execution Metrics", style = typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Contours Extracted: ${lastResult.contours.size}", style = typography.bodySmall, color = colors.textSecondary)
                            Text(text = "Latency: ${lastResult.elapsedTimeMs} ms", style = typography.bodySmall, color = colors.textSecondary)
                            Text(text = "Peak Memory: ${"%.1f".format(lastResult.peakMemoryBytes / (1024 * 1024f))} MB", style = typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.m))

            // Dismiss Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(cornerRadius.m))
                    .background(colors.surfaceElevated)
                    .clickable(onClick = onDismissRequest)
                    .padding(vertical = 10.dp)
                    .semantics { role = Role.Button; contentDescription = "Close Debug Overlay Panel" },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Close Panel", style = typography.bodyMedium, color = colors.textPrimary)
            }
        }
    }
}
