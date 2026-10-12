package com.artstudio.deformation

import kotlin.math.*

/**
 * High-performance 2D vector displacement field.
 * Represents horizontal and vertical pixel offsets (dx, dy) across a 2D surface.
 */
class DisplacementField(
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0 && height > 0) { "Dimensions must be > 0, got $width x $height" }
    }

    val dx: FloatArray = FloatArray(width * height)
    val dy: FloatArray = FloatArray(width * height)

    fun getDx(x: Int, y: Int): Float = dx[y * width + x]
    fun getDy(x: Int, y: Int): Float = dy[y * width + x]

    fun set(x: Int, y: Int, vx: Float, vy: Float) {
        val idx = y * width + x
        dx[idx] = vx
        dy[idx] = vy
    }

    fun add(x: Int, y: Int, vx: Float, vy: Float) {
        val idx = y * width + x
        dx[idx] += vx
        dy[idx] += vy
    }

    fun reset() {
        dx.fill(0f)
        dy.fill(0f)
    }

    fun isZero(): Boolean {
        for (i in dx.indices) {
            if (abs(dx[i]) > 0.001f || abs(dy[i]) > 0.001f) return false
        }
        return true
    }

    fun copy(): DisplacementField {
        val clone = DisplacementField(width, height)
        System.arraycopy(dx, 0, clone.dx, 0, dx.size)
        System.arraycopy(dy, 0, clone.dy, 0, dy.size)
        return clone
    }

    /**
     * Resamples the displacement field to another resolution with bilinear scaling.
     * Vector offsets are proportionately scaled by (destW/width, destH/height).
     */
    fun resampleTo(destW: Int, destH: Int): DisplacementField {
        if (destW == width && destH == height) return copy()

        val result = DisplacementField(destW, destH)
        val scaleX = destW.toFloat() / width
        val scaleY = destH.toFloat() / height
        val invScaleX = width.toFloat() / destW
        val invScaleY = height.toFloat() / destH

        for (dyIdx in 0 until destH) {
            val srcY = (dyIdx + 0.5f) * invScaleY - 0.5f
            val y0 = floor(srcY).toInt().coerceIn(0, height - 1)
            val y1 = (y0 + 1).coerceAtMost(height - 1)
            val fy = (srcY - y0).coerceIn(0f, 1f)

            for (dxIdx in 0 until destW) {
                val srcX = (dxIdx + 0.5f) * invScaleX - 0.5f
                val x0 = floor(srcX).toInt().coerceIn(0, width - 1)
                val x1 = (x0 + 1).coerceAtMost(width - 1)
                val fx = (srcX - x0).coerceIn(0f, 1f)

                // Bilinear sample dx
                val dx00 = dx[y0 * width + x0]
                val dx10 = dx[y0 * width + x1]
                val dx01 = dx[y1 * width + x0]
                val dx11 = dx[y1 * width + x1]
                val sampledDx = (1 - fx) * (1 - fy) * dx00 + fx * (1 - fy) * dx10 +
                        (1 - fx) * fy * dx01 + fx * fy * dx11

                // Bilinear sample dy
                val dy00 = dy[y0 * width + x0]
                val dy10 = dy[y0 * width + x1]
                val dy01 = dy[y1 * width + x0]
                val dy11 = dy[y1 * width + x1]
                val sampledDy = (1 - fx) * (1 - fy) * dy00 + fx * (1 - fy) * dy10 +
                        (1 - fx) * fy * dy01 + fx * fy * dy11

                result.set(dxIdx, dyIdx, sampledDx * scaleX, sampledDy * scaleY)
            }
        }
        return result
    }

    /**
     * Applies a brush push motion displacement.
     */
    fun applyPush(
        cx: Float,
        cy: Float,
        vx: Float,
        vy: Float,
        radius: Float,
        strength: Float
    ) {
        val radSq = radius * radius
        val minX = max(0, floor(cx - radius).toInt())
        val maxX = min(width - 1, ceil(cx + radius).toInt())
        val minY = max(0, floor(cy - radius).toInt())
        val maxY = min(height - 1, ceil(cy + radius).toInt())
        val str = strength.coerceIn(0f, 1f)

        for (y in minY..maxY) {
            val dyVal = y - cy
            val dySq = dyVal * dyVal
            val rowOffset = y * width
            for (x in minX..maxX) {
                val dxVal = x - cx
                val distSq = dxVal * dxVal + dySq
                if (distSq <= radSq) {
                    val r = distSq / radSq
                    val weight = (1f - r) * (1f - r) * str
                    val idx = rowOffset + x
                    dx[idx] += vx * weight
                    dy[idx] += vy * weight
                }
            }
        }
    }

    /**
     * Applies a radial expand (inflate) displacement.
     */
    fun applyExpand(
        cx: Float,
        cy: Float,
        radius: Float,
        strength: Float
    ) {
        val radSq = radius * radius
        val minX = max(0, floor(cx - radius).toInt())
        val maxX = min(width - 1, ceil(cx + radius).toInt())
        val minY = max(0, floor(cy - radius).toInt())
        val maxY = min(height - 1, ceil(cy + radius).toInt())
        val maxOffset = radius * 0.25f * strength.coerceIn(0f, 1f)

        for (y in minY..maxY) {
            val dyVal = y - cy
            val dySq = dyVal * dyVal
            val rowOffset = y * width
            for (x in minX..maxX) {
                val dxVal = x - cx
                val distSq = dxVal * dxVal + dySq
                if (distSq in 0.001f..radSq) {
                    val dist = sqrt(distSq)
                    val r = dist / radius
                    val weight = (1f - r * r) * (1f - r * r)
                    val nx = dxVal / dist
                    val ny = dyVal / dist
                    val idx = rowOffset + x
                    // Expanding pushes content out -> inverse mapping pulls from center
                    dx[idx] -= nx * weight * maxOffset
                    dy[idx] -= ny * weight * maxOffset
                }
            }
        }
    }

    /**
     * Applies a radial pinch (deflate) displacement.
     */
    fun applyPinch(
        cx: Float,
        cy: Float,
        radius: Float,
        strength: Float
    ) {
        val radSq = radius * radius
        val minX = max(0, floor(cx - radius).toInt())
        val maxX = min(width - 1, ceil(cx + radius).toInt())
        val minY = max(0, floor(cy - radius).toInt())
        val maxY = min(height - 1, ceil(cy + radius).toInt())
        val maxOffset = radius * 0.25f * strength.coerceIn(0f, 1f)

        for (y in minY..maxY) {
            val dyVal = y - cy
            val dySq = dyVal * dyVal
            val rowOffset = y * width
            for (x in minX..maxX) {
                val dxVal = x - cx
                val distSq = dxVal * dxVal + dySq
                if (distSq in 0.001f..radSq) {
                    val dist = sqrt(distSq)
                    val r = dist / radius
                    val weight = (1f - r * r) * (1f - r * r)
                    val nx = dxVal / dist
                    val ny = dyVal / dist
                    val idx = rowOffset + x
                    // Pinching pulls content in -> inverse mapping samples from outer region
                    dx[idx] += nx * weight * maxOffset
                    dy[idx] += ny * weight * maxOffset
                }
            }
        }
    }

    /**
     * Applies a twirl (rotational vortex) displacement.
     * @param clockwise True for clockwise twirl, false for counter-clockwise.
     */
    fun applyTwirl(
        cx: Float,
        cy: Float,
        radius: Float,
        strength: Float,
        clockwise: Boolean = true
    ) {
        val radSq = radius * radius
        val minX = max(0, floor(cx - radius).toInt())
        val maxX = min(width - 1, ceil(cx + radius).toInt())
        val minY = max(0, floor(cy - radius).toInt())
        val maxY = min(height - 1, ceil(cy + radius).toInt())
        val maxAngle = (if (clockwise) 1f else -1f) * Math.PI.toFloat() * 0.5f * strength.coerceIn(0f, 1f)

        for (y in minY..maxY) {
            val dyVal = y - cy
            val dySq = dyVal * dyVal
            val rowOffset = y * width
            for (x in minX..maxX) {
                val dxVal = x - cx
                val distSq = dxVal * dxVal + dySq
                if (distSq <= radSq) {
                    val r = distSq / radSq
                    val theta = maxAngle * (1f - r) * (1f - r)
                    val cosT = cos(theta)
                    val sinT = sin(theta)

                    // Rotate (dxVal, dyVal) by -theta for inverse coordinate mapping
                    val newDx = dxVal * cosT + dyVal * sinT
                    val newDy = -dxVal * sinT + dyVal * cosT

                    val idx = rowOffset + x
                    dx[idx] += (newDx - dxVal)
                    dy[idx] += (newDy - dyVal)
                }
            }
        }
    }

    /**
     * Gradually restores displacement toward zero baseline.
     */
    fun applyReconstruct(
        cx: Float,
        cy: Float,
        radius: Float,
        strength: Float
    ) {
        val radSq = radius * radius
        val minX = max(0, floor(cx - radius).toInt())
        val maxX = min(width - 1, ceil(cx + radius).toInt())
        val minY = max(0, floor(cy - radius).toInt())
        val maxY = min(height - 1, ceil(cy + radius).toInt())
        val factor = (1f - strength.coerceIn(0.01f, 1.0f)).coerceAtLeast(0f)

        for (y in minY..maxY) {
            val dyVal = y - cy
            val dySq = dyVal * dyVal
            val rowOffset = y * width
            for (x in minX..maxX) {
                val dxVal = x - cx
                val distSq = dxVal * dxVal + dySq
                if (distSq <= radSq) {
                    val r = distSq / radSq
                    val decay = 1f - (1f - factor) * (1f - r) * (1f - r)
                    val idx = rowOffset + x
                    dx[idx] *= decay
                    dy[idx] *= decay
                }
            }
        }
    }
}

/**
 * Pure-Kotlin execution engine for mesh warp and brush-based liquify deformation.
 */
object DeformationEngine {

    /**
     * Warps a source pixel buffer using a vector displacement field.
     */
    fun warpWithDisplacement(
        srcPixels: IntArray,
        width: Int,
        height: Int,
        destPixels: IntArray,
        displacement: DisplacementField,
        edgeBehavior: EdgeBehavior = EdgeBehavior.CLAMP_TO_EDGE,
        interpolation: DeformInterpolation = DeformInterpolation.BILINEAR
    ) {
        if (!com.artstudio.core.FeatureFlags.isDeformationEnabled) {
            throw IllegalStateException("DeformationEngine is disabled: FeatureFlags.isDeformationEnabled is false")
        }
        require(srcPixels.size == width * height) { "srcPixels size must match width * height" }
        require(destPixels.size == width * height) { "destPixels size must match width * height" }
        require(displacement.width == width && displacement.height == height) {
            "displacement dimensions (${displacement.width}x${displacement.height}) must match canvas (${width}x${height})"
        }

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                val u = x + displacement.dx[idx]
                val v = y + displacement.dy[idx]

                destPixels[idx] = samplePixel(
                    srcPixels, width, height, u, v, edgeBehavior, interpolation
                )
            }
        }
    }

    /**
     * Warps a source pixel buffer using a control-point WarpMesh.
     * Divides each grid quad into two triangles and computes affine inverse mapping per triangle.
     */
    fun warpWithMesh(
        srcPixels: IntArray,
        width: Int,
        height: Int,
        destPixels: IntArray,
        mesh: WarpMesh,
        edgeBehavior: EdgeBehavior = EdgeBehavior.CLAMP_TO_EDGE,
        interpolation: DeformInterpolation = DeformInterpolation.BILINEAR
    ) {
        if (!com.artstudio.core.FeatureFlags.isDeformationEnabled) {
            throw IllegalStateException("DeformationEngine is disabled: FeatureFlags.isDeformationEnabled is false")
        }
        require(srcPixels.size == width * height) { "srcPixels size must match width * height" }
        require(destPixels.size == width * height) { "destPixels size must match width * height" }

        // Initialize destPixels with source by default
        System.arraycopy(srcPixels, 0, destPixels, 0, srcPixels.size)

        if (mesh.isIdentity()) {
            System.arraycopy(srcPixels, 0, destPixels, 0, srcPixels.size)
            return
        }

        val rows = mesh.rows
        val cols = mesh.cols

        // Iterate through all grid quads
        for (r in 0 until rows - 1) {
            for (c in 0 until cols - 1) {
                val v00 = mesh.getVertex(r, c)
                val v01 = mesh.getVertex(r, c + 1)
                val v10 = mesh.getVertex(r + 1, c)
                val v11 = mesh.getVertex(r + 1, c + 1)

                // Rasterize Triangle 1: (v00, v01, v11)
                rasterizeTriangle(
                    srcPixels, width, height, destPixels,
                    v00, v01, v11,
                    edgeBehavior, interpolation
                )

                // Rasterize Triangle 2: (v00, v11, v10)
                rasterizeTriangle(
                    srcPixels, width, height, destPixels,
                    v00, v11, v10,
                    edgeBehavior, interpolation
                )
            }
        }
    }

    private fun rasterizeTriangle(
        srcPixels: IntArray,
        width: Int,
        height: Int,
        destPixels: IntArray,
        v1: MeshVertex,
        v2: MeshVertex,
        v3: MeshVertex,
        edgeBehavior: EdgeBehavior,
        interpolation: DeformInterpolation
    ) {
        val minX = max(0, floor(min(v1.x, min(v2.x, v3.x))).toInt())
        val maxX = min(width - 1, ceil(max(v1.x, max(v2.x, v3.x))).toInt())
        val minY = max(0, floor(min(v1.y, min(v2.y, v3.y))).toInt())
        val maxY = min(height - 1, ceil(max(v1.y, max(v2.y, v3.y))).toInt())

        val det = (v2.y - v3.y) * (v1.x - v3.x) + (v3.x - v2.x) * (v1.y - v3.y)
        if (abs(det) < 1e-6f) return // Degenerate triangle

        val invDet = 1.0f / det

        for (py in minY..maxY) {
            val yf = py.toFloat()
            val rowOffset = py * width
            for (px in minX..maxX) {
                val xf = px.toFloat()

                val l1 = ((v2.y - v3.y) * (xf - v3.x) + (v3.x - v2.x) * (yf - v3.y)) * invDet
                val l2 = ((v3.y - v1.y) * (xf - v3.x) + (v1.x - v3.x) * (yf - v3.y)) * invDet
                val l3 = 1.0f - l1 - l2

                // Generous epsilon to eliminate seams along shared triangle boundaries
                if (l1 >= -0.001f && l2 >= -0.001f && l3 >= -0.001f) {
                    val u = l1 * v1.u + l2 * v2.u + l3 * v3.u
                    val v = l1 * v1.v + l2 * v2.v + l3 * v3.v

                    destPixels[rowOffset + px] = samplePixel(
                        srcPixels, width, height, u, v, edgeBehavior, interpolation
                    )
                }
            }
        }
    }

    /**
     * Samples an ARGB pixel from a 2D raster with selected interpolation and edge behavior.
     */
    fun samplePixel(
        src: IntArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
        edgeBehavior: EdgeBehavior,
        interpolation: DeformInterpolation
    ): Int {
        return when (interpolation) {
            DeformInterpolation.NEAREST_NEIGHBOR -> {
                val (sx, sy, valid) = resolveCoords(round(u).toInt(), round(v).toInt(), width, height, edgeBehavior)
                if (!valid) 0 else src[sy * width + sx]
            }
            DeformInterpolation.BILINEAR -> {
                sampleBilinear(src, width, height, u, v, edgeBehavior)
            }
            DeformInterpolation.BICUBIC -> {
                sampleBicubic(src, width, height, u, v, edgeBehavior)
            }
        }
    }

    private fun resolveCoords(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        edgeBehavior: EdgeBehavior
    ): Triple<Int, Int, Boolean> {
        val inBounds = x in 0 until width && y in 0 until height
        if (inBounds) return Triple(x, y, true)

        return when (edgeBehavior) {
            EdgeBehavior.CLAMP_TO_EDGE -> {
                Triple(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1), true)
            }
            EdgeBehavior.TRANSPARENT_ZERO -> {
                Triple(0, 0, false)
            }
            EdgeBehavior.WRAP -> {
                val wx = ((x % width) + width) % width
                val wy = ((y % height) + height) % height
                Triple(wx, wy, true)
            }
        }
    }

    private fun sampleBilinear(
        src: IntArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
        edgeBehavior: EdgeBehavior
    ): Int {
        val x0 = floor(u).toInt()
        val y0 = floor(v).toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        val fx = u - x0
        val fy = v - y0

        val (c00x, c00y, v00) = resolveCoords(x0, y0, width, height, edgeBehavior)
        val (c10x, c10y, v10) = resolveCoords(x1, y0, width, height, edgeBehavior)
        val (c01x, c01y, v01) = resolveCoords(x0, y1, width, height, edgeBehavior)
        val (c11x, c11y, v11) = resolveCoords(x1, y1, width, height, edgeBehavior)

        val p00 = if (v00) src[c00y * width + c00x] else 0
        val p10 = if (v10) src[c10y * width + c10x] else 0
        val p01 = if (v01) src[c01y * width + c01x] else 0
        val p11 = if (v11) src[c11y * width + c11x] else 0

        val a00 = (p00 ushr 24) and 0xFF
        val r00 = (p00 ushr 16) and 0xFF
        val g00 = (p00 ushr 8) and 0xFF
        val b00 = p00 and 0xFF

        val a10 = (p10 ushr 24) and 0xFF
        val r10 = (p10 ushr 16) and 0xFF
        val g10 = (p10 ushr 8) and 0xFF
        val b10 = p10 and 0xFF

        val a01 = (p01 ushr 24) and 0xFF
        val r01 = (p01 ushr 16) and 0xFF
        val g01 = (p01 ushr 8) and 0xFF
        val b01 = p01 and 0xFF

        val a11 = (p11 ushr 24) and 0xFF
        val r11 = (p11 ushr 16) and 0xFF
        val g11 = (p11 ushr 8) and 0xFF
        val b11 = p11 and 0xFF

        val w00 = (1 - fx) * (1 - fy)
        val w10 = fx * (1 - fy)
        val w01 = (1 - fx) * fy
        val w11 = fx * fy

        val a = round(a00 * w00 + a10 * w10 + a01 * w01 + a11 * w11).toInt().coerceIn(0, 255)
        val r = round(r00 * w00 + r10 * w10 + r01 * w01 + r11 * w11).toInt().coerceIn(0, 255)
        val g = round(g00 * w00 + g10 * w10 + g01 * w01 + g11 * w11).toInt().coerceIn(0, 255)
        val b = round(b00 * w00 + b10 * w10 + b01 * w01 + b11 * w11).toInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun cubicWeight(t: Float): Float {
        val a = -0.5f // Catmull-Rom spline
        val at = abs(t)
        return when {
            at <= 1.0f -> (a + 2f) * at * at * at - (a + 3f) * at * at + 1f
            at < 2.0f -> a * at * at * at - 5f * a * at * at + 8f * a * at - 4f * a
            else -> 0f
        }
    }

    private fun sampleBicubic(
        src: IntArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
        edgeBehavior: EdgeBehavior
    ): Int {
        val xBase = floor(u).toInt()
        val yBase = floor(v).toInt()
        val fx = u - xBase
        val fy = v - yBase

        var aSum = 0f
        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        var weightSum = 0f

        for (j in -1..2) {
            val wy = cubicWeight(fy - j)
            val y = yBase + j

            for (i in -1..2) {
                val wx = cubicWeight(fx - i)
                val w = wx * wy
                val x = xBase + i

                val (sx, sy, valid) = resolveCoords(x, y, width, height, edgeBehavior)
                val pixel = if (valid) src[sy * width + sx] else 0

                val a = (pixel ushr 24) and 0xFF
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                aSum += a * w
                rSum += r * w
                gSum += g * w
                bSum += b * w
                weightSum += w
            }
        }

        if (abs(weightSum) > 0.001f) {
            val inv = 1.0f / weightSum
            aSum *= inv
            rSum *= inv
            gSum *= inv
            bSum *= inv
        }

        val a = round(aSum).toInt().coerceIn(0, 255)
        val r = round(rSum).toInt().coerceIn(0, 255)
        val g = round(gSum).toInt().coerceIn(0, 255)
        val b = round(bSum).toInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
