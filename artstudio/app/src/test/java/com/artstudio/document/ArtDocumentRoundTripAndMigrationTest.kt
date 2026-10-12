package com.artstudio.document

import com.artstudio.document.io.*
import com.artstudio.document.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArtDocumentRoundTripAndMigrationTest {

    private fun resolveFixtureDir(): File {
        val direct = File("tests/fixtures/artstudio/v1/sample_projects")
        if (direct.exists()) return direct
        val relative = File("../tests/fixtures/artstudio/v1/sample_projects")
        if (relative.exists()) return relative
        throw IllegalStateException("Cannot find sample_projects directory at ${direct.absolutePath} or ${relative.absolutePath}")
    }

    // -------------------------------------------------------------------------
    // 1. JSON Parser & Serializer Round-trip
    // -------------------------------------------------------------------------
    @Test
    fun testJsonParserAndSerializerRoundTrip_simpleAndNested() {
        val sampleMap = mapOf(
            "name" to "ArtStudio",
            "version" to 2,
            "dpi" to 300.5,
            "active" to true,
            "tags" to listOf("digital", "painting", "vector"),
            "metadata" to mapOf(
                "creator" to "Lead Android Graphics Engineer",
                "nestedNull" to null
            )
        )

        val jsonStr = ArtJson.stringify(sampleMap, indent = 2)
        assertTrue(jsonStr.contains("\"name\": \"ArtStudio\""))
        assertTrue(jsonStr.contains("\"version\": 2"))

        val parsed = ArtJson.parse(jsonStr) as? Map<*, *>
        assertNotNull(parsed)
        assertEquals("ArtStudio", parsed?.get("name"))
        assertEquals(2L, (parsed?.get("version") as? Number)?.toLong())
        assertEquals(300.5, (parsed?.get("dpi") as? Number)?.toDouble() ?: 0.0, 0.001)
        assertEquals(true, parsed?.get("active"))
        val tags = parsed?.get("tags") as? List<*>
        assertEquals(listOf("digital", "painting", "vector"), tags)
    }

    // -------------------------------------------------------------------------
    // 2. Single Layer Document Round-trip
    // -------------------------------------------------------------------------
    @Test
    fun testSingleLayerDocument_roundTripMatchesExactly() {
        val rasterLayer = LayerNode.RasterLayer(
            id = "layer_base",
            name = "Base Ink",
            visible = true,
            opacity = 0.95f,
            blendMode = BlendModeType.Multiply,
            transform = LayerTransform(10f, 20f, 1f, 1f, 0f),
            isLocked = false,
            isClippingMask = false,
            assetPath = "assets/lineart.png",
            width = 1024,
            height = 1024,
            offsetX = 0,
            offsetY = 0
        )

        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = "doc_single_001",
            title = "Single Layer Test",
            canvas = CanvasConfig(1024, 1024, 300, 0xFFFFFFFFL, "sRGB"),
            rootGroup = LayerNode.LayerGroup(
                id = "root_group",
                name = "Root",
                visible = true,
                opacity = 1f,
                blendMode = BlendModeType.Normal,
                transform = LayerTransform(),
                isLocked = false,
                isClippingMask = false,
                children = listOf(rasterLayer),
                isCollapsed = false
            ),
            palettes = listOf(
                PaletteDefinition("pal_1", "Main", listOf(ColorSwatchData("#000000", "Black")))
            ),
            referenceImages = emptyList(),
            animation = null,
            autoDrawProvenance = null,
            createdAt = 1700000000000L,
            updatedAt = 1700000001000L
        )

        val jsonString = DocumentSerializer.toJsonString(doc)
        val deserialized = DocumentSerializer.fromJsonString(jsonString)

        assertEquals(doc.id, deserialized.id)
        assertEquals(doc.title, deserialized.title)
        assertEquals(doc.canvas.width, deserialized.canvas.width)
        assertEquals(doc.canvas.height, deserialized.canvas.height)
        assertEquals(doc.canvas.dpi, deserialized.canvas.dpi)
        assertEquals(doc.canvas.backgroundColor, deserialized.canvas.backgroundColor)
        assertEquals(1, deserialized.rootGroup.children.size)

        val resLayer = deserialized.rootGroup.children[0] as LayerNode.RasterLayer
        assertEquals("layer_base", resLayer.id)
        assertEquals("Base Ink", resLayer.name)
        assertEquals(0.95f, resLayer.opacity, 0.001f)
        assertEquals(BlendModeType.Multiply, resLayer.blendMode)
        assertEquals(10f, resLayer.transform.translationX, 0.001f)
        assertEquals(20f, resLayer.transform.translationY, 0.001f)
        assertEquals("assets/lineart.png", resLayer.assetPath)
    }

    // -------------------------------------------------------------------------
    // 3. Multilayer Hierarchy, Nested Groups & Layer Order Preservation
    // -------------------------------------------------------------------------
    @Test
    fun testMultilayerHierarchy_preservesLayerOrderAndGroupStructure() {
        val layerL1 = LayerNode.RasterLayer(id = "l1", name = "Background", assetPath = "assets/bg.png")
        val layerL2 = LayerNode.RasterLayer(id = "l2", name = "Char Base", assetPath = "assets/char.png")
        val layerL3 = LayerNode.RasterLayer(id = "l3", name = "Char Shading", isClippingMask = true, blendMode = BlendModeType.Multiply)
        val charGroup = LayerNode.LayerGroup(
            id = "grp_char",
            name = "Character Group",
            children = listOf(layerL2, layerL3)
        )
        val layerL4 = LayerNode.RasterLayer(id = "l4", name = "Top Effects", blendMode = BlendModeType.Screen)

        // Bottom to top: l1, grp_char (l2, l3), l4
        val rootGroup = LayerNode.LayerGroup(
            id = "root",
            name = "Root",
            children = listOf(layerL1, charGroup, layerL4)
        )

        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = "doc_multi_002",
            title = "Multi Hierarchy",
            canvas = CanvasConfig(2048, 2048),
            rootGroup = rootGroup,
            palettes = emptyList(),
            referenceImages = emptyList(),
            animation = null,
            autoDrawProvenance = null,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        // Flat ordering before serialize
        val flatBefore = doc.allLayersFlat().map { it.id }
        assertEquals(listOf("l1", "grp_char", "l2", "l3", "l4"), flatBefore)

        val json = DocumentSerializer.toJsonString(doc)
        val reloaded = DocumentSerializer.fromJsonString(json)

        val flatAfter = reloaded.allLayersFlat().map { it.id }
        assertEquals(flatBefore, flatAfter)

        // Verify nested structure
        val rootChildren = reloaded.rootGroup.children
        assertEquals(3, rootChildren.size)
        assertEquals("l1", rootChildren[0].id)
        assertTrue(rootChildren[1] is LayerNode.LayerGroup)
        val reloadedCharGroup = rootChildren[1] as LayerNode.LayerGroup
        assertEquals(2, reloadedCharGroup.children.size)
        assertEquals("l2", reloadedCharGroup.children[0].id)
        assertEquals("l3", reloadedCharGroup.children[1].id)
        assertEquals("l4", rootChildren[2].id)
    }

    // -------------------------------------------------------------------------
    // 4. Clipping Masks & All 9 Blend Modes Preservation
    // -------------------------------------------------------------------------
    @Test
    fun testClippingMasksAndBlendModes_preservedCorrectly() {
        val blendModes = listOf(
            BlendModeType.Normal,
            BlendModeType.Multiply,
            BlendModeType.Screen,
            BlendModeType.Overlay,
            BlendModeType.SoftLight,
            BlendModeType.Darken,
            BlendModeType.Lighten,
            BlendModeType.ColorDodge,
            BlendModeType.ColorBurn
        )

        val layers = blendModes.mapIndexed { idx, bm ->
            LayerNode.RasterLayer(
                id = "blend_layer_$idx",
                name = "Layer ${bm.name}",
                blendMode = bm,
                isClippingMask = (idx % 2 == 1) // Alternate clipping masks
            )
        }

        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = "doc_blend_test",
            title = "Blend Modes",
            canvas = CanvasConfig(800, 600),
            rootGroup = LayerNode.LayerGroup(id = "root", name = "Root", children = layers),
            palettes = emptyList(),
            referenceImages = emptyList(),
            animation = null,
            autoDrawProvenance = null,
            createdAt = 100L,
            updatedAt = 200L
        )

        val json = DocumentSerializer.toJsonString(doc)
        val reloaded = DocumentSerializer.fromJsonString(json)

        assertEquals(9, reloaded.rootGroup.children.size)
        for (i in 0 until 9) {
            val child = reloaded.rootGroup.children[i] as LayerNode.RasterLayer
            assertEquals(blendModes[i], child.blendMode)
            assertEquals((i % 2 == 1), child.isClippingMask)
        }
    }

    // -------------------------------------------------------------------------
    // 5. Vector Path Data Preservation (Coordinates, Pressure, Tilt, Time)
    // -------------------------------------------------------------------------
    @Test
    fun testVectorPathData_preservesCoordinatesPressureTiltAndTimestamps() {
        val strokePoints = listOf(
            StrokePointData(10.5f, 20.2f, 0.35f, 0.1f, -0.2f, 1000L),
            StrokePointData(25.0f, 45.8f, 0.78f, 0.15f, -0.1f, 1016L),
            StrokePointData(50.3f, 80.1f, 0.99f, 0.2f, 0.0f, 1032L)
        )

        val path = VectorPathData(
            id = "vector_stroke_001",
            points = strokePoints,
            brushId = "japanese_sumi_brush",
            strokeColor = 0xFF1A1A1AL,
            strokeWidth = 7.5f
        )

        val vectorLayer = LayerNode.VectorPathLayer(
            id = "layer_vector_ink",
            name = "Inking Path",
            paths = listOf(path)
        )

        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = "doc_vector_001",
            title = "Vector Test",
            canvas = CanvasConfig(1000, 1000),
            rootGroup = LayerNode.LayerGroup(id = "root", name = "Root", children = listOf(vectorLayer)),
            palettes = emptyList(),
            referenceImages = emptyList(),
            animation = null,
            autoDrawProvenance = null,
            createdAt = 500L,
            updatedAt = 600L
        )

        val json = DocumentSerializer.toJsonString(doc)
        val reloaded = DocumentSerializer.fromJsonString(json)

        val reloadedLayer = reloaded.rootGroup.children[0] as LayerNode.VectorPathLayer
        assertEquals(1, reloadedLayer.paths.size)
        val reloadedPath = reloadedLayer.paths[0]
        assertEquals("vector_stroke_001", reloadedPath.id)
        assertEquals("japanese_sumi_brush", reloadedPath.brushId)
        assertEquals(0xFF1A1A1AL, reloadedPath.strokeColor)
        assertEquals(7.5f, reloadedPath.strokeWidth, 0.001f)
        assertEquals(3, reloadedPath.points.size)

        val pt1 = reloadedPath.points[1]
        assertEquals(25.0f, pt1.x, 0.001f)
        assertEquals(45.8f, pt1.y, 0.001f)
        assertEquals(0.78f, pt1.pressure, 0.001f)
        assertEquals(0.15f, pt1.tiltX, 0.001f)
        assertEquals(-0.1f, pt1.tiltY, 0.001f)
        assertEquals(1016L, pt1.timestampMs)
    }

    // -------------------------------------------------------------------------
    // 6. Animation Timeline Preservation
    // -------------------------------------------------------------------------
    @Test
    fun testAnimationTimeline_roundTripPreservesFrameTracksAndOnionSkin() {
        val anim = AnimationTimelineConfig(
            fps = 24,
            totalFrames = 120,
            frameTracks = listOf(
                FrameTrackData("layer_1", listOf(0, 1, 2, 3, 4)),
                FrameTrackData("layer_2", listOf(5, 6, 7, 8, 9))
            ),
            onionSkinEnabled = true,
            onionSkinFramesBefore = 3,
            onionSkinFramesAfter = 2
        )

        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = "doc_anim_001",
            title = "Animation Project",
            canvas = CanvasConfig(1920, 1080),
            rootGroup = LayerNode.LayerGroup(
                id = "root",
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "layer_1", name = "F1"),
                    LayerNode.RasterLayer(id = "layer_2", name = "F2")
                )
            ),
            palettes = emptyList(),
            referenceImages = emptyList(),
            animation = anim,
            autoDrawProvenance = null,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val json = DocumentSerializer.toJsonString(doc)
        val reloaded = DocumentSerializer.fromJsonString(json)

        assertNotNull(reloaded.animation)
        val rAnim = reloaded.animation!!
        assertEquals(24, rAnim.fps)
        assertEquals(120, rAnim.totalFrames)
        assertTrue(rAnim.onionSkinEnabled)
        assertEquals(3, rAnim.onionSkinFramesBefore)
        assertEquals(2, rAnim.onionSkinFramesAfter)
        assertEquals(2, rAnim.frameTracks.size)
        assertEquals("layer_1", rAnim.frameTracks[0].layerId)
        assertEquals(listOf(0, 1, 2, 3, 4), rAnim.frameTracks[0].activeFrames)
    }

    // -------------------------------------------------------------------------
    // 7. AutoDraw Provenance Preservation
    // -------------------------------------------------------------------------
    @Test
    fun testAutoDrawProvenance_preserved() {
        val prov = AutoDrawProvenance(
            sourceImageHash = "e57bb565dbdc2d8977bbba949b2cbe67576b5c39121a88bb8f060fc18ae676b7",
            algorithm = "offline_morphological_v1",
            edgeThresholdLow = 45.0f,
            edgeThresholdHigh = 135.0f,
            generatedLayerIds = listOf("gen_lines", "gen_flats"),
            timestampMs = 1699999999000L
        )

        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = "doc_prov_001",
            title = "Provenance Test",
            canvas = CanvasConfig(512, 512),
            rootGroup = LayerNode.LayerGroup(id = "root", name = "Root", children = emptyList()),
            palettes = emptyList(),
            referenceImages = emptyList(),
            animation = null,
            autoDrawProvenance = prov,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val json = DocumentSerializer.toJsonString(doc)
        val reloaded = DocumentSerializer.fromJsonString(json)

        assertNotNull(reloaded.autoDrawProvenance)
        val rProv = reloaded.autoDrawProvenance!!
        assertEquals("e57bb565dbdc2d8977bbba949b2cbe67576b5c39121a88bb8f060fc18ae676b7", rProv.sourceImageHash)
        assertEquals("offline_morphological_v1", rProv.algorithm)
        assertEquals(45.0f, rProv.edgeThresholdLow, 0.001f)
        assertEquals(135.0f, rProv.edgeThresholdHigh, 0.001f)
        assertEquals(listOf("gen_lines", "gen_flats"), rProv.generatedLayerIds)
        assertEquals(1699999999000L, rProv.timestampMs)
    }

    // -------------------------------------------------------------------------
    // 8. Palettes and Reference Images Preservation
    // -------------------------------------------------------------------------
    @Test
    fun testPalettesAndReferences_preserved() {
        val palette = PaletteDefinition(
            id = "pal_cyberpunk",
            name = "Neon Cyberpunk",
            swatches = listOf(
                ColorSwatchData("#00F0FF", "Neon Cyan"),
                ColorSwatchData("#FF0055", "Neon Pink")
            )
        )

        val refImage = ReferenceImageConfig(
            id = "ref_pose_01",
            assetPath = "assets/pose_ref.png",
            x = 40f,
            y = 60f,
            width = 300f,
            height = 400f,
            opacity = 0.75f,
            visible = true
        )

        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            appVersion = "1.0.0",
            id = "doc_pal_ref_001",
            title = "Palettes & Refs",
            canvas = CanvasConfig(1024, 1024),
            rootGroup = LayerNode.LayerGroup(id = "root", name = "Root", children = emptyList()),
            palettes = listOf(palette),
            referenceImages = listOf(refImage),
            animation = null,
            autoDrawProvenance = null,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val json = DocumentSerializer.toJsonString(doc)
        val reloaded = DocumentSerializer.fromJsonString(json)

        assertEquals(1, reloaded.palettes.size)
        assertEquals("pal_cyberpunk", reloaded.palettes[0].id)
        assertEquals(2, reloaded.palettes[0].swatches.size)
        assertEquals("#00F0FF", reloaded.palettes[0].swatches[0].hex)
        assertEquals("Neon Cyan", reloaded.palettes[0].swatches[0].name)

        assertEquals(1, reloaded.referenceImages.size)
        val rRef = reloaded.referenceImages[0]
        assertEquals("ref_pose_01", rRef.id)
        assertEquals("assets/pose_ref.png", rRef.assetPath)
        assertEquals(40f, rRef.x, 0.001f)
        assertEquals(60f, rRef.y, 0.001f)
        assertEquals(300f, rRef.width, 0.001f)
        assertEquals(400f, rRef.height, 0.001f)
        assertEquals(0.75f, rRef.opacity, 0.001f)
        assertTrue(rRef.visible)
    }

    // -------------------------------------------------------------------------
    // 9. Legacy v1 to v2 Schema Migration
    // -------------------------------------------------------------------------
    @Test
    fun testLegacyV1ToV2Migration_convertsFlatLayersAndNormalizesOpacity() {
        val sampleProjectsDir = resolveFixtureDir()
        val legacyJsonFile = File(sampleProjectsDir, "sample_legacy_v1.json")
        assertTrue("Legacy v1 sample JSON file must exist", legacyJsonFile.exists())

        val legacyJsonContent = legacyJsonFile.readText(Charsets.UTF_8)
        val migratedDoc = DocumentSerializer.fromJsonString(legacyJsonContent)

        // Schema version should now be upgraded to CURRENT_SCHEMA_VERSION (2.0.0)
        assertEquals(ArtDocument.CURRENT_SCHEMA_VERSION, migratedDoc.schemaVersion)
        assertEquals("doc_legacy_v1_sample", migratedDoc.id)
        assertEquals("Legacy v1 Flat Project", migratedDoc.title)
        assertEquals(512, migratedDoc.canvas.width)
        assertEquals(512, migratedDoc.canvas.height)
        assertEquals("sRGB", migratedDoc.canvas.colorSpace) // Default injected during migration

        // Root group must be created
        assertEquals("root_group", migratedDoc.rootGroup.id)
        assertEquals(2, migratedDoc.rootGroup.children.size)

        // Layer 1: Background (was 100% in v1 -> 1.0f in v2)
        val l1 = migratedDoc.rootGroup.children[0] as LayerNode.RasterLayer
        assertEquals("legacy_layer_bg", l1.id)
        assertEquals("Background", l1.name)
        assertEquals(1.0f, l1.opacity, 0.001f)
        assertTrue(l1.isLocked)
        assertFalse(l1.isClippingMask)

        // Layer 2: Ink (was 85% in v1 -> 0.85f in v2)
        val l2 = migratedDoc.rootGroup.children[1] as LayerNode.RasterLayer
        assertEquals("legacy_layer_ink", l2.id)
        assertEquals("Ink Strokes", l2.name)
        assertEquals(0.85f, l2.opacity, 0.001f)
        assertEquals(BlendModeType.Multiply, l2.blendMode)
        assertFalse(l2.isLocked)
    }

    // -------------------------------------------------------------------------
    // 10. Package Save & Load Round-trip (.ffart Package Archive Integrity)
    // -------------------------------------------------------------------------
    @Test
    fun testPackageSaveAndLoad_ffartArchiveIntegrity() {
        val tempFile = File.createTempFile("test_package_", ".ffart")
        try {
            val rasterLayer = LayerNode.RasterLayer(
                id = "layer_img",
                name = "Image Layer",
                assetPath = "assets/test_art.png",
                width = 512,
                height = 512
            )
            val doc = ArtDocument(
                schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
                appVersion = "1.0.0",
                id = "doc_pkg_test",
                title = "Package Test",
                canvas = CanvasConfig(512, 512),
                rootGroup = LayerNode.LayerGroup(id = "root", name = "Root", children = listOf(rasterLayer)),
                palettes = emptyList(),
                referenceImages = emptyList(),
                animation = null,
                autoDrawProvenance = null,
                createdAt = 1000L,
                updatedAt = 2000L
            )

            val dummyAssetBytes = "DUMMY_PNG_RAW_BYTES_FOR_TESTING".toByteArray(Charsets.UTF_8)
            val dummyThumbnailBytes = "THUMBNAIL_BYTES".toByteArray(Charsets.UTF_8)

            // Save package
            DocumentPackage.save(
                document = doc,
                assets = mapOf("assets/test_art.png" to dummyAssetBytes),
                outputFile = tempFile,
                thumbnail = dummyThumbnailBytes
            )

            assertTrue(tempFile.exists())
            assertTrue(tempFile.length() > 0)

            // Load package
            val packageData = DocumentPackage.load(tempFile)
            assertEquals("doc_pkg_test", packageData.document.id)
            assertEquals(1, packageData.document.rootGroup.children.size)
            assertEquals("assets/test_art.png", (packageData.document.rootGroup.children[0] as LayerNode.RasterLayer).assetPath)

            // Check asset round-trip bit-exact
            assertTrue(packageData.assets.containsKey("assets/test_art.png"))
            assertArrayEquals(dummyAssetBytes, packageData.assets["assets/test_art.png"])

            // Check thumbnail round-trip bit-exact
            assertNotNull(packageData.thumbnail)
            assertArrayEquals(dummyThumbnailBytes, packageData.thumbnail)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    // -------------------------------------------------------------------------
    // 11. Package Corrupt Detection: Missing or Invalid Mimetype
    // -------------------------------------------------------------------------
    @Test
    fun testPackageCorruptDetection_missingMimetypeThrows() {
        val sampleProjectsDir = resolveFixtureDir()
        val badMimeFile = File(sampleProjectsDir, "sample_corrupt_bad_mimetype.ffart")
        assertTrue(badMimeFile.exists())

        try {
            DocumentPackage.load(badMimeFile)
            fail("Expected CorruptDocumentException for package with bad mimetype")
        } catch (e: CorruptDocumentException) {
            assertTrue(e.message?.contains("mimetype", ignoreCase = true) == true)
        }
    }

    // -------------------------------------------------------------------------
    // 12. Package Corrupt Detection: Missing project.json
    // -------------------------------------------------------------------------
    @Test
    fun testPackageCorruptDetection_missingProjectJsonThrows() {
        val sampleProjectsDir = resolveFixtureDir()
        val missingJsonFile = File(sampleProjectsDir, "sample_corrupt_missing_json.ffart")
        assertTrue(missingJsonFile.exists())

        try {
            DocumentPackage.load(missingJsonFile)
            fail("Expected CorruptDocumentException for package missing project.json")
        } catch (e: CorruptDocumentException) {
            assertTrue(e.message?.contains("project.json", ignoreCase = true) == true)
        }
    }

    // -------------------------------------------------------------------------
    // 13. Package Corrupt Detection: Malformed JSON syntax
    // -------------------------------------------------------------------------
    @Test
    fun testPackageCorruptDetection_malformedJsonThrows() {
        val sampleProjectsDir = resolveFixtureDir()
        val corruptJsonFile = File(sampleProjectsDir, "sample_corrupt_invalid_json.ffart")
        assertTrue(corruptJsonFile.exists())

        try {
            DocumentPackage.load(corruptJsonFile)
            fail("Expected CorruptDocumentException for package with malformed JSON")
        } catch (e: CorruptDocumentException) {
            assertTrue(e.message?.contains("Failed to parse project.json", ignoreCase = true) == true)
        }
    }

    // -------------------------------------------------------------------------
    // 14. Package Security: Zip Slip Path Traversal Rejected
    // -------------------------------------------------------------------------
    @Test
    fun testPackageSecurity_zipSlipPathTraversalRejected() {
        val zipSlipFile = File.createTempFile("zip_slip_", ".ffart")
        try {
            ZipOutputStream(FileOutputStream(zipSlipFile)).use { zos ->
                // Write mimetype
                val mimeBytes = DocumentPackage.MIMETYPE.toByteArray(Charsets.US_ASCII)
                val mEntry = ZipEntry("mimetype")
                zos.putNextEntry(mEntry)
                zos.write(mimeBytes)
                zos.closeEntry()

                // Attempt malicious traversal entry
                val maliciousEntry = ZipEntry("../../../etc/shadow")
                zos.putNextEntry(maliciousEntry)
                zos.write("malicious payload".toByteArray())
                zos.closeEntry()
            }

            try {
                DocumentPackage.load(zipSlipFile)
                fail("Expected CorruptDocumentException for Zip Slip attempt")
            } catch (e: CorruptDocumentException) {
                assertTrue(e.message?.contains("path traversal", ignoreCase = true) == true)
            }
        } finally {
            if (zipSlipFile.exists()) {
                zipSlipFile.delete()
            }
        }
    }

    // -------------------------------------------------------------------------
    // 15. Real Fixtures Integration: Load Generated Sample Documents
    // -------------------------------------------------------------------------
    @Test
    fun testSampleDocuments_fromFixturesDirectoryLoadSuccessfully() {
        val sampleProjectsDir = resolveFixtureDir()

        // 1. Single Layer Line Art
        val singleLayerPkg = DocumentPackage.load(File(sampleProjectsDir, "sample_single_layer.ffart"))
        assertEquals("doc_sample_single_01", singleLayerPkg.document.id)
        assertEquals(512, singleLayerPkg.document.canvas.width)
        assertEquals(1, singleLayerPkg.document.rootGroup.children.size)
        assertTrue(singleLayerPkg.assets.containsKey("assets/lineart_simple_01.png"))
        assertNotNull(singleLayerPkg.thumbnail)

        // 2. Multilayer Groups & Masks
        val multiLayerPkg = DocumentPackage.load(File(sampleProjectsDir, "sample_multilayer_groups_masks.ffart"))
        assertEquals("doc_sample_multi_02", multiLayerPkg.document.id)
        assertEquals(2, multiLayerPkg.document.rootGroup.children.size) // grp_background, grp_character
        val charGroup = multiLayerPkg.document.rootGroup.children[1] as LayerNode.LayerGroup
        assertEquals(4, charGroup.children.size) // base, shadows, highlights (clipping), lineart
        val highlightLayer = charGroup.children[2] as LayerNode.RasterLayer
        assertTrue(highlightLayer.isClippingMask)
        assertEquals(BlendModeType.Screen, highlightLayer.blendMode)
        assertNotNull(multiLayerPkg.document.autoDrawProvenance)
        assertEquals("offline_morphological_v1", multiLayerPkg.document.autoDrawProvenance?.algorithm)

        // 3. Vector Paths Sketch
        val vectorPkg = DocumentPackage.load(File(sampleProjectsDir, "sample_vector_paths.ffart"))
        assertEquals("doc_sample_vector_03", vectorPkg.document.id)
        val vectorLayer = vectorPkg.document.rootGroup.children[0] as LayerNode.VectorPathLayer
        assertEquals(2, vectorLayer.paths.size)
        assertEquals("inking_pen", vectorLayer.paths[0].brushId)
        assertEquals(4, vectorLayer.paths[0].points.size)

        // 4. Transparency & Reference Image
        val transpPkg = DocumentPackage.load(File(sampleProjectsDir, "sample_transparency_and_reference.ffart"))
        assertEquals(0L, transpPkg.document.canvas.backgroundColor) // Fully transparent
        assertEquals(1, transpPkg.document.referenceImages.size)
        assertEquals(180f, transpPkg.document.referenceImages[0].width, 0.001f)

        // 5. Animation Timeline
        val animPkg = DocumentPackage.load(File(sampleProjectsDir, "sample_animation_timeline.ffart"))
        assertNotNull(animPkg.document.animation)
        assertEquals(12, animPkg.document.animation?.fps)
        assertEquals(8, animPkg.document.animation?.totalFrames)
        assertEquals(2, animPkg.document.animation?.frameTracks?.size)
    }
}
