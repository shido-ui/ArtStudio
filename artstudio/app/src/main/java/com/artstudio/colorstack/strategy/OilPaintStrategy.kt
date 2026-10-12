package com.artstudio.colorstack.strategy

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.cv.palette.perceivedLuminance
import com.artstudio.document.model.BlendModeType
import kotlin.math.*

/**
 * Strategy implementation for Style 11: Oil Paint.
 *
 * Simulates viscous multi-filament bristle ridges, tactile 2.5D impasto relief lighting,
 * and coarse linen canvas weave.
 *
 * NOTE ON SIMULATION FIDELITY:
 * This strategy produces simulated artistic medium effects via closed-form 2.5D heightfield
 * relief shading, directional bristle synthesis, and optical specular slope modulation.
 * It is an optical medium approximation designed for interactive digital illustration,
 * NOT a true numerical physical simulation of 3D Navier-Stokes fluid mechanics, pigment
 * rheology, or multi-phase viscous fluid suspensions.
 */
object OilPaintStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.OIL_PAINT
    override val displayName: String = "Oil Paint"
    override val description: String =
        "Viscous multi-filament bristle ridges, tactile 2.5D impasto relief lighting, and coarse linen canvas weave (simulated medium effect, not a 3D physical fluid simulation)"

    override val supportedInputModes: List<String> = listOf("Line Art", "Flat Colour", "Tonal Underpaintings")
    override val documentedLimitations: String =
        "Simulated 2.5D impasto relief operates on segmented tonal regions. High-frequency photographic noise interferes with directional bristle alignment, yielding chaotic surface roughness rather than cohesive artistic brush marks."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? OilPaintConfig ?: OilPaintConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val basePixels = IntArray(total)
        val bristlePixels = IntArray(total)
        val impastoPixels = IntArray(total)
        val composite = IntArray(total)

        val lightRad = Math.toRadians(config.lightAngleDeg.toDouble())
        val lx = cos(lightRad).toFloat()
        val ly = sin(lightRad).toFloat()
        val bristleAngleRad = lightRad + (Math.PI / 2.0)
        val bx = cos(bristleAngleRad).toFloat()
        val by = sin(bristleAngleRad).toFloat()

        val isPreview = input.quality == RenderQuality.PREVIEW
        val step = if (isPreview) 2 else 1

        // 1. Render Rich Opaque Base Coat
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

        // 2. Synthesize Multi-Filament Bristle Tracks & Linen Weave Texture
        val bristleFreq = (0.25f + config.bristleDensity * 0.45f)
        val linenFreq = 0.35f

        for (y in 0 until h step step) {
            val row = y * w
            for (x in 0 until w step step) {
                val idx = row + x

                // Check if inside any region
                var inMask = false
                var activeMaskVal = 0
                for ((_, mask) in input.regionMasks) {
                    val mv = mask[idx].toInt() and 0xFF
                    if (mv > 20) {
                        inMask = true
                        activeMaskVal = mv
                        break
                    }
                }
                if (!inMask) continue

                // Directional bristle coordinate
                val u = x * bx + y * by
                val bristleWave = sin(u * bristleFreq.toDouble())
                val bristleVal = (bristleWave * 0.5 + 0.5).toFloat()

                // Linen canvas weave (orthogonal interference)
                val canvasWave = sin(x * linenFreq.toDouble()) * cos(y * linenFreq.toDouble())
                val canvasVal = (canvasWave * 0.5 + 0.5).toFloat()

                // Combined texture modulation factor
                val texFactor = (bristleVal * 0.65f + canvasVal * config.canvasWeave * 0.35f).coerceIn(0f, 1f)
                val texTone = (texFactor * 255f).roundToInt().coerceIn(0, 255)
                val texAlpha = (activeMaskVal * 0.45f).roundToInt().coerceIn(0, 255)

                val texPx = (texAlpha shl 24) or (texTone shl 16) or (texTone shl 8) or texTone

                if (step == 1) {
                    bristlePixels[idx] = texPx
                } else {
                    for (dy in 0 until step) {
                        if (y + dy < h) {
                            val r = (y + dy) * w
                            for (dx in 0 until step) {
                                if (x + dx < w) bristlePixels[r + x + dx] = texPx
                            }
                        }
                    }
                }
            }
        }

        // 3. Compute 2.5D Impasto Relief Lighting (Specular Crests & Valley Crevices)
        if (config.impastoDepth > 0.05f) {
            for (y in 1 until h - 1 step step) {
                val row = y * w
                for (x in 1 until w - 1 step step) {
                    val idx = row + x
                    val basePx = basePixels[idx]
                    val baseA = (basePx ushr 24) and 0xFF
                    if (baseA < 30) continue

                    // Calculate surface slope along light vector (directional gradient)
                    val uNext = (x + 1) * bx + (y + 1) * by
                    val uPrev = (x - 1) * bx + (y - 1) * by
                    val slope = (sin(uNext * bristleFreq.toDouble()) - sin(uPrev * bristleFreq.toDouble())).toFloat()

                    // Modulate slope by light alignment
                    val lightFacing = slope * lx + slope * ly
                    val reliefDepth = config.impastoDepth

                    if (lightFacing < -0.15f) {
                        // Cast shadow on opposing slope valley (Multiply)
                        val shadowStrength = (-lightFacing * reliefDepth * 0.60f).coerceIn(0f, 0.75f)
                        val shAlpha = (baseA * shadowStrength).roundToInt().coerceIn(0, 255)
                        val shPx = (shAlpha shl 24) // Black shadow
                        if (step == 1) {
                            impastoPixels[idx] = shPx
                        } else {
                            for (dy in 0 until step) {
                                if (y + dy < h) {
                                    val r = (y + dy) * w
                                    for (dx in 0 until step) {
                                        if (x + dx < w) impastoPixels[r + x + dx] = shPx
                                    }
                                }
                            }
                        }
                    } else if (lightFacing > 0.20f) {
                        // Specular highlight crest on facing slope (Add/Screen)
                        val highlightStrength = (lightFacing * reliefDepth * 0.50f).coerceIn(0f, 0.65f)
                        val hlAlpha = (baseA * highlightStrength).roundToInt().coerceIn(0, 255)
                        val hlPx = (hlAlpha shl 24) or 0x00FFFFFF // Pure white specular highlight
                        if (step == 1) {
                            impastoPixels[idx] = hlPx
                        } else {
                            for (dy in 0 until step) {
                                if (y + dy < h) {
                                    val r = (y + dy) * w
                                    for (dx in 0 until step) {
                                        if (x + dx < w) impastoPixels[r + x + dx] = hlPx
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Blend passes onto final composite
        for (i in 0 until total) {
            val texPx = bristlePixels[i]
            if (((texPx ushr 24) and 0xFF) > 0) {
                composite[i] = LayerBlendCompositor.blendPixel(
                    composite[i],
                    texPx,
                    BlendModeType.Overlay,
                    0.50f
                )
            }
            val impPx = impastoPixels[i]
            val impA = (impPx ushr 24) and 0xFF
            if (impA > 0) {
                val isSpecular = (impPx and 0x00FFFFFF) == 0x00FFFFFF
                val blendMode = if (isSpecular) BlendModeType.Screen else BlendModeType.Multiply
                composite[i] = LayerBlendCompositor.blendPixel(
                    composite[i],
                    impPx,
                    blendMode,
                    1.0f
                )
            }
        }

        // 5. Assemble Independent Layers
        val layers = mutableListOf<StyleLayerOutput>()
        layers.add(
            StyleLayerOutput(
                name = "Opaque Oil Base",
                role = LayerRole.BASE_FLAT,
                pixels = basePixels,
                blendMode = BlendModeType.Normal,
                opacity = 1.0f
            )
        )
        layers.add(
            StyleLayerOutput(
                name = "Bristle Tracks & Linen Weave",
                role = LayerRole.TEXTURE,
                pixels = bristlePixels,
                blendMode = BlendModeType.Overlay,
                opacity = 0.50f
            )
        )
        if (config.impastoDepth > 0.05f) {
            layers.add(
                StyleLayerOutput(
                    name = "Impasto Relief Lighting",
                    role = LayerRole.SHADOW,
                    pixels = impastoPixels,
                    blendMode = BlendModeType.Multiply,
                    opacity = config.impastoDepth
                )
            )
        }

        // 6. Composite Preserved Linework
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
            name = "Oil Paint Recipe",
            styleId = ColorStyleId.OIL_PAINT,
            stages = listOf(
                BaseFillStageConfig(name = "Opaque Oil Base", fillType = BaseFillStageConfig.BaseFillType.FLAT),
                MediumTextureStageConfig(
                    name = "Impasto Bristle Texture",
                    mediumType = MediumTextureStageConfig.MediumTextureType.OIL_IMPASTO,
                    roughness = config.impastoDepth,
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
