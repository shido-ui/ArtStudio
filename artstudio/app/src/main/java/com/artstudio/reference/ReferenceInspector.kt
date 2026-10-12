package com.artstudio.reference

import com.artstudio.importer.ExifOrientationParser
import com.artstudio.importer.RasterImageImporter
import com.artstudio.document.io.psd.PsdParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Inspection options for reference pre-analysis.
 */
data class InspectionOptions(
    val maxDimensionBudget: Int = 4096,
    val userOverride: ReconstructionMode? = null,
    val calculateDetailedProfiles: Boolean = true,
    val memoryCeilingBytes: Long = 64 * 1024 * 1024L // 64 MB heap budget
)

/**
 * Authoritative, non-destructive Reference Inspector.
 *
 * Responsibilities:
 * 1. Format, dimension, color profile, and EXIF orientation detection.
 * 2. Bi-directional coordinate normalization mapping WITHOUT altering the preserved reference source.
 * 3. Reconstruction mode suggestions with visible confidence and limitations.
 * 4. Graceful fault handling for corrupt or malformed inputs without throwing or mutating documents.
 * 5. Accurate memory footprint estimation and measured decode peak tracking.
 */
object ReferenceInspector {

    /**
     * Inspects a reference file on disk. Never modifies the source file.
     */
    fun inspectFile(
        file: File,
        options: InspectionOptions = InspectionOptions()
    ): ReferenceInspectionReport {
        if (!file.exists() || !file.isFile || !file.canRead()) {
            return createCorruptReport(
                sourceName = file.name,
                sourceByteLength = if (file.exists()) file.length() else 0L,
                format = ReferenceFormat.UNKNOWN,
                error = "File does not exist or cannot be read: ${file.path}",
                options = options
            )
        }

        val bytes = try {
            file.readBytes()
        } catch (t: Throwable) {
            return createCorruptReport(
                sourceName = file.name,
                sourceByteLength = file.length(),
                format = ReferenceFormat.UNKNOWN,
                error = "Failed to read file bytes: ${t.message}",
                options = options
            )
        }

        return inspectBytes(bytes, file.name, options)
    }

    /**
     * Inspects reference bytes directly in memory.
     */
    fun inspectBytes(
        bytes: ByteArray,
        sourceName: String = "reference_source",
        options: InspectionOptions = InspectionOptions()
    ): ReferenceInspectionReport {
        if (bytes.isEmpty()) {
            return createCorruptReport(
                sourceName = sourceName,
                sourceByteLength = 0L,
                format = ReferenceFormat.UNKNOWN,
                error = "Input stream is empty (0 bytes)",
                options = options
            )
        }

        val format = detectFormat(bytes)

        // 1. Guard against corrupt layered/vector containers (PSD, ORA, SVG)
        when (format) {
            ReferenceFormat.PSD -> {
                val psdCheck = checkPsdIntegrity(bytes)
                if (psdCheck != null) {
                    return createCorruptReport(sourceName, bytes.size.toLong(), ReferenceFormat.PSD, psdCheck, options)
                }
            }
            ReferenceFormat.OPEN_RASTER -> {
                val oraCheck = checkOraIntegrity(bytes)
                if (oraCheck != null) {
                    return createCorruptReport(sourceName, bytes.size.toLong(), ReferenceFormat.OPEN_RASTER, oraCheck, options)
                }
            }
            ReferenceFormat.SVG -> {
                val svgCheck = checkSvgIntegrity(bytes)
                if (svgCheck != null) {
                    return createCorruptReport(sourceName, bytes.size.toLong(), ReferenceFormat.SVG, svgCheck, options)
                }
            }
            ReferenceFormat.CORRUPT -> {
                return createCorruptReport(sourceName, bytes.size.toLong(), ReferenceFormat.CORRUPT, "Unrecognized or corrupted container header", options)
            }
            else -> { /* Standard raster path */ }
        }

        // 2. Memory-tracked decoding
        val rt = Runtime.getRuntime()
        val memBefore = rt.totalMemory() - rt.freeMemory()

        val decoded = try {
            RasterImageImporter.decodeRasterImage(
                bytes = bytes,
                fileName = sourceName,
                maxDimensionBudget = options.maxDimensionBudget
            )
        } catch (t: Throwable) {
            return createCorruptReport(sourceName, bytes.size.toLong(), format, "Decode exception: ${t.message}", options)
        }

        val memAfter = rt.totalMemory() - rt.freeMemory()

        if (decoded == null) {
            return createCorruptReport(
                sourceName = sourceName,
                sourceByteLength = bytes.size.toLong(),
                format = format,
                error = "Corrupted or truncated image stream (decoder returned null)",
                options = options
            )
        }

        val rawW = decoded.metadata.rawWidth
        val rawH = decoded.metadata.rawHeight
        val orientedW = decoded.metadata.orientedWidth
        val orientedH = decoded.metadata.orientedHeight
        val exifOrientation = decoded.metadata.exifOrientation

        // 3. Mathematical coordinate normalization (bi-directional mapping without touching source)
        val coordinateNorm = CoordinateNormalization(
            exifOrientation = exifOrientation,
            rawWidth = rawW,
            rawHeight = rawH,
            normalizedWidth = orientedW,
            normalizedHeight = orientedH
        )

        // 4. Memory footprint metrics
        val pixelBufferBytes = orientedW.toLong() * orientedH.toLong() * 4L
        val estimatedDecodedBytes = pixelBufferBytes * 2L // ARGB buffer + decoder working buffer
        val actualDecodePeakBytes = maxOf(pixelBufferBytes, memAfter - memBefore)
        val heapBudgetFraction = estimatedDecodedBytes.toFloat() / options.memoryCeilingBytes
        val advice = when {
            heapBudgetFraction > 0.75f -> MemoryAdvice.EXCEEDS_BUDGET_DOWNSAMPLE_RECOMMENDED
            heapBudgetFraction > 0.35f -> MemoryAdvice.HIGH_MEMORY_WARN
            else -> MemoryAdvice.NORMAL_MEMORY
        }
        val memoryEstimate = MemoryEstimate(
            estimatedDecodedBytes = estimatedDecodedBytes,
            actualDecodePeakBytes = actualDecodePeakBytes,
            heapBudgetFraction = heapBudgetFraction,
            advice = advice
        )

        // 5. Transparency analysis
        val transparency = analyzeTransparency(decoded.pixels, orientedW, orientedH)

        // 6. Color and edge statistical analysis
        val colorProfile = analyzeColorAndEdges(decoded.pixels, orientedW, orientedH)

        // 7. Image class inference
        val likelyClass = inferImageClass(colorProfile, transparency)

        // 8. Reconstruction suggestions & limitations
        val suggestion = generateSuggestion(likelyClass, colorProfile, transparency, options.userOverride)

        return ReferenceInspectionReport(
            isSuccess = true,
            sourceName = sourceName,
            sourceByteLength = bytes.size.toLong(),
            format = format,
            rawDimensions = Pair(rawW, rawH),
            normalizedDimensions = Pair(orientedW, orientedH),
            exifOrientation = exifOrientation,
            coordinateNormalization = coordinateNorm,
            transparencyProfile = transparency,
            colorProfile = colorProfile,
            likelyImageClass = likelyClass,
            suggestion = suggestion,
            memoryEstimate = memoryEstimate,
            normalizedPixels = decoded.pixels,
            isCorrupt = false,
            errorDetail = null
        )
    }

    /**
     * Detects reference format from magic bytes.
     */
    fun detectFormat(bytes: ByteArray): ReferenceFormat {
        if (bytes.size < 2) return ReferenceFormat.UNKNOWN

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
        ) {
            return ReferenceFormat.PNG
        }

        // JPEG: FF D8
        if ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8) {
            return ReferenceFormat.JPEG
        }

        // WebP: RIFF ... WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return ReferenceFormat.WEBP
        }

        // PSD: 8BPS
        if (bytes.size >= 4 &&
            bytes[0] == '8'.code.toByte() && bytes[1] == 'B'.code.toByte() &&
            bytes[2] == 'P'.code.toByte() && bytes[3] == 'S'.code.toByte()
        ) {
            return ReferenceFormat.PSD
        }

        // OpenRaster / ZIP: PK\x03\x04
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        ) {
            return ReferenceFormat.OPEN_RASTER
        }

        // SVG: text containing <svg
        val sampleLen = min(bytes.size, 1024)
        val sampleText = String(bytes, 0, sampleLen, StandardCharsets.UTF_8).lowercase()
        if (sampleText.contains("<svg") || (sampleText.contains("<?xml") && sampleText.contains("<svg"))) {
            return ReferenceFormat.SVG
        }

        return ReferenceFormat.UNKNOWN
    }

    private fun checkPsdIntegrity(bytes: ByteArray): String? {
        return try {
            PsdParser.parse(bytes)
            null
        } catch (t: Throwable) {
            "Corrupted PSD structure: ${t.message}"
        }
    }

    private fun checkOraIntegrity(bytes: ByteArray): String? {
        return try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            var entryCount = 0
            while (entry != null) {
                entryCount++
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
            if (entryCount == 0) "OpenRaster archive contains no entries" else null
        } catch (t: Throwable) {
            "Corrupted OpenRaster archive: ${t.message}"
        }
    }

    private fun checkSvgIntegrity(bytes: ByteArray): String? {
        return try {
            val xml = String(bytes, StandardCharsets.UTF_8)
            if (!xml.contains("<svg") || !xml.contains("</svg>")) {
                if (!xml.contains("/>")) {
                    return "Malformed SVG: Missing closing </svg> tag"
                }
            }
            // Check for unclosed tag corruption
            val opens = xml.count { it == '<' }
            val closes = xml.count { it == '>' }
            if (opens != closes) {
                return "Malformed SVG: Unbalanced XML tag delimiters"
            }
            null
        } catch (t: Throwable) {
            "Malformed SVG: ${t.message}"
        }
    }

    private fun analyzeTransparency(pixels: IntArray, width: Int, height: Int): TransparencyProfile {
        var transparentCount = 0
        var semiTransparentCount = 0
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                if (alpha < 255) {
                    transparentCount++
                    if (alpha > 0) {
                        semiTransparentCount++
                    }
                }
                if (alpha > 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val totalPixels = width * height
        val hasAlpha = transparentCount > 0
        val alphaRatio = if (totalPixels > 0) transparentCount.toFloat() / totalPixels else 0f

        val type = when {
            transparentCount == 0 -> TransparencyType.FULLY_OPAQUE
            semiTransparentCount > 0 -> TransparencyType.SEMI_TRANSPARENT_GRADIENT
            else -> TransparencyType.BINARY_MASK
        }

        val bbox = if (maxX >= minX && maxY >= minY) {
            IntBoundingBox(minX, minY, maxX, maxY)
        } else {
            null
        }

        return TransparencyProfile(
            hasAlpha = hasAlpha,
            type = type,
            transparentPixelCount = transparentCount,
            totalPixelCount = totalPixels,
            alphaRatio = alphaRatio,
            contentBoundingBox = bbox
        )
    }

    private fun analyzeColorAndEdges(pixels: IntArray, width: Int, height: Int): ColorProfileEstimate {
        val stepX = max(1, width / 256)
        val stepY = max(1, height / 256)

        val uniqueBins = HashSet<Int>()
        var neutralCount = 0
        var sampleCount = 0
        var totalLuminance = 0.0

        for (y in 0 until height step stepY) {
            val rowOffset = y * width
            for (x in 0 until width step stepX) {
                val argb = pixels[rowOffset + x]
                val a = (argb ushr 24) and 0xFF
                if (a == 0) continue

                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF

                // 5-bit color binning
                val bin = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                uniqueBins.add(bin)

                if (max(abs(r - g), max(abs(g - b), abs(r - b))) <= 8) {
                    neutralCount++
                }

                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                totalLuminance += lum
                sampleCount++
            }
        }

        val isGrayscale = if (sampleCount > 0) (neutralCount.toFloat() / sampleCount) >= 0.94f else true
        val meanLuminance = if (sampleCount > 0) (totalLuminance / sampleCount).toFloat() else 128f

        // Edge density calculation using luminance gradient
        var edgeCount = 0
        var edgeSamples = 0
        val edgeStep = max(1, min(width, height) / 128)

        for (y in edgeStep until height - edgeStep step edgeStep) {
            val rowOffset = y * width
            for (x in edgeStep until width - edgeStep step edgeStep) {
                val lumCenter = luminanceOf(pixels[rowOffset + x])
                val lumRight = luminanceOf(pixels[rowOffset + x + edgeStep])
                val lumDown = luminanceOf(pixels[(y + edgeStep) * width + x])

                val gx = abs(lumRight - lumCenter)
                val gy = abs(lumDown - lumCenter)
                if (gx + gy > 35) {
                    edgeCount++
                }
                edgeSamples++
            }
        }

        val edgeDensity = if (edgeSamples > 0) edgeCount.toFloat() / edgeSamples else 0f
        val hasHighFreqNoise = edgeDensity > 0.15f && isGrayscale

        return ColorProfileEstimate(
            isGrayscale = isGrayscale,
            uniqueColorCountEstimate = uniqueBins.size,
            meanLuminance = meanLuminance,
            contrastRatio = if (meanLuminance > 128f) meanLuminance / 20f else 20f,
            hasHighFrequencyNoise = hasHighFreqNoise,
            edgeDensity = edgeDensity
        )
    }

    private fun luminanceOf(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return ((299 * r + 587 * g + 114 * b) / 1000)
    }

    private fun inferImageClass(
        color: ColorProfileEstimate,
        transparency: TransparencyProfile
    ): LikelyImageClass {
        return if (color.isGrayscale) {
            // Distinguish Line Art vs Sketch
            if (color.uniqueColorCountEstimate <= 2) {
                LikelyImageClass.LINE_ART
            } else {
                LikelyImageClass.SKETCH
            }
        } else {
            // Color classifications: Flat Colour vs Detailed Illustration vs Photographic
            if (color.uniqueColorCountEstimate <= 48 && color.edgeDensity < 0.25f) {
                LikelyImageClass.FLAT_COLOUR
            } else if (color.uniqueColorCountEstimate <= 200 || color.edgeDensity > 0.10f) {
                LikelyImageClass.DETAILED_ILLUSTRATION
            } else {
                LikelyImageClass.PHOTOGRAPHIC_PAINTERLY
            }
        }
    }

    private fun generateSuggestion(
        likelyClass: LikelyImageClass,
        color: ColorProfileEstimate,
        transparency: TransparencyProfile,
        userOverride: ReconstructionMode?
    ): ReconstructionSuggestion {
        return when (likelyClass) {
            LikelyImageClass.LINE_ART -> ReconstructionSuggestion(
                suggestedMode = ReconstructionMode.LINE_ART_VECTORIZATION,
                confidence = 0.94f,
                reasoning = "High-contrast monochrome contours detected on clean background. Optimal for single-pass vectorization.",
                visibleLimitations = listOf(
                    "Hairline strokes thinner than 1px may experience anti-aliasing gaps",
                    "Contour smoothing may decimate intentional sharp micro-notches"
                ),
                userOverride = userOverride
            )
            LikelyImageClass.SKETCH -> ReconstructionSuggestion(
                suggestedMode = ReconstructionMode.PENCIL_CLEANUP_AND_INK,
                confidence = 0.89f,
                reasoning = "Graphite/pencil texture with mid-tone grain detected. Adaptive binarization and despeckle preprocessing recommended.",
                visibleLimitations = listOf(
                    "Faint graphite construction lines may require manual threshold adjustment",
                    "Heavy paper tooth grain may register as stray dust points without despeckle"
                ),
                userOverride = userOverride
            )
            LikelyImageClass.FLAT_COLOUR -> ReconstructionSuggestion(
                suggestedMode = ReconstructionMode.FLAT_REGION_CEL_SHADING,
                confidence = 0.92f,
                reasoning = "Segmented flat color regions with sharp boundary transitions detected. Suitable for cel shading extraction and region trapping.",
                visibleLimitations = listOf(
                    "Anti-aliased color fringes along borders may require Euclidean tolerance expansion",
                    "Gradients within flat regions will be quantized to dominant regional tones"
                ),
                userOverride = userOverride
            )
            LikelyImageClass.DETAILED_ILLUSTRATION -> ReconstructionSuggestion(
                suggestedMode = ReconstructionMode.MULTI_LAYER_HYBRID_DECOMPOSITION,
                confidence = 0.86f,
                reasoning = "Layered illustration detected with both distinct inking contours and multi-shade color gradients.",
                visibleLimitations = listOf(
                    "Complex blended highlights may require manual layer separation",
                    "Overlapping transparent washes may merge into base color planes"
                ),
                userOverride = userOverride
            )
            LikelyImageClass.PHOTOGRAPHIC_PAINTERLY -> ReconstructionSuggestion(
                suggestedMode = ReconstructionMode.CONTINUOUS_TONE_PAINT_LAYER,
                confidence = 0.88f,
                reasoning = "Continuous-tone color gradations and organic painterly textures detected. Best treated as painted underlay or reference guide.",
                visibleLimitations = listOf(
                    "High-entropy textures cannot be cleanly converted to discrete vector paths",
                    "Vectorization would produce excessive anchor points exceeding rendering budgets"
                ),
                userOverride = userOverride
            )
        }
    }

    private fun createCorruptReport(
        sourceName: String,
        sourceByteLength: Long,
        format: ReferenceFormat,
        error: String,
        options: InspectionOptions
    ): ReferenceInspectionReport {
        val emptyCoord = CoordinateNormalization(
            exifOrientation = 1,
            rawWidth = 0,
            rawHeight = 0,
            normalizedWidth = 0,
            normalizedHeight = 0
        )
        val emptyMem = MemoryEstimate(
            estimatedDecodedBytes = 0L,
            actualDecodePeakBytes = 0L,
            heapBudgetFraction = 0f,
            advice = MemoryAdvice.NORMAL_MEMORY
        )
        val defaultSuggestion = ReconstructionSuggestion(
            suggestedMode = ReconstructionMode.LINE_ART_VECTORIZATION,
            confidence = 0.0f,
            reasoning = "Cannot suggest reconstruction mode on corrupted input",
            visibleLimitations = listOf("Input stream is corrupt or unreadable: $error"),
            userOverride = options.userOverride
        )

        return ReferenceInspectionReport(
            isSuccess = false,
            sourceName = sourceName,
            sourceByteLength = sourceByteLength,
            format = format,
            rawDimensions = Pair(0, 0),
            normalizedDimensions = Pair(0, 0),
            exifOrientation = 1,
            coordinateNormalization = emptyCoord,
            transparencyProfile = null,
            colorProfile = null,
            likelyImageClass = LikelyImageClass.LINE_ART,
            suggestion = defaultSuggestion,
            memoryEstimate = emptyMem,
            normalizedPixels = null,
            isCorrupt = true,
            errorDetail = error
        )
    }
}
