package com.artstudio.ui.model

/**
 * Mutually-exclusive active panel/drawer in the Studio UI.
 * Replaces disparate independent boolean flags with a single, verifiable, sealed state hierarchy.
 */
sealed interface ActivePanel {
    data object Brush : ActivePanel
    data object Color : ActivePanel
    data object Layers : ActivePanel
    data object DrawingAids : ActivePanel
    data object Comic : ActivePanel
    data object Shapes : ActivePanel
    data object Text : ActivePanel
    data object Vector : ActivePanel
    data object Filters : ActivePanel
    data object Adjustments : ActivePanel
    data object Timeline : ActivePanel
    data object Selection : ActivePanel
    data object Transform : ActivePanel
    data object Fill : ActivePanel
    data object PixelOps : ActivePanel
    data object AutoDraw : ActivePanel
    data object ColorStack : ActivePanel
    data object Deformation : ActivePanel
}

/**
 * Active studio tool state.
 */
enum class StudioTool(val displayName: String) {
    BRUSH("Brush"),
    ERASER("Eraser"),
    EYEDROPPER("Eyedropper"),
    FILL("Flood Fill"),
    SMUDGE_BLUR("Smudge & Blur"),
    SELECTION("Selection"),
    TRANSFORM("Transform"),
    SHAPES("Shapes"),
    VECTOR("Vector"),
    TEXT("Text")
}
