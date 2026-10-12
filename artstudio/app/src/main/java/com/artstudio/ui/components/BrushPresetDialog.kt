package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.artstudio.brush.BrushPreset
import com.artstudio.brush.BrushPresetRepository
import com.artstudio.core.BrushConfig
import com.artstudio.ui.theme.StudioTheme

/**
 * Brush Preset Selector & Management Dialog.
 * Allows artists to browse categorized presets, search, toggle favorites,
 * duplicate, rename, delete custom presets, and select an active preset.
 */
@Composable
fun BrushPresetDialog(
    presetRepository: BrushPresetRepository,
    selectedPresetId: String?,
    onPresetSelected: (BrushPreset) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val categories = remember { presetRepository.getCategories() }

    var allPresets by remember { mutableStateOf(presetRepository.getAllPresets()) }

    fun refresh() {
        allPresets = if (searchQuery.isNotBlank()) {
            presetRepository.searchPresets(searchQuery)
        } else {
            presetRepository.getPresetsByCategory(selectedCategory)
        }
    }

    LaunchedEffect(selectedCategory, searchQuery) {
        refresh()
    }

    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    StudioDialog(
        title = "Brush Presets & Dynamics",
        body = "Select, customize, or clone brush presets for the manual studio.",
        confirmText = "Close",
        cancelText = null,
        onConfirm = onDismissRequest,
        onDismiss = onDismissRequest,
        content = {
            Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.m)
        ) {
            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isCatSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(cornerRadius.m))
                            .background(if (isCatSelected) colors.accent.primary else colors.surfaceElevated)
                            .border(
                                1.dp,
                                if (isCatSelected) colors.accent.primary else colors.outlineSubtle,
                                RoundedCornerShape(cornerRadius.m)
                            )
                            .clickable {
                                selectedCategory = cat
                                searchQuery = ""
                            }
                            .padding(horizontal = spacing.m, vertical = spacing.xs)
                    ) {
                        Text(
                            text = cat,
                            style = typography.labelMedium,
                            color = if (isCatSelected) colors.accent.onPrimary else colors.textPrimary
                        )
                    }
                }
            }

            // Presets List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                items(allPresets) { preset ->
                    val isSelected = preset.id == selectedPresetId

                    StudioTooltipBox(
                        tooltipText = "${preset.name}: ${preset.category} brush preset, size ${preset.config.size.toInt()}px"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(cornerRadius.m))
                                .background(if (isSelected) colors.accent.container else colors.surfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accent.primary else colors.outlineSubtle,
                                    RoundedCornerShape(cornerRadius.m)
                                )
                                .clickable {
                                    onPresetSelected(preset)
                                    onDismissRequest()
                                }
                                .padding(spacing.m),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                                ) {
                                    Text(
                                        text = preset.name,
                                        style = typography.titleSmall,
                                        color = colors.textPrimary
                                    )
                                    if (preset.isBuiltIn) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(cornerRadius.xs))
                                                .background(colors.surfaceBase)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Core",
                                                style = typography.labelSmall,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${preset.category} • Size: ${preset.config.size.toInt()}px • Hardness: ${(preset.config.hardness * 100).toInt()}%",
                                    style = typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                // Favorite toggle button
                                StudioIconButton(
                                    contentDescription = if (preset.isFavorite) "Remove favorite" else "Mark favorite",
                                    onClick = {
                                        presetRepository.toggleFavorite(preset.id)
                                        refresh()
                                    },
                                    active = preset.isFavorite,
                                    icon = { Text(if (preset.isFavorite) "⭐" else "☆", style = typography.titleMedium) }
                                )

                                // Duplicate preset button
                                StudioIconButton(
                                    contentDescription = "Duplicate preset",
                                    onClick = {
                                        presetRepository.duplicatePreset(preset.id)
                                        refresh()
                                    },
                                    icon = { Text("📋", style = typography.titleMedium) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
)
}
