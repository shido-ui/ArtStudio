package com.artstudio.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artstudio.BuildConfig
import kotlinx.coroutines.delay

/**
 * Debug-only HUD overlay displaying real-time frame time percentiles (p50, p95),
 * estimated FPS, and heap memory usage against global budget gates:
 * - p95 frame time <= 16 ms (while drawing)
 * - peak heap <= 70% of device limit
 *
 * Automatically disabled in release builds via [BuildConfig.DEBUG].
 */
@Composable
fun PerformanceOverlay(
    monitor: PerformanceMetricsMonitor,
    modifier: Modifier = Modifier,
    updateIntervalMs: Long = 250L
) {
    if (!BuildConfig.DEBUG) return

    var snapshot by remember { mutableStateOf(monitor.snapshot()) }

    LaunchedEffect(monitor, updateIntervalMs) {
        while (true) {
            snapshot = monitor.snapshot()
            delay(updateIntervalMs)
        }
    }

    val statusColor = when {
        !snapshot.isFrameBudgetMet && !snapshot.isHeapBudgetMet -> Color(0xFFEF5350) // Red
        !snapshot.isFrameBudgetMet || !snapshot.isHeapBudgetMet -> Color(0xFFFFA726) // Amber
        else -> Color(0xFF66BB6A) // Green
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xDD121212))
            .border(1.dp, statusColor.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PERF (DEBUG)",
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${String.format("%.1f", snapshot.estimatedFps)} FPS",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "p50: ${String.format("%.1f", snapshot.p50FrameTimeMs)}ms | p95: ${String.format("%.1f", snapshot.p95FrameTimeMs)}ms [<=16ms]",
                color = if (snapshot.isFrameBudgetMet) Color(0xFFB0BEC5) else Color(0xFFFF8A80),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "Heap: ${String.format("%.1f", snapshot.usedHeapMb)} / ${String.format("%.0f", snapshot.maxHeapMb)} MB (${String.format("%.1f", snapshot.heapUsagePercent)}%) [<=70%]",
                color = if (snapshot.isHeapBudgetMet) Color(0xFFB0BEC5) else Color(0xFFFF8A80),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
