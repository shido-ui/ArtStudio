package com.artstudio.colorstack.strategy

import com.artstudio.colorstack.*
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.BlendModeType
import kotlin.math.max

/**
 * Strategy implementation for Style 0: No Colour Baseline.
 *
 * Real rendering strategy that completely skips automatic fills and color shading,
 * preserving clean unpainted canvas and pure linework / vector reconstruction.
 * Region masks and source palette data remain fully intact and accessible for manual painting.
 */
object NoColourStrategy : ColourStyleStrategy {

    override val styleId: ColorStyleId = ColorStyleId.NO_COLOUR
    override val displayName: String = "No Colour Baseline"
    override val description: String =
        "Preserves clean uncoloured canvas and reconstructed linework with zero automatic fills, preparing the document for manual colouring"

    override val supportedInputModes: List<String> = listOf("Line Art", "Vector Contours", "Preserved Sketches")
    override val documentedLimitations: String =
        "Skips all automatic color stages by design. Does not generate fills, shadows, or textures; all regions remain unpainted for manual coloring."

    override fun render(input: StyleStrategyInput): StyleStrategyOutput {
        val startTime = System.currentTimeMillis()
        val config = input.config as? NoColourConfig ?: NoColourConfig()
        val w = input.width
        val h = input.height
        val total = w * h

        val composite = IntArray(total)
        val layers = mutableListOf<StyleLayerOutput>()

        // 1. Optional Paper Canvas (Pure White 0xFFFFFFFF or Transparent 0x00000000)
        // Automatic region color fills are strictly skipped.
        if (config.paperBackground) {
            val paperPixels = IntArray(total) { -0x1 } // 0xFFFFFFFF
            System.arraycopy(paperPixels, 0, composite, 0, total)
            layers.add(
                StyleLayerOutput(
                    name = "Paper Canvas (White)",
                    role = LayerRole.BASE_FLAT,
                    pixels = paperPixels,
                    blendMode = BlendModeType.Normal,
                    opacity = 1.0f
                )
            )
        }

        // 2. Preserved Linework / Reconstruction Layer
        input.linework?.let { lw ->
            val lwPixels = lw.pixels
            val effectiveOpacity = lw.opacity * config.lineArtOpacity
            for (i in 0 until total) {
                val lwPx = lwPixels[i]
                if (((lwPx ushr 24) and 0xFF) > 0) {
                    composite[i] = LayerBlendCompositor.blendPixel(
                        composite[i],
                        lwPx,
                        lw.blendMode,
                        effectiveOpacity
                    )
                }
            }
            layers.add(
                StyleLayerOutput(
                    name = "Line Art (Preserved)",
                    role = LayerRole.LINEWORK,
                    pixels = lwPixels,
                    blendMode = lw.blendMode,
                    opacity = effectiveOpacity
                )
            )
        }

        val elapsed = max(1L, System.currentTimeMillis() - startTime)
        val recipe = ColorStackRecipe(
            name = "No Colour Recipe",
            styleId = ColorStyleId.NO_COLOUR,
            isNoColour = true,
            stages = emptyList() // 0 colour stages; pure manual linework baseline
        )

        return StyleStrategyOutput(
            styleId = styleId,
            width = w,
            height = h,
            finalComposite = composite,
            layers = layers,
            renderTimeMs = elapsed,
            memoryAllocatedBytes = total * 8L,
            recipeSnapshot = recipe
        )
    }
}
