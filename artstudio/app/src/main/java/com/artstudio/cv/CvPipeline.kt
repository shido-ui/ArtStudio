package com.artstudio.cv

import com.artstudio.cv.centreline.CentrelineEngine
import com.artstudio.cv.pathfit.ContourVectorizationEngine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Authoritative, deterministic Computer Vision Pipeline for raster illustration analysis.
 *
 * Fully preserves the original input pixels and executes:
 * 1. Safe max-dimension downscaling stage (long edge <= 1536) for memory boundedness.
 * 2. Cooperative cancellation checks and granular progress callbacks in every stage.
 * 3. Grayscale conversion (Rec. 709).
 * 4. Illumination & paper shadow normalization for photographed sketches.
 * 5. Configurable denoising (Median Despeckle / Gaussian / Bilateral).
 * 6. Thresholding (Otsu Global / Adaptive Local / Manual) and speckle noise suppression.
 * 7. Edge Detection (Canny with NMS & hysteresis / Sobel).
 * 8. Moore-neighborhood topological contour extraction with Jacob's stopping criterion.
 * 9. Stack-safe iterative RDP polygonal decimation and coordinate mapping back to canvas space.
 * 10. Latency & memory tracking.
 */
object CvPipeline {

    /**
     * Executes the full deterministic analysis pipeline. Never modifies the source [pixels] array.
     */
    fun process(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: CvPipelineParameters = CvPipelineParameters(),
        cancellationSignal: (() -> Unit)? = null,
        onProgress: ((progress: Float, stage: String) -> Unit)? = null
    ): CvPipelineResult {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(pixels.size == width * height) { "Pixel buffer size (${pixels.size}) does not match $width x $height" }

        val startTime = System.currentTimeMillis()
        val rt = Runtime.getRuntime()
        val memBefore = rt.totalMemory() - rt.freeMemory()

        onProgress?.invoke(0.05f, "Preparing image analysis")
        cancellationSignal?.invoke()

        // 1. Max-Dimension Downscale Stage for Memory Boundedness (e.g. Long edge <= 1536)
        val scaleFactor: Float
        val procW: Int
        val procH: Int
        val procPixels: IntArray

        if (params.maxDimension > 0 && (width > params.maxDimension || height > params.maxDimension)) {
            scaleFactor = params.maxDimension.toFloat() / max(width, height)
            procW = max(32, (width * scaleFactor).roundToInt())
            procH = max(32, (height * scaleFactor).roundToInt())
            onProgress?.invoke(0.10f, "Downscaling to ${procW}x${procH} for memory safety")
            procPixels = downscaleBilinear(pixels, width, height, procW, procH)
        } else {
            scaleFactor = 1.0f
            procW = width
            procH = height
            procPixels = pixels
        }
        cancellationSignal?.invoke()

        // 2. Grayscale Conversion (Preserves source array)
        onProgress?.invoke(0.20f, "Converting to grayscale")
        val grayscale = CvDenoiseEngine.toGrayscale(procPixels, procW, procH)
        cancellationSignal?.invoke()

        // 3. Optional Illumination Normalization for Photographed/Scanned Sketches
        val normalizedGray = if (params.normalizeIllumination) {
            onProgress?.invoke(0.25f, "Normalizing sketch illumination and shadows")
            CvDenoiseEngine.normalizeIllumination(grayscale, procW, procH, radius = params.illuminationRadius)
        } else {
            grayscale
        }
        cancellationSignal?.invoke()

        // 4. Denoising
        onProgress?.invoke(0.35f, "Denoising linework")
        val denoised = when (params.denoiseMode) {
            DenoiseMode.NONE -> normalizedGray.clone()
            DenoiseMode.GAUSSIAN_BLUR -> CvDenoiseEngine.gaussianBlur(
                gray = normalizedGray,
                width = procW,
                height = procH,
                radius = params.denoiseRadius,
                sigma = params.gaussianSigma
            )
            DenoiseMode.MEDIAN_DESPECKLE -> CvDenoiseEngine.medianDespeckle(
                gray = normalizedGray,
                width = procW,
                height = procH,
                radius = params.denoiseRadius
            )
            DenoiseMode.BILATERAL -> CvDenoiseEngine.bilateralFilter(
                gray = normalizedGray,
                width = procW,
                height = procH,
                radius = params.denoiseRadius
            )
        }
        cancellationSignal?.invoke()

        // 5. Thresholding
        onProgress?.invoke(0.50f, "Thresholding linework")
        val rawThresholded = when (params.thresholdMode) {
            ThresholdMode.OTSU_GLOBAL -> CvThresholdEngine.otsuBinarize(
                gray = denoised,
                width = procW,
                height = procH,
                invertPolarity = params.invertPolarity
            ).second
            ThresholdMode.ADAPTIVE_LOCAL_MEAN -> CvThresholdEngine.adaptiveLocalThreshold(
                gray = denoised,
                width = procW,
                height = procH,
                windowRadius = params.adaptiveWindowRadius,
                c = params.adaptiveConstantC,
                invertPolarity = params.invertPolarity
            )
            ThresholdMode.MANUAL -> CvThresholdEngine.manualThreshold(
                gray = denoised,
                width = procW,
                height = procH,
                threshold = params.manualThreshold,
                invertPolarity = params.invertPolarity
            )
        }
        cancellationSignal?.invoke()

        // 6. Optional Speckle Removal
        val thresholded = if (params.speckleRemovalMinPixels > 1) {
            onProgress?.invoke(0.55f, "Filtering noise speckles")
            CvThresholdEngine.despeckleBinaryMask(rawThresholded, procW, procH, minPixels = params.speckleRemovalMinPixels)
        } else {
            rawThresholded
        }
        cancellationSignal?.invoke()

        // 7. Edge Detection
        onProgress?.invoke(0.65f, "Detecting stroke edges")
        val edges = when (params.edgeMode) {
            EdgeMode.SOBEL -> CvEdgeEngine.sobelEdges(
                gray = denoised,
                width = procW,
                height = procH,
                threshold = params.sobelThreshold
            ).second
            EdgeMode.CANNY -> CvEdgeEngine.cannyEdges(
                gray = denoised,
                width = procW,
                height = procH,
                lowThreshold = params.cannyLowThreshold,
                highThreshold = params.cannyHighThreshold
            )
        }
        cancellationSignal?.invoke()

        // 8. Contour Candidate Extraction
        // For line drawings, canny edges yield crisp stroke contours.
        // If edges are sparse, fall back to thresholded boundaries.
        val contourMask = when (params.contourSource) {
            ContourSource.EDGES -> edges
            ContourSource.THRESHOLD -> thresholded
            ContourSource.AUTO -> if (hasEdgePixels(edges)) edges else thresholded
        }
        val rawContours = CvContourEngine.extractContours(
            binaryMask = contourMask,
            width = procW,
            height = procH,
            minArea = params.contourMinArea,
            rdpEpsilon = params.contourRdpEpsilon
        )
        cancellationSignal?.invoke()

        // 9. Coordinate Mapping back to original canvas space if downscaled
        val contours = if (scaleFactor != 1.0f) {
            val invScale = 1.0f / scaleFactor
            rawContours.map { c ->
                val mappedPoints = c.points.map { CvPoint(it.x * invScale, it.y * invScale) }
                val mappedSimplified = c.simplifiedPoints.map { CvPoint(it.x * invScale, it.y * invScale) }
                val mappedBbox = CvRect(
                    minX = c.boundingBox.minX * invScale,
                    minY = c.boundingBox.minY * invScale,
                    maxX = c.boundingBox.maxX * invScale,
                    maxY = c.boundingBox.maxY * invScale
                )
                val mappedCentroid = CvPoint(c.centroid.x * invScale, c.centroid.y * invScale)
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
            rawContours
        }
        cancellationSignal?.invoke()

        // 10. Optional Single-Pen Centreline Extraction
        val centreline = if (params.extractCentrelines) {
            onProgress?.invoke(0.85f, "Extracting stroke centrelines")
            CentrelineEngine.extractCentrelines(
                pixels = procPixels,
                width = procW,
                height = procH,
                settings = params.centrelineSettings
            )
        } else {
            null
        }
        cancellationSignal?.invoke()

        // 11. Optional Contour Vectorization into Editable Paths
        val vectorization = if (params.vectorizeContours) {
            onProgress?.invoke(0.92f, "Fitting Bezier curves to contours")
            ContourVectorizationEngine.vectorizeContours(
                contours = contours,
                settings = params.pathFitSettings
            )
        } else {
            null
        }
        cancellationSignal?.invoke()

        onProgress?.invoke(1.0f, "Analysis complete")
        val memAfter = rt.totalMemory() - rt.freeMemory()
        val peakMem = max(procW.toLong() * procH.toLong() * 4L * 3L, memAfter - memBefore)
        val elapsedMs = System.currentTimeMillis() - startTime

        return CvPipelineResult(
            width = procW,
            height = procH,
            grayscale = grayscale,
            denoised = denoised,
            thresholded = thresholded,
            edges = edges,
            contours = contours,
            elapsedTimeMs = elapsedMs,
            peakMemoryBytes = peakMem,
            parameters = params,
            centrelineResult = centreline,
            vectorizationResult = vectorization
        )
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

    private fun hasEdgePixels(edges: IntArray): Boolean {
        for (p in edges) {
            if (p == 255) return true
        }
        return false
    }
}
