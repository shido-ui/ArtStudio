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
 * Accessible Icon Button ensuring >= 48dp touch target, state highlights,
 * and semantic accessibility labels.
 */
@Composable
fun StudioIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    disabledExplanation: String? = null,
    icon: @Composable (tint: Color) -> Unit
) {
    val colors = StudioTheme.colors
    val cornerRadius = StudioTheme.cornerRadius
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    val (bgColor, tintColor, borderColor) = when {
        !enabled -> Triple(
            colors.surfaceElevated.copy(alpha = 0.4f),
            colors.textTertiary,
            colors.outlineSubtle
        )
        active -> Triple(
            colors.accent.container,
            colors.accent.primary,
            colors.accent.primary
        )
        else -> Triple(
            colors.surfaceElevated,
            colors.textPrimary,
            colors.outlineSubtle
        )
    }

    val semanticsDesc = buildString {
        append(contentDescription)
        if (active) append(", active")
        if (!enabled) {
            append(", disabled")
            if (disabledExplanation != null) append(": $disabledExplanation")
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(StudioAccessibility.MinTouchTarget) // Guaranteed 48dp x 48dp touch target
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(cornerRadius.m))
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .semantics {
                    this.role = Role.Button
                    this.contentDescription = semanticsDesc
                },
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(StudioAccessibility.IconMedium)) {
                icon(tintColor)
            }

            // Small active dot indicator if active
            if (active) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = (-4).dp)
                        .clip(CircleShape)
                        .background(colors.accent.primary)
                )
            }
        }

        if (!enabled && disabledExplanation != null) {
            Text(
                text = disabledExplanation,
                style = typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = spacing.xxs)
            )
        }
    }
}
