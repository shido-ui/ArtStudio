package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 7: Coloured Pencil.
 *
 * Simulates directional wax-based pencil hatching, pressure-sensitive stroke
 * variation, and physical paper grain tooth skipping.
 */
object ColouredPencilStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.COLOURED_PENCIL
    override val displayName: String = "Coloured Pencil"
    override val description: String = "Directional stippled hatching with visible paper grain tooth skipping"

    override val supportedInputModes: List<String> = listOf("Line Art", "Graphite Sketches", "Vector Inking")
    override val documentedLimitations: String =
        "High-frequency photo detail conflicts with pencil stroke pitch, causing unnatural moiré interference between photo noise and stroke lines."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? ColouredPencilConfig ?: ColouredPencilConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val tintPixels = IntArray(total)
        val hatchPixels = IntArray(total)
        val toothPixels = IntArray(total)
        val composite = IntArray(total)

        val rad = Math.toRadians(config.hatchAngleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val spacing = config.strokeSpacingPx.coerceIn(1.0f, 10.0f)

        // 1. Render Soft Underlay Tint
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
            val rInt = baseColor.redInt
            val gInt = baseColor.greenInt
            val bInt = baseColor.blueInt

            for (i in 0 until total) {
                val maskVal = mask[i].toInt() and 0xFF
                if (maskVal > 12) {
                    val tintAlpha = (maskVal * 0.40f).roundToInt().coerceIn(0, 255)
                    val px = (tintAlpha shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    tintPixels[i] = px
                    composite[i] = px
                }
            }
        }

        // 2. Render Directional Pencil Hatching with Tooth Skipping
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
                    if (maskVal > 12) {
                        // Projected coordinate along hatch normal
                        val proj = x * cosA + y * sinA
                        val rem = (proj % spacing + spacing) % spacing
                        val distToCenter = abs(rem - spacing * 0.5f)

                        // Deterministic pressure variance
                        val hash = ((x * 48271L xor y * 16807L xor config.seed) and 0x7FFFFFFF).toInt()
                        val pressureMod = 1.0f + ((hash and 0xFF) / 255.0f - 0.5f) * config.pressureVariation

                        // Paper tooth skipping
                        val tooth = (sin(x * 1.8f) * cos(y * 1.8f) + 1.0f) * 0.5f
                        val skipThreshold = config.grainToothStrength * 0.45f

                        val strokeWidth = 0.55f * pressureMod
                        if (distToCenter < strokeWidth && tooth >= skipThreshold) {
                            val lineFalloff = 1.0f - (distToCenter / strokeWidth)
                            val effA = (maskVal * lineFalloff * (0.65f + 0.35f * tooth)).roundToInt().coerceIn(0, 255)
                            val hpx = (effA shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                            hatchPixels[idx] = hpx
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                hpx,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        }
                    }
                }
            }
        }

        // 3. Render Paper Tooth Grain
        if (config.grainToothStrength > 0.05f) {
            for (i in 0 until total) {
                val maskVal = (composite[i] ushr 24) and 0xFF
                if (maskVal > 12) {
                    val x = i % w
                    val y = i / w

                    val grain = (sin(x * 1.8f) * cos(y * 1.8f) + 1.0f) * 0.5f
                    val toothMod = (grain - 0.5f) * 2.0f * config.grainToothStrength
                    val grey = (128 + toothMod * 80f).roundToInt().coerceIn(0, 255)
                    val tAlpha = (maskVal * config.grainToothStrength * 0.50f).roundToInt().coerceIn(0, 255)

                    val tpx = (tAlpha shl 24) or (grey shl 16) or (grey shl 8) or grey
                    toothPixels[i] = tpx
                    composite[i] = LayerBlendCompositor.blendPixel(
                        composite[i],
                        tpx,
                        BlendModeType.Overlay,
                        config.grainToothStrength
                    )
                }
            }
        }

        // 4. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(StyleLayerOutput(name = "Underlay Tint", role = LayerRole.BASE_FLAT, pixels = tintPixels, blendMode = BlendModeType.Normal, opacity = 0.40f))
        layers.add(StyleLayerOutput(name = "Directional Hatching", role = LayerRole.TEXTURE, pixels = hatchPixels, blendMode = BlendModeType.Multiply))
        if (config.grainToothStrength > 0.05f) {
            layers.add(StyleLayerOutput(name = "Paper Tooth Texture", role = LayerRole.TEXTURE, pixels = toothPixels, blendMode = BlendModeType.Overlay, opacity = config.grainToothStrength))
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
            name = "Coloured Pencil Recipe",
            styleId = ColorStyleId.COLOURED_PENCIL,
            stages = listOf(
                BaseFillStageConfig(name = "Underlay Tint", fillType = BaseFillStageConfig.BaseFillType.SOFT_TINT),
                MediumTextureStageConfig(
                    name = "Pencil Hatching",
                    mediumType = MediumTextureStageConfig.MediumTextureType.PENCIL_HATCH,
                    scale = config.strokeSpacingPx,
                    roughness = config.grainToothStrength
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
