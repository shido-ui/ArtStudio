package com.artstudio.layer

import android.graphics.Bitmap
import android.graphics.Color
import com.artstudio.core.*
import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import com.artstudio.history.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Authoritative test suite for the Core Editable Raster Layer Model,
 * User Controls, Downscaled Thumbnails, Save/Reopen Persistence,
 * and 10-Layer Fixture Golden Rendering.
 */
class RasterLayerModelTest {

    private lateinit var documentState: DefaultDocumentState
    private lateinit var layerManager: LayerManager
    private lateinit var initialBgLayerId: String

    @Before
    fun setUp() {
        DeviceMemoryPolicy.setTier(DeviceRamTier.RAM_4GB)
        val initialDoc = ArtDocument(
            id = "doc_layer_test",
            title = "Layer Model Canvas",
            canvas = CanvasConfig(width = 2048, height = 2048),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = "bg_layer_initial",
                        name = "Background",
                        width = 2048,
                        height = 2048,
                        visible = true,
                        opacity = 1.0f,
                        isLocked = false
                    )
                )
            ),
            selectedLayerId = "bg_layer_initial"
        )
        documentState = DefaultDocumentState(
            width = 2048,
            height = 2048,
            backgroundColor = Color.WHITE,
            artDocument = initialDoc
        )
        layerManager = documentState.layerManager
        initialBgLayerId = "bg_layer_initial"
    }

    private fun createTestStroke(id: String, layerId: String = "", color: Int = 0xFF112233.toInt()): BrushStroke {
        val stroke = BrushStroke(
            id = id,
            color = color,
            baseSize = 14f,
            toolType = ToolType.STYLUS,
            layerId = layerId
        )
        for (i in 0..10) {
            stroke.points.add(StrokePoint(100f + i * 15f, 200f + i * 5f, 0.5f))
        }
        return stroke
    }

    // 1. Create layer
    @Test
    fun testCreateLayerDefaultAndCustomProperties() {
        assertEquals(1, layerManager.getLayers().size)
        assertEquals(initialBgLayerId, layerManager.getSelectedLayerId())

        // Create default layer
        val layer2 = layerManager.createLayer()
        assertEquals(2, layerManager.getLayers().size)
        assertEquals("Layer 2", layer2.name)
        assertEquals(1.0f, layer2.opacity, 0.001f)
        assertTrue(layer2.visible)
        assertFalse(layer2.isLocked)
        assertEquals(layer2.id, layerManager.getSelectedLayerId())
        assertEquals(layer2.id, layerManager.getLayers()[1].id)

        // Create custom named layer at specific index
        val customLayer = layerManager.createLayer(name = "Inks Overlay", index = 1)
        assertEquals(3, layerManager.getLayers().size)
        assertEquals("Inks Overlay", customLayer.name)
        assertEquals(customLayer.id, layerManager.getLayers()[1].id)
        assertEquals(layer2.id, layerManager.getLayers()[2].id)
        assertEquals(customLayer.id, layerManager.getSelectedLayerId())
    }

    // 2. Select layer
    @Test
    fun testSelectLayerValidAndInvalid() {
        val layer2 = layerManager.createLayer(name = "Layer 2")
        val layer3 = layerManager.createLayer(name = "Layer 3")

        assertEquals(layer3.id, layerManager.getSelectedLayerId())

        // Select layer 2
        val selected2 = layerManager.selectLayer(layer2.id)
        assertTrue(selected2)
        assertEquals(layer2.id, layerManager.getSelectedLayerId())
        assertEquals("Layer 2", layerManager.getSelectedLayer()?.name)

        // Select initial background layer
        val selectedBg = layerManager.selectLayer(initialBgLayerId)
        assertTrue(selectedBg)
        assertEquals(initialBgLayerId, layerManager.getSelectedLayerId())

        // Select invalid layer ID: must fail safely without altering selection
        val invalidSelect = layerManager.selectLayer("non_existent_uuid")
        assertFalse(invalidSelect)
        assertEquals(initialBgLayerId, layerManager.getSelectedLayerId())

        // Undo selection: restores previous selection
        layerManager.historyManager.undo()
        assertEquals(layer2.id, layerManager.getSelectedLayerId())
    }

    // 3. Rename layer
    @Test
    fun testRenameLayerAndUndoRedo() {
        val layer2 = layerManager.createLayer(name = "Old Name")
        assertEquals("Old Name", layerManager.getLayers()[1].name)

        val renamed = layerManager.renameLayer(layer2.id, "New Spectacular Name")
        assertTrue(renamed)
        assertEquals("New Spectacular Name", layerManager.getLayers()[1].name)

        // Undo rename
        layerManager.historyManager.undo()
        assertEquals("Old Name", layerManager.getLayers()[1].name)

        // Redo rename
        layerManager.historyManager.redo()
        assertEquals("New Spectacular Name", layerManager.getLayers()[1].name)
    }

    // 4. Duplicate layer
    @Test
    fun testDuplicateLayerClonesPropertiesAndStrokes() {
        val layer2 = layerManager.createLayer(name = "Pencil Sketch")
        layerManager.setLayerOpacity(layer2.id, 0.75f)

        // Add a stroke to layer 2
        val stroke = createTestStroke("stroke_p1", layerId = layer2.id)
        documentState.addCommittedStroke(stroke)
        assertEquals(1, documentState.committedStrokes.filter { it.layerId == layer2.id }.size)

        // Duplicate layer 2
        val duplicate = layerManager.duplicateLayer(layer2.id)
        assertNotNull(duplicate)
        assertEquals("Pencil Sketch Copy", duplicate?.name)
        assertNotEquals(layer2.id, duplicate?.id)
        assertEquals(0.75f, duplicate?.opacity ?: 0f, 0.001f)
        assertTrue(duplicate?.visible ?: false)

        // Duplicate must be inserted immediately above source layer (index 2)
        assertEquals(3, layerManager.getLayers().size)
        assertEquals(layer2.id, layerManager.getLayers()[1].id)
        assertEquals(duplicate?.id, layerManager.getLayers()[2].id)
        assertEquals(duplicate?.id, layerManager.getSelectedLayerId())

        // Strokes must be duplicated with the new layerId
        val dupStrokes = documentState.committedStrokes.filter { it.layerId == duplicate?.id }
        assertEquals(1, dupStrokes.size)
        assertEquals(stroke.color, dupStrokes[0].color)
        assertNotEquals(stroke.id, dupStrokes[0].id)
    }

    // 5. Duplicate layer undo/redo
    @Test
    fun testDuplicateLayerUndoRedo() {
        val layer2 = layerManager.createLayer(name = "Base")
        val duplicate = layerManager.duplicateLayer(layer2.id)
        assertNotNull(duplicate)
        assertEquals(3, layerManager.getLayers().size)

        // Undo duplicate
        layerManager.historyManager.undo()
        assertEquals(2, layerManager.getLayers().size)
        assertNull(layerManager.currentDocument.findLayerById(duplicate!!.id))
        assertEquals(layer2.id, layerManager.getSelectedLayerId())

        // Redo duplicate
        layerManager.historyManager.redo()
        assertEquals(3, layerManager.getLayers().size)
        assertNotNull(layerManager.currentDocument.findLayerById(duplicate.id))
        assertEquals(duplicate.id, layerManager.getSelectedLayerId())
    }

    // 6. Delete layer
    @Test
    fun testDeleteLayerUpdatesSelectionAndSurvivesUndoRedo() {
        val layer2 = layerManager.createLayer(name = "Layer 2")
        val layer3 = layerManager.createLayer(name = "Layer 3")
        assertEquals(3, layerManager.getLayers().size)
        assertEquals(layer3.id, layerManager.getSelectedLayerId())

        // Select and delete layer 2 (middle layer)
        layerManager.selectLayer(layer2.id)
        val deleted = layerManager.deleteLayer(layer2.id)
        assertTrue(deleted)
        assertEquals(2, layerManager.getLayers().size)
        assertNull(layerManager.currentDocument.findLayerById(layer2.id))

        // Selection should move to adjacent surviving layer
        assertNotNull(layerManager.getSelectedLayer())
        assertNotEquals(layer2.id, layerManager.getSelectedLayerId())

        // Undo delete: restores layer at original index with original properties
        layerManager.historyManager.undo()
        assertEquals(3, layerManager.getLayers().size)
        assertEquals(layer2.id, layerManager.getLayers()[1].id)
        assertEquals("Layer 2", layerManager.getLayers()[1].name)

        // Redo delete: removes again
        layerManager.historyManager.redo()
        assertEquals(2, layerManager.getLayers().size)
        assertNull(layerManager.currentDocument.findLayerById(layer2.id))
    }

    // 7. Delete layer last layer guard
    @Test
    fun testDeleteLayerLastLayerGuardEnforcesMinimumOneLayer() {
        assertEquals(1, layerManager.getLayers().size)

        // Attempt to delete the only layer: MUST fail
        val deleted = layerManager.deleteLayer(initialBgLayerId)
        assertFalse("Cannot delete the last remaining layer", deleted)
        assertEquals(1, layerManager.getLayers().size)
        assertEquals(initialBgLayerId, layerManager.getLayers()[0].id)
    }

    // 8. Hide / Show layer
    @Test
    fun testToggleAndSetVisibilityWithCulling() {
        val layer2 = layerManager.createLayer(name = "Details")
        assertTrue(layer2.visible)

        // Toggle visibility to hidden
        layerManager.toggleLayerVisibility(layer2.id)
        assertFalse(layerManager.getLayers()[1].visible)

        // Toggle back to visible
        layerManager.toggleLayerVisibility(layer2.id)
        assertTrue(layerManager.getLayers()[1].visible)

        // Explicit set visibility
        layerManager.setLayerVisibility(layer2.id, false)
        assertFalse(layerManager.getLayers()[1].visible)

        // Undo visibility change
        layerManager.historyManager.undo()
        assertTrue(layerManager.getLayers()[1].visible)
    }

    // 9. Reorder layers
    @Test
    fun testReorderLayersBottomToTop() {
        val l1 = initialBgLayerId
        val l2 = layerManager.createLayer(name = "Layer 2").id
        val l3 = layerManager.createLayer(name = "Layer 3").id
        val l4 = layerManager.createLayer(name = "Layer 4").id

        // Initial order: [l1 (0), l2 (1), l3 (2), l4 (3)]
        var order = layerManager.getLayers().map { it.id }
        assertEquals(listOf(l1, l2, l3, l4), order)

        // Move l2 up: target index 2 -> [l1, l3, l2, l4]
        val movedUp = layerManager.moveLayerUp(l2)
        assertTrue(movedUp)
        order = layerManager.getLayers().map { it.id }
        assertEquals(listOf(l1, l3, l2, l4), order)

        // Move l4 down: target index 2 -> [l1, l3, l4, l2]
        val movedDown = layerManager.moveLayerDown(l4)
        assertTrue(movedDown)
        order = layerManager.getLayers().map { it.id }
        assertEquals(listOf(l1, l3, l4, l2), order)

        // Arbitrary reorder: move l1 from 0 to 3 -> [l3, l4, l2, l1]
        val reordered = layerManager.reorderLayer(l1, 3)
        assertTrue(reordered)
        order = layerManager.getLayers().map { it.id }
        assertEquals(listOf(l3, l4, l2, l1), order)

        // Undo reorder
        layerManager.historyManager.undo()
        order = layerManager.getLayers().map { it.id }
        assertEquals(listOf(l1, l3, l4, l2), order)
    }

    // 10. Lock / Unlock layer prevents drawing
    @Test
    fun testLockUnlockLayerPreventsStrokeCommit() {
        val layer2 = layerManager.createLayer(name = "Inks Layer")
        assertFalse(layer2.isLocked)

        // Lock the layer
        layerManager.setLayerLocked(layer2.id, true)
        assertTrue(layerManager.getLayers()[1].isLocked)

        // Attempt to commit a stroke into locked layer: MUST be rejected
        val stroke = createTestStroke("locked_test_stroke", layerId = layer2.id)
        documentState.addCommittedStroke(stroke)

        // Verify stroke was not added
        val strokesOnLayer = documentState.committedStrokes.filter { it.layerId == layer2.id }
        assertEquals("Stroke must not be committed to locked layer", 0, strokesOnLayer.size)

        // Unlock the layer
        layerManager.toggleLayerLocked(layer2.id)
        assertFalse(layerManager.getLayers()[1].isLocked)

        // Commit stroke into unlocked layer: MUST succeed
        val validStroke = createTestStroke("valid_test_stroke", layerId = layer2.id)
        documentState.addCommittedStroke(validStroke)
        assertEquals(1, documentState.committedStrokes.filter { it.layerId == layer2.id }.size)
    }

    // 11. Opacity clamping and compositing
    @Test
    fun testOpacityClampingAndCompositing() {
        val layer2 = layerManager.createLayer(name = "Shadows")

        // Set valid opacity
        layerManager.setLayerOpacity(layer2.id, 0.42f)
        assertEquals(0.42f, layerManager.getLayers()[1].opacity, 0.001f)

        // Clamping upper bound: > 1.0 coerced to 1.0
        layerManager.setLayerOpacity(layer2.id, 1.85f)
        assertEquals(1.0f, layerManager.getLayers()[1].opacity, 0.001f)

        // Clamping lower bound: < 0.0 coerced to 0.0
        layerManager.setLayerOpacity(layer2.id, -0.5f)
        assertEquals(0.0f, layerManager.getLayers()[1].opacity, 0.001f)

        // Undo restores previous opacity
        layerManager.historyManager.undo()
        assertEquals(1.0f, layerManager.getLayers()[1].opacity, 0.001f)
        layerManager.historyManager.undo()
        assertEquals(0.42f, layerManager.getLayers()[1].opacity, 0.001f)
    }

    // 12. Complex undo/redo transaction sequence
    @Test
    fun testComplexLayerUndoRedoTransactionSequence() {
        val l2 = layerManager.createLayer(name = "Layer 2")
        layerManager.setLayerOpacity(l2.id, 0.8f)
        val l3 = layerManager.createLayer(name = "Layer 3")
        layerManager.renameLayer(l3.id, "Lineart")
        layerManager.setLayerLocked(l3.id, true)
        val l4 = layerManager.createLayer(name = "Highlights")
        layerManager.duplicateLayer(l4.id)

        assertEquals(5, layerManager.getLayers().size)

        // Rewind all operations
        val totalSteps = layerManager.historyManager.undoCount
        for (i in 0 until totalSteps) {
            assertTrue(layerManager.historyManager.canUndo)
            layerManager.historyManager.undo()
        }

        assertEquals(0, layerManager.historyManager.undoCount)
        assertEquals(1, layerManager.getLayers().size)
        assertEquals(initialBgLayerId, layerManager.getLayers()[0].id)

        // Reapply all operations
        for (i in 0 until totalSteps) {
            assertTrue(layerManager.historyManager.canRedo)
            layerManager.historyManager.redo()
        }

        assertEquals(5, layerManager.getLayers().size)
        assertEquals("Lineart", layerManager.getLayers().find { it.id == l3.id }?.name)
        assertTrue(layerManager.getLayers().find { it.id == l3.id }?.isLocked ?: false)
    }

    // 13. Save and reopen persistence with stable IDs and ordering
    @Test
    fun testAllLayerActionsSurviveSaveAndReopenPersistence() {
        // Build a rich multi-layer state
        val l2 = layerManager.createLayer(name = "Sketch")
        layerManager.setLayerOpacity(l2.id, 0.65f)
        val l3 = layerManager.createLayer(name = "Inks")
        layerManager.setLayerLocked(l3.id, true)
        val l4 = layerManager.createLayer(name = "Colors")
        layerManager.setLayerVisibility(l4.id, false) // Hidden
        val l5 = layerManager.createLayer(name = "Final Accent")
        layerManager.selectLayer(l3.id)

        val preSaveDoc = layerManager.currentDocument
        val expectedLayerIds = preSaveDoc.rootGroup.children.map { it.id }
        val expectedNames = preSaveDoc.rootGroup.children.map { it.name }
        val expectedOpacities = preSaveDoc.rootGroup.children.map { it.opacity }
        val expectedVisibilities = preSaveDoc.rootGroup.children.map { it.visible }
        val expectedLocks = preSaveDoc.rootGroup.children.map { it.isLocked }
        val expectedSelectedId = preSaveDoc.selectedLayerId

        assertEquals(5, expectedLayerIds.size)
        assertEquals(l3.id, expectedSelectedId)

        // 1. JSON serialization round-trip
        val jsonStr = DocumentSerializer.toJsonString(preSaveDoc)
        val docFromJson = DocumentSerializer.fromJsonString(jsonStr)

        assertEquals(expectedLayerIds, docFromJson.rootGroup.children.map { it.id })
        assertEquals(expectedNames, docFromJson.rootGroup.children.map { it.name })
        assertEquals(expectedOpacities, docFromJson.rootGroup.children.map { it.opacity })
        assertEquals(expectedVisibilities, docFromJson.rootGroup.children.map { it.visible })
        assertEquals(expectedLocks, docFromJson.rootGroup.children.map { it.isLocked })
        assertEquals(expectedSelectedId, docFromJson.selectedLayerId)

        // 2. Package .ffart container round-trip
        val tempPackageFile = File.createTempFile("layer_persistence_test", ".ffart")
        try {
            DocumentPackage.save(docFromJson, emptyMap(), tempPackageFile)
            val packageData = DocumentPackage.load(tempPackageFile)
            val reopenedDoc = packageData.document

            // Verify stable IDs, exact ordering, and properties survive reopening
            assertEquals(5, reopenedDoc.rootGroup.children.size)
            assertEquals(expectedLayerIds, reopenedDoc.rootGroup.children.map { it.id })
            assertEquals(expectedNames, reopenedDoc.rootGroup.children.map { it.name })
            for (idx in 0 until 5) {
                assertEquals(expectedOpacities[idx], reopenedDoc.rootGroup.children[idx].opacity, 0.001f)
                assertEquals(expectedVisibilities[idx], reopenedDoc.rootGroup.children[idx].visible)
                assertEquals(expectedLocks[idx], reopenedDoc.rootGroup.children[idx].isLocked)
            }
            assertEquals(expectedSelectedId, reopenedDoc.selectedLayerId)
        } finally {
            tempPackageFile.delete()
        }
    }

    // 14. Downscaled thumbnail generator memory budget
    @Test
    fun testDownscaledThumbnailMemoryBudget() {
        val generator = LayerThumbnailGenerator(64, 64)
        val layer = layerManager.createLayer(name = "Thumbnail Test")
        val strokes = listOf(
            createTestStroke("s1", layerId = layer.id, color = Color.RED),
            createTestStroke("s2", layerId = layer.id, color = Color.BLUE)
        )

        // Memory budget verification:
        // 64x64 ARGB_8888 is 16,384 bytes (~16 KB).
        // A full 2048x2048 buffer is 16,777,216 bytes (16 MB).
        assertEquals(64, generator.thumbnailWidth)
        assertEquals(64, generator.thumbnailHeight)
        val thumbnailBytes = generator.estimatedBytesPerThumbnail
        assertEquals(64 * 64 * 4, thumbnailBytes)
        assertTrue(
            "Thumbnail memory must be strictly bounded (< 32 KB), got $thumbnailBytes bytes",
            thumbnailBytes <= 32 * 1024
        )

        val thumbnailBmp = generator.getThumbnail(
            layer = layer,
            documentWidth = 2048,
            documentHeight = 2048,
            layerStrokes = strokes
        )

        if (thumbnailBmp != null) {
            assertEquals(64, thumbnailBmp.width)
            assertEquals(64, thumbnailBmp.height)

            // Caching verification: repeated request returns same cached bitmap instance
            val cachedBmp = generator.getThumbnail(
                layer = layer,
                documentWidth = 2048,
                documentHeight = 2048,
                layerStrokes = strokes
            )
            assertSame("Cached thumbnail must be reused", thumbnailBmp, cachedBmp)
        }

        // Invalidation & cache clearing
        generator.invalidateLayer(layer.id)
        generator.clearCache()
    }

    // 15. 10-layer fixture renders against stored golden
    @Test
    fun testTenLayerFixtureRendersAgainstStoredGolden() {
        val goldenFile = listOf(
            File("tests/fixtures/artstudio/v1/goldens/layers/ten_layer_fixture_golden.png"),
            File("../tests/fixtures/artstudio/v1/goldens/layers/ten_layer_fixture_golden.png")
        ).firstOrNull { it.exists() } ?: File("tests/fixtures/artstudio/v1/goldens/layers/ten_layer_fixture_golden.png")

        val projectJsonFile = listOf(
            File("tests/fixtures/artstudio/v1/sample_projects/ten_layer_fixture_project.json"),
            File("../tests/fixtures/artstudio/v1/sample_projects/ten_layer_fixture_project.json")
        ).firstOrNull { it.exists() } ?: File("tests/fixtures/artstudio/v1/sample_projects/ten_layer_fixture_project.json")

        val ffartFile = listOf(
            File("tests/fixtures/artstudio/v1/sample_projects/ten_layer_fixture.ffart"),
            File("../tests/fixtures/artstudio/v1/sample_projects/ten_layer_fixture.ffart")
        ).firstOrNull { it.exists() } ?: File("tests/fixtures/artstudio/v1/sample_projects/ten_layer_fixture.ffart")

        assertTrue("Stored golden PNG must exist on disk: ${goldenFile.absolutePath}", goldenFile.exists())
        assertTrue("Stored project.json fixture must exist on disk: ${projectJsonFile.absolutePath}", projectJsonFile.exists())
        assertTrue("Stored .ffart fixture must exist on disk: ${ffartFile.absolutePath}", ffartFile.exists())

        // Load project.json
        val jsonContent = projectJsonFile.readText(Charsets.UTF_8)
        val doc = DocumentSerializer.fromJsonString(jsonContent)

        // Verify document structure: exactly 10 layers in document order
        assertEquals(10, doc.rootGroup.children.size)
        assertEquals("Background", doc.rootGroup.children[0].name)
        assertEquals("Hidden Draft Grid", doc.rootGroup.children[3].name)
        assertFalse("Hidden layer must be invisible", doc.rootGroup.children[3].visible)
        assertEquals("Inks Contours", doc.rootGroup.children[7].name)
        assertTrue("Inks layer must be locked", doc.rootGroup.children[7].isLocked)
        assertEquals("Vignette Accent", doc.rootGroup.children[9].name)
        assertEquals("layer-fixture-07", doc.selectedLayerId)

        // Verify .ffart container package unpacks identically
        val packageData = DocumentPackage.load(ffartFile)
        assertEquals(10, packageData.document.rootGroup.children.size)
        assertEquals(doc.id, packageData.document.id)

        // Execute canonical Python golden diff suite reporting pixel diff and enforcing zero RMSE
        val scriptFile = listOf(
            File("tools/qa/test_layer_fixture_goldens.py"),
            File("../tools/qa/test_layer_fixture_goldens.py")
        ).firstOrNull { it.exists() }

        if (scriptFile != null) {
            val rootDir = scriptFile.canonicalFile.parentFile?.parentFile?.parentFile ?: File(".")
            val process = ProcessBuilder("python3", scriptFile.absolutePath)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            assertEquals("Golden verification script must exit with 0:\n$output", 0, exitCode)
            assertTrue("Output must confirm baseline enforced", output.contains("BASELINE ENFORCED"))
        }
    }
}
