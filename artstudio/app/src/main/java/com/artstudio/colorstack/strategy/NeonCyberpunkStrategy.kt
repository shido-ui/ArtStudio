package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import kotlin.math.*

/**
 * Strategy implementation for Style 14: Neon / Cyberpunk.
 *
 * Simulates high-energy futuristic aesthetic with deep noir underbases, electric glowing contour rims,
 * saturated chromatic bloom, and specular neon spikes.
 *
 * NOTE ON SIMULATION FIDELITY:
 * This strategy produces simulated optical glow and stylized aesthetic post-effects via bounded
 * separable spatial convolution, boundary rim emission, and chromatic offset fringing.
 * It is an optical graphic filter, NOT a physical raymarched volumetric light transport solver
 * or electromagnetic wave emission simulation.
 */
object NeonCyberpunkStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.NEON_CYBERPUNK
    override val displayName: String = "Neon / Cyberpunk"
    override val description: String =
        "Electric glowing rims, chromatic bloom, and saturated neon accents over moody noir underbases (simulated optical glow effect, not a physical volumetric solver)"

    override val supportedInputModes: List<String> = listOf("Line Art", "Flat Colour", "Graphic Inking")
    override val documentedLimitations: String =
        "Dark noir base floor compresses midtone gradations to amplify dynamic glow contrast. Photographic references with dense low-contrast textures will have suppressed details outside glowing rim contours."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? NeonCyberpunkConfig ?: NeonCyberpunkConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val rimPixels = IntArray(total)
        val bloomPixels = IntArray(total)
        val composite = IntArray(total)

        val isPreview = input.quality == RenderQuality.PREVIEW
        val effectiveGlowRadius = if (isPreview) max(1, config.glowRadiusPx / 2) else config.glowRadiusPx

        // 1. Render Deep Dark Noir Underbase (Dynamic Range Suppression)
        val floor = config.darkBaseFloor
        for ((regionId, mask) in input.regionMasks) {
            val rawColor = resolveRegionColor(regionId, input, config.sourcePreservation)

            // Compress to deep noir floor with subtle hue retention
            val nr = (rawColor.red * floor).coerceIn(0f, 1f)
            val ng = (rawColor.green * floor).coerceIn(0f, 1f)
            val nb = (rawColor.blue * floor * 1.20f).coerceIn(0f, 1f) // Indigo/cyan cyber bias

            val rInt = (nr * 255f).roundToInt().coerceIn(0, 255)
            val gInt = (ng * 255f).roundToInt().coerceIn(0, 255)
            val bInt = (nb * 255f).roundToInt().coerceIn(0, 255)

            for (i in 0 until total) {
                val maskVal = mask[i].toInt() and 0xFF
                if (maskVal > 12) {
                    val px = (maskVal shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    basePixels[i] = px
                    composite[i] = px
                }
            }
        }

        // 2. Synthesize Electric Neon Contour Rims (Hyper-Saturated Boundary Emission)
        val rimThickness = 2
        for ((regionId, mask) in input.regionMasks) {
            val rawColor = resolveRegionColor(regionId, input, config.sourcePreservation)

            // Hyper-saturate color for electric emission
            val maxC = max(rawColor.red, max(rawColor.green, rawColor.blue)).coerceAtLeast(0.1f)
            val boost = 1.0f + config.neonSaturationBoost * 0.8f
            val er = ((rawColor.red / maxC) * boost).coerceIn(0f, 1f)
            val eg = ((rawColor.green / maxC) * boost).coerceIn(0f, 1f)
            val eb = ((rawColor.blue / maxC) * boost).coerceIn(0f, 1f)

            val erInt = (er * 255f).roundToInt().coerceIn(0, 255)
            val egInt = (eg * 255f).roundToInt().coerceIn(0, 255)
            val ebInt = (eb * 255f).roundToInt().coerceIn(0, 255)

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 40) {
                        // Check boundary distance
                        var isEdge = false
                        for (dy in -rimThickness..rimThickness) {
                            val ny = y + dy
                            if (ny in 0 until h) {
                                val nrow = ny * w
                                for (dx in -rimThickness..rimThickness) {
                                    val nx = x + dx
                                    if (nx in 0 until w) {
                                        if ((mask[nrow + nx].toInt() and 0xFF) < 30) {
                                            isEdge = true
                                            break
                                        }
                                    }
                                }
                            }
                            if (isEdge) break
                        }

                        if (isEdge) {
                            val rimAlpha = (maskVal * 0.95f).roundToInt().coerceIn(0, 255)
                            val rimPx = (rimAlpha shl 24) or (erInt shl 16) or (egInt shl 8) or ebInt
                            rimPixels[idx] = rimPx
                        }
                    }
                }
            }
        }

        // 3. Bounded Separable Bloom / Glow Convolution (Single Scratch Line, Zero Duplicate Buffers)
        if (config.bloomIntensity > 0.05f && effectiveGlowRadius >= 1) {
            val rad = effectiveGlowRadius
            val scratchHorizontal = IntArray(total)

            // Horizontal pass
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var sumA = 0
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    var samples = 0

                    val minX = max(0, x - rad)
                    val maxX = min(w - 1, x + rad)
                    for (kx in minX..maxX) {
                        val px = rimPixels[row + kx]
                        val a = (px ushr 24) and 0xFF
                        if (a > 0) {
                            sumA += a
                            sumR += (px ushr 16) and 0xFF
                            sumG += (px ushr 8) and 0xFF
                            sumB += px and 0xFF
                            samples++
                        }
                    }

                    if (samples > 0) {
                        val avgA = sumA / (rad * 2 + 1)
                        val avgR = sumR / samples
                        val avgG = sumG / samples
                        val avgB = sumB / samples
                        scratchHorizontal[row + x] = (avgA shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                    }
                }
            }

            // Vertical pass into bloomPixels
            val bloomScale = config.bloomIntensity
            val chromaticOffset = config.chromaticShiftPx
            for (y in 0 until h) {
                val minY = max(0, y - rad)
                val maxY = min(h - 1, y + rad)
                val row = y * w
                for (x in 0 until w) {
                    var sumA = 0
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    var samples = 0

                    for (ky in minY..maxY) {
                        val px = scratchHorizontal[ky * w + x]
                        val a = (px ushr 24) and 0xFF
                        if (a > 0) {
                            sumA += a
                            sumR += (px ushr 16) and 0xFF
                            sumG += (px ushr 8) and 0xFF
                            sumB += px and 0xFF
                            samples++
                        }
                    }

                    if (samples > 0) {
                        val bA = ((sumA / (rad * 2 + 1)) * bloomScale).roundToInt().coerceIn(0, 255)
                        var bR = sumR / samples
                        var bG = sumG / samples
                        var bB = sumB / samples

                        // Subtle chromatic edge offset
                        if (chromaticOffset > 0 && x + chromaticOffset < w) {
                            val shiftedPx = scratchHorizontal[row + x + chromaticOffset]
                            bB = max(bB, shiftedPx and 0xFF)
                        }

                        bloomPixels[row + x] = (bA shl 24) or (bR shl 16) or (bG shl 8) or bB
                    }
                }
            }
        }

        // 4. Blend passes onto final composite (Additive / Screen Highlight)
        for (i in 0 until total) {
            val bloomPx = bloomPixels[i]
            if (((bloomPx ushr 24) and 0xFF) > 0) {
                composite[i] = LayerBlendCompositor.blendPixel(
                    composite[i],
                    bloomPx,
                    BlendModeType.Screen,
                    1.0f
                )
            }
            val rimPx = rimPixels[i]
            if (((rimPx ushr 24) and 0xFF) > 0) {
                composite[i] = LayerBlendCompositor.blendPixel(
                    composite[i],
                    rimPx,
                    BlendModeType.Screen,
                    1.0f
                )
            }
        }

        // 5. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(
            StyleLayerOutput(
                name = "Noir Underbase",
                role = LayerRole.BASE_FLAT,
                pixels = basePixels,
                blendMode = BlendModeType.Normal,
                opacity = 1.0f
            )
        )
        layers.add(
            StyleLayerOutput(
                name = "Electric Contour Rims",
                role = LayerRole.HIGHLIGHT,
                pixels = rimPixels,
                blendMode = BlendModeType.Screen,
                opacity = 1.0f
            )
        )
        if (config.bloomIntensity > 0.05f) {
            layers.add(
                StyleLayerOutput(
                    name = "Chromatic Bloom Glow",
                    role = LayerRole.HIGHLIGHT,
                    pixels = bloomPixels,
                    blendMode = BlendModeType.Screen,
                    opacity = config.bloomIntensity
                )
            )
        }

        // 6. Composite Preserved Linework
        input.linework?.let { lw ->
            val lwPixels = lw.pixels
            for (i in 0 until total) {
                val lwPx = lwPixels[i]
                if (((lwPx ushr 24) and 0xFF) > 0) {
                    composite[i] = LayerBlendCompositor.blendPixel(
                        composite[i],
                        lwPx,
                        lw.blendMode,
                        lw.opacity
                    )
                }
            }
            layers.add(
                StyleLayerOutput(
                    name = "Line Art (Preserved)",
                    role = LayerRole.LINEWORK,
                    pixels = lwPixels,
                    blendMode = lw.blendMode,
                    opacity = lw.opacity
                )
            )
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        val recipe = ColorStackRecipe(
            name = "Neon Cyberpunk Recipe",
            styleId = ColorStyleId.NEON_CYBERPUNK,
            stages = listOf(
                BaseFillStageConfig(name = "Noir Dark Base", fillType = BaseFillStageConfig.BaseFillType.FLAT),
                FinishingEffectStageConfig(
                    name = "Neon Bloom",
                    effectType = FinishingEffectStageConfig.FinishingEffectType.NEON_BLOOM,
                    bloomRadius = config.glowRadiusPx.toFloat(),
                    bloomIntensity = config.bloomIntensity
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
