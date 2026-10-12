package com.artstudio.reference.region

import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic Engine for marking, editing, and managing focus regions on preserved reference images.
 *
 * Implements:
 * 1. Brush, Lasso, Polygon, Rectangle, and Ellipse selection tools.
 * 2. Boolean combination modes: REPLACE, ADD (Union), SUBTRACT (Difference), CLEAR.
 * 3. Multi-region lifecycle management: Rename, Duplicate, Delete, Hide/Show, and Edit Boundary.
 * 4. Coordinate stability under pan, zoom, and source cropping.
 * 5. Composite overlay rendering preserving the immutable reference source.
 */
object ReferenceRegionEngine {

    /**
     * Vibrant cycling palette used to distinguish multiple regions visually.
     */
    val DEFAULT_REGION_COLORS = intArrayOf(
        0xFFFF0055.toInt(), // Neon Crimson / Rose
        0xFF00E5FF.toInt(), // Cyan
        0xFFFFD600.toInt(), // Amber / Yellow
        0xFF00E676.toInt(), // Emerald Green
        0xFFD500F9.toInt(), // Violet / Magenta
        0xFFFF9100.toInt(), // Tangerine Orange
        0xFF2979FF.toInt()  // Royal Blue
    )

    // -------------------------------------------------------------------------
    // 1. Selection Tool Implementations
    // -------------------------------------------------------------------------

    /**
     * Applies a continuous freehand brush stroke of circular dabs with radius [brushRadius].
     */
    fun applyBrush(
        region: NamedReferenceRegion,
        strokePoints: List<Pair<Float, Float>>,
        brushRadius: Float,
        mode: RegionCombineMode
    ): NamedReferenceRegion {
        if (strokePoints.isEmpty()) return region
        require(brushRadius > 0f) { "brushRadius must be positive, got $brushRadius" }

        val width = region.width
        val height = region.height
        val total = width * height
        val shapeMask = ByteArray(total)

        // Interpolate dabs along the path
        val r = brushRadius
        val r2 = r * r
        val step = max(1.0f, r * 0.25f)

        fun drawDab(cx: Float, cy: Float) {
            val minX = max(0, floor(cx - r).toInt())
            val maxX = min(width - 1, ceil(cx + r).toInt())
            val minY = max(0, floor(cy - r).toInt())
            val maxY = min(height - 1, ceil(cy + r).toInt())

            for (y in minY..maxY) {
                val dy = y + 0.5f - cy
                val dy2 = dy * dy
                val rowOffset = y * width
                for (x in minX..maxX) {
                    val dx = x + 0.5f - cx
                    if (dx * dx + dy2 <= r2) {
                        shapeMask[rowOffset + x] = 255.toByte()
                    }
                }
            }
        }

        if (strokePoints.size == 1) {
            drawDab(strokePoints[0].first, strokePoints[0].second)
        } else {
            for (i in 0 until strokePoints.size - 1) {
                val p1 = strokePoints[i]
                val p2 = strokePoints[i + 1]
                val dx = p2.first - p1.first
                val dy = p2.second - p1.second
                val dist = sqrt(dx * dx + dy * dy)
                val steps = max(1, (dist / step).toInt())

                for (s in 0..steps) {
                    val t = s.toFloat() / steps
                    val px = p1.first + t * dx
                    val py = p1.second + t * dy
                    drawDab(px, py)
                }
            }
        }

        val updatedMask = combineMask(region.mask, shapeMask, mode)
        return region.copy(
            mask = updatedMask,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Applies a freehand closed lasso selection loop.
     */
    fun applyLasso(
        region: NamedReferenceRegion,
        points: List<Pair<Float, Float>>,
        mode: RegionCombineMode
    ): NamedReferenceRegion {
        if (points.size < 3) return region

        val shapeMask = ByteArray(region.width * region.height)
        scanlineFillPolygon(points, shapeMask, region.width, region.height)

        val updatedMask = combineMask(region.mask, shapeMask, mode)
        return region.copy(
            mask = updatedMask,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Applies an ordered polygonal lasso selection.
     */
    fun applyPolygon(
        region: NamedReferenceRegion,
        vertices: List<Pair<Float, Float>>,
        mode: RegionCombineMode
    ): NamedReferenceRegion {
        if (vertices.size < 3) return region

        val shapeMask = ByteArray(region.width * region.height)
        scanlineFillPolygon(vertices, shapeMask, region.width, region.height)

        val updatedMask = combineMask(region.mask, shapeMask, mode)
        return region.copy(
            mask = updatedMask,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Applies an axis-aligned rectangular selection box.
     */
    fun applyRectangle(
        region: NamedReferenceRegion,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        mode: RegionCombineMode
    ): NamedReferenceRegion {
        val width = region.width
        val height = region.height
        val shapeMask = ByteArray(width * height)

        val x0 = max(0, floor(min(left, right)).toInt())
        val x1 = min(width - 1, ceil(max(left, right)).toInt())
        val y0 = max(0, floor(min(top, bottom)).toInt())
        val y1 = min(height - 1, ceil(max(top, bottom)).toInt())

        for (y in y0..y1) {
            val rowOffset = y * width
            for (x in x0..x1) {
                shapeMask[rowOffset + x] = 255.toByte()
            }
        }

        val updatedMask = combineMask(region.mask, shapeMask, mode)
        return region.copy(
            mask = updatedMask,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Applies an elliptical selection oval bounded by [left, top, right, bottom].
     */
    fun applyEllipse(
        region: NamedReferenceRegion,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        mode: RegionCombineMode
    ): NamedReferenceRegion {
        val width = region.width
        val height = region.height
        val shapeMask = ByteArray(width * height)

        val minX = min(left, right)
        val maxX = max(left, right)
        val minY = min(top, bottom)
        val maxY = max(top, bottom)

        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val rx = (maxX - minX) / 2f
        val ry = (maxY - minY) / 2f

        if (rx <= 0f || ry <= 0f) return region

        val x0 = max(0, floor(minX).toInt())
        val x1 = min(width - 1, ceil(maxX).toInt())
        val y0 = max(0, floor(minY).toInt())
        val y1 = min(height - 1, ceil(maxY).toInt())

        val invRx2 = 1.0f / (rx * rx)
        val invRy2 = 1.0f / (ry * ry)

        for (y in y0..y1) {
            val dy = y + 0.5f - cy
            val dyTerm = dy * dy * invRy2
            val rowOffset = y * width
            for (x in x0..x1) {
                val dx = x + 0.5f - cx
                if (dx * dx * invRx2 + dyTerm <= 1.0f) {
                    shapeMask[rowOffset + x] = 255.toByte()
                }
            }
        }

        val updatedMask = combineMask(region.mask, shapeMask, mode)
        return region.copy(
            mask = updatedMask,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Clears all selection mask pixels for the region.
     */
    fun clearRegion(region: NamedReferenceRegion): NamedReferenceRegion {
        return region.copy(
            mask = ByteArray(region.width * region.height),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Inverts the selection mask: M'(x, y) = 255 - M(x, y).
     */
    fun invertRegion(region: NamedReferenceRegion): NamedReferenceRegion {
        val total = region.width * region.height
        val inverted = ByteArray(total)
        for (i in 0 until total) {
            val a = region.mask[i].toInt() and 0xFF
            inverted[i] = (255 - a).toByte()
        }
        return region.copy(
            mask = inverted,
            updatedAt = System.currentTimeMillis()
        )
    }

    // -------------------------------------------------------------------------
    // 2. Project & Region Lifecycle Management
    // -------------------------------------------------------------------------

    /**
     * Creates and adds a new named region of interest to the analysis project.
     */
    fun createRegion(
        project: ReferenceAnalysisProject,
        name: String,
        instructions: String = "",
        settings: ReferenceFocusSettings = ReferenceFocusSettings(),
        colorRgba: Int? = null,
        overlayOpacity: Float = 0.45f
    ): ReferenceAnalysisProject {
        val w = project.effectiveWidth
        val h = project.effectiveHeight
        val color = colorRgba ?: DEFAULT_REGION_COLORS[project.regions.size % DEFAULT_REGION_COLORS.size]
        val newRegion = NamedReferenceRegion(
            name = name,
            instructions = instructions,
            settings = settings,
            width = w,
            height = h,
            mask = ByteArray(w * h),
            colorRgba = color,
            overlayOpacity = overlayOpacity
        )

        val updatedRegions = project.regions + newRegion
        return project.copy(
            regions = updatedRegions,
            activeRegionId = newRegion.id,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Renames an existing region by ID.
     */
    fun renameRegion(
        project: ReferenceAnalysisProject,
        regionId: String,
        newName: String
    ): ReferenceAnalysisProject {
        val updatedRegions = project.regions.map { reg ->
            if (reg.id == regionId) {
                reg.copy(name = newName, updatedAt = System.currentTimeMillis())
            } else {
                reg
            }
        }
        return project.copy(
            regions = updatedRegions,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Duplicates an existing region, cloning its mask, instructions, and settings.
     */
    fun duplicateRegion(
        project: ReferenceAnalysisProject,
        regionId: String
    ): ReferenceAnalysisProject {
        val original = project.regions.find { it.id == regionId }
            ?: throw IllegalArgumentException("Region '$regionId' not found in project")

        val newId = UUID.randomUUID().toString()
        val clonedMask = original.mask.clone()
        val nextColor = DEFAULT_REGION_COLORS[(project.regions.size + 1) % DEFAULT_REGION_COLORS.size]

        val duplicate = original.copy(
            id = newId,
            name = "Copy of ${original.name}",
            mask = clonedMask,
            colorRgba = nextColor,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val updatedRegions = project.regions + duplicate
        return project.copy(
            regions = updatedRegions,
            activeRegionId = duplicate.id,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Deletes a region by ID.
     */
    fun deleteRegion(
        project: ReferenceAnalysisProject,
        regionId: String
    ): ReferenceAnalysisProject {
        val updatedRegions = project.regions.filter { it.id != regionId }
        val nextActiveId = if (project.activeRegionId == regionId) {
            updatedRegions.firstOrNull()?.id
        } else {
            project.activeRegionId
        }

        return project.copy(
            regions = updatedRegions,
            activeRegionId = nextActiveId,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Toggles visibility (hide / show) for a region.
     */
    fun toggleVisibility(
        project: ReferenceAnalysisProject,
        regionId: String
    ): ReferenceAnalysisProject {
        val updatedRegions = project.regions.map { reg ->
            if (reg.id == regionId) {
                reg.copy(isVisible = !reg.isVisible, updatedAt = System.currentTimeMillis())
            } else {
                reg
            }
        }
        return project.copy(
            regions = updatedRegions,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Updates user instructions on an existing region.
     */
    fun updateInstructions(
        project: ReferenceAnalysisProject,
        regionId: String,
        instructions: String
    ): ReferenceAnalysisProject {
        val updatedRegions = project.regions.map { reg ->
            if (reg.id == regionId) {
                reg.copy(instructions = instructions, updatedAt = System.currentTimeMillis())
            } else {
                reg
            }
        }
        return project.copy(
            regions = updatedRegions,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Updates focus settings on an existing region.
     */
    fun updateSettings(
        project: ReferenceAnalysisProject,
        regionId: String,
        settings: ReferenceFocusSettings
    ): ReferenceAnalysisProject {
        val updatedRegions = project.regions.map { reg ->
            if (reg.id == regionId) {
                reg.copy(settings = settings, updatedAt = System.currentTimeMillis())
            } else {
                reg
            }
        }
        return project.copy(
            regions = updatedRegions,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Updates overlay opacity for a region.
     */
    fun updateOverlayOpacity(
        project: ReferenceAnalysisProject,
        regionId: String,
        opacity: Float
    ): ReferenceAnalysisProject {
        val clamped = opacity.coerceIn(0f, 1f)
        val updatedRegions = project.regions.map { reg ->
            if (reg.id == regionId) {
                reg.copy(overlayOpacity = clamped, updatedAt = System.currentTimeMillis())
            } else {
                reg
            }
        }
        return project.copy(
            regions = updatedRegions,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Sets the active region targeted for editing.
     */
    fun setActiveRegion(
        project: ReferenceAnalysisProject,
        regionId: String
    ): ReferenceAnalysisProject {
        require(project.regions.any { it.id == regionId }) {
            "Region '$regionId' does not exist in project"
        }
        return project.copy(activeRegionId = regionId)
    }

    /**
     * Updates an existing region with new boundary mask edits.
     */
    fun updateRegion(
        project: ReferenceAnalysisProject,
        updatedRegion: NamedReferenceRegion
    ): ReferenceAnalysisProject {
        val updatedRegions = project.regions.map { reg ->
            if (reg.id == updatedRegion.id) updatedRegion else reg
        }
        return project.copy(
            regions = updatedRegions,
            updatedAt = System.currentTimeMillis()
        )
    }

    // -------------------------------------------------------------------------
    // 3. Source Cropping & Viewport Stability
    // -------------------------------------------------------------------------

    /**
     * Crops all region masks within the project to match a new source crop box,
     * maintaining 100% stable spatial alignment without drift.
     */
    fun applyCrop(
        project: ReferenceAnalysisProject,
        crop: ReferenceCropBounds
    ): ReferenceAnalysisProject {
        val currentW = project.effectiveWidth
        val currentH = project.effectiveHeight

        val newW = crop.width
        val newH = crop.height

        val updatedRegions = project.regions.map { reg ->
            val newMask = ByteArray(newW * newH)
            for (cy in 0 until newH) {
                val srcY = crop.y + cy
                if (srcY in 0 until currentH) {
                    for (cx in 0 until newW) {
                        val srcX = crop.x + cx
                        if (srcX in 0 until currentW) {
                            newMask[cy * newW + cx] = reg.mask[srcY * currentW + srcX]
                        }
                    }
                }
            }
            reg.copy(
                width = newW,
                height = newH,
                mask = newMask,
                updatedAt = System.currentTimeMillis()
            )
        }

        return project.copy(
            cropBounds = crop,
            regions = updatedRegions,
            updatedAt = System.currentTimeMillis()
        )
    }

    // -------------------------------------------------------------------------
    // 4. Non-Destructive Composite Overlay Rendering
    // -------------------------------------------------------------------------

    /**
     * Renders a standalone ARGB debug overlay showing all visible focus regions
     * tinted with their respective colors and opacities on top of the preserved reference.
     * Never mutates [referencePixels].
     */
    fun renderCompositeOverlay(
        project: ReferenceAnalysisProject,
        referencePixels: IntArray,
        width: Int,
        height: Int,
        dimFactor: Float = 0.85f
    ): IntArray {
        val total = width * height
        require(referencePixels.size == total) {
            "referencePixels size (${referencePixels.size}) does not match $width x $height"
        }

        val output = IntArray(total)

        // 1. Copy reference background with optional dimming for visibility
        for (i in 0 until total) {
            output[i] = dimPixel(referencePixels[i], dimFactor)
        }

        // 2. Composite visible regions in order
        for (region in project.regions) {
            if (!region.isVisible || region.overlayOpacity <= 0f) continue
            if (region.width != width || region.height != height) continue

            val tintR = (region.colorRgba ushr 16) and 0xFF
            val tintG = (region.colorRgba ushr 8) and 0xFF
            val tintB = region.colorRgba and 0xFF
            val baseOpacity = region.overlayOpacity

            val mask = region.mask
            for (i in 0 until total) {
                val alphaVal = mask[i].toInt() and 0xFF
                if (alphaVal > 0) {
                    val effAlpha = (alphaVal / 255.0f) * baseOpacity
                    val bg = output[i]
                    val bgA = (bg ushr 24) and 0xFF
                    val bgR = (bg ushr 16) and 0xFF
                    val bgG = (bg ushr 8) and 0xFF
                    val bgB = bg and 0xFF

                    // Standard Alpha Blending (Porter-Duff OVER)
                    val outR = ((tintR * effAlpha) + (bgR * (1.0f - effAlpha))).roundToInt().coerceIn(0, 255)
                    val outG = ((tintG * effAlpha) + (bgG * (1.0f - effAlpha))).roundToInt().coerceIn(0, 255)
                    val outB = ((tintB * effAlpha) + (bgB * (1.0f - effAlpha))).roundToInt().coerceIn(0, 255)

                    output[i] = (bgA shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }
        }

        return output
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private fun combineMask(
        target: ByteArray,
        shape: ByteArray,
        mode: RegionCombineMode
    ): ByteArray {
        val out = ByteArray(target.size)
        when (mode) {
            RegionCombineMode.REPLACE -> {
                System.arraycopy(shape, 0, out, 0, out.size)
            }
            RegionCombineMode.ADD -> {
                for (i in target.indices) {
                    val t = target[i].toInt() and 0xFF
                    val s = shape[i].toInt() and 0xFF
                    out[i] = min(255, t + s).toByte()
                }
            }
            RegionCombineMode.SUBTRACT -> {
                for (i in target.indices) {
                    val t = target[i].toInt() and 0xFF
                    val s = shape[i].toInt() and 0xFF
                    out[i] = max(0, t - s).toByte()
                }
            }
            RegionCombineMode.CLEAR -> {
                // Return all zeros
            }
        }
        return out
    }

    private fun scanlineFillPolygon(
        vertices: List<Pair<Float, Float>>,
        destBuffer: ByteArray,
        width: Int,
        height: Int
    ) {
        if (vertices.size < 3) return

        var minY = height.toFloat()
        var maxY = 0f
        for (v in vertices) {
            if (v.second < minY) minY = v.second
            if (v.second > maxY) maxY = v.second
        }

        val yStart = max(0, floor(minY).toInt())
        val yEnd = min(height - 1, ceil(maxY).toInt())
        val n = vertices.size

        for (y in yStart..yEnd) {
            val scanY = y + 0.5f
            val intersections = mutableListOf<Float>()

            for (i in 0 until n) {
                val p1 = vertices[i]
                val p2 = vertices[(i + 1) % n]

                if ((p1.second <= scanY && p2.second > scanY) || (p2.second <= scanY && p1.second > scanY)) {
                    val t = (scanY - p1.second) / (p2.second - p1.second)
                    val x = p1.first + t * (p2.first - p1.first)
                    intersections.add(x)
                }
            }

            intersections.sort()

            var j = 0
            while (j < intersections.size - 1) {
                val xStart = max(0, floor(intersections[j]).toInt())
                val xEnd = min(width - 1, ceil(intersections[j + 1]).toInt())
                val rowOffset = y * width
                for (x in xStart..xEnd) {
                    destBuffer[rowOffset + x] = 255.toByte()
                }
                j += 2
            }
        }
    }

    private fun dimPixel(argb: Int, factor: Float): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (((argb ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
