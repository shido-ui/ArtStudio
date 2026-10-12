package com.artstudio.autodraw

import com.artstudio.autodraw.integration.AutoDrawDocumentIntegrator
import com.artstudio.autodraw.integration.AutoDrawLayerInput
import com.artstudio.autodraw.integration.AutoDrawLayerRole
import com.artstudio.autodraw.integration.AutoDrawReconstructionConfig
import com.artstudio.autodraw.integration.AutoDrawResult
import com.artstudio.autodraw.integration.IntegrationResult
import com.artstudio.autodraw.schedule.DrawingSchedule
import com.artstudio.autodraw.schedule.DrawingScheduleGenerator
import com.artstudio.autodraw.schedule.SchedulablePath
import com.artstudio.core.FeatureFlags
import com.artstudio.cv.ContourCandidate
import com.artstudio.cv.CvPipeline
import com.artstudio.cv.CvPipelineParameters
import com.artstudio.cv.pathfit.PathFitSettings
import com.artstudio.document.model.ArtDocument
import com.artstudio.vector.VectorObject
import com.artstudio.vector.VectorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Result data container produced by [AutoDrawV1Pipeline].
 */
data class AutoDrawV1Result(
    val originalWidth: Int,
    val originalHeight: Int,
    val processedWidth: Int,
    val processedHeight: Int,
    val scaleFactor: Float,
    val contours: List<ContourCandidate>,
    val vectorObjects: List<VectorObject>,
    val schedule: DrawingSchedule,
    val executionTimeMs: Long
)

/**
 * Authoritative AutoDraw v1 Classical Computer Vision pipeline.
 *
 * Implements:
 * 1. Safe downscaling for bounded memory execution on large line art images.
 * 2. Background job execution with cooperative cancellation and progressive telemetry.
 * 3. Topological contour extraction and polygonal decimation (RDP).
 * 4. Corner-preserving Bezier path vectorization.
 * 5. Stroke replay schedule generation and non-destructive ArtDocument layer integration.
 */
object AutoDrawV1Pipeline {
    const val DEFAULT_MAX_DIMENSION: Int = 1536

    /**
     * Executes the classical CV AutoDraw pipeline off the main thread.
     */
    suspend fun processLineArt(
        pixels: IntArray,
        width: Int,
        height: Int,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        normalizeIllumination: Boolean = true,
        onProgress: ((progress: Float, stage: String) -> Unit)? = null
    ): AutoDrawV1Result = withContext(Dispatchers.Default) {
        if (!FeatureFlags.isAutoDrawV1Enabled) {
            throw IllegalStateException("AutoDraw v1 is disabled by FeatureFlags.isAutoDrawV1Enabled")
        }

        val startTime = System.currentTimeMillis()
        onProgress?.invoke(0.05f, "Preparing line art image")
        coroutineContext.ensureActive()

        // 1. Downscaling for memory boundedness
        val scaleFactor: Float
        val procW: Int
        val procH: Int
        val procPixels: IntArray

        if (width > maxDimension || height > maxDimension) {
            scaleFactor = maxDimension.toFloat() / max(width, height)
            procW = max(32, (width * scaleFactor).roundToInt())
            procH = max(32, (height * scaleFactor).roundToInt())
            onProgress?.invoke(0.15f, "Downscaling to ${procW}x${procH}")
            procPixels = downscaleBilinear(pixels, width, height, procW, procH)
        } else {
            scaleFactor = 1.0f
            procW = width
            procH = height
            procPixels = pixels.clone()
        }
        coroutineContext.ensureActive()

        // 2. Run Classical CV Pipeline with cooperative cancellation
        onProgress?.invoke(0.35f, "Denoising and binarizing linework")
        coroutineContext.ensureActive()

        val cvParams = CvPipelineParameters(
            normalizeIllumination = normalizeIllumination,
            speckleRemovalMinPixels = 4,
            maxDimension = maxDimension,
            vectorizeContours = true,
            pathFitSettings = PathFitSettings(
                maxFittingError = 1.5f,
                cornerAngleThresholdDeg = 100f
            )
        )
        val cvResult = CvPipeline.process(
            pixels = procPixels,
            width = procW,
            height = procH,
            params = cvParams,
            cancellationSignal = { coroutineContext.ensureActive() },
            onProgress = { p, stage ->
                // Map sub-progress from 0.35 to 0.75
                val mapped = 0.35f + p * 0.40f
                onProgress?.invoke(mapped, stage)
            }
        )
        coroutineContext.ensureActive()

        onProgress?.invoke(0.80f, "Extracting contours and fitting vector paths")
        val rawVectorObjects = cvResult.vectorizationResult?.toVectorObjects() ?: emptyList()

        // 3. Upscale vector paths back to original coordinate system if downscaled
        val finalVectorObjects = if (scaleFactor != 1.0f) {
            val invScale = 1.0f / scaleFactor
            rawVectorObjects.map { vo ->
                vo.copy(
                    anchors = vo.anchors.map { anchor ->
                        anchor.copy(
                            x = anchor.x * invScale,
                            y = anchor.y * invScale,
                            handleInX = anchor.handleInX?.let { it * invScale },
                            handleInY = anchor.handleInY?.let { it * invScale },
                            handleOutX = anchor.handleOutX?.let { it * invScale },
                            handleOutY = anchor.handleOutY?.let { it * invScale }
                        )
                    },
                    segments = vo.segments.map { seg ->
                        when (seg) {
                            is VectorSegment.MoveTo -> VectorSegment.MoveTo(seg.x * invScale, seg.y * invScale)
                            is VectorSegment.LineTo -> VectorSegment.LineTo(seg.x * invScale, seg.y * invScale)
                            is VectorSegment.CubicTo -> VectorSegment.CubicTo(
                                seg.x1 * invScale, seg.y1 * invScale,
                                seg.x2 * invScale, seg.y2 * invScale,
                                seg.x * invScale, seg.y * invScale
                            )
                            is VectorSegment.QuadTo -> VectorSegment.QuadTo(
                                seg.x1 * invScale, seg.y1 * invScale,
                                seg.x * invScale, seg.y * invScale
                            )
                            VectorSegment.Close -> VectorSegment.Close
                        }
                    }
                )
            }
        } else {
            rawVectorObjects
        }
        coroutineContext.ensureActive()

        // 4. Generate Drawing Schedule for Stroke Replay
        onProgress?.invoke(0.90f, "Generating stroke replay schedule")
        val schedulablePaths = finalVectorObjects.map { vo ->
            SchedulablePath.fromVectorObject(vo, layerId = "autodraw_linework")
        }
        val schedule = DrawingScheduleGenerator.generateSchedule(schedulablePaths)
        coroutineContext.ensureActive()

        onProgress?.invoke(1.0f, "AutoDraw reconstruction complete")
        val elapsed = System.currentTimeMillis() - startTime

        val finalContours = if (scaleFactor != 1.0f) {
            val invScale = 1.0f / scaleFactor
            cvResult.contours.map { c ->
                val mappedPoints = c.points.map { com.artstudio.cv.CvPoint(it.x * invScale, it.y * invScale) }
                val mappedSimplified = c.simplifiedPoints.map { com.artstudio.cv.CvPoint(it.x * invScale, it.y * invScale) }
                val mappedBbox = com.artstudio.cv.CvRect(
                    minX = c.boundingBox.minX * invScale,
                    minY = c.boundingBox.minY * invScale,
                    maxX = c.boundingBox.maxX * invScale,
                    maxY = c.boundingBox.maxY * invScale
                )
                val mappedCentroid = com.artstudio.cv.CvPoint(c.centroid.x * invScale, c.centroid.y * invScale)
                c.copy(
                    points = mappedPoints,
                    simplifiedPoints = mappedSimplified,
                    area = c.area * (invScale * invScale),
                    perimeter = c.perimeter * invScale,
                    boundingBox = mappedBbox,
                    centroid = mappedCentroid
                )
            }
        } else {
            cvResult.contours
        }

        return@withContext AutoDrawV1Result(
            originalWidth = width,
            originalHeight = height,
            processedWidth = procW,
            processedHeight = procH,
            scaleFactor = scaleFactor,
            contours = finalContours,
            vectorObjects = finalVectorObjects,
            schedule = schedule,
            executionTimeMs = elapsed
        )
    }

    /**
     * Integrates the AutoDraw v1 result into a target ArtDocument.
     */
    fun integrateIntoDocument(
        document: ArtDocument,
        result: AutoDrawV1Result
    ): IntegrationResult {
        val layerInput = AutoDrawLayerInput(
            role = AutoDrawLayerRole.PATHS,
            name = "AutoDraw Linework",
            vectorObjects = result.vectorObjects
        )
        val autoDrawResult = AutoDrawResult(
            config = AutoDrawReconstructionConfig(
                algorithm = "classical_cv_v1",
                stylePresetId = "line_art_contours"
            ),
            regions = emptyList(),
            layers = listOf(layerInput),
            width = document.canvas.width,
            height = document.canvas.height
        )
        return AutoDrawDocumentIntegrator.integrateApprovedResult(document, autoDrawResult)
    }

    private fun downscaleBilinear(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): IntArray {
        val dst = IntArray(dstW * dstH)
        val xRatio = srcW.toFloat() / dstW
        val yRatio = srcH.toFloat() / dstH

        for (y in 0 until dstH) {
            val srcY = (y * yRatio).toInt().coerceIn(0, srcH - 1)
            val srcRow = srcY * srcW
            val dstRow = y * dstW
            for (x in 0 until dstW) {
                val srcX = (x * xRatio).toInt().coerceIn(0, srcW - 1)
                dst[dstRow + x] = src[srcRow + srcX]
            }
        }
        return dst
    }
}
