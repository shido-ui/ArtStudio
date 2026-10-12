package com.artstudio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation levels for canvas-first layering.
 * Keeps controls separated from the canvas without excessive shadows.
 */
@Immutable
data class StudioElevation(
    val flat: Dp = 0.dp,        // In-line canvas surface
    val subtle: Dp = 1.dp,      // Card or toolbar border
    val raised: Dp = 2.dp,      // Docked palette, layer row
    val floating: Dp = 4.dp,    // Floating quick-actions, tool drawer
    val modal: Dp = 8.dp        // Alert dialogs, modal menus
)

val LocalStudioElevation = staticCompositionLocalOf { StudioElevation() }
