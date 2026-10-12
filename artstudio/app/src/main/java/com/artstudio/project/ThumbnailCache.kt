package com.artstudio.project

import com.artstudio.document.io.DocumentPackage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.Collections
import java.util.LinkedHashMap
import java.util.zip.ZipInputStream

/**
 * High-performance, bounded LRU thumbnail cache.
 * Extracts ONLY the "thumbnail.png" entry from a .ffart package archive without
 * decompressing or parsing the multi-megabyte project.json or layer bitmaps.
 */
class ThumbnailCache(
    private val maxEntries: Int = 64,
    private val maxMemoryBytes: Long = 16 * 1024 * 1024L // 16 MB ceiling
) {
    private var currentMemoryBytes: Long = 0L

    private val cache: MutableMap<String, ByteArray> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ByteArray>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, ByteArray>?): Boolean {
                val shouldRemove = size > maxEntries || currentMemoryBytes > maxMemoryBytes
                if (shouldRemove && eldest != null) {
                    currentMemoryBytes -= eldest.value.size
                }
                return shouldRemove
            }
        }
    )

    val size: Int
        get() = cache.size

    val memoryBytesUsed: Long
        get() = currentMemoryBytes

    /**
     * Gets cached thumbnail bytes or extracts it from [packageFile].
     * Returns null if no thumbnail entry is present in the package.
     */
    fun getOrExtractThumbnail(projectId: String, packageFile: File?): ByteArray? {
        val cached = cache[projectId]
        if (cached != null) {
            return cached
        }

        if (packageFile == null || !packageFile.exists() || packageFile.length() == 0L) {
            return null
        }

        val extracted = extractThumbnailFromPackage(packageFile) ?: return null
        put(projectId, extracted)
        return extracted
    }

    fun put(projectId: String, thumbnailBytes: ByteArray) {
        val existing = cache.put(projectId, thumbnailBytes)
        if (existing != null) {
            currentMemoryBytes -= existing.size
        }
        currentMemoryBytes += thumbnailBytes.size
    }

    fun get(projectId: String): ByteArray? = cache[projectId]

    fun remove(projectId: String): ByteArray? {
        val removed = cache.remove(projectId)
        if (removed != null) {
            currentMemoryBytes -= removed.size
        }
        return removed
    }

    fun clear() {
        cache.clear()
        currentMemoryBytes = 0L
    }

    /**
     * Extracts strictly the "thumbnail.png" entry from the ZIP file using stream scanning.
     * Stops immediately after finding thumbnail.png without reading other entries.
     */
    fun extractThumbnailFromPackage(file: File): ByteArray? {
        try {
            FileInputStream(file).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == DocumentPackage.ENTRY_THUMBNAIL) {
                            val baos = ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            var len: Int
                            while (zis.read(buffer).also { len = it } != -1) {
                                baos.write(buffer, 0, len)
                            }
                            zis.closeEntry()
                            return baos.toByteArray()
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        return null
    }
}
