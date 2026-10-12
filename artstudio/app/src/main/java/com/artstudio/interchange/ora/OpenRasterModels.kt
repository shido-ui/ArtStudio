package com.artstudio.interchange.ora

import com.artstudio.document.io.ConversionReport
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.BlendModeType
import java.io.File

/**
 * OpenRaster (ORA) layer tree node.
 */
sealed class OraNode {
    abstract val name: String
    abstract val opacity: Float
    abstract val visible: Boolean
    abstract val compositeOp: String

    /**
     * Standard OpenRaster raster layer containing a PNG image reference.
     */
    data class Layer(
        override val name: String,
        val src: String,
        val x: Int = 0,
        val y: Int = 0,
        override val opacity: Float = 1.0f,
        override val visible: Boolean = true,
        override val compositeOp: String = "svg:src-over"
    ) : OraNode()

    /**
     * OpenRaster layer group (nested stack).
     */
    data class Stack(
        override val name: String = "Group",
        override val opacity: Float = 1.0f,
        override val visible: Boolean = true,
        override val compositeOp: String = "svg:src-over",
        val children: List<OraNode> = emptyList()
    ) : OraNode()
}

/**
 * Representation of OpenRaster `stack.xml` root document.
 */
data class OraImage(
    val version: String = "0.0.3",
    val width: Int,
    val height: Int,
    val rootStack: OraNode.Stack
)

/**
 * Result outcome of importing an OpenRaster (.ora) archive.
 */
data class OraImportResult(
    val document: ArtDocument,
    val layerPixelBuffers: Map<String, IntArray>,
    val thumbnail: ByteArray? = null,
    val mergedImage: ByteArray? = null,
    val report: ConversionReport
)

/**
 * Result outcome of exporting an ArtDocument to OpenRaster (.ora).
 */
data class OraExportResult(
    val outputFile: File,
    val layerCount: Int,
    val fileSize: Long,
    val report: ConversionReport
)
