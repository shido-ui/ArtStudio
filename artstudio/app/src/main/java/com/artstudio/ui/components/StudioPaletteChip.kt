package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Functional Palette Chip representing a selectable color swatch.
 * Guarantees minimum 48dp touch target with active selection ring.
 */
@Composable
fun StudioPaletteChip(
    color: Color,
    hexLabel: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    colorName: String? = null,
    enabled: Boolean = true
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val displayName = colorName ?: hexLabel

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(StudioAccessibility.MinTouchTarget) // 48dp touch target
                .clickable(
                    enabled = enabled,
                    role = Role.RadioButton,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelect
                )
                .semantics {
                    this.role = Role.RadioButton
                    this.contentDescription = "Color $displayName, ${if (isSelected) "selected" else "unselected"}"
                },
            contentAlignment = Alignment.Center
        ) {
            // Active selection ring
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, colors.accent.primary, CircleShape)
                )
            }

            // Swatch color fill
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, colors.outlineSubtle, CircleShape)
            )
        }

        Text(
            text = hexLabel,
            style = typography.labelSmall,
            color = if (isSelected) colors.accent.primary else colors.textSecondary
        )
    }
}
