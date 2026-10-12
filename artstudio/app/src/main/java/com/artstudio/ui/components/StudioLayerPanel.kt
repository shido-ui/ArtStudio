package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artstudio.document.model.BlendModeType
import com.artstudio.document.model.LayerNode
import com.artstudio.layer.LayerManager
import com.artstudio.ui.theme.StudioTheme

/**
 * Visual tree representation item for layer hierarchy.
 */
data class LayerTreeVisualItem(
    val node: LayerNode,
    val depth: Int,
    val parentGroupId: String?,
    val isGroup: Boolean,
    val isExpanded: Boolean
)

/**
 * Accessible Layer Management Panel for digital art studio.
 * Provides controls for create, select, rename, duplicate, delete,
 * hide/show, reorder, lock/unlock, opacity adjustment, hierarchical groups,
 * multi-selection, merge, and explicit confirmation for document flattening.
 */
@Composable
fun StudioLayerPanel(
    layerManager: LayerManager,
    onDismissRequest: () -> Unit,
    onImportRasterRequested: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    // Reactive layer state
    var layersVersion by remember { mutableIntStateOf(0) }
    val selectedLayerId = remember(layersVersion) { layerManager.getSelectedLayerId() }
    val multiSelectedIds = remember(layersVersion) { layerManager.getSelectedLayerIds() }

    // Flatten tree into visual items according to group expansion states
    val visualItems = remember(layersVersion) {
        val items = mutableListOf<LayerTreeVisualItem>()
        fun collectChildren(group: LayerNode.LayerGroup, depth: Int) {
            // Reverse order: top-most drawn layer shown at top of panel
            for (child in group.children.asReversed()) {
                if (child is LayerNode.LayerGroup) {
                    val isExpanded = layerManager.uiState.isGroupExpanded(child.id)
                    items.add(LayerTreeVisualItem(child, depth, group.id, isGroup = true, isExpanded = isExpanded))
                    if (isExpanded) {
                        collectChildren(child, depth + 1)
                    }
                } else {
                    items.add(LayerTreeVisualItem(child, depth, group.id, isGroup = false, isExpanded = false))
                }
            }
        }
        collectChildren(layerManager.currentDocument.rootGroup, depth = 0)
        items
    }

    var showFlattenDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 320.dp, max = 400.dp)
            .clip(RoundedCornerShape(topStart = cornerRadius.l, bottomStart = cornerRadius.l))
            .background(colors.surfaceElevated.copy(alpha = 0.98f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(topStart = cornerRadius.l, bottomStart = cornerRadius.l))
            .padding(spacing.m)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            // Header: Title & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Layers (${layerManager.getAllLayers().size})",
                    style = typography.titleMedium,
                    color = colors.textPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Create group
                    StudioIconButton(
                        contentDescription = "Create group",
                        onClick = {
                            if (multiSelectedIds.isNotEmpty()) {
                                layerManager.createGroup(layerIdsToInclude = multiSelectedIds.toList())
                                layerManager.clearMultiSelection()
                            } else {
                                layerManager.createGroup()
                            }
                            layersVersion++
                        },
                        icon = { Text("📁", style = typography.titleMedium) }
                    )

                    // Add new raster layer
                    StudioIconButton(
                        contentDescription = "Create new layer",
                        onClick = {
                            layerManager.createLayer()
                            layersVersion++
                        },
                        icon = { Text("➕", style = typography.titleMedium) }
                    )

                    // Import image as layer
                    if (onImportRasterRequested != null) {
                        StudioIconButton(
                            contentDescription = "Import image as layer",
                            onClick = onImportRasterRequested,
                            icon = { Text("🖼️", style = typography.titleMedium) }
                        )
                    }

                    // Close panel
                    StudioIconButton(
                        contentDescription = "Close layers panel",
                        onClick = onDismissRequest,
                        icon = { Text("✖️", style = typography.titleSmall) }
                    )
                }
            }

            // Multi-Selection Action Bar
            if (multiSelectedIds.size >= 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerRadius.m))
                        .background(colors.accent.container.copy(alpha = 0.4f))
                        .padding(horizontal = spacing.s, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${multiSelectedIds.size} selected",
                        style = typography.labelSmall,
                        color = colors.accent.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        StudioButton(
                            text = "Group",
                            onClick = {
                                layerManager.createGroup(layerIdsToInclude = multiSelectedIds.toList())
                                layerManager.clearMultiSelection()
                                layersVersion++
                            },
                            variant = StudioButtonVariant.Primary,
                            modifier = Modifier.height(28.dp)
                        )

                        StudioButton(
                            text = "Merge",
                            onClick = {
                                layerManager.mergeSelectedLayers()
                                layersVersion++
                            },
                            variant = StudioButtonVariant.Secondary,
                            modifier = Modifier.height(28.dp)
                        )

                        StudioButton(
                            text = "Clear",
                            onClick = {
                                layerManager.clearMultiSelection()
                                layersVersion++
                            },
                            variant = StudioButtonVariant.Ghost,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // Document operations action bar (Merge Visible, Flatten)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioButton(
                    text = "Merge Visible",
                    onClick = {
                        layerManager.mergeVisibleLayers()
                        layersVersion++
                    },
                    variant = StudioButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )

                StudioButton(
                    text = "Flatten",
                    onClick = {
                        showFlattenDialog = true
                    },
                    variant = StudioButtonVariant.Destructive,
                    modifier = Modifier.weight(1f)
                )
            }

            // Hierarchical Layer list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                items(visualItems, key = { it.node.id }) { item ->
                    val layer = item.node
                    val isSelected = layer.id == selectedLayerId
                    val isMultiSelected = multiSelectedIds.contains(layer.id)
                    val indent = (item.depth * 16).dp

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indent)
                            .clip(RoundedCornerShape(cornerRadius.m))
                            .background(
                                if (isSelected || isMultiSelected) colors.accent.container.copy(alpha = 0.25f)
                                else colors.surfaceElevated
                            )
                            .border(
                                1.dp,
                                if (isSelected) colors.accent.primary else colors.outlineSubtle,
                                RoundedCornerShape(cornerRadius.m)
                            )
                            .padding(spacing.s),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        // Main row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.s)
                        ) {
                            // Expand/collapse icon for groups
                            if (item.isGroup) {
                                Text(
                                    text = if (item.isExpanded) "📂" else "📁",
                                    modifier = Modifier.clickable {
                                        layerManager.uiState.toggleGroupExpanded(layer.id)
                                        layersVersion++
                                    },
                                    style = typography.titleSmall
                                )
                            }

                            // Layer row details
                            Box(modifier = Modifier.weight(1f)) {
                                StudioLayerRow(
                                    layerName = layer.name,
                                    isSelected = isSelected,
                                    isVisible = layer.visible,
                                    isLocked = layer.isLocked,
                                    opacityPercent = (layer.opacity * 100).toInt(),
                                    blendMode = layer.blendMode,
                                    isClippingMask = layer.isClippingMask,
                                    isAlphaLocked = layer.isAlphaLocked,
                                    hasMask = layer.mask != null,
                                    isMaskEnabled = layer.mask?.enabled ?: true,
                                    isEditingMask = layerManager.isEditingMask() && isSelected,
                                    onSelectLayer = {
                                        layerManager.selectLayer(layer.id)
                                        layersVersion++
                                    },
                                    onToggleVisibility = {
                                        layerManager.toggleLayerVisibility(layer.id)
                                        layersVersion++
                                    },
                                    onToggleLock = {
                                        layerManager.toggleLayerLocked(layer.id)
                                        layersVersion++
                                    }
                                )
                            }

                            // Multi-selection toggle checkbox
                            Text(
                                text = if (isMultiSelected) "☑️" else "⬜",
                                modifier = Modifier.clickable {
                                    layerManager.toggleMultiSelect(layer.id)
                                    layersVersion++
                                },
                                style = typography.bodySmall
                            )
                        }

                        // Extended controls for selected layer
                        if (isSelected) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                // Opacity Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.s)
                                ) {
                                    Text(
                                        text = "Opacity",
                                        style = typography.labelSmall,
                                        color = colors.textSecondary
                                    )
                                    Slider(
                                        value = layer.opacity,
                                        onValueChange = { newOp ->
                                            layerManager.setLayerOpacity(layer.id, newOp)
                                            layersVersion++
                                        },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.accent.primary,
                                            activeTrackColor = colors.accent.primary
                                        )
                                    )
                                    Text(
                                        text = "${(layer.opacity * 100).toInt()}%",
                                        style = typography.labelSmall,
                                        color = colors.textPrimary
                                    )
                                }

                                // Clipping & Alpha Lock quick-toggle row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                                ) {
                                    // Clipping Mask Pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(cornerRadius.pill))
                                            .background(if (layer.isClippingMask) colors.accent.primary else colors.surfaceElevated)
                                            .border(
                                                1.dp,
                                                if (layer.isClippingMask) colors.accent.primary else colors.outlineSubtle,
                                                RoundedCornerShape(cornerRadius.pill)
                                            )
                                            .clickable {
                                                layerManager.toggleLayerClippingMask(layer.id)
                                                layersVersion++
                                            }
                                            .padding(horizontal = spacing.s, vertical = spacing.xs)
                                    ) {
                                        Text(
                                            text = if (layer.isClippingMask) "✂️ Clipping: ON" else "✂️ Clipping: OFF",
                                            style = typography.labelSmall,
                                            color = if (layer.isClippingMask) colors.accent.onPrimary else colors.textPrimary
                                        )
                                    }

                                    // Alpha Lock Pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(cornerRadius.pill))
                                            .background(if (layer.isAlphaLocked) colors.warning else colors.surfaceElevated)
                                            .border(
                                                1.dp,
                                                if (layer.isAlphaLocked) colors.warning else colors.outlineSubtle,
                                                RoundedCornerShape(cornerRadius.pill)
                                            )
                                            .clickable {
                                                layerManager.toggleLayerAlphaLock(layer.id)
                                                layersVersion++
                                            }
                                            .padding(horizontal = spacing.s, vertical = spacing.xs)
                                    ) {
                                        Text(
                                            text = if (layer.isAlphaLocked) "🔒α Alpha Lock: ON" else "🔒α Alpha Lock: OFF",
                                            style = typography.labelSmall,
                                            color = if (layer.isAlphaLocked) colors.surfaceElevated else colors.textPrimary
                                        )
                                    }
                                }

                                // Layer Mask Controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (layer.mask == null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(cornerRadius.pill))
                                                .background(colors.surfaceElevated)
                                                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.pill))
                                                .clickable {
                                                    layerManager.addLayerMask(layer.id)
                                                    layersVersion++
                                                }
                                                .padding(horizontal = spacing.s, vertical = spacing.xs)
                                        ) {
                                            Text(
                                                text = "🎭 + Add Mask",
                                                style = typography.labelSmall,
                                                color = colors.textPrimary
                                            )
                                        }
                                    } else {
                                        val mask = layer.mask!!
                                        // Toggle Mask Enabled
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(cornerRadius.pill))
                                                .background(if (mask.enabled) colors.accent.primary else colors.surfaceElevated)
                                                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.pill))
                                                .clickable {
                                                    layerManager.toggleMaskEnabled(layer.id)
                                                    layersVersion++
                                                }
                                                .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                                        ) {
                                            Text(
                                                text = if (mask.enabled) "🎭 On" else "🎭 Off",
                                                style = typography.labelSmall,
                                                color = if (mask.enabled) colors.accent.onPrimary else colors.textTertiary
                                            )
                                        }

                                        // Invert Mask
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(cornerRadius.pill))
                                                .background(if (mask.inverted) colors.accent.primary else colors.surfaceElevated)
                                                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.pill))
                                                .clickable {
                                                    layerManager.invertMask(layer.id)
                                                    layersVersion++
                                                }
                                                .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                                        ) {
                                            Text(
                                                text = if (mask.inverted) "🔄 Inverted" else "🔄 Normal",
                                                style = typography.labelSmall,
                                                color = if (mask.inverted) colors.accent.onPrimary else colors.textPrimary
                                            )
                                        }

                                        // Edit Target (Content vs Mask)
                                        val isEditingMask = layerManager.isEditingMask()
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(cornerRadius.pill))
                                                .background(if (isEditingMask) colors.accent.container else colors.surfaceElevated)
                                                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.pill))
                                                .clickable {
                                                    layerManager.setEditTarget(
                                                        if (isEditingMask) com.artstudio.layer.LayerEditTarget.CONTENT
                                                        else com.artstudio.layer.LayerEditTarget.MASK
                                                    )
                                                    layersVersion++
                                                }
                                                .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                                        ) {
                                            Text(
                                                text = if (isEditingMask) "🖌️ Edit Mask" else "✏️ Edit Content",
                                                style = typography.labelSmall,
                                                color = if (isEditingMask) colors.accent.primary else colors.textPrimary
                                            )
                                        }

                                        // Remove Mask
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(cornerRadius.pill))
                                                .background(colors.surfaceElevated)
                                                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.pill))
                                                .clickable {
                                                    layerManager.removeLayerMask(layer.id)
                                                    layersVersion++
                                                }
                                                .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                                        ) {
                                            Text(
                                                text = "🗑️",
                                                style = typography.labelSmall,
                                                color = colors.warning
                                            )
                                        }
                                    }
                                }

                                // Blend Mode Selector
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                                ) {
                                    Text(
                                        text = "Blend Mode: ${layer.blendMode.displayName}",
                                        style = typography.labelSmall,
                                        color = colors.textSecondary
                                    )
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                                    ) {
                                        items(BlendModeType.supportedModes, key = { it.key }) { mode ->
                                            val isCurrent = layer.blendMode == mode
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(cornerRadius.pill))
                                                    .background(if (isCurrent) colors.accent.primary else colors.surfaceElevated)
                                                    .border(
                                                        1.dp,
                                                        if (isCurrent) colors.accent.primary else colors.outlineSubtle,
                                                        RoundedCornerShape(cornerRadius.pill)
                                                    )
                                                    .clickable {
                                                        layerManager.setLayerBlendMode(layer.id, mode)
                                                        layersVersion++
                                                    }
                                                    .padding(horizontal = spacing.s, vertical = spacing.xs)
                                            ) {
                                                Text(
                                                    text = mode.displayName,
                                                    style = typography.labelSmall,
                                                    color = if (isCurrent) colors.accent.onPrimary else colors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Secondary Action Bar: Move Up, Move Down, Duplicate, Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                        StudioIconButton(
                                            contentDescription = "Move layer up",
                                            onClick = {
                                                layerManager.moveLayerUp(layer.id)
                                                layersVersion++
                                            },
                                            icon = { Text("⬆️", style = typography.bodyMedium) }
                                        )

                                        StudioIconButton(
                                            contentDescription = "Move layer down",
                                            onClick = {
                                                layerManager.moveLayerDown(layer.id)
                                                layersVersion++
                                            },
                                            icon = { Text("⬇️", style = typography.bodyMedium) }
                                        )

                                        if (item.depth > 0) {
                                            StudioIconButton(
                                                contentDescription = "Move out of group",
                                                onClick = {
                                                    layerManager.moveLayerOutOfGroup(layer.id)
                                                    layersVersion++
                                                },
                                                icon = { Text("📤", style = typography.bodyMedium) }
                                            )
                                        }

                                        if (item.isGroup) {
                                            StudioIconButton(
                                                contentDescription = "Ungroup",
                                                onClick = {
                                                    layerManager.ungroup(layer.id)
                                                    layersVersion++
                                                },
                                                icon = { Text("🔓", style = typography.bodyMedium) }
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                        if (!item.isGroup) {
                                            StudioIconButton(
                                                contentDescription = "Duplicate layer",
                                                onClick = {
                                                    layerManager.duplicateLayer(layer.id)
                                                    layersVersion++
                                                },
                                                icon = { Text("📑", style = typography.bodyMedium) }
                                            )
                                        }

                                        StudioIconButton(
                                            contentDescription = "Delete layer",
                                            onClick = {
                                                layerManager.deleteLayer(layer.id)
                                                layersVersion++
                                            },
                                            icon = { Text("🗑️", style = typography.bodyMedium) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Explicit Flatten Confirmation Dialog
    if (showFlattenDialog) {
        StudioDialog(
            title = "Flatten Artwork?",
            body = "This will combine all layers into a single background layer. This action can be undone through history.",
            confirmText = "Flatten Artwork",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showFlattenDialog = false
                layerManager.flattenDocument(confirmed = true)
                layersVersion++
            },
            onDismiss = {
                showFlattenDialog = false
            }
        )
    }
}

