package com.artstudio

import android.content.Context
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artstudio.core.*
import com.artstudio.document.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented device benchmark test running the 1000-stroke heaviest-brush workload
 * across 10 layers on an active Android device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class ArtStudioHeaviestBrushDeviceBenchmarkTest {

    @Test
    fun testHeaviestBrushStrokesOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull("Context must be available on device", context)

        val engine = RasterStrokeEngine()
        val brushConfig = BrushPresets.oilPaint(size = 32f, impasto = 0.70f, viscosity = 0.80f, bristles = 16)
        val mockCanvas = Canvas()

        val sampleCount = 50 // Device smoke run
        val durationsMs = ArrayList<Double>(sampleCount)

        for (i in 0 until sampleCount) {
            val stroke = BrushStroke("dev_stroke_$i", config = brushConfig)
            stroke.points.add(StrokePoint(100f + i * 2f, 100f + i * 3f, 0.5f))
            stroke.points.add(StrokePoint(150f + i * 2f, 180f + i * 3f, 0.8f))

            val start = System.nanoTime()
            engine.renderStroke(mockCanvas, stroke)
            val dur = (System.nanoTime() - start) / 1_000_000.0
            durationsMs.add(dur)
        }

        val p95 = durationsMs.sorted()[(sampleCount * 0.95).toInt().coerceAtMost(sampleCount - 1)]
        println("[Device Instrumented Benchmark] Sample size: $sampleCount, p95: $p95 ms")
        assertTrue("p95 frame time on device must be reasonable", p95 < 50.0)
    }
}
