package com.artstudio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artstudio.animation.AnimationFrame
import com.artstudio.animation.AnimationPlaybackController
import com.artstudio.animation.AnimationTimeline
import com.artstudio.autodraw.AutoDrawV1Pipeline
import com.artstudio.core.FeatureFlags
import com.artstudio.document.io.psd.PsdDocumentImporter
import com.artstudio.document.io.psd.PsdWriter
import com.artstudio.document.model.*
import com.artstudio.export.gif.GifDecoder
import com.artstudio.export.gif.GifEncoder
import com.artstudio.vector.*
import com.artstudio.vector.svg.SafeSvgExporter
import com.artstudio.vector.svg.SafeSvgParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Authoritative instrumented device test verifying shipped features end-to-end on Android device runtime,
 * while verifying disabled hidden features remain strictly unreachable.
 */
@RunWith(AndroidJUnit4::class)
class ShippedFeaturesEndToEndInstrumentedTest {

    @Test
    fun testAutoDrawV1OnDevice() = runBlocking {
        assertTrue(FeatureFlags.isAutoDrawV1Enabled)
        val w = 128
        val h = 128
        val pixels = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        for (i in 30..90) {
            pixels[30 * w + i] = 0xFF000000.toInt()
            pixels[90 * w + i] = 0xFF000000.toInt()
            pixels[i * w + 30] = 0xFF000000.toInt()
            pixels[i * w + 90] = 0xFF000000.toInt()
        }

        val result = AutoDrawV1Pipeline.processLineArt(
            pixels = pixels,
            width = w,
            height = h,
            maxDimension = 64
        )

        assertEquals(64, result.processedWidth)
        assertEquals(64, result.processedHeight)
        assertTrue(result.contours.isNotEmpty())
        assertTrue(result.vectorObjects.isNotEmpty())
        assertTrue(result.schedule.entries.isNotEmpty())

        val doc = ArtDocument(id = "dev_doc", title = "Device Doc", canvas = CanvasConfig(w, h))
        val integration = AutoDrawV1Pipeline.integrateIntoDocument(doc, result)
        assertNotNull(integration)
        assertTrue(integration.document.allLayersFlat().any { it is LayerNode.VectorPathLayer })
    }

    @Test
    fun testPsdAndSvgRoundTripOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = context.cacheDir

        // 1. PSD Round-trip
        val w = 64
        val h = 64
        val l1Id = "dev_l1"
        val l1 = LayerNode.RasterLayer(id = l1Id, name = "Layer 1", width = w, height = h)
        val doc = ArtDocument(
            id = "psd_dev",
            title = "PSD Dev",
            canvas = CanvasConfig(w, h),
            rootGroup = LayerNode.LayerGroup("root", "Root", children = listOf(l1))
        )

        val psdFile = File(cacheDir, "dev_test.psd")
        try {
            val exportRes = PsdWriter.exportPsd(
                document = doc,
                layerPixelBuffers = mapOf(l1Id to IntArray(w * h) { 0xFF336699.toInt() }),
                outputFile = psdFile
            )
            assertTrue(psdFile.exists() && psdFile.length() > 0)
            assertEquals(1, exportRes.layerCount)

            val importRes = PsdDocumentImporter.importPsd(psdFile)
            assertEquals(w, importRes.document.canvas.width)
            assertEquals(h, importRes.document.canvas.height)
        } finally {
            psdFile.delete()
        }

        // 2. SVG Round-trip
        val rectVo = VectorObject(
            id = "dev_rect",
            shapeType = VectorShapeType.RECTANGLE,
            rectData = VectorRectData(x = 5f, y = 5f, width = 50f, height = 50f)
        )
        val svgStr = SafeSvgExporter.exportObjects(listOf(rectVo), 100, 100)
        val parseRes = SafeSvgParser.parse(svgStr)
        assertEquals(100, parseRes.canvasWidth)
        assertTrue(parseRes.vectorObjects.isNotEmpty())
    }

    @Test
    fun testAnimationAndHiddenFeaturesOnDevice() {
        // Animation
        val timeline = AnimationTimeline(
            fps = 24,
            frames = listOf(AnimationFrame(name = "F1"), AnimationFrame(name = "F2")),
            currentFrameIndex = 0
        )
        val controller = AnimationPlaybackController(timeline)
        controller.play()
        controller.stepForward()
        assertEquals(1, controller.currentFrameIndex)

        val bos = ByteArrayOutputStream()
        val encoder = GifEncoder(16, 16, bos, loopCount = 0)
        encoder.encodeFrame(IntArray(256) { 0xFFFF0000.toInt() }, 5)
        encoder.finish()
        val decoded = GifDecoder.decode(bos.toByteArray())
        assertEquals(1, decoded.frames.size)

        // Released and hidden feature flags
        assertFalse(FeatureFlags.isAiEnabled)
        assertTrue(FeatureFlags.isColorStackEnabled)
        assertTrue(FeatureFlags.isDeformationEnabled)
    }
}
