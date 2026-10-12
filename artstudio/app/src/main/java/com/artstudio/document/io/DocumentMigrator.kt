package com.artstudio.document.io

import com.artstudio.document.model.*

/**
 * Handles migration from older schema versions (v1.0.0) to the current authoritative schema (v2.0.0).
 *
 * In Schema v1.0.0:
 * - Layers were stored as a flat list under "layers".
 * - Layer opacity could be specified as 0..100 integer/float or 0..1.
 * - No layer groups or clipping masks existed.
 * - Root canvas lacked colorSpace (defaults to "sRGB").
 *
 * Migration creates an authoritative root LayerGroup, normalizes opacities,
 * attaches default transforms, and preserves layer IDs, order, and coordinates.
 */
object DocumentMigrator {

    fun migrateV1ToV2(v1Map: Map<String, Any?>): ArtDocument {
        val id = v1Map["id"]?.toString() ?: "migrated_${System.currentTimeMillis()}"
        val title = v1Map["title"]?.toString() ?: "Untitled"
        val appVersion = v1Map["appVersion"]?.toString() ?: "1.0.0"
        val createdAt = (v1Map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = System.currentTimeMillis()

        // 1. Canvas Config
        @Suppress("UNCHECKED_CAST")
        val canvasMap = v1Map["canvas"] as? Map<String, Any?>
            ?: throw CorruptDocumentException("Missing canvas configuration in v1 project.json")
        val width = (canvasMap["width"] as? Number)?.toInt()
            ?: throw CorruptDocumentException("Missing canvas width in v1 project.json")
        val height = (canvasMap["height"] as? Number)?.toInt()
            ?: throw CorruptDocumentException("Missing canvas height in v1 project.json")
        if (width <= 0 || height <= 0) {
            throw CorruptDocumentException("Invalid v1 canvas dimensions: ${width}x$height")
        }
        val dpi = (canvasMap["dpi"] as? Number)?.toInt() ?: 300
        val bgColor = (canvasMap["backgroundColor"] as? Number)?.toLong() ?: 0xFFFFFFFFL
        val colorSpace = canvasMap["colorSpace"]?.toString() ?: "sRGB"
        val canvas = CanvasConfig(width, height, dpi, bgColor, colorSpace)

        // 2. Flat Layers -> Root Group with LayerNodes
        @Suppress("UNCHECKED_CAST")
        val rawLayers = (v1Map["layers"] as? List<*>) ?: emptyList<Any?>()
        val migratedLayers = rawLayers.mapIndexedNotNull { index, rawLayer ->
            val layerMap = rawLayer as? Map<String, Any?> ?: return@mapIndexedNotNull null
            val layerId = layerMap["id"]?.toString() ?: "layer_$index"
            val layerName = layerMap["name"]?.toString() ?: "Layer ${index + 1}"
            val visible = (layerMap["visible"] as? Boolean) ?: true

            // Normalize opacity: v1 might have used 0..100 or 0..1
            val rawOpacity = (layerMap["opacity"] as? Number)?.toFloat() ?: 1.0f
            val normalizedOpacity = if (rawOpacity > 1.0f) {
                (rawOpacity / 100f).coerceIn(0f, 1f)
            } else {
                rawOpacity.coerceIn(0f, 1f)
            }

            val blendModeKey = layerMap["blendMode"]?.toString() ?: "normal"
            val blendMode = BlendModeType.fromKey(blendModeKey)
            val isLocked = (layerMap["isLocked"] as? Boolean) ?: false
            val isClipping = (layerMap["isClippingMask"] as? Boolean) ?: false

            val type = layerMap["type"]?.toString() ?: "raster"

            if (type == "vector") {
                @Suppress("UNCHECKED_CAST")
                val rawPaths = (layerMap["paths"] as? List<*>) ?: emptyList<Any?>()
                val paths = rawPaths.mapNotNull { p ->
                    val pMap = p as? Map<String, Any?> ?: return@mapNotNull null
                    val pathId = pMap["id"]?.toString() ?: return@mapNotNull null
                    val brushId = pMap["brushId"]?.toString() ?: "inking_pen"
                    val color = (pMap["strokeColor"] as? Number)?.toLong() ?: 0xFF000000L
                    val strokeW = (pMap["strokeWidth"] as? Number)?.toFloat() ?: 4f

                    @Suppress("UNCHECKED_CAST")
                    val rawPts = (pMap["points"] as? List<*>) ?: emptyList<Any?>()
                    val pts = rawPts.mapNotNull { pt ->
                        val ptMap = pt as? Map<String, Any?> ?: return@mapNotNull null
                        val px = (ptMap["x"] as? Number)?.toFloat() ?: return@mapNotNull null
                        val py = (ptMap["y"] as? Number)?.toFloat() ?: return@mapNotNull null
                        val pr = (ptMap["p"] as? Number)?.toFloat() ?: 0.5f
                        val tx = (ptMap["tx"] as? Number)?.toFloat() ?: 0f
                        val ty = (ptMap["ty"] as? Number)?.toFloat() ?: 0f
                        val t = (ptMap["t"] as? Number)?.toLong() ?: 0L
                        StrokePointData(px, py, pr, tx, ty, t)
                    }
                    VectorPathData(pathId, pts, brushId, color, strokeW)
                }

                LayerNode.VectorPathLayer(
                    id = layerId,
                    name = layerName,
                    visible = visible,
                    opacity = normalizedOpacity,
                    blendMode = blendMode,
                    transform = LayerTransform(),
                    isLocked = isLocked,
                    isClippingMask = isClipping,
                    paths = paths
                )
            } else {
                val assetPath = layerMap["assetPath"]?.toString()
                val layerW = (layerMap["width"] as? Number)?.toInt() ?: width
                val layerH = (layerMap["height"] as? Number)?.toInt() ?: height
                val offX = (layerMap["offsetX"] as? Number)?.toInt() ?: 0
                val offY = (layerMap["offsetY"] as? Number)?.toInt() ?: 0

                LayerNode.RasterLayer(
                    id = layerId,
                    name = layerName,
                    visible = visible,
                    opacity = normalizedOpacity,
                    blendMode = blendMode,
                    transform = LayerTransform(),
                    isLocked = isLocked,
                    isClippingMask = isClipping,
                    assetPath = assetPath,
                    width = layerW,
                    height = layerH,
                    offsetX = offX,
                    offsetY = offY
                )
            }
        }

        val rootGroup = LayerNode.LayerGroup(
            id = "root_group",
            name = "Root",
            visible = true,
            opacity = 1.0f,
            blendMode = BlendModeType.Normal,
            transform = LayerTransform(),
            isLocked = false,
            isClippingMask = false,
            children = migratedLayers,
            isCollapsed = false
        )

        // 3. Palettes (if any in v1)
        @Suppress("UNCHECKED_CAST")
        val rawPalettes = (v1Map["palettes"] as? List<*>) ?: emptyList<Any?>()
        val palettes = rawPalettes.mapNotNull { p ->
            val pMap = p as? Map<String, Any?> ?: return@mapNotNull null
            val pId = pMap["id"]?.toString() ?: return@mapNotNull null
            val pName = pMap["name"]?.toString() ?: "Palette"
            val rawSwatches = (pMap["swatches"] as? List<*>) ?: emptyList<Any?>()
            val swatches = rawSwatches.mapNotNull { s ->
                val sMap = s as? Map<String, Any?> ?: return@mapNotNull null
                val hex = sMap["hex"]?.toString() ?: return@mapNotNull null
                ColorSwatchData(hex, sMap["name"]?.toString())
            }
            PaletteDefinition(pId, pName, swatches)
        }

        // 4. References (if any in v1)
        @Suppress("UNCHECKED_CAST")
        val rawRefs = (v1Map["referenceImages"] as? List<*>) ?: emptyList<Any?>()
        val referenceImages = rawRefs.mapNotNull { r ->
            val rMap = r as? Map<String, Any?> ?: return@mapNotNull null
            val rId = rMap["id"]?.toString() ?: return@mapNotNull null
            val assetPath = rMap["assetPath"]?.toString() ?: return@mapNotNull null
            val x = (rMap["x"] as? Number)?.toFloat() ?: 0f
            val y = (rMap["y"] as? Number)?.toFloat() ?: 0f
            val w = (rMap["width"] as? Number)?.toFloat() ?: 100f
            val h = (rMap["height"] as? Number)?.toFloat() ?: 100f
            val op = (rMap["opacity"] as? Number)?.toFloat() ?: 1f
            val vis = (rMap["visible"] as? Boolean) ?: true
            ReferenceImageConfig(rId, assetPath, x, y, w, h, op, vis)
        }

        return ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = appVersion,
            id = id,
            title = title,
            canvas = canvas,
            rootGroup = rootGroup,
            palettes = palettes,
            referenceImages = referenceImages,
            animation = null,
            autoDrawProvenance = null,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
