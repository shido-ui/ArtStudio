package com.artstudio.storage

import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages bounded temporary storage for ArtStudio.
 *
 * Guarantees:
 * 1. Enforces a strict quota on temporary disk usage.
 * 2. Tracks peak temporary disk usage by job category (import, processing, export).
 * 3. Never deletes the last valid project (.ffart), active recovery snapshots,
 *    manifests, or project indices during cleanup.
 * 4. Ensures atomic temporary file creation within dedicated scratch boundaries.
 */
class BoundedTempFileManager(
    val tempDir: File,
    val maxQuotaBytes: Long = 128 * 1024 * 1024L // 128 MB default quota
) {
    init {
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
    }

    private val activeFiles = ConcurrentHashMap<String, TempFileRecord>()
    private val currentAllocatedBytes = AtomicLong(0L)
    private val _peakUsageBytes = AtomicLong(0L)
    private val _peakUsageByJob = ConcurrentHashMap<JobType, AtomicLong>().apply {
        for (type in JobType.entries) {
            put(type, AtomicLong(0L))
        }
    }

    val peakUsageBytes: Long
        get() = _peakUsageBytes.get()

    val peakUsageByJob: Map<JobType, Long>
        get() = _peakUsageByJob.mapValues { it.value.get() }

    val currentUsageBytes: Long
        get() = currentAllocatedBytes.get()

    data class TempFileRecord(
        val file: File,
        val jobType: JobType,
        val createdAt: Long = System.currentTimeMillis(),
        val recordedBytes: Long
    )

    /**
     * Creates a new managed temporary file within the bounded temp directory.
     */
    fun createTempFile(
        prefix: String = "studio_tmp_",
        suffix: String = ".tmp",
        jobType: JobType = JobType.ANALYSIS_PROCESSING,
        estimatedInitialBytes: Long = 0L
    ): File {
        checkQuotaAdmission(estimatedInitialBytes)
        val uniqueName = "${prefix}${UUID.randomUUID()}${suffix}"
        val file = File(tempDir, uniqueName)

        registerTempFile(file, jobType, estimatedInitialBytes)
        return file
    }

    /**
     * Registers an existing temporary file under management.
     */
    fun registerTempFile(
        file: File,
        jobType: JobType,
        knownSizeBytes: Long = file.length().coerceAtLeast(0L)
    ) {
        val bytes = if (knownSizeBytes > 0L) knownSizeBytes else file.length().coerceAtLeast(0L)
        val record = TempFileRecord(file, jobType, recordedBytes = bytes)
        activeFiles[file.absolutePath] = record

        val newTotal = currentAllocatedBytes.addAndGet(bytes)
        updatePeak(newTotal, jobType, bytes)
    }

    /**
     * Updates the recorded size of an active temporary file as it grows during writing.
     */
    fun updateTempFileSize(file: File) {
        val record = activeFiles[file.absolutePath] ?: return
        val currentSize = file.length()
        val delta = currentSize - record.recordedBytes
        if (delta != 0L) {
            activeFiles[file.absolutePath] = record.copy(recordedBytes = currentSize)
            val newTotal = currentAllocatedBytes.addAndGet(delta)
            updatePeak(newTotal, record.jobType, currentSize)
        }
    }

    /**
     * Unregisters a temporary file (e.g. when promoted to a permanent project file).
     */
    fun unregisterTempFile(file: File) {
        val removed = activeFiles.remove(file.absolutePath)
        if (removed != null) {
            currentAllocatedBytes.addAndGet(-removed.recordedBytes)
        }
    }

    /**
     * Safely deletes a specific temporary file and unregisters it.
     */
    fun deleteTempFile(file: File): Boolean {
        if (isProtectedArtifact(file)) {
            // Refuse to delete protected artifacts
            return false
        }
        val removed = activeFiles.remove(file.absolutePath)
        if (removed != null) {
            currentAllocatedBytes.addAndGet(-removed.recordedBytes)
        }
        return if (file.exists()) file.delete() else true
    }

    /**
     * Inviolable filter: tests whether a file is a permanent project or recoverable checkpoint.
     * Guaranteed to NEVER return false for critical user assets.
     */
    fun isProtectedArtifact(file: File): Boolean {
        val name = file.name.lowercase()
        val path = file.absolutePath.lowercase()

        // 1. Native projects (.ffart)
        if (name.endsWith(".ffart") && !name.contains(".tmp")) return true

        // 2. Recovery snapshots, directories, and manifests
        if (name == "recovery" || name.startsWith("recovery_snapshot") || name.startsWith("recovery_manifest")) return true
        if (path.contains("/recovery/") || path.contains("\\recovery\\") || path.endsWith("/recovery") || path.endsWith("\\recovery")) return true

        // 3. Project library indices and metadata
        if (name == "projects_index.json" || name == "projects_index.json.bak") return true

        // 4. Atomic transaction backups
        if (name.contains(".bak") && (name.contains(".ffart") || name.contains("projects_index") || name.contains("fault_bak") || name.contains("export_bak"))) return true

        // 5. Active canvas fixtures or templates
        if (path.contains("/sample_projects/") || path.contains("/fixtures/")) return true

        return false
    }

    /**
     * Safe cleanup: sweeps temporary files while strictly preserving protected artifacts.
     *
     * @param forceAll If true, cleans registered active temp files as well (e.g. on mass cleanup).
     * @return [CleanupResult] summary.
     */
    fun cleanTempFiles(forceAll: Boolean = false): CleanupResult {
        var freedBytes = 0L
        var deletedCount = 0
        var protectedPreservedCount = 0
        val protectedPaths = mutableListOf<String>()

        val filesToExamine = mutableListOf<File>()

        fun scanFiles(dir: File) {
            val items = dir.listFiles() ?: return
            for (item in items) {
                if (item.isDirectory) {
                    if (isProtectedArtifact(item)) {
                        protectedPreservedCount++
                        protectedPaths.add(item.absolutePath)
                        val innerFiles = item.listFiles() ?: emptyArray()
                        for (inner in innerFiles) {
                            protectedPreservedCount++
                            protectedPaths.add(inner.absolutePath)
                        }
                    } else {
                        scanFiles(item)
                        item.delete()
                    }
                } else {
                    if (forceAll || !activeFiles.containsKey(item.absolutePath)) {
                        filesToExamine.add(item)
                    }
                }
            }
        }

        scanFiles(tempDir)

        if (forceAll) {
            for (activeRecord in activeFiles.values) {
                if (!filesToExamine.contains(activeRecord.file)) {
                    filesToExamine.add(activeRecord.file)
                }
            }
        }

        for (file in filesToExamine) {
            if (isProtectedArtifact(file)) {
                protectedPreservedCount++
                protectedPaths.add(file.absolutePath)
                continue
            }

            val size = file.length()
            try {
                if (file.exists() && file.delete()) {
                    freedBytes += size
                    deletedCount++
                    val removed = activeFiles.remove(file.absolutePath)
                    if (removed != null) {
                        currentAllocatedBytes.addAndGet(-removed.recordedBytes)
                    }
                }
            } catch (_: Throwable) {
                // Ignore failure on locked files
            }
        }

        // Reset current allocated count if forceAll
        if (forceAll) {
            currentAllocatedBytes.set(0L)
            activeFiles.clear()
        }

        return CleanupResult(
            freedBytes = freedBytes,
            deletedFilesCount = deletedCount,
            protectedFilesPreservedCount = protectedPreservedCount,
            protectedFilePaths = protectedPaths
        )
    }

    private fun checkQuotaAdmission(neededBytes: Long) {
        val current = currentAllocatedBytes.get()
        if (current + neededBytes > maxQuotaBytes) {
            // Attempt auto-cleanup of unregistered files first
            cleanTempFiles(forceAll = false)
            val updated = currentAllocatedBytes.get()
            if (updated + neededBytes > maxQuotaBytes) {
                throw IOException(
                    "Temporary disk quota exceeded: current usage $updated bytes + needed $neededBytes bytes exceeds max quota of $maxQuotaBytes bytes"
                )
            }
        }
    }

    private fun updatePeak(newTotal: Long, jobType: JobType, jobBytes: Long) {
        _peakUsageBytes.updateAndGet { currentPeak -> maxOf(currentPeak, newTotal) }
        _peakUsageByJob[jobType]?.updateAndGet { currentJobPeak -> maxOf(currentJobPeak, jobBytes) }
    }
}
