package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 4: Flat Illustration.
 *
 * Produces graphic unshaded flats with boundary bleed dilation under linework
 * to prevent unsightly anti-aliasing halos, and optional graphic contrast boosting.
 */
object FlatIllustrationStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.FLAT_ILLUSTRATION
    override val displayName: String = "Flat Illustration"
    override val description: String = "Bold graphic flat fills with boundary bleed dilation under linework to eliminate halo gaps, zero 3D shading"

    override val supportedInputModes: List<String> = listOf("Line Art", "Vector Inking", "Graphic Silhouettes")
    override val documentedLimitations: String =
        "Photographic references without distinct boundary ink lines result in large uniform flat islands lacking depth; not designed for realistic lighting."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? FlatIllustrationConfig ?: FlatIllustrationConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val planarPixels = if (config.toneMode == FlatToneMode.PLANAR_STEP) IntArray(total) else null
        val composite = IntArray(total)

        // 1. Process each region mask with boundary bleed dilation/erosion
        for ((regionId, rawMask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)

            // Apply contrast boost
            val cb = config.contrastBoost
            val cr = ((baseColor.red - 0.5f) * (1.0f + cb) + 0.5f).coerceIn(0f, 1f)
            val cg = ((baseColor.green - 0.5f) * (1.0f + cb) + 0.5f).coerceIn(0f, 1f)
            val cbVal = ((baseColor.blue - 0.5f) * (1.0f + cb) + 0.5f).coerceIn(0f, 1f)

            val rInt = (cr * 255f).roundToInt().coerceIn(0, 255)
            val gInt = (cg * 255f).roundToInt().coerceIn(0, 255)
            val bInt = (cbVal * 255f).roundToInt().coerceIn(0, 255)

            // Secondary stepped tone if PLANAR_STEP
            val stepR = (cr * 0.82f * 255f).roundToInt().coerceIn(0, 255)
            val stepG = (cg * 0.82f * 255f).roundToInt().coerceIn(0, 255)
            val stepB = (cbVal * 0.82f * 255f).roundToInt().coerceIn(0, 255)

            // Dilation / Erosion for boundary bleed
            val effectiveMask = applyBoundaryBleed(rawMask, w, h, config.boundaryBleedPx)

            // Find centroid for planar step
            var minX = w; var maxX = 0; var minY = h; var maxY = 0
            var sumX = 0L; var sumY = 0L; var count = 0
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if ((effectiveMask[row + x].toInt() and 0xFF) > 12) {
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
            val midY = if (count > 0) (sumY / count).toInt() else h / 2

            for (i in 0 until total) {
                val maskVal = effectiveMask[i].toInt() and 0xFF
                if (maskVal > 12) {
                    val y = i / w
                    val px = (maskVal shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    basePixels[i] = px
                    composite[i] = px

                    if (config.toneMode == FlatToneMode.PLANAR_STEP && y > midY && planarPixels != null) {
                        val stepPx = (maskVal shl 24) or (stepR shl 16) or (stepG shl 8) or stepB
                        planarPixels[i] = stepPx
                        composite[i] = stepPx
                    }
                }
            }
        }

        // 2. Assemble layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(StyleLayerOutput(name = "Base Graphic Flats", role = LayerRole.BASE_FLAT, pixels = basePixels, blendMode = BlendModeType.Normal))
        if (planarPixels != null) {
            layers.add(StyleLayerOutput(name = "Planar Graphic Steps", role = LayerRole.BASE_FLAT, pixels = planarPixels, blendMode = BlendModeType.Normal))
        } else {
            val accentPixels = IntArray(total)
            for (i in 0 until total) {
                val px = basePixels[i]
                val a = (px ushr 24) and 0xFF
                if (a > 0) {
                    val r = (((px ushr 16) and 0xFF) * 0.92f).roundToInt().coerceIn(0, 255)
                    val g = (((px ushr 8) and 0xFF) * 0.92f).roundToInt().coerceIn(0, 255)
                    val b = ((px and 0xFF) * 0.92f).roundToInt().coerceIn(0, 255)
                    accentPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            layers.add(StyleLayerOutput(name = "Graphic Accent Flats", role = LayerRole.SHADOW, pixels = accentPixels, blendMode = BlendModeType.Multiply, opacity = 0.5f))
        }

        // 3. Composite preserved linework
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
            name = "Flat Illustration Recipe",
            styleId = ColorStyleId.FLAT_ILLUSTRATION,
            stages = listOf(
                BaseFillStageConfig(name = "Base Graphic Flats", fillType = BaseFillStageConfig.BaseFillType.FLAT),
                FinishingEffectStageConfig(name = "Graphic Contrast Pop", effectType = FinishingEffectStageConfig.FinishingEffectType.POSTERIZE)
            )
        )

        return StyleStrategyOutput(
            styleId = styleId,
            width = w,
            height = h,
            finalComposite = composite,
            layers = layers,
            renderTimeMs = elapsed,
            memoryAllocatedBytes = total * 8L,
            recipeSnapshot = recipe
        )
    }

    private fun applyBoundaryBleed(mask: ByteArray, w: Int, h: Int, bleedPx: Int): ByteArray {
        if (bleedPx == 0) return mask
        val result = ByteArray(w * h)

        if (bleedPx > 0) {
            // Morphological dilation (max filter)
            val radius = bleedPx.coerceAtMost(3)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var maxVal = mask[row + x].toInt() and 0xFF
                    for (dy in -radius..radius) {
                        val ny = y + dy
                        if (ny in 0 until h) {
                            val nrow = ny * w
                            for (dx in -radius..radius) {
                                val nx = x + dx
                                if (nx in 0 until w) {
                                    val v = mask[nrow + nx].toInt() and 0xFF
                                    if (v > maxVal) maxVal = v
                                }
                            }
                        }
                    }
                    result[row + x] = maxVal.toByte()
                }
            }
        } else {
            // Morphological erosion (min filter)
            val radius = (-bleedPx).coerceAtMost(3)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var minVal = mask[row + x].toInt() and 0xFF
                    for (dy in -radius..radius) {
                        val ny = y + dy
                        if (ny in 0 until h) {
                            val nrow = ny * w
                            for (dx in -radius..radius) {
                                val nx = x + dx
                                if (nx in 0 until w) {
                                    val v = mask[nrow + nx].toInt() and 0xFF
                                    if (v < minVal) minVal = v
                                }
                            }
                        }
                    }
                    result[row + x] = minVal.toByte()
                }
            }
        }
        return result
    }
}
