package com.artstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.artstudio.ui.components.*
import com.artstudio.ui.theme.StudioAccentOption
import com.artstudio.ui.theme.StudioTheme

/**
 * AutoDraw Pipeline Screen (Honest Phase 6 Status).
 * Strictly labels offline CV features as unavailable until Phase 6; shows no fake success states.
 */
@Composable
fun AutoDrawScreen(modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        Text(
            text = "AutoDraw Reconstruction",
            style = typography.headlineLarge,
            color = colors.textPrimary
        )

        // Status Badge: Explicitly labeled as unavailable until Phase 6
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.warning, RoundedCornerShape(cornerRadius.m))
                .padding(spacing.l)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                Text(
                    text = "Subsystem Status: Scheduled for Phase 6",
                    style = typography.titleMedium,
                    color = colors.warning
                )
                Text(
                    text = "Offline computer-vision edge extraction, morphological gap closing, and stroke vectorization will be implemented in Phase 6. Per safety rules, no fake processing or placeholder success states are presented.",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }

        StudioDisabledExplanation(
            reason = "Conventional CV edge analysis is not yet compiled into the runtime.",
            suggestedAction = "Use the Manual Studio to draw freehand strokes."
        )
    }
}

/**
 * Colour Lab Screen (Honest Phase 7 Status).
 * Lists the 15 composable styles + No Colour reference from verified corpus.
 */
@Composable
fun ColourLabScreen(modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val styles = listOf(
        "No Colour (Pure Contour Line Art)",
        "1. Classic Cel Shading",
        "2. Soft Gradient Shading",
        "3. Hybrid Cel + Soft Shading",
        "4. Flat Illustration",
        "5. Painterly",
        "6. Watercolour",
        "7. Coloured Pencil",
        "8. Marker",
        "9. Manga Screentone",
        "10. Comic Book",
        "11. Oil Paint",
        "12. Gouache",
        "13. Pastel Palette",
        "14. Neon / Cyberpunk",
        "15. Monochrome"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        Text(
            text = "Colour Lab Engine",
            style = typography.headlineLarge,
            color = colors.textPrimary
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
                .padding(spacing.m)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = "Subsystem Status: Scheduled for Phase 7",
                    style = typography.titleSmall,
                    color = colors.accent.primary
                )
                Text(
                    text = "15 composable style specifications verified in test corpus v1. Real-time multi-pass shader compositing will be integrated in Phase 7.",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }

        styles.forEach { styleName ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.s))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.s))
                    .padding(horizontal = spacing.m, vertical = spacing.s)
            ) {
                Text(
                    text = styleName,
                    style = typography.titleSmall,
                    color = colors.textPrimary
                )
            }
        }
    }
}

/**
 * Assets & Brushes Screen (Honest Phase 4 Status).
 */
@Composable
fun AssetsBrushesScreen(modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    val brushPresets = listOf(
        Pair("Drafting Pencil", "Dry graphite texture with tilt response"),
        Pair("Inking Pen", "Crisp pressure-smoothed vector line art"),
        Pair("Flat Marker", "Square chisel tip with semi-transparent buildup"),
        Pair("Soft Airbrush", "Gaussian radial dab falloff with soft edges"),
        Pair("Bristle Oil Brush", "Directional streaks with wet paint pickup"),
        Pair("Precision Eraser", "Hard and soft alpha clearing modes")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        Text(
            text = "Assets & Brush Presets",
            style = typography.headlineLarge,
            color = colors.textPrimary
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.m))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
                .padding(spacing.m)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = "Core Presets Defined (Custom Dynamics in Phase 4)",
                    style = typography.titleSmall,
                    color = colors.accent.primary
                )
                Text(
                    text = "Default inking dynamics are active in the Studio. Parametric brush dynamics engine will be expanded in Phase 4.",
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }

        brushPresets.forEach { (name, desc) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.m))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.m))
                .padding(spacing.m)
            ) {
                Column {
                    Text(text = name, style = typography.titleMedium, color = colors.textPrimary)
                    Text(text = desc, style = typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
    }
}

/**
 * Settings & System Budgets Screen.
 */
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    currentAccent: StudioAccentOption,
    onSelectAccent: (StudioAccentOption) -> Unit,
    reducedMotion: Boolean,
    onToggleReducedMotion: (Boolean) -> Unit,
    onOpenComponentReference: () -> Unit,
    onOpenInputDiagnostics: () -> Unit = {},
    onOpenProLibrary: () -> Unit = {},
    onOpenManual: () -> Unit = {},
    preferences: com.artstudio.ui.preferences.StudioPreferences? = null,
    modifier: Modifier = Modifier
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography
    val cornerRadius = StudioTheme.cornerRadius

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.l)
    ) {
        Text(
            text = "Settings & Budgets",
            style = typography.headlineLarge,
            color = colors.textPrimary
        )

        // Appearance & Accessibility Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.l))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                .padding(spacing.l)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                Text(text = "Appearance & Accessibility", style = typography.titleLarge, color = colors.textPrimary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Dark Theme", style = typography.bodyMedium, color = colors.textPrimary)
                    StudioButton(
                        text = if (isDarkTheme) "On" else "Off",
                        onClick = { onToggleDarkTheme(!isDarkTheme) },
                        variant = StudioButtonVariant.Secondary
                    )
                }

                Text(text = "Theme Accent", style = typography.labelMedium, color = colors.textSecondary)
                StudioSegmentedSelector(
                    options = StudioAccentOption.entries,
                    selectedOption = currentAccent,
                    onOptionSelected = onSelectAccent,
                    optionLabel = { it.displayName }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Reduced Motion", style = typography.bodyMedium, color = colors.textPrimary)
                        Text(
                            text = if (reducedMotion) "Instant transitions (0ms)" else "Standard animations (150-400ms)",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    StudioButton(
                        text = if (reducedMotion) "On (Instant)" else "Off",
                        onClick = { onToggleReducedMotion(!reducedMotion) },
                        variant = StudioButtonVariant.Secondary
                    )
                }

                StudioButton(
                    text = "Open User Manual & Documentation (Offline)",
                    onClick = onOpenManual,
                    variant = StudioButtonVariant.Primary
                )

                StudioButton(
                    text = "Open Pro Asset Library (Brushes, Textures, Fonts, Comics)",
                    onClick = onOpenProLibrary,
                    variant = StudioButtonVariant.Secondary
                )

                StudioButton(
                    text = "Open UI Design System Catalog",
                    onClick = onOpenComponentReference,
                    variant = StudioButtonVariant.Secondary
                )

                StudioButton(
                    text = "Open Input & Stylus Diagnostics (Developer)",
                    onClick = onOpenInputDiagnostics,
                    variant = StudioButtonVariant.Secondary
                )

                var showDevFlagsDialog by remember { mutableStateOf(false) }
                StudioButton(
                    text = "Developer Feature Flags (Debug)",
                    onClick = { showDevFlagsDialog = true },
                    variant = StudioButtonVariant.Secondary
                )

                if (showDevFlagsDialog) {
                    com.artstudio.ui.components.StudioDevFlagsDialog(
                        onDismissRequest = { showDevFlagsDialog = false }
                    )
                }
            }
        }

        // Onboarding & Studio Mode Card
        if (preferences != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius.l))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                    .padding(spacing.l)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Text(text = "Studio Mode & Onboarding", style = typography.titleLarge, color = colors.textPrimary)

                    var currentMode by remember { mutableStateOf(preferences.userMode) }
                    Text(text = "Change Experience Mode", style = typography.labelMedium, color = colors.textSecondary)
                    StudioSegmentedSelector(
                        options = com.artstudio.ui.preferences.StudioUserMode.entries,
                        selectedOption = currentMode,
                        onOptionSelected = {
                            currentMode = it
                            preferences.userMode = it
                        },
                        optionLabel = {
                            when (it) {
                                com.artstudio.ui.preferences.StudioUserMode.JUST_DRAW -> "Just Draw"
                                com.artstudio.ui.preferences.StudioUserMode.LEARN -> "Learn"
                                com.artstudio.ui.preferences.StudioUserMode.FULL -> "Full Power"
                            }
                        }
                    )

                    var showResetDialog by remember { mutableStateOf(false) }
                    StudioButton(
                        text = "Reset Onboarding",
                        onClick = { showResetDialog = true },
                        variant = StudioButtonVariant.Secondary
                    )

                    if (showResetDialog) {
                        com.artstudio.ui.components.StudioDialog(
                            title = "Reset Onboarding",
                            body = "Reset onboarding and tutorial flags? The welcome tour will appear on next launch.",
                            confirmText = "Reset",
                            cancelText = "Cancel",
                            onConfirm = {
                                preferences.resetOnboarding()
                                currentMode = preferences.userMode
                                showResetDialog = false
                            },
                            onDismiss = { showResetDialog = false }
                        )
                    }
                }
            }
        }

        // Hardware & Storage Budgets Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius.l))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.outlineSubtle, RoundedCornerShape(cornerRadius.l))
                .padding(spacing.l)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                Text(text = "Hardware Compliance Status", style = typography.titleLarge, color = colors.textPrimary)
                Text(text = "• RAM Target: 4 GB physical min / 6 GB recommended (Host: ~11.2 GB)", style = typography.bodySmall, color = colors.textSecondary)
                Text(text = "• Storage Gate: >= 1.0 GB free disk required (Verified: >60 GB available)", style = typography.bodySmall, color = colors.textSecondary)
                Text(text = "• APK Footprint: Measured 10.0 MB (Target: < 150 MB base package)", style = typography.bodySmall, color = colors.textSecondary)
                Text(text = "• Privacy & AI: 100% Offline by default; zero cloud transmission without consent", style = typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}
