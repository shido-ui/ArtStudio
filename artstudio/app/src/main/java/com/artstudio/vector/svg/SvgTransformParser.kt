package com.artstudio.vector.svg

import com.artstudio.vector.AffineTransform2D
import java.util.Locale

/**
 * Parser for SVG 1.1 `transform` attribute specification.
 *
 * Supports:
 * - `matrix(a, b, c, d, e, f)`
 * - `translate(tx [, ty])`
 * - `scale(sx [, sy])`
 * - `rotate(angle [, cx, cy])`
 * - `skewX(angle)`
 * - `skewY(angle)`
 *
 * Chained transforms are multiplied in SVG left-to-right order:
 * `transform="translate(10, 20) rotate(45)"` -> `T = T_translate * T_rotate`.
 */
object SvgTransformParser {

    private val TRANSFORM_REGEX = Regex("""([a-zA-Z]+)\s*\(([^)]*)\)""")

    fun parseTransform(transformStr: String?): AffineTransform2D {
        if (transformStr.isNullOrBlank()) return AffineTransform2D.IDENTITY

        var combined = AffineTransform2D.IDENTITY
        val matches = TRANSFORM_REGEX.findAll(transformStr)

        for (match in matches) {
            val type = match.groupValues[1].trim().lowercase(Locale.ROOT)
            val argsStr = match.groupValues[2].trim()
            val args = argsStr.split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }

            val opTransform = when (type) {
                "translate" -> {
                    val tx = args.getOrNull(0) ?: 0f
                    val ty = args.getOrNull(1) ?: 0f
                    AffineTransform2D.translation(tx, ty)
                }
                "scale" -> {
                    val sx = args.getOrNull(0) ?: 1f
                    val sy = args.getOrNull(1) ?: sx
                    AffineTransform2D.scale(sx, sy)
                }
                "rotate" -> {
                    val angle = args.getOrNull(0) ?: 0f
                    val cx = args.getOrNull(1) ?: 0f
                    val cy = args.getOrNull(2) ?: 0f
                    AffineTransform2D.rotation(angle, cx, cy)
                }
                "skewx" -> {
                    val angle = args.getOrNull(0) ?: 0f
                    AffineTransform2D.skewX(angle)
                }
                "skewy" -> {
                    val angle = args.getOrNull(0) ?: 0f
                    AffineTransform2D.skewY(angle)
                }
                "matrix" -> {
                    if (args.size >= 6) {
                        AffineTransform2D.matrix(args[0], args[1], args[2], args[3], args[4], args[5])
                    } else {
                        AffineTransform2D.IDENTITY
                    }
                }
                else -> AffineTransform2D.IDENTITY
            }

            combined = combined * opTransform
        }

        return combined
    }
}
