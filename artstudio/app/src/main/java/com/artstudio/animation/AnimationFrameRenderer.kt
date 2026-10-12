package com.artstudio.animation

import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode
import kotlin.math.roundToInt

/**
 * Pure-Kotlin deterministic frame compositor and memory analyzer for frame-by-frame animation.
 *
 * Renders individual animation frames by compositing static background layers, optional onion skin
 * projections, and frame-specific cel drawings using standard Porter-Duff compositing.
 */
object AnimationFrameRenderer {

    /**
     * Composites and renders a complete frame buffer for the specified [frameIndex].
     *
     * @param document The authoritative art document
     * @param document The authoritative art document
     * @param frameIndex Target frame index
     * @param timeline The animation timeline configuration (falls back to document.animation if null)
     * @param pixelBuffers Optional map of layer IDs to 32-bit ARGB pixel buffers
     * @param applyOnionSkin Whether to composite non-destructive onion skin layers
     * @param isPlaying Whether timeline playback is actively running (used to suppress onion skins if configured)
     * @return 32-bit ARGB IntArray of size (width * height)
     */
    fun renderFrame(
        document: ArtDocument,
        frameIndex: Int,
        timeline: AnimationTimeline? = null,
        pixelBuffers: Map<String, IntArray> = emptyMap(),
        applyOnionSkin: Boolean = false,
        isPlaying: Boolean = false
    ): IntArray {
        val anim = timeline ?: document.animation?.toTimeline() ?: AnimationTimeline()
        val width = document.canvas.width
        val height = document.canvas.height
        val totalPixels = width * height
        val safeFrameIndex = frameIndex.coerceIn(anim.frames.indices)
        val currentFrame = anim.frames[safeFrameIndex]

        // 1. Initialize composite canvas with document background color
        val canvasBuffer = IntArray(totalPixels) { document.canvas.backgroundColor.toInt() }

        // Helper to locate layer by ID
        fun findLayer(id: String): LayerNode? = document.findLayerById(id)

        // 2. Render static background layers (layers in staticLayerIds that are below cels)
        val staticLayers = anim.staticLayerIds.mapNotNull { findLayer(it) }

        for (layer in staticLayers) {
            val pixels = pixelBuffers[layer.id]
            if (layer.visible && pixels != null) {
                compositeRasterLayer(canvasBuffer, pixels, layer.opacity)
            }
        }

        // 3. Optional Onion Skinning
        val shouldRenderOnionSkin = applyOnionSkin && anim.onionSkin.enabled && (!isPlaying || !anim.onionSkin.suppressDuringPlayback)
        if (shouldRenderOnionSkin) {
            val os = anim.onionSkin

            // Helper to compute step opacity based on falloff mode
            fun computeStepOpacity(baseOpacity: Float, step: Int, totalSteps: Int): Float {
                val clampedBase = baseOpacity.coerceIn(0f, 1f)
                return when (os.falloff) {
                    OnionSkinFalloff.CONSTANT -> clampedBase
                    OnionSkinFalloff.LINEAR -> {
                        val factor = if (totalSteps <= 1) 1f else (1.0f - (step - 1).toFloat() / totalSteps.toFloat()).coerceIn(0.15f, 1.0f)
                        (clampedBase * factor).coerceIn(0.05f, 1.0f)
                    }
                    OnionSkinFalloff.QUADRATIC -> {
                        val factor = if (totalSteps <= 1) 1f else {
                            val ratio = (totalSteps - step + 1).toFloat() / totalSteps.toFloat()
                            (ratio * ratio).coerceIn(0.1f, 1.0f)
                        }
                        (clampedBase * factor).coerceIn(0.05f, 1.0f)
                    }
                }
            }

            // Preceding frames (backward in time)
            if (os.showBefore) {
                for (step in os.framesBefore downTo 1) {
                    val pastIndex = safeFrameIndex - step
                    if (pastIndex in anim.frames.indices) {
                        val pastFrame = anim.frames[pastIndex]
                        val stepOpacity = computeStepOpacity(os.opacityBefore, step, os.framesBefore)
                        for (celId in pastFrame.celLayerIds) {
                            val cel = findLayer(celId) ?: continue
                            val pixels = pixelBuffers[cel.id]
                            if (pixels != null) {
                                when (os.tintMode) {
                                    OnionSkinTintMode.TINTED -> compositeTintedRasterLayer(canvasBuffer, pixels, stepOpacity, os.tintBefore)
                                    OnionSkinTintMode.ORIGINAL_COLOR -> compositeRasterLayer(canvasBuffer, pixels, stepOpacity)
                                }
                            }
                        }
                    }
                }
            }

            // Following frames (forward in time)
            if (os.showAfter) {
                for (step in os.framesAfter downTo 1) {
                    val futureIndex = safeFrameIndex + step
                    if (futureIndex in anim.frames.indices) {
                        val futureFrame = anim.frames[futureIndex]
                        val stepOpacity = computeStepOpacity(os.opacityAfter, step, os.framesAfter)
                        for (celId in futureFrame.celLayerIds) {
                            val cel = findLayer(celId) ?: continue
                            val pixels = pixelBuffers[cel.id]
                            if (pixels != null) {
                                when (os.tintMode) {
                                    OnionSkinTintMode.TINTED -> compositeTintedRasterLayer(canvasBuffer, pixels, stepOpacity, os.tintAfter)
                                    OnionSkinTintMode.ORIGINAL_COLOR -> compositeRasterLayer(canvasBuffer, pixels, stepOpacity)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Render current frame's cel layers
        for (celId in currentFrame.celLayerIds) {
            val cel = findLayer(celId)
            if (cel != null && cel.visible) {
                val opacity = currentFrame.layerOpacityOverrides[celId] ?: cel.opacity
                val pixels = pixelBuffers[cel.id]
                if (pixels != null) {
                    compositeRasterLayer(canvasBuffer, pixels, opacity)
                }
            }
        }

        return canvasBuffer
    }

    /**
     * Alpha-blends a raster pixel array onto the canvas buffer.
     */
    private fun compositeRasterLayer(
        dest: IntArray,
        srcPixels: IntArray,
        effectiveOpacity: Float
    ) {
        val count = minOf(dest.size, srcPixels.size)
        val layerAlphaMul = (effectiveOpacity.coerceIn(0f, 1f) * 255f).roundToInt()

        for (i in 0 until count) {
            val src = srcPixels[i]
            val srcA = (((src ushr 24) and 0xFF) * layerAlphaMul) / 255
            if (srcA == 0) continue

            if (srcA == 255) {
                dest[i] = src
            } else {
                val dst = dest[i]
                val dstA = (dst ushr 24) and 0xFF
                val srcR = (src ushr 16) and 0xFF
                val srcG = (src ushr 8) and 0xFF
                val srcB = src and 0xFF

                val dstR = (dst ushr 16) and 0xFF
                val dstG = (dst ushr 8) and 0xFF
                val dstB = dst and 0xFF

                // Standard Porter-Duff SRC_OVER
                val outA = srcA + (dstA * (255 - srcA)) / 255
                val denom = maxOf(1, outA)
                val outR = ((srcR * srcA) + (dstR * dstA * (255 - srcA)) / 255) / denom
                val outG = ((srcG * srcA) + (dstG * dstA * (255 - srcA)) / 255) / denom
                val outB = ((srcB * srcA) + (dstB * dstA * (255 - srcA)) / 255) / denom

                dest[i] = (outA.coerceIn(0, 255) shl 24) or
                        (outR.coerceIn(0, 255) shl 16) or
                        (outG.coerceIn(0, 255) shl 8) or
                        outB.coerceIn(0, 255)
            }
        }
    }

    /**
     * Composites a raster pixel array with a single monochrome tint color and custom opacity (for onion skinning).
     */
    private fun compositeTintedRasterLayer(
        dest: IntArray,
        srcPixels: IntArray,
        effectiveOpacity: Float,
        tintColor: Long
    ) {
        val count = minOf(dest.size, srcPixels.size)
        val layerAlphaMul = (effectiveOpacity.coerceIn(0f, 1f) * 255f).roundToInt()

        val tintR = ((tintColor ushr 16) and 0xFF).toInt()
        val tintG = ((tintColor ushr 8) and 0xFF).toInt()
        val tintB = (tintColor and 0xFF).toInt()

        for (i in 0 until count) {
            val src = srcPixels[i]
            val origSrcA = (src ushr 24) and 0xFF
            val srcA = (origSrcA * layerAlphaMul) / 255
            if (srcA == 0) continue

            val dst = dest[i]
            val dstA = (dst ushr 24) and 0xFF
            val dstR = (dst ushr 16) and 0xFF
            val dstG = (dst ushr 8) and 0xFF
            val dstB = dst and 0xFF

            val outA = srcA + (dstA * (255 - srcA)) / 255
            val denom = maxOf(1, outA)
            val outR = ((tintR * srcA) + (dstR * dstA * (255 - srcA)) / 255) / denom
            val outG = ((tintG * srcA) + (dstG * dstA * (255 - srcA)) / 255) / denom
            val outB = ((tintB * srcA) + (dstB * dstA * (255 - srcA)) / 255) / denom

            dest[i] = (outA.coerceIn(0, 255) shl 24) or
                    (outR.coerceIn(0, 255) shl 16) or
                    (outG.coerceIn(0, 255) shl 8) or
                    outB.coerceIn(0, 255)
        }
    }

    /**
     * Analyzes and records memory and storage metrics for an animation project.
     * Demonstrates and quantifies cel-referencing memory efficiency over full snapshot copies.
     */
    fun calculateMemoryMetrics(
        document: ArtDocument,
        timeline: AnimationTimeline
    ): AnimationMemoryMetrics {
        val width = document.canvas.width
        val height = document.canvas.height
        val bytesPerPixel = 4L // ARGB_8888
        val singleCanvasBytes = width.toLong() * height.toLong() * bytesPerPixel

        val uniqueCelLayerIds = timeline.frames.flatMap { it.celLayerIds }.distinct().toSet()
        val staticLayerIds = timeline.staticLayerIds.distinct().toSet()

        // Total allocated pixel layers for animation
        val uniqueCelCount = uniqueCelLayerIds.size
        val staticCount = staticLayerIds.size
        val totalAllocatedLayers = uniqueCelCount + staticCount

        val celReferencedPixelBytes = totalAllocatedLayers.toLong() * singleCanvasBytes

        // If naive full-canvas snapshots were stored for every frame:
        val naiveSnapshotPixelBytes = timeline.frames.size.toLong() * singleCanvasBytes

        val memorySavedBytes = (naiveSnapshotPixelBytes - celReferencedPixelBytes).coerceAtLeast(0L)
        val savingsRatio = if (naiveSnapshotPixelBytes > 0) {
            (memorySavedBytes.toFloat() / naiveSnapshotPixelBytes.toFloat()) * 100f
        } else {
            0f
        }

        val jsonString = DocumentSerializer.toJsonString(document)
        val jsonBytes = jsonString.toByteArray(Charsets.UTF_8).size.toLong()

        return AnimationMemoryMetrics(
            canvasWidth = width,
            canvasHeight = height,
            frameCount = timeline.frames.size,
            totalTicks = timeline.totalDurationTicks,
            uniqueCelLayerCount = uniqueCelCount,
            staticLayerCount = staticCount,
            celReferencedPixelBytes = celReferencedPixelBytes,
            naiveSnapshotPixelBytes = naiveSnapshotPixelBytes,
            memorySavedBytes = memorySavedBytes,
            savingsRatioPercent = savingsRatio,
            serializedJsonBytes = jsonBytes
        )
    }

    /**
     * Reduced-cost preview frame compositing for responsive scrubbing and low-memory devices.
     * Composites artwork at a downscaled resolution [scale] (e.g. 0.5x, 0.25x),
     * cutting pixel calculations by 75% to 93%+.
     */
    fun renderReducedCostPreviewFrame(
        document: ArtDocument,
        frameIndex: Int,
        scale: Float = 0.5f,
        timeline: AnimationTimeline? = null,
        pixelBuffers: Map<String, IntArray> = emptyMap(),
        applyOnionSkin: Boolean = false,
        isPlaying: Boolean = false
    ): PreviewFrameBuffer {
        val safeScale = scale.coerceIn(0.1f, 1.0f)
        val origW = document.canvas.width
        val origH = document.canvas.height

        if (safeScale >= 1.0f) {
            val fullPixels = renderFrame(document, frameIndex, timeline, pixelBuffers, applyOnionSkin, isPlaying)
            return PreviewFrameBuffer(origW, origH, fullPixels, 1.0f)
        }

        val targetW = (origW * safeScale).roundToInt().coerceAtLeast(1)
        val targetH = (origH * safeScale).roundToInt().coerceAtLeast(1)

        // Downsample input layer pixel buffers
        val downsampledBuffers = pixelBuffers.mapValues { (_, src) ->
            downsampleBuffer(src, origW, origH, targetW, targetH)
        }

        // Create downscaled canvas document config
        val scaledCanvas = document.canvas.copy(width = targetW, height = targetH)
        val scaledDoc = document.copy(canvas = scaledCanvas)

        val scaledComposite = renderFrame(
            document = scaledDoc,
            frameIndex = frameIndex,
            timeline = timeline,
            pixelBuffers = downsampledBuffers,
            applyOnionSkin = applyOnionSkin,
            isPlaying = isPlaying
        )

        return PreviewFrameBuffer(targetW, targetH, scaledComposite, safeScale)
    }

    /**
     * Nearest-neighbor spatial downsampler for fast preview generation.
     */
    fun downsampleBuffer(src: IntArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): IntArray {
        if (srcW == dstW && srcH == dstH) return src.clone()
        val dst = IntArray(dstW * dstH)
        val stepX = srcW.toFloat() / dstW.toFloat()
        val stepY = srcH.toFloat() / dstH.toFloat()

        for (y in 0 until dstH) {
            val sy = (y * stepY).toInt().coerceIn(0, srcH - 1)
            val rowOffset = y * dstW
            val srcRowOffset = sy * srcW
            for (x in 0 until dstW) {
                val sx = (x * stepX).toInt().coerceIn(0, srcW - 1)
                dst[rowOffset + x] = src[srcRowOffset + sx]
            }
        }
        return dst
    }
}

/**
 * Container for rendered preview frame pixel buffers.
 */
data class PreviewFrameBuffer(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val scale: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewFrameBuffer) return false
        return width == other.width && height == other.height && scale == other.scale && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + scale.hashCode()
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

/**
 * Bounded LRU cache for rendered animation preview frames.
 * Avoids re-compositing frames repeatedly during interactive scrubbing and looped playback.
 */
class AnimationPreviewCache(
    val maxFrames: Int = 30
) {
    private val cache = object : LinkedHashMap<String, PreviewFrameBuffer>(maxFrames, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PreviewFrameBuffer>?): Boolean {
            return size > maxFrames
        }
    }

    private fun buildKey(frameIndex: Int, scale: Float, onionSkin: Boolean): String =
        "f_${frameIndex}_s_${scale}_os_${onionSkin}"

    @Synchronized
    fun get(frameIndex: Int, scale: Float = 1.0f, onionSkin: Boolean = false): PreviewFrameBuffer? {
        return cache[buildKey(frameIndex, scale, onionSkin)]
    }

    @Synchronized
    fun put(frameIndex: Int, scale: Float = 1.0f, onionSkin: Boolean = false, buffer: PreviewFrameBuffer) {
        cache[buildKey(frameIndex, scale, onionSkin)] = buffer
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    val size: Int get() = cache.size
}
