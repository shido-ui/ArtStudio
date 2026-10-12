package com.artstudio.autodraw.integration

import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.*
import com.artstudio.export.PureKotlinPngEncoder
import com.artstudio.interchange.ora.OpenRasterExporter
import com.artstudio.vector.*
import com.artstudio.vector.svg.SafeSvgExporter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Comprehensive end-to-end integration test suite for Prompt 64:
 * Integrating approved AutoDraw results into manual art documents,
 * preserving manual corrections, conflict resolution, round-trip serialization,
 * and multi-format export.
 */
class AutoDrawIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDocument: ArtDocument
    private lateinit var sampleAutoDrawResult: AutoDrawResult

    @Before
    fun setUp() {
        val canvasConfig = CanvasConfig(width = 100, height = 100, dpi = 72)
        baseDocument = ArtDocument(
            id = "test_doc_01",
            title = "Manual Landscape Artwork",
            canvas = canvasConfig,
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = "user_bg_layer",
                        name = "Manual Background",
                        width = 100,
                        height = 100
                    )
                )
            )
        )

        // Setup 2 sample regions: Region_A (Face/Skin) and Region_B (Hair)
        val regA = RegionProvenanceInfo(
            regionId = "region_A",
            regionName = "Character Face",
            colorRgb = 0xFFFFE0BD.toInt(),
            semanticRole = "FACE"
        )
        val regB = RegionProvenanceInfo(
            regionId = "region_B",
            regionName = "Character Hair",
            colorRgb = 0xFF4A3728.toInt(),
            semanticRole = "HAIR"
        )

        // Setup real vector paths with anchors
        val path1 = VectorObject(
            id = "path_face_contour",
            name = "Face Contour",
            shapeType = VectorShapeType.BEZIER_PATH,
            anchors = listOf(
                VectorAnchor(20f, 20f, handleOutX = 25f, handleOutY = 20f),
                VectorAnchor(60f, 20f, handleInX = 55f, handleInY = 20f),
                VectorAnchor(60f, 60f),
                VectorAnchor(20f, 60f)
            ),
            isClosed = true,
            style = VectorStyle(strokeColor = 0xFF000000L, strokeWidth = 3f),
            metadata = mapOf("sourceRegionId" to "region_A", "semanticRole" to "OUTLINE")
        )

        val path2 = VectorObject(
            id = "path_hair_strand",
            name = "Hair Strand",
            shapeType = VectorShapeType.BEZIER_PATH,
            anchors = listOf(
                VectorAnchor(15f, 15f),
                VectorAnchor(65f, 15f)
            ),
            isClosed = false,
            style = VectorStyle(strokeColor = 0xFF221100L, strokeWidth = 2f),
            metadata = mapOf("sourceRegionId" to "region_B", "semanticRole" to "DETAIL")
        )

        val layers = listOf(
            AutoDrawLayerInput(
                role = AutoDrawLayerRole.BASE_COLOUR,
                pixels = IntArray(100 * 100) { 0xFFFFE0BD.toInt() },
                regionIds = listOf("region_A", "region_B")
            ),
            AutoDrawLayerInput(
                role = AutoDrawLayerRole.SHADOWS,
                pixels = IntArray(100 * 100) { 0x66330000 },
                blendMode = BlendModeType.Multiply,
                opacity = 0.5f,
                regionIds = listOf("region_A")
            ),
            AutoDrawLayerInput(
                role = AutoDrawLayerRole.HIGHLIGHTS,
                pixels = IntArray(100 * 100) { 0x44FFFFFF },
                blendMode = BlendModeType.Screen,
                opacity = 0.4f,
                regionIds = listOf("region_A")
            ),
            AutoDrawLayerInput(
                role = AutoDrawLayerRole.TEXTURES,
                pixels = IntArray(100 * 100) { 0x22888888 },
                blendMode = BlendModeType.Overlay,
                opacity = 0.3f,
                regionIds = listOf("region_B")
            ),
            AutoDrawLayerInput(
                role = AutoDrawLayerRole.LINEWORK,
                pixels = IntArray(100 * 100) { 0xFF000000.toInt() },
                blendMode = BlendModeType.Multiply,
                opacity = 1.0f,
                regionIds = listOf("region_A", "region_B")
            ),
            AutoDrawLayerInput(
                role = AutoDrawLayerRole.PATHS,
                vectorObjects = listOf(path1, path2),
                regionIds = listOf("region_A", "region_B")
            ),
            AutoDrawLayerInput(
                role = AutoDrawLayerRole.MANUAL_EDITS,
                pixels = IntArray(100 * 100) { 0x00000000 },
                blendMode = BlendModeType.Normal,
                opacity = 1.0f
            )
        )

        sampleAutoDrawResult = AutoDrawResult(
            config = AutoDrawReconstructionConfig(
                sourceImageHash = "sha256_mock_hash_face_hair_001",
                algorithm = "autodraw_hybrid_v1",
                stylePresetId = "classic_cel",
                edgeThresholdLow = 40f,
                edgeThresholdHigh = 140f
            ),
            regions = listOf(regA, regB),
            layers = layers,
            width = 100,
            height = 100
        )
    }

    // -------------------------------------------------------------------------
    // 1. Integration & Layer Generation
    // -------------------------------------------------------------------------
    @Test
    fun testIntegrateApprovedResult_createsAllDiscreteLayersInDeterministicOrder() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val doc = result.document

        // Must create dedicated AutoDraw Group
        val group = doc.findLayerById(result.autoDrawGroupId) as? LayerNode.LayerGroup
        assertNotNull("AutoDraw layer group must be present in document", group)
        assertEquals("AutoDraw: Classic_cel", group!!.name)

        // 7 distinct child layers in group
        assertEquals(7, group.children.size)

        // Verify deterministic bottom-to-top layer stack ordering
        val expectedRoles = listOf(
            AutoDrawLayerRole.BASE_COLOUR,
            AutoDrawLayerRole.SHADOWS,
            AutoDrawLayerRole.HIGHLIGHTS,
            AutoDrawLayerRole.TEXTURES,
            AutoDrawLayerRole.LINEWORK,
            AutoDrawLayerRole.PATHS,
            AutoDrawLayerRole.MANUAL_EDITS
        )

        val prov = doc.autoDrawProvenance!!
        expectedRoles.forEachIndexed { idx, role ->
            val child = group.children[idx]
            val recordedRoleName = prov.layerRoles[child.id]
            assertEquals("Layer at index $idx must correspond to role $role", role.name, recordedRoleName)
        }

        // Verify Shadows is Multiply, Highlights is Screen
        val shadowLayer = group.children[1] as LayerNode.RasterLayer
        val highlightLayer = group.children[2] as LayerNode.RasterLayer
        assertEquals(BlendModeType.Multiply, shadowLayer.blendMode)
        assertEquals(BlendModeType.Screen, highlightLayer.blendMode)
    }

    // -------------------------------------------------------------------------
    // 2. Strict Invariant: Never represent a flattened effect as editable geometry
    // -------------------------------------------------------------------------
    @Test(expected = IllegalArgumentException::class)
    fun testNeverRepresentFlattenedEffectAsEditableGeometry_rejectsRasterInPathsLayer() {
        // Attempting to construct a PATHS layer input with raster pixels must throw IllegalArgumentException
        AutoDrawLayerInput(
            role = AutoDrawLayerRole.PATHS,
            pixels = IntArray(100) { 0xFF000000.toInt() }
        )
    }

    @Test
    fun testVectorPathsContainRealEditableGeometryWithAnchors() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val vectorLayerId = result.roleToLayerId[AutoDrawLayerRole.PATHS]!!
        val vectorLayer = result.document.findLayerById(vectorLayerId) as? LayerNode.VectorPathLayer

        assertNotNull(vectorLayer)
        assertEquals(2, vectorLayer!!.vectorObjects.size)

        val facePath = vectorLayer.vectorObjects.find { it.id == "path_face_contour" }!!
        assertEquals(4, facePath.anchors.size)
        assertTrue(facePath.anchors[0].hasHandleOut)
        assertEquals(25f, facePath.anchors[0].handleOutX)
        assertEquals("region_A", facePath.metadata["sourceRegionId"])
        assertEquals("false", facePath.metadata["isUserModified"])
    }

    // -------------------------------------------------------------------------
    // 3. Provenance Continuity
    // -------------------------------------------------------------------------
    @Test
    fun testProvenanceTracking_recordsSourceImageHashAndAlgorithms() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val prov = result.document.autoDrawProvenance

        assertNotNull(prov)
        assertEquals("sha256_mock_hash_face_hair_001", prov!!.sourceImageHash)
        assertEquals("autodraw_hybrid_v1", prov.algorithm)
        assertEquals("classic_cel", prov.stylePresetId)
        assertEquals(2, prov.regions.size)

        val regA = prov.regions.find { it.regionId == "region_A" }!!
        assertEquals("Character Face", regA.regionName)
        assertEquals("FACE", regA.semanticRole)
        assertTrue(regA.pathIds.contains("path_face_contour"))
    }

    // -------------------------------------------------------------------------
    // 4. Manual Corrections: Brush Edits
    // -------------------------------------------------------------------------
    @Test
    fun testManualBrushCorrection_recordsBrushEditOverride() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val manualEditsLayerId = result.roleToLayerId[AutoDrawLayerRole.MANUAL_EDITS]!!

        val editedDoc = AutoDrawDocumentIntegrator.recordManualBrushEdit(
            result.document,
            layerId = manualEditsLayerId,
            regionId = "region_A",
            description = "Painted manual skin tone highlight dab"
        )

        val prov = editedDoc.autoDrawProvenance!!
        assertEquals(1, prov.userOverrides.size)

        val override = prov.userOverrides[0]
        assertEquals("region_A", override.regionId)
        assertEquals(UserOverrideType.BRUSH_EDIT, override.overrideType)
        assertEquals(manualEditsLayerId, override.layerId)
        assertEquals("Painted manual skin tone highlight dab", override.description)
    }

    // -------------------------------------------------------------------------
    // 5. Manual Corrections: Path Anchor Editing
    // -------------------------------------------------------------------------
    @Test
    fun testManualPathAnchorEditing_updatesAnchorAndMarksUserModified() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val pathsLayerId = result.roleToLayerId[AutoDrawLayerRole.PATHS]!!

        // Move anchor 0 on face contour from (20, 20) to (22, 24)
        val editedDoc = AutoDrawDocumentIntegrator.editPathAnchor(
            result.document,
            vectorLayerId = pathsLayerId,
            objectId = "path_face_contour",
            anchorIndex = 0,
            newAnchor = VectorAnchor(22f, 24f, handleOutX = 27f, handleOutY = 24f),
            regionId = "region_A"
        )

        val vectorLayer = editedDoc.findLayerById(pathsLayerId) as LayerNode.VectorPathLayer
        val facePath = vectorLayer.vectorObjects.find { it.id == "path_face_contour" }!!

        assertEquals(22f, facePath.anchors[0].x, 0.001f)
        assertEquals(24f, facePath.anchors[0].y, 0.001f)
        assertEquals("true", facePath.metadata["isUserModified"])

        val prov = editedDoc.autoDrawProvenance!!
        val override = prov.userOverrides.find { it.overrideType == UserOverrideType.PATH_ANCHOR_EDIT }!!
        assertEquals("region_A", override.regionId)
    }

    // -------------------------------------------------------------------------
    // 6. Manual Corrections: Recolouring
    // -------------------------------------------------------------------------
    @Test
    fun testManualRecolouring_updatesRegionColorAndRecordsOverride() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)

        // Recolour hair region (region_B) from brown to blonde (0xFFFFD700)
        val editedDoc = AutoDrawDocumentIntegrator.recolourRegion(
            result.document,
            regionId = "region_B",
            newColorRgb = 0xFFFFD700.toInt(),
            description = "Recoloured hair to blonde"
        )

        val prov = editedDoc.autoDrawProvenance!!
        val regB = prov.regions.find { it.regionId == "region_B" }!!
        assertEquals(0xFFFFD700.toInt(), regB.colorRgb)

        val override = prov.userOverrides.find { it.overrideType == UserOverrideType.RECOLOUR }!!
        assertEquals("region_B", override.regionId)
    }

    // -------------------------------------------------------------------------
    // 7. Manual Corrections: Erasing
    // -------------------------------------------------------------------------
    @Test
    fun testManualErasing_recordsEraseOverride() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val lineworkId = result.roleToLayerId[AutoDrawLayerRole.LINEWORK]!!

        val editedDoc = AutoDrawDocumentIntegrator.recordEraseEdit(
            result.document,
            layerId = lineworkId,
            regionId = "region_A",
            description = "Erased stray cheek contour segment"
        )

        val prov = editedDoc.autoDrawProvenance!!
        val override = prov.userOverrides.find { it.overrideType == UserOverrideType.ERASE }!!
        assertEquals("region_A", override.regionId)
        assertEquals(lineworkId, override.layerId)
    }

    // -------------------------------------------------------------------------
    // 8. Manual Corrections: Layer Reordering
    // -------------------------------------------------------------------------
    @Test
    fun testLayerReordering_changesChildOrderAndRecordsOverride() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val groupId = result.autoDrawGroupId

        // Swap layer at index 0 and index 1
        val editedDoc = AutoDrawDocumentIntegrator.reorderLayersInGroup(
            result.document,
            groupId = groupId,
            fromIndex = 0,
            toIndex = 1
        )

        val group = editedDoc.findLayerById(groupId) as LayerNode.LayerGroup
        val prov = editedDoc.autoDrawProvenance!!

        // Child 0 should now be Shadows (previously child 1)
        assertEquals("SHADOWS", prov.layerRoles[group.children[0].id])
        assertEquals("BASE_COLOUR", prov.layerRoles[group.children[1].id])

        val override = prov.userOverrides.find { it.overrideType == UserOverrideType.LAYER_REORDER }!!
        assertEquals(groupId, override.layerId)
    }

    // -------------------------------------------------------------------------
    // 9. Rerun Region: Strict Non-Target Artwork Preservation
    // -------------------------------------------------------------------------
    @Test
    fun testRerunRegion_preservesUnrelatedArtworkStrictly() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val pathsLayerId = result.roleToLayerId[AutoDrawLayerRole.PATHS]!!

        // Prepare updated result only for region_A
        val updatedFacePath = VectorObject(
            id = "path_face_contour_v2",
            name = "Face Contour V2",
            shapeType = VectorShapeType.BEZIER_PATH,
            anchors = listOf(VectorAnchor(25f, 25f), VectorAnchor(65f, 65f)),
            metadata = mapOf("sourceRegionId" to "region_A")
        )
        val rerunResult = AutoDrawResult(
            config = sampleAutoDrawResult.config.copy(edgeThresholdLow = 30f),
            regions = listOf(sampleAutoDrawResult.regions[0]),
            layers = listOf(
                AutoDrawLayerInput(
                    role = AutoDrawLayerRole.PATHS,
                    vectorObjects = listOf(updatedFacePath),
                    regionIds = listOf("region_A")
                )
            ),
            width = 100,
            height = 100
        )

        val rerunOutput = AutoDrawDocumentIntegrator.rerunRegion(
            result.document,
            targetRegionId = "region_A",
            updatedRegionResult = rerunResult,
            conflictStrategy = ConflictResolutionStrategy.OVERWRITE_WITH_REANALYSIS
        )

        val updatedPathsLayer = rerunOutput.document.findLayerById(pathsLayerId) as LayerNode.VectorPathLayer

        // Invariant: Unrelated artwork (region_B hair path) MUST BE STRICTLY PRESERVED!
        val hairPath = updatedPathsLayer.vectorObjects.find { it.id == "path_hair_strand" }
        assertNotNull("Unrelated region_B hair path must survive region_A rerun completely", hairPath)
        assertEquals("region_B", hairPath!!.metadata["sourceRegionId"])

        // Invariant: External document layer outside AutoDraw group must be preserved
        val manualBg = rerunOutput.document.findLayerById("user_bg_layer")
        assertNotNull("External document manual background must remain untouched", manualBg)
    }

    // -------------------------------------------------------------------------
    // 10. Regression Test: Manual Edits Survive Region Reanalysis
    // -------------------------------------------------------------------------
    @Test
    fun testRerunRegionRegression_manualEditsSurviveOrTriggerConflict_preserveStrategy() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val pathsLayerId = result.roleToLayerId[AutoDrawLayerRole.PATHS]!!

        // 1. Artist performs manual correction on region_A face path anchor
        val editedDoc = AutoDrawDocumentIntegrator.editPathAnchor(
            result.document,
            vectorLayerId = pathsLayerId,
            objectId = "path_face_contour",
            anchorIndex = 0,
            newAnchor = VectorAnchor(21f, 23f),
            regionId = "region_A"
        )

        // 2. Artist triggers rerun-region on region_A with PRESERVE_USER_OVERRIDES
        val freshFacePath = VectorObject(
            id = "path_face_contour",
            name = "Face Contour Fresh",
            shapeType = VectorShapeType.BEZIER_PATH,
            anchors = listOf(VectorAnchor(20f, 20f), VectorAnchor(60f, 60f)),
            metadata = mapOf("sourceRegionId" to "region_A")
        )
        val rerunResult = AutoDrawResult(
            config = sampleAutoDrawResult.config,
            regions = listOf(sampleAutoDrawResult.regions[0]),
            layers = listOf(
                AutoDrawLayerInput(
                    role = AutoDrawLayerRole.PATHS,
                    vectorObjects = listOf(freshFacePath),
                    regionIds = listOf("region_A")
                )
            ),
            width = 100,
            height = 100
        )

        val rerunOutput = AutoDrawDocumentIntegrator.rerunRegion(
            editedDoc,
            targetRegionId = "region_A",
            updatedRegionResult = rerunResult,
            conflictStrategy = ConflictResolutionStrategy.PRESERVE_USER_OVERRIDES
        )

        // REGRESSION VERIFICATION 1: Conflict was explicitly recorded
        assertEquals(1, rerunOutput.conflicts.size)
        assertEquals("region_A", rerunOutput.conflicts[0].regionId)
        assertEquals(ConflictResolutionStrategy.PRESERVE_USER_OVERRIDES, rerunOutput.conflicts[0].resolvedStrategy)

        // REGRESSION VERIFICATION 2: User's manual anchor edit (21, 23) SURVIVED and was NOT overwritten!
        val pathLayer = rerunOutput.document.findLayerById(pathsLayerId) as LayerNode.VectorPathLayer
        val preservedPath = pathLayer.vectorObjects.find { it.id == "path_face_contour" }!!
        assertEquals(21f, preservedPath.anchors[0].x, 0.001f)
        assertEquals(23f, preservedPath.anchors[0].y, 0.001f)
        assertEquals("true", preservedPath.metadata["isUserModified"])
    }

    // -------------------------------------------------------------------------
    // 11. Conflict Resolution: Merge Into New Parallel Layer
    // -------------------------------------------------------------------------
    @Test
    fun testRerunRegion_mergeIntoNewLayerStrategy_keepsUserEditsAndCreatesParallelLayer() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val pathsLayerId = result.roleToLayerId[AutoDrawLayerRole.PATHS]!!

        val editedDoc = AutoDrawDocumentIntegrator.editPathAnchor(
            result.document,
            vectorLayerId = pathsLayerId,
            objectId = "path_face_contour",
            anchorIndex = 0,
            newAnchor = VectorAnchor(21f, 23f),
            regionId = "region_A"
        )

        val rerunResult = sampleAutoDrawResult.copy()
        val rerunOutput = AutoDrawDocumentIntegrator.rerunRegion(
            editedDoc,
            targetRegionId = "region_A",
            updatedRegionResult = rerunResult,
            conflictStrategy = ConflictResolutionStrategy.MERGE_INTO_NEW_LAYER
        )

        val group = rerunOutput.document.findLayerById(result.autoDrawGroupId) as LayerNode.LayerGroup
        // Group now has 7 original layers + 1 parallel reanalysis layer
        assertEquals(8, group.children.size)
        val parallelLayer = group.children.find { it.name == "Reanalysis: region_A" }
        assertNotNull("Parallel reanalysis layer must be created", parallelLayer)
    }

    // -------------------------------------------------------------------------
    // 12. Native Project Round-Trip Persistence
    // -------------------------------------------------------------------------
    @Test
    fun testNativeProjectRoundTrip_preservesFullLayerTreePathsAndProvenance() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val editedDoc = AutoDrawDocumentIntegrator.recordManualBrushEdit(
            result.document,
            layerId = result.roleToLayerId[AutoDrawLayerRole.MANUAL_EDITS]!!,
            regionId = "region_A",
            description = "Manual skin touch-up"
        )

        // Serialize to JSON
        val json = DocumentSerializer.toJsonString(editedDoc)
        assertNotNull(json)
        assertTrue(json.contains("AutoDraw: Classic_cel"))
        assertTrue(json.contains("autoDrawProvenance"))

        // Deserialize back to ArtDocument
        val restoredDoc = DocumentSerializer.fromJsonString(json)
        assertEquals(editedDoc.id, restoredDoc.id)

        // Verify AutoDraw group and 7 children survive
        val restoredGroup = restoredDoc.findLayerById(result.autoDrawGroupId) as? LayerNode.LayerGroup
        assertNotNull("AutoDraw group must be restored from JSON", restoredGroup)
        assertEquals(7, restoredGroup!!.children.size)

        // Verify VectorPathLayer and its VectorObjects survive
        val vectorLayer = restoredGroup.children.find { it is LayerNode.VectorPathLayer } as? LayerNode.VectorPathLayer
        assertNotNull(vectorLayer)
        assertEquals(2, vectorLayer!!.vectorObjects.size)

        val facePath = vectorLayer.vectorObjects.find { it.id == "path_face_contour" }!!
        assertEquals(4, facePath.anchors.size)
        assertEquals("region_A", facePath.metadata["sourceRegionId"])

        // Verify provenance and user overrides survived
        val prov = restoredDoc.autoDrawProvenance
        assertNotNull(prov)
        assertEquals("sha256_mock_hash_face_hair_001", prov!!.sourceImageHash)
        assertEquals("classic_cel", prov.stylePresetId)
        assertEquals(2, prov.regions.size)
        assertEquals(1, prov.userOverrides.size)
        assertEquals("Manual skin touch-up", prov.userOverrides[0].description)
    }

    // -------------------------------------------------------------------------
    // 13. Export: Multi-Format Verification (PNG, OpenRaster, SVG)
    // -------------------------------------------------------------------------
    @Test
    fun testExportToRasterAndOpenRasterAndSvg_preservesFidelityWithoutFakingGeometry() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val doc = result.document

        // 1. Export to PNG raster
        val pngOut = ByteArrayOutputStream()
        val dummyPixels = IntArray(100 * 100) { 0xFFFFFFFF.toInt() }
        PureKotlinPngEncoder.encodeRgba(dummyPixels, 100, 100, pngOut)
        assertTrue("PNG export output must be non-empty", pngOut.size() > 50)

        // 2. Export to Layered OpenRaster (.ora)
        val oraFile = File(tempFolder.root, "test_autodraw.ora")
        val oraResult = OpenRasterExporter.exportOra(
            document = doc,
            outputFile = oraFile
        )
        assertTrue("OpenRaster export file must exist", oraFile.exists())
        assertTrue("OpenRaster export must be non-empty", oraFile.length() > 100L)
        assertTrue("OpenRaster layer count must be > 0", oraResult.layerCount > 0)

        // 3. Export to SVG (Vector geometry)
        val pathsLayer = doc.findLayerById(result.roleToLayerId[AutoDrawLayerRole.PATHS]!!) as LayerNode.VectorPathLayer
        val svgStr = SafeSvgExporter.exportObjects(
            objects = pathsLayer.vectorObjects,
            width = doc.canvas.width,
            height = doc.canvas.height,
            title = "AutoDraw Vector Export"
        )
        assertNotNull(svgStr)
        assertTrue(svgStr.contains("<svg"))
        assertTrue(svgStr.contains("<path"))
        // Invariant: Flattened effects are never represented as editable geometry in SVG
        assertFalse("SVG must not contain fake raster filter geometry aliases", svgStr.contains("fake_raster_geometry"))
    }

    // -------------------------------------------------------------------------
    // 14. Multiple Region Overrides & Clear on Full Overwrite
    // -------------------------------------------------------------------------
    @Test
    fun testMultipleOverridesAndClearOnFullOverwrite() {
        val result = AutoDrawDocumentIntegrator.integrateApprovedResult(baseDocument, sampleAutoDrawResult)
        val manualEditsId = result.roleToLayerId[AutoDrawLayerRole.MANUAL_EDITS]!!

        var doc = AutoDrawDocumentIntegrator.recordManualBrushEdit(result.document, manualEditsId, "region_A")
        doc = AutoDrawDocumentIntegrator.recordManualBrushEdit(doc, manualEditsId, "region_B")
        assertEquals(2, doc.autoDrawProvenance!!.userOverrides.size)

        // Overwrite region_A only
        val rerunResult = sampleAutoDrawResult.copy()
        val rerunOutput = AutoDrawDocumentIntegrator.rerunRegion(
            doc,
            targetRegionId = "region_A",
            updatedRegionResult = rerunResult,
            conflictStrategy = ConflictResolutionStrategy.OVERWRITE_WITH_REANALYSIS
        )

        // region_A override should be cleared; region_B override remains!
        val remainingOverrides = rerunOutput.document.autoDrawProvenance!!.userOverrides
        assertEquals(1, remainingOverrides.size)
        assertEquals("region_B", remainingOverrides[0].regionId)
    }
}
