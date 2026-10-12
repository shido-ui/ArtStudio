package com.artstudio.document

import com.artstudio.document.io.*
import com.artstudio.document.io.psd.PsdParser
import com.artstudio.interchange.ora.OpenRasterImporter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gate Test Suite: Hardened Loader & Fuzz Robustness Verification.
 *
 * Verifies:
 * - Truncated, corrupt, and malformed files are rejected safely without crashing.
 * - Zip-bomb files (inflating past caps) are stopped before memory exhaustion.
 * - Oversized canvas dimensions and excessive layer counts are strictly capped.
 * - Stream-based loaders never trigger OutOfMemoryError.
 */
class LoaderHardeningAndFuzzGateTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File.createTempFile("artstudio_fuzz_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // 1. Truncated & Corrupt Packages (.ffart)
    // -------------------------------------------------------------------------

    @Test
    fun testTruncatedFfartPackage_failsGracefullyWithoutCrash() {
        val testFiles = listOf(
            File(tempDir, "empty.ffart").apply { writeBytes(ByteArray(0)) },
            File(tempDir, "one_byte.ffart").apply { writeBytes(byteArrayOf(0x50)) },
            File(tempDir, "partial_zip_header.ffart").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) },
            File(tempDir, "truncated_256.ffart").apply { writeBytes(ByteArray(256) { 0x42 }) }
        )

        for (file in testFiles) {
            val ex = assertThrows(CorruptDocumentException::class.java) {
                DocumentPackage.load(file)
            }
            assertTrue("Exception message must be descriptive for ${file.name}", ex.message!!.isNotEmpty())
        }
    }

    @Test
    fun testCorruptedFfartPackage_randomGarbage_failsGracefullyWithoutCrash() {
        val corruptFile = File(tempDir, "random_garbage.ffart")
        // Fill 64 KB of random non-ZIP garbage
        corruptFile.writeBytes(ByteArray(65536) { (it * 37 % 256).toByte() })

        val ex = assertThrows(CorruptDocumentException::class.java) {
            DocumentPackage.load(corruptFile)
        }
        assertTrue(ex.message!!.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // 2. Zip-Bomb Robustness (Capped Entry & Total Size)
    // -------------------------------------------------------------------------

    @Test
    fun testZipBombPackage_exceedingMaxEntrySize_rejectedSafelyWithoutOom() {
        // Create an archive with an entry declared/compressed that contains repeated zeros
        // exceeding MAX_ENTRY_SIZE if inflated, or craft a zip entry directly.
        val zipBombFile = File(tempDir, "synthetic_zipbomb.ffart")

        FileOutputStream(zipBombFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // Valid mimetype
                val mimeBytes = DocumentPackage.MIMETYPE.toByteArray(Charsets.US_ASCII)
                val mimeEntry = ZipEntry(DocumentPackage.ENTRY_MIMETYPE)
                zos.putNextEntry(mimeEntry)
                zos.write(mimeBytes)
                zos.closeEntry()

                // Giant entry exceeding MAX_ENTRY_SIZE (100 MB cap)
                // Write 101 MB in compressed chunks (compresses down to a few KB)
                val bombEntry = ZipEntry("assets/huge_layer.raw")
                zos.putNextEntry(bombEntry)
                val chunk = ByteArray(1024 * 1024) { 0 } // 1 MB of zeroes
                for (i in 0..101) { // 102 MB total uncompressed
                    zos.write(chunk)
                }
                zos.closeEntry()
            }
        }

        // Must throw CorruptDocumentException and NEVER throw OutOfMemoryError
        val ex = assertThrows(CorruptDocumentException::class.java) {
            DocumentPackage.load(zipBombFile)
        }
        assertTrue(
            "Exception message must mention size limit or zip bomb, was: ${ex.message}",
            ex.message!!.contains("exceeds") || ex.message!!.contains("zip bomb")
        )
    }

    // -------------------------------------------------------------------------
    // 3. Oversized Canvas Dimensions & Layer Counts
    // -------------------------------------------------------------------------

    @Test
    fun testOversizedCanvasDimensions_rejectedSafely() {
        val file = File(tempDir, "oversized_canvas.ffart")
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val mimeBytes = DocumentPackage.MIMETYPE.toByteArray(Charsets.US_ASCII)
                zos.putNextEntry(ZipEntry(DocumentPackage.ENTRY_MIMETYPE))
                zos.write(mimeBytes)
                zos.closeEntry()

                // Oversized 65536 x 65536 canvas
                val json = """
                {
                  "schemaVersion": "2.0.0",
                  "id": "doc_oversized",
                  "title": "Oversized",
                  "canvas": { "width": 65536, "height": 65536 },
                  "rootGroup": { "id": "root", "name": "Root", "children": [] }
                }
                """.trimIndent()
                zos.putNextEntry(ZipEntry(DocumentPackage.ENTRY_PROJECT_JSON))
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }

        val ex = assertThrows(CorruptDocumentException::class.java) {
            DocumentPackage.load(file)
        }
        assertTrue("Must report dimension limits, was: ${ex.message}", ex.message!!.contains("dimension"))
    }

    @Test
    fun testExcessiveLayerCount_rejectedSafely() {
        val file = File(tempDir, "excessive_layers.ffart")
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val mimeBytes = DocumentPackage.MIMETYPE.toByteArray(Charsets.US_ASCII)
                zos.putNextEntry(ZipEntry(DocumentPackage.ENTRY_MIMETYPE))
                zos.write(mimeBytes)
                zos.closeEntry()

                // Build JSON with 501 layers (> MAX_LAYER_COUNT 500)
                val layersJson = (1..505).joinToString(",") { i ->
                    """{ "id": "layer_$i", "name": "L$i", "width": 256, "height": 256, "blendMode": "normal" }"""
                }
                val json = """
                {
                  "schemaVersion": "2.0.0",
                  "id": "doc_many_layers",
                  "title": "Many Layers",
                  "canvas": { "width": 256, "height": 256 },
                  "rootGroup": { "id": "root", "name": "Root", "children": [ $layersJson ] }
                }
                """.trimIndent()
                zos.putNextEntry(ZipEntry(DocumentPackage.ENTRY_PROJECT_JSON))
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }

        val ex = assertThrows(CorruptDocumentException::class.java) {
            DocumentPackage.load(file)
        }
        assertTrue("Must report layer count limit, was: ${ex.message}", ex.message!!.contains("layers"))
    }

    // -------------------------------------------------------------------------
    // 4. OpenRaster Loader Hardening
    // -------------------------------------------------------------------------

    @Test
    fun testTruncatedOpenRaster_failsGracefully() {
        val file = File(tempDir, "truncated.ora").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
        val ex = assertThrows(CorruptDocumentException::class.java) {
            OpenRasterImporter.importOra(file)
        }
        assertTrue(ex.message!!.isNotEmpty())
    }

    @Test
    fun testOpenRasterZipBomb_rejectedSafely() {
        val file = File(tempDir, "ora_zipbomb.ora")
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val mimeBytes = OpenRasterImporter.MIMETYPE_ORA.toByteArray(Charsets.US_ASCII)
                zos.putNextEntry(ZipEntry("mimetype"))
                zos.write(mimeBytes)
                zos.closeEntry()

                val bombEntry = ZipEntry("data/bomb_layer.png")
                zos.putNextEntry(bombEntry)
                val chunk = ByteArray(1024 * 1024) { 0 }
                for (i in 0..101) {
                    zos.write(chunk)
                }
                zos.closeEntry()
            }
        }

        val ex = assertThrows(CorruptDocumentException::class.java) {
            OpenRasterImporter.importOra(file)
        }
        assertTrue(ex.message!!.contains("exceeds") || ex.message!!.contains("zip bomb"))
    }

    // -------------------------------------------------------------------------
    // 5. PSD Loader Hardening
    // -------------------------------------------------------------------------

    @Test
    fun testPsdOversizedDimensions_rejectedSafely() {
        // Construct minimal PSD header with 65536 width
        val baos = ByteArrayOutputStream()
        baos.write("8BPS".toByteArray(Charsets.US_ASCII))
        baos.write(byteArrayOf(0, 1)) // Version 1
        baos.write(ByteArray(6) { 0 }) // Reserved
        baos.write(byteArrayOf(0, 3)) // 3 channels
        // Height 256
        baos.write(byteArrayOf(0, 0, 1, 0))
        // Width 65536 (0x00010000) -> oversized (> 16384)
        baos.write(byteArrayOf(0, 1, 0, 0))
        baos.write(byteArrayOf(0, 8)) // 8-bit depth
        baos.write(byteArrayOf(0, 3)) // ColorMode 3 (RGB)
        // Pad extra
        baos.write(ByteArray(100) { 0 })

        val ex = assertThrows(CorruptDocumentException::class.java) {
            PsdParser.parse(baos.toByteArray())
        }
        assertTrue(ex.message!!.contains("dimensions") || ex.message!!.contains("max"))
    }

    @Test
    fun testPsdStreamExceedingMaxSize_rejectedSafely() {
        // Stream that produces more than 100 MB of data
        val hugeStream = object : java.io.InputStream() {
            private var bytesRead = 0L
            override fun read(): Int {
                bytesRead++
                return 0
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val toRead = len.coerceAtLeast(1)
                bytesRead += toRead
                return toRead
            }
        }

        val ex = assertThrows(CorruptDocumentException::class.java) {
            PsdParser.parse(hugeStream)
        }
        assertTrue(ex.message!!.contains("maximum allowed size"))
    }
}
