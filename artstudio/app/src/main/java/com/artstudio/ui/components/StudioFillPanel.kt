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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.artstudio.fill.*
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible configuration drawer panel for the Bucket Fill tool.
 * Provides controls for:
 * - Fill Mode: Contiguous Flood Fill vs Global Color Replacement
 * - Tolerance Slider: 0% to 100%
 * - Anti-Aliasing Toggle Switch
 * - Boundary Expansion Slider: -5 to +5 px
 * - Gap Closing Slider: 0 (Off) to 8 px
 * - Sample Source: Active Layer vs All Layers
 * - Target Layer: Current Layer vs New Layer
 * - Transparency Mode: Normal, Transparent Only, Opaque Only
 */
@Composable
fun StudioFillPanel(
    config: FillConfig,
    onConfigChanged: (FillConfig) -> Unit,
    onDismissRequest: () -> Unit,
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
                text = "Bucket Fill Settings",
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            StudioIconButton(
                contentDescription = "Close fill panel",
                onClick = onDismissRequest,
                icon = { Text("✕", style = typography.titleSmall, color = colors.textSecondary) }
            )
        }

        // Fill Mode Selector (Contiguous vs Global)
        Text(text = "Fill Mode", style = typography.labelMedium, color = colors.textSecondary)
        StudioSegmentedSelector(
            options = listOf(FillMode.CONTIGUOUS, FillMode.GLOBAL_COLOR),
            selectedOption = config.fillMode,
            onOptionSelected = { onConfigChanged(config.copy(fillMode = it)) },
            optionLabel = { mode ->
                when (mode) {
                    FillMode.CONTIGUOUS -> "Contiguous"
                    FillMode.GLOBAL_COLOR -> "Global"
                }
            }
        )

        // Tolerance Slider (0% to 100%)
        StudioSlider(
            label = "Tolerance",
            value = config.tolerance,
            onValueChange = { onConfigChanged(config.copy(tolerance = it)) },
            valueRange = 0.0f..1.0f,
            valueFormatter = { "${(it * 100).toInt()}%" }
        )

        // Gap Closing Slider (0 to 8 px)
        StudioSlider(
            label = "Gap Closing",
            value = config.gapClosingRadius.toFloat(),
            onValueChange = { onConfigChanged(config.copy(gapClosingRadius = it.toInt())) },
            valueRange = 0f..8f,
            steps = 7,
            valueFormatter = { if (it.toInt() == 0) "Off" else "${it.toInt()} px" }
        )

        // Boundary Expansion Slider (-5 to +5 px)
        StudioSlider(
            label = "Boundary Bleed",
            value = config.boundaryExpansion.toFloat(),
            onValueChange = { onConfigChanged(config.copy(boundaryExpansion = it.toInt())) },
            valueRange = -5f..5f,
            steps = 9,
            valueFormatter = { v ->
                val iv = v.toInt()
                if (iv > 0) "+$iv px" else if (iv < 0) "$iv px" else "Exact"
            }
        )

        // Anti-Aliasing Selector
        Text(text = "Anti-Aliasing", style = typography.labelMedium, color = colors.textSecondary)
        StudioSegmentedSelector(
            options = listOf(true, false),
            selectedOption = config.antiAlias,
            onOptionSelected = { onConfigChanged(config.copy(antiAlias = it)) },
            optionLabel = { if (it) "Smooth On" else "Off" }
        )

        // Sample Source (Active Layer vs All Layers)
        Text(text = "Sample Source", style = typography.labelMedium, color = colors.textSecondary)
        StudioSegmentedSelector(
            options = listOf(FillSampleSource.ACTIVE_LAYER, FillSampleSource.ALL_LAYERS),
            selectedOption = config.sampleSource,
            onOptionSelected = { onConfigChanged(config.copy(sampleSource = it)) },
            optionLabel = { src ->
                when (src) {
                    FillSampleSource.ACTIVE_LAYER -> "Active Layer"
                    FillSampleSource.ALL_LAYERS -> "All Layers"
                }
            }
        )

        // Target Layer (Current vs New Layer)
        Text(text = "Target Layer", style = typography.labelMedium, color = colors.textSecondary)
        StudioSegmentedSelector(
            options = listOf(FillTargetLayer.CURRENT_LAYER, FillTargetLayer.NEW_LAYER),
            selectedOption = config.targetLayerMode,
            onOptionSelected = { onConfigChanged(config.copy(targetLayerMode = it)) },
            optionLabel = { tgt ->
                when (tgt) {
                    FillTargetLayer.CURRENT_LAYER -> "Current Layer"
                    FillTargetLayer.NEW_LAYER -> "New Layer"
                }
            }
        )

        // Transparency Mode Selector
        Text(text = "Transparency Mode", style = typography.labelMedium, color = colors.textSecondary)
        StudioSegmentedSelector(
            options = listOf(
                FillTransparencyMode.NORMAL,
                FillTransparencyMode.TRANSPARENT_ONLY,
                FillTransparencyMode.OPAQUE_ONLY
            ),
            selectedOption = config.transparencyMode,
            onOptionSelected = { onConfigChanged(config.copy(transparencyMode = it)) },
            optionLabel = { tm ->
                when (tm) {
                    FillTransparencyMode.NORMAL -> "Normal"
                    FillTransparencyMode.TRANSPARENT_ONLY -> "Transparent Only"
                    FillTransparencyMode.OPAQUE_ONLY -> "Opaque Only"
                    FillTransparencyMode.RESPECT_TRANSPARENCY -> "Respect Alpha"
                }
            }
        )
    }
}
