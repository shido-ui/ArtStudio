package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 6: Watercolour.
 *
 * Simulates fluid pigment pooling, capillary drying puddle rings along boundaries,
 * cold-press paper granulation, and translucent layering.
 */
object WatercolourStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.WATERCOLOUR
    override val displayName: String = "Watercolour"
    override val description: String = "Fluid pigment wash pooling, wet-edge puddle rings, and cold-press paper granulation"

    override val supportedInputModes: List<String> = listOf("Line Art", "Pencil Contours", "Wash Underpaintings")
    override val documentedLimitations: String =
        "Photographic textures with fine surface noise conflict with simulated cold-press granulation; clean contour boundaries are required to form authentic drying puddle rings."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? WatercolourConfig ?: WatercolourConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val washPixels = IntArray(total)
        val puddlePixels = IntArray(total)
        val granulationPixels = IntArray(total)
        val composite = IntArray(total)

        // 1. Render Translucent Base Wash
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
            val rInt = baseColor.redInt
            val gInt = baseColor.greenInt
            val bInt = baseColor.blueInt

            for (i in 0 until total) {
                val maskVal = mask[i].toInt() and 0xFF
                if (maskVal > 12) {
                    val washAlpha = (maskVal * config.washWetness * 0.88f).roundToInt().coerceIn(0, 255)
                    val px = (washAlpha shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    washPixels[i] = px
                    composite[i] = px
                }
            }
        }

        // 2. Compute Wet-Edge Puddle Rings (Boundary Rim Pigment Accumulation)
        if (config.puddleRingDarkness > 0.05f) {
            val rimDist = config.edgeDiffusionPx.coerceIn(1, 5)
            for ((regionId, mask) in input.regionMasks) {
                val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
                val mult = (1.0f - config.puddleRingDarkness * 0.55f).coerceIn(0.1f, 1.0f)
                val drInt = (baseColor.red * mult * 255f).roundToInt().coerceIn(0, 255)
                val dgInt = (baseColor.green * mult * 255f).roundToInt().coerceIn(0, 255)
                val dbInt = (baseColor.blue * mult * 255f).roundToInt().coerceIn(0, 255)

                for (y in 0 until h) {
                    val row = y * w
                    for (x in 0 until w) {
                        val idx = row + x
                        val maskVal = mask[idx].toInt() and 0xFF
                        if (maskVal > 40) {
                            // Check boundary proximity
                            var isEdge = false
                            for (dy in -rimDist..rimDist) {
                                val ny = y + dy
                                if (ny in 0 until h) {
                                    val nrow = ny * w
                                    for (dx in -rimDist..rimDist) {
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
                                val rimAlpha = (maskVal * config.puddleRingDarkness).roundToInt().coerceIn(0, 255)
                                val rimPx = (rimAlpha shl 24) or (drInt shl 16) or (dgInt shl 8) or dbInt
                                puddlePixels[idx] = rimPx
                                composite[idx] = LayerBlendCompositor.blendPixel(
                                    composite[idx],
                                    rimPx,
                                    BlendModeType.Multiply,
                                    1.0f
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Procedural Cold-Press Paper Granulation
        if (config.granulation > 0.05f) {
            for (i in 0 until total) {
                val basePx = washPixels[i]
                val maskVal = (basePx ushr 24) and 0xFF
                if (maskVal > 12) {
                    val x = i % w
                    val y = i / w

                    // Multi-frequency cold-press paper grain lattice
                    val g1 = sin(x * 1.25f + y * 0.75f) * cos(x * 0.75f - y * 1.25f)
                    val g2 = sin(x * 2.10f - y * 1.80f) * 0.5f
                    val grain = ((g1 + g2 + 1.5f) / 3.0f).coerceIn(0f, 1f)

                    // Pigment settling in grain valleys
                    val grainMod = (grain - 0.5f) * config.granulation
                    val grey = (128 + grainMod * 120f).roundToInt().coerceIn(0, 255)
                    val gAlpha = (maskVal * config.granulation * 0.70f).roundToInt().coerceIn(0, 255)

                    val gPx = (gAlpha shl 24) or (grey shl 16) or (grey shl 8) or grey
                    granulationPixels[i] = gPx

                    composite[i] = LayerBlendCompositor.blendPixel(
                        composite[i],
                        gPx,
                        BlendModeType.Multiply,
                        config.granulation
                    )
                }
            }
        }

        // 4. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(StyleLayerOutput(name = "Translucent Wash", role = LayerRole.BASE_FLAT, pixels = washPixels, blendMode = BlendModeType.Normal, opacity = config.washWetness))
        if (config.puddleRingDarkness > 0.05f) {
            layers.add(StyleLayerOutput(name = "Wet-Edge Puddle Rings", role = LayerRole.SHADOW, pixels = puddlePixels, blendMode = BlendModeType.Multiply, opacity = config.puddleRingDarkness))
        }
        if (config.granulation > 0.05f) {
            layers.add(StyleLayerOutput(name = "Paper Granulation", role = LayerRole.TEXTURE, pixels = granulationPixels, blendMode = BlendModeType.Multiply, opacity = config.granulation))
        }

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
            name = "Watercolour Recipe",
            styleId = ColorStyleId.WATERCOLOUR,
            stages = listOf(
                BaseFillStageConfig(name = "Translucent Wash", fillType = BaseFillStageConfig.BaseFillType.SOFT_TINT),
                MediumTextureStageConfig(
                    name = "Paper Granulation",
                    mediumType = MediumTextureStageConfig.MediumTextureType.PAPER_GRAIN,
                    roughness = config.granulation,
                    opacity = config.granulation
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
