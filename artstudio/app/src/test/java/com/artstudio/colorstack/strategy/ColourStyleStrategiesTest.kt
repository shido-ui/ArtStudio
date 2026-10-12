package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.cv.palette.*
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.BlendModeType
import com.artstudio.document.model.CanvasConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Verification test suite for Colour Lab Rendering Strategies (Styles 1 through 5).
 * Validates algorithmic distinctions, parameter sensitivities, source preservation modes,
 * layer decomposition, and reproducibility.
 */
class ColourStyleStrategiesTest {

    private val width = 64
    private val height = 64
    private val totalPixels = width * height

    private lateinit var regionMask1: ByteArray // Left half
    private lateinit var regionMask2: ByteArray // Right half
    private lateinit var regionMasks: Map<String, ByteArray>
    private lateinit var regionColors: Map<String, ColorData>
    private lateinit var paletteAnalysis: SourcePaletteAnalysis
    private lateinit var linework: LineworkData

    @Before
    fun setUp() {
        regionMask1 = ByteArray(totalPixels)
        regionMask2 = ByteArray(totalPixels)

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (x < width / 2) {
                    regionMask1[idx] = 255.toByte()
                } else {
                    regionMask2[idx] = 255.toByte()
                }
            }
        }

        regionMasks = mapOf(
            "region_left" to regionMask1,
            "region_right" to regionMask2
        )

        // Region 1: Sky Blue, Region 2: Peach
        regionColors = mapOf(
            "region_left" to ColorData(0.2f, 0.6f, 0.9f, 1.0f),
            "region_right" to ColorData(0.95f, 0.65f, 0.5f, 1.0f)
        )

        // Palette analysis for testing GENTLE_ADAPT and HARMONIC_TONAL
        val swatch1 = SourcePaletteSwatch(
            color = ColorData(0.25f, 0.65f, 0.85f, 1.0f),
            pixelCount = 1000,
            frequency = 0.5f,
            luminance = 0.6f,
            inferredRole = InferredColorRole.BASE_COLOR
        )
        val swatch2 = SourcePaletteSwatch(
            color = ColorData(0.90f, 0.60f, 0.45f, 1.0f),
            pixelCount = 1000,
            frequency = 0.5f,
            luminance = 0.65f,
            inferredRole = InferredColorRole.ACCENT
        )
        val globalSwatches = listOf(swatch1, swatch2)
        val regionAssignments = mapOf(
            "region_left" to RegionColorAssignment(
                regionId = "region_left",
                observedColor = ColorData(0.2f, 0.6f, 0.9f, 1.0f),
                mappedSwatchId = swatch1.id,
                assignedColor = ColorData(0.18f, 0.55f, 0.82f, 1.0f)
            ),
            "region_right" to RegionColorAssignment(
                regionId = "region_right",
                observedColor = ColorData(0.95f, 0.65f, 0.5f, 1.0f),
                mappedSwatchId = swatch2.id,
                assignedColor = ColorData(0.88f, 0.58f, 0.42f, 1.0f)
            )
        )
        paletteAnalysis = SourcePaletteAnalysis(
            globalPalette = globalSwatches,
            regionAssignments = regionAssignments
        )

        // Synthetic linework (center vertical dividing line)
        val lwPixels = IntArray(totalPixels)
        for (y in 0 until height) {
            val idx = y * width + (width / 2)
            lwPixels[idx] = (0xFF shl 24) or (0x10 shl 16) or (0x10 shl 8) or 0x10
        }
        linework = LineworkData(
            width = width,
            height = height,
            pixels = lwPixels,
            blendMode = BlendModeType.Multiply,
            opacity = 0.9f
        )
    }

    // =========================================================================
    // STYLE 1: CLASSIC CEL SHADING TESTS
    // =========================================================================

    @Test
    fun testClassicCelDefaultRenderProducesExpectedLayersAndComposite() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            paletteAnalysis = paletteAnalysis,
            linework = linework,
            config = ClassicCelConfig()
        )

        val output = ClassicCelStrategy.render(input)

        assertEquals(ColorStyleId.CLASSIC_CEL, output.styleId)
        assertEquals(width, output.width)
        assertEquals(height, output.height)
        assertEquals(totalPixels, output.finalComposite.size)

        // Verify independent layer decomposition: Base Flats, Cel Shadows, Specular Highlights, Linework
        val layerNames = output.layers.map { it.name }
        assertTrue("Must contain Base Flats", layerNames.contains("Base Flats"))
        assertTrue("Must contain Cel Shadows", layerNames.contains("Cel Shadows"))
        assertTrue("Must contain Specular Highlights", layerNames.contains("Specular Highlights"))
        assertTrue("Must contain Line Art (Preserved)", layerNames.contains("Line Art (Preserved)"))

        // Verify that cel shadow layer contains multiplied shadow pixels
        val shadowLayer = output.layers.first { it.role == LayerRole.SHADOW }
        val nonZeroShadowCount = shadowLayer.pixels.count { (it ushr 24) > 0 }
        assertTrue("Shadow layer should have active shadow pixels", nonZeroShadowCount > 0)

        // Verify execution metrics
        assertTrue("Render time must be recorded", output.renderTimeMs >= 0)
        assertTrue("Allocated memory must be positive", output.memoryAllocatedBytes > 0)
    }

    @Test
    fun testClassicCelParameterVariationModifiesShadowRegion() {
        val inputBase = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = ClassicCelConfig(shadowDarkness = 0.20f, shadowExtent = 0.30f)
        )
        val outputBase = ClassicCelStrategy.render(inputBase)

        val inputDeep = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = ClassicCelConfig(shadowDarkness = 0.80f, shadowExtent = 0.70f)
        )
        val outputDeep = ClassicCelStrategy.render(inputDeep)

        val shadowBase = outputBase.layers.first { it.role == LayerRole.SHADOW }
        val shadowDeep = outputDeep.layers.first { it.role == LayerRole.SHADOW }

        val baseCount = shadowBase.pixels.count { (it ushr 24) > 0 }
        val deepCount = shadowDeep.pixels.count { (it ushr 24) > 0 }

        assertTrue("Deeper extent must produce larger shadow area: deepCount=$deepCount > baseCount=$baseCount", deepCount > baseCount)

        // Compare composites
        var diffPixels = 0
        for (i in 0 until totalPixels) {
            if (outputBase.finalComposite[i] != outputDeep.finalComposite[i]) {
                diffPixels++
            }
        }
        assertTrue("Changing shadow parameters must alter output composite: diffPixels=$diffPixels", diffPixels > 100)
    }

    @Test
    fun testClassicCelSourcePreservationModes() {
        val inputExact = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            paletteAnalysis = paletteAnalysis,
            config = ClassicCelConfig(sourcePreservation = SourcePreservationMode.PRESERVE_EXACT)
        )
        val outputExact = ClassicCelStrategy.render(inputExact)

        val inputAdapt = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            paletteAnalysis = paletteAnalysis,
            config = ClassicCelConfig(sourcePreservation = SourcePreservationMode.GENTLE_ADAPT)
        )
        val outputAdapt = ClassicCelStrategy.render(inputAdapt)

        val inputHarmonic = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            paletteAnalysis = paletteAnalysis,
            config = ClassicCelConfig(sourcePreservation = SourcePreservationMode.HARMONIC_TONAL)
        )
        val outputHarmonic = ClassicCelStrategy.render(inputHarmonic)

        // Verify base flats differ across source preservation modes
        val baseExact = outputExact.layers.first { it.role == LayerRole.BASE_FLAT }.pixels[5]
        val baseAdapt = outputAdapt.layers.first { it.role == LayerRole.BASE_FLAT }.pixels[5]
        val baseHarmonic = outputHarmonic.layers.first { it.role == LayerRole.BASE_FLAT }.pixels[5]

        assertNotEquals("Gentle adapt should snap to swatch", baseExact, baseAdapt)
        assertNotEquals("Harmonic tonal should use adapted color", baseExact, baseHarmonic)
    }

    // =========================================================================
    // STYLE 2: SOFT GRADIENT SHADING TESTS
    // =========================================================================

    @Test
    fun testSoftGradientDefaultRenderProducesSmoothGradients() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = SoftGradientConfig(softness = 0.65f, shadowRatio = 0.45f)
        )

        val output = SoftGradientStrategy.render(input)

        assertEquals(ColorStyleId.SOFT_GRADIENT, output.styleId)
        val layerNames = output.layers.map { it.name }
        assertTrue("Must contain Base Flats", layerNames.contains("Base Flats"))
        assertTrue("Must contain Soft Gradients", layerNames.contains("Soft Gradients"))

        val gradLayer = output.layers.first { it.role == LayerRole.GRADIENT }
        val alphaValues = gradLayer.pixels.map { (it ushr 24) and 0xFF }.filter { it > 0 }.toSet()

        // Continuous smooth gradient should contain a rich spectrum of intermediate alpha values, not binary 0/255
        assertTrue("Soft gradient must have continuous gradient alpha spectrum (> 15 levels), got ${alphaValues.size}", alphaValues.size > 15)
    }

    @Test
    fun testSoftGradientParameterVariationSoftnessAndShadowRatio() {
        val inputGentle = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = SoftGradientConfig(softness = 0.3f, shadowRatio = 0.20f)
        )
        val outputGentle = SoftGradientStrategy.render(inputGentle)

        val inputDeep = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = SoftGradientConfig(softness = 1.5f, shadowRatio = 0.80f)
        )
        val outputDeep = SoftGradientStrategy.render(inputDeep)

        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (outputGentle.finalComposite[i] != outputDeep.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Changing softness & shadowRatio must change gradient output: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // STYLE 3: HYBRID CEL + SOFT SHADING TESTS
    // =========================================================================

    @Test
    fun testHybridCelSoftDefaultRenderProducesDualLighting() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = HybridCelSoftConfig(celVsSoftBalance = 0.50f)
        )

        val output = HybridCelSoftStrategy.render(input)

        assertEquals(ColorStyleId.HYBRID_CEL_SOFT, output.styleId)
        val layerRoles = output.layers.map { it.role }

        assertTrue("Must have Base Flats", layerRoles.contains(LayerRole.BASE_FLAT))
        assertTrue("Must have Cel Shadow Core", layerRoles.contains(LayerRole.SHADOW))
        assertTrue("Must have Soft Midtone Gradients", layerRoles.contains(LayerRole.GRADIENT))
        assertTrue("Must have Specular Highlights", layerRoles.contains(LayerRole.HIGHLIGHT))
        assertTrue("Must have Preserved Linework", layerRoles.contains(LayerRole.LINEWORK))
    }

    @Test
    fun testHybridCelSoftBalanceShiftsBetweenCelAndSoft() {
        // High Cel Balance (0.90)
        val inputCelHeavy = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = HybridCelSoftConfig(celVsSoftBalance = 0.90f)
        )
        val outputCelHeavy = HybridCelSoftStrategy.render(inputCelHeavy)

        // Low Cel Balance (0.10)
        val inputSoftHeavy = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = HybridCelSoftConfig(celVsSoftBalance = 0.10f)
        )
        val outputSoftHeavy = HybridCelSoftStrategy.render(inputSoftHeavy)

        val celShadowCoreHigh = outputCelHeavy.layers.first { it.name == "Cel Shadow Core" }
        val celShadowCoreLow = outputSoftHeavy.layers.first { it.name == "Cel Shadow Core" }

        val avgAlphaHigh = celShadowCoreHigh.pixels.map { (it ushr 24) and 0xFF }.filter { it > 0 }.average()
        val avgAlphaLow = celShadowCoreLow.pixels.map { (it ushr 24) and 0xFF }.filter { it > 0 }.average()

        assertTrue("Higher cel balance must have stronger cel core alpha: high=$avgAlphaHigh > low=$avgAlphaLow", avgAlphaHigh > avgAlphaLow)
    }

    // =========================================================================
    // STYLE 4: FLAT ILLUSTRATION TESTS
    // =========================================================================

    @Test
    fun testFlatIllustrationDefaultProducesCleanGraphicFlats() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = FlatIllustrationConfig(boundaryBleedPx = 0, contrastBoost = 0.0f)
        )

        val output = FlatIllustrationStrategy.render(input)

        assertEquals(ColorStyleId.FLAT_ILLUSTRATION, output.styleId)
        val baseLayer = output.layers.first { it.role == LayerRole.BASE_FLAT }

        // Flats must have zero gradients or shadows: only pure solid fill colors per region
        val leftPixels = mutableSetOf<Int>()
        for (y in 0 until height) {
            for (x in 0 until (width / 2 - 2)) {
                val px = baseLayer.pixels[y * width + x]
                if ((px ushr 24) > 0) leftPixels.add(px and 0x00FFFFFF)
            }
        }
        assertEquals("Unshaded flat illustration must have exactly 1 solid color in left region", 1, leftPixels.size)
    }

    @Test
    fun testFlatIllustrationBoundaryBleedDilationExpandsMask() {
        val inputNoBleed = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = mapOf("test_box" to createBoxMask(20, 20, 40, 40)),
            regionColors = mapOf("test_box" to ColorData(1f, 0f, 0f, 1f)),
            config = FlatIllustrationConfig(boundaryBleedPx = 0)
        )
        val outputNoBleed = FlatIllustrationStrategy.render(inputNoBleed)

        val inputBleed = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = mapOf("test_box" to createBoxMask(20, 20, 40, 40)),
            regionColors = mapOf("test_box" to ColorData(1f, 0f, 0f, 1f)),
            config = FlatIllustrationConfig(boundaryBleedPx = 2)
        )
        val outputBleed = FlatIllustrationStrategy.render(inputBleed)

        val countNoBleed = outputNoBleed.layers.first { it.role == LayerRole.BASE_FLAT }.pixels.count { (it ushr 24) > 0 }
        val countBleed = outputBleed.layers.first { it.role == LayerRole.BASE_FLAT }.pixels.count { (it ushr 24) > 0 }

        assertTrue("Positive boundary bleed (+2) must dilate mask area under linework: bleed=$countBleed > noBleed=$countNoBleed", countBleed > countNoBleed)
    }

    @Test
    fun testFlatIllustrationPlanarStepMode() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = FlatIllustrationConfig(toneMode = FlatToneMode.PLANAR_STEP)
        )

        val output = FlatIllustrationStrategy.render(input)

        val layerNames = output.layers.map { it.name }
        assertTrue("Must contain Planar Graphic Steps layer", layerNames.contains("Planar Graphic Steps"))
    }

    // =========================================================================
    // STYLE 5: PAINTERLY TESTS
    // =========================================================================

    @Test
    fun testPainterlyDefaultProducesTextureAndBristles() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = PainterlyConfig(brushScale = 1.0f, colorJitter = 0.15f, canvasTooth = 0.35f, seed = 42L)
        )

        val output = PainterlyStrategy.render(input)

        assertEquals(ColorStyleId.PAINTERLY, output.styleId)
        val layerNames = output.layers.map { it.name }

        assertTrue("Must contain Underpainting Base", layerNames.contains("Underpainting Base"))
        assertTrue("Must contain Bristle Brushwork", layerNames.contains("Bristle Brushwork"))
        assertTrue("Must contain Canvas Paper Tooth", layerNames.contains("Canvas Paper Tooth"))
        assertTrue("Must contain Line Art (Preserved)", layerNames.contains("Line Art (Preserved)"))

        // Verify color jitter introduces subtle tonal variation in underpainting
        val underpainting = output.layers.first { it.name == "Underpainting Base" }
        val distinctTones = underpainting.pixels.filter { (it ushr 24) > 0 }.toSet()
        assertTrue("Painterly underpainting must have local chromatic jitter (> 50 tones), got ${distinctTones.size}", distinctTones.size > 50)
    }

    @Test
    fun testPainterlyDeterminismAcrossSeeds() {
        val input1 = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = PainterlyConfig(seed = 12345L)
        )
        val output1A = PainterlyStrategy.render(input1)
        val output1B = PainterlyStrategy.render(input1)

        val input2 = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = PainterlyConfig(seed = 99999L)
        )
        val output2 = PainterlyStrategy.render(input2)

        // Seeded determinism: identical seeds must yield identical pixels
        assertArrayEquals("Same seed must yield bit-for-bit deterministic output", output1A.finalComposite, output1B.finalComposite)

        // Different seeds must yield different painterly dabs
        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (output1A.finalComposite[i] != output2.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Different seeds must yield distinct artistic marks: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // STYLE 6: WATERCOLOUR TESTS
    // =========================================================================

    @Test
    fun testWatercolourDefaultRenderProducesExpectedLayersAndWash() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = WatercolourConfig()
        )

        val output = WatercolourStrategy.render(input)

        assertEquals(ColorStyleId.WATERCOLOUR, output.styleId)
        val layerNames = output.layers.map { it.name }
        assertTrue("Must contain Translucent Wash", layerNames.contains("Translucent Wash"))
        assertTrue("Must contain Wet-Edge Puddle Rings", layerNames.contains("Wet-Edge Puddle Rings"))
        assertTrue("Must contain Paper Granulation", layerNames.contains("Paper Granulation"))
        assertTrue("Must contain Line Art (Preserved)", layerNames.contains("Line Art (Preserved)"))

        val washLayer = output.layers.first { it.name == "Translucent Wash" }
        assertEquals(LayerRole.BASE_FLAT, washLayer.role)
        assertTrue("Wash opacity should match config", washLayer.opacity > 0.5f)
    }

    @Test
    fun testWatercolourParameterVariationPuddleRingAndGranulation() {
        val inputGentle = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = WatercolourConfig(puddleRingDarkness = 0.10f, granulation = 0.10f)
        )
        val outputGentle = WatercolourStrategy.render(inputGentle)

        val inputHeavy = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = WatercolourConfig(puddleRingDarkness = 0.85f, granulation = 0.80f)
        )
        val outputHeavy = WatercolourStrategy.render(inputHeavy)

        var diffPixels = 0
        for (i in 0 until totalPixels) {
            if (outputGentle.finalComposite[i] != outputHeavy.finalComposite[i]) {
                diffPixels++
            }
        }
        assertTrue("Varying puddle ring darkness & granulation must alter composite: diffPixels=$diffPixels", diffPixels > 100)
    }

    // =========================================================================
    // STYLE 7: COLOURED PENCIL TESTS
    // =========================================================================

    @Test
    fun testColouredPencilDefaultRenderProducesHatchingAndTooth() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = ColouredPencilConfig()
        )

        val output = ColouredPencilStrategy.render(input)

        assertEquals(ColorStyleId.COLOURED_PENCIL, output.styleId)
        val layerNames = output.layers.map { it.name }
        assertTrue("Must contain Underlay Tint", layerNames.contains("Underlay Tint"))
        assertTrue("Must contain Directional Hatching", layerNames.contains("Directional Hatching"))
        assertTrue("Must contain Paper Tooth Texture", layerNames.contains("Paper Tooth Texture"))
        assertTrue("Must contain Line Art (Preserved)", layerNames.contains("Line Art (Preserved)"))

        val hatchLayer = output.layers.first { it.name == "Directional Hatching" }
        val nonZeroCount = hatchLayer.pixels.count { (it ushr 24) > 0 }
        assertTrue("Hatching layer should have active strokes", nonZeroCount > 0)
    }

    @Test
    fun testColouredPencilParameterVariationSpacingAndAngle() {
        val inputFine = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = ColouredPencilConfig(strokeSpacingPx = 2.0f, hatchAngleDeg = 30.0f)
        )
        val outputFine = ColouredPencilStrategy.render(inputFine)

        val inputCoarse = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = ColouredPencilConfig(strokeSpacingPx = 8.0f, hatchAngleDeg = 90.0f)
        )
        val outputCoarse = ColouredPencilStrategy.render(inputCoarse)

        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (outputFine.finalComposite[i] != outputCoarse.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Altering stroke spacing and angle must change output: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // STYLE 8: MARKER TESTS
    // =========================================================================

    @Test
    fun testMarkerDefaultRenderProducesChiselBandsAndBleed() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = MarkerConfig(capillaryBleedPx = 1)
        )

        val output = MarkerStrategy.render(input)

        assertEquals(ColorStyleId.MARKER, output.styleId)
        val layerNames = output.layers.map { it.name }
        assertTrue("Must contain Base Marker Fills", layerNames.contains("Base Marker Fills"))
        assertTrue("Must contain Chisel Stroke Overlaps", layerNames.contains("Chisel Stroke Overlaps"))
        assertTrue("Must contain Capillary Edge Bleed", layerNames.contains("Capillary Edge Bleed"))
        assertTrue("Must contain Line Art (Preserved)", layerNames.contains("Line Art (Preserved)"))
    }

    @Test
    fun testMarkerParameterVariationBandWidthAndOverlap() {
        val inputNarrow = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = MarkerConfig(bandWidthPx = 6.0f, overlapDarkening = 0.15f)
        )
        val outputNarrow = MarkerStrategy.render(inputNarrow)

        val inputWide = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = MarkerConfig(bandWidthPx = 24.0f, overlapDarkening = 0.75f)
        )
        val outputWide = MarkerStrategy.render(inputWide)

        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (outputNarrow.finalComposite[i] != outputWide.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Altering bandWidthPx and overlapDarkening must change output: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // STYLE 9: MANGA SCREENTONE TESTS
    // =========================================================================

    @Test
    fun testMangaScreentoneDefaultRenderProducesHalftoneDots() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = MangaScreentoneConfig()
        )

        val output = MangaScreentoneStrategy.render(input)

        assertEquals(ColorStyleId.MANGA_SCREENTONE, output.styleId)
        val layerNames = output.layers.map { it.name }
        assertTrue("Must contain Tonal Base Flats", layerNames.contains("Tonal Base Flats"))
        assertTrue("Must contain Halftone Screentone Pattern", layerNames.contains("Halftone Screentone Pattern"))
        assertTrue("Must contain Line Art (Preserved)", layerNames.contains("Line Art (Preserved)"))

        val toneLayer = output.layers.first { it.name == "Halftone Screentone Pattern" }
        val dotCount = toneLayer.pixels.count { (it ushr 24) > 0 }
        assertTrue("Screentone pattern should have printed halftone dots", dotCount > 0)
    }

    @Test
    fun testMangaScreentoneParameterVariationPitchAndShape() {
        val inputDot = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = MangaScreentoneConfig(screenPitchPx = 4.0f, dotShape = ScreentoneDotShape.ROUND_DOT)
        )
        val outputDot = MangaScreentoneStrategy.render(inputDot)

        val inputLine = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = MangaScreentoneConfig(screenPitchPx = 10.0f, dotShape = ScreentoneDotShape.LINE_SCREEN)
        )
        val outputLine = MangaScreentoneStrategy.render(inputLine)

        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (outputDot.finalComposite[i] != outputLine.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Altering screentone pitch and dot shape must change output: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // STYLE 10: COMIC BOOK TESTS
    // =========================================================================

    @Test
    fun testComicBookDefaultRenderProducesBenDayAndInkShadows() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = ComicBookConfig()
        )

        val output = ComicBookStrategy.render(input)

        assertEquals(ColorStyleId.COMIC_BOOK, output.styleId)
        val layerNames = output.layers.map { it.name }
        assertTrue("Must contain Vivid Pop Base", layerNames.contains("Vivid Pop Base"))
        assertTrue("Must contain Ben-Day Halftone Dots", layerNames.contains("Ben-Day Halftone Dots"))
        assertTrue("Must contain Solid Ink Shadows", layerNames.contains("Solid Ink Shadows"))
        assertTrue("Must contain Line Art (Preserved)", layerNames.contains("Line Art (Preserved)"))
    }

    @Test
    fun testComicBookParameterVariationContrastAndShadowThreshold() {
        val inputMuted = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = ComicBookConfig(comicContrast = 0.05f, inkShadowThreshold = 0.20f)
        )
        val outputMuted = ComicBookStrategy.render(inputMuted)

        val inputDynamic = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = ComicBookConfig(comicContrast = 0.80f, inkShadowThreshold = 0.80f)
        )
        val outputDynamic = ComicBookStrategy.render(inputDynamic)

        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (outputMuted.finalComposite[i] != outputDynamic.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Altering comic contrast and ink shadow threshold must change output: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // STYLE 0: NO COLOUR BASELINE TESTS
    // =========================================================================

    @Test
    fun testNoColourStrategySkipsAutomaticFillsWithWhitePaper() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            paletteAnalysis = paletteAnalysis,
            linework = linework,
            config = NoColourConfig(paperBackground = true)
        )
        val output = NoColourStrategy.render(input)

        assertEquals(ColorStyleId.NO_COLOUR, output.styleId)
        assertEquals(2, output.layers.size) // Paper Canvas + Linework
        assertEquals("Paper Canvas (White)", output.layers[0].name)
        assertEquals(LayerRole.BASE_FLAT, output.layers[0].role)
        assertEquals("Line Art (Preserved)", output.layers[1].name)
        assertEquals(LayerRole.LINEWORK, output.layers[1].role)

        // Prove automatic fills are skipped: non-linework pixel in region_left must be pure white (0xFFFFFFFF), NOT region color (sky blue)
        val nonLwPixel = output.finalComposite[10 * width + 10]
        assertEquals(-0x1, nonLwPixel) // 0xFFFFFFFF

        // Linework pixel must be composite blended
        val lwIdx = 10 * width + (width / 2)
        assertNotEquals(-0x1, output.finalComposite[lwIdx])

        assertTrue(output.recipeSnapshot.isNoColour)
        assertTrue(output.recipeSnapshot.stages.isEmpty())
    }

    @Test
    fun testNoColourStrategySkipsAutomaticFillsTransparentCanvas() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            paletteAnalysis = paletteAnalysis,
            linework = linework,
            config = NoColourConfig(paperBackground = false)
        )
        val output = NoColourStrategy.render(input)

        assertEquals(1, output.layers.size) // Only Linework
        assertEquals("Line Art (Preserved)", output.layers[0].name)

        // Non-linework pixel must be completely transparent (0x00000000)
        val nonLwPixel = output.finalComposite[10 * width + 10]
        assertEquals(0, nonLwPixel)

        // Linework pixel must be present
        val lwIdx = 10 * width + (width / 2)
        assertTrue(((output.finalComposite[lwIdx] ushr 24) and 0xFF) > 0)
    }

    @Test
    fun testNoColourStrategyPreservesRegionMasksAndSourcePalette() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            paletteAnalysis = paletteAnalysis,
            linework = linework,
            config = NoColourConfig(lineArtOpacity = 0.60f)
        )
        val output = NoColourStrategy.render(input)

        // Verify source palette and regions remain accessible in input
        assertEquals(2, input.regionMasks.size)
        assertEquals(2, input.regionColors.size)
        assertNotNull(input.paletteAnalysis)
        assertEquals(2, input.paletteAnalysis?.globalPalette?.size)

        // Verify linework layer opacity modulation
        val lwLayer = output.layers.first { it.role == LayerRole.LINEWORK }
        assertEquals(0.9f * 0.60f, lwLayer.opacity, 0.01f)
    }

    // =========================================================================
    // STYLE 11: OIL PAINT TESTS
    // =========================================================================

    @Test
    fun testOilPaintStrategyImpastoAndBristleLayers() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = OilPaintConfig(impastoDepth = 0.50f, bristleDensity = 0.60f)
        )
        val output = OilPaintStrategy.render(input)

        assertEquals(ColorStyleId.OIL_PAINT, output.styleId)
        assertTrue(output.layers.size in 3..4)
        assertTrue(output.layers.any { it.role == LayerRole.BASE_FLAT })
        assertTrue(output.layers.any { it.role == LayerRole.TEXTURE })
        assertTrue(output.layers.any { it.role == LayerRole.SHADOW })
        assertTrue(output.layers.any { it.role == LayerRole.LINEWORK })

        // Capability description must explicitly distinguish simulated medium from true physical simulation
        assertTrue(OilPaintStrategy.description.contains("simulated medium", ignoreCase = true))
    }

    @Test
    fun testOilPaintStrategyImpastoDepthParameterSensitivity() {
        val inputFlat = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = OilPaintConfig(impastoDepth = 0.0f)
        )
        val outputFlat = OilPaintStrategy.render(inputFlat)

        val inputDeep = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = OilPaintConfig(impastoDepth = 0.85f, bristleDensity = 0.90f)
        )
        val outputDeep = OilPaintStrategy.render(inputDeep)

        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (outputFlat.finalComposite[i] != outputDeep.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Impasto depth variation must produce significant difference: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // STYLE 12: GOUACHE TESTS
    // =========================================================================

    @Test
    fun testGouacheStrategyPlanarSteppingAndMatteFinish() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = GouacheConfig(planarStepCount = 4, chalkyFinish = 0.25f)
        )
        val output = GouacheStrategy.render(input)

        assertEquals(ColorStyleId.GOUACHE, output.styleId)
        assertTrue(output.layers.any { it.name.contains("Planar", ignoreCase = true) })
        assertTrue(output.layers.any { it.role == LayerRole.BASE_FLAT })
        assertTrue(output.layers.any { it.role == LayerRole.LINEWORK })

        assertTrue(GouacheStrategy.description.contains("simulated medium", ignoreCase = true))
    }

    @Test
    fun testGouacheStrategyPlanarStepCountParameterSensitivity() {
        val inputLow = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = GouacheConfig(planarStepCount = 2, edgeDryDarkening = 0.1f)
        )
        val outputLow = GouacheStrategy.render(inputLow)

        val inputHigh = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = GouacheConfig(planarStepCount = 7, edgeDryDarkening = 0.8f)
        )
        val outputHigh = GouacheStrategy.render(inputHigh)

        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (outputLow.finalComposite[i] != outputHigh.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Gouache planar step and edge darkening change must affect output: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // STYLE 13: PASTEL PALETTE TESTS
    // =========================================================================

    @Test
    fun testPastelStrategyChalkDustAndPaperTooth() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = PastelConfig(chalkDustStrength = 0.50f, paperToothDepth = 0.60f)
        )
        val output = PastelStrategy.render(input)

        assertEquals(ColorStyleId.PASTEL, output.styleId)
        assertTrue(output.layers.any { it.role == LayerRole.BASE_FLAT })
        assertTrue(output.layers.any { it.name.contains("Tooth", ignoreCase = true) })
        assertTrue(output.layers.any { it.role == LayerRole.LINEWORK })

        assertTrue(PastelStrategy.description.contains("simulated medium", ignoreCase = true))
    }

    @Test
    fun testPastelStrategyTintLiftAndToothDepthSensitivity() {
        val inputLow = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = PastelConfig(pastelTintLift = 0.10f, paperToothDepth = 0.20f)
        )
        val outputLow = PastelStrategy.render(inputLow)

        val inputHigh = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = PastelConfig(pastelTintLift = 0.60f, paperToothDepth = 0.80f)
        )
        val outputHigh = PastelStrategy.render(inputHigh)

        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (outputLow.finalComposite[i] != outputHigh.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Pastel tint lift and tooth depth change must affect output: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // STYLE 14: NEON / CYBERPUNK TESTS
    // =========================================================================

    @Test
    fun testNeonCyberpunkStrategyElectricRimsAndOpticalBloom() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            linework = linework,
            config = NeonCyberpunkConfig(glowRadiusPx = 4, bloomIntensity = 0.70f)
        )
        val output = NeonCyberpunkStrategy.render(input)

        assertEquals(ColorStyleId.NEON_CYBERPUNK, output.styleId)
        assertTrue(output.layers.any { it.name.contains("Noir", ignoreCase = true) })
        assertTrue(output.layers.any { it.name.contains("Electric", ignoreCase = true) })
        assertTrue(output.layers.any { it.name.contains("Bloom", ignoreCase = true) })

        assertTrue(NeonCyberpunkStrategy.description.contains("simulated optical glow", ignoreCase = true))
    }

    @Test
    fun testNeonCyberpunkStrategyNumericalSaturationAndBloomSensitivity() {
        val inputMuted = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = NeonCyberpunkConfig(bloomIntensity = 0.10f, neonSaturationBoost = 0.0f)
        )
        val outputMuted = NeonCyberpunkStrategy.render(inputMuted)

        val inputElectric = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = NeonCyberpunkConfig(bloomIntensity = 0.90f, neonSaturationBoost = 0.90f)
        )
        val outputElectric = NeonCyberpunkStrategy.render(inputElectric)

        // Numerical verification: electric output must have higher pixel values on blooming edges
        var diffCount = 0
        var higherValueCount = 0
        for (i in 0 until totalPixels) {
            val pMuted = outputMuted.finalComposite[i]
            val pElec = outputElectric.finalComposite[i]
            if (pMuted != pElec) {
                diffCount++
                val valMuted = ((pMuted ushr 16) and 0xFF) + ((pMuted ushr 8) and 0xFF) + (pMuted and 0xFF)
                val valElec = ((pElec ushr 16) and 0xFF) + ((pElec ushr 8) and 0xFF) + (pElec and 0xFF)
                if (valElec >= valMuted) higherValueCount++
            }
        }
        assertTrue("Neon bloom must alter output significantly: diffCount=$diffCount", diffCount > 100)
        assertTrue("Electric bloom must boost luminance on affected pixels", higherValueCount > diffCount / 2)
    }

    // =========================================================================
    // STYLE 15: MONOCHROME TESTS
    // =========================================================================

    @Test
    fun testMonochromeStrategyPureGrayscaleNumericalEquivalence() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            paletteAnalysis = paletteAnalysis,
            config = MonochromeConfig(tintMode = MonochromeTintMode.PURE_GRAYSCALE)
        )
        val output = MonochromeStrategy.render(input)

        assertEquals(ColorStyleId.MONOCHROME, output.styleId)

        // Numerical validation: in PURE_GRAYSCALE mode, every rendered base pixel MUST satisfy R == G == B exactly!
        val baseLayer = output.layers.first { it.role == LayerRole.BASE_FLAT }
        var validPixels = 0
        for (i in 0 until totalPixels) {
            val px = baseLayer.pixels[i]
            val a = (px ushr 24) and 0xFF
            if (a > 12) {
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                assertEquals("Red and Green must match in pure grayscale", r, g)
                assertEquals("Green and Blue must match in pure grayscale", g, b)
                validPixels++
            }
        }
        assertTrue("Must have verified valid grayscale pixels", validPixels > 500)

        // Retain original palette data: input palette analysis and region colors must remain completely intact
        assertNotNull(input.paletteAnalysis)
        assertEquals(2, input.regionColors.size)
        assertEquals(0.2f, input.regionColors["region_left"]?.red ?: 0f, 0.01f)
    }

    @Test
    fun testMonochromeStrategyTintModesAndContrastSensitivity() {
        val inputGray = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = MonochromeConfig(tintMode = MonochromeTintMode.PURE_GRAYSCALE, contrast = 1.0f)
        )
        val outputGray = MonochromeStrategy.render(inputGray)

        val inputSepia = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = MonochromeConfig(tintMode = MonochromeTintMode.WARM_SEPIA, contrast = 1.8f)
        )
        val outputSepia = MonochromeStrategy.render(inputSepia)

        var diffCount = 0
        for (i in 0 until totalPixels) {
            if (outputGray.finalComposite[i] != outputSepia.finalComposite[i]) {
                diffCount++
            }
        }
        assertTrue("Sepia tint and contrast variation must produce visible difference: diffCount=$diffCount", diffCount > 100)
    }

    // =========================================================================
    // REGISTRY & INTEGRATION TESTS
    // =========================================================================

    @Test
    fun testStyleStrategyRegistryDiscoveryAndValidation() {
        val supportedStyles = listOf(
            ColorStyleId.NO_COLOUR,
            ColorStyleId.CLASSIC_CEL,
            ColorStyleId.SOFT_GRADIENT,
            ColorStyleId.HYBRID_CEL_SOFT,
            ColorStyleId.FLAT_ILLUSTRATION,
            ColorStyleId.PAINTERLY,
            ColorStyleId.WATERCOLOUR,
            ColorStyleId.COLOURED_PENCIL,
            ColorStyleId.MARKER,
            ColorStyleId.MANGA_SCREENTONE,
            ColorStyleId.COMIC_BOOK,
            ColorStyleId.OIL_PAINT,
            ColorStyleId.GOUACHE,
            ColorStyleId.PASTEL,
            ColorStyleId.NEON_CYBERPUNK,
            ColorStyleId.MONOCHROME
        )

        for (id in supportedStyles) {
            assertTrue("Registry must report support for $id", StyleStrategyRegistry.isSupported(id))
            val strategy = StyleStrategyRegistry.get(id)
            assertNotNull("Strategy for $id must not be null", strategy)
            assertEquals("Strategy must have correct styleId", id, strategy!!.styleId)
            assertTrue("Strategy must document supported input modes", strategy.supportedInputModes.isNotEmpty())
            assertTrue("Strategy must document textured/photo input limitations", strategy.documentedLimitations.isNotEmpty())
        }

        assertEquals("Registry must list all 16 supported strategies", 16, StyleStrategyRegistry.listSupportedStrategies().size)
    }

    @Test
    fun testToLayerGroupBakesEditableDocumentLayers() {
        val input = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMasks,
            regionColors = regionColors,
            config = ClassicCelConfig()
        )
        val output = ClassicCelStrategy.render(input)

        val doc = ArtDocument(title = "Test Doc", canvas = CanvasConfig(width = width, height = height))
        val layerGroup = output.toLayerGroup(doc, "Rendered Group")

        assertEquals("Rendered Group", layerGroup.name)
        assertEquals(output.layers.size, layerGroup.children.size)
        assertEquals("Base Flats", layerGroup.children[0].name)
    }

    private fun createBoxMask(x0: Int, y0: Int, x1: Int, y1: Int): ByteArray {
        val mask = ByteArray(totalPixels)
        for (y in y0..y1) {
            val row = y * width
            for (x in x0..x1) {
                mask[row + x] = 255.toByte()
            }
        }
        return mask
    }
}
