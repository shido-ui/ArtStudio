package com.artstudio.colorstack

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite verifying Colour Stack recipe resolution, style combinations,
 * conflict detection, No Colour bypass, and Monochrome+Neon ordered semantics.
 */
class ColorStackResolverTest {

    @Test
    fun testResolveValidStandardRecipe() {
        val recipe = ColorStackPresets.classicCel()
        val result = ColorStackResolver.resolve(recipe)

        assertTrue("Standard Cel recipe must be valid", result.isValid)
        assertEquals(3, result.activeStages.size)
        assertTrue(result.bypassedStages.isEmpty())
        assertFalse(result.hasWarnings)
        assertFalse(result.hasErrors)
    }

    @Test
    fun testResolveNoColourSkipsAllFillStages() {
        // Recipe with fill stages but marked as No Colour
        val recipe = ColorStackPresets.classicCel().copy(isNoColour = true)
        val result = ColorStackResolver.resolve(recipe)

        assertTrue(result.isValid)
        assertTrue("No Colour must have 0 active colour stages", result.activeStages.isEmpty())
        assertEquals(3, result.bypassedStages.size)
        assertTrue(result.isNoColourActive)

        val bypassMsg = result.messages.firstOrNull { it.code == "NO_COLOUR_BYPASS" }
        assertNotNull("Must emit NO_COLOUR_BYPASS info message", bypassMsg)
        assertEquals(MessageSeverity.INFO, bypassMsg?.severity)
    }

    @Test
    fun testResolveMonochromeThenNeonOrderedSemantics() {
        // Canonical Noir/Cyberpunk: Monochrome first, then Neon Bloom
        val recipe = ColorStackPresets.cyberpunkNoir()
        val result = ColorStackResolver.resolve(recipe)

        assertTrue(result.isValid)
        assertTrue(result.hasMonochromeNeonSemantic)
        assertFalse("Monochrome -> Neon should not raise warnings", result.hasWarnings)

        val semanticMsg = result.messages.firstOrNull { it.code == "MONOCHROME_THEN_NEON_ORDERED" }
        assertNotNull("Must emit MONOCHROME_THEN_NEON_ORDERED info message", semanticMsg)
        assertEquals(MessageSeverity.INFO, semanticMsg?.severity)
    }

    @Test
    fun testResolveNeonThenMonochromeConflictWarning() {
        // Invalid order: Neon bloom first, then Monochrome desaturation on top
        val recipe = ColorStackRecipe(
            name = "Inverted Neon then Monochrome",
            stages = listOf(
                BaseFillStageConfig(),
                FinishingEffectStageConfig(effectType = FinishingEffectStageConfig.FinishingEffectType.NEON_BLOOM),
                PaletteTransformStageConfig(transformType = PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE)
            )
        )
        val result = ColorStackResolver.resolve(recipe)

        assertTrue(result.isValid)
        assertTrue(result.hasMonochromeNeonSemantic)
        assertTrue("Neon before Monochrome must raise warning", result.hasWarnings)

        val conflictMsg = result.messages.firstOrNull { it.code == "NEON_THEN_MONOCHROME_CONFLICT" }
        assertNotNull("Must emit NEON_THEN_MONOCHROME_CONFLICT warning", conflictMsg)
        assertEquals(MessageSeverity.WARNING, conflictMsg?.severity)
        assertTrue(conflictMsg?.suggestedFix?.contains("Monochrome", ignoreCase = true) == true)
    }

    @Test
    fun testResolveMissingBaseFillForShading() {
        // Cel shadow alone without base fill
        val recipe = ColorStackRecipe(
            name = "Shading Only",
            stages = listOf(
                CelShadowStageConfig()
            )
        )
        val result = ColorStackResolver.resolve(recipe)

        assertTrue(result.hasWarnings)
        val missingMsg = result.messages.firstOrNull { it.code == "MISSING_BASE_FILL" }
        assertNotNull("Must emit MISSING_BASE_FILL warning", missingMsg)
        assertEquals(MessageSeverity.WARNING, missingMsg?.severity)
    }

    @Test
    fun testResolveDuplicateBaseFillConflict() {
        // Multiple base fills with no region restrictions
        val recipe = ColorStackRecipe(
            name = "Duplicate Base",
            stages = listOf(
                BaseFillStageConfig(name = "Base 1"),
                BaseFillStageConfig(name = "Base 2")
            )
        )
        val result = ColorStackResolver.resolve(recipe)

        assertTrue(result.hasWarnings)
        val dupMsg = result.messages.firstOrNull { it.code == "DUPLICATE_BASE_FILL" }
        assertNotNull("Must emit DUPLICATE_BASE_FILL warning", dupMsg)
    }

    @Test
    fun testResolveConflictingMediumTextures() {
        // Manga Screentone + Oil Impasto
        val recipe = ColorStackRecipe(
            name = "Conflicting Media",
            stages = listOf(
                BaseFillStageConfig(),
                MediumTextureStageConfig(mediumType = MediumTextureStageConfig.MediumTextureType.SCREENTONE_DOTS),
                MediumTextureStageConfig(mediumType = MediumTextureStageConfig.MediumTextureType.OIL_IMPASTO)
            )
        )
        val result = ColorStackResolver.resolve(recipe)

        assertTrue(result.hasWarnings)
        val clashMsg = result.messages.firstOrNull { it.code == "CONFLICTING_MEDIUM_TEXTURES" }
        assertNotNull("Must emit CONFLICTING_MEDIUM_TEXTURES warning", clashMsg)
    }

    @Test
    fun testResolveEmptyRecipeHandledGracefully() {
        val emptyRecipe = ColorStackRecipe(name = "Empty", stages = emptyList())
        val result = ColorStackResolver.resolve(emptyRecipe)

        assertTrue(result.isValid)
        assertTrue(result.activeStages.isEmpty())
        val emptyMsg = result.messages.firstOrNull { it.code == "EMPTY_RECIPE" }
        assertNotNull("Must emit EMPTY_RECIPE info message", emptyMsg)
    }

    @Test
    fun testResolveDisabledStagesFilteredCorrectly() {
        val recipe = ColorStackRecipe(
            name = "Partially Disabled",
            stages = listOf(
                BaseFillStageConfig(enabled = true),
                CelShadowStageConfig(enabled = false),
                HighlightStageConfig(enabled = true)
            )
        )
        val result = ColorStackResolver.resolve(recipe)

        assertEquals(2, result.activeStages.size)
        assertEquals(1, result.bypassedStages.size)
        assertEquals(ColorStageType.CEL_SHADOW, result.bypassedStages[0].stageType)
    }

    @Test
    fun testResolveStylePresetsMapping() {
        val allPresets = ColorStackPresets.allPresets()
        assertTrue("Must include presets for all styles", allPresets.size >= 16)

        for (preset in allPresets) {
            val result = ColorStackResolver.resolve(preset)
            assertTrue("Preset ${preset.name} must resolve cleanly as valid", result.isValid)
            assertFalse("Preset ${preset.name} must not contain errors", result.hasErrors)
        }
    }

    @Test
    fun testAutoReorderToCanonical() {
        // Disordered stages: FinishingEffect -> Highlight -> CelShadow -> BaseFill
        val disordered = listOf(
            FinishingEffectStageConfig(),
            HighlightStageConfig(),
            CelShadowStageConfig(),
            BaseFillStageConfig()
        )
        val reordered = ColorStackResolver.autoReorderToCanonical(disordered)

        assertEquals(4, reordered.size)
        assertEquals(ColorStageType.BASE_FILL, reordered[0].stageType)
        assertEquals(ColorStageType.CEL_SHADOW, reordered[1].stageType)
        assertEquals(ColorStageType.HIGHLIGHT, reordered[2].stageType)
        assertEquals(ColorStageType.FINISHING_EFFECT, reordered[3].stageType)
    }
}
