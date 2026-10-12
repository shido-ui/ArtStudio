package com.artstudio.cv.region

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analytical ground-truth synthetic test fixture with known dimensions, seed point,
 * reference segmentation mask, and expected boundary topology.
 */
data class SyntheticRegionFixture(
    val name: String,
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val seedX: Int,
    val seedY: Int,
    val groundTruthMask: ByteArray,
    val expectedBoundaryType: RegionBoundaryType
)

/**
 * Generator producing deterministic synthetic test fixtures for CV region segmentation.
 * Covers flat colours, anti-aliased gradients, 1-pixel micro-gaps, multi-pixel ruptures,
 * donut holes, transparency silhouettes, speckle noise, and touching adjacent colours.
 */
object SyntheticRegionFixtureGenerator {

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val RED = 0xFFFF0000.toInt()
    private const val GREEN = 0xFF00FF00.toInt()
    private const val BLUE = 0xFF0000FF.toInt()
    private const val TRANSPARENT = 0x00000000

    /**
     * 1. Flat colour solid rectangular region on a solid background with sharp boundaries.
     */
    fun createFlatColourFixture(width: Int = 100, height: Int = 100): SyntheticRegionFixture {
        val pixels = IntArray(width * height) { WHITE }
        val groundTruth = ByteArray(width * height)

        val minX = 20
        val minY = 20
        val maxX = 80
        val maxY = 80

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                pixels[y * width + x] = BLUE
                groundTruth[y * width + x] = 255.toByte()
            }
        }

        return SyntheticRegionFixture(
            name = "flat_colour_rectangle",
            width = width,
            height = height,
            pixels = pixels,
            seedX = 50,
            seedY = 50,
            groundTruthMask = groundTruth,
            expectedBoundaryType = RegionBoundaryType.ADJACENT_COLOUR_BARRIER
        )
    }

    /**
     * 2. Closed outline box surrounded by solid 1-pixel black ink stroke.
     */
    fun createClosedOutlineFixture(width: Int = 100, height: Int = 100): SyntheticRegionFixture {
        val pixels = IntArray(width * height) { WHITE }
        val groundTruth = ByteArray(width * height)

        val minX = 20
        val minY = 20
        val maxX = 80
        val maxY = 80

        // Draw solid black border
        for (x in minX..maxX) {
            pixels[minY * width + x] = BLACK
            pixels[maxY * width + x] = BLACK
        }
        for (y in minY..maxY) {
            pixels[y * width + minX] = BLACK
            pixels[y * width + maxX] = BLACK
        }

        // Ground truth is interior of the box
        for (y in (minY + 1) until maxY) {
            for (x in (minX + 1) until maxX) {
                groundTruth[y * width + x] = 255.toByte()
            }
        }

        return SyntheticRegionFixture(
            name = "closed_outline_box",
            width = width,
            height = height,
            pixels = pixels,
            seedX = 50,
            seedY = 50,
            groundTruthMask = groundTruth,
            expectedBoundaryType = RegionBoundaryType.CLOSED_OUTLINE
        )
    }

    /**
     * 3. One-pixel micro-gap fixture: 1-pixel outline box with an exact 1-pixel gap in the top edge.
     */
    fun createOnePixelGapFixture(width: Int = 100, height: Int = 100): SyntheticRegionFixture {
        val pixels = IntArray(width * height) { WHITE }
        val groundTruth = ByteArray(width * height)

        val minX = 20
        val minY = 20
        val maxX = 80
        val maxY = 80

        // Draw border
        for (x in minX..maxX) {
            pixels[minY * width + x] = BLACK
            pixels[maxY * width + x] = BLACK
        }
        for (y in minY..maxY) {
            pixels[y * width + minX] = BLACK
            pixels[y * width + maxX] = BLACK
        }

        // Punch exact 1-pixel gap at top center (50, 20)
        pixels[minY * width + 50] = WHITE

        // Ground truth is the intended interior enclosed region
        for (y in (minY + 1) until maxY) {
            for (x in (minX + 1) until maxX) {
                groundTruth[y * width + x] = 255.toByte()
            }
        }

        return SyntheticRegionFixture(
            name = "one_pixel_gap_box",
            width = width,
            height = height,
            pixels = pixels,
            seedX = 50,
            seedY = 50,
            groundTruthMask = groundTruth,
            expectedBoundaryType = RegionBoundaryType.LEAKY_GAP
        )
    }

    /**
     * 4. Multi-pixel gap fixture: outline box with a configurable 3 to 5 pixel gap.
     */
    fun createMultiPixelGapFixture(
        width: Int = 100,
        height: Int = 100,
        gapSize: Int = 4
    ): SyntheticRegionFixture {
        val pixels = IntArray(width * height) { WHITE }
        val groundTruth = ByteArray(width * height)

        val minX = 20
        val minY = 20
        val maxX = 80
        val maxY = 80

        for (x in minX..maxX) {
            pixels[minY * width + x] = BLACK
            pixels[maxY * width + x] = BLACK
        }
        for (y in minY..maxY) {
            pixels[y * width + minX] = BLACK
            pixels[y * width + maxX] = BLACK
        }

        // Punch multi-pixel gap centered at x=50
        val halfGap = gapSize / 2
        for (gx in (50 - halfGap)..(50 + (gapSize - 1 - halfGap))) {
            pixels[minY * width + gx] = WHITE
        }

        for (y in (minY + 1) until maxY) {
            for (x in (minX + 1) until maxX) {
                groundTruth[y * width + x] = 255.toByte()
            }
        }

        return SyntheticRegionFixture(
            name = "multi_pixel_gap_${gapSize}px",
            width = width,
            height = height,
            pixels = pixels,
            seedX = 50,
            seedY = 50,
            groundTruthMask = groundTruth,
            expectedBoundaryType = RegionBoundaryType.LEAKY_GAP
        )
    }

    /**
     * 5. Donut hole fixture: outer circular disk with an unmasked inner circular island hole.
     */
    fun createDonutHoleFixture(
        width: Int = 100,
        height: Int = 100,
        outerRadius: Float = 36f,
        innerRadius: Float = 14f
    ): SyntheticRegionFixture {
        val pixels = IntArray(width * height) { WHITE }
        val groundTruth = ByteArray(width * height)
        val cx = 50f
        val cy = 50f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= outerRadius && dist >= innerRadius) {
                    pixels[y * width + x] = GREEN
                    groundTruth[y * width + x] = 255.toByte()
                } else {
                    pixels[y * width + x] = WHITE
                }
            }
        }

        return SyntheticRegionFixture(
            name = "donut_hole_annulus",
            width = width,
            height = height,
            pixels = pixels,
            seedX = 50,
            seedY = (cy + (outerRadius + innerRadius) / 2f).toInt(),
            groundTruthMask = groundTruth,
            expectedBoundaryType = RegionBoundaryType.ADJACENT_COLOUR_BARRIER
        )
    }

    /**
     * 6. Anti-aliased circle boundary with gradient fringe.
     */
    fun createAntiAliasedFixture(
        width: Int = 100,
        height: Int = 100,
        radius: Float = 30f
    ): SyntheticRegionFixture {
        val pixels = IntArray(width * height) { WHITE }
        val groundTruth = ByteArray(width * height)
        val cx = 50f
        val cy = 50f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt(dx * dx + dy * dy)
                val diff = dist - radius

                if (diff < -1.0f) {
                    // Solid black interior line or solid fill
                    pixels[y * width + x] = WHITE
                    groundTruth[y * width + x] = 255.toByte()
                } else if (abs(diff) <= 1.0f) {
                    // Anti-aliased edge transition [0.0..1.0]
                    val alpha = ((diff + 1.0f) / 2.0f).coerceIn(0f, 1f)
                    val gray = (alpha * 255f).toInt()
                    pixels[y * width + x] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    if (diff <= 0f) {
                        groundTruth[y * width + x] = 255.toByte()
                    }
                } else if (abs(diff - 2f) <= 1.0f) {
                    // Solid outline ring
                    pixels[y * width + x] = BLACK
                } else {
                    pixels[y * width + x] = WHITE
                }
            }
        }

        return SyntheticRegionFixture(
            name = "anti_aliased_circle",
            width = width,
            height = height,
            pixels = pixels,
            seedX = 50,
            seedY = 50,
            groundTruthMask = groundTruth,
            expectedBoundaryType = RegionBoundaryType.ANTI_ALIASED_FRINGE
        )
    }

    /**
     * 7. Transparency silhouette: solid object surrounded entirely by transparent canvas pixels (alpha = 0).
     */
    fun createTransparencySilhouetteFixture(
        width: Int = 100,
        height: Int = 100
    ): SyntheticRegionFixture {
        val pixels = IntArray(width * height) { TRANSPARENT }
        val groundTruth = ByteArray(width * height)

        val minX = 25
        val minY = 25
        val maxX = 75
        val maxY = 75

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                pixels[y * width + x] = RED
                groundTruth[y * width + x] = 255.toByte()
            }
        }

        return SyntheticRegionFixture(
            name = "transparency_silhouette",
            width = width,
            height = height,
            pixels = pixels,
            seedX = 50,
            seedY = 50,
            groundTruthMask = groundTruth,
            expectedBoundaryType = RegionBoundaryType.TRANSPARENCY_SILHOUETTE
        )
    }

    /**
     * 8. Noisy/speckled region fixture: solid region containing salt-and-pepper noise specks.
     */
    fun createNoisyFixture(width: Int = 100, height: Int = 100): SyntheticRegionFixture {
        val pixels = IntArray(width * height) { WHITE }
        val groundTruth = ByteArray(width * height)

        val minX = 20
        val minY = 20
        val maxX = 80
        val maxY = 80

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                pixels[y * width + x] = BLUE
                groundTruth[y * width + x] = 255.toByte()
            }
        }

        // Deterministic noise coordinates inside region
        val noisePointsInside = listOf(
            Pair(30, 30), Pair(45, 60), Pair(65, 35), Pair(70, 70)
        )
        for ((nx, ny) in noisePointsInside) {
            pixels[ny * width + nx] = WHITE // White specks inside blue region
        }

        // Noise points outside region
        val noisePointsOutside = listOf(
            Pair(10, 10), Pair(90, 90), Pair(15, 85), Pair(85, 15)
        )
        for ((nx, ny) in noisePointsOutside) {
            pixels[ny * width + nx] = BLUE // Blue specks outside
        }

        return SyntheticRegionFixture(
            name = "noisy_speckles",
            width = width,
            height = height,
            pixels = pixels,
            seedX = 50,
            seedY = 50,
            groundTruthMask = groundTruth,
            expectedBoundaryType = RegionBoundaryType.ADJACENT_COLOUR_BARRIER
        )
    }

    /**
     * 9. Adjacent touching colours fixture: two distinct solid colors meeting directly without any ink line.
     */
    fun createAdjacentTouchingColoursFixture(
        width: Int = 100,
        height: Int = 100
    ): SyntheticRegionFixture {
        val pixels = IntArray(width * height)
        val groundTruth = ByteArray(width * height)

        val midX = width / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x < midX) {
                    pixels[y * width + x] = RED
                    groundTruth[y * width + x] = 255.toByte()
                } else {
                    pixels[y * width + x] = BLUE
                }
            }
        }

        return SyntheticRegionFixture(
            name = "adjacent_touching_colours",
            width = width,
            height = height,
            pixels = pixels,
            seedX = (width / 4).coerceAtLeast(0),
            seedY = (height / 2).coerceAtLeast(0),
            groundTruthMask = groundTruth,
            expectedBoundaryType = RegionBoundaryType.ADJACENT_COLOUR_BARRIER
        )
    }
}
