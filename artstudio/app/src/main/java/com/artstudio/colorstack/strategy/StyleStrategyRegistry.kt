package com.artstudio.colorstack.strategy

import com.artstudio.colorstack.ColorStyleId

/**
 * Central registry of Colour Lab style rendering strategies.
 * Provides lookup, capability discovery, and execution dispatch for all 15 Colour Lab styles plus No Colour Baseline.
 */
object StyleStrategyRegistry {

    private val strategies = mapOf(
        ColorStyleId.NO_COLOUR to NoColourStrategy,
        ColorStyleId.CLASSIC_CEL to ClassicCelStrategy,
        ColorStyleId.SOFT_GRADIENT to SoftGradientStrategy,
        ColorStyleId.HYBRID_CEL_SOFT to HybridCelSoftStrategy,
        ColorStyleId.FLAT_ILLUSTRATION to FlatIllustrationStrategy,
        ColorStyleId.PAINTERLY to PainterlyStrategy,
        ColorStyleId.WATERCOLOUR to WatercolourStrategy,
        ColorStyleId.COLOURED_PENCIL to ColouredPencilStrategy,
        ColorStyleId.MARKER to MarkerStrategy,
        ColorStyleId.MANGA_SCREENTONE to MangaScreentoneStrategy,
        ColorStyleId.COMIC_BOOK to ComicBookStrategy,
        ColorStyleId.OIL_PAINT to OilPaintStrategy,
        ColorStyleId.GOUACHE to GouacheStrategy,
        ColorStyleId.PASTEL to PastelStrategy,
        ColorStyleId.NEON_CYBERPUNK to NeonCyberpunkStrategy,
        ColorStyleId.MONOCHROME to MonochromeStrategy
    )

    /**
     * Resolves the strategy registered for the given [styleId], or null if unsupported.
     */
    fun get(styleId: ColorStyleId): ColourStyleStrategy? = strategies[styleId]

    /**
     * Resolves the strategy registered for the given [styleId], throwing if unsupported.
     */
    fun requireStrategy(styleId: ColorStyleId): ColourStyleStrategy {
        return strategies[styleId]
            ?: throw IllegalArgumentException("Style strategy for $styleId is not implemented or registered.")
    }

    /**
     * Returns true if the style strategy has a dedicated concrete implementation.
     */
    fun isSupported(styleId: ColorStyleId): Boolean = strategies.containsKey(styleId)

    /**
     * Returns all currently implemented style strategies.
     */
    fun listSupportedStrategies(): List<ColourStyleStrategy> = strategies.values.toList()

    /**
     * Executes the rendering pipeline for the specified [styleId].
     */
    fun render(styleId: ColorStyleId, input: StyleStrategyInput): StyleStrategyOutput {
        val strategy = requireStrategy(styleId)
        return strategy.render(input)
    }
}
