package com.artstudio.fuzz

import com.artstudio.document.io.CorruptDocumentException
import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.io.DocumentSerializer
import com.artstudio.document.io.UnsupportedSchemaVersionException
import com.artstudio.document.io.psd.PsdDocumentImporter
import com.artstudio.document.io.psd.PsdParser
import com.artstudio.interchange.ora.OpenRasterImporter
import com.artstudio.interchange.ora.OpenRasterStackXml
import com.artstudio.vector.svg.SafeSvgParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Robust format fuzz testing suite executing 10,000 adversarial, corrupted,
 * truncated, and edge-case inputs across PSD, SVG, ORA, and native .ffart formats.
 *
 * GATE VERIFICATION:
 * - 10,000 total inputs (2,500 per format).
 * - Zero unhandled runtime exceptions (IndexOutOfBoundsException, BufferUnderflowException,
 *   NullPointerException, ClassCastException, etc.).
 * - Zero infinite hangs or thread deadlocks.
 * - Zero OutOfMemoryErrors.
 */
class FormatFuzzSuiteTest {

    private val SEED = 0xDEADBEEFL
    private val ITERATIONS_PER_FORMAT = 2500

    @Test
    fun testPsdFuzzSuite_2500Inputs() {
        val random = Random(SEED)
        var handledCleanly = 0
        var corruptRejected = 0
        val startTime = System.currentTimeMillis()

        for (i in 0 until ITERATIONS_PER_FORMAT) {
            val payload = generatePsdFuzzPayload(i, random)
            try {
                // Test both low-level parser and high-level importer
                val doc = PsdParser.parse(payload)
                PsdDocumentImporter.importPsd(ByteArrayInputStream(payload), "fuzz_$i.psd")
                handledCleanly++
            } catch (expected: CorruptDocumentException) {
                corruptRejected++
            } catch (t: Throwable) {
                throw AssertionError("PSD Fuzzer failed at iteration $i with unhandled ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        println("PSD Fuzz Suite: $ITERATIONS_PER_FORMAT inputs tested in ${elapsedMs}ms ($corruptRejected rejected, $handledCleanly accepted)")
        assertEquals(ITERATIONS_PER_FORMAT, handledCleanly + corruptRejected)
        assertTrue("Execution must be bounded", elapsedMs < 30_000L)
    }

    @Test
    fun testSvgFuzzSuite_2500Inputs() {
        val random = Random(SEED + 1)
        var validParses = 0
        var warningsGenerated = 0
        val startTime = System.currentTimeMillis()

        for (i in 0 until ITERATIONS_PER_FORMAT) {
            val svgString = generateSvgFuzzPayload(i, random)
            try {
                val result = SafeSvgParser.parse(svgString)
                if (result.conversionReport.warnings.isNotEmpty()) {
                    warningsGenerated++
                } else {
                    validParses++
                }
            } catch (t: Throwable) {
                throw AssertionError("SVG Fuzzer failed at iteration $i with unhandled ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        println("SVG Fuzz Suite: $ITERATIONS_PER_FORMAT inputs tested in ${elapsedMs}ms ($warningsGenerated warned/sanitized, $validParses parsed)")
        assertEquals(ITERATIONS_PER_FORMAT, validParses + warningsGenerated)
        assertTrue("Execution must be bounded", elapsedMs < 30_000L)
    }

    @Test
    fun testOraFuzzSuite_2500Inputs() {
        val random = Random(SEED + 2)
        var handledCleanly = 0
        var corruptRejected = 0
        val startTime = System.currentTimeMillis()

        for (i in 0 until ITERATIONS_PER_FORMAT) {
            val payload = generateOraFuzzPayload(i, random)
            try {
                val isXmlDirect = (i % 5 == 0)
                if (isXmlDirect) {
                    val xmlStr = String(payload, Charsets.UTF_8)
                    OpenRasterStackXml.parseStackXml(xmlStr)
                } else {
                    OpenRasterImporter.importOra(ByteArrayInputStream(payload), "fuzz_$i.ora")
                }
                handledCleanly++
            } catch (expected: CorruptDocumentException) {
                corruptRejected++
            } catch (expected: SecurityException) {
                corruptRejected++
            } catch (expected: IllegalArgumentException) {
                corruptRejected++
            } catch (t: Throwable) {
                throw AssertionError("ORA Fuzzer failed at iteration $i with unhandled ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        println("ORA Fuzz Suite: $ITERATIONS_PER_FORMAT inputs tested in ${elapsedMs}ms ($corruptRejected rejected, $handledCleanly accepted)")
        assertEquals(ITERATIONS_PER_FORMAT, handledCleanly + corruptRejected)
        assertTrue("Execution must be bounded", elapsedMs < 30_000L)
    }

    @Test
    fun testFfartFuzzSuite_2500Inputs() {
        val random = Random(SEED + 3)
        var handledCleanly = 0
        var corruptRejected = 0
        val startTime = System.currentTimeMillis()

        for (i in 0 until ITERATIONS_PER_FORMAT) {
            val payload = generateFfartFuzzPayload(i, random)
            try {
                val isJsonDirect = (i % 4 == 0)
                if (isJsonDirect) {
                    val jsonStr = String(payload, Charsets.UTF_8)
                    DocumentSerializer.fromJsonString(jsonStr)
                } else {
                    DocumentPackage.load(ByteArrayInputStream(payload))
                }
                handledCleanly++
            } catch (expected: CorruptDocumentException) {
                corruptRejected++
            } catch (expected: UnsupportedSchemaVersionException) {
                corruptRejected++
            } catch (t: Throwable) {
                throw AssertionError("ffart Fuzzer failed at iteration $i with unhandled ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        println("ffart Fuzz Suite: $ITERATIONS_PER_FORMAT inputs tested in ${elapsedMs}ms ($corruptRejected rejected, $handledCleanly accepted)")
        assertEquals(ITERATIONS_PER_FORMAT, handledCleanly + corruptRejected)
        assertTrue("Execution must be bounded", elapsedMs < 30_000L)
    }

    // =========================================================================
    // PAYLOAD GENERATORS
    // =========================================================================

    private fun generatePsdFuzzPayload(index: Int, random: Random): ByteArray {
        return when (index % 10) {
            0 -> ByteArray(0) // Zero bytes
            1 -> ByteArray(random.nextInt(26)) { random.nextInt(256).toByte() } // Truncated header
            2 -> { // Random noise
                val size = random.nextInt(4096) + 26
                ByteArray(size) { random.nextInt(256).toByte() }
            }
            3 -> { // Valid signature, invalid version / channels
                val bb = ByteBuffer.allocate(26).order(ByteOrder.BIG_ENDIAN)
                bb.put(byteArrayOf(0x38, 0x42, 0x50, 0x53)) // 8BPS
                bb.putShort(random.nextInt(10).toShort()) // version
                bb.put(ByteArray(6)) // reserved
                bb.putShort(random.nextInt(20).toShort()) // channels
                bb.putInt(random.nextInt(5000) - 1000) // height
                bb.putInt(random.nextInt(5000) - 1000) // width
                bb.putShort(random.nextInt(32).toShort()) // depth
                bb.putShort(random.nextInt(10).toShort()) // colorMode
                bb.array()
            }
            4 -> { // Valid header, extreme oversized dimensions
                val bb = ByteBuffer.allocate(26 + 100).order(ByteOrder.BIG_ENDIAN)
                bb.put(byteArrayOf(0x38, 0x42, 0x50, 0x53))
                bb.putShort(1) // version 1
                bb.put(ByteArray(6))
                bb.putShort(3) // 3 channels
                bb.putInt(65535) // 65K height
                bb.putInt(65535) // 65K width
                bb.putShort(8) // 8-bit
                bb.putShort(3) // RGB
                // Add corrupt color mode len
                bb.putInt(random.nextInt(1000))
                bb.array()
            }
            5 -> { // Valid header, negative section lengths
                val bb = ByteBuffer.allocate(26 + 16).order(ByteOrder.BIG_ENDIAN)
                bb.put(byteArrayOf(0x38, 0x42, 0x50, 0x53))
                bb.putShort(1)
                bb.put(ByteArray(6))
                bb.putShort(3)
                bb.putInt(100)
                bb.putInt(100)
                bb.putShort(8)
                bb.putShort(3)
                bb.putInt(-1) // negative colorModeLen
                bb.putInt(-1) // negative imgResLen
                bb.array()
            }
            6 -> { // Valid header, huge declared layer count
                val bb = ByteBuffer.allocate(26 + 12 + 100).order(ByteOrder.BIG_ENDIAN)
                bb.put(byteArrayOf(0x38, 0x42, 0x50, 0x53))
                bb.putShort(1)
                bb.put(ByteArray(6))
                bb.putShort(3)
                bb.putInt(100)
                bb.putInt(100)
                bb.putShort(8)
                bb.putShort(3)
                bb.putInt(0) // colorMode
                bb.putInt(0) // imgRes
                bb.putInt(50) // layer section len
                bb.putInt(40) // layer info len
                bb.putShort(10000) // 10k layers (exceeds 500)
                bb.array()
            }
            7 -> { // Corrupted PackBits stream
                val bb = ByteBuffer.allocate(26 + 12 + 64).order(ByteOrder.BIG_ENDIAN)
                bb.put(byteArrayOf(0x38, 0x42, 0x50, 0x53))
                bb.putShort(1)
                bb.put(ByteArray(6))
                bb.putShort(3)
                bb.putInt(10)
                bb.putInt(10)
                bb.putShort(8)
                bb.putShort(3)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0) // no layers
                bb.putShort(1) // compMethod = 1 (RLE)
                // Random truncated RLE data
                for (b in 0 until 50) {
                    bb.put(random.nextInt(256).toByte())
                }
                bb.array()
            }
            8 -> { // Non-RGB color modes
                val bb = ByteBuffer.allocate(26).order(ByteOrder.BIG_ENDIAN)
                bb.put(byteArrayOf(0x38, 0x42, 0x50, 0x53))
                bb.putShort(1)
                bb.put(ByteArray(6))
                bb.putShort(1)
                bb.putInt(64)
                bb.putInt(64)
                bb.putShort(8)
                bb.putShort((random.nextInt(8) + 4).toShort()) // CMYK, Lab, etc.
                bb.array()
            }
            else -> { // Truncated layer record with out-of-bounds Pascal string
                val bb = ByteBuffer.allocate(100).order(ByteOrder.BIG_ENDIAN)
                bb.put(byteArrayOf(0x38, 0x42, 0x50, 0x53))
                bb.putShort(1)
                bb.put(ByteArray(6))
                bb.putShort(3)
                bb.putInt(10)
                bb.putInt(10)
                bb.putShort(8)
                bb.putShort(3)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(40) // layerSectionLen
                bb.putInt(30) // layerInfoLen
                bb.putShort(1) // 1 layer
                bb.putInt(0); bb.putInt(0); bb.putInt(10); bb.putInt(10) // top, left, bottom, right
                bb.putShort(1) // 1 channel
                bb.putShort(0); bb.putInt(10) // ch 0, len 10
                bb.put(byteArrayOf(0x38, 0x42, 0x49, 0x4D)) // 8BIM
                bb.put(byteArrayOf(0x6E, 0x6F, 0x72, 0x6D)) // norm
                bb.put(255.toByte()); bb.put(0); bb.put(0); bb.put(0) // op, clip, flags, pad
                bb.putInt(10) // extraLen
                bb.putInt(0) // maskDataLen
                bb.putInt(0) // blendRangeLen
                bb.put(200.toByte()) // nameLen claiming 200 bytes when buffer ends!
                bb.array()
            }
        }
    }

    private fun generateSvgFuzzPayload(index: Int, random: Random): String {
        return when (index % 10) {
            0 -> "" // Empty string
            1 -> "<svg>" // Unclosed root
            2 -> "not xml at all ${random.nextLong()}"
            3 -> { // DOCTYPE injection
                "<!DOCTYPE svg SYSTEM \"http://127.0.0.1/evil.dtd\"><svg width=\"100\" height=\"100\"><rect width=\"50\" height=\"50\"/></svg>"
            }
            4 -> { // Billion laughs / entity attack
                """
                <!DOCTYPE lolz [
                 <!ENTITY lol "lol">
                 <!ELEMENT lolz (#PCDATA)>
                 <!ENTITY lol1 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                 <!ENTITY lol2 "&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;">
                ]>
                <svg width="100" height="100"><text>&lol2;</text></svg>
                """.trimIndent()
            }
            5 -> { // Malformed path data with excessive dots, signs, exponents
                val dots = ".".repeat(random.nextInt(50) + 1)
                val signs = "+-+-+-+-".repeat(random.nextInt(10) + 1)
                val exps = "1e99999 -1e-99999 NaN Infinity"
                "<svg><path d=\"M $dots $signs $exps Z\"/></svg>"
            }
            6 -> { // Path with endless repeating commands
                val cmds = "M 10 20 L 30 40 C 50 60 70 80 90 100 Q 110 120 130 140 Z ".repeat(random.nextInt(100) + 1)
                "<svg width=\"1024\" height=\"1024\"><path d=\"$cmds\"/></svg>"
            }
            7 -> { // Script injection / inline event handlers
                "<svg><script>alert('xss')</script><circle cx=\"50\" cy=\"50\" r=\"20\" onload=\"evil()\" onclick=\"hack()\"/></svg>"
            }
            8 -> { // Pathological viewBox & transform matrix
                val bigNum = "99999999999999999999"
                "<svg viewBox=\"0 0 $bigNum $bigNum\" transform=\"matrix($bigNum, 0, 0, $bigNum, 0, 0)\"><rect width=\"10\" height=\"10\"/></svg>"
            }
            else -> { // Random unicode and control characters in path and attributes
                val sb = StringBuilder("<svg width=\"100\" height=\"100\"><path d=\"M ")
                for (j in 0 until 50) {
                    sb.append((random.nextInt(128)).toChar())
                }
                sb.append("\"/></svg>")
                sb.toString()
            }
        }
    }

    private fun generateOraFuzzPayload(index: Int, random: Random): ByteArray {
        return when (index % 8) {
            0 -> ByteArray(0) // 0 bytes
            1 -> ByteArray(random.nextInt(1024)) { random.nextInt(256).toByte() } // Random garbage
            2 -> { // Truncated zip header
                byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)
            }
            3 -> { // Zip with directory traversal (Zip Slip)
                createZipPayload(listOf("mimetype" to "image/openraster", "../../evil.txt" to "hack"))
            }
            4 -> { // Missing stack.xml
                createZipPayload(listOf("mimetype" to "image/openraster", "other.txt" to "data"))
            }
            5 -> { // stack.xml with DOCTYPE
                val stackXml = "<!DOCTYPE image SYSTEM \"http://foo.bar\"><image w=\"100\" h=\"100\"><stack/></image>"
                createZipPayload(listOf("mimetype" to "image/openraster", "stack.xml" to stackXml))
            }
            6 -> { // stack.xml with negative/extreme dimensions
                val stackXml = "<image w=\"-100\" h=\"9999999\"><stack><layer name=\"test\" src=\"data/layer.png\"/></stack></image>"
                createZipPayload(listOf("mimetype" to "image/openraster", "stack.xml" to stackXml, "data/layer.png" to "corrupt_png"))
            }
            else -> { // stack.xml with 600 layers (exceeds MAX_LAYER_COUNT)
                val sb = StringBuilder("<image w=\"100\" h=\"100\"><stack>")
                for (j in 0 until 550) {
                    sb.append("<layer name=\"l$j\" src=\"data/$j.png\"/>")
                }
                sb.append("</stack></image>")
                createZipPayload(listOf("mimetype" to "image/openraster", "stack.xml" to sb.toString()))
            }
        }
    }

    private fun generateFfartFuzzPayload(index: Int, random: Random): ByteArray {
        return when (index % 8) {
            0 -> ByteArray(0)
            1 -> ByteArray(random.nextInt(1024)) { random.nextInt(256).toByte() }
            2 -> { // Invalid mimetype
                createZipPayload(listOf("mimetype" to "text/plain", "project.json" to "{}"))
            }
            3 -> { // Missing project.json
                createZipPayload(listOf("mimetype" to DocumentPackage.MIMETYPE, "readme.txt" to "hello"))
            }
            4 -> { // project.json with invalid schema version
                val json = """{"schemaVersion":"99.0.0","id":"doc1","canvas":{"width":100,"height":100}}"""
                createZipPayload(listOf("mimetype" to DocumentPackage.MIMETYPE, "project.json" to json))
            }
            5 -> { // project.json with missing canvas / negative dimensions
                val json = """{"schemaVersion":"2.0.0","id":"doc1","canvas":{"width":-100,"height":0}}"""
                createZipPayload(listOf("mimetype" to DocumentPackage.MIMETYPE, "project.json" to json))
            }
            6 -> { // Malformed JSON syntax in project.json
                val json = """{"schemaVersion":"2.0.0", "canvas": { unclosed json ..."""
                createZipPayload(listOf("mimetype" to DocumentPackage.MIMETYPE, "project.json" to json))
            }
            else -> { // Zip Slip in assets directory
                val json = """{"schemaVersion":"2.0.0","id":"doc1","canvas":{"width":100,"height":100},"rootGroup":{"id":"r","name":"Root","type":"GROUP","children":[]}}"""
                createZipPayload(listOf(
                    "mimetype" to DocumentPackage.MIMETYPE,
                    "project.json" to json,
                    "../../etc/passwd" to "root:x:0:0:root"
                ))
            }
        }
    }

    private fun createZipPayload(entries: List<Pair<String, String>>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val entry = ZipEntry(name)
                if (name == "mimetype") {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    val crc = CRC32()
                    crc.update(bytes)
                    entry.crc = crc.value
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
