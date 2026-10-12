package com.artstudio.color

import com.artstudio.document.io.ArtJson

/**
 * Selection modes for color input in the Studio Color panel.
 */
enum class ColorInputMode {
    WHEEL,
    SLIDERS_RGB,
    SLIDERS_HSV,
    HEX
}

/**
 * Manages foreground and background primary colors with swap and default reset.
 */
class ForegroundBackgroundState(
    initialForeground: ColorData = ColorData.BLACK,
    initialBackground: ColorData = ColorData.WHITE
) {
    var foreground: ColorData = initialForeground
        private set

    var background: ColorData = initialBackground
        private set

    var onColorChanged: ((foreground: ColorData, background: ColorData) -> Unit)? = null

    fun setForeground(color: ColorData) {
        foreground = color
        onColorChanged?.invoke(foreground, background)
    }

    fun setBackground(color: ColorData) {
        background = color
        onColorChanged?.invoke(foreground, background)
    }

    fun swapColors() {
        val temp = foreground
        foreground = background
        background = temp
        onColorChanged?.invoke(foreground, background)
    }

    fun resetDefaults() {
        foreground = ColorData.BLACK
        background = ColorData.WHITE
        onColorChanged?.invoke(foreground, background)
    }
}

/**
 * In-memory and persistent color history (recently used swatches).
 * Automatically deduplicates and clamps to capacity.
 */
class ColorHistory(
    val maxCapacity: Int = 24
) {
    private val _history = mutableListOf<ColorData>()
    val items: List<ColorData> get() = _history.toList()

    var onHistoryChanged: ((List<ColorData>) -> Unit)? = null

    fun push(color: ColorData) {
        // Deduplicate if already present with exact or near-identical color (delta <= 0.01)
        val index = _history.indexOfFirst { it.deltaRgb(color) < 0.01f && kotlin.math.abs(it.alpha - color.alpha) < 0.01f }
        if (index >= 0) {
            _history.removeAt(index)
        }
        _history.add(0, color)
        while (_history.size > maxCapacity) {
            _history.removeAt(_history.lastIndex)
        }
        onHistoryChanged?.invoke(items)
    }

    fun clear() {
        _history.clear()
        onHistoryChanged?.invoke(items)
    }

    fun toJson(): String {
        val list = _history.map { it.hexArgb }
        return ArtJson.stringify(list)
    }

    fun fromJson(json: String) {
        try {
            val list = ArtJson.parse(json) as? List<*> ?: return
            _history.clear()
            for (item in list) {
                if (item is String) {
                    try {
                        _history.add(ColorData.fromHex(item))
                    } catch (e: Exception) {
                        android.util.Log.d("ColorHistory", "Skipping invalid color hex: ${e.message}")
                    }
                }
            }
            while (_history.size > maxCapacity) {
                _history.removeAt(_history.lastIndex)
            }
            onHistoryChanged?.invoke(items)
        } catch (e: Exception) {
            android.util.Log.w("ColorHistory", "Failed parsing color history JSON: ${e.message}")
        }
    }
}

/**
 * Comprehensive color workspace state for manual painting and tooling.
 */
class StudioColorState(
    initialForeground: ColorData = ColorData.BLACK,
    initialBackground: ColorData = ColorData.WHITE,
    val history: ColorHistory = ColorHistory(maxCapacity = 24)
) {
    val fgbg: ForegroundBackgroundState = ForegroundBackgroundState(initialForeground, initialBackground)

    var activeInputMode: ColorInputMode = ColorInputMode.WHEEL

    val activeColor: ColorData get() = fgbg.foreground

    init {
        // Pre-populate history with initial colors
        history.push(initialBackground)
        history.push(initialForeground)
    }

    fun selectColor(color: ColorData, updateForeground: Boolean = true) {
        if (updateForeground) {
            fgbg.setForeground(color)
        } else {
            fgbg.setBackground(color)
        }
        history.push(color)
    }
}
