package com.artstudio.document.io.psd

import com.artstudio.document.io.ConversionReport
import com.artstudio.document.model.ArtDocument
import java.io.File

/**
 * Photoshop 8BPS File Header.
 */
data class PsdHeader(
    val channels: Int,
    val height: Int,
    val width: Int,
    val depth: Int = 8,
    val colorMode: Int = 3 // 3 = RGB
)

/**
 * Channel metadata in a PSD layer record.
 */
data class PsdChannelInfo(
    val id: Int,      // 0 = R, 1 = G, 2 = B, -1 = transparency mask, -2 = user layer mask
    val length: Int   // Length of channel data in bytes
)

/**
 * Mask parameters in a PSD layer record.
 */
data class PsdMaskInfo(
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val defaultColor: Int = 0,
    val flags: Int = 0
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

/**
 * Parsed record for a single PSD layer.
 */
data class PsdLayerRecord(
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val channels: List<PsdChannelInfo>,
    val blendModeKey: String,
    val opacity: Int,      // 0..255
    val clipping: Int,     // 0 = base, 1 = non-base (clipping mask)
    val flags: Int,        // bit 0 = alpha lock, bit 1 = invisible
    val name: String,
    val maskInfo: PsdMaskInfo? = null
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val isVisible: Boolean get() = (flags and 0x02) == 0
    val isAlphaLocked: Boolean get() = (flags and 0x01) != 0
    val isClippingMask: Boolean get() = clipping == 1
}

/**
 * A decoded layer with its in-memory raster pixel and optional mask buffers.
 */
data class PsdLayer(
    val record: PsdLayerRecord,
    val pixelBuffer: IntArray?,
    val maskBuffer: IntArray? = null
)

/**
 * Complete parsed in-memory representation of a Photoshop document.
 */
data class PsdDocument(
    val header: PsdHeader,
    val layers: List<PsdLayer>,
    val compositePixels: IntArray? = null
)

/**
 * Result of importing a PSD file into ArtStudio.
 */
data class PsdImportResult(
    val document: ArtDocument,
    val layerPixelBuffers: Map<String, IntArray> = emptyMap(),
    val layerMaskBuffers: Map<String, IntArray> = emptyMap(),
    val conversionReport: ConversionReport
)

/**
 * Result of exporting an ArtStudio project to a PSD file.
 */
data class PsdExportResult(
    val outputFile: File,
    val layerCount: Int,
    val fileSize: Long,
    val conversionReport: ConversionReport
)
