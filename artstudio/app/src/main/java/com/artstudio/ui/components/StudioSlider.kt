package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme
import kotlin.math.roundToInt

/**
 * Accessible Slider component supporting continuous and stepped adjustments,
 * integrated value readout, and accessibility description.
 */
@Composable
fun StudioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    label: String,
    valueFormatter: (Float) -> String = { "${(it * 100).roundToInt()}%" },
    enabled: Boolean = true,
    disabledExplanation: String? = null,
    tooltip: String? = null
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val formattedValue = valueFormatter(value)
    val semanticDescription = "$label: $formattedValue" + if (!enabled && disabledExplanation != null) " (Disabled: $disabledExplanation)" else ""
    val tooltipText = tooltip ?: "Adjust $label ($formattedValue)"

    StudioTooltipBox(tooltipText = tooltipText) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    this.contentDescription = semanticDescription
                }
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = typography.labelMedium,
                color = if (enabled) colors.textPrimary else colors.textTertiary
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(if (enabled) colors.surfaceOverlay else colors.surfaceElevated)
                    .padding(horizontal = spacing.s, vertical = spacing.xxs)
            ) {
                Text(
                    text = formattedValue,
                    style = typography.labelSmall,
                    color = if (enabled) colors.accent.primary else colors.textTertiary
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = StudioAccessibility.MinTouchTarget),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent.primary,
                    activeTrackColor = colors.accent.primary,
                    inactiveTrackColor = colors.outlineSubtle,
                    disabledThumbColor = colors.textTertiary,
                    disabledActiveTrackColor = colors.outlineSubtle,
                    disabledInactiveTrackColor = colors.surfaceElevated
                )
            )
        }

        if (!enabled && disabledExplanation != null) {
            Text(
                text = "Disabled: $disabledExplanation",
                style = typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = spacing.xs)
            )
        }
    }
}
}
