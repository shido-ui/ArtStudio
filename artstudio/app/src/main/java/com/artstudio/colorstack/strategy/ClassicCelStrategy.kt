package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 1: Classic Cel Shading.
 *
 * Produces crisp 2-tone animation shading with directional hard-edged shadows
 * and bright specular crest highlights.
 */
object ClassicCelStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.CLASSIC_CEL
    override val displayName: String = "Classic Cel Shading"
    override val description: String = "Crisp 2-tone hard-edged shadows and specular highlights inspired by traditional animation"

    override val supportedInputModes: List<String> = listOf("Line Art", "Flat Colour", "Vector Contours")
    override val documentedLimitations: String =
        "Heavily textured photographic references without distinct contour lines require preprocessing (edge extraction or region segmentation) before rendering; otherwise cel cutoffs will be planar approximations."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? ClassicCelConfig ?: ClassicCelConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val shadowPixels = IntArray(total)
        val highlightPixels = IntArray(total)

        val composite = IntArray(total)

        val rad = Math.toRadians(config.lightAngleDeg.toDouble())
        val lx = cos(rad).toFloat()
        val ly = sin(rad).toFloat()
        val sx = -lx
        val sy = -ly

        // 1. Render Base Flats
        for ((regionId, mask) in input.regionMasks) {
            val color = resolveRegionColor(regionId, input, config.sourcePreservation)
            val rInt = color.redInt
            val gInt = color.greenInt
            val bInt = color.blueInt

            for (i in 0 until total) {
                val maskVal = mask[i].toInt() and 0xFF
                if (maskVal > 12) {
                    val px = (maskVal shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    basePixels[i] = px
                    composite[i] = px
                }
            }
        }

        // 2. Render Cel Shadows (Multiply)
        val shadowCutoff = 1.0f - 2.0f * config.shadowExtent
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)

            // Centroid and extent
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

            val mult = (1.0f - config.shadowDarkness).coerceIn(0.05f, 1.0f)
            var sr = baseColor.red * mult
            var sg = baseColor.green * mult
            var sb = baseColor.blue * mult

            if (config.coolShadowTint) {
                sb = (sb * 1.15f).coerceIn(0f, 1f)
                sr = (sr * 0.90f).coerceIn(0f, 1f)
            }

            val srInt = (sr * 255f).roundToInt().coerceIn(0, 255)
            val sgInt = (sg * 255f).roundToInt().coerceIn(0, 255)
            val sbInt = (sb * 255f).roundToInt().coerceIn(0, 255)

            for (y in minY..maxY) {
                val row = y * w
                for (x in minX..maxX) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 128) {
                        val dx = (x - cx) / radius
                        val dy = (y - cy) / radius
                        val proj = dx * sx + dy * sy
                        if (proj > shadowCutoff) {
                            val effA = (maskVal * config.shadowDarkness).roundToInt().coerceIn(0, 255)
                            val px = (effA shl 24) or (srInt shl 16) or (sgInt shl 8) or sbInt
                            shadowPixels[idx] = px
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                px,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        }
                    }
                }
            }
        }

        // 3. Render Specular Highlights (Screen)
        val hlCutoff = 0.70f
        if (config.highlightIntensity > 0.05f) {
            for ((_, mask) in input.regionMasks) {
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

                for (y in minY..maxY) {
                    val row = y * w
                    for (x in minX..maxX) {
                        val idx = row + x
                        val maskVal = mask[idx].toInt() and 0xFF
                        if (maskVal > 128) {
                            val dx = (x - cx) / radius
                            val dy = (y - cy) / radius
                            val proj = dx * lx + dy * ly
                            if (proj > hlCutoff) {
                                val falloff = ((proj - hlCutoff) / (1.0f - hlCutoff)).coerceIn(0f, 1f)
                                val effA = (falloff * config.highlightIntensity * 255f).roundToInt().coerceIn(0, 255)
                                val px = (effA shl 24) or (0xFE shl 16) or (0xFE shl 8) or 0xFE
                                highlightPixels[idx] = px
                                composite[idx] = LayerBlendCompositor.blendPixel(
                                    composite[idx],
                                    px,
                                    BlendModeType.Screen,
                                    1.0f
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Composite Independent Linework
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(StyleLayerOutput(name = "Base Flats", role = LayerRole.BASE_FLAT, pixels = basePixels, blendMode = BlendModeType.Normal))
        layers.add(StyleLayerOutput(name = "Cel Shadows", role = LayerRole.SHADOW, pixels = shadowPixels, blendMode = BlendModeType.Multiply))
        if (config.highlightIntensity > 0.05f) {
            layers.add(StyleLayerOutput(name = "Specular Highlights", role = LayerRole.HIGHLIGHT, pixels = highlightPixels, blendMode = BlendModeType.Screen))
        }

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
            name = "Classic Cel Shading Recipe",
            styleId = ColorStyleId.CLASSIC_CEL,
            stages = listOf(
                BaseFillStageConfig(name = "Base Flats"),
                CelShadowStageConfig(name = "Cel Shadows", shadowDarkness = config.shadowDarkness, lightAngleDeg = config.lightAngleDeg, shadowExtent = config.shadowExtent, coolShadowTint = config.coolShadowTint),
                HighlightStageConfig(name = "Specular Highlights", intensity = config.highlightIntensity, lightAngleDeg = config.lightAngleDeg)
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

