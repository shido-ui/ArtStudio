package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artstudio.filter.FilterConfig
import com.artstudio.filter.FilterType
import com.artstudio.ui.theme.StudioTheme
import kotlin.math.roundToInt

/**
 * Accessible configuration drawer panel for image filter operations.
 *
 * Provides named parameter controls, preview toggle, cancel, and explicit apply actions.
 * Complies with WCAG AAA accessibility standards and >= 48 dp touch target sizes.
 */
@Composable
fun StudioFilterPanel(
    activeConfig: FilterConfig,
    onConfigChanged: (FilterConfig) -> Unit,
    previewEnabled: Boolean,
    onTogglePreview: (Boolean) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .width(340.dp)
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
                text = "Image Filters",
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            StudioIconButton(
                contentDescription = "Cancel and Close Filters",
                onClick = onCancel,
                icon = { Text("✕", style = typography.titleSmall, color = colors.textSecondary) }
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Filter Type Horizontal Selector
        Text(
            text = "FILTER OPERATION",
            style = typography.labelSmall,
            color = colors.textSecondary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            FilterType.entries.forEach { fType ->
                val isSelected = activeConfig.type == fType
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            val newConfig = when (fType) {
                                FilterType.GAUSSIAN_BLUR -> FilterConfig.GaussianBlurConfig()
                                FilterType.SHARPEN -> FilterConfig.SharpenConfig()
                                FilterType.OUTLINE -> FilterConfig.OutlineConfig()
                                FilterType.GLOW -> FilterConfig.GlowConfig()
                                FilterType.NOISE -> FilterConfig.NoiseConfig()
                                FilterType.PIXELATE -> FilterConfig.PixelateConfig()
                                FilterType.RIPPLE -> FilterConfig.RippleConfig()
                            }
                            onConfigChanged(newConfig)
                        }
                    },
                    label = { Text(fType.displayName, style = typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent.primary,
                        selectedLabelColor = colors.accent.onPrimary,
                        containerColor = colors.surfaceElevated,
                        labelColor = colors.textPrimary
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Dynamic Parameter Sliders based on activeConfig
        Text(
            text = "PARAMETERS",
            style = typography.labelSmall,
            color = colors.textSecondary
        )

        when (val cfg = activeConfig) {
            is FilterConfig.GaussianBlurConfig -> {
                StudioSlider(
                    label = "Blur Radius",
                    value = cfg.radius,
                    onValueChange = { onConfigChanged(cfg.copy(radius = it)) },
                    valueRange = 1f..50f,
                    valueFormatter = { "${it.roundToInt()} px" }
                )
                StudioSlider(
                    label = "Convolution Passes",
                    value = cfg.iterations.toFloat(),
                    onValueChange = { onConfigChanged(cfg.copy(iterations = it.roundToInt())) },
                    valueRange = 1f..3f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
            }
            is FilterConfig.SharpenConfig -> {
                StudioSlider(
                    label = "Sharpen Strength",
                    value = cfg.strength,
                    onValueChange = { onConfigChanged(cfg.copy(strength = it)) },
                    valueRange = 0.1f..5.0f,
                    valueFormatter = { "${((it * 10f).roundToInt() / 10f)}x" }
                )
                StudioSlider(
                    label = "Mask Radius",
                    value = cfg.radius,
                    onValueChange = { onConfigChanged(cfg.copy(radius = it)) },
                    valueRange = 1f..10f,
                    valueFormatter = { "${it.roundToInt()} px" }
                )
            }
            is FilterConfig.OutlineConfig -> {
                StudioSlider(
                    label = "Edge Threshold",
                    value = cfg.threshold,
                    onValueChange = { onConfigChanged(cfg.copy(threshold = it)) },
                    valueRange = 1f..255f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Invert Outlines", style = typography.bodyMedium, color = colors.textPrimary)
                    Switch(
                        checked = cfg.invert,
                        onCheckedChange = { onConfigChanged(cfg.copy(invert = it)) }
                    )
                }
            }
            is FilterConfig.GlowConfig -> {
                StudioSlider(
                    label = "Glow Radius",
                    value = cfg.radius,
                    onValueChange = { onConfigChanged(cfg.copy(radius = it)) },
                    valueRange = 1f..50f,
                    valueFormatter = { "${it.roundToInt()} px" }
                )
                StudioSlider(
                    label = "Bloom Intensity",
                    value = cfg.intensity,
                    onValueChange = { onConfigChanged(cfg.copy(intensity = it)) },
                    valueRange = 0.1f..3.0f,
                    valueFormatter = { "${((it * 10f).roundToInt() / 10f)}x" }
                )
                StudioSlider(
                    label = "Luminance Threshold",
                    value = cfg.threshold,
                    onValueChange = { onConfigChanged(cfg.copy(threshold = it)) },
                    valueRange = 0.0f..1.0f,
                    valueFormatter = { "${(it * 100f).roundToInt()}%" }
                )
            }
            is FilterConfig.NoiseConfig -> {
                StudioSlider(
                    label = "Noise Amount",
                    value = cfg.amount,
                    onValueChange = { onConfigChanged(cfg.copy(amount = it)) },
                    valueRange = 0.01f..1.0f,
                    valueFormatter = { "${(it * 100f).roundToInt()}%" }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Monochrome Noise", style = typography.bodyMedium, color = colors.textPrimary)
                    Switch(
                        checked = cfg.monochrome,
                        onCheckedChange = { onConfigChanged(cfg.copy(monochrome = it)) }
                    )
                }
            }
            is FilterConfig.PixelateConfig -> {
                StudioSlider(
                    label = "Block Size",
                    value = cfg.blockSize.toFloat(),
                    onValueChange = { onConfigChanged(cfg.copy(blockSize = it.roundToInt())) },
                    valueRange = 2f..64f,
                    valueFormatter = { "${it.roundToInt()} px" }
                )
            }
            is FilterConfig.RippleConfig -> {
                StudioSlider(
                    label = "Wave Amplitude",
                    value = cfg.amplitude,
                    onValueChange = { onConfigChanged(cfg.copy(amplitude = it)) },
                    valueRange = 1f..50f,
                    valueFormatter = { "${it.roundToInt()} px" }
                )
                StudioSlider(
                    label = "Wavelength",
                    value = cfg.wavelength,
                    onValueChange = { onConfigChanged(cfg.copy(wavelength = it)) },
                    valueRange = 5f..200f,
                    valueFormatter = { "${it.roundToInt()} px" }
                )
                StudioSlider(
                    label = "Phase Angle",
                    value = cfg.phaseDeg,
                    onValueChange = { onConfigChanged(cfg.copy(phaseDeg = it)) },
                    valueRange = 0f..360f,
                    valueFormatter = { "${it.roundToInt()}°" }
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Preview Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Real-Time Preview", style = typography.bodyMedium, color = colors.textPrimary)
            Switch(
                checked = previewEnabled,
                onCheckedChange = onTogglePreview
            )
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // Action Buttons: Cancel and Apply
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            StudioButton(
                text = "Cancel",
                onClick = onCancel,
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            )
            StudioButton(
                text = "Apply Filter",
                onClick = onApply,
                variant = StudioButtonVariant.Primary,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            )
        }
    }
}
