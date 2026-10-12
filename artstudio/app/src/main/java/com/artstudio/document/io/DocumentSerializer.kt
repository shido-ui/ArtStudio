package com.artstudio.document.io

import com.artstudio.document.model.*

/**
 * Serializes and deserializes ArtDocument to/from JSON maps and strings,
 * validating schema invariants, stable IDs, and coordinate bounds.
 */
object DocumentSerializer {

    fun toJsonString(document: ArtDocument, indent: Int = 2): String {
        val map = toMap(document)
        return ArtJson.stringify(map, indent)
    }

    fun fromJsonString(jsonString: String): ArtDocument {
        val raw = try {
            ArtJson.parse(jsonString)
        } catch (e: Exception) {
            throw CorruptDocumentException("Failed to parse project.json: ${e.message}", e)
        }

        @Suppress("UNCHECKED_CAST")
        val map = raw as? Map<String, Any?>
            ?: throw CorruptDocumentException("Root of project.json must be a JSON object")

        return try {
            fromMap(map)
        } catch (e: CorruptDocumentException) {
            throw e
        } catch (e: UnsupportedSchemaVersionException) {
            throw e
        } catch (e: Exception) {
            throw CorruptDocumentException("Corrupted document structure: ${e.message}", e)
        }
    }

    fun toMap(doc: ArtDocument): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map["schemaVersion"] = doc.schemaVersion
        map["appVersion"] = doc.appVersion
        map["id"] = doc.id
        map["title"] = doc.title
        map["createdAt"] = doc.createdAt
        map["updatedAt"] = doc.updatedAt

        // Canvas
        map["canvas"] = mapOf(
            "width" to doc.canvas.width,
            "height" to doc.canvas.height,
            "dpi" to doc.canvas.dpi,
            "backgroundColor" to doc.canvas.backgroundColor,
            "colorSpace" to doc.canvas.colorSpace
        )

        // Root Group
        map["rootGroup"] = layerGroupToMap(doc.rootGroup)
        doc.selectedLayerId?.let { map["selectedLayerId"] = it }

        // Palettes
        map["palettes"] = doc.palettes.map { palette ->
            mapOf(
                "id" to palette.id,
                "name" to palette.name,
                "swatches" to palette.swatches.map { s ->
                    mapOf("hex" to s.hex, "name" to s.name)
                }
            )
        }

        // Gradients
        map["gradientPresets"] = doc.gradientPresets.map { g ->
            mapOf(
                "id" to g.id,
                "name" to g.name,
                "type" to g.type.id,
                "stops" to g.stops.map { s ->
                    mapOf("position" to s.position, "color" to s.color.hexArgb)
                },
                "startX" to g.startX,
                "startY" to g.startY,
                "endX" to g.endX,
                "endY" to g.endY,
                "centerX" to g.centerX,
                "centerY" to g.centerY,
                "radius" to g.radius,
                "tileMode" to g.tileMode.id,
                "interpolateInLinearSpace" to g.interpolateInLinearSpace
            )
        }

        // References
        map["referenceImages"] = doc.referenceImages.map { ref ->
            mapOf(
                "id" to ref.id,
                "assetPath" to ref.assetPath,
                "x" to ref.x,
                "y" to ref.y,
                "width" to ref.width,
                "height" to ref.height,
                "opacity" to ref.opacity,
                "visible" to ref.visible
            )
        }

        // Animation
        doc.animation?.let { anim ->
            map["animation"] = mapOf(
                "fps" to anim.fps,
                "totalFrames" to anim.totalFrames,
                "onionSkinEnabled" to anim.onionSkinEnabled,
                "onionSkinFramesBefore" to anim.onionSkinFramesBefore,
                "onionSkinFramesAfter" to anim.onionSkinFramesAfter,
                "onionSkinTintBefore" to anim.onionSkinTintBefore,
                "onionSkinTintAfter" to anim.onionSkinTintAfter,
                "onionSkinOpacityBefore" to anim.onionSkinOpacityBefore,
                "onionSkinOpacityAfter" to anim.onionSkinOpacityAfter,
                "onionSkinShowBefore" to anim.onionSkinShowBefore,
                "onionSkinShowAfter" to anim.onionSkinShowAfter,
                "onionSkinTintMode" to anim.onionSkinTintMode,
                "onionSkinFalloff" to anim.onionSkinFalloff,
                "onionSkinSuppressDuringPlayback" to anim.onionSkinSuppressDuringPlayback,
                "currentFrameIndex" to anim.currentFrameIndex,
                "staticLayerIds" to anim.staticLayerIds,
                "loopMode" to anim.loopMode,
                "frames" to anim.frames.map { it.toMap() },
                "frameTracks" to anim.frameTracks.map { track ->
                    mapOf(
                        "layerId" to track.layerId,
                        "activeFrames" to track.activeFrames
                    )
                }
            )
        }

        // AutoDraw Provenance
        doc.autoDrawProvenance?.let { prov ->
            val pMap = mutableMapOf<String, Any?>(
                "sourceImageHash" to prov.sourceImageHash,
                "algorithm" to prov.algorithm,
                "edgeThresholdLow" to prov.edgeThresholdLow,
                "edgeThresholdHigh" to prov.edgeThresholdHigh,
                "generatedLayerIds" to prov.generatedLayerIds,
                "timestampMs" to prov.timestampMs
            )
            prov.aiAugmentation?.let { ai ->
                pMap["aiAugmentation"] = ai.toMap()
            }
            prov.stylePresetId?.let { pMap["stylePresetId"] = it }
            prov.autoDrawGroupId?.let { pMap["autoDrawGroupId"] = it }
            if (prov.regions.isNotEmpty()) {
                pMap["regions"] = prov.regions.map { r ->
                    mapOf(
                        "regionId" to r.regionId,
                        "regionName" to r.regionName,
                        "colorRgb" to r.colorRgb,
                        "semanticRole" to r.semanticRole,
                        "layerIds" to r.layerIds,
                        "pathIds" to r.pathIds,
                        "isLocked" to r.isLocked
                    )
                }
            }
            if (prov.userOverrides.isNotEmpty()) {
                pMap["userOverrides"] = prov.userOverrides.map { u ->
                    mapOf(
                        "overrideId" to u.overrideId,
                        "regionId" to u.regionId,
                        "overrideType" to u.overrideType.name,
                        "description" to u.description,
                        "timestampMs" to u.timestampMs,
                        "layerId" to u.layerId,
                        "details" to u.details
                    )
                }
            }
            if (prov.layerRoles.isNotEmpty()) {
                pMap["layerRoles"] = prov.layerRoles
            }
            if (prov.customParameters.isNotEmpty()) {
                pMap["customParameters"] = prov.customParameters
            }
            map["autoDrawProvenance"] = pMap
        }

        // Comic & Manga Elements
        if (doc.comicPanels.isNotEmpty()) {
            map["comicPanels"] = doc.comicPanels.map { comicPanelToMap(it) }
        }
        if (doc.speechBubbles.isNotEmpty()) {
            map["speechBubbles"] = doc.speechBubbles.map { speechBubbleToMap(it) }
        }
        if (doc.screentones.isNotEmpty()) {
            map["screentones"] = doc.screentones.map { screentoneToMap(it) }
        }
        doc.comicPageGuides?.let { guides ->
            map["comicPageGuides"] = comicPageGuidesToMap(guides)
        }

        // Drawing Schedule
        doc.drawingSchedule?.let { sched ->
            map["drawingSchedule"] = drawingScheduleToMap(sched)
        }

        return map
    }

    fun fromMap(map: Map<String, Any?>): ArtDocument {
        val schemaVer = map["schemaVersion"]?.toString()
            ?: throw CorruptDocumentException("Missing schemaVersion in project.json")

        // Check if migration is needed
        if (schemaVer == ArtDocument.LEGACY_SCHEMA_VERSION) {
            return DocumentMigrator.migrateV1ToV2(map)
        }

        if (schemaVer != ArtDocument.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedSchemaVersionException("Unsupported schema version '$schemaVer'")
        }

        val id = map["id"]?.toString() ?: throw CorruptDocumentException("Missing document id")
        val title = map["title"]?.toString() ?: "Untitled"
        val appVersion = map["appVersion"]?.toString() ?: "1.0.0"
        val createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()

        // Canvas
        @Suppress("UNCHECKED_CAST")
        val canvasMap = map["canvas"] as? Map<String, Any?>
            ?: throw CorruptDocumentException("Missing or invalid canvas config")
        val canvasWidth = (canvasMap["width"] as? Number)?.toInt()
            ?: throw CorruptDocumentException("Missing canvas width")
        val canvasHeight = (canvasMap["height"] as? Number)?.toInt()
            ?: throw CorruptDocumentException("Missing canvas height")
        val dpi = (canvasMap["dpi"] as? Number)?.toInt() ?: 300
        val bgColor = (canvasMap["backgroundColor"] as? Number)?.toLong() ?: 0xFFFFFFFFL
        val colorSpace = canvasMap["colorSpace"]?.toString() ?: "sRGB"

        if (canvasWidth <= 0 || canvasHeight <= 0) {
            throw CorruptDocumentException("Invalid canvas dimensions: ${canvasWidth}x${canvasHeight}")
        }
        val canvas = CanvasConfig(canvasWidth, canvasHeight, dpi, bgColor, colorSpace)

        // Root Group
        @Suppress("UNCHECKED_CAST")
        val rootGroupMap = map["rootGroup"] as? Map<String, Any?>
            ?: throw CorruptDocumentException("Missing rootGroup in document")
        val rootGroup = layerGroupFromMap(rootGroupMap)

        // Palettes
        @Suppress("UNCHECKED_CAST")
        val rawPalettes = (map["palettes"] as? List<*>) ?: emptyList<Any?>()
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

        // Gradients
        @Suppress("UNCHECKED_CAST")
        val rawGradients = (map["gradientPresets"] as? List<*>) ?: emptyList<Any?>()
        val gradientPresets = rawGradients.mapNotNull { g ->
            val gMap = g as? Map<String, Any?> ?: return@mapNotNull null
            val gId = gMap["id"]?.toString() ?: java.util.UUID.randomUUID().toString()
            val gName = gMap["name"]?.toString() ?: "Gradient"
            val gType = com.artstudio.color.GradientType.fromId(gMap["type"]?.toString() ?: "linear")
            val rawStops = (gMap["stops"] as? List<*>) ?: emptyList<Any?>()
            val stops = rawStops.mapNotNull { s ->
                val sMap = s as? Map<String, Any?> ?: return@mapNotNull null
                val pos = (sMap["position"] as? Number)?.toFloat() ?: return@mapNotNull null
                val colorHex = sMap["color"]?.toString() ?: return@mapNotNull null
                com.artstudio.color.GradientColorStop(pos, com.artstudio.color.ColorData.fromHex(colorHex))
            }.ifEmpty {
                listOf(
                    com.artstudio.color.GradientColorStop(0f, com.artstudio.color.ColorData.BLACK),
                    com.artstudio.color.GradientColorStop(1f, com.artstudio.color.ColorData.WHITE)
                )
            }
            val startX = (gMap["startX"] as? Number)?.toFloat() ?: 0f
            val startY = (gMap["startY"] as? Number)?.toFloat() ?: 0f
            val endX = (gMap["endX"] as? Number)?.toFloat() ?: 2048f
            val endY = (gMap["endY"] as? Number)?.toFloat() ?: 0f
            val centerX = (gMap["centerX"] as? Number)?.toFloat() ?: 1024f
            val centerY = (gMap["centerY"] as? Number)?.toFloat() ?: 1024f
            val radius = (gMap["radius"] as? Number)?.toFloat() ?: 1024f
            val tileMode = com.artstudio.color.GradientTileMode.fromId(gMap["tileMode"]?.toString() ?: "clamp")
            val linearSpace = (gMap["interpolateInLinearSpace"] as? Boolean) ?: true
            com.artstudio.color.GradientConfig(
                id = gId,
                name = gName,
                type = gType,
                stops = stops,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                tileMode = tileMode,
                interpolateInLinearSpace = linearSpace
            )
        }

        // References
        @Suppress("UNCHECKED_CAST")
        val rawRefs = (map["referenceImages"] as? List<*>) ?: emptyList<Any?>()
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

        // Animation
        @Suppress("UNCHECKED_CAST")
        val animMap = map["animation"] as? Map<String, Any?>
        val animation = animMap?.let { a ->
            val fps = (a["fps"] as? Number)?.toInt() ?: 12
            val totalFrames = (a["totalFrames"] as? Number)?.toInt() ?: 24
            val onionSkin = (a["onionSkinEnabled"] as? Boolean) ?: false
            val before = (a["onionSkinFramesBefore"] as? Number)?.toInt() ?: 1
            val after = (a["onionSkinFramesAfter"] as? Number)?.toInt() ?: 1
            val tintBefore = (a["onionSkinTintBefore"] as? Number)?.toLong() ?: 0xFFFF4500L
            val tintAfter = (a["onionSkinTintAfter"] as? Number)?.toLong() ?: 0xFF00BFFFL
            val opBefore = ((a["onionSkinOpacityBefore"] as? Number)?.toFloat() ?: 0.35f).coerceIn(0f, 1f)
            val opAfter = ((a["onionSkinOpacityAfter"] as? Number)?.toFloat() ?: 0.25f).coerceIn(0f, 1f)
            val showBefore = (a["onionSkinShowBefore"] as? Boolean) ?: true
            val showAfter = (a["onionSkinShowAfter"] as? Boolean) ?: true
            val tintMode = a["onionSkinTintMode"]?.toString() ?: "tinted"
            val falloff = a["onionSkinFalloff"]?.toString() ?: "linear"
            val suppressPlayback = (a["onionSkinSuppressDuringPlayback"] as? Boolean) ?: true
            val currentFrameIndex = (a["currentFrameIndex"] as? Number)?.toInt() ?: 0
            val staticLayerIds = (a["staticLayerIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val loopMode = a["loopMode"]?.toString() ?: "LOOP"
            val rawFrames = (a["frames"] as? List<*>) ?: emptyList<Any?>()
            val frames = rawFrames.mapNotNull { f ->
                (f as? Map<String, Any?>)?.let { com.artstudio.animation.AnimationFrame.fromMap(it) }
            }
            val rawTracks = (a["frameTracks"] as? List<*>) ?: emptyList<Any?>()
            val tracks = rawTracks.mapNotNull { t ->
                val tMap = t as? Map<String, Any?> ?: return@mapNotNull null
                val layerId = tMap["layerId"]?.toString() ?: return@mapNotNull null
                val active = ((tMap["activeFrames"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }) ?: emptyList()
                FrameTrackData(layerId, active)
            }
            AnimationTimelineConfig(
                fps = fps,
                totalFrames = totalFrames,
                frameTracks = tracks,
                onionSkinEnabled = onionSkin,
                onionSkinFramesBefore = before,
                onionSkinFramesAfter = after,
                onionSkinTintBefore = tintBefore,
                onionSkinTintAfter = tintAfter,
                onionSkinOpacityBefore = opBefore,
                onionSkinOpacityAfter = opAfter,
                onionSkinShowBefore = showBefore,
                onionSkinShowAfter = showAfter,
                onionSkinTintMode = tintMode,
                onionSkinFalloff = falloff,
                onionSkinSuppressDuringPlayback = suppressPlayback,
                frames = frames,
                currentFrameIndex = currentFrameIndex,
                staticLayerIds = staticLayerIds,
                loopMode = loopMode
            )
        }

        // AutoDraw Provenance
        @Suppress("UNCHECKED_CAST")
        val provMap = map["autoDrawProvenance"] as? Map<String, Any?>
        val autoDrawProvenance = provMap?.let { p ->
            val hash = p["sourceImageHash"]?.toString()
            val algo = p["algorithm"]?.toString() ?: "offline_morphological_v1"
            val low = (p["edgeThresholdLow"] as? Number)?.toFloat() ?: 50f
            val high = (p["edgeThresholdHigh"] as? Number)?.toFloat() ?: 150f
            val genLayers = ((p["generatedLayerIds"] as? List<*>)?.mapNotNull { it?.toString() }) ?: emptyList()
            val ts = (p["timestampMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val aiMap = p["aiAugmentation"] as? Map<String, Any?>
            val aiAugmentation = aiMap?.let { com.artstudio.ai.model.AiAugmentation.fromMap(it) }
            val stylePreset = p["stylePresetId"]?.toString()
            val groupId = p["autoDrawGroupId"]?.toString()

            val rawRegions = p["regions"] as? List<*>
            val regions = rawRegions?.mapNotNull { r ->
                val rMap = r as? Map<String, Any?> ?: return@mapNotNull null
                val rId = rMap["regionId"]?.toString() ?: return@mapNotNull null
                val rName = rMap["regionName"]?.toString() ?: rId
                val color = (rMap["colorRgb"] as? Number)?.toInt() ?: 0
                val role = rMap["semanticRole"]?.toString() ?: "BODY"
                val lIds = ((rMap["layerIds"] as? List<*>)?.mapNotNull { it?.toString() }) ?: emptyList()
                val pIds = ((rMap["pathIds"] as? List<*>)?.mapNotNull { it?.toString() }) ?: emptyList()
                val locked = (rMap["isLocked"] as? Boolean) ?: false
                com.artstudio.autodraw.integration.RegionProvenanceInfo(rId, rName, color, role, lIds, pIds, locked)
            } ?: emptyList()

            val rawOverrides = p["userOverrides"] as? List<*>
            val overrides = rawOverrides?.mapNotNull { u ->
                val uMap = u as? Map<String, Any?> ?: return@mapNotNull null
                val oId = uMap["overrideId"]?.toString() ?: java.util.UUID.randomUUID().toString()
                val rId = uMap["regionId"]?.toString() ?: return@mapNotNull null
                val typeStr = uMap["overrideType"]?.toString() ?: "CUSTOM"
                val type = runCatching { com.artstudio.autodraw.integration.UserOverrideType.valueOf(typeStr) }.getOrDefault(com.artstudio.autodraw.integration.UserOverrideType.CUSTOM)
                val desc = uMap["description"]?.toString() ?: ""
                val uTs = (uMap["timestampMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
                val lId = uMap["layerId"]?.toString() ?: ""
                val details = (uMap["details"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    if (k != null && v != null) k.toString() to v.toString() else null
                }?.toMap() ?: emptyMap()
                com.artstudio.autodraw.integration.UserOverrideRecord(oId, rId, type, desc, uTs, lId, details)
            } ?: emptyList()

            val layerRoles = (p["layerRoles"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                if (k != null && v != null) k.toString() to v.toString() else null
            }?.toMap() ?: emptyMap()

            val customParams = (p["customParameters"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                if (k != null && v != null) k.toString() to v.toString() else null
            }?.toMap() ?: emptyMap()

            AutoDrawProvenance(
                hash, algo, low, high, genLayers, ts, aiAugmentation,
                stylePreset, groupId, regions, overrides, layerRoles, customParams
            )
        }

        val selectedLayerId = map["selectedLayerId"]?.toString()

        @Suppress("UNCHECKED_CAST")
        val rawPanels = map["comicPanels"] as? List<*>
        val comicPanels = rawPanels?.mapNotNull { p ->
            (p as? Map<String, Any?>)?.let { comicPanelFromMap(it) }
        } ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val rawBubbles = map["speechBubbles"] as? List<*>
        val speechBubbles = rawBubbles?.mapNotNull { b ->
            (b as? Map<String, Any?>)?.let { speechBubbleFromMap(it) }
        } ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val rawTones = map["screentones"] as? List<*>
        val screentones = rawTones?.mapNotNull { t ->
            (t as? Map<String, Any?>)?.let { screentoneFromMap(it) }
        } ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val guidesMap = map["comicPageGuides"] as? Map<String, Any?>
        val comicPageGuides = guidesMap?.let { comicPageGuidesFromMap(it) }

        @Suppress("UNCHECKED_CAST")
        val schedMap = map["drawingSchedule"] as? Map<String, Any?>
        val drawingSchedule = schedMap?.let { drawingScheduleFromMap(it) }

        return ArtDocument(
            schemaVersion = schemaVer,
            appVersion = appVersion,
            id = id,
            title = title,
            canvas = canvas,
            rootGroup = rootGroup,
            selectedLayerId = selectedLayerId,
            palettes = palettes,
            gradientPresets = gradientPresets,
            referenceImages = referenceImages,
            animation = animation,
            autoDrawProvenance = autoDrawProvenance,
            comicPanels = comicPanels,
            speechBubbles = speechBubbles,
            screentones = screentones,
            comicPageGuides = comicPageGuides,
            drawingSchedule = drawingSchedule,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun layerGroupToMap(group: LayerNode.LayerGroup): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map["type"] = "group"
        map["id"] = group.id
        map["name"] = group.name
        map["visible"] = group.visible
        map["opacity"] = group.opacity
        map["blendMode"] = group.blendMode.key
        map["transform"] = transformToMap(group.transform)
        map["isLocked"] = group.isLocked
        map["isClippingMask"] = group.isClippingMask
        map["isAlphaLocked"] = group.isAlphaLocked
        map["mask"] = maskToMap(group.mask)
        map["isCollapsed"] = group.isCollapsed
        map["children"] = group.children.map { layerNodeToMap(it) }
        return map
    }

    private fun layerNodeToMap(node: LayerNode): Map<String, Any?> {
        return when (node) {
            is LayerNode.LayerGroup -> layerGroupToMap(node)
            is LayerNode.RasterLayer -> mapOf(
                "type" to "raster",
                "id" to node.id,
                "name" to node.name,
                "visible" to node.visible,
                "opacity" to node.opacity,
                "blendMode" to node.blendMode.key,
                "transform" to transformToMap(node.transform),
                "isLocked" to node.isLocked,
                "isClippingMask" to node.isClippingMask,
                "isAlphaLocked" to node.isAlphaLocked,
                "mask" to maskToMap(node.mask),
                "assetPath" to node.assetPath,
                "width" to node.width,
                "height" to node.height,
                "offsetX" to node.offsetX,
                "offsetY" to node.offsetY
            )
            is LayerNode.VectorPathLayer -> mapOf(
                "type" to "vector",
                "id" to node.id,
                "name" to node.name,
                "visible" to node.visible,
                "opacity" to node.opacity,
                "blendMode" to node.blendMode.key,
                "transform" to transformToMap(node.transform),
                "isLocked" to node.isLocked,
                "isClippingMask" to node.isClippingMask,
                "isAlphaLocked" to node.isAlphaLocked,
                "mask" to maskToMap(node.mask),
                "paths" to node.paths.map { path ->
                    mapOf(
                        "id" to path.id,
                        "brushId" to path.brushId,
                        "strokeColor" to path.strokeColor,
                        "strokeWidth" to path.strokeWidth,
                        "points" to path.points.map { pt ->
                            mapOf(
                                "x" to pt.x,
                                "y" to pt.y,
                                "p" to pt.pressure,
                                "tx" to pt.tiltX,
                                "ty" to pt.tiltY,
                                "t" to pt.timestampMs
                            )
                        }
                    )
                },
                "vectorObjects" to node.vectorObjects.map { vectorObjectToMap(it) }
            )
            is LayerNode.TextLayer -> mapOf(
                "type" to "text",
                "id" to node.id,
                "name" to node.name,
                "visible" to node.visible,
                "opacity" to node.opacity,
                "blendMode" to node.blendMode.key,
                "transform" to transformToMap(node.transform),
                "isLocked" to node.isLocked,
                "isClippingMask" to node.isClippingMask,
                "isAlphaLocked" to node.isAlphaLocked,
                "mask" to maskToMap(node.mask),
                "textObject" to textObjectToMap(node.textObject)
            )
            is LayerNode.AdjustmentLayer -> mapOf(
                "type" to "adjustment",
                "id" to node.id,
                "name" to node.name,
                "visible" to node.visible,
                "opacity" to node.opacity,
                "blendMode" to node.blendMode.key,
                "transform" to transformToMap(node.transform),
                "isLocked" to node.isLocked,
                "isClippingMask" to node.isClippingMask,
                "isAlphaLocked" to node.isAlphaLocked,
                "mask" to maskToMap(node.mask),
                "adjustment" to node.adjustment.toMap()
            )
        }
    }

    private fun layerGroupFromMap(map: Map<String, Any?>): LayerNode.LayerGroup {
        val id = map["id"]?.toString() ?: throw CorruptDocumentException("Missing group id")
        val name = map["name"]?.toString() ?: "Group"
        val visible = (map["visible"] as? Boolean) ?: true
        val opacity = (map["opacity"] as? Number)?.toFloat() ?: 1.0f
        val blendMode = BlendModeType.fromKey(map["blendMode"]?.toString())
        val isLocked = (map["isLocked"] as? Boolean) ?: false
        val isClipping = (map["isClippingMask"] as? Boolean) ?: false
        val isAlphaLocked = (map["isAlphaLocked"] as? Boolean) ?: false
        @Suppress("UNCHECKED_CAST")
        val mask = maskFromMap(map["mask"] as? Map<String, Any?>)
        val isCollapsed = (map["isCollapsed"] as? Boolean) ?: false

        @Suppress("UNCHECKED_CAST")
        val transformMap = map["transform"] as? Map<String, Any?>
        val transform = transformFromMap(transformMap)

        @Suppress("UNCHECKED_CAST")
        val rawChildren = (map["children"] as? List<*>) ?: emptyList<Any?>()
        val children = rawChildren.mapNotNull { child ->
            val cMap = child as? Map<String, Any?> ?: return@mapNotNull null
            layerNodeFromMap(cMap)
        }

        return LayerNode.LayerGroup(
            id = id,
            name = name,
            visible = visible,
            opacity = opacity.coerceIn(0f, 1f),
            blendMode = blendMode,
            transform = transform,
            isLocked = isLocked,
            isClippingMask = isClipping,
            isAlphaLocked = isAlphaLocked,
            mask = mask,
            children = children,
            isCollapsed = isCollapsed
        )
    }

    private fun layerNodeFromMap(map: Map<String, Any?>): LayerNode {
        val type = map["type"]?.toString() ?: "raster"
        val isAlphaLocked = (map["isAlphaLocked"] as? Boolean) ?: false
        @Suppress("UNCHECKED_CAST")
        val mask = maskFromMap(map["mask"] as? Map<String, Any?>)

        return when (type) {
            "group" -> layerGroupFromMap(map)
            "vector" -> {
                val id = map["id"]?.toString() ?: throw CorruptDocumentException("Missing layer id")
                val name = map["name"]?.toString() ?: "Vector Layer"
                val visible = (map["visible"] as? Boolean) ?: true
                val opacity = (map["opacity"] as? Number)?.toFloat() ?: 1.0f
                val blendMode = BlendModeType.fromKey(map["blendMode"]?.toString())
                val isLocked = (map["isLocked"] as? Boolean) ?: false
                val isClipping = (map["isClippingMask"] as? Boolean) ?: false

                @Suppress("UNCHECKED_CAST")
                val transformMap = map["transform"] as? Map<String, Any?>
                val transform = transformFromMap(transformMap)

                @Suppress("UNCHECKED_CAST")
                val rawPaths = (map["paths"] as? List<*>) ?: emptyList<Any?>()
                val paths = rawPaths.mapNotNull { p ->
                    val pMap = p as? Map<String, Any?> ?: return@mapNotNull null
                    val pathId = pMap["id"]?.toString() ?: return@mapNotNull null
                    val brushId = pMap["brushId"]?.toString() ?: "inking_pen"
                    val color = (pMap["strokeColor"] as? Number)?.toLong() ?: 0xFF000000L
                    val width = (pMap["strokeWidth"] as? Number)?.toFloat() ?: 4.0f

                    @Suppress("UNCHECKED_CAST")
                    val rawPts = (pMap["points"] as? List<*>) ?: emptyList<Any?>()
                    val pts = rawPts.mapNotNull { pt ->
                        val ptMap = pt as? Map<String, Any?> ?: return@mapNotNull null
                        val x = (ptMap["x"] as? Number)?.toFloat() ?: return@mapNotNull null
                        val y = (ptMap["y"] as? Number)?.toFloat() ?: return@mapNotNull null
                        val pr = (ptMap["p"] as? Number)?.toFloat() ?: 0.5f
                        val tx = (ptMap["tx"] as? Number)?.toFloat() ?: 0f
                        val ty = (ptMap["ty"] as? Number)?.toFloat() ?: 0f
                        val t = (ptMap["t"] as? Number)?.toLong() ?: 0L
                        StrokePointData(x, y, pr, tx, ty, t)
                    }
                    VectorPathData(pathId, pts, brushId, color, width)
                }

                @Suppress("UNCHECKED_CAST")
                val rawVecObjs = (map["vectorObjects"] as? List<*>) ?: emptyList<Any?>()
                val vectorObjects = rawVecObjs.mapNotNull { vo ->
                    val voMap = vo as? Map<String, Any?> ?: return@mapNotNull null
                    vectorObjectFromMap(voMap)
                }

                LayerNode.VectorPathLayer(
                    id = id,
                    name = name,
                    visible = visible,
                    opacity = opacity.coerceIn(0f, 1f),
                    blendMode = blendMode,
                    transform = transform,
                    isLocked = isLocked,
                    isClippingMask = isClipping,
                    isAlphaLocked = isAlphaLocked,
                    mask = mask,
                    paths = paths,
                    vectorObjects = vectorObjects
                )
            }
            "text" -> {
                val id = map["id"]?.toString() ?: throw CorruptDocumentException("Missing layer id")
                val name = map["name"]?.toString() ?: "Text Layer"
                val visible = (map["visible"] as? Boolean) ?: true
                val opacity = (map["opacity"] as? Number)?.toFloat() ?: 1.0f
                val blendMode = BlendModeType.fromKey(map["blendMode"]?.toString())
                val isLocked = (map["isLocked"] as? Boolean) ?: false
                val isClipping = (map["isClippingMask"] as? Boolean) ?: false

                @Suppress("UNCHECKED_CAST")
                val transformMap = map["transform"] as? Map<String, Any?>
                val transform = transformFromMap(transformMap)

                @Suppress("UNCHECKED_CAST")
                val textObjMap = map["textObject"] as? Map<String, Any?>
                val textObject = textObjMap?.let { textObjectFromMap(it) }
                    ?: com.artstudio.text.TextObject(text = name)

                LayerNode.TextLayer(
                    id = id,
                    name = name,
                    visible = visible,
                    opacity = opacity.coerceIn(0f, 1f),
                    blendMode = blendMode,
                    transform = transform,
                    isLocked = isLocked,
                    isClippingMask = isClipping,
                    isAlphaLocked = isAlphaLocked,
                    mask = mask,
                    textObject = textObject
                )
            }
            "adjustment" -> {
                val id = map["id"]?.toString() ?: throw CorruptDocumentException("Missing layer id")
                val name = map["name"]?.toString() ?: "Adjustment Layer"
                val visible = (map["visible"] as? Boolean) ?: true
                val opacity = (map["opacity"] as? Number)?.toFloat() ?: 1.0f
                val blendMode = BlendModeType.fromKey(map["blendMode"]?.toString())
                val isLocked = (map["isLocked"] as? Boolean) ?: false
                val isClipping = (map["isClippingMask"] as? Boolean) ?: false

                @Suppress("UNCHECKED_CAST")
                val transformMap = map["transform"] as? Map<String, Any?>
                val transform = transformFromMap(transformMap)

                @Suppress("UNCHECKED_CAST")
                val adjMap = map["adjustment"] as? Map<String, Any?>
                val adjustment = adjMap?.let { com.artstudio.adjustment.ColorAdjustment.fromMap(it) }
                    ?: com.artstudio.adjustment.BrightnessContrastAdjustment()

                LayerNode.AdjustmentLayer(
                    id = id,
                    name = name,
                    visible = visible,
                    opacity = opacity.coerceIn(0f, 1f),
                    blendMode = blendMode,
                    transform = transform,
                    isLocked = isLocked,
                    isClippingMask = isClipping,
                    isAlphaLocked = isAlphaLocked,
                    mask = mask,
                    adjustment = adjustment
                )
            }
            else -> { // Raster
                val id = map["id"]?.toString() ?: throw CorruptDocumentException("Missing layer id")
                val name = map["name"]?.toString() ?: "Raster Layer"
                val visible = (map["visible"] as? Boolean) ?: true
                val opacity = (map["opacity"] as? Number)?.toFloat() ?: 1.0f
                val blendMode = BlendModeType.fromKey(map["blendMode"]?.toString())
                val isLocked = (map["isLocked"] as? Boolean) ?: false
                val isClipping = (map["isClippingMask"] as? Boolean) ?: false

                @Suppress("UNCHECKED_CAST")
                val transformMap = map["transform"] as? Map<String, Any?>
                val transform = transformFromMap(transformMap)

                val assetPath = map["assetPath"]?.toString()
                val width = (map["width"] as? Number)?.toInt() ?: 2048
                val height = (map["height"] as? Number)?.toInt() ?: 2048
                val offX = (map["offsetX"] as? Number)?.toInt() ?: 0
                val offY = (map["offsetY"] as? Number)?.toInt() ?: 0

                LayerNode.RasterLayer(
                    id = id,
                    name = name,
                    visible = visible,
                    opacity = opacity.coerceIn(0f, 1f),
                    blendMode = blendMode,
                    transform = transform,
                    isLocked = isLocked,
                    isClippingMask = isClipping,
                    isAlphaLocked = isAlphaLocked,
                    mask = mask,
                    assetPath = assetPath,
                    width = width,
                    height = height,
                    offsetX = offX,
                    offsetY = offY
                )
            }
        }
    }

    private fun textObjectToMap(obj: com.artstudio.text.TextObject): Map<String, Any?> {
        return mapOf(
            "id" to obj.id,
            "text" to obj.text,
            "x" to obj.x,
            "y" to obj.y,
            "width" to obj.width,
            "height" to obj.height,
            "fontFamily" to obj.fontFamily,
            "fontSize" to obj.fontSize,
            "fontStyle" to obj.fontStyle.id,
            "alignment" to obj.alignment.id,
            "textColor" to obj.textColor,
            "letterSpacing" to obj.letterSpacing,
            "lineSpacingMultiplier" to obj.lineSpacingMultiplier,
            "isUnderline" to obj.isUnderline,
            "isStrikeThrough" to obj.isStrikeThrough,
            "isRasterized" to obj.isRasterized
        )
    }

    private fun textObjectFromMap(map: Map<String, Any?>): com.artstudio.text.TextObject {
        val id = map["id"]?.toString() ?: java.util.UUID.randomUUID().toString()
        val text = map["text"]?.toString() ?: ""
        val x = (map["x"] as? Number)?.toFloat() ?: 0f
        val y = (map["y"] as? Number)?.toFloat() ?: 0f
        val width = (map["width"] as? Number)?.toFloat() ?: 0f
        val height = (map["height"] as? Number)?.toFloat() ?: 0f
        val fontFamily = map["fontFamily"]?.toString() ?: "sans-serif"
        val fontSize = (map["fontSize"] as? Number)?.toFloat() ?: 36f
        val fontStyle = com.artstudio.text.TextFontStyle.fromId(map["fontStyle"]?.toString())
        val alignment = com.artstudio.text.TextAlignment.fromId(map["alignment"]?.toString())
        val textColor = (map["textColor"] as? Number)?.toLong() ?: 0xFF000000L
        val letterSpacing = (map["letterSpacing"] as? Number)?.toFloat() ?: 0f
        val lineSpacingMultiplier = (map["lineSpacingMultiplier"] as? Number)?.toFloat() ?: 1.2f
        val isUnderline = (map["isUnderline"] as? Boolean) ?: false
        val isStrikeThrough = (map["isStrikeThrough"] as? Boolean) ?: false
        val isRasterized = (map["isRasterized"] as? Boolean) ?: false

        return com.artstudio.text.TextObject(
            id = id,
            text = text,
            x = x,
            y = y,
            width = width,
            height = height,
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontStyle = fontStyle,
            alignment = alignment,
            textColor = textColor,
            letterSpacing = letterSpacing,
            lineSpacingMultiplier = lineSpacingMultiplier,
            isUnderline = isUnderline,
            isStrikeThrough = isStrikeThrough,
            isRasterized = isRasterized
        )
    }

    private fun transformToMap(t: LayerTransform): Map<String, Any?> {
        return mapOf(
            "tx" to t.translationX,
            "ty" to t.translationY,
            "sx" to t.scaleX,
            "sy" to t.scaleY,
            "rot" to t.rotationDegrees,
            "flipH" to t.flipHorizontal,
            "flipV" to t.flipVertical,
            "aspectLocked" to t.isAspectRatioLocked,
            "interpolation" to t.interpolation.name
        )
    }

    private fun transformFromMap(map: Map<String, Any?>?): LayerTransform {
        if (map == null) return LayerTransform()
        val tx = (map["tx"] as? Number)?.toFloat() ?: 0f
        val ty = (map["ty"] as? Number)?.toFloat() ?: 0f
        val sx = (map["sx"] as? Number)?.toFloat() ?: 1f
        val sy = (map["sy"] as? Number)?.toFloat() ?: 1f
        val rot = (map["rot"] as? Number)?.toFloat() ?: 0f
        val flipH = map["flipH"] as? Boolean ?: false
        val flipV = map["flipV"] as? Boolean ?: false
        val aspectLocked = map["aspectLocked"] as? Boolean ?: true
        val interpStr = map["interpolation"] as? String ?: InterpolationMode.BILINEAR.name
        val interpolation = try {
            InterpolationMode.valueOf(interpStr)
        } catch (_: Exception) {
            InterpolationMode.BILINEAR
        }
        return LayerTransform(tx, ty, sx, sy, rot, flipH, flipV, aspectLocked, interpolation)
    }

    private fun maskToMap(mask: LayerMask?): Map<String, Any?>? {
        if (mask == null) return null
        return mapOf(
            "id" to mask.id,
            "enabled" to mask.enabled,
            "inverted" to mask.inverted,
            "assetPath" to mask.assetPath,
            "width" to mask.width,
            "height" to mask.height,
            "transform" to transformToMap(mask.transform),
            "linkTransformWithLayer" to mask.linkTransformWithLayer
        )
    }

    private fun maskFromMap(map: Map<String, Any?>?): LayerMask? {
        if (map == null) return null
        val id = map["id"]?.toString() ?: java.util.UUID.randomUUID().toString()
        val enabled = (map["enabled"] as? Boolean) ?: true
        val inverted = (map["inverted"] as? Boolean) ?: false
        val assetPath = map["assetPath"]?.toString()
        val width = (map["width"] as? Number)?.toInt() ?: 2048
        val height = (map["height"] as? Number)?.toInt() ?: 2048
        @Suppress("UNCHECKED_CAST")
        val transform = transformFromMap(map["transform"] as? Map<String, Any?>)
        val link = (map["linkTransformWithLayer"] as? Boolean) ?: true
        return LayerMask(
            id = id,
            enabled = enabled,
            inverted = inverted,
            assetPath = assetPath,
            width = width,
            height = height,
            transform = transform,
            linkTransformWithLayer = link
        )
    }

    private fun vectorObjectToMap(obj: com.artstudio.vector.VectorObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map["id"] = obj.id
        map["name"] = obj.name
        map["shapeType"] = obj.shapeType.id
        map["isClosed"] = obj.isClosed

        // Style
        map["style"] = mapOf(
            "strokeColor" to obj.style.strokeColor,
            "strokeWidth" to obj.style.strokeWidth,
            "strokeCap" to obj.style.strokeCap.id,
            "strokeJoin" to obj.style.strokeJoin.id,
            "strokeMiterLimit" to obj.style.strokeMiterLimit,
            "fillColor" to obj.style.fillColor,
            "fillRule" to obj.style.fillRule.id,
            "opacity" to obj.style.opacity
        )

        // Rect data
        obj.rectData?.let { r ->
            map["rectData"] = mapOf(
                "x" to r.x,
                "y" to r.y,
                "width" to r.width,
                "height" to r.height,
                "rx" to r.rx,
                "ry" to r.ry
            )
        }

        // Ellipse data
        obj.ellipseData?.let { el ->
            map["ellipseData"] = mapOf(
                "cx" to el.cx,
                "cy" to el.cy,
                "rx" to el.rx,
                "ry" to el.ry
            )
        }

        // Anchors
        if (obj.anchors.isNotEmpty()) {
            map["anchors"] = obj.anchors.map { a ->
                val aMap = mutableMapOf<String, Any?>(
                    "x" to a.x,
                    "y" to a.y,
                    "isSmooth" to a.isSmooth
                )
                a.handleInX?.let { aMap["hInX"] = it }
                a.handleInY?.let { aMap["hInY"] = it }
                a.handleOutX?.let { aMap["hOutX"] = it }
                a.handleOutY?.let { aMap["hOutY"] = it }
                aMap
            }
        }

        // Segments
        if (obj.segments.isNotEmpty()) {
            map["segments"] = obj.segments.map { seg ->
                when (seg) {
                    is com.artstudio.vector.VectorSegment.MoveTo -> mapOf("type" to "M", "x" to seg.x, "y" to seg.y)
                    is com.artstudio.vector.VectorSegment.LineTo -> mapOf("type" to "L", "x" to seg.x, "y" to seg.y)
                    is com.artstudio.vector.VectorSegment.QuadTo -> mapOf("type" to "Q", "x1" to seg.x1, "y1" to seg.y1, "x" to seg.x, "y" to seg.y)
                    is com.artstudio.vector.VectorSegment.CubicTo -> mapOf("type" to "C", "x1" to seg.x1, "y1" to seg.y1, "x2" to seg.x2, "y2" to seg.y2, "x" to seg.x, "y" to seg.y)
                    is com.artstudio.vector.VectorSegment.Close -> mapOf("type" to "Z")
                }
            }
        }

        // Metadata
        if (obj.metadata.isNotEmpty()) {
            map["metadata"] = obj.metadata
        }

        return map
    }

    private fun vectorObjectFromMap(map: Map<String, Any?>): com.artstudio.vector.VectorObject {
        val id = map["id"]?.toString() ?: java.util.UUID.randomUUID().toString()
        val name = map["name"]?.toString() ?: "Vector Object"
        val shapeType = com.artstudio.vector.VectorShapeType.fromId(map["shapeType"]?.toString() ?: "bezier_path")
        val isClosed = (map["isClosed"] as? Boolean) ?: false

        @Suppress("UNCHECKED_CAST")
        val styleMap = map["style"] as? Map<String, Any?>
        val strokeColor = (styleMap?.get("strokeColor") as? Number)?.toLong()
        val strokeWidth = (styleMap?.get("strokeWidth") as? Number)?.toFloat() ?: 2.0f
        val strokeCap = com.artstudio.vector.StrokeCap.fromId(styleMap?.get("strokeCap")?.toString() ?: "round")
        val strokeJoin = com.artstudio.vector.StrokeJoin.fromId(styleMap?.get("strokeJoin")?.toString() ?: "round")
        val strokeMiter = (styleMap?.get("strokeMiterLimit") as? Number)?.toFloat() ?: 4.0f
        val fillColor = (styleMap?.get("fillColor") as? Number)?.toLong()
        val fillRule = com.artstudio.vector.FillRule.fromId(styleMap?.get("fillRule")?.toString() ?: "nonzero")
        val opacity = (styleMap?.get("opacity") as? Number)?.toFloat() ?: 1.0f

        val style = com.artstudio.vector.VectorStyle(
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            strokeCap = strokeCap,
            strokeJoin = strokeJoin,
            strokeMiterLimit = strokeMiter,
            fillColor = fillColor,
            fillRule = fillRule,
            opacity = opacity.coerceIn(0f, 1f)
        )

        @Suppress("UNCHECKED_CAST")
        val rMap = map["rectData"] as? Map<String, Any?>
        val rectData = rMap?.let {
            val rx = (it["rx"] as? Number)?.toFloat() ?: 0f
            val ry = (it["ry"] as? Number)?.toFloat() ?: rx
            com.artstudio.vector.VectorRectData(
                x = (it["x"] as? Number)?.toFloat() ?: 0f,
                y = (it["y"] as? Number)?.toFloat() ?: 0f,
                width = (it["width"] as? Number)?.toFloat() ?: 0f,
                height = (it["height"] as? Number)?.toFloat() ?: 0f,
                rx = rx,
                ry = ry
            )
        }

        @Suppress("UNCHECKED_CAST")
        val elMap = map["ellipseData"] as? Map<String, Any?>
        val ellipseData = elMap?.let {
            com.artstudio.vector.VectorEllipseData(
                cx = (it["cx"] as? Number)?.toFloat() ?: 0f,
                cy = (it["cy"] as? Number)?.toFloat() ?: 0f,
                rx = (it["rx"] as? Number)?.toFloat() ?: 0f,
                ry = (it["ry"] as? Number)?.toFloat() ?: 0f
            )
        }

        @Suppress("UNCHECKED_CAST")
        val rawAnchors = (map["anchors"] as? List<*>) ?: emptyList<Any?>()
        val anchors = rawAnchors.mapNotNull { a ->
            val aMap = a as? Map<String, Any?> ?: return@mapNotNull null
            val x = (aMap["x"] as? Number)?.toFloat() ?: return@mapNotNull null
            val y = (aMap["y"] as? Number)?.toFloat() ?: return@mapNotNull null
            val isSmooth = (aMap["isSmooth"] as? Boolean) ?: false
            val hInX = (aMap["hInX"] as? Number)?.toFloat()
            val hInY = (aMap["hInY"] as? Number)?.toFloat()
            val hOutX = (aMap["hOutX"] as? Number)?.toFloat()
            val hOutY = (aMap["hOutY"] as? Number)?.toFloat()
            com.artstudio.vector.VectorAnchor(x, y, hInX, hInY, hOutX, hOutY, isSmooth)
        }

        @Suppress("UNCHECKED_CAST")
        val rawSegs = (map["segments"] as? List<*>) ?: emptyList<Any?>()
        val segments = rawSegs.mapNotNull { s ->
            val sMap = s as? Map<String, Any?> ?: return@mapNotNull null
            val sType = sMap["type"]?.toString() ?: return@mapNotNull null
            when (sType) {
                "M" -> com.artstudio.vector.VectorSegment.MoveTo(
                    (sMap["x"] as? Number)?.toFloat() ?: 0f,
                    (sMap["y"] as? Number)?.toFloat() ?: 0f
                )
                "L" -> com.artstudio.vector.VectorSegment.LineTo(
                    (sMap["x"] as? Number)?.toFloat() ?: 0f,
                    (sMap["y"] as? Number)?.toFloat() ?: 0f
                )
                "Q" -> com.artstudio.vector.VectorSegment.QuadTo(
                    (sMap["x1"] as? Number)?.toFloat() ?: 0f,
                    (sMap["y1"] as? Number)?.toFloat() ?: 0f,
                    (sMap["x"] as? Number)?.toFloat() ?: 0f,
                    (sMap["y"] as? Number)?.toFloat() ?: 0f
                )
                "C" -> com.artstudio.vector.VectorSegment.CubicTo(
                    (sMap["x1"] as? Number)?.toFloat() ?: 0f,
                    (sMap["y1"] as? Number)?.toFloat() ?: 0f,
                    (sMap["x2"] as? Number)?.toFloat() ?: 0f,
                    (sMap["y2"] as? Number)?.toFloat() ?: 0f,
                    (sMap["x"] as? Number)?.toFloat() ?: 0f,
                    (sMap["y"] as? Number)?.toFloat() ?: 0f
                )
                "Z" -> com.artstudio.vector.VectorSegment.Close
                else -> null
            }
        }

        @Suppress("UNCHECKED_CAST")
        val metaMap = (map["metadata"] as? Map<String, Any?>)?.mapValues { it.value?.toString() ?: "" } ?: emptyMap()

        return com.artstudio.vector.VectorObject(
            id = id,
            name = name,
            shapeType = shapeType,
            anchors = anchors,
            segments = segments,
            rectData = rectData,
            ellipseData = ellipseData,
            isClosed = isClosed,
            style = style,
            metadata = metaMap
        )
    }

    private fun comicPanelToMap(panel: com.artstudio.comic.ComicPanel): Map<String, Any?> {
        return mapOf(
            "id" to panel.id,
            "name" to panel.name,
            "vertices" to panel.vertices.map { mapOf("x" to it.x, "y" to it.y) },
            "strokeWidth" to panel.strokeWidth,
            "strokeColor" to panel.strokeColor,
            "fillColor" to panel.fillColor,
            "cornerRadius" to panel.cornerRadius,
            "innerMargin" to panel.innerMargin,
            "clipContent" to panel.clipContent
        )
    }

    private fun comicPanelFromMap(map: Map<String, Any?>): com.artstudio.comic.ComicPanel? {
        val id = map["id"]?.toString() ?: return null
        val name = map["name"]?.toString() ?: "Panel"
        val rawVerts = (map["vertices"] as? List<*>) ?: return null
        val vertices = rawVerts.mapNotNull { v ->
            val vMap = v as? Map<String, Any?> ?: return@mapNotNull null
            val x = (vMap["x"] as? Number)?.toFloat() ?: return@mapNotNull null
            val y = (vMap["y"] as? Number)?.toFloat() ?: return@mapNotNull null
            com.artstudio.comic.Point2D(x, y)
        }
        if (vertices.size < 3) return null

        val strokeWidth = (map["strokeWidth"] as? Number)?.toFloat() ?: 4f
        val strokeColor = (map["strokeColor"] as? Number)?.toLong() ?: 0xFF000000L
        val fillColor = (map["fillColor"] as? Number)?.toLong() ?: 0xFFFFFFFFL
        val cornerRadius = (map["cornerRadius"] as? Number)?.toFloat() ?: 0f
        val innerMargin = (map["innerMargin"] as? Number)?.toFloat() ?: 8f
        val clipContent = (map["clipContent"] as? Boolean) ?: true

        return com.artstudio.comic.ComicPanel(
            id = id,
            name = name,
            vertices = vertices,
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            fillColor = fillColor,
            cornerRadius = cornerRadius,
            innerMargin = innerMargin,
            clipContent = clipContent
        )
    }

    private fun speechBubbleToMap(bubble: com.artstudio.comic.SpeechBubble): Map<String, Any?> {
        return mapOf(
            "id" to bubble.id,
            "name" to bubble.name,
            "bubbleType" to bubble.bubbleType.id,
            "x" to bubble.x,
            "y" to bubble.y,
            "width" to bubble.width,
            "height" to bubble.height,
            "tailType" to bubble.tailType.id,
            "tailTipX" to bubble.tailTipX,
            "tailTipY" to bubble.tailTipY,
            "tailBaseAngleDeg" to bubble.tailBaseAngleDeg,
            "tailBaseWidth" to bubble.tailBaseWidth,
            "strokeWidth" to bubble.strokeWidth,
            "strokeColor" to bubble.strokeColor,
            "fillColor" to bubble.fillColor,
            "cornerRadius" to bubble.cornerRadius,
            "burstSpikeCount" to bubble.burstSpikeCount,
            "burstSpikeDepth" to bubble.burstSpikeDepth,
            "cloudPuffCount" to bubble.cloudPuffCount,
            "text" to bubble.text,
            "fontFamily" to bubble.fontFamily,
            "fontSize" to bubble.fontSize,
            "textColor" to bubble.textColor
        )
    }

    private fun speechBubbleFromMap(map: Map<String, Any?>): com.artstudio.comic.SpeechBubble? {
        val id = map["id"]?.toString() ?: return null
        val name = map["name"]?.toString() ?: "Speech Bubble"
        val bTypeId = map["bubbleType"]?.toString() ?: "speech_oval"
        val bubbleType = com.artstudio.comic.SpeechBubbleType.fromId(bTypeId)
        val x = (map["x"] as? Number)?.toFloat() ?: 0f
        val y = (map["y"] as? Number)?.toFloat() ?: 0f
        val width = (map["width"] as? Number)?.toFloat() ?: 200f
        val height = (map["height"] as? Number)?.toFloat() ?: 120f

        val tTypeId = map["tailType"]?.toString() ?: "straight_pointer"
        val tailType = com.artstudio.comic.BubbleTailType.fromId(tTypeId)
        val tailTipX = (map["tailTipX"] as? Number)?.toFloat() ?: (x + width / 2f)
        val tailTipY = (map["tailTipY"] as? Number)?.toFloat() ?: (y + height + 40f)
        val tailBaseAngleDeg = (map["tailBaseAngleDeg"] as? Number)?.toFloat() ?: 90f
        val tailBaseWidth = (map["tailBaseWidth"] as? Number)?.toFloat() ?: 28f

        val strokeWidth = (map["strokeWidth"] as? Number)?.toFloat() ?: 3.5f
        val strokeColor = (map["strokeColor"] as? Number)?.toLong() ?: 0xFF000000L
        val fillColor = (map["fillColor"] as? Number)?.toLong() ?: 0xFFFFFFFFL
        val cornerRadius = (map["cornerRadius"] as? Number)?.toFloat() ?: 16f
        val burstSpikeCount = (map["burstSpikeCount"] as? Number)?.toInt() ?: 16
        val burstSpikeDepth = (map["burstSpikeDepth"] as? Number)?.toFloat() ?: 0.25f
        val cloudPuffCount = (map["cloudPuffCount"] as? Number)?.toInt() ?: 12

        val text = map["text"]?.toString() ?: ""
        val fontFamily = map["fontFamily"]?.toString() ?: "sans-serif"
        val fontSize = (map["fontSize"] as? Number)?.toFloat() ?: 22f
        val textColor = (map["textColor"] as? Number)?.toLong() ?: 0xFF000000L

        return com.artstudio.comic.SpeechBubble(
            id = id,
            name = name,
            bubbleType = bubbleType,
            x = x,
            y = y,
            width = width,
            height = height,
            tailType = tailType,
            tailTipX = tailTipX,
            tailTipY = tailTipY,
            tailBaseAngleDeg = tailBaseAngleDeg,
            tailBaseWidth = tailBaseWidth,
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            fillColor = fillColor,
            cornerRadius = cornerRadius,
            burstSpikeCount = burstSpikeCount,
            burstSpikeDepth = burstSpikeDepth,
            cloudPuffCount = cloudPuffCount,
            text = text,
            fontFamily = fontFamily,
            fontSize = fontSize,
            textColor = textColor
        )
    }

    private fun screentoneToMap(tone: com.artstudio.comic.ScreentoneConfig): Map<String, Any?> {
        return mapOf(
            "id" to tone.id,
            "name" to tone.name,
            "pattern" to tone.pattern.id,
            "frequency" to tone.frequency,
            "density" to tone.density,
            "angleDeg" to tone.angleDeg,
            "foregroundColor" to tone.foregroundColor,
            "backgroundColor" to tone.backgroundColor,
            "softness" to tone.softness
        )
    }

    private fun screentoneFromMap(map: Map<String, Any?>): com.artstudio.comic.ScreentoneConfig? {
        val id = map["id"]?.toString() ?: return null
        val name = map["name"]?.toString() ?: "Screentone"
        val patId = map["pattern"]?.toString() ?: "dot"
        val pattern = com.artstudio.comic.ScreentonePattern.fromId(patId)
        val frequency = (map["frequency"] as? Number)?.toFloat() ?: 16f
        val density = (map["density"] as? Number)?.toFloat() ?: 0.25f
        val angleDeg = (map["angleDeg"] as? Number)?.toFloat() ?: 45f
        val fg = (map["foregroundColor"] as? Number)?.toLong() ?: 0xFF000000L
        val bg = (map["backgroundColor"] as? Number)?.toLong() ?: 0x00000000L
        val softness = (map["softness"] as? Number)?.toFloat() ?: 0.5f

        return com.artstudio.comic.ScreentoneConfig(
            id = id,
            name = name,
            pattern = pattern,
            frequency = frequency,
            density = density,
            angleDeg = angleDeg,
            foregroundColor = fg,
            backgroundColor = bg,
            softness = softness
        )
    }

    private fun comicPageGuidesToMap(guides: com.artstudio.comic.ComicPageGuides): Map<String, Any?> {
        return mapOf(
            "enabled" to guides.enabled,
            "bleedMargin" to guides.bleedMargin,
            "trimMargin" to guides.trimMargin,
            "safeAreaMargin" to guides.safeAreaMargin,
            "showGutterGuides" to guides.showGutterGuides,
            "guideColor" to guides.guideColor
        )
    }

    private fun comicPageGuidesFromMap(map: Map<String, Any?>): com.artstudio.comic.ComicPageGuides {
        val enabled = (map["enabled"] as? Boolean) ?: true
        val bleed = (map["bleedMargin"] as? Number)?.toFloat() ?: 0f
        val trim = (map["trimMargin"] as? Number)?.toFloat() ?: 40f
        val safe = (map["safeAreaMargin"] as? Number)?.toFloat() ?: 120f
        val gutters = (map["showGutterGuides"] as? Boolean) ?: true
        val color = (map["guideColor"] as? Number)?.toLong() ?: 0x803B82F6L

        return com.artstudio.comic.ComicPageGuides(
            enabled = enabled,
            bleedMargin = bleed,
            trimMargin = trim,
            safeAreaMargin = safe,
            showGutterGuides = gutters,
            guideColor = color
        )
    }

    private fun drawingScheduleToMap(sched: com.artstudio.autodraw.schedule.DrawingSchedule): Map<String, Any?> {
        return mapOf(
            "id" to sched.id,
            "documentId" to sched.documentId,
            "config" to mapOf(
                "orderingStrategy" to sched.config.orderingStrategy.name,
                "nominalPenSpeedPxPerSec" to sched.config.nominalPenSpeedPxPerSec,
                "penLiftSpeedPxPerSec" to sched.config.penLiftSpeedPxPerSec,
                "singlePenCursorMode" to sched.config.singlePenCursorMode,
                "allowDirectionReversal" to sched.config.allowDirectionReversal,
                "allowWorkerPreprocessing" to sched.config.allowWorkerPreprocessing,
                "userPriorityOverrides" to sched.config.userPriorityOverrides,
                "randomizeOrder" to sched.config.randomizeOrder,
                "seed" to sched.config.seed
            ),
            "entries" to sched.entries.map { e ->
                val eMap = mutableMapOf<String, Any?>(
                    "id" to e.id,
                    "pathId" to e.pathId,
                    "layerId" to e.layerId,
                    "sequenceIndex" to e.sequenceIndex,
                    "direction" to e.direction.name,
                    "brushParams" to mapOf(
                        "sizePx" to e.brushParams.sizePx,
                        "opacity" to e.brushParams.opacity,
                        "colorArgb" to e.brushParams.colorArgb,
                        "brushPresetId" to e.brushParams.brushPresetId,
                        "pressureProfile" to e.brushParams.pressureProfile.name
                    ),
                    "timing" to mapOf(
                        "estimatedDurationMs" to e.timing.estimatedDurationMs,
                        "delayBeforeMs" to e.timing.delayBeforeMs,
                        "startTimestampMs" to e.timing.startTimestampMs,
                        "speedFactor" to e.timing.speedFactor
                    ),
                    "dependencies" to e.dependencies.toList(),
                    "startPoint" to mapOf("x" to e.startPoint.x, "y" to e.startPoint.y),
                    "endPoint" to mapOf("x" to e.endPoint.x, "y" to e.endPoint.y),
                    "pathLengthPx" to e.pathLengthPx,
                    "isContour" to e.isContour,
                    "isClosed" to e.isClosed,
                    "semanticRole" to e.semanticRole.name
                )
                e.regionId?.let { eMap["regionId"] = it }
                e.anatomicalTag?.let { eMap["anatomicalTag"] = it.name }
                if (e.waypoints.isNotEmpty()) {
                    eMap["waypoints"] = e.waypoints.map { mapOf("x" to it.x, "y" to it.y) }
                }
                eMap
            },
            "totalDurationMs" to sched.totalDurationMs,
            "totalPenTravelPx" to sched.totalPenTravelPx,
            "totalDrawingLengthPx" to sched.totalDrawingLengthPx,
            "scheduleHash" to sched.scheduleHash,
            "createdAtMs" to sched.createdAtMs,
            "disclaimer" to sched.disclaimer
        )
    }

    private fun drawingScheduleFromMap(map: Map<String, Any?>): com.artstudio.autodraw.schedule.DrawingSchedule? {
        val id = map["id"]?.toString() ?: return null
        val docId = map["documentId"]?.toString()
        val cMap = map["config"] as? Map<String, Any?>
        val config = if (cMap != null) {
            val strategyName = cMap["orderingStrategy"]?.toString() ?: com.artstudio.autodraw.schedule.ScheduleOrderingStrategy.CONTOUR_FIRST.name
            val strategy = try {
                com.artstudio.autodraw.schedule.ScheduleOrderingStrategy.valueOf(strategyName)
            } catch (_: Exception) {
                com.artstudio.autodraw.schedule.ScheduleOrderingStrategy.CONTOUR_FIRST
            }
            val speed = (cMap["nominalPenSpeedPxPerSec"] as? Number)?.toFloat() ?: 400.0f
            val liftSpeed = (cMap["penLiftSpeedPxPerSec"] as? Number)?.toFloat() ?: 1200.0f
            val singlePen = (cMap["singlePenCursorMode"] as? Boolean) ?: true
            val allowRev = (cMap["allowDirectionReversal"] as? Boolean) ?: true
            val allowWorker = (cMap["allowWorkerPreprocessing"] as? Boolean) ?: true
            val rawPrio = (cMap["userPriorityOverrides"] as? Map<*, *>) ?: emptyMap<Any, Any>()
            val prioMap = rawPrio.mapNotNull { (k, v) ->
                val ks = k?.toString() ?: return@mapNotNull null
                val vn = (v as? Number)?.toInt() ?: return@mapNotNull null
                ks to vn
            }.toMap()
            val rand = (cMap["randomizeOrder"] as? Boolean) ?: false
            val seed = (cMap["seed"] as? Number)?.toLong() ?: 42L
            com.artstudio.autodraw.schedule.DrawingScheduleConfig(
                orderingStrategy = strategy,
                nominalPenSpeedPxPerSec = speed,
                penLiftSpeedPxPerSec = liftSpeed,
                singlePenCursorMode = singlePen,
                allowDirectionReversal = allowRev,
                allowWorkerPreprocessing = allowWorker,
                userPriorityOverrides = prioMap,
                randomizeOrder = rand,
                seed = seed
            )
        } else com.artstudio.autodraw.schedule.DrawingScheduleConfig()

        val rawEntries = (map["entries"] as? List<*>) ?: emptyList<Any?>()
        val entries = rawEntries.mapNotNull { eItem ->
            val e = eItem as? Map<String, Any?> ?: return@mapNotNull null
            val eId = e["id"]?.toString() ?: return@mapNotNull null
            val pathId = e["pathId"]?.toString() ?: return@mapNotNull null
            val layerId = e["layerId"]?.toString() ?: return@mapNotNull null
            val seqIdx = (e["sequenceIndex"] as? Number)?.toInt() ?: 0
            val dirStr = e["direction"]?.toString() ?: com.artstudio.autodraw.schedule.StrokeDirection.FORWARD.name
            val direction = try {
                com.artstudio.autodraw.schedule.StrokeDirection.valueOf(dirStr)
            } catch (_: Exception) {
                com.artstudio.autodraw.schedule.StrokeDirection.FORWARD
            }

            val bpMap = e["brushParams"] as? Map<String, Any?>
            val bp = if (bpMap != null) {
                val size = (bpMap["sizePx"] as? Number)?.toFloat() ?: 2.0f
                val op = (bpMap["opacity"] as? Number)?.toFloat() ?: 1.0f
                val color = (bpMap["colorArgb"] as? Number)?.toLong() ?: 0xFF000000L
                val preset = bpMap["brushPresetId"]?.toString() ?: "pen_fineliner"
                val profStr = bpMap["pressureProfile"]?.toString() ?: com.artstudio.autodraw.schedule.PressureProfileType.TAPER_ENDS.name
                val profile = try {
                    com.artstudio.autodraw.schedule.PressureProfileType.valueOf(profStr)
                } catch (_: Exception) {
                    com.artstudio.autodraw.schedule.PressureProfileType.TAPER_ENDS
                }
                com.artstudio.autodraw.schedule.ScheduledBrushParams(size, op, color, preset, profile)
            } else com.artstudio.autodraw.schedule.ScheduledBrushParams()

            val tMap = e["timing"] as? Map<String, Any?>
            val timing = if (tMap != null) {
                val dur = (tMap["estimatedDurationMs"] as? Number)?.toLong() ?: 100L
                val delay = (tMap["delayBeforeMs"] as? Number)?.toLong() ?: 0L
                val start = (tMap["startTimestampMs"] as? Number)?.toLong() ?: 0L
                val speed = (tMap["speedFactor"] as? Number)?.toFloat() ?: 1.0f
                com.artstudio.autodraw.schedule.ScheduleTiming(dur, delay, start, speed)
            } else com.artstudio.autodraw.schedule.ScheduleTiming(100L)

            val deps = (e["dependencies"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()

            val spMap = e["startPoint"] as? Map<String, Any?>
            val sp = com.artstudio.cv.CvPoint(
                (spMap?.get("x") as? Number)?.toFloat() ?: 0f,
                (spMap?.get("y") as? Number)?.toFloat() ?: 0f
            )

            val epMap = e["endPoint"] as? Map<String, Any?>
            val ep = com.artstudio.cv.CvPoint(
                (epMap?.get("x") as? Number)?.toFloat() ?: 0f,
                (epMap?.get("y") as? Number)?.toFloat() ?: 0f
            )

            val pathLen = (e["pathLengthPx"] as? Number)?.toFloat() ?: 1.0f
            val isCont = (e["isContour"] as? Boolean) ?: false
            val isCls = (e["isClosed"] as? Boolean) ?: false
            val semStr = e["semanticRole"]?.toString() ?: com.artstudio.autodraw.schedule.StrokeSemanticRole.OUTLINE.name
            val semRole = try {
                com.artstudio.autodraw.schedule.StrokeSemanticRole.valueOf(semStr)
            } catch (_: Exception) {
                com.artstudio.autodraw.schedule.StrokeSemanticRole.OUTLINE
            }
            val tagStr = e["anatomicalTag"]?.toString()
            val anatTag = tagStr?.let {
                try {
                    com.artstudio.autodraw.schedule.AnatomicalTag.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            }

            val rawWaypoints = (e["waypoints"] as? List<*>) ?: emptyList<Any?>()
            val waypoints = rawWaypoints.mapNotNull { wp ->
                val wpMap = wp as? Map<String, Any?> ?: return@mapNotNull null
                val wx = (wpMap["x"] as? Number)?.toFloat() ?: return@mapNotNull null
                val wy = (wpMap["y"] as? Number)?.toFloat() ?: return@mapNotNull null
                com.artstudio.cv.CvPoint(wx, wy)
            }.ifEmpty { listOf(sp, ep) }

            com.artstudio.autodraw.schedule.DrawingScheduleEntry(
                id = eId,
                pathId = pathId,
                regionId = e["regionId"]?.toString(),
                layerId = layerId,
                sequenceIndex = seqIdx,
                direction = direction,
                brushParams = bp,
                timing = timing,
                dependencies = deps,
                startPoint = sp,
                endPoint = ep,
                waypoints = waypoints,
                pathLengthPx = pathLen,
                isContour = isCont,
                isClosed = isCls,
                semanticRole = semRole,
                anatomicalTag = anatTag
            )
        }

        val totalDur = (map["totalDurationMs"] as? Number)?.toLong() ?: 0L
        val totalPen = (map["totalPenTravelPx"] as? Number)?.toFloat() ?: 0f
        val totalDraw = (map["totalDrawingLengthPx"] as? Number)?.toFloat() ?: 0f
        val hash = map["scheduleHash"]?.toString() ?: com.artstudio.autodraw.schedule.DrawingSchedule.computeScheduleHash(entries)
        val created = (map["createdAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val disclaimer = map["disclaimer"]?.toString() ?: "This synthesized reconstruction schedule represents an optimized machine drawing plan; it does not claim to reproduce the original artist's real human stroke history."

        return com.artstudio.autodraw.schedule.DrawingSchedule(
            id = id,
            documentId = docId,
            entries = entries,
            config = config,
            totalDurationMs = totalDur,
            totalPenTravelPx = totalPen,
            totalDrawingLengthPx = totalDraw,
            scheduleHash = hash,
            createdAtMs = created,
            disclaimer = disclaimer
        )
    }
}
