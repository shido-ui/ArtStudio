package com.artstudio.ui.navigation

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Compact Phone Bottom Navigation Bar.
 * Keeps touch targets >= 48dp while maintaining a compact vertical footprint
 * to preserve maximum vertical screen area for the drawing canvas.
 */
@Composable
fun StudioPhoneBottomBar(
    currentDestination: StudioDestination,
    onNavigate: (StudioDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(colors.surfaceBase)
            .border(1.dp, colors.outlineSubtle)
            .padding(horizontal = spacing.xs, vertical = spacing.xxs),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StudioDestination.entries.forEach { destination ->
            val isSelected = destination == currentDestination

            Column(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = StudioAccessibility.MinTouchTarget)
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .clickable(
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onNavigate(destination) }
                    )
                    .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                    .semantics {
                        this.role = Role.Tab
                        this.contentDescription = "${destination.title} tab, ${if (isSelected) "selected" else "unselected"}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = destination.iconSymbol,
                    style = typography.titleSmall
                )
                Text(
                    text = destination.title,
                    style = typography.labelSmall,
                    color = if (isSelected) colors.accent.primary else colors.textSecondary
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(width = 16.dp, height = 2.dp)
                            .clip(CircleShape)
                            .background(colors.accent.primary)
                    )
                }
            }
        }
    }
}

/**
 * Tablet / Wide-Screen Navigation Rail.
 * Placed on the start/left edge in split view.
 * Leaves the entire center viewport available for multi-layer high-resolution canvas editing.
 */
@Composable
fun StudioTabletNavigationRail(
    currentDestination: StudioDestination,
    onNavigate: (StudioDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(colors.surfaceBase)
            .border(1.dp, colors.outlineSubtle)
            .padding(vertical = spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // App Studio Branding mark
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(colors.accent.container),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AS",
                style = typography.titleMedium,
                color = colors.accent.primary
            )
        }

        Spacer(modifier = Modifier.height(spacing.s))

        StudioDestination.entries.forEach { destination ->
            val isSelected = destination == currentDestination

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = StudioAccessibility.MinTouchTarget)
                    .clip(RoundedCornerShape(cornerRadius.m))
                    .clickable(
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onNavigate(destination) }
                    )
                    .background(
                        if (isSelected) colors.accent.container.copy(alpha = 0.5f) else colors.surfaceBase
                    )
                    .padding(vertical = spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = destination.iconSymbol,
                    style = typography.titleMedium
                )
                Text(
                    text = destination.title,
                    style = typography.labelSmall,
                    color = if (isSelected) colors.accent.primary else colors.textSecondary
                )
            }
        }
    }
}
