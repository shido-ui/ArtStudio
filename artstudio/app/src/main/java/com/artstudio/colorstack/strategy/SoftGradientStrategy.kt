package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import java.util.UUID
import kotlin.math.*

/**
 * Strategy implementation for Style 2: Soft Gradient Shading.
 *
 * Produces continuous smooth ambient gradients and directional lighting
 * without hard step boundaries.
 */
object SoftGradientStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.SOFT_GRADIENT
    override val displayName: String = "Soft Gradient Shading"
    override val description: String = "Smooth continuous ambient lighting and directional gradients without harsh step lines"

    override val supportedInputModes: List<String> = listOf("Line Art", "Flat Colour", "Tonal Sketches")
    override val documentedLimitations: String =
        "Unsegmented photographic references or noisy gradients can create diffuse, muddy tonal transitions; optimal results require clean region separation."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? SoftGradientConfig ?: SoftGradientConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val gradientPixels = IntArray(total)
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

        // 2. Render Soft Gradient Shadows (Multiply)
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

            // Target shadow tint
            val mult = (1.0f - config.shadowRatio * 0.6f).coerceIn(0.1f, 1.0f)
            val srInt = (baseColor.red * mult * 255f).roundToInt().coerceIn(0, 255)
            val sgInt = (baseColor.green * mult * 255f).roundToInt().coerceIn(0, 255)
            val sbInt = (baseColor.blue * mult * 255f).roundToInt().coerceIn(0, 255)

            for (y in minY..maxY) {
                val row = y * w
                for (x in minX..maxX) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 12) {
                        val dx = (x - cx) / radius
                        val dy = (y - cy) / radius
                        val proj = dx * sx + dy * sy
                        // Continuous smooth projection [0, 1]
                        val u = ((proj + 1.0f) * 0.5f).coerceIn(0f, 1f)
                        // Power-law falloff
                        val falloff = u.pow(config.softness)

                        // Ambient edge bleed attenuation
                        val edgeWeight = (maskVal / 255.0f).pow(1.0f + config.ambientBleed)
                        val shadowIntensity = falloff * config.shadowRatio * edgeWeight

                        if (shadowIntensity > 0.01f) {
                            val effA = (shadowIntensity * 255f).roundToInt().coerceIn(0, 255)
                            val px = (effA shl 24) or (srInt shl 16) or (sgInt shl 8) or sbInt
                            gradientPixels[idx] = px
                            composite[idx] = LayerBlendCompositor.blendPixel(
                                composite[idx],
                                px,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        }
                    }
                }
            }
        }

        // 3. Composite Independent Linework
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(StyleLayerOutput(name = "Base Flats", role = LayerRole.BASE_FLAT, pixels = basePixels, blendMode = BlendModeType.Normal))
        layers.add(StyleLayerOutput(name = "Soft Gradients", role = LayerRole.GRADIENT, pixels = gradientPixels, blendMode = BlendModeType.Multiply))

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
            name = "Soft Gradient Shading Recipe",
            styleId = ColorStyleId.SOFT_GRADIENT,
            stages = listOf(
                BaseFillStageConfig(name = "Base Flats"),
                GradientStageConfig(
                    name = "Soft Gradients",
                    softness = config.softness,
                    shadowRatio = config.shadowRatio,
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
            memoryAllocatedBytes = total * 12L,
            recipeSnapshot = recipe
        )
    }
}
