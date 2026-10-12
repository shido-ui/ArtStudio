package com.artstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artstudio.color.ColorData
import com.artstudio.color.PaletteExtractor
import com.artstudio.color.PaletteRepository
import com.artstudio.document.model.ColorSwatchData
import com.artstudio.document.model.PaletteDefinition
import com.artstudio.ui.theme.StudioTheme

/**
 * Palette management panel supporting user palettes, swatch grid,
 * swatch addition/removal, palette extraction from canvas, and import/export.
 */
@Composable
fun StudioPalettePanel(
    paletteRepository: PaletteRepository,
    currentColor: ColorData,
    onColorSelected: (ColorData) -> Unit,
    onExtractFromLayer: (() -> PaletteDefinition)? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    var allPalettes by remember { mutableStateOf(paletteRepository.getAllPalettes()) }
    var selectedPaletteId by remember { mutableStateOf(allPalettes.firstOrNull()?.id ?: "") }
    var showExtractSuccessDialog by remember { mutableStateOf(false) }

    val activePalette = allPalettes.find { it.id == selectedPaletteId } ?: allPalettes.firstOrNull()

    fun refreshPalettes() {
        allPalettes = paletteRepository.getAllPalettes()
    }

    Column(
        modifier = modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .clip(RoundedCornerShape(cornerRadius.l))
            .background(colors.surfaceElevated.copy(alpha = 0.98f))
            .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
            .padding(spacing.m)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎨 Studio Palettes",
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            StudioIconButton(
                contentDescription = "Close palette panel",
                onClick = onDismissRequest,
                icon = { Text("✕", style = typography.titleSmall) }
            )
        }

        // Palette Selector Carousel / List
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = "Select Palette",
                style = typography.labelSmall,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                allPalettes.forEach { p ->
                    val isSelected = p.id == selectedPaletteId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(cornerRadius.s))
                            .background(if (isSelected) colors.accent.primary.copy(alpha = 0.15f) else colors.surfaceBase)
                            .border(
                                1.dp,
                                if (isSelected) colors.accent.primary else colors.outlineSubtle,
                                RoundedCornerShape(cornerRadius.s)
                            )
                            .clickable { selectedPaletteId = p.id }
                            .padding(horizontal = spacing.s, vertical = spacing.xs)
                    ) {
                        Text(
                            text = p.name,
                            style = typography.labelSmall,
                            color = if (isSelected) colors.accent.primary else colors.textPrimary
                        )
                    }
                }
            }
        }

        // Active Palette Swatch Grid
        if (activePalette != null) {
            Text(
                text = "${activePalette.name} (${activePalette.swatches.size} swatches)",
                style = typography.labelMedium,
                color = colors.textSecondary
            )

            // Swatch Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 160.dp)
                    .clip(RoundedCornerShape(cornerRadius.m))
                    .background(colors.surfaceBase)
                    .padding(spacing.s)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    items(
                        count = activePalette.swatches.size,
                        key = { index -> "${activePalette.id}_$index" }
                    ) { index ->
                        val swatch = activePalette.swatches[index]
                        val swatchColor = try {
                            ColorData.fromHex(swatch.hex)
                        } catch (_: Exception) {
                            ColorData.BLACK
                        }
                        val isSelected = swatchColor.deltaRgb(currentColor) < 0.01f

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(swatchColor.argbInt))
                                .border(
                                    if (isSelected) 2.5.dp else 1.dp,
                                    if (isSelected) colors.accent.primary else colors.outlineSubtle,
                                    CircleShape
                                )
                                .clickable(role = Role.RadioButton) {
                                    onColorSelected(swatchColor)
                                }
                                .semantics {
                                    this.role = Role.RadioButton
                                    this.contentDescription = "Swatch ${swatch.name ?: swatch.hex}"
                                }
                        )
                    }
                }
            }
        }

        // Actions: Add Current Swatch, Extract Palette, New Palette
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            // Add current color to palette
            if (activePalette != null && !activePalette.id.startsWith("palette_")) {
                StudioButton(
                    text = "➕ Add Current Color to Palette",
                    onClick = {
                        val newSwatch = ColorSwatchData(currentColor.hexRgb, "Custom Swatch")
                        paletteRepository.addSwatchToPalette(activePalette.id, newSwatch)
                        refreshPalettes()
                    },
                    variant = StudioButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Extract from active layer
            if (onExtractFromLayer != null) {
                StudioButton(
                    text = "🔍 Extract Palette from Layer",
                    onClick = {
                        val extracted = onExtractFromLayer()
                        paletteRepository.addPalette(extracted)
                        refreshPalettes()
                        selectedPaletteId = extracted.id
                        showExtractSuccessDialog = true
                    },
                    variant = StudioButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Create new empty palette
            StudioButton(
                text = "✨ Create New Palette",
                onClick = {
                    val created = paletteRepository.createPalette("My Palette ${allPalettes.size + 1}")
                    refreshPalettes()
                    selectedPaletteId = created.id
                },
                variant = StudioButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showExtractSuccessDialog) {
            StudioDialog(
                title = "Palette Extracted",
                body = "Successfully extracted representative color palette from active layer using Median-Cut quantization.",
                confirmText = "OK",
                onConfirm = { showExtractSuccessDialog = false },
                onDismiss = { showExtractSuccessDialog = false }
            )
        }
    }
}
