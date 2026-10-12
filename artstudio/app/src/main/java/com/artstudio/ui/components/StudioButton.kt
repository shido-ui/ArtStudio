package com.artstudio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

enum class StudioButtonVariant {
    Primary,
    Secondary,
    Destructive,
    Ghost
}

/**
 * Accessible button component enforcing 48dp touch targets, clear loading states,
 * and explicit explanations when disabled.
 */
@Composable
fun StudioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: StudioButtonVariant = StudioButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    disabledExplanation: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    var showDisabledDialog by remember { mutableStateOf(false) }

    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val (bgColor, contentColor, borderStroke) = when {
        !enabled -> Triple(
            colors.surfaceElevated.copy(alpha = 0.5f),
            colors.textTertiary,
            BorderStroke(1.dp, colors.outlineSubtle)
        )
        loading -> Triple(
            colors.accent.container,
            colors.accent.onContainer,
            null
        )
        variant == StudioButtonVariant.Primary -> Triple(
            colors.accent.primary,
            colors.accent.onPrimary,
            null
        )
        variant == StudioButtonVariant.Secondary -> Triple(
            colors.surfaceElevated,
            colors.textPrimary,
            BorderStroke(1.dp, colors.outlineStrong)
        )
        variant == StudioButtonVariant.Destructive -> Triple(
            colors.error,
            colors.onError,
            null
        )
        variant == StudioButtonVariant.Ghost -> Triple(
            Color.Transparent,
            colors.accent.primary,
            null
        )
        else -> Triple(colors.accent.primary, colors.accent.onPrimary, null)
    }

    val semanticsDesc = buildString {
        append(text)
        if (!enabled && disabledExplanation != null) {
            append(". Disabled: ")
            append(disabledExplanation)
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 64.dp, minHeight = StudioAccessibility.MinTouchTarget)
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(bgColor)
                .then(
                    if (borderStroke != null) {
                        Modifier.background(bgColor).padding(borderStroke.width)
                    } else Modifier
                )
                .clickable(
                    enabled = enabled || disabledExplanation != null,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (enabled && !loading) {
                            onClick()
                        } else if (!enabled && disabledExplanation != null) {
                            showDisabledDialog = true
                        }
                    }
                )
                .padding(horizontal = spacing.l, vertical = spacing.s)
                .semantics {
                    this.role = Role.Button
                    this.contentDescription = semanticsDesc
                },
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(StudioAccessibility.IconSmall),
                        color = contentColor,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = text,
                        style = typography.labelLarge,
                        color = contentColor
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s)
                ) {
                    leadingIcon?.invoke()
                    Text(
                        text = text,
                        style = typography.labelLarge,
                        color = contentColor
                    )
                }
            }
        }

        // Inline explanation badge when disabled so no decorative button creates mystery
        if (!enabled && disabledExplanation != null) {
            Text(
                text = "Unavailable: $disabledExplanation",
                style = typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = spacing.xxs, start = spacing.xs)
            )
        }
    }
}
