package com.artstudio.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.artstudio.adjustment.BrightnessContrastAdjustment
import com.artstudio.adjustment.ColorAdjustment
import com.artstudio.color.ColorData
import com.artstudio.color.StudioColorState
import com.artstudio.core.BrushConfig
import com.artstudio.core.BrushType
import com.artstudio.fill.FillConfig
import com.artstudio.filter.FilterConfig
import com.artstudio.guides.DrawingAidsConfig
import com.artstudio.guides.SymmetryConfig
import com.artstudio.guides.SymmetryType
import com.artstudio.importer.DecodedRasterImage
import com.artstudio.pixelops.PixelModifierConfig
import com.artstudio.pixelops.PixelModifierMode
import com.artstudio.colorstack.ColorStyleId
import com.artstudio.deformation.DeformToolMode
import com.artstudio.ui.components.LiquifySubMode
import com.artstudio.ui.model.ActivePanel
import com.artstudio.ui.model.StudioTool
import com.artstudio.ui.preferences.StudioPreferences

/**
 * Authoritative ViewModel managing Studio canvas tools, mutually-exclusive active panels,
 * persisted configurations, transient interaction states, and process death state restoration
 * via SavedStateHandle.
 */
class StudioViewModel(
    initialProjectId: String,
    val preferences: StudioPreferences = StudioPreferences(),
    val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    companion object {
        const val KEY_PROJECT_ID = "studio_project_id"
        const val KEY_ACTIVE_TOOL = "studio_active_tool"
        const val KEY_ACTIVE_PANEL = "studio_active_panel"
        const val KEY_ACTIVE_COLOR_ARGB = "studio_active_color_argb"
        const val KEY_VIEWPORT_PAN_X = "studio_viewport_pan_x"
        const val KEY_VIEWPORT_PAN_Y = "studio_viewport_pan_y"
        const val KEY_VIEWPORT_ZOOM = "studio_viewport_zoom"
        const val KEY_VIEWPORT_ROTATION = "studio_viewport_rotation"
    }

    val projectId: String = savedStateHandle?.get<String>(KEY_PROJECT_ID) ?: initialProjectId

    init {
        savedStateHandle?.set(KEY_PROJECT_ID, projectId)
    }

    // --- 1. Active Tool & Mutually Exclusive Panel Hierarchy ---
    var activeTool by mutableStateOf(
        run {
            val fromSaved = savedStateHandle?.get<String>(KEY_ACTIVE_TOOL)
            if (fromSaved != null) {
                try {
                    StudioTool.valueOf(fromSaved)
                } catch (_: Throwable) {
                    StudioTool.BRUSH
                }
            } else {
                try {
                    StudioTool.valueOf(preferences.lastActiveTool)
                } catch (_: Throwable) {
                    StudioTool.BRUSH
                }
            }
        }
    )
        private set

    var activePanel by mutableStateOf<ActivePanel?>(
        run {
            val fromSaved = savedStateHandle?.get<String>(KEY_ACTIVE_PANEL)
            when (fromSaved) {
                "Fill" -> ActivePanel.Fill
                "PixelOps" -> ActivePanel.PixelOps
                "Selection" -> ActivePanel.Selection
                "Transform" -> ActivePanel.Transform
                "Shapes" -> ActivePanel.Shapes
                "Vector" -> ActivePanel.Vector
                "Text" -> ActivePanel.Text
                "Layers" -> ActivePanel.Layers
                "Color" -> ActivePanel.Color
                "Filters" -> ActivePanel.Filters
                "Adjustments" -> ActivePanel.Adjustments
                "Timeline" -> ActivePanel.Timeline
                "Comic" -> ActivePanel.Comic
                "DrawingAids" -> ActivePanel.DrawingAids
                "Brush" -> ActivePanel.Brush
                "AutoDraw" -> ActivePanel.AutoDraw
                "ColorStack" -> ActivePanel.ColorStack
                "Deformation" -> ActivePanel.Deformation
                else -> null
            }
        }
    )
        private set

    // --- 2. Color & Tool Configs ---
    val colorState = StudioColorState(
        initialForeground = run {
            val fromSaved = savedStateHandle?.get<Int>(KEY_ACTIVE_COLOR_ARGB)
            val argb = fromSaved ?: preferences.activeColorArgb
            ColorData.fromArgb(argb)
        }
    )

    var brushConfig by mutableStateOf(
        BrushConfig(
            type = BrushType.HARD_PEN,
            size = preferences.brushSize,
            opacity = preferences.brushOpacity
        )
    )

    var fillConfig by mutableStateOf(
        FillConfig(fillColor = colorState.activeColor.argbInt)
    )

    var pixelOpsConfig by mutableStateOf(
        PixelModifierConfig(mode = PixelModifierMode.SMUDGE)
    )

    var filterConfig by mutableStateOf<FilterConfig>(
        FilterConfig.GaussianBlurConfig(radius = 10f)
    )

    var activeAdjustment by mutableStateOf<ColorAdjustment>(
        BrightnessContrastAdjustment()
    )

    var drawingAidsConfig by mutableStateOf(
        DrawingAidsConfig(
            guideVisible = preferences.isGridEnabled,
            symmetry = SymmetryConfig(
                type = if (preferences.isSymmetryEnabled) SymmetryType.MIRROR_VERTICAL else SymmetryType.NONE,
                radialSectors = preferences.symmetryAxes
            )
        )
    )

    // --- 3. Viewport State Restoration ---
    var viewportPanX by mutableFloatStateOf(savedStateHandle?.get<Float>(KEY_VIEWPORT_PAN_X) ?: 0f)
        private set
    var viewportPanY by mutableFloatStateOf(savedStateHandle?.get<Float>(KEY_VIEWPORT_PAN_Y) ?: 0f)
        private set
    var viewportZoom by mutableFloatStateOf(savedStateHandle?.get<Float>(KEY_VIEWPORT_ZOOM) ?: 1f)
        private set
    var viewportRotation by mutableFloatStateOf(savedStateHandle?.get<Float>(KEY_VIEWPORT_ROTATION) ?: 0f)
        private set

    // --- 4. Animation Timeline & Preview ---
    var isAnimationPlaying by mutableStateOf(false)
    var isFastPreviewEnabled by mutableStateOf(preferences.isFastPreviewEnabled)

    // --- 5. Modals & Dialog State ---
    var showClearConfirmationDialog by mutableStateOf(false)
    var showExportDialog by mutableStateOf(false)
    var pendingImportedImage by mutableStateOf<DecodedRasterImage?>(null)
    var studioErrorMessage by mutableStateOf<String?>(null)
    var importErrorMessage by mutableStateOf<String?>(null)

    // --- 6. User Interaction Actions ---
    fun selectTool(tool: StudioTool) {
        activeTool = tool
        preferences.lastActiveTool = tool.name
        savedStateHandle?.set(KEY_ACTIVE_TOOL, tool.name)
        when (tool) {
            StudioTool.BRUSH -> {
                brushConfig = brushConfig.copy(type = BrushType.HARD_PEN)
            }
            StudioTool.ERASER -> {
                brushConfig = brushConfig.copy(type = BrushType.ERASER)
            }
            StudioTool.FILL -> {
                activePanel = ActivePanel.Fill
                savedStateHandle?.set(KEY_ACTIVE_PANEL, "Fill")
            }
            StudioTool.SMUDGE_BLUR -> {
                activePanel = ActivePanel.PixelOps
                savedStateHandle?.set(KEY_ACTIVE_PANEL, "PixelOps")
            }
            StudioTool.SELECTION -> {
                activePanel = ActivePanel.Selection
                savedStateHandle?.set(KEY_ACTIVE_PANEL, "Selection")
            }
            StudioTool.TRANSFORM -> {
                activePanel = ActivePanel.Transform
                savedStateHandle?.set(KEY_ACTIVE_PANEL, "Transform")
            }
            StudioTool.SHAPES -> {
                activePanel = ActivePanel.Shapes
                savedStateHandle?.set(KEY_ACTIVE_PANEL, "Shapes")
            }
            StudioTool.VECTOR -> {
                activePanel = ActivePanel.Vector
                savedStateHandle?.set(KEY_ACTIVE_PANEL, "Vector")
            }
            StudioTool.TEXT -> {
                activePanel = ActivePanel.Text
                savedStateHandle?.set(KEY_ACTIVE_PANEL, "Text")
            }
            StudioTool.EYEDROPPER -> {
                closeActivePanel()
            }
        }
    }

    fun togglePanel(panel: ActivePanel) {
        activePanel = if (activePanel == panel) null else panel
        savedStateHandle?.set(KEY_ACTIVE_PANEL, activePanel?.let { it::class.simpleName })
    }

    fun closeActivePanel() {
        activePanel = null
        savedStateHandle?.set(KEY_ACTIVE_PANEL, null)
    }

    fun updateBrushSize(size: Float) {
        val clamped = size.coerceIn(1f, 500f)
        brushConfig = brushConfig.copy(size = clamped)
        preferences.brushSize = clamped
    }

    fun updateBrushOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.01f, 1f)
        brushConfig = brushConfig.copy(opacity = clamped)
        preferences.brushOpacity = clamped
    }

    fun selectColor(color: ColorData) {
        colorState.selectColor(color)
        fillConfig = fillConfig.copy(fillColor = color.argbInt)
        preferences.activeColorArgb = color.argbInt
        savedStateHandle?.set(KEY_ACTIVE_COLOR_ARGB, color.argbInt)
    }

    fun updateViewport(panX: Float, panY: Float, zoom: Float, rotation: Float = 0f) {
        viewportPanX = panX
        viewportPanY = panY
        viewportZoom = zoom
        viewportRotation = rotation
        savedStateHandle?.set(KEY_VIEWPORT_PAN_X, panX)
        savedStateHandle?.set(KEY_VIEWPORT_PAN_Y, panY)
        savedStateHandle?.set(KEY_VIEWPORT_ZOOM, zoom)
        savedStateHandle?.set(KEY_VIEWPORT_ROTATION, rotation)
    }

    fun toggleFastPreview() {
        val newValue = !isFastPreviewEnabled
        isFastPreviewEnabled = newValue
        preferences.isFastPreviewEnabled = newValue
    }

    fun toggleGrid() {
        val updated = drawingAidsConfig.copy(guideVisible = !drawingAidsConfig.guideVisible)
        drawingAidsConfig = updated
        preferences.isGridEnabled = updated.guideVisible
    }

    fun toggleSymmetry() {
        val currentSym = drawingAidsConfig.symmetry
        val nextType = if (currentSym.type == SymmetryType.NONE) SymmetryType.MIRROR_VERTICAL else SymmetryType.NONE
        val updated = drawingAidsConfig.copy(symmetry = currentSym.copy(type = nextType))
        drawingAidsConfig = updated
        preferences.isSymmetryEnabled = (nextType != SymmetryType.NONE)
    }

    // --- ColorStack (Colour Lab) State ---
    var selectedColorStyleId by mutableStateOf(ColorStyleId.CLASSIC_CEL)
    var colorStackStrength by mutableFloatStateOf(1.0f)
    var colorStackOpacity by mutableFloatStateOf(1.0f)

    // --- Deformation Interactive State ---
    var deformationToolMode by mutableStateOf(DeformToolMode.WARP_MESH)
    var deformationLiquifySubMode by mutableStateOf(LiquifySubMode.PUSH)
    var deformationBrushRadius by mutableFloatStateOf(80f)
    var deformationBrushStrength by mutableFloatStateOf(0.5f)
    var isDeformationDirty by mutableStateOf(false)
}
