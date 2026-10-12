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
import com.artstudio.adjustment.*
import com.artstudio.ui.theme.StudioTheme
import kotlin.math.roundToInt

/**
 * Accessible configuration drawer panel for non-destructive color and tonal adjustments.
 *
 * Provides parameter sliders for Brightness/Contrast, Levels, Hue/Saturation, Grayscale, and Color Balance,
 * with live speculative preview toggling, non-destructive layer addition, and explicit baking/rasterization.
 * Complies with WCAG AAA accessibility standards and >= 48 dp touch target sizes.
 */
@Composable
fun StudioAdjustmentPanel(
    activeAdjustment: ColorAdjustment,
    onAdjustmentChanged: (ColorAdjustment) -> Unit,
    previewEnabled: Boolean,
    onTogglePreview: (Boolean) -> Unit,
    onAddAdjustmentLayer: () -> Unit,
    onBakeRasterize: () -> Unit,
    onClose: () -> Unit,
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
                text = "Color & Tonal Adjustments",
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            StudioIconButton(
                contentDescription = "Close Adjustments",
                onClick = onClose,
                icon = { Text("✕", style = typography.titleSmall, color = colors.textSecondary) }
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Adjustment Type Selector Row
        Text(
            text = "ADJUSTMENT TYPE",
            style = typography.labelSmall,
            color = colors.textSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            AdjustmentType.entries.forEach { type ->
                val selected = activeAdjustment.type == type
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            val newAdj: ColorAdjustment = when (type) {
                                AdjustmentType.BRIGHTNESS_CONTRAST -> BrightnessContrastAdjustment()
                                AdjustmentType.LEVELS -> LevelsAdjustment()
                                AdjustmentType.HUE_SATURATION -> HueSaturationAdjustment()
                                AdjustmentType.GRAYSCALE -> GrayscaleAdjustment()
                                AdjustmentType.COLOR_BALANCE -> ColorBalanceAdjustment()
                            }
                            onAdjustmentChanged(newAdj)
                        }
                    },
                    label = {
                        Text(
                            text = type.displayName,
                            style = typography.labelMedium
                        )
                    },
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

        // Dynamic Parameter Controls based on active adjustment
        Text(
            text = "PARAMETERS",
            style = typography.labelSmall,
            color = colors.textSecondary
        )

        when (activeAdjustment) {
            is BrightnessContrastAdjustment -> {
                StudioSlider(
                    label = "Brightness",
                    value = activeAdjustment.brightness,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(brightness = it)) },
                    valueRange = -100f..100f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
                StudioSlider(
                    label = "Contrast",
                    value = activeAdjustment.contrast,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(contrast = it)) },
                    valueRange = -100f..100f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
            }
            is LevelsAdjustment -> {
                StudioSlider(
                    label = "Shadow In (Black)",
                    value = activeAdjustment.inBlack.toFloat(),
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(inBlack = it.roundToInt())) },
                    valueRange = 0f..253f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
                StudioSlider(
                    label = "Midtone Gamma",
                    value = activeAdjustment.inGamma,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(inGamma = it)) },
                    valueRange = 0.1f..5.0f,
                    valueFormatter = { String.format("%.2f", it) }
                )
                StudioSlider(
                    label = "Highlight In (White)",
                    value = activeAdjustment.inWhite.toFloat(),
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(inWhite = it.roundToInt())) },
                    valueRange = (activeAdjustment.inBlack + 2).toFloat()..255f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
                StudioSlider(
                    label = "Output Black Floor",
                    value = activeAdjustment.outBlack.toFloat(),
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(outBlack = it.roundToInt())) },
                    valueRange = 0f..255f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
                StudioSlider(
                    label = "Output White Ceiling",
                    value = activeAdjustment.outWhite.toFloat(),
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(outWhite = it.roundToInt())) },
                    valueRange = 0f..255f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
            }
            is HueSaturationAdjustment -> {
                StudioSlider(
                    label = "Hue Shift",
                    value = activeAdjustment.hueShiftDeg,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(hueShiftDeg = it)) },
                    valueRange = -180f..180f,
                    valueFormatter = { "${it.roundToInt()}°" }
                )
                StudioSlider(
                    label = "Saturation",
                    value = activeAdjustment.saturationPercent,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(saturationPercent = it)) },
                    valueRange = -100f..100f,
                    valueFormatter = { "${it.roundToInt()}%" }
                )
                StudioSlider(
                    label = "Lightness",
                    value = activeAdjustment.lightnessPercent,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(lightnessPercent = it)) },
                    valueRange = -100f..100f,
                    valueFormatter = { "${it.roundToInt()}%" }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Colorize Mode", style = typography.bodyMedium, color = colors.textSecondary)
                    Switch(
                        checked = activeAdjustment.colorize,
                        onCheckedChange = { onAdjustmentChanged(activeAdjustment.copy(colorize = it)) }
                    )
                }
            }
            is GrayscaleAdjustment -> {
                StudioSlider(
                    label = "Red Weight",
                    value = activeAdjustment.redWeight,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(redWeight = it)) },
                    valueRange = 0f..1f,
                    valueFormatter = { String.format("%.2f", it) }
                )
                StudioSlider(
                    label = "Green Weight",
                    value = activeAdjustment.greenWeight,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(greenWeight = it)) },
                    valueRange = 0f..1f,
                    valueFormatter = { String.format("%.2f", it) }
                )
                StudioSlider(
                    label = "Blue Weight",
                    value = activeAdjustment.blueWeight,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(blueWeight = it)) },
                    valueRange = 0f..1f,
                    valueFormatter = { String.format("%.2f", it) }
                )
                StudioSlider(
                    label = "Tint Intensity",
                    value = activeAdjustment.tintIntensity,
                    onValueChange = {
                        val tint = if (it > 0f && activeAdjustment.tintColor == null) 0xFF704214L else activeAdjustment.tintColor
                        onAdjustmentChanged(activeAdjustment.copy(tintIntensity = it, tintColor = tint))
                    },
                    valueRange = 0f..1f,
                    valueFormatter = { "${(it * 100).roundToInt()}%" }
                )
            }
            is ColorBalanceAdjustment -> {
                StudioSlider(
                    label = "Cyan ⟷ Red",
                    value = activeAdjustment.cyanRed,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(cyanRed = it)) },
                    valueRange = -100f..100f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
                StudioSlider(
                    label = "Magenta ⟷ Green",
                    value = activeAdjustment.magentaGreen,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(magentaGreen = it)) },
                    valueRange = -100f..100f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
                StudioSlider(
                    label = "Yellow ⟷ Blue",
                    value = activeAdjustment.yellowBlue,
                    onValueChange = { onAdjustmentChanged(activeAdjustment.copy(yellowBlue = it)) },
                    valueRange = -100f..100f,
                    valueFormatter = { "${it.roundToInt()}" }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Preserve Luminosity", style = typography.bodyMedium, color = colors.textSecondary)
                    Switch(
                        checked = activeAdjustment.preserveLuminosity,
                        onCheckedChange = { onAdjustmentChanged(activeAdjustment.copy(preserveLuminosity = it)) }
                    )
                }
            }
        }

        // Layer Opacity Control
        StudioSlider(
            label = "Adjustment Opacity",
            value = activeAdjustment.opacity,
            onValueChange = {
                val updated: ColorAdjustment = when (activeAdjustment) {
                    is BrightnessContrastAdjustment -> activeAdjustment.copy(opacity = it)
                    is LevelsAdjustment -> activeAdjustment.copy(opacity = it)
                    is HueSaturationAdjustment -> activeAdjustment.copy(opacity = it)
                    is GrayscaleAdjustment -> activeAdjustment.copy(opacity = it)
                    is ColorBalanceAdjustment -> activeAdjustment.copy(opacity = it)
                }
                onAdjustmentChanged(updated)
            },
            valueRange = 0f..1f,
            valueFormatter = { "${(it * 100).roundToInt()}%" }
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        // Live Preview Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Real-Time Preview",
                style = typography.bodyMedium,
                color = colors.textPrimary
            )
            Switch(
                checked = previewEnabled,
                onCheckedChange = onTogglePreview
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Action Buttons: Non-destructive Add Layer vs Explicit Bake/Rasterize
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            StudioButton(
                text = "+ Add Adjustment Layer",
                onClick = onAddAdjustmentLayer,
                variant = StudioButtonVariant.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            )
            StudioButton(
                text = "⚡ Bake / Rasterize Down",
                onClick = onBakeRasterize,
                variant = StudioButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            )
        }
    }
}
