package com.artstudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.artstudio.color.ColorData
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Interactive Radial Color Wheel for Jetpack Compose.
 * Maps angle to Hue [0, 360) and normalized radius to Saturation [0, 1].
 * Maintains constant Value/Brightness and Alpha from [currentColor].
 */
@Composable
fun StudioColorWheel(
    currentColor: ColorData,
    onColorChanged: (ColorData) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val currentHue = currentColor.hue
    val currentSat = currentColor.saturation
    val currentValue = currentColor.value
    val currentAlpha = currentColor.alpha

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                this.role = Role.Image
                this.contentDescription = "Color Wheel: Hue ${currentHue.toInt()} degrees, Saturation ${(currentSat * 100).toInt()}%"
            }
            .pointerInput(currentValue, currentAlpha) {
                detectTapGestures { offset ->
                    val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val radius = size.toPx() / 2f
                    val dist = hypot(dx, dy)
                    val sat = (dist / radius).coerceIn(0f, 1f)
                    var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angleDeg < 0f) angleDeg += 360f

                    val newColor = ColorData.fromHsv(angleDeg, sat, currentValue, currentAlpha)
                    onColorChanged(newColor)
                }
            }
            .pointerInput(currentValue, currentAlpha) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                    val dx = change.position.x - center.x
                    val dy = change.position.y - center.y
                    val radius = size.toPx() / 2f
                    val dist = hypot(dx, dy)
                    val sat = (dist / radius).coerceIn(0f, 1f)
                    var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angleDeg < 0f) angleDeg += 360f

                    val newColor = ColorData.fromHsv(angleDeg, sat, currentValue, currentAlpha)
                    onColorChanged(newColor)
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.width / 2f

            // Draw radial color wheel fan
            val stepDeg = 4
            for (deg in 0 until 360 step stepDeg) {
                val radStart = Math.toRadians(deg.toDouble())
                val radEnd = Math.toRadians((deg + stepDeg).toDouble())

                val p1 = center
                val p2 = Offset(center.x + (radius * cos(radStart)).toFloat(), center.y + (radius * sin(radStart)).toFloat())
                val p3 = Offset(center.x + (radius * cos(radEnd)).toFloat(), center.y + (radius * sin(radEnd)).toFloat())

                val colorAtEdge = ColorData.fromHsv(deg.toFloat(), 1.0f, currentValue)
                val colorCenter = ColorData.fromHsv(deg.toFloat(), 0.0f, currentValue)

                // Simple polygon / line fan representation
                drawLine(
                    color = Color(colorAtEdge.argbInt),
                    start = center,
                    end = Offset((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f),
                    strokeWidth = 6f
                )
            }

            // Draw selection thumb marker at current hue and saturation
            val thumbAngleRad = Math.toRadians(currentHue.toDouble())
            val thumbDist = currentSat * radius
            val thumbX = center.x + (thumbDist * cos(thumbAngleRad)).toFloat()
            val thumbY = center.y + (thumbDist * sin(thumbAngleRad)).toFloat()

            // Outer ring
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            // Inner color center
            drawCircle(
                color = Color(currentColor.argbInt),
                radius = 6.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.6f),
                radius = 9.dp.toPx(),
                center = Offset(thumbX, thumbY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
