package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible, long-press contextual tooltip wrapper.
 * Automatically handles screen-edge bounding, dismiss-on-outside-tap, and TalkBack semantics.
 * Strictly <= 150 lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioTooltipBox(
    tooltipText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val typography = StudioTheme.typography
    val tooltipState = rememberTooltipState(isPersistent = false)

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            Box(
                modifier = Modifier
                    .background(colors.surfaceOverlay, RoundedCornerShape(cornerRadius.s))
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                    .padding(horizontal = spacing.s, vertical = spacing.xs)
                    .semantics { contentDescription = tooltipText }
            ) {
                Text(
                    text = tooltipText,
                    style = typography.bodySmall,
                    color = colors.textPrimary
                )
            }
        },
        state = tooltipState,
        modifier = modifier
    ) {
        content()
    }
}
