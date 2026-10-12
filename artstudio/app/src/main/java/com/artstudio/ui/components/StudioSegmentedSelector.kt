package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Segmented Selector for switching between discrete modes, tools, or styles.
 * Guarantees minimum 48dp touch target height across all options.
 */
@Composable
fun <T> StudioSegmentedSelector(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledExplanation: String? = null
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = StudioAccessibility.MinTouchTarget)
                .clip(RoundedCornerShape(cornerRadius.pill))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.pill))
                .padding(spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                val label = optionLabel(option)

                val (itemBg, itemTextColor) = when {
                    !enabled -> Pair(colors.surfaceElevated, colors.textTertiary)
                    isSelected -> Pair(colors.accent.primary, colors.accent.onPrimary)
                    else -> Pair(colors.surfaceElevated, colors.textSecondary)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(cornerRadius.pill))
                        .background(itemBg)
                        .clickable(
                            enabled = enabled,
                            role = Role.RadioButton,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOptionSelected(option) }
                        )
                        .padding(horizontal = spacing.s, vertical = spacing.s)
                        .semantics {
                            this.role = Role.RadioButton
                            this.contentDescription = "$label, ${if (isSelected) "selected" else "unselected"}"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = typography.labelMedium,
                        color = itemTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        if (!enabled && disabledExplanation != null) {
            Text(
                text = "Disabled: $disabledExplanation",
                style = typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = spacing.xxs, start = spacing.xs)
            )
        }
    }
}
