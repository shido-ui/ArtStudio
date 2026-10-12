package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artstudio.document.model.ArtDocument
import com.artstudio.export.*
import com.artstudio.ui.theme.StudioTheme
import kotlin.math.roundToInt

/**
 * Artist-centric modal dialog for configuring and executing high-fidelity raster exports.
 * Provides explicit format selection (PNG/JPEG/WebP), lossy/lossless controls,
 * layer/group/flattened scope choices, transparent background toggle, and dimensions scaling.
 */
@Composable
fun StudioExportDialog(
    document: ArtDocument,
    selectedLayerName: String? = null,
    isExporting: Boolean = false,
    exportResult: ExportResult? = null,
    onConfirmExport: (ExportConfig, String) -> Unit,
    onCancelExport: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius

    var selectedFormat by remember { mutableStateOf(ExportFormat.PNG) }
    var qualityValue by remember { mutableFloatStateOf(90f) }
    var selectedScope by remember { mutableStateOf(ExportScope.FULL_CANVAS) }
    var transparentBg by remember { mutableStateOf(false) }
    var scalePreset by remember { mutableFloatStateOf(1.0f) }
    var filename by remember {
        mutableStateOf("${document.title.replace("\\s+".toRegex(), "_")}")
    }
    var conflictStrategy by remember { mutableStateOf(FilenameConflictStrategy.AUTO_RENAME) }

    val canvasW = document.canvas.width
    val canvasH = document.canvas.height
    val finalW = (canvasW * scalePreset).roundToInt()
    val finalH = (canvasH * scalePreset).roundToInt()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickableNoRipple(if (!isExporting) onDismissRequest else onCancelExport),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 540.dp)
                .clip(RoundedCornerShape(cornerRadius.l))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                .padding(spacing.l)
                .clickableNoRipple { /* Consume inner clicks */ }
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
                        text = "Export Artwork",
                        style = typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${finalW} × ${finalH} px (${scalePreset}x)",
                        style = typography.labelMedium,
                        color = colors.accent.primary
                    )
                }

                if (!isExporting) {
                    StudioIconButton(
                        contentDescription = "Close",
                        onClick = onDismissRequest,
                        icon = { Text("✕", style = typography.titleMedium) }
                    )
                }
            }

            // Export Format Tabs
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "Format",
                    style = typography.labelMedium,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    ExportFormatTab(
                        label = "PNG",
                        sub = "Lossless",
                        selected = selectedFormat == ExportFormat.PNG,
                        onClick = {
                            selectedFormat = ExportFormat.PNG
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ExportFormatTab(
                        label = "JPEG",
                        sub = "Opaque",
                        selected = selectedFormat == ExportFormat.JPEG,
                        onClick = {
                            selectedFormat = ExportFormat.JPEG
                            transparentBg = false // JPEG cannot have alpha
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ExportFormatTab(
                        label = "WebP",
                        sub = "Lossless",
                        selected = selectedFormat == ExportFormat.WEBP_LOSSLESS,
                        onClick = {
                            selectedFormat = ExportFormat.WEBP_LOSSLESS
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ExportFormatTab(
                        label = "WebP",
                        sub = "Lossy",
                        selected = selectedFormat == ExportFormat.WEBP_LOSSY,
                        onClick = {
                            selectedFormat = ExportFormat.WEBP_LOSSY
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    ExportFormatTab(
                        label = "PSD",
                        sub = "Photoshop",
                        selected = selectedFormat == ExportFormat.PSD,
                        onClick = {
                            selectedFormat = ExportFormat.PSD
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ExportFormatTab(
                        label = "ORA",
                        sub = "OpenRaster",
                        selected = selectedFormat == ExportFormat.ORA,
                        onClick = {
                            selectedFormat = ExportFormat.ORA
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ExportFormatTab(
                        label = "SVG",
                        sub = "Vector",
                        selected = selectedFormat == ExportFormat.SVG,
                        onClick = {
                            selectedFormat = ExportFormat.SVG
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    ExportFormatTab(
                        label = "GIF",
                        sub = "Animated",
                        selected = selectedFormat == ExportFormat.GIF,
                        onClick = {
                            selectedFormat = ExportFormat.GIF
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ExportFormatTab(
                        label = "MP4",
                        sub = "Video",
                        selected = selectedFormat == ExportFormat.MP4,
                        enabled = AnimatedExportCapabilities.isMp4Supported,
                        onClick = {
                            selectedFormat = ExportFormat.MP4
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ExportFormatTab(
                        label = "ZIP",
                        sub = "Sequence",
                        selected = selectedFormat == ExportFormat.ZIP_SEQUENCE,
                        onClick = {
                            selectedFormat = ExportFormat.ZIP_SEQUENCE
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                val disabledReason = AnimatedExportCapabilities.getDisabledReason(selectedFormat)
                if (disabledReason != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cornerRadius.m))
                            .background(colors.warning.copy(alpha = 0.15f))
                            .padding(spacing.m)
                    ) {
                        Text(
                            text = disabledReason,
                            style = typography.bodySmall,
                            color = colors.warning
                        )
                    }
                }
            }

            // Quality Slider (Only for lossy formats: JPEG & WEBP_LOSSY)
            if (selectedFormat == ExportFormat.JPEG || selectedFormat == ExportFormat.WEBP_LOSSY) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Quality",
                            style = typography.labelMedium,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "${qualityValue.toInt()}%",
                            style = typography.labelMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = qualityValue,
                        onValueChange = { qualityValue = it },
                        valueRange = 10f..100f,
                        steps = 17
                    )
                }
            }

            // Export Scope Selector
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "Export Scope",
                    style = typography.labelMedium,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    ScopeButton(
                        label = "Full Canvas",
                        selected = selectedScope == ExportScope.FULL_CANVAS,
                        onClick = { selectedScope = ExportScope.FULL_CANVAS },
                        modifier = Modifier.weight(1f)
                    )
                    ScopeButton(
                        label = "Selected Layer",
                        selected = selectedScope == ExportScope.SELECTED_LAYER,
                        onClick = { selectedScope = ExportScope.SELECTED_LAYER },
                        modifier = Modifier.weight(1f)
                    )
                    ScopeButton(
                        label = "Flattened",
                        selected = selectedScope == ExportScope.EXPLICIT_FLATTENED,
                        onClick = { selectedScope = ExportScope.EXPLICIT_FLATTENED },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (selectedScope == ExportScope.SELECTED_LAYER && selectedLayerName != null) {
                    Text(
                        text = "Active layer: $selectedLayerName",
                        style = typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
            }

            // Transparent Background Toggle
            if (selectedFormat.supportsAlpha) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Transparent Background",
                            style = typography.labelMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Preserves alpha transparency in blank canvas regions",
                            style = typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                    Switch(
                        checked = transparentBg,
                        onCheckedChange = { transparentBg = it }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Alpha not supported by JPEG (composites over opaque background)",
                        style = typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
            }

            // Output Scaling Presets
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "Resolution Scale",
                    style = typography.labelMedium,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    ScaleButton(
                        label = "100% (1x)",
                        selected = scalePreset == 1.0f,
                        onClick = { scalePreset = 1.0f },
                        modifier = Modifier.weight(1f)
                    )
                    ScaleButton(
                        label = "50% (0.5x)",
                        selected = scalePreset == 0.5f,
                        onClick = { scalePreset = 0.5f },
                        modifier = Modifier.weight(1f)
                    )
                    ScaleButton(
                        label = "25% (0.25x)",
                        selected = scalePreset == 0.25f,
                        onClick = { scalePreset = 0.25f },
                        modifier = Modifier.weight(1f)
                    )
                    ScaleButton(
                        label = "200% (2x)",
                        selected = scalePreset == 2.0f,
                        onClick = { scalePreset = 2.0f },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Progress or Error status display
            if (isExporting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerRadius.m))
                        .background(colors.accent.container.copy(alpha = 0.3f))
                        .padding(spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = colors.accent.primary
                    )
                    Text(
                        text = "Encoding and verifying ${selectedFormat.name} export...",
                        style = typography.bodyMedium,
                        color = colors.textPrimary
                    )
                }
            } else if (exportResult is ExportResult.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerRadius.m))
                        .background(colors.error.copy(alpha = 0.15f))
                        .padding(spacing.m)
                ) {
                    Text(
                        text = "Export Failed: ${exportResult.reason}",
                        style = typography.bodyMedium,
                        color = colors.error
                    )
                }
            } else if (exportResult is ExportResult.Success) {
                val context = androidx.compose.ui.platform.LocalContext.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerRadius.m))
                        .background(colors.success.copy(alpha = 0.15f))
                        .padding(spacing.m),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Text(
                        text = "Export Succeeded: ${exportResult.outputFile.name} (${exportResult.fileSize / 1024} KB in ${exportResult.durationMs}ms)",
                        style = typography.bodyMedium,
                        color = colors.success,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Saved to: ${exportResult.displayLocation ?: exportResult.outputFile.absolutePath}",
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(spacing.xs))
                    StudioButton(
                        text = "Share Artwork",
                        onClick = {
                            try {
                                val shareIntent = PlatformExportIntegration.createShareIntent(
                                    context = context,
                                    exportedFile = exportResult.outputFile,
                                    mimeType = exportResult.format.mimeType
                                )
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                android.util.Log.e("StudioExportDialog", "Share failed: ${e.message}", e)
                            }
                        },
                        variant = StudioButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.m)
            ) {
                if (isExporting) {
                    StudioButton(
                        text = "Cancel Export",
                        onClick = onCancelExport,
                        variant = StudioButtonVariant.Destructive,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    StudioButton(
                        text = "Cancel",
                        onClick = onDismissRequest,
                        variant = StudioButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    val isFormatSupported = AnimatedExportCapabilities.isFormatSupported(selectedFormat)
                    val disabledReason = AnimatedExportCapabilities.getDisabledReason(selectedFormat)

                    StudioButton(
                        text = "Export ${selectedFormat.name}",
                        enabled = isFormatSupported,
                        disabledExplanation = disabledReason,
                        onClick = {
                            val config = ExportConfig(
                                format = selectedFormat,
                                quality = qualityValue.toInt(),
                                scope = selectedScope,
                                transparentBackground = transparentBg,
                                scaleFactor = scalePreset,
                                conflictStrategy = conflictStrategy
                            )
                            val finalFilename = if (filename.endsWith(".${selectedFormat.extension}", ignoreCase = true)) {
                                filename
                            } else {
                                "$filename.${selectedFormat.extension}"
                            }
                            onConfirmExport(config, finalFilename)
                        },
                        variant = StudioButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportFormatTab(
    label: String,
    sub: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val typography = StudioTheme.typography
    val spacing = StudioTheme.spacing
    val cornerRadius = StudioTheme.cornerRadius
    val alpha = if (enabled) 1.0f else 0.4f

    val tabModifier = if (enabled) modifier.clickableNoRipple(onClick) else modifier

    Column(
        modifier = tabModifier
            .clip(RoundedCornerShape(cornerRadius.s))
            .background(if (selected) colors.accent.container else colors.surfaceBase)
            .border(
                1.dp,
                if (selected) colors.accent.primary else colors.outlineSubtle,
                RoundedCornerShape(cornerRadius.s)
            )
            .padding(vertical = spacing.s, horizontal = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = typography.labelMedium,
            color = (if (selected) colors.accent.primary else colors.textPrimary).copy(alpha = alpha),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = sub,
            style = typography.labelSmall,
            color = (if (selected) colors.accent.primary else colors.textTertiary).copy(alpha = alpha)
        )
    }
}

@Composable
private fun ScopeButton(
    label: String,
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
            .background(if (selected) colors.accent.container else colors.surfaceBase)
            .border(
                1.dp,
                if (selected) colors.accent.primary else colors.outlineSubtle,
                RoundedCornerShape(cornerRadius.s)
            )
            .clickableNoRipple(onClick)
            .padding(vertical = spacing.s, horizontal = spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.labelSmall,
            color = if (selected) colors.accent.primary else colors.textPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ScaleButton(
    label: String,
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
            .background(if (selected) colors.accent.container else colors.surfaceBase)
            .border(
                1.dp,
                if (selected) colors.accent.primary else colors.outlineSubtle,
                RoundedCornerShape(cornerRadius.s)
            )
            .clickableNoRipple(onClick)
            .padding(vertical = spacing.s, horizontal = spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.labelSmall,
            color = if (selected) colors.accent.primary else colors.textPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick
    )
)
