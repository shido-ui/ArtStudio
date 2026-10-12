package com.artstudio.document.io

/**
 * Exception thrown when a project document is corrupt, truncated, or fails schema validation.
 */
class CorruptDocumentException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when encountering an unknown future schema version that cannot be safely parsed.
 */
class UnsupportedSchemaVersionException(message: String) : Exception(message)
