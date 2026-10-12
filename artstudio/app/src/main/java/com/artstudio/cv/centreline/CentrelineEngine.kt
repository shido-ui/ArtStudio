package com.artstudio.cv.centreline

import com.artstudio.cv.CvDenoiseEngine
import com.artstudio.cv.CvThresholdEngine
import com.artstudio.reference.ReconstructionMode

/**
 * Authoritative, non-destructive engine for extracting plausible single-pen centreline paths from line art.
 *
 * Implements:
 * 1. Reconstruction mode gating: only executes when input mode is supported (or explicitly forced).
 * 2. Deterministic binarization (Otsu variance maximization or manual threshold).
 * 3. 2-Pass Chamfer distance transform for local stroke width recovery.
 * 4. Zhang-Suen 8-connected morphological skeletonization.
 * 5. Topological path tracing, junction discovery, and ambiguity review detection.
 * 6. Non-destructive guarantee: original pixel buffers are never mutated.
 */
object CentrelineEngine {

    /**
     * Extracts plausible single-pen centreline path candidates. Never mutates [pixels].
     */
    fun extractCentrelines(
        pixels: IntArray,
        width: Int,
        height: Int,
        mode: ReconstructionMode = ReconstructionMode.LINE_ART_VECTORIZATION,
        settings: CentrelineSettings = CentrelineSettings()
    ): CentrelineExtractionResult {
        require(width > 0 && height > 0) { "Dimensions must be positive ($width x $height)" }
        require(pixels.size == width * height) { "Pixel buffer size (${pixels.size}) does not match $width x $height" }

        val startTime = System.currentTimeMillis()
        val rt = Runtime.getRuntime()
        val memBefore = rt.totalMemory() - rt.freeMemory()

        // 1. Mode gating check
        val isSupported = CentrelineModeGating.isModeSupported(mode) || settings.forceExtraction
        if (!isSupported) {
            return CentrelineExtractionResult(
                width = width,
                height = height,
                isSupported = false,
                supportDiagnostic = "Mode '$mode' does not support single-pen centreline extraction without explicit forceExtraction = true",
                skeletonMask = IntArray(0),
                distanceTransform = FloatArray(0),
                paths = emptyList(),
                junctions = emptyList(),
                ambiguities = emptyList(),
                unresolvedBreakCount = 0,
                ambiguousForkCount = 0,
                elapsedTimeMs = System.currentTimeMillis() - startTime,
                peakMemoryBytes = 0L,
                settings = settings
            )
        }

        // 2. Grayscale & Binarization (preserves original pixels)
        val binaryMask = prepareBinaryMask(pixels, width, height, settings)

        // 3. Distance Transform
        val distTransform = if (settings.calculateStrokeWidth) {
            ZhangSuenThinning.computeDistanceTransform(binaryMask, width, height)
        } else {
            FloatArray(width * height) { 1.5f }
        }

        // 4. Zhang-Suen Morphological Thinning
        val skeleton = ZhangSuenThinning.thin(binaryMask, width, height)

        // 5. Topological Path Tracing & Ambiguity Detection
        val (paths, junctionAmbiguityPair) = CentrelinePathTracer.trace(
            skeleton = skeleton,
            distTransform = distTransform,
            width = width,
            height = height,
            settings = settings
        )
        val (junctions, ambiguities) = junctionAmbiguityPair

        val breakCount = ambiguities.count { it.type == AmbiguityType.POTENTIAL_STROKE_BREAK }
        val forkCount = ambiguities.count { it.type == AmbiguityType.AMBIGUOUS_FORK }

        val memAfter = rt.totalMemory() - rt.freeMemory()
        val peakMem = maxOf(width.toLong() * height.toLong() * 4L * 3L, memAfter - memBefore)
        val elapsedMs = System.currentTimeMillis() - startTime

        return CentrelineExtractionResult(
            width = width,
            height = height,
            isSupported = true,
            supportDiagnostic = "Successfully extracted centrelines under mode '$mode'",
            skeletonMask = skeleton,
            distanceTransform = distTransform,
            paths = paths,
            junctions = junctions,
            ambiguities = ambiguities,
            unresolvedBreakCount = breakCount,
            ambiguousForkCount = forkCount,
            elapsedTimeMs = elapsedMs,
            peakMemoryBytes = peakMem,
            settings = settings
        )
    }

    private fun prepareBinaryMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        settings: CentrelineSettings
    ): IntArray {
        // Check if already 8-bit binary mask (0 and 255)
        var isAlreadyBinary = true
        for (p in pixels) {
            if (p != 0 && p != 255 && p != 0xFF000000.toInt() && p != 0xFFFFFFFF.toInt()) {
                isAlreadyBinary = false
                break
            }
        }

        if (isAlreadyBinary) {
            val mask = IntArray(pixels.size)
            for (i in pixels.indices) {
                val v = pixels[i]
                mask[i] = if (settings.invertPolarity) {
                    if (v == 0 || v == 0xFF000000.toInt()) 255 else 0
                } else {
                    if (v == 255 || v == 0xFFFFFFFF.toInt()) 255 else 0
                }
            }
            return mask
        }

        val gray = CvDenoiseEngine.toGrayscale(pixels, width, height)
        return if (settings.useAutoThreshold) {
            CvThresholdEngine.otsuBinarize(
                gray = gray,
                width = width,
                height = height,
                invertPolarity = settings.invertPolarity
            ).second
        } else {
            CvThresholdEngine.manualThreshold(
                gray = gray,
                width = width,
                height = height,
                threshold = settings.binarizeThreshold,
                invertPolarity = settings.invertPolarity
            )
        }
    }
}
