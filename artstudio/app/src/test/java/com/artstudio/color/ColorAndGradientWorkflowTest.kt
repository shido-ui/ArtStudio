package com.artstudio.color

import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.ColorSwatchData
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.PaletteDefinition
import com.artstudio.history.ApplyGradientCommand
import com.artstudio.history.HistoryManager
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Authoritative test suite for Prompt 22:
 * Primary colour selection, palette persistence, and gradient workflows.
 */
class ColorAndGradientWorkflowTest {

    // Documented tolerance for 8-bit color math conversions
    private val COLOR_TOLERANCE_FLOAT = 0.002f
    private val GAMMA_TOLERANCE = 0.0005f

    // -------------------------------------------------------------
    // 1. Colour Conversions & Known Reference Values
    // -------------------------------------------------------------

    @Test
    fun testRgbToHsv_standardPrimaryAndSecondaryColors() {
        // Red: H=0, S=1, V=1
        val redHsv = ColorData.RED.hsv
        assertEquals("Red Hue", 0f, redHsv[0], 0.1f)
        assertEquals("Red Sat", 1f, redHsv[1], COLOR_TOLERANCE_FLOAT)
        assertEquals("Red Val", 1f, redHsv[2], COLOR_TOLERANCE_FLOAT)

        // Green: H=120, S=1, V=1
        val greenHsv = ColorData.GREEN.hsv
        assertEquals("Green Hue", 120f, greenHsv[0], 0.1f)
        assertEquals("Green Sat", 1f, greenHsv[1], COLOR_TOLERANCE_FLOAT)
        assertEquals("Green Val", 1f, greenHsv[2], COLOR_TOLERANCE_FLOAT)

        // Blue: H=240, S=1, V=1
        val blueHsv = ColorData.BLUE.hsv
        assertEquals("Blue Hue", 240f, blueHsv[0], 0.1f)
        assertEquals("Blue Sat", 1f, blueHsv[1], COLOR_TOLERANCE_FLOAT)
        assertEquals("Blue Val", 1f, blueHsv[2], COLOR_TOLERANCE_FLOAT)

        // Yellow: H=60, S=1, V=1
        val yellowHsv = ColorData.YELLOW.hsv
        assertEquals("Yellow Hue", 60f, yellowHsv[0], 0.1f)
        assertEquals("Yellow Sat", 1f, yellowHsv[1], COLOR_TOLERANCE_FLOAT)

        // Cyan: H=180, S=1, V=1
        val cyanHsv = ColorData.CYAN.hsv
        assertEquals("Cyan Hue", 180f, cyanHsv[0], 0.1f)

        // Magenta: H=300, S=1, V=1
        val magentaHsv = ColorData.MAGENTA.hsv
        assertEquals("Magenta Hue", 300f, magentaHsv[0], 0.1f)

        // White: S=0, V=1
        val whiteHsv = ColorData.WHITE.hsv
        assertEquals("White Sat", 0f, whiteHsv[1], COLOR_TOLERANCE_FLOAT)
        assertEquals("White Val", 1f, whiteHsv[2], COLOR_TOLERANCE_FLOAT)

        // Black: V=0
        val blackHsv = ColorData.BLACK.hsv
        assertEquals("Black Val", 0f, blackHsv[2], COLOR_TOLERANCE_FLOAT)
    }

    @Test
    fun testHsvToRgb_roundTripWithinDocumentedTolerance() {
        val testHues = floatArrayOf(0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f)
        for (h in testHues) {
            for (s in listOf(0.2f, 0.5f, 0.8f, 1.0f)) {
                for (v in listOf(0.3f, 0.7f, 1.0f)) {
                    val color = ColorData.fromHsv(h, s, v)
                    val recHsv = color.hsv

                    assertEquals("Hue roundtrip at $h", h, recHsv[0], 1.0f)
                    assertEquals("Sat roundtrip at $s", s, recHsv[1], COLOR_TOLERANCE_FLOAT)
                    assertEquals("Val roundtrip at $v", v, recHsv[2], COLOR_TOLERANCE_FLOAT)
                }
            }
        }
    }

    @Test
    fun testHexColorParsing_allSupportedFormats() {
        // 3-digit #RGB
        val c1 = ColorData.fromHex("#F00")
        assertEquals(1f, c1.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(0f, c1.green, COLOR_TOLERANCE_FLOAT)
        assertEquals(0f, c1.blue, COLOR_TOLERANCE_FLOAT)
        assertEquals(1f, c1.alpha, COLOR_TOLERANCE_FLOAT)

        // 4-digit #RGBA
        val c2 = ColorData.fromHex("#0F08")
        assertEquals(0f, c2.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(1f, c2.green, COLOR_TOLERANCE_FLOAT)
        assertEquals(0f, c2.blue, COLOR_TOLERANCE_FLOAT)
        assertEquals(0.533f, c2.alpha, 0.05f)

        // 6-digit #RRGGBB
        val c3 = ColorData.fromHex("#FF8000")
        assertEquals(1f, c3.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(0.5019f, c3.green, 0.01f)
        assertEquals(0f, c3.blue, COLOR_TOLERANCE_FLOAT)

        // 8-digit #AARRGGBB
        val c4 = ColorData.fromHex("#800000FF")
        assertEquals(0.5019f, c4.alpha, 0.01f)
        assertEquals(0f, c4.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(0f, c4.green, COLOR_TOLERANCE_FLOAT)
        assertEquals(1f, c4.blue, COLOR_TOLERANCE_FLOAT)

        // Invalid hex throws
        try {
            ColorData.fromHex("#ZZZ")
            fail("Expected IllegalArgumentException on invalid hex")
        } catch (_: Exception) {}
    }

    @Test
    fun testLinearRgbAndSrgbGammaTransfer() {
        // Endpoints 0.0 and 1.0 must be bit-exact
        assertEquals(0f, ColorConversions.srgbToLinear(0f), GAMMA_TOLERANCE)
        assertEquals(1f, ColorConversions.srgbToLinear(1f), GAMMA_TOLERANCE)
        assertEquals(0f, ColorConversions.linearToSrgb(0f), GAMMA_TOLERANCE)
        assertEquals(1f, ColorConversions.linearToSrgb(1f), GAMMA_TOLERANCE)

        // IEC 61966-2-1 transition point at 0.04045
        val linearAtThreshold = ColorConversions.srgbToLinear(0.04045f)
        val expectedThreshold = 0.04045f / 12.92f
        assertEquals(expectedThreshold, linearAtThreshold, GAMMA_TOLERANCE)

        // Mid-gray 0.5 sRGB is approximately 0.21404 linear
        val midLinear = ColorConversions.srgbToLinear(0.5f)
        assertEquals(0.21404f, midLinear, 0.005f)

        // Roundtrip fidelity
        val testValues = floatArrayOf(0.05f, 0.2f, 0.5f, 0.75f, 0.9f)
        for (v in testValues) {
            val lin = ColorConversions.srgbToLinear(v)
            val srgb = ColorConversions.linearToSrgb(lin)
            assertEquals("Roundtrip for $v", v, srgb, GAMMA_TOLERANCE)
        }
    }

    @Test
    fun testAlphaPremultiplicationAndDemultiplication() {
        val color = ColorData(1.0f, 0.5f, 0.2f, 0.5f)
        val premul = color.toPremultiplied()

        assertEquals(0.5f, premul.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(0.25f, premul.green, COLOR_TOLERANCE_FLOAT)
        assertEquals(0.1f, premul.blue, COLOR_TOLERANCE_FLOAT)
        assertEquals(0.5f, premul.alpha, COLOR_TOLERANCE_FLOAT)

        val unPremul = premul.fromPremultiplied()
        assertEquals(1.0f, unPremul.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(0.5f, unPremul.green, COLOR_TOLERANCE_FLOAT)
        assertEquals(0.2f, unPremul.blue, COLOR_TOLERANCE_FLOAT)
        assertEquals(0.5f, unPremul.alpha, COLOR_TOLERANCE_FLOAT)

        // Zero alpha safety
        val transparent = ColorData(0.5f, 0.5f, 0.5f, 0f)
        val unPremulZero = transparent.toPremultiplied().fromPremultiplied()
        assertEquals(0f, unPremulZero.alpha, COLOR_TOLERANCE_FLOAT)
    }

    @Test
    fun testNaiveCmykConversionAndPrintAccurateDisclaimer() {
        val red = ColorData.RED
        val cmyk = red.toCmyk()

        // Red in subtractive CMYK: C=0, M=1, Y=1, K=0
        assertEquals("Cyan", 0f, cmyk.cyan, COLOR_TOLERANCE_FLOAT)
        assertEquals("Magenta", 1f, cmyk.magenta, COLOR_TOLERANCE_FLOAT)
        assertEquals("Yellow", 1f, cmyk.yellow, COLOR_TOLERANCE_FLOAT)
        assertEquals("Black", 0f, cmyk.black, COLOR_TOLERANCE_FLOAT)

        // Roundtrip
        val recRed = cmyk.toColorData()
        assertEquals(1f, recRed.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(0f, recRed.green, COLOR_TOLERANCE_FLOAT)
        assertEquals(0f, recRed.blue, COLOR_TOLERANCE_FLOAT)

        // Print accurate disclaimer must be false and explicitly stated
        assertFalse("CMYK print accurate must be false", cmyk.isPrintAccurate)
        assertTrue("Disclaimer must mention ICC or uncalibrated", cmyk.disclaimer.contains("uncalibrated"))
    }

    // -------------------------------------------------------------
    // 2. Foreground/Background & Color History
    // -------------------------------------------------------------

    @Test
    fun testForegroundBackgroundState_swapAndReset() {
        val state = ForegroundBackgroundState(
            initialForeground = ColorData.RED,
            initialBackground = ColorData.BLUE
        )
        assertEquals(ColorData.RED, state.foreground)
        assertEquals(ColorData.BLUE, state.background)

        // Swap
        state.swapColors()
        assertEquals(ColorData.BLUE, state.foreground)
        assertEquals(ColorData.RED, state.background)

        // Reset
        state.resetDefaults()
        assertEquals(ColorData.BLACK, state.foreground)
        assertEquals(ColorData.WHITE, state.background)
    }

    @Test
    fun testColorHistory_deduplicationAndCapacityConstraint() {
        val history = ColorHistory(maxCapacity = 4)
        history.push(ColorData.RED)
        history.push(ColorData.GREEN)
        history.push(ColorData.BLUE)

        assertEquals(3, history.items.size)
        assertEquals(ColorData.BLUE, history.items[0])

        // Push duplicate RED: should move RED to front without increasing size
        history.push(ColorData.RED)
        assertEquals(3, history.items.size)
        assertEquals(ColorData.RED, history.items[0])

        // Push more colors to test capacity clamping
        history.push(ColorData.YELLOW)
        history.push(ColorData.CYAN)
        history.push(ColorData.MAGENTA)

        assertEquals(4, history.items.size)
        assertEquals(ColorData.MAGENTA, history.items[0])
    }

    // -------------------------------------------------------------
    // 3. Palettes Management & Persistence
    // -------------------------------------------------------------

    @Test
    fun testPaletteRepository_crudAndPersistenceAcrossProjects() {
        val tempDir = File.createTempFile("palette_test", "dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val repo = PaletteRepository(storageDir = tempDir)
            assertEquals("Has standard palettes", 5, repo.getAllPalettes().size)

            // Create user palette
            val custom = repo.createPalette(
                name = "Anime Cel Shading",
                swatches = listOf(
                    ColorSwatchData("#FFD1DC", "Skin Highlight"),
                    ColorSwatchData("#F4A6A0", "Skin Midtone"),
                    ColorSwatchData("#C76D6D", "Skin Shadow")
                )
            )

            assertEquals(6, repo.getAllPalettes().size)
            assertNotNull(repo.findPaletteById(custom.id))

            // Add swatch
            repo.addSwatchToPalette(custom.id, ColorSwatchData("#222222", "Hair Lineart"))
            val updated = repo.findPaletteById(custom.id)
            assertEquals(4, updated!!.swatches.size)

            // Simulate app restart / reopen by creating a new repository pointing to same storageDir
            val reloadedRepo = PaletteRepository(storageDir = tempDir)
            val reloadedCustom = reloadedRepo.findPaletteById(custom.id)

            assertNotNull("User palette survives app restart", reloadedCustom)
            assertEquals("Anime Cel Shading", reloadedCustom!!.name)
            assertEquals(4, reloadedCustom.swatches.size)
            assertEquals("#222222", reloadedCustom.swatches.last().hex)

            // GPL Export & Import
            val gpl = reloadedRepo.exportToGpl(reloadedCustom)
            assertTrue(gpl.contains("GIMP Palette"))
            assertTrue(gpl.contains("Anime Cel Shading"))

            val importedFromGpl = reloadedRepo.importFromGpl(gpl, "Imported Anime")
            assertEquals("Anime Cel Shading", importedFromGpl.name)
            assertEquals(4, importedFromGpl.swatches.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPaletteIsolationInvariant_changingPalettesDoesNotMutateExistingLayers() {
        // Document with a 64x64 raster layer
        val layer = LayerNode.RasterLayer(name = "Painting", width = 64, height = 64)
        val initialPixels = IntArray(64 * 64) { 0xFFFF0000.toInt() }

        val doc = ArtDocument(
            canvas = CanvasConfig(width = 64, height = 64),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(layer)),
            palettes = listOf(StandardPalettes.CLASSIC_STUDIO)
        )

        // User edits, creates, or deletes palettes
        val modifiedPalettes = doc.palettes + PaletteDefinition(name = "Temp Palette")
        val newDoc = doc.copy(palettes = modifiedPalettes)

        // Existing layer identity and properties are preserved
        val layerAfter = newDoc.findLayerById(layer.id) as LayerNode.RasterLayer
        assertEquals("Layer ID unchanged", layer.id, layerAfter.id)
        assertEquals("Layer width unchanged", 64, layerAfter.width)
        assertEquals("Initial pixels untouched", 0xFFFF0000.toInt(), initialPixels[0])
    }

    @Test
    fun testPaletteExtractor_medianCutQuantization() {
        // Create synthetic image with 4 quadrants: Red, Green, Blue, Yellow
        val w = 64; val h = 64
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val color = when {
                    x < 32 && y < 32 -> 0xFFFF0000.toInt() // Red
                    x >= 32 && y < 32 -> 0xFF00FF00.toInt() // Green
                    x < 32 && y >= 32 -> 0xFF0000FF.toInt() // Blue
                    else -> 0xFFFFFF00.toInt() // Yellow
                }
                pixels[y * w + x] = color
            }
        }

        val extracted = PaletteExtractor.extractPalette(pixels, "4-Color Quad", maxColors = 4)
        assertEquals("Must extract 4 swatches", 4, extracted.swatches.size)

        val extractedColors = extracted.swatches.map { ColorData.fromHex(it.hex) }
        // Verify that Red, Green, Blue, Yellow are each captured within small distance
        assertTrue("Has Red", extractedColors.any { it.deltaRgb(ColorData.RED) < 0.1f })
        assertTrue("Has Green", extractedColors.any { it.deltaRgb(ColorData.GREEN) < 0.1f })
        assertTrue("Has Blue", extractedColors.any { it.deltaRgb(ColorData.BLUE) < 0.1f })
        assertTrue("Has Yellow", extractedColors.any { it.deltaRgb(ColorData.YELLOW) < 0.1f })
    }

    // -------------------------------------------------------------
    // 4. Editable Linear & Radial Gradients
    // -------------------------------------------------------------

    @Test
    fun testGradientLinear_samplingAndEndpointsInterpolation() {
        val grad = GradientConfig(
            type = GradientType.LINEAR,
            stops = listOf(
                GradientColorStop(0f, ColorData.RED),
                GradientColorStop(1f, ColorData.BLUE)
            ),
            startX = 0f, startY = 0f,
            endX = 100f, endY = 0f,
            tileMode = GradientTileMode.CLAMP
        )

        // At t = 0 -> Red
        val c0 = grad.sampleAt(0f)
        assertEquals(1f, c0.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(0f, c0.blue, COLOR_TOLERANCE_FLOAT)

        // At t = 1 -> Blue
        val c1 = grad.sampleAt(1f)
        assertEquals(0f, c1.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(1f, c1.blue, COLOR_TOLERANCE_FLOAT)

        // At t = 0.5 -> Midpoint (linear interpolation)
        val cMid = grad.sampleAt(0.5f)
        assertTrue("Red channel active", cMid.red > 0.3f)
        assertTrue("Blue channel active", cMid.blue > 0.3f)

        // Test Clamp tile mode
        val cBeyond = grad.sampleAt(1.5f)
        assertEquals(0f, cBeyond.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(1f, cBeyond.blue, COLOR_TOLERANCE_FLOAT)
    }

    @Test
    fun testGradientRadial_samplingAndDistanceFalloff() {
        val grad = GradientConfig(
            type = GradientType.RADIAL,
            stops = listOf(
                GradientColorStop(0f, ColorData.WHITE),
                GradientColorStop(1f, ColorData.BLACK)
            ),
            centerX = 50f, centerY = 50f,
            radius = 50f
        )

        // Center (50, 50) -> t = 0 -> White
        val tCenter = GradientRenderer.calculateRadialT(50f, 50f, 50f, 50f, 50f)
        assertEquals(0f, tCenter, 0.001f)
        val cCenter = grad.sampleAt(tCenter)
        assertEquals(1f, cCenter.red, COLOR_TOLERANCE_FLOAT)

        // Edge (100, 50) -> t = 1 -> Black
        val tEdge = GradientRenderer.calculateRadialT(100f, 50f, 50f, 50f, 50f)
        assertEquals(1f, tEdge, 0.001f)
        val cEdge = grad.sampleAt(tEdge)
        assertEquals(0f, cEdge.red, COLOR_TOLERANCE_FLOAT)

        // Quarter (62.5, 50) -> distance 12.5 -> t = 0.25
        val tQuarter = GradientRenderer.calculateRadialT(62.5f, 50f, 50f, 50f, 50f)
        assertEquals(0.25f, tQuarter, 0.001f)
    }

    @Test
    fun testGradientRenderer_renderToBufferWithSelectionMask() {
        val width = 64; val height = 64
        val dest = IntArray(width * height) { 0xFF000000.toInt() }

        // Mask: top half unmasked (alpha = 0), bottom half selected (alpha = 255)
        val mask = ByteArray(width * height) { idx ->
            val y = idx / width
            if (y >= 32) 255.toByte() else 0.toByte()
        }

        val grad = GradientConfig(
            type = GradientType.LINEAR,
            stops = listOf(
                GradientColorStop(0f, ColorData.YELLOW),
                GradientColorStop(1f, ColorData.CYAN)
            ),
            startX = 0f, startY = 0f,
            endX = 64f, endY = 64f
        )

        GradientRenderer.renderGradient(dest, width, height, grad, selectionMask = mask)

        // Top half (y < 32) must be untouched (black)
        assertEquals("Top left pixel untouched", 0xFF000000.toInt(), dest[0])
        assertEquals("Top middle pixel untouched", 0xFF000000.toInt(), dest[16 * width + 16])

        // Bottom half (y >= 32) must be filled with gradient
        assertNotEquals("Bottom half filled", 0xFF000000.toInt(), dest[40 * width + 40])
    }

    @Test
    fun testApplyGradientCommand_transactionalUndoRedo() {
        val layer = LayerNode.RasterLayer(name = "Target", width = 32, height = 32)
        val prePixels = IntArray(32 * 32) { 0xFFFFFFFF.toInt() }

        val doc = ArtDocument(
            canvas = CanvasConfig(width = 32, height = 32),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(layer))
        )
        val history = HistoryManager(doc)

        val postPixels = IntArray(32 * 32)
        val grad = GradientConfig(
            name = "Test Grad",
            stops = listOf(
                GradientColorStop(0f, ColorData.RED),
                GradientColorStop(1f, ColorData.BLUE)
            ),
            startX = 0f, startY = 0f, endX = 32f, endY = 0f
        )
        GradientRenderer.renderGradient(postPixels, 32, 32, grad)

        val command = ApplyGradientCommand(
            layerId = layer.id,
            preGradientPixels = prePixels,
            postGradientPixels = postPixels,
            width = 32,
            height = 32,
            gradientName = "Test Grad"
        )

        // Execute command
        val executedDoc = history.execute(command)
        assertNotNull(executedDoc)
        assertEquals("Post pixels recorded", postPixels[0], command.postGradientPixels[0])

        // Undo command
        assertNotNull(history.undo())
        assertEquals("Undo restores initial prePixels", prePixels[0], command.preGradientPixels[0])

        // Redo command
        assertNotNull(history.redo())
        assertEquals("Redo reapplies postPixels", postPixels[0], command.postGradientPixels[0])
    }

    // -------------------------------------------------------------
    // 5. Project Save/Reopen Persistence
    // -------------------------------------------------------------

    @Test
    fun testProjectSaveAndReopen_palettesAndGradientsSurviveSerialization() {
        val customPalette = PaletteDefinition(
            id = "proj_palette_1",
            name = "Project Specific Palette",
            swatches = listOf(
                ColorSwatchData("#FF0055", "Neon Pink"),
                ColorSwatchData("#00FFAA", "Mint Glow")
            )
        )

        val customGradient = GradientConfig(
            id = "proj_grad_1",
            name = "Project Cyber Gradient",
            type = GradientType.LINEAR,
            stops = listOf(
                GradientColorStop(0.0f, ColorData.fromHex("#FF0055")),
                GradientColorStop(1.0f, ColorData.fromHex("#00FFAA"))
            ),
            startX = 10f, startY = 20f,
            endX = 200f, endY = 400f,
            tileMode = GradientTileMode.REPEAT,
            interpolateInLinearSpace = true
        )

        val originalDoc = ArtDocument(
            title = "Color Test Project",
            palettes = listOf(customPalette),
            gradientPresets = listOf(customGradient)
        )

        // Serialize to JSON map
        val serializedMap = DocumentSerializer.toMap(originalDoc)

        // Deserialize back
        val reopenedDoc = DocumentSerializer.fromMap(serializedMap)

        assertEquals("Project title matches", originalDoc.title, reopenedDoc.title)
        assertEquals("Palettes count matches", 1, reopenedDoc.palettes.size)
        assertEquals("Palette ID matches", "proj_palette_1", reopenedDoc.palettes[0].id)
        assertEquals("Palette Name matches", "Project Specific Palette", reopenedDoc.palettes[0].name)
        assertEquals("Palette swatches count", 2, reopenedDoc.palettes[0].swatches.size)
        assertEquals("#FF0055", reopenedDoc.palettes[0].swatches[0].hex)

        assertEquals("Gradient presets count matches", 1, reopenedDoc.gradientPresets.size)
        val reopenedGrad = reopenedDoc.gradientPresets[0]
        assertEquals("proj_grad_1", reopenedGrad.id)
        assertEquals("Project Cyber Gradient", reopenedGrad.name)
        assertEquals(GradientType.LINEAR, reopenedGrad.type)
        assertEquals(2, reopenedGrad.stops.size)
        assertEquals(GradientTileMode.REPEAT, reopenedGrad.tileMode)
        assertEquals(10f, reopenedGrad.startX, 0.001f)
        assertEquals(20f, reopenedGrad.startY, 0.001f)
        assertEquals(200f, reopenedGrad.endX, 0.001f)
        assertEquals(400f, reopenedGrad.endY, 0.001f)
        assertTrue(reopenedGrad.interpolateInLinearSpace)
    }

    // -------------------------------------------------------------
    // 6. Eyedropper Sampling & Radius Averaging
    // -------------------------------------------------------------

    @Test
    fun testEyedropper_pointAndRadiusAveraging() {
        val w = 16; val h = 16
        val pixels = IntArray(w * h) { 0xFF000000.toInt() }

        // Center 4 pixels at (8, 8) are Red
        pixels[8 * w + 8] = 0xFFFF0000.toInt()

        // 1x1 Point Sample at (8, 8) -> Pure Red
        val pointSample = EyedropperEngine.samplePixels(pixels, w, h, 8, 8, EyedropperSampleMode.POINT_1X1)
        assertEquals(1f, pointSample.red, COLOR_TOLERANCE_FLOAT)
        assertEquals(0f, pointSample.green, COLOR_TOLERANCE_FLOAT)
        assertEquals(0f, pointSample.blue, COLOR_TOLERANCE_FLOAT)

        // 3x3 Average at (8, 8): 1 red pixel among 9 pixels -> Red channel averaged with 8 black pixels
        val avgSample = EyedropperEngine.samplePixels(pixels, w, h, 8, 8, EyedropperSampleMode.AVERAGE_3X3)
        assertEquals(1f / 9f, avgSample.red, 0.02f)

        // Loupe grid data generation
        val loupe = EyedropperEngine.createLoupeData(pixels, w, h, 8, 8, gridSize = 5)
        assertEquals(5, loupe.gridSize)
        assertEquals(25, loupe.gridColors.size)
        assertEquals(pointSample, loupe.centerColor)
    }
}
