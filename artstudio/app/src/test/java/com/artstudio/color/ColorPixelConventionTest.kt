package com.artstudio.color

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Gate test for ColorPixelConvention:
 * - Straight <-> Premultiplied exact and bounded round-trips
 * - Mathematical proof of zero dark edge-halo on transparent boundaries
 * - sRGB color space tagging
 */
class ColorPixelConventionTest {

    @Test
    fun testFullyOpaque_roundTripIsIdentical() {
        val testColors = intArrayOf(
            0xFFFFFFFF.toInt(), // White
            0xFFFF0000.toInt(), // Red
            0xFF00FF00.toInt(), // Green
            0xFF0000FF.toInt(), // Blue
            0xFFFF8000.toInt(), // Orange
            0xFF4A90E2.toInt(), // Studio Blue
            0xFF123456.toInt()  // Custom dark
        )

        for (c in testColors) {
            val premul = ColorPixelConvention.straightToPremultiplied(c)
            val recovered = ColorPixelConvention.premultipliedToStraight(premul)
            assertEquals("Opaque pixel 0x${Integer.toHexString(c)} must round-trip with zero loss", c, recovered)
        }
    }

    @Test
    fun testFullyTransparent_mapsToZero() {
        val transparentRed = (0 shl 24) or (255 shl 16) or (0 shl 8) or 0
        val premul = ColorPixelConvention.straightToPremultiplied(transparentRed)
        assertEquals(0, premul)

        val recovered = ColorPixelConvention.premultipliedToStraight(premul)
        assertEquals(0, recovered)
    }

    @Test
    fun testSemiTransparent_roundTripErrorIsBounded() {
        // Test varying alpha levels across all RGB channels
        val alphas = intArrayOf(16, 32, 64, 128, 192, 224, 250)
        val rgbLevels = intArrayOf(50, 100, 150, 200, 255)

        for (a in alphas) {
            val maxExpectedLsb = (255 / (2 * a)) + 1
            for (r in rgbLevels) {
                for (g in rgbLevels) {
                    val original = (a shl 24) or (r shl 16) or (g shl 8) or 120
                    val premul = ColorPixelConvention.straightToPremultiplied(original)
                    val recovered = ColorPixelConvention.premultipliedToStraight(premul)

                    val recA = (recovered ushr 24) and 0xFF
                    val recR = (recovered ushr 16) and 0xFF
                    val recG = (recovered ushr 8) and 0xFF
                    val recB = recovered and 0xFF

                    assertEquals("Alpha must be identical", a, recA)
                    assertTrue("Red channel difference must be <= $maxExpectedLsb LSB", abs(r - recR) <= maxExpectedLsb)
                    assertTrue("Green channel difference must be <= $maxExpectedLsb LSB", abs(g - recG) <= maxExpectedLsb)
                    assertTrue("Blue channel difference must be <= $maxExpectedLsb LSB", abs(120 - recB) <= maxExpectedLsb)

                    if (a >= 128) {
                        assertTrue("For alpha >= 128, red difference must be <= 1 LSB", abs(r - recR) <= 1)
                        assertTrue("For alpha >= 128, green difference must be <= 1 LSB", abs(g - recG) <= 1)
                        assertTrue("For alpha >= 128, blue difference must be <= 1 LSB", abs(120 - recB) <= 1)
                    }
                }
            }
        }
    }

    @Test
    fun testEdgeHaloElimination_onTransparentBoundary() {
        // 2x2 grid representing a border pixel:
        // Top-left: Opaque Pure Red (255, 255, 0, 0)
        // Top-right: Transparent zero (0, 0, 0, 0)
        // Bottom-left: Opaque Pure Red (255, 255, 0, 0)
        // Bottom-right: Transparent zero (0, 0, 0, 0)
        val opaqueRed = (255 shl 24) or (255 shl 16) or (0 shl 8) or 0
        val transparentZero = 0

        val buffer = intArrayOf(
            opaqueRed, transparentZero,
            opaqueRed, transparentZero
        )

        // Sample at halfway (x = 0.5f, y = 0.5f)
        val premultipliedSample = ColorPixelConvention.sampleBilinearPremultiplied(
            buffer, width = 2, height = 2, x = 0.5f, y = 0.5f
        )
        val naiveSample = ColorPixelConvention.sampleBilinearStraightNaive(
            buffer, width = 2, height = 2, x = 0.5f, y = 0.5f
        )

        val premulA = (premultipliedSample ushr 24) and 0xFF
        val premulR = (premultipliedSample ushr 16) and 0xFF
        val naiveA = (naiveSample ushr 24) and 0xFF
        val naiveR = (naiveSample ushr 16) and 0xFF

        // Both samples have 50% opacity (~128)
        assertEquals(128, premulA)
        assertEquals(128, naiveA)

        // CRITICAL CHECK:
        // In premultiplied sampling, the red pigment is preserved at 255!
        // In naive straight sampling, the red pigment was incorrectly diluted to 128 (dark maroon halo)!
        assertEquals("Premultiplied sampling preserves full red pigment intensity (no edge halo)", 255, premulR)
        assertEquals("Naive sampling produces dark dimmed halo with diluted red", 128, naiveR)
    }

    @Test
    fun testBufferConversion_roundTrip() {
        val original = intArrayOf(
            0xFF112233.toInt(),
            0x80445566.toInt(),
            0x00000000.toInt(),
            0xFFFFFFFF.toInt()
        )
        val copy = original.clone()

        ColorPixelConvention.convertBufferToPremultiplied(copy)
        ColorPixelConvention.convertBufferToStraight(copy)

        assertEquals(original[0], copy[0])
        assertEquals((original[1] ushr 24) and 0xFF, (copy[1] ushr 24) and 0xFF)
        assertEquals(0, copy[2])
        assertEquals(original[3], copy[3])
    }
}
