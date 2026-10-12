package com.artstudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.color.ColorData
import com.artstudio.color.GradientColorStop
import com.artstudio.color.GradientConfig
import com.artstudio.color.GradientTileMode
import com.artstudio.color.GradientType
import com.artstudio.ui.theme.StudioTheme

/**
 * Gradient configuration and fill panel.
 * Provides interactive linear/radial gradient editing, color stop management,
 * tile modes, and layer application.
 */
@Composable
fun StudioGradientPanel(
    activeGradient: GradientConfig,
    currentColor: ColorData,
    onGradientChanged: (GradientConfig) -> Unit,
    onApplyToLayer: (GradientConfig) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    var config by remember { mutableStateOf(activeGradient) }
    var selectedStopIndex by remember { mutableStateOf(0) }
    var showConfirmApplyDialog by remember { mutableStateOf(false) }

    fun updateConfig(newConfig: GradientConfig) {
        config = newConfig
        onGradientChanged(newConfig)
    }

    Column(
        modifier = modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceElevated.copy(alpha = 0.98f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
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
                text = "🌈 Studio Gradients",
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            StudioIconButton(
                contentDescription = "Close gradient panel",
                onClick = onDismissRequest,
                icon = { Text("✕", style = typography.titleSmall) }
            )
        }

        // Gradient Type Selector
        StudioSegmentedSelector(
            options = listOf(GradientType.LINEAR, GradientType.RADIAL),
            selectedOption = config.type,
            onOptionSelected = { updateConfig(config.copy(type = it)) },
            optionLabel = { if (it == GradientType.LINEAR) "Linear" else "Radial" }
        )

        // Visual Gradient Preview Spectrum Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(cornerRadius.m))
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val sampleStep = 4
                for (x in 0 until w.toInt() step sampleStep) {
                    val t = x / w
                    val sampledColor = config.sampleAt(t)
                    drawRect(
                        color = Color(sampledColor.argbInt),
                        topLeft = Offset(x.toFloat(), 0f),
                        size = androidx.compose.ui.geometry.Size(sampleStep.toFloat(), h)
                    )
                }
            }
        }

        // Stops Strip
        Text(
            text = "Color Stops (${config.stops.size})",
            style = typography.labelSmall,
            color = colors.textSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                config.stops.forEachIndexed { idx, stop ->
                    val isSelected = idx == selectedStopIndex
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(stop.color.argbInt))
                            .border(
                                if (isSelected) 2.5.dp else 1.dp,
                                if (isSelected) colors.accent.primary else colors.outlineSubtle,
                                CircleShape
                            )
                            .clickable(role = Role.RadioButton) {
                                selectedStopIndex = idx
                            }
                            .semantics {
                                this.role = Role.RadioButton
                                this.contentDescription = "Stop $idx at ${(stop.position * 100).toInt()}%"
                            }
                    )
                }
            }

            // Stop Actions: Add Stop, Remove Stop, Reverse
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StudioIconButton(
                    contentDescription = "Set selected stop to current color",
                    onClick = {
                        if (selectedStopIndex in config.stops.indices) {
                            val cur = config.stops[selectedStopIndex]
                            updateConfig(config.updateStop(selectedStopIndex, cur.position, currentColor))
                        }
                    },
                    icon = { Text("🖌️", style = typography.titleSmall) }
                )

                StudioIconButton(
                    contentDescription = "Add stop at 50%",
                    onClick = {
                        updateConfig(config.withStop(0.5f, currentColor))
                        selectedStopIndex = config.stops.size - 1
                    },
                    icon = { Text("➕", style = typography.titleSmall) }
                )

                StudioIconButton(
                    contentDescription = "Remove stop",
                    enabled = config.stops.size > 2,
                    onClick = {
                        if (config.stops.size > 2 && selectedStopIndex in config.stops.indices) {
                            updateConfig(config.removeStop(selectedStopIndex))
                            selectedStopIndex = 0
                        }
                    },
                    icon = { Text("➖", style = typography.titleSmall) }
                )

                StudioIconButton(
                    contentDescription = "Reverse gradient stops",
                    onClick = {
                        updateConfig(config.reverseStops())
                    },
                    icon = { Text("⇄", style = typography.titleSmall) }
                )
            }
        }

        // Stop Position Slider
        if (selectedStopIndex in config.stops.indices) {
            val curStop = config.stops[selectedStopIndex]
            StudioSlider(
                value = curStop.position,
                onValueChange = { newPos ->
                    updateConfig(config.updateStop(selectedStopIndex, newPos, curStop.color))
                },
                label = "Stop Position",
                valueFormatter = { "${(it * 100).toInt()}%" }
            )
        }

        // Tile Mode Selector
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = "Tile Mode",
                style = typography.labelSmall,
                color = colors.textSecondary
            )
            StudioSegmentedSelector(
                options = listOf(GradientTileMode.CLAMP, GradientTileMode.REPEAT, GradientTileMode.MIRROR),
                selectedOption = config.tileMode,
                onOptionSelected = { updateConfig(config.copy(tileMode = it)) },
                optionLabel = { mode ->
                    when (mode) {
                        GradientTileMode.CLAMP -> "Clamp"
                        GradientTileMode.REPEAT -> "Repeat"
                        GradientTileMode.MIRROR -> "Mirror"
                    }
                }
            )
        }

        // Apply Gradient to Active Layer Action
        StudioButton(
            text = "⚡ Apply Gradient to Layer",
            onClick = { showConfirmApplyDialog = true },
            variant = StudioButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth()
        )

        // Modal Confirmation Dialog
        if (showConfirmApplyDialog) {
            StudioDialog(
                title = "Apply Gradient",
                body = "Apply '${config.name}' to the currently active raster layer? This action is fully undoable.",
                confirmText = "Apply",
                onConfirm = {
                    showConfirmApplyDialog = false
                    onApplyToLayer(config)
                },
                onDismiss = { showConfirmApplyDialog = false }
            )
        }
    }
}
