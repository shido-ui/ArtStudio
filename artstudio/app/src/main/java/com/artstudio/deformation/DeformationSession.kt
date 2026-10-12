package com.artstudio.deformation

import com.artstudio.history.DeformLayerCommand
import com.artstudio.layer.LayerManager
import kotlin.math.round
import kotlin.system.measureNanoTime

/**
 * Result returned upon committing a deformation session.
 */
data class DeformCommitResult(
    val layerId: String,
    val width: Int,
    val height: Int,
    val command: DeformLayerCommand,
    val metrics: DeformPerfMetrics
)

/**
 * Manages an interactive deformation session on a duplicated preview surface.
 * Ensures the original artwork layer is NEVER mutated until explicit confirmation and commit.
 */
class DeformationSession(
    val layerId: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val originalPixels: IntArray,
    var config: DeformationConfig = DeformationConfig()
) {
    init {
        if (!com.artstudio.core.FeatureFlags.isDeformationEnabled) {
            throw IllegalStateException("Deformation is disabled: FeatureFlags.isDeformationEnabled is false")
        }
        require(originalWidth > 0 && originalHeight > 0) {
            "Dimensions must be > 0, got $originalWidth x $originalHeight"
        }
        require(originalPixels.size == originalWidth * originalHeight) {
            "originalPixels size (${originalPixels.size}) must match dimensions ($originalWidth x $originalHeight)"
        }
        require(originalWidth <= config.maxWorkingResolution && originalHeight <= config.maxWorkingResolution) {
            "Canvas size ($originalWidth x $originalHeight) exceeds maximum working resolution (${config.maxWorkingResolution})"
        }
    }

    /**
     * Effective preview dimensions (adaptive on low memory / high resolution).
     */
    val isAdaptivePreview: Boolean =
        config.adaptivePreviewScale < 1.0f || originalWidth > 1024 || originalHeight > 1024

    val previewScale: Float = if (isAdaptivePreview) {
        val baseScale = config.adaptivePreviewScale
        if (originalWidth > 1024 || originalHeight > 1024) {
            baseScale.coerceAtMost(0.5f)
        } else {
            baseScale
        }
    } else {
        1.0f
    }

    val previewWidth: Int = (originalWidth * previewScale).toInt().coerceAtLeast(64)
    val previewHeight: Int = (originalHeight * previewScale).toInt().coerceAtLeast(64)

    /** Duplicated preview buffer modified interactively. */
    val previewPixels: IntArray = IntArray(previewWidth * previewHeight)

    /** Downscaled baseline snapshot of original artwork for preview rendering. */
    private val previewOriginalPixels: IntArray = IntArray(previewWidth * previewHeight)

    /** Interactive displacement field. */
    val displacement: DisplacementField = DisplacementField(previewWidth, previewHeight)

    /** Interactive warp mesh. */
    var warpMesh: WarpMesh = WarpMesh.createUniformGrid(
        previewWidth.toFloat(),
        previewHeight.toFloat(),
        rows = 4,
        cols = 4
    )
        private set

    var activeToolMode: DeformToolMode = DeformToolMode.WARP_MESH
    var brush: LiquifyBrush = LiquifyBrush()
    var isDirty: Boolean = false
        private set

    var isCommitted: Boolean = false
        private set
    var isCancelled: Boolean = false
        private set

    init {
        // Prepare initial preview surface from original pixels
        if (previewWidth == originalWidth && previewHeight == originalHeight) {
            System.arraycopy(originalPixels, 0, previewOriginalPixels, 0, originalPixels.size)
        } else {
            downscaleOriginalToPreview()
        }
        System.arraycopy(previewOriginalPixels, 0, previewPixels, 0, previewOriginalPixels.size)
    }

    private fun downscaleOriginalToPreview() {
        val invScaleX = originalWidth.toFloat() / previewWidth
        val invScaleY = originalHeight.toFloat() / previewHeight
        for (py in 0 until previewHeight) {
            val sy = ((py + 0.5f) * invScaleY).toInt().coerceIn(0, originalHeight - 1)
            val pOffset = py * previewWidth
            val sOffset = sy * originalWidth
            for (px in 0 until previewWidth) {
                val sx = ((px + 0.5f) * invScaleX).toInt().coerceIn(0, originalWidth - 1)
                previewOriginalPixels[pOffset + px] = originalPixels[sOffset + sx]
            }
        }
    }

    /**
     * Updates mesh vertex position in preview coordinate space.
     */
    fun updateMeshVertex(row: Int, col: Int, newX: Float, newY: Float) {
        check(!isCommitted && !isCancelled) { "Session is no longer active" }
        warpMesh = warpMesh.withVertexPosition(row, col, newX, newY)
        isDirty = !warpMesh.isIdentity() || !displacement.isZero()
    }

    /**
     * Applies a brush push motion.
     */
    fun applyPush(cx: Float, cy: Float, vx: Float, vy: Float) {
        check(!isCommitted && !isCancelled) { "Session is no longer active" }
        val scaledCx = cx * previewScale
        val scaledCy = cy * previewScale
        val scaledVx = vx * previewScale
        val scaledVy = vy * previewScale
        val scaledRadius = brush.radius * previewScale
        displacement.applyPush(scaledCx, scaledCy, scaledVx, scaledVy, scaledRadius, brush.strength)
        isDirty = true
    }

    /**
     * Applies a radial expand (inflate) motion.
     */
    fun applyExpand(cx: Float, cy: Float) {
        check(!isCommitted && !isCancelled) { "Session is no longer active" }
        val scaledCx = cx * previewScale
        val scaledCy = cy * previewScale
        val scaledRadius = brush.radius * previewScale
        displacement.applyExpand(scaledCx, scaledCy, scaledRadius, brush.strength)
        isDirty = true
    }

    /**
     * Applies a radial pinch (deflate) motion.
     */
    fun applyPinch(cx: Float, cy: Float) {
        check(!isCommitted && !isCancelled) { "Session is no longer active" }
        val scaledCx = cx * previewScale
        val scaledCy = cy * previewScale
        val scaledRadius = brush.radius * previewScale
        displacement.applyPinch(scaledCx, scaledCy, scaledRadius, brush.strength)
        isDirty = true
    }

    /**
     * Applies a rotational twirl vortex motion.
     */
    fun applyTwirl(cx: Float, cy: Float, clockwise: Boolean = true) {
        check(!isCommitted && !isCancelled) { "Session is no longer active" }
        val scaledCx = cx * previewScale
        val scaledCy = cy * previewScale
        val scaledRadius = brush.radius * previewScale
        displacement.applyTwirl(scaledCx, scaledCy, scaledRadius, brush.strength, clockwise)
        isDirty = true
    }

    /**
     * Reconstructs displaced area back toward original baseline.
     */
    fun applyReconstruct(cx: Float, cy: Float) {
        check(!isCommitted && !isCancelled) { "Session is no longer active" }
        val scaledCx = cx * previewScale
        val scaledCy = cy * previewScale
        val scaledRadius = brush.radius * previewScale
        displacement.applyReconstruct(scaledCx, scaledCy, scaledRadius, brush.strength)
        isDirty = !displacement.isZero() || !warpMesh.isIdentity()
    }

    /**
     * Re-renders the interactive preview surface.
     */
    fun renderPreview(): DeformPerfMetrics {
        check(!isCommitted && !isCancelled) { "Session is no longer active" }
        val startTime = System.nanoTime()

        if (activeToolMode == DeformToolMode.WARP_MESH) {
            DeformationEngine.warpWithMesh(
                srcPixels = previewOriginalPixels,
                width = previewWidth,
                height = previewHeight,
                destPixels = previewPixels,
                mesh = warpMesh,
                edgeBehavior = config.edgeBehavior,
                interpolation = config.interpolation
            )
        } else {
            DeformationEngine.warpWithDisplacement(
                srcPixels = previewOriginalPixels,
                width = previewWidth,
                height = previewHeight,
                destPixels = previewPixels,
                displacement = displacement,
                edgeBehavior = config.edgeBehavior,
                interpolation = config.interpolation
            )
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        val peakMem = (previewPixels.size * 4L) + (displacement.dx.size * 8L)

        return DeformPerfMetrics(
            canvasWidth = previewWidth,
            canvasHeight = previewHeight,
            latencyMs = elapsedMs,
            peakMemoryBytes = peakMem,
            mode = activeToolMode,
            isDownsampledPreview = isAdaptivePreview
        )
    }

    /**
     * Resets all deformations to identity without committing.
     * Restores preview buffer to exact bit-for-bit duplicate of original artwork.
     */
    fun reset() {
        check(!isCommitted && !isCancelled) { "Session is no longer active" }
        displacement.reset()
        warpMesh = warpMesh.reset()
        System.arraycopy(previewOriginalPixels, 0, previewPixels, 0, previewOriginalPixels.size)
        isDirty = false
    }

    /**
     * Cancels and aborts the deformation session with ZERO mutations to the document.
     */
    fun cancel() {
        isCancelled = true
        displacement.reset()
    }

    /**
     * Commits the deformation with user confirmation.
     * Computes the final deformation at full original resolution, records an undoable command,
     * and updates the layer.
     */
    fun commit(layerManager: LayerManager): DeformCommitResult {
        check(!isCommitted && !isCancelled) { "Session is no longer active" }
        val startTime = System.nanoTime()

        val finalPixels = IntArray(originalWidth * originalHeight)

        if (activeToolMode == DeformToolMode.WARP_MESH) {
            // Scale mesh back to full original resolution if running in adaptive preview
            val fullMesh = if (isAdaptivePreview) {
                val scaleFactor = 1.0f / previewScale
                val scaledVertices = warpMesh.vertices.map { v ->
                    v.copy(
                        x = v.x * scaleFactor,
                        y = v.y * scaleFactor,
                        u = v.u * scaleFactor,
                        v = v.v * scaleFactor
                    )
                }
                warpMesh.copy(vertices = scaledVertices)
            } else {
                warpMesh
            }

            DeformationEngine.warpWithMesh(
                srcPixels = originalPixels,
                width = originalWidth,
                height = originalHeight,
                destPixels = finalPixels,
                mesh = fullMesh,
                edgeBehavior = config.edgeBehavior,
                interpolation = config.interpolation
            )
        } else {
            // Upscale displacement field to full resolution
            val fullDisplacement = if (isAdaptivePreview) {
                displacement.resampleTo(originalWidth, originalHeight)
            } else {
                displacement
            }

            DeformationEngine.warpWithDisplacement(
                srcPixels = originalPixels,
                width = originalWidth,
                height = originalHeight,
                destPixels = finalPixels,
                displacement = fullDisplacement,
                edgeBehavior = config.edgeBehavior,
                interpolation = config.interpolation
            )
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        val peakMem = (finalPixels.size * 4L * 2L) // Pre and post pixels

        val cmd = DeformLayerCommand(
            layerId = layerId,
            preDeformPixels = originalPixels,
            postDeformPixels = finalPixels,
            width = originalWidth,
            height = originalHeight,
            description = when (activeToolMode) {
                DeformToolMode.WARP_MESH -> "Warp Mesh Deformation"
                else -> "Liquify Deformation"
            }
        )

        layerManager.historyManager.execute(cmd)
        isCommitted = true

        val metrics = DeformPerfMetrics(
            canvasWidth = originalWidth,
            canvasHeight = originalHeight,
            latencyMs = elapsedMs,
            peakMemoryBytes = peakMem,
            mode = activeToolMode,
            isDownsampledPreview = false
        )

        return DeformCommitResult(
            layerId = layerId,
            width = originalWidth,
            height = originalHeight,
            command = cmd,
            metrics = metrics
        )
    }
}
