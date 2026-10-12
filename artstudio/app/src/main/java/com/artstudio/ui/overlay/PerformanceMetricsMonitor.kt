package com.artstudio.ui.overlay

import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.ceil
import kotlin.math.max

/**
 * Thread-safe rolling performance monitor for measuring frame rendering times,
 * percentiles (p50, p95), and heap memory utilization against global budgets.
 *
 * Targets:
 * - p95 frame time <= 16.0 ms (60 fps rendering)
 * - peak heap <= 70% of device memory limit
 */
class PerformanceMetricsMonitor(
    private val maxSamples: Int = 120
) {
    private val frameSamples = ConcurrentLinkedDeque<Float>()

    data class Snapshot(
        val p50FrameTimeMs: Float,
        val p95FrameTimeMs: Float,
        val meanFrameTimeMs: Float,
        val sampleCount: Int,
        val estimatedFps: Float,
        val usedHeapMb: Float,
        val maxHeapMb: Float,
        val heapUsagePercent: Float,
        val isFrameBudgetMet: Boolean,
        val isHeapBudgetMet: Boolean
    )

    /**
     * Records a single frame render duration in milliseconds.
     */
    fun recordFrameDurationMs(durationMs: Float) {
        if (durationMs < 0f) return
        frameSamples.addLast(durationMs)
        while (frameSamples.size > maxSamples) {
            frameSamples.pollFirst()
        }
    }

    /**
     * Clears all recorded samples.
     */
    fun reset() {
        frameSamples.clear()
    }

    /**
     * Takes an instantaneous snapshot of performance and memory metrics.
     */
    fun snapshot(): Snapshot {
        val samples = frameSamples.toList()
        val count = samples.size

        val p50: Float
        val p95: Float
        val mean: Float
        if (count > 0) {
            val sorted = samples.sorted()
            val p50Idx = (count * 0.50f).toInt().coerceIn(0, count - 1)
            val p95Idx = ceil(count * 0.95f).toInt().coerceIn(0, count - 1)
            p50 = sorted[p50Idx]
            p95 = sorted[p95Idx]
            mean = samples.average().toFloat()
        } else {
            p50 = 0f
            p95 = 0f
            mean = 0f
        }

        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory()
        val freeMem = runtime.freeMemory()
        val maxMem = runtime.maxMemory()

        val usedBytes = max(0L, totalMem - freeMem)
        val usedMb = usedBytes / (1024f * 1024f)
        val maxMb = maxMem / (1024f * 1024f)
        val heapPercent = if (maxMem > 0L) (usedBytes.toFloat() / maxMem.toFloat()) * 100f else 0f

        val fps = if (mean > 0f) (1000f / mean).coerceAtMost(120f) else 60f

        return Snapshot(
            p50FrameTimeMs = p50,
            p95FrameTimeMs = p95,
            meanFrameTimeMs = mean,
            sampleCount = count,
            estimatedFps = fps,
            usedHeapMb = usedMb,
            maxHeapMb = maxMb,
            heapUsagePercent = heapPercent,
            isFrameBudgetMet = p95 <= 16.0f,
            isHeapBudgetMet = heapPercent <= 70.0f
        )
    }
}
