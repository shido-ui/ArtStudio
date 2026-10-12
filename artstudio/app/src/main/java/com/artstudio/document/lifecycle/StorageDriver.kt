package com.artstudio.document.lifecycle

import com.artstudio.document.io.DocumentPackage
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Storage driver abstraction providing atomic file writes, readback verification,
 * and deterministic fault-injection test seams for low disk space, permission loss,
 * and interrupted writes.
 */
interface StorageDriver {
    /**
     * Executes an atomic write to [targetFile].
     * The [writer] writes to a temporary file. Only after write completion and
     * successful verification is the temporary file atomically renamed to [targetFile].
     * If an error occurs, the temporary file is deleted and [targetFile] is left untouched.
     */
    fun writeAtomic(targetFile: File, writer: (tempFile: File) -> Unit)

    /**
     * Verifies the readback integrity of a written file.
     * For .ffart files, verifies valid ZIP structure and the uncompressed mimetype entry.
     */
    fun verifyIntegrity(file: File): Boolean

    /**
     * Returns available storage space in bytes for the specified directory.
     */
    fun getFreeSpaceBytes(directory: File): Long

    /**
     * Deletes a file safely.
     */
    fun delete(file: File): Boolean

    /**
     * Copies a file from source to destination.
     */
    fun copy(source: File, destination: File)
}

/**
 * Default production implementation using atomic filesystem operations.
 */
class DefaultStorageDriver : StorageDriver {

    override fun writeAtomic(targetFile: File, writer: (tempFile: File) -> Unit) {
        val parentDir = targetFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }

        val tempFile = File.createTempFile("artstudio_atomic_", ".tmp", parentDir)
        var backupFile: File? = null

        try {
            // 1. Perform write to temp file
            writer(tempFile)

            // 2. Mandatory write verification before commit (Never show success before verifying write)
            if (!verifyIntegrity(tempFile)) {
                throw IOException("Write verification failed: corrupt or truncated file produced")
            }

            // 3. Atomic replacement with fail-safe rollback
            if (targetFile.exists()) {
                val bak = File(parentDir, "${targetFile.name}.bak_${System.currentTimeMillis()}")
                if (bak.exists()) bak.delete()
                if (!targetFile.renameTo(bak)) {
                    targetFile.copyTo(bak, overwrite = true)
                    targetFile.delete()
                }
                backupFile = bak
            }

            var commitSuccess = false
            try {
                if (!tempFile.renameTo(targetFile)) {
                    // Cross-device fallback
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                if (!targetFile.exists() || !verifyIntegrity(targetFile)) {
                    throw IOException("Committed target file missing or verification failed")
                }
                commitSuccess = true
            } catch (commitEx: Throwable) {
                // Rollback: restore targetFile from backup if commit failed
                if (backupFile != null && backupFile.exists()) {
                    try {
                        if (targetFile.exists()) targetFile.delete()
                        if (!backupFile.renameTo(targetFile)) {
                            backupFile.copyTo(targetFile, overwrite = true)
                            backupFile.delete()
                        }
                    } catch (_: Throwable) {
                        // Best effort rollback
                    }
                }
                throw commitEx
            } finally {
                // Clean backup file only after verified commit success
                if (commitSuccess && backupFile != null && backupFile.exists()) {
                    backupFile.delete()
                }
            }
        } catch (e: Exception) {
            // Guarantee temp file is cleaned up and target file is preserved
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }

    override fun verifyIntegrity(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false

        // For .ffart packages, verify ZIP structure and valid mimetype
        if (file.name.endsWith(".ffart", ignoreCase = true)) {
            try {
                FileInputStream(file).use { fis ->
                    ZipInputStream(fis).use { zis ->
                        val firstEntry = zis.nextEntry ?: return false
                        if (firstEntry.name != DocumentPackage.ENTRY_MIMETYPE) return false
                        val mimeBytes = ByteArray(firstEntry.size.toInt().coerceAtLeast(32))
                        val readCount = zis.read(mimeBytes)
                        if (readCount <= 0) return false
                        val mimeString = String(mimeBytes, 0, readCount, Charsets.US_ASCII).trim()
                        if (mimeString != DocumentPackage.MIMETYPE) return false
                    }
                }
                return true
            } catch (e: Exception) {
                return false
            }
        }

        return true
    }

    override fun getFreeSpaceBytes(directory: File): Long {
        return directory.usableSpace
    }

    override fun delete(file: File): Boolean {
        return if (file.exists()) file.delete() else true
    }

    override fun copy(source: File, destination: File) {
        source.copyTo(destination, overwrite = true)
    }
}

/**
 * Storage failure test seam simulating disk-full, permission-denied, interrupted write,
 * verification failure, and write latency.
 */
class FaultInjectingStorageDriver(
    private val delegate: StorageDriver = DefaultStorageDriver()
) : StorageDriver {

    var simulateDiskFull: Boolean = false
    var simulatePermissionDenied: Boolean = false
    var simulateInterruptedWrite: Boolean = false
    var simulateVerificationFailure: Boolean = false
    var simulateCommitFailure: Boolean = false
    var simulateWriteDelayMs: Long = 0L
    var freeSpaceOverrideBytes: Long? = null

    var writeAttempts: Int = 0
        private set
    var successfulWrites: Int = 0
        private set
    var failedWrites: Int = 0
        private set
    var cleanedUpTempFiles: Int = 0
        private set

    fun resetStats() {
        writeAttempts = 0
        successfulWrites = 0
        failedWrites = 0
        cleanedUpTempFiles = 0
    }

    override fun writeAtomic(targetFile: File, writer: (tempFile: File) -> Unit) {
        writeAttempts++

        if (simulateWriteDelayMs > 0L) {
            Thread.sleep(simulateWriteDelayMs)
        }

        if (simulatePermissionDenied) {
            failedWrites++
            throw SecurityException("Permission denied: EACCES for destination ${targetFile.absolutePath}")
        }

        if (simulateDiskFull) {
            failedWrites++
            throw IOException("Disk full: No space left on device (ENOSPC)")
        }

        val parentDir = targetFile.parentFile ?: File(".")
        if (!parentDir.exists()) parentDir.mkdirs()

        val tempFile = File.createTempFile("fault_test_", ".tmp", parentDir)
        var backupFile: File? = null

        try {
            if (simulateInterruptedWrite) {
                // Write partial/truncated junk and abort
                tempFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) // Partial ZIP header
                failedWrites++
                throw IOException("Interrupted I/O operation: write stream severed mid-transfer")
            }

            // Normal write
            writer(tempFile)

            if (simulateVerificationFailure) {
                failedWrites++
                throw IOException("Write verification failed: checksum mismatch or truncated header")
            }

            if (!verifyIntegrity(tempFile)) {
                failedWrites++
                throw IOException("Write verification failed: readback corrupted")
            }

            if (simulateCommitFailure) {
                failedWrites++
                throw IOException("Simulated commit failure during target replacement (ENOSPC / power failure)")
            }

            // Atomic replace with rollback protection
            if (targetFile.exists()) {
                val bak = File(parentDir, "${targetFile.name}.fault_bak_${System.currentTimeMillis()}")
                if (bak.exists()) bak.delete()
                if (!targetFile.renameTo(bak)) {
                    targetFile.copyTo(bak, overwrite = true)
                    targetFile.delete()
                }
                backupFile = bak
            }

            var commitSuccess = false
            try {
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                commitSuccess = true
            } catch (commitEx: Throwable) {
                if (backupFile != null && backupFile.exists()) {
                    try {
                        if (targetFile.exists()) targetFile.delete()
                        if (!backupFile.renameTo(targetFile)) {
                            backupFile.copyTo(targetFile, overwrite = true)
                            backupFile.delete()
                        }
                    } catch (_: Throwable) {
                        // Best effort rollback
                    }
                }
                throw commitEx
            } finally {
                if (commitSuccess && backupFile != null && backupFile.exists()) {
                    backupFile.delete()
                }
            }

            successfulWrites++
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
                cleanedUpTempFiles++
            }
            throw e
        }
    }

    override fun verifyIntegrity(file: File): Boolean {
        if (simulateVerificationFailure) return false
        return delegate.verifyIntegrity(file)
    }

    override fun getFreeSpaceBytes(directory: File): Long {
        return freeSpaceOverrideBytes ?: delegate.getFreeSpaceBytes(directory)
    }

    override fun delete(file: File): Boolean {
        return delegate.delete(file)
    }

    override fun copy(source: File, destination: File) {
        delegate.copy(source, destination)
    }
}
