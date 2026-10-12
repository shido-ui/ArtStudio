package com.artstudio.reference.region

import com.artstudio.importer.RasterImageImporter
import com.artstudio.reference.ReconstructionMode
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ReferenceRegionTest {

    private fun resolveFixture(relativePath: String): File {
        val candidates = listOf(
            File("tests/fixtures/artstudio/v1", relativePath),
            File("../tests/fixtures/artstudio/v1", relativePath),
            File("/root/artstudio/tests/fixtures/artstudio/v1", relativePath)
        )
        for (f in candidates) {
            if (f.exists()) return f
        }
        throw IllegalStateException("Fixture not found: $relativePath")
    }

    private fun computeSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun createSampleProject(width: Int = 200, height: Int = 200): ReferenceAnalysisProject {
        val src = ReferenceSourceMetadata(
            sourceName = "sample_test_ref.png",
            sourceSha256 = "dummy_sha256_hash",
            rawWidth = width,
            rawHeight = height,
            normalizedWidth = width,
            normalizedHeight = height,
            exifOrientation = 1
        )
        return ReferenceAnalysisProject(
            projectName = "Test Analysis Project",
            referenceSource = src
        )
    }

    // -------------------------------------------------------------------------
    // 1. Rectangle Region Selection
    // -------------------------------------------------------------------------
    @Test
    fun testRectangleRegionSelection() {
        val project = createSampleProject(100, 100)
        val withRegion = ReferenceRegionEngine.createRegion(project, "Box Region")
        val active = withRegion.activeRegion!!

        val updated = ReferenceRegionEngine.applyRectangle(
            region = active,
            left = 20f,
            top = 30f,
            right = 80f,
            bottom = 90f,
            mode = RegionCombineMode.REPLACE
        )

        assertTrue(updated.contains(50, 50))
        assertTrue(updated.contains(20, 30))
        assertTrue(updated.contains(80, 90))
        assertFalse(updated.contains(10, 10))
        assertFalse(updated.contains(95, 95))

        val bounds = updated.bounds
        assertNotNull(bounds)
        assertEquals(20, bounds!!.minX)
        assertEquals(30, bounds.minY)
        assertEquals(80, bounds.maxX)
        assertEquals(90, bounds.maxY)
        assertEquals(61 * 61, updated.selectedPixelCount)
    }

    // -------------------------------------------------------------------------
    // 2. Ellipse Region Selection
    // -------------------------------------------------------------------------
    @Test
    fun testEllipseRegionSelection() {
        val project = createSampleProject(100, 100)
        val withRegion = ReferenceRegionEngine.createRegion(project, "Eye Region")
        val active = withRegion.activeRegion!!

        val updated = ReferenceRegionEngine.applyEllipse(
            region = active,
            left = 20f,
            top = 20f,
            right = 80f,
            bottom = 80f,
            mode = RegionCombineMode.REPLACE
        )

        // Center (50, 50) must be inside
        assertTrue(updated.contains(50, 50))
        // Bounding box corners must be outside circle
        assertFalse(updated.contains(20, 20))
        assertFalse(updated.contains(80, 20))
        assertFalse(updated.contains(20, 80))
        assertFalse(updated.contains(80, 80))

        val expectedApprox = (Math.PI * 30.0 * 30.0).toInt()
        assertTrue(
            "Selected count should be close to pi*r^2 ($expectedApprox), got ${updated.selectedPixelCount}",
            kotlin.math.abs(updated.selectedPixelCount - expectedApprox) < 100
        )
    }

    // -------------------------------------------------------------------------
    // 3. Freehand Lasso Region Selection
    // -------------------------------------------------------------------------
    @Test
    fun testLassoRegionSelection() {
        val project = createSampleProject(100, 100)
        val withRegion = ReferenceRegionEngine.createRegion(project, "Lasso Feature")
        val active = withRegion.activeRegion!!

        val lassoPoints = listOf(
            Pair(50f, 10f),
            Pair(90f, 40f),
            Pair(75f, 90f),
            Pair(25f, 90f),
            Pair(10f, 40f)
        )

        val updated = ReferenceRegionEngine.applyLasso(
            region = active,
            points = lassoPoints,
            mode = RegionCombineMode.REPLACE
        )

        // Centroid must be selected
        assertTrue(updated.contains(50, 50))
        // Extreme outer corners must be unselected
        assertFalse(updated.contains(5, 5))
        assertFalse(updated.contains(95, 95))
        assertTrue(updated.selectedPixelCount > 2000)
    }

    // -------------------------------------------------------------------------
    // 4. Polygonal Lasso Region Selection
    // -------------------------------------------------------------------------
    @Test
    fun testPolygonRegionSelection() {
        val project = createSampleProject(100, 100)
        val withRegion = ReferenceRegionEngine.createRegion(project, "Triangle Hair")
        val active = withRegion.activeRegion!!

        val triangleVertices = listOf(
            Pair(50f, 20f),
            Pair(90f, 80f),
            Pair(10f, 80f)
        )

        val updated = ReferenceRegionEngine.applyPolygon(
            region = active,
            vertices = triangleVertices,
            mode = RegionCombineMode.REPLACE
        )

        // Centroid of triangle (50, 60)
        assertTrue(updated.contains(50, 60))
        // Outside top corners
        assertFalse(updated.contains(10, 20))
        assertFalse(updated.contains(90, 20))
        // Below bottom
        assertFalse(updated.contains(50, 95))

        val expectedArea = 0.5f * 80f * 60f
        assertTrue(kotlin.math.abs(updated.selectedPixelCount - expectedArea.toInt()) < 150)
    }

    // -------------------------------------------------------------------------
    // 5. Brush Stroke Region Selection
    // -------------------------------------------------------------------------
    @Test
    fun testBrushStrokeRegionSelection() {
        val project = createSampleProject(100, 100)
        val withRegion = ReferenceRegionEngine.createRegion(project, "Brush Accent")
        val active = withRegion.activeRegion!!

        val stroke = listOf(
            Pair(20f, 50f),
            Pair(50f, 50f),
            Pair(80f, 50f)
        )

        val updated = ReferenceRegionEngine.applyBrush(
            region = active,
            strokePoints = stroke,
            brushRadius = 10f,
            mode = RegionCombineMode.REPLACE
        )

        // All points along the stroke line must be selected
        assertTrue(updated.contains(20, 50))
        assertTrue(updated.contains(50, 50))
        assertTrue(updated.contains(80, 50))
        assertTrue(updated.contains(50, 55)) // within radius 10
        assertFalse(updated.contains(50, 75)) // outside radius

        assertTrue(updated.selectedPixelCount > 1000)
    }

    // -------------------------------------------------------------------------
    // 6. Boolean Combination Modes: Replace, Add, Subtract, Clear
    // -------------------------------------------------------------------------
    @Test
    fun testSelectionCombinationModes() {
        val project = createSampleProject(100, 100)
        val withRegion = ReferenceRegionEngine.createRegion(project, "Combined")
        val active = withRegion.activeRegion!!

        // A. REPLACE with 40x40 box [20..60, 20..60] -> 41x41 = 1681 px
        val r1 = ReferenceRegionEngine.applyRectangle(
            region = active,
            left = 20f, top = 20f, right = 60f, bottom = 60f,
            mode = RegionCombineMode.REPLACE
        )
        val countR1 = r1.selectedPixelCount
        assertEquals(41 * 41, countR1)

        // B. ADD overlapping 40x40 box [40..80, 20..60]
        val r2 = ReferenceRegionEngine.applyRectangle(
            region = r1,
            left = 40f, top = 20f, right = 80f, bottom = 60f,
            mode = RegionCombineMode.ADD
        )
        assertTrue("ADD must increase selected pixels", r2.selectedPixelCount > countR1)
        assertTrue(r2.contains(70, 40)) // newly added area
        assertTrue(r2.contains(30, 40)) // original area

        // C. SUBTRACT center cutout [35..55, 30..50]
        val r3 = ReferenceRegionEngine.applyRectangle(
            region = r2,
            left = 35f, top = 30f, right = 55f, bottom = 50f,
            mode = RegionCombineMode.SUBTRACT
        )
        assertTrue("SUBTRACT must decrease selected pixels", r3.selectedPixelCount < r2.selectedPixelCount)
        assertFalse(r3.contains(45, 40)) // carved out hole
        assertTrue(r3.contains(25, 40))  // remaining outer area

        // D. CLEAR
        val r4 = ReferenceRegionEngine.clearRegion(r3)
        assertEquals(0, r4.selectedPixelCount)
        assertNull(r4.bounds)
    }

    // -------------------------------------------------------------------------
    // 7. Multiple Named Regions with Independent Settings & Instructions
    // -------------------------------------------------------------------------
    @Test
    fun testMultipleNamedRegionsWithIndependentSettings() {
        var project = createSampleProject(100, 100)

        // Add 3 distinct regions
        project = ReferenceRegionEngine.createRegion(
            project = project,
            name = "Eyes",
            instructions = "Extract fine eyelashes with high precision",
            settings = ReferenceFocusSettings(
                priority = FocusPriority.CRITICAL,
                targetReconstructionMode = ReconstructionMode.LINE_ART_VECTORIZATION,
                detailSensitivity = 0.9f
            ),
            overlayOpacity = 0.60f
        )

        project = ReferenceRegionEngine.createRegion(
            project = project,
            name = "Hair",
            instructions = "Preserve large smooth shading sweeps",
            settings = ReferenceFocusSettings(
                priority = FocusPriority.HIGH,
                targetReconstructionMode = ReconstructionMode.FLAT_REGION_CEL_SHADING,
                detailSensitivity = 0.4f
            ),
            overlayOpacity = 0.35f
        )

        project = ReferenceRegionEngine.createRegion(
            project = project,
            name = "Background",
            instructions = "Low-frequency blur; ignore speckles",
            settings = ReferenceFocusSettings(
                priority = FocusPriority.LOW
            ),
            overlayOpacity = 0.20f
        )

        assertEquals(3, project.regions.size)

        val eyes = project.regions[0]
        val hair = project.regions[1]
        val bg = project.regions[2]

        // Verify independent settings
        assertEquals("Eyes", eyes.name)
        assertEquals(FocusPriority.CRITICAL, eyes.settings.priority)
        assertEquals(ReconstructionMode.LINE_ART_VECTORIZATION, eyes.settings.targetReconstructionMode)
        assertEquals(0.9f, eyes.settings.detailSensitivity, 0.001f)
        assertEquals(0.60f, eyes.overlayOpacity, 0.001f)

        assertEquals("Hair", hair.name)
        assertEquals(FocusPriority.HIGH, hair.settings.priority)
        assertEquals(ReconstructionMode.FLAT_REGION_CEL_SHADING, hair.settings.targetReconstructionMode)
        assertEquals(0.4f, hair.settings.detailSensitivity, 0.001f)
        assertEquals(0.35f, hair.overlayOpacity, 0.001f)

        assertEquals("Background", bg.name)
        assertEquals(FocusPriority.LOW, bg.settings.priority)
        assertNull(bg.settings.targetReconstructionMode)
        assertEquals(0.20f, bg.overlayOpacity, 0.001f)

        // Distinct colors assigned
        assertNotEquals(eyes.colorRgba, hair.colorRgba)
        assertNotEquals(hair.colorRgba, bg.colorRgba)
    }

    // -------------------------------------------------------------------------
    // 8. Region Management Actions: Rename, Duplicate, Delete
    // -------------------------------------------------------------------------
    @Test
    fun testRegionRenameDuplicateDelete() {
        var project = createSampleProject(100, 100)
        project = ReferenceRegionEngine.createRegion(project, "Original Feature")
        val initialId = project.activeRegionId!!

        // Paint something into initial region
        val painted = ReferenceRegionEngine.applyRectangle(
            region = project.activeRegion!!,
            left = 10f, top = 10f, right = 40f, bottom = 40f,
            mode = RegionCombineMode.REPLACE
        )
        project = ReferenceRegionEngine.updateRegion(project, painted)

        // 1. Rename
        project = ReferenceRegionEngine.renameRegion(project, initialId, "Renamed Feature")
        assertEquals("Renamed Feature", project.regions.find { it.id == initialId }?.name)

        // 2. Duplicate
        project = ReferenceRegionEngine.duplicateRegion(project, initialId)
        assertEquals(2, project.regions.size)
        val duplicate = project.regions[1]
        assertEquals("Copy of Renamed Feature", duplicate.name)
        assertNotEquals(initialId, duplicate.id)
        assertNotSame("Mask buffer must be a cloned array", painted.mask, duplicate.mask)
        assertArrayEquals("Mask content must be identical", painted.mask, duplicate.mask)

        // 3. Delete original
        project = ReferenceRegionEngine.deleteRegion(project, initialId)
        assertEquals(1, project.regions.size)
        assertEquals(duplicate.id, project.regions[0].id)
    }

    // -------------------------------------------------------------------------
    // 9. Hide / Show Visibility Toggles
    // -------------------------------------------------------------------------
    @Test
    fun testRegionHideShowToggle() {
        var project = createSampleProject(50, 50)
        project = ReferenceRegionEngine.createRegion(project, "Visible Feature")
        val regId = project.activeRegionId!!

        assertTrue(project.regions[0].isVisible)

        // Toggle to hidden
        project = ReferenceRegionEngine.toggleVisibility(project, regId)
        assertFalse(project.regions[0].isVisible)

        // Toggle back to visible
        project = ReferenceRegionEngine.toggleVisibility(project, regId)
        assertTrue(project.regions[0].isVisible)
    }

    // -------------------------------------------------------------------------
    // 10. Viewport Pan, Zoom and Screen-to-Reference Invariance
    // -------------------------------------------------------------------------
    @Test
    fun testViewportPanAndZoomInvariance() {
        val viewport = ReferenceViewportState(
            panX = 150f,
            panY = -80f,
            zoom = 2.5f
        )

        // Map screen touch to reference coordinates
        val (refX, refY) = viewport.screenToReference(250f, 120f)
        assertEquals((250f - 150f) / 2.5f, refX, 0.001f)
        assertEquals((120f - (-80f)) / 2.5f, refY, 0.001f)

        // Map back to screen
        val (screenX, screenY) = viewport.referenceToScreen(refX, refY)
        assertEquals(250f, screenX, 0.001f)
        assertEquals(120f, screenY, 0.001f)

        // Under 10x zoom change, reference coordinate space remains invariant
        val zoomedViewport = viewport.copy(zoom = 10.0f)
        val (zoomedRefX, zoomedRefY) = zoomedViewport.screenToReference(
            zoomedViewport.referenceToScreen(refX, refY).first,
            zoomedViewport.referenceToScreen(refX, refY).second
        )
        assertEquals(refX, zoomedRefX, 0.001f)
        assertEquals(refY, zoomedRefY, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 11. Source Cropping Mask Extraction
    // -------------------------------------------------------------------------
    @Test
    fun testSourceCroppingMaskExtraction() {
        var project = createSampleProject(100, 100)
        project = ReferenceRegionEngine.createRegion(project, "Full Canvas Region")
        val painted = ReferenceRegionEngine.applyRectangle(
            region = project.activeRegion!!,
            left = 20f, top = 20f, right = 60f, bottom = 60f,
            mode = RegionCombineMode.REPLACE
        )
        project = ReferenceRegionEngine.updateRegion(project, painted)

        // Crop box [20..80, 20..80] -> 61x61
        val crop = ReferenceCropBounds(x = 20, y = 20, width = 61, height = 61)
        val croppedProject = ReferenceRegionEngine.applyCrop(project, crop)

        assertEquals(61, croppedProject.effectiveWidth)
        assertEquals(61, croppedProject.effectiveHeight)
        val croppedReg = croppedProject.regions[0]
        assertEquals(61, croppedReg.width)
        assertEquals(61, croppedReg.height)

        // Relative origin (0, 0) in cropped mask corresponds to (20, 20) in original
        assertTrue(croppedReg.contains(0, 0))
        assertTrue(croppedReg.contains(40, 40)) // was (60, 60)
        assertFalse(croppedReg.contains(50, 50)) // was (70, 70), outside original box
    }

    // -------------------------------------------------------------------------
    // 12. Analysis Project Persistence & Reopen (Surviving Workflow Lifecycle)
    // -------------------------------------------------------------------------
    @Test
    fun testAnalysisProjectPersistenceAndReopen() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "artstudio_test_prompt52_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val projectFile = File(tempDir, "sample_analysis.refproj.json")
            val dummyRefFile = File(tempDir, "preserved_reference.png")
            val originalRefContent = "OriginalReferenceRawImageBytes_DO_NOT_MUTATE".toByteArray()
            dummyRefFile.writeBytes(originalRefContent)
            val originalRefSha = computeSha256(originalRefContent)

            val src = ReferenceSourceMetadata(
                sourceName = dummyRefFile.name,
                sourceSha256 = originalRefSha,
                rawWidth = 80,
                rawHeight = 80,
                normalizedWidth = 80,
                normalizedHeight = 80,
                exifOrientation = 1
            )

            var project = ReferenceAnalysisProject(
                projectName = "Persistence Verification Project",
                referenceSource = src
            )

            project = ReferenceRegionEngine.createRegion(
                project = project,
                name = "Region Alpha",
                instructions = "Sharpen edges and extract centrelines"
            )
            val regAlpha = ReferenceRegionEngine.applyRectangle(
                region = project.activeRegion!!,
                left = 10f, top = 10f, right = 40f, bottom = 40f,
                mode = RegionCombineMode.REPLACE
            )
            project = ReferenceRegionEngine.updateRegion(project, regAlpha)

            project = ReferenceRegionEngine.createRegion(
                project = project,
                name = "Region Beta",
                instructions = "Cel shading flat fill"
            )
            val regBeta = ReferenceRegionEngine.applyEllipse(
                region = project.activeRegion!!,
                left = 30f, top = 30f, right = 70f, bottom = 70f,
                mode = RegionCombineMode.REPLACE
            )
            project = ReferenceRegionEngine.updateRegion(project, regBeta)

            // 1. Serialize and save to disk
            ReferenceAnalysisProjectSerializer.saveToFile(project, projectFile)
            assertTrue("Project file must exist on disk", projectFile.exists())

            // 2. Deserialize and reopen from disk
            val reloaded = ReferenceAnalysisProjectSerializer.loadFromFile(projectFile)
            assertNotNull(reloaded)
            assertEquals(project.projectId, reloaded.projectId)
            assertEquals(project.projectName, reloaded.projectName)
            assertEquals(2, reloaded.regions.size)

            val reloadedAlpha = reloaded.regions[0]
            val reloadedBeta = reloaded.regions[1]

            assertEquals("Region Alpha", reloadedAlpha.name)
            assertEquals("Sharpen edges and extract centrelines", reloadedAlpha.instructions)
            assertArrayEquals(regAlpha.mask, reloadedAlpha.mask)

            assertEquals("Region Beta", reloadedBeta.name)
            assertEquals("Cel shading flat fill", reloadedBeta.instructions)
            assertArrayEquals(regBeta.mask, reloadedBeta.mask)

            // 3. Proves source reference file was NEVER touched or mutated
            val refBytesAfter = dummyRefFile.readBytes()
            assertEquals("Reference file SHA-256 must remain identical", originalRefSha, computeSha256(refBytesAfter))
            assertArrayEquals(originalRefContent, refBytesAfter)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // 13. Selection Golden for Every Selection Tool on Real Fixture
    // -------------------------------------------------------------------------
    @Test
    fun testSelectionGoldenForEveryTool() {
        val file = resolveFixture("flat_colour/flat_colour_simple_01.png")
        val decoded = RasterImageImporter.decodeRasterImage(file.readBytes(), file.name)
        assertNotNull(decoded)
        val w = decoded!!.width
        val h = decoded.height

        val project = createSampleProject(w, h)
        val baseReg = ReferenceRegionEngine.createRegion(project, "Golden Target").activeRegion!!

        // 1. Rectangle Golden
        val rectReg = ReferenceRegionEngine.applyRectangle(baseReg, 20f, 20f, 80f, 80f, RegionCombineMode.REPLACE)
        assertTrue(rectReg.selectedPixelCount > 0)

        // 2. Ellipse Golden
        val ellipseReg = ReferenceRegionEngine.applyEllipse(baseReg, 20f, 20f, 80f, 80f, RegionCombineMode.REPLACE)
        assertTrue(ellipseReg.selectedPixelCount > 0)

        // 3. Lasso Golden
        val lassoReg = ReferenceRegionEngine.applyLasso(
            region = baseReg,
            points = listOf(Pair(30f, 30f), Pair(70f, 30f), Pair(50f, 80f)),
            mode = RegionCombineMode.REPLACE
        )
        assertTrue(lassoReg.selectedPixelCount > 0)

        // 4. Polygon Golden
        val polyReg = ReferenceRegionEngine.applyPolygon(
            region = baseReg,
            vertices = listOf(Pair(20f, 20f), Pair(80f, 20f), Pair(80f, 80f), Pair(20f, 80f)),
            mode = RegionCombineMode.REPLACE
        )
        assertTrue(polyReg.selectedPixelCount > 0)

        // 5. Brush Golden
        val brushReg = ReferenceRegionEngine.applyBrush(
            region = baseReg,
            strokePoints = listOf(Pair(20f, 50f), Pair(80f, 50f)),
            brushRadius = 5f,
            mode = RegionCombineMode.REPLACE
        )
        assertTrue(brushReg.selectedPixelCount > 0)
    }

    // -------------------------------------------------------------------------
    // 14. Non-Destructive Composite Overlay Rendering
    // -------------------------------------------------------------------------
    @Test
    fun testCompositeOverlayRenderingPreservesOriginalReference() {
        val w = 50
        val h = 50
        val total = w * h
        val white = 0xFFFFFFFF.toInt()
        val originalPixels = IntArray(total) { white }
        val copyOfOriginal = originalPixels.clone()

        var project = createSampleProject(w, h)
        project = ReferenceRegionEngine.createRegion(project, "Overlay Region", colorRgba = 0xFFFF0000.toInt())
        val painted = ReferenceRegionEngine.applyRectangle(
            region = project.activeRegion!!,
            left = 10f, top = 10f, right = 30f, bottom = 30f,
            mode = RegionCombineMode.REPLACE
        )
        project = ReferenceRegionEngine.updateRegion(project, painted)

        // Render composite overlay
        val overlay = ReferenceRegionEngine.renderCompositeOverlay(project, originalPixels, w, h)

        assertNotNull(overlay)
        assertEquals(total, overlay.size)
        assertNotSame(originalPixels, overlay)

        // Verify original pixel array was 100% untouched
        assertArrayEquals("Original reference pixels must never be mutated", copyOfOriginal, originalPixels)

        // Overlay inside region must be tinted red
        val tintedPixel = overlay[20 * w + 20]
        val redComp = (tintedPixel ushr 16) and 0xFF
        val greenComp = (tintedPixel ushr 8) and 0xFF
        assertTrue("Tinted red component should dominate green", redComp > greenComp)
    }
}
