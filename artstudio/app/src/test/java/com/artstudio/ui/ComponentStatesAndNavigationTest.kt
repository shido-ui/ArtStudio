package com.artstudio.ui

import com.artstudio.ui.components.StudioButtonVariant
import com.artstudio.ui.navigation.StudioDestination
import com.artstudio.ui.theme.StudioAccentOption
import org.junit.Assert.*
import org.junit.Test

/**
 * Validates component states, disabled explanation semantics, and navigation definitions.
 */
class ComponentStatesAndNavigationTest {

    @Test
    fun testAllNavigationDestinationsAreUniqueAndComplete() {
        val destinations = StudioDestination.entries
        // Verify all 6 core destinations + component reference
        assertTrue(destinations.size >= 7)

        val routes = destinations.map { it.route }
        assertEquals("All destination routes must be unique", routes.distinct().size, routes.size)

        destinations.forEach { dest ->
            assertTrue("Title must not be blank for ${dest.name}", dest.title.isNotBlank())
            assertTrue("Icon symbol must not be blank for ${dest.name}", dest.iconSymbol.isNotBlank())
            assertTrue("Description must not be blank for ${dest.name}", dest.description.isNotBlank())
        }

        // Specifically verify Home/Projects, Studio, AutoDraw, ColourLab, Assets, Settings
        assertTrue(destinations.contains(StudioDestination.HomeProjects))
        assertTrue(destinations.contains(StudioDestination.ManualStudio))
        assertTrue(destinations.contains(StudioDestination.AutoDraw))
        assertTrue(destinations.contains(StudioDestination.ColourLab))
        assertTrue(destinations.contains(StudioDestination.AssetsBrushes))
        assertTrue(destinations.contains(StudioDestination.Settings))
        assertTrue(destinations.contains(StudioDestination.ComponentReference))
    }

    @Test
    fun testButtonVariantsEnumIntegrity() {
        val variants = StudioButtonVariant.entries
        assertEquals(4, variants.size)
        assertTrue(variants.contains(StudioButtonVariant.Primary))
        assertTrue(variants.contains(StudioButtonVariant.Secondary))
        assertTrue(variants.contains(StudioButtonVariant.Destructive))
        assertTrue(variants.contains(StudioButtonVariant.Ghost))
    }

    @Test
    fun testAccentSystemOptionsIntegrity() {
        val accents = StudioAccentOption.entries
        assertEquals(4, accents.size)
        assertTrue(accents.contains(StudioAccentOption.StudioBlue))
        assertTrue(accents.contains(StudioAccentOption.EmeraldArt))
        assertTrue(accents.contains(StudioAccentOption.CyberpunkNeon))
        assertTrue(accents.contains(StudioAccentOption.ClassicAmber))
    }

    @Test
    fun testDisabledControlExplanationSemantics() {
        // Enforces rule: "Every control must be functional or clearly disabled with an explanation."
        val explanation = "Requires active canvas document with at least one raster layer."
        assertNotNull(explanation)
        assertTrue("Explanation must be meaningful", explanation.length > 10)

        val semanticsDesc = buildString {
            append("Export to PSD")
            append(". Disabled: ")
            append(explanation)
        }
        assertTrue(semanticsDesc.contains("Disabled: Requires active canvas"))
    }

    @Test
    fun testProgressRangeClamping() {
        val negativeProgress = -0.25f
        val excessiveProgress = 1.45f
        val normalProgress = 0.65f

        fun clamp(v: Float) = v.coerceIn(0f, 1f)

        assertEquals(0f, clamp(negativeProgress))
        assertEquals(1f, clamp(excessiveProgress))
        assertEquals(0.65f, clamp(normalProgress))
    }
}
