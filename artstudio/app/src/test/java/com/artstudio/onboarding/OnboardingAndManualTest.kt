package com.artstudio.onboarding

import com.artstudio.document.io.DocumentPackage
import com.artstudio.manual.ManualBook
import com.artstudio.ui.navigation.StudioNavRoute
import com.artstudio.ui.preferences.StudioPreferences
import com.artstudio.ui.preferences.StudioUserMode
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * Comprehensive test gate for Prompt 70:
 * - Onboarding flow preferences and state transitions
 * - In-app manual content integrity, word count budgets, and search
 * - Guided tutorial step transitions and caption word limits
 * - Route serialization/deserialization with sample read-only flag
 * - Sample project integrity and file size constraints (< 200 KB)
 */
class OnboardingAndManualTest {

    @Test
    fun testPreferencesOnboardingAndUserModes() {
        val prefs = StudioPreferences()

        // Defaults
        assertFalse(prefs.hasCompletedOnboarding)
        assertEquals(StudioUserMode.FULL, prefs.userMode)

        // Switch to Just Draw
        prefs.userMode = StudioUserMode.JUST_DRAW
        assertEquals(StudioUserMode.JUST_DRAW, prefs.userMode)

        // Switch to Learn
        prefs.userMode = StudioUserMode.LEARN
        assertEquals(StudioUserMode.LEARN, prefs.userMode)

        // Mark completed
        prefs.hasCompletedOnboarding = true
        assertTrue(prefs.hasCompletedOnboarding)

        // Reset
        prefs.resetOnboarding()
        assertFalse(prefs.hasCompletedOnboarding)
        assertEquals(StudioUserMode.FULL, prefs.userMode)
    }

    @Test
    fun testStudioUserModeParsing() {
        assertEquals(StudioUserMode.JUST_DRAW, StudioUserMode.fromString("just_draw"))
        assertEquals(StudioUserMode.LEARN, StudioUserMode.fromString("LEARN"))
        assertEquals(StudioUserMode.FULL, StudioUserMode.fromString("full"))
        assertEquals(StudioUserMode.FULL, StudioUserMode.fromString("unknown_value"))
    }

    @Test
    fun testTutorialStepProgressionAndWordLimits() {
        assertEquals(5, TutorialStep.totalSteps)

        val steps = TutorialStep.entries
        assertEquals(TutorialStep.DRAW_STROKE, steps[0])
        assertEquals(TutorialStep.CHANGE_SIZE, steps[1])
        assertEquals(TutorialStep.UNDO_STROKE, steps[2])
        assertEquals(TutorialStep.REDO_STROKE, steps[3])
        assertEquals(TutorialStep.SAVE_PROJECT, steps[4])

        for (step in steps) {
            val words = step.caption.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            assertTrue(
                "Tutorial step ${step.name} caption '${step.caption}' has ${words.size} words, must be <= 12",
                words.size <= 12
            )
            assertTrue(step.title.isNotBlank())
        }
    }

    @Test
    fun testManualJsonIntegrityAndWordLimits() {
        val candidates = listOf(
            File("app/src/main/assets/manual/manual.json"),
            File("src/main/assets/manual/manual.json"),
            File("../app/src/main/assets/manual/manual.json")
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("manual.json must exist in assets", file)

        val book = file!!.inputStream().use { ManualBook.loadFromStream(it) }
        assertEquals(11, book.chapters.size)

        val expectedChapterIds = listOf(
            "getting_started",
            "manual_studio",
            "brush_engine",
            "vector_tools",
            "comic_tools",
            "animation",
            "autodraw",
            "colour_lab",
            "file_interchange",
            "pro_library",
            "troubleshooting"
        )

        for (expectedId in expectedChapterIds) {
            val ch = book.chapters.find { it.id == expectedId }
            assertNotNull("Chapter '$expectedId' must exist in manual.json", ch)
            assertTrue("Chapter intro must not be blank", ch!!.intro.isNotBlank())
            assertTrue("Chapter must have sections", ch.sections.isNotEmpty())

            val introWords = ch.intro.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            assertTrue(
                "Chapter '$expectedId' intro has ${introWords.size} words (limit: <= 40)",
                introWords.size <= 40
            )

            for (sec in ch.sections) {
                val secWords = sec.body.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                assertTrue(
                    "Section '${sec.id}' in chapter '$expectedId' has ${secWords.size} words (limit: <= 80)",
                    secWords.size <= 80
                )
                assertFalse("Must not have placeholder text in ${sec.id}", sec.body.contains("TODO", ignoreCase = true))
                assertFalse("Must not have placeholder text in ${sec.id}", sec.body.contains("Lorem ipsum", ignoreCase = true))
            }
        }
    }

    @Test
    fun testManualSearchFunctionality() {
        val candidates = listOf(
            File("app/src/main/assets/manual/manual.json"),
            File("src/main/assets/manual/manual.json"),
            File("../app/src/main/assets/manual/manual.json")
        )
        val file = candidates.firstOrNull { it.exists() }!!
        val book = file.inputStream().use { ManualBook.loadFromStream(it) }

        // Search empty
        val all = book.search("")
        assertEquals(11, all.size)

        // Search specific keyword
        val brushResults = book.search("brush")
        assertTrue(brushResults.isNotEmpty())
        assertTrue(brushResults.any { it.id == "brush_engine" })

        // Search non-existent
        val none = book.search("xyznonexistentterm999")
        assertTrue(none.isEmpty())
    }

    @Test
    fun testNavRouteSerializationWithSampleFlag() {
        val regularRoute = StudioNavRoute.ManualStudio("proj_1", "My Art", isReadOnlySample = false)
        val serialized1 = regularRoute.serialize()
        val restored1 = StudioNavRoute.deserialize(serialized1)
        assertTrue(restored1 is StudioNavRoute.ManualStudio)
        val m1 = restored1 as StudioNavRoute.ManualStudio
        assertEquals("proj_1", m1.projectId)
        assertEquals("My Art", m1.projectTitle)
        assertFalse(m1.isReadOnlySample)

        val sampleRoute = StudioNavRoute.ManualStudio("sample_doodle", "Doodle", isReadOnlySample = true)
        val serialized2 = sampleRoute.serialize()
        val restored2 = StudioNavRoute.deserialize(serialized2)
        assertTrue(restored2 is StudioNavRoute.ManualStudio)
        val m2 = restored2 as StudioNavRoute.ManualStudio
        assertEquals("sample_doodle", m2.projectId)
        assertEquals("Doodle", m2.projectTitle)
        assertTrue(m2.isReadOnlySample)

        // Onboarding and Manual routes
        val onbRoute = StudioNavRoute.Onboarding
        assertEquals(StudioNavRoute.Onboarding, StudioNavRoute.deserialize(onbRoute.serialize()))

        val manRoute = StudioNavRoute.InAppManual("colour_lab")
        val restoredMan = StudioNavRoute.deserialize(manRoute.serialize()) as StudioNavRoute.InAppManual
        assertEquals("colour_lab", restoredMan.chapterId)
    }

    @Test
    fun testSampleProjectsBundledIntegrityAndBudgets() {
        val sampleDirCandidates = listOf(
            File("app/src/main/assets/samples"),
            File("src/main/assets/samples"),
            File("../app/src/main/assets/samples")
        )
        val dir = sampleDirCandidates.firstOrNull { it.exists() }
        assertNotNull("assets/samples directory must exist", dir)

        val samples = listOf("sample_doodle.ffart", "sample_comic_panel.ffart", "sample_auto_trace.ffart")
        var totalBytes = 0L

        for (sampleName in samples) {
            val sampleFile = File(dir, sampleName)
            assertTrue("Sample file $sampleName must exist", sampleFile.exists())
            assertTrue("Sample $sampleName size must be > 1 KB", sampleFile.length() > 1024)
            assertTrue("Sample $sampleName size must be < 200 KB", sampleFile.length() < 200 * 1024)
            totalBytes += sampleFile.length()

            // Verify DocumentPackage can load it
            val loaded = DocumentPackage.load(sampleFile)
            assertNotNull(loaded.document.id)
            assertTrue(loaded.document.canvas.width > 0)
            assertTrue(loaded.document.canvas.height > 0)
            assertTrue(loaded.document.allLayersFlat().isNotEmpty())
        }

        // Entire sample pack must be strictly < 500 KB total
        assertTrue("Total sample packs size $totalBytes must be < 500 KB", totalBytes < 500 * 1024)
    }
}
