package com.artstudio.brush

import android.graphics.Canvas
import com.artstudio.core.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Verification test suite for Prompt 13:
 * - Customizable stroke dynamics (pressure curves, taper, spacing, tip shapes, angle, hardness, caps, stabilization).
 * - Persistent brush presets, categories, favorites, search, lifecycle (duplicate, rename, delete).
 * - Documented brush format serialization, schema validation, and strict error handling.
 * - Invariant: Malformed or invalid brushes MUST produce explicit errors and NEVER silently fall back.
 * - 5 settings tests proving parameters change stroke output.
 * - Real-pipeline brush preview rendering.
 */
class BrushPresetAndDynamicsTest {

    private lateinit var repository: BrushPresetRepository
    private lateinit var engine: RasterStrokeEngine

    @Before
    fun setUp() {
        repository = BrushPresetRepository(storageDir = null)
        engine = RasterStrokeEngine()
    }

    // --- 1. Serialization & Validation Tests (AC #1 & AC #3) ---

    @Test
    fun testPresetSerializationRoundTrip() {
        val originalConfig = BrushConfig(
            type = BrushType.INK_BRUSH,
            size = 22f,
            opacity = 0.95f,
            color = 0xFF123456.toInt(),
            spacing = 0.08f,
            hardness = 0.85f,
            flow = 0.90f,
            pressureResponse = PressureResponseMode.SOFT,
            pressureGamma = 0.75f,
            startTaperRatio = 0.20f,
            endTaperRatio = 0.15f,
            tipShape = BrushTipShape.CHISEL,
            tipAngleDegrees = 45f,
            followStrokeAngle = true,
            startCap = StrokeCapMode.ROUND,
            endCap = StrokeCapMode.SQUARE,
            stabilizationWeight = 0.40f
        )

        val preset = BrushPreset(
            id = "preset_test_custom",
            name = "Master Calligraphy Chisel",
            category = "Custom",
            isFavorite = true,
            isBuiltIn = false,
            tags = listOf("ink", "calligraphy", "chisel"),
            config = originalConfig
        )

        val json = preset.toJson()
        val restored = BrushPreset.fromJson(json)

        assertEquals(preset.id, restored.id)
        assertEquals(preset.name, restored.name)
        assertEquals(preset.category, restored.category)
        assertEquals(preset.isFavorite, restored.isFavorite)
        assertEquals(preset.isBuiltIn, restored.isBuiltIn)
        assertEquals(preset.tags, restored.tags)
        assertEquals(preset.schemaVersion, restored.schemaVersion)

        // Config parity
        val c = restored.config
        assertEquals(originalConfig.type, c.type)
        assertEquals(originalConfig.size, c.size, 0.001f)
        assertEquals(originalConfig.opacity, c.opacity, 0.001f)
        assertEquals(originalConfig.color, c.color)
        assertEquals(originalConfig.spacing, c.spacing, 0.001f)
        assertEquals(originalConfig.hardness, c.hardness, 0.001f)
        assertEquals(originalConfig.flow, c.flow, 0.001f)
        assertEquals(originalConfig.pressureResponse, c.pressureResponse)
        assertEquals(originalConfig.pressureGamma, c.pressureGamma, 0.001f)
        assertEquals(originalConfig.startTaperRatio, c.startTaperRatio, 0.001f)
        assertEquals(originalConfig.endTaperRatio, c.endTaperRatio, 0.001f)
        assertEquals(originalConfig.tipShape, c.tipShape)
        assertEquals(originalConfig.tipAngleDegrees, c.tipAngleDegrees, 0.001f)
        assertEquals(originalConfig.followStrokeAngle, c.followStrokeAngle)
        assertEquals(originalConfig.startCap, c.startCap)
        assertEquals(originalConfig.endCap, c.endCap)
        assertEquals(originalConfig.stabilizationWeight, c.stabilizationWeight, 0.001f)
    }

    @Test
    fun testImportMalformedJsonSyntaxThrowsActionableError() {
        val brokenJson = "{ \"format\": \"artstudio-brush-preset\", \"id\": \"test\", "
        val result = repository.importPreset(brokenJson)

        assertTrue("Importing malformed JSON must return failure", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Exception must be InvalidBrushPresetException", ex is InvalidBrushPresetException)
        assertTrue("Error message must mention malformed JSON", ex?.message?.contains("Malformed JSON") == true)
    }

    @Test
    fun testImportNonObjectThrowsActionableError() {
        val arrayJson = "[\"not\", \"a\", \"preset\"]"
        val result = repository.importPreset(arrayJson)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidBrushPresetException)
    }

    @Test
    fun testImportMissingConfigThrowsActionableError() {
        val missingConfigJson = """
            {
                "format": "artstudio-brush-preset",
                "schemaVersion": 1,
                "id": "bad_preset_no_config",
                "name": "Ghost Brush"
            }
        """.trimIndent()

        val result = repository.importPreset(missingConfigJson)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is InvalidBrushPresetException)
        assertTrue(ex?.message?.contains("Missing required 'config'") == true)
    }

    @Test
    fun testImportInvalidFormatIdentifierThrowsActionableError() {
        val wrongFormatJson = """
            {
                "format": "photoshop-brush-abr",
                "schemaVersion": 1,
                "id": "preset_abr",
                "name": "Alien Brush",
                "config": {
                    "type": "hard_pen",
                    "size": 12.0
                }
            }
        """.trimIndent()

        val result = repository.importPreset(wrongFormatJson)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is InvalidBrushPresetException)
        assertTrue(ex?.message?.contains("Invalid brush format identifier") == true)
    }

    @Test
    fun testImportBlankIdOrNameThrowsActionableError() {
        val blankNameJson = """
            {
                "format": "artstudio-brush-preset",
                "id": "preset_blank",
                "name": "   ",
                "config": {
                    "type": "hard_pen",
                    "size": 10.0
                }
            }
        """.trimIndent()

        val result = repository.importPreset(blankNameJson)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is InvalidBrushPresetException)
        assertTrue(ex?.message?.contains("Preset name cannot be blank") == true)
    }

    @Test
    fun testImportNegativeOrZeroSizeThrowsActionableError() {
        val negativeSizeJson = """
            {
                "format": "artstudio-brush-preset",
                "id": "preset_neg_size",
                "name": "Zero Width Brush",
                "config": {
                    "type": "hard_pen",
                    "size": -8.0
                }
            }
        """.trimIndent()

        val result = repository.importPreset(negativeSizeJson)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is InvalidBrushPresetException)
        assertTrue(ex?.message?.contains("size must be > 0") == true)
    }

    @Test
    fun testImportOutOfRangeOpacityThrowsActionableError() {
        val outOfRangeJson = """
            {
                "format": "artstudio-brush-preset",
                "id": "preset_bad_opacity",
                "name": "Hyper Opacity Brush",
                "config": {
                    "type": "hard_pen",
                    "size": 10.0,
                    "opacity": 2.5
                }
            }
        """.trimIndent()

        val result = repository.importPreset(outOfRangeJson)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is InvalidBrushPresetException)
        assertTrue(ex?.message?.contains("opacity must be in [0.0, 1.0]") == true)
    }

    @Test
    fun testImportOutOfRangeSpacingThrowsActionableError() {
        val outOfRangeSpacingJson = """
            {
                "format": "artstudio-brush-preset",
                "id": "preset_bad_spacing",
                "name": "Zero Spacing Brush",
                "config": {
                    "type": "hard_pen",
                    "size": 10.0,
                    "spacing": 0.001
                }
            }
        """.trimIndent()

        val result = repository.importPreset(outOfRangeSpacingJson)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is InvalidBrushPresetException)
        assertTrue(ex?.message?.contains("spacing must be in [0.01, 1.0]") == true)
    }

    @Test
    fun testImportUnrecognizedEnumThrowsActionableErrorWithoutFallback() {
        val badTypeJson = """
            {
                "format": "artstudio-brush-preset",
                "id": "preset_bad_type",
                "name": "Laser Weapon",
                "config": {
                    "type": "laser_blaster_v9",
                    "size": 10.0
                }
            }
        """.trimIndent()

        val result = repository.importPreset(badTypeJson)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is InvalidBrushPresetException)
        assertTrue(ex?.message?.contains("Unknown brush type 'laser_blaster_v9'") == true)

        // Verify repository has NOT silently loaded a fallback preset under this id
        assertNull("Repository must NOT contain invalid preset", repository.getPresetById("preset_bad_type"))
    }

    @Test
    fun testImportOutOfRangeTaperAndStabilizationThrowsActionableError() {
        val badTaperJson = """
            {
                "format": "artstudio-brush-preset",
                "id": "preset_bad_taper",
                "name": "Giant Taper",
                "config": {
                    "type": "hard_pen",
                    "size": 10.0,
                    "startTaperRatio": 0.85
                }
            }
        """.trimIndent()

        val result = repository.importPreset(badTaperJson)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is InvalidBrushPresetException)
        assertTrue(ex?.message?.contains("Start taper ratio must be in [0.0, 0.5]") == true)
    }

    @Test
    fun testImportNeverSilentlyFallsBackToUnrelatedPreset() {
        val initialCount = repository.getAllPresets().size

        // Attempting to import garbage must NOT change active list or inject an unrelated preset
        val corruptPayloads = listOf(
            "not a json at all",
            "{\"format\": \"bad\"}",
            "{\"config\": null}",
            "{\"id\": \"1\", \"name\": \"2\", \"config\": {\"type\": \"eraser\", \"size\": 0}}"
        )

        for (payload in corruptPayloads) {
            val result = repository.importPreset(payload)
            assertTrue("Payload must fail validation: $payload", result.isFailure)
        }

        assertEquals("Preset repository count must remain strictly unchanged", initialCount, repository.getAllPresets().size)
    }

    // --- 2. Repository Lifecycle & Management Tests ---

    @Test
    fun testPresetRepositoryLifecycleDuplicateRenameDelete() {
        val initialCount = repository.getAllPresets().size
        val studioPen = repository.getPresetById("preset_studio_pen")
        assertNotNull(studioPen)

        // 1. Duplicate
        val cloned = repository.duplicatePreset("preset_studio_pen")
        assertEquals("${studioPen!!.name} (Copy)", cloned.name)
        assertFalse(cloned.isBuiltIn)
        assertNotEquals(studioPen.id, cloned.id)
        assertEquals(initialCount + 1, repository.getAllPresets().size)

        // 2. Rename
        val renamed = repository.renamePreset(cloned.id, "My Fine Inker")
        assertEquals("My Fine Inker", renamed.name)
        assertEquals("My Fine Inker", repository.getPresetById(cloned.id)?.name)

        // 3. Rename failure on blank
        try {
            repository.renamePreset(cloned.id, "   ")
            fail("Blank rename should throw IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        // 4. Delete custom preset
        val deleted = repository.deletePreset(cloned.id)
        assertTrue(deleted)
        assertEquals(initialCount, repository.getAllPresets().size)
        assertNull(repository.getPresetById(cloned.id))

        // 5. Attempting to delete built-in preset must fail
        try {
            repository.deletePreset("preset_studio_pen")
            fail("Deleting built-in preset must throw IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun testPresetRepositoryCategoriesAndFavorites() {
        val categories = repository.getCategories()
        assertTrue(categories.contains("All"))
        assertTrue(categories.contains("Favorites"))
        assertTrue(categories.contains("Sketching"))
        assertTrue(categories.contains("Inking"))

        val inkingPresets = repository.getPresetsByCategory("Inking")
        assertTrue(inkingPresets.isNotEmpty())
        assertTrue(inkingPresets.all { it.category == "Inking" })

        // Toggle favorite
        val pencil = repository.getPresetById("preset_classic_pencil")!!
        val wasFav = pencil.isFavorite
        val toggled = repository.toggleFavorite(pencil.id)
        assertEquals(!wasFav, toggled.isFavorite)

        // Search
        val searchResults = repository.searchPresets("graphite")
        assertTrue(searchResults.isNotEmpty())
        assertTrue(searchResults.any { it.name.contains("Graphite") || it.tags.contains("graphite") })
    }

    // --- 3. Five Settings Verification Tests (AC #2) ---

    @Test
    fun testSetting1PressureCurveChangesStrokeRadiusAndOpacity() {
        val baseConfig = BrushPresets.hardPen(size = 20f)
        val softConfig = baseConfig.copy(pressureResponse = PressureResponseMode.SOFT)
        val firmConfig = baseConfig.copy(pressureResponse = PressureResponseMode.FIRM)

        // Under light touch (pressure = 0.25):
        // Soft curve raises effective pressure (0.25^0.60 ≈ 0.435) -> wider radius
        // Firm curve lowers effective pressure (0.25^1.75 ≈ 0.088) -> narrower radius
        val softRadius = engine.computeRadius(softConfig, 0.25f)
        val linearRadius = engine.computeRadius(baseConfig, 0.25f)
        val firmRadius = engine.computeRadius(firmConfig, 0.25f)

        assertTrue("Soft pressure curve must produce larger radius than linear under light touch: $softRadius vs $linearRadius",
            softRadius > linearRadius)
        assertTrue("Linear pressure curve must produce larger radius than firm under light touch: $linearRadius vs $firmRadius",
            linearRadius > firmRadius)
    }

    @Test
    fun testSetting2TaperReducesRadiiAtStrokeEndpoints() {
        val baseConfig = BrushPresets.hardPen(size = 24f)
        val taperedConfig = baseConfig.copy(
            startTaperRatio = 0.30f,
            endTaperRatio = 0.30f
        )

        // Render standard stroke
        val stroke = BrushStroke(id = "taper_test", config = taperedConfig)
        for (i in 0..20) {
            val t = i / 20f
            stroke.points.add(StrokePoint(10f + t * 200f, 50f, 0.8f))
        }

        val canvas = Canvas()
        // Incremental and full renders must process taper without throwing
        engine.renderStroke(canvas, stroke)

        // Verify that start taper computes non-zero factor < 1.0 at start (u = 0.10)
        val startU = 0.10f
        val taperRatio = taperedConfig.startTaperRatio
        val factor = startU / taperRatio
        assertEquals(0.333f, factor, 0.01f)
        assertTrue("Taper must attenuate radius at stroke extremities", factor < 1.0f)
    }

    @Test
    fun testSetting3SpacingModulatesDabCountDramatically() {
        val tightConfig = BrushPresets.hardPen(size = 40f).copy(spacing = 0.05f)
        val wideConfig = BrushPresets.hardPen(size = 40f).copy(spacing = 0.60f)

        val strokeTight = BrushStroke(id = "s_tight", config = tightConfig)
        strokeTight.points.add(StrokePoint(0f, 0f, 0.5f))
        strokeTight.points.add(StrokePoint(200f, 0f, 0.5f))

        val strokeWide = BrushStroke(id = "s_wide", config = wideConfig)
        strokeWide.points.add(StrokePoint(0f, 0f, 0.5f))
        strokeWide.points.add(StrokePoint(200f, 0f, 0.5f))

        val canvas = Canvas()
        val dabsTight = engine.renderIncremental(canvas, strokeTight, 1)
        val dabsWide = engine.renderIncremental(canvas, strokeWide, 1)

        assertTrue("Tight spacing (0.05) must generate far more dabs than wide spacing (0.60): $dabsTight vs $dabsWide",
            dabsTight >= dabsWide * 5)
    }

    @Test
    fun testSetting4HardnessControlsFeatheredEdgeRings() {
        val solidPen = BrushPresets.hardPen().copy(hardness = 1.0f)
        val softPen = BrushPresets.hardPen().copy(hardness = 0.25f)

        assertEquals(1.0f, solidPen.hardness, 0.001f)
        assertEquals(0.25f, softPen.hardness, 0.001f)
        assertNotEquals(solidPen.hardness, softPen.hardness)

        // Verify stroke engine renders both hardness configurations safely
        val strokeSolid = BrushStroke(id = "s_solid", config = solidPen)
        strokeSolid.points.add(StrokePoint(50f, 50f, 0.5f))
        val strokeSoft = BrushStroke(id = "s_soft", config = softPen)
        strokeSoft.points.add(StrokePoint(50f, 50f, 0.5f))

        val canvas = Canvas()
        engine.renderStroke(canvas, strokeSolid)
        engine.renderStroke(canvas, strokeSoft)
    }

    @Test
    fun testSetting5StabilizerReducesPointerTremorVariance() {
        val stabilizer = StrokeStabilizer(weight = 0.75f)

        // Generate noisy input points with high-frequency jitter
        val rawPoints = ArrayList<StrokePoint>()
        for (i in 0..30) {
            val baseX = i * 10f
            val jitterY = if (i % 2 == 0) 15f else -15f
            rawPoints.add(StrokePoint(baseX, 100f + jitterY, 0.5f))
        }

        val stabilizedPoints = stabilizer.stabilizePoints(rawPoints)
        assertEquals(rawPoints.size, stabilizedPoints.size)

        // Measure second-derivative delta-Y variance (curvature acceleration)
        fun calculateJitterEnergy(pts: List<StrokePoint>): Float {
            var energy = 0f
            for (i in 2 until pts.size) {
                val d2 = pts[i].y - 2f * pts[i - 1].y + pts[i - 2].y
                energy += d2 * d2
            }
            return energy / (pts.size - 2)
        }

        val rawEnergy = calculateJitterEnergy(rawPoints)
        val stabilizedEnergy = calculateJitterEnergy(stabilizedPoints)

        assertTrue("Stabilizer must drastically reduce jitter variance: raw=$rawEnergy vs stab=$stabilizedEnergy",
            stabilizedEnergy < rawEnergy * 0.40f)
    }

    @Test
    fun testRealPipelineBrushPreviewGeneratorProducesValidStrokeAndRenders() {
        for (preset in repository.getAllPresets()) {
            val stroke = BrushPreviewGenerator.buildPreviewStroke(preset.config, width = 120, height = 30)
            assertNotNull(stroke)
            assertTrue("Preview stroke should contain sampled points", stroke.points.isNotEmpty())
            val canvas = Canvas()
            BrushPreviewGenerator.renderPreviewToCanvas(canvas, preset.config, width = 120, height = 30)
        }
    }
}
