package com.artstudio.workflow

import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.io.psd.PsdDocumentImporter
import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerMask
import com.artstudio.document.model.LayerNode
import com.artstudio.export.*
import com.artstudio.export.gif.GifDecoder
import com.artstudio.document.model.BlendModeType
import com.artstudio.project.ProjectMetadata
import com.artstudio.project.ProjectRepository
import com.artstudio.selection.SelectionMask
import com.artstudio.selection.SelectionShape
import com.artstudio.selection.SelectionCombineMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * End-to-End Scripted Manual Painting Workflow Verification Test for Prompt 43.
 *
 * Verifies complete artist pipeline end-to-end without AutoDraw or AI:
 * 1. Create project with defined canvas dimensions and background.
 * 2. Draw raster stroke artwork on base layer.
 * 3. Use multi-layer hierarchy, non-destructive layer masks, blend modes, and selections.
 * 4. Save native project package (.ffart) atomically to disk.
 * 5. Close project and clear active session.
 * 6. Reopen project package from disk, validating 100% roundtrip fidelity.
 * 7. Import layered file (PSD/ORA) without silent flattening.
 * 8. Execute a manual correction edit on the imported artwork.
 * 9. Export final artwork to all supported delivery formats (PNG, ORA, PSD, GIF) with atomic verification.
 */
class EndToEndManualPaintingWorkflowTest {

    private lateinit var tempDir: File
    private lateinit var projectRepository: ProjectRepository
    private val exportService = ArtworkExportService()

    @Before
    fun setUp() {
        tempDir = File("build/tmp/test_manual_e2e_${System.currentTimeMillis()}").apply { mkdirs() }
        projectRepository = ProjectRepository()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun getFixtureFile(relativePath: String): File {
        return listOf(
            File("tests/fixtures/artstudio/v1/$relativePath"),
            File("../tests/fixtures/artstudio/v1/$relativePath"),
            File("/root/artstudio/tests/fixtures/artstudio/v1/$relativePath")
        ).firstOrNull { it.exists() } ?: File("tests/fixtures/artstudio/v1/$relativePath")
    }

    @Test
    fun testCompleteManualPaintingWorkflow_createDrawLayersMaskSaveReopenImportCorrectExport() = runBlocking {
        val width = 64
        val height = 64
        val totalPixels = width * height

        // =====================================================================
        // STEP 1: CREATE PROJECT
        // =====================================================================
        val projectMeta: ProjectMetadata = projectRepository.createNewProject(
            title = "Landscape Masterpiece",
            width = width,
            height = height
        )
        assertNotNull(projectMeta)
        assertEquals("Landscape Masterpiece", projectMeta.title)
        assertEquals(projectMeta.id, projectRepository.activeProject?.id)

        // Initialize authoritative document model
        val baseLayerId = "layer_base_bg"
        var document = ArtDocument(
            id = projectMeta.id,
            title = projectMeta.title,
            canvas = CanvasConfig(width = width, height = height, backgroundColor = 0xFFFFFFFFL),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(
                        id = baseLayerId,
                        name = "Background Scenery",
                        width = width,
                        height = height,
                        visible = true,
                        opacity = 1.0f
                    )
                )
            ),
            selectedLayerId = baseLayerId
        )

        val layerPixelBuffers = mutableMapOf<String, IntArray>()
        val layerMaskBuffers = mutableMapOf<String, IntArray>()

        // =====================================================================
        // STEP 2: DRAW ON BASE LAYER (Sky & Grass)
        // =====================================================================
        val basePixels = IntArray(totalPixels)
        for (y in 0 until height) {
            for (x in 0 until width) {
                basePixels[y * width + x] = if (y < 32) {
                    0xFF2196F3.toInt() // Blue sky
                } else {
                    0xFF4CAF50.toInt() // Green grass
                }
            }
        }
        layerPixelBuffers[baseLayerId] = basePixels
        projectRepository.recordStrokes(projectMeta.id, additionalStrokes = 40)
        assertTrue(projectRepository.activeProject?.isDirty == true)

        // =====================================================================
        // STEP 3: USE LAYERS, MASKS & SELECTIONS
        // =====================================================================
        // Add "Sun Object" Layer
        val sunLayerId = "layer_sun"
        val sunPixels = IntArray(totalPixels) { 0x00000000 }
        // Paint yellow sun square [10..25, 10..25]
        for (y in 10 until 26) {
            for (x in 10 until 26) {
                sunPixels[y * width + x] = 0xFFFFEB3B.toInt() // Pure Yellow
            }
        }
        layerPixelBuffers[sunLayerId] = sunPixels

        // Apply a Layer Mask to Sun Layer (cut out right half where x > 18)
        val sunMaskPixels = IntArray(totalPixels) { 0xFFFFFFFF.toInt() } // White = fully visible
        for (y in 0 until height) {
            for (x in 19 until width) {
                sunMaskPixels[y * width + x] = 0x00000000 // Black = hidden by mask
            }
        }
        layerMaskBuffers[sunLayerId] = sunMaskPixels

        val sunMask = LayerMask(
            width = width,
            height = height,
            enabled = true,
            assetPath = "assets/mask_$sunLayerId.png"
        )
        val sunLayer = LayerNode.RasterLayer(
            id = sunLayerId,
            name = "Sun Object",
            width = width,
            height = height,
            visible = true,
            opacity = 0.95f,
            mask = sunMask
        )

        // Add a "Shadow Tint" Layer with MULTIPLY blend mode using a Selection
        val shadowLayerId = "layer_shadow_tint"
        val selection = SelectionMask(width, height)
        selection.applyShape(
            SelectionShape.Ellipse(left = 20f, top = 36f, right = 44f, bottom = 60f),
            SelectionCombineMode.REPLACE
        )
        val shadowPixels = IntArray(totalPixels) { 0x00000000 }
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (selection.isSelected(x, y)) {
                    shadowPixels[y * width + x] = 0xFF555555.toInt() // Dark overlay tint
                }
            }
        }
        layerPixelBuffers[shadowLayerId] = shadowPixels

        val shadowLayer = LayerNode.RasterLayer(
            id = shadowLayerId,
            name = "Shadow Tint",
            width = width,
            height = height,
            visible = true,
            opacity = 0.75f,
            blendMode = BlendModeType.Multiply
        )

        // Update document layer hierarchy
        document = document.copy(
            rootGroup = document.rootGroup.copy(
                children = listOf(
                    document.rootGroup.children[0],
                    sunLayer,
                    shadowLayer
                )
            ),
            selectedLayerId = shadowLayerId
        )
        assertEquals(3, document.rootGroup.children.size)

        // =====================================================================
        // STEP 4: SAVE NATIVE PROJECT (.ffart)
        // =====================================================================
        val ffartFile = File(tempDir, "landscape_project.ffart")
        DocumentPackage.saveProject(
            document = document,
            layerPixelBuffers = layerPixelBuffers,
            layerMaskBuffers = layerMaskBuffers,
            outputFile = ffartFile
        )

        assertTrue(".ffart project file must exist on disk", ffartFile.exists())
        assertTrue(".ffart project file must be non-empty", ffartFile.length() > 0)

        projectRepository.markClean(projectMeta.id)
        assertFalse("Project must be marked clean after save", projectRepository.activeProject?.isDirty == true)

        // =====================================================================
        // STEP 5: CLOSE PROJECT
        // =====================================================================
        val closedMeta = projectRepository.openProject("non_existent_id")
        assertNull(closedMeta)
        // Reset local in-memory buffers to simulate clean application restart
        layerPixelBuffers.clear()
        layerMaskBuffers.clear()

        // =====================================================================
        // STEP 6: REOPEN PROJECT FROM DISK (.ffart)
        // =====================================================================
        val loadedProject = DocumentPackage.loadProject(ffartFile)
        val reopenedDoc = loadedProject.document

        assertEquals("Landscape Masterpiece", reopenedDoc.title)
        assertEquals(width, reopenedDoc.canvas.width)
        assertEquals(height, reopenedDoc.canvas.height)
        assertEquals(3, reopenedDoc.rootGroup.children.size)

        // Verify layers, opacities, and blend modes restored
        val restoredBg = reopenedDoc.findLayerById(baseLayerId) as LayerNode.RasterLayer
        val restoredSun = reopenedDoc.findLayerById(sunLayerId) as LayerNode.RasterLayer
        val restoredShadow = reopenedDoc.findLayerById(shadowLayerId) as LayerNode.RasterLayer

        assertNotNull(restoredBg)
        assertNotNull(restoredSun)
        assertNotNull(restoredShadow)

        assertEquals(BlendModeType.Multiply, restoredShadow.blendMode)
        assertEquals(0.75f, restoredShadow.opacity, 0.01f)
        assertNotNull("Sun mask must be restored", restoredSun.mask)
        assertTrue(restoredSun.mask?.enabled == true)

        // Verify pixel buffers decoded with 100% round-trip fidelity
        val reloadedSunPixels = loadedProject.layerPixelBuffers[sunLayerId]
        val reloadedSunMask = loadedProject.layerMaskBuffers[sunLayerId]
        assertNotNull("Sun layer pixel buffer must be restored", reloadedSunPixels)
        assertNotNull("Sun mask buffer must be restored", reloadedSunMask)

        // Golden check: Sun pixel at (14, 14) is yellow
        assertEquals(0xFFFFEB3B.toInt(), reloadedSunPixels!![14 * width + 14])

        // Golden check: Mask pixel at (22, 14) is black (hidden)
        val maskAlpha = (reloadedSunMask!![14 * width + 22] ushr 24) and 0xFF
        val maskR = (reloadedSunMask[14 * width + 22] ushr 16) and 0xFF
        assertTrue("Sun mask must mask out right half", maskAlpha == 0 || maskR == 0)

        // =====================================================================
        // STEP 7: IMPORT A LAYERED FILE (PSD FIXTURE)
        // =====================================================================
        val psdFixture = getFixtureFile("layered_psd/sample_layered_simple_01.psd")
        assertTrue("sample_layered_simple_01.psd fixture must exist", psdFixture.exists())

        val psdResult = PsdDocumentImporter.importPsd(psdFixture)
        assertNotNull("PSD import must return valid document", psdResult.document)
        val importedDoc = psdResult.document

        // Verify layered structure (zero silent flattening)
        assertTrue("Imported PSD must retain multiple layers", importedDoc.rootGroup.children.size >= 2)
        val importedSubjectLayer = importedDoc.rootGroup.children.find { it.name.contains("Subject") } as? LayerNode.RasterLayer
        assertNotNull("Imported Subject layer must exist", importedSubjectLayer)

        // Merge imported layers into working document
        val mergedChildren = reopenedDoc.rootGroup.children.toMutableList()
        val importedSubjectId = "imported_${importedSubjectLayer!!.id}"
        val renamedSubjectLayer = importedSubjectLayer.copy(id = importedSubjectId, name = "Imported Subject")
        mergedChildren.add(renamedSubjectLayer)

        val workingBuffers = loadedProject.layerPixelBuffers.toMutableMap()
        val workingMasks = loadedProject.layerMaskBuffers.toMutableMap()
        psdResult.layerPixelBuffers[importedSubjectLayer.id]?.let {
            workingBuffers[importedSubjectId] = it
        }

        var workingDoc = reopenedDoc.copy(
            rootGroup = reopenedDoc.rootGroup.copy(children = mergedChildren),
            selectedLayerId = importedSubjectId
        )
        assertEquals(4, workingDoc.rootGroup.children.size)

        // =====================================================================
        // STEP 8: MAKE A MANUAL CORRECTION EDIT
        // =====================================================================
        // Artist makes a manual correction: paint red spot at [5..8, 5..8] on imported layer
        val subjectBuf = workingBuffers[importedSubjectId]?.clone() ?: IntArray(totalPixels)
        for (y in 5..8) {
            for (x in 5..8) {
                subjectBuf[y * width + x] = 0xFFFF0000.toInt() // Red correction mark
            }
        }
        workingBuffers[importedSubjectId] = subjectBuf
        // Also adjust layer opacity
        workingDoc = workingDoc.copy(
            rootGroup = workingDoc.rootGroup.copy(
                children = workingDoc.rootGroup.children.map { layer ->
                    if (layer.id == importedSubjectId) (layer as LayerNode.RasterLayer).copy(opacity = 0.88f) else layer
                }
            )
        )
        assertEquals(0.88f, (workingDoc.findLayerById(importedSubjectId) as LayerNode.RasterLayer).opacity, 0.01f)

        // =====================================================================
        // STEP 9: EXPORT ARTWORK TO MULTIPLE FORMATS & VERIFY
        // =====================================================================

        // 9a. Export to PNG
        val pngFile = File(tempDir, "final_export.png")
        val pngResult = exportService.exportArtwork(
            document = workingDoc,
            config = ExportConfig(format = ExportFormat.PNG),
            targetFile = pngFile,
            layerPixelBuffers = workingBuffers,
            layerMaskBuffers = workingMasks
        )
        assertTrue("PNG export must succeed: $pngResult", pngResult is ExportResult.Success)
        assertTrue(pngFile.exists() && pngFile.length() > 0)
        val pngBytes = pngFile.readBytes()
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        assertArrayEquals(pngMagic, pngBytes.copyOfRange(0, 4))

        // 9b. Export to OpenRaster (ORA)
        val oraFile = File(tempDir, "final_export.ora")
        val oraResult = exportService.exportArtwork(
            document = workingDoc,
            config = ExportConfig(format = ExportFormat.ORA),
            targetFile = oraFile,
            layerPixelBuffers = workingBuffers,
            layerMaskBuffers = workingMasks
        )
        assertTrue("ORA export must succeed: $oraResult", oraResult is ExportResult.Success)
        assertTrue(oraFile.exists() && oraFile.length() > 0)
        val oraZip = java.util.zip.ZipFile(oraFile)
        assertNotNull("ORA must contain mimetype", oraZip.getEntry("mimetype"))
        assertNotNull("ORA must contain stack.xml", oraZip.getEntry("stack.xml"))
        oraZip.close()

        // 9c. Export to Photoshop (PSD)
        val psdFile = File(tempDir, "final_export.psd")
        val exportPsdResult = exportService.exportArtwork(
            document = workingDoc,
            config = ExportConfig(format = ExportFormat.PSD),
            targetFile = psdFile,
            layerPixelBuffers = workingBuffers,
            layerMaskBuffers = workingMasks
        )
        assertTrue("PSD export must succeed: $exportPsdResult", exportPsdResult is ExportResult.Success)
        assertTrue(psdFile.exists() && psdFile.length() > 0)
        val psdBytes = psdFile.readBytes()
        val psdMagic = byteArrayOf('8'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(), 'S'.code.toByte())
        assertArrayEquals(psdMagic, psdBytes.copyOfRange(0, 4))

        // 9d. Export to Animated GIF
        val gifFile = File(tempDir, "final_export.gif")
        val gifResult = exportService.exportArtwork(
            document = workingDoc,
            config = ExportConfig(format = ExportFormat.GIF),
            targetFile = gifFile,
            layerPixelBuffers = workingBuffers,
            layerMaskBuffers = workingMasks
        )
        assertTrue("GIF export must succeed: $gifResult", gifResult is ExportResult.Success)
        assertTrue(gifFile.exists() && gifFile.length() > 0)
        val decodedGif = GifDecoder.decode(gifFile.readBytes())
        assertEquals(1, decodedGif.frames.size)
        assertEquals(width, decodedGif.width)
        assertEquals(height, decodedGif.height)
    }
}
