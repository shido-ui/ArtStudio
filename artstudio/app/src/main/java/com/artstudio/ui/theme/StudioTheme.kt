package com.artstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Global accessor for Art Studio Design System tokens.
 */
object StudioTheme {
    val colors: StudioColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioColors.current

    val typography: StudioTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioTypography.current

    val spacing: StudioSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioSpacing.current

    val cornerRadius: StudioCornerRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioCornerRadius.current

    val elevation: StudioElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioElevation.current

    val motion: StudioMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioMotion.current
}

/**
 * Root theme composable wrapping Art Studio applications.
 */
@Composable
fun ArtStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentOption: StudioAccentOption = StudioAccentOption.StudioBlue,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val studioColors = if (darkTheme) {
        StudioColors.darkColors(accentOption)
    } else {
        StudioColors.lightColors(accentOption)
    }

    val studioMotion = if (reducedMotion) StudioMotion.Reduced else StudioMotion.Default
    val studioTypography = StudioTypography()
    val studioSpacing = StudioSpacing()
    val studioCornerRadius = StudioCornerRadius()
    val studioElevation = StudioElevation()

    // Bridge to MaterialTheme for compatible baseline components
    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = studioColors.accent.primary,
            onPrimary = studioColors.accent.onPrimary,
            primaryContainer = studioColors.accent.container,
            onPrimaryContainer = studioColors.accent.onContainer,
            surface = studioColors.surfaceBase,
            onSurface = studioColors.textPrimary,
            surfaceVariant = studioColors.surfaceElevated,
            onSurfaceVariant = studioColors.textSecondary,
            background = studioColors.canvasBackground,
            onBackground = studioColors.textPrimary,
            error = studioColors.error,
            onError = studioColors.onError
        )
    } else {
        lightColorScheme(
            primary = studioColors.accent.primary,
            onPrimary = studioColors.accent.onPrimary,
            primaryContainer = studioColors.accent.container,
            onPrimaryContainer = studioColors.accent.onContainer,
            surface = studioColors.surfaceBase,
            onSurface = studioColors.textPrimary,
            surfaceVariant = studioColors.surfaceElevated,
            onSurfaceVariant = studioColors.textSecondary,
            background = studioColors.canvasBackground,
            onBackground = studioColors.textPrimary,
            error = studioColors.error,
            onError = studioColors.onError
        )
    }

    CompositionLocalProvider(
        LocalStudioColors provides studioColors,
        LocalStudioTypography provides studioTypography,
        LocalStudioSpacing provides studioSpacing,
        LocalStudioCornerRadius provides studioCornerRadius,
        LocalStudioElevation provides studioElevation,
        LocalStudioMotion provides studioMotion
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
