package com.artstudio.texture

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

class TextureAssetManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var userStorageDir: File
    private lateinit var repository: TextureRepository

    @Before
    fun setUp() {
        userStorageDir = tempFolder.newFolder("textures_user_storage")
        repository = TextureRepository(userStorageDir = userStorageDir)
        repository.initialize()
    }

    // -------------------------------------------------------------------------
    // 1. Validation & Import Tests
    // -------------------------------------------------------------------------

    @Test
    fun testImportValidation_valid8BitGrayscalePng_succeeds() {
        val pngBytes = createTestPng(width = 64, height = 64, colorType = 0)
        val result = repository.importAsset(
            name = "Test Gray Texture",
            category = TextureCategory.GRAIN,
            fileBytes = pngBytes,
            sourceFileName = "test_gray.png"
        )

        assertTrue("Import must succeed for valid 8-bit PNG", result is TextureValidationResult.Success)
        val success = result as TextureValidationResult.Success
        assertEquals("Test Gray Texture", success.metadata.name)
        assertEquals(TextureCategory.GRAIN, success.metadata.category)
        assertEquals(64, success.metadata.width)
        assertEquals(64, success.metadata.height)
        assertFalse(success.metadata.isBuiltIn)
        assertNotNull(success.metadata.filePath)
        assertTrue(File(success.metadata.filePath!!).exists())
    }

    @Test
    fun testImportValidation_validRgbaPng_extractsAlphaChannel() {
        val pngBytes = createTestPng(width = 32, height = 32, colorType = 6)
        val result = repository.importAsset(
            name = "Test Stamp RGBA",
            category = TextureCategory.STAMP,
            fileBytes = pngBytes,
            sourceFileName = "test_stamp.png"
        )

        assertTrue("Import must succeed for RGBA PNG", result is TextureValidationResult.Success)
        val success = result as TextureValidationResult.Success
        assertEquals(32, success.metadata.width)
        assertEquals(32, success.metadata.height)
        assertEquals(32 * 32, success.texture.data.size)
    }

    @Test
    fun testImportValidation_oversizedDimensions_rejected() {
        val oversizedBytes = createTestPng(width = 1200, height = 1200, colorType = 0)
        val result = repository.importAsset(
            name = "Oversized Texture",
            category = TextureCategory.PATTERN,
            fileBytes = oversizedBytes,
            sourceFileName = "oversized.png"
        )

        assertTrue("Must reject dimensions > 1024x1024", result is TextureValidationResult.Error)
        val error = result as TextureValidationResult.Error
        assertEquals(TextureErrorCode.DIMENSIONS_TOO_LARGE, error.code)
        assertTrue(error.message.contains("1024"))
    }

    @Test
    fun testImportValidation_undersizedDimensions_rejected() {
        val tinyBytes = createTestPng(width = 4, height = 4, colorType = 0)
        val result = repository.importAsset(
            name = "Tiny Texture",
            category = TextureCategory.GRAIN,
            fileBytes = tinyBytes,
            sourceFileName = "tiny.png"
        )

        assertTrue("Must reject dimensions < 8x8", result is TextureValidationResult.Error)
        val error = result as TextureValidationResult.Error
        assertEquals(TextureErrorCode.DIMENSIONS_TOO_SMALL, error.code)
    }

    @Test
    fun testImportValidation_oversizedFileSize_rejected() {
        // Exceeds 2 MB limit
        val hugeBytes = ByteArray(2 * 1024 * 1024 + 1024) { 0x55.toByte() }
        val result = repository.importAsset(
            name = "Huge File",
            category = TextureCategory.GRAIN,
            fileBytes = hugeBytes,
            sourceFileName = "huge.png"
        )

        assertTrue("Must reject files > 2 MB", result is TextureValidationResult.Error)
        val error = result as TextureValidationResult.Error
        assertEquals(TextureErrorCode.FILE_TOO_LARGE, error.code)
    }

    @Test
    fun testImportValidation_corruptedPng_rejected() {
        val validPng = createTestPng(width = 64, height = 64, colorType = 0)
        // Truncate file abruptly right after IHDR chunk header
        val corruptBytes = validPng.copyOfRange(0, 24)

        val result = repository.importAsset(
            name = "Corrupt Asset",
            category = TextureCategory.STAMP,
            fileBytes = corruptBytes,
            sourceFileName = "corrupt.png"
        )

        assertTrue("Must reject truncated PNG", result is TextureValidationResult.Error)
        val error = result as TextureValidationResult.Error
        assertEquals(TextureErrorCode.CORRUPT_OR_TRUNCATED, error.code)
    }

    @Test
    fun testImportValidation_nonImageFile_rejected() {
        val textBytes = "This is a plain text file, not a texture image!".toByteArray(Charsets.UTF_8)
        val result = repository.importAsset(
            name = "Text File",
            category = TextureCategory.PATTERN,
            fileBytes = textBytes,
            sourceFileName = "invalid.txt"
        )

        assertTrue("Must reject non-image format", result is TextureValidationResult.Error)
        val error = result as TextureValidationResult.Error
        assertEquals(TextureErrorCode.INVALID_HEADER_OR_FORMAT, error.code)
    }

    @Test
    fun testImportValidation_emptyFile_rejected() {
        val emptyBytes = ByteArray(0)
        val result = repository.importAsset(
            name = "Empty File",
            category = TextureCategory.GRAIN,
            fileBytes = emptyBytes,
            sourceFileName = "empty.png"
        )

        assertTrue("Must reject empty file (0 bytes)", result is TextureValidationResult.Error)
        val error = result as TextureValidationResult.Error
        assertEquals(TextureErrorCode.EMPTY_FILE, error.code)
    }

    // -------------------------------------------------------------------------
    // 2. Asset Lifecycle & Management Operations
    // -------------------------------------------------------------------------

    @Test
    fun testAssetLifecycle_duplicateAsset_createsIndependentCopy() {
        val pngBytes = createTestPng(width = 64, height = 64, colorType = 0)
        val importRes = repository.importAsset(
            name = "Original Texture",
            category = TextureCategory.TEXTURED_STROKE,
            fileBytes = pngBytes,
            sourceFileName = "original.png"
        ) as TextureValidationResult.Success

        val dupRes = repository.duplicateAsset(importRes.metadata.id, "Cloned Texture")
        assertTrue("Duplication must succeed", dupRes is TextureValidationResult.Success)

        val dupSuccess = dupRes as TextureValidationResult.Success
        assertEquals("Cloned Texture", dupSuccess.metadata.name)
        assertNotEquals(importRes.metadata.id, dupSuccess.metadata.id)
        assertFalse(dupSuccess.metadata.isBuiltIn)
        assertNotNull(repository.getAssetById(dupSuccess.metadata.id))
    }

    @Test
    fun testAssetLifecycle_renameAsset_succeedsForUser_rejectsBuiltIn() {
        // User asset rename
        val pngBytes = createTestPng(width = 64, height = 64, colorType = 0)
        val importRes = repository.importAsset(
            name = "Initial Name",
            category = TextureCategory.PATTERN,
            fileBytes = pngBytes,
            sourceFileName = "initial.png"
        ) as TextureValidationResult.Success

        val renameResult = repository.renameAsset(importRes.metadata.id, "Updated Name")
        assertTrue(renameResult.isSuccess)
        assertEquals("Updated Name", repository.getAssetById(importRes.metadata.id)?.name)

        // Built-in asset rename must FAIL with CannotModifyBuiltInAssetException
        val builtInId = "builtin_paper_tooth_64"
        val builtInRename = repository.renameAsset(builtInId, "Hacked Builtin")
        assertTrue("Renaming built-in asset must fail", builtInRename.isFailure)
        assertTrue(builtInRename.exceptionOrNull() is CannotModifyBuiltInAssetException)
        assertEquals("Cold-Press Paper Tooth", repository.getAssetById(builtInId)?.name)
    }

    @Test
    fun testAssetLifecycle_deleteAsset_succeedsForUser_rejectsBuiltIn() {
        // User asset deletion
        val pngBytes = createTestPng(width = 64, height = 64, colorType = 0)
        val importRes = repository.importAsset(
            name = "Disposable Texture",
            category = TextureCategory.STAMP,
            fileBytes = pngBytes,
            sourceFileName = "disposable.png"
        ) as TextureValidationResult.Success

        val filePath = importRes.metadata.filePath!!
        assertTrue(File(filePath).exists())

        val deleteResult = repository.deleteAsset(importRes.metadata.id)
        assertTrue(deleteResult.isSuccess)
        assertNull(repository.getAssetById(importRes.metadata.id))
        assertFalse("File must be deleted from disk", File(filePath).exists())

        // Built-in asset deletion must FAIL with CannotDeleteBuiltInAssetException
        val builtInId = "builtin_sponge_stamp_64"
        val builtInDelete = repository.deleteAsset(builtInId)
        assertTrue("Deleting built-in asset must fail", builtInDelete.isFailure)
        assertTrue(builtInDelete.exceptionOrNull() is CannotDeleteBuiltInAssetException)
        assertNotNull(repository.getAssetById(builtInId))
    }

    @Test
    fun testFavorites_togglesFavoriteState() {
        val builtInId = "builtin_canvas_grain_64"
        assertFalse(repository.getAssetById(builtInId)!!.isFavorite)

        // Toggle on
        val favOn = repository.toggleFavorite(builtInId)
        assertTrue(favOn.isSuccess && favOn.getOrThrow())
        assertTrue(repository.getAssetById(builtInId)!!.isFavorite)
        assertTrue(repository.getFavorites().any { it.id == builtInId })

        // Toggle off
        val favOff = repository.toggleFavorite(builtInId)
        assertTrue(favOff.isSuccess && !favOff.getOrThrow())
        assertFalse(repository.getAssetById(builtInId)!!.isFavorite)
    }

    // -------------------------------------------------------------------------
    // 3. Graceful Fallback & Explicit Error Reporting
    // -------------------------------------------------------------------------

    @Test
    fun testMissingAsset_gracefulFallbackWithExplicitError() {
        val nonExistentId = "non_existent_texture_uuid_12345"
        val loadResult = repository.loadTexture(nonExistentId)

        // Must NOT crash or return null
        assertTrue("Must return FallbackWithError on missing asset", loadResult is TextureLoadResult.FallbackWithError)
        val fallback = loadResult as TextureLoadResult.FallbackWithError

        assertEquals(TextureErrorCode.FILE_NOT_FOUND, fallback.error.errorCode)
        assertTrue(fallback.error.userMessage.contains(nonExistentId))
        assertNotNull("Must supply a visible non-blank fallback texture", fallback.fallbackTexture)
        assertEquals(64, fallback.fallbackTexture.width)
        assertEquals(64, fallback.fallbackTexture.height)

        // Repository must expose the error for UI inspection
        assertEquals(fallback.error, repository.lastError)
    }

    // -------------------------------------------------------------------------
    // 4. Lazy-Loaded Previews & Bounded Cache
    // -------------------------------------------------------------------------

    @Test
    fun testLazyPreviewCache_cachesThumbnailsAndPreventsRedundantLoads() {
        val pngBytes = createTestPng(width = 128, height = 128, colorType = 0)
        val importRes = repository.importAsset(
            name = "High Res Texture",
            category = TextureCategory.PATTERN,
            fileBytes = pngBytes,
            sourceFileName = "highres.png"
        ) as TextureValidationResult.Success

        val meta = importRes.metadata
        assertFalse("Preview must not be cached initially", repository.previewCache.isCached(meta))

        // First call generates and caches preview
        val preview1 = repository.getPreview(meta)
        assertEquals(64, preview1.width)
        assertEquals(64, preview1.height)
        assertTrue("Preview must now be cached in memory", repository.previewCache.isCached(meta))

        // Second call retrieves cached instance
        val preview2 = repository.getPreview(meta)
        assertSame("Must return cached instance without reload", preview1, preview2)
    }

    // -------------------------------------------------------------------------
    // 5. Licensing Manifest & Provenance Audit
    // -------------------------------------------------------------------------

    @Test
    fun testBundledManifestParity_allCanonicalAssetsHaveValidLicenseAndChecksum() {
        val canonical = TextureRepository.BUILT_IN_CANONICAL_ASSETS
        assertEquals("Must bundle exactly 7 canonical starter assets", 7, canonical.size)

        for (asset in canonical) {
            assertTrue("ID must not be blank", asset.id.isNotBlank())
            assertTrue("Name must not be blank", asset.name.isNotBlank())
            assertEquals("All bundled assets must have CC0-1.0 license", "CC0-1.0", asset.license)
            assertTrue("Author must be Art Studio Core Team", asset.author.contains("Art Studio"))
            assertTrue("Provenance must be documented", asset.provenance.isNotBlank())
            assertEquals("SHA-256 hash must be 64 hex characters", 64, asset.sha256.length)
            assertTrue("File size must be positive", asset.fileSizeBytes > 0)
        }

        val totalFootprint = canonical.sumOf { it.fileSizeBytes }
        assertTrue("Total built-in texture footprint must be < 100 KB ($totalFootprint bytes)", totalFootprint < 100 * 1024)
    }

    // -------------------------------------------------------------------------
    // Test PNG Synthetic Generator Helper
    // -------------------------------------------------------------------------

    private fun createTestPng(width: Int, height: Int, colorType: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        // PNG Signature
        baos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        // IHDR Chunk
        val ihdrData = ByteArrayOutputStream()
        ihdrData.write(intToBytes(width))
        ihdrData.write(intToBytes(height))
        ihdrData.write(8) // bitDepth
        ihdrData.write(colorType) // colorType
        ihdrData.write(0) // compression
        ihdrData.write(0) // filter
        ihdrData.write(0) // interlace
        writeChunk(baos, "IHDR", ihdrData.toByteArray())

        // IDAT Chunk
        val bpp = when (colorType) {
            0 -> 1
            2 -> 3
            4 -> 2
            6 -> 4
            else -> 1
        }

        val rawLines = ByteArrayOutputStream()
        for (y in 0 until height) {
            rawLines.write(0) // Filter: None
            for (x in 0 until width * bpp) {
                rawLines.write((x + y * 2) and 0xFF)
            }
        }

        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(rawLines.toByteArray())
        deflater.finish()
        val compBuffer = ByteArray(width * height * bpp + 1024)
        val compLen = deflater.deflate(compBuffer)
        deflater.end()

        writeChunk(baos, "IDAT", compBuffer.copyOfRange(0, compLen))

        // IEND Chunk
        writeChunk(baos, "IEND", ByteArray(0))

        return baos.toByteArray()
    }

    private fun writeChunk(baos: ByteArrayOutputStream, type: String, data: ByteArray) {
        baos.write(intToBytes(data.size))
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        baos.write(typeBytes)
        baos.write(data)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        baos.write(intToBytes(crc.value.toInt()))
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }
}
