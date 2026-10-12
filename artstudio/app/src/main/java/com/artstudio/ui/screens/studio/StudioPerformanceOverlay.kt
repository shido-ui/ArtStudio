package com.artstudio.ui.screens.studio

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artstudio.BuildConfig
import com.artstudio.ui.overlay.PerformanceMetricsMonitor
import com.artstudio.ui.overlay.PerformanceOverlay
import com.artstudio.ui.theme.StudioTheme

/**
 * Debug-only performance telemetry HUD displaying real-time frame duration (p50/p95),
 * touch-to-ink latency, and heap memory usage.
 * Automatically removed from release builds via BuildConfig.DEBUG.
 */
@Composable
fun BoxScope.StudioPerformanceOverlay(
    monitor: PerformanceMetricsMonitor,
    modifier: Modifier = Modifier
) {
    if (BuildConfig.DEBUG) {
        val spacing = StudioTheme.spacing
        PerformanceOverlay(
            monitor = monitor,
            modifier = modifier
                .align(Alignment.TopEnd)
                .padding(top = 76.dp, end = spacing.m)
        )
    }
}
