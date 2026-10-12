package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.cv.palette.perceivedLuminance
import com.artstudio.document.model.BlendModeType
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 10: Comic Book.
 *
 * Produces high-contrast dynamic ink shading with vintage Ben-Day dot matrix fills,
 * solid black ink shadow pooling, and saturated pop-art accents.
 */
object ComicBookStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.COMIC_BOOK
    override val displayName: String = "Comic Book"
    override val description: String = "High-contrast dynamic ink shading with Ben-Day dot matrix fills and vivid pop accents"

    override val supportedInputModes: List<String> = listOf("Line Art", "Comic Inking", "Graphic Silhouettes")
    override val documentedLimitations: String =
        "Photographic textures destroy the iconic graphic separation between solid ink fills and Ben-Day dots, muddying primary print tones."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? ComicBookConfig ?: ComicBookConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val benDayPixels = IntArray(total)
        val inkShadowPixels = IntArray(total)
        val composite = IntArray(total)

        val cc = config.comicContrast
        val dotPitch = config.benDayDotScale.coerceIn(3.0f, 24.0f)
        val halfPitch = dotPitch * 0.5f
        val pitchSq = halfPitch * halfPitch
        val offset = config.colorOffsetPx.coerceIn(0, 3)

        // 1. Render Vivid Pop Base Flats with Print Registration Offset
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)

            // Dynamic comic contrast pop
            val cr = ((baseColor.red - 0.5f) * (1.0f + cc) + 0.5f).coerceIn(0f, 1f)
            val cg = ((baseColor.green - 0.5f) * (1.0f + cc) + 0.5f).coerceIn(0f, 1f)
            val cb = ((baseColor.blue - 0.5f) * (1.0f + cc) + 0.5f).coerceIn(0f, 1f)

            val rInt = (cr * 255f).roundToInt().coerceIn(0, 255)
            val gInt = (cg * 255f).roundToInt().coerceIn(0, 255)
            val bInt = (cb * 255f).roundToInt().coerceIn(0, 255)

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 12) {
                        // Apply slight misregistration offset if configured
                        val ox = (x + offset).coerceIn(0, w - 1)
                        val outIdx = row + ox
                        val px = (maskVal shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                        basePixels[outIdx] = px
                        composite[outIdx] = px
                    }
                }
            }
        }

        // 2. Render Ben-Day Halftone Dot Shading
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
            val lum = baseColor.perceivedLuminance
            val dotCoverage = (0.75f - lum * 0.5f).coerceIn(0.15f, 0.70f)

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 12) {
                        val cu = ((x % dotPitch + dotPitch) % dotPitch) - halfPitch
                        val cv = ((y % dotPitch + dotPitch) % dotPitch) - halfPitch
                        val dSq = (cu * cu + cv * cv) / pitchSq

                        if (dSq <= dotCoverage) {
                            // Saturated primary Ben-Day dot (Magenta / Cyan tint)
                            val dotA = (maskVal * 0.85f).roundToInt().coerceIn(0, 255)
                            val dotPx = (dotA shl 24) or (0xBE shl 16) or (0x10 shl 8) or 0x60
                            benDayPixels[idx] = dotPx
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                dotPx,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        }
                    }
                }
            }
        }

        // 3. Render Solid Black Ink Shadow Pools
        for ((regionId, mask) in input.regionMasks) {
            var minX = w; var maxX = 0; var minY = h; var maxY = 0
            var sumX = 0L; var sumY = 0L; var count = 0
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if ((mask[row + x].toInt() and 0xFF) > 128) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                        sumX += x
                        sumY += y
                        count++
                    }
                }
            }
            if (count == 0) continue

            val cx = sumX.toFloat() / count
            val cy = sumY.toFloat() / count
            val radius = max(8.0f, max(maxX - minX, maxY - minY) * 0.5f)
            val shadowCutoff = 1.0f - config.inkShadowThreshold * 1.5f

            for (y in minY..maxY) {
                val row = y * w
                for (x in minX..maxX) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 128) {
                        val dx = (x - cx) / radius
                        val dy = (y - cy) / radius
                        // 45 degree directional shadow
                        val proj = dx * (-0.7071f) + dy * (-0.7071f)

                        if (proj > shadowCutoff) {
                            val inkAlpha = maskVal // Solid black ink
                            val inkPx = (inkAlpha shl 24) or (0x0A shl 16) or (0x0A shl 8) or 0x0A
                            inkShadowPixels[idx] = inkPx
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                inkPx,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        }
                    }
                }
            }
        }

        // 4. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(StyleLayerOutput(name = "Vivid Pop Base", role = LayerRole.BASE_FLAT, pixels = basePixels, blendMode = BlendModeType.Normal))
        layers.add(StyleLayerOutput(name = "Ben-Day Halftone Dots", role = LayerRole.TEXTURE, pixels = benDayPixels, blendMode = BlendModeType.Multiply))
        layers.add(StyleLayerOutput(name = "Solid Ink Shadows", role = LayerRole.SHADOW, pixels = inkShadowPixels, blendMode = BlendModeType.Multiply))

        // 5. Composite Preserved Linework
        input.linework?.let { lw ->
            val lwPixels = lw.pixels
            for (i in 0 until total) {
                val lwPx = lwPixels[i]
                if (((lwPx ushr 24) and 0xFF) > 0) {
                    composite[i] = LayerBlendCompositor.blendPixel(composite[i], lwPx, lw.blendMode, lw.opacity)
                }
            }
            layers.add(StyleLayerOutput(name = "Line Art (Preserved)", role = LayerRole.LINEWORK, pixels = lwPixels, blendMode = lw.blendMode, opacity = lw.opacity))
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        val recipe = ColorStackRecipe(
            name = "Comic Book Recipe",
            styleId = ColorStyleId.COMIC_BOOK,
            stages = listOf(
                BaseFillStageConfig(name = "Vivid Pop Base"),
                MediumTextureStageConfig(
                    name = "Ben-Day Dot Matrix",
                    mediumType = MediumTextureStageConfig.MediumTextureType.SCREENTONE_DOTS,
                    scale = config.benDayDotScale
                ),
                CelShadowStageConfig(name = "Solid Black Ink Shadows", shadowDarkness = 0.95f)
            )
        )

        return StyleStrategyOutput(
            styleId = styleId,
            width = w,
            height = h,
            finalComposite = composite,
            layers = layers,
            renderTimeMs = elapsed,
            memoryAllocatedBytes = total * 16L,
            recipeSnapshot = recipe
        )
    }
}
