package com.artstudio.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Standard motion durations and easing curves.
 * Supports reduced-motion accessibility mode where all animated transitions are instant (0ms).
 */
@Immutable
data class StudioMotion(
    val reducedMotion: Boolean = false,
    val durationFastMs: Int = if (reducedMotion) 0 else 150,
    val durationNormalMs: Int = if (reducedMotion) 0 else 250,
    val durationSlowMs: Int = if (reducedMotion) 0 else 400,
    val standardEasing: Easing = FastOutSlowInEasing,
    val linearEasing: Easing = LinearEasing
) {
    companion object {
        val Default = StudioMotion(reducedMotion = false)
        val Reduced = StudioMotion(reducedMotion = true)
    }
}

val LocalStudioMotion = staticCompositionLocalOf { StudioMotion.Default }
