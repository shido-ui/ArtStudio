package com.artstudio.colorstack

import com.artstudio.color.ColorData
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import kotlin.math.*

/**
 * Common interface for executing an individual Colour Stack stage.
 */
interface ColorStageProcessor {
    val stageType: ColorStageType
    fun execute(context: StageExecutionContext, config: ColorStageConfig): StageExecutionOutput
}

/**
 * Processor for [ColorStageType.BASE_FILL].
 * Fills masked regions with assigned source/palette colors.
 */
object BaseFillProcessor : ColorStageProcessor {
    override val stageType: ColorStageType = ColorStageType.BASE_FILL

    override fun execute(context: StageExecutionContext, config: ColorStageConfig): StageExecutionOutput {
        val startTime = System.currentTimeMillis()
        val c = config as? BaseFillStageConfig ?: BaseFillStageConfig()
        val w = context.width
        val h = context.height
        val total = w * h

        val stagePixels = IntArray(total) // Starts transparent (0x00000000)
        val composited = context.backdropPixels.copyOf()

        val targetRegions = c.targetRegionIds ?: context.regionMasks.keys

        for (regionId in targetRegions) {
            val mask = context.regionMasks[regionId] ?: continue
            val colorData = context.regionColors[regionId] ?: ColorData(0.85f, 0.85f, 0.85f, 1.0f)

            var r = colorData.red
            var g = colorData.green
            var b = colorData.blue

            if (c.fillType == BaseFillStageConfig.BaseFillType.PAPER_TONE) {
                // Subtle warm paper wash tone
                r = (r * 1.01f).coerceIn(0f, 1f)
                g = (g * 0.99f).coerceIn(0f, 1f)
                b = (b * 0.96f).coerceIn(0f, 1f)
            }

            val rInt = (r * 255f).roundToInt().coerceIn(0, 255)
            val gInt = (g * 255f).roundToInt().coerceIn(0, 255)
            val bInt = (b * 255f).roundToInt().coerceIn(0, 255)

            for (i in 0 until total) {
                val maskAlpha = mask[i].toInt() and 0xFF
                if (maskAlpha > 12) {
                    val effAlpha = (maskAlpha * c.opacity * c.strength).roundToInt().coerceIn(0, 255)
                    val pixel = (effAlpha shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    stagePixels[i] = pixel
                    composited[i] = LayerBlendCompositor.blendPixel(
                        composited[i],
                        pixel,
                        c.blendMode,
                        1.0f
                    )
                }
            }
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        val mem = total * 8L // stagePixels + composited allocation
        return StageExecutionOutput(
            stageId = c.stageId,
            stageType = stageType,
            stageLayerName = c.name,
            stagePixels = stagePixels,
            compositedBackdrop = composited,
            executionTimeMs = elapsed,
            memoryAllocatedBytes = mem,
            configSnapshot = c
        )
    }
}

/**
 * Processor for [ColorStageType.CEL_SHADOW].
 * Computes crisp 2-tone directional cel shadows based on light angle and region geometry.
 */
object CelShadowProcessor : ColorStageProcessor {
    override val stageType: ColorStageType = ColorStageType.CEL_SHADOW

    override fun execute(context: StageExecutionContext, config: ColorStageConfig): StageExecutionOutput {
        val startTime = System.currentTimeMillis()
        val c = config as? CelShadowStageConfig ?: CelShadowStageConfig()
        val w = context.width
        val h = context.height
        val total = w * h

        val stagePixels = IntArray(total)
        val composited = context.backdropPixels.copyOf()

        val rad = Math.toRadians(c.lightAngleDeg.toDouble())
        // Light vector (lx, ly), shadow direction is opposite (-lx, -ly)
        val sx = -cos(rad).toFloat()
        val sy = -sin(rad).toFloat()

        val targetRegions = c.targetRegionIds ?: context.regionMasks.keys

        for (regionId in targetRegions) {
            val mask = context.regionMasks[regionId] ?: continue
            val baseColor = context.regionColors[regionId] ?: ColorData(0.80f, 0.80f, 0.80f, 1.0f)

            // Compute region bounding box and centroid
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
            val radius = max(10.0f, max((maxX - minX), (maxY - minY)) * 0.5f)

            // Shadow color: base color darkened by shadowDarkness
            val mult = (1.0f - c.shadowDarkness).coerceIn(0.1f, 1.0f)
            var sr = baseColor.red * mult
            var sg = baseColor.green * mult
            var sb = baseColor.blue * mult
            if (c.coolShadowTint) {
                // Indigo/cool anime shadow tint
                sb = (sb * 1.15f).coerceIn(0f, 1f)
                sr = (sr * 0.90f).coerceIn(0f, 1f)
            }

            val srInt = (sr * 255f).roundToInt().coerceIn(0, 255)
            val sgInt = (sg * 255f).roundToInt().coerceIn(0, 255)
            val sbInt = (sb * 255f).roundToInt().coerceIn(0, 255)

            // Threshold for cel shadow cutoff
            val cutoff = (1.0f - 2.0f * c.shadowExtent)

            for (y in minY..maxY) {
                val row = y * w
                for (x in minX..maxX) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 128) {
                        val dx = (x - cx) / radius
                        val dy = (y - cy) / radius
                        val shadowProj = dx * sx + dy * sy

                        if (shadowProj > cutoff) {
                            val effAlpha = ((maskVal / 255f) * c.opacity * c.strength * 255f).roundToInt().coerceIn(0, 255)
                            val px = (effAlpha shl 24) or (srInt shl 16) or (sgInt shl 8) or sbInt
                            stagePixels[idx] = px
                            composited[idx] = LayerBlendCompositor.blendPixel(
                                composited[idx],
                                px,
                                c.blendMode,
                                1.0f
                            )
                        }
                    }
                }
            }
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        return StageExecutionOutput(
            stageId = c.stageId,
            stageType = stageType,
            stageLayerName = c.name,
            stagePixels = stagePixels,
            compositedBackdrop = composited,
            executionTimeMs = elapsed,
            memoryAllocatedBytes = total * 8L,
            configSnapshot = c
        )
    }
}

/**
 * Processor for [ColorStageType.GRADIENT].
 * Smooth directional and ambient lighting gradients.
 */
object GradientProcessor : ColorStageProcessor {
    override val stageType: ColorStageType = ColorStageType.GRADIENT

    override fun execute(context: StageExecutionContext, config: ColorStageConfig): StageExecutionOutput {
        val startTime = System.currentTimeMillis()
        val c = config as? GradientStageConfig ?: GradientStageConfig()
        val w = context.width
        val h = context.height
        val total = w * h

        val stagePixels = IntArray(total)
        val composited = context.backdropPixels.copyOf()

        val rad = Math.toRadians(c.lightAngleDeg.toDouble())
        val lx = cos(rad).toFloat()
        val ly = sin(rad).toFloat()

        val targetRegions = c.targetRegionIds ?: context.regionMasks.keys

        for (regionId in targetRegions) {
            val mask = context.regionMasks[regionId] ?: continue
            val baseColor = context.regionColors[regionId] ?: ColorData(0.80f, 0.80f, 0.80f, 1.0f)

            var minX = w; var maxX = 0; var minY = h; var maxY = 0
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    if ((mask[row + x].toInt() and 0xFF) > 128) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (minX > maxX) continue

            val spanX = max(1.0f, (maxX - minX).toFloat())
            val spanY = max(1.0f, (maxY - minY).toFloat())

            for (y in minY..maxY) {
                val row = y * w
                for (x in minX..maxX) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 128) {
                        val nx = (x - minX) / spanX - 0.5f
                        val ny = (y - minY) / spanY - 0.5f
                        val dot = (nx * lx + ny * ly).coerceIn(-0.5f, 0.5f) + 0.5f // [0, 1]
                        val smooth = dot.pow(c.softness.coerceIn(0.1f, 3.0f))

                        val factor = (1.0f - (1.0f - smooth) * c.shadowRatio).coerceIn(0.2f, 1.0f)
                        val rInt = (baseColor.red * factor * 255f).roundToInt().coerceIn(0, 255)
                        val gInt = (baseColor.green * factor * 255f).roundToInt().coerceIn(0, 255)
                        val bInt = (baseColor.blue * factor * 255f).roundToInt().coerceIn(0, 255)

                        val effAlpha = ((maskVal / 255f) * c.opacity * c.strength * 255f).roundToInt().coerceIn(0, 255)
                        val px = (effAlpha shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                        stagePixels[idx] = px
                        composited[idx] = LayerBlendCompositor.blendPixel(
                            composited[idx],
                            px,
                            c.blendMode,
                            1.0f
                        )
                    }
                }
            }
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        return StageExecutionOutput(
            stageId = c.stageId,
            stageType = stageType,
            stageLayerName = c.name,
            stagePixels = stagePixels,
            compositedBackdrop = composited,
            executionTimeMs = elapsed,
            memoryAllocatedBytes = total * 8L,
            configSnapshot = c
        )
    }
}

/**
 * Processor for [ColorStageType.HIGHLIGHT].
 * Computes specular rim and gloss highlights oriented towards the light source.
 */
object HighlightProcessor : ColorStageProcessor {
    override val stageType: ColorStageType = ColorStageType.HIGHLIGHT

    override fun execute(context: StageExecutionContext, config: ColorStageConfig): StageExecutionOutput {
        val startTime = System.currentTimeMillis()
        val c = config as? HighlightStageConfig ?: HighlightStageConfig()
        val w = context.width
        val h = context.height
        val total = w * h

        val stagePixels = IntArray(total)
        val composited = context.backdropPixels.copyOf()

        val rad = Math.toRadians(c.lightAngleDeg.toDouble())
        val lx = cos(rad).toFloat()
        val ly = sin(rad).toFloat()

        val targetRegions = c.targetRegionIds ?: context.regionMasks.keys

        for (regionId in targetRegions) {
            val mask = context.regionMasks[regionId] ?: continue

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
            val radius = max(10.0f, max((maxX - minX), (maxY - minY)) * 0.5f)
            val cutoff = (1.0f - c.sizeRatio * 2.0f)

            for (y in minY..maxY) {
                val row = y * w
                for (x in minX..maxX) {
                    val idx = row + x
                    val maskVal = mask[idx].toInt() and 0xFF
                    if (maskVal > 128) {
                        val dx = (x - cx) / radius
                        val dy = (y - cy) / radius
                        val proj = dx * lx + dy * ly

                        if (proj > cutoff) {
                            val falloff = ((proj - cutoff) / (1.0f - cutoff)).coerceIn(0f, 1f)
                            val effAlpha = (falloff * c.intensity * c.opacity * c.strength * (maskVal / 255f) * 255f).roundToInt().coerceIn(0, 255)
                            // Pure bright specular highlight (near white with subtle tint)
                            val px = (effAlpha shl 24) or (0xFE shl 16) or (0xFE shl 8) or 0xFE
                            stagePixels[idx] = px
                            composited[idx] = LayerBlendCompositor.blendPixel(
                                composited[idx],
                                px,
                                c.blendMode,
                                1.0f
                            )
                        }
                    }
                }
            }
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        return StageExecutionOutput(
            stageId = c.stageId,
            stageType = stageType,
            stageLayerName = c.name,
            stagePixels = stagePixels,
            compositedBackdrop = composited,
            executionTimeMs = elapsed,
            memoryAllocatedBytes = total * 8L,
            configSnapshot = c
        )
    }
}

/**
 * Processor for [ColorStageType.MEDIUM_TEXTURE].
 * Applies seeded procedural media textures (paper grain, screentone dots, impasto, canvas tooth).
 */
object MediumTextureProcessor : ColorStageProcessor {
    override val stageType: ColorStageType = ColorStageType.MEDIUM_TEXTURE

    override fun execute(context: StageExecutionContext, config: ColorStageConfig): StageExecutionOutput {
        val startTime = System.currentTimeMillis()
        val c = config as? MediumTextureStageConfig ?: MediumTextureStageConfig()
        val w = context.width
        val h = context.height
        val total = w * h

        val stagePixels = IntArray(total)
        val composited = context.backdropPixels.copyOf()
        val seed = c.seed

        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val idx = row + x
                // Deterministic texture generation based on medium type
                val texVal = when (c.mediumType) {
                    MediumTextureStageConfig.MediumTextureType.PAPER_GRAIN -> {
                        val hash = (x * 73856093L xor y * 19349663L xor seed * 83492791L).toInt()
                        val noise = ((hash and 0xFF) / 255.0f - 0.5f) * c.roughness
                        (0.5f + noise).coerceIn(0f, 1f)
                    }
                    MediumTextureStageConfig.MediumTextureType.SCREENTONE_DOTS -> {
                        // 45 degree halftone dot grid
                        val pitch = max(4.0f, 10.0f * c.scale)
                        val u = (x + y) * 0.7071f
                        val v = (y - x) * 0.7071f
                        val du = (u % pitch + pitch) % pitch - pitch * 0.5f
                        val dv = (v % pitch + pitch) % pitch - pitch * 0.5f
                        val dist = sqrt(du * du + dv * dv)
                        val dotR = pitch * 0.28f
                        if (dist < dotR) (0.5f - 0.35f * c.roughness).coerceIn(0f, 1f) else (0.5f + 0.15f * c.roughness).coerceIn(0f, 1f)
                    }
                    MediumTextureStageConfig.MediumTextureType.OIL_IMPASTO -> {
                        val streak = sin(x * 0.15f * c.scale) * cos(y * 0.05f * c.scale)
                        (0.5f + streak * 0.35f * c.roughness).coerceIn(0f, 1f)
                    }
                    MediumTextureStageConfig.MediumTextureType.CANVAS_TOOTH -> {
                        val weave = (sin(x * 0.40f * c.scale) * sin(y * 0.40f * c.scale))
                        (0.5f + weave * 0.25f * c.roughness).coerceIn(0f, 1f)
                    }
                    MediumTextureStageConfig.MediumTextureType.PENCIL_HATCH -> {
                        val hatch = sin((x - y) * 0.50f * c.scale)
                        val hash = (x * 374761393L xor y * 668265263L xor seed).toInt()
                        val stipple = (hash and 0x7F) / 127.0f - 0.5f
                        (0.5f + (hatch * 0.25f + stipple * 0.15f) * c.roughness).coerceIn(0f, 1f)
                    }
                }

                val grayInt = (texVal * 255f).roundToInt().coerceIn(0, 255)
                val alphaInt = (c.opacity * c.strength * 255f).roundToInt().coerceIn(0, 255)
                val px = (alphaInt shl 24) or (grayInt shl 16) or (grayInt shl 8) or grayInt
                stagePixels[idx] = px

                // Only blend where there is active backdrop color
                val backA = (composited[idx] ushr 24) and 0xFF
                if (backA > 0) {
                    composited[idx] = LayerBlendCompositor.blendPixel(
                        composited[idx],
                        px,
                        c.blendMode,
                        1.0f
                    )
                }
            }
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        return StageExecutionOutput(
            stageId = c.stageId,
            stageType = stageType,
            stageLayerName = c.name,
            stagePixels = stagePixels,
            compositedBackdrop = composited,
            executionTimeMs = elapsed,
            memoryAllocatedBytes = total * 8L,
            configSnapshot = c
        )
    }
}

/**
 * Processor for [ColorStageType.PALETTE_TRANSFORM].
 * Executes Rec. 709 monochrome desaturation, pastel remapping, and harmonic tonal transforms.
 */
object PaletteTransformProcessor : ColorStageProcessor {
    override val stageType: ColorStageType = ColorStageType.PALETTE_TRANSFORM

    override fun execute(context: StageExecutionContext, config: ColorStageConfig): StageExecutionOutput {
        val startTime = System.currentTimeMillis()
        val c = config as? PaletteTransformStageConfig ?: PaletteTransformStageConfig()
        val w = context.width
        val h = context.height
        val total = w * h

        val stagePixels = IntArray(total)
        val composited = IntArray(total)

        for (i in 0 until total) {
            val back = context.backdropPixels[i]
            val a = (back ushr 24) and 0xFF
            if (a == 0) {
                composited[i] = 0
                continue
            }
            val r = ((back ushr 16) and 0xFF) / 255.0f
            val g = ((back ushr 8) and 0xFF) / 255.0f
            val b = (back and 0xFF) / 255.0f

            // Rec. 709 luminance
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b

            var outR = r; var outG = g; var outB = b

            when (c.transformType) {
                PaletteTransformStageConfig.TransformType.MONOCHROME_DESATURATE -> {
                    val sat = c.saturation.coerceIn(0f, 1f)
                    outR = lum + (r - lum) * sat
                    outG = lum + (g - lum) * sat
                    outB = lum + (b - lum) * sat
                }
                PaletteTransformStageConfig.TransformType.PASTEL_SHIFT -> {
                    // Lift shadows, soften contrast, desaturate
                    val liftedLum = 0.25f + lum * 0.75f
                    val sat = c.saturation.coerceIn(0f, 1f) * 0.65f
                    outR = (liftedLum + (r - lum) * sat).coerceIn(0f, 1f)
                    outG = (liftedLum + (g - lum) * sat).coerceIn(0f, 1f)
                    outB = (liftedLum + (b - lum) * sat).coerceIn(0f, 1f)
                }
                PaletteTransformStageConfig.TransformType.WARM_SUNSET -> {
                    outR = (r * 1.15f + 0.05f).coerceIn(0f, 1f)
                    outG = (g * 1.05f).coerceIn(0f, 1f)
                    outB = (b * 0.85f).coerceIn(0f, 1f)
                }
                PaletteTransformStageConfig.TransformType.COOL_TWILIGHT -> {
                    outR = (r * 0.85f).coerceIn(0f, 1f)
                    outG = (g * 0.95f).coerceIn(0f, 1f)
                    outB = (b * 1.20f + 0.05f).coerceIn(0f, 1f)
                }
                PaletteTransformStageConfig.TransformType.DUOTONE -> {
                    // Dark navy (0.05, 0.05, 0.20) to bright gold (1.0, 0.85, 0.40)
                    outR = 0.05f + lum * 0.95f
                    outG = 0.05f + lum * 0.80f
                    outB = 0.20f + lum * 0.20f
                }
                PaletteTransformStageConfig.TransformType.SEPIA -> {
                    outR = (r * 0.393f + g * 0.769f + b * 0.189f).coerceIn(0f, 1f)
                    outG = (r * 0.349f + g * 0.686f + b * 0.168f).coerceIn(0f, 1f)
                    outB = (r * 0.272f + g * 0.534f + b * 0.131f).coerceIn(0f, 1f)
                }
            }

            // Lerp with original by opacity/strength
            val effBlend = (c.opacity * c.strength).coerceIn(0f, 1f)
            val finalR = (r + (outR - r) * effBlend).coerceIn(0f, 1f)
            val finalG = (g + (outG - g) * effBlend).coerceIn(0f, 1f)
            val finalB = (b + (outB - b) * effBlend).coerceIn(0f, 1f)

            val rInt = (finalR * 255f).roundToInt().coerceIn(0, 255)
            val gInt = (finalG * 255f).roundToInt().coerceIn(0, 255)
            val bInt = (finalB * 255f).roundToInt().coerceIn(0, 255)

            val px = (a shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
            stagePixels[i] = px
            composited[i] = px
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        return StageExecutionOutput(
            stageId = c.stageId,
            stageType = stageType,
            stageLayerName = c.name,
            stagePixels = stagePixels,
            compositedBackdrop = composited,
            executionTimeMs = elapsed,
            memoryAllocatedBytes = total * 8L,
            configSnapshot = c
        )
    }
}

/**
 * Processor for [ColorStageType.FINISHING_EFFECT].
 * Applies lens and finishing effects (neon bloom, vignette, chromatic aberration).
 */
object FinishingEffectProcessor : ColorStageProcessor {
    override val stageType: ColorStageType = ColorStageType.FINISHING_EFFECT

    override fun execute(context: StageExecutionContext, config: ColorStageConfig): StageExecutionOutput {
        val startTime = System.currentTimeMillis()
        val c = config as? FinishingEffectStageConfig ?: FinishingEffectStageConfig()
        val w = context.width
        val h = context.height
        val total = w * h

        val stagePixels = IntArray(total)
        val composited = context.backdropPixels.copyOf()

        when (c.effectType) {
            FinishingEffectStageConfig.FinishingEffectType.NEON_BLOOM -> {
                // Extract saturated bright pixels into bloom source
                val bloomSource = IntArray(total)
                for (i in 0 until total) {
                    val px = context.backdropPixels[i]
                    val a = (px ushr 24) and 0xFF
                    if (a > 50) {
                        val r = ((px ushr 16) and 0xFF) / 255f
                        val g = ((px ushr 8) and 0xFF) / 255f
                        val b = (px and 0xFF) / 255f
                        val maxC = max(r, max(g, b))
                        val minC = min(r, min(g, b))
                        val sat = if (maxC > 0f) (maxC - minC) / maxC else 0f
                        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b

                        // Neon threshold: bright or highly saturated
                        if (lum > 0.40f || sat > 0.40f) {
                            bloomSource[i] = px
                        }
                    }
                }

                // Separable box blur for bloom glow expansion
                val radius = max(2, (c.bloomRadius * (if (context.quality.isReducedPreview) 0.5f else 1.0f)).roundToInt())
                val hBlur = IntArray(total)

                for (y in 0 until h) {
                    val row = y * w
                    for (x in 0 until w) {
                        var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                        for (k in -radius..radius) {
                            val pxX = (x + k).coerceIn(0, w - 1)
                            val col = bloomSource[row + pxX]
                            val a = (col ushr 24) and 0xFF
                            if (a > 0) {
                                sumR += (col ushr 16) and 0xFF
                                sumG += (col ushr 8) and 0xFF
                                sumB += col and 0xFF
                                count++
                            }
                        }
                        if (count > 0) {
                            val effCount = radius * 2 + 1
                            hBlur[row + x] = ((255 shl 24) or ((sumR / effCount) shl 16) or ((sumG / effCount) shl 8) or (sumB / effCount))
                        }
                    }
                }

                for (x in 0 until w) {
                    for (y in 0 until h) {
                        var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                        for (k in -radius..radius) {
                            val py = (y + k).coerceIn(0, h - 1)
                            val col = hBlur[py * w + x]
                            val a = (col ushr 24) and 0xFF
                            if (a > 0) {
                                sumR += (col ushr 16) and 0xFF
                                sumG += (col ushr 8) and 0xFF
                                sumB += col and 0xFF
                                count++
                            }
                        }
                        val idx = y * w + x
                        if (count > 0) {
                            val effCount = radius * 2 + 1
                            val rInt = (min(255f, (sumR / effCount) * 1.5f * c.bloomIntensity)).roundToInt().coerceIn(0, 255)
                            val gInt = (min(255f, (sumG / effCount) * 1.5f * c.bloomIntensity)).roundToInt().coerceIn(0, 255)
                            val bInt = (min(255f, (sumB / effCount) * 1.5f * c.bloomIntensity)).roundToInt().coerceIn(0, 255)
                            val effA = (c.opacity * c.strength * 255f).roundToInt().coerceIn(0, 255)
                            val bloomPx = (effA shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                            stagePixels[idx] = bloomPx
                            composited[idx] = LayerBlendCompositor.blendPixel(
                                composited[idx],
                                bloomPx,
                                c.blendMode,
                                1.0f
                            )
                        }
                    }
                }
            }
            FinishingEffectStageConfig.FinishingEffectType.VIGNETTE -> {
                val cx = w * 0.5f
                val cy = h * 0.5f
                val maxD = sqrt(cx * cx + cy * cy)
                for (y in 0 until h) {
                    val row = y * w
                    for (x in 0 until w) {
                        val idx = row + x
                        val dx = x - cx
                        val dy = y - cy
                        val dist = sqrt(dx * dx + dy * dy) / maxD
                        if (dist > 0.4f) {
                            val vigAlpha = ((dist - 0.4f) / 0.6f * c.opacity * c.strength * 255f).roundToInt().coerceIn(0, 255)
                            val px = (vigAlpha shl 24) // Black vignette
                            stagePixels[idx] = px
                            composited[idx] = LayerBlendCompositor.blendPixel(
                                composited[idx],
                                px,
                                BlendModeType.Multiply,
                                1.0f
                            )
                        }
                    }
                }
            }
            FinishingEffectStageConfig.FinishingEffectType.CHROMATIC_ABERRATION -> {
                val shift = max(1, (3 * (if (context.quality.isReducedPreview) 0.5f else 1.0f)).roundToInt())
                for (y in 0 until h) {
                    val row = y * w
                    for (x in 0 until w) {
                        val idx = row + x
                        val rX = (x + shift).coerceIn(0, w - 1)
                        val bX = (x - shift).coerceIn(0, w - 1)
                        val rPx = context.backdropPixels[row + rX]
                        val bPx = context.backdropPixels[row + bX]
                        val r = (rPx ushr 16) and 0xFF
                        val g = (context.backdropPixels[idx] ushr 8) and 0xFF
                        val b = bPx and 0xFF
                        val a = (context.backdropPixels[idx] ushr 24) and 0xFF
                        val px = (a shl 24) or (r shl 16) or (g shl 8) or b
                        stagePixels[idx] = px
                        composited[idx] = px
                    }
                }
            }
            FinishingEffectStageConfig.FinishingEffectType.POSTERIZE -> {
                val levels = 4
                for (i in 0 until total) {
                    val col = context.backdropPixels[i]
                    val a = (col ushr 24) and 0xFF
                    val r = (((col ushr 16) and 0xFF) / (256 / levels) * (255 / (levels - 1))).coerceIn(0, 255)
                    val g = (((col ushr 8) and 0xFF) / (256 / levels) * (255 / (levels - 1))).coerceIn(0, 255)
                    val b = ((col and 0xFF) / (256 / levels) * (255 / (levels - 1))).coerceIn(0, 255)
                    val px = (a shl 24) or (r shl 16) or (g shl 8) or b
                    stagePixels[i] = px
                    composited[i] = px
                }
            }
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        return StageExecutionOutput(
            stageId = c.stageId,
            stageType = stageType,
            stageLayerName = c.name,
            stagePixels = stagePixels,
            compositedBackdrop = composited,
            executionTimeMs = elapsed,
            memoryAllocatedBytes = total * 8L,
            configSnapshot = c
        )
    }
}
