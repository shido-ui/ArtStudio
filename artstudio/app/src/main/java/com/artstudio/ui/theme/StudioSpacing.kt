package com.artstudio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Consistent spatial scale and corner radius tokens for Art Studio.
 * Based on an 8-point geometric grid with 2dp and 4dp half-step micro-increments.
 */
@Immutable
data class StudioSpacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp
)

@Immutable
data class StudioCornerRadius(
    val none: Dp = 0.dp,
    val xs: Dp = 2.dp,
    val s: Dp = 4.dp,
    val m: Dp = 8.dp,
    val l: Dp = 12.dp,
    val xl: Dp = 16.dp,
    val pill: Dp = 999.dp
)

val LocalStudioSpacing = staticCompositionLocalOf { StudioSpacing() }
val LocalStudioCornerRadius = staticCompositionLocalOf { StudioCornerRadius() }
