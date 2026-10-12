package com.artstudio.deformation

import kotlin.math.max
import kotlin.math.min

/**
 * Supported deformation tools within the bounded deformation subsystem.
 */
enum class DeformToolMode {
    /** Control-point grid mesh warping. */
    WARP_MESH,

    /** Brush pushes pixels in the direction of stroke travel. */
    LIQUIFY_PUSH,

    /** Brush inflates / expands pixels radially outward from brush center. */
    LIQUIFY_EXPAND,

    /** Brush deflates / pinches pixels radially inward toward brush center. */
    LIQUIFY_PINCH,

    /** Brush twirls pixels clockwise around brush center. */
    LIQUIFY_TWIRL_CW,

    /** Brush twirls pixels counter-clockwise around brush center. */
    LIQUIFY_TWIRL_CCW,

    /** Brush reconstructs / restores pixels toward baseline zero displacement. */
    LIQUIFY_RECONSTRUCT
}

/**
 * Edge sampling behaviors when pixels sample outside canonical bounds.
 */
enum class EdgeBehavior {
    /** Clamps coordinates to the nearest valid border pixel. */
    CLAMP_TO_EDGE,

    /** Samples out-of-bounds coordinates as transparent black (0x00000000). */
    TRANSPARENT_ZERO,

    /** Wraps out-of-bounds coordinates modulo width and height. */
    WRAP
}

/**
 * Resampling interpolation filters for deformation rasterization.
 */
enum class DeformInterpolation {
    /** Nearest-neighbor point sampling (pixel sharp, fastest). */
    NEAREST_NEIGHBOR,

    /** 4-sample bilinear filtering with sub-pixel weighting. */
    BILINEAR,

    /** 16-sample cubic spline filtering (smooth gradients, highest fidelity). */
    BICUBIC
}

/**
 * Axis-aligned bounding box in deformation coordinate space.
 */
data class DeformBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = max(0f, right - left)
    val height: Float get() = max(0f, bottom - top)

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom
}

/**
 * Control vertex in a warp mesh.
 *
 * @param x Current displaced horizontal position in canvas pixels.
 * @param y Current displaced vertical position in canvas pixels.
 * @param u Original reference horizontal position in canvas pixels.
 * @param v Original reference vertical position in canvas pixels.
 */
data class MeshVertex(
    val x: Float,
    val y: Float,
    val u: Float,
    val v: Float
) {
    val isDisplaced: Boolean
        get() = kotlin.math.abs(x - u) > 0.001f || kotlin.math.abs(y - v) > 0.001f
}

/**
 * Grid-based control mesh for non-rigid freeform warping.
 *
 * @param rows Number of vertical grid control points (>= 2).
 * @param cols Number of horizontal grid control points (>= 2).
 * @param vertices List of vertices ordered row-by-row (size = rows * cols).
 */
data class WarpMesh(
    val rows: Int,
    val cols: Int,
    val vertices: List<MeshVertex>
) {
    init {
        require(rows >= 2) { "Mesh rows must be >= 2, got $rows" }
        require(cols >= 2) { "Mesh cols must be >= 2, got $cols" }
        require(vertices.size == rows * cols) {
            "Expected ${rows * cols} vertices, got ${vertices.size}"
        }
    }

    fun getVertex(row: Int, col: Int): MeshVertex {
        require(row in 0 until rows && col in 0 until cols) {
            "Index out of bounds: row=$row (max $rows), col=$col (max $cols)"
        }
        return vertices[row * cols + col]
    }

    /**
     * Updates the position of a single vertex.
     */
    fun withVertexPosition(row: Int, col: Int, newX: Float, newY: Float): WarpMesh {
        val index = row * cols + col
        val current = vertices[index]
        if (current.x == newX && current.y == newY) return this

        val updated = vertices.toMutableList()
        updated[index] = current.copy(x = newX, y = newY)
        return copy(vertices = updated)
    }

    /**
     * Resets all control vertices to their original reference positions.
     */
    fun reset(): WarpMesh {
        val resetVertices = vertices.map { it.copy(x = it.u, y = it.v) }
        return copy(vertices = resetVertices)
    }

    /**
     * Checks if all vertices are at their original undisplaced positions.
     */
    fun isIdentity(): Boolean = vertices.none { it.isDisplaced }

    /**
     * Computes the bounding envelope enclosing all vertices.
     */
    fun getBounds(): DeformBounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (v in vertices) {
            if (v.x < minX) minX = v.x
            if (v.y < minY) minY = v.y
            if (v.x > maxX) maxX = v.x
            if (v.y > maxY) maxY = v.y
        }

        return DeformBounds(minX, minY, maxX, maxY)
    }

    companion object {
        /**
         * Generates an initial uniform rectangular grid across the canvas dimensions.
         */
        fun createUniformGrid(
            width: Float,
            height: Float,
            rows: Int = 4,
            cols: Int = 4
        ): WarpMesh {
            val list = ArrayList<MeshVertex>(rows * cols)
            val stepY = if (rows > 1) height / (rows - 1) else 0f
            val stepX = if (cols > 1) width / (cols - 1) else 0f

            for (r in 0 until rows) {
                val y = (r * stepY).coerceAtMost(height)
                for (c in 0 until cols) {
                    val x = (c * stepX).coerceAtMost(width)
                    list.add(MeshVertex(x = x, y = y, u = x, v = y))
                }
            }
            return WarpMesh(rows, cols, list)
        }
    }
}

/**
 * Brush parameters for brush-based liquify deformation.
 */
data class LiquifyBrush(
    val radius: Float = 50f,
    val strength: Float = 0.5f,
    val mode: DeformToolMode = DeformToolMode.LIQUIFY_PUSH
) {
    init {
        require(radius in 5f..500f) { "Radius must be in 5..500, got $radius" }
        require(strength in 0.01f..1.0f) { "Strength must be in 0.01..1.0, got $strength" }
    }
}

/**
 * Subsystem configuration and safety boundaries.
 */
data class DeformationConfig(
    /** Maximum dimension allowed for interactive / committed deformation (e.g., 2048). */
    val maxWorkingResolution: Int = 2048,

    /** Downsampling factor for interactive preview on constrained memory (< 4 GB). */
    val adaptivePreviewScale: Float = 1.0f,

    /** Active edge extrapolation behavior. */
    val edgeBehavior: EdgeBehavior = EdgeBehavior.CLAMP_TO_EDGE,

    /** Active interpolation resampling filter. */
    val interpolation: DeformInterpolation = DeformInterpolation.BILINEAR
) {
    init {
        require(maxWorkingResolution in 256..4096) {
            "maxWorkingResolution must be in 256..4096, got $maxWorkingResolution"
        }
        require(adaptivePreviewScale in 0.1f..1.0f) {
            "adaptivePreviewScale must be in 0.1..1.0, got $adaptivePreviewScale"
        }
    }
}

/**
 * Performance telemetry recorded during deformation execution.
 */
data class DeformPerfMetrics(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val latencyMs: Double,
    val peakMemoryBytes: Long,
    val mode: DeformToolMode,
    val isDownsampledPreview: Boolean
)
