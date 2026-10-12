package com.artstudio.document.lifecycle

import com.artstudio.document.io.ArtJson
import com.artstudio.document.io.DocumentPackage
import com.artstudio.document.io.PackageData
import com.artstudio.document.model.ArtDocument
import java.io.File
import java.io.IOException

/**
 * Manifest record stored alongside recovery snapshots in the recovery journal.
 */
data class RecoveryManifest(
    val projectId: String,
    val originalFilePath: String,
    val timestamp: Long,
    val revision: Long,
    val isCommitted: Boolean
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "projectId" to projectId,
        "originalFilePath" to originalFilePath,
        "timestamp" to timestamp,
        "revision" to revision,
        "isCommitted" to isCommitted
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): RecoveryManifest {
            return RecoveryManifest(
                projectId = map["projectId"]?.toString() ?: "",
                originalFilePath = map["originalFilePath"]?.toString() ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                revision = (map["revision"] as? Number)?.toLong() ?: 0L,
                isCommitted = (map["isCommitted"] as? Boolean) ?: false
            )
        }
    }
}

/**
 * Manages crash recovery journals and temporary shadow writes.
 * Ensures unsaved artwork survives sudden process death, battery exhaustion, or interruptions
 * without modifying the original document until explicit user commitment.
 *
 * Golden Rule: Never delete recovery material silently!
 */
class DocumentRecoveryManager(
    val recoveryRootDir: File,
    private val storageDriver: StorageDriver = DefaultStorageDriver()
) {

    init {
        if (!recoveryRootDir.exists()) {
            recoveryRootDir.mkdirs()
        }
    }

    private fun getProjectRecoveryDir(projectId: String): File {
        return File(recoveryRootDir, projectId).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getSnapshotFile(projectId: String): File {
        return File(getProjectRecoveryDir(projectId), "recovery_snapshot.ffart")
    }

    fun getManifestFile(projectId: String): File {
        return File(getProjectRecoveryDir(projectId), "recovery_manifest.json")
    }

    /**
     * Checks if uncommitted recovery material exists for [projectId].
     * Returns RecoveryAvailable if a recovery snapshot exists that is uncommitted
     * or newer than [originalFile].
     */
    fun checkRecovery(projectId: String, originalFile: File?): DocumentSaveState.RecoveryAvailable? {
        val snapshotFile = getSnapshotFile(projectId)
        val manifestFile = getManifestFile(projectId)

        if (!snapshotFile.exists() || snapshotFile.length() == 0L || !manifestFile.exists()) {
            return null
        }

        val manifest = readManifest(manifestFile) ?: return null

        // If marked committed and not newer than originalFile, no recovery needed
        val originalTimestamp = if (originalFile?.exists() == true) originalFile.lastModified() else 0L
        if (manifest.isCommitted && manifest.timestamp <= originalTimestamp) {
            return null
        }

        // Verify snapshot integrity
        if (!storageDriver.verifyIntegrity(snapshotFile)) {
            return null
        }

        return DocumentSaveState.RecoveryAvailable(
            timestamp = manifest.timestamp,
            recoveryFile = snapshotFile,
            backupRevision = manifest.revision
        )
    }

    /**
     * Writes an atomic recovery snapshot and journal manifest.
     */
    fun writeRecoverySnapshot(
        document: ArtDocument,
        assets: Map<String, ByteArray>,
        originalFile: File,
        revision: Long
    ) {
        val snapshotFile = getSnapshotFile(document.id)
        val manifestFile = getManifestFile(document.id)

        // 1. Write snapshot package atomically
        storageDriver.writeAtomic(snapshotFile) { tempFile ->
            DocumentPackage.save(document, assets, tempFile)
        }

        // 2. Write manifest atomically
        val manifest = RecoveryManifest(
            projectId = document.id,
            originalFilePath = originalFile.absolutePath,
            timestamp = System.currentTimeMillis(),
            revision = revision,
            isCommitted = false
        )
        val manifestJson = ArtJson.stringify(manifest.toMap())

        storageDriver.writeAtomic(manifestFile) { tempFile ->
            tempFile.writeText(manifestJson, Charsets.UTF_8)
        }
    }

    /**
     * Marks the recovery manifest committed after a verified manual save.
     */
    fun markCommitted(projectId: String, revision: Long) {
        val manifestFile = getManifestFile(projectId)
        if (!manifestFile.exists()) return

        val existing = readManifest(manifestFile) ?: return
        val updated = existing.copy(
            revision = revision,
            timestamp = System.currentTimeMillis(),
            isCommitted = true
        )
        val manifestJson = ArtJson.stringify(updated.toMap())

        try {
            storageDriver.writeAtomic(manifestFile) { tempFile ->
                tempFile.writeText(manifestJson, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            // Non-critical if commit flag write fails; snapshot is preserved
        }
    }

    /**
     * Restores artwork package data from the recovery snapshot.
     */
    fun restoreRecovery(projectId: String): PackageData {
        val snapshotFile = getSnapshotFile(projectId)
        if (!snapshotFile.exists() || snapshotFile.length() == 0L) {
            throw IOException("No recovery snapshot file found for project $projectId")
        }
        return DocumentPackage.load(snapshotFile)
    }

    /**
     * Discards recovery material ONLY upon explicit user instruction.
     */
    fun discardRecovery(projectId: String): Boolean {
        val projectRecoveryDir = getProjectRecoveryDir(projectId)
        val snapshot = getSnapshotFile(projectId)
        val manifest = getManifestFile(projectId)

        var deleted = true
        if (snapshot.exists()) deleted = storageDriver.delete(snapshot) && deleted
        if (manifest.exists()) deleted = storageDriver.delete(manifest) && deleted
        if (projectRecoveryDir.exists()) projectRecoveryDir.delete()

        return deleted
    }

    private fun readManifest(file: File): RecoveryManifest? {
        return try {
            val json = file.readText(Charsets.UTF_8)
            val parsed = ArtJson.parse(json) as? Map<String, Any?> ?: return null
            RecoveryManifest.fromMap(parsed)
        } catch (_: Exception) {
            null
        }
    }
}
