package com.artstudio.core

/**
 * Type-safe point in screen space (device display coordinates in physical pixels,
 * relative to the window or display top-left).
 */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Type-safe point in viewport space (local coordinates within the canvas view widget / surface).
 */
data class ViewportPoint(val x: Float, val y: Float)

/**
 * Type-safe point in authoritative document space (canonical artwork canvas coordinates,
 * where (0, 0) is top-left of artwork and (width, height) is bottom-right).
 */
data class DocumentPoint(val x: Float, val y: Float)

/**
 * Type-safe point in source image space (local pixel coordinates of an imported asset
 * or reference image, where (0, 0) is the asset's top-left).
 */
data class SourceImagePoint(val x: Float, val y: Float)

/**
 * Models the geometric placement of an external source image or reference asset
 * positioned on the authoritative canvas document.
 */
data class SourceImagePlacement(
    val originX: Float,
    val originY: Float,
    val placedWidth: Float,
    val placedHeight: Float,
    val rotationDegrees: Float = 0f,
    val sourceWidth: Int,
    val sourceHeight: Int
)

/**
 * Strict coordinate transformation engine ensuring complete mathematical separation
 * across Screen, Viewport, Document, and SourceImage coordinate spaces.
 *
 * Invariant: Transformations perform pure coordinate conversions and NEVER mutate
 * artwork document state, layer pixels, or vector paths.
 */
object CoordinateTransformer {

    /**
     * Converts a screen coordinate to a viewport coordinate by subtracting the
     * viewport widget's top-left origin on screen.
     */
    fun screenToViewport(screenPt: ScreenPoint, viewportOriginOnScreen: ScreenPoint): ViewportPoint {
        return ViewportPoint(
            x = screenPt.x - viewportOriginOnScreen.x,
            y = screenPt.y - viewportOriginOnScreen.y
        )
    }

    /**
     * Converts a viewport coordinate to a screen coordinate by adding the
     * viewport widget's top-left origin on screen.
     */
    fun viewportToScreen(viewportPt: ViewportPoint, viewportOriginOnScreen: ScreenPoint): ScreenPoint {
        return ScreenPoint(
            x = viewportPt.x + viewportOriginOnScreen.x,
            y = viewportPt.y + viewportOriginOnScreen.y
        )
    }

    /**
     * Converts a viewport coordinate to an authoritative document coordinate
     * using the current viewport transform projection.
     */
    fun viewportToDocument(viewportPt: ViewportPoint, transform: ViewportTransform): DocumentPoint {
        val pt = transform.screenToCanvas(viewportPt.x, viewportPt.y)
        return DocumentPoint(pt.x, pt.y)
    }

    /**
     * Converts an authoritative document coordinate to a viewport coordinate
     * using the current viewport transform projection.
     */
    fun documentToViewport(docPt: DocumentPoint, transform: ViewportTransform): ViewportPoint {
        val pt = transform.canvasToScreen(docPt.x, docPt.y)
        return ViewportPoint(pt.x, pt.y)
    }

    /**
     * Direct screen to document coordinate conversion.
     */
    fun screenToDocument(
        screenPt: ScreenPoint,
        viewportOriginOnScreen: ScreenPoint,
        transform: ViewportTransform
    ): DocumentPoint {
        val viewportPt = screenToViewport(screenPt, viewportOriginOnScreen)
        return viewportToDocument(viewportPt, transform)
    }

    /**
     * Direct document to screen coordinate conversion.
     */
    fun documentToScreen(
        docPt: DocumentPoint,
        viewportOriginOnScreen: ScreenPoint,
        transform: ViewportTransform
    ): ScreenPoint {
        val viewportPt = documentToViewport(docPt, transform)
        return viewportToScreen(viewportPt, viewportOriginOnScreen)
    }

    /**
     * Converts a document coordinate to source image local pixel coordinates.
     * Inverse of sourceImageToDocument.
     */
    fun documentToSourceImage(
        docPt: DocumentPoint,
        placement: SourceImagePlacement
    ): SourceImagePoint {
        // 1. Un-translate from placement origin
        val dx = docPt.x - placement.originX
        val dy = docPt.y - placement.originY

        // 2. Un-rotate
        val rad = Math.toRadians(-placement.rotationDegrees.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        val unrotX = dx * cos - dy * sin
        val unrotY = dx * sin + dy * cos

        // 3. Un-scale from placed dimensions to source image pixel dimensions
        val scaleX = placement.sourceWidth.toFloat() / placement.placedWidth
        val scaleY = placement.sourceHeight.toFloat() / placement.placedHeight

        val imgX = unrotX * scaleX
        val imgY = unrotY * scaleY

        return SourceImagePoint(imgX, imgY)
    }

    /**
     * Converts a source image local pixel coordinate to authoritative document coordinates.
     */
    fun sourceImageToDocument(
        imagePt: SourceImagePoint,
        placement: SourceImagePlacement
    ): DocumentPoint {
        // 1. Scale from source image dimensions to placed dimensions
        val scaleX = placement.placedWidth / placement.sourceWidth.toFloat()
        val scaleY = placement.placedHeight / placement.sourceHeight.toFloat()
        val scaledX = imagePt.x * scaleX
        val scaledY = imagePt.y * scaleY

        // 2. Rotate
        val rad = Math.toRadians(placement.rotationDegrees.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        val rotX = scaledX * cos - scaledY * sin
        val rotY = scaledX * sin + scaledY * cos

        // 3. Translate by placement origin
        val docX = placement.originX + rotX
        val docY = placement.originY + rotY

        return DocumentPoint(docX, docY)
    }
}
