package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.cv.palette.perceivedLuminance
import com.artstudio.document.model.BlendModeType
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 9: Manga Screentone.
 *
 * Produces authentic halftone dot patterns and line screens at calibrated angles,
 * mapping region luminance to mathematical dot coverage for traditional manga shading.
 */
object MangaScreentoneStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.MANGA_SCREENTONE
    override val displayName: String = "Manga Screentone"
    override val description: String = "Halftone dot patterns and line screens at calibrated angles for comic and manga shading"

    override val supportedInputModes: List<String> = listOf("Line Art", "Manga Inking", "Grayscale Tones")
    override val documentedLimitations: String =
        "Photographic noise creates erratic dot clustering and severe moiré patterns when screentone frequency beats against sensor grain; requires pre-filtering or posterization."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? MangaScreentoneConfig ?: MangaScreentoneConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val tonePixels = IntArray(total)
        val composite = IntArray(total)

        val rad = Math.toRadians(config.screenAngleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val pitch = config.screenPitchPx.coerceIn(2.0f, 20.0f)
        val halfPitch = pitch * 0.5f
        val pitchSq = halfPitch * halfPitch

        // 1. Render Tonal Base Flats
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

        // 2. Render Mathematical Screentone Halftone Pattern
        for ((regionId, mask) in input.regionMasks) {
            val baseColor = resolveRegionColor(regionId, input, config.sourcePreservation)
            val lum = baseColor.perceivedLuminance
            // Darker regions have larger dots
            var targetCoverage = (1.0f - lum) * config.densityScale
            if (config.invertTones) targetCoverage = 1.0f - targetCoverage
            targetCoverage = targetCoverage.coerceIn(0.02f, 0.98f)

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 12) {
                        // Rotated screen coordinates
                        val u = x * cosA + y * sinA
                        val v = -x * sinA + y * cosA

                        val cu = ((u % pitch + pitch) % pitch) - halfPitch
                        val cv = ((v % pitch + pitch) % pitch) - halfPitch

                        val isDot = when (config.dotShape) {
                            ScreentoneDotShape.ROUND_DOT -> {
                                val dSq = (cu * cu + cv * cv) / pitchSq
                                dSq <= targetCoverage
                            }
                            ScreentoneDotShape.ELLIPSE -> {
                                val dSq = (cu * cu * 0.7f + cv * cv * 1.4f) / pitchSq
                                dSq <= targetCoverage
                            }
                            ScreentoneDotShape.LINE_SCREEN -> {
                                val d = abs(cu) / halfPitch
                                d <= targetCoverage
                            }
                        }

                        if (isDot) {
                            val dotAlpha = maskVal // Solid ink black dot
                            val dotPx = (dotAlpha shl 24) or (0x12 shl 16) or (0x12 shl 8) or 0x12
                            tonePixels[idx] = dotPx
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                dotPx,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        }
                    }
                }
            }
        }

        // 3. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(StyleLayerOutput(name = "Tonal Base Flats", role = LayerRole.BASE_FLAT, pixels = basePixels, blendMode = BlendModeType.Normal))
        layers.add(StyleLayerOutput(name = "Halftone Screentone Pattern", role = LayerRole.TEXTURE, pixels = tonePixels, blendMode = BlendModeType.Multiply))

        // 4. Composite Preserved Linework
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
            name = "Manga Screentone Recipe",
            styleId = ColorStyleId.MANGA_SCREENTONE,
            stages = listOf(
                BaseFillStageConfig(name = "Tonal Base Flats"),
                MediumTextureStageConfig(
                    name = "Halftone Screentones",
                    mediumType = MediumTextureStageConfig.MediumTextureType.SCREENTONE_DOTS,
                    scale = config.screenPitchPx,
                    roughness = config.densityScale
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
            memoryAllocatedBytes = total * 12L,
            recipeSnapshot = recipe
        )
    }
}
