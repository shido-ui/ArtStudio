package com.artstudio.color

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Supported color space specifications for canvas rendering and interchange.
 */
enum class ColorSpaceType(val id: String, val displayName: String) {
    SRGB("srgb", "sRGB (Standard Dynamic Range)"),
    LINEAR_SRGB("linear_srgb", "Linear sRGB"),
    DISPLAY_P3("display_p3", "Display P3 Wide Gamut (Approximation)"),
    CMYK_UNPROOFED("cmyk_unproofed", "Device CMYK (Uncalibrated Print Approximation)");

    companion object {
        fun fromId(id: String): ColorSpaceType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SRGB
    }
}

/**
 * Official warning and specification regarding CMYK print accuracy.
 * Art Studio enforces transparent disclosure: naive subtractive CMYK conversions
 * do not substitute for an ICC profile colour management pipeline.
 */
object PrintColorNotice {
    const val DISCLAIMER: String =
        "NOTICE: Device CMYK values are uncalibrated display approximations generated " +
        "without ICC output profiles. Art Studio does NOT claim press-ready or print-accurate " +
        "CMYK proofing until full ICC device profiles and soft-proofing rendering intents are loaded."

    fun isPrintAccurate(space: ColorSpaceType): Boolean {
        return false // Always false until true ICC profile soft-proofing engine is loaded
    }
}

/**
 * 4-component subtractive CMYK representation with alpha channel.
 * Values are normalized in [0.0, 1.0].
 */
data class CmykData(
    val cyan: Float,
    val magenta: Float,
    val yellow: Float,
    val black: Float,
    val alpha: Float = 1.0f
) {
    val isPrintAccurate: Boolean = false
    val disclaimer: String = PrintColorNotice.DISCLAIMER

    fun toColorData(): ColorData = ColorConversions.cmykToRgb(this)
}

/**
 * Authoritative, immutable 32-bit/float RGBA color data container.
 * Components r, g, b, a are normalized in [0.0, 1.0].
 */
data class ColorData(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1.0f
) {
    init {
        require(r in -0.001f..1.001f) { "Red component must be in [0, 1], got $r" }
        require(g in -0.001f..1.001f) { "Green component must be in [0, 1], got $g" }
        require(b in -0.001f..1.001f) { "Blue component must be in [0, 1], got $b" }
        require(a in -0.001f..1.001f) { "Alpha component must be in [0, 1], got $a" }
    }

    // Clamped canonical values
    val red: Float get() = r.coerceIn(0f, 1f)
    val green: Float get() = g.coerceIn(0f, 1f)
    val blue: Float get() = b.coerceIn(0f, 1f)
    val alpha: Float get() = a.coerceIn(0f, 1f)

    // 8-bit integer channel representations [0, 255]
    val redInt: Int get() = (red * 255f).roundToInt().coerceIn(0, 255)
    val greenInt: Int get() = (green * 255f).roundToInt().coerceIn(0, 255)
    val blueInt: Int get() = (blue * 255f).roundToInt().coerceIn(0, 255)
    val alphaInt: Int get() = (alpha * 255f).roundToInt().coerceIn(0, 255)

    // Packed 32-bit ARGB (standard Android / Skia format)
    val argbInt: Int
        get() = ((alphaInt and 0xFF) shl 24) or
                ((redInt and 0xFF) shl 16) or
                ((greenInt and 0xFF) shl 8) or
                (blueInt and 0xFF)

    // Packed 32-bit RGBA
    val rgbaInt: Int
        get() = ((redInt and 0xFF) shl 24) or
                ((greenInt and 0xFF) shl 16) or
                ((blueInt and 0xFF) shl 8) or
                (alphaInt and 0xFF)

    // Hex String representations
    val hexRgb: String
        get() = String.format(Locale.US, "#%02X%02X%02X", redInt, greenInt, blueInt)

    val hexArgb: String
        get() = String.format(Locale.US, "#%02X%02X%02X%02X", alphaInt, redInt, greenInt, blueInt)

    val hexRgba: String
        get() = String.format(Locale.US, "#%02X%02X%02X%02X", redInt, greenInt, blueInt, alphaInt)

    // HSV representation: hue in [0, 360), saturation in [0, 1], value in [0, 1]
    val hsv: FloatArray
        get() = ColorConversions.rgbToHsv(red, green, blue)

    val hue: Float get() = hsv[0]
    val saturation: Float get() = hsv[1]
    val value: Float get() = hsv[2]

    // HSL representation: hue in [0, 360), saturation in [0, 1], lightness in [0, 1]
    val hsl: FloatArray
        get() = ColorConversions.rgbToHsl(red, green, blue)

    val hslSaturation: Float get() = hsl[1]
    val lightness: Float get() = hsl[2]

    // Alpha premultiplication
    fun toPremultiplied(): ColorData {
        val aClamped = alpha
        return ColorData(red * aClamped, green * aClamped, blue * aClamped, aClamped)
    }

    fun fromPremultiplied(): ColorData {
        val aClamped = alpha
        if (aClamped <= 1e-6f) return ColorData(0f, 0f, 0f, 0f)
        return ColorData(
            (red / aClamped).coerceIn(0f, 1f),
            (green / aClamped).coerceIn(0f, 1f),
            (blue / aClamped).coerceIn(0f, 1f),
            aClamped
        )
    }

    // Gamma conversion (sRGB <-> Linear RGB)
    fun toLinearRgb(): ColorData {
        return ColorData(
            ColorConversions.srgbToLinear(red),
            ColorConversions.srgbToLinear(green),
            ColorConversions.srgbToLinear(blue),
            alpha
        )
    }

    fun fromLinearRgb(): ColorData {
        return ColorData(
            ColorConversions.linearToSrgb(red),
            ColorConversions.linearToSrgb(green),
            ColorConversions.linearToSrgb(blue),
            alpha
        )
    }

    // CMYK approximation
    fun toCmyk(): CmykData = ColorConversions.rgbToCmyk(this)

    // Linear interpolation
    fun lerp(other: ColorData, t: Float, inLinearSpace: Boolean = false): ColorData {
        val factor = t.coerceIn(0f, 1f)
        return if (inLinearSpace) {
            val c1 = toLinearRgb()
            val c2 = other.toLinearRgb()
            val lr = c1.red + (c2.red - c1.red) * factor
            val lg = c1.green + (c2.green - c1.green) * factor
            val lb = c1.blue + (c2.blue - c1.blue) * factor
            val la = c1.alpha + (c2.alpha - c1.alpha) * factor
            ColorData(lr, lg, lb, la).fromLinearRgb()
        } else {
            ColorData(
                red + (other.red - red) * factor,
                green + (other.green - green) * factor,
                blue + (other.blue - blue) * factor,
                alpha + (other.alpha - alpha) * factor
            )
        }
    }

    // Euclidean RGB distance
    fun deltaRgb(other: ColorData): Float {
        val dr = red - other.red
        val dg = green - other.green
        val db = blue - other.blue
        return sqrt(dr * dr + dg * dg + db * db)
    }

    companion object {
        val BLACK = ColorData(0f, 0f, 0f, 1f)
        val WHITE = ColorData(1f, 1f, 1f, 1f)
        val TRANSPARENT = ColorData(0f, 0f, 0f, 0f)
        val RED = ColorData(1f, 0f, 0f, 1f)
        val GREEN = ColorData(0f, 1f, 0f, 1f)
        val BLUE = ColorData(0f, 0f, 1f, 1f)
        val YELLOW = ColorData(1f, 1f, 0f, 1f)
        val CYAN = ColorData(0f, 1f, 1f, 1f)
        val MAGENTA = ColorData(1f, 0f, 1f, 1f)
        val GRAY = ColorData(0.5f, 0.5f, 0.5f, 1f)

        fun fromArgb(argb: Int): ColorData {
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            return ColorData(r, g, b, a)
        }

        fun fromRgbInt(r: Int, g: Int, b: Int, a: Int = 255): ColorData {
            return ColorData(
                r.coerceIn(0, 255) / 255f,
                g.coerceIn(0, 255) / 255f,
                b.coerceIn(0, 255) / 255f,
                a.coerceIn(0, 255) / 255f
            )
        }

        fun fromHsv(h: Float, s: Float, v: Float, a: Float = 1.0f): ColorData {
            val rgb = ColorConversions.hsvToRgb(h, s, v)
            return ColorData(rgb[0], rgb[1], rgb[2], a.coerceIn(0f, 1f))
        }

        fun fromHsl(h: Float, s: Float, l: Float, a: Float = 1.0f): ColorData {
            val rgb = ColorConversions.hslToRgb(h, s, l)
            return ColorData(rgb[0], rgb[1], rgb[2], a.coerceIn(0f, 1f))
        }

        fun fromHex(hex: String): ColorData {
            val clean = hex.trim().removePrefix("#")
            return when (clean.length) {
                3 -> { // RGB (12-bit) e.g. "F00" -> "FF0000"
                    val r = clean.substring(0, 1).repeat(2).toInt(16) / 255f
                    val g = clean.substring(1, 2).repeat(2).toInt(16) / 255f
                    val b = clean.substring(2, 3).repeat(2).toInt(16) / 255f
                    ColorData(r, g, b, 1.0f)
                }
                4 -> { // RGBA (16-bit) e.g. "F008"
                    val r = clean.substring(0, 1).repeat(2).toInt(16) / 255f
                    val g = clean.substring(1, 2).repeat(2).toInt(16) / 255f
                    val b = clean.substring(2, 3).repeat(2).toInt(16) / 255f
                    val a = clean.substring(3, 4).repeat(2).toInt(16) / 255f
                    ColorData(r, g, b, a)
                }
                6 -> { // RRGGBB e.g. "FF5500"
                    val r = clean.substring(0, 2).toInt(16) / 255f
                    val g = clean.substring(2, 4).toInt(16) / 255f
                    val b = clean.substring(4, 6).toInt(16) / 255f
                    ColorData(r, g, b, 1.0f)
                }
                8 -> { // AARRGGBB or RRGGBBAA. Art Studio standard: AARRGGBB unless detected otherwise
                    val c1 = clean.substring(0, 2).toInt(16) / 255f
                    val c2 = clean.substring(2, 4).toInt(16) / 255f
                    val c3 = clean.substring(4, 6).toInt(16) / 255f
                    val c4 = clean.substring(6, 8).toInt(16) / 255f
                    // Interpret as ARGB
                    ColorData(c2, c3, c4, c1)
                }
                else -> throw IllegalArgumentException("Invalid hex color length: '$hex' (expected 3, 4, 6, or 8 characters)")
            }
        }
    }
}

/**
 * Pure-math color conversion algorithms.
 * Guarantees precision, zero platform Android dependencies, and unit-testable consistency.
 */
object ColorConversions {

    /**
     * Converts sRGB [0, 1] to HSV: H in [0, 360), S in [0, 1], V in [0, 1].
     */
    fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val rClamped = r.coerceIn(0f, 1f)
        val gClamped = g.coerceIn(0f, 1f)
        val bClamped = b.coerceIn(0f, 1f)

        val maxVal = max(rClamped, max(gClamped, bClamped))
        val minVal = min(rClamped, min(gClamped, bClamped))
        val delta = maxVal - minVal

        var h = 0f
        val s = if (maxVal > 1e-6f) delta / maxVal else 0f
        val v = maxVal

        if (delta > 1e-6f) {
            h = when (maxVal) {
                rClamped -> 60f * (((gClamped - bClamped) / delta) % 6f)
                gClamped -> 60f * (((bClamped - rClamped) / delta) + 2f)
                else -> 60f * (((rClamped - gClamped) / delta) + 4f)
            }
            if (h < 0f) h += 360f
        }

        return floatArrayOf(h, s, v)
    }

    /**
     * Converts HSV to sRGB [0, 1].
     */
    fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        val hNorm = ((h % 360f) + 360f) % 360f
        val sClamped = s.coerceIn(0f, 1f)
        val vClamped = v.coerceIn(0f, 1f)

        val c = vClamped * sClamped
        val x = c * (1f - abs((hNorm / 60f) % 2f - 1f))
        val m = vClamped - c

        val (rPrime, gPrime, bPrime) = when {
            hNorm < 60f -> Triple(c, x, 0f)
            hNorm < 120f -> Triple(x, c, 0f)
            hNorm < 180f -> Triple(0f, c, x)
            hNorm < 240f -> Triple(0f, x, c)
            hNorm < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return floatArrayOf(
            (rPrime + m).coerceIn(0f, 1f),
            (gPrime + m).coerceIn(0f, 1f),
            (bPrime + m).coerceIn(0f, 1f)
        )
    }

    /**
     * Converts sRGB [0, 1] to HSL: H in [0, 360), S in [0, 1], L in [0, 1].
     */
    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val rClamped = r.coerceIn(0f, 1f)
        val gClamped = g.coerceIn(0f, 1f)
        val bClamped = b.coerceIn(0f, 1f)

        val maxVal = max(rClamped, max(gClamped, bClamped))
        val minVal = min(rClamped, min(gClamped, bClamped))
        val delta = maxVal - minVal

        val l = (maxVal + minVal) / 2f
        var h = 0f
        val s = if (delta < 1e-6f) {
            0f
        } else {
            delta / (1f - abs(2f * l - 1f))
        }

        if (delta > 1e-6f) {
            h = when (maxVal) {
                rClamped -> 60f * (((gClamped - bClamped) / delta) % 6f)
                gClamped -> 60f * (((bClamped - rClamped) / delta) + 2f)
                else -> 60f * (((rClamped - gClamped) / delta) + 4f)
            }
            if (h < 0f) h += 360f
        }

        return floatArrayOf(h, s, l)
    }

    /**
     * Converts HSL to sRGB [0, 1].
     */
    fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
        val hNorm = ((h % 360f) + 360f) % 360f
        val sClamped = s.coerceIn(0f, 1f)
        val lClamped = l.coerceIn(0f, 1f)

        val c = (1f - abs(2f * lClamped - 1f)) * sClamped
        val x = c * (1f - abs((hNorm / 60f) % 2f - 1f))
        val m = lClamped - c / 2f

        val (rPrime, gPrime, bPrime) = when {
            hNorm < 60f -> Triple(c, x, 0f)
            hNorm < 120f -> Triple(x, c, 0f)
            hNorm < 180f -> Triple(0f, c, x)
            hNorm < 240f -> Triple(0f, x, c)
            hNorm < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return floatArrayOf(
            (rPrime + m).coerceIn(0f, 1f),
            (gPrime + m).coerceIn(0f, 1f),
            (bPrime + m).coerceIn(0f, 1f)
        )
    }

    /**
     * Standard IEC 61966-2-1 sRGB to Linear RGB gamma companding.
     */
    fun srgbToLinear(c: Float): Float {
        val v = c.coerceIn(0f, 1f)
        return if (v <= 0.04045f) {
            v / 12.92f
        } else {
            ((v + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    /**
     * Standard IEC 61966-2-1 Linear RGB to sRGB gamma transfer function.
     */
    fun linearToSrgb(c: Float): Float {
        val v = c.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) {
            v * 12.92f
        } else {
            1.055f * v.pow(1f / 2.4f) - 0.055f
        }
    }

    /**
     * Device CMYK approximation (subtractive model, uncalibrated).
     */
    fun rgbToCmyk(color: ColorData): CmykData {
        val r = color.red
        val g = color.green
        val b = color.blue

        val k = 1f - max(r, max(g, b))
        if (k >= 0.99999f) {
            return CmykData(0f, 0f, 0f, 1f, color.alpha)
        }
        val c = (1f - r - k) / (1f - k)
        val m = (1f - g - k) / (1f - k)
        val y = (1f - b - k) / (1f - k)
        return CmykData(c.coerceIn(0f, 1f), m.coerceIn(0f, 1f), y.coerceIn(0f, 1f), k.coerceIn(0f, 1f), color.alpha)
    }

    /**
     * Converts Device CMYK approximation back to sRGB.
     */
    fun cmykToRgb(cmyk: CmykData): ColorData {
        val c = cmyk.cyan.coerceIn(0f, 1f)
        val m = cmyk.magenta.coerceIn(0f, 1f)
        val y = cmyk.yellow.coerceIn(0f, 1f)
        val k = cmyk.black.coerceIn(0f, 1f)

        val r = (1f - c) * (1f - k)
        val g = (1f - m) * (1f - k)
        val b = (1f - y) * (1f - k)
        return ColorData(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), cmyk.alpha)
    }
}
