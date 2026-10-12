package com.artstudio.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.artstudio.core.*
import com.artstudio.document.io.*
import com.artstudio.document.lifecycle.*
import com.artstudio.document.model.*
import com.artstudio.project.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * End-to-End Save/Load Verification Suite.
 *
 * Covers:
 * - Schema migration (v1 -> v2)
 * - Corrupted file recovery via DocumentRecoveryManager / ProjectLibraryManager
 * - GATE: Draw -> Kill Process -> Reopen gives a pixel-identical document
 */
class SaveLoadEndToEndAndRecoveryTest {

    private lateinit var tempDir: File
    private lateinit var recoveryDir: File
    private lateinit var projectsDir: File
    private val engine = RasterStrokeEngine()

    @Before
    fun setUp() {
        tempDir = File.createTempFile("artstudio_saveload_test_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        recoveryDir = File(tempDir, "recovery").apply { mkdirs() }
        projectsDir = File(tempDir, "projects").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // 1. Schema Migration Tests
    // -------------------------------------------------------------------------

    @Test
    fun testMigrationV1ToV2_preservesLayersAndNormalizesOpacity() {
        val v1Json = """
        {
          "schemaVersion": "1.0.0",
          "id": "legacy_proj_01",
          "title": "Legacy V1 Artwork",
          "canvas": {
            "width": 1024,
            "height": 768,
            "dpi": 300,
            "backgroundColor": -1
          },
          "layers": [
            {
              "id": "v1_layer_bg",
              "name": "Background Ink",
              "visible": true,
              "opacity": 100,
              "blendMode": "normal"
            },
            {
              "id": "v1_layer_overlay",
              "name": "Overlay Shading",
              "visible": true,
              "opacity": 75,
              "blendMode": "multiply"
            }
          ]
        }
        """.trimIndent()

        val migratedDoc = DocumentSerializer.fromJsonString(v1Json)
        assertEquals("legacy_proj_01", migratedDoc.id)
        assertEquals("Legacy V1 Artwork", migratedDoc.title)
        assertEquals(ArtDocument.CURRENT_SCHEMA_VERSION, migratedDoc.schemaVersion)
        assertEquals(1024, migratedDoc.canvas.width)
        assertEquals(768, migratedDoc.canvas.height)

        // Verify root group conversion
        val rootChildren = migratedDoc.rootGroup.children
        assertEquals(2, rootChildren.size)

        val layer1 = rootChildren[0] as LayerNode.RasterLayer
        assertEquals("v1_layer_bg", layer1.id)
        assertEquals("Background Ink", layer1.name)
        assertEquals(1.0f, layer1.opacity, 0.001f)
        assertEquals(BlendModeType.Normal, layer1.blendMode)

        val layer2 = rootChildren[1] as LayerNode.RasterLayer
        assertEquals("v1_layer_overlay", layer2.id)
        assertEquals("Overlay Shading", layer2.name)
        assertEquals(0.75f, layer2.opacity, 0.001f)
        assertEquals(BlendModeType.Multiply, layer2.blendMode)
    }

    @Test
    fun testMigration_unsupportedSchemaVersionThrowsException() {
        val futureJson = """
        {
          "schemaVersion": "99.0.0",
          "id": "future_doc",
          "title": "Future Artwork"
        }
        """.trimIndent()

        assertThrows(UnsupportedSchemaVersionException::class.java) {
            DocumentSerializer.fromJsonString(futureJson)
        }
    }

    // -------------------------------------------------------------------------
    // 2. Corrupted-File Recovery Tests
    // -------------------------------------------------------------------------

    @Test
    fun testCorruptedProject_recoversFromSnapshotWithoutDataLoss() {
        val libraryManager = ProjectLibraryManager(projectsDir)
        val projectId = "corrupt_recovery_test_01"
        val projectFile = File(projectsDir, "$projectId.ffart")

        // 1. Create a valid document with layer
        val layer = LayerNode.RasterLayer(
            id = "layer_rec_1",
            name = "Important Artwork",
            width = 512,
            height = 512
        )
        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            id = projectId,
            title = "Valuable Artwork",
            canvas = CanvasConfig(512, 512),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(layer))
        )
        val validPixels = IntArray(512 * 512) { 0xFF336699.toInt() }

        // Save valid project initially
        DocumentPackage.saveProject(
            document = doc,
            layerPixelBuffers = mapOf("layer_rec_1" to validPixels),
            outputFile = projectFile
        )
        libraryManager.registerExistingProject(projectFile)

        // 2. Write a recovery snapshot in the journal (representing autosaved newer work)
        val recoveryPixels = IntArray(512 * 512) { 0xFFFF5500.toInt() }
        val recoveryFile = libraryManager.recoveryManager.getSnapshotFile(projectId)
        DocumentPackage.saveProject(
            document = doc.copy(title = "Recovered Artwork"),
            layerPixelBuffers = mapOf("layer_rec_1" to recoveryPixels),
            outputFile = recoveryFile
        )
        val manifestFile = libraryManager.recoveryManager.getManifestFile(projectId)
        val manifest = RecoveryManifest(
            projectId = projectId,
            originalFilePath = projectFile.absolutePath,
            timestamp = System.currentTimeMillis() + 10000L,
            revision = 5L,
            isCommitted = false
        )
        manifestFile.writeText(ArtJson.stringify(manifest.toMap()))

        // 3. Corrupt the primary project file completely
        projectFile.writeBytes(ByteArray(256) { 0x00 })

        // 4. Open the project via libraryManager
        val openResult = libraryManager.openProject(projectId)
        assertTrue(
            "Project opening must detect recovery availability for corrupt project, was $openResult",
            openResult is ProjectOpenResult.RecoveryAvailable
        )

        val recResult = openResult as ProjectOpenResult.RecoveryAvailable
        assertEquals(projectId, recResult.metadata.id)
        assertTrue(recResult.recoveryFile.exists())

        // 5. Load the recovery package and verify full fidelity
        val recoveredProject = DocumentPackage.loadProject(recResult.recoveryFile)
        assertEquals("Recovered Artwork", recoveredProject.document.title)
        val recLayerPx = recoveredProject.layerPixelBuffers["layer_rec_1"]
        assertNotNull(recLayerPx)
        assertArrayEquals(recoveryPixels, recLayerPx)
    }

    @Test
    fun testCorruptedProject_withNoRecoveryReturnsErrorResult() {
        val libraryManager = ProjectLibraryManager(projectsDir)
        val projectId = "corrupt_no_rec_01"
        val projectFile = File(projectsDir, "$projectId.ffart")

        // Write corrupt garbage
        projectFile.writeText("NOT A VALID ZIP ARCHIVE")
        val metadata = ProjectMetadata(
            id = projectId,
            title = "Corrupt Artwork",
            width = 512,
            height = 512,
            filePath = projectFile.absolutePath
        )
        libraryManager.updateMetadata(metadata)

        val openResult = libraryManager.openProject(projectId)
        assertTrue("Opening unrecoverable corrupt project must yield ProjectOpenResult.Error", openResult is ProjectOpenResult.Error)
        val error = openResult as ProjectOpenResult.Error
        assertTrue(error.message.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // 3. GATE: Draw -> Kill Process -> Reopen gives a pixel-identical document
    // -------------------------------------------------------------------------

    @Test
    fun testDrawKillProcessReopen_givesPixelIdenticalDocument() = runBlocking {
        val projectId = "gate_draw_reopen_pixel_identical_${UUID.randomUUID()}"
        val projectFile = File(projectsDir, "$projectId.ffart")
        val width = 256
        val height = 256

        // Step 1: Initialize document state
        val layerId = "layer_paint_target"
        val baseLayer = LayerNode.RasterLayer(
            id = layerId,
            name = "Drawing Canvas",
            width = width,
            height = height
        )
        val doc = ArtDocument(
            schemaVersion = ArtDocument.CURRENT_SCHEMA_VERSION,
            id = projectId,
            title = "Pixel Identical Test Artwork",
            canvas = CanvasConfig(width = width, height = height),
            rootGroup = LayerNode.LayerGroup(name = "Root", children = listOf(baseLayer))
        )

        var documentState: DocumentState? = DefaultDocumentState(
            width = width,
            height = height,
            artDocument = doc
        )

        // Step 2: Draw multiple strokes with distinct colors, shapes, and pressures
        val strokeColors = intArrayOf(
            0xFFFF0055.toInt(), // Vivid pink
            0xFF00AAFF.toInt(), // Sky blue
            0xFF00DD44.toInt(), // Emerald
            0xFFFFBB00.toInt()  // Amber
        )

        for ((idx, color) in strokeColors.withIndex()) {
            val stroke = BrushStroke(
                id = "stroke_$idx",
                color = color,
                baseSize = 16f,
                layerId = layerId,
                config = BrushPresets.hardPen(size = 16f, color = color)
            )
            val startY = 30f + (idx * 50f)
            for (pt in 0 until 10) {
                val px = 20f + (pt * 22f)
                val py = startY + (if (pt % 2 == 0) 6f else -6f)
                stroke.points.add(
                    StrokePoint(
                        x = px,
                        y = py,
                        pressure = 0.5f + (pt * 0.05f),
                        timestampNano = System.nanoTime() + pt * 1000L
                    )
                )
            }
            documentState!!.addCommittedStroke(stroke)
        }

        // Step 3: Bake real layer pixels
        val bakedLayerBuffers = (documentState as DefaultDocumentState).getAllLayerPixelBuffers()
        val originalSavedPixels = bakedLayerBuffers[layerId]
        assertNotNull("Baked layer pixels must not be null", originalSavedPixels)
        assertEquals(width * height, originalSavedPixels!!.size)

        // Verify that drawn strokes actually painted non-zero pixels
        val nonZeroPixels = originalSavedPixels.count { it != 0 }
        assertTrue("Drawn strokes must produce non-zero pixels, was $nonZeroPixels", nonZeroPixels > 100)

        // Step 4: Write project atomically via temporary file and rename
        val tempFile = File.createTempFile("atomic_save_", ".tmp", projectsDir)
        DocumentPackage.saveProject(
            document = documentState.authoritativeDocument,
            layerPixelBuffers = bakedLayerBuffers,
            outputFile = tempFile
        )
        if (projectFile.exists()) {
            projectFile.delete()
        }
        assertTrue("Atomic rename must succeed", tempFile.renameTo(projectFile))
        assertTrue("Saved project file must exist on disk", projectFile.exists())

        // Step 5: Simulate process kill: destroy document state, history, and memory
        documentState = null
        System.gc()

        // Step 6: Reopen project from disk from clean process state
        val reloadedProject = DocumentPackage.loadProject(projectFile)

        // Step 7: Verify exact document metadata, layer hierarchy, and bit-exact pixel identity
        assertEquals(projectId, reloadedProject.document.id)
        assertEquals("Pixel Identical Test Artwork", reloadedProject.document.title)
        assertEquals(ArtDocument.CURRENT_SCHEMA_VERSION, reloadedProject.document.schemaVersion)
        assertEquals(width, reloadedProject.document.canvas.width)
        assertEquals(height, reloadedProject.document.canvas.height)

        val reloadedLayer = reloadedProject.document.findLayerById(layerId) as? LayerNode.RasterLayer
        assertNotNull("Target raster layer must exist in reopened document", reloadedLayer)
        assertEquals("Drawing Canvas", reloadedLayer!!.name)

        val reloadedPixels = reloadedProject.layerPixelBuffers[layerId]
        assertNotNull("Reloaded layer pixel buffer must exist", reloadedPixels)
        assertEquals("Reloaded pixel buffer size must match canvas", originalSavedPixels.size, reloadedPixels!!.size)

        // BIT-FOR-BIT EXACT PIXEL IDENTITY ASSERTION
        assertArrayEquals(
            "Reopened layer pixels must be bit-for-bit identical to the saved pixels",
            originalSavedPixels,
            reloadedPixels
        )
    }
}
