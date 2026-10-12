package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import kotlin.math.*

/**
 * Strategy implementation for Style 13: Pastel Palette.
 *
 * Simulates soft chalky pastel pigment with delicate tint lifting, crumbly particle scattering,
 * heavy paper tooth valley skipping, and velvety texture.
 *
 * NOTE ON SIMULATION FIDELITY:
 * This strategy produces simulated artistic medium effects via tonal pastel curve lifting,
 * stochastic crumb dispersion, and paper tooth threshold skipping. It is an optical texture
 * approximation, NOT a physical particle-dynamics or friable binder friction simulation.
 */
object PastelStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.PASTEL
    override val displayName: String = "Pastel Palette"
    override val description: String =
        "Soft chalky pigment with low-saturation pastel tints and textured paper tooth (simulated medium effect, not a particle friction simulation)"

    override val supportedInputModes: List<String> = listOf("Line Art", "Pencil Contours", "Pastel Underdrawings")
    override val documentedLimitations: String =
        "Pastel tint lifting lightens deep blacks and dark tones into soft pastel values. Photographic inputs with extreme dynamic range will have compressed shadow contrasts."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? PastelConfig ?: PastelConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val toothPixels = IntArray(total)
        val crumbPixels = IntArray(total)
        val composite = IntArray(total)

        // 1. Render Pastel-Lifted Soft Chalk Base
        val lift = config.pastelTintLift
        for ((regionId, mask) in input.regionMasks) {
            val rawColor = resolveRegionColor(regionId, input, config.sourcePreservation)

            // Lift luminance and soften saturation towards pastel tint
            val pr = (rawColor.red * (1.0f - lift) + lift * 0.94f).coerceIn(0f, 1f)
            val pg = (rawColor.green * (1.0f - lift) + lift * 0.94f).coerceIn(0f, 1f)
            val pb = (rawColor.blue * (1.0f - lift) + lift * 0.94f).coerceIn(0f, 1f)

            val rInt = (pr * 255f).roundToInt().coerceIn(0, 255)
            val gInt = (pg * 255f).roundToInt().coerceIn(0, 255)
            val bInt = (pb * 255f).roundToInt().coerceIn(0, 255)

            for (i in 0 until total) {
                val maskVal = mask[i].toInt() and 0xFF
                if (maskVal > 12) {
                    val px = (maskVal shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    basePixels[i] = px
                    composite[i] = px
                }
            }
        }

        // 2. Synthesize Paper Tooth Valley Skipping & Crumb Dispersion
        val toothFreq = 0.55
        val skipThreshold = config.paperToothDepth * 0.45f

        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val idx = row + x
                val basePx = basePixels[idx]
                val baseA = (basePx ushr 24) and 0xFF
                if (baseA < 20) continue

                // Paper tooth elevation lattice
                val toothWave = sin(x * toothFreq) * cos(y * toothFreq)
                val toothElevation = (toothWave * 0.5 + 0.5).toFloat()

                // Tooth skipping in deep valleys
                if (toothElevation < skipThreshold) {
                    val skipFactor = (1.0f - (skipThreshold - toothElevation) / skipThreshold).coerceIn(0.1f, 1.0f)
                    val toothAlpha = (baseA * (1.0f - skipFactor) * 0.70f).roundToInt().coerceIn(0, 255)
                    // Paper white tooth reveal (Screen / Lighten)
                    val toothPx = (toothAlpha shl 24) or 0x00FFFFFF
                    toothPixels[idx] = toothPx
                }

                // Stochastic crumb particle scattering
                var lcg = config.seed xor (idx.toLong() * 6364136223846793005L + 1442695040888963407L)
                val crumbRoll = ((lcg ushr 48) and 0xFFFF).toFloat() / 65535.0f

                if (crumbRoll < 0.12f && config.chalkDustStrength > 0.05f) {
                    val delta = if (crumbRoll < 0.06f) 35 else -30
                    val br = ((basePx ushr 16) and 0xFF) + delta
                    val bg = ((basePx ushr 8) and 0xFF) + delta
                    val bb = (basePx and 0xFF) + delta

                    val crInt = br.coerceIn(0, 255)
                    val cgInt = bg.coerceIn(0, 255)
                    val cbInt = bb.coerceIn(0, 255)

                    val crumbAlpha = (baseA * config.chalkDustStrength * 0.60f).roundToInt().coerceIn(0, 255)
                    crumbPixels[idx] = (crumbAlpha shl 24) or (crInt shl 16) or (cgInt shl 8) or cbInt
                }
            }
        }

        // 3. Blend tooth and crumb passes onto composite
        for (i in 0 until total) {
            val tPx = toothPixels[i]
            if (((tPx ushr 24) and 0xFF) > 0) {
                composite[i] = LayerBlendCompositor.blendPixel(
                    composite[i],
                    tPx,
                    BlendModeType.Multiply,
                    0.45f
                )
            }
            val cPx = crumbPixels[i]
            if (((cPx ushr 24) and 0xFF) > 0) {
                composite[i] = LayerBlendCompositor.blendPixel(
                    composite[i],
                    cPx,
                    BlendModeType.Normal,
                    0.50f
                )
            }
        }

        // 4. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(
            StyleLayerOutput(
                name = "Pastel Chalk Base",
                role = LayerRole.BASE_FLAT,
                pixels = basePixels,
                blendMode = BlendModeType.Normal,
                opacity = 1.0f
            )
        )
        layers.add(
            StyleLayerOutput(
                name = "Paper Tooth Grain",
                role = LayerRole.TEXTURE,
                pixels = toothPixels,
                blendMode = BlendModeType.Multiply,
                opacity = config.paperToothDepth
            )
        )
        if (config.chalkDustStrength > 0.05f) {
            layers.add(
                StyleLayerOutput(
                    name = "Chalk Crumb Stippling",
                    role = LayerRole.TEXTURE,
                    pixels = crumbPixels,
                    blendMode = BlendModeType.Normal,
                    opacity = config.chalkDustStrength
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
            name = "Pastel Recipe",
            styleId = ColorStyleId.PASTEL,
            stages = listOf(
                BaseFillStageConfig(name = "Pastel Chalk Base", fillType = BaseFillStageConfig.BaseFillType.SOFT_TINT),
                MediumTextureStageConfig(
                    name = "Ingres Paper Tooth",
                    mediumType = MediumTextureStageConfig.MediumTextureType.PAPER_GRAIN,
                    roughness = config.paperToothDepth,
                    opacity = 0.50f
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
