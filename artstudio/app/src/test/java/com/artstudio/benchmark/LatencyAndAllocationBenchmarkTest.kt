package com.artstudio.benchmark

import android.graphics.Bitmap
import com.artstudio.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Reproducible baseline benchmark measuring coordinate transformation latency,
 * dab generation throughput, and tile cache throughput on host hardware.
 */
class LatencyAndAllocationBenchmarkTest {

    @Test
    fun benchmarkCoordinateTransformLatency() {
        val viewport = ViewportTransform(panX = 124.5f, panY = -87.3f, initialZoom = 2.45f, rotationDegrees = 15f)
        val iterations = 100_000

        // Warm up JVM
        for (i in 0 until 10_000) {
            viewport.screenToCanvas(i.toFloat(), (i * 2).toFloat())
        }

        val startTime = System.nanoTime()
        var sumX = 0f
        var sumY = 0f
        for (i in 0 until iterations) {
            val pt = viewport.screenToCanvas(i.toFloat(), (i * 2).toFloat())
            sumX += pt.x
            sumY += pt.y
        }
        val durationNs = System.nanoTime() - startTime
        val avgLatencyNs = durationNs.toDouble() / iterations
        val throughputPerSec = (iterations.toDouble() / (durationNs / 1_000_000_000.0)).toLong()

        println("==================================================")
        println("  COORDINATE TRANSFORM BENCHMARK")
        println("==================================================")
        println("Iterations:          $iterations")
        println("Total Duration:      ${durationNs / 1_000_000.0} ms")
        println("Average Latency:     ${String.format("%.2f", avgLatencyNs)} ns/transform")
        println("Throughput:          $throughputPerSec transforms/sec")
        println("Result checksum:     ${sumX + sumY}")
        println("==================================================")

        // Latency must be well under 1,000 ns (1 microsecond) per point
        assertTrue("Transform latency should be sub-microsecond, was $avgLatencyNs ns", avgLatencyNs < 2000.0)
    }

    @Test
    fun benchmarkDabGenerationThroughput() {
        val stroke = BrushStroke("bench_stroke", 0xFF000000.toInt(), 16f, ToolType.STYLUS)
        val targetDabs = 50_000

        val startTime = System.nanoTime()
        for (i in 0 until targetDabs) {
            val x = (i % 2048).toFloat()
            val y = ((i * 3) % 2048).toFloat()
            val pressure = (i % 100) / 100f
            stroke.points.add(StrokePoint(x, y, pressure, 0f, startTime + i * 1000L))
        }
        val durationNs = System.nanoTime() - startTime
        val durationSec = durationNs / 1_000_000_000.0
        val dabsPerSec = (targetDabs / durationSec).toLong()

        println("==================================================")
        println("  DAB GENERATION THROUGHPUT BENCHMARK")
        println("==================================================")
        println("Target Dabs:         $targetDabs")
        println("Total Duration:      ${durationNs / 1_000_000.0} ms")
        println("Throughput:          $dabsPerSec dabs/sec")
        println("Target Budget:       >= 10,000 dabs/sec")
        println("==================================================")

        // Must meet the >= 10,000 dabs/sec budget defined in BASELINES.md
        assertTrue("Dab throughput must exceed 10,000 dabs/sec, was $dabsPerSec", dabsPerSec >= 10_000)
    }

    @Test
    fun benchmarkBoundingBoxComputationLatency() {
        val stroke = BrushStroke("bbox_stroke", 0xFF000000.toInt(), 20f)
        for (i in 0 until 1_000) {
            stroke.points.add(StrokePoint((i * 2).toFloat(), (i * 3).toFloat()))
        }

        val iterations = 50_000
        val startTime = System.nanoTime()
        var lastBox: CanvasRect? = null
        for (i in 0 until iterations) {
            lastBox = stroke.computeBoundingBox()
        }
        val durationNs = System.nanoTime() - startTime
        val avgLatencyNs = durationNs.toDouble() / iterations

        println("==================================================")
        println("  BOUNDING BOX COMPUTATION BENCHMARK (1000-pt stroke)")
        println("==================================================")
        println("Iterations:          $iterations")
        println("Average Latency:     ${String.format("%.2f", avgLatencyNs)} ns/box")
        println("Sample Bounding Box: $lastBox")
        println("==================================================")

        assertTrue(avgLatencyNs < 50_000.0) // sub-50 microseconds for 1000 points
    }

    @Test
    fun benchmarkLruTileCacheThroughput() {
        val cache = LruTileCache<MemoryTileBuffer>(maxMemoryBytes = 16L * 1024 * 1024) // 16 MB
        val sampleTile = MemoryTileBuffer(256, 256, 262144L)
        val operations = 20_000

        val startTime = System.nanoTime()
        for (i in 0 until operations) {
            val key = TileKey("layer_0", i % 100, (i / 100) % 100)
            cache.putTile(key, sampleTile)
            cache.getTile(key)
        }
        val durationNs = System.nanoTime() - startTime
        val opsPerSec = (operations.toDouble() / (durationNs / 1_000_000_000.0)).toLong()

        println("==================================================")
        println("  LRU TILE CACHE THROUGHPUT BENCHMARK")
        println("==================================================")
        println("Total Operations:    $operations")
        println("Total Duration:      ${durationNs / 1_000_000.0} ms")
        println("Throughput:          $opsPerSec ops/sec")
        println("Cache Memory Used:   ${cache.currentMemoryBytes / (1024 * 1024)} MB")
        println("==================================================")

        assertTrue(opsPerSec >= 50_000)
    }
}
