package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import kotlin.math.*

/**
 * Strategy implementation for Style 12: Gouache.
 *
 * Simulates opaque, non-reflective matte poster paint with crisp planar brushwork steps,
 * chalky velvety finish, and drying rim contours.
 *
 * NOTE ON SIMULATION FIDELITY:
 * This strategy produces simulated artistic medium effects via discrete planar quantization,
 * diffuse matte attenuation, and edge rim contrast. It is an optical graphic approximation,
 * NOT a physical fluid or chemical drying simulation of gouache pigment suspensions and gum arabic binders.
 */
object GouacheStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.GOUACHE
    override val displayName: String = "Gouache"
    override val description: String =
        "Matte opaque coverage with crisp planar brushwork and velvety chalky finish (simulated medium effect, not a physical pigment suspension simulation)"

    override val supportedInputModes: List<String> = listOf("Line Art", "Flat Colour", "Graphic Posters")
    override val documentedLimitations: String =
        "Planar step quantization collapses continuous photographic gradients into discrete graphic bands. Clean region boundaries are required to anchor planar facet steps and drying edge rims."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? GouacheConfig ?: GouacheConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val planarPixels = IntArray(total)
        val chalkyPixels = IntArray(total)
        val composite = IntArray(total)

        val lightAngleDeg = 45.0f
        val rad = Math.toRadians(lightAngleDeg.toDouble())
        val lx = cos(rad).toFloat()
        val ly = sin(rad).toFloat()

        // 1. Render Matte Opaque Base Fills
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
            val rInt = baseColor.redInt
            val gInt = baseColor.greenInt
            val bInt = baseColor.blueInt

            for (i in 0 until total) {
                val maskVal = mask[i].toInt() and 0xFF
                if (maskVal > 12) {
                    val alpha = (maskVal * config.matteOpacity).roundToInt().coerceIn(0, 255)
                    val px = (alpha shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    basePixels[i] = px
                    composite[i] = px
                }
            }
        }

        // 2. Synthesize Planar Shading Steps (Discrete Graphic Facets)
        val steps = config.planarStepCount.coerceIn(2, 8)
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)

            var minU = Float.MAX_VALUE
            var maxU = -Float.MAX_VALUE
            var count = 0

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if ((mask[row + x].toInt() and 0xFF) > 128) {
                        val u = x * lx + y * ly
                        if (u < minU) minU = u
                        if (u > maxU) maxU = u
                        count++
                    }
                }
            }
            if (count == 0 || maxU <= minU) continue

            val rangeU = max(1.0f, maxU - minU)

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 40) {
                        val u = x * lx + y * ly
                        val normU = ((u - minU) / rangeU).coerceIn(0f, 1f)
                        val stepIndex = floor(normU * steps).toInt().coerceIn(0, steps - 1)

                        // Stepped multiplier
                        val stepMult = 0.80f + 0.35f * (stepIndex.toFloat() / (steps - 1))
                        val pr = (baseColor.red * stepMult * 255f).roundToInt().coerceIn(0, 255)
                        val pg = (baseColor.green * stepMult * 255f).roundToInt().coerceIn(0, 255)
                        val pb = (baseColor.blue * stepMult * 255f).roundToInt().coerceIn(0, 255)

                        val planAlpha = (maskVal * 0.85f).roundToInt().coerceIn(0, 255)
                        val planPx = (planAlpha shl 24) or (pr shl 16) or (pg shl 8) or pb
                        planarPixels[idx] = planPx
                        composite[idx] = planPx
                    }
                }
            }
        }

        // 3. Chalky Matte Finish & Edge Drying Darkening
        val rimDist = 2
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
            val dryMult = (1.0f - config.edgeDryDarkening * 0.30f).coerceIn(0.5f, 1.0f)
            val dr = (baseColor.red * dryMult * 255f).roundToInt().coerceIn(0, 255)
            val dg = (baseColor.green * dryMult * 255f).roundToInt().coerceIn(0, 255)
            val db = (baseColor.blue * dryMult * 255f).roundToInt().coerceIn(0, 255)

            var lcg = config.seed xor (regionId.hashCode().toLong())

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 40) {
                        // Check drying rim
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

                        // Chalky powder noise
                        lcg = lcg * 6364136223846793005L + 1442695040888963407L
                        val noise = ((lcg ushr 48) and 0xFFFF).toFloat() / 65535.0f
                        val powderDelta = ((noise - 0.5f) * config.chalkyFinish * 40f).roundToInt()

                        if (isEdge && config.edgeDryDarkening > 0.05f) {
                            val rimAlpha = (maskVal * config.edgeDryDarkening * 0.70f).roundToInt().coerceIn(0, 255)
                            val rimPx = (rimAlpha shl 24) or (dr shl 16) or (dg shl 8) or db
                            chalkyPixels[idx] = rimPx
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                rimPx,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        } else if (config.chalkyFinish > 0.05f) {
                            val cr = (baseColor.redInt + powderDelta).coerceIn(0, 255)
                            val cg = (baseColor.greenInt + powderDelta).coerceIn(0, 255)
                            val cb = (baseColor.blueInt + powderDelta).coerceIn(0, 255)
                            val chalkAlpha = (maskVal * config.chalkyFinish * 0.50f).roundToInt().coerceIn(0, 255)
                            val chalkPx = (chalkAlpha shl 24) or (cr shl 16) or (cg shl 8) or cb
                            chalkyPixels[idx] = chalkPx
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                chalkPx,
                                BlendModeType.Overlay,
                                0.35f
                            )
                        }
                    }
                }
            }
        }

        // 4. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(
            StyleLayerOutput(
                name = "Matte Base Fills",
                role = LayerRole.BASE_FLAT,
                pixels = basePixels,
                blendMode = BlendModeType.Normal,
                opacity = config.matteOpacity
            )
        )
        layers.add(
            StyleLayerOutput(
                name = "Planar Shading Facets",
                role = LayerRole.SHADOW,
                pixels = planarPixels,
                blendMode = BlendModeType.Normal,
                opacity = 1.0f
            )
        )
        if (config.edgeDryDarkening > 0.05f || config.chalkyFinish > 0.05f) {
            layers.add(
                StyleLayerOutput(
                    name = "Chalky Velvet Finish & Rims",
                    role = LayerRole.TEXTURE,
                    pixels = chalkyPixels,
                    blendMode = BlendModeType.Multiply,
                    opacity = max(config.edgeDryDarkening, config.chalkyFinish)
                )
            )
        }

        // 5. Composite Preserved Linework
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
            name = "Gouache Recipe",
            styleId = ColorStyleId.GOUACHE,
            stages = listOf(
                BaseFillStageConfig(name = "Opaque Matte Fill", fillType = BaseFillStageConfig.BaseFillType.FLAT),
                CelShadowStageConfig(name = "Planar Step Shading", shadowDarkness = 0.35f, lightAngleDeg = lightAngleDeg)
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
