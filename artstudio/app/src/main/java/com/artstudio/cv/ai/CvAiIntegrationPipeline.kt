package com.artstudio.cv.ai

import com.artstudio.ai.model.*
import com.artstudio.cv.region.RegionMask
import com.artstudio.cv.region.RegionMaskConfig
import com.artstudio.cv.region.RegionMaskEngine
import com.artstudio.cv.region.toRegionMask
import com.artstudio.document.model.AnalysisProvenance
import com.artstudio.job.JobCancellationToken
import kotlin.math.max
import kotlin.math.min

/**
 * Strategy chosen by the artist when resolving contradictory classical CV and AI region proposals.
 */
enum class CandidateResolution {
    /** Retain classical CV deterministic boundary (Default). */
    USE_CLASSICAL,

    /** Accept optional AI-assisted candidate mask. */
    USE_AI_CANDIDATE,

    /** Union both masks together. */
    USE_UNION_HYBRID
}

/**
 * Composite dual-candidate review proposal presenting classical CV and optional AI candidate masks side-by-side.
 *
 * In strict alignment with ADR-050:
 * - The classical CV result is NEVER silently discarded.
 * - If AI output contradicts classical CV, both are surfaced to the user.
 */
data class RegionSegmentationProposal(
    val regionId: String,
    val classicalMask: RegionMask,
    val aiCandidateMask: RegionMask?,
    val hasContradiction: Boolean,
    val contradictionDetails: String? = null,
    val resolution: CandidateResolution = CandidateResolution.USE_CLASSICAL,
    val provenance: AnalysisProvenance
) {
    /**
     * Resolves the authoritative output mask based on the selected resolution strategy.
     */
    fun resolve(strategy: CandidateResolution = resolution): RegionMask {
        return when (strategy) {
            CandidateResolution.USE_CLASSICAL -> classicalMask
            CandidateResolution.USE_AI_CANDIDATE -> aiCandidateMask ?: classicalMask
            CandidateResolution.USE_UNION_HYBRID -> {
                if (aiCandidateMask == null) return classicalMask
                val unionBuffer = ByteArray(classicalMask.mask.size)
                for (i in unionBuffer.indices) {
                    val c = classicalMask.mask[i].toInt() and 0xFF
                    val a = aiCandidateMask.mask[i].toInt() and 0xFF
                    unionBuffer[i] = max(c, a).toByte()
                }
                RegionMask(
                    regionId = classicalMask.regionId,
                    width = classicalMask.width,
                    height = classicalMask.height,
                    mask = unionBuffer,
                    semanticTag = if (aiCandidateMask.semanticTag != RegionSemanticTag.UNKNOWN) {
                        aiCandidateMask.semanticTag
                    } else classicalMask.semanticTag,
                    confidence = (classicalMask.confidence + aiCandidateMask.confidence) / 2f,
                    source = RegionSource.HYBRID,
                    aiAugmentation = aiCandidateMask.aiAugmentation
                )
            }
        }
    }
}

/**
 * Integration bridge between the authoritative Classical Computer Vision pipeline
 * and the optional on-device AI Model Registry.
 *
 * In strict alignment with ADR-049 and ADR-050:
 * - Classical CV runs authoritatively and first.
 * - AI inference executes on a downsampled preview surface (<= 512x512).
 * - Models act as optional supplements and suggestions, never replacing classical CV.
 * - Pure-Kotlin Semantic Heuristic Assist acts as the 0-MB fallback when no AI model is downloaded.
 */
object CvAiIntegrationPipeline {

    /**
     * Performs region segmentation with optional AI assistance.
     *
     * @param pixels ARGB canvas or reference pixel buffer.
     * @param width Canvas width.
     * @param height Canvas height.
     * @param seedX Seed X coordinate for flood or point prompt.
     * @param seedY Seed Y coordinate for flood or point prompt.
     * @param registry Active AI model registry.
     * @param config Classical CV region configuration.
     * @param cancellationToken Optional cooperative cancellation token.
     * @return Dual-candidate proposal with classical mask and optional AI candidate.
     */
    suspend fun segmentRegionWithAiSupplement(
        pixels: IntArray,
        width: Int,
        height: Int,
        seedX: Int,
        seedY: Int,
        registry: AiModelRegistry,
        config: RegionMaskConfig = RegionMaskConfig(),
        cancellationToken: JobCancellationToken? = null
    ): RegionSegmentationProposal {
        val regionId = "region_${seedX}_${seedY}"

        // 1. Classical CV segmentation executes authoritatively
        val cvResult = RegionMaskEngine.segmentRegion(
            pixels = pixels,
            width = width,
            height = height,
            seedX = seedX,
            seedY = seedY,
            config = config
        )

        // Classify classical mask using Semantic Heuristic Assist
        val (h, s, v) = SemanticHeuristicAssist.rgbToHsv(pixels[seedY * width + seedX])
        val classicalSemanticTag = SemanticHeuristicAssist.classifyHsv(h, s, v, seedX, seedY, width, height)
        val classicalMask = cvResult.toRegionMask(
            semanticTag = classicalSemanticTag,
            source = RegionSource.CLASSICAL_CV
        )

        // 2. Check if a neural model or AI assist is active
        val hasNeuralModel = registry.hasAnyNeuralModelLoaded()
        val adapter = registry.getActiveAdapter()

        var aiCandidateMask: RegionMask? = null
        var aiAugmentation: AiAugmentation? = null

        if (hasNeuralModel || adapter.descriptor.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) {
            val inferenceParams = InferenceParams(
                promptModality = PromptModality.POINT,
                pointPrompts = listOf(Pair(seedX.toFloat(), seedY.toFloat())),
                maxResolution = 512
            )

            val inferResult = adapter.infer(
                pixels = pixels,
                width = width,
                height = height,
                params = inferenceParams,
                cancellationToken = cancellationToken
            )

            if (inferResult is InferenceResult.Segmentation) {
                // Upscale or resample mask to canvas dimensions if downsampled
                val fullMask = if (inferResult.width == width && inferResult.height == height) {
                    inferResult.mask
                } else {
                    resampleMask(inferResult.mask, inferResult.width, inferResult.height, width, height)
                }

                aiAugmentation = AiAugmentation(
                    modelId = adapter.descriptor.id,
                    modelName = adapter.descriptor.displayName,
                    modelSha256 = adapter.descriptor.sha256,
                    inferenceLatencyMs = inferResult.latencyMs,
                    confidence = inferResult.confidence,
                    semanticTag = inferResult.semanticTag,
                    promptModality = PromptModality.POINT
                )

                aiCandidateMask = RegionMask(
                    regionId = "${regionId}_ai",
                    width = width,
                    height = height,
                    mask = fullMask,
                    semanticTag = inferResult.semanticTag,
                    confidence = inferResult.confidence,
                    source = if (adapter.descriptor.runtimeType == ModelRuntimeType.CUSTOM_HEURISTIC) {
                        RegionSource.CLASSICAL_CV
                    } else {
                        RegionSource.AI_MODEL
                    },
                    aiAugmentation = aiAugmentation
                )
            }
        }

        // 3. Contradiction Detection (IoU comparison)
        var hasContradiction = false
        var contradictionDetails: String? = null

        if (aiCandidateMask != null && aiCandidateMask.source == RegionSource.AI_MODEL) {
            val iou = calculateMaskIoU(classicalMask.mask, aiCandidateMask.mask)
            if (iou < 0.50) {
                hasContradiction = true
                contradictionDetails = String.format(
                    "AI candidate deviates significantly from classical CV boundary (IoU = %.2f). " +
                            "Classical CV boundary is preserved; review both candidates below.",
                    iou
                )
            }
        }

        val provenance = AnalysisProvenance(
            referenceHash = null,
            classicalCvAlgorithm = "classical_cv_morphological_v1",
            aiAugmentation = aiAugmentation
        )

        return RegionSegmentationProposal(
            regionId = regionId,
            classicalMask = classicalMask,
            aiCandidateMask = aiCandidateMask,
            hasContradiction = hasContradiction,
            contradictionDetails = contradictionDetails,
            resolution = CandidateResolution.USE_CLASSICAL,
            provenance = provenance
        )
    }

    /**
     * Pre-processes an imported reference photo through AnimeGANv2 style transfer (if downloaded)
     * as an auxiliary reference hint.
     *
     * In strict alignment with ADR-050:
     * - The stylized output is an advisory hint only, NEVER the final artwork raster.
     * - Classical CV still extracts all editable paths and contours.
     */
    suspend fun stylizeReferenceWithAiHint(
        referencePixels: IntArray,
        width: Int,
        height: Int,
        registry: AiModelRegistry,
        cancellationToken: JobCancellationToken? = null
    ): Pair<IntArray, AiAugmentation?> {
        val animeModel = registry.getModel(AiModelCatalog.ID_ANIMEGAN_V2)
        if (animeModel == null || !registry.isModelDownloaded(animeModel.id)) {
            // No AnimeGAN downloaded; return untouched reference
            return Pair(referencePixels, null)
        }

        val adapter = TfliteModelAdapter(animeModel, registry.downloader.getModelFile(animeModel))
        val loadResult = adapter.load()
        if (loadResult !is LoadResult.Success) {
            return Pair(referencePixels, null)
        }

        val inferResult = adapter.infer(
            pixels = referencePixels,
            width = width,
            height = height,
            params = InferenceParams(promptModality = PromptModality.AUTOMATIC),
            cancellationToken = cancellationToken
        )

        return if (inferResult is InferenceResult.StyleTransfer) {
            val aug = AiAugmentation(
                modelId = animeModel.id,
                modelName = animeModel.displayName,
                modelSha256 = animeModel.sha256,
                inferenceLatencyMs = inferResult.latencyMs,
                confidence = 0.90f,
                promptModality = PromptModality.AUTOMATIC
            )
            Pair(inferResult.stylizedPixels, aug)
        } else {
            Pair(referencePixels, null)
        }
    }

    /**
     * Applies Real-ESRGAN super-resolution upscaling (2x/4x) to an imported reference image.
     *
     * In strict alignment with Prompt 55 constraints:
     * - Output is strictly capped at 4096 x 4096.
     * - NEVER applied to the user's artwork paint layers.
     */
    suspend fun superResolveReference(
        referencePixels: IntArray,
        width: Int,
        height: Int,
        registry: AiModelRegistry,
        cancellationToken: JobCancellationToken? = null
    ): Triple<IntArray, Int, Int> {
        val esrganModel = registry.getModel(AiModelCatalog.ID_REAL_ESRGAN)
        if (esrganModel == null || !registry.isModelDownloaded(esrganModel.id)) {
            return Triple(referencePixels, width, height)
        }

        val adapter = TfliteModelAdapter(esrganModel, registry.downloader.getModelFile(esrganModel))
        if (adapter.load() !is LoadResult.Success) {
            return Triple(referencePixels, width, height)
        }

        val inferResult = adapter.infer(
            pixels = referencePixels,
            width = width,
            height = height,
            params = InferenceParams(promptModality = PromptModality.AUTOMATIC),
            cancellationToken = cancellationToken
        )

        return if (inferResult is InferenceResult.SuperResolution) {
            val outW = min(4096, inferResult.width)
            val outH = min(4096, inferResult.height)
            Triple(inferResult.upscaledPixels, outW, outH)
        } else {
            Triple(referencePixels, width, height)
        }
    }

    /**
     * Computes the Intersection-over-Union (IoU) between two 8-bit masks.
     */
    fun calculateMaskIoU(maskA: ByteArray, maskB: ByteArray): Double {
        if (maskA.size != maskB.size || maskA.isEmpty()) return 0.0

        var intersection = 0
        var union = 0

        for (i in maskA.indices) {
            val a = (maskA[i].toInt() and 0xFF) > 128
            val b = (maskB[i].toInt() and 0xFF) > 128

            if (a && b) intersection++
            if (a || b) union++
        }

        return if (union == 0) 1.0 else intersection.toDouble() / union.toDouble()
    }

    private fun resampleMask(src: ByteArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): ByteArray {
        val dst = ByteArray(dstW * dstH)
        val xRatio = srcW.toFloat() / dstW
        val yRatio = srcH.toFloat() / dstH

        for (y in 0 until dstH) {
            val srcY = (y * yRatio).toInt().coerceIn(0, srcH - 1)
            val rowOffset = y * dstW
            val srcRowOffset = srcY * srcW
            for (x in 0 until dstW) {
                val srcX = (x * xRatio).toInt().coerceIn(0, srcW - 1)
                dst[rowOffset + x] = src[srcRowOffset + srcX]
            }
        }
        return dst
    }
}
