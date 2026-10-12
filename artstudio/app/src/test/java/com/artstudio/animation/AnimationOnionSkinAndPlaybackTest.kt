package com.artstudio.animation

import com.artstudio.document.model.ArtDocument
import com.artstudio.document.model.CanvasConfig
import com.artstudio.document.model.LayerNode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive verification suite for Prompt 34:
 * Onion Skin Overlays, Timeline Scrubbing, Playback Transitions & Low-Memory Preview.
 */
class AnimationOnionSkinAndPlaybackTest {

    private lateinit var doc: ArtDocument
    private lateinit var pixelBuffers: MutableMap<String, IntArray>
    private val width = 32
    private val height = 32
    private val totalPixels = width * height

    @Before
    fun setUp() {
        doc = ArtDocument(
            canvas = CanvasConfig(width, height, backgroundColor = 0xFFEEEEEEL),
            rootGroup = LayerNode.LayerGroup(
                name = "Root",
                children = listOf(
                    LayerNode.RasterLayer(id = "bg_layer", name = "Background"),
                    LayerNode.RasterLayer(id = "cel_1", name = "Frame 1 Cel"),
                    LayerNode.RasterLayer(id = "cel_2", name = "Frame 2 Cel"),
                    LayerNode.RasterLayer(id = "cel_3", name = "Frame 3 Cel")
                )
            )
        )

        pixelBuffers = mutableMapOf()
        // bg_layer: light gray
        pixelBuffers["bg_layer"] = IntArray(totalPixels) { 0xFFEEEEEE.toInt() }
        // cel_1: blue block on top-left [0..15, 0..15]
        val cel1Buf = IntArray(totalPixels) { 0x00000000 }
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                cel1Buf[y * width + x] = 0xFF0000FF.toInt() // Pure Blue
            }
        }
        pixelBuffers["cel_1"] = cel1Buf

        // cel_2: green block in center [8..23, 8..23]
        val cel2Buf = IntArray(totalPixels) { 0x00000000 }
        for (y in 8 until 24) {
            for (x in 8 until 24) {
                cel2Buf[y * width + x] = 0xFF00FF00.toInt() // Pure Green
            }
        }
        pixelBuffers["cel_2"] = cel2Buf

        // cel_3: red block on bottom-right [16..31, 16..31]
        val cel3Buf = IntArray(totalPixels) { 0x00000000 }
        for (y in 16 until 32) {
            for (x in 16 until 32) {
                cel3Buf[y * width + x] = 0xFFFF0000.toInt() // Pure Red
            }
        }
        pixelBuffers["cel_3"] = cel3Buf
    }

    // =========================================================================
    // 1. Onion Skin Visibility & Guide Toggles
    // =========================================================================

    @Test
    fun testOnionSkinVisibility_enabledAndDisabledToggles() {
        val f1 = AnimationFrame(name = "F1", celLayerIds = listOf("cel_1"))
        val f2 = AnimationFrame(name = "F2", celLayerIds = listOf("cel_2"))
        val f3 = AnimationFrame(name = "F3", celLayerIds = listOf("cel_3"))

        val timeline = AnimationTimeline(
            fps = 12,
            frames = listOf(f1, f2, f3),
            currentFrameIndex = 1, // On F2 (center green)
            onionSkin = OnionSkinSettings(
                enabled = false,
                framesBefore = 1,
                framesAfter = 1
            )
        )

        // Render Frame 2 with onion skin disabled
        val noOnion = AnimationFrameRenderer.renderFrame(
            document = doc,
            frameIndex = 1,
            timeline = timeline,
            pixelBuffers = pixelBuffers,
            applyOnionSkin = false
        )

        // Pixel at (4, 4) belongs to cel_1 (past frame) -> should be background (light gray)
        assertEquals(0xFFEEEEEE.toInt(), noOnion[4 * width + 4])

        // Now render with onion skin enabled
        val withOnionTimeline = timeline.copy(onionSkin = timeline.onionSkin.copy(enabled = true))
        val withOnion = AnimationFrameRenderer.renderFrame(
            document = doc,
            frameIndex = 1,
            timeline = withOnionTimeline,
            pixelBuffers = pixelBuffers,
            applyOnionSkin = true
        )

        // Pixel at (4, 4) should now be tinted orange-red (ghost of cel_1)
        val tintedPixel = withOnion[4 * width + 4]
        assertNotEquals(0xFFEEEEEE.toInt(), tintedPixel)
        // Red channel should be dominant due to orange tint (0xFFFF4500)
        val r = (tintedPixel ushr 16) and 0xFF
        val b = tintedPixel and 0xFF
        assertTrue("Red channel ($r) should exceed blue ($b) in orange onion skin", r > b)
    }

    @Test
    fun testOnionSkinVisibility_individualTogglesBeforeAndAfter() {
        val f1 = AnimationFrame(name = "F1", celLayerIds = listOf("cel_1"))
        val f2 = AnimationFrame(name = "F2", celLayerIds = listOf("cel_2"))
        val f3 = AnimationFrame(name = "F3", celLayerIds = listOf("cel_3"))

        val baseSettings = OnionSkinSettings(
            enabled = true,
            framesBefore = 1,
            framesAfter = 1,
            showBefore = true,
            showAfter = false // Suppress future frames
        )
        val timeline = AnimationTimeline(
            fps = 12,
            frames = listOf(f1, f2, f3),
            currentFrameIndex = 1,
            onionSkin = baseSettings
        )

        val renderedPastOnly = AnimationFrameRenderer.renderFrame(
            document = doc,
            frameIndex = 1,
            timeline = timeline,
            pixelBuffers = pixelBuffers,
            applyOnionSkin = true
        )

        // Past pixel (4, 4) should have ghost tint
        assertNotEquals(0xFFEEEEEE.toInt(), renderedPastOnly[4 * width + 4])
        // Future pixel (28, 28) should NOT have ghost tint (remains background light gray)
        assertEquals(0xFFEEEEEE.toInt(), renderedPastOnly[28 * width + 28])

        // Now swap: showAfter = true, showBefore = false
        val futureOnlyTimeline = timeline.copy(
            onionSkin = baseSettings.copy(showBefore = false, showAfter = true)
        )
        val renderedFutureOnly = AnimationFrameRenderer.renderFrame(
            document = doc,
            frameIndex = 1,
            timeline = futureOnlyTimeline,
            pixelBuffers = pixelBuffers,
            applyOnionSkin = true
        )

        // Past pixel (4, 4) is now background light gray
        assertEquals(0xFFEEEEEE.toInt(), renderedFutureOnly[4 * width + 4])
        // Future pixel (28, 28) now has cyan ghost tint (0xFF00BFFF)
        val futureTint = renderedFutureOnly[28 * width + 28]
        assertNotEquals(0xFFEEEEEE.toInt(), futureTint)
        val bFuture = futureTint and 0xFF
        val rFuture = (futureTint ushr 16) and 0xFF
        assertTrue("Blue channel ($bFuture) should exceed red ($rFuture) in cyan onion skin", bFuture > rFuture)
    }

    @Test
    fun testOnionSkin_tintModes() {
        val f1 = AnimationFrame(name = "F1", celLayerIds = listOf("cel_1")) // Pure Blue
        val f2 = AnimationFrame(name = "F2", celLayerIds = listOf("cel_2"))

        // Mode 1: TINTED (monochrome orange)
        val timelineTinted = AnimationTimeline(
            frames = listOf(f1, f2),
            currentFrameIndex = 1,
            onionSkin = OnionSkinSettings(
                enabled = true,
                showBefore = true,
                tintMode = OnionSkinTintMode.TINTED,
                tintBefore = 0xFFFF4500L
            )
        )
        val tintedComposite = AnimationFrameRenderer.renderFrame(
            doc, 1, timelineTinted, pixelBuffers, applyOnionSkin = true
        )
        val tintedPx = tintedComposite[4 * width + 4]
        val rTinted = (tintedPx ushr 16) and 0xFF
        val bTinted = tintedPx and 0xFF
        assertTrue(rTinted > bTinted)

        // Mode 2: ORIGINAL_COLOR (source blue preserved, only blended with alpha)
        val timelineOriginal = timelineTinted.copy(
            onionSkin = timelineTinted.onionSkin.copy(tintMode = OnionSkinTintMode.ORIGINAL_COLOR)
        )
        val origComposite = AnimationFrameRenderer.renderFrame(
            doc, 1, timelineOriginal, pixelBuffers, applyOnionSkin = true
        )
        val origPx = origComposite[4 * width + 4]
        val rOrig = (origPx ushr 16) and 0xFF
        val bOrig = origPx and 0xFF
        assertTrue("Original color mode should preserve source blue dominance", bOrig > rOrig)
    }

    @Test
    fun testOnionSkin_falloffModes() {
        // Multi-frame setup
        val frames = (1..4).map { idx ->
            AnimationFrame(id = "f_$idx", name = "F$idx", celLayerIds = listOf("cel_1"))
        }

        val linearTimeline = AnimationTimeline(
            frames = frames,
            currentFrameIndex = 3, // Frame 4 looking at 3 frames back
            onionSkin = OnionSkinSettings(
                enabled = true,
                framesBefore = 3,
                falloff = OnionSkinFalloff.LINEAR,
                opacityBefore = 0.5f
            )
        )
        assertNotNull(linearTimeline.onionSkin)
        assertEquals(OnionSkinFalloff.LINEAR, linearTimeline.onionSkin.falloff)

        val constantTimeline = linearTimeline.copy(
            onionSkin = linearTimeline.onionSkin.copy(falloff = OnionSkinFalloff.CONSTANT)
        )
        assertEquals(OnionSkinFalloff.CONSTANT, constantTimeline.onionSkin.falloff)

        val quadraticTimeline = linearTimeline.copy(
            onionSkin = linearTimeline.onionSkin.copy(falloff = OnionSkinFalloff.QUADRATIC)
        )
        assertEquals(OnionSkinFalloff.QUADRATIC, quadraticTimeline.onionSkin.falloff)
    }

    @Test
    fun testOnionSkin_suppressDuringPlayback() {
        val f1 = AnimationFrame(name = "F1", celLayerIds = listOf("cel_1"))
        val f2 = AnimationFrame(name = "F2", celLayerIds = listOf("cel_2"))

        val timeline = AnimationTimeline(
            frames = listOf(f1, f2),
            currentFrameIndex = 1,
            onionSkin = OnionSkinSettings(
                enabled = true,
                suppressDuringPlayback = true
            )
        )

        // Stopped (isPlaying = false): onion skin is rendered
        val stopped = AnimationFrameRenderer.renderFrame(
            doc, 1, timeline, pixelBuffers, applyOnionSkin = true, isPlaying = false
        )
        assertNotEquals(0xFFEEEEEE.toInt(), stopped[4 * width + 4])

        // Playing (isPlaying = true): onion skin is muted for maximum FPS
        val playing = AnimationFrameRenderer.renderFrame(
            doc, 1, timeline, pixelBuffers, applyOnionSkin = true, isPlaying = true
        )
        assertEquals(0xFFEEEEEE.toInt(), playing[4 * width + 4])
    }

    // =========================================================================
    // 2. Critical Isolation Guarantee: Source Pixels are Untouched
    // =========================================================================

    @Test
    fun testOnionSkinIsolation_sourceLayerPixelsAreBitExactUntouched() {
        val f1 = AnimationFrame(name = "F1", celLayerIds = listOf("cel_1"))
        val f2 = AnimationFrame(name = "F2", celLayerIds = listOf("cel_2"))
        val f3 = AnimationFrame(name = "F3", celLayerIds = listOf("cel_3"))

        val timeline = AnimationTimeline(
            frames = listOf(f1, f2, f3),
            currentFrameIndex = 1,
            onionSkin = OnionSkinSettings(enabled = true, framesBefore = 1, framesAfter = 1)
        )

        // Take checksums of source pixel buffers before rendering
        val cel1Before = pixelBuffers["cel_1"]!!.clone()
        val cel2Before = pixelBuffers["cel_2"]!!.clone()
        val cel3Before = pixelBuffers["cel_3"]!!.clone()
        val bgBefore = pixelBuffers["bg_layer"]!!.clone()

        // Render composite with full onion skinning active
        val composite = AnimationFrameRenderer.renderFrame(
            document = doc,
            frameIndex = 1,
            timeline = timeline,
            pixelBuffers = pixelBuffers,
            applyOnionSkin = true
        )
        assertNotNull(composite)

        // Verify that source buffers are 100% bit-exact untouched
        assertArrayEquals("Cel 1 pixels must not be mutated by onion skinning", cel1Before, pixelBuffers["cel_1"]!!)
        assertArrayEquals("Cel 2 pixels must not be mutated by onion skinning", cel2Before, pixelBuffers["cel_2"]!!)
        assertArrayEquals("Cel 3 pixels must not be mutated by onion skinning", cel3Before, pixelBuffers["cel_3"]!!)
        assertArrayEquals("Background pixels must not be mutated by onion skinning", bgBefore, pixelBuffers["bg_layer"]!!)
    }

    // =========================================================================
    // 3. Frame Stepping & Navigation
    // =========================================================================

    @Test
    fun testFrameStepping_stepForwardAndBackwardAcrossLoopModes() {
        val frames = (1..3).map { AnimationFrame(name = "F$it") }

        // 1. LOOP Mode: wraps around
        val loopTimeline = AnimationTimeline(frames = frames, loopMode = LoopMode.LOOP)
        val loopController = AnimationPlaybackController(loopTimeline)
        assertEquals(0, loopController.currentFrameIndex)
        assertEquals(1, loopController.stepForward())
        assertEquals(2, loopController.stepForward())
        assertEquals(0, loopController.stepForward()) // Wrap to 0
        assertEquals(2, loopController.stepBackward()) // Wrap back to 2

        // 2. ONCE Mode: clamps at boundaries
        val onceTimeline = AnimationTimeline(frames = frames, loopMode = LoopMode.ONCE)
        val onceController = AnimationPlaybackController(onceTimeline)
        assertEquals(0, onceController.currentFrameIndex)
        assertEquals(0, onceController.stepBackward()) // Clamped at 0
        assertEquals(1, onceController.stepForward())
        assertEquals(2, onceController.stepForward())
        assertEquals(2, onceController.stepForward()) // Clamped at lastIndex 2

        // 3. PING_PONG Mode: steps through indices
        val ppTimeline = AnimationTimeline(frames = frames, loopMode = LoopMode.PING_PONG)
        val ppController = AnimationPlaybackController(ppTimeline)
        assertEquals(0, ppController.currentFrameIndex)
        assertEquals(1, ppController.stepForward())
        assertEquals(2, ppController.stepForward())
        assertEquals(1, ppController.stepForward()) // Bounces back
    }

    @Test
    fun testFrameStepping_firstAndLastFrameJumps() {
        val frames = (1..5).map { AnimationFrame(name = "F$it") }
        val timeline = AnimationTimeline(frames = frames, currentFrameIndex = 2)
        val controller = AnimationPlaybackController(timeline)

        assertEquals(2, controller.currentFrameIndex)

        // Jump to first
        assertEquals(0, controller.goToFirstFrame())
        assertEquals(0, controller.currentFrameIndex)
        assertEquals(0, controller.currentTick)

        // Jump to last
        assertEquals(4, controller.goToLastFrame())
        assertEquals(4, controller.currentFrameIndex)
        assertEquals(4, controller.currentTick)
    }

    // =========================================================================
    // 4. Interactive Timeline Scrubbing
    // =========================================================================

    @Test
    fun testTimelineScrubbing_scrubToFrameAndTick() {
        val f1 = AnimationFrame(name = "F1", durationTicks = 1)
        val f2 = AnimationFrame(name = "F2", durationTicks = 3) // Ticks 1, 2, 3
        val f3 = AnimationFrame(name = "F3", durationTicks = 2) // Ticks 4, 5

        val timeline = AnimationTimeline(fps = 10, frames = listOf(f1, f2, f3))
        var changedFrame = -1
        val controller = AnimationPlaybackController(timeline) { changedFrame = it }

        controller.startScrubbing()
        assertTrue(controller.isScrubbing)
        assertFalse(controller.isPlaying)

        // Scrub directly by frame
        controller.updateScrub(1)
        assertEquals(1, controller.currentFrameIndex)
        assertEquals(1, changedFrame)

        // Scrub by tick: tick 2 should still resolve to frame 1 (F2 has 3 ticks)
        controller.scrubToTick(2)
        assertEquals(1, controller.currentFrameIndex)
        assertEquals(2, controller.currentTick)

        // Scrub by tick: tick 4 should resolve to frame 2 (F3)
        controller.scrubToTick(4)
        assertEquals(2, controller.currentFrameIndex)
        assertEquals(4, controller.currentTick)

        // Finish scrubbing
        controller.finishScrubbing()
        assertFalse(controller.isScrubbing)
    }

    @Test
    fun testTimelineScrubbing_normalizedAndTimestampMapping() {
        val f1 = AnimationFrame(durationTicks = 2)
        val f2 = AnimationFrame(durationTicks = 2)
        val f3 = AnimationFrame(durationTicks = 2)
        // Total ticks = 6. At 10 FPS, total cycle = 600 ms

        val timeline = AnimationTimeline(fps = 10, frames = listOf(f1, f2, f3))
        val controller = AnimationPlaybackController(timeline)

        // Progress 0.0 -> Frame 0
        assertEquals(0, controller.scrubToNormalized(0.0f))
        assertEquals(0, controller.currentFrameIndex)

        // Progress 0.5 -> Tick 2 (Tick 2 of 5) -> Frame 1
        assertEquals(1, controller.scrubToNormalized(0.5f))
        assertEquals(1, controller.currentFrameIndex)

        // Progress 1.0 -> Tick 5 -> Frame 2
        assertEquals(2, controller.scrubToNormalized(1.0f))
        assertEquals(2, controller.currentFrameIndex)

        // Scrub by time ms: 150 ms at 100 ms/tick -> Tick 1 -> Frame 0
        assertEquals(0, controller.scrubToTimeMs(150L))
        assertEquals(0, controller.currentFrameIndex)

        // Scrub by time ms: 250 ms -> Tick 2 -> Frame 1
        assertEquals(1, controller.scrubToTimeMs(250L))
        assertEquals(1, controller.currentFrameIndex)
    }

    // =========================================================================
    // 5. Playback Transitions & Dynamic FPS
    // =========================================================================

    @Test
    fun testPlaybackTransitions_playPauseStop() {
        val timeline = AnimationTimeline(frames = listOf(AnimationFrame(), AnimationFrame(), AnimationFrame()))
        val controller = AnimationPlaybackController(timeline)

        assertFalse(controller.isPlaying)

        // Play
        controller.play()
        assertTrue(controller.isPlaying)

        // Advance a bit
        controller.advanceTime(100L)

        // Pause
        controller.pause()
        assertFalse(controller.isPlaying)

        // Toggle Play/Pause
        assertTrue(controller.togglePlayPause()) // resumes
        assertFalse(controller.togglePlayPause()) // pauses

        // Stop resets to frame 0 and tick 0
        controller.goToFrame(2)
        assertEquals(2, controller.currentFrameIndex)
        controller.stop()
        assertFalse(controller.isPlaying)
        assertEquals(0, controller.currentFrameIndex)
        assertEquals(0, controller.currentTick)
    }

    @Test
    fun testFrameRateConfiguration_dynamicFpsAndTickTiming() {
        val timeline = AnimationTimeline(fps = 12, frames = listOf(AnimationFrame(), AnimationFrame()))
        val controller = AnimationPlaybackController(timeline)

        assertEquals(12, controller.timeline.fps)

        // Dynamically change to 24 FPS
        controller.setFps(24)
        assertEquals(24, controller.timeline.fps)

        // Clamping check
        controller.setFps(120)
        assertEquals(AnimationLimits.MAX_FPS, controller.timeline.fps)

        controller.setFps(0)
        assertEquals(AnimationLimits.MIN_FPS, controller.timeline.fps)
    }

    // =========================================================================
    // 6. Reduced-Cost Preview Path & Cache (Low-Memory Devices)
    // =========================================================================

    @Test
    fun testReducedCostPreviewPath_downscaledOutputMatchesFullComposite() {
        val f1 = AnimationFrame(name = "F1", celLayerIds = listOf("cel_1"))
        val timeline = AnimationTimeline(frames = listOf(f1))

        // Full resolution (32x32 = 1024 pixels)
        val fullBuffer = AnimationFrameRenderer.renderReducedCostPreviewFrame(
            document = doc,
            frameIndex = 0,
            scale = 1.0f,
            timeline = timeline,
            pixelBuffers = pixelBuffers
        )
        assertEquals(32, fullBuffer.width)
        assertEquals(32, fullBuffer.height)
        assertEquals(1024, fullBuffer.pixels.size)
        assertEquals(1.0f, fullBuffer.scale, 0.001f)

        // Half resolution (16x16 = 256 pixels, 75% memory reduction)
        val halfBuffer = AnimationFrameRenderer.renderReducedCostPreviewFrame(
            document = doc,
            frameIndex = 0,
            scale = 0.5f,
            timeline = timeline,
            pixelBuffers = pixelBuffers
        )
        assertEquals(16, halfBuffer.width)
        assertEquals(16, halfBuffer.height)
        assertEquals(256, halfBuffer.pixels.size)
        assertEquals(0.5f, halfBuffer.scale, 0.001f)

        // Top-left block in halfBuffer should match top-left blue block in fullBuffer
        assertEquals(fullBuffer.pixels[2 * 32 + 2], halfBuffer.pixels[1 * 16 + 1])
    }

    @Test
    fun testPreviewCache_storesAndEvictsPreviewFrames() {
        val cache = AnimationPreviewCache(maxFrames = 3)
        assertEquals(0, cache.size)

        val b0 = PreviewFrameBuffer(16, 16, IntArray(256), 0.5f)
        val b1 = PreviewFrameBuffer(16, 16, IntArray(256), 0.5f)
        val b2 = PreviewFrameBuffer(16, 16, IntArray(256), 0.5f)
        val b3 = PreviewFrameBuffer(16, 16, IntArray(256), 0.5f)

        cache.put(0, 0.5f, false, b0)
        cache.put(1, 0.5f, false, b1)
        cache.put(2, 0.5f, false, b2)
        assertEquals(3, cache.size)
        assertNotNull(cache.get(0, 0.5f, false))

        // Inserting 4th entry evicts oldest (frame 1 because 0 was just accessed)
        cache.put(3, 0.5f, false, b3)
        assertEquals(3, cache.size)
        assertNotNull(cache.get(3, 0.5f, false))
        assertNotNull(cache.get(0, 0.5f, false))

        // Clear
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get(0, 0.5f, false))
    }
}
