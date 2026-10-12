package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artstudio.reference.region.*
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioTheme

/**
 * Accessible Compose drawer for marking and managing Reference Focus Regions of interest.
 *
 * Supports:
 * - Tool selection (Brush, Lasso, Polygon, Rectangle, Ellipse).
 * - Combination modes (Replace, Add, Subtract, Clear).
 * - Multi-region listing with Rename, Duplicate, Delete, Hide/Show, and Boundary Editing.
 * - Independent region instructions and focus priority settings.
 * - Overlay opacity adjustment.
 */
@Composable
fun StudioReferenceRegionPanel(
    project: ReferenceAnalysisProject,
    activeTool: ReferenceRegionToolType,
    combineMode: RegionCombineMode,
    brushRadius: Float,
    onSelectTool: (ReferenceRegionToolType) -> Unit,
    onSelectCombineMode: (RegionCombineMode) -> Unit,
    onChangeBrushRadius: (Float) -> Unit,
    onSelectActiveRegion: (String) -> Unit,
    onCreateRegion: (String) -> Unit,
    onRenameRegion: (String, String) -> Unit,
    onDuplicateRegion: (String) -> Unit,
    onDeleteRegion: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onUpdateInstructions: (String, String) -> Unit,
    onUpdateOpacity: (String, Float) -> Unit,
    onUpdatePriority: (String, FocusPriority) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val typography = StudioTheme.typography

    val scrollState = rememberScrollState()
    val activeReg = project.activeRegion

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showNewRegionDialog by remember { mutableStateOf(false) }
    var newRegionText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
            .padding(spacing.m)
            .verticalScroll(scrollState)
            .semantics { contentDescription = "Reference Region Focus Panel" },
        verticalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        // ---------------------------------------------------------------------
        // Header
        // ---------------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Reference Focus Regions",
                    style = typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${project.regions.size} marked regions on ${project.referenceSource.sourceName}",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            StudioButton(
                text = "➕ Add Region",
                onClick = {
                    newRegionText = "Region ${project.regions.size + 1}"
                    showNewRegionDialog = true
                },
                modifier = Modifier.defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
            )
        }

        // ---------------------------------------------------------------------
        // Selection Tools Selector
        // ---------------------------------------------------------------------
        Text(
            text = "Selection Tool",
            style = typography.labelMedium,
            color = colors.textSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            ReferenceRegionToolType.entries.forEach { tool ->
                val isSelected = (tool == activeTool)
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(if (isSelected) colors.accent.primary else colors.surfaceElevated)
                        .border(
                            1.dp,
                            if (isSelected) colors.accent.primary else colors.outlineSubtle,
                            RoundedCornerShape(cornerRadius.s)
                        )
                        .clickable { onSelectTool(tool) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = "Select tool: ${tool.displayName}"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${tool.icon} ${tool.displayName}",
                        fontSize = 12.sp,
                        color = if (isSelected) colors.accent.onPrimary else colors.textPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Brush Radius Slider if Brush is Active
        if (activeTool == ReferenceRegionToolType.BRUSH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Brush Radius: ${brushRadius.toInt()} px",
                    style = typography.bodySmall,
                    color = colors.textPrimary
                )
            }
            Slider(
                value = brushRadius,
                onValueChange = onChangeBrushRadius,
                valueRange = 1f..100f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ---------------------------------------------------------------------
        // Combine Modes
        // ---------------------------------------------------------------------
        Text(
            text = "Combine Mode",
            style = typography.labelMedium,
            color = colors.textSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            RegionCombineMode.entries.forEach { mode ->
                val isSelected = (mode == combineMode)
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                        .clip(RoundedCornerShape(cornerRadius.s))
                        .background(if (isSelected) colors.accent.primary else colors.surfaceElevated)
                        .border(
                            1.dp,
                            if (isSelected) colors.accent.primary else colors.outlineSubtle,
                            RoundedCornerShape(cornerRadius.s)
                        )
                        .clickable { onSelectCombineMode(mode) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = "Combine mode: ${mode.displayName}"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${mode.symbol} ${mode.displayName}",
                        fontSize = 12.sp,
                        color = if (isSelected) colors.accent.onPrimary else colors.textPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Divider(color = colors.outlineSubtle)

        // ---------------------------------------------------------------------
        // Region List
        // ---------------------------------------------------------------------
        Text(
            text = "Marked Regions",
            style = typography.labelMedium,
            color = colors.textSecondary
        )

        if (project.regions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.m),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No focus regions marked yet. Click 'Add Region' to start.",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                project.regions.forEach { reg ->
                    val isActive = (reg.id == project.activeRegionId)
                    val tintColor = Color(reg.colorRgba)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                            .clip(RoundedCornerShape(cornerRadius.s))
                            .background(if (isActive) colors.accent.primary.copy(alpha = 0.15f) else colors.surfaceElevated)
                            .border(
                                1.dp,
                                if (isActive) colors.accent.primary else colors.outlineSubtle,
                                RoundedCornerShape(cornerRadius.s)
                            )
                            .clickable { onSelectActiveRegion(reg.id) }
                            .padding(horizontal = spacing.s, vertical = spacing.xs)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Region ${reg.name}"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.s)
                        ) {
                            // Color Pip
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(tintColor)
                            )

                            Column {
                                Text(
                                    text = reg.name,
                                    style = typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${reg.selectedPixelCount} px | ${reg.settings.priority.displayName} Priority",
                                    fontSize = 10.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        // Actions: Visibility, Duplicate, Delete
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            // Hide/Show Toggle
                            IconButton(
                                onClick = { onToggleVisibility(reg.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(text = if (reg.isVisible) "👁️" else "🙈", fontSize = 14.sp)
                            }

                            // Duplicate
                            IconButton(
                                onClick = { onDuplicateRegion(reg.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(text = "📋", fontSize = 14.sp)
                            }

                            // Delete
                            IconButton(
                                onClick = { onDeleteRegion(reg.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(text = "❌", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // Active Region Details & Instructions
        // ---------------------------------------------------------------------
        if (activeReg != null) {
            Divider(color = colors.outlineSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Editing: ${activeReg.name}",
                    style = typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                StudioButton(
                    text = "✏️ Rename",
                    onClick = {
                        renameText = activeReg.name
                        showRenameDialog = true
                    },
                    modifier = Modifier.defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                )
            }

            // Opacity slider
            Text(
                text = "Overlay Opacity: ${(activeReg.overlayOpacity * 100).toInt()}%",
                style = typography.bodySmall,
                color = colors.textSecondary
            )
            Slider(
                value = activeReg.overlayOpacity,
                onValueChange = { onUpdateOpacity(activeReg.id, it) },
                valueRange = 0.05f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )

            // Focus Priority Selector
            Text(
                text = "Reconstruction Priority",
                style = typography.labelMedium,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                FocusPriority.entries.forEach { p ->
                    val isSelected = (p == activeReg.settings.priority)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = StudioAccessibility.MinTouchTarget)
                            .clip(RoundedCornerShape(cornerRadius.s))
                            .background(if (isSelected) colors.accent.primary else colors.surfaceElevated)
                            .border(
                                1.dp,
                                if (isSelected) colors.accent.primary else colors.outlineSubtle,
                                RoundedCornerShape(cornerRadius.s)
                            )
                            .clickable { onUpdatePriority(activeReg.id, p) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = p.displayName,
                            fontSize = 11.sp,
                            color = if (isSelected) colors.accent.onPrimary else colors.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Instructions Text Field
            Text(
                text = "Instructions / Prompt for this Region",
                style = typography.labelMedium,
                color = colors.textSecondary
            )
            OutlinedTextField(
                value = activeReg.instructions,
                onValueChange = { onUpdateInstructions(activeReg.id, it) },
                placeholder = { Text("e.g. Keep fine line art; preserve eyelashes; cel shading...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }
    }

    // New Region Dialog
    if (showNewRegionDialog) {
        AlertDialog(
            onDismissRequest = { showNewRegionDialog = false },
            title = { Text("Add Focus Region") },
            text = {
                OutlinedTextField(
                    value = newRegionText,
                    onValueChange = { newRegionText = it },
                    label = { Text("Region Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newRegionText.isNotBlank()) {
                        onCreateRegion(newRegionText.trim())
                    }
                    showNewRegionDialog = false
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewRegionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog && activeReg != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Region") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameRegion(activeReg.id, renameText.trim())
                    }
                    showRenameDialog = false
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
