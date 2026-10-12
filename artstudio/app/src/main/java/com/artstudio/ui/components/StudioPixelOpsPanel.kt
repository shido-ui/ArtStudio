package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artstudio.pixelops.PixelModifierConfig
import com.artstudio.pixelops.PixelModifierMode
import com.artstudio.ui.theme.StudioTheme
import kotlin.math.roundToInt

/**
 * Accessible configuration drawer panel for Pixel Modification tools (Smudge, Blur, Blend).
 * Provides controls for:
 * - Tool Mode: Smudge vs Blur vs Blend
 * - Brush Size Slider: 2 dp to 128 dp
 * - Strength Slider: 1% to 100%
 * - Hardness Slider: 0% to 100%
 * - Spacing Slider: 2% to 30%
 * - Preserve Alpha Toggle Switch
 *
 * Meets WCAG AAA accessibility contrast, >= 48 dp touch targets.
 */
@Composable
fun StudioPixelOpsPanel(
    config: PixelModifierConfig,
    onConfigChanged: (PixelModifierConfig) -> Unit,
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
                text = "${config.mode.displayName} Controls",
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            StudioIconButton(
                contentDescription = "Close ${config.mode.displayName} Settings",
                onClick = onDismissRequest,
                icon = { Text("✕", style = typography.titleSmall, color = colors.textSecondary) }
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Tool Mode Selector (Smudge, Blur, Blend)
        Text(
            text = "OPERATION MODE",
            style = typography.labelSmall,
            color = colors.textSecondary
        )
        StudioSegmentedSelector(
            options = PixelModifierMode.entries,
            selectedOption = config.mode,
            onOptionSelected = { onConfigChanged(config.copy(mode = it)) },
            optionLabel = { it.displayName }
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        // Brush Size Slider
        StudioSlider(
            label = "Brush Size",
            value = config.size,
            onValueChange = { onConfigChanged(config.copy(size = it)) },
            valueRange = 2f..128f,
            valueFormatter = { "${it.roundToInt()} px" }
        )

        // Strength Slider
        StudioSlider(
            label = "Strength",
            value = config.strength,
            onValueChange = { onConfigChanged(config.copy(strength = it)) },
            valueRange = 0.05f..1.0f,
            valueFormatter = { "${(it * 100f).roundToInt()}%" }
        )

        // Hardness Slider
        StudioSlider(
            label = "Edge Hardness",
            value = config.hardness,
            onValueChange = { onConfigChanged(config.copy(hardness = it)) },
            valueRange = 0.0f..1.0f,
            valueFormatter = { "${(it * 100f).roundToInt()}%" }
        )

        // Spacing Slider
        StudioSlider(
            label = "Stroke Spacing",
            value = config.spacing,
            onValueChange = { onConfigChanged(config.copy(spacing = it)) },
            valueRange = 0.02f..0.30f,
            valueFormatter = { "${(it * 100f).roundToInt()}%" }
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        // Preserve Alpha Toggle Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Preserve Alpha",
                    style = typography.bodyMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "Locks transparency; only recolors opaque pixels",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            Switch(
                checked = config.preserveAlpha,
                onCheckedChange = { onConfigChanged(config.copy(preserveAlpha = it)) }
            )
        }
    }
}
