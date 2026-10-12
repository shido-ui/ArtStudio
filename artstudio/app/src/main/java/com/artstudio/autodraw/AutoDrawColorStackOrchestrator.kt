package com.artstudio.autodraw

import com.artstudio.color.ColorData
import com.artstudio.colorstack.*
import com.artstudio.colorstack.strategy.StyleStrategyInput
import com.artstudio.colorstack.strategy.StyleStrategyOutput
import com.artstudio.colorstack.strategy.StyleStrategyRegistry
import com.artstudio.cv.palette.SourcePaletteAnalysis
import com.artstudio.cv.palette.SourcePaletteExtractor
import com.artstudio.cv.region.RegionMask
import com.artstudio.cv.region.RegionMaskConfig
import com.artstudio.cv.region.RegionMaskEngine
import com.artstudio.cv.region.toRegionMask
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrator connecting AutoDraw v1 classical computer vision linework vectorization
 * with Colour Lab (ColorStack) region segmentation, source palette extraction,
 * style strategy application, and non-destructive layer baking.
 */
object AutoDrawColorStackOrchestrator {

    data class OrchestrationResult(
        val autoDrawResult: AutoDrawV1Result,
        val regions: List<RegionMask>,
        val paletteAnalysis: SourcePaletteAnalysis,
        val styleOutput: StyleStrategyOutput,
        val bakedLayerGroup: LayerNode.LayerGroup
    )

    /**
     * Executes the end-to-end line art to styled artwork pipeline:
     * 1. AutoDrawV1Pipeline.processLineArt(...)
     * 2. RegionMaskEngine.segmentAllRegions(...)
     * 3. SourcePaletteExtractor.analyze(...)
     * 4. RegionColorAssignment mapping
     * 5. Style application (StyleStrategyRegistry / ColorStackPipeline)
     * 6. ColorStackPipeline.bakeToLayers(...)
     */
    suspend fun processAndColorize(
        pixels: IntArray,
        width: Int,
        height: Int,
        styleId: ColorStyleId = ColorStyleId.CLASSIC_CEL,
        document: ArtDocument? = null,
        onProgress: ((Float, String) -> Unit)? = null
    ): OrchestrationResult = withContext(Dispatchers.Default) {
        onProgress?.invoke(0.05f, "Running AutoDraw v1 line art analysis")
        val autoDrawResult = AutoDrawV1Pipeline.processLineArt(
            pixels = pixels,
            width = width,
            height = height,
            onProgress = { p, stage ->
                onProgress?.invoke(0.05f + p * 0.35f, stage)
            }
        )

        onProgress?.invoke(0.45f, "Segmenting canvas regions")
        val segmentations = RegionMaskEngine.segmentAllRegions(
            pixels = pixels,
            width = width,
            height = height,
            config = RegionMaskConfig(gapClosingRadius = 2, boundaryExpansion = 1)
        )
        val regions = segmentations.map { it.toRegionMask() }

        onProgress?.invoke(0.65f, "Extracting dominant source palette")
        val paletteAnalysis = SourcePaletteExtractor.analyze(
            pixels = pixels,
            width = width,
            height = height,
            regions = regions
        )

        onProgress?.invoke(0.80f, "Applying Colour Lab style: ${styleId.displayName}")
        val regionMaskMap = regions.associate { it.regionId to it.mask }
        val regionColorMap = paletteAnalysis.regionAssignments.mapValues { it.value.effectiveColor }

        // Linework raster representation
        val lineworkPixels = IntArray(width * height)
        for (c in autoDrawResult.contours) {
            for (pt in c.points) {
                val px = pt.x.toInt().coerceIn(0, width - 1)
                val py = pt.y.toInt().coerceIn(0, height - 1)
                lineworkPixels[py * width + px] = 0xFF000000.toInt()
            }
        }
        val lineworkData = LineworkData(
            width = width,
            height = height,
            pixels = lineworkPixels
        )

        val strategy = StyleStrategyRegistry.requireStrategy(styleId)
        val styleInput = StyleStrategyInput(
            width = width,
            height = height,
            regionMasks = regionMaskMap,
            regionColors = regionColorMap,
            paletteAnalysis = paletteAnalysis,
            linework = lineworkData,
            config = when (styleId) {
                ColorStyleId.NO_COLOUR -> com.artstudio.colorstack.strategy.NoColourConfig()
                ColorStyleId.CLASSIC_CEL -> com.artstudio.colorstack.strategy.ClassicCelConfig()
                ColorStyleId.SOFT_GRADIENT -> com.artstudio.colorstack.strategy.SoftGradientConfig()
                ColorStyleId.HYBRID_CEL_SOFT -> com.artstudio.colorstack.strategy.HybridCelSoftConfig()
                ColorStyleId.FLAT_ILLUSTRATION -> com.artstudio.colorstack.strategy.FlatIllustrationConfig()
                ColorStyleId.PAINTERLY -> com.artstudio.colorstack.strategy.PainterlyConfig()
                ColorStyleId.WATERCOLOUR -> com.artstudio.colorstack.strategy.WatercolourConfig()
                ColorStyleId.COLOURED_PENCIL -> com.artstudio.colorstack.strategy.ColouredPencilConfig()
                ColorStyleId.MARKER -> com.artstudio.colorstack.strategy.MarkerConfig()
                ColorStyleId.MANGA_SCREENTONE -> com.artstudio.colorstack.strategy.MangaScreentoneConfig()
                ColorStyleId.COMIC_BOOK -> com.artstudio.colorstack.strategy.ComicBookConfig()
                ColorStyleId.OIL_PAINT -> com.artstudio.colorstack.strategy.OilPaintConfig()
                ColorStyleId.GOUACHE -> com.artstudio.colorstack.strategy.GouacheConfig()
                ColorStyleId.PASTEL -> com.artstudio.colorstack.strategy.PastelConfig()
                ColorStyleId.NEON_CYBERPUNK -> com.artstudio.colorstack.strategy.NeonCyberpunkConfig()
                ColorStyleId.MONOCHROME -> com.artstudio.colorstack.strategy.MonochromeConfig()
                ColorStyleId.CUSTOM -> com.artstudio.colorstack.strategy.ClassicCelConfig()
            }
        )

        val styleOutput = strategy.render(styleInput)

        onProgress?.invoke(0.95f, "Baking non-destructive editable layers")
        val targetDoc = document ?: ArtDocument(
            canvas = CanvasConfig(width = width, height = height)
        )
        val bakedGroup = ColorStackPipeline.bakeToLayers(targetDoc, styleOutput)

        onProgress?.invoke(1.0f, "Colour Lab orchestration complete")

        return@withContext OrchestrationResult(
            autoDrawResult = autoDrawResult,
            regions = regions,
            paletteAnalysis = paletteAnalysis,
            styleOutput = styleOutput,
            bakedLayerGroup = bakedGroup
        )
    }
}
