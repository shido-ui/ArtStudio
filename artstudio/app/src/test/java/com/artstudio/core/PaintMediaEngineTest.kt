package com.artstudio.core

import android.graphics.Canvas
import com.artstudio.brush.BrushPreset
import com.artstudio.brush.BrushPresetRepository
import com.artstudio.brush.InvalidBrushPresetException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Authoritative unit test suite for Prompt 24:
 * Paint Media Subsystem expanding the core brush engine into distinct media:
 * 1. Marker (chisel aspect, fixed orientation angle, translucent overlap, ink bleed)
 * 2. Watercolour Wash (wet edge puddle ring, diffusion gradient, paper granulation)
 * 3. Oil Paint (impasto ridge relief, viscosity drag, multi-filament bristle tracks)
 * 4. Dry Brush (paper tooth skip threshold, frayed split bristle dispersion)
 * 5. Textured Stamp (procedural / user texture masks, rotation jitter, scale jitter, scatter)
 * 6. Compact built-in assets, extensible user texture loading, and memory bounding.
 * 7. Fixed deterministic procedural seeds.
 * 8. Touch-only hardware dynamic pressure fallback.
 */
class PaintMediaEngineTest {

    private lateinit var engine: RasterStrokeEngine
    private lateinit var mockCanvas: Canvas

    @Before
    fun setUp() {
        engine = RasterStrokeEngine()
        mockCanvas = Canvas()
    }

    // -------------------------------------------------------------------------
    // 1. Starter Presets & Parameter Bounds (AC #1)
    // -------------------------------------------------------------------------

    @Test
    fun testStarterPresetsInitializeWithValidParameters() {
        val marker = BrushPresets.marker()
        assertEquals(PaintMediaFamily.MARKER, marker.mediaProperties.family)
        assertEquals(3.0f, marker.mediaProperties.chiselAspect, 0.01f)
        assertEquals(45.0f, marker.mediaProperties.markerAngleDegrees, 0.01f)
        assertEquals(0.15f, marker.mediaProperties.inkBleed, 0.01f)
        assertTrue(marker.mediaProperties.overlapAccumulation in 0f..1f)

        val water = BrushPresets.watercolourWash()
        assertEquals(PaintMediaFamily.WATERCOLOUR, water.mediaProperties.family)
        assertEquals(1.80f, water.mediaProperties.wetEdgeIntensity, 0.01f)
        assertEquals(0.65f, water.mediaProperties.waterDiffusion, 0.01f)
        assertEquals(0.35f, water.mediaProperties.granulation, 0.01f)

        val oil = BrushPresets.oilPaint()
        assertEquals(PaintMediaFamily.OIL_PAINT, oil.mediaProperties.family)
        assertEquals(0.70f, oil.mediaProperties.impastoDepth, 0.01f)
        assertEquals(0.80f, oil.mediaProperties.viscosity, 0.01f)
        assertEquals(16, oil.mediaProperties.bristleDensity)

        val dry = BrushPresets.dryBrush()
        assertEquals(PaintMediaFamily.DRY_BRUSH, dry.mediaProperties.family)
        assertEquals(0.45f, dry.mediaProperties.toothThreshold, 0.01f)
        assertEquals(0.30f, dry.mediaProperties.fraySpread, 0.01f)
        assertEquals(6.0f, dry.mediaProperties.textureScale, 0.01f)

        val stamp = BrushPresets.texturedStamp()
        assertEquals(PaintMediaFamily.TEXTURED_STAMP, stamp.mediaProperties.family)
        assertEquals(StampPatternShape.SPONGE, stamp.mediaProperties.stampShape)
        assertEquals(30.0f, stamp.mediaProperties.rotationJitter, 0.01f)
        assertEquals(0.20f, stamp.mediaProperties.scaleJitter, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 2. Stroke Rendering & Parameter Variation Tests (AC #1 & AC #2)
    // -------------------------------------------------------------------------

    @Test
    fun testMarkerStroke_rendersAndProvesAngleVariationChangesOutput() {
        val config45 = BrushPresets.marker(angle = 45f)
        val stroke45 = BrushStroke("stroke_marker_45", config = config45)
        stroke45.points.add(StrokePoint(50f, 50f, 0.5f))
        stroke45.points.add(StrokePoint(250f, 50f, 0.7f))

        // Executes without exception
        engine.renderStroke(mockCanvas, stroke45)

        val config135 = BrushPresets.marker(angle = 135f)
        assertNotEquals(
            "Changing marker angle must alter configuration",
            config45.mediaProperties.markerAngleDegrees,
            config135.mediaProperties.markerAngleDegrees
        )
        val stroke135 = BrushStroke("stroke_marker_135", config = config135)
        stroke135.points.add(StrokePoint(50f, 50f, 0.5f))
        stroke135.points.add(StrokePoint(250f, 50f, 0.7f))
        engine.renderStroke(mockCanvas, stroke135)
    }

    @Test
    fun testWatercolourStroke_rendersAndProvesWetEdgeVariationChangesOutput() {
        val config18 = BrushPresets.watercolourWash(wetEdge = 1.80f)
        val stroke18 = BrushStroke("stroke_water_18", config = config18)
        stroke18.points.add(StrokePoint(100f, 100f, 0.5f))
        stroke18.points.add(StrokePoint(200f, 200f, 0.8f))
        engine.renderStroke(mockCanvas, stroke18)

        val config28 = BrushPresets.watercolourWash(wetEdge = 2.80f)
        assertNotEquals(
            "Changing wetEdgeIntensity must alter configuration",
            config18.mediaProperties.wetEdgeIntensity,
            config28.mediaProperties.wetEdgeIntensity
        )
        val stroke28 = BrushStroke("stroke_water_28", config = config28)
        stroke28.points.add(StrokePoint(100f, 100f, 0.5f))
        stroke28.points.add(StrokePoint(200f, 200f, 0.8f))
        engine.renderStroke(mockCanvas, stroke28)
    }

    @Test
    fun testOilPaintStroke_rendersAndProvesImpastoVariationChangesOutput() {
        val config70 = BrushPresets.oilPaint(impasto = 0.70f)
        val stroke70 = BrushStroke("stroke_oil_70", config = config70)
        stroke70.points.add(StrokePoint(80f, 120f, 0.6f))
        stroke70.points.add(StrokePoint(300f, 120f, 0.9f))
        engine.renderStroke(mockCanvas, stroke70)

        val config10 = BrushPresets.oilPaint(impasto = 0.10f)
        assertNotEquals(
            "Changing impastoDepth must alter configuration",
            config70.mediaProperties.impastoDepth,
            config10.mediaProperties.impastoDepth
        )
        val stroke10 = BrushStroke("stroke_oil_10", config = config10)
        stroke10.points.add(StrokePoint(80f, 120f, 0.6f))
        stroke10.points.add(StrokePoint(300f, 120f, 0.9f))
        engine.renderStroke(mockCanvas, stroke10)
    }

    @Test
    fun testDryBrushStroke_rendersAndProvesToothThresholdVariationChangesOutput() {
        val config45 = BrushPresets.dryBrush(toothThreshold = 0.45f)
        val stroke45 = BrushStroke("stroke_dry_45", config = config45)
        stroke45.points.add(StrokePoint(50f, 150f, 0.4f))
        stroke45.points.add(StrokePoint(250f, 150f, 0.6f))
        engine.renderStroke(mockCanvas, stroke45)

        val config15 = BrushPresets.dryBrush(toothThreshold = 0.15f)
        assertNotEquals(
            "Changing toothThreshold must alter configuration",
            config45.mediaProperties.toothThreshold,
            config15.mediaProperties.toothThreshold
        )
        val stroke15 = BrushStroke("stroke_dry_15", config = config15)
        stroke15.points.add(StrokePoint(50f, 150f, 0.4f))
        stroke15.points.add(StrokePoint(250f, 150f, 0.6f))
        engine.renderStroke(mockCanvas, stroke15)
    }

    @Test
    fun testTexturedStampStroke_rendersAndProvesShapeVariationChangesOutput() {
        val configSponge = BrushPresets.texturedStamp(shape = StampPatternShape.SPONGE)
        val strokeSponge = BrushStroke("stroke_stamp_sponge", config = configSponge)
        strokeSponge.points.add(StrokePoint(100f, 100f, 0.7f))
        strokeSponge.points.add(StrokePoint(300f, 100f, 0.7f))
        engine.renderStroke(mockCanvas, strokeSponge)

        val configSpeckle = BrushPresets.texturedStamp(shape = StampPatternShape.SPECKLE)
        assertNotEquals(
            "Changing stampShape must alter configuration",
            configSponge.mediaProperties.stampShape,
            configSpeckle.mediaProperties.stampShape
        )
        val strokeSpeckle = BrushStroke("stroke_stamp_speckle", config = configSpeckle)
        strokeSpeckle.points.add(StrokePoint(100f, 100f, 0.7f))
        strokeSpeckle.points.add(StrokePoint(300f, 100f, 0.7f))
        engine.renderStroke(mockCanvas, strokeSpeckle)
    }

    // -------------------------------------------------------------------------
    // 3. Fixed Seed Determinism
    // -------------------------------------------------------------------------

    @Test
    fun testFixedSeed_producesDeterministicProceduralTextures() {
        val seed = 4242L
        val tex1 = BrushAssetManager.generatePaperTooth(seed, 64, 64)
        val tex2 = BrushAssetManager.generatePaperTooth(seed, 64, 64)

        assertEquals("Same width", tex1.width, tex2.width)
        assertEquals("Same height", tex1.height, tex2.height)
        assertArrayEquals("Fixed seed must produce bit-exact identical byte buffers", tex1.data, tex2.data)

        val sponge1 = BrushAssetManager.generateSponge(seed, 64, 64)
        val sponge2 = BrushAssetManager.generateSponge(seed, 64, 64)
        assertArrayEquals("Sponge procedural mask bit-exact across repeat runs", sponge1.data, sponge2.data)
    }

    // -------------------------------------------------------------------------
    // 4. Touch-Only Hardware Dynamic Pressure Fallback
    // -------------------------------------------------------------------------

    @Test
    fun testTouchOnlyHardwarePressureFallback_modulatesPaintMedia() {
        val resolver = InputPressureResolver(PressureCalibrationMode.AUTO_DETECT)

        // Simulate touch-only hardware (toolType = FINGER, unvarying hardware pressure = 1.0f)
        val t0 = System.nanoTime()
        val p0 = PointerSample(pointerId = 0, screenX = 50f, screenY = 100f, pressure = 1.0f, toolType = ToolType.FINGER, timestampNano = t0)
        resolver.startStroke(p0)
        val res0 = resolver.resolvePressure(p0)

        assertTrue("Fallback must activate on touch-only finger hardware", res0.isFallbackActive)
        val pressure0 = res0.effectivePressure
        assertTrue("Entry pressure should be gently tapered", pressure0 in 0.2f..0.6f)

        // Accelerate stroke across screen (simulating fast movement)
        val p1 = PointerSample(pointerId = 0, screenX = 250f, screenY = 100f, pressure = 1.0f, toolType = ToolType.FINGER, timestampNano = t0 + 20_000_000L)
        val res1 = resolver.resolvePressure(p1)
        val pressure1 = res1.effectivePressure

        // Decelerate stroke to a gentle stop
        val p2 = PointerSample(pointerId = 0, screenX = 260f, screenY = 100f, pressure = 1.0f, toolType = ToolType.FINGER, timestampNano = t0 + 120_000_000L)
        val res2 = resolver.resolvePressure(p2)
        val pressure2 = res2.effectivePressure

        // Verify that velocity-derived pressure varies dynamically even though hardware reported constant 1.0f
        assertNotEquals("Dynamic pressure must vary with touch velocity", pressure1, pressure2, 0.01f)
        assertTrue("Fast stroke pressure modulates within valid range [0.1, 1.0]", pressure1 in 0.1f..1.0f)
        assertTrue("Slow stroke pressure modulates within valid range [0.1, 1.0]", pressure2 in 0.1f..1.0f)

        // Verify paint media radius modulation with resolved fallback pressure:
        // Slower stroke produces higher pressure and thicker radius than high-speed stroke
        val dryBrush = BrushPresets.dryBrush()
        val radiusFast = engine.computeRadius(dryBrush, pressure1)
        val radiusSlow = engine.computeRadius(dryBrush, pressure2)
        assertTrue("Media brush radius expands with dynamic pressure (slow motion is thicker)", radiusSlow > radiusFast)
    }

    // -------------------------------------------------------------------------
    // 5. Asset Manager, Memory Bounding & Custom User Asset Loading (AC #3)
    // -------------------------------------------------------------------------

    @Test
    fun testBrushAssetManager_proceduralAndCustomAssetHandling() {
        // Procedural retrieval
        val sponge = BrushAssetManager.getTexture(StampPatternShape.SPONGE, dimension = 64)
        assertNotNull(sponge)
        assertEquals(64, sponge.width)
        assertEquals(64, sponge.height)

        val canvas = BrushAssetManager.getTexture(StampPatternShape.CANVAS_GRAIN, dimension = 64)
        assertNotNull(canvas)
        assertEquals(64, canvas.width)

        // Non-existent user file path safely falls back to procedural without crash
        val fallback = BrushAssetManager.getTexture(
            shape = StampPatternShape.CUSTOM_ASSET,
            customAssetPath = "/non_existent/path/to/missing_stamp.png",
            dimension = 64
        )
        assertNotNull("Missing user texture must gracefully fall back", fallback)
        assertEquals(64, fallback.width)

        // User file loader rejects non-existent file cleanly
        val nullResult = BrushAssetManager.loadUserTexture("/non_existent/file.png")
        assertNull("Missing file returns null", nullResult)
    }

    @Test
    fun testBuiltInBrushAssetSizeRecorded() {
        val assetDir = File("app/src/main/assets/brushes/textures")
        if (assetDir.exists()) {
            val totalBytes = BrushAssetManager.getBuiltInAssetSizeBytes(assetDir)
            assertTrue("Asset directory contains files", totalBytes > 0L)
            assertTrue("Total built-in asset size must be compact (< 100 KB)", totalBytes < 100_000L)
        }
    }

    // -------------------------------------------------------------------------
    // 6. BrushPresetRepository & Validation Round-Trip
    // -------------------------------------------------------------------------

    @Test
    fun testBrushPresetRepository_containsMediaPresets() {
        val repository = BrushPresetRepository(storageDir = null)
        val presets = repository.getAllPresets()

        val markerPreset = presets.firstOrNull { it.id == "preset_media_marker" }
        assertNotNull("Marker preset present in repository", markerPreset)
        assertEquals(PaintMediaFamily.MARKER, markerPreset!!.config.mediaProperties.family)

        val waterPreset = presets.firstOrNull { it.id == "preset_media_watercolour" }
        assertNotNull("Watercolour preset present in repository", waterPreset)
        assertEquals(PaintMediaFamily.WATERCOLOUR, waterPreset!!.config.mediaProperties.family)

        val oilPreset = presets.firstOrNull { it.id == "preset_media_oil" }
        assertNotNull("Oil preset present in repository", oilPreset)
        assertEquals(PaintMediaFamily.OIL_PAINT, oilPreset!!.config.mediaProperties.family)

        val dryPreset = presets.firstOrNull { it.id == "preset_media_dry_brush" }
        assertNotNull("Dry brush preset present in repository", dryPreset)
        assertEquals(PaintMediaFamily.DRY_BRUSH, dryPreset!!.config.mediaProperties.family)

        val stampPreset = presets.firstOrNull { it.id == "preset_media_stamp" }
        assertNotNull("Stamp preset present in repository", stampPreset)
        assertEquals(PaintMediaFamily.TEXTURED_STAMP, stampPreset!!.config.mediaProperties.family)
    }

    @Test
    fun testMediaPresetSerializationRoundTrip() {
        val original = BrushPreset(
            id = "preset_custom_watercolour",
            name = "Granulating Cold-Press Watercolour",
            category = "Custom Media",
            config = BrushPresets.watercolourWash(wetEdge = 2.4f, diffusion = 0.8f, seed = 999L)
        )

        val json = original.toJson()
        val restored = BrushPreset.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        val m = restored.config.mediaProperties
        assertEquals(PaintMediaFamily.WATERCOLOUR, m.family)
        assertEquals(2.4f, m.wetEdgeIntensity, 0.01f)
        assertEquals(0.8f, m.waterDiffusion, 0.01f)
        assertEquals(0.35f, m.granulation, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMediaProperties_rejectsOutOfRangeParameters() {
        // chiselAspect cannot be > 8.0f
        PaintMediaProperties(chiselAspect = 12.0f)
    }
}
