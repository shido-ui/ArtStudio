package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 3: Hybrid Cel + Soft Shading.
 *
 * Combines crisp directional core cel shadows with atmospheric ambient
 * midtone gradient falloff, modulated by celVsSoftBalance.
 */
object HybridCelSoftStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.HYBRID_CEL_SOFT
    override val displayName: String = "Hybrid Cel + Soft Shading"
    override val description: String = "Dual-pass composite combining defined crisp cel core shadows with atmospheric ambient gradient roll-off"

    override val supportedInputModes: List<String> = listOf("Line Art", "Flat Colour", "Rendered 2D Assets")
    override val documentedLimitations: String =
        "Photo inputs without cleanly delineated silhouettes produce uneven cel thresholds; works best with vector or hand-drawn contour lines."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? HybridCelSoftConfig ?: HybridCelSoftConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val celShadowPixels = IntArray(total)
        val softGradientPixels = IntArray(total)
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

        // 2. Dual Lighting Passes (Cel Shadow Core & Soft Midtone Gradients)
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)

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

            // Shadow tint calculations
            val coreMult = (1.0f - config.coreShadowDarkness).coerceIn(0.05f, 1.0f)
            val srCore = (baseColor.red * coreMult * 255f).roundToInt().coerceIn(0, 255)
            val sgCore = (baseColor.green * coreMult * 255f).roundToInt().coerceIn(0, 255)
            val sbCore = (baseColor.blue * coreMult * 255f).roundToInt().coerceIn(0, 255)

            val softMult = (1.0f - config.coreShadowDarkness * 0.6f).coerceIn(0.1f, 1.0f)
            val srSoft = (baseColor.red * softMult * 255f).roundToInt().coerceIn(0, 255)
            val sgSoft = (baseColor.green * softMult * 255f).roundToInt().coerceIn(0, 255)
            val sbSoft = (baseColor.blue * softMult * 255f).roundToInt().coerceIn(0, 255)

            // Cel step cutoff
            val celCutoff = 0.10f

            for (y in minY..maxY) {
                val row = y * w
                for (x in minX..maxX) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 12) {
                        val dx = (x - cx) / radius
                        val dy = (y - cy) / radius
                        val proj = dx * sx + dy * sy

                        // Pass A: Crisp Cel Core
                        if (proj > celCutoff && config.celVsSoftBalance > 0.05f) {
                            val celAlpha = (maskVal * config.coreShadowDarkness * config.celVsSoftBalance).roundToInt().coerceIn(0, 255)
                            val celPx = (celAlpha shl 24) or (srCore shl 16) or (sgCore shl 8) or sbCore
                            celShadowPixels[idx] = celPx
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                celPx,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        }

                        // Pass B: Soft Ambient Falloff
                        val softBalance = 1.0f - config.celVsSoftBalance
                        if (softBalance > 0.05f) {
                            val u = ((proj + 1.0f) * 0.5f).coerceIn(0f, 1f)
                            val falloff = u.pow(config.ambientSoftness)
                            val softAlpha = (maskVal * config.coreShadowDarkness * softBalance * falloff).roundToInt().coerceIn(0, 255)
                            if (softAlpha > 2) {
                                val softPx = (softAlpha shl 24) or (srSoft shl 16) or (sgSoft shl 8) or sbSoft
                                softGradientPixels[idx] = softPx
                                composite[idx] = LayerBlendCompositor.blendPixel(
                                    composite[idx],
                                    softPx,
                                    BlendModeType.Multiply,
                                    1.0f
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Render Specular Highlights (Screen)
        if (config.highlightIntensity > 0.05f) {
            val hlCutoff = 0.68f
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
        if (config.celVsSoftBalance > 0.05f) {
            layers.add(StyleLayerOutput(name = "Cel Shadow Core", role = LayerRole.SHADOW, pixels = celShadowPixels, blendMode = BlendModeType.Multiply))
        }
        if (1.0f - config.celVsSoftBalance > 0.05f) {
            layers.add(StyleLayerOutput(name = "Soft Midtone Gradients", role = LayerRole.GRADIENT, pixels = softGradientPixels, blendMode = BlendModeType.Multiply))
        }
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
            name = "Hybrid Cel + Soft Shading Recipe",
            styleId = ColorStyleId.HYBRID_CEL_SOFT,
            stages = listOf(
                BaseFillStageConfig(name = "Base Flats"),
                CelShadowStageConfig(
                    name = "Cel Shadow Core",
                    shadowDarkness = config.coreShadowDarkness * config.celVsSoftBalance,
                    lightAngleDeg = config.lightAngleDeg
                ),
                GradientStageConfig(
                    name = "Soft Midtone Gradients",
                    softness = config.ambientSoftness,
                    shadowRatio = (1.0f - config.celVsSoftBalance) * config.coreShadowDarkness,
                    lightAngleDeg = config.lightAngleDeg
                ),
                HighlightStageConfig(
                    name = "Specular Highlights",
                    intensity = config.highlightIntensity,
                    lightAngleDeg = config.lightAngleDeg
                )
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
