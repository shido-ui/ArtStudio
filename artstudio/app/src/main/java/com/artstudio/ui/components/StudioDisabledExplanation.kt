package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioTheme

/**
 * Explains disabled features inline or as a banner to ensure no decorative
 * control implies an action that does not exist.
 */
@Composable
fun StudioDisabledExplanation(
    reason: String,
    modifier: Modifier = Modifier,
    suggestedAction: String? = null
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.s))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
            .padding(horizontal = spacing.m, vertical = spacing.s)
            .semantics(mergeDescendants = true) {
                this.contentDescription = "Feature unavailable: $reason" + if (suggestedAction != null) ". Recommendation: $suggestedAction" else ""
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        Text(
            text = "ℹ️",
            style = typography.titleSmall
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reason,
                style = typography.bodySmall,
                color = colors.textSecondary
            )
            if (suggestedAction != null) {
                Text(
                    text = suggestedAction,
                    style = typography.labelSmall,
                    color = colors.accent.primary,
                    modifier = Modifier.padding(top = spacing.xxs)
                )
            }
        }
    }
}
