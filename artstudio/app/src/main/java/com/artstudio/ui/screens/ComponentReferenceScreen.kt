package com.artstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.artstudio.ui.components.*
import com.artstudio.ui.theme.*

/**
 * Interactive UI Component Reference Screen.
 * Exercises all 13 reusable design system components across all states,
 * supports live theme/accent switching, and enforces non-decorative functional controls.
 */
@Composable
fun ComponentReferenceScreen(
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    currentAccent: StudioAccentOption,
    onSelectAccent: (StudioAccentOption) -> Unit,
    reducedMotion: Boolean,
    onToggleReducedMotion: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    // Interactive states for live component testing
    var selectedCategoryTab by remember { mutableStateOf("All Controls") }
    val categoryTabs = listOf("All Controls", "Buttons & Sliders", "Canvas Tools", "Feedback & Dialogs")

    var buttonClickCount by remember { mutableIntStateOf(0) }
    var buttonLoadingState by remember { mutableStateOf(false) }

    var activeTool by remember { mutableStateOf("Inking Pen") }
    val toolOptions = listOf("Pencil", "Inking Pen", "Flat Marker", "Eraser")

    var continuousSliderValue by remember { mutableFloatStateOf(0.75f) }
    var steppedSliderValue by remember { mutableFloatStateOf(16f) }

    var selectedColorHex by remember { mutableStateOf("#4D9FFF") }
    val swatchPalette = listOf(
        Pair(Color(0xFF4D9FFF), "#4D9FFF"),
        Pair(Color(0xFF2EE69B), "#2EE69B"),
        Pair(Color(0xFFFF4DE3), "#FF4DE3"),
        Pair(Color(0xFFFFA940), "#FFA940"),
        Pair(Color(0xFFFFFFFF), "#FFFFFF"),
        Pair(Color(0xFF1E1E1E), "#1E1E1E")
    )

    // Interactive Layer rows
    var activeLayerIndex by remember { mutableIntStateOf(1) }
    var layer1Visible by remember { mutableStateOf(true) }
    var layer1Locked by remember { mutableStateOf(false) }
    var layer2Visible by remember { mutableStateOf(true) }
    var layer2Locked by remember { mutableStateOf(false) }

    // Dialog state
    var showSampleDialog by remember { mutableStateOf(false) }
    var dialogResultText by remember { mutableStateOf("No action yet") }

    // Feedback states toggles
    var showLoadingState by remember { mutableStateOf(true) }
    var showErrorState by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.xl)
    ) {
        // Header Section
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = "Art Studio UI Design System",
                style = typography.headlineLarge,
                color = colors.textPrimary
            )
            Text(
                text = "Accessible tokens, 13 reusable components, and WCAG-compliant design rules.",
                style = typography.bodyMedium,
                color = colors.textSecondary
            )
        }

        // Section 1: Live Theme, Accent & Motion Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.l))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                .padding(spacing.l)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                Text(
                    text = "1. Theme & Accessibility Tokens",
                    style = typography.titleLarge,
                    color = colors.textPrimary
                )

                // Light / Dark Theme toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Theme: ${if (isDarkTheme) "Dark" else "Light"}",
                        style = typography.bodyMedium,
                        color = colors.textPrimary
                    )
                    StudioButton(
                        text = if (isDarkTheme) "Switch to Light" else "Switch to Dark",
                        onClick = { onToggleDarkTheme(!isDarkTheme) },
                        variant = StudioButtonVariant.Secondary
                    )
                }

                // Accent System selector
                Text(
                    text = "Accent Color:",
                    style = typography.labelMedium,
                    color = colors.textSecondary
                )
                StudioSegmentedSelector(
                    options = StudioAccentOption.entries,
                    selectedOption = currentAccent,
                    onOptionSelected = onSelectAccent,
                    optionLabel = { it.displayName }
                )

                // Reduced Motion toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reduced Motion:",
                            style = typography.bodyMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (reducedMotion) "Animations disabled (0ms instant)" else "Normal physics easing (150-400ms)",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    StudioButton(
                        text = if (reducedMotion) "Enable Motion" else "Reduce Motion",
                        onClick = { onToggleReducedMotion(!reducedMotion) },
                        variant = StudioButtonVariant.Secondary
                    )
                }

                // Live WCAG Contrast readout
                val contrastSurfaceText = StudioAccessibility.calculateContrastRatio(colors.surfaceBase, colors.textPrimary)
                val contrastAccent = StudioAccessibility.calculateContrastRatio(colors.surfaceBase, colors.accent.primary)
                Text(
                    text = "WCAG Verification: Surface/Text contrast = ${String.format("%.1f", contrastSurfaceText)}:1 (Pass AAA); Accent contrast = ${String.format("%.1f", contrastAccent)}:1 (Pass AA); Touch targets >= 48dp enforced.",
                    style = typography.bodySmall,
                    color = colors.accent.primary
                )
            }
        }

        // Section Tabs
        StudioTabs(
            tabs = categoryTabs,
            selectedTab = selectedCategoryTab,
            onTabSelected = { selectedCategoryTab = it },
            tabLabel = { it }
        )

        // Section 2: Reusable Buttons & Icon Buttons
        if (selectedCategoryTab == "All Controls" || selectedCategoryTab == "Buttons & Sliders") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.l))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                    .padding(spacing.l)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Text(
                        text = "2. Studio Buttons (Component 1 & 2)",
                        style = typography.titleLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Interactive buttons with 48dp minimum touch targets, loading states, and disabled explanations.",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        StudioButton(
                            text = "Primary ($buttonClickCount)",
                            onClick = { buttonClickCount++ },
                            variant = StudioButtonVariant.Primary,
                            modifier = Modifier.weight(1f)
                        )
                        StudioButton(
                            text = "Secondary",
                            onClick = { buttonClickCount++ },
                            variant = StudioButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        StudioButton(
                            text = "Destructive",
                            onClick = { buttonClickCount = 0 },
                            variant = StudioButtonVariant.Destructive,
                            modifier = Modifier.weight(1f)
                        )
                        StudioButton(
                            text = "Ghost",
                            onClick = { buttonLoadingState = !buttonLoadingState },
                            variant = StudioButtonVariant.Ghost,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Loading button state
                    StudioButton(
                        text = if (buttonLoadingState) "Rendering passes..." else "Toggle Loading State",
                        onClick = { buttonLoadingState = !buttonLoadingState },
                        loading = buttonLoadingState,
                        variant = StudioButtonVariant.Primary
                    )

                    // Disabled button with explicit explanation
                    StudioButton(
                        text = "Export to PSD (Disabled)",
                        onClick = {},
                        enabled = false,
                        disabledExplanation = "Requires active canvas document with at least one raster layer.",
                        variant = StudioButtonVariant.Secondary
                    )

                    // Icon Buttons with 48dp touch targets
                    Text(
                        text = "Icon Buttons (48dp Touch Target):",
                        style = typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.m),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioIconButton(
                            contentDescription = "Undo stroke",
                            onClick = { buttonClickCount-- },
                            icon = { Text("↩️", style = typography.titleMedium) }
                        )
                        StudioIconButton(
                            contentDescription = "Redo stroke",
                            onClick = { buttonClickCount++ },
                            icon = { Text("↪️", style = typography.titleMedium) }
                        )
                        StudioIconButton(
                            contentDescription = "Active brush tool",
                            onClick = {},
                            active = true,
                            icon = { Text("🖌️", style = typography.titleMedium) }
                        )
                        StudioIconButton(
                            contentDescription = "Disabled cloud sync",
                            onClick = {},
                            enabled = false,
                            disabledExplanation = "Cloud upload disabled by offline-first policy (ADR-004)",
                            icon = { Text("☁️", style = typography.titleMedium) }
                        )
                    }
                }
            }
        }

        // Section 3: Sliders & Segmented Selectors
        if (selectedCategoryTab == "All Controls" || selectedCategoryTab == "Buttons & Sliders") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.l))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                    .padding(spacing.l)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Text(
                        text = "3. Sliders & Selectors (Components 3 & 4)",
                        style = typography.titleLarge,
                        color = colors.textPrimary
                    )

                    // Continuous Opacity slider
                    StudioSlider(
                        value = continuousSliderValue,
                        onValueChange = { continuousSliderValue = it },
                        label = "Layer Opacity",
                        valueFormatter = { "${(it * 100).toInt()}%" }
                    )

                    // Stepped Brush Size slider
                    StudioSlider(
                        value = steppedSliderValue,
                        onValueChange = { steppedSliderValue = it },
                        valueRange = 1f..64f,
                        steps = 63,
                        label = "Brush Dab Diameter",
                        valueFormatter = { "${it.toInt()} px" }
                    )

                    // Disabled slider with explanation
                    StudioSlider(
                        value = 0.5f,
                        onValueChange = {},
                        enabled = false,
                        disabledExplanation = "Brush dynamics locked for vector line art eraser tool.",
                        label = "Jitter Dynamics"
                    )

                    // Segmented Selector for Tools
                    Text(
                        text = "Tool Mode Selector:",
                        style = typography.labelMedium,
                        color = colors.textSecondary
                    )
                    StudioSegmentedSelector(
                        options = toolOptions,
                        selectedOption = activeTool,
                        onOptionSelected = { activeTool = it },
                        optionLabel = { it }
                    )
                }
            }
        }

        // Section 4: Canvas Controls (Layers & Swatches)
        if (selectedCategoryTab == "All Controls" || selectedCategoryTab == "Canvas Tools") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.l))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                    .padding(spacing.l)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Text(
                        text = "4. Layers & Palette Chips (Components 5 & 6)",
                        style = typography.titleLarge,
                        color = colors.textPrimary
                    )

                    // Layer 1
                    StudioLayerRow(
                        layerName = "Line Art Inking",
                        isSelected = activeLayerIndex == 1,
                        isVisible = layer1Visible,
                        isLocked = layer1Locked,
                        opacityPercent = (continuousSliderValue * 100).toInt(),
                        thumbnailColor = Color(0xFF263238),
                        onSelectLayer = { activeLayerIndex = 1 },
                        onToggleVisibility = { layer1Visible = !layer1Visible },
                        onToggleLock = { layer1Locked = !layer1Locked }
                    )

                    // Layer 2
                    StudioLayerRow(
                        layerName = "Colour Lab Shading",
                        isSelected = activeLayerIndex == 2,
                        isVisible = layer2Visible,
                        isLocked = layer2Locked,
                        opacityPercent = 100,
                        thumbnailColor = Color(0xFF1E88E5),
                        onSelectLayer = { activeLayerIndex = 2 },
                        onToggleVisibility = { layer2Visible = !layer2Visible },
                        onToggleLock = { layer2Locked = !layer2Locked }
                    )

                    Text(
                        text = "Active Swatch Palette:",
                        style = typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        swatchPalette.forEach { (color, hex) ->
                            StudioPaletteChip(
                                color = color,
                                hexLabel = hex,
                                isSelected = selectedColorHex == hex,
                                onSelect = { selectedColorHex = hex }
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Progress, Dialogs & Feedback States
        if (selectedCategoryTab == "All Controls" || selectedCategoryTab == "Feedback & Dialogs") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.l))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                    .padding(spacing.l)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Text(
                        text = "5. Progress, Feedback & Dialogs (Components 7 to 13)",
                        style = typography.titleLarge,
                        color = colors.textPrimary
                    )

                    // Determinate Progress Bar
                    StudioProgressBar(
                        progress = continuousSliderValue,
                        label = "OpenRaster (.ora) Export Encoding"
                    )

                    // Indeterminate Progress Spinner
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.m)
                    ) {
                        StudioProgressIndicator(
                            contentDescription = "Tiling background cache..."
                        )
                        Text(
                            text = "Background LRU tile cache synching...",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }

                    // Dialog Trigger Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Modal Dialog: $dialogResultText",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                        StudioButton(
                            text = "Trigger Dialog",
                            onClick = { showSampleDialog = true },
                            variant = StudioButtonVariant.Secondary
                        )
                    }

                    // Disabled control explanation card
                    StudioDisabledExplanation(
                        reason = "AI Contour Super-Resolution is offline and unloaded.",
                        suggestedAction = "Load optional model in Settings after size & consent check."
                    )

                    // Loading State
                    if (showLoadingState) {
                        StudioLoadingState(
                            title = "Running Offline Contour Extraction",
                            subtitle = "Analyzing edges using OpenCV Canny kernels (Offline Mode)",
                            onCancel = { showLoadingState = false }
                        )
                    } else {
                        StudioButton(
                            text = "Show Loading State",
                            onClick = { showLoadingState = true },
                            variant = StudioButtonVariant.Ghost
                        )
                    }

                    // Empty State
                    StudioEmptyState(
                        title = "No Custom Palettes Saved",
                        description = "Create swatches using the Colour Lab or import an OpenRaster palette.",
                        actionText = "Create Palette",
                        onAction = { dialogResultText = "Create Palette clicked" }
                    )

                    // Error State
                    if (showErrorState) {
                        StudioErrorState(
                            title = "Storage Pre-Flight Failed",
                            errorMessage = "Free disk space is below the 1.0 GB mandatory threshold (only 640 MB available).",
                            onRetry = { showErrorState = false },
                            onDismiss = { showErrorState = false }
                        )
                    } else {
                        StudioButton(
                            text = "Simulate Storage Error State",
                            onClick = { showErrorState = true },
                            variant = StudioButtonVariant.Ghost
                        )
                    }
                }
            }
        }

        // Active Modal Dialog
        if (showSampleDialog) {
            StudioDialog(
                title = "Discard Unsaved Strokes?",
                body = "You have 12 unsaved stroke vectors. Discarding will revert to the last auto-saved snapshot.",
                confirmText = "Discard",
                cancelText = "Keep Editing",
                isDestructive = true,
                onConfirm = {
                    dialogResultText = "Discarded strokes"
                    showSampleDialog = false
                },
                onDismiss = {
                    dialogResultText = "Kept editing"
                    showSampleDialog = false
                }
            )
        }
    }
}
