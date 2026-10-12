package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import java.util.Random
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 5: Painterly.
 *
 * Produces multi-tonal underpainting with localized color jitter, directional
 * bristle streaks, wet-edge blending diffusion, and procedural canvas tooth.
 */
object PainterlyStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.PAINTERLY
    override val displayName: String = "Painterly"
    override val description: String = "Multi-tonal brushwork with local color jitter, directional bristle streaks, and canvas tooth paper modulation"

    override val supportedInputModes: List<String> = listOf("Line Art", "Flat Colour", "Tonal Underpaintings", "Rough Sketches")
    override val documentedLimitations: String =
        "Fine vector linework may be partially softened or blended into surrounding impasto brush strokes; adjust brush scale or linework opacity to preserve sharp details."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? PainterlyConfig ?: PainterlyConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val bristlePixels = IntArray(total)
        val toothPixels = IntArray(total)
        val composite = IntArray(total)

        // Seeded random for deterministic reproducibility
        val rng = Random(config.seed)

        // 1. Render Base Flats & Underpainting with Color Jitter
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 12) {
                        // Deterministic pseudo-random jitter based on coordinates + seed
                        val hash = ((x * 73856093L xor y * 19349663L xor config.seed) and 0x7FFFFFFF).toInt()
                        val jitterR = ((hash and 0xFF) / 255.0f - 0.5f) * 2.0f * config.colorJitter
                        val jitterG = (((hash ushr 8) and 0xFF) / 255.0f - 0.5f) * 2.0f * config.colorJitter
                        val jitterB = (((hash ushr 16) and 0xFF) / 255.0f - 0.5f) * 2.0f * config.colorJitter

                        val r = (baseColor.red + jitterR).coerceIn(0f, 1f)
                        val g = (baseColor.green + jitterG).coerceIn(0f, 1f)
                        val b = (baseColor.blue + jitterB).coerceIn(0f, 1f)

                        val rInt = (r * 255f).roundToInt().coerceIn(0, 255)
                        val gInt = (g * 255f).roundToInt().coerceIn(0, 255)
                        val bInt = (b * 255f).roundToInt().coerceIn(0, 255)

                        val px = (maskVal shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                        basePixels[idx] = px
                        composite[idx] = px
                    }
                }
            }
        }

        // 2. Bristle Brushwork Streaks
        val scale = config.brushScale.coerceIn(0.5f, 3.0f)
        for (i in 0 until total) {
            val basePx = basePixels[i]
            val maskVal = (basePx ushr 24) and 0xFF
            if (maskVal > 12) {
                val x = i % w
                val y = i / w

                // Multi-frequency directional bristle stroke
                val streak1 = sin((x * 0.75f + y * 0.45f) / scale)
                val streak2 = cos((x * 0.35f - y * 0.65f) / scale)
                val bristleVal = (streak1 * 0.6f + streak2 * 0.4f) * 0.18f

                val baseR = ((basePx ushr 16) and 0xFF) / 255.0f
                val baseG = ((basePx ushr 8) and 0xFF) / 255.0f
                val baseB = (basePx and 0xFF) / 255.0f

                val modR = (baseR + bristleVal).coerceIn(0f, 1f)
                val modG = (baseG + bristleVal).coerceIn(0f, 1f)
                val modB = (baseB + bristleVal).coerceIn(0f, 1f)

                val rInt = (modR * 255f).roundToInt().coerceIn(0, 255)
                val gInt = (modG * 255f).roundToInt().coerceIn(0, 255)
                val bInt = (modB * 255f).roundToInt().coerceIn(0, 255)

                val bristleAlpha = (maskVal * config.blendingSoftness).roundToInt().coerceIn(0, 255)
                val bPx = (bristleAlpha shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                bristlePixels[i] = bPx

                composite[i] = LayerBlendCompositor.blendPixel(
                    composite[i],
                    bPx,
                    BlendModeType.Normal,
                    config.blendingSoftness
                )
            }
        }

        // 3. Procedural Canvas Tooth Texture
        if (config.canvasTooth > 0.02f) {
            for (i in 0 until total) {
                val basePx = basePixels[i]
                val maskVal = (basePx ushr 24) and 0xFF
                if (maskVal > 12) {
                    val x = i % w
                    val y = i / w

                    // High frequency canvas paper weave
                    val weave = (sin(x * 1.57f) * cos(y * 1.57f) + 1.0f) * 0.5f // [0, 1]
                    val toothMod = (weave - 0.5f) * 2.0f * config.canvasTooth // [-tooth, +tooth]

                    // Represented as neutral grey overlay/softlight tooth
                    val grey = (128 + toothMod * 100f).roundToInt().coerceIn(0, 255)
                    val toothAlpha = (maskVal * config.canvasTooth * 0.75f).roundToInt().coerceIn(0, 255)
                    val tPx = (toothAlpha shl 24) or (grey shl 16) or (grey shl 8) or grey
                    toothPixels[i] = tPx

                    composite[i] = LayerBlendCompositor.blendPixel(
                        composite[i],
                        tPx,
                        BlendModeType.SoftLight,
                        config.canvasTooth
                    )
                }
            }
        }

        // 4. Assemble Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(StyleLayerOutput(name = "Underpainting Base", role = LayerRole.BASE_FLAT, pixels = basePixels, blendMode = BlendModeType.Normal))
        layers.add(StyleLayerOutput(name = "Bristle Brushwork", role = LayerRole.TEXTURE, pixels = bristlePixels, blendMode = BlendModeType.Normal, opacity = config.blendingSoftness))
        if (config.canvasTooth > 0.02f) {
            layers.add(StyleLayerOutput(name = "Canvas Paper Tooth", role = LayerRole.TEXTURE, pixels = toothPixels, blendMode = BlendModeType.SoftLight, opacity = config.canvasTooth))
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
            name = "Painterly Recipe",
            styleId = ColorStyleId.PAINTERLY,
            stages = listOf(
                BaseFillStageConfig(name = "Underpainting Base"),
                MediumTextureStageConfig(
                    name = "Painterly Bristle Texture",
                    mediumType = MediumTextureStageConfig.MediumTextureType.CANVAS_TOOTH,
                    roughness = config.canvasTooth,
                    opacity = config.blendingSoftness
                ),
                FinishingEffectStageConfig(
                    name = "Canvas Tooth Finishing",
                    effectType = FinishingEffectStageConfig.FinishingEffectType.POSTERIZE,
                    bloomIntensity = 0f
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
