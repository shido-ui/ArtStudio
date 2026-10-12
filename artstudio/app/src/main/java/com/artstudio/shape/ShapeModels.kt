package com.artstudio.shape

import com.artstudio.vector.StrokeCap
import com.artstudio.vector.StrokeJoin
import com.artstudio.vector.VectorStyle

/**
 * Geometric shape primitive categories.
 */
enum class GeometricShapeType(val id: String) {
    RECTANGLE("rectangle"),
    ROUNDED_RECTANGLE("rounded_rectangle"),
    ELLIPSE("ellipse"),
    CIRCLE("circle"),
    LINE("line"),
    POLYGON("polygon"),
    STAR("star");

    companion object {
        fun fromId(id: String?): GeometricShapeType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: RECTANGLE
    }
}

/**
 * Target layer destination for shape creation operations.
 */
enum class ShapeTargetMode {
    NEW_VECTOR_LAYER,
    ACTIVE_VECTOR_LAYER,
    STAMP_TO_RASTER_LAYER
}

/**
 * Parameter configuration for creating filled and/or outlined geometric shapes.
 */
data class ShapeCreationParams(
    val type: GeometricShapeType = GeometricShapeType.RECTANGLE,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 200f,
    val height: Float = 200f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val polygonSides: Int = 5,
    val starPoints: Int = 5,
    val starInnerRatio: Float = 0.5f,
    val isFilled: Boolean = true,
    val fillColor: Long = 0xFF4A90E2L,
    val isOutlined: Boolean = true,
    val strokeColor: Long = 0xFF000000L,
    val strokeWidth: Float = 4.0f,
    val strokeCap: StrokeCap = StrokeCap.ROUND,
    val strokeJoin: StrokeJoin = StrokeJoin.ROUND,
    val targetMode: ShapeTargetMode = ShapeTargetMode.NEW_VECTOR_LAYER
) {
    fun toVectorStyle(): VectorStyle {
        return VectorStyle(
            strokeColor = if (isOutlined && strokeWidth > 0f) strokeColor else null,
            strokeWidth = if (isOutlined) strokeWidth else 0f,
            fillColor = if (isFilled) fillColor else null,
            strokeCap = strokeCap,
            strokeJoin = strokeJoin
        )
    }
}
