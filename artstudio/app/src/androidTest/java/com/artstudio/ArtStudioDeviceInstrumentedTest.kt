package com.artstudio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artstudio.core.*
import com.artstudio.document.model.*
import com.artstudio.ui.ArtStudioDrawingView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented device test verifying core drawing surface and document state
 * execution directly on an Android device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class ArtStudioDeviceInstrumentedTest {

    @Test
    fun testDrawingViewInstantiationOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawingView = ArtStudioDrawingView(context)

        assertNotNull("DrawingView must instantiate on device context", drawingView)
        assertEquals(2048, drawingView.document.width)
        assertEquals(2048, drawingView.document.height)
        assertNotNull(drawingView.viewport)
        assertNotNull(drawingView.renderer)
    }

    @Test
    fun testTenLayersDocumentCreationAndViewportMapping() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawingView = ArtStudioDrawingView(context)

        // Configure viewport dimensions (simulating 1080x1920 phone display)
        drawingView.viewport.viewportWidth = 1080f
        drawingView.viewport.viewportHeight = 1920f
        drawingView.viewport.fitToScreen(2048f, 2048f, 1080f, 1920f)

        assertTrue("Zoom must be positive when fitted to screen", drawingView.viewport.zoom > 0f)

        // Add 10 discrete raster layers
        val layers = (1..10).map { idx ->
            LayerNode.RasterLayer(
                id = "device_layer_$idx",
                name = "Device Layer $idx",
                width = 2048,
                height = 2048
            )
        }
        val doc = ArtDocument(
            id = "device_doc_10",
            title = "Device 10 Layers",
            canvas = CanvasConfig(2048, 2048),
            rootGroup = LayerNode.LayerGroup("root", "Root", children = layers)
        )

        assertEquals(10, doc.rootGroup.children.size)
    }

    @Test
    fun testDeviceStrokeExecution() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawingView = ArtStudioDrawingView(context)
        val config = BrushPresets.hardPen(size = 12f)
        drawingView.setBrushConfig(config)

        val stroke = BrushStroke("device_stroke", config = config)
        stroke.points.add(StrokePoint(100f, 100f, 0.5f))
        stroke.points.add(StrokePoint(200f, 200f, 0.8f))
        drawingView.document.addCommittedStroke(stroke)

        assertEquals(1, drawingView.document.committedStrokes.size)
    }
}
