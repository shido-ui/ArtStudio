package com.artstudio.document.model

/**
 * Canvas configuration: dimensions, physical resolution, background color and color profile.
 */
data class CanvasConfig(
    val width: Int = 2048,
    val height: Int = 2048,
    val dpi: Int = 300,
    val backgroundColor: Long = 0xFFFFFFFFL, // ARGB White
    val colorSpace: String = "sRGB"
) {
    init {
        require(width > 0) { "Canvas width must be greater than 0, got $width" }
        require(height > 0) { "Canvas height must be greater than 0, got $height" }
        require(dpi > 0) { "Canvas DPI must be greater than 0, got $dpi" }
    }
}

/**
 * Supported interpolation filtering modes for raster resampling.
 */
enum class InterpolationMode(val displayName: String) {
    NEAREST_NEIGHBOR("Nearest Neighbor"),
    BILINEAR("Bilinear"),
    BICUBIC("Bicubic")
}

/**
 * Anchor alignment for canvas resizing.
 */
enum class CanvasResizeAnchor(val displayName: String, val alignX: Float, val alignY: Float) {
    TOP_LEFT("Top Left", 0f, 0f),
    TOP_CENTER("Top Center", 0.5f, 0f),
    TOP_RIGHT("Top Right", 1f, 0f),
    CENTER_LEFT("Center Left", 0f, 0.5f),
    CENTER("Center", 0.5f, 0.5f),
    CENTER_RIGHT("Center Right", 1f, 0.5f),
    BOTTOM_LEFT("Bottom Left", 0f, 1f),
    BOTTOM_CENTER("Bottom Center", 0.5f, 1f),
    BOTTOM_RIGHT("Bottom Right", 1f, 1f)
}

/**
 * Explicit 2D geometric transform applied non-destructively to a layer or group.
 */
data class LayerTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val isAspectRatioLocked: Boolean = true,
    val interpolation: InterpolationMode = InterpolationMode.BILINEAR
) {
    val isIdentity: Boolean
        get() = translationX == 0f && translationY == 0f &&
                scaleX == 1f && scaleY == 1f &&
                rotationDegrees == 0f &&
                !flipHorizontal && !flipVertical

    fun withTranslation(dx: Float, dy: Float) = copy(translationX = dx, translationY = dy)
    fun withScale(sx: Float, sy: Float) = copy(scaleX = sx, scaleY = sy)
    fun withUniformScale(scale: Float) = copy(scaleX = scale, scaleY = scale)
    fun withRotation(deg: Float) = copy(rotationDegrees = (deg % 360f + 360f) % 360f)
    fun toggleFlipHorizontal() = copy(flipHorizontal = !flipHorizontal)
    fun toggleFlipVertical() = copy(flipVertical = !flipVertical)
    fun toggleAspectRatioLock() = copy(isAspectRatioLocked = !isAspectRatioLocked)
    fun reset() = LayerTransform()
}

/**
 * Authoritative set of layer compositing blend modes supported by Art Studio.
 * Clearly distinguishes verified, supported modes from unsupported modes.
 */
enum class BlendModeType(
    val key: String,
    val displayName: String,
    val isSupported: Boolean = true
) {
    Normal("normal", "Normal", isSupported = true),
    Multiply("multiply", "Multiply", isSupported = true),
    Screen("screen", "Screen", isSupported = true),
    Add("add", "Add (Linear Dodge)", isSupported = true),
    Darken("darken", "Darken", isSupported = true),
    Lighten("lighten", "Lighten", isSupported = true),
    Overlay("overlay", "Overlay", isSupported = true),
    ColorDodge("color_dodge", "Color Dodge", isSupported = true),

    // Clearly labeled unsupported modes (defined for schema completeness without silent aliasing)
    ColorBurn("color_burn", "Color Burn (Unsupported)", isSupported = false),
    SoftLight("soft_light", "Soft Light (Unsupported)", isSupported = false),
    HardLight("hard_light", "Hard Light (Unsupported)", isSupported = false),
    Difference("difference", "Difference (Unsupported)", isSupported = false),
    Exclusion("exclusion", "Exclusion (Unsupported)", isSupported = false),
    Hue("hue", "Hue (Unsupported)", isSupported = false),
    Saturation("saturation", "Saturation (Unsupported)", isSupported = false),
    Color("color", "Color (Unsupported)", isSupported = false),
    Luminosity("luminosity", "Luminosity (Unsupported)", isSupported = false);

    companion object {
        /**
         * Curated starter set of verified and supported blend modes exposed in UI.
         */
        val supportedModes: List<BlendModeType>
            get() = entries.filter { it.isSupported }

        fun fromKey(key: String?): BlendModeType {
            if (key == null) return Normal
            val lower = key.lowercase().trim()
            if (lower == "linear_dodge" || lower == "plus") return Add
            return entries.find { it.key.equals(lower, ignoreCase = true) } ?: Normal
        }
    }
}

