package com.artstudio.export

/**
 * Capability detector and runtime environment probe for animated export formats.
 *
 * Implements ADR-044 architectural decisions:
 * - Animated GIF (GIF89a): GO (Cleanroom Pure-Kotlin streaming encoder, universally supported on Android and JVM).
 * - Raw Frame Sequence (ZIP of PNGs): GO (Universal desktop interchange fallback).
 * - MP4 Video (H.264): GO on Android platform via MediaCodec + MediaMuxer; disabled with truthful explanation on host JVM / headless runners.
 */
object AnimatedExportCapabilities {

    /** Animated GIF is universally supported via first-party pure-Kotlin encoder */
    const val isGifSupported: Boolean = true

    /** Raw PNG frame sequence ZIP is universally supported */
    const val isZipSequenceSupported: Boolean = true

    /**
     * Probes whether hardware-accelerated MP4 video encoding is supported on current runtime.
     * Requires Android MediaCodec and MediaMuxer with video/avc encoder capability.
     */
    val isMp4Supported: Boolean by lazy {
        detectMp4Support()
    }

    /**
     * Explanation string displayed in UI when MP4 export is unavailable on the current platform.
     */
    const val MP4_DISABLED_EXPLANATION = "MP4 video export requires Android MediaCodec hardware encoder on device."

    /**
     * Returns true if the requested format is supported on the current platform.
     */
    fun isFormatSupported(format: ExportFormat): Boolean = when (format) {
        ExportFormat.GIF -> isGifSupported
        ExportFormat.ZIP_SEQUENCE -> isZipSequenceSupported
        ExportFormat.MP4 -> isMp4Supported
        else -> true
    }

    /**
     * Returns human-readable explanation if a format is disabled, or null if enabled.
     */
    fun getDisabledReason(format: ExportFormat): String? = when {
        format == ExportFormat.MP4 && !isMp4Supported -> MP4_DISABLED_EXPLANATION
        else -> null
    }

    private fun detectMp4Support(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val count = android.media.MediaCodecList.getCodecCount()
            if (count <= 0) return false

            for (i in 0 until count) {
                @Suppress("DEPRECATION")
                val info = android.media.MediaCodecList.getCodecInfoAt(i) ?: continue
                if (info.isEncoder) {
                    val types = info.supportedTypes
                    if (types.any { it.equals("video/avc", ignoreCase = true) }) {
                        return true
                    }
                }
            }
            false
        } catch (_: Throwable) {
            // Android SDK / MediaCodec not present (e.g. host JVM test environment)
            false
        }
    }
}
