package com.artstudio.ui

import androidx.compose.ui.graphics.Color
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioAccentOption
import com.artstudio.ui.theme.StudioColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates WCAG 2.1 AA and AAA contrast compliance across all color tokens,
 * themes (Light and Dark), and the 4 Accent System presets.
 */
class AccessibilityContrastTest {

    @Test
    fun testDarkThemePrimaryTextContrastMeetsWcagAaa() {
        val darkColors = StudioColors.darkColors()
        val contrast = StudioAccessibility.calculateContrastRatio(
            darkColors.surfaceBase,
            darkColors.textPrimary
        )
        // WCAG AAA requires >= 7.0:1 for normal text
        println("Dark theme Surface to Primary Text contrast: ${String.format("%.2f", contrast)}:1")
        assertTrue(
            "Primary text on dark surface must meet WCAG AAA (>= 7.0:1), got $contrast",
            contrast >= 7.0
        )
    }

    @Test
    fun testDarkThemeSecondaryTextContrastMeetsWcagAa() {
        val darkColors = StudioColors.darkColors()
        val contrast = StudioAccessibility.calculateContrastRatio(
            darkColors.surfaceBase,
            darkColors.textSecondary
        )
        // WCAG AA requires >= 4.5:1 for normal text
        println("Dark theme Surface to Secondary Text contrast: ${String.format("%.2f", contrast)}:1")
        assertTrue(
            "Secondary text on dark surface must meet WCAG AA (>= 4.5:1), got $contrast",
            contrast >= 4.5
        )
    }

    @Test
    fun testLightThemePrimaryTextContrastMeetsWcagAaa() {
        val lightColors = StudioColors.lightColors()
        val contrast = StudioAccessibility.calculateContrastRatio(
            lightColors.surfaceBase,
            lightColors.textPrimary
        )
        println("Light theme Surface to Primary Text contrast: ${String.format("%.2f", contrast)}:1")
        assertTrue(
            "Primary text on light surface must meet WCAG AAA (>= 7.0:1), got $contrast",
            contrast >= 7.0
        )
    }

    @Test
    fun testLightThemeSecondaryTextContrastMeetsWcagAa() {
        val lightColors = StudioColors.lightColors()
        val contrast = StudioAccessibility.calculateContrastRatio(
            lightColors.surfaceBase,
            lightColors.textSecondary
        )
        println("Light theme Surface to Secondary Text contrast: ${String.format("%.2f", contrast)}:1")
        assertTrue(
            "Secondary text on light surface must meet WCAG AA (>= 4.5:1), got $contrast",
            contrast >= 4.5
        )
    }

    @Test
    fun testAllAccentsMeetUiContrastInDarkTheme() {
        for (accent in StudioAccentOption.entries) {
            val colors = StudioColors.darkColors(accent)
            val contrast = StudioAccessibility.calculateContrastRatio(
                colors.surfaceBase,
                colors.accent.primary
            )
            println("Dark theme ${accent.displayName} accent contrast: ${String.format("%.2f", contrast)}:1")
            assertTrue(
                "Accent ${accent.displayName} must meet WCAG UI component contrast (>= 3.0:1), got $contrast",
                contrast >= 3.0
            )
        }
    }

    @Test
    fun testAllAccentsMeetUiContrastInLightTheme() {
        for (accent in StudioAccentOption.entries) {
            val colors = StudioColors.lightColors(accent)
            val contrast = StudioAccessibility.calculateContrastRatio(
                colors.surfaceBase,
                colors.accent.primary
            )
            println("Light theme ${accent.displayName} accent contrast: ${String.format("%.2f", contrast)}:1")
            assertTrue(
                "Accent ${accent.displayName} must meet WCAG UI component contrast (>= 3.0:1), got $contrast",
                contrast >= 3.0
            )
        }
    }

    @Test
    fun testButtonOnAccentContrastMeetsWcagAa() {
        for (accent in StudioAccentOption.entries) {
            val darkColors = StudioColors.darkColors(accent)
            val darkButtonContrast = StudioAccessibility.calculateContrastRatio(
                darkColors.accent.primary,
                darkColors.accent.onPrimary
            )
            assertTrue(
                "Dark button text on accent must meet WCAG AA (>= 4.5:1) for ${accent.displayName}, got $darkButtonContrast",
                darkButtonContrast >= 4.5
            )

            val lightColors = StudioColors.lightColors(accent)
            val lightButtonContrast = StudioAccessibility.calculateContrastRatio(
                lightColors.accent.primary,
                lightColors.accent.onPrimary
            )
            assertTrue(
                "Light button text on accent must meet WCAG AA (>= 4.5:1) for ${accent.displayName}, got $lightButtonContrast",
                lightButtonContrast >= 4.5
            )
        }
    }

    @Test
    fun testErrorContrastMeetsWcagStandards() {
        val darkColors = StudioColors.darkColors()
        val darkErrorContrast = StudioAccessibility.calculateContrastRatio(
            darkColors.surfaceBase,
            darkColors.error
        )
        assertTrue(darkErrorContrast >= 3.0)

        val lightColors = StudioColors.lightColors()
        val lightErrorContrast = StudioAccessibility.calculateContrastRatio(
            lightColors.surfaceBase,
            lightColors.error
        )
        assertTrue(lightErrorContrast >= 3.0)
    }
}
