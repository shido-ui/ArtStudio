package com.artstudio.text

import android.content.Context
import android.graphics.Typeface
import java.util.Locale

/**
 * Result of font resolution query including fallback status and diagnostic report.
 */
data class FontResolutionResult(
    val requestedFamily: String,
    val resolvedFamily: String,
    val fallbackOccurred: Boolean,
    val report: FontFallbackReport? = null
)

/**
 * Font Engine and Resolution Subsystem for Art Studio.
 *
 * Coordinates system typography, bundled open font assets, graceful fallback arbitration,
 * and diagnostic reporting when requested typefaces are unavailable.
 */
object FontEngine {

    const val DEFAULT_FALLBACK_FAMILY = "sans-serif"

    /**
     * Standard Android system font families guaranteed to resolve on Android runtime.
     */
    val SYSTEM_FAMILIES = listOf(
        "sans-serif",
        "serif",
        "monospace",
        "cursive",
        "casual"
    )

    /**
     * Permissively licensed bundled fonts embedded in app assets (SIL Open Font License 1.1).
     */
    val BUNDLED_FAMILIES = mapOf(
        "Carrois Gothic SC" to "fonts/CarroisGothicSC-Regular.ttf",
        "Cutive Mono" to "fonts/CutiveMono.ttf",
        "Bubble Sans" to "fonts/BubbleSans.otf",
        "Tomson Talks" to "fonts/TomsonTalks.ttf"
    )

    /**
     * Case-insensitive alias map for common system typefaces.
     */
    private val ALIAS_MAP = mapOf(
        "roboto" to "sans-serif",
        "droid sans" to "sans-serif",
        "droid sans mono" to "monospace",
        "courier" to "monospace",
        "times" to "serif",
        "times new roman" to "serif",
        "arial" to "sans-serif",
        "helvetica" to "sans-serif",
        "carrois gothic" to "Carrois Gothic SC",
        "carroisgothic" to "Carrois Gothic SC",
        "cutive" to "Cutive Mono",
        "cutivemono" to "Cutive Mono"
    )

    private val externalFonts = mutableMapOf<String, RegisteredFont>()

    /**
     * Returns a complete list of all supported font family names.
     */
    fun getAllAvailableFamilies(): List<String> {
        val list = mutableListOf<String>()
        list.addAll(SYSTEM_FAMILIES)
        list.addAll(BUNDLED_FAMILIES.keys)
        synchronized(externalFonts) {
            list.addAll(externalFonts.values.map { it.family })
        }
        return list.distinct()
    }

    /**
     * Checks if a font family is known and supported on the platform or in bundled assets.
     */
    fun isFontAvailable(family: String?): Boolean {
        if (family.isNullOrBlank()) return false
        val clean = family.trim().lowercase(Locale.ROOT)
        if (SYSTEM_FAMILIES.any { it.equals(clean, ignoreCase = true) }) return true
        if (BUNDLED_FAMILIES.keys.any { it.equals(clean, ignoreCase = true) }) return true
        if (ALIAS_MAP.containsKey(clean)) return true
        synchronized(externalFonts) {
            if (externalFonts.containsKey(clean)) return true
        }
        return false
    }

    fun isFontEmbedRestricted(file: java.io.File): Boolean {
        if (!file.exists() || !file.isFile) return false
        val fsType = readFsType(file.readBytes()) ?: return false
        return ((fsType and 0x0002) != 0) && ((fsType and 0x0200) == 0)
    }

    /**
     * Registers an external .ttf/.otf font file, validating embedding rights via OS/2 fsType.
     */
    fun registerFontFile(file: java.io.File, packId: String? = null): RegisteredFont {
        require(file.exists() && file.isFile) { "Font file does not exist: ${file.absolutePath}" }
        val bytes = file.readBytes()
        val isRestricted = isFontEmbedRestricted(file)
        val familyName = extractFamilyName(bytes) ?: file.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()

        val reg = RegisteredFont(
            family = familyName,
            file = file,
            isEmbedRestricted = isRestricted,
            packId = packId
        )
        synchronized(externalFonts) {
            externalFonts[familyName.lowercase(Locale.ROOT)] = reg
        }
        return reg
    }

    /**
     * Reads fsType from TrueType / OpenType OS/2 table.
     */
    fun readFsType(bytes: ByteArray): Int? {
        if (bytes.size < 12) return null
        val numTables = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        for (i in 0 until numTables) {
            val recOffset = 12 + i * 16
            if (recOffset + 16 > bytes.size) break
            val tag = String(bytes, recOffset, 4, Charsets.US_ASCII)
            if (tag == "OS/2") {
                val tableOffset = ((bytes[recOffset + 8].toLong() and 0xFF) shl 24 or
                        ((bytes[recOffset + 9].toLong() and 0xFF) shl 16) or
                        ((bytes[recOffset + 10].toLong() and 0xFF) shl 8) or
                        (bytes[recOffset + 11].toLong() and 0xFF)).toInt()
                if (tableOffset + 10 <= bytes.size) {
                    return ((bytes[tableOffset + 8].toInt() and 0xFF) shl 8) or (bytes[tableOffset + 9].toInt() and 0xFF)
                }
            }
        }
        return null
    }

    /**
     * Extracts Font Family Name from TrueType / OpenType name table.
     */
    fun extractFamilyName(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        val numTables = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        for (i in 0 until numTables) {
            val recOffset = 12 + i * 16
            if (recOffset + 16 > bytes.size) break
            val tag = String(bytes, recOffset, 4, Charsets.US_ASCII)
            if (tag == "name") {
                val tableOffset = ((bytes[recOffset + 8].toLong() and 0xFF) shl 24 or
                        ((bytes[recOffset + 9].toLong() and 0xFF) shl 16) or
                        ((bytes[recOffset + 10].toLong() and 0xFF) shl 8) or
                        (bytes[recOffset + 11].toLong() and 0xFF)).toInt()
                if (tableOffset + 6 > bytes.size) return null
                val count = ((bytes[tableOffset + 2].toInt() and 0xFF) shl 8) or (bytes[tableOffset + 3].toInt() and 0xFF)
                val stringOffset = tableOffset + (((bytes[tableOffset + 4].toInt() and 0xFF) shl 8) or (bytes[tableOffset + 5].toInt() and 0xFF))
                for (j in 0 until count) {
                    val nRecOffset = tableOffset + 6 + j * 12
                    if (nRecOffset + 12 > bytes.size) break
                    val nameID = ((bytes[nRecOffset + 6].toInt() and 0xFF) shl 8) or (bytes[nRecOffset + 7].toInt() and 0xFF)
                    if (nameID == 1) { // Font Family name
                        val length = ((bytes[nRecOffset + 8].toInt() and 0xFF) shl 8) or (bytes[nRecOffset + 9].toInt() and 0xFF)
                        val offset = stringOffset + (((bytes[nRecOffset + 10].toInt() and 0xFF) shl 8) or (bytes[nRecOffset + 11].toInt() and 0xFF))
                        if (offset + length <= bytes.size && length > 0) {
                            val raw = bytes.copyOfRange(offset, offset + length)
                            val str = if (raw.size >= 2 && raw[0] == 0.toByte()) {
                                String(raw, Charsets.UTF_16BE)
                            } else {
                                String(raw, Charsets.UTF_8)
                            }.trim()
                            if (str.isNotBlank()) return str
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Resolves a requested font family with fallback arbitration and diagnostic audit reporting.
     */
    fun resolveFont(requestedFamily: String, style: TextFontStyle = TextFontStyle.NORMAL): FontResolutionResult {
        val clean = requestedFamily.trim()
        val lower = clean.lowercase(Locale.ROOT)

        // 1. Direct system font match
        val systemMatch = SYSTEM_FAMILIES.firstOrNull { it.equals(lower, ignoreCase = true) }
        if (systemMatch != null) {
            return FontResolutionResult(clean, systemMatch, fallbackOccurred = false)
        }

        // 2. Direct bundled font match
        val bundledMatch = BUNDLED_FAMILIES.keys.firstOrNull { it.equals(clean, ignoreCase = true) }
        if (bundledMatch != null) {
            return FontResolutionResult(clean, bundledMatch, fallbackOccurred = false)
        }

        // 3. Known alias match
        val aliasTarget = ALIAS_MAP[lower]
        if (aliasTarget != null) {
            return FontResolutionResult(clean, aliasTarget, fallbackOccurred = false)
        }

        // 4. External registered font match
        synchronized(externalFonts) {
            val extMatch = externalFonts[lower]
            if (extMatch != null) {
                return FontResolutionResult(clean, extMatch.family, fallbackOccurred = false)
            }
        }

        // 5. Unavailable font: Graceful Fallback with Diagnostic Report
        val reason = "Font family '$clean' is not installed or available on this system; substituted with default '$DEFAULT_FALLBACK_FAMILY'."
        val report = FontFallbackReport(
            requestedFamily = clean,
            resolvedFamily = DEFAULT_FALLBACK_FAMILY,
            fallbackOccurred = true,
            reason = reason
        )
        return FontResolutionResult(
            requestedFamily = clean,
            resolvedFamily = DEFAULT_FALLBACK_FAMILY,
            fallbackOccurred = true,
            report = report
        )
    }

    /**
     * Creates or loads an Android [Typeface] with defensive exception handling and fallback.
     */
    fun createTypeface(context: Context?, family: String, style: TextFontStyle = TextFontStyle.NORMAL): Typeface {
        val resolution = resolveFont(family, style)
        val targetFamily = resolution.resolvedFamily

        // Check external registered fonts first
        synchronized(externalFonts) {
            val extFont = externalFonts[targetFamily.lowercase(Locale.ROOT)]
            if (extFont != null && extFont.file.exists()) {
                try {
                    return Typeface.createFromFile(extFont.file)
                } catch (_: Exception) {}
            }
        }

        // Check bundled assets
        val assetPath = BUNDLED_FAMILIES[targetFamily]
        if (context != null && assetPath != null) {
            try {
                return Typeface.createFromAsset(context.assets, assetPath)
            } catch (_: Exception) {
                // Fall back to system typeface if asset reading fails
            }
        }

        // System typeface
        return try {
            Typeface.create(targetFamily, style.androidStyle) ?: Typeface.DEFAULT
        } catch (_: Throwable) {
            Typeface.DEFAULT
        }
    }
}

/**
 * Metadata record for a dynamically registered font file.
 */
data class RegisteredFont(
    val family: String,
    val file: java.io.File,
    val isEmbedRestricted: Boolean,
    val packId: String? = null
)
