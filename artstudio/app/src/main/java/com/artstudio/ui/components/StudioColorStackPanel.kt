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
import com.artstudio.colorstack.ColorStyleId
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Interactive Colour Lab panel providing:
 * - 16 style presets selector (Classic Cel, Soft Gradient, Hybrid, Painterly, Watercolour, etc.)
 * - Style strength & opacity sliders
 * - Non-destructive "Bake to Layers" action button
 * Split into modular composables strictly <= 150 lines.
 */
@Composable
fun StudioColorStackPanel(
    selectedStyleId: ColorStyleId,
    onStyleSelected: (ColorStyleId) -> Unit,
    strength: Float,
    onStrengthChanged: (Float) -> Unit,
    opacity: Float,
    onOpacityChanged: (Float) -> Unit,
    onBakeToLayers: () -> Unit,
    onDismissRequest: () -> Unit,
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
            ColorStackHeader(onDismissRequest)
            Spacer(modifier = Modifier.height(spacing.s))
            ColorStackStyleSelector(selectedStyleId, onStyleSelected)
            Spacer(modifier = Modifier.height(spacing.s))
            ColorStackSliders(strength, onStrengthChanged, opacity, onOpacityChanged)
            Spacer(modifier = Modifier.height(spacing.s))
            StudioButton(
                text = "Bake to Editable Layers",
                onClick = onBakeToLayers,
                variant = StudioButtonVariant.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StudioAccessibility.MinTouchTarget)
            )
        }
    }
}

@Composable
private fun ColorStackHeader(onDismissRequest: () -> Unit) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Colour Lab (ColorStack)",
            style = typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        StudioButton(
            text = "Close",
            onClick = onDismissRequest,
            variant = StudioButtonVariant.Ghost,
            modifier = Modifier.height(StudioAccessibility.MinTouchTarget)
        )
    }
}

@Composable
private fun ColorStackStyleSelector(
    selectedStyleId: ColorStyleId,
    onStyleSelected: (ColorStyleId) -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Text(
        text = "Style Preset: ${selectedStyleId.displayName}",
        style = typography.labelMedium,
        color = colors.textSecondary
    )
    Spacer(modifier = Modifier.height(spacing.xs))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(ColorStyleId.entries.filter { it != ColorStyleId.CUSTOM }, key = { it.name }) { style ->
            val isSelected = style == selectedStyleId
            Box(
                modifier = Modifier
                    .height(StudioAccessibility.MinTouchTarget)
                    .clip(RoundedCornerShape(cornerRadius.m))
                    .background(if (isSelected) colors.accent.primary else colors.surfaceBase)
                    .border(1.dp, if (isSelected) colors.accent.primary else colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
                    .clickable { onStyleSelected(style) }
                    .padding(horizontal = spacing.m, vertical = spacing.xs)
                    .semantics { contentDescription = "Style ${style.displayName}" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = style.displayName,
                    style = typography.labelMedium,
                    color = if (isSelected) colors.accent.onPrimary else colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun ColorStackSliders(
    strength: Float,
    onStrengthChanged: (Float) -> Unit,
    opacity: Float,
    onOpacityChanged: (Float) -> Unit
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Strength (${(strength * 100).toInt()}%)",
            style = typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.width(110.dp)
        )
        Slider(
            value = strength,
            onValueChange = onStrengthChanged,
            valueRange = 0.1f..1.0f,
            modifier = Modifier.weight(1f).height(StudioAccessibility.MinTouchTarget)
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Opacity (${(opacity * 100).toInt()}%)",
            style = typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.width(110.dp)
        )
        Slider(
            value = opacity,
            onValueChange = onOpacityChanged,
            valueRange = 0.1f..1.0f,
            modifier = Modifier.weight(1f).height(StudioAccessibility.MinTouchTarget)
        )
    }
}
