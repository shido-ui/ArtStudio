package com.artstudio.core

import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Tracks the dirty invalidation bounding box in canonical canvas document space.
 * Enables fine-grained tile invalidation so only modified tiles are re-rasterized.
 */
class DirtyRegionTracker {
    var minX: Float = Float.MAX_VALUE
        private set
    var minY: Float = Float.MAX_VALUE
        private set
    var maxX: Float = -Float.MAX_VALUE
        private set
    var maxY: Float = -Float.MAX_VALUE
        private set

    val isDirty: Boolean
        get() = minX < maxX && minY < maxY

    val width: Float
        get() = if (isDirty) maxX - minX else 0f

    val height: Float
        get() = if (isDirty) maxY - minY else 0f

    val area: Float
        get() = width * height

    fun reset() {
        minX = Float.MAX_VALUE
        minY = Float.MAX_VALUE
        maxX = -Float.MAX_VALUE
        maxY = -Float.MAX_VALUE
    }

    fun markDirtyRect(left: Float, top: Float, right: Float, bottom: Float) {
        if (left >= right || top >= bottom) return
        minX = min(minX, left)
        minY = min(minY, top)
        maxX = max(maxX, right)
        maxY = max(maxY, bottom)
    }

    fun markDirtyPoint(x: Float, y: Float, radius: Float) {
        val r = max(1f, radius)
        markDirtyRect(x - r, y - r, x + r, y + r)
    }

    fun getBounds(): CanvasRect? {
        return if (isDirty) CanvasRect(minX, minY, maxX, maxY) else null
    }

    /**
     * Computes the grid indices (col, row) of all tiles that intersect this dirty region.
     * Guaranteed to return only the subset of affected tiles.
     */
    fun getIntersectingTiles(
        tileSize: Int,
        totalCols: Int,
        totalRows: Int
    ): List<Pair<Int, Int>> {
        if (!isDirty || tileSize <= 0) return emptyList()

        val startCol = max(0, floor(minX / tileSize).toInt())
        val endCol = min(totalCols - 1, floor((maxX - 0.001f) / tileSize).toInt())

        val startRow = max(0, floor(minY / tileSize).toInt())
        val endRow = min(totalRows - 1, floor((maxY - 0.001f) / tileSize).toInt())

        if (startCol > endCol || startRow > endRow) return emptyList()

        val tiles = ArrayList<Pair<Int, Int>>((endCol - startCol + 1) * (endRow - startRow + 1))
        for (r in startRow..endRow) {
            for (c in startCol..endCol) {
                tiles.add(Pair(c, r))
            }
        }
        return tiles
    }
}
