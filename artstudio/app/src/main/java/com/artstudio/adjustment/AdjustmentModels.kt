package com.artstudio.adjustment

import java.util.UUID

/**
 * Enumeration of supported tonal and color adjustment types.
 */
enum class AdjustmentType(val id: String, val displayName: String) {
    BRIGHTNESS_CONTRAST("brightness_contrast", "Brightness / Contrast"),
    LEVELS("levels", "Levels"),
    HUE_SATURATION("hue_saturation", "Hue / Saturation"),
    GRAYSCALE("grayscale", "Grayscale"),
    COLOR_BALANCE("color_balance", "Color Balance");

    companion object {
        fun fromId(id: String?): AdjustmentType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: BRIGHTNESS_CONTRAST
        }
    }
}

/**
 * Common shared interface for all non-destructive color and tonal adjustments.
 * Parameters are stored independently from raster pixel buffers.
 */
sealed interface ColorAdjustment {
    val id: String
    val type: AdjustmentType
    val enabled: Boolean
    val opacity: Float // [0.0, 1.0]

    fun toMap(): Map<String, Any?>

    companion object {
        fun fromMap(map: Map<String, Any?>): ColorAdjustment {
            val typeStr = map["type"]?.toString() ?: "brightness_contrast"
            val type = AdjustmentType.fromId(typeStr)
            val id = map["id"]?.toString() ?: UUID.randomUUID().toString()
            val enabled = (map["enabled"] as? Boolean) ?: true
            val opacity = ((map["opacity"] as? Number)?.toFloat() ?: 1.0f).coerceIn(0f, 1f)

            return when (type) {
                AdjustmentType.BRIGHTNESS_CONTRAST -> {
                    val brightness = ((map["brightness"] as? Number)?.toFloat() ?: 0f).coerceIn(-100f, 100f)
                    val contrast = ((map["contrast"] as? Number)?.toFloat() ?: 0f).coerceIn(-100f, 100f)
                    BrightnessContrastAdjustment(
                        id = id,
                        enabled = enabled,
                        opacity = opacity,
                        brightness = brightness,
                        contrast = contrast
                    )
                }
                AdjustmentType.LEVELS -> {
                    val inBlack = ((map["inBlack"] as? Number)?.toInt() ?: 0).coerceIn(0, 253)
                    val inGamma = ((map["inGamma"] as? Number)?.toFloat() ?: 1.0f).coerceIn(0.1f, 9.99f)
                    val inWhite = ((map["inWhite"] as? Number)?.toInt() ?: 255).coerceIn(inBlack + 2, 255)
                    val outBlack = ((map["outBlack"] as? Number)?.toInt() ?: 0).coerceIn(0, 255)
                    val outWhite = ((map["outWhite"] as? Number)?.toInt() ?: 255).coerceIn(0, 255)
                    LevelsAdjustment(
                        id = id,
                        enabled = enabled,
                        opacity = opacity,
                        inBlack = inBlack,
                        inGamma = inGamma,
                        inWhite = inWhite,
                        outBlack = outBlack,
                        outWhite = outWhite
                    )
                }
                AdjustmentType.HUE_SATURATION -> {
                    val hueShiftDeg = ((map["hueShiftDeg"] as? Number)?.toFloat() ?: 0f).coerceIn(-180f, 180f)
                    val saturationPercent = ((map["saturationPercent"] as? Number)?.toFloat() ?: 0f).coerceIn(-100f, 100f)
                    val lightnessPercent = ((map["lightnessPercent"] as? Number)?.toFloat() ?: 0f).coerceIn(-100f, 100f)
                    val colorize = (map["colorize"] as? Boolean) ?: false
                    HueSaturationAdjustment(
                        id = id,
                        enabled = enabled,
                        opacity = opacity,
                        hueShiftDeg = hueShiftDeg,
                        saturationPercent = saturationPercent,
                        lightnessPercent = lightnessPercent,
                        colorize = colorize
                    )
                }
                AdjustmentType.GRAYSCALE -> {
                    val redWeight = ((map["redWeight"] as? Number)?.toFloat() ?: 0.2126f).coerceIn(0f, 1f)
                    val greenWeight = ((map["greenWeight"] as? Number)?.toFloat() ?: 0.7152f).coerceIn(0f, 1f)
                    val blueWeight = ((map["blueWeight"] as? Number)?.toFloat() ?: 0.0722f).coerceIn(0f, 1f)
                    val tintColor = (map["tintColor"] as? Number)?.toLong()
                    val tintIntensity = ((map["tintIntensity"] as? Number)?.toFloat() ?: 0f).coerceIn(0f, 1f)
                    GrayscaleAdjustment(
                        id = id,
                        enabled = enabled,
                        opacity = opacity,
                        redWeight = redWeight,
                        greenWeight = greenWeight,
                        blueWeight = blueWeight,
                        tintColor = tintColor,
                        tintIntensity = tintIntensity
                    )
                }
                AdjustmentType.COLOR_BALANCE -> {
                    val cyanRed = ((map["cyanRed"] as? Number)?.toFloat() ?: 0f).coerceIn(-100f, 100f)
                    val magentaGreen = ((map["magentaGreen"] as? Number)?.toFloat() ?: 0f).coerceIn(-100f, 100f)
                    val yellowBlue = ((map["yellowBlue"] as? Number)?.toFloat() ?: 0f).coerceIn(-100f, 100f)
                    val preserveLuminosity = (map["preserveLuminosity"] as? Boolean) ?: true
                    ColorBalanceAdjustment(
                        id = id,
                        enabled = enabled,
                        opacity = opacity,
                        cyanRed = cyanRed,
                        magentaGreen = magentaGreen,
                        yellowBlue = yellowBlue,
                        preserveLuminosity = preserveLuminosity
                    )
                }
            }
        }
    }
}

/**
 * Brightness and Contrast tonal adjustment.
 *
 * @param brightness Tonal brightness offset in range [-100.0, 100.0] (0.0 = neutral)
 * @param contrast Sigmoidal contrast expansion factor in range [-100.0, 100.0] (0.0 = neutral)
 */
data class BrightnessContrastAdjustment(
    override val id: String = UUID.randomUUID().toString(),
    override val enabled: Boolean = true,
    override val opacity: Float = 1.0f,
    val brightness: Float = 0.0f,
    val contrast: Float = 0.0f
) : ColorAdjustment {
    override val type: AdjustmentType get() = AdjustmentType.BRIGHTNESS_CONTRAST

    init {
        require(opacity in 0f..1f) { "Opacity must be in 0..1, got $opacity" }
        require(brightness in -100f..100f) { "Brightness must be in -100..100, got $brightness" }
        require(contrast in -100f..100f) { "Contrast must be in -100..100, got $contrast" }
    }

    override fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "type" to type.id,
        "enabled" to enabled,
        "opacity" to opacity,
        "brightness" to brightness,
        "contrast" to contrast
    )
}

/**
 * Histogram Levels tonal adjustment with black/white clipping and midpoint gamma.
 *
 * @param inBlack Input shadow cutoff [0, 253]
 * @param inGamma Input midtone gamma expansion [0.1, 9.99] (1.0 = linear)
 * @param inWhite Input highlight cutoff [inBlack + 2, 255]
 * @param outBlack Output shadow floor [0, 255]
 * @param outWhite Output highlight ceiling [0, 255]
 */
data class LevelsAdjustment(
    override val id: String = UUID.randomUUID().toString(),
    override val enabled: Boolean = true,
    override val opacity: Float = 1.0f,
    val inBlack: Int = 0,
    val inGamma: Float = 1.0f,
    val inWhite: Int = 255,
    val outBlack: Int = 0,
    val outWhite: Int = 255
) : ColorAdjustment {
    override val type: AdjustmentType get() = AdjustmentType.LEVELS

    init {
        require(opacity in 0f..1f) { "Opacity must be in 0..1, got $opacity" }
        require(inBlack in 0..253) { "inBlack must be in 0..253, got $inBlack" }
        require(inGamma in 0.1f..9.99f) { "inGamma must be in 0.1..9.99, got $inGamma" }
        require(inWhite in (inBlack + 2)..255) { "inWhite must be in (inBlack+2)..255, got $inWhite vs $inBlack" }
        require(outBlack in 0..255) { "outBlack must be in 0..255, got $outBlack" }
        require(outWhite in 0..255) { "outWhite must be in 0..255, got $outWhite" }
    }

    override fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "type" to type.id,
        "enabled" to enabled,
        "opacity" to opacity,
        "inBlack" to inBlack,
        "inGamma" to inGamma,
        "inWhite" to inWhite,
        "outBlack" to outBlack,
        "outWhite" to outWhite
    )
}

/**
 * Hue, Saturation and Lightness / Value color adjustment.
 *
 * @param hueShiftDeg Angular hue rotation in degrees [-180.0, 180.0]
 * @param saturationPercent Saturation scaling percentage [-100.0, 100.0] (-100 = complete desaturation)
 * @param lightnessPercent Lightness / Value offset percentage [-100.0, 100.0]
 * @param colorize If true, maps all pixels to a single monochrome hue tinted by hueShiftDeg
 */
data class HueSaturationAdjustment(
    override val id: String = UUID.randomUUID().toString(),
    override val enabled: Boolean = true,
    override val opacity: Float = 1.0f,
    val hueShiftDeg: Float = 0.0f,
    val saturationPercent: Float = 0.0f,
    val lightnessPercent: Float = 0.0f,
    val colorize: Boolean = false
) : ColorAdjustment {
    override val type: AdjustmentType get() = AdjustmentType.HUE_SATURATION

    init {
        require(opacity in 0f..1f) { "Opacity must be in 0..1, got $opacity" }
        require(hueShiftDeg in -180f..180f) { "hueShiftDeg must be in -180..180, got $hueShiftDeg" }
        require(saturationPercent in -100f..100f) { "saturationPercent must be in -100..100, got $saturationPercent" }
        require(lightnessPercent in -100f..100f) { "lightnessPercent must be in -100..100, got $lightnessPercent" }
    }

    override fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "type" to type.id,
        "enabled" to enabled,
        "opacity" to opacity,
        "hueShiftDeg" to hueShiftDeg,
        "saturationPercent" to saturationPercent,
        "lightnessPercent" to lightnessPercent,
        "colorize" to colorize
    )
}

/**
 * Grayscale conversion with customizable channel weights and optional duotone tinting.
 * Default weights use the ITU-R BT.709 HDTV luminance standard (0.2126 R + 0.7152 G + 0.0722 B).
 *
 * @param redWeight Red channel luminance contribution [0.0, 1.0]
 * @param greenWeight Green channel luminance contribution [0.0, 1.0]
 * @param blueWeight Blue channel luminance contribution [0.0, 1.0]
 * @param tintColor Optional ARGB tint color (e.g. Sepia 0xFF704214L)
 * @param tintIntensity Tint blending factor [0.0, 1.0]
 */
data class GrayscaleAdjustment(
    override val id: String = UUID.randomUUID().toString(),
    override val enabled: Boolean = true,
    override val opacity: Float = 1.0f,
    val redWeight: Float = 0.2126f,
    val greenWeight: Float = 0.7152f,
    val blueWeight: Float = 0.0722f,
    val tintColor: Long? = null,
    val tintIntensity: Float = 0.0f
) : ColorAdjustment {
    override val type: AdjustmentType get() = AdjustmentType.GRAYSCALE

    init {
        require(opacity in 0f..1f) { "Opacity must be in 0..1, got $opacity" }
        require(redWeight in 0f..1f) { "redWeight must be in 0..1, got $redWeight" }
        require(greenWeight in 0f..1f) { "greenWeight must be in 0..1, got $greenWeight" }
        require(blueWeight in 0f..1f) { "blueWeight must be in 0..1, got $blueWeight" }
        require(tintIntensity in 0f..1f) { "tintIntensity must be in 0..1, got $tintIntensity" }
    }

    override fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to id,
            "type" to type.id,
            "enabled" to enabled,
            "opacity" to opacity,
            "redWeight" to redWeight,
            "greenWeight" to greenWeight,
            "blueWeight" to blueWeight,
            "tintIntensity" to tintIntensity
        )
        tintColor?.let { map["tintColor"] = it }
        return map
    }
}

/**
 * Color Balance adjustment for cyan/red, magenta/green, and yellow/blue axes.
 *
 * @param cyanRed Cyan vs Red shift [-100.0, 100.0] (negative = cyan, positive = red)
 * @param magentaGreen Magenta vs Green shift [-100.0, 100.0] (negative = magenta, positive = green)
 * @param yellowBlue Yellow vs Blue shift [-100.0, 100.0] (negative = yellow, positive = blue)
 * @param preserveLuminosity If true, normalizes output RGB to preserve original perceived BT.709 luminance
 */
data class ColorBalanceAdjustment(
    override val id: String = UUID.randomUUID().toString(),
    override val enabled: Boolean = true,
    override val opacity: Float = 1.0f,
    val cyanRed: Float = 0.0f,
    val magentaGreen: Float = 0.0f,
    val yellowBlue: Float = 0.0f,
    val preserveLuminosity: Boolean = true
) : ColorAdjustment {
    override val type: AdjustmentType get() = AdjustmentType.COLOR_BALANCE

    init {
        require(opacity in 0f..1f) { "Opacity must be in 0..1, got $opacity" }
        require(cyanRed in -100f..100f) { "cyanRed must be in -100..100, got $cyanRed" }
        require(magentaGreen in -100f..100f) { "magentaGreen must be in -100..100, got $magentaGreen" }
        require(yellowBlue in -100f..100f) { "yellowBlue must be in -100..100, got $yellowBlue" }
    }

    override fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "type" to type.id,
        "enabled" to enabled,
        "opacity" to opacity,
        "cyanRed" to cyanRed,
        "magentaGreen" to magentaGreen,
        "yellowBlue" to yellowBlue,
        "preserveLuminosity" to preserveLuminosity
    )
}
