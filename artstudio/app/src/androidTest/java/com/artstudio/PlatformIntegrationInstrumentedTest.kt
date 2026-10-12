package com.artstudio

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artstudio.color.ColorPixelConvention
import com.artstudio.core.CanvasMemoryBudgetValidator
import com.artstudio.core.DeviceMemoryPolicy
import com.artstudio.core.DeviceRamTier
import com.artstudio.export.PlatformExportIntegration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Authoritative instrumented test suite validating real Android framework integrations:
 * - MediaStore saving & gallery visibility
 * - FileProvider content URI generation & sharing intent
 * - Canvas memory budget deriving from ActivityManager and clean rejection
 * - sRGB ColorSpace Bitmap creation
 * - Storage headroom safeguards
 */
@RunWith(AndroidJUnit4::class)
class PlatformIntegrationInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testExportVisibleInGallery_viaMediaStore() {
        // Create a real test PNG export file
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val testFile = File(exportsDir, "gallery_test_${System.currentTimeMillis()}.png")

        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        FileOutputStream(testFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue(testFile.exists())
        assertTrue(testFile.length() > 0)

        // Save via MediaStore
        val saveResult = PlatformExportIntegration.saveToMediaStore(
            context = context,
            sourceFile = testFile,
            mimeType = "image/png",
            displayName = testFile.name
        )

        assertTrue("MediaStore export must succeed", saveResult.isSuccess)
        val destInfo = saveResult.getOrThrow()
        assertTrue("Export must be flagged gallery-visible", destInfo.isGalleryVisible)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertNotNull("MediaStore URI must be generated on API 29+", destInfo.uri)
            val uri = destInfo.uri!!
            assertTrue("URI must be MediaStore content URI", uri.toString().startsWith("content://media/"))

            // Verify content stream is readable by other apps/gallery
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(8)
                val read = stream.read(header)
                assertEquals(8, read)
                // PNG magic number check
                assertEquals(0x89.toByte(), header[0])
                assertEquals(0x50.toByte(), header[1]) // 'P'
                assertEquals(0x4E.toByte(), header[2]) // 'N'
                assertEquals(0x47.toByte(), header[3]) // 'G'
            } ?: fail("Could not open InputStream for exported MediaStore URI: $uri")
        }
    }

    @Test
    fun testFileProviderShareIntent_generatesValidContentUri() {
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val testFile = File(exportsDir, "share_test_${System.currentTimeMillis()}.png")
        testFile.writeText("fake-image-bytes-for-share")

        val chooserIntent = PlatformExportIntegration.createShareIntent(
            context = context,
            exportedFile = testFile,
            mimeType = "image/png",
            chooserTitle = "Share Artwork"
        )

        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        val sendIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull("Target send intent must be present", sendIntent)
        assertEquals(Intent.ACTION_SEND, sendIntent!!.action)
        assertEquals("image/png", sendIntent.type)

        val streamUri = sendIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull("Stream URI must not be null", streamUri)
        assertTrue(
            "Stream URI must use FileProvider authority com.artstudio.fileprovider",
            streamUri!!.toString().startsWith("content://com.artstudio.fileprovider/")
        )
        assertTrue(
            "Send intent must grant URI read permission",
            sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        )
    }

    @Test
    fun testCanvasAboveMemoryLimit_isRejectedCleanly() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        assertNotNull(am)
        assertTrue("Memory class must be positive", am.memoryClass > 0)

        val budget = CanvasMemoryBudgetValidator.deriveMemoryBudget(context)
        assertTrue(budget.maxDimensionPx in 2048..8192)

        // 1. Valid dimension is accepted
        val acceptedResult = CanvasMemoryBudgetValidator.validateCanvas(1024, 1024, layerCount = 2, context = context)
        assertTrue("Valid 1024x1024 canvas must be accepted", acceptedResult is CanvasMemoryBudgetValidator.CanvasValidationResult.Accepted)

        // 2. Outrageous canvas dimensions (16384x16384) must be rejected cleanly
        val rejectedResult = CanvasMemoryBudgetValidator.validateCanvas(16384, 16384, layerCount = 1, context = context)
        assertTrue("Oversized 16384x16384 canvas must be rejected", rejectedResult is CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected)
        val rejected = rejectedResult as CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected
        assertTrue("Rejection reason must explain memory constraints", rejected.rejectionReason.contains("exceeds maximum supported dimension"))

        // 3. Excess layer count must be rejected cleanly
        val excessLayers = CanvasMemoryBudgetValidator.validateCanvas(2048, 2048, layerCount = 100, context = context)
        assertTrue("100 layers on 2048x2048 must be rejected", excessLayers is CanvasMemoryBudgetValidator.CanvasValidationResult.Rejected)
    }

    @Test
    fun testStorageHeadroomCheck() {
        val filesDir = context.filesDir
        val checkNormal = PlatformExportIntegration.checkStorageHeadroom(filesDir, 1024L)
        assertTrue("1KB should easily fit within storage", checkNormal.hasSufficientSpace)

        // Requesting 10,000 Terabytes must fail cleanly
        val checkGigantic = PlatformExportIntegration.checkStorageHeadroom(filesDir, 10_000_000_000_000_000L)
        assertFalse("10,000 TB must fail storage headroom check", checkGigantic.hasSufficientSpace)
        assertNotNull(checkGigantic.failureReason)
        assertTrue(checkGigantic.failureReason!!.contains("Low disk space"))
    }

    @Test
    fun testColorSpaceExplicitSrgb() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888, true, srgb)
            assertEquals(srgb, bitmap.colorSpace)
            assertEquals(ColorPixelConvention.COLOR_SPACE_NAME, "sRGB IEC61966-2.1")
        }
    }
}
