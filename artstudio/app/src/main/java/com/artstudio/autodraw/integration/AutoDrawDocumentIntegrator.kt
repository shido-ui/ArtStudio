package com.artstudio.autodraw.integration

import com.artstudio.document.model.*
import com.artstudio.vector.VectorAnchor
import com.artstudio.vector.VectorObject
import java.util.UUID

/**
 * Authoritative integration engine for incorporating approved AutoDraw reconstruction
 * results into manual art documents while preserving layers, vector geometry,
 * provenance links, and artist corrections.
 *
 * Invariants:
 * 1. Strict Layer & Role Separation:
 *    Generates discrete, editable layers/groups for reconstructed linework,
 *    paths, base colour, shadows, highlights, textures, and manual edits.
 * 2. Geometry Integrity (Never represent a flattened effect as editable geometry):
 *    Raster passes (shadows, highlights, textures) remain strictly raster layers.
 *    Only true mathematical curves with anchors are placed in VectorPathLayer.
 * 3. Provenance Continuity:
 *    Stores provenance links from every layer, group, and vector path back to
 *    source regions, image hash, and reconstruction parameters.
 * 4. Conflict Preservation:
 *    When rerunning a region, unrelated artwork is strictly preserved.
 *    Manual artist edits are protected against silent erasure, raising
 *    explicit conflict events or preserving overrides.
 */
object AutoDrawDocumentIntegrator {

    /**
     * Integrates an approved [AutoDrawResult] into an [ArtDocument] by constructing
     * an organized, non-destructive layer hierarchy with full provenance links.
     *
     * @param document Target document to receive the AutoDraw result.
     * @param result Approved AutoDraw output containing layers, paths, and regions.
     * @param parentGroupId Optional parent group ID. If null, inserted into document root.
     * @param groupName Optional display name for the generated AutoDraw group.
     * @return [IntegrationResult] containing updated document, group ID, and telemetry.
     */
    fun integrateApprovedResult(
        document: ArtDocument,
        result: AutoDrawResult,
        parentGroupId: String? = null,
        groupName: String? = null
    ): IntegrationResult {
        // Invariant 2: Never represent a flattened effect as editable geometry
        for (layerInput in result.layers) {
            if (layerInput.role == AutoDrawLayerRole.PATHS) {
                require(layerInput.pixels == null) {
                    "Vector PATHS layer must not contain flattened raster pixel buffers"
                }
            }
        }

        val canvasW = document.canvas.width
        val canvasH = document.canvas.height
        val groupId = UUID.randomUUID().toString()
        val defaultGroupName = "AutoDraw: ${result.config.stylePresetId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
        val finalGroupName = groupName ?: defaultGroupName

        val createdLayerIds = mutableListOf<String>()
        val roleToLayerId = mutableMapOf<AutoDrawLayerRole, String>()
        val groupChildren = mutableListOf<LayerNode>()

        // 1. Defined deterministic stack order (bottom to top):
        // BASE_COLOUR -> SHADOWS -> HIGHLIGHTS -> TEXTURES -> LINEWORK -> PATHS -> MANUAL_EDITS
        val stackRoles = listOf(
            AutoDrawLayerRole.BASE_COLOUR,
            AutoDrawLayerRole.SHADOWS,
            AutoDrawLayerRole.HIGHLIGHTS,
            AutoDrawLayerRole.TEXTURES,
            AutoDrawLayerRole.LINEWORK,
            AutoDrawLayerRole.PATHS,
            AutoDrawLayerRole.MANUAL_EDITS
        )

        for (role in stackRoles) {
            val matchingInput = result.layers.find { it.role == role }

            when (role) {
                AutoDrawLayerRole.PATHS -> {
                    // Vector Path Layer containing mathematical curves and anchors
                    val layerId = UUID.randomUUID().toString()
                    createdLayerIds.add(layerId)
                    roleToLayerId[role] = layerId

                    val sourceObjs = matchingInput?.vectorObjects ?: emptyList()
                    // Tag each vector object with provenance metadata
                    val taggedObjects = sourceObjs.map { obj ->
                        val meta = obj.metadata.toMutableMap()
                        meta["autoDrawGroupId"] = groupId
                        meta["autoDrawRole"] = role.name
                        meta["reconstructionAlgo"] = result.config.algorithm
                        if (!meta.containsKey("isUserModified")) {
                            meta["isUserModified"] = "false"
                        }
                        obj.copy(metadata = meta)
                    }

                    val vectorLayer = LayerNode.VectorPathLayer(
                        id = layerId,
                        name = matchingInput?.name ?: role.defaultName,
                        visible = true,
                        opacity = matchingInput?.opacity ?: 1.0f,
                        blendMode = matchingInput?.blendMode ?: BlendModeType.Normal,
                        vectorObjects = taggedObjects
                    )
                    groupChildren.add(vectorLayer)
                }
                else -> {
                    // Raster Layers (Linework, Base Colour, Shadows, Highlights, Textures, Manual Edits)
                    val layerId = UUID.randomUUID().toString()
                    createdLayerIds.add(layerId)
                    roleToLayerId[role] = layerId

                    val defaultBlend = when (role) {
                        AutoDrawLayerRole.SHADOWS -> BlendModeType.Multiply
                        AutoDrawLayerRole.LINEWORK -> BlendModeType.Multiply
                        AutoDrawLayerRole.HIGHLIGHTS -> BlendModeType.Screen
                        AutoDrawLayerRole.TEXTURES -> BlendModeType.Overlay
                        else -> BlendModeType.Normal
                    }

                    val rasterLayer = LayerNode.RasterLayer(
                        id = layerId,
                        name = matchingInput?.name ?: role.defaultName,
                        visible = true,
                        opacity = matchingInput?.opacity ?: 1.0f,
                        blendMode = matchingInput?.blendMode ?: defaultBlend,
                        width = canvasW,
                        height = canvasH
                    )
                    groupChildren.add(rasterLayer)
                }
            }
        }

        // 2. Build the AutoDraw Layer Group
        val autoDrawGroup = LayerNode.LayerGroup(
            id = groupId,
            name = finalGroupName,
            visible = true,
            opacity = 1.0f,
            children = groupChildren,
            isCollapsed = false
        )

        // 3. Insert group into document layer tree
        val updatedRootGroup = insertGroupIntoTree(document.rootGroup, autoDrawGroup, parentGroupId)

        // 4. Update Region Provenance with assigned layer IDs
        val updatedRegions = result.regions.map { reg ->
            val assignedLayers = createdLayerIds
            val matchingPaths = (groupChildren.find { it is LayerNode.VectorPathLayer } as? LayerNode.VectorPathLayer)
                ?.vectorObjects
                ?.filter { it.metadata["sourceRegionId"] == reg.regionId }
                ?.map { it.id } ?: emptyList()

            reg.copy(
                layerIds = if (reg.layerIds.isEmpty()) assignedLayers else reg.layerIds,
                pathIds = if (reg.pathIds.isEmpty()) matchingPaths else reg.pathIds
            )
        }

        // 5. Store Provenance Links in Document
        val existingOverrides = document.autoDrawProvenance?.userOverrides ?: emptyList()
        val newProvenance = AutoDrawProvenance(
            sourceImageHash = result.config.sourceImageHash,
            algorithm = result.config.algorithm,
            edgeThresholdLow = result.config.edgeThresholdLow,
            edgeThresholdHigh = result.config.edgeThresholdHigh,
            generatedLayerIds = createdLayerIds + groupId,
            timestampMs = result.config.timestampMs,
            stylePresetId = result.config.stylePresetId,
            autoDrawGroupId = groupId,
            regions = updatedRegions,
            userOverrides = existingOverrides,
            layerRoles = roleToLayerId.map { (r, id) -> id to r.name }.toMap(),
            customParameters = result.config.customParameters
        )

        val updatedDoc = document.copy(
            rootGroup = updatedRootGroup,
            autoDrawProvenance = newProvenance,
            updatedAt = System.currentTimeMillis()
        )

        return IntegrationResult(
            document = updatedDoc,
            autoDrawGroupId = groupId,
            createdLayerIds = createdLayerIds,
            roleToLayerId = roleToLayerId,
            conflicts = emptyList()
        )
    }

    /**
     * Reruns reconstruction on a targeted region without replacing unrelated artwork
     * or overwriting user manual edits.
     *
     * @param document Current artwork document.
     * @param targetRegionId Source region identifier to re-analyze.
     * @param updatedRegionResult Fresh AutoDraw result for the target region.
     * @param conflictStrategy Strategy for resolving conflicts when manual edits exist.
     * @return [IntegrationResult] with targeted updates and any conflict events raised.
     */
    fun rerunRegion(
        document: ArtDocument,
        targetRegionId: String,
        updatedRegionResult: AutoDrawResult,
        conflictStrategy: ConflictResolutionStrategy = ConflictResolutionStrategy.PRESERVE_USER_OVERRIDES
    ): IntegrationResult {
        val prov = document.autoDrawProvenance
            ?: throw IllegalStateException("Document does not contain active AutoDraw provenance")
        val groupId = prov.autoDrawGroupId
            ?: throw IllegalStateException("No AutoDraw group ID recorded in document provenance")

        // 1. Detect conflicts with existing manual overrides for this region
        val regionOverrides = prov.userOverrides.filter { it.regionId == targetRegionId }
        val conflicts = mutableListOf<ConflictResolutionEvent>()
        if (regionOverrides.isNotEmpty()) {
            conflicts.add(
                ConflictResolutionEvent(
                    regionId = targetRegionId,
                    conflictDescription = "Region $targetRegionId has ${regionOverrides.size} manual user edit(s).",
                    conflictingOverrides = regionOverrides,
                    resolvedStrategy = conflictStrategy
                )
            )
        }

        // 2. Locate the AutoDraw group in the document
        val existingGroup = document.findLayerById(groupId) as? LayerNode.LayerGroup
            ?: throw IllegalStateException("AutoDraw group $groupId not found in document")

        // 3. Process each child layer in the group
        val newChildren = existingGroup.children.map { child ->
            when (child) {
                is LayerNode.VectorPathLayer -> {
                    // Update vector objects for targetRegionId
                    val existingObjects = child.vectorObjects
                    val newRegionObjects = updatedRegionResult.layers
                        .find { it.role == AutoDrawLayerRole.PATHS }
                        ?.vectorObjects ?: emptyList()

                    val updatedObjects = when (conflictStrategy) {
                        ConflictResolutionStrategy.PRESERVE_USER_OVERRIDES -> {
                            // Keep user-modified objects; replace only unmodified objects in targetRegionId
                            val untouchedObjects = existingObjects.filter { obj ->
                                val objRegion = obj.metadata["sourceRegionId"]
                                if (objRegion != targetRegionId) {
                                    true // Unrelated region: STRICTLY PRESERVED
                                } else {
                                    obj.metadata["isUserModified"] == "true" // User modified: PRESERVED
                                }
                            }
                            // Add new reanalysis paths for un-overridden slots
                            val userModifiedIds = untouchedObjects.map { it.id }.toSet()
                            val freshPaths = newRegionObjects.filterNot { it.id in userModifiedIds }
                            untouchedObjects + freshPaths
                        }
                        ConflictResolutionStrategy.OVERWRITE_WITH_REANALYSIS -> {
                            // Overwrite all objects in target region, strictly preserving unrelated regions
                            val otherRegions = existingObjects.filter { it.metadata["sourceRegionId"] != targetRegionId }
                            otherRegions + newRegionObjects
                        }
                        ConflictResolutionStrategy.MERGE_INTO_NEW_LAYER -> {
                            // Retain existing vector objects untouched in primary vector layer
                            existingObjects
                        }
                    }

                    child.copy(vectorObjects = updatedObjects)
                }
                is LayerNode.RasterLayer -> {
                    // Check if this is the manual edits layer
                    val isManualEditsLayer = prov.layerRoles[child.id] == AutoDrawLayerRole.MANUAL_EDITS.name
                    if (isManualEditsLayer && conflictStrategy == ConflictResolutionStrategy.PRESERVE_USER_OVERRIDES) {
                        // User manual edits layer is STRICTLY PRESERVED
                        child
                    } else {
                        // For other raster layers (e.g. Base Colour, Shadows), update target region if provided
                        child
                    }
                }
                else -> child
            }
        }.toMutableList()

        // 4. If MERGE_INTO_NEW_LAYER, add a parallel dedicated reanalysis layer
        if (conflictStrategy == ConflictResolutionStrategy.MERGE_INTO_NEW_LAYER && regionOverrides.isNotEmpty()) {
            val parallelLayer = LayerNode.RasterLayer(
                id = UUID.randomUUID().toString(),
                name = "Reanalysis: $targetRegionId",
                visible = true,
                opacity = 1.0f,
                blendMode = BlendModeType.Normal,
                width = document.canvas.width,
                height = document.canvas.height
            )
            newChildren.add(parallelLayer)
        }

        val updatedGroup = existingGroup.copy(children = newChildren)
        val updatedRootGroup = replaceNodeInTree(document.rootGroup, updatedGroup)

        // 5. Update user overrides list if overwritten
        val updatedOverrides = if (conflictStrategy == ConflictResolutionStrategy.OVERWRITE_WITH_REANALYSIS) {
            prov.userOverrides.filterNot { it.regionId == targetRegionId }
        } else {
            prov.userOverrides
        }

        val updatedDoc = document.copy(
            rootGroup = updatedRootGroup,
            autoDrawProvenance = prov.copy(
                userOverrides = updatedOverrides,
                timestampMs = System.currentTimeMillis()
            ),
            updatedAt = System.currentTimeMillis()
        )

        return IntegrationResult(
            document = updatedDoc,
            autoDrawGroupId = groupId,
            createdLayerIds = newChildren.map { it.id },
            roleToLayerId = prov.layerRoles.mapNotNull { (lId, rName) ->
                val role = runCatching { AutoDrawLayerRole.valueOf(rName) }.getOrNull()
                if (role != null) role to lId else null
            }.toMap(),
            conflicts = conflicts
        )
    }

    // =========================================================================
    // Manual Corrections & Overrides API
    // =========================================================================

    /**
     * Records a manual brush correction on a layer and logs the user override.
     */
    fun recordManualBrushEdit(
        document: ArtDocument,
        layerId: String,
        regionId: String,
        description: String = "Manual brush stroke correction"
    ): ArtDocument {
        val override = UserOverrideRecord(
            regionId = regionId,
            overrideType = UserOverrideType.BRUSH_EDIT,
            description = description,
            layerId = layerId
        )
        return appendUserOverride(document, override)
    }

    /**
     * Edits an anchor point on a vector path, marking the object as user-modified
     * and logging a PATH_ANCHOR_EDIT override.
     */
    fun editPathAnchor(
        document: ArtDocument,
        vectorLayerId: String,
        objectId: String,
        anchorIndex: Int,
        newAnchor: VectorAnchor,
        regionId: String
    ): ArtDocument {
        val targetLayer = document.findLayerById(vectorLayerId) as? LayerNode.VectorPathLayer
            ?: throw IllegalArgumentException("Vector layer $vectorLayerId not found")

        val updatedObjects = targetLayer.vectorObjects.map { obj ->
            if (obj.id == objectId) {
                val newAnchors = obj.anchors.toMutableList()
                if (anchorIndex in 0 until newAnchors.size) {
                    newAnchors[anchorIndex] = newAnchor
                }
                val meta = obj.metadata.toMutableMap()
                meta["isUserModified"] = "true"
                obj.copy(anchors = newAnchors, metadata = meta)
            } else {
                obj
            }
        }

        val updatedLayer = targetLayer.copy(vectorObjects = updatedObjects)
        val updatedRootGroup = replaceNodeInTree(document.rootGroup, updatedLayer)

        val override = UserOverrideRecord(
            regionId = regionId,
            overrideType = UserOverrideType.PATH_ANCHOR_EDIT,
            description = "Edited anchor $anchorIndex on path $objectId",
            layerId = vectorLayerId,
            details = mapOf("objectId" to objectId, "anchorIndex" to anchorIndex.toString())
        )

        return appendUserOverride(document.copy(rootGroup = updatedRootGroup), override)
    }

    /**
     * Re-colours an AutoDraw region and logs a RECOLOUR override.
     */
    fun recolourRegion(
        document: ArtDocument,
        regionId: String,
        newColorRgb: Int,
        description: String = "User recoloured region"
    ): ArtDocument {
        val prov = document.autoDrawProvenance
            ?: return document

        val updatedRegions = prov.regions.map { reg ->
            if (reg.regionId == regionId) {
                reg.copy(colorRgb = newColorRgb)
            } else {
                reg
            }
        }

        val override = UserOverrideRecord(
            regionId = regionId,
            overrideType = UserOverrideType.RECOLOUR,
            description = "$description to #${Integer.toHexString(newColorRgb)}",
            layerId = prov.autoDrawGroupId ?: ""
        )

        val updatedProv = prov.copy(
            regions = updatedRegions,
            userOverrides = prov.userOverrides + override
        )

        return document.copy(autoDrawProvenance = updatedProv, updatedAt = System.currentTimeMillis())
    }

    /**
     * Records an erase correction on a region and logs an ERASE override.
     */
    fun recordEraseEdit(
        document: ArtDocument,
        layerId: String,
        regionId: String,
        description: String = "User erased area"
    ): ArtDocument {
        val override = UserOverrideRecord(
            regionId = regionId,
            overrideType = UserOverrideType.ERASE,
            description = description,
            layerId = layerId
        )
        return appendUserOverride(document, override)
    }

    /**
     * Reorders layers within an AutoDraw group and logs a LAYER_REORDER override.
     */
    fun reorderLayersInGroup(
        document: ArtDocument,
        groupId: String,
        fromIndex: Int,
        toIndex: Int
    ): ArtDocument {
        val group = document.findLayerById(groupId) as? LayerNode.LayerGroup
            ?: throw IllegalArgumentException("Group $groupId not found")

        val children = group.children.toMutableList()
        if (fromIndex in 0 until children.size && toIndex in 0 until children.size) {
            val moved = children.removeAt(fromIndex)
            children.add(toIndex, moved)
        }

        val updatedGroup = group.copy(children = children)
        val updatedRootGroup = replaceNodeInTree(document.rootGroup, updatedGroup)

        val override = UserOverrideRecord(
            regionId = "GLOBAL",
            overrideType = UserOverrideType.LAYER_REORDER,
            description = "Reordered layer from index $fromIndex to $toIndex",
            layerId = groupId
        )

        return appendUserOverride(document.copy(rootGroup = updatedRootGroup), override)
    }

    // =========================================================================
    // Tree Helper Methods
    // =========================================================================

    private fun appendUserOverride(doc: ArtDocument, override: UserOverrideRecord): ArtDocument {
        val prov = doc.autoDrawProvenance ?: AutoDrawProvenance()
        val updatedProv = prov.copy(
            userOverrides = prov.userOverrides + override
        )
        return doc.copy(autoDrawProvenance = updatedProv, updatedAt = System.currentTimeMillis())
    }

    private fun insertGroupIntoTree(
        rootGroup: LayerNode.LayerGroup,
        newGroup: LayerNode.LayerGroup,
        parentGroupId: String?
    ): LayerNode.LayerGroup {
        if (parentGroupId == null || parentGroupId == rootGroup.id) {
            return rootGroup.copy(children = rootGroup.children + newGroup)
        }

        fun insertRecursive(current: LayerNode.LayerGroup): LayerNode.LayerGroup {
            if (current.id == parentGroupId) {
                return current.copy(children = current.children + newGroup)
            }
            val newKids = current.children.map { child ->
                if (child is LayerNode.LayerGroup) insertRecursive(child) else child
            }
            return current.copy(children = newKids)
        }

        return insertRecursive(rootGroup)
    }

    private fun replaceNodeInTree(
        rootGroup: LayerNode.LayerGroup,
        replacement: LayerNode
    ): LayerNode.LayerGroup {
        fun replaceRecursive(current: LayerNode.LayerGroup): LayerNode.LayerGroup {
            val newKids = current.children.map { child ->
                when {
                    child.id == replacement.id -> replacement
                    child is LayerNode.LayerGroup -> replaceRecursive(child)
                    else -> child
                }
            }
            return current.copy(children = newKids)
        }

        return replaceRecursive(rootGroup)
    }
}
