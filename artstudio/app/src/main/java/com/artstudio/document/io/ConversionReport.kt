package com.artstudio.document.io

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.LayerNode

/**
 * Details an explicit non-destructive feature alteration or rasterization
 * during format export or migration.
 */
data class ConversionWarning(
    val layerId: String,
    val layerName: String,
    val feature: String, // e.g. "LAYER_MASK", "CLIPPING_MASK", "ALPHA_LOCK", "UNSUPPORTED_BLEND_MODE"
    val actionTaken: String,
    val message: String
)

/**
 * Authoritative report generated during document import/export auditing.
 * Guarantees zero silent feature loss: whenever a target format cannot faithfully
 * preserve native capabilities, explicit warnings are surfaced to user and logs.
 */
data class ConversionReport(
    val targetFormat: String,
    val warnings: List<ConversionWarning> = emptyList()
) {
    val hasLossyConversions: Boolean
        get() = warnings.isNotEmpty()

    val warningCount: Int
        get() = warnings.size

    fun formatReportText(): String {
        if (warnings.isEmpty()) {
            return "Export to '$targetFormat' completed with 100% feature preservation."
        }
        val sb = StringBuilder()
        sb.appendLine("Conversion Report for '$targetFormat' (${warnings.size} warning(s)):")
        for ((idx, w) in warnings.withIndex()) {
            sb.appendLine("  [${idx + 1}] [${w.feature}] Layer '${w.layerName}' (ID: ${w.layerId}): ${w.message} -> ${w.actionTaken}")
        }
        return sb.toString().trimEnd()
    }
}

/**
 * Audits documents for non-destructive features before export or migration.
 */
object DocumentConversionAuditor {

    fun auditExport(document: ArtDocument, targetFormat: String): ConversionReport {
        val warnings = mutableListOf<ConversionWarning>()
        val flatLayers = document.allLayersFlat()

        val normalizedFormat = targetFormat.uppercase().trim()

        for (layer in flatLayers) {
            // Check Layer Mask
            if (layer.mask != null) {
                when (normalizedFormat) {
                    "PNG", "JPEG", "WEBP", "FLAT_RASTER" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "LAYER_MASK",
                                actionTaken = "Baked mask into raster transparency",
                                message = "Flat raster formats cannot store separate non-destructive editable layer masks."
                            )
                        )
                    }
                    "ORA", "OPENRASTER" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "LAYER_MASK",
                                actionTaken = "Baked mask into layer PNG stream",
                                message = "OpenRaster stack specification does not standardize independent editable grayscale mask channels."
                            )
                        )
                    }
                    "SVG", "VECTOR" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "LAYER_MASK",
                                actionTaken = "Discarded raster mask in vector stream",
                                message = "Safe SVG vector subset export does not support complex raster bitmap masks."
                            )
                        )
                    }
                    "LEGACY_V1" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "LAYER_MASK",
                                actionTaken = "Dropped mask for v1 compatibility",
                                message = "Schema version 1.0.0 did not support layer masks."
                            )
                        )
                    }
                }
            }

            // Check Clipping Mask
            if (layer.isClippingMask) {
                when (normalizedFormat) {
                    "PNG", "JPEG", "WEBP", "FLAT_RASTER" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "CLIPPING_MASK",
                                actionTaken = "Flattened clipping relationship into base layer",
                                message = "Flat raster exports composite clipping masks permanently into base silhouette."
                            )
                        )
                    }
                    "ORA", "OPENRASTER" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "CLIPPING_MASK",
                                actionTaken = "Unclipped layer or baked clipping into layer pixels",
                                message = "OpenRaster specification does not standardize clipping mask relationships across layers."
                            )
                        )
                    }
                    "LEGACY_V1" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "CLIPPING_MASK",
                                actionTaken = "Converted clipping mask to unclipped layer",
                                message = "Schema version 1.0.0 did not support clipping mask relationships."
                            )
                        )
                    }
                }
            }

            // Check Alpha Lock
            if (layer.isAlphaLocked) {
                when (normalizedFormat) {
                    "PNG", "JPEG", "WEBP", "FLAT_RASTER", "ORA", "OPENRASTER", "SVG", "VECTOR", "LEGACY_V1" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "ALPHA_LOCK",
                                actionTaken = "Alpha lock metadata not serialized to external interchange format",
                                message = "Alpha lock is an interactive studio editing control not defined in standard interchange formats."
                            )
                        )
                    }
                }
            }

            // Check Adjustment Layer
            if (layer is LayerNode.AdjustmentLayer) {
                when (normalizedFormat) {
                    "PNG", "JPEG", "WEBP", "FLAT_RASTER" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "ADJUSTMENT_LAYER",
                                actionTaken = "Baked non-destructive adjustment into flattened raster",
                                message = "Flat raster formats cannot preserve non-destructive procedural adjustment layers."
                            )
                        )
                    }
                    "ORA", "OPENRASTER" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "ADJUSTMENT_LAYER",
                                actionTaken = "Baked adjustment into merged PNG raster layer",
                                message = "OpenRaster specification does not support parametric adjustment layers."
                            )
                        )
                    }
                    "SVG", "VECTOR" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "ADJUSTMENT_LAYER",
                                actionTaken = "Excluded adjustment layer from vector output",
                                message = "Safe SVG vector subset does not support bitmap adjustment layers."
                            )
                        )
                    }
                    "LEGACY_V1" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "ADJUSTMENT_LAYER",
                                actionTaken = "Dropped adjustment layer for v1 compatibility",
                                message = "Schema version 1.0.0 did not support adjustment layers."
                            )
                        )
                    }
                }
            }

            // Check Vector Path Layer
            if (layer is LayerNode.VectorPathLayer) {
                when (normalizedFormat) {
                    "PSD", "PNG", "JPEG", "WEBP", "FLAT_RASTER", "ORA", "OPENRASTER" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "VECTOR_GEOMETRY_RASTERIZED",
                                actionTaken = "Rasterized vector paths into bitmap layer",
                                message = "Format '$targetFormat' does not preserve native editable vector paths; rasterized to pixel layer."
                            )
                        )
                    }
                }
            }

            // Check Text Layer
            if (layer is LayerNode.TextLayer) {
                when (normalizedFormat) {
                    "PSD", "PNG", "JPEG", "WEBP", "FLAT_RASTER", "ORA", "OPENRASTER" -> {
                        warnings.add(
                            ConversionWarning(
                                layerId = layer.id,
                                layerName = layer.name,
                                feature = "TEXT_LAYER_RASTERIZED",
                                actionTaken = "Rasterized editable text into bitmap layer",
                                message = "Format '$targetFormat' does not preserve editable text objects; pre-rasterized to bitmap layer."
                            )
                        )
                    }
                }
            }
        }

        // Check Comic Panels Metadata Disclosure
        if (document.comicPanels.isNotEmpty()) {
            when (normalizedFormat) {
                "PSD", "PNG", "JPEG", "WEBP", "FLAT_RASTER", "ORA", "OPENRASTER", "SVG", "VECTOR" -> {
                    warnings.add(
                        ConversionWarning(
                            layerId = "comic_panels",
                            layerName = "Comic Panels & Guides",
                            feature = "COMIC_METADATA_FLATTENED",
                            actionTaken = "Flattened or omitted comic panel guides from exported file",
                            message = "Format '$targetFormat' does not support native comic panel layout and balloon geometry."
                        )
                    )
                }
            }
        }

        // Check Color Profile Workflow Disclosure
        val colorSpace = document.canvas.colorSpace
        if (!colorSpace.equals("sRGB", ignoreCase = true)) {
            warnings.add(
                ConversionWarning(
                    layerId = "canvas",
                    layerName = document.title,
                    feature = "COLOR_PROFILE",
                    actionTaken = "Evaluated in standard sRGB",
                    message = "Color space '$colorSpace' (Display P3 / AdobeRGB / CMYK) is unsupported for direct ICC profile embedding; rendering and export use standard sRGB math."
                )
            )
        }

        // Check Animation Timeline Disclosure
        val anim = document.animation
        if (anim != null && anim.toTimeline().frames.size > 1) {
            when (normalizedFormat) {
                "PNG", "JPEG", "WEBP", "FLAT_RASTER", "SVG", "VECTOR", "PSD", "ORA", "OPENRASTER" -> {
                    warnings.add(
                        ConversionWarning(
                            layerId = "timeline",
                            layerName = "Animation Timeline",
                            feature = "ANIMATION_TIMELINE",
                            actionTaken = "Flattened multi-frame animation to active frame single image",
                            message = "Static format '$targetFormat' cannot encode frame-by-frame animation; only the active frame was exported."
                        )
                    )
                }
            }
        }

        return ConversionReport(
            targetFormat = targetFormat,
            warnings = warnings
        )
    }
}
