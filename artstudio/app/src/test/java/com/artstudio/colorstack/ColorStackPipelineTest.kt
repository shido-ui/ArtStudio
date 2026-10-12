package com.artstudio.colorstack

import com.artstudio.color.ColorData
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.BlendModeType
import com.artstudio.document.model.CanvasConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying Colour Stack execution, ordering, disabled stages,
 * opacity modulation, mask bounding, linework preservation, recipe serialization,
 * preview vs final quality, and incremental dirty-stage dependency tracking.
 */
class ColorStackPipelineTest {

    private val width = 64
    private val height = 64
    private val totalPixels = width * height

    private lateinit var regionMasks: Map<String, ByteArray>
    private lateinit var regionColors: Map<String, ColorData>
    private lateinit var linework: LineworkData

    @Before
    fun setUp() {
        com.artstudio.core.FeatureFlags.isColorStackEnabled = true
        // Create 2 test regions: Left half (r1) and Right half (r2)
        val mask1 = ByteArray(totalPixels)
        val mask2 = ByteArray(totalPixels)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (x < width / 2) {
                    mask1[row + x] = 255.toByte()
                } else {
                    mask2[row + x] = 255.toByte()
                }
            }
        }
        regionMasks = mapOf("reg_left" to mask1, "reg_right" to mask2)

        // Blue on left, Red on right
        regionColors = mapOf(
            "reg_left" to ColorData(0.0f, 0.4f, 1.0f, 1.0f),
            "reg_right" to ColorData(1.0f, 0.2f, 0.2f, 1.0f)
        )

        // Linework: 1-pixel black dividing line down the middle
        val lwPixels = IntArray(totalPixels)
        val midX = width / 2
        for (y in 0 until height) {
            lwPixels[y * width + midX] = (0xFF shl 24) or (0x05 shl 16) or (0x05 shl 8) or 0x05
        }
        linework = LineworkData(width, height, lwPixels, BlendModeType.Multiply, 1.0f)
    }

    @Test
    fun testPipelineOrderingExecutesDeterministically() {
        val recipe = ColorStackPresets.classicCel()
        val result = ColorStackPipeline.execute(recipe, linework, regionMasks, regionColors, width, height)

        assertEquals(3, result.stageOutputs.size)
        assertEquals(ColorStageType.BASE_FILL, result.stageOutputs[0].stageType)
        assertEquals(ColorStageType.CEL_SHADOW, result.stageOutputs[1].stageType)
        assertEquals(ColorStageType.HIGHLIGHT, result.stageOutputs[2].stageType)

        assertNotNull("Linework output must be preserved", result.lineworkOutput)
        assertEquals(totalPixels, result.finalCompositePixels.size)
    }

    @Test
    fun testPipelineDisabledStagesSkipped() {
        // Disable Cel Shadow stage
        val recipe = ColorStackRecipe(
            name = "Disabled Shadow",
            stages = listOf(
                BaseFillStageConfig(enabled = true),
                CelShadowStageConfig(enabled = false),
                HighlightStageConfig(enabled = true)
            )
        )
        val result = ColorStackPipeline.execute(recipe, linework, regionMasks, regionColors, width, height)

        assertEquals(2, result.stageOutputs.size)
        assertEquals(ColorStageType.BASE_FILL, result.stageOutputs[0].stageType)
        assertEquals(ColorStageType.HIGHLIGHT, result.stageOutputs[1].stageType)
        assertTrue(result.stageOutputs.none { it.stageType == ColorStageType.CEL_SHADOW })
    }

    @Test
    fun testPipelineStageOpacityModulation() {
        val fullRecipe = ColorStackRecipe(
            name = "Full Opacity",
            stages = listOf(BaseFillStageConfig(opacity = 1.0f))
        )
        val halfRecipe = ColorStackRecipe(
            name = "Half Opacity",
            stages = listOf(BaseFillStageConfig(opacity = 0.5f))
        )

        val fullResult = ColorStackPipeline.execute(fullRecipe, null, regionMasks, regionColors, width, height)
        val halfResult = ColorStackPipeline.execute(halfRecipe, null, regionMasks, regionColors, width, height)

        val fullPixel = fullResult.stageOutputs[0].stagePixels[10 * width + 10]
        val halfPixel = halfResult.stageOutputs[0].stagePixels[10 * width + 10]

        val fullAlpha = (fullPixel ushr 24) and 0xFF
        val halfAlpha = (halfPixel ushr 24) and 0xFF

        assertTrue("Full opacity alpha must be ~255", fullAlpha > 250)
        assertTrue("Half opacity alpha must be roughly half (~127)", halfAlpha in 120..135)
    }

    @Test
    fun testPipelineStageStrengthModulation() {
        val recipeLow = ColorStackRecipe(
            name = "Low Strength",
            stages = listOf(BaseFillStageConfig(strength = 0.2f))
        )
        val recipeHigh = ColorStackRecipe(
            name = "High Strength",
            stages = listOf(BaseFillStageConfig(strength = 1.0f))
        )

        val lowResult = ColorStackPipeline.execute(recipeLow, null, regionMasks, regionColors, width, height)
        val highResult = ColorStackPipeline.execute(recipeHigh, null, regionMasks, regionColors, width, height)

        val lowAlpha = (lowResult.stageOutputs[0].stagePixels[5 * width + 5] ushr 24) and 0xFF
        val highAlpha = (highResult.stageOutputs[0].stagePixels[5 * width + 5] ushr 24) and 0xFF

        assertTrue("Higher strength must yield higher effective alpha", highAlpha > lowAlpha)
    }

    @Test
    fun testPipelineBlendModesApplication() {
        val recipe = ColorStackRecipe(
            name = "Blend Modes",
            stages = listOf(
                BaseFillStageConfig(blendMode = BlendModeType.Normal),
                CelShadowStageConfig(blendMode = BlendModeType.Multiply, shadowDarkness = 0.5f),
                HighlightStageConfig(blendMode = BlendModeType.Screen, intensity = 0.8f)
            )
        )
        val result = ColorStackPipeline.execute(recipe, null, regionMasks, regionColors, width, height)

        val baseBackdrop = result.stageOutputs[0].compositedBackdrop
        val shadowBackdrop = result.stageOutputs[1].compositedBackdrop

        // Test that multiply shadow darkens the luminance
        val testIdx = 15 * width + 15
        val baseR = (baseBackdrop[testIdx] ushr 16) and 0xFF
        val shadowR = (shadowBackdrop[testIdx] ushr 16) and 0xFF
        assertTrue("Shadow backdrop must be less than or equal to base in luminance/channels", shadowR <= baseR)
    }

    @Test
    fun testPipelineRegionMaskContainment() {
        // Target ONLY reg_left for BaseFill
        val recipe = ColorStackRecipe(
            name = "Left Only",
            stages = listOf(
                BaseFillStageConfig(targetRegionIds = setOf("reg_left"))
            )
        )
        val result = ColorStackPipeline.execute(recipe, null, regionMasks, regionColors, width, height)

        val stagePixels = result.stageOutputs[0].stagePixels
        // Pixel in left half (x=10) should have color
        val leftA = (stagePixels[20 * width + 10] ushr 24) and 0xFF
        assertTrue("Left region must be filled", leftA > 0)

        // Pixel in right half (x=50) must be 0 (untouched)
        val rightA = (stagePixels[20 * width + 50] ushr 24) and 0xFF
        assertEquals("Right region outside mask must remain 0", 0, rightA)
    }

    @Test
    fun testPipelineLineworkPreservation() {
        val recipe = ColorStackPresets.classicCel()
        val result = ColorStackPipeline.execute(recipe, linework, regionMasks, regionColors, width, height)

        assertNotNull("Linework output must exist", result.lineworkOutput)
        val lwStage = result.lineworkOutput!!

        // Verify linework pixels are 100% bit-exact preserved
        val midX = width / 2
        for (y in 0 until height) {
            val idx = y * width + midX
            assertEquals("Linework pixel must be preserved without mutation", linework.pixels[idx], lwStage.stagePixels[idx])
        }
    }

    @Test
    fun testPipelineRecipeJsonSerializationRoundTrip() {
        val original = ColorStackPresets.classicCel()
        val json = ColorStackRecipeSerializer.toJson(original)
        val restored = ColorStackRecipeSerializer.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.styleId, restored.styleId)
        assertEquals(original.stages.size, restored.stages.size)
        assertEquals(original.seed, restored.seed)

        for (i in original.stages.indices) {
            assertEquals(original.stages[i].stageType, restored.stages[i].stageType)
            assertEquals(original.stages[i].name, restored.stages[i].name)
            assertEquals(original.stages[i].opacity, restored.stages[i].opacity, 0.001f)
        }
    }

    @Test
    fun testPipelinePresetSaveAndLoadRoundTrip() {
        val custom = ColorStackRecipe(
            id = "preset_test_custom",
            name = "Test Custom",
            description = "Custom test recipe",
            styleId = ColorStyleId.CUSTOM,
            stages = listOf(
                BaseFillStageConfig(name = "Flats", fillType = BaseFillStageConfig.BaseFillType.PAPER_TONE),
                MediumTextureStageConfig(mediumType = MediumTextureStageConfig.MediumTextureType.CANVAS_TOOTH)
            ),
            seed = 12345L
        )
        val json = ColorStackRecipeSerializer.toJson(custom)
        val loaded = ColorStackRecipeSerializer.fromJson(json)

        assertEquals("preset_test_custom", loaded.id)
        assertEquals(12345L, loaded.seed)
        assertEquals(2, loaded.stages.size)
        assertEquals(MediumTextureStageConfig.MediumTextureType.CANVAS_TOOTH, (loaded.stages[1] as MediumTextureStageConfig).mediumType)
    }

    @Test
    fun testPipelineSeededReproducibility() {
        val recipe1 = ColorStackPresets.mangaScreentone(seed = 9999L)
        val recipe2 = ColorStackPresets.mangaScreentone(seed = 9999L)

        val result1 = ColorStackPipeline.execute(recipe1, null, regionMasks, regionColors, width, height)
        val result2 = ColorStackPipeline.execute(recipe2, null, regionMasks, regionColors, width, height)

        assertArrayEquals("Same seed must produce bit-exact identical final pixels",
            result1.finalCompositePixels, result2.finalCompositePixels)
    }

    @Test
    fun testPipelinePreviewReducedResolutionRendering() {
        val recipe = ColorStackPresets.classicCel()
        // Run with PREVIEW quality
        val result = ColorStackPipeline.execute(
            recipe, linework, regionMasks, regionColors, width, height,
            quality = RenderQuality.PREVIEW
        )

        assertTrue(result.quality.isReducedPreview)
        assertTrue("Preview execution should be fast (< 200ms)", result.totalExecutionTimeMs < 200)
        assertTrue(result.peakMemoryBytes > 0)
    }

    @Test
    fun testPipelineFinalResolutionRendering() {
        val recipe = ColorStackPresets.classicCel()
        val result = ColorStackPipeline.execute(
            recipe, linework, regionMasks, regionColors, width, height,
            quality = RenderQuality.FINAL
        )

        assertFalse(result.quality.isReducedPreview)
        assertEquals(width, result.width)
        assertEquals(height, result.height)
        assertEquals(totalPixels, result.finalCompositePixels.size)
    }

    @Test
    fun testPipelineStageDependencyDirtyTracking() {
        val recipe = ColorStackPresets.classicCel()
        val initialResult = ColorStackPipeline.execute(recipe, linework, regionMasks, regionColors, width, height)

        val cachedMap = initialResult.stageOutputs.associateBy { it.stageId }

        // Now modify stage 3 (Highlight) - change intensity
        val highlightStage = recipe.stages[2] as HighlightStageConfig
        val modifiedHighlight = highlightStage.copy(intensity = 0.20f)
        val updatedRecipe = recipe.withStage(modifiedHighlight)

        val secondResult = ColorStackPipeline.execute(
            updatedRecipe, linework, regionMasks, regionColors, width, height,
            cachedOutputs = cachedMap
        )

        // Stage 1 (BaseFill) and Stage 2 (CelShadow) must be reused from cache!
        assertSame("Base fill output must be reused from cache",
            initialResult.stageOutputs[0], secondResult.stageOutputs[0])
        assertSame("Cel shadow output must be reused from cache",
            initialResult.stageOutputs[1], secondResult.stageOutputs[1])

        // Stage 3 must be newly executed
        assertNotSame("Highlight stage must be newly executed",
            initialResult.stageOutputs[2], secondResult.stageOutputs[2])
    }

    @Test
    fun testPipelineNoColourRendersPureLinework() {
        val noColourRecipe = ColorStackPresets.noColour()
        val result = ColorStackPipeline.execute(noColourRecipe, linework, regionMasks, regionColors, width, height)

        assertTrue("Result must indicate No Colour", result.isNoColour)
        assertTrue("No color stages should be output", result.stageOutputs.isEmpty())
        assertNotNull("Linework must be present", result.lineworkOutput)

        // The only non-zero pixels in final composite must be the linework!
        val midX = width / 2
        for (y in 0 until height) {
            val idx = y * width + midX
            assertEquals("Linework must be preserved in final composite", linework.pixels[idx], result.finalCompositePixels[idx])
        }
    }

    @Test
    fun testPipelineLayerGroupExport() {
        val recipe = ColorStackPresets.classicCel()
        val result = ColorStackPipeline.execute(recipe, linework, regionMasks, regionColors, width, height)

        val doc = ArtDocument(canvas = CanvasConfig(width = width, height = height))
        val group = ColorStackPipeline.toLayerGroup(doc, result, "Exported Colour Stack")

        assertEquals("Exported Colour Stack", group.name)
        // 3 colour stages + 1 linework layer = 4 child layers
        assertEquals(4, group.children.size)
        assertEquals("Base Fills", group.children[0].name)
        assertEquals("Cel Shadows", group.children[1].name)
        assertEquals("Specular Highlights", group.children[2].name)
        assertEquals("Line Art (Preserved)", group.children[3].name)
    }

    @org.junit.After
    fun tearDown() {
        com.artstudio.core.FeatureFlags.isColorStackEnabled = true
    }
}
