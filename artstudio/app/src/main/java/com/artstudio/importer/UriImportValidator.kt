package com.artstudio.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Validates external URIs and securely reads import data under bounded resource limits.
 *
 * Guarantees:
 * - Strictly whitelists URI schemes ('content' and 'file') to prevent SSRF / scheme injection.
 * - Enforces bounded memory streaming (max 50 MB) to prevent OutOfMemoryErrors on malicious streams.
 * - Extracts and validates display name and MIME type safely.
 */
object UriImportValidator {

    private const val TAG = "UriImportValidator"
    const val DEFAULT_MAX_IMPORT_BYTES = 50 * 1024 * 1024 // 50 MB hard cap

    sealed class ValidationResult {
        data class Valid(
            val bytes: ByteArray,
            val mimeType: String?,
            val displayName: String
        ) : ValidationResult() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as Valid
                if (!bytes.contentEquals(other.bytes)) return false
                if (mimeType != other.mimeType) return false
                if (displayName != other.displayName) return false
                return true
            }

            override fun hashCode(): Int {
                var result = bytes.contentHashCode()
                result = 31 * result + (mimeType?.hashCode() ?: 0)
                result = 31 * result + displayName.hashCode()
                return result
            }
        }

        data class Rejected(val reason: String) : ValidationResult()
    }

    /**
     * Validates that [uri] has an approved scheme, verifies permissions, and reads
     * the stream up to [maxBytes] without unbounded heap allocation.
     */
    fun validateAndRead(
        context: Context,
        uri: Uri?,
        maxBytes: Int = DEFAULT_MAX_IMPORT_BYTES
    ): ValidationResult {
        if (uri == null) {
            return ValidationResult.Rejected("URI cannot be null.")
        }

        val uriString = uri.toString()
        val scheme = uri.scheme?.lowercase()
            ?: if (uriString.contains("://")) uriString.substringBefore("://").lowercase() else null

        if (scheme != "content" && scheme != "file") {
            Log.w(TAG, "Rejected untrusted URI scheme: $scheme for $uri")
            return ValidationResult.Rejected("Untrusted or unsupported URI scheme '$scheme'. Only 'content' and 'file' are permitted.")
        }

        val displayName = uri.lastPathSegment?.substringAfterLast('/')
            ?: uriString.substringAfterLast('/').ifEmpty { "imported_artwork" }

        return try {
            val resolver = context.contentResolver
            val mimeType = try {
                resolver.getType(uri)
            } catch (e: Exception) {
                Log.w(TAG, "Could not determine MIME type for $uri: ${e.message}")
                null
            }

            val inputStream = resolver.openInputStream(uri)
                ?: return ValidationResult.Rejected("Unable to open input stream for URI: $uri")

            val bytes = inputStream.use { stream ->
                readBytesBounded(stream, maxBytes)
            } ?: return ValidationResult.Rejected("File exceeds maximum allowed import size (${maxBytes / (1024 * 1024)} MB).")

            if (bytes.isEmpty()) {
                return ValidationResult.Rejected("Imported file is empty (0 bytes).")
            }

            ValidationResult.Valid(
                bytes = bytes,
                mimeType = mimeType,
                displayName = displayName
            )
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException reading URI $uri: ${se.message}")
            ValidationResult.Rejected("Permission denied accessing URI: ${se.message}")
        } catch (e: Exception) {
            Log.w(TAG, "I/O exception reading URI $uri: ${e.message}")
            ValidationResult.Rejected("I/O error reading URI: ${e.message}")
        }
    }

    /**
     * Reads up to [maxBytes] from [inputStream].
     * Returns null if stream length strictly exceeds [maxBytes].
     */
    fun readBytesBounded(inputStream: InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var totalBytes = 0
        var count: Int
        while (inputStream.read(buffer).also { count = it } != -1) {
            totalBytes += count
            if (totalBytes > maxBytes) {
                return null
            }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
