package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 8: Marker.
 *
 * Simulates translucent overlapping alcohol ink bands with chisel-tip edges,
 * optical multiply darkening on stroke overlaps, and capillary edge bleed.
 */
object MarkerStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.MARKER
    override val displayName: String = "Marker"
    override val description: String = "Translucent alcohol ink bands with chisel-tip overlap tracks and capillary edge bleed"

    override val supportedInputModes: List<String> = listOf("Line Art", "Vector Inking", "Concept Sketches")
    override val documentedLimitations: String =
        "Continuous photographic shading is broken into artificial broad chisel tracks; requires clean flat planes to simulate physical marker layering."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? MarkerConfig ?: MarkerConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val overlapPixels = IntArray(total)
        val bleedPixels = if (config.capillaryBleedPx > 0) IntArray(total) else null
        val composite = IntArray(total)

        val rad = Math.toRadians(config.strokeAngleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val bandWidth = config.bandWidthPx.coerceIn(4.0f, 32.0f)

        // 1. Render Base Marker Flats
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
            val rInt = baseColor.redInt
            val gInt = baseColor.greenInt
            val bInt = baseColor.blueInt

            for (i in 0 until total) {
                val maskVal = mask[i].toInt() and 0xFF
                if (maskVal > 12) {
                    val px = (maskVal shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    basePixels[i] = px
                    composite[i] = px
                }
            }
        }

        // 2. Render Overlapping Chisel Stroke Bands
        val overlapThreshold = bandWidth * 0.16f
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
            val mult = (1.0f - config.overlapDarkening * 0.70f).coerceIn(0.1f, 1.0f)
            val drInt = (baseColor.red * mult * 255f).roundToInt().coerceIn(0, 255)
            val dgInt = (baseColor.green * mult * 255f).roundToInt().coerceIn(0, 255)
            val dbInt = (baseColor.blue * mult * 255f).roundToInt().coerceIn(0, 255)

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 12) {
                        val proj = x * cosA + y * sinA
                        val rem = (proj % bandWidth + bandWidth) % bandWidth

                        // Overlap streak where adjacent passes layer up
                        if (rem < overlapThreshold) {
                            val falloff = (1.0f - rem / overlapThreshold).coerceIn(0f, 1f)
                            val effA = (maskVal * config.overlapDarkening * falloff).roundToInt().coerceIn(0, 255)
                            val opx = (effA shl 24) or (drInt shl 16) or (dgInt shl 8) or dbInt
                            overlapPixels[idx] = opx
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                opx,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        }
                    }
                }
            }
        }

        // 3. Render Capillary Edge Bleed
        if (config.capillaryBleedPx > 0 && bleedPixels != null) {
            val bleedRadius = config.capillaryBleedPx.coerceIn(1, 3)
            for ((regionId, mask) in input.regionMasks) {
                val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
                val rInt = baseColor.redInt
                val gInt = baseColor.greenInt
                val bInt = baseColor.blueInt

                for (y in 0 until h) {
                    val row = y * w
                    for (x in 0 until w) {
                        val idx = row + x
                        val maskVal = mask[idx].toInt() and 0xFF
                        if (maskVal <= 12) {
                            // Check if near inside edge
                            var maxNeighbor = 0
                            for (dy in -bleedRadius..bleedRadius) {
                                val ny = y + dy
                                if (ny in 0 until h) {
                                    val nrow = ny * w
                                    for (dx in -bleedRadius..bleedRadius) {
                                        val nx = x + dx
                                        if (nx in 0 until w) {
                                            val nv = mask[nrow + nx].toInt() and 0xFF
                                            if (nv > maxNeighbor) maxNeighbor = nv
                                        }
                                    }
                                }
                            }
                            if (maxNeighbor > 128) {
                                val bleedAlpha = (maxNeighbor * 0.35f).roundToInt().coerceIn(0, 255)
                                val bpx = (bleedAlpha shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                                bleedPixels[idx] = bpx
                                composite[idx] = LayerBlendCompositor.blendPixel(
                                    composite[idx],
                                    bpx,
                                    BlendModeType.Normal,
                                    0.5f
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(StyleLayerOutput(name = "Base Marker Fills", role = LayerRole.BASE_FLAT, pixels = basePixels, blendMode = BlendModeType.Normal))
        layers.add(StyleLayerOutput(name = "Chisel Stroke Overlaps", role = LayerRole.SHADOW, pixels = overlapPixels, blendMode = BlendModeType.Multiply, opacity = config.overlapDarkening))
        if (bleedPixels != null) {
            layers.add(StyleLayerOutput(name = "Capillary Edge Bleed", role = LayerRole.TEXTURE, pixels = bleedPixels, blendMode = BlendModeType.Normal, opacity = 0.5f))
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
            name = "Marker Recipe",
            styleId = ColorStyleId.MARKER,
            stages = listOf(
                BaseFillStageConfig(name = "Base Marker Fills"),
                CelShadowStageConfig(name = "Chisel Stroke Overlaps", shadowDarkness = config.overlapDarkening, lightAngleDeg = config.strokeAngleDeg)
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
