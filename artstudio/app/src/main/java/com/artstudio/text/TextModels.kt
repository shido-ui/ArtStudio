package com.artstudio.text

import com.artstudio.vector.AffineTransform2D
import com.artstudio.vector.VectorBounds
import java.util.UUID

/**
 * Text typeface styles (regular, bold, italic, bold-italic).
 */
enum class TextFontStyle(val id: String, val androidStyle: Int) {
    NORMAL("normal", 0),
    BOLD("bold", 1),
    ITALIC("italic", 2),
    BOLD_ITALIC("bold_italic", 3);

    companion object {
        fun fromId(id: String?): TextFontStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NORMAL

        fun fromAndroidStyle(style: Int): TextFontStyle = when (style) {
            1 -> BOLD
            2 -> ITALIC
            3 -> BOLD_ITALIC
            else -> NORMAL
        }
    }
}

/**
 * Text horizontal alignment within bounding box or relative to anchor coordinate.
 */
enum class TextAlignment(val id: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    companion object {
        fun fromId(id: String?): TextAlignment =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LEFT
    }
}

/**
 * Audit record documenting font fallback occurrences and substitutions.
 */
data class FontFallbackReport(
    val requestedFamily: String,
    val resolvedFamily: String,
    val fallbackOccurred: Boolean,
    val reason: String
)

/**
 * Authoritative, editable text object in document space.
 *
 * Preserves text content, styling, font specification, color, and geometric layout
 * without destructive pixel baking.
 */
data class TextObject(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Sample Text",
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val fontFamily: String = "sans-serif",
    val fontSize: Float = 36f,
    val fontStyle: TextFontStyle = TextFontStyle.NORMAL,
    val alignment: TextAlignment = TextAlignment.LEFT,
    val textColor: Long = 0xFF000000L,
    val letterSpacing: Float = 0f,
    val lineSpacingMultiplier: Float = 1.2f,
    val isUnderline: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val isRasterized: Boolean = false,
    val resolvedFontFamily: String? = null,
    val fallbackReport: FontFallbackReport? = null
) {
    init {
        require(fontSize > 0f) { "Font size must be positive, got $fontSize" }
        require(lineSpacingMultiplier > 0f) { "Line spacing must be positive, got $lineSpacingMultiplier" }
    }

    /**
     * Splits multiline text into discrete lines.
     */
    fun lines(): List<String> = text.split("\n")

    /**
     * Computes the mathematical bounding box encompassing the text layout.
     */
    fun computeBounds(): VectorBounds {
        val lines = lines()
        val lineCount = lines.size.coerceAtLeast(1)
        val lineHeight = fontSize * lineSpacingMultiplier
        val totalH = (lineCount * lineHeight).coerceAtLeast(fontSize)

        val maxChars = lines.maxOfOrNull { it.length } ?: 1
        // Character aspect ratio estimation: 0.6 for standard proportional fonts, 1.0 for monospaced
        val charWidthRatio = if (fontFamily.contains("mono", ignoreCase = true)) 0.65f else 0.55f
        val approxLineW = (maxChars * fontSize * charWidthRatio + maxChars * letterSpacing).coerceAtLeast(fontSize)
        val finalW = if (width > 0f) width else approxLineW
        val finalH = if (height > 0f) height else totalH

        return when (alignment) {
            TextAlignment.LEFT -> VectorBounds(x, y, x + finalW, y + finalH)
            TextAlignment.CENTER -> VectorBounds(x - finalW / 2f, y, x + finalW / 2f, y + finalH)
            TextAlignment.RIGHT -> VectorBounds(x - finalW, y, x, y + finalH)
        }
    }

    /**
     * Applies a 2D affine transformation to anchor coordinates and layout bounds.
     */
    fun transformed(t: AffineTransform2D): TextObject {
        val (nx, ny) = t.transformPoint(x, y)
        val scaleX = kotlin.math.sqrt(t.m00 * t.m00 + t.m10 * t.m10)
        return copy(
            x = nx,
            y = ny,
            width = if (width > 0f) width * scaleX else 0f,
            height = if (height > 0f) height * scaleX else 0f,
            fontSize = fontSize * scaleX
        )
    }
}
