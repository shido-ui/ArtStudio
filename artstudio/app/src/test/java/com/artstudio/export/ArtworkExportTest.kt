package com.artstudio.export

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.importer.RasterImageImporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArtworkExportTest {

    private lateinit var tempDir: File
    private lateinit var exportService: ArtworkExportService
    private lateinit var baseDocument: ArtDocument

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("artstudio_export_tests").toFile()
        exportService = ArtworkExportService()

        baseDocument = ArtDocument(
            title = "Test Artwork",
            canvas = CanvasConfig(width = 64, height = 64, backgroundColor = 0xFFFFFFFFL),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = "layer_base",
                        name = "Base Layer",
                        width = 64,
                        height = 64,
                        visible = true,
                        opacity = 1.0f
                    )
                )
            ),
            selectedLayerId = "layer_base"
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun resolveFixture(relativePath: String): File {
        val candidates = listOf(
            File("tests/fixtures/artstudio/v1/$relativePath"),
            File("../tests/fixtures/artstudio/v1/$relativePath"),
            File("/root/artstudio/tests/fixtures/artstudio/v1/$relativePath")
        )
        val found = candidates.firstOrNull { it.exists() }
        assertNotNull("Fixture '$relativePath' must exist on disk", found)
        return found!!
    }

    // 1. Output Format: PNG with Full Canvas and Bit-Exact Zero Pixel Diff Round-Trip
    @Test
    fun testExport_png_fullCanvas_preservesDimensionsAndZeroPixelDiff() = runBlocking {
        val width = 32
        val height = 32
        val testPixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            // Deterministic gradient pattern with full opacity
            (0xFF shl 24) or ((x * 7) shl 16) or ((y * 7) shl 8) or 0x80
        }

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "layer_1", name = "Test", width = width, height = height)
                )
            )
        )

        val targetFile = File(tempDir, "output_lossless.png")
        val config = ExportConfig(
            format = ExportFormat.PNG,
            scope = ExportScope.FULL_CANVAS,
            transparentBackground = false
        )

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf("layer_1" to testPixels)
        )

        assertTrue("Export must succeed", result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(width, success.width)
        assertEquals(height, success.height)
        assertEquals(ExportFormat.PNG, success.format)
        assertTrue(targetFile.exists())
        assertTrue(targetFile.length() > 0)

        // Decode with RasterImageImporter and verify 100% bit-exact pixel reproduction
        val decoded = RasterImageImporter.decodeRasterImage(targetFile.readBytes(), targetFile.name)
        assertNotNull("Decoded raster must not be null", decoded)
        assertEquals(width, decoded!!.width)
        assertEquals(height, decoded.height)

        var diffPixels = 0
        for (i in testPixels.indices) {
            if (testPixels[i] != decoded.pixels[i]) {
                diffPixels++
            }
        }
        assertEquals("PNG round-trip diff pixels must be exactly zero (bit-exact)", 0, diffPixels)
    }

    // 2. Alpha Preservation: Transparent Background PNG
    @Test
    fun testExport_transparentBackground_png_preservesAlpha() = runBlocking {
        val width = 16
        val height = 16
        val testPixels = IntArray(width * height) { i ->
            if (i < 128) {
                // Semi-transparent cyan: A = 128 (0x80)
                (0x80 shl 24) or (0x00 shl 16) or (0xAA shl 8) or 0xFF
            } else {
                // Fully transparent
                0x00000000
            }
        }

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "trans_layer", name = "Trans", width = width, height = height)
                )
            )
        )

        val targetFile = File(tempDir, "transparent.png")
        val config = ExportConfig(
            format = ExportFormat.PNG,
            transparentBackground = true
        )

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf("trans_layer" to testPixels)
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertTrue("Export result must declare alpha channel present", success.hasAlpha)

        val decoded = RasterImageImporter.decodeRasterImage(targetFile.readBytes(), targetFile.name)
        assertNotNull(decoded)

        var alphaPreservedCount = 0
        for (p in decoded!!.pixels) {
            val a = (p ushr 24) and 0xFF
            if (a < 255) alphaPreservedCount++
        }
        assertEquals("All 256 pixels have non-opaque alpha", 256, alphaPreservedCount)
    }

    // 3. Format: JPEG flattens transparent pixels over opaque background
    @Test
    fun testExport_jpeg_flattensAlphaOverOpaqueBackground() = runBlocking {
        val width = 20
        val height = 20
        val testPixels = IntArray(width * height) {
            0x00000000 // Fully transparent
        }

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height, backgroundColor = 0xFFFFFFFFL),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "layer_alpha", name = "Alpha", width = width, height = height)
                )
            )
        )

        val targetFile = File(tempDir, "flattened.jpg")
        val config = ExportConfig(
            format = ExportFormat.JPEG,
            quality = 90
        )

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf("layer_alpha" to testPixels)
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(ExportFormat.JPEG, success.format)
        assertFalse("JPEG output cannot have alpha", success.hasAlpha)
        assertTrue(targetFile.exists())

        // Verify JPEG magic bytes FF D8
        val bytes = targetFile.readBytes()
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
    }

    // 4. Format: WebP creates valid container with RIFF and WEBP magic bytes
    @Test
    fun testExport_webp_createsValidContainer() = runBlocking {
        val width = 16
        val height = 16
        val testPixels = IntArray(width * height) { 0xFF00FF00.toInt() } // Opaque green

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "l1", name = "L1", width = width, height = height)
                )
            )
        )

        val targetFile = File(tempDir, "image.webp")
        val config = ExportConfig(
            format = ExportFormat.WEBP_LOSSLESS
        )

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf("l1" to testPixels)
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(ExportFormat.WEBP_LOSSLESS, success.format)

        val bytes = targetFile.readBytes()
        assertTrue(bytes.size >= 12)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(bytes, 8, 4, Charsets.US_ASCII))
    }

    // 5. Scope: Selected Layer exports only target layer
    @Test
    fun testExport_selectedLayer_onlyRendersTargetLayer() = runBlocking {
        val width = 10
        val height = 10
        val redPixels = IntArray(width * height) { 0xFFFF0000.toInt() }
        val bluePixels = IntArray(width * height) { 0xFF0000FF.toInt() }

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "layer_red", name = "Red", width = width, height = height),
                    LayerNode.RasterLayer(id = "layer_blue", name = "Blue", width = width, height = height)
                )
            ),
            selectedLayerId = "layer_red"
        )

        val targetFile = File(tempDir, "selected_layer.png")
        val config = ExportConfig(
            format = ExportFormat.PNG,
            scope = ExportScope.SELECTED_LAYER,
            targetLayerOrGroupId = "layer_red",
            transparentBackground = true
        )

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf(
                "layer_red" to redPixels,
                "layer_blue" to bluePixels
            )
        )

        assertTrue(result is ExportResult.Success)
        val decoded = RasterImageImporter.decodeRasterImage(targetFile.readBytes(), targetFile.name)
        assertNotNull(decoded)

        // All pixels must be Red, not Blue
        for (p in decoded!!.pixels) {
            val r = (p ushr 16) and 0xFF
            val b = p and 0xFF
            assertEquals(255, r)
            assertEquals(0, b)
        }
    }

    // 6. Scope: Selected Group exports only group children
    @Test
    fun testExport_selectedGroup_onlyRendersGroupChildren() = runBlocking {
        val width = 10
        val height = 10
        val groupChildPixels = IntArray(width * height) { 0xFF00FF00.toInt() } // Green
        val outsideChildPixels = IntArray(width * height) { 0xFFFF0000.toInt() } // Red

        val group = LayerNode.LayerGroup(
            id = "group_1",
            name = "Group 1",
            children = listOf(
                LayerNode.RasterLayer(id = "group_layer", name = "Child", width = width, height = height)
            )
        )

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "outside_layer", name = "Outside", width = width, height = height),
                    group
                )
            )
        )

        val targetFile = File(tempDir, "selected_group.png")
        val config = ExportConfig(
            format = ExportFormat.PNG,
            scope = ExportScope.SELECTED_GROUP,
            targetLayerOrGroupId = "group_1",
            transparentBackground = true
        )

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf(
                "outside_layer" to outsideChildPixels,
                "group_layer" to groupChildPixels
            )
        )

        assertTrue(result is ExportResult.Success)
        val decoded = RasterImageImporter.decodeRasterImage(targetFile.readBytes(), targetFile.name)
        assertNotNull(decoded)

        // All pixels must be Green from the group
        for (p in decoded!!.pixels) {
            val g = (p ushr 8) and 0xFF
            val r = (p ushr 16) and 0xFF
            assertEquals(255, g)
            assertEquals(0, r)
        }
    }

    // 7. Group Visibility: Hidden group children are culled during export
    @Test
    fun testExport_hiddenGroup_childrenAreCulled() = runBlocking {
        val width = 8
        val height = 8
        val hiddenGroupPixels = IntArray(width * height) { 0xFFFF0000.toInt() } // Red

        val hiddenGroup = LayerNode.LayerGroup(
            id = "hidden_group",
            name = "Hidden",
            visible = false, // Inactive
            children = listOf(
                LayerNode.RasterLayer(id = "hidden_child", name = "Child", width = width, height = height)
            )
        )

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height, backgroundColor = 0xFFFFFFFFL),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(hiddenGroup)
            )
        )

        val targetFile = File(tempDir, "hidden_cull.png")
        val config = ExportConfig(format = ExportFormat.PNG)

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf("hidden_child" to hiddenGroupPixels)
        )

        assertTrue(result is ExportResult.Success)
        val decoded = RasterImageImporter.decodeRasterImage(targetFile.readBytes(), targetFile.name)
        assertNotNull(decoded)

        // Red pixels culled; backdrop remains white
        for (p in decoded!!.pixels) {
            assertEquals("Opaque white background preserved", 0xFFFFFFFF.toInt(), p)
        }
    }

    // 8. Size & Crop: Sub-rectangle crop region extraction
    @Test
    fun testExport_cropRegion_extractsSubRectangle() = runBlocking {
        val width = 20
        val height = 20
        val canvasPixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            if (x >= 5 && x < 15 && y >= 5 && y < 15) {
                0xFF0000FF.toInt() // Blue center 10x10
            } else {
                0xFFFFFFFF.toInt() // White border
            }
        }

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "full_layer", name = "Full", width = width, height = height)
                )
            )
        )

        val targetFile = File(tempDir, "crop_subrect.png")
        val config = ExportConfig(
            format = ExportFormat.PNG,
            scope = ExportScope.CROP_REGION,
            cropRect = ExportCropRect(left = 5, top = 5, right = 15, bottom = 15)
        )

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf("full_layer" to canvasPixels)
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(10, success.width)
        assertEquals(10, success.height)

        val decoded = RasterImageImporter.decodeRasterImage(targetFile.readBytes(), targetFile.name)
        assertNotNull(decoded)
        assertEquals(10, decoded!!.width)
        assertEquals(10, decoded.height)

        // All 10x10 cropped pixels are blue
        for (p in decoded.pixels) {
            assertEquals(0xFF0000FF.toInt(), p)
        }
    }

    // 9. Size & Scaling: Downsampling with scale factor
    @Test
    fun testExport_dimensionScaling_downsamplesPreservingRatio() = runBlocking {
        val width = 40
        val height = 40
        val solidPixels = IntArray(width * height) { 0xFFFFCC00.toInt() }

        val doc = ArtDocument(
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "layer_scale", name = "Scale", width = width, height = height)
                )
            )
        )

        val targetFile = File(tempDir, "scaled_half.png")
        val config = ExportConfig(
            format = ExportFormat.PNG,
            scaleFactor = 0.5f // 40x40 -> 20x20
        )

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf("layer_scale" to solidPixels)
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(20, success.width)
        assertEquals(20, success.height)

        val decoded = RasterImageImporter.decodeRasterImage(targetFile.readBytes(), targetFile.name)
        assertNotNull(decoded)
        assertEquals(20, decoded!!.width)
        assertEquals(20, decoded.height)
    }

    // 10. Cancellation: Cooperative abort cleans up temporary file and leaves target absent
    @Test
    fun testExport_cancellation_abortsAndCleansUpTempFile() = runBlocking {
        val token = ExportCancellationToken()
        token.cancel() // Cancel before start

        val targetFile = File(tempDir, "cancelled.png")
        val config = ExportConfig(format = ExportFormat.PNG)

        val result = exportService.exportArtwork(
            document = baseDocument,
            config = config,
            targetFile = targetFile,
            cancellationToken = token
        )

        assertEquals("Result must be Cancelled", ExportResult.Cancelled, result)
        assertFalse("Destination file must not exist on cancellation", targetFile.exists())

        // Ensure no leftover .tmp files
        val tmpFiles = tempDir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("No temporary files must remain", 0, tmpFiles.size)
    }

    // 11. Collision: AUTO_RENAME strategy creates incremented non-colliding filename
    @Test
    fun testExport_filenameCollision_autoRenameStrategy() = runBlocking {
        val targetFile = File(tempDir, "masterpiece.png")
        targetFile.writeText("pre-existing content")

        val config = ExportConfig(
            format = ExportFormat.PNG,
            conflictStrategy = FilenameConflictStrategy.AUTO_RENAME
        )

        val result = exportService.exportArtwork(
            document = baseDocument,
            config = config,
            targetFile = targetFile
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals("masterpiece (1).png", success.outputFile.name)
        assertTrue(success.outputFile.exists())
        assertEquals("pre-existing content", targetFile.readText()) // Original untouched
    }

    // 12. Collision: FAIL_IF_EXISTS strategy halts with FILENAME_CONFLICT error
    @Test
    fun testExport_filenameCollision_failIfExistsStrategy() = runBlocking {
        val targetFile = File(tempDir, "protected.png")
        targetFile.writeText("unmodifiable")

        val config = ExportConfig(
            format = ExportFormat.PNG,
            conflictStrategy = FilenameConflictStrategy.FAIL_IF_EXISTS
        )

        val result = exportService.exportArtwork(
            document = baseDocument,
            config = config,
            targetFile = targetFile
        )

        assertTrue(result is ExportResult.Error)
        val error = result as ExportResult.Error
        assertEquals(ExportErrorCode.FILENAME_CONFLICT, error.errorCode)
        assertEquals("unmodifiable", targetFile.readText()) // Untouched
    }

    // 13. Low Storage: Fails safely with INSUFFICIENT_STORAGE error
    @Test
    fun testExport_lowStorage_returnsRecoverableError() = runBlocking {
        val targetFile = File(tempDir, "storage_test.png")
        val config = ExportConfig(format = ExportFormat.PNG)

        val result = exportService.exportArtwork(
            document = baseDocument,
            config = config,
            targetFile = targetFile,
            storageSpaceCheck = { _, _ -> false } // Simulate insufficient space
        )

        assertTrue(result is ExportResult.Error)
        val error = result as ExportResult.Error
        assertEquals(ExportErrorCode.INSUFFICIENT_STORAGE, error.errorCode)
        assertFalse(targetFile.exists())
    }

    // 14. Invariant: Failed export never reports success and leaves source document 100% untouched
    @Test
    fun testExport_failedExport_neverDisplaysSuccess_documentUntouched() = runBlocking {
        val initialDocJson = baseDocument.toString()
        val initialLayersCount = baseDocument.rootGroup.children.size

        val config = ExportConfig(
            format = ExportFormat.PNG,
            scope = ExportScope.SELECTED_LAYER,
            targetLayerOrGroupId = "non_existent_layer_id"
        )

        val targetFile = File(tempDir, "should_fail.png")
        val result = exportService.exportArtwork(
            document = baseDocument,
            config = config,
            targetFile = targetFile
        )

        assertTrue("Result must be error, not success", result is ExportResult.Error)
        val err = result as ExportResult.Error
        assertEquals(ExportErrorCode.TARGET_LAYER_NOT_FOUND, err.errorCode)
        assertFalse("Destination file must not be created", targetFile.exists())

        // Invariant check: Document must not be mutated
        assertEquals("Document layers count unchanged", initialLayersCount, baseDocument.rootGroup.children.size)
        assertEquals("Document string representation identical", initialDocJson, baseDocument.toString())
    }

    // 15. Real Fixture Golden: transparent_cutout_01.png round-trip pixel diff zero
    @Test
    fun testExport_fixtureGolden_transparentPng_roundTrip() = runBlocking {
        val fixtureFile = resolveFixture("transparent_png/transparent_cutout_01.png")
        val decodedFixture = RasterImageImporter.decodeRasterImage(fixtureFile.readBytes(), fixtureFile.name)
        assertNotNull("Fixture must decode successfully", decodedFixture)

        val doc = ArtDocument(
            title = "Golden Transparent Fixture",
            canvas = CanvasConfig(width = decodedFixture!!.width, height = decodedFixture.height),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = "golden_layer",
                        name = "Cutout",
                        width = decodedFixture.width,
                        height = decodedFixture.height
                    )
                )
            )
        )

        val targetFile = File(tempDir, "exported_golden_cutout.png")
        val config = ExportConfig(
            format = ExportFormat.PNG,
            transparentBackground = true
        )

        val result = exportService.exportArtwork(
            document = doc,
            config = config,
            targetFile = targetFile,
            layerPixelBuffers = mapOf("golden_layer" to decodedFixture.pixels)
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(decodedFixture.width, success.width)
        assertEquals(decodedFixture.height, success.height)

        val reDecoded = RasterImageImporter.decodeRasterImage(targetFile.readBytes(), targetFile.name)
        assertNotNull(reDecoded)

        var diffPixels = 0
        for (i in decodedFixture.pixels.indices) {
            if (decodedFixture.pixels[i] != reDecoded!!.pixels[i]) {
                diffPixels++
            }
        }
        assertEquals("Transparent golden PNG round-trip must have exactly zero diff pixels", 0, diffPixels)
    }
}
