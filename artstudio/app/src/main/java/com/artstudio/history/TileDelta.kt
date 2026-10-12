package com.artstudio.history

import kotlin.math.max
import kotlin.math.min

/**
 * Tile-level delta representing the before and after pixel states of an individual tile
 * within a canvas grid (e.g. 128x128 or 256x256).
 *
 * Invariants:
 * - Only tiles intersecting modified regions are instantiated and stored.
 * - Entire untouched canvas regions consume ZERO tile memory.
 * - Memory is strictly bounded to the area of modified tiles.
 */
data class TileDelta(
    val col: Int,
    val row: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val prePixels: IntArray,
    val postPixels: IntArray
) {
    val pixelCount: Int get() = width * height

    val estimatedMemoryBytes: Long
        get() = 64L + (prePixels.size.toLong() + postPixels.size.toLong()) * 4L

    fun applyTo(targetPixels: IntArray, canvasWidth: Int, usePre: Boolean) {
        val src = if (usePre) prePixels else postPixels
        val srcWidth = width
        for (r in 0 until height) {
            val dstRowStart = (y + r) * canvasWidth + x
            val srcRowStart = r * srcWidth
            if (dstRowStart >= 0 && dstRowStart + srcWidth <= targetPixels.size && srcRowStart + srcWidth <= src.size) {
                System.arraycopy(src, srcRowStart, targetPixels, dstRowStart, srcWidth)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TileDelta
        return col == other.col &&
                row == other.row &&
                x == other.x &&
                y == other.y &&
                width == other.width &&
                height == other.height &&
                prePixels.contentEquals(other.prePixels) &&
                postPixels.contentEquals(other.postPixels)
    }

    override fun hashCode(): Int {
        var result = col
        result = 31 * result + row
        result = 31 * result + x
        result = 31 * result + y
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + prePixels.contentHashCode()
        result = 31 * result + postPixels.contentHashCode()
        return result
    }
}

data class TileDeltaSlice(
    val col: Int,
    val row: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val pixels: IntArray
)

object TileDeltaHelper {
    const val DEFAULT_TILE_SIZE = 128

    /**
     * Extracts tile-level deltas between two pixel buffers.
     * Only tiles containing at least one changed pixel are returned.
     */
    fun extractTileDeltas(
        prePixels: IntArray,
        postPixels: IntArray,
        canvasWidth: Int,
        canvasHeight: Int,
        tileSize: Int = DEFAULT_TILE_SIZE,
        boundsMinX: Int = 0,
        boundsMinY: Int = 0,
        boundsMaxX: Int = canvasWidth - 1,
        boundsMaxY: Int = canvasHeight - 1
    ): List<TileDelta> {
        if (prePixels.size != canvasWidth * canvasHeight || postPixels.size != canvasWidth * canvasHeight) {
            return emptyList()
        }

        val minX = max(0, boundsMinX)
        val minY = max(0, boundsMinY)
        val maxX = min(canvasWidth - 1, boundsMaxX)
        val maxY = min(canvasHeight - 1, boundsMaxY)

        if (minX > maxX || minY > maxY) return emptyList()

        val startCol = minX / tileSize
        val endCol = maxX / tileSize
        val startRow = minY / tileSize
        val endRow = maxY / tileSize

        val deltas = mutableListOf<TileDelta>()

        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                val tileX = col * tileSize
                val tileY = row * tileSize
                val tileW = min(tileSize, canvasWidth - tileX)
                val tileH = min(tileSize, canvasHeight - tileY)

                var hasDiff = false
                diffCheck@ for (r in 0 until tileH) {
                    val rowOffset = (tileY + r) * canvasWidth + tileX
                    for (c in 0 until tileW) {
                        val idx = rowOffset + c
                        if (prePixels[idx] != postPixels[idx]) {
                            hasDiff = true
                            break@diffCheck
                        }
                    }
                }

                if (hasDiff) {
                    val preSlice = IntArray(tileW * tileH)
                    val postSlice = IntArray(tileW * tileH)
                    for (r in 0 until tileH) {
                        val rowOffset = (tileY + r) * canvasWidth + tileX
                        val sliceOffset = r * tileW
                        System.arraycopy(prePixels, rowOffset, preSlice, sliceOffset, tileW)
                        System.arraycopy(postPixels, rowOffset, postSlice, sliceOffset, tileW)
                    }
                    deltas.add(
                        TileDelta(
                            col = col,
                            row = row,
                            x = tileX,
                            y = tileY,
                            width = tileW,
                            height = tileH,
                            prePixels = preSlice,
                            postPixels = postSlice
                        )
                    )
                }
            }
        }

        return deltas
    }

    /**
     * Extracts a single tile's slice from a canvas pixel buffer.
     */
    fun extractTileSlice(
        pixels: IntArray,
        canvasWidth: Int,
        canvasHeight: Int,
        col: Int,
        row: Int,
        tileSize: Int = DEFAULT_TILE_SIZE
    ): TileDeltaSlice {
        val tileX = col * tileSize
        val tileY = row * tileSize
        val tileW = min(tileSize, canvasWidth - tileX)
        val tileH = min(tileSize, canvasHeight - tileY)
        val slice = IntArray(tileW * tileH)

        for (r in 0 until tileH) {
            val srcOffset = (tileY + r) * canvasWidth + tileX
            val dstOffset = r * tileW
            if (srcOffset >= 0 && srcOffset + tileW <= pixels.size) {
                System.arraycopy(pixels, srcOffset, slice, dstOffset, tileW)
            }
        }

        return TileDeltaSlice(col, row, tileX, tileY, tileW, tileH, slice)
    }
}
