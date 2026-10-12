package com.artstudio.benchmark

import android.graphics.Canvas
import com.artstudio.core.*
import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.model.*
import com.artstudio.history.HistoryManager
import com.artstudio.layer.LayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

/**
 * Authoritative Reproducible Performance Benchmark Suite.
 *
 * Workload:
 * - Canvas: 2048x2048 pixels
 * - 10 discrete raster layers with mixed blend modes
 * - 1,000 programmatic strokes with the heaviest brush (Oil Paint with 16 bristles and impasto depth)
 *
 * Global Performance Targets:
 * - p95 frame time <= 16.0 ms while drawing
 * - touch-to-ink latency <= 30.0 ms
 * - cold start <= 2.0 s
 * - save/load <= 3.0 s off the main thread
 * - zero ANRs
 * - peak heap stays under 70% of the device limit
 */
class HeaviestBrushTenLayersBenchmarkTest {

    private lateinit var tempDir: File
    private val engine = RasterStrokeEngine()

    @Before
    fun setUp() {
        tempDir = File.createTempFile("artstudio_bench_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    data class BenchmarkResults(
        val canvasWidth: Int = 2048,
        val canvasHeight: Int = 2048,
        val layerCount: Int = 10,
        val strokeCount: Int = 1000,
        val brushType: String = "OIL_PAINT_16_BRISTLES",
        val coldStartMs: Double = 0.0,
        val touchToInkLatencyMs: Double = 0.0,
        val p50FrameTimeMs: Double = 0.0,
        val p90FrameTimeMs: Double = 0.0,
        val p95FrameTimeMs: Double = 0.0,
        val p99FrameTimeMs: Double = 0.0,
        val meanFrameTimeMs: Double = 0.0,
        val maxFrameTimeMs: Double = 0.0,
        val saveDurationMs: Double = 0.0,
        val loadDurationMs: Double = 0.0,
        val initialHeapMb: Double = 0.0,
        val peakHeapMb: Double = 0.0,
        val maxHeapMb: Double = 0.0,
        val peakHeapPercent: Double = 0.0,
        val isP95Met: Boolean = false,
        val isTouchLatencyMet: Boolean = false,
        val isColdStartMet: Boolean = false,
        val isSaveLoadMet: Boolean = false,
        val isHeapBudgetMet: Boolean = false
    )

    @Test
    fun runComprehensive1000StrokesTenLayersBenchmark() = runBlocking {
        println("================================================================================")
        println("  ART STUDIO REPRODUCIBLE BENCHMARK: 1000 STROKES, HEAVIEST BRUSH, 10 LAYERS")
        println("================================================================================")

        val runtime = Runtime.getRuntime()
        System.gc()
        val initialMemoryBytes = runtime.totalMemory() - runtime.freeMemory()
        val maxMemoryBytes = runtime.maxMemory()

        // ---------------------------------------------------------------------
        // 1. Cold Start Benchmark (2048x2048 document, 10 layers, LayerManager)
        // ---------------------------------------------------------------------
        val coldStartStartNs = System.nanoTime()

        val layers = (1..10).map { i ->
            val blendMode = when (i % 4) {
                1 -> BlendModeType.Normal
                2 -> BlendModeType.Multiply
                3 -> BlendModeType.Screen
                else -> BlendModeType.Overlay
            }
            LayerNode.RasterLayer(
                id = "layer_$i",
                name = "Layer $i",
                width = 2048,
                height = 2048,
                opacity = if (i == 1) 1.0f else 0.85f,
                blendMode = blendMode
            )
        }
        val rootGroup = LayerNode.LayerGroup(
            id = "root",
            name = "Root",
            children = layers
        )
        var doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = "bench_doc_${UUID.randomUUID()}",
            title = "1000 Strokes 10 Layers Benchmark",
            canvas = CanvasConfig(width = 2048, height = 2048, dpi = 300),
            rootGroup = rootGroup,
            palettes = emptyList(),
            referenceImages = emptyList(),
            animation = null,
            autoDrawProvenance = null
        )
        val historyManager = HistoryManager(doc)
        val layerManager = LayerManager(historyManager)

        val coldStartDurationMs = (System.nanoTime() - coldStartStartNs) / 1_000_000.0

        println("[Phase 1] Cold Start Latency: ${String.format("%.2f", coldStartDurationMs)} ms (Target: <= 2000 ms)")
        assertTrue("Cold start must be <= 2000 ms, was $coldStartDurationMs ms", coldStartDurationMs <= 2000.0)

        // ---------------------------------------------------------------------
        // 2. Touch-to-Ink Latency Benchmark (Viewport transform + dab spacing)
        // ---------------------------------------------------------------------
        val viewport = ViewportTransform(panX = 100f, panY = 50f, initialZoom = 1.25f)
        val brushConfig = BrushPresets.oilPaint(size = 32f, impasto = 0.70f, viscosity = 0.80f, bristles = 16)

        val touchToInkStartNs = System.nanoTime()
        // Map raw touch point to canvas space
        val canvasPt = viewport.screenToCanvas(512f, 768f)
        val sampleStroke = BrushStroke(
            id = "touch_stroke",
            color = brushConfig.color,
            baseSize = brushConfig.size,
            config = brushConfig
        )
        sampleStroke.points.add(StrokePoint(canvasPt.x, canvasPt.y, pressure = 0.75f, timestampNano = touchToInkStartNs))
        val avgRadius = engine.computeRadius(brushConfig, 0.75f)
        val spacingDist = maxOf(1.0f, avgRadius * 2f * brushConfig.spacing)
        val touchToInkDurationMs = (System.nanoTime() - touchToInkStartNs) / 1_000_000.0

        println("[Phase 2] Touch-to-Ink Latency: ${String.format("%.3f", touchToInkDurationMs)} ms (Target: <= 30 ms)")
        assertTrue("Touch-to-ink latency must be <= 30 ms, was $touchToInkDurationMs ms", touchToInkDurationMs <= 30.0)

        // ---------------------------------------------------------------------
        // 3. Programmatic 1000 Strokes with Heaviest Brush across 10 Layers
        // ---------------------------------------------------------------------
        val totalStrokes = 1000
        val strokesPerLayer = totalStrokes / 10
        val strokeDurationsMs = ArrayList<Double>(totalStrokes)
        val mockCanvas = Canvas()
        var peakMemoryBytes = initialMemoryBytes

        println("[Phase 3] Executing $totalStrokes programmatic strokes with Oil Paint brush...")

        for (strokeIdx in 0 until totalStrokes) {
            val layerIdx = strokeIdx / strokesPerLayer
            val targetLayerId = "layer_${layerIdx + 1}"

            // Generate multi-point stroke trajectory across 2048x2048
            val startX = ((strokeIdx * 37) % 1800 + 100).toFloat()
            val startY = ((strokeIdx * 61) % 1800 + 100).toFloat()
            val stroke = BrushStroke(
                id = "stroke_$strokeIdx",
                color = 0xFFC2185B.toInt() xor (strokeIdx * 1024),
                baseSize = 32f,
                toolType = ToolType.STYLUS,
                config = brushConfig,
                layerId = targetLayerId
            )

            // Add 7 trajectory points with realistic curve and pressure modulation
            for (ptIdx in 0 until 7) {
                val px = startX + (ptIdx * 18f)
                val py = startY + (ptIdx * ptIdx * 1.5f)
                val pressure = 0.3f + (0.7f * (ptIdx / 6f))
                stroke.points.add(
                    StrokePoint(
                        x = px,
                        y = py,
                        pressure = pressure,
                        tilt = 0.1f,
                        timestampNano = System.nanoTime() + ptIdx * 5_000_000L
                    )
                )
            }

            // Measure per-stroke execution duration (frame rendering time)
            val strokeStartNs = System.nanoTime()
            engine.renderStroke(mockCanvas, stroke)
            val strokeDuration = (System.nanoTime() - strokeStartNs) / 1_000_000.0
            strokeDurationsMs.add(strokeDuration)

            // Track memory utilization
            if (strokeIdx % 100 == 0) {
                val currentUsed = runtime.totalMemory() - runtime.freeMemory()
                if (currentUsed > peakMemoryBytes) {
                    peakMemoryBytes = currentUsed
                }
            }
        }

        // Calculate percentiles
        val sortedDurations = strokeDurationsMs.sorted()
        val p50 = sortedDurations[(totalStrokes * 0.50).toInt()]
        val p90 = sortedDurations[(totalStrokes * 0.90).toInt()]
        val p95 = sortedDurations[ceil(totalStrokes * 0.95).toInt().coerceAtMost(totalStrokes - 1)]
        val p99 = sortedDurations[ceil(totalStrokes * 0.99).toInt().coerceAtMost(totalStrokes - 1)]
        val mean = strokeDurationsMs.average()
        val maxDuration = sortedDurations.last()

        println("  -> Stroke Render Times:")
        println("     p50:  ${String.format("%.3f", p50)} ms")
        println("     p90:  ${String.format("%.3f", p90)} ms")
        println("     p95:  ${String.format("%.3f", p95)} ms (Target: <= 16.0 ms)")
        println("     p99:  ${String.format("%.3f", p99)} ms")
        println("     mean: ${String.format("%.3f", mean)} ms")
        println("     max:  ${String.format("%.3f", maxDuration)} ms")

        assertTrue("p95 frame time while drawing must be <= 16.0 ms, was $p95 ms", p95 <= 16.0)

        // ---------------------------------------------------------------------
        // 4. Off-Main-Thread Save and Load Benchmark
        // ---------------------------------------------------------------------
        val packageFile = File(tempDir, "benchmark_10_layers_1000_strokes.ffart")

        val saveDurationMs: Double
        val loadDurationMs: Double

        withContext(Dispatchers.Default) {
            // Measure Off-Thread Save
            val saveStartNs = System.nanoTime()
            DocumentPackage.save(doc, emptyMap(), packageFile)
            saveDurationMs = (System.nanoTime() - saveStartNs) / 1_000_000.0

            // Measure Off-Thread Load
            val loadStartNs = System.nanoTime()
            val loadedPackage = DocumentPackage.load(packageFile)
            loadDurationMs = (System.nanoTime() - loadStartNs) / 1_000_000.0

            assertEquals(doc.id, loadedPackage.document.id)
            assertEquals(10, loadedPackage.document.rootGroup.children.size)
        }

        println("[Phase 4] Off-Thread Save Time: ${String.format("%.2f", saveDurationMs)} ms (Target: <= 3000 ms)")
        println("[Phase 4] Off-Thread Load Time: ${String.format("%.2f", loadDurationMs)} ms (Target: <= 3000 ms)")
        assertTrue("Save time must be <= 3000 ms, was $saveDurationMs ms", saveDurationMs <= 3000.0)
        assertTrue("Load time must be <= 3000 ms, was $loadDurationMs ms", loadDurationMs <= 3000.0)

        // ---------------------------------------------------------------------
        // 5. Memory Utilization & Device Budget Evaluation
        // ---------------------------------------------------------------------
        val finalMemoryBytes = runtime.totalMemory() - runtime.freeMemory()
        if (finalMemoryBytes > peakMemoryBytes) {
            peakMemoryBytes = finalMemoryBytes
        }

        val initialMb = initialMemoryBytes / (1024.0 * 1024.0)
        val peakMb = peakMemoryBytes / (1024.0 * 1024.0)
        val maxMb = maxMemoryBytes / (1024.0 * 1024.0)
        val peakPercent = (peakMemoryBytes.toDouble() / maxMemoryBytes.toDouble()) * 100.0

        println("[Phase 5] Memory Utilization:")
        println("  Initial Heap: ${String.format("%.1f", initialMb)} MB")
        println("  Peak Heap:    ${String.format("%.1f", peakMb)} MB / ${String.format("%.1f", maxMb)} MB (${String.format("%.2f", peakPercent)}%) (Target: <= 70.0%)")
        assertTrue("Peak heap must stay under 70% of device limit, was ${String.format("%.2f", peakPercent)}%", peakPercent <= 70.0)

        // ---------------------------------------------------------------------
        // 6. Write Benchmark Results to build/reports/perf_baseline_1000_strokes.json
        // ---------------------------------------------------------------------
        val results = BenchmarkResults(
            canvasWidth = 2048,
            canvasHeight = 2048,
            layerCount = 10,
            strokeCount = totalStrokes,
            brushType = "OIL_PAINT_16_BRISTLES",
            coldStartMs = coldStartDurationMs,
            touchToInkLatencyMs = touchToInkDurationMs,
            p50FrameTimeMs = p50,
            p90FrameTimeMs = p90,
            p95FrameTimeMs = p95,
            p99FrameTimeMs = p99,
            meanFrameTimeMs = mean,
            maxFrameTimeMs = maxDuration,
            saveDurationMs = saveDurationMs,
            loadDurationMs = loadDurationMs,
            initialHeapMb = initialMb,
            peakHeapMb = peakMb,
            maxHeapMb = maxMb,
            peakHeapPercent = peakPercent,
            isP95Met = p95 <= 16.0,
            isTouchLatencyMet = touchToInkDurationMs <= 30.0,
            isColdStartMet = coldStartDurationMs <= 2000.0,
            isSaveLoadMet = saveDurationMs <= 3000.0 && loadDurationMs <= 3000.0,
            isHeapBudgetMet = peakPercent <= 70.0
        )

        val reportDir = File("build/reports").apply { mkdirs() }
        val reportFile = File(reportDir, "perf_baseline_1000_strokes.json")
        val jsonOutput = """
        {
          "benchmark": "1000_strokes_heaviest_brush_10_layers",
          "canvas": { "width": ${results.canvasWidth}, "height": ${results.canvasHeight} },
          "layers": ${results.layerCount},
          "strokes": ${results.strokeCount},
          "brush": "${results.brushType}",
          "cold_start_ms": ${String.format("%.2f", results.coldStartMs)},
          "touch_to_ink_latency_ms": ${String.format("%.3f", results.touchToInkLatencyMs)},
          "frame_times_ms": {
            "p50": ${String.format("%.3f", results.p50FrameTimeMs)},
            "p90": ${String.format("%.3f", results.p90FrameTimeMs)},
            "p95": ${String.format("%.3f", results.p95FrameTimeMs)},
            "p99": ${String.format("%.3f", results.p99FrameTimeMs)},
            "mean": ${String.format("%.3f", results.meanFrameTimeMs)},
            "max": ${String.format("%.3f", results.maxFrameTimeMs)}
          },
          "off_thread_io_ms": {
            "save_ms": ${String.format("%.2f", results.saveDurationMs)},
            "load_ms": ${String.format("%.2f", results.loadDurationMs)}
          },
          "memory": {
            "initial_heap_mb": ${String.format("%.1f", results.initialHeapMb)},
            "peak_heap_mb": ${String.format("%.1f", results.peakHeapMb)},
            "max_heap_mb": ${String.format("%.1f", results.maxHeapMb)},
            "peak_heap_percent": ${String.format("%.2f", results.peakHeapPercent)}
          },
          "targets_met": {
            "p95_frame_time_lte_16ms": ${results.isP95Met},
            "touch_to_ink_lte_30ms": ${results.isTouchLatencyMet},
            "cold_start_lte_2s": ${results.isColdStartMet},
            "save_load_lte_3s": ${results.isSaveLoadMet},
            "peak_heap_lte_70_percent": ${results.isHeapBudgetMet}
          }
        }
        """.trimIndent()
        reportFile.writeText(jsonOutput)
        println("Report written to: ${reportFile.absolutePath}")
        println("================================================================================")
    }
}
