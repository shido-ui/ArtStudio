package com.artstudio.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible Tabs component for sub-section navigation.
 */
@Composable
fun <T> StudioTabs(
    tabs: List<T>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    tabLabel: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StudioAccessibility.MinTouchTarget)
            .background(colors.surfaceElevated),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selectedTab
            val label = tabLabel(tab)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                    .clickable(
                        enabled = enabled,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTabSelected(tab) }
                    )
                    .semantics {
                        this.role = Role.Tab
                        this.contentDescription = "$label tab, ${if (isSelected) "selected" else "not selected"}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = if (isSelected) typography.labelLarge else typography.labelMedium,
                    color = if (isSelected) colors.accent.primary else colors.textSecondary,
                    modifier = Modifier.padding(vertical = spacing.s)
                )

                // Underline indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(cornerRadius.pill))
                        .background(if (isSelected) colors.accent.primary else colors.surfaceElevated)
                )
            }
        }
    }
}
