package com.artstudio.assets

import com.artstudio.assets.download.AssetDownloadManager
import com.artstudio.assets.download.AssetDownloadState
import com.artstudio.assets.download.AssetPreflightResult
import com.artstudio.assets.download.AssetVerificationException
import com.artstudio.assets.registry.AssetCategory
import com.artstudio.assets.registry.AssetFormat
import com.artstudio.assets.registry.AssetRegistry
import com.artstudio.assets.registry.PublicAssetPack
import com.artstudio.brush.BrushPresetRepository
import com.artstudio.brush.parser.BrushPackImporter
import com.artstudio.brush.parser.KppParseException
import com.artstudio.brush.parser.KppParser
import com.artstudio.brush.parser.MybParseException
import com.artstudio.brush.parser.MybParser
import com.artstudio.comic.ComicAssetRepository
import com.artstudio.core.BrushType
import com.artstudio.core.StampPatternShape
import com.artstudio.text.FontEngine
import com.artstudio.texture.ImageDownsampler
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ServerlessProAssetIntegrationTest {

    private lateinit var tempDir: File
    private lateinit var downloadManager: AssetDownloadManager

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "artstudio_assets_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        downloadManager = AssetDownloadManager(baseStorageDir = tempDir, allowCellular = true, isWifiConnected = true)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 1. Asset Registry Tests
    // =========================================================================

    @Test
    fun testAssetRegistryCompletenessAndCategories() {
        val allPacks = AssetRegistry.getAllPacks()
        assertTrue("Registry must have at least 15 packs, found ${allPacks.size}", allPacks.size >= 15)

        val brushPacks = AssetRegistry.getPacksByCategory(AssetCategory.BRUSH)
        assertEquals(3, brushPacks.size)

        val texPacks = AssetRegistry.getPacksByCategory(AssetCategory.TEXTURE)
        assertEquals(4, texPacks.size)

        val fontPacks = AssetRegistry.getPacksByCategory(AssetCategory.FONT)
        assertEquals(2, fontPacks.size)

        val comicPacks = AssetRegistry.getPacksByCategory(AssetCategory.COMIC)
        assertEquals(3, comicPacks.size)

        val aiPacks = AssetRegistry.getPacksByCategory(AssetCategory.AI_MODEL)
        assertTrue(aiPacks.size >= 4)

        for (pack in allPacks) {
            assertTrue("Pack id must be non-empty", pack.id.isNotBlank())
            assertTrue("Must have at least 2 mirrors for redundancy: ${pack.id}", pack.downloadUrls.size >= 2)
            assertTrue("Size must be positive: ${pack.id}", pack.fileSizeBytes > 0L)
            assertTrue("License must be non-empty: ${pack.id}", pack.license.isNotBlank())
            assertTrue("Source URL must be valid: ${pack.id}", pack.sourceUrl.startsWith("http"))
        }
    }

    @Test
    fun testAssetRegistrySearch() {
        val searchKrita = AssetRegistry.searchPacks("krita")
        assertEquals(1, searchKrita.size)
        assertEquals("brushes.krita.davidrevoy", searchKrita.first().id)

        val searchPaper = AssetRegistry.searchPacks("paper", AssetCategory.TEXTURE)
        assertEquals(1, searchPaper.size)
        assertEquals("textures.ambientcg.paper", searchPaper.first().id)

        val searchEmpty = AssetRegistry.searchPacks("nonexistent_pack_query")
        assertTrue(searchEmpty.isEmpty())
    }

    // =========================================================================
    // 2. Download Manager Tests
    // =========================================================================

    @Test
    fun testDownloadPreflightWifiGating() {
        val wifiOnlyManager = AssetDownloadManager(baseStorageDir = tempDir, allowCellular = false, isWifiConnected = false)
        val pack = AssetRegistry.BRUSH_INK_MANGA

        val result = wifiOnlyManager.performPreflight(pack)
        assertEquals(AssetPreflightResult.WifiRequired, result)
    }

    @Test
    fun testDownloadManagerDirectInstallAndStorage() {
        val pack = AssetRegistry.BRUSH_INK_MANGA
        val fakeBytes = "fake_brushpack_content".toByteArray()

        val installedFile = downloadManager.installDirect(pack, fakeBytes)
        assertTrue(installedFile.exists())
        assertEquals(AssetDownloadState.Installed, downloadManager.getDownloadState(pack))
        assertTrue(downloadManager.isPackInstalled(pack))

        val usedBytes = downloadManager.getPackStorageUsedBytes(pack)
        assertEquals(fakeBytes.size.toLong(), usedBytes)

        val deleted = downloadManager.deletePack(pack)
        assertTrue(deleted)
        assertFalse(downloadManager.isPackInstalled(pack))
        assertEquals(AssetDownloadState.NotDownloaded, downloadManager.getDownloadState(pack))
    }

    // =========================================================================
    // 3. Krita (.kpp) Parser Tests
    // =========================================================================

    @Test
    fun testKppXmlParsingValid() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Preset name="Test Krita Inking G-Pen">
                <param name="brush_size" value="18.5"/>
                <param name="Opacity" value="0.95"/>
                <param name="Flow" value="0.90"/>
                <param name="Spacing" value="0.08"/>
                <param name="brush_tip/shape" value="canvas"/>
                <param name="brush_tip/brush_type" value="autobrush"/>
                <param name="PressureSize" value="true"/>
                <param name="PressureOpacity" value="false"/>
            </Preset>
        """.trimIndent()

        val parsed = KppParser.parseXml(xml, "Fallback")
        assertEquals("Test Krita Inking G-Pen", parsed.preset.name)
        assertEquals(18.5f, parsed.preset.config.size, 0.01f)
        assertEquals(0.855f, parsed.preset.config.opacity, 0.01f) // 0.95 * 0.90
        assertEquals(0.08f, parsed.preset.config.spacing, 0.01f)
        assertEquals(StampPatternShape.CANVAS_GRAIN, parsed.mediaProperties.stampShape)
    }

    @Test(expected = KppParseException::class)
    fun testKppMalformedXmlRejection() {
        val malformed = "<Preset name='unclosed'>Some Text Without Close Tag"
        KppParser.parseXml(malformed)
    }

    @Test
    fun testKppZipContainerParsing() {
        val xml = """<Preset name="Packaged Inker"><param name="brush_size" value="25.0"/></Preset>"""
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("paintoppresets/packaged_inker.kpp"))
            zos.write(xml.toByteArray())
            zos.closeEntry()
        }

        val parsed = KppParser.parse(baos.toByteArray().inputStream(), "packaged_inker.kpp")
        assertEquals("Packaged Inker", parsed.preset.name)
        assertEquals(25.0f, parsed.preset.config.size, 0.01f)
    }

    // =========================================================================
    // 4. MyPaint (.myb) Parser Tests
    // =========================================================================

    @Test
    fun testMybJsonParsingValidV2() {
        val json = """
            {
                "name": "Rough Charcoal MyPaint",
                "settings": {
                    "radius_logarithmic": { "base_value": 2.302585 },
                    "opaque": { "base_value": 0.85 },
                    "opaque_multiply": { "base_value": 1.0 },
                    "dabs_per_second": { "base_value": 20.0 },
                    "smudge": { "base_value": 0.35 },
                    "smudge_length": { "base_value": 0.60 }
                }
            }
        """.trimIndent()

        val parsed = MybParser.parseJson(json, "Fallback")
        assertEquals("Rough Charcoal MyPaint", parsed.preset.name)
        // radius = exp(2.302585) ≈ 10.0, size = 2 * 10 = 20.0
        assertEquals(20.0f, parsed.preset.config.size, 0.5f)
        assertEquals(0.85f, parsed.preset.config.opacity, 0.01f)
        assertEquals(BrushType.INK_BRUSH, parsed.preset.config.type)
        assertEquals(0.1f, parsed.preset.config.spacing, 0.01f) // 1.0 / (20.0 * 0.5) = 0.1
    }

    @Test(expected = MybParseException::class)
    fun testMybMalformedJsonRejection() {
        MybParser.parseJson("{ invalid json without closing bracket")
    }

    // =========================================================================
    // 5. Brush Pack Importer & Zip Slip Tests
    // =========================================================================

    @Test
    fun testBrushPackImporterExtractsAndRegisters() {
        val kppXml = """<Preset name="Zip Krita Pen"><param name="brush_size" value="12.0"/></Preset>"""
        val mybJson = """{"name": "Zip MyPaint Pencil", "settings": {"radius_logarithmic": {"base_value": 1.6094}}}"""

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("presets/pen.kpp"))
            zos.write(kppXml.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("presets/pencil.myb"))
            zos.write(mybJson.toByteArray())
            zos.closeEntry()
        }

        val repo = BrushPresetRepository(storageDir = tempDir)
        val targetDir = File(tempDir, "unpacked_pack")

        val result = BrushPackImporter.importBrushPack(
            inputStream = baos.toByteArray().inputStream(),
            packId = "test_pack",
            targetDir = targetDir,
            repository = repo
        )

        assertEquals("test_pack", result.packId)
        assertEquals(2, result.installedPresets.size)
        assertTrue(repo.getAllPresets().any { it.name == "Zip Krita Pen" })
        assertTrue(repo.getAllPresets().any { it.name == "Zip MyPaint Pencil" })
    }

    @Test(expected = SecurityException::class)
    fun testBrushPackImporterRejectsZipSlip() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/passwd"))
            zos.write("evil".toByteArray())
            zos.closeEntry()
        }

        val targetDir = File(tempDir, "safe_dir")
        BrushPackImporter.importBrushPack(
            inputStream = baos.toByteArray().inputStream(),
            packId = "evil_pack",
            targetDir = targetDir
        )
    }

    // =========================================================================
    // 6. Font Engine & OS/2 Table Embedding Validation Tests
    // =========================================================================

    @Test
    fun testFontEngineBundledFamiliesIncludesBubbleSansAndTomsonTalks() {
        val families = FontEngine.getAllAvailableFamilies()
        assertTrue("Must include Bubble Sans", families.contains("Bubble Sans"))
        assertTrue("Must include Tomson Talks", families.contains("Tomson Talks"))
        assertTrue("Must include Carrois Gothic SC", families.contains("Carrois Gothic SC"))
        assertTrue("Must include Cutive Mono", families.contains("Cutive Mono"))
    }

    @Test
    fun testFontEngineOs2FsTypeParsing() {
        // Synthesize minimal valid OpenType table directory containing OS/2 table
        val os2Data = ByteArray(16)
        os2Data[8] = 0x00
        os2Data[9] = 0x02 // fsType = 0x0002 (Restricted License Embedding)

        val sfntBytes = ByteArray(12 + 16 + 16)
        // sfnt header: numTables = 1
        sfntBytes[4] = 0x00
        sfntBytes[5] = 0x01
        // Table 1: tag = 'OS/2'
        "OS/2".toByteArray(Charsets.US_ASCII).copyInto(sfntBytes, 12)
        // offset = 28 (12 + 16)
        sfntBytes[23] = 28.toByte()

        os2Data.copyInto(sfntBytes, 28)

        val fsType = FontEngine.readFsType(sfntBytes)
        assertEquals(0x0002, fsType)

        val fontFile = File(tempDir, "restricted_test.ttf")
        fontFile.writeBytes(sfntBytes)
        assertTrue(FontEngine.isFontEmbedRestricted(fontFile))
    }

    // =========================================================================
    // 7. Comic Asset Repository Tests
    // =========================================================================

    @Test
    fun testComicAssetRepositoryHasBuiltInTemplates() {
        val comicRepo = ComicAssetRepository(tempDir)
        val panels = comicRepo.getAvailablePanels()
        assertTrue("Must have at least 3 panel templates", panels.size >= 3)
        assertTrue(panels.any { it.name.contains("Full Page") })
        assertTrue(panels.any { it.name.contains("4-Koma") })

        val bubbles = comicRepo.getAvailableBubbles()
        assertTrue("Must have at least 4 speech bubble presets", bubbles.size >= 4)
        assertTrue(bubbles.any { it.name.contains("Dialogue") })
        assertTrue(bubbles.any { it.name.contains("Scream") })
    }

    // =========================================================================
    // 8. Image Downsampler Tests
    // =========================================================================

    @Test
    fun testImageDownsamplerBilinearPixels() {
        val src = IntArray(4 * 4) { 0xFF000000.toInt() }
        val dst = ImageDownsampler.downscalePixelsBilinear(src, 4, 4, 2, 2)
        assertEquals(4, dst.size)
        assertEquals(0xFF000000.toInt(), dst[0])
    }
}
