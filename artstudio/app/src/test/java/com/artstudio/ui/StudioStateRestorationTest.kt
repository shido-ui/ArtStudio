package com.artstudio.ui

import androidx.lifecycle.SavedStateHandle
import com.artstudio.color.ColorData
import com.artstudio.ui.model.ActivePanel
import com.artstudio.ui.model.StudioTool
import com.artstudio.ui.viewmodel.StudioViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies process death state restoration via SavedStateHandle:
 * tool, color, viewport (pan/zoom/rotation), active panel, and project state.
 */
class StudioStateRestorationTest {

    @Test
    fun testProcessDeath_restoresToolColorViewportPanelAndProjectState() {
        val originalStateHandle = SavedStateHandle()
        val originalVm = StudioViewModel(
            initialProjectId = "test_project_123",
            savedStateHandle = originalStateHandle
        )

        // Simulate artist actions
        originalVm.selectTool(StudioTool.FILL)
        originalVm.selectColor(ColorData.fromArgb(0xFFFF4500.toInt())) // Orange-red
        originalVm.updateViewport(panX = 142.5f, panY = -88.0f, zoom = 2.75f, rotation = 45f)

        assertEquals("test_project_123", originalVm.projectId)
        assertEquals(StudioTool.FILL, originalVm.activeTool)
        assertEquals(ActivePanel.Fill, originalVm.activePanel)
        assertEquals(0xFFFF4500.toInt(), originalVm.colorState.activeColor.argbInt)
        assertEquals(142.5f, originalVm.viewportPanX, 0.001f)
        assertEquals(-88.0f, originalVm.viewportPanY, 0.001f)
        assertEquals(2.75f, originalVm.viewportZoom, 0.001f)
        assertEquals(45f, originalVm.viewportRotation, 0.001f)

        // Process Death: new VM is constructed using the preserved SavedStateHandle
        val restoredStateHandle = SavedStateHandle(
            mapOf(
                StudioViewModel.KEY_PROJECT_ID to originalStateHandle.get<String>(StudioViewModel.KEY_PROJECT_ID),
                StudioViewModel.KEY_ACTIVE_TOOL to originalStateHandle.get<String>(StudioViewModel.KEY_ACTIVE_TOOL),
                StudioViewModel.KEY_ACTIVE_PANEL to originalStateHandle.get<String>(StudioViewModel.KEY_ACTIVE_PANEL),
                StudioViewModel.KEY_ACTIVE_COLOR_ARGB to originalStateHandle.get<Int>(StudioViewModel.KEY_ACTIVE_COLOR_ARGB),
                StudioViewModel.KEY_VIEWPORT_PAN_X to originalStateHandle.get<Float>(StudioViewModel.KEY_VIEWPORT_PAN_X),
                StudioViewModel.KEY_VIEWPORT_PAN_Y to originalStateHandle.get<Float>(StudioViewModel.KEY_VIEWPORT_PAN_Y),
                StudioViewModel.KEY_VIEWPORT_ZOOM to originalStateHandle.get<Float>(StudioViewModel.KEY_VIEWPORT_ZOOM),
                StudioViewModel.KEY_VIEWPORT_ROTATION to originalStateHandle.get<Float>(StudioViewModel.KEY_VIEWPORT_ROTATION)
            )
        )

        val restoredVm = StudioViewModel(
            initialProjectId = "fallback_id",
            savedStateHandle = restoredStateHandle
        )

        assertEquals("test_project_123", restoredVm.projectId)
        assertEquals(StudioTool.FILL, restoredVm.activeTool)
        assertEquals(ActivePanel.Fill, restoredVm.activePanel)
        assertEquals(0xFFFF4500.toInt(), restoredVm.colorState.activeColor.argbInt)
        assertEquals(142.5f, restoredVm.viewportPanX, 0.001f)
        assertEquals(-88.0f, restoredVm.viewportPanY, 0.001f)
        assertEquals(2.75f, restoredVm.viewportZoom, 0.001f)
        assertEquals(45f, restoredVm.viewportRotation, 0.001f)
    }

    @Test
    fun testPanelDismissal_clearsFromSavedStateHandle() {
        val stateHandle = SavedStateHandle()
        val vm = StudioViewModel(
            initialProjectId = "test_project",
            savedStateHandle = stateHandle
        )

        vm.selectTool(StudioTool.SELECTION)
        assertEquals(ActivePanel.Selection, vm.activePanel)
        assertEquals("Selection", stateHandle.get<String>(StudioViewModel.KEY_ACTIVE_PANEL))

        vm.closeActivePanel()
        assertNull(vm.activePanel)
        assertNull(stateHandle.get<String>(StudioViewModel.KEY_ACTIVE_PANEL))
    }
}
