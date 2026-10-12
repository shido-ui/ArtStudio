package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artstudio.importer.DecodedRasterImage
import com.artstudio.importer.ImportDestination
import com.artstudio.importer.ImportFitMode
import com.artstudio.importer.ImportPlacementConfig
import com.artstudio.ui.theme.StudioTheme

/**
 * Modal dialog for inspecting and placing an imported raster image.
 * Provides interactive placement, scaling, and opacity controls before committing
 * to the chosen destination (Layer, Reference Overlay, or New Project).
 */
@Composable
fun StudioRasterImportDialog(
    decodedImage: DecodedRasterImage,
    canvasWidth: Int,
    canvasHeight: Int,
    onConfirmImport: (destination: ImportDestination, placement: ImportPlacementConfig) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    var selectedDestination by remember { mutableStateOf(ImportDestination.IMPORT_AS_LAYER) }
    var selectedFitMode by remember { mutableStateOf(ImportFitMode.FIT_CANVAS) }
    var scaleValue by remember { mutableFloatStateOf(1f) }
    var opacityValue by remember { mutableFloatStateOf(1.0f) }
    var rotationValue by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val meta = decodedImage.metadata

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickableNoRipple(onDismissRequest),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 520.dp)
                .clip(RoundedCornerShape(cornerRadius.l))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                .padding(spacing.l)
                .clickableNoRipple { /* Consume clicks inside dialog */ }
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.m)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Import Raster Image",
                        style = typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = meta.fileName,
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                StudioIconButton(
                    contentDescription = "Close dialog",
                    onClick = onDismissRequest,
                    icon = { Text("✖️", style = typography.titleSmall) }
                )
            }

            // Metadata Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                MetaBadge(text = meta.format)
                MetaBadge(text = "${decodedImage.width} × ${decodedImage.height} px")
                if (meta.hasAlpha) {
                    MetaBadge(text = "Alpha (Transparent)")
                }
                if (meta.exifOrientation != 1) {
                    MetaBadge(text = "EXIF ${meta.exifOrientation}")
                }
                if (meta.wasDownsampled) {
                    MetaBadge(text = "Resized to Fit Budget")
                }
            }

            // Destination Selector
            Text(
                text = "Destination",
                style = typography.labelMedium,
                color = colors.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                DestinationChip(
                    text = "New Layer",
                    selected = selectedDestination == ImportDestination.IMPORT_AS_LAYER,
                    onClick = { selectedDestination = ImportDestination.IMPORT_AS_LAYER },
                    modifier = Modifier.weight(1f)
                )
                DestinationChip(
                    text = "Reference",
                    selected = selectedDestination == ImportDestination.ADD_AS_REFERENCE,
                    onClick = { selectedDestination = ImportDestination.ADD_AS_REFERENCE },
                    modifier = Modifier.weight(1f)
                )
                DestinationChip(
                    text = "New Project",
                    selected = selectedDestination == ImportDestination.OPEN_AS_NEW_PROJECT,
                    onClick = { selectedDestination = ImportDestination.OPEN_AS_NEW_PROJECT },
                    modifier = Modifier.weight(1f)
                )
            }

            // Placement Controls (When importing into existing canvas)
            if (selectedDestination != ImportDestination.OPEN_AS_NEW_PROJECT) {
                Text(
                    text = "Placement & Sizing",
                    style = typography.labelMedium,
                    color = colors.textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    FitModeChip(
                        text = "Fit",
                        selected = selectedFitMode == ImportFitMode.FIT_CANVAS,
                        onClick = { selectedFitMode = ImportFitMode.FIT_CANVAS },
                        modifier = Modifier.weight(1f)
                    )
                    FitModeChip(
                        text = "Fill",
                        selected = selectedFitMode == ImportFitMode.FILL_CANVAS,
                        onClick = { selectedFitMode = ImportFitMode.FILL_CANVAS },
                        modifier = Modifier.weight(1f)
                    )
                    FitModeChip(
                        text = "1:1",
                        selected = selectedFitMode == ImportFitMode.ORIGINAL_SIZE,
                        onClick = { selectedFitMode = ImportFitMode.ORIGINAL_SIZE },
                        modifier = Modifier.weight(1f)
                    )
                    FitModeChip(
                        text = "Custom",
                        selected = selectedFitMode == ImportFitMode.CUSTOM,
                        onClick = { selectedFitMode = ImportFitMode.CUSTOM },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selectedFitMode == ImportFitMode.CUSTOM) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Scale", style = typography.bodySmall, color = colors.textPrimary)
                            Text(String.format("%.2fx", scaleValue), style = typography.bodySmall, color = colors.textSecondary)
                        }
                        Slider(
                            value = scaleValue,
                            onValueChange = { scaleValue = it },
                            valueRange = 0.1f..4.0f
                        )
                    }
                }

                // Opacity Slider
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity", style = typography.bodySmall, color = colors.textPrimary)
                        Text("${(opacityValue * 100).toInt()}%", style = typography.bodySmall, color = colors.textSecondary)
                    }
                    Slider(
                        value = opacityValue,
                        onValueChange = { opacityValue = it },
                        valueRange = 0.05f..1.0f
                    )
                }

                // Rotation Angle Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rotate: ", style = typography.bodySmall, color = colors.textSecondary)
                    listOf(0f, 90f, 180f, 270f).forEach { deg ->
                        StudioButton(
                            text = "${deg.toInt()}°",
                            variant = if (rotationValue == deg) StudioButtonVariant.Primary else StudioButtonVariant.Secondary,
                            onClick = { rotationValue = deg },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.s))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.m)
            ) {
                StudioButton(
                    text = "Cancel",
                    variant = StudioButtonVariant.Secondary,
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                )

                StudioButton(
                    text = when (selectedDestination) {
                        ImportDestination.IMPORT_AS_LAYER -> "Import as Layer"
                        ImportDestination.ADD_AS_REFERENCE -> "Add as Reference"
                        ImportDestination.OPEN_AS_NEW_PROJECT -> "Open as Project"
                    },
                    variant = StudioButtonVariant.Primary,
                    onClick = {
                        val config = ImportPlacementConfig(
                            x = offsetX,
                            y = offsetY,
                            scale = scaleValue,
                            rotationDegrees = rotationValue,
                            opacity = opacityValue,
                            fitMode = selectedFitMode
                        )
                        onConfirmImport(selectedDestination, config)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius.s))
            .background(colors.surfaceBase)
            .padding(horizontal = spacing.s, vertical = spacing.xxs)
    ) {
        Text(
            text = text,
            style = typography.labelSmall,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun DestinationChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.m))
            .background(if (selected) colors.accent.primary else colors.surfaceBase)
            .clickableNoRipple(onClick)
            .padding(vertical = spacing.s),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.labelMedium,
            color = if (selected) colors.accent.onPrimary else colors.textPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun FitModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.s))
            .background(if (selected) colors.surfaceElevated else colors.surfaceBase)
            .border(
                1.dp,
                if (selected) colors.accent.primary else Color.Transparent,
                RoundedCornerShape(cornerRadius.s)
            )
            .clickableNoRipple(onClick)
            .padding(vertical = spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.labelSmall,
            color = if (selected) colors.accent.primary else colors.textSecondary
        )
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = null,
        indication = null,
        onClick = onClick
    )
)
