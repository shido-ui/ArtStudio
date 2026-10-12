package com.artstudio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 4 Art Studio Accent System presets.
 */
enum class StudioAccentOption(val displayName: String) {
    StudioBlue("Studio Blue"),
    EmeraldArt("Emerald Art"),
    CyberpunkNeon("Cyberpunk Neon"),
    ClassicAmber("Classic Amber")
}

@Immutable
data class StudioAccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color
)

@Immutable
data class StudioColors(
    val isDark: Boolean,
    val canvasBackground: Color,
    val surfaceBase: Color,
    val surfaceElevated: Color,
    val surfaceOverlay: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val outlineSubtle: Color,
    val outlineStrong: Color,
    val accent: StudioAccentPalette,
    val error: Color,
    val onError: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color
) {
    companion object {
        fun darkColors(accentOption: StudioAccentOption = StudioAccentOption.StudioBlue): StudioColors {
            val accentPalette = when (accentOption) {
                StudioAccentOption.StudioBlue -> StudioAccentPalette(
                    primary = Color(0xFF4D9FFF),
                    onPrimary = Color(0xFF002B66),
                    container = Color(0xFF003B82),
                    onContainer = Color(0xFFD6E4FF)
                )
                StudioAccentOption.EmeraldArt -> StudioAccentPalette(
                    primary = Color(0xFF2EE69B),
                    onPrimary = Color(0xFF003D28),
                    container = Color(0xFF00573B),
                    onContainer = Color(0xFFB8F9D7)
                )
                StudioAccentOption.CyberpunkNeon -> StudioAccentPalette(
                    primary = Color(0xFFFF4DE3),
                    onPrimary = Color(0xFF4D0037),
                    container = Color(0xFF660049),
                    onContainer = Color(0xFFFFD6F6)
                )
                StudioAccentOption.ClassicAmber -> StudioAccentPalette(
                    primary = Color(0xFFFFA940),
                    onPrimary = Color(0xFF4D2000),
                    container = Color(0xFF6E3200),
                    onContainer = Color(0xFFFFECCF)
                )
            }

            return StudioColors(
                isDark = true,
                canvasBackground = Color(0xFF1E1E1E),
                surfaceBase = Color(0xFF121212),
                surfaceElevated = Color(0xFF1F1F1F),
                surfaceOverlay = Color(0xFF2A2A2A),
                textPrimary = Color(0xFFF5F5F5),
                textSecondary = Color(0xFFB0B0B0),
                textTertiary = Color(0xFF808080),
                outlineSubtle = Color(0xFF333333),
                outlineStrong = Color(0xFF555555),
                accent = accentPalette,
                error = Color(0xFFFF5252),
                onError = Color(0xFFFFFFFF),
                success = Color(0xFF4CAF50),
                onSuccess = Color(0xFFFFFFFF),
                warning = Color(0xFFFFB300),
                onWarning = Color(0xFF000000)
            )
        }

        fun lightColors(accentOption: StudioAccentOption = StudioAccentOption.StudioBlue): StudioColors {
            val accentPalette = when (accentOption) {
                StudioAccentOption.StudioBlue -> StudioAccentPalette(
                    primary = Color(0xFF0062CC),
                    onPrimary = Color(0xFFFFFFFF),
                    container = Color(0xFFDCE8FC),
                    onContainer = Color(0xFF001D47)
                )
                StudioAccentOption.EmeraldArt -> StudioAccentPalette(
                    primary = Color(0xFF00805A),
                    onPrimary = Color(0xFFFFFFFF),
                    container = Color(0xFFDCF7EC),
                    onContainer = Color(0xFF00291D)
                )
                StudioAccentOption.CyberpunkNeon -> StudioAccentPalette(
                    primary = Color(0xFFA6007A),
                    onPrimary = Color(0xFFFFFFFF),
                    container = Color(0xFFFDE2F6),
                    onContainer = Color(0xFF3B002B)
                )
                StudioAccentOption.ClassicAmber -> StudioAccentPalette(
                    primary = Color(0xFFB35500),
                    onPrimary = Color(0xFFFFFFFF),
                    container = Color(0xFFFFE8D1),
                    onContainer = Color(0xFF3D1A00)
                )
            }

            return StudioColors(
                isDark = false,
                canvasBackground = Color(0xFFE5E5E5),
                surfaceBase = Color(0xFFFAFAFA),
                surfaceElevated = Color(0xFFFFFFFF),
                surfaceOverlay = Color(0xFFF0F0F0),
                textPrimary = Color(0xFF1A1A1A),
                textSecondary = Color(0xFF555555),
                textTertiary = Color(0xFF757575),
                outlineSubtle = Color(0xFFE0E0E0),
                outlineStrong = Color(0xFFBDBDBD),
                accent = accentPalette,
                error = Color(0xFFD32F2F),
                onError = Color(0xFFFFFFFF),
                success = Color(0xFF2E7D32),
                onSuccess = Color(0xFFFFFFFF),
                warning = Color(0xFFEF6C00),
                onWarning = Color(0xFFFFFFFF)
            )
        }
    }
}

val LocalStudioColors = staticCompositionLocalOf { StudioColors.darkColors() }
