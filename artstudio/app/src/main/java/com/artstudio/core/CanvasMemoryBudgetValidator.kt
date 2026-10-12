package com.artstudio.core

import android.app.ActivityManager
import android.content.Context

/**
 * Validates requested canvas dimensions and layer counts against available device memory
 * and Android ActivityManager memoryClass / largeMemoryClass ceilings.
 */
object CanvasMemoryBudgetValidator {

    sealed class CanvasValidationResult {
        data class Accepted(
            val maxAllowedDimension: Int,
            val maxAllowedLayers: Int
        ) : CanvasValidationResult()

        data class Warning(
            val warningMessage: String,
            val maxAllowedDimension: Int,
            val maxAllowedLayers: Int
        ) : CanvasValidationResult()

        data class Rejected(
            val rejectionReason: String,
            val maxAllowedDimension: Int,
            val maxAllowedLayers: Int
        ) : CanvasValidationResult()
    }

    data class MemoryBudget(
        val memoryClassMb: Int,
        val largeMemoryClassMb: Int,
        val maxDimensionPx: Int,
        val maxLayers: Int,
        val maxLayerMemoryBytes: Long
    )

    /**
     * Derives the active memory budget from [context] (or fallback from [DeviceMemoryPolicy]).
     */
    fun deriveMemoryBudget(context: Context? = null): MemoryBudget {
        var memClass = 256
        var largeMemClass = 512

        val activeTier = if (context != null) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (am != null) {
                    memClass = am.memoryClass
                    largeMemClass = am.largeMemoryClass
                    when {
                        memClass <= 128 -> DeviceRamTier.RAM_2GB
                        memClass <= 256 -> DeviceRamTier.RAM_4GB
                        memClass <= 384 -> DeviceRamTier.RAM_6GB
                        else -> DeviceRamTier.RAM_8GB_PLUS
                    }
                } else DeviceMemoryPolicy.currentTier
            } catch (_: Throwable) {
                DeviceMemoryPolicy.currentTier
            }
        } else {
            DeviceMemoryPolicy.currentTier
        }

        val (maxDim, maxLayers, maxMemoryBytes) = when (activeTier) {
            DeviceRamTier.RAM_2GB -> Triple(2048, 4, 64L * 1024 * 1024)
            DeviceRamTier.RAM_4GB -> Triple(4096, 12, 192L * 1024 * 1024)
            DeviceRamTier.RAM_6GB -> Triple(6144, 24, 384L * 1024 * 1024)
            DeviceRamTier.RAM_8GB_PLUS -> Triple(8192, 32, 512L * 1024 * 1024)
        }

        return MemoryBudget(
            memoryClassMb = memClass,
            largeMemoryClassMb = largeMemClass,
            maxDimensionPx = maxDim,
            maxLayers = maxLayers,
            maxLayerMemoryBytes = maxMemoryBytes
        )
    }

    /**
     * Validates whether a canvas with dimensions [width]x[height] and [layerCount] layers
     * is safe to allocate within device memory constraints.
     */
    fun validateCanvas(
        width: Int,
        height: Int,
        layerCount: Int = 1,
        context: Context? = null
    ): CanvasValidationResult {
        if (width <= 0 || height <= 0) {
            return CanvasValidationResult.Rejected(
                rejectionReason = "Canvas dimensions must be positive, got ${width}x${height}.",
                maxAllowedDimension = 2048,
                maxAllowedLayers = 4
            )
        }

        val budget = deriveMemoryBudget(context)

        // 1. Hard dimension check
        if (width > budget.maxDimensionPx || height > budget.maxDimensionPx) {
            return CanvasValidationResult.Rejected(
                rejectionReason = "Canvas size ${width}x${height} exceeds maximum supported dimension of ${budget.maxDimensionPx}x${budget.maxDimensionPx} on this device profile (${budget.memoryClassMb}MB memory class).",
                maxAllowedDimension = budget.maxDimensionPx,
                maxAllowedLayers = budget.maxLayers
            )
        }

        // 2. Maximum layers check
        val dynamicLayerLimit = budget.maxLayers.coerceAtMost(
            DeviceMemoryPolicy.maxLayersForCanvas(width, height)
        )

        if (layerCount > dynamicLayerLimit) {
            return CanvasValidationResult.Rejected(
                rejectionReason = "Requested $layerCount layers exceeds device memory limit of $dynamicLayerLimit layers for a ${width}x${height} canvas.",
                maxAllowedDimension = budget.maxDimensionPx,
                maxAllowedLayers = dynamicLayerLimit
            )
        }

        // 3. Total memory consumption check
        val bytesPerLayer = width.toLong() * height.toLong() * 4L
        val totalLayerBytes = bytesPerLayer * layerCount.toLong()

        if (totalLayerBytes > budget.maxLayerMemoryBytes) {
            return CanvasValidationResult.Rejected(
                rejectionReason = "Total layer buffer allocation (${totalLayerBytes / (1024 * 1024)} MB) exceeds memory budget of ${budget.maxLayerMemoryBytes / (1024 * 1024)} MB.",
                maxAllowedDimension = budget.maxDimensionPx,
                maxAllowedLayers = dynamicLayerLimit
            )
        }

        // 4. Warning for high-stress allocation (> 80% budget)
        if (totalLayerBytes > (budget.maxLayerMemoryBytes * 0.80f).toLong()) {
            return CanvasValidationResult.Warning(
                warningMessage = "Large canvas allocation (${totalLayerBytes / (1024 * 1024)} MB): close background apps to ensure smooth performance.",
                maxAllowedDimension = budget.maxDimensionPx,
                maxAllowedLayers = dynamicLayerLimit
            )
        }

        return CanvasValidationResult.Accepted(
            maxAllowedDimension = budget.maxDimensionPx,
            maxAllowedLayers = dynamicLayerLimit
        )
    }
}
