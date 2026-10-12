package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.selection.SelectionCombineMode
import com.artstudio.selection.SelectionManager
import com.artstudio.selection.SelectionToolType
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible, touch-first Selection Control Toolbar.
 * Provides tool selection (Rect, Ellipse, Lasso, Poly),
 * boolean combination modes (Replace, Add, Subtract, Intersect),
 * and precision mask actions (Invert, Clear, Expand, Contract, Feather).
 *
 * Adheres strictly to the >= 48dp minimum touch target standard and WCAG AAA contrast.
 */
@Composable
fun StudioSelectionBar(
    selectionManager: SelectionManager,
    modifier: Modifier = Modifier,
    onExpandClicked: () -> Unit = { selectionManager.expand(5) },
    onContractClicked: () -> Unit = { selectionManager.contract(5) },
    onFeatherClicked: () -> Unit = { selectionManager.feather(3f) }
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val typography = StudioTheme.typography

    val hasSelection = selectionManager.hasSelection
    val activeTool = selectionManager.activeTool
    val combineMode = selectionManager.combineMode

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StudioAccessibility.MinTouchTarget)
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
            .padding(horizontal = spacing.s, vertical = spacing.xs)
            .horizontalScroll(scrollState)
            .semantics { contentDescription = "Selection Toolbar" },
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 1. Selection Tool Type Selector ---
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionToolType.entries.forEach { tool ->
                val isSelected = activeTool == tool
                StudioIconButton(
                    contentDescription = "Select ${tool.displayName} Tool",
                    active = isSelected,
                    onClick = { selectionManager.activeTool = tool },
                    modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
                ) { tint ->
                    Text(
                        text = when (tool) {
                            SelectionToolType.RECTANGLE -> "□"
                            SelectionToolType.ELLIPSE -> "○"
                            SelectionToolType.LASSO -> "⚬"
                            SelectionToolType.POLYGON -> "⬡"
                        },
                        style = typography.titleMedium,
                        color = tint
                    )
                }
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(colors.outlineSubtle)
        )

        // --- 2. Selection Combination Mode ---
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionCombineMode.entries.forEach { mode ->
                val isSelected = combineMode == mode
                StudioIconButton(
                    contentDescription = "Mode ${mode.displayName}",
                    active = isSelected,
                    onClick = { selectionManager.combineMode = mode },
                    modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
                ) { tint ->
                    Text(
                        text = when (mode) {
                            SelectionCombineMode.REPLACE -> "New"
                            SelectionCombineMode.ADD -> "+"
                            SelectionCombineMode.SUBTRACT -> "-"
                            SelectionCombineMode.INTERSECT -> "∩"
                        },
                        style = typography.bodySmall,
                        color = tint
                    )
                }
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(colors.outlineSubtle)
        )

        // --- 3. Precision Selection Actions ---
        // Invert Selection
        StudioIconButton(
            contentDescription = "Invert Selection",
            onClick = { selectionManager.invert() },
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            Text(text = "Inv", style = typography.bodySmall, color = tint)
        }

        // Clear Selection
        StudioIconButton(
            contentDescription = "Clear Selection",
            enabled = hasSelection,
            disabledExplanation = "No active selection to clear",
            onClick = { selectionManager.clear() },
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            Text(text = "Clr", style = typography.bodySmall, color = tint)
        }

        // Expand Selection
        StudioIconButton(
            contentDescription = "Expand Selection",
            enabled = hasSelection,
            disabledExplanation = "No active selection to expand",
            onClick = onExpandClicked,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            Text(text = "+px", style = typography.bodySmall, color = tint)
        }

        // Contract Selection
        StudioIconButton(
            contentDescription = "Contract Selection",
            enabled = hasSelection,
            disabledExplanation = "No active selection to contract",
            onClick = onContractClicked,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            Text(text = "-px", style = typography.bodySmall, color = tint)
        }

        // Feather Selection
        StudioIconButton(
            contentDescription = "Feather Selection",
            enabled = hasSelection,
            disabledExplanation = "No active selection to feather",
            onClick = onFeatherClicked,
            modifier = Modifier.size(StudioAccessibility.MinTouchTarget)
        ) { tint ->
            Text(text = "~F", style = typography.bodySmall, color = tint)
        }
    }
}
