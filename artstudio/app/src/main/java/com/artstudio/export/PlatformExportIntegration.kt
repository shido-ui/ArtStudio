package com.artstudio.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Platform integration helper for saving artwork to Android MediaStore / SAF,
 * generating FileProvider sharing intents, and enforcing low-disk-space safeguards.
 */
object PlatformExportIntegration {

    const val FILE_PROVIDER_AUTHORITY = "com.artstudio.fileprovider"
    const val SAFETY_HEADROOM_BYTES = 10 * 1024 * 1024L // 10 MB safety headroom

    data class ExportDestinationInfo(
        val uri: Uri?,
        val file: File?,
        val displayLocation: String,
        val isGalleryVisible: Boolean
    )

    data class StorageHeadroomResult(
        val hasSufficientSpace: Boolean,
        val requiredBytes: Long,
        val availableBytes: Long,
        val failureReason: String? = null
    )

    /**
     * Verifies that the storage target has at least [requiredBytes] plus [safetyHeadroomBytes].
     */
    fun checkStorageHeadroom(
        targetDir: File,
        requiredBytes: Long,
        safetyHeadroomBytes: Long = SAFETY_HEADROOM_BYTES
    ): StorageHeadroomResult {
        val totalNeeded = requiredBytes + safetyHeadroomBytes
        return try {
            val dirToCheck = if (targetDir.exists()) targetDir else targetDir.parentFile ?: File(".")
            val stat = StatFs(dirToCheck.absolutePath)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            if (available < totalNeeded) {
                StorageHeadroomResult(
                    hasSufficientSpace = false,
                    requiredBytes = totalNeeded,
                    availableBytes = available,
                    failureReason = "Low disk space: requires ${formatBytes(totalNeeded)} (including 10 MB safety headroom), but only ${formatBytes(available)} is available."
                )
            } else {
                StorageHeadroomResult(
                    hasSufficientSpace = true,
                    requiredBytes = totalNeeded,
                    availableBytes = available
                )
            }
        } catch (e: Exception) {
            // Fallback to usableSpace check
            val usable = targetDir.usableSpace
            if (usable > 0L && usable < totalNeeded) {
                StorageHeadroomResult(
                    hasSufficientSpace = false,
                    requiredBytes = totalNeeded,
                    availableBytes = usable,
                    failureReason = "Low disk space: requires ${formatBytes(totalNeeded)}, but only ${formatBytes(usable)} available."
                )
            } else {
                StorageHeadroomResult(
                    hasSufficientSpace = true,
                    requiredBytes = totalNeeded,
                    availableBytes = usable
                )
            }
        }
    }

    /**
     * Saves [sourceFile] into the Android system MediaStore so that the exported artwork
     * appears immediately in the device Photos/Gallery app.
     */
    fun saveToMediaStore(
        context: Context,
        sourceFile: File,
        mimeType: String,
        displayName: String,
        subDir: String = "ArtStudio"
    ): Result<ExportDestinationInfo> {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return Result.failure(IOException("Source export file is missing or empty: ${sourceFile.absolutePath}"))
        }

        val isVideo = mimeType.startsWith("video/")
        val resolver = context.contentResolver

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collectionUri = if (isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val relativePath = if (isVideo) "Movies/$subDir" else "Pictures/$subDir"

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val itemUri = resolver.insert(collectionUri, contentValues)
                    ?: return Result.failure(IOException("Failed to create MediaStore entry for $displayName"))

                resolver.openOutputStream(itemUri)?.use { outStream ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                } ?: run {
                    resolver.delete(itemUri, null, null)
                    return Result.failure(IOException("Failed to open output stream for MediaStore item: $itemUri"))
                }

                // Finalize entry by clearing IS_PENDING
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

                Result.success(
                    ExportDestinationInfo(
                        uri = itemUri,
                        file = null,
                        displayLocation = "$relativePath/$displayName",
                        isGalleryVisible = true
                    )
                )
            } else {
                // Legacy storage (API 26-28)
                val baseDir = if (isVideo) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                }
                val albumDir = File(baseDir, subDir).apply { if (!exists()) mkdirs() }
                val destFile = File(albumDir, displayName)

                FileInputStream(sourceFile).use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }

                var scannedUri: Uri? = null
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(mimeType)
                ) { _, uri ->
                    scannedUri = uri
                }

                Result.success(
                    ExportDestinationInfo(
                        uri = scannedUri,
                        file = destFile,
                        displayLocation = destFile.absolutePath,
                        isGalleryVisible = true
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates an [Intent.ACTION_SEND] chooser Intent referencing [exportedFile] via [FileProvider].
     */
    fun createShareIntent(
        context: Context,
        exportedFile: File,
        mimeType: String,
        chooserTitle: String = "Share Artwork"
    ): Intent {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            FILE_PROVIDER_AUTHORITY,
            exportedFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format("%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }
}
