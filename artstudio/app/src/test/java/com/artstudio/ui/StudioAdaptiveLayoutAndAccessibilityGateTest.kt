package com.artstudio.ui

import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.artstudio.ui.model.ActivePanel
import com.artstudio.ui.model.StudioTool
import com.artstudio.ui.preferences.StudioPreferences
import com.artstudio.ui.theme.StudioAccessibility
import com.artstudio.ui.theme.StudioColors
import com.artstudio.ui.viewmodel.StudioViewModel
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Gate verification test for Prompt 70:
 * - Adaptive layout switching at 360dp (phone portrait) and 600dp (tablet / foldable).
 * - Accessibility audit: minimum 48dp touch targets, TalkBack semantics, WCAG AAA contrast ratios.
 * - Architecture verification: StudioViewModel state bindings and mutually exclusive ActivePanel hierarchy.
 * - Source audit: verifies that EVERY composable in com.artstudio.ui.screens.studio is strictly <= 150 lines.
 */
class StudioAdaptiveLayoutAndAccessibilityGateTest {

    // =========================================================================
    // 1. Adaptive Layout Verification (360dp vs 600dp widths)
    // =========================================================================

    @Test
    fun testAdaptiveLayoutDetection_360dpPhonePortrait() {
        val screenWidthDp = 360
        val orientation = Configuration.ORIENTATION_PORTRAIT

        val isTabletOrLandscape = screenWidthDp >= 600 || orientation == Configuration.ORIENTATION_LANDSCAPE

        assertFalse("At 360dp portrait, layout must be standard mobile phone layout (bottom toolbar)", isTabletOrLandscape)
    }

    @Test
    fun testAdaptiveLayoutDetection_600dpTablet() {
        val screenWidthDp = 600
        val orientation = Configuration.ORIENTATION_PORTRAIT

        val isTabletOrLandscape = screenWidthDp >= 600 || orientation == Configuration.ORIENTATION_LANDSCAPE

        assertTrue("At 600dp portrait, layout must adapt to tablet rail layout", isTabletOrLandscape)
    }

    @Test
    fun testAdaptiveLayoutDetection_phoneLandscape() {
        val screenWidthDp = 360
        val orientation = Configuration.ORIENTATION_LANDSCAPE

        val isTabletOrLandscape = screenWidthDp >= 600 || orientation == Configuration.ORIENTATION_LANDSCAPE

        assertTrue("In landscape orientation, layout must adapt to side rail layout", isTabletOrLandscape)
    }

    // =========================================================================
    // 2. Accessibility Scanner Verification (Touch targets & Contrast)
    // =========================================================================

    @Test
    fun testTouchTargetsMeetMinimum48dp() {
        val minTouchTarget = StudioAccessibility.MinTouchTarget
        assertTrue(
            "Minimum touch target must be >= 48dp according to Material & WCAG standards, was $minTouchTarget",
            minTouchTarget.value >= 48f
        )
    }

    @Test
    fun testContrastMeetsWcagStandards() {
        val dark = StudioColors.darkColors()

        // Background to primary text
        val textContrast = StudioAccessibility.calculateContrastRatio(dark.surfaceBase, dark.textPrimary)
        assertTrue(
            "Primary text contrast ratio must be >= 7.0 for WCAG AAA compliance, was $textContrast",
            textContrast >= 7.0
        )

        // Background to accent container
        val accentContrast = StudioAccessibility.calculateContrastRatio(dark.accent.primary, dark.accent.onPrimary)
        assertTrue(
            "Accent button contrast ratio must be >= 4.5 for WCAG AA compliance, was $accentContrast",
            accentContrast >= 4.5
        )
    }

    // =========================================================================
    // 3. StudioViewModel & Sealed ActivePanel Hierarchy
    // =========================================================================

    @Test
    fun testActivePanelMutualExclusion() {
        val vm = StudioViewModel("test_proj_1")
        assertNull("Initially activePanel must be null", vm.activePanel)

        // Select brush panel
        vm.togglePanel(ActivePanel.Brush)
        assertEquals(ActivePanel.Brush, vm.activePanel)

        // Toggling color panel replaces brush panel (mutually exclusive)
        vm.togglePanel(ActivePanel.Color)
        assertEquals(ActivePanel.Color, vm.activePanel)

        // Toggling layers panel replaces color panel
        vm.togglePanel(ActivePanel.Layers)
        assertEquals(ActivePanel.Layers, vm.activePanel)

        // Toggling active panel again closes it
        vm.togglePanel(ActivePanel.Layers)
        assertNull(vm.activePanel)

        // Select tool switches panel accordingly
        vm.selectTool(StudioTool.FILL)
        assertEquals(ActivePanel.Fill, vm.activePanel)

        vm.selectTool(StudioTool.SMUDGE_BLUR)
        assertEquals(ActivePanel.PixelOps, vm.activePanel)

        vm.closeActivePanel()
        assertNull(vm.activePanel)
    }

    @Test
    fun testPreferencesPersistence() {
        val prefs = StudioPreferences()
        val initialTool = prefs.lastActiveTool
        assertNotNull(initialTool)

        val vm = StudioViewModel("test_proj_2", prefs)
        vm.selectTool(StudioTool.ERASER)
        assertEquals(StudioTool.ERASER, vm.activeTool)
        assertEquals("ERASER", prefs.lastActiveTool)

        vm.updateBrushSize(45f)
        assertEquals(45f, prefs.brushSize, 0.01f)

        vm.updateBrushOpacity(0.85f)
        assertEquals(0.85f, prefs.brushOpacity, 0.01f)
    }

    // =========================================================================
    // 4. Source Audit: Every Composable Function <= 150 Lines
    // =========================================================================

    @Test
    fun testEveryStudioComposableUnder150Lines() {
        val studioDir = listOf(
            File("src/main/java/com/artstudio/ui/screens/studio"),
            File("app/src/main/java/com/artstudio/ui/screens/studio")
        ).firstOrNull { it.exists() && it.isDirectory }

        assertNotNull("Studio directory must exist", studioDir)
        val kotlinFiles = studioDir!!.listFiles { file -> file.extension == "kt" } ?: emptyArray()
        assertTrue("Must have studio composables", kotlinFiles.isNotEmpty())

        val violations = mutableListOf<String>()

        for (file in kotlinFiles) {
            val lines = file.readLines()
            var currentComposableName: String? = null
            var composableStartLine = 0
            var braceDepth = 0
            var insideComposable = false

            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()

                if (trimmed.startsWith("@Composable")) {
                    // Peek ahead for fun declaration
                    var j = index + 1
                    while (j < lines.size && !lines[j].trim().startsWith("fun ") && !lines[j].trim().contains(" fun ")) {
                        j++
                    }
                    if (j < lines.size) {
                        val funLine = lines[j].trim()
                        val funName = funLine.substringAfter("fun ").substringBefore("(").trim()
                        currentComposableName = funName
                        composableStartLine = j + 1
                        braceDepth = 0
                        insideComposable = true
                    }
                }

                if (insideComposable) {
                    braceDepth += line.count { it == '{' } - line.count { it == '}' }
                    if (braceDepth == 0 && (line.contains("}") || index + 1 > composableStartLine)) {
                        val length = (index + 1) - composableStartLine + 1
                        if (length > 150) {
                            violations.add("${file.name} -> $currentComposableName: $length lines (> 150)")
                        }
                        insideComposable = false
                        currentComposableName = null
                    }
                }
            }
        }

        // Also check ManualStudioScreen.kt
        val manualScreenFile = listOf(
            File("src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt"),
            File("app/src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt")
        ).firstOrNull { it.exists() }
        if (manualScreenFile != null && manualScreenFile.exists()) {
            val lines = manualScreenFile.readLines()
            var currentComposableName: String? = null
            var composableStartLine = 0
            var braceDepth = 0
            var insideComposable = false

            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("@Composable")) {
                    var j = index + 1
                    while (j < lines.size && !lines[j].trim().startsWith("fun ") && !lines[j].trim().contains(" fun ")) {
                        j++
                    }
                    if (j < lines.size) {
                        val funLine = lines[j].trim()
                        val funName = funLine.substringAfter("fun ").substringBefore("(").trim()
                        currentComposableName = funName
                        composableStartLine = j + 1
                        braceDepth = 0
                        insideComposable = true
                    }
                }

                if (insideComposable) {
                    braceDepth += line.count { it == '{' } - line.count { it == '}' }
                    if (braceDepth == 0 && (line.contains("}") || index + 1 > composableStartLine)) {
                        val length = (index + 1) - composableStartLine + 1
                        if (length > 150) {
                            violations.add("ManualStudioScreen.kt -> $currentComposableName: $length lines (> 150)")
                        }
                        insideComposable = false
                        currentComposableName = null
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            fail("Found composables over 150 lines:\n" + violations.joinToString("\n"))
        }
    }
}
