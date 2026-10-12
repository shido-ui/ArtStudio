package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import kotlin.math.*

/**
 * Strategy implementation for Style 15: Monochrome.
 *
 * Transforms source region colors into calibrated ITU-R BT.709 perceived luminance
 * with tonal contrast grading, Sumi-e ink wash toning, and deep shadow pools.
 * The original RGB source palette and region colors remain untouched in the underlying
 * data models, enabling seamless non-destructive recipe switching back to full colour.
 *
 * NOTE ON SIMULATION FIDELITY:
 * This strategy produces an analytical tonal transform with tonal curve grading and
 * optional ink wash tinting. It is a mathematical color-space transform, NOT a physical
 * carbon-stick ink grinding or fibrous Xuan paper capillary absorption simulation.
 */
object MonochromeStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.MONOCHROME
    override val displayName: String = "Monochrome"
    override val description: String =
        "Calibrated Rec.709 perceived luminance tonal transform with S-curve contrast and Sumi-e ink wash toning while preserving original palette data"

    override val supportedInputModes: List<String> = listOf("Line Art", "Flat Colour", "Full Colour References")
    override val documentedLimitations: String =
        "Flattens chromatic hue separation into pure tonal values. Distinct colours with identical perceived luminance (e.g., pure red and pure green) will render to similar grayscale values unless adjusted via contrast curves."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? MonochromeConfig ?: MonochromeConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val shadowPixels = IntArray(total)
        val composite = IntArray(total)

        val bp = config.blackPoint
        val wp = config.whitePoint
        val range = max(0.01f, wp - bp)
        val contrast = config.contrast

        // 1. Render Calibrated Tonal Monochrome Base
        for ((regionId, mask) in input.regionMasks) {
            val rawColor = resolveRegionColor(regionId, input, config.sourcePreservation)

            // Calculate Rec. 709 Perceived Luminance: Y = 0.2126 R + 0.7152 G + 0.0722 B
            val lum = (0.2126f * rawColor.red + 0.7152f * rawColor.green + 0.0722f * rawColor.blue).coerceIn(0f, 1f)

            // Apply S-curve contrast and black/white point re-mapping
            val normalizedLum = ((lum - bp) / range).coerceIn(0f, 1f)
            val gradedLum = normalizedLum.toDouble().pow(contrast.toDouble()).toFloat().coerceIn(0f, 1f)

            // Apply tint variants
            var tr = gradedLum
            var tg = gradedLum
            var tb = gradedLum

            when (config.tintMode) {
                MonochromeTintMode.PURE_GRAYSCALE -> {
                    // Exact R == G == B equality
                }
                MonochromeTintMode.COOL_SLATE -> {
                    tb = (tb * 1.08f).coerceIn(0f, 1f)
                    tr = (tr * 0.94f).coerceIn(0f, 1f)
                }
                MonochromeTintMode.WARM_SEPIA -> {
                    tr = (tr * 1.08f).coerceIn(0f, 1f)
                    tg = (tg * 1.02f).coerceIn(0f, 1f)
                    tb = (tb * 0.90f).coerceIn(0f, 1f)
                }
                MonochromeTintMode.HIGH_CONTRAST_NOIR -> {
                    val noirLum = if (gradedLum < 0.50f) gradedLum * 0.70f else 0.50f + (gradedLum - 0.50f) * 1.30f
                    val clamped = noirLum.coerceIn(0f, 1f)
                    tr = clamped; tg = clamped; tb = clamped
                }
            }

            val rInt = (tr * 255f).roundToInt().coerceIn(0, 255)
            val gInt = (tg * 255f).roundToInt().coerceIn(0, 255)
            val bInt = (tb * 255f).roundToInt().coerceIn(0, 255)

            for (i in 0 until total) {
                val maskVal = mask[i].toInt() and 0xFF
                if (maskVal > 12) {
                    val px = (maskVal shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    basePixels[i] = px
                    composite[i] = px
                }
            }
        }

        // 2. Sumi-e Deep Ink Shadow Accent Pools
        for ((regionId, mask) in input.regionMasks) {
            val rawColor = resolveRegionColor(regionId, input, config.sourcePreservation)
            val lum = (0.2126f * rawColor.red + 0.7152f * rawColor.green + 0.0722f * rawColor.blue).coerceIn(0f, 1f)

            // Darker regions get ink density pooling
            if (lum < 0.40f) {
                val poolStrength = (1.0f - lum / 0.40f) * 0.50f
                for (i in 0 until total) {
                    val maskVal = mask[i].toInt() and 0xFF
                    if (maskVal > 40) {
                        val shAlpha = (maskVal * poolStrength).roundToInt().coerceIn(0, 255)
                        val shPx = (shAlpha shl 24) // Pure black ink pool
                        shadowPixels[i] = shPx
                        composite[i] = LayerBlendCompositor.blendPixel(
                            composite[i],
                            shPx,
                            BlendModeType.Multiply,
                            1.0f
                        )
                    }
                }
            }
        }

        // 3. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(
            StyleLayerOutput(
                name = "Monochrome Tonal Base",
                role = LayerRole.BASE_FLAT,
                pixels = basePixels,
                blendMode = BlendModeType.Normal,
                opacity = 1.0f
            )
        )
        layers.add(
            StyleLayerOutput(
                name = "Sumi-e Deep Shadow Pools",
                role = LayerRole.SHADOW,
                pixels = shadowPixels,
                blendMode = BlendModeType.Multiply,
                opacity = 0.50f
            )
        )

        // 4. Composite Preserved Linework
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
            name = "Monochrome Recipe",
            styleId = ColorStyleId.MONOCHROME,
            stages = listOf(
                PaletteTransformStageConfig(
                    name = "Rec.709 Luminance Transform",
                    transformType = PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE,
                    strength = 1.0f
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
