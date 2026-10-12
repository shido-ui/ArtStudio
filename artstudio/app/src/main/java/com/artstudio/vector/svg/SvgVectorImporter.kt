package com.artstudio.vector.svg

import com.artstudio.document.io.ConversionReport
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.history.RasterizeVectorLayerCommand
import java.util.UUID

/**
 * Service for importing safe SVG vector documents transparently into ArtStudio projects.
 *
 * Guarantees:
 * - Preserves SVG objects as editable [VectorObject]s in a [LayerNode.VectorPathLayer].
 * - Never executes embedded scripts or fetches external network resources.
 * - Rasterises only upon explicit user invocation via [explicitRasterize].
 */
object SvgVectorImporter {

    /**
     * Imports an SVG string directly as a non-destructive [LayerNode.VectorPathLayer]
     * preserving all paths and shapes as editable vector geometry.
     */
    fun importAsVectorLayer(
        svgXml: String,
        layerName: String = "SVG Vectors",
        layerId: String = UUID.randomUUID().toString()
    ): Pair<LayerNode.VectorPathLayer, ConversionReport> {
        val result = SafeSvgParser.parse(svgXml)
        val vectorLayer = LayerNode.VectorPathLayer(
            id = layerId,
            name = layerName,
            vectorObjects = result.vectorObjects
        )
        return Pair(vectorLayer, result.conversionReport)
    }

    /**
     * Imports an SVG string into a newly initialized [ArtDocument] sized to the SVG's canvas
     * dimensions or viewBox, placing all shapes in an editable [LayerNode.VectorPathLayer].
     */
    fun importAsDocument(
        svgXml: String,
        title: String = "Imported SVG"
    ): Pair<ArtDocument, ConversionReport> {
        val result = SafeSvgParser.parse(svgXml)
        val vectorLayer = LayerNode.VectorPathLayer(
            name = "Vector 1",
            vectorObjects = result.vectorObjects
        )
        val doc = ArtDocument(
            title = title,
            canvas = CanvasConfig(
                width = result.canvasWidth,
                height = result.canvasHeight
            ),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(vectorLayer)
            )
        )
        return Pair(doc, result.conversionReport)
    }

    /**
     * Explicit rasterisation operation converting an editable [LayerNode.VectorPathLayer]
     * to a [LayerNode.RasterLayer] upon explicit user choice.
     */
    fun explicitRasterize(
        document: ArtDocument,
        vectorLayerId: String,
        createNewLayer: Boolean = false
    ): RasterizeVectorLayerCommand {
        return RasterizeVectorLayerCommand.create(document, vectorLayerId, createNewLayer)
    }
}
