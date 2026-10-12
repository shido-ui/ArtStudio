package com.artstudio.ui.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Persisted studio user preferences and tool settings.
 * Backed by Android SharedPreferences with an in-memory fallback for headless JVM tests.
 */
class StudioPreferences(context: Context? = null) {

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    private val memoryFallback = mutableMapOf<String, Any>()

    var lastActiveTool: String
        get() = getString(KEY_ACTIVE_TOOL, "BRUSH")
        set(value) = setString(KEY_ACTIVE_TOOL, value)

    var brushSize: Float
        get() = getFloat(KEY_BRUSH_SIZE, 12f)
        set(value) = setFloat(KEY_BRUSH_SIZE, value)

    var brushOpacity: Float
        get() = getFloat(KEY_BRUSH_OPACITY, 1f)
        set(value) = setFloat(KEY_BRUSH_OPACITY, value)

    var activeColorArgb: Int
        get() = getInt(KEY_ACTIVE_COLOR, 0xFF000000.toInt())
        set(value) = setInt(KEY_ACTIVE_COLOR, value)

    var isGridEnabled: Boolean
        get() = getBoolean(KEY_GRID_ENABLED, false)
        set(value) = setBoolean(KEY_GRID_ENABLED, value)

    var isSymmetryEnabled: Boolean
        get() = getBoolean(KEY_SYMMETRY_ENABLED, false)
        set(value) = setBoolean(KEY_SYMMETRY_ENABLED, value)

    var symmetryAxes: Int
        get() = getInt(KEY_SYMMETRY_AXES, 2)
        set(value) = setInt(KEY_SYMMETRY_AXES, value)

    var isFastPreviewEnabled: Boolean
        get() = getBoolean(KEY_FAST_PREVIEW, false)
        set(value) = setBoolean(KEY_FAST_PREVIEW, value)

    var hasCompletedOnboarding: Boolean
        get() = getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        set(value) = setBoolean(KEY_HAS_COMPLETED_ONBOARDING, value)

    var userMode: StudioUserMode
        get() = StudioUserMode.fromString(getString(KEY_USER_MODE, StudioUserMode.FULL.name))
        set(value) = setString(KEY_USER_MODE, value.name)

    var hasSeenAutoDrawTip: Boolean
        get() = getBoolean(KEY_SEEN_AUTODRAW_TIP, false)
        set(value) = setBoolean(KEY_SEEN_AUTODRAW_TIP, value)

    var hasSeenColorLabTip: Boolean
        get() = getBoolean(KEY_SEEN_COLORLAB_TIP, false)
        set(value) = setBoolean(KEY_SEEN_COLORLAB_TIP, value)

    var hasSeenDeformationTip: Boolean
        get() = getBoolean(KEY_SEEN_DEFORMATION_TIP, false)
        set(value) = setBoolean(KEY_SEEN_DEFORMATION_TIP, value)

    fun resetOnboarding() {
        hasCompletedOnboarding = false
        hasSeenAutoDrawTip = false
        hasSeenColorLabTip = false
        hasSeenDeformationTip = false
        userMode = StudioUserMode.FULL
    }

    private fun getString(key: String, def: String): String {
        return prefs?.getString(key, def) ?: (memoryFallback[key] as? String) ?: def
    }

    private fun setString(key: String, value: String) {
        memoryFallback[key] = value
        prefs?.edit()?.putString(key, value)?.apply()
    }

    private fun getFloat(key: String, def: Float): Float {
        return prefs?.getFloat(key, def) ?: (memoryFallback[key] as? Float) ?: def
    }

    private fun setFloat(key: String, value: Float) {
        memoryFallback[key] = value
        prefs?.edit()?.putFloat(key, value)?.apply()
    }

    private fun getInt(key: String, def: Int): Int {
        return prefs?.getInt(key, def) ?: (memoryFallback[key] as? Int) ?: def
    }

    private fun setInt(key: String, value: Int) {
        memoryFallback[key] = value
        prefs?.edit()?.putInt(key, value)?.apply()
    }

    private fun getBoolean(key: String, def: Boolean): Boolean {
        return prefs?.getBoolean(key, def) ?: (memoryFallback[key] as? Boolean) ?: def
    }

    private fun setBoolean(key: String, value: Boolean) {
        memoryFallback[key] = value
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    companion object {
        private const val PREFS_NAME = "art_studio_preferences"
        private const val KEY_ACTIVE_TOOL = "key_active_tool"
        private const val KEY_BRUSH_SIZE = "key_brush_size"
        private const val KEY_BRUSH_OPACITY = "key_brush_opacity"
        private const val KEY_ACTIVE_COLOR = "key_active_color"
        private const val KEY_GRID_ENABLED = "key_grid_enabled"
        private const val KEY_SYMMETRY_ENABLED = "key_symmetry_enabled"
        private const val KEY_SYMMETRY_AXES = "key_symmetry_axes"
        private const val KEY_FAST_PREVIEW = "key_fast_preview"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "key_has_completed_onboarding"
        private const val KEY_USER_MODE = "key_user_mode"
        private const val KEY_SEEN_AUTODRAW_TIP = "key_seen_autodraw_tip"
        private const val KEY_SEEN_COLORLAB_TIP = "key_seen_colorlab_tip"
        private const val KEY_SEEN_DEFORMATION_TIP = "key_seen_deformation_tip"
    }
}

/**
 * User experience mode for progressive disclosure.
 */
enum class StudioUserMode {
    JUST_DRAW,
    LEARN,
    FULL;

    companion object {
        fun fromString(value: String): StudioUserMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FULL
    }
}
