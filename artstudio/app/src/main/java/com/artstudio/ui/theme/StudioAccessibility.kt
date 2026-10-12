package com.artstudio.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Accessibility standards and verification helpers for Art Studio.
 * Enforces Android/WCAG 2.1 touch target minimums and contrast ratios.
 */
object StudioAccessibility {
    /**
     * Minimum interactive touch target dimension (48dp x 48dp).
     * All clickable controls MUST occupy at least this dimension in touch space.
     */
    val MinTouchTarget: Dp = 48.dp

    /**
     * Standard icon visual sizes (decoupled from the 48dp touch target).
     */
    val IconSmall: Dp = 16.dp
    val IconMedium: Dp = 24.dp
    val IconLarge: Dp = 32.dp
    val IconHero: Dp = 48.dp

    /**
     * Calculates WCAG relative luminance for a given sRGB color.
     */
    fun calculateLuminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).pow(2.4)
            }
        }

        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Calculates the WCAG contrast ratio between two colors (ranging from 1.0 to 21.0).
     */
    fun calculateContrastRatio(color1: Color, color2: Color): Double {
        val lum1 = calculateLuminance(color1)
        val lum2 = calculateLuminance(color2)
        val lighter = max(lum1, lum2)
        val darker = min(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Checks if contrast ratio meets WCAG AA standard for normal body text (>= 4.5:1).
     */
    fun meetsWcagBodyText(color1: Color, color2: Color): Boolean {
        return calculateContrastRatio(color1, color2) >= 4.5
    }

    /**
     * Checks if contrast ratio meets WCAG AA standard for large text and UI components (>= 3.0:1).
     */
    fun meetsWcagUiComponent(color1: Color, color2: Color): Boolean {
        return calculateContrastRatio(color1, color2) >= 3.0
    }
}
