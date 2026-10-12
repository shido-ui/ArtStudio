package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Standard Loading State with accessible description and optional cancellation.
 */
@Composable
fun StudioLoadingState(
    title: String = "Processing artwork...",
    subtitle: String = "Calculating raster passes without blocking UI thread",
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
            .padding(spacing.xl)
            .semantics(mergeDescendants = true) {
                this.contentDescription = "$title: $subtitle"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(StudioAccessibility.IconLarge),
            color = colors.accent.primary,
            strokeWidth = 3.dp
        )

        Spacer(modifier = Modifier.height(spacing.m))

        Text(
            text = title,
            style = typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            style = typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xxs)
        )

        if (onCancel != null) {
            Spacer(modifier = Modifier.height(spacing.m))
            StudioButton(
                text = "Cancel Operation",
                onClick = onCancel,
                variant = StudioButtonVariant.Ghost
            )
        }
    }
}

/**
 * Standard Empty State with graphic, title, descriptive message, and action button.
 */
@Composable
fun StudioEmptyState(
    title: String,
    description: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    iconSymbol: String = "🎨",
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
            .padding(spacing.xl)
            .semantics(mergeDescendants = true) {
                this.contentDescription = "$title: $description"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.surfaceOverlay),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconSymbol,
                style = typography.displayMedium
            )
        }

        Spacer(modifier = Modifier.height(spacing.m))

        Text(
            text = title,
            style = typography.titleLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Text(
            text = description,
            style = typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xs, bottom = spacing.m)
        )

        if (actionText != null && onAction != null) {
            StudioButton(
                text = actionText,
                onClick = onAction,
                variant = StudioButtonVariant.Primary
            )
        }
    }
}

/**
 * Standard Error State with warning icon, message, retry action, and fallback.
 */
@Composable
fun StudioErrorState(
    title: String = "Operation Failed",
    errorMessage: String,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.error, RoundedCornerShape(cornerRadius.l))
            .padding(spacing.xl)
            .semantics(mergeDescendants = true) {
                this.contentDescription = "Error $title: $errorMessage"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.error.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚠️",
                style = typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(spacing.s))

        Text(
            text = title,
            style = typography.titleMedium,
            color = colors.error,
            textAlign = TextAlign.Center
        )

        Text(
            text = errorMessage,
            style = typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.m)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onDismiss != null) {
                StudioButton(
                    text = "Dismiss",
                    onClick = onDismiss,
                    variant = StudioButtonVariant.Ghost
                )
            }
            if (onRetry != null) {
                StudioButton(
                    text = "Retry",
                    onClick = onRetry,
                    variant = StudioButtonVariant.Secondary
                )
            }
        }
    }
}
