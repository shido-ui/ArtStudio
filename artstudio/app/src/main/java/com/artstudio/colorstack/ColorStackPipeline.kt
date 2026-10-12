package com.artstudio.colorstack

import com.artstudio.color.ColorData
import com.artstudio.core.LayerBlendCompositor
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.BlendModeType
import com.artstudio.document.model.LayerNode
import com.artstudio.document.model.LayerTransform
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Execution pipeline coordinator for the modular Colour Stack.
 *
 * Provides:
 * 1. Sequential stage processing with deterministic ordering.
 * 2. Incremental dirty-stage caching (changing stage K reuses upstream outputs 0..K-1).
 * 3. Independent line art preservation (linework never mutated by color operations).
 * 4. Dual-resolution execution (sub-16ms interactive PREVIEW vs full-quality FINAL).
 * 5. Non-destructive layer group export into native [ArtDocument].
 */
object ColorStackPipeline {

    /**
     * Executes the colour stack recipe over given region masks and colors.
     */
    fun execute(
        recipe: ColorStackRecipe,
        linework: LineworkData?,
        regionMasks: Map<String, ByteArray>,
        regionColors: Map<String, ColorData>,
        width: Int,
        height: Int,
        quality: RenderQuality = RenderQuality.FINAL,
        cachedOutputs: Map<String, StageExecutionOutput>? = null
    ): ColorStackRenderResult {
        if (!com.artstudio.core.FeatureFlags.isColorStackEnabled) {
            throw IllegalStateException("ColorStack pipeline is disabled: FeatureFlags.isColorStackEnabled is false")
        }
        val overallStart = System.currentTimeMillis()

        // 1. Resolve recipe for validation, No Colour bypass, and diagnostics
        val resolverResult = ColorStackResolver.resolve(recipe)
        val isNoColour = resolverResult.isNoColourActive

        // 2. Downsample if preview quality requested on larger canvas
        val renderW: Int
        val renderH: Int
        val scaledMasks: Map<String, ByteArray>
        val scaledLinework: LineworkData?

        if (quality.isReducedPreview && (width > quality.maxDimension || height > quality.maxDimension)) {
            val scale = quality.maxDimension.toFloat() / max(width, height)
            renderW = max(32, (width * scale).roundToInt())
            renderH = max(32, (height * scale).roundToInt())

            // Nearest-neighbor downsample masks
            scaledMasks = regionMasks.mapValues { (_, mask) ->
                downsampleMask(mask, width, height, renderW, renderH)
            }
            scaledLinework = linework?.let { lw ->
                downsampleLinework(lw, renderW, renderH)
            }
        } else {
            renderW = width
            renderH = height
            scaledMasks = regionMasks
            scaledLinework = linework
        }

        val totalPixels = renderW * renderH
        var currentBackdrop = IntArray(totalPixels) // Starts transparent
        val stageOutputs = mutableListOf<StageExecutionOutput>()
        var peakMemory = totalPixels * 4L // Initial canvas buffer

        var upstreamValid = true

        if (!isNoColour) {
            for (stageConfig in resolverResult.activeStages) {
                val cached = cachedOutputs?.get(stageConfig.stageId)
                if (upstreamValid && cached != null &&
                    cached.configSnapshot == stageConfig &&
                    cached.compositedBackdrop.size == totalPixels
                ) {
                    // Reuse cached stage output
                    stageOutputs.add(cached)
                    currentBackdrop = cached.compositedBackdrop
                    peakMemory += cached.memoryAllocatedBytes
                } else {
                    upstreamValid = false // Any change invalidates subsequent downstream caches

                    val context = StageExecutionContext(
                        width = renderW,
                        height = renderH,
                        backdropPixels = currentBackdrop,
                        regionMasks = scaledMasks,
                        regionColors = regionColors,
                        quality = quality,
                        seed = recipe.seed
                    )

                    val output = when (stageConfig.stageType) {
                        ColorStageType.BASE_FILL -> BaseFillProcessor.execute(context, stageConfig)
                        ColorStageType.CEL_SHADOW -> CelShadowProcessor.execute(context, stageConfig)
                        ColorStageType.GRADIENT -> GradientProcessor.execute(context, stageConfig)
                        ColorStageType.HIGHLIGHT -> HighlightProcessor.execute(context, stageConfig)
                        ColorStageType.MEDIUM_TEXTURE -> MediumTextureProcessor.execute(context, stageConfig)
                        ColorStageType.PALETTE_TRANSFORM -> PaletteTransformProcessor.execute(context, stageConfig)
                        ColorStageType.FINISHING_EFFECT -> FinishingEffectProcessor.execute(context, stageConfig)
                        ColorStageType.LINEWORK -> BaseFillProcessor.execute(context, stageConfig)
                    }

                    stageOutputs.add(output)
                    currentBackdrop = output.compositedBackdrop
                    peakMemory += output.memoryAllocatedBytes
                }
            }
        }

        // 3. Composite independent linework on top
        var lineworkOutput: StageExecutionOutput? = null
        val finalComposite = currentBackdrop.copyOf()

        if (scaledLinework != null) {
            val lwStart = System.currentTimeMillis()
            val lwPixels = scaledLinework.pixels
            for (i in 0 until totalPixels) {
                val lwPx = lwPixels[i]
                val lwA = (lwPx ushr 24) and 0xFF
                if (lwA > 0) {
                    finalComposite[i] = LayerBlendCompositor.blendPixel(
                        finalComposite[i],
                        lwPx,
                        scaledLinework.blendMode,
                        scaledLinework.opacity
                    )
                }
            }
            val lwElapsed = max(1L, System.currentTimeMillis() - lwStart)
            lineworkOutput = StageExecutionOutput(
                stageId = "linework_overlay",
                stageType = ColorStageType.LINEWORK,
                stageLayerName = "Linework / Line Art",
                stagePixels = scaledLinework.pixels,
                compositedBackdrop = finalComposite,
                executionTimeMs = lwElapsed,
                memoryAllocatedBytes = totalPixels * 4L,
                configSnapshot = BaseFillStageConfig(name = "Linework Overlay", blendMode = scaledLinework.blendMode)
            )
        }

        val totalElapsed = max(1L, System.currentTimeMillis() - overallStart)

        return ColorStackRenderResult(
            width = renderW,
            height = renderH,
            finalCompositePixels = finalComposite,
            stageOutputs = stageOutputs,
            lineworkOutput = lineworkOutput,
            totalExecutionTimeMs = totalElapsed,
            peakMemoryBytes = peakMemory,
            quality = quality,
            isNoColour = isNoColour
        )
    }

    /**
     * Bakes the rendered colour stack result into an editable [LayerNode.LayerGroup]
     * where each functional stage is preserved as a separate layer in the [ArtDocument].
     */
    fun toLayerGroup(
        document: ArtDocument,
        result: ColorStackRenderResult,
        groupName: String = "Colour Lab (${result.stageOutputs.size} Stages)"
    ): LayerNode.LayerGroup {
        val childLayers = mutableListOf<LayerNode>()

        // Add layers for each active stage bottom-to-top
        for (stage in result.stageOutputs) {
            val rasterLayer = LayerNode.RasterLayer(
                id = UUID.randomUUID().toString(),
                name = stage.stageLayerName,
                visible = true,
                opacity = stage.configSnapshot.opacity,
                blendMode = stage.configSnapshot.blendMode,
                width = result.width,
                height = result.height,
                transform = LayerTransform()
            )
            childLayers.add(rasterLayer)
        }

        // Add linework on top
        result.lineworkOutput?.let { lw ->
            val lwLayer = LayerNode.RasterLayer(
                id = UUID.randomUUID().toString(),
                name = "Line Art (Preserved)",
                visible = true,
                opacity = 1.0f,
                blendMode = BlendModeType.Multiply,
                width = result.width,
                height = result.height,
                transform = LayerTransform()
            )
            childLayers.add(lwLayer)
        }

        return LayerNode.LayerGroup(
            id = UUID.randomUUID().toString(),
            name = groupName,
            visible = true,
            opacity = 1.0f,
            blendMode = BlendModeType.Normal,
            children = childLayers,
            isCollapsed = false
        )
    }

    /**
     * Bakes the rendered colour stack result into an editable [LayerNode.LayerGroup]
     * where each functional stage is preserved as a separate layer in the [ArtDocument].
     */
    fun bakeToLayers(
        document: ArtDocument,
        result: ColorStackRenderResult,
        groupName: String = "Colour Lab (${result.stageOutputs.size} Stages)"
    ): LayerNode.LayerGroup {
        return toLayerGroup(document, result, groupName)
    }

    /**
     * Bakes a [com.artstudio.colorstack.strategy.StyleStrategyOutput] into an editable [LayerNode.LayerGroup]
     * where each style layer (BASE_FLAT, SHADOW, HIGHLIGHT, TEXTURE, LINEWORK) is preserved.
     */
    fun bakeToLayers(
        document: ArtDocument,
        output: com.artstudio.colorstack.strategy.StyleStrategyOutput,
        groupName: String = "${output.styleId.displayName} (${output.layers.size} Layers)"
    ): LayerNode.LayerGroup {
        return output.toLayerGroup(document, groupName)
    }

    /**
     * Bakes the rendered colour stack result directly into the document root group.
     */
    fun bakeToDocument(
        document: ArtDocument,
        result: ColorStackRenderResult,
        groupName: String = "Colour Lab (${result.stageOutputs.size} Stages)"
    ): ArtDocument {
        val bakedGroup = bakeToLayers(document, result, groupName)
        val updatedChildren = document.rootGroup.children + bakedGroup
        return document.copy(
            rootGroup = document.rootGroup.copy(children = updatedChildren),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Bakes a [com.artstudio.colorstack.strategy.StyleStrategyOutput] directly into the document root group.
     */
    fun bakeToDocument(
        document: ArtDocument,
        output: com.artstudio.colorstack.strategy.StyleStrategyOutput,
        groupName: String = "${output.styleId.displayName} (${output.layers.size} Layers)"
    ): ArtDocument {
        val bakedGroup = bakeToLayers(document, output, groupName)
        val updatedChildren = document.rootGroup.children + bakedGroup
        return document.copy(
            rootGroup = document.rootGroup.copy(children = updatedChildren),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun downsampleMask(
        source: ByteArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): ByteArray {
        val dst = ByteArray(dstW * dstH)
        val scaleX = srcW.toFloat() / dstW
        val scaleY = srcH.toFloat() / dstH

        for (dy in 0 until dstH) {
            val sy = (dy * scaleY).toInt().coerceIn(0, srcH - 1)
            val srcRow = sy * srcW
            val dstRow = dy * dstW
            for (dx in 0 until dstW) {
                val sx = (dx * scaleX).toInt().coerceIn(0, srcW - 1)
                dst[dstRow + dx] = source[srcRow + sx]
            }
        }
        return dst
    }

    private fun downsampleLinework(
        source: LineworkData,
        dstW: Int,
        dstH: Int
    ): LineworkData {
        val dst = IntArray(dstW * dstH)
        val scaleX = source.width.toFloat() / dstW
        val scaleY = source.height.toFloat() / dstH

        for (dy in 0 until dstH) {
            val sy = (dy * scaleY).toInt().coerceIn(0, source.height - 1)
            val srcRow = sy * source.width
            val dstRow = dy * dstW
            for (dx in 0 until dstW) {
                val sx = (dx * scaleX).toInt().coerceIn(0, source.width - 1)
                dst[dstRow + dx] = source.pixels[srcRow + sx]
            }
        }

        return LineworkData(
            width = dstW,
            height = dstH,
            pixels = dst,
            blendMode = source.blendMode,
            opacity = source.opacity
        )
    }
}
