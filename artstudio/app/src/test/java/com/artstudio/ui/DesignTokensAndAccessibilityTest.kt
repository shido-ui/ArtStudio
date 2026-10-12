package com.artstudio.ui

import androidx.compose.ui.unit.dp
import com.artstudio.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Validates design tokens: Touch targets, Spacing, Typography, Elevation, and Motion.
 */
class DesignTokensAndAccessibilityTest {

    @Test
    fun testMinimumTouchTargetIsAtLeast48dp() {
        assertEquals(
            "Android accessibility standard requires minimum 48dp touch targets",
            48.dp,
            StudioAccessibility.MinTouchTarget
        )
        assertTrue(
            "Touch target must be >= 48.dp",
            StudioAccessibility.MinTouchTarget >= 48.dp
        )
    }

    @Test
    fun testSpacingTokensMonotonicityAndPositiveBounds() {
        val spacing = StudioSpacing()
        assertEquals(0.dp, spacing.none)
        assertTrue(spacing.xxs > spacing.none)
        assertTrue(spacing.xs > spacing.xxs)
        assertTrue(spacing.s > spacing.xs)
        assertTrue(spacing.m > spacing.s)
        assertTrue(spacing.l > spacing.m)
        assertTrue(spacing.xl > spacing.l)
        assertTrue(spacing.xxl > spacing.xl)
        assertTrue(spacing.xxxl > spacing.xxl)

        // Ensure 8-point grid alignment
        assertEquals(2.dp, spacing.xxs)
        assertEquals(4.dp, spacing.xs)
        assertEquals(8.dp, spacing.s)
        assertEquals(12.dp, spacing.m)
        assertEquals(16.dp, spacing.l)
        assertEquals(24.dp, spacing.xl)
        assertEquals(32.dp, spacing.xxl)
        assertEquals(48.dp, spacing.xxxl)
    }

    @Test
    fun testElevationTokensMonotonicity() {
        val elevation = StudioElevation()
        assertEquals(0.dp, elevation.flat)
        assertTrue(elevation.subtle > elevation.flat)
        assertTrue(elevation.raised > elevation.subtle)
        assertTrue(elevation.floating > elevation.raised)
        assertTrue(elevation.modal > elevation.floating)

        assertEquals(8.dp, elevation.modal)
    }

    @Test
    fun testMotionTokensAndReducedMotionBypass() {
        val defaultMotion = StudioMotion.Default
        assertFalse(defaultMotion.reducedMotion)
        assertEquals(150, defaultMotion.durationFastMs)
        assertEquals(250, defaultMotion.durationNormalMs)
        assertEquals(400, defaultMotion.durationSlowMs)

        // When reduced motion is activated, all durations MUST be 0ms instant
        val reducedMotion = StudioMotion.Reduced
        assertTrue(reducedMotion.reducedMotion)
        assertEquals(0, reducedMotion.durationFastMs)
        assertEquals(0, reducedMotion.durationNormalMs)
        assertEquals(0, reducedMotion.durationSlowMs)
    }

    @Test
    fun testTypographyScaleScalableText() {
        val type = StudioTypography()
        // Ensure line heights are greater than or equal to font sizes for legibility
        assertTrue(type.displayLarge.lineHeight.value >= type.displayLarge.fontSize.value)
        assertTrue(type.headlineLarge.lineHeight.value >= type.headlineLarge.fontSize.value)
        assertTrue(type.bodyLarge.lineHeight.value >= type.bodyLarge.fontSize.value)
        assertTrue(type.bodyMedium.lineHeight.value >= type.bodyMedium.fontSize.value)
        assertTrue(type.bodySmall.lineHeight.value >= type.bodySmall.fontSize.value)

        // Ensure type scale hierarchy
        assertTrue(type.displayLarge.fontSize > type.displayMedium.fontSize)
        assertTrue(type.displayMedium.fontSize > type.headlineLarge.fontSize)
        assertTrue(type.headlineLarge.fontSize > type.headlineSmall.fontSize)
        assertTrue(type.headlineSmall.fontSize > type.titleLarge.fontSize)
        assertTrue(type.titleLarge.fontSize >= type.bodyLarge.fontSize)
        assertTrue(type.bodyLarge.fontSize > type.bodyMedium.fontSize)
        assertTrue(type.bodyMedium.fontSize > type.bodySmall.fontSize)
    }
}
