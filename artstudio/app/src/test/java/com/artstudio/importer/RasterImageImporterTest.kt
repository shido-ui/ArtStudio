package com.artstudio.importer

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.guides.ReferenceImageManager
import com.artstudio.history.HistoryManager
import com.artstudio.layer.LayerManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class RasterImageImporterTest {

    private lateinit var sampleDocument: ArtDocument
    private lateinit var historyManager: HistoryManager
    private lateinit var layerManager: LayerManager
    private lateinit var referenceManager: ReferenceImageManager

    @Before
    fun setUp() {
        sampleDocument = ArtDocument(
            title = "Base Canvas",
            canvas = CanvasConfig(width = 1920, height = 1080),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = "initial_background_layer",
                        name = "Background",
                        width = 1920,
                        height = 1080,
                        visible = true,
                        opacity = 1.0f
                    )
                )
            ),
            selectedLayerId = "initial_background_layer"
        )
        historyManager = HistoryManager(initialDocument = sampleDocument)
        layerManager = LayerManager(historyManager = historyManager)
        referenceManager = ReferenceImageManager()
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

    private fun computeFileSha256(file: File): String {
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------------------
    // 1. Transparent PNG Tests
    // -------------------------------------------------------------------------

    @Test
    fun testImportAsLayer_transparentPng_preservesAlphaAndDoesNotFlatten() {
        val file = resolveFixture("transparent_png/transparent_cutout_01.png")
        val initialLayerCount = sampleDocument.rootGroup.children.size

        val result = RasterImageImporter.executeImportFromFile(
            file = file,
            destination = ImportDestination.IMPORT_AS_LAYER,
            document = sampleDocument
        )

        assertTrue("Import as layer must succeed", result is ImportResult.Success)
        val success = result as ImportResult.Success
        val updatedDoc = success.document!!
        val importedLayer = success.importedLayer!!

        // Invariant: Canvas is NOT flattened; layer is added
        assertEquals("Document must now contain exactly 2 layers", initialLayerCount + 1, updatedDoc.rootGroup.children.size)
        assertEquals("Initial background layer must remain intact", "initial_background_layer", updatedDoc.rootGroup.children[0].id)
        assertEquals("Newly imported layer must be the second layer", importedLayer.id, updatedDoc.rootGroup.children[1].id)
        assertEquals("Active selection must switch to imported layer", importedLayer.id, updatedDoc.selectedLayerId)

        // Metadata & Alpha verification
        val meta = success.decodedImage.metadata
        assertEquals("PNG", meta.format)
        assertEquals(512, meta.rawWidth)
        assertEquals(512, meta.rawHeight)
        assertTrue("Cutout fixture must report alpha channel transparency", meta.hasAlpha)

        // Pixel transparency check: must contain both 0-alpha and 255-alpha pixels
        var hasTransparent = false
        var hasOpaque = false
        for (p in success.decodedImage.pixels) {
            val a = (p ushr 24) and 0xFF
            if (a == 0) hasTransparent = true
            if (a == 255) hasOpaque = true
        }
        assertTrue("Must contain fully transparent pixels", hasTransparent)
        assertTrue("Must contain fully opaque pixels", hasOpaque)
    }

    @Test
    fun testImportAsLayer_transparentGradient_preservesContinuousAlphaGradient() {
        val file = resolveFixture("transparent_png/transparent_gradient_02.png")

        val result = RasterImageImporter.executeImportFromFile(
            file = file,
            destination = ImportDestination.IMPORT_AS_LAYER,
            document = sampleDocument
        )

        assertTrue("Import gradient must succeed", result is ImportResult.Success)
        val success = result as ImportResult.Success

        val meta = success.decodedImage.metadata
        assertEquals(512, meta.rawWidth)
        assertEquals(512, meta.rawHeight)
        assertTrue("Gradient fixture must have alpha", meta.hasAlpha)

        // Verify continuous intermediate alpha values exist (not binary 1-bit alpha)
        var semiTransparentCount = 0
        for (p in success.decodedImage.pixels) {
            val a = (p ushr 24) and 0xFF
            if (a in 1..254) {
                semiTransparentCount++
            }
        }
        assertTrue("Continuous alpha gradient must contain semi-transparent pixels", semiTransparentCount > 500)
    }

    // -------------------------------------------------------------------------
    // 2. EXIF Rotation Tests
    // -------------------------------------------------------------------------

    @Test
    fun testImportAsLayer_exif90Cw_correctsOrientation() {
        val file = resolveFixture("exif_rotated/exif_rotate_90_cw_01.jpg")

        val result = RasterImageImporter.executeImportFromFile(
            file = file,
            destination = ImportDestination.IMPORT_AS_LAYER,
            document = sampleDocument
        )

        assertTrue("Import EXIF 90 CW must succeed", result is ImportResult.Success)
        val success = result as ImportResult.Success
        val meta = success.decodedImage.metadata

        assertEquals("JPEG", meta.format)
        assertEquals("Must detect EXIF orientation 6 (90 CW)", 6, meta.exifOrientation)

        // Dimensions: Oriented dimensions must match [256, 512]
        assertEquals("Oriented width must be 256", 256, success.decodedImage.width)
        assertEquals("Oriented height must be 512", 512, success.decodedImage.height)
        assertEquals(256 * 512, success.decodedImage.pixels.size)
    }

    @Test
    fun testImportAsLayer_exif180_correctsOrientation() {
        val file = resolveFixture("exif_rotated/exif_rotate_180_02.jpg")

        val result = RasterImageImporter.executeImportFromFile(
            file = file,
            destination = ImportDestination.IMPORT_AS_LAYER,
            document = sampleDocument
        )

        assertTrue("Import EXIF 180 must succeed", result is ImportResult.Success)
        val success = result as ImportResult.Success
        val meta = success.decodedImage.metadata

        assertEquals("JPEG", meta.format)
        assertEquals("Must detect EXIF orientation 3 (180 deg)", 3, meta.exifOrientation)

        // Dimensions: 512 x 512
        assertEquals("Oriented width must be 512", 512, success.decodedImage.width)
        assertEquals("Oriented height must be 512", 512, success.decodedImage.height)
        assertEquals(512 * 512, success.decodedImage.pixels.size)
    }

    // -------------------------------------------------------------------------
    // 3. Add as Reference Tests
    // -------------------------------------------------------------------------

    @Test
    fun testAddAsReference_preservesCanvasIsolation() {
        val file = resolveFixture("transparent_png/transparent_cutout_01.png")
        val initialChildren = sampleDocument.rootGroup.children.toList()

        val result = RasterImageImporter.executeImportFromFile(
            file = file,
            destination = ImportDestination.ADD_AS_REFERENCE,
            document = sampleDocument,
            referenceManager = referenceManager,
            placement = ImportPlacementConfig(x = 100f, y = 150f, scale = 1.0f, opacity = 0.85f)
        )

        assertTrue("Add as reference must succeed", result is ImportResult.Success)
        val success = result as ImportResult.Success

        // Invariant: Canvas is strictly untouched (0 layers added)
        assertEquals("Document layers must remain 100% untouched", initialChildren, sampleDocument.rootGroup.children)
        assertEquals("Reference overlay must be created", 1, referenceManager.references.size)

        val overlay = success.referenceOverlay!!
        assertEquals(100f, overlay.x, 0.01f)
        assertEquals(150f, overlay.y, 0.01f)
        assertEquals(512f, overlay.width, 0.01f)
        assertEquals(512f, overlay.height, 0.01f)
        assertEquals(0.85f, overlay.opacity, 0.01f)
        assertTrue(overlay.visible)

        // Color sampling on reference image works
        val color = referenceManager.sampleColor(overlay.id, 100f + 256f, 150f + 256f)
        assertNotNull("Reference image must support color sampling", color)
    }

    // -------------------------------------------------------------------------
    // 4. Open as New Project Tests
    // -------------------------------------------------------------------------

    @Test
    fun testOpenAsNewProject_createsMatchingCanvasDimensions() {
        val file = resolveFixture("transparent_png/transparent_gradient_02.png")

        val result = RasterImageImporter.executeImportFromFile(
            file = file,
            destination = ImportDestination.OPEN_AS_NEW_PROJECT
        )

        assertTrue("Open as new project must succeed", result is ImportResult.Success)
        val success = result as ImportResult.Success
        val newDoc = success.document!!

        assertEquals("New canvas width must match image width", 512, newDoc.canvas.width)
        assertEquals("New canvas height must match image height", 512, newDoc.canvas.height)
        assertEquals("Root group must have 1 base layer", 1, newDoc.rootGroup.children.size)
        assertEquals("Background", newDoc.rootGroup.children[0].name)
    }

    // -------------------------------------------------------------------------
    // 5. Corrupt / Truncated / Unsupported File Tests (Recoverable Errors)
    // -------------------------------------------------------------------------

    @Test
    fun testImportCorruptPng_truncatedIdat_returnsRecoverableError_documentUntouched() {
        val file = resolveFixture("corrupt_truncated/truncated_idat_01.png")
        val originalDocHash = sampleDocument.hashCode()
        val originalChildren = sampleDocument.rootGroup.children

        val result = RasterImageImporter.executeImportFromFile(
            file = file,
            destination = ImportDestination.IMPORT_AS_LAYER,
            document = sampleDocument
        )

        assertTrue("Corrupt PNG must return ImportResult.Error", result is ImportResult.Error)
        val error = result as ImportResult.Error
        assertEquals("Must report CORRUPT_OR_TRUNCATED", ImportErrorCode.CORRUPT_OR_TRUNCATED, error.errorCode)

        // Invariant: Document is completely untouched
        assertEquals("Active document layers must not change", originalChildren, sampleDocument.rootGroup.children)
        assertEquals("Active document must not mutate", originalDocHash, sampleDocument.hashCode())
    }

    @Test
    fun testImportCorruptOra_returnsRecoverableError_documentUntouched() {
        val file = resolveFixture("corrupt_truncated/corrupted_archive_02.ora")
        val originalChildren = sampleDocument.rootGroup.children

        val result = RasterImageImporter.executeImportFromFile(
            file = file,
            destination = ImportDestination.IMPORT_AS_LAYER,
            document = sampleDocument
        )

        assertTrue("Corrupted archive must return ImportResult.Error", result is ImportResult.Error)
        val error = result as ImportResult.Error
        assertEquals(ImportErrorCode.UNSUPPORTED_FORMAT, error.errorCode)
        assertEquals("Active document layers must not change", originalChildren, sampleDocument.rootGroup.children)
    }

    @Test
    fun testImportMalformedSvg_returnsRecoverableError_documentUntouched() {
        val file = resolveFixture("corrupt_truncated/malformed_tags_03.svg")
        val originalChildren = sampleDocument.rootGroup.children

        val result = RasterImageImporter.executeImportFromFile(
            file = file,
            destination = ImportDestination.IMPORT_AS_LAYER,
            document = sampleDocument
        )

        assertTrue("Malformed SVG must return ImportResult.Error", result is ImportResult.Error)
        val error = result as ImportResult.Error
        assertEquals(ImportErrorCode.UNSUPPORTED_FORMAT, error.errorCode)
        assertEquals("Active document layers must not change", originalChildren, sampleDocument.rootGroup.children)
    }

    // -------------------------------------------------------------------------
    // 6. Oversized Image Resampling Tests
    // -------------------------------------------------------------------------

    @Test
    fun testImportOversizedImage_downsamplesPreservingAspectRatio() {
        // Create an oversized 5000x2500 pixel image buffer (2:1 aspect ratio)
        val rawW = 5000
        val rawH = 2500
        val pixels = IntArray(rawW * rawH) { 0xFF112233.toInt() }

        val (finalW, finalH, finalPixels) = ImageDownsampler.downsampleIfNeeded(
            pixels = pixels,
            width = rawW,
            height = rawH,
            maxDimension = 4096
        )

        assertEquals("Width must be clamped to max budget 4096", 4096, finalW)
        assertEquals("Height must proportionally scale to 2048", 2048, finalH)
        assertEquals(4096 * 2048, finalPixels.size)
        assertEquals("Aspect ratio must be preserved at exactly 2.0", 2.0, finalW.toDouble() / finalH.toDouble(), 0.001)
    }

    // -------------------------------------------------------------------------
    // 7. Source File Immutability Invariant Tests
    // -------------------------------------------------------------------------

    @Test
    fun testSourceFileImmutability_checksumAndSizeUnchanged() {
        val testFixtures = listOf(
            resolveFixture("transparent_png/transparent_cutout_01.png"),
            resolveFixture("transparent_png/transparent_gradient_02.png"),
            resolveFixture("exif_rotated/exif_rotate_90_cw_01.jpg"),
            resolveFixture("exif_rotated/exif_rotate_180_02.jpg"),
            resolveFixture("corrupt_truncated/truncated_idat_01.png")
        )

        val beforeMap = testFixtures.associate { it.absolutePath to Pair(it.length(), computeFileSha256(it)) }

        // Perform multiple import operations on all files
        for (f in testFixtures) {
            RasterImageImporter.executeImportFromFile(
                file = f,
                destination = ImportDestination.IMPORT_AS_LAYER,
                document = sampleDocument
            )
            RasterImageImporter.executeImportFromFile(
                file = f,
                destination = ImportDestination.ADD_AS_REFERENCE,
                referenceManager = referenceManager
            )
        }

        // Verify SHA-256 and byte sizes are 100% bit-exact and untouched
        for (f in testFixtures) {
            val (origLen, origSha) = beforeMap[f.absolutePath]!!
            assertEquals("File size must never change for '${f.name}'", origLen, f.length())
            assertEquals("SHA-256 checksum must be bit-exact for '${f.name}'", origSha, computeFileSha256(f))
        }
    }

    // -------------------------------------------------------------------------
    // 8. Placement & Sizing Geometry Tests
    // -------------------------------------------------------------------------

    @Test
    fun testPlacementConfig_fitCanvasAndFillCanvasMath() {
        // Canvas: 1920 x 1080. Image: 1000 x 2000 (tall 1:2 aspect ratio)
        val (fitX, fitY, fitScale) = RasterImageImporter.calculateInitialPlacement(
            canvasWidth = 1920,
            canvasHeight = 1080,
            imageWidth = 1000,
            imageHeight = 2000,
            fitMode = ImportFitMode.FIT_CANVAS
        )

        // For 1000x2000 fitting into 1920x1080, height is constraint: scale = 1080/2000 = 0.54
        assertEquals(0.54f, fitScale, 0.001f)
        val fitW = 1000f * 0.54f // 540
        assertEquals((1920f - 540f) / 2f, fitX, 0.001f) // 690 centered
        assertEquals(0f, fitY, 0.001f)

        // For FILL_CANVAS, width is constraint: scale = 1920/1000 = 1.92
        val (fillX, fillY, fillScale) = RasterImageImporter.calculateInitialPlacement(
            canvasWidth = 1920,
            canvasHeight = 1080,
            imageWidth = 1000,
            imageHeight = 2000,
            fitMode = ImportFitMode.FILL_CANVAS
        )
        assertEquals(1.92f, fillScale, 0.001f)
        assertEquals(0f, fillX, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 9. Permission Loss & IO Handling Tests
    // -------------------------------------------------------------------------

    @Test
    fun testPermissionLossOrUnreadableFile_returnsRecoverableError() {
        val nonExistentFile = File("/tmp/non_existent_image_${System.currentTimeMillis()}.png")

        val result = RasterImageImporter.executeImportFromFile(
            file = nonExistentFile,
            destination = ImportDestination.IMPORT_AS_LAYER,
            document = sampleDocument
        )

        assertTrue("Missing or unreadable file must return ImportResult.Error", result is ImportResult.Error)
        val error = result as ImportResult.Error
        assertEquals(ImportErrorCode.PERMISSION_DENIED_OR_IO, error.errorCode)
        assertTrue(error.reason.contains("does not exist") || error.reason.contains("permission"))
    }

    // -------------------------------------------------------------------------
    // 10. Undo / Redo Transactional History Tests
    // -------------------------------------------------------------------------

    @Test
    fun testImportUndoRedo_viaHistoryManager() {
        val file = resolveFixture("transparent_png/transparent_cutout_01.png")
        val decoded = RasterImageImporter.decodeRasterImage(file.readBytes(), file.name)
        assertNotNull("Image must decode", decoded)

        assertEquals("Initially 1 layer", 1, layerManager.getLayers().size)

        // Execute import via LayerManager
        val importedLayer = layerManager.importRasterLayer(
            decodedImage = decoded!!,
            layerName = "Imported Cutout"
        )

        assertEquals("Layer count becomes 2 after import", 2, layerManager.getLayers().size)
        assertEquals(importedLayer.id, layerManager.getSelectedLayerId())

        // Undo operation
        historyManager.undo()
        assertEquals("Layer count reverts to 1 after undo", 1, layerManager.getLayers().size)
        assertEquals("initial_background_layer", layerManager.getSelectedLayerId())

        // Redo operation
        historyManager.redo()
        assertEquals("Layer count restored to 2 after redo", 2, layerManager.getLayers().size)
        assertEquals(importedLayer.id, layerManager.getSelectedLayerId())
    }
}
